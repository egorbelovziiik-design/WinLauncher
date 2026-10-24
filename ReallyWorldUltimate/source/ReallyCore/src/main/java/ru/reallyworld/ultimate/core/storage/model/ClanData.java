package ru.reallyworld.ultimate.core.storage.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ClanData(
        long id,
        String name,
        String tag,
        String description,
        UUID leaderUuid,
        long bankCoins,
        int ratingPoints,
        int kills,
        int deaths,
        int captures,
        boolean friendlyFire,
        String homeWorld,
        Double homeX,
        Double homeY,
        Double homeZ,
        Float homeYaw,
        Float homePitch,
        long createdAt,
        List<ClanMember> members,
        Map<Long, String> relations // targetClanId -> ALLY / ENEMY
) {
    public record ClanMember(
            UUID playerUuid,
            String playerName,
            long clanId,
            ClanRole role,
            long contributedCoins,
            long joinedAt
    ) {}

    public enum ClanRole {
        LEADER(4, "Глава"),
        COLEADER(3, "Со-лидер"),
        ELDER(2, "Старейшина"),
        MEMBER(1, "Участник");

        private final int power;
        private final String displayRu;

        ClanRole(int power, String displayRu) {
            this.power = power;
            this.displayRu = displayRu;
        }

        public int power() { return power; }
        public String displayRu() { return displayRu; }
    }
}
