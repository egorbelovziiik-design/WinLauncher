package nexus.core.module.combat;

import nexus.api.IItemStack;
import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Shows the stats of the weapon you are holding. */
public final class WeaponStatsModule extends Module {

    private final BooleanSetting showDurability;
    private final BooleanSetting showDamage;
    private final ColorSetting color;

    public WeaponStatsModule() {
        super("weaponstats", "WeaponStats", "Shows the damage and durability of your current weapon.",
                ModuleCategory.COMBAT);
        this.showDurability = bool("Durability", "Show the durability", true);
        this.showDamage = bool("Damage", "Show the attack damage", true);
        this.color = color("Color", "Text color", 0xFFC084FC);
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        IItemStack stack = player().inventory().heldItem();
        if (stack.isEmpty()) {
            return;
        }
        int value = color.effectiveColor(System.currentTimeMillis());
        double y = event.height - 42.0;
        if (showDamage.isEnabled()) {
            event.renderer.drawText("DMG " + String.format("%.1f", stack.attackDamage()), 6.0, y, value, true, 0.85);
            y += 11.0;
        }
        if (showDurability.isEnabled() && stack.isDamageable()) {
            int remaining = stack.maxDamage() - stack.damage();
            event.renderer.drawText("DUR " + remaining, 6.0, y, ColorUtil.withAlpha(value, 220), true, 0.85);
        }
    }
}
