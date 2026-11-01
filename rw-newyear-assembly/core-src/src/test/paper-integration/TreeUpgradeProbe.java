package net.kinggrief.probe;

import net.kinggrief.core.DataStore;
import net.kinggrief.core.KingGriefCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.Locale;

/** Upgrade test: create real tree data with the previous Core, then replace its JAR. */
public final class TreeUpgradeProbe extends JavaPlugin implements Listener {
    private KingGriefCore core;private Player a,b;private boolean started,done;private int checks;
    @Override public void onEnable(){saveDefaultConfig();core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");Bukkit.getPluginManager().registerEvents(this,this);}
    @EventHandler public void join(PlayerJoinEvent e){if("TreeHelper".equals(e.getPlayer().getName()))a=e.getPlayer();if("TreeFriend".equals(e.getPlayer().getName()))b=e.getPlayer();
        if(a!=null&&b!=null&&!started){started=true;Bukkit.getScheduler().runTaskLater(this,()->{try{run();}catch(Throwable error){fail(error);}},40);}}
    private void ok(boolean value,String message){if(!value)throw new AssertionError(message);checks++;getLogger().info("KG-TREE-UPGRADE PASS "+message);}
    private void fail(Throwable error){if(done)return;done=true;getLogger().severe("KG-TREE-UPGRADE RESULT FAIL "+error);error.printStackTrace();}
    private void finish(){done=true;getLogger().info("KG-TREE-UPGRADE RESULT PASS "+checks+" checks");}
    private YamlConfiguration data(){try{Field f=core.getStore().getClass().getDeclaredField("yml");f.setAccessible(true);return (YamlConfiguration)f.get(core.getStore());}catch(Exception e){throw new RuntimeException(e);}}
    private int visual(){try{Field f=core.getClass().getDeclaredField("winterTree");f.setAccessible(true);Object manager=f.get(core);Field stage=manager.getClass().getDeclaredField("visualStage");stage.setAccessible(true);return stage.getInt(manager);}catch(Exception e){throw new RuntimeException(e);}}
    private String name(Player p){return p.getName().toLowerCase(Locale.ROOT);}
    private String donor(String root,Player p){return root+".donors."+name(p);}
    private void console(String command){Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"tree "+command);}
    private Location mark(){return new Location(Bukkit.getWorld("world"),160,100,160);}
    private void unchanged(){World spawn=Bukkit.getWorld("spawn_world");DataStore.Claim claim=core.getStore().getClaimAt(mark());
        ok(spawn!=null&&spawn.getBlockAt(0,130,0).getType()==Material.SPRUCE_WOOD&&mark().getBlock().getType()==Material.DIAMOND_BLOCK&&claim!=null&&claim.owner.equalsIgnoreCase(a.getName()),"real spawn, foreign-world block and claim remain intact");
        ItemStack item=a.getInventory().getItem(0);ok(item!=null&&item.getType()==Material.DIAMOND&&item.getAmount()==13,"inventory survives the Core upgrade");}
    private void run(){if(getConfig().getInt("phase",1)==1)oldCore();else newCore();}
    private void oldCore(){
        a.getInventory().setItem(0,new ItemStack(Material.DIAMOND,13));
        for(Player p:new Player[]{a,b}){core.getStore().addSnowflakes(p.getName(),50-core.getStore().getSnowflakes(p.getName()));core.getStore().setCoins(p.getName(),1000);}
        mark().getBlock().setType(Material.DIAMOND_BLOCK);core.getStore().createClaim(a.getName(),mark(),7);
        data().set("winterTree.total",20);core.getStore().save();
        a.performCommand("tree donate 10");b.performCommand("tree donate 1");
        ok(data().getInt("winterTree.total")==31&&data().getInt(donor("winterTree",a)+".total")==10&&data().getInt(donor("winterTree",b)+".joinedAt")==30,"previous Core creates real legacy donations and the late-join boundary");
        ok(data().getInt(donor("winterTree",a)+".daily")==10&&!data().getBoolean(donor("winterTree",a)+".claimed.1")&&visual()==1,"previous Core persists daily usage and unclaimed first-stage reward");
        data().set(donor("winterTree",a)+".custom.note","old-core-marker");core.getStore().save();unchanged();finish();
    }
    private void newCore(){
        ok(data().getInt("winterTree.total")==31&&data().getInt(donor("winterTree",a)+".daily")==10&&visual()==1,"new Core reads old progress before any rotation");
        ok(core.getStore().getSnowflakes(a.getName())==40&&core.getStore().getCoins(a.getName())==1000,"old wallet and unclaimed reward are unchanged after upgrade");
        a.performCommand("tree donate 1");ok(data().getInt("winterTree.total")==31&&core.getStore().getSnowflakes(a.getName())==40,"legacy daily allowance remains enforced on upgraded Core");
        console("season start upgrade_2026 confirm");String legacy="winterTree.history.legacy";
        ok("upgrade_2026".equals(data().getString("winterTree.season"))&&data().getInt(legacy+".total")==31&&"old-core-marker".equals(data().getString(donor(legacy,a)+".custom.note")),"upgrade archives old data and unknown donor fields");
        ok(data().getInt("winterTree.dailyLedger.used."+name(a))==10&&data().getInt("winterTree.lifetime."+name(a))==10,"upgrade migrates the limit and lifetime contribution");
        a.performCommand("tree claim legacy");a.performCommand("tree claim legacy");b.performCommand("tree claim legacy");
        ok(core.getStore().getCoins(a.getName())==6000&&core.getStore().getCoins(b.getName())==1000&&data().getBoolean(donor(legacy,a)+".claimed.1"),"legacy first-stage reward pays once and not to the late donor");
        a.performCommand("achievements claim");String badges="achievements."+a.getUniqueId()+".claimed.";
        ok(data().getBoolean(badges+"tree_1")&&data().getBoolean(badges+"tree_10"),"old Core donations unlock cumulative badges after upgrade");
        unchanged();finish();
    }
}
