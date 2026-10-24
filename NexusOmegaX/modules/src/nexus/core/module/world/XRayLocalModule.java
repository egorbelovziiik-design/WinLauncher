package nexus.core.module.world;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Local only XRay: reduces rendered block types so you can see through terrain. */
public final class XRayLocalModule extends Module {

    private final MultiSelectSetting blocks;
    private final NumberSetting range;
    private final BooleanSetting smoothLighting;

    public XRayLocalModule() {
        super("xraylocal", "XRayLocal", "Local only XRay for single player and creative worlds.",
                ModuleCategory.WORLD);
        this.blocks = multi("Blocks", "Blocks to keep rendered", new String[]{"Diamond", "Emerald", "Gold",
                "Iron", "Coal", "Redstone", "Lapis", "Netherite", "Copper", "Chests", "Spawners", "Portals"},
                "Diamond", "Emerald", "Gold", "Netherite");
        this.range = number("Range", "Render distance limit", 32.0, 8.0, 128.0, 1.0);
        this.smoothLighting = bool("Smooth Lighting", "Keep smooth lighting enabled", false);
    }

    public boolean isVisible(String blockId) {
        for (String selected : blocks.selected()) {
            if (blockId.contains(selected.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public int range() {
        return (int) range.value();
    }

    public boolean smoothLighting() {
        return smoothLighting.isEnabled();
    }

    @Override
    public void onEnable() {
        mc().world().reloadRenderer();
        nexus.core.Nexus.instance().notifications().info("XRayLocal", "Renderer rebuilt (local only)");
    }

    @Override
    public void onDisable() {
        mc().world().reloadRenderer();
    }

    @Override
    public void cleanup() {
        mc().world().reloadRenderer();
    }

    @Override
    public String hudInfo() {
        return blocks.selected().size() + " blocks";
    }
}
