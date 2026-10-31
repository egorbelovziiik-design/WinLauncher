package dev.funtime.auctionhelper.model;

/** One notification shown in the overlay and stored in the notification centre. */
public final class NotificationEntry {
    public enum Kind {
        INFO,
        SUCCESS,
        WARNING,
        ERROR
    }

    private String message;
    private String kind;
    private long timestamp;
    private boolean read;

    public NotificationEntry() {
    }

    public NotificationEntry(String message, Kind kind, long timestamp) {
        this.message = message;
        this.kind = kind == null ? Kind.INFO.name() : kind.name();
        this.timestamp = timestamp;
        this.read = false;
    }

    public String getMessage() {
        return message;
    }

    public Kind getKind() {
        try {
            return Kind.valueOf(kind);
        } catch (IllegalArgumentException | NullPointerException error) {
            return Kind.INFO;
        }
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isRead() {
        return read;
    }

    public void markRead() {
        this.read = true;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
