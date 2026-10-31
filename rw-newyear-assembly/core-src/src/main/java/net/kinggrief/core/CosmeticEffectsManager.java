package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Bounded cosmetic particles only: no explosions, entities, items or block changes. */
final class CosmeticEffectsManager implements Listener {
    private final KingGriefCore plugin;
    private final Map<UUID,String> trails=new HashMap<UUID,String>();
    private final Map<UUID,String> logins=new HashMap<UUID,String>();
    private final Map<UUID,Location> previous=new HashMap<UUID,Location>();
    private final Set<UUID> played=new HashSet<UUID>();
    private BukkitRunnable ticker;
    private long nextShow;
    CosmeticEffectsManager(KingGriefCore plugin){this.plugin=plugin;}
    void start(){
        for(Player player:Bukkit.getOnlinePlayers())load(player);
        ticker=new BukkitRunnable(){@Override public void run(){updateTrails();}};ticker.runTaskTimer(plugin,20L,10L);
    }
    void shutdown(){if(ticker!=null)ticker.cancel();trails.clear();logins.clear();previous.clear();played.clear();}
    @EventHandler public void join(PlayerJoinEvent event){
        Player player=event.getPlayer();load(player);
        Bukkit.getScheduler().runTaskLater(plugin,()->{if(player.isOnline())onAuthenticated(player);},30L);
    }
    @EventHandler public void quit(PlayerQuitEvent event){UUID id=event.getPlayer().getUniqueId();trails.remove(id);logins.remove(id);previous.remove(id);played.remove(id);}
    private void load(Player player){
        try{String[] selected=plugin.getStore().inspect(data->new String[]{
                data.getString("cosmetics."+player.getUniqueId()+".trail","off"),
                data.getString("cosmetics."+player.getUniqueId()+".login","off")});
            trails.put(player.getUniqueId(),valid(selected[0])?selected[0]:"off");
            logins.put(player.getUniqueId(),valid(selected[1])?selected[1]:"off");
        }catch(Exception error){trails.put(player.getUniqueId(),"off");logins.put(player.getUniqueId(),"off");}
    }
    void command(Player player,String[] args){
        if(args.length!=2||!("trail".equalsIgnoreCase(args[0])||"login".equalsIgnoreCase(args[0]))){
            player.sendMessage(ColorUtil.c("&e/cosmetic <trail|login> <snow|hearts|stars|off>"));return;
        }
        String kind=args[0].toLowerCase(Locale.ROOT), effect=args[1].toLowerCase(Locale.ROOT);
        if(!valid(effect)){player.sendMessage(ColorUtil.c("&eЭффекты: snow, hearts, stars, off"));return;}
        try{
            plugin.getStore().transact(data->{data.set("cosmetics."+player.getUniqueId()+"."+kind,effect);return null;});
            if("trail".equals(kind))trails.put(player.getUniqueId(),effect);else logins.put(player.getUniqueId(),effect);
            player.sendMessage(ColorUtil.c("&#80D8FFКосметика "+kind+": &f"+effect+"&7. На бой и предметы не влияет."));
        }catch(Exception error){player.sendMessage(ColorUtil.c("&cНе удалось сохранить косметику."));}
    }
    private static boolean valid(String value){return "off".equals(value)||"snow".equals(value)||"hearts".equals(value)||"stars".equals(value);}
    private static Particle particle(String effect){return "snow".equals(effect)?Particle.SNOWFLAKE:"hearts".equals(effect)?Particle.HEART:Particle.END_ROD;}
    private boolean hidden(Player player){
        PotionEffectType invis=ColorUtil.potType("invisibility","INVISIBILITY");
        return plugin.getStore().isVanished(player)||(invis!=null&&player.hasPotionEffect(invis))
                ||!plugin.getStore().isAuthenticated(player)||player.isDead()||player.getGameMode()==GameMode.SPECTATOR;
    }
    private void updateTrails(){
        int max=Math.max(0,Math.min(64,plugin.getConfig().getInt("cosmetics.max-active",32))),count=0;
        for(Player player:Bukkit.getOnlinePlayers()){
            UUID id=player.getUniqueId();String trail=trails.get(id);
            if(trail==null){load(player);trail=trails.get(id);}
            Location now=player.getLocation(),before=previous.put(id,now.clone());
            if("off".equals(trail)||hidden(player)||count>=max||before==null||before.getWorld()!=now.getWorld()
                    ||before.distanceSquared(now)<.04||plugin.getStore().getParkourCp(player)!=null)continue;
            count++;
            now.getWorld().spawnParticle(particle(trail),now.add(0,.15,0),4,.17,.08,.17,0);
        }
        for(UUID id:new ArrayList<UUID>(previous.keySet()))if(Bukkit.getPlayer(id)==null)previous.remove(id);
    }
    void onAuthenticated(Player player){
        if(!player.isOnline()||hidden(player)||played.contains(player.getUniqueId()))return;
        played.add(player.getUniqueId());
        String effect=logins.get(player.getUniqueId());if(effect==null){load(player);effect=logins.get(player.getUniqueId());}
        if("off".equals(effect))return;
        Location loc=player.getLocation().add(0,1,0);
        loc.getWorld().spawnParticle(particle(effect),loc,18,.45,.7,.45,0);
        ColorUtil.sound(player,"block.amethyst_block.chime",.8f,1.1f);
    }
    void winterShow(Player player){
        if(hidden(player)||!plugin.getSpawnEngine().isInSpawnWorld(player.getLocation())){
            player.sendMessage(ColorUtil.c("&eПраздничное шоу доступно видимым игрокам на готовом спавне."));return;
        }
        long now=System.currentTimeMillis();
        if(now<nextShow){player.sendMessage(ColorUtil.c("&eСледующее шоу примерно через "+((nextShow-now+999)/1000)+" сек."));return;}
        nextShow=now+60_000L;
        Location origin=player.getLocation().clone();
        player.sendMessage(ColorUtil.c("&#FFD740✦ Зимнее световое шоу! Только частицы: без взрывов и урона."));
        new BukkitRunnable(){int step;
            @Override public void run(){
                if(!player.isOnline()||hidden(player)||!plugin.getSpawnEngine().isInSpawnWorld(player.getLocation())||step>=8){cancel();return;}
                double angle=step*Math.PI/4.0;
                Location burst=origin.clone().add(Math.cos(angle)*3,3.5+(step%3),Math.sin(angle)*3);
                burst.getWorld().spawnParticle(Particle.FIREWORK,burst,14,.35,.35,.35,.05);
                if(step%2==0)ColorUtil.sound(player,"entity.firework_rocket.blast",.4f,1.1f);
                step++;
            }
        }.runTaskTimer(plugin,0L,8L);
    }
}
