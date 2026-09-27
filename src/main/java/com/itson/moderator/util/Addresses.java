package com.itson.moderator.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/**
 * Validation of address literals, so {@code /mod banip} never turns a typo into
 * a DNS lookup on the main thread.
 */
public final class Addresses {

  private static final Pattern IPV4 =
      Pattern.compile("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");

  private static final String SCOPE_SUFFIX = "%";

  private Addresses() {
  }

  /** True for a bare IPv4 or IPv6 literal, ignoring any IPv6 scope suffix. */
  public static boolean isLiteral(@Nullable String raw) {
    if (raw == null || raw.isBlank()) {
      return false;
    }

    String candidate = stripScope(raw.trim());

    if (candidate.isEmpty() || candidate.length() > 45) {
      return false;
    }

    if (IPV4.matcher(candidate).matches()) {
      return true;
    }

    // An IPv6 literal always carries at least two colons, which is what keeps
    // "1.2.3.4:25565" from passing as a hostname and port.
    return candidate.chars().filter(character -> character == ':').count() >= 2
        && candidate.chars().allMatch(character ->
        Character.digit(character, 16) >= 0 || character == ':' || character == '.');
  }

  /** Parses a literal without ever touching DNS. */
  public static Optional<InetAddress> parse(@Nullable String raw) {
    if (!isLiteral(raw)) {
      return Optional.empty();
    }

    try {
      return Optional.of(InetAddress.getByName(stripScope(raw.trim())));
    } catch (UnknownHostException exception) {
      return Optional.empty();
    }
  }

  /** Removes the {@code %iface} suffix the JVM adds to IPv6 addresses. */
  public static String stripScope(String raw) {
    int percent = raw.indexOf(SCOPE_SUFFIX);

    return percent < 0 ? raw : raw.substring(0, percent);
  }
}
