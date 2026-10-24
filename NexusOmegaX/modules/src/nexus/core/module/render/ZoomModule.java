package nexus.core.module.render;

import nexus.api.IOptions;
import nexus.core.event.KeyEvent;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.render.RenderManager;
import nexus.core.setting.*;

/** Hold to zoom, like a spyglass, with a smooth transition. */
public final class ZoomModule extends Module {

    private final NumberSetting fov;
    private final BooleanSetting smoothCamera;
    private final NumberSetting scrollStep;
    private boolean active;
    private double current;

    public ZoomModule() {
        super("zoom", "Zoom", "Hold the bind to zoom in smoothly.", ModuleCategory.RENDER);
        this.fov = number("FOV", "Field of view while zoomed", 25.0, 5.0, 90.0, 1.0);
        this.smoothCamera = bool("Smooth Camera", "Enable smooth camera while zoomed", true);
        this.scrollStep = number("Scroll Step", "FOV change while scrolling", 5.0, 1.0, 20.0, 1.0);
    }

    @Override
    public void onEnable() {
        active = false;
        current = mc().options().fov();
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        IOptions options = mc().options();
        boolean held = nexus.core.Nexus.instance().keybinds().forModule(this) != null
                && mc().input().isKeyDown(nexus.core.Nexus.instance().keybinds().forModule(this).key());
        if (held && !active) {
            active = true;
            nexus.core.Nexus.instance().render().registerOverride("zoom", new RenderManager.Override() {
                @Override
                public void restore() {
                    mc().options().setFov(original);
                }
            });
        }
        double target = held ? fov.value() : original;
        current = approach(current, target);
        options.setFov(current);
    }

    private double original = 70.0;

    private double approach(double value, double target) {
        return value + (target - value) * 0.25;
    }

    @Override
    public void onKey(KeyEvent event) {
        // zoom works with the hold bind mode, no extra handling needed
    }

    @Override
    public void onDisable() {
        mc().options().setFov(original);
        nexus.core.Nexus.instance().render().releaseOverride("zoom");
        active = false;
    }

    @Override
    public void cleanup() {
        onDisable();
    }

    @Override
    public String hudInfo() {
        return String.valueOf((int) fov.value());
    }
}
