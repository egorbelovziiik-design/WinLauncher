package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Temporary, bounded training measurements; no rewards or world changes. */
final class TrainingStatsManager implements Listener {
    private static final long WINDOW=5_000_000_000L, MIN_INTERVAL=100_000_000L, EXPIRE=300_000_000_000L;
    private static final int MAX_PLAYERS=256, MAX_SAMPLES=51;
    private static final class Hit {
        final long at;final double damage;
        Hit(long at,double damage){this.at=at;this.damage=damage;}
    }
    private static final class Session {
        final Deque<Hit> recent=new ArrayDeque<Hit>();
        long last;int hits,possibleCrits;double total,simulatedTotal,lastRaw,lastSimulated;
    }
    static final class Snapshot {
        final double raw,simulated,dps;final int hits;
        Snapshot(double raw,double simulated,double dps,int hits){this.raw=raw;this.simulated=simulated;this.dps=dps;this.hits=hits;}
    }
    private final KingGriefCore plugin;private final Map<UUID,Session> sessions=new HashMap<UUID,Session>();
    private BukkitRunnable cleanup;
    TrainingStatsManager(KingGriefCore plugin){this.plugin=plugin;}
    void start(){cleanup=new BukkitRunnable(){@Override public void run(){prune(System.nanoTime());}};cleanup.runTaskTimer(plugin,600L,600L);}
    void shutdown(){if(cleanup!=null)cleanup.cancel();sessions.clear();}
    @EventHandler public void quit(PlayerQuitEvent event){sessions.remove(event.getPlayer().getUniqueId());}
    private void prune(long now){Iterator<Session> it=sessions.values().iterator();while(it.hasNext())if(now-it.next().last>EXPIRE)it.remove();}
    private static void trim(Session session,long now){while(!session.recent.isEmpty()&&now-session.recent.peekFirst().at>=WINDOW)session.recent.removeFirst();}
    private static double dps(Session session,long now){trim(session,now);double total=0;for(Hit hit:session.recent)total+=hit.damage;return total/5.0;}
    /** Vanilla armor/toughness formula for ordinary damage, then protection IV on four armor pieces. */
    static double netheriteEstimate(double damage){
        double armor=Math.min(20.0,Math.max(4.0,20.0-damage/5.0));
        return damage*(1.0-armor/25.0)*0.36;
    }
    Snapshot record(Player p,double raw,boolean possibleCrit){
        if(p==null||!p.isOnline()||!plugin.getStore().isAuthenticated(p)||plugin.getStore().isQuickFrozen(p)
                ||plugin.getModeration().isFrozenOrAscending(p)||!Double.isFinite(raw)||raw<0||raw>1_000_000)return null;
        long now=System.nanoTime();Session session=sessions.get(p.getUniqueId());
        if(session==null){prune(now);if(sessions.size()>=MAX_PLAYERS)return null;session=new Session();sessions.put(p.getUniqueId(),session);}
        if(session.last!=0&&now-session.last<MIN_INTERVAL)return null;
        session.last=now;session.lastRaw=raw;session.lastSimulated=netheriteEstimate(raw);
        if(session.hits<Integer.MAX_VALUE)session.hits++;
        if(possibleCrit&&session.possibleCrits<Integer.MAX_VALUE)session.possibleCrits++;
        session.total=Math.min(1_000_000_000_000.0,session.total+raw);
        session.simulatedTotal=Math.min(1_000_000_000_000.0,session.simulatedTotal+session.lastSimulated);
        trim(session,now);session.recent.addLast(new Hit(now,raw));
        while(session.recent.size()>MAX_SAMPLES)session.recent.removeFirst();
        return new Snapshot(raw,session.lastSimulated,dps(session,now),session.hits);
    }
    void command(Player p,String[] args){
        if(args.length==1&&"reset".equalsIgnoreCase(args[0])){sessions.remove(p.getUniqueId());p.sendMessage(ColorUtil.c("&7Статистика тренировки очищена."));return;}
        if(args.length>0){p.sendMessage(ColorUtil.c("&e/trainstats [reset]"));return;}
        long now=System.nanoTime();prune(now);Session s=sessions.get(p.getUniqueId());
        if(s==null){p.sendMessage(ColorUtil.c("&7Пока нет ударов. Манекены находятся на &f/warp train&7."));return;}
        p.sendMessage(ColorUtil.c("&#80D8FF✦ Полигон &8| &7ударов: &f"+s.hits+" &8| &7возможных критов: &f"+s.possibleCrits));
        p.sendMessage(ColorUtil.c("&7Последний удар: &f"+fmt(s.lastRaw)+" HP &8| &7модель незерита: &f"+fmt(s.lastSimulated)+" HP"));
        p.sendMessage(ColorUtil.c("&7Сумма сырых ударов: &f"+fmt(s.total)+" HP &8| &7модель: &f"+fmt(s.simulatedTotal)+" HP"));
        p.sendMessage(ColorUtil.c("&7DPS сырых ударов за 5 секунд: &f"+fmt(dps(s,now))+" HP/с"));
        p.sendMessage(ColorUtil.c("&8Оценка брони не учитывает эффекты, щиты, способности и сторонние плагины; 2 HP = 1 ❤. Данные временные."));
    }
    static String fmt(double value){return String.format(Locale.ROOT,"%.1f",Double.valueOf(value));}
}
