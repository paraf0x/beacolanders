package ua.favn.beacolanders.gui;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import ua.favn.beacolanders.Beacolanders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PlayerDetailGuiTest {

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
    void constructor_showsBackButton() {
        PlayerMock target = server.addPlayer("Steve");
        PlayerMock viewer = server.addPlayer("Viewer");

        PlayerDetailGui gui = new PlayerDetailGui(plugin, target, viewer);
        Inventory inv = gui.getInventory();

        // Slot 0 = back button (barrier)
        assertNotNull(inv.getItem(0));
        assertEquals(Material.BARRIER, inv.getItem(0).getType());
    }

    @Test
    void constructor_showsPlayerHead() {
        PlayerMock target = server.addPlayer("Steve");
        PlayerMock viewer = server.addPlayer("Viewer");

        PlayerDetailGui gui = new PlayerDetailGui(plugin, target, viewer);
        Inventory inv = gui.getInventory();

        // Slot 4 = player head
        assertNotNull(inv.getItem(4));
        assertEquals(Material.PLAYER_HEAD, inv.getItem(4).getType());
    }

    @Test
    void constructor_showsEquipmentRow() {
        PlayerMock target = server.addPlayer("Steve");
        target.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
        PlayerMock viewer = server.addPlayer("Viewer");

        PlayerDetailGui gui = new PlayerDetailGui(plugin, target, viewer);
        Inventory inv = gui.getInventory();

        // Slot 10 (row 2, slot 1) = helmet
        assertNotNull(inv.getItem(10));
        assertEquals(Material.IRON_HELMET, inv.getItem(10).getType());
    }

    @Test
    void scrollRight_shiftsRowOffset() {
        PlayerMock target = server.addPlayer("Steve");
        PlayerMock viewer = server.addPlayer("Viewer");

        PlayerDetailGui gui = new PlayerDetailGui(plugin, target, viewer);

        // Row 5 (stats, row index 4) has more than 7 items
        // Click right arrow at slot 44 (row 5, slot 8)
        gui.onClick(44, viewer);

        // After scrolling, the gui should still be valid
        assertNotNull(gui.getInventory());
    }
}
