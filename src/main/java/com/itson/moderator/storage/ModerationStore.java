package com.itson.moderator.storage;

import com.itson.moderator.model.PlayerRecord;
import com.itson.moderator.model.Punishment;
import com.itson.moderator.model.Report;
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

  @NotNull PlayerRecord player(@NotNull UUID id);

  @NotNull List<PlayerRecord> players();

  /** Looks a player up by the last name the plugin saw, case-insensitively. */
  Optional<PlayerRecord> playerNamed(@NotNull String name);

  void record(@NotNull PlayerRecord record);

  void add(@NotNull Punishment punishment);

  void update(@NotNull Punishment punishment);

  @NotNull List<Punishment> punishments();

  void add(@NotNull Report report);

  void update(@NotNull Report report);

  @NotNull List<Report> reports();
}
