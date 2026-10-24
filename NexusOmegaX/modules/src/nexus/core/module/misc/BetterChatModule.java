package nexus.core.module.misc;

import nexus.core.event.ChatReceiveEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Chat improvements: unlimited history, cleaner formatting and anti reset. */
public final class BetterChatModule extends Module {

    private final NumberSetting historySize;
    private final BooleanSetting keepOnJoin;
    private final BooleanSetting removeEmpty;

    public BetterChatModule() {
        super("betterchat", "BetterChat", "Improves the chat with history and clean formatting.",
                ModuleCategory.MISC);
        this.historySize = number("History", "Lines kept in the chat", 500.0, 50.0, 5000.0, 50.0);
        this.keepOnJoin = bool("Keep On Join", "Keep the chat when you join a world", true);
        this.removeEmpty = bool("Remove Empty", "Hide empty lines", true);
    }

    @Override
    public void onChatReceive(ChatReceiveEvent event) {
        if (!isEnabled() || event.message == null) {
            return;
        }
        if (removeEmpty.isEnabled() && event.message.trim().isEmpty()) {
            event.message = null;
        }
    }

    public int historySize() {
        return (int) historySize.value();
    }

    public boolean keepOnJoin() {
        return isEnabled() && keepOnJoin.isEnabled();
    }
}
