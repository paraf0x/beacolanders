# Daily & Weekly Quests — Design Spec

## Overview

Add a quest system to Beacolanders with two timing categories (daily / weekly) and a mixed visibility model: **1 server-wide Featured Quest** (everyone works toward a shared goal) **+ N personal randomized quests per player** (N configurable, default 3 daily / 2 weekly).

Quests are driven by an extensible **Quest-Type registry**: simple stat-based quests are config-driven, while more creative "special" quest types (e.g. "survive 5 hours without armor", "eat only honey for an in-game day") are implemented as hand-coded Java classes that can register their own Bukkit listeners.

Rewards per quest are configurable and can combine Minecraft XP, items, and **PlayerPoints** currency (via soft-dependency on the PlayerPoints plugin).

## Quest Categories & Selection

### Visibility model

| Type | Count | Scope | Source |
|------|-------|-------|--------|
| Featured Daily | 1 | Server-wide (shared progress) | Date-seeded random from `featured-eligible` daily pool |
| Personal Daily | Configurable (default 3) | Per-player | Random from daily pool, excluding current Featured |
| Featured Weekly | 1 | Server-wide | Date-seeded from `featured-eligible` weekly pool |
| Personal Weekly | Configurable (default 2) | Per-player | Random from weekly pool, excluding current Featured |

### Reset timing

- **Daily**: reset at 00:00 server-time
- **Weekly**: reset Monday 00:00 server-time
- Scheduler polls every 60 seconds for reset conditions; handles "catch-up" on server start if a reset was missed while offline

### Date-seed for Featured Quest

Deterministic selection: `seed = periodStart.toEpochMilli() + category.ordinal()`. All servers running the same plugin version and config would produce the same Featured Quest on the same day — we accept this as fine for a private server.

## Quest-Type System

### Core interface

```java
public interface QuestType {
    String typeId();
    QuestProgress evaluate(Player player, ActiveQuest active);
    void onQuestAssigned(Player player, ActiveQuest active);   // e.g. set snapshot
    void registerListeners(Plugin plugin);                     // optional
}
```

`ActiveQuest` holds per-quest mutable state (`snapshotData` JSON) that each QuestType interprets as it sees fit.

### Built-in types (v1)

**Generic, config-driven:**
- `stat` — polls a Minecraft `Statistic` (optionally with Material/EntityType parameter). Covers most quest shapes. Snapshot = baseline statistic value on assign.
- `event_counter` — counts occurrences of a named Bukkit event. Supported events in v1: `advancement_done`, `tame_entity`, `breed_entity`, `change_world`, `fish_caught_treasure`.

**Hand-coded special types:**
- `naked_streak` — cumulative time worn without any armor equipped. Resets timer to 0 when any armor slot becomes non-empty; otherwise increments per tick while online.
- `diet_streak` — cumulative time while only consuming whitelisted foods. Eating any non-whitelisted food resets timer to 0.
- `fist_kills` — counts `EntityDeathEvent` kills where the killer's main hand was empty.
- `biome_tourist` — counts unique biomes visited during the quest period (via throttled `PlayerMoveEvent`).

Each type lives in its own class under `data/questtypes/`. Adding a new type = adding a new class + entry in the type registry.

### YAML schema (`quests.yml`)

```yaml
daily:
  midnight_rider:
    name: "Midnight Rider"
    description: "Ride a pig a hundred meters through the night. Weirdly specific. Weirdly satisfying."
    icon: SADDLE
    type: stat
    stat: PIG_ONE_CM
    target: 10000          # 100m (stat unit is cm)
    weight: 1.0            # random-selection weight (default 1.0)
    featured-eligible: true
    featured-target: 500000  # server goal; falls back to target * 100 if omitted
    rewards:
      xp: 100
      points: 50
      items:
        - DIAMOND 2

  streaker:
    name: "Streaker"
    description: "Survive a solid five hours without wearing any armor. Scandalous."
    icon: LEATHER_CHESTPLATE
    type: naked_streak
    target: 18000          # seconds (= 5h)
    rewards:
      xp: 500
      points: 250

  honey_hermit:
    name: "Honey Hermit"
    description: "For one whole Minecraft day, eat only honey. Nothing else touches your lips."
    icon: HONEY_BOTTLE
    type: diet_streak
    target: 1200           # seconds (= 1 MC day)
    diet:                  # custom field consumed by diet_streak type
      - HONEY_BOTTLE
      - HONEYCOMB
    rewards:
      xp: 800
      points: 400

  beast_whisperer:
    name: "Beast Whisperer"
    description: "Tame a wild animal. Earn its trust."
    icon: BONE
    type: event_counter
    event: tame_entity
    target: 1
    rewards:
      xp: 200

weekly:
  master_miner:
    name: "Master Miner"
    description: "Mine blocks of any kind across the week."
    icon: DIAMOND_PICKAXE
    type: stat
    stat: MINE_BLOCK
    target: 5000
    featured-target: 500000
    rewards:
      xp: 1000
      points: 500
      items:
        - DIAMOND 8
  # ... 8-10 weeklies
# ... 15-20 dailies total
```

