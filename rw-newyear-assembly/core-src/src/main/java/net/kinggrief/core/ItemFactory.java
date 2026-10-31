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
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
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
                if (customId != null && customId.startsWith("shar_")) {
                    cl.add(ColorUtil.c(""));
                    cl.add(ColorUtil.c("&#80D8FF◼ Приём: присядь и нажми ПКМ пустой правой рукой."));
                    cl.add(ColorUtil.c("&7Своя краткая способность; общий КТ шаров 60 сек."));
                }
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
        return make(Material.NETHER_STAR, 1, "&#80D8FF❄ &#FFD740&lМЕНЮ KINGGRIEF FT × RW v6.4.0 &#80D8FF❄ &7(ПКМ)",
                Arrays.asList(
                        "&7Быстрый доступ ко всем системам v6.4.0:",
                        "&f• Система Тиммейтов и Пати: &#00E676/tm <ник> &7(&f/friends&7, чат: &f/tc&7)",
                        "&f• Божья Аура (лечит всю тиму!) + 8 новых артефактов FT: &#00E5FF/shop",
                        "&f• Выход в выживание: &#00E676/rtp &7+ 12 Мечей: &#FF1744/swords &7+ 12 Кирок: &#FF5722/picks",
                        "&f• Скины Трапок и Пластов: &#E040FB/skins &7+ Бафовар (до 8 зелий): &#FF9100/bafs",
                        "&f• Дуэли на ставку: &#FFD740/duel <ник> <сумма> &7+ Киты: &#69F0AE/kits",
                        "",
                        "&#00E676▸ Нажми ПКМ, чтобы открыть главное меню!"
                ),
                true, "menu_star", null, 0);
    }

    /** A real edible enchanted golden apple; the accepted consume event adds
     * its bonus after vanilla has applied the normal enchanted-apple effects. */
    public static ItemStack megaGapple(int amount) {
        return make(Material.ENCHANTED_GOLDEN_APPLE, Math.max(1, Math.min(amount, 64)),
                "&#FFD740&l🍎 МЕГА-ГЕПЛЬ ЗИМЫ",
                Arrays.asList("&7Нужно съесть, как зачарованное золотое яблоко.",
                        "&7Обычные эффекты чарки + &fРегенерация III на 12 сек.",
                        "&7и &fПоглощение V на 30 сек.&7 после еды.",
                        "&7Общий КТ чарки: &f50 сек. &7• Мега-гепль: &f120 сек.",
                        "&8КТ действуют до перезапуска сервера; OWNER/TECH.ADMIN без КТ."),
                true, "mega_gapple", "mega_gapple", 3101);
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

    // ============ v6.4.0: 4 НОВЫХ 3D ШАРА (включая КВАДРАТНЫЙ ШАР-КУБ!) ============
    public static ItemStack sharKronosa() {
        return make(Material.TOTEM_OF_UNDYING, 1, "&#B388FF&l⏳ 3D Шар Хроноса [FT×RW]",
                Arrays.asList("&8Blender Cycles 3D Анимированный Шар", "", "&7Когда в &fлевой руке&7:",
                        " &#FFD740• Спешка III (копаешь в 3 раза быстрее!)", " &#40C4FF• Скорость I",
                        " &#69F0AE• Прыгучесть I"),
                true, "shar_kronosa", "shar_kronosa", 1109);
    }

    public static ItemStack sharGeliosa() {
        return make(Material.TOTEM_OF_UNDYING, 1, "&#FFB300&l🌞 3D Шар Гелиоса [FT×RW]",
                Arrays.asList("&8Blender Cycles 3D Анимированный Шар", "", "&7Когда в &fлевой руке&7:",
                        " &#FFD740• Огнестойкость I", " &#FFFFFF• Ночное зрение",
                        " &#FFD740• Спешка I", " &#FF4081• Регенерация I"),
                true, "shar_geliosa", "shar_geliosa", 1110);
    }

    public static ItemStack sharMorozko() {
        return make(Material.TOTEM_OF_UNDYING, 1, "&#7DD3FC&l❄ 3D Шар Морозко [Новогодний]",
                Arrays.asList("&8Зимний Blender 3D Шар v6.4.0", "", "&7Когда в &fлевой руке&7:",
                        " &#00E676• Сопротивление II", " &#FF4081• Регенерация I", " &#FF5252• Сила I",
                        " &#80D8FF• Морозная аура: враги в радиусе 6 замерзают и тормозят"),
                true, "shar_morozko", "shar_morozko", 1111);
    }

    public static ItemStack sharKvadrat() {
        return make(Material.TOTEM_OF_UNDYING, 1, "&#FF4081&l◼ КВАДРАТНЫЙ ШАР-КУБ [МЕГА v6.4.0]",
                Arrays.asList("&8Квадратный 3D Шар-Куб — топовый шар сервера", "",
                        "&7Когда в &fлевой руке&7:",
                        " &#FF5252• Сила II", " &#FFD740• Спешка II", " &#00E676• Сопротивление II",
                        " &#FF4081• Регенерация I", " &#B388FF• Прыгучесть II", "",
                        "&#FFD740◼ Форма: идеальный КУБ — шары квадратом!",
                        "&#00E676🛡 Как и любой шар: спасает от смерти 1 раз (как тотем)"),
                true, "shar_kvadrat", "shar_kvadrat", 1112);
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
                        "&7Когда в &fлевой руке&7: &#80D8FF+2 ❤ · Скорость II · Реген I",
                        "&7При срабатывании тотема:", " &#80D8FF• Замораживает всех врагов вокруг на 6 сек!"),
                true, "tal_stuzhi", "tal_stuzhi", 1207);
    }

    // ================= 9 НОВЫХ 3D ТАЛИСМАНОВ v5.7 (ВСЕГО 16 ТАЛИСМАНОВ + БОЖЕСТВЕННЫЙ OWNER!) =================
    public static ItemStack talOwner(int charges) {
        int ch = Math.max(1, Math.min(5, charges));
        ItemStack it = make(Material.TOTEM_OF_UNDYING, 1, "&#FF1744&l👑 &#FFD740&lБОЖЕСТВЕННЫЙ ТАЛИСМАН OWNER [" + ch + "/5]",
                Arrays.asList("&8Эксклюзивный Божественный Артефакт ВЛАДЕЛЬЦА", "",
                        "&7Осталось зарядов бессмертия: &#FFD740&l" + ch + " / 5",
                        "&7Когда в &fлевой руке&7:",
                        " &#FF1744• +10 ❤ Максимального Здоровья (30 ❤!)",
                        " &#FF5252• Сила III  &#40C4FF• Скорость III  &#00E676• Сопротивление II",
                        " &#FF80AB• Регенерация II  &#FFD740• Огнестойкость  &#69F0AE• Спешка II",
                        "&7При срабатывании:",
                        " &#FFD740• Полное исцеление + 10 Золотых ❤ + Молнии по врагам!"),
                true, "tal_owner", "tal_owner", 1208);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.getPersistentDataContainer().set(CHARGES_KEY, PersistentDataType.INTEGER, Integer.valueOf(ch));
            it.setItemMeta(m);
        }
        return it;
    }

    public static ItemStack talKaratelya() {
        return make(Material.TOTEM_OF_UNDYING, 1, "&#EF4444&l⚔ 3D Талисман Карателя [FT×RW]",
                Arrays.asList("&8Боевой 3D Талисман Атаки", "",
                        "&7Когда в &fлевой руке&7:",
                        " &#FF5252• +3 ❤ Здоровья  &#FF1744• Сила III  &#40C4FF• Скорость II",
                        "&7При срабатывании:", " &#EF4444• Поджигает и накладывает Иссушение II врагам!"),
                true, "tal_karatelya", "tal_karatelya", 1209);
    }

    public static ItemStack talZeusa() {
        return make(Material.TOTEM_OF_UNDYING, 1, "&#FACC15&l⚡ 3D Талисман Зевса [FT×RW]",
                Arrays.asList("&8Громовой 3D Талисман Олимпа", "",
                        "&7Когда в &fлевой руке&7:",
                        " &#FF5252• +3 ❤ Здоровья  &#FFD740• Сила II  &#40C4FF• Скорость II",
                        "&7При срабатывании:", " &#FACC15• Удар Молнии Зевса по всем врагам в 7 блоках!"),
                true, "tal_zeusa", "tal_zeusa", 1210);
    }

    public static ItemStack talDraculy() {
        return make(Material.TOTEM_OF_UNDYING, 1, "&#E11D48&l🩸 3D Талисман Дракулы [Вампиризм]",
                Arrays.asList("&8Кровавый 3D Талисман Ночи", "",
                        "&7Когда в &fлевой руке&7:",
                        " &#FF5252• +4 ❤ Здоровья  &#FF1744• Сила II  &#F43F5E• Вампиризм +25%",
                        "&7При срабатывании:", " &#E11D48• Крадёт 4❤ у ближайших врагов!"),
                true, "tal_draculy", "tal_draculy", 1211);
    }

    public static ItemStack talKolossa() {
        return make(Material.TOTEM_OF_UNDYING, 1, "&#6EE7B7&l🛡 3D Талисман Колосса [Танк]",
                Arrays.asList("&8Непробиваемый 3D Талисман Титанов", "",
                        "&7Когда в &fлевой руке&7:",
                        " &#FF5252• +6 ❤ Максимального Здоровья  &#00E676• Сопротивление II  &#FFD740• Огнестойкость",
                        "&7При срабатывании:", " &#6EE7B7• Каменная Крепость (+12 Золотых ❤)!"),
                true, "tal_kolossa", "tal_kolossa", 1212);
    }

    public static ItemStack talZhneca() {
        return make(Material.TOTEM_OF_UNDYING, 1, "&#A855F7&l💀 3D Талисман Жнеца Душ [FT×RW]",
                Arrays.asList("&8Теневой 3D Талисман Жнеца", "",
                        "&7Когда в &fлевой руке&7:",
                        " &#FF5252• +3 ❤ Здоровья  &#C084FC• Сила III  &#38BDF8• Скорость II",
                        "&7При срабатывании:", " &#A855F7• Ослепляет и ослабляет всех врагов вокруг!"),
                true, "tal_zhneca", "tal_zhneca", 1213);
    }

    public static ItemStack talSevera() {
        return make(Material.TOTEM_OF_UNDYING, 1, "&#38BDF8&l❄ 3D Талисман Северной Вьюги [2026]",
                Arrays.asList("&8Новогодний 3D Талисман Метели", "",
                        "&7Когда в &fлевой руке&7:",
                        " &#FF5252• +4 ❤ Здоровья  &#38BDF8• Скорость III  &#F472B6• Регенерация II",
                        "&7При срабатывании:", " &#38BDF8• Снежная Буря замораживает врагов на 7 сек!"),
                true, "tal_severa", "tal_severa", 1214);
    }

    public static ItemStack talBezdn(int charges) {
        int ch = Math.max(1, Math.min(2, charges));
        ItemStack it = make(Material.TOTEM_OF_UNDYING, 1, "&#8B5CF6&l🔮 3D Талисман Бездны [" + ch + "/2]",
                Arrays.asList("&8Двухзарядный 3D Талисман Пустоты", "",
                        "&7Осталось зарядов: &#00E676&l" + ch + " / 2",
                        "&7Когда в &fлевой руке&7:",
                        " &#FF5252• +4 ❤ Здоровья  &#A855F7• Сила II  &#00E676• Сопротивление I"),
                true, "tal_bezdn", "tal_bezdn", 1215);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.getPersistentDataContainer().set(CHARGES_KEY, PersistentDataType.INTEGER, Integer.valueOf(ch));
            it.setItemMeta(m);
        }
        return it;
    }

    public static ItemStack talImperatora() {
        return make(Material.TOTEM_OF_UNDYING, 1, "&#FB923C&l👑 3D Талисман Императора [FT×RW]",
                Arrays.asList("&8Королевский 3D Талисман Власти", "",
                        "&7Когда в &fлевой руке&7:",
                        " &#FF5252• +5 ❤ Здоровья  &#FF1744• Сила II  &#38BDF8• Скорость II  &#F472B6• Реген II",
                        "&7При срабатывании:", " &#FB923C• Императорский Щит (+8 Золотых ❤ + Сила III)!"),
                true, "tal_imperatora", "tal_imperatora", 1216);
    }

    public static ItemStack totemRw() {
        return make(Material.TOTEM_OF_UNDYING, 1, "&#FFD740&l🔱 3D Тотем Бессмертия [RW]",
                Arrays.asList("&8Фирменный 3D Тотем ReallyWorld", "",
                        "&7• Усиленное возрождение + Регенерация III",
                        "&7• Компактная PvP-модель в левой руке!"),
                true, "totem_rw", "totem_rw", 1217);
    }

    // ================= 6 КАСТОМНЫХ BLENDER 3D ЭЛИТР С РАЗНЫМИ БАФФАМИ (ПРОЧНОСТЬ VI / VII!) =================
    private static ItemStack makeBuffedElytra(String name, String id, int cmd, int protLvl, int unbLvl, List<String> lore) {
        ItemStack el = make(Material.ELYTRA, 1, name, lore, false, id, id, cmd);
        ItemMeta m = el.getItemMeta();
        if (m != null) {
            addEnch(m, "protection", "PROTECTION_ENVIRONMENTAL", protLvl);
            addEnch(m, "unbreaking", "DURABILITY", unbLvl);
            addEnch(m, "mending", "MENDING", 1);
            el.setItemMeta(m);
        }
        return el;
    }

    public static ItemStack elytraWind() {
        return makeBuffedElytra("&#80D8FF&l🪽 3D Элитры Ветра [FT×RW]", "elytra_wind", 1401, 4, 6,
                Arrays.asList("&8Blender 3D Кастомные Элитры", "",
                        "&7Когда надеты в слот нагрудника:",
                        " &#40C4FF• Постоянная Скорость II",
                        " &#80D8FF• Плавное падение на Shift в воздухе",
                        " &#B3E5FC• Защита IV · Прочность VI · Починка"));
    }

    public static ItemStack elytraPhoenix() {
        return makeBuffedElytra("&#FF6D00&l🔥 3D Элитры Феникса [FT×RW]", "elytra_phoenix", 1402, 4, 6,
                Arrays.asList("&8Blender 3D Кастомные Элитры", "",
                        "&7Когда надеты в слот нагрудника:",
                        " &#FF5252• +2 ❤ Максимального Здоровья",
                        " &#FFD740• Постоянная Огнестойкость + Огненный шлейф",
                        " &#FF9100• Поджигают атакующих в ближнем бою!",
                        " &#B3E5FC• Защита IV · Прочность VI · Починка"));
    }

    public static ItemStack elytraVampire() {
        return makeBuffedElytra("&#FF1744&l🦇 3D Элитры Вампира [FT×RW]", "elytra_vampire", 1403, 4, 6,
                Arrays.asList("&8Blender 3D Кастомные Элитры", "",
                        "&7Когда надеты в слот нагрудника:",
                        " &#FF1744• Постоянная Сила I + Ночное Зрение",
                        " &#F50057• Вампиризм: исцеляют 20% от урона в PvP!",
                        " &#B3E5FC• Защита IV · Прочность VI · Починка"));
    }

    public static ItemStack elytraThunder() {
        return makeBuffedElytra("&#FFD740&l⚡ 3D Элитры Грома [FT×RW]", "elytra_thunder", 1404, 4, 6,
                Arrays.asList("&8Blender 3D Кастомные Элитры", "",
                        "&7Когда надеты в слот нагрудника:",
                        " &#40C4FF• Скорость II (Сила II во время полёта!)",
                        " &#FFD740• 18% шанс ударить врага Молнией при ударе!",
                        " &#B3E5FC• Защита IV · Прочность VI · Починка"));
    }

    public static ItemStack elytraTitan() {
        return makeBuffedElytra("&#F59E0B&l🛡 3D Элитры Крушителя [Броня]", "elytra_titan", 1405, 5, 7,
                Arrays.asList("&8Blender 3D Тяжёлые Боевые Элитры", "",
                        "&7Когда надеты в слот нагрудника:",
                        " &#FF5252• +4 ❤ Максимального Здоровья",
                        " &#00E676• Постоянное Сопротивление I (как нагрудник!)",
                        " &#FFD740• Защита V · Прочность VII · Починка"));
    }

    public static ItemStack elytraSanta() {
        return makeBuffedElytra("&#00E5FF&l❄ 3D Императорские Элитры Санты [2026]", "elytra_santa", 1406, 5, 7,
                Arrays.asList("&8Топовые Blender 3D Новогодние Элитры", "",
                        "&7Когда надеты в слот нагрудника:",
                        " &#FF5252• +6 ❤ Максимального Здоровья",
                        " &#FF1744• Сила II  &#40C4FF• Скорость II  &#FF4081• Регенерация I",
                        " &#80D8FF• Ледяной шлейф + Защита V · Прочность VII · Починка"));
    }

    // ================= 8 БОЕВЫХ PvP ПРЕДМЕТОВ FUN-TIME × REALLYWORLD =================
    public static ItemStack plast(int amt) {
        return make(Material.DRIED_KELP, amt, "&#A855F7&l🧱 Пласт 5×5×2 [FT]",
                Arrays.asList("&8Боевой расходник FunTime × ReallyWorld", "",
                        "&7ПКМ — мгновенно создаёт нерушимый",
                        "&7пласт &d5×5×2 &7по направлению взгляда",
                        "&7(или под ногами при взгляде вниз) на &e12 сек&7!",
                        "&7• Применяет ваш &bСкин Пласта (&f/plastskins&b) &7с витражным",
                        "&7  стеклом и тактическим эффектом при установке!",
                        "&8Перезарядка: 10 сек."),
                true, "plast", "plast", 1501);
    }

    public static ItemStack trapka(int amt) {
        return make(Material.NETHERITE_SCRAP, amt, "&#FF1744&l⛓ Трапка 5×5×5 [Стекло + Скин]",
                Arrays.asList("&8Боевая клетка FunTime × ReallyWorld", "",
                        "&7ПКМ — закрывает вас и врагов в нерушимой",
                        "&7клетке &c5×5×5 &7на &e15 сек &7с &#80D8FFброне-стеклянными окнами&7!",
                        "&7• Через витражное стекло отлично видно врагов внутри и снаружи!",
                        "&7• Активный &dСкин Трапки (&f/skins&d) &7накладывает мощный дебафф!",
                        "&8Перезарядка: 20 сек."),
                true, "trapka", "trapka", 1502);
    }

    public static ItemStack dezorient(int amt) {
        return make(Material.ENDER_EYE, amt, "&#E040FB&l👁 Дезориентация [FT · Иссушение III]",
                Arrays.asList("&8Боевой расходник FunTime × ReallyWorld", "",
                        "&7ПКМ — поражает всех врагов в радиусе &d10 блоков&7:",
                        "&7• Ослепление + Тошнота II + Замедление II на &e7 сек&7!",
                        "&7• &#FF1744Иссушение III &7на &c5 секунд&7!",
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
        return make(Material.PHANTOM_MEMBRANE, amt, "&#FFD740&l☀ Божья Аура [Хил Тиммейтов FT]",
                Arrays.asList("&8Священный командный расходник FunTime × ReallyWorld", "",
                        "&7ПКМ — исцеляет &fВАС &7и &aВСЕХ ТИММЕЙТОВ &7(&f/tm &7и &f/clan&7)",
                        "&7в радиусе &e12 блоков&7:",
                        " &#00E676• Мгновенно +4❤ Здоровья и снятие ВСЕХ дебаффов!",
                        " &#FF80AB• Регенерация III (7с) + Сопротивление II (6с)",
                        " &#FFD740• +4 Золотых Сердца 💛 и Священный Купол (+1❤/с 5 сек)!",
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

    public static ItemStack bozhyaKara(int amt) {
        return make(Material.BLAZE_ROD, amt, "&#FACC15&l⚡ Божья Кара Зевса [FT×RW]",
                Arrays.asList("&8Карающий расходник FunTime × ReallyWorld", "",
                        "&7ПКМ — обрушивает небесные молнии на всех врагов",
                        "&7в радиусе &e8 блоков &7(&c8❤ урона &7+ поджог + КТ 30с)!",
                        "&8Перезарядка: 22 сек."),
                true, "bozhya_kara", "bozhya_kara", 1509);
    }

    public static ItemStack gravSfera(int amt) {
        return make(Material.HEART_OF_THE_SEA, amt, "&#C084FC&l🕳 Гравитационная Сфера [Чёрная Дыра]",
                Arrays.asList("&8Тактический расходник FunTime × ReallyWorld", "",
                        "&7ПКМ — создаёт гравитационный импульс и стягивает",
                        "&7всех врагов в радиусе &d9 блоков &7прямо под комбо!",
                        "&8Перезарядка: 20 сек."),
                true, "grav_sfera", "grav_sfera", 1510);
    }

    public static ItemStack snegZavesa(int amt) {
        return make(Material.FEATHER, amt, "&#7DD3FC&l❄ Снежная Завеса [Рывок]",
                Arrays.asList("&8Тактический расходник FunTime × ReallyWorld", "",
                        "&7ПКМ — рывок вперёд + &bСкорость III &7и ослепление",
                        "&7снежной бурей всех врагов в радиусе &b6 блоков&7!",
                        "&8Перезарядка: 16 сек."),
                true, "sneg_zavesa", "sneg_zavesa", 1511);
    }

    // ================= 8 НОВЫХ КОМАНДНЫХ И ТАКТИЧЕСКИХ АРТЕФАКТОВ v6.0 =================
    public static ItemStack teamTotem(int amt) {
        return make(Material.BEACON, amt, "&#00E676&l🛡 Тотем Командного Спасения [FT×RW]",
                Arrays.asList("&8Командный расходник FunTime × ReallyWorld v6.4.0", "",
                        "&7ПКМ — разворачивает щит спасения на &e6 сек &7(&b10 блоков&7):",
                        " &#00E676• Даёт ВАМ и ВСЕМ ТИММЕЙТАМ Сопротивление III + Огнестойкость!",
                        " &#FF80AB• Каждую секунду лечит всю тиму на +1.5❤",
                        " &#FF5252• Отталкивает всех врагов от вашей команды!",
                        "&8Перезарядка: 35 сек."),
                true, "team_totem", null, 0);
    }

    public static ItemStack battleAdrenaline(int amt) {
        return make(Material.MAGMA_CREAM, amt, "&#FF6D00&l💉 Боевой Адреналин [Раш Тимы]",
                Arrays.asList("&8Командный расходник FunTime × ReallyWorld v6.4.0", "",
                        "&7ПКМ — заряжает &fВАС &7и &aВСЕХ ТИММЕЙТОВ &7в радиусе &e10 блоков&7:",
                        " &#00E5FF• Скорость III на 10 сек",
                        " &#FF1744• Сила II на 8 сек",
                        " &#FFD740• Спешка II + Огнестойкость на 10 сек",
                        "&8Перезарядка: 25 сек."),
                true, "battle_adrenaline", null, 0);
    }

    public static ItemStack swapOrb(int amt) {
        return make(Material.ENDER_EYE, amt, "&#D500F9&l🌀 Сфера Рокировки [Сваппер FT]",
                Arrays.asList("&8Тактический расходник FunTime × ReallyWorld v6.4.0", "",
                        "&7ПКМ — бросок сферы рокировки (до &d28 блоков&7):",
                        " &#FF5252• Попал во ВРАГА — меняет вас местами + Замедление II и Слабость!",
                        " &#00E676• Попал в ТИММЕЙТА — вытаскивает его из замеса и даёт +4💛 обоим!",
                        "&8Перезарядка: 22 сек. (Запрещена в кабинках)"),
                true, "swap_orb", null, 0);
    }

    public static ItemStack iceDome(int amt) {
        return make(Material.BLUE_ICE, amt, "&#40C4FF&l🧊 Ледяной Купол [Барьер 7×7]",
                Arrays.asList("&8Защитный расходник FunTime × ReallyWorld v6.4.0", "",
                        "&7ПКМ — мгновенно возводит вокруг вас и тиммейтов",
                        "&7броне-купол из &bПлотного Льда и Стекла 7×7 &7на &e12 сек",
                        "&7для безопасного отхила Чаркой и Божьей Аурой!",
                        "&8Перезарядка: 28 сек."),
                true, "ice_dome", null, 0);
    }

    public static ItemStack vampirePulse(int amt) {
        return make(Material.GHAST_TEAR, amt, "&#FF1744&l🩸 Импульс Крови Вампира [FT×RW]",
                Arrays.asList("&8Атакующий расходник FunTime × ReallyWorld v6.4.0", "",
                        "&7ПКМ — высасывает по &c3❤ (6 HP) &7у каждого врага",
                        "&7в радиусе &c8 блоков &7и распределяет выпитую кровь",
                        "&7между &fвами &7и &aранеными тиммейтами &7рядом!",
                        "&8Перезарядка: 24 сек."),
                true, "vampire_pulse", null, 0);
    }

    public static ItemStack empShock(int amt) {
        return make(Material.PRISMARINE_SHARD, amt, "&#00E5FF&l⚡ ЭМИ-Разрядник [Анти-Щит & Анти-Элитры]",
                Arrays.asList("&8Тактический расходник FunTime × ReallyWorld v6.4.0", "",
                        "&7ПКМ — электромагнитный взрыв в радиусе &b11 блоков&7:",
                        " &#FF1744• Сбивает полёт на Элитрах всем врагам в воздухе!",
                        " &#FFD740• Отключает Щиты врагам на 5 сек + Слабость I!",
                        " &#00E676• Безопасен для ваших тиммейтов!",
                        "&8Перезарядка: 20 сек."),
                true, "emp_shock", null, 0);
    }

    public static ItemStack smokeBomb(int amt) {
        return make(Material.CHARCOAL, amt, "&#94A3B8&l💣 Дымовая Бомба Ниндзя [Инвиз Тимы]",
                Arrays.asList("&8Тактический расходник FunTime × ReallyWorld v6.4.0", "",
                        "&7ПКМ — создаёт плотную дымовую завесу на &e6 сек&7:",
                        " &#FF5252• Ослепляет врагов внутри облака на 4 сек",
                        " &#00E676• Даёт ВАМ и ТИММЕЙТАМ Невидимость + Скорость II на 8 сек!",
                        "&8Перезарядка: 22 сек."),
                true, "smoke_bomb", null, 0);
    }

    public static ItemStack grapplingHook() {
        ItemStack it = make(Material.FISHING_ROD, 1, "&#F59E0B&l🧲 Крюк-Кошка [Гарпун FT]",
                Arrays.asList("&8Тактический инструмент FunTime × ReallyWorld v6.4.0", "",
                        "&7Брось крюк и нажми ПКМ — мощно притягивает",
                        "&7тебя вперёд и вверх к точке зацепа!",
                        "&8Перезарядка: 5 сек. (Не разрушается, запрещён в кабинках)"),
                true, "grappling_hook", null, 0);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            try { m.setUnbreakable(true); } catch (Throwable ignored) {}
            it.setItemMeta(m);
        }
        return it;
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
                Arrays.asList("&8Blender 3D Уникальная кирка (#2/12)",
                        "&7Позволяет &eсломать и забрать Рассадник Мобов",
                        "&7в инвентарь с сохранением типа моба!",
                        "&cОдноразовый предмет (1 прочность на спавнер)."),
                true, "pick_spawner", "pick_spawner", 1302);
    }

    // ================= 10 НОВЫХ 3D КИРОК И МОЛОТОВ (ИТОГО 12 КИРОК v5.8!) =================
    private static ItemStack enchantedPick(Material base, String name, List<String> lore, boolean unbreakable,
                                           String customId, int cmd, int eff, int fort, int unb) {
        ItemStack pick = make(base, 1, name, lore, unbreakable, customId, customId, cmd);
        ItemMeta m = pick.getItemMeta();
        if (m != null) {
            if (eff > 0) addEnch(m, "efficiency", "DIG_SPEED", eff);
            if (fort > 0) addEnch(m, "fortune", "LOOT_BONUS_BLOCKS", fort);
            if (unb > 0) addEnch(m, "unbreaking", "DURABILITY", unb);
            addEnch(m, "mending", "MENDING", 1);
            pick.setItemMeta(m);
        }
        return pick;
    }

    /** #3/12 — КИРКА КРУШИТЕЛЯ 3×3 (Особый заказ v5.8!) */
    public static ItemStack pickKrush() {
        return enchantedPick(Material.NETHERITE_PICKAXE,
                "&#FF5722&l💥 3D Кирка Крушителя [Бур 3×3 + Магнит]",
                Arrays.asList("&8Легендарная Кирка FunTime × ReallyWorld (#3/12)",
                        "",
                        "&7• &#FF9100Бульдозер Крушителя 3×3 &7(по направлению взгляда!)",
                        "&7• &#FF5252Авто-Плавка + Магнит &7— слитки сразу в инвентарь!",
                        "&7• &#FFD740Пассивно: Спешка II &7пока в руке! &8(Shift = 1×1)",
                        "&7• Эффективность VIII · Удача V · Нерушимая"),
                true, "pick_krush", 1801, 8, 5, 10);
    }

    /** #4/12 — БОЖЕСТВЕННЫЙ МОЛОТ OWNER'А 10×10 (Эксклюзив Владельца v5.8!) */
    public static ItemStack hammerOwner() {
        return enchantedPick(Material.NETHERITE_PICKAXE,
                "&#FF1744&l👑 3D Божественный Молот OWNER'а [Мега-Бур 10×10!]",
                Arrays.asList("&8Божественный Инструмент Владельца (#4/12)",
                        "",
                        "&7• &#FF1744МЕГА-РАЗЛОМ 10×10 &7(сносит 100 блоков за 1 взмах!)",
                        "&7• &#00E676Авто-Плавка ×2 + Телекинез &7— руда сразу в инвентарь!",
                        "&7• &#FFD740Пассивно: Спешка IV + Ночное Зрение &8(Shift = 1×1)",
                        "&7• Эффективность X · Удача VII · Вечная прочность"),
                true, "hammer_owner", 1802, 10, 7, 10);
    }

    /** #5/12 — ИЗУМРУДНАЯ ЖИЛЬНАЯ КИРКА (VeinMiner до 32 руд сразу!) */
    public static ItemStack pickVein() {
        return enchantedPick(Material.NETHERITE_PICKAXE,
                "&#00E676&l💎 3D Изумрудная Жильная Кирка [Вся Жила]",
                Arrays.asList("&8Кристальная Кирка Рудокопа (#5/12)",
                        "",
                        "&7• &#00E676Жилоискатель (VeinMiner) &7— ломает всю жилу руды",
                        "&7  целиком (до 32 блоков за 1 удар) с Авто-Плавкой!",
                        "&7• Эффективность VI · Удача V · Прочность VI"),
                false, "pick_vein", 1803, 6, 5, 6);
    }

    /** #6/12 — ЛЕДЯНАЯ КИРКА СТУЖИ 2026 (Бур 3×3 + Заморозка Лавы/Воды) */
    public static ItemStack pickFrost() {
        return enchantedPick(Material.DIAMOND_PICKAXE,
                "&#38BDF8&l❄ 3D Ледяная Кирка Стужи [3×3 + Анти-Лава]",
                Arrays.asList("&8Зимний Инструмент 2026 (#6/12)",
                        "",
                        "&7• &#38BDF8Ледяной Бур 3×3 &7+ мгновенно превращает",
                        "&7  соседнюю лаву в Обсидиан, а воду — в Плотный Лёд!",
                        "&7• Эффективность VI · Удача IV · Прочность V"),
                false, "pick_frost", 1804, 6, 4, 5);
    }

    /** #7/12 — ВУЛКАНИЧЕСКАЯ КИРКА ГЕФЕСТА (Авто-Плавка ×2 Слитков + Огнестойкость) */
    public static ItemStack pickMolten() {
        return enchantedPick(Material.NETHERITE_PICKAXE,
                "&#FF6D00&l🌋 3D Вулканическая Кирка Гефеста [Плавка ×2]",
                Arrays.asList("&8Огненная Кирка Кузнеца Богов (#7/12)",
                        "",
                        "&7• &#FF6D00Двойная Плавка &7— даёт 2–3 слитка с каждой руды",
                        "&7  и &#FFD7402× Незеритового Лома &7с Древних Обломков!",
                        "&7• Пассивно даёт &#FF9100Огнестойкость &7в руке!"),
                false, "pick_molten", 1805, 7, 4, 6);
    }

    /** #8/12 — КВАНТОВАЯ МАГНИТНАЯ КИРКА (Бур 3×3 + 100% Телекинез) */
    public static ItemStack pickMagnet() {
        return enchantedPick(Material.NETHERITE_PICKAXE,
                "&#00E5FF&l🧲 3D Квантовая Магнитная Кирка [3×3 + Телекинез]",
                Arrays.asList("&8Технологический Инструмент (#8/12)",
                        "",
                        "&7• &#00E5FFБур 3×3 + 100% Телекинез &7— все добытые блоки",
                        "&7  и опыт мгновенно попадают прямо в ваш инвентарь!",
                        "&7• Ни один алмаз не сгорит в лаве!"),
                false, "pick_magnet", 1806, 7, 4, 6);
    }

    /** #9/12 — ОБСИДИАНОВЫЙ КРУШИТЕЛЬ (Мгновенный слом Обсидиана / Трапок!) */
    public static ItemStack pickObsidian() {
        return enchantedPick(Material.NETHERITE_PICKAXE,
                "&#A855F7&l🟣 3D Обсидиановый Крушитель [Анти-Обсидиан]",
                Arrays.asList("&8Штурмовая Рейд-Кирка (#9/12)",
                        "",
                        "&7• &#E040FBМгновенно раскалывает Обсидиан&7, Плачущий Обсидиан,",
                        "&7  Якоря Возрождения и Эндер-Сундуки с 1 удара ЛКМ!",
                        "&7• Идеальна для рейда баз! (Не ломает временную Трапку)"),
                false, "pick_obsidian", 1807, 8, 3, 7);
    }

    /** #10/12 — ЗОЛОТАЯ КИРКА МИДАСА (Монеты + Золото с обычного камня!) */
    public static ItemStack pickMidas() {
        return enchantedPick(Material.GOLDEN_PICKAXE,
                "&#FFD740&l🪙 3D Золотая Кирка Мидаса [+Монеты с Камня]",
                Arrays.asList("&8Королевская Кирка Богатства (#10/12)",
                        "",
                        "&7• &#FFD740Дар Мидаса &7— каждый вскопанный камень/руда",
                        "&7  приносит &#00E676+25$..+250$ Монет &7и золотые слитки!",
                        "&7• Нерушимая золотая сталь · Эффективность VIII · Удача V"),
                true, "pick_midas", 1808, 8, 5, 10);
    }

    /** #11/12 — ТУННЕЛЬНЫЙ МЕГА-БУР ТИТАНА 5×5 (25 блоков за удар!) */
    public static ItemStack pickTunnel() {
        return enchantedPick(Material.NETHERITE_PICKAXE,
                "&#3B82F6&l🚇 3D Туннельный Мега-Бур Титана [Бур 5×5!]",
                Arrays.asList("&8Тяжёлый Промышленный Молот (#11/12)",
                        "",
                        "&7• &#38BDF8Пробивает туннель 5×5 &7(25 блоков за 1 удар!)",
                        "&7• &#00E676Авто-Плавка + Телекинез ценных руд &8(Shift = 1×1)",
                        "&7• Эффективность VIII · Удача V · Нерушимый"),
                true, "pick_tunnel", 1809, 8, 5, 10);
    }

    /** #12/12 — МЕТЕОРИТНАЯ КИРКА ЗЕВСА 3×3×3 КУБ (27 блоков + Шанс Риликов!) */
    public static ItemStack pickMeteor() {
        return enchantedPick(Material.NETHERITE_PICKAXE,
                "&#D946EF&l☄ 3D Метеоритная Кирка Зевса [Куб 3×3×3!]",
                Arrays.asList("&8Звёздный Артефакт Олимпа (#12/12)",
                        "",
                        "&7• &#E040FBКубический Разлом 3×3×3 &7(27 блоков вокруг точки!)",
                        "&7• Авто-Плавка + шанс найти &#00E5FFСнежинку ❄ / Рилик ❖&7!",
                        "&7• Эффективность IX · Удача VI · Нерушимая &8(Shift = 1×1)"),
                true, "pick_meteor", 1810, 9, 6, 10);
    }

    // ================= 4 НОВЫХ 3D АРТЕФАКТА И ОРУЖИЯ v5.8 ("И МНОГО ЧЕГО ТОЖЕ ДОБАВЬ") =================
    public static ItemStack scytheReaper() {
        ItemStack sc = make(Material.NETHERITE_SWORD, 1, "&#E11D48&l⚔ 3D Коса Смерти Жнеца [Казнь + Вампиризм]",
                Arrays.asList("&8Мифическое 3D Оружие Жнеца Душ",
                        "",
                        "&7• &#FF1744Жатва Душ: &7лечит на &c25% &7от нанесённого урона!",
                        "&7• &#A855F7Казнь: &7+35% урона по врагам, у которых < 8 сердец!",
                        "&7• Накладывает &#9C27B0Иссушение II &7на 4 сек при ударе!",
                        "&7• Острота VII · Заговор Огня II · Нерушимая"),
                true, "scythe_reaper", "scythe_reaper", 1821);
        ItemMeta m = sc.getItemMeta();
        if (m != null) {
            addEnch(m, "sharpness", "DAMAGE_ALL", 7);
            addEnch(m, "fire_aspect", "FIRE_ASPECT", 2);
            addEnch(m, "sweeping_edge", "SWEEPING_EDGE", 3);
            sc.setItemMeta(m);
        }
        return sc;
    }

    public static ItemStack axeThor() {
        ItemStack ax = make(Material.NETHERITE_AXE, 1, "&#38BDF8&l🪓 3D Громовая Секира Тора [Молния + Слом Щита]",
                Arrays.asList("&8Божественная 3D Секира Шторма",
                        "",
                        "&7• &#FFD740Удар Грома: &7призывает настоящую молнию во врага",
                        "&7  (+5 чистого урона шоком, перезарядка 7 сек)!",
                        "&7• &#FF5252Пробивает любой Щит &7и оглушает врага!",
                        "&7• Острота VII · Эффективность VI · Нерушимая"),
                true, "axe_thor", "axe_thor", 1822);
        ItemMeta m = ax.getItemMeta();
        if (m != null) {
            addEnch(m, "sharpness", "DAMAGE_ALL", 7);
            addEnch(m, "efficiency", "DIG_SPEED", 6);
            ax.setItemMeta(m);
        }
        return ax;
    }

    public static ItemStack orbRecall(int amt) {
        return make(Material.HEART_OF_THE_SEA, amt, "&#00E5FF&l🌀 3D Сфера Эвакуации [Экстренный Сейв · 10 сек]",
                Arrays.asList("&8Тактический Артефакт Выживания FT × RW",
                        "",
                        "&7Нажмите ПКМ — запускает &#80D8FF10-секундный купол",
                        "&7экстренной эвакуации прямиком на &#00E676/spawn&7!",
                        "&c(Если не получить урон 10 секунд — вытаскивает даже из ПвП!)"),
                true, "orb_recall", "orb_recall", 1823);
    }

    public static ItemStack lootMagnet() {
        return make(Material.TOTEM_OF_UNDYING, 1, "&#FF4081&l🧲 3D Квантовый Магнит Лута [Авто-Сбор 9м]",
                Arrays.asList("&8Пассивный Реликт (Левая рука / Инвентарь)",
                        "",
                        "&7• Автоматически притягивает &#FFD740весь дроп и опыт",
                        "&7  в радиусе &b9 блоков &7прямо к вам каждую секунду!",
                        "&7• Идеален для Авто-Шахты, Боссов и ПвП!"),
                true, "loot_magnet", "loot_magnet", 1824);
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

    public static ItemStack swordOwner() {
        ItemStack sw = make(Material.NETHERITE_SWORD, 1, "&#FF1744&l👑 &#FFD740&lБОЖЕСТВЕННЫЙ МЕЧ OWNER [Острота VIII]",
                Arrays.asList("&8Эксклюзивный клинок ВЛАДЕЛЬЦА сервера",
                        "&7• Зачарование: &#FF1744Вампиризм III &7+ &#76FF03Окисление III",
                        "&7• Острота VIII · Заговор Огня III · Добыча V · Прочность VI"),
                false, "sword_owner", "sword_owner", 1314);
        ItemMeta m = sw.getItemMeta();
        if (m != null) {
            addEnch(m, "sharpness", "DAMAGE_ALL", 8);
            addEnch(m, "fire_aspect", "FIRE_ASPECT", 3);
            addEnch(m, "looting", "LOOT_BONUS_MOBS", 5);
            addEnch(m, "unbreaking", "DURABILITY", 6);
            addEnch(m, "mending", "MENDING", 1);
            sw.setItemMeta(m);
        }
        return sw;
    }

    // ================= 9 НОВЫХ КАСТОМНЫХ 3D МЕЧЕЙ v5.9 (ИТОГО 12 3D МЕЧЕЙ В /swords!) =================
    private static ItemStack enchantedCustomSword(String display, List<String> lore, String id, int cmd, int sharp, int fire, int loot, int unb) {
        ItemStack sw = make(Material.NETHERITE_SWORD, 1, display, lore, false, id, id, cmd);
        ItemMeta m = sw.getItemMeta();
        if (m != null) {
            addEnch(m, "sharpness", "DAMAGE_ALL", sharp);
            if (fire > 0) addEnch(m, "fire_aspect", "FIRE_ASPECT", fire);
            if (loot > 0) addEnch(m, "looting", "LOOT_BONUS_MOBS", loot);
            addEnch(m, "sweeping_edge", "SWEEPING_EDGE", 3);
            addEnch(m, "unbreaking", "DURABILITY", unb);
            addEnch(m, "mending", "MENDING", 1);
            sw.setItemMeta(m);
        }
        return sw;
    }

    /** #4/12 — ЛЕДЯНОЙ КЛИНОК СТУЖИ */
    public static ItemStack swordFrost() {
        return enchantedCustomSword(
                "&#38BDF8&l❄ 3D Ледяной Клинок Стужи [Заморозка II]",
                Arrays.asList("&8Кастомный 3D Меч Оружейной (#4/12)",
                        "&7• &#80D8FFВечная Мерзлота: &7замораживает врага на 6 сек",
                        "&7  и накладывает &bМедлительность III &7при ударе!",
                        "&7• Острота VI · Разящий Клинок III · Прочность V"),
                "sword_frost", 1831, 6, 0, 3, 5);
    }

    /** #5/12 — ГРОМОВОЙ МЕЧ ЗЕВСА */
    public static ItemStack swordZeus() {
        return enchantedCustomSword(
                "&#FACC15&l⚡ 3D Громовой Меч Зевса [Цепная Молния]",
                Arrays.asList("&8Кастомный 3D Меч Олимпа (#5/12)",
                        "&7• &#FFD740Гнев Олимпа: &7бьёт врага молнией (+4⚡ чистого урона)",
                        "&7  и пробивает блок щита &8(перезарядка 5 сек)&7!",
                        "&7• Острота VII · Заговор Огня II · Прочность V"),
                "sword_zeus", 1832, 7, 2, 3, 5);
    }

    /** #6/12 — САМУРАЙСКАЯ КАТАНА САКУРЫ */
    public static ItemStack swordKatana() {
        return enchantedCustomSword(
                "&#F472B6&l🌸 3D Самурайская Катана Сакуры [Крит +25%]",
                Arrays.asList("&8Изогнутая 3D Катана Мастера (#6/12)",
                        "&7• &#FB7185Танец Лепестков: &7+25% критического урона в прыжке!",
                        "&7• Выдаёт вам &bСкорость III &7на 3 сек при попадании!",
                        "&7• Острота VI · Разящий Клинок III · Прочность V"),
                "sword_katana", 1833, 6, 0, 3, 5);
    }

    /** #7/12 — ПРОКЛЯТЫЙ КЛИНОК ИССУШЕНИЯ */
    public static ItemStack swordWither() {
        return enchantedCustomSword(
                "&#A855F7&l💀 3D Проклятый Клинок Иссушения [Иссушение III]",
                Arrays.asList("&8Зубчатый 3D Тесак Тьмы (#7/12)",
                        "&7• &#E040FBЧёрная Метка: &7накладывает &5Иссушение III &7на 4 сек",
                        "&7  при каждом точном ударе по противнику!",
                        "&7• Острота VII · Заговор Огня II · Прочность V"),
                "sword_wither", 1834, 7, 2, 3, 5);
    }

    /** #8/12 — ЯДОВИТЫЙ ЯТАГАН ЕХИДНЫ */
    public static ItemStack swordVenom() {
        return enchantedCustomSword(
                "&#22C55E&l🐍 3D Ядовитый Ятаган Ехидны [Яд III + Слабость]",
                Arrays.asList("&8Изогнутый 3D Скимитар Чумы (#8/12)",
                        "&7• &#4ADE80Нейротоксин: &7отравляет врага (&aЯд III&7) и снижает",
                        "&7  его урон (&7Слабость I&7) на 4 секунды!",
                        "&7• Острота VI · Добыча IV · Прочность V"),
                "sword_venom", 1835, 6, 0, 4, 5);
    }

    /** #9/12 — ПРИЗРАЧНАЯ РАПИРА ТЕНЕЙ */
    public static ItemStack swordPhantom() {
        return enchantedCustomSword(
                "&#818CF8&l👻 3D Призрачная Рапира Теней [Удар в Спину]",
                Arrays.asList("&8Дуэльная 3D Рапира Ассасина (#9/12)",
                        "&7• &#38BDF8Удар из Тени: &7+30% урона при ударе в спину врагу",
                        "&7  + кратковременная Слепота &8(перезарядка 6 сек)&7!",
                        "&7• Острота VI · Разящий Клинок III · Прочность V"),
                "sword_phantom", 1836, 6, 0, 3, 5);
    }

    /** #10/12 — ПЛАМЕННЫЙ ФЛАМБЕРГ ФЕНИКСА */
    public static ItemStack swordPhoenix() {
        return enchantedCustomSword(
                "&#FB923C&l🔥 3D Пламенный Фламберг Феникса [Волна Огня]",
                Arrays.asList("&8Волнистый 3D Двуручник Солнца (#10/12)",
                        "&7• &#FACC15Вспышка Феникса: &7поджигает врагов вокруг цели",
                        "&7  и даёт вам &dРегенерацию II &7на 4 сек!",
                        "&7• Острота VII · Заговор Огня III · Прочность V"),
                "sword_phoenix", 1837, 7, 3, 3, 5);
    }

    /** #11/12 — АСТРАЛЬНЫЙ МЕЧ БЕЗДНЫ */
    public static ItemStack swordVoid() {
        return enchantedCustomSword(
                "&#38BDF8&l🌌 3D Астральный Меч Бездны [Анти-Элитры + Тяга]",
                Arrays.asList("&8Парный 3D Клинок Разлома (#11/12)",
                        "&7• &#C084FCГравитационный Разлом: &7притягивает убегающего врага",
                        "&7  к вам и мгновенно складывает его Элитры &8(КД 5 сек)&7!",
                        "&7• Острота VII · Заговор Огня II · Прочность VI"),
                "sword_void", 1838, 7, 2, 4, 6);
    }

    /** #12/12 — КОРОЛЕВСКИЙ ЭКСКАЛИБУР МИДАСА */
    public static ItemStack swordMidas() {
        return enchantedCustomSword(
                "&#FFD740&l👑 3D Королевский Экскалибур Мидаса [Золото + Щиты]",
                Arrays.asList("&8Золотой 3D Меч Императоров (#12/12)",
                        "&7• &#FFEA00Золотая Дань: &7каждый удар по врагу приносит вам",
                        "&7  &a+75$..+250$ Монет &7и даёт &eЗолотые Сердца&7!",
                        "&7• Острота VII · Добыча V · Заговор Огня II · Прочность VI"),
                "sword_midas", 1839, 7, 2, 5, 6);
    }

    public static ItemStack maceKrush() {
        Material maceMat = ColorUtil.mat("MACE", "NETHERITE_AXE");
        ItemStack mc = make(maceMat, 1, "&#FF6D00&l🔨 3D Булава Крушителя [Удар с Небес]",
                Arrays.asList("&8Тяжёлое 3D оружие сокрушения",
                        "&7• При ударе в падении создаёт ударную волну",
                        "&7  и полностью гасит ваш урон от падения!",
                        "&7• Острота VI · Прочность V · Починка"),
                false, "mace_krush", "mace_krush", 1311);
        ItemMeta m = mc.getItemMeta();
        if (m != null) {
            addEnch(m, "sharpness", "DAMAGE_ALL", 6);
            addEnch(m, "density", "DAMAGE_ALL", 5);
            addEnch(m, "wind_burst", "KNOCKBACK", 2);
            addEnch(m, "unbreaking", "DURABILITY", 5);
            addEnch(m, "mending", "MENDING", 1);
            mc.setItemMeta(m);
        }
        return mc;
    }

    public static ItemStack tridentPoseidon() {
        ItemStack tr = make(Material.TRIDENT, 1, "&#00E5FF&l🔱 3D Трезубец Посейдона [Громовержец]",
                Arrays.asList("&8Морское 3D оружие Богов",
                        "&7• Призывает молнию во врага даже в ясную погоду!",
                        "&7• Верность III · Пронзатель V · Прочность V"),
                false, "trident_poseidon", "trident_poseidon", 1312);
        ItemMeta m = tr.getItemMeta();
        if (m != null) {
            addEnch(m, "loyalty", "LOYALTY", 3);
            addEnch(m, "impaling", "IMPALING", 5);
            addEnch(m, "channeling", "CHANNELING", 1);
            addEnch(m, "unbreaking", "DURABILITY", 5);
            addEnch(m, "mending", "MENDING", 1);
            tr.setItemMeta(m);
        }
        return tr;
    }

    // ================= 4 КАСТОМНЫХ 3D ЛУКА С ПОЛНОЙ 4-КАДРОВОЙ АНИМАЦИЕЙ НАТЯЖЕНИЯ =================
    private static ItemStack makeCustomBow(String name, String id, int cmd, int powerLvl, boolean flame, List<String> lore) {
        ItemStack bow = make(Material.BOW, 1, name, lore, false, id, id, cmd);
        ItemMeta m = bow.getItemMeta();
        if (m != null) {
            addEnch(m, "power", "ARROW_DAMAGE", powerLvl);
            addEnch(m, "infinity", "ARROW_INFINITE", 1);
            if (flame) addEnch(m, "flame", "ARROW_FIRE", 1);
            addEnch(m, "unbreaking", "DURABILITY", 5);
            addEnch(m, "mending", "MENDING", 1);
            bow.setItemMeta(m);
        }
        return bow;
    }

    public static ItemStack bowIceRw() {
        return makeCustomBow("&#80D8FF&l🏹 3D Ледяной Лук Снайпера [FT×RW]", "bow_ice_rw", 1305, 6, false,
                Arrays.asList("&83D Лук с плавной анимацией натяжения",
                        "&7• Замораживает и замедляет врага при попадании!",
                        "&7• Сила VI · Бесконечность · Прочность V"));
    }

    public static ItemStack bowInferno() {
        return makeCustomBow("&#FF6D00&l🔥 3D Лук Адского Пламени [FT×RW]", "bow_inferno", 1308, 6, true,
                Arrays.asList("&83D Лук с плавной анимацией натяжения",
                        "&7• Поджигает и накладывает Иссушение II на 5 сек!",
                        "&7• Сила VI · Горящая Стрела · Бесконечность"));
    }

    public static ItemStack bowZeus() {
        return makeCustomBow("&#FACC15&l⚡ 3D Громовой Лук Зевса [Олимп]", "bow_zeus", 1309, 7, true,
                Arrays.asList("&83D Лук с плавной анимацией натяжения",
                        "&7• Бьёт врага Небесной Молнией (+3❤ чистого урона)!",
                        "&7• Сила VII · Горящая Стрела · Бесконечность"));
    }

    public static ItemStack bowVampire() {
        return makeCustomBow("&#E11D48&l🩸 3D Кровавый Лук Дракулы [Вампиризм]", "bow_vampire", 1310, 6, false,
                Arrays.asList("&83D Лук с плавной анимацией натяжения",
                        "&7• Исцеляет стрелка на 35% от нанесённого урона!",
                        "&7• Сила VI · Бесконечность · Прочность V"));
    }

    // ================= 6 РАЗНОВИДНОСТЕЙ 3D СТРЕЛ =================
    public static ItemStack arrowIce(int amt) {
        return make(Material.ARROW, amt, "&#80D8FF&l❄ 3D Ледяная Стрела РВ",
                Arrays.asList("&7Замораживает противника (Медлительность IV на 5 сек)!"),
                true, "arrow_ice", "arrow_ice", 1321);
    }

    public static ItemStack arrowExplosive(int amt) {
        return make(Material.ARROW, amt, "&#FF6D00&l💥 3D Разрывная Стрела Крушителя",
                Arrays.asList("&7Создаёт ударный взрыв при попадании (+3❤ урона)!"),
                true, "arrow_explosive", "arrow_explosive", 1322);
    }

    public static ItemStack arrowLightning(int amt) {
        return make(Material.ARROW, amt, "&#FACC15&l⚡ 3D Громовая Стрела Зевса",
                Arrays.asList("&7Призывает молнию в точку попадания и сбивает щит!"),
                true, "arrow_lightning", "arrow_lightning", 1323);
    }

    public static ItemStack arrowVampire(int amt) {
        return make(Material.ARROW, amt, "&#E11D48&l🩸 3D Вампирская Стрела",
                Arrays.asList("&7Вытягивает 2.5❤ здоровья у врага и лечит стрелка!"),
                true, "arrow_vampire", "arrow_vampire", 1324);
    }

    public static ItemStack arrowPoison(int amt) {
        return make(Material.ARROW, amt, "&#22C55E&l☠ 3D Стрела Чумы Ехидны",
                Arrays.asList("&7Накладывает Отравление III, Иссушение II и Слабость!"),
                true, "arrow_poison", "arrow_poison", 1325);
    }

    public static ItemStack arrowAntiFly(int amt) {
        return make(Material.ARROW, amt, "&#A855F7&l⛓ 3D Анти-Флай Стрела ПВО",
                Arrays.asList("&7Мгновенно складывает Элитры врага в полёте и тянет вниз!"),
                true, "arrow_anti_fly", "arrow_anti_fly", 1326);
    }

    public static ItemStack arrowThor(int amt) {
        return make(Material.ARROW, amt, "&#00E5FF&l⚡ 3D Стрела Тора",
                Arrays.asList("&7Цепная молния: +4❤ и бьёт до 3 целей рядом!"),
                true, "arrow_thor", "arrow_thor", 1327);
    }

    public static ItemStack arrowFrostNova(int amt) {
        return make(Material.ARROW, amt, "&#80D8FF&l❄ 3D Стрела Морозной Новы",
                Arrays.asList("&7Замораживает ВСЕХ врагов в радиусе 4 блоков!"),
                true, "arrow_frostnova", "arrow_frostnova", 1328);
    }

    public static ItemStack arrowGold(int amt) {
        return make(Material.ARROW, amt, "&#FFD740&l💰 3D Золотая Стрела",
                Arrays.asList("&7Крадёт до 150$ у игрока-врага (с моба +150$)!"),
                true, "arrow_gold", "arrow_gold", 1329);
    }

    // ================= 6 РАЗНОВИДНОСТЕЙ 3D КАСТОМНОГО ТНТ ДЛЯ РЕЙДОВ =================
    public static ItemStack tntStorm(int amt) {
        return make(Material.TNT, amt, "&#FDE047&l⚡ Грозовой ТНТ",
                Arrays.asList("&7Тактический заряд: урон и подсветка врагов", "&7Без молний, пожаров и разрушения блоков."),
                true, "tnt_storm", "tnt_storm", 1607);
    }

    public static ItemStack tntGravity(int amt) {
        return make(Material.TNT, amt, "&#C4B5FD&l✦ Гравитационный ТНТ",
                Arrays.asList("&7Притягивает врагов к центру и замедляет их.", "&7Не разрушает блоки."),
                true, "tnt_gravity", "tnt_gravity", 1608);
    }

    public static ItemStack tntEnder(int amt) {
        return make(Material.TNT, amt, "&#A78BFA&l✧ Эндер-ТНТ",
                Arrays.asList("&7Ослепляет и дезориентирует врагов.", "&7Не разрушает блоки."),
                true, "tnt_ender", "tnt_ender", 1609);
    }

    public static ItemStack tntTier1(int amt) {
        return make(Material.TNT, amt, "&#EF4444&l🧨 3D ТНТ TIER-II [Усиленный ×2]",
                Arrays.asList("&8Кастомный 3D Динамит для Рейдов", "",
                        "&7Поставь в мире выживания — авто-поджиг &e3.5 сек&7!",
                        "&7• Мощность взрыва увеличена в &c2 раза &7(Power 7.0)!"),
                true, "tnt_tier1", "tnt_tier1", 1601);
    }

    public static ItemStack tntBlack(int amt) {
        return make(Material.TNT, amt, "&#A855F7&l💣 3D ТНТ BLACK [Ломает Обсидиан и Воду!]",
                Arrays.asList("&8Легендарный Рейд-Динамит FunTime × ReallyWorld", "",
                        "&7Поставь у базы врага — авто-поджиг &e3.5 сек&7!",
                        "&7• &#00E5FFИспаряет воду и лаву &7в радиусе 5 блоков!",
                        "&7• &#E040FBПробивает Обсидиан и Плачущий Обсидиан&7!"),
                true, "tnt_black", "tnt_black", 1602);
    }

    public static ItemStack tntNuke(int amt) {
        return make(Material.TNT, amt, "&#84CC16&l☢ 3D Ядерный ТНТ Крушителя [Кратер ×3.5]",
                Arrays.asList("&8Сверхмощный Рейд-Динамит", "",
                        "&7Поставь на рейде — авто-поджиг &e4.5 сек&7!",
                        "&7• Огромный кратер (Power 10.0) + Огонь + Иссушение II!"),
                true, "tnt_nuke", "tnt_nuke", 1603);
    }

    public static ItemStack tntFlash(int amt) {
        return make(Material.TNT, amt, "&#FACC15&l⚡ 3D Штурмовой Мгновенный ТНТ [1 сек]",
                Arrays.asList("&8Тактический PvP-Динамит", "",
                        "&7Взрывается всего через &e1.0 секунду &7после установки!",
                        "&7• Отбрасывает врагов и даёт 30 сек режима ПвП!"),
                true, "tnt_flash", "tnt_flash", 1604);
    }

    public static ItemStack tntCryo(int amt) {
        return make(Material.TNT, amt, "&#38BDF8&l❄ 3D Ледяной Крио-ТНТ [Заморозка]",
                Arrays.asList("&8Зимний Тактический Динамит 2026", "",
                        "&7• Замораживает воду в Синий Лёд, лаву — в Обсидиан!",
                        "&7• Замораживает всех врагов в радиусе &b8 блоков &7на 6 сек!"),
                true, "tnt_cryo", "tnt_cryo", 1605);
    }

    public static ItemStack tntMiner(int amt) {
        return make(Material.TNT, amt, "&#FB923C&l⛏ 3D Шахтёрский Буровой ТНТ [100% Руда]",
                Arrays.asList("&8Промышленный Динамит Джамшута", "",
                        "&7• Выкапывает шахту &e9×9×9 &7со &a100% выпадением",
                        "&7  всех руд и авто-плавкой слитков к вашим ногам!"),
                true, "tnt_miner", "tnt_miner", 1606);
    }

    public static ItemStack bowIce() {
        return bowIceRw();
    }

    public static ItemStack armorPiece(String setKey, int slotIdx) {
        String slot = slotIdx == 0 ? "helmet" : slotIdx == 1 ? "chestplate" : slotIdx == 2 ? "leggings" : "boots";
        return customArmorPiece(setKey, slot);
    }

    // ================= 6 ПОЛНЫХ КАСТОМНЫХ СЕТОВ БРОНИ (24 ПРЕДМЕТА С 3D ИКОНКАМИ + ARMOR TRIMS + СЕТ-БОНУСЫ!) =================
    public static ItemStack customArmorPiece(String setKey, String slot) {
        Material mat;
        String pieceRu;
        int slotIdx;
        if ("helmet".equals(slot)) { mat = Material.NETHERITE_HELMET; pieceRu = "Шлем"; slotIdx = 0; }
        else if ("chestplate".equals(slot)) { mat = Material.NETHERITE_CHESTPLATE; pieceRu = "Нагрудник"; slotIdx = 1; }
        else if ("leggings".equals(slot)) { mat = Material.NETHERITE_LEGGINGS; pieceRu = "Поножи"; slotIdx = 2; }
        else { mat = Material.NETHERITE_BOOTS; pieceRu = "Ботинки"; slotIdx = 3; }

        String colorPref;
        String setTitle;
        String bonusLine1;
        String bonusLine2;
        String trimMat;
        String trimPat;
        int prot = 5;
        int unb = 6;
        int thorns = 0;
        int baseCmd;

        if ("owner".equals(setKey)) {
            colorPref = "&#FF1744&l👑 &#FFD740&l";
            setTitle = "БОЖЕСТВЕННЫЙ " + pieceRu.toUpperCase() + " OWNER";
            bonusLine1 = " &#FF1744• Полный сет (4/4): +12 ❤ · Сила III · Скорость III";
            bonusLine2 = " &#FFD740• Сопротивление II · Регенерация II · Огнестойкость";
            trimMat = "gold"; trimPat = "silence"; prot = 6; unb = 7; thorns = 3; baseCmd = 1721;
        } else if ("krush".equals(setKey)) {
            colorPref = "&#FF6D00&l🔥 ";
            setTitle = "3D " + pieceRu + " Крушителя";
            bonusLine1 = " &#FF6D00• Полный сет (4/4): +6 ❤ · Сила II · Огнестойкость";
            bonusLine2 = " &#FACC15• Несокрушимая броня для жёсткого PvP!";
            trimMat = "redstone"; trimPat = "silence"; unb = 7; baseCmd = 1701;
        } else if ("dracula".equals(setKey)) {
            colorPref = "&#E11D48&l🩸 ";
            setTitle = "3D " + pieceRu + " Дракулы";
            bonusLine1 = " &#E11D48• Полный сет (4/4): +4 ❤ · Скорость II · Ночное Зрение";
            bonusLine2 = " &#FB7185• Вампиризм: лечит +20% от каждого удара!";
            trimMat = "amethyst"; trimPat = "vex"; unb = 6; baseCmd = 1705;
        } else if ("frost".equals(setKey)) {
            colorPref = "&#38BDF8&l❄ ";
            setTitle = "3D " + pieceRu + " Северной Стужи [2026]";
            bonusLine1 = " &#38BDF8• Полный сет (4/4): +6 ❤ · Скорость II · Регенерация I";
            bonusLine2 = " &#BAE6FD• Замораживает врагов при ударе по вам!";
            trimMat = "diamond"; trimPat = "tide"; unb = 7; baseCmd = 1709;
        } else if ("zeus".equals(setKey)) {
            colorPref = "&#FACC15&l⚡ ";
            setTitle = "3D " + pieceRu + " Громовержца Зевса";
            bonusLine1 = " &#FACC15• Полный сет (4/4): +4 ❤ · Скорость III · Прыгучесть II";
            bonusLine2 = " &#7DD3FC• 20% шанс ударить атакующего молнией!";
            trimMat = "gold"; trimPat = "eye"; unb = 6; baseCmd = 1713;
        } else {
            colorPref = "&#A855F7&l☠ ";
            setTitle = "3D " + pieceRu + " Теневого Фантома";
            bonusLine1 = " &#A855F7• Полный сет (4/4): +3 ❤ · Скорость III · Сила II";
            bonusLine2 = " &#38BDF8• +18% критического урона в прыжке!";
            trimMat = "quartz"; trimPat = "spire"; unb = 6; baseCmd = 1717;
        }

        String unbRoman = unb >= 7 ? "VII" : (unb == 6 ? "VI" : "V");
        String modelId = "armor_" + setKey + "_" + slot;
        String customId = "armor_" + setKey + "_" + slot;
        List<String> lore = Arrays.asList(
                "&8Кастомная 3D Броня KINGGRIEF v6.4.0",
                "",
                "&7Бонус комплекта:",
                bonusLine1,
                bonusLine2,
                " &#00E676• Титановая Закалка Брони (Прочность " + unbRoman + " — ломается до ×12 дольше!)",
                "",
                "&bЗащита " + (prot == 6 ? "VI" : "V") + " · Прочность " + unbRoman + " · Починка"
        );
        ItemStack piece = make(mat, 1, colorPref + setTitle, lore, false, customId, modelId, baseCmd + slotIdx);
        ItemMeta m = piece.getItemMeta();
        if (m != null) {
            addEnch(m, "protection", "PROTECTION_ENVIRONMENTAL", prot);
            addEnch(m, "unbreaking", "DURABILITY", unb);
            addEnch(m, "mending", "MENDING", 1);
            if (thorns > 0) addEnch(m, "thorns", "THORNS", thorns);
            if ("boots".equals(slot)) {
                addEnch(m, "feather_falling", "PROTECTION_FALL", 4);
                addEnch(m, "depth_strider", "DEPTH_STRIDER", 3);
            }
            if ("helmet".equals(slot)) {
                addEnch(m, "aqua_affinity", "WATER_WORKER", 1);
                addEnch(m, "respiration", "OXYGEN", 3);
            }
            ColorUtil.applyArmorTrimSafe(m, trimMat, trimPat);
            piece.setItemMeta(m);
        }
        return piece;
    }

    public static ItemStack swordSever() {
        return make(Material.NETHERITE_SWORD, 1, "&#80D8FF&l❄ 3D Клинок Севера",
                Arrays.asList("&7Замораживает врага при ударе:",
                        "&#80D8FF• Заморозка + Медлительность IV на 5 сек!"),
                false, "sword_sever", "sword_sever", 1841);
    }

    public static ItemStack swordZima() {
        return make(Material.NETHERITE_SWORD, 1, "&#AEEA00&l🌨 3D Молот Зимы",
                Arrays.asList("&7Ледяная нова при ударе:",
                        "&#AEEA00• Отбрасывает и замедляет ВСЕХ врагов в 3 блоках!"),
                false, "sword_zima", "sword_zima", 1842);
    }

    public static ItemStack talZvezdy() {
        return make(Material.NETHER_STAR, 1, "&#FFD740&l⭐ 3D Талисман Звезды",
                Arrays.asList("&7Пассивно в левой руке:",
                        "&#FFD740• +25% монет с фармилки и охоты!"),
                true, "tal_zvezdy", "tal_zvezdy", 1843);
    }

    public static ItemStack talMoroza() {
        return make(Material.NETHER_STAR, 1, "&#80D8FF&l🧊 3D Талисман Мороза",
                Arrays.asList("&7Пассивно в левой руке:",
                        "&#80D8FF• Атакующий вас враг замерзает на 3 сек!"),
                true, "tal_moroza", "tal_moroza", 1844);
    }

    public static ItemStack mysteryEgg(int amt) {
        return make(Material.PIG_SPAWN_EGG, amt, "&#E040FB&l🥚 Загадочное Яйцо Джамшута",
                Arrays.asList("&8Предмет для спавнеров",
                        "&7ПКМ по спавнеру — меняет моба внутри на:",
                        "&fКорову, Крипера, Ифрита, Скелета или Зомби!"),
                true, "mystery_egg", null, 0);
    }

    public static ItemStack bookArmorUnbreaking6(int amt) {
        return buildArmorUnbreakingBook(amt, 6);
    }

    public static ItemStack bookArmorUnbreaking7(int amt) {
        return buildArmorUnbreakingBook(amt, 7);
    }

    private static ItemStack buildArmorUnbreakingBook(int amt, int level) {
        String roman = level >= 7 ? "VII" : "VI";
        String accent = level >= 7 ? "&#FF1744&l📕 Книга Брони: &#FFD740&lПрочность VII [7 LVL]"
                : "&#38BDF8&l📘 Книга Брони: &#00E5FF&lПрочность VI [6 LVL]";
        String chanceTxt = level >= 7 ? "88%" : "82%";
        ItemStack book = make(Material.ENCHANTED_BOOK, Math.max(1, amt), accent,
                Arrays.asList(
                        "&8Древний Манускрипт Закалки Брони",
                        "",
                        "&7Накладывает кастомное зачарование:",
                        " &#00E676• Прочность " + roman + " (" + level + " уровень)",
                        " &#80D8FF• " + chanceTxt + " шанс полностью поглотить износ брони в ПвП!",
                        "",
                        "&#FFD740Как применить:",
                        " &f1) Перетащи книгу курсором на Броню / Элитры / Щит в инвентаре",
                        " &f2) Или соедини с бронёй в обычной Наковальне!"
                ),
                false, "book_armor_unb_" + level, null, 0);
        ItemMeta meta = book.getItemMeta();
        if (meta != null) {
            Enchantment unb = ColorUtil.ench("unbreaking", "DURABILITY");
            if (unb != null) {
                if (meta instanceof EnchantmentStorageMeta) {
                    ((EnchantmentStorageMeta) meta).addStoredEnchant(unb, level, true);
                } else {
                    meta.addEnchant(unb, level, true);
                }
            }
            book.setItemMeta(meta);
        }
        return book;
    }

    public static boolean isArmorOrShieldItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        String tn = item.getType().name();
        return tn.endsWith("_HELMET") || tn.endsWith("_CHESTPLATE")
                || tn.endsWith("_LEGGINGS") || tn.endsWith("_BOOTS")
                || "TURTLE_HELMET".equals(tn) || "ELYTRA".equals(tn) || "SHIELD".equals(tn);
    }

    public static boolean applyArmorUnbreakingLevel(ItemStack target, int level) {
        if (!isArmorOrShieldItem(target)) return false;
        Enchantment unb = ColorUtil.ench("unbreaking", "DURABILITY");
        if (unb == null) return false;
        if (target.getEnchantmentLevel(unb) >= level) return false;
        ItemMeta m = target.getItemMeta();
        if (m == null) return false;
        m.addEnchant(unb, level, true);
        String roman = level >= 7 ? "VII" : "VI";
        String badgeLine = ColorUtil.c("&#00E676🛡 Закалка Кузнеца: Прочность " + roman + " [" + level + " LVL]");
        List<String> lore = m.hasLore() && m.getLore() != null
                ? new ArrayList<String>(m.getLore())
                : new ArrayList<String>();
        boolean replaced = false;
        for (int i = 0; i < lore.size(); i++) {
            String plain = lore.get(i);
            if (plain != null && plain.contains("Закалка Кузнеца:")) {
                lore.set(i, badgeLine);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            lore.add(badgeLine);
        }
        m.setLore(lore);
        target.setItemMeta(m);
        return true;
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

    // ================= СБАЛАНСИРОВАННЫЕ КИТЫ v5.7 (DRAGON /FREE -> OWNER EXCLUSIVE!) =================
    // ================= v6.4.0: 3 НОВЫХ ТОТЕМА С ЗАРЯДАМИ =================
    public static ItemStack totemLedyanoy(int charges) {
        int ch = Math.max(1, Math.min(3, charges));
        ItemStack it = make(Material.TOTEM_OF_UNDYING, 1, "&#7DD3FC&l🧊 Тотем Ледяного Спасения [" + ch + "/3]",
                Arrays.asList("&8Новый многоразовый тотем v6.4.0", "",
                        "&7Осталось зарядов: &#00E676&l" + ch + " / 3",
                        "&7При смертельном ударе:", " &#7DD3FC• Полное исцеление 20❤",
                        " &#00E676• Сопротивление III + Поглощение IV (+8 Золотых ❤)",
                        " &#80D8FF• Ледяной взрыв: враги в радиусе 7 замерзают и тормозят"),
                true, "totem_ledyanoy", "totem_ledyanoy", 1250);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.getPersistentDataContainer().set(CHARGES_KEY, PersistentDataType.INTEGER, Integer.valueOf(ch));
            it.setItemMeta(m);
        }
        return it;
    }

    public static ItemStack totemVoskresheniya(int charges) {
        int ch = Math.max(1, Math.min(5, charges));
        ItemStack it = make(Material.TOTEM_OF_UNDYING, 1, "&#FFD740&l✨ Тотем Воскрешения [" + ch + "/5]",
                Arrays.asList("&8Легендарный тотем v6.4.0 — 5 зарядов!", "",
                        "&7Осталось зарядов: &#00E676&l" + ch + " / 5",
                        "&7При смертельном ударе:", " &#FFD740• Полное исцеление 20❤ + Поглощение VI (+12 Золотых ❤)",
                        " &#FF4081• Регенерация III на 30 сек", " &#B388FF• Взрывная волна отбрасывает врагов",
                        " &#00E676• +2 500$ и +5 Риликов за каждое воскрешение"),
                true, "totem_voskresheniya", "totem_voskresheniya", 1251);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.getPersistentDataContainer().set(CHARGES_KEY, PersistentDataType.INTEGER, Integer.valueOf(ch));
            it.setItemMeta(m);
        }
        return it;
    }

    public static ItemStack totemSanty(int charges) {
        int ch = Math.max(1, Math.min(3, charges));
        ItemStack it = make(Material.TOTEM_OF_UNDYING, 1, "&#FF4081&l🎅 Тотем Деда Мороза [" + ch + "/3]",
                Arrays.asList("&8Новогодний тотем v6.4.0", "",
                        "&7Осталось зарядов: &#00E676&l" + ch + " / 3",
                        "&7При смертельном ударе:", " &#FF4081• Полное исцеление 20❤ + Сопротивление II",
                        " &#FFD740• Дед Мороз рассыпает подарки: +5 000$ и +5 Снежинок ❄",
                        " &#80D8FF• Снежная буря ослепляет врагов вокруг"),
                true, "totem_santy", "totem_santy", 1252);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.getPersistentDataContainer().set(CHARGES_KEY, PersistentDataType.INTEGER, Integer.valueOf(ch));
            it.setItemMeta(m);
        }
        return it;
    }

    // ================= v6.4.0: ЛИНЕЙКА «МЕГА» =================
    public static ItemStack megaHeal(int amt) {
        return make(Material.GLISTERING_MELON_SLICE, Math.max(1, amt), "&#FF4081&l💗 МЕГА-ХИЛ [FT×RW]",
                Arrays.asList("&8Мгновенный мега-хил v6.4.0 (ПКМ)", "",
                        " &#FF4081• Мгновенно восстанавливает 100% ХП",
                        " &#FFD740• +8 Золотых ❤ (Поглощение IV) на 60 сек",
                        " &#00E676• Регенерация III на 20 сек", "&7Кулдаун: &f45 сек"),
                true, "mega_heal", "mega_heal", 1300);
    }

    public static ItemStack megaBuff(int amt) {
        return make(Material.NETHER_STAR, Math.max(1, amt), "&#FFD740&l⚡ МЕГА-БАФФ [FT×RW]",
                Arrays.asList("&8Мега-геймплей v6.4.0 (ПКМ)", "",
                        " &#FF5252• Сила III на 3 мин", " &#FFD740• Спешка III на 3 мин",
                        " &#40C4FF• Скорость II + Прыгучесть II на 3 мин",
                        " &#00E676• Регенерация II на 3 мин", "&7Кулдаун: &f90 сек"),
                true, "mega_buff", "mega_buff", 1301);
    }

    public static ItemStack megaShield(int amt) {
        return make(Material.HEART_OF_THE_SEA, Math.max(1, amt), "&#00E5FF&l🛡 МЕГА-ЩИТ [FT×RW]",
                Arrays.asList("&8Мега-защита v6.4.0 (ПКМ)", "",
                        " &#00E676• Сопротивление III на 60 сек (-60% урона)",
                        " &#FFD740• Поглощение IV (+8 Золотых ❤) на 90 сек",
                        " &#FF9100• Огнестойкость на 60 сек", "&7Кулдаун: &f60 сек"),
                true, "mega_shield", "mega_shield", 1302);
    }

    // ================= v6.4.0: НОВЫЕ ЗАЧАРОВАНИЯ (СВИТКИ) =================
    public static final String[] SCROLL_IDS = new String[]{"ice", "vampire", "blast", "smelt"};

    public static int scrollIndex(String id) {
        for (int i = 0; i < SCROLL_IDS.length; i++) if (SCROLL_IDS[i].equals(id)) return i;
        return -1;
    }

    public static String scrollName(String id) {
        if ("ice".equals(id)) return "&#7DD3FC&l❄ Свиток Зачарования: Ледяной Клинок";
        if ("vampire".equals(id)) return "&#FF1744&l🩸 Свиток Зачарования: Вампиризм";
        if ("blast".equals(id)) return "&#FF9100&l💥 Свиток Зачарования: Взрывной Удар";
        if ("smelt".equals(id)) return "&#FFD740&l🔥 Свиток Зачарования: Авто-Плавка";
        return "&#7DD3FC&l❄ Свиток Зачарования";
    }

    public static String scrollDesc(String id) {
        if ("ice".equals(id)) return " &#7DD3FC• Жертва замерзает и получает Замедление II (урон от мороза)";
        if ("vampire".equals(id)) return " &#FF1744• Вытягивает 1❤ у врага за каждый удар (×уровень)";
        if ("blast".equals(id)) return " &#FF9100• Мини-взрыв: 2❤ урона врагам в радиусе 3 (×уровень)";
        return " &#FFD740• Руда сразу плавится в слитки ×(1+уровень) — без печки!";
    }

    public static String scrollMark(String id) {
        if ("ice".equals(id)) return "Ледяной Клинок";
        if ("vampire".equals(id)) return "Вампиризм";
        if ("blast".equals(id)) return "Взрывной Удар";
        return "Авто-Плавка";
    }

    public static String romanLevel(int v) {
        return v >= 3 ? "III" : (v == 2 ? "II" : "I");
    }

    public static ItemStack enchantScroll(String id) {
        int idx = scrollIndex(id);
        if (idx < 0) return null;
        return make(Material.PAPER, 1, scrollName(id),
                Arrays.asList("&8Новое зачарование v6.4.0 — свиток", "", scrollDesc(id), "",
                        "&7Применение: &fсвиток — в ПРАВУЮ руку&7,",
                        "&fмеч/инструмент — в ЛЕВУЮ руку&7 (off-hand),",
                        "&7затем &fПКМ&7. Макс. уровень: &#FFD740III"),
                true, "scroll_" + id, "scroll_" + id, 1400 + idx);
    }

    public static NamespacedKey enchKey(String id) {
        return new NamespacedKey("kinggrief", "kg_ench_" + id);
    }

    /** v6.4.0: уровень кастомного зачарования на инструменте (0 = нет). */
    public static int getToolEnchant(ItemStack tool, String id) {
        if (tool == null || !tool.hasItemMeta() || id == null) return 0;
        try {
            ItemMeta m = tool.getItemMeta();
            if (m == null) return 0;
            Integer v = m.getPersistentDataContainer().get(enchKey(id), PersistentDataType.INTEGER);
            return v == null ? 0 : v.intValue();
        } catch (Throwable t) {
            return 0;
        }
    }

    /** v6.4.0: применяет свиток к предмету. Возвращает новый уровень или -1 (нельзя применить). */
    public static int applyScrollToTool(ItemStack tool, String id) {
        if (tool == null || scrollIndex(id) < 0) return -1;
        ItemMeta m = tool.getItemMeta();
        if (m == null) return -1;
        int cur = getToolEnchant(tool, id);
        if (cur >= 3) return -1;
        int next = cur + 1;
        m.getPersistentDataContainer().set(enchKey(id), PersistentDataType.INTEGER, Integer.valueOf(next));
        List<String> lore = (m.hasLore() && m.getLore() != null)
                ? new ArrayList<String>(m.getLore()) : new ArrayList<String>();
        String mark = scrollMark(id);
        for (int i = lore.size() - 1; i >= 0; i--) {
            String ln = lore.get(i);
            if (ln != null && ln.contains(mark)) lore.remove(i);
        }
        lore.add("");
        lore.add(ColorUtil.c("&#7DD3FC" + mark + " " + romanLevel(next)));
        m.setLore(lore);
        tool.setItemMeta(m);
        return next;
    }

    public static List<ItemStack> buildKitItems(String kitId) {
        String k = kitId == null ? "start" : kitId.toLowerCase();
        List<ItemStack> items = new ArrayList<ItemStack>();
        if ("owner".equals(k)) {
            // ЭКСКЛЮЗИВНЫЙ БОЖЕСТВЕННЫЙ КИТ OWNER — ТОЛЬКО ДЛЯ OWNER! (Включает Молот OWNER 10×10, Кирку Крушителя 3×3, Косу Жнеца, Секиру Тора и все 12 Кирок!)
            items.add(swordOwner());
            items.add(scytheReaper());
            items.add(axeThor());
            items.add(hammerOwner());
            items.add(pickKrush());
            items.add(bowZeus());
            items.add(customArmorPiece("owner", "helmet"));
            items.add(customArmorPiece("owner", "chestplate"));
            items.add(customArmorPiece("owner", "leggings"));
            items.add(customArmorPiece("owner", "boots"));
            items.add(elytraSanta());
            items.add(talOwner(5));
            items.add(lootMagnet());
            items.add(sharSanty());
            // v6.4.0: новые шары, тотемы, МЕГА-расходники и свитки зачарований!
            items.add(sharKvadrat());
            items.add(sharKronosa());
            items.add(sharGeliosa());
            items.add(sharMorozko());
            items.add(totemVoskresheniya(5));
            items.add(totemSanty(3));
            items.add(megaHeal(32));
            items.add(megaBuff(16));
            items.add(megaShield(16));
            items.add(enchantScroll("ice"));
            items.add(enchantScroll("vampire"));
            items.add(enchantScroll("blast"));
            items.add(enchantScroll("smelt"));
            items.add(new ItemStack(Material.FIREWORK_ROCKET, 64));
            items.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 32));
            items.add(new ItemStack(Material.GOLDEN_APPLE, 64));
            items.add(new ItemStack(Material.GOLDEN_CARROT, 64));
            items.add(new ItemStack(Material.ENDER_PEARL, 16));
            items.add(new ItemStack(Material.ENDER_PEARL, 16));
            items.add(plast(16));
            items.add(trapka(16));
            items.add(dezorient(8));
            items.add(orbRecall(8));
            items.add(packedShulker(Material.RED_SHULKER_BOX, "&#FF1744&l👑 Божественный Арсенал OWNER #1",
                    Arrays.asList("&7Все кастомные Талисманы, Стрелы и Зелья ВЛАДЕЛЬЦА!"),
                    maceKrush(), tridentPoseidon(), talImperatora(),
                    talKaratelya(), talZeusa(), talDraculy(), talKolossa(),
                    talZhneca(), talSevera(), talBezdn(2), talFeniksa(3),
                    bozhyaKara(8), gravSfera(8),
                    bozhyaAura(8), yavnayaPyl(8), ognennySmerch(8), snegZavesa(8),
                    arrowIce(64), arrowExplosive(64), arrowLightning(64),
                    arrowVampire(64), arrowPoison(64), arrowAntiFly(64),
                    potionKiller(2), potionMedic(2),
                    orbRecall(8),
                    bookArmorUnbreaking7(4),
                    new ItemStack(Material.NETHERITE_BLOCK, 16),
                    claimBlock(Material.EMERALD_ORE, 30, "&#00E676💚 Изумрудный Приват 61×61")));
            items.add(packedShulker(Material.BLACK_SHULKER_BOX, "&#FFD740&l☢ Мега-Шалкер ТНТ и Тактики OWNER #2",
                    Arrays.asList("&7Все 6 видов ТНТ + 8 новых командных артефактов v6.4.0!"),
                    tntBlack(32), tntNuke(16), tntTier1(32),
                    tntFlash(32), tntCryo(32), tntMiner(32),
                    teamTotem(16), battleAdrenaline(16), swapOrb(16),
                    iceDome(16), vampirePulse(16), empShock(16),
                    smokeBomb(16), grapplingHook(),
                    new ItemStack(Material.OBSIDIAN, 64),
                    new ItemStack(Material.END_CRYSTAL, 64),
                    new ItemStack(Material.RESPAWN_ANCHOR, 32),
                    new ItemStack(Material.GLOWSTONE, 64)));
            items.add(packedShulker(Material.CYAN_SHULKER_BOX, "&#00E5FF&l⛏ Полная Коллекция 12 Кирок OWNER #3",
                    Arrays.asList("&7Все 12 кастомных 3D Кирок и Молотов (включая Молот 10×10 и Крушителя 3×3)!"),
                    hammerOwner(), pickKrush(), pickTunnel(), pickMeteor(),
                    pickVein(), pickFrost(), pickMolten(), pickMagnet(),
                    pickObsidian(), pickMidas(), pickDjamshut(), pickSpawner(),
                    new ItemStack(Material.DIAMOND_BLOCK, 64),
                    new ItemStack(Material.EMERALD_BLOCK, 64)));
            items.add(packedShulker(Material.PURPLE_SHULKER_BOX, "&#FF1744&l⚔ Полная Оружейная 12 Мечей OWNER #4",
                    Arrays.asList("&7Все 12 кастомных 3D Мечей v6.4.0 + Коса Жнеца, Секира Тора и 4 3D Лука!"),
                    swordOwner(), swordKrush(), swordDracula(),
                    swordFrost(), swordZeus(), swordKatana(),
                    swordWither(), swordVenom(), swordPhantom(),
                    swordPhoenix(), swordVoid(), swordMidas(),
                    scytheReaper(), axeThor(), maceKrush(), tridentPoseidon(),
                    bowZeus(), bowInferno(), bowVampire(), bowIceRw()));
            return items;
        }
        if ("helper".equals(k)) {
            // HELPER is intentionally below ML.MODER: modest diamond gear, no mythic shulkers.
            items.add(gear(Material.DIAMOND_SWORD, "&#7DD3FC⚔ Меч HELPER", 3, 3, 0));
            items.add(gear(Material.DIAMOND_PICKAXE, "&#7DD3FC⛏ Кирка HELPER", 3, 3, 1));
            items.add(gear(Material.DIAMOND_HELMET, "&#7DD3FCШлем HELPER", 3, 3, 0));
            items.add(gear(Material.DIAMOND_CHESTPLATE, "&#7DD3FCНагрудник HELPER", 3, 3, 0));
            items.add(gear(Material.DIAMOND_LEGGINGS, "&#7DD3FCПоножи HELPER", 3, 3, 0));
            items.add(gear(Material.DIAMOND_BOOTS, "&#7DD3FCБотинки HELPER", 3, 3, 0));
            items.add(new ItemStack(Material.GOLDEN_APPLE, 12));
            items.add(new ItemStack(Material.GOLDEN_CARROT, 64));
            items.add(new ItemStack(Material.ENDER_PEARL, 16));
            items.add(plast(3));
            items.add(trapka(2));
            return items;
        }
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
        } else if ("dragon".equals(k) || "pegas".equals(k) || "cobra".equals(k) || "dracula".equals(k) || "bunny".equals(k) || "rabbit".equals(k)) {
            int lv = "rabbit".equals(k) ? 4 : ("dragon".equals(k) ? 2 : 3);
            int mult = "rabbit".equals(k) ? 2 : 1;
            String tag = k.toUpperCase();
            items.add("dracula".equals(k) ? swordDracula() : gear(Material.DIAMOND_SWORD, "&#38BDF8⚔ Меч " + tag, lv, 3, 1));
            items.add(gear(Material.DIAMOND_PICKAXE, "&#38BDF8⛏ Кирка " + tag, Math.max(3, lv), 3, 2));
            items.add(gear(Material.DIAMOND_AXE, "&#38BDF8🪓 Топор " + tag, lv, 3, 0));
            items.add(gear(Material.BOW, "&#38BDF8🏹 Лук " + tag, lv, 3, 1));
            items.add(gear(Material.DIAMOND_HELMET, "&#38BDF8Шлем " + tag, lv, 3, 0));
            items.add(gear(Material.DIAMOND_CHESTPLATE, "&#38BDF8Нагрудник " + tag, lv, 3, 0));
            items.add(gear(Material.DIAMOND_LEGGINGS, "&#38BDF8Поножи " + tag, lv, 3, 0));
            items.add(gear(Material.DIAMOND_BOOTS, "&#38BDF8Ботинки " + tag, lv, 3, 0));
            items.add(new ItemStack(Material.SHIELD, 1));
            items.add(new ItemStack(Material.GOLDEN_APPLE, 12 * mult));
            items.add(new ItemStack(Material.GOLDEN_CARROT, 64));
            items.add(new ItemStack(Material.ENDER_PEARL, 16));
            items.add(new ItemStack(Material.ENDER_PEARL, 16));
            items.add(new ItemStack(Material.CHORUS_FRUIT, 16 * mult));
            items.add(new ItemStack(Material.TOTEM_OF_UNDYING, mult));
            items.add(plast(3 * mult));
            items.add(trapka(2 * mult));
            items.add(potionFlash(1));
            items.add(potionMedic(1));
            items.add(packedShulker(Material.LIGHT_BLUE_SHULKER_BOX, "&#38BDF8&l📦 Сундук Ресурсов " + tag,
                    Arrays.asList("&7Поставь на базе и забери ресурсы привилегии!"),
                    new ItemStack(Material.DIAMOND_BLOCK, 6 * mult),
                    new ItemStack(Material.GOLD_BLOCK, 12 * mult),
                    new ItemStack(Material.IRON_BLOCK, 16 * mult),
                    new ItemStack(Material.EMERALD_BLOCK, 6 * mult),
                    new ItemStack(Material.OBSIDIAN, 64),
                    tntTier1(4 * mult),
                    new ItemStack(Material.OAK_LOG, 64),
                    new ItemStack(Material.COBWEB, 24),
                    new ItemStack(Material.GOLDEN_CARROT, 64),
                    new ItemStack(Material.EXPERIENCE_BOTTLE, 64),
                    arrowIce(32),
                    claimBlock(Material.IRON_ORE, 12, "&#38BDF8🛡 Железный Приват 25×25")));
        } else if ("vampire".equals(k) || "tiger".equals(k) || "god".equals(k)) {
            boolean neth = "god".equals(k);
            String tag = k.toUpperCase();
            items.add("vampire".equals(k) ? swordDracula() : gear(neth ? Material.NETHERITE_SWORD : Material.DIAMOND_SWORD, "&#FACC15⚡ Меч " + tag, 5, 3, 2));
            items.add(gear(neth ? Material.NETHERITE_PICKAXE : Material.DIAMOND_PICKAXE, "&#FACC15⛏ Кирка " + tag, 5, 3, 3));
            items.add(gear(neth ? Material.NETHERITE_AXE : Material.DIAMOND_AXE, "&#FACC15🪓 Топор " + tag, 5, 3, 0));
            items.add("vampire".equals(k) ? bowVampire() : gear(Material.BOW, "&#FACC15🏹 Лук " + tag, 5, 3, 1));
            items.add(gear(neth ? Material.NETHERITE_HELMET : Material.DIAMOND_HELMET, "&#FACC15Шлем " + tag, 4, 6, 0));
            items.add(gear(neth ? Material.NETHERITE_CHESTPLATE : Material.DIAMOND_CHESTPLATE, "&#FACC15Нагрудник " + tag, 4, 6, 0));
            items.add(gear(neth ? Material.NETHERITE_LEGGINGS : Material.DIAMOND_LEGGINGS, "&#FACC15Поножи " + tag, 4, 6, 0));
            items.add(gear(neth ? Material.NETHERITE_BOOTS : Material.DIAMOND_BOOTS, "&#FACC15Ботинки " + tag, 4, 6, 0));
            items.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, neth ? 3 : 1));
            items.add(new ItemStack(Material.GOLDEN_APPLE, 32));
            items.add(new ItemStack(Material.GOLDEN_CARROT, 64));
            items.add(new ItemStack(Material.ENDER_PEARL, 16));
            items.add(new ItemStack(Material.ENDER_PEARL, 16));
            items.add(new ItemStack(Material.CHORUS_FRUIT, 32));
            items.add(new ItemStack(Material.TOTEM_OF_UNDYING, 2));
            items.add("god".equals(k) ? sharChimery() : talDraculy());
            items.add(plast(6));
            items.add(trapka(4));
            items.add(dezorient(2));
            items.add(potionKiller(1));
            items.add(potionMedic(1));
            items.add(potionBurp(1));
            items.add(packedShulker(Material.YELLOW_SHULKER_BOX, "&#FACC15&l📦 Мега-Бокс Ресурсов " + tag,
                    Arrays.asList("&7Богатый запас ресурсов, кастомного ТНТ и расходников FT!"),
                    new ItemStack(Material.NETHERITE_INGOT, neth ? 6 : 2),
                    new ItemStack(Material.DIAMOND_BLOCK, 24),
                    new ItemStack(Material.EMERALD_BLOCK, 20),
                    new ItemStack(Material.GOLD_BLOCK, 32),
                    new ItemStack(Material.IRON_BLOCK, 32),
                    new ItemStack(Material.OBSIDIAN, 64),
                    tntTier1(12),
                    tntBlack(2),
                    new ItemStack(Material.END_CRYSTAL, 12),
                    new ItemStack(Material.COBWEB, 48),
                    new ItemStack(Material.GOLDEN_CARROT, 64),
                    yavnayaPyl(2),
                    snezokZamorozki(8),
                    xpOrb100(2),
                    arrowVampire(32),
                    claimBlock(Material.DIAMOND_ORE, 20, "&#38BDF8💎 Алмазный Приват 41×41")));
        } else if ("imperator".equals(k) || "king".equals(k) || "sponsor".equals(k) || "media".equals(k) || "mediaplus".equals(k) || "yt".equals(k) || "twitch".equals(k) || "tiktok".equals(k) || "kp".equals(k) || "mlmoder".equals(k)) {
            String tag = k.toUpperCase();
            items.add("sponsor".equals(k) ? swordPhoenix() : ("king".equals(k) ? swordKatana() : ("imperator".equals(k) ? swordFrost() : gear(Material.NETHERITE_SWORD, "&#A855F7👑 Меч " + tag, 5, 4, 2))));
            items.add(gear(Material.NETHERITE_PICKAXE, "&#A855F7⛏ Кирка " + tag, 5, 4, 3));
            items.add(gear(Material.NETHERITE_AXE, "&#A855F7🪓 Топор " + tag, 5, 4, 0));
            items.add("imperator".equals(k) ? bowInferno() : bowIceRw());
            int highUnb = ("sponsor".equals(k) || "king".equals(k)) ? 7 : 6;
            items.add(gear(Material.NETHERITE_HELMET, "&#A855F7Шлем " + tag, 5, highUnb, 0));
            items.add(gear(Material.NETHERITE_CHESTPLATE, "&#A855F7Нагрудник " + tag, 5, highUnb, 0));
            items.add(gear(Material.NETHERITE_LEGGINGS, "&#A855F7Поножи " + tag, 5, highUnb, 0));
            items.add(gear(Material.NETHERITE_BOOTS, "&#A855F7Ботинки " + tag, 5, highUnb, 0));
            items.add("sponsor".equals(k) ? elytraPhoenix() : elytraWind());
            items.add(new ItemStack(Material.FIREWORK_ROCKET, 64));
            items.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 6));
            items.add(new ItemStack(Material.GOLDEN_APPLE, 48));
            items.add(new ItemStack(Material.GOLDEN_CARROT, 64));
            items.add(new ItemStack(Material.ENDER_PEARL, 16));
            items.add(new ItemStack(Material.ENDER_PEARL, 16));
            items.add(new ItemStack(Material.CHORUS_FRUIT, 64));
            items.add("imperator".equals(k) ? talImperatora() : ("king".equals(k) ? sharAndromedy() : sharApollona()));
            items.add(talKrushitelya());
            items.add(plast(10));
            items.add(trapka(6));
            items.add(dezorient(4));
            items.add(potionKiller(2));
            items.add(potionMedic(2));
            items.add(potionBurp(1));
            items.add(packedShulker(Material.PURPLE_SHULKER_BOX, "&#A855F7&l📦 Королевский Сундук Ресурсов " + tag,
                    Arrays.asList("&7Полный шалкер ресурсов, 3D расходников FT и кастомного ТНТ!"),
                    new ItemStack(Material.NETHERITE_BLOCK, 2),
                    new ItemStack(Material.NETHERITE_INGOT, 12),
                    new ItemStack(Material.DIAMOND_BLOCK, 48),
                    new ItemStack(Material.EMERALD_BLOCK, 48),
                    new ItemStack(Material.GOLD_BLOCK, 64),
                    new ItemStack(Material.IRON_BLOCK, 64),
                    new ItemStack(Material.OBSIDIAN, 64),
                    tntBlack(6),
                    tntTier1(16),
                    tntCryo(8),
                    new ItemStack(Material.END_CRYSTAL, 24),
                    new ItemStack(Material.GOLDEN_CARROT, 64),
                    yavnayaPyl(4),
                    bozhyaAura(2),
                    ognennySmerch(3),
                    pautinnayaGranata(4),
                    xpOrb100(4),
                    nyMandarin(16),
                    arrowLightning(32),
                    claimBlock(Material.DIAMOND_ORE, 20, "&#38BDF8💎 Алмазный Приват 41×41")));
        } else {
            // DHELPER, DKP, DMODER..DADMIN, OPKA, DOWNER, CURATOR, TECHADMIN — ТОПОВЫЕ ДОНАТ/КП КИТЫ!
            boolean supreme = "opka".equals(k) || "downer".equals(k) || "techadmin".equals(k) || "curator".equals(k);
            String tag = k.toUpperCase();
            items.add(supreme ? swordKrush() : swordDracula());
            items.add(supreme ? pickKrush() : pickDjamshut());
            items.add(supreme ? bowZeus() : bowIceRw());
            items.add(supreme ? axeThor() : gear(Material.NETHERITE_AXE, "&#FFD740👑 Топор " + tag, 6, 5, 0));
            String setKey = supreme ? "krush" : "frost";
            items.add(customArmorPiece(setKey, "helmet"));
            items.add(customArmorPiece(setKey, "chestplate"));
            items.add(customArmorPiece(setKey, "leggings"));
            items.add(customArmorPiece(setKey, "boots"));
            items.add(supreme ? elytraSanta() : elytraTitan());
            items.add(new ItemStack(Material.FIREWORK_ROCKET, 64));
            items.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, supreme ? 16 : 8));
            items.add(new ItemStack(Material.GOLDEN_APPLE, 64));
            items.add(new ItemStack(Material.GOLDEN_CARROT, 64));
            items.add(new ItemStack(Material.ENDER_PEARL, 16));
            items.add(new ItemStack(Material.ENDER_PEARL, 16));
            items.add(supreme ? sharSanty() : sharTitana());
            items.add(supreme ? talKaratelya() : talFeniksa(3));
            // v6.4.0: КИТ TECH.ADMIN и КИТ HELPER получили новые шары, тотемы, МЕГА-расходники и свитки!
            items.add(supreme ? sharKvadrat() : sharMorozko());
            items.add(supreme ? sharKronosa() : sharGeliosa());
            items.add(supreme ? totemVoskresheniya(3) : totemLedyanoy(2));
            items.add(megaHeal(supreme ? 16 : 8));
            items.add(megaBuff(supreme ? 8 : 4));
            items.add(megaShield(supreme ? 8 : 4));
            items.add(enchantScroll("ice"));
            items.add(enchantScroll(supreme ? "blast" : "vampire"));
            items.add(enchantScroll("smelt"));
            items.add(plast(16));
            items.add(trapka(10));
            items.add(dezorient(6));
            items.add(bozhyaAura(4));
            items.add(potionKiller(2));
            items.add(potionMedic(2));
            items.add(potionAgent(1));
            items.add(potionBurp(1));
            items.add(potionAcid(1));
            items.add(packedShulker(Material.RED_SHULKER_BOX, "&#FF1744&l📦 Шалкер Богатств " + tag + " #1",
                    Arrays.asList("&7Элитные ресурсы, 3D Элитры, Талисманы и Стрелы!"),
                    new ItemStack(Material.NETHERITE_BLOCK, supreme ? 8 : 4),
                    new ItemStack(Material.NETHERITE_INGOT, supreme ? 32 : 16),
                    new ItemStack(Material.DIAMOND_BLOCK, 64),
                    new ItemStack(Material.EMERALD_BLOCK, 64),
                    new ItemStack(Material.GOLD_BLOCK, 64),
                    new ItemStack(Material.IRON_BLOCK, 64),
                    new ItemStack(Material.GOLDEN_CARROT, 64),
                    elytraThunder(),
                    talZeusa(),
                    talKolossa(),
                    bozhyaKara(4),
                    gravSfera(4),
                    snegZavesa(4),
                    yavnayaPyl(8),
                    ognennySmerch(6),
                    snezokZamorozki(16),
                    pautinnayaGranata(8),
                    pickSpawner(),
                    mysteryEgg(4),
                    xpOrb100(8),
                    nyMandarin(32),
                    snowflakeCoin(supreme ? 25 : 10),
                    arrowExplosive(48),
                    arrowAntiFly(32),
                    claimBlock(Material.EMERALD_ORE, 30, "&#00E676💚 Изумрудный Приват 61×61")));
            items.add(packedShulker(Material.BLACK_SHULKER_BOX, "&#FFD740&l💣 Рейд-Шалкер ТНТ " + tag + " #2",
                    Arrays.asList("&7Полный набор кастомного ТНТ для грифа и рейда баз!"),
                    tntBlack(supreme ? 16 : 8),
                    tntNuke(supreme ? 8 : 4),
                    tntTier1(24),
                    tntFlash(16),
                    tntCryo(16),
                    tntMiner(12),
                    new ItemStack(Material.OBSIDIAN, 64),
                    new ItemStack(Material.OBSIDIAN, 64),
                    new ItemStack(Material.END_CRYSTAL, 64),
                    new ItemStack(Material.RESPAWN_ANCHOR, 16),
                    new ItemStack(Material.GLOWSTONE, 64),
                    new ItemStack(Material.COBWEB, 64)));
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
