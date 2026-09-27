package com.itson.moderator.service;

import com.itson.moderator.util.Text;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Backs {@code /mod invsee}: a staff member opens another player's inventory as
 * a normal container and edits it directly.
 *
 * <p>Edits are written back when the window closes, not on every click, so a
 * half finished change never lands. Sessions are keyed by the staff member,
 * because one person can only have one of these open at a time.
 *
 * <p>Layout of the 54 slot window: armour in the first four slots, the 36 storage
 * slots starting at {@link #STORAGE_SLOT}, the off hand at {@link #OFFHAND_SLOT},
 * and glass everywhere else so the readable part is obvious.
 */
public final class InvseeService {

  public static final int SIZE = 54;

  public static final int ARMOR_SLOT = 0;

  public static final int STORAGE_SLOT = 5;

  public static final int OFFHAND_SLOT = 45;

  private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

  /** Opens the view, replacing a window this staff member already had open. */
  public @NotNull Inventory open(@NotNull Player viewer, @NotNull Player target) {
    close(viewer.getUniqueId());

    Inventory inventory = Bukkit.createInventory(null, SIZE,
        Text.parse("<dark_gray>Inventory of <white><name>", "name", target.getName()));

    for (int slot = 0; slot < SIZE; slot++) {
      inventory.setItem(slot, filler());
    }

    ItemStack[] armor = target.getInventory().getArmorContents();
    ItemStack[] storage = target.getInventory().getStorageContents();

    for (int index = 0; index < armor.length; index++) {
      inventory.setItem(ARMOR_SLOT + index, armor[index]);
    }

    for (int index = 0; index < storage.length; index++) {
      inventory.setItem(STORAGE_SLOT + index, storage[index]);
    }

    inventory.setItem(OFFHAND_SLOT, target.getInventory().getItemInOffHand());

    sessions.put(viewer.getUniqueId(), new Session(viewer.getUniqueId(), target.getUniqueId(), inventory));

    viewer.openInventory(inventory);

    return inventory;
  }

  /** True when the inventory is a view this staff member currently has open. */
  public boolean isSession(@NotNull UUID viewer, @NotNull Inventory inventory) {
    Session session = sessions.get(viewer);

    return session != null && session.inventory().equals(inventory);
  }

  /** The staff member who owns a window, empty for any normal container. */
  public Optional<UUID> viewerOf(@NotNull Inventory inventory) {
    return sessions.values().stream()
        .filter(session -> session.inventory().equals(inventory))
        .map(Session::viewer)
        .findFirst();
  }

  /** Slots that map back to the target's inventory. */
  public static boolean isEditableSlot(int slot) {
    if (slot >= ARMOR_SLOT && slot < ARMOR_SLOT + 4) {
      return true;
    }

    if (slot >= STORAGE_SLOT && slot < STORAGE_SLOT + 36) {
      return true;
    }

    return slot == OFFHAND_SLOT;
  }

  /**
   * Writes the edits back to the target and forgets the session.
   *
   * @return the target, or empty when the target disconnected meanwhile
   */
  public Optional<Player> apply(@NotNull UUID viewer, @NotNull Inventory inventory) {
    Session session = sessions.get(viewer);

    if (session == null || !session.inventory().equals(inventory)) {
      return Optional.empty();
    }

    sessions.remove(viewer);

    Player target = Bukkit.getPlayer(session.target());

    if (target == null) {
      return Optional.empty();
    }

    ItemStack[] armor = new ItemStack[4];
    ItemStack[] storage = new ItemStack[36];

    for (int index = 0; index < armor.length; index++) {
      armor[index] = inventory.getItem(ARMOR_SLOT + index);
    }

    for (int index = 0; index < storage.length; index++) {
      storage[index] = inventory.getItem(STORAGE_SLOT + index);
    }

    target.getInventory().setArmorContents(armor);
    target.getInventory().setStorageContents(storage);
    target.getInventory().setItemInOffHand(inventory.getItem(OFFHAND_SLOT));

    return Optional.of(target);
  }

  /** Drops a session without writing anything back. */
  public void close(@NotNull UUID viewer) {
    sessions.remove(viewer);
  }

  /** The target a staff member is looking at. */
  public Optional<UUID> viewing(@NotNull UUID viewer) {
    Session session = sessions.get(viewer);

    return session == null ? Optional.empty() : Optional.of(session.target());
  }

  public int openSessions() {
    return sessions.size();
  }

  public void reset() {
    sessions.clear();
  }

  private static ItemStack filler() {
    return new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
  }

  private record Session(UUID viewer, UUID target, Inventory inventory) {
  }
}
