package net.kinggrief.probe;

import java.util.*;
import net.kinggrief.core.ColorUtil;
import net.kinggrief.core.ItemFactory;
import net.kinggrief.core.KingGriefCore;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;

public final class OrbProbe extends JavaPlugin implements Listener {
 private KingGriefCore core;
 private Player p, enemy;
 private int errors;
 private boolean running, cancelResurrection;
 private EquipmentSlot lastResurrectHand;
 private Arrow lastArrow;
 private Projectile lastThrown;
 private boolean thrownHit;
 private double thrownDamage;
 private boolean lightningSeen;
 private int explosionDamageCount;
 private LivingEntity explosionWitness;
 private String lastAmmoId;
 private boolean shotHit;
 private double arrowDamage;
 private boolean glidingBefore, glidingAfter;
 private boolean cancelArrowAtHighest, cancelledArrowSeen;
 private boolean bossHit;
 private final Map<String, ItemStack> purchased = new HashMap<>();
 private static final String[] IDS = {"shar_titana","shar_andromedy","shar_chimery","shar_apollona","shar_aida","shar_osirisa","shar_aresa","shar_santy","shar_kronosa","shar_geliosa","shar_morozko","shar_kvadrat"};
 private static final int[] SLOTS = {10,11,12,13,19,20,21,22,28,29,30,31};
 private static final int[] MODELS = {1101,1102,1103,1104,1105,1106,1108,1107,1109,1110,1111,1112};
 private static final double[] HP = {8,0,0,0,0,4,0,12,0,0,0,0};
 private static final String[][] EFFECTS = {
   {"strength:0","resistance:0"}, {"strength:1","speed:1","jump_boost:1"},
   {"strength:1","fire_resistance:0","regeneration:0"}, {"haste:1","speed:1","night_vision:0"},
   {"strength:1","fire_resistance:0"}, {"regeneration:1","resistance:0"},
   {"strength:2","speed:0"}, {"strength:1","speed:1","regeneration:0"},
   {"haste:2","speed:0","jump_boost:0"}, {"fire_resistance:0","night_vision:0","haste:0","regeneration:0"},
   {"resistance:1","regeneration:0","strength:0"}, {"strength:1","haste:1","resistance:1","regeneration:0","jump_boost:1"}
 };
 public void onEnable(){ core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore"); Bukkit.getPluginManager().registerEvents(this,this); }
 @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true) public void observeLaunch(ProjectileLaunchEvent event) {
   if(event.getEntity().getShooter()==p && (event.getEntity() instanceof org.bukkit.entity.Snowball || event.getEntity() instanceof org.bukkit.entity.Trident)) lastThrown=event.getEntity();
 }
 @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true) public void observeThrownHit(ProjectileHitEvent event) {
   if(event.getEntity().equals(lastThrown) && enemy.equals(event.getHitEntity())) thrownHit=true;
 }
 @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true) public void observeThrownDamage(EntityDamageByEntityEvent event) {
   if(event.getDamager().equals(lastThrown) && event.getEntity().equals(enemy)) thrownDamage=event.getDamage();
 }
 @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true) public void observeLightning(org.bukkit.event.weather.LightningStrikeEvent event) {
   if(lastThrown instanceof org.bukkit.entity.Trident || lastArrow!=null) lightningSeen=true;
 }
 @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true) public void observeExplosionDamage(org.bukkit.event.entity.EntityDamageEvent event) {
   if(event.getEntity().equals(explosionWitness) && (event.getCause()==org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
           || event.getCause()==org.bukkit.event.entity.EntityDamageEvent.DamageCause.BLOCK_EXPLOSION)) explosionDamageCount++;
 }
 @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true) public void observeShot(EntityShootBowEvent event) {
   if(event.getEntity().equals(p) && event.getProjectile() instanceof Arrow) {
     lastArrow=(Arrow)event.getProjectile(); lastAmmoId=ItemFactory.getCustomId(event.getConsumable());
     if("arrow_anti_fly".equals(lastAmmoId)) {enemy.setGliding(true);glidingBefore=enemy.isGliding();}
   }
 }
 @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true) public void cancelProjectileForProtection(EntityDamageByEntityEvent event) {
   if(cancelArrowAtHighest && event.getDamager() instanceof Arrow && event.getEntity().equals(enemy)) {
     event.setCancelled(true);cancelledArrowSeen=true;
   }
 }
 @EventHandler(priority=EventPriority.MONITOR) public void observeBossDamage(EntityDamageByEntityEvent event) {
   if(event.getEntity().getScoreboardTags().contains("kg_event_boss")) {
     if(!event.isCancelled() && event.getDamager().equals(lastArrow)) bossHit=true;
   }
 }
 @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true) public void observeHit(EntityDamageByEntityEvent event) {
   if(event.getDamager().equals(lastArrow) && event.getEntity().equals(enemy)) {
     shotHit=true;arrowDamage=event.getDamage();glidingAfter=enemy.isGliding();
   }
 }
 @EventHandler(priority=EventPriority.HIGHEST) public void cancelResurrect(EntityResurrectEvent e) {
   if(cancelResurrection && e.getEntity().equals(p)) { e.setCancelled(true); cancelResurrection=false; }
 }
 @EventHandler(priority=EventPriority.MONITOR) public void rememberHand(EntityResurrectEvent e) {
   if(e.getEntity().equals(p) && !e.isCancelled()) lastResurrectHand=e.getHand();
 }
 @EventHandler public void join(PlayerJoinEvent e) {
   if (e.getPlayer().getName().equals("OrbTester")) p=e.getPlayer();
   if (e.getPlayer().getName().equals("OrbEnemy")) enemy=e.getPlayer();
   if (p!=null && enemy!=null && !running) {running=true; later(20, () -> begin());}
 }
 private void later(long ticks,Runnable task){ Bukkit.getScheduler().runTaskLater(this, () -> {try{task.run();}catch(Throwable t){fail("exception " + t);t.printStackTrace();finish();}},ticks); }
 private void ok(boolean condition, String label){if(condition) getLogger().info("KG-ORBS PASS " + label);else fail(label);}
 private void fail(String label){errors++;getLogger().severe("KG-ORBS FAIL " + label);}
 private void finish(){getLogger().info("KG-ORBS RESULT " + (errors==0 ? "PASS" : "FAIL") + " errors="+errors);}
 private AttributeInstance max(Player player){return player.getAttribute((Attribute)Registry.ATTRIBUTE.get(NamespacedKey.minecraft("max_health")));}
 private void reset(Player player) {
   player.setGameMode(GameMode.SURVIVAL);
   player.getInventory().clear(); player.getInventory().setArmorContents(null);player.getInventory().setItemInOffHand(null);
   for(PotionEffect effect:player.getActivePotionEffects())player.removePotionEffect(effect.getType());
   player.setNoDamageTicks(0); player.setFireTicks(0);player.setFreezeTicks(0);player.setFoodLevel(20);player.setSaturation(20);
   if (max(player)!=null) max(player).setBaseValue(20);
   player.setHealth(Math.min(20,player.getMaxHealth()));
 }
 private void begin(){
   World w=Bukkit.getWorlds().get(0);w.setGameRule(GameRule.NATURAL_REGENERATION,false);
   p.teleport(new Location(w,0.5,70,0.5)); enemy.teleport(new Location(w,3.5,70,0.5));
   reset(p);reset(enemy);
   if(getConfig().getBoolean("protect-only")){protectionTest(0);return;}
   if(getConfig().getBoolean("abilities-only")){arrowAbilities(0);return;}
   if(getConfig().getBoolean("boss-only")){bossArrow();return;}
   if(getConfig().getBoolean("crossbows-only")){crossbowTests(0);return;}
   if(getConfig().getBoolean("throws-only")){throwTests(0);return;}
   if(getConfig().getBoolean("arrows-only")){arrowTests(0);return;}
   core.getStore().addRelics(p.getName(),10000);
   p.performCommand("shars");
   for(int i=0;i<IDS.length;i++){
     InventoryView view=p.getOpenInventory();
     Bukkit.getPluginManager().callEvent(new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,SLOTS[i],ClickType.LEFT,InventoryAction.PICKUP_ALL));
   }
   for(ItemStack it:p.getInventory().getStorageContents()) if(it!=null){String id=ItemFactory.getCustomId(it);if(id!=null&&id.startsWith("shar_"))purchased.put(id,it.clone());}
   for(int i=0;i<IDS.length;i++) {
     ItemStack it=purchased.get(IDS[i]);
     ok(it!=null && it.getType()==Material.TOTEM_OF_UNDYING && it.getItemMeta().getCustomModelData()==MODELS[i], "shop purchase/id/model " + IDS[i]);
   }
   p.closeInventory();
   cases(0);
 }
 private void cases(int i){
   if(i>=IDS.length){regen();return;}
   reset(p);
   ItemStack item=purchased.get(IDS[i]); if(item==null){fail("missing purchase " + IDS[i]); cases(i+1);return;}
   p.getInventory().setItemInOffHand(item);
   later(45, () -> {
     ok(Math.abs(p.getMaxHealth()-(20+HP[i]))<0.01, IDS[i]+" max health expected="+(20+HP[i])+" actual="+p.getMaxHealth());
     for(String spec:EFFECTS[i]){
       String[] parts=spec.split(":"); PotionEffectType type=ColorUtil.potType(parts[0],parts[0].toUpperCase(Locale.ROOT));
       PotionEffect effect=type==null?null:p.getPotionEffect(type);
       ok(effect!=null && effect.getAmplifier()==Integer.parseInt(parts[1]),IDS[i]+" effect "+spec+" actual="+effect);
     }
     cases(i+1);
   });
 }
 private void regen(){
   reset(p);p.getInventory().setItemInOffHand(purchased.get("shar_chimery")); p.setHealth(4);
   later(180, () -> {
     ok(p.getHealth()>=6 && p.getHealth()<=9,"regeneration actually heals with normal cadence (180 ticks), hp="+p.getHealth());
     mainHandOnly();
   });
 }
 private void mainHandOnly(){
   reset(p);p.getInventory().setItemInMainHand(purchased.get("shar_titana"));
   later(95, () -> {
     ok(Math.abs(p.getMaxHealth()-20)<0.01 && !p.hasPotionEffect(ColorUtil.potType("strength","INCREASE_DAMAGE")),"main hand gives no passive bonuses");
     reset(p);p.setGameMode(GameMode.SPECTATOR);p.getInventory().setItemInOffHand(purchased.get("shar_santy"));
     later(50, () -> {
       ok(Math.abs(p.getMaxHealth()-20)<0.01 && !p.hasPotionEffect(ColorUtil.potType("strength","INCREASE_DAMAGE")),"spectator has no orb bonuses");
       externalHealth();
     });
   });
 }
 private void externalHealth(){
   reset(p);max(p).setBaseValue(30);
   max(p).addModifier(new AttributeModifier(new NamespacedKey("probe","other_health"),5,AttributeModifier.Operation.ADD_NUMBER,EquipmentSlotGroup.ANY));
   p.addPotionEffect(new PotionEffect(ColorUtil.potType("health_boost","HEALTH_BOOST"),500,1));
   p.addPotionEffect(new PotionEffect(ColorUtil.potType("strength","INCREASE_DAMAGE"),500,3));
   p.getInventory().setItemInOffHand(purchased.get("shar_titana"));
   p.getInventory().setChestplate(ItemFactory.elytraTitan());
   later(60, () -> {
     ok(Math.abs(max(p).getBaseValue()-30)<0.01 && Math.abs(p.getMaxHealth()-59)<0.01,
          "orb+elytra+external base/modifier/potion stack: base="+max(p).getBaseValue()+" max="+p.getMaxHealth());
     PotionEffect strength=p.getPotionEffect(ColorUtil.potType("strength","INCREASE_DAMAGE"));
     ok(strength!=null && strength.getAmplifier()==3 && strength.getDuration()>400,"strong external effect preserved");
     p.getInventory().setItemInOffHand(null);p.getInventory().setChestplate(null);
     later(60, () -> {
       ok(Math.abs(p.getMaxHealth()-43)<0.01 && max(p).getBaseValue()==30,"removal preserves external health: "+p.getMaxHealth());
       for(AttributeModifier m:max(p).getModifiers()) if(m.getKey().equals(new NamespacedKey("probe","other_health")))max(p).removeModifier(m);
       reset(p); auraEnemy();
     });
   });
 }
 private void auraEnemy(){
   reset(p);reset(enemy);World w=Bukkit.getWorlds().get(0);
   p.teleport(new Location(w,0.5,70,0.5));enemy.teleport(new Location(w,3.5,70,0.5));
   p.getInventory().setItemInOffHand(purchased.get("shar_morozko"));
   later(45, () -> {
     ok(enemy.getFreezeTicks()>0 && enemy.hasPotionEffect(ColorUtil.potType("slowness","SLOW")),"frost aura applies to a nearby enemy");
     w.setPVP(false);enemy.removePotionEffect(ColorUtil.potType("slowness","SLOW"));enemy.setFreezeTicks(0);
     later(45, () -> {
       ok(enemy.getFreezeTicks()==0 && !enemy.hasPotionEffect(ColorUtil.potType("slowness","SLOW")),"frost aura respects world PvP disabled");
       w.setPVP(true);core.getStore().addMutualTeammates(p.getName(),enemy.getName());
       later(45, () -> {
         ok(enemy.getFreezeTicks()==0 && !enemy.hasPotionEffect(ColorUtil.potType("slowness","SLOW")),"frost aura spares teammates");
         core.getStore().removeMutualTeammates(p.getName(),enemy.getName());
         enemy.teleport(new Location(w,5.5,70,5.5));enemy.setFreezeTicks(0);
         later(45, () -> {
           ok(enemy.getFreezeTicks()==0 && !enemy.hasPotionEffect(ColorUtil.potType("slowness","SLOW")),"frost aura respects 6-block spherical radius");
           Location safe=core.getSpawnEngine().getSpawnArrival();p.teleport(safe);enemy.teleport(safe.clone().add(2,0,0));enemy.setFreezeTicks(0);
           later(45, () -> {
             ok(enemy.getFreezeTicks()==0 && !enemy.hasPotionEffect(ColorUtil.potType("slowness","SLOW")),"frost aura disabled in safe spawn");
             normalWorld(); resurrection(0);
           });
         });
       });
     });
   });
 }
 private void normalWorld(){
   World w=Bukkit.getWorlds().get(0);p.teleport(new Location(w,0.5,70,0.5));enemy.teleport(new Location(w,10.5,70,0.5));reset(p);reset(enemy);
 }
 private void hit(){p.setNoDamageTicks(0);p.setLastDamage(0);p.damage(1000.0);}
 private void resurrection(int i){
   if(i>=IDS.length){dualHands();return;}
   reset(p);p.getInventory().setItemInOffHand(purchased.get(IDS[i]));lastResurrectHand=null;
   hit();later(4, () -> {
     ItemStack left=p.getInventory().getItemInOffHand();
     ok(!p.isDead() && p.getHealth()>=19 && (left==null||left.getType()==Material.AIR) && lastResurrectHand==EquipmentSlot.OFF_HAND,
          IDS[i]+" saves once from real lethal damage, consumed, hp="+p.getHealth()+" hand="+lastResurrectHand);
     if(p.isDead()){finish();return;} resurrection(i+1);
   });
 }
 private void dualHands(){
   reset(p);p.getInventory().setItemInMainHand(new ItemStack(Material.TOTEM_OF_UNDYING));p.getInventory().setItemInOffHand(purchased.get("shar_titana"));
   lastResurrectHand=null;hit();later(4, () -> {
     ok(!p.isDead() && lastResurrectHand==EquipmentSlot.HAND && "shar_titana".equals(ItemFactory.getCustomId(p.getInventory().getItemInOffHand()))
         && p.getHealth()<10,"vanilla main-hand totem chosen over off-hand orb; no false orb proc; hand="+lastResurrectHand+" hp="+p.getHealth());
     cancelEvent();
   });
 }
 private void cancelEvent(){
   reset(p);p.setHealth(8);p.getInventory().setItemInOffHand(ItemFactory.totemSanty(3));
   double coins=core.getStore().getCoins(p.getName());cancelResurrection=true;
   EntityResurrectEvent event=new EntityResurrectEvent(p,EquipmentSlot.OFF_HAND);event.setCancelled(false);Bukkit.getPluginManager().callEvent(event);
   later(4, () -> {
     ok(event.isCancelled() && p.getHealth()==8 && ItemFactory.getTotemCharges(p.getInventory().getItemInOffHand())==3
         && core.getStore().getCoins(p.getName())==coins,"cancelled resurrection grants nothing and consumes no charge");
     charged(0);
   });
 }
 private ItemStack chargedItem(int i){switch(i){case 0:return ItemFactory.talFeniksa(3);case 1:return ItemFactory.talOwner(5);case 2:return ItemFactory.talBezdn(2);case 3:return ItemFactory.totemLedyanoy(3);case 4:return ItemFactory.totemVoskresheniya(5);default:return ItemFactory.totemSanty(3);}}
 private void charged(int i){
   if(i==6){slotRace();return;}
   reset(p);ItemStack item=chargedItem(i);String id=ItemFactory.getCustomId(item);int charges=ItemFactory.getTotemCharges(item);
   ItemMeta meta=item.getItemMeta();meta.getPersistentDataContainer().set(new NamespacedKey("probe","kept"),PersistentDataType.STRING,"yes");item.setItemMeta(meta);
   p.getInventory().setItemInMainHand(item);p.getInventory().setItemInOffHand(purchased.get("shar_titana"));hit();later(4, () -> {
     ItemStack next=p.getInventory().getItemInMainHand();
     boolean preserved=next!=null && next.hasItemMeta() && "yes".equals(next.getItemMeta().getPersistentDataContainer().get(new NamespacedKey("probe","kept"),PersistentDataType.STRING));
     ok(!p.isDead() && lastResurrectHand==EquipmentSlot.HAND && id.equals(ItemFactory.getCustomId(next)) && ItemFactory.getTotemCharges(next)==charges-1
         && "shar_titana".equals(ItemFactory.getCustomId(p.getInventory().getItemInOffHand())) && preserved,
         "charged totem "+id+" decremented in used hand and metadata preserved");
     if(p.isDead()){finish();return;} charged(i+1);
   });
 }
 private void slotRace(){
   reset(p);ItemStack item=ItemFactory.totemLedyanoy(3);p.getInventory().setItemInOffHand(item);hit();
   p.getInventory().setItemInOffHand(new ItemStack(Material.DIAMOND));
   later(4, () -> {
     ItemStack found=null;for(ItemStack it:p.getInventory().getStorageContents())if("totem_ledyanoy".equals(ItemFactory.getCustomId(it)))found=it;
     ok(p.getInventory().getItemInOffHand().getType()==Material.DIAMOND && ItemFactory.getTotemCharges(found)==2,"charged totem never overwrites a newly occupied hand");
     reset(p);p.getInventory().setItemInOffHand(ItemFactory.totemLedyanoy(1));hit();later(4, () -> {
       ok(!p.isDead() && p.getInventory().getItemInOffHand().getType()==Material.AIR,"final charge is consumed exactly once");
       reset(p);p.getInventory().setItemInOffHand(ItemFactory.totemRw());hit();later(4, () -> {
         PotionEffect regen=p.getPotionEffect(ColorUtil.potType("regeneration","REGENERATION"));
         ok(regen!=null && regen.getAmplifier()==2,"RW totem grants its advertised regeneration III");
         magnet();
       });
     });
   });
 }
 private void magnet(){
   reset(p);reset(enemy);p.getInventory().setItem(20,ItemFactory.lootMagnet());
   Location origin=p.getLocation().clone().add(5,0,0);
   Item free=p.getWorld().dropItem(origin,new ItemStack(Material.DIAMOND));free.setPickupDelay(0);
   Item protectedItem=p.getWorld().dropItem(origin,new ItemStack(Material.EMERALD));protectedItem.setOwner(enemy.getUniqueId());protectedItem.setPickupDelay(0);
   later(45, () -> {
     ok(!free.isValid() || free.getLocation().distanceSquared(p.getLocation())<9,"magnet works from non-hotbar inventory");
     ok(protectedItem.isValid() && protectedItem.getLocation().distanceSquared(p.getLocation())>9,"magnet respects another player's owned drop");
     if(free.isValid())free.remove();if(protectedItem.isValid())protectedItem.remove();scrollSafety();
   });
 }
 private void scrollSafety(){
   reset(p);reset(enemy);Location safe=core.getSpawnEngine().getSpawnArrival();p.teleport(safe);enemy.teleport(safe.clone().add(1,0,0));
   ItemStack sword=new ItemStack(Material.DIAMOND_SWORD);ItemFactory.applyScrollToTool(sword,"ice");p.getInventory().setItemInMainHand(sword);
   later(15, () -> {
     enemy.setNoDamageTicks(0);enemy.damage(1.0,p);
     later(3, () -> {
       ok(enemy.getFreezeTicks()==0 && !enemy.hasPotionEffect(ColorUtil.potType("slowness","SLOW")),"cancelled safe-zone hit does not proc scroll");
       normalWorld();enemy.teleport(p.getLocation().clone().add(1,0,0));p.getInventory().setItemInMainHand(sword);
       later(15, () -> {
         enemy.setNoDamageTicks(0);enemy.damage(1.0,p);
         later(3, () -> {
           ok(enemy.getFreezeTicks()>0 && enemy.hasPotionEffect(ColorUtil.potType("slowness","SLOW")),"accepted PvP hit triggers ice scroll");
           blastGuard();
         });
       });
     });
   });
 }
 private void arrowTests(int test){
   if(test>=4){protectionTest(0);return;}
   normalWorld();
   World w=p.getWorld();
   p.teleport(new Location(w,0.5,70,0.5,-90f,0f));enemy.teleport(new Location(w,6.5,70,0.5));
   p.getInventory().setHeldItemSlot(0);p.getInventory().setItemInMainHand(new ItemStack(Material.BOW));
   if(test==0){p.getInventory().setItem(1,new ItemStack(Material.ARROW,10));p.getInventory().setItem(2,ItemFactory.arrowIce(10));}
   if(test==1){p.getInventory().setItem(1,ItemFactory.arrowIce(10));p.getInventory().setItem(2,new ItemStack(Material.ARROW,10));}
   if(test==2){p.getInventory().setItem(20,ItemFactory.arrowIce(10));}
   if(test==3){p.getInventory().setItemInOffHand(ItemFactory.arrowIce(10));p.getInventory().setItem(1,new ItemStack(Material.ARROW,10));}
   lastArrow=null;lastAmmoId=null;shotHit=false;
   later(test==0 ? 70 : 20, () -> {
     getLogger().info("KG-ORBS REQUEST_SHOT " + test);
     later(80, () -> {
       String projectileTag=lastArrow==null?null:lastArrow.getPersistentDataContainer().get(new NamespacedKey("kinggrief","arrow_type"),PersistentDataType.STRING);
       boolean custom=test!=0;
       ok(lastArrow!=null && shotHit, "real bow shot reaches player case="+test);
       ok(custom ? "arrow_ice".equals(lastAmmoId) && "arrow_ice".equals(projectileTag)
                 : lastAmmoId==null && projectileTag==null,
          "projectile matches actual consumed arrow case="+test+" ammo="+lastAmmoId+" tag="+projectileTag);
       ok(custom ? enemy.getFreezeTicks()>0 : enemy.getFreezeTicks()==0,
          "actual impact "+(custom?"freezes":"does not falsely freeze")+" case="+test+" ticks="+enemy.getFreezeTicks());
       if(lastArrow!=null && lastArrow.isValid())lastArrow.remove();
       arrowTests(test+1);
     });
   });
 }
 private void protectionTest(int test){
   if(test>=3){if(getConfig().getBoolean("protect-only"))finish();else arrowAbilities(0);return;}
   normalWorld();World w=p.getWorld();core.getHud().clearCombat(p.getUniqueId());core.getHud().clearCombat(enemy.getUniqueId());
   p.teleport(new Location(w,0.5,70,0.5,-90f,0f));enemy.teleport(new Location(w,6.5,70,0.5));
   p.getInventory().setHeldItemSlot(0);p.getInventory().setItemInMainHand(new ItemStack(Material.BOW));
   p.getInventory().setItem(1,test==0?ItemFactory.arrowIce(4):ItemFactory.arrowExplosive(4));
   explosionWitness=(LivingEntity)w.spawnEntity(new Location(w,7.5,70,1.5),org.bukkit.entity.EntityType.COW);
   explosionWitness.setAI(false);explosionWitness.setGravity(false);
   explosionWitness.getAttribute((Attribute)Registry.ATTRIBUTE.get(NamespacedKey.minecraft("max_health"))).setBaseValue(50);explosionWitness.setHealth(50);
   lastArrow=null;cancelledArrowSeen=false;shotHit=false;explosionDamageCount=0;cancelArrowAtHighest=test<2;
   later(test==0?70:30, () -> {
     getLogger().info("KG-ORBS REQUEST_SHOT protected-"+test);
     later(80, () -> {
       if(test<2){
         ok(lastArrow!=null && cancelledArrowSeen,"real custom arrow was cancelled by another protection listener case="+test);
         ok(enemy.getHealth()==20 && enemy.getFreezeTicks()==0 && !enemy.hasPotionEffect(ColorUtil.potType("slowness","SLOW")),
            "cancelled projectile causes no damage or status effects case="+test+" hp="+enemy.getHealth()+" freeze="+enemy.getFreezeTicks());
         ok(explosionDamageCount==0 && explosionWitness.getHealth()==50 && !core.getHud().isInCombat(p) && !core.getHud().isInCombat(enemy),"cancelled projectile causes no explosion or combat penalty case="+test);
       }else{
         ok(lastArrow!=null && shotHit && enemy.getHealth()<20,"accepted explosive arrow deals direct damage");
         ok(explosionDamageCount==1 && explosionWitness.getHealth()<50,"accepted explosive arrow damages a nearby mob once via explosion, count="+explosionDamageCount+" hp="+explosionWitness.getHealth());
         ok(core.getHud().isInCombat(p) && core.getHud().isInCombat(enemy),"accepted player hit starts combat timer");
       }
       cancelArrowAtHighest=false;explosionWitness.remove();explosionWitness=null;
       if(lastArrow!=null && lastArrow.isValid())lastArrow.remove();
       protectionTest(test+1);
     });
   });
 }
 private void arrowAbilities(int test){
   if(test>=7){if(getConfig().getBoolean("abilities-only"))finish();else crossbowTests(0);return;}
   normalWorld();World w=p.getWorld();
   p.teleport(new Location(w,0.5,70,0.5,-90f,0f));enemy.teleport(new Location(w,6.5,70,0.5));
   max(enemy).setBaseValue(60);enemy.setHealth(60);p.setHealth(10);
   p.getInventory().setHeldItemSlot(0);p.getInventory().setItemInMainHand(new ItemStack(Material.BOW));
   ItemStack[] arrows={ItemFactory.arrowPoison(4),ItemFactory.arrowLightning(4),ItemFactory.arrowVampire(4),
     ItemFactory.arrowAntiFly(4),ItemFactory.arrowThor(4),ItemFactory.arrowFrostNova(4),ItemFactory.arrowGold(4)};
   String[] ids={"arrow_poison","arrow_lightning","arrow_vampire","arrow_anti_fly","arrow_thor","arrow_frostnova","arrow_gold"};
   p.getInventory().setItem(1,arrows[test]);
   if(test==3){enemy.getInventory().setChestplate(new ItemStack(Material.ELYTRA));p.teleport(new Location(w,0.5,70,0.5,-90f,12f));}
   if(test==4 || test==5){
     explosionWitness=(LivingEntity)w.spawnEntity(new Location(w,7.5,70,1.5),org.bukkit.entity.EntityType.COW);
     explosionWitness.setAI(false);explosionWitness.setGravity(false);
     explosionWitness.getAttribute((Attribute)Registry.ATTRIBUTE.get(NamespacedKey.minecraft("max_health"))).setBaseValue(50);explosionWitness.setHealth(50);
   }
   if(test==6){core.getStore().setCoins(p.getName(),100);core.getStore().setCoins(enemy.getName(),200);}
   lastArrow=null;lastAmmoId=null;shotHit=false;lightningSeen=false;arrowDamage=0;glidingBefore=false;glidingAfter=false;
   later(test==0?70:30, () -> {
     getLogger().info("KG-ORBS "+(test==3?"REQUEST_SHOT_DOWN ":"REQUEST_SHOT ")+"ability-"+test);
     later(80, () -> {
       String tag=lastArrow==null?null:lastArrow.getPersistentDataContainer().get(new NamespacedKey("kinggrief","arrow_type"),PersistentDataType.STRING);
       ok(lastArrow!=null && shotHit && ids[test].equals(lastAmmoId) && ids[test].equals(tag),"real custom arrow hit and ammunition id "+ids[test]+" shot="+shotHit+" ammo="+lastAmmoId+" tag="+tag);
       if(test==0)ok(enemy.hasPotionEffect(ColorUtil.potType("poison","POISON")) && enemy.hasPotionEffect(ColorUtil.potType("wither","WITHER"))
           && enemy.hasPotionEffect(ColorUtil.potType("weakness","WEAKNESS")),"poison arrow applies three status effects");
       if(test==1)ok(lightningSeen && arrowDamage>=6 && enemy.getCooldown(Material.SHIELD)>0,"lightning arrow deals bonus damage and disables shield");
       if(test==2)ok(Math.abs(p.getHealth()-15.0)<0.01 && arrowDamage>=5,"vampire arrow heals shooter and adds damage, hp="+p.getHealth());
       if(test==3)ok(shotHit && glidingBefore && !glidingAfter,"anti-flight arrow stops active glide");
       if(test==4)ok(arrowDamage>=8 && explosionWitness.getHealth()<50,"Thor arrow adds damage and chains to nearby mob");
       if(test==5)ok(enemy.getFreezeTicks()>0 && explosionWitness.getFreezeTicks()>0 && explosionWitness.hasPotionEffect(ColorUtil.potType("slowness","SLOW")),"frost nova freezes victim and nearby mob");
       if(test==6)ok(core.getStore().getCoins(p.getName())==250 && core.getStore().getCoins(enemy.getName())==50,"gold arrow transfers 150 coins once");
       if(explosionWitness!=null){explosionWitness.remove();explosionWitness=null;}
       if(lastArrow!=null && lastArrow.isValid())lastArrow.remove();
       arrowAbilities(test+1);
     });
   });
 }
 private void crossbowTests(int test){
   if(test>=2){if(getConfig().getBoolean("crossbows-only"))finish();else bossArrow();return;}
   normalWorld();World w=p.getWorld();
   p.teleport(new Location(w,0.5,70,0.5,-90f,0f));enemy.teleport(new Location(w,6.5,70,0.5));
   p.getInventory().setHeldItemSlot(0);p.getInventory().setItemInMainHand(new ItemStack(Material.CROSSBOW));
   if(test==0){p.getInventory().setItem(1,new ItemStack(Material.ARROW,4));p.getInventory().setItem(2,ItemFactory.arrowIce(4));}
   if(test==1)p.getInventory().setItem(20,ItemFactory.arrowIce(4));
   lastArrow=null;lastAmmoId=null;shotHit=false;
   later(test==0?70:30, () -> {
     getLogger().info("KG-ORBS REQUEST_CROSSBOW "+test);
     later(100, () -> {
       String tag=lastArrow==null?null:lastArrow.getPersistentDataContainer().get(new NamespacedKey("kinggrief","arrow_type"),PersistentDataType.STRING);
       ok(lastArrow!=null && shotHit,"real crossbow shot reaches player case="+test);
       ok(test==0 ? lastAmmoId==null && tag==null : "arrow_ice".equals(tag),
          "crossbow tags the actual loaded ammunition case="+test+" ammo="+lastAmmoId+" tag="+tag);
       ok(test==0 ? enemy.getFreezeTicks()==0 : enemy.getFreezeTicks()>0,"crossbow impact effect case="+test+" ticks="+enemy.getFreezeTicks());
       if(lastArrow!=null && lastArrow.isValid())lastArrow.remove();
       crossbowTests(test+1);
     });
   });
 }
 private void bossArrow(){
   normalWorld();World w=p.getWorld();
   p.teleport(new Location(w,0.5,70,0.5,-90f,0f));enemy.teleport(new Location(w,0.5,70,15.5));
   LivingEntity boss=(LivingEntity)w.spawnEntity(new Location(w,6.5,70,0.5),org.bukkit.entity.EntityType.COW);
   boss.setAI(false);boss.setGravity(false);boss.addScoreboardTag("kg_event_boss");
   AttributeInstance bossMax=boss.getAttribute((Attribute)Registry.ATTRIBUTE.get(NamespacedKey.minecraft("max_health")));
   bossMax.setBaseValue(50);boss.setHealth(50);
   p.getInventory().setHeldItemSlot(0);p.getInventory().setItemInMainHand(new ItemStack(Material.BOW));p.getInventory().setItem(1,ItemFactory.arrowIce(4));
   lastArrow=null;lastAmmoId=null;bossHit=false;
   later(70, () -> {
     getLogger().info("KG-ORBS REQUEST_SHOT boss");
     later(80, () -> {
       ok(bossHit && lastArrow!=null,"real custom bow shot reaches event-tagged boss");
       ok(bossHit && boss.getFreezeTicks()>0 && boss.hasPotionEffect(ColorUtil.potType("slowness","SLOW")),
            "custom arrow effects apply to event boss, freeze="+boss.getFreezeTicks());
       boss.remove();if(lastArrow!=null && lastArrow.isValid())lastArrow.remove();
       if(getConfig().getBoolean("boss-only"))finish();else throwTests(0);
     });
   });
 }
 private void throwTests(int test){
   if(test>=5){finish();return;}
   normalWorld();World w=p.getWorld();
   p.teleport(new Location(w,0.5,70,0.5,-90f,0f));enemy.teleport(new Location(w,6.5,70,0.5));
   p.getInventory().setHeldItemSlot(0);
   if(test==0){p.getInventory().setItemInMainHand(new ItemStack(Material.SNOWBALL,4));p.getInventory().setItemInOffHand(ItemFactory.snezokZamorozki(4));}
   if(test==1){p.getInventory().setItemInMainHand(ItemFactory.snezokZamorozki(4));p.getInventory().setItemInOffHand(new ItemStack(Material.SNOWBALL,4));}
   if(test==2)p.getInventory().setItemInMainHand(ItemFactory.tridentPoseidon());
   if(test==3){p.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));p.getInventory().setItemInOffHand(ItemFactory.snezokZamorozki(4));}
   if(test==4){p.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));p.getInventory().setItemInOffHand(ItemFactory.tridentPoseidon());}
   lastThrown=null;thrownHit=false;thrownDamage=0;lightningSeen=false;
   later(test==0?70:30, () -> {
     String request=test==2?"REQUEST_SHOT ":test==4?"REQUEST_SHOT_OFF ":test==3?"REQUEST_THROW_OFF ":"REQUEST_THROW ";
     getLogger().info("KG-ORBS "+request+"throw-"+test);
     later(80, () -> {
       String tag=lastThrown==null?null:lastThrown.getPersistentDataContainer().get(new NamespacedKey("kinggrief","proj_type"),PersistentDataType.STRING);
       ok(lastThrown!=null && thrownHit,"real thrown projectile reaches player case="+test);
       boolean trident=test==2||test==4;
       boolean correct=test==0?tag==null:trident?"trident_poseidon".equals(tag):"frost_ball".equals(tag);
       ok(correct,"thrown projectile uses actual item's identity case="+test+" tag="+tag);
       if(!trident)ok(test==0?enemy.getFreezeTicks()==0:enemy.getFreezeTicks()>0,"snowball impact effects case="+test+" ticks="+enemy.getFreezeTicks());
       else ok(thrownDamage>=12 && lightningSeen,"trident bonus damage and lightning effect case="+test+" damage="+thrownDamage+" lightning="+lightningSeen);
       if(lastThrown!=null && lastThrown.isValid())lastThrown.remove();
       throwTests(test+1);
     });
   });
 }
 private void blastGuard(){
   reset(p);reset(enemy);ItemStack sword=new ItemStack(Material.DIAMOND_SWORD);ItemFactory.applyScrollToTool(sword,"blast");p.getInventory().setItemInMainHand(sword);
   Location where=p.getLocation().clone().add(2,0,1);LivingEntity cow=(LivingEntity)p.getWorld().spawnEntity(where,org.bukkit.entity.EntityType.COW);cow.setAI(false);
   enemy.setNoDamageTicks(0);enemy.damage(1.0,p);
   later(5, () -> {
     ok(cow.isValid() && cow.getHealth()>1 && cow.getHealth()<cow.getMaxHealth(),"blast hits nearby mob once without recursive proc, hp="+cow.getHealth());
     cow.remove();arrowTests(0);
   });
 }
}
