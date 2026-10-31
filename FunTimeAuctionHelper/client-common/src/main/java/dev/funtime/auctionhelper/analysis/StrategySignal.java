package dev.funtime.auctionhelper.analysis;

import dev.funtime.auctionhelper.model.AuctionListing;

/** One actionable suggestion produced by a strategy. Never executes anything by itself. */
public final class StrategySignal {
    public enum Kind {
        INFO,
        OPPORTUNITY,
        WARNING
    }

    private final String strategyId;
    private final String title;
    private final String detail;
    private final AuctionListing listing;
    private final Kind kind;

    public StrategySignal(String strategyId, String title, String detail, AuctionListing listing, Kind kind) {
        this.strategyId = strategyId;
        this.title = title;
        this.detail = detail;
        this.listing = listing;
        this.kind = kind == null ? Kind.INFO : kind;
    }

    public String getStrategyId() {
        return strategyId;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }

    public AuctionListing getListing() {
        return listing;
    }

    public Kind getKind() {
        return kind;
    }
}
