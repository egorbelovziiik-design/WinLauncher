package net.kinggrief.core;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ItemFactory {
    public static final NamespacedKey ITEM_TAG_KEY = new NamespacedKey("kinggrief", "custom_id");

    private ItemFactory() {}

    public static ItemStack make(Material mat, int amount, String name, List<String> lore, boolean glow, String customId, String modelId, int cmdData) {
        ItemStack it = new ItemStack(mat != null ? mat : Material.STONE, Math.max(1, amount));
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            if (name != null) m.setDisplayName(ColorUtil.c(name));
            if (lore != null && !lore.isEmpty()) {
                List<String> cl = new ArrayList<String>(lore.size());
                for (String s : lore) cl.add(ColorUtil.c(s));
                m.setLore(cl);
            }
            if (glow) {
                Enchantment unb = ColorUtil.ench("unbreaking", "DURABILITY");
                if (unb != null) m.addEnchant(unb, 1, true);
                m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            if (customId != null && !customId.isEmpty()) {
                m.getPersistentDataContainer().set(ITEM_TAG_KEY, PersistentDataType.STRING, customId);
            }
            if (modelId != null || cmdData > 0) {
                ColorUtil.applyCustomItemModel(m, modelId, cmdData);
            }
            it.setItemMeta(m);
        }
        return it;
    }

    public static ItemStack icon(Material mat, String name, String... loreLines) {
        return make(mat, 1, name, Arrays.asList(loreLines), false, null, null, 0);
    }

    public static ItemStack glowIcon(Material mat, String name, String... loreLines) {
        return make(mat, 1, name, Arrays.asList(loreLines), true, null, null, 0);
    }

    public static String getCustomId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta m = item.getItemMeta();
        if (m == null) return null;
        if (m.getPersistentDataContainer().has(ITEM_TAG_KEY, PersistentDataType.STRING)) {
            return m.getPersistentDataContainer().get(ITEM_TAG_KEY, PersistentDataType.STRING);
        }
        // Fallback by display name for kits/commands
        String dn = m.hasDisplayName() ? m.getDisplayName() : "";
        if (dn.contains("Божья Аура") || dn.contains("Божья_Аура")) return "god_aura";
        if (dn.contains("Дезориентация")) return "dezor";
        if (dn.contains("Явная Пыль") || dn.contains("Явная_Пыль")) return "yavnaya_pyl";
        if (dn.contains("Трапка")) return "trapka";
        if (dn.contains("Пласт")) return "plast";
        if (dn.contains("Леденец")) return "ny_candy";
        if (dn.contains("Андромед")) return "sphere_andromeda";
        if (dn.contains("Химер")) return "sphere_chimera";
        if (dn.contains("Талисман Крушителя")) return "talisman_krush";
        if (dn.contains("Вечной Мерзлоты")) return "talisman_frost";
        if (dn.contains("Бур")) return "drill_3x3";
        if (dn.contains("Смерч")) return "fire_tornado";
        if (dn.contains("Бомба Гринча")) return "grinch_bomb";
        return null;
    }

    // ================= CUSTOM REALLYWORLD ARTIFACTS =================
    public static ItemStack menuStar() {
        return make(Material.NETHER_STAR, 1, "&#80D8FF❄ &#FFD740&lМЕНЮ СЕРВЕРА KINGGRIEF &#80D8FF❄ &7(ПКМ)",
                Arrays.asList("&7Быстрый доступ ко всем разделам:", "&f• Спавн, РТП, Магазин, Кейсы, Киты", "&f• Кланы, Аукцион, Работы, Боевой Пропуск", "", "&#00E676▸ Нажми ПКМ, чтобы открыть!"),
                true, "menu_star", null, 0);
    }

    public static ItemStack sphereAndromeda() {
        return make(Material.TOTEM_OF_UNDYING, 1, "&#E040FB&l🔮 Сфера Андромеды [MAX]",
                Arrays.asList("&8Легендарный артефакт ReallyWorld", "", "&7Когда в &fлевой руке&7:", " &#FF5252• Сила II", " &#40C4FF• Скорость II", " &#FFD740• Огнестойкость I"),
                true, "sphere_andromeda", "andromeda_sphere", 1001);
    }

    public static ItemStack sphereChimera() {
        return make(Material.TOTEM_OF_UNDYING, 1, "&#FF1744&l🧪 Сфера Химеры [III]",
                Arrays.asList("&8Боевой артефакт ReallyWorld", "", "&7Когда в &fлевой руке&7:", " &#FF5252• Сила II", " &#69F0AE• Прыгучесть II", " &#FFD740• Огнестойкость I"),
                true, "sphere_chimera", "chimera_sphere", 1002);
    }

    public static ItemStack talismanKrush() {
        return make(Material.TOTEM_OF_UNDYING, 1, "&#FF6D00&l🔥 Талисман Крушителя",
                Arrays.asList("&8Древний талисман арены", "", "&7Когда в &fлевой руке&7:", " &#FF5252• Сила II", " &#00E676• Сопротивление I", " &#FF4081• Регенерация I"),
                true, "talisman_krush", "krush_talisman", 1003);
    }

    public static ItemStack talismanFrost() {
        return make(Material.TOTEM_OF_UNDYING, 1, "&#80D8FF&l❄ Талисман Вечной Мерзлоты",
                Arrays.asList("&8Новогодний артефакт Деда Мороза", "", "&7Когда в &fлевой руке&7:", " &#80D8FF• Скорость III", " &#00E676• Сопротивление I", " &#FFFFFF• Ночное зрение"),
                true, "talisman_frost", "frost_talisman", 1004);
    }

    public static ItemStack godAura(int amt) {
        return make(Material.BLAZE_POWDER, amt, "&#FFEE58&l✨ Божья Аура",
                Arrays.asList("&7ПКМ — снимает все негативные эффекты", "&7и даёт &cСилу II &7+ &dРегенерацию III &7+ &6Огнестойкость!"),
                true, "god_aura", null, 1005);
    }

    public static ItemStack dezor(int amt) {
        return make(Material.ENDER_EYE, amt, "&#D946EF&l👁 Дезориентация",
                Arrays.asList("&7ПКМ — ослепляет и сковывает всех", "&7врагов в радиусе &f10 блоков &7на 8 секунд!"),
                true, "dezor", null, 1006);
    }

    public static ItemStack yavnayaPyl(int amt) {
        return make(Material.SUGAR, amt, "&f&l💨 Явная Пыль",
                Arrays.asList("&7ПКМ — подсвечивает всех невидимок", "&7в радиусе &f20 блоков &7и даёт &bСкорость II!"),
                true, "yavnaya_pyl", null, 1007);
    }

    public static ItemStack fireTornado(int amt) {
        return make(Material.FIRE_CHARGE, amt, "&#FF5722&l🌪 Огненный Смерч",
                Arrays.asList("&7ПКМ — поджигает и подбрасывает", "&7врагов вокруг огненной волной!"),
                true, "fire_tornado", null, 1008);
    }

    public static ItemStack trapka(int amt) {
        return make(Material.NETHERITE_SCRAP, amt, "&#A855F7&l⛓ Трапка 5×5",
                Arrays.asList("&7ПКМ в выживании — мгновенно создаёт", "&7неразрушимую клетку 5×5 на &f15 секунд&7!"),
                true, "trapka", "trapka_cube", 1009);
    }

    public static ItemStack plast(int amt) {
        return make(Material.DRIED_KELP, amt, "&#94A3B8&l🧱 Пласт 5×5",
                Arrays.asList("&7ПКМ в выживании — создаёт под ногами", "&7обсидиановую платформу 5×5 на &f15 секунд&7!"),
                true, "plast", null, 1010);
    }

    public static ItemStack grinchBomb(int amt) {
        return make(Material.SNOWBALL, amt, "&#69F0AE&l❄ Ледяная Бомба Гринча",
                Arrays.asList("&7ПКМ — замораживает врагов вокруг", "&7ледяным взрывом на 6 секунд!"),
                true, "grinch_bomb", null, 1011);
    }

    public static ItemStack nyCandy(int amt) {
        return make(Material.COOKIE, amt, "&#FF4081&l🍭 Новогодний Леденец",
                Arrays.asList("&7ПКМ — даёт &bСкорость III &7и мгновенное исцеление!"),
                true, "ny_candy", null, 1012);
    }

    public static ItemStack drill3x3() {
        ItemStack pick = make(Material.NETHERITE_PICKAXE, 1, "&#FF9100&l⛏ Бур Крушителя [3×3]",
                Arrays.asList("&7Копает куб &e3×3 &7камня и руды за один удар!", "&7Эффективность VI · Удача III · Прочность V"),
                false, "drill_3x3", null, 1013);
        ItemMeta m = pick.getItemMeta();
        if (m != null) {
            addEnch(m, "efficiency", "DIG_SPEED", 6);
            addEnch(m, "fortune", "LOOT_BONUS_BLOCKS", 3);
            addEnch(m, "unbreaking", "DURABILITY", 5);
            pick.setItemMeta(m);
        }
        return pick;
    }

    public static ItemStack claimBlock(Material ore, int radius, String title) {
        return make(ore, 1, title,
                Arrays.asList("&7Поставь этот блок на землю в выживании,", "&7чтобы создать приват &e" + radius + "×" + radius + " блоков&7!", "&7Команды: &f/rg info&7, &f/rg add <ник>"),
                true, "claim_" + radius, null, 0);
    }

    private static void addEnch(ItemMeta m, String modern, String legacy, int lvl) {
        Enchantment e = ColorUtil.ench(modern, legacy);
        if (e != null) m.addEnchant(e, lvl, true);
    }

    private static ItemStack gear(Material mat, String name, int protOrSharp, int unb, int fireOrFort) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            if (name != null) m.setDisplayName(ColorUtil.c(name));
            String n = mat.name();
            if (n.endsWith("_SWORD") || n.endsWith("_AXE")) {
                addEnch(m, "sharpness", "DAMAGE_ALL", protOrSharp);
                if (fireOrFort > 0) addEnch(m, "fire_aspect", "FIRE_ASPECT", fireOrFort);
            } else if (n.endsWith("_PICKAXE")) {
                addEnch(m, "efficiency", "DIG_SPEED", protOrSharp);
                if (fireOrFort > 0) addEnch(m, "fortune", "LOOT_BONUS_BLOCKS", fireOrFort);
            } else {
                addEnch(m, "protection", "PROTECTION_ENVIRONMENTAL", protOrSharp);
            }
            if (unb > 0) addEnch(m, "unbreaking", "DURABILITY", unb);
            if (protOrSharp >= 4) addEnch(m, "mending", "MENDING", 1);
            it.setItemMeta(m);
        }
        return it;
    }

    public static void giveKitItems(Player p, String kitId) {
        String k = kitId.toLowerCase();
        List<ItemStack> items = new ArrayList<ItemStack>();
        if ("start".equals(k)) {
            items.add(gear(Material.IRON_SWORD, "&fМеч Новичка", 1, 1, 0));
            items.add(gear(Material.IRON_PICKAXE, "&fКирка Новичка", 2, 1, 0));
            items.add(new ItemStack(Material.IRON_AXE));
            items.add(gear(Material.IRON_HELMET, null, 1, 1, 0));
            items.add(gear(Material.IRON_CHESTPLATE, null, 1, 1, 0));
            items.add(gear(Material.IRON_LEGGINGS, null, 1, 1, 0));
            items.add(gear(Material.IRON_BOOTS, null, 1, 1, 0));
            items.add(new ItemStack(Material.COOKED_BEEF, 32));
            items.add(new ItemStack(Material.OAK_LOG, 16));
            items.add(claimBlock(Material.COAL_ORE, 10, "&a🛡 Приват 10×10 блоков"));
        } else if ("newyear".equals(k)) {
            items.add(gear(Material.DIAMOND_SWORD, "&#80D8FF❄ Новогодний Клинок ❄", 3, 3, 1));
            items.add(gear(Material.DIAMOND_PICKAXE, "&#80D8FF❄ Ледяная Кирка ❄", 4, 3, 2));
            items.add(new ItemStack(Material.GOLDEN_APPLE, 8));
            items.add(nyCandy(4));
            items.add(grinchBomb(4));
            items.add(new ItemStack(Material.FIREWORK_ROCKET, 32));
        } else if ("pegas".equals(k) || "cobra".equals(k) || "hero".equals(k) || "bunny".equals(k) || "titan".equals(k) || "rabbit".equals(k)) {
            int lv = ("rabbit".equals(k) || "titan".equals(k)) ? 4 : 3;
            items.add(gear(Material.DIAMOND_SWORD, "&#38BDF8⚔ Клинок " + k.toUpperCase(), lv, 3, 1));
            items.add(gear(Material.DIAMOND_PICKAXE, "&#38BDF8⛏ Кирка " + k.toUpperCase(), 4, 3, 2));
            items.add(gear(Material.DIAMOND_HELMET, null, lv, 2, 0));
            items.add(gear(Material.DIAMOND_CHESTPLATE, null, lv, 2, 0));
            items.add(gear(Material.DIAMOND_LEGGINGS, null, lv, 2, 0));
            items.add(gear(Material.DIAMOND_BOOTS, null, lv, 2, 0));
            items.add(new ItemStack(Material.GOLDEN_APPLE, 12));
            items.add(new ItemStack(Material.ENDER_PEARL, 8));
        } else if ("vampire".equals(k) || "tiger".equals(k) || "dracula".equals(k) || "god".equals(k)) {
            boolean neth = "dracula".equals(k) || "god".equals(k);
            items.add(gear(neth ? Material.NETHERITE_SWORD : Material.DIAMOND_SWORD, "&#FACC15⚡ Меч " + k.toUpperCase(), 4, 3, 2));
            items.add(gear(neth ? Material.NETHERITE_PICKAXE : Material.DIAMOND_PICKAXE, null, 4, 3, 2));
            items.add(gear(neth ? Material.NETHERITE_HELMET : Material.DIAMOND_HELMET, null, 4, 3, 0));
            items.add(gear(neth ? Material.NETHERITE_CHESTPLATE : Material.DIAMOND_CHESTPLATE, null, 4, 3, 0));
            items.add(gear(Material.DIAMOND_LEGGINGS, null, 4, 3, 0));
            items.add(gear(Material.DIAMOND_BOOTS, null, 4, 3, 0));
            items.add(new ItemStack(Material.GOLDEN_APPLE, 20));
            items.add(new ItemStack(Material.TOTEM_OF_UNDYING, 1));
            items.add(godAura(2));
        } else {
            // KING, OVERLORD, IMPERATOR, DRAGON, BULL, SPONSOR, MEDIA, KP, DKP, DMODER..DADMIN, OPKA, CURATOR, DOWNER, OWNER
            boolean top = "opka".equals(k) || "downer".equals(k) || "owner".equals(k) || "curator".equals(k) || "dkp".equals(k);
            String tag = k.toUpperCase();
            items.add(gear(Material.NETHERITE_SWORD, "&#FFD740👑 Королевский Меч " + tag, 5, 3, 2));
            items.add(top ? drill3x3() : gear(Material.NETHERITE_PICKAXE, "&#FFD740⛏ Кирка " + tag, 5, 3, 3));
            items.add(gear(Material.NETHERITE_HELMET, "&#A855F7Шлем " + tag, 4, 3, 0));
            items.add(gear(Material.NETHERITE_CHESTPLATE, "&#A855F7Нагрудник " + tag, 4, 3, 0));
            items.add(gear(Material.NETHERITE_LEGGINGS, "&#A855F7Поножи " + tag, 4, 3, 0));
            items.add(gear(Material.NETHERITE_BOOTS, "&#A855F7Ботинки " + tag, 4, 3, 0));
            items.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, top ? 12 : 4));
            items.add(new ItemStack(Material.GOLDEN_APPLE, 32));
            items.add(top ? sphereAndromeda() : talismanKrush());
            items.add(godAura(top ? 8 : 3));
            items.add(dezor(top ? 6 : 2));
            items.add(yavnayaPyl(top ? 6 : 3));
            items.add(trapka(top ? 4 : 2));
            items.add(plast(top ? 6 : 3));
            if (top) {
                items.add(claimBlock(Material.NETHERITE_BLOCK, 60, "&#FACC15👑 Приват 60×60 блоков"));
            }
        }
        for (ItemStack it : items) {
            for (ItemStack rem : p.getInventory().addItem(it).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), rem);
            }
        }
    }
}
