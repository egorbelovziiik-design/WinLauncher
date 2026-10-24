package nexus.core.module.combat;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Keeps a shield in the offhand and lowers it when it is not needed. */
public final class ShieldHelperModule extends Module {

    private final BooleanSetting autoEquip;
    private final NumberSetting durabilityWarning;
    private final BooleanSetting notify;
    private final TimeUtil.Timer timer = new TimeUtil.Timer();
    private boolean warned;

    public ShieldHelperModule() {
        super("shieldhelper", "ShieldHelper", "Keeps a shield equipped and warns you before it breaks.",
                ModuleCategory.COMBAT);
        this.autoEquip = bool("Auto Equip", "Automatically move a shield into the offhand", true);
        this.durabilityWarning = number("Warn At", "Warn below this durability in percent", 15.0, 1.0, 90.0, 1.0);
        this.notify = bool("Notify", "Show a notification when the shield is low", true);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !timer.hasPassed(400L, true)) {
            return;
        }
        nexus.api.IInventory inventory = player().inventory();
        nexus.api.IItemStack offhand = inventory.offhandItem();
        if (autoEquip.isEnabled() && !offhand.id().contains("shield")) {
            int slot = inventory.find("shield");
            if (slot >= 0) {
                inventory.clickSlot(slot, 40, nexus.api.IInventory.ClickType.SWAP);
            }
            return;
        }
        if (offhand.id().contains("shield") && offhand.isDamageable()) {
            double percent = offhand.durabilityRatio() * 100.0;
            if (percent <= durabilityWarning.value()) {
                if (!warned && notify.isEnabled()) {
                    nexus.core.Nexus.instance().notifications().warn("Shield", "Shield durability low");
                    warned = true;
                }
            } else {
                warned = false;
            }
        }
    }
}
