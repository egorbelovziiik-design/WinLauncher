package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Private memory-puzzle GUI. No teleport, world edit, wager or inventory transfer. */
final class MemoryGameManager implements Listener {
    private static final int[] TILES={10,11,12,19,20,21,28,29,30};
    private static final int MAX_ROUNDS=12, TITLE_ROUNDS=8, MAX_SESSIONS=64, MAX_RECORDS=4096;
    private static final String ROOT="memoryGame.records";
    private enum Phase { SHOW, INPUT, BETWEEN, ENDED }
    private static final class Session implements InventoryHolder {
        final UUID player;final List<Integer> sequence=new ArrayList<Integer>();
        Inventory inventory;BukkitRunnable task;Phase phase=Phase.BETWEEN;int score,index,lit=-1;boolean recordsAvailable=true;
        Session(UUID player){this.player=player;}
        @Override public Inventory getInventory(){return inventory;}
    }
    private static final class Record {
        final String name;final int score;
        Record(String name,int score){this.name=name;this.score=score;}
    }
    private final KingGriefCore plugin;
    private final Map<UUID,Session> sessions=new HashMap<UUID,Session>();
    private final Map<UUID,Long> lastStart=new HashMap<UUID,Long>();
    private final ItemStack[] dark=new ItemStack[9],light=new ItemStack[9];
    private BukkitRunnable saver;private boolean dirty;
    MemoryGameManager(KingGriefCore plugin){
        this.plugin=plugin;
        for(int i=0;i<9;i++){
            dark[i]=ItemFactory.icon(Material.BLUE_STAINED_GLASS,"&#80D8FFПлитка "+(i+1),"&7Повторите последовательность после показа.");
            light[i]=ItemFactory.glowIcon(Material.SEA_LANTERN,"&#FFD740✦ Плитка "+(i+1),"&7Запомните эту плитку!");
        }
    }
    void start(){saver=new BukkitRunnable(){@Override public void run(){if(dirty)flush();}};saver.runTaskTimer(plugin,1200L,1200L);}
    void shutdown(){
        if(saver!=null)saver.cancel();
        for(Session session:new ArrayList<Session>(sessions.values())){
            Player p=Bukkit.getPlayer(session.player);stop(session);
            if(p!=null&&p.isOnline()&&p.getOpenInventory().getTopInventory().getHolder()==session)p.closeInventory();
        }
        lastStart.clear();if(dirty)flush();
    }
    private void flush(){if(plugin.getStore().saveBuffered())dirty=false;else plugin.getLogger().warning("[Memory] Records remain in memory; save will be retried.");}
    private boolean permitted(Player p){return p!=null&&p.isOnline()&&!p.isDead()&&plugin.getStore().isAuthenticated(p)
            &&!plugin.getStore().isQuickFrozen(p)&&!plugin.getModeration().isFrozenOrAscending(p)&&!plugin.getHud().isInCombat(p);}
    private static void send(Player p,String text){p.sendMessage(ColorUtil.c(text));}
    void command(Player p,String[] args){
        if(args.length==1&&"top".equalsIgnoreCase(args[0])){top(p);return;}
        if(args.length==1&&"stop".equalsIgnoreCase(args[0])){
            Session session=sessions.get(p.getUniqueId());if(session==null){send(p,"&7Сейчас у вас нет игры.");return;}
            stop(session);if(p.getOpenInventory().getTopInventory().getHolder()==session)p.closeInventory();send(p,"&7Игра остановлена.");return;
        }
        if(args.length!=0){send(p,"&e/snowmemory [top|stop]");return;}
        open(p);
    }
    void open(Player p){
        if(!permitted(p)){send(p,"&cДля игры войдите в аккаунт и выйдите из боя или режима модерации.");return;}
        if(p.getItemOnCursor()!=null&&!p.getItemOnCursor().getType().isAir()){
            send(p,"&eСначала положите предмет с курсора в инвентарь.");return;
        }
        Session current=sessions.get(p.getUniqueId());
        if(current!=null){send(p,"&eИгра уже открыта. Закройте её или используйте /snowmemory stop.");return;}
        if(sessions.size()>=MAX_SESSIONS){send(p,"&eСейчас все игровые места заняты. Попробуйте позже.");return;}
        long now=System.nanoTime();Long last=lastStart.get(p.getUniqueId());
        if(last!=null&&now-last.longValue()<2_000_000_000L){send(p,"&eПодождите немного перед новой игрой.");return;}
        Session s=new Session(p.getUniqueId());s.inventory=Bukkit.createInventory(s,45,ColorUtil.c("&#80D8FF&l❄ ЛЕДЯНАЯ ПАМЯТЬ"));
        decorate(s);sessions.put(s.player,s);p.openInventory(s.inventory);
        if(p.getOpenInventory().getTopInventory().getHolder()!=s){stop(s);send(p,"&cОкно игры не открылось.");return;}
        lastStart.put(s.player,Long.valueOf(now));send(p,"&7Игра не защищает от урона и не телепортирует. Ник и лучший результат видны в /snowmemory top.");
        beginRound(s);
    }
    private void decorate(Session s){
        ItemStack frame=ItemFactory.icon(Material.LIGHT_BLUE_STAINED_GLASS_PANE," ");
        for(int i=0;i<s.inventory.getSize();i++)s.inventory.setItem(i,frame);
        for(int i=0;i<TILES.length;i++)s.inventory.setItem(TILES[i],dark[i]);
        s.inventory.setItem(16,ItemFactory.icon(Material.BOOK,"&#80D8FFКак играть",
                "&7Смотрите на подсветку, затем нажмите", "&7плитки в том же порядке.", "&7Всего 12 раундов, без денежных наград."));
        s.inventory.setItem(34,ItemFactory.icon(Material.NAME_TAG,"&#FFD740Титул «Ледяной разум»",
                "&7Откроется после 8 раундов.","&7Рекорды: &f/snowmemory top"));
        s.inventory.setItem(40,ItemFactory.icon(Material.BARRIER,"&cЗакончить игру"));
        status(s,"&7Приготовьтесь...");
    }
    private void status(Session s,String line){
        s.inventory.setItem(4,ItemFactory.glowIcon(Material.SNOWBALL,"&#80D8FF❄ Ледяная память",line,
                "&7Пройдено раундов: &f"+s.score+"/"+MAX_ROUNDS));
        s.inventory.setItem(25,ItemFactory.icon(Material.CLOCK,"&fРаунд: "+Math.min(MAX_ROUNDS,s.score+1),line));
    }
    private Player viewer(Session s){
        Player p=Bukkit.getPlayer(s.player);
        return sessions.get(s.player)==s&&p!=null&&p.isOnline()&&p.getOpenInventory().getTopInventory().getHolder()==s?p:null;
    }
    private void cancelTask(Session s){if(s.task!=null){try{s.task.cancel();}catch(IllegalStateException ignored){}s.task=null;}}
    private void stop(Session s){if(sessions.get(s.player)!=s)return;cancelTask(s);sessions.remove(s.player);}
    private void closeIfInvalid(Session s){
        Player p=viewer(s);if(p==null){stop(s);return;}
        if(!permitted(p)){stop(s);p.closeInventory();send(p,"&eИгра остановлена из-за боя или ограничений аккаунта.");}
    }
    private void beginRound(Session s){
        Player p=viewer(s);if(p==null){stop(s);return;}
        if(!permitted(p)){closeIfInvalid(s);return;}
        cancelTask(s);s.sequence.add(Integer.valueOf(ThreadLocalRandom.current().nextInt(TILES.length)));
        s.phase=Phase.SHOW;s.index=0;s.lit=-1;status(s,"&eСмотрите и запоминайте...");
        s.task=new BukkitRunnable(){int shown=0;
            @Override public void run(){
                Player player=viewer(s);if(player==null){stop(s);return;}
                if(!permitted(player)){closeIfInvalid(s);return;}
                if(s.lit>=0){s.inventory.setItem(TILES[s.lit],dark[s.lit]);s.lit=-1;return;}
                if(shown>=s.sequence.size()){
                    cancelTask(s);s.phase=Phase.INPUT;s.index=0;status(s,"&aВаша очередь! Повторите порядок.");
                    s.task=new BukkitRunnable(){@Override public void run(){
                        if(viewer(s)!=null&&s.phase==Phase.INPUT)end(s,"&cВремя вышло.");else stop(s);
                    }};s.task.runTaskLater(plugin,900L);return;
                }
                int tile=s.sequence.get(shown++).intValue();s.lit=tile;s.inventory.setItem(TILES[tile],light[tile]);
                ColorUtil.sound(player,"block.note_block.pling",0.6f,0.7f+tile*0.1f);
            }
        };s.task.runTaskTimer(plugin,10L,8L);
    }
    private void end(Session s,String message){
        cancelTask(s);if(s.lit>=0){s.inventory.setItem(TILES[s.lit],dark[s.lit]);s.lit=-1;}
        s.phase=Phase.ENDED;status(s,message);Player p=viewer(s);
        if(p!=null){send(p,message+" &7Результат: &f"+s.score+"/"+MAX_ROUNDS+(s.recordsAvailable?"&7. Новые рекорды сохраняются автоматически.":"&c. Запись рекорда недоступна."));
            send(p,"&7Чтобы сыграть снова, закройте окно и введите &f/snowmemory&7.");}
        // Finished windows cannot reserve all session slots indefinitely.
        s.task=new BukkitRunnable(){@Override public void run(){Player viewer=viewer(s);stop(s);if(viewer!=null)viewer.closeInventory();}};
        s.task.runTaskLater(plugin,600L);
    }
    private void answer(Session s,int tile){
        Player p=viewer(s);if(p==null){stop(s);return;}
        if(!permitted(p)){Bukkit.getScheduler().runTask(plugin,()->closeIfInvalid(s));return;}
        if(s.phase!=Phase.INPUT)return;
        if(s.index>=s.sequence.size()||s.sequence.get(s.index).intValue()!=tile){end(s,"&cНеверная плитка.");return;}
        s.index++;ColorUtil.sound(p,"block.note_block.hat",0.6f,1.0f);
        if(s.index<s.sequence.size()){status(s,"&aВерно: "+s.index+"/"+s.sequence.size());return;}
        cancelTask(s);s.score++;if(!record(p,s.score))s.recordsAvailable=false;
        if(s.score>=MAX_ROUNDS){end(s,"&#00E676Все 12 раундов пройдены!");return;}
        s.phase=Phase.BETWEEN;status(s,"&#00E676Раунд пройден! Далее...");
        s.task=new BukkitRunnable(){@Override public void run(){s.task=null;beginRound(s);}};s.task.runTaskLater(plugin,20L);
    }
    private boolean record(Player p,int score){
        if(score<1||score>MAX_ROUNDS||!p.getName().matches("[A-Za-z0-9_]{1,16}"))return false;
        try{
            int result=plugin.getStore().buffered(data->{
                String path=ROOT+"."+p.getUniqueId(),title="players."+p.getName().toLowerCase(Locale.ROOT)+".titles.memory_master";
                int before=data.getInt(path+".score",0);ConfigurationSection root=data.getConfigurationSection(ROOT);
                if(before<=0&&root!=null&&root.getKeys(false).size()>=MAX_RECORDS)return Integer.valueOf(-1);
                boolean update=score>before,unlock=score>=TITLE_ROUNDS&&!data.getBoolean(title,false);
                if(update){data.set(path+".score",score);data.set(path+".name",p.getName());}
                if(unlock)data.set(title,true);return Integer.valueOf(unlock?2:update?1:0);
            }).intValue();
            if(result<0){if(score==1)send(p,"&eТаблица рекордов заполнена; эта игра не записывается.");return false;}
            if(result>0)dirty=true;
            if(result==2)send(p,"&#FFD740Открыт косметический титул «Ледяной разум»: /titles.");
            return true;
        }catch(Exception error){send(p,"&cНе удалось записать рекорд.");plugin.getLogger().warning("[Memory] Record failed: "+error.getMessage());return false;}
    }
    private void top(Player p){
        try{
            List<Record> records=plugin.getStore().inspect(data->{
                List<Record> list=new ArrayList<Record>();ConfigurationSection root=data.getConfigurationSection(ROOT);
                if(root!=null)for(String key:root.getKeys(false)){
                    if(list.size()>=MAX_RECORDS)break;
                    try{UUID.fromString(key);}catch(IllegalArgumentException invalid){continue;}
                    int score=root.getInt(key+".score",0);String name=root.getString(key+".name","");
                    if(score>0&&score<=MAX_ROUNDS&&name.matches("[A-Za-z0-9_]{1,16}"))list.add(new Record(name,score));
                }
                list.sort(Comparator.comparingInt((Record r)->r.score).reversed().thenComparing(r->r.name.toLowerCase(Locale.ROOT)));return list;
            });
            send(p,"&#80D8FF✦ Рекорды ледяной памяти &8(максимум 12 раундов):");
            if(records.isEmpty())send(p,"&7Пока рекордов нет. Начните игру: /snowmemory");
            for(int i=0;i<Math.min(10,records.size());i++){Record r=records.get(i);send(p," &8"+(i+1)+". &f"+r.name+" &8— &b"+r.score+"/12");}
        }catch(Exception error){send(p,"&cНе удалось прочитать рекорды.");plugin.getLogger().warning("[Memory] Top failed: "+error.getMessage());}
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void click(InventoryClickEvent event){
        Inventory top=event.getView().getTopInventory();if(!(top.getHolder() instanceof Session))return;
        Session s=(Session)top.getHolder();boolean blocked=event.isCancelled();event.setCancelled(true);
        if(blocked||!(event.getWhoClicked() instanceof Player)||!event.getWhoClicked().getUniqueId().equals(s.player)||sessions.get(s.player)!=s)return;
        if(event.getClick()!=ClickType.LEFT&&event.getClick()!=ClickType.RIGHT)return;
        int raw=event.getRawSlot();if(raw==40){s.phase=Phase.ENDED;cancelTask(s);Bukkit.getScheduler().runTask(plugin,()->{
            Player p=viewer(s);stop(s);if(p!=null)p.closeInventory();});return;}
        for(int i=0;i<TILES.length;i++)if(raw==TILES[i]){answer(s,i);return;}
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof Session)event.setCancelled(true);}
    @EventHandler public void close(InventoryCloseEvent event){
        if(event.getInventory().getHolder() instanceof Session){Session s=(Session)event.getInventory().getHolder();if(event.getPlayer().getUniqueId().equals(s.player))stop(s);}
    }
    @EventHandler public void quit(PlayerQuitEvent event){Session s=sessions.get(event.getPlayer().getUniqueId());if(s!=null)stop(s);lastStart.remove(event.getPlayer().getUniqueId());}
    @EventHandler public void death(PlayerDeathEvent event){Session s=sessions.get(event.getEntity().getUniqueId());if(s!=null)stop(s);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void damage(EntityDamageEvent event){
        if(!(event.getEntity() instanceof Player)||event.getFinalDamage()<=0)return;Player p=(Player)event.getEntity();Session s=sessions.get(p.getUniqueId());if(s==null)return;
        Bukkit.getScheduler().runTask(plugin,()->{
            if(event.isCancelled()||event.getFinalDamage()<=0||viewer(s)==null)return;
            stop(s);p.closeInventory();send(p,"&eИгра остановлена: вы получили урон.");
        });
    }
}
