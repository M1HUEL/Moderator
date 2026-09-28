package com.itson.moderator.config;

import com.itson.moderator.model.PunishmentType;
import com.itson.moderator.util.Durations;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Reads config.yml into an immutable snapshot.
 *
 * <p>Everything the rest of the plugin needs at runtime comes from here, so
 * {@code /mod reload} only has to build a new instance and swap it. Bad values
 * fall back to a sane default and are reported on the console instead of
 * throwing, because a typo in a config should not take the server down.
 */
public final class ModerationConfig {

  private final List<String> problems = new ArrayList<>();

  private Messages messages = Messages.empty();

  private Map<String, Reason> reasons = Map.of();

  private List<AutoPunishRule> autoPunish = List.of();

  private Set<String> mutedAllowedCommands = Set.of();

  private Map<String, Duration> cooldowns = Map.of();

  private boolean broadcastPunishments = true;

  private boolean broadcastReports = true;

  private boolean autoUnbanOnExpiry = true;

  private boolean muteBlocksCommands = true;

  private boolean freezeBlocksInteractions = true;

  private boolean notifyOnLogin = true;

  private int historyLimit = 20;

  /**
   * Days of finished sanctions to keep, or zero to keep all of them.
   *
   * <p>Default is zero, so nothing is deleted unless an owner asks for it. A
   * moderation history is the record a ban appeal, an abuse report or a staff
   * mistake is settled with, and quietly discarding it is not a decision a plugin
   * should make on its own.
   */
  private int retentionDays;

  /**
   * Rereads config.yml and replaces the whole snapshot at once.
   *
   * <p>Rebuilding instead of mutating is what makes {@code /mod reload} safe: a
   * command in flight either sees the old config or the new one, never half of
   * each.
   */
  public void load(@NotNull JavaPlugin plugin) {
    problems.clear();

    plugin.saveDefaultConfig();
    plugin.reloadConfig();

    FileConfiguration config = plugin.getConfig();

    broadcastPunishments = config.getBoolean("settings.broadcast-punishments", true);
    broadcastReports = config.getBoolean("settings.broadcast-reports", true);
    autoUnbanOnExpiry = config.getBoolean("settings.auto-unban-on-expiry", true);
    muteBlocksCommands = config.getBoolean("settings.muted-blocks-commands", true);
    freezeBlocksInteractions = config.getBoolean("settings.freeze-blocks-interactions", true);
    notifyOnLogin = config.getBoolean("settings.notify-on-login", true);

    historyLimit = Math.max(1, config.getInt("settings.history-limit", 20));
    retentionDays = Math.max(0, config.getInt("settings.retention-days", 0));

    messages = new Messages(readMessages(config));
    reasons = readReasons(config);
    autoPunish = readAutoPunish(config);
    mutedAllowedCommands = readLowerCaseSet(config.getStringList("settings.muted-allowed-commands"));
    cooldowns = readCooldowns(config.getConfigurationSection("settings.cooldowns"));

    if (problems.isEmpty()) {
      return;
    }

    plugin.getLogger().warning("Config problems, defaults were used:");

    for (String problem : problems) {
      plugin.getLogger().warning("  - " + problem);
    }
  }

  /** The message renderer, already built from the messages section. */
  public Messages messages() {
    return messages;
  }

  /**
   * Everything that had to be ignored or defaulted while reading, for the
   * console and for {@code /mod reload}.
   *
   * <p>Empty means the file was read exactly as written.
   */
  public List<String> problems() {
    return List.copyOf(problems);
  }

  /** Reasons in declaration order, so tab completion lists them predictably. */
  public List<Reason> reasons() {
    return List.copyOf(reasons.values());
  }

  /** Every configured reason id, used for tab completion of the reason argument. */
  public List<String> reasonIds() {
    return List.copyOf(reasons.keySet());
  }

  /**
   * Looks a reason up by its configured id, case-insensitively.
   *
   * <p>Empty means the staff member typed free text instead of a known id, which
   * is allowed and stored as the reason text itself.
   */
  public Optional<Reason> reason(String id) {
    if (id == null) {
      return Optional.empty();
    }

    return Optional.ofNullable(reasons.get(id.toLowerCase(Locale.ROOT)));
  }

  /** Reason ids offered for a type, for tab completion. */
  public List<String> reasonIdsFor(PunishmentType type) {
    return reasons.values().stream().filter(reason -> reason.supports(type)).map(Reason::id).toList();
  }

  /**
   * The automatic punishment rules, ordered by descending threshold.
   *
   * <p>{@code ModerationService} relies on that order when it picks which rule a
   * player has just crossed.
   */
  public List<AutoPunishRule> autoPunish() {
    return autoPunish;
  }

  /** Whether a sanction is announced to everyone. */
  public boolean broadcastPunishments() {
    return broadcastPunishments;
  }

  /** Whether a filed report is announced to everyone. */
  public boolean broadcastReports() {
    return broadcastReports;
  }

  /**
   * Whether a ban is lifted from the vanilla list once it runs out.
   *
   * <p>On by default, since the vanilla list expires bans on its own anyway; the
   * setting exists for servers that also want the plugin to pardon explicitly.
   */
  public boolean autoUnbanOnExpiry() {
    return autoUnbanOnExpiry;
  }

  /** Whether a muted player is stopped from running commands, not just from chatting. */
  public boolean muteBlocksCommands() {
    return muteBlocksCommands;
  }

