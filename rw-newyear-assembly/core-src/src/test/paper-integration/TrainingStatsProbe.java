package net.kinggrief.probe;

import net.kinggrief.core.KingGriefCore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Real network hits on the spawn's existing training dummies, plus bounded edge cases. */
public final class TrainingStatsProbe extends JavaPlugin implements Listener {
    private KingGriefCore core;private Player a,b;private World spawn;private ArmorStand first,second;
    private boolean running,done,cancelHits;private int checks,seenA,seenB;private double rawA,rawB;
    @Override public void onEnable(){saveDefaultConfig();core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");Bukkit.getPluginManager().registerEvents(this,this);}
    @EventHandler public void join(PlayerJoinEvent event){
        if("TrainTester".equals(event.getPlayer().getName()))a=event.getPlayer();if("TrainOther".equals(event.getPlayer().getName()))b=event.getPlayer();
        if(a!=null&&b!=null&&!running){running=true;later(30,this::begin);}
    }
    private void later(long ticks,Runnable runnable){Bukkit.getScheduler().runTaskLater(this,()->{try{runnable.run();}catch(Throwable error){fail(error);}},ticks);}
    private void ok(boolean value,String text){if(!value)throw new AssertionError(text);checks++;getLogger().info("KG-TRAIN PASS "+text);}
    private void fail(Throwable error){if(done)return;done=true;getLogger().severe("KG-TRAIN RESULT FAIL "+error);error.printStackTrace();}
    private void finish(){done=true;getLogger().info("KG-TRAIN REQUEST REPORT");getLogger().info("KG-TRAIN RESULT PASS "+checks+" checks");}
    private void request(String command){getLogger().info("KG-TRAIN REQUEST "+command);}
    private Object field(Object object,String name){try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(Exception error){throw new RuntimeException(error);}}
    private Object manager(){return field(core,"trainingStats");}
    @SuppressWarnings("unchecked") private Map<UUID,Object> sessions(){return (Map<UUID,Object>)field(manager(),"sessions");}
    private Object session(Player p){return sessions().get(p.getUniqueId());}
    private double number(Object obj,String name){return ((Number)field(obj,name)).doubleValue();}
    private int integer(Object obj,String name){return ((Number)field(obj,name)).intValue();}
    private double dps(Object session){try{Method m=manager().getClass().getDeclaredMethod("dps",session.getClass(),long.class);m.setAccessible(true);return ((Number)m.invoke(null,session,System.nanoTime())).doubleValue();}catch(Exception e){throw new RuntimeException(e);}}
    private void begin(){
        spawn=core.getSpawnEngine().getSpawnArrival().getWorld();ok(core.getSpawnEngine().isSpawnActive(),"prepared spawn is active");
        a.teleport(new Location(spawn,64.7,100,-5,-90,0));b.teleport(new Location(spawn,64.7,100,-3,-90,0));
        a.setGameMode(GameMode.SURVIVAL);b.setGameMode(GameMode.SURVIVAL);a.setAllowFlight(true);a.setFlying(true);b.setAllowFlight(true);b.setFlying(true);
        a.getInventory().clear();b.getInventory().clear();a.getInventory().setHeldItemSlot(0);b.getInventory().setHeldItemSlot(0);
        a.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));b.getInventory().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
        later(75,()->{
            for(Entity entity:spawn.getEntities())if(entity instanceof ArmorStand&&entity.getScoreboardTags().contains("kg_dps_dummy")){
                if(Math.abs(entity.getLocation().getZ()+5)<0.5)first=(ArmorStand)entity;
                if(Math.abs(entity.getLocation().getZ()+3)<0.5)second=(ArmorStand)entity;
            }
            ok(first!=null&&second!=null,"existing first and second training dummies are loaded without replacing spawn");
            if(getConfig().getInt("phase",1)==1)hitOne();else restart();
        });
    }
    @EventHandler(priority=EventPriority.LOWEST)
    public void protect(EntityDamageByEntityEvent event){if(cancelHits&&event.getEntity().getScoreboardTags().contains("kg_dps_dummy"))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR)
    public void observe(EntityDamageByEntityEvent event){
        if(!event.getEntity().getScoreboardTags().contains("kg_dps_dummy"))return;
        if(event.getDamager().equals(a)){seenA++;rawA=event.getDamage();}
        if(event.getDamager().equals(b)){seenB++;rawB=event.getDamage();}
    }
    private void attack(String player,ArmorStand target,int count,Runnable next){request("ATTACK "+player+" "+target.getEntityId()+" "+count);later(7,next);}
    private void hitOne(){
        double balance=core.getStore().getCoins(a.getName());int before=seenA;
        attack("A",first,1,()->{
            Object s=session(a);ok(seenA>before&&s!=null&&integer(s,"hits")==1&&rawA>0,"real network sword hit produces one personal training sample");
            ok(Math.abs(number(s,"lastRaw")-rawA)<1e-9,"reported raw damage is the server event damage, not a second critical multiplier");
            double armor=Math.min(20.0,Math.max(4.0,20.0-rawA/5.0));double estimated=rawA*(1-armor/25.0)*0.36;
            ok(Math.abs(number(s,"lastSimulated")-estimated)<1e-9&&Math.abs(dps(s)-rawA/5.0)<1e-9,"netherite model and five-second raw DPS use the documented formula");
            String expected="-"+String.format(Locale.ROOT,"%.1f",estimated/2.0)+" ❤";boolean indicator=false;
            for(Entity entity:spawn.getNearbyEntities(first.getLocation(),3,3,3)){
                if(entity.getScoreboardTags().contains("kg_holo")&&entity.getCustomName()!=null
                        &&org.bukkit.ChatColor.stripColor(entity.getCustomName()).equals(expected))indicator=true;
            }
            ok(estimated<0.5||indicator,"damage indicator converts health points to hearts");
            ok(first.isValid()&&!first.isDead()&&core.getStore().getCoins(a.getName())==balance,"training leaves the dummy alive and pays no money");
            int old=seenB;attack("B",second,1,()->{
                ok(seenB>old&&session(b)!=null&&integer(session(b),"hits")==1&&integer(session(a),"hits")==1,"second network player has independent measurements");
                rateLimit();
            });
        });
    }
    private void rateLimit(){
        int old=seenA,previous=integer(session(a),"hits");attack("A",first,5,()->{
            int delta=integer(session(a),"hits")-previous;
            ok(seenA>old&&delta==1,"rapid network hit burst records at most one sample per 100ms");
            cancelled();
        });
    }
    private void cancelled(){
        int old=seenA,previous=integer(session(a),"hits");cancelHits=true;
        attack("A",first,1,()->{
            cancelHits=false;ok(seenA>old&&integer(session(a),"hits")==previous,"earlier protection cancellation records no hit");
            core.getConfig().set("enable-builtin-auth",true);core.getStore().setAuthenticated(a,false);oldAuthHit(previous);
        });
    }
    private void oldAuthHit(int previous){
        int old=seenA;attack("A",first,1,()->{
            ok(seenA>old&&integer(session(a),"hits")==previous,"unauthenticated attack records no training statistics");
            core.getConfig().set("enable-builtin-auth",false);syntheticCrit();
        });
    }
    private void syntheticCrit(){
        a.teleport(first.getLocation().clone().add(-1.8,2.0,0));a.setAllowFlight(true);a.setFlying(true);a.setFallDistance(1.0f);
        EntityDamageByEntityEvent event=new EntityDamageByEntityEvent(a,first,EntityDamageEvent.DamageCause.ENTITY_ATTACK,10.0);
        Bukkit.getPluginManager().callEvent(event);Object s=session(a);
        ok(event.isCancelled()&&Math.abs(number(s,"lastRaw")-10.0)<1e-9,"synthetic ten-HP attack is not multiplied a second time");
        ok(((Deque<?>)field(s,"recent")).size()<=51,"rolling sample queue is bounded");
        later(110,()->{
            ok(dps(s)==0.0&&integer(s,"hits")>=2,"five-second DPS expires while the session totals remain");
            a.performCommand("trainstats");a.performCommand("trainstats reset");
            ok(session(a)==null&&session(b)!=null,"reset clears only the requesting player's temporary session");
            finish();
        });
    }
    private void restart(){
        ok(session(a)==null&&session(b)==null,"training sessions do not survive a server restart");
        a.performCommand("trainstats");ok(first.isValid()&&second.isValid(),"stats command does not change spawn dummies");finish();
    }
}
