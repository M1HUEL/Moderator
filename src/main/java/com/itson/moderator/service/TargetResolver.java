package com.itson.moderator.service;

import com.itson.moderator.model.PlayerRecord;
import com.itson.moderator.model.Target;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Turns the free text staff type into something actionable. */
public final class TargetResolver {

  private final PlayerRegistry registry;

  public TargetResolver(@NotNull PlayerRegistry registry) {
    this.registry = registry;
  }

  /**
   * Resolves a name, a partial name or a raw UUID.
   *
   * <p>Names the server has never seen are reported as missing rather than looked
   * up over the network, because resolving an unknown Mojang name blocks the main
   * thread. The player therefore has to have joined at least once.
   */
  public @NotNull Resolution resolve(@Nullable String input) {
    if (input == null || input.isBlank()) {
      return Resolution.missing(input);
    }

    String name = input.trim();

    Optional<UUID> rawUuid = parseUuid(name);

    if (rawUuid.isPresent()) {
      return fromUuid(rawUuid.get());
    }

    Player online = Bukkit.getPlayerExact(name);

    if (online != null) {
      return Resolution.found(target(online));
    }

    List<Player> partial = Bukkit.matchPlayer(name);

    if (partial.size() == 1) {
      return Resolution.found(target(partial.get(0)));
    }

    if (partial.size() > 1) {
      return Resolution.ambiguous(name, partial.stream().map(Player::getName).toList());
    }

    Optional<PlayerRecord> remembered = registry.byName(name);

    if (remembered.isPresent()) {
      PlayerRecord record = remembered.get();
      Player connected = Bukkit.getPlayer(record.id());

      return Resolution.found(new Target(record.id(), record.name(), record.lastIp(), connected != null));
    }

    OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);

    if (cached != null && (cached.hasPlayedBefore() || cached.isOnline())) {
      UUID id = cached.getUniqueId();
      String resolved = cached.getName() == null ? name : cached.getName();

      return Resolution.found(Target.of(id, resolved, registry.lastIp(id)));
    }

    return Resolution.missing(name);
  }

  /** Resolves an address typed directly, for example {@code 1.2.3.4}. */
  public Optional<String> resolveAddress(@Nullable String input) {
    if (input == null || input.isBlank()) {
      return Optional.empty();
    }

    String name = input.trim();

    if (name.chars().allMatch(character -> Character.isDigit(character) || character == '.' || character == ':')) {
      return Optional.of(name);
    }

    Player online = Bukkit.getPlayerExact(name);

    if (online != null && online.getAddress() != null) {
      return Optional.of(online.getAddress().getAddress().getHostAddress());
    }

    return registry.byName(name).map(PlayerRecord::lastIp).filter(ip -> ip != null);
  }

  /**
   * Resolves a record by address, for {@code /mod unbanip 1.2.3.4}.
   *
   * <p>Only players the plugin has actually seen are returned, so a lift reaches
   * the right history entry instead of a synthetic one.
   */
  public @NotNull Resolution byAddress(@NotNull String address) {
    String cleaned = com.itson.moderator.util.Addresses.stripScope(address);

    for (PlayerRecord record : registry.all()) {
      if (cleaned.equals(record.lastIp())) {
        Player online = Bukkit.getPlayer(record.id());

        return Resolution.found(new Target(record.id(), record.name(), cleaned, online != null));
      }
    }

    return Resolution.missing(cleaned);
  }

  private Resolution fromUuid(UUID id) {
    Player online = Bukkit.getPlayer(id);

    if (online != null) {
      return Resolution.found(target(online));
    }

    String name = registry.all().stream()
        .filter(record -> record.id().equals(id))
        .map(PlayerRecord::name)
        .findFirst()
        .orElse(null);

    if (name == null) {
      name = Bukkit.getOfflinePlayer(id).getName();
    }

    if (name == null) {
      return Resolution.missing(id.toString());
    }

    return Resolution.found(Target.of(id, name, registry.lastIp(id)));
  }

  private static Target target(Player player) {
    String ip = player.getAddress() == null ? null : player.getAddress().getAddress().getHostAddress();

    return new Target(player.getUniqueId(), player.getName(), ip, true);
  }

  private static Optional<UUID> parseUuid(String name) {
    if (name.length() == 36) {
      try {
        return Optional.of(UUID.fromString(name));
      } catch (IllegalArgumentException exception) {
        return Optional.empty();
      }
    }

    if (name.length() == 32) {
      StringBuilder builder = new StringBuilder(36);

      for (int index = 0; index < 32; index += 8) {
        if (index > 0) {
          builder.append('-');
        }

        builder.append(name, index, index + 8);
      }

      try {
        return Optional.of(UUID.fromString(builder.toString()));
      } catch (IllegalArgumentException exception) {
        return Optional.empty();
      }
    }

    return Optional.empty();
  }

  /**
   * The outcome of a lookup, so commands can tell a miss from an ambiguous name.
   *
   * @param found       whether exactly one player matched
   * @param ambiguous   candidate names when more than one matched
   */
  public record Resolution(boolean found, boolean ambiguous, @Nullable Target target, @Nullable String input,
      List<String> candidates) {

    public Resolution {
      candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public static Resolution found(@NotNull Target target) {
      return new Resolution(true, false, target, target.name(), List.of());
    }

    public static Resolution missing(@Nullable String input) {
      return new Resolution(false, false, null, input == null ? "" : input.trim().toLowerCase(Locale.ROOT), List.of());
    }

    public static Resolution ambiguous(@Nullable String input, @NotNull List<String> candidates) {
      return new Resolution(false, true, null, input == null ? "" : input.trim().toLowerCase(Locale.ROOT), candidates);
    }

    /** The resolved target, or null when the lookup did not succeed. */
    public @Nullable Target orNull() {
      return target;
    }
  }
}
