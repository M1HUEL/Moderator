package com.itson.moderator.command;

import java.util.List;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /mod help}: only the tools the sender may actually use, which is the
 * only help list that stays true on a server with a staff hierarchy.
 */
public final class HelpCommand implements SubCommand {

  @Override
  public @NotNull String name() {
    return "help";
  }

  @Override
  public @NotNull List<String> aliases() {
    return List.of("?");
  }

  @Override
  public @NotNull String permission() {
    return ModCommand.ROOT_PERMISSION;
  }

  @Override
  public @NotNull String arguments() {
    return "[tool]";
  }

  @Override
  public @NotNull String description() {
    return "Lists the moderation tools";
  }

  @Override
  public void execute(@NotNull ModContext context, @NotNull CommandSender sender, @NotNull String[] args) {
    send(context, sender);
  }

  /**
   * Used by the dispatcher for a bare {@code /mod}, which has no arguments.
   *
   * <p>Static so the dispatcher can print help without going through the
   * subcommand's own argument handling, and so a context whose dispatcher is not
   * wired yet still answers with a header instead of failing.
   */
  public static void send(ModContext context, CommandSender sender) {
    ModCommand dispatcher = context.dispatcher();

    if (dispatcher == null) {
      Feedback.send(context, sender, "help-header");

      return;
    }

    List<SubCommand> available = dispatcher.available(sender);

    Feedback.send(context, sender, "help-header", "count", available.size());

    for (SubCommand sub : available) {
      Feedback.send(context, sender, "help-entry", "usage", "/" + sub.name() + " " + sub.arguments(),
          "description", sub.description());
    }
  }

  @Override
  public @NotNull List<String> complete(@NotNull ModContext context, @NotNull CommandSender sender,
      @NotNull String[] args) {
    return args.length <= 1 ? context.dispatcher().available(sender).stream().map(SubCommand::name).toList() : List.of();
  }
}
