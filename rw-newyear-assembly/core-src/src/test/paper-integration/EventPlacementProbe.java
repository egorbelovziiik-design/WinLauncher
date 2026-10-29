package net.kinggrief.probe;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.kinggrief.core.BossEventManager;
import net.kinggrief.core.DataStore;
import net.kinggrief.core.KingGriefCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Deterministic safety regression. WorldGuard here is an API double. */
public final class EventPlacementProbe extends JavaPlugin {
    private static final int X = 422, Z = 422, BORDER = 10006, FAR = 10214;
    private static final String[] TEMPLATES = {"airdrop","airship","meteor","beacon","citadel","volcano",
            "crystal","ruins","camp","grinch","krush","titan","osiris","andromeda"};
    private KingGriefCore core;
    private BossEventManager events;
    private World world;
    private RegionManager regions;
    private Object job, originalLoader;
    private DataStore.Claim claim;
    private int stage, ticks, requests, heartbeat, bossIndex;
    private boolean raceInjected;

    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                core = (KingGriefCore) Bukkit.getPluginManager().getPlugin("KingGriefCore");
                events = core.getBossEvents();
                world = Bukkit.getWorld("world");
                if (world == null || !core.isEnabled()) throw new AssertionError("server not ready");
                regions = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
                core.getConfig().set("events.auto-enabled", false);
                core.getConfig().set("events.allow-chunk-generation", false);
                Field loader = field(events, "chunkLoader");
                originalLoader = loader.get(events);
                Class<?> type = loader.getType();
                loader.set(events, Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
                    requests++;
                    throw new IllegalStateException("fixture search must not request generation");
                }));
                new BukkitRunnable() {
                    @Override public void run() {
                        try {
                            heartbeat++;
                            if (++ticks > 2200) throw new AssertionError("placement probe timeout, stage=" + stage);
                            advance();
                        } catch (Throwable failure) { cancel(); fail(failure); }
                    }
                }.runTaskTimer(this, 1L, 1L);
                loadFootprint(X, Z, () -> prepare(X, Z, true, true, () -> {
                    try { startManual(X, Z); stage = 1; }
                    catch (Throwable failure) { fail(failure); }
                }));
            } catch (Throwable failure) { fail(failure); }
        }, 30L);
    }

    private void advance() throws Exception {
        if (stage == 1) {
            BossEventManager.ActiveBossEvent ev = onlyEvent();
            if (ev == null) { requireSearching("nearby safe point"); return; }
            if (ev.center.getBlockX() != 409 || ev.center.getBlockZ() != 409 || ev.center.getBlockY() != 66)
                throw new AssertionError("did not try the safe neighbouring slope: " + ev.center);
            assertChest(430, 65, 430, 3);
            if (heartbeat < 15 || requests != 0) throw new AssertionError("loaded search blocked or generated chunks");
            events.endEvent(ev.instanceId, false);
            assertChest(430, 65, 430, 3);
            if (world.getBlockAt(405, 66, 405).getType() != Material.ALLIUM
                    || world.getBlockAt(405, 67, 405).getType() != Material.OAK_LEAVES)
                throw new AssertionError("natural flower/leaves were not restored");
            stage = 10;
            prepare(X, Z, false, false, () -> {
                try {
                    org.bukkit.block.Block placed = world.getBlockAt(X+2,66,Z+2);
                    placed.setType(Material.OAK_LEAVES,false);
                    org.bukkit.block.data.type.Leaves data=(org.bukkit.block.data.type.Leaves)placed.getBlockData();
                    data.setPersistent(true); placed.setBlockData(data,false);
                    if (events.spawnBossEvent("camp",new Location(world,X,65,Z))!=null || placed.getType()!=Material.OAK_LEAVES)
                        throw new AssertionError("player-placed leaves were overwritten");
                    placed.setType(Material.AIR,false);
                    startManual(X, Z); stage = 2;
                }
                catch (Throwable failure) { fail(failure); }
            });
            return;
        }
        if (stage == 2) {
            if (!raceInjected) {
                Object scanner = field(job, "scanner").get(job);
                if (scanner == null || field(scanner, "cursor").getInt(scanner) == 0) { requireSearching("race scan"); return; }
                // This first column has already been inspected. A player or
                // another plugin may add a chest while the scan yields.
                putChest(413, 65, 413, 7);
                field(job, "localCandidates").set(job, new ArrayList<int[]>(Arrays.asList(new int[]{X, Z})));
                field(job, "localIndex").setInt(job, 1);
                raceInjected = true;
                return;
            }
            if (field(events, "pendingSearch").get(events) != null) return;
            if (onlyEvent() != null || !rejected("RECHECK_OBSTRUCTION"))
                throw new AssertionError("changed earlier block was not rechecked: " + rejections());
            assertChest(413, 65, 413, 7);
            world.getBlockAt(413, 65, 413).setType(Material.AIR, false);
            claim = core.getStore().createClaim("ProbeOwner", new Location(world, X, 65, Z), 100);
            startManual(X, Z); stage = 3;
            return;
        }
        if (stage == 3) {
            if (field(events, "pendingSearch").get(events) != null) return;
            if (onlyEvent() != null || !rejected("CLAIM")) throw new AssertionError("claim bypassed: " + rejections());
            core.getStore().removeClaim(claim);
            regions.getRegions().put("protected", new ProtectedRegion(new BlockVector3(390, 50, 390), new BlockVector3(440, 90, 440)));
            startManual(X, Z); stage = 4;
            return;
        }
        if (stage == 4) {
            if (field(events, "pendingSearch").get(events) != null) return;
            if (onlyEvent() != null || !rejected("WORLDGUARD_REGION")) throw new AssertionError("WG region bypassed: " + rejections());
            regions.getRegions().clear();
            Bukkit.getPluginManager().disablePlugin(Bukkit.getPluginManager().getPlugin("WorldGuard"));
            startManual(X, Z); stage = 5;
            return;
        }
        if (stage == 5) {
            if (field(events, "pendingSearch").get(events) != null) return;
            if (onlyEvent() != null || !rejected("WORLDGUARD_UNAVAILABLE"))
                throw new AssertionError("disabled WG did not fail closed: " + rejections());
            Plugin wg = Bukkit.getPluginManager().getPlugin("WorldGuard");
            Bukkit.getPluginManager().enablePlugin(wg);
            if (!wg.isEnabled()) throw new AssertionError("mock WG did not re-enable");
            world.setSpawnLocation(600, 64, 600);
            Method allowed = BossEventManager.class.getDeclaredMethod("candidateAllowed", World.class, int.class, int.class);
            allowed.setAccessible(true);
            if ((Boolean)allowed.invoke(events, world, 610, 610)) throw new AssertionError("moved spawn was not protected");
            world.getWorldBorder().setCenter(BORDER, BORDER);
            world.getWorldBorder().setSize(600);
            if (world.isChunkLoaded(BORDER >> 4, BORDER >> 4)) throw new AssertionError("border-center fixture already loaded");
            if (!(Boolean)allowed.invoke(events, world, FAR, FAR)) {
                Method why = BossEventManager.class.getDeclaredMethod("candidateRejection", World.class, int.class, int.class);
                why.setAccessible(true);
                throw new AssertionError("moved world border rejected: " + why.invoke(events, world, FAR, FAR)
                        + " spawn=" + world.getSpawnLocation() + " border=" + world.getWorldBorder().getCenter()
                        + " size=" + world.getWorldBorder().getSize());
            }
            if (world.isChunkLoaded(BORDER >> 4, BORDER >> 4)) throw new AssertionError("spawn lookup synchronously loaded border center");
            stage = 10;
            loadFootprint(FAR, FAR, () -> prepare(FAR, FAR, false, false, () -> {
                try { startAutomatic(); stage = 6; }
                catch (Throwable failure) { fail(failure); }
            }));
            return;
        }
        if (stage == 6) {
            BossEventManager.ActiveBossEvent ev = onlyEvent();
            if (ev == null) { requireSearching("automatic shifted-border event"); return; }
            if (ev.center.getBlockX() != FAR || ev.center.getBlockZ() != FAR)
                throw new AssertionError("automatic event ignored shifted border fixture: " + ev.center);
            if (requests != 0) throw new AssertionError("fixture requested generated chunks");
            events.endEvent(ev.instanceId, false);
            stage = 7;
            return;
        }
        if (stage == 8) {
            BossEventManager.ActiveBossEvent ev=onlyEvent();
            if (ev==null) { requireSearching("bounded template " + TEMPLATES[bossIndex]); return; }
            String id=TEMPLATES[bossIndex];
            if (!id.equals(ev.template.id)) throw new AssertionError("wrong template " + id);
            if (ev.template.kind==BossEventManager.EventKind.BOSS) {
                org.bukkit.entity.Entity entity=Bukkit.getEntity(ev.bossUuid);
                if (!(entity instanceof org.bukkit.entity.LivingEntity)) throw new AssertionError("missing boss: " + id);
                org.bukkit.entity.LivingEntity boss=(org.bukkit.entity.LivingEntity)entity;
                if (Math.abs(boss.getHealth()-ev.template.maxHp)>0.01 || boss.getHealth()>1024.0)
                    throw new AssertionError("invalid boss health " + id + ": " + boss.getHealth());
                boss.damage(5.0);
                if (boss.getHealth()>=ev.template.maxHp) throw new AssertionError("boss cannot take damage: " + id);
            }
            events.endEvent(ev.instanceId,false);
            if (ev.bossUuid!=null && Bukkit.getEntity(ev.bossUuid)!=null) throw new AssertionError("boss not cleaned up: " + id);
            bossIndex++; stage=7; return;
        }
        if (stage == 7) {
            if (bossIndex < TEMPLATES.length) {
                startManual(FAR,FAR,TEMPLATES[bossIndex]); stage=8; return;
            }
            for (int[] pos : footprint(FAR, FAR)) {
                if (world.getPluginChunkTickets(pos[0], pos[1]).contains(core)) throw new AssertionError("event ticket leaked");
                world.removePluginChunkTicket(pos[0], pos[1], this);
            }
            for (int[] pos : footprint(X, Z)) world.removePluginChunkTicket(pos[0], pos[1], this);
            field(events, "chunkLoader").set(events, originalLoader);
            getLogger().info("KG-EVENT-PLACEMENT PASS: console command; nearby site; slope/flower/leaves restoration; race recheck; claim and mock WG fail-closed; moved spawn/border; automatic event; all 14 bounded templates and five bosses; tickets/heartbeat");
            stage = 99;
            Bukkit.shutdown();
        }
    }

    private void startManual(int x, int z) throws Exception { startManual(x, z, "camp"); }

    private void startManual(int x, int z, String id) throws Exception {
        if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "events start " + id)) throw new AssertionError("events command not dispatched");
        job = field(events, "pendingSearch").get(events);
        if (job == null) throw new AssertionError("console events command did not queue a search");
        injectLoadedSite(x, z);
    }

    private void startAutomatic() throws Exception {
        core.getConfig().set("events.auto-enabled", true);
        field(events, "autoCountdownSec").setInt(events, 1);
        Method tick = BossEventManager.class.getDeclaredMethod("tickSecond");
        tick.setAccessible(true);
        tick.invoke(events);
        job = field(events, "pendingSearch").get(events);
        if (job == null || field(job, "world").get(job) != world) throw new AssertionError("automatic search not queued");
        // Also exercise random generation sampling itself: all 200 points must
        // remain inside the displaced border, well away from old world origin.
        Method sample = job.getClass().getDeclaredMethod("randomSiteWithinBorder");
        sample.setAccessible(true);
        Set<String> unique = new HashSet<String>();
        for (int i = 0; i < 200; i++) {
            int[] p = (int[])sample.invoke(job);
            if (p == null || p[0] < BORDER-290 || p[0] > BORDER+290 || p[1] < BORDER-290 || p[1] > BORDER+290)
                throw new AssertionError("random site outside shifted border: " + Arrays.toString(p));
            unique.add(p[0] + "," + p[1]);
        }
        if (unique.size() < 20) throw new AssertionError("random sampling collapsed to one point");
        injectLoadedSite(FAR, FAR);
    }

    @SuppressWarnings("unchecked") private void injectLoadedSite(int x, int z) throws Exception {
        List<int[]> candidates = (List<int[]>)field(job, "loadedCandidates").get(job);
        candidates.clear(); candidates.add(new int[]{x, z});
    }

    private Map<?,?> rejections() throws Exception { return (Map<?,?>)field(job, "rejections").get(job); }
    private boolean rejected(String name) throws Exception { return rejections().containsKey(name); }
    private void requireSearching(String what) throws Exception {
        if (field(events, "pendingSearch").get(events) == null) throw new AssertionError(what + " search failed: " + rejections());
    }
    private BossEventManager.ActiveBossEvent onlyEvent() {
        List<BossEventManager.ActiveBossEvent> active = events.getActiveEvents();
        if (active.size() > 1) throw new AssertionError("unexpected multiple events");
        return active.isEmpty() ? null : active.get(0);
    }

    private void putChest(int x, int y, int z, int diamonds) {
        world.getBlockAt(x,y,z).setType(Material.CHEST, false);
        ((Chest)world.getBlockAt(x,y,z).getState()).getBlockInventory().setItem(0, new ItemStack(Material.DIAMOND, diamonds));
    }
    private void assertChest(int x, int y, int z, int diamonds) {
        if (!(world.getBlockAt(x,y,z).getState() instanceof Chest)) throw new AssertionError("foreign chest removed");
        ItemStack item = ((Chest)world.getBlockAt(x,y,z).getState()).getBlockInventory().getItem(0);
        if (item == null || item.getType() != Material.DIAMOND || item.getAmount() != diamonds) throw new AssertionError("foreign chest contents changed");
    }

    private List<int[]> footprint(int x, int z) {
        List<int[]> out = new ArrayList<int[]>();
        for (int cx=(x-9)>>4; cx<=(x+9)>>4; cx++) for (int cz=(z-9)>>4; cz<=(z+9)>>4; cz++) out.add(new int[]{cx,cz});
        return out;
    }
    private void loadFootprint(int x, int z, Runnable ready) throws Exception { loadOne(footprint(x,z), 0, ready); }
    private void loadOne(List<int[]> chunks, int i, Runnable ready) throws Exception {
        if (i == chunks.size()) { ready.run(); return; }
        int[] pos = chunks.get(i);
        Method async = World.class.getMethod("getChunkAtAsync", int.class, int.class, boolean.class);
        @SuppressWarnings("unchecked") CompletableFuture<Object> f = (CompletableFuture<Object>)async.invoke(world, pos[0], pos[1], true);
        f.whenComplete((chunk, error) -> {
            if (!isEnabled()) return;
            Bukkit.getScheduler().runTask(this, () -> {
                try {
                    if (error != null || chunk == null || !world.isChunkLoaded(pos[0],pos[1])) throw new AssertionError("fixture load failed", error);
                    world.addPluginChunkTicket(pos[0],pos[1],this);
                    loadOne(chunks, i+1, ready);
                } catch (Throwable failure) { fail(failure); }
            });
        });
    }

    /** Build test terrain in short tick-sized batches, never via sync chunk generation. */
    private void prepare(int x, int z, boolean slope, boolean obstacle, Runnable ready) {
        final int x0=((x-9)>>4)*16, z0=((z-9)>>4)*16;
        new BukkitRunnable() {
            int column;
            @Override public void run() {
                try {
                    int end=Math.min(1024,column+48);
                    while (column<end) {
                        int bx=x0+column/32, bz=z0+column%32; column++;
                        for (int y=60; y<=63; y++) world.getBlockAt(bx,y,bz).setType(Material.DIRT,false);
                        world.getBlockAt(bx,64,bz).setType(Material.GRASS_BLOCK,false);
                        for (int y=65; y<=80; y++) world.getBlockAt(bx,y,bz).setType(Material.AIR,false);
                        if (slope && bx<=x0+10) world.getBlockAt(bx,65,bz).setType(Material.GRASS_BLOCK,false);
                    }
                    if (column == 1024) {
                        cancel();
                        if (slope) {
                            world.getBlockAt(x0+5,66,z0+5).setType(Material.ALLIUM,false);
                            org.bukkit.block.Block leaf=world.getBlockAt(x0+5,67,z0+5);
                            leaf.setType(Material.OAK_LEAVES,false);
                            org.bukkit.block.data.type.Leaves data=(org.bukkit.block.data.type.Leaves)leaf.getBlockData();
                            data.setPersistent(false); data.setDistance(1); leaf.setBlockData(data,false);
                        }
                        if (obstacle) putChest(x0+30,65,z0+30,3);
                        ready.run();
                    }
                } catch (Throwable failure) { cancel(); fail(failure); }
            }
        }.runTaskTimer(this,1L,1L);
    }

    private static Field field(Object owner, String name) throws Exception {
        Field field=owner.getClass().getDeclaredField(name); field.setAccessible(true); return field;
    }
    private void fail(Throwable failure) {
        getLogger().severe("KG-EVENT-PLACEMENT FAIL: " + failure);
        failure.printStackTrace();
        Bukkit.shutdown();
    }
}
