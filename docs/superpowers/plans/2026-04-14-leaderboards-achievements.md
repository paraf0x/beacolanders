# Leaderboards & Achievements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add per-stat leaderboards (Top 10 + "You") and config-based 3-tier achievements to the Beacolanders plugin, accessible via GUI buttons and chat commands.

**Architecture:** A `LeaderboardService` collects and caches stat rankings from `OfflinePlayer.getStatistic()`. An `AchievementManager` loads tier definitions from `achievements.yml` and computes progress on-the-fly. Three new GUI screens (LeaderboardSelector, Leaderboard, Achievements) extend the existing `GuiHolder`. The `BeacolandersCommand` gains subcommand routing (`top`, `achievements`). Two new buttons in the PlayerListGui nav bar link to the new features.

**Tech Stack:** Java 21, Paper API 1.21, MockBukkit + JUnit 5 for tests.

**Build/Test Commands:**
- Build: `make build`
- Test: `make test`
- Verify: `make verify`
- Deploy: `make deploy`
- E2E: `make e2e`

**Checkstyle:** Max 120 char lines, max 80 line methods, max 500 line files, max 7 params, no star imports, no tabs, @Override required.

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `src/main/resources/config.yml` | Modify | Add leaderboard cache TTL |
| `src/main/resources/achievements.yml` | Create | Default achievement definitions |
| `src/main/java/.../data/StatCategory.java` | Create | Enum mapping Statistic → display name, icon, formatter |
| `src/main/java/.../data/LeaderboardEntry.java` | Create | Record for ranked player entry |
| `src/main/java/.../data/LeaderboardService.java` | Create | Async stat collection + caching |
| `src/main/java/.../data/AchievementDefinition.java` | Create | Record for a single achievement config |
| `src/main/java/.../data/AchievementManager.java` | Create | Config loading + tier computation |
| `src/main/java/.../gui/LeaderboardSelectorGui.java` | Create | Stat category picker screen |
| `src/main/java/.../gui/LeaderboardGui.java` | Create | Top 10 + "You" display |
| `src/main/java/.../gui/AchievementsGui.java` | Create | Achievement grid with progress |
| `src/main/java/.../gui/PlayerListGui.java` | Modify | Add leaderboard + achievements buttons |
| `src/main/java/.../commands/BeacolandersCommand.java` | Modify | Subcommand routing + tab completion |
| `src/main/java/.../Beacolanders.java` | Modify | Init LeaderboardService + AchievementManager |
| `src/test/.../data/LeaderboardServiceTest.java` | Create | Tests for ranking + caching |
| `src/test/.../data/AchievementManagerTest.java` | Create | Tests for config loading + tier logic |
| `src/test/.../gui/LeaderboardGuiTest.java` | Create | Tests for leaderboard GUI layout |
| `src/test/.../gui/AchievementsGuiTest.java` | Create | Tests for achievement GUI layout |
| `src/test/.../commands/BeacolandersCommandTest.java` | Modify | Tests for subcommand routing |
| `e2e/tests/player-gui.test.ts` | Modify | E2E tests for new features |

All paths under `src/main/java/` use the package `ua/favn/beacolanders/`.

---

## Task 1: StatCategory Enum + LeaderboardEntry Record

**Files:**
- Create: `src/main/java/ua/favn/beacolanders/data/StatCategory.java`
- Create: `src/main/java/ua/favn/beacolanders/data/LeaderboardEntry.java`

- [ ] **Step 1: Create StatCategory enum**

Create `src/main/java/ua/favn/beacolanders/data/StatCategory.java`:

```java
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

    /**
     * Find a StatCategory by lowercase keyword (e.g. "playtime", "kills").
     * Returns null if no match.
     */
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
```

- [ ] **Step 2: Create LeaderboardEntry record**

Create `src/main/java/ua/favn/beacolanders/data/LeaderboardEntry.java`:

```java
package ua.favn.beacolanders.data;

import java.util.UUID;

public record LeaderboardEntry(UUID player, String name, long value, int rank) {
}
```

- [ ] **Step 3: Verify build**

Run: `make build`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/ua/favn/beacolanders/data/StatCategory.java \
  src/main/java/ua/favn/beacolanders/data/LeaderboardEntry.java
