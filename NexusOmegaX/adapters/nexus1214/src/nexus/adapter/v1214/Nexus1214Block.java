package nexus.adapter.v1214;

import net.minecraft.world.level.block.state.BlockState;
import nexus.api.IBlockRef;

/** {@link IBlockRef} implementation for Minecraft 1.21.4. */
public final class Nexus1214Block implements IBlockRef {

    private final String id;
    private final BlockState state;

    Nexus1214Block(String id, BlockState state) {
        this.id = id;
        this.state = state;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean isAir() {
        return state.isAir();
    }

    @Override
    public boolean isSolid() {
        return !state.isAir() && state.isSolid();
    }

    @Override
    public boolean isOpaque() {
        return state.canOcclude();
    }

    @Override
    public boolean isLiquid() {
        return !state.getFluidState().isEmpty();
    }

    @Override
    public int lightLevel() {
        return state.getLightEmission();
    }

    @Override
    public int blockLight() {
        return state.getLightBlock();
    }

    @Override
    public float hardness() {
        return state.getDestroySpeed(null, null);
    }

    @Override
    public boolean isOre() {
        return id.contains("_ore") || id.contains("ancient_debris");
    }

    @Override
    public boolean isChest() {
        return id.contains("chest") || id.contains("barrel") || id.contains("shulker_box");
    }

    @Override
    public boolean isSpawner() {
        return id.contains("spawner");
    }

    @Override
    public boolean isLeaf() {
        return id.contains("leaves");
    }

    @Override
    public boolean isLog() {
        return id.contains("log") || id.contains("stem");
    }

    @Override
    public boolean isSource() {
        return isLiquid() && state.getFluidState().isSource();
    }
}
