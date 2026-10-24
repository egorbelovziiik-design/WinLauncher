package nexus.core.module.misc;

import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

import java.util.ArrayList;
import java.util.List;

/** Explains the enchantments on the item you hold. */
public final class EnchantGuideModule extends Module {

    private final NumberSetting x;
    private final NumberSetting y;
    private final BooleanSetting background;

    public EnchantGuideModule() {
        super("enchantguide", "EnchantGuide", "Explains the enchantments on your held item.",
                ModuleCategory.MISC);
        this.x = number("X", "Horizontal position", 8.0, 0.0, 1920.0, 1.0);
        this.y = number("Y", "Vertical position", 260.0, 0.0, 1080.0, 1.0);
        this.background = bool("Background", "Draw a background plate", true);
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        nexus.api.IItemStack held = player().inventory().heldItem();
        if (held.isEmpty() || !held.isEnchanted()) {
            return;
        }
        List<String> lines = explain(held);
        double width = 0.0;
        for (String line : lines) {
            width = Math.max(width, event.renderer.textWidth(line, 1.0));
        }
        double height = lines.size() * 12.0 + 6.0;
        if (background.isEnabled()) {
            event.renderer.fillRoundRect(x.value() - 4.0, y.value() - 4.0, width + 8.0, height, 5.0,
                    ColorUtil.withAlpha(0xFF08080D, 180));
        }
        for (int i = 0; i < lines.size(); i++) {
            event.renderer.drawText(lines.get(i), x.value(), y.value() + i * 12.0, 0xFFC084FC, true, 1.0);
        }
    }

    private List<String> explain(nexus.api.IItemStack stack) {
        List<String> lines = new ArrayList<String>();
        lines.add(stack.displayName());
        for (String enchantment : mc().resources().enchantments(stack)) {
            lines.add("  " + enchantment);
        }
        return lines;
    }
}
