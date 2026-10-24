package nexus.core.event;

/** Fired for outgoing and incoming packets. */
public final class PacketEvent extends Event {

    public final Direction direction;
    public final String type;
    public final int id;
    public final int size;
    public final byte[] payload;

    public PacketEvent(Direction direction, String type) {
        this(direction, type, -1, 0, null);
    }

    public PacketEvent(Direction direction, String type, int id, int size, byte[] payload) {
        this.direction = direction;
        this.type = type;
        this.id = id;
        this.size = size;
        this.payload = payload;
    }

    /** Packet name (same as {@link #type}). */
    public String name() {
        return type;
    }

    public int id() {
        return id;
    }

    public int size() {
        return size;
    }

    public byte[] payload() {
        return payload;
    }

    public boolean isSend() {
        return direction == Direction.SEND;
    }

    public boolean isReceive() {
        return direction == Direction.RECEIVE;
    }

    public enum Direction {
        SEND,
        RECEIVE
    }
}
