package net.kinggrief.probe;

import net.kinggrief.core.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Field;
import java.util.Map;

/** Server's only normal world is spawn_world: RTP must not create another world synchronously. */
public final class RtpNoWorldProbe extends JavaPlugin implements Listener {
    private boolean ran;
    @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);}
    @EventHandler public void join(PlayerJoinEvent e){
        if(ran)return;ran=true;Player p=e.getPlayer();Bukkit.getScheduler().runTaskLater(this,()->{
            try{
                KingGriefCore core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");
                if(Bukkit.getWorld("world")!=null)throw new AssertionError("unexpected pre-existing world");
                core.getSpawnEngine().performRtp(p,"close");
                Field f=KingGriefCore.class.getDeclaredField("rtpManager");f.setAccessible(true);Object manager=f.get(core);
                Field jobs=manager.getClass().getDeclaredField("jobs");jobs.setAccessible(true);
                if(Bukkit.getWorld("world")!=null || !((Map<?,?>)jobs.get(manager)).isEmpty())throw new AssertionError("RTP created a world or started a search");
                getLogger().info("KG-NOWORLD RESULT PASS missing survival world is reported without synchronous creation");
            }catch(Throwable t){getLogger().severe("KG-NOWORLD RESULT FAIL "+t);t.printStackTrace();}
        },20L);
    }
}
