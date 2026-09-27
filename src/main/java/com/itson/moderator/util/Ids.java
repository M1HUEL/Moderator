package com.itson.moderator.util;

import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Helpers for the identifiers staff and players type. */
public final class Ids {

  private static final int SHORT_LENGTH = 8;

  private Ids() {
  }

  /** Parses a dashed UUID, rejecting anything else. */
  public static Optional<UUID> parseUuid(@Nullable String raw) {
    if (raw == null || raw.length() != 36) {
      return Optional.empty();
    }

    try {
      return Optional.of(UUID.fromString(raw));
    } catch (IllegalArgumentException exception) {
      return Optional.empty();
    }
  }

  /** Parses a UUID without dashes, which is what Mojang APIs hand out. */
  public static Optional<UUID> parseUndashed(@Nullable String raw) {
    if (raw == null || raw.length() != 32) {
      return Optional.empty();
    }

    // The dashed form groups the 32 characters as 8-4-4-4-12, not 8-8-8-8.
    int[] bounds = {0, 8, 12, 16, 20, 32};
    StringBuilder builder = new StringBuilder(36);

    for (int index = 0; index < bounds.length - 1; index++) {
      if (index > 0) {
        builder.append('-');
      }

      builder.append(raw, bounds[index], bounds[index + 1]);
    }

    return parseUuid(builder.toString());
  }

  /**
   * The short prefix shown in chat and used by {@code /mod resolve}, for example
   * {@code 3f9a1c22} out of {@code 3f9a1c22-...}.
   */
  public static String shortId(@Nullable String id) {
    if (id == null || id.isBlank()) {
      return "unknown";
    }

    return id.length() <= SHORT_LENGTH ? id : id.substring(0, SHORT_LENGTH);
  }
}
