package nexus.core.module.automation;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Quickly moves all items out of the container you opened. */
public final class QuickLootModule extends Module {

    private final NumberSetting delay;
    private final BooleanSetting closeWhenDone;
    private final BooleanSetting onlyIfEmpty;
    private final nexus.util.TimeUtil.Timer timer = new nexus.util.TimeUtil.Timer();

    public QuickLootModule() {
        super("quickloot", "QuickLoot", "Moves every item out of the container you opened.",
                ModuleCategory.AUTOMATION);
        this.delay = number("Delay", "Delay between clicks in ms", 40.0, 0.0, 1000.0, 10.0);
        this.closeWhenDone = bool("Close When Done", "Close the container when finished", true);
        this.onlyIfEmpty = bool("Only When Empty", "Only loot when your inventory has space", true);
    }

    @Override
    public void onEnable() {
        timer.reset();
    }

    @Override
    public void onTick(nexus.core.event.TickEvent event) {
        if (!mc().isInGame() || !timer.hasPassed((long) delay.value(), true)) {
            return;
        }
        int slots = player().inventory().containerSlots();
        if (slots <= 0) {
            return;
        }
        if (onlyIfEmpty.isEnabled() && player().inventory().freeSlots() == 0) {
            return;
        }
        for (int slot = 0; slot < slots; slot++) {
            nexus.api.IItemStack stack = player().inventory().containerStack(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            player().inventory().clickSlot(slot, 0, nexus.api.IInventory.ClickType.QUICK_MOVE);
            markActivity();
            return;
        }
        if (closeWhenDone.isEnabled()) {
            mc().interaction().closeContainer();
            nexus.core.Nexus.instance().notifications().success("QuickLoot", "Container emptied");
        }
    }

    @Override
    public String hudInfo() {
        return (int) delay.value() + "ms";
    }
}
