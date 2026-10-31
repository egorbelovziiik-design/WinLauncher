package net.kinggrief.core;

import net.kinggrief.common.ResourcePackSettings;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;

/** Executed against a small modern-API fixture: no Minecraft server required. */
public final class ModelMigrationTest {
    private static final String OLD_URL = "https://kinggrief.vercel.app/kinggrief-mega-rp.zip?v=63";
    private static final String OLD_SHA = "3637015af079e6fea621c10fe6dfa51e35cc0113";
    private static final String V64_URL = "https://kinggrief.vercel.app/kinggrief-mega-rp-v64.zip";
    private static final String V64_SHA = "21b2343076c751bf392ac3bd47528c59988fb92c";
    private static int assertions;

    private static void check(boolean value, String reason) {
        assertions++;
        if (!value) throw new AssertionError(reason);
    }

    private static final class Data implements PersistentDataContainer {
        final Map<NamespacedKey, Object> values = new HashMap<NamespacedKey, Object>();
        @SuppressWarnings("unchecked")
        public <P,C> C get(NamespacedKey key, PersistentDataType<P,C> type) { return (C) values.get(key); }
    }

    public static final class Meta implements ItemMeta {
        NamespacedKey model;
        boolean hasCmd;
        int cmd;
        String name = "original name";
        int charges = 3;
        String lore = "original lore / enchantments";
        final Data data = new Data();
        Meta(String id, String namespace, String modelId, boolean hasCmd, int cmd) {
            if (id != null) data.values.put(ItemFactory.ITEM_TAG_KEY, id);
            model = namespace == null ? null : new NamespacedKey(namespace, modelId);
            this.hasCmd = hasCmd;
            this.cmd = cmd;
        }
        Meta(Meta old) {
            model = old.model; hasCmd = old.hasCmd; cmd = old.cmd;
            name = old.name; charges = old.charges; lore = old.lore;
            data.values.putAll(old.data.values);
        }
        public boolean hasCustomModelData() { return hasCmd; }
        public PersistentDataContainer getPersistentDataContainer() { return data; }
        public NamespacedKey getItemModel() { return model; }
        public void setItemModel(NamespacedKey model) { this.model = model; }
    }

    /** Bukkit returns a copy of meta, requiring setItemMeta to save it. */
    private static final class Stack extends ItemStack {
        int writes;
        Stack(Meta meta) { super(Material.TOTEM_OF_UNDYING, meta); }
        @Override public ItemMeta getItemMeta() { return meta == null ? null : new Meta((Meta) meta); }
        @Override public boolean setItemMeta(ItemMeta m) { writes++; meta = m; return true; }
        Meta stored() { return (Meta) meta; }
    }

    private static final class Inv implements Inventory {
        final ItemStack[] slots;
        int writes;
        Inv(int size) { slots = new ItemStack[size]; }
        public int getSize() { return slots.length; }
        public ItemStack getItem(int slot) { return slots[slot]; }
        public void setItem(int slot, ItemStack item) { writes++; slots[slot] = item; }
    }

    private static Meta matching(String id, int cmd) { return new Meta(id, "kinggrief", id, true, cmd); }

    private static void testItemRepair() {
        Stack old = new Stack(matching("totem_santy", 1252));
        check(LegacyItemModelMigration.repairItem(old), "old tagged model should be removed");
        Meta fixed = old.stored();
        check(fixed.model == null && fixed.hasCmd && fixed.cmd == 1252, "keep CustomModelData");
        check("totem_santy".equals(fixed.data.values.get(ItemFactory.ITEM_TAG_KEY)), "keep PDC id");
        check("original name".equals(fixed.name) && fixed.charges == 3 && fixed.lore.contains("enchantments"), "keep all unrelated metadata");
        check(old.writes == 1, "changed copy must be written back");
        check(!LegacyItemModelMigration.repairItem(old) && old.writes == 1, "migration must be idempotent");
        Meta[] untouched = {
            new Meta(null, "kinggrief", "totem_santy", true, 1252),
            new Meta("totem_santy", "otherplugin", "totem_santy", true, 1252),
            new Meta("totem_santy", "kinggrief", "other_item", true, 1252),
            new Meta("totem_santy", "kinggrief", "totem_santy", false, 0),
            new Meta("totem_santy", null, null, true, 1252),
        };
        for (Meta m : untouched) {
            NamespacedKey before = m.model;
            Stack stack = new Stack(m);
            check(!LegacyItemModelMigration.repairItem(stack), "foreign, mismatched, untagged or no-CMD model changed");
            check(stack.writes == 0 && stack.stored().model == before, "unchanged meta was written");
        }
        check(!LegacyItemModelMigration.repairItem(null), "null item");
        check(!LegacyItemModelMigration.repairItem(new ItemStack(Material.AIR, matching("totem_santy", 1252))), "air item");

        Inv inventory = new Inv(41);
        inventory.slots[0] = new Stack(matching("mega_heal", 1300));
        inventory.slots[36] = new Stack(matching("armor_owner_boots", 1724));
        inventory.slots[40] = new Stack(matching("shar_kvadrat", 1112));
        inventory.slots[4] = new Stack(new Meta("someone_else", "otherplugin", "someone_else", true, 9));
        check(LegacyItemModelMigration.repairInventory(inventory) == 3, "inventory, armor and offhand slots");
        check(inventory.writes == 3 && ((Stack) inventory.slots[40]).stored().model == null, "repaired slots saved");
        check(LegacyItemModelMigration.repairInventory(inventory) == 0, "inventory migration idempotent");
        check(LegacyItemModelMigration.repairInventory(null) == 0, "null inventory");
    }

