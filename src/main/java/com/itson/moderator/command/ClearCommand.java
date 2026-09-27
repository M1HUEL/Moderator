package com.itson.moderator.command;

import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /mod clear [player] [inventory|armor|all]}: empties an inventory, one
 * section of it or all of it.
 *
 * <p>Clearing a section other than {@code all} leaves the rest untouched, which
 * is the difference from a plain {@code /clear}: staff usually want the hotbar
 * gone without throwing away a full main inventory.
 */
public final class ClearCommand extends PlayerToolCommand {

  @Override
  public @NotNull String name() {
    return "clear";
  }

  @Override
  public @NotNull List<String> aliases() {
    return List.of("ci");
  }

  @Override
  public @NotNull String permission() {
    return "moderator.clear";
  }

  @Override
  public @NotNull String arguments() {
    return "[player] [inventory|armor|all]";
  }

  @Override
  public @NotNull String description() {
    return "Clears a player inventory";
  }

  /**
   * Clears the requested section and tells both the staff member and the player
   * about it.
   *
   * <p>The staff member is notified even when the target is the one who ran the
   * command, so an audit of who cleared what stays complete.
   */
  @Override
  public void execute(@NotNull ModContext context, @NotNull CommandSender sender, @NotNull String[] args) {
    Player player = resolvePlayer(context, sender, args, true);

    if (player == null) {
      return;
    }

    String section = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "all";

    switch (section) {
      // Storage and armour are separate arrays, so both have to be written for
      // "all": clear() alone would leave the armour on.
      case "inventory" -> player.getInventory().setStorageContents(new ItemStack[36]);
      case "armor" -> player.getInventory().setArmorContents(null);
      case "all" -> {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
      }
      default -> {
        Feedback.invalid(context, sender, "invalid-section", args[1]);

        return;
      }
    }

    if (isSelf(sender, player)) {
      Feedback.send(context, sender, "cleared-self");

      return;
    }

    Feedback.send(context, sender, "cleared", "player", player.getName(), "section", section);
    Feedback.send(context, player, "cleared-by", "staff", sender.getName(), "section", section);
  }

  @Override
  public @NotNull List<String> complete(@NotNull ModContext context, @NotNull CommandSender sender,
      @NotNull String[] args) {
    if (args.length <= 1) {
      return super.complete(context, sender, args);
    }

    if (args.length == 2) {
      return List.of("inventory", "armor", "all");
    }

    return List.of();
  }
}
