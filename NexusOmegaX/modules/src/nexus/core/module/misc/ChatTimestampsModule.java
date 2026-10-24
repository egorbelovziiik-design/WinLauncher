package nexus.core.module.misc;

import nexus.core.event.ChatReceiveEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Adds a timestamp to every chat message. */
public final class ChatTimestampsModule extends Module {

    private final ModeSetting format;
    private final ColorSetting color;
    private final BooleanSetting brackets;

    public ChatTimestampsModule() {
        super("chattimestamps", "ChatTimestamps", "Adds a timestamp to chat messages.",
                ModuleCategory.MISC);
        this.format = mode("Format", "Time format", "HH:MM", "HH:MM", "HH:MM:SS", "12h");
        this.color = color("Color", "Timestamp color", 0xFF7C3AED);
        this.brackets = bool("Brackets", "Wrap the time in brackets", true);
    }

    @Override
    public void onChatReceive(ChatReceiveEvent event) {
        if (!isEnabled() || event.message == null || event.message.trim().isEmpty()) {
            return;
        }
        event.message = prefix() + " " + event.message;
    }

    private String prefix() {
        long now = System.currentTimeMillis();
        String time;
        if (format.is("HH:MM:SS")) {
            time = TimeUtil.formatClock(now) + ":" + second(now);
        } else if (format.is("12h")) {
            time = TimeUtil.formatClock(now) + " " + amPm(now);
        } else {
            time = TimeUtil.formatClock(now);
        }
        return (brackets.isEnabled() ? "[" + time + "]" : time);
    }

    private String second(long now) {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTimeInMillis(now);
        int value = calendar.get(java.util.Calendar.SECOND);
        return (value < 10 ? "0" : "") + value;
    }

    private String amPm(long now) {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTimeInMillis(now);
        return calendar.get(java.util.Calendar.AM_PM) == java.util.Calendar.AM ? "AM" : "PM";
    }

    public int timestampColor() {
        return color.effectiveColor(System.currentTimeMillis());
    }

    @Override
    public String hudInfo() {
        return format.get();
    }
}