    private static void upgraded(String url, String sha) {
        ResourcePackSettings.Pack pack = ResourcePackSettings.resolve(url, sha);
        check(pack.upgraded && ResourcePackSettings.LATEST_URL.equals(pack.url)
                && ResourcePackSettings.LATEST_SHA1.equals(pack.sha1), "official pair not upgraded: " + url);
    }

    private static void unchanged(String url, String sha) {
        ResourcePackSettings.Pack pack = ResourcePackSettings.resolve(url, sha);
        check(!pack.upgraded && (url == null ? pack.url == null : url.equals(pack.url))
                && (sha == null ? pack.sha1 == null : sha.equals(pack.sha1)), "custom URL or hash changed: " + url);
    }

    private static void testPackResolution() {
        upgraded(OLD_URL, OLD_SHA);
        upgraded("https://kinggrief.vercel.app/kinggrief-mega-rp.zip", OLD_SHA.toUpperCase());
        upgraded(OLD_URL, null);
        upgraded(V64_URL, V64_SHA);
        upgraded(V64_URL + "?v=64", "");
        upgraded("https://kinggrief.vercel.app/kinggrief-mega-rp-v65.zip", "fc61175f0692ab85486898160dce223097aa4382");
        upgraded("https://kinggrief.vercel.app/kinggrief-mega-rp-v65.zip?v=65", "");
        upgraded("https://kinggrief.vercel.app/kinggrief-mega-rp-v66.zip", "63461884bd573de19a043f11b5982a34536e47d9");
        upgraded("https://kinggrief.vercel.app/kinggrief-mega-rp-v66.zip?v=66", "");
        upgraded("https://kinggrief.vercel.app/kinggrief-mega-rp-v70.zip", "8286228d4a1e0a95f56ad5fd265f12d1a57827be");
        upgraded("https://kinggrief.vercel.app/kinggrief-mega-rp-v70.zip?v=70", "");
        unchanged("https://kinggrief.vercel.app/kinggrief-mega-rp-v70.zip", "customhash");
        unchanged("https://kinggrief.vercel.app/kinggrief-mega-rp-v66.zip", "customhash");
        unchanged("https://kinggrief.vercel.app/kinggrief-mega-rp-v65.zip", "customhash");
        unchanged(OLD_URL, "aabbcc"); // unknown checksum may describe a custom pack
        unchanged(V64_URL, OLD_SHA);
        unchanged("https://custom.example/kinggrief-mega-rp.zip?v=63", OLD_SHA);
        unchanged("https://kinggrief.vercel.app/kinggrief-mega-rp.zip?v=999", OLD_SHA);
        unchanged("https://kinggrief.vercel.app.evil.example/kinggrief-mega-rp.zip?v=63", OLD_SHA);
        unchanged("https://user@kinggrief.vercel.app/kinggrief-mega-rp.zip?v=63", OLD_SHA);
        unchanged("https://kinggrief.vercel.app/kinggrief-mega-rp.zip?v=63#fragment", OLD_SHA);
        unchanged("http://kinggrief.vercel.app/kinggrief-mega-rp.zip?v=63", OLD_SHA);
        unchanged("not a URL", null);
        unchanged("", "");
        unchanged(null, null);
        unchanged(ResourcePackSettings.LATEST_URL, ResourcePackSettings.LATEST_SHA1);
    }

    public static void main(String[] args) {
        testItemRepair();
        testPackResolution();
        System.out.println("Modern item migration + pack resolution: " + assertions + " assertions OK");
    }
}
