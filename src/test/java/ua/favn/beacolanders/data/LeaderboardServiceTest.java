package ua.favn.beacolanders.data;

import org.bukkit.Statistic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeaderboardServiceTest {

    private ServerMock server;
    private LeaderboardService service;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        service = new LeaderboardService(60);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void getTop_ranksPlayersDescending() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        PlayerMock carol = server.addPlayer("Carol");
        alice.setStatistic(Statistic.DEATHS, 50);
        bob.setStatistic(Statistic.DEATHS, 100);
        carol.setStatistic(Statistic.DEATHS, 25);

        List<LeaderboardEntry> top = service.getTop(StatCategory.DEATHS, 10);

        assertEquals(3, top.size());
        assertEquals("Bob", top.get(0).name());
        assertEquals(100, top.get(0).value());
        assertEquals(1, top.get(0).rank());
        assertEquals("Alice", top.get(1).name());
        assertEquals(2, top.get(1).rank());
        assertEquals("Carol", top.get(2).name());
        assertEquals(3, top.get(2).rank());
    }

    @Test
    void getTop_limitsToN() {
        for (int i = 0; i < 15; i++) {
            PlayerMock p = server.addPlayer("Player" + i);
            p.setStatistic(Statistic.JUMP, (i + 1) * 100);
        }

        List<LeaderboardEntry> top = service.getTop(StatCategory.JUMPS, 10);

        assertEquals(10, top.size());
        assertEquals(1, top.get(0).rank());
        assertEquals(10, top.get(9).rank());
    }

    @Test
    void getRank_returnsPlayerRank() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        alice.setStatistic(Statistic.DEATHS, 10);
        bob.setStatistic(Statistic.DEATHS, 20);

        LeaderboardEntry entry = service.getRank(StatCategory.DEATHS, alice);

        assertEquals("Alice", entry.name());
        assertEquals(10, entry.value());
        assertEquals(2, entry.rank());
    }

    @Test
    void getTop_emptyServer_returnsEmpty() {
        List<LeaderboardEntry> top = service.getTop(StatCategory.DEATHS, 10);
        assertTrue(top.isEmpty());
    }
}
