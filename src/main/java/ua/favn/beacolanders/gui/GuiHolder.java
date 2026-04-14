package ua.favn.beacolanders.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public abstract class GuiHolder implements InventoryHolder {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    protected static final int ROW_SIZE = 9;
    protected static final int ROWS = 6;
    protected static final int INVENTORY_SIZE = ROW_SIZE * ROWS;

    protected final Inventory inventory;

    protected GuiHolder(String title) {
        Component titleComponent = MINI.deserialize(title);
        this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE, titleComponent);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    /**
     * Handle a click on the given raw slot index.
     * Implementations should perform actions (open other GUIs, scroll, etc).
     */
    public abstract void onClick(int slot, org.bukkit.entity.Player player);

    protected static Component mm(String miniMessage) {
        return MINI.deserialize(miniMessage);
    }
}
