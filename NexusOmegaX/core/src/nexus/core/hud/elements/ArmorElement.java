package nexus.core.hud.elements;

import nexus.api.IItemStack;
import nexus.api.IInventory;
import nexus.core.Nexus;
import nexus.core.event.Render2DEvent;
import nexus.core.hud.HudElement;
import nexus.core.setting.BooleanSetting;
import nexus.core.setting.ModeSetting;
import nexus.util.ColorUtil;

/** Armor and held item preview. */
public final class ArmorElement extends HudElement {

    private final ModeSetting orientation;
    private final BooleanSetting showDurability;
    private final BooleanSetting showHeldItem;

    public ArmorElement() {
        super("armor", "Armor");
        this.orientation = new ModeSetting("Orientation", "Stack direction", "Vertical", "Vertical", "Horizontal");
        this.showDurability = new BooleanSetting("Show Durability", "Draw durability bars", true);
        this.showHeldItem = new BooleanSetting("Show Held Item", "Also show the held item", true);
    }

    private int slotCount() {
        return showHeldItem.isEnabled() ? 5 : 4;
    }

    private double cell() {
        return 18.0 * scale();
    }

    @Override
    public double width() {
        return orientation.get().equals("Vertical") ? cell() : cell() * slotCount();
    }

    @Override
    public double height() {
        return orientation.get().equals("Vertical") ? cell() * slotCount() : cell();
    }

    @Override
    protected void draw(Render2DEvent event, double x, double y) {
        if (!Nexus.instance().client().isInGame()) {
            return;
        }
        IInventory inventory = Nexus.instance().client().player().inventory();
        IItemStack[] stacks = new IItemStack[]{inventory.helmet(), inventory.chestplate(),
                inventory.leggings(), inventory.boots(), inventory.heldItem()};
        double cell = cell();
        boolean vertical = orientation.get().equals("Vertical");
        for (int i = 0; i < slotCount(); i++) {
            IItemStack stack = stacks[i];
            double ix = vertical ? x : x + i * cell;
            double iy = vertical ? y + i * cell : y;
            if (stack == null || stack.isEmpty()) {
                event.renderer.outlineRect(ix + 1.0, iy + 1.0, cell - 2.0, cell - 2.0, 1.0,
                        ColorUtil.withAlpha(color(System.currentTimeMillis()), 40));
                continue;
            }
            event.renderer.drawItem(stack, ix + 1.0, iy + 1.0, (cell - 2.0) / 16.0);
            if (showDurability.isEnabled() && stack.isDamageable()) {
                double ratio = stack.durabilityRatio();
                event.renderer.fillRect(ix + 1.0, iy + cell - 3.0, (cell - 2.0) * ratio, 2.0,
                        ratio > 0.5 ? 0xFF4ADE80 : (ratio > 0.25 ? 0xFFFBBF24 : 0xFFF87171));
            }
        }
    }

    @Override
    public java.util.List<nexus.core.setting.Setting<?>> settings() {
        java.util.List<nexus.core.setting.Setting<?>> list = super.settings();
        list.add(orientation);
        list.add(showDurability);
        list.add(showHeldItem);
        return list;
    }

    @Override
    public String previewText() {
        return "Armor + Held";
    }
}
