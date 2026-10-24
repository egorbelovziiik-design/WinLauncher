package nexus.core.module.render;

import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Highlights ores with a separate color per ore. */
public final class OreEspModule extends Module {

    private final NumberSetting range;
    private final BooleanSetting filled;
    private final MultiSelectSetting ores;

    public OreEspModule() {
        super("oreesp", "OreESP", "Highlights ores around you.", ModuleCategory.RENDER);
        this.range = number("Range", "Scan radius in blocks", 24.0, 4.0, 64.0, 1.0);
        this.filled = bool("Filled", "Fill the highlight", true);
        this.ores = multi("Ores", "Ores to highlight", new String[]{"Diamond", "Emerald", "Gold",
                "Iron", "Coal", "Redstone", "Lapis", "Netherite", "Copper"},
                "Diamond", "Emerald", "Gold", "Iron");
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        for (String ore : ores.selected()) {
            int color = colorFor(ore);
            RenderUtil.blocks(event, mc().world(), ore.toLowerCase() + "_ore", color,
                    (int) range.value(), filled.isEnabled(), 1.2);
            if (ore.equals("Netherite")) {
                RenderUtil.blocks(event, mc().world(), "ancient_debris", color, (int) range.value(),
                        filled.isEnabled(), 1.2);
            }
        }
    }

    private int colorFor(String ore) {
        if (ore.equals("Diamond")) {
            return 0xFF60A5FA;
        }
        if (ore.equals("Emerald")) {
            return 0xFF4ADE80;
        }
        if (ore.equals("Gold")) {
            return 0xFFFBBF24;
        }
        if (ore.equals("Iron")) {
            return 0xFFE5E7EB;
        }
        if (ore.equals("Coal")) {
            return 0xFF6B7280;
        }
        if (ore.equals("Redstone")) {
            return 0xFFF87171;
        }
        if (ore.equals("Lapis")) {
            return 0xFF3B82F6;
        }
        if (ore.equals("Copper")) {
            return 0xFFF97316;
        }
        return 0xFF4B5563;
    }

    @Override
    public String hudInfo() {
        return ores.selected().size() + " ores";
    }
}
