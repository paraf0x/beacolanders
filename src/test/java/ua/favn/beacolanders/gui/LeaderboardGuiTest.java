package ua.favn.beacolanders.gui;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import ua.favn.beacolanders.Beacolanders;
import ua.favn.beacolanders.data.StatCategory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LeaderboardGuiTest {

    private ServerMock server;
    private Beacolanders plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Beacolanders.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void selector_showsBackButton() {
        LeaderboardSelectorGui gui = new LeaderboardSelectorGui(plugin);
        Inventory inv = gui.getInventory();

        assertNotNull(inv.getItem(0));
        assertEquals(Material.BARRIER, inv.getItem(0).getType());
    }

    @Test
    void selector_showsCategoryAtSlot9() {
        LeaderboardSelectorGui gui = new LeaderboardSelectorGui(plugin);
        Inventory inv = gui.getInventory();

        // Slot 9 = first StatCategory (PLAYTIME = CLOCK)
        assertNotNull(inv.getItem(9));
        assertEquals(Material.CLOCK, inv.getItem(9).getType());
    }

    @Test
    void leaderboard_showsBackButton() {
        PlayerMock player = server.addPlayer("Alice");
        LeaderboardGui gui = new LeaderboardGui(plugin, StatCategory.PLAYTIME, player);
        Inventory inv = gui.getInventory();

        assertNotNull(inv.getItem(0));
        assertEquals(Material.BARRIER, inv.getItem(0).getType());
    }

    @Test
    void leaderboard_showsStatIconAtSlot4() {
        PlayerMock player = server.addPlayer("Alice");
        LeaderboardGui gui = new LeaderboardGui(plugin, StatCategory.PLAYTIME, player);
        Inventory inv = gui.getInventory();

        assertNotNull(inv.getItem(4));
        assertEquals(Material.CLOCK, inv.getItem(4).getType());
    }

    @Test
    void leaderboard_showsViewerHeadAtSlot49() {
        PlayerMock player = server.addPlayer("Alice");
        LeaderboardGui gui = new LeaderboardGui(plugin, StatCategory.PLAYTIME, player);
        Inventory inv = gui.getInventory();

        // Slot 49 = row 6, slot 4 = viewer's own head
        assertNotNull(inv.getItem(49));
        assertEquals(Material.PLAYER_HEAD, inv.getItem(49).getType());
    }
}
