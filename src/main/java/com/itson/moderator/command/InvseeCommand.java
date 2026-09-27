package com.itson.moderator.command;

import java.util.List;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /mod invsee <player>}: opens and edits another player's inventory.
 *
 * <p>Edits are applied when the window is closed, by
 * {@link com.itson.moderator.listener.InvseeListener}.
 */
public final class InvseeCommand implements SubCommand {

  @Override
  public boolean playerOnly() {
    return true;
  }

  @Override
  public @NotNull String name() {
    return "invsee";
  }

  @Override
  public @NotNull List<String> aliases() {
    return List.of("viewinv", "openinv");
  }

  @Override
  public @NotNull String permission() {
    return "moderator.invsee";
  }

  @Override
  public @NotNull String arguments() {
    return "<player>";
  }

  @Override
  public @NotNull String description() {
    return "Opens a player inventory for editing";
  }

  @Override
  public void execute(@NotNull ModContext context, @NotNull CommandSender sender, @NotNull String[] args) {
    if (args.length == 0) {
      Feedback.send(context, sender, "usage", "usage", "/invsee " + arguments());

      return;
    }

    Optional<com.itson.moderator.model.Target> target = Args.resolveTarget(context, sender, args[0]);

    if (target.isEmpty()) {
      return;
    }

    if (!target.get().online()) {
      Feedback.send(context, sender, "target-offline", "player", target.get().name());

      return;
    }

    Player victim = Bukkit.getPlayer(target.get().id());

    if (victim == null) {
      Feedback.send(context, sender, "target-offline", "player", target.get().name());

      return;
    }

    context.invsee().open((Player) sender, victim);

    Feedback.send(context, sender, "invsee-opened", "player", victim.getName());
    Feedback.send(context, victim, "invsee-by", "staff", sender.getName());
  }

  @Override
  public @NotNull List<String> complete(@NotNull ModContext context, @NotNull CommandSender sender,
      @NotNull String[] args) {
    return args.length <= 1 ? Args.onlineNames() : List.of();
  }
}
