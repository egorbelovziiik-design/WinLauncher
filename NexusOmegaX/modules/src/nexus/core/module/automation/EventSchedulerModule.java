package nexus.core.module.automation;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import java.util.ArrayList;
import java.util.List;

/** Runs commands when an in game event happens. */
public final class EventSchedulerModule extends Module {

    private final ModeSetting trigger;
    private final StringSetting command;
    private final NumberSetting cooldown;
    private final nexus.util.TimeUtil.Timer timer = new nexus.util.TimeUtil.Timer();

    public EventSchedulerModule() {
        super("eventscheduler", "EventScheduler", "Runs a command when an event happens.",
                ModuleCategory.AUTOMATION);
        this.trigger = mode("Trigger", "Event that runs the command", "Low Health", "Low Health",
                "Low Hunger", "On Death", "On Join", "Full Inventory");
        this.command = text("Command", "Command to run (without the prefix)", "toggle autototem");
        this.cooldown = number("Cooldown", "Minimum delay between runs in ms", 3000.0, 0.0, 600000.0, 500.0);
    }

    @Override
    public void onEnable() {
        timer.reset();
    }

    @Override
    public void onTick(nexus.core.event.TickEvent event) {
        if (!mc().isInGame() || !timer.hasPassed((long) cooldown.value())) {
            return;
        }
        if (trigger.is("Low Health") && player().health() > 8.0f) {
            return;
        }
        if (trigger.is("Low Hunger") && player().hunger() > 6.0f) {
            return;
        }
        if (trigger.is("Full Inventory") && !player().inventory().isFull()) {
            return;
        }
        nexus.core.Nexus.instance().commands().execute(command.rawValue());
        markActivity();
        timer.reset();
    }

    @Override
    public void onDeath(nexus.core.event.DeathEvent event) {
        if (!isEnabled() || !trigger.is("On Death")) {
            return;
        }
        nexus.core.Nexus.instance().commands().execute(command.rawValue());
    }

    @Override
    public void onWorldJoin(nexus.core.event.WorldJoinEvent event) {
        if (!isEnabled() || !trigger.is("On Join")) {
            return;
        }
        nexus.core.Nexus.instance().commands().execute(command.rawValue());
    }

    @Override
    public String hudInfo() {
        return trigger.get();
    }
}
