package ru.reallyworld.ultimate.core.storage.repository;

import ru.reallyworld.ultimate.core.storage.StorageModule;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

/**
 * Handles persistent Cooldowns, Case Keys + History, Quest progress,
 * Anti-double-claim Reward Locks, and Security Audit Logs.
 */
public final class ProgressionRepository {
    public record CaseHistoryEntry(long id, String playerName, String caseId, String rewardId, String rewardDisplay, String rarity, long openedAt) {}
    public record QuestState(String questId, String periodKey, long progress, boolean completed, boolean claimed) {}

    private final StorageModule storage;

    public ProgressionRepository(StorageModule storage) {
        this.storage = storage;
    }

    // ---- COOLDOWNS ----
    public Map<String, Long> loadActiveCooldowns(UUID playerUuid, long nowMillis) {
        return storage.query(conn -> {
            Map<String, Long> out = new HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT cooldown_key, expires_at FROM player_cooldowns WHERE player_uuid = ? AND expires_at > ?")) {
                ps.setString(1, playerUuid.toString());
                ps.setLong(2, nowMillis);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.put(rs.getString(1), rs.getLong(2));
                    }
                }
            }
            return out;
        });
    }

    public void setCooldown(UUID playerUuid, String key, long expiresAtMillis) {
        storage.inTransaction(conn -> {
            try (PreparedStatement del = conn.prepareStatement(
                    "DELETE FROM player_cooldowns WHERE player_uuid = ? AND cooldown_key = ?")) {
                del.setString(1, playerUuid.toString());
                del.setString(2, key);
                del.executeUpdate();
            }
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO player_cooldowns (player_uuid, cooldown_key, expires_at) VALUES (?, ?, ?)")) {
                ins.setString(1, playerUuid.toString());
                ins.setString(2, key);
                ins.setLong(3, expiresAtMillis);
                ins.executeUpdate();
            }
            return null;
        });
    }

    // ---- CASE KEYS & HISTORY ----
    public Map<String, Integer> getCaseKeys(UUID playerUuid) {
        return storage.query(conn -> {
            Map<String, Integer> out = new HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT case_id, amount FROM player_case_keys WHERE player_uuid = ? AND amount > 0")) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.put(rs.getString(1).toLowerCase(Locale.ROOT), rs.getInt(2));
                    }
                }
            }
            return out;
        });
    }

    public int addCaseKeys(UUID playerUuid, String caseId, int delta) {
        if (delta <= 0) throw new IllegalArgumentException("Key delta must be > 0");
        String norm = caseId.toLowerCase(Locale.ROOT);
        return storage.inTransaction(conn -> {
            int current = 0;
            try (PreparedStatement sel = conn.prepareStatement(
                    "SELECT amount FROM player_case_keys WHERE player_uuid = ? AND case_id = ?")) {
                sel.setString(1, playerUuid.toString());
                sel.setString(2, norm);
                try (ResultSet rs = sel.executeQuery()) {
                    if (rs.next()) {
                        current = rs.getInt(1);
                    }
                }
            }
            int updated = current + delta;
            try (PreparedStatement del = conn.prepareStatement(
                    "DELETE FROM player_case_keys WHERE player_uuid = ? AND case_id = ?")) {
                del.setString(1, playerUuid.toString());
                del.setString(2, norm);
                del.executeUpdate();
            }
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO player_case_keys (player_uuid, case_id, amount) VALUES (?, ?, ?)")) {
                ins.setString(1, playerUuid.toString());
                ins.setString(2, norm);
                ins.setInt(3, updated);
                ins.executeUpdate();
            }
            return updated;
        });
    }

    /**
     * Atomically consumes 1 key (`WHERE amount >= 1`) BEFORE rolling a case to prevent dupes!
     */
    public boolean consumeOneKeyAtomic(UUID playerUuid, String caseId) {
        String norm = caseId.toLowerCase(Locale.ROOT);
        return storage.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE player_case_keys SET amount = amount - 1 WHERE player_uuid = ? AND case_id = ? AND amount >= 1")) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, norm);
                return ps.executeUpdate() == 1;
            }
        });
    }

    public void recordCaseDrop(UUID playerUuid, String playerName, String caseId, String rewardId, String rewardDisplay, String rarity) {
        storage.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO case_history (open_uuid, player_uuid, player_name, case_id, reward_id, reward_display, opened_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, playerUuid.toString());
                ps.setString(3, playerName);
                ps.setString(4, caseId);
                ps.setString(5, rewardId);
                ps.setString(6, rewardDisplay);
                ps.setLong(7, System.currentTimeMillis());
                ps.executeUpdate();
            }
            return null;
        });
    }

    public List<CaseHistoryEntry> getRecentCaseDrops(int limit) {
        return storage.query(conn -> {
            List<CaseHistoryEntry> out = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM case_history ORDER BY opened_at DESC LIMIT ?")) {
                ps.setInt(1, Math.max(1, Math.min(limit, 50)));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new CaseHistoryEntry(
                                rs.getLong("id"),
                                rs.getString("player_name"),
                                rs.getString("case_id"),
                                rs.getString("reward_id"),
                                rs.getString("reward_display"),
                                "COMMON",
                                rs.getLong("opened_at")
                        ));
                    }
                }
            }
            return out;
        });
    }

    // ---- QUESTS ----
    public Map<String, QuestState> loadPlayerQuests(UUID playerUuid) {
        return storage.query(conn -> {
            Map<String, QuestState> out = new HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM player_quests WHERE player_uuid = ?")) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String qId = rs.getString("quest_id");
                        String pKey = rs.getString("period_key");
                        out.put(qId + ":" + pKey, new QuestState(
                                qId, pKey, rs.getLong("progress"),
                                rs.getInt("completed") != 0,
                                rs.getInt("claimed") != 0
                        ));
                    }
                }
            }
            return out;
        });
    }

    public void upsertQuestProgress(UUID playerUuid, String questId, String periodKey, long progress, boolean completed) {
        long now = System.currentTimeMillis();
        storage.inTransaction(conn -> {
            try (PreparedStatement sel = conn.prepareStatement(
                    "SELECT claimed FROM player_quests WHERE player_uuid = ? AND quest_id = ? AND period_key = ?")) {
                sel.setString(1, playerUuid.toString());
                sel.setString(2, questId);
                sel.setString(3, periodKey);
                try (ResultSet rs = sel.executeQuery()) {
                    if (rs.next()) {
                        try (PreparedStatement upd = conn.prepareStatement(
                                "UPDATE player_quests SET progress = ?, completed = ?, updated_at = ? " +
                                        "WHERE player_uuid = ? AND quest_id = ? AND period_key = ?")) {
                            upd.setLong(1, progress);
                            upd.setInt(2, completed ? 1 : 0);
                            upd.setLong(3, now);
                            upd.setString(4, playerUuid.toString());
                            upd.setString(5, questId);
                            upd.setString(6, periodKey);
                            upd.executeUpdate();
                        }
                        return null;
                    }
                }
            }
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO player_quests (player_uuid, quest_id, period_key, progress, completed, claimed, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, 0, ?)")) {
                ins.setString(1, playerUuid.toString());
                ins.setString(2, questId);
                ins.setString(3, periodKey);
                ins.setLong(4, progress);
                ins.setInt(5, completed ? 1 : 0);
                ins.setLong(6, now);
                ins.executeUpdate();
            }
            return null;
        });
    }

    public boolean markQuestClaimedAtomic(UUID playerUuid, String questId, String periodKey) {
        return storage.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE player_quests SET claimed = 1, updated_at = ? " +
                            "WHERE player_uuid = ? AND quest_id = ? AND period_key = ? AND completed = 1 AND claimed = 0")) {
                ps.setLong(1, System.currentTimeMillis());
                ps.setString(2, playerUuid.toString());
                ps.setString(3, questId);
                ps.setString(4, periodKey);
                return ps.executeUpdate() == 1;
            }
        });
    }

    // ---- REWARD CLAIM LOCK (Daily / Weekly / Playtime / Achievements / BattlePass) ----
    public Set<String> loadClaimedRewards(UUID playerUuid) {
        return storage.query(conn -> {
            Set<String> out = new HashSet<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT reward_key, period_key FROM player_claimed_rewards WHERE player_uuid = ?")) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(rs.getString(1) + ":" + rs.getString(2));
                    }
                }
            }
            return out;
        });
    }

    public boolean tryClaimRewardAtomic(UUID playerUuid, String rewardKey, String periodKey) {
        return storage.inTransaction(conn -> {
            try (PreparedStatement sel = conn.prepareStatement(
                    "SELECT 1 FROM player_claimed_rewards WHERE player_uuid = ? AND reward_key = ? AND period_key = ?")) {
                sel.setString(1, playerUuid.toString());
                sel.setString(2, rewardKey);
                sel.setString(3, periodKey);
                try (ResultSet rs = sel.executeQuery()) {
                    if (rs.next()) return false;
                }
            }
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO player_claimed_rewards (player_uuid, reward_key, period_key, claimed_at) VALUES (?, ?, ?, ?)")) {
                ins.setString(1, playerUuid.toString());
                ins.setString(2, rewardKey);
                ins.setString(3, periodKey);
                ins.setLong(4, System.currentTimeMillis());
                return ins.executeUpdate() == 1;
            }
        });
    }

    // ---- SECURITY AUDIT LOG ----
    public void logSecurityEvent(UUID playerUuid, String playerName, String eventType, String severity, String details) {
        storage.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO security_audit_log (actor_uuid, actor_name, action_type, details, created_at) " +
                            "VALUES (?, ?, ?, ?, ?)")) {
                ps.setString(1, playerUuid != null ? playerUuid.toString() : null);
                ps.setString(2, playerName == null ? "UNKNOWN" : playerName);
                ps.setString(3, eventType + ":" + severity);
                ps.setString(4, details);
                ps.setLong(5, System.currentTimeMillis());
                ps.executeUpdate();
            }
            return null;
        });
    }
}
