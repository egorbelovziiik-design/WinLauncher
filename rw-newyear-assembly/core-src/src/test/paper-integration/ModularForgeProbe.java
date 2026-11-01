package net.kinggrief.probe;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
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
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.ClickType;
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
import java.util.Map;
import java.util.UUID;

/** Two protocol clients, a real previous Core, real workbench clicks, combat and WG. */
public final class ModularForgeProbe extends JavaPlugin implements Listener {
    private KingGriefCore core;
    private Player a,b;
    private World world;
    private CraftingInventory work;
    private ItemStack blade,guard,legacyHeart,frost,aurora,tempest,tempestHeart;
    private boolean started,done,cancelDamage,lateDamage,cancelSpeed,addTeamOnHit,moveOnHit,lastHitCancelled,nestDuringSpeed,nestedIssued;
    private int checks,craftEvents,hits;
    private boolean lastCraftCancelled,lastTeleport;
    private Location denyLocation;
    private RegionManager regionManager;
    private ProtectedCuboidRegion denied;
    private final PotionEffectType slow=ColorUtil.potType("slowness","SLOW"),speed=ColorUtil.potType("speed","SPEED"),weak=ColorUtil.potType("weakness","WEAKNESS");

    @Override public void onEnable(){
        saveDefaultConfig();core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");
        Bukkit.getPluginManager().registerEvents(this,this);
    }
    @EventHandler public void join(PlayerJoinEvent e){
        if("ForgeSmith".equals(e.getPlayer().getName()))a=e.getPlayer();if("ForgeTarget".equals(e.getPlayer().getName()))b=e.getPlayer();
        if(a!=null&&b!=null&&!started){started=true;later(35,this::begin);}
    }
    private void later(long ticks,Runnable action){Bukkit.getScheduler().runTaskLater(this,()->{try{action.run();}catch(Throwable error){fail(error);}},ticks);}
    private void ok(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;getLogger().info("KG-MODFORGE PASS "+message);}
    private void fail(Throwable error){if(done)return;done=true;getLogger().severe("KG-MODFORGE RESULT FAIL "+error);error.printStackTrace();}
    private void finish(){done=true;getLogger().info("KG-MODFORGE RESULT PASS "+checks+" checks");}
    private void request(String command){getLogger().info("KG-MODFORGE REQUEST "+command);}
    private NamespacedKey key(String name){return new NamespacedKey(core,"frost_forge_"+name);}
    private String id(ItemStack item){return ItemFactory.getCustomId(item);}
    private static boolean empty(ItemStack item){return item==null||item.getType().isAir()||item.getAmount()<=0;}
    private static ItemStack item(Material material,int amount){return new ItemStack(material,amount);}
    private ItemStack named(Material material){ItemStack value=item(material,1);ItemMeta meta=value.getItemMeta();meta.setDisplayName("Do not consume this named ingredient");value.setItemMeta(meta);return value;}
    private Object field(Object target,String name){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new RuntimeException(e);}}
    @SuppressWarnings("unchecked") private Map<UUID,Long> cooldowns(){return (Map<UUID,Long>)field(field(core,"frostForge"),"cooldowns");}
    private Location marker(){return new Location(world,268,80,256);}
    private void untouched(){
        DataStore.Claim claim=core.getStore().getClaimAt(marker());World spawn=Bukkit.getWorld("spawn_world");
        ok(marker().getBlock().getType()==Material.DIAMOND_BLOCK&&claim!=null&&claim.owner.equalsIgnoreCase(a.getName())
                &&spawn!=null&&spawn.getBlockAt(0,130,0).getType()==Material.SPRUCE_WOOD,"spawn trunk, foreign-world block and unrelated claim remain unchanged");
        ItemStack diamond=a.getInventory().getItem(8);ok(diamond!=null&&diamond.getType()==Material.DIAMOND&&diamond.getAmount()==13,"unrelated inventory stack survives crafting and restart");
    }
    private void begin(){
        world=Bukkit.getWorld("world");world.getChunkAtAsync(16,16,true).whenComplete((chunk,error)->Bukkit.getScheduler().runTask(this,()->{
            try{
                if(error!=null||chunk==null)throw new AssertionError("chunk loading failed",error);
                world.addPluginChunkTicket(16,16,this);world.setPVP(true);world.setGameRule(org.bukkit.GameRule.NATURAL_REGENERATION,false);
                world.getBlockAt(256,80,256).setType(Material.STONE,false);world.getBlockAt(258,80,256).setType(Material.STONE,false);
                for(Player p:new Player[]{a,b}){p.setGameMode(GameMode.SURVIVAL);p.setAllowFlight(true);p.setFlying(true);}
                a.teleport(new Location(world,256.5,81,256.5,-90,0));b.teleport(new Location(world,258.5,81,256.5,90,0));
                int phase=getConfig().getInt("phase",1);
                if(phase==1)oldCore();else if(phase==2)later(20,this::newCore);else later(40,this::combat);
            }catch(Throwable failure){fail(failure);}
        }));
    }
    private void workbench(){a.closeInventory();a.openWorkbench(a.getLocation(),true);work=(CraftingInventory)a.getOpenInventory().getTopInventory();}
    private void grid(ItemStack... values){ItemStack[] matrix=new ItemStack[9];System.arraycopy(values,0,matrix,0,values.length);work.setMatrix(matrix);a.updateInventory();}
    private int occupied(){int count=0;for(ItemStack x:work.getMatrix())if(!empty(x))count++;return count;}
    private void click(String kind,Runnable after){later(5,()->{request(kind);later(10,after);});}
    @EventHandler(priority=EventPriority.MONITOR) public void craft(CraftItemEvent event){if(event.getWhoClicked().equals(a)){craftEvents++;lastCraftCancelled=event.isCancelled();}}
    private ItemStack cursor(String expected){ItemStack cursor=a.getItemOnCursor();ok(expected.equals(id(cursor)),"real workbench click crafts "+expected);ItemStack copy=cursor.clone();a.setItemOnCursor(null);return copy;}
    private int count(String wanted){int n=0;for(ItemStack value:a.getInventory().getStorageContents())if(wanted.equals(id(value)))n+=value.getAmount();return n;}
    private ItemStack take(String wanted){
        for(int i=0;i<36;i++){ItemStack value=a.getInventory().getItem(i);if(wanted.equals(id(value))){ItemStack one=value.clone();one.setAmount(1);
            if(value.getAmount()==1)a.getInventory().setItem(i,null);else{value=value.clone();value.setAmount(value.getAmount()-1);a.getInventory().setItem(i,value);}return one;}}
        throw new AssertionError("missing ingredient "+wanted);
    }
    private void store(ItemStack value){ok(a.getInventory().addItem(value).isEmpty(),"crafted weapon fits without deleting other inventory items");}

    private void oldCore(){
        a.getInventory().clear();a.getInventory().setItem(8,item(Material.DIAMOND,13));
        marker().getBlock().setType(Material.DIAMOND_BLOCK,false);core.getStore().createClaim(a.getName(),marker(),3);
        for(String name:new String[]{"blade","guard","core","sword"})ok(Bukkit.getRecipe(key(name))!=null,"previous Core has original "+name+" recipe");
        workbench();grid(item(Material.NETHERITE_INGOT,1),item(Material.DIAMOND,1),item(Material.BLUE_ICE,1));
        click("CLICK",()->{blade=cursor("frost_forge_blade");ok(occupied()==0,"old Core consumes the blade inputs once");
            grid(item(Material.GOLD_INGOT,1),item(Material.IRON_INGOT,1),item(Material.AMETHYST_SHARD,1));
            click("CLICK",()->{guard=cursor("frost_forge_guard");grid(item(Material.HEART_OF_THE_SEA,1),item(Material.PRISMARINE_CRYSTALS,1),item(Material.PACKED_ICE,1));
                click("CLICK",()->{legacyHeart=cursor("frost_forge_core");a.getInventory().setItem(3,blade);a.getInventory().setItem(4,guard);a.getInventory().setItem(5,legacyHeart);
                    work.setMatrix(new ItemStack[9]);a.closeInventory();untouched();finish();});
            });
        });
    }
    private void newCore(){
        for(String name:new String[]{"blade","guard","core","sword","aurora_core","tempest_core","aurora_sword","tempest_sword"}){
            ok(Bukkit.getRecipe(key(name))!=null,"new Core registers "+name+" recipe");ok(a.hasDiscoveredRecipe(key(name)),"recipe "+name+" is discoverable after upgrade");
        }
        blade=a.getInventory().getItem(3);guard=a.getInventory().getItem(4);legacyHeart=a.getInventory().getItem(5);
        ok(blade!=null&&blade.isSimilar(Bukkit.getRecipe(key("blade")).getResult())&&guard.isSimilar(Bukkit.getRecipe(key("guard")).getResult())
                &&legacyHeart.isSimilar(Bukkit.getRecipe(key("core")).getResult()),"all three components crafted by previous Core remain exact-match compatible");
        untouched();
        a.setItemOnCursor(item(Material.EMERALD,3));a.performCommand("frostforge");
        Object holder=a.getOpenInventory().getTopInventory().getHolder();
        ok(a.getItemOnCursor().getType()==Material.EMERALD&&a.getItemOnCursor().getAmount()==3
                &&(holder==null||!holder.getClass().getName().contains("ForgeHolder")),"forge refuses to replace a menu with an occupied cursor");
        a.setItemOnCursor(null);a.performCommand("frostforge");Inventory menu=a.getOpenInventory().getTopInventory();
        ok(menu.getSize()==27&&menu.getItem(20).getType()==Material.PRISMARINE_CRYSTALS&&menu.getItem(23).getType()==Material.ECHO_SHARD
                &&menu.getItem(21).getType()==Material.NETHERITE_SWORD&&menu.getItem(24).getType()==Material.NETHERITE_SWORD,"information menu shows both new cores and both variants");
        InventoryClickEvent steal=new InventoryClickEvent(a.getOpenInventory(),InventoryType.SlotType.CONTAINER,21,ClickType.NUMBER_KEY,InventoryAction.HOTBAR_SWAP,0);
        Bukkit.getPluginManager().callEvent(steal);ok(steal.isCancelled(),"new recipe icons cannot be taken via hotbar swap");
        a.getInventory().setItem(3,null);a.getInventory().setItem(4,null);a.getInventory().setItem(5,null);
        workbench();grid(blade,guard,legacyHeart);
        later(3,()->{ok("assembled_frost_sword".equals(id(work.getResult())),"old components preview the original sword after the JAR upgrade");
            click("CLICK",()->{frost=cursor("assembled_frost_sword");ok(occupied()==0,"assembling old fragments consumes exactly three items");store(frost.clone());auroraCore();});});
    }
    private void auroraCore(){
        grid(named(Material.HEART_OF_THE_SEA),item(Material.AMETHYST_SHARD,1),item(Material.GLOWSTONE_DUST,1));
        later(3,()->{ok(empty(work.getResult())&&occupied()==3,"renamed ingredients cannot enter the new heart recipe");
            Recipe recipe=Bukkit.getRecipe(key("aurora_core"));work.setResult(recipe.getResult().clone());a.updateInventory();int before=craftEvents;
            click("CLICK",()->{ok(craftEvents>before&&lastCraftCancelled&&occupied()==3&&empty(a.getItemOnCursor()),"forged preview cannot consume renamed inputs");
                grid(item(Material.HEART_OF_THE_SEA,2),item(Material.AMETHYST_SHARD,2),item(Material.GLOWSTONE_DUST,2));
                click("SHIFT",()->{ok(occupied()==0&&count("frost_forge_aurora_core")==2,"real shift click consumes exactly two sets for two aurora hearts");tempestCore();});
            });
        });
    }
    private void tempestCore(){
        grid(item(Material.HEART_OF_THE_SEA,1),item(Material.ECHO_SHARD,1),item(Material.BLAZE_POWDER,1));
        click("CLICK",()->{tempestHeart=cursor("frost_forge_tempest_core");ok(occupied()==0,"tempest heart consumes its three ordinary ingredients");
            grid(item(Material.NETHERITE_INGOT,2),item(Material.DIAMOND,2),item(Material.BLUE_ICE,2));
            click("SHIFT",()->{ok(occupied()==0&&count("frost_forge_blade")==2,"two interchangeable blades are crafted");
                grid(item(Material.GOLD_INGOT,2),item(Material.IRON_INGOT,2),item(Material.AMETHYST_SHARD,2));
                click("SHIFT",()->{ok(occupied()==0&&count("frost_forge_guard")==2,"two interchangeable guards are crafted");assemble();});
            });
        });
    }
    private void assemble(){
        ItemStack bladeA=take("frost_forge_blade"),bladeT=take("frost_forge_blade"),guardA=take("frost_forge_guard"),guardT=take("frost_forge_guard"),heartA=take("frost_forge_aurora_core");
        ItemStack modified=heartA.clone();ItemMeta meta=modified.getItemMeta();meta.setDisplayName("not the original heart");modified.setItemMeta(meta);
        grid(bladeA,guardA,modified);
        later(3,()->{ok(empty(work.getResult()),"altered custom heart cannot substitute for an exact component");grid(bladeA,guardA,heartA);
            later(3,()->{ok("assembled_aurora_sword".equals(id(work.getResult())),"aurora heart selects the aurora sword");
                click("CLICK",()->{aurora=cursor("assembled_aurora_sword");ok(occupied()==0&&aurora.getItemMeta().getCustomModelData()==1832,"aurora uses the existing visible 1832 model and consumes three parts");store(aurora.clone());
                    grid(bladeT,guardT,tempestHeart);later(3,()->{ok("assembled_tempest_sword".equals(id(work.getResult())),"tempest heart selects the tempest sword");
                        click("CLICK",()->{tempest=cursor("assembled_tempest_sword");ok(occupied()==0&&tempest.getItemMeta().getCustomModelData()==1836,"tempest uses the existing visible 1836 model and consumes three parts");store(tempest.clone());fullInventory();});
                    });
                });
            });
        });
    }
    private void fullInventory(){
        ItemStack[] saved=a.getInventory().getStorageContents();
        for(int i=0;i<36;i++)a.getInventory().setItem(i,item(Material.COBBLESTONE,64));
        grid(item(Material.HEART_OF_THE_SEA,1),item(Material.ECHO_SHARD,1),item(Material.BLAZE_POWDER,1));
        click("SHIFT",()->{ok(occupied()==3&&empty(a.getItemOnCursor()),"shift-craft of a new heart does not consume inputs when inventory is full");
            work.setMatrix(new ItemStack[9]);a.getInventory().setStorageContents(saved);a.closeInventory();untouched();finish();});
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void protection(EntityDamageByEntityEvent e){if(cancelDamage&&e.getDamager().equals(a)&&e.getEntity().equals(b))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR) public void observe(EntityDamageByEntityEvent e){
        if(!e.getDamager().equals(a)||!e.getEntity().equals(b))return;
        if(lateDamage)e.setCancelled(true);hits++;lastHitCancelled=e.isCancelled();
        if(addTeamOnHit&&!e.isCancelled()){addTeamOnHit=false;core.getStore().addMutualTeammates(a.getName(),b.getName());}
        if(moveOnHit&&!e.isCancelled()){moveOnHit=false;lastTeleport=b.teleport(denyLocation);}
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void cancelEffect(EntityPotionEffectEvent e){
        if(e.getEntity().equals(a)&&e.getNewEffect()!=null&&e.getNewEffect().getType().equals(speed)){
            if(cancelSpeed)e.setCancelled(true);
            if(nestDuringSpeed&&!e.isCancelled()){
                nestDuringSpeed=false;nestedIssued=true;hold(tempest);b.setNoDamageTicks(0);b.setLastDamage(0);b.damage(1,a);hold(aurora);
            }
        }
    }
    private void hold(ItemStack value){a.getInventory().setHeldItemSlot(0);a.getInventory().setItemInMainHand(value.clone());}
    private ItemStack find(String wanted){for(ItemStack value:a.getInventory().getStorageContents())if(wanted.equals(id(value)))return value.clone();throw new AssertionError("missing saved sword "+wanted);}
    private void attack(Runnable after){b.setNoDamageTicks(0);b.setLastDamage(0);request("ATTACK "+b.getEntityId());later(10,after);}
    private void clearEffects(){a.removePotionEffect(speed);b.removePotionEffect(slow);b.removePotionEffect(weak);cooldowns().clear();}
    private void combat(){
        frost=find("assembled_frost_sword");aurora=find("assembled_aurora_sword");tempest=find("assembled_tempest_sword");
        ok(aurora.getItemMeta().getCustomModelData()==1832&&tempest.getItemMeta().getCustomModelData()==1836,"both modular swords and model metadata survive restart");
        untouched();b.setMaxHealth(200);b.setHealth(200);b.setNoDamageTicks(0);b.setMaximumNoDamageTicks(0);b.setInvulnerable(false);clearEffects();hold(aurora);
        double hp=b.getHealth();cancelSpeed=true;attack(()->{
            cancelSpeed=false;ok(b.getHealth()<hp&&!a.hasPotionEffect(speed)&&cooldowns().isEmpty(),"cancelled self-speed application does not consume cooldown");
            a.addPotionEffect(new PotionEffect(speed,200,2,false,true,true));attack(()->{
                PotionEffect current=a.getPotionEffect(speed);ok(current!=null&&current.getAmplifier()==2&&cooldowns().isEmpty(),"stronger existing speed is never downgraded or charged");
                a.removePotionEffect(speed);attack(()->{
                    PotionEffect applied=a.getPotionEffect(speed);ok(applied!=null&&applied.getAmplifier()==0&&applied.getDuration()>0&&cooldowns().size()==1,"real aurora hit grants speed to the attacker");
                    hold(tempest);attack(()->{
                        ok(!b.hasPotionEffect(weak)&&cooldowns().size()==1,"switching hearts cannot bypass the shared cooldown");nestedCheck();
                    });
                });
            });
        });
    }
    private void nestedCheck(){
        clearEffects();hold(aurora);nestDuringSpeed=true;nestedIssued=false;int before=hits;
        attack(()->{
            ok(nestedIssued&&hits>=before+2&&a.hasPotionEffect(speed)&&!b.hasPotionEffect(weak)&&cooldowns().size()==1,
                    "nested damage from a potion callback cannot activate a second sword");
            hold(tempest);tempestTests();
        });
    }
    private void tempestTests(){
        clearEffects();cancelDamage=true;attack(()->{
            cancelDamage=false;ok(lastHitCancelled&&!b.hasPotionEffect(weak)&&cooldowns().isEmpty(),"early cancelled hit grants no tempest effect or cooldown");
            lateDamage=true;attack(()->{
                lateDamage=false;ok(lastHitCancelled&&!b.hasPotionEffect(weak)&&cooldowns().isEmpty(),"late MONITOR cancellation also grants no tempest effect");
                core.getStore().toggleQuickFreeze(b.getUniqueId());attack(()->{
                    core.getStore().clearQuickFreeze(b.getUniqueId());ok(!b.hasPotionEffect(weak)&&cooldowns().isEmpty(),"moderation-frozen target is not affected");
                    b.addPotionEffect(new PotionEffect(weak,200,2,false,true,true));attack(()->{
                        PotionEffect current=b.getPotionEffect(weak);ok(current!=null&&current.getAmplifier()==2&&cooldowns().isEmpty(),"stronger existing weakness is never downgraded or charged");
                        b.removePotionEffect(weak);attack(()->{
                            PotionEffect applied=b.getPotionEffect(weak);ok(applied!=null&&applied.getAmplifier()==0&&applied.getDuration()>0&&cooldowns().size()==1,"real tempest hit applies weakness to the target");
                            hold(frost);attack(()->{ok(!b.hasPotionEffect(slow)&&cooldowns().size()==1,"tempest cooldown also blocks frost when switching weapons");
                                cooldowns().clear();attack(()->{ok(b.hasPotionEffect(slow)&&cooldowns().size()==1,"original frost effect still works after clearing the shared cooldown");extraSafety();});
                            });
                        });
                    });
                });
            });
        });
    }
    private void extraSafety(){
        clearEffects();hold(tempest);core.getStore().toggleQuickFreeze(a.getUniqueId());attack(()->{
            core.getStore().clearQuickFreeze(a.getUniqueId());ok(!b.hasPotionEffect(weak)&&cooldowns().isEmpty(),"moderation-frozen attacker triggers no effect");
            core.getConfig().set("enable-builtin-auth",true);core.getStore().setAuthenticated(a,true);core.getStore().setAuthenticated(b,false);
            attack(()->{ok(!b.hasPotionEffect(weak)&&cooldowns().isEmpty(),"unauthenticated target receives no secondary effect");core.getConfig().set("enable-builtin-auth",false);
                ItemStack spoof=ItemFactory.make(Material.STICK,1,"spoof",java.util.Collections.<String>emptyList(),false,"assembled_tempest_sword",null,0);hold(spoof);
                attack(()->{ok(!b.hasPotionEffect(weak)&&cooldowns().isEmpty(),"copied identifier on a stick cannot trigger a modular ability");teamRecheck();});
            });
        });
    }
    private void teamRecheck(){
        hold(tempest);addTeamOnHit=true;int before=hits;attack(()->{
            ok(hits>before&&!lastHitCancelled&&!b.hasPotionEffect(weak)&&cooldowns().isEmpty(),"friendship added after an accepted hit prevents the delayed effect");
            core.getStore().removeMutualTeammates(a.getName(),b.getName());mobCase();
        });
    }
    private void mobCase(){
        Zombie mob=world.spawn(new Location(world,258.5,81,258.5),Zombie.class);mob.setAI(false);mob.setMaxHealth(200);mob.setHealth(200);mob.setInvulnerable(false);mob.setNoDamageTicks(0);
        hold(aurora);clearEffects();later(15,()->{double hp=mob.getHealth();request("ATTACK "+mob.getEntityId());later(10,()->{
            ok(mob.getHealth()<hp&&a.hasPotionEffect(speed)&&cooldowns().size()==1,"real hit on an ordinary mob can grant the aurora speed");
            mob.addScoreboardTag("kg_event_boss");clearEffects();mob.setNoDamageTicks(0);request("ATTACK "+mob.getEntityId());later(10,()->{
                ok(!a.hasPotionEffect(speed)&&cooldowns().isEmpty(),"event-boss-tagged mob cannot trigger the forge ability");mob.remove();capacityCheck();
            });
        });});
    }
    private void capacityCheck(){
        clearEffects();hold(aurora);Map<UUID,Long> values=cooldowns();long now=System.nanoTime();UUID first=new UUID(0x123456789L,1);
        values.put(first,now);for(int i=2;i<=4096;i++)values.put(new UUID(0x123456789L,i),now);
        attack(()->{
            ok(!a.hasPotionEffect(speed)&&values.size()==4096&&values.containsKey(first),"full cooldown table never evicts an active player's cooldown");
            values.put(first,System.nanoTime()-13_000_000_000L);attack(()->{
                ok(a.hasPotionEffect(speed)&&values.size()==4096&&!values.containsKey(first)&&values.containsKey(a.getUniqueId()),"expired cooldown is reclaimed and the new ability can proceed");
                clearEffects();b.removePotionEffect(speed);long stamp=System.nanoTime();
                for(int i=1;i<=4095;i++)values.put(new UUID(0x3456789L,i),stamp);
                hold(aurora);b.getInventory().setItemInMainHand(aurora.clone());b.setNoDamageTicks(0);b.setLastDamage(0);a.setNoDamageTicks(0);a.setLastDamage(0);
                b.damage(1,a);a.damage(1,b);
                later(5,()->{
                    ok(a.hasPotionEffect(speed)&&!b.hasPotionEffect(speed)&&values.size()==4096&&values.containsKey(a.getUniqueId())&&!values.containsKey(b.getUniqueId()),
                            "two same-tick server hits reserve the last cooldown slot at most once");
                    clearEffects();b.removePotionEffect(speed);b.getInventory().setItemInMainHand(null);wgSetup();
                });
            });
        });
    }
    private void wgSetup(){
        regionManager=WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
        ok(regionManager!=null,"real WorldGuard region manager is available");
        denied=new ProtectedCuboidRegion("kg_forge_deny",BlockVector3.at(261,77,254),BlockVector3.at(265,90,258));
        denied.setFlag(Flags.PVP,StateFlag.State.DENY);regionManager.addRegion(denied);denyLocation=new Location(world,262.5,81,256.5,90,0);
        a.teleport(new Location(world,256.5,81,256.5,-90,0));b.teleport(new Location(world,258.5,81,256.5,90,0));
        clearEffects();hold(frost);later(60,()->{
            int before=hits;moveOnHit=true;attack(()->{
                ok(hits>before&&!lastHitCancelled&&lastTeleport&&b.getLocation().getBlockX()>=261&&!b.hasPotionEffect(slow)&&cooldowns().isEmpty(),"real hit accepted before teleport cannot apply a delayed effect inside WG pvp-deny");
                mobProtectedByWg();
            });
        });
    }
    private void mobProtectedByWg(){
        denied.setFlag(Flags.PVP,StateFlag.State.ALLOW);denied.setFlag(Flags.BLOCK_PLACE,StateFlag.State.DENY);
        b.teleport(new Location(world,257.5,81,254.5,90,0));a.teleport(new Location(world,260.3,81,256.5,-90,0));
        clearEffects();hold(tempest);later(60,()->{
            Zombie mob=world.spawn(new Location(world,262.3,81,256.5),Zombie.class);
            mob.setAI(false);mob.setMaxHealth(200);mob.setHealth(200);mob.setInvulnerable(false);mob.setNoDamageTicks(0);
            later(15,()->{double hp=mob.getHealth();request("ATTACK "+mob.getEntityId());later(10,()->{
                ok(mob.getHealth()<hp&&!mob.hasPotionEffect(weak)&&cooldowns().isEmpty(),"accepted mob hit cannot apply a secondary effect in WG block-place-deny");
                mob.remove();disabledWg();
            });});
        });
    }
    private void disabledWg(){
        Bukkit.getPluginManager().disablePlugin(Bukkit.getPluginManager().getPlugin("WorldGuard"));
        b.teleport(new Location(world,258.5,81,256.5,90,0));a.teleport(new Location(world,256.5,81,256.5,-90,0));clearEffects();
        later(40,()->{double hp=b.getHealth();attack(()->{
            ok(b.getHealth()<hp&&!b.hasPotionEffect(slow)&&cooldowns().isEmpty(),"installed but disabled WorldGuard fails closed for the delayed effect");spawnSafety();
        });});
    }
    private void spawnSafety(){
        World spawn=Bukkit.getWorld("spawn_world");spawn.setGameRule(org.bukkit.GameRule.NATURAL_REGENERATION,false);
        a.teleport(new Location(spawn,.5,300,.5,-90,0));b.teleport(new Location(spawn,2.3,300,.5,90,0));
        a.setAllowFlight(true);a.setFlying(true);b.setAllowFlight(true);b.setFlying(true);clearEffects();hold(tempest);
        later(40,()->{double hp=b.getHealth();attack(()->{
            ok(lastHitCancelled&&b.getHealth()==hp&&!b.hasPotionEffect(weak)&&cooldowns().isEmpty(),"protected spawn blocks tempest effects and cooldowns");finish();
        });});
    }
}
