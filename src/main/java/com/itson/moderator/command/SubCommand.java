package com.itson.moderator.command;

import java.util.List;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * One {@code /mod} subcommand.
 *
 * <p>Implementations only parse arguments and call the services: keeping the
 * Bukkit plumbing in the dispatcher is what makes the tools small and testable.
 */
public interface SubCommand {

  String name();

  default @NotNull List<String> aliases() {
    return List.of();
  }

  /** Permission required to see and run this subcommand. */
  @NotNull String permission();

  /** When true, running it from the console is refused. */
  default boolean playerOnly() {
    return false;
  }

  /** Argument summary shown in help and in the usage error, without the label. */
  @NotNull String arguments();

  @NotNull String description();

  void execute(@NotNull ModContext context, @NotNull CommandSender sender, @NotNull String[] args);

  /**
   * Completes the argument at {@code args.length - 1}. Returning an empty list
   * leaves the player with no suggestions.
   */
  default @NotNull List<String> complete(@NotNull ModContext context, @NotNull CommandSender sender,
      @NotNull String[] args) {
    return List.of();
  }
}
