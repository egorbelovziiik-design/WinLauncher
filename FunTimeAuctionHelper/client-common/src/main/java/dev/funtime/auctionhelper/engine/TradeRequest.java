package dev.funtime.auctionhelper.engine;

import dev.funtime.auctionhelper.model.AuctionListing;
import dev.funtime.auctionhelper.model.TradeRecord;

/** A single, fully validated trade intent. */
public final class TradeRequest {
    private final String requestId;
    private final TradeRecord.Type type;
    private final AuctionListing listing;
    private final String automationMode;
    private final long createdAt;

    public TradeRequest(String requestId, TradeRecord.Type type, AuctionListing listing,
                        String automationMode, long createdAt) {
        this.requestId = requestId;
        this.type = type;
        this.listing = listing;
        this.automationMode = automationMode;
        this.createdAt = createdAt;
    }

    public String getRequestId() {
        return requestId;
    }

    public TradeRecord.Type getType() {
        return type;
    }

    public AuctionListing getListing() {
        return listing;
    }

    public String getAutomationMode() {
        return automationMode;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public double getPrice() {
        return listing == null ? Double.NaN : listing.getTotalPrice();
    }
}
