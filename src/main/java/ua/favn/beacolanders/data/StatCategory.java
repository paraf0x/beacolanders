package ua.favn.beacolanders.data;

import org.bukkit.Material;
import org.bukkit.Statistic;

import java.text.NumberFormat;
import java.util.Locale;

public enum StatCategory {

    PLAYTIME(Statistic.PLAY_ONE_MINUTE, "Playtime", Material.CLOCK),
    DEATHS(Statistic.DEATHS, "Deaths", Material.SKELETON_SKULL),
    PLAYER_KILLS(Statistic.PLAYER_KILLS, "Player Kills", Material.DIAMOND_SWORD),
    MOB_KILLS(Statistic.MOB_KILLS, "Mob Kills", Material.IRON_SWORD),
    WALKED(Statistic.WALK_ONE_CM, "Distance Walked", Material.LEATHER_BOOTS),
    FLOWN(Statistic.FLY_ONE_CM, "Distance Flown", Material.ELYTRA),
    MINED(Statistic.MINE_BLOCK, "Blocks Mined", Material.DIAMOND_PICKAXE),
    CRAFTED(Statistic.CRAFT_ITEM, "Items Crafted", Material.CRAFTING_TABLE),
    FISH(Statistic.FISH_CAUGHT, "Fish Caught", Material.FISHING_ROD),
    JUMPS(Statistic.JUMP, "Jumps", Material.RABBIT_FOOT);

    private static final NumberFormat NUMBER_FMT =
        NumberFormat.getIntegerInstance(Locale.US);

    private final Statistic statistic;
    private final String displayName;
    private final Material icon;

    StatCategory(Statistic statistic, String displayName, Material icon) {
        this.statistic = statistic;
        this.displayName = displayName;
        this.icon = icon;
    }

    public Statistic statistic() {
        return statistic;
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    public String format(long value) {
        return switch (this) {
            case PLAYTIME -> formatTicks(value);
            case WALKED, FLOWN -> formatCm(value);
            default -> NUMBER_FMT.format(value);
        };
    }

    public static StatCategory fromKeyword(String keyword) {
        String lower = keyword.toLowerCase(Locale.ROOT);
        for (StatCategory cat : values()) {
            if (cat.displayName.toLowerCase(Locale.ROOT).contains(lower)) {
                return cat;
            }
        }
        return null;
    }

    private static String formatTicks(long ticks) {
        long totalSeconds = ticks / 20;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        return hours + "h " + (totalSeconds % 3600) / 60 + "m";
    }

    private static String formatCm(long cm) {
        double km = cm / 100_000.0;
        return String.format("%.1f km", km);
    }
}
