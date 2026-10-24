package nexus.core.module.misc;

import nexus.core.event.ChatReceiveEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Highlights messages that mention you, your friends or keywords. */
public final class ChatHighlighterModule extends Module {

    private final MultiSelectSetting rules;
    private final StringSetting keywords;
    private final ColorSetting color;

    public ChatHighlighterModule() {
        super("chathighlighter", "ChatHighlighter", "Highlights messages that mention you.",
                ModuleCategory.MISC);
        this.rules = multi("Highlight", "What to highlight", new String[]{"My Name", "Friends",
                "Enemies", "Keywords"}, "My Name", "Keywords");
        this.keywords = text("Keywords", "Comma separated keywords", "");
        this.color = color("Color", "Highlight color", 0xFFFBBF24);
    }

    @Override
    public void onChatReceive(ChatReceiveEvent event) {
        if (!isEnabled() || event.message == null) {
            return;
        }
        String lower = event.message.toLowerCase();
        boolean hit = false;
        if (rules.isSelected("My Name")) {
            String mine = player().name();
            hit = mine != null && lower.contains(mine.toLowerCase());
        }
        if (!hit && rules.isSelected("Friends")) {
            for (String friend : nexus.core.Nexus.instance().friends().friends()) {
                if (lower.contains(friend.toLowerCase())) {
                    hit = true;
                    break;
                }
            }
        }
        if (!hit && rules.isSelected("Enemies")) {
            for (String enemy : nexus.core.Nexus.instance().friends().enemies()) {
                if (lower.contains(enemy.toLowerCase())) {
                    hit = true;
                    break;
                }
            }
        }
        if (!hit && rules.isSelected("Keywords")) {
            for (String part : keywords.rawValue().split(",")) {
                String word = part.trim().toLowerCase();
                if (!word.isEmpty() && lower.contains(word)) {
                    hit = true;
                    break;
                }
            }
        }
        if (hit) {
            event.message = "\u00a7e" + event.message;
            markActivity();
        }
    }
}
