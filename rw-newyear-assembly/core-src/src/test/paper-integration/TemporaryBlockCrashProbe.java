package net.kinggrief.probe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import net.kinggrief.core.KingGriefCore;

/** Disposable test: chunk save, actual SIGKILL, restart, and same-material replacement. */
public final class TemporaryBlockCrashProbe extends JavaPlugin implements Listener {
    private KingGriefCore core;
    private World world;
    private Player player;
    private Object gameplay, recovery;
    private Method place, restore, record, hasRecord;
    private int phase, checks;
    private boolean started, done, cancelPlacement;
    private Block cancelled;

    @Override public void onEnable() {
        try {
            phase = getConfig().getInt("phase", 1);
            core = (KingGriefCore) Bukkit.getPluginManager().getPlugin("KingGriefCore");
            world = Bukkit.getWorlds().get(0);
            Field listenerField = KingGriefCore.class.getDeclaredField("gameplayListener");
            listenerField.setAccessible(true);
            gameplay = listenerField.get(core);
            Field recoveryField = gameplay.getClass().getDeclaredField("tempRecovery");
            recoveryField.setAccessible(true);
            recovery = recoveryField.get(gameplay);
            place = gameplay.getClass().getDeclaredMethod("placeTemporaryBlock", Player.class, Block.class, Material.class, Map.class);
            restore = gameplay.getClass().getDeclaredMethod("restoreTemporaryBlock", Block.class);
            record = recovery.getClass().getDeclaredMethod("record", Block.class, BlockData.class, BlockData.class);
            hasRecord = recovery.getClass().getDeclaredMethod("hasRecord", Block.class);
            place.setAccessible(true); restore.setAccessible(true); record.setAccessible(true); hasRecord.setAccessible(true);
            Bukkit.getPluginManager().registerEvents(this, this);
            if (phase > 1) initialRestartChecks();
        } catch (Throwable error) { fail(error); }
    }

