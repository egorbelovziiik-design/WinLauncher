package dev.funtime.auctionhelper.engine;

import dev.funtime.auctionhelper.analysis.FilterEngine;
import dev.funtime.auctionhelper.analysis.TradeHistoryStore;
import dev.funtime.auctionhelper.config.HelperConfig;
import dev.funtime.auctionhelper.model.AuctionListing;
import dev.funtime.auctionhelper.model.TradeRecord;
import dev.funtime.auctionhelper.notify.NotificationService;
import dev.funtime.auctionhelper.util.Log;
import dev.funtime.auctionhelper.util.Numbers;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The only place that may create trade operations.
 *
 * <p>Guard pipeline for every single request (manual or automated):</p>
 * <ol>
 *   <li>mode must allow the action (ANALYSIS_ONLY never trades),</li>
 *   <li>automated requests need master ON + the matching switch ON + an available integration,</li>
 *   <li>the offer must be complete and allowed by whitelist/blacklist,</li>
 *   <li>price, margin, purchase-count, daily-spend and balance limits,</li>
 *   <li>the price is re-read before execution - a changed price aborts with STALE,</li>
 *   <li>a network error leaves the operation UNVERIFIED: a blind retry is refused.</li>
 * </ol>
 */
public final class TradeEngine implements AutomationState.PendingWork {
    /** Provider of the player's balance. Unknown when the server does not expose it. */
    public interface BalanceProvider {
        boolean isKnown();

        double getBalance();
    }

    public static final class PendingTask {
        public final TradeRequest request;
        public final long queuedAt;
        public final boolean automated;

        PendingTask(TradeRequest request, long queuedAt, boolean automated) {
            this.request = request;
            this.queuedAt = queuedAt;
            this.automated = automated;
        }
    }

    private final HelperConfig config;
    private final AutomationState automation;
    private final TradeHistoryStore history;
    private final NotificationService notifications;
    private final FilterEngine filters = new FilterEngine();
    private final List<PendingTask> pending = new ArrayList<PendingTask>();
    private BalanceProvider balanceProvider;
    private int sessionPurchases;
    private int sessionSales;
    private long sessionStartedAt;
    private String lastRefusal = "";

    public TradeEngine(HelperConfig config, AutomationState automation, TradeHistoryStore history,
                       NotificationService notifications, long now) {
        this.config = config;
        this.automation = automation;
        this.history = history;
        this.notifications = notifications;
        this.sessionStartedAt = now;
        automation.setPendingWork(this);
    }

    public void setBalanceProvider(BalanceProvider provider) {
        this.balanceProvider = provider;
    }

    public void setConfig(HelperConfig config) {
        // config is final for safety; the engine always reads the live object it was built with.
    }

    public List<PendingTask> pendingTasks() {
        return new ArrayList<PendingTask>(pending);
    }

    public String getLastRefusal() {
        return lastRefusal;
    }

    public int getSessionPurchases() {
        return sessionPurchases;
    }

    public int getSessionSales() {
        return sessionSales;
    }

    public long getSessionStartedAt() {
        return sessionStartedAt;
    }

    public void startNewSession(long now) {
        sessionPurchases = 0;
        sessionSales = 0;
        sessionStartedAt = now;
    }

    @Override
    public int getPendingCount() {
        return pending.size();
    }

    /** Emergency stop / switch off: every waiting operation is cancelled (never retried). */
    @Override
    public int cancelAll(String reason) {
        int cancelled = 0;
        List<PendingTask> copy = new ArrayList<PendingTask>(pending);
        pending.clear();
        for (int i = 0; i < copy.size(); i++) {
            PendingTask task = copy.get(i);
            history.updateResult(task.request.getRequestId(), TradeRecord.Result.CANCELLED,
                    "Cancelled: " + reason, 0L);
            cancelled++;
        }
        return cancelled;
    }

    /**
     * Queues a manual buy (the player pressed "Buy" in the GUI). This still requires a valid
     * mode, a complete offer and the configured limits, but not the automation switches.
     */
    public TradeResult requestManualBuy(AuctionListing listing, long now) {
        return queue(listing, TradeRecord.Type.BUY, false, now);
    }

    public TradeResult requestManualSell(AuctionListing listing, long now) {
        return queue(listing, TradeRecord.Type.SELL, false, now);
    }

    /** Automated path, only reachable when AUTO BUY is on and an integration exists. */
    public TradeResult requestAutomatedBuy(AuctionListing listing, long now) {
        return queue(listing, TradeRecord.Type.BUY, true, now);
    }

    public TradeResult requestAutomatedSell(AuctionListing listing, long now) {
        return queue(listing, TradeRecord.Type.SELL, true, now);
    }

