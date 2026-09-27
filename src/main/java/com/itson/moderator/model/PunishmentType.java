package com.itson.moderator.model;

import java.util.Locale;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/**
 * Every kind of record the plugin can write to a player history.
 *
 * <p>Ids are stable strings used in commands and configuration. They are always
 * lower case and never change once released, so stored data keeps resolving.
 */
public enum PunishmentType {

  WARN("warn", "warned"),
  MUTE("mute", "muted"),
  BAN("ban", "banned"),
  BAN_IP("banip", "IP banned"),
  KICK("kick", "kicked"),
  NOTE("note", "noted");

  private final String id;

  private final String label;

  PunishmentType(String id, String label) {
    this.id = id;
    this.label = label;
  }

  public String id() {
    return id;
  }

  /** Past-tense label, for example {@code IP banned}. */
  public String label() {
    return label;
  }

  /**
   * Types that push a player closer to an automatic punishment. Notes are
   * bookkeeping and kicks are not sanctions, so neither counts.
   */
  public boolean isStrike() {
    return this == WARN || this == MUTE || this == BAN;
  }

  /** Types backed by a vanilla ban list, therefore able to block a connection. */
  public boolean isBanListBacked() {
    return this == BAN || this == BAN_IP;
  }

  /** Types that need a duration argument, where {@code perm} is also accepted. */
  public boolean acceptsDuration() {
    return this == MUTE || this == BAN || this == BAN_IP;
  }

  /**
   * True when a second punishment of the same type is meaningful.
   *
   * <p>Warnings pile up on purpose, and notes and kicks are one off events. A
   * mute, ban or IP ban does not: applying it again replaces what was running
   * instead of adding a second one.
   */
  public boolean allowsStack() {
    return this == WARN || this == KICK || this == NOTE;
  }

  public static Optional<PunishmentType> byId(@Nullable String raw) {
    if (raw == null) {
      return Optional.empty();
    }

    String normalized = raw.toLowerCase(Locale.ROOT);

    for (PunishmentType type : values()) {
      if (type.id.equals(normalized)) {
        return Optional.of(type);
      }
    }

    return Optional.empty();
  }
}
