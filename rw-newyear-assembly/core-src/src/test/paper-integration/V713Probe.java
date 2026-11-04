package net.kinggrief.probe;

import net.kinggrief.core.DataStore;
import net.kinggrief.core.KingGriefCore;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Real Paper physics and three protocol players; crash/restart auction journals. */
public final class V713Probe extends JavaPlugin implements Listener {
    private KingGriefCore core; private DataStore store; private Player a,b,c; private World world;
    private DataStore.Claim claim, adjacent; private int phase,checks,errors; private boolean started,finished;
    private boolean crossExtend,crossRetract,insideExtend,tempRetract,fluidBlocked;
    private int bucketEvents, bucketDenied, leverEvents, leverDenied, frameEvents, frameDenied;
    private Block lever,center; private ItemFrame frame; private ArmorStand stand; private StorageMinecart cart;
    private int readyId,reviewId,listingId; private UUID aId,bId,cId;

    @Override public void onEnable() { phase=getConfig().getInt("phase",1); Bukkit.getPluginManager().registerEvents(this,this); }
    @EventHandler public void join(PlayerJoinEvent e) {
        String n=e.getPlayer().getName();
        if(n.equals("ExpOne"))a=e.getPlayer(); else if(n.equals("ExpTwo"))b=e.getPlayer(); else if(n.equals("ExpThree"))c=e.getPlayer();
        if(!started && a!=null && b!=null && c!=null){started=true;later(70,this::begin);}
    }
    @EventHandler(priority=EventPriority.MONITOR) public void piston(BlockPistonExtendEvent e) {
        if(e.getBlock().getWorld()!=world)return;
        if(e.getBlock().getX()==294&&e.getBlock().getY()==102)crossExtend=e.isCancelled();
        if(e.getBlock().getX()==298&&e.getBlock().getY()==110)insideExtend=!e.isCancelled();
    }
    @EventHandler(priority=EventPriority.MONITOR) public void piston(BlockPistonRetractEvent e) {
        if(e.getBlock().getWorld()!=world)return;
        if(e.getBlock().getX()==294&&e.getBlock().getY()==106)crossRetract=e.isCancelled();
        if(e.getBlock().getX()==280&&e.getBlock().getY()==110)tempRetract=e.isCancelled();
    }
    @EventHandler(priority=EventPriority.MONITOR) public void fluid(BlockFromToEvent e) {
        if(world!=null&&e.getBlock().getWorld()==world&&e.getBlock().getY()==115
                &&e.getBlock().getX()==295&&e.getToBlock().getX()==296&&e.isCancelled())fluidBlocked=true;
    }
    @EventHandler(priority=EventPriority.MONITOR) public void bucket(PlayerBucketEmptyEvent e) {
        if(e.getPlayer().equals(c)&&e.getBlock().getX()==296&&e.getBlock().getY()==100){bucketEvents++;if(e.isCancelled())bucketDenied++;}
    }
    @EventHandler(priority=EventPriority.MONITOR) public void interact(PlayerInteractEvent e) {
        if(lever!=null&&lever.equals(e.getClickedBlock())&&e.getPlayer().equals(c)&&e.getAction()==Action.RIGHT_CLICK_BLOCK){
            leverEvents++;if(e.useInteractedBlock()==Event.Result.DENY)leverDenied++;
        }
    }
    @EventHandler(priority=EventPriority.MONITOR) public void entity(PlayerInteractEntityEvent e) {
        if(frame!=null&&e.getRightClicked().equals(frame)&&e.getPlayer().equals(c)){frameEvents++;if(e.isCancelled())frameDenied++;}
    }
    private interface Work { void run() throws Exception; }
    private interface Condition { boolean test() throws Exception; }
    private void later(long ticks,Work work) { Bukkit.getScheduler().runTaskLater(this,()->{if(finished)return;try{work.run();}catch(Throwable t){fail(t);}},ticks); }
    private void waitFor(Condition condition,Work work,int tries) { later(5,()->{if(condition.test())work.run();else if(tries>0)waitFor(condition,work,tries-1);else throw new AssertionError("timed out waiting for integration condition");}); }
    private void ok(boolean value,String label) { checks++;if(!value){errors++;getLogger().severe("KG-V713 FAIL: "+label);}else getLogger().info("KG-V713 PASS: "+label); }
    private void require(boolean value,String label){ok(value,label);if(!value)throw new IllegalStateException("fixture failed: "+label);}
    private void fail(Throwable t){errors++;getLogger().severe("KG-V713 EXCEPTION "+t);t.printStackTrace();finish();}
    private void finish(){if(finished)return;finished=true;getLogger().info("KG-V713 RESULT "+(errors==0?"PASS":"FAIL")+" checks="+checks+" errors="+errors);}
    private Object field(Object obj,String name)throws Exception {Field f=obj.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(obj);}
    private YamlConfiguration yml()throws Exception{return (YamlConfiguration)field(store,"yml");}
    private Object invokeStore(String name,Class<?>[] types,Object... args)throws Exception {Method m=store.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(store,args);}
    private DataStore.AuctionLot reserve(Player p,ItemStack item,double price)throws Exception {
        return (DataStore.AuctionLot)invokeStore("beginAuctionListing",new Class<?>[]{Player.class,ItemStack.class,double.class},p,item,price);
    }
    private DataStore.AuctionLot buy(Player p,int id)throws Exception {
        return (DataStore.AuctionLot)invokeStore("purchaseAuctionLotWithReceipt",new Class<?>[]{int.class,Player.class},id,p);
    }
    private String delivery(Player p,int id){return "auctionDeliveries."+p.getUniqueId()+"."+id;}
    private void request(String s){getLogger().info("KG-V713 REQUEST "+s);}
    private String key(Block block){return block.getWorld().getName()+":"+block.getX()+":"+block.getY()+":"+block.getZ();}
    @SuppressWarnings("unchecked") private Set<String> temporary()throws Exception{return (Set<String>)field(field(core,"gameplayListener"),"activeTempBlocks");}
    private void call(Event event){Bukkit.getPluginManager().callEvent(event);}
    private void blockedSave(Runnable action){
        Path data=core.getDataFolder().toPath().resolve("data.yml"),backup=data.resolveSibling("v713-saved-data.yml");
        try{Files.move(data,backup);Files.createSymbolicLink(data,backup.getFileName());action.run();}
        catch(Exception e){throw new RuntimeException(e);}
        finally{try{if(Files.isSymbolicLink(data))Files.delete(data);if(Files.exists(backup))Files.move(backup,data);}catch(Exception e){throw new RuntimeException(e);}}
    }

