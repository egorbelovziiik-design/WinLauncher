package nexus.core.module.player;

import nexus.api.IInventory;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Moves every matching item into the open container (or out of it). */
public final class QuickMoveModule extends Module {

    private final ModeSetting direction;
    private final StringSetting filter;
    private final KeybindSetting triggerKey;
    private final TimeUtil.Timer timer = new TimeUtil.Timer();

    public QuickMoveModule() {
        super("quickmove", "QuickMove", "Moves matching items between the inventory and a container.",
                ModuleCategory.PLAYER);
        this.direction = mode("Direction", "Move direction", "To Container", "To Container", "From Container");
        this.filter = text("Filter", "Only move items whose id contains this text", "");
        this.triggerKey = new KeybindSetting("Trigger Key", "Key that starts the transfer", -1);
        addSetting(triggerKey);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || triggerKey.key() < 0) {
            return;
        }
        if (!mc().input().isKeyDown(triggerKey.key()) || !timer.hasPassed(80L, true)) {
            return;
        }
        IInventory inventory = player().inventory();
        int containerSlots = inventory.containerSlots();
        if (containerSlots <= 0) {
            return;
        }
        if (direction.is("To Container")) {
            for (int slot = 0; slot < IInventory.MAIN_SIZE; slot++) {
                nexus.api.IItemStack stack = inventory.stack(slot);
                if (matches(stack)) {
                    inventory.clickSlot(slot + 9, 0, IInventory.ClickType.QUICK_MOVE);
                    return;
                }
            }
        } else {
            for (int slot = 0; slot < containerSlots; slot++) {
                nexus.api.IItemStack stack = inventory.containerStack(slot);
                if (matches(stack)) {
                    inventory.clickSlot(slot, 0, IInventory.ClickType.QUICK_MOVE);
                    return;
                }
            }
        }
        markActivity();
    }

    private boolean matches(nexus.api.IItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String value = filter.rawValue().trim();
        return value.isEmpty() || stack.id().contains(value);
    }
}
