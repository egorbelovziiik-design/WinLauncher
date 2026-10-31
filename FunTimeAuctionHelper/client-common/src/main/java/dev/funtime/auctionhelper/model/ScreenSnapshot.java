package dev.funtime.auctionhelper.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Everything the mod may legitimately know about a screen that the player opened:
 * the title and the slots that Minecraft already rendered for the player.
 */
public final class ScreenSnapshot {
    private final String title;
    private final String screenClass;
    private final List<SlotSnapshot> slots;
    private final long capturedAt;
    private final String serverProfile;
    private final boolean containerScreen;

    public ScreenSnapshot(String title, String screenClass, List<SlotSnapshot> slots, long capturedAt,
                          String serverProfile, boolean containerScreen) {
        this.title = title == null ? "" : title;
        this.screenClass = screenClass == null ? "" : screenClass;
        this.slots = slots == null
                ? Collections.<SlotSnapshot>emptyList()
                : Collections.unmodifiableList(new ArrayList<SlotSnapshot>(slots));
        this.capturedAt = capturedAt;
        this.serverProfile = serverProfile == null ? "unknown" : serverProfile;
        this.containerScreen = containerScreen;
    }

    public String getTitle() {
        return title;
    }

    public String getScreenClass() {
        return screenClass;
    }

    public List<SlotSnapshot> getSlots() {
        return slots;
    }

    public long getCapturedAt() {
        return capturedAt;
    }

    public String getServerProfile() {
        return serverProfile;
    }

    public boolean isContainerScreen() {
        return containerScreen;
    }

    public int getNonEmptySlotCount() {
        int count = 0;
        for (int i = 0; i < slots.size(); i++) {
            if (!slots.get(i).isEmpty()) {
                count++;
            }
        }
        return count;
    }
}
