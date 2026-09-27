package com.itson.moderator.service;

import com.itson.moderator.model.Punishment;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;

/**
 * A hot cache of the mutes currently in force.
 *
 * <p>Chat arrives on async threads, so the chat listener cannot afford to walk
 * the whole history on every message. This map is read from those threads, which
 * is why it is concurrent; everything it holds is also on disk, so a stale
 * entry after a crash is repaired on the next load.
 */
public final class MuteRegistry {

  private final Map<UUID, Punishment> active = new ConcurrentHashMap<>();

  /** Caches a mute that is in force, replacing any earlier one for the same player. */
  public void put(@NotNull Punishment mute) {
    active.put(mute.target(), mute);
  }

  /** Drops the cached mute, on unmute, expiry or revoke. */
  public void remove(@NotNull UUID target) {
    active.remove(target);
  }

  /** The cached mute, for the message that tells a muted player why. */
  public Optional<Punishment> activeMute(@NotNull UUID target) {
    return Optional.ofNullable(active.get(target));
  }

  /** Whether the player is muted, the check the chat listener makes per message. */
  public boolean isMuted(@NotNull UUID target) {
    return active.containsKey(target);
  }

  /** How many mutes are in force, reported by {@code /mod reload}. */
  public int size() {
    return active.size();
  }

  /** Empties the cache, so a reload can rebuild it from disk. */
  public void clear() {
    active.clear();
  }
}
