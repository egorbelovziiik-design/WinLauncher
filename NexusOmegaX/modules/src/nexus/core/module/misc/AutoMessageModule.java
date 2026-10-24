package nexus.core.module.misc;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import java.util.ArrayList;
import java.util.List;

/** Sends the next line of a rotating message list when you press the keybind. */
public final class AutoMessageModule extends Module {

    private final StringSetting messages;
    private final BooleanSetting cycle;
    private final List<String> lines = new ArrayList<String>();
    private int index;

    public AutoMessageModule() {
        super("automessage", "AutoMessage", "Sends the next line of a message list.",
                ModuleCategory.MISC);
        this.messages = text("Messages", "One message per line, separate with |", "hello|good game|see you");
        this.cycle = bool("Cycle", "Wrap around the end of the list", true);
    }

    private List<String> lines() {
        lines.clear();
        for (String part : messages.rawValue().split("\\|")) {
            String value = part.trim();
            if (!value.isEmpty()) {
                lines.add(value);
            }
        }
        return lines;
    }

    public String sendNext() {
        List<String> values = lines();
        if (values.isEmpty()) {
            return null;
        }
        if (index >= values.size()) {
            if (cycle.isEnabled()) {
                index = 0;
            } else {
                return null;
            }
        }
        String message = values.get(index);
        index++;
        player().sendChat(message);
        markActivity();
        return message;
    }

    @Override
    public void onKey(nexus.core.event.KeyEvent event) {
        if (isEnabled() && event.isPress() && event.key == keybindSetting().key()) {
            sendNext();
        }
    }

    @Override
    public String hudInfo() {
        return lines().size() + " lines";
    }
}
