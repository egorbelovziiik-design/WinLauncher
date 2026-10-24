package nexus.adapter.v1165;

import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.LightType;
import nexus.api.*;
import nexus.api.math.Box3;

import java.util.ArrayList;
import java.util.List;

/** {@link IWorld} implementation for Minecraft 1.16.5. */
public final class Nexus1165World implements IWorld {

    private final Nexus1165Client client;
    private final BlockPos.Mutable cursor = new BlockPos.Mutable();

    Nexus1165World(Nexus1165Client client) {
        this.client = client;
    }

    private ClientWorld world() {
        return client.handle().world;
    }

    private boolean ready() {
        return world() != null;
    }

    private String blockId(BlockState state) {
        net.minecraft.util.Identifier id = Registry.BLOCK.getId(state.getBlock());
        return id == null ? "minecraft:air" : id.toString();
    }

    @Override
    public boolean isLoaded() {
        return ready();
    }

    @Override
    public String dimensionId() {
        return ready() ? world().getRegistryKey().getValue().toString() : "";
    }

    @Override
    public String dimensionName() {
        return ready() ? world().getRegistryKey().getValue().getPath() : "";
    }

    @Override
    public String dimensionPath() {
        return dimensionName();
    }

    @Override
    public List<IEntity> entities() {
        List<IEntity> result = new ArrayList<IEntity>();
        if (!ready()) {
            return result;
        }
        for (Entity entity : world().getEntities()) {
            result.add(new Nexus1165Entity(entity));
        }
        return result;
    }

    @Override
    public List<IEntity> players() {
        List<IEntity> result = new ArrayList<IEntity>();
        if (!ready()) {
            return result;
        }
        for (Entity entity : world().getEntities()) {
            if (entity instanceof PlayerEntity) {
                result.add(new Nexus1165Entity(entity));
            }
        }
        return result;
    }

    @Override
    public List<IEntity> mobs() {
        List<IEntity> result = new ArrayList<IEntity>();
        if (!ready()) {
            return result;
        }
        for (Entity entity : world().getEntities()) {
            if (entity instanceof net.minecraft.entity.mob.MobEntity) {
                result.add(new Nexus1165Entity(entity));
            }
        }
        return result;
    }

    @Override
    public List<IEntity> items() {
        List<IEntity> result = new ArrayList<IEntity>();
        if (!ready()) {
            return result;
        }
        for (Entity entity : world().getEntities()) {
            if (entity instanceof ItemEntity) {
                result.add(new Nexus1165Entity(entity));
            }
        }
        return result;
    }

    @Override
    public IEntity entityById(int id) {
        return ready() && world().getEntityById(id) != null
                ? new Nexus1165Entity(world().getEntityById(id)) : null;
    }

    @Override
    public IBlockRef blockAt(int x, int y, int z) {
        if (!ready()) {
            return null;
        }
        BlockState state = world().getBlockState(cursor.set(x, y, z));
        return new Nexus1165Block(blockId(state), state);
    }

    @Override
    public boolean isAir(int x, int y, int z) {
        return !ready() || world().getBlockState(cursor.set(x, y, z)).isAir();
    }

    @Override
    public boolean isSolid(int x, int y, int z) {
        return ready() && !world().getBlockState(cursor.set(x, y, z)).isAir()
                && world().getBlockState(cursor.set(x, y, z)).getMaterial().isSolid();
    }

    @Override
    public int lightLevel(int x, int y, int z) {
        return ready() ? world().getLightLevel(new BlockPos(x, y, z)) : 0;
    }

    @Override
    public int blockLight(int x, int y, int z) {
        return ready() ? world().getLightLevel(LightType.BLOCK, new BlockPos(x, y, z)) : 0;
    }

    @Override
    public int skyLight(int x, int y, int z) {
        return ready() ? world().getLightLevel(LightType.SKY, new BlockPos(x, y, z)) : 0;
    }

    @Override
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        return ready() && world().getChunkManager().isChunkLoaded(chunkX, chunkZ);
    }

    @Override
    public void scanBlocks(Box3 region, BlockVisitor visitor) {
        if (!ready()) {
            return;
        }
        for (int x = (int) region.minX; x <= (int) region.maxX; x++) {
            for (int y = (int) region.minY; y <= (int) region.maxY; y++) {
                for (int z = (int) region.minZ; z <= (int) region.maxZ; z++) {
                    visitor.visit(x, y, z, blockId(world().getBlockState(cursor.set(x, y, z))));
                }
            }
        }
    }

    @Override
    public int chunkRenderDistance() {
        return client.options().renderDistance();
    }

    @Override
    public long timeOfDay() {
        return ready() ? world().getTimeOfDay() % 24000L : 0L;
    }

    @Override
    public boolean isRaining() {
        return ready() && world().isRaining();
    }

    @Override
    public boolean isThundering() {
        return ready() && world().isThundering();
    }

    @Override
    public float rainLevel() {
        return ready() ? world().getRainGradient(1.0f) : 0.0f;
    }

    @Override
    public String biomeAt(int x, int z) {
        if (!ready()) {
            return null;
        }
        net.minecraft.util.Identifier id = world().getRegistryManager()
                .get(Registry.BIOME_KEY).getId(world().getBiome(new BlockPos(x, 64, z)));
        return id == null ? null : id.toString();
    }

    @Override
    public int seaLevel() {
        return ready() ? world().getSeaLevel() : 63;
    }

    @Override
    public int minHeight() {
        return 0;
    }

    @Override
    public int maxHeight() {
        return 255;
    }

    @Override
    public int borderRadius() {
        return ready() ? (int) world().getWorldBorder().getSize() / 2 : 0;
    }

    @Override
    public void reloadRenderer() {
        if (ready()) {
            client.handle().worldRenderer.reload();
        }
    }

    @Override
    public String structureAt(int chunkX, int chunkZ) {
        return null;
    }

    @Override
    public String blockEntityText(int x, int y, int z) {
        if (!ready()) {
            return null;
        }
        net.minecraft.block.entity.BlockEntity entity = world().getBlockEntity(new BlockPos(x, y, z));
        if (entity instanceof net.minecraft.block.entity.SignBlockEntity) {
            StringBuilder sb = new StringBuilder();
            net.minecraft.block.entity.SignBlockEntity sign = (net.minecraft.block.entity.SignBlockEntity) entity;
            for (net.minecraft.text.Text line : sign.text) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(line.getString());
            }
            return sb.toString();
        }
        return null;
    }

    @Override
    public int blockEntitySize(int x, int y, int z) {
        if (!ready()) {
            return 0;
        }
        net.minecraft.block.entity.BlockEntity entity = world().getBlockEntity(new BlockPos(x, y, z));
        return entity instanceof net.minecraft.inventory.Inventory
                ? ((net.minecraft.inventory.Inventory) entity).size() : 0;
    }

    @Override
    public int[] mapPixels(IItemStack stack, int resolution) {
        return null;
    }

    @Override
    public int spawnX() {
        return ready() ? world().getSpawnPos().getX() : 0;
    }

    @Override
    public int spawnY() {
        return ready() ? world().getSpawnPos().getY() : 0;
    }

    @Override
    public int spawnZ() {
        return ready() ? world().getSpawnPos().getZ() : 0;
    }
}
