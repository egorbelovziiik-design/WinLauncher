package net.kinggrief.probe;

import net.kinggrief.core.DataStore;
import net.kinggrief.core.ItemFactory;
import net.kinggrief.core.KingGriefCore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;

/** Checks the built-in claim center with real breakBlock calls and late listeners. */
public final class ClaimBreakProbe extends JavaPlugin implements Listener {
    private KingGriefCore core;private Player a,b;private World world;private Block center;
    private boolean started,done,cancelBreak,suppressEarly,restoreLate,cancelClaimSpawn,cancelPlace,failAfterCreate,changePlacedBlock;private int checks,placeEvents;
    private Path data,backup;
    @Override public void onEnable(){saveDefaultConfig();core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");Bukkit.getPluginManager().registerEvents(this,this);}
    @EventHandler public void join(PlayerJoinEvent e){
        if("EcoBuyer".equals(e.getPlayer().getName()))a=e.getPlayer();if("EcoSeller".equals(e.getPlayer().getName()))b=e.getPlayer();
        if(a!=null&&b!=null&&!started){started=true;later(40,this::begin);}
    }
    @EventHandler(priority=EventPriority.LOW,ignoreCancelled=true) public void early(BlockBreakEvent e){if(suppressEarly&&e.getBlock().equals(center))e.setDropItems(false);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void protection(BlockBreakEvent e){
        if(e.getBlock().equals(center)){if(cancelBreak)e.setCancelled(true);if(restoreLate)e.setDropItems(true);}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void protectPlacement(org.bukkit.event.block.BlockPlaceEvent e){
        if(center!=null&&e.getBlockPlaced().equals(center)){
            placeEvents++;
            if(failAfterCreate)blockSave();
            if(changePlacedBlock)e.getBlockPlaced().setType(Material.STONE,false);
            if(cancelPlace)e.setCancelled(true);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void refuseItem(ItemSpawnEvent e){
        if(cancelClaimSpawn&&ItemFactory.getCustomId(e.getEntity().getItemStack())!=null
                &&ItemFactory.getCustomId(e.getEntity().getItemStack()).startsWith("claim_"))e.setCancelled(true);
    }
    private void later(long ticks,Runnable r){Bukkit.getScheduler().runTaskLater(this,()->{try{r.run();}catch(Throwable error){fail(error);}},ticks);}
    private void ok(boolean value,String msg){if(!value)throw new AssertionError(msg);checks++;getLogger().info("KG-CLAIM PASS "+msg);}
    private void fail(Throwable error){if(done)return;done=true;restore();getLogger().severe("KG-CLAIM RESULT FAIL "+error);error.printStackTrace();}
    private void finish(){done=true;restore();getLogger().info("KG-CLAIM RESULT PASS "+checks+" checks");}
    private void blockSave(){try{data=core.getDataFolder().toPath().resolve("data.yml");backup=data.resolveSibling("claim-test-backup.yml");Files.move(data,backup);Files.createSymbolicLink(data,backup.getFileName());}catch(Exception error){throw new RuntimeException(error);}}
    private void restore(){try{if(data!=null&&Files.isSymbolicLink(data))Files.delete(data);if(backup!=null&&Files.exists(backup))Files.move(backup,data);data=null;backup=null;}catch(Exception error){getLogger().severe("Test restore failed: "+error);}}
    private void setup(int x){
        a.setGameMode(GameMode.SURVIVAL);a.getInventory().clear();a.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_PICKAXE));
        a.teleport(new Location(world,x+0.5,161,50.5,0,90));center=world.getBlockAt(x,160,50);center.setType(Material.DIAMOND_ORE,false);
        core.getStore().createClaim(a.getName(),center.getLocation(),7);
    }
    private int claimItems(Location loc){int count=0;for(Entity e:loc.getWorld().getNearbyEntities(loc,8,8,8))if(e instanceof Item){
        ItemStack s=((Item)e).getItemStack();String id=ItemFactory.getCustomId(s);if("claim_7".equals(id))count+=s.getAmount();}return count;}
    private int ordinaryDiamonds(Location loc){int count=0;for(Entity e:loc.getWorld().getNearbyEntities(loc,8,8,8))if(e instanceof Item){
        ItemStack s=((Item)e).getItemStack();if(s.getType()==Material.DIAMOND&&!s.hasItemMeta())count+=s.getAmount();}return count;}
    private void clearItems(Location loc){for(Entity e:loc.getWorld().getNearbyEntities(loc,8,8,8))if(e instanceof Item)e.remove();}
    private void begin(){world=Bukkit.getWorlds().get(0);if(getConfig().getInt("phase",1)==2){restart();return;}setup(200);
        DataStore.Claim old=core.getStore().getClaimAt(center.getLocation());int items=claimItems(center.getLocation());cancelBreak=true;
        boolean broke=a.breakBlock(center);later(8,()->{
            ok(!broke&&center.getType()==Material.DIAMOND_ORE&&core.getStore().getClaimAt(center.getLocation())==old&&claimItems(center.getLocation())==items,
                    "late cancellation keeps claim, center block and custom item unchanged");
            cancelBreak=false;foreign();
        });
    }
    private void foreign(){
        DataStore.Claim old=core.getStore().getClaimAt(center.getLocation());b.teleport(new Location(world,203.5,161,50.5));b.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_PICKAXE));
        boolean broke=b.breakBlock(center);later(8,()->{
            ok(!broke&&center.getType()==Material.DIAMOND_ORE&&core.getStore().getClaimAt(center.getLocation())==old,"another player cannot break or remove the owner's claim");
            suppressEarly=true;boolean suppressed=a.breakBlock(center);later(8,()->{
                ok(!suppressed&&center.getType()==Material.DIAMOND_ORE&&core.getStore().getClaimAt(center.getLocation())==old&&claimItems(center.getLocation())==0,
                        "an earlier drop restriction prevents unsafe claim removal");suppressEarly=false;storageFailure();
            });
        });
    }
    private void storageFailure(){
        DataStore.Claim old=core.getStore().getClaimAt(center.getLocation());blockSave();a.breakBlock(center);later(8,()->{
            restore();ok(center.getType()==Material.DIAMOND_ORE&&core.getStore().getClaimAt(center.getLocation())==old&&claimItems(center.getLocation())==0,
                    "failed claim write restores the center and removes the uncommitted item");spawnFailure();
        });
    }
    private void spawnFailure(){
        DataStore.Claim old=core.getStore().getClaimAt(center.getLocation());cancelClaimSpawn=true;a.breakBlock(center);later(8,()->{
            cancelClaimSpawn=false;ok(center.getType()==Material.DIAMOND_ORE&&core.getStore().getClaimAt(center.getLocation())==old&&claimItems(center.getLocation())==0,
                    "cancelled item spawn restores the center without removing the claim");unload();
        });
    }
    private void unload(){
        DataStore.Claim old=core.getStore().getClaimAt(center.getLocation());a.breakBlock(center);
        Bukkit.getPluginManager().callEvent(new ChunkUnloadEvent(center.getChunk()));later(8,()->{
            ok(center.getType()==Material.DIAMOND_ORE&&core.getStore().getClaimAt(center.getLocation())==old&&claimItems(center.getLocation())==0,
                    "chunk unload before confirmation restores the pending claim center");approved();
        });
    }
    private void approved(){
        a.breakBlock(center);a.teleport(new Location(world,204.5,161,50.5));later(8,()->{
            ok(center.getType().isAir()&&core.getStore().getClaimAt(center.getLocation())==null&&claimItems(center.getLocation())==1,
                    "approved break durably removes the claim and returns exactly one custom claim block");clearItems(center.getLocation());restoredDrops();
        });
    }
    private void restoredDrops(){
        setup(230);restoreLate=true;a.breakBlock(center);a.teleport(new Location(world,234.5,161,50.5));later(8,()->{
            restoreLate=false;ok(center.getType().isAir()&&core.getStore().getClaimAt(center.getLocation())==null&&claimItems(center.getLocation())==1
                    &&ordinaryDiamonds(center.getLocation())==0,"late vanilla-drop restoration still returns one claim item without a normal diamond");
            clearItems(center.getLocation());cancelledPlacement();
        });
    }
    private void preparePlace(int x){
        world.getBlockAt(x,159,50).setType(Material.STONE,false);center=world.getBlockAt(x,160,50);center.setType(Material.AIR,false);
        a.getInventory().clear();a.getInventory().setHeldItemSlot(0);a.getInventory().setItemInMainHand(ItemFactory.claimBlock(Material.DIAMOND_ORE,7,"test claim"));
        a.setGameMode(GameMode.SURVIVAL);a.teleport(new Location(world,x+2.5,160,50.5,90,0));
    }
    private void placePacket(int x){getLogger().info("KG-CLAIM REQUEST PLACE "+x+" 159 50");}
    private void cancelledPlacement(){
        preparePlace(290);cancelPlace=true;int events=placeEvents;later(8,()->{placePacket(290);later(30,()->{
            cancelPlace=false;ItemStack hand=a.getInventory().getItemInMainHand();
            DataStore.Claim observed=core.getStore().getClaimAt(center.getLocation());
            ok(placeEvents>events&&center.getType().isAir()&&observed==null&&"claim_7".equals(ItemFactory.getCustomId(hand)),
                    "a real placement cancelled by a later plugin leaves no phantom claim and keeps the item (events="+(placeEvents-events)+", block="+center.getType()+", claim="+(observed==null?"none":observed.id)+", hand="+ItemFactory.getCustomId(hand)+")");
            failedRollback();
        });});
    }
    private void failedRollback(){
        preparePlace(300);cancelPlace=true;failAfterCreate=true;later(8,()->{placePacket(300);later(30,()->{
            cancelPlace=false;failAfterCreate=false;DataStore.Claim saved=core.getStore().getClaimAt(center.getLocation());
            ok(center.getType().isAir()&&saved!=null&&"claim_7".equals(ItemFactory.getCustomId(a.getInventory().getItemInMainHand())),
                    "if rollback cannot be saved, protection remains live and the cancelled item is retained");
            restore();later(110,()->{
                ok(core.getStore().getClaimAt(center.getLocation())==null,"cancelled placement rollback retries after storage becomes writable");
                failedPlacement();
            });
        });});
    }
    private void failedPlacement(){
        preparePlace(350);blockSave();later(8,()->{placePacket(350);later(30,()->{
            restore();ItemStack hand=a.getInventory().getItemInMainHand();
            ok(center.getType().isAir()&&core.getStore().getClaimAt(center.getLocation())==null&&"claim_7".equals(ItemFactory.getCustomId(hand)),
                    "failed claim creation leaves the placement and item untouched");
            changedPlacement();
        });});
    }
    private void changedPlacement(){
        preparePlace(380);changePlacedBlock=true;later(8,()->{placePacket(380);later(30,()->{
            changePlacedBlock=false;DataStore.Claim placed=core.getStore().getClaimAt(center.getLocation());
            ok(center.getType()==Material.STONE&&placed!=null&&a.getInventory().getItemInMainHand().getType().isAir(),
                    "an accepted placement changed by another plugin retains the paid protection");
            ok(core.getStore().removeClaimDurably(placed),"changed-center fixture is cleaned up durably");
            successfulPlacement();
        });});
    }
    private void successfulPlacement(){
        preparePlace(320);later(8,()->{placePacket(320);later(30,()->{
            DataStore.Claim placed=core.getStore().getClaimAt(center.getLocation());
            ok(center.getType()==Material.DIAMOND_ORE&&placed!=null&&placed.owner.equalsIgnoreCase(a.getName())
                    &&a.getInventory().getItemInMainHand().getType().isAir(),"successful network placement creates the claim and consumes its item");
            overlapPlacement();
        });});
    }
    private void overlapPlacement(){
        preparePlace(325);later(8,()->{placePacket(325);later(30,()->{
            DataStore.Claim existing=core.getStore().getClaimAt(center.getLocation());
            ok(center.getType().isAir()&&existing!=null&&existing.cx==320&&"claim_7".equals(ItemFactory.getCustomId(a.getInventory().getItemInMainHand())),
                    "overlapping network placement is rejected without consuming another claim item");
            shutdownPending();
        });});
    }
    private void shutdownPending(){
        setup(260);DataStore.Claim old=core.getStore().getClaimAt(center.getLocation());a.breakBlock(center);
        Bukkit.getPluginManager().disablePlugin(core);later(8,()->{
            ok(center.getType()==Material.DIAMOND_ORE&&core.getStore().getClaimAt(center.getLocation())==old&&claimItems(center.getLocation())==0,
                    "plugin shutdown before confirmation restores the claim center");finish();
        });
    }
    private void restart(){
        Block retained=world.getBlockAt(260,160,50),removed=world.getBlockAt(200,160,50),second=world.getBlockAt(230,160,50);
        ok(retained.getType()==Material.DIAMOND_ORE&&core.getStore().getClaimAt(retained.getLocation())!=null,"retained claim and its center survive orderly restart");
        ok(removed.getType().isAir()&&core.getStore().getClaimAt(removed.getLocation())==null&&second.getType().isAir()
                &&core.getStore().getClaimAt(second.getLocation())==null,"successfully removed claims stay removed after restart");
        Block placed=world.getBlockAt(320,160,50);ok(placed.getType()==Material.DIAMOND_ORE&&core.getStore().getClaimAt(placed.getLocation())!=null,
                "network-placed claim and its center survive orderly restart");finish();
    }
}
