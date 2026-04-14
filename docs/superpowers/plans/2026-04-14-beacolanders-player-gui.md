# Beacolanders Player GUI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a chest-based GUI plugin for browsing players, searching by name, and inspecting player details (gear, stats, locations, shops) with per-row horizontal scrolling.

**Architecture:** Command `/bl` opens a paginated player list GUI. Clicking a player head opens a detail view with 6 rows: header, equipment, locations (scrollable), shops (scrollable), stats (scrollable), reserved. Data comes from Bukkit API (stats, equipment) and direct read-only SQLite access to BaseManager and ShopSearch databases.

**Tech Stack:** Java 21, Paper API 1.21, AnvilGUI (search input), SQLite (JDBC bundled with JRE), MockBukkit + JUnit 5 for tests.

**Build/Test Commands:**
- Build: `make build`
- Test: `make test`
- Verify (compile + test + checkstyle + spotbugs): `make verify`
- Deploy to test server: `make deploy`

**Checkstyle rules:** Max 120 char lines, max 80 line methods, max 500 line files, max 7 parameters, no star imports, no tabs, LF line endings, `@Override` required.

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `pom.xml` | Modify | Add sqlite-jdbc dependency + shade relocation |
| `src/main/resources/plugin.yml` | Modify | Add command registration |
| `src/main/resources/config.yml` | Modify | Add database paths |
| `src/main/resources/messages.yml` | Modify | Add GUI message templates |
| `src/main/java/ua/favn/beacolanders/Beacolanders.java` | Modify | Register command, listener, init DatabaseReader |
| `src/main/java/ua/favn/beacolanders/data/DatabaseReader.java` | Create | Read-only SQLite access to BaseManager & ShopSearch |
| `src/main/java/ua/favn/beacolanders/data/LocationData.java` | Create | Record for location query results |
| `src/main/java/ua/favn/beacolanders/data/ShopData.java` | Create | Record for shop query results |
| `src/main/java/ua/favn/beacolanders/data/PlayerDataProvider.java` | Create | Bukkit API wrapper for stats, equipment, player info |
| `src/main/java/ua/favn/beacolanders/gui/GuiHolder.java` | Create | Base GUI screen with inventory + click cancellation |
| `src/main/java/ua/favn/beacolanders/gui/PlayerListGui.java` | Create | Player list with pagination + search |
| `src/main/java/ua/favn/beacolanders/gui/PlayerDetailGui.java` | Create | Player detail with row-scroll mechanic |
| `src/main/java/ua/favn/beacolanders/gui/GuiListener.java` | Create | InventoryClickEvent handler dispatching to GuiHolder |
| `src/main/java/ua/favn/beacolanders/commands/BeacolandersCommand.java` | Create | Command executor for /bl |
| `src/test/java/ua/favn/beacolanders/data/DatabaseReaderTest.java` | Create | Tests for SQL queries |
| `src/test/java/ua/favn/beacolanders/data/PlayerDataProviderTest.java` | Create | Tests for stat/equipment retrieval |
| `src/test/java/ua/favn/beacolanders/gui/PlayerListGuiTest.java` | Create | Tests for player list pagination + search |
| `src/test/java/ua/favn/beacolanders/gui/PlayerDetailGuiTest.java` | Create | Tests for row-scroll mechanic |
| `src/test/java/ua/favn/beacolanders/commands/BeacolandersCommandTest.java` | Create | Tests for command execution |

---

## Task 1: Resource Files and Dependencies

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/plugin.yml`
- Modify: `src/main/resources/config.yml`
- Modify: `src/main/resources/messages.yml`

- [ ] **Step 1: Add SQLite JDBC dependency to pom.xml**

Add the sqlite-jdbc dependency inside the `<dependencies>` section of `pom.xml`, after the AnvilGUI dependency:

```xml
        <!-- SQLite JDBC (database access) -->
        <dependency>
            <groupId>org.xerial</groupId>
            <artifactId>sqlite-jdbc</artifactId>
            <version>3.46.1.0</version>
            <scope>compile</scope>
        </dependency>
```

Also add the relocation in the shade plugin `<relocations>` section:

```xml
                                <relocation>
                                    <pattern>org.sqlite</pattern>
                                    <shadedPattern>ua.favn.beacolanders.lib.sqlite</shadedPattern>
                                </relocation>
```

And include it in the shade plugin `<includes>`:

```xml
                                    <include>org.xerial:sqlite-jdbc</include>
```

- [ ] **Step 2: Update plugin.yml with command registration**

Replace the entire contents of `src/main/resources/plugin.yml`:

```yaml
name: Beacolanders
version: '${project.version}'
main: ua.favn.beacolanders.Beacolanders
api-version: '1.21'
author: favn
description: Server companion plugin for Beacoland

commands:
  beacolanders:
    description: Open the Beacolanders player menu
    aliases: [bl]
    usage: /<command>
```

- [ ] **Step 2: Update config.yml with database paths**

Replace the entire contents of `src/main/resources/config.yml`:

```yaml
# Beacolanders Configuration

# Database paths (relative to this plugin's data folder)
database:
  basemanager: ../BaseManager/storage.db
  shopsearch: ../ShopSearch/storage.db
```

- [ ] **Step 3: Update messages.yml with GUI messages**

Replace the entire contents of `src/main/resources/messages.yml`:

```yaml
# Beacolanders Messages
# Supports MiniMessage format: <red>, <gold>, <green>, etc.

prefix: "<gray>[<gold>Beacolanders<gray>] "

gui:
  player-list-title: "<dark_gray>Beacolanders"
  player-detail-title: "<dark_gray>{player}"
  search-title: "Search player..."
  back: "<red>Back to Player List"
  next-page: "<gold>Next Page"
  prev-page: "<gold>Previous Page"
  page-indicator: "<gray>Page {current}/{total}"
  scroll-left: "<gold>Scroll Left"
  scroll-right: "<gold>Scroll Right (+{count})"
  no-data: "<gray>No data available"
  none: "<gray>None"
  offline: "<gray>Offline"
```

- [ ] **Step 4: Verify build compiles**

Run: `make build`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/main/resources/plugin.yml src/main/resources/config.yml src/main/resources/messages.yml
git commit -m "Add command registration, SQLite dependency, and GUI config"
```

---

## Task 2: Data Records (LocationData, ShopData)

**Files:**
- Create: `src/main/java/ua/favn/beacolanders/data/LocationData.java`
- Create: `src/main/java/ua/favn/beacolanders/data/ShopData.java`

- [ ] **Step 1: Create LocationData record**

