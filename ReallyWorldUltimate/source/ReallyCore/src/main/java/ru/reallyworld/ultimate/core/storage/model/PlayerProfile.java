package ru.reallyworld.ultimate.core.storage.model;

import ru.reallyworld.ultimate.core.api.CurrencyType;

import java.util.UUID;

public final class PlayerProfile {
    private final UUID uuid;
    private volatile String username;
    private volatile String rankId;
    private volatile String customPrefix;
    private volatile String nickname;
    private volatile long coins;
    private volatile long rilliks;
    private volatile long tokens;
    private volatile int kills;
    private volatile int deaths;
    private volatile int killStreak;
    private volatile int bestKillStreak;
    private volatile long playtimeSeconds;
    private volatile long blocksMined;
    private volatile int bossKills;
    private volatile int eventsWon;
    private volatile int questsCompleted;
    private volatile int casesOpened;
    private volatile int grantsUsed;
    private volatile boolean tpToggle;
    private volatile boolean scoreboardEnabled;
    private volatile boolean pmEnabled;
    private volatile String equippedTalismanId;
    private volatile int battlepassSeason;
    private volatile long battlepassXp;
    private volatile boolean battlepassPremium;
    private volatile int dailyStreak;
    private volatile long lastDailyDay;
    private final long firstJoinAt;
    private volatile long lastSeenAt;

    public PlayerProfile(UUID uuid, String username, long nowMillis) {
        this.uuid = uuid;
        this.username = username;
        this.rankId = "PLAYER";
        this.tpToggle = true;
        this.scoreboardEnabled = true;
        this.pmEnabled = true;
        this.battlepassSeason = 1;
        this.firstJoinAt = nowMillis;
        this.lastSeenAt = nowMillis;
    }

    public UUID uuid() { return uuid; }
    public String username() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String rankId() { return rankId; }
    public void setRankId(String rankId) { this.rankId = rankId; }
    public String customPrefix() { return customPrefix; }
    public void setCustomPrefix(String customPrefix) { this.customPrefix = customPrefix; }
    public String nickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public long balance(CurrencyType type) {
        return switch (type) {
            case COINS -> coins;
            case RILLIKS -> rilliks;
            case TOKENS -> tokens;
        };
    }

    public void setBalance(CurrencyType type, long value) {
        long clamped = Math.max(0L, value);
        switch (type) {
            case COINS -> this.coins = clamped;
            case RILLIKS -> this.rilliks = clamped;
            case TOKENS -> this.tokens = clamped;
        }
    }

    public long coins() { return coins; }
    public long rilliks() { return rilliks; }
    public long tokens() { return tokens; }

    public int kills() { return kills; }
    public void setKills(int kills) { this.kills = kills; }
    public int deaths() { return deaths; }
    public void setDeaths(int deaths) { this.deaths = deaths; }
    public double kdRatio() { return deaths <= 0 ? kills : Math.round((kills * 100.0) / deaths) / 100.0; }
    public int killStreak() { return killStreak; }
    public void setKillStreak(int killStreak) {
        this.killStreak = killStreak;
        if (killStreak > this.bestKillStreak) {
            this.bestKillStreak = killStreak;
        }
    }
    public int bestKillStreak() { return bestKillStreak; }
    public void setBestKillStreak(int bestKillStreak) { this.bestKillStreak = bestKillStreak; }
    public long playtimeSeconds() { return playtimeSeconds; }
    public void setPlaytimeSeconds(long playtimeSeconds) { this.playtimeSeconds = playtimeSeconds; }
    public long blocksMined() { return blocksMined; }
    public void setBlocksMined(long blocksMined) { this.blocksMined = blocksMined; }
    public int bossKills() { return bossKills; }
    public void setBossKills(int bossKills) { this.bossKills = bossKills; }
    public int eventsWon() { return eventsWon; }
    public void setEventsWon(int eventsWon) { this.eventsWon = eventsWon; }
    public int questsCompleted() { return questsCompleted; }
    public void setQuestsCompleted(int questsCompleted) { this.questsCompleted = questsCompleted; }
    public int casesOpened() { return casesOpened; }
    public void setCasesOpened(int casesOpened) { this.casesOpened = casesOpened; }
    public int grantsUsed() { return grantsUsed; }
    public void setGrantsUsed(int grantsUsed) { this.grantsUsed = grantsUsed; }
    public boolean tpToggle() { return tpToggle; }
    public void setTpToggle(boolean tpToggle) { this.tpToggle = tpToggle; }
    public boolean scoreboardEnabled() { return scoreboardEnabled; }
    public void setScoreboardEnabled(boolean scoreboardEnabled) { this.scoreboardEnabled = scoreboardEnabled; }
    public boolean pmEnabled() { return pmEnabled; }
    public void setPmEnabled(boolean pmEnabled) { this.pmEnabled = pmEnabled; }
    public String equippedTalismanId() { return equippedTalismanId; }
    public void setEquippedTalismanId(String equippedTalismanId) { this.equippedTalismanId = equippedTalismanId; }
    public int battlepassSeason() { return battlepassSeason; }
    public void setBattlepassSeason(int battlepassSeason) { this.battlepassSeason = battlepassSeason; }
    public long battlepassXp() { return battlepassXp; }
    public void setBattlepassXp(long battlepassXp) { this.battlepassXp = battlepassXp; }
    public boolean battlepassPremium() { return battlepassPremium; }
    public void setBattlepassPremium(boolean battlepassPremium) { this.battlepassPremium = battlepassPremium; }
    public int dailyStreak() { return dailyStreak; }
    public void setDailyStreak(int dailyStreak) { this.dailyStreak = dailyStreak; }
    public long lastDailyDay() { return lastDailyDay; }
    public void setLastDailyDay(long lastDailyDay) { this.lastDailyDay = lastDailyDay; }
    public long firstJoinAt() { return firstJoinAt; }
    public long lastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(long lastSeenAt) { this.lastSeenAt = lastSeenAt; }
}
