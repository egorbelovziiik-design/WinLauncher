package dev.funtime.auctionhelper.adapter;

import dev.funtime.auctionhelper.model.ScreenSnapshot;

/**
 * Turns an observed screen into listings.
 *
 * <p>An adapter never clicks, never sends packets and never invents offers: it only reads
 * what Minecraft already shows to the player.</p>
 */
public interface AuctionAdapter {
    String getId();

    String getDisplayName();

    /** True when this adapter believes it understands the screen (usually a title match). */
    boolean matches(ScreenSnapshot snapshot);

    ParseResult parse(ScreenSnapshot snapshot);
}
