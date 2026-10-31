package dev.funtime.auctionhelper.analysis;

import dev.funtime.auctionhelper.model.AuctionListing;
import dev.funtime.auctionhelper.model.FilterRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Auction filter engine: saved rules + whitelist / blacklist / favourites. */
public final class FilterEngine {

    public boolean matches(FilterRule rule, AuctionListing listing, List<String> favorites) {
        if (rule == null || listing == null || !rule.enabled) {
            return false;
        }
        String name = listing.getItemName() == null ? "" : listing.getItemName().toLowerCase(Locale.ROOT);
        String id = listing.getItemId() == null ? "" : listing.getItemId().toLowerCase(Locale.ROOT);
        if (rule.nameContains != null && !rule.nameContains.isEmpty()
                && !name.contains(rule.nameContains.toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (rule.itemIdContains != null && !rule.itemIdContains.isEmpty()
                && !id.contains(rule.itemIdContains.toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (rule.category != null && !rule.category.isEmpty()
                && !id.contains(rule.category.toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (rule.minPrice > 0d && listing.getTotalPrice() < rule.minPrice) {
            return false;
        }
        if (rule.maxPrice > 0d && listing.getTotalPrice() > rule.maxPrice) {
            return false;
        }
        double unit = listing.getUnitPrice();
        if (!Double.isNaN(unit)) {
            if (rule.minUnitPrice > 0d && unit < rule.minUnitPrice) {
                return false;
            }
            if (rule.maxUnitPrice > 0d && unit > rule.maxUnitPrice) {
                return false;
            }
        }
        if (rule.minStackSize > 0 && listing.getQuantity() < rule.minStackSize) {
            return false;
        }
        if (rule.maxStackSize > 0 && listing.getQuantity() > rule.maxStackSize) {
            return false;
        }
        if (rule.favoritesOnly && !containsIgnoreCase(favorites, listing.getItemId())) {
            return false;
        }
        if (!rule.whitelist.isEmpty() && !containsIgnoreCase(rule.whitelist, listing.getItemId())) {
            return false;
        }
        if (containsIgnoreCase(rule.blacklist, listing.getItemId())) {
            return false;
        }
        return true;
    }

    /** Applies the global blacklist/whitelist before any strategy sees an offer. */
    public boolean isGloballyAllowed(AuctionListing listing, List<String> whitelist, List<String> blacklist) {
        if (listing == null || !listing.isComplete()) {
            return false;
        }
        if (containsIgnoreCase(blacklist, listing.getItemId())) {
            return false;
        }
        if (whitelist != null && !whitelist.isEmpty() && !containsIgnoreCase(whitelist, listing.getItemId())) {
            return false;
        }
        return true;
    }

    public List<AuctionListing> apply(List<AuctionListing> listings, List<FilterRule> rules,
                                      List<String> favorites) {
        List<AuctionListing> result = new ArrayList<AuctionListing>();
        if (listings == null) {
            return result;
        }
        for (int i = 0; i < listings.size(); i++) {
            AuctionListing listing = listings.get(i);
            for (int r = 0; r < (rules == null ? 0 : rules.size()); r++) {
                if (matches(rules.get(r), listing, favorites)) {
                    result.add(listing);
                    break;
                }
            }
        }
        return result;
    }

    public static boolean containsIgnoreCase(List<String> list, String value) {
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
