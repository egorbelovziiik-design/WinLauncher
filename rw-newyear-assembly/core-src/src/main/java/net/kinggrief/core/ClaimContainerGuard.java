package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Protects container clicks in Core claims, including both halves of a double chest.
 * This does not replace WorldGuard or protect against every non-player transport. */
final class ClaimContainerGuard implements Listener {
    private static final int MAX_LOG=50,MAX_RATE_KEYS=4096;
    private static final long RATE_NANOS=5_000_000_000L;
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd.MM HH:mm 'UTC'").withZone(ZoneOffset.UTC);
    private final KingGriefCore plugin;
    private final Map<String,Long> last=new HashMap<String,Long>();
    private final Set<UUID> closing=new HashSet<UUID>();
    private BukkitRunnable ticker;
    private boolean dirty;
    ClaimContainerGuard(KingGriefCore plugin){this.plugin=plugin;}
    void start(){ticker=new BukkitRunnable(){@Override public void run(){if(dirty)flush();prune();}};ticker.runTaskTimer(plugin,1200L,1200L);}
    void shutdown(){if(ticker!=null)ticker.cancel();if(dirty)flush();last.clear();closing.clear();}
    @EventHandler public void quit(PlayerQuitEvent event){closing.remove(event.getPlayer().getUniqueId());}
    private void flush(){if(plugin.getStore().saveBuffered())dirty=false;}
    private void prune(){long cutoff=System.nanoTime()-RATE_NANOS;last.values().removeIf(value->value<cutoff);}

