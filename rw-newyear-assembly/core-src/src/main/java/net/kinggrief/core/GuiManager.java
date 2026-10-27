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

    public static final org.bukkit.NamespacedKey SHOP_PRICE_KEY = new org.bukkit.NamespacedKey("kinggrief", "shop_price");

    private static final String[][] ALL_KITS = new String[][]{
            {"start",     "Кит Игрока",      "IRON_SWORD",           "900"},
            {"newyear",   "Новогодний Кит",  "SNOWBALL",             "3600"},
            {"dragon",    "Кит DRAGON (/free dragon)", "DRAGON_BREATH", "21600"},
            {"pegas",     "Кит PEGAS",       "FEATHER",              "21600"},
            {"cobra",     "Кит COBRA",       "SLIME_BALL",           "21600"},
            {"dracula",   "Кит DRACULA",     "NETHERITE_SWORD",      "21600"},
            {"bunny",     "Кит BUNNY",       "RABBIT_FOOT",          "21600"},
            {"rabbit",    "Кит RABBIT",      "GOLDEN_CARROT",        "21600"},
            {"vampire",   "Кит VAMPIRE",     "REDSTONE",             "21600"},
            {"tiger",     "Кит TIGER",       "BLAZE_POWDER",         "21600"},
            {"god",       "Кит GOD",         "ENCHANTED_GOLDEN_APPLE","21600"},
            {"imperator", "Кит IMPERATOR",   "GOLDEN_HELMET",        "21600"},
            {"king",      "Кит KING",        "NETHER_STAR",          "21600"},
            {"sponsor",   "Кит SPONSOR",     "BEACON",               "21600"},
            {"media",     "Кит MEDIA",       "AMETHYST_SHARD",       "21600"},
            {"dhelper",   "Кит D.HELPER",    "CYAN_SHULKER_BOX",     "21600"},
            {"dkp",       "Кит ДКП",         "YELLOW_SHULKER_BOX",   "18000"},
            {"dmoder",    "Кит D.MODER",     "LIGHT_BLUE_SHULKER_BOX","21600"},
            {"dstmoder",  "Кит D.ST.MODER",  "BLUE_SHULKER_BOX",     "21600"},
            {"dglmoder",  "Кит D.GLMODER",   "PURPLE_SHULKER_BOX",   "21600"},
            {"dmladmin",  "Кит D.ML.ADMIN",  "ORANGE_SHULKER_BOX",   "21600"},
            {"dadmin",    "Кит D.ADMIN",     "RED_SHULKER_BOX",      "21600"},
            {"opka",      "Кит OPKA",        "MAGENTA_SHULKER_BOX",  "14400"},
            {"downer",    "Кит D.OWNER",     "BLACK_SHULKER_BOX",    "10800"},
            {"kp",        "Кит Персонала КП","EMERALD",              "21600"},
            {"mlmoder",   "Кит МЛ. МОДЕРА",  "DIAMOND_SWORD",        "18000"},
            {"mladmin",   "Кит МЛ. АДМИНА",  "ORANGE_SHULKER_BOX",   "14400"},
            {"admin",     "Кит АДМИНА КП",   "RED_SHULKER_BOX",      "12000"},
            {"curator",   "Кит CURATOR",     "LIME_SHULKER_BOX",     "10800"},
            {"owner",     "Кит OWNER [ТОЛЬКО OWNER]", "COMMAND_BLOCK", "3600"}
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
                ColorUtil.c("&#FF1744&l❄ МЕНЮ KINGGRIEF FT×RW v5.9"));
        decorateFrame(inv);
        DataStore st = plugin.getStore();
        RankManager.RankDef r = plugin.getRanks().getRank(p);

        inv.setItem(4, ItemFactory.glowIcon(Material.PLAYER_HEAD, "&#FFD740&l👤 Профиль: &f" + p.getName(),
                "&7Ранг: " + plugin.getRanks().getAnimatedBadge(r, 0),
                "&7Монеты: &a" + ColorUtil.fmtMoney(st.getCoins(p.getName())) + "$",
                "&7Рилики: &b" + st.getRelics(p) + " ❖ &8| &7Снежинки: &b" + st.getSnowflakes(p.getName()) + " ❄",
                "&7Наиграно: &e" + ColorUtil.fmtTime(st.getPlaySeconds(p.getName()))));

        inv.setItem(10, ItemFactory.glowIcon(Material.COMPASS, "&#00E676&l⚡ РТП в Мир Выживания (/rtp)",
                "&7Единственный вход из мира спавна в", "&7обычный мир выживания, Дальний РТП и Ад!", "", "&#00E676▸ Нажми для выбора!"));
        inv.setItem(11, ItemFactory.glowIcon(Material.ENDER_PEARL, "&#80D8FF&l🧭 Все Варпы и Арены",
                "&7Авто-Шахта, Фармилка, Казино, 40-Остр. Паркур,", "&7PvP-Колизей, 8 Кабинок, Кейсы, Бафовар!", "", "&#80D8FF▸ Открыть /warps"));
        inv.setItem(12, ItemFactory.glowIcon(Material.EMERALD_BLOCK, "&#00E5FF&l🛒 Серверный Магазин (/shop)",
                "&78 категорий (64+ товара за Монеты $):", "&7Блоки, Еда/Перки, PvP FT×RW, Рейд, Элитры!", "", "&#00E5FF▸ Открыть /shop"));
        inv.setItem(13, ItemFactory.glowIcon(Material.ENDER_CHEST, "&#38BDF8&l📦 Киты Привилегий (/kits)",
                "&72 стака перок + Золотая морковь + Пласты!", "&cДоступ строго по вашей привилегии.", "", "&#38BDF8▸ Открыть /kits"));
        inv.setItem(14, ItemFactory.glowIcon(Material.RED_SHULKER_BOX, "&#FF4081&l🎁 Шалкер-Кейсы (11 шт.)",
                "&7Открывай кейсы на спавне или в меню!", "", "&#FF4081▸ Открыть /cases"));
        inv.setItem(15, ItemFactory.glowIcon(Material.NETHER_STAR, "&#FFD740&l👑 Донат и Привилегии",
                "&740 анимированных рангов от 19₽:", "&fkinggrief.vercel.app", "", "&#FFD740▸ Открыть /donate"));
        inv.setItem(16, ItemFactory.glowIcon(Material.DIAMOND, "&#FFEA00&l🎰 Гранд-Казино 777",
                "&7Слоты 777, Рулетка ×14, PvP-Монетка!", "&7Джекпот банка: &a" + ColorUtil.fmtMoney(casinoJackpotPool) + "$", "", "&#FFEA00▸ Открыть /casino"));

        inv.setItem(19, ItemFactory.icon(Material.EMERALD, "&#69F0AE&l💰 Скупщик [Прокачка до ×2.0]",
                "&7Продай руду, урожай и дроп мобов", "&7в 1 клик с прокачкой множителя!", "", "&#69F0AE▸ Открыть /buyer"));
        inv.setItem(20, ItemFactory.icon(Material.GOLD_INGOT, "&#FFAB40&l⚖ Аукцион Игроков",
                "&7Покупай и продавай редкие вещи!", "&7Продать предмет: &f/ah sell <цена>", "", "&#FFAB40▸ Открыть /ah"));
        inv.setItem(21, ItemFactory.glowIcon(Material.BLAZE_POWDER, "&#FF1744&l🔥 Боссы и Ивенты",
                "&7Боссы, Метеорит и Маяк Смерти!", "", "&#FF1744▸ Открыть /events"));
        inv.setItem(22, ItemFactory.glowIcon(Material.WITHER_SKELETON_SKULL, "&#D500F9&l🕵 Чёрный Рынок",
                "&7Редкие 3D товары за Монеты!", "", "&#D500F9▸ Открыть /bm"));
        inv.setItem(23, ItemFactory.glowIcon(Material.TOTEM_OF_UNDYING, "&#E040FB&l🔮 Магазин Риликов (/rshop)",
                "&7Blender 3D Шары, Талисманы, Элитры и FT-расходники!", "", "&#E040FB▸ Открыть /rshop"));
        inv.setItem(24, ItemFactory.icon(Material.PRISMARINE_CRYSTALS, "&#80D8FF&l👳 Обменник Джамшута",
                "&7Обменяй 3D Снежинки ❄ на Шары и Рилики!", "", "&#80D8FF▸ Открыть /quests"));
        inv.setItem(25, ItemFactory.icon(Material.SHIELD, "&#B388FF&l🛡 Кланы Сервера",
                "&7Рейтинг кланов, казна и клан-база!", "", "&#B388FF▸ Открыть /clan"));

        inv.setItem(28, ItemFactory.glowIcon(Material.TOTEM_OF_UNDYING, "&#FF1744&l🔮 17 3D Талисманов (/talismans)",
                "&7Все талисманы Крушителя, Зевса, Жнеца,", "&7Дракулы, Бездны и &#FF1744Талисман OWNER&7!"));
        inv.setItem(29, ItemFactory.icon(Material.IRON_PICKAXE, "&#AEEA00&l💼 Работы (/jobs)", "&7Шахтёр, Охотник, Дровосек, Воин!"));
        inv.setItem(30, ItemFactory.icon(Material.EXPERIENCE_BOTTLE, "&#40C4FF&l🎄 Боевой Пропуск (/bp)", "&7Награды за активность на сервере!"));
        inv.setItem(31, ItemFactory.icon(Material.SNOW_BLOCK, "&#80D8FF&l🏰 Телепорт на Спавн", "&7Вернуться в Яркое Зимнее Королевство!"));
        inv.setItem(32, ItemFactory.icon(Material.NAME_TAG, "&#FFD740&l✨ Титулы (/titles)", "&712 красивых титулов в чат и ТАБ!"));
        inv.setItem(33, ItemFactory.icon(Material.CLOCK, "&#00E676&l⏰ Награды за Время (/pt)", "&7Получай Рилики и Кейсы за онлайн!"));
        inv.setItem(34, ItemFactory.glowIcon(Material.NETHERITE_CHESTPLATE, "&#FFD740&l🛡 3D Броня, Луки и ТНТ (/armors)",
                "&76 кастомных сетов брони, 4 3D Лука,", "&76 видов Стрел и 6 видов Рейд-ТНТ!"));

        inv.setItem(37, ItemFactory.glowIcon(Material.NETHERITE_PICKAXE, "&#FF5722&l⛏ 12 Кирок и Молот 10×10 (/picks)",
                "&7Кирка Крушителя 3×3, Молот OWNER 10×10,", "&7Бур 5×5, Жильная, Коса Жнеца, Секира Тора!"));
        inv.setItem(38, ItemFactory.glowIcon(Material.BREWING_STAND, "&#FF9100&l⚗ Бафовар (/bafs)",
                "&7Свари кастомное зелье (макс 8 зелек за 5 000$)", "&7или купи готовые зелья и пакеты баффов!"));
        inv.setItem(39, ItemFactory.glowIcon(Material.NETHERITE_SCRAP, "&#E040FB&l⛓ Скины Трапок и Пластов (/skins)",
                "&7Витражное броне-стекло в Трапках 5×5×5",
                "&7+ 8 кастомных скинов на Пласты (/plastskins)!"));
        boolean fpsOn = plugin.getStore().isFpsBoost(p);
        inv.setItem(40, ItemFactory.glowIcon(fpsOn ? Material.LIME_DYE : Material.REDSTONE_TORCH,
                "&#00E676&l🚀 FPS-БУСТЕР (/fps) &8[" + (fpsOn ? "&aВКЛ" : "&eВЫКЛ") + "&8]",
                "&7Убирает лишние частицы и анимации для слабых ПК!",
                "&a+40..80 FPS &7на спавне и в замесах!", "", "&#00E676▸ Нажми для переключения"));
        inv.setItem(41, ItemFactory.glowIcon(Material.SKELETON_SKULL, "&#FF1744&l🎯 Охота за Головами (/bounty)",
                "&7Закажи награду за голову врага!"));
        inv.setItem(42, ItemFactory.glowIcon(Material.CHEST_MINECART, "&#FFD740&l🎁 Халява и Промокоды (/promo)",
                "&7Промокоды с сайта и бесплатные подарки!"));
        inv.setItem(43, ItemFactory.glowIcon(Material.NETHERITE_SWORD, "&#FF1744&l⚔ 12 Кастомных 3D Мечей (/swords)",
                "&7Катана Сакуры, Меч Зевса, Фламберг Феникса,",
                "&7Астральный Меч Бездны, Экскалибур Мидаса!"));

        if (plugin.getCurator() != null && plugin.getCurator().hasCuratorAccess(p)) {
            inv.setItem(47, ItemFactory.glowIcon(Material.EMERALD, "&#86EFAC&l👑 Панель Куратора (/curator)",
                    "&7Выдача ролей КП, Медиа и Донатов,",
                    "&7список состава КП/Медиа и Журнал выдач!", "", "&#86EFAC▸ Открыть /curator"));
        }
        if (plugin.getLp().hasLpAccess(p)) {
            inv.setItem(48, ItemFactory.glowIcon(Material.MAGENTA_SHULKER_BOX, "&#FF1744&l🎁 Раздачи, Квесты и Тех.Работы (/razdacha)",
                    "&7Раздача Риликов, Денег, Кейсов по таймеру,",
                    "&7Квесты всему серверу, /txt и /tex!", "", "&#FF1744▸ Открыть панель OWNER"));
            inv.setItem(49, ItemFactory.glowIcon(Material.COMMAND_BLOCK, "&#00E676&l⚡ LP EDITOR & ГРУППЫ (/lp)",
                    "&7Управление 40 группами, правами и Веб-Редактор!", "", "&#00E676▸ Нажми для открытия LP GUI"));
        }
        p.openInventory(inv);
    }

    // ================= 2. МЕНЮ ВЫБОРА РТП (/rtp + /rtp near) =================
    public void openRtpMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("rtp", "", 0), 27,
                ColorUtil.c("&#00E676&l⚡ ВЫБОР ТЕЛЕПОРТАЦИИ РТП"));
        decorateFrame(inv);
        RankManager.RankDef r = plugin.getRanks().getRank(p);
        boolean hasGod = r.weight >= 65;
        boolean hasKing = r.weight >= 90;

        inv.setItem(10, ItemFactory.glowIcon(Material.GRASS_BLOCK, "&#00E676&l🌲 Близкая Телепортация",
                "&7Мир выживания (&fworld&7, строго на сушу)",
                "&7Дистанция: &e300 – 1 200 блоков",
                "&7Доступ: &#00E676✔ Всем игрокам (PLAYER)",
                "", "&#00E676▸ Нажми для телепорта!"));

        inv.setItem(12, ItemFactory.glowIcon(Material.GOLDEN_BOOTS, "&#38BDF8&l⚡ Средняя Телепортация",
                "&7Мир выживания (&fworld&7, чистые земли)",
                "&7Дистанция: &e1 500 – 3 500 блоков",
                hasGod ? "&7Доступ: &#00E676✔ Открыто (GOD+)" : "&7Доступ: &#FF5252✖ Требуется привилегия &#FEF9C3&lGOD",
                "", hasGod ? "&#38BDF8▸ Нажми для телепорта!" : "&#FFD740Магазин: &fkinggrief.vercel.app"));

        inv.setItem(14, ItemFactory.glowIcon(Material.DIAMOND_BOOTS, "&#FDE047&l👑 Высокая (Дальняя) РТП",
                "&7Далёкие земли для неприступных баз!",
                "&7Дистанция: &e4 000 – 8 000 блоков",
                hasKing ? "&7Доступ: &#00E676✔ Открыто (KING+)" : "&7Доступ: &#FF5252✖ Требуется привилегия &#FDE047&lKING",
                "", hasKing ? "&#FDE047▸ Нажми для телепорта!" : "&#FFD740Магазин: &fkinggrief.vercel.app"));

        inv.setItem(16, ItemFactory.glowIcon(Material.RECOVERY_COMPASS, "&#FF5252&l🎯 РТП рядом с Игроками (/rtp near)",
                "&7Телепортация в &e55–110 блоках &7от",
                "&7случайного игрока в мире выживания!",
                hasKing ? "&7Доступ: &#00E676✔ Открыто (KING+)" : "&7Доступ: &#FF5252✖ Требуется привилегия &#FDE047&lKING",
                "", hasKing ? "&#FF5252▸ Нажми для охоты (/rtp near)!" : "&#FFD740Магазин: &fkinggrief.vercel.app"));

        inv.setItem(22, ItemFactory.glowIcon(Material.NETHERRACK, "&#FF9100&l🔥 Адский РТП (Нижний мир)",
                "&7Безопасная точка в Аду (&fworld_nether&7)",
                "&7Дистанция: &e180 – 850 блоков",
                "", "&#FF9100▸ Нажми для телепорта в Ад!"));
        p.openInventory(inv);
    }

    // ================= 3. ВАРПЫ СЕРВЕРА (/warps — v5.7 11 ЧИСТЫХ ВАРПОВ БЕЗ СТЕН!) =================
    public void openWarpsMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("warps", "", 0), 45,
                ColorUtil.c("&#80D8FF&l🧭 ВАРПЫ И АРЕНЫ СЕРВЕРА"));
        decorateFrame(inv);
        inv.setItem(10, ItemFactory.glowIcon(Material.DIAMOND_PICKAXE, "&#FF9100&l⛏ /warp mine — Авто-Шахта",
                "&7Кристальный карьер с алмазами, изумрудами", "&7и древними обломками! Авто-реген 90с.", "&a• Широкий мост и лестница!"));
        inv.setItem(11, ItemFactory.glowIcon(Material.ZOMBIE_HEAD, "&#AEEA00&l🧟 /warp farm — Фармилка Мобов",
                "&74 безопасные камеры с зимними мобами!", "&7Фарми Монеты, Опыт и Снежинки ❄!"));
        inv.setItem(12, ItemFactory.glowIcon(Material.GOLD_BLOCK, "&#FFD740&l🎰 /warp casino — Гранд-Казино 777",
                "&7Зал игровых автоматов 777, Рулетка ×14,", "&7Монетка между игроками и Колесо Риликов!"));
        inv.setItem(13, ItemFactory.glowIcon(Material.NETHERITE_SWORD, "&#FF1744&l⚔ /warp pvp — PvP-Колизей",
                "&7Огромная открытая арена без ледяных стен!", "&73 лестницы + Эндер-Сундуки и Наковальни!"));
        inv.setItem(14, ItemFactory.glowIcon(Material.RED_STAINED_GLASS, "&#FF5252&l🥊 /warp kabinki — Кабинки 1×1 и 2×2",
                "&76 кабинок 1×1 + 2 кабинки 2×2!", "&7Авто-закрытие + 8 сек анти-подсос лута!"));
        inv.setItem(15, ItemFactory.glowIcon(Material.FEATHER, "&#40C4FF&l🏃 /warp parkour — Небесный Паркур",
                "&740 парящих островов до высоты Y=131,", "&73 чекпоинта и приз +25 000$ + 15❖ + Титул!"));
        inv.setItem(16, ItemFactory.glowIcon(Material.RED_SHULKER_BOX, "&#FF4081&l🎁 /warp cases — 11 Шалкер-Кейсов",
                "&7Королевская терраса из 11 цветных кейсов!"));
        inv.setItem(20, ItemFactory.glowIcon(Material.TARGET, "&#FF5252&l🥊 /warp train — Манекены Урона",
                "&73 Незеритовых Манекена для замера критов и мечей!"));
        inv.setItem(21, ItemFactory.glowIcon(Material.SUNFLOWER, "&#FF4081&l🎡 /warp wheel — Колесо Фортуны",
                "&7Беспроигрышный прокрут за 5❄ или 15 000$!"));
        inv.setItem(22, ItemFactory.glowIcon(Material.ENCHANTING_TABLE, "&#E040FB&l🔮 /warp bafs — Бафовар и Чары",
                "&7Отдельный павильон чародея 30 лвл и Алхимика!"));
        inv.setItem(23, ItemFactory.glowIcon(Material.TOTEM_OF_UNDYING, "&#FFD740&l🏆 /warp top — Аллея Топеров",
                "&7Живые стенды ТОП-5 Богачей и ТОП-5 Киллеров!"));
        inv.setItem(24, ItemFactory.glowIcon(Material.PINK_SHULKER_BOX, "&#00E676&l🎅 Подарки Санты (/gifts)",
                "&7Найдено подарков на спавне: &e" + plugin.getStore().getFoundSpawnGiftCount(p.getName()) + "/10",
                "&7Супер-приз 10/10: &a+25 000$ + 25❖ + Донат-Кейс!"));
        inv.setItem(31, ItemFactory.icon(Material.SNOW_BLOCK, "&#80D8FF&l🏰 Центральный Спавн (/spawn)",
                "&7Главная площадь у 82-блоковой Новогодней Ёлки"));
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

    public boolean isKnownKit(String kitId) {
        if (kitId == null) return false;
        String k = kitId.toLowerCase(Locale.ROOT);
        for (String[] row : ALL_KITS) {
            if (row[0].equalsIgnoreCase(k)) return true;
        }
        return false;
    }

    public void tryClaimKit(Player p, String kitId) {
        tryClaimKitToTarget(p, kitId, p);
    }

    public void tryClaimKitToTarget(org.bukkit.command.CommandSender sender, String kitId, Player target) {
        if (target == null || !target.isOnline()) {
            sender.sendMessage(ColorUtil.c("&#FF1744✖ Игрок не найден в сети! Использование: &f/kit <кит> [ник]"));
            return;
        }
        String k = kitId.toLowerCase(Locale.ROOT);
        if (!isKnownKit(k)) {
            StringBuilder valid = new StringBuilder();
            for (String[] row : ALL_KITS) {
                if (valid.length() > 0) valid.append("&8, &f");
                valid.append(row[0]);
            }
            sender.sendMessage(ColorUtil.c("&#FF1744✖ Неизвестный кит &f" + k + "&#FF1744! Доступные киты: &f" + valid.toString()));
            return;
        }

        boolean fromConsole = !(sender instanceof Player);
        Player sp = fromConsole ? null : (Player) sender;
        RankManager.RankDef senderRank = fromConsole ? plugin.getRanks().byId("owner") : plugin.getRanks().getRank(sp);

        // СТРОЖАЙШИЙ ЗАКОН v5.7: Кит OWNER могут брать и выдавать СТРОГО ТОЛЬКО ОВНЕРЫ!
        if ("owner".equals(k)) {
            if (!fromConsole && (senderRank == null || !"owner".equalsIgnoreCase(senderRank.id))) {
                if (sp != null) ColorUtil.sound(sp, "entity.villager.no", 1.0f, 0.8f);
                sender.sendMessage(ColorUtil.c("&#FF1744✖ Кит &f/kit owner &#FF1744могут брать СТРОГО ТОЛЬКО владельцы (привилегия &#FF1744&lOWNER&#FF1744)!"));
                return;
            }
            if (target != null && !fromConsole && !"owner".equalsIgnoreCase(plugin.getRanks().getRank(target).id)) {
                sender.sendMessage(ColorUtil.c("&#FF1744✖ Кит &f/kit owner &#FF1744предназначен исключительно для владельцев (&#FF1744&lOWNER&#FF1744)!"));
                return;
            }
        }

        boolean isHighAdmin = fromConsole || (!"owner".equals(k) && senderRank != null && senderRank.weight >= 160);

        if (!isHighAdmin && sp != null) {
            if (!plugin.getRanks().canClaimKit(sp, k)) {
                RankManager.RankDef req = plugin.getRanks().byId(k);
                ColorUtil.sound(sp, "entity.villager.no", 1.0f, 1.0f);
                sp.sendMessage(ColorUtil.c("&#FF1744✖ Этот кит доступен ТОЛЬКО с привилегией " + req.shortLabel + "&#FF1744!"));
                sp.sendMessage(ColorUtil.c("&7Приобрести привилегию: &#FF4081https://kinggrief.vercel.app"));
                return;
            }
            long cdSec = 21600L;
            for (String[] row : ALL_KITS) {
                if (row[0].equals(k)) { cdSec = Long.parseLong(row[3]); break; }
            }
            long now = System.currentTimeMillis();
            long ready = plugin.getStore().getKitReadyAt(sp.getName(), k);
            if (now < ready) {
                sp.sendMessage(ColorUtil.c("&#FFD740⏳ Подождите ещё &f" + ColorUtil.fmtTime((ready - now) / 1000L) + " &#FFD740до следующего получения кита!"));
                return;
            }
            plugin.getStore().setKitReadyAt(sp.getName(), k, now + cdSec * 1000L);
            plugin.getStore().save();
        }

        ItemFactory.giveKitItems(target, k);
        ColorUtil.sound(target, "entity.player.levelup", 1.0f, 1.2f);
        if (sp != null && sp.getUniqueId().equals(target.getUniqueId())) {
            target.sendMessage(ColorUtil.c("&#00E676✔ Вы получили богатый набор &f/kit " + k + " &#00E676(включая шалкеры ресурсов)!"));
        } else {
            String giver = fromConsole ? "Сервер" : sp.getName();
            sender.sendMessage(ColorUtil.c("&#00E676✔ Вы успешно выдали набор &f/kit " + k + " &#00E676игроку &e" + target.getName() + "&#00E676!"));
            target.sendMessage(ColorUtil.c("&#00E676🎁 Вам выдан набор &f/kit " + k + " &#00E676от &e" + giver + "&#00E676!"));
        }
    }

    // ================= 5. ЧИСТЫЙ МАГАЗИН РИЛИКОВ REALLYWORLD (/shop) =================
    public void openRelicShop(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("shop", "", 0), 54,
                ColorUtil.c("&#00E5FF&l🔮 МАГАЗИН РИЛИКОВ [3D FT×RW]"));
        decorateFrame(inv);
        int bal = plugin.getStore().getRelics(p);
        inv.setItem(4, ItemFactory.glowIcon(Material.DIAMOND, "&#00E5FF&lВаш баланс: &f" + bal + " Риликов ❖",
                "&7Blender Cycles 3D Шары, Талисманы, Элитры и FT-предметы!",
                "&7Пополнить Рилики: &#FF4081kinggrief.vercel.app"));

        // Row 1: 7 Blender 3D Шаров
        inv.setItem(10, shopTag(ItemFactory.sharTitana(),    290));
        inv.setItem(11, shopTag(ItemFactory.sharAndromedy(), 260));
        inv.setItem(12, shopTag(ItemFactory.sharChimery(),   220));
        inv.setItem(13, shopTag(ItemFactory.sharApollona(),  270));
        inv.setItem(14, shopTag(ItemFactory.sharAida(),      280));
        inv.setItem(15, shopTag(ItemFactory.sharAresa(),     310));
        inv.setItem(16, shopTag(ItemFactory.sharSanty(),     350));

        // Row 2: 7 Blender 3D Талисманов
        inv.setItem(19, shopTag(ItemFactory.talKrushitelya(), 310));
        inv.setItem(20, shopTag(ItemFactory.talGrani(),       290));
        inv.setItem(21, shopTag(ItemFactory.talDedala(),      250));
        inv.setItem(22, shopTag(ItemFactory.talTritona(),     230));
        inv.setItem(23, shopTag(ItemFactory.talFeniksa(3),    390));
        inv.setItem(24, shopTag(ItemFactory.talEchidny(),     280));
        inv.setItem(25, shopTag(ItemFactory.talStuzhi(),      300));

        // Row 3: 6 Кастомных 3D Элитр с разными баффами + Шар Осириса!
        inv.setItem(28, shopTag(ItemFactory.elytraWind(),     220));
        inv.setItem(29, shopTag(ItemFactory.elytraPhoenix(),  270));
        inv.setItem(30, shopTag(ItemFactory.elytraVampire(),  290));
        inv.setItem(31, shopTag(ItemFactory.elytraThunder(),  310));
        inv.setItem(32, shopTag(ItemFactory.elytraTitan(),    360));
        inv.setItem(33, shopTag(ItemFactory.elytraSanta(),    420));
        inv.setItem(34, shopTag(ItemFactory.sharOsirisa(),    240));

        // Row 4: 3D Оружие, Инструменты и FT×RW Расходники
        inv.setItem(37, shopTag(ItemFactory.plast(8),          35));
        inv.setItem(38, shopTag(ItemFactory.trapka(4),         45));
        inv.setItem(39, shopTag(ItemFactory.dezorient(4),      40));
        inv.setItem(40, shopTag(ItemFactory.bozhyaAura(2),     50));
        inv.setItem(41, shopTag(ItemFactory.pickDjamshut(),   340));
        inv.setItem(42, shopTag(ItemFactory.swordDracula(),   320));
        inv.setItem(43, shopTag(ItemFactory.swordKrush(),     420));

        inv.setItem(47, ItemFactory.glowIcon(Material.NETHERITE_PICKAXE, "&#FF5722&l⛏ 12 Кирок и Молот 10×10 (/picks)",
                "&7Кирка Крушителя 3×3, Молот OWNER 10×10, Коса Жнеца!"));
        inv.setItem(48, ItemFactory.glowIcon(Material.TOTEM_OF_UNDYING, "&#FF1744&l🔮 Все 17 3D Талисманов (/talismans)",
                "&7Открой полный Алтарь Талисманов v5.9!"));
        inv.setItem(49, ItemFactory.icon(Material.ARROW, "&7« Назад в меню"));
        inv.setItem(50, ItemFactory.glowIcon(Material.NETHERITE_CHESTPLATE, "&#FFD740&l🛡 3D Броня ×2, Луки и ТНТ (/armors)",
                "&76 сетов брони с Закалкой ×2, Луки и ТНТ!"));
        inv.setItem(51, ItemFactory.glowIcon(Material.NETHERITE_SWORD, "&#FF1744&l⚔ Оружейная 12 3D Мечей (/swords)",
                "&7Открой все 12 кастомных 3D Мечей v5.9!"));
        p.openInventory(inv);
    }

    // ================= 5A. КУЗНИЦА 12 КАСТОМНЫХ 3D КИРОК, МОЛОТА OWNER 10×10 И АРТЕФАКТОВ (/picks) =================
    public void openPickaxesMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("picks", "", 0), 54,
                ColorUtil.c("&#FF5722&l⛏ 12 КИРОК, МОЛОТ 10×10 И ОРУЖИЕ"));
        decorateFrame(inv);
        int bal = plugin.getStore().getRelics(p);
        boolean isOwner = "owner".equalsIgnoreCase(plugin.getRanks().getRank(p).id);

        inv.setItem(4, ItemFactory.glowIcon(Material.NETHERITE_PICKAXE, "&#FFD740&lВаш баланс: &b" + bal + " Риликов ❖",
                "&7Все 12 кастомных 3D Кирок и Молотов v5.8!",
                "&7Подсказка: зажмите &eShift &7при копке для точного режима 1×1!"));

        // Row 1 & Row 2: ALL 12 CUSTOM 3D PICKAXES & HAMMERS!
        inv.setItem(10, shopTag(ItemFactory.pickDjamshut(), 320));
        inv.setItem(11, shopTag(ItemFactory.pickSpawner(),  260));
        inv.setItem(12, shopTag(ItemFactory.pickKrush(),    460));

        ItemStack ownerHammerDisplay = ItemFactory.hammerOwner();
        ItemMeta ohm = ownerHammerDisplay.getItemMeta();
        if (ohm != null) {
            List<String> ol = ohm.hasLore() ? new ArrayList<String>(ohm.getLore()) : new ArrayList<String>();
            ol.add("");
            ol.add(ColorUtil.c(isOwner ? "&#00E676&l👑 ДОСТУПНО БЕСПЛАТНО (ВЫ — OWNER)!" : "&#FF1744&l🔒 ЭКСКЛЮЗИВ СТРОГО ДЛЯ OWNER!"));
            ol.add(ColorUtil.c(isOwner ? "&#FFD740▸ Нажми, чтобы забрать Молот 10×10!" : "&7Не продаётся обычным игрокам."));
            ohm.setLore(ol);
            ownerHammerDisplay.setItemMeta(ohm);
        }
        inv.setItem(13, ownerHammerDisplay);

        inv.setItem(14, shopTag(ItemFactory.pickVein(),     360));
        inv.setItem(15, shopTag(ItemFactory.pickFrost(),    330));
        inv.setItem(16, shopTag(ItemFactory.pickMolten(),   370));

        inv.setItem(20, shopTag(ItemFactory.pickMagnet(),   390));
        inv.setItem(21, shopTag(ItemFactory.pickObsidian(), 440));
        inv.setItem(22, shopTag(ItemFactory.pickMidas(),    380));
        inv.setItem(23, shopTag(ItemFactory.pickTunnel(),   520));
        inv.setItem(24, shopTag(ItemFactory.pickMeteor(),   560));

        // Row 3: 4 NEW 3D MYTHIC WEAPONS & ARTIFACTS!
        inv.setItem(29, shopTag(ItemFactory.scytheReaper(), 480));
        inv.setItem(31, shopTag(ItemFactory.axeThor(),      470));
        inv.setItem(32, shopTag(ItemFactory.orbRecall(4),    95));
        inv.setItem(33, shopTag(ItemFactory.lootMagnet(),   350));

        inv.setItem(49, ItemFactory.icon(Material.ARROW, "&7« Назад в главное меню"));
        inv.setItem(50, ItemFactory.glowIcon(Material.NETHERITE_SWORD, "&#FF1744&l⚔ Открыть 12 3D Мечей (/swords)",
                "&7Перейти в Оружейную всех 12 кастомных мечей!"));
        p.openInventory(inv);
    }

    // ================= 5A-2. ОРУЖЕЙНАЯ 12 КАСТОМНЫХ 3D МЕЧЕЙ (/swords) =================
    public void openSwordsMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("swords", "", 0), 54,
                ColorUtil.c("&#FF1744&l⚔ ОРУЖЕЙНАЯ 12 3D МЕЧЕЙ (/swords)"));
        decorateFrame(inv);
        int bal = plugin.getStore().getRelics(p);
        boolean isOwner = "owner".equalsIgnoreCase(plugin.getRanks().getRank(p).id);

        inv.setItem(4, ItemFactory.glowIcon(Material.NETHERITE_SWORD, "&#FFD740&lВаш баланс: &b" + bal + " Риликов ❖",
                "&7Все 12 уникальных 3D Мечей KINGGRIEF v5.9!",
                "&7У каждого меча свой 3D-силуэт и боевая способность!"));

        inv.setItem(10, shopTag(ItemFactory.swordDracula(), 320));
        inv.setItem(11, shopTag(ItemFactory.swordKrush(),   420));

        ItemStack ownerSwordDisplay = ItemFactory.swordOwner();
        ItemMeta osm = ownerSwordDisplay.getItemMeta();
        if (osm != null) {
            List<String> ol = osm.hasLore() ? new ArrayList<String>(osm.getLore()) : new ArrayList<String>();
            ol.add("");
            ol.add(ColorUtil.c(isOwner ? "&#00E676&l👑 ДОСТУПНО БЕСПЛАТНО (ВЫ — OWNER)!" : "&#FF1744&l🔒 ЭКСКЛЮЗИВ СТРОГО ДЛЯ OWNER!"));
            ol.add(ColorUtil.c(isOwner ? "&#FFD740▸ Нажми, чтобы забрать Меч OWNER!" : "&7Не продаётся обычным игрокам."));
            osm.setLore(ol);
            ownerSwordDisplay.setItemMeta(osm);
        }
        inv.setItem(12, ownerSwordDisplay);

        inv.setItem(13, shopTag(ItemFactory.swordFrost(),   330));
        inv.setItem(14, shopTag(ItemFactory.swordZeus(),    450));
        inv.setItem(15, shopTag(ItemFactory.swordKatana(),  380));
        inv.setItem(16, shopTag(ItemFactory.swordWither(),  410));

        inv.setItem(20, shopTag(ItemFactory.swordVenom(),   340));
        inv.setItem(21, shopTag(ItemFactory.swordPhantom(), 390));
        inv.setItem(22, shopTag(ItemFactory.swordPhoenix(), 440));
        inv.setItem(23, shopTag(ItemFactory.swordVoid(),    490));
        inv.setItem(24, shopTag(ItemFactory.swordMidas(),   470));

        // Row 3: Mythic Heavy Arsenal
        inv.setItem(29, shopTag(ItemFactory.scytheReaper(),    480));
        inv.setItem(30, shopTag(ItemFactory.axeThor(),         470));
        inv.setItem(32, shopTag(ItemFactory.maceKrush(),       380));
        inv.setItem(33, shopTag(ItemFactory.tridentPoseidon(), 320));

        inv.setItem(48, ItemFactory.glowIcon(Material.NETHERITE_PICKAXE, "&#FF5722&l⛏ 12 3D Кирок и Молот (/picks)",
                "&7Перейти в Кузницу Кирок и Молотов!"));
        inv.setItem(49, ItemFactory.icon(Material.ARROW, "&7« Назад в главное меню"));
        inv.setItem(50, ItemFactory.glowIcon(Material.NETHERITE_CHESTPLATE, "&#FFD740&l🛡 3D Броня ×2 и Луки (/armors)",
                "&7Перейти к сетам брони и 3D лукам!"));
        p.openInventory(inv);
    }

    // ================= 5B. 8-КАТЕГОРИЙНЫЙ СЕРВЕРНЫЙ МАГАЗИН ЗА МОНЕТЫ (/shop) =================
    public void openCoinShopHub(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("cshop_hub", "", 0), 45,
                ColorUtil.c("&#00E5FF&l🛒 МАГАЗИН СЕРВЕРА (/shop)"));
        decorateFrame(inv);
        inv.setItem(4, ItemFactory.glowIcon(Material.EMERALD_BLOCK, "&#00E5FF&lВаш баланс: &a" + ColorUtil.fmtMoney(plugin.getStore().getCoins(p.getName())) + "$",
                "&7Выбери нужный отдел магазина ниже", "&7или открой Магазин за Рилики ❖!"));

        inv.setItem(10, ItemFactory.glowIcon(Material.QUARTZ_BLOCK, "&#38BDF8&l🧱 Строительные и Цветные Блоки",
                "&7Кварц, Цветной бетон, Лёд, Стекло, Дерево!", "", "&#00E676▸ Открыть отдел"));
        inv.setItem(11, ItemFactory.glowIcon(Material.GOLDEN_CARROT, "&#FB923C&l🥕 Еда, Перки и Фермерство",
                "&7Золотая морковь, Эндер-жемчуг, Гэплы, Хорусы!", "", "&#00E676▸ Открыть отдел"));
        inv.setItem(12, ItemFactory.glowIcon(Material.NETHERITE_SWORD, "&#FACC15&l⚔ Броня, Оружие и Тотемы",
                "&7Снаряжение, Тотемы, Фейерверки, Стрелы!", "", "&#00E676▸ Открыть отдел"));
        inv.setItem(13, ItemFactory.glowIcon(Material.TNT, "&#EF4444&l💣 Рейд, Обсидиан и Механизмы",
                "&7ТНТ, Кристаллы, Якоря, Поршни, Паутина!", "", "&#00E676▸ Открыть отдел"));
        inv.setItem(14, ItemFactory.glowIcon(Material.NETHERITE_SCRAP, "&#A855F7&l⛓ PvP Расходники FT × RW",
                "&7Пласты, Трапки, Дезориентация, Явная Пыль,", "&7Божья Аура, Огненный Смерч, Снежки Заморозки!", "", "&#00E676▸ Открыть отдел"));
        inv.setItem(15, ItemFactory.glowIcon(Material.ELYTRA, "&#00E5FF&l🪽 Лавка 3D Кастомных Элитр",
                "&76 видов Элитр с уникальными баффами!", "", "&#00E676▸ Открыть отдел"));
        inv.setItem(16, ItemFactory.glowIcon(Material.SPAWNER, "&#AEEA00&l🥚 Спавнера, Яйца и Опыт",
                "&7Рассадники, Яйца Джамшута, Божье Прикосновение!", "", "&#00E676▸ Открыть отдел"));

        inv.setItem(21, ItemFactory.glowIcon(Material.DIAMOND_ORE, "&#69F0AE&l🛡 Рудные Приваты и Слитки",
                "&7Угольный, Железный, Алмазный и Изумрудный приват!", "", "&#00E676▸ Открыть отдел"));
        inv.setItem(22, ItemFactory.glowIcon(Material.BREWING_STAND, "&#FF9100&l⚗ Лавка и Варка Бафовара (/bafs)",
                "&7Свари микс до 8 зелий за 5 000$ + готовые зелья!", "", "&#00E676▸ Открыть /bafs"));
        inv.setItem(23, ItemFactory.glowIcon(Material.TOTEM_OF_UNDYING, "&#E040FB&l🔮 Магазин за Рилики (/rshop)",
                "&73D Шары, Талисманы и Топовые Артефакты!", "", "&#00E676▸ Открыть /rshop"));
        inv.setItem(24, ItemFactory.glowIcon(Material.NETHERITE_PICKAXE, "&#FF5722&l⛏ 12 Легендарных Кирок (/picks)",
                "&7Кирка Крушителя 3×3, Молот 10×10, Бур 5×5!", "", "&#00E676▸ Открыть /picks"));
        inv.setItem(25, ItemFactory.glowIcon(Material.NETHERITE_SWORD, "&#FF1744&l⚔ 12 Кастомных 3D Мечей (/swords)",
                "&7Катана, Фламберг, Зевс, Бездна, Экскалибур!", "", "&#00E676▸ Открыть /swords"));

        inv.setItem(40, ItemFactory.icon(Material.ARROW, "&7« Назад в главное меню"));
        p.openInventory(inv);
    }

    public void openCoinShopCat(Player p, String cat) {
        String c = cat == null ? "blocks" : cat.toLowerCase(Locale.ROOT);
        String title = "&#00E5FF&l🛒 МАГАЗИН: " + c.toUpperCase(Locale.ROOT);
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("cshop_cat", c, 0), 45, ColorUtil.c(title));
        decorateFrame(inv);
        inv.setItem(4, ItemFactory.glowIcon(Material.GOLD_INGOT, "&#FFD740&lБаланс: &a" + ColorUtil.fmtMoney(plugin.getStore().getCoins(p.getName())) + "$",
                "&7Нажми на предмет для мгновенной покупки!"));

        List<ItemStack> items = new ArrayList<ItemStack>();
        List<Integer> costs = new ArrayList<Integer>();
        if ("pvp".equals(c)) {
            items.add(ItemFactory.plast(4));             costs.add(Integer.valueOf(8500));
            items.add(ItemFactory.trapka(2));            costs.add(Integer.valueOf(12000));
            items.add(ItemFactory.dezorient(2));         costs.add(Integer.valueOf(14000));
            items.add(ItemFactory.yavnayaPyl(2));        costs.add(Integer.valueOf(11000));
            items.add(ItemFactory.bozhyaAura(1));        costs.add(Integer.valueOf(18000));
            items.add(ItemFactory.ognennySmerch(2));     costs.add(Integer.valueOf(13500));
            items.add(ItemFactory.snezokZamorozki(8));   costs.add(Integer.valueOf(9000));
            items.add(ItemFactory.pautinnayaGranata(4)); costs.add(Integer.valueOf(10500));
            items.add(ItemFactory.bozhyaKara(2));        costs.add(Integer.valueOf(22000));
            items.add(ItemFactory.gravSfera(2));         costs.add(Integer.valueOf(19500));
            items.add(ItemFactory.snegZavesa(2));        costs.add(Integer.valueOf(16000));
        } else if ("food".equals(c)) {
            items.add(new ItemStack(Material.GOLDEN_CARROT, 64));          costs.add(Integer.valueOf(2500));
            items.add(new ItemStack(Material.ENDER_PEARL, 16));            costs.add(Integer.valueOf(3500));
            items.add(new ItemStack(Material.GOLDEN_APPLE, 16));           costs.add(Integer.valueOf(9500));
            items.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));  costs.add(Integer.valueOf(45000));
            items.add(new ItemStack(Material.CHORUS_FRUIT, 32));           costs.add(Integer.valueOf(4500));
            items.add(ItemFactory.nyMandarin(8));                          costs.add(Integer.valueOf(8000));
            items.add(new ItemStack(Material.COOKED_BEEF, 64));            costs.add(Integer.valueOf(1200));
            items.add(new ItemStack(Material.EXPERIENCE_BOTTLE, 64));      costs.add(Integer.valueOf(7500));
        } else if ("gear".equals(c)) {
            items.add(new ItemStack(Material.TOTEM_OF_UNDYING, 1));        costs.add(Integer.valueOf(15000));
            items.add(new ItemStack(Material.FIREWORK_ROCKET, 64));        costs.add(Integer.valueOf(3000));
            items.add(ItemFactory.arrowIce(32));                           costs.add(Integer.valueOf(9000));
            items.add(ItemFactory.arrowExplosive(16));                     costs.add(Integer.valueOf(16000));
            items.add(ItemFactory.arrowLightning(16));                     costs.add(Integer.valueOf(18000));
            items.add(ItemFactory.arrowVampire(24));                       costs.add(Integer.valueOf(14000));
            items.add(ItemFactory.arrowPoison(24));                        costs.add(Integer.valueOf(12000));
            items.add(ItemFactory.arrowAntiFly(16));                       costs.add(Integer.valueOf(22000));
            items.add(ItemFactory.bowIce());                               costs.add(Integer.valueOf(185000));
            items.add(ItemFactory.bowInferno());                           costs.add(Integer.valueOf(210000));
            items.add(ItemFactory.bowZeus());                              costs.add(Integer.valueOf(245000));
            items.add(ItemFactory.bowVampire());                           costs.add(Integer.valueOf(225000));
            items.add(ItemFactory.maceKrush());                            costs.add(Integer.valueOf(360000));
            items.add(ItemFactory.tridentPoseidon());                      costs.add(Integer.valueOf(295000));
        } else if ("raid".equals(c)) {
            items.add(new ItemStack(Material.OBSIDIAN, 64));               costs.add(Integer.valueOf(6000));
            items.add(new ItemStack(Material.TNT, 32));                    costs.add(Integer.valueOf(7500));
            items.add(ItemFactory.tntTier1(4));                            costs.add(Integer.valueOf(24000));
            items.add(ItemFactory.tntBlack(2));                            costs.add(Integer.valueOf(58000));
            items.add(ItemFactory.tntNuke(1));                             costs.add(Integer.valueOf(115000));
            items.add(ItemFactory.tntFlash(4));                            costs.add(Integer.valueOf(32000));
            items.add(ItemFactory.tntCryo(4));                             costs.add(Integer.valueOf(34000));
            items.add(ItemFactory.tntMiner(4));                            costs.add(Integer.valueOf(29000));
            items.add(new ItemStack(Material.END_CRYSTAL, 8));             costs.add(Integer.valueOf(14000));
            items.add(new ItemStack(Material.RESPAWN_ANCHOR, 4));          costs.add(Integer.valueOf(12000));
            items.add(new ItemStack(Material.COBWEB, 32));                 costs.add(Integer.valueOf(5000));
            items.add(new ItemStack(Material.STICKY_PISTON, 16));          costs.add(Integer.valueOf(4000));
            items.add(new ItemStack(Material.SLIME_BLOCK, 16));            costs.add(Integer.valueOf(4500));
        } else if ("elytra".equals(c)) {
            items.add(new ItemStack(Material.ELYTRA, 1));                  costs.add(Integer.valueOf(65000));
            items.add(ItemFactory.elytraWind());                           costs.add(Integer.valueOf(185000));
            items.add(ItemFactory.elytraPhoenix());                        costs.add(Integer.valueOf(240000));
            items.add(ItemFactory.elytraVampire());                        costs.add(Integer.valueOf(265000));
            items.add(ItemFactory.elytraThunder());                        costs.add(Integer.valueOf(290000));
            items.add(ItemFactory.elytraTitan());                          costs.add(Integer.valueOf(340000));
            items.add(ItemFactory.elytraSanta());                          costs.add(Integer.valueOf(420000));
            items.add(new ItemStack(Material.FIREWORK_ROCKET, 64));        costs.add(Integer.valueOf(3000));
        } else if ("spawners".equals(c)) {
            items.add(new ItemStack(Material.SPAWNER, 1));                 costs.add(Integer.valueOf(160000));
            items.add(ItemFactory.pickSpawner());                          costs.add(Integer.valueOf(140000));
            items.add(ItemFactory.mysteryEgg(1));                          costs.add(Integer.valueOf(75000));
            items.add(ItemFactory.xpOrb100(1));                            costs.add(Integer.valueOf(12000));
            items.add(new ItemStack(Material.HOPPER, 8));                  costs.add(Integer.valueOf(5000));
            items.add(new ItemStack(Material.CHEST, 32));                  costs.add(Integer.valueOf(2000));
        } else if ("ores".equals(c)) {
            items.add(ItemFactory.claimBlock(Material.COAL_ORE, 7, "&a🛡 Угольный Приват 15×15"));         costs.add(Integer.valueOf(5000));
            items.add(ItemFactory.claimBlock(Material.IRON_ORE, 12, "&#38BDF8🛡 Железный Приват 25×25"));  costs.add(Integer.valueOf(15000));
            items.add(ItemFactory.claimBlock(Material.DIAMOND_ORE, 20, "&#38BDF8💎 Алмазный Приват 41×41"));costs.add(Integer.valueOf(45000));
            items.add(ItemFactory.claimBlock(Material.EMERALD_ORE, 30, "&#00E676💚 Изумрудный Приват 61×61"));costs.add(Integer.valueOf(110000));
            items.add(new ItemStack(Material.DIAMOND, 16));                costs.add(Integer.valueOf(9000));
            items.add(new ItemStack(Material.EMERALD, 16));                costs.add(Integer.valueOf(9500));
            items.add(new ItemStack(Material.GOLD_INGOT, 32));             costs.add(Integer.valueOf(5500));
            items.add(new ItemStack(Material.IRON_INGOT, 32));             costs.add(Integer.valueOf(3500));
        } else {
            items.add(new ItemStack(Material.QUARTZ_BLOCK, 64));           costs.add(Integer.valueOf(2200));
            items.add(new ItemStack(Material.SNOW_BLOCK, 64));             costs.add(Integer.valueOf(1000));
            items.add(new ItemStack(Material.PACKED_ICE, 64));             costs.add(Integer.valueOf(2500));
            items.add(new ItemStack(Material.SEA_LANTERN, 32));            costs.add(Integer.valueOf(3500));
            items.add(new ItemStack(Material.WHITE_CONCRETE, 64));         costs.add(Integer.valueOf(1800));
            items.add(new ItemStack(Material.LIGHT_BLUE_CONCRETE, 64));    costs.add(Integer.valueOf(1800));
            items.add(new ItemStack(Material.RED_CONCRETE, 64));           costs.add(Integer.valueOf(1800));
            items.add(new ItemStack(Material.OAK_LOG, 64));                costs.add(Integer.valueOf(1600));
        }

        int[] slots = new int[]{10,11,12,13,14,15,16,19,20,21,22,23,24,25};
        for (int i = 0; i < items.size() && i < slots.length; i++) {
            ItemStack tagged = coinShopTag(items.get(i), costs.get(i).intValue());
            ItemMeta m = tagged.getItemMeta();
            if (m != null) {
                m.getPersistentDataContainer().set(SHOP_PRICE_KEY, org.bukkit.persistence.PersistentDataType.INTEGER, costs.get(i));
                tagged.setItemMeta(m);
            }
            inv.setItem(slots[i], tagged);
        }
        inv.setItem(40, ItemFactory.icon(Material.ARROW, "&7« Все отделы магазина (/shop)"));
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
            m.getPersistentDataContainer().set(SHOP_PRICE_KEY, org.bukkit.persistence.PersistentDataType.INTEGER, Integer.valueOf(relicCost));
            copy.setItemMeta(m);
        }
        return copy;
    }

    // ================= 6. АЛТАРЬ АЛХИМИКА И КУЗНЕЦА РВ (/enchants) =================
    public void openEnchantsMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("enchants", "", 0), 45,
                ColorUtil.c("&#E040FB&l🧪 АЛХИМИК И КУЗНЕЦ"));
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

    // ================= 6B. NPC БАФОВАР (/bafs, /bafovar): ВАРКА ДО 8 ЗЕЛИЙ ЗА 5 000$ + ЛАВКА ГОТОВЫХ ЗЕЛИЙ =================
    public void openBafovarMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("bafovar", "", 0), 54,
                ColorUtil.c("&#FF9100&l⚗ АЛХИМИК БАФОВАР [ВАРКА + ЛАВКА]"));
        decorateFrame(inv);
        inv.setItem(4, ItemFactory.glowIcon(Material.BREWING_STAND, "&#FF9100&l⚗ АЛХИМИК БАФОВАР — Варка & Зелья",
                "&7Ваш баланс: &a" + ColorUtil.fmtMoney(plugin.getStore().getCoins(p.getName())) + "$",
                "&f1) &eВари кастомное зелье (макс 8 зелек в 1 флакон) за &a5 000$&e!",
                "&f2) &bПокупай готовые кастомные зелья и стакай флаконы!"));

        inv.setItem(10, coinShopTag(ItemFactory.potionKiller(1),    14000));
        inv.setItem(11, coinShopTag(ItemFactory.potionMedic(1),     11000));
        inv.setItem(12, coinShopTag(ItemFactory.potionFlash(1),      9500));
        inv.setItem(13, coinShopTag(ItemFactory.potionAgent(1),     13000));
        inv.setItem(14, coinShopTag(ItemFactory.potionChampagne(1), 15000));
        inv.setItem(15, coinShopTag(ItemFactory.potionBurp(1),      17000));
        inv.setItem(16, coinShopTag(ItemFactory.potionAcid(1),      19000));
        inv.setItem(19, coinShopTag(ItemFactory.potionTurtle(1),    16000));
        inv.setItem(20, coinShopTag(ItemFactory.potionVampire(1),   18000));

        inv.setItem(29, ItemFactory.glowIcon(Material.DRAGON_BREATH, "&#FF9100&l⚗ СВАРИТЬ КАСТОМНОЕ ЗЕЛЬЕ (МАКС 8 ЗЕЛЕК — 5 000$!)",
                "&7Открой варочный котёл Бафовара:",
                "&f• Положи от &e1 до 8 любых зелий &fиз инвентаря",
                "&f• Бафовар смешает их все в &aОДИН мощный флакон&f!",
                "", "&#FFEA00Стоимость варки: &a5 000$", "&#00E676▸ Нажми, чтобы открыть Котёл Варки!"));

        inv.setItem(30, ItemFactory.glowIcon(Material.CAULDRON, "&#00E676&l🧪 СТАКНУТЬ ЗЕЛЬЯ В ИНВЕНТАРЕ (до x8 — 5 000$)",
                "&7Объединяет одинаковые зелья в вашем", "&7инвентаре в удобные стаки по &e8 штук&7!",
                "", "&#FFEA00Цена: &a5 000$", "&#00E676▸ Нажми, чтобы стакнуть все зелья!"));

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

        inv.setItem(32, ItemFactory.glowIcon(Material.MILK_BUCKET, "&f&l🥛 Очистить Негативные Дебаффы",
                "&7Мгновенно снимает Отравление, Иссушение,", "&7Слепоту, Слабость и Замедление!",
                "", "&#FFEA00Цена: &a2 500$", "&#00E676▸ Нажми для очищения!"));

        inv.setItem(49, ItemFactory.icon(Material.ARROW, "&7« Назад в меню"));
        p.openInventory(inv);
    }

    public static final int[] BAFOVAR_SLOTS = new int[]{10, 11, 12, 13, 14, 15, 16, 22};

    public void openBafovarBrewerMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("baf_brew", "", 0), 45,
                ColorUtil.c("&#FF9100&l⚗ КОТЁЛ БАФОВАРА (МАКС 8 ЗЕЛЕК)"));
        ItemStack orange = ItemFactory.icon(Material.ORANGE_STAINED_GLASS_PANE, " ");
        ItemStack black = ItemFactory.icon(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) inv.setItem(i, (i % 2 == 0) ? black : orange);
        for (int s : BAFOVAR_SLOTS) inv.setItem(s, null);

        inv.setItem(4, ItemFactory.glowIcon(Material.BREWING_STAND, "&#FFD740&l⚗ Положи от 1 до 8 любых зелий в слоты!",
                "&7Кликни по зельям в своём инвентаре или перетащи",
                "&7их в &e8 пустых слотов &7выше!",
                "&7Все эффекты объединятся в &a1 кастомное зелье&7!"));
        inv.setItem(30, ItemFactory.glowIcon(Material.LIME_CONCRETE, "&#00E676&l✔ СВАРИТЬ ПИТЬЕВОЕ ЗЕЛЬЕ ЗА 5 000$",
                "&7Объединяет все вложенные зелья (до 8 шт.)",
                "&7в один питьевой флакон &eМикс Бафовара&7!",
                "", "&#FFEA00Цена варки: &a5 000$"));
        inv.setItem(32, ItemFactory.glowIcon(Material.MAGENTA_CONCRETE, "&#E040FB&l💥 СВАРИТЬ ВЗРЫВНОЕ ЗЕЛЬЕ ЗА 5 000$",
                "&7Объединяет все вложенные зелья (до 8 шт.)",
                "&7в один взрывной флакон &dМикс Бафовара&7!",
                "", "&#FFEA00Цена варки: &a5 000$"));
        inv.setItem(40, ItemFactory.icon(Material.ARROW, "&7« Назад в лавку Бафовара"));
        p.openInventory(inv);
    }

    // ================= 6D. СКИНЫ ТРАПОК (СО СТЕКЛОМ!) И СКИНЫ ПЛАСТОВ (/skins, /plastskins) =================
    public static final String[][] TRAP_SKINS = new String[][]{
            {"classic", "&#B0BEC5&l⛓ Незеритовый Бастион [Стандарт]",   "NETHERITE_BLOCK", "0",     "0",   "&7Чёрное броне-стекло · Замедление I врагам"},
            {"ice",     "&#80D8FF&l❄ Ледяная Гробница Стужи",            "BLUE_ICE",        "25000", "35",  "&bГолубое броне-стекло · Мороз + Медлительность III!"},
            {"inferno", "&#FF6D00&l🔥 Адское Пекло Аида",                "MAGMA_BLOCK",     "35000", "45",  "&6Оранжевое стекло · Поджог 6 сек + Слабость I!"},
            {"toxic",   "&#76FF03&l☣ Кислотный Реактор Ехидны",          "EMERALD_BLOCK",   "45000", "60",  "&aЛаймовое стекло · Отравление II + Голод III!"},
            {"void",    "&#E040FB&l👁 Бездна Эндера",                    "CRYING_OBSIDIAN", "60000", "80",  "&dПурпурное стекло · Слепота + Утомление II!"},
            {"royal",   "&#FFD740&l👑 Императорская Золотая Клетка",     "GOLD_BLOCK",      "85000", "110", "&eЖёлтое стекло · Иссушение II + Свечение!"},
            {"blood",   "&#EF4444&l🩸 Кровавый Саркофаг Дракулы",        "REDSTONE_BLOCK",  "95000", "125", "&cКрасное стекло · Иссушение II + Медлительность II!"},
            {"owner",   "&#FF1744&l👑 &#FFD740&lБОЖЕСТВЕННАЯ ТРАПКА OWNER","BEACON",         "-1",    "-1",  "&#FF1744Иссушение III + Слепота врагам · Сила II вам!"}
    };

    public static final String[][] PLAST_SKINS = new String[][]{
            {"classic",  "&#A855F7&l🧱 Обсидиановый Монолит [Стандарт]", "OBSIDIAN",        "0",     "0",   "&7Фиолетовое броне-стекло в центре пласта"},
            {"ice",      "&#38BDF8&l❄ Крио-Щит Северной Вьюги",          "BLUE_ICE",        "20000", "30",  "&bГолубое стекло · Замораживает врагов рядом!"},
            {"magma",    "&#FB923C&l🔥 Вулканический Бастион",           "MAGMA_BLOCK",     "30000", "40",  "&6Оранжевое стекло · Огнестойкость вам + поджог врагам!"},
            {"toxic",    "&#22C55E&l☣ Изумрудный Био-Барьер",            "EMERALD_BLOCK",   "40000", "55",  "&aЛаймовое стекло · Отравление II врагам рядом!"},
            {"amethyst", "&#E879F9&l🔮 Аметистовый Резонатор",           "AMETHYST_BLOCK",  "50000", "70",  "&dПурпурное стекло · Скорость II и Прыгучесть II вам!"},
            {"royal",    "&#FFD740&l👑 Золотая Цитадель Мидаса",         "GOLD_BLOCK",      "70000", "95",  "&eЖёлтое стекло · +2 Золотых Сердца при установке!"},
            {"wither",   "&#9333EA&l💀 Тёмный Бастион Жнеца",            "NETHERITE_BLOCK", "85000", "115", "&cКрасное стекло · Иссушение II врагам рядом!"},
            {"owner",    "&#FF1744&l👑 &#FFD740&lБОЖЕСТВЕННЫЙ ПЛАСТ OWNER","BEACON",        "-1",    "-1",  "&#FFD740Реген II + Скорость III вам · Иссушение II врагам!"}
    };

    public void openTrapSkinsMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("skins", "", 0), 45,
                ColorUtil.c("&#E040FB&l⛓ СКИНЫ ТРАПОК [СТЕКЛО + ДЕБАФФЫ]"));
        decorateFrame(inv);
        String active = plugin.getStore().getSelectedTrapSkin(p.getName());
        boolean isOwner = "owner".equalsIgnoreCase(plugin.getRanks().getRank(p).id);

        inv.setItem(3, ItemFactory.glowIcon(Material.NETHERITE_SCRAP, "&#FF1744&l⛓ [ВКЛАДКА] Скины Трапок 5×5×5",
                "&a✔ Сейчас открыта эта вкладка!",
                "&7Все трапки оснащены нерушимыми витражными",
                "&7окнами 3×3 со всех сторон и сверху!"));
        inv.setItem(5, ItemFactory.glowIcon(Material.DRIED_KELP, "&#A855F7&l🧱 [ПЕРЕЙТИ] Скины Пластов 5×5×2 (/plastskins)",
                "&7Нажми, чтобы открыть 8 кастомных скинов",
                "&7для вашего боевого Пласта 5×5×2!", "",
                "&#00E676▸ Нажмите для переключения!"));

        int[] slots = new int[]{10, 11, 12, 13, 14, 15, 16, 22};
        for (int i = 0; i < TRAP_SKINS.length && i < slots.length; i++) {
            String[] sk = TRAP_SKINS[i];
            boolean ownerOnly = "owner".equalsIgnoreCase(sk[0]);
            boolean owned = ownerOnly ? isOwner : plugin.getStore().hasTrapSkin(p.getName(), sk[0]);
            boolean sel = sk[0].equalsIgnoreCase(active) && (!ownerOnly || isOwner);
            Material mat = ColorUtil.mat(sk[2], "OBSIDIAN");
            List<String> lore = new ArrayList<String>();
            lore.add("&8Скин Трапки 5×5×5 с витражным броне-стеклом");
            lore.add("");
            lore.add("&7Эффект: " + sk[5]);
            lore.add("");
            if (sel) {
                lore.add("&#00E676&l✔ АКТИВЕН СЕЙЧАС!");
            } else if (ownerOnly && !isOwner) {
                lore.add("&#FF1744&l🔒 ЭКСКЛЮЗИВ СТРОГО ДЛЯ OWNER!");
            } else if (owned) {
                lore.add("&#00E676✔ Разблокирован — Нажмите ЛКМ для выбора!");
            } else {
                lore.add("&#FFEA00ЛКМ — Купить за &a" + sk[3] + "$ Монет");
                lore.add("&#00E5FFПКМ — Купить за &b" + sk[4] + " Риликов ❖");
            }
            inv.setItem(slots[i], ItemFactory.make(mat, 1, sk[1], lore, sel || ownerOnly, "skin_" + sk[0], null, 0));
        }
        inv.setItem(40, ItemFactory.icon(Material.ARROW, "&7« Назад в меню"));
        p.openInventory(inv);
    }

    public void openPlastSkinsMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("plast_skins", "", 0), 45,
                ColorUtil.c("&#A855F7&l🧱 СКИНЫ ПЛАСТОВ 5×5×2 (/plastskins)"));
        decorateFrame(inv);
        String active = plugin.getStore().getSelectedPlastSkin(p.getName());
        boolean isOwner = "owner".equalsIgnoreCase(plugin.getRanks().getRank(p).id);

        inv.setItem(3, ItemFactory.glowIcon(Material.NETHERITE_SCRAP, "&#FF1744&l⛓ [ПЕРЕЙТИ] Скины Трапок 5×5×5 (/skins)",
                "&7Нажми, чтобы открыть скины для Трапки",
                "&7с бронированными стеклянными окнами!", "",
                "&#00E676▸ Нажмите для переключения!"));
        inv.setItem(5, ItemFactory.glowIcon(Material.DRIED_KELP, "&#A855F7&l🧱 [ВКЛАДКА] Скины Пластов 5×5×2",
                "&a✔ Сейчас открыта эта вкладка!",
                "&7Меняет блоки и витражное окно вашего Пласта",
                "&7+ даёт тактический бафф при установке!"));

        int[] slots = new int[]{10, 11, 12, 13, 14, 15, 16, 22};
        for (int i = 0; i < PLAST_SKINS.length && i < slots.length; i++) {
            String[] sk = PLAST_SKINS[i];
            boolean ownerOnly = "owner".equalsIgnoreCase(sk[0]);
            boolean owned = ownerOnly ? isOwner : plugin.getStore().hasPlastSkin(p.getName(), sk[0]);
            boolean sel = sk[0].equalsIgnoreCase(active) && (!ownerOnly || isOwner);
            Material mat = ColorUtil.mat(sk[2], "OBSIDIAN");
            List<String> lore = new ArrayList<String>();
            lore.add("&8Скин боевого Пласта 5×5×2 FT×RW");
            lore.add("");
            lore.add("&7Эффект: " + sk[5]);
            lore.add("");
            if (sel) {
                lore.add("&#00E676&l✔ АКТИВЕН СЕЙЧАС!");
            } else if (ownerOnly && !isOwner) {
                lore.add("&#FF1744&l🔒 ЭКСКЛЮЗИВ СТРОГО ДЛЯ OWNER!");
            } else if (owned) {
                lore.add("&#00E676✔ Разблокирован — Нажмите ЛКМ для выбора!");
            } else {
                lore.add("&#FFEA00ЛКМ — Купить за &a" + sk[3] + "$ Монет");
                lore.add("&#00E5FFПКМ — Купить за &b" + sk[4] + " Риликов ❖");
            }
            inv.setItem(slots[i], ItemFactory.make(mat, 1, sk[1], lore, sel || ownerOnly, "pskin_" + sk[0], null, 0));
        }
        inv.setItem(40, ItemFactory.icon(Material.ARROW, "&7« Назад в меню"));
        p.openInventory(inv);
    }

    public void openBountyMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("bounty", "", 0), 45,
                ColorUtil.c("&#FF1744&l🎯 ОХОТА ЗА ГОЛОВАМИ (/bounty)"));
        decorateFrame(inv);
        inv.setItem(4, ItemFactory.glowIcon(Material.SKELETON_SKULL, "&#FF1744&l🎯 Контракты на Головы Игроков",
                "&7Убей цель с контрактом — забери 100% награды!",
                "&7Назначить награду: &#FFD740/bounty add <ник> <сумма>"));
        Map<String, Double> all = plugin.getStore().getActiveBounties();
        int slot = 10;
        for (Map.Entry<String, Double> en : all.entrySet()) {
            if (slot >= 35) break;
            if (slot % 9 == 0 || slot % 9 == 8) slot++;
            if (slot % 9 == 0 || slot % 9 == 8) slot++;
            inv.setItem(slot++, ItemFactory.glowIcon(Material.PLAYER_HEAD, "&#FF5252🎯 Цель: &f" + en.getKey(),
                    "&7Награда за убийство: &a" + ColorUtil.fmtMoney(en.getValue().doubleValue()) + "$",
                    "&7Статус: " + (Bukkit.getPlayerExact(en.getKey()) != null ? "&#00E676В сети!" : "&7Не в сети")));
        }
        inv.setItem(40, ItemFactory.icon(Material.ARROW, "&7« Назад в меню"));
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

    // ================= 8. БОССЫ, АИРДРОПЫ И 4 МИРОВЫХ ИВЕНТА (/events) & ЧЁРНЫЙ РЫНОК (/bm) =================
    public void openEventsMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("events", "", 0), 45,
                ColorUtil.c("&#FF1744&l🔥 АКТИВНЫЕ БОССЫ И ИВЕНТЫ"));
        decorateFrame(inv);
        List<BossEventManager.ActiveBossEvent> active = plugin.getBossEvents().getActiveEvents();
        inv.setItem(4, ItemFactory.glowIcon(Material.BEACON, "&#FFD740&lРадар Мировых Ивентов KINGGRIEF",
                "&7Активных ивентов сейчас: &a" + active.size(),
                "&7Следующий авто-ивент через: &e" + ColorUtil.fmtTime(plugin.getBossEvents().getNextAutoSeconds()),
                "",
                "&fВиды ивентов (спавнятся строго на СУШЕ!):",
                " &#FF1744• Мифические Боссы &8(5 видов)",
                " &#FF9100• Военный АирДроп с парашютом",
                " &#FF5252• Звёздный Метеорит с рудами",
                " &#FFD740• Золотой Маяк Богатства (+$$$)",
                " &#40C4FF• Ледяная Цитадель Санты"));

        if (active.isEmpty()) {
            inv.setItem(22, ItemFactory.icon(Material.CLOCK, "&eСейчас нет активных ивентов",
                    "&7Новый Ивент появится на суше через &f" + ColorUtil.fmtTime(plugin.getBossEvents().getNextAutoSeconds())));
        } else {
            int[] slots = new int[]{19, 21, 23, 25};
            for (int i = 0; i < Math.min(slots.length, active.size()); i++) {
                BossEventManager.ActiveBossEvent ev = active.get(i);
                String statusLine;
                if (ev.bossDefeated) {
                    statusLine = "&#00E676🔓 Сокровищница Открыта!";
                } else if (ev.template.kind == BossEventManager.EventKind.BOSS) {
                    statusLine = "&#FF1744⚔ Босс Жив!";
                } else {
                    statusLine = "&#FFD740⏳ Замок откроется через " + ColorUtil.fmtTime(ev.unlockCountdownSec);
                }
                inv.setItem(slots[i], ItemFactory.glowIcon(ev.template.platformMat,
                        ev.template.displayName,
                        "&7Статус: " + statusLine,
                        "&7Координаты на суше (/rtp):",
                        " &#80D8FFX: " + ev.center.getBlockX() + "  Y: " + ev.center.getBlockY() + "  Z: " + ev.center.getBlockZ(),
                        "&7До исчезновения: &e" + ColorUtil.fmtTime(ev.secondsLeft)));
            }
        }

        // СТРОГО ТОЛЬКО ДЛЯ АДМИНОВ И ВЫШЕ (D.ADMIN+, ДКП, OPKA, D.OWNER, КП, CURATOR, TECH.ADMIN, OWNER)!
        if (plugin.getBossEvents().canAdminSpawnEvents(p)) {
            inv.setItem(36, ItemFactory.glowIcon(Material.BLAZE_SPAWN_EGG, "&#FF1744&l👹 [АДМИН+] Призвать Босса",
                    "&7Спавнит Босса в стильном Храмовом Колизее 17×17"));
            inv.setItem(37, ItemFactory.glowIcon(Material.RED_SHULKER_BOX, "&#FF9100&l🪂 [АДМИН+] Сбросить АирДроп",
                    "&7Бронированная площадка + 3D купол парашюта 11×11"));
            inv.setItem(38, ItemFactory.glowIcon(Material.MAGMA_BLOCK, "&#FF5252&l☄ [АДМИН+] Уронить Метеорит",
                    "&7Кратер с пиками аметиста, рудами и огненным хвостом"));
            inv.setItem(41, ItemFactory.glowIcon(Material.CAMPFIRE, "&#FF6D00&l🌋 [АДМИН+] Извержение Вулкана",
                    "&7Базальтовая кальдера с лавой, ТНТ и 3D Луками!"));
            inv.setItem(42, ItemFactory.glowIcon(Material.BEACON, "&#FFD740&l💎 [АДМИН+] Пантеон Маяка",
                    "&7Мраморно-золотой храм — раздаёт Монеты и Рилики!"));
            inv.setItem(43, ItemFactory.glowIcon(Material.BLUE_ICE, "&#40C4FF&l❄ [АДМИН+] Ледяная Цитадель",
                    "&7Готический ледяной дворец со шпилями Санты!"));
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
                ColorUtil.c("&#69F0AE&l💰 СКУПЩИК [ПРОКАЧКА МНОЖИТЕЛЯ]"));
        decorateFrame(inv);
        double lvlMult = plugin.getStore().getBuyerMultiplier(p.getName());
        double rankMult = 1.0 + plugin.getRanks().getRank(p).tier * 0.12;
        double mult = lvlMult * rankMult;
        inv.setItem(4, ItemFactory.glowIcon(Material.EMERALD_BLOCK, "&#00E676&l⚡ ПРОДАТЬ ВСЁ ИЗ ИНВЕНТАРЯ В 1 КЛИК!",
                "&7Множитель прокачки Скупщика: &#00E676×" + String.format(Locale.US, "%.2f", Double.valueOf(lvlMult)) + " &8(макс ×2.00)",
                "&7Общий множитель с рангом: &e×" + String.format(Locale.US, "%.2f", Double.valueOf(mult)),
                "&7Продано всего: &a" + ColorUtil.fmtMoney(plugin.getStore().getBuyerSoldTotal(p.getName())) + "$",
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
                "dragon", "pegas", "cobra", "dracula", "bunny", "rabbit", "vampire",
                "tiger", "god", "imperator", "king", "sponsor",
                "dhelper", "dkp", "dmoder", "dstmoder", "dglmoder", "dmladmin", "dadmin", "opka", "downer"
        };
        int[] slots = new int[]{
                10,11,12,13,14,15,16,
                19,20,21,22,23,24,25,
                28,29,30,31,32,33,34
        };
        for (int i = 0; i < topRanks.length && i < slots.length; i++) {
            RankManager.RankDef r = plugin.getRanks().byId(topRanks[i]);
            boolean isFreeDragon = "dragon".equals(r.id);
            inv.setItem(slots[i], ItemFactory.glowIcon(
                    i >= 12 ? Material.NETHER_STAR : (isFreeDragon ? Material.DRAGON_BREATH : Material.EMERALD),
                    r.shortLabel + (isFreeDragon ? " &8— &#00E676БЕСПЛАТНО (/free dragon)" : " &8— &#FFD740" + r.priceRub + "₽"),
                    "&7Домов: &e" + r.maxHomes + " &8| &7Приватов: &e" + r.maxClaims,
                    "&7Личный богатый кит: &f/kit " + r.kitName,
                    isFreeDragon ? "&#00E676▸ Нажми, чтобы получить DRAGON бесплатно!" : "&#00E676▸ Нажми для ссылки на покупку!"));
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
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("clans", "", 0), 54,
                ColorUtil.c("&#B388FF&l🛡 КЛАНОВАЯ СИСТЕМА СЕРВЕРА"));
        decorateFrame(inv);
        DataStore.Clan my = plugin.getStore().getClanOf(p.getName());
        if (my == null) {
            inv.setItem(4, ItemFactory.glowIcon(Material.SHIELD, "&#B388FF&lВы ещё не состоите в клане!",
                    "&7Создай свой клан командой: &#00E676/clan create <тег>",
                    "&7Стоимость создания: &a25 000$",
                    "&7Приглашения с кликабельными кнопками: &#00E676[Принять] &8/ &#FF5252[Отклонить]"));
        } else {
            String role = my.getRoleName(p.getName());
            inv.setItem(4, ItemFactory.glowIcon(Material.SHIELD,
                    my.color + "&l[" + my.tag + "] &8— &fКлан Ур. " + my.level,
                    "&7Ваша должность: " + role,
                    "&7Лидер: &f" + my.leader + " &8| &7Участников: &a" + my.members.size() + "/" + my.getMaxMembers(),
                    "&7Казна: &e" + ColorUtil.fmtMoney(my.bank) + "$ &8| &7Убийств: &c" + my.kills,
                    "&7Огонь по своим: " + (my.friendlyFire ? "&cВКЛЮЧЁН" : "&aОТКЛЮЧЁН")));
            inv.setItem(19, ItemFactory.glowIcon(Material.ENDER_CHEST, "&#00E5FF&l📦 Хранилище Клана (/clan storage)",
                    "&7Общий сейф клана на &e" + my.getStorageSize() + " слотов&7!", "", "&#00E676▸ Нажми, чтобы открыть"));
            inv.setItem(20, ItemFactory.glowIcon(Material.RED_BED, "&#00E676&l🏠 База Клана (/clan home)",
                    my.home != null ? "&7Телепортация на клановую базу" : "&cТочка базы не установлена (/clan sethome)", "", "&#00E676▸ Телепортироваться"));
            inv.setItem(21, ItemFactory.glowIcon(Material.GOLD_BLOCK, "&#FFD740&l💰 Внести 10 000$ в Казну",
                    "&7Баланс казны: &a" + ColorUtil.fmtMoney(my.bank) + "$", "", "&#00E676▸ Нажми, чтобы пополнить +10 000$"));
            inv.setItem(23, ItemFactory.glowIcon(Material.EXPERIENCE_BOTTLE, "&#E040FB&l⬆ Прокачать Уровень Клана (/clan upgrade)",
                    "&7Текущий уровень: &e" + my.level + "/10",
                    "&7Бонус за килл: &a+" + (my.level * 50) + "$ &8| &7Мест: &a" + my.getMaxMembers(),
                    my.level < 10 ? "&7Цена апгрейда: &e" + ColorUtil.fmtMoney(my.level * 50000.0) + "$ из казны" : "&#00E676МАКСИМАЛЬНЫЙ УРОВЕНЬ!"));
            inv.setItem(24, ItemFactory.glowIcon(Material.IRON_SWORD, "&#FF5252&l⚔ PvP внутри клана (/clan pvp)",
                    "&7Сейчас: " + (my.friendlyFire ? "&cВКЛ" : "&aВЫКЛ"), "", "&e▸ Нажми для переключения"));
            inv.setItem(25, ItemFactory.glowIcon(Material.RECOVERY_COMPASS, "&#40C4FF&l📍 Координаты Тиммейтов (/clan coords)",
                    "&7Показать координаты всех участников онлайн!"));
        }

        List<DataStore.Clan> all = new ArrayList<DataStore.Clan>(plugin.getStore().getAllClans());
        Collections.sort(all, new Comparator<DataStore.Clan>() {
            @Override
            public int compare(DataStore.Clan a, DataStore.Clan b) {
                if (b.kills != a.kills) return Integer.compare(b.kills, a.kills);
                return Double.compare(b.bank, a.bank);
            }
        });
        int[] slots = new int[]{10,11,12,13,14,15,16};
        for (int i = 0; i < Math.min(slots.length, all.size()); i++) {
            DataStore.Clan c = all.get(i);
            inv.setItem(slots[i], ItemFactory.glowIcon(Material.DRAGON_HEAD,
                    "&#FFD740#" + (i + 1) + " " + c.color + "[" + c.tag + "] &7(Ур." + c.level + ")",
                    "&7Лидер: &f" + c.leader, "&7Участников: &a" + c.members.size() + "/" + c.getMaxMembers(),
                    "&7Убийств: &c" + c.kills + " &8| &7Казна: &e" + ColorUtil.fmtMoney(c.bank) + "$"));
        }
        inv.setItem(49, ItemFactory.icon(Material.ARROW, "&7« Назад в меню"));
        p.openInventory(inv);
    }

    public void openClanStorage(Player p, DataStore.Clan clan) {
        if (clan == null) return;
        Inventory inv = plugin.getStore().getOrCreateClanStorage(clan);
        p.openInventory(inv);
    }

    // ================= 12. АЛТАРЬ 17 КАСТОМНЫХ 3D ТАЛИСМАНОВ (/talismans) =================
    public void openTalismansMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("talismans", "", 0), 54,
                ColorUtil.c("&#FF1744&l🔮 АЛТАРЬ 3D ТАЛИСМАНОВ [v5.7]"));
        decorateFrame(inv);
        int bal = plugin.getStore().getRelics(p);
        boolean isOwner = "owner".equalsIgnoreCase(plugin.getRanks().getRank(p).id);
        inv.setItem(4, ItemFactory.glowIcon(Material.TOTEM_OF_UNDYING, "&#FF1744&lВаш баланс: &f" + bal + " Риликов ❖",
                "&7Все 3D Талисманы дают пассивные эффекты в левой руке",
                "&7и спасают от смерти с уникальным взрывом!",
                "&8Предметы в левой руке откалиброваны под чистый PvP-обзор!"));

        ItemStack[] tals = new ItemStack[]{
                ItemFactory.talKrushitelya(), ItemFactory.talGrani(), ItemFactory.talDedala(),
                ItemFactory.talTritona(),     ItemFactory.talFeniksa(3), ItemFactory.talEchidny(),
                ItemFactory.talStuzhi(),      ItemFactory.talKaratelya(), ItemFactory.talZeusa(),
                ItemFactory.talDraculy(),     ItemFactory.talKolossa(),   ItemFactory.talZhneca(),
                ItemFactory.talSevera(),      ItemFactory.talBezdn(2),    ItemFactory.talImperatora(),
                ItemFactory.totemRw()
        };
        int[] costs = new int[]{310, 290, 250, 230, 390, 280, 300, 340, 360, 330, 350, 370, 310, 440, 410, 190};
        int[] slots = new int[]{10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 29,30};
        for (int i = 0; i < tals.length && i < slots.length; i++) {
            ItemStack tagged = shopTag(tals[i], costs[i]);
            ItemMeta m = tagged.getItemMeta();
            if (m != null) {
                m.getPersistentDataContainer().set(SHOP_PRICE_KEY, org.bukkit.persistence.PersistentDataType.INTEGER, Integer.valueOf(costs[i]));
                tagged.setItemMeta(m);
            }
            inv.setItem(slots[i], tagged);
        }

        // Слот 32: ЭКСКЛЮЗИВНЫЙ ТАЛИСМАН OWNER (СТРОГО ТОЛЬКО ДЛЯ OWNER!)
        ItemStack ownerTal = ItemFactory.talOwner(5);
        ItemMeta om = ownerTal.getItemMeta();
        if (om != null) {
            List<String> l = om.hasLore() ? new ArrayList<String>(om.getLore()) : new ArrayList<String>();
            l.add("");
            if (isOwner) {
                l.add(ColorUtil.c("&#00E676✔ ДОСТУПНО ВЛАДЕЛЬЦУ (БЕСПЛАТНО)!"));
                l.add(ColorUtil.c("&#00E676▸ Нажми, чтобы получить Талисман OWNER!"));
            } else {
                l.add(ColorUtil.c("&#FF1744✖ СТРОГО ТОЛЬКО ДЛЯ ПРИВИЛЕГИИ OWNER!"));
            }
            om.setLore(l);
            ownerTal.setItemMeta(om);
        }
        inv.setItem(32, ownerTal);
        inv.setItem(49, ItemFactory.icon(Material.ARROW, "&7« Назад в меню"));
        p.openInventory(inv);
    }

    // ================= 13. ОРУЖЕЙНАЯ 3D БРОНИ, ЛУКОВ И КАСТОМНЫХ ТНТ (/armors) =================
    public void openArmorsMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("armors", "", 0), 54,
                ColorUtil.c("&#FFD740&l🛡 3D БРОНЯ, ЛУКИ И ТНТ [v5.7]"));
        decorateFrame(inv);
        int bal = plugin.getStore().getRelics(p);
        boolean isOwner = "owner".equalsIgnoreCase(plugin.getRanks().getRank(p).id);
        inv.setItem(4, ItemFactory.glowIcon(Material.NETHERITE_CHESTPLATE, "&#FFD740&lВаш баланс: &f" + bal + " Риликов ❖",
                "&7Полный сет из 4 вещей даёт мощный пассивный бонус сета!",
                "&7Нажми на нагрудник сета, чтобы купить СРАЗУ ПОЛНЫЙ КОМПЛЕКТ (4 вещи)!"));

        String[][] sets = new String[][]{
                {"krush",   "&#FF1744&l🔥 Полный Сет Крушителя (4 предмета)", "590", "&7Бонус 4/4: &cСила III + Сопротивление I + Огнестойкость"},
                {"dracula", "&#D500F9&l🩸 Полный Сет Дракулы (4 предмета)",   "540", "&7Бонус 4/4: &dВампиризм + Скорость II + Ночное зрение"},
                {"frost",   "&#40C4FF&l❄ Полный Сет Стужи (4 предмета)",     "520", "&7Бонус 4/4: &bАура льда + Защита от огня + Скорость II"},
                {"zeus",    "&#FFD740&l⚡ Полный Сет Зевса (4 предмета)",     "580", "&7Бонус 4/4: &eСкорость III + Спешка II + Анти-отдача"},
                {"phantom", "&#B388FF&l👻 Полный Сет Фантома (4 предмета)",   "550", "&7Бонус 4/4: &dНевидимость + Прыгучесть II + Плавное падение"}
        };
        int[] setSlots = new int[]{10,11,12,13,14};
        for (int i = 0; i < sets.length; i++) {
            String sid = sets[i][0];
            int cost = Integer.parseInt(sets[i][2]);
            ItemStack chest = ItemFactory.armorPiece(sid, 1);
            ItemMeta m = chest.getItemMeta();
            if (m != null) {
                m.setDisplayName(ColorUtil.c(sets[i][1]));
                m.setLore(Arrays.asList(
                        ColorUtil.c(sets[i][3]),
                        ColorUtil.c("&7В комплекте: &fШлем + Нагрудник + Поножи + Ботинки (Защита V)"),
                        "",
                        ColorUtil.c("&#00E5FFЦена комплекта: &f" + cost + " Риликов ❖"),
                        ColorUtil.c("&#00E676▸ Нажми, чтобы получить весь сет!")));
                m.getPersistentDataContainer().set(ItemFactory.ITEM_TAG_KEY, org.bukkit.persistence.PersistentDataType.STRING, "armorset_" + sid);
                m.getPersistentDataContainer().set(SHOP_PRICE_KEY, org.bukkit.persistence.PersistentDataType.INTEGER, Integer.valueOf(cost));
                chest.setItemMeta(m);
            }
            inv.setItem(setSlots[i], chest);
        }

        // Слот 16: Сет OWNER (СТРОГО ТОЛЬКО ДЛЯ OWNER!)
        ItemStack ownerChest = ItemFactory.armorPiece("owner", 1);
        ItemMeta ocm = ownerChest.getItemMeta();
        if (ocm != null) {
            ocm.setDisplayName(ColorUtil.c("&#FF1744&l👑 Полный Сет OWNER [4 предмета]"));
            ocm.setLore(Arrays.asList(
                    ColorUtil.c("&7Бонус 4/4: &cСила IV + Сопр. II + Регенерация II + +20 HP"),
                    "",
                    ColorUtil.c(isOwner ? "&#00E676✔ ДОСТУПНО ВЛАДЕЛЬЦУ (БЕСПЛАТНО)!" : "&#FF1744✖ СТРОГО ТОЛЬКО ДЛЯ ПРИВИЛЕГИИ OWNER!")));
                ocm.getPersistentDataContainer().set(ItemFactory.ITEM_TAG_KEY, org.bukkit.persistence.PersistentDataType.STRING, "armorset_owner");
            ownerChest.setItemMeta(ocm);
        }
        inv.setItem(16, ownerChest);

        // Ряд 2: 4 Анимированных 3D Лука + Булава + Трезубец + Меч OWNER
        ItemStack[] weps = new ItemStack[]{
                ItemFactory.bowIce(), ItemFactory.bowInferno(), ItemFactory.bowZeus(),
                ItemFactory.bowVampire(), ItemFactory.maceKrush(), ItemFactory.tridentPoseidon()
        };
        int[] wCosts = new int[]{240, 260, 290, 270, 380, 320};
        int[] wSlots = new int[]{19,20,21,22,23,24};
        for (int i = 0; i < weps.length; i++) {
            ItemStack tagged = shopTag(weps[i], wCosts[i]);
            ItemMeta m = tagged.getItemMeta();
            if (m != null) {
                m.getPersistentDataContainer().set(SHOP_PRICE_KEY, org.bukkit.persistence.PersistentDataType.INTEGER, Integer.valueOf(wCosts[i]));
                tagged.setItemMeta(m);
            }
            inv.setItem(wSlots[i], tagged);
        }
        ItemStack owSword = ItemFactory.swordOwner();
        ItemMeta osm = owSword.getItemMeta();
        if (osm != null) {
            List<String> l = osm.hasLore() ? new ArrayList<String>(osm.getLore()) : new ArrayList<String>();
            l.add("");
            l.add(ColorUtil.c(isOwner ? "&#00E676✔ ДОСТУПНО ВЛАДЕЛЬЦУ (БЕСПЛАТНО)!" : "&#FF1744✖ СТРОГО ТОЛЬКО ДЛЯ ПРИВИЛЕГИИ OWNER!"));
            osm.setLore(l);
            owSword.setItemMeta(osm);
        }
        inv.setItem(25, owSword);

        // Ряд 3: 6 Кастомных 3D ТНТ
        ItemStack[] tnts = new ItemStack[]{
                ItemFactory.tntTier1(4), ItemFactory.tntBlack(2), ItemFactory.tntNuke(1),
                ItemFactory.tntFlash(4), ItemFactory.tntCryo(4),  ItemFactory.tntMiner(4)
        };
        int[] tCosts = new int[]{35, 75, 130, 45, 45, 40};
        int[] tSlots = new int[]{28,29,30,31,32,33};
        for (int i = 0; i < tnts.length; i++) {
            ItemStack tagged = shopTag(tnts[i], tCosts[i]);
            ItemMeta m = tagged.getItemMeta();
            if (m != null) {
                m.getPersistentDataContainer().set(SHOP_PRICE_KEY, org.bukkit.persistence.PersistentDataType.INTEGER, Integer.valueOf(tCosts[i]));
                tagged.setItemMeta(m);
            }
            inv.setItem(tSlots[i], tagged);
        }

        inv.setItem(49, ItemFactory.icon(Material.ARROW, "&7« Назад в меню"));
        p.openInventory(inv);
    }

    public void openLpGroupsGui(Player p, int page) {
        if (!plugin.getLp().hasLpAccess(p)) {
            p.closeInventory();
            p.sendMessage(ColorUtil.c("&#FF1744✖ Доступ к LP Editor разрешён СТРОГО только владельцу (OWNER)!"));
            return;
        }
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
        if ("trash".equals(h.menuType) || "clan_storage".equals(h.menuType)) {
            return;
        }
        Player p = (Player) e.getWhoClicked();
        if ("baf_brew".equals(h.menuType)) {
            handleBafovarBrewerClick(e, p, top);
            return;
        }
        e.setCancelled(true);
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(top)) return;

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        int slot = e.getSlot();
        String type = h.menuType;

        if ("main".equals(type)) {
            if (slot == 10) openRtpMenu(p);
            else if (slot == 11) openWarpsMenu(p);
            else if (slot == 12) openCoinShopHub(p);
            else if (slot == 13) openKitsMenu(p, 0);
            else if (slot == 14) openCasesMenu(p);
            else if (slot == 15) openDonateMenu(p);
            else if (slot == 16) openCasinoMenu(p);
            else if (slot == 19) openBuyerMenu(p);
            else if (slot == 20) openAuctionMenu(p, 0);
            else if (slot == 21) openEventsMenu(p);
            else if (slot == 22) openBlackMarket(p);
            else if (slot == 23) openRelicShop(p);
            else if (slot == 24) openDjamshutMenu(p);
            else if (slot == 25) openClanMenu(p);
            else if (slot == 28) openTalismansMenu(p);
            else if (slot == 29) openJobsMenu(p);
            else if (slot == 30) openBpMenu(p);
            else if (slot == 31) { p.closeInventory(); p.teleport(plugin.getSpawnEngine().getSpawnArrival()); }
            else if (slot == 32) openTitlesMenu(p);
            else if (slot == 33) openPlaytimeMenu(p);
            else if (slot == 34) openArmorsMenu(p);
            else if (slot == 37) openPickaxesMenu(p);
            else if (slot == 38) openBafovarMenu(p);
            else if (slot == 39) openTrapSkinsMenu(p);
            else if (slot == 40) { p.performCommand("fps"); openMainMenu(p); }
            else if (slot == 41) openBountyMenu(p);
            else if (slot == 42) openFreebieMenu(p);
            else if (slot == 43) openSwordsMenu(p);
            else if (slot == 47 && plugin.getCurator() != null && plugin.getCurator().hasCuratorAccess(p)) plugin.getCurator().openMainCuratorGui(p, "");
            else if (slot == 48 && plugin.getLp().hasLpAccess(p)) { p.closeInventory(); plugin.getOwnerGiveaway().openRazdachaMainGui(p); }
            else if (slot == 49 && plugin.getLp().hasLpAccess(p)) openLpGroupsGui(p, 0);
        } else if ("cshop_hub".equals(type)) {
            if (slot == 40) { openMainMenu(p); return; }
            if (slot == 10) openCoinShopCat(p, "blocks");
            else if (slot == 11) openCoinShopCat(p, "food");
            else if (slot == 12) openCoinShopCat(p, "gear");
            else if (slot == 13) openCoinShopCat(p, "raid");
            else if (slot == 14) openCoinShopCat(p, "pvp");
            else if (slot == 15) openCoinShopCat(p, "elytra");
            else if (slot == 16) openCoinShopCat(p, "spawners");
            else if (slot == 21) openCoinShopCat(p, "ores");
            else if (slot == 22) openBafovarMenu(p);
            else if (slot == 23) openRelicShop(p);
            else if (slot == 24) openPickaxesMenu(p);
            else if (slot == 25) openSwordsMenu(p);
        } else if ("cshop_cat".equals(type)) {
            if (slot == 40) { openCoinShopHub(p); return; }
            handleCategoryCoinPurchase(p, clicked, h.extra);
        } else if ("skins".equals(type)) {
            if (slot == 40 || slot == 31) { openMainMenu(p); return; }
            if (slot == 5) { openPlastSkinsMenu(p); return; }
            handleTrapSkinClick(p, clicked, e.getClick());
        } else if ("plast_skins".equals(type)) {
            if (slot == 40) { openMainMenu(p); return; }
            if (slot == 3) { openTrapSkinsMenu(p); return; }
            handlePlastSkinClick(p, clicked, e.getClick());
        } else if ("bounty".equals(type)) {
            if (slot == 40) openMainMenu(p);
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
            if (slot == 10) plugin.getSpawnEngine().performRtp(p, "close");
            else if (slot == 12) plugin.getSpawnEngine().performRtp(p, "medium");
            else if (slot == 14) plugin.getSpawnEngine().performRtp(p, "high");
            else if (slot == 16) plugin.getSpawnEngine().performRtp(p, "near");
            else if (slot == 22) plugin.getSpawnEngine().performRtp(p, "nether");
        } else if ("warps".equals(type)) {
            if (slot == 40) { openMainMenu(p); return; }
            if (slot == 24) { p.closeInventory(); p.performCommand("gifts"); return; }
            String w = slot == 10 ? "mine" : slot == 11 ? "farm" : slot == 12 ? "casino"
                    : slot == 13 ? "pvp" : slot == 14 ? "kabinki" : slot == 15 ? "parkour"
                    : slot == 16 ? "cases" : slot == 20 ? "train" : slot == 21 ? "wheel"
                    : slot == 22 ? "bafs" : slot == 23 ? "top" : slot == 31 ? "spawn" : null;
            if (w != null) {
                p.closeInventory();
                plugin.getStore().setBack(p, p.getLocation());
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
            if (slot == 47) { openPickaxesMenu(p); return; }
            if (slot == 48) { openTalismansMenu(p); return; }
            if (slot == 49) { openMainMenu(p); return; }
            if (slot == 50) { openArmorsMenu(p); return; }
            if (slot == 51) { openSwordsMenu(p); return; }
            handleRelicPurchase(p, slot);
        } else if ("picks".equals(type)) {
            if (slot == 49) { openMainMenu(p); return; }
            if (slot == 50) { openSwordsMenu(p); return; }
            if (slot == 13) {
                if (!"owner".equalsIgnoreCase(plugin.getRanks().getRank(p).id)) {
                    ColorUtil.sound(p, "entity.villager.no", 1f, 0.8f);
                    p.sendMessage(ColorUtil.c("&#FF1744✖ Божественный Молот OWNER'а [10×10] доступен СТРОГО ТОЛЬКО владельцу (OWNER)!"));
                    return;
                }
                for (ItemStack rem : p.getInventory().addItem(ItemFactory.hammerOwner()).values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), rem);
                }
                ColorUtil.sound(p, "ui.toast.challenge_complete", 1f, 1.2f);
                p.sendMessage(ColorUtil.c("&#FF1744👑 Вы получили эксклюзивный &lБожественный Молот OWNER'а [Мега-Бур 10×10]&r&#FF1744!"));
                return;
            }
            handleRelicItemTagPurchase(p, clicked, "picks");
        } else if ("swords".equals(type)) {
            if (slot == 48) { openPickaxesMenu(p); return; }
            if (slot == 49) { openMainMenu(p); return; }
            if (slot == 50) { openArmorsMenu(p); return; }
            if (slot == 12) {
                if (!"owner".equalsIgnoreCase(plugin.getRanks().getRank(p).id)) {
                    ColorUtil.sound(p, "entity.villager.no", 1f, 0.8f);
                    p.sendMessage(ColorUtil.c("&#FF1744✖ Божественный Меч OWNER доступен СТРОГО ТОЛЬКО владельцу (OWNER)!"));
                    return;
                }
                for (ItemStack rem : p.getInventory().addItem(ItemFactory.swordOwner()).values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), rem);
                }
                ColorUtil.sound(p, "ui.toast.challenge_complete", 1f, 1.2f);
                p.sendMessage(ColorUtil.c("&#FF1744👑 Вы получили эксклюзивный &lБожественный Меч OWNER [Острота VIII]&r&#FF1744!"));
                return;
            }
            handleRelicItemTagPurchase(p, clicked, "swords");
        } else if ("talismans".equals(type)) {
            if (slot == 49) { openMainMenu(p); return; }
            if (slot == 32) {
                if (!"owner".equalsIgnoreCase(plugin.getRanks().getRank(p).id)) {
                    ColorUtil.sound(p, "entity.villager.no", 1f, 0.8f);
                    p.sendMessage(ColorUtil.c("&#FF1744✖ Талисман OWNER доступен СТРОГО ТОЛЬКО владельцам (привилегия OWNER)!"));
                    return;
                }
                for (ItemStack rem : p.getInventory().addItem(ItemFactory.talOwner(5)).values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), rem);
                }
                ColorUtil.sound(p, "ui.toast.challenge_complete", 1f, 1.2f);
                p.sendMessage(ColorUtil.c("&#FF1744👑 Вы получили эксклюзивный &lТалисман OWNER (5 зарядов)&r&#FF1744!"));
                return;
            }
            handleRelicItemTagPurchase(p, clicked, "talismans");
        } else if ("armors".equals(type)) {
            if (slot == 49) { openMainMenu(p); return; }
            if (slot == 16) {
                if (!"owner".equalsIgnoreCase(plugin.getRanks().getRank(p).id)) {
                    ColorUtil.sound(p, "entity.villager.no", 1f, 0.8f);
                    p.sendMessage(ColorUtil.c("&#FF1744✖ Сет Брони OWNER доступен СТРОГО ТОЛЬКО владельцам (привилегия OWNER)!"));
                    return;
                }
                for (int s = 0; s < 4; s++) {
                    for (ItemStack rem : p.getInventory().addItem(ItemFactory.armorPiece("owner", s)).values()) {
                        p.getWorld().dropItemNaturally(p.getLocation(), rem);
                    }
                }
                ColorUtil.sound(p, "ui.toast.challenge_complete", 1f, 1.2f);
                p.sendMessage(ColorUtil.c("&#FF1744👑 Вы получили полный комплект &lБрони OWNER [4 предмета]&r&#FF1744!"));
                return;
            }
            if (slot == 25) {
                if (!"owner".equalsIgnoreCase(plugin.getRanks().getRank(p).id)) {
                    ColorUtil.sound(p, "entity.villager.no", 1f, 0.8f);
                    p.sendMessage(ColorUtil.c("&#FF1744✖ Клинок Властелина OWNER доступен СТРОГО ТОЛЬКО владельцам!"));
                    return;
                }
                for (ItemStack rem : p.getInventory().addItem(ItemFactory.swordOwner()).values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), rem);
                }
                ColorUtil.sound(p, "ui.toast.challenge_complete", 1f, 1.2f);
                p.sendMessage(ColorUtil.c("&#FF1744👑 Вы получили &lКлинок Властелина OWNER&r&#FF1744!"));
                return;
            }
            handleRelicItemTagPurchase(p, clicked, "armors");
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
            if (slot == 36 || slot == 37 || slot == 38 || slot == 41 || slot == 42 || slot == 43) {
                if (!plugin.getBossEvents().canAdminSpawnEvents(p)) {
                    p.sendMessage(ColorUtil.c("&#FF1744✖ Призывать Боссов и Ивенты могут только Админы и выше!"));
                    return;
                }
                if (slot == 36) plugin.getBossEvents().startRandomBossOnly();
                else if (slot == 37) plugin.getBossEvents().spawnBossEvent("airdrop", null);
                else if (slot == 38) plugin.getBossEvents().spawnBossEvent("meteor", null);
                else if (slot == 41) plugin.getBossEvents().spawnBossEvent("volcano", null);
                else if (slot == 42) plugin.getBossEvents().spawnBossEvent("beacon", null);
                else if (slot == 43) plugin.getBossEvents().spawnBossEvent("citadel", null);
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
            else if (slot == 10) {
                p.closeInventory();
                p.performCommand("free dragon");
            } else {
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
            if (slot == 49 || slot == 31) { openMainMenu(p); return; }
            DataStore.Clan my = plugin.getStore().getClanOf(p.getName());
            if (my != null) {
                if (slot == 19) { openClanStorage(p, my); return; }
                if (slot == 20) { p.closeInventory(); p.performCommand("clan home"); return; }
                if (slot == 21) { p.performCommand("clan deposit 10000"); openClanMenu(p); return; }
                if (slot == 23) { p.performCommand("clan upgrade"); openClanMenu(p); return; }
                if (slot == 24) { p.performCommand("clan pvp"); openClanMenu(p); return; }
                if (slot == 25) { p.closeInventory(); p.performCommand("clan coords"); return; }
            }
        } else if ("lp_groups".equals(type)) {
            if (!plugin.getLp().hasLpAccess(p)) { p.closeInventory(); return; }
            if (slot == 45 && h.page > 0) { openLpGroupsGui(p, h.page - 1); return; }
            if (slot == 53) { openLpGroupsGui(p, h.page + 1); return; }
            if (slot == 49) { p.closeInventory(); plugin.getLp().openEditorSession(p); return; }
            String cid = ItemFactory.getCustomId(clicked);
            if (cid != null && cid.startsWith("lpg_")) {
                openLpGroupEditGui(p, cid.substring(4));
            }
        } else if ("lp_edit".equals(type)) {
            if (!plugin.getLp().hasLpAccess(p)) { p.closeInventory(); return; }
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
            case 15: reward = ItemFactory.sharAresa(); cost = 310; break;
            case 16: reward = ItemFactory.sharSanty(); cost = 350; break;
            case 19: reward = ItemFactory.talKrushitelya(); cost = 310; break;
            case 20: reward = ItemFactory.talGrani(); cost = 290; break;
            case 21: reward = ItemFactory.talDedala(); cost = 250; break;
            case 22: reward = ItemFactory.talTritona(); cost = 230; break;
            case 23: reward = ItemFactory.talFeniksa(3); cost = 390; break;
            case 24: reward = ItemFactory.talEchidny(); cost = 280; break;
            case 25: reward = ItemFactory.talStuzhi(); cost = 300; break;
            case 28: reward = ItemFactory.elytraWind(); cost = 220; break;
            case 29: reward = ItemFactory.elytraPhoenix(); cost = 270; break;
            case 30: reward = ItemFactory.elytraVampire(); cost = 290; break;
            case 31: reward = ItemFactory.elytraThunder(); cost = 310; break;
            case 32: reward = ItemFactory.elytraTitan(); cost = 360; break;
            case 33: reward = ItemFactory.elytraSanta(); cost = 420; break;
            case 34: reward = ItemFactory.sharOsirisa(); cost = 240; break;
            case 37: reward = ItemFactory.plast(8); cost = 35; break;
            case 38: reward = ItemFactory.trapka(4); cost = 45; break;
            case 39: reward = ItemFactory.dezorient(4); cost = 40; break;
            case 40: reward = ItemFactory.bozhyaAura(2); cost = 50; break;
            case 41: reward = ItemFactory.pickDjamshut(); cost = 340; break;
            case 42: reward = ItemFactory.swordDracula(); cost = 320; break;
            case 43: reward = ItemFactory.swordKrush(); cost = 420; break;
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
        double lvlMult = plugin.getStore().getBuyerMultiplier(p.getName());
        double rankMult = 1.0 + plugin.getRanks().getRank(p).tier * 0.12;
        double mult = lvlMult * rankMult;
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
        plugin.getStore().addBuyerSold(p.getName(), earned);
        plugin.getStore().save();
        ColorUtil.sound(p, "entity.experience_orb.pickup", 1f, 1.3f);
        p.sendMessage(ColorUtil.c("&#00E676💰 Скупщик купил &f" + count + " шт. &#00E676за &a+" + ColorUtil.fmtMoney(earned)
                + "$ &8(Множитель Скупщика: &#00E676×" + String.format(Locale.US, "%.2f", Double.valueOf(plugin.getStore().getBuyerMultiplier(p.getName()))) + "&8)&7!"));
        openBuyerMenu(p);
    }

    private void handleBafovarClick(Player p, int slot) {
        if (slot == 29) {
            openBafovarBrewerMenu(p);
            return;
        }
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

    private void handleRelicItemTagPurchase(Player p, ItemStack clicked, String reopenMenu) {
        ItemMeta m = clicked.getItemMeta();
        if (m == null) return;
        Integer costObj = m.getPersistentDataContainer().get(SHOP_PRICE_KEY, org.bukkit.persistence.PersistentDataType.INTEGER);
        if (costObj == null || costObj.intValue() <= 0) return;
        int cost = costObj.intValue();
        if (!plugin.getStore().takeRelics(p, cost)) {
            ColorUtil.sound(p, "entity.villager.no", 1f, 1f);
            p.sendMessage(ColorUtil.c("&cНедостаточно Риликов! Нужно: " + cost + " ❖ (kinggrief.vercel.app)"));
            return;
        }
        plugin.getStore().save();
        String cid = ItemFactory.getCustomId(clicked);
        if (cid != null && cid.startsWith("armorset_")) {
            String sid = cid.substring(9);
            for (int s = 0; s < 4; s++) {
                for (ItemStack rem : p.getInventory().addItem(ItemFactory.armorPiece(sid, s)).values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), rem);
                }
            }
            ColorUtil.sound(p, "entity.player.levelup", 1f, 1.25f);
            p.sendMessage(ColorUtil.c("&#00E676✔ Вы приобрели полный 3D сет брони за &b" + cost + " ❖&7!"));
        } else {
            ItemStack give = clicked.clone();
            ItemMeta gm = give.getItemMeta();
            if (gm != null) {
                if (gm.hasLore() && gm.getLore().size() >= 3) {
                    List<String> l = new ArrayList<String>(gm.getLore());
                    for (int k = 0; k < 3 && !l.isEmpty(); k++) l.remove(l.size() - 1);
                    gm.setLore(l.isEmpty() ? null : l);
                }
                gm.getPersistentDataContainer().remove(SHOP_PRICE_KEY);
                give.setItemMeta(gm);
            }
            for (ItemStack rem : p.getInventory().addItem(give).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), rem);
            }
            ColorUtil.sound(p, "entity.experience_orb.pickup", 1f, 1.25f);
            p.sendMessage(ColorUtil.c("&#00E676✔ Вы приобрели предмет за &b" + cost + " ❖&7!"));
        }
        if ("talismans".equals(reopenMenu)) openTalismansMenu(p);
        else if ("armors".equals(reopenMenu)) openArmorsMenu(p);
        else if ("picks".equals(reopenMenu)) openPickaxesMenu(p);
        else if ("swords".equals(reopenMenu)) openSwordsMenu(p);
    }

    private void handleCategoryCoinPurchase(Player p, ItemStack clicked, String cat) {
        ItemMeta m = clicked.getItemMeta();
        if (m == null) return;
        Integer costObj = m.getPersistentDataContainer().get(SHOP_PRICE_KEY, org.bukkit.persistence.PersistentDataType.INTEGER);
        if (costObj == null || costObj.intValue() <= 0) return;
        int cost = costObj.intValue();
        if (!plugin.getStore().takeCoins(p.getName(), cost)) {
            ColorUtil.sound(p, "entity.villager.no", 1f, 1f);
            p.sendMessage(ColorUtil.c("&cНедостаточно монет! Нужно: " + ColorUtil.fmtMoney(cost) + "$"));
            return;
        }
        plugin.getStore().save();
        // Rebuild clean item copy without shop price lore
        ItemStack give = clicked.clone();
        ItemMeta gm = give.getItemMeta();
        if (gm != null) {
            if (gm.hasLore() && gm.getLore().size() >= 3) {
                List<String> l = new ArrayList<String>(gm.getLore());
                for (int k = 0; k < 3 && !l.isEmpty(); k++) l.remove(l.size() - 1);
                gm.setLore(l.isEmpty() ? null : l);
            }
            gm.getPersistentDataContainer().remove(SHOP_PRICE_KEY);
            give.setItemMeta(gm);
        }
        for (ItemStack rem : p.getInventory().addItem(give).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rem);
        }
        ColorUtil.sound(p, "entity.experience_orb.pickup", 1f, 1.25f);
        p.sendMessage(ColorUtil.c("&#00E676✔ Покупка успешна за &a" + ColorUtil.fmtMoney(cost) + "$&7!"));
        openCoinShopCat(p, cat);
    }

    private void handleTrapSkinClick(Player p, ItemStack clicked, ClickType click) {
        String cid = ItemFactory.getCustomId(clicked);
        if (cid == null || !cid.startsWith("skin_")) return;
        String skinId = cid.substring(5);
        String[] found = null;
        for (String[] sk : TRAP_SKINS) {
            if (sk[0].equalsIgnoreCase(skinId)) { found = sk; break; }
        }
        if (found == null) return;
        boolean isOwner = "owner".equalsIgnoreCase(plugin.getRanks().getRank(p).id);
        if ("owner".equalsIgnoreCase(skinId)) {
            if (!isOwner) {
                ColorUtil.sound(p, "entity.villager.no", 1f, 0.8f);
                p.sendMessage(ColorUtil.c("&#FF1744✖ Скин Трапки OWNER доступен СТРОГО только владельцу (OWNER)!"));
                return;
            }
            plugin.getStore().setSelectedTrapSkin(p.getName(), "owner");
            ColorUtil.sound(p, "ui.toast.challenge_complete", 1f, 1.2f);
            p.sendMessage(ColorUtil.c("&#00E676✔ Активирован эксклюзивный скин: " + found[1]));
            openTrapSkinsMenu(p);
            return;
        }
        if (plugin.getStore().hasTrapSkin(p.getName(), skinId)) {
            plugin.getStore().setSelectedTrapSkin(p.getName(), skinId);
            ColorUtil.sound(p, "block.enchantment_table.use", 1f, 1.2f);
            p.sendMessage(ColorUtil.c("&#00E676✔ Выбран скин Трапки: " + found[1] + " &8(" + found[5] + "&8)"));
            openTrapSkinsMenu(p);
            return;
        }
        if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
            int rCost = Integer.parseInt(found[4]);
            if (!plugin.getStore().takeRelics(p, rCost)) {
                p.sendMessage(ColorUtil.c("&cНедостаточно Риликов! Нужно: " + rCost + " ❖"));
                return;
            }
        } else {
            int cCost = Integer.parseInt(found[3]);
            if (!plugin.getStore().takeCoins(p.getName(), cCost)) {
                p.sendMessage(ColorUtil.c("&cНедостаточно монет! Нужно: " + ColorUtil.fmtMoney(cCost) + "$"));
                return;
            }
        }
        plugin.getStore().unlockTrapSkin(p.getName(), skinId);
        plugin.getStore().setSelectedTrapSkin(p.getName(), skinId);
        ColorUtil.sound(p, "ui.toast.challenge_complete", 1f, 1.2f);
        p.sendMessage(ColorUtil.c("&#00E676🎉 Скин Трапки " + found[1] + " &#00E676разблокирован и активирован!"));
        openTrapSkinsMenu(p);
    }

    private void handlePlastSkinClick(Player p, ItemStack clicked, ClickType click) {
        String cid = ItemFactory.getCustomId(clicked);
        if (cid == null || !cid.startsWith("pskin_")) return;
        String skinId = cid.substring(6);
        String[] found = null;
        for (String[] sk : PLAST_SKINS) {
            if (sk[0].equalsIgnoreCase(skinId)) { found = sk; break; }
        }
        if (found == null) return;
        boolean isOwner = "owner".equalsIgnoreCase(plugin.getRanks().getRank(p).id);
        if ("owner".equalsIgnoreCase(skinId)) {
            if (!isOwner) {
                ColorUtil.sound(p, "entity.villager.no", 1f, 0.8f);
                p.sendMessage(ColorUtil.c("&#FF1744✖ Скин Пласта OWNER доступен СТРОГО только владельцу (OWNER)!"));
                return;
            }
            plugin.getStore().setSelectedPlastSkin(p.getName(), "owner");
            ColorUtil.sound(p, "ui.toast.challenge_complete", 1f, 1.2f);
            p.sendMessage(ColorUtil.c("&#00E676✔ Активирован эксклюзивный скин Пласта: " + found[1]));
            openPlastSkinsMenu(p);
            return;
        }
        if (plugin.getStore().hasPlastSkin(p.getName(), skinId)) {
            plugin.getStore().setSelectedPlastSkin(p.getName(), skinId);
            ColorUtil.sound(p, "block.enchantment_table.use", 1f, 1.2f);
            p.sendMessage(ColorUtil.c("&#00E676✔ Выбран скин Пласта: " + found[1] + " &8(" + found[5] + "&8)"));
            openPlastSkinsMenu(p);
            return;
        }
        if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
            int rCost = Integer.parseInt(found[4]);
            if (!plugin.getStore().takeRelics(p, rCost)) {
                p.sendMessage(ColorUtil.c("&cНедостаточно Риликов! Нужно: " + rCost + " ❖"));
                return;
            }
        } else {
            int cCost = Integer.parseInt(found[3]);
            if (!plugin.getStore().takeCoins(p.getName(), cCost)) {
                p.sendMessage(ColorUtil.c("&cНедостаточно монет! Нужно: " + ColorUtil.fmtMoney(cCost) + "$"));
                return;
            }
        }
        plugin.getStore().unlockPlastSkin(p.getName(), skinId);
        plugin.getStore().setSelectedPlastSkin(p.getName(), skinId);
        ColorUtil.sound(p, "ui.toast.challenge_complete", 1f, 1.2f);
        p.sendMessage(ColorUtil.c("&#00E676🎉 Скин Пласта " + found[1] + " &#00E676разблокирован и активирован!"));
        openPlastSkinsMenu(p);
    }

    private boolean isBafovarSlot(int rawSlot) {
        for (int s : BAFOVAR_SLOTS) if (s == rawSlot) return true;
        return false;
    }

    private boolean isPotionStack(ItemStack it) {
        if (it == null) return false;
        Material t = it.getType();
        return t == Material.POTION || t == Material.SPLASH_POTION || t == Material.LINGERING_POTION;
    }

    private void handleBafovarBrewerClick(InventoryClickEvent e, Player p, Inventory top) {
        int raw = e.getRawSlot();
        // Clicking player's own inventory: allow normal pickup or quick-move of 1 potion into an open brew slot!
        if (raw >= top.getSize()) {
            if (e.isShiftClick()) {
                e.setCancelled(true);
                ItemStack cur = e.getCurrentItem();
                if (!isPotionStack(cur)) {
                    p.sendMessage(ColorUtil.c("&cВ котёл Бафовара можно класть только зелья (макс 8 шт.)!"));
                    return;
                }
                for (int bs : BAFOVAR_SLOTS) {
                    if (top.getItem(bs) == null || top.getItem(bs).getType() == Material.AIR) {
                        ItemStack one = cur.clone();
                        one.setAmount(1);
                        top.setItem(bs, one);
                        if (cur.getAmount() <= 1) e.setCurrentItem(null);
                        else cur.setAmount(cur.getAmount() - 1);
                        ColorUtil.sound(p, "item.bottle.fill", 0.8f, 1.2f);
                        return;
                    }
                }
                p.sendMessage(ColorUtil.c("&eВсе 8 слотов котла Бафовара уже заполнены!"));
            }
            return;
        }
        // Inside top brewer inventory: allow placing/taking ONLY in the 8 BAFOVAR_SLOTS
        if (isBafovarSlot(raw)) {
            ItemStack cursor = e.getCursor();
            if (cursor != null && cursor.getType() != Material.AIR && !isPotionStack(cursor)) {
                e.setCancelled(true);
                p.sendMessage(ColorUtil.c("&cВ этот слот можно положить только зелье!"));
            }
            return;
        }
        e.setCancelled(true);
        if (raw == 40) {
            returnBrewerPotions(p, top);
            openBafovarMenu(p);
            return;
        }
        if (raw == 30 || raw == 32) {
            boolean splash = (raw == 32);
            List<org.bukkit.potion.PotionEffect> merged = new ArrayList<org.bukkit.potion.PotionEffect>();
            List<String> labels = new ArrayList<String>();
            int potCount = 0;
            for (int bs : BAFOVAR_SLOTS) {
                ItemStack inSlot = top.getItem(bs);
                if (!isPotionStack(inSlot)) continue;
                potCount++;
                org.bukkit.inventory.meta.PotionMeta pm = (org.bukkit.inventory.meta.PotionMeta) inSlot.getItemMeta();
                if (pm != null) {
                    List<org.bukkit.potion.PotionEffect> allEffs = new ArrayList<org.bukkit.potion.PotionEffect>();
                    if (pm.hasCustomEffects()) allEffs.addAll(pm.getCustomEffects());
                    try {
                        if (pm.getBasePotionType() != null && pm.getBasePotionType().getPotionEffects() != null) {
                            allEffs.addAll(pm.getBasePotionType().getPotionEffects());
                        }
                    } catch (Throwable ignored) {}
                    for (org.bukkit.potion.PotionEffect pe : allEffs) {
                        merged.add(pe);
                        labels.add("&e" + pe.getType().getKey().getKey() + " " + (pe.getAmplifier() + 1) + " &7(" + (pe.getDuration() / 20) + "с)");
                    }
                }
            }
            if (potCount <= 0 || merged.isEmpty()) {
                p.sendMessage(ColorUtil.c("&cПоложите от 1 до 8 зелий с эффектами в пустые слоты котла!"));
                ColorUtil.sound(p, "entity.villager.no", 1f, 1f);
                return;
            }
            if (!plugin.getStore().takeCoins(p.getName(), 5000)) {
                p.sendMessage(ColorUtil.c("&cНедостаточно монет! Варка у Бафовара стоит 5 000$."));
                ColorUtil.sound(p, "entity.villager.no", 1f, 1f);
                return;
            }
            for (int bs : BAFOVAR_SLOTS) {
                ItemStack inSlot = top.getItem(bs);
                if (inSlot == null) continue;
                if (inSlot.getAmount() > 1) {
                    inSlot.setAmount(inSlot.getAmount() - 1);
                    top.setItem(bs, inSlot);
                } else {
                    top.setItem(bs, null);
                }
            }
            plugin.getStore().save();
            ItemStack result = ItemFactory.bafovarCustomPotion(splash, merged, labels);
            for (ItemStack rem : p.getInventory().addItem(result).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), rem);
            }
            ColorUtil.sound(p, "block.brewing_stand.brew", 1f, 1.15f);
            p.sendMessage(ColorUtil.c("&#FF9100⚗ Бафовар сварил для вас кастомное зелье из &e" + potCount + " зелек &#FF9100за &a5 000$&7!"));
        }
    }

    public void returnBrewerPotions(Player p, Inventory top) {
        if (top == null) return;
        for (int bs : BAFOVAR_SLOTS) {
            ItemStack it = top.getItem(bs);
            if (it != null && it.getType() != Material.AIR) {
                top.setItem(bs, null);
                for (ItemStack rem : p.getInventory().addItem(it).values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), rem);
                }
            }
        }
    }

    public void handleInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        Inventory top = e.getView().getTopInventory();
        if (top != null && top.getHolder() instanceof KgMenuHolder) {
            KgMenuHolder h = (KgMenuHolder) top.getHolder();
            if ("baf_brew".equals(h.menuType)) {
                returnBrewerPotions((Player) e.getPlayer(), top);
            }
        }
        if (e.getView().getTitle() != null && e.getView().getTitle().contains("Хранилище Клана")) {
            plugin.getStore().save();
        }
    }
}