git commit -m "Add StatCategory enum and LeaderboardEntry record"
```

---

## Task 2: LeaderboardService (async collection + caching)

**Files:**
- Create: `src/main/java/ua/favn/beacolanders/data/LeaderboardService.java`
- Create: `src/test/java/ua/favn/beacolanders/data/LeaderboardServiceTest.java`

- [ ] **Step 1: Write failing tests**

Create `src/test/java/ua/favn/beacolanders/data/LeaderboardServiceTest.java`:

```java
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

        List<LeaderboardEntry> top = service.getTop(
            StatCategory.DEATHS, 10);

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

        List<LeaderboardEntry> top = service.getTop(
            StatCategory.JUMPS, 10);

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

        LeaderboardEntry entry = service.getRank(
            StatCategory.DEATHS, alice);

        assertEquals("Alice", entry.name());
        assertEquals(10, entry.value());
        assertEquals(2, entry.rank());
    }

    @Test
    void getTop_emptyServer_returnsEmpty() {
        List<LeaderboardEntry> top = service.getTop(
            StatCategory.DEATHS, 10);
        assertTrue(top.isEmpty());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `make test`
Expected: FAIL — `LeaderboardService` not found

- [ ] **Step 3: Implement LeaderboardService**

Create `src/main/java/ua/favn/beacolanders/data/LeaderboardService.java`:

```java
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

    public LeaderboardEntry getRank(StatCategory category,
                                    OfflinePlayer player) {
        List<LeaderboardEntry> all = getRanked(category);
        for (LeaderboardEntry entry : all) {
            if (entry.player().equals(player.getUniqueId())) {
                return entry;
            }
        }
        String name = player.getName() != null
            ? player.getName() : "Unknown";
        return new LeaderboardEntry(
            player.getUniqueId(), name, 0, all.size() + 1);
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

    private List<LeaderboardEntry> collectStats(
        StatCategory category
    ) {
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
                // stat needs sub-type, skip
            }
        }

        raw.sort(Comparator.comparingLong(RawEntry::value).reversed());

        List<LeaderboardEntry> entries = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            RawEntry r = raw.get(i);
            entries.add(new LeaderboardEntry(
                r.player.getUniqueId(), r.player.getName(),
                r.value, i + 1));
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `make test`
Expected: All LeaderboardServiceTest tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ua/favn/beacolanders/data/LeaderboardService.java \
  src/test/java/ua/favn/beacolanders/data/LeaderboardServiceTest.java
git commit -m "Add LeaderboardService with async stat collection and caching"
```

---

## Task 3: AchievementDefinition + AchievementManager

**Files:**
- Create: `src/main/resources/achievements.yml`
- Create: `src/main/java/ua/favn/beacolanders/data/AchievementDefinition.java`
- Create: `src/main/java/ua/favn/beacolanders/data/AchievementManager.java`
- Create: `src/test/java/ua/favn/beacolanders/data/AchievementManagerTest.java`

- [ ] **Step 1: Create achievements.yml**

Create `src/main/resources/achievements.yml`:

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
      1: 10000000
      2: 100000000
      3: 1000000000

  warrior:
    name: "Warrior"
    icon: DIAMOND_SWORD
    stat: MOB_KILLS
    tiers:
      1: 100
      2: 1000
      3: 10000

  hunter:
    name: "Hunter"
    icon: BOW
    stat: PLAYER_KILLS
    tiers:
      1: 10
      2: 100
      3: 1000

  angler:
    name: "Angler"
    icon: FISHING_ROD
    stat: FISH_CAUGHT
    tiers:
      1: 50
      2: 500
      3: 5000

  survivor:
    name: "Survivor"
    icon: TOTEM_OF_UNDYING
    stat: PLAY_ONE_MINUTE
    tiers:
      1: 1440000
      2: 14400000
      3: 144000000

  aviator:
    name: "Aviator"
    icon: ELYTRA
    stat: FLY_ONE_CM
    tiers:
      1: 10000000
      2: 100000000
      3: 1000000000

  builder:
    name: "Builder"
    icon: CRAFTING_TABLE
    stat: CRAFT_ITEM
    tiers:
      1: 500
      2: 5000
      3: 50000
```

- [ ] **Step 2: Create AchievementDefinition record**

Create `src/main/java/ua/favn/beacolanders/data/AchievementDefinition.java`:

```java
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
```

- [ ] **Step 3: Write failing tests for AchievementManager**

Create `src/test/java/ua/favn/beacolanders/data/AchievementManagerTest.java`:

```java
package ua.favn.beacolanders.data;

import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AchievementManagerTest {

    private static final String YAML = """
        achievements:
          miner:
            name: "Miner"
            icon: DIAMOND_PICKAXE
            stat: MINE_BLOCK
            tiers:
              1: 1000
              2: 10000
              3: 100000
          warrior:
            name: "Warrior"
            icon: DIAMOND_SWORD
            stat: MOB_KILLS
            tiers:
              1: 100
              2: 1000
              3: 10000
        """;

    @Test
    void load_parsesAchievementsFromYaml() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(
            new StringReader(YAML));
        AchievementManager manager = new AchievementManager();
        manager.load(config);

        List<AchievementDefinition> defs = manager.getAll();
        assertEquals(2, defs.size());
    }

    @Test
    void load_parsesFieldsCorrectly() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(
            new StringReader(YAML));
        AchievementManager manager = new AchievementManager();
        manager.load(config);

        AchievementDefinition miner = manager.get("miner");
        assertNotNull(miner);
        assertEquals("Miner", miner.name());
        assertEquals(Material.DIAMOND_PICKAXE, miner.icon());
        assertEquals(Statistic.MINE_BLOCK, miner.stat());
        assertEquals(1000, miner.tier1());
        assertEquals(10000, miner.tier2());
        assertEquals(100000, miner.tier3());
    }

    @Test
    void getTier_computesCorrectly() {
        AchievementDefinition def = new AchievementDefinition(
            "test", "Test", Material.STONE, Statistic.JUMP,
            100, 1000, 10000);

        assertEquals(0, def.getTier(0));
        assertEquals(0, def.getTier(99));
        assertEquals(1, def.getTier(100));
        assertEquals(1, def.getTier(999));
        assertEquals(2, def.getTier(1000));
        assertEquals(2, def.getTier(9999));
        assertEquals(3, def.getTier(10000));
        assertEquals(3, def.getTier(99999));
    }

    @Test
    void load_skipsInvalidStat() {
        String yaml = """
            achievements:
              broken:
                name: "Broken"
                icon: STONE
                stat: NOT_A_REAL_STAT
                tiers:
                  1: 100
                  2: 200
                  3: 300
            """;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(
            new StringReader(yaml));
        AchievementManager manager = new AchievementManager();
        manager.load(config);

        assertTrue(manager.getAll().isEmpty());
    }
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `make test`
Expected: FAIL — `AchievementManager` not found

- [ ] **Step 5: Implement AchievementManager**

Create `src/main/java/ua/favn/beacolanders/data/AchievementManager.java`:

