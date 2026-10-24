package nexus.core.module.render;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Replaces the fog with a custom color and density. */
public final class CustomFogModule extends Module {

    private final ColorSetting color;
    private final NumberSetting start;
    private final NumberSetting end;

    public CustomFogModule() {
        super("customfog", "CustomFog", "Replaces the fog with a custom color.", ModuleCategory.RENDER);
        this.color = color("Color", "Fog color", 0xFF08080D);
        this.start = number("Start", "Fog start distance", 24.0, 0.0, 256.0, 1.0);
        this.end = number("End", "Fog end distance", 96.0, 8.0, 512.0, 1.0);
    }

    public int fogColor(long timeMs) {
        return color.effectiveColor(timeMs);
    }

    public boolean isActive() {
        return isEnabled();
    }

    public float fogStart() {
        return (float) start.value();
    }

    public float fogEnd() {
        return (float) end.value();
    }
}
