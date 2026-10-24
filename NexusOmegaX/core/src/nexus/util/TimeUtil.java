package nexus.util;

/**
 * Small timing helpers: cooldowns, tick counters and a stopwatch.
 */
public final class TimeUtil {

    private TimeUtil() {
    }

    public static final class Timer {
        private long last = 0L;

        public boolean hasPassed(long ms) {
            return System.currentTimeMillis() - last >= ms;
        }

        public boolean hasPassed(long ms, boolean reset) {
            if (hasPassed(ms)) {
                if (reset) {
                    reset();
                }
                return true;
            }
            return false;
        }

        public long elapsed() {
            return System.currentTimeMillis() - last;
        }

        public void reset() {
            last = System.currentTimeMillis();
        }

        public long last() {
            return last;
        }
    }

    public static final class Stopwatch {
        private long start = System.currentTimeMillis();

        public void restart() {
            start = System.currentTimeMillis();
        }

        public long elapsedMs() {
            return System.currentTimeMillis() - start;
        }

        public String format() {
            return TimeUtil.format(elapsedMs());
        }
    }

    /** Formats milliseconds as {@code 1h 02m 03s}. */
    public static String format(long ms) {
        long seconds = Math.max(0L, ms / 1000L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        if (hours > 0) {
            return String.format("%dh %02dm %02ds", hours, minutes, secs);
        }
        return String.format("%02dm %02ds", minutes, secs);
    }

    public static String formatClock(long timeMs) {
        long minutes = (timeMs / 1000L / 60L) % 60L;
        long hours = (timeMs / 1000L / 3600L) % 24L;
        return String.format("%02d:%02d", hours, minutes);
    }
}
