package dev.funtime.auctionhelper.engine;

import dev.funtime.auctionhelper.model.TradeRecord;

/** Outcome of an execution attempt. */
public final class TradeResult {
    private final String requestId;
    private final TradeRecord.Result result;
    private final String message;
    private final double executedPrice;
    private final boolean retryAllowed;

    public TradeResult(String requestId, TradeRecord.Result result, String message,
                       double executedPrice, boolean retryAllowed) {
        this.requestId = requestId;
        this.result = result;
        this.message = message;
        this.executedPrice = executedPrice;
        this.retryAllowed = retryAllowed;
    }

    public static TradeResult confirmed(TradeRequest request, double price, String message) {
        return new TradeResult(request.getRequestId(), TradeRecord.Result.CONFIRMED, message, price, false);
    }

    public static TradeResult rejected(TradeRequest request, String message) {
        return new TradeResult(request.getRequestId(), TradeRecord.Result.REJECTED, message,
                request.getPrice(), false);
    }

    /** Network or protocol problem: the outcome is unknown and must be verified before a retry. */
    public static TradeResult unverified(TradeRequest request, String message) {
        return new TradeResult(request.getRequestId(), TradeRecord.Result.UNVERIFIED, message,
                request.getPrice(), false);
    }

    public String getRequestId() {
        return requestId;
    }

    public TradeRecord.Result getResult() {
        return result;
    }

    public String getMessage() {
        return message;
    }

    public double getExecutedPrice() {
        return executedPrice;
    }

    /** A blind retry is never allowed: the previous operation must be verified first. */
    public boolean isRetryAllowed() {
        return retryAllowed;
    }
}
