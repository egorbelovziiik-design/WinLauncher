package nexus.core.module.automation;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Digs a 1x2 or 2x3 tunnel in the direction you are looking. */
public final class AutoTunnelModule extends Module {

    private final ModeSetting size;
    private final BooleanSetting placeFloor;
    private final NumberSetting maxBlocks;

    public AutoTunnelModule() {
        super("autotunnel", "AutoTunnel", "Digs a tunnel in the direction you look.",
                ModuleCategory.AUTOMATION);
        this.size = mode("Size", "Tunnel size", "1x2", "1x2", "2x3", "3x3");
        this.placeFloor = bool("Place Floor", "Place blocks under you", true);
        this.maxBlocks = number("Max Blocks", "Blocks per tick", 2.0, 1.0, 8.0, 1.0);
    }

    @Override
    public void onTick(nexus.core.event.TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        int height = size.is("1x2") ? 2 : 3;
        int width = size.is("3x3") ? 3 : (size.is("2x3") ? 2 : 1);
        int x = player().blockX();
        int y = player().blockY();
        int z = player().blockZ();
        String facing = player().facing();
        int dirX = facing.contains("west") ? -1 : (facing.contains("east") ? 1 : 0);
        int dirZ = facing.contains("north") ? -1 : (facing.contains("south") ? 1 : 0);
        int mined = 0;
        for (int dy = 0; dy < height && mined < maxBlocks.value(); dy++) {
            for (int side = 0; side < width && mined < maxBlocks.value(); side++) {
                int offset = side - (width / 2);
                int bx = x + dirX + (dirX == 0 ? offset : 0);
                int bz = z + dirZ + (dirZ == 0 ? offset : 0);
                int by = y + dy;
                if (!mc().world().isAir(bx, by, bz)) {
                    mc().interaction().breakBlock(bx, by, bz);
                    mined++;
                }
            }
        }
        if (placeFloor.isEnabled() && mc().world().isAir(x, y - 1, z)) {
            mc().interaction().placeBlock(x, y - 1, z, 1);
        }
        if (mined > 0) {
            markActivity();
        }
    }

    @Override
    public String hudInfo() {
        return size.get();
    }
}
