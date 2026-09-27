package com.itson.moderator.model;

import com.itson.moderator.util.Durations;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An immutable entry of a player moderation history.
 *
 * <p>A punishment is <em>active</em> when it has not been revoked and has not run
 * out of time. Expired or revoked entries stay in the history for auditing, only
 * {@link #active()} flips to {@code false}.
 */
public record Punishment(
    String id,
    UUID target,
    String targetName,
    @Nullable String targetIp,
    PunishmentType type,
    @Nullable String reasonId,
    String reason,
    UUID staff,
    String staffName,
    Instant createdAt,
    @Nullable Instant expiresAt,
    boolean active,
    @Nullable Instant revokedAt,
    @Nullable String revokedBy,
    @Nullable String revokeReason) {

  public Punishment {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("id must not be blank");
    }

    if (target == null) {
      throw new IllegalArgumentException("target must not be null");
    }

    if (type == null) {
      throw new IllegalArgumentException("type must not be null");
    }

    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt must not be null");
    }

    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("reason must not be blank");
    }
  }

  /** Creates a fresh, active punishment that never expires. */
  public static Punishment permanent(String id, UUID target, String targetName, @Nullable String targetIp,
      PunishmentType type, @Nullable String reasonId, String reason, UUID staff, String staffName, Instant createdAt) {
    return of(id, target, targetName, targetIp, type, reasonId, reason, staff, staffName, createdAt, null);
  }

  /** Creates a fresh, active punishment that expires at the given instant. */
  public static Punishment temporary(String id, UUID target, String targetName, @Nullable String targetIp,
      PunishmentType type, @Nullable String reasonId, String reason, UUID staff, String staffName, Instant createdAt,
      Instant expiresAt) {
    if (expiresAt == null) {
      throw new IllegalArgumentException("expiresAt must not be null for a temporary punishment");
    }

    return of(id, target, targetName, targetIp, type, reasonId, reason, staff, staffName, createdAt, expiresAt);
  }

  private static Punishment of(String id, UUID target, String targetName, @Nullable String targetIp,
      PunishmentType type, @Nullable String reasonId, String reason, UUID staff, String staffName, Instant createdAt,
      @Nullable Instant expiresAt) {
    return new Punishment(id, target, targetName, targetIp, type, reasonId, reason, staff, staffName, createdAt,
        expiresAt, true, null, null, null);
  }

  public boolean isPermanent() {
    return expiresAt == null;
  }

  public boolean isExpiredAt(Instant now) {
    return expiresAt != null && !now.isBefore(expiresAt);
  }

  public boolean isActiveAt(Instant now) {
    return active && !isExpiredAt(now);
  }

  /** True when the entry is still counted by auto-punish rules and by mutes. */
  public boolean countsAt(Instant now) {
    return isActiveAt(now) && type.isStrike();
  }

  /** Time left before expiry, empty for permanent punishments. */
  public Optional<Duration> remainingAt(Instant now) {
    if (expiresAt == null) {
      return Optional.empty();
    }

    Duration left = Duration.between(now, expiresAt);

    return Optional.of(left.isNegative() ? Duration.ZERO : left);
  }

  /** Human readable lifetime, for example {@code 2d 4h} or {@code permanent}. */
  public String lifetimeAt(@NotNull Instant now) {
    return remainingAt(now)
        .map(left -> Durations.format(left))
        .orElse("permanent");
  }

  /**
   * One of {@code active}, {@code expired} or {@code revoked}. Used for the
   * history view, so the wording is kept short and lower case.
   */
  public String stateAt(Instant now) {
    if (revokedAt != null) {
      return "revoked";
    }

    return isActiveAt(now) ? "active" : "expired";
  }

  /** Returns a copy marked as revoked, keeping the original data for auditing. */
  public Punishment revoke(Instant when, String by, @Nullable String reason) {
    return new Punishment(id, target, targetName, targetIp, type, reasonId, this.reason, staff, staffName, createdAt,
        expiresAt, false, when, by, reason);
  }

  /** Returns a copy marked as expired by the passage of time. */
  public Punishment expire() {
    return new Punishment(id, target, targetName, targetIp, type, reasonId, reason, staff, staffName, createdAt,
        expiresAt, false, null, null, null);
  }
}
