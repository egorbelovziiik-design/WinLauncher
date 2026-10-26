package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public final class GuiManager {

    public static final class KgMenuHolder implements InventoryHolder {
        public final String menuType;
        public final String extra;
        public final int page;

        public KgMenuHolder(String menuType, String extra, int page) {
            this.menuType = menuType;
            this.extra = extra != null ? extra : "";
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static final String[][] ALL_KITS = new String[][]{
            {"start",     "Кит Игрока",      "IRON_SWORD",           "900"},
            {"newyear",   "Новогодний Кит",  "SNOWBALL",             "3600"},
            {"pegas",     "Кит PEGAS",       "FEATHER",              "21600"},
            {"cobra",     "Кит COBRA",       "SLIME_BALL",           "21600"},
            {"hero",      "Кит HERO",        "SHIELD",               "21600"},
            {"bunny",     "Кит BUNNY",       "RABBIT_FOOT",          "21600"},
            {"titan",     "Кит TITAN",       "IRON_CHESTPLATE",      "21600"},
            {"rabbit",    "Кит RABBIT",      "GOLDEN_CARROT",        "21600"},
            {"vampire",   "Кит VAMPIRE",     "REDSTONE",             "21600"},
            {"tiger",     "Кит TIGER",       "BLAZE_POWDER",         "21600"},
            {"dracula",   "Кит DRACULA",     "NETHERITE_SWORD",      "21600"},
            {"god",       "Кит GOD",         "ENCHANTED_GOLDEN_APPLE","21600"},
            {"overlord",  "Кит OVERLORD",    "PRISMARINE_SHARD",     "21600"},
            {"imperator", "Кит IMPERATOR",   "GOLDEN_HELMET",        "21600"},
            {"dragon",    "Кит DRAGON",      "DRAGON_BREATH",        "21600"},
            {"king",      "Кит KING",        "NETHER_STAR",          "21600"},
            {"bull",      "Кит BULL",        "ANVIL",                "21600"},
            {"sponsor",   "Кит SPONSOR",     "BEACON",               "21600"},
            {"media",     "Кит MEDIA",       "AMETHYST_SHARD",       "21600"},
            {"kp",        "Кит Персонала КП","EMERALD",              "21600"},
            {"dhelper",   "Кит D.HELPER",    "CYAN_SHULKER_BOX",     "21600"},
            {"dmoder",    "Кит D.MODER",     "LIGHT_BLUE_SHULKER_BOX","21600"},
            {"dstmoder",  "Кит D.ST.MODER",  "BLUE_SHULKER_BOX",     "21600"},
            {"dglmoder",  "Кит D.GLMODER",   "PURPLE_SHULKER_BOX",   "21600"},
            {"dmladmin",  "Кит D.ML.ADMIN",  "ORANGE_SHULKER_BOX",   "21600"},
            {"dadmin",    "Кит D.ADMIN",     "RED_SHULKER_BOX",      "21600"},
            {"dkp",       "Кит ДКП",         "YELLOW_SHULKER_BOX",   "18000"},
            {"opka",      "Кит OPKA",        "MAGENTA_SHULKER_BOX",  "14400"},
            {"downer",    "Кит D.OWNER",     "BLACK_SHULKER_BOX",    "10800"},
            {"curator",   "Кит CURATOR",     "LIME_SHULKER_BOX",     "10800"},
            {"owner",     "Кит OWNER",       "COMMAND_BLOCK",        "3600"}
    };

    private static final String[][] TITLES_CATALOG = new String[][]{
            {"winter",      "&#80D8FF[❄ Зимний]",       "0",   "Бесплатный новогодний титул!"},
            {"rilik",       "&#00E5FF[❖ РИЛИК]",        "75",  "Классический титул ReallyWorld"},
            {"krush",       "&#FF6D00[⚔ КРУШИТЕЛЬ]",    "140", "Гроза всех арен и дуэлей"},
            {"vampire_t",   "&#FF1744[🩸 ВАМПИР]",      "120", "Кровавый охотник выживания"},
            {"magnat",      "&#FFD740[💎 МАГНАТ]",      "180", "Король экономики и аукциона"},
            {"raider",      "&#DC2626[💣 РЕЙДЕР]",      "150", "Разрушитель любых баз"},
            {"santa_t",     "&#FF4081[🎅 ДЕД МОРОЗ]",   "160", "Праздничный титул 2026"},
            {"immortal",    "&#E040FB[🌟 БЕССМЕРТНЫЙ]", "220", "Элитный титул сервера"},
            {"parkour_pro", "&#40C4FF[🏃 ПАРКУРИСТ]",   "-1",  "Выдаётся за прохождение /warp parkour!"},
            {"boss_slayer", "&#00E676[🔥 ГРОЗА БОССОВ]","-1",  "Выдаётся за Топ-1 урон по Боссу!"},
            {"ludoman",     "&#FFEA00[🎰 ЛУДОМАН]",     "110", "Легенда Гранд-Казино 777"},
            {"rw_legend",   "&#FACC15[👑 ЛЕГЕНДА РВ]",  "300", "Максимальный статус ReallyWorld"}
    };

    private static final String[] COMMON_GROUP_PERMS = new String[]{
            "kinggrief.fly", "kinggrief.heal", "kinggrief.feed", "kinggrief.repair",
            "kinggrief.enderchest", "kinggrief.workbench", "kinggrief.staff",
            "kinggrief.vanish", "kinggrief.gm", "kinggrief.lp", "kinggrief.spawn.edit", "*"
    };

    private final KingGriefCore plugin;
    private final Random rng = new Random();
    private double casinoJackpotPool = 150000.0;

    public GuiManager(KingGriefCore plugin) {
        this.plugin = plugin;
    }

    private void decorateFrame(Inventory inv) {
        ItemStack blue = ItemFactory.icon(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ");
        ItemStack white = ItemFactory.icon(Material.WHITE_STAINED_GLASS_PANE, " ");
        int size = inv.getSize();
        for (int i = 0; i < 9 && i < size; i++) inv.setItem(i, (i % 2 == 0) ? blue : white);
        for (int i = size - 9; i < size; i++) if (i >= 0) inv.setItem(i, (i % 2 == 0) ? white : blue);
        for (int r = 1; r < size / 9 - 1; r++) {
            inv.setItem(r * 9, white);
            inv.setItem(r * 9 + 8, white);
        }
    }

    // ================= 1. ГЛАВНОЕ МЕНЮ (/menu) =================
    public void openMainMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("main", "", 0), 54,
                ColorUtil.c("&#FF1744&l❄ МЕНЮ REALLYWORLD v4.0"));
        decorateFrame(inv);
        DataStore st = plugin.getStore();
        RankManager.RankDef r = plugin.getRanks().getRank(p);

        inv.setItem(4, ItemFactory.glowIcon(Material.PLAYER_HEAD, "&#FFD740&l👤 Профиль: &f" + p.getName(),
                "&7Привилегия: " + r.shortLabel,
                "&7Монеты: &a" + ColorUtil.fmtMoney(st.getCoins(p.getName())) + "$",
                "&7Рилики: &b" + st.getRelics(p) + " ❖ &8| &7Снежинки: &b" + st.getSnowflakes(p.getName()) + " ❄",
                "&7Наиграно: &e" + ColorUtil.fmtTime(st.getPlaySeconds(p.getName()))));

        inv.setItem(10, ItemFactory.glowIcon(Material.COMPASS, "&#00E676&l⚡ РТП в Мир Выживания",
                "&7Телепорт из мира спавна в отдельный", "&7мир выживания, Ад или дальний РТП!", "", "&#00E676▸ Нажми для выбора!"));
        inv.setItem(11, ItemFactory.glowIcon(Material.ENDER_PEARL, "&#80D8FF&l🧭 Все Варпы и Арены",
                "&7Авто-Шахта, Фармилка, Казино, Паркур,", "&7PvP-Колизей, 6 Кабинок, Кейсы, Чары!", "", "&#80D8FF▸ Открыть /warps"));
        inv.setItem(12, ItemFactory.glowIcon(Material.TOTEM_OF_UNDYING, "&#00E5FF&l🔮 Магазин Риликов РВ",
                "&77 Шаров РВ, 7 Кастомных Тотемов,", "&7Зелья Киллера/Отрыжки/Кислоты, Буры!", "", "&#00E5FF▸ Открыть /shop"));
        inv.setItem(13, ItemFactory.glowIcon(Material.ENDER_CHEST, "&#38BDF8&l📦 Киты Привилегий",
                "&7Богатые наборы с шалкерами ресурсов!", "&cДоступ строго по вашей привилегии.", "", "&#38BDF8▸ Открыть /kits"));
        inv.setItem(14, ItemFactory.glowIcon(Material.RED_SHULKER_BOX, "&#FF4081&l🎁 Шалкер-Кейсы (11 шт.)",
                "&7Открывай кейсы на спавне или в меню!", "", "&#FF4081▸ Открыть /cases"));
        inv.setItem(15, ItemFactory.glowIcon(Material.NETHER_STAR, "&#FFD740&l👑 Донат и Привилегии",
                "&740 привилегий от 19₽ на сайте:", "&fkinggrief.vercel.app", "", "&#FFD740▸ Открыть /donate"));
        inv.setItem(16, ItemFactory.glowIcon(Material.DIAMOND, "&#FFEA00&l🎰 Гранд-Казино 777",
                "&7Слоты 777, Рулетка ×14, PvP-Монетка!", "&7Джекпот банка: &a" + ColorUtil.fmtMoney(casinoJackpotPool) + "$", "", "&#FFEA00▸ Открыть /casino"));

        inv.setItem(19, ItemFactory.icon(Material.EMERALD, "&#69F0AE&l💰 Скупщик Ресурсов",
                "&7Продай руду, урожай и дроп мобов", "&7в 1 клик с бонусом привилегии!", "", "&#69F0AE▸ Открыть /buyer"));
        inv.setItem(20, ItemFactory.icon(Material.GOLD_INGOT, "&#FFAB40&l⚖ Аукцион Игроков",
                "&7Покупай и продавай редкие вещи РВ!", "&7Продать предмет: &f/ah sell <цена>", "", "&#FFAB40▸ Открыть /ah"));
        inv.setItem(21, ItemFactory.glowIcon(Material.BLAZE_POWDER, "&#FF1744&l🔥 Боссы и Ивенты",
                "&7Активные Боссы (Ледяной Гринч, Крушитель,", "&7Титан Олимпа, Осирис, Андромеда)!", "", "&#FF1744▸ Открыть /events"));
        inv.setItem(22, ItemFactory.glowIcon(Material.WITHER_SKELETON_SKULL, "&#D500F9&l🕵 Чёрный Рынок РВ",
                "&7Редкие товары за Монеты и Снежинки!", "", "&#D500F9▸ Открыть /bm"));
        inv.setItem(23, ItemFactory.icon(Material.BREWING_STAND, "&#E040FB&l🧪 Алхимик и Кузнец",
                "&7Баффы за монеты и починка всей брони!", "", "&#E040FB▸ Открыть /enchants"));
        inv.setItem(24, ItemFactory.icon(Material.PRISMARINE_CRYSTALS, "&#80D8FF&l👳 Обменник Джамшута",
                "&7Обменяй Снежинки ❄ на Шары и Рилики!", "", "&#80D8FF▸ Открыть /quests"));
        inv.setItem(25, ItemFactory.icon(Material.SHIELD, "&#B388FF&l🛡 Кланы Сервера",
                "&7Рейтинг кланов, казна и клан-база!", "", "&#B388FF▸ Открыть /clan"));

        inv.setItem(29, ItemFactory.icon(Material.IRON_PICKAXE, "&#AEEA00&l💼 Работы (/jobs)", "&7Шахтёр, Охотник, Дровосек, Воин!"));
        inv.setItem(30, ItemFactory.icon(Material.EXPERIENCE_BOTTLE, "&#40C4FF&l🎄 Боевой Пропуск (/bp)", "&7Награды за активность на сервере!"));
        inv.setItem(31, ItemFactory.icon(Material.SNOW_BLOCK, "&#80D8FF&l🏰 Телепорт на Спавн", "&7Вернуться в Зимний Замок спавна!"));
        inv.setItem(32, ItemFactory.icon(Material.NAME_TAG, "&#FFD740&l✨ Титулы (/titles)", "&712 красивых титулов в чат и ТАБ!"));
        inv.setItem(33, ItemFactory.icon(Material.CLOCK, "&#00E676&l⏰ Награды за Время (/pt)", "&7Получай Рилики и Кейсы за онлайн!"));
        inv.setItem(38, ItemFactory.glowIcon(Material.POTION, "&#FF1744&l🧪 Лавка Бафовара (/bafovar)", "&7Все Зелья РВ, Пакеты Баффов и Стакер Зелий!"));
        inv.setItem(42, ItemFactory.glowIcon(Material.CHEST_MINECART, "&#FFD740&l🎁 Халява и Промокоды (/free)", "&7Активация 6 промокодов и подарков в 1 клик!"));

        if (plugin.getLp().hasLpAccess(p)) {
            inv.setItem(49, ItemFactory.glowIcon(Material.COMMAND_BLOCK, "&#00E676&l⚡ LP EDITOR & ГРУППЫ (/lp)",
                    "&7Управление 40 группами, правами и Веб-Редактор!", "", "&#00E676▸ Нажми для открытия LP GUI"));
        }
        p.openInventory(inv);
    }

    // ================= 2. ОТДЕЛЬНЫЙ МИР РТП (/rtp gui) =================
    public void openRtpMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("rtp", "", 0), 27,
                ColorUtil.c("&#00E676&l⚡ РТП В МИР ВЫЖИВАНИЯ"));
        decorateFrame(inv);
        inv.setItem(11, ItemFactory.glowIcon(Material.GRASS_BLOCK, "&#00E676&l🌲 Обычный РТП (Выживание)",
                "&7Отдельный мир выживания (&fworld&7)", "&7Дистанция: &eдо 1 500 блоков", "", "&#00E676▸ Нажми для телепорта!"));
        inv.setItem(13, ItemFactory.glowIcon(Material.DIAMOND_BOOTS, "&#38BDF8&l🏔 Дальний РТП (Для баз)",
                "&7Отдельный мир выживания (&fworld&7)", "&7Дистанция: &eдо 3 500 блоков", "", "&#38BDF8▸ Нажми для телепорта!"));
        inv.setItem(15, ItemFactory.glowIcon(Material.NETHERRACK, "&#FF5252&l🔥 Адский РТП (Нижний мир)",
                "&7Безопасная точка в Аду (&fworld_nether&7)", "&7Дистанция: &eдо 800 блоков", "", "&#FF5252▸ Нажми для телепорта!"));
        p.openInventory(inv);
    }

    // ================= 3. ВАРПЫ СЕРВЕРА (/warps) =================
    public void openWarpsMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("warps", "", 0), 45,
                ColorUtil.c("&#80D8FF&l🧭 ВАРПЫ И АРЕНЫ СЕРВЕРА"));
        decorateFrame(inv);
        inv.setItem(10, ItemFactory.glowIcon(Material.DIAMOND_PICKAXE, "&#FF9100&l⛏ /warp mine — Авто-Шахта",
                "&7Огромный карьер с алмазами, изумрудами", "&7и древними обломками! Авто-реген 90с.", "&a• Разрешено копать всем игрокам!"));
        inv.setItem(11, ItemFactory.glowIcon(Material.ZOMBIE_HEAD, "&#AEEA00&l🧟 /warp farm — Фармилка Мобов",
                "&73 безопасные камеры с зимними мобами!", "&7Фарми Монеты, Опыт и Снежинки ❄!"));
        inv.setItem(12, ItemFactory.glowIcon(Material.GOLD_BLOCK, "&#FFD740&l🎰 /warp casino — Гранд-Казино",
                "&7Зал игровых автоматов 777, Рулетка ×14,", "&7Монетка между игроками и Колесо Риликов!"));
        inv.setItem(13, ItemFactory.glowIcon(Material.NETHERITE_SWORD, "&#FF1744&l⚔ /warp pvp — PvP-Колизей",
                "&7Гладиаторская яма для массовых замесов!"));
        inv.setItem(14, ItemFactory.glowIcon(Material.RED_STAINED_GLASS, "&#FF5252&l🥊 Кабинки 1×1 и 2×2",
                "&75 кабинок 1×1 + 1 кабинка 2×2!", "&7Авто-закрытие + 8 сек анти-подсос лута!"));
        inv.setItem(15, ItemFactory.glowIcon(Material.FEATHER, "&#40C4FF&l🏃 /warp parkour — Небесный Паркур",
                "&730 парящих островов до высоты Y=131,", "&73 чекпоинта и приз +25 000$ + 15❖ + Титул!"));
        inv.setItem(16, ItemFactory.glowIcon(Material.RED_SHULKER_BOX, "&#FF4081&l🎁 /warp cases — Шалкер-Кейсы",
                "&7Амфитеатр из 11 цветных Шалкер-Кейсов!"));
        inv.setItem(22, ItemFactory.glowIcon(Material.ENCHANTING_TABLE, "&#E040FB&l🧪 /warp enchant — Чары и Алхимик",
                "&7Купол чародея на 30 уровень и алхимик РВ!"));
        inv.setItem(31, ItemFactory.icon(Material.SNOW_BLOCK, "&#80D8FF&l🏰 Центральный Спавн (/spawn)",
                "&7Главная площадь у 64-блоковой Ёлки"));
        inv.setItem(40, ItemFactory.icon(Material.ARROW, "&7« Назад в меню"));
        p.openInventory(inv);
    }

    // ================= 4. СТРОГИЕ КИТЫ ПО ПРИВИЛЕГИЯМ (/kits) + ПРЕДПРОСМОТР =================
    public void openKitsMenu(Player p, int page) {
        int pg = Math.max(0, Math.min(1, page));
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("kits", "", pg), 54,
                ColorUtil.c("&#38BDF8&l📦 КИТЫ ПРИВИЛЕГИЙ (Стр. " + (pg + 1) + "/2)"));
        decorateFrame(inv);
        RankManager.RankDef myRank = plugin.getRanks().getRank(p);
        inv.setItem(4, ItemFactory.glowIcon(Material.NETHER_STAR, "&#FFD740&lВаша привилегия: " + myRank.shortLabel,
                "&7Киты выдаются &cСТРОГО по привилегии&7!",
                "&a• ЛКМ &7— Получить набор в инвентарь",
                "&b• ПКМ &7— Посмотреть ВСЕ ресурсы внутри кита!"));

        int[] slots = new int[]{
                10,11,12,13,14,15,16,
                19,20,21,22,23,24,25,
                28,29,30,31,32,33,34
        };
        int start = pg * slots.length;
        long now = System.currentTimeMillis();
        for (int i = 0; i < slots.length && (start + i) < ALL_KITS.length; i++) {
            String[] kd = ALL_KITS[start + i];
            String kid = kd[0];
            String kTitle = kd[1];
            Material mat = ColorUtil.mat(kd[2], "CHEST");
            long cdSec = Long.parseLong(kd[3]);
            boolean allowed = plugin.getRanks().canClaimKit(p, kid);
            long readyAt = plugin.getStore().getKitReadyAt(p.getName(), kid);
            RankManager.RankDef reqDef = plugin.getRanks().byId(kid);

            List<String> lore = new ArrayList<String>();
            lore.add("&8Богатый набор ресурсов ReallyWorld");
            lore.add("&7Кулдаун: &f" + ColorUtil.fmtTime(cdSec));
            lore.add("");
            if (allowed) {
                if (now >= readyAt) {
                    lore.add("&#00E676✔ ДОСТУПНО ВАШЕЙ ПРИВИЛЕГИИ!");
                    lore.add("&#00E676▸ ЛКМ — Забрать кит сейчас");
                } else {
                    lore.add("&#FFD740⏳ Перезарядка: " + ColorUtil.fmtTime((readyAt - now) / 1000L));
                }
            } else {
                String priceInfo = reqDef.priceRub > 0 ? " &7(от &#FFD740" + reqDef.priceRub + "₽&7)" : "";
                lore.add("&#FF1744✖ НЕДОСТУПНО! Нужна привилегия: &f" + kid.toUpperCase(Locale.ROOT) + priceInfo);
                lore.add("&7Покупка: &#FF4081kinggrief.vercel.app");
            }
            lore.add("&#80D8FF▸ ПКМ — Посмотреть содержимое и шалкеры!");

            inv.setItem(slots[i], ItemFactory.make(mat, 1,
                    (allowed ? "&#00E676&l" : "&#FF5252&l") + "📦 " + kTitle, lore, allowed, "kit_" + kid, null, 0));
        }
        if (pg > 0) inv.setItem(45, ItemFactory.icon(Material.ARROW, "&#FFD740« Страница 1"));
        inv.setItem(49, ItemFactory.icon(Material.BARRIER, "&7« Назад в меню"));
        if (pg == 0) inv.setItem(53, ItemFactory.glowIcon(Material.ARROW, "&#FFD740Страница 2 (ДКП / OPKA / OWNER) »"));
        p.openInventory(inv);
    }

    public void openKitPreview(Player p, String kitId, int backPage) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("kit_preview", kitId, backPage), 54,
                ColorUtil.c("&#38BDF8&l👁 СОДЕРЖИМОЕ КИТА: " + kitId.toUpperCase(Locale.ROOT)));
        List<ItemStack> items = ItemFactory.buildKitItems(kitId);
        for (int i = 0; i < Math.min(45, items.size()); i++) {
            inv.setItem(i, items.get(i));
        }
        for (int s = 45; s < 54; s++) inv.setItem(s, ItemFactory.icon(Material.BLACK_STAINED_GLASS_PANE, " "));
        inv.setItem(49, ItemFactory.glowIcon(Material.ARROW, "&#FFD740« Назад к списку китов"));
        p.openInventory(inv);
    }

    public void tryClaimKit(Player p, String kitId) {
        String k = kitId.toLowerCase(Locale.ROOT);
        if (!plugin.getRanks().canClaimKit(p, k)) {
            RankManager.RankDef req = plugin.getRanks().byId(k);
            ColorUtil.sound(p, "entity.villager.no", 1.0f, 1.0f);
            p.sendMessage(ColorUtil.c("&#FF1744✖ Этот кит доступен ТОЛЬКО с привилегией " + req.shortLabel + "&#FF1744!"));
            p.sendMessage(ColorUtil.c("&7Приобрести привилегию: &#FF4081https://kinggrief.vercel.app"));
            return;
        }
        long cdSec = 21600L;
        for (String[] row : ALL_KITS) {
            if (row[0].equals(k)) { cdSec = Long.parseLong(row[3]); break; }
        }
        long now = System.currentTimeMillis();
        long ready = plugin.getStore().getKitReadyAt(p.getName(), k);
        if (now < ready && !"owner".equals(plugin.getRanks().getRank(p).id)) {
            p.sendMessage(ColorUtil.c("&#FFD740⏳ Подождите ещё &f" + ColorUtil.fmtTime((ready - now) / 1000L) + " &#FFD740до следующего получения кита!"));
            return;
        }
        plugin.getStore().setKitReadyAt(p.getName(), k, now + cdSec * 1000L);
        plugin.getStore().save();
        ItemFactory.giveKitItems(p, k);
        ColorUtil.sound(p, "entity.player.levelup", 1.0f, 1.2f);
        p.sendMessage(ColorUtil.c("&#00E676✔ Вы получили богатый набор &f/kit " + k + " &#00E676(включая шалкеры ресурсов)!"));
    }

    // ================= 5. ЧИСТЫЙ МАГАЗИН РИЛИКОВ REALLYWORLD (/shop) =================
    public void openRelicShop(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("shop", "", 0), 54,
                ColorUtil.c("&#00E5FF&l🔮 МАГАЗИН РИЛИКОВ REALLYWORLD"));
        decorateFrame(inv);
        int bal = plugin.getStore().getRelics(p);
        inv.setItem(4, ItemFactory.glowIcon(Material.DIAMOND, "&#00E5FF&lВаш баланс: &f" + bal + " Риликов ❖",
                "&7100% оригинальные предметы ReallyWorld!", "&7Пополнить Рилики: &#FF4081kinggrief.vercel.app"));

        // Row 1: 7 Шаров ReallyWorld
        inv.setItem(10, shopTag(ItemFactory.sharTitana(),    290));
        inv.setItem(11, shopTag(ItemFactory.sharAndromedy(), 260));
        inv.setItem(12, shopTag(ItemFactory.sharChimery(),   220));
        inv.setItem(13, shopTag(ItemFactory.sharApollona(),  270));
        inv.setItem(14, shopTag(ItemFactory.sharAida(),      280));
        inv.setItem(15, shopTag(ItemFactory.sharOsirisa(),   240));
        inv.setItem(16, shopTag(ItemFactory.sharSanty(),     350));

        // Row 2: 7 Кастомных Тотемов Бессмертия РВ
        inv.setItem(19, shopTag(ItemFactory.talKrushitelya(), 310));
        inv.setItem(20, shopTag(ItemFactory.talGrani(),       290));
        inv.setItem(21, shopTag(ItemFactory.talDedala(),      250));
        inv.setItem(22, shopTag(ItemFactory.talTritona(),     230));
        inv.setItem(23, shopTag(ItemFactory.talFeniksa(3),    390));
        inv.setItem(24, shopTag(ItemFactory.talEchidny(),     280));
        inv.setItem(25, shopTag(ItemFactory.talStuzhi(),      300));

        // Row 3: Зелья РВ и Оружие/Инструменты РВ
        inv.setItem(28, shopTag(ItemFactory.potionKiller(2),    45));
        inv.setItem(29, shopTag(ItemFactory.potionBurp(2),      40));
        inv.setItem(30, shopTag(ItemFactory.potionAcid(2),      45));
        inv.setItem(31, shopTag(ItemFactory.pickDjamshut(),    340));
        inv.setItem(32, shopTag(ItemFactory.pickSpawner(),     190));
        inv.setItem(33, shopTag(ItemFactory.swordDracula(),    320));
        inv.setItem(34, shopTag(ItemFactory.swordKrush(),      420));

        // Row 4: Доп. товары и приваты РВ
        inv.setItem(38, shopTag(ItemFactory.bowIceRw(),        210));
        inv.setItem(39, shopTag(ItemFactory.mysteryEgg(2),     110));
        inv.setItem(40, shopTag(ItemFactory.potionMedic(2),     35));
        inv.setItem(41, shopTag(ItemFactory.potionAgent(2),     35));
        inv.setItem(42, shopTag(ItemFactory.claimBlock(Material.EMERALD_ORE, 30, "&#00E676💚 Изумрудный Приват 61×61"), 150));

        inv.setItem(49, ItemFactory.icon(Material.ARROW, "&7« Назад в меню"));
        p.openInventory(inv);
    }

    private ItemStack shopTag(ItemStack base, int relicCost) {
        ItemStack copy = base.clone();
        ItemMeta m = copy.getItemMeta();
        if (m != null) {
            List<String> lore = m.hasLore() ? new ArrayList<String>(m.getLore()) : new ArrayList<String>();
            lore.add("");
            lore.add(ColorUtil.c("&#00E5FFЦена: &f" + relicCost + " Риликов ❖"));
            lore.add(ColorUtil.c("&#00E676▸ Нажмите ЛКМ для покупки!"));
            m.setLore(lore);
            copy.setItemMeta(m);
        }
        return copy;
    }

    // ================= 6. АЛТАРЬ АЛХИМИКА И КУЗНЕЦА РВ (/enchants) =================
    public void openEnchantsMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("enchants", "", 0), 45,
                ColorUtil.c("&#E040FB&l🧪 АЛХИМИК И КУЗНЕЦ РВ"));
        decorateFrame(inv);
        inv.setItem(10, coinShopTag(ItemFactory.potionKiller(1),    15000));
        inv.setItem(11, coinShopTag(ItemFactory.potionMedic(1),     12000));
        inv.setItem(12, coinShopTag(ItemFactory.potionFlash(1),     10000));
        inv.setItem(13, coinShopTag(ItemFactory.potionAgent(1),     14000));
        inv.setItem(14, coinShopTag(ItemFactory.potionChampagne(1), 16000));
        inv.setItem(15, coinShopTag(ItemFactory.potionBurp(1),      18000));
        inv.setItem(16, coinShopTag(ItemFactory.potionAcid(1),      20000));

        inv.setItem(20, coinShopTag(ItemFactory.arrowIce(16),       9000));
        inv.setItem(21, coinShopTag(ItemFactory.xpOrb100(1),        12000));
        inv.setItem(22, ItemFactory.glowIcon(Material.ANVIL, "&#00E676&l🔧 Починить ВСЮ Броню и Оружие",
                "&7Мгновенно чинит все вещи в инвентаре!", "&7Стоимость: &a10 000$ Монет", "", "&#00E676▸ Нажми для починки!"));
        inv.setItem(23, coinShopTag(ItemFactory.claimBlock(Material.IRON_ORE, 12, "&#38BDF8🛡 Железный Приват 25×25"), 15000));
        inv.setItem(24, coinShopTag(ItemFactory.claimBlock(Material.DIAMOND_ORE, 20, "&#38BDF8💎 Алмазный Приват 41×41"), 45000));
        inv.setItem(40, ItemFactory.icon(Material.ARROW, "&7« Назад в меню"));
        p.openInventory(inv);
    }

    // ================= 6B. NPC БАФОВАР REALLYWORLD (/bafovar, /bafs) =================
    public void openBafovarMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("bafovar", "", 0), 54,
                ColorUtil.c("&#FF1744&l🧪 ЛАВКА БАФОВАРА REALLYWORLD"));
        decorateFrame(inv);
        inv.setItem(4, ItemFactory.glowIcon(Material.BREWING_STAND, "&#FF1744&l🧪 БАФОВАР РВ — Зелья и Баффы",
                "&7Ваш баланс: &a" + ColorUtil.fmtMoney(plugin.getStore().getCoins(p.getName())) + "$",
                "&7Покупай топовые зелья РВ, накладывай", "&7мгновенные пакеты баффов и стакай зелья!"));

        // Row 1: Все 9 Кастомных Зелий ReallyWorld
        inv.setItem(10, coinShopTag(ItemFactory.potionKiller(1),    14000));
        inv.setItem(11, coinShopTag(ItemFactory.potionMedic(1),     11000));
        inv.setItem(12, coinShopTag(ItemFactory.potionFlash(1),      9500));
        inv.setItem(13, coinShopTag(ItemFactory.potionAgent(1),     13000));
        inv.setItem(14, coinShopTag(ItemFactory.potionChampagne(1), 15000));
        inv.setItem(15, coinShopTag(ItemFactory.potionBurp(1),      17000));
        inv.setItem(16, coinShopTag(ItemFactory.potionAcid(1),      19000));
        inv.setItem(19, coinShopTag(ItemFactory.potionTurtle(1),    16000));
        inv.setItem(20, coinShopTag(ItemFactory.potionVampire(1),   18000));

        // Row 2-3: Мгновенные пакеты баффов прямо на игрока + Стакер зелий РВ + Снятие дебаффов!
        inv.setItem(22, ItemFactory.glowIcon(Material.BLAZE_POWDER, "&#FF6D00&l⚔ Пакет «Боевой Баф» (5 мин)",
                "&7Мгновенно накладывает на вас:", "&c• Сила II  &b• Скорость II  &6• Огнестойкость  &9• Ночное Зрение",
                "", "&#FFEA00Цена: &a12 000$", "&#00E676▸ Нажми для активации!"));
        inv.setItem(23, ItemFactory.glowIcon(Material.NETHER_STAR, "&#FFD740&l👑 Пакет «Королевский Баф» (8 мин)",
                "&7Мгновенно накладывает на вас:", "&c• Сила III  &b• Скорость III  &a• Сопротивление I",
                "&6• Огнестойкость  &e• +6 Золотых Сердец",
                "", "&#FFEA00Цена: &a30 000$", "&#00E676▸ Нажми для активации!"));
        inv.setItem(24, ItemFactory.glowIcon(Material.GOLDEN_PICKAXE, "&#00E5FF&l⛏ Пакет «Шахтёрский Баф» (10 мин)",
                "&7Мгновенно накладывает на вас:", "&e• Спешка III  &b• Скорость III  &9• Ночное Зрение",
                "", "&#FFEA00Цена: &a8 000$", "&#00E676▸ Нажми для активации!"));

        inv.setItem(30, ItemFactory.glowIcon(Material.CAULDRON, "&#00E676&l🧪 СТАКНУТЬ ЗЕЛЬЯ В ИНВЕНТАРЕ (до x8!)",
                "&7Фирменная функция Бафовара ReallyWorld!",
                "&7Объединяет одинаковые зелья в вашем", "&7инвентаре в удобные стаки по &e8 штук&7!",
                "", "&#FFEA00Цена: &a5 000$", "&#00E676▸ Нажми, чтобы стакнуть все зелья!"));
        inv.setItem(32, ItemFactory.glowIcon(Material.MILK_BUCKET, "&f&l🥛 Очистить Негативные Дебаффы",
                "&7Мгновенно снимает Отравление, Иссушение,", "&7Слепоту, Слабость и Замедление!",
                "", "&#FFEA00Цена: &a2 500$", "&#00E676▸ Нажми для очищения!"));

        inv.setItem(49, ItemFactory.icon(Material.ARROW, "&7« Назад в меню"));
        p.openInventory(inv);
    }

    // ================= 6C. ХАЛЯВА И ПРОМОКОДЫ (/free) & МУСОРКА (/trash) =================
    public void openFreebieMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("freebie", "", 0), 36,
                ColorUtil.c("&#FFD740&l🎁 ХАЛЯВА И ПРОМОКОДЫ РВ"));
        decorateFrame(inv);
        String[][] promos = new String[][]{
                {"NEWYEAR2026", "+25 000$ + 25❖ + Новогодний Кейс!"},
                {"REALLYWORLD", "+30 000$ + 30❖ + 10 Снежинок ❄!"},
                {"KINGGRIEF",   "+20 000$ + 20❖ + Зелье Киллера!"},
                {"SANTA",       "+15 000$ + 8 Мандаринов + Шампанское!"},
                {"RILIK",       "+35 Риликов ❖ на счёт!"},
                {"FREE",        "+15 000$ + Денежный Кейс!"}
        };
        int[] slots = new int[]{10, 11, 12, 14, 15, 16};
        for (int i = 0; i < promos.length; i++) {
            String code = promos[i][0];
            boolean used = plugin.getStore().hasUsedPromo(p.getName(), code);
            inv.setItem(slots[i], ItemFactory.make(used ? Material.MINECART : Material.CHEST_MINECART, 1,
                    "&#FFD740🎁 Промокод: &f" + code,
                    Arrays.asList("&7Награда: &a" + promos[i][1], "",
                            used ? "&7✔ Уже активирован вами" : "&#00E676▸ Нажми ЛКМ для активации в 1 клик!"),
                    !used, "promo_" + code, null, 0));
        }
        inv.setItem(13, ItemFactory.glowIcon(Material.SNOWBALL, "&#80D8FF&l🎅 Ежедневный Подарок Деда Мороза",
                "&7Каждые 24 часа: &a+20 000$&7, &b+15❖ &7и &b+5❄&7!", "", "&#00E676▸ Нажми, чтобы забрать!"));
        inv.setItem(31, ItemFactory.icon(Material.ARROW, "&7« Назад в меню"));
        p.openInventory(inv);
    }

    public void openTrashMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("trash", "", 0), 36,
                ColorUtil.c("&8🗑 Мусорка (Закрой для удаления вещей)"));
        p.openInventory(inv);
    }

    private ItemStack coinShopTag(ItemStack base, int coinCost) {
        ItemStack copy = base.clone();
        ItemMeta m = copy.getItemMeta();
        if (m != null) {
            List<String> lore = m.hasLore() ? new ArrayList<String>(m.getLore()) : new ArrayList<String>();
            lore.add("");
            lore.add(ColorUtil.c("&#FFEA00Цена: &a" + ColorUtil.fmtMoney(coinCost) + "$ Монет"));
            lore.add(ColorUtil.c("&#00E676▸ Нажмите ЛКМ для покупки!"));
            m.setLore(lore);
            copy.setItemMeta(m);
        }
        return copy;
    }

    // ================= 7. ГРАНД-КАЗИНО 777 (/casino) =================
    public void openCasinoMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("casino", "", 0), 45,
                ColorUtil.c("&#FFD740&l🎰 ГРАНД-КАЗИНО REALLYWORLD 777"));
        decorateFrame(inv);
        inv.setItem(4, ItemFactory.glowIcon(Material.NETHER_STAR, "&#FFD740&l🏆 СЕРВЕРНЫЙ ДЖЕКПОТ: &a" + ColorUtil.fmtMoney(casinoJackpotPool) + "$",
                "&7Каждая игра увеличивает банк Джекпота!",
                "&7Ваш баланс: &a" + ColorUtil.fmtMoney(plugin.getStore().getCoins(p.getName())) + "$"));

        inv.setItem(10, ItemFactory.glowIcon(Material.DIAMOND_BLOCK, "&#00E5FF&l🎰 СЛОТЫ 777 — Ставка 5 000$",
                "&7Крути барабан! 3 одинаковых = &a×5&7, 3 Звезды = &eДЖЕКПОТ&7!"));
        inv.setItem(11, ItemFactory.glowIcon(Material.EMERALD_BLOCK, "&#00E676&l🎰 СЛОТЫ VIP — Ставка 25 000$",
                "&7Высокие ставки! Шанс сорвать весь Джекпот!"));
        inv.setItem(13, ItemFactory.glowIcon(Material.RED_CONCRETE, "&#FF1744&l🔴 РУЛЕТКА: КРАСНОЕ (×2)",
                "&7Ставка: &a10 000$ &8→ &7Выигрыш: &a20 000$"));
        inv.setItem(14, ItemFactory.glowIcon(Material.BLACK_CONCRETE, "&f&l⚫ РУЛЕТКА: ЧЁРНОЕ (×2)",
                "&7Ставка: &a10 000$ &8→ &7Выигрыш: &a20 000$"));
        inv.setItem(15, ItemFactory.glowIcon(Material.LIME_CONCRETE, "&#00E676&l🟢 РУЛЕТКА: ЗЕРО ZERO (×14!)",
                "&7Ставка: &a10 000$ &8→ &7Выигрыш: &a140 000$!"));
        inv.setItem(16, ItemFactory.glowIcon(Material.AMETHYST_CLUSTER, "&#E040FB&l🎡 КОЛЕСО РИЛИКОВ (25 ❖)",
                "&7Покрути за &b25 Риликов &7— выиграй до &b150 Риликов &7или Шар!"));

        // Coinflip PvP rooms
        List<DataStore.CoinflipRoom> cfs = plugin.getStore().getCoinflips();
        inv.setItem(28, ItemFactory.glowIcon(Material.SUNFLOWER, "&#FFD740&l🪙 Создать Монетку PvP (10 000$)",
                "&7Или введи любую сумму: &f/cf <ставка>"));
        for (int i = 0; i < Math.min(5, cfs.size()); i++) {
            DataStore.CoinflipRoom cf = cfs.get(i);
            inv.setItem(30 + i, ItemFactory.glowIcon(Material.GOLD_NUGGET,
                    "&#FFEA00🪙 Игра #" + cf.id + " от &f" + cf.creator,
                    "&7Ставка: &a" + ColorUtil.fmtMoney(cf.bet) + "$",
                    "&7Банк победителя: &a" + ColorUtil.fmtMoney(cf.bet * 2) + "$",
                    "", "&#00E676▸ Нажми, чтобы сыграть 50/50!"));
        }
        inv.setItem(40, ItemFactory.icon(Material.ARROW, "&7« Назад в меню"));
        p.openInventory(inv);
    }

    private void runAnimatedSlots(final Player p, final double bet) {
        if (!plugin.getStore().takeCoins(p.getName(), bet)) {
            p.sendMessage(ColorUtil.c("&cНедостаточно монет! Нужно: " + ColorUtil.fmtMoney(bet) + "$"));
            ColorUtil.sound(p, "entity.villager.no", 1f, 1f);
            return;
        }
        casinoJackpotPool += bet * 0.20;
        final Inventory spinInv = Bukkit.createInventory(new KgMenuHolder("slots_anim", "", 0), 27,
                ColorUtil.c("&#FFD740&l🎰 КРУТЯТСЯ СЛОТЫ 777..."));
        decorateFrame(spinInv);
        p.openInventory(spinInv);

        final Material[] syms = new Material[]{
                Material.CHERRY_SAPLING, Material.GOLD_INGOT, Material.EMERALD,
                Material.DIAMOND, Material.NETHER_STAR
        };
        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (!p.isOnline()) { cancel(); return; }
                tick++;
                Material s1 = syms[rng.nextInt(syms.length)];
                Material s2 = syms[rng.nextInt(syms.length)];
                Material s3 = syms[rng.nextInt(syms.length)];
                spinInv.setItem(11, ItemFactory.glowIcon(s1, "&#FFD740★ БАРАБАН 1"));
                spinInv.setItem(13, ItemFactory.glowIcon(s2, "&#FFD740★ БАРАБАН 2"));
                spinInv.setItem(15, ItemFactory.glowIcon(s3, "&#FFD740★ БАРАБАН 3"));
                ColorUtil.sound(p, "block.note_block.hat", 0.8f, 0.9f + tick * 0.06f);
                if (tick < 12) return;
                cancel();
                double win = 0.0;
                if (s1 == Material.NETHER_STAR && s2 == Material.NETHER_STAR && s3 == Material.NETHER_STAR) {
                    win = casinoJackpotPool + bet * 10;
                    casinoJackpotPool = 100000.0;
                    Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l🎰 ДЖЕКПОТ КАЗИНО! &fИгрок &#00E676" + p.getName()
                            + " &fсорвал банк &a+" + ColorUtil.fmtMoney(win) + "$&f!"));
                } else if (s1 == s2 && s2 == s3) {
                    win = bet * 5.0;
                } else if (s1 == s2 || s2 == s3 || s1 == s3) {
                    win = bet * 1.6;
                }
                if (win > 0) {
                    plugin.getStore().addCoins(p.getName(), win);
                    plugin.getStore().save();
                    ColorUtil.sound(p, "entity.player.levelup", 1f, 1.3f);
                    p.sendMessage(ColorUtil.c("&#00E676&l🎰 ПОБЕДА В СЛОТАХ! &fВы выиграли &a+" + ColorUtil.fmtMoney(win) + "$&f!"));
                } else {
                    ColorUtil.sound(p, "block.note_block.bass", 1f, 0.7f);
                    p.sendMessage(ColorUtil.c("&#FF5252🎰 Не повезло в этот спин! Попробуйте ещё (/casino)."));
                }
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    // ================= 8. БОССЫ И ИВЕНТЫ (/events) & ЧЁРНЫЙ РЫНОК (/bm) =================
    public void openEventsMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("events", "", 0), 45,
                ColorUtil.c("&#FF1744&l🔥 АКТИВНЫЕ БОССЫ И ИВЕНТЫ"));
        decorateFrame(inv);
        List<BossEventManager.ActiveBossEvent> active = plugin.getBossEvents().getActiveEvents();
        inv.setItem(4, ItemFactory.glowIcon(Material.BEACON, "&#FFD740&lРадар Боссов ReallyWorld",
                "&7Активных боссов сейчас: &a" + active.size(),
                "&7Следующий авто-босс через: &e" + ColorUtil.fmtTime(plugin.getBossEvents().getNextAutoSeconds()),
                "&7Все полоски ХП боссов отображаются", "&7сверху экрана друг под другом!"));

        if (active.isEmpty()) {
            inv.setItem(22, ItemFactory.icon(Material.CLOCK, "&eСейчас нет активных Боссов",
                    "&7Новый Босс появится через &f" + ColorUtil.fmtTime(plugin.getBossEvents().getNextAutoSeconds())));
        } else {
            int[] slots = new int[]{19, 21, 23, 25};
            for (int i = 0; i < Math.min(slots.length, active.size()); i++) {
                BossEventManager.ActiveBossEvent ev = active.get(i);
                inv.setItem(slots[i], ItemFactory.glowIcon(ev.template.platformMat,
                        ev.template.displayName,
                        "&7Статус: " + (ev.bossDefeated ? "&#00E676🔓 Сундук Открыт!" : "&#FF1744⚔ Босс Жив!"),
                        "&7Координаты в выживании (/rtp):",
                        " &#80D8FFX: " + ev.center.getBlockX() + "  Y: " + ev.center.getBlockY() + "  Z: " + ev.center.getBlockZ(),
                        "&7Осталось времени: &e" + ColorUtil.fmtTime(ev.secondsLeft)));
            }
        }
        if ("owner".equals(plugin.getRanks().getRank(p).id) || p.isOp()) {
            inv.setItem(38, ItemFactory.glowIcon(Material.BLAZE_SPAWN_EGG, "&#00E676&l⚡ [АДМИН] Запустить Случайного Босса!",
                    "&7Создаёт нового Босса (полоска ХП встанет", "&7ниже текущих ивентов без наложения!)"));
        }
        inv.setItem(40, ItemFactory.icon(Material.ARROW, "&7« Назад в меню"));
        p.openInventory(inv);
    }

    public void openBlackMarket(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("bm", "", 0), 36,
                ColorUtil.c("&#D500F9&l🕵 ЧЁРНЫЙ РЫНОК КОНТРАБАНДИСТА"));
        decorateFrame(inv);
        inv.setItem(11, coinShopTag(ItemFactory.pickSpawner(),     180000));
        inv.setItem(12, coinShopTag(ItemFactory.mysteryEgg(1),      95000));
        inv.setItem(13, coinShopTag(ItemFactory.talFeniksa(3),     320000));
        inv.setItem(14, coinShopTag(ItemFactory.sharTitana(),      290000));
        inv.setItem(15, coinShopTag(ItemFactory.pickDjamshut(),    350000));
        inv.setItem(31, ItemFactory.icon(Material.ARROW, "&7« Назад в меню"));
        p.openInventory(inv);
    }

    // ================= 9. ОБМЕННИК ДЖАМШУТА (/quests) =================
    public void openDjamshutMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("djamshut", "", 0), 36,
                ColorUtil.c("&#FFAB40&l👳 ОБМЕННИК СНЕЖИНОК ДЖАМШУТА"));
        decorateFrame(inv);
        int snow = plugin.getStore().getSnowflakes(p.getName());
        inv.setItem(4, ItemFactory.glowIcon(Material.PRISMARINE_CRYSTALS, "&#80D8FF&lВаши Снежинки: &f" + snow + " ❄",
                "&7Добывай Снежинки на /warp mine, /warp farm и с Боссов!"));
        inv.setItem(11, snowTag(ItemFactory.nyMandarin(8),      10));
        inv.setItem(12, snowTag(ItemFactory.potionChampagne(2), 18));
        inv.setItem(13, snowTag(ItemFactory.icon(Material.DIAMOND, "&#00E5FF+50 Риликов ❖"), 35));
        inv.setItem(14, snowTag(ItemFactory.talStuzhi(),        75));
        inv.setItem(15, snowTag(ItemFactory.sharSanty(),       110));
        inv.setItem(31, ItemFactory.icon(Material.ARROW, "&7« Назад в меню"));
        p.openInventory(inv);
    }

    private ItemStack snowTag(ItemStack base, int snowCost) {
        ItemStack copy = base.clone();
        ItemMeta m = copy.getItemMeta();
        if (m != null) {
            List<String> lore = m.hasLore() ? new ArrayList<String>(m.getLore()) : new ArrayList<String>();
            lore.add("");
            lore.add(ColorUtil.c("&#80D8FFЦена обмена: &f" + snowCost + " Снежинок ❄"));
            lore.add(ColorUtil.c("&#00E676▸ Нажми для обмена!"));
            m.setLore(lore);
            copy.setItemMeta(m);
        }
        return copy;
    }

    // ================= 10. ТИТУЛЫ (/titles) & НАГРАДЫ ЗА ВРЕМЯ (/playtime) =================
    public void openTitlesMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("titles", "", 0), 45,
                ColorUtil.c("&#FFD740&l✨ КОЛЛЕКЦИЯ ТИТУЛОВ СЕРВЕРА"));
        decorateFrame(inv);
        int[] slots = new int[]{10,11,12,13,14,15,19,20,21,22,23,24};
        for (int i = 0; i < TITLES_CATALOG.length && i < slots.length; i++) {
            String[] t = TITLES_CATALOG[i];
            String tid = t[0], disp = t[1], desc = t[3];
            int cost = Integer.parseInt(t[2]);
            boolean unlocked = cost == 0 || plugin.getStore().hasUnlockedTitle(p.getName(), tid);
            inv.setItem(slots[i], ItemFactory.make(unlocked ? Material.NAME_TAG : Material.PAPER, 1, disp,
                    Arrays.asList("&7" + desc, "",
                            unlocked ? "&#00E676✔ РАЗБЛОКИРОВАНО — Нажми, чтобы надеть!"
                                     : (cost > 0 ? "&#00E5FFЦена: &f" + cost + " Риликов ❖" : "&#FFD740Трофейный титул за достижение!")),
                    unlocked, "title_" + tid, null, 0));
        }
        inv.setItem(39, ItemFactory.icon(Material.BARRIER, "&cСнять активный титул"));
        inv.setItem(40, ItemFactory.icon(Material.ARROW, "&7« Назад в меню"));
        inv.setItem(41, ItemFactory.glowIcon(Material.CLOCK, "&#00E676&l⏰ Награды за Время (/playtime)"));
        p.openInventory(inv);
    }

    public void openPlaytimeMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("playtime", "", 0), 36,
                ColorUtil.c("&#00E676&l⏰ НАГРАДЫ ЗА ОНЛАЙН"));
        decorateFrame(inv);
        long sec = plugin.getStore().getPlaySeconds(p.getName());
        inv.setItem(4, ItemFactory.glowIcon(Material.CLOCK, "&#00E676&lВаше время в игре: &f" + ColorUtil.fmtTime(sec)));
        int[][] tiers = new int[][]{
                {900, 10000, 10}, {1800, 25000, 20}, {3600, 50000, 40},
                {10800, 120000, 80}, {21600, 250000, 150}
        };
        int[] slots = new int[]{11, 12, 13, 14, 15};
        for (int i = 0; i < tiers.length; i++) {
            String rid = "pt_" + tiers[i][0];
            boolean claimed = plugin.getStore().hasClaimedPlayReward(p.getName(), rid);
            boolean ready = sec >= tiers[i][0];
            inv.setItem(slots[i], ItemFactory.make(claimed ? Material.MINECART : Material.CHEST_MINECART, 1,
                    "&#FFD740Награда за " + ColorUtil.fmtTime(tiers[i][0]),
                    Arrays.asList("&7Приз: &a+" + ColorUtil.fmtMoney(tiers[i][1]) + "$ &7и &b+" + tiers[i][2] + " Риликов ❖", "",
                            claimed ? "&7✔ Уже получено" : (ready ? "&#00E676▸ Нажми, чтобы забрать!" : "&cЕщё нужно поиграть!")),
                    ready && !claimed, rid, null, 0));
        }
        inv.setItem(31, ItemFactory.icon(Material.ARROW, "&7« Назад в меню"));
        p.openInventory(inv);
    }

    // ================= 11. СКУПЩИК, КЕЙСЫ, ДОНАТ, АУКЦИОН, РАБОТЫ, БП, КЛАНЫ, LP GUI =================
    public void openBuyerMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("buyer", "", 0), 45,
                ColorUtil.c("&#69F0AE&l💰 СКУПЩИК РЕСУРСОВ REALLYWORLD"));
        decorateFrame(inv);
        double mult = 1.0 + plugin.getRanks().getRank(p).tier * 0.15;
        inv.setItem(4, ItemFactory.glowIcon(Material.EMERALD_BLOCK, "&#00E676&l⚡ ПРОДАТЬ ВСЁ ИЗ ИНВЕНТАРЯ В 1 КЛИК!",
                "&7Ваш бонус привилегии: &e×" + String.format(Locale.US, "%.2f", Double.valueOf(mult)),
                "", "&#00E676▸ Нажми, чтобы продать всю руду, слитки, урожай и дроп!"));
        Material[] mats = new Material[]{
                Material.COAL, Material.IRON_INGOT, Material.GOLD_INGOT, Material.DIAMOND,
                Material.EMERALD, Material.NETHERITE_INGOT, Material.LAPIS_LAZULI,
                Material.ROTTEN_FLESH, Material.BONE, Material.GUNPOWDER, Material.BLAZE_ROD,
                Material.WHEAT, Material.CARROT, Material.POTATO
        };
        int[] prices = new int[]{25, 70, 110, 350, 420, 2200, 35, 18, 22, 40, 95, 15, 15, 15};
        int[] slots = new int[]{10,11,12,13,14,15,16,19,20,21,22,23,24,25};
        for (int i = 0; i < mats.length; i++) {
            long unit = Math.round(prices[i] * mult);
            inv.setItem(slots[i], ItemFactory.icon(mats[i], "&fПродать " + mats[i].name(),
                    "&7Цена за 1 шт.: &a" + unit + "$", "&e▸ ЛКМ — Продать все стаки из инвентаря"));
        }
        inv.setItem(40, ItemFactory.icon(Material.ARROW, "&7« Назад в меню"));
        p.openInventory(inv);
    }

    public void openCasesMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("cases", "", 0), 45,
                ColorUtil.c("&#FF4081&l🎁 11 ШАЛКЕР-КЕЙСОВ СЕРВЕРА"));
        decorateFrame(inv);
        List<SpawnEngine.CasePedestal> list = plugin.getSpawnEngine().getCasePedestals();
        int[] slots = new int[]{10,11,12,13,14,15,16,20,21,23,24};
        for (int i = 0; i < Math.min(slots.length, list.size()); i++) {
            SpawnEngine.CasePedestal cp = list.get(i);
            int keys = plugin.getStore().getCaseKeys(p.getName(), cp.caseId);
            inv.setItem(slots[i], ItemFactory.make(cp.shulkerMat, 1, cp.title,
                    Arrays.asList(cp.subtitle, "", "&7Ваши ключи: " + (keys > 0 ? "&a" + keys + " шт." : "&c0 шт."),
                            "&7Цена на сайте: &#FFD740" + cp.priceRub + "₽ &8(kinggrief.vercel.app)", "",
                            "&#00E676▸ ЛКМ — Открыть кейс!", "&#80D8FF▸ ПКМ — Телепорт к Шалкеру на спавне!"),
                    keys > 0, "case_" + cp.caseId, null, 0));
        }
        inv.setItem(40, ItemFactory.icon(Material.ARROW, "&7« Назад в меню"));
        p.openInventory(inv);
    }

    public void openDonateMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("donate", "", 0), 54,
                ColorUtil.c("&#FFD740&l👑 ПРИВИЛЕГИИ KINGGRIEF (kinggrief.vercel.app)"));
        decorateFrame(inv);
        String[] topRanks = new String[]{
                "pegas", "cobra", "bunny", "rabbit", "vampire", "god", "king",
                "dmoder", "dstmoder", "dglmoder", "dmladmin", "dadmin", "opka", "downer"
        };
        int[] slots = new int[]{10,11,12,13,14,15,16,19,20,21,22,23,24,25};
        for (int i = 0; i < topRanks.length; i++) {
            RankManager.RankDef r = plugin.getRanks().byId(topRanks[i]);
            inv.setItem(slots[i], ItemFactory.glowIcon(
                    i >= 7 ? Material.NETHER_STAR : Material.EMERALD,
                    r.shortLabel + " &8— &#FFD740" + r.priceRub + "₽",
                    "&7Домов: &e" + r.maxHomes + " &8| &7Приватов: &e" + r.maxClaims,
                    "&7Личный богатый кит: &f/kit " + r.kitName,
                    "", "&#00E676▸ Нажми для ссылки на покупку!"));
        }
        inv.setItem(49, ItemFactory.icon(Material.ARROW, "&7« Назад в меню"));
        p.openInventory(inv);
    }

    public void openAuctionMenu(Player p, int page) {
        List<DataStore.AuctionLot> lots = plugin.getStore().getAuctionLots();
        int pg = Math.max(0, page);
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("ah", "", pg), 54,
                ColorUtil.c("&#FFAB40&l⚖ АУКЦИОН ИГРОКОВ (Стр. " + (pg + 1) + ")"));
        int start = pg * 45;
        for (int i = 0; i < 45 && (start + i) < lots.size(); i++) {
            DataStore.AuctionLot lot = lots.get(start + i);
            ItemStack disp = lot.item.clone();
            ItemMeta m = disp.getItemMeta();
            if (m != null) {
                List<String> l = m.hasLore() ? new ArrayList<String>(m.getLore()) : new ArrayList<String>();
                l.add("");
                l.add(ColorUtil.c("&7Продавец: &f" + lot.seller));
                l.add(ColorUtil.c("&7Цена: &a" + ColorUtil.fmtMoney(lot.price) + "$"));
                l.add(ColorUtil.c("&#00E676▸ Нажмите для покупки! (ID #" + lot.id + ")"));
                m.setLore(l);
                disp.setItemMeta(m);
            }
            inv.setItem(i, disp);
        }
        for (int s = 45; s < 54; s++) inv.setItem(s, ItemFactory.icon(Material.BLACK_STAINED_GLASS_PANE, " "));
        inv.setItem(49, ItemFactory.icon(Material.ARROW, "&7« Назад в меню &8(/ah sell <цена>)"));
        p.openInventory(inv);
    }

    public void openJobsMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("jobs", "", 0), 27,
                ColorUtil.c("&#AEEA00&l💼 ВЫБОР РАБОТЫ REALLYWORLD"));
        decorateFrame(inv);
        String cur = plugin.getStore().getJob(p.getName());
        inv.setItem(10, ItemFactory.glowIcon(Material.DIAMOND_PICKAXE, "&#38BDF8⛏ Шахтёр" + ("miner".equals(cur) ? " &a[АКТИВНА]" : ""), "&7Плата за добычу камня и руды!"));
        inv.setItem(12, ItemFactory.glowIcon(Material.IRON_AXE, "&#AEEA00🪓 Дровосек" + ("lumber".equals(cur) ? " &a[АКТИВНА]" : ""), "&7Плата за рубку любого дерева!"));
        inv.setItem(14, ItemFactory.glowIcon(Material.BOW, "&#FF9100🏹 Охотник" + ("hunter".equals(cur) ? " &a[АКТИВНА]" : ""), "&7Плата за убийство мобов!"));
        inv.setItem(16, ItemFactory.glowIcon(Material.NETHERITE_SWORD, "&#FF1744⚔ Гладиатор" + ("killer".equals(cur) ? " &a[АКТИВНА]" : ""), "&7Премия за PvP-победы!"));
        p.openInventory(inv);
    }

    public void openBpMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("bp", "", 0), 36,
                ColorUtil.c("&#80D8FF&l🎄 НОВОГОДНИЙ БОЕВОЙ ПРОПУСК"));
        decorateFrame(inv);
        int score = plugin.getStore().getMinedBlocks(p.getName()) / 20 + plugin.getStore().getKills(p.getName()) * 5;
        for (int lv = 1; lv <= 7; lv++) {
            int req = lv * 10;
            boolean done = plugin.getStore().hasClaimedBp(p.getName(), lv);
            boolean ready = score >= req;
            inv.setItem(9 + lv, ItemFactory.make(done ? Material.MINECART : Material.CHEST_MINECART, lv,
                    "&#FFD740Уровень БП #" + lv,
                    Arrays.asList("&7Очков прогресса: &f" + score + " / " + req,
                            "&7Награда: &a+" + (lv * 15000) + "$ &7+ &b" + (lv * 15) + "❖ &7+ &b" + (lv * 5) + "❄", "",
                            done ? "&7✔ Получено" : (ready ? "&#00E676▸ Забрать награду!" : "&cКопи очки шахтой и PvP!")),
                    ready && !done, "bp_" + lv, null, 0));
        }
        inv.setItem(31, ItemFactory.icon(Material.ARROW, "&7« Назад в меню"));
        p.openInventory(inv);
    }

    public void openClanMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("clans", "", 0), 36,
                ColorUtil.c("&#B388FF&l🛡 ТОП КЛАНОВ СЕРВЕРА"));
        decorateFrame(inv);
        List<DataStore.Clan> all = new ArrayList<DataStore.Clan>(plugin.getStore().getAllClans());
        Collections.sort(all, new Comparator<DataStore.Clan>() {
            @Override
            public int compare(DataStore.Clan a, DataStore.Clan b) {
                return Integer.compare(b.kills, a.kills);
            }
        });
        inv.setItem(4, ItemFactory.glowIcon(Material.SHIELD, "&#B388FFУправление Кланом (/clan)",
                "&f/clan create <тег> &7— Создать клан (25 000$)",
                "&f/clan invite <ник> &8| &f/clan home &8| &f/clan sethome"));
        int[] slots = new int[]{10,11,12,13,14,15,16};
        for (int i = 0; i < Math.min(slots.length, all.size()); i++) {
            DataStore.Clan c = all.get(i);
            inv.setItem(slots[i], ItemFactory.glowIcon(Material.DRAGON_HEAD,
                    "&#FFD740#" + (i + 1) + " &#B388FF[" + c.tag + "]",
                    "&7Лидер: &f" + c.leader, "&7Участников: &a" + c.members.size(),
                    "&7Убийств: &c" + c.kills + " &8| &7Банк: &e" + ColorUtil.fmtMoney(c.bank) + "$"));
        }
        inv.setItem(31, ItemFactory.icon(Material.ARROW, "&7« Назад в меню"));
        p.openInventory(inv);
    }

    public void openLpGroupsGui(Player p, int page) {
        List<RankManager.RankDef> all = new ArrayList<RankManager.RankDef>(plugin.getRanks().allRanks());
        int pg = Math.max(0, page);
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("lp_groups", "", pg), 54,
                ColorUtil.c("&#00E676&l⚡ LP EDITOR — ГРУППЫ (Стр. " + (pg + 1) + ")"));
        int start = pg * 45;
        for (int i = 0; i < 45 && (start + i) < all.size(); i++) {
            RankManager.RankDef r = all.get(start + i);
            inv.setItem(i, ItemFactory.make(Material.BOOK, 1, r.shortLabel + " &8(&f" + r.id + "&8)",
                    Arrays.asList("&7Вес группы: &e" + r.weight, "&7Префикс: " + r.chatPrefix + "&fНик",
                            "&7Доп. прав: &b" + plugin.getStore().getGroupPermissions(r.id).size(),
                            "", "&#00E676▸ Нажми для настройки прав группы!"),
                    r.weight >= 90, "lpg_" + r.id, null, 0));
        }
        inv.setItem(45, pg > 0 ? ItemFactory.icon(Material.ARROW, "&#FFD740« Пред. страница") : ItemFactory.icon(Material.BLACK_STAINED_GLASS_PANE, " "));
        inv.setItem(49, ItemFactory.glowIcon(Material.EMERALD_BLOCK, "&#00E676&l🌐 ОТКРЫТЬ WEB LP EDITOR В БРАУЗЕРЕ",
                "&fhttps://kinggrief.vercel.app/lp-editor"));
        inv.setItem(53, (start + 45) < all.size() ? ItemFactory.icon(Material.ARROW, "&#FFD740След. страница »") : ItemFactory.icon(Material.BLACK_STAINED_GLASS_PANE, " "));
        p.openInventory(inv);
    }

    public void openLpGroupEditGui(Player p, String groupId) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("lp_edit", groupId, 0), 36,
                ColorUtil.c("&#00E676&l⚡ ПРАВА ГРУППЫ: " + groupId.toUpperCase(Locale.ROOT)));
        decorateFrame(inv);
        Map<String, Boolean> map = plugin.getStore().getGroupPermissions(groupId);
        int[] slots = new int[]{10,11,12,13,14,15,19,20,21,22,23,24};
        for (int i = 0; i < COMMON_GROUP_PERMS.length && i < slots.length; i++) {
            String perm = COMMON_GROUP_PERMS[i];
            boolean on = Boolean.TRUE.equals(map.get(perm));
            inv.setItem(slots[i], ItemFactory.make(on ? Material.LIME_DYE : Material.GRAY_DYE, 1,
                    (on ? "&#00E676✔ " : "&7✖ ") + perm,
                    Arrays.asList("&7Статус: " + (on ? "&aВКЛЮЧЕНО" : "&cВЫКЛЮЧЕНО"), "", "&e▸ Нажми для переключения!"),
                    on, perm, null, 0));
        }
        inv.setItem(31, ItemFactory.icon(Material.ARROW, "&7« Назад к списку групп"));
        p.openInventory(inv);
    }

    // ================= ОБРАБОТКА ВСЕХ КЛИКОВ GUI (100% БЕЗ БАГОВ) =================
    public void handleInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Inventory top = e.getView().getTopInventory();
        if (top == null || !(top.getHolder() instanceof KgMenuHolder)) return;
        KgMenuHolder h = (KgMenuHolder) top.getHolder();
        if ("trash".equals(h.menuType)) {
            // Allow placing items freely into trash inventory!
            return;
        }
        e.setCancelled(true);
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(top)) return;

        Player p = (Player) e.getWhoClicked();
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        int slot = e.getSlot();
        String type = h.menuType;

        if ("main".equals(type)) {
            if (slot == 10) openRtpMenu(p);
            else if (slot == 11) openWarpsMenu(p);
            else if (slot == 12) openRelicShop(p);
            else if (slot == 13) openKitsMenu(p, 0);
            else if (slot == 14) openCasesMenu(p);
            else if (slot == 15) openDonateMenu(p);
            else if (slot == 16) openCasinoMenu(p);
            else if (slot == 19) openBuyerMenu(p);
            else if (slot == 20) openAuctionMenu(p, 0);
            else if (slot == 21) openEventsMenu(p);
            else if (slot == 22) openBlackMarket(p);
            else if (slot == 23) openEnchantsMenu(p);
            else if (slot == 24) openDjamshutMenu(p);
            else if (slot == 25) openClanMenu(p);
            else if (slot == 29) openJobsMenu(p);
            else if (slot == 30) openBpMenu(p);
            else if (slot == 31) { p.closeInventory(); p.teleport(plugin.getSpawnEngine().getSpawnArrival()); }
            else if (slot == 32) openTitlesMenu(p);
            else if (slot == 33) openPlaytimeMenu(p);
            else if (slot == 38) openBafovarMenu(p);
            else if (slot == 42) openFreebieMenu(p);
            else if (slot == 49 && plugin.getLp().hasLpAccess(p)) openLpGroupsGui(p, 0);
        } else if ("bafovar".equals(type)) {
            if (slot == 49) { openMainMenu(p); return; }
            handleBafovarClick(p, slot);
        } else if ("freebie".equals(type)) {
            if (slot == 31) { openMainMenu(p); return; }
            if (slot == 13) { p.closeInventory(); p.performCommand("daily"); return; }
            String cid = ItemFactory.getCustomId(clicked);
            if (cid != null && cid.startsWith("promo_")) {
                p.performCommand("promo " + cid.substring(6));
                openFreebieMenu(p);
            }
        } else if ("rtp".equals(type)) {
            p.closeInventory();
            if (slot == 11) plugin.getSpawnEngine().performRtp(p, "normal");
            else if (slot == 13) plugin.getSpawnEngine().performRtp(p, "far");
            else if (slot == 15) plugin.getSpawnEngine().performRtp(p, "nether");
        } else if ("warps".equals(type)) {
            if (slot == 40) { openMainMenu(p); return; }
            String w = slot == 10 ? "mine" : slot == 11 ? "farm" : slot == 12 ? "casino"
                    : slot == 13 ? "pvp" : slot == 14 ? "kabinki" : slot == 15 ? "parkour"
                    : slot == 16 ? "cases" : slot == 22 ? "enchant" : slot == 31 ? "spawn" : null;
            if (w != null) {
                p.closeInventory();
                p.teleport("spawn".equals(w) ? plugin.getSpawnEngine().getSpawnArrival() : plugin.getSpawnEngine().getWarpLocation(w));
                ColorUtil.sound(p, "entity.enderman.teleport", 1f, 1.1f);
            }
        } else if ("kits".equals(type)) {
            if (slot == 45 && h.page > 0) { openKitsMenu(p, 0); return; }
            if (slot == 53 && h.page == 0) { openKitsMenu(p, 1); return; }
            if (slot == 49) { openMainMenu(p); return; }
            String cid = ItemFactory.getCustomId(clicked);
            if (cid != null && cid.startsWith("kit_")) {
                String kid = cid.substring(4);
                if (e.getClick() == ClickType.RIGHT || e.getClick() == ClickType.SHIFT_RIGHT) {
                    openKitPreview(p, kid, h.page);
                } else {
                    p.closeInventory();
                    tryClaimKit(p, kid);
                }
            }
        } else if ("kit_preview".equals(type)) {
            if (slot == 49) openKitsMenu(p, h.page);
        } else if ("shop".equals(type)) {
            if (slot == 49) { openMainMenu(p); return; }
            handleRelicPurchase(p, slot);
        } else if ("enchants".equals(type)) {
            if (slot == 40) { openMainMenu(p); return; }
            if (slot == 22) {
                if (!plugin.getStore().takeCoins(p.getName(), 10000)) {
                    p.sendMessage(ColorUtil.c("&cНужно 10 000$ для починки всех вещей!"));
                    return;
                }
                repairAllItems(p);
                p.sendMessage(ColorUtil.c("&#00E676🔧 Все предметы и броня в инвентаре полностью починены!"));
                ColorUtil.sound(p, "block.anvil.use", 1f, 1.1f);
                return;
            }
            handleCoinPurchase(p, "enchants", slot);
        } else if ("bm".equals(type)) {
            if (slot == 31) { openMainMenu(p); return; }
            handleCoinPurchase(p, "bm", slot);
        } else if ("djamshut".equals(type)) {
            if (slot == 31) { openMainMenu(p); return; }
            handleSnowflakeExchange(p, slot);
        } else if ("casino".equals(type)) {
            if (slot == 40) { openMainMenu(p); return; }
            handleCasinoClick(p, slot);
        } else if ("events".equals(type)) {
            if (slot == 40) { openMainMenu(p); return; }
            if (slot == 38 && ("owner".equals(plugin.getRanks().getRank(p).id) || p.isOp())) {
                plugin.getBossEvents().startRandomEvent();
                openEventsMenu(p);
            }
        } else if ("titles".equals(type)) {
            if (slot == 40) { openMainMenu(p); return; }
            if (slot == 41) { openPlaytimeMenu(p); return; }
            if (slot == 39) {
                plugin.getStore().setActiveTitleDisplay(p.getName(), "");
                plugin.getHud().updatePlayerHud(p);
                p.sendMessage(ColorUtil.c("&eАктивный титул снят."));
                return;
            }
            handleTitleClick(p, clicked);
        } else if ("playtime".equals(type)) {
            if (slot == 31) { openMainMenu(p); return; }
            handlePlaytimeReward(p, clicked);
        } else if ("buyer".equals(type)) {
            if (slot == 40) { openMainMenu(p); return; }
            handleBuyerSell(p, slot, clicked.getType());
        } else if ("cases".equals(type)) {
            if (slot == 40) { openMainMenu(p); return; }
            String cid = ItemFactory.getCustomId(clicked);
            if (cid != null && cid.startsWith("case_")) {
                String caseId = cid.substring(5);
                for (SpawnEngine.CasePedestal cp : plugin.getSpawnEngine().getCasePedestals()) {
                    if (cp.caseId.equals(caseId)) {
                        p.closeInventory();
                        if (e.getClick() == ClickType.RIGHT) {
                            p.teleport(plugin.getSpawnEngine().getWarpLocation("cases"));
                        } else {
                            plugin.getSpawnEngine().openShulkerCaseAnimated(p, cp);
                        }
                        break;
                    }
                }
            }
        } else if ("donate".equals(type)) {
            if (slot == 49) openMainMenu(p);
            else {
                p.closeInventory();
                p.sendMessage(ColorUtil.c("&#FFD740👑 Покупка и моментальная авто-выдача на сайте: &#FF4081https://kinggrief.vercel.app"));
            }
        } else if ("ah".equals(type)) {
            if (slot == 49) { openMainMenu(p); return; }
            List<DataStore.AuctionLot> lots = plugin.getStore().getAuctionLots();
            int idx = h.page * 45 + slot;
            if (idx >= 0 && idx < lots.size()) {
                DataStore.AuctionLot lot = lots.get(idx);
                if (!plugin.getStore().takeCoins(p.getName(), lot.price)) {
                    p.sendMessage(ColorUtil.c("&cНедостаточно монет!"));
                    return;
                }
                DataStore.AuctionLot removed = plugin.getStore().removeAuctionLot(lot.id);
                if (removed != null) {
                    plugin.getStore().addCoins(removed.seller, removed.price);
                    p.getInventory().addItem(removed.item);
                    p.sendMessage(ColorUtil.c("&#00E676✔ Вы купили лот #" + removed.id + " за " + ColorUtil.fmtMoney(removed.price) + "$!"));
                    openAuctionMenu(p, h.page);
                }
            }
        } else if ("jobs".equals(type)) {
            String job = slot == 10 ? "miner" : slot == 12 ? "lumber" : slot == 14 ? "hunter" : slot == 16 ? "killer" : null;
            if (job != null) {
                plugin.getStore().setJob(p.getName(), job);
                plugin.getStore().save();
                p.sendMessage(ColorUtil.c("&#00E676💼 Вы устроились на работу!"));
                openJobsMenu(p);
            }
        } else if ("bp".equals(type)) {
            if (slot == 31) { openMainMenu(p); return; }
            int lv = slot - 9;
            int score = plugin.getStore().getMinedBlocks(p.getName()) / 20 + plugin.getStore().getKills(p.getName()) * 5;
            if (lv >= 1 && lv <= 7 && score >= lv * 10 && !plugin.getStore().hasClaimedBp(p.getName(), lv)) {
                plugin.getStore().setClaimedBp(p.getName(), lv);
                plugin.getStore().addCoins(p.getName(), lv * 15000);
                plugin.getStore().addRelics(p.getName(), lv * 15);
                plugin.getStore().addSnowflakes(p.getName(), lv * 5);
                plugin.getStore().save();
                ColorUtil.sound(p, "entity.player.levelup", 1f, 1.2f);
                openBpMenu(p);
            }
        } else if ("clans".equals(type)) {
            if (slot == 31) openMainMenu(p);
        } else if ("lp_groups".equals(type)) {
            if (slot == 45 && h.page > 0) { openLpGroupsGui(p, h.page - 1); return; }
            if (slot == 53) { openLpGroupsGui(p, h.page + 1); return; }
            if (slot == 49) { p.closeInventory(); plugin.getLp().openEditorSession(p); return; }
            String cid = ItemFactory.getCustomId(clicked);
            if (cid != null && cid.startsWith("lpg_")) {
                openLpGroupEditGui(p, cid.substring(4));
            }
        } else if ("lp_edit".equals(type)) {
            if (slot == 31) { openLpGroupsGui(p, 0); return; }
            String perm = ItemFactory.getCustomId(clicked);
            if (perm != null) {
                boolean cur = Boolean.TRUE.equals(plugin.getStore().getGroupPermissions(h.extra).get(perm));
                if (cur) plugin.getStore().unsetGroupPermission(h.extra, perm);
                else plugin.getStore().setGroupPermission(h.extra, perm, true);
                for (Player on : Bukkit.getOnlinePlayers()) plugin.getStore().refreshPermissions(on, plugin.getRanks());
                openLpGroupEditGui(p, h.extra);
            }
        }
    }

    private void handleRelicPurchase(Player p, int slot) {
        ItemStack reward = null;
        int cost = 0;
        switch (slot) {
            case 10: reward = ItemFactory.sharTitana(); cost = 290; break;
            case 11: reward = ItemFactory.sharAndromedy(); cost = 260; break;
            case 12: reward = ItemFactory.sharChimery(); cost = 220; break;
            case 13: reward = ItemFactory.sharApollona(); cost = 270; break;
            case 14: reward = ItemFactory.sharAida(); cost = 280; break;
            case 15: reward = ItemFactory.sharOsirisa(); cost = 240; break;
            case 16: reward = ItemFactory.sharSanty(); cost = 350; break;
            case 19: reward = ItemFactory.talKrushitelya(); cost = 310; break;
            case 20: reward = ItemFactory.talGrani(); cost = 290; break;
            case 21: reward = ItemFactory.talDedala(); cost = 250; break;
            case 22: reward = ItemFactory.talTritona(); cost = 230; break;
            case 23: reward = ItemFactory.talFeniksa(3); cost = 390; break;
            case 24: reward = ItemFactory.talEchidny(); cost = 280; break;
            case 25: reward = ItemFactory.talStuzhi(); cost = 300; break;
            case 28: reward = ItemFactory.potionKiller(2); cost = 45; break;
            case 29: reward = ItemFactory.potionBurp(2); cost = 40; break;
            case 30: reward = ItemFactory.potionAcid(2); cost = 45; break;
            case 31: reward = ItemFactory.pickDjamshut(); cost = 340; break;
            case 32: reward = ItemFactory.pickSpawner(); cost = 190; break;
            case 33: reward = ItemFactory.swordDracula(); cost = 320; break;
            case 34: reward = ItemFactory.swordKrush(); cost = 420; break;
            case 38: reward = ItemFactory.bowIceRw(); cost = 210; break;
            case 39: reward = ItemFactory.mysteryEgg(2); cost = 110; break;
            case 40: reward = ItemFactory.potionMedic(2); cost = 35; break;
            case 41: reward = ItemFactory.potionAgent(2); cost = 35; break;
            case 42: reward = ItemFactory.claimBlock(Material.EMERALD_ORE, 30, "&#00E676💚 Изумрудный Приват 61×61"); cost = 150; break;
            default: return;
        }
        if (!plugin.getStore().takeRelics(p, cost)) {
            ColorUtil.sound(p, "entity.villager.no", 1f, 1f);
            p.sendMessage(ColorUtil.c("&#FF1744✖ Недостаточно Риликов! Нужно: &b" + cost + " ❖ &7(Сайт: kinggrief.vercel.app)"));
            return;
        }
        plugin.getStore().save();
        p.getInventory().addItem(reward);
        ColorUtil.sound(p, "entity.player.levelup", 1f, 1.25f);
        p.sendMessage(ColorUtil.c("&#00E676✔ Вы приобрели " + reward.getItemMeta().getDisplayName() + " &#00E676за &b" + cost + " Риликов ❖&7!"));
        openRelicShop(p);
    }

    private void handleCoinPurchase(Player p, String menu, int slot) {
        ItemStack reward = null;
        int cost = 0;
        if ("enchants".equals(menu)) {
            if (slot == 10) { reward = ItemFactory.potionKiller(1); cost = 15000; }
            else if (slot == 11) { reward = ItemFactory.potionMedic(1); cost = 12000; }
            else if (slot == 12) { reward = ItemFactory.potionFlash(1); cost = 10000; }
            else if (slot == 13) { reward = ItemFactory.potionAgent(1); cost = 14000; }
            else if (slot == 14) { reward = ItemFactory.potionChampagne(1); cost = 16000; }
            else if (slot == 15) { reward = ItemFactory.potionBurp(1); cost = 18000; }
            else if (slot == 16) { reward = ItemFactory.potionAcid(1); cost = 20000; }
            else if (slot == 20) { reward = ItemFactory.arrowIce(16); cost = 9000; }
            else if (slot == 21) { reward = ItemFactory.xpOrb100(1); cost = 12000; }
            else if (slot == 23) { reward = ItemFactory.claimBlock(Material.IRON_ORE, 12, "&#38BDF8🛡 Железный Приват 25×25"); cost = 15000; }
            else if (slot == 24) { reward = ItemFactory.claimBlock(Material.DIAMOND_ORE, 20, "&#38BDF8💎 Алмазный Приват 41×41"); cost = 45000; }
        } else {
            if (slot == 11) { reward = ItemFactory.pickSpawner(); cost = 180000; }
            else if (slot == 12) { reward = ItemFactory.mysteryEgg(1); cost = 95000; }
            else if (slot == 13) { reward = ItemFactory.talFeniksa(3); cost = 320000; }
            else if (slot == 14) { reward = ItemFactory.sharTitana(); cost = 290000; }
            else if (slot == 15) { reward = ItemFactory.pickDjamshut(); cost = 350000; }
        }
        if (reward == null) return;
        if (!plugin.getStore().takeCoins(p.getName(), cost)) {
            ColorUtil.sound(p, "entity.villager.no", 1f, 1f);
            p.sendMessage(ColorUtil.c("&cНедостаточно монет! Нужно: " + ColorUtil.fmtMoney(cost) + "$"));
            return;
        }
        plugin.getStore().save();
        p.getInventory().addItem(reward);
        ColorUtil.sound(p, "entity.experience_orb.pickup", 1f, 1.2f);
        p.sendMessage(ColorUtil.c("&#00E676✔ Куплено за &a" + ColorUtil.fmtMoney(cost) + "$&7!"));
    }

    private void handleSnowflakeExchange(Player p, int slot) {
        int cost = slot == 11 ? 10 : slot == 12 ? 18 : slot == 13 ? 35 : slot == 14 ? 75 : slot == 15 ? 110 : 0;
        if (cost <= 0) return;
        if (!plugin.getStore().takeSnowflakes(p.getName(), cost)) {
            p.sendMessage(ColorUtil.c("&cНедостаточно Снежинок ❄! Фармите на /warp mine и /warp farm."));
            return;
        }
        if (slot == 11) p.getInventory().addItem(ItemFactory.nyMandarin(8));
        else if (slot == 12) p.getInventory().addItem(ItemFactory.potionChampagne(2));
        else if (slot == 13) plugin.getStore().addRelics(p.getName(), 50);
        else if (slot == 14) p.getInventory().addItem(ItemFactory.talStuzhi());
        else if (slot == 15) p.getInventory().addItem(ItemFactory.sharSanty());
        plugin.getStore().save();
        ColorUtil.sound(p, "entity.player.levelup", 1f, 1.3f);
        p.sendMessage(ColorUtil.c("&#80D8FF❄ Обмен у Джамшута выполнен успешно!"));
        openDjamshutMenu(p);
    }

    private void handleCasinoClick(Player p, int slot) {
        if (slot == 10) { runAnimatedSlots(p, 5000.0); return; }
        if (slot == 11) { runAnimatedSlots(p, 25000.0); return; }
        if (slot == 13 || slot == 14 || slot == 15) {
            if (!plugin.getStore().takeCoins(p.getName(), 10000.0)) {
                p.sendMessage(ColorUtil.c("&cНужно 10 000$ для ставки на Рулетке!"));
                return;
            }
            casinoJackpotPool += 1500.0;
            int roll = rng.nextInt(15); // 0 = green, 1..7 = red, 8..14 = black
            boolean win = (slot == 15 && roll == 0) || (slot == 13 && roll >= 1 && roll <= 7) || (slot == 14 && roll >= 8);
            if (win) {
                double prize = slot == 15 ? 140000.0 : 20000.0;
                plugin.getStore().addCoins(p.getName(), prize);
                ColorUtil.sound(p, "entity.player.levelup", 1f, 1.3f);
                p.sendMessage(ColorUtil.c("&#00E676🎰 РУЛЕТКА! Выпало число &f" + roll + " &8→ &aВыигрыш +" + ColorUtil.fmtMoney(prize) + "$!"));
            } else {
                ColorUtil.sound(p, "block.note_block.bass", 1f, 0.7f);
                p.sendMessage(ColorUtil.c("&#FF5252🎰 Рулетка выдала число &f" + roll + "&7. Ставка не сыграла!"));
            }
            plugin.getStore().save();
            openCasinoMenu(p);
            return;
        }
        if (slot == 16) {
            if (!plugin.getStore().takeRelics(p, 25)) {
                p.sendMessage(ColorUtil.c("&cНужно 25 Риликов ❖ для прокрутки Колеса!"));
                return;
            }
            int r = rng.nextInt(100);
            if (r < 8) {
                p.getInventory().addItem(ItemFactory.sharApollona());
                p.sendMessage(ColorUtil.c("&#FFD740🎡 СУПЕРПРИЗ КОЛЕСА! Вы выбили Шар Аполлона!"));
            } else if (r < 45) {
                int won = 40 + rng.nextInt(80);
                plugin.getStore().addRelics(p.getName(), won);
                p.sendMessage(ColorUtil.c("&#00E676🎡 Колесо Риликов принесло вам &b+" + won + " ❖&7!"));
            } else {
                plugin.getStore().addCoins(p.getName(), 25000);
                p.sendMessage(ColorUtil.c("&e🎡 Утешительный приз: +25 000$ Монет!"));
            }
            plugin.getStore().save();
            openCasinoMenu(p);
            return;
        }
        if (slot == 28) {
            if (!plugin.getStore().takeCoins(p.getName(), 10000.0)) {
                p.sendMessage(ColorUtil.c("&cНужно 10 000$ для создания Монетки!"));
                return;
            }
            plugin.getStore().createCoinflip(p.getName(), 10000.0);
            Bukkit.broadcastMessage(ColorUtil.c("&#FFD740🪙 [Монетка] &f" + p.getName() + " &7создал игру на &a10 000$&7! Принять: &f/casino"));
            openCasinoMenu(p);
            return;
        }
        if (slot >= 30 && slot <= 34) {
            List<DataStore.CoinflipRoom> cfs = plugin.getStore().getCoinflips();
            int idx = slot - 30;
            if (idx < cfs.size()) {
                DataStore.CoinflipRoom target = cfs.get(idx);
                if (target.creator.equalsIgnoreCase(p.getName())) {
                    p.sendMessage(ColorUtil.c("&eНельзя играть против самого себя!"));
                    return;
                }
                if (!plugin.getStore().takeCoins(p.getName(), target.bet)) {
                    p.sendMessage(ColorUtil.c("&cНедостаточно монет для принятия ставки!"));
                    return;
                }
                DataStore.CoinflipRoom taken = plugin.getStore().takeCoinflip(target.id);
                if (taken == null) {
                    plugin.getStore().addCoins(p.getName(), target.bet);
                    return;
                }
                String winner = rng.nextBoolean() ? p.getName() : taken.creator;
                double pot = taken.bet * 2.0;
                plugin.getStore().addCoins(winner, pot);
                plugin.getStore().save();
                Bukkit.broadcastMessage(ColorUtil.c("&#FFD740🪙 [Монетка] &f" + winner + " &aпобедил в игре 50/50 и забрал &e" + ColorUtil.fmtMoney(pot) + "$&a!"));
                openCasinoMenu(p);
            }
        }
    }

    private void handleTitleClick(Player p, ItemStack clicked) {
        String cid = ItemFactory.getCustomId(clicked);
        if (cid == null || !cid.startsWith("title_")) return;
        String tid = cid.substring(6);
        for (String[] row : TITLES_CATALOG) {
            if (row[0].equals(tid)) {
                int cost = Integer.parseInt(row[2]);
                boolean unlocked = cost == 0 || plugin.getStore().hasUnlockedTitle(p.getName(), tid);
                if (!unlocked) {
                    if (cost < 0) {
                        p.sendMessage(ColorUtil.c("&eЭтот титул выдаётся за достижение: " + row[3]));
                        return;
                    }
                    if (!plugin.getStore().takeRelics(p, cost)) {
                        p.sendMessage(ColorUtil.c("&cНужно " + cost + " Риликов ❖ для разблокировки титула!"));
                        return;
                    }
                    plugin.getStore().unlockTitle(p.getName(), tid);
                }
                plugin.getStore().setActiveTitleDisplay(p.getName(), row[1]);
                plugin.getHud().updatePlayerHud(p);
                ColorUtil.sound(p, "entity.player.levelup", 1f, 1.3f);
                p.sendMessage(ColorUtil.c("&#00E676✨ Вы установили титул: " + row[1]));
                openTitlesMenu(p);
                return;
            }
        }
    }

    private void handlePlaytimeReward(Player p, ItemStack clicked) {
        String rid = ItemFactory.getCustomId(clicked);
        if (rid == null || !rid.startsWith("pt_")) return;
        int reqSec = Integer.parseInt(rid.substring(3));
        if (plugin.getStore().hasClaimedPlayReward(p.getName(), rid)) return;
        if (plugin.getStore().getPlaySeconds(p.getName()) < reqSec) {
            p.sendMessage(ColorUtil.c("&cВы ещё не наиграли нужное время!"));
            return;
        }
        plugin.getStore().setClaimedPlayReward(p.getName(), rid);
        double coins = reqSec * 12.0;
        int relics = Math.max(10, reqSec / 90);
        plugin.getStore().addCoins(p.getName(), coins);
        plugin.getStore().addRelics(p.getName(), relics);
        plugin.getStore().save();
        ColorUtil.sound(p, "entity.player.levelup", 1f, 1.2f);
        p.sendMessage(ColorUtil.c("&#00E676⏰ Награда получена: &a+" + ColorUtil.fmtMoney(coins) + "$ &7и &b+" + relics + " Риликов ❖&7!"));
        openPlaytimeMenu(p);
    }

    private void handleBuyerSell(Player p, int slot, Material targetMat) {
        double mult = 1.0 + plugin.getRanks().getRank(p).tier * 0.15;
        Material[] mats = new Material[]{
                Material.COAL, Material.IRON_INGOT, Material.GOLD_INGOT, Material.DIAMOND,
                Material.EMERALD, Material.NETHERITE_INGOT, Material.LAPIS_LAZULI,
                Material.ROTTEN_FLESH, Material.BONE, Material.GUNPOWDER, Material.BLAZE_ROD,
                Material.WHEAT, Material.CARROT, Material.POTATO
        };
        int[] prices = new int[]{25, 70, 110, 350, 420, 2200, 35, 18, 22, 40, 95, 15, 15, 15};
        double earned = 0.0;
        int count = 0;
        ItemStack[] contents = p.getInventory().getContents();
        for (int s = 0; s < contents.length; s++) {
            ItemStack it = contents[s];
            if (it == null || it.hasItemMeta()) continue;
            for (int m = 0; m < mats.length; m++) {
                if (mats[m] == it.getType() && (slot == 4 || mats[m] == targetMat)) {
                    earned += it.getAmount() * prices[m] * mult;
                    count += it.getAmount();
                    p.getInventory().setItem(s, null);
                }
            }
        }
        if (count <= 0) {
            p.sendMessage(ColorUtil.c("&eВ инвентаре нет подходящих ресурсов для продажи!"));
            return;
        }
        plugin.getStore().addCoins(p.getName(), earned);
        plugin.getStore().save();
        ColorUtil.sound(p, "entity.experience_orb.pickup", 1f, 1.3f);
        p.sendMessage(ColorUtil.c("&#00E676💰 Скупщик купил &f" + count + " шт. &#00E676ресурсов за &a+" + ColorUtil.fmtMoney(earned) + "$&7!"));
    }

    private void handleBafovarClick(Player p, int slot) {
        ItemStack pot = null;
        int cost = 0;
        if (slot == 10) { pot = ItemFactory.potionKiller(1); cost = 14000; }
        else if (slot == 11) { pot = ItemFactory.potionMedic(1); cost = 11000; }
        else if (slot == 12) { pot = ItemFactory.potionFlash(1); cost = 9500; }
        else if (slot == 13) { pot = ItemFactory.potionAgent(1); cost = 13000; }
        else if (slot == 14) { pot = ItemFactory.potionChampagne(1); cost = 15000; }
        else if (slot == 15) { pot = ItemFactory.potionBurp(1); cost = 17000; }
        else if (slot == 16) { pot = ItemFactory.potionAcid(1); cost = 19000; }
        else if (slot == 19) { pot = ItemFactory.potionTurtle(1); cost = 16000; }
        else if (slot == 20) { pot = ItemFactory.potionVampire(1); cost = 18000; }

        if (pot != null) {
            if (!plugin.getStore().takeCoins(p.getName(), cost)) {
                p.sendMessage(ColorUtil.c("&cНедостаточно монет! Нужно: " + ColorUtil.fmtMoney(cost) + "$"));
                ColorUtil.sound(p, "entity.villager.no", 1f, 1f);
                return;
            }
            plugin.getStore().save();
            p.getInventory().addItem(pot);
            ColorUtil.sound(p, "block.brewing_stand.brew", 1f, 1.2f);
            p.sendMessage(ColorUtil.c("&#00E676🧪 Бафовар сварил для вас зелье за &a" + ColorUtil.fmtMoney(cost) + "$&7!"));
            openBafovarMenu(p);
            return;
        }
        if (slot == 22) {
            if (!plugin.getStore().takeCoins(p.getName(), 12000)) {
                p.sendMessage(ColorUtil.c("&cНужно 12 000$ для пакета «Боевой Баф»!"));
                return;
            }
            ColorUtil.addPot(p, "strength", "INCREASE_DAMAGE", 6000, 1);
            ColorUtil.addPot(p, "speed", "SPEED", 6000, 1);
            ColorUtil.addPot(p, "fire_resistance", "FIRE_RESISTANCE", 6000, 0);
            ColorUtil.addPot(p, "night_vision", "NIGHT_VISION", 6000, 0);
            ColorUtil.sound(p, "entity.player.levelup", 1f, 1.2f);
            p.sendMessage(ColorUtil.c("&#FF6D00⚔ Бафовар наложил на вас «Боевой Баф» на 5 минут!"));
            return;
        }
        if (slot == 23) {
            if (!plugin.getStore().takeCoins(p.getName(), 30000)) {
                p.sendMessage(ColorUtil.c("&cНужно 30 000$ для пакета «Королевский Баф»!"));
                return;
            }
            ColorUtil.addPot(p, "strength", "INCREASE_DAMAGE", 9600, 2);
            ColorUtil.addPot(p, "speed", "SPEED", 9600, 2);
            ColorUtil.addPot(p, "resistance", "DAMAGE_RESISTANCE", 9600, 0);
            ColorUtil.addPot(p, "fire_resistance", "FIRE_RESISTANCE", 9600, 0);
            ColorUtil.addPot(p, "absorption", "ABSORPTION", 9600, 2);
            ColorUtil.sound(p, "ui.toast.challenge_complete", 1f, 1.2f);
            p.sendMessage(ColorUtil.c("&#FFD740👑 Бафовар наложил «Королевский Баф» на 8 минут!"));
            return;
        }
        if (slot == 24) {
            if (!plugin.getStore().takeCoins(p.getName(), 8000)) {
                p.sendMessage(ColorUtil.c("&cНужно 8 000$ для пакета «Шахтёрский Баф»!"));
                return;
            }
            ColorUtil.addPot(p, "haste", "FAST_DIGGING", 12000, 2);
            ColorUtil.addPot(p, "speed", "SPEED", 12000, 2);
            ColorUtil.addPot(p, "night_vision", "NIGHT_VISION", 12000, 0);
            ColorUtil.sound(p, "entity.player.levelup", 1f, 1.3f);
            p.sendMessage(ColorUtil.c("&#00E5FF⛏ Бафовар наложил «Шахтёрский Баф» на 10 минут!"));
            return;
        }
        if (slot == 30) {
            if (!plugin.getStore().takeCoins(p.getName(), 5000)) {
                p.sendMessage(ColorUtil.c("&cНужно 5 000$ для стакования зелий!"));
                return;
            }
            int merged = stackPlayerPotions(p);
            if (merged <= 0) {
                plugin.getStore().addCoins(p.getName(), 5000);
                p.sendMessage(ColorUtil.c("&eВ инвентаре нет одинаковых одиночных зелий для объединения!"));
                return;
            }
            plugin.getStore().save();
            ColorUtil.sound(p, "block.brewing_stand.brew", 1f, 1.3f);
            p.sendMessage(ColorUtil.c("&#00E676🧪 Бафовар успешно объединил ваши зелья в стаки (до 8 шт.)!"));
            return;
        }
        if (slot == 32) {
            if (!plugin.getStore().takeCoins(p.getName(), 2500)) {
                p.sendMessage(ColorUtil.c("&cНужно 2 500$!"));
                return;
            }
            String[][] bads = new String[][]{
                    {"poison","POISON"}, {"wither","WITHER"}, {"blindness","BLINDNESS"},
                    {"weakness","WEAKNESS"}, {"slowness","SLOW"}, {"mining_fatigue","SLOW_DIGGING"}
            };
            for (String[] b : bads) {
                org.bukkit.potion.PotionEffectType t = ColorUtil.potType(b[0], b[1]);
                if (t != null) p.removePotionEffect(t);
            }
            ColorUtil.sound(p, "entity.generic.drink", 1f, 1.2f);
            p.sendMessage(ColorUtil.c("&#00E676🥛 Все негативные дебаффы сняты!"));
        }
    }

    private int stackPlayerPotions(Player p) {
        ItemStack[] inv = p.getInventory().getStorageContents();
        int merges = 0;
        for (int i = 0; i < inv.length; i++) {
            ItemStack a = inv[i];
            if (a == null || (a.getType() != Material.POTION && a.getType() != Material.SPLASH_POTION && a.getType() != Material.LINGERING_POTION)) continue;
            if (a.getAmount() >= 8) continue;
            for (int j = i + 1; j < inv.length; j++) {
                ItemStack b = inv[j];
                if (b == null || !a.isSimilar(b)) continue;
                int space = 8 - a.getAmount();
                if (space <= 0) break;
                int move = Math.min(space, b.getAmount());
                a.setAmount(a.getAmount() + move);
                if (b.getAmount() <= move) inv[j] = null;
                else b.setAmount(b.getAmount() - move);
                merges++;
            }
        }
        p.getInventory().setStorageContents(inv);
        return merges;
    }

    public void repairAllItems(Player p) {
        for (ItemStack it : p.getInventory().getContents()) repairSingle(it);
        for (ItemStack it : p.getInventory().getArmorContents()) repairSingle(it);
        repairSingle(p.getInventory().getItemInOffHand());
    }

    private void repairSingle(ItemStack it) {
        if (it == null || it.getType() == Material.AIR || !it.hasItemMeta()) return;
        ItemMeta m = it.getItemMeta();
        if (m instanceof Damageable) {
            ((Damageable) m).setDamage(0);
            it.setItemMeta(m);
        }
    }
}
