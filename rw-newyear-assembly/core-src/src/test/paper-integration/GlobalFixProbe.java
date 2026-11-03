package net.kinggrief.probe;

import net.kinggrief.core.DataStore;
import net.kinggrief.core.KingGriefCore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.CommandBlock;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Proxy;

/** Real players, physical redstone command blocks/hoppers, and a loaded old spawn. */
public final class GlobalFixProbe extends JavaPlugin implements Listener {
    private KingGriefCore core; private DataStore store; private Player guest, owner;
    private int checks; private boolean started, finished; private World world, legacy;
    private Block commandBlock, redstone, insideHopper, outsideHopper, destination;
    @Override public void onEnable() { core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore"); store=core.getStore(); Bukkit.getPluginManager().registerEvents(this,this); }
    @EventHandler public void join(PlayerJoinEvent event) {
        if("WheelOne".equals(event.getPlayer().getName()))guest=event.getPlayer();
        if("WheelTwo".equals(event.getPlayer().getName()))owner=event.getPlayer();
        if(!started && guest!=null && owner!=null) { started=true; later(40,this::begin); }
    }
    private interface Task { void run() throws Exception; }
    private void later(long ticks,Task task) { Bukkit.getScheduler().runTaskLater(this,()->{try{task.run();}catch(Throwable error){fail(error);}},ticks); }
    private void ok(boolean condition,String text) { if(!condition)throw new AssertionError(text); checks++;getLogger().info("KG-GLOBAL PASS "+text); }
    private void fail(Throwable error) { if(finished)return;finished=true;getLogger().severe("KG-GLOBAL RESULT FAIL "+error);error.printStackTrace(); }
    private void finish() { if(finished)return;finished=true;getLogger().info("KG-GLOBAL RESULT PASS checks="+checks); }
    private CommandSender fake(Class<?> type,Block location) {
        return (CommandSender)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,method,args)->{
            String name=method.getName();
            if(name.equals("getName"))return "BlockFixture";
            if(name.equals("getBlock"))return location;
            if(name.equals("isOp") || name.equals("hasPermission") || name.equals("isPermissionSet"))return true;
            if(name.equals("isPermissionSet"))return true;
            if(name.equals("equals"))return proxy==args[0];
            if(name.equals("hashCode"))return System.identityHashCode(proxy);
            if(name.equals("toString"))return "sender-fixture";
            Class<?> ret=method.getReturnType();
            if(ret==boolean.class)return false;
            if(ret==int.class)return 0;
            if(ret==long.class)return 0L;
            return null;
        });
    }
    private void command(CommandSender sender,String command,String... args) {
        org.bukkit.command.PluginCommand pc=core.getCommand(command);
        if(pc==null)throw new AssertionError("missing Core command "+command);
        pc.execute(sender,command,args);
    }
    private void begin() throws Exception {
        world=Bukkit.getWorld("world"); if(world==null)throw new AssertionError("missing ordinary world");
        guest.setOp(false);owner.setOp(false);store.setSavedRank(guest.getName(),"default");store.setSavedRank(owner.getName(),"owner");
        guest.setGameMode(GameMode.SURVIVAL);owner.setGameMode(GameMode.CREATIVE);
        store.setCoins(guest.getName(),1000);store.save();
        Block at=world.getBlockAt(24,100,24);CommandSender block=fake(BlockCommandSender.class,at);
        command(block,"money","give",guest.getName(),"4000");
        command(block,"gm","1",guest.getName());
        command(block,"ban","domep444543","1h","block");
        command(block,"tex");
        ok(store.getCoins(guest.getName())==1000 && guest.getGameMode()==GameMode.SURVIVAL
                && store.getActiveBanReason("domep444543")==null && !core.getOwnerGiveaway().isTechWorkMode(),
                "operator-like block sender cannot issue economy, gamemode, moderation or maintenance commands");
        ok(!core.getModeration().isStaff(block) && !core.getCurator().hasCuratorAccess(block) && !core.getOwnerGiveaway().isOwnerSender(block),
                "command-block sender is not curator, owner or moderation staff");
        CommandSender remote=fake(RemoteConsoleCommandSender.class,at);
        command(remote,"money","give",guest.getName(),"7");
        ok(store.getCoins(guest.getName())==1007,"RCON sender retains console economy access");
        command(Bukkit.getConsoleSender(),"money","give",guest.getName(),"3");
        ok(store.getCoins(guest.getName())==1010,"local console retains economy access");

        commandBlock=world.getBlockAt(26,100,26);redstone=commandBlock.getRelative(BlockFace.EAST);
        commandBlock.setType(Material.COMMAND_BLOCK,false);
        CommandBlock cb=(CommandBlock)commandBlock.getState(); cb.setCommand("say kg_command_control");cb.update(true,false);
        redstone.setType(Material.REDSTONE_BLOCK,true);
        later(15,this::afterControl);
    }
    private void afterControl() {
        CommandBlock cb=(CommandBlock)commandBlock.getState();
        ok(cb.getSuccessCount()>0,"real powered command block executes a control command on Paper");
        redstone.setType(Material.AIR,true);
        cb=(CommandBlock)commandBlock.getState();cb.setCommand("kinggriefcore:money give WheelOne 90000");cb.update(true,false);
        redstone.setType(Material.REDSTONE_BLOCK,true);
        later(15,this::afterPhysicalBlock);
    }
    private void afterPhysicalBlock() throws Exception {
        ok(store.getCoins(guest.getName())==1010,"real powered command block cannot grant coins");
        legacy=Bukkit.createWorld(new WorldCreator("legacy_spawn"));
        ok(legacy!=null && !core.getSpawnEngine().isInSpawnWorld(legacy.getSpawnLocation()),"old marked spawn loads without becoming the active gameplay spawn");
        Block oldBlock=legacy.getBlockAt(80,130,80);oldBlock.setType(Material.STONE,false);
        ok(!guest.breakBlock(oldBlock) && oldBlock.getType()==Material.STONE,"ordinary player cannot break blocks in the loaded old spawn");
        ok(owner.breakBlock(oldBlock) && oldBlock.getType()==Material.AIR,"OWNER in creative can edit the old spawn");
        Block normal=world.getBlockAt(80,130,80);normal.setType(Material.STONE,false);
        ok(guest.breakBlock(normal) && normal.getType()==Material.AIR,"ordinary unmarked world remains editable");
        Block chestBlock=legacy.getBlockAt(82,130,80);chestBlock.setType(Material.CHEST,false);
        org.bukkit.block.Chest chest=(org.bukkit.block.Chest)chestBlock.getState();
        chest.getBlockInventory().setItem(0,new ItemStack(Material.DIAMOND,11));
        guest.openInventory(chest.getBlockInventory());
        ok(!guest.getOpenInventory().getTopInventory().equals(chest.getBlockInventory()),"ordinary player cannot open old-spawn chest");
        owner.openInventory(chest.getBlockInventory());
        ok(owner.getOpenInventory().getTopInventory().getItem(0)!=null && owner.getOpenInventory().getTopInventory().getItem(0).getAmount()==11,
                "OWNER can open the preserved old-spawn chest");owner.closeInventory();
        PlayerInteractEvent click=new PlayerInteractEvent(guest,Action.RIGHT_CLICK_BLOCK,new ItemStack(Material.BONE_MEAL),chestBlock,BlockFace.UP,EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(click);
        ok(click.useInteractedBlock()==Event.Result.DENY && click.useItemInHand()==Event.Result.DENY,"container and block-changing item interaction denied in old spawn");
        Block blast=legacy.getBlockAt(85,130,80);blast.setType(Material.DIAMOND_BLOCK,false);
        legacy.createExplosion(blast.getLocation().add(.5,.5,.5),4F,false,true);
        ok(blast.getType()==Material.DIAMOND_BLOCK,"explosion does not change old-spawn blocks");
        ok(Bukkit.unloadWorld(legacy,true),"old spawn unloads without player relocation");
        legacy=Bukkit.createWorld(new WorldCreator("legacy_spawn"));
        chest=(org.bukkit.block.Chest)legacy.getBlockAt(82,130,80).getState();
        ok(chest.getBlockInventory().getItem(0)!=null && chest.getBlockInventory().getItem(0).getAmount()==11,
                "old chest contents survive unloading and reloading");
        Block reloaded=legacy.getBlockAt(80,130,80);reloaded.setType(Material.STONE,false);
        ok(!guest.breakBlock(reloaded),"old-spawn protection is restored when its world is reloaded");
        beginHoppers();
    }
    private void hopperDirection(Block block,BlockFace face) {
        block.setType(Material.HOPPER,false);
        org.bukkit.block.data.type.Hopper data=(org.bukkit.block.data.type.Hopper)block.getBlockData();
        data.setFacing(face);block.setBlockData(data,false);
    }
    private Inventory inventory(Block block) { return ((org.bukkit.block.Container)block.getState()).getInventory(); }
    private void beginHoppers() {
        Location center=new Location(world,128,90,128);center.getChunk().load();world.getChunkAt(129>>4,128>>4).load();
        DataStore.Claim claim=store.createClaim(owner.getName(),center,1);
        ok(claim!=null,"isolated built-in claim created");
        destination=world.getBlockAt(129,90,128);destination.setType(Material.CHEST,false);
        insideHopper=world.getBlockAt(128,90,128);outsideHopper=world.getBlockAt(130,90,128);
        hopperDirection(insideHopper,BlockFace.EAST);hopperDirection(outsideHopper,BlockFace.WEST);
        inventory(insideHopper).setItem(0,new ItemStack(Material.EMERALD,1));
        inventory(outsideHopper).setItem(0,new ItemStack(Material.DIAMOND,1));
        later(70,this::afterHoppers);
    }
    private void afterHoppers() {
        ok(inventory(destination).contains(Material.EMERALD) && !inventory(insideHopper).contains(Material.EMERALD),"real hopper automation fully inside a claim still works");
        ok(!inventory(destination).contains(Material.DIAMOND) && inventory(outsideHopper).contains(Material.DIAMOND),"real hopper transfer across the claim boundary is blocked without item loss");
        InventoryMoveItemEvent outbound=new InventoryMoveItemEvent(inventory(destination),new ItemStack(Material.EMERALD),inventory(outsideHopper),true);
        Bukkit.getPluginManager().callEvent(outbound);
        ok(outbound.isCancelled(),"direct outbound transfer across a claim boundary is cancelled");
        Item item=world.dropItem(new Location(world,129.95,91,128.5),new ItemStack(Material.GOLD_INGOT));
        InventoryPickupItemEvent pickup=new InventoryPickupItemEvent(inventory(outsideHopper),item);Bukkit.getPluginManager().callEvent(pickup);
        ok(pickup.isCancelled(),"pickup across the claim boundary is cancelled");item.remove();
        insideHopper.setType(Material.AIR,false);outsideHopper.setType(Material.AIR,false);destination.setType(Material.AIR,false);
        Block left=world.getBlockAt(129,92,128),right=world.getBlockAt(130,92,128),under=world.getBlockAt(130,91,128);
        left.setType(Material.CHEST,false);right.setType(Material.CHEST,false);
        org.bukkit.block.data.type.Chest l=(org.bukkit.block.data.type.Chest)left.getBlockData();l.setFacing(BlockFace.NORTH);l.setType(org.bukkit.block.data.type.Chest.Type.LEFT);left.setBlockData(l,false);
        org.bukkit.block.data.type.Chest r=(org.bukkit.block.data.type.Chest)right.getBlockData();r.setFacing(BlockFace.NORTH);r.setType(org.bukkit.block.data.type.Chest.Type.RIGHT);right.setBlockData(r,false);
        hopperDirection(under,BlockFace.DOWN);
        Inventory pair=((org.bukkit.block.Chest)left.getState()).getInventory();
        ok(pair instanceof DoubleChestInventory,"fixture is an actual double chest straddling the claim boundary");
        pair.setItem(0,new ItemStack(Material.DIAMOND,2));
        later(70,()->{
            ok(inventory(under).isEmpty() && pair.contains(Material.DIAMOND),"outside hopper cannot drain a double chest with a protected half");
            finish();
        });
    }
}
