package nexus.core.module.misc;

import nexus.core.event.ChatReceiveEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Saves the chat to a log file on disk. */
public final class ChatLoggerModule extends Module {

    private final BooleanSetting timestamps;
    private final BooleanSetting includeServer;
    private final List<String> buffer = new ArrayList<String>();
    private File file;

    public ChatLoggerModule() {
        super("chatlogger", "ChatLogger", "Saves the chat into a log file.", ModuleCategory.MISC);
        this.timestamps = bool("Timestamps", "Add a timestamp to each line", true);
        this.includeServer = bool("Server Name", "Include the server name in the file name", true);
    }

    @Override
    public void onEnable() {
        String name = "chat-" + (includeServer.isEnabled() ? nexus.core.Nexus.instance().session().currentWorld() : "local");
        file = new File(new File(nexus.core.NexusInfo.directory(), "logs"),
                nexus.util.FileUtil.sanitize(name) + ".log");
        nexus.util.FileUtil.ensure(file.getParentFile());
        buffer.clear();
    }

    @Override
    public void onDisable() {
        flush();
    }

    @Override
    public void cleanup() {
        flush();
    }

    @Override
    public void onChatReceive(ChatReceiveEvent event) {
        if (!isEnabled() || event.message == null) {
            return;
        }
        String line = (timestamps.isEnabled() ? "[" + nexus.util.TimeUtil.formatClock(System.currentTimeMillis()) + "] " : "")
                + event.message;
        buffer.add(strip(line));
        if (buffer.size() > 50) {
            flush();
        }
    }

    private String strip(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7') {
                i++;
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private void flush() {
        if (file == null || buffer.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String line : buffer) {
            sb.append(line).append('\n');
        }
        buffer.clear();
        String existing = nexus.util.FileUtil.read(file);
        nexus.util.FileUtil.write(file, (existing == null ? "" : existing) + sb);
    }

    public File file() {
        return file;
    }
}
