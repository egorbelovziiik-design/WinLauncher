package nexus.core.module.misc;

import nexus.core.event.ChatReceiveEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import java.util.ArrayList;
import java.util.List;

/** Hides chat lines containing words you do not want to see. */
public final class ChatFilterModule extends Module {

    private final ModeSetting mode;
    private final StringSetting words;
    private final BooleanSetting caseSensitive;
    private final List<String> blocked = new ArrayList<String>();

    public ChatFilterModule() {
        super("chatfilter", "ChatFilter", "Hides chat lines that contain certain words.",
                ModuleCategory.MISC);
        this.mode = mode("Mode", "How to filter", "Hide", "Hide", "Highlight", "Replace");
        this.words = text("Words", "Comma separated list", "");
        this.caseSensitive = bool("Case Sensitive", "Match the case exactly", false);
    }

    private List<String> words() {
        blocked.clear();
        for (String part : words.rawValue().split(",")) {
            String value = part.trim();
            if (!value.isEmpty()) {
                blocked.add(caseSensitive.isEnabled() ? value : value.toLowerCase());
            }
        }
        return blocked;
    }

    @Override
    public void onChatReceive(ChatReceiveEvent event) {
        if (!isEnabled() || event.message == null) {
            return;
        }
        String text = caseSensitive.isEnabled() ? event.message : event.message.toLowerCase();
        for (String word : words()) {
            if (!text.contains(word)) {
                continue;
            }
            if (mode.is("Hide")) {
                event.message = null;
            } else if (mode.is("Highlight")) {
                event.message = "\u00a7e" + event.message;
            } else {
                event.message = event.message.replace(word, "***");
            }
            markActivity();
            return;
        }
    }

    @Override
    public String hudInfo() {
        return words().size() + " words";
    }
}
