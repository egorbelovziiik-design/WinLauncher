package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Bounded, cancellable Paper async random teleport. No synchronous chunk-generation fallback. */
final class RtpManager implements Listener {
    private static final int MAX_JOBS = 2, MAX_CANDIDATES = 24;
    private static final long TOTAL_TIMEOUT = TimeUnit.SECONDS.toNanos(60), CHUNK_TIMEOUT = TimeUnit.SECONDS.toNanos(20);
    private final KingGriefCore plugin;
    private final Map<UUID, Search> jobs = new HashMap<UUID, Search>();
    private final Random random = new Random();
    private boolean shuttingDown;

    interface ChunkLoader { CompletableFuture<?> request(World world, int x, int z, boolean generate) throws Exception; }
    private ChunkLoader chunkLoader = new ChunkLoader() {
        @Override public CompletableFuture<?> request(World world, int x, int z, boolean generate) throws Exception {
            Method method = World.class.getMethod("getChunkAtAsync", int.class, int.class, boolean.class);
            Object value = method.invoke(world, x, z, generate);
            if (!(value instanceof CompletableFuture)) throw new IllegalStateException("Paper async chunk API unavailable");
            return (CompletableFuture<?>) value;
        }
    };

    RtpManager(KingGriefCore plugin) { this.plugin = plugin; }

    void start(Player player, String requested) {
        String mode = requested == null ? "close" : requested.toLowerCase(Locale.ROOT);
        if ("cancel".equals(mode) || "отмена".equals(mode)) {
            Search old = jobs.get(player.getUniqueId());
            if (old != null) old.finish("&eПоиск РТП отменён.");
            else player.sendMessage(ColorUtil.c("&7У вас нет активного поиска РТП."));
            return;
        }
        if (!allowed(player)) { player.sendMessage(ColorUtil.c("&cРТП недоступна во время боя, проверки или до входа.")); return; }
        if (jobs.containsKey(player.getUniqueId())) { player.sendMessage(ColorUtil.c("&eПоиск РТП уже идёт. Отмена: /rtp cancel")); return; }
        if (jobs.size() >= MAX_JOBS) { player.sendMessage(ColorUtil.c("&eСейчас много запросов РТП. Попробуйте ещё раз позже.")); return; }
        int weight = plugin.getRanks().getRank(player).weight;
        if (("medium".equals(mode) || "mid".equals(mode)) && weight < 65) {
            player.sendMessage(ColorUtil.c("&cСредняя РТП доступна с GOD и выше.")); return;
        }
        if (("high".equals(mode) || "far".equals(mode) || "near".equals(mode)) && weight < 90) {
            player.sendMessage(ColorUtil.c("&cДальняя РТП и РТП рядом с игроком доступны с KING и выше.")); return;
        }
        World world;
        int min, max;
        String label;
        UUID target = null;
        if ("nether".equals(mode)) {
            world = VoidSpawnWorldGenerator.getNetherWorld(); min = 180; max = 850; label = "&#FF5252АДСКИЙ РТП";
            if (world == null) { player.sendMessage(ColorUtil.c("&cНижний мир недоступен; в обычный мир без вашего согласия не переносим.")); return; }
        } else {
            world = VoidSpawnWorldGenerator.getSurvivalWorld();
            if (world == null) { player.sendMessage(ColorUtil.c("&cМир выживания недоступен.")); return; }
            if ("near".equals(mode)) {
                List<Player> candidates = new ArrayList<Player>();
                for (Player other : world.getPlayers()) {
                    if (!other.getUniqueId().equals(player.getUniqueId()) && !other.isDead()
                            && other.getGameMode() != GameMode.SPECTATOR && !plugin.getStore().isVanished(other)) candidates.add(other);
                }
                if (!candidates.isEmpty()) target = candidates.get(random.nextInt(candidates.size())).getUniqueId();
                else {
                    mode = "high";
                    player.sendMessage(ColorUtil.c("&eИгроков в мире выживания нет: ищем дальнюю РТП."));
                }
            }
            if (target != null) { min = 55; max = 110; label = "&#FF5252РТП РЯДОМ С ИГРОКОМ"; }
            else if ("high".equals(mode) || "far".equals(mode)) { min = 4000; max = 8000; label = "&#FDE047ДАЛЬНЯЯ РТП"; }
            else if ("medium".equals(mode) || "mid".equals(mode)) { min = 1500; max = 3500; label = "&#38BDF8СРЕДНЯЯ РТП"; }
            else { min = 300; max = 1200; label = "&#00E676БЛИЗКАЯ РТП"; }
        }
        Search search = new Search(player, world, target, min, max, label);
        jobs.put(player.getUniqueId(), search);
        player.sendMessage(ColorUtil.c("&bИщем безопасную точку РТП без синхронной генерации. Не двигайтесь; отмена: &f/rtp cancel"));
        search.runTaskTimer(plugin, 1L, 10L);
    }

