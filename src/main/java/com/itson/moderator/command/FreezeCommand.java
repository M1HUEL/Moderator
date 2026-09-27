package com.itson.moderator.command;

import java.util.List;
import java.util.Optional;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /mod freeze <player> [reason]} and {@code /mod unfreeze <player>}.
 *
 * <p>Two subcommands in one class because they share the resolution and the
 * broadcast, and because freezing somebody and then having to look up how to
 * undo it is exactly the wrong moment.
 */
public final class FreezeCommand implements SubCommand {

  /** Flag that freezes a player who is not the sender, even for staff. */
  private static final String SELF_FLAG = "-self";

  private static final List<String> SELF_FLAGS = List.of("self");

  private final boolean freeze;

  private FreezeCommand(boolean freeze) {
    this.freeze = freeze;
  }

  public static @NotNull List<SubCommand> all() {
    return List.of(new FreezeCommand(true), new FreezeCommand(false));
  }

  @Override
  public @NotNull String name() {
    return freeze ? "freeze" : "unfreeze";
  }

  @Override
  public @NotNull List<String> aliases() {
    return freeze ? List.of("freezeplayer") : List.of();
  }

  @Override
  public @NotNull String permission() {
    return freeze ? "moderator.freeze" : "moderator.unfreeze";
  }

  @Override
  public boolean playerOnly() {
    return true;
  }

  @Override
  public @NotNull String arguments() {
    return freeze ? "<player> [reason] " + SELF_FLAG : "<player>";
  }

  @Override
  public @NotNull String description() {
    return freeze ? "Freezes a player in place" : "Releases a frozen player";
  }

  @Override
  public void execute(@NotNull ModContext context, @NotNull CommandSender sender, @NotNull String[] args) {
    boolean[] explicit = {false};
    String[] cleaned = Args.withoutFlags(args, SELF_FLAGS, explicit);

    if (cleaned.length == 0) {
      Feedback.send(context, sender, "usage", "usage", "/" + name() + " " + arguments());

      return;
    }

    Optional<com.itson.moderator.model.Target> resolved = Args.resolveTarget(context, sender, cleaned[0]);

    if (resolved.isEmpty()) {
      return;
    }

    if (!resolved.get().online()) {
      Feedback.send(context, sender, "target-offline", "player", resolved.get().name());

      return;
    }

    Player target = org.bukkit.Bukkit.getPlayer(resolved.get().id());

    if (target == null) {
      Feedback.send(context, sender, "target-offline", "player", resolved.get().name());

      return;
    }

    if (sender.equals(target) && freeze && !explicit[0]) {
      Feedback.send(context, sender, "freeze-self", "flag", SELF_FLAG);

      return;
    }

    if (freeze) {
      apply(context, sender, target, Args.join(cleaned, 1, " "));

      return;
    }

    release(context, sender, target);
  }

  @Override
  public @NotNull List<String> complete(@NotNull ModContext context, @NotNull CommandSender sender,
      @NotNull String[] args) {
    return args.length <= 1 ? Args.onlineNames() : List.of();
  }

  private void apply(ModContext context, CommandSender sender, Player target, String reason) {
    if (!context.freeze().freeze(target)) {
      Feedback.send(context, sender, "already-frozen", "player", target.getName());

      return;
    }

    String text = reason.isBlank() ? context.messages().text("fallback-reason") : reason;

    Feedback.send(context, target, "frozen-screen", "reason", text, "staff", sender.getName());
    Feedback.send(context, sender, "frozen-staff", "player", target.getName(), "reason", text);
    Feedback.broadcast(context, "frozen-broadcast", "player", target.getName(), "reason", text,
        "staff", sender.getName());
  }

  private void release(ModContext context, CommandSender sender, Player target) {
    if (!context.freeze().unfreeze(target)) {
      Feedback.send(context, sender, "not-frozen", "player", target.getName());

      return;
    }

    Feedback.send(context, target, "unfrozen-screen", "staff", sender.getName());
    Feedback.send(context, sender, "unfrozen-staff", "player", target.getName());
    Feedback.broadcast(context, "unfrozen-broadcast", "player", target.getName(), "staff", sender.getName());
  }
}
