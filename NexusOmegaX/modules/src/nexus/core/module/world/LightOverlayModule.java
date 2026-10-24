package nexus.core.module.world;

import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Shows the light level on the ground so you can place torches correctly. */
public final class LightOverlayModule extends Module {

    private final ModeSetting mode;
    private final ColorSetting spawnColor;
    private final NumberSetting radius;
    private final BooleanSetting numbers;

    public LightOverlayModule() {
        super("lightoverlay", "LightOverlay", "Shows light levels on the ground.", ModuleCategory.WORLD);
        this.mode = mode("Mode", "What to highlight", "Spawnable", "Spawnable", "Block Light", "Sky Light");
        this.spawnColor = color("Color", "Overlay color", 0xFFF87171);
        this.radius = number("Radius", "Scan radius", 10.0, 2.0, 32.0, 1.0);
        this.numbers = bool("Numbers", "Show the light value", true);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        int value = spawnColor.effectiveColor(System.currentTimeMillis());
        int playerX = player().blockX();
        int playerY = player().blockY();
        int playerZ = player().blockZ();
        int radiusInt = (int) radius.value();
        for (int dx = -radiusInt; dx <= radiusInt; dx++) {
            for (int dz = -radiusInt; dz <= radiusInt; dz++) {
                int x = playerX + dx;
                int z = playerZ + dz;
                int y = surfaceY(x, z, playerY);
                int light = mc().world().lightLevel(x, y, z);
                boolean spawnable = light <= 7;
                if (mode.is("Spawnable") && !spawnable) {
                    continue;
                }
                double py = y + 1.02;
                event.renderer.drawWorldQuad(new nexus.api.math.Vec3d(x, py, z),
                        new nexus.api.math.Vec3d(x + 1, py, z),
                        new nexus.api.math.Vec3d(x + 1, py, z + 1),
                        new nexus.api.math.Vec3d(x, py, z + 1),
                        ColorUtil.withAlpha(spawnable ? value : 0xFFFBBF24, spawnable ? 70 : 40));
                if (numbers.isEnabled()) {
                    event.renderer.drawNametag(new nexus.api.math.Vec3d(x + 0.5, py + 0.1, z + 0.5),
                            String.valueOf(light), 0xFFFFFFFF, 0.7, true, 0);
                }
            }
        }
    }

    private int surfaceY(int x, int z, int fromY) {
        for (int y = fromY; y > mc().world().minHeight(); y--) {
            if (!mc().world().isAir(x, y, z)) {
                return y;
            }
        }
        return mc().world().minHeight();
    }

    @Override
    public String hudInfo() {
        return mode.get();
    }
}
