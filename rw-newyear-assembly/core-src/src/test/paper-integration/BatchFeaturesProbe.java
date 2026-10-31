package net.kinggrief.probe;

import net.kinggrief.core.BossEventManager;
import net.kinggrief.core.DataStore;
import net.kinggrief.core.KingGriefCore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Disposable Paper checks, with two real protocol players. */
public final class BatchFeaturesProbe extends JavaPlugin implements Listener {
    private KingGriefCore core;
    private Player a,b;
    private World world;
    private boolean running, done, cancelHit, cancelMine;
    private int checks;
    private final int[][] chunks={{25,25},{25,26},{26,25},{26,26}};
    @Override public void onEnable(){
        saveDefaultConfig();
        core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");
        Bukkit.getPluginManager().registerEvents(this,this);
    }
    @EventHandler public void join(PlayerJoinEvent event){
        if("OrbTester".equals(event.getPlayer().getName()))a=event.getPlayer();
        if("OrbEnemy".equals(event.getPlayer().getName()))b=event.getPlayer();
        if(a!=null&&b!=null&&!running){running=true;later(30,()->begin());}
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void protect(EntityDamageByEntityEvent e){
        if(cancelHit&&e.getEntity().getScoreboardTags().contains("kg_event_boss"))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void protectMine(BlockBreakEvent e){
        if(cancelMine)e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR) public void observe(EntityDamageByEntityEvent e){
        if(e.getEntity() instanceof Player) getLogger().info("KG-BATCH DAMAGE cancelled="+e.isCancelled()+" final="+e.getFinalDamage()+" world="+e.getEntity().getWorld().getName());
    }
    private void later(long ticks,Runnable task){Bukkit.getScheduler().runTaskLater(this,()->{try{task.run();}catch(Throwable error){fail(error);}},ticks);}
    private void ok(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;getLogger().info("KG-BATCH PASS "+message);}
    private void fail(Throwable error){if(done)return;done=true;getLogger().severe("KG-BATCH RESULT FAIL "+error);error.printStackTrace();}
    private void finish(){done=true;getLogger().info("KG-BATCH RESULT PASS "+checks+" checks");}
    private void begin(){
        world=Bukkit.getWorld("world");
        if(world==null)throw new AssertionError("fixture world missing");
        if(getConfig().getInt("phase",1)==2){later(20,()->restartChecks());return;}
        loadChunk(0);
    }
    private void loadChunk(int index){
        if(index==chunks.length){initial();return;}
        try {
            Method async=World.class.getMethod("getChunkAtAsync",int.class,int.class,boolean.class);
            int x=chunks[index][0],z=chunks[index][1];
            CompletableFuture<?> future=(CompletableFuture<?>)async.invoke(world,x,z,true);
            future.whenComplete((chunk,error)->Bukkit.getScheduler().runTask(this,()->{
                try{
                    if(error!=null||chunk==null)throw new AssertionError("async chunk load failed",error);
                    world.addPluginChunkTicket(x,z,this);loadChunk(index+1);
                }catch(Throwable failure){fail(failure);}
            }));
        }catch(Exception error){fail(error);}
    }
    private void initial(){
        World arena=Bukkit.getWorld("spawn_world");arena.setPVP(true);
        a.teleport(new Location(arena,75.5,94,10.5));b.teleport(new Location(arena,77.5,94,10.5));
        a.setGameMode(GameMode.SURVIVAL);b.setGameMode(GameMode.SURVIVAL);
        a.getInventory().clear();b.getInventory().clear();
        // Same-world movement and initial login invulnerability settle on real ticks.
        later(40,()->clanChecks());
    }
    private void clanChecks(){
        DataStore store=core.getStore();
        ok(!b.isInvulnerable(),"initial player invulnerability has expired");
        DataStore.Clan red=store.createClan(a.getName(),"RedTest"), blue=store.createClan(b.getName(),"BlueTest");
        ok(red!=null&&blue!=null,"test clans created");
        b.performCommand("clan allyaccept RedTest");
        ok(!red.allies.contains(blue.id),"cannot accept without invitation");
        a.performCommand("clan ally BlueTest");
        ok(!red.allies.contains(blue.id)&&!blue.allies.contains(red.id),"one-sided invitation is not an alliance");
        b.performCommand("clan allyaccept RedTest");
        ok(red.allies.contains(blue.id)&&blue.allies.contains(red.id),"alliance needs acceptance by other clan");
        ok(!store.isTeammatePvpAllowed(a.getName(),b.getName()),"allied fire defaults to off");
        b.setHealth(20);b.setNoDamageTicks(0);b.damage(4,a);
        ok(b.getHealth()==20,"real allied attack is blocked");
        a.performCommand("clan allypvp BlueTest");
        ok(!store.isTeammatePvpAllowed(a.getName(),b.getName()),"one-sided PvP consent is insufficient");
        withBlockedSave(()->b.performCommand("clan allypvp RedTest"));
        ok(!blue.allyPvp.contains(red.id)&&!store.isTeammatePvpAllowed(a.getName(),b.getName()),"failed consent save rolls back in-memory alliance state");
        b.performCommand("clan allypvp RedTest");
        ok(store.isTeammatePvpAllowed(a.getName(),b.getName()),"both sides can consent to PvP");
        b.setNoDamageTicks(0);b.damage(4,a);
        ok(b.getHealth()<20,"real allied hit is accepted after mutual consent");
        b.setHealth(20);
        // The PvP probe deliberately created a combat tag; clear it before the offline-boss scenario.
        core.getHud().clearCombat(a.getUniqueId());core.getHud().clearCombat(b.getUniqueId());
        ok(a.performCommand("cosmetic trail snow")&&a.performCommand("cosmetic login stars"),"cosmetic commands registered");
        String cosmetics="cosmetics."+a.getUniqueId();
        ok("snow".equals(yaml().getString(cosmetics+".trail"))&&"stars".equals(yaml().getString(cosmetics+".login")),"cosmetic settings are saved");
        withBlockedSave(()->a.performCommand("cosmetic trail hearts"));
        ok("snow".equals(yaml().getString(cosmetics+".trail")),"failed cosmetic update keeps the previous choice");
        ok(a.performCommand("wintershow"),"non-destructive spawn light show is registered");
        a.teleport(new Location(world,418,75,418));b.teleport(new Location(world,420,75,418));
        a.setAllowFlight(true);a.setFlying(true);b.setAllowFlight(true);b.setFlying(true);
        later(20,()->utility());
    }
    private void utility(){
        a.getInventory().clear();
        ItemStack held=new ItemStack(Material.DIAMOND_PICKAXE);
        ItemStack special=new ItemStack(Material.DIAMOND,7);
        ItemMeta meta=special.getItemMeta();meta.setDisplayName("§bUnique metadata");
        meta.setLore(java.util.Arrays.asList("§dKeep me"));
        meta.getPersistentDataContainer().set(new NamespacedKey(this,"unique"),PersistentDataType.STRING,"value");special.setItemMeta(meta);
        a.getInventory().setItem(0,held);a.getInventory().setItem(12,special);a.getInventory().setItem(20,new ItemStack(Material.STONE,32));
        a.getInventory().setItemInOffHand(new ItemStack(Material.IRON_PICKAXE));
        ok(a.performCommand("sortinv"),"sort command registered");
        ok(held.equals(a.getInventory().getItem(0))&&a.getInventory().getItemInOffHand().getType()==Material.IRON_PICKAXE,"sorting preserves hotbar and offhand");
        ok(special.equals(a.getInventory().getItem(9))&&a.getInventory().getItem(10).getType()==Material.STONE,"sorting preserves custom lore, PDC and amounts");
        a.openInventory(Bukkit.createInventory(null,9));
        ItemStack[] before=a.getInventory().getStorageContents().clone();a.performCommand("sortinv");
        ok(java.util.Arrays.equals(before,a.getInventory().getStorageContents()),"sorting refuses an open container");
        a.closeInventory();
        ok(a.performCommand("compare")&&a.performCommand("calendar")&&a.performCommand("kgstatus")
                &&a.performCommand("kgspawn status")&&a.performCommand("way mine")&&a.performCommand("deathpoint off"),"diagnostic and navigation commands are available");
        DataStore.Claim claim=core.getStore().createClaim(a.getName(),a.getLocation(),4);
        Material blockBefore=a.getLocation().getBlock().getType();
        ok(a.performCommand("rg borders")&&a.getLocation().getBlock().getType()==blockBefore,"private border preview does not change blocks");
        core.getStore().removeClaim(claim);
        org.bukkit.block.Block block=world.getBlockAt(418,65,417);block.setType(Material.DIAMOND_ORE,false);
        cancelMine=true;a.breakBlock(block);cancelMine=false;
        ok(block.getType()==Material.DIAMOND_ORE,"cancelled mining leaves ore intact");
        a.breakBlock(block);
        ok(block.getType()!=Material.DIAMOND_ORE,"accepted real mining breaks ore");
        later(4,()->{
            ok(yaml().getInt("utility.mining."+a.getUniqueId()+".total")==1,"only accepted mining is counted");
            ok(a.performCommand("minestats"),"mining statistics command registered");
            tree();a.performCommand("pet penguin");later(15,()->petChecks());
        });
    }
    private void tree(){
        DataStore store=core.getStore();
        store.addSnowflakes(a.getName(),40);store.addSnowflakes(b.getName(),10);
        yaml().set("winterTree.total",20);store.save();
        int before=store.getSnowflakes(a.getName());double coins=store.getCoins(a.getName());
        a.performCommand("tree donate 0");
        ok(store.getSnowflakes(a.getName())==before,"invalid tree donation is rejected");
        a.performCommand("tree donate 10");
        ok(store.getSnowflakes(a.getName())==before-10&&yaml().getInt("winterTree.total")==30,"tree donation and progress commit together");
        a.performCommand("tree donate 1");
        ok(store.getSnowflakes(a.getName())==before-10&&yaml().getInt("winterTree.total")==30,"UTC daily donation limit is enforced");
        a.performCommand("tree claim");
        ok(store.getCoins(a.getName())==coins+5000,"earned first milestone is paid");
        a.performCommand("tree claim");
        ok(store.getCoins(a.getName())==coins+5000,"tree milestone cannot pay twice");
        int beforeB=store.getSnowflakes(b.getName());
        withBlockedSave(()->b.performCommand("tree donate 1"));
        ok(store.getSnowflakes(b.getName())==beforeB&&yaml().getInt("winterTree.total")==30,"failed tree commit leaves both balances intact");
        b.performCommand("tree donate 1");
        b.performCommand("tree claim");
        ok(store.getCoins(b.getName())==2500.0,"late contributor cannot claim a past milestone");
        a.performCommand("tree top");
    }
    private long displays(){return a.getWorld().getEntities().stream().filter(e->e.getScoreboardTags().contains("kg_cosmetic_pet")).count();}
    private void petChecks(){
        long count=displays();ok(count==10,"penguin uses ten display-model parts");
        for(Entity e:a.getWorld().getEntities())if(e.getScoreboardTags().contains("kg_cosmetic_pet"))
            ok(e instanceof BlockDisplay&&!e.isPersistent(),"pet part is a non-persistent block display");
        core.getStore().setParkourCp(a,a.getLocation());
        later(10,()->{
            ok(displays()==0,"pet disappears during parkour");
            core.getStore().setParkourCp(a,null);a.performCommand("pet dragon");
            later(10,()->{
                ok(displays()==13,"dragon model replaces penguin without leaking displays");
                a.performCommand("pet off");later(10,()->{
                    ok(displays()==0,"pet off removes every part");
                    a.performCommand("pet deer");later(10,()->{ok(displays()==14,"deer model selected");boss();});
                });
            });
        });
    }
    private void boss(){
        BossEventManager events=core.getBossEvents();
        BossEventManager.ActiveBossEvent ev=events.spawnBossEvent("grinch",new Location(world,422,65,422));
        ok(ev!=null,"real boss event spawned in isolated fixture");
        LivingEntity boss=(LivingEntity)Bukkit.getEntity(ev.bossUuid);
        cancelHit=true;boss.damage(40,a);cancelHit=false;
        ok(!ev.damageMap.containsKey(a.getName()),"cancelled boss hit never counts");
        boss.setNoDamageTicks(0);boss.damage(80,b);
        boss.setNoDamageTicks(0);boss.damage(80,b);
        boss.setNoDamageTicks(0);boss.damage(200,a);
        boss.setNoDamageTicks(0);boss.damage(200,a);
        ok(ev.damageMap.containsKey(a.getName())&&ev.damageMap.containsKey(b.getName()),"accepted real damage records both players");
        double aDamage=ev.damageMap.get(a.getName());
        ok(aDamage>ev.damageMap.get(b.getName())&&ev.damageMap.get(b.getName())>24,"real contributors meet damage threshold and expected ranking");
        ev.damageMap.put("OfflineThree",24.0);ev.damageOwners.put("OfflineThree",UUID.randomUUID());
        ev.damageMap.put("OfflineFour",20.0);ev.damageOwners.put("OfflineFour",UUID.randomUUID());
        ev.damageMap.put("TinyDamage",1.0);ev.damageOwners.put("TinyDamage",UUID.randomUUID());
        double aCoins=core.getStore().getCoins(a.getName()), bCoins=core.getStore().getCoins(b.getName());
        int aKeys=core.getStore().getCaseKeys(a.getName(),"legendary");
        b.kickPlayer("test offline contribution");
        later(15,()->{
            ok(Bukkit.getPlayerExact("OrbEnemy")==null,"second contributor is offline before the kill");
            boss.setHealth(5);boss.setNoDamageTicks(0);boss.damage(10000,a);
            ok(ev.damageMap.get(a.getName())<=aDamage+5.0001,"overkill counts at most remaining boss health");
            later(5,()->{
                ok(ev.bossDefeated,"boss death processed");
                ok(core.getStore().getCoins(a.getName())==aCoins+75000&&core.getStore().getCaseKeys(a.getName(),"legendary")==aKeys+1,"top contributor earns reward and key");
                ok(core.getStore().getCoins("OrbEnemy")==bCoins+45000,"offline real contributor earns second-place reward");
                ok(core.getStore().getCoins("OfflineThree")==2500+26250&&core.getStore().getCoins("OfflineFour")==2500+9000,"third and fourth synthetic contributions earn scaled rewards");
                ok(core.getStore().getCoins("TinyDamage")==2500,"damage below threshold is not rewarded");
                events.handleBossDeath(boss,a);
                ok(core.getStore().getCoins(a.getName())==aCoins+75000,"duplicate death callback cannot pay twice");
                events.endEvent(ev.instanceId,false);
                failedBoss();
            });
        });
    }
    private void failedBoss(){
        BossEventManager events=core.getBossEvents();
        BossEventManager.ActiveBossEvent ev=events.spawnBossEvent("grinch",new Location(world,422,65,422));
        ok(ev!=null,"second fixture boss spawned after cleanup");
        LivingEntity boss=(LivingEntity)Bukkit.getEntity(ev.bossUuid);
        boss.setNoDamageTicks(0);boss.damage(200,a);
        double coins=core.getStore().getCoins(a.getName());
        withBlockedSave(()->{boss.setHealth(5);boss.setNoDamageTicks(0);boss.damage(10000,a);});
        later(5,()->{
            ok(ev.bossDefeated&&core.getStore().getCoins(a.getName())==coins,"failed boss payout leaves local balances unchanged");
            events.handleBossDeath(boss,a);
            ok(core.getStore().getCoins(a.getName())==coins,"unpaid boss cannot silently pay again after failure");
            events.endEvent(ev.instanceId,false);finish();
        });
    }
    private void restartChecks(){
        DataStore store=core.getStore();
        DataStore.Clan red=store.getClanOf(a.getName()), blue=store.getClanOf(b.getName());
        ok(red!=null&&blue!=null&&red.allies.contains(blue.id)&&blue.allies.contains(red.id),"alliance survives restart");
        ok(store.isTeammatePvpAllowed(a.getName(),b.getName()),"mutual PvP consent survives restart");
        ok(yaml().getInt("winterTree.total")==31,"tree progress survives restart");
        ok("snow".equals(yaml().getString("cosmetics."+a.getUniqueId()+".trail")),"cosmetic selection survives restart");
        ok(yaml().getInt("utility.mining."+a.getUniqueId()+".total")==1,"buffered mining statistics survive clean restart");
        double before=store.getCoins(a.getName());a.performCommand("tree claim");
        ok(store.getCoins(a.getName())==before,"claimed tree milestone remains claimed after restart");
        later(20,()->{
            ok(displays()==14,"selected deer model returns after restart");
            a.performCommand("pet off");later(10,()->{ok(displays()==0,"restored pet can be switched off");finish();});
        });
    }
    private YamlConfiguration yaml(){try{Field f=DataStore.class.getDeclaredField("yml");f.setAccessible(true);return (YamlConfiguration)f.get(core.getStore());}catch(Exception e){throw new RuntimeException(e);}}
    private void withBlockedSave(Runnable action){
        Path data=core.getDataFolder().toPath().resolve("data.yml"),backup=data.resolveSibling("test-data-saved.yml");
        try {Files.move(data,backup);Files.createSymbolicLink(data,backup.getFileName());action.run();}
        catch(Exception e){throw new RuntimeException(e);}
        finally {try{if(Files.isSymbolicLink(data))Files.delete(data);if(Files.exists(backup))Files.move(backup,data);}catch(Exception e){throw new RuntimeException(e);}}
    }
}
