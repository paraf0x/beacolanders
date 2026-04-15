package ua.favn.beacolanders.gui;

import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import ua.favn.beacolanders.Beacolanders;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AchievementsGuiTest {

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

    private ServerMock server;
    private Beacolanders plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Beacolanders.class);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(
            new StringReader(YAML));
        plugin.getAchievementManager().load(config);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void constructor_showsBackButton() {
        PlayerMock viewer = server.addPlayer("Viewer");
        AchievementsGui gui = new AchievementsGui(plugin, viewer, viewer);
        Inventory inv = gui.getInventory();

        assertNotNull(inv.getItem(0));
        assertEquals(Material.BARRIER, inv.getItem(0).getType());
    }

    @Test
    void constructor_showsAchievementAtSlot9() {
        PlayerMock viewer = server.addPlayer("Viewer");
        AchievementsGui gui = new AchievementsGui(plugin, viewer, viewer);
        Inventory inv = gui.getInventory();

        assertNotNull(inv.getItem(9));
    }

    @Test
    void tier0_showsGrayPane() {
        PlayerMock viewer = server.addPlayer("Viewer");
        // No stats set, so tier = 0 for all achievements
        AchievementsGui gui = new AchievementsGui(plugin, viewer, viewer);
        Inventory inv = gui.getInventory();

        assertNotNull(inv.getItem(9));
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, inv.getItem(9).getType());
    }

    @Test
    void tier1_showsIconWithCount1() {
        PlayerMock viewer = server.addPlayer("Viewer");
        // MOB_KILLS does not require a parameter - warrior achievement tier 1 = 100
        viewer.setStatistic(Statistic.MOB_KILLS, 100);
        AchievementsGui gui = new AchievementsGui(plugin, viewer, viewer);
        Inventory inv = gui.getInventory();

        // Slot 10 = warrior achievement (second), tier 1
        assertNotNull(inv.getItem(10));
        assertEquals(Material.DIAMOND_SWORD, inv.getItem(10).getType());
        assertEquals(1, inv.getItem(10).getAmount());
    }

    @Test
    void tier2_showsIconWithCount2() {
        PlayerMock viewer = server.addPlayer("Viewer");
        // MOB_KILLS does not require a parameter - warrior achievement tier 2 = 1000
        viewer.setStatistic(Statistic.MOB_KILLS, 1000);
        AchievementsGui gui = new AchievementsGui(plugin, viewer, viewer);
        Inventory inv = gui.getInventory();

        // Slot 10 = warrior achievement (second), tier 2
        assertNotNull(inv.getItem(10));
        assertEquals(Material.DIAMOND_SWORD, inv.getItem(10).getType());
        assertEquals(2, inv.getItem(10).getAmount());
    }
}
