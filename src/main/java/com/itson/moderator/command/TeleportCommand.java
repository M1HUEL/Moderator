package com.itson.moderator.command;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** {@code /mod tp [player]}: teleports to a player, or brings one to you. */
public final class TeleportCommand extends PlayerToolCommand {

  @Override
  public boolean playerOnly() {
    return true;
  }

  @Override
  public @NotNull String name() {
    return "tp";
  }

  @Override
  public @NotNull List<String> aliases() {
    return List.of("teleport");
  }

  @Override
  public @NotNull String permission() {
    return "moderator.tp";
  }

  @Override
  public @NotNull String arguments() {
    return "[player]";
  }

  @Override
  public @NotNull String description() {
    return "Teleports to a player, or brings them to you";
  }

  @Override
  public void execute(@NotNull ModContext context, @NotNull CommandSender sender, @NotNull String[] args) {
    if (!(sender instanceof Player actor)) {
      return;
    }

    Player target = resolvePlayer(context, sender, args, false);

    if (target == null) {
      return;
    }

    if (isSelf(sender, target)) {
      Feedback.send(context, sender, "tp-self");

      return;
    }

    if (args.length == 0) {
      actor.teleport(target.getLocation());
      Feedback.send(context, sender, "tp-there", "player", target.getName());

      return;
    }

    if (!withinReach(actor, target, 32d)) {
      Feedback.send(context, sender, "tp-too-far", "player", target.getName());

      return;
    }

    Location destination = actor.getLocation();

    target.teleport(destination);
    Feedback.send(context, sender, "tp-here", "player", target.getName());
    Feedback.send(context, target, "tp-to", "staff", actor.getName());
  }
}
