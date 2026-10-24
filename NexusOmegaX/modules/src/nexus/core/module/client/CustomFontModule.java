package nexus.core.module.client;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Chooses the font used by every client interface. */
public final class CustomFontModule extends Module {

    private final ModeSetting font;
    private final NumberSetting shadowOffset;
    private final BooleanSetting forceUnicode;

    public CustomFontModule() {
        super("customfont", "CustomFont", "Chooses the font of the client interface.",
                ModuleCategory.CLIENT);
        this.font = mode("Font", "Interface font", "Nexus Sans",
                nexus.core.theme.ThemeManager.BUILT_IN_FONTS);
        this.shadowOffset = number("Shadow", "Shadow offset in pixels", 1.0, 0.0, 3.0, 0.5);
        this.forceUnicode = bool("Force Unicode", "Prefer the unicode font", false);
    }

    @Override
    public void onEnable() {
        apply();
    }

    @Override
    public void onTick(nexus.core.event.TickEvent event) {
        apply();
    }

    private void apply() {
        nexus.core.Nexus.instance().themes().setFont(font.get());
    }

    public double shadowOffset() {
        return shadowOffset.value();
    }

    public boolean unicode() {
        return forceUnicode.isEnabled();
    }

    @Override
    public String hudInfo() {
        return font.get();
    }
}
