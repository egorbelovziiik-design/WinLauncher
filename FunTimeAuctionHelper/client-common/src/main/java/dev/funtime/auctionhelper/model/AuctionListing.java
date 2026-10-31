package dev.funtime.auctionhelper.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One offer that was actually observed on screen (never invented, never guessed).
 *
 * <p>All prices are the <b>total</b> price of the whole stack as displayed by the server;
 * {@link #getUnitPrice()} is derived and can be used for per item comparisons.</p>
 */
public final class AuctionListing {
    private final String itemId;
    private final String itemName;
    private final int quantity;
    private final double totalPrice;
    private final long observedAt;
    private final String serverProfile;
    private final List<String> lore;
    private final int sourceSlot;

    public AuctionListing(String itemId, String itemName, int quantity, double totalPrice,
                          long observedAt, String serverProfile, List<String> lore, int sourceSlot) {
        this.itemId = itemId == null ? "minecraft:unknown" : itemId;
        this.itemName = itemName == null ? "" : itemName;
        this.quantity = Math.max(0, quantity);
        this.totalPrice = totalPrice;
        this.observedAt = observedAt;
        this.serverProfile = serverProfile == null ? "unknown" : serverProfile;
        this.lore = lore == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(lore));
        this.sourceSlot = sourceSlot;
    }

    public String getItemId() {
        return itemId;
    }

    public String getItemName() {
        return itemName;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public double getUnitPrice() {
        if (quantity <= 0 || Double.isNaN(totalPrice) || totalPrice <= 0d) {
            return Double.NaN;
        }
        return totalPrice / quantity;
    }

    public double getStackPrice() {
        return getUnitPrice() * 64d;
    }

    public long getObservedAt() {
        return observedAt;
    }

    public String getServerProfile() {
        return serverProfile;
    }

    public List<String> getLore() {
        return lore;
    }

    public int getSourceSlot() {
        return sourceSlot;
    }

    public boolean isComplete() {
        return quantity > 0 && !Double.isNaN(totalPrice) && totalPrice > 0d
                && itemId != null && !itemId.isEmpty() && !"minecraft:unknown".equals(itemId);
    }

    public boolean sameOfferAs(AuctionListing other) {
        if (other == null) {
            return false;
        }
        return itemId.equals(other.itemId)
                && quantity == other.quantity
                && Math.abs(totalPrice - other.totalPrice) < 0.01d;
    }

    public AuctionListing withServerProfile(String profile) {
        return new AuctionListing(itemId, itemName, quantity, totalPrice, observedAt, profile, lore, sourceSlot);
    }

    @Override
    public String toString() {
        return itemId + " x" + quantity + " for " + totalPrice + " (" + serverProfile + ")";
    }
}
