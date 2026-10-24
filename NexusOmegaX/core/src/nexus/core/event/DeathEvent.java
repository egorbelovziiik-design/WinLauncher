package nexus.core.event;

/** Fired when the local player dies. */
public final class DeathEvent extends Event {

    public final String cause;
    public final int score;

    public DeathEvent(String cause, int score) {
        this.cause = cause;
        this.score = score;
    }

    @Override
    public boolean isCancellable() {
        return false;
    }
}
