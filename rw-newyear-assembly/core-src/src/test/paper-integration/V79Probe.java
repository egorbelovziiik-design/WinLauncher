package net.kinggrief.probe;

import net.kinggrief.core.DataStore;
import net.kinggrief.core.ItemFactory;
import net.kinggrief.core.KingGriefCore;
import net.kinggrief.core.SpawnEngine;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.type.Piston;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Two protocol players on disposable Paper. No production data/worlds. */
public final class V79Probe extends JavaPlugin implements Listener {
    private KingGriefCore core; private DataStore store; private Player a,b; private World spawn,normal;
    private boolean started,finished,cancelTeleport; private int checks,menuClicks,activePistons,normalPistons;
    private final String[] ids={"penguin","deer","dragon","fox","wolf","bunny","polar_bear","owl","snowman"};
    private final int[] sizes={10,14,13,14,14,12,13,13,13};
    @Override public void onEnable(){core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");store=core.getStore();Bukkit.getPluginManager().registerEvents(this,this);}
    @EventHandler public void join(PlayerJoinEvent event){
        if("WheelOne".equals(event.getPlayer().getName()))a=event.getPlayer();
        if("WheelTwo".equals(event.getPlayer().getName()))b=event.getPlayer();
        if(!started&&a!=null&&b!=null){started=true;later(30,()->{
            spawn=core.getSpawnEngine().getPreparedSpawnArrival().getWorld();normal=Bukkit.getWorld("world");
            a.teleport(new Location(spawn,.5,101,22.5));b.teleport(new Location(spawn,2.5,101,22.5));
            later(80,()->{if(getConfig().getInt("phase",1)==1)first();else second();});
        });}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void cancelTeleport(PlayerTeleportEvent e){if(cancelTeleport&&e.getPlayer().equals(a))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR) public void piston(BlockPistonExtendEvent e){if(e.getBlock().getWorld().equals(spawn))activePistons++;else if(e.getBlock().getWorld().equals(normal))normalPistons++;}
    @EventHandler(priority=EventPriority.MONITOR) public void click(InventoryClickEvent e){
        if(e.getWhoClicked().equals(a)&&e.getView().getTopInventory().getHolder()!=null
                &&e.getView().getTopInventory().getHolder().getClass().getSimpleName().equals("PetMenu"))menuClicks++;
    }
    private void later(long ticks,Runnable run){Bukkit.getScheduler().runTaskLater(this,()->{try{run.run();}catch(Throwable error){fail(error);}},ticks);}
    private void ok(boolean condition,String text){if(!condition)throw new AssertionError(text);checks++;getLogger().info("KG-V79 PASS "+text);}
    private void fail(Throwable error){if(finished)return;finished=true;getLogger().severe("KG-V79 RESULT FAIL "+error);error.printStackTrace();}
    private void finish(){if(finished)return;finished=true;getLogger().info("KG-V79 RESULT PASS checks="+checks);}
    @SuppressWarnings("unchecked") private <T>T field(Object object,String name){try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return (T)f.get(object);}catch(Exception e){throw new RuntimeException(e);}}
    private YamlConfiguration data(){return field(store,"yml");}
    private Object manager(){return field(core,"displayPets");}
    private Object pet(Player p){return ((Map<?,?>)field(manager(),"pets")).get(p.getUniqueId());}
    @SuppressWarnings("unchecked") private List<Entity> parts(Player p){Object pet=pet(p);return pet==null?Collections.emptyList():(List<Entity>)field(pet,"parts");}
    private String selected(Player p){return ((Map<UUID,String>)field(manager(),"selected")).get(p.getUniqueId());}
    private void resetPetRate(Player p){((Map<?,?>)field(manager(),"lastChoice")).remove(p.getUniqueId());}
    private void resetGiftRate(Player p){((Map<?,?>)field(field(core,"gameplayListener"),"npcClickCd")).remove(p.getUniqueId());}
    private void choice(Player p,String id){resetPetRate(p);p.performCommand("pet "+id);}
    private void blocked(Runnable task){
        Path data=core.getDataFolder().toPath().resolve("data.yml"),backup=data.resolveSibling("v79-test-backup.yml");
        try{Files.move(data,backup);Files.createSymbolicLink(data,backup.getFileName());task.run();}
        catch(Exception e){throw new RuntimeException(e);}
        finally{try{if(Files.isSymbolicLink(data))Files.delete(data);if(Files.exists(backup))Files.move(backup,data);}catch(Exception e){throw new RuntimeException(e);}}
    }
    private void first(){
        ok(core.getDescription().getVersion().equals("7.11.0"),"7.11.0 is actually loaded while running the 7.9 regression");
        a.setOp(false);b.setOp(false);store.setSavedRank(a.getName(),"default");store.setSavedRank(b.getName(),"default");
        a.getInventory().clear();b.getInventory().clear();
        cycle(0);
    }
    private void cycle(int index){
        if(index==ids.length){visibility();return;}
        List<Entity> previous=new ArrayList<Entity>(parts(a));choice(a,ids[index]);
        later(15,()->{
            List<Entity> current=parts(a);
            ok(current.size()==sizes[index]&&current.stream().allMatch(e->e instanceof BlockDisplay&&e.isValid()&&!e.isPersistent()&&!e.isVisibleByDefault()),
                    ids[index]+" has the exact nonpersistent, initially private model");
            ok(previous.stream().noneMatch(Entity::isValid),ids[index]+" replaces every old part without leaks");
            cycle(index+1);
        });
    }
    private void visibility(){
        List<Entity> current=new ArrayList<Entity>(parts(a));
        ok(!current.isEmpty()&&current.stream().allMatch(b::canSee),"observer can see a visible owner's model");
        b.hidePlayer(this,a);
        ok(current.stream().noneMatch(b::canSee)&&current.stream().allMatch(a::canSee),"external hidePlayer hides the pet from that observer immediately, not from its owner");
        b.showPlayer(this,a);
        later(15,()->{
            ok(parts(a).stream().allMatch(b::canSee),"model is visible to the observer again after showPlayer");
            a.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,200,0));
            later(15,()->{
                ok(parts(a).isEmpty(),"invisibility removes every pet part");a.removePotionEffect(PotionEffectType.INVISIBILITY);
                later(15,()->{
                    ok(parts(a).size()==13,"pet returns once invisibility ends");store.setParkourCp(a,a.getLocation());
                    later(15,()->{
                        ok(parts(a).isEmpty(),"parkour checkpoint suppresses pet without deleting selection");store.setParkourCp(a,null);
                        later(15,()->{
                            ok(parts(a).size()==13,"pet returns after leaving parkour");a.setGameMode(GameMode.SPECTATOR);
                            later(15,()->{
                                ok(parts(a).isEmpty(),"spectators do not reveal a pet");a.setGameMode(GameMode.SURVIVAL);
                                later(15,this::repairPet);
                            });
                        });
                    });
                });
            });
        });
    }
    private void repairPet(){
        List<Entity> old=new ArrayList<Entity>(parts(a));ok(old.size()==13,"pet returned after spectator mode");old.get(0).remove();
        later(15,()->{
            ok(old.stream().noneMatch(Entity::isValid)&&parts(a).size()==13,"one removed part causes a single leak-free rebuild");
            List<Entity> before=new ArrayList<Entity>(parts(a));
            ok(a.teleport(normal.getSpawnLocation()),"owner can enter another world");
            later(15,()->{
                ok(before.stream().noneMatch(Entity::isValid)&&parts(a).size()==13&&parts(a).get(0).getWorld().equals(normal),"cross-world teleport removes old parts and recreates only in destination");
                ok(parts(a).stream().noneMatch(b::canSee),"other-world observer cannot see destination model");
                a.teleport(new Location(spawn,.5,101,22.5));
                later(15,this::cap);
            });
        });
    }
    private void cap(){
        choice(b,"wolf");later(15,()->{
            ok(parts(a).size()==13&&parts(b).size()==14,"two players own separate models");
            core.getConfig().set("pets.max-active",1);
            later(15,()->{
                ok((parts(a).isEmpty()?0:1)+(parts(b).isEmpty()?0:1)==1,"lowering the active limit removes excess displays");
                core.getConfig().set("pets.max-active",0);
                later(15,()->{
                    ok(parts(a).isEmpty()&&parts(b).isEmpty()&&"snowman".equals(selected(a))&&"wolf".equals(selected(b)),"zero cap hides models but keeps choices");
                    core.getConfig().set("pets.max-active",16);
                    later(15,()->{
                        ok(parts(a).size()==13&&parts(b).size()==14,"pets return when capacity is restored");choice(b,"off");menu();
                    });
                });
            });
        });
    }
    private void menu(){
        a.performCommand("pets");
        Inventory top=a.getOpenInventory().getTopInventory();
        ok(top.getSize()==36&&top.getItem(13)!=null&&top.getItem(13).getType()==Material.FOX_SPAWN_EGG,"/pets opens the complete selection menu with fox");
        getLogger().info("KG-V79 REQUEST CLICK 13");
        later(30,()->{
            ok(menuClicks>0&&"fox".equals(selected(a))&&parts(a).size()==14,"real client selected fox using the menu");
            boolean leaked=Arrays.stream(a.getInventory().getContents()).anyMatch(item->item!=null&&item.getType()==Material.FOX_SPAWN_EGG);
            ok(!leaked&&(a.getItemOnCursor()==null||a.getItemOnCursor().getType().isAir()),"menu icons are not transferred to player inventory or cursor");
            resetPetRate(a);blocked(()->a.performCommand("pet owl"));
            ok("fox".equals(selected(a))&&"fox".equals(data().getString("cosmeticPets."+a.getUniqueId())),"failed save preserves the prior selection");
            choice(a,"off");later(10,()->{ok(parts(a).isEmpty(),"pet off immediately removes every display");teleports();});
        });
    }
    private void teleports(){
        Location initial=a.getLocation(),sentinel=new Location(normal,8.5,100,8.5);
        store.setBack(a,sentinel);store.setHome(a.getName(),"test",new Location(normal,9.5,100,9.5));
        Location cp=a.getLocation().clone();store.setParkourCp(a,cp);cancelTeleport=true;
        for(String command:new String[]{"warp mine","mine","home test","back","kgspawn","parkour leave"})a.performCommand(command);
        ok(a.getLocation().distanceSquared(initial)<.01&&store.getBack(a).getWorld().equals(normal)
                &&store.getBack(a).distanceSquared(sentinel)<.01&&store.getParkourCp(a)!=null,"cancelled navigation keeps location, /back and parkour checkpoint");
        a.performCommand("warps");
        InventoryClickEvent click=new InventoryClickEvent(a.getOpenInventory(),InventoryType.SlotType.CONTAINER,10,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        Bukkit.getPluginManager().callEvent(click);
        ok(store.getBack(a).getWorld().equals(normal)&&store.getBack(a).distanceSquared(sentinel)<.01,"cancelled GUI warp keeps /back");
        cancelTeleport=false;store.setParkourCp(a,null);Location before=a.getLocation();a.performCommand("warp mine");
        ok(a.getWorld().equals(spawn)&&a.getLocation().distanceSquared(core.getSpawnEngine().getWarpLocation("mine"))<.1
                &&store.getBack(a).getWorld().equals(before.getWorld())&&store.getBack(a).distanceSquared(before)<.1,"accepted warp saves the actual origin to /back");
        protections();
    }
    private Inventory inv(Block block){return ((Container)block.getState()).getInventory();}
    private Block piston(World w,int x,int y,int z){
        Block piston=w.getBlockAt(x,y,z);piston.setType(Material.PISTON,false);Piston state=(Piston)piston.getBlockData();state.setFacing(BlockFace.EAST);piston.setBlockData(state,false);
        piston.getRelative(BlockFace.EAST).setType(Material.STONE,false);piston.getRelative(BlockFace.SOUTH).setType(Material.REDSTONE_BLOCK,true);return piston;
    }
    private void protections(){
        int x=96,y=180,z=96;
        spawn.getBlockAt(x,y-1,z).setType(Material.STONE,false);normal.getBlockAt(x,y-1,z).setType(Material.STONE,false);
        a.teleport(new Location(spawn,x+.5,y,z+.5));b.teleport(new Location(normal,x+.5,y,z+.5));
        Block chest=spawn.getBlockAt(x+1,y,z);chest.setType(Material.CHEST,false);inv(chest).setItem(0,new ItemStack(Material.DIAMOND,11));
        a.openInventory(inv(chest));
        ok(!a.getOpenInventory().getTopInventory().equals(inv(chest)),"ordinary player cannot open active-spawn world storage");
        store.setSavedRank(b.getName(),"owner");b.teleport(new Location(spawn,x+.5,y,z+.5));b.openInventory(inv(chest));
        ok(b.getOpenInventory().getTopInventory().getItem(0)!=null&&b.getOpenInventory().getTopInventory().getItem(0).getAmount()==11,"OWNER may open the preserved chest");b.closeInventory();
        a.openInventory(a.getEnderChest());ok(a.getOpenInventory().getTopInventory().getType()==InventoryType.ENDER_CHEST,"personal ender chest remains available");a.closeInventory();
        PlayerInteractEvent tool=new PlayerInteractEvent(a,Action.RIGHT_CLICK_BLOCK,new ItemStack(Material.DIAMOND_AXE),chest,BlockFace.UP,EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(tool);ok(tool.useItemInHand()==Event.Result.DENY,"vanilla world-changing tool denied at active spawn");
        Block spawner=spawn.getBlockAt(x+2,y,z);spawner.setType(Material.SPAWNER,false);CreatureSpawner state=(CreatureSpawner)spawner.getState();state.setSpawnedType(org.bukkit.entity.EntityType.PIG);state.update(true,false);
        PlayerInteractEvent vanillaEgg=new PlayerInteractEvent(a,Action.RIGHT_CLICK_BLOCK,new ItemStack(Material.COW_SPAWN_EGG),spawner,BlockFace.UP,EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(vanillaEgg);ok(vanillaEgg.useItemInHand()==Event.Result.DENY,"vanilla spawn egg cannot alter an active-spawn spawner");
        ItemStack egg=ItemFactory.mysteryEgg(1);a.getInventory().setItemInMainHand(egg);
        PlayerInteractEvent use=new PlayerInteractEvent(a,Action.RIGHT_CLICK_BLOCK,egg,spawner,BlockFace.UP,EquipmentSlot.HAND);Bukkit.getPluginManager().callEvent(use);
        ok(a.getInventory().getItemInMainHand().getAmount()==1&&((CreatureSpawner)spawner.getState()).getSpawnedType()==org.bukkit.entity.EntityType.PIG,"custom mystery egg cannot alter spawn spawner or consume itself");a.getInventory().setItemInMainHand(null);
        Block ice=spawn.getBlockAt(x+3,y,z);ice.setType(Material.ICE,false);BlockFadeEvent fade=new BlockFadeEvent(ice,ice.getState());Bukkit.getPluginManager().callEvent(fade);
        ok(fade.isCancelled(),"ice melt is cancelled in active spawn");
        Block ordinaryIce=normal.getBlockAt(x+3,y,z);ordinaryIce.setType(Material.ICE,false);BlockFadeEvent otherFade=new BlockFadeEvent(ordinaryIce,ordinaryIce.getState());Bukkit.getPluginManager().callEvent(otherFade);
        ok(!otherFade.isCancelled(),"ice melt in ordinary survival is untouched");
        EntityChangeBlockEvent change=new EntityChangeBlockEvent(a,ice,Bukkit.createBlockData(Material.DIRT));Bukkit.getPluginManager().callEvent(change);
        ok(change.isCancelled(),"unauthorized entity cannot change active-spawn block");
        EntityChangeBlockEvent otherChange=new EntityChangeBlockEvent(a,ordinaryIce,Bukkit.createBlockData(Material.DIRT));Bukkit.getPluginManager().callEvent(otherChange);
        ok(!otherChange.isCancelled(),"entity block change in ordinary survival is untouched");
        ArmorStand activeStand=spawn.spawn(new Location(spawn,x+6.5,y,z+.5),ArmorStand.class);
        ArmorStand normalStand=normal.spawn(new Location(normal,x+6.5,y,z+.5),ArmorStand.class);
        EntityPlaceEvent denyPlace=new EntityPlaceEvent(activeStand,a,ice,BlockFace.UP,EquipmentSlot.HAND);Bukkit.getPluginManager().callEvent(denyPlace);
        EntityPlaceEvent allowPlace=new EntityPlaceEvent(normalStand,a,ordinaryIce,BlockFace.UP,EquipmentSlot.HAND);Bukkit.getPluginManager().callEvent(allowPlace);
        b.setGameMode(GameMode.CREATIVE);
        EntityPlaceEvent adminPlace=new EntityPlaceEvent(activeStand,b,ice,BlockFace.UP,EquipmentSlot.HAND);Bukkit.getPluginManager().callEvent(adminPlace);
        ok(denyPlace.isCancelled()&&!allowPlace.isCancelled()&&!adminPlace.isCancelled(),"entity placement denied only at spawn for non-editors");
        b.setGameMode(GameMode.SURVIVAL);activeStand.remove();normalStand.remove();
        // Actual redstone physics and hoppers in both worlds. One player keeps each fixture ticking.
        b.teleport(new Location(normal,x+.5,y,z+.5));
        Block ps=piston(spawn,x,y,z+3),pn=piston(normal,x,y,z+3);
        Block sh=spawn.getBlockAt(x+4,y,z);sh.setType(Material.HOPPER,false);
        Block sc=sh.getRelative(BlockFace.UP);sc.setType(Material.CHEST,false);inv(sc).setItem(0,new ItemStack(Material.EMERALD,8));
        Block nh=normal.getBlockAt(x+4,y,z);nh.setType(Material.HOPPER,false);
        Block nc=nh.getRelative(BlockFace.UP);nc.setType(Material.CHEST,false);inv(nc).setItem(0,new ItemStack(Material.EMERALD,8));
        later(80,()->{
            ok(activePistons>0&&ps.getRelative(BlockFace.EAST).getType()==Material.STONE&&ps.getRelative(BlockFace.EAST,2).getType()!=Material.STONE,"real active-spawn piston event cannot move blocks");
            ok(normalPistons>0&&pn.getRelative(BlockFace.EAST,2).getType()==Material.STONE,"real survival piston still moves blocks");
            ItemStack protectedItem=inv(sc).getItem(0),normalItem=inv(nc).getItem(0);
            ok(protectedItem!=null&&protectedItem.getAmount()==8,"real hopper cannot extract active-spawn storage");
            ok(normalItem==null||normalItem.getAmount()<8,"real ordinary-world hopper still transfers items");
            giftStart();
        });
    }
    private void giftStart(){
        a.setGameMode(GameMode.SURVIVAL);a.getInventory().clear();a.teleport(new Location(spawn,.5,100,22.5,0,0));
        store.setCoins(a.getName(),1000);store.setRelics(a.getName(),100);
        data().set("players."+a.getName().toLowerCase(Locale.ROOT)+".snowflakes",4);store.save();
        int[] g=SpawnEngine.GIFT_BLOCKS[0];
        later(30,()->{getLogger().info("KG-V79 REQUEST BLOCK "+g[0]+" "+g[1]+" "+g[2]);later(30,()->{
            ok(store.getFoundSpawnGiftCount(a.getName())==1&&store.getCoins(a.getName())==4500&&store.getSnowflakes(a.getName())==7,"real protocol gift click commits first flag, coins and snow");
            String base="players."+a.getName().toLowerCase(Locale.ROOT);
            for(int i=1;i<=7;i++)data().set(base+".spawnGifts.g"+i,true);store.save();
            giftFailureAndRetry();
        });});
    }
    private void giftClick(int index){
        int[] pos=SpawnEngine.GIFT_BLOCKS[index];Block block=spawn.getBlockAt(pos[0],pos[1],pos[2]);
        resetGiftRate(a);PlayerInteractEvent event=new PlayerInteractEvent(a,Action.RIGHT_CLICK_BLOCK,null,block,BlockFace.UP,EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(event);
    }
    private void giftFailureAndRetry(){
        double coins=store.getCoins(a.getName());int snow=store.getSnowflakes(a.getName());
        blocked(()->giftClick(8));
        ok(!store.hasFoundSpawnGift(a.getName(),8)&&store.getCoins(a.getName())==coins&&store.getSnowflakes(a.getName())==snow,"failed ninth-gift write rolls back flag and reward");
        giftClick(8);giftClick(8);
        ok(store.getFoundSpawnGiftCount(a.getName())==9&&store.getCoins(a.getName())==coins+3500&&store.getSnowflakes(a.getName())==snow+3,"ninth gift is paid exactly once after retry");
        coins=store.getCoins(a.getName());snow=store.getSnowflakes(a.getName());int relics=store.getRelics(a),keys=store.getCaseKeys(a.getName(),"donate");
        final double oldCoins=coins;final int oldSnow=snow,oldRelics=relics,oldKeys=keys;
        blocked(()->giftClick(9));
        ok(!store.hasFoundSpawnGift(a.getName(),9)&&store.getCoins(a.getName())==oldCoins&&store.getSnowflakes(a.getName())==oldSnow
                &&store.getRelics(a)==oldRelics&&store.getCaseKeys(a.getName(),"donate")==oldKeys,"failed tenth-gift write rolls back all local rewards and progress");
        giftClick(9);giftClick(9);
        ok(store.getFoundSpawnGiftCount(a.getName())==10&&store.getCoins(a.getName())==coins+28500&&store.getSnowflakes(a.getName())==snow+3
                &&store.getRelics(a)==relics+25&&store.getCaseKeys(a.getName(),"donate")==keys+1,"completion pays all four local rewards exactly once");
        try{Object points=Bukkit.getPluginManager().getPlugin("PlayerPoints");int external=(Integer)points.getClass().getMethod("balance",UUID.class).invoke(points,a.getUniqueId());
            ok(external==relics+25,"completed relic reward synchronizes the PlayerPoints test API");}
        catch(Exception error){throw new RuntimeException(error);}
        choice(a,"owl");later(15,()->{ok(parts(a).size()==13&&"owl".equals(selected(a)),"new owl choice and gifts are ready for crash restart");finish();});
    }
    private void second(){
        ok("owl".equals(selected(a))&&parts(a).size()==13,"new pet selection and one model survive crash restart");
        ok(store.getFoundSpawnGiftCount(a.getName())==10&&store.getCoins(a.getName())==36500&&store.getSnowflakes(a.getName())==13
                &&store.getRelics(a)==125&&store.getCaseKeys(a.getName(),"donate")==1,"all completed gift progress and balances survive crash restart");
        giftClick(9);
        ok(store.getCoins(a.getName())==36500&&store.getCaseKeys(a.getName(),"donate")==1,"completed gift cannot be claimed again after restart");
        choice(a,"off");later(15,()->{ok(parts(a).isEmpty(),"restored pet can be disabled");finish();});
    }
}
