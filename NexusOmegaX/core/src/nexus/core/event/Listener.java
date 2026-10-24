package nexus.core.event;

/**
 * Functional listener used by modules: {@code bus.subscribe(TickEvent.class, this::onTick)}.
 */
public interface Listener<T extends Event> {

    void on(T event);
}
