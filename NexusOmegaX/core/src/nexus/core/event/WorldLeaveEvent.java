package nexus.core.event;

/** Fired when the current world / server is left. */
public final class WorldLeaveEvent extends Event {

    public final String address;

    public WorldLeaveEvent(String address) {
        this.address = address;
    }

    @Override
    public boolean isCancellable() {
        return false;
    }
}
