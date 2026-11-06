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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
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
    private boolean running, done, cancelHit, cancelMine,watchChest,watchVote;
    private int checks, chestAttempts, deniedChestAttempts,voteTeleports;
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
    @EventHandler(priority=EventPriority.MONITOR) public void observeChest(PlayerInteractEvent e){
        if(watchChest&&e.getPlayer().equals(b)&&e.getClickedBlock()!=null&&e.getClickedBlock().getType()==Material.CHEST
                &&e.getAction()==org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK){
            chestAttempts++;if(e.useInteractedBlock()==org.bukkit.event.Event.Result.DENY)deniedChestAttempts++;
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void observeVoteTeleport(PlayerTeleportEvent event){
        if(watchVote&&(event.getPlayer().equals(a)||event.getPlayer().equals(b)))voteTeleports++;
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
        gameVoteChecks(()->claimChecks(()->{
            a.teleport(new Location(world,418,75,418));b.teleport(new Location(world,420,75,418));
            a.setAllowFlight(true);a.setFlying(true);b.setAllowFlight(true);b.setFlying(true);
            later(20,()->utility());
        }));
    }
    private Object voteManager(){try{Field f=KingGriefCore.class.getDeclaredField("gameVotes");f.setAccessible(true);return f.get(core);}catch(Exception e){throw new RuntimeException(e);}}
    private Object voteField(String name){try{Object manager=voteManager();Field f=manager.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(manager);}catch(Exception e){throw new RuntimeException(e);}}
    private void expireVote(){try{Object manager=voteManager();Field f=manager.getClass().getDeclaredField("deadline");f.setAccessible(true);f.setLong(manager,System.nanoTime()-1);}catch(Exception e){throw new RuntimeException(e);}}
    private void gameVoteChecks(Runnable next){
        double beforeA=core.getStore().getCoins(a.getName()),beforeB=core.getStore().getCoins(b.getName());
        String previousRank=core.getStore().getSavedRank(a.getName());
        core.getConfig().set("enable-builtin-auth",true);core.getStore().setSavedRank(a.getName(),"owner");core.getStore().setAuthenticated(a,false);
        a.performCommand("gamevote start 60");a.performCommand("kgbackup create");
        ok(((Long)voteField("deadline"))==0L&&!Files.exists(core.getDataFolder().toPath().resolve("backups")),"unauthenticated owner cannot start a vote or backup");
        core.getStore().setSavedRank(a.getName(),previousRank);core.getConfig().set("enable-builtin-auth",false);
        watchVote=true;a.performCommand("gamevote start 60");
        ok(((Long)voteField("deadline"))==0L,"ordinary player cannot start a game vote");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"gamevote start 1");
        ok(((Long)voteField("deadline"))==0L,"invalid vote duration is rejected");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"gamevote start 60");
        a.performCommand("gamevote parkour");a.performCommand("gamevote duel");b.performCommand("gamevote duel");
        java.util.Map<?,?> votes=(java.util.Map<?,?>)voteField("votes");
        ok(votes.size()==2&&votes.values().stream().allMatch(v->"DUEL".equals(v.toString())),"each player has one changeable vote");
        expireVote();
        later(25,()->{
            ok(((Long)voteField("deadline"))==0L&&((java.util.Map<?,?>)voteField("votes")).isEmpty()
                    &&String.valueOf(voteField("lastResult")).startsWith("Дуэли"),"timer closes the vote and announces the selected activity");
            ok(core.getStore().getCoins(a.getName())==beforeA&&core.getStore().getCoins(b.getName())==beforeB&&voteTeleports==0,
                    "game vote neither charges nor teleports participants");
            watchVote=false;
            b.performCommand("gamevote parkour");
            ok(((java.util.Map<?,?>)voteField("votes")).isEmpty(),"votes after closing are rejected");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"gamevote start 60");
            a.performCommand("gamevote parkour");b.performCommand("gamevote duel");expireVote();a.performCommand("gamevote status");
            ok(String.valueOf(voteField("lastResult")).startsWith("ничья"),"a tie is announced without selecting a winner");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"gamevote start 60");
            a.performCommand("gamevote memory");b.performCommand("gamevote memory");expireVote();a.performCommand("gamevote status");
            ok(String.valueOf(voteField("lastResult")).startsWith("Ледяная память")&&core.getStore().getCoins(a.getName())==beforeA,
                    "the new memory option can win without charging the voters");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"gamevote start 60");
            a.performCommand("gamevote memory");Object memory=((java.util.Map<?,?>)voteField("votes")).get(a.getUniqueId());
            a.performCommand("gamevote parkour");b.performCommand("gamevote duel");
            @SuppressWarnings({"unchecked","rawtypes"}) java.util.Map rawVotes=(java.util.Map)voteField("votes");
            rawVotes.put(java.util.UUID.randomUUID(),memory);expireVote();a.performCommand("gamevote status");
            ok(String.valueOf(voteField("lastResult")).startsWith("ничья")&&String.valueOf(voteField("lastResult")).contains("1:1:1"),
                    "three equally popular activities produce no winner");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"gamevote start 60");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"gamevote cancel");
            ok(((Long)voteField("deadline"))==0L&&((java.util.Map<?,?>)voteField("votes")).isEmpty(),"administrator can cancel the poll safely");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"gamevote start 60");a.performCommand("gamevote parkour");
            next.run();
        });
    }
    private void claimChecks(Runnable next){
        // Claim ownership is meaningful in survival: the active spawn intentionally
        // denies physical storage even if a player creates a claim inside it.
        World arena=world;
        for(int x=74;x<=77;x++)for(int z=9;z<=12;z++){
            arena.getBlockAt(x,93,z).setType(Material.STONE,false);
            for(int y=94;y<=96;y++)arena.getBlockAt(x,y,z).setType(Material.AIR,false);
        }
        org.bukkit.block.Block first=arena.getBlockAt(75,94,12),second=arena.getBlockAt(76,94,12);
        first.setType(Material.CHEST,false);second.setType(Material.CHEST,false);
        org.bukkit.block.data.type.Chest left=(org.bukkit.block.data.type.Chest)first.getBlockData();
        org.bukkit.block.data.type.Chest right=(org.bukkit.block.data.type.Chest)second.getBlockData();
        left.setFacing(org.bukkit.block.BlockFace.NORTH);left.setType(org.bukkit.block.data.type.Chest.Type.LEFT);
        right.setFacing(org.bukkit.block.BlockFace.NORTH);right.setType(org.bukkit.block.data.type.Chest.Type.RIGHT);
        first.setBlockData(left,false);second.setBlockData(right,false);
        org.bukkit.inventory.Inventory combined=((org.bukkit.block.Chest)first.getState()).getInventory();
        ok(combined.getSize()==54,"double-chest fixture is a real combined inventory");
        combined.setItem(0,new ItemStack(Material.DIAMOND));
        DataStore.Claim claim=core.getStore().createClaim(a.getName(),first.getLocation(),0);
        ok(claim!=null&&core.getStore().getClaimAt(second.getLocation())==null,"only one chest half is in the built-in claim");
        a.teleport(new Location(arena,75.5,94,10.5));b.teleport(new Location(arena,76.5,94,10.5));
        watchChest=true;
        later(15,()->{
            getLogger().info("KG-BATCH REQUEST BLOCK B 76 94 12");
            later(25,()->{
                ok(chestAttempts>0&&deniedChestAttempts>0&&b.getOpenInventory().getTopInventory().getType()!=InventoryType.CHEST,
                        "real second client cannot open the outside half of a protected double chest");
                ok(yaml().getStringList("claimAudit."+claim.id+".entries").size()==1,"blocked chest attempt is logged once");
                getLogger().info("KG-BATCH REQUEST BLOCK A 76 94 12");
                later(25,()->{
                    ok(a.getOpenInventory().getTopInventory().getType()==InventoryType.CHEST
                            &&a.getOpenInventory().getTopInventory().contains(Material.DIAMOND),"real owner can still open and see the combined chest");
                    a.closeInventory();claimRoleNetwork(claim,second,next);
                });
            });
        });
    }
    private void claimRoleNetwork(DataStore.Claim claim,org.bukkit.block.Block second,Runnable next){
        DataStore store=core.getStore();World arena=second.getWorld();
        a.teleport(new Location(arena,75.5,95,12.5));b.teleport(new Location(arena,75.7,95,12.5));
        b.performCommand("rg role OrbEnemy builder");
        ok(!claim.canAccess(b.getName())&&!claim.canOpenContainers(b.getName()),"non-owner cannot grant a claim role");
        withBlockedSave(()->a.performCommand("rg role OrbEnemy container"));
        ok(!claim.canOpenContainers(b.getName())&&yaml().getStringList("claims."+claim.id+".containerMembers").isEmpty(),
                "failed role grant leaves live and stored access unchanged");
        a.performCommand("rg role OrbEnemy container");
        ok(claim.canOpenContainers(b.getName())&&!claim.canAccess(b.getName()),"container role grants contents access but not build permission");
        b.teleport(new Location(arena,76.5,94,10.5));int deniedBefore=deniedChestAttempts,attemptsBefore=chestAttempts;
        later(10,()->{
        getLogger().info("KG-BATCH REQUEST BLOCK B 76 94 12");
        later(25,()->{
            ok(chestAttempts>attemptsBefore&&deniedChestAttempts==deniedBefore
                    &&b.getOpenInventory().getTopInventory().getType()==InventoryType.CHEST,"container role opens the outside half of a protected double chest");
            InventoryClickEvent allowed=new InventoryClickEvent(b.getOpenInventory(),InventoryType.SlotType.CONTAINER,0,ClickType.LEFT,InventoryAction.PICKUP_ALL);
            Bukkit.getPluginManager().callEvent(allowed);
            ok(!allowed.isCancelled(),"container role may interact with the open chest inventory");
            withBlockedSave(()->a.performCommand("rg remove OrbEnemy"));
            ok(claim.canOpenContainers(b.getName())&&b.getOpenInventory().getTopInventory().getType()==InventoryType.CHEST,
                    "failed revocation preserves the open container and role");
            a.performCommand("rg remove OrbEnemy");
            InventoryClickEvent denied=new InventoryClickEvent(b.getOpenInventory(),InventoryType.SlotType.CONTAINER,0,ClickType.LEFT,InventoryAction.PICKUP_ALL);
            Bukkit.getPluginManager().callEvent(denied);
            ok(denied.isCancelled(),"clicks in an open chest are denied immediately after role revocation");
            ItemStack cursor=new ItemStack(Material.STONE);
            org.bukkit.event.inventory.InventoryDragEvent drag=new org.bukkit.event.inventory.InventoryDragEvent(
                    b.getOpenInventory(),null,cursor,false,java.util.Collections.singletonMap(1,cursor));
            Bukkit.getPluginManager().callEvent(drag);
            ok(drag.isCancelled(),"dragging into an open chest is denied immediately after revocation");
            later(2,()->{
                ok(b.getOpenInventory().getTopInventory().getType()!=InventoryType.CHEST,"revoked player has the physical chest closed");
                a.performCommand("rg role OrbEnemy container");
                getLogger().info("KG-BATCH REQUEST BLOCK B 76 94 12");
                later(25,()->{
                    ok(b.getOpenInventory().getTopInventory().getType()==InventoryType.CHEST,"regaining the role allows the chest to open again");
                    b.closeInventory();Inventory unrelated=Bukkit.createInventory(null,9);unrelated.setItem(0,new ItemStack(Material.EMERALD));b.openInventory(unrelated);
                    a.performCommand("rg remove OrbEnemy");
                    later(2,()->{
                        ok(b.getOpenInventory().getTopInventory()==unrelated&&unrelated.getItem(0).getType()==Material.EMERALD,
                                "revocation does not close or change an unrelated virtual inventory");
                        a.performCommand("rg role OrbEnemy container");b.closeInventory();claimCrossBoundary(claim,second,next);
                    });
                });
            });
        });
        });
    }
    private void claimCrossBoundary(DataStore.Claim claim,org.bukkit.block.Block second,Runnable next){
            DataStore store=core.getStore();World arena=second.getWorld();
            DataStore.Claim other=store.createClaim("OtherOwner",second.getLocation(),0);
            int beforeDenied=deniedChestAttempts;
            getLogger().info("KG-BATCH REQUEST BLOCK B 76 94 12");
            later(25,()->{
                ok(deniedChestAttempts>beforeDenied&&b.getOpenInventory().getTopInventory().getType()!=InventoryType.CHEST,
                        "a second claim can deny a double chest even when the first grants container access");
                ok(store.setClaimRole(other,b.getName(),DataStore.ClaimRole.CONTAINER)==DataStore.ClaimRoleResult.CHANGED,
                        "second claim owner can independently grant container access");
                int deniedAgain=deniedChestAttempts,attemptedAgain=chestAttempts;
                getLogger().info("KG-BATCH REQUEST BLOCK B 76 94 12");
                later(25,()->{
                    ok(chestAttempts>attemptedAgain&&deniedChestAttempts==deniedAgain
                            &&b.getOpenInventory().getTopInventory().getType()==InventoryType.CHEST,"double chest opens only after both claims allow it");
                    b.closeInventory();store.removeClaim(other);
                    a.performCommand("rg add OrbEnemy");
                    ok(claim.canAccess(b.getName())&&!claim.containerMembers.contains(b.getName().toLowerCase(java.util.Locale.ROOT)),"legacy add promotes a container member to builder");
                    a.performCommand("rg role OrbEnemy container");
                    withBlockedSave(()->a.performCommand("rg remove OrbEnemy"));
                    ok(claim.canOpenContainers(b.getName())&&!claim.canAccess(b.getName()),"failed role removal restores the live container role");
                    a.performCommand("rg remove OrbEnemy");
                    ok(!claim.canOpenContainers(b.getName())&&!claim.canAccess(b.getName()),"legacy remove revokes the container role");
                    for(int i=0;i<128;i++)claim.containerMembers.add("test"+i);
                    ok(store.setClaimRole(claim,"Overflow",DataStore.ClaimRole.CONTAINER)==DataStore.ClaimRoleResult.LIMIT,"new claim roles are bounded to 128 players");
                    claim.containerMembers.clear();
                    a.performCommand("rg role SavedChest container");a.performCommand("rg add SavedBuilder");
                    a.teleport(new Location(arena,75.5,95,12.5));b.teleport(new Location(arena,75.7,95,12.5));
                    ok(a.performCommand("rg roles")&&b.performCommand("rg roles"),"role list handles owner and non-owner requests");
                    ok(a.performCommand("rg log")&&b.performCommand("rg log"),"claim audit command handles owner and non-owner access");
                    watchChest=false;store.save();claimRoleBuild(next);
                });
            });
    }
    private void claimRoleBuild(Runnable next){
        DataStore store=core.getStore();org.bukkit.block.Block target=world.getBlockAt(419,65,418);target.setType(Material.STONE,false);
        DataStore.Claim claim=store.createClaim(a.getName(),new Location(world,418,65,418),3);
        a.teleport(new Location(world,418.5,75,418.5));b.teleport(new Location(world,419.5,75,418.5));
        a.performCommand("rg role OrbEnemy container");b.breakBlock(target);
        ok(target.getType()==Material.STONE,"container-only member cannot break blocks in survival");
        a.performCommand("rg role OrbEnemy builder");b.breakBlock(target);
        ok(target.getType()==Material.AIR,"builder can break an allowed block in survival");
        store.removeClaim(claim);next.run();
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
            ok(yaml().getInt("utility.mining."+a.getUniqueId()+".ores.diamond_ore")==1,"mining statistics record the exact ore type");
            ok(a.performCommand("minestats")&&a.performCommand("minestats 2")&&a.performCommand("minestats -1"),"mining statistics pages and invalid-page guard are registered");
            auction();tree();achievementOwnerCheck(()->backupChecks(()->{a.performCommand("pet penguin");later(15,()->petChecks());}));
        });
    }
    private Inventory auctionInventory(){return a.getOpenInventory().getTopInventory();}
    private void auctionClick(int slot,ClickType type){
        InventoryClickEvent event=new InventoryClickEvent(a.getOpenInventory(),InventoryType.SlotType.CONTAINER,slot,type,InventoryAction.PICKUP_ALL);
        Bukkit.getPluginManager().callEvent(event);
        ok(event.isCancelled(),"auction click is contained by the menu");
    }
    private void auction(){
        DataStore store=core.getStore();String seller="MarketTrader";
        ItemStack named=new ItemStack(Material.DIAMOND_SWORD);ItemMeta meta=named.getItemMeta();meta.setDisplayName("§bFrozen Blade");named.setItemMeta(meta);
        DataStore.AuctionLot sword=store.addAuctionLot(seller,named,200);
        DataStore.AuctionLot emerald=store.addAuctionLot(seller,new ItemStack(Material.EMERALD),25);
        DataStore.AuctionLot stone=store.addAuctionLot(seller,new ItemStack(Material.STONE),75);
        ok(sword!=null&&emerald!=null&&stone!=null,"auction fixture lots were created");
        for(int i=0;i<46;i++)if(store.addAuctionLot(seller,new ItemStack(Material.DIRT),500+i)==null)throw new AssertionError("auction pagination fixture");
        a.performCommand("ah clear");
        ok(auctionInventory().getItem(53).getType()==Material.ARROW,"unfiltered auction has a second page");
        auctionClick(53,ClickType.LEFT);
        ok(((net.kinggrief.core.GuiManager.KgMenuHolder)auctionInventory().getHolder()).page==1,"pagination opens the next page");
        a.performCommand("ah sort cheap");
        ok(auctionInventory().getItem(0).getType()==Material.EMERALD&&auctionInventory().getItem(1).getType()==Material.STONE
                &&auctionInventory().getItem(2).getType()==Material.DIAMOND_SWORD,"price sorting is deterministic and correct");
        a.performCommand("ah search Frozen");
        ok(auctionInventory().getItem(0).getType()==Material.DIAMOND_SWORD&&auctionInventory().getItem(1)==null
                &&auctionInventory().getItem(53).getType()!=Material.ARROW,"display-name search and filtered pagination work");
        a.performCommand("ah price 100");
        ok(auctionInventory().getItem(0)==null,"maximum price combines with text search");
        a.performCommand("ah price NaN");a.performCommand("ah search Frozen");
        ok(auctionInventory().getItem(0)==null,"invalid numeric filter does not overwrite the active limit");
        a.performCommand("ah price off");
        double before=store.getCoins(a.getName());
        auctionClick(0,ClickType.SHIFT_LEFT);
        ok(store.findAuctionLot(sword.id)!=null&&store.getCoins(a.getName())==before,"shift-click favorites without buying or charging");
        String path="auctionPreferences."+a.getUniqueId()+".favorites";
        ok(yaml().getIntegerList(path).contains(sword.id),"favorite listing is persisted");
        withBlockedSave(()->a.performCommand("ah favorite "+emerald.id));
        ok(!yaml().getIntegerList(path).contains(emerald.id)&&yaml().getIntegerList(path).contains(sword.id),"failed favorite save leaves old choice intact");
        b.performCommand("ah favorites");
        ok(b.getOpenInventory().getTopInventory().getItem(0)==null,"favorites are private to each player");b.closeInventory();
        a.performCommand("ah clear");a.performCommand("ah sort cheap");
        auctionClick(0,ClickType.NUMBER_KEY);
        ok(store.findAuctionLot(emerald.id)!=null&&store.getCoins(a.getName())==before,"hotbar swap cannot accidentally purchase a lot");
        DataStore.AuctionLot newer=store.addAuctionLot(seller,new ItemStack(Material.GOLD_INGOT),1);
        ok(newer!=null&&auctionInventory().getItem(0).getType()==Material.EMERALD,"open screen retains its displayed item after listings change");
        withBlockedSave(()->auctionClick(0,ClickType.LEFT));
        ok(store.findAuctionLot(emerald.id)!=null&&store.getCoins(a.getName())==before
                &&yaml().getStringList("auctionHistory.emerald_plain").isEmpty(),"failed purchase records no sale and keeps money and lot");
        auctionClick(0,ClickType.LEFT);
        ok(store.findAuctionLot(emerald.id)==null&&store.findAuctionLot(newer.id)!=null
                &&store.getCoins(a.getName())==before-25&&a.getInventory().contains(Material.EMERALD),"buy targets displayed lot ID, not newly sorted position");
        java.util.List<String> history=yaml().getStringList("auctionHistory.emerald_plain");
        ok(history.size()==1&&history.get(0).endsWith("|25.0|1"),"successful purchase atomically records price and quantity");
        ItemStack hand=a.getInventory().getItemInMainHand();a.getInventory().setItemInMainHand(new ItemStack(Material.EMERALD));
        ok(a.performCommand("ah history"),"auction price history command is registered");
        a.getInventory().setItemInMainHand(hand);a.closeInventory();
        ItemStack custom=net.kinggrief.core.ItemFactory.make(Material.EMERALD,3,"&aTest",java.util.Collections.emptyList(),false,"history_test",null,0);
        for(int i=0;i<22;i++){
            DataStore.AuctionLot lot=store.addAuctionLot(seller,custom,30+i);
            if(lot==null||store.purchaseAuctionLot(lot.id,"HistoryBuyer")==null)throw new AssertionError("history bounded fixture sale");
        }
        java.util.List<String> special=yaml().getStringList("auctionHistory.emerald_custom_history_test");
        ok(special.size()==20&&special.get(0).endsWith("|51.0|3")&&special.get(19).endsWith("|32.0|3")
                &&yaml().getStringList("auctionHistory.emerald_plain").size()==1,"custom IDs and stack sizes have bounded, separate histories");
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
        treeLaterMilestones();
    }
    private void treeLaterMilestones(){
        DataStore store=core.getStore();YamlConfiguration data=yaml();String player="players."+b.getName().toLowerCase(java.util.Locale.ROOT);
        int snow=store.getSnowflakes(b.getName()),keys=store.getCaseKeys(b.getName(),"legendary");double coins=store.getCoins(b.getName());
        Object oldTitle=data.get(player+".titles.tree_friend");
        try{
            Field f=KingGriefCore.class.getDeclaredField("winterTree");f.setAccessible(true);Object manager=f.get(core);
            Field stage=manager.getClass().getDeclaredField("visualStage");stage.setAccessible(true);
            data.set("winterTree.total",99);b.performCommand("tree donate 1");
            ok(data.getInt("winterTree.total")==100&&stage.getInt(manager)==2,"second tree milestone updates the visual stage");
            b.performCommand("tree claim");ok(store.getCoins(b.getName())==coins+15000,"eligible donor receives only the second milestone");
            data.set("winterTree.total",299);b.performCommand("tree donate 1");
            ok(data.getInt("winterTree.total")==300&&stage.getInt(manager)==3,"final tree milestone updates the visual stage");
            withBlockedSave(()->b.performCommand("tree claim"));
            ok(store.getCoins(b.getName())==coins+15000&&store.getCaseKeys(b.getName(),"legendary")==keys,
                    "failed final milestone commit does not partially pay");
            b.performCommand("tree claim");b.performCommand("tree claim");
            ok(store.getCoins(b.getName())==coins+50000&&store.getCaseKeys(b.getName(),"legendary")==keys+1
                    &&yaml().getBoolean(player+".titles.tree_friend"),"final milestone pays coins, key and title exactly once");
            // Restore the first-milestone fixture for the subsequent restart checks.
            data=yaml();data.set("winterTree.total",31);data.set("winterTree.donors."+b.getName().toLowerCase(java.util.Locale.ROOT)+".total",1);
            data.set("winterTree.donors."+b.getName().toLowerCase(java.util.Locale.ROOT)+".daily",1);
            data.set("winterTree.donors."+b.getName().toLowerCase(java.util.Locale.ROOT)+".claimed.2",null);
            data.set("winterTree.donors."+b.getName().toLowerCase(java.util.Locale.ROOT)+".claimed.3",null);
            data.set(player+".coins",coins);data.set(player+".snowflakes",snow);data.set(player+".cases.legendary",keys);
            data.set(player+".titles.tree_friend",oldTitle);stage.setInt(manager,1);store.save();
        }catch(Exception error){throw new RuntimeException(error);}
    }
    private void achievementOwnerCheck(Runnable next){
        a.performCommand("achievements");Inventory menu=a.getOpenInventory().getTopInventory();
        ok(menu.getSize()==45&&menu.getItem(10).getType()==Material.IRON_PICKAXE
                &&menu.getItem(28).getType()==Material.SPRUCE_SAPLING,"achievement menu presents primary and seasonal goals");
        b.openInventory(menu);
        InventoryClickEvent foreign=new InventoryClickEvent(b.getOpenInventory(),InventoryType.SlotType.CONTAINER,28,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        Bukkit.getPluginManager().callEvent(foreign);
        ok(foreign.isCancelled(),"another viewer cannot move an achievement menu item");
        later(2,()->{
            ok(!yaml().getBoolean("achievements."+b.getUniqueId()+".claimed.tree_1"),"another viewer cannot claim someone else's achievement menu");
            b.closeInventory();a.closeInventory();next.run();
        });
    }
    private void backupChecks(Runnable next){
        Path folder=core.getDataFolder().toPath().resolve("backups"), outside=core.getDataFolder().toPath().resolveSibling("kg-backup-outside-fixture");
        try{
            a.performCommand("kgbackup create");
            ok(!Files.exists(folder),"ordinary players cannot create Core backups");
            Files.createDirectory(outside);Files.createSymbolicLink(folder,outside);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"kgbackup create");
            try(java.util.stream.Stream<Path> files=Files.list(outside)){
                ok(Files.isSymbolicLink(folder)&&files.findAny().isEmpty(),"backup refuses a symlinked destination without changing it");
            }
            Files.delete(folder);
            withBlockedSave(()->Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"kgbackup create"));
            ok(backupArchives(folder).isEmpty(),"failed data checkpoint creates no archive");
            double atSnapshot=core.getStore().getCoins(a.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"kgbackup create");
            core.getStore().setCoins(a.getName(),atSnapshot+7);core.getStore().save();
            waitBackup(folder,atSnapshot,0,next);
        }catch(Exception error){throw new RuntimeException(error);}
    }
    private java.util.List<Path> backupArchives(Path folder) throws Exception {
        java.util.List<Path> paths=new java.util.ArrayList<Path>();
        if(!Files.exists(folder))return paths;
        try(java.nio.file.DirectoryStream<Path> stream=Files.newDirectoryStream(folder,"kgcore-*.zip")){for(Path path:stream)paths.add(path);}
        return paths;
    }
    private void waitBackup(Path folder,double atSnapshot,int attempt,Runnable next){
        later(10,()->{
            java.util.List<Path> archives;
            try{archives=backupArchives(folder);}catch(Exception error){throw new RuntimeException(error);}
            if(archives.isEmpty()&&attempt<20){waitBackup(folder,atSnapshot,attempt+1,next);return;}
            ok(archives.size()==1,"asynchronous Core backup was published exactly once");
            try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(archives.get(0).toFile())){
                byte[] bytes=zip.getInputStream(zip.getEntry("data.yml")).readAllBytes();
                String manifest=new String(zip.getInputStream(zip.getEntry("MANIFEST.txt")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                String readme=new String(zip.getInputStream(zip.getEntry("README.txt")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                String digest=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
                YamlConfiguration snapshot=new YamlConfiguration();snapshot.loadFromString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
                ok(manifest.contains(digest)&&zip.size()==3,"archive manifest matches the saved data and exact scope");
                ok(readme.contains("NOT INCLUDED")&&readme.contains("worlds")&&readme.contains("inventories"),"archive explicitly excludes worlds and inventories");
                ok(snapshot.getDouble("players."+a.getName().toLowerCase(java.util.Locale.ROOT)+".coins")==atSnapshot
                        &&core.getStore().getCoins(a.getName())==atSnapshot+7,"backup retains its point-in-time data while the live file changes");
                ok(snapshot.getIntegerList("auctionPreferences."+a.getUniqueId()+".favorites").size()==1,"backup includes saved Core auction preferences");
                try{java.util.Set<java.nio.file.attribute.PosixFilePermission> perms=Files.getPosixFilePermissions(archives.get(0));
                    ok(!perms.contains(java.nio.file.attribute.PosixFilePermission.GROUP_READ)&&!perms.contains(java.nio.file.attribute.PosixFilePermission.OTHERS_READ),"archive is private on POSIX filesystems");}
                catch(UnsupportedOperationException ignored){}
            }catch(Exception error){throw new RuntimeException(error);}
            core.getStore().setCoins(a.getName(),atSnapshot);core.getStore().save();
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"kgbackup list");
            later(10,()->{
                try(java.util.stream.Stream<Path> entries=Files.list(folder)){
                    ok(entries.noneMatch(path->path.getFileName().toString().startsWith(".kgbackup-")),"temporary backup links and partial files are cleaned up");
                }catch(Exception error){throw new RuntimeException(error);}
                next.run();
            });
        });
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
            events.endEvent(ev.instanceId,false);achievementChecks();
        });
    }
    private void achievementChecks(){
        DataStore store=core.getStore();String path="achievements."+a.getUniqueId()+".claimed.";
        core.getConfig().set("enable-builtin-auth",true);store.setAuthenticated(a,false);
        a.performCommand("achievements claim");
        ok(!yaml().getBoolean(path+"tree_1"),"unauthenticated players cannot claim achievements");
        core.getConfig().set("enable-builtin-auth",false);
        a.performCommand("achievements");
        InventoryClickEvent swap=new InventoryClickEvent(a.getOpenInventory(),InventoryType.SlotType.CONTAINER,28,ClickType.NUMBER_KEY,InventoryAction.HOTBAR_SWAP,0);
        Bukkit.getPluginManager().callEvent(swap);
        ok(swap.isCancelled()&&!yaml().getBoolean(path+"tree_1"),"hotbar swapping cannot claim or take achievement icons");
        InventoryClickEvent click=new InventoryClickEvent(a.getOpenInventory(),InventoryType.SlotType.CONTAINER,28,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        Bukkit.getPluginManager().callEvent(click);ok(click.isCancelled(),"normal achievement menu clicks cannot move icons");
        later(2,()->{
            ok(yaml().getBoolean(path+"tree_1")&&!yaml().getBoolean(path+"tree_10")&&!yaml().getBoolean(path+"boss"),"clicking a seasonal badge claims only that badge");
            double coins=store.getCoins(a.getName());int snow=store.getSnowflakes(a.getName());
            a.performCommand("achievements claim");
            ok(yaml().getBoolean(path+"tree_10")&&yaml().getBoolean(path+"boss")
                    &&!yaml().getBoolean(path+"ore_25")&&!store.hasUnlockedTitle(a.getName(),"king_achiever"),"claim-all skips unfinished goals and seasonal badges do not grant the core title");
            String ore="utility.mining."+a.getUniqueId()+".total",record="parkour.records."+a.getUniqueId()+".all";
            int originalOre=yaml().getInt(ore);Object originalRecord=yaml().get(record);
            yaml().set(ore,250);yaml().set(record,42_000L);
            withBlockedSave(()->a.performCommand("achievements claim"));
            ok(!yaml().getBoolean(path+"ore_25")&&!yaml().getBoolean(path+"parkour")
                    &&!store.hasUnlockedTitle(a.getName(),"king_achiever")&&store.getCoins(a.getName())==coins,
                    "failed achievement save rolls back every badge and title");
            a.performCommand("achievements claim");a.performCommand("achievements claim");
            ok(yaml().getBoolean(path+"ore_25")&&yaml().getBoolean(path+"ore_250")&&yaml().getBoolean(path+"parkour")
                    &&store.hasUnlockedTitle(a.getName(),"king_achiever")&&store.getCoins(a.getName())==coins
                    &&store.getSnowflakes(a.getName())==snow,"eligible achievements and cosmetic title are durable and idempotent");
            yaml().set(ore,originalOre);yaml().set(record,originalRecord);store.save();
            a.performCommand("titles");Inventory titles=a.getOpenInventory().getTopInventory();
            ok(titles.getItem(28)!=null&&titles.getItem(28).getType()==Material.NAME_TAG,"achievement title appears unlocked in the titles menu");
            InventoryClickEvent equip=new InventoryClickEvent(a.getOpenInventory(),InventoryType.SlotType.CONTAINER,28,ClickType.LEFT,InventoryAction.PICKUP_ALL);
            Bukkit.getPluginManager().callEvent(equip);
            ok(equip.isCancelled()&&store.getActiveTitleDisplay(a.getName()).contains("ИСКАТЕЛЬ"),"achievement title can be equipped without taking the icon");
            a.closeInventory();finish();
        });
    }
    private void restartChecks(){
        DataStore store=core.getStore();
        World treeWorld=core.getSpawnEngine().getPreparedSpawnArrival().getWorld();
        a.teleport(new Location(treeWorld,0.5,100,0.5));
        ok(((Long)voteField("deadline"))==0L&&((java.util.Map<?,?>)voteField("votes")).isEmpty(),"unfinished vote does not survive a server restart");
        DataStore.Clan red=store.getClanOf(a.getName()), blue=store.getClanOf(b.getName());
        ok(red!=null&&blue!=null&&red.allies.contains(blue.id)&&blue.allies.contains(red.id),"alliance survives restart");
        ok(store.isTeammatePvpAllowed(a.getName(),b.getName()),"mutual PvP consent survives restart");
        ok(yaml().getInt("winterTree.total")==31,"tree progress survives restart");
        ok("snow".equals(yaml().getString("cosmetics."+a.getUniqueId()+".trail")),"cosmetic selection survives restart");
        ok(yaml().getInt("utility.mining."+a.getUniqueId()+".total")==1,"buffered mining statistics survive clean restart");
        DataStore.Claim protectedChest=store.getClaimAt(new Location(world,75,94,12));
        ok(protectedChest!=null&&yaml().getStringList("claimAudit."+protectedChest.id+".entries").size()==1,"blocked chest attempt survives restart");
        ok(protectedChest.canOpenContainers("SavedChest")&&!protectedChest.canAccess("SavedChest")
                &&protectedChest.canAccess("SavedBuilder"),"container and legacy builder roles survive restart separately");
        store.removeClaim(protectedChest);
        ok(yaml().getStringList("claimAudit."+protectedChest.id+".entries").isEmpty(),"removing a claim removes only its own audit records");
        try{ok(backupArchives(core.getDataFolder().toPath().resolve("backups")).size()==1,"private Core backup survives restart");}
        catch(Exception error){throw new RuntimeException(error);}
        java.util.List<Integer> favorites=yaml().getIntegerList("auctionPreferences."+a.getUniqueId()+".favorites");
        ok(favorites.size()==1&&store.findAuctionLot(favorites.get(0))!=null,"favorite lot survives restart");
        ok(yaml().getStringList("auctionHistory.emerald_plain").size()==1,"auction sale history survives restart");
        ok(yaml().getBoolean("achievements."+a.getUniqueId()+".claimed.ore_250")
                &&yaml().getBoolean("achievements."+a.getUniqueId()+".claimed.parkour")
                &&store.hasUnlockedTitle(a.getName(),"king_achiever"),"achievement badges and cosmetic title survive restart");
        a.performCommand("achievements");
        ok(a.getOpenInventory().getTopInventory().getItem(34).getType()==Material.NAME_TAG,"achievement menu restores unlocked title state");
        a.closeInventory();
        a.performCommand("ah favorites");
        ok(a.getOpenInventory().getTopInventory().getItem(0)!=null
                &&a.getOpenInventory().getTopInventory().getItem(0).getType()==Material.DIAMOND_SWORD
                &&a.getOpenInventory().getTopInventory().getItem(1)==null,"restored favorites filter shows only the saved listing");
        a.closeInventory();
        double before=store.getCoins(a.getName());a.performCommand("tree claim");
        ok(store.getCoins(a.getName())==before,"claimed tree milestone remains claimed after restart");
        ok("deer".equals(yaml().getString("cosmeticPets."+a.getUniqueId())),"deer selection survives restart");
        // Pet spawning is rate-limited and can retry five seconds after a chunk
        // or entity spawn failure; don't assume it always settles in 20 ticks.
        awaitRestoredPet(0);
    }
    private void awaitRestoredPet(int attempts){
        long count=displays();
        if(count==14){
            ok(true,"selected deer model returns after restart");
            // Keep the real protocol player near the tree long enough to span
            // several one-second particle ticks after the cross-world teleport.
            // Otherwise an immediately restored pet races the first tick and
            // the network assertion intermittently observes zero packets.
            a.performCommand("pet off");later(70,()->{ok(displays()==0,"restored pet can be switched off");finish();});
        }else if(attempts<30){
            later(5,()->awaitRestoredPet(attempts+1));
        }else{
            throw new AssertionError("selected deer model returns after restart: parts="+count+", world="+a.getWorld().getName()
                    +", loc="+a.getLocation()+", authenticated="+core.getStore().isAuthenticated(a)
                    +", parkour="+core.getStore().getParkourCp(a)+", dead="+a.isDead()+", gameMode="+a.getGameMode());
        }
    }
    private YamlConfiguration yaml(){try{Field f=DataStore.class.getDeclaredField("yml");f.setAccessible(true);return (YamlConfiguration)f.get(core.getStore());}catch(Exception e){throw new RuntimeException(e);}}
    private void withBlockedSave(Runnable action){
        Path data=core.getDataFolder().toPath().resolve("data.yml"),backup=data.resolveSibling("test-data-saved.yml");
        try {Files.move(data,backup);Files.createSymbolicLink(data,backup.getFileName());action.run();}
        catch(Exception e){throw new RuntimeException(e);}
        finally {try{if(Files.isSymbolicLink(data))Files.delete(data);if(Files.exists(backup))Files.move(backup,data);}catch(Exception e){throw new RuntimeException(e);}}
    }
}
