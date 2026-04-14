package ua.favn.beacolanders;

import org.bukkit.plugin.java.JavaPlugin;
import ua.favn.beacolanders.data.DatabaseReader;

/**
 * Main plugin class for Beacolanders.
 */
public class Beacolanders extends JavaPlugin {

    @Override
    public void onEnable() {
        this.saveDefaultConfig();

        this.getLogger().info("Beacolanders enabled!");
    }

    @Override
    public void onDisable() {
        this.getLogger().info("Beacolanders disabled!");
    }

    public DatabaseReader getDatabaseReader() {
        return null; // Will be properly initialized in Task 9
    }
}
