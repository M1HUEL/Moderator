package com.itson.moderator.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itson.moderator.model.PunishmentType;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The shipped {@code config.yml} has to be readable, otherwise a fresh server
 * silently loses features. A YAML list of mappings arrives as a {@code Map}, not
 * as a {@code ConfigurationSection}, which is easy to get wrong and easy to miss.
 */
class ModerationConfigTest {

  private YamlConfiguration config;

  private List<String> problems;

  @BeforeEach
  void setUp() throws Exception {
    try (InputStream stream = getClass().getResourceAsStream("/config.yml")) {
      assertNotNull(stream, "config.yml must be on the test classpath");
      config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    problems = new ArrayList<>();
  }

  private List<AutoPunishRule> readAutoPunish() {
    return AutoPunishReader.read(config.getList("auto-punish", List.of()), problems);
  }

  @Test
  @DisplayName("the shipped auto-punish rules survive loading")
  void autoPunishLoads() {
    assertEquals(2, readAutoPunish().size(), () -> "expected both shipped rules, problems: " + problems);
  }

  @Test
  @DisplayName("the rules come back sorted by descending threshold")
  void autoPunishIsSorted() {
    List<AutoPunishRule> rules = readAutoPunish();

    assertTrue(rules.get(0).threshold() > rules.get(1).threshold());
  }

  @Test
  @DisplayName("count types and actions are resolved, not left as raw strings")
  void autoPunishResolvesTypes() {
    AutoPunishRule rule = readAutoPunish().stream().filter(entry -> entry.threshold() == 5).findFirst().orElseThrow();

    assertEquals(PunishmentType.BAN, rule.action());
    assertTrue(rule.counts(PunishmentType.WARN));
    assertTrue(rule.counts(PunishmentType.MUTE));
    assertFalse(rule.counts(PunishmentType.NOTE));
    assertEquals("moderator.admin", rule.permission());
  }

  @Test
  @DisplayName("a rule without a permission is open to every moderator")
  void openRuleHasNoPermission() {
    AutoPunishRule rule = readAutoPunish().stream().filter(entry -> entry.threshold() == 3).findFirst()
        .orElseThrow();

    assertTrue(rule.permission() == null || rule.permission().isBlank(),
        () -> "expected no permission, got " + rule.permission());
  }

  @Test
  @DisplayName("the shipped durations are readable")
  void durationsAreValid() {
    for (AutoPunishRule rule : readAutoPunish()) {
      assertNotNull(rule.reason());
      assertFalse(rule.reason().isBlank());
      assertFalse(rule.lifetime().isBlank());
    }

    assertEquals("30m", readAutoPunish().stream().filter(entry -> entry.threshold() == 3).findFirst().orElseThrow()
        .lifetime());
  }

  @Test
  @DisplayName("a permanent rule stays permanent after loading")
  void permanentRuleHasNoDuration() {
    YamlConfiguration only = new YamlConfiguration();

    only.set("auto-punish", List.of(Map.of("threshold", 2, "count-types", List.of("warn"), "action", "ban",
        "duration", "perm", "reason", "Too many")));

    List<AutoPunishRule> rules = AutoPunishReader.read(only.getList("auto-punish", List.of()), new ArrayList<>());

    assertEquals(1, rules.size());
    assertEquals("permanent", rules.get(0).lifetime());
  }

  @Test
  @DisplayName("an entry with a threshold of zero is skipped, not applied at once")
  void invalidRuleIsSkipped() {
    YamlConfiguration bad = new YamlConfiguration();

    bad.set("auto-punish", List.of(
        Map.of("threshold", 0, "count-types", List.of("warn"), "action", "ban", "reason", "Never"),
        Map.of("threshold", 2, "count-types", List.of("warn"), "action", "teleport", "reason", "Bad action"),
        Map.of("threshold", 2, "count-types", List.of("nonsense"), "action", "ban", "reason", "Bad count"),
        Map.of("threshold", 2, "count-types", List.of("warn"), "action", "ban", "duration", "5x", "reason", "Bad time"),
        Map.of("threshold", 2, "count-types", List.of("warn"), "action", "ban", "reason", " "),
        "not a mapping"));

    List<String> issues = new ArrayList<>();
    List<AutoPunishRule> rules = AutoPunishReader.read(bad.getList("auto-punish", List.of()), issues);

    assertTrue(rules.isEmpty(), () -> "expected no usable rules, got " + rules);
    assertEquals(6, issues.size(), () -> "expected every entry to be reported, got " + issues);
  }

  @Test
  @DisplayName("an empty or missing list yields no rules and no complaints")
  void emptyListIsFine() {
    assertTrue(AutoPunishReader.read(List.of(), problems).isEmpty());
    assertTrue(problems.isEmpty());
  }

  @Test
  @DisplayName("a threshold reached several times over picks the harshest rule")
  void highestThresholdWins() {
    YamlConfiguration stacked = new YamlConfiguration();

    stacked.set("auto-punish", List.of(
        Map.of("threshold", 3, "count-types", List.of("warn"), "action", "mute", "duration", "10m", "reason", "Three"),
        Map.of("threshold", 6, "count-types", List.of("warn", "mute"), "action", "ban", "duration", "1d",
            "reason", "Six")));

    List<AutoPunishRule> rules = AutoPunishReader.read(stacked.getList("auto-punish", List.of()), new ArrayList<>());

    assertEquals(PunishmentType.MUTE, AutoPunishRule.select(rules, 3).orElseThrow().action());
    assertEquals(PunishmentType.BAN, AutoPunishRule.select(rules, 6).orElseThrow().action());
    assertEquals(PunishmentType.BAN, AutoPunishRule.select(rules, 9).orElseThrow().action());
  }

  @Test
  @DisplayName("the shipped reasons carry a label each")
  void reasonsHaveLabels() {
    org.bukkit.configuration.ConfigurationSection reasons = config.getConfigurationSection("reasons");

    assertNotNull(reasons, "the reasons section is missing");

    assertFalse(reasons.getKeys(false).isEmpty());

    for (String id : reasons.getKeys(false)) {
      String label = reasons.getString(id + ".label");

      assertNotNull(label, () -> "reason without a label: " + id);
      assertFalse(label.isBlank(), () -> "reason with a blank label: " + id);
    }
  }

  @Test
  @DisplayName("a reason that limits its types names real ones")
  void reasonAppliesAreValid() {
    org.bukkit.configuration.ConfigurationSection reasons = config.getConfigurationSection("reasons");

    for (String id : reasons.getKeys(false)) {
      for (String type : reasons.getStringList(id + ".applies")) {
        assertNotNull(PunishmentType.byId(type).orElse(null),
            () -> "reason " + id + " applies to unknown type " + type);
      }
    }
  }

  @Test
  @DisplayName("the shipped messages exist for the keys the plugin sends")
  void messagesExist() {
    org.bukkit.configuration.ConfigurationSection messages = config.getConfigurationSection("messages");

    assertNotNull(messages, "the messages section is missing");

    List<String> keys = List.of("prefix", "usage", "unknown-subcommand", "no-permission", "players-only",
        "name-required", "cooldown", "target-not-found", "target-ambiguous", "target-offline", "invalid-duration",
        "invalid-type", "invalid-status", "invalid-gamemode", "invalid-section", "punish-staff", "punish-broadcast",
        "punish-replaced", "punish-expired", "auto-punish", "already-active", "revoke-staff", "revoke-broadcast",
        "nothing-active", "vanilla-reason", "login-punishment", "muted-chat", "muted-command", "history-header",
        "history-entry", "history-empty", "profile-header", "profile-entry", "profile-note", "report-created",
        "report-self", "report-broadcast", "report-handled", "reports-header", "reports-empty", "report-entry",
        "report-detail", "report-not-found", "report-resolved", "report-already-handled", "freeze-self",
        "frozen-staff", "frozen-broadcast", "frozen-screen", "frozen-command", "already-frozen", "unfrozen-staff",
        "unfrozen-screen", "not-frozen", "invsee-opened", "invsee-by", "invsee-saved", "reloaded",
        "reloaded-with-problems", "help-header", "help-entry");

    for (String key : keys) {
      assertNotNull(messages.getString(key), () -> "missing message: " + key);
    }
  }

  @Test
  @DisplayName("the sanction screens name the reason and the expiry")
  void banScreensExist() {
    for (String screen : List.of("mute", "ban", "ipban", "kick", "warn")) {
      String text = config.getString("messages.punish." + screen + "-screen");

      assertNotNull(text, () -> "missing screen: " + screen);
      assertTrue(text.contains("<reason>"), () -> screen + " screen does not show the reason");
    }

    for (String screen : List.of("mute", "ban", "ipban")) {
      String text = config.getString("messages.punish." + screen + "-screen");

      assertTrue(text.contains("<duration>"), () -> screen + " screen does not show the expiry");
    }
  }

  @Test
  @DisplayName("a note has no screen, because a note says nothing to the player")
  void noteHasNoScreen() {
    assertEquals("", config.getString("messages.punish.note-screen"));
  }

  @Test
  @DisplayName("the vanilla reason is plain text, since the ban screen renders it literally")
  void vanillaReasonHasNoMarkup() {
    String text = config.getString("messages.vanilla-reason");

    assertNotNull(text);
    assertFalse(text.contains("<red>"), "the vanilla ban screen does not understand MiniMessage");
    assertFalse(text.contains("</"), "the vanilla ban screen does not understand MiniMessage");
  }

  @Test
  @DisplayName("every permission the plugin checks is declared in plugin.yml")
  void permissionsAreDeclared() throws Exception {
    try (InputStream stream = getClass().getResourceAsStream("/plugin.yml")) {
      assertNotNull(stream, "plugin.yml must be on the test classpath");
      YamlConfiguration descriptor = YamlConfiguration.loadConfiguration(
          new InputStreamReader(stream, StandardCharsets.UTF_8));

      List<String> checked = List.of("moderator.use", "moderator.kick", "moderator.mute", "moderator.ban",
          "moderator.banip", "moderator.warn", "moderator.note", "moderator.unmute", "moderator.unban",
          "moderator.history", "moderator.reports", "moderator.report", "moderator.freeze", "moderator.unfreeze",
          "moderator.tp", "moderator.heal", "moderator.feed", "moderator.clear", "moderator.gamemode",
          "moderator.kill", "moderator.invsee", "moderator.admin", "moderator.notify");

      org.bukkit.configuration.ConfigurationSection permissions =
          descriptor.getConfigurationSection("permissions");

      assertNotNull(permissions, "plugin.yml declares no permissions");

      // Dots are path separators, so moderator.kick is a nested section and the
      // full path has to be read with getKeys(true).
      java.util.Set<String> declared = permissions.getKeys(true);

      for (String permission : checked) {
        assertTrue(declared.contains(permission), () -> "permission not declared: " + permission);
      }
    }
  }

  @Test
  @DisplayName("the shipped config reports no problems when parsed")
  void noProblems() {
    readAutoPunish();

    assertTrue(problems.isEmpty(), () -> "config problems: " + problems);
  }

  @Test
  @DisplayName("cooldowns and history limits are read with sane bounds")
  void settingsAreUsable() {
    assertTrue(config.getInt("settings.history-limit", 20) >= 1);
    assertNotNull(config.getConfigurationSection("settings.cooldowns"));
    assertNotNull(Duration.ofSeconds(config.getLong("settings.cooldowns.warn", 0)));
  }
}
