package net.kinggrief.probe;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import net.kinggrief.core.BossEventManager;
import net.kinggrief.core.KingGriefCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Integration against real WorldEdit and WorldGuard, not the API double. */
public final class RealWorldGuardProbe extends JavaPlugin {
    private static final int X=422, Z=422, Y=65;
    private KingGriefCore core;
    private BossEventManager events;
    private World world;

    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");
                if (core==null || !core.isEnabled() || !Bukkit.getPluginManager().isPluginEnabled("WorldGuard")
                        || !Bukkit.getPluginManager().isPluginEnabled("WorldEdit")) throw new AssertionError("required plugins not enabled");
                events=core.getBossEvents(); world=Bukkit.getWorld("world");
                List<int[]> chunks=new ArrayList<int[]>();
                for (int cx=(X-9)>>4; cx<=(X+9)>>4; cx++) for (int cz=(Z-9)>>4; cz<=(Z+9)>>4; cz++) chunks.add(new int[]{cx,cz});
                load(chunks,0,() -> {
                    try { check(chunks); } catch (Throwable failure) { fail(failure); }
                });
            } catch (Throwable failure) { fail(failure); }
        },30L);
    }

    private void check(List<int[]> chunks) throws Exception {
        RegionManager manager=WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
        if (manager==null) throw new AssertionError("real WG region manager unavailable");
        // Only a corner of the footprint overlaps; checking the center alone
        // would miss this region. A chest proves it remains untouched.
        String id="kg_real_wg_probe";
        manager.addRegion(new ProtectedCuboidRegion(id,BlockVector3.at(X+9,Y-2,Z+9),BlockVector3.at(X+13,Y+5,Z+13)));
        Location center=new Location(world,X,Y,Z);
        Location rtpProtected=new Location(world,X+9,Y,Z+9);
        if (rtpSafe(rtpProtected)) throw new AssertionError("RTP accepted a physical WG region");
        if (!rtpSafe(center)) throw new AssertionError("RTP rejected an unprotected point outside the region");
        if (events.spawnBossEvent("camp",center)!=null) throw new AssertionError("real WG corner region was ignored");
        world.getBlockAt(X+9,Y,Z+9).setType(Material.CHEST,false);
        ((Chest)world.getBlockAt(X+9,Y,Z+9).getState()).getBlockInventory().setItem(0,new ItemStack(Material.DIAMOND,4));
        if (events.spawnBossEvent("camp",center)!=null) throw new AssertionError("real WG protected chest was ignored");
        if (!(world.getBlockAt(X+9,Y,Z+9).getState() instanceof Chest)
                || ((Chest)world.getBlockAt(X+9,Y,Z+9).getState()).getBlockInventory().getItem(0).getAmount()!=4)
            throw new AssertionError("protected chest changed");
        manager.removeRegion(id);
        if (!rtpSafe(rtpProtected)) throw new AssertionError("RTP rejected the same point after region removal");
        world.getBlockAt(X+9,Y,Z+9).setType(Material.AIR,false);
        BossEventManager.ActiveBossEvent event=events.spawnBossEvent("camp",center);
        if (event==null) throw new AssertionError("real WG rejected a clear site");
        events.endEvent(event.instanceId,false);
        Bukkit.getPluginManager().disablePlugin(Bukkit.getPluginManager().getPlugin("WorldGuard"));
        if (events.spawnBossEvent("camp",center)!=null) throw new AssertionError("disabled real WG did not fail closed");
        if (rtpSafe(center)) throw new AssertionError("RTP did not fail closed with disabled WG");
        for (int[] pos:chunks) {
            if (world.getPluginChunkTickets(pos[0],pos[1]).contains(core)) throw new AssertionError("event ticket leaked");
            world.removePluginChunkTicket(pos[0],pos[1],this);
        }
        getLogger().info("KG-REAL-WG PASS: WorldGuard 7.0.13 and WorldEdit 7.3.10; corner overlap and RTP point protected; clear site allowed; disabled WG fail-closed");
        Bukkit.shutdown();
    }

    private boolean rtpSafe(Location at) throws Exception {
        Method method=BossEventManager.class.getDeclaredMethod("isRtpSiteOutsideWorldGuard",Location.class);
        method.setAccessible(true);return (Boolean)method.invoke(events,at);
    }

    private void load(List<int[]> chunks,int i,Runnable ready) throws Exception {
        if (i==chunks.size()) {ready.run(); return;}
        int[] pos=chunks.get(i);
        Method async=World.class.getMethod("getChunkAtAsync",int.class,int.class,boolean.class);
        @SuppressWarnings("unchecked") CompletableFuture<Object> future=(CompletableFuture<Object>)async.invoke(world,pos[0],pos[1],true);
        future.whenComplete((chunk,error) -> {
            if (!isEnabled()) return;
            Bukkit.getScheduler().runTask(this,() -> {
                try {
                    if (error!=null || chunk==null || !world.isChunkLoaded(pos[0],pos[1])) throw new AssertionError("fixture load failed",error);
                    world.addPluginChunkTicket(pos[0],pos[1],this);
                    load(chunks,i+1,ready);
                } catch (Throwable failure) {fail(failure);}
            });
        });
    }
    private void fail(Throwable failure) {
        getLogger().severe("KG-REAL-WG FAIL: "+failure);
        failure.printStackTrace();
        Bukkit.shutdown();
    }
}
