package ua.favn.beacolanders.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
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
    private static final int TOP_LIMIT = 10;
    private static final int VIEWER_SLOT = ROW_SIZE * 5 + 4;

    private final Beacolanders plugin;
    private final StatCategory category;
    private final Player viewer;

    public LeaderboardGui(Beacolanders plugin, StatCategory category,
                          Player viewer) {
        super("<dark_gray>" + category.displayName() + " Leaderboard");
        this.plugin = plugin;
        this.category = category;
        this.viewer = viewer;
        render();
    }

    @Override
    public void onClick(int slot, Player player) {
        if (slot == BACK_SLOT) {
            LeaderboardSelectorGui selector = new LeaderboardSelectorGui(plugin);
            player.openInventory(selector.getInventory());
        }
    }

    private void render() {
        inventory.clear();
        renderHeader();
        renderTopEntries();
        renderViewerEntry();
    }

    private void renderHeader() {
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(mm("<red>Back to Leaderboards"));
        back.setItemMeta(backMeta);
        inventory.setItem(BACK_SLOT, back);

        ItemStack icon = new ItemStack(category.icon());
        ItemMeta iconMeta = icon.getItemMeta();
        iconMeta.displayName(mm("<yellow>" + category.displayName()));
        icon.setItemMeta(iconMeta);
        inventory.setItem(TITLE_SLOT, icon);
    }

    private void renderTopEntries() {
        LeaderboardService service = plugin.getLeaderboardService();
        List<LeaderboardEntry> top = service.getTop(category, TOP_LIMIT);
        for (int i = 0; i < top.size(); i++) {
            LeaderboardEntry entry = top.get(i);
            ItemStack head = buildEntryHead(entry, i == 0);
            inventory.setItem(TOP_START + i, head);
        }
    }

    private ItemStack buildEntryHead(LeaderboardEntry entry, boolean isFirst) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        OfflinePlayer op = plugin.getServer().getOfflinePlayer(entry.player());
        meta.setOwningPlayer(op);
        meta.displayName(mm("<gold>#" + entry.rank() + " <white>" + entry.name()));
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

    private void renderViewerEntry() {
        LeaderboardService service = plugin.getLeaderboardService();
        LeaderboardEntry entry = service.getRank(category, viewer);
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(viewer);
        meta.displayName(mm("<aqua>You <gray>- <gold>#" + entry.rank()
            + " <white>" + entry.name()));
        List<Component> lore = new ArrayList<>();
        lore.add(mm("<gray>" + category.displayName() + ": <white>"
            + category.format(entry.value())));
        meta.lore(lore);
        head.setItemMeta(meta);
        inventory.setItem(VIEWER_SLOT, head);
    }
}