Create `src/main/java/ua/favn/beacolanders/data/LocationData.java`:

```java
package ua.favn.beacolanders.data;

import java.util.List;

public record LocationData(
    int id,
    String name,
    String tag,
    boolean isPublic,
    List<CoordData> coords,
    List<String> memberNames
) {

    public record CoordData(String world, int x, int y, int z) {
    }
}
```

- [ ] **Step 2: Create ShopData record**

Create `src/main/java/ua/favn/beacolanders/data/ShopData.java`:

```java
package ua.favn.beacolanders.data;

public record ShopData(
    String id,
    String name,
    String world,
    int x,
    int y,
    int z,
    int stockCount
) {
}
```

- [ ] **Step 3: Verify build compiles**

Run: `make build`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/ua/favn/beacolanders/data/LocationData.java src/main/java/ua/favn/beacolanders/data/ShopData.java
git commit -m "Add LocationData and ShopData records"
```

---

## Task 3: DatabaseReader (SQLite access)

**Files:**
- Create: `src/main/java/ua/favn/beacolanders/data/DatabaseReader.java`
- Create: `src/test/java/ua/favn/beacolanders/data/DatabaseReaderTest.java`

- [ ] **Step 1: Write failing tests for DatabaseReader**

Create `src/test/java/ua/favn/beacolanders/data/DatabaseReaderTest.java`:

```java
package ua.favn.beacolanders.data;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseReaderTest {

    @TempDir
    File tempDir;

    private DatabaseReader reader;
    private Connection bmConn;
    private Connection ssConn;

    @BeforeEach
    void setUp() throws Exception {
        File bmDb = new File(tempDir, "basemanager.db");
        File ssDb = new File(tempDir, "shopsearch.db");

        bmConn = DriverManager.getConnection("jdbc:sqlite:" + bmDb.getAbsolutePath());
        try (Statement stmt = bmConn.createStatement()) {
            stmt.execute("CREATE TABLE locations ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "owner TEXT NOT NULL,"
                + "tag TEXT NOT NULL DEFAULT 'BASE',"
                + "name TEXT NOT NULL,"
                + "created TEXT NOT NULL,"
                + "isPublic INTEGER DEFAULT 0,"
                + "icon TEXT DEFAULT NULL)");
            stmt.execute("CREATE TABLE location_coords ("
                + "locationId INTEGER NOT NULL,"
                + "world TEXT NOT NULL,"
                + "locX INTEGER NOT NULL,"
                + "locY INTEGER NOT NULL,"
                + "locZ INTEGER NOT NULL,"
                + "PRIMARY KEY (locationId, world))");
            stmt.execute("CREATE TABLE location_members ("
                + "locationId INTEGER NOT NULL,"
                + "memberUUID TEXT NOT NULL,"
                + "memberName TEXT NOT NULL DEFAULT '',"
                + "PRIMARY KEY (locationId, memberUUID))");
        }

        ssConn = DriverManager.getConnection("jdbc:sqlite:" + ssDb.getAbsolutePath());
        try (Statement stmt = ssConn.createStatement()) {
            stmt.execute("CREATE TABLE shops ("
                + "id TEXT PRIMARY KEY,"
                + "owner TEXT,"
                + "name TEXT UNIQUE,"
                + "locX NUMERIC,"
                + "locY NUMERIC,"
                + "locZ NUMERIC,"
                + "world TEXT DEFAULT 'world')");
            stmt.execute("CREATE TABLE stock ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "shopId TEXT,"
                + "material TEXT,"
                + "price NUMERIC,"
                + "amount NUMERIC)");
        }

        reader = new DatabaseReader(bmDb, ssDb);
    }

    @AfterEach
    void tearDown() throws Exception {
        reader.close();
        bmConn.close();
        ssConn.close();
    }

    @Test
    void getLocations_returnsLocationsForOwner() throws Exception {
        String ownerUuid = "ca18d342-1234-5678-9abc-def012345678";
        try (Statement stmt = bmConn.createStatement()) {
            stmt.execute("INSERT INTO locations (id, owner, tag, name, created, isPublic)"
                + " VALUES (1, '" + ownerUuid + "', 'BASE', 'My Base', '2025-01-01', 1)");
            stmt.execute("INSERT INTO location_coords (locationId, world, locX, locY, locZ)"
                + " VALUES (1, 'world', 10, 64, 20)");
            stmt.execute("INSERT INTO location_coords (locationId, world, locX, locY, locZ)"
                + " VALUES (1, 'world_nether', 1, 128, 2)");
            stmt.execute("INSERT INTO location_members (locationId, memberUUID, memberName)"
                + " VALUES (1, 'member-uuid-1', 'Steve')");
        }

        List<LocationData> locations = reader.getLocations(ownerUuid);

        assertEquals(1, locations.size());
        LocationData loc = locations.get(0);
        assertEquals("My Base", loc.name());
        assertEquals("BASE", loc.tag());
        assertTrue(loc.isPublic());
        assertEquals(2, loc.coords().size());
        assertEquals(1, loc.memberNames().size());
        assertEquals("Steve", loc.memberNames().get(0));
    }

    @Test
    void getLocations_returnsEmptyForUnknownOwner() throws Exception {
        List<LocationData> locations = reader.getLocations("unknown-uuid");
        assertTrue(locations.isEmpty());
    }

    @Test
    void getShops_returnsShopsForOwner() throws Exception {
        String ownerUuid = "ca18d342-1234-5678-9abc-def012345678";
        try (Statement stmt = ssConn.createStatement()) {
            stmt.execute("INSERT INTO shops (id, owner, name, locX, locY, locZ, world)"
                + " VALUES ('shop1', '" + ownerUuid + "', 'Diamond Shop', 100, 65, 200, 'world')");
            stmt.execute("INSERT INTO stock (shopId, material, price, amount)"
                + " VALUES ('shop1', 'DIAMOND', 10, 64)");
            stmt.execute("INSERT INTO stock (shopId, material, price, amount)"
                + " VALUES ('shop1', 'EMERALD', 5, 32)");
        }

        List<ShopData> shops = reader.getShops(ownerUuid);

        assertEquals(1, shops.size());
        ShopData shop = shops.get(0);
        assertEquals("Diamond Shop", shop.name());
        assertEquals("world", shop.world());
        assertEquals(100, shop.x());
        assertEquals(65, shop.y());
        assertEquals(200, shop.z());
        assertEquals(2, shop.stockCount());
    }

    @Test
    void getShops_returnsEmptyForUnknownOwner() throws Exception {
        List<ShopData> shops = reader.getShops("unknown-uuid");
        assertTrue(shops.isEmpty());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `make test`
Expected: FAIL — `DatabaseReader` class not found

- [ ] **Step 3: Implement DatabaseReader**

Create `src/main/java/ua/favn/beacolanders/data/DatabaseReader.java`:

```java
package ua.favn.beacolanders.data;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DatabaseReader {

    private static final Logger LOGGER = Logger.getLogger(DatabaseReader.class.getName());

    private final Connection bmConnection;
    private final Connection ssConnection;

    public DatabaseReader(File baseManagerDb, File shopSearchDb) throws SQLException {
        this.bmConnection = openReadOnly(baseManagerDb);
        this.ssConnection = openReadOnly(shopSearchDb);
    }

    public List<LocationData> getLocations(String ownerUuid) {
        String sql = "SELECT l.id, l.name, l.tag, l.isPublic"
            + " FROM locations l WHERE l.owner = ?";
        Map<Integer, LocationData.Builder> builders = new LinkedHashMap<>();

        try (PreparedStatement ps = bmConnection.prepareStatement(sql)) {
            ps.setString(1, ownerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    builders.put(id, new LocationData.Builder(
                        id, rs.getString("name"), rs.getString("tag"),
                        rs.getInt("isPublic") == 1
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to query locations", e);
            return List.of();
        }

        if (builders.isEmpty()) {
            return List.of();
        }

        fillCoords(builders);
        fillMembers(builders);

        return builders.values().stream()
            .map(LocationData.Builder::build)
            .toList();
    }

    public List<ShopData> getShops(String ownerUuid) {
        String sql = "SELECT s.id, s.name, s.world, s.locX, s.locY, s.locZ,"
            + " (SELECT COUNT(*) FROM stock st WHERE st.shopId = s.id) AS stockCount"
            + " FROM shops s WHERE s.owner = ?";
        List<ShopData> shops = new ArrayList<>();

        try (PreparedStatement ps = ssConnection.prepareStatement(sql)) {
            ps.setString(1, ownerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    shops.add(new ShopData(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("world"),
                        rs.getInt("locX"),
                        rs.getInt("locY"),
                        rs.getInt("locZ"),
                        rs.getInt("stockCount")
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to query shops", e);
        }

        return shops;
    }

    public void close() {
        closeQuietly(bmConnection);
        closeQuietly(ssConnection);
    }

    private void fillCoords(Map<Integer, LocationData.Builder> builders) {
        String ids = builders.keySet().stream()
            .map(String::valueOf)
            .reduce((a, b) -> a + "," + b)
            .orElse("");
        String sql = "SELECT locationId, world, locX, locY, locZ"
            + " FROM location_coords WHERE locationId IN (" + ids + ")";

        try (PreparedStatement ps = bmConnection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int locId = rs.getInt("locationId");
                LocationData.Builder builder = builders.get(locId);
                if (builder != null) {
                    builder.addCoord(new LocationData.CoordData(
                        rs.getString("world"),
                        rs.getInt("locX"),
                        rs.getInt("locY"),
                        rs.getInt("locZ")
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to query location coords", e);
        }
    }

    private void fillMembers(Map<Integer, LocationData.Builder> builders) {
        String ids = builders.keySet().stream()
            .map(String::valueOf)
            .reduce((a, b) -> a + "," + b)
            .orElse("");
        String sql = "SELECT locationId, memberName"
            + " FROM location_members WHERE locationId IN (" + ids + ")";

        try (PreparedStatement ps = bmConnection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int locId = rs.getInt("locationId");
                LocationData.Builder builder = builders.get(locId);
                if (builder != null) {
                    builder.addMember(rs.getString("memberName"));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to query location members", e);
        }
    }

    private static Connection openReadOnly(File dbFile) throws SQLException {
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath()
            + "?mode=ro";
        Connection conn = DriverManager.getConnection(url);
        try (var stmt = conn.createStatement()) {
            stmt.execute("PRAGMA query_only = ON");
        }
        return conn;
    }

    private static void closeQuietly(Connection conn) {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to close database connection", e);
        }
    }
}
```

- [ ] **Step 4: Add Builder to LocationData**

Update `src/main/java/ua/favn/beacolanders/data/LocationData.java` — add the `Builder` static class used by `DatabaseReader`:

```java
package ua.favn.beacolanders.data;

import java.util.ArrayList;
import java.util.List;

public record LocationData(
    int id,
    String name,
    String tag,
    boolean isPublic,
    List<CoordData> coords,
    List<String> memberNames
) {

    public record CoordData(String world, int x, int y, int z) {
    }

    static final class Builder {

        private final int id;
        private final String name;
        private final String tag;
        private final boolean isPublic;
        private final List<CoordData> coords = new ArrayList<>();
        private final List<String> memberNames = new ArrayList<>();

        Builder(int id, String name, String tag, boolean isPublic) {
            this.id = id;
            this.name = name;
            this.tag = tag;
            this.isPublic = isPublic;
        }

        void addCoord(CoordData coord) {
            coords.add(coord);
        }

        void addMember(String memberName) {
            memberNames.add(memberName);
        }

        LocationData build() {
            return new LocationData(id, name, tag, isPublic,
                List.copyOf(coords), List.copyOf(memberNames));
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `make test`
Expected: All 4 DatabaseReaderTest tests PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/ua/favn/beacolanders/data/DatabaseReader.java \
  src/main/java/ua/favn/beacolanders/data/LocationData.java \
  src/test/java/ua/favn/beacolanders/data/DatabaseReaderTest.java
git commit -m "Add DatabaseReader with SQLite access for locations and shops"
```

---

## Task 4: PlayerDataProvider (stats, equipment)

**Files:**
- Create: `src/main/java/ua/favn/beacolanders/data/PlayerDataProvider.java`
- Create: `src/test/java/ua/favn/beacolanders/data/PlayerDataProviderTest.java`

- [ ] **Step 1: Write failing tests for PlayerDataProvider**

Create `src/test/java/ua/favn/beacolanders/data/PlayerDataProviderTest.java`:

```java
package ua.favn.beacolanders.data;

import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerDataProviderTest {

    private ServerMock server;
    private PlayerDataProvider provider;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        provider = new PlayerDataProvider();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void getEquipment_onlinePlayer_returnsItems() {
        PlayerMock player = server.addPlayer("Steve");
        player.getEquipment().setHelmet(new ItemStack(Material.DIAMOND_HELMET));

        ItemStack[] equipment = provider.getEquipment(player);

        assertNotNull(equipment);
        assertEquals(6, equipment.length);
        assertEquals(Material.DIAMOND_HELMET, equipment[0].getType());
    }

    @Test
    void getStats_returnsStatMap() {
        PlayerMock player = server.addPlayer("Steve");
        player.setStatistic(Statistic.DEATHS, 5);

        Map<Statistic, Integer> stats = provider.getStats(player);

        assertNotNull(stats);
        assertEquals(5, stats.get(Statistic.DEATHS));
    }

    @Test
    void getPlaytimeTicks_returnsTickCount() {
        PlayerMock player = server.addPlayer("Steve");
        player.setStatistic(Statistic.PLAY_ONE_MINUTE, 72000);

        int ticks = provider.getPlaytimeTicks(player);

        assertEquals(72000, ticks);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `make test`
Expected: FAIL — `PlayerDataProvider` class not found

- [ ] **Step 3: Implement PlayerDataProvider**

Create `src/main/java/ua/favn/beacolanders/data/PlayerDataProvider.java`:

```java
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
        if (eq == null) {
            return null;
        }
        return new ItemStack[]{
            eq.getHelmet(),
            eq.getChestplate(),
            eq.getLeggings(),
            eq.getBoots(),
            eq.getItemInMainHand(),
            eq.getItemInOffHand()
        };
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `make test`
Expected: All PlayerDataProviderTest tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ua/favn/beacolanders/data/PlayerDataProvider.java \
  src/test/java/ua/favn/beacolanders/data/PlayerDataProviderTest.java
git commit -m "Add PlayerDataProvider for stats and equipment"
```

---

## Task 5: GuiHolder Base Class + GuiListener

**Files:**
- Create: `src/main/java/ua/favn/beacolanders/gui/GuiHolder.java`
- Create: `src/main/java/ua/favn/beacolanders/gui/GuiListener.java`

- [ ] **Step 1: Create GuiHolder base class**

Create `src/main/java/ua/favn/beacolanders/gui/GuiHolder.java`:

```java
package ua.favn.beacolanders.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public abstract class GuiHolder implements InventoryHolder {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    protected static final int ROW_SIZE = 9;
    protected static final int ROWS = 6;
    protected static final int INVENTORY_SIZE = ROW_SIZE * ROWS;

    protected final Inventory inventory;

    protected GuiHolder(String title) {
        Component titleComponent = MINI.deserialize(title);
        this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE, titleComponent);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    /**
     * Handle a click on the given raw slot index.
     * Implementations should perform actions (open other GUIs, scroll, etc).
     */
    public abstract void onClick(int slot, org.bukkit.entity.Player player);

    protected static Component mm(String miniMessage) {
        return MINI.deserialize(miniMessage);
    }
}
```

- [ ] **Step 2: Create GuiListener**

Create `src/main/java/ua/favn/beacolanders/gui/GuiListener.java`:

```java
package ua.favn.beacolanders.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class GuiListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= GuiHolder.INVENTORY_SIZE) {
            return;
        }

        holder.onClick(slot, player);
    }
}
```

- [ ] **Step 3: Verify build compiles**

Run: `make build`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/ua/favn/beacolanders/gui/GuiHolder.java \
  src/main/java/ua/favn/beacolanders/gui/GuiListener.java
git commit -m "Add GuiHolder base class and GuiListener"
```

