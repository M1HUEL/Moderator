package com.itson.moderator.model;

import java.time.Instant;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A report filed by a player against another player.
 *
 * <p>The short {@link #id()} is what staff type in {@code /mod resolve}, so it is
 * generated independently of the reporter and never reused.
 */
public record Report(
    String id,
    UUID target,
    String targetName,
    UUID reporter,
    String reporterName,
    String reasonId,
    @Nullable String details,
    Instant createdAt,
    ReportStatus status,
    @Nullable String handledBy,
    @Nullable String resolution) {

  public Report {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("id must not be blank");
    }

    if (target == null || reporter == null) {
      throw new IllegalArgumentException("target and reporter must not be null");
    }

    if (reasonId == null || reasonId.isBlank()) {
      throw new IllegalArgumentException("reasonId must not be blank");
    }

    if (status == null) {
      throw new IllegalArgumentException("status must not be null");
    }

    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt must not be null");
    }
  }

  public static Report open(String id, UUID target, String targetName, UUID reporter, String reporterName,
      String reasonId, @Nullable String details, Instant createdAt) {
    return new Report(id, target, targetName, reporter, reporterName, reasonId, details, createdAt, ReportStatus.OPEN,
        null, null);
  }

  public boolean isOpen() {
    return status.isOpen();
  }

  public boolean involves(@NotNull UUID player) {
    return target.equals(player) || reporter.equals(player);
  }

  /** Returns a copy closed with the given outcome. */
  public Report close(ReportStatus newStatus, @Nullable String handler, @Nullable String note) {
    if (newStatus == ReportStatus.OPEN) {
      throw new IllegalArgumentException("a closed report cannot stay open");
    }

    return new Report(id, target, targetName, reporter, reporterName, reasonId, details, createdAt, newStatus, handler,
        note);
  }
}
