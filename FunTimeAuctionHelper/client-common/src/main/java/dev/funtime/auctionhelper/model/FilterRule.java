package dev.funtime.auctionhelper.model;

import java.util.ArrayList;
import java.util.List;

/** A saved auction filter. All configured bounds must match (logical AND). */
public final class FilterRule {
    public String id = "";
    public String name = "New filter";
    public boolean enabled = true;
    public String nameContains = "";
    public String itemIdContains = "";
    public double minPrice = 0d;
    public double maxPrice = 0d;
    public double minUnitPrice = 0d;
    public double maxUnitPrice = 0d;
    public int minStackSize = 0;
    public int maxStackSize = 64;
    public String category = "";
    public boolean favoritesOnly = false;
    public List<String> whitelist = new ArrayList<String>();
    public List<String> blacklist = new ArrayList<String>();

    public FilterRule() {
    }

    public FilterRule(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public FilterRule copy() {
        FilterRule copy = new FilterRule(id, name);
        copy.enabled = enabled;
        copy.nameContains = nameContains;
        copy.itemIdContains = itemIdContains;
        copy.minPrice = minPrice;
        copy.maxPrice = maxPrice;
        copy.minUnitPrice = minUnitPrice;
        copy.maxUnitPrice = maxUnitPrice;
        copy.minStackSize = minStackSize;
        copy.maxStackSize = maxStackSize;
        copy.category = category;
        copy.favoritesOnly = favoritesOnly;
        copy.whitelist = new ArrayList<String>(whitelist);
        copy.blacklist = new ArrayList<String>(blacklist);
        return copy;
    }

    public String describe() {
        StringBuilder builder = new StringBuilder();
        if (nameContains != null && !nameContains.isEmpty()) {
            builder.append("name~").append(nameContains).append(' ');
        }
        if (itemIdContains != null && !itemIdContains.isEmpty()) {
            builder.append("id~").append(itemIdContains).append(' ');
        }
        if (maxPrice > 0d) {
            builder.append("price<=").append(maxPrice).append(' ');
        }
        if (minPrice > 0d) {
            builder.append("price>=").append(minPrice).append(' ');
        }
        if (maxUnitPrice > 0d) {
            builder.append("unit<=").append(maxUnitPrice).append(' ');
        }
        if (minStackSize > 0 || maxStackSize < 64) {
            builder.append("stack ").append(minStackSize).append('-').append(maxStackSize).append(' ');
        }
        if (builder.length() == 0) {
            builder.append("no bounds");
        }
        return builder.toString().trim();
    }
}
