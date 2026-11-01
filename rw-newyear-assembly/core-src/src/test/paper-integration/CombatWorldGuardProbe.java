package net.kinggrief.probe;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import net.kinggrief.core.ItemFactory;
import net.kinggrief.core.KingGriefCore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/** Real WorldGuard and two accepted/cancelled PvP targets in an ephemeral world. */
public final class CombatWorldGuardProbe extends JavaPlugin implements Listener {
    private KingGriefCore core; private Player a,b,c,observedTarget;
    private World world; private RegionManager manager; private ProtectedCuboidRegion protectedArea;
    private boolean started,finished,observedCancelled; private int checks;

    @Override public void onEnable(){
        core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");
        Bukkit.getPluginManager().registerEvents(this,this);
    }
    @EventHandler public void join(PlayerJoinEvent event){
        if("FightTester".equals(event.getPlayer().getName()))a=event.getPlayer();
        if("FightTarget".equals(event.getPlayer().getName()))b=event.getPlayer();
        if("FightOther".equals(event.getPlayer().getName()))c=event.getPlayer();
        if(a!=null&&b!=null&&c!=null&&!started){started=true;later(35,this::begin);}
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void observe(EntityDamageByEntityEvent event){if(event.getEntity().equals(observedTarget))observedCancelled=event.isCancelled();}
    private void later(long ticks,Runnable work){Bukkit.getScheduler().runTaskLater(this,()->{try{work.run();}catch(Throwable error){fail(error);}},ticks);}
    private void ok(boolean condition,String text){if(!condition)throw new AssertionError(text);checks++;getLogger().info("KG-WGPVP PASS "+text);}
    private void cleanup(){for(Player p:new Player[]{a,b,c})if(p!=null)core.getHud().clearCombat(p.getUniqueId());}
    private void fail(Throwable error){if(finished)return;finished=true;cleanup();getLogger().severe("KG-WGPVP RESULT FAIL "+error);error.printStackTrace();}
    private void clear(Player p){
        p.setFireTicks(0);p.setFreezeTicks(0);p.setVelocity(new Vector());p.removePotionEffect(PotionEffectType.SLOWNESS);
        p.setNoDamageTicks(0);p.setLastDamage(0);p.setHealth(20);
    }
    private void hit(Player target){target.setNoDamageTicks(0);target.setLastDamage(0);observedTarget=target;observedCancelled=true;target.damage(4,a);observedTarget=null;}
    private void begin(){
        world=core.getSpawnEngine().getSpawnArrival().getWorld();world.setPVP(true);
        for(Player p:new Player[]{a,b,c}){p.getInventory().clear();p.setGameMode(GameMode.SURVIVAL);}
        a.teleport(new Location(world,75.5,94,10.5));b.teleport(new Location(world,77.5,94,10.5));c.teleport(new Location(world,79.5,94,10.5));
        later(40,()->{
            manager=WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
            ok(manager!=null,"real WorldGuard region manager is available");
            protectedArea=new ProtectedCuboidRegion("kg_test_deny",BlockVector3.at(79,90,8),BlockVector3.at(81,99,13));
            protectedArea.setFlag(Flags.PVP,StateFlag.State.DENY);manager.addRegion(protectedArea);
            later(60,this::denyRegion);
        });
    }
    private void denyRegion(){
        clear(b);clear(c);a.getInventory().setItemInMainHand(ItemFactory.swordPhoenix());hit(b);
        ok(!observedCancelled&&b.getHealth()<20&&b.getFireTicks()>0&&c.getFireTicks()==0,
                "accepted primary Phoenix hit spares a WorldGuard pvp-deny bystander");
        clear(b);clear(c);a.getInventory().setItemInMainHand(ItemFactory.swordZima());hit(b);
        ok(!observedCancelled&&b.hasPotionEffect(PotionEffectType.SLOWNESS)&&!c.hasPotionEffect(PotionEffectType.SLOWNESS)
                &&c.getVelocity().lengthSquared()<0.001,"winter splash spares the protected bystander");
        clear(c);a.getInventory().setItemInMainHand(ItemFactory.swordFrost());hit(c);
        ok(observedCancelled&&c.getHealth()==20&&c.getFreezeTicks()==0&&!c.hasPotionEffect(PotionEffectType.SLOWNESS),
                "WorldGuard itself cancels a protected direct hit before frost effects");
        protectedArea.setFlag(Flags.PVP,StateFlag.State.ALLOW);
        later(60,this::allowRegion);
    }
    private void allowRegion(){
        clear(b);clear(c);a.getInventory().setItemInMainHand(ItemFactory.swordPhoenix());hit(b);
        ok(!observedCancelled&&c.getFireTicks()>0,"explicit WorldGuard pvp-allow permits splash again");
        manager.removeRegion("kg_test_deny");
        GlobalProtectedRegion global=new GlobalProtectedRegion("__global__");global.setFlag(Flags.PVP,StateFlag.State.DENY);manager.addRegion(global);
        ProtectedCuboidRegion allowed=new ProtectedCuboidRegion("kg_primary_allow",BlockVector3.at(74,90,8),BlockVector3.at(77,99,13));
        allowed.setPriority(10);allowed.setFlag(Flags.PVP,StateFlag.State.ALLOW);manager.addRegion(allowed);
        later(60,this::globalDeny);
    }
    private void globalDeny(){
        clear(b);clear(c);a.getInventory().setItemInMainHand(ItemFactory.swordPhoenix());hit(b);
        ok(!observedCancelled&&b.getHealth()<20&&c.getFireTicks()==0,"global WorldGuard pvp-deny protects bystander outside the allowed primary region");
        Bukkit.getPluginManager().disablePlugin(Bukkit.getPluginManager().getPlugin("WorldGuard"));
        clear(b);clear(c);hit(b);
        ok(!observedCancelled&&b.getHealth()<20&&c.getFireTicks()==0,"installed but disabled WorldGuard fails closed for secondary player effects");
        finished=true;cleanup();getLogger().info("KG-WGPVP RESULT PASS "+checks+" checks");
    }
}
