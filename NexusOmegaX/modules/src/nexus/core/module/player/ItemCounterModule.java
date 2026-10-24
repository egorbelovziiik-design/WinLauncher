package nexus.core.module.player;

import nexus.api.IInventory;
import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Counts selected items and shows the totals on screen. */
public final class ItemCounterModule extends Module {

    private final MultiSelectSetting items;
    private final BooleanSetting showZero;
    private final ColorSetting color;

    public ItemCounterModule() {
        super("itemcounter", "ItemCounter", "Counts selected items in your inventory.",
                ModuleCategory.PLAYER);
        this.items = multi("Items", "Items to count", new String[]{"Arrows", "Totem", "Ender Pearl",
                "Golden Apple", "Diamond", "Emerald", "Iron Ingot", "Coal", "Obsidian", "Torch"},
                "Arrows", "Totem", "Ender Pearl", "Golden Apple");
        this.showZero = bool("Show Zero", "Also show items you do not have", false);
        this.color = color("Color", "Text color", 0xFFC084FC);
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        IInventory inventory = player().inventory();
        int value = color.effectiveColor(System.currentTimeMillis());
        double y = event.height * 0.26;
        for (String selected : items.selected()) {
            int count = inventory.count(selected.toLowerCase().replace(" ", "_"));
            if (count == 0 && !showZero.isEnabled()) {
                continue;
            }
            event.renderer.drawText(selected + " x" + count, 6.0, y, value, true, 0.85);
            y += 11.0;
        }
    }
}
