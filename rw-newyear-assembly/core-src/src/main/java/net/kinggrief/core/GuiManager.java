package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
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

    public static final class CasePrize {
        public final String display;
        public final Material icon;
        public final String type; // rank, relics, coins, item, kit
        public final String value;
        public final int weight;

        public CasePrize(String display, Material icon, String type, String value, int weight) {
            this.display = display;
            this.icon = icon;
            this.type = type;
            this.value = value;
            this.weight = weight;
        }
    }

    private final KingGriefCore plugin;
    private final Random rng = new Random();

    public GuiManager(KingGriefCore plugin) {
        this.plugin = plugin;
    }

    private void fillBorder(Inventory inv, Material paneMat) {
        ItemStack pane = ItemFactory.icon(paneMat, "&8•");
        int size = inv.getSize();
        for (int i = 0; i < size; i++) {
            if (i < 9 || i >= size - 9 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, pane);
            }
        }
    }

    // ================= 1. MAIN SERVER MENU (/menu) =================
    public void openMainMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("main", "", 0), 54,
                ColorUtil.c("&#80D8FF❄ &#FF1744&lМЕНЮ СЕРВЕРА KINGGRIEF &#80D8FF❄"));
        fillBorder(inv, Material.LIGHT_BLUE_STAINED_GLASS_PANE);

        DataStore st = plugin.getStore();
        RankManager.RankDef rk = plugin.getRanks().getRank(p);
        DataStore.Clan cl = st.getClanOf(p.getName());

        inv.setItem(4, ItemFactory.glowIcon(Material.PLAYER_HEAD,
                "&#FFD740&l👤 Профиль: &f" + p.getName(),
                "&7Ранг: &f" + rk.shortLabel,
                "&7Клан: &f" + (cl != null ? "[" + cl.tag + "]" : "Нет клана"),
                "&7Монеты: &#FFEA00" + ColorUtil.fmtMoney(st.getCoins(p.getName())) + "$",
                "&7Рилики: &#00E5FF" + st.getRelics(p) + " ❖"));

        inv.setItem(19, ItemFactory.glowIcon(Material.COMPASS, "&#00E676&l🧭 Выход в Выживание (/rtp)",
                "&7Телепортация в рандомную точку мира!", "", "&#00E676▸ Нажми, чтобы открыть!"));
        inv.setItem(20, ItemFactory.glowIcon(Material.BEACON, "&#80D8FF&l🎄 Новогодний Спавн (/spawn)",
                "&7Телепорт к главной Ёлке и NPC!", "", "&#00E676▸ Нажми для телепорта!"));
        inv.setItem(21, ItemFactory.glowIcon(Material.TOTEM_OF_UNDYING, "&#FF4081&l💎 Донат-Магазин (/shop)",
                "&7Сферы, Талисманы, Трапки, Пласты, Бур 3×3!", "", "&#00E676▸ Открыть за рилики!"));
        inv.setItem(22, ItemFactory.glowIcon(Material.GOLDEN_HELMET, "&#FFD740&l👑 Привилегии и Цены (/donate)",
                "&7PEGAS (19₽) · KING (199₽) · OPKA (999₽) · D.OWNER!", "", "&#FFD740▸ Открыть каталог!"));
        inv.setItem(23, ItemFactory.glowIcon(Material.ENDER_CHEST, "&#A855F7&l🎁 Все 11 Кейсов (/cases)",
                "&7Открывай кейсы с крутящейся рулеткой!", "", "&#00E676▸ Открыть кейсы!"));
        inv.setItem(24, ItemFactory.glowIcon(Material.NETHERITE_CHESTPLATE, "&#FF9100&l🛡 Наборы Привилегий (/kits)",
                "&7Стартовый, Новогодний и все донат-киты!", "", "&#00E676▸ Выбрать кит!"));
        inv.setItem(25, ItemFactory.glowIcon(Material.NETHER_STAR, "&#FF5252&l🎅 Лавка Деда Мороза (/nyshop)",
                "&7Новогодние леденцы, бомбы Гринча и кейсы за $!", "", "&#00E676▸ К Деду Морозу!"));

        inv.setItem(28, ItemFactory.icon(Material.GOLD_INGOT, "&#FFD740&l⚖ Аукцион Игроков (/ah)",
                "&7Покупай и продавай топ-вещи: &f/ah sell <цена>"));
        inv.setItem(29, ItemFactory.icon(Material.EMERALD, "&#FFAB40&l💰 Скупщик Ресурсов (/buyer)",
                "&7Продавай руду, урожай и лут мобов в 1 клик!"));
        inv.setItem(30, ItemFactory.icon(Material.IRON_PICKAXE, "&#FFEE58&l⛏ Работы и Авто-Шахта (/jobs)",
                "&7Устройся на работу и зарабатывай монеты!"));
        inv.setItem(31, ItemFactory.icon(Material.SHIELD, "&#B388FF&l🏰 Кланы Сервера (/clan)",
                "&7Создай клан: &f/clan create <тег>"));
        inv.setItem(32, ItemFactory.glowIcon(Material.EXPERIENCE_BOTTLE, "&#38BDF8&l⭐ Боевой Пропуск (/bp)",
                "&7Выполняй задания зимы и забирай рилики!"));
        inv.setItem(33, ItemFactory.icon(Material.CHEST_MINECART, "&#76FF03&l🎁 Подарки и Промокоды (/gifts)",
                "&7Ежедневный бонус + промокод &e/promo NEWYEAR"));
        inv.setItem(34, ItemFactory.icon(Material.DIAMOND_PICKAXE, "&#FFEE58&l⛏ Телепорт на Авто-Шахту (/mine)",
                "&7Бесплатная руда прямо на спавне!"));

        inv.setItem(48, ItemFactory.icon(Material.DIAMOND_SWORD, "&#10B981&l🛡 Команда Проекта КП и ДКП (/kp)",
                "&7Иерархия персонала и набор в модерацию"));
        inv.setItem(49, ItemFactory.glowIcon(Material.EMERALD_BLOCK, "&#00E676&l🌐 САЙТ АВТОДОНАТА",
                "&fhttps://kinggrief.vercel.app", "&7Моментальная авто-выдача за 5 секунд!"));
        inv.setItem(50, ItemFactory.icon(Material.SPYGLASS, "&#FF0050&l🎬 Медиа-Партнёрка (/media)",
                "&7YouTube · TikTok · Twitch · MEDIA+"));

        p.openInventory(inv);
        ColorUtil.sound(p, "ui.button.click", 0.7f, 1.2f);
    }

    // ================= 2. DONATE & REAL SITE PRICES (/donate) =================
    public void openDonateMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("donate", "", 0), 54,
                ColorUtil.c("&#FFD740&l👑 ДОНАТ И ЦЕНЫ С САЙТА"));
        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        Object[][] siteRanks = new Object[][]{
                {10, Material.LIME_DYE,           "&#22C55E&lPEGAS &8— &#00E67619₽ &8(&m39₽&8)",      "3 дома, 3 привата, /kit pegas, /wb, /hat"},
                {11, Material.SLIME_BALL,         "&#84CC16&lCOBRA &8— &#00E67639₽ &8(&m59₽&8)",      "4 дома, 4 привата, /kit cobra, /ext, /ci"},
                {12, Material.RABBIT_FOOT,        "&#EC4899&lBUNNY &8— &#00E67659₽ &8(&m89₽&8)",      "6 домов, 5 приватов, /kit bunny, /back"},
                {13, Material.FEATHER,            "&#38BDF8&lRABBIT &8— &#00E67689₽ &8(&m129₽&8)",    "8 домов, 6 приватов, /kit rabbit, цветной чат"},
                {14, Material.REDSTONE,           "&#EF4444&lVAMPIRE &8— &#00E67699₽ &8(&m159₽&8)",   "10 домов, тотем в ките, /near"},
                {15, Material.GOLDEN_APPLE,       "&#FACC15&lGOD &8— &#00E676149₽ &8(&m229₽&8)",      "13 домов, /fly, /feed, /fix all, Божья Аура"},
                {16, Material.GOLDEN_HELMET,      "&#A855F7&l👑 KING &8— &#FFD740&l199₽ &8(&m299₽&8)","★ ХИТ! 18 домов, 12 приватов, /ec, /anvil, /heal"},
                {19, Material.SHIELD,             "&#14B8A6&lD.MODER &8— &#00E676249₽ &8(&m399₽&8)",  "ДКП: 24 дома, /mute, /kick, /invsee, /tp"},
                {20, Material.DIAMOND_CHESTPLATE, "&#3B82F6&lD.ST.MODER &8— &#00E676349₽",            "ДКП: 28 домов, /tempban, /vanish"},
                {21, Material.AMETHYST_SHARD,     "&#8B5CF6&lD.GLMODER &8— &#00E676449₽",             "ДКП: 32 дома, /unban, наблюдение"},
                {22, Material.BLAZE_ROD,          "&#F97316&lD.ML.ADMIN &8— &#00E676599₽",            "ДКП: 36 домов, /bc объявления"},
                {23, Material.NETHERITE_AXE,      "&#DC2626&lD.ADMIN &8— &#00E676799₽ &8(&m1099₽&8)", "ДКП: 40 домов, мощный кит D.ADMIN"},
                {24, Material.DRAGON_BREATH,      "&#C026D3&l🔥 OPKA &8— &#FFD740&l999₽ &8(&m1399₽&8)","★ 60 домов, 50 приватов, /gm 1, /tphere, Бур 3×3!"},
                {25, Material.NETHER_STAR,        "&#FACC15&l👑 D.OWNER &8— &#FFEA00&l1 499₽",        "★ ВЕРШИНА! Безлимит домов, Сфера Андромеды, всё!"}
        };
        for (Object[] r : siteRanks) {
            inv.setItem(((Integer) r[0]).intValue(), ItemFactory.glowIcon((Material) r[1], (String) r[2],
                    "&7" + r[3], "", "&#00E5FF▸ Купить на: &fkinggrief.vercel.app"));
        }

        inv.setItem(29, ItemFactory.glowIcon(Material.ENDER_CHEST, "&#A855F7&l🎁 Все 11 Кейсов (от 19₽)",
                "&7Денежный 19₽ · Джамшута 25₽ · Донат 29₽ · Киты 35₽",
                "&7Рилики 39₽ · Сезонный 39₽ · Всё или ничего 45₽",
                "&7Вещи 49₽ · Легендарный 59₽ · Супер 99₽ · ReallyDev 129₽",
                "&#00E676▸ Нажми, чтобы открыть меню кейсов!"));
        inv.setItem(31, ItemFactory.glowIcon(Material.SUNFLOWER, "&#F59E0B&l❖ Рилики (от 19₽ за 1 000 ❖)",
                "&71 000 ❖ — 19₽ | 2 500 ❖ — 39₽ | 5 000 ❖ — 69₽",
                "&710 000 ❖ — 119₽ | 25 000 ❖ — 249₽ | 100 000 ❖ — 799₽",
                "&#00E676▸ Нажми, чтобы открыть /shop!"));
        inv.setItem(33, ItemFactory.icon(Material.TOTEM_OF_UNDYING, "&#22C55E&l🔓 Разбан и Размут",
                "&7Размут — 49₽ | Разбан — 99₽",
                "&7Размут по IP — 99₽ | Разбан по IP — 199₽"));

        inv.setItem(39, ItemFactory.icon(Material.DIAMOND_SWORD, "&#10B981&l🛡 Команда Проекта (/kp)", "&7Открыть меню КП и ДКП"));
        inv.setItem(40, ItemFactory.glowIcon(Material.EMERALD_BLOCK, "&#00E676&l🌐 КУПИТЬ НА САЙТЕ", "&fhttps://kinggrief.vercel.app"));
        inv.setItem(41, ItemFactory.icon(Material.SPYGLASS, "&#FF0050&l🎬 Медиа-Партнёрка (/media)", "&7Открыть меню блогеров"));
        p.openInventory(inv);
    }

    // ================= 3. KP & DKP (/kp) + MEDIA (/media) =================
    public void openKpMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("kp", "", 0), 36,
                ColorUtil.c("&#10B981&l🛡 КОМАНДА ПРОЕКТА (КП И ДКП)"));
        fillBorder(inv, Material.GRAY_STAINED_GLASS_PANE);
        inv.setItem(11, ItemFactory.glowIcon(Material.DIAMOND_HELMET, "&#38BDF8&l🛡 Иерархия КП (Персонал)",
                "&bХелпер &8→ &3Ст.Хелпер &8→ &9Модер &8→ &1Ст.Модер",
                "&5Гл.Модер &8→ &aКП &8→ &2CURATOR &8→ &cТех.Админ &8→ &4OWNER",
                "", "&7Подать заявку: &fkinggrief.vercel.app/support"));
        inv.setItem(13, ItemFactory.glowIcon(Material.EMERALD, "&#22C55E&l☘ CURATOR & OWNER",
                "&7Кастомные анимированные значки из нашего РП!",
                "&7Полное управление набором Хелперов и Медиа!"));
        inv.setItem(15, ItemFactory.glowIcon(Material.GOLDEN_SWORD, "&#FF6D00&l⚡ Ветка ДКП (Донат-Персонал)",
                "&7D.HELPER · D.MODER (249₽) · D.ST.MODER (349₽)",
                "&7D.GLMODER (449₽) · D.ADMIN (799₽) · ДКП · OPKA (999₽)",
                "&#FFD740▸ Нажми, чтобы открыть /donate!"));
        inv.setItem(22, ItemFactory.glowIcon(Material.NETHERITE_CHESTPLATE, "&#10B981&l🎁 Забрать набор сотрудника (/kit kp)",
                "&7Доступно Хелперам, Модераторам, КП и ДКП!"));
        p.openInventory(inv);
    }

    public void openMediaMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("media", "", 0), 27,
                ColorUtil.c("&#FF0050&l🎬 МЕДИА-ПАРТНЁРКА ПРОЕКТА"));
        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);
        inv.setItem(11, ItemFactory.icon(Material.AMETHYST_CLUSTER, "&#FF0050&lTik&#00F2EA&lTok &7/ &#9146FF&lTWITCH",
                "&7TikTok от 500 просм. | Twitch от 10 зрит.", "&aДаёт префикс, /fly и /kit media!"));
        inv.setItem(13, ItemFactory.glowIcon(Material.FIREWORK_ROCKET, "&#00E676&l🎁 Забрать кит блогера (/kit media)",
                "&7Нажми, если у тебя есть статус TikTok / YT / Media!"));
        inv.setItem(15, ItemFactory.glowIcon(Material.NETHER_STAR, "&#F43F5E&l🎬 YOUTUBE & MEDIA+",
                "&7Доступ к /vanish, /gm 3 и кейсам на розыгрыши!", "&fЗаявка: kinggrief.vercel.app/support"));
        p.openInventory(inv);
    }

    // ================= 4. RELICS DONATE SHOP (/shop) =================
    public void openShopMenu(Player p) {
        int rel = plugin.getStore().getRelics(p);
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("shop", "", 0), 54,
                ColorUtil.c("&#FF1744&l💎 МАГАЗИН &8(&b" + rel + " ❖&8)"));
        fillBorder(inv, Material.LIGHT_BLUE_STAINED_GLASS_PANE);

        inv.setItem(4, ItemFactory.glowIcon(Material.SUNFLOWER, "&#00E5FF&lТвой баланс: &f" + rel + " риликов ❖",
                "&7Пополнить от 19₽: &#FFD740kinggrief.vercel.app/relics"));

        inv.setItem(10, shopDisplay(ItemFactory.sphereAndromeda(), 499));
        inv.setItem(11, shopDisplay(ItemFactory.sphereChimera(), 399));
        inv.setItem(12, shopDisplay(ItemFactory.talismanKrush(), 549));
        inv.setItem(13, shopDisplay(ItemFactory.talismanFrost(), 449));
        inv.setItem(14, shopDisplay(ItemFactory.drill3x3(), 649));
        inv.setItem(15, shopDisplay(ItemFactory.godAura(4), 199));
        inv.setItem(16, shopDisplay(ItemFactory.dezor(4), 199));

        inv.setItem(19, shopDisplay(ItemFactory.yavnayaPyl(6), 149));
        inv.setItem(20, shopDisplay(ItemFactory.fireTornado(4), 179));
        inv.setItem(21, shopDisplay(ItemFactory.trapka(3), 229));
        inv.setItem(22, shopDisplay(ItemFactory.plast(6), 149));
        inv.setItem(23, shopDisplay(ItemFactory.claimBlock(Material.DIAMOND_ORE, 25, "&#38BDF8💎 Приват 25×25 блоков"), 99));
        inv.setItem(24, shopDisplay(ItemFactory.claimBlock(Material.EMERALD_ORE, 40, "&#22C55E💚 Приват 40×40 блоков"), 199));
        inv.setItem(25, shopDisplay(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 8), 249));

        inv.setItem(31, ItemFactory.glowIcon(Material.ENDER_CHEST, "&#80D8FF&l❄ Ключ Сезонного Кейса",
                "&7Открой на Арене Кейсов или в /cases!", "", "&fЦена: &#00E5FF299 риликов ❖", "&#00E676▸ Нажми для покупки!"));
        inv.setItem(49, ItemFactory.icon(Material.ARROW, "&e← Назад в Меню (/menu)"));
        p.openInventory(inv);
    }

    private ItemStack shopDisplay(ItemStack base, int priceRelics) {
        ItemStack copy = base.clone();
        org.bukkit.inventory.meta.ItemMeta m = copy.getItemMeta();
        if (m != null) {
            List<String> lore = m.hasLore() ? new ArrayList<String>(m.getLore()) : new ArrayList<String>();
            lore.add("");
            lore.add(ColorUtil.c("&fЦена: &#00E5FF" + priceRelics + " риликов ❖"));
            lore.add(ColorUtil.c("&#00E676▸ Нажми ЛКМ для покупки!"));
            m.setLore(lore);
            copy.setItemMeta(m);
        }
        return copy;
    }

    // ================= 5. SANTA SHOP (/nyshop) & SNOWMAIDEN GIFTS (/gifts) =================
    public void openNyShop(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("nyshop", "", 0), 27,
                ColorUtil.c("&#FF5252&l🎅 ЛАВКА ДЕДА МОРОЗА ❄"));
        fillBorder(inv, Material.RED_STAINED_GLASS_PANE);
        inv.setItem(10, ItemFactory.glowIcon(Material.CHEST, "&#80D8FF&l🎁 Бесплатный Новогодний Набор",
                "&7Клинок, кирка, леденцы, бомбы Гринча и салюты!", "&#00E676▸ Нажми, чтобы получить!"));
        inv.setItem(12, ItemFactory.glowIcon(Material.COOKIE, "&#FF4081&l🍭 Новогодний Леденец ×4",
                "&7Скорость III + Исцеление!", "&fЦена: &#FFEA005 000$ монет"));
        inv.setItem(14, ItemFactory.glowIcon(Material.SNOWBALL, "&#69F0AE&l❄ Ледяная Бомба Гринча ×4",
                "&7Замораживает врагов вокруг!", "&fЦена: &#FFEA008 000$ монет"));
        inv.setItem(16, ItemFactory.glowIcon(Material.ENDER_CHEST, "&#FFD740&l🎄 Донат-Кейс за Монеты",
                "&7Выбивай привилегии игровым путём!", "&fЦена: &#FFEA00250 000$ монет"));
        p.openInventory(inv);
    }

    public void openGiftsMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("gifts", "", 0), 27,
                ColorUtil.c("&#80D8FF&l🤶 ПОДАРКИ СНЕГУРОЧКИ (/free)"));
        fillBorder(inv, Material.WHITE_STAINED_GLASS_PANE);
        long rem = (plugin.getStore().getDailyReadyAt(p.getName()) - System.currentTimeMillis()) / 1000L;
        String status = rem <= 0 ? "&#00E676✔ Доступно прямо сейчас!" : "&eДоступно через: " + ColorUtil.fmtTime(rem);
        inv.setItem(11, ItemFactory.glowIcon(Material.GOLD_BLOCK, "&#FFD740&l🎁 Ежедневный Подарок Снегурочки",
                "&7Награда: &#FFEA00+7 500$ &7и &#00E5FF+25 риликов ❖", status));
        inv.setItem(13, ItemFactory.glowIcon(Material.IRON_CHESTPLATE, "&#76FF03&l🛡 Стартовый + Новогодний Кит",
                "&7Инструменты, броня, приват 10×10 и сладости!"));
        inv.setItem(15, ItemFactory.glowIcon(Material.NAME_TAG, "&#FF4081&l🎟 Бесплатные Промокоды Сезона!",
                "&f• &e/promo NEWYEAR &7— +10 000$ и Сезонный Кейс!",
                "&f• &e/promo KINGGRIEF &7— +50 риликов ❖ и Божья Аура!",
                "&f• &e/promo FREE &7— +5 000$ и 2 Леденца!"));
        p.openInventory(inv);
    }

    // ================= 6. RTP MENU (/rtp) =================
    public void openRtpMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("rtp", "", 0), 27,
                ColorUtil.c("&#00E676&l🧭 РАНДОМНАЯ ТЕЛЕПОРТАЦИЯ"));
        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);
        inv.setItem(10, ItemFactory.glowIcon(Material.GRASS_BLOCK, "&#00E676&l🌲 Ближняя телепортация",
                "&7Дистанция: &f350 — 1 800 блоков", "&7Безопасный поиск суши без лавы и воды!"));
        inv.setItem(12, ItemFactory.glowIcon(Material.ENDER_EYE, "&#FFD740&l🏔 Далёкая телепортация (Для Базы)",
                "&7Дистанция: &f1 800 — 5 500 блоков", "&7Идеально для постройки секретной базы!"));
        inv.setItem(14, ItemFactory.glowIcon(Material.CRYING_OBSIDIAN, "&#FF5252&l🔥 VIP Телепортация (До 9 000 бл.)",
                "&7Доступно от привилегии GOD / KING и выше!"));
        inv.setItem(16, ItemFactory.glowIcon(Material.ENDER_CHEST, "&#FF4081&l☄ К Мистическому Аирдропу!",
                "&7Телепортирует в 65 блоках от активного сундука!"));
        p.openInventory(inv);
    }

    // ================= 7. BUYER MENU (/buyer) =================
    public static final Object[][] BUYER_ITEMS = new Object[][]{
            {Material.DIAMOND,         16,  8000.0, "16 Алмазов"},
            {Material.EMERALD,         16,  6400.0, "16 Изумрудов"},
            {Material.GOLD_INGOT,      32,  6400.0, "32 Золота"},
            {Material.IRON_INGOT,      32,  4800.0, "32 Железа"},
            {Material.COAL,            64,  2800.0, "64 Угля"},
            {Material.LAPIS_LAZULI,    64,  3000.0, "64 Лазурита"},
            {Material.REDSTONE,        64,  2800.0, "64 Редстоуна"},
            {Material.NETHERITE_SCRAP,  4, 16000.0, "4 Незеритовых лома"},
            {Material.WHEAT,           64,  2500.0, "64 Пшеницы"},
            {Material.CARROT,          64,  2400.0, "64 Моркови"},
            {Material.POTATO,          64,  2400.0, "64 Картофеля"},
            {Material.SUGAR_CANE,      64,  2200.0, "64 Тростника"},
            {Material.BONE,            64,  3200.0, "64 Кости"},
            {Material.ROTTEN_FLESH,    64,  2000.0, "64 Гнилой плоти"},
            {Material.GUNPOWDER,       32,  4500.0, "32 Пороха"},
            {Material.ENDER_PEARL,     16,  5000.0, "16 Жемчуга Эндера"}
    };

    public void openBuyerMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("buyer", "", 0), 45,
                ColorUtil.c("&#FFAB40&l💰 СКУПЩИК РЕСУРСОВ REALLYWORLD"));
        fillBorder(inv, Material.GRAY_STAINED_GLASS_PANE);
        int[] slots = new int[]{10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 29,33};
        for (int i = 0; i < BUYER_ITEMS.length && i < slots.length; i++) {
            Material m = (Material) BUYER_ITEMS[i][0];
            int amt = ((Integer) BUYER_ITEMS[i][1]).intValue();
            double pay = ((Double) BUYER_ITEMS[i][2]).doubleValue();
            String lbl = (String) BUYER_ITEMS[i][3];
            inv.setItem(slots[i], ItemFactory.make(m, Math.min(64, amt), "&#FFEE58&lПродать " + lbl,
                    Arrays.asList("&7Награда: &#00E676+" + ColorUtil.fmtMoney(pay) + "$", "", "&#00E676▸ Нажми ЛКМ для продажи!"),
                    false, null, null, 0));
        }
        inv.setItem(31, ItemFactory.glowIcon(Material.HOPPER, "&#00E676&l⚡ ПРОДАТЬ ВСЕ РЕСУРСЫ СРАЗУ!",
                "&7Автоматически продаст всю добытую руду", "&7и ресурсы из вашего инвентаря в 1 клик!"));
        p.openInventory(inv);
    }

    // ================= 8. JOBS (/jobs) & BATTLEPASS (/bp) & KITS (/kits) =================
    public void openJobsMenu(Player p) {
        String cur = plugin.getStore().getJob(p.getName());
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("jobs", "", 0), 27,
                ColorUtil.c("&#FFEE58&l⛏ РАБОТЫ СЕРВЕРА"));
        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);
        inv.setItem(10, ItemFactory.glowIcon(Material.DIAMOND_PICKAXE, "&#38BDF8&l⛏ Шахтёр" + ("miner".equals(cur) ? " &a[Активна]" : ""),
                "&7Доход: &#FFEA00+25$ — 250$ &7за каждую добытую руду!"));
        inv.setItem(12, ItemFactory.glowIcon(Material.IRON_AXE, "&#84CC16&l🪓 Лесоруб" + ("woodcutter".equals(cur) ? " &a[Активна]" : ""),
                "&7Доход: &#FFEA00+30$ &7за каждое срубленное бревно!"));
        inv.setItem(14, ItemFactory.glowIcon(Material.DIAMOND_SWORD, "&#FF5252&l⚔ Охотник" + ("hunter".equals(cur) ? " &a[Активна]" : ""),
                "&7Доход: &#FFEA00+80$ &7за каждого моба и &#FFEA00+500$ &7за игрока!"));
        inv.setItem(16, ItemFactory.glowIcon(Material.GOLDEN_HOE, "&#FACC15&l🌾 Фермер" + ("farmer".equals(cur) ? " &a[Активна]" : ""),
                "&7Доход: &#FFEA00+35$ &7за сбор урожая!"));
        inv.setItem(22, ItemFactory.icon(Material.BARRIER, "&cУволиться с текущей работы"));
        p.openInventory(inv);
    }

    public void openBattlePassMenu(Player p) {
        DataStore st = plugin.getStore();
        int mined = st.getMinedBlocks(p.getName());
        int kills = st.getKills(p.getName());
        int score = mined + kills * 20;
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("bp", "", 0), 27,
                ColorUtil.c("&#38BDF8&l⭐ ЗИМНИЙ БОЕВОЙ ПРОПУСК &8(&fОчки: " + score + "&8)"));
        fillBorder(inv, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        int[] reqs = new int[]{25, 75, 150, 300, 500, 800, 1200};
        String[] rewards = new String[]{
                "+5 000$ монет", "+25 риликов ❖", "Ключ Денежного Кейса",
                "+50 риликов ❖ + 2 Божьи Ауры", "Ключ Сезонного Кейса",
                "+150 риликов ❖ + 2 Трапки", "🔮 Сфера Химеры + 300 риликов ❖"
        };
        for (int i = 0; i < 7; i++) {
            int lvl = i + 1;
            boolean done = st.hasClaimedBp(p.getName(), lvl);
            boolean ready = score >= reqs[i];
            Material mat = done ? Material.LIME_STAINED_GLASS_PANE : (ready ? Material.CHEST_MINECART : Material.MINECART);
            inv.setItem(10 + i, ItemFactory.glowIcon(mat,
                    "&#FFD740&lУровень #" + lvl + (done ? " &a[Получено]" : ""),
                    "&7Требуется очков сезона: &f" + reqs[i] + " &8(У вас: " + score + ")",
                    "&7Награда: &#00E676" + rewards[i],
                    "&8(1 блок руды = 1 очко, 1 килл = 20 очков)",
                    done ? "&a✔ Уже забрано" : (ready ? "&#00E676▸ Нажми, чтобы забрать!" : "&cЕщё не выполнено")));
        }
        p.openInventory(inv);
    }

    public void openKitsMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("kits", "", 0), 45,
                ColorUtil.c("&#FF9100&l🎄 НАБОРЫ ВСЕХ ПРИВИЛЕГИЙ (/kits)"));
        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);
        Object[][] ks = new Object[][]{
                {10, Material.IRON_SWORD,          "start",   "&f&lНабор Игрока (Бесплатно)"},
                {11, Material.SNOWBALL,            "newyear", "&#80D8FF&l❄ Новогодний Набор (Бесплатно)"},
                {12, Material.LIME_DYE,            "pegas",   "&#22C55E&lКит PEGAS (19₽)"},
                {13, Material.RABBIT_FOOT,         "bunny",   "&#EC4899&lКит BUNNY (59₽)"},
                {14, Material.REDSTONE,            "vampire", "&#EF4444&lКит VAMPIRE (99₽)"},
                {15, Material.GOLDEN_APPLE,        "god",     "&#FACC15&l⚡ Кит GOD (149₽)"},
                {16, Material.GOLDEN_HELMET,       "king",    "&#A855F7&l👑 Кит KING (199₽)"},
                {19, Material.SPYGLASS,            "media",   "&#FF0050&l🎬 Кит MEDIA / Блогера"},
                {20, Material.SHIELD,              "kp",      "&#10B981&l🛡 Кит КП (Персонал)"},
                {21, Material.DIAMOND_CHESTPLATE,  "dmoder",  "&#14B8A6&lКит D.MODER (249₽)"},
                {22, Material.NETHERITE_AXE,       "dadmin",  "&#DC2626&lКит D.ADMIN (799₽)"},
                {23, Material.BLAZE_ROD,           "dkp",     "&#FF6D00&l⚡ Кит ДКП"},
                {24, Material.DRAGON_BREATH,       "opka",    "&#C026D3&l🔥 Кит OPKA (999₽)"},
                {25, Material.NETHER_STAR,         "downer",  "&#FACC15&l👑 Кит D.OWNER (1 499₽)"},
                {31, Material.EMERALD,             "curator", "&#22C55E&l☘ Кит CURATOR"}
        };
        for (Object[] row : ks) {
            inv.setItem(((Integer) row[0]).intValue(), ItemFactory.glowIcon((Material) row[1], (String) row[3],
                    "&7Команда: &f/kit " + row[2], "", "&#00E676▸ Нажми, чтобы получить!"));
        }
        p.openInventory(inv);
    }

    // ================= 9. AUCTION HOUSE (/ah) & CLANS TOP (/clan top) =================
    public void openAuctionMenu(Player p, int page) {
        List<DataStore.AuctionLot> lots = plugin.getStore().getAuctionLots();
        int maxPage = Math.max(0, (lots.size() - 1) / 36);
        int pg = Math.max(0, Math.min(page, maxPage));
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("ah", "", pg), 54,
                ColorUtil.c("&#FFD740&l⚖ АУКЦИОН ИГРОКОВ &8(Стр. " + (pg + 1) + ")"));
        ItemStack bar = ItemFactory.icon(Material.BLACK_STAINED_GLASS_PANE, "&8•");
        for (int s = 36; s < 54; s++) inv.setItem(s, bar);

        int start = pg * 36;
        for (int i = 0; i < 36 && (start + i) < lots.size(); i++) {
            DataStore.AuctionLot lot = lots.get(start + i);
            ItemStack disp = lot.item.clone();
            org.bukkit.inventory.meta.ItemMeta m = disp.getItemMeta();
            if (m != null) {
                List<String> lore = m.hasLore() ? new ArrayList<String>(m.getLore()) : new ArrayList<String>();
                lore.add(ColorUtil.c("&8&m                        "));
                lore.add(ColorUtil.c("&7Лот: &f#" + lot.id));
                lore.add(ColorUtil.c("&7Продавец: &e" + lot.seller));
                lore.add(ColorUtil.c("&7Цена: &#00E676" + ColorUtil.fmtMoney(lot.price) + "$"));
                lore.add(ColorUtil.c(lot.seller.equalsIgnoreCase(p.getName())
                        ? "&e▸ Нажми, чтобы снять свой лот!"
                        : "&#00E676▸ Нажми ЛКМ, чтобы купить!"));
                m.setLore(lore);
                disp.setItemMeta(m);
            }
            inv.setItem(i, disp);
        }
        if (pg > 0) inv.setItem(45, ItemFactory.icon(Material.ARROW, "&e← Предыдущая страница"));
        inv.setItem(49, ItemFactory.glowIcon(Material.GOLD_BLOCK, "&#FFD740&lКак выставить предмет?",
                "&fВозьми предмет в руку и напиши:", "&#00E676/ah sell <цена>"));
        if (pg < maxPage) inv.setItem(53, ItemFactory.icon(Material.ARROW, "&eСледующая страница →"));
        p.openInventory(inv);
    }

    public void openClanTopMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("clantop", "", 0), 36,
                ColorUtil.c("&#B388FF&l🏰 ТОП КЛАНОВ СЕРВЕРА"));
        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);
        List<DataStore.Clan> all = new ArrayList<DataStore.Clan>(plugin.getStore().getAllClans());
        Collections.sort(all, new Comparator<DataStore.Clan>() {
            @Override
            public int compare(DataStore.Clan a, DataStore.Clan b) {
                return Integer.compare(b.kills, a.kills);
            }
        });
        int[] slots = new int[]{10,11,12,13,14,15,16, 19,20,21,22,23,24,25};
        for (int i = 0; i < all.size() && i < slots.length; i++) {
            DataStore.Clan c = all.get(i);
            inv.setItem(slots[i], ItemFactory.glowIcon(Material.SHIELD,
                    "&#FFD740#" + (i + 1) + " &8[&#B388FF" + c.tag + "&8]",
                    "&7Лидер: &f" + c.leader,
                    "&7Участников: &a" + c.members.size(),
                    "&7Убийств клана: &c" + c.kills,
                    "&7Казна клана: &e" + ColorUtil.fmtMoney(c.bank) + "$"));
        }
        p.openInventory(inv);
    }

    // ================= 10. ALL 11 CASES MENU & SPINNING ROULETTE =================
    public void openAllCasesMenu(Player p) {
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("cases", "", 0), 45,
                ColorUtil.c("&#FFD740&l🎁 ВСЕ 11 КЕЙСОВ KINGGRIEF"));
        fillBorder(inv, Material.PURPLE_STAINED_GLASS_PANE);
        int[] slots = new int[]{10,11,12,13,14,15,16, 20,21,23,24};
        for (int i = 0; i < SpawnEngine.CASES.size() && i < slots.length; i++) {
            SpawnEngine.CasePedestal cp = SpawnEngine.CASES.get(i);
            int keys = plugin.getStore().getCaseKeys(p.getName(), cp.id);
            inv.setItem(slots[i], ItemFactory.glowIcon(cp.icon, cp.title,
                    cp.dropLine, cp.priceLine, "",
                    "&fВаши ключи: &#00E676" + keys + " шт.",
                    "&#00E676▸ ЛКМ — Открыть рулетку!",
                    "&e▸ ПКМ — Посмотреть призы!"));
        }
        inv.setItem(40, ItemFactory.glowIcon(Material.EMERALD_BLOCK, "&#00E676&l🌐 Купить ключи от 19₽",
                "&fhttps://kinggrief.vercel.app/cases"));
        p.openInventory(inv);
    }

    public List<CasePrize> getPrizesForCase(String caseId) {
        List<CasePrize> list = new ArrayList<CasePrize>();
        String c = caseId.toLowerCase();
        if ("money".equals(c)) {
            list.add(new CasePrize("&#22C55E💰 75 000$ монет", Material.GOLD_NUGGET, "coins", "75000", 40));
            list.add(new CasePrize("&#22C55E💰 200 000$ монет", Material.GOLD_INGOT, "coins", "200000", 30));
            list.add(new CasePrize("&#FFD740💰 750 000$ монет", Material.GOLD_BLOCK, "coins", "750000", 20));
            list.add(new CasePrize("&#FF1744💎 ДЖЕКПОТ 5 000 000$!", Material.EMERALD_BLOCK, "coins", "5000000", 10));
        } else if ("riliks".equals(c)) {
            list.add(new CasePrize("&#00E5FF❖ 300 риликов", Material.SUNFLOWER, "relics", "300", 40));
            list.add(new CasePrize("&#00E5FF❖ 750 риликов", Material.AMETHYST_SHARD, "relics", "750", 30));
            list.add(new CasePrize("&#FFD740❖ 2 500 риликов", Material.DIAMOND, "relics", "2500", 20));
            list.add(new CasePrize("&#FF1744★ ДЖЕКПОТ 15 000 РИЛИКОВ!", Material.NETHER_STAR, "relics", "15000", 10));
        } else if ("ditems".equals(c)) {
            list.add(new CasePrize("&#FFEE58✨ Божья Аура ×4", Material.BLAZE_POWDER, "item", "aura", 30));
            list.add(new CasePrize("&#A855F7⛓ Трапка 5×5 ×4", Material.NETHERITE_SCRAP, "item", "trapka", 28));
            list.add(new CasePrize("&#E040FB🔮 Сфера Андромеды", Material.TOTEM_OF_UNDYING, "item", "andromeda", 22));
            list.add(new CasePrize("&#FF6D00🔥 Талисман Крушителя", Material.TOTEM_OF_UNDYING, "item", "krush", 20));
        } else if ("kits".equals(c)) {
            list.add(new CasePrize("&#EF4444🛡 Кит VAMPIRE", Material.REDSTONE, "kit", "vampire", 35));
            list.add(new CasePrize("&#FACC15⚡ Кит GOD", Material.GOLDEN_APPLE, "kit", "god", 30));
            list.add(new CasePrize("&#A855F7👑 Кит KING", Material.GOLDEN_HELMET, "kit", "king", 25));
            list.add(new CasePrize("&#C026D3🔥 Кит OPKA", Material.DRAGON_BREATH, "kit", "opka", 10));
        } else if ("sdonate".equals(c) || "reallydev".equals(c)) {
            list.add(new CasePrize("&#FACC15⚡ Привилегия GOD", Material.GOLDEN_APPLE, "rank", "god", 30));
            list.add(new CasePrize("&#A855F7👑 Привилегия KING", Material.GOLDEN_HELMET, "rank", "king", 28));
            list.add(new CasePrize("&#DC2626🔨 Привилегия D.ADMIN", Material.NETHERITE_AXE, "rank", "dadmin", 20));
            list.add(new CasePrize("&#C026D3🔥 Привилегия OPKA", Material.DRAGON_BREATH, "rank", "opka", 14));
            list.add(new CasePrize("&#FACC15👑 Привилегия D.OWNER", Material.NETHER_STAR, "rank", "downer", 8));
        } else {
            // donate, legendary, allorno, djamshut, summer
            list.add(new CasePrize("&#22C55EПривилегия PEGAS", Material.LIME_DYE, "rank", "pegas", 25));
            list.add(new CasePrize("&#EC4899Привилегия BUNNY", Material.RABBIT_FOOT, "rank", "bunny", 22));
            list.add(new CasePrize("&#EF4444Привилегия VAMPIRE", Material.REDSTONE, "rank", "vampire", 18));
            list.add(new CasePrize("&#FACC15⚡ Привилегия GOD", Material.GOLDEN_APPLE, "rank", "god", 15));
            list.add(new CasePrize("&#A855F7👑 Привилегия KING", Material.GOLDEN_HELMET, "rank", "king", 12));
            list.add(new CasePrize("&#80D8FF❄ Талисман Мерзлоты + 300 ❖", Material.SNOWBALL, "item", "frost_combo", 8));
        }
        return list;
    }

    public void openCasePreview(Player p, String caseId) {
        List<CasePrize> prizes = getPrizesForCase(caseId);
        Inventory inv = Bukkit.createInventory(new KgMenuHolder("case_preview", caseId, 0), 27,
                ColorUtil.c("&#FFD740&lПРИЗЫ КЕЙСА: " + caseId.toUpperCase()));
        fillBorder(inv, Material.GRAY_STAINED_GLASS_PANE);
        int slot = 10;
        for (CasePrize pr : prizes) {
            if (slot > 16) break;
            inv.setItem(slot++, ItemFactory.glowIcon(pr.icon, pr.display, "&7Возможная награда из этого кейса!"));
        }
        inv.setItem(22, ItemFactory.icon(Material.ARROW, "&e← Все кейсы (/cases)"));
        p.openInventory(inv);
    }

    public void trySpinCase(final Player p, final String caseId) {
        int keys = plugin.getStore().getCaseKeys(p.getName(), caseId);
        if (keys <= 0) {
            ColorUtil.sound(p, "entity.villager.no", 1f, 1f);
            p.sendMessage(ColorUtil.c("&cУ вас нет ключей от кейса &e" + caseId.toUpperCase()
                    + "&c! Купите от 19₽ на &#FFD740https://kinggrief.vercel.app/cases &cили введите &a/promo NEWYEAR"));
            openCasePreview(p, caseId);
            return;
        }
        plugin.getStore().addCaseKeys(p.getName(), caseId, -1);
        plugin.getStore().save();

        final List<CasePrize> pool = getPrizesForCase(caseId);
        final Inventory spinInv = Bukkit.createInventory(new KgMenuHolder("case_spin", caseId, 0), 27,
                ColorUtil.c("&#FFD740&l🎰 КРУТИТСЯ КЕЙС..."));
        fillBorder(spinInv, Material.BLACK_STAINED_GLASS_PANE);
        spinInv.setItem(4, ItemFactory.glowIcon(Material.HOPPER, "&#FFD740&l▼ ВАШ ВЫИГРЫШ ▼"));
        spinInv.setItem(22, ItemFactory.glowIcon(Material.REDSTONE_TORCH, "&#FFD740&l▲ ВАШ ВЫИГРЫШ ▲"));
        p.openInventory(spinInv);

        final List<CasePrize> strip = new ArrayList<CasePrize>(30);
        for (int i = 0; i < 30; i++) {
            strip.add(pickWeighted(pool));
        }
        final CasePrize winner = strip.get(22);

        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (!p.isOnline()) {
                    cancel();
                    grantCasePrize(p, caseId, winner);
                    return;
                }
                for (int col = 0; col < 7; col++) {
                    CasePrize pr = strip.get((step + col) % strip.size());
                    spinInv.setItem(10 + col, ItemFactory.glowIcon(pr.icon, pr.display));
                }
                ColorUtil.sound(p, "block.note_block.hat", 0.7f, 0.9f + (step * 0.03f));
                step++;
                if (step > 19) {
                    cancel();
                    grantCasePrize(p, caseId, winner);
                }
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    private CasePrize pickWeighted(List<CasePrize> pool) {
        int sum = 0;
        for (CasePrize c : pool) sum += c.weight;
        int r = rng.nextInt(Math.max(1, sum));
        int acc = 0;
        for (CasePrize c : pool) {
            acc += c.weight;
            if (r < acc) return c;
        }
        return pool.get(0);
    }

    private void grantCasePrize(Player p, String caseId, CasePrize win) {
        if ("coins".equals(win.type)) {
            plugin.getStore().addCoins(p.getName(), Double.parseDouble(win.value));
        } else if ("relics".equals(win.type)) {
            plugin.getStore().addRelics(p.getName(), Integer.parseInt(win.value));
        } else if ("rank".equals(win.type)) {
            RankManager.RankDef cur = plugin.getRanks().getRank(p);
            RankManager.RankDef won = plugin.getRanks().byId(win.value);
            if (won.weight > cur.weight) {
                plugin.getRanks().setRank(p.getName(), won.id, true);
            } else {
                plugin.getStore().addRelics(p.getName(), 250);
                p.sendMessage(ColorUtil.c("&#00E5FF❖ &7У вас уже есть ранг выше — начислена компенсация &b+250 риликов&7!"));
            }
        } else if ("kit".equals(win.type)) {
            ItemFactory.giveKitItems(p, win.value);
        } else if ("item".equals(win.type)) {
            if ("aura".equals(win.value)) p.getInventory().addItem(ItemFactory.godAura(4));
            else if ("trapka".equals(win.value)) p.getInventory().addItem(ItemFactory.trapka(4));
            else if ("andromeda".equals(win.value)) p.getInventory().addItem(ItemFactory.sphereAndromeda());
            else if ("krush".equals(win.value)) p.getInventory().addItem(ItemFactory.talismanKrush());
            else {
                p.getInventory().addItem(ItemFactory.talismanFrost());
                plugin.getStore().addRelics(p.getName(), 300);
            }
        }
        plugin.getStore().save();
        if (p.isOnline()) {
            ColorUtil.sound(p, "ui.toast.challenge_complete", 1.0f, 1.1f);
            ColorUtil.spawnFirework(p.getLocation());
            plugin.getHud().updatePlayerHud(p);
        }
        Bukkit.broadcastMessage(ColorUtil.c("&#FFD740🎁 &fИгрок &#00E676" + p.getName()
                + " &fоткрыл кейс &e" + caseId.toUpperCase() + " &fи выбил: " + win.display + "&f!"));
    }

    public Location findSafeRtpLocation(World w, int minR, int maxR) {
        for (int tries = 0; tries < 25; tries++) {
            int x = (rng.nextBoolean() ? 1 : -1) * (minR + rng.nextInt(Math.max(10, maxR - minR)));
            int z = (rng.nextBoolean() ? 1 : -1) * (minR + rng.nextInt(Math.max(10, maxR - minR)));
            w.getChunkAt(x >> 4, z >> 4).load(true);
            int y = w.getHighestBlockYAt(x, z);
            if (y <= w.getMinHeight() + 2) continue;
            Material ground = w.getBlockAt(x, y, z).getType();
            if (ground == Material.WATER || ground == Material.LAVA || ground == Material.MAGMA_BLOCK || ground == Material.CACTUS) {
                continue;
            }
            return new Location(w, x + 0.5, y + 1.2, z + 0.5);
        }
        int fx = minR + 50, fz = minR + 50;
        w.getChunkAt(fx >> 4, fz >> 4).load(true);
        return new Location(w, fx + 0.5, w.getHighestBlockYAt(fx, fz) + 1.2, fz + 0.5);
    }
}
