package net.kinggrief.core;

import org.bukkit.Bukkit;
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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Former prepared spawn worlds remain separate from the current gameplay world.
 * A marker identifies them only when the world is loaded; no world or chunk is
 * loaded by this guard. Unmarked/custom worlds are not classified as spawns.
 */
final class LegacySpawnGuard implements Listener {
    private final KingGriefCore plugin;
    private final Set<UUID> preparedWorlds = new HashSet<UUID>();

    LegacySpawnGuard(KingGriefCore plugin) {
        this.plugin = plugin;
        for (World world : Bukkit.getWorlds()) remember(world);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) { remember(event.getWorld()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) { preparedWorlds.remove(event.getWorld().getUID()); }

    private void remember(World world) {
        if (hasPreparedMarker(world.getWorldFolder().toPath())) preparedWorlds.add(world.getUID());
        else preparedWorlds.remove(world.getUID());
    }

    private static boolean hasPreparedMarker(Path folder) {
        try {
            if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) return false;
            Path old = folder.resolve(".kinggrief_prebuilt_v70");
            Path built = folder.resolve(".spawn_v70_village_ok");
            if (validMarker(old, "KINGGRIEF Royal Winter") && Files.isRegularFile(built, LinkOption.NOFOLLOW_LINKS)) return true;
            return validMarker(folder.resolve(".kinggrief_prebuilt_v66"), "KINGGRIEF Royal Winter spawn v66")
                    && Files.isRegularFile(folder.resolve(".spawn_v66_royal_ok"), LinkOption.NOFOLLOW_LINKS);
        } catch (IOException | SecurityException error) {
            return false;
        }
    }

    private static boolean validMarker(Path marker, String prefix) throws IOException {
        return Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS) && Files.size(marker) <= 128
                && new String(Files.readAllBytes(marker), StandardCharsets.US_ASCII).startsWith(prefix);
    }

    boolean isLegacy(World world) {
        return world != null && preparedWorlds.contains(world.getUID())
                && !plugin.getSpawnEngine().isInSpawnWorld(new Location(world, 0, 0, 0));
    }
    private boolean canEdit(Player player) { return plugin.getRanks().canModifySpawn(player); }
    private boolean canAccess(Player player) { return plugin.getRanks().isOwnerLevel(player); }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (isLegacy(event.getBlock().getWorld()) && !canEdit(event.getPlayer())) {
            event.setCancelled(true);
            ColorUtil.actionBar(event.getPlayer(), "&cСтарый спавн защищён. Изменять его могут OWNER и ТЕХ.АДМИН в креативе.");
        }
    }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isLegacy(event.getBlock().getWorld()) && !canEdit(event.getPlayer())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEmpty(PlayerBucketEmptyEvent event) {
        if (isLegacy(event.getBlock().getWorld()) && !canEdit(event.getPlayer())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFill(PlayerBucketFillEvent event) {
        if (isLegacy(event.getBlock().getWorld()) && !canEdit(event.getPlayer())) event.setCancelled(true);
    }

    /** Called by Core before custom items: setting Bukkit's use flags alone
     * would not stop a later plugin handler from applying an item ability. */
    boolean enforceInteraction(PlayerInteractEvent event, boolean notify) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null
                || !isLegacy(block.getWorld()) || canAccess(event.getPlayer())) return false;
        event.setUseItemInHand(Event.Result.DENY);
        // Ender chests contain the player's private inventory, not world storage.
        if (block.getType() != Material.ENDER_CHEST && block.getState() instanceof InventoryHolder) {
            event.setUseInteractedBlock(Event.Result.DENY);
            if (notify) event.getPlayer().sendMessage(ColorUtil.c("&cКонтейнер старого спавна защищён."));
        }
        return true;
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) { enforceInteraction(event, false); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractLate(PlayerInteractEvent event) { enforceInteraction(event, false); }

    private boolean legacyStorage(Inventory inventory) {
        if (inventory instanceof DoubleChestInventory) {
            DoubleChestInventory pair = (DoubleChestInventory) inventory;
            Location left = pair.getLeftSide().getLocation(), right = pair.getRightSide().getLocation();
            return (left != null && isLegacy(left.getWorld())) || (right != null && isLegacy(right.getWorld()));
        }
        InventoryHolder holder;
        try { holder = inventory.getHolder(false); }
        catch (NoSuchMethodError | AbstractMethodError ignored) { holder = inventory.getHolder(); }
        if (holder instanceof BlockState) return isLegacy(((BlockState) holder).getWorld());
        if (holder instanceof Entity) return isLegacy(((Entity) holder).getWorld());
        return false; // virtual menus and the player's personal ender chest
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player && !canAccess((Player) event.getPlayer()) && legacyStorage(event.getInventory()))
            event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player && !canAccess((Player) event.getWhoClicked())
                && legacyStorage(event.getView().getTopInventory())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player && !canAccess((Player) event.getWhoClicked())
                && legacyStorage(event.getView().getTopInventory())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (isLegacy(event.getLocation().getWorld())) { event.blockList().clear(); event.setCancelled(true); }
        else event.blockList().removeIf(block -> isLegacy(block.getWorld()));
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (isLegacy(event.getBlock().getWorld())) { event.blockList().clear(); event.setCancelled(true); }
        else event.blockList().removeIf(block -> isLegacy(block.getWorld()));
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) { if (isLegacy(event.getBlock().getWorld())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) { if (isLegacy(event.getBlock().getWorld())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExtend(BlockPistonExtendEvent event) { if (isLegacy(event.getBlock().getWorld())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRetract(BlockPistonRetractEvent event) { if (isLegacy(event.getBlock().getWorld())) event.setCancelled(true); }

    private boolean allowedRemover(Entity remover) {
        if (remover instanceof Projectile && ((Projectile) remover).getShooter() instanceof Player)
            remover = (Player) ((Projectile) remover).getShooter();
        return remover instanceof Player && canEdit((Player) remover);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (!isLegacy(event.getEntity().getWorld())) return;
        Entity remover = event instanceof HangingBreakByEntityEvent ? ((HangingBreakByEntityEvent) event).getRemover() : null;
        if (!allowedRemover(remover)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        if (isLegacy(entity.getWorld()) && (entity instanceof Hanging || entity instanceof ArmorStand || entity instanceof Display)
                && !allowedRemover(event.getDamager())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityUse(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (isLegacy(entity.getWorld()) && (entity instanceof Hanging || entity instanceof ArmorStand || entity instanceof Display)
                && !canEdit(event.getPlayer())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (isLegacy(event.getRightClicked().getWorld()) && !canEdit(event.getPlayer())) event.setCancelled(true);
    }
}
