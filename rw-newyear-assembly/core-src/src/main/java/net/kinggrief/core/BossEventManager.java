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
import org.bukkit.command.CommandSender;
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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

public final class BossEventManager {

    public enum EventKind {
        BOSS,
        AIRDROP,
        AIRSHIP,
        METEOR,
        BEACON,
        FROST_CITADEL,
        VOLCANO,
        CRYSTAL,
        RUINS,
        CAMP
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
        // Last block data actually written by this event. A different current block
        // may belong to another plugin/player and must never be overwritten on cleanup.
        public final Map<Location, BlockData> expectedBlocks = new HashMap<Location, BlockData>();
        public final List<UUID> hologramStands = new ArrayList<UUID>();
        // Unique world-space scrap nodes. Parts are virtual and bound to this event,
        // so they cannot be copied, carried into another event or farmed repeatedly.
        public final java.util.Set<Location> airshipParts = new java.util.LinkedHashSet<Location>();
        // The event owns tickets for every chunk touched by its structure. This
        // keeps cleanup and chest unlocks from synchronously loading chunks.
        private final List<ChunkPos> chunkTickets = new ArrayList<ChunkPos>();
    }

    private static final int SITE_RADIUS = 9;
    private static final int MAX_LOADED_CANDIDATES = 160;
    private static final int MAX_GENERATED_CANDIDATES = 8;
    private static final int ASYNC_CHUNK_TIMEOUT_TICKS = 20 * 20;
    private static final int SEARCH_TIMEOUT_TICKS = 20 * 120;
    private static final long ASYNC_CHUNK_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(20);
    private static final long SEARCH_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(120);
    private static final long MEBIBYTE = 1024L * 1024L;

