package net.kinggrief.probe;

import net.kinggrief.core.DataStore;
import net.kinggrief.core.KingGriefCore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.Files;
import java.nio.file.Path;

/** Actual protocol GUI clicks plus failure, replay and restart-safe promo assertions. */
public final class ShopPromoProbe extends JavaPlugin implements Listener {
    private KingGriefCore core; private DataStore store; private Player buyer, other;
    private Path data, saved; private int checks, errors, clickCount, expectedClicks; private boolean started, finished;
    @Override public void onEnable() { Bukkit.getPluginManager().registerEvents(this, this); }
    @EventHandler public void join(PlayerJoinEvent e) {
        if (!started && Bukkit.getOnlinePlayers().size() >= 2) { started = true; later(40, this::begin); }
    }
    @EventHandler(priority=EventPriority.MONITOR) public void observedClick(InventoryClickEvent e) {
        if (buyer != null && e.getWhoClicked().equals(buyer)) clickCount++;
    }
    private void later(long ticks, Runnable run) {
        Bukkit.getScheduler().runTaskLater(this, () -> { try { run.run(); } catch (Throwable e) { fail(e); } }, ticks);
    }
    private void ok(boolean value, String text) {
        checks++;
        if (value) getLogger().info("KG-SHOP PASS " + text);
        else { errors++; getLogger().severe("KG-SHOP FAIL " + text); }
    }
    private void fail(Throwable e) { errors++; getLogger().severe("KG-SHOP EXCEPTION " + e); e.printStackTrace(); restore(); finish(); }
    private void finish() { if(finished)return; finished=true; restore(); getLogger().info("KG-SHOP RESULT " + (errors==0?"PASS":"FAIL") + " checks=" + checks + " errors=" + errors); }
    private void link() throws Exception { Files.move(data,saved); Files.createSymbolicLink(data,saved.getFileName()); }
    private void restore() { try { if(data!=null && Files.isSymbolicLink(data)) { Files.delete(data); Files.move(saved,data); } } catch(Exception e) { errors++; getLogger().severe("restore error " + e); } }
    private void click(int slot, Runnable after) {
        expectedClicks=clickCount+1;
        later(8, () -> { getLogger().info("KG-SHOP REQUEST CLICK " + slot); later(20, () -> { ok(clickCount>=expectedClicks,"actual inventory click reached server at slot "+slot); after.run(); }); });
    }
    private void full() { for(int i=0;i<36;i++) buyer.getInventory().setItem(i,new ItemStack(Material.DIRT,64)); }
    private int quantity(Material material) { int n=0; for(ItemStack i:buyer.getInventory().getStorageContents()) if(i!=null && i.getType()==material)n+=i.getAmount();return n; }
    private void begin() {
        core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore"); store=core.getStore();
        buyer=Bukkit.getPlayerExact("EcoBuyer");other=Bukkit.getPlayerExact("EcoSeller");
        if(buyer==null || other==null)throw new IllegalStateException("protocol players missing");
        buyer.setGameMode(GameMode.SURVIVAL);buyer.getInventory().clear();store.setCoins(buyer.getName(),1_000_000);store.setRelics(buyer.getName(),2_000);store.addSnowflakes(buyer.getName(),500);store.save();
        data=core.getDataFolder().toPath().resolve("data.yml");saved=data.resolveSibling("shop-probe-saved.yml");
        full();buyer.performCommand("rshop");click(10, this::afterRelicFull);
    }
    private void afterRelicFull() {
        ok(store.getRelics(buyer)==2000,"full inventory rejects relic shop without charge");
        buyer.performCommand("bm");click(11,this::afterBlackMarketFull);
    }
    private void afterBlackMarketFull() {
        ok(store.getCoins(buyer.getName())==1_000_000,"full inventory rejects black-market purchase");
        buyer.performCommand("shop food");click(10,this::afterCategoryFull);
    }
    private void afterCategoryFull() {
        ok(store.getCoins(buyer.getName())==1_000_000,"full inventory rejects category shop purchase");
        buyer.performCommand("quests");click(11,this::afterSnowFull);
    }
    private void afterSnowFull() {
        ok(store.getSnowflakes(buyer.getName())==500,"full inventory rejects snowflake item exchange");
        buyer.performCommand("casino");click(16,this::afterCasinoFull);
    }
    private void afterCasinoFull() {
        ok(store.getRelics(buyer)==2000,"relic roulette checks jackpot space before charge and roll");
        buyer.performCommand("promo SANTA");
        ok(!store.hasUsedPromo(buyer.getName(),"SANTA") && store.getCoins(buyer.getName())==1_000_000,"full inventory leaves item promo unused and balance unchanged");
        buyer.getInventory().setItem(0,null);buyer.getInventory().setItem(1,null);
        buyer.performCommand("promo SANTA");
        ok(store.hasUsedPromo(buyer.getName(),"SANTA") && store.getCoins(buyer.getName())==1_015_000,"item promo commits once when both rewards fit");
        ok(quantity(Material.POTION)>0,"item promo delivered a potion");
        buyer.performCommand("promo SANTA");
        ok(store.getCoins(buyer.getName())==1_015_000,"item promo replay refused");
        buyer.getInventory().clear(); buyer.performCommand("bm");
        try { link(); } catch(Exception e) { throw new RuntimeException(e); }
        click(11,this::afterFailedCoinSave);
    }
    private void afterFailedCoinSave() {
        restore();
        ok(store.getCoins(buyer.getName())==1_015_000 && quantity(Material.GOLDEN_PICKAXE)==0,"coin purchase refuses a failed durable save and gives no item");
        buyer.performCommand("bm");click(11,this::afterCoinSuccess);
    }
    private void afterCoinSuccess() {
        ok(store.getCoins(buyer.getName())==835_000 && quantity(Material.GOLDEN_PICKAXE)==1,"black-market purchase charges once and gives item");
        buyer.getInventory().clear();buyer.performCommand("quests");
        try { link(); } catch(Exception e) { throw new RuntimeException(e); }
        click(11,this::afterFailedSnowSave);
    }
    private void afterFailedSnowSave() {
        restore();
        ok(store.getSnowflakes(buyer.getName())==500 && quantity(Material.GOLDEN_APPLE)==0,"snowflake item exchange refuses failed save");
        buyer.performCommand("quests");click(11,this::afterSnowSuccess);
    }
    private void afterSnowSuccess() {
        ok(store.getSnowflakes(buyer.getName())==490,"successful snowflake item exchange debits exactly once");
        store.syncServerPromo("FIX72",1,12345,0,"summer",2,"");
        double before=store.getCoins(buyer.getName()); int beforeKeys=store.getCaseKeys(buyer.getName(),"summer");
        try { link(); buyer.performCommand("promo FIX72"); restore(); } catch(Exception e) { throw new RuntimeException(e); }
        ok(!store.hasUsedPromo(buyer.getName(),"FIX72") && store.getCoins(buyer.getName())==before && store.getCaseKeys(buyer.getName(),"summer")==beforeKeys && store.getServerPromo("FIX72").usedCount==0,"failed promo save rolls back rewards, per-player marker and global use count");
        buyer.performCommand("promo FIX72");
        ok(store.hasUsedPromo(buyer.getName(),"FIX72") && store.getCoins(buyer.getName())==before+12345 && store.getCaseKeys(buyer.getName(),"summer")==beforeKeys+2 && store.getServerPromo("FIX72").usedCount==1,"server promo commits all rewards and both use markers once");
        buyer.performCommand("promo FIX72");other.performCommand("promo FIX72");
        ok(store.getCoins(buyer.getName())==before+12345 && !store.hasUsedPromo(other.getName(),"FIX72") && store.getServerPromo("FIX72").usedCount==1,"replay and second player beyond global limit refused");
        DataStore disk=new DataStore(core);
        ok(disk.hasUsedPromo(buyer.getName(),"FIX72") && disk.getServerPromo("FIX72").usedCount==1 && disk.getCaseKeys(buyer.getName(),"summer")==beforeKeys+2,"promo rewards and markers survive reloading data.yml");
        testPoints();
    }
    private void mode(Plugin points,String value) { try { points.getClass().getMethod("mode",String.class).invoke(points,value); } catch(Exception e) { throw new RuntimeException(e); } }
    private void testPoints() {
        Plugin points=Bukkit.getPluginManager().getPlugin("PlayerPoints");
        if(points==null) { finish();return; }
        int before=store.getRelics(buyer),snow=store.getSnowflakes(buyer.getName());
        mode(points,"reject");buyer.performCommand("quests");click(13,()->{
            ok(store.getSnowflakes(buyer.getName())==snow && store.getRelics(buyer)==before,"rejected PlayerPoints exchange leaves both balances unchanged");
            mode(points,"normal");buyer.performCommand("quests");click(13,()->{
                ok(store.getSnowflakes(buyer.getName())==snow-35 && store.getRelics(buyer)==before+50,"successful PlayerPoints snow exchange updates both balances");
                store.syncServerPromo("POINTS72",1,100,20,"",0,"");
                mode(points,"reject");double coins=store.getCoins(buyer.getName());buyer.performCommand("promo POINTS72");
                ok(!store.hasUsedPromo(buyer.getName(),"POINTS72") && store.getCoins(buyer.getName())==coins && store.getServerPromo("POINTS72").usedCount==0,"rejected PlayerPoints promo commits nothing locally");
                mode(points,"normal");buyer.performCommand("promo POINTS72");
                ok(store.hasUsedPromo(buyer.getName(),"POINTS72") && store.getRelics(buyer)==before+70 && store.getServerPromo("POINTS72").usedCount==1,"accepted PlayerPoints promo grants correct balance and marks claim");
                finish();
            });
        });
    }
}
