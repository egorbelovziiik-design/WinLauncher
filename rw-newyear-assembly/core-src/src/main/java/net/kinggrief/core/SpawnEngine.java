package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class SpawnEngine {

    public static final class CasePedestal {
        public final String caseId;
        public final String title;
        public final String subtitle;
        public final int priceRub;
        public final int x, y, z;
        public final Material shulkerMat;

        public CasePedestal(String caseId, String title, String subtitle, int priceRub, int x, int y, int z, Material shulkerMat) {
            this.caseId = caseId;
            this.title = title;
            this.subtitle = subtitle;
            this.priceRub = priceRub;
            this.x = x;
            this.y = y;
            this.z = z;
            this.shulkerMat = shulkerMat;
        }
    }

    public static final class NpcDef {
        public final String id;
        public final String line1;
        public final String line2;
        public final double x, y, z;
        public final float yaw;
        public final String skinHash;
        public final Color armorColor;
        public final ItemStack mainHand;
        public final ItemStack offHand;
        public final ItemStack chestOverride;
        public final String commandOrAction;

        public NpcDef(String id, String line1, String line2, double x, double y, double z, float yaw,
                      String skinHash, Color armorColor, ItemStack mainHand, ItemStack offHand,
                      ItemStack chestOverride, String commandOrAction) {
            this.id = id;
            this.line1 = line1;
            this.line2 = line2;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.skinHash = skinHash;
            this.armorColor = armorColor;
            this.mainHand = mainHand;
            this.offHand = offHand;
            this.chestOverride = chestOverride;
            this.commandOrAction = commandOrAction;
        }
    }

    public static final class KabinkaState {
        public final int index;
        public final boolean is2v2;
        public final int minX, maxX, minZ, maxZ;
        public final int doorX, doorZ;
        public boolean locked = false;
        public int lootLockSec = 0;
        public UUID statusStandId;
        public String lastHoloText = "";

        public KabinkaState(int index, boolean is2v2, int minX, int maxX, int minZ, int maxZ, int doorX, int doorZ) {
            this.index = index;
            this.is2v2 = is2v2;
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.doorX = doorX;
            this.doorZ = doorZ;
        }

        public boolean contains(Location l) {
            if (l == null || l.getWorld() == null) return false;
            if (!l.getWorld().getName().equalsIgnoreCase(VoidSpawnWorldGenerator.getSpawnWorldName())) return false;
            int bx = l.getBlockX(), by = l.getBlockY(), bz = l.getBlockZ();
            return bx >= minX && bx <= maxX && by >= 99 && by <= 104 && bz >= minZ && bz <= maxZ;
        }
    }

    // 10 Secret New Year Present Blocks across all 7 islands (/gifts) — ZERO LivingEntities!
    public static final int[][] GIFT_BLOCKS = new int[][]{
            {0, 100, 24},      // #0 South Tree Base
            {-31, 100, 21},    // #1 Near Giant Snowman
            {0, 100, 74},      // #2 Inside Royal Winter Palace
            {-26, 102, -54},   // #3 North Shulker Case Terrace
            {41, 100, -21},    // #4 Parkour Start Terrace
            {63, 100, 34},     // #5 Gladiator PvP South Deck
            {80, 100, -24},    // #6 Between 1v1 and 2v2 Kabinki
            {-70, 100, 13},    // #7 Crystal Auto-Mine Deck
            {-74, 100, 68},    // #8 Grand Casino 777 Dome
            {75, 100, 68}      // #9 Bafovar Alchemy Pavilion
    };

    private static final class StaticHoloDef {
        final String id;
        final double x, y, z;
        final String text;
        StaticHoloDef(String id, double x, double y, double z, String text) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.text = text;
        }
    }

    private final KingGriefCore plugin;
    private final Random rng = new Random();
    private final List<CasePedestal> casePedestals = new ArrayList<CasePedestal>();
    private final List<NpcDef> npcDefs = new ArrayList<NpcDef>();
    private final List<StaticHoloDef> staticHoloDefs = new ArrayList<StaticHoloDef>();
    private final List<KabinkaState> kabinki = new ArrayList<KabinkaState>();
    private final Map<String, NpcDisplayModel> npcModels = new HashMap<String, NpcDisplayModel>();
    private final Map<String, UUID> dpsDummyMap = new HashMap<String, UUID>();
    // v6.3.0: планировщик «волн рукой» живых NPC + отдельный быстрый таск анимации
    private final Map<String, Integer> npcWaveSwings = new HashMap<String, Integer>();
    private final Map<String, Long> npcNextWaveAt = new HashMap<String, Long>();
    private BukkitRunnable npcAnimTask;
    private final Map<String, UUID> npcHitboxMap = new HashMap<String, UUID>();
    private final Map<String, UUID> npcHoloMap = new HashMap<String, UUID>();
    private final Map<String, UUID> staticHoloMap = new HashMap<String, UUID>();
    private final Set<UUID> validSpawnEntities = new HashSet<UUID>();
    private final String sessionTag = "kg_sess_" + UUID.randomUUID().toString().replace("-", "");
    private boolean loopsStarted = false;
    private final Map<String, ItemStack> cachedSkinHeads = new HashMap<String, ItemStack>();
    private final Set<String> spinningCases = new HashSet<String>();
    private final Map<String, UUID> activeCaseDisplays = new HashMap<String, UUID>();
    private final List<UUID> topHoloIds = new ArrayList<UUID>();
    private int mineRefillCountdown = 300; // v6.1: регенерация шахты каждые 5 минут!
    private int clearLagCountdown = 300;
    private int tickCounter = 0;
    private boolean spawnActive = false;
    private BukkitTask pendingSpawnSweep;
    private long lastSecondWallMs = System.currentTimeMillis();
    private double measuredTps = 20.0;

    public SpawnEngine(KingGriefCore plugin) {
        this.plugin = plugin;
        initDefinitions();
    }

    private void initDefinitions() {
        // 11 Colored Shulker Case Pedestals on Northern Royal Terrace (Y=103 in spawn_v5.dat!)
        casePedestals.add(new CasePedestal("donate",    "&#FF1744&l🎁 ДОНАТ КЕЙС",        "&7Привилегии PEGAS → D.OWNER!",   29, -30, 103, -58, Material.RED_SHULKER_BOX));
        casePedestals.add(new CasePedestal("sdonate",   "&#E040FB&l👑 СУПЕР-ДОНАТ КЕЙС",  "&7Шанс на KING, OPKA, D.OWNER!",  99, -24, 103, -63, Material.MAGENTA_SHULKER_BOX));
        casePedestals.add(new CasePedestal("legendary", "&#FFD740&l⭐ ЛЕГЕНДАРНЫЙ КЕЙС",  "&73D Шары, Элитры и Донат!",      59, -18, 103, -67, Material.YELLOW_SHULKER_BOX));
        casePedestals.add(new CasePedestal("reallydev", "&#A855F7&l⚡ КЕЙС REALLYDEV",    "&7Топ ДКП, OPKA и Шар Санты!",   129, -12, 103, -70, Material.PURPLE_SHULKER_BOX));
        casePedestals.add(new CasePedestal("riliks",    "&#00E5FF&l❖ КЕЙС РИЛИКОВ",       "&7От 60 до 2 500 Риликов ❖!",     39,  -6, 103, -72, Material.LIGHT_BLUE_SHULKER_BOX));
        casePedestals.add(new CasePedestal("allorno",   "&#FF5252&l🎲 ВСЁ ИЛИ НИЧЕГО",    "&750/50 — Мега-Джекпот или Уголь!",45,   0, 103, -73, Material.BLACK_SHULKER_BOX));
        casePedestals.add(new CasePedestal("ditems",    "&#FF9100&l🔮 3D ДОНАТ-ВЕЩИ",     "&7Шары, Элитры, Меч Крушителя!",  49,   6, 103, -72, Material.ORANGE_SHULKER_BOX));
        casePedestals.add(new CasePedestal("kits",      "&#26C6DA&l📦 КЕЙС КИТОВ",        "&7Полные шалкеры ресурсов!",      35,  12, 103, -70, Material.CYAN_SHULKER_BOX));
        casePedestals.add(new CasePedestal("money",     "&#69F0AE&l💰 ДЕНЕЖНЫЙ КЕЙС",     "&7До 1 000 000$ игровых монет!",  19,  18, 103, -67, Material.LIME_SHULKER_BOX));
        casePedestals.add(new CasePedestal("djamshut",  "&#FFAB40&l⛏ КЕЙС ДЖАМШУТА",     "&7Бульдозер 3×3 и Божье Прикосн.",25,  24, 103, -63, Material.BROWN_SHULKER_BOX));
        casePedestals.add(new CasePedestal("summer",    "&#80D8FF&l❄ НОВОГОДНИЙ КЕЙС",    "&7Зимние 3D артефакты и подарки!",39,  30, 103, -58, Material.WHITE_SHULKER_BOX));

        String sSanta  = "fb8f9a667dc8c53f9d4fb8ca428dc0bf97c46f76ca4e22e307d5771856c5015b";
        String sMaiden = "79b0b10d5d4e21a2a28ce331ad7c45e0ba2ef01b86d42dcbb0a82ef8f86bd8d8";
        String sKing   = "2340c0e03dd24a11b15a8b33c2a7e9e32ab205232b00a9e7794e7682ba8c92ab";
        String sKnight = "79f1790d319c16c73b0df15f6c3c6bf992978d4cb4b756a8146d3d2f2b7ad69e";
        String sMage   = "e8a9c63f0e58966b4c806a5fc6a26f73774c8f6be2a8f7b8e144b912437f5be6";
        String sMiner  = "8b1e214450d341bb9998d6822f1cf9ea0aa6cc90bf578bb6d090f2e1010537bf";
        String sPirate = "a75e3f60d7e3c3c94871aa4d960f9c92a29d7f55551a8899622857ff79a5cf22";
        String sDealer = "5d9b9585722959d782b770f15f63bc64a6c27dc0b50e6cf74a98817b8a0156ab";

        // 27 existing plaza NPCs plus the expedition guide near the entrance.
        npcDefs.add(new NpcDef("rtp",        "&#00E676&l🧭 МАСТЕР РТП",          "&fВход в мир выживания (/rtp)",         -8.0, 100.0,  42.0, -35f, sKnight, Color.fromRGB(16, 185, 129), new ItemStack(Material.COMPASS), ItemFactory.sharApollona(), null, "gui:rtp"));
        npcDefs.add(new NpcDef("donate",     "&#FFD740&l👑 ДОНАТ И ПРИВИЛЕГИИ",  "&fВсе 45 привилегий (/donate)",          8.0, 100.0,  42.0,  35f, sKing,   Color.fromRGB(250, 204, 21), new ItemStack(Material.NETHER_STAR), ItemFactory.sharSanty(), null, "gui:donate"));
        npcDefs.add(new NpcDef("wheel_npc",  "&#FF4081&l🎡 КОЛЕСО ФОРТУНЫ",      "&fБеспроигрышный прокрут (/wheel)",      0.5, 100.0,  36.5,   0f, sMaiden, Color.fromRGB(244, 63, 94),  ItemFactory.snowflakeCoin(1), ItemFactory.sharSanty(), null, "action:wheel"));
        npcDefs.add(new NpcDef("shop_main",  "&#38BDF8&l🛒 ГЛАВНЫЙ МАГАЗИН",     "&f8 категорий за Монеты (/shop)",      -13.0, 100.0,  34.0, -55f, sDealer, Color.fromRGB(56, 189, 248), new ItemStack(Material.EMERALD), new ItemStack(Material.GOLDEN_CARROT), null, "gui:shop"));
        npcDefs.add(new NpcDef("rshop",      "&#00E5FF&l❖ МАГАЗИН РИЛИКОВ",      "&f3D Шары, Элитры, Талисманы (/rshop)", 13.0, 100.0,  34.0,  55f, sMage,   Color.fromRGB(14, 165, 233), ItemFactory.swordKrush(), ItemFactory.sharTitana(), ItemFactory.elytraSanta(), "gui:rshop"));
        npcDefs.add(new NpcDef("bafovar",    "&#D946EF&l⚗ АЛХИМИК БАФОВАР",      "&fСмешивание 8 зелий — 5 000$ (/bafs)", -16.0, 100.0,  25.0, -70f, sMage,   Color.fromRGB(192, 38, 211), ItemFactory.potionKiller(1), new ItemStack(Material.BREWING_STAND), null, "gui:bafovar"));
        npcDefs.add(new NpcDef("skins",      "&#F43F5E&l⛓ СКИНЫ ТРАПКИ И ПЛАСТА","&fОкна в Трапках и Пласты (/skins)",    16.0, 100.0,  25.0,  70f, sPirate, Color.fromRGB(225, 29, 72),  ItemFactory.trapka(1), ItemFactory.plast(1), null, "gui:skins"));
        npcDefs.add(new NpcDef("kits",       "&#38BDF8&l📦 НАБОРЫ КИТОВ",        "&fСтрого по привилегии (/kits)",       -16.0, 100.0,  14.0, -85f, sKnight, Color.fromRGB(59, 130, 246), new ItemStack(Material.CHEST), new ItemStack(Material.ENDER_PEARL), null, "gui:kits"));
        npcDefs.add(new NpcDef("buyer",      "&#69F0AE&l💰 СКУПЩИК РЕСУРСОВ",    "&fПродавай лут за Монеты (/buyer)",     16.0, 100.0,  14.0,  85f, sDealer, Color.fromRGB(34, 197, 94),  new ItemStack(Material.GOLD_INGOT), new ItemStack(Material.DIAMOND), null, "gui:buyer"));
        npcDefs.add(new NpcDef("ah",         "&#FB923C&l⚖ АУКЦИОН ИГРОКОВ",     "&fТорговля между игроками (/ah)",      -14.0, 100.0, -15.0,-140f, sDealer, Color.fromRGB(249, 115, 22), new ItemStack(Material.GOLD_BLOCK), null, null, "gui:ah"));
        npcDefs.add(new NpcDef("bounty",     "&#EF4444&l💀 ОХОТА ЗА ГОЛОВАМИ",   "&fНаграды за убийство (/bounty)",       14.0, 100.0, -15.0, 140f, sPirate, Color.fromRGB(185, 28, 28),  ItemFactory.swordDracula(), new ItemStack(Material.SKELETON_SKULL), null, "gui:bounty"));
        npcDefs.add(new NpcDef("santa",      "&#FF1744&l🎅 ДЕДУШКА МОРОЗ",       "&fЕжедневные подарки (/daily)",         -8.0, 100.0,  19.0, -25f, sSanta,  Color.fromRGB(220, 38, 38),  ItemFactory.nyMandarin(1), ItemFactory.sharSanty(), null, "action:santa"));
        npcDefs.add(new NpcDef("snowmaiden", "&#80D8FF&l❄ СНЕГУРОЧКА",           "&fХалява, Подарки 10/10 и Промо (/free)",8.0, 100.0,  19.0,  25f, sMaiden, Color.fromRGB(56, 189, 248), ItemFactory.snowflakeCoin(1), new ItemStack(Material.TOTEM_OF_UNDYING), null, "gui:freebie"));

        // 6 Category Merchant Stalls (with open plazas in front and festive canopies behind!)
        npcDefs.add(new NpcDef("sh_pvp",     "&#FF1744&l⚔ ПВП РАСХОДНИКИ",      "&fПласт, Трапка, Пыль, Смерч",          25.0, 100.0,  12.0,  90f, sKnight, Color.fromRGB(220, 38, 38),  ItemFactory.trapka(1), ItemFactory.plast(1), null, "shop:pvp"));
        npcDefs.add(new NpcDef("sh_gear",    "&#38BDF8&l🛡 ОРУЖИЕ И БРОНЯ",      "&fНезерит, Чарки, Перки, Тотемы",       25.0, 100.0,   5.0,  90f, sKnight, Color.fromRGB(37, 99, 235),  new ItemStack(Material.NETHERITE_SWORD), new ItemStack(Material.TOTEM_OF_UNDYING), null, "shop:gear"));
        npcDefs.add(new NpcDef("sh_raid",    "&#FF9100&l💣 РЕЙД И ГРИФ",         "&fКастом ТНТ, Стрелы, Обсидиан",       -25.0, 100.0,   9.0, -90f, sPirate, Color.fromRGB(234, 88, 12),  ItemFactory.tntNuke(1), new ItemStack(Material.OBSIDIAN), null, "shop:raid"));
        npcDefs.add(new NpcDef("sh_blocks",  "&#A3E635&l🧱 СТРОЙБЛОКИ",          "&fКварц, Бетон, Стекло, Лёд",          -25.0, 100.0,  -1.0, -90f, sMiner,  Color.fromRGB(101, 163, 13), new ItemStack(Material.SMOOTH_QUARTZ), null, null, "shop:blocks"));
        npcDefs.add(new NpcDef("sh_food",    "&#FACC15&l🥕 ЕДА И ФЕРМА",         "&fЗолотая Морковь, Гэплы, Опыт",       -23.0, 100.0,  28.0, -45f, sMaiden, Color.fromRGB(234, 179, 8),  new ItemStack(Material.GOLDEN_CARROT), new ItemStack(Material.GOLDEN_APPLE), null, "shop:food"));
        npcDefs.add(new NpcDef("sh_elytra",  "&#C084FC&l🕊 КАСТОМНЫЕ ЭЛИТРЫ",    "&fЭлитры с баффами и ХП!",              23.0, 100.0,  28.0,  45f, sKing,   Color.fromRGB(168, 85, 247), new ItemStack(Material.FIREWORK_ROCKET), ItemFactory.sharAndromedy(), ItemFactory.elytraPhoenix(), "shop:elytra"));

        // Outer Island & New Attraction NPCs
        npcDefs.add(new NpcDef("top_npc",    "&#FFD740&l🏆 АЛЛЕЯ ТОПЕРОВ",       "&fТОП-5 Богачей и Киллеров (/top)",    -34.0, 100.0, -26.0,   0f, sKing,   Color.fromRGB(245, 158, 11), new ItemStack(Material.TOTEM_OF_UNDYING), ItemFactory.sharTitana(), null, "action:top"));
        npcDefs.add(new NpcDef("dummy_master","&#FF5252&l🥊 ТРЕНЕР УРОНА",       "&fТест критов и мечей на манекенах!",   62.0, 100.0,  -3.0,  90f, sKnight, Color.fromRGB(239, 68, 68),  ItemFactory.swordKrush(), ItemFactory.talKrushitelya(), null, "action:train_hint"));
        npcDefs.add(new NpcDef("cases_npc",  "&#FF4081&l🎁 ХРАНИТЕЛЬ КЕЙСОВ",    "&fВсе 11 Шалкер-Кейсов (/cases)",        0.5, 102.0, -53.0,   0f, sKing,   Color.fromRGB(236, 72, 153), new ItemStack(Material.TRIPWIRE_HOOK), ItemFactory.sharSanty(), null, "gui:cases"));
        npcDefs.add(new NpcDef("miner_npc",  "&#FFAB00&l⛏ ШАХТЁР ДЖАМШУТ",      "&fОбмен снежинок и Буры (/quests)",    -66.0, 100.0,   4.5, -90f, sMiner,  Color.fromRGB(245, 158, 11), ItemFactory.pickDjamshut(), ItemFactory.snowflakeCoin(1), null, "gui:djamshut"));
        npcDefs.add(new NpcDef("farm_npc",   "&#AEEA00&l⚔ СТРАЖ ФАРМИЛКИ",      "&fБезопасный фарм мобов и опыта!",     -78.0, 100.0, -34.0,   0f, sKnight, Color.fromRGB(132, 204, 22), new ItemStack(Material.DIAMOND_SWORD), null, null, "gui:jobs"));
        npcDefs.add(new NpcDef("casino_npc", "&#FFD740&l🎰 КРУПЬЕ КАЗИНО 777",   "&fРулетка, Слоты и Монетка (/casino)", -66.0, 100.0,  68.5, -90f, sDealer, Color.fromRGB(234, 179, 8),  new ItemStack(Material.GOLD_INGOT), new ItemStack(Material.EMERALD), null, "gui:casino"));
        npcDefs.add(new NpcDef("baf_island", "&#E040FB&l🔮 ЧАРОДЕЙ И АЛХИМИК",   "&fЧарки 30 лвл и Зелья Бафовара!",      66.0, 100.0,  68.5,  90f, sMage,   Color.fromRGB(168, 85, 247), new ItemStack(Material.ENCHANTED_BOOK), ItemFactory.potionMedic(1), null, "gui:enchants"));
        npcDefs.add(new NpcDef("parkour_npc","&#40C4FF&l🏃 МАСТЕР ПАРКУРА",      "&f32 острова → Приз +25 000$ и 15❖!",  35.0, 100.0, -24.0, -90f, sKnight, Color.fromRGB(14, 165, 233), new ItemStack(Material.FEATHER), null, null, "warp:parkour"));
        npcDefs.add(new NpcDef("expedition_npc", "&#80D8FF&l🧭 ПРОВОДНИК ЭКСПЕДИЦИЙ", "&f5 заданий и бонусы (/expeditions)", -12.5, 100.0, 52.5, -90f, sMiner, Color.fromRGB(30, 180, 180), new ItemStack(Material.MAP), new ItemStack(Material.COMPASS), null, "gui:expeditions"));

        // 6x 1v1 Kabinki + 2x 2v2 Kabinki on East Colosseum Wing (matching spawn_v5.dat!)
        int[] z1v1 = new int[]{-42, -35, -28, -21, -14, -7};
        for (int i = 0; i < z1v1.length; i++) {
            int zc = z1v1[i];
            kabinki.add(new KabinkaState(i + 1, false, 69, 73, zc - 1, zc + 1, 68, zc));
        }
        int[] z2v2 = new int[]{-32, -16};
        for (int i = 0; i < z2v2.length; i++) {
            int zc = z2v2[i];
            kabinki.add(new KabinkaState(7 + i, true, 83, 89, zc - 2, zc + 2, 82, zc));
        }
    }

    // ================= BUILD / REBUILD IN SEPARATE VOID WORLD "spawn_world" =================
    public boolean initializeSpawnWorldAndBuild(boolean forceRebuild) {
        File worldFolder = new File(Bukkit.getWorldContainer(), VoidSpawnWorldGenerator.getSpawnWorldName());
        boolean existedBefore = Bukkit.getWorld(VoidSpawnWorldGenerator.getSpawnWorldName()) != null
                || worldFolder.exists() || java.nio.file.Files.isSymbolicLink(worldFolder.toPath());
        if (forceRebuild) {
            plugin.getLogger().warning("[SpawnEngine] Immediate block-by-block rebuild is disabled. Use /rebuildspawn confirm and restart.");
            return false;
        }
        if (!existedBefore) {
            plugin.getLogger().severe("[SpawnEngine] No prepared spawn world is available; refusing synchronous block generation.");
            return false;
        }
        File marker = new File(worldFolder, ".spawn_v70_village_ok");
        if (!java.nio.file.Files.isRegularFile(marker.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            // An old, unknown or custom world is never loaded, decorated, cleared or rebuilt by the prepared-spawn engine.
            plugin.getLogger().warning("[SpawnEngine] Existing unmarked spawn world preserved; no blocks were rebuilt.");
            return false;
        }
        World sw = VoidSpawnWorldGenerator.getOrCreateSpawnWorld();
        if (sw == null) {
            plugin.getLogger().severe("[SpawnEngine] Could not load prepared spawn; no build attempted.");
            return false;
        }
        lockSpawnWorldRules(sw);
        // Do not synchronously generate/pin hundreds of chunks at every startup.
        // Existing administrator force-loads are deliberately preserved.

        // Old releases force-loaded this whole square and persisted those tickets.
        // They may now be administrator-owned; report rather than remove them.
        int forced = 0;
        for (int cx = -8; cx <= 8; cx++) for (int cz = -8; cz <= 8; cz++)
            if (sw.isChunkForceLoaded(cx, cz)) forced++;
        if (forced >= 128) plugin.getLogger().warning("[SpawnEngine] " + forced + " spawn chunks remain force-loaded; "
                + "review old /forceload tickets manually. Core 7.1 no longer creates or removes them.");
        placeShulkerPedestalBlocks(sw);
        placeSantaGiftBlocks(sw);

        // Reconcile only loaded chunks. Later chunk loads perform the same scoped cleanup.
        purgeAllSpawnEntities(sw);
        spawnActive = true;
        spawnLoadedChunkDecorations(sw);
        if (!loopsStarted) {
            loopsStarted = true;
            startEngineLoops();
        }
        // Delayed sweeps at +20t and +60t wipe any async 1.21 EntitiesLoadEvent leftovers from old disk saves!
        new BukkitRunnable() {
            @Override
            public void run() {
                enforceStrictSingletonsOnSpawn(Bukkit.getWorld(VoidSpawnWorldGenerator.getSpawnWorldName()));
            }
        }.runTaskLater(plugin, 20L);
        new BukkitRunnable() {
            @Override
            public void run() {
                enforceStrictSingletonsOnSpawn(Bukkit.getWorld(VoidSpawnWorldGenerator.getSpawnWorldName()));
            }
        }.runTaskLater(plugin, 60L);
        return true;
    }

    private void lockSpawnWorldRules(World sw) {
        try {
            sw.setTime(6000L);
            sw.setStorm(false);
            sw.setThundering(false);
            sw.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, Boolean.FALSE);
            sw.setGameRule(GameRule.DO_WEATHER_CYCLE, Boolean.FALSE);
            sw.setGameRule(GameRule.DO_MOB_SPAWNING, Boolean.FALSE);
            sw.setGameRule(GameRule.RANDOM_TICK_SPEED, Integer.valueOf(0));
            sw.setGameRule(GameRule.DO_FIRE_TICK, Boolean.FALSE);
            sw.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, Boolean.FALSE);
            sw.setGameRule(GameRule.DISABLE_RAIDS, Boolean.TRUE);
        } catch (Throwable ignored) {}
    }

    /** Remove only Core-owned entities in loaded chunks, never load or generate chunks here. */
    public void purgeAllSpawnEntities(World sw) {
        if (sw == null) return;
        int removed = 0;
        for (Entity e : sw.getEntities()) {
            if (e instanceof Player) continue;
            Set<String> tags = e.getScoreboardTags();
            if (tags.contains("kg_mod_anim") || tags.contains("kg_sack") || tags.contains("kg_mute_gag")) continue;
            if (!isSpawnOwnedEntity(tags)) continue;
            e.remove();
            removed++;
        }
        npcModels.clear();
        npcHitboxMap.clear();
        npcHoloMap.clear();
        dpsDummyMap.clear();
        staticHoloMap.clear();
        validSpawnEntities.clear();
        topHoloIds.clear();
        for (KabinkaState kb : kabinki) kb.statusStandId = null;
        if (removed > 0) plugin.getLogger().info("[SpawnEngine] Removed " + removed + " stale loaded spawn entities.");
    }

    /** Reconcile canonical models and decorations in loaded chunks only. */
    public void enforceStrictSingletonsOnSpawn(World sw) {
        if (!spawnActive || sw == null) return;
        for (Entity e : sw.getEntities()) {
            if (e instanceof Player) continue;
            Set<String> tags = e.getScoreboardTags();
            if (tags.contains("kg_mod_anim") || tags.contains("kg_sack") || tags.contains("kg_mute_gag")) continue;
            if ((tags.contains("kg_npc") || tags.contains("kg_holo") || tags.contains("kg_dps_dummy"))
                    && (!validSpawnEntities.contains(e.getUniqueId()) || !tags.contains(sessionTag))) e.remove();
        }
        placeShulkerPedestalBlocks(sw);
        placeSantaGiftBlocks(sw);
        ensureStaticHolosAlive(sw);
        ensureDpsDummies(sw);
        for (NpcDef nd : npcDefs) {
            int cx = ((int) Math.floor(nd.x)) >> 4;
            int cz = ((int) Math.floor(nd.z)) >> 4;
            if (sw.isChunkLoaded(cx, cz)) ensureNpcAlive(sw, nd);
        }
        if (topHologramNeedsSpawn(sw)) refreshTopLeaderboards(sw);
    }

    private boolean isSpawnOwnedEntity(Set<String> tags) {
        return tags.contains("kg_npc") || tags.contains("kg_holo") || tags.contains("kg_top_holo")
                || tags.contains("kg_dps_dummy") || tags.contains("kg_farm_mob") || tags.contains("kg_case_float");
    }

    private void placeShulkerPedestalBlocks(World sw) {
        for (CasePedestal cp : casePedestals) {
            if (!sw.isChunkLoaded(cp.x >> 4, cp.z >> 4)) continue;
            Block b = sw.getBlockAt(cp.x, cp.y, cp.z);
            // v6.4.0: пьедестал не перекрывает постройку — ставим только в свободный блок.
            Material cur = b.getType();
            if (cur != Material.AIR && cur != Material.CAVE_AIR) continue;
            b.setType(cp.shulkerMat, false);
            BlockData bd = b.getBlockData();
            if (bd instanceof Directional) {
                ((Directional) bd).setFacing(BlockFace.UP);
                b.setBlockData(bd, false);
            }
        }
    }

    private void placeSantaGiftBlocks(World sw) {
        for (int i = 0; i < GIFT_BLOCKS.length; i++) {
            int gx = GIFT_BLOCKS[i][0], gy = GIFT_BLOCKS[i][1], gz = GIFT_BLOCKS[i][2];
            if (!sw.isChunkLoaded(gx >> 4, gz >> 4)) continue;
            // v6.4.0: МАЛЕНЬКИЕ подарочки — тонкий коврик вместо массивного золотого пьедестала
            // и компактная коробочка вместо огромного шалкера. Плюс постройки спавна больше НЕ
            // перекрываются: декорация ставится только в воздух или поверх своей же старой декорации.
            Block under = sw.getBlockAt(gx, gy - 1, gz);
            if (isFreeForDecoration(under)) under.setType(Material.RED_CARPET, false);
            Block b = sw.getBlockAt(gx, gy, gz);
            if (!isFreeForDecoration(b)) continue;
            b.setType(Material.CHEST, false);
            BlockData bd = b.getBlockData();
            if (bd instanceof org.bukkit.block.data.Rotatable) {
                try {
                    ((org.bukkit.block.data.Rotatable) bd).setRotation(BlockFace.SOUTH);
                    b.setBlockData(bd, false);
                } catch (Throwable ignored) {}
            }
        }
    }

    /**
     * v6.4.0: декорации спавна (подарки, пьедесталы кейсов) НЕ заходят в постройки —
     * блок можно занять, только если он пустой или это наша собственная старая декорация.
     */
    private boolean isFreeForDecoration(Block b) {
        if (b == null) return false;
        Material m = b.getType();
        return m == Material.AIR || m == Material.CAVE_AIR;
    }

    public int getSantaGiftAt(Block b) {
        if (b == null || !isInSpawnWorld(b.getLocation())) return -1;
        int bx = b.getX(), by = b.getY(), bz = b.getZ();
        for (int i = 0; i < GIFT_BLOCKS.length; i++) {
            int gx = GIFT_BLOCKS[i][0], gy = GIFT_BLOCKS[i][1], gz = GIFT_BLOCKS[i][2];
            // v6.4.0: засчитываем и сам подарочек, и коврик под ним (маленькие подарочки удобнее кликать).
            if (gx == bx && gz == bz && (gy == by || gy - 1 == by)) {
                return i;
            }
        }
        return -1;
    }

    public int getGiftIndexByEntity(Entity e) {
        return -1;
    }

    // ================= CHUNK-SAFE NON-PERSISTENT ENTITY SPAWNING (ZERO DUPLICATES!) =================
    public void onSpawnChunkLoad(Chunk c) {
        final World sw = c.getWorld();
        if (!VoidSpawnWorldGenerator.getSpawnWorldName().equalsIgnoreCase(sw.getName())) return;
        // Chunk entity slices may arrive after ChunkLoadEvent. Coalesce a burst of
        // loads into one sweep, and never spawn synchronously inside the event.
        if (pendingSpawnSweep != null) return;
        pendingSpawnSweep = Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override public void run() {
                pendingSpawnSweep = null;
                if (spawnActive && Bukkit.getWorld(sw.getUID()) != null) enforceStrictSingletonsOnSpawn(sw);
            }
        }, 5L);
    }

    private void buildStaticHoloDefsIfNeeded() {
        if (!staticHoloDefs.isEmpty()) return;

        staticHoloDefs.add(new StaticHoloDef("holo_main", 0.5, 104.2, 46.0,
                "&#80D8FF&l❄ &#FFD740&lKINGGRIEF NEW YEAR v7.2.1 &#80D8FF&l❄\n"
                + "&#FF1744&lМОДЕЛИ NPC &8• &#FFD740&l150 АРТЕФАКТОВ &8• &#00E676&lЭКСПЕДИЦИИ &8• &#E040FB&lЗИМНИЕ НАГРАДЫ\n"
                + "&fВыживание: &#00E676&l/rtp &8│ &fЭкспедиции: &#80D8FF&l/expeditions &8│ &fРуны: &#FFD740&l/artifacts &8│ &fВарпы: &#38BDF8&l/warps"));

        // ================= ГОЛОГРАММЫ СЛЕВА И СПРАВА НА СПАВНЕ (ПРИВИЛЕГИИ + КЕЙСЫ) =================
        staticHoloDefs.add(new StaticHoloDef("holo_spawn_left_privs", -7.0, 101.55, 47.5,
                "&#FFD740&l👑 ПРИВИЛЕГИИ СЕРВЕРА &#80D8FF&l(/donate)\n"
                + "&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                + "&#00E676&lDRAGON &8— &a15₽ &7(Бесплатно: &#00E676/free dragon&7)\n"
                + "&#38BDF8&lPEGAS &a19₽ &8• &#22C55E&lCOBRA &a39₽ &8• &#EF4444&lDRACULA &a49₽\n"
                + "&#F472B6&lBUNNY &a59₽ &8• &#A855F7&lVAMPIRE &a99₽ &8• &#FB923C&lTIGER &a119₽\n"
                + "&#FEF08A&lGOD &a149₽ &8• &#EC4899&lIMPERATOR &a179₽ &8• &#FACC15&lKING &a199₽\n"
                + "&#38BDF8&lSPONSOR &a215₽ &8• &#2DD4BF&lD.HELPER &a219₽ &8• &#60A5FA&lD.MODER &a249₽\n"
                + "&#F97316&lD.ADMIN &a799₽ &8• &#EF4444&lOPKA &a999₽ &8• &#FF1744&lD.OWNER &a1499₽\n"
                + "&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                + "&fКиты: &#38BDF8&l/kits &8│ &fСайт: &#FFD740&lkinggrief.vercel.app"));

        staticHoloDefs.add(new StaticHoloDef("holo_spawn_right_cases", 8.0, 101.55, 47.5,
                "&#FF4081&l🎁 11 ШАЛКЕР-КЕЙСОВ &#80D8FF&l(/cases)\n"
                + "&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                + "&#69F0AE💰 Денежный &8— &a19₽ &8│ &#FFAB40⛏ Джамшута &8— &a25₽\n"
                + "&#FF1744🎁 Донат Кейс &8— &a29₽ &8│ &#26C6DA📦 Кейс Китов &8— &a35₽\n"
                + "&#00E5FF❖ Кейс Риликов &8— &a39₽ &8│ &#80D8FF❄ Новогодний &8— &a39₽\n"
                + "&#FF5252🎲 Всё или Ничего &8— &a45₽ &8│ &#FF9100🔮 3D Донат-Вещи &8— &a49₽\n"
                + "&#FFD740⭐ Легендарный &8— &a59₽ &8│ &#E040FB👑 Супер-Донат &8— &a99₽\n"
                + "&#A855F7&l⚡ КЕЙС REALLYDEV &8— &a129₽ &7(Топ ДКП + 3D Шары!)\n"
                + "&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                + "&fАрена Кейсов: &#00E676&l/warp cases &8│ &#FFD740&lkinggrief.vercel.app"));

        staticHoloDefs.add(new StaticHoloDef("holo_palace", 0.5, 103.6, 64.2,
                "&#FFD740&l🏰 ИМПЕРАТОРСКИЙ ЗИМНИЙ ДВОРЕЦ (3 ЭТАЖА)\n&fТронный Зал &8• &bБиблиотека &8• &eСокровищница и Панорамный Балкон!"));

        staticHoloDefs.add(new StaticHoloDef("holo_mine", -71.0, 102.5, 4.5,
                "&#FF9100&l⛏ КРИСТАЛЬНАЯ АВТО-ШАХТА — РЕДКОСТИ РУДЫ\n"
                + "&f🪨 46% Камень • ⚫ 20% Уголь • ⛓ 12% Железо • 🟣 10% Золото/Лазурит/Редстоун\n"
                + "&#80D8FF💎 7% Алмаз • &#22C55E💚 4% Изумруд • &#E040FB&l🌌 1% ДРЕВНИЕ ОБЛОМКИ\n"
                + "&fБлоки ломаются по-настоящему • Регенерация каждые &e5 минут&f!"));

        staticHoloDefs.add(new StaticHoloDef("holo_farm", -80.0, 103.4, -37.5,
                "&#AEEA00&l🧟 ЗИМНЯЯ ФАРМИЛКА МОБОВ (4 КАМЕРЫ)\n&fБейте мобов через нижнее окно — полная безопасность!"));

        staticHoloDefs.add(new StaticHoloDef("holo_pvp", 64.5, 103.3, 17.5,
                "&#FF1744&l⚔ ГЛАДИАТОРСКИЙ PVP-КОЛИЗЕЙ (/warp pvp)\n&fШирокий вход и 3 лестницы &8• &eНаковальни и Эндер-Сундуки!"));

        staticHoloDefs.add(new StaticHoloDef("holo_train", 66.5, 103.0, -3.0,
                "&#FF5252&l🥊 БОЕВЫЕ МАНЕКЕНЫ УРОНА (/warp train)\n&7Бейте манекенов для замера урона мечей и критов!"));

        // ================= ГОЛОГРАММЫ ВОЗЛЕ КАБИНОК (ЦЕНТР + СЛЕВА ПРИВИЛЕГИИ + СПРАВА КЕЙСЫ + 2x2) =================
        staticHoloDefs.add(new StaticHoloDef("holo_kabinki", 66.8, 103.4, -24.5,
                "&#FF1744&l🥊 8 КАБИНОК ДЛЯ ДУЭЛЕЙ (6× 1v1 + 2× 2v2)\n"
                + "&fДвери закрываются автоматически при входе бойцов!\n"
                + "&#00E676✔ 8 сек анти-подсос лута &8• &#38BDF8🛡 Броня ×2 &8• &#E040FB⛓ Скины /skins"));

        staticHoloDefs.add(new StaticHoloDef("holo_kabinki_left_privs", 65.2, 101.55, -31.5,
                "&#FFD740&l👑 ТОП ПРИВИЛЕГИИ ДЛЯ ПВП &#80D8FF&l(/donate)\n"
                + "&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                + "&#00E676&lDRAGON &a15₽ &7(&f/free dragon&7) &8• &#A855F7&lVAMPIRE &a99₽\n"
                + "&#FEF08A&lGOD &a149₽ &8• &#EC4899&lIMPERATOR &a179₽ &7(Меч Сакуры!)\n"
                + "&#FACC15&lKING &a199₽ &7(Клинок Мороза!) &8• &#38BDF8&lSPONSOR &a215₽\n"
                + "&#F97316&lD.ADMIN &a799₽ &8• &#EF4444&lOPKA &a999₽ &8• &#FF1744&lD.OWNER &a1499₽\n"
                + "&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                + "&fВзять кит: &#00E676&l/kits &8│ &fПочинка: &#FFD740&l/repair"));

        staticHoloDefs.add(new StaticHoloDef("holo_kabinki_right_cases", 65.2, 101.55, -17.5,
                "&#FF4081&l🎁 КЕЙСЫ И 3D ОРУЖИЕ ДЛЯ КАБИНОК\n"
                + "&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                + "&#FF1744🎁 Донат Кейс &a29₽ &8• &#00E5FF❖ Кейс Риликов &a39₽\n"
                + "&#FF9100🔮 3D Донат-Вещи &a49₽ &8• &#FFD740⭐ Легендарный &a59₽\n"
                + "&#E040FB👑 Супер-Донат &a99₽ &8• &#A855F7⚡ ReallyDev &a129₽\n"
                + "&#FF5252⚔ 12 3D Мечей: &f/swords &8│ &#E040FB⛓ Скины: &f/skins\n"
                + "&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                + "&fОткрыть кейсы: &#00E676&l/cases &8│ &#FFD740&lkinggrief.vercel.app"));

        staticHoloDefs.add(new StaticHoloDef("holo_kabinki_2v2", 79.5, 102.5, -24.5,
                "&#FFD740&l⚔ ПАРНЫЕ КАБИНКИ 2×2 (КАБИНКИ #7 И #8)\n"
                + "&fЗаходите вчетвером (&e4/4&f) — двери закроются сами!\n"
                + "&7Союзники клана: &#00E676/clan &8• &7Защита лута: &#FFD7408 сек"));

        staticHoloDefs.add(new StaticHoloDef("holo_parkour", 39.0, 104.0, -29.0,
                "&#40C4FF&l🏃 НЕБЕСНЫЙ ПАРКУР (32 ОСТРОВА)\n&fВстань на золотую плиту для старта! Приз: &a+25 000$ &f+ &b15❖ &f+ &b15❄!"));

        staticHoloDefs.add(new StaticHoloDef("holo_casino", -62.0, 103.2, 68.5, "&#FFD740&l🎰 ГРАНД-КАЗИНО 777 (/warp casino)"));
        staticHoloDefs.add(new StaticHoloDef("holo_bafs", 62.0, 103.2, 68.5, "&#E040FB&l🔮 БАФОВАР И ЧАРОДЕЙ (/warp bafs)"));

        // Главная вывеска Арены Кейсов + 11 ярких голограмм прямо над каждым Шалкер-Кейсом!
        staticHoloDefs.add(new StaticHoloDef("holo_cases_hub", 0.5, 106.2, -57.5,
                "&#FFD740&l🎁 КОРОЛЕВСКАЯ АРЕНА 11 КЕЙСОВ (/cases)\n"
                + "&fНажмите &#00E676ПКМ &fпо любому шалкеру для прокрутки &8• &#80D8FFkinggrief.vercel.app"));

        for (CasePedestal cp : casePedestals) {
            staticHoloDefs.add(new StaticHoloDef("case_holo_" + cp.caseId, cp.x + 0.5, cp.y + 1.72, cp.z + 0.5,
                    cp.title + "\n"
                    + cp.subtitle + "\n"
                    + "&#FFD740На сайте: &a" + cp.priceRub + "₽ &8• &#80D8FFkinggrief.vercel.app\n"
                    + "&#00E676▸ ПКМ — Открыть &8│ &#FFE082ЛКМ — Все кейсы"));
        }

        for (int i = 0; i < GIFT_BLOCKS.length; i++) {
            staticHoloDefs.add(new StaticHoloDef("gift_holo_" + i,
                    GIFT_BLOCKS[i][0] + 0.5, GIFT_BLOCKS[i][1] + 1.35, GIFT_BLOCKS[i][2] + 0.5,
                    "&#FFD740&l🎁 Подарок Санты #" + (i + 1) + "\n&7Нажмите ПКМ по коробке!"));
        }
    }

    private void ensureStaticHolosAlive(World sw) {
        buildStaticHoloDefsIfNeeded();
        for (StaticHoloDef hd : staticHoloDefs) {
            int cx = ((int) Math.floor(hd.x)) >> 4;
            int cz = ((int) Math.floor(hd.z)) >> 4;
            if (!sw.isChunkLoaded(cx, cz)) continue;
            UUID curId = staticHoloMap.get(hd.id);
            Entity cur = curId != null ? Bukkit.getEntity(curId) : null;
            if (cur == null || cur.isDead() || !cur.isValid()) {
                if (curId != null) validSpawnEntities.remove(curId);
                Entity spawned = spawnHolo(sw, hd.x, hd.y, hd.z, hd.text);
                staticHoloMap.put(hd.id, spawned.getUniqueId());
            }
        }
        for (KabinkaState kb : kabinki) {
            int cx = kb.doorX >> 4;
            int cz = kb.doorZ >> 4;
            if (!sw.isChunkLoaded(cx, cz)) continue;
            Entity cur = kb.statusStandId != null ? Bukkit.getEntity(kb.statusStandId) : null;
            if (cur == null || cur.isDead() || !cur.isValid()) {
                if (kb.statusStandId != null) validSpawnEntities.remove(kb.statusStandId);
                String mode = kb.is2v2 ? "2×2" : "1×1";
                int req = kb.is2v2 ? 4 : 2;
                String initTxt = "&#00E676&l⚔ КАБИНКА #" + kb.index + " &8[&f" + mode + "&8]\n&a✔ СВОБОДНА &7(0/" + req + ")";
                Entity st = spawnHolo(sw, kb.doorX - 0.35, 102.45, kb.doorZ + 0.5, initTxt);
                kb.statusStandId = st.getUniqueId();
                kb.lastHoloText = initTxt;
            }
        }
    }

    private void spawnLoadedChunkDecorations(World sw) {
        enforceStrictSingletonsOnSpawn(sw);
    }

    private Entity spawnHolo(World w, double x, double y, double z, String text) {
        Location loc = new Location(w, x, y, z);
        try {
            TextDisplay td = (TextDisplay) w.spawnEntity(loc, EntityType.TEXT_DISPLAY);
            td.setPersistent(false);
            td.setBillboard(Display.Billboard.CENTER);
            td.setShadowed(true);
            td.setSeeThrough(false);
            td.setLineWidth(380);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setBackgroundColor(Color.fromARGB(135, 8, 12, 26));
            td.setViewRange(0.85f); // ~54 blocks view range -> crystal-clear visibility across Spawn & Kabinki!
            td.setText(ColorUtil.c(text));
            td.addScoreboardTag("kg_holo");
            td.addScoreboardTag(sessionTag);
            validSpawnEntities.add(td.getUniqueId());
            return td;
        } catch (Throwable fallback) {
            ArmorStand as = (ArmorStand) w.spawnEntity(loc, EntityType.ARMOR_STAND);
            as.setPersistent(false);
            as.setVisible(false);
            as.setGravity(false);
            as.setMarker(true);
            as.setSmall(true);
            as.setBasePlate(false);
            as.setCustomName(ColorUtil.c(text.replace("\n", " • ")));
            as.setCustomNameVisible(true);
            as.setInvulnerable(true);
            as.addScoreboardTag("kg_holo");
            as.addScoreboardTag(sessionTag);
            validSpawnEntities.add(as.getUniqueId());
            return as;
        }
    }

    private void ensureDpsDummies(World sw) {
        String[] titles = {"&#FF5252&l🥊 Манекен #1 [Незерит]", "&#FFD740&l🥊 Манекен #2 [Босс-Броня]", "&#00E5FF&l🥊 Манекен #3 [Криты]"};
        for (int i = 0; i < titles.length; i++) {
            int z = -5 + 2 * i;
            if (!sw.isChunkLoaded(66 >> 4, z >> 4)) continue;
            String key = Integer.toString(i);
            UUID id = dpsDummyMap.get(key);
            Entity current = id == null ? null : Bukkit.getEntity(id);
            if (current != null && current.isValid() && !current.isDead()) continue;
            if (id != null) validSpawnEntities.remove(id);
            Location loc = new Location(sw, 66.5, 100.0, z, -90f, 0f);
            ArmorStand dummy = (ArmorStand) sw.spawnEntity(loc, EntityType.ARMOR_STAND);
            dummy.setPersistent(false);
            dummy.setGravity(false);
            dummy.setBasePlate(true);
            dummy.setArms(true);
            dummy.setCustomName(ColorUtil.c(titles[i]));
            dummy.setCustomNameVisible(true);
            dummy.addScoreboardTag("kg_dps_dummy");
            dummy.addScoreboardTag(sessionTag);
            validSpawnEntities.add(dummy.getUniqueId());
            dpsDummyMap.put(key, dummy.getUniqueId());
            EntityEquipment eq = dummy.getEquipment();
            if (eq != null) {
                eq.setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
                eq.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
                eq.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
                eq.setBoots(new ItemStack(Material.NETHERITE_BOOTS));
                eq.setItemInMainHand(new ItemStack(Material.SHIELD));
            }
        }
    }

    /** Interpolated model rotation and a short articulated wave near players. */
    private void animateNpcs(World sw) {
        if (sw == null) return;
        long now = System.currentTimeMillis();
        List<Player> players = sw.getPlayers();
        if (players.isEmpty()) return;
        for (NpcDef nd : npcDefs) {
            NpcDisplayModel model = npcModels.get(nd.id);
            if (model == null) continue;
            Location base = new Location(sw, nd.x, nd.y, nd.z);
            Player nearest = null;
            double best = 9.0 * 9.0;
            for (Player pl : players) {
                double distance = pl.getLocation().distanceSquared(base);
                if (distance < best) { best = distance; nearest = pl; }
            }
            float wantYaw = nd.yaw;
            if (nearest != null) {
                double dx = nearest.getEyeLocation().getX() - nd.x;
                double dz = nearest.getEyeLocation().getZ() - nd.z;
                float rel = wrapYaw((float) Math.toDegrees(Math.atan2(-dx, dz)) - nd.yaw);
                wantYaw = nd.yaw + Math.max(-78f, Math.min(78f, rel));
            }
            float newYaw = model.yaw() + wrapYaw(wantYaw - model.yaw()) * .35f;
            int remaining = npcWaveSwings.containsKey(nd.id) ? npcWaveSwings.get(nd.id).intValue() : 0;
            float wave = 0f;
            if (remaining > 0) {
                wave = (float) (-.48 * Math.sin((12 - remaining) * Math.PI / 4.0));
                npcWaveSwings.put(nd.id, Integer.valueOf(remaining - 1));
            } else if (nearest != null && best <= 6.5 * 6.5) {
                Long next = npcNextWaveAt.get(nd.id);
                if (next == null || now >= next.longValue()) {
                    npcWaveSwings.put(nd.id, Integer.valueOf(12));
                    npcNextWaveAt.put(nd.id, Long.valueOf(now + 9000L + rng.nextInt(9001)));
                }
            }
            model.pose(newYaw, wave, false);
        }
    }

    /** Нормализация разницы углов в диапазон -180..180. */
    private float wrapYaw(float y) {
        float r = y % 360.0f;
        if (r > 180.0f) r -= 360.0f;
        if (r < -180.0f) r += 360.0f;
        return r;
    }

    public void refreshTopLeaderboards(World sw) {
        StringBuilder sbRich = new StringBuilder("&#FFD740&l💰 ТОП-5 БОГАЧЕЙ СЕРВЕРА");
        List<Map.Entry<String, Double>> rich = plugin.getStore().getTopCoins(5);
        for (int i = 0; i < 5; i++) {
            sbRich.append("\n");
            if (i < rich.size()) {
                sbRich.append("&#FFE082#").append(i + 1).append(" &f").append(rich.get(i).getKey())
                        .append(" &8— &a").append(ColorUtil.fmtMoney(rich.get(i).getValue().doubleValue())).append("$");
            } else {
                sbRich.append("&7#").append(i + 1).append(" — свободно");
            }
        }

        StringBuilder sbKill = new StringBuilder("&#FF1744&l⚔ ТОП-5 КИЛЛЕРОВ СЕРВЕРА");
        List<Map.Entry<String, Integer>> killers = plugin.getStore().getTopKills(5);
        for (int i = 0; i < 5; i++) {
            sbKill.append("\n");
            if (i < killers.size()) {
                sbKill.append("&#FF8A80#").append(i + 1).append(" &f").append(killers.get(i).getKey())
                        .append(" &8— &c").append(killers.get(i).getValue().intValue()).append(" ⚔");
            } else {
                sbKill.append("&7#").append(i + 1).append(" — свободно");
            }
        }

        while (topHoloIds.size() < 2) topHoloIds.add(null);
        double[] xs = {-38.0, -30.0};
        String[] texts = {sbRich.toString(), sbKill.toString()};
        for (int i = 0; i < xs.length; i++) {
            int cx = ((int) Math.floor(xs[i])) >> 4, cz = ((int) Math.floor(-33.2)) >> 4;
            UUID id = topHoloIds.get(i);
            Entity current = id == null ? null : Bukkit.getEntity(id);
            if (!sw.isChunkLoaded(cx, cz)) {
                if (id != null) validSpawnEntities.remove(id);
                topHoloIds.set(i, null);
                continue;
            }
            if (current == null || !current.isValid() || current.isDead()) {
                if (id != null) validSpawnEntities.remove(id);
                current = spawnHolo(sw, xs[i], 103.5, -33.2, texts[i]);
                current.addScoreboardTag("kg_top_holo");
                topHoloIds.set(i, current.getUniqueId());
            } else setHoloText(current, texts[i]);
        }
    }

    private boolean topHologramNeedsSpawn(World sw) {
        double[] xs = {-38.0, -30.0};
        for (int i = 0; i < xs.length; i++) {
            if (!sw.isChunkLoaded(((int) Math.floor(xs[i])) >> 4, ((int) Math.floor(-33.2)) >> 4)) continue;
            UUID id = i < topHoloIds.size() ? topHoloIds.get(i) : null;
            Entity e = id == null ? null : Bukkit.getEntity(id);
            if (e == null || !e.isValid() || e.isDead()) return true;
        }
        return false;
    }

    private void setHoloText(Entity e, String raw) {
        if (e instanceof TextDisplay) {
            ((TextDisplay) e).setText(ColorUtil.c(raw));
        } else if (e != null) {
            e.setCustomName(ColorUtil.c(raw.replace("\n", " • ")));
        }
    }

    /** Cached by textureHash so the client decodes ONLY 8 shared skins once — ZERO skin-fetch freeze when walking! */
    private ItemStack makeTexturedHead(String textureHash) {
        ItemStack cached = cachedSkinHeads.get(textureHash);
        if (cached != null) return cached.clone();
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        if (sm != null) {
            try {
                String shortName = "KGS_" + textureHash.substring(0, 10);
                PlayerProfile prof = Bukkit.createPlayerProfile(UUID.nameUUIDFromBytes(textureHash.getBytes("UTF-8")), shortName);
                PlayerTextures tex = prof.getTextures();
                tex.setSkin(new URL("http://textures.minecraft.net/texture/" + textureHash));
                prof.setTextures(tex);
                sm.setOwnerProfile(prof);
            } catch (Throwable ignored) {}
            head.setItemMeta(sm);
        }
        cachedSkinHeads.put(textureHash, head.clone());
        return head;
    }

    private void ensureNpcAlive(World sw, NpcDef nd) {
        int cx = ((int) Math.floor(nd.x)) >> 4, cz = ((int) Math.floor(nd.z)) >> 4;
        if (!sw.isChunkLoaded(cx, cz)) return;
        Location loc = new Location(sw, nd.x, nd.y, nd.z, 0f, 0f);
        String npcTag = "kg_npc_" + nd.id;
        NpcDisplayModel model = npcModels.get(nd.id);
        if (model == null || !model.isComplete()) {
            if (model != null) model.remove(validSpawnEntities);
            model = new NpcDisplayModel(sw, nd, makeTexturedHead(nd.skinHash), sessionTag, validSpawnEntities);
            npcModels.put(nd.id, model);
        }
        UUID hitId = npcHitboxMap.get(nd.id);
        Entity oldHit = hitId == null ? null : Bukkit.getEntity(hitId);
        if (!(oldHit instanceof Interaction) || !oldHit.isValid() || oldHit.isDead()) {
            if (hitId != null) validSpawnEntities.remove(hitId);
            Interaction hit = (Interaction) sw.spawnEntity(loc, EntityType.INTERACTION);
            hit.setPersistent(false);
            hit.setInteractionWidth(1.05f);
            hit.setInteractionHeight(2.05f);
            hit.setResponsive(true);
            hit.setInvulnerable(true);
            hit.addScoreboardTag("kg_npc");
            hit.addScoreboardTag("kg_npc_hitbox");
            hit.addScoreboardTag(npcTag);
            hit.addScoreboardTag(sessionTag);
            npcHitboxMap.put(nd.id, hit.getUniqueId());
            validSpawnEntities.add(hit.getUniqueId());
        }
        UUID holoId = npcHoloMap.get(nd.id);
        Entity oldHolo = holoId == null ? null : Bukkit.getEntity(holoId);
        if (!(oldHolo instanceof TextDisplay) || !oldHolo.isValid() || oldHolo.isDead()) {
            if (holoId != null) validSpawnEntities.remove(holoId);
            if (oldHolo != null) oldHolo.remove();
            Entity holo = spawnHolo(sw, nd.x, nd.y + 2.25, nd.z, nd.line1 + "\n" + nd.line2);
            if (!(holo instanceof TextDisplay)) {
                validSpawnEntities.remove(holo.getUniqueId());
                holo.remove();
                plugin.getLogger().warning("[SpawnEngine] TextDisplay unavailable for NPC " + nd.id);
                return;
            }
            holo.addScoreboardTag(npcTag);
            npcHoloMap.put(nd.id, holo.getUniqueId());
        }
    }

    // ================= HIGH-FPS ANIMATION, DUEL KABINKI, CLEARLAG & AUTO-MINE LOOPS =================
    private void startEngineLoops() {
        // Display transforms update at most every four ticks, and only while
        // players are in the world. No skeletal mobs or armor-stand animation.
        npcAnimTask = new BukkitRunnable() {
            @Override public void run() {
                World sw = Bukkit.getWorld(VoidSpawnWorldGenerator.getSpawnWorldName());
                if (sw != null && !sw.getPlayers().isEmpty()) animateNpcs(sw);
            }
        };
        npcAnimTask.runTaskTimer(plugin, 30L, 4L);

        // 2. 1-Second Engine Loop (TPS meter, Auto-Mine, Farm Mobs, Kabinki, Top-5 & Auto-ClearLag)
        new BukkitRunnable() {
            @Override
            public void run() {
                long nowMs = System.currentTimeMillis();
                long elapsed = Math.max(50L, nowMs - lastSecondWallMs);
                lastSecondWallMs = nowMs;
                double instantTps = Math.min(20.0, 20.0 * (1000.0 / elapsed));
                measuredTps = measuredTps * 0.8 + instantTps * 0.2;

                tickCounter++;
                World sw = Bukkit.getWorld(VoidSpawnWorldGenerator.getSpawnWorldName());
                if (sw == null || !spawnActive) return;

                mineRefillCountdown--;
                if (mineRefillCountdown == 60) {
                    for (Player mp : sw.getPlayers()) {
                        mp.sendMessage(ColorUtil.c("&#FF9100⛏ [Авто-Шахта] &fРуда восстановится через &#FFD74060 секунд&f! Успейте добыть редкость!"));
                    }
                }
                if (mineRefillCountdown <= 0) {
                    mineRefillCountdown = 300;
                    refillAutoMine(sw, true);
                }

                // Smart Auto-ClearLag every 5 minutes (300s)
                clearLagCountdown--;
                if (clearLagCountdown == 30) {
                    Bukkit.broadcastMessage(ColorUtil.c("&#80D8FF🧹 [Оптимизация] &fОчистка лишнего мусора и стрел на земле через &#FFD74030 секунд&f! &7(Ценные и 3D вещи защищены)"));
                } else if (clearLagCountdown <= 0) {
                    clearLagCountdown = 300;
                    runClearLagSweep(false);
                }

                if (!sw.getPlayers().isEmpty()) {
                    if (tickCounter % 2 == 0) {
                        enforceStrictSingletonsOnSpawn(sw);
                    }
                    if (tickCounter % 5 == 0) {
                        maintainMobFarm(sw);
                    }
                    if (tickCounter % 30 == 0) {
                        refreshTopLeaderboards(sw);
                    }
                    // v6.4.0: красота спавна — лёгкие частицы у варпов и подарков (без лагов и без правки блоков).
                    spawnBeautyParticles(sw);
                    updateDuelKabinki(sw);
                }
                checkVoidAndParkourSafety(sw);
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    // ================= v6.4.0: ЖИВОЙ МАНЕКЕН БРОНИ И ЭЛИТР (/maneken -> «Примерить») =================
    private String mannequinSetName(String setId) {
        if ("krush".equals(setId)) return "&#FF1744&l🔥 Сет Крушителя";
        if ("dracula".equals(setId)) return "&#D500F9&l🩸 Сет Дракулы";
        if ("frost".equals(setId)) return "&#40C4FF&l❄ Сет Стужи";
        if ("zeus".equals(setId)) return "&#FFD740&l⚡ Сет Зевса";
        if ("phantom".equals(setId)) return "&#B388FF&l👻 Сет Фантома";
        if ("owner".equals(setId)) return "&#FFD740&l👑 Сет OWNER";
        return "&#FFD740&l👀 Манекен";
    }

    private ItemStack mannequinElytraItem(String elytraId) {
        if ("elytra_vampire".equals(elytraId)) return ItemFactory.elytraVampire();
        if ("elytra_wind".equals(elytraId)) return ItemFactory.elytraWind();
        if ("elytra_thunder".equals(elytraId)) return ItemFactory.elytraThunder();
        if ("elytra_titan".equals(elytraId)) return ItemFactory.elytraTitan();
        if ("elytra_santa".equals(elytraId)) return ItemFactory.elytraSanta();
        return ItemFactory.elytraPhoenix();
    }

    /**
     * v6.4.0: ставит рядом с игроком ЖИВУЮ модель в полном 3D-сете брони и с элитрами за спиной,
     * вращает её 15 секунд и убирает. Никаких блоков не меняет — значит никаких багов спавна.
     */
    public void spawnArmorMannequin(Player p, String setId, String elytraId) {
        if (p == null || setId == null) return;
        try {
            Location base = p.getLocation().clone();
            Vector look = base.getDirection().setY(0);
            if (look.lengthSquared() < 0.0001) look = new Vector(0, 0, 1);
            look.normalize();
            Location spot = base.clone().add(look.multiply(1.7));
            for (int i = 0; i < 6; i++) {
                Block under = spot.clone().add(0, -1 - i, 0).getBlock();
                if (under.getType().isSolid()) {
                    spot.setY(under.getY() + 1);
                    break;
                }
            }
            spot.setYaw(base.getYaw() + 180.0f);
            spot.setPitch(0.0f);
            final ArmorStand as = (ArmorStand) p.getWorld().spawnEntity(spot, EntityType.ARMOR_STAND);
            as.setGravity(false);
            as.setInvulnerable(true);
            as.setPersistent(false);
            as.setRemoveWhenFarAway(false);
            as.setArms(true);
            as.setBasePlate(false);
            as.setCustomName(ColorUtil.c(mannequinSetName(setId)));
            as.setCustomNameVisible(true);
            as.addScoreboardTag("kg_mod_anim"); // защита от зачистки спавна (как у анимаций Деда)
            as.addScoreboardTag("kg_preview");
            as.setHelmet(ItemFactory.armorPiece(setId, 0));
            as.setChestplate(mannequinElytraItem(elytraId));   // элитры за спиной — их реально видно!
            as.setItemInHand(ItemFactory.armorPiece(setId, 1)); // нагрудник в руке — видно и его
            as.setLeggings(ItemFactory.armorPiece(setId, 2));
            as.setBoots(ItemFactory.armorPiece(setId, 3));
            final Location center = spot.clone();
            ColorUtil.sound(p, "block.amethyst_block.chime", 1.0f, 1.25f);
            p.sendMessage(ColorUtil.c("&#FFD740👀 Манекен поставлен на &f15 секунд&#FFD740: " + mannequinSetName(setId)
                    + " &#FFD740+ элитры! Модель вращается — смотрите со всех сторон."));
            new BukkitRunnable() {
                int t = 0;
                float yaw = center.getYaw();
                @Override
                public void run() {
                    t++;
                    if (!as.isValid()) { cancel(); return; }
                    yaw += 5.0f;
                    if (yaw > 360.0f) yaw -= 360.0f;
                    try {
                        // Location#setYaw возвращает void — собираем локацию отдельно.
                        Location spin = center.clone();
                        spin.setYaw(yaw);
                        spin.setPitch(0.0f);
                        as.teleport(spin);
                    } catch (Throwable ignored) {}
                    if (t % 5 == 0) {
                        try {
                            center.getWorld().spawnParticle(Particle.END_ROD, center.clone().add(0, 1.15, 0), 5, 0.45, 0.75, 0.45, 0.01);
                            center.getWorld().spawnParticle(ColorUtil.particle("ENCHANTED_HIT", "CRIT_MAGIC"), center.clone().add(0, 0.85, 0), 4, 0.5, 0.85, 0.5, 0.02);
                        } catch (Throwable ignored) {}
                    }
                    if (t >= 300) {
                        try {
                            center.getWorld().spawnParticle(Particle.POOF, center.clone().add(0, 1.0, 0), 26, 0.4, 0.7, 0.4, 0.04);
                        } catch (Throwable ignored) {}
                        try { as.remove(); } catch (Throwable ignored) {}
                        cancel();
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L);
        } catch (Throwable t) {
            p.sendMessage(ColorUtil.c("&#FF1744✖ Не удалось поставить манекен — рядом нет места."));
        }
    }

    // ================= v6.4.0: КРАСОТА СПАВНА БЕЗ БАГОВ (частицы у варпов и подарков) =================
    private final List<Location> beautyPoints = new ArrayList<Location>();
    private long lastBeautyMs = 0L;

    /**
     * Лёгкие праздничные частицы у всех варпов и подарков. Блоки НЕ меняются вообще,
     * рисуются только рядом с игроками (радиус 25) и не чаще 1 раза в секунду — ноль лагов.
     */
    private void spawnBeautyParticles(World sw) {
        if (sw == null || sw.getPlayers().isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - lastBeautyMs < 900L) return;
        lastBeautyMs = now;
        try {
            if (beautyPoints.isEmpty()) {
                String[] names = new String[]{"spawn", "mine", "farm", "casino", "wheel", "enchant",
                        "pvp", "train", "kabinki", "parkour", "cases", "top"};
                for (String n : names) {
                    try {
                        Location l = getWarpLocation(n);
                        if (l != null && l.getWorld() != null) beautyPoints.add(l.clone().add(0.0, 1.3, 0.0));
                    } catch (Throwable ignored) {}
                }
                for (int i = 0; i < GIFT_BLOCKS.length; i++) {
                    beautyPoints.add(new Location(sw, GIFT_BLOCKS[i][0] + 0.5, GIFT_BLOCKS[i][1] + 1.15, GIFT_BLOCKS[i][2] + 0.5));
                }
            }
            for (Player p : sw.getPlayers()) {
                Location pl = p.getLocation();
                int shown = 0;
                for (int i = 0; i < beautyPoints.size() && shown < 3; i++) {
                    Location bp = beautyPoints.get(i);
                    if (bp == null || bp.getWorld() == null || !bp.getWorld().equals(sw)) continue;
                    try {
                        if (bp.distanceSquared(pl) > 625.0) continue;
                        sw.spawnParticle(Particle.END_ROD, bp.clone().add(0, 0.95, 0), 3, 0.35, 0.5, 0.35, 0.005);
                        sw.spawnParticle(Particle.SNOWFLAKE, bp.clone().add(0, 1.6, 0), 2, 0.45, 0.25, 0.45, 0.0);
                        shown++;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

    public void shutdown() {
        spawnActive = false;
        if (pendingSpawnSweep != null) { pendingSpawnSweep.cancel(); pendingSpawnSweep = null; }
        if (npcAnimTask != null) {
            try { npcAnimTask.cancel(); } catch (Throwable ignored) {}
            npcAnimTask = null;
        }
        npcWaveSwings.clear();
        npcNextWaveAt.clear();
        for (UUID id : activeCaseDisplays.values()) {
            Entity floating = Bukkit.getEntity(id);
            if (floating != null) floating.remove();
        }
        activeCaseDisplays.clear();
        spinningCases.clear();
        try {
            World sw = Bukkit.getWorld(VoidSpawnWorldGenerator.getSpawnWorldName());
            if (sw != null) {
                for (UUID uid : validSpawnEntities) {
                    Entity e = Bukkit.getEntity(uid);
                    if (e != null) e.remove();
                }
            }
            validSpawnEntities.clear();
            npcModels.clear();
            dpsDummyMap.clear();
            npcHitboxMap.clear();
            npcHoloMap.clear();
        } catch (Throwable ignored) {}
    }

    public double getCurrentTps() {
        return Math.round(measuredTps * 100.0) / 100.0;
    }

    /**
     * Smart Server Optimization Sweep: removes trash ground items, stuck arrows, excess XP orbs,
     * and over-crammed chunk mobs while 100% PROTECTING custom 3D items, Netherite, Totems, Shulkers & PvP gear!
     */
    public int runClearLagSweep(boolean announceAlways) {
        int cleaned = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Chunk ch : w.getLoadedChunks()) {
                int mobCountInChunk = 0;
                for (Entity e : ch.getEntities()) {
                    if (e instanceof Arrow || e instanceof org.bukkit.entity.SpectralArrow) {
                        if (e.getTicksLived() > 200) {
                            e.remove();
                            cleaned++;
                        }
                    } else if (e instanceof ExperienceOrb) {
                        if (e.getTicksLived() > 600) {
                            e.remove();
                            cleaned++;
                        }
                    } else if (e instanceof Item) {
                        Item it = (Item) e;
                        if (it.getScoreboardTags().contains("kg_case_float")) continue;
                        if (!announceAlways && it.getTicksLived() < 300) continue;
                        if (isProtectedValuableItem(it.getItemStack())) continue;
                        it.remove();
                        cleaned++;
                    } else if (e instanceof Mob && !e.getScoreboardTags().contains("kg_event_boss")
                            && !e.getScoreboardTags().contains("kg_farm_mob")
                            && !e.getScoreboardTags().contains("kg_npc")
                            && !e.getScoreboardTags().contains("kg_mod_anim") && e.getCustomName() == null) {
                        mobCountInChunk++;
                        if (mobCountInChunk > 18) {
                            e.remove();
                            cleaned++;
                        }
                    }
                }
            }
        }
        if (announceAlways || cleaned >= 10) {
            Bukkit.broadcastMessage(ColorUtil.c("&#00E676🧹 [Оптимизация] &fУбрано лишних объектов/стрел: &#FFD740"
                    + cleaned + " шт. &8│ &7TPS: &#00E676" + getCurrentTps()));
        }
        return cleaned;
    }

    private boolean isProtectedValuableItem(ItemStack st) {
        if (st == null || st.getType() == Material.AIR) return false;
        if (ItemFactory.getCustomId(st) != null) return true;
        String n = st.getType().name();
        if (n.contains("NETHERITE") || n.contains("DIAMOND") || n.contains("SHULKER_BOX")
                || n.contains("TOTEM") || n.contains("ELYTRA") || n.contains("TRIDENT")
                || n.contains("BEACON") || n.contains("SPAWNER") || n.contains("ENCHANTED_GOLDEN_APPLE")
                || n.contains("GOLDEN_APPLE") || n.contains("ENDER_PEARL")) {
            return true;
        }
        return st.hasItemMeta() && st.getItemMeta().hasEnchants();
    }

    private void maintainMobFarm(World sw) {
        int[] chambersX = new int[]{-92, -84, -76, -68};
        EntityType[] types = new EntityType[]{EntityType.ZOMBIE, EntityType.SKELETON, EntityType.STRAY, EntityType.HUSK};

        for (int i = 0; i < chambersX.length; i++) {
            Location center = new Location(sw, chambersX[i] + 0.5, 100.2, -42.0);
            if (!sw.isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4)) continue;
            int count = 0;
            for (Entity e : sw.getNearbyEntities(center, 3.0, 3.0, 3.0)) {
                if (e.getScoreboardTags().contains("kg_farm_mob") && !e.isDead()) count++;
            }
            while (count < 2) {
                LivingEntity mob = (LivingEntity) sw.spawnEntity(center, types[i]);
                mob.setPersistent(false);
                mob.setCustomName(ColorUtil.c("&#AEEA00❄ Фарм-Моб &7[+45$ + Опыт]"));
                mob.setCustomNameVisible(true);
                mob.setRemoveWhenFarAway(true);
                mob.addScoreboardTag("kg_farm_mob");
                if (mob instanceof Mob) ((Mob) mob).setAware(false);
                if (mob.getEquipment() != null) {
                    mob.getEquipment().setHelmet(new ItemStack(Material.LEATHER_HELMET));
                }
                count++;
            }
        }
    }

    private void updateDuelKabinki(World sw) {
        for (KabinkaState kb : kabinki) {
            int required = kb.is2v2 ? 4 : 2;
            List<Player> inside = new ArrayList<Player>();
            for (Player p : sw.getPlayers()) {
                if (!p.isDead() && p.getGameMode() != org.bukkit.GameMode.SPECTATOR && kb.contains(p.getLocation())) {
                    inside.add(p);
                }
            }
            String mode = kb.is2v2 ? "2×2" : "1×1";
            if (!kb.locked) {
                if (inside.size() >= required) {
                    kb.locked = true;
                    setKabinkaDoor(sw, kb, Material.RED_STAINED_GLASS);
                    updateKabinkaHolo(kb, "&#FF1744&l⚔ КАБИНКА #" + kb.index + " &8[&f" + mode + "&8]\n&c🔥 ИДЁТ БОЙ &7(" + inside.size() + "/" + required + ")");
                    for (Player f : inside) {
                        ColorUtil.sound(f, "block.iron_door.close", 1.0f, 0.8f);
                        f.sendTitle(ColorUtil.c("&#FF1744&l🥊 БОЙ НАЧАЛСЯ!"), ColorUtil.c("&fКабинка закрыта до победы!"), 3, 25, 8);
                    }
                } else if (!inside.isEmpty()) {
                    updateKabinkaHolo(kb, "&#FFD740&l⚔ КАБИНКА #" + kb.index + " &8[&f" + mode + "&8]\n&e⏳ ЖДЁТ СОПЕРНИКА &f(" + inside.size() + "/" + required + ")");
                } else {
                    updateKabinkaHolo(kb, "&#00E676&l⚔ КАБИНКА #" + kb.index + " &8[&f" + mode + "&8]\n&a✔ СВОБОДНА &7(0/" + required + ")");
                }
            } else {
                if (inside.size() <= 1 && kb.lootLockSec == 0) {
                    kb.lootLockSec = 8;
                    if (inside.size() == 1) {
                        Player winner = inside.get(0);
                        winner.sendMessage(ColorUtil.c("&#00E676🏆 Победа в кабинке! У вас есть &e8 секунд &aсобрать весь лут до открытия дверей!"));
                        ColorUtil.sound(winner, "ui.toast.challenge_complete", 1.0f, 1.2f);
                    }
                }
                if (kb.lootLockSec > 0) {
                    kb.lootLockSec--;
                    updateKabinkaHolo(kb, "&#FFD740&l⚔ КАБИНКА #" + kb.index + " &8[&f" + mode + "&8]\n&e🏆 СБОР ЛУТА: &f" + kb.lootLockSec + " сек.");
                    if (kb.lootLockSec == 0 || inside.isEmpty()) {
                        kb.locked = false;
                        kb.lootLockSec = 0;
                        setKabinkaDoor(sw, kb, Material.AIR);
                        updateKabinkaHolo(kb, "&#00E676&l⚔ КАБИНКА #" + kb.index + " &8[&f" + mode + "&8]\n&a✔ СВОБОДНА &7(0/" + required + ")");
                    }
                } else {
                    updateKabinkaHolo(kb, "&#FF1744&l⚔ КАБИНКА #" + kb.index + " &8[&f" + mode + "&8]\n&c🔥 ИДЁТ БОЙ &7(" + inside.size() + "/" + required + ")");
                }
            }
        }
    }

    private void setKabinkaDoor(World sw, KabinkaState kb, Material m) {
        sw.getBlockAt(kb.doorX, 100, kb.doorZ).setType(m, false);
        sw.getBlockAt(kb.doorX, 101, kb.doorZ).setType(m, false);
    }

    private void updateKabinkaHolo(KabinkaState kb, String txt) {
        if (kb.statusStandId == null || txt.equals(kb.lastHoloText)) return;
        Entity e = Bukkit.getEntity(kb.statusStandId);
        if (e != null) {
            kb.lastHoloText = txt;
            setHoloText(e, txt);
        }
    }

    public void notifyKabinkaDeath(Location loc) {
        if (!isInSpawnWorld(loc)) return;
        for (KabinkaState kb : kabinki) {
            if (kb.contains(loc) && kb.locked && kb.lootLockSec == 0) {
                kb.lootLockSec = 8;
            }
        }
    }

    private void spawnGentleSnowfall(World sw) {
        for (Player p : sw.getPlayers()) {
            if (plugin.getStore().isFpsBoost(p)) continue; // Respect player /fps booster!
            Location pl = p.getLocation();
            try {
                p.spawnParticle(Particle.SNOWFLAKE, pl.clone().add(0, 6.5, 0), 5, 8.0, 3.0, 8.0, 0.01);
            } catch (Throwable ignored) {}
        }
    }

    private void checkVoidAndParkourSafety(World sw) {
        for (Player p : sw.getPlayers()) {
            Location cp = plugin.getStore().getParkourCp(p);
            if (p.getLocation().getY() < 62.0) {
                p.setFallDistance(0f);
                Location destination = cp != null ? cp : getSpawnArrival();
                if (destination != null && p.teleport(destination)) ColorUtil.sound(p, "entity.enderman.teleport", 0.8f, 1.2f);
                continue;
            }
            // Smart Parkour Safety: block fly cheating & auto-return on fall onto plaza!
            if (cp != null && isInSpawnWorld(p.getLocation())) {
                if (p.getGameMode() == org.bukkit.GameMode.SURVIVAL || p.getGameMode() == org.bukkit.GameMode.ADVENTURE) {
                    if (p.isFlying() || p.getAllowFlight()) {
                        p.setFlying(false);
                        p.setAllowFlight(false);
                    }
                    if (p.isGliding()) {
                        p.setGliding(false);
                    }
                }
                if (p.getLocation().getY() < cp.getY() - 3.6 && cp.getY() >= 105.0) {
                    p.setFallDistance(0f);
                    p.teleport(cp);
                    ColorUtil.sound(p, "entity.enderman.teleport", 0.7f, 1.35f);
                    ColorUtil.actionBar(p, "&#40C4FF🏃 Возврат на чекпоинт! Выйти из паркура: &f/parkour leave");
                }
            }
        }
    }

    // ================= AUTO-MINE REFILL (DELTA-ONLY BLOCK UPDATES = ZERO SPIKE!) =================
    /**
     * v6.1: РЕДКОСТИ АВТО-ШАХТЫ (взвешенная таблица, 1000 роллов):
     * 🪨 Камень 46% • ⚫ Уголь 20% • ⛓ Железо 12% • 🟣 Золото 4% / Лазурит 3% / Редстоун 3%
     * 💎 Алмаз 7% • 💚 Изумруд 4% • 🌌 Древние Обломки 1%
     */
    private Material pickMineOreByRarity() {
        int roll = rng.nextInt(1000);
        if (roll < 10) return Material.ANCIENT_DEBRIS;   // 🌌 1.0% АРТЕФАКТ
        if (roll < 50) return Material.EMERALD_ORE;      // 💚 4.0% Мифическая
        if (roll < 120) return Material.DIAMOND_ORE;     // 💎 7.0% Легендарная
        if (roll < 160) return Material.GOLD_ORE;        // 🟣 4.0% Эпическая
        if (roll < 190) return Material.LAPIS_ORE;       // 🟣 3.0% Эпическая
        if (roll < 220) return Material.REDSTONE_ORE;    // 🟣 3.0% Эпическая
        if (roll < 340) return Material.IRON_ORE;        // ⛓ 12.0% Редкая
        if (roll < 540) return Material.COAL_ORE;        // ⚫ 20.0% Необычная
        return Material.STONE;                           // 🪨 46.0% Обычная
    }

    public void refillAutoMine(World sw, boolean announce) {
        for (int x = -96; x <= -75; x++) {
            for (int y = 91; y <= 98; y++) {
                for (int z = -6; z <= 14; z++) {
                    if (!sw.isChunkLoaded(x >> 4, z >> 4)) continue;
                    Block blk = sw.getBlockAt(x, y, z);
                    Material cur = blk.getType();
                    // v6.1: восполняем воздух/камень/булыжник/любую старую руду — блоки теперь реально ломаются!
                    if (!announce || cur == Material.COBBLESTONE || cur == Material.AIR
                            || cur == Material.STONE || cur == Material.CAVE_AIR
                            || cur.name().endsWith("_ORE") || cur == Material.ANCIENT_DEBRIS) {
                        blk.setType(pickMineOreByRarity(), false);
                    }
                }
            }
        }
        if (announce && !sw.getPlayers().isEmpty()) {
            for (Player p : sw.getPlayers()) {
                p.sendMessage(ColorUtil.c("&#FF9100⛏ [Авто-Шахта] &fРуда полностью восстановилась! Следующий реген через &#FFD7405 минут&f. "
                        + "&7Редкости: &#BDBDBD🪨46% &#9E9E9E⚫20% &#FB923C⛓12% &#A855F7🟣10% &#80D8FF💎7% &#22C55E💚4% &#E040FB🌌1%"));
            }
        }
    }

    // ================= ZONE HELPERS (v5.7 100% BFS-VERIFIED CLEAN WARPS!) =================
    public boolean isSpawnActive() { return spawnActive; }

    /** Returns null if there is no currently safe, loaded fallback spawn. */
    public Location getSpawnArrival() {
        Location override = plugin.getStore() != null ? plugin.getStore().getCustomSpawnLoc() : null;
        if (override != null && override.getWorld() != null) return override;
        if (!spawnActive) {
            List<World> worlds = Bukkit.getWorlds();
            return worlds.isEmpty() ? null : loadedVanillaSpawn(worlds.get(0));
        }
        return getPreparedSpawnArrival();
    }

    /** The built-in village, independent of an owner's saved /setspawn point. */
    public Location getPreparedSpawnArrival() {
        if (!spawnActive) return null;
        // Never recreate a world synchronously if another plugin unloaded it.
        World sw = Bukkit.getWorld(VoidSpawnWorldGenerator.getSpawnWorldName());
        return sw == null ? null : new Location(sw, 0.5, 100.0, 52.5, 180.0f, 0.0f);
    }

    /**
     * Paper may generate the border-center chunk in getSpawnLocation() when a
     * moved border excludes the saved spawn. Read the saved coordinates first,
     * and never request a destination that would require a synchronous load.
     * If the version-specific lookup fails, the loaded border center provides
     * a conservative fallback; otherwise the caller should refuse to teleport.
     */
    private Location loadedVanillaSpawn(World world) {
        org.bukkit.WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        boolean centerLoaded = world.isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4);
        boolean safeToRead = false;
        try {
            Object handle = world.getClass().getMethod("getHandle").invoke(world);
            Class<?> level = Class.forName("net.minecraft.world.level.Level");
            Object data = level.getMethod("getLevelData").invoke(handle);
            Class<?> levelData = Class.forName("net.minecraft.world.level.storage.LevelData");
            Object pos = levelData.getMethod("getSpawnPos").invoke(data);
            Class<?> vector = Class.forName("net.minecraft.core.Vec3i");
            int x = ((Number) vector.getMethod("getX").invoke(pos)).intValue();
            int y = ((Number) vector.getMethod("getY").invoke(pos)).intValue();
            int z = ((Number) vector.getMethod("getZ").invoke(pos)).intValue();
            Location saved = new Location(world, x, y, z);
            // One block of margin avoids ambiguity between Bukkit's point
            // border test and Minecraft's block-based test at the edge.
            boolean wellInside = border.isInside(saved.clone().add(-1, 0, -1))
                    && border.isInside(saved.clone().add(1, 0, 1));
            safeToRead = wellInside ? world.isChunkLoaded(x >> 4, z >> 4) : centerLoaded;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            safeToRead = centerLoaded;
        }
        if (!safeToRead) return null;
        Location spawn = world.getSpawnLocation();
        return world.isChunkLoaded(spawn.getBlockX() >> 4, spawn.getBlockZ() >> 4) ? spawn : null;
    }

    public Location getWarpLocation(String warpName) {
        String w = warpName == null ? "" : warpName.toLowerCase(Locale.ROOT);
        if (!spawnActive) {
            if ("spawn".equals(w) || "спавн".equals(w)) return getSpawnArrival();
            return plugin.getStore() != null ? plugin.getStore().getCustomWarpLocation(w) : null;
        }
        World sw = Bukkit.getWorld(VoidSpawnWorldGenerator.getSpawnWorldName());
        if (sw == null) {
            if ("spawn".equals(w) || "спавн".equals(w)) return getSpawnArrival();
            return plugin.getStore() != null ? plugin.getStore().getCustomWarpLocation(w) : null;
        }
        if ("mine".equals(w) || "шахта".equals(w)) return new Location(sw, -68.5, 100.0, 4.5, 90.0f, 10.0f);
        if ("farm".equals(w) || "фарм".equals(w)) return new Location(sw, -80.0, 100.0, -34.5, 180.0f, 0.0f);
        if ("casino".equals(w) || "казино".equals(w)) return new Location(sw, -59.5, 100.0, 68.5, 90.0f, 0.0f);
        if ("wheel".equals(w) || "колесо".equals(w)) return new Location(sw, 0.5, 100.0, 40.5, 180.0f, 0.0f);
        if ("enchant".equals(w) || "enchants".equals(w) || "bafs".equals(w) || "чары".equals(w)) return new Location(sw, 59.5, 100.0, 68.5, -90.0f, 0.0f);
        if ("pvp".equals(w) || "пвп".equals(w) || "arena".equals(w)) return new Location(sw, 61.5, 100.0, 17.5, -90.0f, 8.0f);
        if ("train".equals(w) || "totem".equals(w) || "dummy".equals(w) || "тренировка".equals(w)) return new Location(sw, 61.5, 100.0, -3.0, -90.0f, 0.0f);
        if ("kabinki".equals(w) || "duels".equals(w) || "кабинки".equals(w)) return new Location(sw, 63.5, 100.0, -24.5, -90.0f, 0.0f);
        if ("parkour".equals(w) || "паркур".equals(w)) return new Location(sw, 38.5, 100.0, -24.5, 180.0f, 0.0f);
        if ("cases".equals(w) || "кейсы".equals(w)) return new Location(sw, 0.5, 102.0, -54.5, 180.0f, 0.0f);
        if ("top".equals(w) || "топ".equals(w)) return new Location(sw, -34.0, 100.0, -28.0, 180.0f, 0.0f);
        if ("spawn".equals(w) || "спавн".equals(w)) return getSpawnArrival();
        if (plugin.getStore() != null) {
            Location custom = plugin.getStore().getCustomWarpLocation(w);
            if (custom != null) return custom;
        }
        return null;
    }

    public boolean isInSpawnWorld(Location loc) {
        return spawnActive && loc != null && loc.getWorld() != null
                && loc.getWorld().getName().equalsIgnoreCase(VoidSpawnWorldGenerator.getSpawnWorldName());
    }

    public boolean isInsideAutoMine(Location loc) {
        if (!isInSpawnWorld(loc)) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        return x >= -96 && x <= -75 && y >= 91 && y <= 98 && z >= -6 && z <= 14;
    }

    public boolean isInsidePvpZone(Location loc) {
        if (!isInSpawnWorld(loc)) return true; // PvP enabled in survival worlds!
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        // Sunken Gladiator Colosseum pit + stairs (X=66..103, Y=91..98, Z=4..38)
        if (x >= 66 && x <= 103 && y >= 91 && y <= 98 && z >= 4 && z <= 38) {
            return true;
        }
        // Any of the 8 Duel Kabinki interiors
        for (KabinkaState kb : kabinki) {
            if (kb.contains(loc)) return true;
        }
        return false;
    }

    public boolean isInsideAnyKabinka(Location loc) {
        if (loc == null || !isInSpawnWorld(loc)) return false;
        for (KabinkaState kb : kabinki) {
            if (kb.contains(loc)) return true;
            // Also include 1-block doorway threshold of the kabinka
            if (Math.abs(loc.getBlockX() - kb.doorX) <= 1 && Math.abs(loc.getBlockZ() - kb.doorZ) <= 1
                    && loc.getBlockY() >= 99 && loc.getBlockY() <= 104) {
                return true;
            }
        }
        return false;
    }

    public CasePedestal getCasePedestalAt(Block b) {
        if (b == null || !isInSpawnWorld(b.getLocation())) return null;
        for (CasePedestal cp : casePedestals) {
            if (cp.x == b.getX() && cp.y == b.getY() && cp.z == b.getZ()) return cp;
        }
        return null;
    }

    public List<CasePedestal> getCasePedestals() {
        return casePedestals;
    }

    public NpcDef getNpcByEntity(Entity e) {
        if (!(e instanceof Interaction) || !e.isValid() || !e.getScoreboardTags().contains(sessionTag)) return null;
        for (NpcDef nd : npcDefs) {
            if (e.getUniqueId().equals(npcHitboxMap.get(nd.id))) return nd;
        }
        return null;
    }

    public int getAirdropSecondsLeft() {
        return plugin.getBossEvents() != null ? plugin.getBossEvents().getNextAutoSeconds() : 300;
    }

    // ================= FORTUNE WHEEL 2026 (/wheel) =================
    public void spinFortuneWheel(Player p, boolean useSnowflakes) {
        // Check every possible item outcome before rolling or charging, so a
        // full inventory cannot lose a prize or be used to reroll for free.
        if (!InventoryCapacity.canFit(p, ItemFactory.plast(4), ItemFactory.trapka(2))
                || !InventoryCapacity.canFit(p, ItemFactory.nyMandarin(8), ItemFactory.potionKiller(1))
                || !InventoryCapacity.canFit(p, ItemFactory.bozhyaAura(2), ItemFactory.dezorient(2))
                || !InventoryCapacity.canFit(p, ItemFactory.sharSanty())) {
            p.sendMessage(ColorUtil.c("&cОсвободите место для двух предметов колеса. Валюта не списана."));
            return;
        }
        if (useSnowflakes) {
            if (!plugin.getStore().takeSnowflakes(p.getName(), 5)) {
                ColorUtil.sound(p, "entity.villager.no", 1f, 1f);
                p.sendMessage(ColorUtil.c("&#FF1744✖ Нужно &b5 Снежинок ❄ &c(или введите &f/wheel money &cза 15 000$)!"));
                return;
            }
        } else {
            if (!plugin.getStore().takeCoins(p.getName(), 15000)) {
                ColorUtil.sound(p, "entity.villager.no", 1f, 1f);
                p.sendMessage(ColorUtil.c("&#FF1744✖ Нужно &a15 000$ Монет &c(или &b5 Снежинок ❄ &cдля &f/wheel&c)!"));
                return;
            }
        }
        int roll = rng.nextInt(100);
        String prize;
        if (roll < 22) {
            plugin.getStore().addCoins(p.getName(), 30000);
            prize = "&a+30 000$ Монет";
        } else if (roll < 42) {
            p.getInventory().addItem(ItemFactory.plast(4), ItemFactory.trapka(2));
            prize = "&#A855F74× Пласт + 2× Трапка";
        } else if (roll < 58) {
            p.getInventory().addItem(ItemFactory.nyMandarin(8), ItemFactory.potionKiller(1));
            prize = "&#FF91008× Новогодних Мандаринов + Зелье Киллера";
        } else if (roll < 74) {
            plugin.getStore().addRelics(p.getName(), 25);
            prize = "&#00E5FF+25 Риликов ❖";
        } else if (roll < 88) {
            p.getInventory().addItem(ItemFactory.bozhyaAura(2), ItemFactory.dezorient(2));
            prize = "&#FFD7402× Божья Аура + 2× Дезориентация";
        } else if (roll < 97) {
            plugin.getStore().addCaseKeys(p.getName(), "summer", 1);
            prize = "&#80D8FFНовогодний Кейс (/cases)";
        } else {
            p.getInventory().addItem(ItemFactory.sharSanty());
            prize = "&#FF1744&lДЖЕКПОТ! 3D ШАР САНТЫ!";
            Bukkit.broadcastMessage(ColorUtil.c("&#FF4081&l🎡 [КОЛЕСО ФОРТУНЫ] &fИгрок &#FFD740" + p.getName() + " &fвыбил ДЖЕКПОТ — &#FF1744&l3D ШАР САНТЫ&f!"));
        }
        plugin.getStore().save();
        plugin.getHud().updatePlayerHud(p);
        ColorUtil.sound(p, "entity.player.levelup", 1.0f, 1.3f);
        p.sendTitle(ColorUtil.c("&#FF4081&l🎡 КОЛЕСО ФОРТУНЫ"), ColorUtil.c("&fПриз: " + prize), 5, 45, 10);
        p.sendMessage(ColorUtil.c("&#FF4081🎡 Вы прокрутили Колесо Фортуны и выиграли: " + prize + "&f!"));
    }

    // ================= SEPARATE WORLD TIERED RTP (/rtp + /rtp near) =================
    public void performRtp(Player p, String mode) {
        plugin.getRtpManager().start(p, mode);
    }

    // ================= ANIMATED SHULKER CASE ROULETTE =================
    private boolean casePrizesFit(Player player, String caseId) {
        if ("donate".equals(caseId) || "sdonate".equals(caseId) || "reallydev".equals(caseId)
                || "riliks".equals(caseId) || "money".equals(caseId)) return true;
        if ("kits".equals(caseId)) {
            for (String kit : new String[]{"god", "king", "sponsor", "dadmin"}) {
                List<ItemStack> items = ItemFactory.buildKitItems(kit);
                if (!InventoryCapacity.canFit(player, items.toArray(new ItemStack[items.size()]))) return false;
            }
            return true;
        }
        // Every other case has a single item-stack prize. Requiring an empty
        // storage slot covers every possible result without exposing the roll.
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) return true;
        }
        return false;
    }

    public void openShulkerCaseAnimated(final Player p, final CasePedestal cp) {
        int keys = plugin.getStore().getCaseKeys(p.getName(), cp.caseId);
        if (keys <= 0) {
            ColorUtil.sound(p, "entity.villager.no", 1.0f, 1.0f);
            p.sendMessage(ColorUtil.c("&#FF1744✖ У вас нет ключей от " + cp.title + "&#FF1744!"));
            p.sendMessage(ColorUtil.c("&7Купите на сайте от &#FFD740" + cp.priceRub + "₽&7: &#FF4081https://kinggrief.vercel.app &7или в &f/cases"));
            return;
        }
        if (!casePrizesFit(p, cp.caseId)) {
            p.sendMessage(ColorUtil.c("&cОсвободите место для возможного приза кейса. Ключ не списан."));
            return;
        }
        final String lockKey = cp.caseId;
        if (spinningCases.contains(lockKey)) {
            p.sendMessage(ColorUtil.c("&eЭтот Шалкер-Кейс прямо сейчас крутится! Подождите 3 секунды."));
            return;
        }
        // The key is charged only after the animation, once the player is
        // online and the inventory has been rechecked. No key is lost on quit.
        final World sw = p.getWorld();
        Location pedestal = new Location(sw, cp.x + .5, cp.y + 1.35, cp.z + .5);
        final Location center = isInSpawnWorld(p.getLocation()) && sw.isChunkLoaded(cp.x >> 4, cp.z >> 4)
                && p.getLocation().distanceSquared(pedestal) <= 16 * 16
                ? pedestal : p.getLocation().clone().add(0, 1.6, 0);
        final ItemStack[] spinItems = new ItemStack[]{
                ItemFactory.sharSanty(), ItemFactory.elytraSanta(), ItemFactory.swordKrush(),
                ItemFactory.talFeniksa(3), ItemFactory.plast(4), ItemFactory.trapka(4), ItemFactory.pickDjamshut()
        };
        final Item floating = sw.dropItem(center, spinItems[0]);
        floating.setPersistent(false);
        floating.setPickupDelay(Integer.MAX_VALUE);
        floating.setGravity(false);
        floating.setVelocity(new Vector(0, 0, 0));
        floating.setCustomNameVisible(true);
        floating.addScoreboardTag("kg_case_float");
        spinningCases.add(lockKey);
        activeCaseDisplays.put(lockKey, floating.getUniqueId());

        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                step++;
                if (!p.isOnline() || !floating.isValid() || floating.isDead()) {
                    cancel();
                    floating.remove();
                    spinningCases.remove(lockKey);
                    activeCaseDisplays.remove(lockKey);
                    return;
                }
                if (step <= 14) {
                    floating.setItemStack(spinItems[step % spinItems.length]);
                    floating.setCustomName(ColorUtil.c("&#FFD740✦ Крутится " + cp.title + " &#FFD740✦"));
                    floating.teleport(center);
                    sw.spawnParticle(Particle.END_ROD, center, 4, 0.25, 0.25, 0.25, 0.02);
                    sw.playSound(center, "minecraft:block.note_block.pling", 0.9f, 0.8f + step * 0.08f);
                    return;
                }
                cancel();
                floating.remove();
                spinningCases.remove(lockKey);
                activeCaseDisplays.remove(lockKey);
                if (plugin.getStore().getCaseKeys(p.getName(), cp.caseId) <= 0) {
                    p.sendMessage(ColorUtil.c("&cКлюч больше недоступен: кейс не открыт."));
                    return;
                }
                if (!casePrizesFit(p, cp.caseId)) {
                    p.sendMessage(ColorUtil.c("&cИнвентарь заполнился во время прокрутки. Ключ не списан."));
                    return;
                }
                if (!plugin.getStore().changeCaseKeyDurably(p.getName(), cp.caseId, -1)) {
                    p.sendMessage(ColorUtil.c("&cНе удалось сохранить открытие. Ключ не списан, приз не выдан."));
                    return;
                }
                try {
                    grantCasePrize(p, cp);
                } catch (Throwable error) {
                    // A plugin/API may throw after a partial external reward. Restore
                    // the key when possible and log the uncertainty for an admin.
                    boolean restored = plugin.getStore().changeCaseKeyDurably(p.getName(), cp.caseId, 1);
                    plugin.getLogger().severe("[Cases] Prize failed for " + p.getName() + "/" + cp.caseId
                            + "; key " + (restored ? "restored" : "refund FAILED") + "; inspect partial rewards: " + error);
                    p.sendMessage(ColorUtil.c(restored ? "&cОшибка выдачи приза; ключ возвращён. Обратитесь к администратору."
                            : "&cОшибка выдачи приза и возврата ключа. Обратитесь к администратору."));
                    return;
                }
                ColorUtil.spawnFirework(center);
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    public void grantCasePrize(Player p, CasePedestal cp) {
        String cid = cp.caseId;
        String prizeText;
        if ("donate".equals(cid) || "sdonate".equals(cid) || "reallydev".equals(cid)) {
            String[] pool = "sdonate".equals(cid)
                    ? new String[]{"god", "imperator", "king", "sponsor", "dmoder", "dstmoder", "dadmin", "opka", "downer"}
                    : "reallydev".equals(cid)
                    ? new String[]{"king", "sponsor", "dmoder", "dstmoder", "dglmoder", "dadmin", "dkp", "opka", "downer"}
                    : new String[]{"dragon", "pegas", "cobra", "dracula", "bunny", "rabbit", "vampire", "tiger", "god", "imperator", "king"};
            String wonRank = pool[rng.nextInt(pool.length)];
            RankManager.RankDef cur = plugin.getRanks().getRank(p);
            RankManager.RankDef won = plugin.getRanks().byId(wonRank);
            if (won.weight > cur.weight) {
                plugin.getRanks().setRank(p.getName(), won.id, true);
                prizeText = "Привилегия " + won.shortLabel;
            } else {
                int comp = won.weight * 3;
                plugin.getStore().addRelics(p.getName(), comp);
                prizeText = won.shortLabel + " &7(повтор → &b+" + comp + " Риликов ❖&7)";
            }
        } else if ("riliks".equals(cid)) {
            int[] amts = new int[]{60, 120, 200, 350, 600, 1200};
            int a = amts[rng.nextInt(amts.length)];
            plugin.getStore().addRelics(p.getName(), a);
            prizeText = "&#00E5FF+" + a + " Риликов ❖";
        } else if ("money".equals(cid)) {
            int[] amts = new int[]{25000, 50000, 100000, 250000, 500000, 1000000};
            int a = amts[rng.nextInt(amts.length)];
            plugin.getStore().addCoins(p.getName(), a);
            prizeText = "&a+" + ColorUtil.fmtMoney(a) + "$ Монет";
        } else if ("allorno".equals(cid)) {
            if (rng.nextBoolean()) {
                plugin.getStore().addRelics(p.getName(), 450);
                plugin.getStore().addCoins(p.getName(), 150000);
                p.getInventory().addItem(ItemFactory.sharTitana());
                prizeText = "&#00E676ДЖЕКПОТ! 3D Шар Титана + 450❖ + 150 000$!";
            } else {
                p.getInventory().addItem(new ItemStack(Material.COAL, 1));
                prizeText = "&7Уголёк Неудачи (попробуй ещё!)";
            }
        } else if ("ditems".equals(cid) || "legendary".equals(cid)) {
            ItemStack[] pool = new ItemStack[]{
                    ItemFactory.sharTitana(), ItemFactory.sharAndromedy(), ItemFactory.sharApollona(),
                    ItemFactory.sharAresa(), ItemFactory.sharSanty(), ItemFactory.talKrushitelya(),
                    ItemFactory.talFeniksa(3), ItemFactory.elytraPhoenix(), ItemFactory.elytraSanta(),
                    ItemFactory.swordDracula(), ItemFactory.swordKrush(),
                    ItemFactory.swordFrost(), ItemFactory.swordZeus(), ItemFactory.swordKatana(),
                    ItemFactory.swordWither(), ItemFactory.swordVenom(), ItemFactory.swordPhantom(),
                    ItemFactory.swordPhoenix(), ItemFactory.swordVoid(), ItemFactory.swordMidas()
            };
            ItemStack won = pool[rng.nextInt(pool.length)];
            p.getInventory().addItem(won);
            prizeText = won.getItemMeta().getDisplayName();
        } else if ("djamshut".equals(cid)) {
            ItemStack[] pool = new ItemStack[]{
                    ItemFactory.pickDjamshut(), ItemFactory.pickSpawner(), ItemFactory.mysteryEgg(3),
                    ItemFactory.xpOrb100(5), ItemFactory.snowflakeCoin(25)
            };
            ItemStack won = pool[rng.nextInt(pool.length)];
            p.getInventory().addItem(won);
            prizeText = won.getItemMeta().getDisplayName();
        } else if ("kits".equals(cid)) {
            String[] kPool = new String[]{"god", "king", "sponsor", "dadmin"};
            String wk = kPool[rng.nextInt(kPool.length)];
            ItemFactory.giveKitItems(p, wk);
            prizeText = "&#38BDF8Полный Набор Кита " + wk.toUpperCase();
        } else {
            ItemStack[] pool = new ItemStack[]{
                    ItemFactory.sharSanty(), ItemFactory.elytraSanta(), ItemFactory.talStuzhi(),
                    ItemFactory.plast(8), ItemFactory.trapka(6), ItemFactory.nyMandarin(16), ItemFactory.snowflakeCoin(20)
            };
            ItemStack won = pool[rng.nextInt(pool.length)];
            p.getInventory().addItem(won);
            prizeText = won.getItemMeta().getDisplayName();
        }
        plugin.getStore().save();
        plugin.getHud().updatePlayerHud(p);
        Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l[КЕЙСЫ] &fИгрок &#80D8FF" + p.getName() + " &fоткрыл " + cp.title + " &fи выбил: " + prizeText + "&f!"));
    }
}
