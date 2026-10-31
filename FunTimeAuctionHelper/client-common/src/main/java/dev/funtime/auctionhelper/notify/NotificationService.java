package dev.funtime.auctionhelper.notify;

import dev.funtime.auctionhelper.config.HelperConfig;
import dev.funtime.auctionhelper.model.NotificationEntry;
import dev.funtime.auctionhelper.util.Colors;
import java.util.ArrayList;
import java.util.List;

/**
 * Notification centre + toast queue.
 *
 * <p>The service only collects messages; the Minecraft layer decides where to draw them
 * (overlay inside the mod GUI and, if enabled, mirrored to chat).</p>
 */
public final class NotificationService {
    public interface Sink {
        void onNotification(NotificationEntry entry, boolean mirrorToChat);
    }

    private static final int HISTORY_CAP = 200;

    private final List<NotificationEntry> history = new ArrayList<NotificationEntry>();
    private final List<NotificationEntry> active = new ArrayList<NotificationEntry>();
    private final List<Long> expiry = new ArrayList<Long>();
    private HelperConfig config;
    private Sink sink;
    private int unread = 0;

    public NotificationService(HelperConfig config) {
        this.config = config;
    }

    public void setConfig(HelperConfig config) {
        this.config = config;
    }

    public void setSink(Sink sink) {
        this.sink = sink;
    }

    public void info(String message, long now) {
        push(message, NotificationEntry.Kind.INFO, now);
    }

    public void success(String message, long now) {
        push(message, NotificationEntry.Kind.SUCCESS, now);
    }

    public void warn(String message, long now) {
        push(message, NotificationEntry.Kind.WARNING, now);
    }

    public void error(String message, long now) {
        push(message, NotificationEntry.Kind.ERROR, now);
    }

    public void push(String message, NotificationEntry.Kind kind, long now) {
        if (message == null || message.isEmpty()) {
            return;
        }
        NotificationEntry entry = new NotificationEntry(message, kind, now);
        history.add(entry);
        while (history.size() > HISTORY_CAP) {
            history.remove(0);
        }
        unread++;
        HelperConfig.NotificationSettings settings = config == null ? null : config.notifications;
        if (settings != null && settings.enabled) {
            active.add(entry);
            expiry.add(now + Math.max(1000, settings.durationMs));
            while (active.size() > Math.max(1, settings.maxVisible)) {
                active.remove(0);
                expiry.remove(0);
            }
        }
        if (sink != null) {
            sink.onNotification(entry, settings == null || settings.chatMirror);
        }
    }

    /** Removes expired toasts. Called once per frame; cheap and allocation free when idle. */
    public void tick(long now) {
        for (int i = active.size() - 1; i >= 0; i--) {
            if (expiry.get(i) <= now) {
                active.remove(i);
                expiry.remove(i);
            }
        }
    }

    public List<NotificationEntry> active() {
        return new ArrayList<NotificationEntry>(active);
    }

    public List<NotificationEntry> history() {
        return new ArrayList<NotificationEntry>(history);
    }

    public int unreadCount() {
        return unread;
    }

    public void markAllRead() {
        unread = 0;
        for (int i = 0; i < history.size(); i++) {
            history.get(i).markRead();
        }
    }

    public void clearHistory() {
        history.clear();
        active.clear();
        expiry.clear();
        unread = 0;
    }

    public int colorFor(NotificationEntry.Kind kind) {
        HelperConfig.NotificationSettings settings = config == null ? null : config.notifications;
        if (settings == null) {
            return Colors.WHITE;
        }
        switch (kind) {
            case SUCCESS:
                return settings.successColor;
            case WARNING:
                return settings.warningColor;
            case ERROR:
                return settings.errorColor;
            case INFO:
            default:
                return settings.infoColor;
        }
    }
}
