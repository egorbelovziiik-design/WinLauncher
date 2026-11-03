package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.UUID;

/** A persistent, cosmetic-only collection built on existing Core progress. */
final class AchievementManager implements Listener {
    private enum Badge {
        ORE_25("ore_25", "Первая жила", "Добудьте 25 блоков руды.", Material.IRON_PICKAXE, 25, 10, false),
        ORE_250("ore_250", "Зимний шахтёр", "Добудьте 250 блоков руды.", Material.DIAMOND_PICKAXE, 250, 11, false),
        PARKOUR("parkour", "Вершина без возврата", "Завершите рейтинговый забег по паркуру.", Material.FEATHER, 1, 13, false),
        BOSS("boss", "Гроза босса", "Станьте лидером по урону в событии с боссом.", Material.NETHER_STAR, 1, 15, false),
        TREE_1("tree_1", "Помощник ёлки", "Внесите хотя бы одну снежинку в общую ёлку.", Material.SPRUCE_SAPLING, 1, 28, true),
        TREE_10("tree_10", "Украшатель ёлки", "Внесите десять снежинок в общую ёлку.", Material.SNOWBALL, 10, 30, true),
        ORE_1000("ore_1000", "Мастер жил", "Добудьте 1000 блоков руды.", Material.NETHERITE_PICKAXE, 1000, 12, false, false),
        ORE_5000("ore_5000", "Легенда шахт", "Добудьте 5000 блоков руды.", Material.DIAMOND_BLOCK, 5000, 14, false, false),
        GIFTS_1("gifts_1", "Первый сюрприз", "Найдите один подарок на спавне.", Material.CHEST, 1, 16, false, false),
        GIFTS_5("gifts_5", "Охотник за подарками", "Найдите пять подарков на спавне.", Material.GOLD_BLOCK, 5, 19, false, false),
        GIFTS_10("gifts_10", "Секреты спавна", "Найдите все десять подарков.", Material.ENDER_CHEST, 10, 20, false, false),
        FISH_5("fish_5", "Зимний рыбак", "Поймайте пять рыб с зимним бонусом.", Material.COD, 5, 21, false, false),
        FISH_25("fish_25", "Покоритель льдов", "Поймайте 25 рыб с зимним бонусом.", Material.FISHING_ROD, 25, 22, false, false),
        PLAY_1("play_1", "Первый час", "Наиграйте один час на сервере.", Material.CLOCK, 1, 23, false, false),
        PLAY_24("play_24", "День приключений", "Наиграйте 24 часа на сервере.", Material.COMPASS, 24, 24, false, false),
        PLAY_168("play_168", "Неделя приключений", "Наиграйте 168 часов на сервере.", Material.NETHER_STAR, 168, 25, false, false);
        final String id, title, description;
        final Material icon;
        final int goal, slot;
        final boolean seasonal, requiredForTitle;
        Badge(String id,String title,String description,Material icon,int goal,int slot,boolean seasonal){
            this(id,title,description,icon,goal,slot,seasonal,!seasonal);
        }
        Badge(String id,String title,String description,Material icon,int goal,int slot,boolean seasonal,boolean requiredForTitle){
            this.id=id;this.title=title;this.description=description;this.icon=icon;this.goal=goal;this.slot=slot;
            this.seasonal=seasonal;this.requiredForTitle=requiredForTitle;
        }
    }
    private static final class Snapshot {
        final int[] progress=new int[Badge.values().length];
        final boolean[] claimed=new boolean[Badge.values().length];
        boolean title;
        boolean allCore(){
            for(Badge badge:Badge.values())if(badge.requiredForTitle&&!claimed[badge.ordinal()])return false;
            return true;
        }
        int count(){int n=0;for(boolean value:claimed)if(value)n++;return n;}
        int coreCount(){int n=0;for(Badge badge:Badge.values())if(badge.requiredForTitle&&claimed[badge.ordinal()])n++;return n;}
    }
    public static final class AchievementHolder implements InventoryHolder {
        final UUID owner;
        Inventory inventory;
        AchievementHolder(UUID owner){this.owner=owner;}
        @Override public Inventory getInventory(){return inventory;}
    }
    private static final class Result {
        final int badges;
        final boolean title;
        Result(int badges,boolean title){this.badges=badges;this.title=title;}
    }
    private final KingGriefCore plugin;
    AchievementManager(KingGriefCore plugin){this.plugin=plugin;}

