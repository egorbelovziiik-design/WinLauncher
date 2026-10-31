package dev.funtime.auctionhelper.model;

/** A single immutable price point stored in the local price history. */
public final class PriceObservation {
    private final String itemId;
    private final String itemName;
    private final double unitPrice;
    private final double totalPrice;
    private final int quantity;
    private final long timestamp;
    private final String serverProfile;

    public PriceObservation(String itemId, String itemName, double unitPrice, double totalPrice,
                            int quantity, long timestamp, String serverProfile) {
        this.itemId = itemId == null ? "minecraft:unknown" : itemId;
        this.itemName = itemName == null ? "" : itemName;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
        this.quantity = quantity;
        this.timestamp = timestamp;
        this.serverProfile = serverProfile == null ? "unknown" : serverProfile;
    }

    public static PriceObservation of(AuctionListing listing) {
        return new PriceObservation(listing.getItemId(), listing.getItemName(), listing.getUnitPrice(),
                listing.getTotalPrice(), listing.getQuantity(), listing.getObservedAt(),
                listing.getServerProfile());
    }

    public String getItemId() {
        return itemId;
    }

    public String getItemName() {
        return itemName;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getServerProfile() {
        return serverProfile;
    }

    public boolean isValid() {
        return !Double.isNaN(unitPrice) && unitPrice > 0d && quantity > 0;
    }
}
