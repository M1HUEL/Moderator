package com.itson.moderator.util;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Rendering helpers shared by commands, listeners and storage. */
public final class Text {

  private static final MiniMessage MINI = MiniMessage.miniMessage();

  /** Short enough to read in a line of chat, precise enough to compare entries. */
  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());

  private Text() {
  }

  /**
   * Parses a config string as MiniMessage.
   *
   * <p>Use this only for strings the server owner wrote. Anything that came from a
   * player must go through the overload with a tag, or a name containing
   * {@code <} would be read as markup.
   */
  public static Component parse(@NotNull String miniMessage) {
    return MINI.deserialize(miniMessage);
  }

  /**
   * Renders a template with a single tag, inserted literally.
   *
   * <p>Used for player names, which must never be read as MiniMessage tags.
   */
  public static Component parse(@NotNull String miniMessage, @NotNull String tag, @Nullable String value) {
    return MINI.deserialize(miniMessage, Placeholder.unparsed(tag, value == null ? "" : value));
  }

  /** Formats an instant for staff facing output, for example {@code 27/09 14:02}. */
  public static String timestamp(Instant instant) {
    return TIMESTAMP.format(instant);
  }

  /**
   * Approximate age of a record, for example {@code 3d} or {@code 12m}.
   *
   * <p>A record dated in the future, from a clock that moved backwards, reports
   * {@code 0s} rather than a negative age.
   */
  public static String age(Instant then, Instant now) {
    Duration elapsed = Duration.between(then, now);

    if (elapsed.isNegative()) {
      return "0s";
    }

    return Durations.format(elapsed);
  }
}