    private boolean allowed(Player p) {
        return p != null && p.isOnline() && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR
                && plugin.getStore().isAuthenticated(p) && !plugin.getStore().isQuickFrozen(p)
                && !plugin.getModeration().isFrozenOrAscending(p) && !plugin.getHud().isInCombat(p);
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) {
        Search job = jobs.get(e.getPlayer().getUniqueId());
        if (job != null) job.finish(null);
    }
    @EventHandler public void onWorldUnload(WorldUnloadEvent e) {
        for (Search job : new ArrayList<Search>(jobs.values())) if (job.world.equals(e.getWorld())) job.finish("&cМир РТП выгружен.");
    }
    void shutdown() {
        shuttingDown = true;
        for (Search job : new ArrayList<Search>(jobs.values())) job.finish(null);
        jobs.clear();
    }

    private final class Search extends BukkitRunnable {
        final UUID playerId, targetId;
        final World world;
        final Location origin;
        final int min, max;
        final String label;
        final long started = System.nanoTime();
        long requestedAt;
        int attempts;
        CompletableFuture<?> waiting;
        boolean finished;

        Search(Player p, World world, UUID target, int min, int max, String label) {
            playerId = p.getUniqueId(); targetId = target; this.world = world; origin = p.getLocation().clone();
            this.min = min; this.max = max; this.label = label;
        }
        @Override public void run() {
            if (finished) return;
            Player p = Bukkit.getPlayer(playerId);
            if (!allowed(p) || !sameOrigin(p)) { finish("&eРТП отменена: игрок переместился или больше не может телепортироваться."); return; }
            if (Bukkit.getWorld(world.getUID()) == null) { finish("&cМир РТП недоступен."); return; }
            long now = System.nanoTime();
            if (now - started > TOTAL_TIMEOUT) { finish("&cНе удалось найти точку РТП вовремя. Попробуйте позже."); return; }
            if (waiting != null) {
                if (now - requestedAt > CHUNK_TIMEOUT) finish("&cЗагрузка чанка РТП заняла слишком много времени; поиск остановлен.");
                return;
            }
            if (attempts >= MAX_CANDIDATES) { finish("&cБезопасное место не найдено. Попробуйте ещё раз."); return; }
            Location center = world.getWorldBorder().getCenter();
            if (targetId != null) {
                Player target = Bukkit.getPlayer(targetId);
                if (target == null || !target.isOnline() || target.isDead() || !target.getWorld().equals(world)
                        || target.getGameMode() == GameMode.SPECTATOR || plugin.getStore().isVanished(target)) {
                    finish("&eЦель РТП больше недоступна; поиск отменён."); return;
                }
                center = target.getLocation();
            }
            double angle = random.nextDouble() * Math.PI * 2.0, distance = min + random.nextDouble() * (max - min);
            final int x = (int) Math.round(center.getX() + Math.cos(angle) * distance);
            final int z = (int) Math.round(center.getZ() + Math.sin(angle) * distance);
            attempts++;
            if (!world.getWorldBorder().isInside(new Location(world, x + .5, world.getMinHeight() + 1, z + .5))) return;
            boolean generate = plugin.getConfig().getBoolean("rtp.allow-chunk-generation", true);
            if (generate && !world.isChunkLoaded(x >> 4, z >> 4)) {
                int minMb = Math.max(0, Math.min(4096, plugin.getConfig().getInt("rtp.min-free-heap-mb", 256)));
                Runtime rt = Runtime.getRuntime();
                long headroom = Math.max(0L, rt.maxMemory() - (rt.totalMemory() - rt.freeMemory()));
                if (headroom < minMb * 1024L * 1024L) { finish("&cНедостаточно свободного JVM heap для генерации РТП; попробуйте позже."); return; }
            }
            try {
                final CompletableFuture<?> future = chunkLoader.request(world, x >> 4, z >> 4, generate);
                if (future == null) throw new IllegalStateException("No async chunk future");
                waiting = future; requestedAt = System.nanoTime();
                future.whenComplete((chunk, error) -> {
                    Runnable complete = () -> onChunk(future, x, z, chunk, error);
                    if (Bukkit.isPrimaryThread()) complete.run();
                    else if (plugin.isEnabled()) {
                        try { Bukkit.getScheduler().runTask(plugin, complete); } catch (IllegalStateException ignored) { }
                    }
                });
            } catch (Exception error) {
                plugin.getLogger().warning("[RTP] Paper async chunk API unavailable: " + error.getClass().getSimpleName());
                finish("&cАсинхронная РТП недоступна. Нужен Paper 1.21.4.");
            }
        }
        boolean sameOrigin(Player p) {
            if (p == null || !p.getWorld().equals(origin.getWorld())) return false;
            return p.getLocation().distanceSquared(origin) <= 16.0;
        }
        void onChunk(CompletableFuture<?> future, int x, int z, Object chunk, Throwable error) {
            if (finished || shuttingDown || jobs.get(playerId) != this || waiting != future) return;
            waiting = null;
            Player p = Bukkit.getPlayer(playerId);
            if (!allowed(p) || !sameOrigin(p)) { finish("&eРТП отменена: игрок переместился или больше не может телепортироваться."); return; }
            if (error != null || chunk == null || !world.isChunkLoaded(x >> 4, z >> 4)) return;
            boolean ownedTicket = false;
            try {
                ownedTicket = world.addPluginChunkTicket(x >> 4, z >> 4, plugin);
                if (!world.isChunkLoaded(x >> 4, z >> 4)) return;
                Location safe = world.getEnvironment() == World.Environment.NETHER ? netherSite(world, x, z) : surfaceSite(world, x, z);
                if (safe == null || !world.getWorldBorder().isInside(safe) || plugin.getStore().getClaimAt(safe) != null
                        || !plugin.getBossEvents().isRtpSiteOutsideWorldGuard(safe)) return;
                if (targetId != null) {
                    Player target = Bukkit.getPlayer(targetId);
                    if (target == null || !target.isOnline() || !target.getWorld().equals(world) || plugin.getStore().isVanished(target)) return;
                    double dx = safe.getX() - target.getLocation().getX(), dz = safe.getZ() - target.getLocation().getZ();
                    if (dx * dx + dz * dz < 45 * 45 || dx * dx + dz * dz > 150 * 150) return;
                }
                Location previous = p.getLocation().clone();
                if (!p.teleport(safe)) { finish("&cТелепортация отменена другим плагином."); return; }
                plugin.getStore().setBack(p, previous);
                plugin.getStore().setParkourCp(p, null);
                ColorUtil.addPot(p, "resistance", "DAMAGE_RESISTANCE", 100, 4);
                ColorUtil.sound(p, "entity.enderman.teleport", 1.0f, 1.15f);
                p.sendTitle(ColorUtil.c(label), ColorUtil.c("&fXYZ: &e" + safe.getBlockX() + ", " + safe.getBlockY() + ", " + safe.getBlockZ()), 5, 45, 10);
                finish("&aРТП выполнена: &f" + safe.getBlockX() + ", " + safe.getBlockY() + ", " + safe.getBlockZ());
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("[RTP] Could not inspect/reserve loaded chunk: " + ex.getClass().getSimpleName());
                finish("&cНе удалось безопасно проверить точку РТП.");
            } finally {
                if (ownedTicket) world.removePluginChunkTicket(x >> 4, z >> 4, plugin);
            }
        }
        void finish(String message) {
            if (finished) return;
            finished = true; jobs.remove(playerId, this);
            CompletableFuture<?> future = waiting; waiting = null;
            if (future != null && !future.isDone()) future.cancel(false); // best effort
            cancel();
            Player p = Bukkit.getPlayer(playerId);
            if (message != null && p != null && p.isOnline()) p.sendMessage(ColorUtil.c(message));
        }
    }

