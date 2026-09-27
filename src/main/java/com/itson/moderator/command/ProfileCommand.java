package com.itson.moderator.command;

import com.itson.moderator.model.PlayerRecord;
import com.itson.moderator.model.Punishment;
import com.itson.moderator.model.ReportStatus;
import com.itson.moderator.model.Target;
import com.itson.moderator.util.Text;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /mod profile <player>}: the one screen a moderator needs before
 * deciding, gathering identity, address, active sanctions and open reports.
 */
public final class ProfileCommand implements SubCommand {

  @Override
  public @NotNull String name() {
    return "profile";
  }

  @Override
  public @NotNull List<String> aliases() {
    return List.of("info", "user");
  }

  @Override
  public @NotNull String permission() {
    return "moderator.history";
  }

  @Override
  public @NotNull String arguments() {
    return "<player>";
  }

  @Override
  public @NotNull String description() {
    return "Shows everything known about a player";
  }

  @Override
  public void execute(@NotNull ModContext context, @NotNull CommandSender sender, @NotNull String[] args) {
    if (args.length == 0) {
      Feedback.send(context, sender, "usage", "usage", "/profile " + arguments());

      return;
    }

    Optional<Target> target = Args.resolveTarget(context, sender, args[0]);

    if (target.isEmpty()) {
      return;
    }

    PlayerRecord record = context.players().record(target.get().id());
    Instant now = context.moderation().now();

    long active = context.moderation().history(target.get().id()).stream().filter(punishment -> punishment.countsAt(now))
        .count();
    long total = context.moderation().history(target.get().id()).size();
    long reports = context.moderation().countReports(ReportStatus.OPEN);
    long against = context.moderation().reportsInvolving(target.get().id(), Integer.MAX_VALUE).stream()
        .filter(report -> report.status() == ReportStatus.OPEN && report.target().equals(target.get().id())).count();

    Feedback.send(context, sender, "profile-header", "player", target.get().name(), "uuid", target.get().id().toString());

    Feedback.send(context, sender, "profile-entry", "key", "Address", "value",
        record.lastIp() == null ? "unknown" : record.lastIp());
    Feedback.send(context, sender, "profile-entry", "key", "First seen", "value",
        Text.timestamp(record.firstSeen()));
    Feedback.send(context, sender, "profile-entry", "key", "Last seen", "value", Text.timestamp(record.lastSeen()));
    Feedback.send(context, sender, "profile-entry", "key", "Active sanctions", "value", String.valueOf(active));
    Feedback.send(context, sender, "profile-entry", "key", "Total entries", "value", String.valueOf(total));
    Feedback.send(context, sender, "profile-entry", "key", "Open reports", "value", String.valueOf(against));
    Feedback.send(context, sender, "profile-entry", "key", "Open reports on the server", "value",
        String.valueOf(reports));
    Feedback.send(context, sender, "profile-entry", "key", "Muted", "value",
        context.moderation().isMuted(target.get().id()) ? "yes" : "no");

    for (Punishment punishment : latestNotes(context, target.get())) {
      Feedback.send(context, sender, "profile-note", "date", Text.timestamp(punishment.createdAt()), "staff",
          punishment.staffName(), "reason", punishment.reason());
    }
  }

  @Override
  public @NotNull List<String> complete(@NotNull ModContext context, @NotNull CommandSender sender,
      @NotNull String[] args) {
    return args.length <= 1 ? Args.onlineNames() : List.of();
  }

  private static List<Punishment> latestNotes(ModContext context, Target target) {
    return context.moderation().notes(target.id()).stream().limit(3).toList();
  }
}
