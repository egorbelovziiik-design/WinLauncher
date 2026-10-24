package nexus.core.module.automation;

import nexus.api.IEntity;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Automates piglin bartering by throwing gold ingots at piglins. */
public final class AutoBarterModule extends Module {

    private final NumberSetting range;
    private final NumberSetting delay;
    private final BooleanSetting pickupResults;
    private final nexus.util.TimeUtil.Timer timer = new nexus.util.TimeUtil.Timer();

    public AutoBarterModule() {
        super("autobarter", "AutoBarter", "Automates piglin bartering.", ModuleCategory.AUTOMATION);
        this.range = number("Range", "Maximum distance", 4.0, 1.0, 8.0, 0.5);
        this.delay = number("Delay", "Delay between trades in ms", 500.0, 100.0, 10000.0, 100.0);
        this.pickupResults = bool("Pick Up Results", "Walk towards the traded items", false);
    }

    @Override
    public void onTick(nexus.core.event.TickEvent event) {
        if (!mc().isInGame() || !timer.hasPassed((long) delay.value(), true)) {
            return;
        }
        int slot = player().inventory().findHotbar("gold_ingot");
        if (slot < 0) {
            return;
        }
        for (IEntity entity : mc().world().entities()) {
            if (!entity.typeId().contains("piglin") || !entity.isAlive()) {
                continue;
            }
            if (entity.distanceToPlayer() > range.value()) {
                continue;
            }
            player().inventory().setSelectedSlot(slot);
            player().lookAt(entity.position());
            player().dropSelectedItem(false);
            player().swingHand();
            markActivity();
            return;
        }
        if (pickupResults.isEnabled()) {
            for (IEntity entity : mc().world().items()) {
                if (entity.distanceToPlayer() < 6.0) {
                    player().lookAt(entity.position());
                    mc().input().setKeyState(nexus.core.automation.MacroManager.KeyMappingKeys.FORWARD, true);
                    return;
                }
            }
            mc().input().setKeyState(nexus.core.automation.MacroManager.KeyMappingKeys.FORWARD, false);
        }
    }

    @Override
    public void onDisable() {
        mc().input().setKeyState(nexus.core.automation.MacroManager.KeyMappingKeys.FORWARD, false);
    }

    @Override
    public void cleanup() {
        onDisable();
    }
}
