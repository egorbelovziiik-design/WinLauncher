package net.kinggrief.core;
import org.bukkit.inventory.meta.ItemMeta; import org.bukkit.persistence.PersistentDataContainer;
/** Old Spigot API has no getItemModel / setItemModel at all. */
public final class LegacyApiTest {
    public static void main(String[] args) {
        ItemMeta meta = new ItemMeta() {
            public boolean hasCustomModelData() { return true; }
            public PersistentDataContainer getPersistentDataContainer() { return null; }
        };
        if (LegacyItemModelMigration.repairMeta(meta)) throw new AssertionError("legacy API must be a no-op");
        System.out.println("Legacy API without item_model: OK");
    }
}
