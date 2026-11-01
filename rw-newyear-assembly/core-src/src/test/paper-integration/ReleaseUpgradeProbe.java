package net.kinggrief.probe;

import net.kinggrief.core.DataStore;
import net.kinggrief.core.KingGriefCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Set;

/** Representative data created by the published Core, then read/saved by the prototype. */
public final class ReleaseUpgradeProbe extends JavaPlugin implements Listener {
    private KingGriefCore core;private DataStore store;private Player a,b;private World world,spawn;
    private boolean started,done;private int checks;
    private static final long KIT=2_000_000_000_000L;
    @Override public void onEnable(){saveDefaultConfig();core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");store=core.getStore();Bukkit.getPluginManager().registerEvents(this,this);}
    @EventHandler public void join(PlayerJoinEvent event){
        if("TreeHelper".equals(event.getPlayer().getName()))a=event.getPlayer();
        if("TreeFriend".equals(event.getPlayer().getName()))b=event.getPlayer();
        if(a!=null&&b!=null&&!started){started=true;later(40,this::prepare);}
    }
    private void later(long ticks,Runnable work){Bukkit.getScheduler().runTaskLater(this,()->{try{work.run();}catch(Throwable error){fail(error);}},ticks);}
    private void ok(boolean value,String message){if(!value)throw new AssertionError(message);checks++;getLogger().info("KG-RELEASE-UPGRADE PASS "+message);}
    private void fail(Throwable error){if(done)return;done=true;getLogger().severe("KG-RELEASE-UPGRADE RESULT FAIL "+error);error.printStackTrace();}
    private void finish(){done=true;getLogger().info("KG-RELEASE-UPGRADE RESULT PASS "+checks+" checks");}
    private YamlConfiguration data(){try{Field f=store.getClass().getDeclaredField("yml");f.setAccessible(true);return (YamlConfiguration)f.get(store);}catch(Exception e){throw new RuntimeException(e);}}
    private ItemStack marker(String label){
        ItemStack item=new ItemStack(Material.DIAMOND_SWORD);ItemMeta meta=item.getItemMeta();meta.setDisplayName("Legacy "+label);
        meta.addEnchant(Enchantment.UNBREAKING,2,true);meta.getPersistentDataContainer().set(new NamespacedKey(this,"upgrade-marker"),PersistentDataType.STRING,label);
        item.setItemMeta(meta);return item;
    }
    private boolean matches(ItemStack item,String label){
        if(item==null||item.getType()!=Material.DIAMOND_SWORD||item.getAmount()!=1||!item.hasItemMeta())return false;
        ItemMeta meta=item.getItemMeta();return ("Legacy "+label).equals(meta.getDisplayName())&&meta.getEnchantLevel(Enchantment.UNBREAKING)==2
                &&label.equals(meta.getPersistentDataContainer().get(new NamespacedKey(this,"upgrade-marker"),PersistentDataType.STRING));
    }
    private Location claimCenter(){return new Location(world,160,81,160);}
    private Location home(){return new Location(world,161.5,83,160.5);}
    private Location chestLocation(){return new Location(world,162,81,160);}
    private Location spawnChest(){return new Location(spawn,0,160,0);}
    private void putChest(Location loc,String marker){loc.getBlock().setType(Material.CHEST);Chest chest=(Chest)loc.getBlock().getState();chest.getBlockInventory().setItem(0,marker(marker));}
    private boolean hasChest(Location loc,String marker){return loc.getBlock().getState() instanceof Chest&&matches(((Chest)loc.getBlock().getState()).getBlockInventory().getItem(0),marker);}
    private void prepare(){
        world=Bukkit.getWorld("world");spawn=Bukkit.getWorld("spawn_world");if(world==null||spawn==null)throw new IllegalStateException("expected worlds unavailable");
        world.getChunkAtAsync(10,10).whenComplete((chunk,error)->Bukkit.getScheduler().runTask(this,()->{
            try{if(error!=null||chunk==null)throw new IllegalStateException("failed to prepare remote chunk",error);world.addPluginChunkTicket(10,10,this);runPhase();}
            catch(Throwable failure){fail(failure);}
        }));
    }
    private void runPhase(){int phase=getConfig().getInt("phase",1);if(phase==1)oldCore();else afterUpgrade(phase);}
    private void oldCore(){
        ok(Bukkit.getRecipe(new NamespacedKey(core,"frost_forge_blade"))==null,"released Core does not yet contain experimental forge recipes");
        a.getInventory().clear();a.getInventory().setHeldItemSlot(0);a.getInventory().setItemInMainHand(marker("auction"));
        a.performCommand("ah sell 1250");
        ok(store.getAuctionLots().size()==1&&a.getInventory().getItemInMainHand().getType().isAir(),"published Core really lists a marked item through /ah sell");
        DataStore.AuctionLot lot=store.getAuctionLots().get(0);
        ok(lot.price==1250&&matches(lot.item,"auction"),"legacy auction contains the exact item metadata");
        a.getInventory().setItem(0,marker("inventory"));a.getInventory().setItem(5,new ItemStack(Material.DIAMOND,13));
        a.getInventory().setItemInOffHand(marker("offhand"));a.getEnderChest().setItem(4,marker("ender"));
        store.setCoins(a.getName(),123456.75);store.setCoins(b.getName(),9876.5);store.setCoins("AbsentPlayer",42000.25);
        store.addSnowflakes(a.getName(),73-store.getSnowflakes(a.getName()));store.setRelics(a.getName(),321);
        store.setCaseKeys(a.getName(),"legendary",7);store.setSavedRank(a.getName(),"dragon");store.setJob(a.getName(),"miner");store.setKitReadyAt(a.getName(),"start",KIT);
        store.mute("AbsentPlayer",0,"upgrade-mute","Console");store.ban("AbsentPlayer",0,"upgrade-ban","Console");
        claimCenter().getBlock().setType(Material.DIAMOND_ORE);putChest(chestLocation(),"world-chest");putChest(spawnChest(),"spawn-chest");
        DataStore.Claim claim=store.createClaim(a.getName(),claimCenter(),7);claim.members.add(b.getName().toLowerCase(Locale.ROOT));
        DataStore.Clan clan=store.createClan(a.getName(),"IceLegacy");if(clan==null)throw new IllegalStateException("legacy clan creation failed");
        store.joinClan(b.getName(),clan);clan.bank=4321.5;clan.friendlyFire=true;clan.liveStorage.setItem(6,marker("clan"));
        store.setHome(a.getName(),"legacy",home());store.setCustomWarp("legacywarp",a.getName(),home());
        data().set("upgradeProbe.spawnUUID",spawn.getUID().toString());data().set("upgradeProbe.lotId",lot.id);
        data().set("foreignExtension.keep","unknown-root-field");store.save();a.saveData();b.saveData();world.save();spawn.save();
        unchanged();finish();
    }
    private void unchanged(){
        ok(store.getCoins(a.getName())==123456.75&&store.getCoins(b.getName())==9876.5&&store.getCoins("AbsentPlayer")==42000.25,
                "online and offline coin balances are unchanged");
        ok(store.getSnowflakes(a.getName())==73&&store.getRelics(a.getName())==321&&store.getCaseKeys(a.getName(),"legendary")==7,
                "snowflakes, relics and case keys are unchanged");
        ok("dragon".equals(store.getSavedRank(a.getName()))&&"miner".equals(store.getJob(a.getName()))&&store.getKitReadyAt(a.getName(),"start")==KIT,
                "rank, profession and kit cooldown are unchanged");
        ok(store.getMuteRemainingSec("AbsentPlayer")>0&&"upgrade-mute".equals(store.getMuteReason("AbsentPlayer"))
                &&"upgrade-ban".equals(store.getActiveBanReason("AbsentPlayer")),"offline permanent mute and ban are unchanged");
        ok(matches(a.getInventory().getItem(0),"inventory")&&a.getInventory().getItem(5)!=null&&a.getInventory().getItem(5).getType()==Material.DIAMOND
                &&a.getInventory().getItem(5).getAmount()==13&&matches(a.getInventory().getItemInOffHand(),"offhand")
                &&matches(a.getEnderChest().getItem(4),"ender"),"inventory, offhand, ender chest and custom metadata are unchanged");
        DataStore.Claim claim=store.getClaimAt(claimCenter());
        ok(claim!=null&&claim.owner.equalsIgnoreCase(a.getName())&&claim.radius==7&&claim.canAccess(b.getName())
                &&claimCenter().getBlock().getType()==Material.DIAMOND_ORE,"legacy claim center, owner, radius and builder survive");
        DataStore.Clan clan=store.getClanOf(a.getName());
        ok(clan!=null&&clan==store.getClanOf(b.getName())&&clan.bank==4321.5&&clan.friendlyFire&&matches(clan.liveStorage.getItem(6),"clan"),
                "legacy clan membership, bank, friendly-fire and marked storage survive");
        DataStore.AuctionLot found=null;for(DataStore.AuctionLot lot:store.getAuctionLots())if(lot.id==data().getInt("upgradeProbe.lotId"))found=lot;
        ok(found!=null&&found.seller.equalsIgnoreCase(a.getName())&&found.price==1250&&matches(found.item,"auction"),
                "legacy auction lot and its custom metadata survive");
        Location storedHome=store.getHomes(a.getName()).get("legacy"),storedWarp=store.getCustomWarpLocation("legacywarp");
        ok(storedHome!=null&&storedWarp!=null&&storedHome.getWorld().equals(world)&&storedHome.distanceSquared(home())<0.01
                &&storedWarp.getWorld().equals(world)&&storedWarp.distanceSquared(home())<0.01,"home and custom warp survive");
        ok(spawn.getUID().toString().equals(data().getString("upgradeProbe.spawnUUID"))&&hasChest(spawnChest(),"spawn-chest")
                &&spawn.getBlockAt(0,130,0).getType()==Material.SPRUCE_WOOD&&hasChest(chestLocation(),"world-chest"),
                "prepared spawn world and both player-edited chests are preserved");
        ok("unknown-root-field".equals(data().getString("foreignExtension.keep")),"unknown root data is preserved");
    }
    private boolean roleExists(DataStore.Claim claim){
        try{Field f=claim.getClass().getField("containerMembers");return ((Set<?>)f.get(claim)).contains("offlinemember");}
        catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
    }
    private void afterUpgrade(int phase){
        unchanged();ok(Bukkit.getRecipe(new NamespacedKey(core,"frost_forge_blade"))!=null,"new forge recipe registers without rebuilding the spawn");
        DataStore.Claim claim=store.getClaimAt(claimCenter());
        if(phase==2){
            ok(a.teleport(home()),"owner teleports to the preserved claim for the role command");
            a.performCommand("rg role OfflineMember container");
            ok(roleExists(claim)&&claim.canAccess(b.getName()),"new container role adds access without dropping the old builder");
            a.performCommand("frostforge");
            ok(a.getOpenInventory().getTopInventory().getSize()==27,"new forge menu opens for the upgraded player");
            a.closeInventory();store.save();a.saveData();world.save();spawn.save();
        }else ok(roleExists(claim)&&claim.canAccess(b.getName()),"new role and old builder both survive a further restart");
        finish();
    }
}
