package nexus.core.notification;

/** A single toast shown by the notification engine. */
public final class Notification {

    public enum Type {
        INFO("info"),
        SUCCESS("success"),
        WARNING("warning"),
        ERROR("error"),
        MODULE_ENABLED("enabled"),
        MODULE_DISABLED("disabled"),
        CONFIG_SAVED("saved"),
        CONFIG_LOADED("loaded"),
        CLIENT_UPDATED("updated"),
        MINECRAFT_CONNECTED("connected");

        private final String id;

        Type(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    public final Type type;
    public final String title;
    public final String message;
    public final long createdAt;
    public final long durationMs;
    public final boolean playSound;
    private long startedAt = -1L;

    public Notification(Type type, String title, String message, long durationMs, boolean playSound) {
        this.type = type;
        this.title = title;
        this.message = message;
        this.durationMs = durationMs;
        this.playSound = playSound;
        this.createdAt = System.currentTimeMillis();
    }

    /** 0..1 life progress (1 = just created, 0 = expired). */
    public double remaining(long now) {
        if (startedAt < 0L) {
            return 1.0;
        }
        double progress = (now - startedAt) / (double) Math.max(1L, durationMs);
        return Math.max(0.0, 1.0 - progress);
    }

    public void start(long now) {
        if (startedAt < 0L) {
            startedAt = now;
        }
    }

    public boolean isExpired(long now) {
        return startedAt >= 0L && now - startedAt > durationMs;
    }

    /** 0..1 slide-in/out animation factor. */
    public double animation(long now) {
        long age = now - createdAt;
        if (age < 260L) {
            return age / 260.0;
        }
        double remaining = remaining(now);
        return remaining < 0.15 ? Math.max(0.0, remaining / 0.15) : 1.0;
    }
}
