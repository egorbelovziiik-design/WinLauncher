package nexus.core.module.player;

import nexus.api.IInventory;
import nexus.api.IItemStack;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Warns you before an item breaks. */
public final class DurabilityWarningModule extends Module {

    private final NumberSetting threshold;
    private final BooleanSetting sound;
    private final TimeUtil.Timer timer = new TimeUtil.Timer();

    public DurabilityWarningModule() {
        super("durabilitywarning", "DurabilityWarning", "Warns you before an item breaks.",
                ModuleCategory.PLAYER);
        this.threshold = number("Warn Below", "Warn below this durability percentage", 10.0, 1.0, 50.0, 1.0);
        this.sound = bool("Sound", "Play a warning sound", true);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !timer.hasPassed(3000L, true)) {
            return;
        }
        IInventory inventory = player().inventory();
        IItemStack[] watched = new IItemStack[]{inventory.heldItem(), inventory.helmet(),
                inventory.chestplate(), inventory.leggings(), inventory.boots()};
        for (IItemStack stack : watched) {
            if (stack == null || stack.isEmpty() || !stack.isDamageable()) {
                continue;
            }
            if (stack.durabilityRatio() * 100.0 <= threshold.value()) {
                nexus.core.Nexus.instance().notifications().warn("Durability",
                        stack.displayName() + " is about to break");
                if (sound.isEnabled()) {
                    mc().resources().playSound("block.anvil.land", 0.5f, 1.2f);
                }
                markActivity();
                return;
            }
        }
    }
}
