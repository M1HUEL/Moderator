package com.itson.moderator.model;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * A resolved command argument: the player a command should act on, whether or
 * not that player is connected right now.
 *
 * @param id     the authoritative identifier, used for every stored record
 * @param name   the most recent name, shown to staff even when offline
 * @param ip     last known address, used by {@code /mod banip}
 * @param online whether the player is currently connected
 */
public record Target(UUID id, String name, @Nullable String ip, boolean online) {

  public Target {
    if (id == null) {
      throw new IllegalArgumentException("id must not be null");
    }

    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
  }

  public static Target of(UUID id, String name, @Nullable String ip) {
    return new Target(id, name, ip, false);
  }
}
