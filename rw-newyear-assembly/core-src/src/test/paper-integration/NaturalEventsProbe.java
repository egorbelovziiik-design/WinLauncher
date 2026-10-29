package net.kinggrief.probe;

import net.kinggrief.core.BossEventManager;
import net.kinggrief.core.KingGriefCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

/** End-to-end searches in an ordinary generated Overworld: no forced sites. */
public final class NaturalEventsProbe extends JavaPlugin {
    private KingGriefCore core;
    private BossEventManager events;
    private World world;
    private Object job;
    private int stage, ticks, heartbeats;
    private long previousNanos, largestGapNanos;

    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                core = (KingGriefCore) Bukkit.getPluginManager().getPlugin("KingGriefCore");
                if (core == null || !core.isEnabled()) throw new AssertionError("Core not enabled");
                events = core.getBossEvents();
                world = Bukkit.getWorld("world");
                if (world == null) throw new AssertionError("normal world missing");
                core.getConfig().set("events.auto-enabled", false);
                core.getConfig().set("events.allow-chunk-generation", true);
                core.getConfig().set("events.search-radius-blocks", 2500);
                ((Random)field(events,"rng").get(events)).setSeed(Long.getLong("kg.search.seed", 46819235L));
                if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "events start airship")) throw new AssertionError("console command not dispatched");
                job = field(events,"pendingSearch").get(events);
                if (job == null || field(job,"world").get(job) != world) throw new AssertionError("manual command did not start Overworld search");
                stage = 1;
                previousNanos = System.nanoTime();
                new BukkitRunnable() {
                    @Override public void run() {
                        try {
                            long now = System.nanoTime();
                            largestGapNanos = Math.max(largestGapNanos, now - previousNanos);
                            previousNanos = now;
                            heartbeats++;
                            if (++ticks > 5000) throw new AssertionError("natural event probe timeout stage="+stage);
                            advance();
                        } catch (Throwable error) { cancel(); fail(error); }
                    }
                }.runTaskTimer(this,1L,1L);
            } catch (Throwable error) { fail(error); }
        },30L);
    }

    private void advance() throws Exception {
        if (stage == 1 || stage == 2) {
            BossEventManager.ActiveBossEvent found = null;
            for (BossEventManager.ActiveBossEvent ev : events.getActiveEvents()) {
                if (ev.center.getWorld().equals(world)) {
                    if (found != null) throw new AssertionError("unexpected multiple events");
                    found = ev;
                }
            }
            if (found == null) {
                if (field(events,"pendingSearch").get(events) == null)
                    throw new AssertionError((stage == 1 ? "manual" : "automatic") + " normal-world search failed; rejected=" + field(job,"rejections").get(job));
                return;
            }
            if (stage == 1 && !"airship".equals(found.template.id)) throw new AssertionError("manual command spawned wrong event");
            if (heartbeats < 5 || largestGapNanos > 9_000_000_000L)
                throw new AssertionError("heartbeat stalled during search: " + largestGapNanos / 1_000_000L + "ms");
            int asyncRequests = field(job,"asyncChunkRequests").getInt(job);
            if (asyncRequests > 64) throw new AssertionError("async chunk request budget exceeded: " + asyncRequests);
            Location center = found.center.clone(), spawn = world.getSpawnLocation();
            if (Math.abs(center.getBlockX()-spawn.getBlockX()) < 180 && Math.abs(center.getBlockZ()-spawn.getBlockZ()) < 180)
                throw new AssertionError("event too close to world spawn");
            BlockState original = found.savedBlocks.get(center);
            if (original == null) throw new AssertionError("event did not save center block");
            BlockData before = original.getBlockData().clone();
            getLogger().info("KG-NATURAL " + (stage == 1 ? "MANUAL" : "AUTO") + " FOUND: " + center.getBlockX()+","+center.getBlockY()+","+center.getBlockZ()
                    + " loadedAreas=" + field(job,"loadedIndex").get(job) + " generatedAreas=" + field(job,"generatedCandidates").get(job)
                    + " asyncRequests=" + asyncRequests + " checkedSites=" + field(job,"checkedSites").get(job) + " rejected=" + field(job,"rejections").get(job));
            events.endEvent(found.instanceId,false);
            java.util.Collection<org.bukkit.Chunk> remaining = world.getPluginChunkTickets().get(core);
            if (remaining != null && !remaining.isEmpty()) throw new AssertionError("normal-world search leaked " + remaining.size() + " chunk tickets");
            if (!center.getBlock().getBlockData().equals(before)) throw new AssertionError("normal-world center block not restored");
            for (int cx=(center.getBlockX()-9)>>4; cx<=(center.getBlockX()+9)>>4; cx++)
                for (int cz=(center.getBlockZ()-9)>>4; cz<=(center.getBlockZ()+9)>>4; cz++)
                    if (world.getPluginChunkTickets(cx,cz).contains(core)) throw new AssertionError("normal-world event ticket leaked");
            if (stage == 1) {
                core.getConfig().set("events.auto-enabled", true);
                field(events,"autoCountdownSec").setInt(events,1);
                Method tick=BossEventManager.class.getDeclaredMethod("tickSecond"); tick.setAccessible(true); tick.invoke(events);
                job=field(events,"pendingSearch").get(events);
                if (job == null || field(job,"world").get(job) != world) throw new AssertionError("automatic Overworld search not queued");
                stage=2; ticks=0; heartbeats=0; largestGapNanos=0;
                return;
            }
            getLogger().info("KG-NATURAL-EVENTS PASS: unforced console airship and automatic events completed on generated Overworld; heartbeat, terrain restoration and tickets checked");
            stage=3;
            Bukkit.shutdown();
        }
    }

    private static Field field(Object owner, String name) throws Exception {
        Field f=owner.getClass().getDeclaredField(name); f.setAccessible(true); return f;
    }
    private void fail(Throwable failure) {
        getLogger().severe("KG-NATURAL-EVENTS FAIL: " + failure);
        failure.printStackTrace();
        Bukkit.shutdown();
    }
}
