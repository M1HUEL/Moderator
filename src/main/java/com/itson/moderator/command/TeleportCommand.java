package com.itson.moderator.command;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /mod tp <player>}: brings a player to you.
 *
 * <p>A name is required. With no name this is a usage error, not a teleport to
 * the nearest player.
 *
 * <p>Pulling someone is limited to {@value #MAX_REACH} blocks and to the same
 * world, so staff cannot use the command to bring a player across a dimension
 * or from the other side of the map.
 */
public final class TeleportCommand extends PlayerToolCommand {

  /** How close a player has to be before they can be brought over. */
  private static final double MAX_REACH = 32d;

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

  /**
   * Brings the named player to the sender.
   *
   * <p>Self is refused first, so being named by accident is a message rather than
   * a pointless teleport.
   */
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

    if (!withinReach(actor, target, MAX_REACH)) {
      Feedback.send(context, sender, "tp-too-far", "player", target.getName());

      return;
    }

    Location destination = actor.getLocation();

    target.teleport(destination);
    Feedback.send(context, sender, "tp-here", "player", target.getName());
    Feedback.send(context, target, "tp-to", "staff", actor.getName());
  }
}
