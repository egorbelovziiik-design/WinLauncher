package nexus.core.module.player;

import nexus.api.IEntity;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Heals tamed animals when their health is low. */
public final class AutoFeedModule extends Module {

    private final NumberSetting healthThreshold;
    private final NumberSetting range;
    private final TimeUtil.Timer timer = new TimeUtil.Timer();

    public AutoFeedModule() {
        super("autofeed", "AutoFeed", "Feeds tamed animals when they are hurt.", ModuleCategory.PLAYER);
        this.healthThreshold = number("Health", "Feed below this health percentage", 60.0, 5.0, 100.0, 5.0);
        this.range = number("Range", "Maximum distance", 6.0, 1.0, 16.0, 0.5);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !timer.hasPassed(1500L, true)) {
            return;
        }
        for (IEntity entity : mc().world().entities()) {
            if (!entity.isAlive() || entity.isPlayer() || !entity.isMob()) {
                continue;
            }
            if (entity.distanceToPlayer() > range.value()) {
                continue;
            }
            double percent = entity.health() / Math.max(1.0f, entity.maxHealth()) * 100.0;
            if (percent > healthThreshold.value()) {
                continue;
            }
            String food = entity.typeId().contains("cat") || entity.typeId().contains("wolf") ? "fish" : "wheat";
            int slot = player().inventory().findHotbar(food);
            if (slot < 0) {
                continue;
            }
            int previous = player().inventory().selectedSlot();
            player().inventory().setSelectedSlot(slot);
            player().lookAt(entity.eyePosition());
            mc().interaction().interactEntity(entity);
            player().inventory().setSelectedSlot(previous);
            markActivity();
            return;
        }
    }
}
