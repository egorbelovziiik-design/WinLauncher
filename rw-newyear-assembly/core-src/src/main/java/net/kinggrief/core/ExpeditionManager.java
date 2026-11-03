package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The five original daily tasks retain their paths and bonus requirements.
 * Five expert tasks track the same activities independently. Progress is
 * buffered; a claim (including claim-all) commits every marker and both
 * currencies together in data.yml. Bukkit state is accessed on the main thread.
 * Ore marks cannot recognize old placed ore or edits which bypass Bukkit.
 */
public final class ExpeditionManager implements Listener, CommandExecutor, TabCompleter {
    private enum Task {
        ORE("ore", "⛏ Шахтёр", "Добудьте 32 руды или древних обломка", Material.DIAMOND_ORE, 32, 3500, 3),
        HARVEST("harvest", "🌾 Земледелец", "Соберите 20 полностью созревших культур", Material.WHEAT, 20, 3000, 2),
        HUNT("hunt", "⚔ Охотник", "Победите 12 враждебных монстров", Material.IRON_SWORD, 12, 4000, 3),
        FISH("fish", "🎣 Рыбак", "Поймайте 3 рыбы удочкой", Material.FISHING_ROD, 3, 3000, 2),
        WALK("walk", "🧭 Путешественник", "Пройдите 600 блоков пешком", Material.COMPASS, 600, 4000, 3),
        ORE_PLUS("ore_plus", "⛏ Мастер шахты", "Добудьте 64 руды или древних обломка", Material.DEEPSLATE_DIAMOND_ORE, 64, 7000, 6),
        HARVEST_PLUS("harvest_plus", "🌾 Мастер урожая", "Соберите 40 полностью созревших культур", Material.GOLDEN_HOE, 40, 6000, 4),
        HUNT_PLUS("hunt_plus", "⚔ Мастер охоты", "Победите 24 враждебных монстра", Material.DIAMOND_SWORD, 24, 8000, 6),
        FISH_PLUS("fish_plus", "🎣 Мастер рыбалки", "Поймайте 6 рыб удочкой", Material.TROPICAL_FISH, 6, 6000, 4),
        WALK_PLUS("walk_plus", "🧭 Мастер пути", "Пройдите 1200 блоков пешком", Material.FILLED_MAP, 1200, 8000, 6);
        final String id, title, description; final Material icon; final int goal, coins, snow;
        Task(String id, String title, String description, Material icon, int goal, int coins, int snow) {
            this.id=id; this.title=title; this.description=description; this.icon=icon; this.goal=goal; this.coins=coins; this.snow=snow;
        }
        boolean expert() { return ordinal()>=5; }
        Task basic() { return expert()?values()[ordinal()-5]:this; }
        Task advanced() { return expert()?this:values()[ordinal()+5]; }
    }
    private static final int DAILY_COINS=10000, DAILY_SNOW=5, WEEKLY_GOAL=10, WEEKLY_COINS=25000, WEEKLY_SNOW=10;
    private static final int EXPERT_DAILY_COINS=20000, EXPERT_DAILY_SNOW=10, EXPERT_WEEKLY_COINS=50000, EXPERT_WEEKLY_SNOW=20;
    private static final int[] SLOTS = {10, 11, 12, 13, 14};
    private final KingGriefCore plugin;
    private final NamespacedKey placedOreKey;
    private final Map<UUID, Walk> walking = new HashMap<UUID, Walk>();
    private final Map<UUID, Long> lastClaim = new HashMap<UUID, Long>();
    private final Map<BlockKey, Object> markerChanges = new HashMap<BlockKey, Object>();
    private final List<PlayerMoveEvent> moves = new ArrayList<PlayerMoveEvent>();
    private boolean moveScheduled;
    private Clock clock = Clock.systemUTC();
    private boolean dirty;
    private BukkitRunnable autosave;

    private static final class Walk {
        final String day; double fraction;
        Walk(String day) { this.day = day; }
    }
    private static final class Periods {
        final String day, week;
        Periods(LocalDate d) { day=d.toString(); week=d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString(); }
    }
    private static final class Snapshot {
        final int[] progress = new int[Task.values().length];
        final boolean[] claimed = new boolean[Task.values().length];
        int weekly, expertWeekly; boolean dailyBonus, weeklyBonus, expertDailyBonus, expertWeeklyBonus, clockError;
    }
    private enum Result { CLAIMED, NOT_READY, ALREADY, CLOCK_ERROR }
    private static final class ClaimResult {
        final Result result; final int coins, snow, count;
        ClaimResult(Result result, int coins, int snow, int count) { this.result=result; this.coins=coins; this.snow=snow; this.count=count; }
    }
    public static final class ExpeditionHolder implements InventoryHolder {
        final UUID owner; final int page; Inventory inventory;
        ExpeditionHolder(UUID owner,int page) { this.owner=owner; this.page=page; }
        @Override public Inventory getInventory() { return inventory; }
    }

