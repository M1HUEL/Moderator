package com.itson.moderator.command;

import com.itson.moderator.service.ModerationService;
import java.util.UUID;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Who is behind a command, so sanctions can name an author. */
public record Staff(UUID id, String name) {

  public static @NotNull Staff of(@NotNull CommandSender sender) {
    if (sender instanceof Player player) {
      return new Staff(player.getUniqueId(), player.getName());
    }

    return new Staff(ModerationService.CONSOLE_STAFF_ID, ModerationService.CONSOLE_STAFF);
  }

  public boolean isPlayer() {
    return !id.equals(ModerationService.CONSOLE_STAFF_ID);
  }
}
