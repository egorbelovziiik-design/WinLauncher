package nexus.core.module.automation;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Inserts ingredients and takes finished potions from a brewing stand. */
public final class AutoBrewModule extends Module {

    private final StringSetting ingredient;
    private final BooleanSetting takePotions;
    private final nexus.util.TimeUtil.Timer timer = new nexus.util.TimeUtil.Timer();

    public AutoBrewModule() {
        super("autobrew", "AutoBrew", "Automates the brewing stand screens.", ModuleCategory.AUTOMATION);
        this.ingredient = text("Ingredient", "Ingredient item id part", "nether_wart");
        this.takePotions = bool("Take Potions", "Move finished potions to your inventory", true);
    }

    @Override
    public void onEnable() {
        timer.reset();
    }

    @Override
    public void onTick(nexus.core.event.TickEvent event) {
        if (!mc().isInGame() || !timer.hasPassed(200L, true)) {
            return;
        }
        String screen = mc().screens().currentVanillaScreen();
        if (screen == null || !screen.contains("brewing")) {
            return;
        }
        int slots = player().inventory().containerSlots();
        int ingredientSlot = player().inventory().find(ingredient.rawValue());
        if (ingredientSlot >= 0) {
            player().inventory().clickSlot(ingredientSlot, 0, nexus.api.IInventory.ClickType.QUICK_MOVE);
            markActivity();
            return;
        }
        if (takePotions.isEnabled() && slots > 0) {
            for (int slot = 0; slot < slots; slot++) {
                nexus.api.IItemStack stack = player().inventory().containerStack(slot);
                if (stack != null && stack.id().contains("potion")) {
                    player().inventory().clickSlot(slot, 0, nexus.api.IInventory.ClickType.QUICK_MOVE);
                    markActivity();
                    return;
                }
            }
        }
    }

    @Override
    public String hudInfo() {
        return ingredient.rawValue();
    }
}
