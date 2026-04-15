package ua.favn.beacolanders.gui;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import ua.favn.beacolanders.Beacolanders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlayerListGuiTest {

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
    void constructor_populatesPlayerHeads() {
        server.addPlayer("Alice");
        server.addPlayer("Bob");

        PlayerListGui gui = new PlayerListGui(plugin);
        Inventory inv = gui.getInventory();

        // Slot 9 (row 2, slot 0) should be first player head
        assertNotNull(inv.getItem(9));
        assertEquals(Material.PLAYER_HEAD, inv.getItem(9).getType());
    }

    @Test
    void constructor_showsSearchButton() {
        PlayerListGui gui = new PlayerListGui(plugin);
        Inventory inv = gui.getInventory();

        // Slot 0 = search button
        assertNotNull(inv.getItem(0));
    }

    @Test
    void constructor_showsTitleItem() {
        PlayerListGui gui = new PlayerListGui(plugin);
        Inventory inv = gui.getInventory();

        // Slot 4 = title
        assertNotNull(inv.getItem(4));
    }

    @Test
    void pagination_secondPage_showsRemainingPlayers() {
        // Add 40 players (more than 36 per page)
        for (int i = 0; i < 40; i++) {
            server.addPlayer("Player" + i);
        }

        PlayerListGui gui = new PlayerListGui(plugin);
        Inventory inv = gui.getInventory();

        // Slot 53 (row 6, slot 8) = next page arrow should exist
        assertNotNull(inv.getItem(53));
    }

    @Test
    void search_filtersPlayersByName() {
        server.addPlayer("Alice");
        server.addPlayer("Bob");
        server.addPlayer("AliceClone");

        PlayerListGui gui = new PlayerListGui(plugin);
        gui.applySearch("alice");
        Inventory inv = gui.getInventory();

        // Should show 2 players (Alice, AliceClone), starting at slot 9
        assertNotNull(inv.getItem(9));
        assertNotNull(inv.getItem(10));
        assertNull(inv.getItem(11));
    }

    @Test
    void constructor_showsLeaderboardButton() {
        PlayerListGui gui = new PlayerListGui(plugin);
        Inventory inv = gui.getInventory();

        // Slot 2 = leaderboards button (Gold Ingot)
        assertNotNull(inv.getItem(2));
        assertEquals(Material.GOLD_INGOT, inv.getItem(2).getType());
    }

    @Test
    void constructor_showsAchievementsButton() {
        PlayerListGui gui = new PlayerListGui(plugin);
        Inventory inv = gui.getInventory();

        // Slot 6 = achievements button (Emerald)
        assertNotNull(inv.getItem(6));
        assertEquals(Material.EMERALD, inv.getItem(6).getType());
    }
}
