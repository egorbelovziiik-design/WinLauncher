package org.bukkit.inventory;
import org.bukkit.Material; import org.bukkit.inventory.meta.ItemMeta;
public class ItemStack {
    protected Material type; protected ItemMeta meta;
    public ItemStack(Material type, ItemMeta meta) { this.type = type; this.meta = meta; }
    public Material getType() { return type; } public boolean hasItemMeta() { return meta != null; }
    public ItemMeta getItemMeta() { return meta; } public boolean setItemMeta(ItemMeta meta) { this.meta = meta; return true; }
}
