package com.itson.moderator.config;

import com.itson.moderator.model.PunishmentType;
import com.itson.moderator.util.Durations;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One entry of {@code auto-punish}: when a player collects enough active
 * sanctions, apply another one automatically.
 *
 * <p>Counting is explicit through {@link #countTypes()} so a server can, for
 * example, let warnings and mutes add up while kicks stay out of the maths.
 *
 * @param threshold  how many active counted punishments trigger the rule
 * @param countTypes which punishment types add towards the threshold
 * @param action     the punishment to apply
 * @param duration   how long it lasts, {@code null} for permanent
 * @param reason     reason stored on the automatic punishment
 * @param permission extra permission required to trigger the rule
 * @param silent     when true the automatic punishment is not broadcast
 */
public record AutoPunishRule(
    int threshold,
    Set<PunishmentType> countTypes,
    PunishmentType action,
    @Nullable Duration duration,
    String reason,
    @Nullable String permission,
    boolean silent) {

  public AutoPunishRule {
    if (threshold < 1) {
      throw new IllegalArgumentException("threshold must be at least 1");
    }

    if (countTypes == null || countTypes.isEmpty()) {
      throw new IllegalArgumentException("countTypes must not be empty");
    }

    if (action == null) {
      throw new IllegalArgumentException("action must not be null");
    }

    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("reason must not be blank");
    }
  }

  public boolean counts(@NotNull PunishmentType type) {
    return countTypes.contains(type);
  }

  public boolean canTrigger(@NotNull org.bukkit.command.CommandSender staff) {
    return permission == null || permission.isBlank() || staff.hasPermission(permission);
  }

  /**
   * Picks the rule to apply for the given number of active counted sanctions.
   *
   * <p>The most severe match wins, which is why the list is ordered by descending
   * threshold before the first match is taken: hitting four sanctions should
   * trigger the 4-ban rule and not the 3-mute rule on top of it.
   */
  public static Optional<AutoPunishRule> select(List<AutoPunishRule> rules, int activeCount) {
    return rules.stream()
        .filter(rule -> activeCount >= rule.threshold)
        .max((left, right) -> Integer.compare(left.threshold, right.threshold));
  }

  /** Renders the configured duration for messages. */
  public String lifetime() {
    return duration == null ? "permanent" : Durations.format(duration);
  }
}
