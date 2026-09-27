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

  public Messages messages() {
    return messages;
  }

  public List<String> problems() {
    return List.copyOf(problems);
  }

  /** Reasons in declaration order, so tab completion lists them predictably. */
  public List<Reason> reasons() {
    return List.copyOf(reasons.values());
  }

  public List<String> reasonIds() {
    return List.copyOf(reasons.keySet());
  }

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

  public List<AutoPunishRule> autoPunish() {
    return autoPunish;
  }

  public boolean broadcastPunishments() {
    return broadcastPunishments;
  }

  public boolean broadcastReports() {
    return broadcastReports;
  }

  public boolean autoUnbanOnExpiry() {
    return autoUnbanOnExpiry;
  }

  public boolean muteBlocksCommands() {
    return muteBlocksCommands;
  }

  public boolean freezeBlocksInteractions() {
    return freezeBlocksInteractions;
  }

  public boolean notifyOnLogin() {
    return notifyOnLogin;
  }

  public int historyLimit() {
    return historyLimit;
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

  private List<AutoPunishRule> readAutoPunish(FileConfiguration config) {
    return AutoPunishReader.read(config.getList("auto-punish", List.of()), problems);
  }

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
