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
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
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
