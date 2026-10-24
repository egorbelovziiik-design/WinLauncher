package nexus.core.module.automation;

import nexus.core.event.ChatReceiveEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Replies to private messages automatically. */
public final class AutoReplyModule extends Module {

    private final StringSetting reply;
    private final BooleanSetting onlyWhispers;
    private final NumberSetting cooldown;
    private final nexus.util.TimeUtil.Timer timer = new nexus.util.TimeUtil.Timer();

    public AutoReplyModule() {
        super("autoreply", "AutoReply", "Replies to private messages automatically.",
                ModuleCategory.AUTOMATION);
        this.reply = text("Reply", "Automatic reply text", "I am away right now, be back soon.");
        this.onlyWhispers = bool("Whispers Only", "Only reply to private messages", true);
        this.cooldown = number("Cooldown", "Delay between replies in ms", 3000.0, 0.0, 60000.0, 500.0);
    }

    @Override
    public void onEnable() {
        timer.reset();
    }

    @Override
    public void onChatReceive(ChatReceiveEvent event) {
        if (!isEnabled() || !timer.hasPassed((long) cooldown.value())) {
            return;
        }
        String text = event.message;
        if (text == null) {
            return;
        }
        boolean whisper = text.contains("whispers") || text.startsWith("/msg") || text.contains("[PM]");
        if (onlyWhispers.isEnabled() && !whisper) {
            return;
        }
        player().sendChat("/r " + reply.rawValue());
        markActivity();
        timer.reset();
    }

    @Override
    public String hudInfo() {
        return TimeUtil.format((long) cooldown.value()) + " cd";
    }
}
