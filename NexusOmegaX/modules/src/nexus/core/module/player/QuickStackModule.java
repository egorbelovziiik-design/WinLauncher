package nexus.core.module.player;

import nexus.api.IInventory;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Stacks items into the open container (quick stack). */
public final class QuickStackModule extends Module {

    private final KeybindSetting triggerKey;
    private final BooleanSetting onlyExisting;
    private final TimeUtil.Timer timer = new TimeUtil.Timer();

    public QuickStackModule() {
        super("quickstack", "QuickStack", "Stacks your items into the open container.",
                ModuleCategory.PLAYER);
        this.triggerKey = new KeybindSetting("Trigger Key", "Key that starts quick stacking", -1);
        this.onlyExisting = bool("Only Existing", "Only stack items already present in the container", true);
        addSetting(triggerKey);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || triggerKey.key() < 0 || !mc().input().isKeyDown(triggerKey.key())) {
            return;
        }
        if (!timer.hasPassed(90L, true)) {
            return;
        }
        IInventory inventory = player().inventory();
        int containerSlots = Math.min(inventory.containerSlots(), 54);
        if (containerSlots <= 0) {
            return;
        }
        for (int slot = 9; slot < IInventory.MAIN_SIZE; slot++) {
            nexus.api.IItemStack stack = inventory.stack(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            boolean present = false;
            for (int containerSlot = 0; containerSlot < containerSlots; containerSlot++) {
                nexus.api.IItemStack target = inventory.containerStack(containerSlot);
                if (target != null && !target.isEmpty() && target.id().equals(stack.id())
                        && target.count() < target.maxStackSize()) {
                    present = true;
                    break;
                }
            }
            if (onlyExisting.isEnabled() && !present) {
                continue;
            }
            inventory.clickSlot(slot + 9, 0, IInventory.ClickType.QUICK_MOVE);
            markActivity();
            return;
        }
    }
}
