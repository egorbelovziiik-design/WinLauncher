package nexus.core.module.player;

import nexus.api.IInventory;
import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;
import nexus.util.MathUtil;

/** Food and hunger overview: hunger, saturation and available food. */
public final class FoodManagerModule extends Module {

    private final BooleanSetting showSaturation;
    private final BooleanSetting showFoodCount;
    private final ColorSetting color;

    public FoodManagerModule() {
        super("foodmanager", "FoodManager", "Shows hunger, saturation and how much food you carry.",
                ModuleCategory.PLAYER);
        this.showSaturation = bool("Saturation", "Show the saturation value", true);
        this.showFoodCount = bool("Food Count", "Show the number of food items", true);
        this.color = color("Color", "Text color", 0xFFC084FC);
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        int value = color.effectiveColor(System.currentTimeMillis());
        String text = "food " + (int) player().hunger() + "/20";
        if (showSaturation.isEnabled()) {
            text += " | sat " + String.format("%.1f", player().saturation());
        }
        if (showFoodCount.isEnabled()) {
            text += " | " + countFood() + " items";
        }
        double y = event.height * 0.38;
        event.renderer.drawText(text, 6.0, y, value, true, 0.85);
        double ratio = MathUtil.clamp(player().hunger() / 20.0, 0.0, 1.0);
        event.renderer.fillRect(6.0, y + 12.0, 80.0, 4.0, ColorUtil.withAlpha(0xFF000000, 120));
        event.renderer.fillRect(6.0, y + 12.0, 80.0 * ratio, 4.0, ColorUtil.healthColor(player().hunger(), 20.0f));
    }

    private int countFood() {
        IInventory inventory = player().inventory();
        int count = 0;
        for (int slot = 0; slot < IInventory.MAIN_SIZE; slot++) {
            nexus.api.IItemStack stack = inventory.stack(slot);
            if (stack != null && !stack.isEmpty() && stack.isFood()) {
                count += stack.count();
            }
        }
        return count;
    }
}
