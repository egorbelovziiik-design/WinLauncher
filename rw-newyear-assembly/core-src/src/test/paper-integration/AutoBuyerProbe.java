package net.kinggrief.probe;

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
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Real Paper break/pickup paths with two network-connected players. */
public final class AutoBuyerProbe extends JavaPlugin implements Listener {
    private KingGriefCore core;
    private Player a,b;
    private World world;
    private Block protectedBlock;
    private boolean cancelBreak, namedDrop, networkMine, started, done;
    private int checks;
    private Path data, backup;
    private double failedSold, changeSold, offSold, aSold, bSold;
    private Object manager;

    @Override public void onEnable(){saveDefaultConfig();core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");Bukkit.getPluginManager().registerEvents(this,this);}
    @EventHandler public void join(PlayerJoinEvent event){
        if("EcoBuyer".equals(event.getPlayer().getName()))a=event.getPlayer();
        if("EcoSeller".equals(event.getPlayer().getName()))b=event.getPlayer();
        if(a!=null&&b!=null&&!started){started=true;later(40,this::begin);}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void protect(BlockBreakEvent event){
        if(cancelBreak && event.getBlock().equals(protectedBlock))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void customize(BlockDropItemEvent event){
        if(namedDrop && event.getBlock().equals(protectedBlock))for(Item item:event.getItems()){
            ItemStack stack=item.getItemStack();ItemMeta meta=stack.getItemMeta();meta.setDisplayName("protected mining reward");stack.setItemMeta(meta);item.setItemStack(stack);
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void prepareNetworkPickup(BlockDropItemEvent event){
        if(!networkMine || !event.getBlock().equals(protectedBlock))return;
        for(Item item:event.getItems()){
            item.setPickupDelay(6);
            later(1,()->{if(item.isValid()){item.teleport(a.getLocation().add(0,0.15,0));item.setVelocity(new org.bukkit.util.Vector());}});
        }
    }
    private void later(long ticks,Runnable runnable){Bukkit.getScheduler().runTaskLater(this,()->{try{runnable.run();}catch(Throwable error){fail(error);}},ticks);}
    private void ok(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;getLogger().info("KG-AUTO PASS "+message);}
    private void fail(Throwable error){if(done)return;done=true;restore();getLogger().severe("KG-AUTO RESULT FAIL "+error);error.printStackTrace();}
    private void finish(){done=true;restore();getLogger().info("KG-AUTO RESULT PASS "+checks+" checks");}
    private org.bukkit.configuration.file.YamlConfiguration yaml(){try{Field f=core.getStore().getClass().getDeclaredField("yml");f.setAccessible(true);return (org.bukkit.configuration.file.YamlConfiguration)f.get(core.getStore());}catch(Exception e){throw new RuntimeException(e);}}
    private boolean enabled(Player p){return yaml().getBoolean("autoBuyer."+p.getUniqueId()+".enabled",false);}
    private double sold(Player p){return core.getStore().getBuyerSoldTotal(p.getName());}
    private double factor(Player p){return core.getStore().getBuyerMultiplier(p.getName())*(1.0+core.getRanks().getRank(p).tier*0.12);}
    private boolean near(double left,double right){return Math.abs(left-right)<1e-6;}
    private void blockSave(){try{
        data=core.getDataFolder().toPath().resolve("data.yml");backup=data.resolveSibling("auto-test-backup.yml");
        Files.move(data,backup);Files.createSymbolicLink(data,backup.getFileName());
    }catch(Exception error){throw new RuntimeException(error);}}
    private void restore(){try{
        if(data!=null&&Files.isSymbolicLink(data))Files.delete(data);
        if(backup!=null&&Files.exists(backup))Files.move(backup,data);data=null;backup=null;
    }catch(Exception error){getLogger().severe("Could not restore disposable test data: "+error);}}
    private void initManager(){try{
        Field f=core.getClass().getDeclaredField("autoBuyer");f.setAccessible(true);manager=f.get(core);
    }catch(Exception error){throw new RuntimeException(error);}}
    private void pauseWorker(){try{Field f=manager.getClass().getDeclaredField("timer");f.setAccessible(true);((BukkitRunnable)f.get(manager)).cancel();}catch(Exception error){throw new RuntimeException(error);}}
    private void flush(){try{Method m=manager.getClass().getDeclaredMethod("flush");m.setAccessible(true);m.invoke(manager);}catch(Exception error){throw new RuntimeException(error);}}
    private void clearToggleLimit(){try{
        Field f=manager.getClass().getDeclaredField("lastToggle");f.setAccessible(true);f.setLong(manager,0L);
        Field t=manager.getClass().getDeclaredField("toggles");t.setAccessible(true);((java.util.Map<?,?>)t.get(manager)).clear();
    }catch(Exception error){throw new RuntimeException(error);}}
    private int count(Player p,Material mat,boolean named){
        int amount=0;for(ItemStack item:p.getInventory().getStorageContents())if(item!=null&&item.getType()==mat&&item.hasItemMeta()==named)amount+=item.getAmount();return amount;
    }
    private int plainIncludingOffhand(Player p,Material mat){ItemStack off=p.getInventory().getItemInOffHand();return count(p,mat,false)+(off!=null&&off.getType()==mat&&!off.hasItemMeta()?off.getAmount():0);}
    private int ground(Location loc,Material mat){int n=0;for(Entity entity:loc.getWorld().getNearbyEntities(loc,6,6,6))if(entity instanceof Item){ItemStack s=((Item)entity).getItemStack();if(s.getType()==mat)n+=s.getAmount();}return n;}
    private Item firstDrop(Location loc,Material mat){for(Entity entity:loc.getWorld().getNearbyEntities(loc,6,6,6))if(entity instanceof Item&&((Item)entity).getItemStack().getType()==mat)return (Item)entity;return null;}
    private void pick(Player p,ItemStack pick){p.getInventory().setHeldItemSlot(8);p.getInventory().setItemInMainHand(pick);}
    private ItemStack smelter(){ItemStack tool=new ItemStack(Material.DIAMOND_PICKAXE);ItemFactory.applyScrollToTool(tool,"smelt");return tool;}
    private Block ore(Player p,int x,Material type){
        p.setGameMode(GameMode.SURVIVAL);world.getBlockAt(x,159,20).setType(Material.STONE,false);
        p.teleport(new Location(world,x+0.5,160,20.5,0,90));Block block=world.getBlockAt(x,160,20);block.setType(type,false);return block;
    }
    private void begin(){
        initManager();world=Bukkit.getWorlds().get(0);int phase=getConfig().getInt("phase",1);
        if(phase==2){restart();return;}if(phase==3){crash();return;}if(phase==4){recover();return;}
        a.getInventory().clear();b.getInventory().clear();a.setGameMode(GameMode.SURVIVAL);b.setGameMode(GameMode.SURVIVAL);
        a.getInventory().setItem(0,new ItemStack(Material.DIAMOND,4));
        ItemStack named=new ItemStack(Material.DIAMOND,3);ItemMeta meta=named.getItemMeta();meta.setDisplayName("old named diamonds");named.setItemMeta(meta);a.getInventory().setItem(1,named);
        a.getInventory().setItemInOffHand(new ItemStack(Material.DIAMOND,2));b.getInventory().setItem(0,new ItemStack(Material.DIAMOND,6));
        pick(a,smelter());protectedBlock=ore(a,20,Material.COPPER_ORE);
        ok(!enabled(a)&&!enabled(b),"autosell is disabled by default for both players");
        double before=sold(a);a.breakBlock(protectedBlock);
        later(70,()->{
            ok(count(a,Material.COPPER_INGOT,false)==4&&sold(a)==before,"disabled mode delivers smelted loot without selling");
            a.getInventory().remove(Material.COPPER_INGOT);a.performCommand("autosell on");
            ok(enabled(a),"explicit opt-in enables autosell");
            pick(a,smelter());protectedBlock=ore(a,30,Material.COPPER_ORE);double old=sold(a);a.breakBlock(protectedBlock);
            later(70,()->{
                ok(count(a,Material.COPPER_INGOT,false)==0&&sold(a)>old,"periodic worker sells newly smelted copper in survival world");
                ok(count(a,Material.DIAMOND,false)==4&&count(a,Material.DIAMOND,true)==3&&a.getInventory().getItemInOffHand().getAmount()==2,
                        "periodic sale preserves old plain, named and offhand diamonds");
                pauseWorker();clearToggleLimit();b.performCommand("autosell on");ok(enabled(b),"second player can opt in independently");
                warpMine();
            });
        });
    }
    private void warpMine(){
        World spawn=Bukkit.getWorld("spawn_world");if(spawn==null)throw new AssertionError("spawn missing");
        a.teleport(new Location(spawn,-85.5,97,4.5,0,90));pick(a,new ItemStack(Material.IRON_PICKAXE));
        protectedBlock=spawn.getBlockAt(-86,94,4);protectedBlock.setType(Material.IRON_ORE,false);double before=sold(a),mult=factor(a);a.breakBlock(protectedBlock);
        later(5,()->{
            int gained=count(a,Material.IRON_INGOT,false);ok(gained>0,"warp mine gives its fresh iron to inventory before sale");flush();
            ok(count(a,Material.IRON_INGOT,false)==0&&near(sold(a),before+gained*70*mult),"warp mine reward is sold at the correct price and multiplier after durable credit");
            cancelled();
        });
    }
    private void cancelled(){
        pick(a,smelter());protectedBlock=ore(a,40,Material.COPPER_ORE);cancelBreak=true;double before=sold(a);
        boolean broken=a.breakBlock(protectedBlock);later(5,()->{
            flush();ok(!broken&&protectedBlock.getType()==Material.COPPER_ORE&&sold(a)==before&&count(a,Material.COPPER_INGOT,false)==0,
                    "late protection cancellation grants neither resources nor sale credit");
            cancelBreak=false;vanilla();
        });
    }
    private void vanilla(){
        pick(a,new ItemStack(Material.DIAMOND_PICKAXE));protectedBlock=ore(a,50,Material.DIAMOND_ORE);double before=sold(a),mult=factor(a);
        a.breakBlock(protectedBlock);Item item=firstDrop(protectedBlock.getLocation(),Material.DIAMOND);
        if(item==null)throw new AssertionError("vanilla diamond drop missing");item.setPickupDelay(5);item.teleport(a.getLocation().add(0,0.15,0));item.setVelocity(new org.bukkit.util.Vector());
        later(30,()->{
            int found=plainIncludingOffhand(a,Material.DIAMOND);ok(found==7,"vanilla ore is really picked up by the owner (old six plus new one; found="+found+", valid="+item.isValid()+")");
            flush();ok(count(a,Material.DIAMOND,false)==4&&a.getInventory().getItemInOffHand().getAmount()==2&&near(sold(a),before+350*mult),
                    "vanilla pickup sells only the observed new diamond at the right price, preserving old storage and offhand");
            ok(count(a,Material.DIAMOND,true)==3&&b.getInventory().getItem(0).getAmount()==6,"named items and another player's inventory stay unchanged");
            failedWrite();
        });
    }
    private void failedWrite(){
        pick(a,smelter());protectedBlock=ore(a,60,Material.COPPER_ORE);failedSold=sold(a);double coins=core.getStore().getCoins(a.getName());
        a.breakBlock(protectedBlock);later(5,()->{
            ok(count(a,Material.COPPER_INGOT,false)==4,"failed-write fixture received normal loot");
            blockSave();flush();restore();
            ok(count(a,Material.COPPER_INGOT,false)==4&&sold(a)==failedSold&&core.getStore().getCoins(a.getName())==coins,
                    "failed durable batch keeps both resources and balances unchanged");
            a.getInventory().remove(Material.COPPER_INGOT);changedInventory();
        });
    }
    private void changedInventory(){
        pick(a,smelter());protectedBlock=ore(a,70,Material.COPPER_ORE);changeSold=sold(a);a.breakBlock(protectedBlock);
        later(5,()->{
            ItemStack found=null;int slot=-1;
            for(int i=0;i<36;i++){ItemStack it=a.getInventory().getItem(i);if(it!=null&&it.getType()==Material.COPPER_INGOT){found=it.clone();slot=i;break;}}
            if(found==null)throw new AssertionError("change fixture missing copper");
            a.getInventory().setItem(slot,null);a.getInventory().setItem(15,found);flush();
            ok(count(a,Material.COPPER_INGOT,false)==4&&sold(a)==changeSold,"moving inventory during the batch skips the sale rather than touching old items");
            a.getInventory().remove(Material.COPPER_INGOT);turnOff();
        });
    }
    private void turnOff(){
        pick(a,smelter());protectedBlock=ore(a,80,Material.COPPER_ORE);offSold=sold(a);a.breakBlock(protectedBlock);
        later(5,()->{
            clearToggleLimit();a.performCommand("autosell off");flush();
            ok(!enabled(a)&&count(a,Material.COPPER_INGOT,false)==4&&sold(a)==offSold,"turning off discards an unsold batch without taking its resources");
            a.getInventory().remove(Material.COPPER_INGOT);clearToggleLimit();a.performCommand("autosell on");ok(enabled(a),"autosell can be enabled again");guardedSales();
        });
    }
    private void guardedSales(){
        pick(a,smelter());protectedBlock=ore(a,82,Material.COPPER_ORE);double before=sold(a);a.breakBlock(protectedBlock);
        later(5,()->{
            core.getStore().toggleQuickFreeze(a.getUniqueId());flush();core.getStore().clearQuickFreeze(a.getUniqueId());
            ok(count(a,Material.COPPER_INGOT,false)==4&&sold(a)==before,"quick freeze before the batch preserves resources and denies payment");
            a.getInventory().remove(Material.COPPER_INGOT);pick(a,smelter());protectedBlock=ore(a,84,Material.COPPER_ORE);a.breakBlock(protectedBlock);
            later(5,()->{
                core.getConfig().set("enable-builtin-auth",true);core.getStore().setAuthenticated(a,false);flush();core.getConfig().set("enable-builtin-auth",false);
                ok(count(a,Material.COPPER_INGOT,false)==4&&sold(a)==before,"losing builtin authentication before the batch preserves resources");
                a.getInventory().remove(Material.COPPER_INGOT);numericFailure();
            });
        });
    }
    private void numericFailure(){
        pick(a,smelter());protectedBlock=ore(a,86,Material.COPPER_ORE);double before=sold(a),coins=core.getStore().getCoins(a.getName());a.breakBlock(protectedBlock);
        later(5,()->{
            String path="players."+a.getName().toLowerCase(Locale.ROOT)+".coins";yaml().set(path,Double.MAX_VALUE);flush();
            ok(count(a,Material.COPPER_INGOT,false)==4&&sold(a)==before&&core.getStore().getCoins(a.getName())==Double.MAX_VALUE,
                    "an unrepresentable balance blocks the batch without taking resources");
            yaml().set(path,coins);a.getInventory().remove(Material.COPPER_INGOT);bothPlayers();
        });
    }
    private void bothPlayers(){
        pick(a,smelter());pick(b,smelter());Block aa=ore(a,90,Material.COPPER_ORE),bb=ore(b,100,Material.IRON_ORE);
        aSold=sold(a);bSold=sold(b);double am=factor(a),bm=factor(b);a.breakBlock(aa);b.breakBlock(bb);
        later(5,()->{
            int iron=count(b,Material.IRON_INGOT,false);
            ok(count(a,Material.COPPER_INGOT,false)==4&&iron>=2&&iron<=3,"two players' smelt rewards are delivered independently (iron="+iron+")");
            flush();ok(near(sold(a),aSold+4*25*am)&&near(sold(b),bSold+iron*70*bm)&&count(a,Material.COPPER_INGOT,false)==0&&count(b,Material.IRON_INGOT,false)==0,
                    "one batched commit credits both players at their prices/multipliers and removes their own rewards");
            ok(count(a,Material.DIAMOND,false)==4&&count(b,Material.DIAMOND,false)==6,"batch still preserves both players' old diamonds");
            metadata();
        });
    }
    private void metadata(){
        pick(a,new ItemStack(Material.DIAMOND_PICKAXE));protectedBlock=ore(a,110,Material.DIAMOND_ORE);namedDrop=true;double before=sold(a);
        a.breakBlock(protectedBlock);namedDrop=false;Item item=firstDrop(protectedBlock.getLocation(),Material.DIAMOND);
        if(item==null)throw new AssertionError("named ore drop missing");item.setPickupDelay(5);item.teleport(a.getLocation().add(0,0.15,0));item.setVelocity(new org.bukkit.util.Vector());
        later(25,()->{
            flush();ok(count(a,Material.DIAMOND,true)==4&&sold(a)==before,"another plugin's named ore drop is kept, not autosold");
            otherOwner();
        });
    }
    private void otherOwner(){
        pick(a,new ItemStack(Material.DIAMOND_PICKAXE));protectedBlock=ore(a,120,Material.DIAMOND_ORE);double before=sold(b);
        b.teleport(new Location(world,120.5,160,20.5));a.breakBlock(protectedBlock);Item item=firstDrop(protectedBlock.getLocation(),Material.DIAMOND);
        if(item==null)throw new AssertionError("foreign pickup drop missing");item.setPickupDelay(8);item.teleport(b.getLocation().add(0,0.15,0));item.setVelocity(new org.bukkit.util.Vector());
        a.teleport(new Location(world,140.5,161,20.5));
        later(30,()->{
            flush();ok(count(b,Material.DIAMOND,false)==7&&sold(b)==before,"another player picking up the drop keeps it instead of triggering its owner's autosell");
            fullInventory();
        });
    }
    private void fullInventory(){
        a.getInventory().clear();for(int i=0;i<36;i++)a.getInventory().setItem(i,new ItemStack(Material.DIRT,64));
        pick(a,smelter());protectedBlock=ore(a,150,Material.COPPER_ORE);double before=sold(a);a.breakBlock(protectedBlock);
        later(4,()->{
            Item item=firstDrop(protectedBlock.getLocation(),Material.COPPER_INGOT);if(item==null)throw new AssertionError("full inventory overflow missing");
            item.setPickupDelay(200);flush();ok(ground(protectedBlock.getLocation(),Material.COPPER_INGOT)==4&&sold(a)==before,"full inventory keeps overflow as a ground item without premature sale");
            a.getInventory().setItem(0,null);item.teleport(a.getLocation().add(0,0.15,0));item.setVelocity(new org.bukkit.util.Vector());item.setPickupDelay(2);
            later(20,()->{
                ok(count(a,Material.COPPER_INGOT,false)==4,"owner can really pick up the overflow after freeing a slot");flush();
                ok(count(a,Material.COPPER_INGOT,false)==0&&sold(a)>before,"fresh overflow is sold only after successful pickup");
                networkMine();
            });
        });
    }
    private void networkMine(){
        a.getInventory().clear();a.getInventory().setItemInOffHand(null);a.getInventory().setItem(0,new ItemStack(Material.DIAMOND,4));
        ItemStack tool=new ItemStack(Material.DIAMOND_PICKAXE);tool.addEnchantment(org.bukkit.enchantments.Enchantment.EFFICIENCY,5);pick(a,tool);
        protectedBlock=world.getBlockAt(170,160,20);protectedBlock.setType(Material.DIAMOND_ORE,false);
        world.getBlockAt(170,159,22).setType(Material.STONE,false);a.teleport(new Location(world,170.5,160,22.5,180,0));
        double before=sold(a),mult=factor(a);networkMine=true;getLogger().info("KG-AUTO REQUEST DIG 170 160 20");
        later(90,()->{
            networkMine=false;
            ok(protectedBlock.getType().isAir()&&count(a,Material.DIAMOND,false)==5,"real network dig breaks and picks up the vanilla ore");
            flush();ok(count(a,Material.DIAMOND,false)==4&&near(sold(a),before+350*mult),"network-mined diamond is sold at the correct price without touching the old stack");
            finalState();
        });
    }
    private void finalState(){
        clearToggleLimit();b.performCommand("autosell off");
        ok(enabled(a)&&!enabled(b),"per-player enabled and disabled states remain independent");
        a.getInventory().clear();a.getInventory().setItem(0,new ItemStack(Material.DIAMOND,7));a.saveData();b.saveData();core.getStore().save();finish();
    }
    private void restart(){
        ok(enabled(a)&&!enabled(b),"opt-in and opt-out survive orderly restart");
        ok(count(a,Material.DIAMOND,false)==7,"unsold old inventory survives restart");
        ok(sold(a)>0&&sold(b)>0,"buyer progress for both players survives restart");finish();
    }
    private void crash(){
        pauseWorker();a.getInventory().clear();a.getInventory().setItem(0,new ItemStack(Material.DIAMOND,7));pick(a,smelter());
        protectedBlock=ore(a,160,Material.COPPER_ORE);double before=sold(a);a.breakBlock(protectedBlock);
        later(5,()->{
            flush();ok(count(a,Material.COPPER_INGOT,false)==0&&sold(a)>before,"crash fixture finished the sale and player-data save");
            yaml().set("testProbe.autoBuyerExpectedSold",sold(a));core.getStore().save();
            getLogger().info("KG-AUTO CRASH_READY");
        });
    }
    private void recover(){
        ok(enabled(a)&&count(a,Material.DIAMOND,false)==7&&count(a,Material.COPPER_INGOT,false)==0,
                "opt-in and saved inventory survive SIGKILL after completed sale");
        ok(Math.abs(sold(a)-yaml().getDouble("testProbe.autoBuyerExpectedSold"))<1e-7,"saved sale progression survives SIGKILL");finish();
    }
}
