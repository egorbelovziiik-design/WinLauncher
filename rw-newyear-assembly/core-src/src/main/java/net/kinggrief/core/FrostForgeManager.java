package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Interchangeable, exact components crafted in a vanilla workbench; no world edits. */
final class FrostForgeManager implements Listener {
    private static final String SWORD_ID="assembled_frost_sword";
    private static final String AURORA_ID="assembled_aurora_sword";
    private static final String TEMPEST_ID="assembled_tempest_sword";
    private static final long COOLDOWN_NS=12_000_000_000L;
    private static final int MAX_COOLDOWNS=4096;
    private enum Variant {
        FROST(SWORD_ID,"slowness","SLOW",60,0,false,Particle.SNOWFLAKE,"Ледяное касание"),
        AURORA(AURORA_ID,"speed","SPEED",120,1,true,Particle.END_ROD,"Шаг сияния"),
        TEMPEST(TEMPEST_ID,"weakness","WEAKNESS",100,1,false,Particle.ELECTRIC_SPARK,"Порыв бури");
        final String id,modern,legacy,label; final int duration,amplifier; final boolean self; final Particle particle;
        Variant(String id,String modern,String legacy,int duration,int amplifier,boolean self,Particle particle,String label){
            this.id=id;this.modern=modern;this.legacy=legacy;this.duration=duration;this.amplifier=amplifier;this.self=self;this.particle=particle;this.label=label;
        }
        static Variant of(String id){for(Variant value:values())if(value.id.equals(id))return value;return null;}
    }
    private static final class Spec {
        final NamespacedKey key;final ItemStack result;final Material[] materials;final ItemStack[] exact;
        Spec(NamespacedKey key,ItemStack result,Material[] materials,ItemStack[] exact){this.key=key;this.result=result;this.materials=materials;this.exact=exact;}
    }
    public static final class ForgeHolder implements InventoryHolder {
        final UUID owner;Inventory inventory;
        ForgeHolder(UUID owner){this.owner=owner;}
        @Override public Inventory getInventory(){return inventory;}
    }
    private final KingGriefCore plugin;
    private final PvpRegionGuard regionGuard;
    private final Map<NamespacedKey,Spec> specs=new LinkedHashMap<NamespacedKey,Spec>();
    private final List<NamespacedKey> registered=new ArrayList<NamespacedKey>();
    private final Map<UUID,Long> cooldowns=new LinkedHashMap<UUID,Long>();
    private final Set<UUID> pendingHits=new HashSet<UUID>();
    private final Set<UUID> reservedCooldownSlots=new HashSet<UUID>();
    private boolean enabled;
    FrostForgeManager(KingGriefCore plugin){this.plugin=plugin;this.regionGuard=new PvpRegionGuard(plugin);}
    private ItemStack part(Material material,String id,String title,String... description){
        return ItemFactory.make(material,1,title,Arrays.asList(description),false,id,null,0);
    }
    private void initialize(){
        ItemStack blade=part(Material.PRISMARINE_SHARD,"frost_forge_blade","&#80D8FF❄ Лезвие полярной кузницы",
                "&7Часть оружия. Сохраните для сборки в верстаке.");
        ItemStack guard=part(Material.AMETHYST_SHARD,"frost_forge_guard","&#B39DDB✦ Гарда полярной кузницы",
                "&7Часть оружия. Сохраните для сборки в верстаке.");
        ItemStack core=part(Material.HEART_OF_THE_SEA,"frost_forge_core","&#00E5FF◆ Сердце полярной кузницы",
                "&7Часть оружия. Сохраните для сборки в верстаке.");
        // Keep the original three components byte-compatible with earlier prototypes.
        ItemStack aurora=part(Material.PRISMARINE_CRYSTALS,"frost_forge_aurora_core","&#FFD740✦ Сердце сияния",
                "&7Выбор сердца меняет способность собранного клинка.");
        ItemStack tempest=part(Material.ECHO_SHARD,"frost_forge_tempest_core","&#B388FF✦ Сердце бури",
                "&7Выбор сердца меняет способность собранного клинка.");
        ItemStack sword=ItemFactory.make(Material.NETHERITE_SWORD,1,"&#80D8FF&l❄ СОБРАННЫЙ КЛИНОК СЕВЕРА",
                Arrays.asList("&7Собран из трёх деталей в верстаке.","&bЛедяное касание: &7Медлительность I на 3 секунды",
                        "&7при разрешённом ударе; перезарядка &f12 секунд&7.","&8Боссы и безопасная зона спавна исключены."),
                false,SWORD_ID,"sword_frost",1831);
        ItemMeta meta=sword.getItemMeta();
        if(meta==null)throw new IllegalStateException("Sword metadata unavailable");
        org.bukkit.enchantments.Enchantment sharp=ColorUtil.ench("sharpness","DAMAGE_ALL"),unbreaking=ColorUtil.ench("unbreaking","DURABILITY");
        if(sharp==null||unbreaking==null)throw new IllegalStateException("Required sword enchantments unavailable");
        meta.addEnchant(sharp,3,false);meta.addEnchant(unbreaking,2,false);meta.removeItemFlags(ItemFlag.HIDE_ATTRIBUTES);sword.setItemMeta(meta);
        ItemStack auroraSword=variantSword(AURORA_ID,"&#FFD740&l✦ КЛИНОК СИЯНИЯ", "sword_zeus",1832,
                "&eШаг сияния: &7Скорость II себе на 6 секунд");
        ItemStack tempestSword=variantSword(TEMPEST_ID,"&#B388FF&l✦ КЛИНОК БУРИ", "sword_phantom",1836,
                "&dПорыв бури: &7Слабость II цели на 5 секунд");
        addSpec("frost_forge_blade",blade,new Material[]{Material.NETHERITE_INGOT,Material.DIAMOND,Material.BLUE_ICE},null);
        addSpec("frost_forge_guard",guard,new Material[]{Material.GOLD_INGOT,Material.IRON_INGOT,Material.AMETHYST_SHARD},null);
        addSpec("frost_forge_core",core,new Material[]{Material.HEART_OF_THE_SEA,Material.PRISMARINE_CRYSTALS,Material.PACKED_ICE},null);
        addSpec("frost_forge_aurora_core",aurora,new Material[]{Material.HEART_OF_THE_SEA,Material.AMETHYST_SHARD,Material.GLOWSTONE_DUST},null);
        addSpec("frost_forge_tempest_core",tempest,new Material[]{Material.HEART_OF_THE_SEA,Material.ECHO_SHARD,Material.BLAZE_POWDER},null);
        addSpec("frost_forge_sword",sword,null,new ItemStack[]{blade,guard,core});
        addSpec("frost_forge_aurora_sword",auroraSword,null,new ItemStack[]{blade,guard,aurora});
        addSpec("frost_forge_tempest_sword",tempestSword,null,new ItemStack[]{blade,guard,tempest});
    }
    private ItemStack variantSword(String id,String title,String model,int modelData,String effect){
        ItemStack sword=ItemFactory.make(Material.NETHERITE_SWORD,1,title,
                Arrays.asList("&7Лезвие + гарда + выбранное сердце.","&7Острота VI · Прочность V",effect,
                        "&7при разрешённом ударе по живой цели.","&7Общий КТ клинков кузницы: &f12 секунд&7.",
                        "&8Боссы, манекены и безопасная зона исключены."),false,id,model,modelData);
        ItemMeta meta=sword.getItemMeta();
        org.bukkit.enchantments.Enchantment sharp=ColorUtil.ench("sharpness","DAMAGE_ALL"),unbreaking=ColorUtil.ench("unbreaking","DURABILITY");
        if(meta==null||sharp==null||unbreaking==null)throw new IllegalStateException("Required sword metadata unavailable");
        // Match the server's high-powered custom weapons while preserving the old frost sword unchanged.
        meta.addEnchant(sharp,6,true);meta.addEnchant(unbreaking,5,true);meta.removeItemFlags(ItemFlag.HIDE_ATTRIBUTES);sword.setItemMeta(meta);
        return sword;
    }
    private void addSpec(String key,ItemStack result,Material[] materials,ItemStack[] exact){
        NamespacedKey id=new NamespacedKey(plugin,key);specs.put(id,new Spec(id,result,materials,exact));
    }
    void start(){
        try{
            initialize();
            for(Spec spec:specs.values()){
                if(Bukkit.getRecipe(spec.key)!=null)throw new IllegalStateException("Recipe already exists: "+spec.key);
                ShapelessRecipe recipe=new ShapelessRecipe(spec.key,spec.result.clone());
                if(spec.materials!=null)for(Material material:spec.materials)recipe.addIngredient(material);
                if(spec.exact!=null)for(ItemStack item:spec.exact)recipe.addIngredient(new RecipeChoice.ExactChoice(item.clone()));
                if(!Bukkit.addRecipe(recipe))throw new IllegalStateException("Cannot register recipe: "+spec.key);
                registered.add(spec.key);
            }
            enabled=true;for(Player player:Bukkit.getOnlinePlayers())discover(player);
        }catch(RuntimeException error){
            for(NamespacedKey key:registered)Bukkit.removeRecipe(key);registered.clear();enabled=false;
            plugin.getLogger().warning("[FrostForge] Recipes disabled: "+error.getMessage());
        }
    }
    void shutdown(){
        enabled=false;pendingHits.clear();reservedCooldownSlots.clear();cooldowns.clear();regionGuard.invalidate();
        for(NamespacedKey key:registered)Bukkit.removeRecipe(key);registered.clear();
    }
    private void discover(Player player){
        if(!enabled||player==null||!player.isOnline())return;
        for(NamespacedKey key:registered)try{player.discoverRecipe(key);}
        catch(RuntimeException error){plugin.getLogger().warning("[FrostForge] Could not show recipe to "+player.getName()+": "+error.getMessage());}
    }
    @EventHandler public void join(PlayerJoinEvent event){Bukkit.getScheduler().runTask(plugin,()->discover(event.getPlayer()));}
    private Spec spec(Recipe recipe){return recipe instanceof org.bukkit.Keyed?specs.get(((org.bukkit.Keyed)recipe).getKey()):null;}
    private static boolean empty(ItemStack item){return item==null||item.getType().isAir()||item.getAmount()<=0;}
    private static boolean matrixMatches(Spec spec,ItemStack[] matrix){
        if(spec.materials!=null){
            Set<Material> needed=EnumSet.noneOf(Material.class);needed.addAll(Arrays.asList(spec.materials));int count=0;
            for(ItemStack item:matrix){
                if(empty(item))continue;
                if(item.hasItemMeta()||!needed.remove(item.getType()))return false;count++;
            }
            return count==spec.materials.length&&needed.isEmpty();
        }
        boolean[] found=new boolean[spec.exact.length];int count=0;
        for(ItemStack item:matrix){
            if(empty(item))continue;
            boolean match=false;for(int i=0;i<spec.exact.length;i++)if(!found[i]&&spec.exact[i].isSimilar(item)){found[i]=true;match=true;break;}
            if(!match)return false;count++;
        }
        return count==spec.exact.length;
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void prepare(PrepareItemCraftEvent event){
        Spec spec=spec(event.getRecipe());if(spec==null)return;
        if(!enabled||!matrixMatches(spec,event.getInventory().getMatrix()))event.getInventory().setResult(null);
    }
    private boolean allowed(Player player){
        return player!=null&&player.isOnline()&&!player.isDead()&&plugin.getStore().isAuthenticated(player)
                &&!plugin.getStore().isQuickFrozen(player)&&!plugin.getModeration().isFrozenOrAscending(player);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void craft(CraftItemEvent event){
        Spec spec=spec(event.getRecipe());if(spec==null)return;
        CraftingInventory inventory=event.getInventory();ItemStack shown=event.getCurrentItem();
        if(!enabled||!(event.getWhoClicked() instanceof Player)||!allowed((Player)event.getWhoClicked())
                ||!matrixMatches(spec,inventory.getMatrix())||empty(shown)||shown.getAmount()!=1||!spec.result.isSimilar(shown))event.setCancelled(true);
    }
    private boolean safeArea(org.bukkit.Location location){
        return !plugin.getSpawnEngine().isInSpawnWorld(location)||plugin.getSpawnEngine().isInsidePvpZone(location);
    }
    private boolean validTarget(Player attacker,LivingEntity victim){
        if(!allowed(attacker)||attacker.getGameMode()==GameMode.SPECTATOR||victim==null||victim.isDead()||!victim.isValid()
                ||attacker.getWorld()!=victim.getWorld()||!safeArea(attacker.getLocation())||!safeArea(victim.getLocation()))return false;
        if(!(victim instanceof Player)) {
            // Conservative for mobs: use WG build/block-place and PvP flags as
            // an extra guard after the accepted damage event, not as a claim to
            // understand every plugin's entity-protection rules.
            return regionGuard.canPlace(attacker,victim.getLocation());
        }
        Player target=(Player)victim;
        if(target.equals(attacker)||!allowed(target)||target.getGameMode()==GameMode.SPECTATOR||!attacker.getWorld().getPVP())return false;
        DataStore store=plugin.getStore();
        DataStore.ActiveWagerDuel duel=store.getActiveWagerDuel(attacker.getName());
        boolean inDuel=duel!=null&&(duel.playerA.equalsIgnoreCase(target.getName())||duel.playerB.equalsIgnoreCase(target.getName()));
        if(!inDuel&&!plugin.getSpawnEngine().isInsideAnyKabinka(target.getLocation())){
            if(store.isDirectTeammate(attacker.getName(),target.getName())&&!store.isTeammatePvpAllowed(attacker.getName(),target.getName()))return false;
            DataStore.Clan left=store.getClanOf(attacker.getName()),right=store.getClanOf(target.getName());
            if(left!=null&&right!=null){
                if(left.equals(right)&&!left.friendlyFire)return false;
                if(!left.equals(right)&&(left.allies.contains(right.id)||right.allies.contains(left.id))
                        &&!store.isTeammatePvpAllowed(attacker.getName(),target.getName()))return false;
            }
        }
        return regionGuard.canAffect(attacker,target);
    }
    private boolean reserveHit(UUID id,long now){
        if(pendingHits.contains(id))return false;
        // Insertion order is successful activation order: prune expired entries
        // without evicting a live cooldown just to make room for a new player.
        java.util.Iterator<Map.Entry<UUID,Long>> iterator=cooldowns.entrySet().iterator();
        while(iterator.hasNext()){
            Map.Entry<UUID,Long> oldest=iterator.next();
            if(now-oldest.getValue().longValue()<COOLDOWN_NS)break;
            iterator.remove();
        }
        Long last=cooldowns.get(id);
        if(last!=null&&now-last.longValue()<COOLDOWN_NS)return false;
        boolean needsSlot=last==null;
        if(needsSlot&&cooldowns.size()+reservedCooldownSlots.size()>=MAX_COOLDOWNS)return false;
        pendingHits.add(id);if(needsSlot)reservedCooldownSlots.add(id);
        return true;
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void hit(EntityDamageByEntityEvent event){
        if(!enabled||!(event.getDamager() instanceof Player)||!(event.getEntity() instanceof LivingEntity)||event.getFinalDamage()<=0)return;
        Player attacker=(Player)event.getDamager();LivingEntity victim=(LivingEntity)event.getEntity();
        ItemStack hand=attacker.getInventory().getItemInMainHand();
        Variant variant=hand.getType()==Material.NETHERITE_SWORD?Variant.of(ItemFactory.getCustomId(hand)):null;
        if(variant==null||victim.getScoreboardTags().contains("kg_dps_dummy")||victim.getScoreboardTags().contains("kg_event_boss")
                ||victim.getScoreboardTags().contains("kg_holo")||!validTarget(attacker,victim))return;
        UUID id=attacker.getUniqueId();
        if(!reserveHit(id,System.nanoTime()))return;
        // Keep the reservation through potion callbacks as well: another plugin
        // can synchronously cause a nested hit while an effect is being applied.
        try{
            Bukkit.getScheduler().runTask(plugin,()->{
                try{
                    if(!enabled||event.isCancelled()||event.getFinalDamage()<=0||!validTarget(attacker,victim)
                            ||attacker.getLocation().distanceSquared(victim.getLocation())>64.0)return;
                    PotionEffectType type=ColorUtil.potType(variant.modern,variant.legacy);if(type==null)return;
                    LivingEntity recipient=variant.self?attacker:victim;
                    PotionEffect previous=recipient.getPotionEffect(type);
                    if(previous!=null&&(previous.getAmplifier()>variant.amplifier
                            ||previous.getAmplifier()==variant.amplifier&&previous.getDuration()>=variant.duration))return;
                    recipient.addPotionEffect(new PotionEffect(type,variant.duration,variant.amplifier,false,true,true));
                    // Paper may return true even when another plugin cancelled the potion event.
                    PotionEffect applied=recipient.getPotionEffect(type);
                    if(applied==null||applied.getAmplifier()!=variant.amplifier||applied.getDuration()<variant.duration)return;
                    cooldowns.remove(id);cooldowns.put(id,System.nanoTime());
                    attacker.spawnParticle(variant.particle,recipient.getLocation().add(0,1,0),8,.25,.35,.25,0);
                    ColorUtil.actionBar(attacker,"&#80D8FF✦ "+variant.label+" &8• &fобщая перезарядка 12 сек.");
                }finally{
                    pendingHits.remove(id);reservedCooldownSlots.remove(id);
                }
            });
        }catch(RuntimeException error){
            pendingHits.remove(id);reservedCooldownSlots.remove(id);
            plugin.getLogger().warning("[FrostForge] Could not schedule ability: "+error.getClass().getSimpleName());
        }
    }
    void command(Player player,String[] args){
        if(args.length!=0){player.sendMessage(ColorUtil.c("&e/frostforge &7— детали и три варианта клинка."));return;}
        open(player);
    }
    void open(Player player){
        if(!enabled||!allowed(player)){player.sendMessage(ColorUtil.c("&cКузница недоступна: войдите в аккаунт и повторите позже."));return;}
        if(!empty(player.getItemOnCursor())){player.sendMessage(ColorUtil.c("&eСначала положите предмет с курсора в инвентарь."));return;}
        ForgeHolder holder=new ForgeHolder(player.getUniqueId());Inventory inventory=Bukkit.createInventory(holder,27,ColorUtil.c("&#80D8FF&l⚒ ПОЛЯРНАЯ КУЗНИЦА"));holder.inventory=inventory;
        ItemStack frame=ItemFactory.icon(Material.LIGHT_BLUE_STAINED_GLASS_PANE," ");
        for(int i=0;i<27;i++)if(i<9||i>=18||i%9==0||i%9==8)inventory.setItem(i,frame);
        inventory.setItem(4,ItemFactory.glowIcon(Material.CRAFTING_TABLE,"&#80D8FFРецепты в верстаке",
                "&7Каждая деталь: три обычных предмета в любом порядке.","&7Предметы с именами, чарами и другими данными не расходуются.",
                "&7Лезвие + гарда + выбранное сердце = свой клинок.","&7Детали должны быть неизменёнными."));
        inventory.setItem(10,ItemFactory.icon(Material.PRISMARINE_SHARD,"&#80D8FFЛезвие", "&7Незеритовый слиток + алмаз + синий лёд"));
        inventory.setItem(12,ItemFactory.icon(Material.AMETHYST_SHARD,"&#B39DDBГарда", "&7Золотой слиток + железный слиток + осколок аметиста"));
        inventory.setItem(14,ItemFactory.icon(Material.HEART_OF_THE_SEA,"&#00E5FFСердце", "&7Сердце моря + призмариновый кристалл + плотный лёд"));
        inventory.setItem(16,ItemFactory.glowIcon(Material.NETHERITE_SWORD,"&#80D8FFСобранный клинок севера",
                "&7Лезвие + гарда + полярное сердце.","&7Острота III, Прочность II; медлительность I на 3 сек.",
                "&7У клинков кузницы общий КТ 12 сек.","&8Иконки меню — подсказки, не выдача предметов."));
        inventory.setItem(20,ItemFactory.icon(Material.PRISMARINE_CRYSTALS,"&#FFD740Сердце сияния",
                "&7Сердце моря + аметистовый осколок + светопыль"));
        inventory.setItem(21,ItemFactory.glowIcon(Material.NETHERITE_SWORD,"&#FFD740Клинок сияния",
                "&7Лезвие + гарда + сердце сияния.","&7Острота VI, Прочность V; скорость II себе на 6 сек.","&7Общий КТ клинков 12 сек."));
        inventory.setItem(23,ItemFactory.icon(Material.ECHO_SHARD,"&#B388FFСердце бури",
                "&7Сердце моря + осколок эха + огненный порошок"));
        inventory.setItem(24,ItemFactory.glowIcon(Material.NETHERITE_SWORD,"&#B388FFКлинок бури",
                "&7Лезвие + гарда + сердце бури.","&7Острота VI, Прочность V; слабость II цели на 5 сек.","&7Общий КТ клинков 12 сек."));
        inventory.setItem(26,ItemFactory.icon(Material.BARRIER,"&cЗакрыть"));player.openInventory(inventory);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void click(InventoryClickEvent event){
        Inventory top=event.getView().getTopInventory();if(!(top.getHolder() instanceof ForgeHolder))return;
        boolean wasCancelled=event.isCancelled();event.setCancelled(true);
        if(wasCancelled||!(event.getWhoClicked() instanceof Player)||!top.equals(event.getClickedInventory())||event.getRawSlot()!=26)return;
        Player player=(Player)event.getWhoClicked();ForgeHolder holder=(ForgeHolder)top.getHolder();
        if(!holder.owner.equals(player.getUniqueId())||!allowed(player))return;
        Bukkit.getScheduler().runTask(plugin,()->{if(player.isOnline()&&player.getOpenInventory().getTopInventory().getHolder()==holder)player.closeInventory();});
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof ForgeHolder)event.setCancelled(true);}
}
