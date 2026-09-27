package com.itson.moderator.command;

import java.util.List;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** {@code /mod heal [player]}: restores health and food and clears debuffs. */
public final class HealCommand extends PlayerToolCommand {

  @Override
  public @NotNull String name() {
    return "heal";
  }

  @Override
  public @NotNull List<String> aliases() {
    return List.of("h");
  }

  @Override
  public @NotNull String permission() {
    return "moderator.heal";
  }

  @Override
  public @NotNull String arguments() {
    return "[player]";
  }

  @Override
  public @NotNull String description() {
    return "Heals a player";
  }

  @Override
  public void execute(@NotNull ModContext context, @NotNull CommandSender sender, @NotNull String[] args) {
    Player player = resolvePlayer(context, sender, args, true);

    if (player == null) {
      return;
    }

    var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);

    if (maxHealth != null) {
      player.setHealth(maxHealth.getValue());
    }

    player.setFoodLevel(20);
    player.setSaturation(20f);
    player.setFireTicks(0);
    player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));

    if (isSelf(sender, player)) {
      Feedback.send(context, sender, "healed-self");

      return;
    }

    Feedback.send(context, sender, "healed", "player", player.getName());
    Feedback.send(context, player, "healed-by", "staff", sender.getName());
  }
}
