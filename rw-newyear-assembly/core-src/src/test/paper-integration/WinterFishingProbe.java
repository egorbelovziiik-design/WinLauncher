package net.kinggrief.probe;

import net.kinggrief.core.DataStore;
import net.kinggrief.core.KingGriefCore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.bukkit.configuration.file.YamlConfiguration;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Disposable Paper: actual protocol fishing plus controlled fault and limit fixtures. */
public final class WinterFishingProbe extends JavaPlugin implements Listener {
    private KingGriefCore core;private Player a,b;private World world;
    private boolean running,done,realMode,cancelFish;private int checks,attempts;
    private UUID realItem;private Material realType;private Path blockedData,blockedBackup;
    @Override public void onEnable(){
        saveDefaultConfig();core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");
        Bukkit.getPluginManager().registerEvents(this,this);
    }
    @EventHandler public void join(PlayerJoinEvent event){
        if("FishTester".equals(event.getPlayer().getName()))a=event.getPlayer();
        if("FishFriend".equals(event.getPlayer().getName()))b=event.getPlayer();
        if(a!=null&&b!=null&&!running){running=true;later(30,this::begin);}
    }
    private void later(long ticks,Runnable action){Bukkit.getScheduler().runTaskLater(this,()->{try{action.run();}catch(Throwable error){fail(error);}},ticks);}
    private void ok(boolean value,String message){if(!value)throw new AssertionError(message);checks++;getLogger().info("KG-FISH PASS "+message);}
    private void fail(Throwable error){
        if(done)return;done=true;restoreSave();getLogger().severe("KG-FISH RESULT FAIL "+error);error.printStackTrace();
    }
    private void finish(){done=true;restoreSave();getLogger().info("KG-FISH RESULT PASS "+checks+" checks");}
    private void begin(){
        world=Bukkit.getWorld("world");
        if(getConfig().getInt("phase",1)==2){later(10,this::restartChecks);return;}
        CompletableFuture<?> future=world.getChunkAtAsync(26,26,true);
        future.whenComplete((chunk,error)->Bukkit.getScheduler().runTask(this,()->{
            try{if(error!=null||chunk==null)throw new AssertionError("async chunk load",error);
                world.addPluginChunkTicket(26,26,this);pond();}
            catch(Throwable failure){fail(failure);}
        }));
    }
    private void pond(){
        for(int x=417;x<=423;x++)for(int z=416;z<=423;z++){
            world.getBlockAt(x,64,z).setType(Material.STONE,false);
            world.getBlockAt(x,65,z).setType(Material.WATER,false);
            for(int y=66;y<=69;y++)world.getBlockAt(x,y,z).setType(Material.AIR,false);
        }
        for(int z=419;z<=420;z++){
            world.getBlockAt(416,64,z).setType(Material.STONE,false);
            for(int y=65;y<=69;y++)world.getBlockAt(416,y,z).setType(Material.AIR,false);
        }
        a.teleport(new Location(world,416.5,65,419.5,-90,0));b.teleport(new Location(world,416.5,65,420.5,-90,0));
        a.setGameMode(GameMode.SURVIVAL);b.setGameMode(GameMode.SURVIVAL);
        a.getInventory().clear();a.getInventory().setHeldItemSlot(0);a.getInventory().setItemInMainHand(new ItemStack(Material.FISHING_ROD));
        realMode=true;later(40,this::cast);
        later(400,()->{if(realMode)throw new AssertionError("real fishing timed out after "+attempts+" casts");});
    }
    private void cast(){
        if(!realMode)return;
        if(++attempts>8)throw new AssertionError("too many non-fish catches");
        getLogger().info("KG-FISH REQUEST fish");
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void protect(PlayerFishEvent event){if(cancelFish&&event.getState()==PlayerFishEvent.State.CAUGHT_FISH)event.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR)
    public void observe(PlayerFishEvent event){
        if(!realMode||!event.getPlayer().equals(a))return;
        if(event.getState()==PlayerFishEvent.State.FISHING){
            FishHook hook=event.getHook();
            later(2,()->{
                hook.setWaitTime(1,2);hook.setLureTime(1,2);
                hook.teleport(new Location(world,419.5,65.4,419.5));hook.setVelocity(new Vector(0,0,0));
            });
        }else if(event.getState()==PlayerFishEvent.State.BITE){
            getLogger().info("KG-FISH REQUEST fish");
        }else if(event.getState()==PlayerFishEvent.State.CAUGHT_FISH&&event.getCaught() instanceof Item){
            Item item=(Item)event.getCaught();Material type=item.getItemStack().getType();
            getLogger().info("KG-FISH REAL catch="+type+" cancelled="+event.isCancelled());
            if(!event.isCancelled()&&(type==Material.COD||type==Material.SALMON||type==Material.TROPICAL_FISH||type==Material.PUFFERFISH)){
                realMode=false;realItem=item.getUniqueId();realType=type;later(30,this::afterRealCatch);
            }else later(20,this::cast);
        }else if(event.getState()==PlayerFishEvent.State.FAILED_ATTEMPT||event.getState()==PlayerFishEvent.State.IN_GROUND){later(20,this::cast);}
    }
    private String root(Player player){return "winterFishing."+player.getUniqueId();}
    private static String day(){return LocalDate.now(ZoneOffset.UTC).toString();}
    private void afterRealCatch(){
        DataStore store=core.getStore();int amount=store.getSnowflakes(a.getName());
        ok(amount==1||amount==2||amount==5,"real protocol rod catch earns one bounded winter bonus");
        ok(yaml().getInt(root(a)+".total")==1&&yaml().getInt(root(a)+".daily")==1,"real catch updates personal totals");
        boolean itemExists=a.getInventory().contains(realType)||Bukkit.getEntity(realItem) instanceof Item;
        ok(itemExists,"real vanilla fish remains in inventory or as its normal item entity");
        invalidCases();
    }
    private FishHook hook(UUID id){
        return (FishHook)Proxy.newProxyInstance(FishHook.class.getClassLoader(),new Class<?>[]{FishHook.class},(proxy,method,args)->{
            if("getUniqueId".equals(method.getName()))return id;
            if("hashCode".equals(method.getName()))return id.hashCode();
            if("equals".equals(method.getName()))return proxy==args[0];
            if("toString".equals(method.getName()))return "SyntheticFishHook["+id+"]";
            Class<?> type=method.getReturnType();
            if(type==boolean.class)return false;if(type==int.class)return 0;if(type==long.class)return 0L;
            if(type==double.class)return 0D;if(type==float.class)return 0F;return null;
        });
    }
    private PlayerFishEvent synthetic(Player player,UUID id,Material material,PlayerFishEvent.State state){
        Item item=world.dropItem(new Location(world,419.5,67,419.5),new ItemStack(material));
        PlayerFishEvent event=new PlayerFishEvent(player,item,hook(id),EquipmentSlot.HAND,state);
        Bukkit.getPluginManager().callEvent(event);item.remove();return event;
    }
    private int pendingSize(){try{
        Field field=KingGriefCore.class.getDeclaredField("winterFishing");field.setAccessible(true);Object manager=field.get(core);
        Field pending=manager.getClass().getDeclaredField("pending");pending.setAccessible(true);return ((Map<?,?>)pending.get(manager)).size();
    }catch(Exception error){throw new RuntimeException(error);}}
    private void invalidCases(){
        DataStore store=core.getStore();int snow=store.getSnowflakes(a.getName()),total=yaml().getInt(root(a)+".total");
        yaml().set(root(a)+".last",0L);
        PlayerFishEvent late=synthetic(a,UUID.randomUUID(),Material.COD,PlayerFishEvent.State.CAUGHT_FISH);late.setCancelled(true);
        synthetic(a,UUID.randomUUID(),Material.LEATHER,PlayerFishEvent.State.CAUGHT_FISH);
        synthetic(a,UUID.randomUUID(),Material.COD,PlayerFishEvent.State.CAUGHT_ENTITY);
        cancelFish=true;synthetic(a,UUID.randomUUID(),Material.COD,PlayerFishEvent.State.CAUGHT_FISH);cancelFish=false;
        core.getConfig().set("enable-builtin-auth",true);store.setAuthenticated(a,false);
        synthetic(a,UUID.randomUUID(),Material.COD,PlayerFishEvent.State.CAUGHT_FISH);
        core.getConfig().set("enable-builtin-auth",false);
        later(30,()->{
            ok(store.getSnowflakes(a.getName())==snow&&yaml().getInt(root(a)+".total")==total,
                    "cancelled, unauthenticated, junk and caught-entity events pay nothing");
            failureCase();
        });
    }
    private void blockSave(){try{
        blockedData=core.getDataFolder().toPath().resolve("data.yml");blockedBackup=blockedData.resolveSibling("test-fish-backup.yml");
        Files.move(blockedData,blockedBackup);Files.createSymbolicLink(blockedData,blockedBackup.getFileName());
    }catch(Exception error){throw new RuntimeException(error);}}
    private void restoreSave(){try{
        if(blockedData!=null&&Files.isSymbolicLink(blockedData))Files.delete(blockedData);
        if(blockedBackup!=null&&Files.exists(blockedBackup))Files.move(blockedBackup,blockedData);
        blockedData=null;blockedBackup=null;
    }catch(Exception error){getLogger().severe("Could not restore disposable test file: "+error);}}
    private void failureCase(){
        DataStore store=core.getStore();int snow=store.getSnowflakes(a.getName()),total=yaml().getInt(root(a)+".total");
        yaml().set(root(a)+".last",0L);store.save();blockSave();
        synthetic(a,UUID.randomUUID(),Material.SALMON,PlayerFishEvent.State.CAUGHT_FISH);
        later(30,()->{
            ok(store.getSnowflakes(a.getName())==snow&&yaml().getInt(root(a)+".total")==total,
                    "failed batch save rolls back both bonus and progress");
            restoreSave();awardCase();
        });
    }
    private void awardCase(){
        DataStore store=core.getStore();int beforeA=store.getSnowflakes(a.getName()),beforeB=store.getSnowflakes(b.getName());
        yaml().set(root(a)+".day",day());yaml().set(root(a)+".daily",0);yaml().set(root(a)+".last",0L);yaml().set(root(a)+".total",24);
        UUID same=UUID.randomUUID();synthetic(a,same,Material.COD,PlayerFishEvent.State.CAUGHT_FISH);
        synthetic(a,same,Material.COD,PlayerFishEvent.State.CAUGHT_FISH);
        synthetic(b,UUID.randomUUID(),Material.PUFFERFISH,PlayerFishEvent.State.CAUGHT_FISH);
        ok(pendingSize()==2,"duplicate hook is queued once alongside another player");
        later(30,()->{
            int da=store.getSnowflakes(a.getName())-beforeA,db=store.getSnowflakes(b.getName())-beforeB;
            ok((da==1||da==2||da==5)&&(db==1||db==2||db==5)&&yaml().getInt(root(a)+".total")==25
                    &&yaml().getInt(root(a)+".daily")==1&&yaml().getInt(root(b)+".total")==1,
                    "batch pays each player once with the configured bounds");
            ok(store.hasUnlockedTitle(a.getName(),"winter_fisher"),"25th bonus unlocks the cosmetic fishing title");
            a.performCommand("winterfish");Inventory menu=a.getOpenInventory().getTopInventory();
            ok(menu.getSize()==27&&menu.getItem(20).getType()==Material.NAME_TAG,"fishing menu presents saved title and statistics");
            InventoryClickEvent take=new InventoryClickEvent(a.getOpenInventory(),InventoryType.SlotType.CONTAINER,11,ClickType.NUMBER_KEY,InventoryAction.HOTBAR_SWAP,0);
            Bukkit.getPluginManager().callEvent(take);ok(take.isCancelled(),"fishing menu icons cannot be taken");
            a.performCommand("titles");ok(a.getOpenInventory().getTopInventory().getItem(29).getType()==Material.NAME_TAG,"fishing title is available in the titles menu");a.closeInventory();
            cooldownCase();
        });
    }
    private void cooldownCase(){
        int snow=core.getStore().getSnowflakes(a.getName()),total=yaml().getInt(root(a)+".total");
        synthetic(a,UUID.randomUUID(),Material.SALMON,PlayerFishEvent.State.CAUGHT_FISH);
        later(30,()->{
            ok(core.getStore().getSnowflakes(a.getName())==snow&&yaml().getInt(root(a)+".total")==total,"ten-second cooldown blocks another bonus");
            limitCase();
        });
    }
    private void limitCase(){
        int snow=core.getStore().getSnowflakes(a.getName()),total=yaml().getInt(root(a)+".total");
        yaml().set(root(a)+".daily",12);yaml().set(root(a)+".last",0L);
        synthetic(a,UUID.randomUUID(),Material.COD,PlayerFishEvent.State.CAUGHT_FISH);
        later(30,()->{
            ok(core.getStore().getSnowflakes(a.getName())==snow&&yaml().getInt(root(a)+".total")==total,"daily limit does not alter the ordinary catch or balance");
            yaml().set(root(a)+".day",LocalDate.now(ZoneOffset.UTC).plusDays(1).toString());yaml().set(root(a)+".daily",0);
            synthetic(a,UUID.randomUUID(),Material.COD,PlayerFishEvent.State.CAUGHT_FISH);
            later(30,()->{
                ok(core.getStore().getSnowflakes(a.getName())==snow&&yaml().getInt(root(a)+".total")==total,"backward server clock cannot reset the reward limit");
                yaml().set(root(a)+".day",day());yaml().set(root(a)+".daily",12);core.getStore().save();finish();
            });
        });
    }
    private void restartChecks(){
        DataStore store=core.getStore();
        ok(yaml().getInt(root(a)+".total")==25&&yaml().getInt(root(a)+".daily")==12
                &&store.hasUnlockedTitle(a.getName(),"winter_fisher"),"fishing total, limit and title survive restart");
        int snow=store.getSnowflakes(a.getName());
        synthetic(a,UUID.randomUUID(),Material.COD,PlayerFishEvent.State.CAUGHT_FISH);
        later(30,()->{ok(store.getSnowflakes(a.getName())==snow&&yaml().getInt(root(a)+".total")==25,"daily limit is enforced after restart");finish();});
    }
    private YamlConfiguration yaml(){try{Field field=DataStore.class.getDeclaredField("yml");field.setAccessible(true);return (YamlConfiguration)field.get(core.getStore());}
        catch(Exception error){throw new RuntimeException(error);}}
}
