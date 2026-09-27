package com.itson.moderator.model;

import java.time.Instant;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * What the plugin remembers about a player between sessions.
 *
 * <p>Kept deliberately small: the last known name and IP are what make sanctions
 * still reachable after a player renames or reconnects from another connection.
 */
public record PlayerRecord(UUID id, String name, @Nullable String lastIp, Instant firstSeen, Instant lastSeen) {

  public PlayerRecord {
    if (id == null) {
      throw new IllegalArgumentException("id must not be null");
    }

    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }

    if (firstSeen == null || lastSeen == null) {
      throw new IllegalArgumentException("firstSeen and lastSeen must not be null");
    }
  }

  public static PlayerRecord of(UUID id, String name, @Nullable String lastIp, Instant now) {
    return new PlayerRecord(id, name, lastIp, now, now);
  }

  /** Returns a copy refreshed after a join, preserving the original first-seen. */
  public PlayerRecord seen(String newName, @Nullable String newIp, Instant now) {
    return new PlayerRecord(id, newName, newIp, firstSeen, now);
  }
}