```java
package ua.favn.beacolanders.data;

import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public final class AchievementManager {

    private static final Logger LOGGER =
        Logger.getLogger(AchievementManager.class.getName());

    private final Map<String, AchievementDefinition> achievements =
        new LinkedHashMap<>();

    public void load(YamlConfiguration config) {
        achievements.clear();
        ConfigurationSection section =
            config.getConfigurationSection("achievements");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }

            String name = entry.getString("name", key);
            Material icon = Material.matchMaterial(
                entry.getString("icon", "STONE"));
            if (icon == null) {
                icon = Material.STONE;
            }

            Statistic stat;
            try {
                stat = Statistic.valueOf(entry.getString("stat", ""));
            } catch (IllegalArgumentException e) {
                LOGGER.warning("Invalid stat for achievement '"
                    + key + "': " + entry.getString("stat"));
                continue;
            }

            ConfigurationSection tiers =
                entry.getConfigurationSection("tiers");
            if (tiers == null) {
                continue;
            }

            long tier1 = tiers.getLong("1", 0);
            long tier2 = tiers.getLong("2", 0);
            long tier3 = tiers.getLong("3", 0);

            achievements.put(key, new AchievementDefinition(
                key, name, icon, stat, tier1, tier2, tier3));
        }
    }

    public List<AchievementDefinition> getAll() {
        return new ArrayList<>(achievements.values());
    }

    public AchievementDefinition get(String key) {
        return achievements.get(key);
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `make test`
Expected: All AchievementManagerTest tests PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/achievements.yml \
  src/main/java/ua/favn/beacolanders/data/AchievementDefinition.java \
  src/main/java/ua/favn/beacolanders/data/AchievementManager.java \
  src/test/java/ua/favn/beacolanders/data/AchievementManagerTest.java
git commit -m "Add AchievementManager with config loading and tier computation"
```

---

## Task 4: LeaderboardSelectorGui + LeaderboardGui

**Files:**
- Create: `src/main/java/ua/favn/beacolanders/gui/LeaderboardSelectorGui.java`
- Create: `src/main/java/ua/favn/beacolanders/gui/LeaderboardGui.java`
- Create: `src/test/java/ua/favn/beacolanders/gui/LeaderboardGuiTest.java`

- [ ] **Step 1: Write failing tests**

Create `src/test/java/ua/favn/beacolanders/gui/LeaderboardGuiTest.java`:

```java
package ua.favn.beacolanders.gui;

import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import ua.favn.beacolanders.Beacolanders;
import ua.favn.beacolanders.data.StatCategory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LeaderboardGuiTest {

    private ServerMock server;
    private Beacolanders plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Beacolanders.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void selectorGui_showsAllStatCategories() {
        PlayerMock viewer = server.addPlayer("Viewer");
        LeaderboardSelectorGui gui =
            new LeaderboardSelectorGui(plugin, viewer);
        Inventory inv = gui.getInventory();

        // Back button at slot 0
        assertNotNull(inv.getItem(0));
        assertEquals(Material.BARRIER, inv.getItem(0).getType());

        // 10 stat items starting at slot 9
        assertNotNull(inv.getItem(9));
        assertEquals(Material.CLOCK, inv.getItem(9).getType());
    }

    @Test
    void leaderboardGui_showsBackButton() {
        PlayerMock viewer = server.addPlayer("Viewer");
        LeaderboardGui gui = new LeaderboardGui(
            plugin, viewer, StatCategory.DEATHS);
        Inventory inv = gui.getInventory();

        assertNotNull(inv.getItem(0));
        assertEquals(Material.BARRIER, inv.getItem(0).getType());
    }

    @Test
    void leaderboardGui_showsStatIcon() {
        PlayerMock viewer = server.addPlayer("Viewer");
        LeaderboardGui gui = new LeaderboardGui(
            plugin, viewer, StatCategory.DEATHS);
        Inventory inv = gui.getInventory();

        // Stat icon at slot 4
        assertNotNull(inv.getItem(4));
        assertEquals(Material.SKELETON_SKULL, inv.getItem(4).getType());
    }

    @Test
    void leaderboardGui_showsViewerHead() {
        PlayerMock viewer = server.addPlayer("Viewer");
        viewer.setStatistic(Statistic.DEATHS, 42);

        LeaderboardGui gui = new LeaderboardGui(
            plugin, viewer, StatCategory.DEATHS);
        Inventory inv = gui.getInventory();

        // "You" slot at row 6, slot 4 = index 49
        assertNotNull(inv.getItem(49));
        assertEquals(Material.PLAYER_HEAD, inv.getItem(49).getType());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `make test`
Expected: FAIL — classes not found

- [ ] **Step 3: Implement LeaderboardSelectorGui**

Create `src/main/java/ua/favn/beacolanders/gui/LeaderboardSelectorGui.java`:

```java
package ua.favn.beacolanders.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ua.favn.beacolanders.Beacolanders;
import ua.favn.beacolanders.data.StatCategory;

public final class LeaderboardSelectorGui extends GuiHolder {

    private static final int BACK_SLOT = 0;
    private static final int TITLE_SLOT = 4;
    private static final int ITEMS_START = ROW_SIZE;

    private final Beacolanders plugin;
    private final Player viewer;

    public LeaderboardSelectorGui(Beacolanders plugin, Player viewer) {
        super("<dark_gray>Leaderboards");
        this.plugin = plugin;
        this.viewer = viewer;
        render();
    }

    @Override
    public void onClick(int slot, Player player) {
        if (slot == BACK_SLOT) {
            PlayerListGui list = new PlayerListGui(plugin);
            player.openInventory(list.getInventory());
            return;
        }

        int index = slot - ITEMS_START;
        StatCategory[] cats = StatCategory.values();
        if (index >= 0 && index < cats.length) {
            LeaderboardGui gui = new LeaderboardGui(
                plugin, player, cats[index]);
            player.openInventory(gui.getInventory());
        }
    }

