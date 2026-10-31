package net.kinggrief.probe;

import java.lang.reflect.Field;
import java.util.*;
import net.kinggrief.core.ColorUtil;
import net.kinggrief.core.ItemFactory;
import net.kinggrief.core.KingGriefCore;
import net.kinggrief.core.WinterItemsManager;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;

/** Exercises each of the 50 new artifacts via real use-item packets on Paper. */
public final class NewArtifactsProbe extends JavaPlugin implements Listener {
    private KingGriefCore core;private Player player;private Map<UUID,Long> cooldowns;private int checks,errors;private boolean started,finished,cancelHeal,cancelFood,cancelPotions;
    private static final String[][] EFFECTS={
        {"regeneration","absorption"},{"regeneration","luck"},{"regeneration","strength"},{"resistance","luck"},{"speed","absorption"},
        {"speed","luck"},{"jump_boost","luck"},{"jump_boost","regeneration"},{"jump_boost","absorption"},{"speed","resistance"},
        {"regeneration","water_breathing"},{"regeneration","dolphins_grace"},{"regeneration","slow_falling"},{"haste","regeneration"},{"night_vision","regeneration"},
        {"resistance","fire_resistance"},{"resistance","water_breathing"},{"resistance","dolphins_grace"},{"resistance","slow_falling"},{"night_vision","absorption"},
        {"fire_resistance","water_breathing"},{"fire_resistance","dolphins_grace"},{"fire_resistance","slow_falling"},{"fire_resistance","luck"},{"strength","fire_resistance"},
        {"water_breathing","slow_falling"},{"water_breathing","luck"},{"water_breathing","absorption"},{"night_vision","water_breathing"},{"haste","water_breathing"},
        {"speed","haste"},{"speed","dolphins_grace"},{"jump_boost","haste"},{"jump_boost","resistance"},{"jump_boost","fire_resistance"},
        {"jump_boost","strength"},{"haste","strength"},{"strength","water_breathing"},{"strength","dolphins_grace"},{"strength","slow_falling"},
        {"haste","resistance"},{"haste","dolphins_grace"},{"haste","slow_falling"},{"haste","absorption"},{"jump_boost","water_breathing"},
        {"night_vision","resistance"},{"night_vision","fire_resistance"},{"night_vision","dolphins_grace"},{"night_vision","slow_falling"},{"dolphins_grace","slow_falling"}
    };
    private static final Set<String> SCALE=new HashSet<>(Arrays.asList("speed","jump_boost","haste","resistance","regeneration","strength","luck","absorption"));
    private static final int[] SECONDS={15,25,35,45,60};
    @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);}
    @EventHandler public void join(PlayerJoinEvent e){if(started)return;started=true;player=e.getPlayer();later(40,()->{core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");Field f=WinterItemsManager.class.getDeclaredField("runeReady");f.setAccessible(true);cooldowns=(Map<UUID,Long>)f.get(core.getWinterItems());begin();});}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void heal(EntityRegainHealthEvent e){if(cancelHeal && e.getEntity().equals(player))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void food(FoodLevelChangeEvent e){if(cancelFood && e.getEntity().equals(player))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void potion(EntityPotionEffectEvent e){if(cancelPotions && e.getEntity().equals(player))e.setCancelled(true);}
    private interface Checked {void run() throws Exception;}
    private void later(long delay,Checked run){Bukkit.getScheduler().runTaskLater(this,()->{try{run.run();}catch(Throwable t){fail(t);}},delay);}
    private void ok(boolean state,String label){checks++;if(!state){errors++;getLogger().severe("KG-ART FAIL "+label);}}
    private void fail(Throwable e){errors++;getLogger().severe("KG-ART EXCEPTION "+e);e.printStackTrace();finish();}
    private void finish(){if(finished)return;finished=true;getLogger().info("KG-ART RESULT "+(errors==0?"PASS":"FAIL")+" checks="+checks+" errors="+errors);}
    private PotionEffectType type(String key){return ColorUtil.potType(key,key.toUpperCase(Locale.ROOT));}
    private void clean(){
        cancelPotions=false;cancelHeal=false;cancelFood=false;player.getInventory().clear();
        for(PotionEffect e:player.getActivePotionEffects())player.removePotionEffect(e.getType());
        player.setGameMode(GameMode.SURVIVAL);player.setHealth(Math.min(20,player.getMaxHealth()));player.setFoodLevel(20);
        player.setFireTicks(0);player.setRemainingAir(player.getMaximumAir());cooldowns.clear();
    }
    private void begin(){
        // The prepared spawn intentionally cancels hunger changes and keeps
        // food full; exercise recoverable food in the normal survival world.
        World world=Bukkit.getWorlds().get(0);
        player.teleport(world.getSpawnLocation().clone().add(0,2,0));
        Set<String> ids=new HashSet<>(),names=new HashSet<>(),combinations=new HashSet<>();Set<Integer> models=new HashSet<>();
        ok(WinterItemsManager.itemCount()==150,"catalog contains the original 100 and 50 new artifacts");
        for(int n=101;n<=150;n++){
            ItemStack item=WinterItemsManager.makeRune(n,2);ItemMeta meta=item.getItemMeta();
            ids.add(ItemFactory.getCustomId(item));models.add(meta.getCustomModelData());names.add(meta.getDisplayName());
            List<String> effectPair=Arrays.asList(EFFECTS[n-101]);List<String> sorted=new ArrayList<>(effectPair);Collections.sort(sorted);combinations.add(String.join("+",sorted));
            ok(item.getType()==Material.AMETHYST_SHARD && WinterItemsManager.idFor(n).equals(ItemFactory.getCustomId(item))
                    && meta.getCustomModelData()==3000+n,"registered unique item "+n);
        }
        ok(ids.size()==50 && models.size()==50 && names.size()==50 && combinations.size()==50,"50 unique IDs, names, models and effect pairs");
        later(20,()->usePacket(101));
    }
    private void usePacket(int n){
        if(n>150){utilityChecks();return;}
        clean();player.getInventory().setHeldItemSlot(0);player.getInventory().setItemInMainHand(WinterItemsManager.makeRune(n,2));
        getLogger().info("KG-ART REQUEST_USE");
        later(6,()->{
            ItemStack remain=player.getInventory().getItemInMainHand();int tier=(n-1)%5;
            ok(remain!=null && remain.getAmount()==1 && WinterItemsManager.idFor(n).equals(ItemFactory.getCustomId(remain)),"real packet consumes exactly one #"+n);
            for(String key:EFFECTS[n-101]){
                PotionEffect effect=player.getPotionEffect(type(key));int amp=SCALE.contains(key)&&tier>=3?1:0;
                ok(effect!=null && effect.getAmplifier()==amp && effect.getDuration()>=SECONDS[tier]*20-20 && effect.getDuration()<=SECONDS[tier]*20,
                        "real packet #"+n+" applies "+key+" with expected level/duration");
            }
            usePacket(n+1);
        });
    }
    private void syntheticUse(ItemStack item){
        player.getInventory().setItemInMainHand(item);
        Bukkit.getPluginManager().callEvent(new PlayerInteractEvent(player,Action.RIGHT_CLICK_AIR,item,null,BlockFace.SELF,EquipmentSlot.HAND));
    }
    private void utilityChecks(){
        String[] negative={"","","poison","wither","","","slowness","weakness","mining_fatigue","blindness"};
        for(int n=101;n<=150;n++){
            clean();int group=(n-101)/5,tier=(n-1)%5;
            if(group==0)player.setHealth(10);
            else if(group==1)player.setFoodLevel(5);
            else if(group==4)player.setFireTicks(100);
            else if(group==5)player.setRemainingAir(5);
            else player.addPotionEffect(new PotionEffect(type(negative[group]),500,0,false,false,true));
            syntheticUse(WinterItemsManager.makeRune(n,2));
            if(group==0)ok(Math.abs(player.getHealth()-(12+tier))<0.001,"#"+n+" heals by its advertised amount");
            else if(group==1)ok(player.getFoodLevel()==7+tier,"#"+n+" restores its advertised food amount");
            else if(group==4)ok(player.getFireTicks()==0,"#"+n+" extinguishes fire");
            else if(group==5)ok(player.getRemainingAir()==player.getMaximumAir(),"#"+n+" restores air");
            else ok(!player.hasPotionEffect(type(negative[group])),"#"+n+" removes "+negative[group]);
            ok(player.getInventory().getItemInMainHand().getAmount()==1,"utility consumes precisely one #"+n);
        }
        clean();player.setHealth(10);cancelHeal=true;syntheticUse(WinterItemsManager.makeRune(101,2));
        ok(player.getHealth()==10 && player.getInventory().getItemInMainHand().getAmount()==1,"cancelled healing is respected while accepted buffs still consume the item");
        clean();player.setFoodLevel(5);cancelFood=true;syntheticUse(WinterItemsManager.makeRune(106,2));
        ok(player.getFoodLevel()==5 && player.getInventory().getItemInMainHand().getAmount()==1,"cancelled food change is respected");
        clean();player.setHealth(10);cancelPotions=true;syntheticUse(WinterItemsManager.makeRune(101,2));cancelPotions=false;
        ok(player.getHealth()==10 && player.getInventory().getItemInMainHand().getAmount()==2 && !cooldowns.containsKey(player.getUniqueId()),"both cancelled potion effects preserve item, utility and cooldown");
        clean();ItemStack item=WinterItemsManager.makeRune(101,2);player.getInventory().setItemInMainHand(item);player.setHealth(10);
        PlayerInteractEvent blocked=new PlayerInteractEvent(player,Action.RIGHT_CLICK_BLOCK,item,player.getLocation().getBlock(),BlockFace.UP,EquipmentSlot.HAND);
        blocked.setCancelled(true);Bukkit.getPluginManager().callEvent(blocked);
        ok(player.getHealth()==10 && player.getInventory().getItemInMainHand().getAmount()==2 && !cooldowns.containsKey(player.getUniqueId()),"cancelled protected block interaction does nothing");
        clean();player.setGameMode(GameMode.SPECTATOR);syntheticUse(WinterItemsManager.makeRune(101,2));
        ok(player.getInventory().getItemInMainHand().getAmount()==2 && !cooldowns.containsKey(player.getUniqueId()),"spectator cannot use an artifact");
        clean();syntheticUse(WinterItemsManager.makeRune(101,2));player.getInventory().setItemInMainHand(WinterItemsManager.makeRune(150,2));
        syntheticUse(player.getInventory().getItemInMainHand());
        ok(player.getInventory().getItemInMainHand().getAmount()==2,"shared rune cooldown covers new artifacts");
        shopChecks();
    }
    private void click(int slot){InventoryClickEvent event=new InventoryClickEvent(player.getOpenInventory(),InventoryType.SlotType.CONTAINER,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL);Bukkit.getPluginManager().callEvent(event);ok(event.isCancelled(),"catalog click is protected");}
    private void shopChecks(){
        clean();core.getStore().setRelics(player.getName(),10000);core.getWinterItems().open(player,3);
        ItemStack icon=player.getOpenInventory().getTopInventory().getItem(30);
        ok("winter_rune_101".equals(ItemFactory.getCustomId(icon)),"first new artifact visible in catalogue");
        int before=core.getStore().getRelics(player);click(30);
        ok(core.getStore().getRelics(player)==before-WinterItemsManager.price(101),"new artifact purchase charges exactly its advertised price");
        ok(Arrays.stream(player.getInventory().getStorageContents()).anyMatch(i->"winter_rune_101".equals(ItemFactory.getCustomId(i))),"new artifact is delivered");
        core.getWinterItems().open(player,5);ok("winter_rune_150".equals(ItemFactory.getCustomId(player.getOpenInventory().getTopInventory().getItem(21))),"last artifact appears on page six");
        before=core.getStore().getRelics(player);click(40);ok(core.getStore().getRelics(player)==before,"empty page-six slot does not charge");
        player.closeInventory();player.getInventory().clear();for(int i=0;i<36;i++)player.getInventory().setItem(i,new ItemStack(Material.STONE,64));
        player.getInventory().setItem(0,WinterItemsManager.makeRune(101,63));core.getWinterItems().open(player,3);before=core.getStore().getRelics(player);click(30);
        ok(player.getInventory().getItem(0).getAmount()==64 && core.getStore().getRelics(player)==before-WinterItemsManager.price(101),"catalogue allows safe merging into an existing stack");
        player.closeInventory();player.getInventory().clear();
        boolean done=Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"artifacts give "+player.getName()+" 150 2");
        ok(done && Arrays.stream(player.getInventory().getStorageContents()).anyMatch(i->i!=null && "winter_rune_150".equals(ItemFactory.getCustomId(i)) && i.getAmount()==2),"console can give item 150");
        finish();
    }
}
