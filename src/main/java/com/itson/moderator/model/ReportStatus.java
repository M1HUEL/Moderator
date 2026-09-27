package com.itson.moderator.model;

import java.util.Locale;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/** Lifecycle of a player report. */
public enum ReportStatus {

  OPEN("open", "open"),
  RESOLVED("resolved", "resolved"),
  DISMISSED("dismissed", "dismissed");

  private final String id;

  private final String label;

  ReportStatus(String id, String label) {
    this.id = id;
    this.label = label;
  }

  public String id() {
    return id;
  }

  public String label() {
    return label;
  }

  public boolean isOpen() {
    return this == OPEN;
  }

  public static Optional<ReportStatus> byId(@Nullable String raw) {
    if (raw == null) {
      return Optional.empty();
    }

    String normalized = raw.toLowerCase(Locale.ROOT);

    for (ReportStatus status : values()) {
      if (status.id.equals(normalized)) {
        return Optional.of(status);
      }
    }

    return Optional.empty();
  }
}
