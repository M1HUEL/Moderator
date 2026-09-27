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

  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());

  private Text() {
  }

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

  /** Approximate age of a record, for example {@code 3d} or {@code 12m}. */
  public static String age(Instant then, Instant now) {
    Duration elapsed = Duration.between(then, now);

    if (elapsed.isNegative()) {
      return "0s";
    }

    return Durations.format(elapsed);
  }
}
