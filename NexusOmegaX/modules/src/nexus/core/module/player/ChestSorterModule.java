package nexus.core.module.player;

import nexus.api.IInventory;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Sorts the contents of an open chest with a single action. */
public final class ChestSorterModule extends Module {

    private final ModeSetting action;
    private final KeybindSetting sortKey;
    private final NumberSetting delay;
    private final TimeUtil.Timer timer = new TimeUtil.Timer();
    private boolean running;

    public ChestSorterModule() {
        super("chestsorter", "ChestSorter", "Sorts or transfers the contents of an open container.",
                ModuleCategory.PLAYER);
        this.action = mode("Action", "Action performed", "Sort", "Sort", "Stack", "Take All");
        this.sortKey = new KeybindSetting("Sort Key", "Key used to trigger the action", -1);
        this.delay = number("Delay", "Delay between clicks in ms", 60.0, 0.0, 500.0, 10.0);
        addSetting(sortKey);
    }

    @Override
    public void onEnable() {
        running = false;
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        int syncId = mc().interaction().openSyncId();
        if (syncId < 0) {
            running = false;
            return;
        }
        if (!running && sortKey.key() >= 0 && mc().input().isKeyDown(sortKey.key())) {
            running = true;
        }
        if (!running || !timer.hasPassed((long) delay.value(), true)) {
            return;
        }
        if (action.is("Take All")) {
            takeAll();
        } else if (action.is("Stack")) {
            stackAll();
        } else {
            sort();
        }
        markActivity();
    }

    private void takeAll() {
        IInventory inventory = player().inventory();
        int slots = inventory.containerSlots();
        for (int slot = 0; slot < slots; slot++) {
            if (inventory.containerStack(slot) != null && !inventory.containerStack(slot).isEmpty()) {
                inventory.clickSlot(slot, 0, IInventory.ClickType.QUICK_MOVE);
                return;
            }
        }
        running = false;
    }

    private void stackAll() {
        IInventory inventory = player().inventory();
        int slots = Math.min(inventory.containerSlots(), 27);
        for (int slot = 0; slot < slots; slot++) {
            nexus.api.IItemStack stack = inventory.containerStack(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            for (int other = slot + 1; other < slots; other++) {
                nexus.api.IItemStack candidate = inventory.containerStack(other);
                if (candidate != null && !candidate.isEmpty() && candidate.id().equals(stack.id())
                        && candidate.count() < candidate.maxStackSize()) {
                    inventory.clickSlot(other, 0, IInventory.ClickType.PICKUP);
                    inventory.clickSlot(slot, 0, IInventory.ClickType.PICKUP);
                    return;
                }
            }
        }
        running = false;
    }

    private void sort() {
        IInventory inventory = player().inventory();
        int slots = Math.min(inventory.containerSlots(), 27);
        for (int slot = 0; slot < slots - 1; slot++) {
            nexus.api.IItemStack current = inventory.containerStack(slot);
            nexus.api.IItemStack next = inventory.containerStack(slot + 1);
            if (current == null || current.isEmpty() || next == null || next.isEmpty()) {
                continue;
            }
            if (current.id().compareToIgnoreCase(next.id()) > 0) {
                inventory.clickSlot(slot, 0, IInventory.ClickType.PICKUP);
                inventory.clickSlot(slot + 1, 0, IInventory.ClickType.PICKUP);
                inventory.clickSlot(slot, 0, IInventory.ClickType.PICKUP);
                return;
            }
        }
        running = false;
        nexus.core.Nexus.instance().notifications().success("ChestSorter", "Container sorted");
    }
}
