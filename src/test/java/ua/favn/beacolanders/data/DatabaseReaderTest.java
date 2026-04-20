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
import static org.junit.jupiter.api.Assertions.assertNull;
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
            stmt.execute("INSERT INTO locations (id, owner, tag, name, created, isPublic, icon)"
                + " VALUES (1, '" + ownerUuid + "', 'BASE', 'My Base', '2025-01-01', 1, 'STICK')");
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
        assertEquals("STICK", loc.icon());
    }

    @Test
    void getLocations_returnsNullIconWhenNotSet() throws Exception {
        String ownerUuid = "ca18d342-1234-5678-9abc-000000000001";
        try (Statement stmt = bmConn.createStatement()) {
            stmt.execute("INSERT INTO locations (id, owner, tag, name, created)"
                + " VALUES (99, '" + ownerUuid + "', 'BASE', 'No Icon', '2025-01-01')");
        }

        List<LocationData> locations = reader.getLocations(ownerUuid);
        assertEquals(1, locations.size());
        assertNull(locations.get(0).icon());
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
