package com.itson.moderator.listener;

import com.itson.moderator.command.ModContext;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Keeps a frozen player in place and harmless.
 *
 * <p>Movement is the important one: the client keeps sending position packets
 * regardless, so they are refused until the player is released. Looking around
 * is left alone, because a frozen player that cannot turn their head looks broken
 * rather than held.
 */
public final class FreezeListener implements Listener {

  private final ModContext context;

  public FreezeListener(@NotNull ModContext context) {
    this.context = context;
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onMove(@NotNull PlayerMoveEvent event) {
    if (!frozen(event.getPlayer()) || !event.hasChangedPosition()) {
      return;
    }

    event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onCommand(@NotNull PlayerCommandPreprocessEvent event) {
    if (!frozen(event.getPlayer())) {
      return;
    }

    event.setCancelled(true);
    event.getPlayer().sendMessage(context.messages().renderPrefixed("frozen-command"));
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onInteract(@NotNull PlayerInteractEvent event) {
    if (frozen(event.getPlayer())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onDrop(@NotNull PlayerDropItemEvent event) {
    if (frozen(event.getPlayer())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onBreak(@NotNull BlockBreakEvent event) {
    if (frozen(event.getPlayer())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onPlace(@NotNull BlockPlaceEvent event) {
    if (frozen(event.getPlayer())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onBucketEmpty(@NotNull PlayerBucketEmptyEvent event) {
    if (frozen(event.getPlayer())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onBucketFill(@NotNull PlayerBucketFillEvent event) {
    if (frozen(event.getPlayer())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onPickup(@NotNull EntityPickupItemEvent event) {
    if (event.getEntity() instanceof Player player && frozen(player)) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onHunger(@NotNull FoodLevelChangeEvent event) {
    if (event.getEntity() instanceof Player player && frozen(player)) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onGameMode(@NotNull PlayerGameModeChangeEvent event) {
    if (frozen(event.getPlayer())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onFlight(@NotNull PlayerToggleFlightEvent event) {
    if (frozen(event.getPlayer())) {
      event.setCancelled(true);
    }
  }

  private boolean frozen(Player player) {
    return context.freeze().isFrozen(player.getUniqueId());
  }
}
