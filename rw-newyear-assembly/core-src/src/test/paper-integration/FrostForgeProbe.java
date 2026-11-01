package net.kinggrief.probe;

import net.kinggrief.core.ColorUtil;
import net.kinggrief.core.DataStore;
import net.kinggrief.core.ItemFactory;
import net.kinggrief.core.KingGriefCore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Craft each piece through real protocol clicks, then hit a real protocol player. */
public final class FrostForgeProbe extends JavaPlugin implements Listener {
    private KingGriefCore core;private Player a,b;private World world;private CraftingInventory work;
    private ItemStack blade,guard,heart,sword;private boolean running,done,cancelDamage,lateDamage,cancelPotion;
    private int checks,craftEvents;private boolean lastCraftCancelled;
    private final PotionEffectType slow=ColorUtil.potType("slowness","SLOW");
    @Override public void onEnable(){saveDefaultConfig();core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");Bukkit.getPluginManager().registerEvents(this,this);}
    @EventHandler public void join(PlayerJoinEvent event){
        if("ForgeSmith".equals(event.getPlayer().getName()))a=event.getPlayer();if("ForgeTarget".equals(event.getPlayer().getName()))b=event.getPlayer();
        if(a!=null&&b!=null&&!running){running=true;later(30,this::begin);}
    }
    private void later(long ticks,Runnable action){Bukkit.getScheduler().runTaskLater(this,()->{try{action.run();}catch(Throwable error){fail(error);}},ticks);}
    private void ok(boolean value,String text){if(!value)throw new AssertionError(text);checks++;getLogger().info("KG-FORGE PASS "+text);}
    private void fail(Throwable error){if(done)return;done=true;getLogger().severe("KG-FORGE RESULT FAIL "+error);error.printStackTrace();}
    private void finish(){done=true;getLogger().info("KG-FORGE RESULT PASS "+checks+" checks");}
    private void request(String command){getLogger().info("KG-FORGE REQUEST "+command);}
    private NamespacedKey key(String name){return new NamespacedKey(core,"frost_forge_"+name);}
    private void begin(){
        world=Bukkit.getWorld("world");world.getChunkAtAsync(16,16,true).whenComplete((chunk,error)->Bukkit.getScheduler().runTask(this,()->{
            try{
                if(error!=null||chunk==null)throw new AssertionError("async chunk load",error);
                world.addPluginChunkTicket(16,16,this);
                world.getBlockAt(256,80,256).setType(Material.STONE,false);world.getBlockAt(258,80,256).setType(Material.STONE,false);
                a.teleport(new Location(world,256.5,81,256.5,-90,0));b.teleport(new Location(world,258.5,81,256.5,90,0));
                a.setGameMode(GameMode.SURVIVAL);b.setGameMode(GameMode.SURVIVAL);
                a.setAllowFlight(true);a.setFlying(true);b.setAllowFlight(true);b.setFlying(true);
                for(String name:new String[]{"blade","guard","core","sword"}){
                    ok(Bukkit.getRecipe(key(name))!=null,"recipe "+name+" is registered");
                    ok(a.hasDiscoveredRecipe(key(name)),"recipe "+name+" is discovered by a real player");
                }
                if(getConfig().getInt("phase",1)==1)menu();else later(30,this::restartChecks);
            }catch(Throwable failure){fail(failure);}
        }));
    }
    private void menu(){
        a.performCommand("frostforge");Inventory menu=a.getOpenInventory().getTopInventory();
        ok(menu.getSize()==27&&menu.getItem(4).getType()==Material.CRAFTING_TABLE,"forge information menu opens");
        InventoryClickEvent take=new InventoryClickEvent(a.getOpenInventory(),InventoryType.SlotType.CONTAINER,10,ClickType.NUMBER_KEY,InventoryAction.HOTBAR_SWAP,0);
        Bukkit.getPluginManager().callEvent(take);ok(take.isCancelled(),"forge menu icons cannot be taken");
        Map<Integer,ItemStack> slots=new HashMap<Integer,ItemStack>();slots.put(10,new ItemStack(Material.DIAMOND));
        InventoryDragEvent drag=new InventoryDragEvent(a.getOpenInventory(),new ItemStack(Material.DIAMOND),new ItemStack(Material.DIAMOND),false,slots);
        Bukkit.getPluginManager().callEvent(drag);ok(drag.isCancelled(),"items cannot be dragged into the information menu");
        core.getGuis().openSwordsMenu(a);Inventory swords=a.getOpenInventory().getTopInventory();
        ok("sword_sever".equals(ItemFactory.getCustomId(swords.getItem(31)))&&"trident_poseidon".equals(ItemFactory.getCustomId(swords.getItem(33))),
                "northern sword and trident are both visible in the arsenal");
        ok(swords.getItem(40).getType()==Material.CRAFTING_TABLE,"arsenal links to the forge");
        InventoryClickEvent open=new InventoryClickEvent(a.getOpenInventory(),InventoryType.SlotType.CONTAINER,40,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        Bukkit.getPluginManager().callEvent(open);
        later(2,()->{
            ok(open.isCancelled()&&a.getOpenInventory().getTopInventory().getSize()==27,"arsenal button opens the forge safely on the next tick");
            a.closeInventory();a.getInventory().clear();a.openWorkbench(a.getLocation(),true);
            work=(CraftingInventory)a.getOpenInventory().getTopInventory();invalidInput();
        });
    }
    private static ItemStack item(Material material){return new ItemStack(material,1);}
    private void grid(ItemStack... items){
        ItemStack[] matrix=new ItemStack[9];System.arraycopy(items,0,matrix,0,items.length);work.setMatrix(matrix);a.updateInventory();
    }
    private ItemStack named(Material material){ItemStack item=item(material);ItemMeta meta=item.getItemMeta();meta.setDisplayName("Important custom material");item.setItemMeta(meta);return item;}
    private boolean empty(ItemStack item){return item==null||item.getType().isAir()||item.getAmount()<=0;}
    private int occupied(){int count=0;for(ItemStack item:work.getMatrix())if(!empty(item))count++;return count;}
    private void click(String kind,Runnable check){later(5,()->{request(kind);later(10,check);});}
    @EventHandler(priority=EventPriority.MONITOR)
    public void observeCraft(CraftItemEvent event){if(event.getWhoClicked().equals(a)){craftEvents++;lastCraftCancelled=event.isCancelled();}}
    private void invalidInput(){
        grid(item(Material.NETHERITE_INGOT),named(Material.DIAMOND),item(Material.BLUE_ICE));
        later(3,()->{
            ok(empty(work.getResult())&&occupied()==3,"renamed ingredient cannot be consumed by the recipe");
            Recipe recipe=Bukkit.getRecipe(key("blade"));work.setResult(recipe.getResult().clone());a.updateInventory();int before=craftEvents;
            click("CLICK",()->{
                ok(craftEvents>before&&lastCraftCancelled&&occupied()==3&&empty(a.getItemOnCursor()),
                        "forged result cannot bypass the final crafting validation");
                authenticatedCraft();
            });
        });
    }
    private void authenticatedCraft(){
        grid(item(Material.NETHERITE_INGOT),item(Material.DIAMOND),item(Material.BLUE_ICE));
        later(3,()->{
            ok("frost_forge_blade".equals(ItemFactory.getCustomId(work.getResult())),"plain ingredients preview the blade part");
            core.getConfig().set("enable-builtin-auth",true);core.getStore().setAuthenticated(a,false);
            click("CLICK",()->{
                ok(occupied()==3&&empty(a.getItemOnCursor()),"unauthenticated player cannot consume crafting inputs");
                core.getConfig().set("enable-builtin-auth",false);bladeCraft();
            });
        });
    }
    private void bladeCraft(){
        grid(item(Material.NETHERITE_INGOT),item(Material.DIAMOND),item(Material.BLUE_ICE));
        click("CLICK",()->{
            blade=a.getItemOnCursor().clone();ok("frost_forge_blade".equals(ItemFactory.getCustomId(blade))&&occupied()==0,"real click crafts and consumes the blade ingredients");
            a.setItemOnCursor(null);guardCraft();
        });
    }
    private void guardCraft(){
        grid(item(Material.GOLD_INGOT),item(Material.IRON_INGOT),item(Material.AMETHYST_SHARD));
        click("CLICK",()->{
            guard=a.getItemOnCursor().clone();ok("frost_forge_guard".equals(ItemFactory.getCustomId(guard))&&occupied()==0,"real click crafts the guard");
            a.setItemOnCursor(null);heartCraft();
        });
    }
    private void heartCraft(){
        grid(item(Material.HEART_OF_THE_SEA),item(Material.PRISMARINE_CRYSTALS),item(Material.PACKED_ICE));
        click("CLICK",()->{
            heart=a.getItemOnCursor().clone();ok("frost_forge_core".equals(ItemFactory.getCustomId(heart))&&occupied()==0,"real click crafts the core");
            a.setItemOnCursor(null);finalCraft();
        });
    }
    private void finalCraft(){
        ItemStack changed=blade.clone();ItemMeta meta=changed.getItemMeta();meta.setDisplayName("altered");changed.setItemMeta(meta);
        grid(changed,guard,heart);
        later(3,()->{
            ok(empty(work.getResult()),"a modified fragment cannot substitute for an exact component");
            grid(blade,guard,heart);
            later(3,()->{
                ok("assembled_frost_sword".equals(ItemFactory.getCustomId(work.getResult())),"three real fragments preview the assembled sword");
                click("CLICK",()->{
                    sword=a.getItemOnCursor().clone();ok("assembled_frost_sword".equals(ItemFactory.getCustomId(sword))&&occupied()==0,
                            "real click assembles the sword and consumes exactly three fragments");
                    ok(sword.getType()==Material.NETHERITE_SWORD&&sword.getEnchantments().size()>=2&&sword.getItemMeta().hasCustomModelData(),
                            "assembled weapon carries enchantments and the existing frost model identifier");
                    a.setItemOnCursor(null);fullInventory();
                });
            });
        });
    }
    private void fullInventory(){
        for(int i=0;i<36;i++)a.getInventory().setItem(i,new ItemStack(Material.COBBLESTONE,64));
        grid(item(Material.GOLD_INGOT),item(Material.IRON_INGOT),item(Material.AMETHYST_SHARD));
        click("SHIFT",()->{
            ok(occupied()==3&&empty(a.getItemOnCursor()),"shift-craft with a full inventory does not consume ingredients");
            work.setMatrix(new ItemStack[9]);a.closeInventory();a.getInventory().clear();a.getInventory().setHeldItemSlot(0);a.getInventory().setItemInMainHand(sword);
            finish();
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void damageProtection(EntityDamageByEntityEvent event){if(cancelDamage&&event.getDamager().equals(a)&&event.getEntity().equals(b))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR)
    public void lateDamageProtection(EntityDamageByEntityEvent event){if(lateDamage&&event.getDamager().equals(a)&&event.getEntity().equals(b))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void potionProtection(EntityPotionEffectEvent event){
        if(cancelPotion&&event.getEntity().equals(b)&&event.getNewEffect()!=null&&event.getNewEffect().getType().equals(slow))event.setCancelled(true);
    }
    private int cooldownSize(){try{
        Field field=KingGriefCore.class.getDeclaredField("frostForge");field.setAccessible(true);Object manager=field.get(core);
        Field cooldowns=manager.getClass().getDeclaredField("cooldowns");cooldowns.setAccessible(true);return ((Map<?,?>)cooldowns.get(manager)).size();
    }catch(Exception error){throw new RuntimeException(error);}}
    private void attack(Runnable check){b.setNoDamageTicks(0);request("ATTACK "+b.getEntityId());later(10,check);}
    private void restartChecks(){
        sword=a.getInventory().getItemInMainHand();
        ok("assembled_frost_sword".equals(ItemFactory.getCustomId(sword)),"assembled sword survives restart in the player's inventory");
        b.setMaxHealth(200);b.setHealth(200);b.setNoDamageTicks(0);b.setMaximumNoDamageTicks(0);b.setInvulnerable(false);b.removePotionEffect(slow);
        double hp=b.getHealth();cancelDamage=true;
        attack(()->{
            cancelDamage=false;ok(b.getHealth()==hp&&!b.hasPotionEffect(slow)&&cooldownSize()==0,"a cancelled real hit applies no damage, effect or cooldown");
            lateDamage=true;attack(()->{
                lateDamage=false;ok(b.getHealth()==hp&&!b.hasPotionEffect(slow)&&cooldownSize()==0,"late cancellation is rechecked before the effect");
                potionCase();
            });
        });
    }
    private void potionCase(){
        double hp=b.getHealth();cancelPotion=true;
        attack(()->{
            cancelPotion=false;ok(b.getHealth()<hp&&!b.hasPotionEffect(slow)&&cooldownSize()==0,"cancelled potion application does not consume the ability cooldown");
            b.addPotionEffect(new PotionEffect(slow,200,2,false,true,true));attack(()->{
                PotionEffect stronger=b.getPotionEffect(slow);
                ok(stronger!=null&&stronger.getAmplifier()==2&&cooldownSize()==0,"stronger existing slowness is not overwritten");
                b.removePotionEffect(slow);acceptedHit();
            });
        });
    }
    private void acceptedHit(){
        double hp=b.getHealth();attack(()->{
            PotionEffect effect=b.getPotionEffect(slow);
            ok(b.getHealth()<hp&&effect!=null&&effect.getAmplifier()==0&&effect.getDuration()>0&&cooldownSize()==1,
                    "real allowed sword hit applies three-second slowness and starts cooldown");
            b.removePotionEffect(slow);attack(()->{
                ok(!b.hasPotionEffect(slow)&&cooldownSize()==1,"another hit during cooldown does not reapply slowness");
                finish();
            });
        });
    }
}
