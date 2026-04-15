# Leaderboards & Achievements — Design Spec

## Overview

Extend Beacolanders with two features: per-stat leaderboards (Top 10) and config-based achievements with 3 tiers. Both use vanilla Minecraft statistics as their data source. Accessible via GUI buttons in the existing player list and via chat commands.

## Leaderboards

### GUI: Leaderboard View

A 6-row (54-slot) chest inventory per stat category.

| Row | Content |
|-----|---------|
| 1 | Navigation: Back button (slot 0), stat icon + title (slot 4) |
| 2–4 | Top 10 player heads (slots 9–18), ranked #1–#10. Each head shows rank, player name, and stat value in lore. Empty slots if fewer than 10 players. |
| 5 | Empty (separator) |
| 6 | "You" slot: viewer's own head (slot 4) with rank + value in lore. Always shown regardless of whether they're in the top 10. |

Player heads use `SkullMeta.setOwningPlayer()`. Rank #1 head gets an enchant glint (`addUnsafeEnchantment` + `HIDE_ENCHANTS` flag) to stand out.

### Stat Categories

Each stat gets its own leaderboard. The stat list is the same set already tracked in PlayerDataProvider:

| Stat | Display Name | Icon |
|------|-------------|------|
| PLAY_ONE_MINUTE | Playtime | Clock |
| DEATHS | Deaths | Skeleton Skull |
| PLAYER_KILLS | Player Kills | Diamond Sword |
| MOB_KILLS | Mob Kills | Iron Sword |
| WALK_ONE_CM | Distance Walked | Leather Boots |
| FLY_ONE_CM | Distance Flown | Elytra |
| MINE_BLOCK | Blocks Mined | Diamond Pickaxe |
| CRAFT_ITEM | Items Crafted | Crafting Table |
| FISH_CAUGHT | Fish Caught | Fishing Rod |
| JUMP | Jumps | Rabbit Foot |

### Leaderboard Selector GUI

A 6-row chest showing all available stat categories as clickable items. Click one to open that stat's leaderboard.

| Row | Content |
|-----|---------|
| 1 | Back button (slot 0), title "Leaderboards" (slot 4) |
| 2–5 | Stat category items (same icons as above), one per stat |
| 6 | Empty |

### Data Collection

Leaderboards query `OfflinePlayer.getStatistic()` for all known players (`Bukkit.getOfflinePlayers()`). To avoid blocking the main thread:

- Leaderboard data is computed **asynchronously** when the GUI is opened.
- Results are cached in memory with a configurable TTL (default: 60 seconds) to avoid re-querying on every GUI open.
- Cache is per-stat: `Map<Statistic, LeaderboardEntry[]>` where `LeaderboardEntry` is `record LeaderboardEntry(UUID player, String name, long value, int rank)`.

### Value Formatting

- PLAY_ONE_MINUTE: ticks → "Xd Xh" (days + hours)
- WALK_ONE_CM / FLY_ONE_CM: cm → "X.X km"
- All others: plain number with thousands separator (e.g., "12,345")

### Commands

- `/bl top` — opens the leaderboard selector GUI
- `/bl top <stat>` — opens the leaderboard for a specific stat. `<stat>` is the lowercase display name (e.g., `playtime`, `deaths`, `kills`). Tab-completes.

### Chat Output

When used from console or with `--chat` flag, `/bl top <stat>` prints the top 10 in chat instead of opening a GUI:

```
[Beacolanders] Top 10 — Playtime
#1  Steve      48d 12h
#2  Alex       32d 5h
...
#10 Bob        2d 1h
---
#42 You        0d 3h
```

## Achievements

### Config Format

Achievements are defined in `achievements.yml` (saved as default resource, editable by admins):

```yaml
achievements:
  miner:
    name: "Miner"
    icon: DIAMOND_PICKAXE
    stat: MINE_BLOCK
    tiers:
      1: 1000
      2: 10000
      3: 100000

  traveler:
    name: "Traveler"
    icon: LEATHER_BOOTS
    stat: WALK_ONE_CM
    tiers:
      1: 10000000     # 100 km
      2: 100000000    # 1000 km
      3: 1000000000   # 10000 km

  warrior:
    name: "Warrior"
    icon: DIAMOND_SWORD
    stat: MOB_KILLS
    tiers:
      1: 100
      2: 1000
      3: 10000

  # Admins add more here...
```

