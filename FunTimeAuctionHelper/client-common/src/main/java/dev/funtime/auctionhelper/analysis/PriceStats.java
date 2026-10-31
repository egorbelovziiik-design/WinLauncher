package dev.funtime.auctionhelper.analysis;

/** Immutable statistics over a set of observed unit prices. */
public final class PriceStats {
    public static final PriceStats EMPTY = new PriceStats(0, Double.NaN, Double.NaN, Double.NaN,
            Double.NaN, Double.NaN, Double.NaN, 0d);

    private final int sampleCount;
    private final double average;
    private final double median;
    private final double minimum;
    private final double maximum;
    private final double p25;
    private final double p75;
    private final double averageQuantity;

    public PriceStats(int sampleCount, double average, double median, double minimum, double maximum,
                      double p25, double p75, double averageQuantity) {
        this.sampleCount = sampleCount;
        this.average = average;
        this.median = median;
        this.minimum = minimum;
        this.maximum = maximum;
        this.p25 = p25;
        this.p75 = p75;
        this.averageQuantity = averageQuantity;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    public double getAverage() {
        return average;
    }

    public double getMedian() {
        return median;
    }

    public double getMinimum() {
        return minimum;
    }

    public double getMaximum() {
        return maximum;
    }

    public double getP25() {
        return p25;
    }

    public double getP75() {
        return p75;
    }

    public double getAverageQuantity() {
        return averageQuantity;
    }

    public double getSpread() {
        if (Double.isNaN(minimum) || Double.isNaN(maximum)) {
            return Double.NaN;
        }
        return maximum - minimum;
    }

    public boolean isEmpty() {
        return sampleCount == 0;
    }

    /** 0..1 heuristic confidence based on how many samples back the number. */
    public double getConfidence(int minimumSampleCount) {
        if (sampleCount <= 0) {
            return 0d;
        }
        double target = Math.max(1, minimumSampleCount);
        return Math.min(1d, sampleCount / (target * 2d));
    }

    public boolean isReliable(int minimumSampleCount) {
        return sampleCount >= Math.max(1, minimumSampleCount);
    }
}
