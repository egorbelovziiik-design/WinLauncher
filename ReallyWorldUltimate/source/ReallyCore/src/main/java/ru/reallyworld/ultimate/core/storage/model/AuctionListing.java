package ru.reallyworld.ultimate.core.storage.model;

import ru.reallyworld.ultimate.core.api.CurrencyType;

import java.util.UUID;

public record AuctionListing(
        long id,
        UUID sellerUuid,
        String sellerName,
        String itemMaterial,
        String itemDisplayName,
        String itemCategory,
        int itemAmount,
        String itemSerialized,
        long price,
        CurrencyType currency,
        String status,
        UUID buyerUuid,
        String buyerName,
        long createdAt,
        long expiresAt,
        Long resolvedAt
) {
    public boolean isExpired(long nowMillis) {
        return "ACTIVE".equalsIgnoreCase(status) && nowMillis >= expiresAt;
    }
}
