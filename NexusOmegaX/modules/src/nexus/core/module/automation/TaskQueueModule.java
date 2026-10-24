package nexus.core.module.automation;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import java.util.ArrayDeque;
import java.util.Deque;

/** Runs a queue of chat commands one after another. */
public final class TaskQueueModule extends Module {

    private final NumberSetting interval;
    private final BooleanSetting repeat;
    private final Deque<String> queue = new ArrayDeque<String>();
    private final nexus.util.TimeUtil.Timer timer = new nexus.util.TimeUtil.Timer();

    public TaskQueueModule() {
        super("taskqueue", "TaskQueue", "Runs queued commands one after another.",
                ModuleCategory.AUTOMATION);
        this.interval = number("Interval", "Delay between tasks in ms", 1000.0, 50.0, 60000.0, 50.0);
        this.repeat = bool("Repeat", "Run the queue in a loop", false);
    }

    @Override
    public void onEnable() {
        timer.reset();
    }

    public void add(String command) {
        queue.addLast(command);
    }

    public void clear() {
        queue.clear();
    }

    public int size() {
        return queue.size();
    }

    @Override
    public void onTick(nexus.core.event.TickEvent event) {
        if (queue.isEmpty() || !timer.hasPassed((long) interval.value(), true)) {
            return;
        }
        String command = queue.removeFirst();
        if (repeat.isEnabled()) {
            queue.addLast(command);
        }
        nexus.core.Nexus.instance().commands().execute(command);
        markActivity();
    }

    @Override
    public String hudInfo() {
        return queue.size() + " queued";
    }
}
