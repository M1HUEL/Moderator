package com.itson.moderator.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itson.moderator.model.PlayerRecord;
import com.itson.moderator.model.Punishment;
import com.itson.moderator.model.PunishmentType;
import com.itson.moderator.model.Report;
import com.itson.moderator.model.ReportStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Sanctions and reports have to survive a restart, so the store is written to a
 * real file and read back through a second instance.
 */
class YamlStoreTest {

  private static final UUID TARGET = UUID.fromString("11111111-1111-1111-1111-111111111111");

  private static final UUID STAFF = UUID.fromString("22222222-2222-2222-2222-222222222222");

  private static final UUID REPORTER = UUID.fromString("33333333-3333-3333-3333-333333333333");

  private static final Instant CREATED = Instant.parse("2026-01-01T10:00:00Z");

  private Path file;

  private YamlStore store;

  @BeforeEach
  void setUp(@TempDir Path directory) throws IOException {
    file = directory.resolve("data.yml");
    Files.writeString(file, "seed: true\n");
    store = newStore();
    store.load();
  }

  private YamlStore newStore() {
    return new YamlStore(file.toFile(), Logger.getLogger(YamlStoreTest.class.getName()));
  }

  private static Punishment temporary(UUID id, PunishmentType type, Instant expiresAt) {
    return Punishment.temporary(id.toString(), TARGET, "Steve", "1.2.3.4", type, "spam", "Spam", STAFF, "Admin",
        CREATED, expiresAt);
  }

  @Test
  @DisplayName("a temporary sanction survives a restart with every field intact")
  void temporaryRoundTrip() {
    store.add(temporary(TARGET, PunishmentType.MUTE, CREATED.plus(Duration.ofHours(1))));
    store.save();

    YamlStore reloaded = newStore();

    reloaded.load();

    assertEquals(1, reloaded.punishments().size());

    Punishment stored = reloaded.punishments().get(0);

    assertEquals(TARGET.toString(), stored.id());
    assertEquals(TARGET, stored.target());
    assertEquals("Steve", stored.targetName());
    assertEquals("1.2.3.4", stored.targetIp());
    assertEquals(PunishmentType.MUTE, stored.type());
    assertEquals("spam", stored.reasonId());
    assertEquals("Spam", stored.reason());
    assertEquals(STAFF, stored.staff());
    assertEquals("Admin", stored.staffName());
    assertEquals(CREATED, stored.createdAt());
    assertEquals(CREATED.plus(Duration.ofHours(1)), stored.expiresAt());
    assertTrue(stored.active());
    assertTrue(stored.isActiveAt(CREATED));
  }

  @Test
  @DisplayName("a permanent sanction keeps a null expiry rather than a zero millis")
  void permanentRoundTrip() {
    store.add(Punishment.permanent(TARGET.toString(), TARGET, "Steve", null, PunishmentType.BAN, null, "Cheating", STAFF,
        "Admin", CREATED));
    store.save();

    YamlStore reloaded = newStore();

    reloaded.load();

    Punishment stored = reloaded.punishments().get(0);

    assertNull(stored.expiresAt());
    assertTrue(stored.isPermanent());
    assertNull(stored.targetIp());
    assertNull(stored.reasonId());
  }

  @Test
  @DisplayName("a revoked sanction keeps who lifted it and why")
  void revokeRoundTrip() {
    Punishment revoked = temporary(TARGET, PunishmentType.MUTE, CREATED.plus(Duration.ofHours(1)))
        .revoke(CREATED.plusSeconds(60), "Admin", "Appeal accepted");

    store.add(revoked);
    store.save();

    YamlStore reloaded = newStore();

    reloaded.load();

    Punishment stored = reloaded.punishments().get(0);

    assertFalse(stored.active());
    assertEquals(CREATED.plusSeconds(60), stored.revokedAt());
    assertEquals("Admin", stored.revokedBy());
    assertEquals("Appeal accepted", stored.revokeReason());
    assertEquals("revoked", stored.stateAt(CREATED.plusSeconds(60)));
  }

