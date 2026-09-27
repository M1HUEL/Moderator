package com.itson.moderator.command;

import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** {@code /mod feed [player]}: refills hunger and clears the exhaustion that causes it. */
public final class FeedCommand extends PlayerToolCommand {

  @Override
  public @NotNull String name() {
    return "feed";
  }

  @Override
  public @NotNull List<String> aliases() {
    return List.of();
  }

  @Override
  public @NotNull String permission() {
    return "moderator.feed";
  }

  @Override
  public @NotNull String arguments() {
    return "[player]";
  }

  @Override
  public @NotNull String description() {
    return "Feeds a player";
  }

  @Override
  public void execute(@NotNull ModContext context, @NotNull CommandSender sender, @NotNull String[] args) {
    Player player = resolvePlayer(context, sender, args, true);

    if (player == null) {
      return;
    }

    player.setFoodLevel(20);
    player.setSaturation(20f);
    player.setExhaustion(0f);

    if (isSelf(sender, player)) {
      Feedback.send(context, sender, "fed-self");

      return;
    }

    Feedback.send(context, sender, "fed", "player", player.getName());
    Feedback.send(context, player, "fed-by", "staff", sender.getName());
  }
}
