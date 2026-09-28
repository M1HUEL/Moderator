package com.itson.moderator.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itson.moderator.model.FreezeRecord;
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

  /** A second player, so the index can be checked for not leaking across targets. */
  private static final UUID OTHER = UUID.fromString("44444444-4444-4444-4444-444444444444");

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

  /** A temporary sanction of the shared target, by readable id. */
  private static Punishment temporary(String id, PunishmentType type, Instant expiresAt) {
    return Punishment.temporary(id, TARGET, "Steve", "1.2.3.4", type, "spam", "Spam", STAFF, "Admin", CREATED,
        expiresAt);
  }

  /** A permanent sanction of the shared target. */
  private static Punishment permanent(String id, PunishmentType type, String reason) {
    return Punishment.permanent(id, TARGET, "Steve", "1.2.3.4", type, null, reason, STAFF, "Admin", CREATED);
  }

  /** A permanent sanction of the second player, with their own name. */
  private static Punishment permanentForOther(String id, PunishmentType type, String reason) {
    return Punishment.permanent(id, OTHER, "Alex", "5.6.7.8", type, null, reason, STAFF, "Admin", CREATED);
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

  /**
   * A freeze owns the only copy of a player's inventory, so it has to come back
   * out of the file exactly as it went in.
   */
  @Test
  @DisplayName("a freeze round trips through the file, snapshot included")
  void freezeRoundTrips() {
    store.freeze(freeze("SURVIVAL", 17.5d, "world", 1.5d, 64.0d, -2.5d, 90f, 45f));

    store.save();

    YamlStore reloaded = newStore();

    reloaded.load();

    FreezeRecord found = reloaded.freeze(TARGET).orElseThrow();

    assertEquals("Steve", found.name());
    assertEquals("Griefing", found.reason());
    assertEquals("Admin", found.staffName());
    assertEquals(CREATED, found.createdAt());
    assertEquals("SURVIVAL", found.gameMode());
    assertTrue(found.allowFlight());
    assertTrue(found.flying());
    assertEquals(17.5d, found.health());
    assertEquals(11, found.foodLevel());
    assertEquals(3.5f, found.saturation());
    assertEquals(40, found.fireTicks());
    assertEquals("world", found.world());
    assertEquals(1.5d, found.x());
    assertEquals(64.0d, found.y());
    assertEquals(-2.5d, found.z());
    assertEquals(90f, found.yaw());
    assertEquals(45f, found.pitch());
    assertEquals("Y29tbGVudHM=", found.contents());
    assertEquals("Y29tb3Vy", found.armor());
    assertEquals("Y29mZmhhbmQ=", found.offHand());
    assertFalse(found.released());
  }

  @Test
  @DisplayName("a freeze released while the player was away stays on file until it is handed back")
  void releasedFreezeIsKept() {
    store.freeze(freeze("SURVIVAL", 20.0d, "world", 0.0d, 64.0d, 0.0d, 0f, 0f).markedReleased());

    store.save();

    YamlStore reloaded = newStore();

    reloaded.load();

    assertTrue(reloaded.freeze(TARGET).orElseThrow().released());
    assertEquals(1, reloaded.freezes().size());
  }

  @Test
  @DisplayName("a freeze with a missing snapshot is dropped rather than loaded")
  void incompleteFreezeIsSkipped() throws IOException {
    Files.writeString(file, """
        freezes:
          11111111-1111-1111-1111-111111111111:
            name: Steve
            reason: Griefing
            staff-name: Admin
            created: 1767261600000
            game-mode: SURVIVAL
        """);

    YamlStore fresh = newStore();

    fresh.load();

    assertTrue(fresh.freezes().isEmpty());
  }

  @Test
  @DisplayName("a freeze with no world keeps the player where they are")
  void freezeWithoutWorldRoundTrips() {
    store.freeze(freeze("CREATIVE", 20.0d, null, 0.0d, 0.0d, 0.0d, 0f, 0f));

    store.save();

    YamlStore reloaded = newStore();

    reloaded.load();

    assertNull(reloaded.freeze(TARGET).orElseThrow().world());
  }

  private static FreezeRecord freeze(String gameMode, double health, String world, double x, double y, double z,
      float yaw, float pitch) {
    return new FreezeRecord(TARGET, "Steve", "Griefing", "Admin", CREATED, false, gameMode, true, true, health, 11,
        3.5f, 40, world, x, y, z, yaw, pitch, "Y29tbGVudHM=", "Y29tb3Vy", "Y29mZmhhbmQ=");
  }

  /**
   * The per-target index is a copy of the flat list, so the two drifting apart
   * would show a sanction as active to {@code /mod history} and gone to the mute
   * cache. Each test compares the index against a scan of the real list.
   */
  @Test
  @DisplayName("the per-target index holds exactly the entries of that player")
  void targetIndexMatchesScan() {
    store.add(temporary("p1", PunishmentType.MUTE, Instant.parse("2026-02-01T10:00:00Z")));
    store.add(permanent("p2", PunishmentType.BAN, "Cheating"));
    store.add(permanent("p3", PunishmentType.NOTE, "Cheating"));
    store.add(permanentForOther("p4", PunishmentType.WARN, "Spam"));

    assertEquals(List.of("p1", "p2", "p3"), ids(store.punishments(TARGET)));
    assertEquals(List.of("p4"), ids(store.punishments(OTHER)));
    assertEquals(List.of(), ids(store.punishments(UUID.randomUUID())));
  }

  @Test
  @DisplayName("closing a sanction replaces it in the index instead of adding a second copy")
  void targetIndexFollowsUpdate() {
    store.add(temporary("p1", PunishmentType.MUTE, Instant.parse("2026-02-01T10:00:00Z")));

    Punishment revoked = store.punishments(TARGET).get(0).revoke(CREATED, "Admin", "Appealed");

    store.update(revoked);

    assertEquals(1, store.punishments(TARGET).size());
    assertEquals(1, store.punishments().size());
    assertEquals(revoked, store.punishments(TARGET).get(0));
    assertEquals(ids(store.punishments()), ids(store.punishments(TARGET)));
  }

  @Test
  @DisplayName("an update for an id the store never saw is appended to both")
  void updateAppendsUnknown() {
    store.update(permanent("p9", PunishmentType.KICK, "Steve"));

    assertEquals(List.of("p9"), ids(store.punishments()));
    assertEquals(List.of("p9"), ids(store.punishments(TARGET)));
  }

  @Test
  @DisplayName("the index survives a reload")
  void targetIndexSurvivesReload() {
    store.add(temporary("p1", PunishmentType.MUTE, Instant.parse("2026-02-01T10:00:00Z")));
    store.add(permanentForOther("p4", PunishmentType.NOTE, "Spam"));
    store.save();

    YamlStore reloaded = newStore();

    reloaded.load();

    assertEquals(List.of("p1"), ids(reloaded.punishments(TARGET)));
    assertEquals(List.of("p4"), ids(reloaded.punishments(OTHER)));
  }

  @Test
  @DisplayName("a reload replaces the index rather than adding to the one already there")
  void reloadClearsIndex() {
    store.add(temporary("p1", PunishmentType.MUTE, Instant.parse("2026-02-01T10:00:00Z")));
    store.save();

    YamlStore reloaded = newStore();

    reloaded.load();
    reloaded.load();

    assertEquals(1, reloaded.punishments(TARGET).size());
  }

  private static List<String> ids(List<Punishment> entries) {
    return entries.stream().map(Punishment::id).toList();
  }

  /**
   * Pruning has to be the narrowest thing that fixes the growth of the file:
   * anything still in force, and every note, has to survive it.
   */
  @Test
  @DisplayName("pruning only drops finished sanctions older than the cutoff")
  void prunesOnlyOldFinishedEntries() {
    Instant cutoff = Instant.parse("2026-01-01T00:00:00Z");
    Instant longAgo = Instant.parse("2025-01-01T00:00:00Z");
    Instant afterCutoff = Instant.parse("2026-06-01T00:00:00Z");

    store.add(temporary("finished-old", PunishmentType.MUTE, longAgo).expire());
    store.add(temporary("finished-recent", PunishmentType.MUTE, afterCutoff).expire());
    store.add(temporary("still-running", PunishmentType.MUTE, Instant.parse("2030-01-01T00:00:00Z")));
    store.add(permanent("note", PunishmentType.NOTE, "Careful with this one"));
    store.add(permanent("live-ban", PunishmentType.BAN, "Cheating"));

    assertEquals(1, store.pruneFinished(cutoff));

    assertEquals(List.of("finished-recent", "still-running", "note", "live-ban"), ids(store.punishments()));
    assertEquals(4, store.punishments(TARGET).size());
  }

  @Test
  @DisplayName("pruning a revoked sanction uses the moment it was lifted, not when it started")
  void pruneUsesRevocationTime() {
    Instant old = Instant.parse("2025-01-01T00:00:00Z");
    Instant afterCutoff = Instant.parse("2026-06-01T00:00:00Z");

    store.add(permanent("old-ban", PunishmentType.BAN, "Cheating").revoke(old, "Admin", "Wrong player"));
    store.add(permanent("new-ban", PunishmentType.BAN, "Cheating").revoke(afterCutoff, "Admin", "Wrong player"));

    assertEquals(1, store.pruneFinished(Instant.parse("2026-01-01T00:00:00Z")));

    assertEquals(List.of("new-ban"), ids(store.punishments()));
  }

  @Test
  @DisplayName("a temporary sanction is judged by the moment it ended, not the one it was noticed")
  void pruneUsesExpiryTime() {
    Instant old = Instant.parse("2025-01-01T00:00:00Z");
    Instant afterCutoff = Instant.parse("2026-06-01T00:00:00Z");

    store.add(temporary("old-mute", PunishmentType.MUTE, old).expire());
    store.add(temporary("new-mute", PunishmentType.MUTE, afterCutoff).expire());

    assertEquals(1, store.pruneFinished(Instant.parse("2026-01-01T00:00:00Z")));

    assertEquals(List.of("new-mute"), ids(store.punishments()));
  }

  @Test
  @DisplayName("pruning leaves the store clean when nothing is old enough")
  void pruneWithNothingToDoDoesNotDirty() {
    store.add(permanent("note", PunishmentType.NOTE, "Still here"));
    store.save();

    assertEquals(0, store.pruneFinished(Instant.parse("2020-01-01T00:00:00Z")));
    assertFalse(store.isDirty());
  }

  @Test
  @DisplayName("a save triggered by unchanged data leaves the file exactly as it was")
  void saveWithUnchangedDataLeavesTheFileAlone() throws IOException {
    store.record(new PlayerRecord(TARGET, "Steve", "1.2.3.4", CREATED, CREATED));
    store.add(permanent("p1", PunishmentType.BAN, "Cheating"));
    store.save();

    String before = Files.readString(file);

    // Re-recording a player who has not moved or changed marks the store dirty
    // without changing anything, which is the case a save has nothing to do about.
    store.record(new PlayerRecord(TARGET, "Steve", "1.2.3.4", CREATED, CREATED));
    assertTrue(store.isDirty());

    store.save();

    assertEquals(before, Files.readString(file));
    assertFalse(store.isDirty());
  }

  @Test
  @DisplayName("no temporary file is left behind after a save")
  void saveLeavesNoTemporaryFile() {
    store.add(permanent("p1", PunishmentType.BAN, "Cheating"));
    store.save();

    assertFalse(Files.exists(file.resolveSibling(file.getFileName() + ".tmp")));
  }

  @Test
  @DisplayName("a save replaces the file instead of truncating it")
  void saveLeavesAReadableFile() throws IOException {
    store.add(permanent("p1", PunishmentType.BAN, "Cheating"));
    store.save();
    store.add(permanent("p2", PunishmentType.NOTE, "Note"));
    store.save();

    YamlStore reloaded = newStore();

    reloaded.load();

    assertEquals(List.of("p1", "p2"), ids(reloaded.punishments()));
  }
}
