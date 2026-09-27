package com.itson.moderator;

import org.bukkit.plugin.java.JavaPlugin;

public final class ModeratorPlugin extends JavaPlugin {

  @Override
  public void onEnable() {
    getLogger().info("Moderator enabled on " + getServer().getMinecraftVersion() + ".");
  }

  @Override
  public void onDisable() {
    getLogger().info("Moderator disabled.");
  }
}
