package net.kinggrief.probe;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import net.kinggrief.core.ItemFactory;
import net.kinggrief.core.KingGriefCore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Integration with the real WorldGuard/WorldEdit Bukkit plugins. */
public final class RealWorldGuardMiningProbe extends JavaPlugin implements Listener {
    private boolean started,finished;private Player player;private World world;private RegionManager regions;
    private KingGriefCore core;private int checks,errors;
    @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);}
    @EventHandler public void join(PlayerJoinEvent e){if(!started&&Bukkit.getOnlinePlayers().size()>=2){started=true;later(40,this::begin);}}
    private void later(long ticks,Runnable run){Bukkit.getScheduler().runTaskLater(this,()->{try{run.run();}catch(Throwable e){fail(e);}},ticks);}
    private void ok(boolean value,String text){checks++;if(value)getLogger().info("KG-MINE-WG PASS "+text);else{errors++;getLogger().severe("KG-MINE-WG FAIL "+text);}}
    private void fail(Throwable e){errors++;getLogger().severe("KG-MINE-WG EXCEPTION "+e);e.printStackTrace();finish();}
    private void finish(){if(finished)return;finished=true;getLogger().info("KG-MINE-WG RESULT "+(errors==0?"PASS":"FAIL")+" checks="+checks+" errors="+errors);Bukkit.shutdown();}
    private void protect(String id,int x,int y,int z){BlockVector3 pos=BlockVector3.at(x,y,z);regions.addRegion(new ProtectedCuboidRegion(id,pos,pos));}
    private void begin(){
        core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");player=Bukkit.getPlayerExact("EcoBuyer");world=Bukkit.getWorld("world");
        if(player==null||world==null)throw new AssertionError("missing player or world");
        regions=WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
        if(regions==null)throw new AssertionError("real WorldGuard manager unavailable");
        player.setOp(false);player.setGameMode(GameMode.SURVIVAL);player.teleport(new Location(world,10.5,161,10.5,0f,90f));player.getInventory().clear();player.getInventory().setItemInMainHand(ItemFactory.pickKrush());
        Block center=world.getBlockAt(10,160,10),guarded=world.getBlockAt(11,160,10),free=world.getBlockAt(9,160,10);
        center.setType(Material.STONE,false);guarded.setType(Material.STONE,false);free.setType(Material.STONE,false);protect("kg_guard_neighbour",11,160,10);
        boolean accepted=player.breakBlock(center);later(4,()->{
            ok(accepted && center.getType().isAir() && free.getType().isAir(),"unprotected central and neighbour blocks break");
            ok(guarded.getType()==Material.STONE,"real WorldGuard protects a neighbouring drilled block");
            centralCancel();
        });
    }
    private void centralCancel(){
        player.teleport(new Location(world,20.5,161,20.5,0f,90f));player.getInventory().setItemInMainHand(ItemFactory.pickMidas());
        Block center=world.getBlockAt(20,160,20);center.setType(Material.STONE,false);protect("kg_guard_center",20,160,20);
        double coins=core.getStore().getCoins(player.getName());boolean accepted=player.breakBlock(center);later(4,()->{
            ok(!accepted && center.getType()==Material.STONE && core.getStore().getCoins(player.getName())==coins,"real WorldGuard cancellation prevents central-break rewards");
            frost();
        });
    }
    private void frost(){
        player.teleport(new Location(world,30.5,161,30.5,0f,90f));player.getInventory().setItemInMainHand(ItemFactory.pickFrost());
        Block center=world.getBlockAt(30,160,30),guarded=world.getBlockAt(31,160,30),free=world.getBlockAt(29,160,30);
        center.setType(Material.STONE,false);guarded.setType(Material.WATER,false);free.setType(Material.WATER,false);protect("kg_guard_liquid",31,160,30);
        player.breakBlock(center);later(4,()->{
            ok(guarded.getType()==Material.WATER,"real WorldGuard prevents protected frost placement");
            ok(free.getType()==Material.PACKED_ICE,"unprotected frost placement still works");finish();
        });
    }
}
