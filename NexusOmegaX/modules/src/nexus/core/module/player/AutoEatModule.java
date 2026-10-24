package nexus.core.module.player;

import nexus.api.IInventory;
import nexus.api.IItemStack;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import nexus.core.automation.MacroManager;

/** Eats food automatically when hunger (or health) drops. */
public final class AutoEatModule extends Module {

    private final NumberSetting hungerThreshold;
    private final NumberSetting healthThreshold;
    private final BooleanSetting preferGolden;
    private final BooleanSetting stopOnFull;
    private int eatingSlot = -1;

    public AutoEatModule() {
        super("autoeat", "AutoEat", "Eats food automatically when your hunger or health is low.",
                ModuleCategory.PLAYER);
        this.hungerThreshold = number("Hunger", "Eat below this hunger value", 14.0, 1.0, 20.0, 0.5);
        this.healthThreshold = number("Health", "Also eat below this health", 12.0, 0.0, 20.0, 0.5);
        this.preferGolden = bool("Prefer Golden", "Prefer golden apples when health is low", true);
        this.stopOnFull = bool("Stop When Full", "Stop eating once the hunger bar is full", true);
    }

    @Override
    public void onEnable() {
        eatingSlot = -1;
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        IInventory inventory = player().inventory();
        boolean needsFood = player().hunger() < hungerThreshold.value();
        boolean needsHealth = player().health() < healthThreshold.value();
        if (player().isUsingItem() && eatingSlot >= 0) {
            if (!needsFood && !needsHealth && stopOnFull.isEnabled()) {
                stopEating();
            }
            return;
        }
        if (!needsFood && !needsHealth) {
            return;
        }
        IItemStack food = findFood(inventory, needsHealth);
        if (food == null || food.isEmpty()) {
            return;
        }
        eatingSlot = inventory.find(food.id());
        if (eatingSlot < 0) {
            return;
        }
        if (eatingSlot >= 9) {
            inventory.swapWithHotbar(eatingSlot, inventory.selectedSlot());
            eatingSlot = inventory.selectedSlot();
            return;
        }
        inventory.setSelectedSlot(eatingSlot);
        player().startUsingItem();
        markActivity();
    }

    private IItemStack findFood(IInventory inventory, boolean urgent) {
        IItemStack best = null;
        double bestScore = -1.0;
        for (int slot = 0; slot < IInventory.MAIN_SIZE; slot++) {
            IItemStack stack = inventory.stack(slot);
            if (stack == null || stack.isEmpty() || !stack.isFood()) {
                continue;
            }
            double score = stack.foodValue();
            if (urgent && preferGolden.isEnabled() && stack.id().contains("golden_apple")) {
                score += 20.0;
            }
            if (stack.id().contains("rotten_flesh") || stack.id().contains("spider_eye")) {
                score -= 8.0;
            }
            if (score > bestScore) {
                bestScore = score;
                best = stack;
            }
        }
        return best;
    }

    private void stopEating() {
        player().stopUsingItem();
        eatingSlot = -1;
    }

    @Override
    public void onDisable() {
        if (eatingSlot >= 0) {
            stopEating();
        }
        mc().input().setKeyState(MacroManager.KeyMappingKeys.USE, false);
    }

    @Override
    public String hudInfo() {
        return String.valueOf((int) hungerThreshold.value());
    }
}