    private static String root(UUID id){return "achievements."+id+".claimed.";}
    private static String playerRoot(Player player){return "players."+player.getName().toLowerCase(Locale.ROOT);}
    private static Snapshot snapshot(YamlConfiguration data,Player player){
        Snapshot snap=new Snapshot();UUID id=player.getUniqueId();String name=player.getName().toLowerCase(Locale.ROOT);
        int ore=(int)Math.min(Integer.MAX_VALUE,Math.max(0L,data.getLong("utility.mining."+id+".total",0L)));
        // A season rollover archives donors, but does not erase their lifetime badge progress.
        int donated=TreeSeasonStore.lifetimeProgress(data,name);
        long parkour=data.getLong("parkour.records."+id+".all",0L);
        int gifts=0;
        for(int i=0;i<10;i++)if(data.getBoolean(playerRoot(player)+".spawnGifts.g"+i,false))gifts++;
        int fish=(int)Math.min(Integer.MAX_VALUE,Math.max(0L,data.getLong("winterFishing."+id+".total",0L)));
        int hours=(int)Math.min(Integer.MAX_VALUE,Math.max(0L,data.getLong(playerRoot(player)+".playSec",0L))/3600L);
        for(Badge badge:Badge.values()){
            int value;
            switch(badge){
                case ORE_25:case ORE_250:case ORE_1000:case ORE_5000:value=ore;break;
                case TREE_1:case TREE_10:value=donated;break;
                case GIFTS_1:case GIFTS_5:case GIFTS_10:value=gifts;break;
                case FISH_5:case FISH_25:value=fish;break;
                case PLAY_1:case PLAY_24:case PLAY_168:value=hours;break;
                case PARKOUR:value=parkour>=ParkourRecordsManager.MIN_TIME_MS&&parkour<=ParkourRecordsManager.MAX_TIME_MS?1:0;break;
                case BOSS:value=data.getBoolean(playerRoot(player)+".titles.boss_slayer",false)?1:0;break;
                default:value=0;
            }
            snap.progress[badge.ordinal()]=Math.min(badge.goal,value);
            snap.claimed[badge.ordinal()]=data.getBoolean(root(id)+badge.id,false);
        }
        snap.title=data.getBoolean(playerRoot(player)+".titles.king_achiever",false);
        return snap;
    }
    private boolean allowed(Player player){return player!=null&&player.isOnline()&&plugin.getStore().isAuthenticated(player);}

