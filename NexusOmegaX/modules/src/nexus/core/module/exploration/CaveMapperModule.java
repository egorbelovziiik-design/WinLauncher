package nexus.core.module.exploration;

import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

import java.util.HashSet;
import java.util.Set;

/** Slowly builds a map of the caves you have explored. */
public final class CaveMapperModule extends Module {

    private final ColorSetting color;
    private final NumberSetting radius;
    private final BooleanSetting persist;
    private final Set<Long> known = new HashSet<Long>();
    private final nexus.util.TimeUtil.Timer timer = new nexus.util.TimeUtil.Timer();

    public CaveMapperModule() {
        super("cavemapper", "CaveMapper", "Builds a map of the caves you explore.",
                ModuleCategory.EXPLORATION);
        this.color = color("Color", "Cave color", 0xFF22D3EE);
        this.radius = number("Radius", "Scan radius", 16.0, 4.0, 48.0, 1.0);
        this.persist = bool("Persist", "Keep the map after disabling", true);
    }

    @Override
    public void onEnable() {
        timer.reset();
        if (!persist.isEnabled()) {
            known.clear();
        }
    }

    @Override
    public void onDisable() {
        if (!persist.isEnabled()) {
            known.clear();
        }
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame() || !timer.hasPassed(250L, true)) {
            return;
        }
        int playerX = player().blockX();
        int playerY = player().blockY();
        int playerZ = player().blockZ();
        int radiusInt = (int) radius.value();
        for (int dx = -radiusInt; dx <= radiusInt; dx++) {
            for (int dz = -radiusInt; dz <= radiusInt; dz++) {
                for (int dy = -radiusInt; dy <= radiusInt; dy++) {
                    int x = playerX + dx;
                    int y = playerY + dy;
                    int z = playerZ + dz;
                    if (y < mc().world().minHeight() || y > playerY + 8) {
                        continue;
                    }
                    if (!mc().world().isAir(x, y, z) || mc().world().lightLevel(x, y, z) > 3) {
                        continue;
                    }
                    known.add(Long.valueOf(pack(x, y, z)));
                }
            }
        }
        int value = color.effectiveColor(System.currentTimeMillis());
        for (Long key : known) {
            long packed = key.longValue();
            int x = (int) (packed >> 42);
            int y = (int) ((packed >> 21) & 0x1FFFFF) - 64;
            int z = (int) (packed & 0x1FFFFF) - 1048576;
            if (Math.abs(x - playerX) > radiusInt * 2 || Math.abs(z - playerZ) > radiusInt * 2) {
                continue;
            }
            nexus.api.math.Box3 box = nexus.api.math.Box3.block(x, y, z);
            event.renderer.drawBox(box, ColorUtil.withAlpha(value, 25), ColorUtil.withAlpha(value, 90), true);
        }
    }

    private long pack(int x, int y, int z) {
        return ((long) x << 42) | ((long) (y + 64) << 21) | ((long) (z + 1048576));
    }

    public int mappedBlocks() {
        return known.size();
    }

    @Override
    public String hudInfo() {
        return known.size() + " blocks";
    }
}
