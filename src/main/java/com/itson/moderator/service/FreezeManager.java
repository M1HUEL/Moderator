package com.itson.moderator.service;

import com.itson.moderator.model.FreezeRecord;
import com.itson.moderator.storage.ModerationStore;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holds frozen players in place and stores exactly what they were carrying.
 *
 * <p>Freezing is not a punishment with an end date, it is a state staff can only
 * lift with {@code /mod unfreeze}, so membership has to survive more than a
 * reconnect: the record lives in the same store as the sanctions, in the
 * {@code freezes} section of {@code data.yml}.
 *
 * <p>Storing the snapshot on disk rather than in memory is what makes a freeze
 * safe. The plugin takes control of a player's avatar by clearing their
 * inventory, and Minecraft saves that cleared inventory with the rest of the
 * world. A snapshot held only in memory would therefore turn any crash, or even a
 * restart, into permanent item loss for everyone who was frozen at the time.
 *
 * <p>Because the snapshot is written before the avatar is touched, a crash at the
 * worst possible moment still leaves the items recoverable: the empty inventory
 * is on disk and so is the copy that fills it back in.
 */
public final class FreezeManager {

  private final ModerationStore store;

  public FreezeManager(@NotNull ModerationStore store) {
    this.store = store;
  }

  /**
   * Puts a player into the frozen state and takes control of their avatar.
   *
   * @return false when the player was already frozen
   */
  public boolean freeze(@NotNull Player player, @NotNull String reason, @NotNull String staffName) {
    if (store.freeze(player.getUniqueId()).isPresent()) {
      return false;
    }

    store.freeze(capture(player, reason, staffName));
    store.save();

    // Only now that the snapshot is safely on disk is the inventory emptied, so a
    // failure between the two lines cannot cost the player their items.
    apply(player);

    return true;
  }

  /**
   * Lifts the freeze of an online player and gives their avatar back.
   *
   * @return false when the player was not frozen
   */
  public boolean unfreeze(@NotNull Player player) {
    if (store.freeze(player.getUniqueId()).isEmpty()) {
      return false;
    }

    restore(player);
    store.unfreeze(player.getUniqueId());

    return true;
  }

  /**
   * Lifts the freeze of a player who is not online.
   *
   * <p>The record is kept and only marked as released, because the avatar cannot
   * be handed back until the player is actually here. Deleting it now would throw
   * away the snapshot in the one case it is still the only copy, which is a
   * server that went down while the player was frozen.
   *
   * @return false when the player was not frozen
   */
  public boolean release(@NotNull UUID player) {
    Optional<FreezeRecord> freeze = store.freeze(player);

    if (freeze.isEmpty()) {
      return false;
    }

    store.freeze(freeze.get().markedReleased());

    return true;
  }

  public boolean isFrozen(@NotNull UUID player) {
    return store.freeze(player).filter(freeze -> !freeze.released()).isPresent();
  }

  public int size() {
    return (int) store.freezes().stream().filter(freeze -> !freeze.released()).count();
  }

  /**
   * Called on join: a player that disconnected while frozen is put back under
   * control, without overwriting the snapshot taken the first time.
   *
   * <p>A freeze that staff lifted while the player was away is completed instead:
   * the snapshot goes back in and the record is dropped, which is the moment the
   * avatar is finally whole again.
   *
   * <p>The record that was acted upon is returned either way, so the caller can
   * put the right screen in front of the player. Check {@link FreezeRecord#released()}
   * to tell a freeze that still stands from one that was just lifted.
   *
   * @return the freeze this join acted on, if there was one
   */
  public Optional<FreezeRecord> reapplyOnJoin(@NotNull Player player) {
    Optional<FreezeRecord> freeze = store.freeze(player.getUniqueId());

    if (freeze.isEmpty()) {
      return Optional.empty();
    }

    if (freeze.get().released()) {
      restore(player);
      store.unfreeze(player.getUniqueId());
    } else {
      apply(player);
    }

    return freeze;
  }

  /**
   * Called on quit: the avatar is handed back to the player, but the freeze
   * itself stays in place for the next join.
   *
   * <p>Restoring on the way out is what keeps a frozen player's items safe from a
   * logout: Minecraft saves the inventory as it is at quit, so leaving it cleared
   * would be as destructive as a crash.
   */
  public void releaseOnQuit(@NotNull Player player) {
    store.freeze(player.getUniqueId()).filter(freeze -> !freeze.released()).ifPresent(freeze -> restore(player));
  }

