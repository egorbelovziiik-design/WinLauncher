package nexus.core.module.automation;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import java.util.ArrayList;
import java.util.List;

/** Sends a rotating message to the chat on a timer. */
public final class AutoAdvertModule extends Module {

    private final NumberSetting interval;
    private final NumberSetting maxMessages;
    private final List<String> messages = new ArrayList<String>();
    private int index;
    private final nexus.util.TimeUtil.Timer timer = new nexus.util.TimeUtil.Timer();

    public AutoAdvertModule() {
        super("autoadvert", "AutoAdvert", "Sends a rotating chat message on a timer.",
                ModuleCategory.AUTOMATION);
        this.interval = number("Interval", "Delay between messages in ms", 300000.0, 10000.0, 3600000.0, 10000.0);
        this.maxMessages = number("Max Messages", "Stop after this many messages", 0.0, 0.0, 1000.0, 1.0);
        messages.add("Nexus Client Omega X - the ultimate Minecraft experience");
    }

    public List<String> messages() {
        return messages;
    }

    public void setMessages(List<String> values) {
        messages.clear();
        messages.addAll(values);
        index = 0;
    }

    @Override
    public void onEnable() {
        timer.reset();
        index = 0;
    }

    private int sent;

    @Override
    public void onTick(nexus.core.event.TickEvent event) {
        if (!mc().isInGame() || messages.isEmpty() || !timer.hasPassed((long) interval.value(), true)) {
            return;
        }
        if (maxMessages.value() > 0 && sent >= maxMessages.value()) {
            return;
        }
        player().sendChat(messages.get(index % messages.size()));
        index++;
        sent++;
        markActivity();
    }

    @Override
    public String hudInfo() {
        return messages.size() + " messages";
    }
}
