package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class BossEventManager {

    public enum EventKind {
        BOSS,
        AIRDROP,
        METEOR,
        BEACON,
        FROST_CITADEL,
        VOLCANO
    }

    public static final class BossTemplate {
        public final String id;
        public final EventKind kind;
        public final String displayName;
        public final EntityType entityType; // null for timed world events
        public final double maxHp;           // HP for BOSS, or unlock duration seconds for world events
        public final BarColor barColor;
        public final BarStyle barStyle;
        public final Material platformMat;
        public final double coinReward;
        public final int relicReward;

        public BossTemplate(String id, EventKind kind, String displayName, EntityType entityType, double maxHp,
                            BarColor barColor, BarStyle barStyle, Material platformMat,
                            double coinReward, int relicReward) {
            this.id = id;
            this.kind = kind;
            this.displayName = displayName;
            this.entityType = entityType;
            this.maxHp = maxHp;
            this.barColor = barColor;
            this.barStyle = barStyle;
            this.platformMat = platformMat;
            this.coinReward = coinReward;
            this.relicReward = relicReward;
        }
    }

    public static final class ActiveBossEvent {
        public String instanceId;
        public BossTemplate template;
        public Location center; // loot chest location
        public UUID bossUuid;
        public BossBar bossBar;
        public double lastKnownHp = 1000.0;
        public boolean bossDefeated = false; // true = unlocked / open chest!
        public int unlockCountdownSec = 0;   // >0 while timed event (airdrop/meteor/beacon/citadel) is unlocking
        public int unlockTotalSec = 180;
        public int secondsLeft;
        public final Map<String, Double> damageMap = new HashMap<String, Double>();
        public final Map<Location, BlockState> savedBlocks = new LinkedHashMap<Location, BlockState>();
        public final List<UUID> hologramStands = new ArrayList<UUID>();
    }

    private final KingGriefCore plugin;
    private final Random rng = new Random();
    private final Map<String, BossTemplate> templates = new LinkedHashMap<String, BossTemplate>();
    private final Map<String, ActiveBossEvent> activeEvents = new LinkedHashMap<String, ActiveBossEvent>();
    private int autoCountdownSec = 360;
    private int seq = 1;

    public BossEventManager(KingGriefCore plugin) {
        this.plugin = plugin;
        registerTemplates();
    }

    private void registerTemplates() {
        // 5 Mythic Bosses
        templates.put("grinch", new BossTemplate(
                "grinch", EventKind.BOSS, "&#80D8FF❄ Ледяной Король Гринч", EntityType.STRAY, 800.0,
                BarColor.BLUE, BarStyle.SEGMENTED_10, Material.PACKED_ICE, 75000.0, 25));
        templates.put("krush", new BossTemplate(
                "krush", EventKind.BOSS, "&#FF1744🔥 Адский Крушитель", EntityType.WITHER_SKELETON, 1100.0,
                BarColor.RED, BarStyle.SEGMENTED_12, Material.CRYING_OBSIDIAN, 100000.0, 35));
        templates.put("titan", new BossTemplate(
                "titan", EventKind.BOSS, "&#FFD740⚡ Титан Олимпа", EntityType.IRON_GOLEM, 1400.0,
                BarColor.YELLOW, BarStyle.SEGMENTED_20, Material.GOLD_BLOCK, 125000.0, 40));
        templates.put("osiris", new BossTemplate(
                "osiris", EventKind.BOSS, "&#00E676☠ Фараон Осирис", EntityType.HUSK, 950.0,
                BarColor.GREEN, BarStyle.SEGMENTED_10, Material.EMERALD_BLOCK, 90000.0, 30));
        templates.put("andromeda", new BossTemplate(
                "andromeda", EventKind.BOSS, "&#E040FB🌠 Страж Андромеды", EntityType.BLAZE, 900.0,
                BarColor.PURPLE, BarStyle.SEGMENTED_10, Material.AMETHYST_BLOCK, 95000.0, 32));

        // 4 Distinct Non-Boss World Events (AirDrop + 3 New World Events!)
        templates.put("airdrop", new BossTemplate(
                "airdrop", EventKind.AIRDROP, "&#FF9100🪂 Военный АирДроп Снабжения", null, 150.0,
                BarColor.YELLOW, BarStyle.SEGMENTED_10, Material.RED_SHULKER_BOX, 80000.0, 30));
        templates.put("meteor", new BossTemplate(
                "meteor", EventKind.METEOR, "&#FF5252☄ Звёздный Метеорит", null, 150.0,
                BarColor.RED, BarStyle.SEGMENTED_12, Material.MAGMA_BLOCK, 95000.0, 35));
        templates.put("beacon", new BossTemplate(
                "beacon", EventKind.BEACON, "&#FFD740💎 Золотой Маяк Богатства", null, 180.0,
                BarColor.GREEN, BarStyle.SEGMENTED_10, Material.BEACON, 110000.0, 40));
        templates.put("citadel", new BossTemplate(
                "citadel", EventKind.FROST_CITADEL, "&#40C4FF❄ Ледяная Цитадель Санты", null, 150.0,
                BarColor.WHITE, BarStyle.SEGMENTED_10, Material.BLUE_ICE, 100000.0, 35));
        templates.put("volcano", new BossTemplate(
                "volcano", EventKind.VOLCANO, "&#FF6D00🌋 Адский Вулкан Ифрита", null, 150.0,
                BarColor.RED, BarStyle.SEGMENTED_20, Material.MAGMA_BLOCK, 115000.0, 40));
    }

    /**
     * Строгая проверка: призывать ивенты через меню / команды могут ТОЛЬКО Админы и выше (D.ADMIN+, ДКП, OPKA, D.OWNER, КП, CURATOR, TECH.ADMIN, OWNER),
     * а НЕ обычные игроки/донатеры (даже если игрок тестирует ранг Игрок с включённым /op!).
     */
    public boolean canAdminSpawnEvents(Player p) {
        if (p == null) return false;
        RankManager.RankDef r = plugin.getRanks().getRank(p);
        return r.weight >= 162; // dadmin(162), opka(178), downer(190), kp(200), curator(275), techadmin(290), owner(300)
    }

    public void start() {
        // Purge any leftover event bosses or holograms from previous server runs!
        for (World w : Bukkit.getWorlds()) {
            for (org.bukkit.Chunk ch : w.getLoadedChunks()) {
                purgeOrphanEventEntitiesInChunk(ch);
            }
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                tickSecond();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void purgeOrphanEventEntitiesInChunk(org.bukkit.Chunk chunk) {
        if (chunk == null) return;
        try {
            for (Entity en : chunk.getEntities()) {
                if (en.getScoreboardTags().contains("kg_event_boss")) {
                    boolean belongsToActive = false;
                    for (ActiveBossEvent ev : activeEvents.values()) {
                        if (!ev.bossDefeated && (en.getUniqueId().equals(ev.bossUuid)
                                || en.getScoreboardTags().contains("kg_boss_" + ev.instanceId))) {
                            belongsToActive = true;
                            ev.bossUuid = en.getUniqueId();
                            break;
                        }
                    }
                    if (!belongsToActive) {
                        en.remove();
                    }
                } else if (en.getScoreboardTags().contains("kg_holo")) {
                    boolean activeHolo = false;
                    for (ActiveBossEvent ev : activeEvents.values()) {
                        if (ev.hologramStands.contains(en.getUniqueId())) {
                            activeHolo = true;
                            break;
                        }
                    }
                    if (!activeHolo) {
                        en.remove();
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    public void shutdown() {
        List<String> ids = new ArrayList<String>(activeEvents.keySet());
        for (String id : ids) {
            endEvent(id, false);
        }
    }

    public List<ActiveBossEvent> getActiveEvents() {
        return new ArrayList<ActiveBossEvent>(activeEvents.values());
    }

    public int getNextAutoSeconds() {
        return autoCountdownSec;
    }

    public void syncBarsForJoin(Player p) {
        if (p == null) return;
        for (ActiveBossEvent ev : activeEvents.values()) {
            if (ev.bossBar != null) {
                ev.bossBar.addPlayer(p);
            }
        }
    }

    public void removeBarsForQuit(Player p) {
        if (p == null) return;
        for (ActiveBossEvent ev : activeEvents.values()) {
            if (ev.bossBar != null) {
                ev.bossBar.removePlayer(p);
            }
        }
    }

    /**
     * Гарантирует, что полоска РЕЖИМА ПВП (BossBar КТ) ВСЕГДА находится #1 САМОЙ ВЕРХНЕЙ
     * посередине экрана над любыми активными ивентами!
     */
    public void moveEventBarsBelowCombat(Player p) {
        if (p == null || !p.isOnline()) return;
        for (ActiveBossEvent ev : activeEvents.values()) {
            if (ev.bossBar != null && ev.bossBar.getPlayers().contains(p)) {
                ev.bossBar.removePlayer(p);
                ev.bossBar.addPlayer(p);
            }
        }
    }

    public ActiveBossEvent startRandomEvent() {
        // Alternate richly between AirDrop, Meteor, Beacon, Frost Citadel, Volcano, and Mythic Bosses!
        String[] pool = new String[]{"airdrop", "meteor", "beacon", "citadel", "volcano", "grinch", "krush", "titan", "osiris", "andromeda"};
        List<String> unused = new ArrayList<String>();
        for (String pid : pool) {
            boolean used = false;
            for (ActiveBossEvent ev : activeEvents.values()) {
                if (ev.template.id.equals(pid)) { used = true; break; }
            }
            if (!used) unused.add(pid);
        }
        String pick = unused.isEmpty() ? pool[rng.nextInt(pool.length)] : unused.get(rng.nextInt(unused.size()));
        return spawnBossEvent(pick, null);
    }

    public ActiveBossEvent startRandomBossOnly() {
        String[] bosses = new String[]{"grinch", "krush", "titan", "osiris", "andromeda"};
        return spawnBossEvent(bosses[rng.nextInt(bosses.length)], null);
    }

    public ActiveBossEvent spawnBossEvent(String templateId, Location forcedCenter) {
        if (activeEvents.size() >= 4) {
            String oldest = activeEvents.keySet().iterator().next();
            endEvent(oldest, false);
        }
        String key = templateId == null ? "" : templateId.toLowerCase(Locale.ROOT);
        if ("boss".equals(key)) {
            return startRandomBossOnly();
        }
        BossTemplate tpl = templates.get(key);
        if (tpl == null) {
            tpl = templates.get("airdrop");
        }

        World surv = forcedCenter != null && forcedCenter.getWorld() != null
                ? forcedCenter.getWorld()
                : VoidSpawnWorldGenerator.getSurvivalWorld();
        if (surv == null) return null;

        Location center = forcedCenter != null ? forcedCenter.getBlock().getLocation() : findStrictDryLandSpot(surv);
        if (center == null) return null;

        ActiveBossEvent ev = new ActiveBossEvent();
        ev.instanceId = tpl.id + "_" + (seq++);
        ev.template = tpl;
        ev.center = center;

        if (tpl.kind == EventKind.BOSS) {
            ev.secondsLeft = 900;
            ev.unlockCountdownSec = 0;
            ev.lastKnownHp = tpl.maxHp;
            buildBossAltar(ev);

            Location bossLoc = center.clone().add(0.5, 1.2, 2.5);
            LivingEntity boss = (LivingEntity) surv.spawnEntity(bossLoc, tpl.entityType);
            configureBossEntity(boss, tpl, ev.instanceId, tpl.maxHp);
            ev.bossUuid = boss.getUniqueId();
        } else {
            ev.unlockTotalSec = (int) tpl.maxHp;
            ev.unlockCountdownSec = ev.unlockTotalSec;
            ev.secondsLeft = ev.unlockTotalSec + 180;
            if (tpl.kind == EventKind.AIRDROP) {
                buildBeautifulAirdropStructure(ev);
            } else if (tpl.kind == EventKind.METEOR) {
                buildStarMeteorStructure(ev);
            } else if (tpl.kind == EventKind.BEACON) {
                buildGoldenBeaconStructure(ev);
            } else if (tpl.kind == EventKind.VOLCANO) {
                buildVolcanoStructure(ev);
            } else {
                buildFrostCitadelStructure(ev);
            }
        }

        spawnChestHologram(ev);

        String initTitle = formatBarTitle(ev, tpl.maxHp);
        BossBar bar = Bukkit.createBossBar(initTitle, tpl.barColor, tpl.barStyle);
        bar.setProgress(1.0);
        bar.setVisible(true);
        for (Player on : Bukkit.getOnlinePlayers()) {
            bar.addPlayer(on);
        }
        ev.bossBar = bar;
        activeEvents.put(ev.instanceId, ev);

        announceEventSpawn(ev);
        return ev;
    }

    private void announceEventSpawn(ActiveBossEvent ev) {
        int bx = ev.center.getBlockX(), by = ev.center.getBlockY(), bz = ev.center.getBlockZ();
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l╔══════════════════════════════════════════════╗"));
        if (ev.template.kind == EventKind.BOSS) {
            Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l║  &#FF1744&l👹 НОВЫЙ БОСС-ИВЕНТ НА СУШЕ!"));
            Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l║  &fБосс: " + ev.template.displayName + " &7(&c" + (int) ev.template.maxHp + " ❤&7)"));
            Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l║  &7Убейте Босса, чтобы распечатать Мифический Сундук!"));
        } else if (ev.template.kind == EventKind.AIRDROP) {
            Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l║  &#FF9100&l🪂 СБРОШЕН ВОЕННЫЙ АИРДРОП НА ЗЕМЛЮ!"));
            Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l║  &7Купол с парашютом приземлился на суше — открытие через &e" + ColorUtil.fmtTime(ev.unlockCountdownSec) + "&7!"));
        } else if (ev.template.kind == EventKind.METEOR) {
            Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l║  &#FF5252&l☄ УПАЛ ЗВЁЗДНЫЙ МЕТЕОРИТ С РУДАМИ!"));
            Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l║  &7Добывайте алмазы/незерит в кратере! Ядро откроется через &e" + ColorUtil.fmtTime(ev.unlockCountdownSec) + "&7!"));
        } else if (ev.template.kind == EventKind.BEACON) {
            Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l║  &#00E676&l💎 АКТИВИРОВАН ХРАМ МАЯКА БОГАТСТВА!"));
            Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l║  &7Стойте в Пантеоне — каждые 5 сек капают &a+3 000$ &7и &b+2❖&7!"));
        } else if (ev.template.kind == EventKind.VOLCANO) {
            Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l║  &#FF6D00&l🌋 НАЧАЛОСЬ ИЗВЕРЖЕНИЕ АДСКОГО ВУЛКАНА!"));
            Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l║  &7Кальдера с Кастомными ТНТ и 3D Луками откроется через &e" + ColorUtil.fmtTime(ev.unlockCountdownSec) + "&7!"));
        } else {
            Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l║  &#40C4FF&l❄ ВЫРОСЛА ГОТИЧЕСКАЯ ЦИТАДЕЛЬ САНТЫ!"));
            Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l║  &7Захватите ледяной дворец — Сундук откроется через &e" + ColorUtil.fmtTime(ev.unlockCountdownSec) + "&7!"));
        }
        Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l║  &fМир: &#00E676Выживание (/rtp) &8│ &fКоординаты: &#80D8FF" + bx + ", " + by + ", " + bz));
        Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l╚══════════════════════════════════════════════╝"));
        Bukkit.broadcastMessage("");
        for (Player on : Bukkit.getOnlinePlayers()) {
            ColorUtil.sound(on, ev.template.kind == EventKind.BOSS ? "entity.ender_dragon.growl" : "block.beacon.activate", 0.85f, 1.05f);
        }
    }

    /**
     * 100% сухопутный поиск координат: НИКОГДА не спавнит в океане, море, реке, болоте, воде, лаве или на льду над водой!
     */
    private Location findStrictDryLandSpot(World surv) {
        for (int attempt = 0; attempt < 140; attempt++) {
            int x = rng.nextInt(2400) - 1200;
            int z = rng.nextInt(2400) - 1200;
            if (Math.abs(x) < 180 && Math.abs(z) < 180) continue;

            boolean tooClose = false;
            for (ActiveBossEvent other : activeEvents.values()) {
                if (other.center.getWorld() != null && other.center.getWorld().equals(surv)) {
                    if (Math.hypot(other.center.getBlockX() - x, other.center.getBlockZ() - z) < 380.0) {
                        tooClose = true;
                        break;
                    }
                }
            }
            if (tooClose) continue;
            if (plugin.getStore().overlapsAnyClaim(surv.getName(), x, z, 16)) continue;

            Location dry = inspectDryColumn(surv, x, z);
            if (dry != null) return dry;
        }
        // Spiral scan fallback until strict dry land is found
        for (int r = 250; r <= 2000; r += 85) {
            for (int deg = 0; deg < 360; deg += 30) {
                int x = (int) Math.round(Math.cos(Math.toRadians(deg)) * r);
                int z = (int) Math.round(Math.sin(Math.toRadians(deg)) * r);
                Location dry = inspectDryColumn(surv, x, z);
                if (dry != null) return dry;
            }
        }
        Block fb = surv.getHighestBlockAt(350, 350);
        return fb.getLocation().add(0, 1, 0);
    }

    private Location inspectDryColumn(World surv, int x, int z) {
        surv.getChunkAt(x >> 4, z >> 4).load();
        try {
            Biome bio = surv.getBiome(x, 70, z);
            if (bio != null) {
                String bn = bio.name().toUpperCase(Locale.ROOT);
                if (bn.contains("OCEAN") || bn.contains("RIVER") || bn.contains("BEACH") || bn.contains("SWAMP")) {
                    return null;
                }
            }
        } catch (Throwable ignored) {}

        int topY = surv.getHighestBlockYAt(x, z);
        // Step down through leaves/snow/grass/logs to find true solid terrain
        int groundY = -1;
        for (int y = topY; y >= 62; y--) {
            Material m = surv.getBlockAt(x, y, z).getType();
            String mn = m.name();
            if (m == Material.AIR || mn.contains("LEAVES") || mn.contains("LOG") || mn.contains("GRASS") && m != Material.GRASS_BLOCK
                    || mn.contains("FERN") || mn.contains("FLOWER") || m == Material.SNOW || mn.contains("VINE")) {
                continue;
            }
            if (isWetOrAquatic(m)) return null;
            if (m.isSolid()) {
                groundY = y;
                break;
            }
            return null;
        }
        if (groundY < 63 || groundY > 135) return null;

        // Verify 7x7 area around (x, groundY, z) has ZERO water/lava/ice/kelp!
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    Material check = surv.getBlockAt(x + dx, groundY + dy, z + dz).getType();
                    if (isWetOrAquatic(check)) return null;
                }
            }
        }
        return new Location(surv, x, groundY + 1, z);
    }

    private boolean isWetOrAquatic(Material m) {
        if (m == null) return false;
        String n = m.name();
        return m == Material.WATER || m == Material.LAVA || m == Material.BUBBLE_COLUMN
                || m == Material.ICE || m == Material.FROSTED_ICE || m == Material.KELP
                || m == Material.KELP_PLANT || m == Material.SEAGRASS || m == Material.TALL_SEAGRASS
                || n.contains("CORAL") || m == Material.LILY_PAD;
    }

    // ================= STYLISH v5.7 ARCHITECTURAL EVENT STRUCTURES =================
    private void setBlk(ActiveBossEvent ev, int dx, int dy, int dz, Material mat) {
        Location l = ev.center.clone().add(dx, dy, dz);
        saveBlock(ev, l);
        l.getBlock().setType(mat, false);
    }

    private void setStair(ActiveBossEvent ev, int dx, int dy, int dz, Material stairMat, BlockFace facing, boolean upsideDown) {
        Location l = ev.center.clone().add(dx, dy, dz);
        saveBlock(ev, l);
        Block b = l.getBlock();
        b.setType(stairMat, false);
        try {
            BlockData bd = b.getBlockData();
            if (bd instanceof Directional) {
                ((Directional) bd).setFacing(facing);
            }
            if (bd instanceof Bisected) {
                ((Bisected) bd).setHalf(upsideDown ? Bisected.Half.TOP : Bisected.Half.BOTTOM);
            }
            b.setBlockData(bd, false);
        } catch (Throwable ignored) {}
    }

    private void clearAndSavePad(ActiveBossEvent ev, int radius, int height) {
        Location c = ev.center;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > (radius + 1) * (radius + 1)) continue;
                for (int dy = -3; dy <= height; dy++) {
                    Location l = c.clone().add(dx, dy, dz);
                    saveBlock(ev, l);
                    if (dy >= 0) l.getBlock().setType(Material.AIR, false);
                }
            }
        }
    }

    private void fillGroundedFloor(ActiveBossEvent ev, int dx, int dz, Material floorMat, Material baseMat) {
        setBlk(ev, dx, -1, dz, floorMat);
        for (int dy = -2; dy >= -4; dy--) {
            Block below = ev.center.clone().add(dx, dy, dz).getBlock();
            if (!below.getType().isSolid()) {
                setBlk(ev, dx, dy, dz, baseMat);
            }
        }
    }

    private void addCardinalStairRamps(ActiveBossEvent ev, int ringDist, Material stairMat, Material baseMat) {
        //Note: In Minecraft Stairs, setFacing() points toward the BACK (full-height side) of the stair!
        //So walking UP toward center (0,0) from z=-ringDist means facing SOUTH; from z=+ringDist means facing NORTH!
        for (int w = -2; w <= 2; w++) {
            fillGroundedFloor(ev, w, -ringDist - 1, baseMat, baseMat);
            setStair(ev, w, -1, -ringDist, stairMat, BlockFace.SOUTH, false);
            fillGroundedFloor(ev, w, ringDist + 1, baseMat, baseMat);
            setStair(ev, w, -1, ringDist, stairMat, BlockFace.NORTH, false);
            fillGroundedFloor(ev, -ringDist - 1, w, baseMat, baseMat);
            setStair(ev, -ringDist, -1, w, stairMat, BlockFace.EAST, false);
            fillGroundedFloor(ev, ringDist + 1, w, baseMat, baseMat);
            setStair(ev, ringDist, -1, w, stairMat, BlockFace.WEST, false);
        }
    }

    private void buildCenterPedestalBevel(ActiveBossEvent ev, Material stairMat, Material centerBase) {
        setBlk(ev, 0, -1, 0, centerBase);
        setStair(ev, 0, 0, -1, stairMat, BlockFace.SOUTH, false);
        setStair(ev, 0, 0, 1, stairMat, BlockFace.NORTH, false);
        setStair(ev, -1, 0, 0, stairMat, BlockFace.EAST, false);
        setStair(ev, 1, 0, 0, stairMat, BlockFace.WEST, false);
    }

    /**
     * 👹 17×17 Стильный Храмовый Колизей для каждого из 5 Мифических Боссов (5 индивидуальных стилей архитектуры!)
     */
    private void buildBossAltar(ActiveBossEvent ev) {
        clearAndSavePad(ev, 8, 9);
        String id = ev.template.id;

        Material mainFloor = Material.POLISHED_BLACKSTONE_BRICKS;
        Material accentFloor = Material.CHISELED_POLISHED_BLACKSTONE;
        Material runeGlass = Material.RED_STAINED_GLASS;
        Material pillarMat = Material.POLISHED_BLACKSTONE_BRICKS;
        Material capMat = Material.CRYING_OBSIDIAN;
        Material stairMat = Material.POLISHED_BLACKSTONE_BRICK_STAIRS;
        Material lightMat = Material.SHROOMLIGHT;
        Material lanternMat = Material.SOUL_LANTERN;

        if ("grinch".equals(id)) {
            mainFloor = Material.SMOOTH_QUARTZ;
            accentFloor = Material.BLUE_ICE;
            runeGlass = Material.LIGHT_BLUE_STAINED_GLASS;
            pillarMat = Material.QUARTZ_PILLAR;
            capMat = Material.PACKED_ICE;
            stairMat = Material.QUARTZ_STAIRS;
            lightMat = Material.SEA_LANTERN;
            lanternMat = Material.SOUL_LANTERN;
        } else if ("titan".equals(id)) {
            mainFloor = Material.SMOOTH_QUARTZ;
            accentFloor = Material.CHISELED_QUARTZ_BLOCK;
            runeGlass = Material.YELLOW_STAINED_GLASS;
            pillarMat = Material.QUARTZ_PILLAR;
            capMat = Material.GOLD_BLOCK;
            stairMat = Material.QUARTZ_STAIRS;
            lightMat = Material.SEA_LANTERN;
            lanternMat = Material.LANTERN;
        } else if ("osiris".equals(id)) {
            mainFloor = Material.SMOOTH_SANDSTONE;
            accentFloor = Material.EMERALD_BLOCK;
            runeGlass = Material.LIME_STAINED_GLASS;
            pillarMat = Material.CHISELED_SANDSTONE;
            capMat = Material.GOLD_BLOCK;
            stairMat = Material.SMOOTH_SANDSTONE_STAIRS;
            lightMat = Material.SEA_LANTERN;
            lanternMat = Material.LANTERN;
        } else if ("andromeda".equals(id)) {
            mainFloor = Material.DEEPSLATE_TILES;
            accentFloor = Material.AMETHYST_BLOCK;
            runeGlass = Material.MAGENTA_STAINED_GLASS;
            pillarMat = Material.PURPUR_PILLAR;
            capMat = Material.CRYING_OBSIDIAN;
            stairMat = Material.DEEPSLATE_TILE_STAIRS;
            lightMat = Material.SEA_LANTERN;
            lanternMat = Material.SOUL_LANTERN;
        }

        // Circular 15x15 Sanctum Floor with Glowing Geometric Star-Sigil!
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                double r = Math.hypot(dx, dz);
                if (r > 7.4) continue;
                boolean isStarRune = (dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz)) && r >= 1.5 && r <= 5.8;
                boolean isRim = r >= 6.3;
                if (isStarRune) {
                    setBlk(ev, dx, -2, dz, lightMat);
                    setBlk(ev, dx, -1, dz, runeGlass);
                } else {
                    fillGroundedFloor(ev, dx, dz, isRim ? accentFloor : mainFloor, mainFloor);
                }
            }
        }
        addCardinalStairRamps(ev, 7, stairMat, mainFloor);

        // 8 Octagonal Gothic Sanctuary Columns + Sculpted Arch Finials!
        int[][] cols = new int[][]{
                {-6, -2}, {-6, 2}, {6, -2}, {6, 2},
                {-2, -6}, {2, -6}, {-2, 6}, {2, 6}
        };
        for (int[] cl : cols) {
            int cx = cl[0], cz = cl[1];
            setBlk(ev, cx, 0, cz, accentFloor);
            for (int dy = 1; dy <= 4; dy++) {
                setBlk(ev, cx, dy, cz, pillarMat);
            }
            setBlk(ev, cx, 5, cz, capMat);
            setBlk(ev, cx, 6, cz, lightMat);
            setBlk(ev, cx, 7, cz, Material.END_ROD);
            // Inward hanging lantern bracket facing arena!
            int ix = cx == 0 ? 0 : (cx > 0 ? cx - 1 : cx + 1);
            int iz = cz == 0 ? 0 : (cz > 0 ? cz - 1 : cz + 1);
            if (Math.abs(cx) == 6) {
                setStair(ev, ix, 4, cz, stairMat, cx > 0 ? BlockFace.EAST : BlockFace.WEST, true);
                setBlk(ev, ix, 3, cz, lanternMat);
            } else {
                setStair(ev, cx, 4, iz, stairMat, cz > 0 ? BlockFace.SOUTH : BlockFace.NORTH, true);
                setBlk(ev, cx, 3, iz, lanternMat);
            }
        }

        // Connect paired columns into 4 Grand Entrance Portals!
        for (int s = -1; s <= 1; s++) {
            setBlk(ev, -6, 5, s, accentFloor);
            setBlk(ev, 6, 5, s, accentFloor);
            setBlk(ev, s, 5, -6, accentFloor);
            setBlk(ev, s, 5, 6, accentFloor);
        }

        // Raised Central Vault Pedestal + Sealed Chest
        buildCenterPedestalBevel(ev, stairMat, lightMat);
        setBlk(ev, 0, 0, 0, Material.ENDER_CHEST);
    }

    /**
     * 🪂 Стильный Военный АирДроп 15×15: бронированная вертолётная площадка, сигнальные вышки и огромный 3D купол парашюта 11×11!
     */
    private void buildBeautifulAirdropStructure(ActiveBossEvent ev) {
        clearAndSavePad(ev, 8, 12);
        // 15x15 Octagonal Tactical Helipad with Yellow/Black Chevron Hazard Rim & Glowing Crosshair!
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > 10 || Math.max(Math.abs(dx), Math.abs(dz)) > 6) continue;
                boolean border = Math.max(Math.abs(dx), Math.abs(dz)) == 6 || Math.abs(dx) + Math.abs(dz) == 10;
                boolean lightCross = (dx == 0 || dz == 0) && Math.max(Math.abs(dx), Math.abs(dz)) <= 4;
                boolean innerRing = Math.max(Math.abs(dx), Math.abs(dz)) == 3 && Math.min(Math.abs(dx), Math.abs(dz)) <= 3;
                if (border) {
                    Material haz = ((dx + dz) & 1) == 0 ? Material.YELLOW_CONCRETE : Material.BLACK_CONCRETE;
                    fillGroundedFloor(ev, dx, dz, haz, Material.POLISHED_BLACKSTONE_BRICKS);
                } else if (lightCross) {
                    setBlk(ev, dx, -2, dz, Material.SHROOMLIGHT);
                    setBlk(ev, dx, -1, dz, Material.ORANGE_STAINED_GLASS);
                } else {
                    fillGroundedFloor(ev, dx, dz,
                            innerRing ? Material.CHISELED_POLISHED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS,
                            Material.DEEPSLATE_TILES);
                }
            }
        }
        addCardinalStairRamps(ev, 6, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, Material.POLISHED_BLACKSTONE_BRICKS);

        // 4 Tactical Flare Towers at (±4, ±4)
        int[][] towers = new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}};
        for (int[] tw : towers) {
            setBlk(ev, tw[0], 0, tw[1], Material.POLISHED_BLACKSTONE_BRICKS);
            setBlk(ev, tw[0], 1, tw[1], Material.COBBLED_DEEPSLATE_WALL);
            setBlk(ev, tw[0], 2, tw[1], Material.SHROOMLIGHT);
            setBlk(ev, tw[0], 3, tw[1], Material.CAMPFIRE);
        }

        // Diagonal Parachute Rigging Cables (Chains & Iron Bars rising from cargo corners up to 11x11 Canopy!)
        int[][] dirs = new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
        for (int[] d : dirs) {
            setBlk(ev, d[0], 0, d[1], Material.IRON_BARS);
            setBlk(ev, d[0], 1, d[1], Material.CHAIN);
            setBlk(ev, d[0] * 2, 2, d[1] * 2, Material.CHAIN);
            setBlk(ev, d[0] * 2, 3, d[1] * 2, Material.CHAIN);
            setBlk(ev, d[0] * 3, 4, d[1] * 3, Material.CHAIN);
            setBlk(ev, d[0] * 3, 5, d[1] * 3, Material.CHAIN);
            setBlk(ev, d[0] * 4, 6, d[1] * 4, Material.CHAIN);
            setBlk(ev, d[0] * 4, 7, d[1] * 4, Material.CHAIN);
        }

        // Giant Billowing 11x11 3D Curved Parachute Canopy (dy = 8..11) in Red & White Military Stripes!
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                double r = Math.hypot(dx, dz);
                if (r > 5.4) continue;
                int dy = r <= 1.6 ? 11 : (r <= 3.3 ? 10 : (r <= 4.5 ? 9 : 8));
                // Leave small realistic central apex air-vent at (0,11,0)
                if (dx == 0 && dz == 0) {
                    setBlk(ev, 0, 11, 0, Material.IRON_BARS);
                    continue;
                }
                boolean redGore = (((int) Math.floor((Math.atan2(dz, dx) + Math.PI) / (Math.PI / 4.0))) & 1) == 0;
                setBlk(ev, dx, dy, dz, redGore ? Material.RED_CONCRETE : Material.WHITE_CONCRETE);
            }
        }

        // Armored Supply Pod Core
        buildCenterPedestalBevel(ev, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, Material.LODESTONE);
        setBlk(ev, 0, 0, 0, Material.RED_SHULKER_BOX);
    }

    /**
     * ☄ Стильный Звёздный Метеорит 17×17: ударный кратер с разломами магмы под стеклом, пиками аметиста и огненным хвостом!
     */
    private void buildStarMeteorStructure(ActiveBossEvent ev) {
        clearAndSavePad(ev, 8, 10);
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                double r = Math.hypot(dx, dz);
                if (r > 7.5) continue;
                boolean cardinalPass = Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
                boolean fissure = (Math.abs(dx - dz) <= 0 || Math.abs(dx + dz) <= 0) && r >= 1.5 && r <= 5.5;
                if (fissure) {
                    setBlk(ev, dx, -2, dz, Material.SHROOMLIGHT);
                    setBlk(ev, dx, -1, dz, r < 3.2 ? Material.RED_STAINED_GLASS : Material.ORANGE_STAINED_GLASS);
                } else {
                    Material fl = r < 2.4 ? Material.CRYING_OBSIDIAN
                            : (r < 4.8 ? Material.BLACKSTONE : Material.POLISHED_BASALT);
                    fillGroundedFloor(ev, dx, dz, fl, Material.BLACKSTONE);
                }
                // Jagged Impact Crater Rim + Erupting Amethyst Crystal Spikes!
                if (r >= 5.6 && r <= 7.4 && !cardinalPass) {
                    setBlk(ev, dx, 0, dz, (dx + dz) % 2 == 0 ? Material.OBSIDIAN : Material.BLACKSTONE);
                    if (((dx * 31 + dz * 17) & 3) == 0) {
                        setBlk(ev, dx, 1, dz, Material.AMETHYST_BLOCK);
                        setBlk(ev, dx, 2, dz, Material.PURPLE_STAINED_GLASS);
                    } else if (((dx + dz) & 1) == 0) {
                        setBlk(ev, dx, 1, dz, Material.MAGMA_BLOCK);
                    }
                }
            }
        }

        // Rich Mineable Meteorite Ore Cluster around the core!
        setBlk(ev, 2, 0, 0, Material.DEEPSLATE_DIAMOND_ORE);
        setBlk(ev, -2, 0, 0, Material.DEEPSLATE_DIAMOND_ORE);
        setBlk(ev, 0, 0, 2, Material.DEEPSLATE_EMERALD_ORE);
        setBlk(ev, 0, 0, -2, Material.DEEPSLATE_EMERALD_ORE);
        setBlk(ev, 1, 0, 1, Material.GILDED_BLACKSTONE);
        setBlk(ev, -1, 0, -1, Material.GILDED_BLACKSTONE);
        setBlk(ev, 1, 0, -1, Material.AMETHYST_BLOCK);
        setBlk(ev, -1, 0, 1, Material.CRYING_OBSIDIAN);

        // Dramatic Diagonal Cosmic Fire-Plume Trailing into the Sky!
        for (int step = 1; step <= 5; step++) {
            setBlk(ev, -step, step + 2, -step, step <= 2 ? Material.MAGMA_BLOCK : Material.ORANGE_STAINED_GLASS);
            setBlk(ev, -step, step + 3, -step, step <= 3 ? Material.RED_STAINED_GLASS : Material.PURPLE_STAINED_GLASS);
        }
        setBlk(ev, -2, 1, -2, Material.CAMPFIRE);
        setBlk(ev, 2, 1, 2, Material.CAMPFIRE);

        setBlk(ev, 0, -1, 0, Material.CRYING_OBSIDIAN);
        setBlk(ev, 0, 0, 0, Material.ENDER_CHEST);
    }

    /**
     * 💎 Стильный Пантеон Золотого Маяка Богатства 17×17 с мраморной колоннадой, фризом и небесным лучом Маяка!
     */
    private void buildGoldenBeaconStructure(ActiveBossEvent ev) {
        clearAndSavePad(ev, 8, 9);
        // Real 3x3 Gold Pyramid at y=-2 so the Beacon at (0,-1,0) activates a real sky beam!
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                setBlk(ev, dx, -2, dz, Material.GOLD_BLOCK);
            }
        }
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                double r = Math.hypot(dx, dz);
                if (r > 7.4) continue;
                Material fl;
                if (dx == 0 && dz == 0) {
                    fl = Material.BEACON;
                } else if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                    fl = Material.GOLD_BLOCK;
                } else if ((dx == 0 || dz == 0) && r <= 6.0) {
                    fl = Material.CHISELED_QUARTZ_BLOCK;
                } else if (Math.abs(dx) == Math.abs(dz) && r <= 5.5) {
                    fl = Material.EMERALD_BLOCK;
                } else if (r >= 6.3) {
                    fl = Material.GOLD_BLOCK;
                } else {
                    fl = Material.SMOOTH_QUARTZ;
                }
                if (dx == 0 && dz == 0) {
                    setBlk(ev, 0, -1, 0, Material.BEACON);
                } else {
                    fillGroundedFloor(ev, dx, dz, fl, Material.SMOOTH_QUARTZ);
                }
            }
        }
        addCardinalStairRamps(ev, 7, Material.QUARTZ_STAIRS, Material.SMOOTH_QUARTZ);

        // 8 Fluted Temple Columns + Circular Marble & Gold Entablature Ring at dy=5..6 (open sky center for Beacon beam!)
        int[][] cols = new int[][]{
                {-5, -2}, {-5, 2}, {5, -2}, {5, 2},
                {-2, -5}, {2, -5}, {-2, 5}, {2, 5}
        };
        for (int[] cl : cols) {
            for (int dy = 0; dy <= 4; dy++) {
                setBlk(ev, cl[0], dy, cl[1], Material.QUARTZ_PILLAR);
            }
            setBlk(ev, cl[0], 5, cl[1], Material.GOLD_BLOCK);
            setBlk(ev, cl[0], 6, cl[1], Material.SEA_LANTERN);
            setBlk(ev, cl[0], 7, cl[1], Material.END_ROD);
        }
        // Connect Temple Ring Crown at dy = 5
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                double r = Math.hypot(dx, dz);
                if (r >= 4.7 && r <= 5.6) {
                    setBlk(ev, dx, 5, dz, ((dx + dz) & 1) == 0 ? Material.SMOOTH_QUARTZ : Material.CHISELED_QUARTZ_BLOCK);
                }
            }
        }
        setBlk(ev, -4, 4, 0, Material.LANTERN);
        setBlk(ev, 4, 4, 0, Material.LANTERN);
        setBlk(ev, 0, 4, -4, Material.LANTERN);
        setBlk(ev, 0, 4, 4, Material.LANTERN);

        // Stained glass while locked so Beacon beam pierces straight into the sky!
        buildCenterPedestalBevel(ev, Material.QUARTZ_STAIRS, Material.BEACON);
        setBlk(ev, 0, 0, 0, Material.YELLOW_STAINED_GLASS);
    }

    /**
     * ❄ Стильная Готическая Ледяная Цитадель Санты 17×17 с шпилями изо льда, ковровыми дорожками и люстрами душ!
     */
    private void buildFrostCitadelStructure(ActiveBossEvent ev) {
        clearAndSavePad(ev, 8, 10);
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                double r = Math.hypot(dx, dz);
                if (r > 7.4) continue;
                boolean redCarpet = (dx == 0 || dz == 0) && r >= 1.5 && r <= 6.5;
                boolean snowflakeDiag = Math.abs(dx) == Math.abs(dz) && r <= 5.8;
                if (redCarpet) {
                    fillGroundedFloor(ev, dx, dz, Material.RED_CONCRETE, Material.SMOOTH_QUARTZ);
                } else if (snowflakeDiag) {
                    setBlk(ev, dx, -2, dz, Material.SEA_LANTERN);
                    setBlk(ev, dx, -1, dz, Material.LIGHT_BLUE_STAINED_GLASS);
                } else {
                    Material fl = r >= 6.3 ? Material.PRISMARINE_BRICKS
                            : (((dx + dz) & 1) == 0 ? Material.BLUE_ICE : Material.SMOOTH_QUARTZ);
                    fillGroundedFloor(ev, dx, dz, fl, Material.PACKED_ICE);
                }
            }
        }
        addCardinalStairRamps(ev, 7, Material.QUARTZ_STAIRS, Material.SMOOTH_QUARTZ);

        // 8 Tall Gothic Ice Spires (dy = 0..8) + Frosted Crystal Dome Crown!
        int[][] spires = new int[][]{
                {-5, -2}, {-5, 2}, {5, -2}, {5, 2},
                {-2, -5}, {2, -5}, {-2, 5}, {2, 5}
        };
        for (int[] sp : spires) {
            setBlk(ev, sp[0], 0, sp[1], Material.CHISELED_QUARTZ_BLOCK);
            setBlk(ev, sp[0], 1, sp[1], Material.QUARTZ_PILLAR);
            setBlk(ev, sp[0], 2, sp[1], Material.BLUE_ICE);
            setBlk(ev, sp[0], 3, sp[1], Material.QUARTZ_PILLAR);
            setBlk(ev, sp[0], 4, sp[1], Material.BLUE_ICE);
            setBlk(ev, sp[0], 5, sp[1], Material.SEA_LANTERN);
            setBlk(ev, sp[0], 6, sp[1], Material.BLUE_ICE);
            setBlk(ev, sp[0], 7, sp[1], Material.LIGHT_BLUE_STAINED_GLASS);
            setBlk(ev, sp[0], 8, sp[1], Material.END_ROD);
        }
        // Overhead Frost Crystal Halo at dy = 6..7
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                double r = Math.hypot(dx, dz);
                if (r >= 2.8 && r <= 4.3) {
                    setBlk(ev, dx, 6, dz, ((dx + dz) & 1) == 0 ? Material.LIGHT_BLUE_STAINED_GLASS : Material.WHITE_STAINED_GLASS);
                } else if (r > 1.2 && r < 2.8) {
                    setBlk(ev, dx, 7, dz, Material.LIGHT_BLUE_STAINED_GLASS);
                }
            }
        }
        setBlk(ev, 0, 7, 0, Material.SEA_LANTERN);
        setBlk(ev, 0, 6, 0, Material.CHAIN);
        setBlk(ev, 0, 5, 0, Material.SOUL_LANTERN);

        buildCenterPedestalBevel(ev, Material.QUARTZ_STAIRS, Material.SEA_LANTERN);
        setBlk(ev, 0, 0, 0, Material.LIGHT_BLUE_SHULKER_BOX);
    }

    /**
     * 🌋 НОВЫЙ СТИЛЬНЫЙ ИВЕНТ v5.7 — Адский Вулкан Ифрита 17×17 с базальтовыми рогами, реками лавы под стеклом и Рейд-лутом!
     */
    private void buildVolcanoStructure(ActiveBossEvent ev) {
        clearAndSavePad(ev, 8, 10);
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                double r = Math.hypot(dx, dz);
                if (r > 7.5) continue;
                boolean lavaRiver = (dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz)) && r >= 1.4 && r <= 6.2;
                if (lavaRiver && Math.abs(dx) != 0 && Math.abs(dz) != 0) {
                    setBlk(ev, dx, -2, dz, Material.SHROOMLIGHT);
                    setBlk(ev, dx, -1, dz, Material.ORANGE_STAINED_GLASS);
                } else {
                    Material fl = r < 2.6 ? Material.MAGMA_BLOCK
                            : (r >= 6.2 ? Material.CRIMSON_HYPHAE : Material.POLISHED_BLACKSTONE_BRICKS);
                    fillGroundedFloor(ev, dx, dz, fl, Material.BLACKSTONE);
                }
            }
        }
        addCardinalStairRamps(ev, 7, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, Material.POLISHED_BLACKSTONE_BRICKS);

        // 4 Towering Curved Volcanic Horn Spires curving inward toward the caldera!
        int[][] horns = new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
        for (int[] h : horns) {
            int hx = h[0], hz = h[1];
            for (int dy = 0; dy <= 3; dy++) {
                setBlk(ev, hx * 5, dy, hz * 5, dy == 0 ? Material.GILDED_BLACKSTONE : Material.POLISHED_BASALT);
            }
            for (int dy = 3; dy <= 5; dy++) {
                setBlk(ev, hx * 4, dy, hz * 4, Material.BLACKSTONE);
            }
            setBlk(ev, hx * 3, 6, hz * 3, Material.MAGMA_BLOCK);
            setBlk(ev, hx * 3, 7, hz * 3, Material.CAMPFIRE);
        }

        buildCenterPedestalBevel(ev, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, Material.SHROOMLIGHT);
        setBlk(ev, 0, 0, 0, Material.ORANGE_SHULKER_BOX);
    }

    private void saveBlock(ActiveBossEvent ev, Location loc) {
        Location key = loc.getBlock().getLocation();
        if (!ev.savedBlocks.containsKey(key)) {
            ev.savedBlocks.put(key, key.getBlock().getState());
        }
    }

    private void spawnChestHologram(ActiveBossEvent ev) {
        removeHologram(ev);
        String[] lines;
        if (ev.bossDefeated) {
            lines = new String[]{
                    "&#00E676&l🔓 СОКРОВИЩНИЦА ОТКРЫТА!",
                    "&fИвент: " + ev.template.displayName,
                    "&#FFD740▸ Забирай редкий 3D лут скорее!"
            };
        } else if (ev.template.kind == EventKind.BOSS) {
            lines = new String[]{
                    "&#FF1744&l🔒 СУНДУК ЗАПЕЧАТАН БОССОМ",
                    "&fСтраж: " + ev.template.displayName,
                    "&7Убейте Босса рядом, чтобы открыть!"
            };
        } else {
            lines = new String[]{
                    ev.template.displayName,
                    "&#FFD740⏳ До открытия замка: &f" + ColorUtil.fmtTime(ev.unlockCountdownSec),
                    ev.template.kind == EventKind.BEACON
                            ? "&#00E676💰 Раздаёт по +3 000$ и +2❖ каждые 5 сек!"
                            : "&7Удерживайте точку от других игроков!"
            };
        }
        double baseY = ev.center.getY() + 2.05;
        for (int i = 0; i < lines.length; i++) {
            Location hl = ev.center.clone().add(0.5, baseY - i * 0.28, 0.5);
            ArmorStand as = (ArmorStand) hl.getWorld().spawnEntity(hl, EntityType.ARMOR_STAND);
            as.setVisible(false);
            as.setGravity(false);
            as.setMarker(true);
            as.setSmall(true);
            as.setCustomName(ColorUtil.c(lines[i]));
            as.setCustomNameVisible(true);
            as.addScoreboardTag("kg_holo");
            ev.hologramStands.add(as.getUniqueId());
        }
    }

    private void removeHologram(ActiveBossEvent ev) {
        if (ev.center != null && ev.center.getWorld() != null) {
            try { ev.center.getChunk().load(); } catch (Throwable ignored) {}
            for (Entity near : ev.center.getWorld().getNearbyEntities(ev.center, 6, 8, 6)) {
                if (near.getScoreboardTags().contains("kg_holo") && ev.hologramStands.contains(near.getUniqueId())) {
                    near.remove();
                }
            }
        }
        for (UUID id : ev.hologramStands) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        ev.hologramStands.clear();
    }

    private void configureBossEntity(LivingEntity boss, BossTemplate tpl, String instanceId, double currentHp) {
        boss.addScoreboardTag("kg_event_boss");
        boss.addScoreboardTag("kg_boss_" + instanceId);
        boss.setRemoveWhenFarAway(false);
        boss.setPersistent(true);
        setMaxHealthSafe(boss, tpl.maxHp);
        boss.setHealth(Math.max(1.0, Math.min(tpl.maxHp, currentHp)));
        boss.setCustomName(ColorUtil.c(tpl.displayName + " &c❤ " + (int) Math.ceil(boss.getHealth())));
        boss.setCustomNameVisible(true);
        ColorUtil.addPot(boss, "fire_resistance", "FIRE_RESISTANCE", 72000, 0);

        EntityEquipment eq = boss.getEquipment();
        if (eq != null) {
            if ("krush".equals(tpl.id)) {
                eq.setHelmet(ItemFactory.armorPiece("krush", 0));
                eq.setChestplate(ItemFactory.armorPiece("krush", 1));
                eq.setLeggings(ItemFactory.armorPiece("krush", 2));
                eq.setBoots(ItemFactory.armorPiece("krush", 3));
                eq.setItemInMainHand(ItemFactory.swordKrush());
            } else if ("grinch".equals(tpl.id)) {
                eq.setHelmet(ItemFactory.armorPiece("frost", 0));
                eq.setChestplate(ItemFactory.armorPiece("frost", 1));
                eq.setLeggings(ItemFactory.armorPiece("frost", 2));
                eq.setBoots(ItemFactory.armorPiece("frost", 3));
                eq.setItemInMainHand(ItemFactory.bowIceRw());
            } else if ("osiris".equals(tpl.id)) {
                eq.setHelmet(ItemFactory.armorPiece("dracula", 0));
                eq.setChestplate(ItemFactory.armorPiece("dracula", 1));
                eq.setLeggings(ItemFactory.armorPiece("dracula", 2));
                eq.setBoots(ItemFactory.armorPiece("dracula", 3));
                eq.setItemInMainHand(ItemFactory.swordDracula());
            } else if ("andromeda".equals(tpl.id)) {
                eq.setHelmet(ItemFactory.armorPiece("phantom", 0));
            }
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            eq.setLeggingsDropChance(0f);
            eq.setBootsDropChance(0f);
            eq.setItemInMainHandDropChance(0f);
        }
    }

    private void setMaxHealthSafe(LivingEntity ent, double maxHp) {
        try {
            Attribute attr = null;
            for (String fn : new String[]{"MAX_HEALTH", "GENERIC_MAX_HEALTH"}) {
                try {
                    Field f = Attribute.class.getField(fn);
                    attr = (Attribute) f.get(null);
                    if (attr != null) break;
                } catch (Throwable ignored) {}
            }
            if (attr != null) {
                AttributeInstance inst = ent.getAttribute(attr);
                if (inst != null) inst.setBaseValue(maxHp);
            }
        } catch (Throwable ignored) {
        }
    }

    private String formatBarTitle(ActiveBossEvent ev, double curHp) {
        int bx = ev.center.getBlockX(), by = ev.center.getBlockY(), bz = ev.center.getBlockZ();
        if (ev.bossDefeated) {
            return ColorUtil.c("&#00E676&l🔓 ОТКРЫТ &8(" + ev.template.displayName + "&8)"
                    + " &8│ &#80D8FFXYZ: &f" + bx + " " + by + " " + bz
                    + " &8│ &#FFD740⏳ " + ColorUtil.fmtTime(ev.secondsLeft));
        }
        if (ev.template.kind == EventKind.BOSS) {
            return ColorUtil.c(ev.template.displayName
                    + " &8│ &c❤ " + (int) Math.ceil(curHp) + "&7/&c" + (int) ev.template.maxHp
                    + " &8│ &#80D8FFXYZ: &f" + bx + " " + by + " " + bz
                    + " &8│ &#FFD740⏳ " + ColorUtil.fmtTime(ev.secondsLeft));
        }
        return ColorUtil.c(ev.template.displayName
                + " &8│ &#FFD740🔒 Открытие: &f" + ColorUtil.fmtTime(ev.unlockCountdownSec)
                + " &8│ &#80D8FFXYZ: &f" + bx + " " + by + " " + bz);
    }

    private void tickSecond() {
        autoCountdownSec--;
        if (autoCountdownSec <= 0) {
            autoCountdownSec = 600; // Every 10 minutes auto-spawn a world event or boss!
            if (activeEvents.size() < 3) {
                startRandomEvent();
            }
        }

        Iterator<Map.Entry<String, ActiveBossEvent>> it = activeEvents.entrySet().iterator();
        List<String> toEnd = new ArrayList<String>();
        while (it.hasNext()) {
            ActiveBossEvent ev = it.next().getValue();
            ev.secondsLeft--;
            if (ev.secondsLeft <= 0) {
                toEnd.add(ev.instanceId);
                continue;
            }

            double curHp = 0.0;
            if (ev.template.kind == EventKind.BOSS) {
                if (!ev.bossDefeated) {
                    Entity e = ev.bossUuid != null ? Bukkit.getEntity(ev.bossUuid) : null;
                    if (e instanceof LivingEntity && !e.isDead()) {
                        LivingEntity le = (LivingEntity) e;
                        le.setFireTicks(0);
                        curHp = le.getHealth();
                        ev.lastKnownHp = curHp;
                        le.setCustomName(ColorUtil.c(ev.template.displayName + " &c❤ " + (int) Math.ceil(curHp)));
                        if (le.getLocation().distanceSquared(ev.center) > 16 * 16) {
                            le.teleport(ev.center.clone().add(0.5, 1.2, 2.0));
                        }
                        if (ev.secondsLeft % 9 == 0) {
                            castBossSkill(ev, le);
                        }
                    } else if (e != null && e.isDead() && !ev.damageMap.isEmpty()) {
                        onBossSlain(ev, null);
                    } else {
                        // Entity chunk may be unloaded when no player is nearby — NEVER falsely kill the Boss!
                        // If a player IS nearby (<= 48 blocks) and the Boss entity vanished, respawn it with lastKnownHp!
                        curHp = ev.lastKnownHp;
                        World bw = ev.center.getWorld();
                        if (bw != null) {
                            boolean playerNear = false;
                            for (Player pl : bw.getPlayers()) {
                                if (pl.getLocation().distanceSquared(ev.center) <= 48 * 48) {
                                    playerNear = true;
                                    break;
                                }
                            }
                            if (playerNear) {
                                ev.center.getChunk().load();
                                LivingEntity found = null;
                                for (Entity near : bw.getNearbyEntities(ev.center, 32, 24, 32)) {
                                    if (near instanceof LivingEntity && !near.isDead()
                                            && near.getScoreboardTags().contains("kg_boss_" + ev.instanceId)) {
                                        found = (LivingEntity) near;
                                        break;
                                    }
                                }
                                if (found != null) {
                                    ev.bossUuid = found.getUniqueId();
                                    curHp = found.getHealth();
                                    ev.lastKnownHp = curHp;
                                } else {
                                    LivingEntity respawned = (LivingEntity) bw.spawnEntity(ev.center.clone().add(0.5, 1.2, 2.5), ev.template.entityType);
                                    configureBossEntity(respawned, ev.template, ev.instanceId, ev.lastKnownHp);
                                    ev.bossUuid = respawned.getUniqueId();
                                }
                            }
                        }
                    }
                }
            } else {
                // Timed World Events: AirDrop, Meteor, Golden Beacon, Frost Citadel!
                tickWorldEventVisualsAndRewards(ev);
                if (!ev.bossDefeated) {
                    ev.unlockCountdownSec--;
                    if (ev.unlockCountdownSec % 5 == 0 && ev.center.getChunk().isLoaded()) {
                        updateCountdownHoloLine(ev);
                    }
                    if (ev.unlockCountdownSec <= 0) {
                        unlockTimedWorldEvent(ev);
                    }
                }
            }

            if (ev.bossBar != null) {
                ev.bossBar.setTitle(formatBarTitle(ev, curHp));
                double prog;
                if (ev.bossDefeated) {
                    prog = Math.max(0.0, Math.min(1.0, ev.secondsLeft / 180.0));
                } else if (ev.template.kind == EventKind.BOSS) {
                    prog = Math.max(0.0, Math.min(1.0, curHp / ev.template.maxHp));
                } else {
                    prog = Math.max(0.0, Math.min(1.0, (double) ev.unlockCountdownSec / (double) Math.max(1, ev.unlockTotalSec)));
                }
                ev.bossBar.setProgress(prog);
            }
        }
        for (String id : toEnd) {
            endEvent(id, true);
        }
    }

    private void updateCountdownHoloLine(ActiveBossEvent ev) {
        if (ev.hologramStands.size() >= 2) {
            Entity st = Bukkit.getEntity(ev.hologramStands.get(1));
            if (st instanceof ArmorStand) {
                st.setCustomName(ColorUtil.c("&#FFD740⏳ До открытия замка: &f" + ColorUtil.fmtTime(Math.max(0, ev.unlockCountdownSec))));
            }
        }
    }

    private void tickWorldEventVisualsAndRewards(ActiveBossEvent ev) {
        if (!ev.center.getChunk().isLoaded()) return;
        World w = ev.center.getWorld();
        if (w == null) return;
        Location mid = ev.center.clone().add(0.5, 1.2, 0.5);

        if (ev.template.kind == EventKind.AIRDROP) {
            // Signal flare beam shooting into the sky!
            for (int dy = 1; dy <= 10; dy += 2) {
                w.spawnParticle(Particle.FIREWORK, mid.clone().add(0, dy, 0), 4, 0.15, 0.3, 0.15, 0.02);
            }
            w.spawnParticle(Particle.FLAME, mid, 8, 0.4, 0.3, 0.4, 0.02);
        } else if (ev.template.kind == EventKind.METEOR) {
            w.spawnParticle(Particle.LAVA, mid, 6, 1.2, 0.6, 1.2, 0.02);
            w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, mid.clone().add(0, 0.8, 0), 5, 0.6, 0.8, 0.6, 0.02);
        } else if (ev.template.kind == EventKind.BEACON) {
            w.spawnParticle(Particle.TOTEM_OF_UNDYING, mid.clone().add(0, 0.8, 0), 12, 1.5, 1.0, 1.5, 0.08);
            // Every 5 seconds, rain real Coins & Relics to all players standing near the Golden Beacon!
            if (ev.secondsLeft % 5 == 0) {
                for (Entity ent : w.getNearbyEntities(mid, 14, 10, 14)) {
                    if (ent instanceof Player) {
                        Player p = (Player) ent;
                        plugin.getStore().addCoins(p.getName(), 3000.0);
                        plugin.getStore().addRelics(p.getName(), 2);
                        p.giveExp(8);
                        ColorUtil.sound(p, "entity.experience_orb.pickup", 0.7f, 1.4f);
                        ColorUtil.actionBar(p, "&#FFD740💎 Маяк Богатства: &a+3 000$ &8│ &b+2 Рилика ❖");
                    }
                }
            }
        } else if (ev.template.kind == EventKind.FROST_CITADEL) {
            w.spawnParticle(Particle.SNOWFLAKE, mid.clone().add(0, 0.6, 0), 22, 1.8, 1.2, 1.8, 0.04);
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, mid, 6, 0.8, 0.4, 0.8, 0.02);
        } else if (ev.template.kind == EventKind.VOLCANO) {
            w.spawnParticle(Particle.LAVA, mid.clone().add(0, 1.5, 0), 10, 1.8, 1.2, 1.8, 0.03);
            w.spawnParticle(Particle.FLAME, mid, 16, 1.5, 0.8, 1.5, 0.04);
            w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, mid.clone().add(0, 2.0, 0), 6, 1.0, 1.2, 1.0, 0.02);
        }
    }

    private void unlockTimedWorldEvent(ActiveBossEvent ev) {
        if (ev.bossDefeated) return;
        ev.bossDefeated = true;
        ev.secondsLeft = 180;

        int bx = ev.center.getBlockX(), by = ev.center.getBlockY(), bz = ev.center.getBlockZ();
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ColorUtil.c("&#00E676&l🔓 [ИВЕНТ ОТКРЫТ] &f" + ev.template.displayName
                + " &#00E676открыл сокровищницу на координатах &#80D8FF" + bx + ", " + by + ", " + bz + "&#00E676!"));
        Bukkit.broadcastMessage("");

        Block b = ev.center.getBlock();
        b.setType(Material.CHEST, false);
        if (b.getState() instanceof Chest) {
            Chest ch = (Chest) b.getState();
            populateWorldEventLoot(ch.getBlockInventory(), ev.template.kind);
        }

        // Fountain-drop bonus loot around AirDrop / Meteor / Citadel!
        World w = ev.center.getWorld();
        if (w != null) {
            ColorUtil.spawnFirework(ev.center);
            ItemStack[] fountain = new ItemStack[]{
                    new ItemStack(Material.DIAMOND, 12),
                    new ItemStack(Material.GOLDEN_CARROT, 32),
                    new ItemStack(Material.ENDER_PEARL, 16),
                    ItemFactory.plast(4),
                    ItemFactory.trapka(2),
                    ItemFactory.snowflakeCoin(10)
            };
            for (ItemStack it : fountain) {
                org.bukkit.entity.Item dropped = w.dropItemNaturally(ev.center.clone().add(0.5, 1.4, 0.5), it);
                dropped.setVelocity(new Vector((rng.nextDouble() - 0.5) * 0.45, 0.45, (rng.nextDouble() - 0.5) * 0.45));
            }
        }
        spawnChestHologram(ev);
    }

    private void populateWorldEventLoot(Inventory inv, EventKind kind) {
        if (kind == EventKind.AIRDROP) {
            inv.setItem(4, rng.nextBoolean() ? ItemFactory.sharTitana() : ItemFactory.elytraWind());
            inv.setItem(10, ItemFactory.plast(8));
            inv.setItem(11, ItemFactory.trapka(4));
            inv.setItem(12, ItemFactory.dezorient(3));
            inv.setItem(13, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 3));
            inv.setItem(14, ItemFactory.bozhyaAura(2));
            inv.setItem(15, ItemFactory.tntTier1(4));
            inv.setItem(16, new ItemStack(Material.GOLDEN_CARROT, 64));
            inv.setItem(21, ItemFactory.arrowAntiFly(16));
            inv.setItem(22, ItemFactory.potionKiller(2));
            inv.setItem(23, ItemFactory.armorPiece("zeus", rng.nextInt(4)));
        } else if (kind == EventKind.METEOR) {
            inv.setItem(4, rng.nextBoolean() ? ItemFactory.sharAresa() : ItemFactory.elytraPhoenix());
            inv.setItem(11, new ItemStack(Material.NETHERITE_INGOT, 5));
            inv.setItem(12, ItemFactory.ognennySmerch(4));
            inv.setItem(13, ItemFactory.pickDjamshut());
            inv.setItem(14, new ItemStack(Material.DIAMOND_BLOCK, 8));
            inv.setItem(15, rng.nextBoolean() ? ItemFactory.talKrushitelya() : ItemFactory.talKolossa());
            inv.setItem(21, ItemFactory.tntBlack(2));
            inv.setItem(22, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 4));
            inv.setItem(23, ItemFactory.maceKrush());
        } else if (kind == EventKind.BEACON) {
            inv.setItem(4, ItemFactory.sharApollona());
            inv.setItem(11, new ItemStack(Material.GOLD_BLOCK, 24));
            inv.setItem(12, new ItemStack(Material.EMERALD_BLOCK, 16));
            inv.setItem(13, ItemFactory.pickSpawner());
            inv.setItem(14, ItemFactory.yavnayaPyl(4));
            inv.setItem(15, ItemFactory.talImperatora());
            inv.setItem(21, ItemFactory.bowZeus());
            inv.setItem(22, ItemFactory.elytraThunder());
            inv.setItem(23, ItemFactory.arrowLightning(24));
        } else if (kind == EventKind.VOLCANO) {
            inv.setItem(4, rng.nextBoolean() ? ItemFactory.tntNuke(2) : ItemFactory.bowInferno());
            inv.setItem(11, ItemFactory.tntBlack(4));
            inv.setItem(12, ItemFactory.tntFlash(4));
            inv.setItem(13, ItemFactory.talZhneca());
            inv.setItem(14, ItemFactory.bozhyaKara(3));
            inv.setItem(15, ItemFactory.armorPiece("krush", rng.nextInt(4)));
            inv.setItem(21, ItemFactory.arrowExplosive(24));
            inv.setItem(22, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 4));
            inv.setItem(23, ItemFactory.gravSfera(3));
        } else {
            inv.setItem(4, rng.nextBoolean() ? ItemFactory.elytraSanta() : ItemFactory.sharSanty());
            inv.setItem(11, ItemFactory.snezokZamorozki(8));
            inv.setItem(12, ItemFactory.pautinnayaGranata(6));
            inv.setItem(13, rng.nextBoolean() ? ItemFactory.talStuzhi() : ItemFactory.talSevera());
            inv.setItem(14, ItemFactory.nyMandarin(16));
            inv.setItem(15, ItemFactory.tntCryo(4));
            inv.setItem(21, ItemFactory.bowIceRw());
            inv.setItem(22, ItemFactory.talFeniksa(3));
            inv.setItem(23, ItemFactory.armorPiece("frost", rng.nextInt(4)));
        }
    }

    private void castBossSkill(ActiveBossEvent ev, LivingEntity boss) {
        Location bl = boss.getLocation();
        World w = bl.getWorld();
        if (w == null) return;
        List<Player> nearby = new ArrayList<Player>();
        for (Entity e : w.getNearbyEntities(bl, 14, 10, 14)) {
            if (e instanceof Player) nearby.add((Player) e);
        }
        if (nearby.isEmpty()) return;

        String id = ev.template.id;
        if ("grinch".equals(id)) {
            w.spawnParticle(Particle.SNOWFLAKE, bl.clone().add(0, 1, 0), 60, 3.0, 1.5, 3.0, 0.05);
            for (Player p : nearby) {
                p.setFreezeTicks(140);
                ColorUtil.addPot(p, "slowness", "SLOW", 80, 1);
                ColorUtil.sound(p, "entity.player.hurt_freeze", 1.0f, 0.9f);
            }
        } else if ("krush".equals(id)) {
            w.spawnParticle(Particle.FLAME, bl.clone().add(0, 1, 0), 60, 3.0, 1.2, 3.0, 0.08);
            for (Player p : nearby) {
                p.setFireTicks(80);
                Vector kb = p.getLocation().toVector().subtract(bl.toVector()).normalize().multiply(0.9).setY(0.45);
                p.setVelocity(kb);
                ColorUtil.sound(p, "entity.blaze.shoot", 1.0f, 0.8f);
            }
        } else if ("titan".equals(id)) {
            for (Player p : nearby) {
                w.strikeLightningEffect(p.getLocation());
                p.damage(4.0, boss);
                p.setVelocity(new Vector(0, 0.75, 0));
            }
        } else if ("osiris".equals(id)) {
            for (Player p : nearby) {
                ColorUtil.addPot(p, "poison", "POISON", 100, 1);
                ColorUtil.addPot(p, "weakness", "WEAKNESS", 100, 0);
            }
        } else {
            w.spawnParticle(Particle.PORTAL, bl.clone().add(0, 1, 0), 70, 2.5, 1.5, 2.5, 0.2);
            for (Player p : nearby) {
                ColorUtil.addPot(p, "levitation", "LEVITATION", 35, 1);
            }
        }
    }

    public void recordBossDamage(LivingEntity boss, Player attacker, double dmg) {
        if (boss == null || attacker == null) return;
        ActiveBossEvent ev = findByBoss(boss);
        if (ev == null) return;
        Double prev = ev.damageMap.get(attacker.getName());
        ev.damageMap.put(attacker.getName(), Double.valueOf((prev != null ? prev.doubleValue() : 0.0) + dmg));
    }

    public boolean handleBossDeath(LivingEntity deadEntity, Player killer) {
        ActiveBossEvent ev = findByBoss(deadEntity);
        if (ev == null) return false;
        onBossSlain(ev, killer);
        return true;
    }

    public boolean isLockedEventChest(Block block) {
        if (block == null) return false;
        Location loc = block.getLocation();
        for (ActiveBossEvent ev : activeEvents.values()) {
            if (!ev.bossDefeated && ev.center.equals(loc)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Защищает стильные храмы/ротонды/пантеоны ивентов от грифа и фарма золотых блоков!
     * Исключение: алмазные/изумрудные/золочёные руды внутри Звёздного Метеорита можно честно добывать киркой!
     */
    public boolean isProtectedEventStructureBlock(Block block, boolean allowMeteorOres) {
        if (block == null) return false;
        Location loc = block.getLocation();
        for (ActiveBossEvent ev : activeEvents.values()) {
            if (ev.savedBlocks.containsKey(loc)) {
                if (allowMeteorOres && ev.template.kind == EventKind.METEOR && !ev.center.equals(loc)) {
                    Material m = block.getType();
                    if (m == Material.DEEPSLATE_DIAMOND_ORE || m == Material.DEEPSLATE_EMERALD_ORE || m == Material.GILDED_BLACKSTONE) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    private ActiveBossEvent findByBoss(LivingEntity ent) {
        for (ActiveBossEvent ev : activeEvents.values()) {
            if (ev.bossUuid != null && ev.bossUuid.equals(ent.getUniqueId())) return ev;
            if (ent.getScoreboardTags().contains("kg_boss_" + ev.instanceId)) return ev;
        }
        return null;
    }

    private void onBossSlain(ActiveBossEvent ev, Player killer) {
        if (ev.bossDefeated) return;
        ev.bossDefeated = true;
        ev.secondsLeft = 180;

        List<Map.Entry<String, Double>> top = new ArrayList<Map.Entry<String, Double>>(ev.damageMap.entrySet());
        Collections.sort(top, new Comparator<Map.Entry<String, Double>>() {
            @Override
            public int compare(Map.Entry<String, Double> a, Map.Entry<String, Double> b) {
                return Double.compare(b.getValue().doubleValue(), a.getValue().doubleValue());
            }
        });
        if (top.isEmpty() && killer != null) {
            top.add(new HashMap.SimpleEntry<String, Double>(killer.getName(), Double.valueOf(ev.template.maxHp)));
        }

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ColorUtil.c("&#00E676&l╔══════════════════════════════════════════╗"));
        Bukkit.broadcastMessage(ColorUtil.c("&#00E676&l║  🏆 &fБосс " + ev.template.displayName + " &#00E676&lПОВЕРЖЕН!"));
        for (int i = 0; i < Math.min(3, top.size()); i++) {
            String pName = top.get(i).getKey();
            double dmg = top.get(i).getValue().doubleValue();
            double mult = (i == 0) ? 1.0 : (i == 1 ? 0.6 : 0.35);
            double coins = Math.round(ev.template.coinReward * mult);
            int relics = Math.max(5, (int) Math.round(ev.template.relicReward * mult));
            int snow = (i == 0) ? 15 : 8;
            plugin.getStore().addCoins(pName, coins);
            plugin.getStore().addRelics(pName, relics);
            plugin.getStore().addSnowflakes(pName, snow);
            if (i == 0) {
                plugin.getStore().unlockTitle(pName, "boss_slayer");
                plugin.getStore().addCaseKeys(pName, "legendary", 1);
            }
            Bukkit.broadcastMessage(ColorUtil.c("&#00E676&l║  &#FFD740#" + (i + 1) + " &f" + pName
                    + " &7(" + (int) dmg + " урона) &8→ &a+" + ColorUtil.fmtMoney(coins) + "$ &b+" + relics + "❖"));
        }
        Bukkit.broadcastMessage(ColorUtil.c("&#00E676&l║  🔓 &fМифический Сундук открыт на 3 минуты!"));
        Bukkit.broadcastMessage(ColorUtil.c("&#00E676&l╚══════════════════════════════════════════╝"));
        Bukkit.broadcastMessage("");
        plugin.getStore().save();

        Block b = ev.center.getBlock();
        b.setType(Material.CHEST, false);
        if (b.getState() instanceof Chest) {
            Chest ch = (Chest) b.getState();
            populateBossChestLoot(ch.getBlockInventory(), ev.template.id);
        }
        spawnChestHologram(ev);
        ColorUtil.spawnFirework(ev.center);
    }

    private void populateBossChestLoot(Inventory inv, String id) {
        inv.setItem(4, "grinch".equals(id) ? ItemFactory.sharSanty()
                : "krush".equals(id) ? ItemFactory.talKrushitelya()
                : "titan".equals(id) ? ItemFactory.sharTitana()
                : "osiris".equals(id) ? ItemFactory.sharOsirisa()
                : ItemFactory.sharAndromedy());
        inv.setItem(11, ItemFactory.potionKiller(2));
        inv.setItem(12, ItemFactory.potionMedic(2));
        inv.setItem(13, rng.nextBoolean() ? ItemFactory.talFeniksa(3) : ItemFactory.pickSpawner());
        inv.setItem(14, ItemFactory.potionBurp(2));
        inv.setItem(15, ItemFactory.snowflakeCoin(16));
        inv.setItem(21, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 3));
        inv.setItem(22, rng.nextBoolean() ? ItemFactory.swordDracula() : ItemFactory.pickDjamshut());
        inv.setItem(23, new ItemStack(Material.NETHERITE_INGOT, 4));
    }

    public void endEvent(String instanceId, boolean announce) {
        ActiveBossEvent ev = activeEvents.remove(instanceId);
        if (ev == null) return;
        if (ev.bossBar != null) {
            ev.bossBar.removeAll();
            ev.bossBar.setVisible(false);
        }
        // Force-load surrounding chunks so Boss & Holograms ALWAYS die and get 100% removed when the event ends!
        World w = ev.center != null ? ev.center.getWorld() : null;
        if (w != null) {
            int cx = ev.center.getBlockX() >> 4;
            int cz = ev.center.getBlockZ() >> 4;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    try { w.getChunkAt(cx + dx, cz + dz).load(); } catch (Throwable ignored) {}
                }
            }
            for (Entity near : w.getNearbyEntities(ev.center, 45, 35, 45)) {
                if ((ev.bossUuid != null && ev.bossUuid.equals(near.getUniqueId()))
                        || near.getScoreboardTags().contains("kg_boss_" + ev.instanceId)) {
                    w.spawnParticle(Particle.POOF, near.getLocation().add(0, 1.0, 0), 35, 0.5, 0.8, 0.5, 0.05);
                    near.remove();
                }
            }
        }
        if (ev.bossUuid != null) {
            Entity e = Bukkit.getEntity(ev.bossUuid);
            if (e != null) {
                e.remove();
            }
        }
        removeHologram(ev);
        if (ev.center.getBlock().getState() instanceof Chest) {
            ((Chest) ev.center.getBlock().getState()).getBlockInventory().clear();
        }
        List<BlockState> states = new ArrayList<BlockState>(ev.savedBlocks.values());
        Collections.reverse(states);
        for (BlockState st : states) {
            try { st.update(true, false); } catch (Throwable ignored) {}
        }
        if (announce && !ev.bossDefeated) {
            Bukkit.broadcastMessage(ColorUtil.c("&#FF5252[Ивенты] &7Время ивента " + ev.template.displayName
                    + " &7вышло — Босс погиб и храм исчез!"));
        }
    }
}
