package net.kinggrief.probe;

import net.kinggrief.core.KingGriefCore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.List;
import java.util.Map;

/** Disposable Paper calendar checks including actual protocol menu clicks and restart. */
public final class CalendarProbe extends JavaPlugin implements Listener {
    private static final String ROOT="eventCalendar.entries";
    private KingGriefCore core;private Player a,b;private boolean running,done,watch;private int checks,teleports;
    private String keepA;private long base;private double coinsA,coinsB;
    private final Set<Integer> networkClicks=new HashSet<Integer>();
    @Override public void onEnable(){saveDefaultConfig();core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");Bukkit.getPluginManager().registerEvents(this,this);}
    @EventHandler public void join(PlayerJoinEvent event){
        if("CalendarTester".equals(event.getPlayer().getName()))a=event.getPlayer();if("CalendarOther".equals(event.getPlayer().getName()))b=event.getPlayer();
        if(a!=null&&b!=null&&!running){running=true;later(40,this::begin);}
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleport(PlayerTeleportEvent e){if(watch&&(e.getPlayer().equals(a)||e.getPlayer().equals(b)))teleports++;}
    @EventHandler(priority=EventPriority.MONITOR) public void networkClick(InventoryClickEvent e){
        if (e.getWhoClicked().equals(a) && e.getClick()==ClickType.LEFT && calendarOpen()
                && e.getInventory().getHolder()==a.getOpenInventory().getTopInventory().getHolder())
            networkClicks.add(e.getRawSlot());
    }
    private void later(long ticks,Runnable runnable){Bukkit.getScheduler().runTaskLater(this,()->{try{runnable.run();}catch(Throwable error){fail(error);}},ticks);}
    private void ok(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;getLogger().info("KG-CALENDAR PASS "+message);}
    private void fail(Throwable error){if(done)return;done=true;getLogger().severe("KG-CALENDAR RESULT FAIL "+error);error.printStackTrace();}
    private void finish(){done=true;getLogger().info("KG-CALENDAR REQUEST REPORT");getLogger().info("KG-CALENDAR RESULT PASS "+checks+" checks");}
    private void request(String command){getLogger().info("KG-CALENDAR REQUEST "+command);}
    // Wait for the real packet and resulting server view instead of assuming a
    // four-tick flush between Paper and the separately scheduled Node process.
    private void await(BooleanSupplier condition,String message,Runnable next){
        new BukkitRunnable(){int ticks;@Override public void run(){try{
            if(condition.getAsBoolean()){cancel();ok(true,message);next.run();}
            else if(++ticks>100){cancel();fail(new AssertionError(message+" (timed out)"));}
        }catch(Throwable error){try{cancel();}catch(Throwable ignored){}fail(error);}}}.runTaskTimer(this,1L,1L);
    }
    private Object field(Object obj,String name){try{Field f=obj.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(obj);}catch(Exception e){throw new RuntimeException(e);}}
    private Object manager(){return field(core,"eventCalendar");}
    private YamlConfiguration yaml(){return (YamlConfiguration)field(core.getStore(),"yml");}
    private List<String> keys(){ConfigurationSection root=yaml().getConfigurationSection(ROOT);return root==null?new ArrayList<String>():new ArrayList<String>(root.getKeys(false));}
    private void console(String command){Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"calendar "+command);}
    private String at(long millis){return Instant.ofEpochMilli(millis).toString();}
    private void save(){core.getStore().save();}
    private void withBlockedSave(Runnable action){
        Path data=core.getDataFolder().toPath().resolve("data.yml"),backup=data.resolveSibling("calendar-test-backup.yml");
        try{Files.move(data,backup);Files.createSymbolicLink(data,backup.getFileName());action.run();}
        catch(Exception error){throw new RuntimeException(error);}
        finally{try{if(Files.isSymbolicLink(data))Files.delete(data);if(Files.exists(backup))Files.move(backup,data);}catch(Exception error){throw new RuntimeException(error);}}
    }
    private void begin(){
        if(getConfig().getInt("phase",1)==2){restart();return;}
        core.getStore().save();base=System.currentTimeMillis();coinsA=core.getStore().getCoins(a.getName());coinsB=core.getStore().getCoins(b.getName());watch=true;
        String savedRank=core.getStore().getSavedRank(a.getName());
        core.getStore().setSavedRank(a.getName(),"owner");core.getConfig().set("enable-builtin-auth",true);core.getStore().setAuthenticated(a,false);
        a.performCommand("calendar add parkour "+at(base+3_600_000L));
        ok(keys().isEmpty(),"unauthenticated owner cannot add a schedule");
        core.getStore().setSavedRank(a.getName(),savedRank);core.getConfig().set("enable-builtin-auth",false);
        b.performCommand("calendar add boss "+at(base+3_600_000L));
        ok(keys().isEmpty(),"ordinary player cannot change the calendar");
        console("add unknown "+at(base+3_600_000L));console("add boss invalid");console("add boss "+at(base-10_000L));console("add boss "+at(base+91L*86_400_000L));
        ok(keys().isEmpty(),"invalid kind, date, past and excessive future are rejected");
        core.getStore().setSavedRank(a.getName(),"techadmin");a.performCommand("calendar add forge "+at(base+3_600_000L));
        ok(keys().size()==1&&"forge".equals(yaml().getString(ROOT+"."+keys().get(0)+".kind")),"TECH.ADMIN can schedule without an OP bypass");
        console("remove "+keys().get(0)+" confirm");core.getStore().setSavedRank(a.getName(),savedRank);
        long first=(base+3_600_000L)/1000*1000;String offset=OffsetDateTime.ofInstant(Instant.ofEpochMilli(first),ZoneOffset.ofHours(3)).toString();
        console("add parkour "+offset);console("add memory "+at(base+7_200_000L));console("add boss "+at(base+10_800_000L));
        List<String> created=keys();ok(created.size()==3&&created.stream().distinct().count()==3,"console adds three distinct durable announcements");
        keepA=created.get(0);String remove=created.get(2);
        ok(yaml().getLong(ROOT+"."+keepA+".at")==first,"explicit UTC offset is normalized without changing the instant");
        withBlockedSave(()->console("add duel "+at(base+4_000_000L)));
        ok(keys().size()==3,"failed add rolls back the in-memory calendar");
        console("remove "+remove);b.performCommand("calendar remove "+remove+" confirm");
        ok(keys().contains(remove),"removal requires confirmation and administrator authorization");
        withBlockedSave(()->console("remove "+remove+" confirm"));
        ok(keys().contains(remove),"failed removal restores the announcement");
        console("remove "+remove+" confirm");ok(keys().size()==2&&!keys().contains(remove),"confirmed removal commits only the selected announcement");
        for(int i=0;i<62;i++)putTemporary(i,base+4_000_000L+i*1000L);
        save();console("add duel "+at(base+5_000_000L));
        ok(keys().size()==64,"calendar is capped at 64 entries");
        for(int i=28;i<62;i++)removeTemporary(i);save();
        ok(keys().size()==30,"multi-page fixture has 30 entries");
        menuChecks();
    }
    private static String tempId(int index){return String.format(java.util.Locale.ROOT,"%08x",0x10000000+index);}
    private void putTemporary(int index,long at){String path=ROOT+"."+tempId(index);yaml().set(path+".kind","memory");yaml().set(path+".at",at);}
    private void removeTemporary(int index){yaml().set(ROOT+"."+tempId(index),null);}
    private boolean calendarOpen(){Object holder=a.getOpenInventory().getTopInventory().getHolder();return holder!=null&&holder.getClass().getName().endsWith("EventCalendarManager$Menu");}
    private int page(){return ((Number)field(a.getOpenInventory().getTopInventory().getHolder(),"page")).intValue();}
    private void menuChecks(){
        a.getInventory().clear();a.getInventory().setItem(0,new ItemStack(Material.DIAMOND,7));
        a.setItemOnCursor(new ItemStack(Material.GOLD_INGOT));a.performCommand("calendar");
        ok(!calendarOpen()&&a.getItemOnCursor().getType()==Material.GOLD_INGOT,"menu refuses to open over an occupied cursor");a.setItemOnCursor(null);
        a.performCommand("events");
        InventoryClickEvent button=new InventoryClickEvent(a.getOpenInventory(),InventoryType.SlotType.CONTAINER,31,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        Bukkit.getPluginManager().callEvent(button);ok(button.isCancelled(),"events calendar button cannot be taken");
        later(4,()->{
            ok(calendarOpen()&&page()==0,"events button opens the first private calendar page");
            InventoryClickEvent swap=new InventoryClickEvent(a.getOpenInventory(),InventoryType.SlotType.CONTAINER,10,ClickType.NUMBER_KEY,InventoryAction.HOTBAR_SWAP,0);
            Bukkit.getPluginManager().callEvent(swap);
            Map<Integer,ItemStack> changes=new HashMap<Integer,ItemStack>();changes.put(10,new ItemStack(Material.DIRT));
            InventoryDragEvent drag=new InventoryDragEvent(a.getOpenInventory(),new ItemStack(Material.DIRT),new ItemStack(Material.DIRT),false,changes);
            Bukkit.getPluginManager().callEvent(drag);
            ok(swap.isCancelled()&&drag.isCancelled()&&a.getInventory().getItem(0).getAmount()==7,"hotbar swaps and drags cannot transfer menu icons or alter items");
            request("CLICK 53");await(()->networkClicks.contains(53)&&calendarOpen()&&page()==1,"real network click opens page two",()->{
                request("CLICK 45");await(()->networkClicks.contains(45)&&calendarOpen()&&page()==0,"real network click returns to page one",()->{
                    request("CLICK 10");await(()->networkClicks.contains(10),"event selection reaches the server",()->{
                        ok(a.getInventory().getItem(0).getAmount()==7,"selecting an event does not give or remove items");
                        request("CLICK 49");await(()->networkClicks.contains(49)&&!calendarOpen(),"real network click closes the menu safely on the next tick",()->{
                            for(int i=0;i<28;i++)removeTemporary(i);save();reminders();
                        });
                    });
                });
            });
        });
    }
    private void tick(long now){try{Method method=manager().getClass().getDeclaredMethod("tick",long.class);method.setAccessible(true);method.invoke(manager(),now);}catch(Exception e){throw new RuntimeException(e);}}
    private void reminders(){
        long now=System.currentTimeMillis();for(int i=0;i<5;i++)putTemporary(i,now+660_000L);save();
        tick(now+61_000L);tick(now+61_000L);tick(now+601_000L);tick(now+661_000L);tick(now+662_000L);
        @SuppressWarnings("unchecked") Map<String,Integer> stages=(Map<String,Integer>)field(manager(),"stages");
        ok(stages.size()==5&&stages.values().stream().allMatch(stage->stage.intValue()==3),"five simultaneous reminders advance once through all three stages");
        for(int i=1;i<5;i++)removeTemporary(i);yaml().set(ROOT+"."+tempId(0)+".at",System.currentTimeMillis()-1000L);save();
        withBlockedSave(()->console("prune confirm"));ok(keys().contains(tempId(0)),"failed prune retains expired announcements");
        console("prune confirm");ok(!keys().contains(tempId(0))&&keys().size()==2,"confirmed prune removes expired entries but preserves future entries");
        ok(teleports==0&&core.getStore().getCoins(a.getName())==coinsA&&core.getStore().getCoins(b.getName())==coinsB
                &&core.getBossEvents().getActiveEvents().isEmpty(),"calendar does not teleport, charge or automatically start a boss event");
        later(10,this::finish);
    }
    private void restart(){
        List<String> entries=keys();ok(entries.size()==2,"future announcements survive full server restart");
        boolean parkour=false,memory=false;for(String id:entries){String kind=yaml().getString(ROOT+"."+id+".kind");parkour|="parkour".equals(kind);memory|="memory".equals(kind);}
        ok(parkour&&memory,"restart retains the kinds and excludes deleted and temporary announcements");
        a.performCommand("calendar");ok(calendarOpen(),"calendar GUI reopens after restart");
        // Leave the window open long enough for the network client to observe it.
        later(8,()->{a.closeInventory();later(3,this::finish);});
    }
}
