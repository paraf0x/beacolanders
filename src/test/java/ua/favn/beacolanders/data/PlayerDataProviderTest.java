package ua.favn.beacolanders.data;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerDataProviderTest {

    @Mock
    private Player player;

    @Mock
    private EntityEquipment equipment;

    @Mock
    private OfflinePlayer offlinePlayer;

    private PlayerDataProvider provider;

    @BeforeEach
    void setUp() {
        provider = new PlayerDataProvider();
    }

    @Test
    void getEquipment_onlinePlayer_returnsItems() {
        when(player.getEquipment()).thenReturn(equipment);
        when(equipment.getHelmet()).thenReturn(null);
        when(equipment.getChestplate()).thenReturn(null);
        when(equipment.getLeggings()).thenReturn(null);
        when(equipment.getBoots()).thenReturn(null);
        when(equipment.getItemInMainHand()).thenReturn(null);
        when(equipment.getItemInOffHand()).thenReturn(null);

        ItemStack[] result = provider.getEquipment(player);

        assertNotNull(result);
        assertEquals(6, result.length);
    }

    @Test
    void getEquipment_nullEquipment_returnsNull() {
        when(player.getEquipment()).thenReturn(null);

        ItemStack[] result = provider.getEquipment(player);

        assertNull(result);
    }

    @Test
    void getStats_returnsStatMap() {
        when(offlinePlayer.getStatistic(Statistic.DEATHS)).thenReturn(5);
        when(offlinePlayer.getStatistic(Statistic.PLAYER_KILLS)).thenReturn(2);
        when(offlinePlayer.getStatistic(Statistic.MOB_KILLS)).thenReturn(10);
        when(offlinePlayer.getStatistic(Statistic.WALK_ONE_CM)).thenReturn(1000);
        when(offlinePlayer.getStatistic(Statistic.FLY_ONE_CM)).thenReturn(500);
        when(offlinePlayer.getStatistic(Statistic.MINE_BLOCK))
            .thenThrow(new IllegalArgumentException());
        when(offlinePlayer.getStatistic(Statistic.CRAFT_ITEM))
            .thenThrow(new IllegalArgumentException());
        when(offlinePlayer.getStatistic(Statistic.FISH_CAUGHT)).thenReturn(0);
        when(offlinePlayer.getStatistic(Statistic.JUMP)).thenReturn(50);
        when(offlinePlayer.getStatistic(Statistic.DAMAGE_DEALT)).thenReturn(100);
        when(offlinePlayer.getStatistic(Statistic.DAMAGE_TAKEN)).thenReturn(50);
        when(offlinePlayer.getStatistic(Statistic.PLAY_ONE_MINUTE)).thenReturn(72000);

        Map<Statistic, Integer> stats = provider.getStats(offlinePlayer);

        assertNotNull(stats);
        assertEquals(5, stats.get(Statistic.DEATHS));
        assertEquals(2, stats.get(Statistic.PLAYER_KILLS));
        assertEquals(10, stats.get(Statistic.MOB_KILLS));
        assertEquals(10, stats.size());
    }

    @Test
    void getPlaytimeTicks_returnsTickCount() {
        when(offlinePlayer.getStatistic(Statistic.PLAY_ONE_MINUTE)).thenReturn(72000);

        int ticks = provider.getPlaytimeTicks(offlinePlayer);

        assertEquals(72000, ticks);
    }
}