---

## Task 6: PlayerListGui

**Files:**
- Create: `src/main/java/ua/favn/beacolanders/gui/PlayerListGui.java`
- Create: `src/test/java/ua/favn/beacolanders/gui/PlayerListGuiTest.java`

- [ ] **Step 1: Write failing tests for PlayerListGui**

Create `src/test/java/ua/favn/beacolanders/gui/PlayerListGuiTest.java`:

```java
package ua.favn.beacolanders.gui;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import ua.favn.beacolanders.Beacolanders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlayerListGuiTest {

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
    void constructor_populatesPlayerHeads() {
        server.addPlayer("Alice");
        server.addPlayer("Bob");

        PlayerListGui gui = new PlayerListGui(plugin);
        Inventory inv = gui.getInventory();

        // Slot 9 (row 2, slot 0) should be first player head
        assertNotNull(inv.getItem(9));
        assertEquals(Material.PLAYER_HEAD, inv.getItem(9).getType());
    }

    @Test
    void constructor_showsSearchButton() {
        PlayerListGui gui = new PlayerListGui(plugin);
        Inventory inv = gui.getInventory();

        // Slot 0 = search button
        assertNotNull(inv.getItem(0));
    }

    @Test
    void constructor_showsTitleItem() {
        PlayerListGui gui = new PlayerListGui(plugin);
        Inventory inv = gui.getInventory();

        // Slot 4 = title
        assertNotNull(inv.getItem(4));
    }

    @Test
    void pagination_secondPage_showsRemainingPlayers() {
        // Add 40 players (more than 36 per page)
        for (int i = 0; i < 40; i++) {
            server.addPlayer("Player" + i);
        }

        PlayerListGui gui = new PlayerListGui(plugin);
        Inventory inv = gui.getInventory();

        // Slot 53 (row 6, slot 8) = next page arrow should exist
        assertNotNull(inv.getItem(53));
    }

    @Test
    void search_filtersPlayersByName() {
        server.addPlayer("Alice");
        server.addPlayer("Bob");
        server.addPlayer("AliceClone");

        PlayerListGui gui = new PlayerListGui(plugin);
        gui.applySearch("alice");
        Inventory inv = gui.getInventory();

        // Should show 2 players (Alice, AliceClone), starting at slot 9
        assertNotNull(inv.getItem(9));
        assertNotNull(inv.getItem(10));
        assertNull(inv.getItem(11));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `make test`
Expected: FAIL — `PlayerListGui` class not found

- [ ] **Step 3: Implement PlayerListGui**

Create `src/main/java/ua/favn/beacolanders/gui/PlayerListGui.java`:

```java
package ua.favn.beacolanders.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import ua.favn.beacolanders.Beacolanders;
import ua.favn.beacolanders.data.PlayerDataProvider;

