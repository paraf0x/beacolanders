package ua.favn.beacolanders.data;

import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public final class AchievementManager {

    private static final Logger LOGGER = Logger.getLogger(AchievementManager.class.getName());

    private final Map<String, AchievementDefinition> achievements = new LinkedHashMap<>();

    public void load(YamlConfiguration config) {
        achievements.clear();
        ConfigurationSection section = config.getConfigurationSection("achievements");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }

            String name = entry.getString("name", key);
            Material icon = Material.matchMaterial(entry.getString("icon", "STONE"));
            if (icon == null) {
                icon = Material.STONE;
            }

            Statistic stat;
            try {
                stat = Statistic.valueOf(entry.getString("stat", ""));
            } catch (IllegalArgumentException e) {
                LOGGER.warning("Invalid stat for achievement '" + key + "': " + entry.getString("stat"));
                continue;
            }

            ConfigurationSection tiers = entry.getConfigurationSection("tiers");
            if (tiers == null) {
                continue;
            }

            long tier1 = tiers.getLong("1", 0);
            long tier2 = tiers.getLong("2", 0);
            long tier3 = tiers.getLong("3", 0);

            achievements.put(key, new AchievementDefinition(key, name, icon, stat, tier1, tier2, tier3));
        }
    }

    public List<AchievementDefinition> getAll() {
        return new ArrayList<>(achievements.values());
    }

    public AchievementDefinition get(String key) {
        return achievements.get(key);
    }
}
