package com.itson.moderator.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Dispatches {@code /mod} to its subcommands.
 *
 * <p>Subcommand lookup happens on names and aliases alike, and the permission of
 * the resolved tool is what gates it, so listing a tool in help is enough to know
 * a member can use it.
 */
public final class ModCommand implements CommandExecutor, TabCompleter {

  /** Needed to use {@code /mod} at all, as declared in plugin.yml. */
  public static final String ROOT_PERMISSION = "moderator.use";

  private final ModContext context;

  /** Names and aliases, both lowercased, to the subcommand behind them. Insertion ordered for help. */
  private final Map<String, SubCommand> byName = new LinkedHashMap<>();

  /**
   * Indexes the subcommands.
   *
   * <p>A later subcommand may reuse an earlier alias, in which case it wins; the
   * registration list is ordered, so a plugin main class controls that by
   * construction rather than by luck.
   */
  public ModCommand(@NotNull ModContext context, @NotNull List<SubCommand> commands) {
    this.context = context;

    for (SubCommand command : commands) {
      byName.put(command.name().toLowerCase(Locale.ROOT), command);

      for (String alias : command.aliases()) {
        byName.put(alias.toLowerCase(Locale.ROOT), command);
      }
    }
  }

  /**
   * Routes to a subcommand, or prints help when none was named.
   *
   * <p>Always returns true: an unknown subcommand or a missing permission is
   * answered with a message, and returning false would make the server print its
   * own usage line on top of it.
   */
  @Override
  public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
      @NotNull String[] args) {
    if (args.length == 0) {
      HelpCommand.send(context, sender);

      return true;
    }

    SubCommand sub = byName.get(args[0].toLowerCase(Locale.ROOT));

    if (sub == null) {
      Feedback.send(context, sender, "unknown-subcommand", "input", args[0], "label", label);

      return true;
    }

    if (!sender.hasPermission(sub.permission())) {
      Feedback.send(context, sender, "no-permission");

      return true;
    }

    if (sub.playerOnly() && !(sender instanceof Player)) {
      Feedback.send(context, sender, "players-only");

      return true;
    }

    String[] rest = new String[args.length - 1];
    System.arraycopy(args, 1, rest, 0, rest.length);

    sub.execute(context, sender, rest);

    return true;
  }

  /**
   * Completes subcommand names, then delegates to the resolved subcommand.
   *
   * <p>Alias entries are filtered out of the name list so a command is not
   * suggested twice, and nothing the sender lacks permission for is offered.
   */
  @Override
  public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
      @NotNull String label, @NotNull String[] args) {
    if (args.length <= 1) {
      String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);

      return byName.entrySet().stream()
          .filter(entry -> entry.getKey().startsWith(prefix))
          .filter(entry -> !entry.getValue().aliases().contains(entry.getKey()))
          .map(Map.Entry::getValue)
          .filter(sub -> sender.hasPermission(sub.permission()))
          .map(sub -> sub.name())
          .distinct()
          .sorted()
          .toList();
    }

    SubCommand sub = byName.get(args[0].toLowerCase(Locale.ROOT));

    if (sub == null || !sender.hasPermission(sub.permission())) {
      return List.of();
    }

    String[] rest = new String[args.length - 1];
    System.arraycopy(args, 1, rest, 0, rest.length);

    return filter(sub.complete(context, sender, rest), rest[rest.length - 1]);
  }

  /** Subcommands the sender may run, in registration order. */
  public @NotNull List<SubCommand> available(@NotNull CommandSender sender) {
    List<SubCommand> list = new ArrayList<>();

    for (SubCommand sub : byName.values()) {
      if (!list.contains(sub) && sender.hasPermission(sub.permission())) {
        list.add(sub);
      }
    }

    return list;
  }

  private static List<String> filter(List<String> options, String prefix) {
    String lower = prefix.toLowerCase(Locale.ROOT);

    return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
  }

  /** Resolves a subcommand, for reuse by the player facing report command. */
  public @Nullable SubCommand find(@NotNull String name) {
    return byName.get(name.toLowerCase(Locale.ROOT));
  }
}
