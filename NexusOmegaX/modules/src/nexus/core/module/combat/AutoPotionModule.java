package nexus.core.module.combat;

import nexus.api.IPotionEffect;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Throws or drinks healing / buffing potions automatically. */
public final class AutoPotionModule extends Module {

    private final NumberSetting health;
    private final BooleanSetting healing;
    private final BooleanSetting strength;
    private final BooleanSetting speed;
    private final BooleanSetting fireResistance;
    private final NumberSetting delay;
    private final TimeUtil.Timer timer = new TimeUtil.Timer();

    public AutoPotionModule() {
        super("autopotion", "AutoPotion", "Uses potions automatically when health is low or a buff is missing.",
                ModuleCategory.COMBAT);
        this.health = number("Health", "Use a healing potion below this health", 12.0, 1.0, 20.0, 0.5);
        this.healing = bool("Healing", "Use healing potions", true);
        this.strength = bool("Strength", "Keep strength active", false);
        this.speed = bool("Speed", "Keep speed active", false);
        this.fireResistance = bool("Fire Resistance", "Use fire resistance while burning", true);
        this.delay = number("Delay", "Delay between usages in ms", 800.0, 100.0, 5000.0, 100.0);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !timer.hasPassed((long) delay.value(), true)) {
            return;
        }
        if (healing.isEnabled() && player().health() <= health.value()) {
            if (use("healing") || use("instant_health")) {
                return;
            }
        }
        if (fireResistance.isEnabled() && player().isInLava()) {
            if (use("fire_resistance")) {
                return;
            }
        }
        boolean hasStrength = false;
        boolean hasSpeed = false;
        for (IPotionEffect effect : player().potionEffects()) {
            String name = effect.name().toLowerCase();
            if (name.contains("strength")) {
                hasStrength = true;
            }
            if (name.contains("speed")) {
                hasSpeed = true;
            }
        }
        if (strength.isEnabled() && !hasStrength && use("strength")) {
            return;
        }
        if (speed.isEnabled() && !hasSpeed) {
            use("swiftness");
        }
    }

    private boolean use(String potionId) {
        int slot = player().inventory().find(potionId);
        if (slot < 0) {
            return false;
        }
        player().inventory().setSelectedSlot(slot < 9 ? slot : player().inventory().selectedSlot());
        if (slot >= 9) {
            player().inventory().swapWithHotbar(slot, player().inventory().selectedSlot());
        }
        player().startUsingItem();
        markActivity();
        return true;
    }
}
