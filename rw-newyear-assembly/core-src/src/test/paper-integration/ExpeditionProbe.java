package net.kinggrief.probe;

import net.kinggrief.core.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Actual Paper block breaks and kills, simulated fishing/movement events, real GUI click, durable claim tests. */
public final class ExpeditionProbe extends JavaPlugin implements Listener {
    private KingGriefCore core;
    private ExpeditionManager manager;
    private Player player;
    private World world;
    private Block ore;
    private int checks, errors;
    private boolean running, finished, cancelNextBreak;
    private static final Instant MONDAY=Instant.parse("2026-01-05T12:00:00Z");
    @Override public void onEnable() { Bukkit.getPluginManager().registerEvents(this,this); }
    @EventHandler public void join(PlayerJoinEvent e) { if(running)return; running=true;player=e.getPlayer();later(35,()->begin()); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void denyBreak(BlockBreakEvent e) {
        if(cancelNextBreak && e.getPlayer().equals(player)) { e.setCancelled(true);cancelNextBreak=false; }
    }
    private void later(long delay,Runnable r){Bukkit.getScheduler().runTaskLater(this,()->{try{r.run();}catch(Throwable t){fail(t);}},delay);}
    private void ok(boolean v,String text){checks++;if(!v){errors++;getLogger().severe("KG-EXP FAIL: "+text);}}
    private void fail(Throwable t){errors++;getLogger().severe("KG-EXP EXCEPTION: "+t);t.printStackTrace();finish();}
    private void finish(){if(finished)return;finished=true;getLogger().info("KG-EXP RESULT "+(errors==0?"PASS":"FAIL")+" checks="+checks+" errors="+errors);}
    private Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private void set(Object o,String name,Object value)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    private void clock(Instant instant)throws Exception{set(manager,"clock",Clock.fixed(instant,ZoneOffset.UTC));}
    private Object snapshot()throws Exception{Method m=manager.getClass().getDeclaredMethod("snapshot",Player.class);m.setAccessible(true);return m.invoke(manager,player);}
    private int progress(int index)throws Exception{return ((int[])field(snapshot(),"progress"))[index];}
    private boolean claimed(int index)throws Exception{return ((boolean[])field(snapshot(),"claimed"))[index];}
    private int weekly()throws Exception{return (Integer)field(snapshot(),"weekly");}
    private boolean flag(String name)throws Exception{return (Boolean)field(snapshot(),name);}
    @SuppressWarnings({"rawtypes","unchecked"}) private void completeBySimulation(String id)throws Exception{
        Class task=Class.forName("net.kinggrief.core.ExpeditionManager$Task");
        Object value=Enum.valueOf(task,id.toUpperCase(Locale.ROOT));
        Method m=manager.getClass().getDeclaredMethod("advance",Player.class,task,int.class);m.setAccessible(true);m.invoke(manager,player,value,1000);
    }
    private void begin(){
        try {
            core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");manager=core.getExpeditions();clock(MONDAY);
            world=Bukkit.getWorlds().get(0); world.getBlockAt(5,69,5).setType(Material.STONE,false);
            player.teleport(new Location(world,5.5,70,5.5));player.setGameMode(GameMode.SURVIVAL);
            player.getInventory().clear();player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_PICKAXE));
            ore=world.getBlockAt(6,70,5);
            later(30,()->exerciseEvents());
        } catch(Throwable t){fail(t);}
    }
    private void placeOre(Block b){
        b.setType(Material.DIAMOND_ORE,false);
        BlockPlaceEvent ev=new BlockPlaceEvent(b,b.getState(),b.getRelative(BlockFace.DOWN),new ItemStack(Material.DIAMOND_ORE),player,true,EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(ev);
    }
    private void exerciseEvents(){
        try {
            int start=progress(0);
            placeOre(ore);ok(player.breakBlock(ore),"player-placed ore can be broken");
            ok(progress(0)==start,"placed ore does not advance task");
            placeOre(ore);
            Block piston=world.getBlockAt(4,70,5);piston.setType(Material.PISTON,false);
            Block moved=ore.getRelative(BlockFace.EAST);
            BlockPistonExtendEvent move=new BlockPistonExtendEvent(piston,Arrays.asList(ore),BlockFace.EAST);
            Bukkit.getPluginManager().callEvent(move);
            ore.setType(Material.AIR,false);moved.setType(Material.DIAMOND_ORE,false);player.breakBlock(moved);
            ok(progress(0)==start,"piston-moved placed ore remains ineligible");
            ore.setType(Material.DIAMOND_ORE,false);cancelNextBreak=true;player.breakBlock(ore);
            ok(progress(0)==start && ore.getType()==Material.DIAMOND_ORE,"cancelled ore break is not counted");
            player.setGameMode(GameMode.CREATIVE);player.breakBlock(ore);player.setGameMode(GameMode.SURVIVAL);
            ok(progress(0)==start,"creative ore is not counted");
            for(int i=0;i<32;i++){ore.setType(Material.DIAMOND_ORE,false);player.breakBlock(ore);}
            ok(progress(0)==32,"32 actual accepted natural ore breaks counted and capped");
            Block crop=world.getBlockAt(7,70,5);crop.getRelative(BlockFace.DOWN).setType(Material.FARMLAND,false);
            crop.setType(Material.WHEAT,false);Ageable age=(Ageable)crop.getBlockData();age.setAge(1);crop.setBlockData(age,false);player.breakBlock(crop);
            ok(progress(1)==0,"unripe crop not counted");
            for(int i=0;i<20;i++){crop.setType(Material.WHEAT,false);age=(Ageable)crop.getBlockData();age.setAge(age.getMaximumAge());crop.setBlockData(age,false);player.breakBlock(crop);}
            ok(progress(1)==20,"20 mature crop breaks counted");
            Cow cow=(Cow)world.spawnEntity(new Location(world,8,70,5),EntityType.COW);cow.damage(100,player);
            ok(progress(2)==0,"passive animals do not count as monsters");
            for(int i=0;i<12;i++){Zombie z=(Zombie)world.spawnEntity(new Location(world,8,70,5),EntityType.ZOMBIE);z.damage(100,player);}
            ok(progress(2)==12,"12 actual hostile kills counted");
            Item junk=world.dropItem(new Location(world,8,71,5),new ItemStack(Material.BOWL));
            Bukkit.getPluginManager().callEvent(new PlayerFishEvent(player,junk,null,PlayerFishEvent.State.CAUGHT_FISH));junk.remove();
            ok(progress(3)==0,"fishing junk does not count as fish");
            Item fish=world.dropItem(new Location(world,8,71,5),new ItemStack(Material.COD));
            PlayerFishEvent cancelled=new PlayerFishEvent(player,fish,null,PlayerFishEvent.State.CAUGHT_FISH);cancelled.setCancelled(true);
            Bukkit.getPluginManager().callEvent(cancelled);ok(progress(3)==0,"cancelled fishing event ignored");
            for(int i=0;i<3;i++) Bukkit.getPluginManager().callEvent(new PlayerFishEvent(player,fish,null,PlayerFishEvent.State.CAUGHT_FISH));
            fish.remove();ok(progress(3)==3,"three caught-fish events counted");
            Location from=player.getLocation().clone(), teleport=from.clone().add(500,0,0);
            Bukkit.getPluginManager().callEvent(new PlayerTeleportEvent(player,from,teleport,PlayerTeleportEvent.TeleportCause.PLUGIN));
            ok(progress(4)==0,"teleport never counts as walking");
            for(int i=0;i<700;i++) Bukkit.getPluginManager().callEvent(new PlayerMoveEvent(player,from,from.clone().add(.9,0,0)));
            ok(progress(4)==600,"bounded walking steps count and stop at goal");
            core.getStore().setCoins(player.getName(),1000);core.getStore().addSnowflakes(player.getName(),-core.getStore().getSnowflakes(player.getName()));
            manager.open(player);later(20,()->{getLogger().info("KG-EXP REQUEST CLICK 10");later(16,()->afterGui());});
        }catch(Throwable t){fail(t);}
    }
    private void afterGui(){
        try{
            ok(claimed(0) && core.getStore().getCoins(player.getName())==4500 && core.getStore().getSnowflakes(player.getName())==3 && weekly()==1,
                    "real GUI click commits one ore reward and weekly progress");
            double before=core.getStore().getCoins(player.getName());
            later(12,()->{try{
                player.performCommand("expeditions claim ore");
                ok(core.getStore().getCoins(player.getName())==before && weekly()==1,"repeated claim cannot duplicate reward");
                later(12,()->testFailure());
            }catch(Throwable t){fail(t);}});
        }catch(Throwable t){fail(t);}
    }
    private void testFailure(){
        Path data=core.getDataFolder().toPath().resolve("data.yml"), backup=data.resolveSibling("probe-data-backup.yml");
        try{
            Files.move(data,backup);Files.createSymbolicLink(data,data.resolveSibling("probe-no-target.yml"));
            double before=core.getStore().getCoins(player.getName());
            player.performCommand("expeditions claim harvest");
            ok(core.getStore().getCoins(player.getName())==before && !claimed(1) && weekly()==1,"failed durable write rolls back money and claim marker");
        }catch(Throwable t){fail(t);}
        finally{try{Files.deleteIfExists(data);if(Files.exists(backup))Files.move(backup,data);}catch(Throwable t){fail(t);}}
        later(12,()->claimRemaining(new String[]{"harvest","hunt","fish","walk","bonus"},0));
    }
    private void claimRemaining(String[] ids,int index){
        try{
            if(index<ids.length){player.performCommand("expeditions claim "+ids[index]);later(12,()->claimRemaining(ids,index+1));return;}
            ok(weekly()==5 && flag("dailyBonus"),"all five daily rewards unlock the daily bonus");
            double afterDay=core.getStore().getCoins(player.getName());
            clock(MONDAY.plusSeconds(86400));
            ok(progress(0)==0 && weekly()==5 && !flag("dailyBonus"),"daily reset preserves weekly counter");
            for(String id:new String[]{"ore","harvest","hunt","fish","walk"}) completeBySimulation(id);
            claimSecondDay(new String[]{"ore","harvest","hunt","fish","walk","weekly"},0,afterDay);
        }catch(Throwable t){fail(t);}
    }
    private void claimSecondDay(String[] ids,int index,double afterDay){
        try{
            if(index<ids.length){player.performCommand("expeditions claim "+ids[index]);later(12,()->claimSecondDay(ids,index+1,afterDay));return;}
            ok(weekly()==10 && flag("weeklyBonus") && core.getStore().getCoins(player.getName())>afterDay+25000,
                    "10 daily claims unlock weekly reward exactly once");
            double before=core.getStore().getCoins(player.getName());
            player.performCommand("expeditions claim weekly");ok(core.getStore().getCoins(player.getName())==before,"weekly reward not duplicated");
            core.getStore().load();ok(weekly()==10 && flag("weeklyBonus") && core.getStore().getCoins(player.getName())==before,
                    "durable rewards survive a DataStore reload");
            clock(MONDAY);ok(flag("clockError"),"backwards clock fails closed");
            clock(MONDAY.plusSeconds(7*86400));ok(weekly()==0 && !flag("weeklyBonus"),"next UTC week resets weekly progress");
            finish();
        }catch(Throwable t){fail(t);}
    }
}
