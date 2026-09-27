package com.itson.moderator.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Covers address recognition and parsing, including the IPv6 shapes staff actually paste. */
class AddressesTest {

  @ParameterizedTest
  @ValueSource(strings = {"1.2.3.4", "0.0.0.0", "255.255.255.255", "192.168.1.100", "2001:db8::1", "::1"})
  @DisplayName("accepts address literals")
  void acceptsLiterals(String input) {
    assertTrue(Addresses.isLiteral(input));
    assertTrue(Addresses.parse(input).isPresent());
  }

  @ParameterizedTest
  @ValueSource(strings = {"256.1.1.1", "1.2.3", "1.2.3.4.5", "999.999.999.999", "notanip", "", " ", "player",
      "1.2.3.4:25565"})
  @DisplayName("refuses anything that is not a literal, so banip never hits DNS")
  void refusesNonLiterals(String input) {
    assertFalse(Addresses.isLiteral(input));
    assertEquals(Optional.empty(), Addresses.parse(input));
  }

  @Test
  @DisplayName("drops the IPv6 scope suffix the JVM adds")
  void stripsScope() {
    assertTrue(Addresses.isLiteral("2001:db8::1%eth0"));
    assertTrue(Addresses.parse("2001:db8::1%eth0").isPresent());
    assertEquals("2001:db8::1", Addresses.stripScope("2001:db8::1%eth0"));
    assertEquals("1.2.3.4", Addresses.stripScope("1.2.3.4"));
  }

  @Test
  @DisplayName("null is not a literal")
  void nullIsNotALiteral() {
    assertFalse(Addresses.isLiteral(null));
    assertEquals(Optional.empty(), Addresses.parse(null));
  }
}