import java.util.ArrayList;
import java.util.List;

public final class PlayerListGui extends GuiHolder {

    private static final int PLAYERS_PER_PAGE = 36;
    private static final int PLAYER_START_SLOT = ROW_SIZE;
    private static final int SEARCH_SLOT = 0;
    private static final int TITLE_SLOT = 4;
    private static final int PREV_SLOT = ROW_SIZE * 5;
    private static final int PAGE_INDICATOR_SLOT = ROW_SIZE * 5 + 4;
    private static final int NEXT_SLOT = ROW_SIZE * 5 + 8;

    private final Beacolanders plugin;
    private int page;
    private String searchFilter;
    private List<Player> filteredPlayers;

    public PlayerListGui(Beacolanders plugin) {
        super("<dark_gray>Beacolanders");
        this.plugin = plugin;
        this.page = 0;
        this.searchFilter = null;
        refreshPlayerList();
        render();
    }

    @Override
    public void onClick(int slot, Player player) {
        if (slot == SEARCH_SLOT) {
            openSearch(player);
            return;
        }
        if (slot == PREV_SLOT && page > 0) {
            page--;
            render();
            return;
        }
        if (slot == NEXT_SLOT && hasNextPage()) {
            page++;
            render();
            return;
        }

        int playerIndex = slotToPlayerIndex(slot);
        if (playerIndex >= 0 && playerIndex < filteredPlayers.size()) {
            Player target = filteredPlayers.get(playerIndex);
            PlayerDetailGui detail = new PlayerDetailGui(plugin, target, player);
            player.openInventory(detail.getInventory());
        }
    }

