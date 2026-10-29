package net.kinggrief.probe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.command.CommandSender;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import net.kinggrief.core.BossEventManager;
import net.kinggrief.core.KingGriefCore;

/** Real Paper probe. No production code in this test plugin is packaged into Core. */
public final class EventSearchProbe extends JavaPlugin implements Listener {
    private static final int REAL_X = 1001, REAL_Z = 1094;
    private static final int LOADED_X = 422, LOADED_Z = 422;
    private KingGriefCore core;
    private BossEventManager events;
    private World world;
    private Object originalLoader;
    private CompletableFuture<Object> stalled;
    private int requests, requestedX, requestedZ, stage, ticks, heartbeat;
    private boolean requestWasUnloaded;
    private int heldX, heldZ;
    private Object job;
    private boolean unloadChecked;
    private String unloadFailure;

    @Override public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            @Override public void run() {
                try { setup(); }
                catch (Throwable error) { fail(error); }
            }
        }, 30L);
    }

    private void setup() throws Exception {
        core = (KingGriefCore) Bukkit.getPluginManager().getPlugin("KingGriefCore");
        if (core == null || !core.isEnabled()) throw new AssertionError("Core not enabled");
        events = core.getBossEvents();
        WorldCreator wc = new WorldCreator("kg_event_search_test");
        wc.generator(new ChunkGenerator() {
            @Override public ChunkData generateChunkData(World w, java.util.Random random, int x, int z, BiomeGrid biome) {
                ChunkData data = createChunkData(w);
                data.setRegion(0, 60, 0, 16, 64, 16, Material.DIRT);
                data.setRegion(0, 64, 0, 16, 65, 16, Material.GRASS_BLOCK);
                for (int bx = 0; bx < 16; bx++) for (int bz = 0; bz < 16; bz++) biome.setBiome(bx, bz, Biome.PLAINS);
                return data;
            }
            @Override public boolean shouldGenerateNoise() { return false; }
            @Override public boolean shouldGenerateSurface() { return false; }
            @Override public boolean shouldGenerateCaves() { return false; }
            @Override public boolean shouldGenerateDecorations() { return false; }
            @Override public boolean shouldGenerateMobs() { return false; }
            @Override public boolean shouldGenerateStructures() { return false; }
        });
        wc.generateStructures(false);
        world = Bukkit.createWorld(wc);
        if (world == null) throw new AssertionError("test world not created");
        world.setKeepSpawnInMemory(false);

        // A forced event on an untouched far-away chunk must be rejected without
        // entering getBlock()/getChunkAt() or creating any of the four chunks.
        Location ungenerated = new Location(world, 1500006, 65, 1500006);
        long start = System.nanoTime();
        if (events.spawnBossEvent("camp", ungenerated) != null) throw new AssertionError("unloaded forced event accepted");
        long millis = (System.nanoTime() - start) / 1_000_000L;
        if (millis > 500) throw new AssertionError("unloaded forced event blocked for " + millis + " ms");
        for (int x = (ungenerated.getBlockX()-9)>>4; x <= (ungenerated.getBlockX()+9)>>4; x++)
            for (int z = (ungenerated.getBlockZ()-9)>>4; z <= (ungenerated.getBlockZ()+9)>>4; z++)
                if (world.isChunkLoaded(x,z)) throw new AssertionError("forced event loaded an absent chunk");

        // The first footprint chunk is a deliberately loaded fixture. The next
        // request is held forever by a test double while the real tick thread runs.
        int candidateX = 6406, candidateZ = 6406;
        heldX = (candidateX - 9) >> 4; heldZ = (candidateZ - 9) >> 4;
        world.getChunkAt(heldX, heldZ).load(); // fixture setup, not production search
        Field loader = field(events, "chunkLoader");
        originalLoader = loader.get(events);
        stalled = new CompletableFuture<Object>();
        Class<?> loaderType = loader.getType();
        Object fakeLoader = Proxy.newProxyInstance(loaderType.getClassLoader(), new Class<?>[]{loaderType}, (proxy, method, args) -> {
            if (method.getName().equals("request")) {
                requests++;
                requestedX = ((Integer)args[1]).intValue();
                requestedZ = ((Integer)args[2]).intValue();
                requestWasUnloaded = !world.isChunkLoaded(requestedX, requestedZ);
                return stalled;
            }
            throw new UnsupportedOperationException(method.getName());
        });
        loader.set(events, fakeLoader);
        job = startAt(candidateX, candidateZ);
        if (events.requestBossEvent("camp", null)) throw new AssertionError("parallel event search was accepted");
        stage = 1;
        new BukkitRunnable() {
            @Override public void run() {
                try {
                    heartbeat++;
                    ticks++;
                    if (ticks > 1800) throw new AssertionError("event search probe timeout, stage " + stage);
                    advance();
                } catch (Throwable error) {
                    cancel();
                    fail(error);
                }
            }
        }.runTaskTimer(this, 1L, 1L);
    }

    @SuppressWarnings("unchecked")
    private Object queueSearch() throws Exception {
        Map<String, BossEventManager.BossTemplate> templates = (Map<String, BossEventManager.BossTemplate>) field(events, "templates").get(events);
        Method queue = BossEventManager.class.getDeclaredMethod("queueSearch", BossEventManager.BossTemplate.class, World.class, CommandSender.class);
        queue.setAccessible(true);
        if (!((Boolean)queue.invoke(events, templates.get("camp"), world, null)).booleanValue()) throw new AssertionError("search not queued");
        Object current = field(events, "pendingSearch").get(events);
        if (current == null) throw new AssertionError("missing pending search");
        ((List<?>) field(current, "loadedCandidates").get(current)).clear();
        return current;
    }

    private Object startAt(int x, int z) throws Exception {
        Object current = queueSearch();
        field(current, "candidate").set(current, new int[]{x,z});
        field(current, "allowAsync").setBoolean(current, true);
        Method footprint = BossEventManager.class.getDeclaredMethod("footprint", int.class, int.class);
        footprint.setAccessible(true);
        field(current, "required").set(current, footprint.invoke(events, x, z));
        return current;
    }

    private void advance() throws Exception {
        if (stage == 1) {
            if (requests == 0 || ticks < 30) return;
            if (requests != 1 || stalled.isDone()) throw new AssertionError("unbounded/finished stalled request");
            if (heartbeat < 30) throw new AssertionError("server heartbeat stopped while chunk future was pending");
            if (!world.getPluginChunkTickets(heldX, heldZ).contains(core)) throw new AssertionError("loaded fixture chunk was not pinned");
            if (!requestWasUnloaded) throw new AssertionError("async request did not begin on an unloaded chunk");
            // Paper may independently load neighbours after the first fixture
            // ticket; their later loaded state does not imply a blocking call.
            // Accelerate the timeout rather than making the disposable server wait 20 seconds.
            int waitingSince = field(job, "waitingSince").getInt(job);
            field(job, "elapsedTicks").setInt(job, waitingSince + 399);
            stage = 2;
            return;
        }
        if (stage == 2) {
            if (field(events, "pendingSearch").get(events) != null) return;
            if (world.getPluginChunkTickets(heldX, heldZ).contains(core)) throw new AssertionError("timeout leaked a chunk ticket");
            if (requests != 1) throw new AssertionError("timeout launched another chunk request");
            stalled.complete(null); // Late completion must be ignored after cancellation.
            field(events, "chunkLoader").set(events, originalLoader);
            int x = REAL_X, z = REAL_Z;
            for (int cx=(x-9)>>4; cx<=(x+9)>>4; cx++) for (int cz=(z-9)>>4; cz<=(z+9)>>4; cz++)
                if (world.isChunkLoaded(cx,cz)) throw new AssertionError("real test chunk unexpectedly loaded");
            job = startAt(x,z);
            stage = 3;
            ticks = 0;
            heartbeat = 0;
            return;
        }
        if (stage == 3) {
            BossEventManager.ActiveBossEvent found = null;
            for (BossEventManager.ActiveBossEvent event : events.getActiveEvents()) {
                if (event.center.getWorld().equals(world) && event.center.getBlockX() == REAL_X && event.center.getBlockZ() == REAL_Z) found = event;
            }
            if (found == null) {
                if (field(events, "pendingSearch").get(events) == null) throw new AssertionError("real async search ended without an event");
                return;
            }
            if (heartbeat < 4) throw new AssertionError("real search did not yield between chunk loads");
            int count = 0;
            for (int cx=(REAL_X-9)>>4; cx<=(REAL_X+9)>>4; cx++) for (int cz=(REAL_Z-9)>>4; cz<=(REAL_Z+9)>>4; cz++) {
                if (!world.isChunkLoaded(cx,cz) || !world.getPluginChunkTickets(cx,cz).contains(core))
                    throw new AssertionError("active event did not keep its footprint loaded");
                count++;
            }
            if (count != 4) throw new AssertionError("unexpected footprint " + count);
            Location center = found.center.clone();
            org.bukkit.block.Block external = center.clone().add(3,1,0).getBlock();
            external.setType(Material.DIAMOND_BLOCK,false);
            events.endEvent(found.instanceId, false);
            if (center.getBlock().getType() != Material.AIR || external.getType() != Material.DIAMOND_BLOCK)
                throw new AssertionError("event restore overwrote a foreign block or left its chest");
            for (int cx=(REAL_X-9)>>4; cx<=(REAL_X+9)>>4; cx++) for (int cz=(REAL_Z-9)>>4; cz<=(REAL_Z+9)>>4; cz++)
                if (world.getPluginChunkTickets(cx,cz).contains(core)) throw new AssertionError("event cleanup leaked a ticket");
            external.setType(Material.AIR, false);
            // Exercise the normal loaded-candidate selection path without asking
            // Paper to generate anything during the search itself.
            for (int cx=(LOADED_X-9)>>4; cx<=(LOADED_X+9)>>4; cx++) for (int cz=(LOADED_Z-9)>>4; cz<=(LOADED_Z+9)>>4; cz++)
                world.getChunkAt(cx,cz).load(); // fixture setup only
            requests = 0;
            Field loader = field(events, "chunkLoader");
            Class<?> loaderType = loader.getType();
            loader.set(events, Proxy.newProxyInstance(loaderType.getClassLoader(), new Class<?>[]{loaderType}, (proxy, method, args) -> {
                requests++;
                throw new IllegalStateException("loaded-site search requested a chunk");
            }));
            job = queueSearch();
            @SuppressWarnings("unchecked") List<int[]> candidates = (List<int[]>)field(job, "loadedCandidates").get(job);
            candidates.add(new int[]{LOADED_X, LOADED_Z});
            stage = 4;
            ticks = 0;
            return;
        }
        if (stage == 4) {
            BossEventManager.ActiveBossEvent unloading = null;
            for (BossEventManager.ActiveBossEvent event : events.getActiveEvents()) {
                if (event.center.getWorld().equals(world) && event.center.getBlockX() == LOADED_X && event.center.getBlockZ() == LOADED_Z) unloading = event;
            }
            if (unloading == null) {
                if (field(events, "pendingSearch").get(events) == null) throw new AssertionError("loaded-site search failed");
                return;
            }
            if (requests != 0) throw new AssertionError("loaded-site search unnecessarily requested generation");
            field(events, "chunkLoader").set(events, originalLoader);
            if (!Bukkit.unloadWorld(world, true)) throw new AssertionError("test world failed to unload");
            if (events.getActiveEvents().contains(unloading) || !unloadChecked || unloadFailure != null)
                throw new AssertionError("world unload failed to restore event: " + unloadFailure);
            getLogger().info("KG-EVENT-SEARCH PASS: unloaded refusal, stalled future heartbeat, timeout/ticket release, real Paper async generation, loaded-site search, event restoration and world unload");
            stage = 5;
            Bukkit.shutdown();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void inspectWorldUnload(WorldUnloadEvent event) {
        if (stage != 4 || !event.getWorld().equals(world)) return;
        unloadChecked = true;
        if (!world.isChunkLoaded(LOADED_X >> 4, LOADED_Z >> 4)) unloadFailure = "center unexpectedly unloaded";
        else if (world.getBlockAt(LOADED_X, 65, LOADED_Z).getType() != Material.AIR) unloadFailure = "center block not restored";
        else if (!events.getActiveEvents().isEmpty()) unloadFailure = "event remained active";
        for (int cx=(LOADED_X-9)>>4; cx<=(LOADED_X+9)>>4; cx++) for (int cz=(LOADED_Z-9)>>4; cz<=(LOADED_Z+9)>>4; cz++)
            if (world.getPluginChunkTickets(cx,cz).contains(core)) unloadFailure = "event ticket not released";
    }

    private static Field field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private void fail(Throwable error) {
        getLogger().severe("KG-EVENT-SEARCH FAIL: " + error);
        error.printStackTrace();
        Bukkit.shutdown();
    }
}
