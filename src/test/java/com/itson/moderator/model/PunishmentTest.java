package com.itson.moderator.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itson.moderator.config.AutoPunishRule;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers what a punishment says about itself over time: when it is still in
 * force, what is left of it, and how permanent and self sanctions differ.
 */
class PunishmentTest {

  private static final UUID TARGET = UUID.fromString("11111111-1111-1111-1111-111111111111");

  private static final UUID STAFF = UUID.fromString("22222222-2222-2222-2222-222222222222");

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private static Punishment temporary(Duration length) {
    return Punishment.temporary("id-1", TARGET, "Steve", "1.2.3.4", PunishmentType.MUTE, "spam", "Spam", STAFF, "Admin",
        NOW, NOW.plus(length));
  }

  private static Punishment permanent(PunishmentType type) {
    return Punishment.permanent("id-2", TARGET, "Steve", "1.2.3.4", type, null, "No reason", STAFF, "Admin", NOW);
  }

  @Test
  @DisplayName("a temporary punishment is active until its end and expired after it")
  void expiry() {
    Punishment mute = temporary(Duration.ofHours(1));

    assertTrue(mute.isActiveAt(NOW));
    assertTrue(mute.isActiveAt(NOW.plusSeconds(3599)));
    assertFalse(mute.isActiveAt(NOW.plusSeconds(3600)));
    assertFalse(mute.isActiveAt(NOW.plusSeconds(3601)));
  }

  @Test
  @DisplayName("a permanent punishment never expires")
  void permanentNeverExpires() {
    Punishment ban = permanent(PunishmentType.BAN);

    assertTrue(ban.isPermanent());
    assertTrue(ban.isActiveAt(NOW.plus(Duration.ofDays(3650))));
    assertEquals(Optional.empty(), ban.remainingAt(NOW));
  }

  @Test
  @DisplayName("time left is never negative")
  void remainingIsClamped() {
    Punishment mute = temporary(Duration.ofMinutes(30));

    assertEquals(Optional.of(Duration.ofMinutes(30)), mute.remainingAt(NOW));
    assertEquals(Optional.of(Duration.ofMinutes(20)), mute.remainingAt(NOW.plusSeconds(600)));
    assertEquals(Optional.of(Duration.ZERO), mute.remainingAt(NOW.plusSeconds(3600)));
  }

  @Test
  @DisplayName("a revoked punishment is inactive even before its end")
  void revokeWins() {
    Punishment revoked = temporary(Duration.ofHours(1)).revoke(NOW, "Admin", "Appeal accepted");

    assertFalse(revoked.active());
    assertFalse(revoked.isActiveAt(NOW));
    assertEquals("revoked", revoked.stateAt(NOW));
    assertEquals("Appeal accepted", revoked.revokeReason());
  }

  @Test
  @DisplayName("an expired punishment reads as expired, not revoked")
  void expireKeepsAuthor() {
    Punishment closed = temporary(Duration.ofMinutes(1)).expire();

    assertFalse(closed.active());
    assertNull(closed.revokedAt());
    assertNull(closed.revokeReason());
    assertEquals("expired", closed.stateAt(NOW.plusSeconds(120)));
  }

  @Test
  @DisplayName("lifetime wording matches what staff typed")
  void lifetimeWording() {
    assertEquals("2h", temporary(Duration.ofHours(2)).lifetimeAt(NOW));
    assertEquals("permanent", permanent(PunishmentType.BAN).lifetimeAt(NOW));
  }

  @Test
  @DisplayName("only real sanctions count towards auto-punish")
  void counting() {
    assertTrue(temporary(Duration.ofHours(1)).countsAt(NOW));
    assertTrue(permanent(PunishmentType.WARN).countsAt(NOW));
    assertFalse(permanent(PunishmentType.NOTE).countsAt(NOW));
    assertFalse(permanent(PunishmentType.KICK).countsAt(NOW));
  }

  @Test
  @DisplayName("only the sanctions that can stack say so")
  void stacking() {
    assertTrue(PunishmentType.WARN.allowsStack());
    assertTrue(PunishmentType.KICK.allowsStack());
    assertTrue(PunishmentType.NOTE.allowsStack());
    assertFalse(PunishmentType.MUTE.allowsStack());
    assertFalse(PunishmentType.BAN.allowsStack());
    assertFalse(PunishmentType.BAN_IP.allowsStack());
  }

  @Test
  @DisplayName("incomplete records are rejected at construction")
  void validatesInput() {
    assertThrows(IllegalArgumentException.class, () -> Punishment.permanent("  ", TARGET, "Steve", null,
        PunishmentType.BAN, null, "x", STAFF, "Admin", NOW));
    assertThrows(IllegalArgumentException.class, () -> Punishment.permanent("id", TARGET, "Steve", null,
        PunishmentType.BAN, null, "  ", STAFF, "Admin", NOW));
    assertThrows(IllegalArgumentException.class, () -> Punishment.temporary("id", TARGET, "Steve", null,
        PunishmentType.BAN, null, "x", STAFF, "Admin", NOW, null));
    assertThrows(IllegalArgumentException.class, () -> new AutoPunishRule(0, Set.of(PunishmentType.WARN),
        PunishmentType.BAN, null, "x", null, false));
    assertThrows(IllegalArgumentException.class, () -> new AutoPunishRule(1, Set.of(), PunishmentType.BAN, null,
        "x", null, false));
  }

  @Test
  @DisplayName("type ids are stable and resolve in any case")
  void typeLookup() {
    assertEquals(Optional.of(PunishmentType.BAN_IP), PunishmentType.byId("banip"));
    assertEquals(Optional.of(PunishmentType.BAN_IP), PunishmentType.byId("BANIP"));
    assertEquals(Optional.of(PunishmentType.BAN), PunishmentType.byId("Ban"));
    assertEquals(Optional.empty(), PunishmentType.byId("teleport"));
    assertEquals(Optional.empty(), PunishmentType.byId(null));
  }
}
