package nexus.core.module.player;

import nexus.api.IInventory;
import nexus.api.IItemStack;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Keeps the inventory tidy: drops trash, refills stacks and reports fullness. */
public final class InventoryManagerModule extends Module {

    private final NumberSetting fullnessWarn;
    private final BooleanSetting autoClose;
    private final BooleanSetting warnFull;
    private final TimeUtil.Timer timer = new TimeUtil.Timer();

    public InventoryManagerModule() {
        super("inventorymanager", "InventoryManager", "Keeps your inventory tidy and warns you when it is full.",
                ModuleCategory.PLAYER);
        this.fullnessWarn = number("Warn At", "Warn when this many slots are used", 32.0, 10.0, 36.0, 1.0);
        this.autoClose = bool("Auto Close", "Close the inventory when finished", true);
        this.warnFull = bool("Notify", "Show a notification when the inventory is full", true);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !timer.hasPassed(1500L, true)) {
            return;
        }
        IInventory inventory = player().inventory();
        int used = usedSlots(inventory);
        if (used >= fullnessWarn.value() && warnFull.isEnabled()) {
            nexus.core.Nexus.instance().notifications().warn("Inventory", used + "/" + IInventory.MAIN_SIZE + " slots used");
        }
        if (inventory.isFull() && warnFull.isEnabled()) {
            nexus.core.Nexus.instance().notifications().error("Inventory", "Inventory is full");
        }
    }

    private int usedSlots(IInventory inventory) {
        int used = 0;
        for (int slot = 0; slot < IInventory.MAIN_SIZE; slot++) {
            IItemStack stack = inventory.stack(slot);
            if (stack != null && !stack.isEmpty()) {
                used++;
            }
        }
        return used;
    }

    public int usedSlots() {
        return mc().isInGame() ? usedSlots(player().inventory()) : 0;
    }

    public double fullness() {
        return usedSlots() / (double) IInventory.MAIN_SIZE;
    }
}
