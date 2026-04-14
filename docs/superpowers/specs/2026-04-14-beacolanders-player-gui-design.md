# Beacolanders Player GUI — Design Spec

## Overview

Beacolanders is a server companion plugin for the Beacoland Minecraft server (Paper/Purpur 1.21). It provides a chest-based GUI for browsing online players, searching by name, and inspecting player details including equipped gear, vanilla statistics, BaseManager locations, and ShopSearch shops.

## Command

- **Primary**: `/beacolanders`
- **Alias**: `/bl`
- **Behavior**: Opens the Player List GUI
- **Permissions**: None — accessible to all players

## GUI Screens

### Screen 1: Player List

A 6-row (54-slot) chest inventory.

| Row | Content |
|-----|---------|
| 1 | Navigation bar: Search button (slot 0), title item "Beacolanders" (slot 4) |
| 2–5 | Player heads — up to 36 per page, one head per online player. Head displays player name, shows online status via lore. Click to open detail. |
| 6 | Pagination: Previous (slot 0), page indicator (slot 4), Next (slot 8). Arrows hidden/greyed when at bounds. |

**Search**: Clicking the search button (slot 0, row 1) opens an AnvilGUI text input. The typed text filters the player list by name (case-insensitive substring match). Results replace the player grid. An "X" / clear button returns to the full list.

**Player heads**: Use `SkullMeta.setOwningPlayer()` for real player skins. Show player name as item display name. Lore shows brief info (online/offline, gamemode).

### Screen 2: Player Detail

A 6-row (54-slot) chest inventory with **per-row horizontal scrolling**.

| Row | Content | Scrollable |
|-----|---------|------------|
| 1 | Header: Back button (slot 0), player head with name + health/food/level/gamemode (slot 4) | No |
| 2 | Equipment: Helmet (slot 1), Chestplate (slot 2), Leggings (slot 3), Boots (slot 4), gap, Main Hand (slot 6), Off Hand (slot 7) | No |
| 3 | BaseManager Locations | Yes |
| 4 | ShopSearch Shops | Yes |
| 5 | Vanilla Statistics | Yes |
| 6 | Empty (reserved) | No |

#### Row Scroll Mechanic

Each scrollable row operates as a **sliding window** over its item list:

- **Slot 0**: Left arrow (◀) — scrolls the window left
- **Slots 1–7**: 7 visible items from the list at the current offset
- **Slot 8**: Right arrow (▶) — scrolls the window right

Clicking an arrow shifts the offset for **that row only** and re-renders slots 1–7. The right arrow item shows `+N` in its name indicating how many more items exist beyond the visible window. Arrows are greyed out (grey stained glass pane) or hidden when at the start/end of the list.

If a row has ≤7 items, no arrows are shown — items fill from slot 1.

#### Row 1: Header

- **Slot 0**: Barrier block — "← Back to Player List". Click returns to Player List.
- **Slot 4**: Player head with display name. Lore shows:
  - Health (❤), Food (🍖), XP Level (⭐)
  - Gamemode
  - Online/Offline status

#### Row 2: Equipment

Shows the inspected player's currently equipped items in their actual slots. Each slot displays the real item (with enchantments, lore, durability visible). Empty slots show a grey stained glass pane. Only works for online players — offline players show placeholder items.

- Slot 1: Helmet
- Slot 2: Chestplate
- Slot 3: Leggings
- Slot 4: Boots
- Slot 6: Main Hand
- Slot 7: Off Hand

#### Row 3: Locations (from BaseManager)

Each location displayed as a compass item:
- **Display name**: Location name
- **Lore**: Tag (BASE, FARM, etc.), world, coordinates (X, Y, Z), members list, public/private

Data source: BaseManager's `storage.db` — tables `locations`, `location_coords`, `location_members`.

Query: All locations owned by this player's UUID.

#### Row 4: Shops (from ShopSearch)

