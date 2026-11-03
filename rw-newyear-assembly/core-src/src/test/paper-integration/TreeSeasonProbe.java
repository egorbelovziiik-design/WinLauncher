package net.kinggrief.probe;

import net.kinggrief.core.DataStore;
import net.kinggrief.core.KingGriefCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;

/** Disposable Paper tests with three protocol players, restart, and a SIGKILL. */
public final class TreeSeasonProbe extends JavaPlugin implements Listener {
    private KingGriefCore core;
    private Player a,b,c;
    private boolean running,done;
    private int checks;
    private static final String ROOT="winterTree";
    @Override public void onEnable(){saveDefaultConfig();core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");Bukkit.getPluginManager().registerEvents(this,this);}
    @EventHandler public void join(PlayerJoinEvent event){
        Player p=event.getPlayer();if("TreeHelper".equals(p.getName()))a=p;if("TreeFriend".equals(p.getName()))b=p;if("TreeObserver".equals(p.getName()))c=p;
        if(a!=null&&b!=null&&c!=null&&!running){running=true;later(40,this::begin);}
    }
    private void later(long ticks,Runnable action){Bukkit.getScheduler().runTaskLater(this,()->{try{action.run();}catch(Throwable e){fail(e);}},ticks);}
    private void ok(boolean value,String text){if(!value)throw new AssertionError(text);checks++;getLogger().info("KG-TREE PASS "+text);}
    private void fail(Throwable error){if(done)return;done=true;getLogger().severe("KG-TREE RESULT FAIL "+error);error.printStackTrace();}
    private void finish(){done=true;getLogger().info("KG-TREE RESULT PASS "+checks+" checks");}
    private void request(String action){getLogger().info("KG-TREE REQUEST "+action);}
    private Object field(Object target,String name){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new RuntimeException(e);}}
    private YamlConfiguration data(){return (YamlConfiguration)field(core.getStore(),"yml");}
    private String name(Player p){return p.getName().toLowerCase(Locale.ROOT);}
    private String today(){return LocalDate.now(ZoneOffset.UTC).toString();}
    private String current(){return data().getString(ROOT+".season","legacy");}
    private String donor(String root,Player p){return root+".donors."+name(p);}
    private String archived(String id){return ROOT+".history."+id;}
    private String player(Player p){return "players."+name(p);}
    private void console(String args){Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"kgtree "+args);}
    private CommandSender syntheticSender(Class<? extends CommandSender> type){
        return (CommandSender)java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,method,args)->{
            String n=method.getName();if("getName".equals(n))return "probe";if("getServer".equals(n))return Bukkit.getServer();
            if("isOp".equals(n)||"hasPermission".equals(n)||"isPermissionSet".equals(n))return true;
            Class<?> result=method.getReturnType();if(result==boolean.class)return false;if(result==int.class)return 0;if(result==long.class)return 0L;return null;
        });
    }
    private boolean treeAdmin(CommandSender sender){try{java.lang.reflect.Method m=core.getClass().getDeclaredMethod("hasTreeSeasonAdmin",CommandSender.class);m.setAccessible(true);return (Boolean)m.invoke(core,sender);}catch(Exception error){throw new RuntimeException(error);}}
    private int archiveCount(){ConfigurationSection s=data().getConfigurationSection(ROOT+".history");return s==null?0:s.getKeys(false).size();}
    private int visual(){return ((Number)field(field(core,"winterTree"),"visualStage")).intValue();}
    private Location marker(){World w=Bukkit.getWorld("world");return new Location(w,160,100,160);}
    private void unaffected(String description){
        Location loc=marker();DataStore.Claim claim=core.getStore().getClaimAt(loc);
        ok(loc.getBlock().getType()==Material.DIAMOND_BLOCK&&claim!=null&&claim.owner.equalsIgnoreCase("TreeHelper"),description);
        World spawn=Bukkit.getWorld("spawn_world");
        ok(spawn!=null&&spawn.getBlockAt(0,102,0).getType()==Material.SPRUCE_WOOD,"spawn tree trunk is unchanged by season operations");
        ok(a.getInventory().getItem(0)!=null&&a.getInventory().getItem(0).getType()==Material.DIAMOND&&a.getInventory().getItem(0).getAmount()==13,"kgtree commands do not move the participant's inventory");
    }
    private void withBlockedSave(Runnable action){
        Path file=core.getDataFolder().toPath().resolve("data.yml"),backup=file.resolveSibling("tree-test-backup.yml");
        try{Files.move(file,backup);Files.createSymbolicLink(file,backup.getFileName());action.run();}
        catch(Exception e){throw new RuntimeException(e);}
        finally{try{if(Files.isSymbolicLink(file))Files.delete(file);if(Files.exists(backup))Files.move(backup,file);}catch(Exception e){throw new RuntimeException(e);}}
    }
    private void begin(){int phase=getConfig().getInt("phase",1);if(phase==1)first();else if(phase==2)second();else third();}
    private void commandCollision(){
        org.bukkit.plugin.Plugin foreign=Bukkit.getPluginManager().getPlugin("KgForeignTree");
        org.bukkit.command.PluginCommand shortName=Bukkit.getPluginCommand("tree");
        org.bukkit.command.PluginCommand ours=Bukkit.getPluginCommand("kgtree");
        ok(foreign!=null&&shortName!=null&&shortName.getPlugin()==foreign,"other plugin keeps its own /tree command");
        ok(ours!=null&&ours.getPlugin()==core&&Bukkit.getPluginCommand("kinggriefcore:kgtree")==ours,"/kgtree and the namespaced fallback both belong to Core");
        try {
            int before=((Number)foreign.getClass().getMethod("getCalls").invoke(foreign)).intValue();
            a.performCommand("tree status");
            ok(((Number)foreign.getClass().getMethod("getCalls").invoke(foreign)).intValue()==before+1,"bare /tree still reaches the unrelated plugin");
            ok(ours.tabComplete(a,"kgtree",new String[]{"s"}).contains("status"),"canonical /kgtree offers tab completion");
        } catch(Exception error){throw new RuntimeException(error);}
    }
    private void first(){
        commandCollision();
        a.getInventory().setItem(0,new ItemStack(Material.DIAMOND,13));
        for(Player p:new Player[]{a,b,c}){core.getStore().addSnowflakes(p.getName(),50-core.getStore().getSnowflakes(p.getName()));core.getStore().setCoins(p.getName(),1000);}
        Location loc=marker();loc.getBlock().setType(Material.DIAMOND_BLOCK);core.getStore().createClaim("TreeHelper",loc,7);
        // Legacy data written by an earlier experimental build: four of A's daily decorations already exist.
        data().set(ROOT+".total",18);data().set(donor(ROOT,a)+".total",4);data().set(donor(ROOT,a)+".joinedAt",14);
        data().set(donor(ROOT,a)+".day",today());data().set(donor(ROOT,a)+".daily",4);data().set(donor(ROOT,a)+".custom.note","preserve-me");
        core.getStore().save();
        a.performCommand("kgtree donate 7");ok(core.getStore().getSnowflakes(a.getName())==50&&data().getInt(ROOT+".total")==18,"legacy per-day count prevents crossing the cap before migration");
        a.performCommand("kgtree donate 6");ok(core.getStore().getSnowflakes(a.getName())==44&&data().getInt(donor(ROOT,a)+".total")==10&&data().getInt(ROOT+".total")==24,"legacy donor can contribute the remaining daily allowance atomically");
        c.performCommand("kgtree donate 6");b.performCommand("kgtree donate 1");
        ok(data().getInt(ROOT+".total")==31&&visual()==1&&data().getInt(donor(ROOT,b)+".joinedAt")==30,"first stage opens, and a late donor is not retroactively eligible");
        b.performCommand("kgtree claim");ok(core.getStore().getCoins(b.getName())==1000&&!data().getBoolean(donor(ROOT,b)+".claimed.1"),"late donor cannot claim an already reached threshold");
        b.performCommand("kgtree season start unauthorized confirm");console("season start missing_confirmation");
        ok("legacy".equals(current())&&archiveCount()==0,"ordinary players and unconfirmed administrative commands cannot rotate");
        b.setOp(true);try{b.performCommand("kgtree season start op_bypass confirm");}finally{b.setOp(false);}
        ok("legacy".equals(current()),"OP alone cannot administer seasons without an OWNER-level rank");
        CommandSender block=syntheticSender(BlockCommandSender.class);Bukkit.dispatchCommand(block,"kgtree season start block_bypass confirm");
        ok("legacy".equals(current())&&!treeAdmin(block),"synthetic command-block sender cannot administer seasons");
        Bukkit.dispatchCommand(block,"calendar add parkour "+java.time.Instant.now().plusSeconds(3600));
        Bukkit.dispatchCommand(block,"gamevote start 60");
        Bukkit.dispatchCommand(block,"buildcontest start 10");
        Bukkit.dispatchCommand(block,"kgbackup create");
        ok(!data().contains("eventCalendar.entries")&&!data().contains("buildContest.phase"),"command block cannot schedule or start a contest");
        ok(((Number)field(field(core,"gameVotes"),"deadline")).longValue()==0L
                &&!Files.exists(core.getDataFolder().toPath().resolve("backups")),"command block cannot start a vote or create a backup");
        ok(treeAdmin(syntheticSender(RemoteConsoleCommandSender.class)),"RCON console is explicitly recognized by the authorization predicate");
        String oldRank=core.getStore().getSavedRank(a.getName());core.getStore().setSavedRank(a.getName(),"techadmin");
        ok(!a.isOp(),"TECH.ADMIN test runs without granting OP");
        core.getConfig().set("enable-builtin-auth",true);core.getStore().setAuthenticated(a,false);a.performCommand("kgtree season start unauth confirm");
        ok("legacy".equals(current()),"unauthenticated TECH.ADMIN cannot rotate the tree");
        core.getStore().setAuthenticated(a,true);core.getConfig().set("enable-builtin-auth",false);
        core.getStore().toggleQuickFreeze(a.getUniqueId());a.performCommand("kgtree season start frozen confirm");a.performCommand("kgtree claim");
        ok("legacy".equals(current())&&core.getStore().getCoins(a.getName())==1000,"freeze blocks both administration and rewards");core.getStore().toggleQuickFreeze(a.getUniqueId());
        // Simulate upgrade from an old server: per-donor daily counts exist but the new ledger does not.
        data().set(ROOT+".dailyLedger",null);core.getStore().save();
        withBlockedSave(()->console("season start winter_2026 confirm"));
        ok("legacy".equals(current())&&archiveCount()==0&&!data().contains(ROOT+".dailyLedger")&&data().getInt(ROOT+".total")==31,"failed durable rollover restores legacy data and leaves no partial ledger");
        a.performCommand("kgtree season start winter_2026 confirm");
        ok("winter_2026".equals(current())&&data().getInt(ROOT+".total")==0&&visual()==0,"TECH.ADMIN rotates only after confirmation and resets visuals");
        ok(data().getInt(archived("legacy")+".total")==31&&data().getInt(donor(archived("legacy"),a)+".total")==10&&"preserve-me".equals(data().getString(donor(archived("legacy"),a)+".custom.note")),"legacy donor data and unknown nested fields are archived intact");
        ok(data().getIntegerList(archived("legacy")+".policy.goals").equals(java.util.Arrays.asList(30,100,300))&&data().getIntegerList(archived("legacy")+".policy.coins").equals(java.util.Arrays.asList(5000,15000,35000))&&data().getInt(archived("legacy")+".policy.version")==1,"archive freezes reward thresholds and amounts");
        ok(data().getInt(ROOT+".dailyLedger.used."+name(a))==10&&data().getInt(ROOT+".dailyLedger.used."+name(b))==1&&data().getInt(ROOT+".dailyLedger.used."+name(c))==6,"rollover migrates all legacy daily counts even without a ledger");
        ok(data().getInt(ROOT+".lifetime."+name(a))==10&&data().getInt(ROOT+".lifetime."+name(b))==1&&data().getInt(ROOT+".lifetime."+name(c))==6,"rollover preserves cumulative achievement contribution");
        core.getStore().setSavedRank(a.getName(),oldRank);
        a.performCommand("achievements claim");String badges="achievements."+a.getUniqueId()+".claimed.";
        ok(data().getBoolean(badges+"tree_1")&&data().getBoolean(badges+"tree_10"),"legacy contribution still unlocks both tree badges after rotation");
        a.performCommand("kgtree donate 1");b.performCommand("kgtree donate 9");
        ok(core.getStore().getSnowflakes(a.getName())==44&&data().getInt(ROOT+".total")==9&&data().getInt(ROOT+".dailyLedger.used."+name(b))==10,"same-day limit is shared across seasons without resetting on rollover");
        request("DISCONNECT_C");later(35,()->afterDisconnect());
    }
    private void afterDisconnect(){
        ok(!c.isOnline(),"unclaimed eligible player is offline during subsequent rotations");
        console("season prune legacy confirm");ok(data().isConfigurationSection(archived("legacy")),"an archive with unclaimed player rewards cannot be deleted");
        a.performCommand("kgtree history");later(35,()->afterHistoryClick());
    }
    private void afterHistoryClick(){
        ok(core.getStore().getCoins(a.getName())==6000&&data().getBoolean(donor(archived("legacy"),a)+".claimed.1"),"real history chat button pays the correct archived reward");
        a.performCommand("kgtree claim legacy");b.performCommand("kgtree claim legacy");
        ok(core.getStore().getCoins(a.getName())==6000&&core.getStore().getCoins(b.getName())==1000,"archived rewards are idempotent and not transferable to a late donor");
        console("season prune legacy confirm");ok(data().isConfigurationSection(archived("legacy")),"offline player's unclaimed reward still blocks archive deletion");
        console("season start winter_2027 confirm");
        ok("winter_2027".equals(current())&&data().getInt(archived("winter_2026")+".total")==9,"second season archives its own contributions");
        console("season prune winter_2026 confirm");
        ok(!data().contains(archived("winter_2026"))&&data().getInt(ROOT+".lifetime."+name(b))==10,"an archive without reached rewards can be pruned without losing achievements");
        console("season start winter_2026 confirm");ok("winter_2027".equals(current()),"a pruned season ID cannot be reused");
        a.performCommand("kgtree donate 1");ok(data().getInt(ROOT+".total")==0,"two same-day rollovers cannot bypass the contribution limit");
        // Simulate the next UTC day without waiting 24 hours. Ten old contributions
        // are unattributed fixture progress, as in a migrated pre-existing tree.
        data().set(ROOT+".dailyLedger.day","2001-01-01");data().set(ROOT+".dailyLedger.used",null);data().set(ROOT+".total",10);core.getStore().save();
        a.performCommand("kgtree donate 10");b.performCommand("kgtree donate 10");
        ok(data().getInt(ROOT+".total")==30&&data().getInt(ROOT+".dailyLedger.used."+name(a))==10&&data().getInt(ROOT+".dailyLedger.used."+name(b))==10,"a new UTC date allows fresh daily contributions");
        b.performCommand("kgtree claim");ok(core.getStore().getCoins(b.getName())==6000&&data().getBoolean(donor(ROOT,b)+".claimed.1"),"new season pays its first milestone independently");
        unaffected("rotations preserve unrelated claim and block");core.getStore().save();finish();
    }
    private void second(){
        ok("winter_2027".equals(current())&&data().getInt(ROOT+".total")==30&&visual()==1,"active season and particle stage survive orderly restart");
        ok(data().getInt(archived("legacy")+".total")==31&&!data().getBoolean(donor(archived("legacy"),c)+".claimed.1")&&data().getBoolean(donor(archived("legacy"),a)+".claimed.1"),"offline archived rewards and prior claim markers survive restart");
        ok(data().getInt(ROOT+".lifetime."+name(a))==10&&data().getInt(ROOT+".lifetime."+name(b))==10&&data().getInt(ROOT+".lifetime."+name(c))==6,"lifetime progress and pruning survive restart");
        b.performCommand("kgtree claim");ok(core.getStore().getCoins(b.getName())==6000,"current milestone cannot pay twice after restart");
        withBlockedSave(()->c.performCommand("kgtree claim legacy"));
        ok(core.getStore().getCoins(c.getName())==1000&&!data().getBoolean(donor(archived("legacy"),c)+".claimed.1"),"failed archived payout leaves balance and claim marker unchanged");
        c.performCommand("kgtree claim legacy");c.performCommand("kgtree claim legacy");
        ok(core.getStore().getCoins(c.getName())==6000&&data().getBoolean(donor(archived("legacy"),c)+".claimed.1"),"returning offline player receives the archived reward once");
        withBlockedSave(()->console("season prune legacy confirm"));ok(data().isConfigurationSection(archived("legacy")),"failed archive cleanup rolls back");
        console("season prune legacy confirm");ok(!data().contains(archived("legacy"))&&data().getInt(ROOT+".lifetime."+name(c))==6,"settled archive can be pruned while keeping lifetime progress");
        a.performCommand("kgtree claim");ok(core.getStore().getCoins(a.getName())==11000,"another current participant claims exactly once");
        // Simulate many other participants reaching all three milestones.
        data().set(ROOT+".total",300);core.getStore().save();console("season start cap0 confirm");
        ok(data().getInt(archived("winter_2027")+".total")==300&&data().getBoolean(donor(archived("winter_2027"),b)+".claimed.1"),"rotation snapshots all reached stages and existing claim flags");
        console("season prune winter_2027 confirm");ok(data().isConfigurationSection(archived("winter_2027")),"new archive with remaining stages cannot be pruned");
        withBlockedSave(()->b.performCommand("kgtree claim winter_2027"));
        ok(core.getStore().getCoins(b.getName())==6000&&core.getStore().getCaseKeys(b.getName(),"legendary")==0&&!data().getBoolean(donor(archived("winter_2027"),b)+".claimed.2"),"failed multi-stage payout rolls back coins, key, and markers together");
        data().set(player(b)+".coins",Double.MAX_VALUE);b.performCommand("kgtree claim winter_2027");
        ok(data().getDouble(player(b)+".coins")==Double.MAX_VALUE&&!data().getBoolean(donor(archived("winter_2027"),b)+".claimed.2"),"unrepresentable balance refuses the whole reward");data().set(player(b)+".coins",6000.0);
        data().set(player(b)+".cases.legendary",Integer.MAX_VALUE);b.performCommand("kgtree claim winter_2027");
        ok(data().getInt(player(b)+".cases.legendary")==Integer.MAX_VALUE&&core.getStore().getCoins(b.getName())==6000&&!data().getBoolean(donor(archived("winter_2027"),b)+".claimed.3"),"key overflow refuses the whole reward");data().set(player(b)+".cases.legendary",0);core.getStore().save();
        b.performCommand("kgtree claim winter_2027");b.performCommand("kgtree claim winter_2027");
        ok(core.getStore().getCoins(b.getName())==56000&&core.getStore().getCaseKeys(b.getName(),"legendary")==1&&data().getBoolean(player(b)+".titles.tree_friend")&&data().getBoolean(donor(archived("winter_2027"),b)+".claimed.2")&&data().getBoolean(donor(archived("winter_2027"),b)+".claimed.3"),"old stages pay 50000 coins, one key and title exactly once");
        // Fill bounded archive with small, unreached fixture seasons.
        String savedRank=core.getStore().getSavedRank(a.getName());core.getStore().setSavedRank(a.getName(),"owner");
        data().set(ROOT+".total",1);a.performCommand("kgtree season start cap1 confirm");core.getStore().setSavedRank(a.getName(),savedRank);
        ok("cap1".equals(current()),"OWNER can start a confirmed season");
        for(int i=1;i<7;i++){data().set(ROOT+".total",1);console("season start cap"+(i+1)+" confirm");}
        ok(archiveCount()==8&&"cap7".equals(current()),"at most eight prior seasons are retained");
        data().set(ROOT+".total",1);console("season start cap8 confirm");
        ok(archiveCount()==8&&"cap7".equals(current())&&data().getInt(ROOT+".total")==1,"full archive blocks rotation without discarding the current progress");
        console("season prune winter_2027 confirm");ok(data().isConfigurationSection(archived("winter_2027")),"another player's remaining final rewards still block deletion");
        data().set(archived("cap1")+".policy.version",99);core.getStore().save();console("season prune cap1 confirm");
        ok(data().isConfigurationSection(archived("cap1"))&&data().getInt(archived("cap1")+".policy.version")==99,"unknown archive policy cannot be silently deleted");data().set(archived("cap1")+".policy.version",1);core.getStore().save();
        console("season prune cap0 confirm");console("season start cap8 confirm");
        ok(archiveCount()==8&&"cap8".equals(current()),"after safe cleanup the blocked rotation succeeds");
        console("season start cap0 confirm");ok("cap8".equals(current()),"cleaned-up season names remain permanently reserved");
        unaffected("archive limits and payouts preserve the claim and block");finish();
    }
    private void third(){
        ok("cap8".equals(current())&&archiveCount()==8&&data().getInt(archived("winter_2027")+".total")==300,"season metadata and archive cap survive abrupt SIGKILL");
        ok(core.getStore().getCoins(b.getName())==56000&&core.getStore().getCaseKeys(b.getName(),"legendary")==1&&data().getBoolean(donor(archived("winter_2027"),b)+".claimed.3"),"durable payout and markers survive abrupt SIGKILL");
        b.performCommand("kgtree claim winter_2027");ok(core.getStore().getCoins(b.getName())==56000&&core.getStore().getCaseKeys(b.getName(),"legendary")==1,"SIGKILL restart cannot duplicate a completed payout");
        a.performCommand("kgtree claim winter_2027");a.performCommand("kgtree claim winter_2027");
        ok(core.getStore().getCoins(a.getName())==61000&&core.getStore().getCaseKeys(a.getName(),"legendary")==1&&data().getBoolean(player(a)+".titles.tree_friend"),"remaining player can claim final archived rewards once after SIGKILL");
        console("season prune winter_2027 confirm");ok(!data().contains(archived("winter_2027"))&&data().getInt(ROOT+".lifetime."+name(a))==20&&data().getInt(ROOT+".lifetime."+name(b))==20,"settled archive prunes without reducing lifetime achievements");
        String badges="achievements."+a.getUniqueId()+".claimed.";ok(data().getBoolean(badges+"tree_1")&&data().getBoolean(badges+"tree_10"),"earned tree achievements survive cleanup and SIGKILL");
        unaffected("unrelated claim and world block survive restart and SIGKILL");
        String ledgerDay=data().getString(ROOT+".dailyLedger.day");int usedBefore=data().getInt(ROOT+".dailyLedger.used."+name(a));
        data().set(ROOT+".dailyLedger.day","not-a-date");console("season start invalid_day confirm");
        ok("cap8".equals(current())&&"not-a-date".equals(data().getString(ROOT+".dailyLedger.day"))&&data().getInt(ROOT+".dailyLedger.used."+name(a))==usedBefore,"invalid ledger dates cannot erase a daily limit");
        data().set(ROOT+".dailyLedger.day",null);console("season start missing_day confirm");
        ok("cap8".equals(current())&&data().getInt(ROOT+".dailyLedger.used."+name(a))==usedBefore,"an undated nonempty ledger fails closed");data().set(ROOT+".dailyLedger.day",ledgerDay);
        data().set(ROOT+".total",1);data().set(ROOT+".donors.corrupt.total",1);data().set(ROOT+".donors.corrupt.joinedAt",0);data().set(ROOT+".donors.corrupt.claimed","unexpected scalar");
        console("season start should_fail confirm");
        ok("cap8".equals(current())&&"unexpected scalar".equals(data().getString(ROOT+".donors.corrupt.claimed"))&&data().getInt(ROOT+".total")==1,"malformed donor metadata fails closed without overwriting it");
        data().set(ROOT+".donors",null);data().set(ROOT+".total",0);
        ConfigurationSection ids=data().getConfigurationSection(ROOT+".usedSeasonIds");int index=0;
        while(ids.getKeys(false).size()<255){data().set(ROOT+".usedSeasonIds.f"+(index++),true);}
        core.getStore().save();console("season start cap9 confirm");
        ok("cap9".equals(current())&&data().getConfigurationSection(ROOT+".usedSeasonIds").getKeys(false).size()==256,"the final available season ID can be used");
        console("season start overflow confirm");ok("cap9".equals(current())&&data().getConfigurationSection(ROOT+".usedSeasonIds").getKeys(false).size()==256,"the 256-ID bound rejects further rotations without clearing data");
        finish();
    }
}
