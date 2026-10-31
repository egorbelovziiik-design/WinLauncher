package dev.funtime.auctionhelper.adapter;

import dev.funtime.auctionhelper.model.AuctionListing;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Outcome of one screen parse, including the diagnostics the GUI shows. */
public final class ParseResult {
    private final String profileId;
    private final List<AuctionListing> listings;
    private final List<String> warnings;
    private final boolean titleMatched;
    private final boolean profileVerified;

    public ParseResult(String profileId, List<AuctionListing> listings, List<String> warnings,
                       boolean titleMatched, boolean profileVerified) {
        this.profileId = profileId;
        this.listings = listings == null
                ? Collections.<AuctionListing>emptyList()
                : Collections.unmodifiableList(new ArrayList<AuctionListing>(listings));
        this.warnings = warnings == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(warnings));
        this.titleMatched = titleMatched;
        this.profileVerified = profileVerified;
    }

    public static ParseResult unknown(String profileId, String warning) {
        List<String> warnings = new ArrayList<String>();
        warnings.add(warning);
        return new ParseResult(profileId, null, warnings, false, false);
    }

    public String getProfileId() {
        return profileId;
    }

    public List<AuctionListing> getListings() {
        return listings;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public boolean isTitleMatched() {
        return titleMatched;
    }

    public boolean isProfileVerified() {
        return profileVerified;
    }

    public boolean isEmpty() {
        return listings.isEmpty();
    }

    public int getConfidence() {
        if (listings.isEmpty()) {
            return 0;
        }
        int score = 1;
        if (titleMatched) {
            score++;
        }
        if (profileVerified) {
            score++;
        }
        if (listings.size() >= 3) {
            score++;
        }
        return score;
    }
}