  @Test
  @DisplayName("a report survives a restart with its status and resolution")
  void reportRoundTrip() {
    Report report = new Report("rep-1", TARGET, "Steve", REPORTER, "Alex", "griefing", "Broke spawn", CREATED,
        ReportStatus.RESOLVED, STAFF.toString(), "Warned");

    store.add(report);
    store.save();

    YamlStore reloaded = newStore();

    reloaded.load();

    assertEquals(1, reloaded.reports().size());

    Report stored = reloaded.reports().get(0);

    assertEquals("rep-1", stored.id());
    assertEquals(TARGET, stored.target());
    assertEquals(REPORTER, stored.reporter());
    assertEquals("griefing", stored.reasonId());
    assertEquals("Broke spawn", stored.details());
    assertEquals(ReportStatus.RESOLVED, stored.status());
    assertEquals(STAFF.toString(), stored.handledBy());
    assertEquals("Warned", stored.resolution());
  }

  @Test
  @DisplayName("players are indexed by both uuid and name")
  void playerRoundTrip() {
    PlayerRecord record = new PlayerRecord(TARGET, "Steve", "1.2.3.4", CREATED, CREATED.plusSeconds(90));

    store.record(record);
    store.save();

    YamlStore reloaded = newStore();

    reloaded.load();

    assertEquals("Steve", reloaded.player(TARGET).name());
    assertEquals("1.2.3.4", reloaded.player(TARGET).lastIp());
    assertEquals(CREATED, reloaded.player(TARGET).firstSeen());
    assertEquals(Optional.of(record), reloaded.playerNamed("STEVE"));
    assertEquals(Optional.empty(), reloaded.playerNamed("Alex"));
  }

  @Test
  @DisplayName("a renamed player does not keep answering to the old name")
  void renameDropsOldLookup() {
    store.record(new PlayerRecord(TARGET, "Steve", null, CREATED, CREATED));
    store.record(new PlayerRecord(TARGET, "SteveRenamed", null, CREATED, CREATED));

    assertEquals(Optional.empty(), store.playerNamed("Steve"));
    assertEquals("SteveRenamed", store.playerNamed("steveRenamed").orElseThrow().name());
  }

  @Test
  @DisplayName("saving twice does not duplicate entries")
  void repeatedSaveKeepsOneCopy() {
    store.add(temporary(TARGET, PunishmentType.WARN, CREATED.plus(Duration.ofDays(1))));
    store.save();
    store.save();

    assertEquals(1, store.punishments().size());

    YamlStore reloaded = newStore();

    reloaded.load();

    assertEquals(1, reloaded.punishments().size());
  }

  @Test
  @DisplayName("updating in place replaces the entry instead of adding one")
  void updateReplacesInPlace() {
    Punishment issued = temporary(TARGET, PunishmentType.MUTE, CREATED.plusSeconds(3600));

    store.add(issued);

    Punishment revoked = issued.revoke(CREATED.plusSeconds(60), "Admin", "Mistake");

    store.update(revoked);

    assertEquals(1, store.punishments().size());
    assertFalse(store.punishments().get(0).active());
  }

  @Test
  @DisplayName("a clean store does not rewrite the file")
  void doesNotSaveWhenClean() throws IOException {
    assertFalse(store.isDirty());

    store.save();

    assertTrue(Files.readString(file).contains("seed: true"));
  }

  @Test
  @DisplayName("a missing file loads as an empty store instead of failing")
  void missingFileIsEmpty() throws IOException {
    Files.delete(file);

    YamlStore fresh = newStore();

    fresh.load();

    assertTrue(fresh.punishments().isEmpty());
    assertTrue(fresh.reports().isEmpty());
    assertTrue(fresh.players().isEmpty());
  }

  @Test
  @DisplayName("a corrupt entry is skipped and the rest of the file still loads")
  void corruptEntriesAreSkipped() throws IOException {
    Files.writeString(file, """
        players:
          11111111-1111-1111-1111-111111111111:
            name: Steve
            first-seen: 1767261600000
            last-seen: 1767261600000
          not-a-uuid:
            name: Broken
            first-seen: 1767261600000
            last-seen: 1767261600000
        punishments:
          - id: broken-1
            target: nope
            type: nonsense
        reports: []
        """);

    YamlStore fresh = newStore();

    fresh.load();

    assertEquals(List.of("Steve"), fresh.players().stream().map(PlayerRecord::name).toList());
    assertTrue(fresh.punishments().isEmpty());
  }
}
