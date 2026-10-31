package dev.funtime.auctionhelper.analysis;

/** Result of a profitability estimate for one offer. */
public final class FlipAnalysis {
    private final double buyUnitPrice;
    private final double referencePrice;
    private final double estimatedFees;
    private final double netProfitPerItem;
    private final double netProfitTotal;
    private final double marginRatio;
    private final int sampleCount;
    private final boolean lowConfidence;
    private final String note;
    private final boolean insufficientData;

    public FlipAnalysis(double buyUnitPrice, double referencePrice, double estimatedFees,
                        double netProfitPerItem, double netProfitTotal, double marginRatio,
                        int sampleCount, boolean lowConfidence, String note, boolean insufficientData) {
        this.buyUnitPrice = buyUnitPrice;
        this.referencePrice = referencePrice;
        this.estimatedFees = estimatedFees;
        this.netProfitPerItem = netProfitPerItem;
        this.netProfitTotal = netProfitTotal;
        this.marginRatio = marginRatio;
        this.sampleCount = sampleCount;
        this.lowConfidence = lowConfidence;
        this.note = note;
        this.insufficientData = insufficientData;
    }

    public static FlipAnalysis insufficient(String note) {
        return new FlipAnalysis(Double.NaN, Double.NaN, 0d, Double.NaN, Double.NaN, Double.NaN, 0,
                true, note, true);
    }

    public double getBuyUnitPrice() {
        return buyUnitPrice;
    }

    public double getReferencePrice() {
        return referencePrice;
    }

    public double getEstimatedFees() {
        return estimatedFees;
    }

    public double getNetProfitPerItem() {
        return netProfitPerItem;
    }

    public double getNetProfitTotal() {
        return netProfitTotal;
    }

    public double getMarginRatio() {
        return marginRatio;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    public boolean isLowConfidence() {
        return lowConfidence;
    }

    public String getNote() {
        return note;
    }

    public boolean isInsufficientData() {
        return insufficientData;
    }

    public boolean isProfitable() {
        return !insufficientData && !Double.isNaN(netProfitPerItem) && netProfitPerItem > 0d;
    }
}
