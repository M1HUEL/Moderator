package com.itson.moderator.util;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Parsing and formatting of the human durations staff type, for example
 * {@code 30m}, {@code 2h30m} or {@code 7d}.
 *
 * <p>A bare number is read as minutes, which is by far the most common unit when
 * sanctioning. {@code perm}, {@code permanent} and {@code -1} all mean no expiry.
 */
public final class Durations {

  /** Units from largest to smallest, so output is always the most readable pair. */
  private static final List<Map.Entry<Character, Duration>> UNITS = List.of(
      Map.entry('y', Duration.ofDays(365)),
      Map.entry('w', Duration.ofDays(7)),
      Map.entry('d', Duration.ofDays(1)),
      Map.entry('h', Duration.ofHours(1)),
      Map.entry('m', Duration.ofMinutes(1)),
      Map.entry('s', Duration.ofSeconds(1)));

  private static final Pattern TOKEN = Pattern.compile("(\\d+)\\s*([smhdwy])", Pattern.CASE_INSENSITIVE);

  private static final int MAX_UNITS = 8;

  private static final String PERMANENT_WORD = "permanent";

  private static Duration unitFor(char unit) {
    for (Map.Entry<Character, Duration> entry : UNITS) {
      if (entry.getKey() == unit) {
        return entry.getValue();
      }
    }

    return null;
  }

  private Durations() {
  }

  /** True when the argument asks for a punishment that never expires. */
  public static boolean isPermanent(@Nullable String raw) {
    if (raw == null) {
      return false;
    }

    String normalized = raw.trim().toLowerCase(Locale.ROOT);

    return normalized.equals("perm") || normalized.equals(PERMANENT_WORD) || normalized.equals("-1");
  }

  /**
   * Parses a duration such as {@code 45s}, {@code 2h30m} or {@code 1w2d}.
   *
   * <p>Every character of the input has to be consumed by a unit token, so
   * {@code 5x} and {@code 10h junk} are rejected instead of being silently
   * truncated. A bare number is interpreted as minutes.
   */
  public static Optional<Duration> parse(@Nullable String raw) {
    if (raw == null) {
      return Optional.empty();
    }

    String input = raw.trim().toLowerCase(Locale.ROOT).replace(" ", "");

    if (input.isEmpty() || isPermanent(input)) {
      return Optional.empty();
    }

    if (input.chars().allMatch(Character::isDigit)) {
      try {
        Duration minutes = Duration.ofMinutes(Long.parseLong(input));

        return minutes.isZero() ? Optional.empty() : Optional.of(minutes);
      } catch (NumberFormatException exception) {
        return Optional.empty();
      }
    }

    Matcher matcher = TOKEN.matcher(input);
    Duration total = Duration.ZERO;
    int consumed = 0;
    int tokens = 0;

    while (matcher.find()) {
      Duration unit = unitFor(matcher.group(2).charAt(0));
      long amount;

      try {
        amount = Long.parseLong(matcher.group(1));
      } catch (NumberFormatException exception) {
        return Optional.empty();
      }

      if (unit == null) {
        return Optional.empty();
      }

      try {
        total = total.plus(unit.multipliedBy(amount));
      } catch (ArithmeticException exception) {
        return Optional.empty();
      }

      consumed += matcher.end() - matcher.start();
      tokens++;
    }

    if (tokens == 0 || tokens > MAX_UNITS || consumed != input.length() || total.isZero()) {
      return Optional.empty();
    }

    return Optional.of(total);
  }

  /**
   * Renders a duration as at most two units, for example {@code 2d 4h} or
   * {@code 45s}. Sub-second remainders collapse to {@code 0s} so the text is
   * never empty.
   */
  public static String format(@NotNull Duration duration) {
    if (duration.isZero() || duration.isNegative()) {
      return "0s";
    }

    long seconds = duration.toSeconds();
    StringBuilder builder = new StringBuilder();
    int used = 0;

    for (Map.Entry<Character, Duration> entry : UNITS) {
      if (used == 2) {
        break;
      }

      long unitSeconds = entry.getValue().toSeconds();
      long amount = seconds / unitSeconds;

      if (amount <= 0) {
        continue;
      }

      if (!builder.isEmpty()) {
        builder.append(' ');
      }

      builder.append(amount).append(entry.getKey());
      seconds -= amount * unitSeconds;
      used++;
    }

    if (builder.isEmpty()) {
      return "0s";
    }

    if (seconds > 0 && used < 2) {
      builder.append(' ').append(seconds).append('s');
    }

    return builder.toString();
  }

  /**
   * Human wording for a punishment end instant, used in kick screens and history
   * rows: {@code in 2d 4h} while running, {@code expired} once past.
   */
  public static String describeUntil(@Nullable Instant end, @NotNull Instant now) {
    if (end == null) {
      return PERMANENT_WORD;
    }

    Duration left = Duration.between(now, end);

    if (left.isNegative() || left.isZero()) {
      return "expired";
    }

    return "in " + format(left);
  }
}
