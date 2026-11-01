package net.kinggrief.probe;

import net.kinggrief.core.KingGriefCore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
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
    private Entity ordinaryTarget;private boolean lateCancel,lateModify;private double ordinaryFinal;private boolean ordinaryCancelled;
    @Override public void onEnable(){saveDefaultConfig();core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");Bukkit.getPluginManager().registerEvents(this,this);}
    @EventHandler public void join(PlayerJoinEvent event){
        if("TrainTester".equals(event.getPlayer().getName()))a=event.getPlayer();if("TrainOther".equals(event.getPlayer().getName()))b=event.getPlayer();
        if(a!=null&&b!=null&&!running){running=true;later(30,this::begin);}
    }
    private void later(long ticks,Runnable runnable){Bukkit.getScheduler().runTaskLater(this,()->{try{runnable.run();}catch(Throwable error){fail(error);}},ticks);}
    private void ok(boolean value,String text){if(!value)throw new AssertionError(text);checks++;getLogger().info("KG-TRAIN PASS "+text);}
    private void clearCombat(){if(core!=null){if(a!=null)core.getHud().clearCombat(a.getUniqueId());if(b!=null)core.getHud().clearCombat(b.getUniqueId());}}
    private void fail(Throwable error){if(done)return;done=true;clearCombat();getLogger().severe("KG-TRAIN RESULT FAIL "+error);error.printStackTrace();}
    private void finish(){done=true;clearCombat();getLogger().info("KG-TRAIN REQUEST REPORT");getLogger().info("KG-TRAIN RESULT PASS "+checks+" checks");}
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
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void laterProtection(EntityDamageByEntityEvent event){
        if(event.getEntity().equals(ordinaryTarget)){if(lateCancel)event.setCancelled(true);else if(lateModify)event.setDamage(8.0);}
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void observe(EntityDamageByEntityEvent event){
        if(event.getEntity().equals(ordinaryTarget)){ordinaryFinal=event.getFinalDamage();ordinaryCancelled=event.isCancelled();}
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
                ok(seenB>old&&session(b)!=null&&integer(session(b),"hits")==1&&integer(session(a),"hits")==1
                        &&Math.abs(number(session(b),"lastRaw")-rawB)<1e-9,"second network player has independent measurements");
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
        a.teleport(first.getLocation().clone().add(-1.8,2.0,0));a.setAllowFlight(true);a.setFlying(true);
        // Teleport retains the previous on-ground state. The real client clears it,
        // then the fixture supplies fall distance before firing the synthetic event.
        later(8,()->{request("AIR");later(5,()->{
            a.setFallDistance(1.0f);
            boolean possible=a.getFallDistance()>0.0f&&!a.isOnGround()&&!a.isSwimming()&&!a.isSprinting()&&!a.isInsideVehicle();
            ok(possible,"synthetic fixture meets the possible-critical heuristic (onGround="+a.isOnGround()+")");
            int previousCrits=integer(session(a),"possibleCrits");
            EntityDamageByEntityEvent event=new EntityDamageByEntityEvent(a,first,EntityDamageEvent.DamageCause.ENTITY_ATTACK,10.0);
            Bukkit.getPluginManager().callEvent(event);Object s=session(a);
            ok(event.isCancelled()&&integer(s,"possibleCrits")==previousCrits+1&&Math.abs(number(s,"lastRaw")-10.0)<1e-9,
                    "possible-critical ten-HP event is not multiplied a second time");
            ok(((Deque<?>)field(s,"recent")).size()<=51,"rolling sample queue is bounded");
            later(110,()->{
                ok(dps(s)==0.0&&integer(s,"hits")>=2,"five-second DPS expires while the session totals remain");
                a.performCommand("trainstats");a.performCommand("trainstats reset");
                ok(session(a)==null&&session(b)!=null,"reset clears only the requesting player's temporary session");
                damageIndicators();
            });
        });});
    }
    private boolean indicatorAt(Location location,double damage){
        String expected="-"+String.format(Locale.ROOT,"%.1f",damage/2.0)+" ❤";
        for(Entity entity:location.getWorld().getNearbyEntities(location,3,3,3))
            if(entity.getScoreboardTags().contains("kg_holo")&&entity.getCustomName()!=null
                    &&org.bukkit.ChatColor.stripColor(entity.getCustomName()).equals(expected))return true;
        return false;
    }
    private int clearIndicators(Location location){
        int count=0;for(Entity entity:location.getWorld().getNearbyEntities(location,3,3,3))
            if(entity.getScoreboardTags().contains("kg_holo")){entity.remove();count++;}return count;
    }
    private void damageIndicators(){
        a.teleport(new Location(spawn,75.5,94,10.5));b.teleport(new Location(spawn,77.5,94,10.5));
        a.setAllowFlight(true);a.setFlying(true);b.setAllowFlight(true);b.setFlying(true);
        b.getInventory().clear();b.setHealth(20);spawn.setPVP(true);
        later(40,()->{
            ordinaryTarget=b;lateModify=true;ordinaryFinal=-1;ordinaryCancelled=true;b.setNoDamageTicks(0);b.damage(6.0,a);lateModify=false;
            ok(!ordinaryCancelled&&Math.abs(ordinaryFinal-8.0)<1e-9&&b.getHealth()<20&&indicatorAt(b.getLocation(),ordinaryFinal),
                    "PvP indicator uses final accepted damage in hearts after a later modifier");
            clearIndicators(b.getLocation());b.setHealth(20);b.setNoDamageTicks(0);lateCancel=true;ordinaryFinal=-1;ordinaryCancelled=false;b.damage(6.0,a);lateCancel=false;
            ok(ordinaryCancelled&&b.getHealth()==20&&clearIndicators(b.getLocation())==0,"late-cancelled PvP damage has no floating indicator");
            Location pigLocation=new Location(spawn,82.5,94,10.5);Pig pig=spawn.spawn(pigLocation,Pig.class,p->{p.setAI(false);p.setGravity(false);});
            Arrow arrow=spawn.spawn(pigLocation.clone().add(0,1,0),Arrow.class);arrow.setShooter(a);ordinaryTarget=pig;ordinaryFinal=-1;ordinaryCancelled=true;
            // LivingEntity.damage(amount, arrow) is generic on Paper; an explicit
            // source is needed to exercise the actual projectile event pipeline.
            org.bukkit.damage.DamageSource source=org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.ARROW)
                    .withDirectEntity(arrow).withCausingEntity(a).build();
            pig.damage(6.0,source);
            boolean pigIndicator=indicatorAt(pig.getLocation(),ordinaryFinal);
            ok(!ordinaryCancelled&&ordinaryFinal>0&&pig.getHealth()<10&&pigIndicator,
                    "projectile indicator converts accepted mob damage to hearts");
            clearIndicators(pig.getLocation());pig.remove();arrow.remove();
            Location bossLocation=new Location(spawn,88.5,94,10.5);Zombie boss=spawn.spawn(bossLocation,Zombie.class,z->{z.setAI(false);z.setGravity(false);});
            boss.addScoreboardTag("kg_event_boss");ordinaryTarget=boss;ordinaryFinal=-1;ordinaryCancelled=true;boss.damage(6.0,a);
            ok(!ordinaryCancelled&&ordinaryFinal>0&&boss.getHealth()<20&&indicatorAt(boss.getLocation(),ordinaryFinal),
                    "boss indicator converts accepted damage to hearts");
            clearIndicators(boss.getLocation());boss.remove();ordinaryTarget=null;later(8,this::finish);
        });
    }
    private void restart(){
        ok(session(a)==null&&session(b)==null,"training sessions do not survive a server restart");
        a.performCommand("trainstats");ok(first.isValid()&&second.isValid(),"stats command does not change spawn dummies");finish();
    }
}