### Reset semantics for streak-based types

All quests are **reset-only, never fail**. A streak reset (e.g. putting on armor during `naked_streak`) sets the progress back to 0 but the quest remains active and can be re-attempted until the period ends.

## Rewards

### Reward components (all optional per quest)

- `xp`: vanilla Minecraft experience (via `Player.giveExp`)
- `points`: PlayerPoints currency (via `PlayerPointsAPI.give(uuid, amount)`; skipped if PlayerPoints not installed)
- `items`: list of `"MATERIAL AMOUNT"` strings, parsed into ItemStacks

### Item syntax

`"MATERIAL AMOUNT"` — e.g. `"DIAMOND 2"`. v1 does not support enchantments or item metadata (YAGNI).

### PlayerPoints integration

- PlayerPoints declared as `softdepend` in `plugin.yml`
- Maven dependency scope `provided` (resolved via local jar or external repo — to be finalized in the implementation plan)
- `PlayerPointsHook` wraps the API: at startup, checks `Bukkit.getPluginManager().getPlugin("PlayerPoints")`; if present, resolves and caches the API instance. All `give()` calls flow through the hook, which is a silent no-op when the plugin is absent.

### Delivery

- **Personal quests**: reward paid immediately on completion. XP via `giveExp`, items via `player.getInventory().addItem()` (overflow drops at feet), points via hook.
- **Featured quests**: on completion, iterate `featured_contributions` rows with `contribution >= 1`. Online contributors are paid immediately; offline contributors get rows inserted into `pending_rewards` and are paid on next login.

## GUI: Quests View

6×9 chest inventory, two tabs: Daily / Weekly. Shared layout:

| Row | Slot(s) | Content |
|-----|---------|---------|
| 0 | 0 | Back button (BARRIER) — returns to PlayerListGui |
| 0 | 3, 5 | Tab toggles: Daily / Weekly. Active tab is `ENCHANTED_GOLDEN_APPLE`, inactive `GOLDEN_APPLE` |
| 0 | 8 | Rerolls remaining indicator (ENDER_PEARL) — shows `N/2` with hint "Hold Q on a personal quest to reroll" |
| 1 | 9–17 | Featured Quest banner. Slot 13 is the quest (NETHER_STAR), slots 9-12 and 14-17 are black glass panes as frame |
| 2 | 10, 12, 14, 16 | Personal Quest slots (up to the configured count; spare slots filled with gray glass panes) |
| 3 | all | Spacer (gray glass panes) |
| 4 | 40 | "Your Contribution to Featured" — PLAYER_HEAD of viewer showing contribution count |
| 5 | 49 | Timer (CLOCK) — live countdown to next reset (updates on GUI refresh tick) |

### Featured banner lore

```
<gold>FEATURED QUEST

<italic><gray>Mine blocks of any kind across the week.

<gray>Server Progress: <white>6420 / 500000 blocks
<gray>[████░░░░░░] 1%

<gray>Contributors: <white>12 players
<gray>Ends in: <white>05d 11h 23m

<gray>Your contribution: <white>142
```

### Personal quest slot lore

```
<green>Streaker

<italic><gray>Survive a solid five hours without
<italic><gray>wearing any armor. Scandalous.

<gray>Progress: <white>01h 32m / 05h 00m
<gray>[███░░░░░░░] 30%

<gray>Rewards:
<gray>  <aqua>XP: <white>500
<gray>  <yellow>Points: <white>250

<dark_gray>Hold Q to reroll  (2 rerolls left)
```

### Reroll UX: Hold-Q animation

Mouse-wheel scroll events are not available in custom chest GUIs (Minecraft only sends them for creative-mode container interactions). Q-key (drop) is the only "held-button" input Bukkit can observe in an inventory: pressing and holding Q over a slot fires repeated `InventoryClickEvent` with `ClickType.DROP` every ~50ms.

**Flow:**
1. Player holds Q over a personal quest slot.
2. First DROP event: start charge animation. Async task runs at 2-tick intervals, each tick increments charge by ~2.5% (full charge at 2 seconds of continuous holding).
3. Each subsequent DROP event extends a "still-holding" timestamp. If the task sees no DROP events for 150ms, it cancels and reverts.
4. Item lore updates live:
   ```
   <red>🔁 Rerolling...
   [▰▰▰▰▰░░░░░] 50%
   <dark_gray>Release Q to cancel
   ```
