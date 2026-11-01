package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
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
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** An opt-in announcement calendar. It does not start events, teleport, or edit worlds. */
final class EventCalendarManager implements Listener {
    private static final String ROOT="eventCalendar.entries";
    private static final int MAX_ENTRIES=64, PAGE_SIZE=28;
    private static final long MIN_LEAD=60_000L, MAX_LEAD=90L*24L*60L*60L*1000L;
    private static final DateTimeFormatter SHOW=DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss 'UTC'",Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final int[] SLOTS={10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
    private enum Kind {
        PARKOUR("parkour","Паркур",Material.FEATHER,"/warp parkour"),
        DUEL("duel","Дуэли",Material.IRON_SWORD,"/duel <ник> 0"),
        MEMORY("memory","Ледяная память",Material.SNOWBALL,"/snowmemory"),
        BOSS("boss","Босс-ивент",Material.NETHER_STAR,"/events"),
        BUILD("build","Конкурс построек",Material.BRICKS,"/buildcontest"),
        FISH("fish","Зимняя рыбалка",Material.FISHING_ROD,"/winterfish"),
        FORGE("forge","Ледяная кузница",Material.ANVIL,"/frostforge");
        final String id,label,join;final Material icon;
        Kind(String id,String label,Material icon,String join){this.id=id;this.label=label;this.icon=icon;this.join=join;}
        static Kind parse(String value){if(value!=null)for(Kind k:values())if(k.id.equalsIgnoreCase(value))return k;return null;}
    }
    private static final class Entry {
        final String id;final Kind kind;final long at;
        Entry(String id,Kind kind,long at){this.id=id;this.kind=kind;this.at=at;}
    }
    private static final class Menu implements InventoryHolder {
        final UUID viewer;final int page;final String[] ids=new String[PAGE_SIZE];Inventory inventory;
        Menu(UUID viewer,int page){this.viewer=viewer;this.page=page;}
        @Override public Inventory getInventory(){return inventory;}
    }
    private static final class Rejected extends Exception {
        Rejected(String message){super(message);}
    }
    private final KingGriefCore plugin;
    private final Map<String,Integer> stages=new HashMap<String,Integer>();
    private BukkitRunnable ticker;private boolean readWarning;
    EventCalendarManager(KingGriefCore plugin){this.plugin=plugin;}
    void start(){ticker=new BukkitRunnable(){@Override public void run(){tick(System.currentTimeMillis());}};ticker.runTaskTimer(plugin,100L,200L);}
    void shutdown(){
        if(ticker!=null)ticker.cancel();stages.clear();
        for(Player p:Bukkit.getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof Menu)p.closeInventory();
    }
    void command(CommandSender sender,String[] args,boolean admin){
        if(sender instanceof Player&&!plugin.getStore().isAuthenticated((Player)sender)){send(sender,"&cСначала войдите в аккаунт.");return;}
        String sub=args.length==0?"menu":args[0].toLowerCase(Locale.ROOT);
        if("add".equals(sub)){
            if(!admin){send(sender,"&cПланировать события могут OWNER, ТЕХ.АДМИН и консоль.");return;}
            if(args.length!=3){send(sender,"&e/calendar add <parkour|duel|memory|boss|build|fish|forge> <2026-12-31T18:00Z>");return;}
            Kind kind=Kind.parse(args[1]);
            if(kind==null){send(sender,"&cНеизвестный тип события.");return;}
            long at;
            try{at=OffsetDateTime.parse(args[2],DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli();}
            catch(DateTimeParseException|ArithmeticException invalid){send(sender,"&cДата неверна. Укажите время с зоной: 2026-12-31T18:00Z или 2026-12-31T21:00+03:00.");return;}
            long now=System.currentTimeMillis();
            if(at<now+MIN_LEAD||at>now+MAX_LEAD){send(sender,"&cВремя должно быть от 1 минуты до 90 дней в будущем.");return;}
            try{
                String id=plugin.getStore().transact(data->{
                    ConfigurationSection root=data.getConfigurationSection(ROOT);
                    if(root!=null&&root.getKeys(false).size()>=MAX_ENTRIES)throw new Rejected("Лимит 64 записи. Удалите старые: /calendar prune confirm");
                    String key=null;
                    for(int attempt=0;attempt<16;attempt++){
                        String candidate=UUID.randomUUID().toString().substring(0,8);
                        if(!data.contains(ROOT+"."+candidate)){key=candidate;break;}
                    }
                    if(key==null)throw new Rejected("Не удалось выбрать идентификатор; попробуйте ещё раз.");
                    data.set(ROOT+"."+key+".kind",kind.id);data.set(ROOT+"."+key+".at",at);
                    return key;
                });
                send(sender,"&#00E676Добавлено: &f"+kind.label+" &8• &f"+SHOW.format(Instant.ofEpochMilli(at))+" &8["+id+"]");
                send(sender,"&7Календарь отправляет напоминания, но не запускает мероприятие автоматически.");
            }catch(Rejected rejected){send(sender,"&c"+rejected.getMessage());}
            catch(Exception error){failure(sender,"сохранить событие",error);}
            return;
        }
        if("remove".equals(sub)){
            if(!admin){send(sender,"&cИзменять календарь могут OWNER, ТЕХ.АДМИН и консоль.");return;}
            if(args.length!=3||!args[1].matches("[0-9a-fA-F]{8}")||!"confirm".equalsIgnoreCase(args[2])){send(sender,"&e/calendar remove <8-значный id> confirm");return;}
            String id=args[1].toLowerCase(Locale.ROOT);
            try{
                boolean exists=plugin.getStore().inspect(data->data.isConfigurationSection(ROOT+"."+id));
                if(!exists){send(sender,"&eЗапись не найдена.");return;}
                plugin.getStore().transact(data->{if(!data.isConfigurationSection(ROOT+"."+id))throw new Rejected("Запись не найдена.");data.set(ROOT+"."+id,null);return null;});
                stages.remove(id);send(sender,"&#00E676Запись "+id+" удалена.");
            }catch(Rejected rejected){send(sender,"&c"+rejected.getMessage());}
            catch(Exception error){failure(sender,"удалить событие",error);}
            return;
        }
        if("prune".equals(sub)){
            if(!admin){send(sender,"&cИзменять календарь могут OWNER, ТЕХ.АДМИН и консоль.");return;}
            if(args.length!=2||!"confirm".equalsIgnoreCase(args[1])){send(sender,"&e/calendar prune confirm &7— удалить прошедшие записи.");return;}
            long now=System.currentTimeMillis();
            try{
                List<Entry> expired=readEntries();expired.removeIf(entry->entry.at>=now);
                if(expired.isEmpty()){send(sender,"&7Прошедших корректных записей нет.");return;}
                int removed=plugin.getStore().transact(data->{int n=0;for(Entry entry:expired){
                    String path=ROOT+"."+entry.id;
                    if(data.isConfigurationSection(path)&&data.getLong(path+".at",0)==entry.at){data.set(path,null);n++;}
                }return n;});
                for(Entry entry:expired)stages.remove(entry.id);
                send(sender,"&#00E676Удалено прошедших записей: &f"+removed+"&7. Некорректные записи не удаляются автоматически.");
            }catch(Exception error){failure(sender,"очистить календарь",error);}
            return;
        }
        if("menu".equals(sub)&&args.length<=1){
            if(sender instanceof Player){open((Player)sender,0);plugin.getUtilities().calendar((Player)sender);}else list(sender,0);
            return;
        }
        if("list".equals(sub)&&args.length<=2){
            int page=1;if(args.length==2)try{page=Integer.parseInt(args[1]);}catch(NumberFormatException invalid){page=0;}
            if(page<1||page>1000){send(sender,"&e/calendar list [номер страницы]");return;}list(sender,page-1);return;
        }
        send(sender,"&e/calendar &7— меню; &e/calendar list [страница] &7— список. Управление: add, remove, prune.");
    }
    private static void send(CommandSender sender,String message){sender.sendMessage(ColorUtil.c(message));}
    private void failure(CommandSender sender,String action,Exception error){
        send(sender,"&cНе удалось "+action+". Данные не изменены; обратитесь к администрации.");
        plugin.getLogger().warning("[Calendar] Could not "+action+": "+error.getMessage());
    }
    private List<Entry> readEntries() throws Exception {
        return plugin.getStore().inspect(data->{
            List<Entry> result=new ArrayList<Entry>();ConfigurationSection root=data.getConfigurationSection(ROOT);
            if(root!=null){int inspected=0;for(String id:root.getKeys(false)){
                if(inspected++>=MAX_ENTRIES)break;
                if(!id.matches("[0-9a-f]{8}")||!root.isConfigurationSection(id))continue;
                Kind kind=Kind.parse(root.getString(id+".kind"));long at=root.getLong(id+".at",0);
                if(kind!=null&&at>0&&at<=4_102_444_800_000L)result.add(new Entry(id,kind,at));
            }}
            return result;
        });
    }
    private List<Entry> sortedEntries(long now) throws Exception {
        List<Entry> entries=readEntries();
        entries.sort(Comparator.comparingInt((Entry e)->e.at>=now?0:1).thenComparingLong(e->e.at).thenComparing(e->e.id));return entries;
    }
    private void list(CommandSender sender,int requested){
        try{
            List<Entry> entries=sortedEntries(System.currentTimeMillis());int pages=Math.max(1,(entries.size()+7)/8),page=Math.min(requested,pages-1);
            send(sender,"&#80D8FF✦ Календарь &8("+(page+1)+"/"+pages+", UTC)");
            if(entries.isEmpty())send(sender,"&7Запланированных объявлений нет. Текущий статус: /events");
            int end=Math.min(entries.size(),(page+1)*8);
            for(int i=page*8;i<end;i++){
                Entry e=entries.get(i);String state=e.at<System.currentTimeMillis()?" &8(время прошло)":"";
                send(sender," &8• &f"+e.kind.label+" &8— &b"+SHOW.format(Instant.ofEpochMilli(e.at))+state+" &8["+e.id+"]");
            }
            send(sender,"&7Это объявления: автоматического запуска и телепорта нет.");
        }catch(Exception error){failure(sender,"прочитать календарь",error);}
    }
    void open(Player p,int requested){
        if(!plugin.getStore().isAuthenticated(p)){send(p,"&cСначала войдите в аккаунт.");return;}
        if(p.getItemOnCursor()!=null&&!p.getItemOnCursor().getType().isAir()){
            send(p,"&eСначала положите предмет с курсора в инвентарь.");return;
        }
        try{
            long now=System.currentTimeMillis();List<Entry> entries=sortedEntries(now);
            int pages=Math.max(1,(entries.size()+PAGE_SIZE-1)/PAGE_SIZE),page=Math.max(0,Math.min(requested,pages-1));
            Menu menu=new Menu(p.getUniqueId(),page);menu.inventory=Bukkit.createInventory(menu,54,ColorUtil.c("&#80D8FF&l❄ КАЛЕНДАРЬ СОБЫТИЙ"));
            ItemStack frame=ItemFactory.icon(Material.LIGHT_BLUE_STAINED_GLASS_PANE," ");
            for(int i=0;i<54;i++)menu.inventory.setItem(i,frame);
            menu.inventory.setItem(4,ItemFactory.glowIcon(Material.CLOCK,"&#FFD740Расписание &8("+(page+1)+"/"+pages+")",
                    "&7Время показано в UTC.","&7Календарь напоминает, но не запускает","&7события автоматически. Статус: /events"));
            if(entries.isEmpty())menu.inventory.setItem(22,ItemFactory.icon(Material.PAPER,"&eПока нет запланированных объявлений","&7Администрация может добавить их через /calendar add."));
            int start=page*PAGE_SIZE,end=Math.min(entries.size(),start+PAGE_SIZE);
            for(int i=start;i<end;i++){
                Entry entry=entries.get(i);String status=entry.at<now?"&8Запланированное время прошло":"&aПредстоит";
                menu.ids[i-start]=entry.id;
                menu.inventory.setItem(SLOTS[i-start],ItemFactory.icon(entry.kind.icon,"&#80D8FF"+entry.kind.label,
                        "&7Дата: &f"+SHOW.format(Instant.ofEpochMilli(entry.at)),status,
                        "&7Информация и участие: &e"+entry.kind.join,"&8ID: "+entry.id,"&7Нажмите, чтобы увидеть команду."));
            }
            if(page>0)menu.inventory.setItem(45,ItemFactory.icon(Material.ARROW,"&e« Предыдущая страница"));
            if(page+1<pages)menu.inventory.setItem(53,ItemFactory.icon(Material.ARROW,"&eСледующая страница »"));
            menu.inventory.setItem(49,ItemFactory.icon(Material.BARRIER,"&cЗакрыть"));p.openInventory(menu.inventory);
        }catch(Exception error){failure(p,"открыть календарь",error);}
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void click(InventoryClickEvent event){
        Inventory top=event.getView().getTopInventory();if(!(top.getHolder() instanceof Menu))return;
        Menu menu=(Menu)top.getHolder();boolean blocked=event.isCancelled();event.setCancelled(true);
        if(blocked||!(event.getWhoClicked() instanceof Player)||!event.getWhoClicked().getUniqueId().equals(menu.viewer)
                ||(event.getClick()!=ClickType.LEFT&&event.getClick()!=ClickType.RIGHT))return;
        Player p=(Player)event.getWhoClicked();int slot=event.getRawSlot();
        if(slot==49||slot==45||slot==53){
            Bukkit.getScheduler().runTask(plugin,()->{
                if(!p.isOnline()||p.getOpenInventory().getTopInventory().getHolder()!=menu)return;
                if(slot==49)p.closeInventory();else if(slot==45&&menu.page>0)open(p,menu.page-1);else if(slot==53)open(p,menu.page+1);
            });return;
        }
        int index=-1;for(int i=0;i<SLOTS.length;i++)if(SLOTS[i]==slot){index=i;break;}
        if(index<0||menu.ids[index]==null)return;
        String id=menu.ids[index];
        try{
            Entry selected=null;for(Entry entry:readEntries())if(entry.id.equals(id)){selected=entry;break;}
            if(selected==null){send(p,"&eЭту запись уже удалили. Откройте календарь заново.");return;}
            send(p,"&#80D8FF"+selected.kind.label+" &8• &f"+SHOW.format(Instant.ofEpochMilli(selected.at))+" &8• &e"+selected.kind.join);
            send(p,"&7Участие добровольное; запись в календаре не запускает событие.");
        }catch(Exception error){failure(p,"прочитать событие",error);}
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof Menu)event.setCancelled(true);}
    /** At most one grouped broadcast per check, even when many entries are scheduled together. */
    private void tick(long now){
        try{
            List<Entry> entries=readEntries();readWarning=false;Set<String> present=new HashSet<String>();List<String> notices=new ArrayList<String>();int total=0;
            for(Entry e:entries){
                present.add(e.id);long remaining=e.at-now;int stage=remaining>600_000L?0:remaining>60_000L?1:remaining>0?2:3;
                int before=stages.getOrDefault(e.id,0);if(stage<=before)continue;stages.put(e.id,stage);
                if(stage==0||remaining< -30_000L)continue;
                total++;if(notices.size()<3)notices.add(e.kind.label+(stage==1?" ≤10 мин":stage==2?" ≤1 мин":" сейчас"));
            }
            stages.keySet().retainAll(present);
            if(total>0){
                String extra=total>notices.size()?" &8(+"+(total-notices.size())+" в /calendar)":"";
                Bukkit.broadcastMessage(ColorUtil.c("&#80D8FF✦ Календарь: &f"+String.join("&8, &f",notices)+extra+" &7— объявления, без автозапуска. /calendar"));
            }
        }catch(Exception error){if(!readWarning){readWarning=true;plugin.getLogger().warning("[Calendar] Reminders paused: "+error.getMessage());}}
    }
}
