package nexus.core.module.misc;

import nexus.core.event.Render3DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.ColorUtil;
import nexus.util.TimeUtil;

/** Spawns a local test dummy for practising combat (single player only). */
public final class FakePlayerModule extends Module {

    private final NumberSetting distance;
    private final BooleanSetting showBox;
    private final ColorSetting color;
    private nexus.api.math.Vec3d position;

    public FakePlayerModule() {
        super("fakeplayer", "FakePlayer", "Spawns a local test dummy to practise combat.",
                ModuleCategory.MISC);
        this.distance = number("Distance", "Distance in front of you", 3.0, 1.0, 8.0, 0.5);
        this.showBox = bool("Show Box", "Draw the hitbox of the dummy", true);
        this.color = color("Color", "Dummy color", 0xFFC084FC);
    }

    @Override
    public void onEnable() {
        if (!mc().connection().isSingleplayer()) {
            nexus.core.Nexus.instance().notifications().warn("FakePlayer",
                    "Only available in single player");
            setEnabled(false, true);
            return;
        }
        update();
    }

    private void update() {
        double yaw = Math.toRadians(player().yaw());
        position = new nexus.api.math.Vec3d(
                player().position().x - Math.sin(yaw) * distance.value(),
                player().position().y,
                player().position().z + Math.cos(yaw) * distance.value());
    }

    @Override
    public void onTick(nexus.core.event.TickEvent event) {
        if (isEnabled() && !player().isDead()) {
            update();
        }
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled() || position == null || !showBox.isEnabled()) {
            return;
        }
        int value = color.effectiveColor(System.currentTimeMillis());
        nexus.api.math.Box3 box = new nexus.api.math.Box3(position.x - 0.3, position.y,
                position.z - 0.3, position.x + 0.3, position.y + 1.8, position.z + 0.3);
        event.renderer.drawBox(box, ColorUtil.withAlpha(value, 45), value, true);
        event.renderer.drawNametag(new nexus.api.math.Vec3d(position.x, position.y + 2.0, position.z),
                "Training Dummy", value, 0.9, true, ColorUtil.withAlpha(0xFF08080D, 160));
    }

    public nexus.api.math.Vec3d dummyPosition() {
        return position;
    }
}
