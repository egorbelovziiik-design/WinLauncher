package net.kinggrief.probe;

import net.kinggrief.core.DataStore;
import net.kinggrief.core.KingGriefCore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/** Disposable Paper: three protocol players and actual Core claims. */
public final class BuildContestProbe extends JavaPlugin implements Listener {
    private KingGriefCore core;private Player a,b,c;private World world;private DataStore.Claim ca,cb;
    private boolean running,done;private int checks;private String previousRound;private Path blockedData,blockedBackup;
    @Override public void onEnable(){saveDefaultConfig();core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");Bukkit.getPluginManager().registerEvents(this,this);}
    @EventHandler public void join(PlayerJoinEvent event){
        Player p=event.getPlayer();if("PlayerA".equals(p.getName()))a=p;if("PlayerB".equals(p.getName()))b=p;if("PlayerC".equals(p.getName()))c=p;
        if(a!=null&&b!=null&&c!=null&&!running){running=true;later(30,this::begin);}
    }
    private void later(long ticks,Runnable action){Bukkit.getScheduler().runTaskLater(this,()->{try{action.run();}catch(Throwable error){fail(error);}},ticks);}
    private void ok(boolean value,String text){if(!value)throw new AssertionError(text);checks++;getLogger().info("KG-CONTEST PASS "+text);}
    private void fail(Throwable error){if(done)return;done=true;restoreSave();getLogger().severe("KG-CONTEST RESULT FAIL "+error);error.printStackTrace();}
    private void finish(){done=true;restoreSave();getLogger().info("KG-CONTEST RESULT PASS "+checks+" checks");}
    private void console(String args){Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"buildcontest "+args);}
    private void vote(Player p,int number){p.performCommand("buildcontest vote "+yaml().getString("buildContest.round")+" "+number);}
    private String phase(){return yaml().getString("buildContest.phase","idle");}
    private String entry(Player p){return "buildContest.entries."+p.getUniqueId();}
    private String ballot(Player p){return "buildContest.votes."+p.getUniqueId();}
    private String value(Player p){return yaml().getString(ballot(p),"");}
    private void begin(){
        world=Bukkit.getWorld("world");world.getChunkAtAsync(32,32,true).whenComplete((chunk,error)->Bukkit.getScheduler().runTask(this,()->{
            try{
                if(error!=null||chunk==null)throw new AssertionError("async chunk load",error);
                world.addPluginChunkTicket(32,32,this);position();
                if(getConfig().getInt("phase",1)==1)setup();else restart();
            }catch(Throwable failure){fail(failure);}
        }));
    }
    private Location loc(int x){return new Location(world,x+.5,81,512.5);}
    private void position(){
        for(Player player:new Player[]{a,b,c}){player.setGameMode(GameMode.SURVIVAL);player.setAllowFlight(true);player.setFlying(true);}
        a.teleport(loc(512));b.teleport(loc(526));c.teleport(loc(519));
    }
    private void setup(){
        world.getBlockAt(512,80,512).setType(Material.DIAMOND_BLOCK,false);world.getBlockAt(526,80,512).setType(Material.EMERALD_BLOCK,false);
        ca=core.getStore().createClaim(a.getName(),loc(512),5);cb=core.getStore().createClaim(b.getName(),loc(526),5);
        ca.members.add(b.getName().toLowerCase(java.util.Locale.ROOT));core.getStore().save();
        a.performCommand("buildcontest start 1");ok("idle".equals(phase()),"ordinary player cannot start the contest");
        console("start 60");ok("entries".equals(phase()),"console starts a persisted submission phase");
        a.performCommand("buildcontest enter");ok(!yaml().contains(entry(a)),"coordinates are not published without explicit confirmation");
        core.getConfig().set("enable-builtin-auth",true);core.getStore().setAuthenticated(a,false);
        a.performCommand("buildcontest enter confirm");ok(!yaml().contains(entry(a)),"unauthenticated player cannot enter");core.getConfig().set("enable-builtin-auth",false);
        b.teleport(loc(512));b.performCommand("buildcontest enter confirm");
        ok(!yaml().contains(entry(b)),"a trusted builder cannot submit someone else's claim");b.teleport(loc(526));
        blockSave();a.performCommand("buildcontest enter confirm");
        ok(!yaml().contains(entry(a))&&ca.members.contains("playerb"),"failed submission save publishes nothing and leaves claim roles intact");restoreSave();
        a.performCommand("buildcontest enter confirm");ok(yaml().contains(entry(a)),"owner submits own claim voluntarily");
        a.performCommand("buildcontest enter confirm");ok(yaml().getInt("buildContest.sequence")==1,"duplicate submissions do not consume another entry number");
        console("voting 60");ok("entries".equals(phase()),"voting requires two valid claims");
        c.performCommand("buildcontest enter confirm");ok(!yaml().contains(entry(c)),"player outside a claim cannot enter");
        b.performCommand("buildcontest enter confirm");ok(yaml().contains(entry(b)),"second owner submits another claim");
        ok(world.getBlockAt(512,80,512).getType()==Material.DIAMOND_BLOCK&&world.getBlockAt(526,80,512).getType()==Material.EMERALD_BLOCK
                &&ca.members.contains("playerb"),"submissions do not alter blocks or claim roles");
        core.getGuis().openEventsMenu(a);Inventory menu=a.getOpenInventory().getTopInventory();
        ok(menu.getItem(32).getType()==Material.BRICKS,"events menu exposes the contest");
        InventoryClickEvent click=new InventoryClickEvent(a.getOpenInventory(),InventoryType.SlotType.CONTAINER,32,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        Bukkit.getPluginManager().callEvent(click);
        later(2,()->{ok(click.isCancelled()&&a.getOpenInventory().getTopInventory()!=menu,"events button safely opens contest status");openVoting();});
    }
    private int pending(){try{Field f=KingGriefCore.class.getDeclaredField("buildContest");f.setAccessible(true);Object manager=f.get(core);
        Field p=manager.getClass().getDeclaredField("pending");p.setAccessible(true);return ((Map<?,?>)p.get(manager)).size();
    }catch(Exception error){throw new RuntimeException(error);}}
    private void openVoting(){
        console("voting 60");ok("voting".equals(phase()),"administrator starts voting");
        c.performCommand("buildcontest vote "+UUID.randomUUID()+" 1");vote(a,1);vote(b,2);
        ok(pending()==0,"stale round and self-votes are rejected");
        vote(b,1);vote(a,2);vote(c,1);vote(c,1);
        ok(pending()==3,"one pending vote per account is retained");
        later(30,()->{
            ok(value(a).equals(b.getUniqueId().toString())&&value(b).equals(a.getUniqueId().toString())
                    &&value(c).equals(a.getUniqueId().toString()),"three real accounts' votes are saved in one batch");
            vote(c,1);ok(pending()==0,"repeating an already saved vote is idempotent");
            failureCase();
        });
    }
    private void blockSave(){try{
        blockedData=core.getDataFolder().toPath().resolve("data.yml");blockedBackup=blockedData.resolveSibling("test-contest-backup.yml");
        Files.move(blockedData,blockedBackup);Files.createSymbolicLink(blockedData,blockedBackup.getFileName());
    }catch(Exception error){throw new RuntimeException(error);}}
    private void restoreSave(){try{
        if(blockedData!=null&&Files.isSymbolicLink(blockedData))Files.delete(blockedData);
        if(blockedBackup!=null&&Files.exists(blockedBackup))Files.move(blockedBackup,blockedData);
        blockedData=null;blockedBackup=null;
    }catch(Exception error){getLogger().severe("Could not restore disposable test file: "+error);}}
    private void failureCase(){
        blockSave();vote(c,2);
        later(30,()->{
            ok(pending()==1&&value(c).equals(a.getUniqueId().toString()),"failed vote save preserves the old vote and queues the replacement");
            console("finish");ok("voting".equals(phase())&&pending()==1&&!core.getStore().hasUnlockedTitle(b.getName(),"build_master"),
                    "failed vote save blocks contest closing and prize");
            restoreSave();
            later(30,()->{
                ok(pending()==0&&value(c).equals(b.getUniqueId().toString()),"pending vote retries and is saved when storage recovers");
                ok("voting".equals(phase()),"voting remains active across the end of the first phase");finish();
            });
        });
    }
    private void restart(){
        ca=core.getStore().getClaimAt(loc(512));cb=core.getStore().getClaimAt(loc(526));
        ok(ca!=null&&cb!=null&&"voting".equals(phase())&&yaml().contains(entry(a))&&yaml().contains(entry(b)),"claims and active contest survive restart");
        ok(value(a).equals(b.getUniqueId().toString())&&value(b).equals(a.getUniqueId().toString())&&value(c).equals(b.getUniqueId().toString()),
                "saved votes and the changed vote survive restart");
        previousRound=yaml().getString("buildContest.round");
        blockSave();console("finish");
        ok("voting".equals(phase())&&!core.getStore().hasUnlockedTitle(b.getName(),"build_master"),
                "failed result commit keeps voting active and grants no title");restoreSave();
        console("finish");
        ok("closed".equals(phase())&&yaml().getString("buildContest.result","").contains(b.getName())
                &&core.getStore().hasUnlockedTitle(b.getName(),"build_master")&&!core.getStore().hasUnlockedTitle(a.getName(),"build_master"),
                "unique winner is calculated and cosmetic title is saved atomically");
        core.getGuis().openTitlesMenu(b);ok(b.getOpenInventory().getTopInventory().getItem(30).getType()==Material.NAME_TAG,"winner can see the title in /titles");b.closeInventory();
        b.performCommand("buildcontest withdraw");ok(!yaml().contains(entry(b))&&"closed".equals(phase()),"entrant can remove public coordinates after contest closes");
        tieCase();
    }
    private void enterBoth(){a.performCommand("buildcontest enter confirm");b.performCommand("buildcontest enter confirm");}
    private void tieCase(){
        console("start 60");enterBoth();console("voting 60");
        c.performCommand("buildcontest vote "+previousRound+" 1");ok(pending()==0,"a button from a previous round cannot vote in a new round");
        vote(a,2);vote(b,1);
        later(30,()->{
            console("finish");ok("closed".equals(phase())&&yaml().getString("buildContest.result","").contains("ничья")
                    &&!core.getStore().hasUnlockedTitle(a.getName(),"build_master"),"tie has no new winner or title");
            invalidClaimCase();
        });
    }
    private void invalidClaimCase(){
        console("start 60");enterBoth();console("voting 60");vote(b,1);vote(c,1);
        later(30,()->{
            core.getStore().removeClaim(ca);console("finish");
            ok("closed".equals(phase())&&yaml().getString("buildContest.result","").contains("меньше двух")
                    &&!core.getStore().hasUnlockedTitle(a.getName(),"build_master"),"deleted claim and its votes cannot win");
            ok(world.getBlockAt(512,80,512).getType()==Material.DIAMOND_BLOCK&&world.getBlockAt(526,80,512).getType()==Material.EMERALD_BLOCK,
                    "contest lifecycle never edits build blocks");
            cancelCase();
        });
    }
    private void cancelCase(){
        console("start 60");b.performCommand("buildcontest enter confirm");console("cancel");
        ok("entries".equals(phase()),"cancellation requires explicit confirmation");
        console("cancel confirm");ok("closed".equals(phase())&&yaml().getString("buildContest.result","").contains("отменён"),"confirmed cancellation closes without a winner");
        autoCloseCase();
    }
    private void autoCloseCase(){
        ca=core.getStore().createClaim(a.getName(),loc(512),5);
        console("start 60");enterBoth();console("voting 60");vote(b,1);
        later(30,()->{
            ok(value(b).equals(a.getUniqueId().toString()),"ballot is persisted before automatic close");
            yaml().set("buildContest.deadline",System.currentTimeMillis()-1);core.getStore().save();
            later(30,()->{
                ok("closed".equals(phase())&&core.getStore().hasUnlockedTitle(a.getName(),"build_master"),"deadline closes the vote and awards its unique winner");
                ok(core.getStore().getClaimAt(loc(512))==ca&&core.getStore().getClaimAt(loc(526))==cb,
                        "contest does not remove or replace participant claims");
                finish();
            });
        });
    }
    private YamlConfiguration yaml(){try{Field f=DataStore.class.getDeclaredField("yml");f.setAccessible(true);return (YamlConfiguration)f.get(core.getStore());}
        catch(Exception error){throw new RuntimeException(error);}}
}
