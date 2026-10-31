package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.joml.Matrix4f;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Ranked times and optional, viewer-only replays for the checked v74 course.
 * Validation is deliberately conservative and is not a replacement for an anti-cheat. */
final class ParkourRecordsManager implements Listener {
    private static final int MAX_FRAMES=3600; // 2-tick samples, at most six minutes at 20 TPS
    private static final long MIN_TIME_MS=12_000L, MAX_TIME_MS=360_000L;
    private static final int MAGIC=0x4b475032; // KGP2 includes its associated best time
    private static final class Frame {
        final float x,y,z,yaw;
        Frame(float x,float y,float z,float yaw){this.x=x;this.y=y;this.z=z;this.yaw=yaw;}
    }
    private static final class Run {
        final UUID token=UUID.randomUUID(); final World world; final long started=System.nanoTime();
        final List<Frame> frames=new ArrayList<Frame>(); final List<BlockDisplay> ghost=new ArrayList<BlockDisplay>();
        boolean valid; int next=1; List<Frame> replay;
        Run(Player player){world=player.getWorld();valid=player.getGameMode()==GameMode.SURVIVAL&&!player.isInsideVehicle()&&!player.isFlying()&&!player.isGliding();}
        void removeGhost(){for(Entity entity:ghost)if(entity!=null)entity.remove();ghost.clear();}
    }
    private static final class Score {
        final UUID uuid; final String name; final long millis;
        Score(UUID uuid,String name,long millis){this.uuid=uuid;this.name=name;this.millis=millis;}
    }
    private final KingGriefCore plugin;
    private final ParkourCourse course;
    private final Map<UUID,Run> active=new HashMap<UUID,Run>();
    private final ThreadPoolExecutor io;
    private BukkitRunnable ticker;
    ParkourRecordsManager(KingGriefCore plugin){
        this.plugin=plugin;course=ParkourCourse.load(plugin);
        io=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(16),r->{Thread t=new Thread(r,"KingGrief-parkour-io");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    }
    void start(){ticker=new BukkitRunnable(){@Override public void run(){tick();}};ticker.runTaskTimer(plugin,2L,2L);}
    void shutdown(){if(ticker!=null)ticker.cancel();for(Run run:active.values())run.removeGhost();active.clear();io.shutdown();}
    @EventHandler public void quit(PlayerQuitEvent event){end(event.getPlayer());}
    void begin(Player player){
        end(player);if(course==null)return;
        Run run=new Run(player);active.put(player.getUniqueId(),run);
        String selected;
        try{selected=plugin.getStore().inspect(data->data.getString("parkour.settings."+player.getUniqueId()+".ghost","off"));}
        catch(Exception error){selected="off";}
        Score reference=null;
        if("me".equals(selected)||"top".equals(selected)){
            List<Score> all=scores("all");
            if("top".equals(selected)&&!all.isEmpty())reference=all.get(0);
            else if("me".equals(selected))for(Score score:all)if(score.uuid.equals(player.getUniqueId())){reference=score;break;}
        }
        if(reference!=null)loadReplay(player,run,reference.uuid,reference.millis);
    }
    void end(Player player){Run run=active.remove(player.getUniqueId());if(run!=null)run.removeGhost();}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void movement(PlayerMoveEvent event){
        Player p=event.getPlayer();Run run=active.get(p.getUniqueId());if(run==null||event.getTo()==null)return;
        if(event instanceof PlayerTeleportEvent){run.valid=false;return;}
        Location from=event.getFrom(),to=event.getTo();
        if(to.getWorld()!=run.world||from.getWorld()!=to.getWorld()||from.distanceSquared(to)>16.0
                ||p.getGameMode()!=GameMode.SURVIVAL||p.isFlying()||p.isGliding()||p.isInsideVehicle())run.valid=false;
        if(!run.valid)return;
        while(run.next<course.routeSize()&&course.route(run.next).isNearSurface(to))run.next++;
    }
    void finish(Player player){
        Run run=active.get(player.getUniqueId());if(run==null)return;
        long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-run.started);
        if(course!=null&&run.next<course.routeSize()&&course.route(run.next).isNearSurface(player.getLocation()))run.next++;
        if(!run.valid||run.next!=course.routeSize()||elapsed<MIN_TIME_MS||elapsed>MAX_TIME_MS){
            player.sendMessage(ColorUtil.c("&7В топ попадают только непрерывные забеги в выживании по всем площадкам, без возврата на чекпоинт. Это не полноценный античит."));return;
        }
        final String name=player.getName(), id=player.getUniqueId().toString(),today=day(),week=week();
        try{
            boolean improved=plugin.getStore().transact(data->{
                String base="parkour.records."+id;long old=data.getLong(base+".all",0L);
                boolean best=old<=0||elapsed<old;
                if(best)data.set(base+".all",elapsed);
                data.set(base+".name",name);
                long dayBest=today.equals(data.getString(base+".day.date"))?data.getLong(base+".day.time",0L):0L;
                if(dayBest<=0||elapsed<dayBest){data.set(base+".day.date",today);data.set(base+".day.time",elapsed);}
                long weekBest=week.equals(data.getString(base+".week.date"))?data.getLong(base+".week.time",0L):0L;
                if(weekBest<=0||elapsed<weekBest){data.set(base+".week.date",week);data.set(base+".week.time",elapsed);}
                return best;
            });
            player.sendMessage(ColorUtil.c("&#80D8FF🏁 Результат принят в топ: &f"+format(elapsed)+(improved?" &aличный рекорд!":"")));
            if(improved&&run.frames.size()>=20)saveReplay(player.getUniqueId(),elapsed,new ArrayList<Frame>(run.frames));
        }catch(Exception error){plugin.getLogger().warning("[Parkour] Record not saved: "+error.getMessage());player.sendMessage(ColorUtil.c("&cНе удалось сохранить результат в топ."));}
    }
    void top(Player player,String[] args){
        String period=args.length>1?args[1].toLowerCase(Locale.ROOT):"all";
        if(!"day".equals(period)&&!"week".equals(period)&&!"all".equals(period)){
            player.sendMessage(ColorUtil.c("&e/parkour top <day|week|all>"));return;
        }
        List<Score> scores=scores(period);
        player.sendMessage(ColorUtil.c("&#40C4FF✦ Топ паркура: &f"+("day".equals(period)?"сегодня UTC":"week".equals(period)?"неделя UTC":"за всё время")));
        for(int i=0;i<Math.min(10,scores.size());i++){Score s=scores.get(i);player.sendMessage(ColorUtil.c("&7"+(i+1)+". &f"+s.name+" &8— &b"+format(s.millis)));}
        if(scores.isEmpty())player.sendMessage(ColorUtil.c("&7Пока нет подтверждённых результатов."));
    }
    void ghost(Player player,String[] args){
        if(args.length!=2){player.sendMessage(ColorUtil.c("&e/parkour ghost <me|top|off>"));return;}
        String value=args[1].toLowerCase(Locale.ROOT);
        if(!"me".equals(value)&&!"top".equals(value)&&!"off".equals(value)){player.sendMessage(ColorUtil.c("&e/parkour ghost <me|top|off>"));return;}
        try{plugin.getStore().transact(data->{data.set("parkour.settings."+player.getUniqueId()+".ghost",value);return null;});
            Run run=active.get(player.getUniqueId());if(run!=null&&"off".equals(value)){run.removeGhost();run.replay=null;}
            player.sendMessage(ColorUtil.c("&#80D8FFПризрак: &f"+value+"&7. Выбор me/top применяется со следующего старта и виден только вам."));
        }catch(Exception error){player.sendMessage(ColorUtil.c("&cНе удалось сохранить настройку призрака."));}
    }
    private List<Score> scores(String period){
        try{return plugin.getStore().inspect(data->{
            List<Score> list=new ArrayList<Score>();ConfigurationSection section=data.getConfigurationSection("parkour.records");
            if(section==null)return list;
            String required="day".equals(period)?day():"week".equals(period)?week():null;
            for(String id:section.getKeys(false)){
                try{
                    UUID uuid=UUID.fromString(id);String name=section.getString(id+".name","");
                    if(!name.matches("[A-Za-z0-9_]{1,16}"))continue;
                    long value="all".equals(period)?section.getLong(id+".all",0L):section.getLong(id+"."+period+".time",0L);
                    if(required!=null&&!required.equals(section.getString(id+"."+period+".date")))continue;
                    if(value>=MIN_TIME_MS&&value<=MAX_TIME_MS)list.add(new Score(uuid,name,value));
                }catch(IllegalArgumentException ignored){}
            }
            list.sort(Comparator.comparingLong((Score s)->s.millis).thenComparing(s->s.name.toLowerCase(Locale.ROOT)));
            return list;
        });}catch(Exception error){return new ArrayList<Score>();}
    }
    private void tick(){
        int ghostLimit=Math.max(0,Math.min(16,plugin.getConfig().getInt("parkour.max-ghosts",8))),displayed=0;
        for(Map.Entry<UUID,Run> entry:new ArrayList<Map.Entry<UUID,Run>>(active.entrySet())){
            Player p=Bukkit.getPlayer(entry.getKey());Run run=entry.getValue();
            if(p==null||!p.isOnline()){run.removeGhost();active.remove(entry.getKey());continue;}
            if(p.getWorld()!=run.world){run.valid=false;run.removeGhost();continue;}
            Location loc=p.getLocation();
            if(run.valid&&run.frames.size()<MAX_FRAMES&&validFrame(loc))run.frames.add(new Frame((float)loc.getX(),(float)loc.getY(),(float)loc.getZ(),loc.getYaw()));
            else if(run.valid)run.valid=false;
            if(run.replay!=null&&displayed++<ghostLimit){
                if(run.ghost.isEmpty())createGhost(p,run);
                int index=(int)Math.min(run.replay.size()-1,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-run.started)/100L);
                if(index>=0)moveGhost(run,run.replay.get(index));
            }else if(!run.ghost.isEmpty())run.removeGhost();
        }
    }
    private static boolean validFrame(Location l){return l.getWorld()!=null&&Double.isFinite(l.getX())&&Double.isFinite(l.getY())&&Double.isFinite(l.getZ())
            &&Math.abs(l.getX())<200&&Math.abs(l.getZ())<200&&l.getY()>=80&&l.getY()<=180&&Float.isFinite(l.getYaw());}
    private void createGhost(Player viewer,Run run){
        try{
            Location spawn=viewer.getLocation().clone();spawn.setYaw(0);spawn.setPitch(0);
            float[][] parts={{-.19f,.72f,-.11f,.38f,.55f,.22f},{-.16f,1.27f,-.15f,.32f,.32f,.30f},
                    {-.33f,.74f,-.10f,.13f,.52f,.20f},{.20f,.74f,-.10f,.13f,.52f,.20f},
                    {-.18f,.15f,-.10f,.15f,.56f,.20f},{.03f,.15f,-.10f,.15f,.56f,.20f}};
            for(float[] part:parts){
                BlockDisplay entity=run.world.spawn(spawn,BlockDisplay.class,d->{
                    d.setVisibleByDefault(false);d.setPersistent(false);d.setGravity(false);d.setInvulnerable(true);d.setSilent(true);
                    d.setBillboard(Display.Billboard.FIXED);d.setBlock(Bukkit.createBlockData(Material.LIGHT_BLUE_STAINED_GLASS));
                    d.setDisplayWidth(2f);d.setDisplayHeight(3f);d.setViewRange(.6f);d.setTeleportDuration(2);d.setInterpolationDuration(2);
                    d.setTransformationMatrix(new Matrix4f().translate(part[0],part[1],part[2]).scale(part[3],part[4],part[5]));d.addScoreboardTag("kg_parkour_ghost");
                });run.ghost.add(entity);viewer.showEntity(plugin,entity);
            }
        }catch(RuntimeException error){run.removeGhost();run.replay=null;plugin.getLogger().warning("[Parkour] Could not display ghost: "+error.getMessage());}
    }
    private void moveGhost(Run run,Frame frame){
        Location loc=new Location(run.world,frame.x,frame.y,frame.z,frame.yaw,0);
        if(!run.world.isChunkLoaded(loc.getBlockX()>>4,loc.getBlockZ()>>4))return;
        for(BlockDisplay entity:run.ghost)if(entity.isValid())entity.teleport(loc);
    }
    private Path replayDir() throws IOException{
        Path data=plugin.getDataFolder().toPath().toAbsolutePath().normalize(),dir=data.resolve("parkour-replays");
        for(Path p=data;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("symbolic data path");
        if(Files.isSymbolicLink(dir))throw new IOException("symbolic replay directory");
        Files.createDirectories(dir);return dir;
    }
    private void saveReplay(UUID uuid,long elapsed,List<Frame> frames){
        try{io.execute(()->{
            Path temp=null;
            try{
                Path dir=replayDir(),target=dir.resolve(uuid+".gz");
                if(Files.isSymbolicLink(target))throw new IOException("symbolic replay file");
                temp=Files.createTempFile(dir,".replay-",".tmp");
                try(DataOutputStream out=new DataOutputStream(new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(temp))))){
                    out.writeInt(MAGIC);out.writeLong(elapsed);out.writeInt(frames.size());
                    for(Frame frame:frames){out.writeFloat(frame.x);out.writeFloat(frame.y);out.writeFloat(frame.z);out.writeFloat(frame.yaw);}
                }
                Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);temp=null;
            }catch(Exception error){plugin.getLogger().warning("[Parkour] Replay not saved: "+error.getMessage());}
            finally{if(temp!=null)try{Files.deleteIfExists(temp);}catch(IOException ignored){}}
        });}catch(java.util.concurrent.RejectedExecutionException error){plugin.getLogger().warning("[Parkour] Replay queue full; time was saved without a recording.");}
    }
    private void loadReplay(Player player,Run run,UUID owner,long expectedTime){
        UUID viewer=player.getUniqueId(),token=run.token;
        try{io.execute(()->{
            List<Frame> frames=null;
            try{
                Path file=replayDir().resolve(owner+".gz");
                if(Files.isSymbolicLink(file)||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>512_000L)return;
                try(DataInputStream input=new DataInputStream(new GZIPInputStream(new BufferedInputStream(Files.newInputStream(file,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS))))){
                    if(input.readInt()!=MAGIC||input.readLong()!=expectedTime)throw new IOException("replay does not match the current record");
                    int count=input.readInt();
                    if(count<20||count>MAX_FRAMES)throw new IOException("invalid replay size");
                    frames=new ArrayList<Frame>(count);
                    for(int i=0;i<count;i++){
                        Frame f=new Frame(input.readFloat(),input.readFloat(),input.readFloat(),input.readFloat());
                        if(!Float.isFinite(f.x)||!Float.isFinite(f.y)||!Float.isFinite(f.z)||!Float.isFinite(f.yaw)
                                ||Math.abs(f.x)>=200||Math.abs(f.z)>=200||f.y<80||f.y>180)throw new IOException("invalid replay coordinate");
                        frames.add(f);
                    }
                    if(input.read()!=-1)throw new IOException("trailing replay data");
                }
                final List<Frame> loaded=frames;
                if(plugin.isEnabled())Bukkit.getScheduler().runTask(plugin,()->{
                    Run current=active.get(viewer);if(current!=null&&current.token.equals(token))current.replay=loaded;
                });
            }catch(Exception error){plugin.getLogger().warning("[Parkour] Replay unavailable: "+error.getMessage());}
        });}catch(java.util.concurrent.RejectedExecutionException error){player.sendMessage(ColorUtil.c("&eПризрак временно недоступен: очередь занята."));}
    }
    private static String day(){return LocalDate.now(ZoneOffset.UTC).toString();}
    private static String week(){LocalDate d=LocalDate.now(ZoneOffset.UTC);WeekFields iso=WeekFields.ISO;
        return String.format(Locale.ROOT,"%04d-W%02d",d.get(iso.weekBasedYear()),d.get(iso.weekOfWeekBasedYear()));}
    private static String format(long millis){return String.format(Locale.ROOT,"%d:%05.2f",millis/60_000L,(millis%60_000L)/1000.0);}
}
