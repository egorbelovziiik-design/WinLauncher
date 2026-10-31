package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Side-effect-free preflight on the 36 actual storage slots, including merges. */
final class InventoryCapacity {
    private InventoryCapacity() {}

    static boolean canFit(Player player, ItemStack... items) {
        if (player == null || items == null) return false;
        Inventory trial = Bukkit.createInventory(null, 36);
        ItemStack[] storage = player.getInventory().getStorageContents();
        ItemStack[] copy = new ItemStack[36];
        for (int i = 0; i < copy.length && i < storage.length; i++) {
            if (storage[i] != null) copy[i] = storage[i].clone();
        }
        trial.setContents(copy);
        ItemStack[] incoming = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            if (items[i] == null) return false;
            incoming[i] = items[i].clone();
        }
        return trial.addItem(incoming).isEmpty();
    }
}
