package nexus.core.module.configs;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import java.util.List;

/** Manages configuration profiles from the ClickGUI. */
public final class ProfileManagerModule extends Module {

    private final ModeSetting profile;
    private final BooleanSetting autoLoad;
    private final StringSetting newName;

    public ProfileManagerModule() {
        super("profilemanager", "ProfileManager", "Manages your configuration profiles.",
                ModuleCategory.CONFIGS);
        this.profile = mode("Profile", "Active profile", "default", profileNames());
        this.autoLoad = bool("Auto Load", "Load the active profile on startup", true);
        this.newName = text("New Name", "Name for a new profile", "pvp");
    }

    private static String[] profileNames() {
        List<String> names = nexus.core.Nexus.instance().configManager().listProfiles();
        if (names.isEmpty()) {
            return new String[]{"default"};
        }
        return names.toArray(new String[0]);
    }

    public String load() {
        boolean ok = nexus.core.Nexus.instance().configManager().loadProfile(profile.get(), true);
        markActivity();
        return ok ? "Profile " + profile.get() + " loaded" : "Failed to load " + profile.get();
    }

    public String save() {
        boolean ok = nexus.core.Nexus.instance().configManager().saveProfile(profile.get());
        markActivity();
        return ok ? "Profile " + profile.get() + " saved" : "Failed to save " + profile.get();
    }

    public String create() {
        String name = newName.rawValue().trim();
        if (name.isEmpty()) {
            return "Empty profile name";
        }
        boolean ok = nexus.core.Nexus.instance().configManager().saveProfile(name);
        markActivity();
        return ok ? "Profile " + name + " created" : "Failed to create " + name;
    }

    public boolean autoLoad() {
        return isEnabled() && autoLoad.isEnabled();
    }

    @Override
    public String hudInfo() {
        return profile.get();
    }
}
