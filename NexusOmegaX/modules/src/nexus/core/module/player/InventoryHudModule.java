package nexus.core.module.player;

import nexus.core.hud.HudElement;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Enables the inventory preview HUD element. */
public final class InventoryHudModule extends Module {

    private final NumberSetting cellSize;

    public InventoryHudModule() {
        super("inventoryhud", "InventoryHUD", "Shows a compact preview of your inventory.",
                ModuleCategory.PLAYER);
        this.cellSize = number("Cell Size", "Size of one inventory slot", 16.0, 8.0, 32.0, 1.0);
    }

    @Override
    public void onEnable() {
        apply(true);
    }

    @Override
    public void onDisable() {
        apply(false);
    }

    private void apply(boolean value) {
        HudElement element = nexus.core.Nexus.instance().hud().byId("inventoryhud");
        if (element == null) {
            return;
        }
        element.setEnabled(value);
        nexus.core.setting.Setting<?> setting = element.setting("Cell Size");
        if (setting instanceof NumberSetting) {
            ((NumberSetting) setting).set(Double.valueOf(cellSize.value()));
        }
    }
}
