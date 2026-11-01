package net.kinggrief.core;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Personal read-only filters. Purchases still use the stable ID displayed in the GUI. */
final class AuctionSearchManager implements Listener {
    private static final int MAX_FAVORITES=128;
    private static final class Preferences {
        String text="";double max=0;int sort=0;boolean onlyFavorites=false;
        final Set<Integer> favorites=new LinkedHashSet<Integer>();
    }
    private final KingGriefCore plugin;
    private final Map<UUID,Preferences> users=new HashMap<UUID,Preferences>();
    AuctionSearchManager(KingGriefCore plugin){this.plugin=plugin;}
    @EventHandler public void quit(PlayerQuitEvent event){users.remove(event.getPlayer().getUniqueId());}
    private Preferences get(Player player){
        Preferences cached=users.get(player.getUniqueId());if(cached!=null)return cached;
        Preferences p=new Preferences();
        try{
            List<Integer> saved=plugin.getStore().inspect(data->data.getIntegerList("auctionPreferences."+player.getUniqueId()+".favorites"));
            for(Integer id:saved)if(id!=null&&id>0&&p.favorites.size()<MAX_FAVORITES)p.favorites.add(id);
        }catch(Exception error){plugin.getLogger().warning("[Auction] Could not load favorites: "+error.getMessage());}
        users.put(player.getUniqueId(),p);return p;
    }
    List<DataStore.AuctionLot> results(Player player){
        Preferences p=get(player);List<DataStore.AuctionLot> result=new ArrayList<DataStore.AuctionLot>();
        for(DataStore.AuctionLot lot:plugin.getStore().getAuctionLots()){
            if(lot.item==null||lot.item.getType().isAir()||!Double.isFinite(lot.price)||lot.price<=0)continue;
            if(p.max>0&&lot.price>p.max)continue;
            if(p.onlyFavorites&&!p.favorites.contains(lot.id))continue;
            if(!p.text.isEmpty()&&!matches(lot.item,p.text))continue;
            result.add(lot);
        }
        Comparator<DataStore.AuctionLot> tie=Comparator.comparingInt((DataStore.AuctionLot lot)->lot.id).reversed();
        if(p.sort==1)result.sort(Comparator.comparingDouble((DataStore.AuctionLot lot)->lot.price).thenComparing(tie));
        else if(p.sort==2)result.sort(Comparator.comparingDouble((DataStore.AuctionLot lot)->lot.price).reversed().thenComparing(tie));
        else result.sort(Comparator.comparingLong((DataStore.AuctionLot lot)->lot.createdAt).reversed().thenComparing(tie));
        return result;
    }
    private static boolean matches(ItemStack stack,String query){
        if(stack.getType().name().toLowerCase(Locale.ROOT).contains(query.replace(' ','_')))return true;
        ItemMeta meta=stack.getItemMeta();
        if(meta==null||!meta.hasDisplayName())return false;
        String display=ChatColor.stripColor(meta.getDisplayName());
        return display!=null&&display.toLowerCase(Locale.ROOT).contains(query);
    }
    String sortLabel(Player player){int n=get(player).sort;return n==1?"сначала дешёвые":n==2?"сначала дорогие":"сначала новые";}
    boolean favoritesOnly(Player player){return get(player).onlyFavorites;}
    boolean favorite(Player player,int id){return get(player).favorites.contains(id);}
    String filters(Player player){Preferences p=get(player);return (p.text.isEmpty()?"любой предмет":p.text)+", "+(p.max<=0?"любая цена":"до "+ColorUtil.fmtMoney(p.max))+", "+sortLabel(player)+(p.onlyFavorites?", избранное":"");}
    void cycleSort(Player player){Preferences p=get(player);p.sort=(p.sort+1)%3;}
    void toggleOnly(Player player){Preferences p=get(player);p.onlyFavorites=!p.onlyFavorites;}
    void clear(Player player){Preferences p=get(player);p.text="";p.max=0;p.sort=0;p.onlyFavorites=false;}
    boolean toggleFavorite(Player player,int id){
        Preferences p=get(player);boolean exists=p.favorites.contains(id);
        DataStore.AuctionLot lot=plugin.getStore().findAuctionLot(id);
        if(lot==null&&!exists){player.sendMessage(ColorUtil.c("&eЛот уже недоступен."));return false;}
        Set<Integer> next=new LinkedHashSet<Integer>(p.favorites);
        if(exists)next.remove(id);
        else {
            Set<Integer> activeIds=new java.util.HashSet<Integer>();
            for(DataStore.AuctionLot active:plugin.getStore().getAuctionLots())activeIds.add(active.id);
            next.removeIf(old -> !activeIds.contains(old));
            if(next.size()>=MAX_FAVORITES){player.sendMessage(ColorUtil.c("&cВ избранном не более "+MAX_FAVORITES+" активных лотов."));return false;}
            next.add(id);
        }
        try{plugin.getStore().transact(data->{data.set("auctionPreferences."+player.getUniqueId()+".favorites",new ArrayList<Integer>(next));return null;});
            p.favorites.clear();p.favorites.addAll(next);
            player.sendMessage(ColorUtil.c(exists?"&7Лот #"+id+" удалён из избранного.":"&#FFD740Лот #"+id+" добавлен в избранное."));return true;
        }catch(Exception error){player.sendMessage(ColorUtil.c("&cНе удалось сохранить избранное."));return false;}
    }
    /** Returns true only when this is a search/filter subcommand. */
    boolean command(Player player,String[] args){
        if(args.length==0)return false;
        String sub=args[0].toLowerCase(Locale.ROOT);Preferences p=get(player);
        if("search".equals(sub)){
            if(args.length<2){player.sendMessage(ColorUtil.c("&e/ah search <материал или название>"));return true;}
            String query=String.join(" ",java.util.Arrays.copyOfRange(args,1,args.length)).trim().toLowerCase(Locale.ROOT);
            if(query.isEmpty()||query.length()>48||query.indexOf('§')>=0||query.indexOf('&')>=0){player.sendMessage(ColorUtil.c("&cПоиск: от 1 до 48 символов без цветовых кодов."));return true;}
            p.text=query;plugin.getGuis().openAuctionMenu(player,0);return true;
        }
        if("price".equals(sub)){
            if(args.length!=2){player.sendMessage(ColorUtil.c("&e/ah price <максимум|off>"));return true;}
            if("off".equalsIgnoreCase(args[1]))p.max=0;
            else try{double value=Double.parseDouble(args[1]);if(!Double.isFinite(value)||value<=0||value>1_000_000_000_000.0)throw new NumberFormatException();p.max=value;}
                catch(NumberFormatException error){player.sendMessage(ColorUtil.c("&cУкажите положительную цену до 1 000 000 000 000 или off."));return true;}
            plugin.getGuis().openAuctionMenu(player,0);return true;
        }
        if("sort".equals(sub)){
            if(args.length!=2){player.sendMessage(ColorUtil.c("&e/ah sort <new|cheap|expensive>"));return true;}
            String v=args[1].toLowerCase(Locale.ROOT);
            if("new".equals(v))p.sort=0;else if("cheap".equals(v))p.sort=1;else if("expensive".equals(v))p.sort=2;
            else {player.sendMessage(ColorUtil.c("&e/ah sort <new|cheap|expensive>"));return true;}
            plugin.getGuis().openAuctionMenu(player,0);return true;
        }
        if("favorites".equals(sub)){p.onlyFavorites=!p.onlyFavorites;plugin.getGuis().openAuctionMenu(player,0);return true;}
        if("clear".equals(sub)){clear(player);plugin.getGuis().openAuctionMenu(player,0);return true;}
        if("favorite".equals(sub)){
            if(args.length!=2){player.sendMessage(ColorUtil.c("&e/ah favorite <ID>"));return true;}
            try{toggleFavorite(player,Integer.parseInt(args[1]));}catch(NumberFormatException error){player.sendMessage(ColorUtil.c("&cУкажите числовой ID лота."));}return true;
        }
        return false;
    }
}
