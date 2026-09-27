package com.itson.moderator.listener;

import com.itson.moderator.command.Feedback;
import com.itson.moderator.command.ModContext;
import com.itson.moderator.service.InvseeService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Applies whatever the staff member changed in an inventory view.
 *
 * <p>The filler slots are refused so the glass cannot be used to smuggle items,
 * and the write back happens on close, once, not on every click.
 */
public final class InvseeListener implements Listener {

  private final ModContext context;

  public InvseeListener(@NotNull ModContext context) {
    this.context = context;
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onClick(@NotNull InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player viewer)) {
      return;
    }

    if (context.invsee().viewerOf(event.getInventory()).isEmpty()) {
      return;
    }

    int slot = event.getRawSlot();

    if (slot < 0 || InvseeService.isEditableSlot(slot)) {
      return;
    }

    event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onDrag(@NotNull InventoryDragEvent event) {
    if (!(event.getWhoClicked() instanceof Player viewer)) {
      return;
    }

    if (context.invsee().viewerOf(event.getInventory()).isEmpty()) {
      return;
    }

    for (int slot : event.getRawSlots()) {
      if (!InvseeService.isEditableSlot(slot)) {
        event.setCancelled(true);

        return;
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onClose(@NotNull InventoryCloseEvent event) {
    if (!(event.getPlayer() instanceof Player viewer)) {
      return;
    }

    context.invsee().apply(viewer.getUniqueId(), event.getInventory()).ifPresent(target ->
        Feedback.send(context, viewer, "invsee-saved", "player", target.getName()));
  }
}
