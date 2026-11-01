package net.kinggrief.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Best-effort recovery of temporary combat blocks in chunks saved while Core
 * was running. Records and block data belong to the same chunk, but neither a
 * chunk save nor its relationship to inventory data is a transaction/fsync.
 * All methods run on the server thread and never load an unloaded chunk.
 */
final class TemporaryBlockRecovery {
    private static final String PREFIX = "tmprecovery_b_";
    private static final int MAX_PER_CHUNK = 512;
    private static final int SCAN_CHUNKS_PER_TICK = 24;
    private static final int RECOVER_BLOCKS_PER_TICK = 64;
    private static final int RECOVER_CHUNKS_PER_TICK = 4;
    private final KingGriefCore plugin;
    private final Predicate<Block> live;
    private final NamespacedKey countKey;
    private final ArrayDeque<Work> queue = new ArrayDeque<Work>();
    private final Set<String> queued = new HashSet<String>();
    private final List<World> startupWorlds;
    private int startupWorldIndex, startupChunkIndex;
    private Chunk[] startupChunks;
    private Work current;
    private long lastFailureWarning, lastInvalidWarning;

    private static final class Work {
        final UUID worldId;
        final int x, z;
        final String id;
        List<NamespacedKey> keys;
        int index;
        boolean invalid;
        Work(Chunk chunk) {
            worldId = chunk.getWorld().getUID();
            x = chunk.getX();
            z = chunk.getZ();
            id = worldId + ":" + x + ":" + z;
        }
    }

    TemporaryBlockRecovery(KingGriefCore plugin, Predicate<Block> live) {
        this.plugin = plugin;
        this.live = live;
        this.countKey = new NamespacedKey(plugin, "tmprecovery_count");
        this.startupWorlds = new ArrayList<World>(Bukkit.getWorlds());
    }

