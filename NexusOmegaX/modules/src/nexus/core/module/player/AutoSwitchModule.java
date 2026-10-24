package nexus.core.module.player;

import nexus.api.IInventory;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Switches to the right item for the current action (blocks, tools, food). */
public final class AutoSwitchModule extends Module {

    private final BooleanSetting switchToPickaxe;
    private final BooleanSetting switchToFood;
    private final BooleanSetting switchToBlocks;
    private final NumberSetting switchDelay;

    public AutoSwitchModule() {
        super("autoswitch", "AutoSwitch", "Switches to the item you need for the current action.",
                ModuleCategory.PLAYER);
        this.switchToPickaxe = bool("Mining", "Switch to a pickaxe while mining", true);
        this.switchToFood = bool("Eating", "Switch to food when hunger is low", true);
        this.switchToBlocks = bool("Building", "Switch to blocks while placing", true);
        this.switchDelay = number("Delay", "Delay between switches in ticks", 2.0, 0.0, 20.0, 1.0);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        IInventory inventory = player().inventory();
        if (switchToPickaxe.isEnabled() && mc().input().isMouseDown(0)
                && !inventory.heldItem().id().contains("pickaxe")) {
            int pickaxe = inventory.findHotbar("pickaxe");
            if (pickaxe >= 0) {
                inventory.setSelectedSlot(pickaxe);
                return;
            }
        }
        if (switchToFood.isEnabled() && player().hunger() <= 6.0f && !inventory.heldItem().isFood()) {
            int food = inventory.findHotbar("bread");
            if (food < 0) {
                food = findAnyFood(inventory);
            }
            if (food >= 0) {
                inventory.setSelectedSlot(food);
                return;
            }
        }
        if (switchToBlocks.isEnabled() && mc().input().isMouseDown(1) && !inventory.heldItem().isBlockItem()) {
            int blocks = findBlocks(inventory);
            if (blocks >= 0) {
                inventory.setSelectedSlot(blocks);
            }
        }
    }

    private int findAnyFood(IInventory inventory) {
        for (int slot = 0; slot < 9; slot++) {
            if (inventory.stack(slot).isFood()) {
                return slot;
            }
        }
        return -1;
    }

    private int findBlocks(IInventory inventory) {
        for (int slot = 0; slot < 9; slot++) {
            if (inventory.stack(slot).isBlockItem()) {
                return slot;
            }
        }
        return -1;
    }
}
