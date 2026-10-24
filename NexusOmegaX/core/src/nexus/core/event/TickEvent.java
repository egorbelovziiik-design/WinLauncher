package nexus.core.event;

/** Fired once per client tick before the player is updated. */
public final class TickEvent extends Event {

    public final Phase phase;
    public int tickCount;

    public TickEvent(Phase phase, int tickCount) {
        this.phase = phase;
        this.tickCount = tickCount;
    }

    @Override
    public boolean isCancellable() {
        return false;
    }

    public enum Phase {
        PRE,
        POST
    }
}
