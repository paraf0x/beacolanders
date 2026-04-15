package ua.favn.beacolanders.data;

import java.util.UUID;

public record LeaderboardEntry(UUID player, String name, long value, int rank) {
}