    private void render() {
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(mm("<red>Back to Player List"));
        back.setItemMeta(backMeta);
        inventory.setItem(BACK_SLOT, back);

        ItemStack title = new ItemStack(Material.GOLD_INGOT);
        ItemMeta titleMeta = title.getItemMeta();
        titleMeta.displayName(mm("<gold>Leaderboards"));
        title.setItemMeta(titleMeta);
        inventory.setItem(TITLE_SLOT, title);

        StatCategory[] cats = StatCategory.values();
        for (int i = 0; i < cats.length; i++) {
            StatCategory cat = cats[i];
            ItemStack item = new ItemStack(cat.icon());
            ItemMeta meta = item.getItemMeta();
            meta.displayName(mm("<yellow>" + cat.displayName()));
            item.setItemMeta(meta);
            inventory.setItem(ITEMS_START + i, item);
        }
    }
}
```

- [ ] **Step 4: Implement LeaderboardGui**

Create `src/main/java/ua/favn/beacolanders/gui/LeaderboardGui.java`:

```java
package ua.favn.beacolanders.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import ua.favn.beacolanders.Beacolanders;
import ua.favn.beacolanders.data.LeaderboardEntry;
import ua.favn.beacolanders.data.LeaderboardService;
import ua.favn.beacolanders.data.StatCategory;

import java.util.ArrayList;
import java.util.List;

public final class LeaderboardGui extends GuiHolder {

    private static final int BACK_SLOT = 0;
    private static final int TITLE_SLOT = 4;
    private static final int TOP_START = ROW_SIZE;
    private static final int YOU_SLOT = ROW_SIZE * 5 + 4;

    private final Beacolanders plugin;
    private final Player viewer;
    private final StatCategory category;

    public LeaderboardGui(Beacolanders plugin, Player viewer,
                          StatCategory category) {
        super("<dark_gray>" + category.displayName() + " Leaderboard");
        this.plugin = plugin;
        this.viewer = viewer;
        this.category = category;
        render();
    }

    @Override
    public void onClick(int slot, Player player) {
        if (slot == BACK_SLOT) {
            LeaderboardSelectorGui selector =
                new LeaderboardSelectorGui(plugin, player);
            player.openInventory(selector.getInventory());
        }
    }

    private void render() {
        renderNavBar();
        renderTop10();
        renderYou();
    }

    private void renderNavBar() {
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(mm("<red>Back to Leaderboards"));
        back.setItemMeta(backMeta);
        inventory.setItem(BACK_SLOT, back);

        ItemStack title = new ItemStack(category.icon());
        ItemMeta titleMeta = title.getItemMeta();
        titleMeta.displayName(
            mm("<gold>" + category.displayName()));
        title.setItemMeta(titleMeta);
        inventory.setItem(TITLE_SLOT, title);
    }

    private void renderTop10() {
        LeaderboardService service = plugin.getLeaderboardService();
        List<LeaderboardEntry> top = service.getTop(category, 10);

        for (int i = 0; i < top.size(); i++) {
            LeaderboardEntry entry = top.get(i);
            ItemStack head = buildRankedHead(entry, i == 0);
            inventory.setItem(TOP_START + i, head);
        }
    }

    private void renderYou() {
        LeaderboardService service = plugin.getLeaderboardService();
        LeaderboardEntry self = service.getRank(category, viewer);

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(viewer);
        meta.displayName(mm("<white>You"));
        List<Component> lore = new ArrayList<>();
        lore.add(mm("<gray>Rank: <gold>#" + self.rank()));
        lore.add(mm("<gray>" + category.displayName() + ": <white>"
            + category.format(self.value())));
        meta.lore(lore);
        head.setItemMeta(meta);
        inventory.setItem(YOU_SLOT, head);
    }

