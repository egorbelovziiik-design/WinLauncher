package org.bukkit.inventory.meta;
import org.bukkit.NamespacedKey; import org.bukkit.persistence.PersistentDataContainer;
public interface ItemMeta {
    boolean hasCustomModelData(); PersistentDataContainer getPersistentDataContainer();
    NamespacedKey getItemModel(); void setItemModel(NamespacedKey key);
}
