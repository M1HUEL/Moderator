package com.itson.moderator.command;

import com.itson.moderator.config.Reason;
import com.itson.moderator.model.PlayerRecord;
import com.itson.moderator.model.Punishment;
import com.itson.moderator.model.PunishmentType;
import com.itson.moderator.model.Target;
import com.itson.moderator.service.ModerationService;
import com.itson.moderator.service.TargetResolver;
import com.itson.moderator.util.Addresses;
import com.itson.moderator.util.Durations;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * Backs every {@code /mod <type>} sanction: {@code mute}, {@code ban},
 * {@code banip}, {@code kick}, {@code warn} and {@code note}.
 *
 * <p>They differ only in whether a duration is required, so they are one class
 * built six times rather than six near identical ones. The grammar is
 * {@code /mod <type> <player> [duration] [reason] [-s]}, with the duration
 * mandatory for the types that can expire, where {@code perm} means no expiry.
 *
 * <p>A reason is either one of the ids from the {@code reasons} config section,
 * in which case its label is stored, or any free text.
 */
public final class PunishCommand implements SubCommand {

  private static final List<String> FLAGS = List.of("s");

  private static final String FALLBACK_REASON = "No reason given";

  private final String name;

  private final List<String> aliases;

  private final String permission;

  private final PunishmentType type;

  private final boolean durationRequired;

  private final String description;

  private PunishCommand(String name, List<String> aliases, String permission, PunishmentType type,
      boolean durationRequired, String description) {
    this.name = name;
    this.aliases = aliases;
    this.permission = permission;
    this.type = type;
    this.durationRequired = durationRequired;
    this.description = description;
  }

  /** The six sanction tools, in the order they appear in help. */
  public static @NotNull List<SubCommand> all() {
    return List.of(
        of("mute", List.of("tempmute"), "moderator.mute", PunishmentType.MUTE, "Mutes a player"),
        of("ban", List.of("tempban"), "moderator.ban", PunishmentType.BAN, "Bans a player"),
        of("banip", List.of("tempbanip"), "moderator.banip", PunishmentType.BAN_IP, "Bans an address"),
        of("kick", List.of(), "moderator.kick", PunishmentType.KICK, "Kicks a player"),
        of("warn", List.of(), "moderator.warn", PunishmentType.WARN, "Warns a player"),
        of("note", List.of(), "moderator.note", PunishmentType.NOTE, "Leaves a staff note"));
  }

  private static SubCommand of(String name, List<String> aliases, String permission, PunishmentType type,
      String description) {
    return new PunishCommand(name, aliases, permission, type, type.acceptsDuration(), description);
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
    return durationRequired ? "<player|ip> <duration|perm> [reason] [-s]" : "<player> [reason] [-s]";
  }

  @Override
  public @NotNull String description() {
    return description;
  }

  @Override
  public void execute(@NotNull ModContext context, @NotNull CommandSender sender, @NotNull String[] args) {
    boolean[] silent = {false};
    String[] cleaned = Args.withoutFlags(args, FLAGS, silent);

    if (cleaned.length == 0) {
      usage(context, sender);

      return;
    }

    TargetResolver.Resolution resolution = resolve(context, cleaned[0]);

    if (!resolution.found()) {
      reportMiss(context, sender, resolution);

      return;
    }

    Target target = resolution.target();
    int index = 1;
    Instant expiresAt = null;

    if (durationRequired) {
      if (cleaned.length < 2) {
        usage(context, sender);

        return;
      }

      Optional<Duration> duration = readDuration(context, sender, cleaned[1]);

      if (duration == null) {
        return;
      }

      expiresAt = duration.map(value -> context.moderation().now().plus(value)).orElse(null);
      index = 2;
    }

    if (type == PunishmentType.KICK && !target.online()) {
      Feedback.send(context, sender, "target-offline", "player", target.name());

      return;
    }

    Staff staff = Staff.of(sender);
    Optional<Duration> cooldown = context.cooldowns().remaining(staff.id(), name, context.moderation().now());

    if (cooldown.isPresent()) {
      Feedback.coolingDown(context, sender, cooldown.get());

      return;
    }

    ReasonText reason = readReason(context, cleaned, index);

    ModerationService.Spec spec = new ModerationService.Spec(target, type, reason.id(), reason.text(), staff.id(),
        staff.name(), expiresAt, silent[0]);

    Optional<ModerationService.Outcome> outcome = context.moderation().apply(spec);

    if (outcome.isEmpty()) {
      return;
    }

    context.cooldowns().start(staff.id(), name, context.moderation().now());

    report(context, sender, staff, target, outcome.get(), reason.text(), silent[0]);
    context.moderation().save();
  }

