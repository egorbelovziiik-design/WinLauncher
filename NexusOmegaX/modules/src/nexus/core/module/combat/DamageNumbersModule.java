package nexus.core.module.combat;

import nexus.api.IEntity;
import nexus.core.event.Render3DEvent;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;

/** Floating damage numbers above damaged entities. */
public final class DamageNumbersModule extends Module {

    private static final class Popup {
        private final nexus.api.math.Vec3d position;
        private final double amount;
        private final long created;

        Popup(nexus.api.math.Vec3d position, double amount, long created) {
            this.position = position;
            this.amount = amount;
            this.created = created;
        }
    }

    private final NumberSetting duration;
    private final ColorSetting color;
    private final NumberSetting scale;
    private final java.util.List<Popup> popups = new java.util.ArrayList<Popup>(8);
    private final java.util.Map<Integer, Float> lastHealth = new java.util.HashMap<Integer, Float>();

    public DamageNumbersModule() {
        super("damagenumbers", "DamageNumbers", "Shows floating damage numbers above damaged entities.",
                ModuleCategory.COMBAT);
        this.duration = number("Duration", "Popup lifetime in ms", 900.0, 200.0, 3000.0, 100.0);
        this.color = color("Color", "Number color", 0xFFF87171);
        this.scale = number("Scale", "Number scale", 1.0, 0.5, 2.5, 0.05);
    }

    @Override
    public void onEnable() {
        lastHealth.clear();
        popups.clear();
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        for (IEntity entity : mc().world().entities()) {
            if (!entity.isAlive()) {
                continue;
            }
            Float previous = lastHealth.get(Integer.valueOf(entity.id()));
            float health = entity.health();
            if (previous != null && health < previous.floatValue()) {
                popups.add(new Popup(entity.position().add(0.0, entity.height() + 0.2, 0.0),
                        previous.floatValue() - health, System.currentTimeMillis()));
            }
            lastHealth.put(Integer.valueOf(entity.id()), Float.valueOf(health));
        }
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        long now = System.currentTimeMillis();
        for (int i = popups.size() - 1; i >= 0; i--) {
            Popup popup = popups.get(i);
            double age = (now - popup.created) / duration.value();
            if (age >= 1.0) {
                popups.remove(i);
                continue;
            }
            nexus.api.math.Vec3d position = popup.position.add(0.0, age * 0.9, 0.0);
            int alpha = (int) (255.0 * (1.0 - age));
            String text = "-" + String.format("%.0f", popup.amount);
            event.renderer.drawNametag(position, text,
                    ColorUtil.withAlpha(color.effectiveColor(now), alpha), scale.value(), true,
                    ColorUtil.withAlpha(0x00000000, 0));
        }
    }
}
