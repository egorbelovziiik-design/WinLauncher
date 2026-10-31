package net.kinggrief.probe;

import net.kinggrief.core.*;
import net.kinggrief.core.ItemFactory;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Real Paper/protocol player exercises all catalogue models and actual use packets. */
public final class WinterProbe extends JavaPlugin implements Listener {
 private KingGriefCore core;
 private Player player;
 private int errors, checks;
 private boolean running, cancelNextApple;
 private int acceptedApples;
 private Map<UUID,Long> runes, orbs;
 private Map<String,Long> appleCooldowns;
 private static final String[][] EFFECTS = {
   {"speed","jump_boost"}, {"haste","night_vision"}, {"resistance","regeneration"}, {"strength","resistance"},
   {"fire_resistance","speed"}, {"water_breathing","dolphins_grace"}, {"slow_falling","jump_boost"}, {"luck","night_vision"},
   {"absorption","resistance"}, {"strength","speed"}, {"speed","night_vision"}, {"fire_resistance","haste"},
   {"regeneration","fire_resistance"}, {"strength","night_vision"}, {"speed","slow_falling"}, {"jump_boost","night_vision"},
   {"water_breathing","speed"}, {"absorption","fire_resistance"}, {"luck","haste"}, {"regeneration","speed"}
 };
 private static final Set<String> SCALE = new HashSet<String>(Arrays.asList("speed","jump_boost","haste","resistance","regeneration","strength","luck","absorption"));
 private static final int[] SECONDS={15,25,35,45,60};
 @Override public void onEnable() { Bukkit.getPluginManager().registerEvents(this,this); }
 @SuppressWarnings("unchecked") private <T> T field(Object o,String name) throws Exception {
  Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return (T)f.get(o);
 }
 @EventHandler public void join(PlayerJoinEvent event){
  if(running)return; running=true;player=event.getPlayer();
  later(40,()->{try {
    core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");
    Object manager=core.getWinterItems();
    runes=field(manager,"runeReady");orbs=field(manager,"orbReady");
    appleCooldowns=field(field(core,"gameplayListener"),"itemCooldowns");
    begin();
  }catch(Throwable t){fail(t);}});
 }
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
 public void cancelApple(PlayerItemConsumeEvent e){
  if(cancelNextApple && "mega_gapple".equals(ItemFactory.getCustomId(e.getItem()))) { e.setCancelled(true);cancelNextApple=false; }
 }
 @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
 public void countApple(PlayerItemConsumeEvent e){ if("mega_gapple".equals(ItemFactory.getCustomId(e.getItem())))acceptedApples++; }
 private void later(long ticks,Runnable r){Bukkit.getScheduler().runTaskLater(this,()->{try{r.run();}catch(Throwable t){fail(t);}},ticks);}
 private void ok(boolean state,String message){checks++;if(!state){errors++;getLogger().severe("KG-WINTER FAIL: "+message);} }
 private void fail(Throwable t){errors++;getLogger().severe("KG-WINTER EXCEPTION: "+t);t.printStackTrace();finish();}
 private void finish(){ getLogger().info("KG-WINTER RESULT "+(errors==0?"PASS":"FAIL")+" checks="+checks+" errors="+errors); }
 private void clean(){
  player.getInventory().clear();player.getInventory().setItemInOffHand(null);
  for(PotionEffect e:player.getActivePotionEffects())player.removePotionEffect(e.getType());
  player.setGameMode(GameMode.SURVIVAL);player.setSneaking(false);player.setFoodLevel(20);
 }
 private void request(){getLogger().info("KG-WINTER REQUEST_USE");}
 private void requestOff(){getLogger().info("KG-WINTER REQUEST_OFF");}
 private PotionEffect effect(String key){return player.getPotionEffect(ColorUtil.potType(key,key.toUpperCase(Locale.ROOT)));}
 private void begin(){
  World spawn=Bukkit.getWorld("spawn_world");
  ok(spawn!=null && spawn.getBlockAt(2,135,0).getType()==Material.OCHRE_FROGLIGHT && spawn.getBlockAt(3,135,0).getType()==Material.WHITE_CONCRETE,
     "new tree star meets top person's raised hand");
  ok(spawn.getBlockAt(13,101,1).getType()==Material.DARK_OAK_PLANKS && spawn.getBlockAt(13,111,1).getType()==Material.DARK_OAK_PLANKS
       && spawn.getBlockAt(13,121,1).getType()==Material.DARK_OAK_PLANKS, "three stacked figures present at all three heights");
  World w=Bukkit.getWorlds().get(0); w.getBlockAt(5,69,5).setType(Material.STONE,false);
  player.teleport(new Location(w,5.5,70,5.5));clean();
  Set<String> ids=new HashSet<String>();Set<Integer> models=new HashSet<Integer>();
  ok(WinterItemsManager.itemCount()==150,"150 registered items, including 100 original runes");
  for(int n=1;n<=100;n++){
   ItemStack it=WinterItemsManager.makeRune(n,2);ItemMeta m=it.getItemMeta();
   ids.add(ItemFactory.getCustomId(it));models.add(m.getCustomModelData());
   ok(it.getType()==Material.AMETHYST_SHARD && ItemFactory.getCustomId(it).equals(WinterItemsManager.idFor(n)) && m.getCustomModelData()==3000+n,
      "registered item "+n+" has its exact material, id and model");
  }
  ok(ids.size()==100 && models.size()==100,"all 100 IDs and models are distinct");
  // Wait for the actual cross-world teleport acknowledgement before sending use packets.
  later(45,()->consumeRune(1));
 }
 private void consumeRune(final int n){
  if(n>100){cooldownTest();return;}
  clean();runes.clear();player.getInventory().setHeldItemSlot(0);player.getInventory().setItemInMainHand(WinterItemsManager.makeRune(n,2));
  request();later(6,()->{
    ItemStack remains=player.getInventory().getItemInMainHand();
    ok(remains.getAmount()==1 && ItemFactory.getCustomId(remains).equals(WinterItemsManager.idFor(n)),"packet consumed exactly one rune #"+n);
    int fam=(n-1)/5,tier=(n-1)%5;
    for(String key:EFFECTS[fam]){
     PotionEffect e=effect(key);int amp=SCALE.contains(key)&&tier>=3?1:0;
     ok(e!=null && e.getAmplifier()==amp && e.getDuration()>=SECONDS[tier]*20-15 && e.getDuration()<=SECONDS[tier]*20,
       "#"+n+" gives "+key+" amp="+amp+" duration="+SECONDS[tier]+"s, got="+e);
    }
    consumeRune(n+1);
  });
 }
 private void cooldownTest(){
   clean();player.getInventory().setItemInMainHand(WinterItemsManager.makeRune(1,2));request();
   later(5,()->{
    ok(player.getInventory().getItemInMainHand().getAmount()==2,"shared cooldown blocks another family without consumption");
    runes.clear();PotionEffectType speed=ColorUtil.potType("speed","SPEED");
    player.addPotionEffect(new PotionEffect(speed,800,3,false,false,true));request();
    later(5,()->{
      ok(effect("speed")!=null && effect("speed").getAmplifier()==3,"stronger existing effect preserved");
      safetyTest();
    });
   });
 }
 private void safetyTest(){
  clean();runes.clear();ItemStack item=WinterItemsManager.makeRune(1,2);player.getInventory().setItemInMainHand(item);
  PlayerInteractEvent cancelled=new PlayerInteractEvent(player,Action.RIGHT_CLICK_BLOCK,item,player.getWorld().getBlockAt(5,69,5),BlockFace.UP,EquipmentSlot.HAND);
  cancelled.setCancelled(true);Bukkit.getPluginManager().callEvent(cancelled);
  ok(cancelled.isCancelled() && player.getInventory().getItemInMainHand().getAmount()==2 && !runes.containsKey(player.getUniqueId()),"cancelled block interaction leaves rune and cooldown untouched");
  player.setGameMode(GameMode.SPECTATOR);request();later(5,()->{
   ok(player.getInventory().getItemInMainHand().getAmount()==2 && !runes.containsKey(player.getUniqueId()),"spectator cannot consume a rune");
   clean();shopTest();
  });
 }
 private void click(int raw){
   InventoryClickEvent e=new InventoryClickEvent(player.getOpenInventory(),InventoryType.SlotType.CONTAINER,raw,ClickType.LEFT,InventoryAction.PICKUP_ALL);
   Bukkit.getPluginManager().callEvent(e);ok(e.isCancelled(),"catalog click is cancelled against item theft");
 }
 private void shopTest(){
   core.getStore().setRelics(player.getName(),1000);core.getWinterItems().open(player,0);
   ItemStack icon=player.getOpenInventory().getTopInventory().getItem(10);
   ok("winter_rune_001".equals(ItemFactory.getCustomId(icon)),"catalog first page opens");
   int before=core.getStore().getRelics(player);click(10);
   ok(before-core.getStore().getRelics(player)==WinterItemsManager.price(1),"rune purchase charges the fixed server-side price");
   boolean found=false;for(ItemStack it:player.getInventory().getStorageContents())if("winter_rune_001".equals(ItemFactory.getCustomId(it)))found=true;
   ok(found,"purchased rune was delivered");
   core.getWinterItems().open(player,5);click(40); // page 6 index 27 is empty, may not charge
   ok(core.getStore().getRelics(player)==before-WinterItemsManager.price(1),"empty catalogue slot cannot charge");
   core.getWinterItems().open(player,0);
   if (Bukkit.getPluginManager().getPlugin("PlayerPoints") != null) {
    try { pointsFailureTests(); } catch (Exception e) { throw new RuntimeException(e); }
   }
   player.getInventory().clear();for(int i=0;i<36;i++)player.getInventory().setItem(i,new ItemStack(Material.STONE,64));
   int fullBefore=core.getStore().getRelics(player);click(10);
   ok(core.getStore().getRelics(player)==fullBefore,"full inventory cannot charge");
   player.closeInventory();clean();orbTest();
 }
 private void pointsFailureTests() throws Exception {
  Object points=Bukkit.getPluginManager().getPlugin("PlayerPoints");
  java.lang.reflect.Method mode=points.getClass().getMethod("mode",String.class);
  java.lang.reflect.Method balance=points.getClass().getMethod("balance",UUID.class);
  player.getInventory().clear();
  int before=core.getStore().getRelics(player);
  ok(((Number)balance.invoke(points,player.getUniqueId())).intValue()==before,"normal purchase debits PlayerPoints and the local balance equally");
  for(String failure:new String[]{"reject","alter-once","throw-once","read-fail"}){
   mode.invoke(points,failure);click(10);mode.invoke(points,"normal");
   ok(player.getInventory().firstEmpty()==0,"PlayerPoints "+failure+" does not issue a rune");
   ok(core.getStore().getRelics(player)==before && ((Number)balance.invoke(points,player.getUniqueId())).intValue()==before,
      "PlayerPoints "+failure+" retains or compensates the balance");
  }
  Path file=core.getDataFolder().toPath().resolve("data.yml");
  Path saved=file.resolveSibling("data-test-backup.yml");
  Files.move(file,saved);
  try{
   Files.createSymbolicLink(file,saved.getFileName());click(10);
   ok(player.getInventory().firstEmpty()==0,"YAML save refusal does not issue a rune");
   ok(core.getStore().getRelics(player)==before && ((Number)balance.invoke(points,player.getUniqueId())).intValue()==before,
      "YAML save refusal compensates PlayerPoints and keeps the local balance");
  }finally { Files.deleteIfExists(file);Files.move(saved,file); }
 }
 private ItemStack orb(int n) {
  switch(n){
   case 0:return ItemFactory.sharTitana();case 1:return ItemFactory.sharAndromedy();
   case 2:return ItemFactory.sharChimery();case 3:return ItemFactory.sharApollona();
   case 4:return ItemFactory.sharAida();case 5:return ItemFactory.sharOsirisa();
   case 6:return ItemFactory.sharAresa();case 7:return ItemFactory.sharSanty();
   case 8:return ItemFactory.sharKronosa();case 9:return ItemFactory.sharGeliosa();
   case 10:return ItemFactory.sharMorozko();default:return ItemFactory.sharKvadrat();
  }
 }
 private void orbTest(){ orbBurst(0); }
 private void orbBurst(final int n){
  if(n>=12){orbCooldownTest();return;}
  final String[] bursts={"resistance","speed","regeneration","haste","fire_resistance","regeneration", "strength","absorption","haste","fire_resistance","resistance","absorption"};
  clean();orbs.clear();ItemStack equipped=orb(n);String id=ItemFactory.getCustomId(equipped);
  player.getInventory().setItemInOffHand(equipped);player.setSneaking(true);
  requestOff();later(6,()->{
   PotionEffect active=effect(bursts[n]);int minimum=SCALE.contains(bursts[n])?1:0;
   ok(active!=null && active.getAmplifier()>=minimum && orbs.containsKey(player.getUniqueId()),
      "off-hand packet activates "+id+" burst "+bursts[n]+", got="+active);
   ok(id.equals(ItemFactory.getCustomId(player.getInventory().getItemInOffHand())),id+" burst leaves orb intact");
   orbBurst(n+1);
  });
 }
 private void orbCooldownTest(){
  Long first=orbs.get(player.getUniqueId());player.removePotionEffect(ColorUtil.potType("absorption","ABSORPTION"));
  requestOff();later(6,()->{
   ok(effect("absorption")==null && Objects.equals(first,orbs.get(player.getUniqueId())),"orb's shared cooldown blocks repeated use");
   player.getInventory().setItemInOffHand(ItemFactory.sharTitana());
   player.removePotionEffect(ColorUtil.potType("resistance","DAMAGE_RESISTANCE"));requestOff();later(6,()->{
    PotionEffect res=effect("resistance");
    ok((res==null || res.getAmplifier()<1) && Objects.equals(first,orbs.get(player.getUniqueId())),"switching orbs cannot bypass the shared cooldown");
    clean();appleTest();
   });
  });
 }
 private void appleTest(){
   player.getInventory().setItemInMainHand(ItemFactory.megaGapple(2));cancelNextApple=true;
   PlayerItemConsumeEvent cancelled=new PlayerItemConsumeEvent(player,ItemFactory.megaGapple(1),EquipmentSlot.HAND);
   Bukkit.getPluginManager().callEvent(cancelled);
   later(4,()->{
    ok(cancelled.isCancelled() && acceptedApples==0 && !appleCooldowns.containsKey(player.getUniqueId()+":cd_mega_gapple") && effect("absorption")==null,
       "cancelled consumption grants no bonus or cooldown");
    request();later(55,()->{
      ItemStack left=player.getInventory().getItemInMainHand();PotionEffect regen=effect("regeneration"), absorb=effect("absorption");
      ok(acceptedApples==1 && left.getAmount()==1 && "mega_gapple".equals(ItemFactory.getCustomId(left)),"real use packet eats exactly one mega apple");
      ok(regen!=null && regen.getAmplifier()==2 && absorb!=null && absorb.getAmplifier()==4,"mega apple adds Regen III and Absorption V after vanilla");
      ok(appleCooldowns.containsKey(player.getUniqueId()+":cd_charka") && appleCooldowns.containsKey(player.getUniqueId()+":cd_mega_gapple"),"accepted mega apple commits shared and independent cooldowns");
      appleCooldowns.remove(player.getUniqueId()+":cd_charka");player.setCooldown(Material.ENCHANTED_GOLDEN_APPLE,0);
      request();later(55,()->{
       ok(player.getInventory().getItemInMainHand().getAmount()==1 && acceptedApples==1,"mega-specific cooldown prevents eating again even after ordinary apple cooldown is cleared");
       finish();
      });
    });
   });
 }
}
