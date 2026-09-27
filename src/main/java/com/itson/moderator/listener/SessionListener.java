package com.itson.moderator.listener;

import com.itson.moderator.command.ModContext;
import com.itson.moderator.model.Punishment;
import com.itson.moderator.model.PunishmentType;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Keeps the plugin's idea of a player up to date.
 *
 * <p>On join it records the address sanctions depend on, tells a muted player
 * why they cannot talk, and puts a frozen player back under control. On quit it
 * hands a frozen player their avatar back so a disconnect never costs them
 * items, while the freeze itself survives for the next join.
 */
public final class SessionListener implements Listener {

  private final ModContext context;

  public SessionListener(@NotNull ModContext context) {
    this.context = context;
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onJoin(@NotNull PlayerJoinEvent event) {
    Player player = event.getPlayer();

    context.moderation().registerPlayer(player);
    context.freeze().reapplyOnJoin(player);

    if (!context.config().notifyOnLogin()) {
      return;
    }

    List<Punishment> active = context.moderation().history(player.getUniqueId()).stream()
        .filter(punishment -> punishment.isActiveAt(context.moderation().now()))
        .filter(punishment -> punishment.type() != PunishmentType.NOTE)
        .toList();

    for (Punishment punishment : active) {
      player.sendMessage(context.messages().renderPrefixed("login-punishment", "type", punishment.type().label(),
          "reason", punishment.reason(), "duration", punishment.lifetimeAt(context.moderation().now()),
          "staff", punishment.staffName()));
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onQuit(@NotNull PlayerQuitEvent event) {
    Player player = event.getPlayer();

    context.freeze().releaseOnQuit(player);
    context.cooldowns().clear(player.getUniqueId());
    context.invsee().close(player.getUniqueId());
    context.moderation().save();
  }
}