    private final KingGriefCore plugin;
    private final Random rng = new Random();
    private final Map<String, BossTemplate> templates = new LinkedHashMap<String, BossTemplate>();
    private final Map<String, ActiveBossEvent> activeEvents = new LinkedHashMap<String, ActiveBossEvent>();
    private int autoCountdownSec = 360;
    private int seq = 1;
    private SearchJob pendingSearch;
    private boolean shuttingDown;
    // Headroom inside the JVM heap only; it does not measure native memory or
    // the host/container RAM limit. Do not force a GC on the main thread.
    private LongSupplier availableHeapBytes = new LongSupplier() {
        @Override public long getAsLong() {
            Runtime runtime = Runtime.getRuntime();
            return Math.max(0L, runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory()));
        }
    };

    // Paper exposes asynchronous chunk loading on World. Reflection keeps the
    // compile-time API usable on Spigot; no synchronous fallback is permitted.
    interface AsyncChunkLoader {
        CompletableFuture<?> request(World world, int chunkX, int chunkZ) throws Exception;
    }
    private AsyncChunkLoader chunkLoader = new AsyncChunkLoader() {
        @Override public CompletableFuture<?> request(World world, int chunkX, int chunkZ) throws Exception {
            Method method = World.class.getMethod("getChunkAtAsync", int.class, int.class, boolean.class);
            Object future = method.invoke(world, chunkX, chunkZ, true);
            if (!(future instanceof CompletableFuture)) throw new IllegalStateException("Paper async chunk API unavailable");
            return (CompletableFuture<?>) future;
        }
    };

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
        templates.put("airship", new BossTemplate(
                "airship", EventKind.AIRSHIP, "&#80D8FF✈ Разбитый дирижабль", null, 300.0,
                BarColor.BLUE, BarStyle.SEGMENTED_10, Material.SPRUCE_PLANKS, 85000.0, 30));
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
        templates.put("crystal", new BossTemplate(
                "crystal", EventKind.CRYSTAL, "&#C4B5FD✧ Кристальный храм", null, 120.0,
                BarColor.PURPLE, BarStyle.SEGMENTED_10, Material.AMETHYST_BLOCK, 85000.0, 30));
        templates.put("ruins", new BossTemplate(
                "ruins", EventKind.RUINS, "&#A7F3D0⚒ Древние руины", null, 130.0,
                BarColor.GREEN, BarStyle.SEGMENTED_10, Material.MOSSY_STONE_BRICKS, 90000.0, 32));
        templates.put("camp", new BossTemplate(
                "camp", EventKind.CAMP, "&#FCD34D⛺ Лагерь кладоискателей", null, 100.0,
                BarColor.YELLOW, BarStyle.SEGMENTED_10, Material.SPRUCE_PLANKS, 70000.0, 24));
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
                } else if (en.getScoreboardTags().contains("kg_event_holo")) {
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
        shuttingDown = true;
        if (pendingSearch != null) pendingSearch.cancelSearch();
        List<String> ids = new ArrayList<String>(activeEvents.keySet());
        for (String id : ids) {
            endEvent(id, false);
        }
    }

    /** Restore event blocks before a plugin unloads their world. */
    public void onWorldUnload(World world) {
        if (world == null) return;
        if (pendingSearch != null && world.equals(pendingSearch.world))
            pendingSearch.finish(false, "Мир ивента выгружается; поиск отменён.");
        List<String> ids = new ArrayList<String>();
        for (ActiveBossEvent event : activeEvents.values()) {
            if (world.equals(event.center.getWorld())) ids.add(event.instanceId);
        }
        for (String id : ids) endEvent(id, false);
    }

    public List<ActiveBossEvent> getActiveEvents() {
        return new ArrayList<ActiveBossEvent>(activeEvents.values());
    }

    public int getNextAutoSeconds() {
        return autoCountdownSec;
    }

    public boolean isAutoEnabled() {
        return plugin.getConfig().getBoolean("events.auto-enabled", true);
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

    /** Queue the automatic event search. Search never waits for chunk generation on the tick thread. */
    public boolean requestRandomEvent(CommandSender sender) {
        String[] pool = new String[]{"airdrop", "airship", "meteor", "beacon", "citadel", "volcano", "crystal", "ruins", "camp", "grinch", "krush", "titan", "osiris", "andromeda"};
        List<String> unused = new ArrayList<String>();
        for (String pid : pool) {
            boolean used = false;
            for (ActiveBossEvent ev : activeEvents.values()) {
                if (ev.template.id.equals(pid)) { used = true; break; }
            }
            if (!used) unused.add(pid);
        }
        String pick = unused.isEmpty() ? pool[rng.nextInt(pool.length)] : unused.get(rng.nextInt(unused.size()));
        return requestBossEvent(pick, sender);
    }

    /** Legacy APIs now queue the search and cannot return an event synchronously. */
    @Deprecated
    public ActiveBossEvent startRandomEvent() {
        requestRandomEvent(null);
        return null;
    }

    @Deprecated
    public ActiveBossEvent startRandomBossOnly() {
        requestBossEvent("boss", null);
        return null;
    }

    public boolean requestBossEvent(String templateId, CommandSender sender) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Event requests must be made on the server thread");
        String key = normalizeTemplateId(templateId);
        BossTemplate tpl = templates.get(key);
        if (tpl == null) {
            notifyRequester(sender, "&cНеизвестный ивент: &f" + key);
            return false;
        }
        if (shuttingDown || pendingSearch != null) {
            notifyRequester(sender, "&eПоиск места для другого ивента уже выполняется. Попробуйте позже.");
            return false;
        }
        World world = null;
        for (World candidate : Bukkit.getWorlds()) {
            if (candidate.getEnvironment() == World.Environment.NORMAL
                    && !VoidSpawnWorldGenerator.SPAWN_WORLD_NAME.equalsIgnoreCase(candidate.getName())) {
                world = candidate;
                break;
            }
        }
        // Do not create a missing survival world from an event tick.
        return queueSearch(tpl, world, sender);
    }

    private String normalizeTemplateId(String templateId) {
        String key = templateId == null ? "" : templateId.toLowerCase(Locale.ROOT);
        if ("boss".equals(key)) {
            String[] bosses = new String[]{"grinch", "krush", "titan", "osiris", "andromeda"};
            return bosses[rng.nextInt(bosses.length)];
        }
        return key;
    }

    private boolean queueSearch(BossTemplate tpl, World world, CommandSender sender) {
        if (world == null || shuttingDown || pendingSearch != null) {
            notifyRequester(sender, "&cНе удалось начать поиск места для ивента.");
            return false;
        }
        SearchJob job = new SearchJob(tpl, world, sender);
        pendingSearch = job;
        job.runTaskTimer(plugin, 1L, 1L);
        notifyRequester(sender, "&eПоиск безопасного места для ивента запущен. Сервер продолжает работать.");
        return true;
    }

    private void notifyRequester(CommandSender sender, String message) {
        if (sender != null) sender.sendMessage(ColorUtil.c(message));
    }

    /**
     * Compatibility entry point: a null center queues a non-blocking search and
     * returns null. A forced center is accepted only if its whole footprint is
     * already loaded and safe. Never call getBlock() before the loaded check.
     */
    public ActiveBossEvent spawnBossEvent(String templateId, Location forcedCenter) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Events must be created on the server thread");
        if (forcedCenter == null) {
            requestBossEvent(templateId, null);
            return null;
        }
        BossTemplate tpl = templates.get(normalizeTemplateId(templateId));
        World world = forcedCenter.getWorld();
        if (tpl == null || world == null || shuttingDown) return null;
        Location center = new Location(world, forcedCenter.getBlockX(), forcedCenter.getBlockY(), forcedCenter.getBlockZ());
        if (!isSafeEventSite(center)) return null;
        List<ChunkPos> tickets = new ArrayList<ChunkPos>();
        try {
            if (!pinAlreadyLoadedChunks(world, footprint(center.getBlockX(), center.getBlockZ()), tickets)) {
                releaseTickets(world, tickets);
                return null;
            }
            ActiveBossEvent event = createEvent(tpl, center, tickets);
            if (event == null) releaseTickets(world, tickets);
            return event;
        } catch (Exception error) {
            releaseTickets(world, tickets);
            plugin.getLogger().warning("[Events] Forced event could not reserve its chunks: " + error.getMessage());
            return null;
        }
    }

    private ActiveBossEvent createEvent(BossTemplate tpl, Location center, List<ChunkPos> tickets) {
        World surv = center.getWorld();
        if (surv == null || !allSiteChunksLoaded(surv, center.getBlockX(), center.getBlockZ())) return null;
        if (activeEvents.size() >= 4) {
            String oldest = activeEvents.keySet().iterator().next();
            endEvent(oldest, false);
        }

        ActiveBossEvent ev = new ActiveBossEvent();
        ev.instanceId = tpl.id + "_" + (seq++);
        ev.template = tpl;
        ev.center = center;
        ev.chunkTickets.addAll(tickets);
        try {
            if (tpl.kind == EventKind.BOSS) {
                ev.secondsLeft = 900;
                ev.unlockCountdownSec = 0;
                ev.lastKnownHp = tpl.maxHp;
                buildBossAltar(ev);
                Location bossLoc = center.clone().add(0.5, 1.2, 2.5);
                LivingEntity boss = (LivingEntity) surv.spawnEntity(bossLoc, tpl.entityType);
                ev.bossUuid = boss.getUniqueId();
                configureBossEntity(boss, tpl, ev.instanceId, tpl.maxHp);
            } else {
                ev.unlockTotalSec = (int) tpl.maxHp;
                ev.unlockCountdownSec = ev.unlockTotalSec;
                ev.secondsLeft = ev.unlockTotalSec + 180;
                if (tpl.kind == EventKind.AIRDROP) buildBeautifulAirdropStructure(ev);
                else if (tpl.kind == EventKind.AIRSHIP) buildCrashedAirship(ev);
                else if (tpl.kind == EventKind.METEOR) buildStarMeteorStructure(ev);
                else if (tpl.kind == EventKind.BEACON) buildGoldenBeaconStructure(ev);
                else if (tpl.kind == EventKind.VOLCANO) buildVolcanoStructure(ev);
                else if (tpl.kind == EventKind.CRYSTAL || tpl.kind == EventKind.RUINS || tpl.kind == EventKind.CAMP) buildNewWorldEventStructure(ev);
                else buildFrostCitadelStructure(ev);
            }
            spawnChestHologram(ev);
            BossBar bar = Bukkit.createBossBar(formatBarTitle(ev, tpl.maxHp), tpl.barColor, tpl.barStyle);
            ev.bossBar = bar;
            bar.setProgress(1.0);
            bar.setVisible(true);
            for (Player on : Bukkit.getOnlinePlayers()) bar.addPlayer(on);
            activeEvents.put(ev.instanceId, ev);
            announceEventSpawn(ev);
            return ev;
        } catch (Exception error) {
            // A failed build must not leave blocks or tickets behind.
            plugin.getLogger().severe("[Events] Event construction failed: " + error.getMessage());
            activeEvents.remove(ev.instanceId);
            if (ev.bossBar != null) ev.bossBar.removeAll();
            if (ev.bossUuid != null) {
                Entity boss = Bukkit.getEntity(ev.bossUuid);
                if (boss != null) boss.remove();
            }
            removeHologram(ev);
            restoreEventBlocks(ev);
            return null;
        }
    }

    private static final class ChunkPos {
        final int x, z;
        ChunkPos(int x, int z) { this.x = x; this.z = z; }
    }

    private List<ChunkPos> footprint(int x, int z) {
        List<ChunkPos> chunks = new ArrayList<ChunkPos>();
        for (int cx = (x - SITE_RADIUS) >> 4; cx <= (x + SITE_RADIUS) >> 4; cx++) {
            for (int cz = (z - SITE_RADIUS) >> 4; cz <= (z + SITE_RADIUS) >> 4; cz++) {
                chunks.add(new ChunkPos(cx, cz));
            }
        }
        return chunks;
    }

    private boolean allSiteChunksLoaded(World world, int x, int z) {
        for (ChunkPos chunk : footprint(x, z)) {
            if (!world.isChunkLoaded(chunk.x, chunk.z)) return false;
        }
        return true;
    }

    private boolean pinAlreadyLoadedChunks(World world, List<ChunkPos> positions, List<ChunkPos> pinned) {
        for (ChunkPos pos : positions) {
            if (!pinLoadedChunk(world, pos, pinned)) return false;
        }
        return true;
    }

    private boolean pinLoadedChunk(World world, ChunkPos pos, List<ChunkPos> pinned) {
        // addPluginChunkTicket itself loads a chunk, so it is crucial that this
        // guard and the ticket operation both run on the main thread, together.
        if (!world.isChunkLoaded(pos.x, pos.z)) return false;
        if (!world.addPluginChunkTicket(pos.x, pos.z, plugin)) return false;
        pinned.add(pos);
        return true;
    }

    private void releaseTickets(World world, List<ChunkPos> tickets) {
        for (ChunkPos pos : tickets) {
            try { world.removePluginChunkTicket(pos.x, pos.z, plugin); }
            catch (Exception error) { plugin.getLogger().warning("[Events] Could not release a chunk ticket: " + error.getMessage()); }
        }
        tickets.clear();
    }

    private boolean candidateAllowed(World world, int x, int z) {
        if ((Math.abs(x) < 180 && Math.abs(z) < 180) || Math.abs(x) > 2000 || Math.abs(z) > 2000) return false;
        if (plugin.getStore().overlapsAnyClaim(world.getName(), x, z, 16)) return false;
        for (ActiveBossEvent other : activeEvents.values()) {
            if (world.equals(other.center.getWorld())) {
                double dx = (double) other.center.getBlockX() - x, dz = (double) other.center.getBlockZ() - z;
                if (dx * dx + dz * dz < 380.0 * 380.0) return false;
            }
        }
        return world.getWorldBorder().isInside(new Location(world, x - SITE_RADIUS, 70, z - SITE_RADIUS))
                && world.getWorldBorder().isInside(new Location(world, x + SITE_RADIUS, 70, z + SITE_RADIUS));
    }

    private final class SearchJob extends BukkitRunnable {
        final BossTemplate template;
        final World world;
        final CommandSender sender;
        final List<int[]> loadedCandidates = new ArrayList<int[]>();
        final List<ChunkPos> pinned = new ArrayList<ChunkPos>();
        int loadedIndex, generatedCandidates, elapsedTicks, waitingSince;
        int[] candidate;
        List<ChunkPos> required;
        ChunkPos awaited;
        boolean allowAsync, waiting, finished;
        final long startedNanos = System.nanoTime();
        long waitingStartedNanos;
        CompletableFuture<?> inFlight;

        SearchJob(BossTemplate template, World world, CommandSender sender) {
            this.template = template;
            this.world = world;
            this.sender = sender;
            List<org.bukkit.Chunk> chunks = Arrays.asList(world.getLoadedChunks());
            Collections.shuffle(chunks, rng);
            for (org.bukkit.Chunk chunk : chunks) {
                if (loadedCandidates.size() >= MAX_LOADED_CANDIDATES) break;
                // Offset 6 or 9 makes the 19x19 footprint intersect only four
                // chunks instead of nine. All are checked before any block read.
                int x = chunk.getX() * 16 + (rng.nextBoolean() ? 6 : 9);
                int z = chunk.getZ() * 16 + (rng.nextBoolean() ? 6 : 9);
                if (Math.abs(x) >= 180 || Math.abs(z) >= 180) loadedCandidates.add(new int[]{x, z});
            }
        }

        @Override public void run() {
            try {
                tickSearch();
            } catch (Exception error) {
                plugin.getLogger().warning("[Events] Event search failed: " + error.getMessage());
                finish(false, "Ошибка поиска места ивента; сервер продолжает работу.");
            }
        }

        private void tickSearch() {
            if (finished || shuttingDown || pendingSearch != this) { cancelSearch(); return; }
            elapsedTicks++;
            if (elapsedTicks >= SEARCH_TIMEOUT_TICKS || System.nanoTime() - startedNanos >= SEARCH_TIMEOUT_NANOS) {
                finish(false, "Поиск ивента прерван по тайм-ауту.");
                return;
            }
            if (waiting) {
                // Wall-clock deadlines still expire when TPS is low. They are
                // checked between ticks and never interrupt or block the tick.
                if (elapsedTicks - waitingSince >= ASYNC_CHUNK_TIMEOUT_TICKS
                        || System.nanoTime() - waitingStartedNanos >= ASYNC_CHUNK_TIMEOUT_NANOS)
                    finish(false, "Чанк не загрузился вовремя; поиск ивента отменён.");
                return;
            }
            if (candidate == null) {
                if (!selectCandidate()) return;
                required = footprint(candidate[0], candidate[1]);
            }
            if (pinned.size() < required.size()) {
                ChunkPos next = required.get(pinned.size());
                if (world.isChunkLoaded(next.x, next.z)) {
                    if (!pinLoadedChunk(world, next, pinned)) abandonCandidate();
                } else if (allowAsync) {
                    if (mayRequestNewChunk()) requestChunk(next);
                } else {
                    abandonCandidate();
                }
                return; // At most one chunk/ticket operation per tick.
            }
            Location center = inspectDryColumn(world, candidate[0], candidate[1]);
            if (center != null && isSafeEventSite(center)) {
                ActiveBossEvent event = createEvent(template, center, pinned);
                if (event != null) {
                    pinned.clear(); // ownership transferred to the active event
                    finish(true, null);
                    return;
                }
                finish(false, "Не удалось построить ивент; изменения отменены.");
                return;
            }
            abandonCandidate();
        }

        private boolean selectCandidate() {
            while (loadedIndex < loadedCandidates.size()) {
                int[] next = loadedCandidates.get(loadedIndex++);
                if (candidateAllowed(world, next[0], next[1])) {
                    candidate = next;
                    allowAsync = false;
                    return true;
                }
            }
            if (generatedCandidates >= MAX_GENERATED_CANDIDATES) {
                finish(false, "Не найдено безопасное место для ивента вдали от построек и приватов.");
                return false;
            }
            // Prefer already-loaded locations. An administrator may forbid new
            // generation, and low heap headroom also stops this fallback.
            if (!mayRequestNewChunk()) return false;
            // Small bounded fallback: only ONE chunk request can be in flight,
            // and no more than eight new sites are considered per search.
            for (int i = 0; i < 64; i++) {
                int x = (rng.nextInt(150) - 75) * 16 + (rng.nextBoolean() ? 6 : 9);
                int z = (rng.nextInt(150) - 75) * 16 + (rng.nextBoolean() ? 6 : 9);
                if (candidateAllowed(world, x, z)) {
                    generatedCandidates++;
                    candidate = new int[]{x, z};
                    allowAsync = true;
                    return true;
                }
            }
            finish(false, "Нет доступных координат ивента внутри границы мира.");
            return false;
        }

        private boolean mayRequestNewChunk() {
            if (!plugin.getConfig().getBoolean("events.allow-chunk-generation", true)) {
                finish(false, "Генерация новых чанков для ивентов отключена; подходящее загруженное место не найдено.", 300);
                return false;
            }
            int minMb = Math.max(0, Math.min(4096, plugin.getConfig().getInt("events.min-free-heap-mb", 256)));
            if (availableHeapBytes.getAsLong() < minMb * MEBIBYTE) {
                finish(false, "Мало свободного места в Java heap для поиска ивента (нужно " + minMb + " МиБ); генерация отложена.", 300);
                return false;
            }
            return true;
        }

        private void requestChunk(final ChunkPos pos) {
            waiting = true;
            waitingSince = elapsedTicks;
            waitingStartedNanos = System.nanoTime();
            awaited = pos;
            final CompletableFuture<?> future;
            try {
                future = chunkLoader.request(world, pos.x, pos.z);
                if (future == null) throw new IllegalStateException("Paper async chunk API returned no future");
                inFlight = future;
            } catch (Exception error) {
                waiting = false;
                plugin.getLogger().warning("[Events] Paper async chunk API unavailable: " + error.getClass().getSimpleName());
                finish(false, "Асинхронная загрузка чанков недоступна. Используйте Paper 1.21.4.");
                return;
            }
            future.whenComplete((chunk, error) -> {
                Runnable completion = new Runnable() {
                    @Override public void run() { chunkReady(pos, chunk, error); }
                };
                if (Bukkit.isPrimaryThread()) completion.run();
                else if (plugin.isEnabled()) {
                    try { Bukkit.getScheduler().runTask(plugin, completion); }
                    catch (IllegalStateException ignored) { /* plugin stopped before completion */ }
                }
            });
        }

        private void chunkReady(ChunkPos pos, Object chunk, Throwable error) {
            if (finished || shuttingDown || pendingSearch != this || !waiting || awaited != pos) return;
            inFlight = null;
            waiting = false;
            awaited = null;
            try {
                if (error != null || chunk == null || !world.isChunkLoaded(pos.x, pos.z)
                        || !pinLoadedChunk(world, pos, pinned)) {
                    abandonCandidate();
                }
            } catch (Exception failure) {
                plugin.getLogger().warning("[Events] Could not reserve a loaded chunk: " + failure.getMessage());
                finish(false, "Не удалось зарезервировать чанки ивента.");
            }
        }

        private void abandonCandidate() {
            releaseTickets(world, pinned);
            candidate = null;
            required = null;
            waiting = false;
            awaited = null;
        }

        void cancelSearch() {
            if (finished) return;
            finished = true; // callbacks, including cancellation, must not re-enter the search
            CompletableFuture<?> future = inFlight;
            inFlight = null;
            if (future != null && !future.isDone()) {
                // Best effort only: Paper may continue work already scheduled.
                try { future.cancel(false); }
                catch (RuntimeException error) { plugin.getLogger().warning("[Events] Could not cancel chunk future: " + error.getMessage()); }
            }
            releaseTickets(world, pinned);
            if (pendingSearch == this) pendingSearch = null;
            cancel();
        }

        private void finish(boolean success, String reason) {
            finish(success, reason, 60);
        }

        private void finish(boolean success, String reason, int autoRetrySeconds) {
            if (finished) return;
            if (!success) {
                plugin.getLogger().warning("[Events] " + reason);
                notifyRequester(sender, "&c" + reason);
                if (sender == null) autoCountdownSec = Math.min(autoCountdownSec, autoRetrySeconds);
            }
            cancelSearch();
        }
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
        } else if (ev.template.kind == EventKind.AIRSHIP) {
            Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l║  &#80D8FF&l✈ РАЗБИЛСЯ ЗИМНИЙ ДИРИЖАБЛЬ!"));
            Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l║  &7Найдите три медные детали среди обломков и нажмите ПКМ. Время: &e" + ColorUtil.fmtTime(ev.unlockCountdownSec) + "&7!"));
        } else if (ev.template.kind == EventKind.METEOR) {
            Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l║  &#FF5252&l☄ УПАЛ ЗВЁЗДНЫЙ МЕТЕОРИТ С РУДАМИ!"));
            Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l║  &7Добывайте алмазы/незерит в кратере! Ядро откроется через &e" + ColorUtil.fmtTime(ev.unlockCountdownSec) + "&7!"));
        } else if (ev.template.kind == EventKind.BEACON) {
            Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l║  &#00E676&l💎 АКТИВИРОВАН ХРАМ МАЯКА БОГАТСТВА!"));
            Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l║  &7Стойте в Пантеоне — каждые 5 сек капают &a+3 000$ &7и &b+2❖&7!"));
        } else if (ev.template.kind == EventKind.VOLCANO) {
            Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l║  &#FF6D00&l🌋 НАЧАЛОСЬ ИЗВЕРЖЕНИЕ АДСКОГО ВУЛКАНА!"));
            Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l║  &7Кальдера с Кастомными ТНТ и 3D Луками откроется через &e" + ColorUtil.fmtTime(ev.unlockCountdownSec) + "&7!"));
        } else if (ev.template.kind == EventKind.CRYSTAL || ev.template.kind == EventKind.RUINS || ev.template.kind == EventKind.CAMP) {
            Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l║  &fНОВОЕ СОБЫТИЕ: " + ev.template.displayName));
            Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l║  &7Сокровищница откроется через &e" + ColorUtil.fmtTime(ev.unlockCountdownSec) + "&7!"));
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

    private Location inspectDryColumn(World surv, int x, int z) {
        // Includes the 7x7 dry-land sample and the larger 19x19 structure.
        // getHighestBlockYAt/getBlockAt can otherwise generate a neighbour.
        if (!allSiteChunksLoaded(surv, x, z)) return null;
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
        int groundY = Integer.MIN_VALUE;
        for (int y = topY; y >= surv.getMinHeight() + 4; y--) {
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
        if (groundY < surv.getMinHeight() + 4 || groundY + 14 >= surv.getMaxHeight()) return null;

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

    private boolean isSafeEventSite(Location center) {
        if (center == null || center.getWorld() == null || plugin.getSpawnEngine().isInSpawnWorld(center)) return false;
        World w = center.getWorld();
        int x = center.getBlockX(), y = center.getBlockY(), z = center.getBlockZ();
        if (!allSiteChunksLoaded(w, x, z)) return false;
        if (y - 4 < w.getMinHeight() || y + 13 >= w.getMaxHeight()) return false;
        if (plugin.getStore().overlapsAnyClaim(w.getName(), x, z, 16)) return false;
        if (intersectsWorldGuardRegion(w, x, y, z)) return false;
        for (ActiveBossEvent other : activeEvents.values()) {
            if (w.equals(other.center.getWorld())) {
                double dx = (double) other.center.getBlockX() - x, dz = (double) other.center.getBlockZ() - z;
                if (dx * dx + dz * dz < 40.0 * 40.0) return false;
            }
        }
        for (int dx = -9; dx <= 9; dx++) for (int dz = -9; dz <= 9; dz++) {
            if (!w.getWorldBorder().isInside(new Location(w, x + dx, y, z + dz))) return false;
            for (int dy = -4; dy <= 13; dy++) {
                Block block = w.getBlockAt(x + dx, y + dy, z + dz);
                Material m = block.getType();
                if (!isNaturalEventMaterial(m)) return false;
                // Never clear trees, logs, stone walls or other possible structures
                // above ground even if their materials can also occur naturally.
                if (dy >= 0 && !isReplaceableEventSurface(m)) return false;
                if (dy == -1 && (m == Material.STONE || m == Material.DEEPSLATE || m == Material.TUFF
                        || m == Material.ANDESITE || m == Material.DIORITE || m == Material.GRANITE
                        || m == Material.SANDSTONE || m == Material.RED_SANDSTONE || m.name().endsWith("_ORE"))) return false;
                if (m != Material.AIR && m != Material.CAVE_AIR && m != Material.VOID_AIR
                        && block.getState() instanceof org.bukkit.block.TileState) return false;
            }
        }
        return true;
    }

    private boolean warnedWorldGuard = false;

    /** Conservatively reject a site whose construction volume intersects any WG region.
     * If WorldGuard is present but its region data/API is unavailable, fail closed. */
    private boolean intersectsWorldGuardRegion(World world, int x, int y, int z) {
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) return false;
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) return true;
        try {
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object wg = wgClass.getMethod("getInstance").invoke(null);
            Object platform = wgClass.getMethod("getPlatform").invoke(wg);
            Class<?> platformType = Class.forName("com.sk89q.worldguard.internal.platform.WorldGuardPlatform");
            Object container = platformType.getMethod("getRegionContainer").invoke(platform);
            Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object adapted = adapter.getMethod("adapt", World.class).invoke(null, world);
            Class<?> worldType = Class.forName("com.sk89q.worldedit.world.World");
            Class<?> containerType = Class.forName("com.sk89q.worldguard.protection.regions.RegionContainer");
            Object manager = containerType.getMethod("get", worldType).invoke(container, adapted);
            if (manager == null) throw new IllegalStateException("region manager unavailable");
            Class<?> managerType = Class.forName("com.sk89q.worldguard.protection.managers.RegionManager");
            Object regions = managerType.getMethod("getRegions").invoke(manager);
            if (!(regions instanceof java.util.Map)) throw new IllegalStateException("region data unavailable");
            Class<?> regionType = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion");
            Class<?> vectorType = Class.forName("com.sk89q.worldedit.math.BlockVector3");
            java.lang.reflect.Method bx = vectorType.getMethod("getBlockX"), by = vectorType.getMethod("getBlockY"), bz = vectorType.getMethod("getBlockZ");
            for (Object region : ((java.util.Map<?, ?>) regions).values()) {
                Object min = regionType.getMethod("getMinimumPoint").invoke(region);
                Object max = regionType.getMethod("getMaximumPoint").invoke(region);
                int minX = ((Number) bx.invoke(min)).intValue(), minY = ((Number) by.invoke(min)).intValue(), minZ = ((Number) bz.invoke(min)).intValue();
                int maxX = ((Number) bx.invoke(max)).intValue(), maxY = ((Number) by.invoke(max)).intValue(), maxZ = ((Number) bz.invoke(max)).intValue();
                if (x + 9 >= minX && x - 9 <= maxX && y + 13 >= minY && y - 4 <= maxY && z + 9 >= minZ && z - 9 <= maxZ) return true;
            }
            return false;
        } catch (Throwable error) {
            if (!warnedWorldGuard) {
                warnedWorldGuard = true;
                plugin.getLogger().warning("[Events] WorldGuard regions could not be checked; refusing event placement: " + error.getClass().getSimpleName());
            }
            return true;
        }
    }

    private boolean isReplaceableEventSurface(Material m) {
        String n = m.name();
        return m == Material.AIR || m == Material.CAVE_AIR || m == Material.VOID_AIR || m == Material.SNOW
                || n.endsWith("_SAPLING") || n.endsWith("_GRASS") || n.endsWith("_FERN")
                || n.endsWith("_FLOWER") || n.endsWith("_TULIP") || n.equals("DANDELION")
                || n.equals("POPPY") || n.equals("VINE") || n.equals("DEAD_BUSH");
    }

    private boolean isNaturalEventMaterial(Material m) {
        String n = m.name();
        return m == Material.AIR || m == Material.CAVE_AIR || m == Material.VOID_AIR
                || m == Material.STONE || m == Material.DIRT || m == Material.GRASS_BLOCK
                || m == Material.COARSE_DIRT || m == Material.ROOTED_DIRT || m == Material.PODZOL
                || m == Material.GRAVEL || m == Material.SAND || m == Material.RED_SAND
                || m == Material.SANDSTONE || m == Material.RED_SANDSTONE || m == Material.CLAY
                || m == Material.TUFF || m == Material.DEEPSLATE || m == Material.ANDESITE
                || m == Material.DIORITE || m == Material.GRANITE || m == Material.SNOW || m == Material.SNOW_BLOCK
                || n.endsWith("_ORE") || n.endsWith("_LOG") || n.endsWith("_LEAVES")
                || n.endsWith("_SAPLING") || n.endsWith("_GRASS") || n.endsWith("_FERN")
                || n.endsWith("_FLOWER") || n.endsWith("_TULIP") || n.equals("DANDELION")
                || n.equals("POPPY") || n.equals("VINE") || n.equals("DEAD_BUSH");
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
        rememberEventBlock(ev, l.getBlock());
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
        rememberEventBlock(ev, b);
    }

    private void clearAndSavePad(ActiveBossEvent ev, int radius, int height) {
        Location c = ev.center;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > (radius + 1) * (radius + 1)) continue;
                for (int dy = -3; dy <= height; dy++) {
                    Location l = c.clone().add(dx, dy, dz);
                    saveBlock(ev, l);
                    if (dy >= 0) {
                        l.getBlock().setType(Material.AIR, false);
                        rememberEventBlock(ev, l.getBlock());
                    }
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

    /** Compact wreck inside the already checked 19x19 footprint. Only changed blocks are saved. */
    private void buildCrashedAirship(ActiveBossEvent ev) {
        for (int x = -6; x <= 6; x++) for (int z = -3; z <= 3; z++) {
            int width = Math.abs(x) >= 5 ? 2 : 3;
            if (Math.abs(z) > width) continue;
            fillGroundedFloor(ev, x, z, Math.abs(z) == width ? Material.DARK_OAK_PLANKS : Material.SPRUCE_PLANKS,
                    Material.SPRUCE_PLANKS);
            if (Math.abs(z) == width && !(x == 0 && z < 0)) {
                setBlk(ev, x, 0, z, Material.STRIPPED_SPRUCE_LOG);
                if (Math.abs(x) <= 4 && x % 2 == 0) setBlk(ev, x, 1, z, Material.SPRUCE_FENCE);
            }
        }
        // A damaged envelope, connected to the low mast by chains. Leave the
        // approach and the chest hologram clear; no hanging entities or mobs.
        for (int y = 6; y <= 11; y++) for (int x = -4; x <= 4; x++) for (int z = -3; z <= 3; z++) {
            double d = x*x/16.0 + (y-8.5)*(y-8.5)/6.25 + z*z/9.0;
            if (d < 0.58 || d > 1.04 || (x >= 1 && y <= 7 && z >= 0)) continue;
            setBlk(ev, x, y, z, ((x + y) & 1) == 0 ? Material.LIGHT_BLUE_WOOL : Material.WHITE_WOOL);
        }
        for (int y = 1; y <= 7; y++) setBlk(ev, -3, y, 2, Material.STRIPPED_SPRUCE_LOG);
        for (int y = 3; y <= 6; y++) setBlk(ev, 3, y, -2, Material.CHAIN);
        setBlk(ev, -5, 1, -2, Material.LANTERN);
        setBlk(ev, 5, 1, 2, Material.SOUL_LANTERN);
        // Three distinct pieces among the wreckage. Ground the markers on uneven
        // terrain and remember every modified block for the usual safe restoration.
        int[][] parts = new int[][]{{-7, -3}, {7, 3}, {6, -4}};
        for (int[] part : parts) {
            fillGroundedFloor(ev, part[0], part[1], Material.SPRUCE_PLANKS, Material.SPRUCE_PLANKS);
            setBlk(ev, part[0], 0, part[1], Material.COPPER_BLOCK);
            ev.airshipParts.add(ev.center.clone().add(part[0], 0, part[1]).getBlock().getLocation());
        }
        setBlk(ev, 0, 0, 0, Material.ENDER_CHEST);
    }

    /** Collect one event-bound component, after other plugins' protection checks. */
    public boolean collectAirshipPart(Player player, Block block) {
        if (player == null || block == null || !plugin.getStore().isAuthenticated(player)) return false;
        for (ActiveBossEvent ev : activeEvents.values()) {
            Location loc = block.getLocation();
            if (ev.template.kind != EventKind.AIRSHIP || ev.bossDefeated || !ev.airshipParts.contains(loc)) continue;
            if (!eventStillOwnsBlock(ev, block)) {
                player.sendMessage(ColorUtil.c("&cДеталь изменена извне. Ивент завершится без замены чужого блока."));
                ev.secondsLeft = 0;
                return true;
            }
            ev.airshipParts.remove(loc);
            setBlk(ev, loc.getBlockX() - ev.center.getBlockX(), loc.getBlockY() - ev.center.getBlockY(),
                    loc.getBlockZ() - ev.center.getBlockZ(), Material.AIR);
            int collected = 3 - ev.airshipParts.size();
            Bukkit.broadcastMessage(ColorUtil.c("&#80D8FF✈ " + player.getName() + " нашёл деталь дирижабля: &f" + collected + "/3"));
            ColorUtil.sound(player, "entity.experience_orb.pickup", 1f, 1.3f);
            if (ev.airshipParts.isEmpty() && isCenterChunkLoaded(ev)) unlockTimedWorldEvent(ev);
            else updateCountdownHoloLine(ev);
            return true;
        }
        return false;
    }

    /** Three timed world events, each built from saved blocks and restored on end/shutdown. */
    private void buildNewWorldEventStructure(ActiveBossEvent ev) {
        clearAndSavePad(ev, 7, 10);
        boolean crystal = ev.template.kind == EventKind.CRYSTAL;
        boolean ruins = ev.template.kind == EventKind.RUINS;
        Material floor = crystal ? Material.DEEPSLATE_TILES : (ruins ? Material.MOSSY_STONE_BRICKS : Material.SPRUCE_PLANKS);
        Material accent = crystal ? Material.AMETHYST_BLOCK : (ruins ? Material.CHISELED_STONE_BRICKS : Material.HAY_BLOCK);
        Material light = crystal ? Material.SEA_LANTERN : (ruins ? Material.GLOWSTONE : Material.LANTERN);
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
            if (dx * dx + dz * dz <= 39) fillGroundedFloor(ev, dx, dz,
                    (dx == 0 || dz == 0) ? accent : floor, floor);
        }
        for (int sx : new int[]{-5, 5}) for (int sz : new int[]{-5, 5}) {
            for (int dy = 0; dy < (ruins ? 3 : 5); dy++) setBlk(ev, sx, dy, sz, floor);
            setBlk(ev, sx, ruins ? 3 : 5, sz, accent);
            setBlk(ev, sx, ruins ? 4 : 6, sz, light);
        }
        if (crystal) {
            for (int dy = 2; dy <= 7; dy++) setBlk(ev, 0, dy, 4, dy % 2 == 0 ? Material.AMETHYST_BLOCK : Material.PURPLE_STAINED_GLASS);
        } else if (ruins) {
            for (int dx = -3; dx <= 3; dx++) setBlk(ev, dx, 4, 5, floor);
            setBlk(ev, 0, 5, 5, accent);
        } else {
            for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
                if ((dx + dz) % 2 == 0) setBlk(ev, dx, 6, dz, Material.RED_WOOL);
                else setBlk(ev, dx, 6, dz, Material.WHITE_WOOL);
            }
        }
        setBlk(ev, 0, -1, 0, accent);
        setBlk(ev, 0, 0, 0, Material.ENDER_CHEST);
    }

    private void rememberEventBlock(ActiveBossEvent ev, Block block) {
        ev.expectedBlocks.put(block.getLocation(), block.getBlockData());
    }

    private boolean eventStillOwnsBlock(ActiveBossEvent ev, Block block) {
        BlockData expected = ev.expectedBlocks.get(block.getLocation());
        return expected != null && expected.equals(block.getBlockData());
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
        } else if (ev.template.kind == EventKind.AIRSHIP) {
            lines = new String[]{ev.template.displayName,
                    "&#FFD740⚙ Детали: &f" + (3 - ev.airshipParts.size()) + "/3 &7| " + ColorUtil.fmtTime(ev.unlockCountdownSec),
                    "&7Найдите медные блоки и нажмите ПКМ"};
        } else {
            lines = new String[]{
                    ev.template.displayName,
                    "&#FFD740⏳ До открытия замка: &f" + ColorUtil.fmtTime(ev.unlockCountdownSec),
                    ev.template.kind == EventKind.BEACON
                            ? "&#00E676💰 Раздаёт по +3 000$ и +2❖ каждые 5 сек!"
                            : "&7Удерживайте точку от других игроков!"
            };
        }
        double baseY = 2.05; // add() is relative to center, not an absolute Y
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
            as.addScoreboardTag("kg_event_holo");
            ev.hologramStands.add(as.getUniqueId());
        }
    }

    private void removeHologram(ActiveBossEvent ev) {
        // The UUID lookup does not force-load an absent chunk. Orphaned tagged
        // entities are also purged when their chunk is loaded later.
        for (UUID id : ev.hologramStands) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        ev.hologramStands.clear();
    }

    private boolean isCenterChunkLoaded(ActiveBossEvent ev) {
        World world = ev.center != null ? ev.center.getWorld() : null;
        return world != null && world.isChunkLoaded(ev.center.getBlockX() >> 4, ev.center.getBlockZ() >> 4);
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
        if (ev.template.kind == EventKind.AIRSHIP) {
            return ColorUtil.c(ev.template.displayName + " &8│ &#FFD740⚙ Детали: &f" + (3 - ev.airshipParts.size())
                    + "/3 &8│ ⏳ " + ColorUtil.fmtTime(ev.unlockCountdownSec)
                    + " &8│ &#80D8FFXYZ: &f" + bx + " " + by + " " + bz);
        }
        return ColorUtil.c(ev.template.displayName
                + " &8│ &#FFD740🔒 Открытие: &f" + ColorUtil.fmtTime(ev.unlockCountdownSec)
                + " &8│ &#80D8FFXYZ: &f" + bx + " " + by + " " + bz);
    }

    private void tickSecond() {
        if (isAutoEnabled()) {
            autoCountdownSec--;
            if (autoCountdownSec <= 0) {
                autoCountdownSec = 600; // Every 10 minutes auto-spawn a world event or boss!
                if (activeEvents.size() < 3) {
                    requestRandomEvent(null);
                }
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
                            if (playerNear && isCenterChunkLoaded(ev)) {
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
                    if (ev.unlockCountdownSec % 5 == 0 && isCenterChunkLoaded(ev)) {
                        updateCountdownHoloLine(ev);
                    }
                    if (ev.unlockCountdownSec <= 0) {
                        ev.unlockCountdownSec = 0;
                        if (ev.template.kind == EventKind.AIRSHIP) {
                            toEnd.add(ev.instanceId); // no free unlock if the parts were not found
                        } else if (isCenterChunkLoaded(ev)) unlockTimedWorldEvent(ev);
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
                st.setCustomName(ColorUtil.c(ev.template.kind == EventKind.AIRSHIP
                        ? "&#FFD740⚙ Детали: &f" + (3 - ev.airshipParts.size()) + "/3 &7| " + ColorUtil.fmtTime(Math.max(0, ev.unlockCountdownSec))
                        : "&#FFD740⏳ До открытия замка: &f" + ColorUtil.fmtTime(Math.max(0, ev.unlockCountdownSec))));
            }
        }
    }

    private void tickWorldEventVisualsAndRewards(ActiveBossEvent ev) {
        if (!isCenterChunkLoaded(ev)) return;
        World w = ev.center.getWorld();
        if (w == null) return;
        Location mid = ev.center.clone().add(0.5, 1.2, 0.5);

        if (ev.template.kind == EventKind.AIRDROP) {
            // Signal flare beam shooting into the sky!
            for (int dy = 1; dy <= 10; dy += 2) {
                w.spawnParticle(Particle.FIREWORK, mid.clone().add(0, dy, 0), 4, 0.15, 0.3, 0.15, 0.02);
            }
            w.spawnParticle(Particle.FLAME, mid, 8, 0.4, 0.3, 0.4, 0.02);
        } else if (ev.template.kind == EventKind.AIRSHIP) {
            w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, mid.clone().add(-3, 1.2, 2), 3, 0.3, 0.4, 0.3, 0.01);
            w.spawnParticle(Particle.SNOWFLAKE, mid.clone().add(0, 5, 0), 4, 1.5, 0.8, 1.5, 0.01);
            if (ev.secondsLeft % 3 == 0) for (Location part : ev.airshipParts) {
                w.spawnParticle(Particle.ELECTRIC_SPARK, part.clone().add(0.5, 1.1, 0.5), 3, 0.15, 0.12, 0.15, 0.01);
            }
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
        } else if (ev.template.kind == EventKind.CRYSTAL) {
            w.spawnParticle(Particle.END_ROD, mid.clone().add(0, 1, 0), 12, 1.1, 1.2, 1.1, 0.01);
        } else if (ev.template.kind == EventKind.RUINS) {
            w.spawnParticle(Particle.HAPPY_VILLAGER, mid, 10, 1.2, 0.6, 1.2, 0.01);
        } else if (ev.template.kind == EventKind.CAMP) {
            w.spawnParticle(Particle.FIREWORK, mid, 10, 1.2, 0.5, 1.2, 0.01);
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
        if (!eventStillOwnsBlock(ev, b)) {
            plugin.getLogger().warning("[Events] Chest location was changed externally; ending " + ev.instanceId + " without overwriting it.");
            ev.secondsLeft = 0; // removed safely by the next event tick
            return;
        }
        b.setType(Material.CHEST, false);
        rememberEventBlock(ev, b);
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
        } else if (kind == EventKind.AIRSHIP) {
            inv.setItem(4, rng.nextBoolean() ? ItemFactory.sharSanty() : ItemFactory.elytraWind());
            inv.setItem(10, ItemFactory.plast(4));
            inv.setItem(11, ItemFactory.trapka(2));
            inv.setItem(12, new ItemStack(Material.ENDER_PEARL, 8));
            inv.setItem(13, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 2));
            inv.setItem(14, ItemFactory.snowflakeCoin(12));
            inv.setItem(15, new ItemStack(Material.DIAMOND, 16));
            inv.setItem(16, new ItemStack(Material.GOLDEN_CARROT, 32));
            inv.setItem(22, ItemFactory.arrowIce(12));
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
        } else if (kind == EventKind.CRYSTAL) {
            inv.setItem(4, ItemFactory.sharAndromedy());
            inv.setItem(11, new ItemStack(Material.AMETHYST_SHARD, 32));
            inv.setItem(13, ItemFactory.enchantScroll("ice"));
            inv.setItem(15, ItemFactory.tntEnder(2));
            inv.setItem(22, new ItemStack(Material.DIAMOND, 16));
        } else if (kind == EventKind.RUINS) {
            inv.setItem(4, ItemFactory.pickDjamshut());
            inv.setItem(11, new ItemStack(Material.EMERALD, 24));
            inv.setItem(13, ItemFactory.enchantScroll("smelt"));
            inv.setItem(15, ItemFactory.tntMiner(2));
            inv.setItem(22, new ItemStack(Material.GOLDEN_APPLE, 8));
        } else if (kind == EventKind.CAMP) {
            inv.setItem(4, ItemFactory.totemLedyanoy(3));
            inv.setItem(11, new ItemStack(Material.GOLDEN_CARROT, 64));
            inv.setItem(13, ItemFactory.tntGravity(2));
            inv.setItem(15, ItemFactory.trapka(3));
            inv.setItem(22, new ItemStack(Material.ENDER_PEARL, 16));
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
        if (!eventStillOwnsBlock(ev, b)) {
            plugin.getLogger().warning("[Events] Chest location was changed externally; ending " + ev.instanceId + " without overwriting it.");
            ev.secondsLeft = 0; // removed safely by the next event tick
            return;
        }
        b.setType(Material.CHEST, false);
        rememberEventBlock(ev, b);
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
        World world = ev.center != null ? ev.center.getWorld() : null;
        try {
            if (ev.bossBar != null) {
                ev.bossBar.removeAll();
                ev.bossBar.setVisible(false);
            }
            // Do not synchronously load 3x3 chunks at the end of an event.
            // The structure footprint stays loaded via our tickets; any boss
            // outside it is removed by UUID or as an orphan on chunk load.
            if (world != null && isCenterChunkLoaded(ev)) {
                for (Entity near : world.getNearbyEntities(ev.center, 45, 35, 45)) {
                    if ((ev.bossUuid != null && ev.bossUuid.equals(near.getUniqueId()))
                            || near.getScoreboardTags().contains("kg_boss_" + ev.instanceId)) {
                        world.spawnParticle(Particle.POOF, near.getLocation().add(0, 1.0, 0), 35, 0.5, 0.8, 0.5, 0.05);
                        near.remove();
                    }
                }
            }
            if (ev.bossUuid != null) {
                Entity boss = Bukkit.getEntity(ev.bossUuid);
                if (boss != null) boss.remove();
            }
            removeHologram(ev);
            restoreEventBlocks(ev);
            if (announce && !ev.bossDefeated) {
                Bukkit.broadcastMessage(ColorUtil.c("&#FF5252[Ивенты] &7Время ивента " + ev.template.displayName
                        + (ev.template.kind == EventKind.AIRSHIP
                        ? " &7вышло: все детали не собраны, груз остался закрыт."
                        : " &7вышло — событие завершено.")));
            }
        } finally {
            if (world != null) releaseTickets(world, ev.chunkTickets);
        }
    }

    private void restoreEventBlocks(ActiveBossEvent ev) {
        World world = ev.center != null ? ev.center.getWorld() : null;
        if (world == null || !allSiteChunksLoaded(world, ev.center.getBlockX(), ev.center.getBlockZ())) {
            // Normally impossible while event tickets are held. Fail closed rather
            // than block the tick thread if a third party forcibly unloads them.
            plugin.getLogger().severe("[Events] Event chunks unexpectedly unloaded; blocks were not force-loaded for restoration: " + ev.instanceId);
            return;
        }
        Block centerBlock = ev.center.getBlock();
        if (eventStillOwnsBlock(ev, centerBlock) && centerBlock.getState() instanceof Chest) {
            ((Chest) centerBlock.getState()).getBlockInventory().clear();
        }
        List<BlockState> states = new ArrayList<BlockState>(ev.savedBlocks.values());
        Collections.reverse(states);
        for (BlockState st : states) {
            try {
                Block current = st.getBlock();
                BlockData expected = ev.expectedBlocks.get(current.getLocation());
                if (expected == null) continue;
                boolean minedMeteorOre = ev.template.kind == EventKind.METEOR && current.getType() == Material.AIR
                        && (expected.getMaterial() == Material.DEEPSLATE_DIAMOND_ORE
                        || expected.getMaterial() == Material.DEEPSLATE_EMERALD_ORE
                        || expected.getMaterial() == Material.GILDED_BLACKSTONE);
                if (eventStillOwnsBlock(ev, current) || minedMeteorOre) st.update(true, false);
            } catch (Exception error) {
                plugin.getLogger().warning("[Events] Could not restore block at " + st.getLocation() + ": " + error.getMessage());
            }
        }
    }

}
