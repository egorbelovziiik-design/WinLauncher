package dev.funtime.auctionhelper.adapter;

import dev.funtime.auctionhelper.config.HelperConfig;
import dev.funtime.auctionhelper.model.AuctionListing;
import dev.funtime.auctionhelper.model.ScreenSnapshot;
import dev.funtime.auctionhelper.model.SlotSnapshot;
import dev.funtime.auctionhelper.util.Numbers;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Configurable, honest adapter.
 *
 * <p>The public FunTime auction layout is not documented anywhere the author could verify, so
 * this mod does <b>not</b> hard code it. Instead every profile is a set of editable regular
 * expressions stored in the config. A profile starts as <i>unverified</i>; the player can
 * inspect the parsed result in the AUCTION ANALYSIS page and confirm it, after which the
 * profile is marked verified and its offers may feed the price history.</p>
 */
public final class RegexAuctionAdapter implements AuctionAdapter {
    private final HelperConfig.AdapterProfileConfig profile;

    public RegexAuctionAdapter(HelperConfig.AdapterProfileConfig profile) {
        this.profile = profile;
    }

    @Override
    public String getId() {
        return profile.id;
    }

    @Override
    public String getDisplayName() {
        return profile.displayName;
    }

    @Override
    public boolean matches(ScreenSnapshot snapshot) {
        if (snapshot == null || !profile.enabled) {
            return false;
        }
        if (profile.serverNamePattern != null && !profile.serverNamePattern.isEmpty()) {
            if (snapshot.getServerProfile() != null
                    && !matchesPattern(profile.serverNamePattern, snapshot.getServerProfile())) {
                return false;
            }
        }
        if (profile.screenTitlePattern != null && !profile.screenTitlePattern.isEmpty()) {
            return matchesPattern(profile.screenTitlePattern, snapshot.getTitle());
        }
        // Without a title pattern any container screen may be an auction; the parse result will
        // simply be empty for screens that are not auctions, which is not an error.
        return snapshot.isContainerScreen() || snapshot.getNonEmptySlotCount() > 0;
    }

    private static boolean matchesPattern(String pattern, String value) {
        if (value == null) {
            return false;
        }
        try {
            return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                    .matcher(value).find();
        } catch (PatternSyntaxException error) {
            return false;
        }
    }

    @Override
    public ParseResult parse(ScreenSnapshot snapshot) {
        List<AuctionListing> listings = new ArrayList<AuctionListing>();
        List<String> warnings = new ArrayList<String>();
        if (snapshot == null) {
            return ParseResult.unknown(profile.id, "No screen captured");
        }
        Pattern price = compile(profile.pricePattern);
        Pattern quantity = compile(profile.quantityPattern);
        if (price == null) {
            return ParseResult.unknown(profile.id, "Price pattern in profile \"" + profile.displayName
                    + "\" is invalid - fix it in SETTINGS > ADAPTER");
        }
        String itemNamePattern = profile.itemNamePattern;
        for (int i = 0; i < snapshot.getSlots().size(); i++) {
            SlotSnapshot slot = snapshot.getSlots().get(i);
            if (slot.isEmpty()) {
                continue;
            }
            String combined = combine(slot);
            double total = extractPrice(price, combined, snapshot);
            if (Double.isNaN(total)) {
                continue;
            }
            int amount = slot.getCount();
            if (quantity != null) {
                Matcher quantityMatcher = quantity.matcher(combined);
                if (quantityMatcher.find()) {
                    try {
                        amount = Integer.parseInt(quantityMatcher.group(1));
                    } catch (RuntimeException ignored) {
                        // keep the stack size
                    }
                }
            }
            String name = slot.getDisplayName();
            if (itemNamePattern != null && !itemNamePattern.isEmpty()) {
                Matcher nameMatcher = compile(itemNamePattern) == null
                        ? null : compile(itemNamePattern).matcher(combined);
                if (nameMatcher != null && nameMatcher.find()) {
                    name = nameMatcher.group();
                }
            }
            listings.add(new AuctionListing(slot.getItemId(), name, amount, total,
                    snapshot.getCapturedAt(), snapshot.getServerProfile(), slot.getLore(), slot.getIndex()));
        }
        boolean titleMatched = profile.screenTitlePattern == null || profile.screenTitlePattern.isEmpty()
                || matchesPattern(profile.screenTitlePattern, snapshot.getTitle());
        if (listings.isEmpty()) {
            warnings.add("No price could be read from this screen - the GUI may have changed");
        }
        if (!profile.verified) {
            warnings.add("Profile \"" + profile.displayName + "\" is unverified: confirm the parsed "
                    + "offers in AUCTION ANALYSIS before trusting them");
        }
        return new ParseResult(profile.id, listings, warnings, titleMatched, profile.verified);
    }

    private static String combine(SlotSnapshot slot) {
        StringBuilder builder = new StringBuilder();
        builder.append(slot.getDisplayName() == null ? "" : slot.getDisplayName());
        for (int i = 0; i < slot.getLore().size(); i++) {
            builder.append('\n').append(slot.getLore().get(i));
        }
        return builder.toString();
    }

    private double extractPrice(Pattern price, String text, ScreenSnapshot snapshot) {
        Matcher matcher = price.matcher(text);
        double best = Double.NaN;
        while (matcher.find()) {
            String raw = matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group();
            double value = Numbers.parseAmount(raw);
            if (!Double.isNaN(value) && value > 0d) {
                best = value;
                break;
            }
        }
        if (Double.isNaN(best)) {
            return Double.NaN;
        }
        return best * (profile.priceMultiplier == 0d ? 1d : profile.priceMultiplier);
    }

    private static Pattern compile(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return null;
        }
        try {
            return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        } catch (PatternSyntaxException error) {
            return null;
        }
    }
}
