package com.itson.moderator.service;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * Holds frozen players in place and stores exactly what they were carrying.
 *
 * <p>Freezing is not a punishment with an end date, it is a state staff can only
 * lift with {@code /mod unfreeze}, so membership survives a reconnect. The saved
 * snapshot is restored whenever the player leaves, which means a frozen player
 * never loses items or a game mode just because they logged out.
 */
public final class FreezeManager {

  private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();

  private final Map<UUID, FrozenState> states = new ConcurrentHashMap<>();

  /**
   * Puts a player into the frozen state and takes control of their avatar.
   *
   * @return false when the player was already frozen
   */
  public boolean freeze(@NotNull Player player) {
    if (!frozen.add(player.getUniqueId())) {
      return false;
    }

    states.put(player.getUniqueId(), FrozenState.capture(player));
    apply(player);

    return true;
  }

  /**
   * Lifts the freeze and gives the player their avatar back.
   *
   * @return false when the player was not frozen
   */
  public boolean unfreeze(@NotNull Player player) {
    if (!frozen.remove(player.getUniqueId())) {
      return false;
    }

    states.remove(player.getUniqueId());
    restore(player);

    return true;
  }

  public boolean isFrozen(@NotNull UUID player) {
    return frozen.contains(player);
  }

  public int size() {
    return frozen.size();
  }

  /** Players currently frozen, for {@code /mod freeze list}. */
  public Collection<UUID> frozenIds() {
    return Set.copyOf(frozen);
  }

  /**
   * Called on join: a player that disconnected while frozen is put back under
   * control, without overwriting the snapshot taken the first time.
   */
  public void reapplyOnJoin(@NotNull Player player) {
    if (!frozen.contains(player.getUniqueId())) {
      return;
    }

    apply(player);
  }

  /**
   * Called on quit: the avatar is handed back to the player, but the freeze
   * itself stays in place for the next join.
   */
  public void releaseOnQuit(@NotNull Player player) {
    if (!frozen.contains(player.getUniqueId())) {
      return;
    }

    restore(player);
  }

  public void reset() {
    frozen.clear();
    states.clear();
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
    FrozenState state = states.get(player.getUniqueId());

    if (state == null) {
      return;
    }

    player.getInventory().setContents(state.contents());
    player.getInventory().setArmorContents(state.armor());
    player.getInventory().setItemInOffHand(state.offHand());
    player.setGameMode(state.gameMode());
    player.setAllowFlight(state.allowFlight());
    player.setFlying(state.flying());
    player.setFoodLevel(state.foodLevel());
    player.setSaturation(state.saturation());
    player.setFireTicks(state.fireTicks());

    if (state.health() > 0) {
      player.setHealth(Math.min(state.health(), maxHealth(player)));
    }

    Location location = state.location();

    if (location != null) {
      player.teleport(location);
    }
  }

  private static double maxHealth(Player player) {
    return player.getAttribute(Attribute.MAX_HEALTH) == null
        ? 20.0d
        : player.getAttribute(Attribute.MAX_HEALTH).getValue();
  }

  /**
   * Everything needed to hand the avatar back untouched. Item arrays are cloned
   * on capture so later inventory changes cannot rewrite history.
   */
  private record FrozenState(Location location, GameMode gameMode, ItemStack[] contents, ItemStack[] armor,
      ItemStack offHand, boolean allowFlight, boolean flying, double health, int foodLevel, float saturation,
      int fireTicks) {

    static FrozenState capture(Player player) {
      return new FrozenState(player.getLocation().clone(), player.getGameMode(),
          player.getInventory().getContents().clone(), player.getInventory().getArmorContents().clone(),
          player.getInventory().getItemInOffHand().clone(), player.getAllowFlight(), player.isFlying(),
          player.getHealth(), player.getFoodLevel(), player.getSaturation(), player.getFireTicks());
    }
  }
}
