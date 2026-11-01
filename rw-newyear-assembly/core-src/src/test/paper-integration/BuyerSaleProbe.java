package net.kinggrief.probe;

import net.kinggrief.core.KingGriefCore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Disposable Paper checks for fail-closed manual buyer sales, using a real protocol client. */
public final class BuyerSaleProbe extends JavaPlugin implements Listener {
    private KingGriefCore core;private Player a,b;private boolean running,done;private int checks;
    private Path data,backup;private double initialCoins,initialSold,mult,firstEarn,allEarn;
    @Override public void onEnable(){saveDefaultConfig();core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");Bukkit.getPluginManager().registerEvents(this,this);}
    @EventHandler public void join(PlayerJoinEvent event){
        if("BuyerTester".equals(event.getPlayer().getName()))a=event.getPlayer();if("BuyerOther".equals(event.getPlayer().getName()))b=event.getPlayer();
        if(a!=null&&b!=null&&!running){running=true;later(40,this::begin);}
    }
    private void later(long ticks,Runnable runnable){Bukkit.getScheduler().runTaskLater(this,()->{try{runnable.run();}catch(Throwable error){fail(error);}},ticks);}
    private void ok(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;getLogger().info("KG-BUYER PASS "+message);}
    private void fail(Throwable error){if(done)return;done=true;restore();getLogger().severe("KG-BUYER RESULT FAIL "+error);error.printStackTrace();}
    private void finish(){done=true;restore();getLogger().info("KG-BUYER REQUEST REPORT");getLogger().info("KG-BUYER RESULT PASS "+checks+" checks");}
    private void request(String value){getLogger().info("KG-BUYER REQUEST "+value);}
    private void block(){try{
        data=core.getDataFolder().toPath().resolve("data.yml");backup=data.resolveSibling("buyer-test-backup.yml");
        Files.move(data,backup);Files.createSymbolicLink(data,backup.getFileName());
    }catch(Exception error){throw new RuntimeException(error);}}
    private void restore(){try{
        if(data!=null&&Files.isSymbolicLink(data))Files.delete(data);
        if(backup!=null&&Files.exists(backup))Files.move(backup,data);data=null;backup=null;
    }catch(Exception error){getLogger().severe("Could not restore disposable test file: "+error);}}
    private org.bukkit.configuration.file.YamlConfiguration yaml(){try{Field f=core.getStore().getClass().getDeclaredField("yml");f.setAccessible(true);return (org.bukkit.configuration.file.YamlConfiguration)f.get(core.getStore());}catch(Exception e){throw new RuntimeException(e);}}
    private boolean empty(int slot){ItemStack item=a.getInventory().getItem(slot);return item==null||item.getType().isAir();}
    private boolean namedDiamond(){ItemStack it=a.getInventory().getItem(1);return it!=null&&it.getAmount()==3&&it.getType()==Material.DIAMOND&&it.hasItemMeta();}
    private void begin(){
        int phase=getConfig().getInt("phase",1);
        if(phase==2){restart();return;}
        if(phase==3){crashFixture();return;}
        if(phase==4){recover();return;}
        a.getInventory().clear();b.getInventory().clear();
        a.getInventory().setItem(0,new ItemStack(Material.DIAMOND,5));
        ItemStack named=new ItemStack(Material.DIAMOND,3);ItemMeta meta=named.getItemMeta();meta.setDisplayName("Protected test diamond");named.setItemMeta(meta);a.getInventory().setItem(1,named);
        a.getInventory().setItem(2,new ItemStack(Material.COAL,10));a.getInventory().setItem(3,new ItemStack(Material.CARROT,4));a.getInventory().setItemInOffHand(new ItemStack(Material.IRON_INGOT,2));
        b.getInventory().setItem(0,new ItemStack(Material.EMERALD,6));core.getStore().save();
        initialCoins=core.getStore().getCoins(a.getName());initialSold=core.getStore().getBuyerSoldTotal(a.getName());
        mult=core.getStore().getBuyerMultiplier(a.getName())*(1.0+core.getRanks().getRank(a).tier*0.12);firstEarn=5*350*mult;
        a.performCommand("buyer");
        InventoryClickEvent swap=new InventoryClickEvent(a.getOpenInventory(),InventoryType.SlotType.CONTAINER,13,ClickType.NUMBER_KEY,InventoryAction.HOTBAR_SWAP,0);
        Bukkit.getPluginManager().callEvent(swap);
        ok(swap.isCancelled()&&a.getInventory().getItem(0).getAmount()==5&&core.getStore().getCoins(a.getName())==initialCoins,
                "hotbar number key cannot accidentally sell or move the inventory");
        InventoryClickEvent denied=new InventoryClickEvent(a.getOpenInventory(),InventoryType.SlotType.CONTAINER,13,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        denied.setCancelled(true);Bukkit.getPluginManager().callEvent(denied);
        ok(a.getInventory().getItem(0).getAmount()==5&&core.getStore().getCoins(a.getName())==initialCoins,
                "an earlier cancelled GUI click does not sell anything");
        a.setItemOnCursor(new ItemStack(Material.EMERALD));
        InventoryClickEvent carrying=new InventoryClickEvent(a.getOpenInventory(),InventoryType.SlotType.CONTAINER,13,ClickType.LEFT,InventoryAction.SWAP_WITH_CURSOR);
        Bukkit.getPluginManager().callEvent(carrying);
        ok(carrying.isCancelled()&&a.getItemOnCursor().getType()==Material.EMERALD&&a.getInventory().getItem(0).getAmount()==5
                &&core.getStore().getCoins(a.getName())==initialCoins,"a click with an occupied cursor cannot trigger a sale");
        a.setItemOnCursor(null);
        later(6,()->{
            block();request("CLICK 13");later(14,()->{
                restore();
                ok(a.getInventory().getItem(0).getAmount()==5&&namedDiamond()&&core.getStore().getCoins(a.getName())==initialCoins
                        &&core.getStore().getBuyerSoldTotal(a.getName())==initialSold,"failed durable write keeps items, coins and progression unchanged");
                request("CLICK 13");later(14,()->{
                    ok(empty(0)&&namedDiamond()&&a.getInventory().getItem(2).getAmount()==10,"successful material sale removes only plain matching items");
                    ok(Math.abs(core.getStore().getCoins(a.getName())-(initialCoins+firstEarn))<1e-7
                            &&Math.abs(core.getStore().getBuyerSoldTotal(a.getName())-(initialSold+firstEarn))<1e-7,
                            "money and buyer progression commit together using existing multipliers");
                    simulateRecentAttempt();
                    InventoryClickEvent rapid=new InventoryClickEvent(a.getOpenInventory(),InventoryType.SlotType.CONTAINER,10,ClickType.LEFT,InventoryAction.PICKUP_ALL);
                    Bukkit.getPluginManager().callEvent(rapid);
                    ok(rapid.isCancelled()&&a.getInventory().getItem(2).getAmount()==10
                            &&Math.abs(core.getStore().getCoins(a.getName())-(initialCoins+firstEarn))<1e-7,
                            "a rapid repeat is rate-limited without taking items or paying twice");
                    later(14,()->{
                        double nextMult=core.getStore().getBuyerMultiplier(a.getName())*(1.0+core.getRanks().getRank(a).tier*0.12);
                        allEarn=(10*25+4*15+2*70)*nextMult;request("CLICK 4");later(14,()->{
                            ItemStack off=a.getInventory().getItemInOffHand();
                            ok(empty(2)&&empty(3)&&(off==null||off.getType().isAir())&&namedDiamond(),"sell-all preserves named items but sells ordinary resources including offhand");
                            ok(Math.abs(core.getStore().getCoins(a.getName())-(initialCoins+firstEarn+allEarn))<1e-7
                                    &&Math.abs(core.getStore().getBuyerSoldTotal(a.getName())-(initialSold+firstEarn+allEarn))<1e-7,
                                    "sell-all applies the current buyer multiplier and correct prices");
                            ok(b.getInventory().getItem(0).getAmount()==6&&core.getStore().getCoins(b.getName())==initialCoins,
                                    "another player's items and balance are unaffected");
                            numericFailure();
                        });
                    });
                });
            });
        });
    }
    @SuppressWarnings("unchecked") private void simulateRecentAttempt(){
        try{Field field=core.getGuis().getClass().getDeclaredField("buyerAttempts");field.setAccessible(true);
            ((java.util.Map<java.util.UUID,Long>)field.get(core.getGuis())).put(a.getUniqueId(),System.nanoTime());
        }catch(Exception error){throw new RuntimeException(error);}
    }
    private void numericFailure(){
        double valid=core.getStore().getCoins(a.getName()),sold=core.getStore().getBuyerSoldTotal(a.getName());
        a.getInventory().setItem(5,new ItemStack(Material.COAL,1));String path="players."+a.getName().toLowerCase(Locale.ROOT)+".coins";
        yaml().set(path,Double.MAX_VALUE);core.getStore().save();request("CLICK 10");later(14,()->{
            ok(a.getInventory().getItem(5)!=null&&core.getStore().getCoins(a.getName())==Double.MAX_VALUE
                    &&core.getStore().getBuyerSoldTotal(a.getName())==sold,"unrepresentable credit fails without removing resources");
            yaml().set(path,valid);core.getStore().save();request("CLICK 10");later(14,()->{
                ok(empty(5)&&core.getStore().getCoins(a.getName())>valid,"sale succeeds again once the balance is valid");
                a.closeInventory();core.getStore().save();later(6,this::finish);
            });
        });
    }
    private void crashFixture(){
        double before=core.getStore().getCoins(a.getName());
        double beforeSold=core.getStore().getBuyerSoldTotal(a.getName());
        double factor=core.getStore().getBuyerMultiplier(a.getName())*(1.0+core.getRanks().getRank(a).tier*0.12);
        double earned=4*350*factor;
        yaml().set("testProbe.buyerExpectedCoins",before+earned);yaml().set("testProbe.buyerExpectedSold",beforeSold+earned);
        a.getInventory().setItem(0,new ItemStack(Material.DIAMOND,4));core.getStore().save();a.saveData();
        a.performCommand("buyer");later(8,()->{request("CLICK 13");later(14,()->{
            ok(empty(0)&&Math.abs(core.getStore().getCoins(a.getName())-(before+earned))<1e-7,
                    "crash fixture completed the durable credit and player-data save");
            getLogger().info("KG-BUYER CRASH_READY");
        });});
    }
    private void recover(){
        ok(empty(0)&&namedDiamond(),"sold plain diamonds remain removed after SIGKILL; named items remain");
        ok(Math.abs(core.getStore().getCoins(a.getName())-yaml().getDouble("testProbe.buyerExpectedCoins"))<1e-7
                &&Math.abs(core.getStore().getBuyerSoldTotal(a.getName())-yaml().getDouble("testProbe.buyerExpectedSold"))<1e-7,
                "committed buyer balance and progression survive SIGKILL");
        finish();
    }
    private void restart(){
        ok(namedDiamond()&&empty(0)&&empty(2)&&empty(3)&&empty(5),"sold items stay removed and metadata-bearing diamond survives restart");
        ok(core.getStore().getCoins(a.getName())>2500.0&&core.getStore().getBuyerSoldTotal(a.getName())>2000.0,
                "buyer credit and progression survive orderly restart");
        ok(b.getInventory().getItem(0)!=null&&b.getInventory().getItem(0).getAmount()==6,"other player's items survive restart");
        finish();
    }
}
