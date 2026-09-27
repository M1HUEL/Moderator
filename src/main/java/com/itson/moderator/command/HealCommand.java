package com.itson.moderator.command;

import java.util.List;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /mod heal [player]}: restores health and food, clears debuffs and puts
 * out fires.
 *
 * <p>Health is restored against the player's own maximum rather than a fixed
 * amount, so a player under a health boost is not healed to less than they could
 * hold.
 */
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

  /**
   * Heals the player and tells them a member of staff did it.
   *
   * <p>No confirmation prompt: every effect here is what a player would ask for
   * anyway, and none of it can be lost.
   */
  @Override
  public void execute(@NotNull ModContext context, @NotNull CommandSender sender, @NotNull String[] args) {
    Player player = resolvePlayer(context, sender, args, true);

    if (player == null) {
      return;
    }

    var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);

    // The attribute is absent on a player whose gamemode or plugin removed it,
    // in which case the rest of the heal still goes through.
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
