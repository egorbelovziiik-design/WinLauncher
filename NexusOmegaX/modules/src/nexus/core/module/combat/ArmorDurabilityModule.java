package nexus.core.module.combat;

import nexus.api.IItemStack;
import nexus.api.IInventory;
import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Shows the durability of every equipped armor piece. */
public final class ArmorDurabilityModule extends Module {

    private final BooleanSetting showPercent;
    private final NumberSetting warnPercent;

    public ArmorDurabilityModule() {
        super("armordurability", "ArmorDurability", "Displays the durability of your armor pieces.",
                ModuleCategory.COMBAT);
        this.showPercent = bool("Show Percent", "Show percentages instead of points", true);
        this.warnPercent = number("Warn Below", "Highlight below this percentage", 20.0, 1.0, 90.0, 1.0);
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        IInventory inventory = player().inventory();
        IItemStack[] pieces = new IItemStack[]{inventory.helmet(), inventory.chestplate(),
                inventory.leggings(), inventory.boots()};
        double y = event.height - 90.0;
        for (IItemStack piece : pieces) {
            if (piece == null || piece.isEmpty() || !piece.isDamageable()) {
                continue;
            }
            double percent = piece.durabilityRatio() * 100.0;
            String label = showPercent.isEnabled()
                    ? String.format("%.0f%%", percent)
                    : String.valueOf(piece.maxDamage() - piece.damage());
            int color = percent <= warnPercent.value() ? 0xFFF87171 : 0xFFC084FC;
            event.renderer.drawText(piece.displayName() + " " + label, 6.0, y, ColorUtil.withAlpha(color, 230), true, 0.8);
            y += 10.0;
        }
    }
}