    /** Run before Core's custom-item handler so a denied chest does not trigger an item ability. */
    boolean enforce(PlayerInteractEvent event,boolean notify){
        if(event.getAction()!=Action.RIGHT_CLICK_BLOCK||event.getClickedBlock()==null)return false;
        Player player=event.getPlayer();Block clicked=event.getClickedBlock();
        if(!container(clicked)||plugin.getRanks().isOwnerLevel(player))return false;
        List<Block> parts=new ArrayList<Block>();parts.add(clicked);
        if(!addPairedChest(clicked,parts)){
            event.setUseInteractedBlock(Event.Result.DENY);event.setUseItemInHand(Event.Result.DENY);
            if(notify)player.sendMessage(ColorUtil.c("&eДвойной сундук временно недоступен: соседний блок не загружен или повреждён."));
            return true;
        }
        Map<String,DataStore.Claim> denied=new LinkedHashMap<String,DataStore.Claim>();
        for(Block part:parts){
            DataStore.Claim claim=plugin.getStore().getClaimAt(part.getLocation());
            if(claim!=null&&!claim.canOpenContainers(player.getName()))denied.put(claim.id,claim);
        }
        if(denied.isEmpty())return false;
        event.setUseInteractedBlock(Event.Result.DENY);event.setUseItemInHand(Event.Result.DENY);
        for(DataStore.Claim claim:denied.values())record(claim,player,clicked);
        if(notify)player.sendMessage(ColorUtil.c("&cКонтейнер защищён встроенным приватом &f"+denied.values().iterator().next().owner+"&c."));
        return true;
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void enforceAfterOtherListeners(PlayerInteractEvent event){enforce(event,false);}

    /** Access is rechecked while a physical container is open, not just on its initial click. */
    @EventHandler(priority=EventPriority.LOWEST)
    public void onInventoryClickEarly(InventoryClickEvent event){onInventoryClick(event);}
    @EventHandler(priority=EventPriority.LOWEST)
    public void onInventoryDragEarly(InventoryDragEvent event){onInventoryDrag(event);}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event){
        if(!(event.getWhoClicked() instanceof Player))return;
        Player player=(Player)event.getWhoClicked();Inventory top=event.getView().getTopInventory();
        if(!canUseOpenInventory(player,top)){event.setCancelled(true);closeLater(player,top);}
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event){
        if(!(event.getWhoClicked() instanceof Player))return;
        Player player=(Player)event.getWhoClicked();Inventory top=event.getView().getTopInventory();
        if(!canUseOpenInventory(player,top)){event.setCancelled(true);closeLater(player,top);}
    }
    void accessChanged(String name){
        if(name==null)return;
        for(Player player:Bukkit.getOnlinePlayers())if(player.getName().equalsIgnoreCase(name)){
            Inventory top=player.getOpenInventory().getTopInventory();
            if(!canUseOpenInventory(player,top))closeLater(player,top);
        }
    }
    private boolean canUseOpenInventory(Player player,Inventory inventory){
        if(plugin.getRanks().isOwnerLevel(player))return true;
        List<Location> locations=new ArrayList<Location>(2);
        if(inventory instanceof DoubleChestInventory){
            DoubleChestInventory pair=(DoubleChestInventory)inventory;
            Location left=pair.getLeftSide().getLocation(),right=pair.getRightSide().getLocation();
            if(left==null||right==null)return false;
            locations.add(left);locations.add(right);
        }else{
            InventoryHolder holder=holderWithoutSnapshot(inventory);
            if(!(holder instanceof BlockState))return true; // virtual GUIs and the player's own ender chest
            if(!addHolderLocation(holder,locations))return false;
        }
        for(Location location:locations){
            if(location.getWorld()==null||!location.getWorld().isChunkLoaded(location.getBlockX()>>4,location.getBlockZ()>>4))return false;
            DataStore.Claim claim=plugin.getStore().getClaimAt(location);
            if(claim!=null&&!claim.canOpenContainers(player.getName()))return false;
        }
        return true;
    }
    private static InventoryHolder holderWithoutSnapshot(Inventory inventory){
        // Paper avoids cloning a potentially huge container on every inventory click.
        try{return inventory.getHolder(false);}catch(NoSuchMethodError|AbstractMethodError unavailable){return inventory.getHolder();}
    }
    private static boolean addHolderLocation(InventoryHolder holder,List<Location> locations){
        if(!(holder instanceof BlockState))return false;
        Location location=((BlockState)holder).getLocation();
        if(location==null||location.getWorld()==null)return false;
        locations.add(location);return true;
    }
    private void closeLater(Player player,Inventory top){
        if(!closing.add(player.getUniqueId()))return;
        Bukkit.getScheduler().runTask(plugin,()->{
            closing.remove(player.getUniqueId());
            if(player.isOnline()&&player.getOpenInventory().getTopInventory()==top&&!canUseOpenInventory(player,top)){
                player.closeInventory();player.sendMessage(ColorUtil.c("&cДоступ к контейнеру изменён; окно закрыто."));
            }
        });
    }
    private static boolean container(Block block){
        if(block.getType()==Material.ENDER_CHEST)return true;
        return block.getState() instanceof InventoryHolder;
    }
    /** Check neighbours only in already-loaded chunks; malformed or unloaded halves fail closed. */
    private static boolean addPairedChest(Block clicked,List<Block> parts){
        BlockData data=clicked.getBlockData();if(!(data instanceof Chest))return true;
        Chest chest=(Chest)data;if(chest.getType()==Chest.Type.SINGLE)return true;
        BlockFace facing=chest.getFacing();
        // Chest.Type is relative to the chest itself, not the player looking at it.
        // A LEFT half's partner is clockwise from its facing; RIGHT is counter-clockwise.
        int sign=chest.getType()==Chest.Type.LEFT?1:-1;
        int x=clicked.getX()-sign*facing.getModZ(),z=clicked.getZ()+sign*facing.getModX();
        if(!clicked.getWorld().isChunkLoaded(x>>4,z>>4))return false;
        Block other=clicked.getWorld().getBlockAt(x,clicked.getY(),z);
        if(other.getType()!=clicked.getType())return false;
        BlockData otherData=other.getBlockData();if(!(otherData instanceof Chest))return false;
        Chest neighbor=(Chest)otherData;
        if(neighbor.getFacing()!=facing||neighbor.getType()==Chest.Type.SINGLE||neighbor.getType()==chest.getType())return false;
        parts.add(other);return true;
    }
    private void record(DataStore.Claim claim,Player player,Block clicked){
        if(claim.id==null||!claim.id.matches("rg_[0-9]+_[0-9]+"))return;
        long now=System.nanoTime();String rate=claim.id+":"+player.getUniqueId()+":"+clicked.getX()+":"+clicked.getY()+":"+clicked.getZ();
        Long previous=last.get(rate);if(previous!=null&&now-previous<RATE_NANOS)return;
        if(last.size()>=MAX_RATE_KEYS){prune();if(last.size()>=MAX_RATE_KEYS)return;}
        last.put(rate,now);
        try{plugin.getStore().buffered(data->{
            String path="claimAudit."+claim.id+".entries";
            List<String> entries=new ArrayList<String>(data.getStringList(path));
            String name=player.getName();if(!name.matches("[A-Za-z0-9_]{1,16}"))name="unknown";
            entries.add(0,System.currentTimeMillis()+"|"+name+"|"+clicked.getType().name()+"|"+clicked.getX()+"|"+clicked.getY()+"|"+clicked.getZ());
            while(entries.size()>MAX_LOG)entries.remove(entries.size()-1);
            data.set(path,entries);return null;
        });dirty=true;}catch(Exception error){plugin.getLogger().warning("[ClaimAudit] Could not buffer an attempt: "+error.getMessage());}
    }
    void log(Player player,DataStore.Claim claim,String[] args){
        if(!claim.owner.equalsIgnoreCase(player.getName())&&!plugin.getRanks().isOwnerLevel(player)){
            player.sendMessage(ColorUtil.c("&cЖурнал доступен владельцу привата, OWNER и ТЕХ.АДМИНУ."));return;
        }
        if(args.length>2){player.sendMessage(ColorUtil.c("&e/rg log [страница]"));return;}
        int number=1;if(args.length==2)try{number=Integer.parseInt(args[1]);}catch(NumberFormatException error){number=0;}
        if(number<1){player.sendMessage(ColorUtil.c("&e/rg log [номер страницы от 1]"));return;}
        final int requested=number;
        try{plugin.getStore().inspect(data->{
            List<String> entries=data.getStringList("claimAudit."+claim.id+".entries");
            int pages=Math.max(1,(entries.size()+4)/5),page=Math.min(requested,pages);
            player.sendMessage(ColorUtil.c("&#80D8FFПопытки открыть контейнеры в привате &f"+claim.owner+" &8("+page+"/"+pages+")&7:"));
            for(int i=(page-1)*5;i<Math.min(entries.size(),page*5);i++){
                String[] fields=entries.get(i).split("\\|",-1);
                if(fields.length!=6||!fields[1].matches("[A-Za-z0-9_]{1,16}")||!fields[2].matches("[A-Z0-9_]{1,64}"))continue;
                try{
                    long time=Long.parseLong(fields[0]);int x=Integer.parseInt(fields[3]),y=Integer.parseInt(fields[4]),z=Integer.parseInt(fields[5]);
                    player.sendMessage(ColorUtil.c(" &8• &7"+DATE.format(Instant.ofEpochMilli(time))+" &f"+fields[1]+" &8— &7"+fields[2]+" ("+x+", "+y+", "+z+")"));
                }catch(Exception ignored){}
            }
            if(entries.isEmpty())player.sendMessage(ColorUtil.c(" &8• &7Записей пока нет."));
            if(page<pages)player.sendMessage(ColorUtil.c("&7Следующая страница: &f/rg log "+(page+1)));
            player.sendMessage(ColorUtil.c("&8Записываются только заблокированные попытки контейнеров встроенного привата; журнал не отслеживает WorldGuard и воронки."));
            return null;
        });}catch(Exception error){player.sendMessage(ColorUtil.c("&cНе удалось прочитать журнал."));}
    }
}
