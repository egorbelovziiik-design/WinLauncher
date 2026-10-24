package nexus.core.module.misc;

import nexus.core.event.ChatSendEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Lets you use color and formatting codes in your own messages. */
public final class ColorChatModule extends Module {

    private final BooleanSetting allowColors;
    private final BooleanSetting allowFormatting;
    private final BooleanSetting rainbowText;

    public ColorChatModule() {
        super("colorchat", "ColorChat", "Lets you use color codes in your messages.",
                ModuleCategory.MISC);
        this.allowColors = bool("Colors", "Allow color codes", true);
        this.allowFormatting = bool("Formatting", "Allow bold, italic and more", true);
        this.rainbowText = bool("Rainbow", "Cycle colors inside {rainbow: text}", false);
    }

    /** Converts the friendly & codes into Minecraft section signs. */
    public String apply(String text) {
        if (!isEnabled()) {
            return text;
        }
        String result = text;
        if (rainbowText.isEnabled()) {
            result = rainbow(result);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < result.length(); i++) {
            char c = result.charAt(i);
            if (c == '&' && i + 1 < result.length()) {
                char code = Character.toLowerCase(result.charAt(i + 1));
                boolean color = (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f');
                boolean format = "klmnor".indexOf(code) >= 0;
                if ((color && allowColors.isEnabled()) || (format && allowFormatting.isEnabled())) {
                    sb.append('\u00a7').append(code);
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private String rainbow(String text) {
        StringBuilder out = new StringBuilder();
        int index = 0;
        int start = text.indexOf("{rainbow:");
        while (start >= 0) {
            int end = text.indexOf('}', start);
            if (end < 0) {
                break;
            }
            out.append(text, index, start);
            String inner = text.substring(start + 9, end);
            String[] colors = new String[]{"c", "6", "e", "a", "b", "9", "d"};
            for (int i = 0; i < inner.length(); i++) {
                char c = inner.charAt(i);
                if (c == ' ') {
                    out.append(c);
                    continue;
                }
                out.append('\u00a7').append(colors[i % colors.length]).append(c);
            }
            index = end + 1;
            start = text.indexOf("{rainbow:", index);
        }
        out.append(text.substring(index));
        return out.toString();
    }

    @Override
    public void onChatSend(ChatSendEvent event) {
        if (isEnabled() && event.message != null) {
            event.message = apply(event.message);
        }
    }
}
