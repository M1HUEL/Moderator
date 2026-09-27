package com.itson.moderator.config;

import com.itson.moderator.model.PunishmentType;
import com.itson.moderator.util.Durations;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Reads the {@code auto-punish} list out of config.yml.
 *
 * <p>Kept apart from {@link ModerationConfig} because it needs no plugin, which
 * makes the parsing of the shipped file testable.
 */
final class AutoPunishReader {

  private AutoPunishReader() {
  }

  /**
   * Reads every valid rule, ordered by descending threshold so the first match
   * is also the harshest.
   *
   * <p>A YAML list of mappings reaches the caller as a {@code Map}, not as a
   * {@code ConfigurationSection}, so both shapes are accepted here. Anything
   * unusable is skipped with a note in {@code problems} rather than thrown.
   */
  static List<AutoPunishRule> read(List<?> raw, List<String> problems) {
    List<AutoPunishRule> rules = new ArrayList<>();

    for (Object entry : raw) {
      ConfigurationSection section = asSection(entry);

      if (section == null) {
        problems.add("auto-punish: expected a mapping, ignoring the entry");

        continue;
      }

      int threshold = section.getInt("threshold", 0);

      if (threshold < 1) {
        problems.add("auto-punish.threshold: must be 1 or more, ignoring the rule");

        continue;
      }

      Set<PunishmentType> countTypes = EnumSet.noneOf(PunishmentType.class);

      for (String value : section.getStringList("count-types")) {
        PunishmentType.byId(value).ifPresent(countTypes::add);
      }

      if (countTypes.isEmpty()) {
        problems.add("auto-punish.count-types: no valid type listed, ignoring the rule");

        continue;
      }

      PunishmentType action = PunishmentType.byId(section.getString("action", "")).orElse(null);

      if (action == null) {
        problems.add("auto-punish.action: unknown punishment type, ignoring the rule");

        continue;
      }

      String durationText = section.getString("duration", "perm");
      Duration duration = null;

      if (!Durations.isPermanent(durationText)) {
        Optional<Duration> parsed = Durations.parse(durationText);

        if (parsed.isEmpty()) {
          problems.add("auto-punish.duration: '" + durationText + "' is not a valid duration, ignoring the rule");

          continue;
        }

        duration = parsed.get();
      }

      String reason = section.getString("reason");

      if (reason == null || reason.isBlank()) {
        problems.add("auto-punish.reason: missing reason, ignoring the rule");

        continue;
      }

      rules.add(new AutoPunishRule(threshold, Set.copyOf(countTypes), action, duration, reason,
          section.getString("permission"), section.getBoolean("silent", false)));
    }

    return rules.stream().sorted((left, right) -> Integer.compare(right.threshold(), left.threshold())).toList();
  }

  private static ConfigurationSection asSection(Object entry) {
    if (entry instanceof ConfigurationSection section) {
      return section;
    }

    if (!(entry instanceof Map<?, ?> map)) {
      return null;
    }

    YamlConfiguration wrapper = new YamlConfiguration();

    for (Map.Entry<?, ?> field : map.entrySet()) {
      if (field.getKey() != null) {
        wrapper.set(String.valueOf(field.getKey()), field.getValue());
      }
    }

    return wrapper;
  }
}
