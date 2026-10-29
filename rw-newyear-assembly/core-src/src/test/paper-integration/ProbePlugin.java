package net.kinggrief.probe;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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
    ResourcePackSettings.Pack rp=ResourcePackSettings.resolve(core.getConfig().getString("resourcepack-url"),core.getConfig().getString("resourcepack-sha1"));
    if(!rp.upgraded || !rp.url.endsWith("v65.zip")) throw new AssertionError("old config not upgraded");
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
    getLogger().info("KG-PROBE PASS: PAPER 1.21.4 old item_model migration join/ender/container/pickup, metadata, /mega, buff, old pack config");
   } catch(Throwable t) {getLogger().severe("KG-PROBE FAIL: "+t); t.printStackTrace();}
  }},50L);
 }
 private static void check(Inventory inv,int slot,Material type,int cmd,String id){
  ItemStack item=inv.getItem(slot); ItemMeta meta=item.getItemMeta();
  if(item.getType()!=type || !meta.hasCustomModelData() || meta.getCustomModelData()!=cmd || !id.equals(ItemFactory.getCustomId(item))) throw new AssertionError("slot "+slot+" expected "+id+"/"+cmd);
 }
}
