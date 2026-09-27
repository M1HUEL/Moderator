package com.itson.moderator.storage;

import com.itson.moderator.model.PlayerRecord;
import com.itson.moderator.model.Punishment;
import com.itson.moderator.model.PunishmentType;
import com.itson.moderator.model.Report;
import com.itson.moderator.model.ReportStatus;
import com.itson.moderator.util.Ids;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A flat YAML file, {@code data.yml}, holding players, punishments and reports.
 *
 * <p>Entries are indexed by position in memory: applying a sanction replaces the
 * old record with the revoked one, so the history keeps growing without any
 * update-in-place logic on the file.
 *
 * <p>The whole dataset is held in memory and written as one document, which is
 * what makes reads during a command cheap. The trade off is that the file is
 * rewritten on every flush, so the flush interval is deliberately generous: see
 * {@code ModeratorPlugin} for the schedule.
 *
 * <p>Reading is defensive on purpose. A hand edited or half written file should
 * cost the server one bad record, not a failed startup, so an entry that cannot
 * be understood is logged and skipped rather than thrown.
 */
public final class YamlStore implements ModerationStore {

  /** Root key of the player section, indexed by UUID. */
  private static final String PLAYERS = "players";

  /**
   * Root key of the punishment list.
   *
   * <p>The list is written at the root under this key, not inside a section of
   * the same name: {@code punishments.punishments} is a section holding a list,
   * which does not round trip through the YAML parser.
   */
  private static final String PUNISHMENTS = "punishments";

  /** Root key of the report list, with the same flat layout as the punishments. */
  private static final String REPORTS = "reports";

  private final File file;

  private final Logger logger;

  /** Known players by UUID, the primary key of the player section. */
  private final Map<UUID, PlayerRecord> players = new HashMap<>();

  /** Secondary index by lower cased name, so a rename can drop the old key. */
  private final Map<String, PlayerRecord> playersByName = new HashMap<>();

  private final List<Punishment> punishments = new ArrayList<>();

  private final List<Report> reports = new ArrayList<>();

  /**
   * Whether anything changed since the last successful flush.
   *
   * <p>Volatile because it is written on the main thread and read by the periodic
   * save task, which is the only other reader.
   */
  private volatile boolean dirty;

  public YamlStore(@NotNull File file, @NotNull Logger logger) {
    this.file = file;
    this.logger = logger;
  }

  /**
   * Rebuilds the in-memory state from disk, discarding whatever was loaded before.
   *
   * <p>A missing file is not an error: it is simply a server that has never
   * issued a sanction, and the plugin starts with an empty history.
   */
  @Override
  public void load() {
    players.clear();
    playersByName.clear();
    punishments.clear();
    reports.clear();
    dirty = false;

    if (!file.exists()) {
      return;
    }

    YamlConfiguration data = YamlConfiguration.loadConfiguration(file);

    readPlayers(data.getConfigurationSection(PLAYERS));
    readPunishments(data);
    readReports(data);
  }

  /**
   * Writes the whole document when something changed.
   *
   * <p>A failure is logged and the store is left dirty, so the next scheduled
   * flush retries. Losing the in-memory history would be worse than a noisy
   * console, and the alternative, throwing, would take the server down over an
   * unwritable file.
   */
  @Override
  public void save() {
    if (!dirty) {
      return;
    }

    YamlConfiguration data = new YamlConfiguration();

    writePlayers(data.createSection(PLAYERS));
    data.set(PUNISHMENTS, punishmentEntries());
    data.set(REPORTS, reportEntries());

    try {
      data.save(file);
      dirty = false;
    } catch (IOException exception) {
      logger.log(Level.SEVERE, "Could not save " + file.getName() + ", changes are kept in memory only", exception);
    }
  }

  /** True when there are changes that a flush would write out. */
  @Override
  public boolean isDirty() {
    return dirty;
  }

  @Override
  public @NotNull PlayerRecord player(@NotNull UUID id) {
    PlayerRecord record = players.get(id);

    if (record != null) {
      return record;
    }

    return PlayerRecord.of(id, id.toString(), null, Instant.now());
  }

  @Override
  public @NotNull List<PlayerRecord> players() {
    return List.copyOf(players.values());
  }

  @Override
  public Optional<PlayerRecord> playerNamed(@NotNull String name) {
    return Optional.ofNullable(playersByName.get(name.toLowerCase(Locale.ROOT)));
  }

  /**
   * Stores a record, replacing any previous one for the same UUID.
   *
   * <p>The name index is rebuilt from the outgoing record so a player who changed
   * name stops answering to the old one.
   */
  @Override
  public void record(@NotNull PlayerRecord record) {
    PlayerRecord previous = players.put(record.id(), record);

    if (previous != null) {
      playersByName.remove(previous.name().toLowerCase(Locale.ROOT));
    }

    playersByName.put(record.name().toLowerCase(Locale.ROOT), record);
    dirty = true;
  }

  @Override
  public void add(@NotNull Punishment punishment) {
    punishments.add(punishment);
    dirty = true;
  }