    private ItemStack buildRankedHead(LeaderboardEntry entry,
                                      boolean isFirst) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(
            plugin.getServer().getOfflinePlayer(entry.player()));
        meta.displayName(mm("<gold>#" + entry.rank()
            + " <white>" + entry.name()));
        List<Component> lore = new ArrayList<>();
        lore.add(mm("<gray>" + category.displayName() + ": <white>"
            + category.format(entry.value())));
        meta.lore(lore);
        if (isFirst) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        head.setItemMeta(meta);
        return head;
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `make test`
Expected: LeaderboardGuiTest tests PASS (some may need `getLeaderboardService()` stub — add it in step 6 if needed)

- [ ] **Step 6: Add getLeaderboardService() to Beacolanders.java if tests fail**

If tests fail because `getLeaderboardService()` doesn't exist, add a temporary stub to `Beacolanders.java`:

```java
private LeaderboardService leaderboardService;

public LeaderboardService getLeaderboardService() {
    if (leaderboardService == null) {
        leaderboardService = new LeaderboardService(60);
    }
    return leaderboardService;
}
```

Also add the import: `import ua.favn.beacolanders.data.LeaderboardService;`

- [ ] **Step 7: Run tests again**

Run: `make test`
Expected: All tests PASS

- [ ] **Step 8: Commit**

```bash
git add src/main/java/ua/favn/beacolanders/gui/LeaderboardSelectorGui.java \
  src/main/java/ua/favn/beacolanders/gui/LeaderboardGui.java \
  src/main/java/ua/favn/beacolanders/Beacolanders.java \
  src/test/java/ua/favn/beacolanders/gui/LeaderboardGuiTest.java
git commit -m "Add LeaderboardSelectorGui and LeaderboardGui"
```

---

## Task 5: AchievementsGui

**Files:**
- Create: `src/main/java/ua/favn/beacolanders/gui/AchievementsGui.java`
- Create: `src/test/java/ua/favn/beacolanders/gui/AchievementsGuiTest.java`

- [ ] **Step 1: Write failing tests**

Create `src/test/java/ua/favn/beacolanders/gui/AchievementsGuiTest.java`:

```java
package ua.favn.beacolanders.gui;

import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import ua.favn.beacolanders.Beacolanders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AchievementsGuiTest {

    private ServerMock server;
    private Beacolanders plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Beacolanders.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void constructor_showsBackButton() {
        PlayerMock viewer = server.addPlayer("Viewer");
        AchievementsGui gui = new AchievementsGui(
            plugin, viewer, viewer);
        Inventory inv = gui.getInventory();

        assertNotNull(inv.getItem(0));
        assertEquals(Material.BARRIER, inv.getItem(0).getType());
    }

    @Test
    void constructor_showsAchievementItems() {
        PlayerMock viewer = server.addPlayer("Viewer");
        AchievementsGui gui = new AchievementsGui(
            plugin, viewer, viewer);
        Inventory inv = gui.getInventory();

        // At least one achievement item starting at slot 9
        assertNotNull(inv.getItem(9));
    }

    @Test
    void tier0_showsGreyPane() {
        PlayerMock viewer = server.addPlayer("Viewer");
        // No stats set → all at tier 0
        AchievementsGui gui = new AchievementsGui(
            plugin, viewer, viewer);
        Inventory inv = gui.getInventory();

        // First achievement (miner) should be grey pane (tier 0)
        assertEquals(Material.GRAY_STAINED_GLASS_PANE,
            inv.getItem(9).getType());
    }

    @Test
    void achievedTier_showsIconWithStackCount() {
        PlayerMock viewer = server.addPlayer("Viewer");
        // Set MINE_BLOCK to 5000 → miner tier 1 (threshold 1000)
        viewer.setStatistic(Statistic.MINE_BLOCK, 5000);

        AchievementsGui gui = new AchievementsGui(
            plugin, viewer, viewer);
        Inventory inv = gui.getInventory();

        // First achievement = miner = diamond pickaxe with count 1
        assertEquals(Material.DIAMOND_PICKAXE,
            inv.getItem(9).getType());
        assertEquals(1, inv.getItem(9).getAmount());
    }

    @Test
    void tier2_showsStackCount2() {
        PlayerMock viewer = server.addPlayer("Viewer");
        viewer.setStatistic(Statistic.MINE_BLOCK, 15000);

        AchievementsGui gui = new AchievementsGui(
            plugin, viewer, viewer);
        Inventory inv = gui.getInventory();

        assertEquals(Material.DIAMOND_PICKAXE,
            inv.getItem(9).getType());
        assertEquals(2, inv.getItem(9).getAmount());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `make test`
Expected: FAIL — `AchievementsGui` not found

- [ ] **Step 3: Implement AchievementsGui**

Create `src/main/java/ua/favn/beacolanders/gui/AchievementsGui.java`:

```java
package ua.favn.beacolanders.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ua.favn.beacolanders.Beacolanders;
import ua.favn.beacolanders.data.AchievementDefinition;
import ua.favn.beacolanders.data.AchievementManager;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AchievementsGui extends GuiHolder {

    private static final int BACK_SLOT = 0;
    private static final int TITLE_SLOT = 4;
    private static final int ITEMS_START = ROW_SIZE;
    private static final int ITEMS_PER_PAGE = 28;
    private static final int PREV_SLOT = ROW_SIZE * 5;
    private static final int PAGE_SLOT = ROW_SIZE * 5 + 4;
    private static final int NEXT_SLOT = ROW_SIZE * 5 + 8;
    private static final NumberFormat NUM_FMT =
        NumberFormat.getIntegerInstance(Locale.US);
    private static final String[] TIER_LABELS =
        {"", "I", "II", "III"};

    private final Beacolanders plugin;
    private final Player viewer;
    private final OfflinePlayer target;
    private int page;

    public AchievementsGui(Beacolanders plugin, Player viewer,
                           OfflinePlayer target) {
        super("<dark_gray>Achievements");
        this.plugin = plugin;
        this.viewer = viewer;
        this.target = target;
        this.page = 0;
        render();
    }

    @Override
    public void onClick(int slot, Player player) {
        if (slot == BACK_SLOT) {
            PlayerListGui list = new PlayerListGui(plugin);
            player.openInventory(list.getInventory());
            return;
        }
        if (slot == PREV_SLOT && page > 0) {
            page--;
            render();
        } else if (slot == NEXT_SLOT && hasNextPage()) {
            page++;
            render();
        }
    }

    private void render() {
        inventory.clear();
        renderNavBar();
        renderAchievements();
        renderPagination();
    }

    private void renderNavBar() {
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(mm("<red>Back to Player List"));
        back.setItemMeta(backMeta);
        inventory.setItem(BACK_SLOT, back);

        ItemStack title = new ItemStack(Material.EMERALD);
        ItemMeta titleMeta = title.getItemMeta();
        titleMeta.displayName(mm("<green>Achievements"));
        title.setItemMeta(titleMeta);
        inventory.setItem(TITLE_SLOT, title);
    }

    private void renderAchievements() {
        AchievementManager manager = plugin.getAchievementManager();
        List<AchievementDefinition> defs = manager.getAll();

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, defs.size());

        for (int i = start; i < end; i++) {
            AchievementDefinition def = defs.get(i);
            long value = getStatValue(def);
            int tier = def.getTier(value);

            ItemStack item = buildAchievementItem(def, value, tier);
            inventory.setItem(ITEMS_START + (i - start), item);
        }
    }

