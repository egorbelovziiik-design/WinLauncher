package nexus.core.module.player;

import nexus.api.IInventory;
import nexus.api.IItemStack;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Combines damaged items or uses an anvil when available. */
public final class AutoRepairModule extends Module {

    private final NumberSetting threshold;
    private final BooleanSetting combineItems;
    private final TimeUtil.Timer timer = new TimeUtil.Timer();

    public AutoRepairModule() {
        super("autorepair", "AutoRepair", "Combines damaged items to repair them automatically.",
                ModuleCategory.PLAYER);
        this.threshold = number("Repair Below", "Repair items below this durability in percent", 25.0, 1.0, 90.0, 1.0);
        this.combineItems = bool("Combine Items", "Combine two damaged items of the same type", true);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !combineItems.isEnabled() || !timer.hasPassed(600L, true)) {
            return;
        }
        if (mc().interaction().openSyncId() < 0) {
            mc().screens().openInventoryScreen();
            return;
        }
        IInventory inventory = player().inventory();
        for (int slot = 9; slot < IInventory.MAIN_SIZE; slot++) {
            IItemStack stack = inventory.stack(slot);
            if (stack == null || stack.isEmpty() || !stack.isDamageable()
                    || stack.durabilityRatio() * 100.0 > threshold.value()) {
                continue;
            }
            for (int other = slot + 1; other < IInventory.MAIN_SIZE; other++) {
                IItemStack partner = inventory.stack(other);
                if (partner == null || partner.isEmpty() || !partner.id().equals(stack.id())) {
                    continue;
                }
                inventory.clickSlot(slot + 9, 0, IInventory.ClickType.PICKUP);
                inventory.clickSlot(other + 9, 0, IInventory.ClickType.PICKUP);
                inventory.clickSlot(slot + 9, 0, IInventory.ClickType.PICKUP);
                markActivity();
                return;
            }
        }
    }
}