5. On 100% charge: quest is rerolled (new random daily/weekly quest excluding current 3 personal + Featured), reroll budget decremented, GUI re-rendered.
6. On cancel (no DROP events for 150ms, or other click, or inventory close): revert slot, charge discarded (no budget used).

Blocked when `rerolls_used >= 2` — lore shows `<red>No rerolls left today` and no animation starts.

### Tab switching

Clicking Daily/Weekly tab re-renders the GUI with the other category. Reroll animations in progress are cancelled on tab switch.

### Notifications (outside GUI)

- **Personal quest completed**: chat message `<green>[Quest] Completed: Midnight Rider! +100 XP, +50 Points, +2 Diamond` + `ENTITY_PLAYER_LEVELUP` sound
- **Featured quest completed**: server-wide broadcast `<gold>[Quest] The server completed Master Miner! Contributors earned their rewards.` + `UI_TOAST_CHALLENGE_COMPLETE` sound for contributors
- **Pending reward delivered on login**: chat message `<yellow>[Quest] You received a pending reward from <Featured Quest Name>: +500 XP, +250 Points`

## Data Model (SQLite)

Database file: `plugins/Beacolanders/quests.db`. All writes run on async scheduler threads.

### Schema

```sql
CREATE TABLE personal_quests (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid   TEXT NOT NULL,
    quest_key     TEXT NOT NULL,
    category      TEXT NOT NULL,        -- 'DAILY' or 'WEEKLY'
    period_start  INTEGER NOT NULL,     -- epoch ms
    period_end    INTEGER NOT NULL,
    snapshot_data TEXT,                 -- JSON, QuestType-specific
    progress      INTEGER NOT NULL DEFAULT 0,
    completed_at  INTEGER,
    rewards_paid  INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_personal_player_period
    ON personal_quests(player_uuid, period_start);

CREATE TABLE featured_quests (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    category        TEXT NOT NULL,
    quest_key       TEXT NOT NULL,
    period_start    INTEGER NOT NULL,
    period_end      INTEGER NOT NULL,
    global_progress INTEGER NOT NULL DEFAULT 0,
    completed_at    INTEGER
);
CREATE UNIQUE INDEX idx_featured_period
    ON featured_quests(category, period_start);

CREATE TABLE featured_contributions (
    featured_id   INTEGER NOT NULL,
    player_uuid   TEXT NOT NULL,
    snapshot_data TEXT,
    contribution  INTEGER NOT NULL DEFAULT 0,
    reward_paid   INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (featured_id, player_uuid)
);

CREATE TABLE reroll_budget (
    player_uuid  TEXT NOT NULL,
    day_start    INTEGER NOT NULL,      -- epoch ms of midnight
    rerolls_used INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (player_uuid, day_start)
);

CREATE TABLE pending_rewards (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid TEXT NOT NULL,
    reward_json TEXT NOT NULL,          -- XP, points, items
    source      TEXT,                   -- e.g. 'featured:master_miner:2026-04-16'
    created_at  INTEGER NOT NULL
);
```

### Why JSON snapshot_data

Each QuestType stores its own shape: `stat` needs `{baseline: 1234}`, `naked_streak` needs `{timer_seconds: 92, last_equip_ts: 1718...}`, etc. Flexible without schema migrations when new types are added.

### Rewards_paid flag

Prevents duplicate payout if the server crashes after completion detection but before reward delivery. Rewards only paid when `completed_at IS NOT NULL AND rewards_paid = 0`, and the flag is flipped atomically after delivery.

## Components

```
data/
  QuestDefinition.java        # record: key, name, desc, icon, typeId, target, weight, featured-*, rewards, typeConfig
  QuestCategory.java          # enum: DAILY, WEEKLY
  QuestReward.java            # record: xp, points, items
  QuestProgress.java          # record: current, target, completed
  ActiveQuest.java            # record: questKey, periodStart, periodEnd, snapshotData, progress, completedAt
  QuestManager.java           # loads quests.yml, holds pool by category
  QuestService.java           # core: assign/reroll/complete/reset + featured logic
  QuestDatabase.java          # SQLite wrapper (async writes via scheduler)
  PlayerPointsHook.java       # soft-dep integration
  questtypes/
    QuestType.java            # interface
    QuestTypeRegistry.java    # typeId -> QuestType
    StatQuestType.java
    EventCounterQuestType.java
    NakedStreakQuestType.java
    DietStreakQuestType.java
    FistKillsQuestType.java
    BiomeTouristQuestType.java
gui/
  QuestsGui.java              # 2 tabs + Featured banner + personal slots + Q-hold animator
commands/
  (BeacolandersCommand extended — quests, quest reroll, quest reset, quest info)
resources/
  quests.yml                  # default quest pool
```

