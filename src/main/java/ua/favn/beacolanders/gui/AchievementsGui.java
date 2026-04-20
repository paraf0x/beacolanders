package ua.favn.beacolanders.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ua.favn.beacolanders.Beacolanders;
import ua.favn.beacolanders.data.AchievementDefinition;

import java.util.ArrayList;
import java.util.List;

public final class AchievementsGui extends GuiHolder {

    private static final int BACK_SLOT = 0;
    private static final int TITLE_SLOT = 4;
    private static final int ITEMS_START = ROW_SIZE;
    private static final int ITEMS_END = ROW_SIZE * 5;
    private static final int ITEMS_PER_PAGE = ITEMS_END - ITEMS_START;
    private static final int PREV_SLOT = ROW_SIZE * 5;
    private static final int NEXT_SLOT = ROW_SIZE * 5 + 8;
    private static final int BAR_LENGTH = 10;

    private final Beacolanders plugin;
    private final Player viewer;
    private final OfflinePlayer target;
    private final List<AchievementDefinition> definitions;
    private int page;

    public AchievementsGui(Beacolanders plugin, Player viewer,
                           OfflinePlayer target) {
        super("<dark_gray>Achievements");
        this.plugin = plugin;
        this.viewer = viewer;
        this.target = target;
        this.definitions = plugin.getAchievementManager().getAll();
        this.page = 0;
        render();
    }

    @Override
    public void onClick(int slot, Player player) {
        if (slot == BACK_SLOT) {
            PlayerDetailGui detail = new PlayerDetailGui(plugin, target, viewer);
            player.openInventory(detail.getInventory());
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
        }
    }

    private void render() {
        inventory.clear();
        renderHeader();
        renderAchievements();
        renderPagination();
    }

    private void renderHeader() {
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(mm("<red>Back"));
        back.setItemMeta(backMeta);
        inventory.setItem(BACK_SLOT, back);

        ItemStack title = new ItemStack(Material.EMERALD);
        ItemMeta titleMeta = title.getItemMeta();
        titleMeta.displayName(mm("<green>Achievements"));
        title.setItemMeta(titleMeta);
        inventory.setItem(TITLE_SLOT, title);
    }

    private void renderAchievements() {
        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, definitions.size());
        for (int i = start; i < end; i++) {
            AchievementDefinition def = definitions.get(i);
            int slot = ITEMS_START + (i - start);
            inventory.setItem(slot, buildAchievementItem(def));
        }
    }

    private ItemStack buildAchievementItem(AchievementDefinition def) {
        long value = getStat(def);
        int tier = def.getTier(value);
        ItemStack item = createBaseItem(def, tier);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(buildDisplayName(def, tier));
        meta.lore(buildLore(def, value, tier));
        item.setItemMeta(meta);
        return item;
    }

    private long getStat(AchievementDefinition def) {
        try {
            return target.getStatistic(def.stat());
        } catch (IllegalArgumentException ignored) {
            return 0L;
        }
    }

    private ItemStack createBaseItem(AchievementDefinition def, int tier) {
        if (tier == 0) {
            return new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        }
        ItemStack item = new ItemStack(def.icon());
        item.setAmount(tier);
        return item;
    }

    private Component buildDisplayName(AchievementDefinition def, int tier) {
        if (tier == 0) {
            return mm("<gray>" + def.name());
        }
        String tierLabel = switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(tier);
        };
        return mm("<green>" + def.name() + " " + tierLabel);
    }

    private List<Component> buildLore(AchievementDefinition def, long value, int tier) {
        List<Component> lore = new ArrayList<>();
        long next = def.getNextThreshold(tier);
        long cap = tier < 3 ? next : def.tier3();
        long capped = Math.min(value, cap);
        lore.add(mm("<gray>Progress: <white>" + capped + " / " + cap));
        lore.add(mm("<gray>" + buildBar(capped, cap)));
        lore.add(mm(""));
        lore.add(buildTierLine("I", def.tier1(), value));
        lore.add(buildTierLine("II", def.tier2(), value));
        lore.add(buildTierLine("III", def.tier3(), value));
        return lore;
    }

    private String buildBar(long value, long max) {
        if (max <= 0) {
            return "[" + "░".repeat(BAR_LENGTH) + "] 0%";
        }
        double ratio = Math.min(1.0, (double) value / max);
        int filled = (int) Math.round(ratio * BAR_LENGTH);
        int percent = (int) Math.round(ratio * 100);
        return "[" + "█".repeat(filled)
            + "░".repeat(BAR_LENGTH - filled) + "] " + percent + "%";
    }

    private Component buildTierLine(String tierLabel, long threshold, long value) {
        String check = value >= threshold ? "<green>✔ " : "<gray>  ";
        return mm(check + tierLabel + ": <white>" + threshold);
    }

    private void renderPagination() {
        int totalPages = Math.max(1,
            (int) Math.ceil((double) definitions.size() / ITEMS_PER_PAGE));

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
        inventory.setItem(ROW_SIZE * 5 + 4, indicator);

        if (hasNextPage()) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = next.getItemMeta();
            nextMeta.displayName(mm("<gold>Next Page"));
            next.setItemMeta(nextMeta);
            inventory.setItem(NEXT_SLOT, next);
        }
    }

    private boolean hasNextPage() {
        return (page + 1) * ITEMS_PER_PAGE < definitions.size();
    }
}
