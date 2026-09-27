package com.itson.moderator.command;

import com.itson.moderator.model.Report;
import com.itson.moderator.model.ReportStatus;
import com.itson.moderator.util.Text;
import java.util.List;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /mod resolve <id> [resolved|dismissed] [note]}: closes a report and, in
 * the same view, shows the full report so the outcome can be written against it.
 */
public final class ResolveCommand implements SubCommand {

  @Override
  public @NotNull String name() {
    return "resolve";
  }

  @Override
  public @NotNull List<String> aliases() {
    return List.of("report");
  }

  @Override
  public @NotNull String permission() {
    return "moderator.reports";
  }

  @Override
  public @NotNull String arguments() {
    return "<id> [resolved|dismissed] [note]";
  }

  @Override
  public @NotNull String description() {
    return "Closes a player report";
  }

  @Override
  public void execute(@NotNull ModContext context, @NotNull CommandSender sender, @NotNull String[] args) {
    if (args.length == 0) {
      Feedback.send(context, sender, "usage", "usage", "/resolve " + arguments());

      return;
    }

    Optional<Report> existing = context.moderation().report(args[0]);

    if (existing.isEmpty()) {
      Feedback.send(context, sender, "report-not-found", "id", args[0]);

      return;
    }

    Report report = existing.get();

    if (report.isOpen()) {
      show(context, sender, report);
    }

    if (args.length == 1) {
      Feedback.send(context, sender, "usage", "usage", "/resolve " + arguments());

      return;
    }

    ReportStatus status = ReportStatus.RESOLVED;

    if (args.length > 1) {
      Optional<ReportStatus> parsed = ReportStatus.byId(args[1]);

      if (parsed.isEmpty()) {
        Feedback.invalid(context, sender, "invalid-status", args[1]);

        return;
      }

      status = parsed.get();
    }

    if (status == ReportStatus.OPEN) {
      Feedback.invalid(context, sender, "invalid-status", args[1]);

      return;
    }

    if (!report.isOpen()) {
      Feedback.send(context, sender, "report-already-handled", "id", report.id(), "status", report.status().label());

      return;
    }

    String note = Args.join(args, 2, " ");
    Staff staff = Staff.of(sender);

    context.moderation().resolveReport(report.id(), status, staff.name(), note.isBlank() ? null : note);
    context.moderation().save();

    Feedback.send(context, sender, "report-resolved", "id", report.id(), "status", status.label(), "staff",
        staff.name());

    notifyReporter(context, report, status);
  }

  @Override
  public @NotNull List<String> complete(@NotNull ModContext context, @NotNull CommandSender sender,
      @NotNull String[] args) {
    if (args.length == 1) {
      return context.moderation().reports(ReportStatus.OPEN, 20).stream().map(Report::id).toList();
    }

    if (args.length == 2) {
      return List.of("resolved", "dismissed");
    }

    return List.of();
  }

  private static void show(ModContext context, CommandSender sender, Report report) {
    Feedback.send(context, sender, "report-detail", "id", report.id(), "target", report.targetName(), "reporter",
        report.reporterName(), "reason", report.reasonId(), "details",
        report.details() == null ? "-" : report.details(), "date", Text.timestamp(report.createdAt()));
  }

  /** Tells the player who filed the report that staff looked at it. */
  private static void notifyReporter(ModContext context, Report report, ReportStatus status) {
    Player reporter = Bukkit.getPlayer(report.reporter());

    if (reporter == null) {
      return;
    }

    reporter.sendMessage(context.messages().renderPrefixed("report-handled", "id", report.id(), "status",
        status.label()));
  }
}
