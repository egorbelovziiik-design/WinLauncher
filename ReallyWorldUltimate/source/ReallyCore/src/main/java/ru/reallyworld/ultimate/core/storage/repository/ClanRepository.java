package ru.reallyworld.ultimate.core.storage.repository;

import ru.reallyworld.ultimate.core.api.CurrencyType;
import ru.reallyworld.ultimate.core.storage.StorageModule;
import ru.reallyworld.ultimate.core.storage.model.ClanData;
import ru.reallyworld.ultimate.core.storage.model.ClanData.ClanMember;
import ru.reallyworld.ultimate.core.storage.model.ClanData.ClanRole;

import java.sql.*;
import java.util.*;

public final class ClanRepository {
    private final StorageModule storage;

    public ClanRepository(StorageModule storage) {
        this.storage = storage;
    }

    public List<ClanData> loadAllClans() {
        return storage.query(conn -> {
            Map<Long, List<ClanMember>> membersMap = new HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM clan_members");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long clanId = rs.getLong("clan_id");
                    membersMap.computeIfAbsent(clanId, k -> new ArrayList<>()).add(new ClanMember(
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getString("player_name"),
                            clanId,
                            ClanRole.valueOf(rs.getString("role")),
                            rs.getLong("contributed_coins"),
                            rs.getLong("joined_at")
                    ));
                }
            }

            Map<Long, Map<Long, String>> relationsMap = new HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM clan_relations");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long cId = rs.getLong("clan_id");
                    long tId = rs.getLong("target_clan_id");
                    relationsMap.computeIfAbsent(cId, k -> new HashMap<>()).put(tId, rs.getString("relation_type"));
                }
            }

            List<ClanData> out = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM clans ORDER BY rating_points DESC");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    out.add(mapClan(rs,
                            membersMap.getOrDefault(id, List.of()),
                            relationsMap.getOrDefault(id, Map.of())));
                }
            }
            return out;
        });
    }

    public ClanData createClanAtomic(UUID leaderUuid, String leaderName, String name, String tag, long costCoins) {
        long now = System.currentTimeMillis();
        return storage.inTransaction(conn -> {
            try (PreparedStatement chk = conn.prepareStatement(
                    "SELECT id FROM clans WHERE LOWER(name) = LOWER(?) OR LOWER(tag) = LOWER(?)")) {
                chk.setString(1, name);
                chk.setString(2, tag);
                try (ResultSet rs = chk.executeQuery()) {
                    if (rs.next()) {
                        throw new IllegalArgumentException("CLAN_EXISTS");
                    }
                }
            }
            if (costCoins > 0) {
                EconomyRepository.EconomyResult res = storage.economy().withdrawInTx(
                        conn, leaderUuid, CurrencyType.COINS, CurrencyType.COINS.column(),
                        costCoins, "CLAN_CREATE", "Created clan [" + tag + "] " + name
                );
                if (!res.success()) {
                    throw new IllegalStateException("INSUFFICIENT_FUNDS");
                }
            }
            long clanId;
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO clans (name, tag, description, leader_uuid, bank_coins, rating_points, created_at) " +
                            "VALUES (?, ?, 'Боевой клан ReallyWorld', ?, 0, 100, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ins.setString(1, name);
                ins.setString(2, tag);
                ins.setString(3, leaderUuid.toString());
                ins.setLong(4, now);
                ins.executeUpdate();
                try (ResultSet keys = ins.getGeneratedKeys()) {
                    clanId = keys.next() ? keys.getLong(1) : -1L;
                }
            }
            ClanMember leaderMember = new ClanMember(leaderUuid, leaderName, clanId, ClanRole.LEADER, 0L, now);
            try (PreparedStatement mem = conn.prepareStatement(
                    "INSERT INTO clan_members (player_uuid, player_name, clan_id, role, contributed_coins, joined_at) " +
                            "VALUES (?, ?, ?, 'LEADER', 0, ?)")) {
                mem.setString(1, leaderUuid.toString());
                mem.setString(2, leaderName);
                mem.setLong(3, clanId);
                mem.setLong(4, now);
                mem.executeUpdate();
            }
            return new ClanData(
                    clanId, name, tag, "Боевой клан ReallyWorld", leaderUuid,
                    0L, 100, 0, 0, 0, false, null, null, null, null, null, null,
                    now, List.of(leaderMember), Map.of()
            );
        });
    }

    public void addMember(long clanId, UUID playerUuid, String playerName) {
        long now = System.currentTimeMillis();
        storage.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO clan_members (player_uuid, player_name, clan_id, role, contributed_coins, joined_at) " +
                            "VALUES (?, ?, ?, 'MEMBER', 0, ?)")) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, playerName);
                ps.setLong(3, clanId);
                ps.setLong(4, now);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void removeMember(UUID playerUuid) {
        storage.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM clan_members WHERE player_uuid = ?")) {
                ps.setString(1, playerUuid.toString());
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void updateMemberRole(UUID playerUuid, ClanRole newRole) {
        storage.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE clan_members SET role = ? WHERE player_uuid = ?")) {
                ps.setString(1, newRole.name());
                ps.setString(2, playerUuid.toString());
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void disbandClan(long clanId) {
        storage.inTransaction(conn -> {
            try (PreparedStatement rel = conn.prepareStatement(
                    "DELETE FROM clan_relations WHERE clan_id = ? OR target_clan_id = ?")) {
                rel.setLong(1, clanId);
                rel.setLong(2, clanId);
                rel.executeUpdate();
            }
            try (PreparedStatement mem = conn.prepareStatement("DELETE FROM clan_members WHERE clan_id = ?")) {
                mem.setLong(1, clanId);
                mem.executeUpdate();
            }
            try (PreparedStatement prog = conn.prepareStatement("DELETE FROM clan_progress WHERE clan_id = ?")) {
                prog.setLong(1, clanId);
                prog.executeUpdate();
            }
            try (PreparedStatement cl = conn.prepareStatement("DELETE FROM clans WHERE id = ?")) {
                cl.setLong(1, clanId);
                cl.executeUpdate();
            }
            return null;
        });
    }

    public long depositToBank(long clanId, UUID playerUuid, long amount, long maxBankCoins) {
        return storage.inTransaction(conn -> {
            long currentBank;
            try (PreparedStatement sel = conn.prepareStatement("SELECT bank_coins FROM clans WHERE id = ?")) {
                sel.setLong(1, clanId);
                try (ResultSet rs = sel.executeQuery()) {
                    if (!rs.next()) throw new IllegalStateException("CLAN_NOT_FOUND");
                    currentBank = rs.getLong(1);
                }
            }
            if (currentBank + amount > maxBankCoins) {
                throw new IllegalStateException("BANK_CAP_EXCEEDED");
            }
            EconomyRepository.EconomyResult res = storage.economy().withdrawInTx(
                    conn, playerUuid, CurrencyType.COINS, CurrencyType.COINS.column(),
                    amount, "CLAN_DEPOSIT", "Clan #" + clanId + " deposit"
            );
            if (!res.success()) {
                throw new IllegalStateException("INSUFFICIENT_FUNDS");
            }
            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE clans SET bank_coins = bank_coins + ? WHERE id = ?")) {
                upd.setLong(1, amount);
                upd.setLong(2, clanId);
                upd.executeUpdate();
            }
            try (PreparedStatement mem = conn.prepareStatement(
                    "UPDATE clan_members SET contributed_coins = contributed_coins + ? WHERE player_uuid = ?")) {
                mem.setLong(1, amount);
                mem.setString(2, playerUuid.toString());
                mem.executeUpdate();
            }
            return currentBank + amount;
        });
    }

    public long withdrawFromBank(long clanId, UUID playerUuid, long amount) {
        return storage.inTransaction(conn -> {
            try (PreparedStatement dec = conn.prepareStatement(
                    "UPDATE clans SET bank_coins = bank_coins - ? WHERE id = ? AND bank_coins >= ?")) {
                dec.setLong(1, amount);
                dec.setLong(2, clanId);
                dec.setLong(3, amount);
                if (dec.executeUpdate() != 1) {
                    throw new IllegalStateException("INSUFFICIENT_CLAN_BANK");
                }
            }
            EconomyRepository.EconomyResult res = storage.economy().depositInTx(
                    conn, playerUuid, CurrencyType.COINS, CurrencyType.COINS.column(),
                    amount, "CLAN_WITHDRAW", "Clan #" + clanId + " withdrawal"
            );
            if (!res.success()) {
                throw new IllegalStateException("PLAYER_BALANCE_CAP");
            }
            try (PreparedStatement sel = conn.prepareStatement("SELECT bank_coins FROM clans WHERE id = ?")) {
                sel.setLong(1, clanId);
                try (ResultSet rs = sel.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        });
    }

    public void updateClanHome(long clanId, String world, double x, double y, double z, float yaw, float pitch) {
        storage.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE clans SET home_world=?, home_x=?, home_y=?, home_z=?, home_yaw=?, home_pitch=? WHERE id=?")) {
                ps.setString(1, world);
                ps.setDouble(2, x);
                ps.setDouble(3, y);
                ps.setDouble(4, z);
                ps.setFloat(5, yaw);
                ps.setFloat(6, pitch);
                ps.setLong(7, clanId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void updateClanStats(long clanId, int ratingPoints, int kills, int deaths, int captures, boolean friendlyFire) {
        storage.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE clans SET rating_points=?, kills=?, deaths=?, captures=?, pvp_friendly_fire=? WHERE id=?")) {
                ps.setInt(1, Math.max(0, ratingPoints));
                ps.setInt(2, kills);
                ps.setInt(3, deaths);
                ps.setInt(4, captures);
                ps.setInt(5, friendlyFire ? 1 : 0);
                ps.setLong(6, clanId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void setRelation(long clanId, long targetClanId, String relationType) {
        storage.inTransaction(conn -> {
            try (PreparedStatement del = conn.prepareStatement(
                    "DELETE FROM clan_relations WHERE clan_id = ? AND target_clan_id = ?")) {
                del.setLong(1, clanId);
                del.setLong(2, targetClanId);
                del.executeUpdate();
            }
            if (relationType != null && !relationType.isBlank()) {
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO clan_relations (clan_id, target_clan_id, relation_type, created_at) VALUES (?, ?, ?, ?)")) {
                    ins.setLong(1, clanId);
                    ins.setLong(2, targetClanId);
                    ins.setString(3, relationType);
                    ins.setLong(4, System.currentTimeMillis());
                    ins.executeUpdate();
                }
            }
            return null;
        });
    }

    private ClanData mapClan(ResultSet rs, List<ClanMember> members, Map<Long, String> relations) throws SQLException {
        String hw = rs.getString("home_world");
        Double hx = hw != null ? rs.getDouble("home_x") : null;
        Double hy = hw != null ? rs.getDouble("home_y") : null;
        Double hz = hw != null ? rs.getDouble("home_z") : null;
        Float hyaw = hw != null ? rs.getFloat("home_yaw") : null;
        Float hpitch = hw != null ? rs.getFloat("home_pitch") : null;
        return new ClanData(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("tag"),
                rs.getString("description"),
                UUID.fromString(rs.getString("leader_uuid")),
                rs.getLong("bank_coins"),
                rs.getInt("rating_points"),
                rs.getInt("kills"),
                rs.getInt("deaths"),
                rs.getInt("captures"),
                rs.getInt("pvp_friendly_fire") != 0,
                hw, hx, hy, hz, hyaw, hpitch,
                rs.getLong("created_at"),
                members,
                relations
        );
    }
}
