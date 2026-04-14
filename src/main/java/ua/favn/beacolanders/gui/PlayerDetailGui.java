package ua.favn.beacolanders.gui;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import ua.favn.beacolanders.Beacolanders;

public final class PlayerDetailGui extends GuiHolder {

    public PlayerDetailGui(Beacolanders plugin, OfflinePlayer target, Player viewer) {
        super("<dark_gray>" + target.getName());
    }

    @Override
    public void onClick(int slot, Player player) {
        // Stub — implemented in Task 7
    }
}
