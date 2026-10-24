package nexus.core.module.misc;

import nexus.core.event.ChatReceiveEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** Translates common words and slang in the chat using your own dictionary. */
public final class ChatTranslatorModule extends Module {

    private final StringSetting dictionary;
    private final BooleanSetting keepOriginal;
    private final Map<String, String> entries = new LinkedHashMap<String, String>();

    public ChatTranslatorModule() {
        super("chattranslator", "ChatTranslator", "Translates words in chat with your dictionary.",
                ModuleCategory.MISC);
        this.dictionary = text("Dictionary", "from=to pairs separated by commas", "gg=good game,wp=well played");
        this.keepOriginal = bool("Keep Original", "Show the original word in brackets", true);
    }

    private Map<String, String> parse() {
        entries.clear();
        for (String pair : dictionary.rawValue().split(",")) {
            int index = pair.indexOf('=');
            if (index <= 0 || index + 1 >= pair.length()) {
                continue;
            }
            entries.put(pair.substring(0, index).trim().toLowerCase(),
                    pair.substring(index + 1).trim());
        }
        return entries;
    }

    @Override
    public void onChatReceive(ChatReceiveEvent event) {
        if (!isEnabled() || event.message == null) {
            return;
        }
        String result = event.message;
        for (Map.Entry<String, String> entry : parse().entrySet()) {
            if (!result.toLowerCase().contains(entry.getKey())) {
                continue;
            }
            String replacement = keepOriginal.isEnabled()
                    ? entry.getValue() + " (" + entry.getKey() + ")"
                    : entry.getValue();
            result = result.replace(entry.getKey(), replacement);
        }
        event.message = result;
    }

    public int entries() {
        return parse().size();
    }

    @Override
    public String hudInfo() {
        return entries() + " words";
    }
}
