package ru.reallyworld.ultimate.core.config;

import java.util.List;
import java.util.Set;

public record RankDefinition(
        String id,
        String alias,
        int weight,
        String displayName,
        String prefix,
        String tabPrefix,
        String color,
        long priceRilliks,
        boolean purchasable,
        int maxHomes,
        int auctionSlots,
        double killCoinMultiplier,
        int monthlyGrantQuota,
        List<String> inherits,
        Set<String> resolvedPermissions
) {}
