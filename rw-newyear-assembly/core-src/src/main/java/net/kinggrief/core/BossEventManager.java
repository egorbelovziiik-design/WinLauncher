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
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
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
        FROST_CITADEL
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
    }

    /**
     * Строгая проверка: призывать ивенты через меню / команды могут ТОЛЬКО Админы и выше (D.ADMIN+, Куратор, Тех.Админ, OWNER),
     * а НЕ обычные игроки/донатеры (даже если игрок тестирует ранг Игрок с включённым /op!).
     */
    public boolean canAdminSpawnEvents(Player p) {
        if (p == null) return false;
        RankManager.RankDef r = plugin.getRanks().getRank(p);
        return r.weight >= 200; // dadmin (200), dkp (210), opka (215), downer (220), kp (285), curator (290), techadmin (295), owner (300)
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                tickSecond();
            }
        }.runTaskTimer(plugin, 20L, 20L);
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

    public ActiveBossEvent startRandomEvent() {
        // Alternate richly between AirDrop, Meteor, Beacon, Frost Citadel, and Mythic Bosses!
        String[] pool = new String[]{"airdrop", "meteor", "beacon", "citadel", "grinch", "krush", "titan", "osiris", "andromeda"};
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
            buildBossAltar(ev);

            Location bossLoc = center.clone().add(0.5, 1.2, 2.5);
            LivingEntity boss = (LivingEntity) surv.spawnEntity(bossLoc, tpl.entityType);
            configureBossEntity(boss, tpl, ev.instanceId);
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
            Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l║  &#00E676&l💎 АКТИВИРОВАН ЗОЛОТОЙ МАЯК БОГАТСТВА!"));
            Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l║  &7Стойте рядом с Маяком — каждые 5 сек капают &a+3 000$ &7и &b+2❖&7!"));
        } else {
            Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l║  &#40C4FF&l❄ ВЫРОСЛА ЛЕДЯНАЯ ЦИТАДЕЛЬ САНТЫ!"));
            Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l║  &7Захватите ротонду — Ледяной Сундук откроется через &e" + ColorUtil.fmtTime(ev.unlockCountdownSec) + "&7!"));
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

    // ================= ARCHITECTURAL EVENT STRUCTURES =================
    private void clearAndSavePad(ActiveBossEvent ev, int radius, int height) {
        Location c = ev.center;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= height; dy++) {
                    Location l = c.clone().add(dx, dy, dz);
                    saveBlock(ev, l);
                    if (dy >= 0) l.getBlock().setType(Material.AIR, false);
                }
            }
        }
    }

    private void buildBossAltar(ActiveBossEvent ev) {
        clearAndSavePad(ev, 2, 4);
        Location c = ev.center;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean border = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                c.clone().add(dx, -1, dz).getBlock().setType(border ? Material.OBSIDIAN : ev.template.platformMat, false);
            }
        }
        int[][] corners = new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}};
        for (int[] cr : corners) {
            c.clone().add(cr[0], 0, cr[1]).getBlock().setType(Material.COBBLED_DEEPSLATE_WALL, false);
            c.clone().add(cr[0], 1, cr[1]).getBlock().setType(Material.SEA_LANTERN, false);
        }
        c.getBlock().setType(Material.ENDER_CHEST, false);
    }

    /**
     * 🪂 Красивейший Военный АирДроп на сухой земле с парашютным куполом сверху, сигнальными огнями и грузовыми опорами!
     */
    private void buildBeautifulAirdropStructure(ActiveBossEvent ev) {
        clearAndSavePad(ev, 3, 9);
        Location c = ev.center;
        // 5x5 Armored Landing Pad with yellow/black hazard trim & glowing beacons
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Material floor;
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                    floor = ((dx + dz) & 1) == 0 ? Material.YELLOW_CONCRETE : Material.BLACK_CONCRETE;
                } else if (dx == 0 || dz == 0) {
                    floor = Material.SEA_LANTERN;
                } else {
                    floor = Material.IRON_BLOCK;
                }
                c.clone().add(dx, -1, dz).getBlock().setType(floor, false);
            }
        }
        // Corner flare campfires + iron bar parachute rigging up to Y+5..Y+7 parachute canopy!
        int[][] corners = new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}};
        for (int[] cr : corners) {
            c.clone().add(cr[0], 0, cr[1]).getBlock().setType(Material.CAMPFIRE, false);
        }
        int[][] cords = new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
        for (int[] cd : cords) {
            for (int dy = 1; dy <= 5; dy++) {
                c.clone().add(cd[0], dy, cd[1]).getBlock().setType(Material.IRON_BARS, false);
            }
        }
        // Parachute Canopy Dome at dy = 6..7 (Red & White striped parachute!)
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int dist = Math.abs(dx) + Math.abs(dz);
                if (dist <= 3) {
                    int dy = dist <= 1 ? 7 : 6;
                    Material wool = (dx & 1) == 0 ? Material.RED_WOOL : Material.WHITE_WOOL;
                    c.clone().add(dx, dy, dz).getBlock().setType(wool, false);
                }
            }
        }
        // Supply Crate Core (Locked Red Shulker / EnderChest until timer hits 0)
        c.getBlock().setType(Material.ENDER_CHEST, false);
    }

    /**
     * ☄ Звёздный Метеорит: кратер из обсидиана, магмы, аметистов и настоящих алмазных руд!
     */
    private void buildStarMeteorStructure(ActiveBossEvent ev) {
        clearAndSavePad(ev, 3, 5);
        Location c = ev.center;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > 3.2) continue;
                Material m = d < 1.5 ? Material.CRYING_OBSIDIAN
                        : (rng.nextInt(3) == 0 ? Material.MAGMA_BLOCK : Material.OBSIDIAN);
                c.clone().add(dx, -1, dz).getBlock().setType(m, false);
                if (d >= 2.2 && d <= 3.1 && rng.nextBoolean()) {
                    c.clone().add(dx, 0, dz).getBlock().setType(rng.nextBoolean() ? Material.AMETHYST_BLOCK : Material.MAGMA_BLOCK, false);
                }
            }
        }
        // Bonus mineable ores around the meteor core!
        c.clone().add(1, 0, 0).getBlock().setType(Material.DEEPSLATE_DIAMOND_ORE, false);
        c.clone().add(-1, 0, 0).getBlock().setType(Material.DEEPSLATE_EMERALD_ORE, false);
        c.clone().add(0, 0, 1).getBlock().setType(Material.GOLD_BLOCK, false);
        c.clone().add(0, 0, -1).getBlock().setType(Material.AMETHYST_BLOCK, false);
        c.getBlock().setType(Material.ENDER_CHEST, false);
    }

    /**
     * 💎 Золотой Маяк Богатства: настоящая 3x3 золотая пирамида + активный Маяк под сундуком сокровищ!
     */
    private void buildGoldenBeaconStructure(ActiveBossEvent ev) {
        clearAndSavePad(ev, 3, 5);
        Location c = ev.center;
        // 3x3 Gold Pyramid base at y-2 and Beacon at y-1 so a real Minecraft Beacon beam shoots skyward!
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Location b2 = c.clone().add(dx, -2, dz);
                saveBlock(ev, b2);
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                    b2.getBlock().setType(Material.GOLD_BLOCK, false);
                }
                c.clone().add(dx, -1, dz).getBlock().setType(
                        (dx == 0 && dz == 0) ? Material.BEACON
                                : (Math.abs(dx) == 2 || Math.abs(dz) == 2 ? Material.EMERALD_BLOCK : Material.GOLD_BLOCK),
                        false);
            }
        }
        int[][] corners = new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}};
        for (int[] cr : corners) {
            c.clone().add(cr[0], 0, cr[1]).getBlock().setType(Material.QUARTZ_PILLAR, false);
            c.clone().add(cr[0], 1, cr[1]).getBlock().setType(Material.SEA_LANTERN, false);
        }
        // Stained glass while locked so Beacon beam shines right through to the sky!
        c.getBlock().setType(Material.YELLOW_STAINED_GLASS, false);
    }

    /**
     * ❄ Ледяная Цитадель Санты: кварцево-ледяная ротонда с морозным куполом!
     */
    private void buildFrostCitadelStructure(ActiveBossEvent ev) {
        clearAndSavePad(ev, 3, 6);
        Location c = ev.center;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Material fl = ((dx + dz) & 1) == 0 ? Material.BLUE_ICE : Material.SMOOTH_QUARTZ;
                c.clone().add(dx, -1, dz).getBlock().setType(fl, false);
            }
        }
        int[][] pillars = new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}};
        for (int[] pl : pillars) {
            for (int dy = 0; dy <= 2; dy++) {
                c.clone().add(pl[0], dy, pl[1]).getBlock().setType(Material.QUARTZ_PILLAR, false);
            }
            c.clone().add(pl[0], 3, pl[1]).getBlock().setType(Material.SEA_LANTERN, false);
        }
        c.getBlock().setType(Material.ENDER_CHEST, false);
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
        for (UUID id : ev.hologramStands) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        ev.hologramStands.clear();
    }

    private void configureBossEntity(LivingEntity boss, BossTemplate tpl, String instanceId) {
        boss.addScoreboardTag("kg_event_boss");
        boss.addScoreboardTag("kg_boss_" + instanceId);
        boss.setRemoveWhenFarAway(false);
        boss.setPersistent(true);
        setMaxHealthSafe(boss, tpl.maxHp);
        boss.setHealth(tpl.maxHp);
        boss.setCustomName(ColorUtil.c(tpl.displayName + " &c❤ " + (int) tpl.maxHp));
        boss.setCustomNameVisible(true);

        EntityEquipment eq = boss.getEquipment();
        if (eq != null) {
            if ("krush".equals(tpl.id)) {
                eq.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
                eq.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
                eq.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
                eq.setBoots(new ItemStack(Material.NETHERITE_BOOTS));
                eq.setItemInMainHand(ItemFactory.swordKrush());
            } else if ("grinch".equals(tpl.id)) {
                eq.setHelmet(new ItemStack(Material.DIAMOND_HELMET));
                eq.setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
                eq.setItemInMainHand(ItemFactory.bowIceRw());
            } else if ("osiris".equals(tpl.id)) {
                eq.setHelmet(new ItemStack(Material.GOLDEN_HELMET));
                eq.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
                eq.setItemInMainHand(ItemFactory.swordDracula());
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
                        curHp = le.getHealth();
                        le.setCustomName(ColorUtil.c(ev.template.displayName + " &c❤ " + (int) Math.ceil(curHp)));
                        if (le.getLocation().distanceSquared(ev.center) > 22 * 22) {
                            le.teleport(ev.center.clone().add(0.5, 1.2, 1.5));
                        }
                        if (ev.secondsLeft % 9 == 0) {
                            castBossSkill(ev, le);
                        }
                    } else if (ev.center.getChunk().isLoaded()) {
                        onBossSlain(ev, null);
                    } else {
                        curHp = ev.template.maxHp;
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
            inv.setItem(15, new ItemStack(Material.ENDER_PEARL, 16));
            inv.setItem(16, new ItemStack(Material.GOLDEN_CARROT, 64));
            inv.setItem(22, ItemFactory.potionKiller(2));
        } else if (kind == EventKind.METEOR) {
            inv.setItem(4, rng.nextBoolean() ? ItemFactory.sharAresa() : ItemFactory.elytraPhoenix());
            inv.setItem(11, new ItemStack(Material.NETHERITE_INGOT, 5));
            inv.setItem(12, ItemFactory.ognennySmerch(4));
            inv.setItem(13, ItemFactory.pickDjamshut());
            inv.setItem(14, new ItemStack(Material.DIAMOND_BLOCK, 8));
            inv.setItem(15, ItemFactory.talKrushitelya());
            inv.setItem(22, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 4));
        } else if (kind == EventKind.BEACON) {
            inv.setItem(4, ItemFactory.sharApollona());
            inv.setItem(11, new ItemStack(Material.GOLD_BLOCK, 24));
            inv.setItem(12, new ItemStack(Material.EMERALD_BLOCK, 16));
            inv.setItem(13, ItemFactory.pickSpawner());
            inv.setItem(14, ItemFactory.yavnayaPyl(4));
            inv.setItem(15, ItemFactory.snowflakeCoin(24));
            inv.setItem(22, ItemFactory.elytraThunder());
        } else {
            inv.setItem(4, rng.nextBoolean() ? ItemFactory.elytraSanta() : ItemFactory.sharSanty());
            inv.setItem(11, ItemFactory.snezokZamorozki(8));
            inv.setItem(12, ItemFactory.pautinnayaGranata(6));
            inv.setItem(13, ItemFactory.talStuzhi());
            inv.setItem(14, ItemFactory.nyMandarin(16));
            inv.setItem(15, ItemFactory.potionChampagne(2));
            inv.setItem(22, ItemFactory.talFeniksa(3));
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
        if (ev.bossUuid != null) {
            Entity e = Bukkit.getEntity(ev.bossUuid);
            if (e != null) e.remove();
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
            Bukkit.broadcastMessage(ColorUtil.c("&#FF5252[Ивенты] &7Время ивента " + ev.template.displayName + " &7истекло, постройка исчезла."));
        }
    }
}
