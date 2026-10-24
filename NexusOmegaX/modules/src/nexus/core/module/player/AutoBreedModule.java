package nexus.core.module.player;

import nexus.api.IEntity;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Breeds nearby animals with the food they like. */
public final class AutoBreedModule extends Module {

    private final NumberSetting range;
    private final NumberSetting delay;
    private final BooleanSetting notify;
    private final TimeUtil.Timer timer = new TimeUtil.Timer();
    private int bred;

    public AutoBreedModule() {
        super("autobreed", "AutoBreed", "Breeds nearby animals automatically.", ModuleCategory.PLAYER);
        this.range = number("Range", "Maximum distance", 5.0, 1.0, 12.0, 0.5);
        this.delay = number("Delay", "Delay between breedings in ms", 1200.0, 200.0, 10000.0, 100.0);
        this.notify = bool("Notify", "Show a notification for every breeding", true);
    }

    @Override
    public void onEnable() {
        bred = 0;
        timer.reset();
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !timer.hasPassed((long) delay.value(), true)) {
            return;
        }
        for (IEntity entity : mc().world().entities()) {
            if (!entity.isAlive() || entity.isPlayer() || entity.isHostile() || !entity.isMob()) {
                continue;
            }
            if (entity.isBaby()) {
                continue;
            }
            if (entity.distanceToPlayer() > range.value()) {
                continue;
            }
            String food = foodFor(entity.typeId());
            int slot = player().inventory().findHotbar(food);
            if (slot < 0) {
                continue;
            }
            int previous = player().inventory().selectedSlot();
            player().inventory().setSelectedSlot(slot);
            player().lookAt(entity.eyePosition());
            mc().interaction().interactEntity(entity);
            player().inventory().setSelectedSlot(previous);
            bred++;
            if (notify.isEnabled()) {
                nexus.core.Nexus.instance().notifications().info("Breed", entity.name());
            }
            markActivity();
            return;
        }
    }

    private String foodFor(String typeId) {
        if (typeId.contains("cow") || typeId.contains("sheep") || typeId.contains("goat")) {
            return "wheat";
        }
        if (typeId.contains("pig")) {
            return "carrot";
        }
        if (typeId.contains("chicken")) {
            return "seeds";
        }
        if (typeId.contains("horse") || typeId.contains("donkey")) {
            return "golden_apple";
        }
        if (typeId.contains("cat") || typeId.contains("wolf")) {
            return "fish";
        }
        if (typeId.contains("rabbit")) {
            return "dandelion";
        }
        return "wheat";
    }

    public int bred() {
        return bred;
    }
}
