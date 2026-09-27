package com.itson.moderator.command;

import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/** {@code /mod clear [player]}: empties the inventory, optionally only one section. */
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

  @Override
  public void execute(@NotNull ModContext context, @NotNull CommandSender sender, @NotNull String[] args) {
    Player player = resolvePlayer(context, sender, args, true);

    if (player == null) {
      return;
    }

    String section = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "all";

    switch (section) {
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
