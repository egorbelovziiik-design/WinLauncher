package dev.axsesclient.common.notifications;

import dev.axsesclient.common.animation.AnimatedFloat;
import dev.axsesclient.common.animation.Easing;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public final class NotificationManager {
    public enum Severity {
        INFO,
        SUCCESS,
        WARNING,
        ERROR
    }

    public static final class NotificationEntry {
        private final long id;
        private final String title;
        private final String message;
        private final Severity severity;
        private final long createdAtMs;
        private final long durationMs;
        private final AnimatedFloat visibility = new AnimatedFloat(0.0f, 0.22f, Easing.EASE_OUT_CUBIC);

        public NotificationEntry(long id, String title, String message, Severity severity, long createdAtMs, long durationMs) {
            this.id = id;
            this.title = title == null ? "AXSESCLIENT" : title;
            this.message = message == null ? "" : message;
            this.severity = severity == null ? Severity.INFO : severity;
            this.createdAtMs = createdAtMs;
            this.durationMs = Math.max(800L, durationMs);
            this.visibility.setTarget(1.0f);
        }

        public long getId() { return id; }
        public String getTitle() { return title; }
        public String getMessage() { return message; }
        public Severity getSeverity() { return severity; }
        public long getCreatedAtMs() { return createdAtMs; }
        public long getDurationMs() { return durationMs; }

        public float getRemainingFraction(long nowMs) {
            long elapsed = Math.max(0L, nowMs - createdAtMs);
            return Math.max(0.0f, Math.min(1.0f, 1.0f - (float) elapsed / (float) durationMs));
        }

        public float updateVisibility(long nowMs, float deltaSeconds, float speed, boolean reducedMotion) {
            if (nowMs - createdAtMs >= durationMs - 220L) {
                visibility.setTarget(0.0f);
            }
            return visibility.update(deltaSeconds, speed, reducedMotion);
        }

        public boolean isExpired(long nowMs) {
            return (nowMs - createdAtMs) > (durationMs + 250L);
        }
    }

    private static final int MAX_HISTORY = 200;
    private static final int MAX_ACTIVE = 6;

    private long nextId = 1L;
    private boolean notificationsEnabled = true;
    private boolean moduleToggleAlerts = true;
    private long defaultDurationMs = 2800L;

    private final List<NotificationEntry> active = new ArrayList<NotificationEntry>();
    private final List<NotificationEntry> history = new ArrayList<NotificationEntry>();

    public synchronized NotificationEntry push(String title, String message, Severity severity) {
        return push(title, message, severity, defaultDurationMs);
    }

    public synchronized NotificationEntry push(String title, String message, Severity severity, long durationMs) {
        long now = System.currentTimeMillis();
        NotificationEntry entry = new NotificationEntry(nextId++, title, message, severity, now, durationMs);
        history.add(0, entry);
        while (history.size() > MAX_HISTORY) {
            history.remove(history.size() - 1);
        }
        if (notificationsEnabled) {
            active.add(entry);
            while (active.size() > MAX_ACTIVE) {
                active.remove(0);
            }
        }
        return entry;
    }

    public synchronized List<NotificationEntry> updateAndGetActive(float deltaSeconds, float animSpeed, boolean reducedMotion) {
        long now = System.currentTimeMillis();
        Iterator<NotificationEntry> it = active.iterator();
        while (it.hasNext()) {
            NotificationEntry e = it.next();
            e.updateVisibility(now, deltaSeconds, animSpeed, reducedMotion);
            if (e.isExpired(now)) {
                it.remove();
            }
        }
        return new ArrayList<NotificationEntry>(active);
    }

    public synchronized List<NotificationEntry> getHistory() {
        return Collections.unmodifiableList(new ArrayList<NotificationEntry>(history));
    }

    public synchronized void clearHistory() {
        history.clear();
    }

    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    public void setNotificationsEnabled(boolean enabled) { this.notificationsEnabled = enabled; }
    public boolean isModuleToggleAlerts() { return moduleToggleAlerts; }
    public void setModuleToggleAlerts(boolean alerts) { this.moduleToggleAlerts = alerts; }
    public long getDefaultDurationMs() { return defaultDurationMs; }
    public void setDefaultDurationMs(long durationMs) { this.defaultDurationMs = Math.max(800L, Math.min(10000L, durationMs)); }
}