    private ItemStack buildAchievementItem(AchievementDefinition def,
                                           long value, int tier) {
        if (tier == 0) {
            ItemStack pane =
                new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = pane.getItemMeta();
            meta.displayName(mm("<gray>" + def.name()));
            meta.lore(buildLore(def, value, tier));
            pane.setItemMeta(meta);
            return pane;
        }

        ItemStack item = new ItemStack(def.icon(), tier);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(mm("<green>" + def.name()
            + " " + TIER_LABELS[tier]));
        meta.lore(buildLore(def, value, tier));
        item.setItemMeta(meta);
        return item;
    }

    private List<Component> buildLore(AchievementDefinition def,
                                      long value, int tier) {
        List<Component> lore = new ArrayList<>();
        long nextThreshold = def.getNextThreshold(tier);
        long displayTarget = tier < 3 ? nextThreshold
            : def.tier3();

        lore.add(mm("<gray>" + NUM_FMT.format(value)
            + " / " + NUM_FMT.format(displayTarget)));

        lore.add(mm("<gray>" + buildProgressBar(value,
            displayTarget)));

        lore.add(mm("<gray>Tier 1: "
            + NUM_FMT.format(def.tier1())
            + (value >= def.tier1() ? " <green>✔" : "")));
        lore.add(mm("<gray>Tier 2: "
            + NUM_FMT.format(def.tier2())
            + (value >= def.tier2() ? " <green>✔" : "")));
        lore.add(mm("<gray>Tier 3: "
            + NUM_FMT.format(def.tier3())
            + (value >= def.tier3() ? " <green>✔" : "")));

        return lore;
    }

    private String buildProgressBar(long value, long target) {
        double ratio = target > 0
            ? Math.min(1.0, (double) value / target) : 0;
        int filled = (int) (ratio * 10);
        int empty = 10 - filled;
        int pct = (int) (ratio * 100);
        return "[" + "█".repeat(filled) + "░".repeat(empty)
            + "] " + pct + "%";
    }

    private long getStatValue(AchievementDefinition def) {
        try {
            return target.getStatistic(def.stat());
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    private boolean hasNextPage() {
        AchievementManager manager = plugin.getAchievementManager();
        return (page + 1) * ITEMS_PER_PAGE
            < manager.getAll().size();
    }

    private void renderPagination() {
        AchievementManager manager = plugin.getAchievementManager();
        int totalPages = Math.max(1, (int) Math.ceil(
            (double) manager.getAll().size() / ITEMS_PER_PAGE));

        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta m = prev.getItemMeta();
            m.displayName(mm("<gold>Previous Page"));
            prev.setItemMeta(m);
            inventory.setItem(PREV_SLOT, prev);
        }

        ItemStack indicator = new ItemStack(Material.PAPER);
        ItemMeta im = indicator.getItemMeta();
        im.displayName(mm("<gray>Page " + (page + 1)
            + "/" + totalPages));
        indicator.setItemMeta(im);
        inventory.setItem(PAGE_SLOT, indicator);

        if (hasNextPage()) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta m = next.getItemMeta();
            m.displayName(mm("<gold>Next Page"));
            next.setItemMeta(m);
            inventory.setItem(NEXT_SLOT, next);
        }
    }
}
```

- [ ] **Step 4: Add getAchievementManager() to Beacolanders.java**

Add to `Beacolanders.java`:

```java
private AchievementManager achievementManager;

public AchievementManager getAchievementManager() {
    return achievementManager;
}
```

In `onEnable()`, after `saveDefaultConfig()`:

```java
saveResource("achievements.yml", false);
initAchievements();
```

Add method:

```java
private void initAchievements() {
    achievementManager = new AchievementManager();
    File achievementsFile = new File(getDataFolder(), "achievements.yml");
    YamlConfiguration config = YamlConfiguration.loadConfiguration(achievementsFile);
    achievementManager.load(config);
    getLogger().info("Loaded " + achievementManager.getAll().size() + " achievements.");
}
```

Add imports: `import ua.favn.beacolanders.data.AchievementManager;`, `import org.bukkit.configuration.file.YamlConfiguration;`

- [ ] **Step 5: Run tests**

Run: `make test`
Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/ua/favn/beacolanders/gui/AchievementsGui.java \
  src/main/java/ua/favn/beacolanders/Beacolanders.java \
  src/test/java/ua/favn/beacolanders/gui/AchievementsGuiTest.java
git commit -m "Add AchievementsGui with progress bars and tier display"
```

---

## Task 6: Update BeacolandersCommand (subcommand routing + tab completion)

**Files:**
- Modify: `src/main/java/ua/favn/beacolanders/commands/BeacolandersCommand.java`
- Modify: `src/test/java/ua/favn/beacolanders/commands/BeacolandersCommandTest.java`

- [ ] **Step 1: Write failing tests**

Replace `src/test/java/ua/favn/beacolanders/commands/BeacolandersCommandTest.java`:

```java
package ua.favn.beacolanders.commands;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import ua.favn.beacolanders.Beacolanders;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BeacolandersCommandTest {

    private ServerMock server;
    private Beacolanders plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Beacolanders.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void noArgs_opensPlayerList() {
        PlayerMock player = server.addPlayer("Steve");
        BeacolandersCommand cmd = new BeacolandersCommand(plugin);
        assertTrue(cmd.onCommand(player, null, "bl", new String[]{}));
    }

    @Test
    void top_opensLeaderboardSelector() {
        PlayerMock player = server.addPlayer("Steve");
        BeacolandersCommand cmd = new BeacolandersCommand(plugin);
        assertTrue(cmd.onCommand(
            player, null, "bl", new String[]{"top"}));
    }

    @Test
    void topWithStat_opensLeaderboard() {
        PlayerMock player = server.addPlayer("Steve");
        BeacolandersCommand cmd = new BeacolandersCommand(plugin);
        assertTrue(cmd.onCommand(
            player, null, "bl", new String[]{"top", "deaths"}));
    }

    @Test
    void achievements_opensAchievementsGui() {
        PlayerMock player = server.addPlayer("Steve");
        BeacolandersCommand cmd = new BeacolandersCommand(plugin);
        assertTrue(cmd.onCommand(
            player, null, "bl", new String[]{"achievements"}));
    }
}
```

