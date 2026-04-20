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
    private static final int CATEGORIES_START = ROW_SIZE;

    private final Beacolanders plugin;

    public LeaderboardSelectorGui(Beacolanders plugin) {
        super("<dark_gray>Leaderboards");
        this.plugin = plugin;
        render();
    }

    @Override
    public void onClick(int slot, Player player) {
        if (slot == BACK_SLOT) {
            PlayerListGui list = new PlayerListGui(plugin);
            player.openInventory(list.getInventory());
            return;
        }

        int catIndex = slot - CATEGORIES_START;
        StatCategory[] cats = StatCategory.values();
        if (catIndex >= 0 && catIndex < cats.length) {
            StatCategory cat = cats[catIndex];
            LeaderboardGui lb = new LeaderboardGui(plugin, cat, player);
            player.openInventory(lb.getInventory());
        }
    }

    private void render() {
        inventory.clear();
        renderHeader();
        renderCategories();
    }

    private void renderHeader() {
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
    }

    private void renderCategories() {
        StatCategory[] cats = StatCategory.values();
        for (int i = 0; i < cats.length; i++) {
            StatCategory cat = cats[i];
            ItemStack item = new ItemStack(cat.icon());
            ItemMeta meta = item.getItemMeta();
            meta.displayName(mm("<yellow>" + cat.displayName()));
            item.setItemMeta(meta);
            inventory.setItem(CATEGORIES_START + i, item);
        }
    }
}
