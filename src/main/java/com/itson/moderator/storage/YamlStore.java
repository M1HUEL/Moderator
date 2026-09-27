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
 */
public final class YamlStore implements ModerationStore {

  private static final String PLAYERS = "players";
  private static final String PUNISHMENTS = "punishments";
  private static final String REPORTS = "reports";

  private final File file;

  private final Logger logger;

  private final Map<UUID, PlayerRecord> players = new HashMap<>();

  private final Map<String, PlayerRecord> playersByName = new HashMap<>();

  private final List<Punishment> punishments = new ArrayList<>();

  private final List<Report> reports = new ArrayList<>();

  private volatile boolean dirty;

  public YamlStore(@NotNull File file, @NotNull Logger logger) {
    this.file = file;
    this.logger = logger;
  }

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

  private static void put(Map<String, Object> entry, String key, @Nullable Object value) {
    if (value != null) {
      entry.put(key, value);
    }
  }

  private static @Nullable String string(Map<?, ?> raw, String key) {
    Object value = raw.get(key);

    if (value == null) {
      return null;
    }

    String text = String.valueOf(value);

    return text.isBlank() ? null : text;
  }

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

  private static boolean bool(Map<?, ?> raw, String key) {
    Object value = raw.get(key);

    return value instanceof Boolean flag ? flag : Boolean.parseBoolean(String.valueOf(value));
  }

  private static @Nullable Instant instant(long millis) {
    return millis <= 0 ? null : Instant.ofEpochMilli(millis);
  }

  private static long millis(@Nullable Instant instant) {
    return instant == null ? 0L : instant.toEpochMilli();
  }
}
