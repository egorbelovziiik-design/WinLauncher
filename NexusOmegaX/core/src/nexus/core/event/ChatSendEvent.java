package nexus.core.event;

/** Fired when the player sends a chat message or command. */
public final class ChatSendEvent extends Event {

    public String message;

    public ChatSendEvent(String message) {
        this.message = message;
    }

    public boolean isCommand() {
        return message != null && message.startsWith("/");
    }
}
