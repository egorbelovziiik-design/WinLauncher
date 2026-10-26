package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.Color;
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
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.InputStream;
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
import java.util.zip.GZIPInputStream;

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
            if (!l.getWorld().getName().equalsIgnoreCase(VoidSpawnWorldGenerator.SPAWN_WORLD_NAME)) return false;
            int bx = l.getBlockX(), by = l.getBlockY(), bz = l.getBlockZ();
            return bx >= minX && bx <= maxX && by >= 99 && by <= 104 && bz >= minZ && bz <= maxZ;
        }
    }

    private final KingGriefCore plugin;
    private final Random rng = new Random();
    private final List<CasePedestal> casePedestals = new ArrayList<CasePedestal>();
    private final List<NpcDef> npcDefs = new ArrayList<NpcDef>();
    private final List<KabinkaState> kabinki = new ArrayList<KabinkaState>();
    private final Map<String, UUID> npcBodyMap = new HashMap<String, UUID>();
    private final Map<String, UUID> npcHitboxMap = new HashMap<String, UUID>();
    private final Set<String> spinningCases = new HashSet<String>();
    private int mineRefillCountdown = 90;
    private int tickCounter = 0;
    private int animPhase = 0;

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

        // 24 Custom-Skinned Animated Humanoid NPCs — ZERO vanilla Villagers, EACH with its OWN dedicated Shop/GUI!
        String skSanta    = "14e424b1676feec3a3f8ebade9e7d6a6f7ffd2b46eb1769f36f7d22a50d445f8";
        String skKing     = "d5c6dc2bbf51c36cfc7714585a6a5683ef2b14d47d8ff714654a893f5da622";
        String skMage     = "e97e2068816972c524429d2c5ed1f9b7ba9a39589a1fd7a1149fa32d8b41c1";
        String skWarrior  = "a116e69a845e227f7eab1ed2b8bc57be67b1f0fb8c219a063bc25be7a8e0b2";
        String skMerchant = "797955462e4e576664499ac4a1c572f6143f19b4d6794772716f18647582a2";
        String skSnowGirl = "90ad8b525c79e0f2014c0065edbc5b03bd254e6b40b24f6bc676bf796b21a5";
        String skPirate   = "8e69a68452a2cf1e8999e2d8db0f0d1aa8b4e3c5c0e8efaa714492d9299f18";
        String skMiner    = "b0419eb5cb3e39ef7b2f88c8c5b6a0882610c5ec4650fb76ca39d0f221a1e0";

        // Central Royal Plaza Ring (aroundArrival Z=48 -> Giant Pine Z=0)
        npcDefs.add(new NpcDef("rtp",        "&#00E676&l⚡ ПРОВОДНИК В МИР (/rtp)",  "&7Единственный вход в выживание!",  -7.5, 100, 42.5, -145f, skWarrior,  Color.fromRGB(0, 220, 110),   new ItemStack(Material.RECOVERY_COMPASS), new ItemStack(Material.ENDER_PEARL), null, "gui:rtp"));
        npcDefs.add(new NpcDef("donate",     "&#FFD740&l👑 ИМПЕРАТОР ДОНАТА",        "&740 анимированных рангов (/donate)", 8.5, 100, 42.5,  145f, skKing,     Color.fromRGB(255, 200, 0),   new ItemStack(Material.NETHER_STAR),      ItemFactory.sharSanty(),             null, "gui:donate"));
        npcDefs.add(new NpcDef("shop_main",  "&#00E5FF&l🛒 ВЕРХОВНЫЙ КУПЕЦ (/shop)", "&78 категорий товаров за Монеты $", -13.5, 100, 34.5, -125f, skMerchant, Color.fromRGB(0, 190, 255),   new ItemStack(Material.EMERALD_BLOCK),    new ItemStack(Material.GOLDEN_CARROT),null, "gui:shop"));
        npcDefs.add(new NpcDef("rshop",      "&#E040FB&l🔮 МАГИСТР РИЛИКОВ (/rshop)","&73D Шары, Элитры и Расходники FT",  14.5, 100, 34.5,  125f, skMage,     Color.fromRGB(190, 40, 240),  ItemFactory.sharTitana(),                 ItemFactory.talFeniksa(3),           ItemFactory.elytraSanta(), "gui:rshop"));
        npcDefs.add(new NpcDef("bafovar",    "&#FF9100&l⚗ АЛХИМИК БАФОВАР (/bafs)", "&7Микс до 8 зелий за 5 000$ + Лавка!",-16.5,100, 24.5, -105f, skMage,     Color.fromRGB(255, 110, 0),   ItemFactory.potionKiller(1),              new ItemStack(Material.BLAZE_ROD),   null, "gui:bafovar"));
        npcDefs.add(new NpcDef("skins",      "&#FF4081&l⛓ МАСТЕР СКИНОВ ТРАПОК",    "&7Скины трапок с дебаффами (/skins)", 17.5, 100, 24.5,  105f, skPirate,   Color.fromRGB(220, 20, 90),   ItemFactory.trapka(1),                    ItemFactory.plast(1),                null, "gui:skins"));
        npcDefs.add(new NpcDef("kits",       "&#38BDF8&l📦 ОРУЖЕЙНИК КИТОВ (/kits)", "&7Киты с 2 стаками перок и морковью!",-16.5,100, 14.5,  -85f, skWarrior,  Color.fromRGB(40, 150, 250),  ItemFactory.swordKrush(),                 new ItemStack(Material.ENDER_PEARL), new ItemStack(Material.NETHERITE_CHESTPLATE), "gui:kits"));
        npcDefs.add(new NpcDef("buyer",      "&#69F0AE&l💰 СКУПЩИК ДЖАМШУТ [x2.0]",  "&7Прокачка множителя до x2.0 (/buyer)",17.5,100, 14.5,   85f, skMerchant, Color.fromRGB(50, 220, 100),  new ItemStack(Material.GOLD_BLOCK),       ItemFactory.snowflakeCoin(1),        null, "gui:buyer"));
        npcDefs.add(new NpcDef("ah",         "&#FFAB40&l⚖ АУКЦИОНИСТ (/ah)",         "&7Торговля предметами игроков",      -14.5, 100, -14.5, -45f, skMerchant, Color.fromRGB(255, 170, 30),  new ItemStack(Material.DIAMOND),          new ItemStack(Material.GOLD_INGOT),  null, "gui:ah"));
        npcDefs.add(new NpcDef("bounty",     "&#FF1744&l🎯 ОХОТНИК ЗА ГОЛОВАМИ",     "&7Контракты на убийство (/bounty)",   15.5, 100, -14.5,  45f, skPirate,   Color.fromRGB(180, 15, 30),   ItemFactory.swordDracula(),               new ItemStack(Material.SKELETON_SKULL),null, "gui:bounty"));
        npcDefs.add(new NpcDef("santa",      "&#FF1744&l🎅 ДЕДУШКА МОРОЗ 2026",      "&7Новогодний подарок (/daily)",       -8.5, 100,  18.5, -160f, skSanta,   Color.fromRGB(235, 30, 40),   ItemFactory.nyMandarin(1),                ItemFactory.potionChampagne(1),      null, "action:santa"));
        npcDefs.add(new NpcDef("snowmaiden", "&#80D8FF&l❄ СНЕГУРОЧКА (ПРОПУСК)",    "&7Зимний Боевой Пропуск (/bp)",        9.5, 100,  18.5,  160f, skSnowGirl,Color.fromRGB(110, 210, 255), ItemFactory.snowflakeCoin(1),             ItemFactory.talStuzhi(),             null, "gui:bp"));

        // Dedicated Category Merchants around the Plaza & Islands (each has its OWN shop!)
        npcDefs.add(new NpcDef("sh_pvp",     "&#A855F7&l🧱 ЛАВКА ПЛАСТОВ И ТРАПОК",  "&7Боевые расходники FT×RW",          24.5, 100,  16.5,   90f, skWarrior,  Color.fromRGB(160, 50, 240),  ItemFactory.plast(1),                     ItemFactory.dezorient(1),            null, "shop:pvp"));
        npcDefs.add(new NpcDef("sh_gear",    "&#FACC15&l⚔ КУЗНЕЦ БРОНИ И ОРУЖИЯ",    "&7Алмазное и Незеритовое снаряжение",24.5, 100,   6.5,   90f, skWarrior,  Color.fromRGB(240, 190, 20),  new ItemStack(Material.NETHERITE_SWORD),  new ItemStack(Material.TOTEM_OF_UNDYING), new ItemStack(Material.DIAMOND_CHESTPLATE), "shop:gear"));
        npcDefs.add(new NpcDef("sh_raid",    "&#EF4444&l💣 ПОДРЫВНИК РЕЙДА (TNT)",   "&7Динамит, Обсидиан, Кристаллы",    -24.5, 100,   6.5,  -90f, skPirate,   Color.fromRGB(220, 50, 30),   new ItemStack(Material.TNT),              new ItemStack(Material.END_CRYSTAL), null, "shop:raid"));
        npcDefs.add(new NpcDef("sh_blocks",  "&#38BDF8&l🧱 СТРОИТЕЛЬНАЯ ЯРМАРКА",   "&7Цветной бетон, кварц, стекло, лёд",-24.5,100,  16.5,  -90f, skMiner,    Color.fromRGB(60, 180, 245),  new ItemStack(Material.QUARTZ_BLOCK),     new ItemStack(Material.LANTERN),     null, "shop:blocks"));
        npcDefs.add(new NpcDef("sh_food",    "&#FB923C&l🥕 ПЕКАРНЯ И ФЕРМА",         "&7Золотая морковь, Перки, Чар-яблоки",-20.5,100, 28.5, -110f, skSnowGirl, Color.fromRGB(250, 140, 40),  new ItemStack(Material.GOLDEN_CARROT),    new ItemStack(Material.ENDER_PEARL), null, "shop:food"));
        npcDefs.add(new NpcDef("sh_elytra",  "&#00E5FF&l🪽 МАСТЕР 3D ЭЛИТР",         "&76 Элитр с уникальными баффами!",    20.5, 100, 28.5,  110f, skKing,     Color.fromRGB(0, 220, 255),   new ItemStack(Material.FIREWORK_ROCKET),  ItemFactory.sharAndromedy(),         ItemFactory.elytraPhoenix(), "shop:elytra"));

        // Wing & Island Specialists
        npcDefs.add(new NpcDef("cases_npc",  "&#FFD740&l🎁 ХРАНИТЕЛЬ 11 КЕЙСОВ",     "&7Шалкер-Кейсы и Промокоды (/cases)",  0.5, 102, -52.5,    0f, skKing,    Color.fromRGB(255, 215, 0),   new ItemStack(Material.TRIPWIRE_HOOK),    new ItemStack(Material.ENDER_CHEST), null, "gui:cases"));
        npcDefs.add(new NpcDef("miner_npc",  "&#FF9100&l⛏ БРИГАДИР АВТО-ШАХТЫ",     "&7Работы и Скупка руды (/jobs)",     -66.5, 100,   4.5,  -90f, skMiner,   Color.fromRGB(255, 140, 0),   ItemFactory.pickDjamshut(),               new ItemStack(Material.DIAMOND),     null, "gui:jobs"));
        npcDefs.add(new NpcDef("farm_npc",   "&#AEEA00&l🥚 ЕГЕРЬ СПАВНЕРОВ И МОБОВ", "&7Рассадники и Яйца призыва",        -78.5, 100, -33.5,    0f, skMiner,   Color.fromRGB(140, 220, 20),  ItemFactory.pickSpawner(),                ItemFactory.mysteryEgg(1),           null, "shop:spawners"));
        npcDefs.add(new NpcDef("casino_npc", "&#FFD740&l🎰 КРУПЬЕ КАЗИНО 777",       "&7Рулетка ×14, Слоты и Монетка (/cf)",-68.5,100,  50.5,   90f, skKing,    Color.fromRGB(230, 30, 60),   new ItemStack(Material.GOLD_BLOCK),       new ItemStack(Material.EMERALD),     null, "gui:casino"));
        npcDefs.add(new NpcDef("baf_island", "&#E040FB&l🧪 ГРАНД-АЛХИМИК БАФОВАР",   "&7Смешай до 8 зелий за 5 000$!",      68.5, 100,  48.5,  -90f, skMage,    Color.fromRGB(180, 30, 220),  ItemFactory.potionAcid(1),                ItemFactory.potionMedic(1),          null, "gui:bafovar"));
        npcDefs.add(new NpcDef("parkour_npc","&#40C4FF&l🏃 ТРЕНЕР ПАРКУРА (40 ОСТР.)","&7100% проходимый паркур + Награда!", 35.5, 100, -24.5, -145f, skSnowGirl,Color.fromRGB(50, 200, 255),  new ItemStack(Material.FEATHER),          ItemFactory.snowflakeCoin(1),        null, "warp:parkour"));

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

    public void initializeSpawnWorldAndBuild(boolean forceRebuild) {
        World sw = VoidSpawnWorldGenerator.getOrCreateSpawnWorld();
        if (sw == null) return;
        File marker = new File(sw.getWorldFolder(), ".spawn_v56_ny_ok");
        boolean stoneBugDetected = sw.getBlockAt(0, 108, 0).getType() == Material.STONE
                || sw.getBlockAt(0, 99, 15).getType() == Material.STONE
                || sw.getBlockAt(0, 99, 0).getType() == Material.STONE;
        if (forceRebuild || !marker.exists() || stoneBugDetected || sw.getBlockAt(0, 99, 0).getType() == Material.AIR) {
            plugin.getLogger().info("[SpawnEngine v5.6] Building 241x124x241 Festive New Year Sky Kingdom (Santa Sleigh, Garlands, 2026 Palace) in 'spawn_world'...");
            boolean needClear = forceRebuild || stoneBugDetected || !marker.exists()
                    || new File(sw.getWorldFolder(), ".spawn_v4_built").exists()
                    || new File(sw.getWorldFolder(), ".spawn_v5_built").exists()
                    || new File(sw.getWorldFolder(), ".spawn_v5_colorful_ok").exists();
            clearOldBlocksIfUpgrade(sw, needClear);
            buildFromRleResource(sw, "/spawn_v5.dat");
            try { marker.createNewFile(); } catch (Exception ignored) {}
        }
        sw.setTime(6000L);
        sw.setStorm(false);
        sw.setThundering(false);
        placeShulkerCases(sw);
        refillAutoMine(sw, false);
        rebuildAllHologramsAndNpcs(sw);
        startEngineLoop();
    }

    private void clearOldBlocksIfUpgrade(World sw, boolean shouldClear) {
        if (!shouldClear) return;
        for (int x = -121; x <= 121; x++) {
            for (int z = -121; z <= 121; z++) {
                for (int y = 62; y <= 188; y++) {
                    Block b = sw.getBlockAt(x, y, z);
                    if (b.getType() != Material.AIR) b.setType(Material.AIR, false);
                }
            }
        }
    }

    private BlockData parsePaletteBlockData(String rawSpec) {
        if (rawSpec == null || rawSpec.isEmpty()) return Material.AIR.createBlockData();
        String clean = rawSpec.trim();
        if (clean.regionMatches(true, 0, "minecraft:", 0, 10)) {
            clean = clean.substring(10);
        }
        String lower = clean.toLowerCase(Locale.ROOT);
        if ("air".equals(lower)) return Material.AIR.createBlockData();
        try {
            BlockData bd = Bukkit.createBlockData("minecraft:" + lower);
            if (bd instanceof org.bukkit.block.data.type.Leaves) {
                ((org.bukkit.block.data.type.Leaves) bd).setPersistent(true);
            }
            return bd;
        } catch (Throwable ignored) {
        }
        String baseName = lower.contains("[") ? lower.substring(0, lower.indexOf('[')) : lower;
        Material m = Material.getMaterial(baseName.toUpperCase(Locale.ROOT));
        if (m == null || !m.isBlock()) {
            m = ColorUtil.mat(baseName, "SNOW_BLOCK");
        }
        BlockData bd = m.createBlockData();
        if (bd instanceof org.bukkit.block.data.type.Leaves) {
            ((org.bukkit.block.data.type.Leaves) bd).setPersistent(true);
        }
        return bd;
    }

    private void buildFromRleResource(World sw, String resPath) {
        InputStream raw = KingGriefCore.class.getResourceAsStream(resPath);
        if (raw == null) {
            plugin.getLogger().warning("Missing " + resPath + " in jar!");
            return;
        }
        try {
            DataInputStream dis = new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw)));
            int W = dis.readShort();
            int H = dis.readShort();
            int L = dis.readShort();
            int OX = dis.readShort();
            int OY = dis.readShort();
            int OZ = dis.readShort();
            int palCount = dis.readUnsignedShort();
            BlockData[] palData = new BlockData[palCount];
            for (int i = 0; i < palCount; i++) {
                String spec = dis.readUTF();
                palData[i] = parsePaletteBlockData(spec);
            }
            int baseY = 100 - OY;
            int baseX = -OX;
            int baseZ = -OZ;
            int HL = H * L;
            long cursor = 0L;
            long total = (long) W * H * L;

            // Note: build_spawn_v5.py uses grid[lx, ly, lz].reshape(-1), so stride is (lx * H * L) + (ly * L) + lz!
            while (cursor < total) {
                int pIdx = dis.readUnsignedByte();
                int runLen = dis.readUnsignedShort();
                if (pIdx == 0 && runLen == 0) break;
                if (pIdx == 0) {
                    cursor += runLen;
                    continue;
                }
                BlockData bd = palData[pIdx];
                for (int r = 0; r < runLen && cursor < total; r++, cursor++) {
                    int lx = (int) (cursor / HL);
                    int rem = (int) (cursor % HL);
                    int ly = rem / L;
                    int lz = rem % L;
                    sw.getBlockAt(baseX + lx, baseY + ly, baseZ + lz).setBlockData(bd, false);
                }
            }
            dis.close();
            for (KabinkaState kb : kabinki) {
                setKabinkaDoorBlocks(sw, kb, false);
            }
            plugin.getLogger().info("[SpawnEngine v5.0] Colorful 241x124x241 Spawn built cleanly in 'spawn_world'!");
        } catch (Exception e) {
            plugin.getLogger().warning("[SpawnEngine v5.0] Error decoding spawn_v5.dat: " + e.getMessage());
        }
    }

    private void placeShulkerCases(World sw) {
        for (CasePedestal cp : casePedestals) {
            Block b = sw.getBlockAt(cp.x, cp.y, cp.z);
            b.setType(cp.shulkerMat, false);
            try {
                BlockData bd = b.getBlockData();
                if (bd instanceof Directional) {
                    ((Directional) bd).setFacing(BlockFace.UP);
                    b.setBlockData(bd, false);
                }
            } catch (Throwable ignored) {}
        }
    }

    public void rebuildAllHologramsAndNpcs(World sw) {
        if (sw == null) return;
        for (int cx = -8; cx <= 8; cx++) {
            for (int cz = -8; cz <= 8; cz++) {
                sw.getChunkAt(cx, cz).load();
            }
        }
        // Purge ALL previous holograms, NPCs, hitboxes, and ANY vanilla Villagers/Witches!
        for (Entity e : sw.getEntities()) {
            if (e.getScoreboardTags().contains("kg_holo")
                    || e.getScoreboardTags().contains("kg_npc")
                    || e.getScoreboardTags().contains("kg_npc_body")
                    || e.getScoreboardTags().contains("kg_case_float")
                    || e instanceof AbstractVillager) {
                e.remove();
            }
        }
        npcBodyMap.clear();
        npcHitboxMap.clear();

        // 1. Central Welcome Hologram
        spawnHoloStack(sw, 0.5, 104.2, 47.5,
                "\uE976 &#FF1744&lKINGGRIEF v5.0 &8· &#80D8FF&lFT × REALLYWORLD &f\uE976",
                "&#FFD740★ Яркое Зимнее Королевство (Отдельный Мир) ★",
                "&fВход в мир выживания: &#00E676&l/rtp &8│ &fШоп: &#00E5FF/shop &8│ &fБафовар: &#FF9100/bafs",
                "&fСкины Трапок: &#E040FB/skins &8│ &fСайт и Промокоды: &#FF4081kinggrief.vercel.app");

        // 2. Holograms above all 11 Shulker Cases
        for (CasePedestal cp : casePedestals) {
            spawnHoloStack(sw, cp.x + 0.5, cp.y + 2.05, cp.z + 0.5,
                    cp.title + " &8[&#FFD740" + cp.priceRub + "₽&8]",
                    cp.subtitle,
                    "&#00E676▸ ПКМ — Открыть &8│ &#80D8FFЛКМ — Призы",
                    "&7Сайт: &#FF4081kinggrief.vercel.app");
        }
        spawnHoloStack(sw, 0.5, 107.0, -56.5,
                "&#FFD740&l✨ КОРОЛЕВСКАЯ ТЕРРАСА 11 ШАЛКЕР-КЕЙСОВ ✨",
                "&7Нажми по любому цветному Шалкеру для 3D-прокрутки рулетки!");

        // 3. Island & Zone Holograms
        spawnHoloStack(sw, -71.5, 103.0, 4.5,
                "&#FF9100&l⛏ КРИСТАЛЬНАЯ АВТО-ШАХТА",
                "&7Ломай руду → получай &aМонеты&7, &bОпыт &7и &b3D Снежинки ❄&7!",
                "&eАвто-обновление каждые 90 секунд!");
        spawnHoloStack(sw, -80.0, 103.5, -36.5,
                "&#AEEA00&l🧟 БЕЗОПАСНАЯ ФАРМИЛКА МОБОВ (4 КАМЕРЫ)",
                "&7Бей зимних мобов — фарми &aМонеты &7и &b3D Снежинки ❄&7!");
        spawnHoloStack(sw, -64.5, 103.5, 50.5,
                "&#FFD740&l🎰 ГРАНД-КАЗИНО 777",
                "&7Слоты 777 · Рулетка ×14 · Монетка PvP (/cf) · Колесо Риликов");
        spawnHoloStack(sw, 65.5, 103.5, 50.5,
                "&#FF9100&l⚗ ПАВИЛЬОН АЛХИМИКА БАФОВАРА (/bafs)",
                "&7Смешай от 1 до 8 любых зелий в один флакон за &a5 000$&7!",
                "&7Или купи готовые зелья Киллера, Медика, Отрыжки и Кислоты!");
        spawnHoloStack(sw, 64.5, 103.5, 17.5,
                "&#FF1744&l⚔ ЗОЛОТОЙ КОЛИЗЕЙ FT × RW",
                "&7Прыгай вниз для открытого PvP с Пластами и Трапками!");
        spawnHoloStack(sw, 38.5, 103.2, -26.5,
                "&#40C4FF&l🏃 РАДУЖНЫЙ ПАРКУР (40 ОСТРОВОВ — 100% ПРОХОДИМЫЙ!)",
                "&7Дойди до вершины (Y=131) → &#00E676+25 000$ + 15❖ + 15❄ + Титул!",
                "&7Упал? Возврат на чекпоинт: &f/parkour cp");

        // 4. Live Status Holograms above all 8 Duel Kabinki
        for (KabinkaState kb : kabinki) {
            Location hl = new Location(sw, kb.doorX - 0.3, 102.65, kb.doorZ + 0.5);
            ArmorStand st = spawnSingleStand(hl, formatKabinkaTitle(kb, 0));
            kb.statusStandId = st.getUniqueId();
        }

        // 5. Spawn all 24 Custom-Skinned Animated Humanoid NPCs + Holograms!
        for (NpcDef nd : npcDefs) {
            ensureNpcAlive(sw, nd, true);
        }
    }

    private ItemStack makeSkinnedHead(String textureHash) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        if (sm != null && textureHash != null && !textureHash.isEmpty()) {
            try {
                PlayerProfile prof = Bukkit.createPlayerProfile(UUID.nameUUIDFromBytes(("kg_skin_" + textureHash).getBytes()), "KG_NPC");
                PlayerTextures tex = prof.getTextures();
                tex.setSkin(new URL("http://textures.minecraft.net/texture/" + textureHash));
                prof.setTextures(tex);
                sm.setOwnerProfile(prof);
            } catch (Throwable ignored) {
            }
            head.setItemMeta(sm);
        }
        return head;
    }

    private ItemStack dyedLeather(Material mat, Color color) {
        ItemStack it = new ItemStack(mat, 1);
        LeatherArmorMeta lam = (LeatherArmorMeta) it.getItemMeta();
        if (lam != null) {
            lam.setColor(color);
            it.setItemMeta(lam);
        }
        return it;
    }

    private void ensureNpcAlive(World sw, NpcDef nd, boolean buildHolo) {
        Location loc = new Location(sw, nd.x, nd.y, nd.z, nd.yaw, 0f);
        if (buildHolo) {
            int bx = (int) Math.floor(nd.x);
            int bz = (int) Math.floor(nd.z);
            int floorY = (int) Math.floor(nd.y) - 1;
            sw.getBlockAt(bx, floorY, bz).setType(Material.GOLD_BLOCK, false);
        }

        UUID bodyUuid = npcBodyMap.get(nd.id);
        Entity existingBody = bodyUuid != null ? Bukkit.getEntity(bodyUuid) : null;
        if (!(existingBody instanceof ArmorStand) || existingBody.isDead() || !existingBody.isValid()) {
            if (existingBody != null) existingBody.remove();
            ArmorStand body = (ArmorStand) sw.spawnEntity(loc, EntityType.ARMOR_STAND);
            body.setBasePlate(false);
            body.setArms(true);
            body.setGravity(false);
            body.setVisible(true);
            body.setInvulnerable(true);
            body.setPersistent(true);
            body.setCustomNameVisible(false);
            body.addScoreboardTag("kg_npc_body");
            body.addScoreboardTag("kg_npc_" + nd.id);

            EntityEquipment eq = body.getEquipment();
            if (eq != null) {
                eq.setHelmet(makeSkinnedHead(nd.skinHash));
                eq.setChestplate(nd.chestOverride != null ? nd.chestOverride.clone() : dyedLeather(Material.LEATHER_CHESTPLATE, nd.armorColor));
                eq.setLeggings(dyedLeather(Material.LEATHER_LEGGINGS, nd.armorColor));
                eq.setBoots(dyedLeather(Material.LEATHER_BOOTS, Color.fromRGB(35, 35, 45)));
                if (nd.mainHand != null) eq.setItemInMainHand(nd.mainHand.clone());
                if (nd.offHand != null) eq.setItemInOffHand(nd.offHand.clone());
            }
            npcBodyMap.put(nd.id, body.getUniqueId());
        }

        UUID hitUuid = npcHitboxMap.get(nd.id);
        Entity existingHit = hitUuid != null ? Bukkit.getEntity(hitUuid) : null;
        if (!(existingHit instanceof Interaction) || existingHit.isDead() || !existingHit.isValid()) {
            if (existingHit != null) existingHit.remove();
            Interaction hit = (Interaction) sw.spawnEntity(loc, EntityType.INTERACTION);
            hit.setInteractionWidth(1.15f);
            hit.setInteractionHeight(2.15f);
            hit.setResponsive(true);
            hit.setPersistent(true);
            hit.addScoreboardTag("kg_npc");
            hit.addScoreboardTag("kg_npc_" + nd.id);
            npcHitboxMap.put(nd.id, hit.getUniqueId());
        }

        if (buildHolo) {
            spawnHoloStack(sw, nd.x, nd.y + 2.48, nd.z,
                    nd.line1,
                    nd.line2,
                    "&#00E676▸ Нажми ПКМ / ЛКМ — Открыть лавку");
        }
    }

    private void spawnHoloStack(World w, double x, double topY, double z, String... lines) {
        for (int i = 0; i < lines.length; i++) {
            spawnSingleStand(new Location(w, x, topY - i * 0.28, z), lines[i]);
        }
    }

    private ArmorStand spawnSingleStand(Location loc, String text) {
        ArmorStand as = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        as.setVisible(false);
        as.setGravity(false);
        as.setMarker(true);
        as.setSmall(true);
        as.setInvulnerable(true);
        as.setPersistent(true);
        as.setCustomName(ColorUtil.c(text));
        as.setCustomNameVisible(true);
        as.addScoreboardTag("kg_holo");
        return as;
    }

    private void startEngineLoop() {
        // 1-second world systems loop
        new BukkitRunnable() {
            @Override
            public void run() {
                tickSecond();
            }
        }.runTaskTimer(plugin, 20L, 20L);

        // 5-FPS (every 4 ticks) smooth Custom-Skinned NPC humanoid animation!
        new BukkitRunnable() {
            @Override
            public void run() {
                animateCustomNpcs();
            }
        }.runTaskTimer(plugin, 10L, 4L);
    }

    private void animateCustomNpcs() {
        animPhase++;
        World sw = Bukkit.getWorld(VoidSpawnWorldGenerator.SPAWN_WORLD_NAME);
        if (sw == null || sw.getPlayers().isEmpty()) return;

        for (int i = 0; i < npcDefs.size(); i++) {
            NpcDef nd = npcDefs.get(i);
            UUID bid = npcBodyMap.get(nd.id);
            if (bid == null) continue;
            Entity ent = Bukkit.getEntity(bid);
            if (!(ent instanceof ArmorStand) || !ent.isValid()) continue;
            ArmorStand as = (ArmorStand) ent;

            Player nearest = null;
            double bestD2 = 64.0;
            for (Player p : sw.getPlayers()) {
                double d2 = p.getLocation().distanceSquared(as.getLocation());
                if (d2 < bestD2) {
                    bestD2 = d2;
                    nearest = p;
                }
            }

            double t = (animPhase + i * 3) * 0.28;
            if (nearest != null) {
                Location nl = as.getLocation();
                Vector dir = nearest.getLocation().toVector().subtract(nl.toVector());
                nl.setDirection(dir);
                nl.setPitch(0f);
                as.teleport(nl);
                // Enthusiastic greeting wave with right arm when player is within 5.5 blocks!
                if (bestD2 < 30.0) {
                    as.setRightArmPose(new EulerAngle(-1.85 + Math.sin(t * 2.2) * 0.35, 0.15, 0.25 + Math.cos(t * 2.2) * 0.22));
                } else {
                    as.setRightArmPose(new EulerAngle(-0.45 + Math.sin(t) * 0.12, 0.0, 0.10));
                }
                as.setHeadPose(new EulerAngle(Math.sin(t * 0.7) * 0.06, 0.0, Math.cos(t * 0.5) * 0.05));
            } else {
                as.setRightArmPose(new EulerAngle(-0.35 + Math.sin(t) * 0.10, 0.0, 0.08));
                as.setHeadPose(new EulerAngle(Math.sin(t * 0.5) * 0.05, Math.sin(t * 0.35) * 0.18, 0.0));
            }
            as.setLeftArmPose(new EulerAngle(-0.28 - Math.sin(t) * 0.10, 0.0, -0.08));
        }
    }

    private void tickSecond() {
        tickCounter++;
        World sw = Bukkit.getWorld(VoidSpawnWorldGenerator.SPAWN_WORLD_NAME);
        if (sw == null) return;

        mineRefillCountdown--;
        if (mineRefillCountdown <= 0) {
            mineRefillCountdown = 90;
            refillAutoMine(sw, true);
        }

        tickKabinki(sw);

        if (tickCounter % 4 == 0) {
            tickMobFarm(sw);
        }

        if (tickCounter % 5 == 0) {
            for (NpcDef nd : npcDefs) {
                ensureNpcAlive(sw, nd, false);
            }
        }

        // Void rescue in spawn_world -> ALWAYS keeps player inside spawn_world (ONLY /rtp enters survival world!)
        for (Player p : sw.getPlayers()) {
            if (p.getLocation().getY() < 62.0) {
                Location cp = plugin.getStore().getParkourCp(p);
                if (cp != null && isInSpawnWorld(cp)) {
                    p.teleport(cp);
                    ColorUtil.actionBar(p, "&#40C4FF🏃 Возврат на чекпоинт паркура!");
                } else {
                    p.teleport(getSpawnArrival());
                    ColorUtil.actionBar(p, "&#80D8FF❄ Вы возвращены на спавн! Вход в обычный мир — только через /rtp!");
                }
                p.setFallDistance(0f);
            }
            p.spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 5.5, 0), 14, 7.5, 3.0, 7.5, 0.01);
            if (tickCounter % 2 == 0) {
                p.spawnParticle(Particle.END_ROD, p.getLocation().add(0, 4.5, 0), 4, 6.0, 2.2, 6.0, 0.01);
            }
        }
        sw.setTime(6000L);
    }

    private void tickKabinki(World sw) {
        for (KabinkaState kb : kabinki) {
            List<Player> inside = new ArrayList<Player>();
            for (Player p : sw.getPlayers()) {
                if (!p.isDead() && kb.contains(p.getLocation())) {
                    inside.add(p);
                }
            }
            int cap = kb.is2v2 ? 4 : 2;
            while (inside.size() > cap) {
                Player extra = inside.remove(inside.size() - 1);
                extra.teleport(new Location(sw, kb.doorX - 1.5, 100.0, kb.doorZ + 0.5, -90f, 0f));
                extra.sendMessage(ColorUtil.c("&#FF1744✖ Кабинка #" + kb.index + " уже заполнена (" + cap + "/" + cap + ")!"));
            }

            if (kb.lootLockSec > 0) {
                kb.lootLockSec--;
            }

            boolean shouldLock;
            if (inside.size() >= cap) {
                shouldLock = true;
            } else if (kb.locked && inside.size() >= 2 && kb.is2v2) {
                shouldLock = true;
            } else if (kb.lootLockSec > 0 && !inside.isEmpty()) {
                shouldLock = true;
            } else {
                shouldLock = false;
                kb.lootLockSec = 0;
            }

            if (shouldLock != kb.locked) {
                kb.locked = shouldLock;
                setKabinkaDoorBlocks(sw, kb, shouldLock);
                for (Player p : inside) {
                    ColorUtil.sound(p, shouldLock ? "block.anvil.place" : "block.iron_door.open", 0.9f, 1.1f);
                    p.sendMessage(ColorUtil.c(shouldLock
                            ? "&#FF1744⚔ Кабинка #" + kb.index + " ЗАКРЫТА! Бой начался!"
                            : "&#00E676🔓 Кабинка #" + kb.index + " ОТКРЫТА!"));
                }
            }

            Entity stand = kb.statusStandId != null ? Bukkit.getEntity(kb.statusStandId) : null;
            if (stand != null) {
                stand.setCustomName(ColorUtil.c(formatKabinkaTitle(kb, inside.size())));
            }
        }
    }

    private String formatKabinkaTitle(KabinkaState kb, int count) {
        String mode = kb.is2v2 ? "2×2" : "1×1";
        int cap = kb.is2v2 ? 4 : 2;
        if (kb.lootLockSec > 0) {
            return "&#FFD740⏳ КАБИНКА #" + kb.index + " [" + mode + "] &8— &eЛУТ-ТАЙМЕР " + kb.lootLockSec + "с";
        }
        if (kb.locked) {
            return "&#FF1744⚔ КАБИНКА #" + kb.index + " [" + mode + "] &8— &cИДЁТ БОЙ (" + count + "/" + cap + ")";
        }
        return "&#00E676🔓 КАБИНКА #" + kb.index + " [" + mode + "] &8— &aСВОБОДНА (" + count + "/" + cap + ")";
    }

    private void setKabinkaDoorBlocks(World sw, KabinkaState kb, boolean closed) {
        Material m = closed ? Material.RED_STAINED_GLASS : Material.AIR;
        sw.getBlockAt(kb.doorX, 100, kb.doorZ).setType(m, false);
        sw.getBlockAt(kb.doorX, 101, kb.doorZ).setType(m, false);
    }

    public void notifyKabinkaDeath(Location loc) {
        for (KabinkaState kb : kabinki) {
            if (kb.contains(loc)) {
                kb.lootLockSec = 8;
            }
        }
    }

    private void tickMobFarm(World sw) {
        int[][] cells = new int[][]{{-92, -42}, {-84, -42}, {-76, -42}, {-68, -42}};
        EntityType[] types = new EntityType[]{EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.BLAZE};
        String[] names = new String[]{
                "&#80D8FF❄ Зимний Зомби", "&#E0F7FA🦴 Ледяной Скелет",
                "&#B388FF🕷 Морозный Паук", "&#FF9100🔥 Новогодний Ифрит"
        };

        for (int i = 0; i < cells.length; i++) {
            Location center = new Location(sw, cells[i][0] + 0.5, 100.0, cells[i][1] + 0.5);
            if (!center.getChunk().isLoaded()) continue;
            int count = 0;
            for (Entity e : sw.getNearbyEntities(center, 2.5, 2.5, 2.5)) {
                if (e.getScoreboardTags().contains("kg_farm_mob") && !e.isDead()) count++;
            }
            if (count < 3) {
                LivingEntity mob = (LivingEntity) sw.spawnEntity(center, types[i]);
                if (mob instanceof Mob) ((Mob) mob).setAware(false);
                mob.setAI(false);
                mob.setSilent(true);
                mob.setRemoveWhenFarAway(false);
                mob.setCustomName(ColorUtil.c(names[i]));
                mob.setCustomNameVisible(true);
                mob.addScoreboardTag("kg_farm_mob");
            }
        }
    }

    public void refillAutoMine(World sw, boolean announce) {
        if (sw == null) return;
        for (Player p : sw.getPlayers()) {
            if (isInsideAutoMine(p.getLocation())) {
                p.teleport(getWarpLocation("mine"));
                ColorUtil.actionBar(p, "&#FF9100⛏ Авто-Шахта обновлена! Вы подняты наверх.");
            }
        }
        Material[] ores = new Material[]{
                Material.QUARTZ_BLOCK, Material.COAL_ORE, Material.IRON_ORE,
                Material.GOLD_ORE, Material.LAPIS_ORE, Material.REDSTONE_ORE,
                Material.DIAMOND_ORE, Material.EMERALD_ORE
        };
        for (int x = -96; x <= -75; x++) {
            for (int y = 91; y <= 98; y++) {
                for (int z = -6; z <= 14; z++) {
                    // Preserve exit stairs at x=-74..-66, z=2..6
                    Material pick = rng.nextInt(85) == 0 ? Material.ANCIENT_DEBRIS : ores[rng.nextInt(ores.length)];
                    sw.getBlockAt(x, y, z).setType(pick, false);
                }
            }
        }
        if (announce && !sw.getPlayers().isEmpty()) {
            for (Player p : sw.getPlayers()) {
                p.sendMessage(ColorUtil.c("&#FF9100[Авто-Шахта] &fРуда на &e/warp mine &fполностью обновилась!"));
            }
        }
    }

    // ================= ZONE HELPERS (v5.0 EXACT COORDINATES) =================
    public Location getSpawnArrival() {
        World sw = VoidSpawnWorldGenerator.getOrCreateSpawnWorld();
        return new Location(sw, 0.5, 100.0, 52.5, 180.0f, 0.0f);
    }

    public Location getWarpLocation(String warpName) {
        World sw = VoidSpawnWorldGenerator.getOrCreateSpawnWorld();
        String w = warpName == null ? "" : warpName.toLowerCase(Locale.ROOT);
        if ("mine".equals(w) || "шахта".equals(w)) return new Location(sw, -68.5, 100.0, 4.5, 90.0f, 10.0f);
        if ("farm".equals(w) || "фарм".equals(w)) return new Location(sw, -80.0, 100.0, -35.5, 180.0f, 0.0f);
        if ("casino".equals(w) || "казино".equals(w)) return new Location(sw, -62.5, 100.0, 50.5, 90.0f, 0.0f);
        if ("enchant".equals(w) || "enchants".equals(w) || "bafs".equals(w) || "чары".equals(w)) return new Location(sw, 64.5, 100.0, 50.5, -90.0f, 0.0f);
        if ("pvp".equals(w) || "пвп".equals(w) || "arena".equals(w)) return new Location(sw, 63.5, 100.0, 17.5, -90.0f, 10.0f);
        if ("kabinki".equals(w) || "duels".equals(w)) return new Location(sw, 64.5, 100.0, -24.5, -90.0f, 0.0f);
        if ("parkour".equals(w) || "паркур".equals(w)) return new Location(sw, 38.5, 100.0, -24.5, 180.0f, 0.0f);
        if ("cases".equals(w) || "кейсы".equals(w)) return new Location(sw, 0.5, 102.0, -54.5, 180.0f, 0.0f);
        return null;
    }

    public boolean isInSpawnWorld(Location loc) {
        return loc != null && loc.getWorld() != null
                && loc.getWorld().getName().equalsIgnoreCase(VoidSpawnWorldGenerator.SPAWN_WORLD_NAME);
    }

    public boolean isInsideAutoMine(Location loc) {
        if (!isInSpawnWorld(loc)) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        return x >= -96 && x <= -75 && y >= 91 && y <= 98 && z >= -6 && z <= 14;
    }

    public boolean isInsidePvpZone(Location loc) {
        if (!isInSpawnWorld(loc)) return true; // PvP enabled in survival worlds!
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        // Sunken Gladiator Colosseum pit (X=66..102, Y=91..98, Z=4..38)
        if (x >= 66 && x <= 102 && y >= 91 && y <= 98 && z >= 4 && z <= 38) {
            return true;
        }
        // Any of the 8 Duel Kabinki interiors
        for (KabinkaState kb : kabinki) {
            if (kb.contains(loc)) return true;
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
        if (e == null || e instanceof Player) return null;
        if (e.getScoreboardTags().contains("kg_holo")) return null;
        for (NpcDef nd : npcDefs) {
            if (e.getScoreboardTags().contains("kg_npc_" + nd.id)) return nd;
        }
        return null;
    }

    public int getAirdropSecondsLeft() {
        return plugin.getBossEvents() != null ? plugin.getBossEvents().getNextAutoSeconds() : 300;
    }

    // ================= SEPARATE WORLD TIERED RTP (/rtp + /rtp near) =================
    public void performRtp(Player p, String mode) {
        String mKey = mode == null ? "close" : mode.toLowerCase(Locale.ROOT);
        RankManager.RankDef rank = plugin.getRanks().getRank(p);

        if ("near".equals(mKey)) {
            if (rank.weight < 90) {
                ColorUtil.sound(p, "entity.villager.no", 1.0f, 1.0f);
                p.sendMessage(ColorUtil.c("&#FF1744✖ Команда &f/rtp near &#FF1744доступна с привилегии &#FDE047&lKING &#FF1744и выше!"));
                p.sendMessage(ColorUtil.c("&7Приобрести привилегию: &#FF4081https://kinggrief.vercel.app"));
                return;
            }
            World surv = VoidSpawnWorldGenerator.getSurvivalWorld();
            if (surv == null) {
                p.sendMessage(ColorUtil.c("&cМир выживания недоступен!"));
                return;
            }
            List<Player> targets = new ArrayList<Player>();
            for (Player other : surv.getPlayers()) {
                if (other.getUniqueId().equals(p.getUniqueId())) continue;
                if (other.isDead() || other.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
                if (plugin.getStore().isVanished(other)) continue;
                targets.add(other);
            }
            if (targets.isEmpty()) {
                p.sendMessage(ColorUtil.c("&#FFD740[РТП NEAR] &fВ мире выживания сейчас нет других игроков — выполняем &#FDE047Дальнюю РТП&f!"));
                mKey = "high";
            } else {
                Player victim = targets.get(rng.nextInt(targets.size()));
                Location vLoc = victim.getLocation();
                for (int att = 0; att < 30; att++) {
                    double ang = rng.nextDouble() * Math.PI * 2.0;
                    double dist = 55.0 + rng.nextDouble() * 55.0; // 55..110 blocks away
                    int tx = vLoc.getBlockX() + (int) Math.round(Math.cos(ang) * dist);
                    int tz = vLoc.getBlockZ() + (int) Math.round(Math.sin(ang) * dist);
                    Location safeNear = findDrySurfaceLocation(surv, tx, tz);
                    if (safeNear != null) {
                        plugin.getStore().setBack(p, p.getLocation());
                        plugin.getStore().setParkourCp(p, null);
                        p.teleport(safeNear);
                        ColorUtil.addPot(p, "resistance", "DAMAGE_RESISTANCE", 100, 4);
                        ColorUtil.sound(p, "entity.enderman.teleport", 1.0f, 1.2f);
                        int realDist = (int) Math.round(safeNear.distance(vLoc));
                        p.sendTitle(ColorUtil.c("&#FF5252&l🎯 РТП РЯДОМ С ИГРОКОМ!"),
                                ColorUtil.c("&fДо цели &#FFD740~" + realDist + " м &8│ &e" + safeNear.getBlockX() + ", " + safeNear.getBlockZ()), 5, 45, 10);
                        p.sendMessage(ColorUtil.c("&#FF5252[РТП NEAR] &fВы телепортированы в &#FFD740" + realDist
                                + " блоках &fот игрока в мире выживания! XYZ: &#80D8FF"
                                + safeNear.getBlockX() + ", " + safeNear.getBlockY() + ", " + safeNear.getBlockZ()));
                        return;
                    }
                }
                p.sendMessage(ColorUtil.c("&#FFD740[РТП NEAR] &fИгрок находится над водой — выполняем безопасную РТП на сушу!"));
                mKey = "high";
            }
        }

        if ("medium".equals(mKey) || "mid".equals(mKey)) {
            if (rank.weight < 65) {
                ColorUtil.sound(p, "entity.villager.no", 1.0f, 1.0f);
                p.sendMessage(ColorUtil.c("&#FF1744✖ Средняя телепортация (1 500–3 500 бл.) доступна с привилегии &#FEF9C3&lGOD &#FF1744и выше!"));
                return;
            }
        } else if ("high".equals(mKey) || "far".equals(mKey)) {
            if (rank.weight < 90) {
                ColorUtil.sound(p, "entity.villager.no", 1.0f, 1.0f);
                p.sendMessage(ColorUtil.c("&#FF1744✖ Высокая (Дальняя) телепортация (4 000–8 000 бл.) доступна с привилегии &#FDE047&lKING &#FF1744и выше!"));
                return;
            }
        }

        World targetWorld;
        int minDist;
        int maxDist;
        String tierLabel;
        if ("nether".equals(mKey)) {
            targetWorld = VoidSpawnWorldGenerator.getNetherWorld();
            if (targetWorld == null) targetWorld = VoidSpawnWorldGenerator.getSurvivalWorld();
            minDist = 180;
            maxDist = 850;
            tierLabel = "&#FF5252АДСКИЙ РТП";
        } else if ("high".equals(mKey) || "far".equals(mKey)) {
            targetWorld = VoidSpawnWorldGenerator.getSurvivalWorld();
            minDist = 4000;
            maxDist = 8000;
            tierLabel = "&#FDE047ДАЛЬНЯЯ РТП [KING]";
        } else if ("medium".equals(mKey) || "mid".equals(mKey)) {
            targetWorld = VoidSpawnWorldGenerator.getSurvivalWorld();
            minDist = 1500;
            maxDist = 3500;
            tierLabel = "&#38BDF8СРЕДНЯЯ РТП [GOD]";
        } else {
            targetWorld = VoidSpawnWorldGenerator.getSurvivalWorld();
            minDist = 300;
            maxDist = 1200;
            tierLabel = "&#00E676БЛИЗКАЯ РТП";
        }

        if (targetWorld == null) {
            p.sendMessage(ColorUtil.c("&cМир выживания недоступен!"));
            return;
        }

        for (int attempt = 0; attempt < 40; attempt++) {
            double ang = rng.nextDouble() * Math.PI * 2.0;
            double rDist = minDist + rng.nextDouble() * (maxDist - minDist);
            int x = (int) Math.round(Math.cos(ang) * rDist);
            int z = (int) Math.round(Math.sin(ang) * rDist);
            Location safe = (targetWorld.getEnvironment() == World.Environment.NETHER)
                    ? findNetherSafe(targetWorld, x, z)
                    : findDrySurfaceLocation(targetWorld, x, z);
            if (safe == null) continue;
            plugin.getStore().setBack(p, p.getLocation());
            plugin.getStore().setParkourCp(p, null);
            p.teleport(safe);
            ColorUtil.addPot(p, "resistance", "DAMAGE_RESISTANCE", 100, 4);
            ColorUtil.sound(p, "entity.enderman.teleport", 1.0f, 1.15f);
            p.sendTitle(ColorUtil.c(tierLabel),
                    ColorUtil.c("&fXYZ: &e" + safe.getBlockX() + ", " + safe.getBlockY() + ", " + safe.getBlockZ()), 5, 45, 10);
            p.sendMessage(ColorUtil.c("&#00E676[РТП] &fТелепортация (" + tierLabel + "&f) выполнена на сушу: &#80D8FF"
                    + safe.getBlockX() + ", " + safe.getBlockY() + ", " + safe.getBlockZ()));
            return;
        }
        Location fb = targetWorld.getHighestBlockAt(420, 420).getLocation().add(0.5, 1.0, 0.5);
        p.teleport(fb);
    }

    private Location findDrySurfaceLocation(World w, int x, int z) {
        w.getChunkAt(x >> 4, z >> 4).load();
        String bio = String.valueOf(w.getBiome(x, 68, z)).toUpperCase(Locale.ROOT);
        if (bio.contains("OCEAN") || bio.contains("RIVER")) return null;
        Block b = w.getHighestBlockAt(x, z);
        Material m = b.getType();
        if (!m.isSolid() || m == Material.WATER || m == Material.LAVA || m == Material.KELP
                || m == Material.SEAGRASS || m == Material.MAGMA_BLOCK || m == Material.CACTUS) {
            return null;
        }
        if (b.getY() < 61) return null;
        Material above1 = w.getBlockAt(x, b.getY() + 1, z).getType();
        Material above2 = w.getBlockAt(x, b.getY() + 2, z).getType();
        if (above1 == Material.WATER || above1 == Material.LAVA || above2 == Material.WATER || above2 == Material.LAVA) {
            return null;
        }
        return b.getLocation().add(0.5, 1.0, 0.5);
    }

    private Location findNetherSafe(World w, int x, int z) {
        for (int y = 35; y < 105; y++) {
            Material floor = w.getBlockAt(x, y, z).getType();
            Material b1 = w.getBlockAt(x, y + 1, z).getType();
            Material b2 = w.getBlockAt(x, y + 2, z).getType();
            if (floor.isSolid() && floor != Material.LAVA && floor != Material.MAGMA_BLOCK && b1 == Material.AIR && b2 == Material.AIR) {
                return new Location(w, x + 0.5, y + 1.0, z + 0.5);
            }
        }
        return null;
    }

    // ================= ANIMATED SHULKER CASE ROULETTE =================
    public void openShulkerCaseAnimated(final Player p, final CasePedestal cp) {
        int keys = plugin.getStore().getCaseKeys(p.getName(), cp.caseId);
        if (keys <= 0) {
            ColorUtil.sound(p, "entity.villager.no", 1.0f, 1.0f);
            p.sendMessage(ColorUtil.c("&#FF1744✖ У вас нет ключей от " + cp.title + "&#FF1744!"));
            p.sendMessage(ColorUtil.c("&7Купите на сайте от &#FFD740" + cp.priceRub + "₽&7: &#FF4081https://kinggrief.vercel.app &7или в &f/cases"));
            return;
        }
        final String lockKey = cp.caseId;
        if (spinningCases.contains(lockKey)) {
            p.sendMessage(ColorUtil.c("&eЭтот Шалкер-Кейс прямо сейчас крутится! Подождите 3 секунды."));
            return;
        }
        plugin.getStore().addCaseKeys(p.getName(), cp.caseId, -1);
        plugin.getStore().save();
        spinningCases.add(lockKey);

        final World sw = VoidSpawnWorldGenerator.getOrCreateSpawnWorld();
        final Location center = new Location(sw, cp.x + 0.5, cp.y + 1.35, cp.z + 0.5);
        final ItemStack[] spinItems = new ItemStack[]{
                ItemFactory.sharSanty(), ItemFactory.elytraSanta(), ItemFactory.swordKrush(),
                ItemFactory.talFeniksa(3), ItemFactory.plast(4), ItemFactory.trapka(4), ItemFactory.pickDjamshut()
        };
        final Item floating = sw.dropItem(center, spinItems[0]);
        floating.setPickupDelay(Integer.MAX_VALUE);
        floating.setGravity(false);
        floating.setVelocity(new Vector(0, 0, 0));
        floating.setCustomNameVisible(true);
        floating.addScoreboardTag("kg_case_float");

        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                step++;
                if (step <= 14) {
                    floating.setItemStack(spinItems[step % spinItems.length]);
                    floating.setCustomName(ColorUtil.c("&#FFD740✦ Крутится " + cp.title + " &#FFD740✦"));
                    floating.teleport(center);
                    sw.spawnParticle(Particle.END_ROD, center, 6, 0.25, 0.25, 0.25, 0.02);
                    sw.playSound(center, "minecraft:block.note_block.pling", 0.9f, 0.8f + step * 0.08f);
                    return;
                }
                cancel();
                floating.remove();
                spinningCases.remove(lockKey);
                grantCasePrize(p, cp);
                ColorUtil.spawnFirework(center);
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    public void grantCasePrize(Player p, CasePedestal cp) {
        String cid = cp.caseId;
        String prizeText;
        if ("donate".equals(cid) || "sdonate".equals(cid) || "reallydev".equals(cid)) {
            String[] pool = "sdonate".equals(cid)
                    ? new String[]{"god", "overlord", "imperator", "dragon", "king", "dmoder", "dstmoder", "opka", "downer"}
                    : "reallydev".equals(cid)
                    ? new String[]{"king", "dmoder", "dstmoder", "dglmoder", "dadmin", "dkp", "opka", "downer"}
                    : new String[]{"pegas", "cobra", "hero", "bunny", "titan", "rabbit", "vampire", "god", "king"};
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
                    ItemFactory.swordDracula(), ItemFactory.swordKrush()
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
