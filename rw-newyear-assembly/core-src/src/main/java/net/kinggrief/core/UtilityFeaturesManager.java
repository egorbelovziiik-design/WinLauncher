package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Non-destructive quality-of-life tools; no command loads chunks or edits world blocks. */
final class UtilityFeaturesManager implements Listener {
    private static final String[] WARPS={"spawn","pvp","kabinki","train","mine","farm","casino","wheel","bafs","parkour","cases","top"};
    private static final class PendingOre {
        final BlockBreakEvent event; final Material material; final UUID player;
        PendingOre(BlockBreakEvent event){this.event=event;material=event.getBlock().getType();player=event.getPlayer().getUniqueId();}
    }
    private static final class Beam {
        final Location location; final long until;
        Beam(Location location,long until){this.location=location;this.until=until;}
    }
    private final KingGriefCore plugin;
    private final List<PendingOre> pending=new ArrayList<PendingOre>();
    private final Map<UUID,Beam> beams=new HashMap<UUID,Beam>();
    private BukkitRunnable ticker;
    private boolean miningScheduled, dirty;
    private int tick;
    UtilityFeaturesManager(KingGriefCore plugin){this.plugin=plugin;}
    void start(){
        ticker=new BukkitRunnable(){@Override public void run(){
            tick++;
            showBeams();
            if(tick%60==0&&dirty)flush();
        }};ticker.runTaskTimer(plugin,20L,20L);
    }
    void shutdown(){if(ticker!=null)ticker.cancel();processPending();beams.clear();if(dirty)flush();}
    @EventHandler public void quit(PlayerQuitEvent e){beams.remove(e.getPlayer().getUniqueId());}

