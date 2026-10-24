package nexus.core.event;

import nexus.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * High performance event bus.
 * <p>
 * Design goals (performance section of the spec):
 * <ul>
 *   <li>No reflection: listeners are plain functional interfaces.</li>
 *   <li>Handlers are sorted once at registration time, not per dispatch.</li>
 *   <li>Copy-on-write handler lists: subscribing during dispatch is safe and
 *       does not allocate a new list per frame.</li>
 *   <li>One try/catch per listener so a broken module cannot break the game.</li>
 * </ul>
 */
public final class EventBus {

    private static final class Handler {
        private final Object owner;
        private final int priority;
        private final Listener<? extends Event> listener;

        Handler(Object owner, int priority, Listener<? extends Event> listener) {
            this.owner = owner;
            this.priority = priority;
            this.listener = listener;
        }
    }

    private static final Comparator<Handler> BY_PRIORITY = new Comparator<Handler>() {
        @Override
        public int compare(Handler a, Handler b) {
            return Integer.compare(b.priority, a.priority);
        }
    };

    private final Map<Class<?>, List<Handler>> handlers = new HashMap<Class<?>, List<Handler>>();
    private boolean dispatching;

    public <T extends Event> void subscribe(Class<T> type, Listener<T> listener) {
        subscribe(type, listener, 0);
    }

    public <T extends Event> void subscribe(Class<T> type, Listener<T> listener, int priority) {
        subscribe(type, listener, priority, null);
    }

    public <T extends Event> void subscribe(Class<T> type, Listener<T> listener, int priority, Object owner) {
        if (type == null || listener == null) {
            return;
        }
        List<Handler> list = handlers.get(type);
        if (list == null) {
            list = new CopyOnWriteArrayList<Handler>();
            handlers.put(type, list);
        }
        list.add(new Handler(owner == null ? listener : owner, priority, listener));
        Collections.sort(list, BY_PRIORITY);
    }

    /** Removes every listener registered by {@code owner} (used when unloading). */
    public void unsubscribe(Object owner) {
        if (owner == null) {
            return;
        }
        for (List<Handler> list : handlers.values()) {
            List<Handler> toRemove = new ArrayList<Handler>();
            for (Handler handler : list) {
                if (handler.owner == owner) {
                    toRemove.add(handler);
                }
            }
            list.removeAll(toRemove);
        }
    }

    public void unsubscribe(Class<?> type, Object owner) {
        List<Handler> list = handlers.get(type);
        if (list == null) {
            return;
        }
        List<Handler> toRemove = new ArrayList<Handler>();
        for (Handler handler : list) {
            if (handler.owner == owner) {
                toRemove.add(handler);
            }
        }
        list.removeAll(toRemove);
    }

    public <T extends Event> T post(T event) {
        if (event == null) {
            return null;
        }
        List<Handler> list = handlers.get(event.getClass());
        if (list == null || list.isEmpty()) {
            return event;
        }
        boolean wasDispatching = dispatching;
        dispatching = true;
        try {
            for (Handler handler : list) {
                if (event.isCancelled() && handler.priority < 0) {
                    // low priority listeners are skipped once cancelled
                    continue;
                }
                try {
                    invoke(handler, event);
                } catch (Throwable throwable) {
                    Log.error("Exception while dispatching " + event.getClass().getSimpleName(), throwable);
                }
            }
        } finally {
            dispatching = wasDispatching;
        }
        return event;
    }

    @SuppressWarnings("unchecked")
    private void invoke(Handler handler, Event event) {
        ((Listener<Event>) handler.listener).on(event);
    }

    public int handlerCount(Class<?> type) {
        List<Handler> list = handlers.get(type);
        return list == null ? 0 : list.size();
    }

    public int totalHandlers() {
        int total = 0;
        for (List<Handler> list : handlers.values()) {
            total += list.size();
        }
        return total;
    }

    public boolean isDispatching() {
        return dispatching;
    }

    public void clear() {
        handlers.clear();
    }
}
