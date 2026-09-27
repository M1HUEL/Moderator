package com.itson.moderator.command;

import com.itson.moderator.config.ModerationConfig;
import com.itson.moderator.config.Messages;
import com.itson.moderator.service.CooldownService;
import com.itson.moderator.service.FreezeManager;
import com.itson.moderator.service.InvseeService;
import com.itson.moderator.service.ModerationService;
import com.itson.moderator.service.MuteRegistry;
import com.itson.moderator.service.PlayerRegistry;
import com.itson.moderator.service.TargetResolver;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Everything a subcommand needs, handed over by the dispatcher.
 *
 * <p>Exists so adding a tool is a single class with no constructor plumbing, and
 * so the live configuration is always read through one place.
 */
public final class ModContext {

  private final JavaPlugin plugin;

  private final ModerationConfig config;

  private final ModerationService moderation;

  private final TargetResolver targets;

  private final PlayerRegistry players;

  private final MuteRegistry mutes;

  private final FreezeManager freeze;

  private final CooldownService cooldowns;

  private final InvseeService invsee;

  /**
   * The dispatcher, needed by {@code /mod help} to list what the sender may use.
   *
   * <p>Set after construction: the dispatcher needs this context, and the help
   * output needs the dispatcher, so one of the two has to be filled in later.
   */
  private volatile ModCommand dispatcher;

  public ModContext(@NotNull JavaPlugin plugin, @NotNull ModerationConfig config, @NotNull ModerationService moderation,
      @NotNull TargetResolver targets, @NotNull PlayerRegistry players, @NotNull MuteRegistry mutes,
      @NotNull FreezeManager freeze, @NotNull CooldownService cooldowns, @NotNull InvseeService invsee) {
    this.plugin = plugin;
    this.config = config;
    this.moderation = moderation;
    this.targets = targets;
    this.players = players;
    this.mutes = mutes;
    this.freeze = freeze;
    this.cooldowns = cooldowns;
    this.invsee = invsee;
  }

  public void setDispatcher(@NotNull ModCommand dispatcher) {
    this.dispatcher = dispatcher;
  }

  public ModCommand dispatcher() {
    return dispatcher;
  }

  public InvseeService invsee() {
    return invsee;
  }

  public JavaPlugin plugin() {
    return plugin;
  }

  public ModerationConfig config() {
    return config;
  }

  public Messages messages() {
    return config.messages();
  }

  public ModerationService moderation() {
    return moderation;
  }

  public TargetResolver targets() {
    return targets;
  }

  public PlayerRegistry players() {
    return players;
  }

  public MuteRegistry mutes() {
    return mutes;
  }

  public FreezeManager freeze() {
    return freeze;
  }

  public CooldownService cooldowns() {
    return cooldowns;
  }
}
