package nexus.core.event;

/** Fired when a world / server has been joined. */
public final class WorldJoinEvent extends Event {

    public final String address;
    public final boolean singleplayer;

    public WorldJoinEvent(String address, boolean singleplayer) {
        this.address = address;
        this.singleplayer = singleplayer;
    }

    @Override
    public boolean isCancellable() {
        return false;
    }
}
