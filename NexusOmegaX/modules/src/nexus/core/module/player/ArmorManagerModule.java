package nexus.core.module.player;

import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Monitors your armor set and warns you about missing or broken pieces. */
public final class ArmorManagerModule extends Module {

    private final BooleanSetting warnMissing;
    private final BooleanSetting warnBroken;
    private final NumberSetting brokenPercent;

    public ArmorManagerModule() {
        super("armormanager", "AutoArmor", "Monitors your armor and warns about missing or broken pieces.",
                ModuleCategory.PLAYER);
        this.warnMissing = bool("Warn Missing", "Warn when a slot is empty", true);
        this.warnBroken = bool("Warn Broken", "Warn when a piece is almost broken", true);
        this.brokenPercent = number("Broken At", "Warn below this durability in percent", 10.0, 1.0, 50.0, 1.0);
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        nexus.api.IInventory inventory = player().inventory();
        nexus.api.IItemStack[] pieces = new nexus.api.IItemStack[]{
                inventory.helmet(), inventory.chestplate(), inventory.leggings(), inventory.boots()};
        String[] names = new String[]{"Helmet", "Chestplate", "Leggings", "Boots"};
        int warnings = 0;
        for (int i = 0; i < pieces.length; i++) {
            nexus.api.IItemStack piece = pieces[i];
            if (piece == null || piece.isEmpty()) {
                if (warnMissing.isEnabled()) {
                    event.renderer.drawText("Missing " + names[i], 6.0, event.height * 0.7 + warnings * 11.0,
                            ColorUtil.withAlpha(0xFFFBBF24, 230), true, 0.8);
                    warnings++;
                }
            } else if (warnBroken.isEnabled() && piece.isDamageable()
                    && piece.durabilityRatio() * 100.0 <= brokenPercent.value()) {
                event.renderer.drawText(names[i] + " low durability", 6.0, event.height * 0.7 + warnings * 11.0,
                        ColorUtil.withAlpha(0xFFF87171, 240), true, 0.8);
                warnings++;
            }
        }
    }

    public int armorValue() {
        return mc().isInGame() ? player().inventory().armorValue() : 0;
    }
}
