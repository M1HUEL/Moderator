package com.itson.moderator;

import com.itson.moderator.command.FreezeCommand;
import com.itson.moderator.command.GameModeCommand;
import com.itson.moderator.command.HealCommand;
import com.itson.moderator.command.FeedCommand;
import com.itson.moderator.command.ClearCommand;
import com.itson.moderator.command.HelpCommand;
import com.itson.moderator.command.HistoryCommand;
import com.itson.moderator.command.InvseeCommand;
import com.itson.moderator.command.KillCommand;
import com.itson.moderator.command.ModCommand;
import com.itson.moderator.command.ModContext;
import com.itson.moderator.command.ProfileCommand;
import com.itson.moderator.command.PunishCommand;
import com.itson.moderator.command.ReloadCommand;
import com.itson.moderator.command.ReportCommand;
import com.itson.moderator.command.ResolveCommand;
import com.itson.moderator.command.ReportsCommand;
import com.itson.moderator.command.RevokeCommand;
import com.itson.moderator.command.SubCommand;
import com.itson.moderator.command.TeleportCommand;
import com.itson.moderator.config.ModerationConfig;
import com.itson.moderator.listener.FreezeListener;
import com.itson.moderator.listener.InvseeListener;
import com.itson.moderator.listener.MuteListener;
import com.itson.moderator.listener.SessionListener;
import com.itson.moderator.model.Punishment;
import com.itson.moderator.model.PunishmentType;
import com.itson.moderator.service.CooldownService;
import com.itson.moderator.service.FreezeManager;
import com.itson.moderator.service.InvseeService;
import com.itson.moderator.service.ModerationService;
import com.itson.moderator.service.MuteRegistry;
import com.itson.moderator.service.PlayerRegistry;
import com.itson.moderator.service.TargetResolver;
import com.itson.moderator.storage.YamlStore;
import java.io.File;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Moderation toolkit: sanctions, warnings, reports, history and the player tools
 * a moderator reaches for during a session.
 *
 * <p>All state lives in a small set of services constructed here, so the command
 * and listener classes stay free of wiring. Temporary sanctions are ticked by a
 * repeating task that also flushes storage, which is the only place the plugin
 * writes to disk outside a command.
 */
public final class ModeratorPlugin extends JavaPlugin {

  private static final long EXPIRY_INTERVAL_TICKS = 1200L;

  private static final long FLUSH_INTERVAL_TICKS = 6000L;

  private ModerationConfig config;

  private YamlStore store;

  private ModerationService moderation;

  private MuteRegistry mutes;

  private FreezeManager freeze;

  private CooldownService cooldowns;

  private InvseeService invsee;

  private ModContext context;

  private BukkitTask maintenance;

  @Override
  public void onEnable() {
    config = new ModerationConfig();
    config.load(this);

    store = new YamlStore(new File(getDataFolder(), "data.yml"), getLogger());
    store.load();
    pruneHistory();

    mutes = new MuteRegistry();
    freeze = new FreezeManager(store);
    cooldowns = new CooldownService();
    cooldowns.bind(config);
    invsee = new InvseeService();

    PlayerRegistry players = new PlayerRegistry(store);

    moderation = new ModerationService(this, config, store, mutes, players, Clock.systemUTC());
    moderation.reloadMutes();

    context = new ModContext(this, config, moderation, new TargetResolver(players), players, mutes, freeze, cooldowns,
        invsee);

    registerCommands();
    registerListeners();
    startMaintenance();

    getLogger().info("Moderator enabled on " + getServer().getMinecraftVersion() + " with " + mutes.size()
        + " active mute(s), " + store.punishments().size() + " stored sanction(s) and " + freeze.size()
        + " restored freeze(s).");
  }

  @Override
  public void onDisable() {
    if (maintenance != null) {
      maintenance.cancel();
      maintenance = null;
    }

    releaseEveryone();

    if (store != null) {
      store.save();
    }

    if (mutes != null) {
      mutes.clear();
    }

    if (invsee != null) {
      invsee.reset();
    }

    getLogger().info("Moderator disabled.");
  }

