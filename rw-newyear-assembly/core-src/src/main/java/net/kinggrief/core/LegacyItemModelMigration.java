package net.kinggrief.core;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;

/**
 * Removes only the explicit item model written by older KingGrief cores.
 * CustomModelData and every other item component are left intact. Reflection
 * keeps the plugin loadable on APIs predating ItemMeta#getItemModel.
 */
public final class LegacyItemModelMigration {
    private static final Method GET_MODEL = findMethod("getItemModel");
    private static final Method SET_MODEL = findMethod("setItemModel", NamespacedKey.class);

    private LegacyItemModelMigration() {}

    private static Method findMethod(String name, Class<?>... parameters) {
        try {
            return ItemMeta.class.getMethod(name, parameters);
        } catch (NoSuchMethodException | SecurityException ignored) {
            return null;
        }
    }

    /** Mutates only a matching, tagged meta and returns true when changed. */
    static boolean repairMeta(ItemMeta meta) {
        if (meta == null || GET_MODEL == null || SET_MODEL == null) return false;
        try {
            if (!meta.hasCustomModelData()) return false;
            String customId = meta.getPersistentDataContainer().get(ItemFactory.ITEM_TAG_KEY, PersistentDataType.STRING);
            if (customId == null || customId.isEmpty()) return false;
            Object current = GET_MODEL.invoke(meta);
            if (!(current instanceof NamespacedKey)) return false;
            NamespacedKey key = (NamespacedKey) current;
            if (!"kinggrief".equals(key.getNamespace()) || !customId.equals(key.getKey())) return false;
            SET_MODEL.invoke(meta, new Object[] { null });
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    public static boolean repairItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return repairMeta(meta) && item.setItemMeta(meta);
    }

    /** Also works for armor/offhand slots exposed by PlayerInventory#getSize. */
    public static int repairInventory(Inventory inventory) {
        if (inventory == null) return 0;
        int repaired = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (repairItem(item)) {
                inventory.setItem(slot, item);
                repaired++;
            }
        }
        return repaired;
    }
}
