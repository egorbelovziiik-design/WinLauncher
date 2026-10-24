package nexus.core.module.render;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Cosmetic physics for dropped items (rolling and bouncing). */
public final class ItemPhysicsModule extends Module {

    private final BooleanSetting rotate;
    private final BooleanSetting bounce;
    private final NumberSetting bounceStrength;

    public ItemPhysicsModule() {
        super("itemphysics", "ItemPhysics", "Adds rotation and bounce to dropped items.",
                ModuleCategory.RENDER);
        this.rotate = bool("Rotate", "Rotate items while they fall", true);
        this.bounce = bool("Bounce", "Bounce items on the ground", true);
        this.bounceStrength = number("Bounce", "Bounce strength", 0.3, 0.05, 1.0, 0.05);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        // the rendering side reads these values every frame
        markActivity();
    }

    public boolean rotateItems() {
        return isEnabled() && rotate.isEnabled();
    }

    public boolean bounceItems() {
        return isEnabled() && bounce.isEnabled();
    }

    public double bounceStrength() {
        return bounceStrength.value();
    }
}
