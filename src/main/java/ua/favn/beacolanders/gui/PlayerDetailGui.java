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

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

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
            "Deaths: " + stats.getOrDefault(Statistic.DEATHS, 0),
            null));
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
            ItemStack item = parseIcon(loc.icon());
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
            ItemStack item = new ItemStack(Material.CHEST);
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
            for (int i = 0; i < items.size(); i++) {
                inventory.setItem(baseSlot + 1 + i, items.get(i));
            }
            return;
        }

        // Left arrow
        if (offset > 0) {
            inventory.setItem(baseSlot, arrowItem("<gold>Scroll Left"));
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
            inventory.setItem(baseSlot + 8,
                arrowItem("<gold>Scroll Right (+" + remaining + ")"));
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

    private static ItemStack parseIcon(String icon) {
        if (icon == null || icon.isEmpty()) {
            return new ItemStack(Material.LODESTONE);
        }
        // Simple material name (e.g. "STICK")
        Material simple = Material.matchMaterial(icon);
        if (simple != null) {
            return new ItemStack(simple);
        }
        // Base64-encoded Bukkit ItemStack YAML
        try {
            String yaml = new String(
                Base64.getDecoder().decode(icon));
            YamlConfiguration config = new YamlConfiguration();
            config.load(new StringReader(yaml));
            ItemStack parsed = config.getItemStack("item");
            if (parsed != null) {
                return parsed;
            }
        } catch (Exception e) {
            Logger.getLogger(PlayerDetailGui.class.getName())
                .log(Level.FINE, "Failed to parse icon", e);
        }
        return new ItemStack(Material.LODESTONE);
    }

    private static ItemStack statItem(Material material, String name,
                                      String loreText) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(mm(name));
        if (loreText != null) {
            meta.lore(List.of(mm("<gray>" + loreText)));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack arrowItem(String name) {
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
