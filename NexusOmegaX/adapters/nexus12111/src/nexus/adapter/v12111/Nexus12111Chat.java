package nexus.adapter.v12111;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import nexus.api.IChat;

import java.util.ArrayList;
import java.util.List;

/** {@link IChat} implementation for Minecraft 1.21.11. */
public final class Nexus12111Chat implements IChat {

    private final Nexus12111Client client;

    Nexus12111Chat(Nexus12111Client client) {
        this.client = client;
    }

    private Minecraft handle() {
        return client.handle();
    }

    @Override
    public void send(String text) {
        if (handle().player == null) {
            return;
        }
        if (text.startsWith("/")) {
            handle().player.connection.sendCommand(text.substring(1));
        } else {
            handle().player.connection.sendChat(text);
        }
    }

    @Override
    public void print(String text) {
        if (handle().player == null) {
            return;
        }
        handle().player.displayClientMessage(Component.literal(text), false);
    }

    @Override
    public void print(String prefix, String text, int color) {
        print(prefix + " " + text);
    }

    @Override
    public List<String> history() {
        List<String> result = new ArrayList<String>();
        for (String message : handle().gui.getChat().getRecentChat()) {
            result.add(message);
        }
        return result;
    }

    @Override
    public void clearHistory() {
        handle().gui.getChat().clearMessages(true);
    }

    @Override
    public void addSuggestion(String suggestion) {
        if (handle().player != null) {
            handle().player.connection.sendCommand(suggestion);
        }
    }
}
