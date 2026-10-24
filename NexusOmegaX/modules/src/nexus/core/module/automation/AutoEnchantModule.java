package nexus.core.module.automation;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Clicks the enchanting table for you with the chosen lapis slot. */
public final class AutoEnchantModule extends Module {

    private final ModeSetting slot;
    private final BooleanSetting requireLapis;
    private final nexus.util.TimeUtil.Timer timer = new nexus.util.TimeUtil.Timer();

    public AutoEnchantModule() {
        super("autoenchant", "AutoEnchant", "Clicks the enchanting table for you.",
                ModuleCategory.AUTOMATION);
        this.slot = mode("Slot", "Which enchantment to take", "Bottom", "Top", "Middle", "Bottom");
        this.requireLapis = bool("Require Lapis", "Only click when you have lapis", true);
    }

    @Override
    public void onEnable() {
        timer.reset();
    }

    @Override
    public void onTick(nexus.core.event.TickEvent event) {
        if (!mc().isInGame() || !timer.hasPassed(250L, true)) {
            return;
        }
        String screen = mc().screens().currentVanillaScreen();
        if (screen == null || !screen.contains("enchant")) {
            return;
        }
        if (requireLapis.isEnabled() && player().inventory().count("lapis_lazuli") == 0) {
            return;
        }
        int button = slot.is("Top") ? 0 : (slot.is("Middle") ? 1 : 2);
        mc().screens().clickEnchantment(button);
        markActivity();
    }

    @Override
    public String hudInfo() {
        return slot.get();
    }
}
