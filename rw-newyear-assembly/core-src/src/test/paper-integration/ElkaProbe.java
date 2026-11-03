package net.kinggrief.probe;

import net.kinggrief.core.KingGriefCore;
import org.bukkit.*;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Exercises the live Paper command and model with three genuine protocol clients. */
public final class ElkaProbe extends JavaPlugin implements Listener {
    private KingGriefCore core;
    private Player owner,admin,ordinary;
    private boolean started,finished;
    private int checks;
    @Override public void onEnable(){saveDefaultConfig();core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");Bukkit.getPluginManager().registerEvents(this,this);}
    @EventHandler public void join(PlayerJoinEvent event){
        Player p=event.getPlayer();if(p.getName().equals("TreeHelper"))owner=p;if(p.getName().equals("TreeFriend"))admin=p;if(p.getName().equals("TreeObserver"))ordinary=p;
        if(!started&&owner!=null&&admin!=null&&ordinary!=null){started=true;later(20,()->{
            Location spawn=core.getSpawnEngine().getPreparedSpawnArrival();
            if(spawn==null)throw new AssertionError("prepared spawn missing");
            Location near=new Location(spawn.getWorld(),0.5,101,17.5);owner.teleport(near);admin.teleport(near);ordinary.teleport(near);
            later(100,this::run);
        });}
    }
    private void later(long ticks,Runnable action){Bukkit.getScheduler().runTaskLater(this,()->{try{action.run();}catch(Throwable e){fail(e);}},ticks);}
    private void ok(boolean b,String text){if(!b)throw new AssertionError(text);checks++;getLogger().info("KG-ELKA PASS "+text);}
    private void fail(Throwable e){if(finished)return;finished=true;getLogger().severe("KG-ELKA RESULT FAIL "+e);e.printStackTrace();}
    private void finish(){finished=true;getLogger().info("KG-ELKA RESULT PASS "+checks+" checks");}
    private Object field(Object obj,String name){try{Field f=obj.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(obj);}catch(Exception e){throw new RuntimeException(e);}}
    private void field(Object obj,String name,Object value){try{Field f=obj.getClass().getDeclaredField(name);f.setAccessible(true);f.set(obj,value);}catch(Exception e){throw new RuntimeException(e);}}
    private Object model(){return field(core,"spawnTreeModel");}
    private YamlConfiguration data(){return (YamlConfiguration)field(core.getStore(),"yml");}
    private int level(){return data().getInt("spawnTree.modelLevel",0);}
    @SuppressWarnings("unchecked") private List<BlockDisplay> displays(){return (List<BlockDisplay>)field(model(),"displays");}
    private boolean permitted(CommandSender sender){try{java.lang.reflect.Method m=model().getClass().getDeclaredMethod("permitted",CommandSender.class);m.setAccessible(true);return (Boolean)m.invoke(model(),sender);}catch(Exception e){throw new RuntimeException(e);}}
    private CommandSender fake(Class<?> type){return (CommandSender)Proxy.newProxyInstance(getClassLoader(),new Class<?>[]{type},(proxy,method,args)->{
        Class<?> r=method.getReturnType();if(r==boolean.class)return false;if(r==int.class)return 0;if(r==long.class)return 0L;if(r==float.class)return 0f;if(r==double.class)return 0d;return null;
    });}
    private void rank(Player p,String id){core.getStore().setSavedRank(p.getName(),id);}
    private void command(Player p,String text){field(model(),"lastChangeNanos",0L);p.performCommand("elka "+text);}
    private void near(float actual,float expected,String msg){ok(Math.abs(actual-expected)<0.005,msg+" actual="+actual+" expected="+expected);}
    private void blocked(Runnable task){
        Path file=core.getDataFolder().toPath().resolve("data.yml"),backup=file.resolveSibling("elka-save-test.yml");
        try{Files.move(file,backup);Files.createSymbolicLink(file,backup.getFileName());task.run();}
        catch(Exception e){throw new RuntimeException(e);}
        finally{try{if(Files.isSymbolicLink(file))Files.delete(file);if(Files.exists(backup))Files.move(backup,file);}catch(Exception e){throw new RuntimeException(e);}}
    }
    private void run(){
        ok(core.getDescription().getVersion().equals("7.6.0"),"new Core version loaded");
        World w=core.getSpawnEngine().getPreparedSpawnArrival().getWorld();
        ok(displays().size()==163&&displays().stream().allMatch(e->e.isValid()&&!e.isPersistent()),"all 163 nonpersistent display parts are loaded");
        ok(w.getBlockAt(0,130,0).getType()==Material.SPRUCE_WOOD&&w.getBlockAt(0,140,0).getType().isAir(),"separate trunk and clear scalable tree envelope");
        rank(owner,"owner");rank(admin,"admin");rank(ordinary,"default");owner.setOp(false);admin.setOp(false);ordinary.setOp(true);
        if(getConfig().getInt("phase")==2){
            ok(level()==2,"saved level survives an abrupt process kill");
            BlockDisplay gift=displays().get(132);
            near(gift.getTransformation().getTranslation().x(),-13.65f*1.2f,"gift reloads at the persisted factor");
            command(owner,"-2");ok(level()==0,"resizing after restart works");
            later(40,this::finish);return;
        }
        ok(level()==0,"fresh default level is zero");
        ok(permitted(owner)&&permitted(admin)&&!permitted(ordinary),"owner/admin without OP are allowed, plain OP is denied");
        ok(permitted(Bukkit.getConsoleSender())&&permitted(fake(RemoteConsoleCommandSender.class))&&!permitted(fake(BlockCommandSender.class)),"console and RCON work; command blocks are denied");
        for(String id:new String[]{"techadmin","curator","curator_discord","curator_kp","curator_media","curator_dkp","gladmin","stadmin","admin","mladmin","downer","dadmin","dmladmin"}){
            rank(admin,id);ok(permitted(admin),id+" is allowed");
        }
        for(String id:new String[]{"glmoder","moder","opka","dkp","default"}){rank(admin,id);ok(!permitted(admin),id+" is denied");}
        rank(admin,"admin");
        command(ordinary,"3");ok(level()==0,"ordinary OP cannot resize");
        ok(Bukkit.getPluginCommand("elka").tabComplete(owner,"elka",new String[]{"-"}).containsAll(java.util.Arrays.asList("-1","-2","-3")),"authorized tab completion lists negative steps");
        ok(Bukkit.getPluginCommand("elka").tabComplete(ordinary,"elka",new String[]{""}).isEmpty(),"unprivileged tab completion is hidden");
        int season=data().getInt("winterTree.total",0);int snow=core.getStore().getSnowflakes(owner.getName());
        float before=displays().get(132).getTransformation().getTranslation().x();
        command(owner,"3");ok(level()==3,"+3 is relative to level zero");
        near(displays().get(132).getTransformation().getTranslation().x(),before*1.3f,"gift moves and scales with the tree");
        owner.performCommand("elka -1");ok(level()==3,"one-second rate limit prevents spam");
        command(owner,"1");ok(level()==3,"upper bound rejects overflow");
        command(admin,"-3");ok(level()==0,"administrator can decrease three steps");
        command(admin,"-3");ok(level()==-3,"lower level is reachable");
        command(admin,"-1");ok(level()==-3,"lower bound rejects overflow");
        command(owner,"2");ok(level()==-1,"+2 changes the level relatively");
        command(owner,"1");ok(level()==0,"+1 changes the level relatively");
        command(owner,"-2");ok(level()==-2,"-2 changes the level relatively");
        command(owner,"3");ok(level()==1,"+3 from -2 reaches +1");
        command(owner,"0");command(owner,"4");command(owner,"-4");command(owner,"1 2");command(owner,"2147483648");ok(level()==1,"invalid and oversized arguments leave state untouched");
        blocked(()->command(owner,"1"));ok(level()==1,"failed durable save rolls back without resizing");
        core.getConfig().set("enable-builtin-auth",true);core.getStore().setAuthenticated(owner,false);command(owner,"1");ok(level()==1,"unauthenticated owner denied");
        core.getStore().setAuthenticated(owner,true);core.getConfig().set("enable-builtin-auth",false);
        core.getStore().toggleQuickFreeze(admin.getUniqueId());command(admin,"1");ok(level()==1,"frozen admin denied");core.getStore().toggleQuickFreeze(admin.getUniqueId());
        ok(data().getInt("winterTree.total",0)==season&&core.getStore().getSnowflakes(owner.getName())==snow,"season and snowflake balance are independent");
        ok(core.getSpawnEngine().getSantaGiftAt(w.getBlockAt(0,100,24))==0 && w.getBlockAt(0,100,24).getType()==Material.CHEST,"separate /gifts hunt chest remains present and addressable");
        ok(w.getBlockAt(0,130,0).getType()==Material.SPRUCE_WOOD&&w.getBlockAt(0,140,0).getType().isAir(),"all sizes leave physical blocks unchanged");
        command(owner,"1");ok(level()==2,"final +2 level durably stored before crash");
        later(60,this::finish);
    }
}
