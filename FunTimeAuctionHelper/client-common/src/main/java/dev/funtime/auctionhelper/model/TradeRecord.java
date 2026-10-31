package dev.funtime.auctionhelper.model;

/**
 * One journal entry of the trade history.
 *
 * <p><b>Only {@link Result#CONFIRMED} records are counted as money spent or earned.</b>
 * A queued or attempted operation is never reported as a completed trade: the mod waits for
 * an explicit, verifiable confirmation (server answer or the player pressing confirm).</p>
 */
public final class TradeRecord {
    public enum Type {
        BUY,
        SELL;

        public static Type parse(String raw) {
            if (raw != null && "SELL".equalsIgnoreCase(raw)) {
                return SELL;
            }
            return BUY;
        }
    }

    public enum Result {
        /** Waiting for the player / server confirmation. Never counted in totals. */
        PENDING,
        /** Confirmed by the server or explicitly confirmed by the player. Counted. */
        CONFIRMED,
        /** Server rejected the operation, or the player rejected it. Not counted. */
        REJECTED,
        /** Price or offer changed between planning and execution; needs a new check. */
        STALE,
        /** Network/protocol error: the outcome is UNKNOWN and must be verified before a retry. */
        UNVERIFIED,
        /** Cancelled by the emergency stop or by the player. Not counted. */
        CANCELLED;

        public boolean isFinal() {
            return this != PENDING;
        }

        public boolean countsTowardsTotals() {
            return this == CONFIRMED;
        }

        public static Result parse(String raw) {
            if (raw == null) {
                return PENDING;
            }
            for (Result result : values()) {
                if (result.name().equalsIgnoreCase(raw)) {
                    return result;
                }
            }
            return PENDING;
        }
    }

    private String operationId;
    private String itemId;
    private String itemName;
    private int quantity;
    private double price;
    private String type;
    private long timestamp;
    private String result;
    private String automationMode;
    private String note;

    public TradeRecord() {
    }

    public TradeRecord(String operationId, String itemId, String itemName, int quantity, double price,
                       Type type, long timestamp, Result result, String automationMode, String note) {
        this.operationId = operationId;
        this.itemId = itemId;
        this.itemName = itemName;
        this.quantity = quantity;
        this.price = price;
        this.type = type == null ? Type.BUY.name() : type.name();
        this.timestamp = timestamp;
        this.result = result == null ? Result.PENDING.name() : result.name();
        this.automationMode = automationMode;
        this.note = note;
    }

    public String getOperationId() {
        return operationId;
    }

    public String getItemId() {
        return itemId;
    }

    public String getItemName() {
        return itemName;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getPrice() {
        return price;
    }

    public Type getType() {
        return Type.parse(type);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Result getResult() {
        return Result.parse(result);
    }

    public void setResult(Result newResult, String newNote) {
        this.result = newResult == null ? Result.PENDING.name() : newResult.name();
        if (newNote != null) {
            this.note = newNote;
        }
    }

    public void setTimestamp(long newTimestamp) {
        this.timestamp = newTimestamp;
    }

    public String getAutomationMode() {
        return automationMode;
    }

    public String getNote() {
        return note;
    }

    public double getTotal() {
        return price;
    }
}
