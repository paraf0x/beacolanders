package ua.favn.beacolanders.data;

import org.bukkit.Material;
import org.bukkit.Statistic;

public record AchievementDefinition(
    String key,
    String name,
    Material icon,
    Statistic stat,
    long tier1,
    long tier2,
    long tier3
) {

    public int getTier(long value) {
        if (value >= tier3) {
            return 3;
        }
        if (value >= tier2) {
            return 2;
        }
        if (value >= tier1) {
            return 1;
        }
        return 0;
    }

    public long getNextThreshold(int currentTier) {
        return switch (currentTier) {
            case 0 -> tier1;
            case 1 -> tier2;
            case 2 -> tier3;
            default -> tier3;
        };
    }
}