    private void begin()throws Exception {
        core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");store=core.getStore();world=Bukkit.getWorlds().get(0);
        aId=a.getUniqueId();bId=b.getUniqueId();cId=c.getUniqueId();
        require(core.getDescription().getVersion().equals("7.13.0"),"Core 7.13 loaded");
        if(phase==2){restart();return;}
        for(int x=17;x<=19;x++)for(int z=18;z<=19;z++)world.getChunkAt(x,z).load();
        for(Player p:new Player[]{a,b,c}){p.setGameMode(GameMode.SURVIVAL);p.getInventory().clear();p.setHealth(20);p.teleport(new Location(world,294.5,100,299.5));}
        center=world.getBlockAt(300,100,300);center.setType(Material.EMERALD_ORE,false);
        claim=store.createClaimDurably(a.getName(),center.getLocation(),4);
        adjacent=store.createClaimDurably(b.getName(),new Location(world,309,100,300),4);
        require(claim!=null&&adjacent!=null,"two adjacent non-overlapping Core claims saved");
        DataStore.Claim extreme=new DataStore.Claim();extreme.world=world.getName();extreme.cx=Integer.MIN_VALUE;extreme.cz=0;extreme.radius=1;
        ok(!extreme.contains(new Location(world,Integer.MAX_VALUE,100,0)),"claim containment does not overflow at extreme coordinates");
        java.lang.reflect.Field claimsField=DataStore.class.getDeclaredField("claims");claimsField.setAccessible(true);
        @SuppressWarnings("unchecked") List<DataStore.Claim> liveClaims=(List<DataStore.Claim>)claimsField.get(store);
        extreme.world="kg_extreme_only";liveClaims.add(extreme);
        ok(!store.overlapsAnyClaim("kg_extreme_only",Integer.MAX_VALUE,0,1),"claim overlap distance does not overflow");
        extreme.cx=0;extreme.radius=Integer.MAX_VALUE;
        ok(store.overlapsAnyClaim("kg_extreme_only",1,0,128),"claim overlap radius does not overflow");liveClaims.remove(extreme);
        require(store.setClaimRole(claim,b.getName(),DataStore.ClaimRole.BUILDER)==DataStore.ClaimRoleResult.CHANGED,"builder role committed");
        store.setCoins(a.getName(),1000);store.setCoins(b.getName(),1000);store.setCoins(c.getName(),1000);
        synthetic();realPistonCross();
    }
    private PlayerInteractEvent interact(Player p,Block block,ItemStack hand){
        PlayerInteractEvent e=new PlayerInteractEvent(p,Action.RIGHT_CLICK_BLOCK,hand,block,BlockFace.UP,EquipmentSlot.HAND);call(e);return e;
    }
    private void synthetic()throws Exception {
        Block target=world.getBlockAt(298,100,301);target.setType(Material.LEVER,false);
        PlayerInteractEvent visitor=interact(c,target,new ItemStack(Material.FLINT_AND_STEEL));
        PlayerInteractEvent builder=interact(b,target,new ItemStack(Material.FLINT_AND_STEEL));
        ok(visitor.useInteractedBlock()==Event.Result.DENY&&visitor.useItemInHand()==Event.Result.DENY,"visitor cannot use claim mechanisms or items on a protected block");
        ok(builder.useInteractedBlock()!=Event.Result.DENY&&builder.useItemInHand()!=Event.Result.DENY,"builder can use a protected mechanism");
        Block outside=world.getBlockAt(295,100,300),inside=world.getBlockAt(296,100,300);
        PlayerBucketEmptyEvent empty=new PlayerBucketEmptyEvent(c,inside,outside,BlockFace.EAST,Material.WATER_BUCKET,new ItemStack(Material.BUCKET),EquipmentSlot.HAND);
        call(empty);ok(empty.isCancelled(),"bucket checks the actual target when clicked support is outside");
        PlayerBucketFillEvent fill=new PlayerBucketFillEvent(c,inside,outside,BlockFace.EAST,Material.BUCKET,new ItemStack(Material.WATER_BUCKET),EquipmentSlot.HAND);
        call(fill);ok(fill.isCancelled(),"foreign player cannot fill a bucket from a claim");
        PlayerBucketEmptyEvent own=new PlayerBucketEmptyEvent(b,inside,outside,BlockFace.EAST,Material.WATER_BUCKET,new ItemStack(Material.BUCKET),EquipmentSlot.HAND);
        call(own);ok(!own.isCancelled(),"builder may use a bucket inside a claim");
        BlockFromToEvent entering=new BlockFromToEvent(outside,inside);outside.setType(Material.WATER,false);call(entering);
        ok(entering.isCancelled(),"liquid entering from wilderness is denied");
        BlockFromToEvent exiting=new BlockFromToEvent(inside,outside);inside.setType(Material.WATER,false);call(exiting);
        ok(exiting.isCancelled(),"liquid leaving a claim is denied");
        BlockFromToEvent between=new BlockFromToEvent(world.getBlockAt(304,120,300),world.getBlockAt(305,120,300));
        between.getBlock().setType(Material.LAVA,false);call(between);ok(between.isCancelled(),"liquid cannot flow between adjacent claims");
        BlockFromToEvent within=new BlockFromToEvent(world.getBlockAt(298,120,300),world.getBlockAt(299,120,300));
        within.getBlock().setType(Material.WATER,false);call(within);ok(!within.isCancelled(),"liquid within one claim remains allowed");
        outside.setType(Material.AIR,false);inside.setType(Material.AIR,false);
        Block piston=world.getBlockAt(275,100,300),source=world.getBlockAt(280,100,300),destination=source.getRelative(BlockFace.WEST),wrong=source.getRelative(BlockFace.EAST);
        Set<String> temps=temporary();temps.add(key(destination));
        BlockPistonRetractEvent retract=new BlockPistonRetractEvent(piston,Collections.singletonList(source),BlockFace.WEST);call(retract);
        ok(retract.isCancelled(),"sticky retract protects the destination in its actual movement direction");temps.remove(key(destination));temps.add(key(wrong));
        retract=new BlockPistonRetractEvent(piston,Collections.singletonList(source),BlockFace.WEST);call(retract);
        ok(!retract.isCancelled(),"sticky retract does not confuse the opposite, unrelated block with its destination");temps.remove(key(wrong));
        Block head=piston.getRelative(BlockFace.EAST);temps.add(key(head));
        BlockPistonExtendEvent emptyHead=new BlockPistonExtendEvent(piston,Collections.emptyList(),BlockFace.EAST);call(emptyHead);
        ok(emptyHead.isCancelled(),"empty piston head cannot enter a protected temporary block");temps.remove(key(head));
        BlockPistonExtendEvent twoClaims=new BlockPistonExtendEvent(world.getBlockAt(304,100,301),Collections.emptyList(),BlockFace.EAST);call(twoClaims);
        ok(twoClaims.isCancelled(),"piston head cannot enter the adjacent claim even with no payload");
        BlockPistonExtendEvent centerMove=new BlockPistonExtendEvent(world.getBlockAt(298,100,300),Arrays.asList(world.getBlockAt(299,100,300),center),BlockFace.EAST);call(centerMove);
        ok(centerMove.isCancelled(),"piston cannot move a claim center even wholly inside its own claim");
        Entity tnt=world.spawnEntity(new Location(world,300.5,105,300.5),EntityType.TNT);
        Block neighbor=world.getBlockAt(301,100,300);neighbor.setType(Material.STONE,false);
        List<Block> blast=new ArrayList<Block>(Arrays.asList(center,neighbor));
        EntityExplodeEvent explosion=new EntityExplodeEvent(tnt,tnt.getLocation(),blast,1f,ExplosionResult.DESTROY);call(explosion);
        ok(!explosion.blockList().contains(center)&&explosion.blockList().contains(neighbor),"entity explosion preserves only the claim center, not all raid blocks");tnt.remove();
        BlockExplodeEvent blockExplosion=new BlockExplodeEvent(neighbor,neighbor.getState(),new ArrayList<Block>(Arrays.asList(center,neighbor)),1f,ExplosionResult.DESTROY);call(blockExplosion);
        ok(!blockExplosion.blockList().contains(center)&&blockExplosion.blockList().contains(neighbor),"block explosion preserves the center without changing normal raid policy");
        ok(a.performCommand("rg list")&&a.performCommand("rg status")&&c.performCommand("rg list"),"claim status and personal list commands are available");
    }
    private void setPiston(Block piston,Material type,BlockFace face){
        piston.setType(type,false);Directional d=(Directional)piston.getBlockData();d.setFacing(face);piston.setBlockData(d,false);
    }
    private void realPistonCross(){
        Block piston=world.getBlockAt(294,102,300),source=world.getBlockAt(295,102,300),dest=world.getBlockAt(296,102,300);
        setPiston(piston,Material.PISTON,BlockFace.EAST);source.setType(Material.STONE,false);dest.setType(Material.AIR,false);
        Block power=piston.getRelative(BlockFace.UP);power.setType(Material.REDSTONE_BLOCK,true);
        later(15,()->{
            ok(crossExtend&&source.getType()==Material.STONE&&dest.getType().isAir(),"real powered piston cannot push across a claim boundary");
            power.setType(Material.AIR,true);realStickyCross();
        });
    }
    private void realStickyCross(){
        Block piston=world.getBlockAt(294,106,300),head=world.getBlockAt(295,106,300),source=world.getBlockAt(296,106,300);
        head.setType(Material.AIR,false);source.setType(Material.AIR,false);setPiston(piston,Material.STICKY_PISTON,BlockFace.EAST);
        Block power=piston.getRelative(BlockFace.UP);power.setType(Material.REDSTONE_BLOCK,true);
        later(12,()->{
            ok(head.getType()==Material.PISTON_HEAD,"sticky piston extends normally in wilderness");source.setType(Material.STONE,false);power.setType(Material.AIR,true);
            later(15,()->{
                ok(crossRetract&&source.getType()==Material.STONE,"real sticky piston cannot pull a block out of a claim");realPistonInside();
            });
        });
    }
    private void realPistonInside(){
        Block piston=world.getBlockAt(298,110,300),source=world.getBlockAt(299,110,300),dest=world.getBlockAt(300,110,300);
        setPiston(piston,Material.PISTON,BlockFace.EAST);source.setType(Material.STONE,false);dest.setType(Material.AIR,false);
        piston.getRelative(BlockFace.UP).setType(Material.REDSTONE_BLOCK,true);
        later(15,()->{ok(insideExtend&&dest.getType()==Material.STONE,"real piston wholly inside one claim still works");realTempRetract();});
    }
    private void realTempRetract()throws Exception{
        Block piston=world.getBlockAt(280,110,300),head=world.getBlockAt(281,110,300),source=world.getBlockAt(282,110,300);
        head.setType(Material.AIR,false);source.setType(Material.AIR,false);setPiston(piston,Material.STICKY_PISTON,BlockFace.EAST);
        Block power=piston.getRelative(BlockFace.UP);power.setType(Material.REDSTONE_BLOCK,true);
        later(12,()->{
            ok(head.getType()==Material.PISTON_HEAD,"wilderness sticky piston extended for temporary-block test");
            source.setType(Material.STONE,false);temporary().add(key(source));power.setType(Material.AIR,true);
            later(15,()->{ok(tempRetract&&source.getType()==Material.STONE,"real sticky piston cannot pull a protected temporary block");temporary().remove(key(source));realFluids();});
        });
    }
    private void realFluids(){
        // A bounded channel makes the boundary the only downhill route: vanilla
        // water otherwise prefers the nearby open edge of a small test platform.
        for(int x=294;x<=297;x++)for(int z=299;z<=301;z++){
            world.getBlockAt(x,114,z).setType(Material.STONE,false);
            world.getBlockAt(x,115,z).setType(Material.STONE,false);
        }
        Block source=world.getBlockAt(295,115,300),dest=world.getBlockAt(296,115,300);
        source.setType(Material.AIR,false);dest.setType(Material.AIR,false);source.setType(Material.WATER,true);
        // A real neighbor update schedules fluid processing on Paper.
        Block wall=world.getBlockAt(294,115,300);wall.setType(Material.AIR,true);wall.setType(Material.STONE,true);
        later(65,()->{
            ok(fluidBlocked&&dest.getType()!=Material.WATER,"real flowing water stops at the Core claim boundary (attempt="+fluidBlocked+", source="+source.getType()+", destination="+dest.getType()+")");
            for(int x=297;x<=302;x++)for(int z=298;z<=302;z++)world.getBlockAt(x,118,z).setType(Material.STONE,false);
            world.getBlockAt(299,119,300).setType(Material.AIR,false);world.getBlockAt(298,119,300).setType(Material.WATER,true);
            later(55,()->{ok(world.getBlockAt(299,119,300).getType()==Material.WATER,"real water flow within one claim remains functional");realBucket();});
        });
    }
    private void realBucket(){
        Block support=world.getBlockAt(295,100,300),dest=world.getBlockAt(296,100,300);
        support.setType(Material.STONE,false);dest.setType(Material.AIR,false);
        c.teleport(new Location(world,294.5,100,299.5));c.getInventory().clear();c.getInventory().setItemInMainHand(new ItemStack(Material.WATER_BUCKET));
        later(15,()->{request("BLOCK 2 295 100 300 5 0");later(25,()->{
            ok(bucketEvents>0&&bucketDenied>0&&dest.getType().isAir()&&c.getInventory().getItemInMainHand().getType()==Material.WATER_BUCKET,
                    "real client bucket from outside cannot place water inside a claim");realLever();
        });});
    }
    private void realLever(){
        lever=world.getBlockAt(298,100,303);lever.getRelative(BlockFace.DOWN).setType(Material.STONE,false);lever.setType(Material.LEVER,false);
        Switch sw=(Switch)lever.getBlockData();sw.setAttachedFace(FaceAttachable.AttachedFace.FLOOR);sw.setPowered(false);lever.setBlockData(sw,false);
        c.teleport(new Location(world,297.5,100,302.5));c.getInventory().clear();
        later(15,()->{request("BLOCK 2 298 100 303 1 0");later(25,()->{
            ok(leverEvents>0&&leverDenied>0&&!((Switch)lever.getBlockData()).isPowered(),"real visitor cannot toggle another player's lever");
            b.teleport(new Location(world,297.5,100,302.5));b.getInventory().clear();
            later(15,()->{request("BLOCK 1 298 100 303 1 0");later(25,()->{
                ok(((Switch)lever.getBlockData()).isPowered(),"real builder can toggle the same lever");decorationChecks();
            });});
        });});
    }
    private void decorationChecks()throws Exception{
        Block support=world.getBlockAt(300,102,302);support.setType(Material.STONE,false);
        frame=(ItemFrame)world.spawnEntity(new Location(world,300.5,102.5,301.5),EntityType.ITEM_FRAME);
        frame.setFacingDirection(BlockFace.NORTH,true);frame.setItem(new ItemStack(Material.DIAMOND));
        stand=(ArmorStand)world.spawnEntity(new Location(world,299.5,100,301.5),EntityType.ARMOR_STAND);stand.setGravity(false);
        stand.getEquipment().setHelmet(new ItemStack(Material.GOLDEN_HELMET));
        EntityPlaceEvent placeDenied=new EntityPlaceEvent(stand,c,support,BlockFace.UP,EquipmentSlot.HAND);call(placeDenied);
        EntityPlaceEvent placeAllowed=new EntityPlaceEvent(stand,b,support,BlockFace.UP,EquipmentSlot.HAND);call(placeAllowed);
        ok(placeDenied.isCancelled()&&!placeAllowed.isCancelled(),"entity placement checks claim builder access");
        HangingPlaceEvent hanging=new HangingPlaceEvent(frame,c,support,BlockFace.NORTH,EquipmentSlot.HAND,new ItemStack(Material.ITEM_FRAME));call(hanging);
        ok(hanging.isCancelled(),"foreign hanging placement is denied");
        PlayerInteractAtEntityEvent precise=new PlayerInteractAtEntityEvent(c,stand,new Vector(0,1,0),EquipmentSlot.HAND);call(precise);
        PlayerArmorStandManipulateEvent manipulation=new PlayerArmorStandManipulateEvent(c,stand,new ItemStack(Material.AIR),new ItemStack(Material.GOLDEN_HELMET),EquipmentSlot.HEAD,EquipmentSlot.HAND);call(manipulation);
        ok(precise.isCancelled()&&manipulation.isCancelled(),"both precise armor-stand interaction event types are protected");
        EntityDamageByEntityEvent hit=new EntityDamageByEntityEvent(c,stand,EntityDamageEvent.DamageCause.ENTITY_ATTACK,1);call(hit);
        HangingBreakByEntityEvent breakFrame=new HangingBreakByEntityEvent(frame,c);call(breakFrame);
        ok(hit.isCancelled()&&breakFrame.isCancelled(),"foreign attacks cannot remove protected stands or frames");
        c.teleport(new Location(world,299.5,102,300.5));c.getInventory().clear();
        Rotation before=frame.getRotation();later(15,()->{request("ENTITY 2 "+frame.getEntityId()+" 0");later(25,()->{
            ok(frameEvents>0&&frameDenied>0&&frame.isValid()&&frame.getRotation()==before&&frame.getItem().getType()==Material.DIAMOND,
                    "real client cannot rotate or remove an item frame in another claim");cartChecks();
        });});
    }
    private void cartChecks()throws Exception{
        cart=(StorageMinecart)world.spawnEntity(new Location(world,299.5,104,302.5),EntityType.CHEST_MINECART);cart.setGravity(false);
        Inventory inv=cart.getInventory();inv.setItem(0,new ItemStack(Material.DIAMOND,3));
        InventoryView denied=c.openInventory(inv);
        ok(denied==null&&!inv.equals(c.getOpenInventory().getTopInventory()),"foreign player cannot open a chest minecart in a claim");
        require(store.setClaimRole(claim,c.getName(),DataStore.ClaimRole.CONTAINER)==DataStore.ClaimRoleResult.CHANGED,"container role saved");
        Block chest=world.getBlockAt(298,103,302);chest.setType(Material.CHEST,false);
        PlayerInteractEvent container=interact(c,chest,new ItemStack(Material.FLINT_AND_STEEL));
        ok(container.useInteractedBlock()!=Event.Result.DENY&&container.useItemInHand()==Event.Result.DENY,"container role opens storage but cannot use a held item on it");
        InventoryView opened=c.openInventory(inv);
        require(opened!=null&&inv.equals(opened.getTopInventory()),"container role can open a chest minecart");
        require(store.setClaimRole(claim,c.getName(),DataStore.ClaimRole.NONE)==DataStore.ClaimRoleResult.CHANGED,"container role revoked");
        InventoryClickEvent click=new InventoryClickEvent(c.getOpenInventory(),InventoryType.SlotType.CONTAINER,0,ClickType.LEFT,InventoryAction.PICKUP_ALL);call(click);
        ok(click.isCancelled(),"open minecart access is rechecked after role revocation");
        later(3,()->{
            ok(!inv.equals(c.getOpenInventory().getTopInventory())&&inv.getItem(0).getAmount()==3,"revoked minecart view closes without changing its contents");
            store.setClaimRole(claim,c.getName(),DataStore.ClaimRole.CONTAINER);
            frame.remove();stand.remove();cart.remove();auctionChecks();
        });
    }
    private ItemStack special(){
        ItemStack stack=new ItemStack(Material.DIAMOND,5);ItemMeta meta=stack.getItemMeta();
        meta.setDisplayName("§bReceipt metadata");meta.setLore(Arrays.asList("§dKeep exactly"));stack.setItemMeta(meta);return stack;
    }
    private void auctionChecks()throws Exception{
        a.getInventory().clear();a.getInventory().setHeldItemSlot(0);ItemStack item=special();a.getInventory().setItemInMainHand(item.clone());
        int count=store.getAuctionLots().size();blockedSave(()->a.performCommand("ah sell 125"));
        ok(a.getInventory().getItemInMainHand().equals(item)&&store.getAuctionLots().size()==count,"failed listing reservation leaves the item and listings unchanged");
        a.performCommand("ah sell 125");List<DataStore.AuctionLot> lots=store.getAuctionLots();
        require(lots.size()==count+1,"normal player listing is published");DataStore.AuctionLot lot=lots.get(0);
        ok(a.getInventory().getItemInMainHand().getType().isAir()&&lot.item.equals(item)
                &&!yml().contains("auctionListings."+aId),"listing removes exactly the item and clears its journal");
        b.getInventory().clear();for(int i=0;i<36;i++)b.getInventory().setItem(i,new ItemStack(Material.DIRT,64));
        b.performCommand("ah clear");
        later(15,()->{request("CLICK 1 0 0 0");later(25,()->{
            ok(store.findAuctionLot(lot.id)==null&&store.getCoins(b.getName())==875&&store.getCoins(a.getName())==1125,
                    "real GUI purchase transfers coins and removes only the displayed lot");
            ok("ready".equals(yml().getString(delivery(b,lot.id)+".state"))&&!b.getInventory().contains(Material.DIAMOND),
                    "full inventory leaves a durable ready purchase instead of dropping items");
            b.closeInventory();b.getInventory().setItem(0,null);b.performCommand("ah pending");b.performCommand("ah claim "+lot.id);
            ok(!yml().contains(delivery(b,lot.id))&&count(b,Material.DIAMOND)==5&&item.equals(b.getInventory().getItem(0)),
                    "claim gives the exact metadata and quantity once when space is available");
            b.performCommand("ah claim "+lot.id);ok(count(b,Material.DIAMOND)==5,"repeating claim cannot duplicate a delivered purchase");
            capacityAndFailure();
        });});
    }
    private int count(Player p,Material type){int n=0;for(ItemStack s:p.getInventory().getStorageContents())if(s!=null&&s.getType()==type)n+=s.getAmount();return n;}
    private void capacityAndFailure()throws Exception{
        b.getInventory().clear();DataStore.AuctionLot failure=store.addAuctionLot(a.getName(),new ItemStack(Material.EMERALD,2),10);
        require(failure!=null,"failed-purchase fixture created");double before=store.getCoins(b.getName());
        blockedSave(()->{try{buy(b,failure.id);}catch(Exception ex){throw new RuntimeException(ex);}});
        ok(store.findAuctionLot(failure.id)!=null&&store.getCoins(b.getName())==before&&!yml().contains(delivery(b,failure.id)),
                "failed purchase leaves balances, lot and delivery unchanged");
        String base="auctionDeliveries."+cId;
        for(int i=0;i<32;i++){String p=base+"."+(9000+i);yml().set(p+".state","ready");yml().set(p+".item",new ItemStack(Material.STONE));}
        store.save();double cBefore=store.getCoins(c.getName());
        ok(buy(c,failure.id)==null&&store.findAuctionLot(failure.id)!=null&&store.getCoins(c.getName())==cBefore,
                "32 pending purchases stop another debit without removing the lot");
        yml().set(base,null);store.save();
        DataStore.AuctionLot ready=store.addAuctionLot(a.getName(),new ItemStack(Material.EMERALD,3),30);
        DataStore.AuctionLot review=store.addAuctionLot(a.getName(),new ItemStack(Material.GOLD_INGOT,2),40);
        require(ready!=null&&review!=null&&buy(b,ready.id)!=null&&buy(c,review.id)!=null,"two persisted purchase receipts created");
        readyId=ready.id;reviewId=review.id;yml().set(delivery(c,reviewId)+".state","delivering");store.save();
        a.getInventory().clear();a.getInventory().setItemInMainHand(new ItemStack(Material.NETHERITE_INGOT,2));
        DataStore.AuctionLot pending=reserve(a,a.getInventory().getItemInMainHand(),90);require(pending!=null,"unpublished listing journal created");listingId=pending.id;
        a.saveData();b.saveData();c.saveData();
        YamlConfiguration state=new YamlConfiguration();state.set("ready",readyId);state.set("review",reviewId);state.set("listing",listingId);
        state.save(getDataFolder().toPath().resolve("state.yml").toFile());
        Location safe=core.getSpawnEngine().getSpawnArrival();require(safe!=null,"separate spawn available for saved-chunk test");
        a.teleport(safe);b.teleport(safe);c.teleport(safe);
        // This disposable flat world's spawn tickets and the explicit fixture
        // load tickets can keep nearby chunks in memory after players leave.
        world.save();world.setKeepSpawnInMemory(false);
        for(int x=17;x<=19;x++)for(int z=18;z<=19;z++)world.unloadChunkRequest(x,z);
        later(40,()->waitFor(()->world.unloadChunk(18,18,true),()->{
            ok(true,"survival test chunk saved and unloaded before SIGKILL");later(40,this::finish);
        },40));
    }
    private void restart()throws Exception{
        YamlConfiguration state=YamlConfiguration.loadConfiguration(getDataFolder().toPath().resolve("state.yml").toFile());
        readyId=state.getInt("ready");reviewId=state.getInt("review");listingId=state.getInt("listing");
        center=world.getBlockAt(300,100,300);claim=store.getClaimAt(center.getLocation());
        require(claim!=null&&claim.owner.equals(a.getName())&&claim.canAccess(b.getName())&&claim.canOpenContainers(c.getName()),"claim and both roles survived SIGKILL");
        ok(center.getType()==Material.EMERALD_ORE&&store.getClaimAt(new Location(world,309,100,300))!=null,"center block and adjacent claim survived restart");
        ok("ready".equals(yml().getString(delivery(b,readyId)+".state"))&&store.findAuctionLot(readyId)==null,
                "paid purchase remains ready after SIGKILL, not publicly resold");
        b.getInventory().clear();b.performCommand("ah claim "+readyId);
        ok(count(b,Material.EMERALD)==3&&!yml().contains(delivery(b,readyId)),"ready purchase is recovered exactly once after restart");
        b.performCommand("ah claim "+readyId);ok(count(b,Material.EMERALD)==3,"recovery claim cannot be replayed");
        c.getInventory().clear();c.performCommand("ah claim "+reviewId);
        ok("delivering".equals(yml().getString(delivery(c,reviewId)+".state"))&&count(c,Material.GOLD_INGOT)==0,
                "ambiguous delivering state is not automatically replayed");
        c.performCommand("ah resolve "+cId+" buy "+reviewId+" retry confirm");
        ok("delivering".equals(yml().getString(delivery(c,reviewId)+".state")),"ordinary player cannot resolve a delivery");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"ah resolve "+cId+" buy "+reviewId+" retry confirm");
        ok("delivering".equals(yml().getString(delivery(c,reviewId)+".state")),"administrator cannot resolve while the target is online");
        ok("taking".equals(yml().getString("auctionListings."+aId+".state"))&&store.findAuctionLot(listingId)==null
                &&a.getInventory().getItemInMainHand().getType()==Material.NETHERITE_INGOT,
                "interrupted listing remains unpublished and the pre-removal item survives");
        a.performCommand("ah sell 90");ok(store.findAuctionLot(listingId)==null&&a.getInventory().getItemInMainHand().getAmount()==2,
                "another sale is blocked until the ambiguous listing is reviewed");
        request("DISCONNECT 2");waitFor(()->Bukkit.getPlayer(cId)==null,()->resolvePurchase(),40);
    }
    private void resolvePurchase()throws Exception{
        blockedSave(()->Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"ah resolve "+cId+" buy "+reviewId+" retry confirm"));
        ok("delivering".equals(yml().getString("auctionDeliveries."+cId+"."+reviewId+".state")),"failed manual resolution keeps the review state");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"ah review "+cId);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"ah resolve "+cId+" buy "+reviewId+" retry confirm");
        ok("ready".equals(yml().getString("auctionDeliveries."+cId+"."+reviewId+".state")),"console can retry a reviewed purchase while the player is offline");
        request("RECONNECT 2");waitFor(()->Bukkit.getPlayer(cId)!=null,()->{
            c=Bukkit.getPlayer(cId);c.getInventory().clear();c.performCommand("ah claim "+reviewId);
            ok(count(c,Material.GOLD_INGOT)==2&&!yml().contains(delivery(c,reviewId)),"manually reviewed purchase can be claimed once");
            request("DISCONNECT 0");waitFor(()->Bukkit.getPlayer(aId)==null,()->resolveListing(),40);
        },40);
    }
    private void resolveListing()throws Exception{
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"ah review "+aId);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"ah resolve "+aId+" sell "+listingId+" cancel confirm");
        ok(!yml().contains("auctionListings."+aId)&&store.findAuctionLot(listingId)==null,"console can cancel a reviewed unpublished listing without inventing an item");
        request("RECONNECT 0");waitFor(()->Bukkit.getPlayer(aId)!=null,()->{
            a=Bukkit.getPlayer(aId);a.performCommand("ah sell 90");
            ok(a.getInventory().getItemInMainHand().getType().isAir()&&store.getAuctionLots().stream().anyMatch(l->l.seller.equals(a.getName())&&l.item.getType()==Material.NETHERITE_INGOT&&l.item.getAmount()==2),
                    "owner can list the verified item after review");
            manualBranches();finish();
        },40);
    }
    private void manualBranches()throws Exception{
        UUID offline=UUID.fromString("00000000-0000-4000-8000-000000001234");String path="auctionDeliveries."+offline+".777";
        yml().set(path+".state","delivering");yml().set(path+".item",new ItemStack(Material.IRON_INGOT));
        String listing="auctionListings."+offline;yml().set(listing+".state","taking");yml().set(listing+".id",778);
        yml().set(listing+".seller","OfflineOwner");yml().set(listing+".price",15.0);yml().set(listing+".createdAt",System.currentTimeMillis());
        yml().set(listing+".item",new ItemStack(Material.LAPIS_LAZULI,4));store.save();
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"ah resolve "+offline+" buy 777 done confirm");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"ah resolve "+offline+" sell 778 publish confirm");
        ok(!yml().contains(path)&&!yml().contains(listing)&&store.findAuctionLot(778)!=null,
                "manual done and publish branches operate only on matching offline journals");
    }
}
