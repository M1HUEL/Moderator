package com.itson.moderator.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itson.moderator.model.Report;
import com.itson.moderator.storage.YamlStore;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Report ids are shown shortened, so {@code /mod resolve} has to accept the part a
 * moderator actually reads off the queue. The lookup is the one piece of
 * {@link ModerationService} that needs no server, so it is tested against a real
 * store.
 */
class ModerationServiceReportTest {

  private static final UUID TARGET = UUID.fromString("11111111-1111-1111-1111-111111111111");

  private static final UUID REPORTER = UUID.fromString("22222222-2222-2222-2222-222222222222");

  private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");

  private YamlStore store;

  private ModerationService service;

  @BeforeEach
  void setUp(@TempDir Path directory) {
    store = new YamlStore(directory.resolve("data.yml").toFile(),
        Logger.getLogger(ModerationServiceReportTest.class.getName()));

    store.load();

    // The lookup touches neither the plugin nor the config, so both stay null.
    service = new ModerationService(null, null, store, new MuteRegistry(), new PlayerRegistry(store), Clock.systemUTC());
  }

  /** Files a report under a chosen id, since the service generates random ones. */
  private void file(String id) {
    store.add(Report.open(id, TARGET, "Steve", REPORTER, "Alex", "griefing", "broke spawn", CREATED));
  }

  @Test
  @DisplayName("the full id resolves")
  void exactId() {
    file("3f9a1c22");

    assertTrue(service.report("3f9a1c22").isPresent());
  }

  @Test
  @DisplayName("a prefix of the id resolves, because that is what chat prints")
  void prefixResolves() {
    file("3f9a1c22");

    assertTrue(service.report("3f9a").isPresent());
    assertTrue(service.report("3f9a1c").isPresent());
  }

  @Test
  @DisplayName("the lookup ignores case, since ids get read aloud")
  void caseInsensitive() {
    file("3f9a1c22");

    assertTrue(service.report("3F9A").isPresent());
  }

  @Test
  @DisplayName("surrounding whitespace is tolerated")
  void trimsInput() {
    file("3f9a1c22");

    assertTrue(service.report("  3f9a  ").isPresent());
  }

  @Test
  @DisplayName("an unknown prefix resolves to nothing")
  void unknownPrefix() {
    file("3f9a1c22");

    assertTrue(service.report("zzzz").isEmpty());
    assertTrue(service.report("3f9a1c22extra").isEmpty());
  }

  @Test
  @DisplayName("an empty id never matches, so a blank argument is not a lucky hit")
  void emptyId() {
    file("3f9a1c22");

    assertTrue(service.report("").isEmpty());
    assertTrue(service.report("   ").isEmpty());
  }

  @Test
  @DisplayName("a shared prefix is not a choice, so nothing resolves")
  void ambiguousPrefix() {
    file("3f9a1c22");
    file("3f9a1c33");

    assertTrue(service.report("3f9a").isEmpty());
  }

  @Test
  @DisplayName("an exact id wins even when it is a prefix of another")
  void exactWinsOverPrefix() {
    file("3f9a");
    file("3f9a1c22");

    assertEquals("3f9a", service.report("3f9a").orElseThrow().id());
    assertEquals("3f9a1c22", service.report("3f9a1c22").orElseThrow().id());
  }
}
