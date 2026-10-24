package nexus.core.module.render;

import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Highlights any block type you type in. */
public final class BlockEspModule extends Module {

    private final StringSetting block;
    private final ColorSetting color;
    private final NumberSetting range;
    private final BooleanSetting filled;

    public BlockEspModule() {
        super("blockesp", "BlockESP", "Highlights a custom block type.", ModuleCategory.RENDER);
        this.block = text("Block", "Block id part to highlight (e.g. diamond_ore)", "diamond_ore");
        this.color = color("Color", "Highlight color", 0xFF60A5FA);
        this.range = number("Range", "Scan radius in blocks", 16.0, 2.0, 48.0, 1.0);
        this.filled = bool("Filled", "Fill the highlight", true);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        String value = block.rawValue().trim();
        if (value.isEmpty()) {
            return;
        }
        RenderUtil.blocks(event, mc().world(), value, color.effectiveColor(System.currentTimeMillis()),
                (int) range.value(), filled.isEnabled(), 1.2);
    }

    @Override
    public String hudInfo() {
        String value = block.rawValue();
        return value.length() > 10 ? value.substring(0, 10) : value;
    }
}
