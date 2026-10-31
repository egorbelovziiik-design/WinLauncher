package net.kinggrief.probe;

import net.kinggrief.core.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Field;
import java.util.Map;

/** Simulate an unavailable prepared spawn without generating terrain. */
public final class RtpNoWorldProbe extends JavaPlugin implements Listener {
    private boolean ran;
    @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);}
    @EventHandler public void join(PlayerJoinEvent e){
        if(ran)return;ran=true;Player p=e.getPlayer();Bukkit.getScheduler().runTaskLater(this,()->{
            try{
                KingGriefCore core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");
                if(Bukkit.getWorld("world")!=null)throw new AssertionError("unexpected pre-existing world");
                World main=Bukkit.getWorld("spawn_world"), prepared=Bukkit.getWorld("kinggrief_spawn");
                if(main==null || prepared==null || !core.getSpawnEngine().isSpawnActive() || main.equals(prepared))
                    throw new AssertionError("main world and prepared fallback were not safely separated");
                if(!p.teleport(main.getSpawnLocation()))throw new AssertionError("could not move player to main-world fixture");
                Field active=SpawnEngine.class.getDeclaredField("spawnActive");active.setAccessible(true);active.setBoolean(core.getSpawnEngine(),false);
                if(!Bukkit.unloadWorld(prepared,false))throw new AssertionError("could not unload prepared fallback fixture");
                java.lang.reflect.Method select=VoidSpawnWorldGenerator.class.getDeclaredMethod("setSpawnWorldName",String.class);
                select.setAccessible(true);select.invoke(null,"spawn_world");
                core.getSpawnEngine().performRtp(p,"close");
                Field f=KingGriefCore.class.getDeclaredField("rtpManager");f.setAccessible(true);Object manager=f.get(core);
                Field jobs=manager.getClass().getDeclaredField("jobs");jobs.setAccessible(true);
                if(Bukkit.getWorld("world")!=null || !((Map<?,?>)jobs.get(manager)).isEmpty())throw new AssertionError("RTP created a world or started a search");
                getLogger().info("KG-NOWORLD PASS missing survival world is reported without synchronous creation");

                World w=p.getWorld();Location original=p.getLocation().clone();
                w.setSpawnLocation(original);
                Location loaded=core.getSpawnEngine().getSpawnArrival();
                if(loaded==null || loaded.getWorld()!=w || loaded.getBlockX()!=original.getBlockX() || loaded.getBlockZ()!=original.getBlockZ())
                    throw new AssertionError("loaded vanilla spawn unavailable");
                getLogger().info("KG-NOWORLD PASS loaded vanilla spawn remains available");

                WorldBorder border=w.getWorldBorder();border.setSize(16);border.setCenter(1_000_000,1_000_000);
                int cx=1_000_000>>4,cz=1_000_000>>4;
                if(w.isChunkLoaded(cx,cz))throw new AssertionError("distant border fixture unexpectedly loaded");
                long start=System.nanoTime();
                if(core.getSpawnEngine().getSpawnArrival()!=null || w.isChunkLoaded(cx,cz))throw new AssertionError("fallback generated distant border chunk");
                if((System.nanoTime()-start)>5_000_000_000L)throw new AssertionError("fallback was not bounded");
                getLogger().info("KG-NOWORLD PASS moved border refuses unloaded center without generation");

                p.performCommand("spawn");
                if(w.isChunkLoaded(cx,cz) || p.getLocation().distanceSquared(original)>0.1)throw new AssertionError("/spawn loaded distant chunk or moved player");
                core.getGuis().openMainMenu(p);
                InventoryClickEvent click=new InventoryClickEvent(p.getOpenInventory(),InventoryType.SlotType.CONTAINER,31,ClickType.LEFT,InventoryAction.PICKUP_ALL);
                Bukkit.getPluginManager().callEvent(click);
                if(w.isChunkLoaded(cx,cz) || p.getLocation().distanceSquared(original)>0.1)throw new AssertionError("spawn GUI loaded distant chunk or moved player");
                getLogger().info("KG-NOWORLD PASS unavailable /spawn and GUI leave player in place");

                ItemStack orb=ItemFactory.orbRecall(2);p.getInventory().setItemInMainHand(orb);
                PlayerInteractEvent use=new PlayerInteractEvent(p,Action.RIGHT_CLICK_AIR,orb,null,BlockFace.SELF,EquipmentSlot.HAND);
                Bukkit.getPluginManager().callEvent(use);
                ItemStack remaining=p.getInventory().getItemInMainHand();
                if(remaining==null || remaining.getAmount()!=2 || w.isChunkLoaded(cx,cz))throw new AssertionError("unavailable recall consumed orb or loaded distant chunk");
                getLogger().info("KG-NOWORLD PASS unavailable recall preserves the item and does not load terrain");

                border.setCenter(original.getX(),original.getZ());border.setSize(60_000_000);
                if(core.getSpawnEngine().getSpawnArrival()==null)throw new AssertionError("spawn did not recover after border restored");
                getLogger().info("KG-NOWORLD RESULT PASS missing world, safe loaded spawn and moved-border fallback");
            }catch(Throwable t){getLogger().severe("KG-NOWORLD RESULT FAIL "+t);t.printStackTrace();}
        },20L);
    }
}