    @EventHandler public void join(PlayerJoinEvent event) {
        if (!event.getPlayer().getName().equals("EcoBuyer") || started) return;
        player = event.getPlayer(); started = true;
        later(40, () -> { if (phase == 1) begin(); else if (phase == 2) afterCrash(); else afterSecondCrash(); });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void lateCancellation(BlockPlaceEvent event) {
        if (cancelPlacement && event.getBlockPlaced().equals(cancelled)) event.setCancelled(true);
    }

    private Block b(int x, int y, int z) { return world.getBlockAt(x, y, z); }
    private boolean place(Block block, Material material) throws Exception {
        return (Boolean) place.invoke(gameplay, player, block, material, new HashMap<Block, BlockData>());
    }
    private boolean record(Block block, Material old, Material expected) throws Exception {
        return (Boolean) record.invoke(recovery, block, old.createBlockData(), expected.createBlockData());
    }
    private boolean has(Block block) throws Exception { return (Boolean) hasRecord.invoke(recovery, block); }
    private void later(long ticks, Throwing action) {
        Bukkit.getScheduler().runTaskLater(this, () -> { try { if (!done) action.run(); } catch (Throwable error) { fail(error); } }, ticks);
    }
    private interface Throwing { void run() throws Exception; }
    private void ok(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++; getLogger().info("KG-TEMP PASS " + message);
    }
    private void fail(Throwable error) {
        if (done) return;
        done = true; getLogger().severe("KG-TEMP RESULT FAIL " + error); error.printStackTrace();
    }
    private void ready() { done = true; getLogger().info("KG-TEMP READY phase=" + phase + " checks=" + checks); }

    private void begin() throws Exception {
        player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().setItemInMainHand(new ItemStack(Material.NETHERITE_PICKAXE));
        player.teleport(new Location(world, 6.5, 161, 6.5));
        world.getChunkAt(0, 0).setForceLoaded(true);
        world.getChunkAt(12, 12).setForceLoaded(true);
        Block expensive = b(5, 160, 5), cave = b(197, 160, 197), changed = b(198, 160, 197), negative = b(6, -60, 5);
        // An all-air section is optimized to ordinary AIR by Paper, even when
        // its only written cell was CAVE_AIR. Keep this section nonempty.
        b(196, 160, 197).setType(Material.STONE, false);
        expensive.setType(Material.AIR, false); cave.setType(Material.CAVE_AIR, false);
        changed.setType(Material.AIR, false); negative.setType(Material.CAVE_AIR, false);
        ok(cave.getType() == Material.CAVE_AIR && negative.getType() == Material.CAVE_AIR,
                "fixture starts with actual cave-air block data");
        ok(place(expensive, Material.NETHERITE_BLOCK) && has(expensive), "valuable temporary block has a chunk record");
        ok(place(cave, Material.COBWEB) && has(cave), "cave air has an independent record");
        ok(place(changed, Material.OBSIDIAN) && has(changed), "foreign replacement fixture is recorded");
        ok(place(negative, Material.BLUE_ICE) && has(negative), "negative world height is recorded");
        changed.setType(Material.DIAMOND_BLOCK, false);
        ok(!player.breakBlock(expensive) && expensive.getType() == Material.NETHERITE_BLOCK,
                "a live temporary block cannot be mined for loot");
        cancelled = b(199, 160, 197); cancelled.setType(Material.AIR, false); cancelPlacement = true;
        boolean placed = place(cancelled, Material.COBWEB); cancelPlacement = false;
        ok(!placed && cancelled.getType() == Material.AIR && !has(cancelled),
                "late cancellation restores air and removes the record");
        Block ordinary = b(200, 160, 197); ordinary.setType(Material.CAVE_AIR, false);
        ok(place(ordinary, Material.COBWEB), "ordinary temporary block placed");
        restore.invoke(gameplay, ordinary);
        ok(ordinary.getType() == Material.CAVE_AIR && !has(ordinary), "normal cleanup restores the exact air and removes the record");
        Block stale = b(201, 160, 197); stale.setType(Material.CAVE_AIR, false);
        ok(record(stale, Material.CAVE_AIR, Material.COBWEB), "orphan record can be staged for protection test");
        stale.setType(Material.COBWEB, false);
        ok(!player.breakBlock(stale) && stale.getType() == Material.COBWEB,
                "a saved but not yet recovered block cannot be mined");
        BlockPistonExtendEvent piston = new BlockPistonExtendEvent(b(200, 160, 197), java.util.List.of(stale), BlockFace.EAST);
        Bukkit.getPluginManager().callEvent(piston);
        ok(piston.isCancelled(), "a piston cannot move a saved but not yet recovered block");
        later(30, () -> {
            ok(stale.getType() == Material.CAVE_AIR && !has(stale), "orphan is recovered on the server thread");
            ok(expensive.getType() == Material.NETHERITE_BLOCK && cave.getType() == Material.COBWEB,
                    "startup scanning and orphan recovery do not remove live structures");
            capacityAndCorruption();
        });
    }

    private void capacityAndCorruption() throws Exception {
        world.getChunkAt(14, 0).setForceLoaded(true);
        for (int i = 0; i < 512; i++) {
            Block block = b(224 + (i % 16), 160 + (i / 256), (i / 16) % 16);
            block.setType(Material.AIR, false);
            if (!record(block, Material.AIR, Material.COBWEB)) throw new AssertionError("record capacity at " + i);
        }
        Block extra = b(224, 162, 0); extra.setType(Material.AIR, false);
        ok(!record(extra, Material.AIR, Material.COBWEB), "a chunk refuses records beyond its 512-block cap");
        ok(has(b(224, 160, 0)), "capped chunk is queued for recovery");
        Chunk corruptChunk = world.getChunkAt(15, 0); corruptChunk.setForceLoaded(true);
        Block corrupt = b(240, 160, 0); corrupt.setType(Material.NETHERITE_BLOCK, false);
        PersistentDataContainer data = corruptChunk.getPersistentDataContainer();
        NamespacedKey count = new NamespacedKey(core, "tmprecovery_count");
        NamespacedKey entry = new NamespacedKey(core, "tmprecovery_b_0_160_0");
        data.set(count, PersistentDataType.INTEGER, 1);
        data.set(entry, PersistentDataType.STRING, "minecraft:stone|minecraft:netherite_block");
        ok(has(corrupt), "invalid record is detected but not trusted");
        Chunk missingIndexChunk = world.getChunkAt(16, 0); missingIndexChunk.setForceLoaded(true);
        Block missingIndex = b(256, 160, 0); missingIndex.setType(Material.OBSIDIAN, false);
        PersistentDataContainer missingData = missingIndexChunk.getPersistentDataContainer();
        NamespacedKey missingEntry = new NamespacedKey(core, "tmprecovery_b_0_160_0");
        missingData.set(missingEntry, PersistentDataType.STRING, "minecraft:air|minecraft:obsidian");
        Bukkit.getPluginManager().callEvent(new org.bukkit.event.world.ChunkLoadEvent(missingIndexChunk, false));
        later(45, () -> {
            ok(!has(b(224, 160, 0)) && b(224, 160, 0).getType() == Material.AIR,
                    "incremental recovery clears the capped chunk without changing different blocks");
            ok(corrupt.getType() == Material.NETHERITE_BLOCK && has(corrupt),
                    "invalid original data is left for inspection without deleting a block");
            ok(missingIndex.getType() == Material.AIR && !missingData.has(missingEntry),
                    "chunk-load scan repairs a missing index and recovers its orphan record");
            data.remove(entry); data.remove(count);
            ok(b(5, 160, 5).getType() == Material.NETHERITE_BLOCK && b(197, 160, 197).getType() == Material.COBWEB,
                    "live fixtures remain present before saved-chunk crash");
            ready();
        });
    }

    private void initialRestartChecks() throws Exception {
        // These chunks were explicitly saved before SIGKILL. The remote chunk
        // loads here before the scheduled recovery worker is allowed to run.
        world.getChunkAt(0, 0); world.getChunkAt(12, 12);
        if (phase == 2) {
            ok(b(5, 160, 5).getType() == Material.NETHERITE_BLOCK && has(b(5, 160, 5)),
                    "saved valuable block and its record are present before recovery");
            ok(b(197, 160, 197).getType() == Material.COBWEB && has(b(197, 160, 197)),
                    "remote saved chunk retains its temporary block and record");
            ok(b(198, 160, 197).getType() == Material.DIAMOND_BLOCK && has(b(198, 160, 197)),
                    "foreign replacement and original record survived the crash");
            ok(b(6, -60, 5).getType() == Material.BLUE_ICE && has(b(6, -60, 5)),
                    "negative-height saved block and record survived the crash");
        } else {
            ok(b(5, 160, 5).getType() == Material.NETHERITE_BLOCK && !has(b(5, 160, 5)),
                    "later same-material block has no stale record before recovery");
            ok(b(197, 160, 197).getType() == Material.COBWEB && !has(b(197, 160, 197)),
                    "later same-material web has no stale record before recovery");
        }
    }

    private void afterCrash() throws Exception {
        ok(b(5, 160, 5).getType() == Material.AIR && !has(b(5, 160, 5)),
                "saved valuable temporary block is restored after SIGKILL");
        Material remoteType = b(197, 160, 197).getType();
        boolean remoteRecord = has(b(197, 160, 197));
        ok(remoteType == Material.CAVE_AIR && !remoteRecord,
                "remote chunk restores the exact original cave air (type=" + remoteType + ", record=" + remoteRecord + ")");
        ok(b(198, 160, 197).getType() == Material.DIAMOND_BLOCK && !has(b(198, 160, 197)),
                "a different block placed by another plugin is preserved");
        ok(b(6, -60, 5).getType() == Material.CAVE_AIR && !has(b(6, -60, 5)),
                "negative-height block is restored correctly");
        b(5, 160, 5).setType(Material.NETHERITE_BLOCK, false);
        b(197, 160, 197).setType(Material.COBWEB, false);
        ready();
    }

    private void afterSecondCrash() throws Exception {
        ok(b(5, 160, 5).getType() == Material.NETHERITE_BLOCK && !has(b(5, 160, 5)),
                "legitimate identical replacement survives another saved crash and scan");
        ok(b(197, 160, 197).getType() == Material.COBWEB && !has(b(197, 160, 197)),
                "legitimate identical remote replacement survives another saved crash and scan");
        ok(b(198, 160, 197).getType() == Material.DIAMOND_BLOCK,
                "foreign replacement remains untouched after another restart");
        ready();
    }
}
