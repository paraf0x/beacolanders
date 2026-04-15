package ua.favn.beacolanders.data;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class LeaderboardService {

    private final long cacheTtlMs;
    private final Map<StatCategory, CachedResult> cache =
        new EnumMap<>(StatCategory.class);

    public LeaderboardService(int cacheTtlSeconds) {
        this.cacheTtlMs = cacheTtlSeconds * 1000L;
    }

    public List<LeaderboardEntry> getTop(StatCategory category, int limit) {
        List<LeaderboardEntry> all = getRanked(category);
        return all.subList(0, Math.min(limit, all.size()));
    }

    public LeaderboardEntry getRank(StatCategory category, OfflinePlayer player) {
        List<LeaderboardEntry> all = getRanked(category);
        for (LeaderboardEntry entry : all) {
            if (entry.player().equals(player.getUniqueId())) {
                return entry;
            }
        }
        String name = player.getName() != null ? player.getName() : "Unknown";
        return new LeaderboardEntry(player.getUniqueId(), name, 0, all.size() + 1);
    }

    public void invalidate() {
        cache.clear();
    }

    private List<LeaderboardEntry> getRanked(StatCategory category) {
        CachedResult cached = cache.get(category);
        if (cached != null && !cached.isExpired(cacheTtlMs)) {
            return cached.entries;
        }
        List<LeaderboardEntry> entries = collectStats(category);
        cache.put(category, new CachedResult(entries));
        return entries;
    }

    private List<LeaderboardEntry> collectStats(StatCategory category) {
        Statistic stat = category.statistic();
        List<RawEntry> raw = new ArrayList<>();
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() == null) {
                continue;
            }
            try {
                long value = op.getStatistic(stat);
                if (value > 0) {
                    raw.add(new RawEntry(op, value));
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        raw.sort(Comparator.comparingLong(RawEntry::value).reversed());
        List<LeaderboardEntry> entries = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            RawEntry r = raw.get(i);
            entries.add(new LeaderboardEntry(
                r.player.getUniqueId(), r.player.getName(), r.value, i + 1));
        }
        return entries;
    }

    private record RawEntry(OfflinePlayer player, long value) {
    }

    private static final class CachedResult {
        final List<LeaderboardEntry> entries;
        final long timestamp;

        CachedResult(List<LeaderboardEntry> entries) {
            this.entries = entries;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired(long ttlMs) {
            return System.currentTimeMillis() - timestamp > ttlMs;
        }
    }
}
