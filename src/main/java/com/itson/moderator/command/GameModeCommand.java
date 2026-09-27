package com.itson.moderator.command;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** {@code /mod gamemode <mode> [player]}: changes a game mode, yours by default. */
public final class GameModeCommand extends PlayerToolCommand {

  @Override
  public @NotNull String name() {
    return "gamemode";
  }

  @Override
  public @NotNull List<String> aliases() {
    return List.of("gm");
  }

  @Override
  public @NotNull String permission() {
    return "moderator.gamemode";
  }

  @Override
  public @NotNull String arguments() {
    return "<mode> [player]";
  }

  @Override
  public @NotNull String description() {
    return "Changes a game mode";
  }

  @Override
  public void execute(@NotNull ModContext context, @NotNull CommandSender sender, @NotNull String[] args) {
    if (args.length == 0) {
      Feedback.send(context, sender, "usage", "usage", "/gamemode " + arguments());

      return;
    }

    Optional<GameMode> mode = readMode(args[0]);

    if (mode.isEmpty()) {
      Feedback.invalid(context, sender, "invalid-gamemode", args[0]);

      return;
    }

    String[] rest = new String[args.length - 1];
    System.arraycopy(args, 1, rest, 0, rest.length);

    Player player = resolvePlayer(context, sender, rest, true);

    if (player == null) {
      return;
    }

    player.setGameMode(mode.get());

    if (isSelf(sender, player)) {
      Feedback.send(context, sender, "gamemode-self", "mode", label(mode.get()));

      return;
    }

    Feedback.send(context, sender, "gamemode-set", "player", player.getName(), "mode", label(mode.get()));
    Feedback.send(context, player, "gamemode-by", "staff", sender.getName(), "mode", label(mode.get()));
  }

  @Override
  public @NotNull List<String> complete(@NotNull ModContext context, @NotNull CommandSender sender,
      @NotNull String[] args) {
    if (args.length == 1) {
      return List.of("survival", "creative", "adventure", "spectator");
    }

    if (args.length == 2) {
      return super.complete(context, sender, new String[] {args[1]});
    }

    return List.of();
  }

  private static Optional<GameMode> readMode(String raw) {
    String normalized = raw.toLowerCase(Locale.ROOT);

    for (GameMode mode : GameMode.values()) {
      if (mode.name().toLowerCase(Locale.ROOT).equals(normalized)) {
        return Optional.of(mode);
      }
    }

    return switch (normalized) {
      case "s" -> Optional.of(GameMode.SURVIVAL);
      case "c" -> Optional.of(GameMode.CREATIVE);
      case "a" -> Optional.of(GameMode.ADVENTURE);
      case "sp", "view", "v" -> Optional.of(GameMode.SPECTATOR);
      default -> Optional.empty();
    };
  }

  private static String label(GameMode mode) {
    String name = mode.name().toLowerCase(Locale.ROOT);

    return Character.toUpperCase(name.charAt(0)) + name.substring(1);
  }
}
