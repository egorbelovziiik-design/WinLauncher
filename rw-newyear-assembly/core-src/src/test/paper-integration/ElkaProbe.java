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
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

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
    private BlockDisplay part(String group,Material material,float x,float y,float scale){
        return displays().stream().filter(d->d.getScoreboardTags().contains("kg_elka_v77_"+group)
                &&d.getBlock().getMaterial()==material
                &&Math.abs(d.getTransformation().getTranslation().x()-x*scale)<.005f
                &&Math.abs(d.getTransformation().getTranslation().y()-y*scale)<.005f).findFirst()
                .orElseThrow(()->new AssertionError("missing part "+group+" "+material));
    }
    private Map<UUID,float[]> snapshot(){
        Map<UUID,float[]> result=new HashMap<UUID,float[]>();
        for(BlockDisplay d:displays()){
            org.bukkit.util.Transformation t=d.getTransformation();
            result.put(d.getUniqueId(),new float[]{t.getTranslation().x(),t.getTranslation().y(),t.getTranslation().z(),
                    t.getScale().x(),t.getScale().y(),t.getScale().z()});
        }
        return result;
    }
    private void groupAt(Map<UUID,float[]> original,String group,float factor){
        int count=0;
        for(BlockDisplay d:displays()){
            if(!d.getScoreboardTags().contains("kg_elka_v77_"+group))continue;
            count++;float[] before=original.get(d.getUniqueId());if(before==null)throw new AssertionError("unexpected display");
            org.bukkit.util.Transformation t=d.getTransformation();
            float[] after={t.getTranslation().x(),t.getTranslation().y(),t.getTranslation().z(),t.getScale().x(),t.getScale().y(),t.getScale().z()};
            for(int i=0;i<6;i++)if(Math.abs(after[i]-before[i]*factor)>.006f)throw new AssertionError(group+" was not uniformly resized at component "+i);
        }
        ok(count>0,group+" has "+count+" parts, all translated and scaled at "+factor);
    }
    private void allGroupsAt(Map<UUID,float[]> original,float factor){
        for(String group:new String[]{"tree","gift_west","gift_north","gift_south","figure_base","person_1","person_2","person_3","hand_star"})groupAt(original,group,factor);
        starTouchesHand(factor);
    }
    private void starTouchesHand(float factor){
        BlockDisplay star=part("tree",Material.GOLD_BLOCK,-1.35f,44.65f,factor);
        BlockDisplay hand=part("hand_star",Material.PINK_TERRACOTTA,1.35f,44.65f,factor);
        org.bukkit.util.Transformation a=star.getTransformation(),b=hand.getTransformation();
        near(a.getTranslation().x()+a.getScale().x(),b.getTranslation().x(),"hand meets star at factor "+factor);
        ok(a.getTranslation().y()<b.getTranslation().y()+b.getScale().y()&&b.getTranslation().y()<a.getTranslation().y()+a.getScale().y()
                &&a.getTranslation().z()<b.getTranslation().z()+b.getScale().z()&&b.getTranslation().z()<a.getTranslation().z()+a.getScale().z(),"star and glove overlap in height and depth");
    }
    private void cleanup(World w){
        BlockDisplay legacy=w.spawn(new Location(w,.5,100,.5),BlockDisplay.class,d->d.addScoreboardTag("kg_spawn_tree_v76"));
        BlockDisplay duplicate=w.spawn(new Location(w,.5,100,.5),BlockDisplay.class,d->d.addScoreboardTag("kg_spawn_tree_v77"));
        BlockDisplay foreign=w.spawn(new Location(w,.5,100,.5),BlockDisplay.class,d->d.addScoreboardTag("foreign"));
        BlockDisplay distant=w.spawn(new Location(w,48.5,100,48.5),BlockDisplay.class,d->d.addScoreboardTag("kg_spawn_tree_v76"));
        displays().get(0).remove();
        try{java.lang.reflect.Method method=model().getClass().getDeclaredMethod("ensureModel");method.setAccessible(true);method.invoke(model());}
        catch(Exception error){throw new RuntimeException(error);}
        ok(!legacy.isValid()&&!duplicate.isValid()&&foreign.isValid()&&distant.isValid(),"scoped cleanup removes old/duplicate model but preserves foreign and distant displays");
        ok(displays().size()==232&&w.getChunkAt(0,0).getEntities().length>0
                &&java.util.Arrays.stream(w.getChunkAt(0,0).getEntities()).filter(e->e instanceof BlockDisplay&&e.getScoreboardTags().contains("kg_spawn_tree_v77")).count()==232,
                "invalid display is rebuilt once without orphaned duplicates");
        foreign.remove();distant.remove();
    }
    private void blocked(Runnable task){
        Path file=core.getDataFolder().toPath().resolve("data.yml"),backup=file.resolveSibling("elka-save-test.yml");
        try{Files.move(file,backup);Files.createSymbolicLink(file,backup.getFileName());task.run();}
        catch(Exception e){throw new RuntimeException(e);}
        finally{try{if(Files.isSymbolicLink(file))Files.delete(file);if(Files.exists(backup))Files.move(backup,file);}catch(Exception e){throw new RuntimeException(e);}}
    }
    private void run(){
        ok(core.getDescription().getVersion().equals("7.14.0"),"new Core version loaded");
        World w=core.getSpawnEngine().getPreparedSpawnArrival().getWorld();
        ok(displays().size()==232&&displays().stream().allMatch(e->e.isValid()&&!e.isPersistent()),"all 232 nonpersistent display parts are loaded");
        ok(w.getBlockAt(0,102,0).getType()==Material.SPRUCE_WOOD&&w.getBlockAt(0,103,0).getType().isAir()
                &&w.getBlockAt(16,101,1).getType().isAir()&&w.getBlockAt(14,115,1).getType().isAir()
                &&w.getBlockAt(12,129,1).getType().isAir()&&w.getBlockAt(3,146,0).getType().isAir(),"short collision stub and no rigid legacy statues");
        rank(owner,"owner");rank(admin,"admin");rank(ordinary,"default");owner.setOp(false);admin.setOp(false);ordinary.setOp(true);
        if(getConfig().getInt("phase")==2){
            ok(level()==2,"saved level survives an abrupt process kill");
            BlockDisplay gift=part("gift_west",Material.RED_CONCRETE,-13.65f,0,1.2f);
            near(gift.getTransformation().getTranslation().x(),-13.65f*1.2f,"gift reloads at the persisted factor");
            for(int i=1;i<=3;i++){
                float x=16-2*i,y=1+14*(i-1);
                BlockDisplay shoe=part("person_"+i,Material.DARK_OAK_PLANKS,x,y,1.2f);
                near(shoe.getTransformation().getScale().x(),1.85f*1.2f,"person "+i+" reloads at persisted size");
            }
            starTouchesHand(1.2f);
            command(owner,"-2");ok(level()==0,"resizing after restart works");
            later(40,this::finish);return;
        }
        ok(level()==0,"fresh default level is zero");
        cleanup(w);
        Map<UUID,float[]> original=snapshot();
        allGroupsAt(original,1.0f);
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
        float before=part("gift_west",Material.RED_CONCRETE,-13.65f,0,1f).getTransformation().getTranslation().x();
        command(owner,"3");ok(level()==3,"+3 is relative to level zero");
        near(part("gift_west",Material.RED_CONCRETE,-13.65f,0,1.3f).getTransformation().getTranslation().x(),before*1.3f,"gift moves and scales with the tree");
        allGroupsAt(original,1.3f);
        owner.performCommand("elka -1");ok(level()==3,"one-second rate limit prevents spam");
        command(owner,"1");ok(level()==3,"upper bound rejects overflow");
        command(admin,"-3");ok(level()==0,"administrator can decrease three steps");
        command(admin,"-3");ok(level()==-3,"lower level is reachable");
        allGroupsAt(original,.7f);
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
        ok(w.getBlockAt(0,102,0).getType()==Material.SPRUCE_WOOD&&w.getBlockAt(0,103,0).getType().isAir()&&w.getBlockAt(16,101,1).getType().isAir(),"all sizes leave physical blocks unchanged");
        command(owner,"1");ok(level()==2,"final +2 level durably stored before crash");
        allGroupsAt(original,1.2f);
        later(60,this::finish);
    }
}
