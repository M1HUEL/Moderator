package com.itson.moderator.command;

import com.itson.moderator.model.Report;
import com.itson.moderator.model.Target;
import com.itson.moderator.service.TargetResolver;
import com.itson.moderator.util.Text;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /report <player> <reason> [details]}: the player facing half of the
 * report system.
 *
 * <p>Deliberately not a {@code /mod} subcommand: it is meant to be available to
 * everyone, while {@code /mod} is gated behind staff permissions.
 */
public final class ReportCommand implements CommandExecutor, TabCompleter {

  private final ModContext context;

  public ReportCommand(@NotNull ModContext context) {
    this.context = context;
  }

  @Override
  public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
      @NotNull String[] args) {
    if (!(sender instanceof Player reporter)) {
      Feedback.send(context, sender, "players-only");

      return true;
    }

    if (args.length < 2) {
      Feedback.send(context, sender, "usage", "usage", "/report <player> <reason> [details]");

      return true;
    }

    Optional<Duration> cooldown =
        context.cooldowns().remaining(reporter.getUniqueId(), "report", context.moderation().now());

    if (cooldown.isPresent()) {
      Feedback.coolingDown(context, reporter, cooldown.get());

      return true;
    }

    TargetResolver.Resolution resolution = context.targets().resolve(args[0]);

    if (!resolution.found()) {
      Feedback.send(context, reporter, "target-not-found", "input", args[0]);

      return true;
    }

    Target target = resolution.target();

    if (target.id().equals(reporter.getUniqueId())) {
      Feedback.send(context, reporter, "report-self");

      return true;
    }

    String reasonId = args[1].toLowerCase(Locale.ROOT);
    String details = Args.join(args, 2, " ");

    context.moderation().registerPlayer(reporter);
    Report report = context.moderation().openReport(target, reporter.getUniqueId(), reporter.getName(), reasonId,
        details.isBlank() ? null : details);

    context.cooldowns().start(reporter.getUniqueId(), "report", context.moderation().now());

    Feedback.send(context, reporter, "report-created", "player", target.name(), "id", report.id());

    if (context.config().broadcastReports()) {
      Feedback.broadcast(context, "report-broadcast", "player", target.name(), "reporter", reporter.getName(),
          "reason", reasonId, "id", report.id(), "age", Text.age(report.createdAt(), context.moderation().now()));
    }

    context.moderation().save();

    return true;
  }

  @Override
  public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
      @NotNull String label, @NotNull String[] args) {
    if (!(sender instanceof Player)) {
      return List.of();
    }

    if (args.length == 1) {
      return Bukkit.getOnlinePlayers().stream()
          .filter(player -> !player.equals(sender))
          .map(player -> player.getName().toLowerCase(Locale.ROOT))
          .toList();
    }

    if (args.length == 2) {
      return context.config().reasonIds();
    }

    return List.of();
  }
}
