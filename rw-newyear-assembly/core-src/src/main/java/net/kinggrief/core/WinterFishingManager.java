package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Optional bounded bonuses for normal fish catches. Vanilla loot is never replaced or cancelled. */
final class WinterFishingManager implements Listener {
    private static final int DAILY_LIMIT=12, TITLE_GOAL=25, MAX_PENDING=512, MAX_BATCH=64, MAX_SEEN=2048;
    private static final long COOLDOWN_MS=10_000L;
    private static final class Catch {
        final UUID hook, player;
        final String name;
        final PlayerFishEvent event;
        boolean ready;
        Catch(PlayerFishEvent event){
            this.event=event;hook=event.getHook().getUniqueId();player=event.getPlayer().getUniqueId();name=event.getPlayer().getName();
        }
    }
    private static final class Award {
        final UUID player;final int snow;final String rarity;final boolean title;
        Award(UUID player,int snow,String rarity,boolean title){this.player=player;this.snow=snow;this.rarity=rarity;this.title=title;}
    }
    private static final class Stats {
        int total,daily;long last;boolean title,clockError;
    }
    public static final class FishingHolder implements InventoryHolder {
        final UUID owner;Inventory inventory;
        FishingHolder(UUID owner){this.owner=owner;}
        @Override public Inventory getInventory(){return inventory;}
    }
    private final KingGriefCore plugin;
    private final Map<UUID,Catch> pending=new LinkedHashMap<UUID,Catch>();
    private final Map<UUID,Long> seen=new LinkedHashMap<UUID,Long>();
    private final Random random=new Random();
    private BukkitRunnable timer;
    WinterFishingManager(KingGriefCore plugin){this.plugin=plugin;}
    void start(){
        timer=new BukkitRunnable(){@Override public void run(){flush(false);pruneSeen();}};
        timer.runTaskTimer(plugin,20L,20L);
    }
    void shutdown(){if(timer!=null)timer.cancel();while(!pending.isEmpty())flush(true);seen.clear();}
    private boolean allowed(Player player){
        return player!=null&&player.isOnline()&&!player.isDead()&&player.getGameMode()==GameMode.SURVIVAL
                &&plugin.getStore().isAuthenticated(player)&&!plugin.getStore().isQuickFrozen(player)
                &&!plugin.getModeration().isFrozenOrAscending(player);
    }
    private static boolean fish(Material material){
        return material==Material.COD||material==Material.SALMON||material==Material.TROPICAL_FISH||material==Material.PUFFERFISH;
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void caught(PlayerFishEvent event){
        if(event.getState()!=PlayerFishEvent.State.CAUGHT_FISH||!(event.getCaught() instanceof Item)||!allowed(event.getPlayer()))return;
        ItemStack caught=((Item)event.getCaught()).getItemStack();
        if(caught==null||caught.getAmount()<=0||!fish(caught.getType())||event.getHook()==null)return;
        Catch candidate=new Catch(event);
        if(seen.containsKey(candidate.hook))return;
        if(pending.size()>=MAX_PENDING){event.getPlayer().sendMessage(ColorUtil.c("&eЗимний бонус пропущен: очередь занята. Обычный улов не меняется."));return;}
        seen.put(candidate.hook,System.nanoTime());
        while(seen.size()>MAX_SEEN)seen.remove(seen.keySet().iterator().next());
        pending.put(candidate.hook,candidate);
        // Check cancellation again only after all listeners have returned.
        Bukkit.getScheduler().runTask(plugin,()->{Catch live=pending.get(candidate.hook);if(live==candidate)live.ready=true;});
    }
    private void pruneSeen(){
        long now=System.nanoTime();Iterator<Map.Entry<UUID,Long>> iterator=seen.entrySet().iterator();
        while(iterator.hasNext())if(now-iterator.next().getValue()>120_000_000_000L)iterator.remove();
    }
    private static String today(){return LocalDate.now(ZoneOffset.UTC).toString();}
    private static String base(UUID uuid){return "winterFishing."+uuid;}
    private static String playerBase(String name){return "players."+name.toLowerCase(Locale.ROOT);}
    private static Stats stats(YamlConfiguration data,UUID id,String name,String day,long now){
        Stats result=new Stats();String root=base(id),previous=data.getString(root+".day");
        result.clockError=(previous!=null&&previous.compareTo(day)>0);
        result.daily=day.equals(previous)?Math.max(0,data.getInt(root+".daily",0)):0;
        result.total=Math.max(0,data.getInt(root+".total",0));result.last=Math.max(0,data.getLong(root+".last",0L));
        result.clockError|=result.last>now;
        result.title=data.getBoolean(playerBase(name)+".titles.winter_fisher",false);
        return result;
    }
    private boolean eligible(YamlConfiguration data,Catch candidate,String day,long now){
        Stats stats=stats(data,candidate.player,candidate.name,day,now);
        int snow=data.getInt(playerBase(candidate.name)+".snowflakes",0);
        return !stats.clockError&&stats.daily<DAILY_LIMIT&&(stats.last==0||now-stats.last>=COOLDOWN_MS)
                &&snow>=0&&snow<=Integer.MAX_VALUE-5;
    }
    private void flush(boolean stopping){
        List<Catch> batch=new ArrayList<Catch>();Iterator<Catch> iterator=pending.values().iterator();
        while(iterator.hasNext()&&batch.size()<MAX_BATCH){
            Catch candidate=iterator.next();
            if(!candidate.ready&&!stopping)continue;
            iterator.remove();if(!candidate.event.isCancelled())batch.add(candidate);
        }
        if(batch.isEmpty())return;
        final String day=today();final long now=System.currentTimeMillis();List<Award> awards;
        try{
            boolean any=plugin.getStore().inspect(data->{for(Catch candidate:batch)if(eligible(data,candidate,day,now))return true;return false;});
            if(!any)return;
            awards=plugin.getStore().transact(data->{
                List<Award> result=new ArrayList<Award>();
                for(Catch candidate:batch){
                    if(!eligible(data,candidate,day,now))continue;
                    String root=base(candidate.player),player=playerBase(candidate.name);
                    Stats state=stats(data,candidate.player,candidate.name,day,now);
                    int roll=random.nextInt(100),snow=roll<80?1:roll<97?2:5;
                    String rarity=roll<80?"ледяной":roll<97?"серебряный":"полярный";
                    int total=state.total==Integer.MAX_VALUE?Integer.MAX_VALUE:state.total+1;
                    data.set(player+".snowflakes",data.getInt(player+".snowflakes",0)+snow);
                    data.set(root+".day",day);data.set(root+".daily",state.daily+1);data.set(root+".last",now);data.set(root+".total",total);
                    boolean title=total>=TITLE_GOAL&&!state.title;
                    if(title)data.set(player+".titles.winter_fisher",true);
                    result.add(new Award(candidate.player,snow,rarity,title));
                }
                return result;
            });
        }catch(Exception error){
            plugin.getLogger().warning("[WinterFishing] Batch not committed; vanilla catches unchanged: "+error.getMessage());
            for(Catch candidate:batch){Player player=Bukkit.getPlayer(candidate.player);if(player!=null&&player.isOnline())
                player.sendMessage(ColorUtil.c("&cЗимний бонус не сохранён. Обычный улов не меняется."));}
            return;
        }
        // Notifications cannot turn a successful commit into a reported failure.
        for(Award award:awards){
            Player player=Bukkit.getPlayer(award.player);if(player==null||!player.isOnline())continue;
            try{
                player.sendMessage(ColorUtil.c("&#80D8FF❄ Зимняя рыбалка: &f"+award.rarity+" клёв &8• &b+"+award.snow+" снежинок"
                        +(award.title?" &8• &#FFD740титул «Рыбак севера» открыт в /titles":"")));
                player.spawnParticle(Particle.SNOWFLAKE,player.getLocation().add(0,1,0),8,.3,.4,.3,0);
                ColorUtil.sound(player,"entity.player.levelup",.4f,1.4f);
            }catch(RuntimeException error){plugin.getLogger().warning("[WinterFishing] Award committed, but notification failed: "+error.getMessage());}
        }
    }
    void command(Player player,String[] args){
        if(args.length!=0){player.sendMessage(ColorUtil.c("&e/winterfish &7— правила и личная статистика"));return;}
        open(player);
    }
    void open(Player player){
        if(!allowed(player)){player.sendMessage(ColorUtil.c("&cДля зимней рыбалки войдите и используйте режим выживания."));return;}
        try{
            String day=today();long now=System.currentTimeMillis();
            Stats state=plugin.getStore().inspect(data->stats(data,player.getUniqueId(),player.getName(),day,now));
            FishingHolder holder=new FishingHolder(player.getUniqueId());
            Inventory inventory=Bukkit.createInventory(holder,27,ColorUtil.c("&#80D8FF&l🎣 ЗИМНЯЯ РЫБАЛКА"));holder.inventory=inventory;
            ItemStack frame=ItemFactory.icon(Material.LIGHT_BLUE_STAINED_GLASS_PANE," ");
            for(int i=0;i<27;i++)if(i<9||i>=18||i%9==0||i%9==8)inventory.setItem(i,frame);
            inventory.setItem(4,ItemFactory.glowIcon(Material.FISHING_ROD,"&#80D8FF&lЗИМНИЙ КЛЁВ",
                    "&7Ловите обычную рыбу удочкой в выживании.","&7Ванильный улов не меняется; бонус начисляется отдельно.",
                    "&7Бонусов сегодня: &f"+Math.min(DAILY_LIMIT,state.daily)+" / "+DAILY_LIMIT+" &8(UTC)",
                    "&7Между бонусами: &f10 секунд",state.clockError?"&cЧасы сервера отстают от сохранённого времени.":"&8Бонусы не выдаются за мусор и сокровища."));
            inventory.setItem(11,ItemFactory.icon(Material.COD,"&#80D8FFЛедяной клёв", "&780% &8• &b+1 снежинка"));
            inventory.setItem(13,ItemFactory.icon(Material.SALMON,"&fСеребряный клёв", "&717% &8• &b+2 снежинки"));
            inventory.setItem(15,ItemFactory.icon(Material.TROPICAL_FISH,"&#FFD740Полярный клёв", "&73% &8• &b+5 снежинок"));
            inventory.setItem(20,ItemFactory.icon(Material.NAME_TAG,"&#FFD740Титул «Рыбак севера»",
                    "&7Получите 25 зимних бонусов за рыбалку.","&7Прогресс: &f"+Math.min(TITLE_GOAL,state.total)+" / "+TITLE_GOAL,
                    state.title?"&#00E676✔ Открыт в /titles":"&8Только косметика."));
            inventory.setItem(24,ItemFactory.icon(Material.BOOK,"&bСтатистика", "&7Получено бонусов: &f"+state.total,
                    "&7Ожидают обработки: &f"+pendingFor(player.getUniqueId()),"&8При переполнении очереди бонус может быть пропущен."));
            inventory.setItem(26,ItemFactory.icon(Material.BARRIER,"&cЗакрыть"));
            player.openInventory(inventory);
        }catch(Exception error){player.sendMessage(ColorUtil.c("&cНе удалось прочитать статистику рыбалки."));}
    }
    private int pendingFor(UUID uuid){int count=0;for(Catch candidate:pending.values())if(candidate.player.equals(uuid))count++;return count;}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void click(InventoryClickEvent event){
        Inventory top=event.getView().getTopInventory();if(!(top.getHolder() instanceof FishingHolder))return;
        boolean wasCancelled=event.isCancelled();event.setCancelled(true);
        if(wasCancelled||!(event.getWhoClicked() instanceof Player)||!top.equals(event.getClickedInventory())||event.getRawSlot()!=26)return;
        Player player=(Player)event.getWhoClicked();FishingHolder holder=(FishingHolder)top.getHolder();
        if(!holder.owner.equals(player.getUniqueId())||!allowed(player))return;
        Bukkit.getScheduler().runTask(plugin,()->{if(player.isOnline()&&player.getOpenInventory().getTopInventory().getHolder()==holder)player.closeInventory();});
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof FishingHolder)event.setCancelled(true);}
}
