package com.itson.moderator.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itson.moderator.model.PunishmentType;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the escalation table behind {@code auto-punish}: which rule wins when
 * several match, and what a rule is allowed to count.
 */
class AutoPunishRuleTest {

  private static AutoPunishRule rule(int threshold, PunishmentType action, String... countTypes) {
    Set<PunishmentType> types = Arrays.stream(countTypes)
        .map(PunishmentType::byId)
        .flatMap(Optional::stream)
        .collect(Collectors.toUnmodifiableSet());

    return new AutoPunishRule(threshold, types, action, Duration.ofHours(1), "Too many strikes", null, false);
  }

  @Test
  @DisplayName("picks the rule with the highest threshold that the count reaches")
  void picksHighestMatch() {
    List<AutoPunishRule> rules = List.of(rule(5, PunishmentType.BAN, "warn", "mute"),
        rule(3, PunishmentType.MUTE, "warn"), rule(8, PunishmentType.BAN, "warn"));

    assertEquals(3, AutoPunishRule.select(rules, 3).orElseThrow().threshold());
    assertEquals(3, AutoPunishRule.select(rules, 4).orElseThrow().threshold());
    assertEquals(5, AutoPunishRule.select(rules, 5).orElseThrow().threshold());
    assertEquals(5, AutoPunishRule.select(rules, 6).orElseThrow().threshold());
    assertEquals(5, AutoPunishRule.select(rules, 7).orElseThrow().threshold());
    assertEquals(8, AutoPunishRule.select(rules, 8).orElseThrow().threshold());
    assertEquals(8, AutoPunishRule.select(rules, 40).orElseThrow().threshold());
  }

  @Test
  @DisplayName("triggers nothing below the lowest threshold")
  void triggersNothingBelowThreshold() {
    assertTrue(AutoPunishRule.select(List.of(rule(3, PunishmentType.MUTE, "warn")), 2).isEmpty());
    assertTrue(AutoPunishRule.select(List.of(rule(3, PunishmentType.MUTE, "warn")), 0).isEmpty());
  }

  @Test
  @DisplayName("an empty rule set never triggers")
  void emptyRulesNeverTrigger() {
    assertTrue(AutoPunishRule.select(List.of(), 99).isEmpty());
  }

  @Test
  @DisplayName("counts only the listed types")
  void countsOnlyListedTypes() {
    AutoPunishRule counted = rule(3, PunishmentType.MUTE, "warn", "mute");

    assertTrue(counted.counts(PunishmentType.WARN));
    assertTrue(counted.counts(PunishmentType.MUTE));
    assertFalse(counted.counts(PunishmentType.BAN));
    assertFalse(counted.counts(PunishmentType.NOTE));
    assertFalse(counted.counts(PunishmentType.KICK));
  }

  @Test
  @DisplayName("a permanent rule reads as permanent in messages")
  void lifetimeWording() {
    AutoPunishRule temporary = rule(3, PunishmentType.MUTE, "warn");
    AutoPunishRule permanent = new AutoPunishRule(5, Set.of(PunishmentType.WARN), PunishmentType.BAN, null,
        "Banned", "moderator.admin", true);

    assertEquals("1h", temporary.lifetime());
    assertEquals("permanent", permanent.lifetime());
  }
}
