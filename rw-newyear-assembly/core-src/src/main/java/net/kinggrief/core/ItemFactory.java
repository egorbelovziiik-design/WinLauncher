package net.kinggrief.core;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ItemFactory {
    public static final NamespacedKey ITEM_TAG_KEY = new NamespacedKey("kinggrief", "custom_id");
    public static final NamespacedKey CHARGES_KEY = new NamespacedKey("kinggrief", "totem_charges");

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
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return null;
        ItemMeta m = item.getItemMeta();
        if (m == null) return null;
        if (m.getPersistentDataContainer().has(ITEM_TAG_KEY, PersistentDataType.STRING)) {
            return m.getPersistentDataContainer().get(ITEM_TAG_KEY, PersistentDataType.STRING);
        }
        return null;
    }

    public static int getTotemCharges(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        ItemMeta m = item.getItemMeta();
        if (m == null) return 0;
        Integer v = m.getPersistentDataContainer().get(CHARGES_KEY, PersistentDataType.INTEGER);
        return v != null ? v.intValue() : 1;
    }

    // ================= МЕНЮ СЕРВЕРА =================
    public static ItemStack menuStar() {
        return make(Material.NETHER_STAR, 1, "&#80D8FF❄ &#FFD740&lМЕНЮ KINGGRIEF FT × RW &#80D8FF❄ &7(ПКМ)",
                Arrays.asList(
                        "&7Быстрый доступ ко всем системам v5.0:",
                        "&f• Выход в мир выживания: &#00E676/rtp &7(Спавн — отдельный мир!)",
                        "&f• 8-категорийный Магазин: &#00E5FF/shop &7+ Рилики: &#FFD740/rshop",
                        "&f• Бафовар (до 8 зелий за 5 000$): &#FF9100/bafs &7+ Скины Трапок: &#E040FB/skins",
                        "&f• Киты с 2 стаками перок: &#69F0AE/kits &7+ Головы за награду: &#FF5252/bounty",
                        "",
                        "&#00E676▸ Нажми ПКМ, чтобы открыть главное меню!"
                ),
                true, "menu_star", null, 0);
    }

    // ================= 8 BLENDER 3D АНИМИРОВАННЫХ ШАРОВ (ЛЕВАЯ РУКА) =================
    public static ItemStack sharTitana() {
        return make(Material.TOTEM_OF_UNDYING, 1, "&#FF9100&l🔮 3D Шар Титана [FT×RW]",
                Arrays.asList("&8Blender Cycles 3D Анимированный Шар", "", "&7Когда в &fлевой руке&7:",
                        " &#FF5252• +4 ❤ Максимального Здоровья", " &#FF6D00• Сила I", " &#00E676• Сопротивление I"),
                true, "shar_titana", "shar_titana", 1101);
    }

    public static ItemStack sharAndromedy() {
        return make(Material.TOTEM_OF_UNDYING, 1, "&#E040FB&l🔮 3D Шар Андромеды [FT×RW]",
                Arrays.asList("&8Blender Cycles 3D Анимированный Шар", "", "&7Когда в &fлевой руке&7:",
                        " &#FF5252• Сила II", " &#40C4FF• Скорость II", " &#69F0AE• Прыгучесть II"),
                true, "shar_andromedy", "shar_andromedy", 1102);
    }

    public static ItemStack sharChimery() {
        return make(Material.TOTEM_OF_UNDYING, 1, "&#FF1744&l🔮 3D Шар Химеры [FT×RW]",
                Arrays.asList("&8Blender Cycles 3D Анимированный Шар", "", "&7Когда в &fлевой руке&7:",
                        " &#FF5252• Сила II", " &#FFD740• Огнестойкость I", " &#FF4081• Регенерация I"),
                true, "shar_chimery", "shar_chimery", 1103);
    }

    public static ItemStack sharApollona() {
        return make(Material.TOTEM_OF_UNDYING, 1, "&#FFD740&l☀ 3D Шар Аполлона [FT×RW]",
                Arrays.asList("&8Blender Cycles 3D Анимированный Шар", "", "&7Когда в &fлевой руке&7:",
                        " &#FFD740• Спешка II", " &#40C4FF• Скорость II", " &#FFFFFF• Ночное зрение"),
                true, "shar_apollona", "shar_apollona", 1104);
    }

    public static ItemStack sharAida() {
        return make(Material.TOTEM_OF_UNDYING, 1, "&#DC2626&l🔥 3D Шар Аида [FT×RW]",
                Arrays.asList("&8Blender Cycles 3D Анимированный Шар", "", "&7Когда в &fлевой руке&7:",
                        " &#FF1744• Сила II", " &#FFD740• Огнестойкость I"),
                true, "shar_aida", "shar_aida", 1105);
    }

    public static ItemStack sharOsirisa() {
        return make(Material.TOTEM_OF_UNDYING, 1, "&#00E5FF&l🌊 3D Шар Осириса [FT×RW]",
                Arrays.asList("&8Blender Cycles 3D Анимированный Шар", "", "&7Когда в &fлевой руке&7:",
                        " &#FF5252• +2 ❤ Здоровья", " &#FF4081• Регенерация II", " &#00E676• Сопротивление I"),
                true, "shar_osirisa", "shar_osirisa", 1106);
    }

    public static ItemStack sharAresa() {
        return make(Material.TOTEM_OF_UNDYING, 1, "&#B91C1C&l⚔ 3D Шар Ареса [FT×RW]",
                Arrays.asList("&8Blender Cycles 3D Боевой Шар", "", "&7Когда в &fлевой руке&7:",
                        " &#FF1744• Сила III (Сокрушительный урон!)", " &#40C4FF• Скорость I"),
                true, "shar_aresa", "shar_aresa", 1108);
    }

    public static ItemStack sharSanty() {
        return make(Material.TOTEM_OF_UNDYING, 1, "&#80D8FF&l❄ 3D Новогодний Шар Санты [2026]",
                Arrays.asList("&8Эксклюзивный Blender 3D Зимний Шар", "", "&7Когда в &fлевой руке&7:",
                        " &#FF5252• +6 ❤ Максимального Здоровья", " &#FF1744• Сила II", " &#40C4FF• Скорость II", " &#FF4081• Регенерация I"),
                true, "shar_santy", "shar_santy", 1107);
    }

    // ================= 7 BLENDER 3D ТАЛИСМАНОВ =================
    public static ItemStack talKrushitelya() {
        return make(Material.TOTEM_OF_UNDYING, 1, "&#FF6D00&l🛡 3D Талисман Крушителя [FT×RW]",
                Arrays.asList("&8Blender 3D Анимированный Талисман", "",
                        "&7При срабатывании тотема:", " &#FF6D00• Взрывная волна Крушителя + 8 Золотых ❤ + Сила III"),
                true, "tal_krushitelya", "tal_krushitelya", 1201);
    }

    public static ItemStack talGrani() {
        return make(Material.TOTEM_OF_UNDYING, 1, "&#FACC15&l🛡 3D Талисман Грани [FT×RW]",
                Arrays.asList("&8Blender 3D Анимированный Талисман", "",
                        "&7При срабатывании тотема:", " &#FACC15• Несокрушимый Щит (+10 Золотых ❤ + Сопротивление III)"),
                true, "tal_grani", "tal_grani", 1202);
    }

    public static ItemStack talDedala() {
        return make(Material.TOTEM_OF_UNDYING, 1, "&#38BDF8&l🛡 3D Талисман Дедала [FT×RW]",
                Arrays.asList("&8Blender 3D Анимированный Талисман", "",
                        "&7При срабатывании тотема:", " &#38BDF8• Крылья Дедала (Скорость IV + Прыгучесть III)"),
                true, "tal_dedala", "tal_dedala", 1203);
    }

    public static ItemStack talTritona() {
        return make(Material.TOTEM_OF_UNDYING, 1, "&#00E5FF&l🔱 3D Талисман Тритона [FT×RW]",
                Arrays.asList("&8Blender 3D Анимированный Талисман", "",
                        "&7При срабатывании тотема:", " &#00E5FF• Волна Океана (отталкивает врагов + Реген III)"),
                true, "tal_tritona", "tal_tritona", 1204);
    }

    public static ItemStack talFeniksa(int charges) {
        int ch = Math.max(1, Math.min(3, charges));
        ItemStack it = make(Material.TOTEM_OF_UNDYING, 1, "&#FFAB00&l🦅 3D Талисман Феникса [" + ch + "/3]",
                Arrays.asList("&8Многоразовый Blender 3D Тотем Бессмертия", "",
                        "&7Осталось зарядов воскрешения: &#00E676&l" + ch + " / 3",
                        "&7При срабатывании тотема:", " &#FFAB00• Полное исцеление 20❤ и НЕ пропадает до 0/3!"),
                true, "tal_feniksa", "tal_feniksa", 1205);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.getPersistentDataContainer().set(CHARGES_KEY, PersistentDataType.INTEGER, Integer.valueOf(ch));
            it.setItemMeta(m);
        }
        return it;
    }

    public static ItemStack talEchidny() {
        return make(Material.TOTEM_OF_UNDYING, 1, "&#22C55E&l🐍 3D Талисман Ехидны [FT×RW]",
                Arrays.asList("&8Blender 3D Ядовитый Талисман", "",
                        "&7При срабатывании тотема:", " &#22C55E• Облако Ехидны (Отравление III + Слабость врагам)"),
                true, "tal_echidny", "tal_echidny", 1206);
    }

    public static ItemStack talStuzhi() {
        return make(Material.TOTEM_OF_UNDYING, 1, "&#80D8FF&l❄ 3D Новогодний Тотем Стужи [2026]",
                Arrays.asList("&8Blender 3D Зимний Талисман", "",
                        "&7При срабатывании тотема:", " &#80D8FF• Замораживает всех врагов вокруг на 6 сек!"),
                true, "tal_stuzhi", "tal_stuzhi", 1207);
    }

    // ================= 6 КАСТОМНЫХ BLENDER 3D ЭЛИТР С РАЗНЫМИ БАФФАМИ =================
    private static ItemStack makeBuffedElytra(String name, String id, int cmd, int protLvl, List<String> lore) {
        ItemStack el = make(Material.ELYTRA, 1, name, lore, false, id, id, cmd);
        ItemMeta m = el.getItemMeta();
        if (m != null) {
            addEnch(m, "protection", "PROTECTION_ENVIRONMENTAL", protLvl);
            addEnch(m, "unbreaking", "DURABILITY", 5);
            addEnch(m, "mending", "MENDING", 1);
            el.setItemMeta(m);
        }
        return el;
    }

    public static ItemStack elytraWind() {
        return makeBuffedElytra("&#80D8FF&l🪽 3D Элитры Ветра [FT×RW]", "elytra_wind", 1401, 4,
                Arrays.asList("&8Blender 3D Кастомные Элитры", "",
                        "&7Когда надеты в слот нагрудника:",
                        " &#40C4FF• Постоянная Скорость II",
                        " &#80D8FF• Плавное падение на Shift в воздухе",
                        " &#B3E5FC• Защита IV · Прочность V · Починка"));
    }

    public static ItemStack elytraPhoenix() {
        return makeBuffedElytra("&#FF6D00&l🔥 3D Элитры Феникса [FT×RW]", "elytra_phoenix", 1402, 4,
                Arrays.asList("&8Blender 3D Кастомные Элитры", "",
                        "&7Когда надеты в слот нагрудника:",
                        " &#FF5252• +2 ❤ Максимального Здоровья",
                        " &#FFD740• Постоянная Огнестойкость + Огненный шлейф",
                        " &#FF9100• Поджигают атакующих в ближнем бою!",
                        " &#B3E5FC• Защита IV · Прочность V · Починка"));
    }

    public static ItemStack elytraVampire() {
        return makeBuffedElytra("&#FF1744&l🦇 3D Элитры Вампира [FT×RW]", "elytra_vampire", 1403, 4,
                Arrays.asList("&8Blender 3D Кастомные Элитры", "",
                        "&7Когда надеты в слот нагрудника:",
                        " &#FF1744• Постоянная Сила I + Ночное Зрение",
                        " &#F50057• Вампиризм: исцеляют 20% от урона в PvP!",
                        " &#B3E5FC• Защита IV · Прочность V · Починка"));
    }

    public static ItemStack elytraThunder() {
        return makeBuffedElytra("&#FFD740&l⚡ 3D Элитры Грома [FT×RW]", "elytra_thunder", 1404, 4,
                Arrays.asList("&8Blender 3D Кастомные Элитры", "",
                        "&7Когда надеты в слот нагрудника:",
                        " &#40C4FF• Скорость II (Сила II во время полёта!)",
                        " &#FFD740• 18% шанс ударить врага Молнией при ударе!",
                        " &#B3E5FC• Защита IV · Прочность V · Починка"));
    }

    public static ItemStack elytraTitan() {
        return makeBuffedElytra("&#F59E0B&l🛡 3D Элитры Крушителя [Броня]", "elytra_titan", 1405, 5,
                Arrays.asList("&8Blender 3D Тяжёлые Боевые Элитры", "",
                        "&7Когда надеты в слот нагрудника:",
                        " &#FF5252• +4 ❤ Максимального Здоровья",
                        " &#00E676• Постоянное Сопротивление I (как нагрудник!)",
                        " &#FFD740• Защита V · Прочность V · Починка"));
    }

    public static ItemStack elytraSanta() {
        return makeBuffedElytra("&#00E5FF&l❄ 3D Императорские Элитры Санты [2026]", "elytra_santa", 1406, 5,
                Arrays.asList("&8Топовые Blender 3D Новогодние Элитры", "",
                        "&7Когда надеты в слот нагрудника:",
                        " &#FF5252• +6 ❤ Максимального Здоровья",
                        " &#FF1744• Сила II  &#40C4FF• Скорость II  &#FF4081• Регенерация I",
                        " &#80D8FF• Ледяной шлейф + Защита V · Прочность V · Починка"));
    }

    // ================= 8 БОЕВЫХ PvP ПРЕДМЕТОВ FUN-TIME × REALLYWORLD =================
    public static ItemStack plast(int amt) {
        return make(Material.DRIED_KELP, amt, "&#A855F7&l🧱 Пласт 5×5×2 [FT]",
                Arrays.asList("&8Боевой расходник FunTime × ReallyWorld", "",
                        "&7ПКМ — мгновенно создаёт нерушимый",
                        "&7пласт &d5×5×2 &7по направлению взгляда",
                        "&7(или под ногами при взгляде вниз) на &e12 сек&7!",
                        "&8Перезарядка: 10 сек."),
                true, "plast", "plast", 1501);
    }

    public static ItemStack trapka(int amt) {
        return make(Material.NETHERITE_SCRAP, amt, "&#FF1744&l⛓ Трапка 5×5×5 [FT]",
                Arrays.asList("&8Боевая клетка FunTime × ReallyWorld", "",
                        "&7ПКМ — закрывает вас и врагов в нерушимой",
                        "&7клетке &c5×5×5 &7на &e15 сек&7!",
                        "&7• Применяет ваш активный &dСкин Трапки (&f/skins&d)&7,",
                        "&7  накладывающий мощный дебафф на врагов внутри!",
                        "&8Перезарядка: 20 сек."),
                true, "trapka", "trapka", 1502);
    }

    public static ItemStack dezorient(int amt) {
        return make(Material.ENDER_EYE, amt, "&#E040FB&l👁 Дезориентация [FT]",
                Arrays.asList("&8Боевой расходник FunTime × ReallyWorld", "",
                        "&7ПКМ — ослепляет, замедляет и дезориентирует",
                        "&7всех врагов в радиусе &d10 блоков &7на &e7 сек&7!",
                        "&8Перезарядка: 20 сек."),
                true, "dezorient", "dezorient", 1503);
    }

    public static ItemStack yavnayaPyl(int amt) {
        return make(Material.SUGAR, amt, "&#00E5FF&l✨ Явная Пыль [FT]",
                Arrays.asList("&8Боевой расходник FunTime × ReallyWorld", "",
                        "&7ПКМ — выдаёт &bСвечение &7и снимает Невидимость",
                        "&7со всех игроков в радиусе &b25 блоков &7на &e15 сек&7!",
                        "&8Перезарядка: 15 сек."),
                true, "yavnaya_pyl", "yavnaya_pyl", 1504);
    }

    public static ItemStack bozhyaAura(int amt) {
        return make(Material.PHANTOM_MEMBRANE, amt, "&#FFD740&l☀ Божья Аура [FT]",
                Arrays.asList("&8Священный расходник FunTime × ReallyWorld", "",
                        "&7ПКМ — мгновенно снимает ВСЕ дебаффы",
                        "&7и даёт &dРегенерацию III &7+ &aСопротивление II &7на &e6 сек&7!",
                        "&8Перезарядка: 30 сек."),
                true, "bozhya_aura", "bozhya_aura", 1505);
    }

    public static ItemStack ognennySmerch(int amt) {
        return make(Material.FIRE_CHARGE, amt, "&#FF6D00&l🌪 Огненный Смерч [FT]",
                Arrays.asList("&8Боевой расходник FunTime × ReallyWorld", "",
                        "&7ПКМ — создаёт огненный вихрь в радиусе &68 блоков&7:",
                        "&7поджигает врагов, отбрасывает и наносит &c6❤ урона&7!",
                        "&8Перезарядка: 18 сек."),
                true, "ognenny_smerch", "ognenny_smerch", 1506);
    }

    public static ItemStack snezokZamorozki(int amt) {
        return make(Material.SNOWBALL, amt, "&#80D8FF&l❄ Снежок Заморозки [FT×RW]",
                Arrays.asList("&8Метательный расходник FunTime × ReallyWorld", "",
                        "&7Брось во врага — накладывает &bМедлительность IV&7,",
                        "&7&7Слабость II &7и &bМороз &7на &e6 секунд&7!"),
                true, "snezok_zamorozki", "snezok_zamorozki", 1507);
    }

    public static ItemStack pautinnayaGranata(int amt) {
        return make(Material.SLIME_BALL, amt, "&#69F0AE&l🕸 Паутинная Граната [FT]",
                Arrays.asList("&8Тактический расходник FunTime × ReallyWorld", "",
                        "&7ПКМ — бросает гранату, опутывающую врагов",
                        "&7паутиной &a3×3 &7на месте падения на &e8 сек&7!",
                        "&8Перезарядка: 15 сек."),
                true, "pautinnaya_granata", "pautinnaya_granata", 1508);
    }

    // ================= ЗЕЛЬЯ И МИКСЫ БАФОВАРА (ДО 8 ЗЕЛИЙ ЗА 5 000$!) =================
    private static ItemStack buildCustomPotion(boolean splash, int amount, Color color, String name, String customId,
                                               List<String> lore, PotionEffect... effects) {
        ItemStack pot = new ItemStack(splash ? Material.SPLASH_POTION : Material.POTION, Math.max(1, amount));
        PotionMeta pm = (PotionMeta) pot.getItemMeta();
        if (pm != null) {
            pm.setDisplayName(ColorUtil.c(name));
            List<String> cl = new ArrayList<String>();
            for (String s : lore) cl.add(ColorUtil.c(s));
            pm.setLore(cl);
            pm.setColor(color);
            for (PotionEffect pe : effects) {
                if (pe != null) pm.addCustomEffect(pe, true);
            }
            pm.getPersistentDataContainer().set(ITEM_TAG_KEY, PersistentDataType.STRING, customId);
            pot.setItemMeta(pm);
        }
        return pot;
    }

    public static ItemStack bafovarCustomPotion(boolean splash, List<PotionEffect> effects, List<String> effectLabels) {
        List<String> lore = new ArrayList<String>();
        lore.add("&8Авторский Коктейль NPC Бафовара [" + effects.size() + "/8]");
        lore.add("");
        for (String lbl : effectLabels) {
            lore.add(" &f• " + lbl);
        }
        lore.add("");
        lore.add("&#FFD740Сварено у Бафовара за 5 000$ (/bafs)");
        ItemStack pot = new ItemStack(splash ? Material.SPLASH_POTION : Material.POTION, 1);
        PotionMeta pm = (PotionMeta) pot.getItemMeta();
        if (pm != null) {
            pm.setDisplayName(ColorUtil.c("&#FF9100&l⚗ Микс Бафовара [" + effects.size() + " эфф.]"));
            List<String> cl = new ArrayList<String>();
            for (String s : lore) cl.add(ColorUtil.c(s));
            pm.setLore(cl);
            pm.setColor(Color.fromRGB(255, 145, 0));
            for (PotionEffect pe : effects) {
                if (pe != null) pm.addCustomEffect(pe, true);
            }
            pm.getPersistentDataContainer().set(ITEM_TAG_KEY, PersistentDataType.STRING, "potion_bafovar_mix");
            pot.setItemMeta(pm);
        }
        return pot;
    }

    private static PotionEffect eff(String modern, String legacy, int sec, int amp) {
        PotionEffectType t = ColorUtil.potType(modern, legacy);
        return t != null ? new PotionEffect(t, sec * 20, amp) : null;
    }

    public static ItemStack potionKiller(int amt) {
        return buildCustomPotion(false, amt, Color.fromRGB(220, 20, 60),
                "&#FF1744&l🧪 Зелье Киллера [FT×RW]", "potion_killer",
                Arrays.asList("&8Боевой бафф Бафовара", "&c• Сила III (3:00)", "&b• Скорость III (3:00)", "&6• Огнестойкость (3:00)"),
                eff("strength", "INCREASE_DAMAGE", 180, 2),
                eff("speed", "SPEED", 180, 2),
                eff("fire_resistance", "FIRE_RESISTANCE", 180, 0));
    }

    public static ItemStack potionMedic(int amt) {
        return buildCustomPotion(false, amt, Color.fromRGB(255, 64, 129),
                "&#FF4081&l💖 Зелье Медика [FT×RW]", "potion_medic",
                Arrays.asList("&8Исцеляющий бафф Бафовара", "&d• Регенерация III (1:30)", "&e• Поглощение IV (+8 Золотых ❤)", "&c• Исцеление II"),
                eff("regeneration", "REGENERATION", 90, 2),
                eff("absorption", "ABSORPTION", 180, 3),
                eff("instant_health", "HEAL", 1, 1));
    }

    public static ItemStack potionFlash(int amt) {
        return buildCustomPotion(false, amt, Color.fromRGB(0, 229, 255),
                "&#00E5FF&l⚡ Зелье Флеша [FT×RW]", "potion_flash",
                Arrays.asList("&8Скоростной бафф Бафовара", "&b• Скорость IV (2:00)", "&e• Спешка III (2:00)", "&a• Прыгучесть II (2:00)"),
                eff("speed", "SPEED", 120, 3),
                eff("haste", "FAST_DIGGING", 120, 2),
                eff("jump_boost", "JUMP", 120, 1));
    }

    public static ItemStack potionAgent(int amt) {
        return buildCustomPotion(false, amt, Color.fromRGB(120, 144, 156),
                "&#B0BEC5&l🕵 Зелье Агента [FT×RW]", "potion_agent",
                Arrays.asList("&8Шпионский бафф Бафовара", "&f• Невидимость (5:00)", "&b• Скорость III (3:00)", "&9• Ночное Зрение (5:00)"),
                eff("invisibility", "INVISIBILITY", 300, 0),
                eff("speed", "SPEED", 180, 2),
                eff("night_vision", "NIGHT_VISION", 300, 0));
    }

    public static ItemStack potionChampagne(int amt) {
        return buildCustomPotion(false, amt, Color.fromRGB(255, 215, 64),
                "&#FFD740&l🥂 Новогоднее Шампанское [2026]", "potion_champagne",
                Arrays.asList("&8Праздничный бафф KINGGRIEF", "&c• Сила II (3:00)", "&b• Скорость III (3:00)", "&e• +6 Золотых Сердец", "&d• Регенерация II (2:00)"),
                eff("strength", "INCREASE_DAMAGE", 180, 1),
                eff("speed", "SPEED", 180, 2),
                eff("absorption", "ABSORPTION", 180, 2),
                eff("regeneration", "REGENERATION", 120, 1));
    }

    public static ItemStack potionBurp(int amt) {
        return buildCustomPotion(true, amt, Color.fromRGB(118, 255, 3),
                "&#76FF03&l🤢 Зелье Отрыжки [FT×RW]", "potion_burp",
                Arrays.asList("&8Взрывное дебафф-зелье", "&2• Отравление II (0:30)", "&7• Слабость II (0:45)", "&8• Замедление III (0:25)", "&0• Слепота (0:08)"),
                eff("poison", "POISON", 30, 1),
                eff("weakness", "WEAKNESS", 45, 1),
                eff("slowness", "SLOW", 25, 2),
                eff("blindness", "BLINDNESS", 8, 0));
    }

    public static ItemStack potionAcid(int amt) {
        return buildCustomPotion(true, amt, Color.fromRGB(170, 0, 255),
                "&#D500F9&l☣ Зелье Серной Кислоты [FT×RW]", "potion_acid",
                Arrays.asList("&8Взрывное кислотное зелье", "&5• Иссушение III (0:25)", "&4• Моментальный Урон II", "&7• Утомление II (0:30)"),
                eff("wither", "WITHER", 25, 2),
                eff("instant_damage", "HARM", 1, 1),
                eff("mining_fatigue", "SLOW_DIGGING", 30, 1));
    }

    public static ItemStack potionTurtle(int amt) {
        return buildCustomPotion(false, amt, Color.fromRGB(0, 150, 136),
                "&#26A69A&l🐢 Зелье Титана-Черепахи [FT×RW]", "potion_turtle",
                Arrays.asList("&8Непробиваемый бафф Бафовара", "&a• Сопротивление IV (0:40)", "&6• Огнестойкость (3:00)", "&9• Замедление II (0:40)"),
                eff("resistance", "DAMAGE_RESISTANCE", 40, 3),
                eff("fire_resistance", "FIRE_RESISTANCE", 180, 0),
                eff("slowness", "SLOW", 40, 1));
    }

    public static ItemStack potionVampire(int amt) {
        return buildCustomPotion(false, amt, Color.fromRGB(136, 14, 79),
                "&#AD1457&l🩸 Эликсир Вампира [FT×RW]", "potion_vampire",
                Arrays.asList("&8Кровавый отвар Бафовара", "&c• Сила III (2:00)", "&d• Регенерация III (1:00)", "&9• Ночное Зрение (4:00)"),
                eff("strength", "INCREASE_DAMAGE", 120, 2),
                eff("regeneration", "REGENERATION", 60, 2),
                eff("night_vision", "NIGHT_VISION", 240, 0));
    }

    // ================= BLENDER 3D ОРУЖИЕ, ИНСТРУМЕНТЫ И РЕСУРСЫ =================
    public static ItemStack pickDjamshut() {
        ItemStack pick = make(Material.NETHERITE_PICKAXE, 1, "&#FF9100&l⛏ 3D Кирка Джамшута [Бульдозер 3×3]",
                Arrays.asList("&8Blender 3D Легендарный инструмент",
                        "&7• Зачарование: &#FF9100Бульдозер II &7(копает 3×3!)",
                        "&7• Зачарование: &#FF5252Авто-Плавка &7(плавит руду!)",
                        "&7• Эффективность VI · Удача IV · Прочность V"),
                false, "pick_djamshut", "pick_djamshut", 1301);
        ItemMeta m = pick.getItemMeta();
        if (m != null) {
            addEnch(m, "efficiency", "DIG_SPEED", 6);
            addEnch(m, "fortune", "LOOT_BONUS_BLOCKS", 4);
            addEnch(m, "unbreaking", "DURABILITY", 5);
            addEnch(m, "mending", "MENDING", 1);
            pick.setItemMeta(m);
        }
        return pick;
    }

    public static ItemStack pickSpawner() {
        return make(Material.GOLDEN_PICKAXE, 1, "&#FACC15&l✨ 3D Божье Прикосновение [Спавнера]",
                Arrays.asList("&8Blender 3D Уникальная кирка",
                        "&7Позволяет &eсломать и забрать Рассадник Мобов",
                        "&7в инвентарь с сохранением типа моба!",
                        "&cОдноразовый предмет (1 прочность на спавнер)."),
                true, "pick_spawner", "pick_spawner", 1302);
    }

    public static ItemStack swordDracula() {
        ItemStack sw = make(Material.NETHERITE_SWORD, 1, "&#FF1744&l🩸 3D Меч Дракулы [Вампиризм II]",
                Arrays.asList("&8Blender 3D Кровавый клинок",
                        "&7• Зачарование: &#FF1744Вампиризм II &7(лечит при ударе!)",
                        "&7• Острота VI · Заговор Огня II · Прочность V"),
                false, "sword_dracula", "sword_dracula", 1303);
        ItemMeta m = sw.getItemMeta();
        if (m != null) {
            addEnch(m, "sharpness", "DAMAGE_ALL", 6);
            addEnch(m, "fire_aspect", "FIRE_ASPECT", 2);
            addEnch(m, "unbreaking", "DURABILITY", 5);
            addEnch(m, "mending", "MENDING", 1);
            sw.setItemMeta(m);
        }
        return sw;
    }

    public static ItemStack swordKrush() {
        ItemStack sw = make(Material.NETHERITE_SWORD, 1, "&#FF6D00&l⚔ 3D Меч Крушителя [Острота VII]",
                Arrays.asList("&8Blender 3D Топовый донат-клинок",
                        "&7• Зачарование: &#76FF03Окисление II &7(ломает броню врага)",
                        "&7• Острота VII · Заговор Огня II · Прочность V"),
                false, "sword_krush", "sword_krush", 1304);
        ItemMeta m = sw.getItemMeta();
        if (m != null) {
            addEnch(m, "sharpness", "DAMAGE_ALL", 7);
            addEnch(m, "fire_aspect", "FIRE_ASPECT", 2);
            addEnch(m, "unbreaking", "DURABILITY", 5);
            addEnch(m, "mending", "MENDING", 1);
            sw.setItemMeta(m);
        }
        return sw;
    }

    public static ItemStack bowIceRw() {
        ItemStack bow = make(Material.BOW, 1, "&#80D8FF&l🏹 3D Ледяной Лук Снайпера [FT×RW]",
                Arrays.asList("&8Blender 3D Зимнее оружие",
                        "&7• Замораживает и замедляет врага при попадании!",
                        "&7• Сила VI · Бесконечность · Горящая Стрела"),
                false, "bow_ice_rw", "bow_ice_rw", 1305);
        ItemMeta m = bow.getItemMeta();
        if (m != null) {
            addEnch(m, "power", "ARROW_DAMAGE", 6);
            addEnch(m, "infinity", "ARROW_INFINITE", 1);
            addEnch(m, "flame", "ARROW_FIRE", 1);
            addEnch(m, "unbreaking", "DURABILITY", 5);
            bow.setItemMeta(m);
        }
        return bow;
    }

    public static ItemStack arrowIce(int amt) {
        return make(Material.TIPPED_ARROW, amt, "&#80D8FF&l❄ Ледяная Стрела РВ",
                Arrays.asList("&7Замораживает противника и снимает скорость!"),
                true, "arrow_ice", null, 0);
    }

    public static ItemStack mysteryEgg(int amt) {
        return make(Material.PIG_SPAWN_EGG, amt, "&#E040FB&l🥚 Загадочное Яйцо Джамшута",
                Arrays.asList("&8Предмет для спавнеров",
                        "&7ПКМ по спавнеру — меняет моба внутри на:",
                        "&fКорову, Крипера, Ифрита, Скелета или Зомби!"),
                true, "mystery_egg", null, 0);
    }

    public static ItemStack xpOrb100(int amt) {
        return make(Material.EXPERIENCE_BOTTLE, amt, "&#69F0AE&l✨ Пузырёк Опыта Джамшута [+30 LVL]",
                Arrays.asList("&7ПКМ — мгновенно начисляет &a+30 уровней опыта&7!"),
                true, "xp_orb_100", null, 0);
    }

    public static ItemStack nyMandarin(int amt) {
        return make(Material.GOLDEN_CARROT, amt, "&#FF9100&l🍊 3D Новогодний Мандарин [2026]",
                Arrays.asList("&7ПКМ — мгновенно утоляет голод, исцеляет 4❤", "&7и даёт &bСкорость II &7+ &dРегенерацию II!"),
                true, "ny_mandarin", "ny_mandarin", 1306);
    }

    public static ItemStack snowflakeCoin(int amt) {
        return make(Material.PRISMARINE_CRYSTALS, amt, "&#80D8FF&l❄ 3D Снежинка Джамшута",
                Arrays.asList("&8Новогодняя 3D валюта KINGGRIEF",
                        "&7Добывается на Авто-Шахте, Фармилке и с Боссов!",
                        "&7Обменяй у NPC &bДжамшута &7или в &f/quests&7!"),
                true, "snowflake_coin", "snowflake_coin", 1307);
    }

    public static ItemStack claimBlock(Material ore, int radius, String title) {
        int size = radius * 2 + 1;
        return make(ore, 1, title,
                Arrays.asList("&8Рудный приват KINGGRIEF",
                        "&7Поставь блок в мире выживания (&f/rtp&7),",
                        "&7чтобы защитить территорию &e" + size + "×" + size + " блоков&7!",
                        "&7Управление регионом: &f/rg info&7, &f/rg add <ник>"),
                true, "claim_" + radius, null, 0);
    }

    public static ItemStack packedShulker(Material boxMat, String title, List<String> lore, ItemStack... contents) {
        ItemStack box = new ItemStack(boxMat != null ? boxMat : Material.SHULKER_BOX, 1);
        ItemMeta meta = box.getItemMeta();
        if (meta instanceof BlockStateMeta) {
            BlockStateMeta bsm = (BlockStateMeta) meta;
            BlockState state = bsm.getBlockState();
            if (state instanceof ShulkerBox) {
                ShulkerBox sb = (ShulkerBox) state;
                Inventory inv = sb.getInventory();
                for (int i = 0; i < Math.min(27, contents.length); i++) {
                    if (contents[i] != null) inv.setItem(i, contents[i]);
                }
                bsm.setBlockState(sb);
            }
            bsm.setDisplayName(ColorUtil.c(title));
            List<String> cl = new ArrayList<String>();
            for (String s : lore) cl.add(ColorUtil.c(s));
            bsm.setLore(cl);
            box.setItemMeta(bsm);
        }
        return box;
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
            } else if (n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL")) {
                addEnch(m, "efficiency", "DIG_SPEED", protOrSharp);
                if (fireOrFort > 0) addEnch(m, "fortune", "LOOT_BONUS_BLOCKS", fireOrFort);
            } else if (n.equals("BOW")) {
                addEnch(m, "power", "ARROW_DAMAGE", protOrSharp);
                if (fireOrFort > 0) addEnch(m, "flame", "ARROW_FIRE", 1);
            } else {
                addEnch(m, "protection", "PROTECTION_ENVIRONMENTAL", protOrSharp);
            }
            if (unb > 0) addEnch(m, "unbreaking", "DURABILITY", unb);
            if (protOrSharp >= 4) addEnch(m, "mending", "MENDING", 1);
            it.setItemMeta(m);
        }
        return it;
    }

    // ================= СБАЛАНСИРОВАННЫЕ КИТЫ v5.0 (2 СТАКА ПЕРОК + ЗОЛОТАЯ МОРКОВЬ + ПЛАСТЫ/ТРАПКИ + УМЕРЕННО ЗЕЛЕК) =================
    public static List<ItemStack> buildKitItems(String kitId) {
        String k = kitId == null ? "start" : kitId.toLowerCase();
        List<ItemStack> items = new ArrayList<ItemStack>();
        if ("start".equals(k)) {
            items.add(gear(Material.IRON_SWORD, "&fМеч Игрока", 2, 2, 0));
            items.add(gear(Material.IRON_PICKAXE, "&fКирка Игрока", 2, 2, 1));
            items.add(gear(Material.IRON_AXE, "&fТопор Игрока", 2, 2, 0));
            items.add(gear(Material.IRON_SHOVEL, "&fЛопата Игрока", 2, 2, 0));
            items.add(gear(Material.BOW, "&fЛук Игрока", 2, 1, 0));
            items.add(new ItemStack(Material.SHIELD, 1));
            items.add(gear(Material.IRON_HELMET, null, 2, 2, 0));
            items.add(gear(Material.IRON_CHESTPLATE, null, 2, 2, 0));
            items.add(gear(Material.IRON_LEGGINGS, null, 2, 2, 0));
            items.add(gear(Material.IRON_BOOTS, null, 2, 2, 0));
            items.add(new ItemStack(Material.GOLDEN_CARROT, 64));
            items.add(new ItemStack(Material.ENDER_PEARL, 16));
            items.add(new ItemStack(Material.ENDER_PEARL, 16));
            items.add(new ItemStack(Material.GOLDEN_APPLE, 4));
            items.add(plast(2));
            items.add(trapka(1));
            items.add(new ItemStack(Material.ARROW, 64));
            items.add(new ItemStack(Material.OAK_LOG, 64));
            items.add(new ItemStack(Material.COBBLESTONE, 64));
            items.add(new ItemStack(Material.WATER_BUCKET, 1));
            items.add(claimBlock(Material.COAL_ORE, 7, "&a🛡 Угольный Приват 15×15"));
        } else if ("newyear".equals(k)) {
            items.add(gear(Material.DIAMOND_SWORD, "&#80D8FF❄ Новогодний Клинок ❄", 3, 3, 1));
            items.add(gear(Material.DIAMOND_PICKAXE, "&#80D8FF❄ Ледяная Кирка ❄", 4, 3, 2));
            items.add(new ItemStack(Material.GOLDEN_CARROT, 64));
            items.add(new ItemStack(Material.ENDER_PEARL, 16));
            items.add(new ItemStack(Material.ENDER_PEARL, 16));
            items.add(new ItemStack(Material.GOLDEN_APPLE, 10));
            items.add(nyMandarin(12));
            items.add(potionChampagne(1));
            items.add(snezokZamorozki(8));
            items.add(plast(3));
            items.add(trapka(2));
            items.add(new ItemStack(Material.FIREWORK_ROCKET, 64));
        } else if ("pegas".equals(k) || "cobra".equals(k) || "hero".equals(k) || "bunny".equals(k) || "titan".equals(k) || "rabbit".equals(k)) {
            int lv = ("rabbit".equals(k) || "titan".equals(k)) ? 4 : 3;
            int mult = ("rabbit".equals(k) || "titan".equals(k)) ? 2 : 1;
            String tag = k.toUpperCase();
            items.add(gear(Material.DIAMOND_SWORD, "&#38BDF8⚔ Меч " + tag, lv, 3, 1));
            items.add(gear(Material.DIAMOND_PICKAXE, "&#38BDF8⛏ Кирка " + tag, 4, 3, 2));
            items.add(gear(Material.DIAMOND_AXE, "&#38BDF8🪓 Топор " + tag, lv, 3, 0));
            items.add(gear(Material.BOW, "&#38BDF8🏹 Лук " + tag, lv, 3, 1));
            items.add(gear(Material.DIAMOND_HELMET, "&#38BDF8Шлем " + tag, lv, 3, 0));
            items.add(gear(Material.DIAMOND_CHESTPLATE, "&#38BDF8Нагрудник " + tag, lv, 3, 0));
            items.add(gear(Material.DIAMOND_LEGGINGS, "&#38BDF8Поножи " + tag, lv, 3, 0));
            items.add(gear(Material.DIAMOND_BOOTS, "&#38BDF8Ботинки " + tag, lv, 3, 0));
            items.add(new ItemStack(Material.SHIELD, 1));
            items.add(new ItemStack(Material.GOLDEN_APPLE, 16 * mult));
            items.add(new ItemStack(Material.GOLDEN_CARROT, 64));
            items.add(new ItemStack(Material.ENDER_PEARL, 16));
            items.add(new ItemStack(Material.ENDER_PEARL, 16));
            items.add(new ItemStack(Material.CHORUS_FRUIT, 16 * mult));
            items.add(new ItemStack(Material.TOTEM_OF_UNDYING, mult));
            items.add(plast(3 * mult));
            items.add(trapka(2 * mult));
            // Чуть меньше зелек, но и не слишком мало (2 шт):
            items.add(potionFlash(1));
            items.add(potionMedic(1));
            items.add(packedShulker(Material.LIGHT_BLUE_SHULKER_BOX, "&#38BDF8&l📦 Сундук Ресурсов " + tag,
                    Arrays.asList("&7Поставь на базе и забери ресурсы привилегии!"),
                    new ItemStack(Material.DIAMOND_BLOCK, 6 * mult),
                    new ItemStack(Material.GOLD_BLOCK, 12 * mult),
                    new ItemStack(Material.IRON_BLOCK, 16 * mult),
                    new ItemStack(Material.EMERALD_BLOCK, 6 * mult),
                    new ItemStack(Material.OBSIDIAN, 64),
                    new ItemStack(Material.TNT, 32 * mult),
                    new ItemStack(Material.OAK_LOG, 64),
                    new ItemStack(Material.COBWEB, 24),
                    new ItemStack(Material.GOLDEN_CARROT, 64),
                    new ItemStack(Material.EXPERIENCE_BOTTLE, 64),
                    new ItemStack(Material.ARROW, 64),
                    claimBlock(Material.IRON_ORE, 12, "&#38BDF8🛡 Железный Приват 25×25")));
        } else if ("vampire".equals(k) || "tiger".equals(k) || "dracula".equals(k) || "god".equals(k)) {
            boolean neth = "dracula".equals(k) || "god".equals(k);
            String tag = k.toUpperCase();
            items.add("dracula".equals(k) ? swordDracula() : gear(neth ? Material.NETHERITE_SWORD : Material.DIAMOND_SWORD, "&#FACC15⚡ Меч " + tag, 5, 3, 2));
            items.add(gear(neth ? Material.NETHERITE_PICKAXE : Material.DIAMOND_PICKAXE, "&#FACC15⛏ Кирка " + tag, 5, 3, 3));
            items.add(gear(neth ? Material.NETHERITE_AXE : Material.DIAMOND_AXE, "&#FACC15🪓 Топор " + tag, 5, 3, 0));
            items.add(gear(Material.BOW, "&#FACC15🏹 Лук " + tag, 5, 3, 1));
            items.add(gear(neth ? Material.NETHERITE_HELMET : Material.DIAMOND_HELMET, "&#FACC15Шлем " + tag, 4, 3, 0));
            items.add(gear(neth ? Material.NETHERITE_CHESTPLATE : Material.DIAMOND_CHESTPLATE, "&#FACC15Нагрудник " + tag, 4, 3, 0));
            items.add(gear(neth ? Material.NETHERITE_LEGGINGS : Material.DIAMOND_LEGGINGS, "&#FACC15Поножи " + tag, 4, 3, 0));
            items.add(gear(neth ? Material.NETHERITE_BOOTS : Material.DIAMOND_BOOTS, "&#FACC15Ботинки " + tag, 4, 3, 0));
            items.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, neth ? 3 : 1));
            items.add(new ItemStack(Material.GOLDEN_APPLE, 32));
            items.add(new ItemStack(Material.GOLDEN_CARROT, 64));
            items.add(new ItemStack(Material.ENDER_PEARL, 16));
            items.add(new ItemStack(Material.ENDER_PEARL, 16));
            items.add(new ItemStack(Material.CHORUS_FRUIT, 32));
            items.add(new ItemStack(Material.TOTEM_OF_UNDYING, 2));
            items.add("god".equals(k) ? sharChimery() : talTritona());
            items.add(plast(6));
            items.add(trapka(4));
            items.add(dezorient(2));
            // Сбалансированные зельки (3 шт вместо 6):
            items.add(potionKiller(1));
            items.add(potionMedic(1));
            items.add(potionBurp(1));
            items.add(packedShulker(Material.YELLOW_SHULKER_BOX, "&#FACC15&l📦 Мега-Бокс Ресурсов " + tag,
                    Arrays.asList("&7Богатый запас ресурсов, расходников FT и блоков!"),
                    new ItemStack(Material.NETHERITE_INGOT, neth ? 6 : 2),
                    new ItemStack(Material.DIAMOND_BLOCK, 24),
                    new ItemStack(Material.EMERALD_BLOCK, 20),
                    new ItemStack(Material.GOLD_BLOCK, 32),
                    new ItemStack(Material.IRON_BLOCK, 32),
                    new ItemStack(Material.OBSIDIAN, 64),
                    new ItemStack(Material.TNT, 64),
                    new ItemStack(Material.END_CRYSTAL, 12),
                    new ItemStack(Material.COBWEB, 48),
                    new ItemStack(Material.GOLDEN_CARROT, 64),
                    yavnayaPyl(2),
                    snezokZamorozki(8),
                    xpOrb100(2),
                    arrowIce(32),
                    claimBlock(Material.DIAMOND_ORE, 20, "&#38BDF8💎 Алмазный Приват 41×41")));
        } else if ("overlord".equals(k) || "imperator".equals(k) || "dragon".equals(k) || "king".equals(k) || "bull".equals(k) || "sponsor".equals(k) || "media".equals(k) || "mediaplus".equals(k) || "yt".equals(k) || "twitch".equals(k) || "tiktok".equals(k) || "kp".equals(k)) {
            String tag = k.toUpperCase();
            items.add(gear(Material.NETHERITE_SWORD, "&#A855F7👑 Меч " + tag, 5, 4, 2));
            items.add(gear(Material.NETHERITE_PICKAXE, "&#A855F7⛏ Кирка " + tag, 5, 4, 3));
            items.add(gear(Material.NETHERITE_AXE, "&#A855F7🪓 Топор " + tag, 5, 4, 0));
            items.add(bowIceRw());
            items.add(gear(Material.NETHERITE_HELMET, "&#A855F7Шлем " + tag, 4, 4, 0));
            items.add(gear(Material.NETHERITE_CHESTPLATE, "&#A855F7Нагрудник " + tag, 4, 4, 0));
            items.add(gear(Material.NETHERITE_LEGGINGS, "&#A855F7Поножи " + tag, 4, 4, 0));
            items.add(gear(Material.NETHERITE_BOOTS, "&#A855F7Ботинки " + tag, 4, 4, 0));
            items.add("sponsor".equals(k) || "bull".equals(k) ? elytraPhoenix() : elytraWind());
            items.add(new ItemStack(Material.FIREWORK_ROCKET, 64));
            items.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 6));
            items.add(new ItemStack(Material.GOLDEN_APPLE, 48));
            items.add(new ItemStack(Material.GOLDEN_CARROT, 64));
            items.add(new ItemStack(Material.ENDER_PEARL, 16));
            items.add(new ItemStack(Material.ENDER_PEARL, 16));
            items.add(new ItemStack(Material.CHORUS_FRUIT, 64));
            items.add("king".equals(k) || "sponsor".equals(k) ? sharAndromedy() : sharApollona());
            items.add(talKrushitelya());
            items.add(plast(10));
            items.add(trapka(6));
            items.add(dezorient(4));
            // Сбалансированные зельки (5 шт вместо 11):
            items.add(potionKiller(2));
            items.add(potionMedic(2));
            items.add(potionBurp(1));
            items.add(packedShulker(Material.PURPLE_SHULKER_BOX, "&#A855F7&l📦 Королевский Сундук Ресурсов " + tag,
                    Arrays.asList("&7Полный шалкер ресурсов, 3D расходников FT и рейд-набора!"),
                    new ItemStack(Material.NETHERITE_BLOCK, 2),
                    new ItemStack(Material.NETHERITE_INGOT, 12),
                    new ItemStack(Material.DIAMOND_BLOCK, 48),
                    new ItemStack(Material.EMERALD_BLOCK, 48),
                    new ItemStack(Material.GOLD_BLOCK, 64),
                    new ItemStack(Material.IRON_BLOCK, 64),
                    new ItemStack(Material.OBSIDIAN, 64),
                    new ItemStack(Material.TNT, 64),
                    new ItemStack(Material.END_CRYSTAL, 24),
                    new ItemStack(Material.GOLDEN_CARROT, 64),
                    yavnayaPyl(4),
                    bozhyaAura(2),
                    ognennySmerch(3),
                    pautinnayaGranata(4),
                    xpOrb100(4),
                    nyMandarin(16),
                    arrowIce(64),
                    claimBlock(Material.DIAMOND_ORE, 20, "&#38BDF8💎 Алмазный Приват 41×41")));
        } else {
            // DHELPER..DADMIN, DKP, OPKA, DOWNER, CURATOR, TECHADMIN, OWNER — SUPREME FT × RW КИТЫ!
            boolean supreme = "opka".equals(k) || "downer".equals(k) || "owner".equals(k) || "techadmin".equals(k) || "curator".equals(k) || "dkp".equals(k);
            String tag = k.toUpperCase();
            items.add(supreme ? swordKrush() : swordDracula());
            items.add(pickDjamshut());
            items.add(bowIceRw());
            items.add(gear(Material.NETHERITE_AXE, "&#FFD740👑 Топор " + tag, 6, 5, 0));
            items.add(gear(Material.NETHERITE_HELMET, "&#FFD740👑 Шлем " + tag, 5, 5, 0));
            items.add(gear(Material.NETHERITE_CHESTPLATE, "&#FFD740👑 Нагрудник " + tag, 5, 5, 0));
            items.add(gear(Material.NETHERITE_LEGGINGS, "&#FFD740👑 Поножи " + tag, 5, 5, 0));
            items.add(gear(Material.NETHERITE_BOOTS, "&#FFD740👑 Ботинки " + tag, 5, 5, 0));
            items.add(supreme ? elytraSanta() : elytraTitan());
            items.add(new ItemStack(Material.FIREWORK_ROCKET, 64));
            items.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, supreme ? 16 : 8));
            items.add(new ItemStack(Material.GOLDEN_APPLE, 64));
            items.add(new ItemStack(Material.GOLDEN_CARROT, 64));
            items.add(new ItemStack(Material.ENDER_PEARL, 16));
            items.add(new ItemStack(Material.ENDER_PEARL, 16));
            items.add(supreme ? sharSanty() : sharTitana());
            items.add(talFeniksa(3));
            items.add(plast(16));
            items.add(trapka(10));
            items.add(dezorient(6));
            items.add(bozhyaAura(4));
            // Умеренное количество зелий (7 шт вместо 18):
            items.add(potionKiller(2));
            items.add(potionMedic(2));
            items.add(potionAgent(1));
            items.add(potionBurp(1));
            items.add(potionAcid(1));
            items.add(packedShulker(Material.RED_SHULKER_BOX, "&#FF1744&l📦 Шалкер Богатств " + tag + " #1",
                    Arrays.asList("&7Элитные ресурсы, 3D Элитры, Шары и расходники FT×RW!"),
                    new ItemStack(Material.NETHERITE_BLOCK, supreme ? 8 : 4),
                    new ItemStack(Material.NETHERITE_INGOT, supreme ? 32 : 16),
                    new ItemStack(Material.DIAMOND_BLOCK, 64),
                    new ItemStack(Material.EMERALD_BLOCK, 64),
                    new ItemStack(Material.GOLD_BLOCK, 64),
                    new ItemStack(Material.IRON_BLOCK, 64),
                    new ItemStack(Material.GOLDEN_CARROT, 64),
                    new ItemStack(Material.ENDER_PEARL, 16),
                    new ItemStack(Material.ENDER_PEARL, 16),
                    new ItemStack(Material.CHORUS_FRUIT, 64),
                    elytraThunder(),
                    sharAresa(),
                    yavnayaPyl(8),
                    ognennySmerch(6),
                    snezokZamorozki(16),
                    pautinnayaGranata(8),
                    pickSpawner(),
                    mysteryEgg(4),
                    xpOrb100(8),
                    nyMandarin(32),
                    snowflakeCoin(supreme ? 25 : 10),
                    talStuzhi(),
                    claimBlock(Material.EMERALD_ORE, 30, "&#00E676💚 Изумрудный Приват 61×61")));
            items.add(packedShulker(Material.BLACK_SHULKER_BOX, "&#FFD740&l💣 Рейд-Шалкер " + tag + " #2",
                    Arrays.asList("&7Полный набор для грифа, рейда баз и трапок на выживании!"),
                    new ItemStack(Material.OBSIDIAN, 64),
                    new ItemStack(Material.OBSIDIAN, 64),
                    new ItemStack(Material.TNT, 64),
                    new ItemStack(Material.TNT, 64),
                    new ItemStack(Material.END_CRYSTAL, 64),
                    new ItemStack(Material.RESPAWN_ANCHOR, 16),
                    new ItemStack(Material.GLOWSTONE, 64),
                    new ItemStack(Material.COBWEB, 64),
                    new ItemStack(Material.PISTON, 64),
                    new ItemStack(Material.STICKY_PISTON, 64),
                    new ItemStack(Material.SLIME_BLOCK, 64),
                    new ItemStack(Material.REDSTONE_BLOCK, 64),
                    arrowIce(64)));
        }
        return items;
    }

    public static void giveKitItems(Player p, String kitId) {
        List<ItemStack> items = buildKitItems(kitId);
        for (ItemStack it : items) {
            for (ItemStack rem : p.getInventory().addItem(it).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), rem);
            }
        }
    }
}