### QuestService responsibilities

- `assignPersonalQuests(Player, QuestCategory)` — on first GUI-open or login without active quests
- `rollFeatured(QuestCategory, periodStart)` — idempotent; date-seeded
- `reroll(Player, ActiveQuest)` — validates budget, picks new random excluding current set
- `tickProgress()` — periodic task (every 20s) that evaluates all online players' active quests, detects completions, pays rewards
- `onReset(QuestCategory)` — finalizes expired featured (queue pending rewards for offline contributors, pay online), marks personal as expired, rolls new featured, clears reroll budgets for new day

## Commands

| Command | Permission | Effect |
|---------|-----------|--------|
| `/bl quests` | `beacolanders.quests` (default: true) | Opens QuestsGui for the caller |
| `/bl quest reroll <player\|all>` | `beacolanders.quests.admin` (default: op) | Forces regeneration of personal quests for one or all players |
| `/bl quest reset <daily\|weekly>` | `beacolanders.quests.admin` | Forces immediate reset of the specified category (incl. Featured re-roll) |
| `/bl quest info <questKey>` | `beacolanders.quests.admin` | Debug output: player progress distribution, current Featured progress |

Tab completion for all arguments via Commodore.

## Configuration (`config.yml` additions)

```yaml
quests:
  daily-count: 3           # personal daily quests per player
  weekly-count: 2          # personal weekly quests per player
  rerolls-per-day: 2       # combined across daily and weekly
  reset-hour: 0            # hour of day (server time) for daily reset
  weekly-reset-day: MONDAY # day of week for weekly reset
```

## Testing Strategy

### Unit tests (MockBukkit)

| Component | Tests |
|-----------|-------|
| `QuestManager` | loads quests.yml, skips invalid entries, parses `stat` and `event_counter` trigger types and special types |
| `QuestReward` | parses `"MATERIAL AMOUNT"` syntax, rejects unknown materials |
| `QuestDatabase` | CRUD for all tables, idempotent schema creation |
| `QuestService.assignPersonal` | snapshot correctly set, count == config, no dupe with Featured |
| `QuestService.rollFeatured` | date-seeded deterministic, `featured-eligible: false` skipped |
| `QuestService.reroll` | budget decremented, fails at 0, new quest ≠ old quest |
| `QuestService.onReset` | expired marking, Featured finalization, pending rewards queued |
| `StatQuestType` | progress = current − snapshot, with optional Material |
| `NakedStreakQuestType` | timer advances; armor event resets to 0 |
| `DietStreakQuestType` | whitelisted foods advance timer; forbidden food resets |
| `FistKillsQuestType` | counts only empty-main-hand kills |
| `BiomeTouristQuestType` | counts unique biomes, ignores duplicates |
| `PlayerPointsHook` | no-op when plugin absent; forwards when present |

### GUI tests (MockBukkit)

| Test | Checks |
|------|--------|
| `opensWithTwoTabs` | Daily + Weekly tab slots populated |
| `switchTabRerenders` | clicking Weekly tab shows weekly quests |
| `qHoldAnimation_charges` | repeated `DROP` clicks increase charge state |
| `qHoldAnimation_cancelsOnRelease` | no DROP events for 150ms → task cancels |
| `qHoldAnimation_completesAtFullCharge` | 2-second hold → new quest assigned |
| `rerollBlocked_whenBudgetZero` | no animation starts when `rerolls_used >= 2` |
| `featuredSlot_rendersGlobalProgress` | banner shows `global_progress / featured-target` |

### E2E tests (McTestFramework)

| Scenario | Flow |
|----------|------|
| `commandOpensGui` | `/bl quests` → chest inventory with title "Quests" opens |
| `statQuest_progressIncreases` | bot mines 5 stone → GUI reflects 5/X |
| `personalQuest_completion_grantsReward` | bot completes a stat quest → XP + items in inventory |
| `adminReset_clearsQuests` | op runs `/bl quest reset daily` → bot sees new quests |
| `pendingReward_deliveredOnLogin` | Featured completes while bot offline → on login: chat message + items |

PlayerPoints is mocked for tests (no live plugin in E2E).

## Out of scope for v1

- Quest chains (completing quest A unlocks quest B)
- Conditional quests (e.g. "kill a skeleton while wielding a sword")
- Item rewards with enchantments or metadata
- Per-player or per-world reset timezones
- Quest history UI (historic data is kept in DB but no view yet)
- Scroll-wheel charge (technically not capturable in survival containers)
- Economy plugins other than PlayerPoints
