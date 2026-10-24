package nexus.core.module.misc;

import nexus.core.event.ChatSendEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Shows commands that other players execute (server permission required). */
public final class CommandSpyModule extends Module {

    private final BooleanSetting includeSelf;
    private final BooleanSetting notify;
    private final StringSetting filter;

    public CommandSpyModule() {
        super("commandspy", "CommandSpy", "Shows commands that are executed in the chat.",
                ModuleCategory.MISC);
        this.includeSelf = bool("Include Self", "Also show your own commands", false);
        this.notify = bool("Notify", "Show a notification for each command", false);
        this.filter = text("Filter", "Only show commands containing this text", "");
    }

    @Override
    public void onChatSend(ChatSendEvent event) {
        if (!isEnabled() || event.message == null || !event.isCommand()) {
            return;
        }
        boolean own = event.message.startsWith(".") || event.message.startsWith("/");
        if (own && !includeSelf.isEnabled()) {
            return;
        }
        String wanted = filter.rawValue().trim().toLowerCase();
        if (!wanted.isEmpty() && !event.message.toLowerCase().contains(wanted)) {
            return;
        }
        String line = "[CommandSpy] " + event.message;
        if (notify.isEnabled()) {
            nexus.core.Nexus.instance().notifications().info("CommandSpy", event.message);
        } else {
            mc().chat().print(line);
        }
        markActivity();
    }
}
