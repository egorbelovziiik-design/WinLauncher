package net.kinggrief.core;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.projectiles.ProjectileSource;

import java.util.List;

/**
 * Mechanical protection for the built-in ore claims, not a replacement for
 * WorldGuard. Actorless pistons and fluids must stay entirely in one claim or
 * entirely outside claims. No chunk is force-loaded and no old data is changed.
 */
final class ClaimMechanicsGuard implements Listener {
    private final KingGriefCore plugin;

    ClaimMechanicsGuard(KingGriefCore plugin) { this.plugin = plugin; }

    private DataStore.Claim claim(Block block) { return plugin.getStore().getClaimAt(block.getLocation()); }
    private boolean sameRegion(DataStore.Claim a, DataStore.Claim b) { return a == b; }
    private boolean center(Block block, DataStore.Claim claim) {
        return claim != null && claim.isCenter(block.getLocation());
    }

    private boolean validPlayer(Player player) {
        return player != null && plugin.getStore().isAuthenticated(player)
                && !plugin.getStore().isQuickFrozen(player)
                && !plugin.getModeration().isFrozenOrAscending(player);
    }
    private boolean canBuild(Player player, Location at) {
        DataStore.Claim claim = plugin.getStore().getClaimAt(at);
        if (player == null) return claim == null; // e.g. a dispenser has no trustworthy player owner
        if (!validPlayer(player)) return false;
        return claim == null || claim.canAccess(player.getName()) || plugin.getRanks().isOwnerLevel(player);
    }
    private boolean canOpen(Player player, Location at) {
        DataStore.Claim claim = plugin.getStore().getClaimAt(at);
        return validPlayer(player) && (claim == null || claim.canOpenContainers(player.getName())
                || plugin.getRanks().isOwnerLevel(player));
    }