    private static Location surfaceSite(World world, int x, int z) {
        String biome = world.getBiome(x, Math.max(world.getMinHeight(), 68), z).name();
        if (biome.contains("OCEAN") || biome.contains("RIVER")) return null;
        Block floor = world.getHighestBlockAt(x, z);
        return safeColumn(floor) ? floor.getLocation().add(.5, 1, .5) : null;
    }
    private static Location netherSite(World world, int x, int z) {
        int start = Math.max(world.getMinHeight() + 1, 35), end = Math.min(world.getMaxHeight() - 3, 105);
        for (int y = start; y <= end; y++) {
            Block floor = world.getBlockAt(x, y, z);
            if (safeColumn(floor)) return floor.getLocation().add(.5, 1, .5);
        }
        return null;
    }
    private static boolean safeColumn(Block floor) {
        Material m = floor.getType(); String name = m.name();
        if (!m.isSolid() || m == Material.MAGMA_BLOCK || m == Material.CACTUS || m == Material.CAMPFIRE
                || m == Material.SOUL_CAMPFIRE || m == Material.POWDER_SNOW || m == Material.BEDROCK
                || name.endsWith("_LEAVES") || name.endsWith("_LOG") || name.endsWith("_WOOD")) return false;
        if (floor.getY() + 2 >= floor.getWorld().getMaxHeight()) return false;
        Block one = floor.getRelative(0, 1, 0), two = floor.getRelative(0, 2, 0);
        return one.isPassable() && two.isPassable() && !one.isLiquid() && !two.isLiquid()
                && one.getType() != Material.FIRE && two.getType() != Material.FIRE
                && one.getType() != Material.SOUL_FIRE && two.getType() != Material.SOUL_FIRE
                && one.getType() != Material.POWDER_SNOW && two.getType() != Material.POWDER_SNOW;
    }
}