  /**
   * Called on disable: every frozen player gets their avatar back, and every
   * freeze stays on file.
   *
   * <p>The records are deliberately kept. The server is still going to save the
   * players' inventories on shutdown, so their avatar has to be whole before the
   * process ends, and the freeze has to still be there on the next start.
   */
  public void releaseEveryone() {
    for (Player online : Bukkit.getOnlinePlayers()) {
      releaseOnQuit(online);
    }
  }

  private void apply(Player player) {
    player.setGameMode(GameMode.ADVENTURE);
    player.setAllowFlight(false);
    player.setFlying(false);
    player.setVelocity(new Vector());
    player.setFireTicks(0);
    player.setFoodLevel(20);
    player.setSaturation(20f);
    player.setHealth(maxHealth(player));
    player.getInventory().clear();
    player.getInventory().setArmorContents(null);
    player.getInventory().setItemInOffHand(null);
    player.teleport(player.getLocation());
    player.setSneaking(false);
  }

  private void restore(Player player) {
    store.freeze(player.getUniqueId()).ifPresent(freeze -> {
      player.getInventory().setContents(decode(freeze.contents()));
      player.getInventory().setArmorContents(decode(freeze.armor()));
      player.getInventory().setItemInOffHand(decodeOne(freeze.offHand()));
      player.setGameMode(gameMode(freeze.gameMode()));
      player.setAllowFlight(freeze.allowFlight());
      player.setFlying(freeze.flying());
      player.setFoodLevel(freeze.foodLevel());
      player.setSaturation(freeze.saturation());
      player.setFireTicks(freeze.fireTicks());

      if (freeze.health() > 0) {
        player.setHealth(Math.min(freeze.health(), maxHealth(player)));
      }

      Location location = location(freeze);

      if (location != null) {
        player.teleport(location);
      }
    });
  }

  /** Takes the snapshot a freeze restores from, and records who did it and why. */
  private static @NotNull FreezeRecord capture(@NotNull Player player, @NotNull String reason,
      @NotNull String staffName) {
    Location location = player.getLocation();
    World world = location.getWorld();

    return new FreezeRecord(player.getUniqueId(), player.getName(), reason, staffName, Instant.now(), false,
        player.getGameMode().name(), player.getAllowFlight(), player.isFlying(), player.getHealth(),
        player.getFoodLevel(), player.getSaturation(), player.getFireTicks(),
        world == null ? null : world.getName(), location.getX(), location.getY(), location.getZ(),
        location.getYaw(), location.getPitch(), encode(player.getInventory().getContents()),
        encode(player.getInventory().getArmorContents()), encodeOne(player.getInventory().getItemInOffHand()));
  }

  /**
   * The world a freeze was taken in, or null when it is gone.
   *
   * <p>A missing world is not an error: the player is simply left where they are
   * rather than being teleported into a world that no longer exists.
   */
  private static @Nullable Location location(@NotNull FreezeRecord freeze) {
    if (freeze.world() == null) {
      return null;
    }

    World world = Bukkit.getWorld(freeze.world());

    return world == null ? null
        : new Location(world, freeze.x(), freeze.y(), freeze.z(), freeze.yaw(), freeze.pitch());
  }

  /**
   * The game mode a freeze was taken in, defaulting to survival.
   *
   * <p>Unknown values fall back rather than throwing, because a game mode added
   * by a newer version, or a hand edited file, must not cost a player their
   * inventory on release.
   */
  private static @NotNull GameMode gameMode(@NotNull String id) {
    for (GameMode mode : GameMode.values()) {
      if (mode.name().equalsIgnoreCase(id)) {
        return mode;
      }
    }

    return GameMode.SURVIVAL;
  }

  private static String encode(@NotNull ItemStack[] items) {
    return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(items));
  }

  private static String encodeOne(@NotNull ItemStack item) {
    return Base64.getEncoder().encodeToString(item.serializeAsBytes());
  }

  private static @NotNull ItemStack[] decode(@NotNull String data) {
    ItemStack[] items = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(data));

    return items == null ? new ItemStack[0] : items;
  }

  private static @NotNull ItemStack decodeOne(@NotNull String data) {
    ItemStack item = ItemStack.deserializeBytes(Base64.getDecoder().decode(data));

    return item == null ? new ItemStack(Material.AIR) : item;
  }

  private static double maxHealth(Player player) {
    return player.getAttribute(Attribute.MAX_HEALTH) == null
        ? 20.0d
        : player.getAttribute(Attribute.MAX_HEALTH).getValue();
  }
}
