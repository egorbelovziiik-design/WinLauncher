package dev.funtime.auctionhelper.analysis;

import dev.funtime.auctionhelper.config.HelperConfig;
import dev.funtime.auctionhelper.model.AuctionListing;
import dev.funtime.auctionhelper.model.PriceObservation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Local price analysis.
 *
 * <p>Nothing here talks to a server and nothing here is called "guaranteed profit":
 * every number is derived from offers the player actually saw and is reported together with
 * its sample count so the GUI can warn about weak data.</p>
 */
public final class PriceAnalyzer {

    public PriceStats compute(List<PriceObservation> observations) {
        if (observations == null || observations.isEmpty()) {
            return PriceStats.EMPTY;
        }
        List<Double> prices = new ArrayList<Double>();
        double quantitySum = 0d;
        int valid = 0;
        for (int i = 0; i < observations.size(); i++) {
            PriceObservation observation = observations.get(i);
            if (observation == null || !observation.isValid()) {
                continue;
            }
            prices.add(observation.getUnitPrice());
            quantitySum += observation.getQuantity();
            valid++;
        }
        if (prices.isEmpty()) {
            return PriceStats.EMPTY;
        }
        Collections.sort(prices);
        double sum = 0d;
        for (int i = 0; i < prices.size(); i++) {
            sum += prices.get(i);
        }
        return new PriceStats(
                valid,
                sum / prices.size(),
                percentile(prices, 0.5),
                prices.get(0),
                prices.get(prices.size() - 1),
                percentile(prices, 0.25),
                percentile(prices, 0.75),
                valid == 0 ? 0d : quantitySum / valid);
    }

    /** Linear interpolation percentile on an already sorted list. */
    public static double percentile(List<Double> sorted, double fraction) {
        if (sorted == null || sorted.isEmpty()) {
            return Double.NaN;
        }
        if (sorted.size() == 1) {
            return sorted.get(0);
        }
        double position = fraction * (sorted.size() - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        if (lower == upper) {
            return sorted.get(lower);
        }
        double weight = position - lower;
        return sorted.get(lower) * (1d - weight) + sorted.get(upper) * weight;
    }

    /**
     * Evaluates a listing against the local market statistics.
     *
     * @param referencePrice the price the player would resell at (usually the median)
     */
    public FlipAnalysis analyze(AuctionListing listing, PriceStats stats, double referencePrice,
                                HelperConfig.SellFees fees, int minimumSampleCount) {
        if (listing == null) {
            return FlipAnalysis.insufficient("No offer selected");
        }
        double unit = listing.getUnitPrice();
        if (Double.isNaN(unit) || unit <= 0d) {
            return FlipAnalysis.insufficient("Offer has no usable price");
        }
        if (stats == null || stats.isEmpty() || Double.isNaN(referencePrice)) {
            return FlipAnalysis.insufficient("No local price history for this item yet");
        }
        boolean weak = !stats.isReliable(minimumSampleCount);
        double grossPerItem = referencePrice - unit;
        double feePerItem = fees == null ? 0d : fees.estimate(referencePrice);
        double netPerItem = grossPerItem - feePerItem;
        double marginRatio = referencePrice <= 0d ? 0d : netPerItem / referencePrice;
        return new FlipAnalysis(
                unit,
                referencePrice,
                feePerItem,
                netPerItem,
                netPerItem * Math.max(1, listing.getQuantity()),
                marginRatio,
                stats.getSampleCount(),
                weak,
                weak ? "Only " + stats.getSampleCount() + " sample(s) - estimate is unreliable"
                        : "Based on " + stats.getSampleCount() + " local observations",
                false);
    }

    /** Explicit buy -> sell flip calculation used by MANUAL FLIP ANALYSIS. */
    public FlipAnalysis analyzeFlip(double buyPrice, double sellPrice, HelperConfig.SellFees fees,
                                    int sampleCount) {
        if (Double.isNaN(buyPrice) || Double.isNaN(sellPrice) || buyPrice <= 0d || sellPrice <= 0d) {
            return FlipAnalysis.insufficient("Buy and sell price are required");
        }
        double fee = fees == null ? 0d : fees.estimate(sellPrice);
        double gross = sellPrice - buyPrice;
        double net = gross - fee;
        double margin = sellPrice <= 0d ? 0d : net / sellPrice;
        return new FlipAnalysis(buyPrice, sellPrice, fee, net, net, margin, sampleCount, false,
                "Estimated from your own buy/sell numbers, not a guarantee", false);
    }
}
