package ua.favn.beacolanders.gui;

import net.kyori.adventure.text.Component;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import ua.favn.beacolanders.Beacolanders;

import java.util.ArrayList;
import java.util.List;

public final class PlayerListGui extends GuiHolder {

    private static final int PLAYERS_PER_PAGE = 36;
    private static final int PLAYER_START_SLOT = ROW_SIZE;
    private static final int SEARCH_SLOT = 0;
    private static final int LEADERBOARDS_SLOT = 2;
    private static final int TITLE_SLOT = 4;
    private static final int ACHIEVEMENTS_SLOT = 6;
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
        if (slot == LEADERBOARDS_SLOT) {
            LeaderboardSelectorGui lb = new LeaderboardSelectorGui(plugin);
            player.openInventory(lb.getInventory());
            return;
        }
        if (slot == ACHIEVEMENTS_SLOT) {
            AchievementsGui ach = new AchievementsGui(plugin, player, player);
            player.openInventory(ach.getInventory());
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

        ItemStack leaderboards = new ItemStack(Material.GOLD_INGOT);
        ItemMeta leaderboardsMeta = leaderboards.getItemMeta();
        leaderboardsMeta.displayName(mm("<gold>Leaderboards"));
        leaderboards.setItemMeta(leaderboardsMeta);
        inventory.setItem(LEADERBOARDS_SLOT, leaderboards);

        ItemStack title = new ItemStack(Material.NETHER_STAR);
        ItemMeta titleMeta = title.getItemMeta();
        titleMeta.displayName(mm("<gold>Beacolanders"));
        title.setItemMeta(titleMeta);
        inventory.setItem(TITLE_SLOT, title);

        ItemStack achievements = new ItemStack(Material.EMERALD);
        ItemMeta achievementsMeta = achievements.getItemMeta();
        achievementsMeta.displayName(mm("<green>Achievements"));
        achievements.setItemMeta(achievementsMeta);
        inventory.setItem(ACHIEVEMENTS_SLOT, achievements);
    }

    private void renderPlayers() {
        int start = page * PLAYERS_PER_PAGE;
        int end = Math.min(start + PLAYERS_PER_PAGE, filteredPlayers.size());

        for (int i = start; i < end; i++) {
            Player target = filteredPlayers.get(i);
            ItemStack head = buildPlayerHead(target);
            int slot = PLAYER_START_SLOT + (i - start);
            inventory.setItem(slot, head);
        }
    }

    private ItemStack buildPlayerHead(Player target) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(target);
        meta.displayName(mm("<white>" + target.getName()));
        List<Component> lore = new ArrayList<>();
        lore.add(mm("<green>Online"));
        lore.add(mm("<gray>" + target.getGameMode().name()));
        meta.lore(lore);
        head.setItemMeta(meta);
        return head;
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
        indicatorMeta.displayName(mm("<gray>Page " + (page + 1) + "/" + totalPages));
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
        if (slot < PLAYER_START_SLOT || slot >= PLAYER_START_SLOT + PLAYERS_PER_PAGE) {
            return -1;
        }
        return page * PLAYERS_PER_PAGE + (slot - PLAYER_START_SLOT);
    }

    private void openSearch(Player player) {
        new AnvilGUI.Builder()
            .plugin(plugin)
            .title("Search player...")
            .text("")
            .onClick((slot, state) -> {
                if (slot == AnvilGUI.Slot.OUTPUT) {
                    applySearch(state.getText());
                    player.openInventory(getInventory());
                    return List.of(AnvilGUI.ResponseAction.close());
                }
                return List.of();
            })
            .open(player);
    }
}