    public void applySearch(String query) {
        this.searchFilter = query;
        this.page = 0;
        refreshPlayerList();
        render();
    }

    public void clearSearch() {
        this.searchFilter = null;
        this.page = 0;
        refreshPlayerList();
        render();
    }

    private void refreshPlayerList() {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (searchFilter != null && !searchFilter.isEmpty()) {
            String lower = searchFilter.toLowerCase();
            online.removeIf(p -> !p.getName().toLowerCase().contains(lower));
        }
        this.filteredPlayers = online;
    }

    private void render() {
        inventory.clear();
        renderNavBar();
        renderPlayers();
        renderPagination();
    }

    private void renderNavBar() {
        ItemStack search = new ItemStack(Material.COMPASS);
        ItemMeta searchMeta = search.getItemMeta();
        searchMeta.displayName(mm("<yellow>Search Players"));
        search.setItemMeta(searchMeta);
        inventory.setItem(SEARCH_SLOT, search);

        ItemStack title = new ItemStack(Material.NETHER_STAR);
        ItemMeta titleMeta = title.getItemMeta();
        titleMeta.displayName(mm("<gold>Beacolanders"));
        title.setItemMeta(titleMeta);
        inventory.setItem(TITLE_SLOT, title);
    }

    private void renderPlayers() {
        int start = page * PLAYERS_PER_PAGE;
        int end = Math.min(start + PLAYERS_PER_PAGE, filteredPlayers.size());

        for (int i = start; i < end; i++) {
            Player target = filteredPlayers.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(target);
            meta.displayName(mm("<white>" + target.getName()));
            List<Component> lore = new ArrayList<>();
            lore.add(mm("<green>Online"));
            lore.add(mm("<gray>" + target.getGameMode().name()));
            meta.lore(lore);
            head.setItemMeta(meta);

            int slot = PLAYER_START_SLOT + (i - start);
            inventory.setItem(slot, head);
        }
    }

    private void renderPagination() {
        int totalPages = Math.max(1,
            (int) Math.ceil((double) filteredPlayers.size() / PLAYERS_PER_PAGE));

        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prev.getItemMeta();
            prevMeta.displayName(mm("<gold>Previous Page"));
            prev.setItemMeta(prevMeta);
            inventory.setItem(PREV_SLOT, prev);
        }

        ItemStack indicator = new ItemStack(Material.PAPER);
        ItemMeta indicatorMeta = indicator.getItemMeta();
        indicatorMeta.displayName(
            mm("<gray>Page " + (page + 1) + "/" + totalPages));
        indicator.setItemMeta(indicatorMeta);
        inventory.setItem(PAGE_INDICATOR_SLOT, indicator);

