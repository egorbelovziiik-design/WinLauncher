package net.kinggrief.probe;

import net.kinggrief.core.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** The first server stops mid-animation; the second verifies the key and clean display state. */
public final class CaseRestartProbe extends JavaPlugin implements Listener {
    private boolean ran;
    @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);}
    @EventHandler public void joined(PlayerJoinEvent e){
        if(ran)return;ran=true;Player p=e.getPlayer();Bukkit.getScheduler().runTaskLater(this,()->{
            try{
                KingGriefCore core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");
                DataStore store=core.getStore();String mode=getConfig().getString("mode","");
                if(mode.equals("prepare")){
                    store.addCaseKeys(p.getName(),"money",1-store.getCaseKeys(p.getName(),"money"));store.setCoins(p.getName(),4200);store.save();
                    SpawnEngine.CasePedestal money=null;for(SpawnEngine.CasePedestal cp:core.getSpawnEngine().getCasePedestals())if(cp.caseId.equals("money"))money=cp;
                    if(money==null)throw new AssertionError("money case missing");
                    core.getSpawnEngine().openShulkerCaseAnimated(p,money);
                    Bukkit.getScheduler().runTaskLater(this,()->{getLogger().info("KG-CASE-RESTART PASS prepare");Bukkit.shutdown();},10L);
                    return;
                }
                if(!mode.equals("verify")||store.getCaseKeys(p.getName(),"money")!=1||store.getCoins(p.getName())!=4200)
                    throw new AssertionError("key or balance changed across interrupted animation");
                for(Entity ent:p.getWorld().getEntities())if(ent.getScoreboardTags().contains("kg_case_float"))throw new AssertionError("orphan case display after restart");
                getLogger().info("KG-CASE-RESTART PASS verify");Bukkit.shutdown();
            }catch(Throwable t){getLogger().severe("KG-CASE-RESTART FAIL: "+t);t.printStackTrace();Bukkit.shutdown();}
        },40L);
    }
}
