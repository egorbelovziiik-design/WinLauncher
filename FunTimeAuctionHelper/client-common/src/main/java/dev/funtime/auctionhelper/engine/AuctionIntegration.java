package dev.funtime.auctionhelper.engine;

import dev.funtime.auctionhelper.model.AuctionListing;

/**
 * The only way automated trading may ever happen.
 *
 * <p>A mod cannot legally buy or sell on a server GUI without a documented, server provided
 * interface: everything else would be automation of the client GUI (clicking, packet spam),
 * which this project deliberately does not implement. When a server exposes such an interface
 * (a plugin with a signed API, an official bot API, a web endpoint with a token, ...) it is
 * plugged in here and only then is the AUTHORIZED_AUTOMATION mode selectable.</p>
 */
public interface AuctionIntegration {
    /** Human readable name of the integration, e.g. "FunTime Auction API v1". */
    String getName();

    /** True only when the integration is connected, authenticated and allowed to trade. */
    boolean isAvailable();

    /** Short reason why automation is unavailable (shown in the GUI). */
    String getUnavailableReason();

    /** Executes a confirmed buy order. Implementations must be idempotent per request id. */
    TradeResult buy(TradeRequest request);

    /** Executes a confirmed sell order. Implementations must be idempotent per request id. */
    TradeResult sell(TradeRequest request);

    /** Re-reads the current price of an offer right before execution. */
    double currentPriceOf(AuctionListing listing);

    /**
     * Default integration used when the server provides nothing: it never trades.
     */
    final class None implements AuctionIntegration {
        private final String reason;

        public None() {
            this("No authorized automation API for this server");
        }

        public None(String reason) {
            this.reason = reason;
        }

        @Override
        public String getName() {
            return "Manual only";
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public String getUnavailableReason() {
            return reason;
        }

        @Override
        public TradeResult buy(TradeRequest request) {
            return TradeResult.rejected(request, "Automation is not authorized on this server");
        }

        @Override
        public TradeResult sell(TradeRequest request) {
            return TradeResult.rejected(request, "Automation is not authorized on this server");
        }

        @Override
        public double currentPriceOf(AuctionListing listing) {
            return listing == null ? Double.NaN : listing.getTotalPrice();
        }
    }
}
