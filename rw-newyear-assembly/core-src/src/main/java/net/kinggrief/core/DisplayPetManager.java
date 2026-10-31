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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
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
    private static final class Pet {
        final String type; final World world; final Part[] model;
        final List<Entity> parts=new ArrayList<Entity>();
        Pet(Player p,String type,Part[] model) {world=p.getWorld();this.type=type;this.model=model;}
        void remove() {for(Entity e:parts) if(e!=null)e.remove();parts.clear();}
    }
    private final KingGriefCore plugin;
    private final Map<UUID,String> selected=new HashMap<UUID,String>();
    private final Map<UUID,Pet> pets=new HashMap<UUID,Pet>();
    private BukkitRunnable ticker;
    private int tick;
    DisplayPetManager(KingGriefCore plugin) {this.plugin=plugin;}
    void start() {
        for(Player p:Bukkit.getOnlinePlayers())load(p);
        ticker=new BukkitRunnable() {@Override public void run(){update();}};
        ticker.runTaskTimer(plugin,20L,5L);
    }
    void shutdown() {if(ticker!=null)ticker.cancel();for(Pet p:pets.values())p.remove();pets.clear();selected.clear();}
    @EventHandler public void onJoin(PlayerJoinEvent e) {load(e.getPlayer());}
    @EventHandler public void onQuit(PlayerQuitEvent e) {remove(e.getPlayer().getUniqueId());selected.remove(e.getPlayer().getUniqueId());}
    private void load(Player p) {
        try {String type=plugin.getStore().inspect(data -> data.getString("cosmeticPets."+p.getUniqueId(),"off"));selected.put(p.getUniqueId(),valid(type)?type:"off");}
        catch(Exception error){selected.put(p.getUniqueId(),"off");}
    }
    void command(Player p,String[] args) {
        if(args.length==0){p.sendMessage(ColorUtil.c("&#80D8FFПитомцы-модельки: &fpenguin, deer, dragon, off &8| &e/pet <имя>"));return;}
        String type=args[0].toLowerCase(Locale.ROOT);
        if("олень".equals(type)||"reindeer".equals(type))type="deer";
        if("пингвин".equals(type))type="penguin";
        if("дракон".equals(type))type="dragon";
        if(!valid(type)){p.sendMessage(ColorUtil.c("&e/pet <penguin|deer|dragon|off>"));return;}
        final String chosen=type;
        try {
            plugin.getStore().transact(data -> {data.set("cosmeticPets."+p.getUniqueId(),chosen);return null;});
            selected.put(p.getUniqueId(),chosen);remove(p.getUniqueId());
            p.sendMessage(ColorUtil.c("off".equals(chosen)?"&7Питомец скрыт.":"&#80D8FFПитомец выбран: &f"+chosen+"&7. Косметическая модель без влияния на бой."));
        } catch(Exception error){p.sendMessage(ColorUtil.c("&cНе удалось сохранить выбор питомца."));}
    }
    private static boolean valid(String type){return "off".equals(type)||"penguin".equals(type)||"deer".equals(type)||"dragon".equals(type);}
    private void update() {
        tick++;int max=Math.max(0,Math.min(32,plugin.getConfig().getInt("pets.max-active",16)));
        for(Player p:Bukkit.getOnlinePlayers()) {
            String type=selected.get(p.getUniqueId());if(type==null){load(p);type=selected.get(p.getUniqueId());}
            PotionEffectType invis=ColorUtil.potType("invisibility","INVISIBILITY");
            boolean hidden=plugin.getStore().isVanished(p)||(invis!=null&&p.hasPotionEffect(invis));
            boolean disabled="off".equals(type)||hidden||!plugin.getStore().isAuthenticated(p)||p.isDead()
                    ||p.getGameMode()==GameMode.SPECTATOR||plugin.getStore().getParkourCp(p)!=null;
            Pet pet=pets.get(p.getUniqueId());
            if(disabled){if(pet!=null)remove(p.getUniqueId());continue;}
            if(pet!=null&&(pet.world!=p.getWorld()||!pet.type.equals(type)||!complete(pet))){remove(p.getUniqueId());pet=null;}
            if(pet==null&&pets.size()<max){pet=spawn(p,type);if(pet!=null)pets.put(p.getUniqueId(),pet);}
            if(pet!=null)move(p,pet);
        }
        for(UUID id:new ArrayList<UUID>(pets.keySet()))if(Bukkit.getPlayer(id)==null)remove(id);
    }
    private boolean complete(Pet pet){if(pet.parts.size()!=pet.model.length)return false;for(Entity e:pet.parts)if(e==null||!e.isValid())return false;return true;}
    private Pet spawn(Player p,String type) {
        Part[] model="penguin".equals(type)?PENGUIN:"deer".equals(type)?DEER:DRAGON;
        Pet pet=new Pet(p,type,model);Location base=p.getLocation().clone();base.setYaw(0f);base.setPitch(0f);
        try {
            for(Part part:model){
                BlockDisplay entity=pet.world.spawn(base,BlockDisplay.class,d -> {
                    d.setPersistent(false);d.setGravity(false);d.setInvulnerable(true);d.setSilent(true);
                    d.setBillboard(Display.Billboard.FIXED);d.setViewRange(.65f);d.setDisplayWidth(2f);d.setDisplayHeight(2f);
                    d.setInterpolationDuration(5);d.setTeleportDuration(5);d.setBlock(Bukkit.createBlockData(part.material));d.addScoreboardTag("kg_cosmetic_pet");
                });pet.parts.add(entity);
            }
            return pet;
        } catch(RuntimeException error){pet.remove();plugin.getLogger().warning("[Pets] Could not spawn display pet: "+error.getMessage());return null;}
    }
    private void move(Player player,Pet pet) {
        Location owner=player.getLocation();Vector direction=owner.getDirection().setY(0);
        if(direction.lengthSquared()<.001)direction=new Vector(0,0,1);else direction.normalize();
        Location base=owner.clone().subtract(direction.multiply(1.25));
        if(!pet.world.isChunkLoaded(base.getBlockX()>>4,base.getBlockZ()>>4))base=owner.clone();
        base.setYaw(0f);base.setPitch(0f);float flap=(float)Math.sin(tick*.45)*.28f;
        for(int i=0;i<pet.parts.size();i++){
            Entity entity=pet.parts.get(i);if(!(entity instanceof BlockDisplay)||!entity.isValid())continue;
            Part part=pet.model[i];Matrix4f matrix=new Matrix4f().rotateY((float)Math.toRadians(-owner.getYaw()));
            if(part.wing!=0)matrix.translate(part.x,.55f,0).rotateZ(part.wing*flap).translate(-part.x,-.55f,0);
            matrix.translate(part.x-part.w/2,part.y,part.z-part.d/2).scale(part.w,part.h,part.d);
            ((BlockDisplay)entity).setTransformationMatrix(matrix);entity.teleport(base);
        }
    }
    private void remove(UUID id){Pet p=pets.remove(id);if(p!=null)p.remove();}
}
