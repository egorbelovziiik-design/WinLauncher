package ru.reallyworld.ultimate.core.storage.repository;

import ru.reallyworld.ultimate.core.api.CurrencyType;
import ru.reallyworld.ultimate.core.storage.StorageModule;
import ru.reallyworld.ultimate.core.storage.model.AuctionListing;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Crash-safe, anti-double-buy SQL Auction Repository.
 * Purchase and return operations use strict Compare-And-Swap (`WHERE id = ? AND status = 'ACTIVE'`)
 * inside atomic SQL transactions coupled with EconomyRepository balance updates.
 */
public final class AuctionRepository {
    public record BuyOutcome(boolean success, AuctionListing listing, long buyerBalanceAfter, long sellerNetReceived, String reason) {}
    public record CreateOutcome(boolean success, long listingId, long sellerBalanceAfter, String reason) {}

    private final StorageModule storage;

    public AuctionRepository(StorageModule storage) {
        this.storage = storage;
    }

    public int countActiveListings(UUID sellerUuid, long nowMillis) {
        return storage.query(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM auction_listings WHERE seller_uuid = ? AND status = 'ACTIVE' AND expires_at > ?")) {
                ps.setString(1, sellerUuid.toString());
                ps.setLong(2, nowMillis);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        });
    }

    public CreateOutcome createListing(
            UUID sellerUuid,
            String sellerName,
            String itemMaterial,
            String itemDisplayName,
            String itemCategory,
            int itemAmount,
            String itemSerialized,
            long price,
            CurrencyType currency,
            long listingFeeCoins,
            long expiresAtMillis,
            int maxAllowedSlots
    ) {
        long now = System.currentTimeMillis();
        return storage.inTransaction(conn -> {
            try (PreparedStatement cnt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM auction_listings WHERE seller_uuid = ? AND status = 'ACTIVE' AND expires_at > ?")) {
                cnt.setString(1, sellerUuid.toString());
                cnt.setLong(2, now);
                try (ResultSet rs = cnt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) >= maxAllowedSlots) {
                        return new CreateOutcome(false, -1L, 0L, "SLOTS_FULL");
                    }
                }
            }

            long sellerBalanceAfter = 0L;
            if (listingFeeCoins > 0) {
                EconomyRepository.EconomyResult feeRes = storage.economy().withdrawInTx(
                        conn, sellerUuid, CurrencyType.COINS, CurrencyType.COINS.column(),
                        listingFeeCoins, "AUCTION_LISTING_FEE", "Auction listing fee for " + itemMaterial
                );
                if (!feeRes.success()) {
                    return new CreateOutcome(false, -1L, feeRes.newBalance(), "INSUFFICIENT_FEE");
                }
                sellerBalanceAfter = feeRes.newBalance();
            }

            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO auction_listings " +
                            "(seller_uuid, seller_name, item_material, item_display_name, item_category, item_amount, " +
                            "item_serialized, price, currency, status, created_at, expires_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ins.setString(1, sellerUuid.toString());
                ins.setString(2, sellerName);
                ins.setString(3, itemMaterial);
                ins.setString(4, itemDisplayName);
                ins.setString(5, itemCategory);
                ins.setInt(6, itemAmount);
                ins.setString(7, itemSerialized);
                ins.setLong(8, price);
                ins.setString(9, currency.name());
                ins.setLong(10, now);
                ins.setLong(11, expiresAtMillis);
                ins.executeUpdate();
                try (ResultSet keys = ins.getGeneratedKeys()) {
                    long id = keys.next() ? keys.getLong(1) : -1L;
                    return new CreateOutcome(true, id, sellerBalanceAfter, null);
                }
            }
        });
    }

    /**
     * Atomically purchases a listing:
     * 1) Marks expired rows if needed.
     * 2) Atomically transitions `ACTIVE` -> `SOLD` (`WHERE id=? AND status='ACTIVE' AND expires_at > ?`).
     *    If 0 rows updated, another buyer or cancellation won the race -> aborts cleanly!
     * 3) Withdraws full price from buyer; if insufficient funds -> throws & rolls back status to `ACTIVE`!
     * 4) Deposits net proceeds (after tax) to seller!
     */
    public BuyOutcome buyListingAtomic(long listingId, UUID buyerUuid, String buyerName, int saleTaxPercent) {
        long now = System.currentTimeMillis();
        return storage.inTransaction(conn -> {
            AuctionListing listing;
            try (PreparedStatement sel = conn.prepareStatement("SELECT * FROM auction_listings WHERE id = ?")) {
                sel.setLong(1, listingId);
                try (ResultSet rs = sel.executeQuery()) {
                    if (!rs.next()) {
                        return new BuyOutcome(false, null, 0L, 0L, "NOT_FOUND");
                    }
                    listing = mapListing(rs);
                }
            }

            if (!"ACTIVE".equalsIgnoreCase(listing.status())) {
                return new BuyOutcome(false, listing, 0L, 0L, "ALREADY_SOLD_OR_CANCELLED");
            }
            if (now >= listing.expiresAt()) {
                try (PreparedStatement exp = conn.prepareStatement(
                        "UPDATE auction_listings SET status = 'EXPIRED' WHERE id = ? AND status = 'ACTIVE'")) {
                    exp.setLong(1, listingId);
                    exp.executeUpdate();
                }
                return new BuyOutcome(false, listing, 0L, 0L, "EXPIRED");
            }
            if (listing.sellerUuid().equals(buyerUuid)) {
                return new BuyOutcome(false, listing, 0L, 0L, "SELF_BUY");
            }

            try (PreparedStatement cas = conn.prepareStatement(
                    "UPDATE auction_listings SET status = 'SOLD', buyer_uuid = ?, buyer_name = ?, resolved_at = ? " +
                            "WHERE id = ? AND status = 'ACTIVE' AND expires_at > ?")) {
                cas.setString(1, buyerUuid.toString());
                cas.setString(2, buyerName);
                cas.setLong(3, now);
                cas.setLong(4, listingId);
                cas.setLong(5, now);
                if (cas.executeUpdate() != 1) {
                    return new BuyOutcome(false, listing, 0L, 0L, "RACE_LOST");
                }
            }

            CurrencyType currency = listing.currency();
            EconomyRepository.EconomyResult withdrawBuyer = storage.economy().withdrawInTx(
                    conn, buyerUuid, currency, currency.column(), listing.price(),
                    "AUCTION_BUY", "Bought auction #" + listingId + " from " + listing.sellerName()
            );
            if (!withdrawBuyer.success()) {
                throw new InsufficientAuctionFundsException(withdrawBuyer.failureReason());
            }

            long tax = (listing.price() * Math.max(0, Math.min(saleTaxPercent, 50))) / 100L;
            long sellerNet = Math.max(1L, listing.price() - tax);
            EconomyRepository.EconomyResult creditSeller = storage.economy().depositInTx(
                    conn, listing.sellerUuid(), currency, currency.column(), sellerNet,
                    "AUCTION_SALE", "Sold auction #" + listingId + " to " + buyerName
            );
            if (!creditSeller.success()) {
                throw new IllegalStateException("Seller balance cap exceeded");
            }

            return new BuyOutcome(true, listing, withdrawBuyer.newBalance(), sellerNet, null);
        });
    }

    public static final class InsufficientAuctionFundsException extends RuntimeException {
        public InsufficientAuctionFundsException(String code) { super(code); }
    }

    public Optional<AuctionListing> claimReturnAtomic(long listingId, UUID sellerUuid) {
        long now = System.currentTimeMillis();
        return storage.inTransaction(conn -> {
            AuctionListing listing;
            try (PreparedStatement sel = conn.prepareStatement(
                    "SELECT * FROM auction_listings WHERE id = ? AND seller_uuid = ?")) {
                sel.setLong(1, listingId);
                sel.setString(2, sellerUuid.toString());
                try (ResultSet rs = sel.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    listing = mapListing(rs);
                }
            }
            if (!"ACTIVE".equalsIgnoreCase(listing.status()) && !"EXPIRED".equalsIgnoreCase(listing.status())) {
                return Optional.empty();
            }
            String nextStatus = now >= listing.expiresAt() ? "RETURNED" : "CANCELLED";
            try (PreparedStatement cas = conn.prepareStatement(
                    "UPDATE auction_listings SET status = ?, resolved_at = ? " +
                            "WHERE id = ? AND seller_uuid = ? AND status IN ('ACTIVE', 'EXPIRED')")) {
                cas.setString(1, nextStatus);
                cas.setLong(2, now);
                cas.setLong(3, listingId);
                cas.setString(4, sellerUuid.toString());
                if (cas.executeUpdate() != 1) {
                    return Optional.empty();
                }
            }
            return Optional.of(listing);
        });
    }

    public int expireOverdueListings(long nowMillis) {
        return storage.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE auction_listings SET status = 'EXPIRED' WHERE status = 'ACTIVE' AND expires_at <= ?")) {
                ps.setLong(1, nowMillis);
                return ps.executeUpdate();
            }
        });
    }

    public List<AuctionListing> findActive(String categoryFilter, String searchQuery, String sortMode, int limit, int offset) {
        long now = System.currentTimeMillis();
        String orderClause = switch (sortMode == null ? "NEWEST" : sortMode.toUpperCase()) {
            case "CHEAPEST" -> "price ASC";
            case "EXPENSIVE" -> "price DESC";
            case "EXPIRING" -> "expires_at ASC";
            default -> "created_at DESC";
        };
        return storage.query(conn -> {
            StringBuilder sql = new StringBuilder(
                    "SELECT * FROM auction_listings WHERE status = 'ACTIVE' AND expires_at > ?");
            List<Object> params = new ArrayList<>();
            params.add(now);
            if (categoryFilter != null && !categoryFilter.isBlank() && !"ALL".equalsIgnoreCase(categoryFilter)) {
                sql.append(" AND UPPER(item_category) = UPPER(?)");
                params.add(categoryFilter);
            }
            if (searchQuery != null && !searchQuery.isBlank()) {
                sql.append(" AND (LOWER(item_display_name) LIKE LOWER(?) OR LOWER(item_material) LIKE LOWER(?) OR LOWER(seller_name) LIKE LOWER(?))");
                String q = "%" + searchQuery.trim() + "%";
                params.add(q);
                params.add(q);
                params.add(q);
            }
            sql.append(" ORDER BY ").append(orderClause).append(" LIMIT ? OFFSET ?");
            params.add(Math.max(1, Math.min(limit, 200)));
            params.add(Math.max(0, offset));

            List<AuctionListing> out = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(mapListing(rs));
                    }
                }
            }
            return out;
        });
    }

    public List<AuctionListing> findPlayerClaimableOrActive(UUID sellerUuid) {
        return storage.query(conn -> {
            List<AuctionListing> out = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM auction_listings WHERE seller_uuid = ? AND status IN ('ACTIVE', 'EXPIRED') ORDER BY created_at DESC")) {
                ps.setString(1, sellerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(mapListing(rs));
                    }
                }
            }
            return out;
        });
    }

    public List<AuctionListing> findPlayerHistory(UUID playerUuid, int limit) {
        return storage.query(conn -> {
            List<AuctionListing> out = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM auction_listings WHERE (seller_uuid = ? OR buyer_uuid = ?) AND status = 'SOLD' ORDER BY resolved_at DESC LIMIT ?")) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, playerUuid.toString());
                ps.setInt(3, Math.max(1, Math.min(limit, 50)));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(mapListing(rs));
                    }
                }
            }
            return out;
        });
    }

    private AuctionListing mapListing(ResultSet rs) throws SQLException {
        String buyerRaw = rs.getString("buyer_uuid");
        long resolvedRaw = rs.getLong("resolved_at");
        Long resolved = rs.wasNull() ? null : resolvedRaw;
        return new AuctionListing(
                rs.getLong("id"),
                UUID.fromString(rs.getString("seller_uuid")),
                rs.getString("seller_name"),
                rs.getString("item_material"),
                rs.getString("item_display_name"),
                rs.getString("item_category"),
                rs.getInt("item_amount"),
                rs.getString("item_serialized"),
                rs.getLong("price"),
                CurrencyType.fromString(rs.getString("currency")),
                rs.getString("status"),
                buyerRaw != null ? UUID.fromString(buyerRaw) : null,
                rs.getString("buyer_name"),
                rs.getLong("created_at"),
                rs.getLong("expires_at"),
                resolved
        );
    }
}
