package net.kinggrief.probe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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
    private static final int OVERWORLD_X = 1126, OVERWORLD_Z = 1190;
    private KingGriefCore core;
    private BossEventManager events;
    private World world;
    private Object originalLoader, originalHeapSupplier;
    private NonCancellableFuture stalled;
    private CompletableFuture<Object> cancellable;
    private int requests, requestedX, requestedZ, stage, ticks, heartbeat;
    private boolean requestWasUnloaded;
    private int heldX, heldZ;
    private Object job;
    private boolean unloadChecked;
    private String unloadFailure;

    // Some server implementations may be unable to cancel work already queued.
    // A late completion must still be ignored after timeout and ticket cleanup.
    private static final class NonCancellableFuture extends CompletableFuture<Object> {
        boolean cancelAttempted;
        @Override public boolean cancel(boolean mayInterruptIfRunning) {
            cancelAttempted = true;
            return false;
        }
    }

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
        verifyInstallDiagnostics();
        verifyAutoSwitch();
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
        stalled = new NonCancellableFuture();
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

    private void verifyInstallDiagnostics() throws Exception {
        Files.createDirectories(getDataFolder().toPath());
        Path dir = Files.createTempDirectory(getDataFolder().toPath(), "diagnostic-jars-");
        Path original = dir.resolve("KingGriefCore.jar");
        archive(original, "plugin.yml", "name: KingGriefCore\n");
        archive(dir.resolve("old (16).jar"), "plugin.yml", "name: KingGriefCore\n");
        archive(dir.resolve("renamed.jar"), "paper-plugin.yml", "name: kinggriefcore\n");
        archive(dir.resolve("other.jar"), "plugin.yml", "name: DifferentPlugin\n");
        archive(dir.resolve("disabled.jar.off"), "plugin.yml", "name: KingGriefCore\n");
        archive(dir.resolve("oversized.jar"), "plugin.yml", "name: KingGriefCore\n#" + "x".repeat(70_000));
        Files.writeString(dir.resolve("broken.jar"), "not a jar", StandardCharsets.UTF_8);
        // Another .jar path is relevant even if it is a symlink to the same file:
        // the plugin loader can encounter both names.
        boolean hasAlias;
        try { Files.createSymbolicLink(dir.resolve("alias.jar"), original.getFileName()); hasAlias = true; }
        catch (UnsupportedOperationException | java.io.IOException ignored) { hasAlias = false; }
        Class<?> diagnostics = Class.forName("net.kinggrief.core.CoreInstallDiagnostics", true, core.getClass().getClassLoader());
        Method find = diagnostics.getDeclaredMethod("findOtherCoreJars", java.io.File.class, java.io.File.class);
        find.setAccessible(true);
        @SuppressWarnings("unchecked") List<String> duplicates = (List<String>)find.invoke(null, dir.toFile(), original.toFile());
        List<String> expected = hasAlias ? Arrays.asList("alias.jar", "old (16).jar", "renamed.jar")
                : Arrays.asList("old (16).jar", "renamed.jar");
        if (!duplicates.equals(expected)) throw new AssertionError("incorrect duplicate diagnostics: " + duplicates);

        // Put a synthetic duplicate in the real plugin folder only after Paper
        // has loaded, then exercise the actual startup report and warning.
        Path duplicate = core.getDataFolder().toPath().getParent().resolve("diagnostic-duplicate-test.jar");
        if (Files.exists(duplicate)) throw new AssertionError("duplicate fixture already exists");
        try {
            archive(duplicate, "plugin.yml", "name: KingGriefCore\n");
            Method getFile = JavaPlugin.class.getDeclaredMethod("getFile");
            getFile.setAccessible(true);
            Method report = diagnostics.getDeclaredMethod("report", KingGriefCore.class, java.io.File.class);
            report.setAccessible(true);
            report.invoke(null, core, (java.io.File)getFile.invoke(core));
        } finally {
            Files.deleteIfExists(duplicate);
        }
    }

    private static void archive(Path file, String entry, String contents) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry(entry));
            zip.write(contents.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    private void verifyAutoSwitch() throws Exception {
        if (!events.isAutoEnabled()) throw new AssertionError("default automatic events must be enabled");
        Field countdown = field(events, "autoCountdownSec");
        countdown.setInt(events, 1);
        core.getConfig().set("events.auto-enabled", false);
        Method tick = BossEventManager.class.getDeclaredMethod("tickSecond");
        tick.setAccessible(true);
        tick.invoke(events);
        if (countdown.getInt(events) != 1 || field(events, "pendingSearch").get(events) != null)
            throw new AssertionError("automatic event ran while disabled");
        if (!events.requestBossEvent("camp", Bukkit.getConsoleSender()))
            throw new AssertionError("manual search was disabled along with automatic events");
        Object current = field(events, "pendingSearch").get(events);
        Method cancel = current.getClass().getDeclaredMethod("cancelSearch");
        cancel.setAccessible(true);
        cancel.invoke(current);
        if (field(events, "pendingSearch").get(events) != null) throw new AssertionError("manual search did not cancel");
        core.getConfig().set("events.auto-enabled", true);
        countdown.setInt(events, 360);
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
        forceCandidate(current, x, z);
        return current;
    }

    private void forceCandidate(Object current, int x, int z) throws Exception {
        ((List<?>)field(current, "loadedCandidates").get(current)).clear();
        field(current, "candidate").set(current, new int[]{x,z});
        field(current, "allowAsync").setBoolean(current, true);
        Method footprint = BossEventManager.class.getDeclaredMethod("footprint", int.class, int.class);
        footprint.setAccessible(true);
        field(current, "required").set(current, footprint.invoke(events, x, z));
    }

    private void advance() throws Exception {
        if (stage == 1) {
            if (requests == 0 || ticks < 30) return;
            if (requests != 1 || stalled.isDone()) throw new AssertionError("unbounded/finished stalled request");
            if (heartbeat < 30) throw new AssertionError("server heartbeat stopped while chunk future was pending");
            if (!world.getPluginChunkTickets(heldX, heldZ).contains(core)) throw new AssertionError("loaded fixture chunk was not pinned");
            if (!requestWasUnloaded) throw new AssertionError("async request did not begin on an unloaded chunk");
            // Simulate more than 20 real seconds passing while relatively few
            // ticks elapsed. The wall clock, not only tick count, must end it.
            field(job, "waitingStartedNanos").setLong(job, System.nanoTime() - TimeUnit.SECONDS.toNanos(21));
            stage = 2;
            return;
        }
        if (stage == 2) {
            if (field(events, "pendingSearch").get(events) != null) return;
            if (world.getPluginChunkTickets(heldX, heldZ).contains(core)) throw new AssertionError("timeout leaked a chunk ticket");
            if (requests != 1 || !stalled.cancelAttempted) throw new AssertionError("timeout did not cancel the pending request");
            stalled.complete(null); // Non-cancellable late completion must be ignored.
            if (field(events, "pendingSearch").get(events) != null) throw new AssertionError("late future resurrected search");
            requests = 0;
            core.getConfig().set("events.allow-chunk-generation", false);
            job = queueSearch(); // no loaded candidates: must stop without a chunk request
            stage = 3;
            return;
        }
        if (stage == 3) {
            if (field(events, "pendingSearch").get(events) != null) return;
            if (requests != 0) throw new AssertionError("disabled generation requested a chunk");
            core.getConfig().set("events.allow-chunk-generation", true);
            Field heap = field(events, "availableHeapBytes");
            originalHeapSupplier = heap.get(events);
            heap.set(events, (LongSupplier)() -> 0L);
            job = startAt(48006, 48006); // no chunk in this footprint is loaded
            stage = 4;
            return;
        }
        if (stage == 4) {
            if (field(events, "pendingSearch").get(events) != null) return;
            if (requests != 0) throw new AssertionError("low-heap search requested a chunk");
            field(events, "availableHeapBytes").set(events, originalHeapSupplier);
            cancellable = new CompletableFuture<Object>();
            Field loader = field(events, "chunkLoader");
            Class<?> loaderType = loader.getType();
            loader.set(events, Proxy.newProxyInstance(loaderType.getClassLoader(), new Class<?>[]{loaderType}, (proxy, method, args) -> {
                requests++;
                return cancellable;
            }));
            job = startAt(48006, 48006);
            stage = 5;
            return;
        }
        if (stage == 5) {
            if (requests == 0) return;
            Method cancel = job.getClass().getDeclaredMethod("cancelSearch");
            cancel.setAccessible(true);
            cancel.invoke(job);
            if (!cancellable.isCancelled() || field(events, "pendingSearch").get(events) != null)
                throw new AssertionError("cancellable in-flight future was not cancelled");
            field(events, "chunkLoader").set(events, originalLoader);
            int x = REAL_X, z = REAL_Z;
            for (int cx=(x-9)>>4; cx<=(x+9)>>4; cx++) for (int cz=(z-9)>>4; cz<=(z+9)>>4; cz++)
                if (world.isChunkLoaded(cx,cz)) throw new AssertionError("real test chunk unexpectedly loaded");
            job = startAt(x,z);
            stage = 6;
            ticks = 0;
            heartbeat = 0;
            return;
        }
        if (stage == 6) {
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
            // Even when generation is disabled, an already-loaded, safe site
            // should work without asking the loader for a new chunk.
            for (int cx=(LOADED_X-9)>>4; cx<=(LOADED_X+9)>>4; cx++) for (int cz=(LOADED_Z-9)>>4; cz<=(LOADED_Z+9)>>4; cz++)
                world.getChunkAt(cx,cz).load(); // fixture setup only
            requests = 0;
            core.getConfig().set("events.allow-chunk-generation", false);
            Field loader = field(events, "chunkLoader");
            Class<?> loaderType = loader.getType();
            loader.set(events, Proxy.newProxyInstance(loaderType.getClassLoader(), new Class<?>[]{loaderType}, (proxy, method, args) -> {
                requests++;
                throw new IllegalStateException("loaded-site search requested a chunk");
            }));
            job = queueSearch();
            @SuppressWarnings("unchecked") List<int[]> candidates = (List<int[]>)field(job, "loadedCandidates").get(job);
            candidates.add(new int[]{LOADED_X, LOADED_Z});
            stage = 7;
            ticks = 0;
            return;
        }
        if (stage == 7) {
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

            // Exercise the screenshot's automatic tick path against actual
            // ungenerated normal Overworld terrain, not the flat test generator.
            world = Bukkit.getWorld("world");
            if (world == null) throw new AssertionError("normal Overworld not found");
            for (int cx=(OVERWORLD_X-9)>>4; cx<=(OVERWORLD_X+9)>>4; cx++)
                for (int cz=(OVERWORLD_Z-9)>>4; cz<=(OVERWORLD_Z+9)>>4; cz++)
                    if (world.isChunkLoaded(cx,cz)) throw new AssertionError("normal terrain fixture was already loaded");
            core.getConfig().set("events.allow-chunk-generation", true);
            requests = 0;
            Field loader = field(events, "chunkLoader");
            Class<?> loaderType = loader.getType();
            loader.set(events, Proxy.newProxyInstance(loaderType.getClassLoader(), new Class<?>[]{loaderType}, (proxy, method, args) -> {
                requests++;
                method.setAccessible(true);
                return method.invoke(originalLoader, args);
            }));
            field(events, "autoCountdownSec").setInt(events, 1);
            Method tick = BossEventManager.class.getDeclaredMethod("tickSecond");
            tick.setAccessible(true);
            tick.invoke(events);
            job = field(events, "pendingSearch").get(events);
            if (job == null || field(job, "world").get(job) != world) throw new AssertionError("automatic search did not use Overworld");
            forceCandidate(job, OVERWORLD_X, OVERWORLD_Z);
            stage = 8;
            ticks = 0;
            heartbeat = 0;
            return;
        }
        if (stage == 8) {
            if (field(events, "pendingSearch").get(events) == null) throw new AssertionError("normal-terrain search ended prematurely");
            List<?> pinned = (List<?>)field(job, "pinned").get(job);
            if (requests == 0 || pinned.isEmpty()) return;
            if (heartbeat < 1) throw new AssertionError("normal-terrain generation stopped the server heartbeat");
            Method cancel = job.getClass().getDeclaredMethod("cancelSearch");
            cancel.setAccessible(true);
            cancel.invoke(job);
            for (int cx=(OVERWORLD_X-9)>>4; cx<=(OVERWORLD_X+9)>>4; cx++)
                for (int cz=(OVERWORLD_Z-9)>>4; cz<=(OVERWORLD_Z+9)>>4; cz++)
                    if (world.getPluginChunkTickets(cx,cz).contains(core)) throw new AssertionError("normal-terrain search leaked a ticket");
            getLogger().info("KG-EVENT-SEARCH PASS: duplicate diagnosis, auto/manual switch, heap/generation limits, wall-clock timeout, cancellation/late callback, ticket cleanup, Paper async generation, loaded-only site, world unload and automatic normal-terrain generation");
            stage = 9;
            Bukkit.shutdown();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void inspectWorldUnload(WorldUnloadEvent event) {
        if (stage != 7 || !event.getWorld().equals(world)) return;
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