  @Override
  public void update(@NotNull Punishment punishment) {
    for (int index = 0; index < punishments.size(); index++) {
      if (punishments.get(index).id().equals(punishment.id())) {
        punishments.set(index, punishment);
        dirty = true;

        return;
      }
    }

    punishments.add(punishment);
    dirty = true;
  }

  @Override
  public @NotNull List<Punishment> punishments() {
    return List.copyOf(punishments);
  }

  @Override
  public void add(@NotNull Report report) {
    reports.add(report);
    dirty = true;
  }

  @Override
  public void update(@NotNull Report report) {
    for (int index = 0; index < reports.size(); index++) {
      if (reports.get(index).id().equals(report.id())) {
        reports.set(index, report);
        dirty = true;

        return;
      }
    }

    reports.add(report);
    dirty = true;
  }

  @Override
  public @NotNull List<Report> reports() {
    return List.copyOf(reports);
  }

  /**
   * Reads the player section, which is keyed by UUID.
   *
   * <p>A key that is not a UUID, or an entry missing a name or a timestamp, is
   * logged and skipped so one bad line does not cost the whole section.
   */
  private void readPlayers(@Nullable ConfigurationSection section) {
    if (section == null) {
      return;
    }

    for (String key : section.getKeys(false)) {
      Optional<UUID> id = Ids.parseUuid(key);

      if (id.isEmpty()) {
        logger.warning("data.yml: players." + key + " is not a valid UUID, skipping it");

        continue;
      }

      ConfigurationSection entry = section.getConfigurationSection(key);

      if (entry == null) {
        continue;
      }

      String name = entry.getString("name");
      Instant firstSeen = instant(entry.getLong("first-seen"));
      Instant lastSeen = instant(entry.getLong("last-seen"));

      if (name == null || name.isBlank() || firstSeen == null || lastSeen == null) {
        logger.warning("data.yml: players." + key + " is incomplete, skipping it");

        continue;
      }

      PlayerRecord record = new PlayerRecord(id.get(), name, entry.getString("last-ip"), firstSeen, lastSeen);

      players.put(record.id(), record);
      playersByName.put(name.toLowerCase(Locale.ROOT), record);
    }
  }

  private void readPunishments(@NotNull YamlConfiguration data) {
    // The list is written at the root under its own key, not inside a section of
    // the same name: punishments.punishments is a section holding a list, which
    // does not round trip.
    for (Map<?, ?> raw : data.getMapList(PUNISHMENTS)) {
      Punishment punishment = readPunishment(raw);

      if (punishment != null) {
        punishments.add(punishment);
      }
    }
  }

  /**
   * Rebuilds one punishment from a raw map.
   *
   * @return the entry, or null when it is incomplete or fails validation
   */
  private @Nullable Punishment readPunishment(@Nullable Map<?, ?> raw) {
    if (raw == null) {
      return null;
    }

    String id = string(raw, "id");
    Optional<UUID> target = Ids.parseUuid(string(raw, "target"));
    Optional<UUID> staff = Ids.parseUuid(string(raw, "staff"));
    PunishmentType type = PunishmentType.byId(string(raw, "type")).orElse(null);
    String targetName = string(raw, "target-name");
    String staffName = string(raw, "staff-name");
    String reason = string(raw, "reason");
    Instant created = instant(number(raw, "created"));

    if (id == null || target.isEmpty() || staff.isEmpty() || type == null || targetName == null || staffName == null
        || reason == null || created == null) {
      logger.warning("data.yml: a punishment entry is incomplete, skipping it");

      return null;
    }

    try {
      return new Punishment(id, target.get(), targetName, string(raw, "target-ip"), type, string(raw, "reason-id"),
          reason, staff.get(), staffName, created, instant(number(raw, "expires")), bool(raw, "active"),
          instant(number(raw, "revoked-at")), string(raw, "revoked-by"), string(raw, "revoke-reason"));
    } catch (IllegalArgumentException exception) {
      logger.warning("data.yml: a punishment entry is invalid (" + exception.getMessage() + "), skipping it");

      return null;
    }
  }

  private void readReports(@NotNull YamlConfiguration data) {
    for (Map<?, ?> raw : data.getMapList(REPORTS)) {
      Report report = readReport(raw);

      if (report != null) {
        reports.add(report);
      }
    }
  }

  /**
   * Rebuilds one report from a raw map.
   *
   * <p>Unlike a punishment, a report cannot fail its own validation here: the
   * fields checked by this method are the ones the record constructor insists on.
   *
   * @return the entry, or null when it is incomplete
   */
  private @Nullable Report readReport(@Nullable Map<?, ?> raw) {
    if (raw == null) {
      return null;
    }

    String id = string(raw, "id");
    Optional<UUID> target = Ids.parseUuid(string(raw, "target"));
    Optional<UUID> reporter = Ids.parseUuid(string(raw, "reporter"));
    String targetName = string(raw, "target-name");
    String reporterName = string(raw, "reporter-name");
    String reasonId = string(raw, "reason-id");
    Instant created = instant(number(raw, "created"));
    ReportStatus status = ReportStatus.byId(string(raw, "status")).orElse(ReportStatus.OPEN);

    if (id == null || target.isEmpty() || reporter.isEmpty() || targetName == null || reporterName == null
        || reasonId == null || created == null) {
      logger.warning("data.yml: a report entry is incomplete, skipping it");

      return null;
    }

    return new Report(id, target.get(), targetName, reporter.get(), reporterName, reasonId, string(raw, "details"),
        created, status, string(raw, "handled-by"), string(raw, "resolution"));
  }

