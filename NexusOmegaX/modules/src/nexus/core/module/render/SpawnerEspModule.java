package nexus.core.module.render;

import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Highlights mob spawners. */
public final class SpawnerEspModule extends Module {

    private final ColorSetting color;
    private final NumberSetting range;
    private final BooleanSetting filled;

    public SpawnerEspModule() {
        super("spawneresp", "SpawnerESP", "Highlights mob spawners.", ModuleCategory.RENDER);
        this.color = color("Color", "Spawner color", 0xFFF87171);
        this.range = number("Range", "Scan radius in blocks", 32.0, 4.0, 64.0, 1.0);
        this.filled = bool("Filled", "Fill the highlight", true);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        RenderUtil.blocks(event, mc().world(), "spawner", color.effectiveColor(System.currentTimeMillis()),
                (int) range.value(), filled.isEnabled(), 1.4);
    }
}
