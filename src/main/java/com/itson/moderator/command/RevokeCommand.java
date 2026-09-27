package com.itson.moderator.command;

import com.itson.moderator.model.Punishment;
import com.itson.moderator.model.PunishmentType;
import com.itson.moderator.model.Target;
import com.itson.moderator.service.TargetResolver;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * Backs {@code /mod unmute}, {@code /mod unban} and {@code /mod unbanip}.
 *
 * <p>Revoking always works on the most recent active punishment of that type and
 * reports what it lifted, because the staff member usually needs to tell the
 * player why they are free again.
 */
public final class RevokeCommand implements SubCommand {

  private final String name;

  private final List<String> aliases;

  private final String permission;

  private final PunishmentType type;

  private final boolean acceptsAddress;

  private final String description;

  private RevokeCommand(String name, List<String> aliases, String permission, PunishmentType type,
      boolean acceptsAddress, String description) {
    this.name = name;
    this.aliases = aliases;
    this.permission = permission;
    this.type = type;
    this.acceptsAddress = acceptsAddress;
    this.description = description;
  }

  public static @NotNull List<SubCommand> all() {
    return List.of(
        new RevokeCommand("unmute", List.of(), "moderator.unmute", PunishmentType.MUTE, false, "Lifts a mute"),
        new RevokeCommand("unban", List.of(), "moderator.unban", PunishmentType.BAN, false, "Lifts a ban"),
        new RevokeCommand("unbanip", List.of(), "moderator.banip", PunishmentType.BAN_IP, true,
            "Lifts an address ban"));
  }

  @Override
  public @NotNull String name() {
    return name;
  }

  @Override
  public @NotNull List<String> aliases() {
    return aliases;
  }

  @Override
  public @NotNull String permission() {
    return permission;
  }

  @Override
  public @NotNull String arguments() {
    return acceptsAddress ? "<player|ip> [note]" : "<player> [note]";
  }

  @Override
  public @NotNull String description() {
    return description;
  }

  @Override
  public void execute(@NotNull ModContext context, @NotNull CommandSender sender, @NotNull String[] args) {
    if (args.length == 0) {
      Feedback.send(context, sender, "usage", "usage", "/" + name + " " + arguments());

      return;
    }

    TargetResolver.Resolution resolution = resolve(context, args[0]);

    if (!resolution.found()) {
      if (resolution.ambiguous()) {
        Feedback.send(context, sender, "target-ambiguous", "input", resolution.input(), "candidates",
            String.join(", ", resolution.candidates()));
      } else {
        Feedback.send(context, sender, "target-not-found", "input", resolution.input());
      }

      return;
    }

    Target target = resolution.target();
    Staff staff = Staff.of(sender);
    String note = Args.join(args, 1, " ");

    List<Punishment> revoked = context.moderation().revoke(target, type, staff.name(),
        note.isBlank() ? null : note);

    if (revoked.isEmpty()) {
      Feedback.send(context, sender, "nothing-active", "type", type.label(), "player", target.name());

      return;
    }

    Punishment lifted = revoked.get(0);
    String lifetime = lifted.lifetimeAt(context.moderation().now());

    Feedback.send(context, sender, "revoke-staff", "type", type.label(), "player", target.name(), "reason",
        lifted.reason(), "duration", lifetime, "staff", staff.name());

    if (context.config().broadcastPunishments()) {
      Feedback.broadcast(context, "revoke-broadcast", "type", type.label(), "player", target.name(), "reason",
          lifted.reason(), "staff", staff.name());
    }

    context.moderation().save();
  }

  @Override
  public @NotNull List<String> complete(@NotNull ModContext context, @NotNull CommandSender sender,
      @NotNull String[] args) {
    return args.length <= 1 ? Bukkit.getOnlinePlayers().stream().map(player -> player.getName().toLowerCase(Locale.ROOT))
        .toList() : List.of();
  }

  private TargetResolver.Resolution resolve(ModContext context, String input) {
    TargetResolver.Resolution player = context.targets().resolve(input);

    if (player.found() || !acceptsAddress) {
      return player;
    }

    Optional<String> address = context.targets().resolveAddress(input);

    if (address.isEmpty()) {
      return player;
    }

    return context.targets().byAddress(address.get());
  }
}
