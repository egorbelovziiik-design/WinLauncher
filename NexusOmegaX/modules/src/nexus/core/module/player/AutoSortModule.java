package nexus.core.module.player;

import nexus.api.IInventory;
import nexus.api.IItemStack;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Sorts the inventory alphabetically and by type. */
public final class AutoSortModule extends Module {

    private final ModeSetting order;
    private final BooleanSetting stackFirst;
    private final NumberSetting delay;
    private final TimeUtil.Timer timer = new TimeUtil.Timer();
    private boolean sorted;

    public AutoSortModule() {
        super("autosort", "AutoSort", "Sorts your inventory automatically.", ModuleCategory.PLAYER);
        this.order = mode("Order", "Sorting order", "Type", "Type", "Alphabetical", "Count");
        this.stackFirst = bool("Stack First", "Merge stacks before sorting", true);
        this.delay = number("Delay", "Delay between moves in ms", 120.0, 0.0, 1000.0, 10.0);
    }

    @Override
    public void onEnable() {
        sorted = false;
        timer.reset();
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || sorted || !timer.hasPassed((long) delay.value(), true)) {
            return;
        }
        if (mc().interaction().openSyncId() < 0) {
            mc().screens().openInventoryScreen();
            return;
        }
        IInventory inventory = player().inventory();
        if (stackFirst.isEnabled() && mergeStacks(inventory)) {
            markActivity();
            return;
        }
        for (int slot = 9; slot < IInventory.MAIN_SIZE - 1; slot++) {
            IItemStack current = inventory.stack(slot);
            IItemStack next = inventory.stack(slot + 1);
            if (current == null || current.isEmpty() || next == null || next.isEmpty()) {
                continue;
            }
            if (compare(current, next) > 0) {
                inventory.clickSlot(slot + 9, 0, IInventory.ClickType.PICKUP);
                inventory.clickSlot(slot + 10, 0, IInventory.ClickType.PICKUP);
                inventory.clickSlot(slot + 9, 0, IInventory.ClickType.PICKUP);
                markActivity();
                return;
            }
        }
        sorted = true;
        nexus.core.Nexus.instance().notifications().success("AutoSort", "Inventory sorted");
    }

    private boolean mergeStacks(IInventory inventory) {
        for (int slot = 9; slot < IInventory.MAIN_SIZE; slot++) {
            IItemStack current = inventory.stack(slot);
            if (current == null || current.isEmpty() || current.count() >= current.maxStackSize()) {
                continue;
            }
            for (int other = slot + 1; other < IInventory.MAIN_SIZE; other++) {
                IItemStack candidate = inventory.stack(other);
                if (candidate == null || candidate.isEmpty() || !candidate.id().equals(current.id())) {
                    continue;
                }
                inventory.clickSlot(other + 9, 0, IInventory.ClickType.PICKUP);
                inventory.clickSlot(slot + 9, 0, IInventory.ClickType.PICKUP);
                return true;
            }
        }
        return false;
    }

    private int compare(IItemStack first, IItemStack second) {
        if (order.is("Alphabetical")) {
            return first.id().compareToIgnoreCase(second.id());
        }
        if (order.is("Count")) {
            return Integer.compare(second.count(), first.count());
        }
        return Integer.compare(category(first), category(second));
    }

    private int category(IItemStack stack) {
        if (stack.isWeapon()) {
            return 0;
        }
        if (stack.isTool()) {
            return 1;
        }
        if (stack.isArmor()) {
            return 2;
        }
        if (stack.isFood()) {
            return 3;
        }
        if (stack.isBlockItem()) {
            return 4;
        }
        return 5;
    }

    public boolean isSorted() {
        return sorted;
    }
}
