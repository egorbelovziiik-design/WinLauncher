package nexus.core.hud.elements;

import nexus.api.IInventory;
import nexus.api.IItemStack;
import nexus.core.Nexus;
import nexus.core.event.Render2DEvent;
import nexus.core.hud.HudElement;
import nexus.core.setting.NumberSetting;
import nexus.util.ColorUtil;

/** Compact preview of the player inventory. */
public final class InventoryHudElement extends HudElement {

    private final NumberSetting cellSize;

    public InventoryHudElement() {
        super("inventoryhud", "InventoryHUD", false);
        this.cellSize = new NumberSetting("Cell Size", "Size of a single slot", 16.0, 8.0, 32.0, 1.0);
    }

    private double cell() {
        return cellSize.value() * scale();
    }

    @Override
    public double width() {
        return cell() * 9.0;
    }

    @Override
    public double height() {
        return cell() * 3.0;
    }

    @Override
    protected void draw(Render2DEvent event, double x, double y) {
        if (!Nexus.instance().client().isInGame()) {
            return;
        }
        IInventory inventory = Nexus.instance().client().player().inventory();
        double cell = cell();
        int accent = ColorUtil.withAlpha(color(System.currentTimeMillis()), 60);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int slot = 9 + row * 9 + column;
                IItemStack stack = inventory.stack(slot);
                double ix = x + column * cell;
                double iy = y + row * cell;
                event.renderer.outlineRect(ix, iy, cell, cell, 0.75, accent);
                if (stack != null && !stack.isEmpty()) {
                    event.renderer.drawItemWithCount(stack, ix + 1.0, iy + 1.0, (cell - 2.0) / 16.0);
                }
            }
        }
    }

    @Override
    public java.util.List<nexus.core.setting.Setting<?>> settings() {
        java.util.List<nexus.core.setting.Setting<?>> list = super.settings();
        list.add(cellSize);
        return list;
    }

    @Override
    public String previewText() {
        return "[inventory grid]";
    }
}
