package com.itson.moderator.model;

import java.time.Instant;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A freeze, as it has to survive a restart and a crash.
 *
 * <p>Freezing takes control of a player's avatar: it clears their inventory,
 * forces adventure mode and locks them in place. Everything needed to hand that
 * avatar back untouched is therefore stored here rather than in memory, because
 * a freeze that only lived in memory would leave a player with an empty inventory
 * and no way to get their items back if the server went down while they were
 * frozen.
 *
 * <p>The snapshot is taken once, when the freeze starts, and is never refreshed.
 * Refreshing it on every reconnect would overwrite the original with an already
 * emptied inventory, which is exactly the data that has to survive.
 *
 * <p>Item data is held as base64 of Paper's binary item encoding, which is far
 * smaller than a YAML map of every component and does not need the plugin to
 * understand item formats.
 *
 * @param id          the frozen player
 * @param name        their name at the time of the freeze
 * @param reason      why they were frozen, for staff and for the frozen screen
 * @param staffName   who froze them
 * @param createdAt   when the freeze started
 * @param released    staff lifted the freeze while the player was not online, so
 *                    the snapshot still has to be handed over on their next join
 * @param gameMode    the game mode to put them back into
 * @param allowFlight whether flight was allowed before
 * @param flying      whether they were flying before
 * @param health      health before, so a half heart is not healed on release
 * @param foodLevel   hunger before
 * @param saturation  saturation before
 * @param fireTicks   burning ticks before
 * @param world       world name to send them back to, or null to leave them be
 * @param x           world x
 * @param y           world y
 * @param z           world z
 * @param yaw         world yaw
 * @param pitch       world pitch
 * @param contents    base64 of the main inventory
 * @param armor       base64 of the armour
 * @param offHand     base64 of the off hand
 */
public record FreezeRecord(
    @NotNull UUID id,
    @NotNull String name,
    @NotNull String reason,
    @NotNull String staffName,
    @NotNull Instant createdAt,
    boolean released,
    @NotNull String gameMode,
    boolean allowFlight,
    boolean flying,
    double health,
    int foodLevel,
    float saturation,
    int fireTicks,
    @Nullable String world,
    double x,
    double y,
    double z,
    float yaw,
    float pitch,
    @NotNull String contents,
    @NotNull String armor,
    @NotNull String offHand) {

  /** Rejects a record that could not be restored later, rather than failing quietly. */
  public FreezeRecord {
    if (id == null) {
      throw new IllegalArgumentException("id must not be null");
    }

    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }

    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("reason must not be blank");
    }

    if (staffName == null || staffName.isBlank()) {
      throw new IllegalArgumentException("staffName must not be blank");
    }

    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt must not be null");
    }

    if (gameMode == null || gameMode.isBlank()) {
      throw new IllegalArgumentException("gameMode must not be blank");
    }

    if (contents == null || armor == null || offHand == null) {
      throw new IllegalArgumentException("item data must not be null");
    }
  }

  /** How long the player has been held, for staff facing output. */
  public @NotNull String age(Instant now) {
    return com.itson.moderator.util.Text.age(createdAt, now);
  }

  /**
   * The same freeze, marked as lifted while the player was away.
   *
   * <p>The snapshot is kept on purpose: releasing someone who never got their
   * items back would destroy them, so the record stays until the next join has
   * actually handed the avatar over.
   */
  public @NotNull FreezeRecord markedReleased() {
    return released ? this : new FreezeRecord(id, name, reason, staffName, createdAt, true, gameMode, allowFlight,
        flying, health, foodLevel, saturation, fireTicks, world, x, y, z, yaw, pitch, contents, armor, offHand);
  }
}
