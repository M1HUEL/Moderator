package com.itson.moderator.command;

import com.itson.moderator.config.Messages;
import com.itson.moderator.util.Durations;
import java.time.Duration;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Shared output helpers, so every tool reports success and failure the same way. */
public final class Feedback {

  /** Permission that opts a staff member into sanction broadcasts. */
  public static final String NOTIFY_PERMISSION = "moderator.notify";

  private Feedback() {
  }

  /** Sends a message to the prefix, resolving any placeholders. */
  public static void send(@NotNull ModContext context, @NotNull CommandSender sender, @NotNull String key,
      Object... placeholders) {
    Messages messages = context.messages();

    sender.sendMessage(messages.renderPrefixed(key, placeholders));
  }

  /**
   * Sends a message to every staff member who asked to be notified.
   *
   * <p>Sanctions are broadcast rather than printed to the sender only, because
   * the staff member who did not type the command still needs to know.
   */
  public static void broadcast(@NotNull ModContext context, @NotNull String key, Object... placeholders) {
    Messages messages = context.messages();
    Component message = messages.renderPrefixed(key, placeholders);

    for (Player online : Bukkit.getOnlinePlayers()) {
      if (online.hasPermission(NOTIFY_PERMISSION)) {
        online.sendMessage(message);
      }
    }

    Bukkit.getConsoleSender().sendMessage(message);
  }

  /** Sends a message only to the staff member who ran the command. */
  public static void notifyStaff(@NotNull ModContext context, @NotNull CommandSender sender, @NotNull String key,
      Object... placeholders) {
    Messages messages = context.messages();
    Component message = messages.renderPrefixed(key, placeholders);

    for (Player online : Bukkit.getOnlinePlayers()) {
      if (!online.equals(sender) && online.hasPermission(NOTIFY_PERMISSION)) {
        online.sendMessage(message);
      }
    }
  }

  /** Reports an argument that is neither a number nor a known value. */
  public static void invalid(@NotNull ModContext context, @NotNull CommandSender sender, @NotNull String key,
      @Nullable String value) {
    send(context, sender, key, "value", value == null ? "" : value);
  }

  /** Reports a remaining cooldown such as {@code 1m 20s}. */
  public static void coolingDown(@NotNull ModContext context, @NotNull CommandSender sender, Duration remaining) {
    send(context, sender, "cooldown", "time", Durations.format(remaining));
  }

  /** Lower cases and trims, the shape every identifier argument needs. */
  public static @NotNull String normalize(@Nullable String raw) {
    return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
  }
}
