package net.kinggrief.probe;

import net.kinggrief.core.BossEventManager;
import net.kinggrief.core.ItemFactory;
import net.kinggrief.core.KingGriefCore;
import net.kinggrief.core.SafeTradeManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/** Assertions run on a disposable real Paper server with two protocol clients. */
public final class NewFeaturesProbe extends JavaPlugin implements Listener {
    private KingGriefCore core;
    private Player a, b;
    private boolean running, cancelHit;
    private int checks;

    @Override public void onEnable() {
        core = (KingGriefCore) Bukkit.getPluginManager().getPlugin("KingGriefCore");
        Bukkit.getPluginManager().registerEvents(this, this);
    }
    @EventHandler public void join(PlayerJoinEvent e) {
        if ("OrbTester".equals(e.getPlayer().getName())) a=e.getPlayer();
        if ("OrbEnemy".equals(e.getPlayer().getName())) b=e.getPlayer();
        if (a!=null && b!=null && !running) { running=true; later(30, () -> begin()); }
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void protect(EntityDamageByEntityEvent e) {
        if (cancelHit && e.getEntity() instanceof Player && e.getDamager() instanceof Player) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void observeDamage(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player && e.getDamager() instanceof Player) {
            getLogger().info("KG-NEW DAMAGE cancelled=" + e.isCancelled() + " final=" + e.getFinalDamage());
        }
    }
    private void ok(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
        getLogger().info("KG-NEW PASS " + message);
    }
    private void later(long ticks, Runnable action) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { action.run(); } catch (Throwable error) { fail(error); }
        }, ticks);
    }
    private void fail(Throwable error) {
        getLogger().severe("KG-NEW RESULT FAIL " + error);
        error.printStackTrace();
        Bukkit.getScheduler().runTaskLater(this, () -> Bukkit.shutdown(), 10);
    }
    private Field field(Object o, String name) throws Exception {
        Field f=o.getClass().getDeclaredField(name); f.setAccessible(true); return f;
    }
    private Object invoke(Object o, String name) throws Exception {
        Method m=o.getClass().getDeclaredMethod(name); m.setAccessible(true); return m.invoke(o);
    }
    private void begin() {
        try {
            airship();
            World w=Bukkit.getWorld("spawn_world");
            w.setPVP(true);
            a.teleport(new Location(w,75.5,94,10.5)); b.teleport(new Location(w,77.5,94,10.5));
            a.setGameMode(GameMode.SURVIVAL); b.setGameMode(GameMode.SURVIVAL);
            // Teleport/join invulnerability and chunk tracking settle on real ticks.
            later(40, () -> {
                try { crown(); trade(); } catch (Exception error) { throw new RuntimeException(error); }
            });
        } catch (Exception error) { throw new RuntimeException(error); }
    }
    private void airship() throws Exception {
        WorldCreator wc=new WorldCreator("kg_new_fixture");
        wc.generator(new ChunkGenerator() {
            @Override public ChunkData generateChunkData(World world, Random rng, int x,int z,BiomeGrid biome) {
                ChunkData d=createChunkData(world); d.setRegion(0,60,0,16,64,16,Material.DIRT);
                d.setRegion(0,64,0,16,65,16,Material.GRASS_BLOCK);
                for(int i=0;i<16;i++) for(int j=0;j<16;j++) biome.setBiome(i,j,org.bukkit.block.Biome.PLAINS);
                return d;
            }
            @Override public boolean shouldGenerateNoise(){return false;}
            @Override public boolean shouldGenerateSurface(){return false;}
            @Override public boolean shouldGenerateCaves(){return false;}
            @Override public boolean shouldGenerateDecorations(){return false;}
            @Override public boolean shouldGenerateMobs(){return false;}
            @Override public boolean shouldGenerateStructures(){return false;}
        });
        wc.generateStructures(false);
        World w=Bukkit.createWorld(wc);
        int x=422,z=422;
        for(int cx=(x-9)>>4;cx<=(x+9)>>4;cx++) for(int cz=(z-9)>>4;cz<=(z+9)>>4;cz++) w.getChunkAt(cx,cz).load();
        Location center=new Location(w,x,65,z);
        BossEventManager events=core.getBossEvents();
        BossEventManager.ActiveBossEvent ev=events.spawnBossEvent("airship",center);
        ok(ev!=null && ev.airshipParts.size()==3 && events.isLockedEventChest(center.getBlock()), "airship spawned with three distinct parts and locked cargo");
        List<Location> parts=new ArrayList<Location>(ev.airshipParts);
        for(Location part:parts) ok(part.getBlock().getType()==Material.COPPER_BLOCK && events.isProtectedEventStructureBlock(part.getBlock(),false), "airship scrap is visible and protected");
        Block block=parts.get(0).getBlock();
        org.bukkit.event.block.BlockPistonExtendEvent piston=new org.bukkit.event.block.BlockPistonExtendEvent(
                block.getRelative(BlockFace.WEST),java.util.Collections.singletonList(block),BlockFace.EAST);
        Bukkit.getPluginManager().callEvent(piston);
        ok(piston.isCancelled(),"pistons cannot move event structure blocks");
        org.bukkit.event.block.BlockBurnEvent burn=new org.bukkit.event.block.BlockBurnEvent(block);
        Bukkit.getPluginManager().callEvent(burn);
        ok(burn.isCancelled(),"event structure blocks cannot burn");
        PlayerInteractEvent denied=new PlayerInteractEvent(a,Action.RIGHT_CLICK_BLOCK,null,block,BlockFace.UP,EquipmentSlot.HAND);
        denied.setCancelled(true); Bukkit.getPluginManager().callEvent(denied);
        ok(ev.airshipParts.size()==3,"cancelled protection interaction cannot collect a part");
        for(int i=0;i<3;i++) {
            PlayerInteractEvent click=new PlayerInteractEvent(a,Action.RIGHT_CLICK_BLOCK,null,parts.get(i).getBlock(),BlockFace.UP,EquipmentSlot.HAND);
            Bukkit.getPluginManager().callEvent(click);
            ok(click.isCancelled() && ev.airshipParts.size()==2-i,"part collected exactly once " + (i+1));
            if(i==0) ok(!events.collectAirshipPart(a,parts.get(0).getBlock()) && ev.airshipParts.size()==2,"same part cannot be collected twice");
        }
        ok(ev.bossDefeated && center.getBlock().getState() instanceof Chest,"all parts open the actual cargo chest");
        ok(((Chest)center.getBlock().getState()).getBlockInventory().getItem(4)!=null,"cargo contains actual custom loot");
        ev.secondsLeft=1;
        invoke(events,"tickSecond");
        ok(!events.getActiveEvents().contains(ev) && center.getBlock().getType()==Material.AIR,"finished airship restores terrain and chest");
        for(Location part:parts) ok(part.getBlock().getType()==Material.AIR && part.clone().add(0,-1,0).getBlock().getType()==Material.GRASS_BLOCK,"scrap and foundation restored");
        BossEventManager.ActiveBossEvent timeout=events.spawnBossEvent("airship",center);
        timeout.unlockCountdownSec=1; timeout.secondsLeft=20;
        invoke(events,"tickSecond");
        ok(!events.getActiveEvents().contains(timeout) && center.getBlock().getType()==Material.AIR,"timeout closes without free cargo");
    }
    private void crown() throws Exception {
        a.setHealth(20); b.setHealth(20); a.setNoDamageTicks(0); b.setNoDamageTicks(0);
        core.getConfig().set("crown-event.auto-enabled",false);
        core.getConfig().set("crown-event.lobby-seconds",10);
        core.getConfig().set("crown-event.goal-seconds",10);
        core.getConfig().set("crown-event.match-seconds",30);
        Object mgr=field(core,"crownEvents").get(core);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"crown start");
        core.getStore().toggleGodMode(a);
        a.performCommand("crown join");
        Set<?> members=(Set<?>)field(mgr,"players").get(mgr);
        ok(!members.contains(a.getUniqueId()),"crown rejects invulnerable god mode");
        core.getStore().disableGodMode(a);
        a.performCommand("crown join"); b.performCommand("crown join");
        ok(members.size()==2,"crown opt-in requires both eligible arena participants");
        for(int i=0;i<10;i++) invoke(mgr,"tickSecond");
        UUID initial=(UUID)field(mgr,"holder").get(mgr);
        ok(initial!=null && "RUNNING".equals(field(mgr,"phase").get(mgr).toString()),"crown starts after lobby");
        Player victim=initial.equals(a.getUniqueId())?a:b, attacker=victim==a?b:a;
        cancelHit=true; victim.damage(2.0,attacker); cancelHit=false;
        ok(initial.equals(field(mgr,"holder").get(mgr)),"cancelled PvP hit does not transfer crown");
        victim.setNoDamageTicks(0); victim.damage(2.0,attacker);
        ok(attacker.getUniqueId().equals(field(mgr,"holder").get(mgr)),"real accepted player damage transfers crown");
        double old=core.getStore().getCoins(attacker.getName());
        int relics=core.getStore().getRelics(attacker.getName());
        for(int i=0;i<10;i++) invoke(mgr,"tickSecond");
        ok("IDLE".equals(field(mgr,"phase").get(mgr).toString()),"crown round ends at target score");
        ok(core.getStore().getCoins(attacker.getName())==old+50000.0 && core.getStore().getRelics(attacker.getName())==relics+20,"one crown reward credited");
        invoke(mgr,"tickSecond");
        ok(core.getStore().getCoins(attacker.getName())==old+50000.0,"crown reward not paid twice");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"crown start");
        a.performCommand("crown join"); b.performCommand("crown join");
        for(int i=0;i<10;i++) invoke(mgr,"tickSecond");
        b.performCommand("crown leave"); invoke(mgr,"tickSecond");
        ok("IDLE".equals(field(mgr,"phase").get(mgr).toString()) && core.getStore().getCoins(attacker.getName())==old+50000.0,"uncontested crown round cancels without reward");
    }
    private InventoryClickEvent click(Player p,int raw,ClickType type,InventoryAction action) {
        InventoryClickEvent event=new InventoryClickEvent(p.getOpenInventory(),InventoryType.SlotType.CONTAINER,raw,type,action);
        Bukkit.getPluginManager().callEvent(event); return event;
    }
    private void trade() {
        a.getInventory().clear(); b.getInventory().clear();
        ItemStack santa=ItemFactory.sharSanty();
        ItemMeta meta=santa.getItemMeta(); meta.getPersistentDataContainer().set(new NamespacedKey(this,"token"),PersistentDataType.STRING,"уникальный-тег");
        meta.setLore(Arrays.asList("§6Не терять свойства", "§fстрока 2")); santa.setItemMeta(meta);
        ItemStack diamond=new ItemStack(Material.DIAMOND,12);
        a.getInventory().setItem(0,santa); b.getInventory().setItem(0,diamond);
        a.performCommand("trade " + b.getName()); b.performCommand("trade accept " + a.getName());
        ok(a.getOpenInventory().getTopInventory().getHolder() instanceof SafeTradeManager.TradeHolder,"trade invitation opens protected previews");
        ok(click(a,81,ClickType.LEFT,InventoryAction.PICKUP_ALL).isCancelled(),"adding first offer cancels vanilla movement");
        ok(click(b,81,ClickType.LEFT,InventoryAction.PICKUP_ALL).isCancelled(),"adding second offer cancels vanilla movement");
        ok(santa.equals(a.getInventory().getItem(0)) && diamond.equals(b.getInventory().getItem(0)),"previews never remove actual items");
        InventoryClickEvent number=click(a,9,ClickType.NUMBER_KEY,InventoryAction.HOTBAR_SWAP);
        ok(number.isCancelled() && santa.equals(a.getInventory().getItem(0)),"number-key bypass blocked");
        Map<Integer,ItemStack> dragged=new HashMap<Integer,ItemStack>(); dragged.put(9,new ItemStack(Material.DIAMOND));
        InventoryDragEvent drag=new InventoryDragEvent(a.getOpenInventory(),new ItemStack(Material.DIAMOND),new ItemStack(Material.DIAMOND),false,dragged);
        Bukkit.getPluginManager().callEvent(drag); ok(drag.isCancelled(),"drag into preview blocked");
        click(a,45,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        click(b,81,ClickType.LEFT,InventoryAction.PICKUP_ALL); // changing offer resets both confirmations
        ok(a.getOpenInventory().getTopInventory().getItem(45).getType()==Material.GRAY_DYE,"changing offer revokes both confirmations");
        click(b,81,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        click(a,45,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        click(b,45,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        later(5,() -> finishTrade(santa,diamond));
    }
    private void finishTrade(ItemStack santa, ItemStack diamond) {
        ok(a.getInventory().containsAtLeast(diamond,12),"buyer received actual diamond stack");
        boolean found=false; for(ItemStack item:b.getInventory().getStorageContents()) if(santa.equals(item)) found=true;
        ok(found,"exchange preserved PDC, lore and all custom metadata");
        ok(!a.getInventory().containsAtLeast(santa,1) && !b.getInventory().containsAtLeast(diamond,12),"offered items removed from original owners");
        ok(!(a.getOpenInventory().getTopInventory().getHolder() instanceof SafeTradeManager.TradeHolder),"completed trade view closed");
        try {
            java.nio.file.Path dir=core.getDataFolder().toPath().resolve("safe-trades/pending");
            try(java.util.stream.Stream<java.nio.file.Path> paths=java.nio.file.Files.list(dir)) {
                ok(paths.noneMatch(path -> path.toString().endsWith(".trade")),"completed journal cleaned up");
            }
        } catch(Exception error) {throw new RuntimeException(error);}
        // One free slot after offering cannot fit two different incoming stacks.
        a.getInventory().clear(); b.getInventory().clear();
        for(int slot=0;slot<36;slot++) a.getInventory().setItem(slot,new ItemStack(Material.COBBLESTONE,64));
        a.getInventory().setItem(0,new ItemStack(Material.GOLD_INGOT,3));
        b.getInventory().setItem(0,new ItemStack(Material.EMERALD,5));
        b.getInventory().setItem(1,new ItemStack(Material.DIAMOND,7));
        a.performCommand("trade " + b.getName()); b.performCommand("trade accept " + a.getName());
        click(a,81,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        click(b,81,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        click(b,82,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        click(a,45,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        click(b,45,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        later(5,() -> {
            ok(a.getInventory().getItem(0).getType()==Material.GOLD_INGOT && b.getInventory().getItem(0).getType()==Material.EMERALD
                    && b.getInventory().getItem(1).getType()==Material.DIAMOND,"not enough space aborts without moving either offer");
            ok(a.getOpenInventory().getTopInventory().getItem(45).getType()==Material.GRAY_DYE,"not enough space resets confirmations");
            a.closeInventory();
            later(3,() -> {
                ok(!(b.getOpenInventory().getTopInventory().getHolder() instanceof SafeTradeManager.TradeHolder)
                        && a.getInventory().getItem(0).getAmount()==3,"closing trade cancels both views without item loss");
                try { orders(); } catch(Exception error) { throw new RuntimeException(error); }
            });
        });
    }

    private void orders() throws Exception {
        a.getInventory().clear(); b.getInventory().clear();
        core.getStore().setCoins(a.getName(),1000); core.getStore().setCoins(b.getName(),1000); core.getStore().save();
        ItemStack wanted=ItemFactory.arrowIce(1);
        ItemMeta meta=wanted.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(this,"order"),PersistentDataType.STRING,"exact-properties");
        meta.setLore(Arrays.asList("§bРедкая стрела"));wanted.setItemMeta(meta);
        a.getInventory().setItemInMainHand(wanted.clone());
        a.performCommand("buyorder create 3 100");
        ok(core.getStore().getCoins(a.getName())==700 && wanted.equals(a.getInventory().getItemInMainHand()),"buy order reserves all coins without consuming template");
        a.performCommand("buyorder create 20 100");
        ok(core.getStore().getCoins(a.getName())==700,"insufficient funds cannot create an unfunded order");
        b.performCommand("buyorder cancel 1");
        ok(core.getStore().getCoins(b.getName())==1000 && core.getStore().getCoins(a.getName())==700,"only the owner can cancel an order");
        b.getInventory().setItemInMainHand(new ItemStack(Material.ARROW,2));
        b.performCommand("buyorder sell 1 2");
        ok(b.getInventory().getItemInMainHand().getAmount()==2 && core.getStore().getCoins(b.getName())==1000,"mismatched item metadata cannot fill an order");
        ItemStack offered=wanted.clone();offered.setAmount(2);b.getInventory().setItemInMainHand(offered);
        org.bukkit.configuration.file.YamlConfiguration inMemory=(org.bukkit.configuration.file.YamlConfiguration)field(core.getStore(),"yml").get(core.getStore());
        for(int i=0;i<9;i++)inMemory.set("buy-orders.mail."+a.getUniqueId()+".size-test-"+i+".item","A".repeat(120000));
        b.performCommand("buyorder sell 1 2");
        ok(b.getInventory().getItemInMainHand().getAmount()==2 && core.getStore().getCoins(b.getName())==1000,
                "mail byte limit rejects sale without taking items or paying");
        inMemory.set("buy-orders.mail."+a.getUniqueId(),null);
        for(int i=0;i<36;i++) a.getInventory().setItem(i,new ItemStack(Material.COBBLESTONE,64));
        b.performCommand("buyorder sell 1 2");
        ok(b.getInventory().getItemInMainHand().getType()==Material.AIR && core.getStore().getCoins(b.getName())==1200
                && core.getStore().getCoins(a.getName())==700,"partial sale removes items and pays only from escrow");
        a.performCommand("buyorder claim");
        ok(a.getInventory().getItem(0).getType()==Material.COBBLESTONE,"full inventory does not discard mailbox items");
        a.getInventory().setItem(0,null);a.performCommand("buyorder claim");
        ItemStack claimed=a.getInventory().getItem(0);
        ok(claimed!=null && claimed.getAmount()==2 && claimed.isSimilar(wanted),"mail claim preserves custom item metadata and quantity");
        a.performCommand("buyorder claim");
        ok(a.getInventory().getItem(0).getAmount()==2,"mail claim cannot be repeated");
        a.performCommand("buyorder cancel 1");
        ok(core.getStore().getCoins(a.getName())==800 && core.getStore().getCoins(b.getName())==1200,"cancel returns only unfilled escrow; money is conserved");
        // A failed atomic replace must roll back the in-memory balance and order too.
        a.getInventory().setItemInMainHand(wanted.clone());
        Field file=field(core.getStore(),"file");Object original=file.get(core.getStore());
        java.nio.file.Path invalid=core.getDataFolder().toPath().resolve("not-a-data-file");
        java.nio.file.Files.createDirectories(invalid);java.nio.file.Files.write(invalid.resolve("sentinel"),new byte[]{1});
        try {file.set(core.getStore(),invalid.toFile());a.performCommand("buyorder create 1 100");}
        finally {file.set(core.getStore(),original);}
        ok(core.getStore().getCoins(a.getName())==800,"failed durable order write rolls balance back");
        org.bukkit.configuration.file.YamlConfiguration disk=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration((java.io.File)original);
        ok(!disk.contains("buy-orders.orders.2"),"failed order was not committed to data.yml");
        getLogger().info("KG-NEW RESULT PASS checks=" + checks);
    }
}
