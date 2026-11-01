package net.kinggrief.probe;

import net.kinggrief.core.DataStore;
import net.kinggrief.core.KingGriefCore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.ArrayList;
import java.util.List;

/** Second boot of the real clean-run test: verify disk replay and viewer isolation. */
public final class ParkourRestartProbe extends JavaPlugin implements Listener {
    private KingGriefCore core; private Player owner,observer; private World world;private BlockDisplay marker;
    private boolean running,done;private int checks;
    @Override public void onEnable(){core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");Bukkit.getPluginManager().registerEvents(this,this);}
    @EventHandler public void join(PlayerJoinEvent e){
        if("ParkourRunner".equals(e.getPlayer().getName()))owner=e.getPlayer();
        if("GhostObserver".equals(e.getPlayer().getName()))observer=e.getPlayer();
        if(owner!=null&&observer!=null&&!running){running=true;later(40,()->begin());}
    }
    private void later(long ticks,Runnable task){Bukkit.getScheduler().runTaskLater(this,()->{try{task.run();}catch(Throwable error){fail(error);}},ticks);}
    private void ok(boolean value,String message){if(!value)throw new AssertionError(message);checks++;getLogger().info("KG-GHOST PASS "+message);}
    private void fail(Throwable error){if(done)return;done=true;getLogger().severe("KG-GHOST RESULT FAIL "+error);error.printStackTrace();}
    private void begin(){
        world=core.getSpawnEngine().getPreparedSpawnArrival().getWorld();
        ok(recordedTime()>12000L,"ranked result survives a full server restart");
        ok(owner.performCommand("parkour ghost me"),"personal replay selected after restart");
        observer.teleport(new Location(world,38.5,103,-26.5));observer.setGameMode(GameMode.SPECTATOR);
        owner.teleport(new Location(world,39.5,103,-28.5));owner.setGameMode(GameMode.SURVIVAL);
        marker=world.spawn(new Location(world,40.5,103,-29.5),BlockDisplay.class,display->{
            display.setBlock(org.bukkit.Material.GLASS.createBlockData());display.setPersistent(false);
        });
        later(100,()->view());
    }
    private long recordedTime(){try{java.lang.reflect.Field f=DataStore.class.getDeclaredField("yml");f.setAccessible(true);
        return ((org.bukkit.configuration.file.YamlConfiguration)f.get(core.getStore())).getLong("parkour.records."+owner.getUniqueId()+".all",0L);
    }catch(Exception error){throw new RuntimeException(error);}}
    private List<Entity> ghosts(){List<Entity> list=new ArrayList<Entity>();for(Entity entity:world.getEntities())if(entity.getScoreboardTags().contains("kg_parkour_ghost"))list.add(entity);return list;}
    private void view(){
        List<Entity> list=ghosts();ok(list.size()==6,"saved compressed recording replays after restart");
        for(Entity e:list){ok(e instanceof BlockDisplay&&!e.isPersistent()&&!e.isVisibleByDefault()&&owner.canSee(e),"owner can see private non-persistent ghost part");
            ok(!observer.canSee(e),"second real client cannot see ghost part");}
        StringBuilder ids=new StringBuilder();for(Entity e:list){if(ids.length()>0)ids.append(',');ids.append(e.getEntityId());}
        String packetSignal="KG_GHOST_IDS="+ids+" MARKER="+marker.getEntityId();
        owner.sendMessage(packetSignal);observer.sendMessage(packetSignal);
        marker.remove();owner.performCommand("parkour ghost off");ok(ghosts().isEmpty(),"disabling replay cleans every model part");
        done=true;getLogger().info("KG-GHOST RESULT PASS "+checks+" checks");
    }
}