  @Override
  public @NotNull List<String> complete(@NotNull ModContext context, @NotNull CommandSender sender,
      @NotNull String[] args) {
    if (args.length <= 1) {
      return onlineNames();
    }

    int durationSlot = durationRequired ? 2 : 1;

    if (args.length == durationSlot) {
      return List.of("perm", "10m", "1h", "12h", "1d", "7d", "30d");
    }

    if (args.length > durationSlot) {
      return context.config().reasonIdsFor(type);
    }

    return List.of();
  }

  /**
   * Parses the duration argument.
   *
   * @return the duration, empty when the punishment is permanent, or null when
   *         the argument was rejected and the caller should stop
   */
  private Optional<Duration> readDuration(ModContext context, CommandSender sender, String raw) {
    if (Durations.isPermanent(raw)) {
      return Optional.empty();
    }

    Optional<Duration> parsed = Durations.parse(raw);

    if (parsed.isEmpty()) {
      Feedback.invalid(context, sender, "invalid-duration", raw);

      return null;
    }

    return parsed;
  }

  private void report(ModContext context, CommandSender sender, Staff staff, Target target,
      ModerationService.Outcome outcome, String reason, boolean silent) {
    String lifetime = outcome.primary().lifetimeAt(context.moderation().now());

    for (Punishment replaced : outcome.replaced()) {
      Feedback.send(context, sender, "punish-replaced", "type", type.label(), "player", target.name(),
          "previous-reason", replaced.reason(), "previous-duration", replaced.lifetimeAt(context.moderation().now()));
    }

    Feedback.send(context, sender, "punish-staff", "type", type.label(), "player", target.name(), "reason", reason,
        "duration", lifetime, "staff", staff.name());

    if (!silent && context.config().broadcastPunishments()) {
      Feedback.broadcast(context, "punish-broadcast", "type", type.label(), "player", target.name(), "reason", reason,
          "duration", lifetime, "staff", staff.name());
    }

    for (Punishment followUp : outcome.followUps()) {
      Feedback.broadcast(context, "auto-punish", "type", followUp.type().label(), "player", followUp.targetName(),
          "reason", followUp.reason(), "duration", followUp.lifetimeAt(context.moderation().now()));
    }
  }

  /**
   * Resolves the first argument, which is a player for every type except
   * {@code banip}, where a raw address is also accepted.
   */
  private TargetResolver.Resolution resolve(ModContext context, String input) {
    if (type != PunishmentType.BAN_IP) {
      return context.targets().resolve(input);
    }

    TargetResolver.Resolution player = context.targets().resolve(input);

    if (player.found()) {
      return player;
    }

    Optional<String> address = context.targets().resolveAddress(input);

    if (address.isEmpty()) {
      return player;
    }

    return TargetResolver.Resolution.found(addressTarget(context, address.get()));
  }

  /**
   * Turns an address into a target: the player it belongs to when one is known,
   * otherwise a record derived from the address so repeated bans of the same
   * address land on the same history.
   */
  private Target addressTarget(ModContext context, String address) {
    String cleaned = Addresses.stripScope(address);

    for (PlayerRecord record : context.players().all()) {
      if (cleaned.equals(record.lastIp())) {
        return Target.of(record.id(), record.name(), cleaned);
      }
    }

    UUID id = UUID.nameUUIDFromBytes(("Moderator:ip:" + cleaned).getBytes(StandardCharsets.UTF_8));

    return new Target(id, cleaned, cleaned, false);
  }

  private void reportMiss(ModContext context, CommandSender sender, TargetResolver.Resolution resolution) {
    if (resolution.ambiguous()) {
      Feedback.send(context, sender, "target-ambiguous", "input", resolution.input(), "candidates",
          String.join(", ", resolution.candidates()));

      return;
    }

    Feedback.send(context, sender, "target-not-found", "input", resolution.input());
  }

  /** A reason, either a configured id with its label or the free text staff typed. */
  private record ReasonText(String id, String text) {
  }

  private ReasonText readReason(ModContext context, String[] args, int from) {
    String free = Args.join(args, from, " ");

    if (free.isBlank()) {
      String fallback = context.messages().has("fallback-reason") ? context.messages().text("fallback-reason")
          : FALLBACK_REASON;

      return new ReasonText(null, fallback);
    }

    Optional<Reason> configured = context.config().reason(free);

    if (configured.isPresent() && configured.get().supports(type)) {
      return new ReasonText(configured.get().id(), configured.get().label());
    }

    return new ReasonText(null, free);
  }

  private void usage(ModContext context, CommandSender sender) {
    Feedback.send(context, sender, "usage", "usage", "/" + name + " " + arguments());
  }

  private static List<String> onlineNames() {
    return Bukkit.getOnlinePlayers().stream().map(player -> player.getName().toLowerCase(Locale.ROOT)).toList();
  }
}
