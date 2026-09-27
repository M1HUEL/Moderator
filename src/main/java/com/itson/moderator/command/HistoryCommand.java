package com.itson.moderator.command;

import com.itson.moderator.model.Punishment;
import com.itson.moderator.model.PunishmentType;
import com.itson.moderator.model.Target;
import com.itson.moderator.util.Text;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /mod history <player> [type]}: the audit trail every moderator ends up
 * asking for, newest entry first.
 */
public final class HistoryCommand implements SubCommand {

  @Override
  public @NotNull String name() {
    return "history";
  }

  @Override
  public @NotNull List<String> aliases() {
    return List.of("punishments", "hist");
  }

  @Override
  public @NotNull String permission() {
    return "moderator.history";
  }

  @Override
  public @NotNull String arguments() {
    return "<player> [type]";
  }

  @Override
  public @NotNull String description() {
    return "Shows the sanction history of a player";
  }

  @Override
  public void execute(@NotNull ModContext context, @NotNull CommandSender sender, @NotNull String[] args) {
    if (args.length == 0) {
      Feedback.send(context, sender, "usage", "usage", "/history " + arguments());

      return;
    }

    Optional<Target> target = Args.resolveTarget(context, sender, args[0]);

    if (target.isEmpty()) {
      return;
    }

    PunishmentType filter = null;

    if (args.length > 1) {
      Optional<PunishmentType> parsed = PunishmentType.byId(args[1]);

      if (parsed.isEmpty()) {
        Feedback.invalid(context, sender, "invalid-type", args[1]);

        return;
      }

      filter = parsed.get();
    }

    List<Punishment> history = filter == null ? context.moderation().history(target.get().id())
        : context.moderation().history(target.get().id(), filter);
    int limit = context.config().historyLimit();
    int shown = Math.min(limit, history.size());

    Feedback.send(context, sender, "history-header", "player", target.get().name(), "count", shown, "total",
        history.size());

    if (history.isEmpty()) {
      Feedback.send(context, sender, "history-empty", "player", target.get().name());

      return;
    }

    Instant now = context.moderation().now();

    for (int index = 0; index < shown; index++) {
      Punishment punishment = history.get(index);

      Feedback.send(context, sender, "history-entry", "index", index + 1, "type", punishment.type().label(),
          "reason", punishment.reason(), "staff", punishment.staffName(), "date", Text.timestamp(punishment.createdAt()),
          "duration", punishment.lifetimeAt(now), "state", punishment.stateAt(now));
    }

    if (history.size() > shown) {
      Feedback.send(context, sender, "history-truncated", "remaining", history.size() - shown);
    }
  }

  @Override
  public @NotNull List<String> complete(@NotNull ModContext context, @NotNull CommandSender sender,
      @NotNull String[] args) {
    if (args.length <= 1) {
      return Args.onlineNames();
    }

    if (args.length == 2) {
      return List.of("warn", "mute", "ban", "banip", "kick", "note");
    }

    return List.of();
  }
}