- [ ] **Step 2: Run tests to verify new tests fail**

Run: `make test`
Expected: New tests FAIL (subcommand routing not implemented)

- [ ] **Step 3: Implement subcommand routing**

Replace `src/main/java/ua/favn/beacolanders/commands/BeacolandersCommand.java`:

```java
package ua.favn.beacolanders.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ua.favn.beacolanders.Beacolanders;
import ua.favn.beacolanders.data.StatCategory;
import ua.favn.beacolanders.gui.AchievementsGui;
import ua.favn.beacolanders.gui.LeaderboardGui;
import ua.favn.beacolanders.gui.LeaderboardSelectorGui;
import ua.favn.beacolanders.gui.PlayerListGui;

import java.util.ArrayList;
import java.util.List;

public final class BeacolandersCommand
    implements CommandExecutor, TabCompleter {

    private final Beacolanders plugin;

    public BeacolandersCommand(Beacolanders plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             Command command,
                             @NotNull String label,
                             String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            player.openInventory(
                new PlayerListGui(plugin).getInventory());
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "top" -> handleTop(player, args);
            case "achievements" -> handleAchievements(player, args);
            default -> player.openInventory(
                new PlayerListGui(plugin).getInventory());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String alias,
                                      String @NotNull [] args) {
        if (args.length == 1) {
            return filterStartsWith(
                List.of("top", "achievements"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("top")) {
            List<String> stats = new ArrayList<>();
            for (StatCategory cat : StatCategory.values()) {
                stats.add(cat.displayName().toLowerCase()
                    .replace(" ", ""));
            }
            return filterStartsWith(stats, args[1]);
        }
        if (args.length == 2
            && args[0].equalsIgnoreCase("achievements")) {
            return filterStartsWith(
                Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName).toList(),
                args[1]);
        }
        return List.of();
    }

    private void handleTop(Player player, String[] args) {
        if (args.length < 2) {
            player.openInventory(new LeaderboardSelectorGui(
                plugin, player).getInventory());
            return;
        }

        StatCategory cat = StatCategory.fromKeyword(args[1]);
        if (cat == null) {
            player.sendMessage("Unknown stat: " + args[1]);
            return;
        }
        player.openInventory(new LeaderboardGui(
            plugin, player, cat).getInventory());
    }

    private void handleAchievements(Player player, String[] args) {
        if (args.length >= 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                player.sendMessage("Player not found: " + args[1]);
                return;
            }
            player.openInventory(new AchievementsGui(
                plugin, player, target).getInventory());
        } else {
            player.openInventory(new AchievementsGui(
                plugin, player, player).getInventory());
        }
    }

    private static List<String> filterStartsWith(
        List<String> options, String prefix
    ) {
        String lower = prefix.toLowerCase();
        return options.stream()
            .filter(s -> s.toLowerCase().startsWith(lower))
            .toList();
    }
}
```

- [ ] **Step 4: Register tab completer in Beacolanders.java**

Update `registerCommand()` in `Beacolanders.java`:

```java
private void registerCommand() {
    var command = getCommand("beacolanders");
    if (command != null) {
        BeacolandersCommand executor = new BeacolandersCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
```

- [ ] **Step 5: Run tests**

Run: `make test`
Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/ua/favn/beacolanders/commands/BeacolandersCommand.java \
  src/main/java/ua/favn/beacolanders/Beacolanders.java \
  src/test/java/ua/favn/beacolanders/commands/BeacolandersCommandTest.java
git commit -m "Add subcommand routing: /bl top and /bl achievements"
```

---

## Task 7: Update PlayerListGui (add nav buttons)

**Files:**
- Modify: `src/main/java/ua/favn/beacolanders/gui/PlayerListGui.java`
- Modify: `src/test/java/ua/favn/beacolanders/gui/PlayerListGuiTest.java`

- [ ] **Step 1: Add test for new buttons**

Add to `PlayerListGuiTest.java`:

```java
@Test
void constructor_showsLeaderboardButton() {
    PlayerListGui gui = new PlayerListGui(plugin);
    Inventory inv = gui.getInventory();
    assertNotNull(inv.getItem(2));
    assertEquals(Material.GOLD_INGOT, inv.getItem(2).getType());
}

