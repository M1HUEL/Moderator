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

  public void put(@NotNull Punishment mute) {
    active.put(mute.target(), mute);
  }

  public void remove(@NotNull UUID target) {
    active.remove(target);
  }

  public Optional<Punishment> activeMute(@NotNull UUID target) {
    return Optional.ofNullable(active.get(target));
  }

  public boolean isMuted(@NotNull UUID target) {
    return active.containsKey(target);
  }

  public int size() {
    return active.size();
  }

  public void clear() {
    active.clear();
  }
}
