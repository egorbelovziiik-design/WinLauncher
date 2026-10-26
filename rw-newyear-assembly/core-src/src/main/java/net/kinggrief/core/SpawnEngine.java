package net.kinggrief.core;

import org.bukkit.Bukkit;
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
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
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
        public final EntityType type;
        public final Material handItem;
        public final Material helmet;
        public final Material chest;
        public final String commandOrAction;

        public NpcDef(String id, String line1, String line2, double x, double y, double z, float yaw,
                      EntityType type, Material handItem, Material helmet, Material chest, String commandOrAction) {
            this.id = id;
            this.line1 = line1;
            this.line2 = line2;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.type = type;
            this.handItem = handItem;
            this.helmet = helmet;
            this.chest = chest;
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
    private final Map<String, UUID> npcEntities = new HashMap<String, UUID>();
    private final Set<String> spinningCases = new HashSet<String>();
    private int mineRefillCountdown = 90;
    private int tickCounter = 0;

    public SpawnEngine(KingGriefCore plugin) {
        this.plugin = plugin;
        initDefinitions();
    }

    private void initDefinitions() {
        // 11 Colored Shulker Case Pedestals on Northern Amphitheater (Y=103)
        casePedestals.add(new CasePedestal("donate",    "&#FF1744&l🎁 ДОНАТ КЕЙС",        "&7Привилегии PEGAS → D.OWNER!",  29, -25, 103, -46, Material.RED_SHULKER_BOX));
        casePedestals.add(new CasePedestal("sdonate",   "&#E040FB&l👑 СУПЕР-ДОНАТ КЕЙС",  "&7Шанс на KING, OPKA, D.OWNER!", 99, -20, 103, -50, Material.MAGENTA_SHULKER_BOX));
        casePedestals.add(new CasePedestal("legendary", "&#FFD740&l⭐ ЛЕГЕНДАРНЫЙ КЕЙС",  "&7Шары РВ, Талисманы и Донат!",  59, -15, 103, -53, Material.YELLOW_SHULKER_BOX));
        casePedestals.add(new CasePedestal("reallydev", "&#A855F7&l⚡ КЕЙС REALLYDEV",    "&7Топ ДКП, OPKA и Шар Санты!",  129, -10, 103, -55, Material.PURPLE_SHULKER_BOX));
        casePedestals.add(new CasePedestal("riliks",    "&#00E5FF&l❖ КЕЙС РИЛИКОВ",       "&7От 50 до 2 500 Риликов ❖!",    39,  -5, 103, -56, Material.LIGHT_BLUE_SHULKER_BOX));
        casePedestals.add(new CasePedestal("allorno",   "&#FF5252&l🎲 ВСЁ ИЛИ НИЧЕГО",    "&750/50 — Мега-Джекпот или Уголь!", 45, 0, 103, -57, Material.BLACK_SHULKER_BOX));
        casePedestals.add(new CasePedestal("ditems",    "&#FF9100&l🔮 ДОНАТ-ВЕЩИ РВ",     "&7Шары, Тотемы, Кирка Джамшута!", 49,  5, 103, -56, Material.ORANGE_SHULKER_BOX));
        casePedestals.add(new CasePedestal("kits",      "&#26C6DA&l📦 КЕЙС КИТОВ РВ",     "&7Полные шалкеры ресурсов!",      35, 10, 103, -55, Material.CYAN_SHULKER_BOX));
        casePedestals.add(new CasePedestal("money",     "&#69F0AE&l💰 ДЕНЕЖНЫЙ КЕЙС",     "&7До 1 000 000$ игровых монет!",  19, 15, 103, -53, Material.LIME_SHULKER_BOX));
        casePedestals.add(new CasePedestal("djamshut",  "&#FFAB40&l⛏ КЕЙС ДЖАМШУТА",     "&7Бульдозер 3×3 и Божье Прикосн.", 25, 20, 103, -50, Material.BROWN_SHULKER_BOX));
        casePedestals.add(new CasePedestal("summer",    "&#80D8FF&l❄ НОВОГОДНИЙ КЕЙС",    "&7Зимние артефакты и подарки!",   39, 25, 103, -46, Material.WHITE_SHULKER_BOX));

        // 20 ReallyWorld Working NPCs on Pedestals (Y=101)
        npcDefs.add(new NpcDef("rtp",        "&#00E676&l⚡ РАНДОМНЫЙ ТЕЛЕПОРТ",  "&7Отдельный мир выживания (/rtp)", -9.5,  101,  32.5, -45f, EntityType.VILLAGER,  Material.COMPASS,             Material.DIAMOND_HELMET,   Material.DIAMOND_CHESTPLATE,   "gui:rtp"));
        npcDefs.add(new NpcDef("donate",     "&#FFD740&l👑 ДОНАТ И ПРИВИЛЕГИИ", "&740 рангов от 19₽ (/donate)",     10.5,  101,  32.5,  45f, EntityType.VILLAGER,  Material.NETHER_STAR,         Material.GOLDEN_HELMET,    Material.NETHERITE_CHESTPLATE, "gui:donate"));
        npcDefs.add(new NpcDef("shop",       "&#00E5FF&l🔮 МАГАЗИН РИЛИКОВ РВ", "&7Шары, Тотемы, Баффы (/shop)",   -15.5,  101,  24.5, -60f, EntityType.WANDERING_TRADER, Material.TOTEM_OF_UNDYING, Material.TURTLE_HELMET, Material.DIAMOND_CHESTPLATE, "gui:shop"));
        npcDefs.add(new NpcDef("buyer",      "&#69F0AE&l💰 СКУПЩИК РЕСУРСОВ",   "&7Продажа руды, фарм и мобов",     16.5,  101,  24.5,  60f, EntityType.VILLAGER,  Material.EMERALD,             Material.GOLDEN_HELMET,    Material.IRON_CHESTPLATE,      "gui:buyer"));
        npcDefs.add(new NpcDef("ah",         "&#FFAB40&l⚖ АУКЦИОН ИГРОКОВ",     "&7Торговля вещами (/ah)",         -17.5,  101,  14.5, -75f, EntityType.VILLAGER,  Material.GOLD_INGOT,          null,                      null,                          "gui:ah"));
        npcDefs.add(new NpcDef("kits",       "&#38BDF8&l📦 КИТЫ ПРИВИЛЕГИЙ",    "&7Наборы строго по рангу (/kits)", 18.5,  101,  14.5,  75f, EntityType.VILLAGER,  Material.ENDER_CHEST,         Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, "gui:kits"));
        npcDefs.add(new NpcDef("santa",      "&#FF1744&l🎅 ДЕДУШКА МОРОЗ",      "&7Новогодний подарок (/daily)",    -9.5,  101,  16.5, -25f, EntityType.SNOW_GOLEM,Material.SNOWBALL,            null,                      null,                          "action:santa"));
        npcDefs.add(new NpcDef("snowmaiden", "&#80D8FF&l❄ СНЕГУРОЧКА 2026",     "&7Боевой Пропуск Сезона (/bp)",    10.5,  101,  16.5,  25f, EntityType.ALLAY,     Material.AMETHYST_SHARD,      null,                      null,                          "gui:bp"));
        npcDefs.add(new NpcDef("w_mine",     "&#FF9100&l⛏ АВТО-ШАХТА РВ",       "&7Бесплатная добыча руд (/mine)", -21.5,  101,   2.5, -90f, EntityType.IRON_GOLEM,Material.NETHERITE_PICKAXE,   null,                      null,                          "warp:mine"));
        npcDefs.add(new NpcDef("w_pvp",      "&#FF1744&l⚔ PVP АРЕНА И КАБИНКИ", "&7Колизей + 6 Кабинок (/pvp)",     22.5,  101,   2.5,  90f, EntityType.PIGLIN_BRUTE, Material.NETHERITE_SWORD,  Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, "warp:pvp"));
        npcDefs.add(new NpcDef("w_farm",     "&#AEEA00&l🧟 ФАРМИЛКА МОБОВ",     "&7Опыт, Монеты и Снежинки (/farm)",-21.5, 101,  -9.5, -90f, EntityType.VILLAGER,  Material.DIAMOND_SWORD,       Material.IRON_HELMET,      Material.IRON_CHESTPLATE,      "warp:farm"));
        npcDefs.add(new NpcDef("w_parkour",  "&#40C4FF&l🏃 НЕБЕСНЫЙ ПАРКУР",    "&730 островов + Главный Приз!",    22.5,  101,  -9.5,  90f, EntityType.VILLAGER,  Material.FEATHER,             Material.GOLDEN_HELMET,    Material.GOLDEN_BOOTS,         "warp:parkour"));
        npcDefs.add(new NpcDef("w_casino",   "&#FFD740&l🎰 ГРАНД-КАЗИНО 777",   "&7Слоты, Рулетка ×14, Монетка!",  -17.5,  101, -21.5, -60f, EntityType.VILLAGER,  Material.DIAMOND,             Material.GOLDEN_HELMET,    Material.GOLDEN_CHESTPLATE,    "gui:casino"));
        npcDefs.add(new NpcDef("enchants",   "&#E040FB&l🧪 АЛХИМИК И КУЗНЕЦ",   "&7Баффы РВ и Чар-Вещи (/enchants)",18.5,  101, -21.5,  60f, EntityType.WITCH,     Material.SPLASH_POTION,       null,                      null,                          "gui:enchants"));
        npcDefs.add(new NpcDef("clans",      "&#B388FF&l🛡 КЛАНЫ И ТОПЫ",       "&7Создай свой клан (/clan)",      -11.5,  101, -31.5, -25f, EntityType.VILLAGER,  Material.SHIELD,              Material.DIAMOND_HELMET,   Material.DIAMOND_CHESTPLATE,   "gui:clans"));
        npcDefs.add(new NpcDef("jobs",       "&#69F0AE&l💼 БИРЖА РАБОТ РВ",     "&7Заработок за действия (/jobs)",  12.5,  101, -31.5,  25f, EntityType.VILLAGER,  Material.IRON_PICKAXE,        null,                      null,                          "gui:jobs"));
        npcDefs.add(new NpcDef("bosses",     "&#FF5252&l🔥 БОССЫ И ИВЕНТЫ",     "&7Активные Боссы и Чёрный Рынок",  -5.5,  101,  26.5, -20f, EntityType.BLAZE,     Material.BLAZE_ROD,           null,                      null,                          "gui:events"));
        npcDefs.add(new NpcDef("djamshut",   "&#FFAB40&l👳 ДЖАМШУТ (ОБМЕННИК)", "&7Обмен Снежинок ❄ на Шары и Рилики",6.5, 101,  26.5,  20f, EntityType.VILLAGER,  Material.PRISMARINE_CRYSTALS, Material.GOLDEN_HELMET,    null,                          "gui:djamshut"));
        npcDefs.add(new NpcDef("staff",      "&#22C55E&l🍀 ВСЕ ВАРПЫ И КП",     "&7Навигатор по аренам (/warps)",  -14.5,  101,  36.5, -30f, EntityType.VILLAGER,  Material.WRITABLE_BOOK,       null,                      null,                          "gui:warps"));
        npcDefs.add(new NpcDef("titles_lp",  "&#00E676&l✨ ТИТУЛЫ И НАГРАДЫ",    "&7Титулы (/titles) и Время (/pt)", 15.5,  101,  36.5,  30f, EntityType.VILLAGER,  Material.NAME_TAG,            null,                      null,                          "gui:titles"));
        npcDefs.add(new NpcDef("bafovar",    "&#FF1744&l🧪 БАФОВАР REALLYWORLD","&7Зелья Киллера, Отрыжки и Стакер!",-5.5, 101,  35.5, -15f, EntityType.WITCH,     Material.POTION,              null,                      null,                          "gui:bafovar"));
        npcDefs.add(new NpcDef("freebie",    "&#FFD740&l🎁 ХАЛЯВА И ПРОМОКОДЫ", "&7Бесплатные награды и Промо (/free)",6.5,101,  35.5,  15f, EntityType.VILLAGER,  Material.CHEST_MINECART,      Material.GOLDEN_HELMET,    Material.DIAMOND_CHESTPLATE,   "gui:freebie"));

        // 5x 1v1 Kabinki + 1x 2v2 Kabinka
        int[] zCenters = new int[]{-26, -19, -12, -5, 2};
        for (int i = 0; i < zCenters.length; i++) {
            int zc = zCenters[i];
            kabinki.add(new KabinkaState(i + 1, false, 49, 53, zc - 1, zc + 1, 48, zc));
        }
        kabinki.add(new KabinkaState(6, true, 59, 65, -15, -11, 58, -13));
    }

    public void initializeSpawnWorldAndBuild(boolean forceRebuild) {
        World sw = VoidSpawnWorldGenerator.getOrCreateSpawnWorld();
        if (sw == null) return;
        File marker = new File(sw.getWorldFolder(), ".spawn_v4_built");
        if (forceRebuild || !marker.exists() || sw.getBlockAt(0, 99, 0).getType() == Material.AIR) {
            plugin.getLogger().info("[SpawnEngine v4.0] Building 181x96x181 Colossal New Year Castle-Kingdom in Void World 'spawn_world'...");
            buildFromRleResource(sw, "/spawn_v4.dat");
            try { marker.createNewFile(); } catch (Exception ignored) {}
        }
        placeShulkerCases(sw);
        refillAutoMine(sw, false);
        rebuildAllHologramsAndNpcs(sw);
        startEngineLoop();
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
            Material[] palMat = new Material[palCount];
            BlockData[] palData = new BlockData[palCount];
            for (int i = 0; i < palCount; i++) {
                String spec = dis.readUTF();
                try {
                    BlockData bd = Bukkit.createBlockData("minecraft:" + spec);
                    palData[i] = bd;
                    palMat[i] = bd.getMaterial();
                } catch (Throwable t) {
                    String baseName = spec.contains("[") ? spec.substring(0, spec.indexOf('[')) : spec;
                    Material m = ColorUtil.mat(baseName.toUpperCase(Locale.ROOT), "STONE");
                    palMat[i] = m;
                    palData[i] = m.createBlockData();
                }
            }
            int baseY = 100 - OY; // 74
            int baseX = -OX;      // -90
            int baseZ = -OZ;      // -90
            int WL = W * L;
            long cursor = 0L;
            long total = (long) W * H * L;

            while (cursor < total) {
                int pIdx = dis.readUnsignedByte();
                int runLen = dis.readUnsignedShort();
                if (pIdx == 0 && runLen == 0) break;
                if (pIdx == 0) {
                    // AIR run: in Void World, chunks are already AIR! Only clear if rebuilding over existing blocks
                    cursor += runLen;
                    continue;
                }
                BlockData bd = palData[pIdx];
                for (int r = 0; r < runLen && cursor < total; r++, cursor++) {
                    int rem = (int) (cursor % WL);
                    int ly = (int) (cursor / WL);
                    int lz = rem / W;
                    int lx = rem % W;
                    Block b = sw.getBlockAt(baseX + lx, baseY + ly, baseZ + lz);
                    b.setBlockData(bd, false);
                }
            }
            dis.close();
            // Open initial Kabinki doorways cleanly
            for (KabinkaState kb : kabinki) {
                setKabinkaDoorBlocks(sw, kb, false);
            }
            plugin.getLogger().info("[SpawnEngine v4.0] Colossal 3D Spawn built cleanly in 'spawn_world'!");
        } catch (Exception e) {
            plugin.getLogger().warning("[SpawnEngine v4.0] Error decoding spawn_v4.dat: " + e.getMessage());
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
        // Force-load spawn chunks (-6..6) so entities never fail to spawn
        for (int cx = -6; cx <= 6; cx++) {
            for (int cz = -6; cz <= 6; cz++) {
                sw.getChunkAt(cx, cz).load();
            }
        }
        // Clear old spawn holograms, NPCs, and any leftover Villagers from previous builds cleanly!
        for (Entity e : sw.getEntities()) {
            if (e.getScoreboardTags().contains("kg_holo")
                    || e.getScoreboardTags().contains("kg_npc")
                    || e.getScoreboardTags().contains("kg_case_float")
                    || e instanceof AbstractVillager) {
                e.remove();
            }
        }
        npcEntities.clear();

        // 1. Central Welcome Hologram
        spawnHoloStack(sw, 0.5, 104.2, 37.5,
                "\uE976 &#FF1744&lKINGGRIEF &8· &#80D8FF&lНОВОГОДНИЙ REALLYWORLD &f\uE976",
                "&#FFD740★ Огромный Зимний Спавн v4.0 (Отдельный Мир) ★",
                "&fРТП в мир выживания: &#00E676/rtp &8│ &fВарпы: &#80D8FF/warps &8│ &fМеню: &#FFAB40/menu",
                "&fДонат, Кейсы и LP-Редактор: &#FF4081kinggrief.vercel.app");

        // 2. Holograms above EVERY ONE of the 11 Shulker Cases!
        for (CasePedestal cp : casePedestals) {
            spawnHoloStack(sw, cp.x + 0.5, cp.y + 2.05, cp.z + 0.5,
                    cp.title + " &8[&#FFD740" + cp.priceRub + "₽&8]",
                    cp.subtitle,
                    "&#00E676▸ ПКМ — Открыть &8│ &#80D8FFЛКМ — Призы",
                    "&7Сайт: &#FF4081kinggrief.vercel.app");
        }
        spawnHoloStack(sw, 0.5, 106.5, -45.5,
                "&#FFD740&l✨ АМФИТЕАТР ШАЛКЕР-КЕЙСОВ REALLYWORLD ✨",
                "&7Нажми по любому цветному Шалкеру для прокрутки рулетки!");

        // 3. Zone & Warp Holograms
        spawnHoloStack(sw, -53.0, 102.8, 12.5,
                "&#FF9100&l⛏ АВТО-ШАХТА REALLYWORLD",
                "&7Ломай руду → получай &aМонеты&7, &bОпыт &7и &bСнежинки ❄&7!",
                "&eАвто-обновление каждые 90 секунд!");
        spawnHoloStack(sw, -50.0, 103.2, -26.5,
                "&#AEEA00&l🧟 БЕЗОПАСНАЯ ФАРМИЛКА МОБОВ",
                "&7Бей зимних мобов в камерах — фарми &aМонеты &7и &bСнежинки ❄&7!");
        spawnHoloStack(sw, -34.0, 103.5, 38.5,
                "&#FFD740&l🎰 ГРАНД-КАЗИНО REALLYWORLD 777",
                "&7Слоты 777 · Рулетка ×14 · Монетка PvP (/cf) · Колесо Риликов",
                "&#00E676Нажми рычаг у автомата или введи /casino!");
        spawnHoloStack(sw, 34.5, 103.5, 38.5,
                "&#E040FB&l🧪 АЛТАРЬ АЛХИМИКА И ЧАРОДЕЯ РВ",
                "&7Зелья Киллера, Отрыжки, Кислоты, Шары и Тотемы (/enchants)");
        spawnHoloStack(sw, 52.0, 103.5, 17.5,
                "&#FF1744&l⚔ ГЛАДИАТОРСКАЯ PVP-АРЕНА",
                "&7Прыгай вниз для открытого PvP! Наверху PvP выключено.");
        spawnHoloStack(sw, 28.5, 102.8, -22.5,
                "&#40C4FF&l🏃 НЕБЕСНЫЙ ПАРКУР НА 30 ОСТРОВОВ",
                "&7Дойди до вершины (Y=131) → &#00E676+25 000$ + 15❖ + 15❄ + Титул!",
                "&7Упал? Вернись на чекпоинт: &f/parkour cp");

        // 4. Live Status Holograms above all 6 Duel Kabinki
        for (KabinkaState kb : kabinki) {
            Location hl = new Location(sw, kb.doorX - 0.3, 102.65, kb.doorZ + 0.5);
            ArmorStand st = spawnSingleStand(hl, formatKabinkaTitle(kb, 0));
            kb.statusStandId = st.getUniqueId();
        }

        // 5. Spawn all 20 Working ReallyWorld NPCs + 3-line Holograms above each!
        for (NpcDef nd : npcDefs) {
            ensureNpcAlive(sw, nd, true);
        }
    }

    private void ensureNpcAlive(World sw, NpcDef nd, boolean buildHolo) {
        UUID curId = npcEntities.get(nd.id);
        Entity existing = curId != null ? Bukkit.getEntity(curId) : null;
        Location loc = new Location(sw, nd.x, nd.y, nd.z, nd.yaw, 0f);
        if (buildHolo) {
            int bx = (int) Math.floor(nd.x);
            int bz = (int) Math.floor(nd.z);
            if (sw.getBlockAt(bx, 100, bz).getType() == Material.AIR) {
                sw.getBlockAt(bx, 100, bz).setType(Material.GOLD_BLOCK, false);
            }
        }
        if (existing == null || existing.isDead() || !existing.isValid()) {
            LivingEntity npc = (LivingEntity) sw.spawnEntity(loc, nd.type);
            npc.setAI(false);
            npc.setGravity(false);
            npc.setInvulnerable(true);
            npc.setSilent(true);
            npc.setCollidable(false);
            npc.setRemoveWhenFarAway(false);
            npc.setPersistent(true);
            npc.setCustomName(ColorUtil.c(nd.line1));
            npc.setCustomNameVisible(true);
            npc.addScoreboardTag("kg_npc");
            npc.addScoreboardTag("kg_npc_" + nd.id);
            if (npc instanceof Villager) {
                Villager v = (Villager) npc;
                v.setAdult();
                v.setProfession(Villager.Profession.NONE);
                v.setVillagerLevel(5);
            }
            if (npc instanceof AbstractVillager) {
                ((AbstractVillager) npc).setRecipes(Collections.<MerchantRecipe>emptyList());
            }
            EntityEquipment eq = npc.getEquipment();
            if (eq != null) {
                if (nd.handItem != null) eq.setItemInMainHand(new ItemStack(nd.handItem));
                if (nd.helmet != null) eq.setHelmet(new ItemStack(nd.helmet));
                if (nd.chest != null) eq.setChestplate(new ItemStack(nd.chest));
            }
            npcEntities.put(nd.id, npc.getUniqueId());
        } else {
            existing.teleport(loc);
            if (existing instanceof AbstractVillager) {
                ((AbstractVillager) existing).setRecipes(Collections.<MerchantRecipe>emptyList());
            }
        }
        if (buildHolo) {
            double hY = nd.type == EntityType.IRON_GOLEM ? nd.y + 3.25 : nd.y + 2.55;
            spawnHoloStack(sw, nd.x, hY, nd.z,
                    nd.line1,
                    nd.line2,
                    "&#00E676[ Нажми ПКМ / ЛКМ ]");
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
        new BukkitRunnable() {
            @Override
            public void run() {
                tickSecond();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void tickSecond() {
        tickCounter++;
        World sw = Bukkit.getWorld(VoidSpawnWorldGenerator.SPAWN_WORLD_NAME);
        if (sw == null) return;

        // 1. Auto-Mine countdown & regeneration
        mineRefillCountdown--;
        if (mineRefillCountdown <= 0) {
            mineRefillCountdown = 90;
            refillAutoMine(sw, true);
        }

        // 2. Smart Duel Kabinki (1v1 & 2v2) auto-doors & 8s anti-steal loot lock!
        tickKabinki(sw);

        // 3. Mob Grinder Farm (/warp farm) — replenish safe mobs every 4 seconds
        if (tickCounter % 4 == 0) {
            tickMobFarm(sw);
        }

        // 4. Watchdog: keep all 20 NPCs alive & facing nearest player + snow particles around Giant Tree
        if (tickCounter % 5 == 0) {
            for (NpcDef nd : npcDefs) {
                ensureNpcAlive(sw, nd, false);
            }
        }
        for (Player p : sw.getPlayers()) {
            // Void rescue in spawn_world!
            if (p.getLocation().getY() < 68.0) {
                Location cp = plugin.getStore().getParkourCp(p);
                if (cp != null) {
                    p.teleport(cp);
                    ColorUtil.actionBar(p, "&#40C4FF🏃 Возврат на чекпоинт паркура!");
                } else {
                    p.teleport(getSpawnArrival());
                    ColorUtil.actionBar(p, "&#80D8FF❄ Вы спасены из бездны и возвращены на спавн!");
                }
                p.setFallDistance(0f);
            }
            // Gentle holiday snowfall around player in spawn_world
            p.spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 5.5, 0), 8, 6.0, 2.5, 6.0, 0.01);
        }

        // Rotate NPCs smoothly toward nearby players
        for (Map.Entry<String, UUID> en : npcEntities.entrySet()) {
            Entity ent = Bukkit.getEntity(en.getValue());
            if (ent == null || !ent.isValid()) continue;
            Player nearest = null;
            double bestD2 = 64.0;
            for (Player p : sw.getPlayers()) {
                double d2 = p.getLocation().distanceSquared(ent.getLocation());
                if (d2 < bestD2) {
                    bestD2 = d2;
                    nearest = p;
                }
            }
            if (nearest != null) {
                Location nl = ent.getLocation();
                Vector dir = nearest.getLocation().toVector().subtract(nl.toVector());
                nl.setDirection(dir);
                ent.teleport(nl);
            }
        }
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
            // Push out extra 3rd-party entrants if over capacity
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
                kb.lootLockSec = 8; // 8 seconds protected looting!
            }
        }
    }

    private void tickMobFarm(World sw) {
        int[][] cells = new int[][]{{-56, -31}, {-50, -31}, {-44, -31}};
        EntityType[] types = new EntityType[]{EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER};
        String[] names = new String[]{"&#80D8FF❄ Зимний Зомби РВ", "&#E0F7FA🦴 Ледяной Скелет РВ", "&#B388FF🕷 Морозный Паук РВ"};

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
        // Rescue any player inside the ore pit before refilling!
        for (Player p : sw.getPlayers()) {
            if (isInsideAutoMine(p.getLocation())) {
                p.teleport(getWarpLocation("mine"));
                ColorUtil.actionBar(p, "&#FF9100⛏ Авто-Шахта обновлена! Вы подняты наверх.");
            }
        }
        Material[] ores = new Material[]{
                Material.STONE, Material.STONE, Material.COAL_ORE, Material.IRON_ORE,
                Material.GOLD_ORE, Material.LAPIS_ORE, Material.REDSTONE_ORE,
                Material.DIAMOND_ORE, Material.EMERALD_ORE
        };
        for (int x = -58; x <= -48; x++) {
            for (int y = 93; y <= 98; y++) {
                for (int z = 6; z <= 18; z++) {
                    Material pick = rng.nextInt(90) == 0 ? Material.ANCIENT_DEBRIS : ores[rng.nextInt(ores.length)];
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

    // ================= ZONE HELPERS =================
    public Location getSpawnArrival() {
        World sw = VoidSpawnWorldGenerator.getOrCreateSpawnWorld();
        return new Location(sw, 0.5, 100.0, 42.5, 180.0f, 0.0f);
    }

    public Location getWarpLocation(String warpName) {
        World sw = VoidSpawnWorldGenerator.getOrCreateSpawnWorld();
        String w = warpName == null ? "" : warpName.toLowerCase(Locale.ROOT);
        if ("mine".equals(w) || "шахта".equals(w)) return new Location(sw, -43.5, 100.0, 12.5, 90.0f, 10.0f);
        if ("farm".equals(w) || "фарм".equals(w)) return new Location(sw, -50.0, 100.0, -24.5, 180.0f, 0.0f);
        if ("casino".equals(w) || "казино".equals(w)) return new Location(sw, -26.5, 100.0, 38.5, 90.0f, 0.0f);
        if ("enchant".equals(w) || "enchants".equals(w) || "чары".equals(w)) return new Location(sw, 29.5, 100.0, 38.5, -90.0f, 0.0f);
        if ("pvp".equals(w) || "пвп".equals(w) || "arena".equals(w)) return new Location(sw, 38.5, 100.0, 17.5, -90.0f, 10.0f);
        if ("kabinki".equals(w) || "duels".equals(w)) return new Location(sw, 45.5, 100.0, -12.0, -90.0f, 0.0f);
        if ("parkour".equals(w) || "паркур".equals(w)) return new Location(sw, 28.5, 100.0, -19.5, 180.0f, 0.0f);
        if ("cases".equals(w) || "кейсы".equals(w)) return new Location(sw, 0.5, 102.0, -43.5, 180.0f, 0.0f);
        return null;
    }

    public boolean isInSpawnWorld(Location loc) {
        return loc != null && loc.getWorld() != null
                && loc.getWorld().getName().equalsIgnoreCase(VoidSpawnWorldGenerator.SPAWN_WORLD_NAME);
    }

    public boolean isInsideAutoMine(Location loc) {
        if (!isInSpawnWorld(loc)) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        return x >= -58 && x <= -48 && y >= 93 && y <= 98 && z >= 6 && z <= 18;
    }

    public boolean isInsidePvpZone(Location loc) {
        if (!isInSpawnWorld(loc)) return true; // PvP enabled in survival worlds!
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        // Sunken Colosseum pit (Y <= 98 inside pit X=41..63, Z=7..27)
        if (x >= 41 && x <= 63 && y >= 91 && y <= 98 && z >= 7 && z <= 27) {
            return true;
        }
        // Any of the 6 Duel Kabinki interiors
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
        if (e == null || e instanceof Player || e instanceof ArmorStand) return null;
        for (NpcDef nd : npcDefs) {
            if (e.getScoreboardTags().contains("kg_npc_" + nd.id)) return nd;
        }
        if (isInSpawnWorld(e.getLocation())) {
            Location el = e.getLocation();
            for (NpcDef nd : npcDefs) {
                double dx = el.getX() - nd.x;
                double dz = el.getZ() - nd.z;
                if (dx * dx + dz * dz <= 3.0 && Math.abs(el.getY() - nd.y) <= 2.5) {
                    return nd;
                }
            }
        }
        return null;
    }

    public int getAirdropSecondsLeft() {
        return plugin.getBossEvents() != null ? plugin.getBossEvents().getNextAutoSeconds() : 300;
    }

    // ================= SEPARATE WORLD RTP (/rtp) =================
    public void performRtp(Player p, String mode) {
        World targetWorld;
        int bound;
        if ("nether".equalsIgnoreCase(mode)) {
            targetWorld = VoidSpawnWorldGenerator.getNetherWorld();
            bound = 800;
            if (targetWorld == null) targetWorld = VoidSpawnWorldGenerator.getSurvivalWorld();
        } else {
            targetWorld = VoidSpawnWorldGenerator.getSurvivalWorld();
            bound = "far".equalsIgnoreCase(mode) ? 3500 : 1500;
        }
        if (targetWorld == null) {
            p.sendMessage(ColorUtil.c("&cМир выживания недоступен!"));
            return;
        }
        for (int attempt = 0; attempt < 25; attempt++) {
            int x = rng.nextInt(bound * 2) - bound;
            int z = rng.nextInt(bound * 2) - bound;
            if (Math.abs(x) < 180 && Math.abs(z) < 180) continue;
            targetWorld.getChunkAt(x >> 4, z >> 4).load();
            Location safe;
            if (targetWorld.getEnvironment() == World.Environment.NETHER) {
                safe = findNetherSafe(targetWorld, x, z);
            } else {
                Block b = targetWorld.getHighestBlockAt(x, z);
                Material m = b.getType();
                if (m == Material.WATER || m == Material.LAVA || m == Material.KELP) continue;
                safe = b.getLocation().add(0.5, 1.0, 0.5);
            }
            if (safe == null) continue;
            plugin.getStore().setBack(p, p.getLocation());
            plugin.getStore().setParkourCp(p, null);
            p.teleport(safe);
            ColorUtil.addPot(p, "resistance", "DAMAGE_RESISTANCE", 100, 4);
            ColorUtil.sound(p, "entity.enderman.teleport", 1.0f, 1.15f);
            p.sendTitle(ColorUtil.c("&#00E676&l⚡ МИР ВЫЖИВАНИЯ"),
                    ColorUtil.c("&fXYZ: &e" + safe.getBlockX() + ", " + safe.getBlockY() + ", " + safe.getBlockZ()), 5, 45, 10);
            p.sendMessage(ColorUtil.c("&#00E676[РТП] &fВы телепортированы в отдельный мир выживания (&e" + targetWorld.getName()
                    + "&f): &#80D8FF" + safe.getBlockX() + ", " + safe.getBlockY() + ", " + safe.getBlockZ()));
            return;
        }
        Location fb = targetWorld.getHighestBlockAt(350, 350).getLocation().add(0.5, 1.0, 0.5);
        p.teleport(fb);
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
        final Material[] spinIcons = new Material[]{
                Material.TOTEM_OF_UNDYING, Material.NETHER_STAR, Material.NETHERITE_SWORD,
                Material.ENCHANTED_GOLDEN_APPLE, Material.DIAMOND_BLOCK, Material.EMERALD, Material.PLAYER_HEAD
        };
        final Item floating = sw.dropItem(center, new ItemStack(Material.NETHER_STAR));
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
                    Material m = spinIcons[step % spinIcons.length];
                    floating.setItemStack(new ItemStack(m));
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
                prizeText = "&#00E676ДЖЕКПОТ! Шар Титана + 450❖ + 150 000$!";
            } else {
                p.getInventory().addItem(new ItemStack(Material.COAL, 1));
                prizeText = "&7Уголёк Неудачи (попробуй ещё!)";
            }
        } else if ("ditems".equals(cid) || "legendary".equals(cid)) {
            ItemStack[] pool = new ItemStack[]{
                    ItemFactory.sharTitana(), ItemFactory.sharAndromedy(), ItemFactory.sharApollona(),
                    ItemFactory.sharSanty(), ItemFactory.talKrushitelya(), ItemFactory.talFeniksa(3),
                    ItemFactory.talGrani(), ItemFactory.swordDracula(), ItemFactory.swordKrush()
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
                    ItemFactory.sharSanty(), ItemFactory.talStuzhi(), ItemFactory.potionChampagne(3),
                    ItemFactory.nyMandarin(16), ItemFactory.snowflakeCoin(20)
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
