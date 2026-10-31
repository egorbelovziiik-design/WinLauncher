package dev.funtime.auctionhelper.model;

/** User configuration of one of the five built-in trade strategies. */
public final class StrategyConfig {
    public enum Type {
        LOW_PRICE_ALERT,
        PRICE_COMPARISON,
        MANUAL_FLIP_ANALYSIS,
        PRICE_HISTORY_ANALYSIS,
        INVENTORY_SELLING_PLANNER
    }

    public String id = "";
    public String type = Type.LOW_PRICE_ALERT.name();
    public String displayName = "Strategy";
    public boolean enabled = false;
    public double threshold = 0d;
    public double secondaryThreshold = 0d;
    public String itemFilter = "";
    public int minimumSamples = 3;
    public boolean notifyOnly = true;

    public StrategyConfig() {
    }

    public StrategyConfig(String id, Type type, String displayName) {
        this.id = id;
        this.type = type.name();
        this.displayName = displayName;
    }

    public Type getType() {
        try {
            return Type.valueOf(type);
        } catch (IllegalArgumentException | NullPointerException error) {
            return Type.LOW_PRICE_ALERT;
        }
    }

    public StrategyConfig copy() {
        StrategyConfig copy = new StrategyConfig(id, getType(), displayName);
        copy.enabled = enabled;
        copy.threshold = threshold;
        copy.secondaryThreshold = secondaryThreshold;
        copy.itemFilter = itemFilter;
        copy.minimumSamples = minimumSamples;
        copy.notifyOnly = notifyOnly;
        return copy;
    }
}
