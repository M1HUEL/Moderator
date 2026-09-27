package com.itson.moderator.command;

import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /mod kill [player]}: kills a player, for cleaning up after a mistake.
 *
 * <p>This is a tool, not a sanction: nothing is written to the history and the
 * death reads as an ordinary one. Use {@code /mod ban} or {@code /mod kick} when
 * the death is meant to be on the record.
 */
public final class KillCommand extends PlayerToolCommand {

  @Override
  public @NotNull String name() {
    return "kill";
  }

  @Override
  public @NotNull List<String> aliases() {
    return List.of();
  }

  @Override
  public @NotNull String permission() {
    return "moderator.kill";
  }

  @Override
  public @NotNull String arguments() {
    return "[player]";
  }

  @Override
  public @NotNull String description() {
    return "Kills a player";
  }

  /**
   * Kills the player and tells the staff member who did it.
   *
   * <p>The target is not told, since a death message already covers that.
   */
  @Override
  public void execute(@NotNull ModContext context, @NotNull CommandSender sender, @NotNull String[] args) {
    Player player = resolvePlayer(context, sender, args, true);

    if (player == null) {
      return;
    }

    player.setHealth(0d);

    if (isSelf(sender, player)) {
      Feedback.send(context, sender, "killed-self");

      return;
    }

    Feedback.send(context, sender, "killed", "player", player.getName());
  }
}
