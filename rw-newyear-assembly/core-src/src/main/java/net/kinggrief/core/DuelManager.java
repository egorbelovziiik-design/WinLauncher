package net.kinggrief.core;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Invites are temporary; accepted stakes are durably escrowed together. */
final class DuelManager implements Listener, CommandExecutor, TabCompleter {
    private final KingGriefCore plugin;
    private final Map<UUID,Invite> invites=new HashMap<UUID,Invite>(); // target -> invite
    private final Map<UUID,Invite> preparing=new HashMap<UUID,Invite>();
    private final Map<String,Vote> cancelVotes=new HashMap<String,Vote>();
    private final Map<String,Settlement> pending=new LinkedHashMap<String,Settlement>();
    private boolean shuttingDown;
    private org.bukkit.scheduler.BukkitTask retryTask;
    private static final class Invite {
        final UUID from,to; final double bet; final long expires;
        Invite(Player from,Player to,double bet) { this.from=from.getUniqueId(); this.to=to.getUniqueId(); this.bet=bet; this.expires=System.currentTimeMillis()+60_000L; }
    }
    private static final class Vote {
        final UUID by; final long until;
        Vote(UUID by) { this.by=by; this.until=System.currentTimeMillis()+60_000L; }
    }
    private static final class Settlement {
        final DataStore.ActiveWagerDuel duel; final String winner,reason;
        Settlement(DataStore.ActiveWagerDuel duel,String winner,String reason) { this.duel=duel; this.winner=winner; this.reason=reason; }
    }
    DuelManager(KingGriefCore plugin) { this.plugin=plugin; }
    void start() {
        plugin.getStore().recoverWagerDuels();
        retryTask=Bukkit.getScheduler().runTaskTimer(plugin,()->{
            for(Settlement result:new ArrayList<Settlement>(pending.values())) attempt(result);
        },100L,100L);
    }
    void shutdown() {
        shuttingDown=true;
        if(retryTask!=null) retryTask.cancel();
        // Preserve a known result when possible; all other interrupted fights
        // are ties. Failed writes leave the escrow for startup recovery.
        for(Settlement result:new ArrayList<Settlement>(pending.values())) attempt(result);
        for(DataStore.ActiveWagerDuel duel:plugin.getStore().getActiveWagerDuels()) {
            if(!pending.containsKey(duel.id)) attempt(new Settlement(duel,null,"сервер остановлен"));
        }
        invites.clear(); preparing.clear(); cancelVotes.clear();
    }
    private boolean allowed(Player p) {
        return p!=null && p.isOnline() && !p.isDead() && p.getGameMode()==GameMode.SURVIVAL
                && plugin.getStore().isAuthenticated(p) && !plugin.getStore().isQuickFrozen(p)
                && !plugin.getModeration().isFrozenOrAscending(p);
    }
    private void prune() {
        long now=System.currentTimeMillis();
        invites.entrySet().removeIf(e->e.getValue().expires<now);
        cancelVotes.entrySet().removeIf(e->e.getValue().until<now);
    }
    private void message(Player p,String text) { if(p!=null && p.isOnline()) p.sendMessage(ColorUtil.c(text)); }
    private Invite outgoing(UUID id) { for(Invite i:invites.values()) if(i.from.equals(id)) return i; return null; }
    private Player online(String name) { return Bukkit.getPlayerExact(name); }
    private void sendInvite(Player from,Player target,double bet) {
        prune();
        if(!allowed(from) || !allowed(target)) { message(from,"&cОба игрока должны войти и находиться в выживании без заморозки."); return; }
        if(plugin.getStore().isWagerRecoveryBlocked()) { message(from,"&cДуэли остановлены: администрация проверяет журнал ставок."); return; }
        if(plugin.getStore().getActiveWagerDuel(from.getName())!=null || plugin.getStore().getActiveWagerDuel(target.getName())!=null
                || preparing.containsKey(from.getUniqueId()) || preparing.containsKey(target.getUniqueId())) {
            message(from,"&cОдин из игроков уже участвует в дуэли или готовится к ней."); return;
        }
        if(outgoing(from.getUniqueId())!=null || invites.containsKey(target.getUniqueId())) {
            message(from,"&eУ вас уже есть исходящий вызов или соперник рассматривает другой вызов. Используйте /duel cancel."); return;
        }
        if(!Double.isFinite(bet) || bet<0 || !Double.isFinite(bet*2) || (bet>0 && bet<100)) {
            message(from,"&cСтавка должна быть конечной и не меньше 100$ (0 — без ставки)."); return;
        }
        if(plugin.getStore().getCoins(from.getName())<bet) { message(from,"&cНедостаточно монет для ставки."); return; }
        Invite invite=new Invite(from,target,bet); invites.put(target.getUniqueId(),invite);
        message(from,"&aВызов отправлен &f"+target.getName()+"&a. Действует 60 секунд; /duel cancel отменяет вызов.");
        TextComponent text=new TextComponent(ColorUtil.c("&#FF1744⚔ &f"+from.getName()+" &7вызывает вас на дуэль"
                +(bet>0?" со ставкой &a"+ColorUtil.fmtMoney(bet)+"$":" без ставки")+" &7"));
        TextComponent button=new TextComponent(ColorUtil.c("&#00E676&l[ПРИНЯТЬ]"));
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/duel accept"));
        button.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,new ComponentBuilder(ColorUtil.c("&aПринять вызов на дуэль")).create()));
        text.addExtra(button); target.spigot().sendMessage(text);
        message(target,"&7Отказаться: &f/duel decline");
    }
    private void accept(Player target) {
        prune(); Invite invite=invites.remove(target.getUniqueId());
        if(invite==null) { message(target,"&eАктивного вызова нет."); return; }
        Player challenger=Bukkit.getPlayer(invite.from);
        if(!allowed(challenger) || !allowed(target) || plugin.getStore().getActiveWagerDuel(challenger==null?"":challenger.getName())!=null
                || plugin.getStore().getActiveWagerDuel(target.getName())!=null || preparing.containsKey(invite.from) || preparing.containsKey(invite.to)) {
            message(target,"&cИгрок недоступен, не вошёл в игру или уже участвует в дуэли."); return;
        }
        if(plugin.getHud().isInCombat(challenger) || plugin.getHud().isInCombat(target)) {
            message(target,"&cНельзя начинать дуэль во время действующего PvP-таймера."); return;
        }
        Location spawn=plugin.getSpawnEngine().getPreparedSpawnArrival(); World world=spawn==null?null:spawn.getWorld();
        if(world==null || plugin.getStore().isWagerRecoveryBlocked()) { message(target,"&cГотовый спавн или журнал дуэлей сейчас недоступен."); return; }
        preparing.put(invite.from,invite); preparing.put(invite.to,invite);
        message(target,"&7Подбираю свободную кабинку без синхронной генерации чанков…");
        // Only request already-generated arena chunks. This does not replace or
        // generate worlds, and the callback revalidates the entire invitation.
        try {
            List<CompletableFuture<?>> futures=new ArrayList<CompletableFuture<?>>();
            for(int z:new int[]{-3,-2,-1}) futures.add(world.getChunkAtAsync(4,z,false));
            CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0])).whenComplete((ignored,error)->{
                if(!plugin.isEnabled() || shuttingDown) return;
                try { Bukkit.getScheduler().runTask(plugin,()->finishPrepare(invite,world,error)); }
                catch(IllegalStateException disabled) { /* no money was taken */ }
            });
        } catch(RuntimeException error) {
            preparing.remove(invite.from); preparing.remove(invite.to);
            message(target,"&cНе удалось подготовить арену; ставки не списаны.");
            plugin.getLogger().warning("[Duel] Async arena preparation failed: "+error);
        }
    }
    private SpawnEngine.KabinkaState available(World world) {
        for(SpawnEngine.KabinkaState box:plugin.getSpawnEngine().duelKabinki()) {
            if(box.is2v2 || box.locked || plugin.getStore().isDuelArenaReserved(box.index)) continue;
            int z=(box.minZ+box.maxZ)/2;
            if(!world.isChunkLoaded(70>>4,z>>4) || !world.isChunkLoaded(72>>4,z>>4)) continue;
            boolean occupied=false;
            for(Player p:world.getPlayers()) {
                Location at=p.getLocation();
                if(at.getBlockY()>=99 && at.getBlockY()<=104 && at.getBlockX()>=box.doorX-1 && at.getBlockX()<=box.maxX
                        && at.getBlockZ()>=box.minZ && at.getBlockZ()<=box.maxZ) { occupied=true; break; }
            }
            if(occupied) continue;
            boolean safe=true;
            for(int x:new int[]{70,72}) {
                Location l=new Location(world,x+.5,100,z+.5);
                if(!world.getWorldBorder().isInside(l) || !world.getBlockAt(x,99,z).getType().isSolid()
                        || !world.getBlockAt(x,100,z).isPassable() || !world.getBlockAt(x,101,z).isPassable()) safe=false;
            }
            if(safe) return box;
        }
        return null;
    }
    private void finishPrepare(Invite invite,World world,Throwable error) {
        if(preparing.get(invite.from)!=invite || preparing.get(invite.to)!=invite) return;
        preparing.remove(invite.from); preparing.remove(invite.to);
        Player a=Bukkit.getPlayer(invite.from),b=Bukkit.getPlayer(invite.to);
        Location spawn=plugin.getSpawnEngine().getPreparedSpawnArrival();
        if(error!=null || spawn==null || spawn.getWorld()!=world || !allowed(a) || !allowed(b) || System.currentTimeMillis()>invite.expires
                || plugin.getHud().isInCombat(a) || plugin.getHud().isInCombat(b)) {
            message(a,"&cДуэль не началась; ставки не списаны."); message(b,"&cДуэль не началась; ставки не списаны."); return;
        }
        SpawnEngine.KabinkaState box=available(world);
        if(box==null) { message(a,"&eНет свободной готовой кабинки. Ставки не списаны."); message(b,"&eНет свободной готовой кабинки. Ставки не списаны."); return; }
        DataStore.ActiveWagerDuel duel=plugin.getStore().startWagerDuel(a.getName(),b.getName(),invite.bet,box.index);
        if(duel==null) { message(a,"&cДуэль не сохранена: проверьте баланс и журнал сервера."); message(b,"&cДуэль не сохранена: проверьте баланс и журнал сервера."); return; }
        Location originalA=a.getLocation().clone(),originalB=b.getLocation().clone(); int z=(box.minZ+box.maxZ)/2;
        boolean movedA=false,movedB=false;
        try {
            movedA=a.teleport(new Location(world,70.5,100,z+.5,-90f,0f));
            if(movedA && box.contains(a.getLocation())) movedB=b.teleport(new Location(world,72.5,100,z+.5,90f,0f));
        } catch(RuntimeException problem) { plugin.getLogger().warning("[Duel] Teleport failed: "+problem); }
        if(!movedA || !movedB || plugin.getStore().getActiveWagerDuel(a.getName())!=duel
                || plugin.getStore().getActiveWagerDuel(b.getName())!=duel || !box.contains(a.getLocation()) || !box.contains(b.getLocation())) {
            // Teleport cancellation is not a win. Return both stakes, and make
            // a best effort to return any player already moved by another plugin.
            attempt(new Settlement(duel,null,"телепорт в кабинку отменён"));
            if(movedA) a.teleport(originalA);
            if(movedB) b.teleport(originalB);
            return;
        }
        Bukkit.broadcastMessage(ColorUtil.c("&#FF1744⚔ Дуэль: &f"+a.getName()+" &cvs &f"+b.getName()+" &7в кабинке #"+box.index
                +(invite.bet>0?" &8[банк: &a"+ColorUtil.fmtMoney(invite.bet*2)+"$&8]":" &7без ставки")+"!"));
    }
    private boolean inArena(DataStore.ActiveWagerDuel duel,Location location) {
        for(SpawnEngine.KabinkaState box:plugin.getSpawnEngine().duelKabinki()) if(box.index==duel.arena) return box.contains(location);
        return false;
    }
    private void attempt(Settlement result) {
        DataStore.ActiveWagerDuel d=result.duel;
        if(plugin.getStore().getActiveWagerDuel(d.playerA)!=d) { pending.remove(d.id); cancelVotes.remove(d.id); return; }
        if(!plugin.getStore().settleWagerDuel(d,result.winner)) {
            pending.put(d.id,result);
            message(online(d.playerA),"&cРезультат дуэли ожидает безопасного сохранения; банк зарезервирован.");
            message(online(d.playerB),"&cРезультат дуэли ожидает безопасного сохранения; банк зарезервирован.");
            return;
        }
        pending.remove(d.id); cancelVotes.remove(d.id);
        if(result.winner==null) {
            message(online(d.playerA),"&eДуэль завершена вничью ("+result.reason+"); ставки возвращены.");
            message(online(d.playerB),"&eДуэль завершена вничью ("+result.reason+"); ставки возвращены.");
        } else {
            Bukkit.broadcastMessage(ColorUtil.c("&#FFD740⚔ Победитель дуэли: &#00E676"+result.winner
                    +(d.betEach>0?" &7— банк &a"+ColorUtil.fmtMoney(d.betEach*2)+"$":"")+" &8("+result.reason+")"));
        }
    }
    private void settle(DataStore.ActiveWagerDuel d,String winner,String reason) {
        if(d==null || pending.containsKey(d.id)) return;
        attempt(new Settlement(d,winner,reason));
    }
    private void cancel(Player p) {
        prune(); DataStore.ActiveWagerDuel duel=plugin.getStore().getActiveWagerDuel(p.getName());
        if(duel!=null) {
            if(pending.containsKey(duel.id)) { message(p,"&eРезультат уже ожидает сохранения."); return; }
            Vote v=cancelVotes.get(duel.id);
            if(v!=null && !v.by.equals(p.getUniqueId())) { settle(duel,null,"оба участника согласились"); return; }
            if(v==null) cancelVotes.put(duel.id,new Vote(p.getUniqueId()));
            message(p,"&eПредложена ничья на 60 секунд. Сопернику нужно ввести /duel cancel.");
            message(online(duel.opponent(p.getName())),"&eСоперник предлагает ничью. Для возврата ставок введите /duel cancel."); return;
        }
        Invite wait=preparing.get(p.getUniqueId());
        if(wait!=null) { preparing.remove(wait.from); preparing.remove(wait.to); message(p,"&eПодготовка отменена; ставки не списаны."); return; }
        Invite outgoing=outgoing(p.getUniqueId());
        if(outgoing!=null) { invites.remove(outgoing.to); message(p,"&eВызов отменён."); message(Bukkit.getPlayer(outgoing.to),"&eСоперник отменил вызов."); return; }
        message(p,"&eНет исходящего вызова или активной дуэли.");
    }
    private void status(Player p) {
        prune(); DataStore.ActiveWagerDuel d=plugin.getStore().getActiveWagerDuel(p.getName());
        if(d!=null) message(p,"&#FFD740⚔ Соперник: &f"+d.opponent(p.getName())+" &7| кабинка #"+d.arena+" | банк &a"+ColorUtil.fmtMoney(d.betEach*2)+"$"
                +(pending.containsKey(d.id)?" &c| результат ожидает сохранения":""));
        else if(preparing.containsKey(p.getUniqueId())) message(p,"&7Идёт подбор кабинки; ставки ещё не списаны.");
        else if(invites.containsKey(p.getUniqueId())) message(p,"&7Вам отправлен вызов: &f/duel accept &7или &f/duel decline");
        else if(outgoing(p.getUniqueId())!=null) message(p,"&7Ваш вызов ожидает ответа. &f/duel cancel");
        else message(p,"&7Сейчас активной дуэли нет.");
        message(p,"&7Победы: &a"+plugin.getStore().getDuelStat(p.getName(),"wins")+" &7| Поражения: &c"+plugin.getStore().getDuelStat(p.getName(),"losses"));
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(!(sender instanceof Player)) { sender.sendMessage("Дуэли доступны игрокам."); return true; }
        Player p=(Player)sender;
        if(args.length==0) { message(p,"&e/duel <ник> [ставка] | accept | decline | cancel | status | stats"); return true; }
        if(args.length==1 && ("status".equalsIgnoreCase(args[0]) || "stats".equalsIgnoreCase(args[0]))) { status(p); return true; }
        if(args.length==1 && "accept".equalsIgnoreCase(args[0])) { accept(p); return true; }
        if(args.length==1 && "cancel".equalsIgnoreCase(args[0])) { cancel(p); return true; }
        if(args.length==1 && "decline".equalsIgnoreCase(args[0])) {
            prune(); Invite invite=invites.remove(p.getUniqueId());
            if(invite==null) message(p,"&eАктивного вызова нет."); else { message(p,"&eВызов отклонён."); message(Bukkit.getPlayer(invite.from),"&eСоперник отклонил вызов."); }
            return true;
        }
        if(args.length>2) { message(p,"&e/duel <ник> [ставка]"); return true; }
        Player target=Bukkit.getPlayerExact(args[0]);
        if(target==null || target.equals(p)) { message(p,"&cСоперник не найден."); return true; }
        double bet=0;
        if(args.length==2) try { bet=Double.parseDouble(args[1]); } catch(NumberFormatException invalid) { bet=Double.NaN; }
        sendInvite(p,target,bet); return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command cmd,String alias,String[] args) {
        List<String> out=new ArrayList<String>();
        if(args.length==1) {
            String prefix=args[0].toLowerCase(Locale.ROOT);
            for(String s:Arrays.asList("accept","decline","cancel","status","stats")) if(s.startsWith(prefix)) out.add(s);
            for(Player p:Bukkit.getOnlinePlayers()) if(p!=sender && p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(p.getName());
        }
        return out;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if(!(e.getEntity() instanceof Player)) return;
        Player victim=(Player)e.getEntity(),attacker=null;
        if(e.getDamager() instanceof Player) attacker=(Player)e.getDamager();
        else if(e.getDamager() instanceof Projectile && ((Projectile)e.getDamager()).getShooter() instanceof Player) attacker=(Player)((Projectile)e.getDamager()).getShooter();
        if(attacker==null) return;
        DataStore.ActiveWagerDuel a=plugin.getStore().getActiveWagerDuel(attacker.getName()), b=plugin.getStore().getActiveWagerDuel(victim.getName());
        if(a==null && b==null) return;
        if(a==null || a!=b || pending.containsKey(a.id) || !inArena(a,attacker.getLocation()) || !inArena(a,victim.getLocation())) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onDeath(PlayerDeathEvent e) {
        Player dead=e.getEntity(); DataStore.ActiveWagerDuel d=plugin.getStore().getActiveWagerDuel(dead.getName());
        if(d==null) return;
        Player killer=dead.getKiller(); String opponent=d.opponent(dead.getName());
        boolean fair=killer!=null && killer.getName().equalsIgnoreCase(opponent) && inArena(d,dead.getLocation()) && inArena(d,killer.getLocation());
        Bukkit.getScheduler().runTask(plugin,()->{
            if(!e.isCancelled() && dead.isDead()) settle(d,fair?opponent:null,fair?"победа в бою":"бой прерван");
        });
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onLeaveArena(PlayerMoveEvent e) {
        Player p=e.getPlayer(); DataStore.ActiveWagerDuel d=plugin.getStore().getActiveWagerDuel(p.getName());
        if(d==null || e.getTo()==null || !inArena(d,e.getFrom()) || inArena(d,e.getTo())) return;
        Bukkit.getScheduler().runTask(plugin,()->{
            if(!e.isCancelled() && p.isOnline() && !p.isDead() && !inArena(d,p.getLocation())) settle(d,null,"участник покинул кабинку");
        });
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player p=e.getPlayer(); invites.remove(p.getUniqueId()); invites.entrySet().removeIf(entry->entry.getValue().from.equals(p.getUniqueId()));
        Invite prep=preparing.remove(p.getUniqueId()); if(prep!=null) { preparing.remove(prep.from); preparing.remove(prep.to); }
        if(shuttingDown || Bukkit.isStopping()) return;
        DataStore.ActiveWagerDuel d=plugin.getStore().getActiveWagerDuel(p.getName());
        if(d==null) return;
        Player other=online(d.opponent(p.getName()));
        boolean fair=other!=null && inArena(d,p.getLocation()) && inArena(d,other.getLocation());
        settle(d,fair?other.getName():null,fair?"соперник вышел":"бой прерван");
    }
}
