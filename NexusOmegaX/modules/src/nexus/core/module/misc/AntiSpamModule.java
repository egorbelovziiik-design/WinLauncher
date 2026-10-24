package nexus.core.module.misc;

import nexus.core.event.ChatSendEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import java.util.ArrayDeque;
import java.util.Deque;

/** Blocks duplicate messages so you do not flood the chat by accident. */
public final class AntiSpamModule extends Module {

    private final NumberSetting window;
    private final NumberSetting maxRepeats;
    private final BooleanSetting blockDuplicates;
    private final Deque<String> recent = new ArrayDeque<String>();

    public AntiSpamModule() {
        super("antispam", "AntiSpam", "Blocks duplicate messages so you do not flood the chat.",
                ModuleCategory.MISC);
        this.window = number("Window", "Remembered messages", 5.0, 2.0, 20.0, 1.0);
        this.maxRepeats = number("Max Repeats", "Allowed repeats inside the window", 1.0, 0.0, 10.0, 1.0);
        this.blockDuplicates = bool("Block Duplicates", "Block exact duplicates", true);
    }

    @Override
    public void onChatSend(ChatSendEvent event) {
        if (!isEnabled() || event.message == null) {
            return;
        }
        int count = 0;
        for (String value : recent) {
            if (value.equals(event.message)) {
                count++;
            }
        }
        recent.addLast(event.message);
        while (recent.size() > window.value()) {
            recent.removeFirst();
        }
        if (blockDuplicates.isEnabled() && count >= maxRepeats.value()) {
            event.message = null;
            nexus.core.Nexus.instance().notifications().warn("AntiSpam", "Duplicate message blocked");
            markActivity();
        }
    }
}
