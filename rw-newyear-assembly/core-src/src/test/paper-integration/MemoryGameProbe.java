package net.kinggrief.probe;

import net.kinggrief.core.ItemFactory;
import net.kinggrief.core.KingGriefCore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Drives all twelve rounds through real protocol clicks; reads the generated pattern only as test fixture. */
public final class MemoryGameProbe extends JavaPlugin implements Listener {
    private static final int[] TILES={10,11,12,19,20,21,28,29,30};
    private KingGriefCore core;private Player a,b;private World world;private boolean started,done;
    private int checks;private Path blocked,backup;
    @Override public void onEnable(){saveDefaultConfig();core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");Bukkit.getPluginManager().registerEvents(this,this);}
    @EventHandler public void join(PlayerJoinEvent event){
        if("MemoryPlayer".equals(event.getPlayer().getName()))a=event.getPlayer();
        if("MemoryObserver".equals(event.getPlayer().getName()))b=event.getPlayer();
        if(a!=null&&b!=null&&!started){started=true;later(30,this::begin);}
    }
    private void later(long ticks,Runnable runnable){Bukkit.getScheduler().runTaskLater(this,()->{try{runnable.run();}catch(Throwable error){fail(error);}},ticks);}
    private void ok(boolean value,String text){if(!value)throw new AssertionError(text);checks++;getLogger().info("KG-MEM PASS "+text);}
    private void fail(Throwable error){if(done)return;restore();done=true;getLogger().severe("KG-MEM RESULT FAIL "+error);error.printStackTrace();}
    private void finish(){done=true;getLogger().info("KG-MEM REQUEST REPORT");getLogger().info("KG-MEM RESULT PASS "+checks+" checks");}
    private void request(String command){getLogger().info("KG-MEM REQUEST "+command);}
    private Object field(Object instance,String name){try{Field f=instance.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(instance);}catch(Exception error){throw new RuntimeException(error);}}
    private Object manager(){return field(core,"memoryGame");}
    @SuppressWarnings("unchecked") private Map<UUID,Object> sessions(){return (Map<UUID,Object>)field(manager(),"sessions");}
    private Object session(){return sessions().get(a.getUniqueId());}
    private String phase(){Object s=session();return s==null?"NONE":field(s,"phase").toString();}
    private int score(){return ((Integer)field(session(),"score")).intValue();}
    private int index(){return ((Integer)field(session(),"index")).intValue();}
    @SuppressWarnings("unchecked") private List<Integer> sequence(){return new ArrayList<Integer>((List<Integer>)field(session(),"sequence"));}
    private YamlConfiguration yaml(){return (YamlConfiguration)field(core.getStore(),"yml");}
    private int savedScore(){return yaml().getInt("memoryGame.records."+a.getUniqueId()+".score");}
    private boolean dirty(){return ((Boolean)field(manager(),"dirty")).booleanValue();}
    private void flush(){try{Method m=manager().getClass().getDeclaredMethod("flush");m.setAccessible(true);m.invoke(manager());}catch(Exception error){throw new RuntimeException(error);}}
    private void begin(){
        world=Bukkit.getWorld("world");world.getChunkAtAsync(16,16,true).whenComplete((chunk,error)->Bukkit.getScheduler().runTask(this,()->{
            try{
                if(error!=null||chunk==null)throw new AssertionError("world chunk",error);world.addPluginChunkTicket(16,16,this);
                world.getBlockAt(256,80,256).setType(Material.STONE,false);world.getBlockAt(261,80,256).setType(Material.STONE,false);
                a.teleport(new Location(world,256.5,81,256.5));b.teleport(new Location(world,261.5,81,256.5));
                a.setGameMode(GameMode.SURVIVAL);b.setGameMode(GameMode.SURVIVAL);a.getInventory().clear();a.getInventory().setItem(0,new ItemStack(Material.DIAMOND,7));
                core.getStore().save();
                if(getConfig().getInt("phase",1)==1)later(30,this::restrictions);else later(30,this::restart);
            }catch(Throwable failure){fail(failure);}
        }));
    }
    private void restrictions(){
        core.getConfig().set("enable-builtin-auth",true);core.getStore().setAuthenticated(a,false);
        a.performCommand("snowmemory");ok(session()==null,"unauthenticated player cannot start the game");core.getConfig().set("enable-builtin-auth",false);
        a.setItemOnCursor(new ItemStack(Material.EMERALD));a.performCommand("snowmemory");ok(session()==null,"game refuses to open while a cursor item is held");a.setItemOnCursor(null);
        core.getStore().toggleQuickFreeze(a.getUniqueId());a.performCommand("snowmemory");ok(session()==null,"moderation-frozen player cannot start");core.getStore().clearQuickFreeze(a.getUniqueId());
        core.getHud().tagCombat(a);a.performCommand("snowmemory");ok(session()==null,"combat-tagged player cannot start");core.getHud().clearCombat(a.getUniqueId());
        core.getGuis().openEventsMenu(a);Inventory events=a.getOpenInventory().getTopInventory();
        ok(events.getItem(30).getType()==Material.LIGHT_BLUE_STAINED_GLASS&&events.getItem(32).getType()==Material.BRICKS,"events menu exposes memory without replacing build contest");
        InventoryClickEvent open=new InventoryClickEvent(a.getOpenInventory(),InventoryType.SlotType.CONTAINER,30,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        Bukkit.getPluginManager().callEvent(open);
        later(3,()->{
            ok(open.isCancelled()&&session()!=null&&"SHOW".equals(phase()),"events button starts a private game on the next tick");
            InventoryClickEvent steal=new InventoryClickEvent(a.getOpenInventory(),InventoryType.SlotType.CONTAINER,10,ClickType.NUMBER_KEY,InventoryAction.HOTBAR_SWAP,0);
            Bukkit.getPluginManager().callEvent(steal);ok(steal.isCancelled()&&a.getInventory().getItem(0).getAmount()==7,"hotbar swap cannot steal tiles or alter inventory");
            Map<Integer,ItemStack> map=new HashMap<Integer,ItemStack>();map.put(10,new ItemStack(Material.DIRT));
            InventoryDragEvent drag=new InventoryDragEvent(a.getOpenInventory(),new ItemStack(Material.DIRT),new ItemStack(Material.DIRT),false,map);
            Bukkit.getPluginManager().callEvent(drag);ok(drag.isCancelled(),"drag into game inventory is blocked");
            request("CLICK "+TILES[sequence().get(0).intValue()]);later(5,()->{
                ok("SHOW".equals(phase())&&score()==0&&index()==0,"real click during the show cannot advance the puzzle");
                awaitInput(0,this::wrongAnswer);
            });
        });
    }
    private void awaitInput(int attempts,Runnable next){
        if(attempts>200)throw new AssertionError("timed out awaiting input; phase="+phase());
        if("INPUT".equals(phase())){next.run();return;}
        if("NONE".equals(phase())||"ENDED".equals(phase()))throw new AssertionError("game ended before input: "+phase());
        later(5,()->awaitInput(attempts+1,next));
    }
    private void wrongAnswer(){
        int correct=sequence().get(0).intValue(),wrong=(correct+1)%9;request("CLICK "+TILES[wrong]);later(6,()->{
            ok("ENDED".equals(phase())&&score()==0&&savedScore()==0,"wrong network click ends the game without a score");
            request("CLICK 40");later(6,()->{
                ok(session()==null&&a.getInventory().getItem(0).getAmount()==7,"closing the puzzle leaves player items unchanged");
                later(45,this::startPerfect);
            });
        });
    }
    private void startPerfect(){
        a.performCommand("snowmemory");ok(session()!=null,"second game starts from the command");runRound(1);
    }
    private void runRound(int round){awaitInput(0,()->{
        List<Integer> seq=sequence();ok(seq.size()==round,"round "+round+" shows a sequence of the correct length");sendAnswer(round,seq,0);
    });}
    private void sendAnswer(int round,List<Integer> seq,int step){
        request("CLICK "+TILES[seq.get(step).intValue()]);later(5,()->{
            if(step+1<seq.size()){
                ok("INPUT".equals(phase())&&index()==step+1,"real input accepted in round "+round+" step "+(step+1));sendAnswer(round,seq,step+1);
            }else{
                ok(score()==round&&savedScore()==round,"completed round "+round+" updates the buffered personal record");
                if(round==8)afterEight();else if(round<12)runRound(round+1);else afterTwelve();
            }
        });
    }
    private void afterEight(){
        ok(core.getStore().hasUnlockedTitle(a.getName(),"memory_master"),"eight real rounds unlock the cosmetic title");
        ok(dirty(),"new record is buffered for periodic save");
        block();flush();ok(dirty()&&savedScore()==8,"failed save keeps the in-memory record for retry");restore();flush();ok(!dirty()&&savedScore()==8,"record save retries after storage recovers");
        runRound(9);
    }
    private void afterTwelve(){
        ok("ENDED".equals(phase())&&score()==12&&savedScore()==12,"all twelve real rounds finish at the score limit");
        a.damage(1.0);later(5,()->{
            ok(session()==null&&a.getOpenInventory().getTopInventory().getType()==InventoryType.CRAFTING,"damage closes the game instead of protecting the player");
            core.getGuis().openTitlesMenu(a);Inventory titles=a.getOpenInventory().getTopInventory();
            ok(titles.getItem(31).getType()==Material.NAME_TAG&&"title_memory_master".equals(ItemFactory.getCustomId(titles.getItem(31))),"new title appears unlocked in the title menu");
            InventoryClickEvent select=new InventoryClickEvent(a.getOpenInventory(),InventoryType.SlotType.CONTAINER,31,ClickType.LEFT,InventoryAction.PICKUP_ALL);
            Bukkit.getPluginManager().callEvent(select);ok(select.isCancelled()&&core.getStore().getActiveTitleDisplay(a.getName()).contains("ЛЕДЯНОЙ РАЗУМ"),"unlocked title can be selected");
            a.closeInventory();a.performCommand("snowmemory top");finish();
        });
    }
    private void block(){try{
        blocked=core.getDataFolder().toPath().resolve("data.yml");backup=blocked.resolveSibling("memory-test-backup.yml");
        Files.move(blocked,backup);Files.createSymbolicLink(blocked,backup.getFileName());
    }catch(Exception error){throw new RuntimeException(error);}}
    private void restore(){try{
        if(blocked!=null&&Files.isSymbolicLink(blocked))Files.delete(blocked);
        if(backup!=null&&Files.exists(backup))Files.move(backup,blocked);blocked=null;backup=null;
    }catch(Exception error){getLogger().severe("Could not restore disposable test file: "+error);}}
    private void restart(){
        ok(session()==null&&savedScore()==12,"record survives server restart without restoring an active session");
        ok(core.getStore().hasUnlockedTitle(a.getName(),"memory_master")&&core.getStore().getActiveTitleDisplay(a.getName()).contains("ЛЕДЯНОЙ РАЗУМ"),
                "unlocked and selected title survives restart");
        a.performCommand("snowmemory top");a.performCommand("snowmemory");ok(session()!=null,"fresh game starts after restart");
        awaitInput(0,()->{
            BukkitRunnable timeout=(BukkitRunnable)field(session(),"task");timeout.run();
            ok("ENDED".equals(phase())&&score()==0&&savedScore()==12,"input timeout ends game without reducing the previous best");
            a.performCommand("snowmemory stop");ok(session()==null,"stop command removes session and closes the game");finish();
        });
    }
}