@Test
void constructor_showsAchievementsButton() {
    PlayerListGui gui = new PlayerListGui(plugin);
    Inventory inv = gui.getInventory();
    assertNotNull(inv.getItem(6));
    assertEquals(Material.EMERALD, inv.getItem(6).getType());
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `make test`
Expected: New tests FAIL

- [ ] **Step 3: Add buttons to PlayerListGui**

In `PlayerListGui.java`, add constants:

```java
private static final int LEADERBOARD_SLOT = 2;
private static final int ACHIEVEMENTS_SLOT = 6;
```

In `renderNavBar()`, add after the title item:

```java
ItemStack lb = new ItemStack(Material.GOLD_INGOT);
ItemMeta lbMeta = lb.getItemMeta();
lbMeta.displayName(mm("<gold>Leaderboards"));
lb.setItemMeta(lbMeta);
inventory.setItem(LEADERBOARD_SLOT, lb);

ItemStack ach = new ItemStack(Material.EMERALD);
ItemMeta achMeta = ach.getItemMeta();
achMeta.displayName(mm("<green>Achievements"));
ach.setItemMeta(achMeta);
inventory.setItem(ACHIEVEMENTS_SLOT, ach);
```

In `onClick()`, add before the player index check:

```java
if (slot == LEADERBOARD_SLOT) {
    LeaderboardSelectorGui selector =
        new LeaderboardSelectorGui(plugin, player);
    player.openInventory(selector.getInventory());
    return;
}
if (slot == ACHIEVEMENTS_SLOT) {
    AchievementsGui achievements =
        new AchievementsGui(plugin, player, player);
    player.openInventory(achievements.getInventory());
    return;
}
```

Add imports for `LeaderboardSelectorGui` and `AchievementsGui`.

- [ ] **Step 4: Run tests**

Run: `make test`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ua/favn/beacolanders/gui/PlayerListGui.java \
  src/test/java/ua/favn/beacolanders/gui/PlayerListGuiTest.java
git commit -m "Add leaderboard and achievements buttons to player list"
```

---

## Task 8: Wire Everything + Config Update

**Files:**
- Modify: `src/main/java/ua/favn/beacolanders/Beacolanders.java`
- Modify: `src/main/resources/config.yml`

- [ ] **Step 1: Update config.yml**

Append to `src/main/resources/config.yml`:

```yaml

# Leaderboard settings
leaderboard:
  cache-ttl-seconds: 60
```

- [ ] **Step 2: Wire LeaderboardService properly in Beacolanders.java**

Update `onEnable()` to initialize `LeaderboardService` from config:

```java
int ttl = getConfig().getInt("leaderboard.cache-ttl-seconds", 60);
leaderboardService = new LeaderboardService(ttl);
```

This replaces the lazy-init stub from Task 4.

- [ ] **Step 3: Run full verify**

Run: `make verify`
Expected: BUILD SUCCESS — all tests pass, checkstyle and spotbugs clean

- [ ] **Step 4: Commit**

```bash
git add src/main/java/ua/favn/beacolanders/Beacolanders.java \
  src/main/resources/config.yml
git commit -m "Wire LeaderboardService and AchievementManager in main plugin"
```

---

## Task 9: E2E Tests

**Files:**
- Modify: `e2e/tests/player-gui.test.ts`

- [ ] **Step 1: Add leaderboard E2E tests**

Add to `e2e/tests/player-gui.test.ts` after the existing Navigation section:

```typescript
// ─── Leaderboards ────────────────────────────────────────

it('TC-70: /bl top opens leaderboard selector', async () => {
    tc('TC-70');
    bot.chat('/bl top');
    const win = await bot.nextWindow(10_000);

    checkContains('Title', win.title, 'Leaderboards');
    check('Back button', 'minecraft:barrier', win.slot(1, 1).item);
    // First stat category (clock = playtime) at slot 9
    check('First stat is clock', 'minecraft:clock', win.slotAt(9).item);
});

it('TC-71: clicking stat opens top 10 leaderboard', async () => {
    tc('TC-71');
    bot.chat('/bl top');
    const selector = await bot.nextWindow(10_000);

    // Click first stat (playtime/clock at slot 9)
    const lb = await selector.clickSlot(9);
    checkContains('Title has stat name', lb.title, 'Leaderboard');
    check('Back button', 'minecraft:barrier', lb.slot(1, 1).item);
    // "You" head at row 6 slot 5 = index 49
    check('You head', 'minecraft:player_head', lb.slotAt(49).item);
});

it('TC-72: /bl top deaths opens deaths leaderboard', async () => {
    tc('TC-72');
    bot.chat('/bl top deaths');
    const win = await bot.nextWindow(10_000);

    checkContains('Title has Deaths', win.title, 'Deaths');
    check('Stat icon is skull', 'minecraft:skeleton_skull',
        win.slot(5, 1).item);
});

it('TC-73: gold ingot in player list opens leaderboards', async () => {
    tc('TC-73');
    bot.chat('/bl');
    const list = await bot.nextWindow(10_000);

    check('Leaderboard button', 'minecraft:gold_ingot',
        list.slotAt(2).item);
    const selector = await list.clickSlot(2);
    checkContains('Leaderboard selector', selector.title,
        'Leaderboards');
});
```

- [ ] **Step 2: Add achievement E2E tests**

```typescript
// ─── Achievements ────────────────────────────────────────

it('TC-80: /bl achievements opens achievements GUI', async () => {
    tc('TC-80');
    bot.chat('/bl achievements');
    const win = await bot.nextWindow(10_000);

    checkContains('Title', win.title, 'Achievements');
    check('Back button', 'minecraft:barrier', win.slot(1, 1).item);
    // At least 1 achievement item at slot 9
    assertNotNull(win.slotAt(9).item);
});

it('TC-81: emerald in player list opens achievements', async () => {
    tc('TC-81');
    bot.chat('/bl');
    const list = await bot.nextWindow(10_000);

    check('Achievements button', 'minecraft:emerald',
        list.slotAt(6).item);
    const ach = await list.clickSlot(6);
    checkContains('Achievements GUI', ach.title, 'Achievements');
});

it('TC-82: tier 0 shows grey pane', async () => {
    tc('TC-82');
    bot.chat('/bl achievements');
    const win = await bot.nextWindow(10_000);

    // New bot with no stats → first achievement is grey pane
    // (unless bot has been playing, which it has minimally)
    const firstItem = win.slotAt(9);
    checkDefined('First achievement exists', firstItem.item);
});
```

- [ ] **Step 3: Deploy and run E2E**

Run: `make e2e`
Expected: All tests PASS

- [ ] **Step 4: Fix any failures and re-run**

- [ ] **Step 5: Commit**

```bash
git add e2e/tests/player-gui.test.ts
git commit -m "Add E2E tests for leaderboards and achievements"
```