Each shop displayed as a barrel item:
- **Display name**: Shop name
- **Lore**: World, coordinates (X, Y, Z), stock item count, co-owners

Data source: ShopSearch's `storage.db` — tables `shops`, `stock`, `coowners`.

Query: All shops owned by this player's UUID.

#### Row 5: Vanilla Statistics

Displayed as themed items with stat values in the display name:
- Clock: Playtime (formatted as hours/days)
- Skeleton skull: Deaths
- Diamond sword: Player kills / Mob kills
- Leather boots: Distance walked (km)
- Elytra: Distance flown (km)
- Diamond pickaxe: Blocks mined
- Crafting table: Items crafted
- Fishing rod: Fish caught
- Additional vanilla `Statistic` values as available

Data source: `Player.getStatistic()` / `OfflinePlayer.getStatistic()` from the Bukkit API.

## Data Access

### Strategy: Direct SQLite (Read-Only)

Beacolanders reads BaseManager's and ShopSearch's SQLite databases directly. No compile-time dependency on those plugins.

**Database paths** (configurable in `config.yml`, defaults relative to plugin data folder):
- BaseManager: `../BaseManager/storage.db`
- ShopSearch: `../ShopSearch/storage.db`

**Connection handling**:
- Read-only connections (`?mode=ro` or `PRAGMA query_only = ON`)
- WAL mode for safe concurrent reads while the owning plugin writes
- Single shared connection per database (sufficient for read-only MVP)
- Connections opened on plugin enable, closed on disable

**Async execution**:
- All database reads run on async Bukkit scheduler tasks
- GUI inventory updates dispatched back to the main thread via `Bukkit.getScheduler().runTask()`

### Player Data

- **Equipment**: `Player.getEquipment()` — only available for online players
- **Stats**: `Player.getStatistic()` / `OfflinePlayer.getStatistic()` — works for offline too
- **Health/Food/Level**: `Player.getHealth()`, `.getFoodLevel()`, `.getLevel()` — online only
- **Player heads**: `SkullMeta.setOwningPlayer(OfflinePlayer)` — works for all known players

## Package Structure

```
ua.favn.beacolanders/
  Beacolanders.java            — Main plugin class. Registers command and event listener.
  commands/
    BeacolandersCommand.java   — Handles /bl and /beacolanders. Opens PlayerListGui.
  gui/
    GuiHolder.java             — Base class for GUI screens. Manages inventory creation,
                                  click cancellation, and holder identification.
    PlayerListGui.java         — Player list with pagination and AnvilGUI search.
    PlayerDetailGui.java       — Player detail with row-scroll mechanic.
  data/
    DatabaseReader.java        — Async SQLite read access for BaseManager and ShopSearch.
    PlayerDataProvider.java    — Wraps Bukkit API for stats, equipment, and online status.
```

## Configuration

`config.yml`:
```yaml
database:
  basemanager: ../BaseManager/storage.db
  shopsearch: ../ShopSearch/storage.db
```

`plugin.yml` additions:
```yaml
commands:
  beacolanders:
    description: Open the Beacolanders player menu
    aliases: [bl]
    usage: /<command>
```

## Constraints & Edge Cases

- **Offline players**: Equipment row shows grey panes. Health/food/level unavailable — show "Offline" in lore. Stats still work via OfflinePlayer.
- **Missing databases**: If BaseManager or ShopSearch DB is not found, the corresponding row shows a single info item ("No data available") and logs a warning on startup.
- **Empty rows**: If a player has no locations/shops, show a single grey pane with "None" as display name.
- **Main thread safety**: Never open DB connections or run queries on the main server thread.
- **Inventory click handling**: Cancel all clicks in GUI inventories (prevent item theft). Only process left-clicks on functional slots.

## Out of Scope (MVP)

- Standalone location/shop browsing (separate from player detail)
- Teleportation to locations or shops from the GUI
- Permission-based visibility restrictions
- Caching layer for database results
- Fuzzy search (exact substring match is sufficient)
- Offline player equipment snapshots
