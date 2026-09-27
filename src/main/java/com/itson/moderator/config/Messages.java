package com.itson.moderator.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An immutable snapshot of the {@code messages} section.
 *
 * <p>Every message is MiniMessage. Values coming from players (names, reasons,
 * note text) are inserted with {@link Placeholder#unparsed}, so a nickname full
 * of {@code <red>} tags is shown literally instead of colouring the screen.
 */
public final class Messages {

  private static final MiniMessage MINI = MiniMessage.miniMessage();

  private static final String FALLBACK = "<red>Missing message: <white><key>";

  private final Map<String, String> templates;

  private final Set<String> missing = ConcurrentHashMap.newKeySet();

  public Messages(Map<String, String> templates) {
    this.templates = Map.copyOf(new LinkedHashMap<>(templates));
  }

  public static Messages empty() {
    return new Messages(Map.of());
  }

  /**
   * Renders a template.
   *
   * @param key         the message key
   * @param placeholders alternating tag names and values, for example
   *                     {@code "player", "Steve", "reason", "Spam"}
   */
  public Component render(String key, Object... placeholders) {
    return MINI.deserialize(text(key), resolver(placeholders));
  }

  /** Renders a template with the {@code prefix} tag already applied. */
  public Component renderPrefixed(String key, Object... placeholders) {
    return MINI.deserialize(prefix() + text(key), resolver(placeholders));
  }

  /** The raw MiniMessage source, used when a message has to be embedded elsewhere. */
  public String text(String key) {
    String template = templates.get(key);

    if (template == null) {
      missing.add(key);

      return FALLBACK.replace("<key>", key);
    }

    return template;
  }

  /**
   * Substitutes {@code <name>} tags without going through MiniMessage.
   *
   * <p>Needed for the vanilla ban list, whose screen is not a MiniMessage
   * renderer, and for anything that has to end up as plain text in a log line.
   */
  public String renderPlain(String key, Object... placeholders) {
    String result = text(key);

    for (int index = 0; index + 1 < placeholders.length; index += 2) {
      String name = String.valueOf(placeholders[index]);
      String value = placeholders[index + 1] == null ? "" : String.valueOf(placeholders[index + 1]);

      result = result.replace("<" + name + ">", value);
    }

    return result;
  }

  public boolean has(String key) {
    return templates.containsKey(key);
  }

  public String prefix() {
    return templates.getOrDefault("prefix", "");
  }

  public List<String> keys() {
    return List.copyOf(templates.keySet());
  }

  /** Keys that were requested but absent, so {@code /mod reload} can report them. */
  public List<String> missingKeys() {
    return new ArrayList<>(missing);
  }

  private static TagResolver resolver(Object... placeholders) {
    if (placeholders.length == 0) {
      return TagResolver.empty();
    }

    TagResolver.Builder builder = TagResolver.builder();

    for (int index = 0; index + 1 < placeholders.length; index += 2) {
      String name = String.valueOf(placeholders[index]);
      String value = placeholders[index + 1] == null ? "" : String.valueOf(placeholders[index + 1]);

      builder.resolver(Placeholder.unparsed(name, value));
    }

    return builder.build();
  }

  @Override
  public String toString() {
    return "Messages[" + templates.size() + " templates]";
  }

  /** Convenience for building a map literal in tests. */
  public static @NotNull Map<String, String> mapOf(@Nullable String... pairs) {
    Map<String, String> map = new LinkedHashMap<>();

    for (int index = 0; index + 1 < pairs.length; index += 2) {
      map.put(pairs[index], pairs[index + 1]);
    }

    return map;
  }
}
