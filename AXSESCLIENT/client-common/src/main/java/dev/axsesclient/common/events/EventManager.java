package dev.axsesclient.common.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class EventManager {
    public static final class ListenerEntry<E> {
        private final int priority;
        private final Consumer<E> consumer;

        public ListenerEntry(int priority, Consumer<E> consumer) {
            this.priority = priority;
            this.consumer = consumer;
        }
    }

    private final Map<Class<?>, List<ListenerEntry<?>>> listeners = new ConcurrentHashMap<Class<?>, List<ListenerEntry<?>>>();
    private Consumer<Throwable> errorHandler;

    public void setErrorHandler(Consumer<Throwable> errorHandler) {
        this.errorHandler = errorHandler;
    }

    public synchronized <E> void subscribe(Class<E> eventType, Consumer<E> listener) {
        subscribe(eventType, 0, listener);
    }

    public synchronized <E> void subscribe(Class<E> eventType, int priority, Consumer<E> listener) {
        List<ListenerEntry<?>> current = listeners.get(eventType);
        List<ListenerEntry<?>> copy = current == null
                ? new ArrayList<ListenerEntry<?>>()
                : new ArrayList<ListenerEntry<?>>(current);
        copy.add(new ListenerEntry<E>(priority, listener));
        Collections.sort(copy, new Comparator<ListenerEntry<?>>() {
            @Override
            public int compare(ListenerEntry<?> a, ListenerEntry<?> b) {
                return Integer.compare(b.priority, a.priority);
            }
        });
        listeners.put(eventType, Collections.unmodifiableList(copy));
    }

    @SuppressWarnings("unchecked")
    public <E> void post(E event) {
        if (event == null) {
            return;
        }
        List<ListenerEntry<?>> list = listeners.get(event.getClass());
        if (list == null || list.isEmpty()) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            ListenerEntry<E> entry = (ListenerEntry<E>) list.get(i);
            try {
                entry.consumer.accept(event);
            } catch (Throwable t) {
                if (errorHandler != null) {
                    errorHandler.accept(t);
                }
            }
        }
    }
}
