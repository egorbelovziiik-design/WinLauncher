package nexus.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight logging with an in-memory ring buffer. The buffer feeds the
 * launcher LOGS tab and the {@code .debug} command.
 */
public final class Log {

    public static final int MAX_ENTRIES = 2000;

    private static final List<Entry> ENTRIES = new ArrayList<Entry>(MAX_ENTRIES);
    private static Listener listener;

    public interface Listener {
        void onLog(Level level, String message);
    }

    public enum Level {
        DEBUG("DEBUG"),
        INFO("INFO"),
        WARN("WARN"),
        ERROR("ERROR");

        private final String display;

        Level(String display) {
            this.display = display;
        }

        public String display() {
            return display;
        }
    }

    public static final class Entry {
        public final long time;
        public final Level level;
        public final String message;

        Entry(Level level, String message) {
            this.time = System.currentTimeMillis();
            this.level = level;
            this.message = message;
        }

        @Override
        public String toString() {
            return String.format("[%s] %s: %s", TimeUtil.formatClock(time), level.display(), message);
        }
    }

    private Log() {
    }

    public static void setListener(Listener value) {
        listener = value;
    }

    public static void debug(String message) {
        log(Level.DEBUG, message);
    }

    public static void info(String message) {
        log(Level.INFO, message);
    }

    public static void warn(String message) {
        log(Level.WARN, message);
    }

    public static void error(String message, Throwable throwable) {
        log(Level.ERROR, message + (throwable == null ? "" : " -> " + throwable));
        if (throwable != null) {
            throwable.printStackTrace();
        }
    }

    private static void log(Level level, String message) {
        synchronized (ENTRIES) {
            if (ENTRIES.size() >= MAX_ENTRIES) {
                ENTRIES.remove(0);
            }
            ENTRIES.add(new Entry(level, message));
        }
        Listener active = listener;
        if (active != null) {
            try {
                active.onLog(level, message);
            } catch (Throwable ignored) {
                // never let logging break the game
            }
        }
    }

    public static List<Entry> entries() {
        synchronized (ENTRIES) {
            return new ArrayList<Entry>(ENTRIES);
        }
    }

    public static String tail(int count) {
        List<Entry> all = entries();
        int from = Math.max(0, all.size() - count);
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < all.size(); i++) {
            sb.append(all.get(i).toString()).append('\n');
        }
        return sb.toString();
    }

    public static void clear() {
        synchronized (ENTRIES) {
            ENTRIES.clear();
        }
    }
}
