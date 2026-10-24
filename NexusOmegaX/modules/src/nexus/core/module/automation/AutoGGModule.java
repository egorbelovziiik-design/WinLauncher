package nexus.core.module.automation;

import nexus.core.event.ChatReceiveEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Sends a friendly message after a round or duel ends. */
public final class AutoGGModule extends Module {

    private final StringSetting message;
    private final BooleanSetting onDeath;
    private final BooleanSetting onWinWord;
    private final nexus.util.TimeUtil.Timer timer = new nexus.util.TimeUtil.Timer();

    public AutoGGModule() {
        super("autogg", "AutoGG", "Sends a message after a round or duel.", ModuleCategory.AUTOMATION);
        this.message = text("Message", "Message to send", "gg");
        this.onDeath = bool("On Death", "Send when you die", true);
        this.onWinWord = bool("On Round End", "Send when the round ends", true);
    }

    @Override
    public void onEnable() {
        timer.reset();
    }

    @Override
    public void onDeath(nexus.core.event.DeathEvent event) {
        if (isEnabled() && onDeath.isEnabled() && timer.hasPassed(3000L)) {
            player().sendChat(message.rawValue());
            timer.reset();
            markActivity();
        }
    }

    @Override
    public void onChatReceive(ChatReceiveEvent event) {
        if (!isEnabled() || !onWinWord.isEnabled() || !timer.hasPassed(3000L)) {
            return;
        }
        String text = event.message;
        if (text == null) {
            return;
        }
        String lower = text.toLowerCase();
        if (lower.contains("has won") || lower.contains("victory") || lower.contains("game over")) {
            player().sendChat(message.rawValue());
            timer.reset();
            markActivity();
        }
    }

    @Override
    public String hudInfo() {
        return message.rawValue();
    }
}