    void status(Player p){
        SpawnEngine spawn=plugin.getSpawnEngine();
        p.sendMessage(ColorUtil.c("&#80D8FF✦ Состояние сервера &8| &fигроков: "+Bukkit.getOnlinePlayers().size()+" &8| &fTPS: "+String.format(Locale.ROOT,"%.1f",spawn.getCurrentTps())+"/20"));
        p.sendMessage(ColorUtil.c("&7Активных встроенных ивентов: &f"+plugin.getBossEvents().getActiveEvents().size()+" &8| &7Спавн: &f/kgspawn status &8| &7События: &f/calendar"));
    }
    void spawnStatus(Player p){
        Location arrival=plugin.getSpawnEngine().getPreparedSpawnArrival();
        if(arrival==null||arrival.getWorld()==null){p.sendMessage(ColorUtil.c("&cГотовый спавн сейчас недоступен. Мир не загружается принудительно."));return;}
        World world=arrival.getWorld(); int defined=0,loaded=0;
        for(String name:WARPS){
            Location loc=plugin.getSpawnEngine().getWarpLocation(name);
            if(loc!=null&&loc.getWorld()!=null){defined++;if(loc.getWorld().isChunkLoaded(loc.getBlockX()>>4,loc.getBlockZ()>>4))loaded++;}
        }
        p.sendMessage(ColorUtil.c("&#80D8FFСпавн: &f"+world.getName()+" &8| &7точка: &f"+arrival.getBlockX()+", "+arrival.getBlockY()+", "+arrival.getBlockZ()));
        p.sendMessage(ColorUtil.c("&7Варпы заданы: &f"+defined+"/"+WARPS.length+" &8| &7чанки уже загружены: &f"+loaded+"&7. Это быстрая диагностика, не полная проверка построек."));
    }
    void calendar(Player p){
        BossEventManager events=plugin.getBossEvents();
        p.sendMessage(ColorUtil.c("&#FFD740✦ Календарь встроенных событий:"));
        if(events.isAutoEnabled())p.sendMessage(ColorUtil.c("&7Следующая попытка автоивента примерно через &f"+ColorUtil.fmtTime(Math.max(0,events.getNextAutoSeconds()))+"&7; поиск площадки может перенестись."));
        else p.sendMessage(ColorUtil.c("&7Автоматические ивенты выключены администрацией."));
        List<BossEventManager.ActiveBossEvent> active=events.getActiveEvents();
        if(active.isEmpty())p.sendMessage(ColorUtil.c("&7Сейчас активных встроенных ивентов нет."));
        for(BossEventManager.ActiveBossEvent event:active){
            String phase=event.bossDefeated?"сундук открыт":"активен";
            p.sendMessage(ColorUtil.c(" &8• &f"+event.template.displayName+" &7("+phase+", ещё до "+ColorUtil.fmtTime(Math.max(0,event.secondsLeft))+")"));
        }
    }
    void borders(Player p){
        DataStore.Claim claim=plugin.getStore().getClaimAt(p.getLocation());
        if(claim==null){p.sendMessage(ColorUtil.c("&eВы стоите вне встроенного рудного привата."));return;}
        if(claim.radius<0||claim.radius>4096){p.sendMessage(ColorUtil.c("&cРазмер привата некорректен; сообщите администрации."));return;}
        String worldName=claim.world; int cx=claim.cx,cz=claim.cz,r=claim.radius;
        p.sendMessage(ColorUtil.c("&#00E676Границы привата показаны только вам на 8 секунд. Блоки не меняются."));
        new BukkitRunnable(){int left=8;
            @Override public void run(){
                if(!p.isOnline()||p.getWorld()==null||!p.getWorld().getName().equalsIgnoreCase(worldName)||left--<=0){cancel();return;}
                double y=p.getLocation().getY()+0.15;
                int steps=Math.max(1,Math.min(16,(2*r+1+2)/3));
                for(int i=0;i<=steps;i++){
                    double along=-r+(2.0*r+1.0)*i/steps;
                    p.spawnParticle(Particle.END_ROD,cx+along,y,cz-r,1,0,0,0,0);
                    p.spawnParticle(Particle.END_ROD,cx+along,y,cz+r+1,1,0,0,0,0);
                    p.spawnParticle(Particle.END_ROD,cx-r,y,cz+along,1,0,0,0,0);
                    p.spawnParticle(Particle.END_ROD,cx+r+1,y,cz+along,1,0,0,0,0);
                }
            }
        }.runTaskTimer(plugin,0L,20L);
    }
    void sort(Player p){
        if(p.getOpenInventory().getTopInventory().getType()!=InventoryType.CRAFTING||!empty(p.getItemOnCursor())){
            p.sendMessage(ColorUtil.c("&eЗакройте открытый контейнер и освободите курсор перед сортировкой."));return;
        }
        ItemStack[] contents=p.getInventory().getStorageContents();
        if(contents.length<36){p.sendMessage(ColorUtil.c("&cСортировка этого инвентаря недоступна."));return;}
        ItemStack[] bag=Arrays.copyOfRange(contents,9,36);
        Arrays.sort(bag,Comparator.nullsLast((x,y)->{
            if(empty(x)&&empty(y))return 0;if(empty(x))return 1;if(empty(y))return -1;
            int type=x.getType().name().compareTo(y.getType().name());
            if(type!=0)return type;
            return display(x).compareToIgnoreCase(display(y));
        }));
        System.arraycopy(bag,0,contents,9,bag.length);
        p.getInventory().setStorageContents(contents);
        p.sendMessage(ColorUtil.c("&#00E676Инвентарь отсортирован. Хотбар, броня и вторая рука не изменены; предметы не объединяются."));
    }
    void compare(Player p){
        ItemStack main=p.getInventory().getItemInMainHand(), off=p.getInventory().getItemInOffHand();
        if(empty(main)||empty(off)){p.sendMessage(ColorUtil.c("&eВозьмите два предмета в основную и вторую руку: /compare"));return;}
        p.sendMessage(ColorUtil.c("&#80D8FFСравнение: &f"+display(main)+" &8↔ &f"+display(off)));
        p.sendMessage(ColorUtil.c("&7Основная: &f"+details(main)));
        p.sendMessage(ColorUtil.c("&7Вторая рука: &f"+details(off)));
        p.sendMessage(ColorUtil.c("&8Сравнение показывает материал, прочность и чары, но не оценивает способности кастомных предметов."));
    }
    private static String details(ItemStack item){
        StringBuilder text=new StringBuilder(item.getType().name());
        short max=item.getType().getMaxDurability();
        if(max>0){ItemMeta meta=item.getItemMeta();int used=meta instanceof Damageable?((Damageable)meta).getDamage():0;text.append(" | прочность ").append(Math.max(0,max-used)).append('/').append(max);}
        if(item.getEnchantments().isEmpty())text.append(" | без чар");
        else item.getEnchantments().entrySet().stream().sorted(Comparator.comparing(e->e.getKey().getKey().getKey())).limit(8)
                .forEach(e->text.append(" | ").append(e.getKey().getKey().getKey()).append(' ').append(e.getValue()));
        return text.toString();
    }
    private static boolean empty(ItemStack item){return item==null||item.getType().isAir()||item.getAmount()<=0;}
    private static String display(ItemStack item){if(empty(item))return "—";ItemMeta meta=item.getItemMeta();return meta!=null&&meta.hasDisplayName()?meta.getDisplayName():item.getType().name();}
    void way(Player p,String[] args){
        if(args.length!=1){p.sendMessage(ColorUtil.c("&e/way <spawn|pvp|train|mine|farm|casino|parkour|cases|top> &7— направление к варпу"));return;}
        String name=args[0].toLowerCase(Locale.ROOT);
        if(!name.matches("[a-z0-9_-]{1,32}")){p.sendMessage(ColorUtil.c("&cНеизвестный варп."));return;}
        Location target=plugin.getSpawnEngine().getWarpLocation(name);
        if(target==null||target.getWorld()==null){p.sendMessage(ColorUtil.c("&cВарп не найден или его мир не загружен."));return;}
        if(!target.getWorld().equals(p.getWorld())){
            p.sendMessage(ColorUtil.c("&7Варп &f"+name+" &7находится в другом мире (&f"+target.getWorld().getName()+"&7). Команда: &e/warp "+name));return;
        }
        double dx=target.getX()-p.getLocation().getX(),dz=target.getZ()-p.getLocation().getZ();
        String eastWest=Math.abs(dx)<1?"":dx>0?"восток":"запад",northSouth=Math.abs(dz)<1?"":dz>0?"юг":"север";
        p.sendMessage(ColorUtil.c("&#80D8FFНавигатор &f"+name+"&7: "+(eastWest+" "+northSouth).trim()+", ≈"+(int)Math.round(Math.hypot(dx,dz))+" блоков &8| &f"+target.getBlockX()+", "+target.getBlockY()+", "+target.getBlockZ()));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void mine(BlockBreakEvent e){
        if(e.getPlayer().getGameMode()!=GameMode.SURVIVAL||!ore(e.getBlock().getType())||pending.size()>=4096)return;
        pending.add(new PendingOre(e));
        if(!miningScheduled){miningScheduled=true;Bukkit.getScheduler().runTask(plugin,this::processPending);}
    }
    private static boolean ore(Material material){return material.name().endsWith("_ORE")||material==Material.ANCIENT_DEBRIS||material==Material.GILDED_BLACKSTONE;}
    private void processPending(){
        miningScheduled=false;if(pending.isEmpty())return;
        List<PendingOre> batch=new ArrayList<PendingOre>(pending);pending.clear();
        String today=LocalDate.now(ZoneOffset.UTC).toString();
        for(PendingOre ore:batch){
            org.bukkit.block.Block block=ore.event.getBlock();
            if(ore.event.isCancelled()||!block.getWorld().isChunkLoaded(block.getX()>>4,block.getZ()>>4)||block.getType()==ore.material)continue;
            try{plugin.getStore().buffered(data->{
                String path="utility.mining."+ore.player, material=ore.material.name().toLowerCase(Locale.ROOT);
                data.set(path+".total",increment(data.getInt(path+".total",0)));
                data.set(path+".ores."+material,increment(data.getInt(path+".ores."+material,0)));
                int daily=today.equals(data.getString(path+".date"))?data.getInt(path+".today",0):0;
                data.set(path+".date",today);data.set(path+".today",increment(daily));return null;
            });dirty=true;}catch(Exception error){plugin.getLogger().warning("[MineStats] Could not record statistic: "+error.getMessage());}
        }
    }
    private static int increment(int value){return value>=Integer.MAX_VALUE?Integer.MAX_VALUE:Math.max(0,value)+1;}
    void miningStats(Player p){
        try{
            String path="utility.mining."+p.getUniqueId(),today=LocalDate.now(ZoneOffset.UTC).toString();
            int[] stats=plugin.getStore().inspect(data->new int[]{data.getInt(path+".total",0),today.equals(data.getString(path+".date"))?data.getInt(path+".today",0):0});
            p.sendMessage(ColorUtil.c("&#80D8FFДобыто руды: &f"+stats[0]+" &8| &7сегодня (UTC): &f"+stats[1]));
            p.sendMessage(ColorUtil.c("&8Счётчик информационный: учитывает и ранее поставленную игроками руду."));
        }catch(Exception error){p.sendMessage(ColorUtil.c("&cНе удалось прочитать статистику."));}
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void death(PlayerDeathEvent e){
        Player p=e.getEntity();Location loc=p.getLocation();if(loc.getWorld()==null)return;
        try{plugin.getStore().buffered(data->{String path="utility.death."+p.getUniqueId();
            data.set(path+".world",loc.getWorld().getName());data.set(path+".x",loc.getX());data.set(path+".y",loc.getY());data.set(path+".z",loc.getZ());return null;
        });dirty=true;}catch(Exception error){plugin.getLogger().warning("[DeathPoint] Could not record death: "+error.getMessage());}
        beams.remove(p.getUniqueId());
    }
    void deathPoint(Player p,String[] args){
        if(args.length>1){p.sendMessage(ColorUtil.c("&e/deathpoint [beacon|off]"));return;}
        String sub=args.length==0?"status":args[0].toLowerCase(Locale.ROOT);
        if("off".equals(sub)){beams.remove(p.getUniqueId());p.sendMessage(ColorUtil.c("&7Личный маяк смерти выключен."));return;}
        if(!"status".equals(sub)&&!"beacon".equals(sub)){p.sendMessage(ColorUtil.c("&e/deathpoint [beacon|off]"));return;}
        try{
            Location loc=plugin.getStore().inspect(data->{String path="utility.death."+p.getUniqueId();String name=data.getString(path+".world");
                World w=name==null?null:Bukkit.getWorld(name);if(w==null)return null;
                double x=data.getDouble(path+".x"),y=data.getDouble(path+".y"),z=data.getDouble(path+".z");
                return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)?new Location(w,x,y,z):null;
            });
            if(loc==null){p.sendMessage(ColorUtil.c("&eТочка смерти неизвестна или её мир не загружен."));return;}
            p.sendMessage(ColorUtil.c("&#80D8FFПоследняя смерть: &f"+loc.getWorld().getName()+" "+loc.getBlockX()+", "+loc.getBlockY()+", "+loc.getBlockZ()));
            if("beacon".equals(sub)){beams.put(p.getUniqueId(),new Beam(loc,System.currentTimeMillis()+300_000L));p.sendMessage(ColorUtil.c("&7Личный луч будет виден в том же мире на расстоянии до 128 блоков, максимум 5 минут. Это не сохранение вещей."));}
        }catch(Exception error){p.sendMessage(ColorUtil.c("&cНе удалось прочитать точку смерти."));}
    }
    private void showBeams(){
        long now=System.currentTimeMillis();
        for(UUID id:new ArrayList<UUID>(beams.keySet())){
            Beam beam=beams.get(id);Player p=Bukkit.getPlayer(id);
            if(beam==null||beam.until<=now||p==null){beams.remove(id);continue;}
            if(p.getWorld()!=beam.location.getWorld()||p.getLocation().distanceSquared(beam.location)>128*128)continue;
            for(int i=0;i<10;i++)p.spawnParticle(Particle.END_ROD,beam.location.clone().add(0,i*.8,0),1,0,0,0,0);
        }
    }
    private void flush(){if(plugin.getStore().saveBuffered())dirty=false;}
}
