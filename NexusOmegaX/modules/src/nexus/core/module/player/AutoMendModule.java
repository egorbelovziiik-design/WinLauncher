package nexus.core.module.player;

import nexus.api.IInventory;
import nexus.api.IItemStack;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Moves mending items into the offhand while collecting experience. */
public final class AutoMendModule extends Module {

    private final BooleanSetting onlyWhenDamaged;
    private final NumberSetting threshold;
    private final TimeUtil.Timer timer = new TimeUtil.Timer();

    public AutoMendModule() {
        super("automend", "AutoMend", "Equips damaged mending gear so experience repairs it.",
                ModuleCategory.PLAYER);
        this.onlyWhenDamaged = bool("Only When Damaged", "Only equip damaged items", true);
        this.threshold = number("Durability Below", "Equip below this durability in percent", 95.0, 5.0, 100.0, 5.0);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !timer.hasPassed(500L, true)) {
            return;
        }
        IInventory inventory = player().inventory();
        for (int armorSlot = IInventory.BOOTS_SLOT; armorSlot <= IInventory.HELMET_SLOT; armorSlot++) {
            IItemStack piece = inventory.stack(armorSlot);
            if (piece == null || piece.isEmpty() || !piece.isDamageable()) {
                continue;
            }
            if (onlyWhenDamaged.isEnabled() && piece.durabilityRatio() * 100.0 > threshold.value()) {
                continue;
            }
            if (piece.isEnchanted()) {
                // mending gear already equipped: experience is absorbed automatically
                return;
            }
        }
        for (int slot = 0; slot < IInventory.MAIN_SIZE; slot++) {
            IItemStack stack = inventory.stack(slot);
            if (stack == null || stack.isEmpty() || !stack.isEnchanted() || !stack.isDamageable()) {
                continue;
            }
            if (onlyWhenDamaged.isEnabled() && stack.durabilityRatio() * 100.0 > threshold.value()) {
                continue;
            }
            inventory.clickSlot(slot, 0, IInventory.ClickType.QUICK_MOVE);
            markActivity();
            return;
        }
    }
}
