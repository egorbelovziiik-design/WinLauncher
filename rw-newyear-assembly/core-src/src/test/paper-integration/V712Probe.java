package net.kinggrief.probe;

import net.kinggrief.core.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Actual Paper, protocol clicks, late event cancellations, escrow failure and SIGKILL recovery. */
public final class V712Probe extends JavaPlugin implements Listener {
    private KingGriefCore core; private ExpeditionManager exp; private Player a,b,c; private World world;
    private Block ore,other; private int checks,errors,phase; private boolean started,finished;
    private boolean denyBreak,denyPlace,denyPiston,denyFish,denyMove,denyMonster,denyTeleport,denyEscape;
    private static final Instant MONDAY=Instant.parse("2026-01-05T12:00:00Z");
    @Override public void onEnable() { phase=getConfig().getInt("phase",1); Bukkit.getPluginManager().registerEvents(this,this); }
    @EventHandler public void join(PlayerJoinEvent e) {
        String name=e.getPlayer().getName();
        if(name.equals("ExpOne")) a=e.getPlayer(); else if(name.equals("ExpTwo")) b=e.getPlayer(); else if(name.equals("ExpThree")) c=e.getPlayer();
        if(!started && a!=null && b!=null && c!=null) { started=true; later(70,this::begin); }
    }
    @EventHandler(priority=EventPriority.MONITOR) public void lateBreak(BlockBreakEvent e) { if(denyBreak && e.getPlayer().equals(a)) { denyBreak=false; e.setCancelled(true); } }
    @EventHandler(priority=EventPriority.MONITOR) public void latePlace(BlockPlaceEvent e) { if(denyPlace && e.getPlayer().equals(a)) { denyPlace=false; e.setCancelled(true); } }
    @EventHandler(priority=EventPriority.MONITOR) public void latePiston(BlockPistonExtendEvent e) { if(denyPiston) { denyPiston=false; e.setCancelled(true); } }
    @EventHandler(priority=EventPriority.MONITOR) public void lateFish(PlayerFishEvent e) { if(denyFish && e.getPlayer().equals(a)) { denyFish=false; e.setCancelled(true); } }
    @EventHandler(priority=EventPriority.MONITOR) public void lateMove(PlayerMoveEvent e) { if(denyMove && e.getPlayer().equals(a) && !(e instanceof PlayerTeleportEvent)) { denyMove=false; e.setCancelled(true); } }
    @EventHandler(priority=EventPriority.MONITOR) public void lateMonster(EntityDeathEvent e) { if(denyMonster && e.getEntity() instanceof Monster) { denyMonster=false; e.setReviveHealth(10); e.setCancelled(true); } }
    @EventHandler(priority=EventPriority.HIGHEST) public void cancelledTeleport(PlayerTeleportEvent e) {
        if(denyTeleport && e.getPlayer().equals(b) && e.getTo()!=null && core.getSpawnEngine().isInsideAnyKabinka(e.getTo())) { denyTeleport=false; e.setCancelled(true); }
        if(denyEscape && e.getPlayer().equals(a) && e.getFrom()!=null && core.getSpawnEngine().isInsideAnyKabinka(e.getFrom())) { denyEscape=false; e.setCancelled(true); }
    }
    private interface Checked { void run() throws Exception; }
    private interface Condition { boolean test() throws Exception; }
    private void later(long ticks,Checked r) { Bukkit.getScheduler().runTaskLater(this,()->{ if(finished)return; try { r.run(); } catch(Throwable t) { fail(t); } },ticks); }
    private void waitFor(Condition c,Checked done,int attempts) {
        later(5,()->{ if(c.test()) done.run(); else if(attempts>0) waitFor(c,done,attempts-1); else throw new AssertionError("timed out waiting for async duel/respawn"); });
    }
    private void ok(boolean condition,String label) { checks++; if(!condition) { errors++; getLogger().severe("KG-V712 FAIL: "+label); } }
    private void fail(Throwable error) { errors++; getLogger().severe("KG-V712 EXCEPTION: "+error); error.printStackTrace(); finish(); }
    private void finish() { if(finished)return; finished=true; getLogger().info("KG-V712 RESULT "+(errors==0?"PASS":"FAIL")+" checks="+checks+" errors="+errors); }
    private Object field(Object obj,String name)throws Exception { Field f=obj.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(obj); }
    private void setField(Object obj,String name,Object value)throws Exception { Field f=obj.getClass().getDeclaredField(name); f.setAccessible(true); f.set(obj,value); }
    private void clock()throws Exception { setField(exp,"clock",Clock.fixed(MONDAY,ZoneOffset.UTC)); }
    private Object snap()throws Exception { Method m=exp.getClass().getDeclaredMethod("snapshot",Player.class);m.setAccessible(true);return m.invoke(exp,a); }
    private int progress(int i)throws Exception { return ((int[])field(snap(),"progress"))[i]; }
    private boolean claimed(int i)throws Exception { return ((boolean[])field(snap(),"claimed"))[i]; }
    private boolean flag(String name)throws Exception { return (Boolean)field(snap(),name); }
    private int weekly(String name)throws Exception { return (Integer)field(snap(),name); }
    private boolean marked(Block block)throws Exception { Method m=exp.getClass().getDeclaredMethod("markedOre",Block.class);m.setAccessible(true);return (Boolean)m.invoke(exp,block); }
    private Object duels()throws Exception { return field(core,"duels"); }
    private boolean preparing()throws Exception { return !((Map<?,?>)field(duels(),"preparing")).isEmpty(); }
    private boolean pending()throws Exception { return !((Map<?,?>)field(duels(),"pending")).isEmpty(); }
    private DataStore.ActiveWagerDuel active() { return core.getStore().getActiveWagerDuel(a.getName()); }
    private YamlConfiguration yml()throws Exception { return (YamlConfiguration)field(core.getStore(),"yml"); }
    @SuppressWarnings({"rawtypes","unchecked"}) private void advance(String name,int amount)throws Exception {
        Class task=Class.forName("net.kinggrief.core.ExpeditionManager$Task"); Object id=Enum.valueOf(task,name);
        Method m=exp.getClass().getDeclaredMethod("advance",Player.class,task,int.class);m.setAccessible(true);m.invoke(exp,a,id,amount);
    }
    private void begin() throws Exception {
        core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore"); exp=core.getExpeditions(); clock();
        if(phase==2) { verifyRestart(); return; }
        world=Bukkit.getWorlds().get(0); world.getChunkAt(10,10).load();
        for(int x=160;x<=168;x++) for(int z=160;z<=164;z++) world.getBlockAt(x,69,z).setType(Material.STONE,false);
        a.teleport(new Location(world,160.5,70,160.5));b.teleport(new Location(world,162.5,70,160.5));c.teleport(new Location(world,164.5,70,160.5));
        for(Player p:new Player[]{a,b,c}) { p.setGameMode(GameMode.SURVIVAL);p.getInventory().clear();p.setHealth(20); }
        a.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_PICKAXE));
        ore=world.getBlockAt(161,70,161);other=world.getBlockAt(162,70,161);
        core.getStore().setCoins(a.getName(),1000);core.getStore().setCoins(b.getName(),10000);core.getStore().setCoins(c.getName(),2500);
        core.getStore().addSnowflakes(a.getName(),-core.getStore().getSnowflakes(a.getName()));
        YamlConfiguration data=yml(); String root="expeditions."+a.getUniqueId(); data.set(root,null);
        data.set(root+".day.date","2026-01-05");data.set(root+".week.date","2026-01-05");
        data.set(root+".day.progress.ore",31);data.set(root+".day.progress.harvest",20);data.set(root+".day.claimed.harvest",true);data.set(root+".week.claims",4);
        core.getStore().save();
        ok(progress(0)==31 && progress(5)==31 && claimed(1) && weekly("weekly")==4,"legacy progress, claimed flags, weekly counter preserved; expert starts from legacy progress");
        ore.setType(Material.DIAMOND_ORE,false);ok(a.breakBlock(ore),"real natural ore break accepted");
        later(4,this::afterLegacyBreak);
    }
    private void afterLegacyBreak() throws Exception {
        ok(progress(0)==32 && progress(5)==32,"first post-upgrade block advances expert exactly once");
        denyPlace=true; place(ore); ok(!denyPlace,"late place cancellation executed");
        later(4,()->{
            ok(!marked(ore),"late-cancelled placement restores original ore marker");
            ore.setType(Material.DIAMOND_ORE,false);a.breakBlock(ore);
            later(4,()->{
                ok(progress(5)==33,"natural ore remains eligible after cancelled placement");
                place(ore);a.breakBlock(ore);
                later(4,()->{
                    ok(progress(5)==33 && !marked(ore),"same-tick placed ore is excluded and marker clears after actual break");
                    place(ore);denyBreak=true;a.breakBlock(ore);
                    later(4,()->{
                        ok(!denyBreak && ore.getType()==Material.DIAMOND_ORE && marked(ore) && progress(5)==33,"late-cancelled break retains placed marker and gives no progress");
                        a.breakBlock(ore);
                        later(4,()->{
                            ok(progress(5)==33 && !marked(ore),"accepted break of marked ore remains ineligible");
                            ore.setType(Material.DIAMOND_ORE,false);a.breakBlock(ore);
                            later(4,()->{ ok(progress(5)==34,"natural ore after marked break counts"); pistonCancelled(); });
                        });
                    });
                });
            });
        });
    }
    private void place(Block block) {
        BlockState previous=block.getState();block.setType(Material.DIAMOND_ORE,false);
        BlockPlaceEvent event=new BlockPlaceEvent(block,previous,block.getRelative(BlockFace.DOWN),new ItemStack(Material.DIAMOND_ORE),a,true,EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(event);
        if(event.isCancelled()) previous.update(true,false);
    }
    private void pistonCancelled() throws Exception {
        other.setType(Material.AIR,false);place(ore);
        Block piston=world.getBlockAt(159,70,161);piston.setType(Material.PISTON,false);
        denyPiston=true; BlockPistonExtendEvent event=new BlockPistonExtendEvent(piston,Arrays.asList(ore),BlockFace.EAST);Bukkit.getPluginManager().callEvent(event);
        later(4,()->{
            ok(!denyPiston && marked(ore) && !marked(other),"late-cancelled piston retains source mark and rolls back destination");
            a.breakBlock(ore);other.setType(Material.DIAMOND_ORE,false);a.breakBlock(other);
            later(4,()->{ ok(progress(5)==35,"cancelled piston does not mark unrelated natural destination"); pistonMoved(); });
        });
    }
    private void pistonMoved() throws Exception {
        place(ore);Block piston=world.getBlockAt(159,70,161);
        BlockPistonExtendEvent event=new BlockPistonExtendEvent(piston,Arrays.asList(ore),BlockFace.EAST);Bukkit.getPluginManager().callEvent(event);
        ore.setType(Material.AIR,false);other.setType(Material.DIAMOND_ORE,false);
        later(4,()->{
            ok(!marked(ore) && marked(other),"successful piston transfers placed marker");
            a.breakBlock(other);
            later(4,()->{ ok(progress(5)==35 && !marked(other),"piston-moved player ore cannot be farmed"); realPiston(); });
        });
    }
    private void realPiston() throws Exception {
        Block piston=world.getBlockAt(168,70,161),source=world.getBlockAt(169,70,161),dest=world.getBlockAt(170,70,161);
        piston.setType(Material.PISTON,false);org.bukkit.block.data.Directional direction=(org.bukkit.block.data.Directional)piston.getBlockData();
        direction.setFacing(BlockFace.EAST);piston.setBlockData(direction,false);dest.setType(Material.AIR,false);place(source);
        piston.getRelative(BlockFace.UP).setType(Material.REDSTONE_BLOCK,true);
        later(15,()->{
            ok(dest.getType()==Material.DIAMOND_ORE && marked(dest) && !marked(source),"real redstone piston transfers mark after movement animation");
            a.breakBlock(dest);
            later(4,()->{ ok(progress(5)==35,"real piston-moved placed ore grants no progress"); realStickyPiston(); });
        });
    }
    private void realStickyPiston() throws Exception {
        Block piston=world.getBlockAt(172,70,161),head=world.getBlockAt(173,70,161),source=world.getBlockAt(174,70,161);
        head.setType(Material.AIR,false);source.setType(Material.AIR,false);piston.setType(Material.STICKY_PISTON,false);
        org.bukkit.block.data.Directional direction=(org.bukkit.block.data.Directional)piston.getBlockData();
        direction.setFacing(BlockFace.EAST);piston.setBlockData(direction,false);
        Block power=piston.getRelative(BlockFace.UP);power.setType(Material.REDSTONE_BLOCK,true);
        later(12,()->{
            ok(head.getType()==Material.PISTON_HEAD,"real sticky piston extended");
            place(source);power.setType(Material.AIR,true);
            later(15,()->{
                ok(head.getType()==Material.DIAMOND_ORE && marked(head) && !marked(source),"real sticky retract moves the mark toward the piston");
                a.breakBlock(head);
                later(4,()->{ ok(progress(5)==35,"retracted placed ore grants no progress"); fishMoveMonster(); });
            });
        });
    }
    private void fishMoveMonster() throws Exception {
        Item fish=world.dropItem(new Location(world,165,71,161),new ItemStack(Material.COD));
        denyFish=true; Bukkit.getPluginManager().callEvent(new PlayerFishEvent(a,fish,null,PlayerFishEvent.State.CAUGHT_FISH));
        for(int i=0;i<3;i++) Bukkit.getPluginManager().callEvent(new PlayerFishEvent(a,fish,null,PlayerFishEvent.State.CAUGHT_FISH));
        Location from=a.getLocation().clone();denyMove=true;
        Bukkit.getPluginManager().callEvent(new PlayerMoveEvent(a,from,from.clone().add(1,0,0)));
        for(int i=0;i<4;i++) Bukkit.getPluginManager().callEvent(new PlayerMoveEvent(a,from,from.clone().add(.9,0,0)));
        denyMonster=true;Zombie cancelled=(Zombie)world.spawnEntity(new Location(world,166,70,161),EntityType.ZOMBIE);cancelled.damage(100,a);
        Zombie killed=(Zombie)world.spawnEntity(new Location(world,167,70,161),EntityType.ZOMBIE);killed.damage(100,a);
        later(5,()->{
            fish.remove(); cancelled.remove();
            ok(!denyFish && progress(3)==3 && progress(8)==3,"late-cancelled fishing excluded; accepted fish advance both tiers");
            ok(!denyMove && progress(4)==3 && progress(9)==3,"late-cancelled movement excluded and accepted movement batched");
            ok(!denyMonster && progress(2)==1 && progress(7)==1,"late-cancelled monster death excluded");
            exp.open(a);later(20,()->{getLogger().info("KG-V712 REQUEST CLICK 0 10 0 1");later(12,this::afterShift);});
        });
    }
    private void afterShift() throws Exception {
        ok(!claimed(0) && core.getStore().getCoins(a.getName())==1000,"real shift-click cannot claim or move GUI items");
        getLogger().info("KG-V712 REQUEST CLICK 0 10 0 2");later(12,()->{
            ok(!claimed(0),"real number-key click cannot claim");
            getLogger().info("KG-V712 REQUEST CLICK 0 25 0 0");later(20,()->{
                Object holder=a.getOpenInventory().getTopInventory().getHolder();
                ok(holder!=null && ((Integer)field(holder,"page"))==1,"real click opens expert page");
                getLogger().info("KG-V712 REQUEST CLICK 0 18 0 0");later(20,()->{
                    Object h=a.getOpenInventory().getTopInventory().getHolder();
                    ok(h!=null && ((Integer)field(h,"page"))==0,"real click returns to basic page");
                    a.closeInventory(); batchFailure();
                });
            });
        });
    }
    private void batchFailure() throws Exception {
        for(String task:new String[]{"ORE","HARVEST","HUNT","FISH","WALK"}) advance(task,5000);
        ok(progress(0)==32 && progress(5)==64 && progress(6)==40 && progress(7)==24 && progress(8)==6 && progress(9)==1200,"all expert objectives cap at their separate goals");
        Path data=core.getDataFolder().toPath().resolve("data.yml"), backup=data.resolveSibling("v712-blocked.yml");
        Files.move(data,backup);Files.createSymbolicLink(data,data.resolveSibling("v712-no-target.yml"));
        try {
            a.performCommand("expeditions claim all");
            ok(!claimed(0) && !claimed(5) && core.getStore().getCoins(a.getName())==1000 && weekly("weekly")==4 && weekly("expertWeekly")==0,
                    "failed claim-all commits neither currencies nor any markers/counters");
        } finally { Files.deleteIfExists(data);Files.move(backup,data); }
        later(15,()->{
            a.performCommand("expeditions claim all");
            ok(core.getStore().getCoins(a.getName())==80500 && core.getStore().getSnowflakes(a.getName())==52,"claim-all awards exact sum of ready legacy and expert rewards");
            ok(claimed(0) && claimed(5) && claimed(9) && flag("dailyBonus") && flag("expertDailyBonus"),"both daily bonuses and all eligible markers committed");
            ok(weekly("weekly")==8 && weekly("expertWeekly")==5 && !flag("weeklyBonus") && !flag("expertWeeklyBonus"),"expert claims do not accelerate the legacy weekly goal");
            later(15,()->{
                a.performCommand("expeditions claim all");
                ok(core.getStore().getCoins(a.getName())==80500 && core.getStore().getSnowflakes(a.getName())==52,"repeated batch cannot duplicate rewards");
                a.performCommand("expeditions status"); expertWeeklyBonus(); initialDuels();
            });
        });
    }
    private void expertWeeklyBonus() throws Exception {
        String root="expeditions."+c.getUniqueId();YamlConfiguration data=yml();data.set(root,null);
        data.set(root+".day.date","2026-01-05");data.set(root+".week.date","2026-01-05");
        data.set(root+".day.progress.ore_plus",64);data.set(root+".week.expertClaims",9);data.set(root+".week.claims",2);
        core.getStore().setCoins(c.getName(),2500);core.getStore().addSnowflakes(c.getName(),-core.getStore().getSnowflakes(c.getName()));
        c.performCommand("expeditions claim all");data=yml();
        ok(data.getInt(root+".week.expertClaims")==10 && data.getBoolean(root+".week.expertBonus")
                && data.getInt(root+".week.claims")==2 && !data.getBoolean(root+".week.bonus")
                && core.getStore().getCoins(c.getName())==59500 && core.getStore().getSnowflakes(c.getName())==26,
                "claim-all atomically unlocks expert weekly bonus without altering original weekly progress");
    }
    private void initialDuels() throws Exception {
        double av=core.getStore().getCoins(a.getName()),bv=core.getStore().getCoins(b.getName());
        a.performCommand("duel ExpTwo NaN");b.performCommand("duel accept");
        ok(active()==null && core.getStore().getCoins(a.getName())==av && core.getStore().getCoins(b.getName())==bv,"nonfinite duel bet is rejected");
        core.getStore().setCoins(a.getName(),Double.MAX_VALUE);core.getStore().setCoins(b.getName(),Double.MAX_VALUE);
        ok(core.getStore().startWagerDuel(a.getName(),b.getName(),8e307,1)==null && active()==null,"duel is refused when a future winner payout would overflow");
        core.getStore().setCoins(a.getName(),av);core.getStore().setCoins(b.getName(),bv);
        a.performCommand("duel ExpTwo 1000");b.performCommand("duel decline");b.performCommand("duel accept");
        ok(active()==null && core.getStore().getCoins(a.getName())==av,"declined invite does not debit");
        denyTeleport=true;a.performCommand("duel ExpTwo 1000");b.performCommand("duel accept");
        waitFor(()->!denyTeleport && !preparing(),()->{
            ok(active()==null && core.getStore().getCoins(a.getName())==av && core.getStore().getCoins(b.getName())==bv,"cancelled second teleport refunds both stakes");
            ok(a.getWorld().equals(world),"first player returned after other teleport was cancelled");
            testDuelSaveFailure(av,bv);
        },100);
    }
    private void testDuelSaveFailure(double av,double bv) throws Exception {
        Path data=core.getDataFolder().toPath().resolve("data.yml"),backup=data.resolveSibling("v712-duel-blocked.yml");
        Files.move(data,backup);Files.createSymbolicLink(data,data.resolveSibling("v712-no-target.yml"));
        a.performCommand("duel ExpTwo 1000");b.performCommand("duel accept");
        waitFor(()->!preparing(),()->{
            try { ok(active()==null && core.getStore().getCoins(a.getName())==av && core.getStore().getCoins(b.getName())==bv,"failed escrow save debits neither player"); }
            finally { Files.deleteIfExists(data);Files.move(backup,data); }
            startCancelDuel(av,bv);
        },100);
    }
    private void startCancelDuel(double av,double bv) throws Exception {
        World spawn=core.getSpawnEngine().getPreparedSpawnArrival().getWorld();
        c.teleport(new Location(spawn,70.5,100,-41.5)); // an existing occupant prevents reuse of cabin #1
        a.performCommand("duel ExpTwo 1000");b.performCommand("duel accept");
        waitFor(()->active()!=null && !preparing(),()->{
            DataStore.ActiveWagerDuel d=active();
            ok(d.arena>1 && d.arena<=6 && core.getStore().getCoins(a.getName())==av-1000 && core.getStore().getCoins(b.getName())==bv-1000,"accepted duel durably escrows both stakes in one free cabin");
            ok(core.getSpawnEngine().isInsideAnyKabinka(a.getLocation()) && core.getSpawnEngine().isInsideAnyKabinka(b.getLocation()),"both participants arrive inside a cabin");
            EntityDamageByEntityEvent outsider=new EntityDamageByEntityEvent(c,a,EntityDamageEvent.DamageCause.ENTITY_ATTACK,2.0); Bukkit.getPluginManager().callEvent(outsider);
            ok(outsider.isCancelled(),"outsider cannot damage a reserved duel participant");
            a.performCommand("duel status");a.performCommand("duel cancel");
            ok(active()==d && core.getStore().getCoins(a.getName())==av-1000,"one player cannot unilaterally cancel the bank");
            b.performCommand("duel cancel");
            ok(active()==null && core.getStore().getCoins(a.getName())==av && core.getStore().getCoins(b.getName())==bv,"mutual cancellation refunds both exactly once");
            a.teleport(new Location(world,160.5,70,160.5));b.teleport(new Location(world,162.5,70,160.5));c.teleport(new Location(world,164.5,70,160.5));
            core.getHud().clearCombat(a.getUniqueId());core.getHud().clearCombat(b.getUniqueId());
            later(25,()->startEscapeDuel(av,bv));
        },100);
    }
    private void startEscapeDuel(double av,double bv) throws Exception {
        a.performCommand("duel ExpTwo 1000");b.performCommand("duel accept");
        waitFor(()->active()!=null && !preparing(),()->{
            denyEscape=true;boolean moved=a.teleport(new Location(world,160.5,70,160.5));
            later(5,()->{
                ok(!moved && !denyEscape && active()!=null && core.getStore().getCoins(a.getName())==av-1000,
                        "cancelled escape teleport does not refund or end the duel");
                a.teleport(new Location(world,160.5,70,160.5));
                later(6,()->{
                    boolean refunded=active()==null && core.getStore().getCoins(a.getName())==av && core.getStore().getCoins(b.getName())==bv;
                    ok(refunded,"leaving a cabin resolves a draw and returns both stakes");
                    if(!refunded) throw new AssertionError("escape failed; do not test a stale duel");
                    b.teleport(new Location(world,162.5,70,160.5));
                    core.getHud().clearCombat(a.getUniqueId());core.getHud().clearCombat(b.getUniqueId());
                    later(25,this::startWinDuel);
                });
            });
        },100);
    }
    private void startWinDuel() throws Exception {
        a.performCommand("duel ExpTwo 1000");b.performCommand("duel accept");
        waitFor(()->active()!=null && !preparing(),()->later(45,this::killWithBlockedSave),100);
    }
    private void killWithBlockedSave() throws Exception {
        Path data=core.getDataFolder().toPath().resolve("data.yml"),backup=data.resolveSibling("v712-pay-blocked.yml");
        Files.move(data,backup);Files.createSymbolicLink(data,data.resolveSibling("v712-no-target.yml"));
        a.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));b.setNoDamageTicks(0);b.damage(100,a);
        later(5,()->{
            try {
                ok(b.isDead(),"real duel damage caused player death");
                ok(active()!=null && pending() && core.getStore().getDuelStat(a.getName(),"wins")==0,"failed winner payout keeps escrow and does not record a win");
            } finally { Files.deleteIfExists(data);Files.move(backup,data); }
            waitFor(()->active()==null && !pending(),()->{
                ok(core.getStore().getDuelStat(a.getName(),"wins")==1 && core.getStore().getDuelStat(b.getName(),"losses")==1,"automatic retry pays and records exactly one win/loss");
                getLogger().info("KG-V712 REQUEST RESPAWN 1");
                waitFor(()->!b.isDead(),()->{
                    core.getHud().clearCombat(a.getUniqueId());core.getHud().clearCombat(b.getUniqueId());
                    a.teleport(new Location(world,160.5,70,160.5));b.teleport(new Location(world,162.5,70,160.5));
                    later(35,this::startCrashDuel);
                },100);
            },100);
        });
    }
    private void startCrashDuel() throws Exception {
        double av=core.getStore().getCoins(a.getName()),bv=core.getStore().getCoins(b.getName());
        a.performCommand("duel ExpTwo 1000");b.performCommand("duel accept");
        waitFor(()->active()!=null && !preparing(),()->{
            ok(core.getStore().getCoins(a.getName())==av-1000 && core.getStore().getCoins(b.getName())==bv-1000,"interrupted-duel fixture has both durable stakes reserved");
            Properties props=new Properties();props.setProperty("a",Double.toString(av));props.setProperty("b",Double.toString(bv));
            Files.createDirectories(getDataFolder().toPath());try(java.io.OutputStream out=Files.newOutputStream(getDataFolder().toPath().resolve("expected.properties"))) { props.store(out,"before crash"); }
            finish();
        },100);
    }
    private void verifyRestart() throws Exception {
        Properties props=new Properties();try(java.io.InputStream in=Files.newInputStream(getDataFolder().toPath().resolve("expected.properties"))) { props.load(in); }
        double av=Double.parseDouble(props.getProperty("a")),bv=Double.parseDouble(props.getProperty("b"));
        ok(active()==null && core.getStore().getActiveWagerDuel(b.getName())==null,"interrupted duel no longer active after startup");
        ok(core.getStore().getCoins(a.getName())==av && core.getStore().getCoins(b.getName())==bv,"startup refunds each interrupted stake exactly once");
        ok(core.getStore().getDuelStat(a.getName(),"wins")==1 && core.getStore().getDuelStat(b.getName(),"losses")==1,"committed duel stats survive SIGKILL");
        ok(claimed(0) && claimed(5) && claimed(9) && flag("dailyBonus") && flag("expertDailyBonus"),"batched expedition markers survive SIGKILL");
        ok(weekly("weekly")==8 && weekly("expertWeekly")==5 && core.getStore().getSnowflakes(a.getName())==52,"both weekly counters and snowflakes survive SIGKILL");
        core.getStore().load();
        ok(core.getStore().getCoins(a.getName())==av && core.getStore().getCoins(b.getName())==bv,"reloading saved refund cannot duplicate it");
        finish();
    }
}
