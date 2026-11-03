package net.kinggrief.core;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Protect physical decorations and world storage in the active spawn. Legacy
 * worlds are only considered when LegacySpawnGuard already recognized a marker;
 * no world/chunk is loaded and unrelated survival worlds remain untouched.
 * Plugin-written blocks and other plugins bypassing Bukkit events are out of scope.
 */
final class SpawnWorldGuard implements Listener {
    private final KingGriefCore plugin;
    SpawnWorldGuard(KingGriefCore plugin) { this.plugin = plugin; }
    private boolean active(World world) {
        return world != null && plugin.getSpawnEngine().isInSpawnWorld(new Location(world, 0, 0, 0));
    }
    private boolean protectedWorld(World world) {
        return active(world) || (plugin.getLegacySpawnGuard() != null && plugin.getLegacySpawnGuard().isLegacy(world));
    }
    private boolean canEdit(Player player) { return plugin.getRanks().canModifySpawn(player); }
    private boolean canAccess(Player player) { return plugin.getRanks().isOwnerLevel(player); }
    private boolean environmentEnabled() { return plugin.getConfig().getBoolean("spawn-protection.protect-environment", true); }

    // LegacySpawnGuard independently blocks its own pistons even if the active
    // environment protection is explicitly disabled by the operator.
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onExtend(BlockPistonExtendEvent event) {
        if (environmentEnabled() && active(event.getBlock().getWorld())) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onRetract(BlockPistonRetractEvent event) {
        if (environmentEnabled() && active(event.getBlock().getWorld())) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!environmentEnabled() || !protectedWorld(event.getBlock().getWorld())) return;
        if (!(event.getEntity() instanceof Player) || !canEdit((Player) event.getEntity())) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onMelt(BlockFadeEvent event) {
        if (!environmentEnabled() || !protectedWorld(event.getBlock().getWorld())) return;
        Material material = event.getBlock().getType();
        if (material == Material.ICE || material == Material.FROSTED_ICE || material == Material.SNOW
                || material == Material.SNOW_BLOCK) event.setCancelled(true);
    }

    /** Only vanilla right-click mutations, not food, GUI items, case/gift clicks or buttons. */
    private boolean mutatingTool(ItemStack item) {
        if (item == null) return false;
        String name=item.getType().name();
        return name.endsWith("_AXE") || name.endsWith("_HOE") || name.endsWith("_SHOVEL")
                || name.endsWith("_SPAWN_EGG") || name.equals("BONE_MEAL") || name.equals("FLINT_AND_STEEL") || name.equals("FIRE_CHARGE")
                || name.equals("HONEYCOMB") || name.equals("INK_SAC") || name.equals("GLOW_INK_SAC") || name.equals("SHEARS");
    }
    private void enforceTool(PlayerInteractEvent event) {
        Block block=event.getClickedBlock();
        if (event.getAction()==Action.RIGHT_CLICK_BLOCK && block!=null && protectedWorld(block.getWorld())
                && !canEdit(event.getPlayer()) && mutatingTool(event.getItem())) event.setUseItemInHand(Event.Result.DENY);
    }
    @EventHandler(priority=EventPriority.LOWEST)
    public void onTool(PlayerInteractEvent event) { enforceTool(event); }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void onToolLate(PlayerInteractEvent event) { enforceTool(event); }

    /** Virtual inventories and the player's private ender chest are not world storage. */
    private boolean activeStorage(Inventory inventory) {
        if (inventory == null || inventory.getType() == InventoryType.ENDER_CHEST) return false;
        if (inventory instanceof DoubleChestInventory) {
            DoubleChestInventory pair=(DoubleChestInventory)inventory;
            Location left=pair.getLeftSide().getLocation(),right=pair.getRightSide().getLocation();
            return (left!=null && active(left.getWorld())) || (right!=null && active(right.getWorld()));
        }
        InventoryHolder holder;
        try { holder=inventory.getHolder(false); }
        catch (NoSuchMethodError | AbstractMethodError ignored) { holder=inventory.getHolder(); }
        if (holder instanceof BlockState) return active(((BlockState)holder).getWorld());
        if (holder instanceof Entity && !(holder instanceof Player)) return active(((Entity)holder).getWorld());
        return false;
    }
    private boolean guardedStorage(Inventory inventory) {
        if (inventory == null || inventory.getType() == InventoryType.ENDER_CHEST) return false;
        if (inventory instanceof DoubleChestInventory) {
            DoubleChestInventory pair=(DoubleChestInventory)inventory;
            Location left=pair.getLeftSide().getLocation(),right=pair.getRightSide().getLocation();
            return (left!=null && protectedWorld(left.getWorld())) || (right!=null && protectedWorld(right.getWorld()));
        }
        InventoryHolder holder;
        try { holder=inventory.getHolder(false); }
        catch (NoSuchMethodError | AbstractMethodError ignored) { holder=inventory.getHolder(); }
        if (holder instanceof BlockState) return protectedWorld(((BlockState)holder).getWorld());
        if (holder instanceof Entity && !(holder instanceof Player)) return protectedWorld(((Entity)holder).getWorld());
        return false;
    }
    // Automated inventories have no player identity; default to protecting the
    // contents. Operators can opt out for intentional spawn machinery.
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onTransport(InventoryMoveItemEvent event) {
        if (plugin.getConfig().getBoolean("spawn-protection.block-inventory-transport", true)
                && (guardedStorage(event.getSource()) || guardedStorage(event.getDestination()))) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onPickup(InventoryPickupItemEvent event) {
        if (plugin.getConfig().getBoolean("spawn-protection.block-inventory-transport", true)
                && (guardedStorage(event.getInventory()) || protectedWorld(event.getItem().getWorld()))) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player && !canAccess((Player)event.getPlayer()) && activeStorage(event.getInventory())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ColorUtil.c("&cКонтейнер спавна защищён."));
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player && !canAccess((Player)event.getWhoClicked())
                && activeStorage(event.getView().getTopInventory())) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player && !canAccess((Player)event.getWhoClicked())
                && activeStorage(event.getView().getTopInventory())) event.setCancelled(true);
    }
    private boolean allowedRemover(Entity remover) {
        if (remover instanceof Projectile && ((Projectile)remover).getShooter() instanceof Player)
            remover=(Player)((Projectile)remover).getShooter();
        return remover instanceof Player && canEdit((Player)remover);
    }
    // Armor stands, vehicles, crystals and hanging decorations are placed as
    // entities rather than blocks, so BlockPlaceEvent alone cannot protect spawn.
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onEntityPlace(EntityPlaceEvent event) {
        if (protectedWorld(event.getEntity().getWorld())
                && (event.getPlayer()==null || !canEdit(event.getPlayer()))) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (protectedWorld(event.getEntity().getWorld())
                && (event.getPlayer()==null || !canEdit(event.getPlayer()))) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (!active(event.getEntity().getWorld())) return;
        Entity remover=event instanceof HangingBreakByEntityEvent ? ((HangingBreakByEntityEvent)event).getRemover() : null;
        if (!allowedRemover(remover)) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity entity=event.getEntity();
        if (active(entity.getWorld()) && (entity instanceof Hanging || entity instanceof ArmorStand || entity instanceof Display)
                && !allowedRemover(event.getDamager())) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onEntityUse(PlayerInteractEntityEvent event) {
        Entity entity=event.getRightClicked();
        if (active(entity.getWorld()) && (entity instanceof Hanging || entity instanceof ArmorStand || entity instanceof Display)
                && !canEdit(event.getPlayer())) event.setCancelled(true);
    }
}