    ExpeditionManager(KingGriefCore plugin) {
        this.plugin=plugin; this.placedOreKey=new NamespacedKey(plugin, "expedition_placed_ore");
    }
    void start() {
        autosave = new BukkitRunnable() { @Override public void run() { flush(); } };
        autosave.runTaskTimer(plugin, 1200L, 1200L);
    }
    void shutdown() {
        if (autosave != null) autosave.cancel();
        flush(); walking.clear(); lastClaim.clear(); markerChanges.clear(); moves.clear();
    }
    private void flush() { if (dirty && plugin.getStore().saveBuffered()) dirty=false; }
    private Periods periods() { return new Periods(LocalDate.now(clock.withZone(ZoneOffset.UTC))); }
    private static String base(UUID id) { return "expeditions." + id; }
    private static boolean resetPeriods(YamlConfiguration data, UUID id, Periods p) {
        String root=base(id), previousDay=data.getString(root+".day.date"), previousWeek=data.getString(root+".week.date");
        if ((previousDay != null && previousDay.compareTo(p.day) > 0) || (previousWeek != null && previousWeek.compareTo(p.week) > 0)) return false;
        if (!p.day.equals(previousDay)) { data.set(root+".day", null); data.set(root+".day.date", p.day); }
        if (!p.week.equals(previousWeek)) { data.set(root+".week", null); data.set(root+".week.date", p.week); }
        return true;
    }
    /** A legacy day's basic progress is credited once toward its new expert goal. */
    private static int progress(YamlConfiguration data,String root,Task task) {
        String path=root+".day.progress."+task.id;
        int fallback=task.expert()?data.getInt(root+".day.progress."+task.basic().id,0):0;
        return Math.max(0,Math.min(task.goal,data.getInt(path,fallback)));
    }
    private boolean allowed(Player p) {
        return p != null && p.isOnline() && !p.isDead() && p.getGameMode()==GameMode.SURVIVAL
                && plugin.getStore().isAuthenticated(p) && !plugin.getStore().isQuickFrozen(p)
                && !plugin.getModeration().isFrozenOrAscending(p);
    }
    private void advance(Player p, Task task, int amount) {
        if (!allowed(p) || amount <= 0 || task.expert()) return;
        final UUID id=p.getUniqueId(); final Periods now=periods();
        try {
            List<Task> completed=plugin.getStore().buffered(data -> {
                List<Task> out=new ArrayList<Task>();
                if (!resetPeriods(data,id,now)) return out;
                String root=base(id); Task expert=task.advanced();
                // Read both before updating the original counter, otherwise a legacy
                // fallback would count the first post-upgrade event twice.
                int oldBasic=progress(data,root,task), oldExpert=progress(data,root,expert);
                for (int i=0;i<2;i++) {
                    Task t=i==0?task:expert; int previous=i==0?oldBasic:oldExpert;
                    if (data.getBoolean(root+".day.claimed."+t.id,false)) continue;
                    int next=(int)Math.min((long)t.goal,(long)previous+amount);
                    if (next==previous) continue;
                    data.set(root+".day.progress."+t.id,next); dirty=true;
                    if (previous<t.goal && next==t.goal) out.add(t);
                }
                return out;
            });
            for(Task ready:completed) p.sendMessage(ColorUtil.c("&#80D8FF✦ Задание «"+ready.title+"&r&#80D8FF» готово! Награда: &f/expeditions"));
        } catch (Exception error) { plugin.getLogger().warning("[Expeditions] Progress update failed: "+error.getMessage()); }
    }
    private Snapshot snapshot(Player p) {
        final UUID id=p.getUniqueId(); final Periods now=periods();
        try {
            return plugin.getStore().buffered(data -> {
                Snapshot result=new Snapshot();
                if (!resetPeriods(data,id,now)) { result.clockError=true; return result; }
                String root=base(id); int i=0;
                for (Task t:Task.values()) { result.progress[i]=progress(data,root,t); result.claimed[i]=data.getBoolean(root+".day.claimed."+t.id,false); i++; }
                result.dailyBonus=data.getBoolean(root+".day.bonus",false);
                result.expertDailyBonus=data.getBoolean(root+".day.expertBonus",false);
                result.weekly=Math.max(0,Math.min(35,data.getInt(root+".week.claims",0)));
                result.expertWeekly=Math.max(0,Math.min(35,data.getInt(root+".week.expertClaims",0)));
                result.weeklyBonus=data.getBoolean(root+".week.bonus",false);
                result.expertWeeklyBonus=data.getBoolean(root+".week.expertBonus",false);
                dirty=true;
                return result;
            });
        } catch (Exception error) { plugin.getLogger().warning("[Expeditions] Read failed: "+error.getMessage()); Snapshot bad=new Snapshot(); bad.clockError=true; return bad; }
    }
    private ClaimResult claim(Player p, String id) throws Exception {
        final Periods now=periods(); final UUID uuid=p.getUniqueId();
        final String name=p.getName().toLowerCase(Locale.ROOT);
        Task selected=null; for (Task t:Task.values()) if(t.id.equals(id)) selected=t;
        final Task task=selected; final boolean all="all".equals(id);
        if (task==null && !all && !Arrays.asList("bonus","weekly","expert","expert_weekly").contains(id)) return new ClaimResult(Result.NOT_READY,0,0,0);
        return plugin.getStore().transact(data -> {
            if (!resetPeriods(data,uuid,now)) return new ClaimResult(Result.CLOCK_ERROR,0,0,0);
            String root=base(uuid); List<String> markers=new ArrayList<String>();
            int coins=0,snow=0,basicAdded=0,expertAdded=0;
            for(Task t:Task.values()) {
                if (!all && t!=task) continue;
                String marker=root+".day.claimed."+t.id;
                if(data.getBoolean(marker,false)) {
                    if(!all) return new ClaimResult(Result.ALREADY,0,0,0);
                    continue;
                }
                if(progress(data,root,t)<t.goal) continue;
                markers.add(marker); coins+=t.coins; snow+=t.snow;
                if(t.expert()) expertAdded++; else basicAdded++;
            }
            int weekly=Math.max(0,Math.min(35,data.getInt(root+".week.claims",0)));
            int expertWeekly=Math.max(0,Math.min(35,data.getInt(root+".week.expertClaims",0)));
            boolean basicComplete=true,expertComplete=true;
            for(Task t:Task.values()) {
                String marker=root+".day.claimed."+t.id;
                boolean done=data.getBoolean(marker,false) || markers.contains(marker);
                if(t.expert() && !done) expertComplete=false;
                if(!t.expert() && !done) basicComplete=false;
            }
            String[] bonusIds={"bonus","weekly","expert","expert_weekly"};
            String[] bonusMarkers={root+".day.bonus",root+".week.bonus",root+".day.expertBonus",root+".week.expertBonus"};
            boolean[] ready={basicComplete,weekly+basicAdded>=WEEKLY_GOAL,expertComplete,expertWeekly+expertAdded>=WEEKLY_GOAL};
            int[] bonusCoins={DAILY_COINS,WEEKLY_COINS,EXPERT_DAILY_COINS,EXPERT_WEEKLY_COINS};
            int[] bonusSnow={DAILY_SNOW,WEEKLY_SNOW,EXPERT_DAILY_SNOW,EXPERT_WEEKLY_SNOW};
            for(int i=0;i<bonusIds.length;i++) {
                if(!all && !bonusIds[i].equals(id)) continue;
                if(data.getBoolean(bonusMarkers[i],false)) {
                    if(!all) return new ClaimResult(Result.ALREADY,0,0,0);
                    continue;
                }
                if(ready[i]) { markers.add(bonusMarkers[i]); coins+=bonusCoins[i]; snow+=bonusSnow[i]; }
            }
            if(markers.isEmpty()) return new ClaimResult(Result.NOT_READY,0,0,0);
            String cp="players."+name+".coins", sp="players."+name+".snowflakes";
            double balance=data.getDouble(cp,plugin.getConfig().getDouble("starting-coins",2500.0));
            long flakes=data.getLong(sp,0L);
            if (!Double.isFinite(balance) || balance<0 || !Double.isFinite(balance+coins) || balance+coins<=balance || flakes<0 || flakes>Integer.MAX_VALUE-snow)
                throw new IllegalStateException("invalid player balance");
            data.set(cp,balance+coins); data.set(sp,(int)flakes+snow);
            for(String marker:markers) data.set(marker,true);
            if(basicAdded>0) data.set(root+".week.claims",Math.min(35,weekly+basicAdded));
            if(expertAdded>0) data.set(root+".week.expertClaims",Math.min(35,expertWeekly+expertAdded));
            return new ClaimResult(Result.CLAIMED,coins,snow,markers.size());
        });
    }
    private void tryClaim(Player p, String id) {
        if (!allowed(p)) { p.sendMessage(ColorUtil.c("&cЗадания доступны авторизованным игрокам в выживании без заморозки.")); return; }
        long now=System.currentTimeMillis(); Long previous=lastClaim.get(p.getUniqueId());
        if(previous!=null && now-previous.longValue()<500L) return;
        lastClaim.put(p.getUniqueId(),Long.valueOf(now));
        try {
            ClaimResult result=claim(p,id);
            if (result.result==Result.CLAIMED) {
                dirty=false; plugin.getHud().updatePlayerHud(p);
                p.sendMessage(ColorUtil.c("&aПолучено наград: &f"+result.count+"&a. Итого: +"+result.coins+"$ и +"+result.snow+" снежинок ❄."));
                ColorUtil.sound(p,"entity.player.levelup",.8f,1.3f);
            } else if (result.result==Result.ALREADY) p.sendMessage(ColorUtil.c("&eЭта награда уже получена."));
            else if (result.result==Result.CLOCK_ERROR) p.sendMessage(ColorUtil.c("&cЧасы сервера отстают от сохранённого периода. Сообщите администрации."));
            else p.sendMessage(ColorUtil.c("&eГотовых неполученных наград пока нет."));
        } catch (Exception error) {
            plugin.getLogger().severe("[Expeditions] Claim failed without commit: "+error.getMessage());
            p.sendMessage(ColorUtil.c("&cНе удалось безопасно сохранить награду. Она не выдана; сообщите администрации."));
        }
    }
    private void status(Player p) {
        if(!allowed(p)) { p.sendMessage(ColorUtil.c("&cДля экспедиций войдите и используйте режим выживания.")); return; }
        Snapshot s=snapshot(p);
        if(s.clockError) { p.sendMessage(ColorUtil.c("&cЧасы сервера отстают от сохранённого периода.")); return; }
        p.sendMessage(ColorUtil.c("&#80D8FF&lЭкспедиции &7— обновление в 00:00 UTC"));
        for(Task t:Task.values()) {
            int i=t.ordinal(); p.sendMessage(ColorUtil.c("&7"+t.title+": &f"+s.progress[i]+"/"+t.goal+(s.claimed[i]?" &a✓":"")));
        }
        p.sendMessage(ColorUtil.c("&7Неделя: &f"+Math.min(s.weekly,WEEKLY_GOAL)+"/"+WEEKLY_GOAL+" &8| &7Мастер: &f"+Math.min(s.expertWeekly,WEEKLY_GOAL)+"/"+WEEKLY_GOAL));
        p.sendMessage(ColorUtil.c("&7Все готовые награды: &f/expeditions claim all"));
    }
    public void open(Player p) { open(p,0); }
    private void open(Player p,int page) {
        if (!allowed(p)) { p.sendMessage(ColorUtil.c("&cДля экспедиций войдите и используйте режим выживания.")); return; }
        Snapshot snap=snapshot(p);
        if (snap.clockError) { p.sendMessage(ColorUtil.c("&cЧасы сервера отстают от сохранённого периода заданий.")); return; }
        page=page==1?1:0;
        ExpeditionHolder holder=new ExpeditionHolder(p.getUniqueId(),page);
        Inventory inv=Bukkit.createInventory(holder,27,ColorUtil.c("&#80D8FF&l✦ ЭКСПЕДИЦИИ " + (page+1) + "/2")); holder.inventory=inv;
        ItemStack frame=ItemFactory.icon(Material.LIGHT_BLUE_STAINED_GLASS_PANE," ");
        for(int i=0;i<27;i++) inv.setItem(i,frame);
        inv.setItem(4,ItemFactory.glowIcon(Material.WRITABLE_BOOK,page==0?"&#80D8FF&lОБЫЧНЫЕ ЗАДАНИЯ":"&#FFD740&lЗАДАНИЯ МАСТЕРА",
                "&7Обновление в &f00:00 UTC&7, неделя — в понедельник.","&7Добыча в выживании и /warp mine; обычная ходьба.","&7Нажмите на готовое задание, чтобы забрать награду."));
        for(int i=0;i<5;i++) {
            Task t=Task.values()[i+page*5]; int n=t.ordinal();
            String state=snap.claimed[n]?"&aПолучено":snap.progress[n]>=t.goal?"&eНажмите: забрать":"&7В процессе";
            inv.setItem(SLOTS[i],ItemFactory.icon(t.icon,"&#FFD740&l"+t.title,"&7"+t.description,
                    "&7Прогресс: &f"+snap.progress[n]+" / "+t.goal,"&7Награда: &a"+t.coins+"$ &7+ &b"+t.snow+" ❄",state));
        }
        int dc=page==0?DAILY_COINS:EXPERT_DAILY_COINS, ds=page==0?DAILY_SNOW:EXPERT_DAILY_SNOW;
        int wc=page==0?WEEKLY_COINS:EXPERT_WEEKLY_COINS, ws=page==0?WEEKLY_SNOW:EXPERT_WEEKLY_SNOW;
        boolean daily=page==0?snap.dailyBonus:snap.expertDailyBonus, weekly=page==0?snap.weeklyBonus:snap.expertWeeklyBonus;
        int week=page==0?snap.weekly:snap.expertWeekly;
        inv.setItem(20,ItemFactory.glowIcon(Material.CHEST,page==0?"&#FFD740&lБОНУС ДНЯ":"&#FFD740&lБОНУС МАСТЕРА",
                "&7Заберите все пять наград на этой странице.","&7Награда: &a"+dc+"$ &7+ &b"+ds+" ❄",daily?"&aПолучено":"&eНажмите для получения"));
        inv.setItem(22,ItemFactory.glowIcon(Material.HOPPER,"&#00E676&lЗАБРАТЬ ВСЁ","&7Все готовые награды и бонусы обеих страниц.","&7Одна безопасная запись в data.yml."));
        inv.setItem(24,ItemFactory.glowIcon(Material.NETHER_STAR,page==0?"&#E040FB&lНЕДЕЛЬНЫЙ БОНУС":"&#E040FB&lНЕДЕЛЯ МАСТЕРА",
                "&7Получите 10 наград этой страницы за неделю.","&7Прогресс: &f"+Math.min(week,WEEKLY_GOAL)+" / "+WEEKLY_GOAL,
                "&7Награда: &a"+wc+"$ &7+ &b"+ws+" ❄",weekly?"&aПолучено":"&eНажмите для получения"));
        inv.setItem(page==0?25:18,ItemFactory.icon(Material.ARROW,page==0?"&eЗадания мастера →":"&e← Обычные задания"));
        inv.setItem(26,ItemFactory.icon(Material.BARRIER,"&7Закрыть"));
        p.openInventory(inv);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void onInventory(InventoryClickEvent e) {
        Inventory top=e.getView().getTopInventory();
        if (!(top.getHolder() instanceof ExpeditionHolder)) return;
        boolean cancelledBefore=e.isCancelled(); e.setCancelled(true);
        if (cancelledBefore || !(e.getWhoClicked() instanceof Player) || !top.equals(e.getClickedInventory())) return;
        if(e.getClick()!=ClickType.LEFT && e.getClick()!=ClickType.RIGHT) return;
        if(e.getCursor()!=null && !e.getCursor().getType().isAir()) return;
        Player p=(Player)e.getWhoClicked(); ExpeditionHolder holder=(ExpeditionHolder)top.getHolder();
        if (!holder.owner.equals(p.getUniqueId()) || !allowed(p)) return;
        int slot=e.getRawSlot();
        if(slot==26) { p.closeInventory(); return; }
        if((holder.page==0 && slot==25) || (holder.page==1 && slot==18)) {
            Bukkit.getScheduler().runTask(plugin,()->{ if(allowed(p) && p.getOpenInventory().getTopInventory().getHolder()==holder) open(p,1-holder.page); });
            return;
        }
        String id=slot==20?(holder.page==0?"bonus":"expert"):slot==24?(holder.page==0?"weekly":"expert_weekly"):slot==22?"all":null;
        for(int i=0;i<SLOTS.length;i++) if(slot==SLOTS[i]) id=Task.values()[i+holder.page*5].id;
        if(id==null) return;
        final String chosen=id;
        Bukkit.getScheduler().runTask(plugin,()->{
            if(allowed(p) && p.getOpenInventory().getTopInventory().getHolder()==holder) { tryClaim(p,chosen); open(p,holder.page); }
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) { if(e.getView().getTopInventory().getHolder() instanceof ExpeditionHolder) e.setCancelled(true); }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage("Эта команда доступна игроку."); return true; }
        Player p=(Player)sender;
        if(args.length==0) open(p);
        else if(args.length==1 && "status".equalsIgnoreCase(args[0])) status(p);
        else if("claim".equalsIgnoreCase(args[0]) && args.length<=2) tryClaim(p,args.length==1?"all":args[1].toLowerCase(Locale.ROOT));
        else if(args.length==2 && "page".equalsIgnoreCase(args[0]) && ("1".equals(args[1]) || "2".equals(args[1]))) open(p,"2".equals(args[1])?1:0);
        else p.sendMessage(ColorUtil.c("&e/expeditions [status|page 1/2|claim [all/название]]"));
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        List<String> out=new ArrayList<String>();
        if(args.length==1) { for(String name:Arrays.asList("status","claim","page")) if(name.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(name); }
        else if(args.length==2 && "claim".equalsIgnoreCase(args[0])) {
            List<String> names=new ArrayList<String>(); for(Task t:Task.values()) names.add(t.id);
            names.addAll(Arrays.asList("all","bonus","weekly","expert","expert_weekly"));
            for(String name:names) if(name.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(name);
        } else if(args.length==2 && "page".equalsIgnoreCase(args[0])) for(String name:Arrays.asList("1","2")) if(name.startsWith(args[1])) out.add(name);
        return out;
    }

    private static boolean ore(Material m) { return m.name().endsWith("_ORE") || m==Material.ANCIENT_DEBRIS; }
    private boolean isSpawn(Block b) { return plugin.getSpawnEngine().isInSpawnWorld(b.getLocation()); }
    private static boolean loaded(Block b) { return b.getWorld().isChunkLoaded(b.getX()>>4,b.getZ()>>4); }
    private static final class BlockKey {
        final UUID world; final int x,y,z;
        BlockKey(Block b) { world=b.getWorld().getUID(); x=b.getX(); y=b.getY(); z=b.getZ(); }
        @Override public int hashCode() { return (((world.hashCode()*31+x)*31+y)*31+z); }
        @Override public boolean equals(Object other) {
            if(!(other instanceof BlockKey)) return false; BlockKey k=(BlockKey)other;
            return x==k.x && y==k.y && z==k.z && world.equals(k.world);
        }
    }
    private Object touch(Block b) { Object token=new Object(); markerChanges.put(new BlockKey(b),token); return token; }
    private boolean finish(Block b,Object token) {
        BlockKey key=new BlockKey(b);
        if(markerChanges.get(key)!=token) return false;
        markerChanges.remove(key); return true;
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onPlace(BlockPlaceEvent e) {
        Block b=e.getBlockPlaced(); if(!loaded(b)) return;
        Material placed=e.getBlockPlaced().getType(); boolean before=markedOre(b);
        if(!ore(placed) && !before) return;
        Object token=touch(b);
        // Mark immediately to prevent place+break in the same tick. Removal is
        // deferred until cancellation and the resulting block are known.
        if(ore(placed)) markOre(b,true);
        Bukkit.getScheduler().runTask(plugin,()->{
            if(!finish(b,token) || !loaded(b)) return;
            if(e.isCancelled()) markOre(b,before);
            else if(b.getType()==placed) markOre(b,ore(placed));
        });
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onBreak(BlockBreakEvent e) {
        Block b=e.getBlock(); if(!loaded(b)) return;
        Material type=b.getType(); boolean placed=ore(type) && markedOre(b);
        boolean eligible=allowed(e.getPlayer());
        boolean oreTask=eligible && ore(type) && !placed && (!isSpawn(b) || plugin.getSpawnEngine().isInsideAutoMine(b.getLocation()));
        boolean harvest=false;
        if(eligible && !isSpawn(b) && (type==Material.WHEAT || type==Material.CARROTS || type==Material.POTATOES
                || type==Material.BEETROOTS || type==Material.NETHER_WART) && b.getBlockData() instanceof Ageable) {
            Ageable age=(Ageable)b.getBlockData(); harvest=age.getAge()==age.getMaximumAge();
        }
        if(!placed && !oreTask && !harvest) return;
        final boolean harvestTask=harvest; final Object token=placed?touch(b):null;
        Bukkit.getScheduler().runTask(plugin,()->{
            boolean current=token==null || finish(b,token);
            if(!loaded(b) || e.isCancelled() || !b.getType().isAir()) return;
            if(placed && current) markOre(b,false);
            if(oreTask && !markedOre(b)) advance(e.getPlayer(),Task.ORE,1);
            if(harvestTask) advance(e.getPlayer(),Task.HARVEST,1);
        });
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onMonster(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Monster)) return;
        Player p=e.getEntity().getKiller(); if(!allowed(p)) return;
        if(plugin.getSpawnEngine().isInSpawnWorld(e.getEntity().getLocation()) && !e.getEntity().getScoreboardTags().contains("kg_farm_mob")) return;
        Bukkit.getScheduler().runTask(plugin,()->{ if(!e.isCancelled() && e.getEntity().isDead()) advance(p,Task.HUNT,1); });
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onFish(PlayerFishEvent e) {
        if(e.getState()!=PlayerFishEvent.State.CAUGHT_FISH || !(e.getCaught() instanceof Item) || !allowed(e.getPlayer())) return;
        Material type=((Item)e.getCaught()).getItemStack().getType();
        if(type==Material.COD || type==Material.SALMON || type==Material.TROPICAL_FISH || type==Material.PUFFERFISH)
            Bukkit.getScheduler().runTask(plugin,()->{ if(!e.isCancelled()) advance(e.getPlayer(),Task.FISH,1); });
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onWalk(PlayerMoveEvent e) {
        if(e instanceof PlayerTeleportEvent || e.getTo()==null || !allowed(e.getPlayer())) return;
        Location a=e.getFrom(),b=e.getTo();
        if(!a.getWorld().equals(b.getWorld()) || (a.getX()==b.getX() && a.getZ()==b.getZ())) return;
        // A single next-tick task rechecks final cancellation and batches the
        // resulting whole blocks per player; never schedule one task per packet.
        if(moves.size()>=10000) return;
        moves.add(e);
        if(!moveScheduled) { moveScheduled=true; Bukkit.getScheduler().runTask(plugin,this::processMoves); }
    }
    private void processMoves() {
        moveScheduled=false; List<PlayerMoveEvent> pending=new ArrayList<PlayerMoveEvent>(moves); moves.clear();
        Map<UUID,Integer> totals=new HashMap<UUID,Integer>(); Map<UUID,Player> players=new HashMap<UUID,Player>();
        String day=periods().day;
        for(PlayerMoveEvent e:pending) {
            Player p=e.getPlayer(); Location a=e.getFrom(),b=e.getTo();
            if(e.isCancelled() || b==null || !allowed(p) || !a.getWorld().equals(b.getWorld()) || p.isFlying() || p.isGliding() || p.isInsideVehicle()) continue;
            double dx=b.getX()-a.getX(), dz=b.getZ()-a.getZ(), dy=b.getY()-a.getY(), distance=Math.sqrt(dx*dx+dz*dz);
            if(!Double.isFinite(distance) || distance<=0.0001 || distance>1.25 || Math.abs(dy)>1.25) continue;
            UUID id=p.getUniqueId(); Walk w=walking.get(id);
            if(w==null || !w.day.equals(day)) { w=new Walk(day); walking.put(id,w); }
            w.fraction+=distance; int whole=(int)w.fraction;
            if(whole>0) { w.fraction-=whole; totals.put(id,Math.min(1200,totals.getOrDefault(id,0)+whole)); players.put(id,p); }
        }
        for(Map.Entry<UUID,Integer> entry:totals.entrySet()) advance(players.get(entry.getKey()),Task.WALK,entry.getValue());
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) { walking.remove(e.getPlayer().getUniqueId()); lastClaim.remove(e.getPlayer().getUniqueId()); flush(); }

    // Chunk PDC is bounded by chunk height and survives ordinary saves. It is
    // not a journal shared atomically with player data or arbitrary plugins.
    private int oreIndex(Block b) {
        int y=b.getY()-b.getWorld().getMinHeight();
        if(y<0 || y>=b.getWorld().getMaxHeight()-b.getWorld().getMinHeight()) return -1;
        return y*256+(b.getZ()&15)*16+(b.getX()&15);
    }
    private boolean markedOre(Block b) {
        int index=oreIndex(b); if(index<0) return false;
        byte[] bytes=b.getChunk().getPersistentDataContainer().get(placedOreKey,PersistentDataType.BYTE_ARRAY);
        return bytes!=null && BitSet.valueOf(bytes).get(index);
    }
    private void markOre(Block b,boolean marked) {
        int index=oreIndex(b); if(index<0) return;
        Chunk c=b.getChunk(); PersistentDataContainer pdc=c.getPersistentDataContainer();
        byte[] bytes=pdc.get(placedOreKey,PersistentDataType.BYTE_ARRAY);
        BitSet bits=bytes==null?new BitSet():BitSet.valueOf(bytes);
        if(marked) bits.set(index); else bits.clear(index);
        if(bits.isEmpty()) pdc.remove(placedOreKey); else pdc.set(placedOreKey,PersistentDataType.BYTE_ARRAY,bits.toByteArray());
    }
    private void clearDestroyed(List<Block> blocks,java.util.function.BooleanSupplier cancelled,java.util.function.Predicate<Block> stillListed) {
        for(Block b:new ArrayList<Block>(blocks)) {
            if(!loaded(b) || !ore(b.getType()) || !markedOre(b)) continue;
            Object token=touch(b);
            Bukkit.getScheduler().runTask(plugin,()->{
                if(finish(b,token) && loaded(b) && !cancelled.getAsBoolean() && stillListed.test(b) && b.getType().isAir()) markOre(b,false);
            });
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onExplode(EntityExplodeEvent e) { clearDestroyed(e.blockList(),e::isCancelled,b->e.blockList().contains(b)); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onBlockExplode(BlockExplodeEvent e) { clearDestroyed(e.blockList(),e::isCancelled,b->e.blockList().contains(b)); }
    private static final class Move {
        final Block from,to; final Material type; final boolean marked;
        Move(Block from,Block to,boolean marked) { this.from=from; this.to=to; this.type=from.getType(); this.marked=marked; }
    }
    private void moveMarks(List<Block> moved,BlockFace direction,java.util.function.BooleanSupplier cancelled) {
        List<Move> changes=new ArrayList<Move>(); Map<BlockKey,Block> blocks=new LinkedHashMap<BlockKey,Block>();
        Map<BlockKey,Boolean> before=new HashMap<BlockKey,Boolean>(),after=new HashMap<BlockKey,Boolean>();
        for(Block b:moved) {
            Block to=b.getRelative(direction); if(!loaded(b) || !loaded(to)) return;
            boolean marked=ore(b.getType()) && markedOre(b); changes.add(new Move(b,to,marked));
            blocks.put(new BlockKey(b),b); blocks.put(new BlockKey(to),to);
        }
        if(changes.isEmpty()) return;
        for(Map.Entry<BlockKey,Block> entry:blocks.entrySet()) before.put(entry.getKey(),markedOre(entry.getValue()));
        for(Move m:changes) after.put(new BlockKey(m.from),false);
        for(Move m:changes) after.put(new BlockKey(m.to),m.marked);
        Map<BlockKey,Object> tokens=new HashMap<BlockKey,Object>();
        for(Map.Entry<BlockKey,Block> entry:blocks.entrySet()) {
            BlockKey key=entry.getKey(); Block b=entry.getValue(); tokens.put(key,touch(b));
            if(Boolean.TRUE.equals(after.get(key))) markOre(b,true); // conservative until actual movement is confirmed
        }
        // Vanilla pistons temporarily expose MOVING_PISTON for their animation.
        // Wait three ticks before verifying the resulting blocks; the extra
        // destination marks remain conservative throughout that window.
        Bukkit.getScheduler().runTaskLater(plugin,()->{
            boolean success=!cancelled.getAsBoolean(); Set<BlockKey> destinations=new HashSet<BlockKey>();
            for(Move m:changes) destinations.add(new BlockKey(m.to));
            if(success) for(Move m:changes) {
                if(!loaded(m.to) || m.to.getType()!=m.type || (!destinations.contains(new BlockKey(m.from))
                        && (!loaded(m.from) || m.from.getType()==m.type))) { success=false; break; }
            }
            for(Map.Entry<BlockKey,Block> entry:blocks.entrySet()) {
                BlockKey key=entry.getKey(); Block b=entry.getValue();
                if(!finish(b,tokens.get(key)) || !loaded(b)) continue;
                boolean old=Boolean.TRUE.equals(before.get(key)), desired=Boolean.TRUE.equals(after.get(key));
                // An ambiguous or unloaded result is conservative: never erase
                // a possible mark merely because another plugin changed blocks.
                markOre(b,cancelled.getAsBoolean()?old:success?desired:old||desired);
            }
        },3L);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onPiston(BlockPistonExtendEvent e) { moveMarks(e.getBlocks(),e.getDirection(),e::isCancelled); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onPiston(BlockPistonRetractEvent e) { moveMarks(e.getBlocks(),e.getDirection(),e::isCancelled); }
}
