package ua.favn.beacolanders.commands;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import ua.favn.beacolanders.Beacolanders;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BeacolandersCommandTest {

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
    void onCommand_opensGui() {
        PlayerMock player = server.addPlayer("Steve");

        BeacolandersCommand cmd = new BeacolandersCommand(plugin);
        boolean result = cmd.onCommand(player, null, "bl", new String[]{});

        assertTrue(result);
    }
}
