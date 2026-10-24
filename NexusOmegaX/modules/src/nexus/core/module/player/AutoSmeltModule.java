package nexus.core.module.player;

import nexus.api.IInventory;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Moves ore and fuel into an open furnace and takes the results out. */
public final class AutoSmeltModule extends Module {

    private final MultiSelectSetting items;
    private final NumberSetting delay;
    private final TimeUtil.Timer timer = new TimeUtil.Timer();

    public AutoSmeltModule() {
        super("autosmelt", "AutoSmelt", "Fills an open furnace and collects the results.",
                ModuleCategory.PLAYER);
        this.items = multi("Items", "Items to smelt", new String[]{"Iron Ore", "Gold Ore", "Raw Iron",
                "Raw Gold", "Sand", "Cobblestone", "Raw Copper", "Chicken", "Beef", "Porkchop"},
                "Iron Ore", "Raw Iron", "Raw Copper");
        this.delay = number("Delay", "Delay between actions in ms", 400.0, 100.0, 3000.0, 100.0);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !timer.hasPassed((long) delay.value(), true)) {
            return;
        }
        int syncId = mc().interaction().openSyncId();
        if (syncId < 0) {
            return;
        }
        IInventory inventory = player().inventory();
        if (inventory.containerSlots() < 3) {
            return;
        }
        nexus.api.IItemStack result = inventory.containerStack(2);
        if (result != null && !result.isEmpty()) {
            inventory.clickSlot(2, 0, IInventory.ClickType.QUICK_MOVE);
            markActivity();
            return;
        }
        for (int slot = 9; slot < IInventory.MAIN_SIZE; slot++) {
            nexus.api.IItemStack stack = inventory.stack(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (stack.id().contains("coal") || stack.id().contains("lava_bucket")) {
                nexus.api.IItemStack fuel = inventory.containerStack(1);
                if (fuel == null || fuel.isEmpty()) {
                    inventory.clickSlot(slot + 9, 0, IInventory.ClickType.PICKUP);
                    inventory.clickSlot(1, 0, IInventory.ClickType.PICKUP);
                    markActivity();
                    return;
                }
                continue;
            }
            if (matches(stack.id())) {
                nexus.api.IItemStack input = inventory.containerStack(0);
                if (input == null || input.isEmpty()) {
                    inventory.clickSlot(slot + 9, 0, IInventory.ClickType.PICKUP);
                    inventory.clickSlot(0, 0, IInventory.ClickType.PICKUP);
                    markActivity();
                    return;
                }
            }
        }
    }

    private boolean matches(String id) {
        for (String selected : items.selected()) {
            if (id.contains(selected.toLowerCase().replace(" ", "_"))) {
                return true;
            }
        }
        return false;
    }
}
