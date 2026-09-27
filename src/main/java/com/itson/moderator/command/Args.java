package com.itson.moderator.command;

import com.itson.moderator.model.Target;
import com.itson.moderator.service.TargetResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Argument plumbing shared by the sanction commands. */
public final class Args {

  private Args() {
  }

  /**
   * Removes flags such as {@code -s} from the arguments.
   *
   * <p>The flag is reported through {@code present} so the caller can tell the
   * difference between {@code /mod warn Steve -s} and {@code /mod warn Steve -s
   * because they are being weird}.
   *
   * <p>Every recognised flag sets {@code present[0]}, whatever its length, so a
   * multi character flag such as {@code -self} behaves like a single letter one.
   *
   * @param flags flags to pull out, lower case and without the dash
   */
  public static @NotNull String[] withoutFlags(@NotNull String[] args, @NotNull List<String> flags,
      @Nullable boolean[] present) {
    List<String> kept = new ArrayList<>(args.length);

    for (String arg : args) {
      if (arg.startsWith("-") && flags.contains(arg.substring(1).toLowerCase(Locale.ROOT))) {
        if (present != null) {
          present[0] = true;
        }

        continue;
      }

      kept.add(arg);
    }

    return kept.toArray(String[]::new);
  }

  /** Joins the arguments from an index on, so free text reasons keep their spaces. */
  public static @NotNull String join(@NotNull String[] args, int from, @NotNull String separator) {
    if (from >= args.length) {
      return "";
    }

    return String.join(separator, List.of(args).subList(from, args.length)).trim();
  }

  /** Everything after the target, used for tab completion of free text. */
  public static @NotNull List<String> tail(@NotNull String[] args, int from) {
    if (from >= args.length) {
      return List.of();
    }

    return List.of(args).subList(from, args.length);
  }

  /**
   * Resolves a target argument, reporting a miss or an ambiguous name to the
   * sender when it cannot be resolved.
   *
   * @return the target, or empty when the sender has already been told why not
   */
  public static @NotNull Optional<Target> resolveTarget(@NotNull ModContext context, @NotNull CommandSender sender,
      @NotNull String input) {
    TargetResolver.Resolution resolution = context.targets().resolve(input);

    if (resolution.found()) {
      return Optional.of(resolution.target());
    }

    if (resolution.ambiguous()) {
      Feedback.send(context, sender, "target-ambiguous", "input", resolution.input(), "candidates",
          String.join(", ", resolution.candidates()));
    } else {
      Feedback.send(context, sender, "target-not-found", "input", resolution.input());
    }

    return Optional.empty();
  }

  /** Names of connected players, lower cased for completion. */
  public static @NotNull List<String> onlineNames() {
    return Bukkit.getOnlinePlayers().stream().map(player -> player.getName().toLowerCase(Locale.ROOT)).toList();
  }
}
