package nexus.core.module.misc;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Hides your own name in the HUD, nametags and streamer overlays. */
public final class NameProtectModule extends Module {

    private final StringSetting replacement;
    private final BooleanSetting hideInNametags;
    private final BooleanSetting hideInHud;

    public NameProtectModule() {
        super("nameprotect", "NameProtect", "Hides your name for screenshots and streams.",
                ModuleCategory.MISC);
        this.replacement = text("Replacement", "Name shown instead of yours", "You");
        this.hideInNametags = bool("Nametags", "Hide it in nametags", true);
        this.hideInHud = bool("HUD", "Hide it in the HUD", true);
    }

    public String protect(String name) {
        if (!isEnabled()) {
            return name;
        }
        String mine = player().name();
        if (mine != null && mine.equalsIgnoreCase(name)) {
            String value = replacement.rawValue();
            return value.isEmpty() ? "You" : value;
        }
        return name;
    }

    public boolean protectNametags() {
        return isEnabled() && hideInNametags.isEnabled();
    }

    public boolean protectHud() {
        return isEnabled() && hideInHud.isEnabled();
    }

    @Override
    public String hudInfo() {
        return replacement.rawValue();
    }
}
