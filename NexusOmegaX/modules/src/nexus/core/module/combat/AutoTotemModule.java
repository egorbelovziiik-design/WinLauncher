package nexus.core.module.combat;

import nexus.api.IInventory;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Keeps a totem of undying in the offhand. */
public final class AutoTotemModule extends Module {

    private final NumberSetting healthThreshold;
    private final BooleanSetting always;
    private final NumberSetting delay;
    private final TimeUtil.Timer timer = new TimeUtil.Timer();

    public AutoTotemModule() {
        super("autototem", "AutoTotem", "Automatically moves a totem of undying into the offhand slot.",
                ModuleCategory.COMBAT);
        this.healthThreshold = number("Health", "Equip below this health", 12.0, 1.0, 20.0, 0.5);
        this.always = bool("Always", "Keep the totem equipped at all times", true);
        this.delay = number("Delay", "Delay between inventory actions", 250.0, 0.0, 2000.0, 50.0);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !timer.hasPassed((long) delay.value(), true)) {
            return;
        }
        IInventory inventory = player().inventory();
        if (!inventory.offhandItem().isTotem()) {
            if (!always.isEnabled() && player().health() > healthThreshold.value()) {
                return;
            }
            int slot = inventory.find("totem_of_undying");
            if (slot < 0) {
                return;
            }
            inventory.swapWithHotbar(slot, 8);
            inventory.clickSlot(slot, 40 - 36 + 4, IInventory.ClickType.SWAP);
            markActivity();
        }
    }

    @Override
    public String hudInfo() {
        return String.valueOf(player().inventory().count("totem_of_undying"));
    }
}
