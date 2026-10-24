package nexus.adapter.v12111;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import nexus.api.*;
import nexus.api.math.Box3;

import java.util.ArrayList;
import java.util.List;

/** {@link IWorld} implementation for Minecraft 1.21.11. */
public final class Nexus12111World implements IWorld {

    private final Nexus12111Client client;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    Nexus12111World(Nexus12111Client client) {
        this.client = client;
    }

    private ClientLevel level() {
        return client.minecraft().level;
    }

    private boolean ready() {
        return level() != null;
    }

    @Override
    public boolean isLoaded() {
        return ready();
    }

    @Override
    public String dimensionId() {
        if (!ready()) {
            return "";
        }
        ResourceLocation id = level().dimensionTypeRegistration().unwrapKey()
                .map(key -> key.identifier()).orElse(null);
        return id == null ? level().dimension().toString() : id.toString();
    }

    @Override
    public String dimensionName() {
        return ready() ? level().dimension().identifier().getPath() : "";
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
        for (Entity entity : level().entitiesForRendering()) {
            result.add(new Nexus12111Entity(entity));
        }
        return result;
    }

    @Override
    public List<IEntity> players() {
        List<IEntity> result = new ArrayList<IEntity>();
        if (!ready()) {
            return result;
        }
        for (Entity entity : level().entitiesForRendering()) {
            if (entity instanceof Player) {
                result.add(new Nexus12111Entity(entity));
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
        for (Entity entity : level().entitiesForRendering()) {
            if (entity instanceof net.minecraft.world.entity.Mob) {
                result.add(new Nexus12111Entity(entity));
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
        for (Entity entity : level().entitiesForRendering()) {
            if (entity instanceof ItemEntity) {
                result.add(new Nexus12111Entity(entity));
            }
        }
        return result;
    }

    @Override
    public IEntity entityById(int id) {
        if (!ready()) {
            return null;
        }
        Entity entity = level().getEntity(id);
        return entity == null ? null : new Nexus12111Entity(entity);
    }

    @Override
    public IBlockRef blockAt(int x, int y, int z) {
        if (!ready()) {
            return null;
        }
        cursor.set(x, y, z);
        BlockState state = level().getBlockState(cursor);
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return new Nexus12111Block(id == null ? "minecraft:air" : id.toString(), state);
    }

    @Override
    public boolean isAir(int x, int y, int z) {
        return !ready() || level().getBlockState(cursor.set(x, y, z)).isAir();
    }

    @Override
    public boolean isSolid(int x, int y, int z) {
        return ready() && !level().getBlockState(cursor.set(x, y, z)).isAir()
                && level().getBlockState(cursor.set(x, y, z)).getBlock() != net.minecraft.world.level.block.Blocks.WATER;
    }

    @Override
    public int lightLevel(int x, int y, int z) {
        return ready() ? level().getLightEmission(cursor.set(x, y, z)) : 0;
    }

    @Override
    public int blockLight(int x, int y, int z) {
        return ready() ? level().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, cursor.set(x, y, z)) : 0;
    }

    @Override
    public int skyLight(int x, int y, int z) {
        return ready() ? level().getBrightness(net.minecraft.world.level.LightLayer.SKY, cursor.set(x, y, z)) : 0;
    }

    @Override
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        return ready() && level().getChunkSource().hasChunk(chunkX, chunkZ);
    }

    @Override
    public void scanBlocks(Box3 region, BlockVisitor visitor) {
        if (!ready()) {
            return;
        }
        for (int x = (int) region.minX; x <= (int) region.maxX; x++) {
            for (int y = (int) region.minY; y <= (int) region.maxY; y++) {
                for (int z = (int) region.minZ; z <= (int) region.maxZ; z++) {
                    cursor.set(x, y, z);
                    BlockState state = level().getBlockState(cursor);
                    ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    visitor.visit(x, y, z, id == null ? "minecraft:air" : id.toString());
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
        return ready() ? level().getDayTime() % 24000L : 0L;
    }

    @Override
    public boolean isRaining() {
        return ready() && level().isRaining();
    }

    @Override
    public boolean isThundering() {
        return ready() && level().isThundering();
    }

    @Override
    public float rainLevel() {
        return ready() ? level().getRainLevel(1.0f) : 0.0f;
    }

    @Override
    public String biomeAt(int x, int z) {
        if (!ready()) {
            return null;
        }
        Registry<Biome> registry = level().registryAccess().lookupOrThrow(Registries.BIOME);
        Biome biome = level().getBiome(new BlockPos(x, level().getSeaLevel(), z)).value();
        ResourceLocation id = registry.getKey(biome);
        return id == null ? null : id.toString();
    }

    @Override
    public int seaLevel() {
        return ready() ? level().getSeaLevel() : 63;
    }

    @Override
    public int minHeight() {
        return ready() ? level().getMinY() : -64;
    }

    @Override
    public int maxHeight() {
        return ready() ? level().getMaxY() : 319;
    }

    @Override
    public int borderRadius() {
        return ready() ? (int) level().getWorldBorder().getSize() / 2 : 0;
    }

    @Override
    public void reloadRenderer() {
        if (ready()) {
            client.minecraft().levelRenderer.allChanged();
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
        net.minecraft.world.level.block.entity.BlockEntity entity = level().getBlockEntity(new BlockPos(x, y, z));
        if (entity instanceof net.minecraft.world.level.block.entity.SignBlockEntity) {
            net.minecraft.world.level.block.entity.SignBlockEntity sign =
                    (net.minecraft.world.level.block.entity.SignBlockEntity) entity;
            StringBuilder sb = new StringBuilder();
            for (net.minecraft.network.chat.Component line : sign.getFrontText().getMessages(false)) {
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
        net.minecraft.world.level.block.entity.BlockEntity entity = level().getBlockEntity(new BlockPos(x, y, z));
        if (entity instanceof net.minecraft.world.Container) {
            return ((net.minecraft.world.Container) entity).getContainerSize();
        }
        return 0;
    }

    @Override
    public int[] mapPixels(IItemStack stack, int resolution) {
        return null;
    }

    @Override
    public int spawnX() {
        return ready() ? level().getRespawnData().pos().getX() : 0;
    }

    @Override
    public int spawnY() {
        return ready() ? level().getRespawnData().pos().getY() : 0;
    }

    @Override
    public int spawnZ() {
        return ready() ? level().getRespawnData().pos().getZ() : 0;
    }
}
