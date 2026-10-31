package dev.funtime.auctionhelper.analysis;

import dev.funtime.auctionhelper.config.HelperConfig;
import dev.funtime.auctionhelper.model.AuctionListing;
import dev.funtime.auctionhelper.model.PriceObservation;
import dev.funtime.auctionhelper.model.StrategyConfig;
import dev.funtime.auctionhelper.util.Numbers;
import java.util.ArrayList;
import java.util.List;

/**
 * The five built-in trade strategies.
 *
 * <p>Every strategy only produces {@link StrategySignal}s text: none of them buys, sells or
 * clicks anything. Turning a signal into an order always goes through
 * {@link dev.funtime.auctionhelper.engine.TradeEngine} which enforces the automation rules.</p>
 */
public final class StrategyEngine {
    private final PriceAnalyzer analyzer = new PriceAnalyzer();

    public List<StrategySignal> evaluate(List<AuctionListing> listings, HelperConfig config,
                                         PriceHistoryStore history, long now) {
        List<StrategySignal> signals = new ArrayList<StrategySignal>();
        if (config == null) {
            return signals;
        }
        for (int i = 0; i < config.strategies.size(); i++) {
            StrategyConfig strategy = config.strategies.get(i);
            if (strategy == null || !strategy.enabled) {
                continue;
            }
            switch (strategy.getType()) {
                case LOW_PRICE_ALERT:
                    lowPriceAlert(strategy, listings, signals);
                    break;
                case PRICE_COMPARISON:
                    priceComparison(strategy, listings, config, history, now, signals);
                    break;
                case MANUAL_FLIP_ANALYSIS:
                    flipAnalysis(strategy, listings, config, history, now, signals);
                    break;
                case PRICE_HISTORY_ANALYSIS:
                    priceHistory(strategy, listings, config, history, now, signals);
                    break;
                case INVENTORY_SELLING_PLANNER:
                    // Produced by the Minecraft layer with the real inventory contents.
                    break;
                default:
                    break;
            }
        }
        return signals;
    }

    private void lowPriceAlert(StrategyConfig strategy, List<AuctionListing> listings,
                               List<StrategySignal> signals) {
        if (listings == null) {
            return;
        }
        for (int i = 0; i < listings.size(); i++) {
            AuctionListing listing = listings.get(i);
            if (!appliesToItem(strategy, listing) || !listing.isComplete()) {
                continue;
            }
            if (strategy.threshold > 0d && listing.getTotalPrice() <= strategy.threshold) {
                signals.add(new StrategySignal(strategy.id, "Price below your threshold",
                        listing.getItemName() + " x" + listing.getQuantity() + " for "
                                + Numbers.formatAmount(listing.getTotalPrice())
                                + " (limit " + Numbers.formatAmount(strategy.threshold) + ")",
                        listing, StrategySignal.Kind.OPPORTUNITY));
            }
        }
    }

    private void priceComparison(StrategyConfig strategy, List<AuctionListing> listings, HelperConfig config,
                                 PriceHistoryStore history, long now, List<StrategySignal> signals) {
        if (listings == null || listings.isEmpty()) {
            return;
        }
        List<AuctionListing> relevant = new ArrayList<AuctionListing>();
        for (int i = 0; i < listings.size(); i++) {
            if (appliesToItem(strategy, listings.get(i)) && listings.get(i).isComplete()) {
                relevant.add(listings.get(i));
            }
        }
        for (int i = 0; i < relevant.size(); i++) {
            AuctionListing candidate = relevant.get(i);
            double unit = candidate.getUnitPrice();
            if (Double.isNaN(unit)) {
                continue;
            }
            int cheaper = 0;
            for (int j = 0; j < relevant.size(); j++) {
                if (j != i && relevant.get(j).getUnitPrice() < unit) {
                    cheaper++;
                }
            }
            if (cheaper == 0 && relevant.size() > 1) {
                double secondBest = Double.MAX_VALUE;
                for (int j = 0; j < relevant.size(); j++) {
                    if (j != i) {
                        secondBest = Math.min(secondBest, relevant.get(j).getUnitPrice());
                    }
                }
                double diff = secondBest - unit;
                double ratio = secondBest <= 0d ? 0d : diff / secondBest;
                if (ratio >= strategy.threshold) {
                    signals.add(new StrategySignal(strategy.id, "Cheapest offer on screen",
                            candidate.getItemName() + " at " + Numbers.formatAmount(unit)
                                    + "/item is " + Numbers.formatPercent(ratio) + " under the next offer",
                            candidate, StrategySignal.Kind.OPPORTUNITY));
                }
            }
        }
    }

