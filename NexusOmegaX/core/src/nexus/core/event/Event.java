package nexus.core.event;

/**
 * Base class of every Nexus event. Events are dispatched through
 * {@link EventBus} and are plain data carriers.
 */
public abstract class Event {

    private boolean cancelled;

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public void cancel() {
        this.cancelled = true;
    }

    /** Only meaningful for events that can actually be cancelled. */
    public boolean isCancellable() {
        return true;
    }
}
