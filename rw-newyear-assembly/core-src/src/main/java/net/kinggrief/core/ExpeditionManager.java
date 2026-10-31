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
import org.bukkit.entity.Entity;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Five daily objectives, a daily completion reward and a weekly reward.
 * Progress is buffered and periodically saved; claims commit both currency and
 * their claim markers in one durable DataStore transaction. All Bukkit access
 * is on the main thread. The ore marker blocks ordinary place-and-mine loops,
 * but cannot detect historical ore or edits that bypass Bukkit events. */
public final class ExpeditionManager implements Listener, CommandExecutor, TabCompleter {
    private enum Task {
        ORE("ore", "⛏ Шахтёр", "Добудьте 32 руды или древних обломка", Material.DIAMOND_ORE, 32, 3500, 3),
        HARVEST("harvest", "🌾 Земледелец", "Соберите 20 полностью созревших культур", Material.WHEAT, 20, 3000, 2),
        HUNT("hunt", "⚔ Охотник", "Победите 12 враждебных монстров", Material.IRON_SWORD, 12, 4000, 3),
        FISH("fish", "🎣 Рыбак", "Поймайте 3 рыбы удочкой", Material.FISHING_ROD, 3, 3000, 2),
        WALK("walk", "🧭 Путешественник", "Пройдите 600 блоков пешком", Material.COMPASS, 600, 4000, 3);
        final String id, title, description; final Material icon; final int goal, coins, snow;
        Task(String id, String title, String description, Material icon, int goal, int coins, int snow) {
            this.id=id; this.title=title; this.description=description; this.icon=icon; this.goal=goal; this.coins=coins; this.snow=snow;
        }
    }
    private static final int DAILY_COINS=10000, DAILY_SNOW=5, WEEKLY_GOAL=10, WEEKLY_COINS=25000, WEEKLY_SNOW=10;
    private static final int[] SLOTS = {10, 11, 12, 13, 14};
    private final KingGriefCore plugin;
    private final NamespacedKey placedOreKey;
    private final Map<UUID, Walk> walking = new HashMap<UUID, Walk>();
    private final Map<UUID, Long> lastClaim = new HashMap<UUID, Long>();
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
        int weekly; boolean dailyBonus, weeklyBonus, clockError;
    }
    private enum Result { CLAIMED, NOT_READY, ALREADY, CLOCK_ERROR }
    private static final class ClaimResult {
        final Result result; final int coins, snow;
        ClaimResult(Result result, int coins, int snow) { this.result=result; this.coins=coins; this.snow=snow; }
    }
    public static final class ExpeditionHolder implements InventoryHolder {
        final UUID owner; Inventory inventory;
        ExpeditionHolder(UUID owner) { this.owner=owner; }
        @Override public Inventory getInventory() { return inventory; }
    }

    ExpeditionManager(KingGriefCore plugin) {
        this.plugin=plugin; this.placedOreKey=new NamespacedKey(plugin, "expedition_placed_ore");
    }
    void start() {
        autosave = new BukkitRunnable() {
            @Override public void run() { flush(); }
        };
        autosave.runTaskTimer(plugin, 1200L, 1200L);
    }
    void shutdown() {
        if (autosave != null) autosave.cancel();
        flush(); walking.clear(); lastClaim.clear();
    }
    private void flush() {
        if (dirty && plugin.getStore().saveBuffered()) dirty=false;
    }
    private Periods periods() { return new Periods(LocalDate.now(clock.withZone(ZoneOffset.UTC))); }
    private static String base(UUID id) { return "expeditions." + id; }
    private static boolean resetPeriods(YamlConfiguration data, UUID id, Periods p) {
        String root=base(id), previousDay=data.getString(root+".day.date"), previousWeek=data.getString(root+".week.date");
        // Never grant yesterday again if the machine clock moves backwards.
        if ((previousDay != null && previousDay.compareTo(p.day) > 0) || (previousWeek != null && previousWeek.compareTo(p.week) > 0)) return false;
        if (!p.day.equals(previousDay)) { data.set(root+".day", null); data.set(root+".day.date", p.day); }
        if (!p.week.equals(previousWeek)) { data.set(root+".week", null); data.set(root+".week.date", p.week); }
        return true;
    }
    private boolean allowed(Player p) {
        return p != null && p.isOnline() && !p.isDead() && p.getGameMode()==GameMode.SURVIVAL
                && plugin.getStore().isAuthenticated(p) && !plugin.getStore().isQuickFrozen(p)
                && !plugin.getModeration().isFrozenOrAscending(p);
    }
    private void advance(Player p, Task task, int amount) {
        if (!allowed(p) || amount <= 0) return;
        final UUID id=p.getUniqueId(); final Periods now=periods();
        try {
            Boolean completed=plugin.getStore().buffered(data -> {
                if (!resetPeriods(data,id,now)) return Boolean.FALSE;
                String root=base(id)+".day.";
                if (data.getBoolean(root+"claimed."+task.id, false)) return Boolean.FALSE;
                int previous=Math.max(0, data.getInt(root+"progress."+task.id,0));
                int next=(int)Math.min((long)task.goal, (long)previous+amount);
                if (next==previous) return Boolean.FALSE;
                data.set(root+"progress."+task.id,next); dirty=true;
                return Boolean.valueOf(previous<task.goal && next==task.goal);
            });
            if (completed.booleanValue()) p.sendMessage(ColorUtil.c("&#80D8FF✦ Задание «"+task.title+"&r&#80D8FF» готово! Награда: &f/expeditions"));
        } catch (Exception error) { plugin.getLogger().warning("[Expeditions] Progress update failed: "+error.getMessage()); }
    }
    private Snapshot snapshot(Player p) {
        final UUID id=p.getUniqueId(); final Periods now=periods();
        try {
            return plugin.getStore().buffered(data -> {
                Snapshot result=new Snapshot();
                if (!resetPeriods(data,id,now)) { result.clockError=true; return result; }
                // Period initialization is buffered and will be persisted with the next progress/claim.
                String root=base(id); int i=0;
                for (Task t:Task.values()) {
                    result.progress[i]=Math.max(0,Math.min(t.goal,data.getInt(root+".day.progress."+t.id,0)));
                    result.claimed[i]=data.getBoolean(root+".day.claimed."+t.id,false); i++;
                }
                result.dailyBonus=data.getBoolean(root+".day.bonus",false);
                result.weekly=Math.max(0,Math.min(35,data.getInt(root+".week.claims",0)));
                result.weeklyBonus=data.getBoolean(root+".week.bonus",false);
                dirty=true;
                return result;
            });
        } catch (Exception error) { plugin.getLogger().warning("[Expeditions] Read failed: "+error.getMessage()); Snapshot bad=new Snapshot(); bad.clockError=true; return bad; }
    }
    private ClaimResult claim(Player p, String id) throws Exception {
        final Periods now=periods(); final UUID uuid=p.getUniqueId();
        final String name=p.getName().toLowerCase(Locale.ROOT);
        Task selected=null; for (Task t:Task.values()) if(t.id.equals(id)) selected=t;
        final Task task=selected;
        if (task==null && !"bonus".equals(id) && !"weekly".equals(id)) return new ClaimResult(Result.NOT_READY,0,0);
        return plugin.getStore().transact(data -> {
            if (!resetPeriods(data,uuid,now)) return new ClaimResult(Result.CLOCK_ERROR,0,0);
            String root=base(uuid), marker; int coins, snow;
            if (task!=null) {
                marker=root+".day.claimed."+task.id; coins=task.coins; snow=task.snow;
                if (data.getBoolean(marker,false)) return new ClaimResult(Result.ALREADY,0,0);
                if (data.getInt(root+".day.progress."+task.id,0)<task.goal) return new ClaimResult(Result.NOT_READY,0,0);
            } else if ("bonus".equals(id)) {
                marker=root+".day.bonus"; coins=DAILY_COINS; snow=DAILY_SNOW;
                if (data.getBoolean(marker,false)) return new ClaimResult(Result.ALREADY,0,0);
                for(Task t:Task.values()) if (!data.getBoolean(root+".day.claimed."+t.id,false)) return new ClaimResult(Result.NOT_READY,0,0);
            } else {
                marker=root+".week.bonus"; coins=WEEKLY_COINS; snow=WEEKLY_SNOW;
                if (data.getBoolean(marker,false)) return new ClaimResult(Result.ALREADY,0,0);
                if (data.getInt(root+".week.claims",0)<WEEKLY_GOAL) return new ClaimResult(Result.NOT_READY,0,0);
            }
            String cp="players."+name+".coins", sp="players."+name+".snowflakes";
            double balance=data.getDouble(cp,plugin.getConfig().getDouble("starting-coins",2500.0));
            long flakes=data.getLong(sp,0L);
            if (!Double.isFinite(balance) || balance<0 || !Double.isFinite(balance+coins) || balance+coins<=balance || flakes<0 || flakes>Integer.MAX_VALUE-snow)
                throw new IllegalStateException("invalid player balance");
            data.set(cp,balance+coins); data.set(sp,(int)flakes+snow); data.set(marker,true);
            if (task!=null) data.set(root+".week.claims", Math.min(35,Math.max(0,data.getInt(root+".week.claims",0))+1));
            return new ClaimResult(Result.CLAIMED,coins,snow);
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
                p.sendMessage(ColorUtil.c("&aНаграда экспедиции: +"+result.coins+"$ и +"+result.snow+" снежинок ❄."));
                ColorUtil.sound(p,"entity.player.levelup",.8f,1.3f);
            } else if (result.result==Result.ALREADY) p.sendMessage(ColorUtil.c("&eЭта награда уже получена."));
            else if (result.result==Result.CLOCK_ERROR) p.sendMessage(ColorUtil.c("&cЧасы сервера отстают от сохранённого периода. Сообщите администрации."));
            else p.sendMessage(ColorUtil.c("&eЗадание или бонус ещё не выполнен."));
        } catch (Exception error) {
            plugin.getLogger().severe("[Expeditions] Claim failed without commit: "+error.getMessage());
            p.sendMessage(ColorUtil.c("&cНе удалось безопасно сохранить награду. Она не выдана; сообщите администрации."));
        }
    }

    public void open(Player p) {
        if (!allowed(p)) { p.sendMessage(ColorUtil.c("&cДля экспедиций войдите и используйте режим выживания.")); return; }
        Snapshot snap=snapshot(p);
        if (snap.clockError) { p.sendMessage(ColorUtil.c("&cЧасы сервера отстают от сохранённого периода заданий.")); return; }
        ExpeditionHolder holder=new ExpeditionHolder(p.getUniqueId());
        Inventory inv=Bukkit.createInventory(holder,27,ColorUtil.c("&#80D8FF&l✦ ЭКСПЕДИЦИИ KINGGRIEF")); holder.inventory=inv;
        ItemStack frame=ItemFactory.icon(Material.LIGHT_BLUE_STAINED_GLASS_PANE," ");
        for(int i=0;i<27;i++) inv.setItem(i,frame);
        inv.setItem(4,ItemFactory.glowIcon(Material.WRITABLE_BOOK,"&#80D8FF&lЕЖЕДНЕВНЫЕ ЭКСПЕДИЦИИ",
                "&7Обновление в &f00:00 UTC&7, неделя — в понедельник.","&7Добыча в выживании и /warp mine; обычная ходьба.","&7Нажмите на готовое задание, чтобы забрать награду."));
        int i=0;
        for(Task t:Task.values()) {
            String state=snap.claimed[i]?"&aПолучено":snap.progress[i]>=t.goal?"&eНажмите: забрать":"&7В процессе";
            inv.setItem(SLOTS[i],ItemFactory.icon(t.icon,"&#FFD740&l"+t.title,"&7"+t.description,
                    "&7Прогресс: &f"+snap.progress[i]+" / "+t.goal,"&7Награда: &a"+t.coins+"$ &7+ &b"+t.snow+" ❄",state)); i++;
        }
        inv.setItem(20,ItemFactory.glowIcon(Material.CHEST,"&#FFD740&lБОНУС ДНЯ","&7Заберите все пять ежедневных наград.",
                "&7Награда: &a"+DAILY_COINS+"$ &7+ &b"+DAILY_SNOW+" ❄",snap.dailyBonus?"&aПолучено":"&eНажмите для получения"));
        inv.setItem(24,ItemFactory.glowIcon(Material.NETHER_STAR,"&#E040FB&lНЕДЕЛЬНЫЙ БОНУС","&7Получите 10 ежедневных наград за неделю.",
                "&7Прогресс: &f"+Math.min(snap.weekly,WEEKLY_GOAL)+" / "+WEEKLY_GOAL,
                "&7Награда: &a"+WEEKLY_COINS+"$ &7+ &b"+WEEKLY_SNOW+" ❄",snap.weeklyBonus?"&aПолучено":"&eНажмите для получения"));
        inv.setItem(26,ItemFactory.icon(Material.BARRIER,"&7Закрыть"));
        p.openInventory(inv);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void onInventory(InventoryClickEvent e) {
        Inventory top=e.getView().getTopInventory();
        if (!(top.getHolder() instanceof ExpeditionHolder)) return;
        boolean cancelledBefore = e.isCancelled();
        e.setCancelled(true);
        if (cancelledBefore || !(e.getWhoClicked() instanceof Player) || !top.equals(e.getClickedInventory())) return;
        Player p=(Player)e.getWhoClicked(); ExpeditionHolder holder=(ExpeditionHolder)top.getHolder();
        if (!holder.owner.equals(p.getUniqueId()) || !allowed(p)) return;
        int slot=e.getRawSlot();
        if(slot==26) { p.closeInventory(); return; }
        String id=slot==20?"bonus":slot==24?"weekly":null;
        for(int i=0;i<SLOTS.length;i++) if(slot==SLOTS[i]) id=Task.values()[i].id;
        if(id==null) return;
        final String chosen=id;
        // Opening another inventory inside InventoryClickEvent is unsafe on Bukkit.
        Bukkit.getScheduler().runTask(plugin,()->{
            if(allowed(p) && p.getOpenInventory().getTopInventory().getHolder()==holder) { tryClaim(p,chosen); open(p); }
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) { if(e.getView().getTopInventory().getHolder() instanceof ExpeditionHolder) e.setCancelled(true); }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage("Эта команда доступна игроку."); return true; }
        Player p=(Player)sender;
        if(args.length>=2 && "claim".equalsIgnoreCase(args[0])) tryClaim(p,args[1].toLowerCase(Locale.ROOT));
        else open(p);
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        List<String> out=new ArrayList<String>();
        if(args.length==1 && "claim".startsWith(args[0].toLowerCase(Locale.ROOT))) out.add("claim");
        else if(args.length==2 && "claim".equalsIgnoreCase(args[0])) {
            List<String> names=new ArrayList<String>(); for(Task t:Task.values()) names.add(t.id); names.add("bonus"); names.add("weekly");
            for(String name:names) if(name.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(name);
        }
        return out;
    }

    private static boolean ore(Material m) { return m.name().endsWith("_ORE") || m==Material.ANCIENT_DEBRIS; }
    private boolean isSpawn(Block b) { return plugin.getSpawnEngine().isInSpawnWorld(b.getLocation()); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onPlace(BlockPlaceEvent e) { if(ore(e.getBlockPlaced().getType())) markOre(e.getBlockPlaced(),true); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onBreak(BlockBreakEvent e) {
        Block b=e.getBlock(); Material type=b.getType(); boolean placed=ore(type) && markedOre(b);
        if(placed) markOre(b,false);
        if(!allowed(e.getPlayer())) return;
        if(ore(type) && !placed && (!isSpawn(b) || plugin.getSpawnEngine().isInsideAutoMine(b.getLocation()))) advance(e.getPlayer(),Task.ORE,1);
        if(!isSpawn(b) && (type==Material.WHEAT || type==Material.CARROTS || type==Material.POTATOES
                || type==Material.BEETROOTS || type==Material.NETHER_WART) && b.getBlockData() instanceof Ageable) {
            Ageable age=(Ageable)b.getBlockData(); if(age.getAge()==age.getMaximumAge()) advance(e.getPlayer(),Task.HARVEST,1);
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onMonster(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Monster)) return;
        Player p=e.getEntity().getKiller(); if(!allowed(p)) return;
        if(plugin.getSpawnEngine().isInSpawnWorld(e.getEntity().getLocation()) && !e.getEntity().getScoreboardTags().contains("kg_farm_mob")) return;
        advance(p,Task.HUNT,1);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onFish(PlayerFishEvent e) {
        if(e.getState()!=PlayerFishEvent.State.CAUGHT_FISH || !(e.getCaught() instanceof Item) || !allowed(e.getPlayer())) return;
        Material type=((Item)e.getCaught()).getItemStack().getType();
        if(type==Material.COD || type==Material.SALMON || type==Material.TROPICAL_FISH || type==Material.PUFFERFISH) advance(e.getPlayer(),Task.FISH,1);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onWalk(PlayerMoveEvent e) {
        if(e instanceof PlayerTeleportEvent || e.getTo()==null || !allowed(e.getPlayer())) return;
        Player p=e.getPlayer(); Location a=e.getFrom(),b=e.getTo();
        if(!a.getWorld().equals(b.getWorld()) || p.isFlying() || p.isGliding() || p.isInsideVehicle()) return;
        double dx=b.getX()-a.getX(), dz=b.getZ()-a.getZ(), dy=b.getY()-a.getY();
        double distance=Math.sqrt(dx*dx+dz*dz);
        // Ignore look-only packets, teleport-like leaps and large vertical jumps.
        if(distance<=0.0001 || distance>1.25 || Math.abs(dy)>1.25) return;
        String day=periods().day; Walk w=walking.get(p.getUniqueId());
        if(w==null || !w.day.equals(day)) { w=new Walk(day); walking.put(p.getUniqueId(),w); }
        w.fraction+=distance; int whole=(int)w.fraction;
        if(whole>0) { w.fraction-=whole; advance(p,Task.WALK,whole); }
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) { walking.remove(e.getPlayer().getUniqueId()); lastClaim.remove(e.getPlayer().getUniqueId()); flush(); }

    // The fixed-size bitset is stored in the chunk rather than in a global
    // map; it survives normal restarts without loading any neighbouring chunks.
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
    private void clearDestroyed(List<Block> blocks) { for(Block b:blocks) if(ore(b.getType()) && markedOre(b)) markOre(b,false); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onExplode(EntityExplodeEvent e) { clearDestroyed(e.blockList()); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onBlockExplode(BlockExplodeEvent e) { clearDestroyed(e.blockList()); }
    private void moveMarks(List<Block> moved,BlockFace direction) {
        List<Block> marked=new ArrayList<Block>();
        for(Block b:moved) if(ore(b.getType()) && markedOre(b)) marked.add(b);
        for(Block b:marked) markOre(b,false);
        for(Block b:marked) markOre(b.getRelative(direction),true);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onPiston(BlockPistonExtendEvent e) { moveMarks(e.getBlocks(),e.getDirection()); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onPiston(BlockPistonRetractEvent e) { moveMarks(e.getBlocks(),e.getDirection().getOppositeFace()); }
}
