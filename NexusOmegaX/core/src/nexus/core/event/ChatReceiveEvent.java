package nexus.core.event;

/** Fired for every incoming chat / system message. */
public final class ChatReceiveEvent extends Event {

    public String message;
    public final String sender;

    public ChatReceiveEvent(String message, String sender) {
        this.message = message;
        this.sender = sender;
    }
}
