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
