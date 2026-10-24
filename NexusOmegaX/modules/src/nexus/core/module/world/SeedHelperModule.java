package nexus.core.module.world;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Stores the world seed sent by the server and keeps it in the session. */
public final class SeedHelperModule extends Module {

    private final BooleanSetting remember;
    private final BooleanSetting showInHud;
    private final StringSetting storedSeed;
    private long seed = Long.MIN_VALUE;

    public SeedHelperModule() {
        super("seedhelper", "SeedHelper", "Remembers the seed of the current world.",
                ModuleCategory.WORLD);
        this.remember = bool("Remember", "Remember seeds across worlds", true);
        this.showInHud = bool("Show In HUD", "Show the seed in the HUD", true);
        this.storedSeed = text("Last Seed", "Seed found in the last world", "");
    }

    public void setSeed(long value) {
        this.seed = value;
        if (remember.isEnabled()) {
            storedSeed.set(String.valueOf(value));
        }
    }

    public long seed() {
        if (seed != Long.MIN_VALUE) {
            return seed;
        }
        String raw = storedSeed.rawValue().trim();
        if (raw.isEmpty()) {
            return Long.MIN_VALUE;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return Long.MIN_VALUE;
        }
    }

    public boolean known() {
        return seed != Long.MIN_VALUE;
    }

    @Override
    public String hudInfo() {
        return known() ? String.valueOf(seed) : "unknown";
    }

    public boolean hudEnabled() {
        return showInHud.isEnabled();
    }
}