  /** Whether a frozen player is stopped from moving, interacting and using commands. */
  public boolean freezeBlocksInteractions() {
    return freezeBlocksInteractions;
  }

  /** Whether staff are told what a joining player is still banned or muted for. */
  public boolean notifyOnLogin() {
    return notifyOnLogin;
  }

  /**
   * How many rows the history view shows.
   *
   * <p>Floored at one, so a mistaken zero does not leave staff with an empty
   * screen and no clue why.
   */
  public int historyLimit() {
    return historyLimit;
  }

  /**
   * How long finished sanctions are kept, or zero to keep them all.
   *
   * @see #retentionDays
   */
  public int retentionDays() {
    return retentionDays;
  }

  /**
   * Whether a muted player may still run a command. Entries are matched loosely
   * so {@code login} also allows {@code /essentials:login} and {@code /l}.
   */
  public boolean isCommandAllowedWhileMuted(String rawCommand) {
    if (rawCommand == null || rawCommand.isBlank()) {
      return false;
    }

    String command = rawCommand.startsWith("/") ? rawCommand.substring(1) : rawCommand;

    if (command.isBlank()) {
      return false;
    }

    int colon = command.indexOf(':');
    String plain = colon < 0 ? command : command.substring(colon + 1);

    return mutedAllowedCommands.contains(command) || mutedAllowedCommands.contains(plain);
  }

  /** Cooldown for a command id such as {@code warn}, empty when unlimited. */
  public Optional<Duration> cooldown(String id) {
    return Optional.ofNullable(cooldowns.get(id));
  }

  /**
   * Flattens the messages section into key to template pairs.
   *
   * <p>Only string leaves are taken. Nested keys are flattened with dots, which
   * is what lets a config write {@code punish.mute-screen} and the code ask for
   * exactly that name.
   */
  private Map<String, String> readMessages(FileConfiguration config) {
    Map<String, String> map = new LinkedHashMap<>();

    ConfigurationSection section = config.getConfigurationSection("messages");

    if (section == null) {
      problems.add("messages: section is missing, every message will fall back to a placeholder");

      return map;
    }

    for (String key : section.getKeys(true)) {
      if (section.isString(key)) {
        map.put(key, section.getString(key, ""));
      }
    }

    return map;
  }

  /**
   * Reads the reasons section, keyed by the id staff type.
   *
   * <p>A reason without a label is dropped rather than shown blank, and an
   * {@code applies} list that names an unknown type simply loses that type, so a
   * typo in one entry does not cost the whole config.
   */
  private Map<String, Reason> readReasons(FileConfiguration config) {
    Map<String, Reason> map = new LinkedHashMap<>();

    ConfigurationSection section = config.getConfigurationSection("reasons");

    if (section == null) {
      problems.add("reasons: section is missing, only free text reasons will be accepted");

      return map;
    }

    for (String id : section.getKeys(false)) {
      ConfigurationSection entry = section.getConfigurationSection(id);

      if (entry == null) {
        problems.add("reasons." + id + ": expected a section, ignoring it");

        continue;
      }

      String label = entry.getString("label");

      if (label == null || label.isBlank()) {
        problems.add("reasons." + id + ".label: missing label, ignoring the reason");

        continue;
      }

      Set<PunishmentType> applies = EnumSet.noneOf(PunishmentType.class);

      for (String raw : entry.getStringList("applies")) {
        Optional<PunishmentType> type = PunishmentType.byId(raw);

        if (type.isEmpty()) {
          problems.add("reasons." + id + ".applies: unknown punishment type '" + raw + "'");
        } else {
          applies.add(type.get());
        }
      }

      map.put(id.toLowerCase(Locale.ROOT), new Reason(id.toLowerCase(Locale.ROOT), label, Set.copyOf(applies)));
    }

    return Map.copyOf(map);
  }

  /**
   * Reads the automatic punishment rules.
   *
   * <p>The parsing lives in {@link AutoPunishReader} so it can be tested without
   * a plugin instance.
   */
  private List<AutoPunishRule> readAutoPunish(FileConfiguration config) {
    return AutoPunishReader.read(config.getList("auto-punish", List.of()), problems);
  }

  /**
   * Reads per-command cooldowns as seconds, keyed by command id and lower cased.
   *
   * <p>A zero or negative entry means no cooldown, so it is left out rather than
   * stored as a duration of nothing.
   */
  private Map<String, Duration> readCooldowns(ConfigurationSection section) {
    Map<String, Duration> map = new LinkedHashMap<>();

    if (section == null) {
      return map;
    }

    for (String id : section.getKeys(false)) {
      long seconds = section.getLong(id, 0L);

      if (seconds <= 0) {
        continue;
      }

      map.put(id.toLowerCase(Locale.ROOT), Duration.ofSeconds(seconds));
    }

    return Map.copyOf(map);
  }

  /**
   * Normalises a list of command names, dropping any leading slash and blank
   * entries, and lower casing what is left.
   */
  private static Set<String> readLowerCaseSet(List<String> values) {
    Set<String> set = new LinkedHashSet<>();

    for (String value : values) {
      String cleaned = value.startsWith("/") ? value.substring(1) : value;

      if (!cleaned.isBlank()) {
        set.add(cleaned.toLowerCase(Locale.ROOT));
      }
    }

    return Set.copyOf(set);
  }
}
