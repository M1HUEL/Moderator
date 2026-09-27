package com.itson.moderator.service;

import com.itson.moderator.model.PlayerRecord;
import com.itson.moderator.storage.ModerationStore;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves names to {@link com.itson.moderator.model.Target} records and keeps the
 * last known name and address of every player seen.
 *
 * <p>Lookup order is online players, then names remembered by the plugin, then
 * names the server has already seen. An unknown name is never turned into a web
 * request, because that would block the main thread.
 */
public final class PlayerRegistry {

  private final ModerationStore store;

  public PlayerRegistry(@NotNull ModerationStore store) {
    this.store = store;
  }

  /**
   * Records a join, creating the entry the first time a player is seen.
   *
   * <p>A null address keeps whatever was known before, so a command that only
   * knows the name of an offline player cannot wipe the IP that {@code /mod banip}
   * depends on.
   */
  public PlayerRecord seen(@NotNull UUID id, @NotNull String name, @Nullable String ip, @NotNull Instant now) {
    PlayerRecord current = store.player(id);

    if (current.name().equals(id.toString())) {
      PlayerRecord fresh = PlayerRecord.of(id, name, ip, now);

      store.record(fresh);

      return fresh;
    }

    PlayerRecord updated = current.seen(name, ip != null ? ip : current.lastIp(), now);

    store.record(updated);

    return updated;
  }

  public Optional<PlayerRecord> byName(@NotNull String name) {
    return store.playerNamed(name);
  }

  public @NotNull PlayerRecord record(@NotNull UUID id) {
    return store.player(id);
  }

  public @NotNull List<PlayerRecord> all() {
    return store.players();
  }

  /** The address last seen for a player, used by {@code /mod banip}. */
  public @Nullable String lastIp(@NotNull UUID id) {
    return store.player(id).lastIp();
  }
}
