package nexus.adapter.v1165;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.LiteralText;
import nexus.api.IChat;

import java.util.ArrayList;
import java.util.List;

/** {@link IChat} implementation for Minecraft 1.16.5. */
public final class Nexus1165Chat implements IChat {

    private final Nexus1165Client client;

    Nexus1165Chat(Nexus1165Client client) {
        this.client = client;
    }

    @Override
    public void send(String text) {
        if (client.localPlayer() != null) {
            client.localPlayer().sendChatMessage(text);
        }
    }

    @Override
    public void print(String text) {
        if (client.localPlayer() != null) {
            client.localPlayer().sendMessage(new LiteralText(text), false);
        }
    }

    @Override
    public void print(String prefix, String text, int color) {
        print(prefix + " " + text);
    }

    @Override
    public List<String> history() {
        List<String> result = new ArrayList<String>();
        List<String> recent = client.handle().inGameHud.getChatHud().getMessageHistory();
        for (int i = recent.size() - 1; i >= 0 && result.size() < 100; i--) {
            result.add(recent.get(i));
        }
        return result;
    }

    @Override
    public void clearHistory() {
        client.handle().inGameHud.getChatHud().clear(true);
    }

    @Override
    public void addSuggestion(String suggestion) {
    }
}
