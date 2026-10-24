package nexus.core.module.render;

import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Highlights every kind of storage block at once. */
public final class StorageEspModule extends Module {

    private final ColorSetting color;
    private final NumberSetting range;
    private final BooleanSetting filled;
    private final BooleanSetting includeHoppers;

    public StorageEspModule() {
        super("storageesp", "StorageESP", "Highlights all storage containers at once.", ModuleCategory.RENDER);
        this.color = color("Color", "Highlight color", 0xFFFBBF24);
        this.range = number("Range", "Scan radius in blocks", 20.0, 4.0, 64.0, 1.0);
        this.filled = bool("Filled", "Fill the highlight", true);
        this.includeHoppers = bool("Hoppers", "Also highlight hoppers and dispensers", false);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        int value = color.effectiveColor(System.currentTimeMillis());
        String[] targets = new String[]{"chest", "barrel", "shulker", "furnace", "chest_minecart"};
        for (String target : targets) {
            RenderUtil.blocks(event, mc().world(), target, value, (int) range.value(), filled.isEnabled(), 1.1);
        }
        if (includeHoppers.isEnabled()) {
            RenderUtil.blocks(event, mc().world(), "hopper", value, (int) range.value(), filled.isEnabled(), 1.1);
            RenderUtil.blocks(event, mc().world(), "dispenser", value, (int) range.value(), filled.isEnabled(), 1.1);
        }
    }
}
