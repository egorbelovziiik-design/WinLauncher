package nexus.core.module.combat;

import nexus.api.IPotionEffect;
import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Warns when an important effect is about to expire. */
public final class PotionHelperModule extends Module {

    private final NumberSetting warnTime;
    private final BooleanSetting warnExpiring;
    private final MultiSelectSetting watch;

    public PotionHelperModule() {
        super("potionhelper", "PotionHelper", "Warns you when a potion effect is about to run out.",
                ModuleCategory.COMBAT);
        this.warnTime = number("Warn Before", "Warn this many seconds before expiry", 5.0, 1.0, 60.0, 1.0);
        this.warnExpiring = bool("Notify", "Show a notification", true);
        this.watch = multi("Watch", "Effects to watch", new String[]{"Strength", "Speed", "Regeneration",
                "Resistance", "Fire Resistance", "Absorption", "Jump Boost"},
                "Strength", "Regeneration", "Resistance");
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        for (IPotionEffect effect : player().potionEffects()) {
            if (!watch.isSelected(effect.name())) {
                continue;
            }
            int secondsLeft = effect.duration() / 20;
            String label = effect.name() + " " + secondsLeft + "s";
            double y = event.height * 0.2;
            event.renderer.drawText(label, 6.0, y, secondsLeft <= warnTime.value() ? 0xFFF87171 : 0xFFC084FC, true, 0.85);
            if (secondsLeft <= warnTime.value() && warnExpiring.isEnabled()) {
                nexus.core.Nexus.instance().notifications().warn("Potion", effect.name() + " expires soon");
            }
        }
    }
}
