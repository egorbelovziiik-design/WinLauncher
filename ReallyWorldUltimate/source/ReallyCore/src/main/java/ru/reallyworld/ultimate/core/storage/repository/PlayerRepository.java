package ru.reallyworld.ultimate.core.storage.repository;

import ru.reallyworld.ultimate.core.api.CurrencyType;
import ru.reallyworld.ultimate.core.storage.StorageModule;
import ru.reallyworld.ultimate.core.storage.model.HomePoint;
import ru.reallyworld.ultimate.core.storage.model.PlayerProfile;
import ru.reallyworld.ultimate.core.storage.model.WarpPoint;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public final class PlayerRepository {
    public record LeaderboardEntry(UUID uuid, String username, String rankId, long value) {}

    private final StorageModule storage;

    public PlayerRepository(StorageModule storage) {
        this.storage = storage;
    }

    public PlayerProfile loadOrCreate(UUID uuid, String username, long startCoins, long startRilliks, long startTokens) {
        long now = System.currentTimeMillis();
        return storage.inTransaction(conn -> {
            try (PreparedStatement sel = conn.prepareStatement("SELECT * FROM players WHERE uuid = ?")) {
                sel.setString(1, uuid.toString());
                try (ResultSet rs = sel.executeQuery()) {
                    if (rs.next()) {
                        PlayerProfile p = mapProfile(rs);
                        p.setUsername(username);
                        p.setLastSeenAt(now);
                        try (PreparedStatement upd = conn.prepareStatement(
                                "UPDATE players SET username = ?, last_seen_at = ? WHERE uuid = ?")) {
                            upd.setString(1, username);
                            upd.setLong(2, now);
                            upd.setString(3, uuid.toString());
                            upd.executeUpdate();
                        }
                        return p;
                    }
                }
            }
            PlayerProfile fresh = new PlayerProfile(uuid, username, now);
            fresh.setBalance(CurrencyType.COINS, startCoins);
            fresh.setBalance(CurrencyType.RILLIKS, startRilliks);
            fresh.setBalance(CurrencyType.TOKENS, startTokens);
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO players (uuid, username, rank_id, coins, rilliks, tokens, first_join_at, last_seen_at) " +
                            "VALUES (?, ?, 'PLAYER', ?, ?, ?, ?, ?)")) {
                ins.setString(1, uuid.toString());
                ins.setString(2, username);
                ins.setLong(3, startCoins);
                ins.setLong(4, startRilliks);
                ins.setLong(5, startTokens);
                ins.setLong(6, now);
                ins.setLong(7, now);
                ins.executeUpdate();
            }
            return fresh;
        });
    }

    public Optional<PlayerProfile> findByUuid(UUID uuid) {
        return storage.query(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM players WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapProfile(rs)) : Optional.empty();
                }
            }
        });
    }

    public Optional<PlayerProfile> findByUsername(String username) {
        return storage.query(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM players WHERE LOWER(username) = LOWER(?) LIMIT 1")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapProfile(rs)) : Optional.empty();
                }
            }
        });
    }

    /**
     * Saves non-currency metadata & statistics (currency columns are exclusively mutated via EconomyRepository).
     */
    public void saveProfileStats(PlayerProfile p) {
        storage.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE players SET username=?, rank_id=?, custom_prefix=?, nickname=?, kills=?, deaths=?, " +
                            "kill_streak=?, best_kill_streak=?, playtime_seconds=?, blocks_mined=?, boss_kills=?, " +
                            "events_won=?, quests_completed=?, cases_opened=?, grants_used=?, tp_toggle=?, " +
                            "scoreboard_enabled=?, pm_enabled=?, equipped_talisman_id=?, battlepass_season=?, " +
                            "battlepass_xp=?, battlepass_premium=?, daily_streak=?, last_daily_day=?, last_seen_at=? " +
                            "WHERE uuid=?")) {
                ps.setString(1, p.username());
                ps.setString(2, p.rankId());
                ps.setString(3, p.customPrefix());
                ps.setString(4, p.nickname());
                ps.setInt(5, p.kills());
                ps.setInt(6, p.deaths());
                ps.setInt(7, p.killStreak());
                ps.setInt(8, p.bestKillStreak());
                ps.setLong(9, p.playtimeSeconds());
                ps.setLong(10, p.blocksMined());
                ps.setInt(11, p.bossKills());
                ps.setInt(12, p.eventsWon());
                ps.setInt(13, p.questsCompleted());
                ps.setInt(14, p.casesOpened());
                ps.setInt(15, p.grantsUsed());
                ps.setInt(16, p.tpToggle() ? 1 : 0);
                ps.setInt(17, p.scoreboardEnabled() ? 1 : 0);
                ps.setInt(18, p.pmEnabled() ? 1 : 0);
                ps.setString(19, p.equippedTalismanId());
                ps.setInt(20, p.battlepassSeason());
                ps.setLong(21, p.battlepassXp());
                ps.setInt(22, p.battlepassPremium() ? 1 : 0);
                ps.setInt(23, p.dailyStreak());
                ps.setLong(24, p.lastDailyDay());
                ps.setLong(25, p.lastSeenAt());
                ps.setString(26, p.uuid().toString());
                ps.executeUpdate();
            }
            return null;
        });
    }

    public List<HomePoint> getHomes(UUID playerUuid) {
        return storage.query(conn -> {
            List<HomePoint> out = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM player_homes WHERE player_uuid = ? ORDER BY home_name ASC")) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new HomePoint(
                                playerUuid,
                                rs.getString("home_name"),
                                rs.getString("world_name"),
                                rs.getDouble("x"),
                                rs.getDouble("y"),
                                rs.getDouble("z"),
                                rs.getFloat("yaw"),
                                rs.getFloat("pitch"),
                                rs.getLong("created_at")
                        ));
                    }
                }
            }
            return out;
        });
    }

    public void upsertHome(HomePoint home) {
        storage.inTransaction(conn -> {
            try (PreparedStatement del = conn.prepareStatement(
                    "DELETE FROM player_homes WHERE player_uuid = ? AND LOWER(home_name) = LOWER(?)")) {
                del.setString(1, home.playerUuid().toString());
                del.setString(2, home.name());
                del.executeUpdate();
            }
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO player_homes (player_uuid, home_name, world_name, x, y, z, yaw, pitch, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ins.setString(1, home.playerUuid().toString());
                ins.setString(2, home.name().toLowerCase(Locale.ROOT));
                ins.setString(3, home.worldName());
                ins.setDouble(4, home.x());
                ins.setDouble(5, home.y());
                ins.setDouble(6, home.z());
                ins.setFloat(7, home.yaw());
                ins.setFloat(8, home.pitch());
                ins.setLong(9, home.createdAt());
                ins.executeUpdate();
            }
            return null;
        });
    }

    public boolean deleteHome(UUID playerUuid, String name) {
        return storage.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM player_homes WHERE player_uuid = ? AND LOWER(home_name) = LOWER(?)")) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, name);
                return ps.executeUpdate() > 0;
            }
        });
    }

    public List<WarpPoint> getWarps() {
        return storage.query(conn -> {
            List<WarpPoint> out = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM server_warps ORDER BY warp_name ASC");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new WarpPoint(
                            rs.getString("warp_name"),
                            rs.getString("display_name"),
                            rs.getString("category"),
                            rs.getString("permission"),
                            rs.getString("world_name"),
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("yaw"),
                            rs.getFloat("pitch"),
                            rs.getString("created_by"),
                            rs.getLong("created_at")
                    ));
                }
            }
            return out;
        });
    }

    public void upsertWarp(WarpPoint warp) {
        storage.inTransaction(conn -> {
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM server_warps WHERE LOWER(warp_name) = LOWER(?)")) {
                del.setString(1, warp.name());
                del.executeUpdate();
            }
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO server_warps (warp_name, display_name, category, permission, world_name, x, y, z, yaw, pitch, created_by, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ins.setString(1, warp.name().toLowerCase(Locale.ROOT));
                ins.setString(2, warp.displayName());
                ins.setString(3, warp.category());
                ins.setString(4, warp.permission());
                ins.setString(5, warp.worldName());
                ins.setDouble(6, warp.x());
                ins.setDouble(7, warp.y());
                ins.setDouble(8, warp.z());
                ins.setFloat(9, warp.yaw());
                ins.setFloat(10, warp.pitch());
                ins.setString(11, warp.createdBy());
                ins.setLong(12, warp.createdAt());
                ins.executeUpdate();
            }
            return null;
        });
    }

    public boolean deleteWarp(String name) {
        return storage.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM server_warps WHERE LOWER(warp_name) = LOWER(?)")) {
                ps.setString(1, name);
                return ps.executeUpdate() > 0;
            }
        });
    }

    public Set<UUID> getIgnoredPlayers(UUID playerUuid) {
        return storage.query(conn -> {
            Set<UUID> out = new HashSet<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT ignored_uuid FROM player_ignores WHERE player_uuid = ?")) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(UUID.fromString(rs.getString(1)));
                    }
                }
            }
            return out;
        });
    }

    public boolean toggleIgnore(UUID playerUuid, UUID ignoredUuid) {
        return storage.inTransaction(conn -> {
            try (PreparedStatement del = conn.prepareStatement(
                    "DELETE FROM player_ignores WHERE player_uuid = ? AND ignored_uuid = ?")) {
                del.setString(1, playerUuid.toString());
                del.setString(2, ignoredUuid.toString());
                if (del.executeUpdate() > 0) {
                    return false; // unignored
                }
            }
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO player_ignores (player_uuid, ignored_uuid, created_at) VALUES (?, ?, ?)")) {
                ins.setString(1, playerUuid.toString());
                ins.setString(2, ignoredUuid.toString());
                ins.setLong(3, System.currentTimeMillis());
                ins.executeUpdate();
                return true; // ignored
            }
        });
    }

    public List<LeaderboardEntry> getTopByColumn(String column, int limit) {
        String safeColumn = switch (column) {
            case "coins", "rilliks", "kills", "playtime_seconds", "boss_kills", "events_won" -> column;
            default -> throw new IllegalArgumentException("Disallowed leaderboard column: " + column);
        };
        return storage.query(conn -> {
            List<LeaderboardEntry> out = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT uuid, username, rank_id, " + safeColumn + " FROM players ORDER BY " + safeColumn + " DESC LIMIT ?")) {
                ps.setInt(1, Math.max(1, Math.min(limit, 50)));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new LeaderboardEntry(
                                UUID.fromString(rs.getString("uuid")),
                                rs.getString("username"),
                                rs.getString("rank_id"),
                                rs.getLong(4)
                        ));
                    }
                }
            }
            return out;
        });
    }

    private PlayerProfile mapProfile(ResultSet rs) throws SQLException {
        PlayerProfile p = new PlayerProfile(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("username"),
                rs.getLong("first_join_at")
        );
        p.setRankId(rs.getString("rank_id"));
        p.setCustomPrefix(rs.getString("custom_prefix"));
        p.setNickname(rs.getString("nickname"));
        p.setBalance(CurrencyType.COINS, rs.getLong("coins"));
        p.setBalance(CurrencyType.RILLIKS, rs.getLong("rilliks"));
        p.setBalance(CurrencyType.TOKENS, rs.getLong("tokens"));
        p.setKills(rs.getInt("kills"));
        p.setDeaths(rs.getInt("deaths"));
        p.setKillStreak(rs.getInt("kill_streak"));
        p.setBestKillStreak(rs.getInt("best_kill_streak"));
        p.setPlaytimeSeconds(rs.getLong("playtime_seconds"));
        p.setBlocksMined(rs.getLong("blocks_mined"));
        p.setBossKills(rs.getInt("boss_kills"));
        p.setEventsWon(rs.getInt("events_won"));
        p.setQuestsCompleted(rs.getInt("quests_completed"));
        p.setCasesOpened(rs.getInt("cases_opened"));
        p.setGrantsUsed(rs.getInt("grants_used"));
        p.setTpToggle(rs.getInt("tp_toggle") != 0);
        p.setScoreboardEnabled(rs.getInt("scoreboard_enabled") != 0);
        p.setPmEnabled(rs.getInt("pm_enabled") != 0);
        p.setEquippedTalismanId(rs.getString("equipped_talisman_id"));
        p.setBattlepassSeason(rs.getInt("battlepass_season"));
        p.setBattlepassXp(rs.getLong("battlepass_xp"));
        p.setBattlepassPremium(rs.getInt("battlepass_premium") != 0);
        p.setDailyStreak(rs.getInt("daily_streak"));
        p.setLastDailyDay(rs.getLong("last_daily_day"));
        p.setLastSeenAt(rs.getLong("last_seen_at"));
        return p;
    }
}
