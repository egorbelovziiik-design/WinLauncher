package dev.funtime.auctionhelper.model;

import java.util.Collections;
import java.util.List;

/** Version independent snapshot of one container slot that was visible on the client. */
public final class SlotSnapshot {
    private final int index;
    private final String itemId;
    private final String displayName;
    private final int count;
    private final List<String> lore;

    public SlotSnapshot(int index, String itemId, String displayName, int count, List<String> lore) {
        this.index = index;
        this.itemId = itemId;
        this.displayName = displayName;
        this.count = count;
        this.lore = lore == null ? Collections.<String>emptyList() : lore;
    }

    public int getIndex() {
        return index;
    }

    public String getItemId() {
        return itemId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getCount() {
        return count;
    }

    public List<String> getLore() {
        return lore;
    }

    public boolean isEmpty() {
        return itemId == null || itemId.isEmpty() || "minecraft:air".equals(itemId);
    }

    @Override
    public String toString() {
        return "#" + index + " " + itemId + " x" + count + " \"" + displayName + "\"";
    }
}
