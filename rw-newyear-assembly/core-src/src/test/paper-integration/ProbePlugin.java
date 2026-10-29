package net.kinggrief.probe;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import net.kinggrief.core.BossEventManager;
import net.kinggrief.core.DataStore;
import net.kinggrief.core.GameplayListener;
import net.kinggrief.core.GuiManager;
import net.kinggrief.core.RankManager;
import net.kinggrief.core.VoidSpawnWorldGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.block.Action;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import net.kinggrief.core.ColorUtil;
import net.kinggrief.core.ItemFactory;
import net.kinggrief.core.LegacyItemModelMigration;
import net.kinggrief.core.KingGriefCore;
import net.kinggrief.common.ResourcePackSettings;

public final class ProbePlugin extends JavaPlugin implements Listener {
 private Method getModel, setModel;
 private Location protectedExplosionBlock;
 private boolean cancelProbeExplosion;
 @EventHandler(priority=EventPriority.NORMAL, ignoreCancelled=true)
 public void simulateOtherProtection(EntityExplodeEvent e) {
  if (protectedExplosionBlock == null) return;
  if (cancelProbeExplosion) e.setCancelled(true);
  else e.blockList().removeIf(block -> block.getLocation().equals(protectedExplosionBlock));
 }
 public void onEnable() {
  try {
   getModel=ItemMeta.class.getMethod("getItemModel");
   setModel=ItemMeta.class.getMethod("setItemModel",NamespacedKey.class);
  } catch (ReflectiveOperationException ex) {throw new RuntimeException(ex);}
  Bukkit.getPluginManager().registerEvents(this,this);
 }
 private ItemStack oldTotem() throws Exception {
  ItemStack s=ItemFactory.totemSanty(3); ItemMeta m=s.getItemMeta();
  setModel.invoke(m,new NamespacedKey("kinggrief","totem_santy")); s.setItemMeta(m); return s;
 }
 private void checkMigrated(ItemStack item,String where) throws Exception {
  if(item==null || !"totem_santy".equals(ItemFactory.getCustomId(item)) || ItemFactory.getTotemCharges(item)!=3 || item.getItemMeta().getCustomModelData()!=1252 || getModel.invoke(item.getItemMeta())!=null)
   throw new AssertionError(where+" migration failed: "+item);
 }
 @EventHandler(priority=EventPriority.LOWEST)
 public void seedOldItems(PlayerJoinEvent e) {
  try {
   e.getPlayer().getInventory().setItem(5,oldTotem());
   e.getPlayer().getEnderChest().setItem(0,oldTotem());
  } catch(Exception ex) {throw new RuntimeException(ex);}
 }
 @EventHandler(priority=EventPriority.MONITOR)
 public void onJoin(PlayerJoinEvent e) {
  final Player p=e.getPlayer();
  Bukkit.getScheduler().runTaskLater(this,new Runnable() {public void run(){
   try {
    KingGriefCore core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");
    if (getConfig().getBoolean("expect-luckperms") && !Bukkit.getPluginManager().isPluginEnabled("LuckPerms"))
     throw new AssertionError("LuckPerms did not load");
    ResourcePackSettings.Pack rp=ResourcePackSettings.resolve(core.getConfig().getString("resourcepack-url"),core.getConfig().getString("resourcepack-sha1"));
    if(!rp.upgraded || !rp.url.endsWith("v66.zip")) throw new AssertionError("old config not upgraded");
    checkMigrated(p.getInventory().getItem(5),"join inventory");
    checkMigrated(p.getEnderChest().getItem(0),"join ender chest");
    ItemStack fresh=ItemFactory.totemSanty(3);
    if(getModel.invoke(fresh.getItemMeta())!=null || !fresh.getItemMeta().hasCustomModelData() || fresh.getItemMeta().getCustomModelData()!=1252) throw new AssertionError("fresh item model/CMD");
    if(LegacyItemModelMigration.repairItem(fresh)) throw new AssertionError("fresh item was modified");

    Inventory container=Bukkit.createInventory(null,9,"Migration check");
    container.setItem(0,oldTotem()); p.openInventory(container);
    checkMigrated(container.getItem(0),"container open");

    Item dropped=p.getWorld().dropItem(p.getLocation(),oldTotem());
    EntityPickupItemEvent pickup=new EntityPickupItemEvent(p,dropped,0);
    Bukkit.getPluginManager().callEvent(pickup);
    checkMigrated(dropped.getItemStack(),"item pickup"); dropped.remove();

    ItemStack thirdParty=oldTotem(); ItemMeta other=thirdParty.getItemMeta();
    setModel.invoke(other,new NamespacedKey("example","my_model")); thirdParty.setItemMeta(other);
    if(LegacyItemModelMigration.repairItem(thirdParty) || getModel.invoke(thirdParty.getItemMeta())==null) throw new AssertionError("third-party item changed");

    core.getGuis().openMegaShop(p); Inventory inv=p.getOpenInventory().getTopInventory();
    check(inv,10,Material.PAPER,1400,"scroll_ice");
    check(inv,19,Material.GLISTERING_MELON_SLICE,1300,"mega_heal");
    check(inv,28,Material.TOTEM_OF_UNDYING,1250,"totem_ledyanoy");
    check(inv,31,Material.TOTEM_OF_UNDYING,1112,"shar_kvadrat");
    ItemStack buff=ItemFactory.megaBuff(2); p.getInventory().setItemInMainHand(buff);
    PlayerInteractEvent click=new PlayerInteractEvent(p,Action.RIGHT_CLICK_AIR,p.getInventory().getItemInMainHand(),null,BlockFace.SELF,EquipmentSlot.HAND);
    Bukkit.getPluginManager().callEvent(click);
    if(!click.isCancelled() || p.getInventory().getItemInMainHand().getAmount()!=1 || !p.hasPotionEffect(ColorUtil.potType("strength","INCREASE_DAMAGE"))) throw new AssertionError("mega buff use");
    testRanksAndKits(core,p);
    testTntShop(core,p);
    // Wait for the teleport to update Paper's entity/chunk spatial index.
    Bukkit.getScheduler().runTaskLater(ProbePlugin.this, new Runnable() { public void run() {
     try {
      testTntEffects(core,p);
      testEvents(core,p);
      Runnable finish=() -> {
       try {
        testSpawnPreservation(core,p);
        getLogger().info("KG-PROBE PASS: migration, v66 pack wire, main-thread chat, rank hierarchy, kits, TNT shop/effects, events, safe spawn/backup/restore" + (getConfig().getBoolean("expect-luckperms") ? (getConfig().getBoolean("mock-lp") ? ", mock LP hierarchy/sync" : ", real LuckPerms hierarchy/sync") : "") + (getConfig().getBoolean("mock-wg") ? ", mock WG regions" : ""));
       } catch (Throwable t) { getLogger().severe("KG-PROBE FAIL: "+t); t.printStackTrace(); }
      };
      Runnable afterChat=() -> {
       try {
        if (getConfig().getBoolean("expect-luckperms")) {
         Class.forName(getConfig().getBoolean("mock-lp") ? "net.kinggrief.probe.MockLpAssertions" : "net.kinggrief.probe.LuckPermsAssertions").getMethod("run",org.bukkit.plugin.java.JavaPlugin.class,KingGriefCore.class,Player.class,Runnable.class).invoke(null,ProbePlugin.this,core,p,finish);
        } else finish.run();
       } catch (Throwable t) { getLogger().severe("KG-PROBE FAIL: "+t); t.printStackTrace(); }
      };
      testAsyncChat(core,p,afterChat);
     } catch(Throwable t) {getLogger().severe("KG-PROBE FAIL: "+t); t.printStackTrace();}
    }},10L);
   } catch(Throwable t) {getLogger().severe("KG-PROBE FAIL: "+t); t.printStackTrace();}
  }},50L);
 }