  /**
   * Writes the player section, sorted by UUID.
   *
   * <p>Sorting is what makes the file diffable: without it, two servers writing
   * the same history would produce unrelated diffs on every flush.
   */
  private void writePlayers(ConfigurationSection section) {
    Map<UUID, PlayerRecord> sorted = new TreeMap<>(Comparator.comparing(UUID::toString));

    sorted.putAll(players);

    for (PlayerRecord record : sorted.values()) {
      ConfigurationSection entry = section.createSection(record.id().toString());

      entry.set("name", record.name());
      entry.set("last-ip", record.lastIp());
      entry.set("first-seen", millis(record.firstSeen()));
      entry.set("last-seen", millis(record.lastSeen()));
    }
  }

  private List<Map<String, Object>> punishmentEntries() {
    List<Map<String, Object>> list = new ArrayList<>(punishments.size());

    for (Punishment punishment : punishments) {
      Map<String, Object> entry = new LinkedHashMap<>();

      entry.put("id", punishment.id());
      entry.put("target", punishment.target().toString());
      entry.put("target-name", punishment.targetName());
      put(entry, "target-ip", punishment.targetIp());
      entry.put("type", punishment.type().id());
      put(entry, "reason-id", punishment.reasonId());
      entry.put("reason", punishment.reason());
      entry.put("staff", punishment.staff().toString());
      entry.put("staff-name", punishment.staffName());
      entry.put("created", millis(punishment.createdAt()));
      put(entry, "expires", millis(punishment.expiresAt()));
      entry.put("active", punishment.active());
      put(entry, "revoked-at", millis(punishment.revokedAt()));
      put(entry, "revoked-by", punishment.revokedBy());
      put(entry, "revoke-reason", punishment.revokeReason());

      list.add(entry);
    }

    return list;
  }

  private List<Map<String, Object>> reportEntries() {
    List<Map<String, Object>> list = new ArrayList<>(reports.size());

    for (Report report : reports) {
      Map<String, Object> entry = new LinkedHashMap<>();

      entry.put("id", report.id());
      entry.put("target", report.target().toString());
      entry.put("target-name", report.targetName());
      entry.put("reporter", report.reporter().toString());
      entry.put("reporter-name", report.reporterName());
      entry.put("reason-id", report.reasonId());
      put(entry, "details", report.details());
      entry.put("created", millis(report.createdAt()));
      entry.put("status", report.status().id());
      put(entry, "handled-by", report.handledBy());
      put(entry, "resolution", report.resolution());

      list.add(entry);
    }

    return list;
  }

  /**
   * Writes a field only when it holds something.
   *
   * <p>Keeps nulls out of the file entirely, so an absent field and a field
   * holding nothing stay indistinguishable on read.
   */
  private static void put(Map<String, Object> entry, String key, @Nullable Object value) {
    if (value != null) {
      entry.put(key, value);
    }
  }

  /**
   * Reads a string field, treating blank text as absent.
   *
   * <p>The YAML parser hands back whatever type was written, so the value is
   * coerced through {@code String.valueOf} rather than assumed to be a string.
   */
  private static @Nullable String string(Map<?, ?> raw, String key) {
    Object value = raw.get(key);

    if (value == null) {
      return null;
    }

    String text = String.valueOf(value);

    return text.isBlank() ? null : text;
  }

  /**
   * Reads a millisecond field, accepting a number or a numeric string.
   *
   * <p>A missing or unparseable field reads as zero, which
   * {@link #instant(long)} then turns into "no instant at all". That is the
   * behaviour an optional timestamp wants.
   */
  private static long number(Map<?, ?> raw, String key) {
    Object value = raw.get(key);

    if (value instanceof Number number) {
      return number.longValue();
    }

    if (value == null) {
      return 0L;
    }

    try {
      return Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException exception) {
      return 0L;
    }
  }

  /** Reads a boolean field, accepting a real boolean or its text form. */
  private static boolean bool(Map<?, ?> raw, String key) {
    Object value = raw.get(key);

    return value instanceof Boolean flag ? flag : Boolean.parseBoolean(String.valueOf(value));
  }

  /**
   * Turns epoch millis into an instant, mapping zero and anything negative to
   * "no instant", which is how the writer spells a null timestamp.
   */
  private static @Nullable Instant instant(long millis) {
    return millis <= 0 ? null : Instant.ofEpochMilli(millis);
  }

  /** The other half of {@link #instant(long)}: a null instant becomes zero. */
  private static long millis(@Nullable Instant instant) {
    return instant == null ? 0L : instant.toEpochMilli();
  }
}
