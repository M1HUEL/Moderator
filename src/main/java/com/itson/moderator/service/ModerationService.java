package com.itson.moderator.service;

import com.itson.moderator.config.AutoPunishRule;
import com.itson.moderator.config.ModerationConfig;
import com.itson.moderator.model.Punishment;
import com.itson.moderator.model.PunishmentType;
import com.itson.moderator.model.Report;
import com.itson.moderator.model.ReportStatus;
import com.itson.moderator.model.Target;
import com.itson.moderator.storage.ModerationStore;
import com.itson.moderator.util.Addresses;
import com.itson.moderator.util.Ids;
import com.itson.moderator.util.Text;
import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.ban.BanListType;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The single place where a moderation decision turns into stored data and into
 * server side effects.
 *
 * <p>Commands and listeners only read from here. Everything that touches the
 * vanilla ban lists, kicks a player, or writes to the store goes through
 * {@link #apply}, which is also the single place where auto-punish is evaluated.
 */
public final class ModerationService {

  /** Name shown as the author of punishments applied from the console. */
  public static final String CONSOLE_STAFF = "Console";

  /** Identifier used for punishments nobody typed, so they can be told apart. */
  public static final UUID CONSOLE_STAFF_ID =
      UUID.nameUUIDFromBytes(("Moderator:" + CONSOLE_STAFF).getBytes(StandardCharsets.UTF_8));

  private final JavaPlugin plugin;

  private final ModerationStore store;

  private final MuteRegistry mutes;

  private final PlayerRegistry players;

  private final Clock clock;

  private volatile ModerationConfig config;

  public ModerationService(@NotNull JavaPlugin plugin, @NotNull ModerationConfig config,
      @NotNull ModerationStore store, @NotNull MuteRegistry mutes, @NotNull PlayerRegistry players,
      @NotNull Clock clock) {
    this.plugin = plugin;
    this.config = config;
    this.store = store;
    this.mutes = mutes;
    this.players = players;
    this.clock = clock;
  }

  public void setConfig(@NotNull ModerationConfig config) {
    this.config = config;
  }

  public Instant now() {
    return clock.instant();
  }

  // ---------------------------------------------------------------- sanctions

  /**
   * Records a punishment and makes it take effect.
   *
   * @return the applied entry, or empty when an equivalent one is already active
   */
  public Optional<Outcome> apply(@NotNull Spec spec) {
    return apply(spec, true);
  }

  private Optional<Outcome> apply(Spec spec, boolean triggerAutoPunish) {
    List<Punishment> replaced = spec.type().allowsStack()
        ? List.of()
        : revoke(spec.target(), spec.type(), spec.staffName(), "Replaced by a newer punishment");

    Instant created = now();
    String id = UUID.randomUUID().toString();

    Punishment punishment = spec.expiresAt() == null
        ? Punishment.permanent(id, spec.target().id(), spec.target().name(), spec.target().ip(), spec.type(),
            spec.reasonId(), spec.reason(), spec.staffId(), spec.staffName(), created)
        : Punishment.temporary(id, spec.target().id(), spec.target().name(), spec.target().ip(), spec.type(),
            spec.reasonId(), spec.reason(), spec.staffId(), spec.staffName(), created, spec.expiresAt());

    rememberTarget(spec.target(), created);
    store.add(punishment);
    enforce(punishment);

    List<Punishment> followUps = triggerAutoPunish ? evaluateAutoPunish(spec.target()) : List.of();

    return Optional.of(new Outcome(punishment, replaced, followUps));
  }

  /**
   * Marks every active punishment of a type as revoked and undoes its effect.
   *
   * @return the entries that were revoked, newest first
   */
  public List<Punishment> revoke(@NotNull Target target, @NotNull PunishmentType type, @NotNull String staffName,
      @Nullable String note) {
    Instant now = now();
    List<Punishment> revoked = new ArrayList<>();

    for (Punishment punishment : store.punishments(target.id())) {
      if (punishment.type() != type || !punishment.isActiveAt(now)) {
        continue;
      }

      Punishment closed = punishment.revoke(now, staffName, note);

      store.update(closed);
      lift(closed);
      revoked.add(closed);
    }

    revoked.sort(Comparator.comparing(Punishment::createdAt).reversed());

    return revoked;
  }

  /** Drops punishments whose end time has passed, for example every minute. */
  public List<Punishment> expire() {
    Instant now = now();
    List<Punishment> expired = new ArrayList<>();

    for (Punishment punishment : store.punishments()) {
      if (!punishment.active() || !punishment.isExpiredAt(now)) {
        continue;
      }

      Punishment closed = punishment.expire();

      store.update(closed);
      lift(closed);
      expired.add(closed);
    }

    return expired;
  }

  /**
   * Counts the active sanctions of the given types, which is what auto-punish
   * thresholds are compared against.
   */
  public int countActive(@NotNull UUID target, @NotNull Iterable<PunishmentType> types) {
    Instant now = now();
    int count = 0;

    for (Punishment punishment : store.punishments(target)) {
      if (punishment.countsAt(now) && contains(types, punishment.type())) {
        count++;
      }
    }

    return count;
  }

  /** The active punishment of a type, if there is one. */
  public Optional<Punishment> active(@NotNull UUID target, @NotNull PunishmentType type) {
    Instant now = now();

    return store.punishments(target).stream()
        .filter(punishment -> punishment.type() == type)
        .filter(punishment -> punishment.isActiveAt(now))
        .max(Comparator.comparing(Punishment::createdAt));
  }

  public boolean typeActive(@NotNull UUID target, @NotNull PunishmentType type) {
    return active(target, type).isPresent();
  }

  public boolean isMuted(@NotNull UUID target) {
    return mutes.isMuted(target);
  }

  public Optional<Punishment> activeMute(@NotNull UUID target) {
    return mutes.activeMute(target);
  }

  /** Full history of a player, newest first. */
  public List<Punishment> history(@NotNull UUID target) {
    return store.punishments(target).stream()
        .sorted(Comparator.comparing(Punishment::createdAt).reversed())
        .toList();
  }

  public List<Punishment> history(@NotNull UUID target, @NotNull PunishmentType type) {
    return history(target).stream().filter(punishment -> punishment.type() == type).toList();
  }

  /** All notes ever left on a player. */
  public List<Punishment> notes(@NotNull UUID target) {
    return history(target, PunishmentType.NOTE);
  }

  // ------------------------------------------------------------------ reports

  public Report openReport(@NotNull Target target, @NotNull UUID reporter, @NotNull String reporterName,
      @NotNull String reasonId, @Nullable String details) {
    Instant now = now();
    Report report = Report.open(Ids.shortId(UUID.randomUUID().toString()), target.id(), target.name(), reporter,
        reporterName, reasonId, details, now);

    store.add(report);

    return report;
  }

  /** Closes a report, returning empty when the id is unknown. */
  public Optional<Report> resolveReport(@NotNull String id, @NotNull ReportStatus status, @NotNull String handler,
      @Nullable String resolution) {
    Optional<Report> existing = report(id);

    if (existing.isEmpty()) {
      return Optional.empty();
    }

    Report closed = existing.get().close(status, handler, resolution);

    store.update(closed);

    return Optional.of(closed);
  }

  /**
   * Finds a report by id, accepting any unambiguous prefix.
   *
   * <p>Ids are shown in chat shortened, so a moderator reading one out of the
   * queue types the first few characters rather than the whole thing. An exact
   * match always wins over a prefix, so a longer id can never be shadowed by a
   * shorter one that happens to start the same way.
   */
  public Optional<Report> report(@NotNull String id) {
    String needle = id.trim().toLowerCase(Locale.ROOT);

    if (needle.isEmpty()) {
      return Optional.empty();
    }

    Optional<Report> exact = store.reports().stream().filter(report -> report.id().equalsIgnoreCase(id)).findFirst();

    if (exact.isPresent()) {
      return exact;
    }

    List<Report> prefixed = store.reports().stream()
        .filter(report -> report.id().toLowerCase(Locale.ROOT).startsWith(needle))
        .toList();

    // A shared prefix is not a choice, so nothing is returned rather than a
    // coin flip between two reports.
    return prefixed.size() == 1 ? Optional.of(prefixed.get(0)) : Optional.empty();
  }

  public List<Report> reports(@NotNull ReportStatus status, int limit) {
    return reports(List.of(status), limit);
  }

  /** Reports in any of the given states, oldest first. */
  public List<Report> reports(@NotNull java.util.Collection<ReportStatus> statuses, int limit) {
    return store.reports().stream()
        .filter(report -> statuses.contains(report.status()))
        .sorted(Comparator.comparing(Report::createdAt))
        .limit(Math.max(1, limit))
        .toList();
  }

  public long countReports(@NotNull ReportStatus status) {
    return store.reports().stream().filter(report -> report.status() == status).count();
  }

  public List<Report> reportsInvolving(@NotNull UUID player, int limit) {
    return store.reports().stream()
        .filter(report -> report.involves(player))
        .sorted(Comparator.comparing(Report::createdAt).reversed())
        .limit(Math.max(1, limit))
        .toList();
  }

  // -------------------------------------------------------------- maintenance

  /** Saves pending changes. */
  public void save() {
    store.save();
  }

  public void registerPlayer(@NotNull Player player) {
    players.seen(player.getUniqueId(), player.getName(), address(player), now());
  }

  /**
   * Rebuilds the mute cache from the store, used on enable and after a reload so
   * nobody walks in un-muted.
   */
  public void reloadMutes() {
    mutes.clear();

    Instant now = now();

    for (Punishment punishment : store.punishments()) {
      if (punishment.type() == PunishmentType.MUTE && punishment.isActiveAt(now)) {
        mutes.put(punishment);
      }
    }
  }

  // ------------------------------------------------------------------ helpers

  /**
   * Brings the vanilla ban lists back in line with the history, on enable.
   *
   * <p>The store and the ban lists are two pieces of state kept in two files, and
   * they drift apart in ways that matter:
   *
   * <ul>
   * <li>A ban that is active in the history but missing from the list, because
   * somebody ponsed the player by hand or another plugin touched the list, leaves
   * the staff member who issued it believing the player is banned. The entry is
   * added back.
   * <li>A ban that was revoked or that expired while the server was down is still
   * in the list, which is the worse case: the player cannot join and nothing in
   * {@code /mod history} explains why. The entry is lifted.
   * </ul>
   *
   * <p>Only entries this plugin put there are lifted. A ban whose source or reason
   * does not match our record was placed by somebody else, by the owner or by
   * another plugin, and is reported as a conflict rather than undone: a plugin
   * that quietly unbans on a guess is a plugin that unblocks the wrong person.
   *
   * @return what the pass had to change
   */
  public BanListReconciliation reconcileBanLists() {
    Instant now = now();
    int reasserted = 0;
    int lifted = 0;
    int conflicts = 0;

    for (Punishment punishment : store.punishments()) {
      if (!punishment.type().isBanListBacked()) {
        continue;
      }

      if (punishment.type() == PunishmentType.BAN_IP) {
        Optional<InetAddress> address = Addresses.parse(punishment.targetIp());

        if (address.isEmpty()) {
          continue;
        }

        BanList<InetAddress> list = Bukkit.getBanList(BanListType.IP);
        boolean listed = list.isBanned(address.get());

        if (punishment.isActiveAt(now)) {
          if (listed) {
            continue;
          }

          banAddress(punishment);
          reasserted++;
          logReconciled("re-applied the IP ban of", punishment);
        } else if (listed) {
          if (!ours(list.getBanEntry(address.get()), punishment)) {
            conflicts++;
            logConflict(punishment);

            continue;
          }

          pardonAddress(punishment);
          lifted++;
          logReconciled("lifted the finished IP ban of", punishment);
        }

        continue;
      }

      BanList<PlayerProfile> list = Bukkit.getBanList(BanListType.PROFILE);
      PlayerProfile profile = Bukkit.createProfile(punishment.target(), punishment.targetName());
      boolean listed = list.isBanned(profile);

      if (punishment.isActiveAt(now)) {
        if (listed) {
          continue;
        }

        banProfile(punishment);
        reasserted++;
        logReconciled("re-applied the ban of", punishment);
      } else if (listed) {
        if (!ours(list.getBanEntry(profile), punishment)) {
          conflicts++;
          logConflict(punishment);

          continue;
        }

        pardonProfile(punishment);
        lifted++;
        logReconciled("lifted the finished ban of", punishment);
      }
    }

    return new BanListReconciliation(reasserted, lifted, conflicts);
  }

  /**
   * Whether a ban list entry is the one this plugin wrote for a given sanction.
   *
   * <p>Compared on the staff member and the reason, because those are exactly the
   * two fields the plugin supplies. A match means nobody has overwritten the entry
   * since, so removing it is undoing our own work and not somebody else's.
   */
  private boolean ours(org.bukkit.BanEntry<?> entry, @NotNull Punishment punishment) {
    if (entry == null) {
      return false;
    }

    return punishment.staffName().equals(entry.getSource())
        && vanillaReason(punishment).equals(entry.getReason());
  }

  private void logReconciled(@NotNull String action, @NotNull Punishment punishment) {
    plugin.getLogger().info("Ban lists: " + action + " " + punishment.targetName() + " ("
        + punishment.type().label() + ").");
  }

  private void logConflict(@NotNull Punishment punishment) {
    plugin.getLogger().warning("Ban lists: " + punishment.targetName() + " is banned in the vanilla list by "
        + "something other than this plugin, but the history says the sanction is over. Left it in place, "
        + "unban it by hand if the server owner meant to keep it.");
  }

  /**
   * What a pass over the ban lists changed.
   *
   * @param reasserted active sanctions that were missing from the list
   * @param lifted     finished sanctions that were still in the list
   * @param conflicts  entries somebody else owns, reported and left alone
   */
  public record BanListReconciliation(int reasserted, int lifted, int conflicts) {
  }

  private List<Punishment> evaluateAutoPunish(Target target) {
    Instant now = now();
    List<Punishment> applied = new ArrayList<>();

    for (AutoPunishRule rule : config.autoPunish()) {
      int count = countActive(target.id(), rule.countTypes());

      if (count < rule.threshold() || typeActive(target.id(), rule.action())) {
        continue;
      }

      Spec spec = new Spec(target, rule.action(), null, rule.reason(), CONSOLE_STAFF_ID, CONSOLE_STAFF,
          rule.duration() == null ? null : now.plus(rule.duration()), true);

      apply(spec, false).ifPresent(outcome -> applied.add(outcome.primary()));
    }

    return applied;
  }

  private void rememberTarget(Target target, Instant now) {
    players.seen(target.id(), target.name(), target.ip(), now);
  }

  /**
   * Makes a stored punishment real: cache entry, vanilla ban list, and the
   * immediate reaction of a player who is connected right now.
   */
  private void enforce(Punishment punishment) {
    switch (punishment.type()) {
      case MUTE -> mutes.put(punishment);
      case BAN -> banProfile(punishment);
      case BAN_IP -> banAddress(punishment);
      default -> {
        // WARN, KICK and NOTE are not held anywhere, they only need a reaction.
      }
    }

    Player online = Bukkit.getPlayer(punishment.target());

    if (online == null) {
      return;
    }

    switch (punishment.type()) {
      case BAN, BAN_IP, KICK -> online.kick(screen(punishment));
      case MUTE, WARN -> online.sendMessage(screen(punishment));
      case NOTE -> {
        // Notes are staff side only.
      }
    }
  }

  private void lift(Punishment punishment) {
    switch (punishment.type()) {
      case MUTE -> mutes.remove(punishment.target());
      case BAN -> pardonProfile(punishment);
      case BAN_IP -> pardonAddress(punishment);
      default -> {
        // Nothing to undo.
      }
    }
  }

  private void banProfile(Punishment punishment) {
    BanListType<org.bukkit.ban.ProfileBanList> type = BanListType.PROFILE;

    Bukkit.getBanList(type).addBan(Bukkit.createProfile(punishment.target(), punishment.targetName()),
        vanillaReason(punishment), punishment.expiresAt(), punishment.staffName());
  }

  private void pardonProfile(Punishment punishment) {
    try {
      BanListType<org.bukkit.ban.ProfileBanList> type = BanListType.PROFILE;

      Bukkit.getBanList(type).pardon(Bukkit.createProfile(punishment.target(), punishment.targetName()));
    } catch (RuntimeException exception) {
      plugin.getLogger().warning("Could not lift the ban of " + punishment.targetName() + ": " + exception.getMessage());
    }
  }

  private void banAddress(Punishment punishment) {
    Optional<InetAddress> address = Addresses.parse(punishment.targetIp());

    if (address.isEmpty()) {
      plugin.getLogger().warning("No usable address recorded for " + punishment.targetName()
          + ", the IP ban was only stored in the history");

      return;
    }

    Bukkit.getBanList(BanListType.IP).addBan(address.get(), vanillaReason(punishment), punishment.expiresAt(),
        punishment.staffName());
  }

  private void pardonAddress(Punishment punishment) {
    Addresses.parse(punishment.targetIp()).ifPresent(address -> {
      try {
        Bukkit.getBanList(BanListType.IP).pardon(address);
      } catch (RuntimeException exception) {
        plugin.getLogger().warning("Could not lift the IP ban of " + punishment.targetName() + ": "
            + exception.getMessage());
      }
    });
  }

  /** The coloured screen shown to a player who is online right now. */
  private Component screen(Punishment punishment) {
    return config.messages().render("punish." + punishment.type().id() + "-screen", tags(punishment));
  }

  /**
   * The plain text stored in the vanilla ban list. The vanilla screen is not a
   * MiniMessage renderer, so this is a separate template without colour codes.
   */
  private String vanillaReason(Punishment punishment) {
    return config.messages().renderPlain("vanilla-reason", tags(punishment));
  }

  private static Object[] tags(Punishment punishment) {
    return new Object[] {
        "reason", punishment.reason(),
        "staff", punishment.staffName(),
        "duration", punishment.lifetimeAt(punishment.createdAt()),
        "date", Text.timestamp(punishment.createdAt())};
  }

  private static boolean contains(Iterable<PunishmentType> types, PunishmentType type) {
    for (PunishmentType candidate : types) {
      if (candidate == type) {
        return true;
      }
    }

    return false;
  }

  private static String address(Player player) {
    return player.getAddress() == null ? null : player.getAddress().getAddress().getHostAddress();
  }

  /**
   * Everything needed to record a sanction.
   *
   * @param silent true for automated punishments and staff that ticked the flag
   */
  public record Spec(Target target, PunishmentType type, @Nullable String reasonId, String reason, UUID staffId,
      String staffName, @Nullable Instant expiresAt, boolean silent) {

    public Spec {
      if (target == null || type == null) {
        throw new IllegalArgumentException("target and type must not be null");
      }

      if (reason == null || reason.isBlank()) {
        throw new IllegalArgumentException("reason must not be blank");
      }

      if (staffName == null || staffName.isBlank()) {
        throw new IllegalArgumentException("staffName must not be blank");
      }
    }
  }

  /**
   * A punishment that was applied, plus what it replaced and what it triggered.
   *
   * @param replaced  an active punishment of the same type that was lifted first
   * @param followUps automated sanctions, which staff should be told about
   */
  public record Outcome(Punishment primary, List<Punishment> replaced, List<Punishment> followUps) {

    public Outcome {
      replaced = replaced == null ? List.of() : List.copyOf(replaced);
      followUps = followUps == null ? List.of() : List.copyOf(followUps);
    }
  }
}
