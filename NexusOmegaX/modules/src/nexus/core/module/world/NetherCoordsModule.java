package nexus.core.module.world;

import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Converts between overworld and nether coordinates. */
public final class NetherCoordsModule extends Module {

    private final BooleanSetting showInChat;
    private final BooleanSetting autoDetect;

    public NetherCoordsModule() {
        super("nethercoords", "NetherCoords", "Converts between overworld and nether coordinates.",
                ModuleCategory.WORLD);
        this.showInChat = bool("Show In Chat", "Print converted coordinates", true);
        this.autoDetect = bool("Auto Detect", "Detect the current dimension", true);
    }

    public void convert() {
        int x = player().blockX();
        int z = player().blockZ();
        boolean nether = autoDetect.isEnabled() && mc().world().dimensionId().contains("nether");
        int resultX = nether ? x * 8 : x / 8;
        int resultZ = nether ? z * 8 : z / 8;
        String message = (nether ? "Overworld" : "Nether") + ": " + resultX + ", " + resultZ;
        if (showInChat.isEnabled()) {
            mc().chat().print(message);
        } else {
            nexus.core.Nexus.instance().notifications().info("NetherCoords", message);
        }
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        // conversion is triggered by the command or keybind, nothing per frame
    }

    public int convert(int value, boolean toNether) {
        return toNether ? value / 8 : value * 8;
    }
}
