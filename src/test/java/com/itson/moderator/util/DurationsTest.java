package com.itson.moderator.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DurationsTest {

  @ParameterizedTest
  @CsvSource({
      "45s, PT45S",
      "30m, PT30M",
      "2h, PT2H",
      "7d, P7D",
      "1w, P7D",
      "2h30m, PT2H30M",
      "1w2d, P9D",
      "1Y, P365D",
      "2 h, PT2H"})
  @DisplayName("parses the units staff actually type")
  void parsesKnownUnits(String input, String expected) {
    assertEquals(Optional.of(Duration.parse(expected)), Durations.parse(input));
  }

  @Test
  @DisplayName("reads a bare number as minutes")
  void bareNumberIsMinutes() {
    assertEquals(Optional.of(Duration.ofMinutes(90)), Durations.parse("90"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "x", "5x", "10h junk", "abc", "-5m", "0", "0m", "m5", "5m10x"})
  @DisplayName("rejects anything it cannot fully consume")
  void rejectsGarbage(String input) {
    assertTrue(Durations.parse(input).isEmpty(), () -> "expected no duration for '" + input + "'");
  }

  @ParameterizedTest
  @ValueSource(strings = {"perm", "PERM", "permanent", "Permanent", "-1"})
  @DisplayName("recognises every spelling of no expiry")
  void recognisesPermanent(String input) {
    assertTrue(Durations.isPermanent(input));
    assertTrue(Durations.parse(input).isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"30m", "2h30m", "1d", "permanent"})
  @DisplayName("does not mistake a real duration for a permanent one")
  void durationIsNotPermanent(String input) {
    if ("permanent".equals(input)) {
      assertTrue(Durations.isPermanent(input));

      return;
    }

    assertFalse(Durations.isPermanent(input));
  }

  @Test
  @DisplayName("null input is not permanent and does not parse")
  void nullIsSafe() {
    assertFalse(Durations.isPermanent(null));
    assertTrue(Durations.parse(null).isEmpty());
  }

  @Test
  @DisplayName("formats back to at most two units")
  void formatsCompactly() {
    assertEquals("2d 4h", Durations.format(Duration.ofDays(2).plusHours(4)));
    assertEquals("45s", Durations.format(Duration.ofSeconds(45)));
    assertEquals("1h", Durations.format(Duration.ofHours(1)));
    assertEquals("0s", Durations.format(Duration.ZERO));
    assertEquals("0s", Durations.format(Duration.ofSeconds(-5)));
  }

  @Test
  @DisplayName("formatting never loses a remainder it cannot name")
  void keepsSmallRemainder() {
    assertEquals("1h 30s", Durations.format(Duration.ofHours(1).plusSeconds(30)));
  }

  @Test
  @DisplayName("describes the time left, in both directions")
  void describesUntil() {
    assertEquals("permanent", Durations.describeUntil(null, java.time.Instant.EPOCH));
    assertEquals("in 30m", Durations.describeUntil(java.time.Instant.EPOCH.plusSeconds(1800), java.time.Instant.EPOCH));
    assertEquals("expired",
        Durations.describeUntil(java.time.Instant.EPOCH.minusSeconds(1), java.time.Instant.EPOCH));
  }
}
