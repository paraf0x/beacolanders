package ua.favn.beacolanders;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ua.favn.beacolanders.commands.BeacolandersCommand;
import ua.favn.beacolanders.data.AchievementManager;
import ua.favn.beacolanders.data.DatabaseReader;
import ua.favn.beacolanders.data.LeaderboardService;
import ua.favn.beacolanders.gui.GuiListener;

import java.io.File;
import java.sql.SQLException;

public class Beacolanders extends JavaPlugin {

    private static final int LEADERBOARD_CACHE_TTL_SECONDS = 60;

    private DatabaseReader databaseReader;
    private LeaderboardService leaderboardService;
    private AchievementManager achievementManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("achievements.yml", false);
        initAchievements();
        int ttl = getConfig().getInt("leaderboard.cache-ttl-seconds", 60);
        leaderboardService = new LeaderboardService(ttl);
        initDatabase();
        registerCommand();
        registerListener();
        getLogger().info("Beacolanders enabled!");
    }

    @Override
    public void onDisable() {
        if (databaseReader != null) {
            databaseReader.close();
        }
        getLogger().info("Beacolanders disabled!");
    }

    public DatabaseReader getDatabaseReader() {
        return databaseReader;
    }

    public LeaderboardService getLeaderboardService() {
        return leaderboardService;
    }

    public AchievementManager getAchievementManager() {
        return achievementManager;
    }

    private void initAchievements() {
        achievementManager = new AchievementManager();
        File achievementsFile = new File(getDataFolder(), "achievements.yml");
        org.bukkit.configuration.file.YamlConfiguration config =
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(achievementsFile);
        achievementManager.load(config);
        getLogger().info("Loaded " + achievementManager.getAll().size() + " achievements.");
    }

    private void initDatabase() {
        String bmPath = getConfig().getString("database.basemanager",
            "../BaseManager/storage.db");
        String ssPath = getConfig().getString("database.shopsearch",
            "../ShopSearch/storage.db");

        File bmFile = new File(getDataFolder(), bmPath);
        File ssFile = new File(getDataFolder(), ssPath);

        if (!bmFile.exists()) {
            getLogger().warning("BaseManager database not found: "
                + bmFile.getAbsolutePath());
        }
        if (!ssFile.exists()) {
            getLogger().warning("ShopSearch database not found: "
                + ssFile.getAbsolutePath());
        }

        if (bmFile.exists() && ssFile.exists()) {
            try {
                databaseReader = new DatabaseReader(bmFile, ssFile);
            } catch (SQLException e) {
                getLogger().severe("Failed to open databases: "
                    + e.getMessage());
            }
        }
    }

    private void registerCommand() {
        var command = getCommand("beacolanders");
        if (command != null) {
            BeacolandersCommand executor = new BeacolandersCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    private void registerListener() {
        getServer().getPluginManager()
            .registerEvents(new GuiListener(), this);
    }
}