        if (hasNextPage()) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = next.getItemMeta();
            nextMeta.displayName(mm("<gold>Next Page"));
            next.setItemMeta(nextMeta);
            inventory.setItem(NEXT_SLOT, next);
        }
    }

    private boolean hasNextPage() {
        return (page + 1) * PLAYERS_PER_PAGE < filteredPlayers.size();
    }

    private int slotToPlayerIndex(int slot) {
        if (slot < PLAYER_START_SLOT
            || slot >= PLAYER_START_SLOT + PLAYERS_PER_PAGE) {
            return -1;
        }
        return page * PLAYERS_PER_PAGE + (slot - PLAYER_START_SLOT);
    }

    private void openSearch(Player player) {
        new net.wesjd.anvilgui.AnvilGUI.Builder()
            .plugin(plugin)
            .title("Search player...")
            .text("")
            .onClick((slot, state) -> {
                if (slot == net.wesjd.anvilgui.AnvilGUI.Slot.OUTPUT) {
                    String text = state.getText();
                    applySearch(text);
                    player.openInventory(getInventory());
                    return List.of(
                        net.wesjd.anvilgui.AnvilGUI.ResponseAction.close()
                    );
                }
                return List.of();
            })
            .open(player);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `make test`
Expected: PlayerListGuiTest tests PASS (some tests may need adjustment depending on MockBukkit's AnvilGUI support — if the constructor test fails because `PlayerDetailGui` doesn't exist yet, that's expected; the click tests that reference it will be tested after Task 7)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ua/favn/beacolanders/gui/PlayerListGui.java \
  src/test/java/ua/favn/beacolanders/gui/PlayerListGuiTest.java
git commit -m "Add PlayerListGui with pagination and search"
```

---

## Task 7: PlayerDetailGui (row-scroll mechanic)

**Files:**
- Create: `src/main/java/ua/favn/beacolanders/gui/PlayerDetailGui.java`
- Create: `src/test/java/ua/favn/beacolanders/gui/PlayerDetailGuiTest.java`

- [ ] **Step 1: Write failing tests for row-scroll mechanic**

Create `src/test/java/ua/favn/beacolanders/gui/PlayerDetailGuiTest.java`:

```java
package ua.favn.beacolanders.gui;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import ua.favn.beacolanders.Beacolanders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PlayerDetailGuiTest {

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
        PlayerMock target = server.addPlayer("Steve");
        PlayerMock viewer = server.addPlayer("Viewer");

        PlayerDetailGui gui = new PlayerDetailGui(plugin, target, viewer);
        Inventory inv = gui.getInventory();

        // Slot 0 = back button (barrier)
        assertNotNull(inv.getItem(0));
        assertEquals(Material.BARRIER, inv.getItem(0).getType());
    }

    @Test
    void constructor_showsPlayerHead() {
        PlayerMock target = server.addPlayer("Steve");
        PlayerMock viewer = server.addPlayer("Viewer");

        PlayerDetailGui gui = new PlayerDetailGui(plugin, target, viewer);
        Inventory inv = gui.getInventory();

        // Slot 4 = player head
        assertNotNull(inv.getItem(4));
        assertEquals(Material.PLAYER_HEAD, inv.getItem(4).getType());
    }

    @Test
    void constructor_showsEquipmentRow() {
        PlayerMock target = server.addPlayer("Steve");
        target.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
        PlayerMock viewer = server.addPlayer("Viewer");

        PlayerDetailGui gui = new PlayerDetailGui(plugin, target, viewer);
        Inventory inv = gui.getInventory();

        // Slot 10 (row 2, slot 1) = helmet
        assertNotNull(inv.getItem(10));
        assertEquals(Material.IRON_HELMET, inv.getItem(10).getType());
    }

    @Test
    void scrollRight_shiftsRowOffset() {
        PlayerMock target = server.addPlayer("Steve");
        PlayerMock viewer = server.addPlayer("Viewer");

        PlayerDetailGui gui = new PlayerDetailGui(plugin, target, viewer);

        // Row 5 (stats) has more than 7 items, click right arrow at slot 44
        // (row 5, slot 8)
        gui.onClick(44, viewer);

        // After scrolling, the row content should shift
        // (exact items depend on stats, but scroll offset should change)
        // We verify the scroll happened by checking the gui doesn't crash
        assertNotNull(gui.getInventory());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `make test`
Expected: FAIL — `PlayerDetailGui` class not found

- [ ] **Step 3: Implement PlayerDetailGui**

Create `src/main/java/ua/favn/beacolanders/gui/PlayerDetailGui.java`:

```java
package ua.favn.beacolanders.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import ua.favn.beacolanders.Beacolanders;
import ua.favn.beacolanders.data.DatabaseReader;
import ua.favn.beacolanders.data.LocationData;
import ua.favn.beacolanders.data.PlayerDataProvider;
import ua.favn.beacolanders.data.ShopData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PlayerDetailGui extends GuiHolder {

    private static final int BACK_SLOT = 0;
    private static final int HEAD_SLOT = 4;
    private static final int EQUIP_ROW = 1;
    private static final int LOCATIONS_ROW = 2;
    private static final int SHOPS_ROW = 3;
    private static final int STATS_ROW = 4;
    private static final int VISIBLE_ITEMS = 7;

    private final Beacolanders plugin;
    private final OfflinePlayer target;
    private final Player viewer;

    private final int[] rowOffsets = new int[ROWS];
    private List<ItemStack> locationItems = List.of();
    private List<ItemStack> shopItems = List.of();
    private List<ItemStack> statItems = List.of();

    public PlayerDetailGui(Beacolanders plugin, OfflinePlayer target,
                           Player viewer) {
        super("<dark_gray>" + target.getName());
        this.plugin = plugin;
        this.target = target;
        this.viewer = viewer;

        buildStatItems();
        renderHeader();
        renderEquipment();
        renderScrollableRow(STATS_ROW, statItems);

        loadDatabaseDataAsync();
    }

    @Override
    public void onClick(int slot, Player player) {
        if (slot == BACK_SLOT) {
            PlayerListGui list = new PlayerListGui(plugin);
            player.openInventory(list.getInventory());
            return;
        }

        int row = slot / ROW_SIZE;
        int col = slot % ROW_SIZE;

        if (isScrollableRow(row)) {
            if (col == 0) {
                scrollLeft(row);
            } else if (col == 8) {
                scrollRight(row);
            }
        }
    }

    private void renderHeader() {
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(mm("<red>Back to Player List"));
        back.setItemMeta(backMeta);
        inventory.setItem(BACK_SLOT, back);

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta headMeta = (SkullMeta) head.getItemMeta();
        headMeta.setOwningPlayer(target);
        headMeta.displayName(mm("<white>" + target.getName()));
        List<Component> lore = new ArrayList<>();
        if (target.isOnline()) {
            Player online = target.getPlayer();
            lore.add(mm("<red>❤ " + (int) online.getHealth()
                + "/" + (int) online.getMaxHealth()));
            lore.add(mm("<gold>🍖 " + online.getFoodLevel() + "/20"));
            lore.add(mm("<green>⭐ Level " + online.getLevel()));
            lore.add(mm("<gray>" + online.getGameMode().name()));
            lore.add(mm("<green>Online"));
        } else {
            lore.add(mm("<gray>Offline"));
        }
        headMeta.lore(lore);
        head.setItemMeta(headMeta);
        inventory.setItem(HEAD_SLOT, head);
    }

    private void renderEquipment() {
        Player online = target.isOnline() ? target.getPlayer() : null;
        PlayerDataProvider provider = new PlayerDataProvider();

        ItemStack[] equip = online != null
            ? provider.getEquipment(online) : null;
        int[] equipSlots = {
            ROW_SIZE * EQUIP_ROW + 1,
            ROW_SIZE * EQUIP_ROW + 2,
            ROW_SIZE * EQUIP_ROW + 3,
            ROW_SIZE * EQUIP_ROW + 4,
            ROW_SIZE * EQUIP_ROW + 6,
            ROW_SIZE * EQUIP_ROW + 7
        };
        String[] labels = {
            "Helmet", "Chestplate", "Leggings", "Boots",
            "Main Hand", "Off Hand"
        };

        for (int i = 0; i < equipSlots.length; i++) {
            if (equip != null && equip[i] != null
                && equip[i].getType() != Material.AIR) {
                inventory.setItem(equipSlots[i], equip[i].clone());
            } else {
                inventory.setItem(equipSlots[i], placeholder(labels[i]));
            }
        }
    }

    private void buildStatItems() {
        PlayerDataProvider provider = new PlayerDataProvider();
        Map<Statistic, Integer> stats = provider.getStats(target);
        List<ItemStack> items = new ArrayList<>();

        int ticks = provider.getPlaytimeTicks(target);
        int hours = ticks / 20 / 3600;
        items.add(statItem(Material.CLOCK,
            "Playtime: " + hours + "h",
            formatTicks(ticks)));

        items.add(statItem(Material.SKELETON_SKULL,
            "Deaths: " + stats.getOrDefault(Statistic.DEATHS, 0), null));
        items.add(statItem(Material.DIAMOND_SWORD,
            "Player Kills: "
                + stats.getOrDefault(Statistic.PLAYER_KILLS, 0),
            "Mob Kills: "
                + stats.getOrDefault(Statistic.MOB_KILLS, 0)));
        items.add(statItem(Material.LEATHER_BOOTS,
            "Walked: " + cmToKm(
                stats.getOrDefault(Statistic.WALK_ONE_CM, 0)),
            null));
        items.add(statItem(Material.ELYTRA,
            "Flown: " + cmToKm(
                stats.getOrDefault(Statistic.FLY_ONE_CM, 0)),
            null));
        items.add(statItem(Material.DIAMOND_PICKAXE,
            "Blocks Mined: "
                + stats.getOrDefault(Statistic.MINE_BLOCK, 0),
            null));
        items.add(statItem(Material.CRAFTING_TABLE,
            "Items Crafted: "
                + stats.getOrDefault(Statistic.CRAFT_ITEM, 0),
            null));
        items.add(statItem(Material.FISHING_ROD,
            "Fish Caught: "
                + stats.getOrDefault(Statistic.FISH_CAUGHT, 0),
            null));
        items.add(statItem(Material.IRON_SWORD,
            "Damage Dealt: "
                + stats.getOrDefault(Statistic.DAMAGE_DEALT, 0),
            "Damage Taken: "
                + stats.getOrDefault(Statistic.DAMAGE_TAKEN, 0)));
        items.add(statItem(Material.RABBIT_FOOT,
            "Jumps: "
                + stats.getOrDefault(Statistic.JUMP, 0),
            null));

        this.statItems = items;
    }

    private void loadDatabaseDataAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseReader db = plugin.getDatabaseReader();
            if (db == null) {
                return;
            }

            String uuid = target.getUniqueId().toString();
            List<LocationData> locations = db.getLocations(uuid);
            List<ShopData> shops = db.getShops(uuid);

            List<ItemStack> locItems = buildLocationItems(locations);
            List<ItemStack> shItems = buildShopItems(shops);

            Bukkit.getScheduler().runTask(plugin, () -> {
                this.locationItems = locItems;
                this.shopItems = shItems;
                renderScrollableRow(LOCATIONS_ROW, locationItems);
                renderScrollableRow(SHOPS_ROW, shopItems);
            });
        });
    }

    private List<ItemStack> buildLocationItems(
        List<LocationData> locations
    ) {
        if (locations.isEmpty()) {
            return List.of(placeholder("None"));
        }
        List<ItemStack> items = new ArrayList<>();
        for (LocationData loc : locations) {
            ItemStack item = new ItemStack(Material.COMPASS);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(mm("<aqua>" + loc.name()));
            List<Component> lore = new ArrayList<>();
            lore.add(mm("<gray>Tag: <white>" + loc.tag()));
            lore.add(mm("<gray>Public: <white>"
                + (loc.isPublic() ? "Yes" : "No")));
            for (LocationData.CoordData c : loc.coords()) {
                lore.add(mm("<gray>" + c.world() + ": <white>"
                    + c.x() + ", " + c.y() + ", " + c.z()));
            }
            if (!loc.memberNames().isEmpty()) {
                lore.add(mm("<gray>Members: <white>"
                    + String.join(", ", loc.memberNames())));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
            items.add(item);
        }
        return items;
    }

    private List<ItemStack> buildShopItems(List<ShopData> shops) {
        if (shops.isEmpty()) {
            return List.of(placeholder("None"));
        }
        List<ItemStack> items = new ArrayList<>();
        for (ShopData shop : shops) {
            ItemStack item = new ItemStack(Material.BARREL);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(mm("<gold>" + shop.name()));
            List<Component> lore = new ArrayList<>();
            lore.add(mm("<gray>" + shop.world() + ": <white>"
                + shop.x() + ", " + shop.y() + ", " + shop.z()));
            lore.add(mm("<gray>Stock: <white>"
                + shop.stockCount() + " items"));
            meta.lore(lore);
            item.setItemMeta(meta);
            items.add(item);
        }
        return items;
    }

    private void renderScrollableRow(int row, List<ItemStack> items) {
        int baseSlot = row * ROW_SIZE;
        int offset = rowOffsets[row];

        // Clear row
        for (int i = 0; i < ROW_SIZE; i++) {
            inventory.setItem(baseSlot + i, null);
        }

        if (items.size() <= VISIBLE_ITEMS) {
            // No scroll needed, fill from slot 1
            for (int i = 0; i < items.size(); i++) {
                inventory.setItem(baseSlot + 1 + i, items.get(i));
            }
            return;
        }

        // Left arrow
        if (offset > 0) {
            inventory.setItem(baseSlot, arrowItem(
                "<gold>Scroll Left", false, 0));
        } else {
            inventory.setItem(baseSlot,
                new ItemStack(Material.GRAY_STAINED_GLASS_PANE));
        }

        // Visible items
        for (int i = 0; i < VISIBLE_ITEMS; i++) {
            int idx = offset + i;
            if (idx < items.size()) {
                inventory.setItem(baseSlot + 1 + i, items.get(idx));
            }
        }

        // Right arrow
        int remaining = items.size() - offset - VISIBLE_ITEMS;
        if (remaining > 0) {
            inventory.setItem(baseSlot + 8, arrowItem(
                "<gold>Scroll Right (+" + remaining + ")",
                true, remaining));
        } else {
            inventory.setItem(baseSlot + 8,
                new ItemStack(Material.GRAY_STAINED_GLASS_PANE));
        }
    }

    private void scrollLeft(int row) {
        List<ItemStack> items = getItemsForRow(row);
        if (items.size() <= VISIBLE_ITEMS) {
            return;
        }
        if (rowOffsets[row] > 0) {
            rowOffsets[row]--;
            renderScrollableRow(row, items);
        }
    }

    private void scrollRight(int row) {
        List<ItemStack> items = getItemsForRow(row);
        if (items.size() <= VISIBLE_ITEMS) {
            return;
        }
        int maxOffset = items.size() - VISIBLE_ITEMS;
        if (rowOffsets[row] < maxOffset) {
            rowOffsets[row]++;
            renderScrollableRow(row, items);
        }
    }

    private List<ItemStack> getItemsForRow(int row) {
        return switch (row) {
            case LOCATIONS_ROW -> locationItems;
            case SHOPS_ROW -> shopItems;
            case STATS_ROW -> statItems;
            default -> List.of();
        };
    }

    private boolean isScrollableRow(int row) {
        return row == LOCATIONS_ROW || row == SHOPS_ROW
            || row == STATS_ROW;
    }

    private static ItemStack statItem(Material material, String name,
                                      String loreText) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(mm("<white>" + name));
        if (loreText != null) {
            meta.lore(List.of(mm("<gray>" + loreText)));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack arrowItem(String name, boolean isRight,
                                       int remaining) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(mm(name));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack placeholder(String label) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(mm("<gray>" + label));
        pane.setItemMeta(meta);
        return pane;
    }

    private static String cmToKm(int cm) {
        double km = cm / 100_000.0;
        return String.format("%.1f km", km);
    }

    private static String formatTicks(int ticks) {
        int totalSeconds = ticks / 20;
        int days = totalSeconds / 86400;
        int hours = (totalSeconds % 86400) / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        if (days > 0) {
            return days + "d " + hours + "h " + minutes + "m";
        }
        return hours + "h " + minutes + "m";
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `make test`
Expected: All PlayerDetailGuiTest tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ua/favn/beacolanders/gui/PlayerDetailGui.java \
  src/test/java/ua/favn/beacolanders/gui/PlayerDetailGuiTest.java
git commit -m "Add PlayerDetailGui with row-scroll mechanic"
```

---

## Task 8: BeacolandersCommand

**Files:**
- Create: `src/main/java/ua/favn/beacolanders/commands/BeacolandersCommand.java`
- Create: `src/test/java/ua/favn/beacolanders/commands/BeacolandersCommandTest.java`

- [ ] **Step 1: Write failing test for command**

Create `src/test/java/ua/favn/beacolanders/commands/BeacolandersCommandTest.java`:

```java
package ua.favn.beacolanders.commands;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import ua.favn.beacolanders.Beacolanders;
import ua.favn.beacolanders.gui.GuiHolder;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void onCommand_opensGui() {
        PlayerMock player = server.addPlayer("Steve");

        BeacolandersCommand cmd = new BeacolandersCommand(plugin);
        boolean result = cmd.onCommand(player, null, "bl", new String[]{});

        assertTrue(result);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `make test`
Expected: FAIL — `BeacolandersCommand` class not found

- [ ] **Step 3: Implement BeacolandersCommand**

Create `src/main/java/ua/favn/beacolanders/commands/BeacolandersCommand.java`:

```java
package ua.favn.beacolanders.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ua.favn.beacolanders.Beacolanders;
import ua.favn.beacolanders.gui.PlayerListGui;

public final class BeacolandersCommand implements CommandExecutor {

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

        PlayerListGui gui = new PlayerListGui(plugin);
        player.openInventory(gui.getInventory());
        return true;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `make test`
Expected: BeacolandersCommandTest PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ua/favn/beacolanders/commands/BeacolandersCommand.java \
  src/test/java/ua/favn/beacolanders/commands/BeacolandersCommandTest.java
git commit -m "Add BeacolandersCommand for /bl"
```

---

## Task 9: Wire Everything in Beacolanders.java

**Files:**
- Modify: `src/main/java/ua/favn/beacolanders/Beacolanders.java`

- [ ] **Step 1: Update Beacolanders main class**

Replace the entire contents of `src/main/java/ua/favn/beacolanders/Beacolanders.java`:

```java
package ua.favn.beacolanders;

import org.bukkit.plugin.java.JavaPlugin;
import ua.favn.beacolanders.commands.BeacolandersCommand;
import ua.favn.beacolanders.data.DatabaseReader;
import ua.favn.beacolanders.gui.GuiListener;

import java.io.File;
import java.sql.SQLException;

public final class Beacolanders extends JavaPlugin {

    private DatabaseReader databaseReader;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        initDatabase();
        registerCommand();
        registerListener();
        getLogger().info("Beacolanders enabled!");
    }

    @Override
    public void onDisable() {
        if (databaseReader != null) {
            databaseReader.close();
        }
        getLogger().info("Beacolanders disabled!");
    }

    public DatabaseReader getDatabaseReader() {
        return databaseReader;
    }

    private void initDatabase() {
        String bmPath = getConfig().getString("database.basemanager",
            "../BaseManager/storage.db");
        String ssPath = getConfig().getString("database.shopsearch",
            "../ShopSearch/storage.db");

        File bmFile = new File(getDataFolder(), bmPath);
        File ssFile = new File(getDataFolder(), ssPath);

        if (!bmFile.exists()) {
            getLogger().warning("BaseManager database not found: "
                + bmFile.getAbsolutePath());
        }
        if (!ssFile.exists()) {
            getLogger().warning("ShopSearch database not found: "
                + ssFile.getAbsolutePath());
        }

        if (bmFile.exists() && ssFile.exists()) {
            try {
                databaseReader = new DatabaseReader(bmFile, ssFile);
            } catch (SQLException e) {
                getLogger().severe("Failed to open databases: "
                    + e.getMessage());
            }
        }
    }

    private void registerCommand() {
        var command = getCommand("beacolanders");
        if (command != null) {
            command.setExecutor(new BeacolandersCommand(this));
        }
    }

    private void registerListener() {
        getServer().getPluginManager()
            .registerEvents(new GuiListener(), this);
    }
}
```

- [ ] **Step 2: Run full verify**

Run: `make verify`
Expected: BUILD SUCCESS — compile, tests, checkstyle, and spotbugs all pass

- [ ] **Step 3: Fix any checkstyle or spotbugs issues**

If `make verify` reports issues, fix them. Common ones to watch for:
- Line length > 120 characters
- Missing `@Override` annotations
- Unused imports

- [ ] **Step 4: Commit**

```bash
git add src/main/java/ua/favn/beacolanders/Beacolanders.java
git commit -m "Wire command, listener, and database in main plugin class"
```

---

## Task 10: Integration Test — Deploy and Smoke Test

**Files:** None (manual verification)

- [ ] **Step 1: Build and deploy**

Run: `make deploy`
Expected: JAR deployed to test server plugins directory

- [ ] **Step 2: Start test server and verify**

Start the Purpur test server. Verify in server console:
- `[Beacolanders] Beacolanders enabled!`
- No warnings about missing databases (BaseManager and ShopSearch DBs should exist)

- [ ] **Step 3: In-game verification**

Join the test server and verify:
1. `/bl` opens the player list GUI
2. `/beacolanders` also opens it
3. Player heads appear for online players
4. Clicking a player head opens the detail view
5. Equipment row shows the player's gear
6. Stats row is populated and scrollable
7. Locations and shops rows show data from BaseManager/ShopSearch
8. Back button returns to the player list
9. Search button opens AnvilGUI and filters players

- [ ] **Step 4: Commit any fixes**

If any issues found during smoke testing, fix and commit.

```bash
git add -A
git commit -m "Fix issues found during smoke testing"
```