 private void testAsyncChat(KingGriefCore core, Player player, Runnable done) {
  core.getRanks().setRank(player.getName(), "dracula", false);
  final java.util.Set<Player> recipients=new java.util.HashSet<Player>(Bukkit.getOnlinePlayers());
  Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
   try {
    org.bukkit.event.player.AsyncPlayerChatEvent chat=new org.bukkit.event.player.AsyncPlayerChatEvent(true,player,"CHAT_PROBE &lplain &4red",recipients);
    Bukkit.getPluginManager().callEvent(chat);
    if (!chat.isCancelled()) throw new AssertionError("async chat was not delegated to main thread");
    Bukkit.getScheduler().runTaskLater(this,done,10L);
   } catch (Throwable t) { getLogger().severe("KG-PROBE FAIL: async chat: "+t); t.printStackTrace(); }
  });
 }

 private void testRanksAndKits(KingGriefCore core, Player p) {
  RankManager ranks=core.getRanks();
  String[] order={"owner","techadmin","curator_discord","curator_kp","curator_media","curator_dkp","curator","gladmin","stadmin","admin","mlmoder","sthelper","helper","kp"};
  for(int i=1;i<order.length;i++) if(ranks.byId(order[i-1]).weight<=ranks.byId(order[i]).weight) throw new AssertionError("rank order "+order[i]);
  Set<String> colors=new HashSet<String>();
  for(RankManager.RankDef def:ranks.allRanks()) if(def.priceRub>0 && !colors.add(def.chatTextColor)) throw new AssertionError("duplicate donor color "+def.id);
  Permission external=new Permission("kgprobe.external-node",PermissionDefault.FALSE);
  Bukkit.getPluginManager().addPermission(external);
  ranks.setRank(p.getName(),"techadmin",false); p.setGameMode(GameMode.CREATIVE);
  if(!ranks.isOwnerLevel(p) || !ranks.canModifySpawn(p) || !ranks.canClaimKit(p,"owner")
      || !p.hasPermission("kgprobe.external-node") || p.isOp())
      throw new AssertionError("TECH.ADMIN: rank="+ranks.getRank(p).id+" owner="+ranks.isOwnerLevel(p)+" creative="+ranks.canModifySpawn(p)+" kit="+ranks.canClaimKit(p,"owner")+" external="+p.hasPermission("kgprobe.external-node")+" opperm="+p.hasPermission("minecraft.command.op")+" op="+p.isOp());
  PlayerCommandPreprocessEvent opCommand=new PlayerCommandPreprocessEvent(p,"/minecraft:op Other");
  Bukkit.getPluginManager().callEvent(opCommand);
  if(!opCommand.isCancelled()) throw new AssertionError("TECH.ADMIN can grant vanilla OP");
  ranks.setRank(p.getName(),"helper",false); p.getInventory().clear();
  if(!ranks.canClaimKit(p,"helper") || ranks.canClaimKit(p,"mlmoder")) throw new AssertionError("helper kit hierarchy");
  core.getGuis().tryClaimKit(p,"mlmoder");
  if(!p.getInventory().isEmpty()) throw new AssertionError("HELPER acquired ML.MODER kit");
  core.getGuis().tryClaimKit(p,"helper");
  if(!p.getInventory().contains(Material.DIAMOND_SWORD)) throw new AssertionError("HELPER kit not granted");
  for(ItemStack item:ItemFactory.buildKitItems("helper")) if((item.getType().name().startsWith("NETHERITE_") && item.getType()!=Material.NETHERITE_SCRAP) || item.getType().name().contains("SHULKER_BOX")) throw new AssertionError("helper gear too strong");
  ranks.setRank(p.getName(),"mlmoder",false);
  if(!ranks.canClaimKit(p,"mlmoder") || !ranks.canClaimKit(p,"helper")) throw new AssertionError("ML.MODER kit permissions");
  ranks.setRank(p.getName(),"dadmin",false); p.getInventory().clear();
  core.getGuis().tryClaimKit(p,"mlmoder");
  if(!p.getInventory().isEmpty()) throw new AssertionError("donor bypassed staff kit hierarchy");

  ranks.setRank(p.getName(),"curator_kp",false);
  if (!getConfig().getBoolean("expect-luckperms")) {
  core.getCurator().executeRankChange(p,"ProbeSubject","curator_media",null);
  if(!"curator_media".equals(ranks.getRankByName("ProbeSubject").id)) throw new AssertionError("curator promotion below own rank");
  core.getCurator().executeRankChange(p,"ProbeSubject","curator_discord",null);
  if(!"curator_media".equals(ranks.getRankByName("ProbeSubject").id)) throw new AssertionError("curator promoted equal/higher");
  ranks.setRank("ProbeSubject","curator_discord",false);
  core.getCurator().executeRankChange(p,"ProbeSubject","default",null);
  if(!"curator_discord".equals(ranks.getRankByName("ProbeSubject").id)) throw new AssertionError("curator demoted higher offline target");
  }
  java.util.List<String> tabs=core.getCurator().onTabComplete(p,core.getCommand("curator"),"curator",new String[]{"give","ProbeSubject",""});
  for(String id:new String[]{"curator_discord","curator_kp","curator_media","curator_dkp","gladmin","stadmin"}) if(!tabs.contains(id)) throw new AssertionError("missing curator completion "+id);
  ranks.setRank(p.getName(),"helper",false);
  ranks.setRank("ProbeSubject","curator_discord",false);
  core.getCurator().executeRankChange(p,"ProbeSubject","default",null);
  if(!"curator_discord".equals(ranks.getRankByName("ProbeSubject").id)) throw new AssertionError("unauthorized curator API call");
 }
 private EntityExplodeEvent newExplosionEvent(TNTPrimed entity, Location loc) throws Exception {
  java.util.List<Block> blocks=new java.util.ArrayList<Block>();
  try {
   Class<?> result=Class.forName("org.bukkit.ExplosionResult");
   return (EntityExplodeEvent)EntityExplodeEvent.class.getConstructor(Entity.class,Location.class,java.util.List.class,float.class,result)
       .newInstance(entity,loc,blocks,Float.valueOf(1f),result.getEnumConstants()[0]);
  } catch (ClassNotFoundException ex) {
   return (EntityExplodeEvent)EntityExplodeEvent.class.getConstructor(Entity.class,Location.class,java.util.List.class,float.class)
       .newInstance(entity,loc,blocks,Float.valueOf(1f));
  }
 }
 private void testTntShop(KingGriefCore core, Player p) throws Exception {
  p.getInventory().clear(); core.getStore().setRelics(p.getName(),6000); core.getStore().setCoins(p.getName(),12345);
  core.getGuis().openCoinShopCat(p,"raid"); Inventory raid=p.getOpenInventory().getTopInventory();
  int[] slots={11,12,13,14,15,16,19,20,21,22};
  int[] prices={1,5,60,5000,8,12,20,30,25,40};
  for(int i=0;i<slots.length;i++) {
   ItemStack item=raid.getItem(slots[i]); Integer price=item.getItemMeta().getPersistentDataContainer().get(GuiManager.SHOP_PRICE_KEY,PersistentDataType.INTEGER);
   if(item.getType()!=Material.TNT || item.getAmount()!=1 || price==null || price.intValue()!=prices[i]) throw new AssertionError("TNT price/amount slot "+slots[i]);
  }
  InventoryClickEvent buy=new InventoryClickEvent(p.getOpenInventory(),InventoryType.SlotType.CONTAINER,14,ClickType.LEFT,InventoryAction.PICKUP_ALL);
  Bukkit.getPluginManager().callEvent(buy);
  if(!buy.isCancelled() || core.getStore().getRelics(p)!=1000 || core.getStore().getCoins(p.getName())!=12345) throw new AssertionError("TNT wrong currency");
  boolean nuke=false;
  for(ItemStack item:p.getInventory().getContents()) if(item!=null && "tnt_nuke".equals(ItemFactory.getCustomId(item)) && item.getAmount()==1) nuke=true;
  if(!nuke) throw new AssertionError("nuke not delivered");
  core.getStore().setRelics(p.getName(),0);
  buy=new InventoryClickEvent(p.getOpenInventory(),InventoryType.SlotType.CONTAINER,11,ClickType.LEFT,InventoryAction.PICKUP_ALL);
  Bukkit.getPluginManager().callEvent(buy);
  if(core.getStore().getRelics(p)!=0 || p.getInventory().contains(Material.TNT,2)) throw new AssertionError("TNT sold without balance");

  World world=VoidSpawnWorldGenerator.getSurvivalWorld();
  if(!p.teleport(new Location(world,200.5,150,200.5))) throw new AssertionError("test teleport failed");
  p.setGameMode(GameMode.SURVIVAL);
 }
 private void testTntEffects(KingGriefCore core, Player p) throws Exception {
  Field field=KingGriefCore.class.getDeclaredField("gameplayListener"); field.setAccessible(true);
  GameplayListener listener=(GameplayListener)field.get(core);
  Location center=p.getLocation().clone();
  listener.detonateCustomTnt(center,"tnt_storm",null);
  if(!p.hasPotionEffect(ColorUtil.potType("glowing","GLOWING"))) throw new AssertionError("storm TNT effect");
  listener.detonateCustomTnt(center,"tnt_gravity",null);
  if(!p.hasPotionEffect(ColorUtil.potType("slowness","SLOW"))) throw new AssertionError("gravity TNT effect");
  listener.detonateCustomTnt(center,"tnt_ender",null);
  if(!p.hasPotionEffect(ColorUtil.potType("blindness","BLINDNESS"))) throw new AssertionError("ender TNT effect");
  World world=p.getWorld(); Location blast=new Location(world,750.5,150,750.5);
  Block protectedBlock=world.getBlockAt(751,150,750), allowed=world.getBlockAt(749,150,750);
  protectedBlock.setType(Material.STONE,false); allowed.setType(Material.STONE,false);
  protectedExplosionBlock=protectedBlock.getLocation();
  TNTPrimed primed=listener.spawnCustomPrimedTnt(blast,"tnt_tier1",null,null);
  EntityExplodeEvent event=newExplosionEvent(primed,blast);
  Bukkit.getPluginManager().callEvent(event); primed.remove();
  if(protectedBlock.getType()!=Material.STONE || allowed.getType()!=Material.AIR) throw new AssertionError("TNT ignored filtered protection block list");
  allowed.setType(Material.STONE,false); cancelProbeExplosion=true;
  primed=listener.spawnCustomPrimedTnt(blast,"tnt_tier1",null,null);
  event=newExplosionEvent(primed,blast);
  Bukkit.getPluginManager().callEvent(event); primed.remove();
  if(!event.isCancelled() || allowed.getType()!=Material.STONE) throw new AssertionError("TNT ignored cancelled explosion");
  protectedExplosionBlock=null; cancelProbeExplosion=false;
 }
 private void testEvents(KingGriefCore core, Player p) throws Exception {
  World world=VoidSpawnWorldGenerator.getSurvivalWorld(); BossEventManager manager=core.getBossEvents();
  if (getConfig().getBoolean("mock-wg"))
   Class.forName("net.kinggrief.probe.MockWgAssertions").getMethod("run",BossEventManager.class,World.class).invoke(null,manager,world);
  org.bukkit.entity.ArmorStand foreignHolo=(org.bukkit.entity.ArmorStand)world.spawnEntity(new Location(world,280,151,280),org.bukkit.entity.EntityType.ARMOR_STAND);
  foreignHolo.addScoreboardTag("kg_holo");
  org.bukkit.entity.ArmorStand staleHolo=(org.bukkit.entity.ArmorStand)world.spawnEntity(new Location(world,281,151,280),org.bukkit.entity.EntityType.ARMOR_STAND);
  staleHolo.addScoreboardTag("kg_event_holo");
  manager.purgeOrphanEventEntitiesInChunk(foreignHolo.getLocation().getChunk());
  if (!foreignHolo.isValid() || staleHolo.isValid()) throw new AssertionError("event cleanup removed unrelated hologram or kept stale event holo");
  foreignHolo.remove();
  Location blocked=new Location(world,300,150,300); Block foreign=blocked.clone().add(4,0,0).getBlock();
  foreign.setType(Material.CHEST,false);
  ((Chest)foreign.getState()).getBlockInventory().setItem(0,new ItemStack(Material.DIAMOND,3));
  if(manager.spawnBossEvent("crystal",blocked)!=null || ((Chest)foreign.getState()).getBlockInventory().getItem(0)==null) throw new AssertionError("event overwrote chest");
  foreign.setType(Material.STONE,false);
  if(manager.spawnBossEvent("ruins",blocked)!=null || foreign.getType()!=Material.STONE) throw new AssertionError("event overwrote stone structure");
  foreign.setType(Material.AIR,false);
  DataStore.Claim claim=core.getStore().createClaim("Other",blocked,10);
  if(manager.spawnBossEvent("camp",blocked)!=null) throw new AssertionError("event spawned in protected claim");
  core.getStore().removeClaim(claim);
  Method tick=BossEventManager.class.getDeclaredMethod("tickSecond"); tick.setAccessible(true);
  int x=400;
  for(String id:new String[]{"crystal","ruins","camp"}) {
   Location site=new Location(world,x,150,400); x+=80;
   BossEventManager.ActiveBossEvent ev=manager.spawnBossEvent(id,site);
   if(ev==null || ev.template.kind==BossEventManager.EventKind.BOSS || ev.unlockCountdownSec<=0) throw new AssertionError("event did not start: "+id);
   Entity hologram=Bukkit.getEntity(ev.hologramStands.get(0));
   if(hologram==null || Math.abs(hologram.getLocation().getY()-site.getY())>4) throw new AssertionError("hologram height "+id);
   Block at=site.clone().add(3,1,0).getBlock();
   if(!manager.isProtectedEventStructureBlock(at,false)) throw new AssertionError("event placement protection "+id);
   ev.unlockCountdownSec=1; tick.invoke(manager);
   if(!ev.bossDefeated || site.getBlock().getType()!=Material.CHEST || ((Chest)site.getBlock().getState()).getBlockInventory().isEmpty()) throw new AssertionError("event unlock/loot "+id);
   at.setType(Material.DIAMOND_BLOCK,false); // Simulate another plugin's external change.
   ev.secondsLeft=1; tick.invoke(manager);
   if(manager.getActiveEvents().contains(ev) || site.getBlock().getType()!=Material.AIR || at.getType()!=Material.DIAMOND_BLOCK)
      throw new AssertionError("event cleanup or external build preservation "+id);
   at.setType(Material.AIR,false);
  }
 }
 private void testSpawnPreservation(KingGriefCore core, Player p) throws Exception {
  World spawn=Bukkit.getWorld("spawn_world");
  if(spawn==null || !new File(spawn.getWorldFolder(),".spawn_v66_royal_ok").isFile()) throw new AssertionError("fresh spawn not generated");
  p.teleport(new Location(spawn,0.5,101,52.5));
  Block chest=spawn.getBlockAt(50,101,50); chest.setType(Material.CHEST,false);
  ((Chest)chest.getState()).getBlockInventory().setItem(0,new ItemStack(Material.DIAMOND,7));
  Entity ownedByUser=spawn.spawnEntity(new Location(spawn,45.5,102,45.5),EntityType.ARMOR_STAND);
  if(!core.getSpawnEngine().initializeSpawnWorldAndBuild(false) || chest.getType()!=Material.CHEST || ownedByUser.isDead()) throw new AssertionError("automatic spawn initialization damaged existing world/entity");
  if(core.getSpawnEngine().initializeSpawnWorldAndBuild(true) || chest.getType()!=Material.CHEST) throw new AssertionError("rebuild with player in spawn was not refused");
  p.teleport(new Location(VoidSpawnWorldGenerator.getSurvivalWorld(),200.5,150,200.5));
  Path link=spawn.getWorldFolder().toPath().resolve("kg-test-link");
  Files.createSymbolicLink(link,Paths.get(".."));
  try {
   if(core.getSpawnEngine().initializeSpawnWorldAndBuild(true)) throw new AssertionError("rebuild followed/skipped a symlink");
   spawn=Bukkit.getWorld("spawn_world");
   chest=spawn.getBlockAt(50,101,50);
   if(!(chest.getState() instanceof Chest) || ((Chest)chest.getState()).getBlockInventory().getItem(0)==null) throw new AssertionError("failed backup damaged world");
  } finally { Files.delete(link); }
  if(!core.getSpawnEngine().initializeSpawnWorldAndBuild(true)) throw new AssertionError("safe backup and rebuild failed");
  File[] copies=new File(core.getDataFolder(),"backups").listFiles((dir,name)->name.endsWith(".zip"));
  if(copies==null || copies.length!=1) throw new AssertionError("expected one verified backup");
  Path restored=new File(Bukkit.getWorldContainer(),"kg_restore_probe").toPath().toAbsolutePath().normalize(); Files.createDirectories(restored);
  try(ZipFile zip=new ZipFile(copies[0])) {
   ZipEntry level=zip.getEntry("level.dat"), region=zip.getEntry("region/r.0.0.mca");
   if(level==null || level.getSize()<64 || region==null || region.getSize()<64) throw new AssertionError("backup missing world data");
   java.util.Enumeration<? extends ZipEntry> entries=zip.entries();
   while(entries.hasMoreElements()) {
    ZipEntry entry=entries.nextElement(); Path dest=restored.resolve(entry.getName()).normalize();
    if(!dest.startsWith(restored)) throw new AssertionError("bad backup entry");
    Files.createDirectories(dest.getParent());
    try(InputStream in=zip.getInputStream(entry)) { Files.copy(in,dest); }
   }
  }
  Files.deleteIfExists(restored.resolve("uid.dat"));
  World copy=Bukkit.createWorld(new WorldCreator("kg_restore_probe").generator(new VoidSpawnWorldGenerator()));
  if(copy==null) throw new AssertionError("backup did not load");
  Block saved=copy.getBlockAt(50,101,50);
  if(!(saved.getState() instanceof Chest) || ((Chest)saved.getState()).getBlockInventory().getItem(0)==null
     || ((Chest)saved.getState()).getBlockInventory().getItem(0).getAmount()!=7) throw new AssertionError("backup restore lost chest contents");
  Bukkit.unloadWorld(copy,false);
 }
 private static void check(Inventory inv,int slot,Material type,int cmd,String id){
  ItemStack item=inv.getItem(slot); ItemMeta meta=item.getItemMeta();
  if(item.getType()!=type || !meta.hasCustomModelData() || meta.getCustomModelData()!=cmd || !id.equals(ItemFactory.getCustomId(item))) throw new AssertionError("slot "+slot+" expected "+id+"/"+cmd);
 }
}
