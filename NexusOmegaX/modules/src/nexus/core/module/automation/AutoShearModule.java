package nexus.core.module.automation;

import nexus.api.IEntity;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Shears sheep that have wool. */
public final class AutoShearModule extends Module {

    private final NumberSetting range;
    private final NumberSetting delay;
    private final nexus.util.TimeUtil.Timer timer = new nexus.util.TimeUtil.Timer();

    public AutoShearModule() {
        super("autoshear", "AutoShear", "Shears sheep that still have wool.", ModuleCategory.AUTOMATION);
        this.range = number("Range", "Maximum distance", 4.5, 1.0, 8.0, 0.5);
        this.delay = number("Delay", "Delay between sheep in ms", 350.0, 0.0, 5000.0, 50.0);
    }

    @Override
    public void onTick(nexus.core.event.TickEvent event) {
        if (!mc().isInGame() || !timer.hasPassed((long) delay.value(), true)) {
            return;
        }
        int slot = player().inventory().findHotbar("shears");
        if (slot < 0) {
            return;
        }
        for (IEntity entity : mc().world().entities()) {
            if (!entity.typeId().contains("sheep") || !entity.isAlive()) {
                continue;
            }
            if (entity.distanceToPlayer() > range.value()) {
                continue;
            }
            player().inventory().setSelectedSlot(slot);
            player().lookAt(entity.position());
            mc().interaction().interactEntity(entity);
            player().swingHand();
            markActivity();
            return;
        }
    }
}
