package com.itson.moderator.service;

import com.itson.moderator.config.ModerationConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;

/**
 * Per-player, per-action cooldowns, so staff cannot spam warnings into their own
 * history or players cannot flood the report queue.
 *
 * <p>Elapsed entries are dropped when read, which keeps the map from growing for
 * the whole uptime of the server. Durations come from
 * {@code settings.cooldowns} in config.yml; an action that is not listed is
 * unlimited.
 */
public final class CooldownService {

  private final Map<String, Map<UUID, Instant>> started = new ConcurrentHashMap<>();

  /**
   * Configured duration per action, replaceable on reload.
   *
   * <p>Defaults to "nothing is limited", which is the safe state before the config
   * has been read.
   */
  private volatile java.util.function.Function<String, Optional<Duration>> durations = action -> Optional.empty();

  /**
   * Points the service at the live config.
   *
   * <p>Kept as a function of the config rather than a copied map so a reload
   * changes the cooldowns without anyone having to rebind by hand.
   */
  public void bind(@NotNull ModerationConfig config) {
    this.durations = config::cooldown;
  }

  /**
   * Remaining cooldown for an action, empty when the player may act now.
   *
   * <p>Reading does not start the cooldown: the caller starts it once the action
   * really ran, otherwise a rejected attempt would lock the staff member out.
   */
  public Optional<Duration> remaining(@NotNull UUID player, @NotNull String action, @NotNull Instant now) {
    Optional<Duration> configured = durations.apply(action);

    if (configured.isEmpty()) {
      return Optional.empty();
    }

    Map<UUID, Instant> perPlayer = started.get(action);

    if (perPlayer == null) {
      return Optional.empty();
    }

    Instant since = perPlayer.get(player);

    if (since == null) {
      return Optional.empty();
    }

    Duration elapsed = Duration.between(since, now);

    if (elapsed.compareTo(configured.get()) >= 0) {
      perPlayer.remove(player, since);

      return Optional.empty();
    }

    return Optional.of(configured.get().minus(elapsed));
  }

  /** Starts the cooldown for an action that just ran. */
  public void start(@NotNull UUID player, @NotNull String action, @NotNull Instant now) {
    if (durations.apply(action).isEmpty()) {
      return;
    }

    started.computeIfAbsent(action, key -> new ConcurrentHashMap<>()).put(player, now);
  }

  /** Forgets a player's cooldowns, so staff are not locked out after a reload. */
  public void clear(@NotNull UUID player) {
    for (Map<UUID, Instant> perPlayer : started.values()) {
      perPlayer.remove(player);
    }
  }

  /** Empties every action, on reload or shutdown. */
  public void reset() {
    started.clear();
  }
}
