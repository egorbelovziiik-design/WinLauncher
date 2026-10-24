package nexus.core.module.player;

import nexus.api.IInventory;
import nexus.api.IItemStack;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Refills a hotbar stack from the inventory when it runs low. */
public final class AutoRefillModule extends Module {

    private final NumberSetting threshold;
    private final BooleanSetting onlyHotbar;
    private final TimeUtil.Timer timer = new TimeUtil.Timer();

    public AutoRefillModule() {
        super("autorefill", "AutoRefill", "Refills hotbar stacks from the inventory automatically.",
                ModuleCategory.PLAYER);
        this.threshold = number("Refill Below", "Refill when a stack drops below this count", 16.0, 1.0, 64.0, 1.0);
        this.onlyHotbar = bool("Hotbar Only", "Only refill hotbar slots", true);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !timer.hasPassed(400L, true)) {
            return;
        }
        IInventory inventory = player().inventory();
        int limit = onlyHotbar.isEnabled() ? 9 : IInventory.MAIN_SIZE;
        for (int slot = 0; slot < limit; slot++) {
            IItemStack stack = inventory.stack(slot);
            if (stack == null || stack.isEmpty() || stack.count() >= threshold.value()
                    || stack.count() >= stack.maxStackSize()) {
                continue;
            }
            for (int source = 9; source < IInventory.MAIN_SIZE; source++) {
                IItemStack candidate = inventory.stack(source);
                if (candidate == null || candidate.isEmpty() || !candidate.id().equals(stack.id())) {
                    continue;
                }
                inventory.clickSlot(source, slot, IInventory.ClickType.SWAP);
                inventory.clickSlot(source, slot, IInventory.ClickType.SWAP);
                markActivity();
                return;
            }
        }
    }
}
