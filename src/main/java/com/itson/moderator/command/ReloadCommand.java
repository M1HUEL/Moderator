package com.itson.moderator.command;

import java.util.List;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/** {@code /mod reload}: re-reads config.yml and rebuilds the mute cache. */
public final class ReloadCommand implements SubCommand {

  @Override
  public @NotNull String name() {
    return "reload";
  }

  @Override
  public @NotNull List<String> aliases() {
    return List.of("rl");
  }

  @Override
  public @NotNull String permission() {
    return "moderator.admin";
  }

  @Override
  public @NotNull String arguments() {
    return "";
  }

  @Override
  public @NotNull String description() {
    return "Reloads the configuration";
  }

  @Override
  public void execute(@NotNull ModContext context, @NotNull CommandSender sender, @NotNull String[] args) {
    context.config().load(context.plugin());
    context.moderation().setConfig(context.config());
    context.cooldowns().bind(context.config());
    context.moderation().reloadMutes();

    List<String> problems = context.config().problems();

    if (problems.isEmpty()) {
      Feedback.send(context, sender, "reloaded");

      return;
    }

    Feedback.send(context, sender, "reloaded-with-problems", "count", problems.size());

    for (String problem : problems) {
      sender.sendMessage(context.messages().render("<gray>  - <red><problem>", "problem", problem));
    }
  }
}