    private void flipAnalysis(StrategyConfig strategy, List<AuctionListing> listings, HelperConfig config,
                              PriceHistoryStore history, long now, List<StrategySignal> signals) {
        if (listings == null) {
            return;
        }
        int window = config.analysis.observationWindowMinutes;
        for (int i = 0; i < listings.size(); i++) {
            AuctionListing listing = listings.get(i);
            if (!appliesToItem(strategy, listing) || !listing.isComplete()) {
                continue;
            }
            List<PriceObservation> observations = history == null
                    ? new ArrayList<PriceObservation>()
                    : history.windowFor(listing.getItemId(), window, now);
            PriceStats stats = analyzer.compute(observations);
            if (stats.isEmpty()) {
                continue;
            }
            FlipAnalysis analysis = analyzer.analyze(listing, stats, stats.getMedian(), config.fees,
                    config.analysis.minimumSampleCount);
            if (analysis.isInsufficientData()) {
                continue;
            }
            boolean bigEnough = strategy.threshold <= 0d || analysis.getNetProfitTotal() >= strategy.threshold;
            boolean marginEnough = strategy.secondaryThreshold <= 0d
                    || analysis.getMarginRatio() >= strategy.secondaryThreshold;
            if (bigEnough && marginEnough) {
                signals.add(new StrategySignal(strategy.id, "Possible flip",
                        listing.getItemName() + " x" + listing.getQuantity() + ": estimated net "
                                + Numbers.formatSigned(analysis.getNetProfitTotal()) + " ("
                                + Numbers.formatPercent(analysis.getMarginRatio()) + ") - estimate only",
                        listing, StrategySignal.Kind.OPPORTUNITY));
            }
        }
    }

    private void priceHistory(StrategyConfig strategy, List<AuctionListing> listings, HelperConfig config,
                              PriceHistoryStore history, long now, List<StrategySignal> signals) {
        if (listings == null || history == null) {
            return;
        }
        for (int i = 0; i < listings.size(); i++) {
            AuctionListing listing = listings.get(i);
            if (!appliesToItem(strategy, listing) || !listing.isComplete()) {
                continue;
            }
            List<PriceObservation> observations = history.windowFor(listing.getItemId(),
                    config.analysis.observationWindowMinutes, now);
            PriceStats stats = analyzer.compute(observations);
            if (!stats.isReliable(strategy.minimumSamples)) {
                signals.add(new StrategySignal(strategy.id, "Not enough price history",
                        listing.getItemName() + ": " + stats.getSampleCount() + " of "
                                + strategy.minimumSamples + " samples collected",
                        listing, StrategySignal.Kind.WARNING));
                continue;
            }
            double unit = listing.getUnitPrice();
            double factor = strategy.threshold <= 0d ? 0.75d : strategy.threshold;
            if (!Double.isNaN(unit) && stats.getMedian() > 0d && unit <= stats.getMedian() * factor) {
                signals.add(new StrategySignal(strategy.id, "Below observed median",
                        listing.getItemName() + " at " + Numbers.formatAmount(unit)
                                + "/item vs median " + Numbers.formatAmount(stats.getMedian())
                                + " over " + stats.getSampleCount() + " samples",
                        listing, StrategySignal.Kind.OPPORTUNITY));
            }
        }
    }

    static boolean appliesToItem(StrategyConfig strategy, AuctionListing listing) {
        if (listing == null) {
            return false;
        }
        if (strategy.itemFilter == null || strategy.itemFilter.isEmpty()) {
            return true;
        }
        String filter = strategy.itemFilter.toLowerCase(java.util.Locale.ROOT);
        return listing.getItemId().toLowerCase(java.util.Locale.ROOT).contains(filter)
                || listing.getItemName().toLowerCase(java.util.Locale.ROOT).contains(filter);
    }
}
