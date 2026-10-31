package net.kinggrief.probe;

import net.kinggrief.core.DataStore;
import net.kinggrief.core.GuiManager;
import net.kinggrief.core.KingGriefCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Real two-player GUI plus restart, storage fault, stale slot, paging and refunds. */
public final class CoinflipProbe extends JavaPlugin implements Listener {
    private KingGriefCore core; private DataStore store; private Player buyer,seller; private Path data,saved;
    private int checks, errors, clicks, expected; private boolean started,finished;
    @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);}
    @EventHandler public void onJoin(PlayerJoinEvent e){if(!started && Bukkit.getOnlinePlayers().size()>=2){started=true;later(35,this::begin);}}
    @EventHandler(priority=EventPriority.MONITOR) public void onClick(InventoryClickEvent e){if(buyer!=null&&e.getWhoClicked().equals(buyer))clicks++;}
    private void later(long ticks,Runnable run){Bukkit.getScheduler().runTaskLater(this,()->{try{run.run();}catch(Throwable e){fail(e);}},ticks);}
    private void ok(boolean value,String label){checks++;if(value)getLogger().info("KG-CF PASS "+label);else{errors++;getLogger().severe("KG-CF FAIL "+label);}}
    private void fail(Throwable e){errors++;getLogger().severe("KG-CF EXCEPTION "+e);e.printStackTrace();restore();finish();}
    private void finish(){if(finished)return;finished=true;restore();getLogger().info("KG-CF RESULT "+(errors==0?"PASS":"FAIL")+" "+getConfig().getString("mode")+" checks="+checks+" errors="+errors);Bukkit.shutdown();}
    private void link(){try{Files.move(data,saved);Files.createSymbolicLink(data,saved.getFileName());}catch(Exception e){throw new RuntimeException(e);}}
    private void restore(){try{if(data!=null && Files.isSymbolicLink(data)){Files.delete(data);Files.move(saved,data);}}catch(Exception e){errors++;getLogger().severe("restore error "+e);}}
    private void click(int slot,Runnable after){expected=clicks+1;later(8,()->{getLogger().info("KG-CF REQUEST CLICK "+slot);later(20,()->{ok(clicks>=expected,"protocol click delivered at "+slot);after.run();});});}
    private void begin(){
        core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");store=core.getStore();
        buyer=Bukkit.getPlayerExact("EcoBuyer");seller=Bukkit.getPlayerExact("EcoSeller");
        if(buyer==null||seller==null)throw new AssertionError("protocol players absent");
        data=core.getDataFolder().toPath().resolve("data.yml");saved=data.resolveSibling("cf-probe-save.yml");
        if("prepare".equals(getConfig().getString("mode"))){prepare();return;}
        verify();
    }
    private void prepare(){
        store.setCoins(seller.getName(),100_000);store.setCoins(buyer.getName(),200_000);store.save();
        seller.performCommand("cf 10000");
        ok(store.getCoinflips().size()==1 && store.getCoins(seller.getName())==90_000,"creation atomically records room and escrow debit");
        DataStore disk=new DataStore(core);
        ok(disk.getCoinflips().size()==1 && disk.getCoins(seller.getName())==90_000,"room and debit are durable before server restart");
        link();seller.performCommand("cf 1000");restore();
        ok(store.getCoinflips().size()==1 && store.getCoins(seller.getName())==90_000,"failed creation save leaves no second room or debit");
        finish();
    }
    private void verify(){
        ok(store.getCoinflips().size()==1 && store.getCoins(seller.getName())==90_000 && store.getCoins(buyer.getName())==200_000,"pending game and balances survive full restart");
        buyer.performCommand("casino");link();click(30,()->{
            restore();
            ok(store.getCoinflips().size()==1 && store.getCoins(seller.getName())==90_000 && store.getCoins(buyer.getName())==200_000,"failed settlement save leaves escrow, game and balances intact");
            buyer.performCommand("casino");click(30,this::afterFirstSettlement);
        });
    }
    private void afterFirstSettlement(){
        ok(store.getCoinflips().isEmpty() && store.getCoins(seller.getName())+store.getCoins(buyer.getName())==300_000,"accepted game pays escrow once and conserves total money");
        DataStore disk=new DataStore(core);
        ok(disk.getCoinflips().isEmpty() && disk.getCoins(seller.getName())==store.getCoins(seller.getName()) && disk.getCoins(buyer.getName())==store.getCoins(buyer.getName()),"completed settlement is durable and room cannot replay");
        double before=store.getCoins(buyer.getName());buyer.performCommand("cf 15000");
        ok(store.getCoinflips().size()==1 && store.getCoins(buyer.getName())==before-15000,"own game holds stake");
        buyer.performCommand("casino");click(30,()->{
            ok(store.getCoinflips().isEmpty() && store.getCoins(buyer.getName())==before,"clicking own game cancels and refunds escrow");
            seller.performCommand("cf 1000");seller.performCommand("cf 2000");
            List<DataStore.CoinflipRoom> rooms=store.getCoinflips();
            if(rooms.size()!=2)throw new AssertionError("could not create stale fixture");
            int removed=rooms.get(0).id, older=rooms.get(1).id;
            buyer.performCommand("casino");
            DataStore.CoinflipSettlement cancelled=store.settleCoinflip(removed,seller.getName(),true);
            if(cancelled.outcome!=DataStore.CoinflipOutcome.CANCELLED)throw new AssertionError("fixture cancellation failed");
            double b=store.getCoins(buyer.getName()),s=store.getCoins(seller.getName());
            click(30,()->{
                ok(store.getCoinflips().size()==1 && store.getCoinflips().get(0).id==older && store.getCoins(buyer.getName())==b && store.getCoins(seller.getName())==s,"stale GUI click cannot accept a different game after list shifts");
                click(30,()->{ok(store.getCoinflips().isEmpty(),"refreshed GUI accepts the intended remaining game");pagination();});
            });
        });
    }
    private void pagination(){
        for(int i=0;i<4;i++)seller.performCommand("cf 1000");
        double before=store.getCoins(seller.getName());seller.performCommand("cf 1000");
        ok(store.getCoinflips().size()==4 && store.getCoins(seller.getName())==before,"per-player room limit prevents unbounded escrow entries");
        buyer.performCommand("cf 1000");buyer.performCommand("cf 1000");
        ok(store.getCoinflips().size()==6,"six rooms created for paging");
        int oldest=store.getCoinflips().get(5).id;
        buyer.performCommand("casino");click(35,()->{
            GuiManager.KgMenuHolder holder=(GuiManager.KgMenuHolder)buyer.getOpenInventory().getTopInventory().getHolder();
            ok(holder.page==1,"casino next-page control reaches older games");
            click(30,()->{
                boolean present=false;for(DataStore.CoinflipRoom room:store.getCoinflips())if(room.id==oldest)present=true;
                ok(!present && store.getCoinflips().size()==5,"second page accepts its displayed stable room ID");
                for(DataStore.CoinflipRoom room:new ArrayList<DataStore.CoinflipRoom>(store.getCoinflips()))
                    if(store.settleCoinflip(room.id,room.creator,false).outcome!=DataStore.CoinflipOutcome.CANCELLED)throw new AssertionError("cleanup refund failed");
                ok(store.getCoinflips().isEmpty(),"all remaining escrows can be refunded");finish();
            });
        });
    }
}