    /** Run early and again after other listeners; GameplayListener also calls
     * it before applying custom-item effects. A container-only member can open
     * a physical container, but cannot use the held item to modify the block. */
    boolean enforce(PlayerInteractEvent event, boolean notify) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return false;
        Block block = event.getClickedBlock();
        DataStore.Claim claim = claim(block);
        Player player = event.getPlayer();
        if (claim == null || claim.canAccess(player.getName()) || plugin.getRanks().isOwnerLevel(player)) return false;
        boolean mayOpen = claim.canOpenContainers(player.getName())
                && (block.getType() == Material.ENDER_CHEST || block.getState() instanceof InventoryHolder);
        if (!mayOpen) event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (notify && !mayOpen && event.getHand() != EquipmentSlot.OFF_HAND)
            player.sendMessage(ColorUtil.c("&cВзаимодействие защищено встроенным приватом &f" + claim.owner + "&c."));
        return true;
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void interactEarly(PlayerInteractEvent event) { enforce(event, false); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void interactLate(PlayerInteractEvent event) { enforce(event, false); }

    // getBlock() is the actually affected block, including waterlogged blocks;
    // the clicked support can be outside a claim while the target is inside it.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void empty(PlayerBucketEmptyEvent event) {
        if (!canBuild(event.getPlayer(), event.getBlock().getLocation())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fill(PlayerBucketFillEvent event) {
        if (!canBuild(event.getPlayer(), event.getBlock().getLocation())) event.setCancelled(true);
    }

    private boolean pistonDenied(Block piston, Block head, List<Block> moved, BlockFace movement) {
        DataStore.Claim base = claim(piston), headClaim = claim(head);
        if (center(piston, base) || center(head, headClaim) || !sameRegion(base, headClaim)) return true;
        for (Block block : moved) {
            Block destination = block.getRelative(movement);
            DataStore.Claim sourceClaim = claim(block), destinationClaim = claim(destination);
            if (center(block, sourceClaim) || center(destination, destinationClaim)
                    || !sameRegion(base, sourceClaim) || !sameRegion(base, destinationClaim)) return true;
        }
        return false;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void extend(BlockPistonExtendEvent event) {
        if (pistonDenied(event.getBlock(), event.getBlock().getRelative(event.getDirection()), event.getBlocks(), event.getDirection()))
            event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void retract(BlockPistonRetractEvent event) {
        // Paper's direction on retract already points toward the piston.
        Block head = event.getBlock().getRelative(event.getDirection().getOppositeFace());
        if (pistonDenied(event.getBlock(), head, event.getBlocks(), event.getDirection())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void flow(BlockFromToEvent event) {
        Block from = event.getBlock();
        Material type = from.getType();
        if (type != Material.WATER && type != Material.LAVA
                && !(from.getBlockData() instanceof Waterlogged && ((Waterlogged) from.getBlockData()).isWaterlogged())) return;
        if (!sameRegion(claim(from), claim(event.getToBlock()))) event.setCancelled(true);
    }

    // Preserve only the registered center; ordinary blocks keep the existing
    // server raid/explosion policy rather than making whole claims blastproof.
    private boolean center(Block block) { return center(block, claim(block)); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entityExplosion(EntityExplodeEvent event) { event.blockList().removeIf(this::center); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void blockExplosion(BlockExplodeEvent event) { event.blockList().removeIf(this::center); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void changeBlock(EntityChangeBlockEvent event) { if (center(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void burn(BlockBurnEvent event) { if (center(event.getBlock())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void placeEntity(EntityPlaceEvent event) {
        if (!canBuild(event.getPlayer(), event.getBlock().getLocation())
                || !canBuild(event.getPlayer(), event.getEntity().getLocation())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void placeHanging(HangingPlaceEvent event) {
        if (!canBuild(event.getPlayer(), event.getBlock().getLocation())
                || !canBuild(event.getPlayer(), event.getEntity().getLocation())) event.setCancelled(true);
    }

    private boolean canEditEntity(Player player, Entity entity) {
        if (!canBuild(player, entity.getLocation())) return false;
        if (entity instanceof Hanging) {
            Block support = entity.getLocation().getBlock().getRelative(((Hanging) entity).getAttachedFace());
            return canBuild(player, support.getLocation());
        }
        return true;
    }
    private boolean denyEntityUse(Player player, Entity entity) {
        if (entity instanceof Hanging || entity instanceof ArmorStand) return !canEditEntity(player, entity);
        if (entity instanceof Vehicle && entity instanceof InventoryHolder) return !canOpen(player, entity.getLocation());
        return false;
    }
    // These event subclasses have distinct HandlerLists on Paper.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void useEntity(PlayerInteractEntityEvent event) {
        if (denyEntityUse(event.getPlayer(), event.getRightClicked())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void useEntityAt(PlayerInteractAtEntityEvent event) {
        if (denyEntityUse(event.getPlayer(), event.getRightClicked())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void manipulateArmorStand(PlayerArmorStandManipulateEvent event) {
        if (!canEditEntity(event.getPlayer(), event.getRightClicked())) event.setCancelled(true);
    }
    private static Player playerAttacker(Entity source) {
        if (source instanceof Player) return (Player) source;
        if (source instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) source).getShooter();
            if (shooter instanceof Player) return (Player) shooter;
        }
        return null;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent event) {
        Entity target = event.getEntity();
        if (!(target instanceof ArmorStand) && !(target instanceof Hanging)
                && !(target instanceof Vehicle && target instanceof InventoryHolder)) return;
        Player player = playerAttacker(event.getDamager());
        // Non-player environmental/explosion damage follows the server's existing raid rules.
        if (player != null && !canEditEntity(player, target)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void breakHanging(HangingBreakEvent event) {
        if (!(event instanceof HangingBreakByEntityEvent)) return;
        Player player = playerAttacker(((HangingBreakByEntityEvent) event).getRemover());
        if (player != null && !canEditEntity(player, event.getEntity())) event.setCancelled(true);
    }
}
