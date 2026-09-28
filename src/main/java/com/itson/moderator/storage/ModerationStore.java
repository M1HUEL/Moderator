package com.itson.moderator.storage;

import com.itson.moderator.model.FreezeRecord;
import com.itson.moderator.model.PlayerRecord;
import com.itson.moderator.model.Punishment;
import com.itson.moderator.model.Report;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * Persistent home of every moderation record.
 *
 * <p>Implementations keep the whole dataset in memory and flush it to disk, so
 * reads during a command are cheap and never block the main thread. Writes mark
 * the store dirty; flushing is the caller's decision.
 */
public interface ModerationStore {

  /** Reads the backing file into memory, replacing anything already loaded. */
  void load();

  /** Writes pending changes. A no-op when nothing changed since the last flush. */
  void save();

  /** True when there are unsaved changes. */
  boolean isDirty();

  /**
   * The record of a player, creating a placeholder when they are unknown.
   *
   * <p>Never returns empty, so callers can read a name without a null check. An
   * unknown player comes back as their own UUID used as the name, which is enough
   * for a lookup that failed anyway.
   */
  @NotNull PlayerRecord player(@NotNull UUID id);

  /** Every known player, in no particular order. */
  @NotNull List<PlayerRecord> players();

  /** Looks a player up by the last name the plugin saw, case-insensitively. */
  Optional<PlayerRecord> playerNamed(@NotNull String name);

  /** Inserts or replaces a player record, keeping the name index in step. */
  void record(@NotNull PlayerRecord record);

  /** Appends a punishment, used when a sanction is first issued. */
  void add(@NotNull Punishment punishment);

  /**
   * Replaces a punishment in place, matching on its id, and appends it when the
   * id is unknown.
   */
  void update(@NotNull Punishment punishment);

  /**
   * Every punishment ever recorded, in insertion order.
   *
   * <p>Revoked and expired entries stay in this list on purpose: the history has
   * to remain auditable. Callers that want only what is running today have to
   * filter with {@link Punishment#isActiveAt}.
   */
  @NotNull List<Punishment> punishments();

  /**
   * Every punishment ever issued against one player, in insertion order.
   *
   * <p>Exists so that {@code /mod history}, the auto-punish counters and the
   * active lookup do not walk the whole history of every player on the server.
   * Looking up what is running against somebody is by far the most common query,
   * and it only ever concerns one target.
   */
  @NotNull List<Punishment> punishments(@NotNull UUID target);

  /**
   * Forgets finished sanctions that closed before the given instant.
   *
   * <p>Only entries that are already closed are eligible. Anything still in force,
   * and every note ever left, stays: pruning a live ban would unban somebody, and
   * a note is the part of a history most likely to be read back months later.
   *
   * @param cutoff closed entries from before this instant are dropped
   * @return how many entries were dropped
   */
  int pruneFinished(@NotNull Instant cutoff);

  /** Appends a report, used when a player files one. */
  void add(@NotNull Report report);

  /** Replaces a report in place, matching on its id, and appends it when unknown. */
  void update(@NotNull Report report);

  /** Every report ever filed, in insertion order. */
  @NotNull List<Report> reports();

  /**
   * The freeze on a player, if there is one.
   *
   * <p>Kept alongside the history because a freeze has to be restored from disk:
   * the plugin has emptied the player's inventory, so the snapshot that gives it
   * back may not be held in memory only.
   */
  Optional<FreezeRecord> freeze(@NotNull UUID id);

  /** Every current freeze. */
  @NotNull List<FreezeRecord> freezes();

  /** Stores a freeze, replacing any previous one for the same player. */
  void freeze(@NotNull FreezeRecord freeze);

  /** Forgets a freeze, called when staff release the player. */
  void unfreeze(@NotNull UUID id);
}