    void start() {
        new BukkitRunnable() {
            @Override public void run() {
                try { scanInitiallyLoaded(); }
                catch (RuntimeException | LinkageError error) { warnFailure("Temporary block startup scan failed", error); }
                try { recoverQueued(); }
                catch (RuntimeException | LinkageError error) { warnFailure("Temporary block recovery worker failed", error); }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private NamespacedKey blockKey(Block block) {
        return new NamespacedKey(plugin, PREFIX + (block.getX() & 15) + "_" + block.getY() + "_" + (block.getZ() & 15));
    }

    private boolean isRecordKey(NamespacedKey key) {
        return key.getNamespace().equals(countKey.getNamespace()) && key.getKey().startsWith(PREFIX);
    }

    private int recordCount(PersistentDataContainer data) {
        int n = 0;
        for (NamespacedKey key : data.getKeys()) if (isRecordKey(key)) n++;
        return n;
    }

    private void syncCount(PersistentDataContainer data) {
        int count = recordCount(data);
        if (count == 0) data.remove(countKey);
        else data.set(countKey, PersistentDataType.INTEGER, Integer.valueOf(Math.min(count, MAX_PER_CHUNK)));
    }

    /** Register before changing an air block; false means no placement. */
    boolean record(Block block, BlockData original, BlockData expected) {
        if (!loaded(block) || !isAir(original.getMaterial()) || !allowed(expected.getMaterial())) return false;
        NamespacedKey key = blockKey(block);
        try {
            PersistentDataContainer data = block.getChunk().getPersistentDataContainer();
            if (data.has(key)) {
                if (!data.has(countKey)) syncCount(data);
                enqueue(block.getChunk());
                return false;
            }
            Integer stored = data.get(countKey, PersistentDataType.INTEGER);
            int count;
            boolean recounted = stored == null || stored.intValue() <= 0 || stored.intValue() > MAX_PER_CHUNK;
            if (recounted) {
                // A missing or malformed index may still have records. Recount
                // before adding, rather than silently hiding orphan records.
                count = recordCount(data);
                if (count > 0) {
                    data.set(countKey, PersistentDataType.INTEGER, Integer.valueOf(Math.min(count, MAX_PER_CHUNK)));
                    enqueue(block.getChunk());
                }
            } else count = stored.intValue();
            if (count >= MAX_PER_CHUNK) return false;
            data.set(key, PersistentDataType.STRING, original.getAsString() + "|" + expected.getAsString());
            try {
                data.set(countKey, PersistentDataType.INTEGER, Integer.valueOf(count + 1));
            } catch (RuntimeException | LinkageError error) {
                data.remove(key);
                throw error;
            }
            return true;
        } catch (RuntimeException | LinkageError error) {
            warnFailure("Could not register a temporary block", error);
            return false;
        }
    }

    /** Forget only after restoration or after a different block is observed. */
    void forget(Block block) {
        if (!loaded(block)) return;
        try {
            PersistentDataContainer data = block.getChunk().getPersistentDataContainer();
            NamespacedKey key = blockKey(block);
            if (!data.has(key)) return;
            data.remove(key);
            Integer old = data.get(countKey, PersistentDataType.INTEGER);
            if (old == null || old.intValue() <= 1 || old.intValue() > MAX_PER_CHUNK) syncCount(data);
            else data.set(countKey, PersistentDataType.INTEGER, Integer.valueOf(old.intValue() - 1));
        } catch (RuntimeException | LinkageError error) {
            warnFailure("Could not remove a temporary block record", error);
            enqueue(block.getChunk());
        }
    }

    /** Protect saved blocks even before the incremental recovery reaches them. */
    boolean hasRecord(Block block) {
        if (!loaded(block)) return false;
        try {
            PersistentDataContainer data = block.getChunk().getPersistentDataContainer();
            if (!data.has(countKey) || !data.has(blockKey(block))) return false;
            enqueue(block.getChunk());
            return true;
        } catch (RuntimeException | LinkageError error) {
            warnFailure("Could not check a temporary block record", error);
            return true; // Do not turn an unreadable recovery record into loot.
        }
    }

    void onChunkLoad(Chunk chunk) { repairIndexAndEnqueue(chunk); }

    private void repairIndexAndEnqueue(Chunk chunk) {
        try {
            if (!chunk.getWorld().isChunkLoaded(chunk.getX(), chunk.getZ())) return;
            PersistentDataContainer data = chunk.getPersistentDataContainer();
            // A partial/corrupt index must not hide otherwise intact records.
            // The common empty-PDC case does not allocate a key set.
            if (!data.has(countKey) && !data.isEmpty() && recordCount(data) > 0) syncCount(data);
            enqueue(chunk);
        } catch (RuntimeException | LinkageError error) {
            warnFailure("Could not inspect temporary block records", error);
        }
    }

    void retry(Block block) {
        if (loaded(block)) enqueue(block.getChunk());
    }

    private boolean loaded(Block block) {
        return block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4);
    }

    private void enqueue(Chunk chunk) {
        try {
            if (!chunk.getWorld().isChunkLoaded(chunk.getX(), chunk.getZ())
                    || !chunk.getPersistentDataContainer().has(countKey)) return;
            Work work = new Work(chunk);
            if (queued.add(work.id)) queue.addLast(work);
        } catch (RuntimeException | LinkageError error) {
            warnFailure("Could not queue temporary block recovery", error);
        }
    }

    private void scanInitiallyLoaded() {
        int remaining = SCAN_CHUNKS_PER_TICK;
        while (remaining > 0) {
            if (startupChunks == null || startupChunkIndex >= startupChunks.length) {
                if (startupWorldIndex >= startupWorlds.size()) {
                    startupChunks = null;
                    startupWorlds.clear();
                    startupWorldIndex = 0;
                    return;
                }
                World world = startupWorlds.get(startupWorldIndex++);
                if (Bukkit.getWorld(world.getUID()) == null) continue;
                // getLoadedChunks returns only existing chunks and does not generate terrain.
                startupChunks = world.getLoadedChunks();
                startupChunkIndex = 0;
                if (startupChunks.length == 0) continue;
            }
            repairIndexAndEnqueue(startupChunks[startupChunkIndex++]);
            remaining--;
        }
    }

    private void recoverQueued() {
        int blocks = 0, chunks = 0;
        while (blocks < RECOVER_BLOCKS_PER_TICK && chunks < RECOVER_CHUNKS_PER_TICK) {
            if (current == null) current = queue.pollFirst();
            if (current == null) return;
            World world = Bukkit.getWorld(current.worldId);
            if (world == null || !world.isChunkLoaded(current.x, current.z)) {
                finishCurrent();
                chunks++;
                continue;
            }
            Chunk chunk = world.getChunkAt(current.x, current.z);
            try {
                PersistentDataContainer data = chunk.getPersistentDataContainer();
                if (current.keys == null) {
                    current.keys = new ArrayList<NamespacedKey>();
                    for (NamespacedKey key : data.getKeys()) {
                        if (isRecordKey(key)) current.keys.add(key);
                    }
                }
                if (current.index >= current.keys.size()) {
                    syncCount(data);
                    long now = System.currentTimeMillis();
                    if (current.invalid && now - lastInvalidWarning >= 5000L) {
                        lastInvalidWarning = now;
                        plugin.getLogger().warning("Invalid temporary-block recovery record in "
                                + world.getName() + " chunk " + current.x + "," + current.z + "; left unchanged for inspection.");
                    }
                    finishCurrent();
                    chunks++;
                    continue;
                }
                NamespacedKey key = current.keys.get(current.index++);
                blocks++;
                if (!data.has(key)) continue;
                Block block = decodeBlock(chunk, key);
                String value = data.get(key, PersistentDataType.STRING);
                BlockData[] states = decodeStates(value);
                if (block == null || states == null) {
                    current.invalid = true;
                    continue;
                }
                // A new structure may have been created while startup scanning
                // was running. Its own timer remains responsible for cleanup.
                if (live.test(block)) continue;
                if (block.getBlockData().equals(states[1])) block.setBlockData(states[0], false);
                // If a different block is present, keep it and discard this record.
                data.remove(key);
            } catch (RuntimeException | LinkageError error) {
                current.invalid = true;
                warnFailure("Temporary block recovery failed", error);
                // Continue with other records; the failed one remains recorded.
                // A repeated failure while listing/finalizing must not spin on
                // the main thread without consuming the block budget.
                if (current.keys == null || current.index >= current.keys.size()) { finishCurrent(); chunks++; }
            }
        }
    }

    private void finishCurrent() {
        if (current != null) queued.remove(current.id);
        current = null;
    }

    private Block decodeBlock(Chunk chunk, NamespacedKey key) {
        String[] parts = key.getKey().substring(PREFIX.length()).split("_", -1);
        if (parts.length != 3) return null;
        try {
            int x = Integer.parseInt(parts[0]), y = Integer.parseInt(parts[1]), z = Integer.parseInt(parts[2]);
            if (x < 0 || x > 15 || z < 0 || z > 15
                    || y < chunk.getWorld().getMinHeight() || y >= chunk.getWorld().getMaxHeight()) return null;
            return chunk.getBlock(x, y, z);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private BlockData[] decodeStates(String value) {
        if (value == null || value.length() > 512) return null;
        int separator = value.indexOf('|');
        if (separator <= 0 || separator != value.lastIndexOf('|') || separator == value.length() - 1) return null;
        try {
            BlockData original = Bukkit.createBlockData(value.substring(0, separator));
            BlockData expected = Bukkit.createBlockData(value.substring(separator + 1));
            if (!isAir(original.getMaterial()) || !allowed(expected.getMaterial())) return null;
            return new BlockData[]{original, expected};
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private static boolean isAir(Material material) {
        return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }

    private static boolean allowed(Material material) {
        switch (material) {
            case COBWEB: case OBSIDIAN: case CRYING_OBSIDIAN: case BLUE_ICE: case PACKED_ICE:
            case AMETHYST_BLOCK: case PURPUR_BLOCK: case EMERALD_BLOCK: case OXIDIZED_COPPER:
            case PRISMARINE_BRICKS: case GOLD_BLOCK: case GILDED_BLACKSTONE: case NETHERITE_BLOCK:
            case POLISHED_BLACKSTONE_BRICKS: case REDSTONE_BLOCK: case CRIMSON_HYPHAE:
            case NETHER_WART_BLOCK: case RED_NETHER_BRICKS: case MAGMA_BLOCK: case SEA_LANTERN:
            case PEARLESCENT_FROGLIGHT: case SHROOMLIGHT: case OCHRE_FROGLIGHT: case GLOWSTONE:
            case BLACK_STAINED_GLASS: case PURPLE_STAINED_GLASS: case LIGHT_BLUE_STAINED_GLASS:
            case LIME_STAINED_GLASS: case MAGENTA_STAINED_GLASS: case ORANGE_STAINED_GLASS:
            case RED_STAINED_GLASS: case YELLOW_STAINED_GLASS:
                return true;
            default:
                return false;
        }
    }

    private void warnFailure(String message, Throwable error) {
        long now = System.currentTimeMillis();
        if (now - lastFailureWarning < 5000L) return;
        lastFailureWarning = now;
        plugin.getLogger().warning(message + ": " + error.getClass().getSimpleName());
    }
}
