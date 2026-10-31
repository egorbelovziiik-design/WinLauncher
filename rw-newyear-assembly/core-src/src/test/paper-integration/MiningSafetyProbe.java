package net.kinggrief.probe;

import net.kinggrief.core.ItemFactory;
import net.kinggrief.core.KingGriefCore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Real Player.breakBlock calls with a later external protection listener. */
public final class MiningSafetyProbe extends JavaPlugin implements Listener {
    private Player p;private KingGriefCore core;private Block central,protectedNeighbour,protectedLiquid;
    private boolean cancelCentral,started,finished,forceExternalWear;private int neighbourEvents,liquidEvents,spawnerDamageEvents,spawnerDamageCancelled,checks,errors;
    @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);}
    @EventHandler public void join(PlayerJoinEvent e){if(!started && Bukkit.getOnlinePlayers().size()>=2){started=true;later(40,this::begin);}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void protect(BlockBreakEvent event){
        if(cancelCentral && event.getBlock().equals(central))event.setCancelled(true);
        if(protectedNeighbour!=null && event.getBlock().equals(protectedNeighbour)){neighbourEvents++;event.setCancelled(true);}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void protectLiquid(BlockPlaceEvent event){
        if(protectedLiquid!=null && event.getBlock().equals(protectedLiquid)){liquidEvents++;event.setCancelled(true);}
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void overrideToolDamage(PlayerItemDamageEvent event){
        if(forceExternalWear && "pick_spawner".equals(ItemFactory.getCustomId(event.getItem())))event.setCancelled(false);
    }
    @EventHandler(priority=EventPriority.MONITOR) public void observeToolDamage(PlayerItemDamageEvent event){
        if("pick_spawner".equals(ItemFactory.getCustomId(event.getItem()))){
            spawnerDamageEvents++;if(event.isCancelled())spawnerDamageCancelled++;
        }
    }
    private ItemStack wornSpawnerPick(int damage){
        ItemStack pick=ItemFactory.pickSpawner();pick.removeEnchantment(Enchantment.UNBREAKING);
        ItemMeta meta=pick.getItemMeta();((Damageable)meta).setDamage(damage);pick.setItemMeta(meta);return pick;
    }
    private void later(long ticks,Runnable run){Bukkit.getScheduler().runTaskLater(this,()->{try{run.run();}catch(Throwable e){fail(e);}},ticks);}
    private void ok(boolean value,String label){checks++;if(value)getLogger().info("KG-MINE PASS "+label);else{errors++;getLogger().severe("KG-MINE FAIL "+label);}}
    private void fail(Throwable error){errors++;getLogger().severe("KG-MINE EXCEPTION "+error);error.printStackTrace();finish();}
    private void finish(){if(finished)return;finished=true;getLogger().info("KG-MINE RESULT "+(errors==0?"PASS":"FAIL")+" checks="+checks+" errors="+errors);Bukkit.shutdown();}
    private void fill(){for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Material.DIRT,64));}
    private int drops(Location around,Material type){int n=0;for(Entity entity:around.getWorld().getNearbyEntities(around,5,5,5))if(entity instanceof Item && ((Item)entity).getItemStack().getType()==type)n+=((Item)entity).getItemStack().getAmount();return n;}
    private int inventory(Material type){int n=0;for(ItemStack item:p.getInventory().getStorageContents())if(item!=null && item.getType()==type)n+=item.getAmount();return n;}
    private boolean savedType(ItemStack item,EntityType type){
        return item!=null && "saved_spawner".equals(ItemFactory.getCustomId(item)) && item.hasItemMeta()
                && type.name().equals(item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey("kinggrief","spawner_mob"),PersistentDataType.STRING));
    }
    private boolean hasSavedType(Location around,EntityType type){
        for(ItemStack item:p.getInventory().getStorageContents())if(savedType(item,type))return true;
        for(Entity entity:around.getWorld().getNearbyEntities(around,5,5,5))if(entity instanceof Item && savedType(((Item)entity).getItemStack(),type))return true;
        return false;
    }
    private void begin(){
        core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");p=Bukkit.getPlayerExact("EcoBuyer");
        if(p==null)throw new AssertionError("player missing");World world=Bukkit.getWorlds().get(0);p.setGameMode(GameMode.SURVIVAL);
        p.teleport(new Location(world,10.5,161,10.5,0f,90f));fill();p.getInventory().setItemInMainHand(ItemFactory.pickKrush());
        central=world.getBlockAt(10,160,10);protectedNeighbour=world.getBlockAt(11,160,10);Block neighbour=world.getBlockAt(9,160,10);
        central.setType(Material.STONE,false);protectedNeighbour.setType(Material.STONE,false);neighbour.setType(Material.STONE,false);
        Block furnace=world.getBlockAt(10,160,11);furnace.setType(Material.FURNACE,false);
        ((org.bukkit.block.Furnace)furnace.getState()).getInventory().setItem(0,new ItemStack(Material.DIAMOND,3));
        double coins=core.getStore().getCoins(p.getName());int cobble=drops(central.getLocation(),Material.COBBLESTONE);
        cancelCentral=true;boolean allowed=p.breakBlock(central);
        later(4,()->{
            ok(!allowed && central.getType()==Material.STONE && neighbour.getType()==Material.STONE,"late central cancellation leaves all blocks intact");
            ok(core.getStore().getCoins(p.getName())==coins && drops(central.getLocation(),Material.COBBLESTONE)==cobble,"cancelled break grants no currency or custom drops");
            cancelCentral=false;boolean success=p.breakBlock(central);
            later(4,()->{
                ok(success && central.getType().isAir() && neighbour.getType().isAir(),"approved central and unprotected neighbouring blocks break");
                ok(neighbourEvents>0 && protectedNeighbour.getType()==Material.STONE,"external BlockBreakEvent protects neighbouring block");
                ItemStack furnaceItem=((org.bukkit.block.Furnace)furnace.getState()).getInventory().getItem(0);
                ok(furnace.getType()==Material.FURNACE && furnaceItem!=null && furnaceItem.getType()==Material.DIAMOND && furnaceItem.getAmount()==3,"drilling preserves tile entities and their inventories");
                ok(drops(p.getLocation(),Material.COBBLESTONE)>cobble,"full inventory drops telekinetic mining remainder instead of losing it");
                frost(world);
            });
        });
    }
    private void frost(World world){
        p.teleport(new Location(world,20.5,161,20.5,0f,90f));p.getInventory().setItemInMainHand(ItemFactory.pickFrost());
        central=world.getBlockAt(20,160,20);central.setType(Material.STONE,false);protectedNeighbour=null;
        protectedLiquid=world.getBlockAt(21,160,20);Block free=world.getBlockAt(19,160,20);
        protectedLiquid.setType(Material.WATER,false);free.setType(Material.WATER,false);
        p.breakBlock(central);later(4,()->{
            ok(liquidEvents>0 && protectedLiquid.getType()==Material.WATER,"external BlockPlaceEvent protects liquid from frost conversion");
            ok(free.getType()==Material.PACKED_ICE,"unprotected water is still converted by frost pick");
            protectedLiquid=null;spawner(world);
        });
    }
    private void spawner(World world){
        // Remove random Unbreaking and start with damage so a normal break deterministically changes metadata.
        p.teleport(new Location(world,30.5,161,30.5,0f,90f));p.getInventory().setItemInMainHand(wornSpawnerPick(3));
        central=world.getBlockAt(30,160,30);central.setType(Material.SPAWNER,false);
        CreatureSpawner state=(CreatureSpawner)central.getState();state.setSpawnedType(EntityType.ZOMBIE);state.update(true,false);
        int before=drops(central.getLocation(),Material.SPAWNER)+inventory(Material.SPAWNER), damageBefore=spawnerDamageEvents, cancelledBefore=spawnerDamageCancelled;
        cancelCentral=true;p.breakBlock(central);later(4,()->{
            ok(central.getType()==Material.SPAWNER && "pick_spawner".equals(ItemFactory.getCustomId(p.getInventory().getItemInMainHand())) && drops(central.getLocation(),Material.SPAWNER)+inventory(Material.SPAWNER)==before,"cancelled spawner break consumes no tool and gives no spawner");
            cancelCentral=false;p.breakBlock(central);later(4,()->{
                int worldDrops=drops(central.getLocation(),Material.SPAWNER), inInventory=inventory(Material.SPAWNER);
                String hand=ItemFactory.getCustomId(p.getInventory().getItemInMainHand());
                ok(central.getType().isAir() && !"pick_spawner".equals(hand) && worldDrops+inInventory==before+1,
                    "approved worn spawner pick consumes tool and gives one saved spawner (block="+central.getType()+", hand="+hand+", ground="+worldDrops+", inventory="+inInventory+")");
                ok(spawnerDamageEvents>damageBefore && spawnerDamageCancelled>cancelledBefore,"one-use spawner pick ignores vanilla wear during the transaction");
                ok(hasSavedType(central.getLocation(),EntityType.ZOMBIE),"mined spawner retains the configured mob type");
                almostBrokenSpawner(world);
            });
        });
    }
    private void almostBrokenSpawner(World world){
        p.teleport(new Location(world,42.5,161,30.5,0f,90f));p.getInventory().setItemInMainHand(wornSpawnerPick(Material.GOLDEN_PICKAXE.getMaxDurability()-1));
        central=world.getBlockAt(42,160,30);central.setType(Material.SPAWNER,false);
        int before=drops(central.getLocation(),Material.SPAWNER)+inventory(Material.SPAWNER);
        p.breakBlock(central);later(4,()->{
            ok(central.getType().isAir() && !"pick_spawner".equals(ItemFactory.getCustomId(p.getInventory().getItemInMainHand()))
                    && drops(central.getLocation(),Material.SPAWNER)+inventory(Material.SPAWNER)==before+1,
                    "almost-broken legacy pick still yields exactly one spawner");
            changedDurability(world);
        });
    }
    private void changedDurability(World world){
        p.teleport(new Location(world,54.5,161,30.5,0f,90f));p.getInventory().setItemInMainHand(wornSpawnerPick(3));
        central=world.getBlockAt(54,160,30);central.setType(Material.SPAWNER,false);
        int before=drops(central.getLocation(),Material.SPAWNER)+inventory(Material.SPAWNER), damageBefore=spawnerDamageEvents;
        forceExternalWear=true;p.breakBlock(central);forceExternalWear=false;later(4,()->{
            ok(spawnerDamageEvents>damageBefore && central.getType().isAir()
                    && !"pick_spawner".equals(ItemFactory.getCustomId(p.getInventory().getItemInMainHand()))
                    && drops(central.getLocation(),Material.SPAWNER)+inventory(Material.SPAWNER)==before+1,
                    "external wear changing durability still consumes the same pick exactly once");
            ordinaryToolWear(world);
        });
    }
    private void ordinaryToolWear(World world){
        p.teleport(new Location(world,66.5,161,30.5,0f,90f));p.getInventory().setItemInMainHand(wornSpawnerPick(3));
        central=world.getBlockAt(66,160,30);central.setType(Material.STONE,false);int beforeCancelled=spawnerDamageCancelled;
        p.breakBlock(central);later(4,()->{
            ItemStack held=p.getInventory().getItemInMainHand();
            ok(held.hasItemMeta() && ((Damageable)held.getItemMeta()).getDamage()>3 && spawnerDamageCancelled==beforeCancelled,
                    "ordinary stone mining still wears the pick after the guard is cleared");
            movedTool(world);
        });
    }
    private void movedTool(World world){
        p.teleport(new Location(world,78.5,161,30.5,0f,90f));p.getInventory().setItemInMainHand(wornSpawnerPick(3));p.getInventory().setItemInOffHand(null);
        central=world.getBlockAt(78,160,30);central.setType(Material.SPAWNER,false);
        int before=drops(central.getLocation(),Material.SPAWNER)+inventory(Material.SPAWNER);
        ItemStack pick=p.getInventory().getItemInMainHand().clone();p.breakBlock(central);
        p.getInventory().setItemInMainHand(null);p.getInventory().setItemInOffHand(pick);
        later(4,()->{
            ok(central.getType().isAir() && !"pick_spawner".equals(ItemFactory.getCustomId(p.getInventory().getItemInOffHand()))
                    && drops(central.getLocation(),Material.SPAWNER)+inventory(Material.SPAWNER)==before+1,
                    "pick moved to offhand is consumed once and spawner is delivered");
            missingTool(world);
        });
    }
    private void missingTool(World world){
        p.teleport(new Location(world,90.5,161,30.5,0f,90f));p.getInventory().setItemInMainHand(wornSpawnerPick(3));
        central=world.getBlockAt(90,160,30);central.setType(Material.SPAWNER,false);
        CreatureSpawner saved=(CreatureSpawner)central.getState();saved.setSpawnedType(EntityType.ZOMBIE);saved.setDelay(317);saved.update(true,false);
        int before=drops(central.getLocation(),Material.SPAWNER)+inventory(Material.SPAWNER);
        p.breakBlock(central);p.getInventory().setItemInMainHand(null);later(4,()->{
            CreatureSpawner restored=central.getType()==Material.SPAWNER?(CreatureSpawner)central.getState():null;
            ok(restored!=null && restored.getSpawnedType()==EntityType.ZOMBIE && restored.getDelay()>=300 && restored.getDelay()<=317
                    && drops(central.getLocation(),Material.SPAWNER)+inventory(Material.SPAWNER)==before,
                    "missing tool restores spawner with its mob and delay without paying a reward");
            autoMine();
        });
    }
    private void autoMine(){
        World spawn=Bukkit.getWorld("spawn_world");p.teleport(new Location(spawn,-85.5,97,4.5,0f,90f));p.getInventory().setItemInMainHand(ItemFactory.pickKrush());
        central=spawn.getBlockAt(-86,94,4);protectedNeighbour=spawn.getBlockAt(-85,94,4);Block free=spawn.getBlockAt(-87,94,4);
        central.setType(Material.IRON_ORE,false);protectedNeighbour.setType(Material.IRON_ORE,false);free.setType(Material.IRON_ORE,false);
        cancelCentral=true;double coins=core.getStore().getCoins(p.getName());p.breakBlock(central);later(4,()->{
            ok(central.getType()==Material.IRON_ORE && free.getType()==Material.IRON_ORE && core.getStore().getCoins(p.getName())==coins,"auto-mine honours late cancellation before rewards or drilling");
            cancelCentral=false;int events=neighbourEvents;p.breakBlock(central);later(4,()->{
                ok(central.getType().isAir() && free.getType().isAir(),"auto-mine still drills allowed ore");
                ok(neighbourEvents>events && protectedNeighbour.getType()==Material.IRON_ORE,"auto-mine fires external protection event for neighbours");
                disconnectedTool();
            });
        });
    }
    private void disconnectedTool(){
        World world=Bukkit.getWorlds().get(0);p.teleport(new Location(world,102.5,161,30.5,0f,90f));
        p.getInventory().setItemInMainHand(wornSpawnerPick(3));central=world.getBlockAt(102,160,30);central.setType(Material.SPAWNER,false);
        CreatureSpawner saved=(CreatureSpawner)central.getState();saved.setSpawnedType(EntityType.SKELETON);saved.update(true,false);
        int before=drops(central.getLocation(),Material.SPAWNER)+inventory(Material.SPAWNER);
        p.breakBlock(central);p.kickPlayer("mining disconnect test");later(8,()->{
            CreatureSpawner restored=central.getType()==Material.SPAWNER?(CreatureSpawner)central.getState():null;
            ok(!p.isOnline() && restored!=null && restored.getSpawnedType()==EntityType.SKELETON
                    && drops(central.getLocation(),Material.SPAWNER)+inventory(Material.SPAWNER)==before
                    && "pick_spawner".equals(ItemFactory.getCustomId(p.getInventory().getItemInMainHand())),
                    "disconnect before confirmation restores spawner and preserves the pick");
            shutdownDuringBreak(world);
        });
    }
    private void shutdownDuringBreak(World world){
        p=Bukkit.getPlayerExact("EcoSeller");if(p==null)throw new AssertionError("second player missing");
        p.setGameMode(GameMode.SURVIVAL);p.teleport(new Location(world,114.5,161,30.5,0f,90f));
        p.getInventory().setItemInMainHand(wornSpawnerPick(3));central=world.getBlockAt(114,160,30);central.setType(Material.SPAWNER,false);
        CreatureSpawner saved=(CreatureSpawner)central.getState();saved.setSpawnedType(EntityType.SPIDER);saved.update(true,false);
        int before=drops(central.getLocation(),Material.SPAWNER)+inventory(Material.SPAWNER);
        p.breakBlock(central);Bukkit.getPluginManager().disablePlugin(core);later(4,()->{
            CreatureSpawner restored=central.getType()==Material.SPAWNER?(CreatureSpawner)central.getState():null;
            ok(!core.isEnabled() && restored!=null && restored.getSpawnedType()==EntityType.SPIDER
                    && drops(central.getLocation(),Material.SPAWNER)+inventory(Material.SPAWNER)==before
                    && "pick_spawner".equals(ItemFactory.getCustomId(p.getInventory().getItemInMainHand())),
                    "plugin shutdown before confirmation restores spawner and preserves the pick");
            finish();
        });
    }
}
