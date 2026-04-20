package ua.favn.beacolanders.data;

import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Map;

public final class PlayerDataProvider {

    private static final Statistic[] TRACKED_STATS = {
        Statistic.DEATHS,
        Statistic.PLAYER_KILLS,
        Statistic.MOB_KILLS,
        Statistic.WALK_ONE_CM,
        Statistic.FLY_ONE_CM,
        Statistic.MINE_BLOCK,
        Statistic.CRAFT_ITEM,
        Statistic.FISH_CAUGHT,
        Statistic.JUMP,
        Statistic.DAMAGE_DEALT,
        Statistic.DAMAGE_TAKEN,
        Statistic.PLAY_ONE_MINUTE
    };

    /**
     * Returns equipment array: [helmet, chestplate, leggings, boots, mainHand, offHand].
     * Null entries mean the slot is empty. Returns null if player is not online.
     */
    public ItemStack[] getEquipment(Player player) {
        EntityEquipment eq = player.getEquipment();
        if (eq != null) {
            return new ItemStack[]{
                eq.getHelmet(),
                eq.getChestplate(),
                eq.getLeggings(),
                eq.getBoots(),
                eq.getItemInMainHand(),
                eq.getItemInOffHand()
            };
        }
        return null;
    }

    public Map<Statistic, Integer> getStats(OfflinePlayer player) {
        Map<Statistic, Integer> stats = new EnumMap<>(Statistic.class);
        for (Statistic stat : TRACKED_STATS) {
            try {
                stats.put(stat, player.getStatistic(stat));
            } catch (IllegalArgumentException ignored) {
                // Some stats require sub-type (Material/EntityType), skip those
            }
        }
        return stats;
    }

    public int getPlaytimeTicks(OfflinePlayer player) {
        return player.getStatistic(Statistic.PLAY_ONE_MINUTE);
    }
}
