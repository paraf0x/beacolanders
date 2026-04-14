package ua.favn.beacolanders;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class for Beacolanders.
 */
public final class Beacolanders extends JavaPlugin {

    @Override
    public void onEnable() {
        this.saveDefaultConfig();

        this.getLogger().info("Beacolanders enabled!");
    }

    @Override
    public void onDisable() {
        this.getLogger().info("Beacolanders disabled!");
    }
}
