package ua.favn.beacolanders.commands;

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
import java.util.Locale;

public final class BeacolandersCommand implements CommandExecutor, TabCompleter {

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
            PlayerListGui gui = new PlayerListGui(plugin);
            player.openInventory(gui.getInventory());
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("top")) {
            if (args.length >= 2) {
                StatCategory cat = StatCategory.fromKeyword(args[1]);
                if (cat != null) {
                    LeaderboardGui gui = new LeaderboardGui(plugin, cat, player);
                    player.openInventory(gui.getInventory());
                    return true;
                }
            }
            LeaderboardSelectorGui gui = new LeaderboardSelectorGui(plugin);
            player.openInventory(gui.getInventory());
            return true;
        }

        if (sub.equals("achievements")) {
            if (args.length >= 2) {
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target != null) {
                    AchievementsGui gui = new AchievementsGui(plugin, player, target);
                    player.openInventory(gui.getInventory());
                    return true;
                }
            }
            AchievementsGui gui = new AchievementsGui(plugin, player, player);
            player.openInventory(gui.getInventory());
            return true;
        }

        PlayerListGui gui = new PlayerListGui(plugin);
        player.openInventory(gui.getInventory());
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            for (String option : List.of("top", "achievements")) {
                if (option.startsWith(partial)) {
                    suggestions.add(option);
                }
            }
            return suggestions;
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String partial = args[1].toLowerCase(Locale.ROOT);

            if (sub.equals("top")) {
                for (StatCategory cat : StatCategory.values()) {
                    String keyword = cat.displayName().toLowerCase(Locale.ROOT)
                        .replace(" ", "");
                    if (keyword.startsWith(partial)) {
                        suggestions.add(keyword);
                    }
                }
            } else if (sub.equals("achievements")) {
                for (Player online : plugin.getServer().getOnlinePlayers()) {
                    if (online.getName().toLowerCase(Locale.ROOT).startsWith(partial)) {
                        suggestions.add(online.getName());
                    }
                }
            }
        }

        return suggestions;
    }
}