    void command(Player player,String[] args){
        if(args.length==0){open(player);return;}
        if(args.length==1&&"claim".equalsIgnoreCase(args[0])){claim(player,null);return;}
        if(args.length==1&&"status".equalsIgnoreCase(args[0])){status(player);return;}
        player.sendMessage(ColorUtil.c("&e/achievements &7или &e/achievements status &7или &e/achievements claim"));
    }
    private void status(Player player){
        if(!allowed(player)){player.sendMessage(ColorUtil.c("&cСначала войдите в аккаунт."));return;}
        try{
            Snapshot snap=plugin.getStore().inspect(data->snapshot(data,player));int ready=0;
            for(Badge badge:Badge.values())if(!snap.claimed[badge.ordinal()]&&snap.progress[badge.ordinal()]>=badge.goal)ready++;
            player.sendMessage(ColorUtil.c("&#80D8FF✦ Достижения: &f"+snap.count()+"/"+Badge.values().length+" &8| &7готово к закреплению: &a"+ready
                    +" &8| &7основных для титула: &f"+snap.coreCount()+"/4"));
        }catch(Exception error){player.sendMessage(ColorUtil.c("&cНе удалось прочитать достижения."));}
    }
    void open(Player player){
        if(!allowed(player)){player.sendMessage(ColorUtil.c("&cСначала войдите в аккаунт."));return;}
        try{
            Snapshot snap=plugin.getStore().inspect(data->snapshot(data,player));
            AchievementHolder holder=new AchievementHolder(player.getUniqueId());
            Inventory inventory=Bukkit.createInventory(holder,45,ColorUtil.c("&#80D8FF&l✦ ДОСТИЖЕНИЯ KINGGRIEF"));holder.inventory=inventory;
            ItemStack frame=ItemFactory.icon(Material.LIGHT_BLUE_STAINED_GLASS_PANE," ");
            for(int slot=0;slot<45;slot++)if(slot<9||slot>=36||slot%9==0||slot%9==8)inventory.setItem(slot,frame);
            inventory.setItem(4,ItemFactory.glowIcon(Material.BOOK,"&#FFD740&lКОЛЛЕКЦИЯ ДОСТИЖЕНИЙ",
                    "&7Закреплено: &f"+snap.count()+" / "+Badge.values().length,
                    "&7Нажмите на готовый значок, чтобы сохранить его.","&8Дополнительные и сезонные значки не обязательны для титула."));
            for(Badge badge:Badge.values()){
                int index=badge.ordinal(),progress=snap.progress[index];
                boolean claimed=snap.claimed[index],ready=progress>=badge.goal;
                String state=claimed?"&#00E676✔ Закреплено":ready?"&#FFD740▸ Нажмите, чтобы закрепить":"&7В процессе";
                String detail=badge.seasonal?"&8Сезонное: общая ёлка принимает до 300 снежинок.":
                        badge.id.startsWith("ore_")?"&8Счётчик включает ранее поставленную игроками руду.":
                        badge==Badge.PARKOUR?"&8Рейтинг использует эвристику, не полноценный античит.":
                        badge==Badge.BOSS?"&8Засчитывается награда лидеру босса.":
                        badge.id.startsWith("gifts_")?"&8Прогресс: /gifts.":
                        badge.id.startsWith("fish_")?"&8Только рыба, учтённая в /winterfish.":"&8Прогресс: /playtime status.";
                inventory.setItem(badge.slot,ItemFactory.icon(badge.icon,"&#80D8FF&l"+badge.title,
                        "&7"+badge.description,"&7Прогресс: &f"+progress+" / "+badge.goal,
                        progressBar(progress,badge.goal),detail,"",state));
            }
            inventory.setItem(32,ItemFactory.glowIcon(Material.CHEST,"&#FFD740&lЗАКРЕПИТЬ ВСЁ ГОТОВОЕ",
                    "&7Сохранить все выполненные достижения.","&7То же действие: &f/achievements claim"));
            inventory.setItem(34,ItemFactory.icon(snap.title?Material.NAME_TAG:Material.PAPER,"&#FFD740&lТИТУЛ «ИСКАТЕЛЬ»",
                    "&7Закрепите четыре основных достижения.","&7Готово: &f"+snap.coreCount()+" / 4",
                    snap.title?"&#00E676✔ Открыт в /titles":"&7Только косметика: без монет и предметов."));
            inventory.setItem(40,ItemFactory.icon(Material.BARRIER,"&cЗакрыть"));
            player.openInventory(inventory);
        }catch(Exception error){player.sendMessage(ColorUtil.c("&cНе удалось прочитать достижения."));}
    }
    private static String progressBar(int value,int goal){
        int filled=Math.min(10,(int)((long)value*10/goal));
        StringBuilder bar=new StringBuilder("&#00E676");
        for(int i=0;i<10;i++){if(i==filled)bar.append("&8");bar.append('■');}
        return bar.toString();
    }
    private void claim(Player player,Badge selected){
        if(!allowed(player)){player.sendMessage(ColorUtil.c("&cСначала войдите в аккаунт."));return;}
        try{
            Snapshot before=plugin.getStore().inspect(data->snapshot(data,player));
            boolean any=false;
            for(Badge badge:Badge.values())if((selected==null||selected==badge)&&!before.claimed[badge.ordinal()]&&before.progress[badge.ordinal()]>=badge.goal)any=true;
            if(!any&&(!before.allCore()||before.title)){
                player.sendMessage(ColorUtil.c("&eНовых готовых достижений нет."));return;
            }
            Result result=plugin.getStore().transact(data->{
                Snapshot current=snapshot(data,player);int count=0;
                for(Badge badge:Badge.values())if((selected==null||selected==badge)&&!current.claimed[badge.ordinal()]&&current.progress[badge.ordinal()]>=badge.goal){
                    data.set(root(player.getUniqueId())+badge.id,true);current.claimed[badge.ordinal()]=true;count++;
                }
                boolean title=current.allCore()&&!current.title;
                if(title)data.set(playerRoot(player)+".titles.king_achiever",true);
                return new Result(count,title);
            });
            player.sendMessage(ColorUtil.c("&#00E676Закреплено достижений: &f"+result.badges+(result.title?" &8• &#FFD740титул «Искатель» открыт в /titles":"")));
            if(result.badges>0||result.title)ColorUtil.sound(player,"entity.player.levelup",.7f,1.3f);
        }catch(Exception error){
            plugin.getLogger().warning("[Achievements] Claim was not committed: "+error.getMessage());
            player.sendMessage(ColorUtil.c("&cНе удалось сохранить достижения. Изменения не применены."));
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void click(InventoryClickEvent event){
        Inventory top=event.getView().getTopInventory();
        if(!(top.getHolder() instanceof AchievementHolder))return;
        boolean previouslyCancelled=event.isCancelled();event.setCancelled(true);
        if(previouslyCancelled||!(event.getWhoClicked() instanceof Player)||!top.equals(event.getClickedInventory()))return;
        if(event.getClick()!=ClickType.LEFT&&event.getClick()!=ClickType.RIGHT)return;
        Player player=(Player)event.getWhoClicked();AchievementHolder holder=(AchievementHolder)top.getHolder();
        if(!holder.owner.equals(player.getUniqueId())||!allowed(player))return;
        int slot=event.getRawSlot();Badge selected=null;
        for(Badge badge:Badge.values())if(badge.slot==slot)selected=badge;
        if(slot!=40&&slot!=32&&selected==null)return;
        final Badge chosen=selected;
        Bukkit.getScheduler().runTask(plugin,()->{
            if(!allowed(player)||player.getOpenInventory().getTopInventory().getHolder()!=holder)return;
            if(slot==40){player.closeInventory();return;}
            claim(player,chosen);open(player);
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event){
        if(event.getView().getTopInventory().getHolder() instanceof AchievementHolder)event.setCancelled(true);
    }
}
