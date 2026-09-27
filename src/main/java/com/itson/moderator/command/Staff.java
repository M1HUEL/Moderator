package com.itson.moderator.command;

import com.itson.moderator.service.ModerationService;
import java.util.UUID;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Who is behind a command, so sanctions can name an author.
 *
 * <p>The console is a real author here rather than a blank: sanctions applied from
 * terminal have to say so in the history, otherwise they read as if a player
 * staff member did them.
 */
public record Staff(UUID id, String name) {

  /** The author of a command, with the console mapped onto its reserved id. */
  public static @NotNull Staff of(@NotNull CommandSender sender) {
    if (sender instanceof Player player) {
      return new Staff(player.getUniqueId(), player.getName());
    }

    return new Staff(ModerationService.CONSOLE_STAFF_ID, ModerationService.CONSOLE_STAFF);
  }

  /** Whether a player ran the command, false for the console. */
  public boolean isPlayer() {
    return !id.equals(ModerationService.CONSOLE_STAFF_ID);
  }
}
