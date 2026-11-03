package net.kinggrief.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Matrix4f;

/** Scalable, non-persistent decorative displays: never rewrites world blocks. */
final class SpawnTreeModel implements Listener {
    private static final String KEY = "spawnTree.modelLevel";
    private static final String TAG = "kg_spawn_tree_v77";
    private static final String LEGACY_TAG = "kg_spawn_tree_v76";
    private static final String PART_TAG_PREFIX = "kg_elka_v77_";
    private static final int MIN_LEVEL = -3, MAX_LEVEL = 3, MAX_PARTS = 256;
    private static final Set<String> GROUPS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "tree", "gift_west", "gift_north", "gift_south", "figure_base",
            "person_1", "person_2", "person_3", "hand_star")));
    private static final Set<String> ADMIN_RANKS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "owner", "techadmin", "curator", "curator_discord", "curator_kp", "curator_media", "curator_dkp",
            "gladmin", "stadmin", "admin", "mladmin", "downer", "dadmin", "dmladmin")));
    private final KingGriefCore plugin;
    private final List<Part> parts = new ArrayList<Part>();
    private final List<BlockDisplay> displays = new ArrayList<BlockDisplay>();
    private World displayWorld;
    private BukkitTask refreshTask;
    private int level;
    private boolean ready;
    private long lastChangeNanos;

    private static final class Part {
        final String group;
        final BlockData block;
        final float x,y,z,width,height,depth;
        Part(String group,BlockData block,float x,float y,float z,float width,float height,float depth) {
            this.group=group;this.block=block;this.x=x;this.y=y;this.z=z;this.width=width;this.height=height;this.depth=depth;
        }
        Matrix4f matrix(float factor) {
            return new Matrix4f().translate(x*factor,y*factor,z*factor)
                    .scale(width*factor,height*factor,depth*factor);
        }
    }

    SpawnTreeModel(KingGriefCore plugin) { this.plugin=plugin; }
    void start() {
        try {
            readParts();
            level=plugin.getStore().inspect(data -> {
                Object raw=data.get(KEY);
                if (raw==null) return 0;
                if (!(raw instanceof Integer) || ((Integer)raw)<MIN_LEVEL || ((Integer)raw)>MAX_LEVEL)
                    throw new IOException("invalid saved spawn tree level");
                return (Integer)raw;
            });
            ready=true;
            // Never force-load or generate a chunk. On an empty server the model
            // appears as soon as a player approaches and that chunk loads.
            refreshTask=Bukkit.getScheduler().runTaskTimer(plugin, this::ensureModel, 20L, 100L);
        } catch (Exception error) {
            plugin.getLogger().severe("[Elka] Disabled safely: " + error.getMessage());
        }
    }
    private void readParts() throws IOException {
        InputStream stream=plugin.getResource("elka_v77.csv");
        if(stream==null)throw new IOException("missing model resource");
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.US_ASCII))) {
            String line;
            while((line=reader.readLine())!=null) {
                if(line.isEmpty()||line.startsWith("#"))continue;
                String[] a=line.split(",",-1);
                if(a.length!=8||parts.size()>=MAX_PARTS)throw new IOException("invalid or excessive model parts");
                if(!GROUPS.contains(a[0]))throw new IOException("invalid part group");
                Material material=Material.matchMaterial(a[1]);
                if(material==null||!material.isBlock())throw new IOException("invalid block material " + a[1]);
                float[] v=new float[6];
                try { for(int i=0;i<6;i++){v[i]=Float.parseFloat(a[i+2]);if(!Float.isFinite(v[i])||Math.abs(v[i])>80)throw new NumberFormatException();} }
                catch(NumberFormatException error){throw new IOException("invalid model dimension",error);}
                if(v[3]<=0||v[4]<=0||v[5]<=0)throw new IOException("invalid model size");
                parts.add(new Part(a[0],Bukkit.createBlockData(material),v[0],v[1],v[2],v[3],v[4],v[5]));
            }
        }
        if(parts.isEmpty())throw new IOException("empty model");
    }
    float factor() { return 1.0f+0.1f*level; }
    private World activeWorld() {
        Location spawn=plugin.getSpawnEngine().getPreparedSpawnArrival();
        return spawn==null?null:spawn.getWorld();
    }
    private void ensureModel() {
        if(!ready)return;
        World world=activeWorld();
        if(world==null||!world.isChunkLoaded(0,0)) { removeDisplays();return; }
        if(world.equals(displayWorld)&&displays.size()==parts.size()) {
            boolean valid=true;
            for(BlockDisplay display:displays)if(!display.isValid()){valid=false;break;}
            if(valid)return;
        }
        removeDisplays();
        // Only our own exact versioned display tags in this loaded anchor chunk.
        // The old tag covers an interrupted in-process upgrade; unrelated
        // entities and all other worlds/chunks are never touched.
        for(Entity entity:world.getChunkAt(0,0).getEntities()) {
            if(entity instanceof BlockDisplay && (entity.getScoreboardTags().contains(TAG)
                    || entity.getScoreboardTags().contains(LEGACY_TAG))) entity.remove();
        }
        try {
            displayWorld=world;
            Location anchor=new Location(world,0,100,0);
            float scale=factor();
            for(Part part:parts) {
                BlockDisplay display=world.spawn(anchor,BlockDisplay.class,d->{
                    d.setBlock(part.block);d.setPersistent(false);d.setGravity(false);d.setInvulnerable(true);d.setSilent(true);
                    d.setBillboard(Display.Billboard.FIXED);d.setDisplayWidth(56f);d.setDisplayHeight(64f);d.setViewRange(1.5f);
                    d.setInterpolationDuration(8);d.setTransformationMatrix(part.matrix(scale));
                    d.addScoreboardTag(TAG);d.addScoreboardTag(PART_TAG_PREFIX+part.group);
                });
                displays.add(display);
            }
        } catch(RuntimeException error) {
            removeDisplays();
            plugin.getLogger().warning("[Elka] Could not render model; will retry: " + error.getMessage());
        }
    }
    private void clearReferences() { displays.clear(); displayWorld=null; }
    private void removeDisplays() {
        for(BlockDisplay display:displays)if(display.isValid())display.remove();
        clearReferences();
    }
    @EventHandler public void chunkLoad(ChunkLoadEvent event) {
        if(event.getChunk().getX()!=0||event.getChunk().getZ()!=0||!event.getWorld().equals(activeWorld()))return;
        Bukkit.getScheduler().runTask(plugin,this::ensureModel);
    }
    void shutdown() { if(refreshTask!=null)refreshTask.cancel();removeDisplays(); }

    boolean permitted(CommandSender sender) {
        if(sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender)return true;
        if(!(sender instanceof Player))return false;
        Player player=(Player)sender;
        RankManager.RankDef rank=plugin.getRanks().getRank(player);
        return rank!=null&&ADMIN_RANKS.contains(rank.id);
    }
    List<String> complete(CommandSender sender,String[] args) {
        if(!permitted(sender)||args.length!=1)return Collections.emptyList();
        List<String> result=new ArrayList<String>();
        String prefix=args[0].toLowerCase(Locale.ROOT);
        for(String option:Arrays.asList("status","1","2","3","-1","-2","-3"))if(option.startsWith(prefix))result.add(option);
        return result;
    }
    void command(CommandSender sender,String[] args) {
        if(sender instanceof Player) {
            Player player=(Player)sender;
            if(!player.isOnline()||!plugin.getStore().isAuthenticated(player)) { send(sender,"&cСначала войдите в аккаунт.");return; }
            if(plugin.getStore().isQuickFrozen(player)||plugin.getModeration().isFrozenOrAscending(player)) { send(sender,"&cКоманда недоступна во время проверки.");return; }
        }
        if(!permitted(sender)){send(sender,"&cРазмер ёлки доступен только овнерам, администраторам и консоли.");return;}
        if(!ready||activeWorld()==null){send(sender,"&cГотовый спавн или модель ёлки сейчас недоступны.");return;}
        if(args.length==0||(args.length==1&&"status".equalsIgnoreCase(args[0]))) {
            send(sender,"&#80D8FFЁлка: уровень &f"+level+" &7(масштаб "+Math.round(factor()*100)+"%, предел -3…+3).");
            send(sender,"&e/elka <1|2|3|-1|-2|-3> &7— изменить размер на указанное число шагов.");return;
        }
        if(args.length!=1||!Arrays.asList("1","2","3","-1","-2","-3").contains(args[0])){
            send(sender,"&e/elka <1|2|3|-1|-2|-3> &7или &e/elka status");return;
        }
        int target=level+Integer.parseInt(args[0]);
        if(target<MIN_LEVEL||target>MAX_LEVEL){send(sender,"&cПредел размера: -3…+3. Сейчас: "+level+".");return;}
        long now=System.nanoTime();
        if(lastChangeNanos!=0&&now-lastChangeNanos<1_000_000_000L){send(sender,"&eПодождите секунду перед следующим изменением.");return;}
        try {
            plugin.getStore().transact(data->{data.set(KEY,target);return null;});
        } catch(Exception error){plugin.getLogger().warning("[Elka] Could not save level: "+error.getMessage());send(sender,"&cНе удалось сохранить размер; ёлка не изменена.");return;}
        level=target;lastChangeNanos=now;
        if(displayWorld!=null&&displays.size()==parts.size()) {
            try {
                float scale=factor();
                for(int i=0;i<parts.size();i++){
                    BlockDisplay display=displays.get(i);
                    if(!display.isValid())throw new IllegalStateException("model chunk unloaded");
                    display.setTransformationMatrix(parts.get(i).matrix(scale));
                    display.setInterpolationDelay(0);
                }
            } catch(RuntimeException error) { removeDisplays();plugin.getLogger().warning("[Elka] Recreating model after resize: "+error.getMessage()); }
        }
        send(sender,"&aЁлка, люди и подарки: уровень "+level+" ("+Math.round(factor()*100)+"%). Размер сохранён.");
    }
    private static void send(CommandSender sender,String message){sender.sendMessage(ColorUtil.c(message));}
}
