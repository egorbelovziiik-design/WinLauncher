package nexus.core.module.render;

import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Highlights chests, barrels and shulker boxes. */
public final class ChestEspModule extends Module {

    private final ColorSetting color;
    private final NumberSetting range;
    private final BooleanSetting filled;
    private final BooleanSetting includeEnder;

    public ChestEspModule() {
        super("chestesp", "ChestESP", "Highlights chests and other containers.", ModuleCategory.RENDER);
        this.color = color("Color", "Chest color", 0xFFFBBF24);
        this.range = number("Range", "Scan radius in blocks", 24.0, 4.0, 64.0, 1.0);
        this.filled = bool("Filled", "Fill the highlight", true);
        this.includeEnder = bool("Ender Chests", "Also highlight ender chests", true);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        int value = color.effectiveColor(System.currentTimeMillis());
        RenderUtil.blocks(event, mc().world(), "chest", value, (int) range.value(), filled.isEnabled(), 1.2);
        RenderUtil.blocks(event, mc().world(), "barrel", value, (int) range.value(), filled.isEnabled(), 1.2);
        RenderUtil.blocks(event, mc().world(), "shulker", value, (int) range.value(), filled.isEnabled(), 1.2);
        if (includeEnder.isEnabled()) {
            RenderUtil.blocks(event, mc().world(), "ender_chest", 0xFFA855F7, (int) range.value(),
                    filled.isEnabled(), 1.2);
        }
    }
}
