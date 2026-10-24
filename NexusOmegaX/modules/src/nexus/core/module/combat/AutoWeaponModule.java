package nexus.core.module.combat;

import nexus.api.IInventory;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Selects the strongest weapon (or the best tool) when a target is close. */
public final class AutoWeaponModule extends Module {

    private final NumberSetting range;
    private final BooleanSetting preferAxe;
    private final BooleanSetting keepShield;

    public AutoWeaponModule() {
        super("autoweapon", "AutoWeapon", "Automatically selects the best weapon when a target is in range.",
                ModuleCategory.COMBAT);
        this.range = number("Range", "Range in which the weapon is equipped", 6.0, 1.0, 16.0, 0.5);
        this.preferAxe = bool("Prefer Axe", "Prefer axes over swords", false);
        this.keepShield = bool("Keep Shield", "Do not replace a shield in the offhand", true);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        if (mc().world().entities().isEmpty()) {
            return;
        }
        boolean targetNear = false;
        for (nexus.api.IEntity entity : mc().world().entities()) {
            if (entity.isAttackable() && entity.distanceToPlayer() <= range.value()) {
                targetNear = true;
                break;
            }
        }
        if (!targetNear) {
            return;
        }
        IInventory inventory = player().inventory();
        int best = inventory.bestWeaponSlot();
        if (best < 0 || best == inventory.selectedSlot()) {
            return;
        }
        if (keepShield.isEnabled() && inventory.offhandItem().id().contains("shield")) {
            // the offhand is protected, the main hand can still be upgraded
        }
        inventory.setSelectedSlot(best);
        markActivity();
    }

    @Override
    public String hudInfo() {
        return preferAxe.isEnabled() ? "Axe" : "Sword";
    }
}
