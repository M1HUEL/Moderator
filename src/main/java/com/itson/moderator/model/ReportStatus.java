package com.itson.moderator.model;

import java.util.Locale;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/**
 * Lifecycle of a player report.
 *
 * <p>Resolved and dismissed are kept apart on purpose: one means staff acted, the
 * other means the report did not hold up. Collapsing them would make the queue
 * look like it was being worked when it was being dismissed.
 */
public enum ReportStatus {

  /** Still waiting for a moderator. */
  OPEN("open", "open"),

  /** Acted on. */
  RESOLVED("resolved", "resolved"),

  /** Reviewed and found to be not worth acting on. */
  DISMISSED("dismissed", "dismissed");

  /** Stable id used in {@code data.yml} and in commands, so renaming a constant is safe. */
  private final String id;

  /** Shown in chat. */
  private final String label;

  ReportStatus(String id, String label) {
    this.id = id;
    this.label = label;
  }

  /** The persisted id, for YAML and for command arguments. */
  public String id() {
    return id;
  }

  /** The human readable name shown in chat. */
  public String label() {
    return label;
  }

  /** Whether the report is still waiting. */
  public boolean isOpen() {
    return this == OPEN;
  }

  /**
   * Parses a status id, case insensitively.
   *
   * @return the status, or empty when the id is unknown or absent
   */
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
