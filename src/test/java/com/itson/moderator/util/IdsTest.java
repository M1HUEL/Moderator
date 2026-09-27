package com.itson.moderator.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IdsTest {

  private static final String UUID = "3f9a1c22-8b4d-4a1e-9c3b-77d2e5f0a1b4";

  @Test
  @DisplayName("parses a dashed uuid")
  void parsesDashed() {
    assertEquals(Optional.of(java.util.UUID.fromString(UUID)), Ids.parseUuid(UUID));
  }

  @ParameterizedTest
  @ValueSource(strings = {"3f9a1c22", "not-a-uuid", "3f9a1c22-8b4d-4a1e-9c3b-77d2e5f0a1b4x", "3f9a1c22-8b4d-4a1e-9c3b"})
  @DisplayName("refuses anything that is not a uuid")
  void refusesGarbage(String input) {
    assertEquals(Optional.empty(), Ids.parseUuid(input));
    assertEquals(Optional.empty(), Ids.parseUndashed(input));
  }

  @Test
  @DisplayName("parses the undashed form Mojang hands out")
  void parsesUndashed() {
    assertEquals(Optional.of(java.util.UUID.fromString(UUID)), Ids.parseUndashed("3f9a1c228b4d4a1e9c3b77d2e5f0a1b4"));
  }

  @Test
  @DisplayName("shortens an id to what staff actually type")
  void shortens() {
    assertEquals("3f9a1c22", Ids.shortId(UUID));
    assertEquals("3f9a1c22", Ids.shortId("3f9a1c22-8b4d-4a1e-9c3b-77d2e5f0a1b4"));
    assertEquals("short", Ids.shortId("short"));
    assertEquals("unknown", Ids.shortId(null));
    assertEquals("unknown", Ids.shortId("  "));
  }
}
