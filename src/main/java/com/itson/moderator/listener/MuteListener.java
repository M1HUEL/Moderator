package com.itson.moderator.listener;

import com.itson.moderator.command.ModContext;
import com.itson.moderator.config.Messages;
import com.itson.moderator.model.Punishment;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Enforces mutes on chat and on commands.
 *
 * <p>Chat arrives on an async thread, so the only thing done there is a lookup in
 * the concurrent mute cache; the reply is scheduled back onto the main thread.
 */
public final class MuteListener implements Listener {

  private final ModContext context;

  public MuteListener(@NotNull ModContext context) {
    this.context = context;
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onChat(@NotNull AsyncChatEvent event) {
    Player player = event.getPlayer();

    if (!context.mutes().isMuted(player.getUniqueId())) {
      return;
    }

    event.setCancelled(true);
    reply(player, mutedChatMessage(player));
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onCommand(@NotNull PlayerCommandPreprocessEvent event) {
    if (!context.config().muteBlocksCommands()) {
      return;
    }

    Player player = event.getPlayer();

    if (!context.mutes().isMuted(player.getUniqueId())) {
      return;
    }

    String command = event.getMessage();

    if (context.config().isCommandAllowedWhileMuted(command)) {
      return;
    }

    event.setCancelled(true);
    reply(player, context.messages().renderPrefixed("muted-command"));
  }

  private Component mutedChatMessage(Player player) {
    Messages messages = context.messages();
    Punishment mute = context.mutes().activeMute(player.getUniqueId()).orElse(null);

    if (mute == null) {
      return messages.renderPrefixed("muted-chat");
    }

    return messages.renderPrefixed("muted-chat", "duration", mute.lifetimeAt(context.moderation().now()),
        "staff", mute.staffName(), "reason", mute.reason());
  }

  /** Chat is async, so the reply is queued on the player's own scheduler. */
  private void reply(Player player, Component message) {
    player.getScheduler().run(context.plugin(), scheduled -> player.sendMessage(message), null);
  }
}