    private TradeResult queue(AuctionListing listing, TradeRecord.Type type, boolean automated, long now) {
        String refusal = validate(listing, type, automated, now);
        if (refusal != null) {
            lastRefusal = refusal;
            notifications.warn(refusal, now);
            return new TradeResult("none", TradeRecord.Result.REJECTED, refusal, 0d, false);
        }
        if (hasUnverifiedOperation(type, listing)) {
            lastRefusal = "A previous " + type.name() + " of this item is unverified - check the trade "
                    + "history before trying again";
            notifications.error(lastRefusal, now);
            return new TradeResult("none", TradeRecord.Result.UNVERIFIED, lastRefusal, 0d, false);
        }
        String id = type.name().toLowerCase(java.util.Locale.ROOT) + "-" + UUID.randomUUID().toString().substring(0, 8);
        TradeRequest request = new TradeRequest(id, type, listing, automation.getMode().name(), now);
        history.add(new TradeRecord(id, listing.getItemId(), listing.getItemName(), listing.getQuantity(),
                listing.getTotalPrice(), type, now, TradeRecord.Result.PENDING, automation.getMode().name(),
                "queued"));
        pending.add(new PendingTask(request, now, automated));
        notifications.info((automated ? "Queued automated " : "Queued ") + type.name().toLowerCase(
                java.util.Locale.ROOT) + " of " + listing.getItemName() + " for "
                + Numbers.formatAmount(listing.getTotalPrice()) + " - waiting for confirmation", now);
        return new TradeResult(id, TradeRecord.Result.PENDING, "Queued", listing.getTotalPrice(), false);
    }

    /**
     * Executes a queued operation after the player confirmed it (MANUAL_ASSISTANT) or after the
     * authorized integration accepted it.
     */
    public TradeResult execute(String requestId, boolean playerConfirmed, long now) {
        PendingTask task = find(requestId);
        if (task == null) {
            return new TradeResult(requestId, TradeRecord.Result.REJECTED, "Unknown operation", 0d, false);
        }
        boolean automated = task.automated;
        if (automated && !automation.isAutomationActive()) {
            pending.remove(task);
            history.updateResult(requestId, TradeRecord.Result.CANCELLED,
                    "Automation is off", now);
            notifications.warn("Automation is off - operation cancelled", now);
            return new TradeResult(requestId, TradeRecord.Result.CANCELLED, "Automation is off",
                    task.request.getPrice(), false);
        }
        if (!automated && !playerConfirmed) {
            return new TradeResult(requestId, TradeRecord.Result.PENDING, "Waiting for confirmation",
                    task.request.getPrice(), false);
        }
        AuctionIntegration integration = automation.getIntegration();
        if (!integration.isAvailable()) {
            pending.remove(task);
            history.updateResult(requestId, TradeRecord.Result.REJECTED,
                    "No authorized automation API - confirming by hand only", now);
            notifications.warn("This server has no authorized automation API: place the order yourself "
                    + "and confirm it in TRADE HISTORY", now);
            return new TradeResult(requestId, TradeRecord.Result.REJECTED,
                    "No authorized automation API", task.request.getPrice(), false);
        }
        AuctionListing listing = task.request.getListing();
        if (config.buy.recheckPriceBeforeBuy || task.request.getType() == TradeRecord.Type.SELL) {
            double current = integration.currentPriceOf(listing);
            if (!Double.isNaN(current) && Math.abs(current - listing.getTotalPrice()) > 0.01d) {
                pending.remove(task);
                history.updateResult(requestId, TradeRecord.Result.STALE,
                        "Price changed from " + listing.getTotalPrice() + " to " + current, now);
                notifications.warn("Price changed (" + Numbers.formatAmount(listing.getTotalPrice())
                        + " -> " + Numbers.formatAmount(current) + "): operation cancelled, please review", now);
                return new TradeResult(requestId, TradeRecord.Result.STALE, "Price changed",
                        current, false);
            }
        }
        TradeResult result;
        try {
            result = task.request.getType() == TradeRecord.Type.BUY
                    ? integration.buy(task.request)
                    : integration.sell(task.request);
        } catch (RuntimeException error) {
            result = TradeResult.unverified(task.request, "Network/protocol error: " + error.getMessage());
            Log.error("Trade execution failed", error);
        }
        pending.remove(task);
        history.updateResult(requestId, result.getResult(), result.getMessage(), now);
        if (result.getResult() == TradeRecord.Result.CONFIRMED) {
            if (task.request.getType() == TradeRecord.Type.BUY) {
                sessionPurchases++;
            } else {
                sessionSales++;
            }
            notifications.success("Confirmed " + task.request.getType().name().toLowerCase(java.util.Locale.ROOT)
                    + " of " + listing.getItemName() + " for " + Numbers.formatAmount(result.getExecutedPrice()), now);
        } else if (result.getResult() == TradeRecord.Result.UNVERIFIED) {
            notifications.error("Operation unverified: " + result.getMessage()
                    + ". Check the result before retrying - the mod will not retry on its own", now);
        } else {
            notifications.warn("Operation rejected: " + result.getMessage(), now);
        }
        return result;
    }