Each achievement has:
- `name`: Display name
- `icon`: Material for the GUI item
- `stat`: Vanilla Statistic enum name
- `tiers`: Map of tier level (1/2/3) to threshold value

### GUI: Achievements View

A 6-row chest showing all achievements for the viewing player.

| Row | Content |
|-----|---------|
| 1 | Back button (slot 0), title "Achievements" (slot 4) |
| 2–5 | Achievement items, one per achievement. Paginated if more than 28. |
| 6 | Pagination (prev/next) if needed |

Each achievement item:
- **Material**: The configured icon material
- **Stack count**: Current tier level (1, 2, or 3). If not yet achieved, count is 1 but the item is greyed out (grey stained glass pane with the achievement name).
- **Display name**: Achievement name + tier label (e.g., "Miner II")
- **Lore**:
  - Current progress: "1,234 / 10,000 blocks"
  - Progress bar: `[████░░░░░░] 12%`
  - Tier thresholds: "Tier 1: 1,000 ✔ | Tier 2: 10,000 | Tier 3: 100,000"

### Achievement Storage

No persistent storage needed. Achievements are computed on-the-fly from vanilla statistics against the config thresholds. The stat value determines the current tier:

```
value >= tier3 threshold → tier 3
value >= tier2 threshold → tier 2
value >= tier1 threshold → tier 1
value < tier1 threshold → not achieved (tier 0)
```

### Commands

- `/bl achievements` — opens the achievements GUI for the executing player
- `/bl achievements <player>` — opens achievements GUI for another player. Tab-completes online player names.

### Chat Output

No chat output mode for achievements — GUI only. The progress bars and tier indicators don't translate well to chat.

## Integration with Existing Plugin

### Command Changes

The existing `/bl` (`/beacolanders`) command needs subcommand routing:

- `/bl` (no args) — opens player list GUI (unchanged)
- `/bl top [stat]` — leaderboards
- `/bl achievements [player]` — achievements

The `BeacolandersCommand` executor needs to be updated to dispatch subcommands instead of always opening the player list.

### Player List GUI Changes

Add two new buttons to the navigation bar (row 1):

| Slot | Item | Action |
|------|------|--------|
| 0 | Compass — Search | Opens AnvilGUI search (unchanged) |
| 2 | Gold Ingot — Leaderboards | Opens leaderboard selector GUI |
| 4 | Nether Star — Beacolanders | Title (unchanged) |
| 6 | Emerald — Achievements | Opens achievements GUI for the viewer |

### Player Detail GUI Changes

No changes to the detail view. Achievements and leaderboards are global features, not per-player-detail features.

## Package Structure (additions)

```
ua.favn.beacolanders/
  commands/
    BeacolandersCommand.java    — Updated: dispatch subcommands
  gui/
    LeaderboardSelectorGui.java — New: stat category picker
    LeaderboardGui.java         — New: top 10 + "you" display
    AchievementsGui.java        — New: achievement grid with progress
  data/
    LeaderboardService.java     — New: async stat collection + caching
    AchievementManager.java     — New: config loading + tier computation
```

## Configuration

`achievements.yml` (new file, saved as default resource):
- See format above. Loaded on plugin enable and on `/bl reload` (if added later).

`config.yml` additions:
```yaml
leaderboard:
  cache-ttl-seconds: 60
```

## Edge Cases

- **No players**: Leaderboard shows empty slots with "No data" indicator.
- **Stat requires sub-type**: MINE_BLOCK and CRAFT_ITEM are untyped totals from `getStatistic(Statistic)` — they return the sum across all materials. This is intentional for simplicity.
- **New achievement added to config**: Instantly works — no migration needed since everything is computed from live stats.
- **Invalid stat in config**: Log warning on load, skip that achievement.
- **Player with no stats**: Shows tier 0 (grey pane) for all achievements, rank "N/A" on leaderboards.

## Out of Scope

- Achievement announcements in chat (when a player hits a new tier)
- Rewards for achievements (items, titles, permissions)
- Custom (non-vanilla) stat tracking
- Persistent achievement unlock timestamps
- Leaderboard history/trends over time
