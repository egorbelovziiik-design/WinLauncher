package net.kinggrief.probe;

import net.kinggrief.core.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.util.*;

/** Real Paper command paths, two protocol players, stale GUI, and failed durable commits. */
public final class EconomyProbe extends JavaPlugin implements Listener {
    private KingGriefCore core;
    private DataStore store;
    private Player seller, buyer;
    private Path data, backup;
    private int checks, errors, oldId, goldId;
    private boolean running, finished, cancelNextClick;

    @Override public void onEnable() { Bukkit.getPluginManager().registerEvents(this, this); }
    @EventHandler public void joined(PlayerJoinEvent e) {
        if (!running && Bukkit.getOnlinePlayers().size() >= 2) { running = true; later(40, this::begin); }
    }
    @EventHandler(priority=EventPriority.LOWEST) public void cancelClick(InventoryClickEvent e) {
        if (cancelNextClick && e.getWhoClicked().equals(buyer)) { e.setCancelled(true); cancelNextClick=false; }
    }
    private void later(long ticks, Runnable fn) { Bukkit.getScheduler().runTaskLater(this, () -> { try { fn.run(); } catch(Throwable t) { fail(t); } }, ticks); }
    private void ok(boolean value, String description) { checks++; if(!value) { errors++; getLogger().severe("KG-ECO FAIL: "+description); } }
    private void fail(Throwable t) { errors++;getLogger().severe("KG-ECO EXCEPTION: "+t);t.printStackTrace();restore();finish(); }
    private void finish() { if(finished)return;finished=true;restore();getLogger().info("KG-ECO RESULT "+(errors==0?"PASS":"FAIL")+" checks="+checks+" errors="+errors); }
    private void requestClick() { requestClick(0); }
    private void requestClick(int slot) { getLogger().info("KG-ECO REQUEST CLICK " + slot); }
    private void link() throws Exception {
        if (!Files.exists(data) || Files.exists(backup)) throw new IllegalStateException("invalid fixture for data link");
        Files.move(data,backup);Files.createSymbolicLink(data,backup.getFileName());
    }
    private void restore() {
        try { if(data!=null && Files.isSymbolicLink(data)) { Files.delete(data);Files.move(backup,data); } }
        catch(Exception e){errors++;getLogger().severe("KG-ECO restore failed "+e);}
    }
    private int count(Material m) { int n=0;for(ItemStack i:buyer.getInventory().getStorageContents())if(i!=null&&i.getType()==m)n+=i.getAmount();return n; }
    private void begin() {
        try {
            core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");store=core.getStore();
            seller=Bukkit.getPlayerExact("EcoSeller");buyer=Bukkit.getPlayerExact("EcoBuyer");
            if(seller==null||buyer==null)throw new IllegalStateException("protocol players absent");
            data=core.getDataFolder().toPath().resolve("data.yml");backup=data.resolveSibling("data-probe-backup.yml");
            seller.getInventory().clear();buyer.getInventory().clear();seller.setGameMode(GameMode.SURVIVAL);buyer.setGameMode(GameMode.SURVIVAL);
            store.setCoins(seller.getName(),10000);store.setCoins(buyer.getName(),1000);store.save();
            seller.performCommand("pay EcoBuyer NaN");seller.performCommand("pay EcoBuyer Infinity");seller.performCommand("pay EcoBuyer -100");
            ok(store.getCoins(seller.getName())==10000 && store.getCoins(buyer.getName())==1000,"invalid transfer values leave both balances intact");
            ok(!store.takeCoins(seller.getName(),Double.NaN) && !store.takeCoins(seller.getName(),-2),"store rejects non-finite and negative debits");
            store.setCoins(seller.getName(),Double.POSITIVE_INFINITY);store.addCoins(seller.getName(),Double.NaN);
            ok(store.getCoins(seller.getName())==10000,"invalid direct balance updates are refused");
            link();seller.performCommand("pay EcoBuyer 1000");restore();
            ok(store.getCoins(seller.getName())==10000 && store.getCoins(buyer.getName())==1000,"failed durable transfer rolls back both accounts");
            seller.performCommand("pay EcoBuyer 1000");
            ok(store.getCoins(seller.getName())==9000 && store.getCoins(buyer.getName())==2000,"valid transfer debits and credits exactly once");
            DataStore disk=new DataStore(core);
            ok(disk.getCoins(seller.getName())==9000 && disk.getCoins(buyer.getName())==2000,"successful transfer persisted both sides");
            store.addCaseKeys(seller.getName(),"eco_probe",Integer.MAX_VALUE);store.addCaseKeys(seller.getName(),"eco_probe",1);
            store.addSnowflakes(seller.getName(),Integer.MAX_VALUE);store.addSnowflakes(seller.getName(),1);
            store.setRelics(seller.getName(),Integer.MAX_VALUE);store.addRelics(seller.getName(),1);
            ok(store.getCaseKeys(seller.getName(),"eco_probe")==Integer.MAX_VALUE && store.getSnowflakes(seller.getName())==Integer.MAX_VALUE
                    && store.getRelics(seller)==Integer.MAX_VALUE,"integer balances saturate instead of overflowing to zero");
            seller.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND,3));
            int initial=store.getAuctionLots().size();
            seller.performCommand("ah sell NaN");seller.performCommand("ah sell Infinity");
            ok(seller.getInventory().getItemInMainHand().getAmount()==3 && store.getAuctionLots().size()==initial,"invalid listing prices do not consume the item");
            link();seller.performCommand("ah sell 500");restore();
            ok(seller.getInventory().getItemInMainHand().getAmount()==3 && store.getAuctionLots().size()==initial,"failed listing save retains item and removes temporary lot");
            seller.performCommand("ah sell 500");oldId=store.getAuctionLots().get(0).id;
            ok(seller.getInventory().getItemInMainHand().getType().isAir(),"successful listing consumes exactly the held item");
            buyer.performCommand("ah");
            Map<Integer,ItemStack> dragged = new HashMap<Integer,ItemStack>(); dragged.put(Integer.valueOf(0), new ItemStack(Material.DIRT));
            InventoryDragEvent drag = new InventoryDragEvent(buyer.getOpenInventory(), new ItemStack(Material.DIRT), new ItemStack(Material.DIRT), false, dragged);
            Bukkit.getPluginManager().callEvent(drag);
            ok(drag.isCancelled(), "drag into an auction preview is refused");
            later(20,()->{
                seller.getInventory().setItemInMainHand(new ItemStack(Material.GOLD_INGOT,2));seller.performCommand("ah sell 800");
                goldId=store.getAuctionLots().get(0).id;
                ok(goldId!=oldId,"new listing has a distinct id");
                requestClick();later(30,this::afterStaleClick);
            });
        } catch(Throwable t){fail(t);}
    }
    private void afterStaleClick() {
        try {
            ok(count(Material.DIAMOND)==3 && count(Material.GOLD_INGOT)==0,"stale displayed slot buys the shown diamond, not the newly inserted lot");
            ok(store.findAuctionLot(oldId)==null && store.findAuctionLot(goldId)!=null,"only displayed lot removed");
            ok(store.getCoins(seller.getName())==9500 && store.getCoins(buyer.getName())==1500,"auction debits and credits exactly once");
            DataStore disk=new DataStore(core);
            ok(disk.findAuctionLot(oldId)==null && disk.findAuctionLot(goldId)!=null && disk.getCoins(seller.getName())==9500
                    && disk.getCoins(buyer.getName())==1500,"auction transfer and lot removal persisted together");
            ok(store.purchaseAuctionLot(oldId,buyer.getName())==null && store.getCoins(buyer.getName())==1500,"replayed sold lot cannot debit twice");
            buyer.performCommand("ah");cancelNextClick=true;later(20,()->{requestClick();later(25,this::afterCancelledClick);});
        }catch(Throwable t){fail(t);}
    }
    private void afterCancelledClick() {
        ok(!cancelNextClick && store.findAuctionLot(goldId)!=null && store.getCoins(buyer.getName())==1500,"earlier plugin cancellation prevents auction action");
        buyer.getInventory().clear();for(int i=0;i<36;i++)buyer.getInventory().setItem(i,new ItemStack(Material.COBBLESTONE,64));
        later(20,()->{requestClick();later(25,this::afterFullInventory);});
    }
    private void afterFullInventory() {
        ok(store.findAuctionLot(goldId)!=null && store.getCoins(buyer.getName())==1500,"full inventory cannot buy or lose item");
        buyer.getInventory().clear();
        try{link();}catch(Exception e){fail(e);return;}
        later(20,()->{requestClick();later(25,this::afterFailedPurchase);});
    }
    private void afterFailedPurchase() {
        restore();
        ok(store.findAuctionLot(goldId)!=null && store.getCoins(buyer.getName())==1500 && store.getCoins(seller.getName())==9500
                && count(Material.GOLD_INGOT)==0,"failed auction commit restores lot and both balances, without item delivery");
        later(20,()->{requestClick();later(25,this::afterFinalPurchase);});
    }
    private void afterFinalPurchase() {
        ok(store.findAuctionLot(goldId)==null && count(Material.GOLD_INGOT)==2 && store.getCoins(buyer.getName())==700
                && store.getCoins(seller.getName())==10300,"valid purchase after failures delivers exact item and payment");
        for(int i=0;i<46;i++) if(store.addAuctionLot("SyntheticSeller",new ItemStack(Material.DIRT),10+i)==null) throw new IllegalStateException("cannot seed pages");
        buyer.performCommand("ah");
        ok(buyer.getOpenInventory().getTopInventory().getItem(53).getType()==Material.ARROW,"next-page arrow appears for more than 45 lots");
        later(20,()->{requestClick(53);later(25,this::afterNextPage);});
    }
    private void afterNextPage() {
        GuiManager.KgMenuHolder h=(GuiManager.KgMenuHolder)buyer.getOpenInventory().getTopInventory().getHolder();
        ok(h.page==1 && buyer.getOpenInventory().getTopInventory().getItem(45).getType()==Material.ARROW,"next page opens with previous-page arrow");
        later(20,()->{requestClick(45);later(25,()->{
            GuiManager.KgMenuHolder back=(GuiManager.KgMenuHolder)buyer.getOpenInventory().getTopInventory().getHolder();
            ok(back.page==0,"previous-page arrow returns to page zero");finish();
        });});
    }
}
