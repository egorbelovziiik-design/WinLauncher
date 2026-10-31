package net.kinggrief.probe;

import net.kinggrief.core.DataStore;
import net.kinggrief.core.KingGriefCore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Test-only plugin. First exercises event edge cases, then observes a real physics client. */
public final class ParkourProbe extends JavaPlugin implements Listener {
    private KingGriefCore core;
    private World world;
    private int checks;
    private boolean prepared, done;
    private boolean[] realStages = new boolean[5];
    private double baseCoins;
    private int baseRelics, baseSnow;
    private static final int[][] PADS = {{39,101,-29},{12,105,-43},{-18,109,-40},{-39,113,-18},{-41,117,9}};

    @Override public void onEnable() {
        core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");
        Bukkit.getPluginManager().registerEvents(this,this);
    }
    @EventHandler public void join(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { edgeCases(event.getPlayer()); }
            catch(Throwable failure) { fail(failure); }
        }, 50L);
    }
    private void ok(boolean yes, String message) {
        if(!yes) throw new AssertionError(message);
        checks++; getLogger().info("KG-PARKOUR PASS " + message);
    }
    private void fail(Throwable failure) {
        if(done) return; done=true;
        getLogger().severe("KG-PARKOUR RESULT FAIL " + failure); failure.printStackTrace();
    }
    private Block plate(int stage) {
        int[] p=PADS[stage]; return world.getBlockAt(p[0],p[1]+1,p[2]);
    }
    private void press(Player player, Block block, boolean cancelled, boolean denied) {
        PlayerInteractEvent event=new PlayerInteractEvent(player, Action.PHYSICAL, null, block, BlockFace.SELF);
        if(cancelled) event.setCancelled(true);
        if(denied) event.setUseInteractedBlock(Event.Result.DENY);
        Bukkit.getPluginManager().callEvent(event);
    }
    private boolean cpIs(DataStore store, Player p, int stage) {
        Location loc=store.getParkourCp(p); int[] c=PADS[stage];
        return loc!=null && loc.getWorld().equals(world) && Math.abs(loc.getX()-(c[0]+.5))<.01
                && Math.abs(loc.getY()-(c[1]+1.125))<.01 && Math.abs(loc.getZ()-(c[2]+.5))<.01;
    }
    private void edgeCases(Player p) {
        DataStore store=core.getStore();
        world=core.getSpawnEngine().getPreparedSpawnArrival().getWorld();
        p.teleport(core.getSpawnEngine().getPreparedSpawnArrival());
        p.setGameMode(GameMode.SURVIVAL);
        String name=p.getName();
        double coins=store.getCoins(name); int relics=store.getRelics(name), snow=store.getSnowflakes(name);
        long ready=store.getParkourRewardReadyAt(name);
        store.setParkourRewardReadyAt(name,0L);

        press(p,plate(4),false,false);
        ok(store.getParkourCp(p)==null && store.getCoins(name)==coins && store.getParkourRewardReadyAt(name)==0L,
                "unstarted finish cannot give a reward");
        Block unrelated=world.getBlockAt(30,145,20); Material old=unrelated.getType();
        try {
            unrelated.setType(Material.LIGHT_WEIGHTED_PRESSURE_PLATE,false);
            press(p,unrelated,false,false);
            ok(store.getParkourCp(p)==null,"unrelated high plate does not start or change a run");
        } finally { unrelated.setType(old,false); }
        press(p,plate(0),true,false);
        press(p,plate(0),false,true);
        ok(store.getParkourCp(p)==null,"cancelled or denied plate interaction cannot start");

        p.setAllowFlight(true);
        press(p,plate(0),false,false);
        ok(cpIs(store,p,0) && !p.getAllowFlight(),"start captures flight and creates a safe start checkpoint");
        press(p,plate(0),false,false);
        ok(cpIs(store,p,0),"standing on another start plate does not reset the run");
        press(p,plate(2),false,false); press(p,plate(4),false,false);
        ok(cpIs(store,p,0) && store.getCoins(name)==coins,"skipped checkpoints and premature finish are refused");
        press(p,plate(1),true,false);
        ok(cpIs(store,p,0),"cancelled checkpoint does not advance");
        press(p,plate(1),false,false);
        ok(cpIs(store,p,1),"first checkpoint advances in order");
        press(p,plate(3),false,false);
        ok(cpIs(store,p,1),"third checkpoint cannot bypass second checkpoint");
        Location checkpoint=store.getParkourCp(p).clone();
        ok(p.performCommand("parkour cp") && cpIs(store,p,1)
                && p.getWorld().equals(world) && p.getLocation().distanceSquared(checkpoint)<.01,
                "checkpoint command teleports without ending the run");
        PlayerTeleportEvent denied = new PlayerTeleportEvent(p,p.getLocation(),core.getSpawnEngine().getPreparedSpawnArrival(),PlayerTeleportEvent.TeleportCause.PLUGIN);
        denied.setCancelled(true); Bukkit.getPluginManager().callEvent(denied);
        ok(cpIs(store,p,1),"cancelled teleport does not discard progress");
        p.teleport(core.getSpawnEngine().getPreparedSpawnArrival());
        ok(store.getParkourCp(p)==null && p.getAllowFlight(),"successful teleport away ends run and restores flight");

        p.setFlying(true); press(p,plate(0),false,false);
        ok(!p.getAllowFlight() && !p.isFlying(),"flight is disabled during a survival run");
        ok(p.performCommand("parkour leave") && store.getParkourCp(p)==null && p.getAllowFlight() && p.isFlying(),
                "leave clears run and restores original flight state");
        p.setFlying(false); p.setAllowFlight(false);
        press(p,plate(0),false,false);press(p,plate(1),false,false);press(p,plate(2),false,false);press(p,plate(3),false,false);press(p,plate(4),false,false);
        ok(store.getParkourCp(p)==null && store.getCoins(name)==coins+25000.0
                && store.getRelics(name)==relics+15 && store.getSnowflakes(name)==snow+15,
                "ordered finish awards exactly 25000 coins, 15 relics and 15 snowflakes");
        long rewardAt=store.getParkourRewardReadyAt(name);
        ok(rewardAt>System.currentTimeMillis()+890_000L,"reward cooldown is 15 minutes");
        press(p,plate(0),false,false);press(p,plate(1),false,false);press(p,plate(2),false,false);press(p,plate(3),false,false);press(p,plate(4),false,false);
        ok(store.getCoins(name)==coins+25000.0 && store.getRelics(name)==relics+15
                && store.getSnowflakes(name)==snow+15 && store.getParkourRewardReadyAt(name)==rewardAt,
                "second finish inside cooldown does not duplicate a reward");
        store.setCoins(name,coins);store.setRelics(name,relics);store.addSnowflakes(name,snow-store.getSnowflakes(name));
        store.setParkourRewardReadyAt(name,0L);
        baseCoins=coins;baseRelics=relics;baseSnow=snow;
        p.setFlying(false);p.setAllowFlight(false);
        prepared=true;
        p.teleport(new Location(world,39.5,103.0,-28.5,0f,0f));
        p.sendMessage("KG_PARKOUR_PROBE_READY");
        getLogger().info("KG-PARKOUR EDGE CASES PASS checks="+checks);
        // `ready` is only meaningful outside this isolated throwaway server.
        if(ready!=0L) getLogger().info("Test reset a prior test-fixture cooldown");
    }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void physical(PlayerInteractEvent e) {
        if(!prepared || done || e.getAction()!=Action.PHYSICAL || e.getClickedBlock()==null) return;
        Block b=e.getClickedBlock();
        for(int i=0;i<PADS.length;i++) {
            int[] c=PADS[i];
            if(b.getWorld().equals(world) && b.getY()==c[1]+1 && Math.abs(b.getX()-c[0])<=1 && Math.abs(b.getZ()-c[2])<=1) {
                realStages[i]=true;
                getLogger().info("KG-PARKOUR REAL_PLATE " + i + " at " + b.getX()+","+b.getY()+","+b.getZ());
                if(i==4) Bukkit.getScheduler().runTaskLater(this,()->checkRealFinish(e.getPlayer()),2L);
                return;
            }
        }
    }
    private void checkRealFinish(Player p) {
        if(done) return;
        try {
            DataStore store=core.getStore();
            for(int i=0;i<realStages.length;i++) ok(realStages[i],"real player triggered stage " + i);
            ok(store.getParkourCp(p)==null && store.getParkourRewardReadyAt(p.getName())>System.currentTimeMillis(),
                    "real player completed run and received cooldown");
            ok(store.getCoins(p.getName())==baseCoins+25000.0 && store.getRelics(p.getName())==baseRelics+15
                    && store.getSnowflakes(p.getName())==baseSnow+15,"real player received complete reward");
            done=true; p.sendMessage("KG_PARKOUR_REAL_FINISH_OK");
            getLogger().info("KG-PARKOUR RESULT PASS checks="+checks);
        } catch(Throwable failure) { fail(failure); }
    }
}
