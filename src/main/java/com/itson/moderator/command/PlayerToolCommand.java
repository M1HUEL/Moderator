package com.itson.moderator.command;

import com.itson.moderator.model.Target;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for the tools that act on a player, defaulting to the sender.
 *
 * <p>Freezing is the exception: it is meaningless from the console and refuses to
 * be run against yourself.
 */
abstract class PlayerToolCommand implements SubCommand {

  @Override
  public boolean playerOnly() {
    return false;
  }

  @Override
  public @NotNull List<String> complete(@NotNull ModContext context, @NotNull CommandSender sender,
      @NotNull String[] args) {
    return args.length <= 1 ? Args.onlineNames() : List.of();
  }

  /**
   * Resolves the player a tool should act on: the named one, or the sender when
   * no name was given.
   */
  protected Player resolvePlayer(ModContext context, CommandSender sender, String[] args, boolean optionalSelf) {
    if (args.length == 0) {
      if (!optionalSelf) {
        Feedback.send(context, sender, "usage", "usage", "/" + name() + " " + arguments());

        return null;
      }

      if (!(sender instanceof Player player)) {
        Feedback.send(context, sender, "name-required");

        return null;
      }

      return player;
    }

    Optional<Target> target = Args.resolveTarget(context, sender, args[0]);

    if (target.isEmpty()) {
      return null;
    }

    if (!target.get().online()) {
      Feedback.send(context, sender, "target-offline", "player", target.get().name());

      return null;
    }

    return Bukkit.getPlayer(target.get().id());
  }

  /** Refuses a self action, unless the staff member opted in with {@code -self}. */
  protected boolean isSelf(CommandSender sender, Player player) {
    return sender.equals(player);
  }

  protected static boolean withinReach(Player actor, Player target, double range) {
    return actor.getWorld().equals(target.getWorld()) && actor.getLocation().distanceSquared(target.getLocation())
        <= range * range;
  }

  protected static Duration noCooldown() {
    return Duration.ZERO;
  }
}
