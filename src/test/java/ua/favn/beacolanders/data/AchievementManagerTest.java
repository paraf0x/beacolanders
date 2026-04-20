package ua.favn.beacolanders.data;

import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AchievementManagerTest {

    private static final String YAML = """
        achievements:
          miner:
            name: "Miner"
            icon: DIAMOND_PICKAXE
            stat: MINE_BLOCK
            tiers:
              1: 1000
              2: 10000
              3: 100000
          warrior:
            name: "Warrior"
            icon: DIAMOND_SWORD
            stat: MOB_KILLS
            tiers:
              1: 100
              2: 1000
              3: 10000
        """;

    @Test
    void load_parsesAchievementsFromYaml() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new StringReader(YAML));
        AchievementManager manager = new AchievementManager();
        manager.load(config);

        List<AchievementDefinition> defs = manager.getAll();
        assertEquals(2, defs.size());
    }

    @Test
    void load_parsesFieldsCorrectly() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new StringReader(YAML));
        AchievementManager manager = new AchievementManager();
        manager.load(config);

        AchievementDefinition miner = manager.get("miner");
        assertNotNull(miner);
        assertEquals("Miner", miner.name());
        assertEquals(Material.DIAMOND_PICKAXE, miner.icon());
        assertEquals(Statistic.MINE_BLOCK, miner.stat());
        assertEquals(1000, miner.tier1());
        assertEquals(10000, miner.tier2());
        assertEquals(100000, miner.tier3());
    }

    @Test
    void getTier_computesCorrectly() {
        AchievementDefinition def = new AchievementDefinition(
            "test", "Test", Material.STONE, Statistic.JUMP, 100, 1000, 10000);

        assertEquals(0, def.getTier(0));
        assertEquals(0, def.getTier(99));
        assertEquals(1, def.getTier(100));
        assertEquals(1, def.getTier(999));
        assertEquals(2, def.getTier(1000));
        assertEquals(2, def.getTier(9999));
        assertEquals(3, def.getTier(10000));
        assertEquals(3, def.getTier(99999));
    }

    @Test
    void load_skipsInvalidStat() {
        String yaml = """
            achievements:
              broken:
                name: "Broken"
                icon: STONE
                stat: NOT_A_REAL_STAT
                tiers:
                  1: 100
                  2: 200
                  3: 300
            """;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        AchievementManager manager = new AchievementManager();
        manager.load(config);

        assertTrue(manager.getAll().isEmpty());
    }
}
