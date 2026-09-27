package com.itson.moderator.command;

import com.itson.moderator.model.Report;
import com.itson.moderator.model.ReportStatus;
import com.itson.moderator.util.Text;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /mod reports [open|all]}: the queue staff work through, oldest first,
 * because an old report is the one about to go stale.
 */
public final class ReportsCommand implements SubCommand {

  private static final int MAX_LISTED = 10;

  @Override
  public @NotNull String name() {
    return "reports";
  }

  @Override
  public @NotNull List<String> aliases() {
    return List.of("queue");
  }

  @Override
  public @NotNull String permission() {
    return "moderator.reports";
  }

  @Override
  public @NotNull String arguments() {
    return "[open|all]";
  }

  @Override
  public @NotNull String description() {
    return "Lists pending player reports";
  }

  @Override
  public void execute(@NotNull ModContext context, @NotNull CommandSender sender, @NotNull String[] args) {
    boolean all = args.length > 0 && args[0].equalsIgnoreCase("all");
    List<ReportStatus> statuses =
        all ? List.of(ReportStatus.OPEN, ReportStatus.RESOLVED, ReportStatus.DISMISSED) : List.of(ReportStatus.OPEN);
    List<Report> reports = context.moderation().reports(statuses, MAX_LISTED);
    long open = context.moderation().countReports(ReportStatus.OPEN);

    Feedback.send(context, sender, "reports-header", "open", open);

    if (reports.isEmpty()) {
      Feedback.send(context, sender, "reports-empty");

      return;
    }

    for (int index = 0; index < reports.size(); index++) {
      Report report = reports.get(index);

      Feedback.send(context, sender, "report-entry", "index", index + 1, "id", report.id(), "target",
          report.targetName(), "reporter", report.reporterName(), "reason", report.reasonId(), "age",
          Text.age(report.createdAt(), context.moderation().now()), "status", report.status().label());
    }

    if (open > reports.size() && !all) {
      Feedback.send(context, sender, "reports-truncated", "remaining", open - reports.size());
    }
  }

  @Override
  public @NotNull List<String> complete(@NotNull ModContext context, @NotNull CommandSender sender,
      @NotNull String[] args) {
    return args.length <= 1 ? List.of("open", "all") : List.of();
  }
}