    /** The player rejects a queued operation. */
    public boolean reject(String requestId, String reason, long now) {
        PendingTask task = find(requestId);
        if (task == null) {
            return false;
        }
        pending.remove(task);
        history.updateResult(requestId, TradeRecord.Result.REJECTED,
                reason == null ? "Rejected by player" : reason, now);
        return true;
    }

    private PendingTask find(String requestId) {
        for (int i = 0; i < pending.size(); i++) {
            if (pending.get(i).request.getRequestId().equals(requestId)) {
                return pending.get(i);
            }
        }
        return null;
    }

    private boolean hasUnverifiedOperation(TradeRecord.Type type, AuctionListing listing) {
        List<TradeRecord> unverified = history.byResult(TradeRecord.Result.UNVERIFIED);
        for (int i = 0; i < unverified.size(); i++) {
            TradeRecord record = unverified.get(i);
            if (record.getType() == type && record.getItemId().equals(listing.getItemId())) {
                return true;
            }
        }
        return false;
    }

    /** @return {@code null} when the operation may be queued, otherwise the refusal reason */
    public String validate(AuctionListing listing, TradeRecord.Type type, boolean automated, long now) {
        if (listing == null || !listing.isComplete()) {
            return "Offer data is incomplete (item id, amount or price missing) - nothing is executed";
        }
        AutomationState.Mode mode = automation.getMode();
        if (mode == AutomationState.Mode.ANALYSIS_ONLY) {
            return "Mode ANALYSIS ONLY never trades - switch to MANUAL ASSISTANT first";
        }
        if (automated) {
            if (!config.automation.masterEnabled) {
                return "Master automation switch is OFF";
            }
            if (type == TradeRecord.Type.BUY && !automation.isAutoBuySwitchOn()) {
                return "AUTO BUY is OFF";
            }
            if (type == TradeRecord.Type.SELL && !automation.isAutoSellSwitchOn()) {
                return "AUTO SELL is OFF";
            }
            if (!automation.isAutomationAllowed()) {
                return "Automation unavailable: " + automation.getAutomationBlockedReason();
            }
        }
        if (!filters.isGloballyAllowed(listing, config.whitelist, config.blacklist)) {
            return "Item is blocked by your whitelist/blacklist";
        }
        if (type == TradeRecord.Type.BUY) {
            if (containsIgnoreCase(config.buy.blockedItems, listing.getItemId())) {
                return "Item is in BUY > blocked items";
            }
            if (!config.buy.allowedItems.isEmpty()
                    && !containsIgnoreCase(config.buy.allowedItems, listing.getItemId())) {
                return "Item is not in BUY > allowed items";
            }
            if (config.buy.maxPurchasePrice > 0d && listing.getTotalPrice() > config.buy.maxPurchasePrice) {
                return "Price " + Numbers.formatAmount(listing.getTotalPrice()) + " is above the maximum purchase price";
            }
            if (config.buy.maxPurchasesPerSession > 0 && sessionPurchases >= config.buy.maxPurchasesPerSession) {
                return "Maximum purchases for this session reached";
            }
            if (config.buy.dailySpendingLimit > 0d) {
                long dayStart = now - 86_400_000L;
                double spent = history.spentSince(dayStart);
                if (spent + listing.getTotalPrice() > config.buy.dailySpendingLimit) {
                    return "Daily spending limit would be exceeded";
                }
            }
            if (config.buy.minimumBalance > 0d) {
                if (balanceProvider == null || !balanceProvider.isKnown()) {
                    if (automated) {
                        return "Balance is unknown - automated buying needs a verified balance";
                    }
                } else if (balanceProvider.getBalance() - listing.getTotalPrice() < config.buy.minimumBalance) {
                    return "Purchase would drop your balance below the minimum";
                }
            }
        } else {
            if (containsIgnoreCase(config.sell.blockedItems, listing.getItemId())) {
                return "Item is in SELL > blocked items";
            }
            if (!config.sell.allowedItems.isEmpty()
                    && !containsIgnoreCase(config.sell.allowedItems, listing.getItemId())) {
                return "Item is not in SELL > allowed items";
            }
            if (listing.getTotalPrice() < config.sell.minSellingPrice) {
                return "Price is below the minimum selling price";
            }
            if (config.sell.maxSellingPrice > 0d && listing.getTotalPrice() > config.sell.maxSellingPrice) {
                return "Price is above the maximum selling price";
            }
            if (config.sell.maxActiveListings > 0 && listing.getQuantity() > 0
                    && pending.size() >= config.sell.maxActiveListings) {
                return "Maximum active listings reached";
            }
        }
        return null;
    }

    private static boolean containsIgnoreCase(List<String> list, String value) {
        if (list == null || value == null) {
            return false;
        }
        for (int i = 0; i < list.size(); i++) {
            if (value.equalsIgnoreCase(list.get(i))) {
                return true;
            }
        }
        return false;
    }
}
