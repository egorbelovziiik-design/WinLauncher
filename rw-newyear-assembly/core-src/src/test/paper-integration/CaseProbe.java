package net.kinggrief.probe;

import net.kinggrief.core.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;

/** Real Paper case animation, debit/refund failures, inventory and disconnect. */
public final class CaseProbe extends JavaPlugin implements Listener {
    private KingGriefCore core; private SpawnEngine engine; private DataStore store; private Player player;
    private SpawnEngine.CasePedestal money, summer; private Path data,backup;
    private int checks,errors;private boolean running,finished;private Random original;
    @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);}
    @EventHandler public void join(PlayerJoinEvent e){if(!running){running=true;player=e.getPlayer();later(40,this::begin);}}
    private void later(long ticks,Runnable r){Bukkit.getScheduler().runTaskLater(this,()->{try{r.run();}catch(Throwable t){fail(t);}},ticks);}
    private void ok(boolean b,String text){checks++;if(!b){errors++;getLogger().severe("KG-CASE FAIL: "+text);}}
    private void fail(Throwable t){errors++;getLogger().severe("KG-CASE EXCEPTION: "+t);t.printStackTrace();restore();finish();}
    private void finish(){if(finished)return;finished=true;restore();getLogger().info("KG-CASE RESULT "+(errors==0?"PASS":"FAIL")+" checks="+checks+" errors="+errors);}
    private void link()throws Exception{Files.move(data,backup);Files.createSymbolicLink(data,backup.getFileName());}
    private void restore(){try{if(data!=null&&Files.isSymbolicLink(data)){Files.delete(data);Files.move(backup,data);}}catch(Exception e){errors++;}}
    @SuppressWarnings("unchecked") private int displays()throws Exception{Field f=SpawnEngine.class.getDeclaredField("activeCaseDisplays");f.setAccessible(true);return ((Map<String,UUID>)f.get(engine)).size();}
    private void replaceRandom(Random value)throws Exception{Field f=SpawnEngine.class.getDeclaredField("rng");f.setAccessible(true);f.set(engine,value);}
    private void begin(){try{
        core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");engine=core.getSpawnEngine();store=core.getStore();
        for(SpawnEngine.CasePedestal cp:engine.getCasePedestals()){if(cp.caseId.equals("money"))money=cp;if(cp.caseId.equals("summer"))summer=cp;}
        if(money==null||summer==null)throw new IllegalStateException("case definitions absent");
        data=core.getDataFolder().toPath().resolve("data.yml");backup=data.resolveSibling("case-probe-backup.yml");
        player.setGameMode(GameMode.SURVIVAL);player.getInventory().clear();store.setCoins(player.getName(),1000);
        store.addSnowflakes(player.getName(),10);store.addCaseKeys(player.getName(),"summer",1);store.addCaseKeys(player.getName(),"money",1);store.save();
        for(int i=0;i<36;i++)player.getInventory().setItem(i,new ItemStack(Material.COBBLESTONE,64));
        int summerBefore=store.getCaseKeys(player.getName(),"summer"), snowBefore=store.getSnowflakes(player.getName());
        engine.openShulkerCaseAnimated(player,summer);engine.spinFortuneWheel(player,true);
        ok(store.getCaseKeys(player.getName(),"summer")==summerBefore && displays()==0,"full inventory refuses case without spending");
        ok(store.getSnowflakes(player.getName())==snowBefore,"full inventory refuses wheel without spending");
        player.getInventory().clear();
        engine.openShulkerCaseAnimated(player,money);engine.openShulkerCaseAnimated(player,money);
        ok(store.getCaseKeys(player.getName(),"money")==1 && displays()==1,"key remains during animation and duplicate start is locked");
        later(75,()->{try{success();}catch(Exception e){throw new RuntimeException(e);}});
    }catch(Throwable t){fail(t);}}
    private void success()throws Exception{
        ok(store.getCaseKeys(player.getName(),"money")==0 && store.getCoins(player.getName())>1000 && displays()==0,
                "completed animation consumes one key and grants money");
        DataStore disk=new DataStore(core);
        ok(disk.getCaseKeys(player.getName(),"money")==0 && disk.getCoins(player.getName())==store.getCoins(player.getName()),"case result persisted");
        store.addCaseKeys(player.getName(),"money",1);store.save();final double before=store.getCoins(player.getName());
        engine.openShulkerCaseAnimated(player,money);
        later(20,()->{try{link();}catch(Exception e){throw new RuntimeException(e);}});
        later(75,()->failedSave(before));
    }
    private void failedSave(double before){try{
        restore();ok(store.getCaseKeys(player.getName(),"money")==1 && store.getCoins(player.getName())==before && displays()==0,
                "failed durable key debit grants nothing and retains key");
        DataStore disk=new DataStore(core);ok(disk.getCaseKeys(player.getName(),"money")==1,"refused debit leaves the disk key intact");
        engine.openShulkerCaseAnimated(player,money);
        Field f=SpawnEngine.class.getDeclaredField("rng");f.setAccessible(true);original=(Random)f.get(engine);
        later(50,()->{try{replaceRandom(new Random(){@Override public int nextInt(int bound){throw new IllegalStateException("injected prize error");}});}catch(Exception e){throw new RuntimeException(e);}});
        later(75,()->failedPrize(before));
    }catch(Throwable t){fail(t);}}
    private void failedPrize(double before){try{
        replaceRandom(original);original=null;
        ok(store.getCaseKeys(player.getName(),"money")==1 && store.getCoins(player.getName())==before && displays()==0,
                "exception before prize restores key and clears animation");
        DataStore disk=new DataStore(core);ok(disk.getCaseKeys(player.getName(),"money")==1,"refunded key persisted");
        engine.openShulkerCaseAnimated(player,money);later(10,()->player.kickPlayer("case disconnect test"));
        later(35,()->{try{ok(store.getCaseKeys(player.getName(),"money")==1 && displays()==0,"disconnect cancels animation without consuming key");finish();}catch(Exception e){fail(e);}});
    }catch(Throwable t){try{if(original!=null)replaceRandom(original);}catch(Exception ignored){}fail(t);}}
}
