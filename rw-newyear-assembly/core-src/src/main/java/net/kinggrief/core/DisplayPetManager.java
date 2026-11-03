package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerHideEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Cosmetic pets use non-persistent block displays, not mobs, armor stands or world blocks. */
final class DisplayPetManager implements Listener {
    private static final class Part {
        final Material material; final float x,y,z,w,h,d; final int wing;
        Part(Material m,float x,float y,float z,float w,float h,float d) { this(m,x,y,z,w,h,d,0); }
        Part(Material m,float x,float y,float z,float w,float h,float d,int wing) {
            material=m;this.x=x;this.y=y;this.z=z;this.w=w;this.h=h;this.d=d;this.wing=wing;
        }
    }
    private static Part part(Material m,float x,float y,float z,float w,float h,float d) {return new Part(m,x,y,z,w,h,d);}
    private static final Part[] PENGUIN={
        part(Material.BLACK_CONCRETE,0,.18f,0,.46f,.55f,.32f),
        part(Material.WHITE_CONCRETE,0,.22f,.167f,.30f,.38f,.035f),
        part(Material.BLACK_CONCRETE,0,.70f,0,.42f,.40f,.36f),
        part(Material.WHITE_CONCRETE,-.105f,.91f,.185f,.09f,.08f,.025f),
        part(Material.WHITE_CONCRETE,.105f,.91f,.185f,.09f,.08f,.025f),
        part(Material.ORANGE_CONCRETE,0,.78f,.22f,.14f,.10f,.13f),
        part(Material.BLACK_CONCRETE,-.29f,.33f,0,.13f,.38f,.20f),
        part(Material.BLACK_CONCRETE,.29f,.33f,0,.13f,.38f,.20f),
        part(Material.ORANGE_CONCRETE,-.13f,.04f,.07f,.19f,.10f,.28f),
        part(Material.ORANGE_CONCRETE,.13f,.04f,.07f,.19f,.10f,.28f)
    };
    private static final Part[] DEER={
        part(Material.BROWN_CONCRETE,0,.35f,0,.38f,.38f,.68f),
        part(Material.BROWN_CONCRETE,0,.53f,.31f,.31f,.43f,.24f),
        part(Material.BROWN_CONCRETE,0,.78f,.42f,.35f,.32f,.34f),
        part(Material.LIGHT_GRAY_CONCRETE,0,.81f,.60f,.24f,.13f,.05f),
        part(Material.BLACK_CONCRETE,-.12f,.98f,.58f,.05f,.05f,.025f),
        part(Material.BLACK_CONCRETE,.12f,.98f,.58f,.05f,.05f,.025f),
        part(Material.BROWN_CONCRETE,-.13f,.05f,-.22f,.11f,.32f,.11f),
        part(Material.BROWN_CONCRETE,.13f,.05f,-.22f,.11f,.32f,.11f),
        part(Material.BROWN_CONCRETE,-.13f,.05f,.23f,.11f,.32f,.11f),
        part(Material.BROWN_CONCRETE,.13f,.05f,.23f,.11f,.32f,.11f),
        part(Material.DARK_OAK_PLANKS,-.17f,1.10f,.39f,.07f,.26f,.07f),
        part(Material.DARK_OAK_PLANKS,.17f,1.10f,.39f,.07f,.26f,.07f),
        part(Material.DARK_OAK_PLANKS,-.24f,1.25f,.39f,.20f,.06f,.07f),
        part(Material.DARK_OAK_PLANKS,.24f,1.25f,.39f,.20f,.06f,.07f)
    };
    private static final Part[] DRAGON={
        part(Material.LIGHT_BLUE_CONCRETE,0,.32f,0,.42f,.36f,.64f),
        part(Material.CYAN_CONCRETE,0,.58f,.31f,.30f,.30f,.32f),
        part(Material.LIGHT_BLUE_CONCRETE,0,.62f,.52f,.32f,.24f,.25f),
        part(Material.BLUE_CONCRETE,-.11f,.81f,.47f,.055f,.055f,.025f),
        part(Material.BLUE_CONCRETE,.11f,.81f,.47f,.055f,.055f,.025f),
        part(Material.WHITE_CONCRETE,-.10f,.89f,.27f,.07f,.16f,.07f),
        part(Material.WHITE_CONCRETE,.10f,.89f,.27f,.07f,.16f,.07f),
        part(Material.LIGHT_BLUE_CONCRETE,0,.38f,-.45f,.14f,.14f,.34f),
        part(Material.CYAN_CONCRETE,0,.39f,-.67f,.09f,.09f,.18f),
        new Part(Material.LIGHT_BLUE_STAINED_GLASS,-.43f,.53f,-.02f,.44f,.07f,.48f,-1),
        new Part(Material.LIGHT_BLUE_STAINED_GLASS,.43f,.53f,-.02f,.44f,.07f,.48f,1),
        part(Material.CYAN_CONCRETE,-.16f,.16f,.17f,.10f,.18f,.13f),
        part(Material.CYAN_CONCRETE,.16f,.16f,.17f,.10f,.18f,.13f)
    };
    // Small block-display models: no resource pack, real mobs, collision, combat
    // effects, inventory or pathfinding. Positive Z is each model's face.
    private static final Part[] FOX={
        part(Material.ORANGE_CONCRETE,0,.25f,-.08f,.34f,.34f,.58f),
        part(Material.ORANGE_CONCRETE,0,.50f,.27f,.38f,.32f,.32f),
        part(Material.WHITE_CONCRETE,0,.47f,.46f,.26f,.16f,.09f),
        part(Material.BLACK_CONCRETE,0,.53f,.52f,.08f,.07f,.04f),
        part(Material.BLACK_CONCRETE,-.12f,.68f,.44f,.05f,.05f,.02f),
        part(Material.BLACK_CONCRETE,.12f,.68f,.44f,.05f,.05f,.02f),
        part(Material.ORANGE_CONCRETE,-.15f,.80f,.25f,.11f,.17f,.12f),
        part(Material.ORANGE_CONCRETE,.15f,.80f,.25f,.11f,.17f,.12f),
        part(Material.BLACK_CONCRETE,-.13f,.03f,-.25f,.09f,.23f,.10f),
        part(Material.BLACK_CONCRETE,.13f,.03f,-.25f,.09f,.23f,.10f),
        part(Material.BLACK_CONCRETE,-.13f,.03f,.15f,.09f,.23f,.10f),
        part(Material.BLACK_CONCRETE,.13f,.03f,.15f,.09f,.23f,.10f),
        part(Material.ORANGE_CONCRETE,0,.28f,-.52f,.22f,.22f,.32f),
        part(Material.WHITE_CONCRETE,0,.31f,-.72f,.21f,.18f,.12f)
    };
    private static final Part[] WOLF={
        part(Material.LIGHT_GRAY_CONCRETE,0,.25f,-.06f,.38f,.40f,.60f),
        part(Material.LIGHT_GRAY_CONCRETE,0,.48f,.30f,.38f,.36f,.34f),
        part(Material.WHITE_CONCRETE,0,.49f,.52f,.23f,.16f,.14f),
        part(Material.BLACK_CONCRETE,0,.57f,.60f,.09f,.07f,.03f),
        part(Material.BLACK_CONCRETE,-.12f,.70f,.48f,.05f,.05f,.02f),
        part(Material.BLACK_CONCRETE,.12f,.70f,.48f,.05f,.05f,.02f),
        part(Material.GRAY_CONCRETE,-.16f,.81f,.25f,.11f,.20f,.11f),
        part(Material.GRAY_CONCRETE,.16f,.81f,.25f,.11f,.20f,.11f),
        part(Material.RED_CONCRETE,0,.34f,.20f,.39f,.075f,.12f),
        part(Material.LIGHT_GRAY_CONCRETE,-.14f,.03f,-.27f,.10f,.23f,.10f),
        part(Material.LIGHT_GRAY_CONCRETE,.14f,.03f,-.27f,.10f,.23f,.10f),
        part(Material.LIGHT_GRAY_CONCRETE,-.14f,.03f,.14f,.10f,.23f,.10f),
        part(Material.LIGHT_GRAY_CONCRETE,.14f,.03f,.14f,.10f,.23f,.10f),
        part(Material.LIGHT_GRAY_CONCRETE,0,.49f,-.45f,.11f,.16f,.30f)
    };
    private static final Part[] BUNNY={
        part(Material.WHITE_CONCRETE,0,.14f,-.03f,.34f,.36f,.43f),
        part(Material.WHITE_CONCRETE,0,.46f,.19f,.34f,.32f,.30f),
        part(Material.WHITE_CONCRETE,-.11f,.73f,.16f,.10f,.29f,.10f),
        part(Material.WHITE_CONCRETE,.11f,.73f,.16f,.10f,.29f,.10f),
        part(Material.PINK_CONCRETE,-.11f,.78f,.22f,.045f,.19f,.015f),
        part(Material.PINK_CONCRETE,.11f,.78f,.22f,.045f,.19f,.015f),
        part(Material.BLACK_CONCRETE,-.10f,.64f,.35f,.05f,.05f,.025f),
        part(Material.BLACK_CONCRETE,.10f,.64f,.35f,.05f,.05f,.025f),
        part(Material.PINK_CONCRETE,0,.54f,.36f,.08f,.05f,.03f),
        part(Material.WHITE_CONCRETE,-.12f,.03f,.17f,.13f,.13f,.19f),
        part(Material.WHITE_CONCRETE,.12f,.03f,.17f,.13f,.13f,.19f),
        part(Material.WHITE_CONCRETE,0,.25f,-.30f,.14f,.14f,.14f)
    };
    private static final Part[] POLAR_BEAR={
        part(Material.WHITE_CONCRETE,0,.30f,-.08f,.52f,.45f,.68f),
        part(Material.WHITE_CONCRETE,0,.48f,.34f,.41f,.38f,.36f),
        part(Material.WHITE_CONCRETE,0,.50f,.57f,.23f,.17f,.12f),
        part(Material.BLACK_CONCRETE,0,.59f,.65f,.09f,.07f,.025f),
        part(Material.BLACK_CONCRETE,-.14f,.72f,.52f,.05f,.05f,.025f),
        part(Material.BLACK_CONCRETE,.14f,.72f,.52f,.05f,.05f,.025f),
        part(Material.WHITE_CONCRETE,-.17f,.82f,.27f,.12f,.13f,.12f),
        part(Material.WHITE_CONCRETE,.17f,.82f,.27f,.12f,.13f,.12f),
        part(Material.WHITE_CONCRETE,-.20f,.03f,-.30f,.14f,.28f,.15f),
        part(Material.WHITE_CONCRETE,.20f,.03f,-.30f,.14f,.28f,.15f),
        part(Material.WHITE_CONCRETE,-.20f,.03f,.20f,.14f,.28f,.15f),
        part(Material.WHITE_CONCRETE,.20f,.03f,.20f,.14f,.28f,.15f),
        part(Material.LIGHT_BLUE_CONCRETE,0,.36f,.20f,.53f,.08f,.10f)
    };
    private static final Part[] OWL={
        part(Material.BROWN_CONCRETE,0,.20f,0,.40f,.45f,.34f),
        part(Material.BROWN_CONCRETE,0,.60f,.03f,.43f,.32f,.34f),
        part(Material.WHITE_CONCRETE,-.11f,.70f,.21f,.14f,.13f,.025f),
        part(Material.WHITE_CONCRETE,.11f,.70f,.21f,.14f,.13f,.025f),
        part(Material.BLACK_CONCRETE,-.11f,.74f,.235f,.055f,.055f,.02f),
        part(Material.BLACK_CONCRETE,.11f,.74f,.235f,.055f,.055f,.02f),
        part(Material.ORANGE_CONCRETE,0,.63f,.26f,.09f,.09f,.08f),
        part(Material.BROWN_CONCRETE,-.15f,.91f,.02f,.10f,.12f,.10f),
        part(Material.BROWN_CONCRETE,.15f,.91f,.02f,.10f,.12f,.10f),
        new Part(Material.BROWN_CONCRETE,-.30f,.42f,-.01f,.20f,.09f,.34f,-1),
        new Part(Material.BROWN_CONCRETE,.30f,.42f,-.01f,.20f,.09f,.34f,1),
        part(Material.ORANGE_CONCRETE,-.12f,.04f,.04f,.10f,.16f,.16f),
        part(Material.ORANGE_CONCRETE,.12f,.04f,.04f,.10f,.16f,.16f)
    };
    private static final Part[] SNOWMAN={
        part(Material.SNOW_BLOCK,0,.04f,0,.45f,.42f,.40f),
        part(Material.SNOW_BLOCK,0,.44f,0,.34f,.34f,.32f),
        part(Material.BLACK_CONCRETE,-.09f,.64f,.17f,.05f,.05f,.025f),
        part(Material.BLACK_CONCRETE,.09f,.64f,.17f,.05f,.05f,.025f),
        part(Material.ORANGE_CONCRETE,0,.53f,.23f,.08f,.07f,.13f),
        part(Material.BLACK_CONCRETE,0,.28f,.21f,.055f,.055f,.025f),
        part(Material.BLACK_CONCRETE,0,.16f,.21f,.055f,.055f,.025f),
        part(Material.BLACK_CONCRETE,0,.78f,0,.40f,.06f,.36f),
        part(Material.BLACK_CONCRETE,0,.84f,0,.25f,.22f,.23f),
        part(Material.RED_CONCRETE,0,.81f,0,.27f,.06f,.25f),
        part(Material.DARK_OAK_PLANKS,-.30f,.37f,0,.23f,.06f,.06f),
        part(Material.DARK_OAK_PLANKS,.30f,.37f,0,.23f,.06f,.06f),
        part(Material.RED_CONCRETE,0,.43f,.01f,.37f,.07f,.34f)
    };
    private static final class Spec {
        final String id, name; final Material icon; final Part[] parts;
        Spec(String id,String name,Material icon,Part[] parts){this.id=id;this.name=name;this.icon=icon;this.parts=parts;}
    }
    private static final Spec[] SPECS={
        new Spec("penguin","Пингвин",Material.FEATHER,PENGUIN),
        new Spec("deer","Олень",Material.LEATHER,DEER),
        new Spec("dragon","Дракон",Material.DRAGON_EGG,DRAGON),
        new Spec("fox","Лисёнок",Material.FOX_SPAWN_EGG,FOX),
        new Spec("wolf","Волчонок",Material.WOLF_SPAWN_EGG,WOLF),
        new Spec("bunny","Зайчик",Material.RABBIT_SPAWN_EGG,BUNNY),
        new Spec("polar_bear","Белый медвежонок",Material.POLAR_BEAR_SPAWN_EGG,POLAR_BEAR),
        new Spec("owl","Сова",Material.ECHO_SHARD,OWL),
        new Spec("snowman","Снеговичок",Material.CARVED_PUMPKIN,SNOWMAN)
    };
    private static final int[] SLOTS={10,11,12,13,14,15,16,19,20};
    private static final class Pet {
        final String type; final World world; final Part[] model;
        final List<Entity> parts=new ArrayList<Entity>();
        final Set<UUID> viewers=new HashSet<UUID>();
        Location previous;
        Pet(Player p,Spec spec){world=p.getWorld();type=spec.id;model=spec.parts;}
    }
    private static final class PetMenu implements InventoryHolder {
        final UUID owner; Inventory inventory;
        PetMenu(UUID owner){this.owner=owner;}
        @Override public Inventory getInventory(){return inventory;}
    }
    private final KingGriefCore plugin;
    private final Map<UUID,String> selected=new HashMap<UUID,String>();
    // Insertion order permits lowering the cap without evicting every pet.
    private final Map<UUID,Pet> pets=new LinkedHashMap<UUID,Pet>();
    private final Map<UUID,Long> lastChoice=new HashMap<UUID,Long>();
    private final Map<UUID,Long> retrySpawn=new HashMap<UUID,Long>();
    private BukkitRunnable ticker;
    private long tick;
    DisplayPetManager(KingGriefCore plugin){this.plugin=plugin;}
    static List<String> choices(){
        List<String> result=new ArrayList<String>();
        result.add("menu");result.add("list");result.add("status");
        for(Spec spec:SPECS)result.add(spec.id);
        result.add("off");return result;
    }
    private static Spec spec(String type){for(Spec s:SPECS)if(s.id.equals(type))return s;return null;}
    private static boolean valid(String type){return "off".equals(type)||spec(type)!=null;}
    void start(){
        for(Player p:Bukkit.getOnlinePlayers())load(p);
        ticker=new BukkitRunnable(){@Override public void run(){update();}};
        ticker.runTaskTimer(plugin,20L,5L);
    }
    void shutdown(){
        if(ticker!=null)ticker.cancel();
        for(UUID id:new ArrayList<UUID>(pets.keySet()))remove(id);
        for(Player p:Bukkit.getOnlinePlayers())
            if(p.getOpenInventory().getTopInventory().getHolder() instanceof PetMenu)p.closeInventory();
        selected.clear();lastChoice.clear();retrySpawn.clear();
    }
    @EventHandler public void onJoin(PlayerJoinEvent e){load(e.getPlayer());}
    @EventHandler public void onQuit(PlayerQuitEvent e){
        UUID id=e.getPlayer().getUniqueId();remove(id);selected.remove(id);lastChoice.remove(id);retrySpawn.remove(id);
        // A reconnect creates a new Player instance; it must be shown displays again.
        for(Pet pet:pets.values())pet.viewers.remove(id);
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void onOwnerHidden(PlayerHideEntityEvent e){
        if(!(e.getEntity() instanceof Player))return;
        Pet pet=pets.get(e.getEntity().getUniqueId());
        if(pet!=null)hideViewer(pet,e.getPlayer());
    }
    private void load(Player p){
        try{String type=plugin.getStore().inspect(data->data.getString("cosmeticPets."+p.getUniqueId(),"off"));
            selected.put(p.getUniqueId(),valid(type)?type:"off");}
        catch(Exception error){selected.put(p.getUniqueId(),"off");plugin.getLogger().warning("[Pets] Could not read choice: "+error.getMessage());}
    }
    void command(Player p,String[] args){
        if(args.length==0||(args.length==1&&"menu".equalsIgnoreCase(args[0]))){openMenu(p);return;}
        if(args.length!=1){p.sendMessage(ColorUtil.c("&e/pet [menu|list|status|имя|off]"));return;}
        String type=args[0].toLowerCase(Locale.ROOT);
        if("list".equals(type)){
            p.sendMessage(ColorUtil.c("&#80D8FFПитомцы: &fpenguin, deer, dragon, fox, wolf, bunny, polar_bear, owl, snowman &8| &e/pet off"));return;
        }
        if("status".equals(type)){
            String chosen=selected.getOrDefault(p.getUniqueId(),"off");Spec s=spec(chosen);
            p.sendMessage(ColorUtil.c(s==null?"&7Питомец выключен.":"&#80D8FFВыбран: &f"+s.name+" &8("+chosen+") &7— "+
                    (pets.containsKey(p.getUniqueId())?"показан":"сейчас скрыт или ожидает свободного места")+". &e/pet"));return;
        }
        if("олень".equals(type)||"reindeer".equals(type))type="deer";
        if("пингвин".equals(type))type="penguin";
        if("дракон".equals(type))type="dragon";
        if("лиса".equals(type))type="fox";
        if("волк".equals(type))type="wolf";
        if("заяц".equals(type)||"rabbit".equals(type))type="bunny";
        if("медведь".equals(type)||"bear".equals(type))type="polar_bear";
        if("сова".equals(type))type="owl";
        if("снеговик".equals(type))type="snowman";
        if(!valid(type)){p.sendMessage(ColorUtil.c("&eНеизвестный питомец. Откройте &f/pet &eили &f/pet list"));return;}
        choose(p,type);
    }
    private boolean choose(Player p,String type){
        if(!plugin.getStore().isAuthenticated(p)){p.sendMessage(ColorUtil.c("&cСначала войдите в аккаунт."));return false;}
        UUID id=p.getUniqueId();
        if(type.equals(selected.get(id))){p.sendMessage(ColorUtil.c("&7Этот выбор уже сохранён."));return true;}
        long now=System.nanoTime();Long last=lastChoice.get(id);
        // Turning off is always immediate. Avoid repeated fsync and respawn spam.
        if(!"off".equals(type)&&last!=null&&now-last.longValue()<250_000_000L){
            p.sendMessage(ColorUtil.c("&eПодождите немного перед сменой питомца."));return false;
        }
        try{
            plugin.getStore().transact(data->{data.set("cosmeticPets."+id,type);return null;});
            selected.put(id,type);lastChoice.put(id,now);retrySpawn.remove(id);remove(id);
            Spec s=spec(type);
            p.sendMessage(ColorUtil.c(s==null?"&7Питомец скрыт.":"&#80D8FFВыбран питомец: &f"+s.name+"&7. Только косметика, без бонусов в бою."));return true;
        }catch(Exception error){p.sendMessage(ColorUtil.c("&cНе удалось сохранить выбор питомца; прежний выбор не изменён."));return false;}
    }
    private void openMenu(Player p){
        ItemStack cursor=p.getItemOnCursor();
        if(cursor!=null&&!cursor.getType().isAir()){p.sendMessage(ColorUtil.c("&eСначала положите предмет с курсора в инвентарь."));return;}
        PetMenu menu=new PetMenu(p.getUniqueId());menu.inventory=Bukkit.createInventory(menu,36,ColorUtil.c("&#80D8FF&l✦ ПИТОМЦЫ"));
        ItemStack frame=ItemFactory.icon(Material.LIGHT_BLUE_STAINED_GLASS_PANE," ");
        for(int i=0;i<menu.inventory.getSize();i++)menu.inventory.setItem(i,frame);
        menu.inventory.setItem(4,ItemFactory.icon(Material.NETHER_STAR,"&#FFD740Выберите питомца",
                "&7Модель следует за вами, не атакует", "&7и не занимает блоки. Выбор сохранится.",
                "&7На паркуре и в невидимости скрывается."));
        String chosen=selected.getOrDefault(p.getUniqueId(),"off");
        for(int i=0;i<SPECS.length;i++){
            Spec s=SPECS[i];String status=s.id.equals(chosen)?"&a✓ Выбран":"&eНажмите, чтобы выбрать";
            menu.inventory.setItem(SLOTS[i],ItemFactory.icon(s.icon,"&#80D8FF"+s.name,"&7Команда: &f/pet "+s.id,status));
        }
        menu.inventory.setItem(31,ItemFactory.icon(Material.BARRIER,"&cСкрыть питомца","&7Команда: &f/pet off"));
        p.openInventory(menu.inventory);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onMenuClick(InventoryClickEvent event){
        Inventory top=event.getView().getTopInventory();
        if(!(top.getHolder() instanceof PetMenu))return;
        event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player))return;
        Player p=(Player)event.getWhoClicked();PetMenu menu=(PetMenu)top.getHolder();
        if(!menu.owner.equals(p.getUniqueId())||event.getRawSlot()<0||event.getRawSlot()>=top.getSize())return;
        if(event.getClick()!=ClickType.LEFT&&event.getClick()!=ClickType.RIGHT)return;
        String choice=null;
        if(event.getRawSlot()==31)choice="off";
        for(int i=0;i<SLOTS.length;i++)if(event.getRawSlot()==SLOTS[i])choice=SPECS[i].id;
        if(choice!=null&&choose(p,choice))Bukkit.getScheduler().runTask(plugin,()->{
            if(p.isOnline()&&p.getOpenInventory().getTopInventory()==top)p.closeInventory();
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onMenuDrag(InventoryDragEvent event){
        if(event.getView().getTopInventory().getHolder() instanceof PetMenu)event.setCancelled(true);
    }
    private boolean hidden(Player p,String type){
        PotionEffectType invis=ColorUtil.potType("invisibility","INVISIBILITY");
        return "off".equals(type)||spec(type)==null||plugin.getStore().isVanished(p)||(invis!=null&&p.hasPotionEffect(invis))
                ||!plugin.getStore().isAuthenticated(p)||p.isDead()||p.getGameMode()==GameMode.SPECTATOR
                ||plugin.getStore().getParkourCp(p)!=null;
    }
    private void update(){
        tick++;int max=Math.max(0,Math.min(32,plugin.getConfig().getInt("pets.max-active",16)));
        // Clean invalid/hidden/old-world pets before filling slots, then enforce a lowered cap.
        for(UUID id:new ArrayList<UUID>(pets.keySet())){
            Player p=Bukkit.getPlayer(id);Pet pet=pets.get(id);String type=selected.get(id);
            if(p==null||!p.isOnline()||hidden(p,type)||pet.world!=p.getWorld()||!pet.type.equals(type)||!complete(pet))remove(id);
        }
        int retained=0;for(UUID id:new ArrayList<UUID>(pets.keySet()))if(++retained>max)remove(id);
        int spawned=0;
        for(Player p:Bukkit.getOnlinePlayers()){
            UUID id=p.getUniqueId();String type=selected.get(id);if(type==null){load(p);type=selected.get(id);}
            if(hidden(p,type))continue;
            Pet pet=pets.get(id);
            Long retry=retrySpawn.get(id);
            if(pet==null&&pets.size()<max&&spawned<2&&(retry==null||System.nanoTime()>=retry.longValue())){
                spawned++;pet=spawn(p,spec(type));if(pet!=null){pets.put(id,pet);retrySpawn.remove(id);}
                else retrySpawn.put(id,System.nanoTime()+5_000_000_000L);
            }
            if(pet!=null){move(p,pet);syncViewers(p,pet);}
        }
    }
    private boolean complete(Pet pet){
        if(pet.parts.size()!=pet.model.length)return false;
        for(Entity e:pet.parts)if(e==null||!e.isValid())return false;return true;
    }
    private Pet spawn(Player p,Spec spec){
        if(spec==null)return null;
        Pet pet=new Pet(p,spec);Location base=p.getLocation().clone();base.setYaw(0f);base.setPitch(0f);
        if(!pet.world.isChunkLoaded(base.getBlockX()>>4,base.getBlockZ()>>4))return null;
        try{
            for(Part part:spec.parts){
                BlockDisplay entity=pet.world.spawn(base,BlockDisplay.class,d->{
                    d.setVisibleByDefault(false);d.setPersistent(false);d.setGravity(false);d.setInvulnerable(true);d.setSilent(true);
                    d.setBillboard(Display.Billboard.FIXED);d.setViewRange(.65f);d.setDisplayWidth(2f);d.setDisplayHeight(2f);
                    d.setInterpolationDuration(5);d.setTeleportDuration(5);d.setBlock(Bukkit.createBlockData(part.material));
                    d.addScoreboardTag("kg_cosmetic_pet");
                });pet.parts.add(entity);
            }
            return pet;
        }catch(RuntimeException error){destroy(pet);plugin.getLogger().warning("[Pets] Could not spawn display pet: "+error.getMessage());return null;}
    }
    private void move(Player player,Pet pet){
        Location owner=player.getLocation();Vector direction=owner.getDirection().setY(0);
        if(direction.lengthSquared()<.001)direction=new Vector(0,0,1);else direction.normalize();
        Location target=owner.clone().subtract(direction.multiply(1.25));
        if(!pet.world.isChunkLoaded(target.getBlockX()>>4,target.getBlockZ()>>4))target=owner.clone();
        Location base=target.clone();
        if(pet.previous!=null&&pet.previous.getWorld()==pet.world&&pet.previous.distanceSquared(target)<64.0
                &&Math.abs(pet.previous.getY()-target.getY())<3.0){
            base=pet.previous.clone().add(target.toVector().subtract(pet.previous.toVector()).multiply(.55));
            if(!pet.world.isChunkLoaded(base.getBlockX()>>4,base.getBlockZ()>>4))base=target.clone();
        }
        base.setYaw(0f);base.setPitch(0f);pet.previous=base.clone();
        float flap=(float)Math.sin(tick*.45)*.28f;
        for(int i=0;i<pet.parts.size();i++){
            Entity entity=pet.parts.get(i);if(!(entity instanceof BlockDisplay)||!entity.isValid())continue;
            Part part=pet.model[i];Matrix4f matrix=new Matrix4f().rotateY((float)Math.toRadians(-owner.getYaw()));
            if(part.wing!=0)matrix.translate(part.x,.55f,0).rotateZ(part.wing*flap).translate(-part.x,-.55f,0);
            matrix.translate(part.x-part.w/2,part.y,part.z-part.d/2).scale(part.w,part.h,part.d);
            ((BlockDisplay)entity).setTransformationMatrix(matrix);entity.teleport(base);
        }
    }
    private void syncViewers(Player owner,Pet pet){
        for(Player viewer:Bukkit.getOnlinePlayers()){
            boolean can=viewer.getWorld()==pet.world&&(viewer.equals(owner)||viewer.canSee(owner));
            if(can&&pet.viewers.add(viewer.getUniqueId()))for(Entity part:pet.parts)viewer.showEntity(plugin,part);
            else if(!can)hideViewer(pet,viewer);
        }
    }
    private void hideViewer(Pet pet,Player viewer){
        if(!pet.viewers.remove(viewer.getUniqueId()))return;
        if(plugin.isEnabled())for(Entity part:pet.parts)viewer.hideEntity(plugin,part);
    }
    private void destroy(Pet pet){
        for(UUID id:new ArrayList<UUID>(pet.viewers)){
            Player viewer=Bukkit.getPlayer(id);if(viewer!=null)hideViewer(pet,viewer);
        }
        pet.viewers.clear();for(Entity e:pet.parts)if(e!=null)e.remove();pet.parts.clear();
    }
    private void remove(UUID id){Pet p=pets.remove(id);if(p!=null)destroy(p);}
}