  private void registerCommands() {
    ModCommand dispatcher = new ModCommand(context, tools());
    context.setDispatcher(dispatcher);

    PluginCommand mod = getCommand("mod");

    if (mod != null) {
      mod.setExecutor(dispatcher);
      mod.setTabCompleter(dispatcher);
    } else {
      getLogger().severe("Command 'mod' is missing from plugin.yml, no moderation tool will be reachable.");
    }

    PluginCommand report = getCommand("report");

    if (report != null) {
      ReportCommand handler = new ReportCommand(context);

      report.setExecutor(handler);
      report.setTabCompleter(handler);
    } else {
      getLogger().severe("Command 'report' is missing from plugin.yml, players cannot report anyone.");
    }
  }

  private void registerListeners() {
    getServer().getPluginManager().registerEvents(new MuteListener(context), this);
    getServer().getPluginManager().registerEvents(new SessionListener(context), this);
    getServer().getPluginManager().registerEvents(new FreezeListener(context), this);
    getServer().getPluginManager().registerEvents(new InvseeListener(context), this);
  }

  /**
   * Expires temporary sanctions and flushes storage.
   *
   * <p>Both are cheap enough to do on a timer, which keeps every command and
   * listener free of scheduling concerns.
   */
  private void startMaintenance() {
    maintenance = getServer().getScheduler().runTaskTimer(this, () -> {
      for (Punishment expired : moderation.expire()) {
        announceExpiry(expired);
      }

      if (store.isDirty()) {
        store.save();
      }
    }, EXPIRY_INTERVAL_TICKS, EXPIRY_INTERVAL_TICKS);

    getServer().getScheduler().runTaskTimer(this, store::save, FLUSH_INTERVAL_TICKS, FLUSH_INTERVAL_TICKS);
  }

  /**
   * Forgets finished sanctions the owner asked not to keep, once, on enable.
   *
   * <p>Disabled by default, because deleting a moderation history is a decision
   * about compliance, not about the server's comfort. When it is on, the count is
   * logged: a silent deletion of somebody's ban record would be indefensible.
   */
  private void pruneHistory() {
    if (config.retentionDays() <= 0) {
      return;
    }

    int dropped = store.pruneFinished(Instant.now().minus(Duration.ofDays(config.retentionDays())));

    if (dropped > 0) {
      getLogger().info("Pruned " + dropped + " finished sanction(s) older than " + config.retentionDays() + " days.");
    }
  }

  private void announceExpiry(Punishment punishment) {
    if (!config.autoUnbanOnExpiry() || !punishment.type().isBanListBacked()) {
      return;
    }

    com.itson.moderator.command.Feedback.broadcast(context, "punish-expired", "type", punishment.type().label(),
        "player", punishment.targetName(), "reason", punishment.reason());
  }

  /**
   * Hands a frozen player their avatar back before the plugin goes away.
   *
   * <p>The freeze itself stays on file. Shutdown is not the same as a release, so
   * the records survive and the next start puts these players back under control.
   */
  private void releaseEveryone() {
    if (freeze == null) {
      return;
    }

    freeze.releaseEveryone();
  }

  /** Every tool, in the order they show up in {@code /mod help}. */
  private static List<SubCommand> tools() {
    List<SubCommand> tools = new ArrayList<>();

    tools.addAll(PunishCommand.all());
    tools.addAll(RevokeCommand.all());
    tools.add(new HistoryCommand());
    tools.add(new ProfileCommand());
    tools.add(new ReportsCommand());
    tools.add(new ResolveCommand());
    tools.addAll(FreezeCommand.all());
    tools.add(new TeleportCommand());
    tools.add(new HealCommand());
    tools.add(new FeedCommand());
    tools.add(new ClearCommand());
    tools.add(new GameModeCommand());
    tools.add(new KillCommand());
    tools.add(new InvseeCommand());
    tools.add(new ReloadCommand());
    tools.add(new HelpCommand());

    return List.copyOf(tools);
  }

  public ModerationService moderation() {
    return moderation;
  }

  public ModerationConfig moderationConfig() {
    return config;
  }

  public MuteRegistry mutes() {
    return mutes;
  }

  public FreezeManager freeze() {
    return freeze;
  }

  public ModContext context() {
    return context;
  }

  /** The punishment types the plugin can apply, for documentation and tests. */
  public static List<PunishmentType> types() {
    return List.of(PunishmentType.values());
  }
}
