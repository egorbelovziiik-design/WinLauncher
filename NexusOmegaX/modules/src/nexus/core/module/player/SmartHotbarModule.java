package nexus.core.module.player;

import nexus.api.IInventory;
import nexus.api.IItemStack;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Keeps useful items (food, blocks, weapons) in the hotbar. */
public final class SmartHotbarModule extends Module {

    private final BooleanSetting keepFood;
    private final BooleanSetting keepBlocks;
    private final BooleanSetting keepWeapon;
    private final NumberSetting blocksMinimum;

    public SmartHotbarModule() {
        super("smarthotbar", "SmartHotbar", "Keeps food, blocks and a weapon in your hotbar.",
                ModuleCategory.PLAYER);
        this.keepFood = bool("Keep Food", "Keep at least one food item", true);
        this.keepBlocks = bool("Keep Blocks", "Keep a stack of building blocks", true);
        this.keepWeapon = bool("Keep Weapon", "Keep the best weapon available", true);
        this.blocksMinimum = number("Blocks Minimum", "Minimum number of blocks", 32.0, 1.0, 64.0, 1.0);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        IInventory inventory = player().inventory();
        if (keepWeapon.isEnabled()) {
            int weapon = inventory.bestWeaponSlot();
            if (weapon >= 9) {
                int empty = emptyHotbarSlot(inventory);
                if (empty >= 0) {
                    inventory.swapWithHotbar(weapon, empty);
                    return;
                }
            }
        }
        if (keepFood.isEnabled() && countHotbar(inventory, true, false, false) == 0) {
            int food = findInInventory(inventory, true, false, false);
            if (food >= 9) {
                int empty = emptyHotbarSlot(inventory);
                if (empty >= 0) {
                    inventory.swapWithHotbar(food, empty);
                    return;
                }
            }
        }
        if (keepBlocks.isEnabled() && countHotbar(inventory, false, true, false) < (int) blocksMinimum.value()) {
            int blocks = findInInventory(inventory, false, true, false);
            if (blocks >= 9) {
                int empty = emptyHotbarSlot(inventory);
                if (empty >= 0) {
                    inventory.swapWithHotbar(blocks, empty);
                }
            }
        }
    }

    private int countHotbar(IInventory inventory, boolean food, boolean blocks, boolean weapon) {
        int count = 0;
        for (int slot = 0; slot < 9; slot++) {
            IItemStack stack = inventory.stack(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (food && stack.isFood()) {
                count += stack.count();
            } else if (blocks && stack.isBlockItem()) {
                count += stack.count();
            } else if (weapon && stack.isWeapon()) {
                count++;
            }
        }
        return count;
    }

    private int findInInventory(IInventory inventory, boolean food, boolean blocks, boolean weapon) {
        for (int slot = 9; slot < IInventory.MAIN_SIZE; slot++) {
            IItemStack stack = inventory.stack(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (food && stack.isFood()) {
                return slot;
            }
            if (blocks && stack.isBlockItem()) {
                return slot;
            }
            if (weapon && stack.isWeapon()) {
                return slot;
            }
        }
        return -1;
    }

    private int emptyHotbarSlot(IInventory inventory) {
        for (int slot = 0; slot < 9; slot++) {
            IItemStack stack = inventory.stack(slot);
            if (stack == null || stack.isEmpty()) {
                return slot;
            }
        }
        return -1;
    }
}
