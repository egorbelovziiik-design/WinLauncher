package ru.reallyworld.ultimate.core.storage.repository;

import ru.reallyworld.ultimate.core.api.CurrencyType;
import ru.reallyworld.ultimate.core.storage.StorageModule;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Atomic integer-only Economy Ledger Repository.
 * Every deposit, withdrawal, and P2P transfer runs inside a single SQL transaction,
 * verifies non-negative balances and overflow caps, and writes an immutable audit row.
 */
public final class EconomyRepository {
    public static final long MAX_BALANCE = 1_000_000_000_000L; // 1 Trillion cap

    public record EconomyResult(boolean success, long newBalance, String failureReason) {}
    public record TransferResult(boolean success, long senderBalance, long receiverBalance, long netReceived, String failureReason) {}

    private final StorageModule storage;

    public EconomyRepository(StorageModule storage) {
        this.storage = storage;
    }

    public long getBalance(UUID playerUuid, CurrencyType currency) {
        String col = currency.column();
        return storage.query(conn -> readBalance(conn, playerUuid, col));
    }

    public EconomyResult deposit(UUID playerUuid, CurrencyType currency, long amount, String txType, String details) {
        if (amount <= 0) {
            return new EconomyResult(false, 0L, "NON_POSITIVE_AMOUNT");
        }
        String col = currency.column();
        return storage.inTransaction(conn -> depositInTx(conn, playerUuid, currency, col, amount, txType, details));
    }

    public EconomyResult withdraw(UUID playerUuid, CurrencyType currency, long amount, String txType, String details) {
        if (amount <= 0) {
            return new EconomyResult(false, 0L, "NON_POSITIVE_AMOUNT");
        }
        String col = currency.column();
        return storage.inTransaction(conn -> withdrawInTx(conn, playerUuid, currency, col, amount, txType, details));
    }

    public EconomyResult setBalance(UUID playerUuid, CurrencyType currency, long newAmount, String actor, String details) {
        if (newAmount < 0 || newAmount > MAX_BALANCE) {
            return new EconomyResult(false, 0L, "OUT_OF_RANGE");
        }
        String col = currency.column();
        return storage.inTransaction(conn -> {
            long before = readBalance(conn, playerUuid, col);
            if (before < 0) {
                return new EconomyResult(false, 0L, "PLAYER_NOT_FOUND");
            }
            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE players SET " + col + " = ? WHERE uuid = ?")) {
                upd.setLong(1, newAmount);
                upd.setString(2, playerUuid.toString());
                if (upd.executeUpdate() != 1) {
                    return new EconomyResult(false, before, "UPDATE_FAILED");
                }
            }
            insertTxLog(conn, actor, playerUuid.toString(), currency, Math.abs(newAmount - before), 0L,
                    before, newAmount, "ADMIN_SET", details);
            return new EconomyResult(true, newAmount, null);
        });
    }

    public TransferResult transfer(UUID senderUuid, UUID receiverUuid, CurrencyType currency, long grossAmount, int feePercent, String details) {
        if (senderUuid.equals(receiverUuid)) {
            return new TransferResult(false, 0L, 0L, 0L, "SELF_TRANSFER");
        }
        if (grossAmount <= 0) {
            return new TransferResult(false, 0L, 0L, 0L, "NON_POSITIVE_AMOUNT");
        }
        long fee = (grossAmount * Math.max(0, Math.min(feePercent, 50))) / 100L;
        long net = grossAmount - fee;
        if (net <= 0) {
            return new TransferResult(false, 0L, 0L, 0L, "ZERO_AFTER_FEE");
        }
        String col = currency.column();
        return storage.inTransaction(conn -> {
            long senderBefore = readBalance(conn, senderUuid, col);
            long receiverBefore = readBalance(conn, receiverUuid, col);
            if (senderBefore < 0 || receiverBefore < 0) {
                return new TransferResult(false, Math.max(0, senderBefore), Math.max(0, receiverBefore), 0L, "PLAYER_NOT_FOUND");
            }
            if (senderBefore < grossAmount) {
                return new TransferResult(false, senderBefore, receiverBefore, 0L, "INSUFFICIENT_FUNDS");
            }
            if (receiverBefore > MAX_BALANCE - net) {
                return new TransferResult(false, senderBefore, receiverBefore, 0L, "RECEIVER_BALANCE_CAP");
            }
            try (PreparedStatement dec = conn.prepareStatement(
                    "UPDATE players SET " + col + " = " + col + " - ? WHERE uuid = ? AND " + col + " >= ?")) {
                dec.setLong(1, grossAmount);
                dec.setString(2, senderUuid.toString());
                dec.setLong(3, grossAmount);
                if (dec.executeUpdate() != 1) {
                    throw new IllegalStateException("Concurrent balance modification aborted transfer");
                }
            }
            try (PreparedStatement inc = conn.prepareStatement(
                    "UPDATE players SET " + col + " = " + col + " + ? WHERE uuid = ? AND " + col + " <= ?")) {
                inc.setLong(1, net);
                inc.setString(2, receiverUuid.toString());
                inc.setLong(3, MAX_BALANCE - net);
                if (inc.executeUpdate() != 1) {
                    throw new IllegalStateException("Receiver overflow aborted transfer");
                }
            }
            long senderAfter = senderBefore - grossAmount;
            long receiverAfter = receiverBefore + net;
            insertTxLog(conn, senderUuid.toString(), receiverUuid.toString(), currency, grossAmount, fee,
                    senderBefore, senderAfter, "PAY_TRANSFER", details);
            return new TransferResult(true, senderAfter, receiverAfter, net, null);
        });
    }

    public EconomyResult depositInTx(Connection conn, UUID playerUuid, CurrencyType currency, String col, long amount, String txType, String details) throws SQLException {
        long before = readBalance(conn, playerUuid, col);
        if (before < 0) {
            return new EconomyResult(false, 0L, "PLAYER_NOT_FOUND");
        }
        if (before > MAX_BALANCE - amount) {
            return new EconomyResult(false, before, "MAX_BALANCE_EXCEEDED");
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE players SET " + col + " = " + col + " + ? WHERE uuid = ? AND " + col + " <= ?")) {
            ps.setLong(1, amount);
            ps.setString(2, playerUuid.toString());
            ps.setLong(3, MAX_BALANCE - amount);
            if (ps.executeUpdate() != 1) {
                return new EconomyResult(false, before, "CONCURRENT_OVERFLOW");
            }
        }
        long after = before + amount;
        insertTxLog(conn, "SYSTEM", playerUuid.toString(), currency, amount, 0L, before, after, txType, details);
        return new EconomyResult(true, after, null);
    }

    public EconomyResult withdrawInTx(Connection conn, UUID playerUuid, CurrencyType currency, String col, long amount, String txType, String details) throws SQLException {
        long before = readBalance(conn, playerUuid, col);
        if (before < 0) {
            return new EconomyResult(false, 0L, "PLAYER_NOT_FOUND");
        }
        if (before < amount) {
            return new EconomyResult(false, before, "INSUFFICIENT_FUNDS");
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE players SET " + col + " = " + col + " - ? WHERE uuid = ? AND " + col + " >= ?")) {
            ps.setLong(1, amount);
            ps.setString(2, playerUuid.toString());
            ps.setLong(3, amount);
            if (ps.executeUpdate() != 1) {
                return new EconomyResult(false, before, "INSUFFICIENT_FUNDS");
            }
        }
        long after = before - amount;
        insertTxLog(conn, playerUuid.toString(), "SYSTEM", currency, amount, 0L, before, after, txType, details);
        return new EconomyResult(true, after, null);
    }

    private long readBalance(Connection conn, UUID playerUuid, String col) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT " + col + " FROM players WHERE uuid = ?")) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1L;
            }
        }
    }

    public void insertTxLog(
            Connection conn,
            String actorUuid,
            String targetUuid,
            CurrencyType currency,
            long amount,
            long fee,
            long balanceBefore,
            long balanceAfter,
            String txType,
            String details
    ) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO economy_transactions " +
                        "(tx_uuid, actor_uuid, target_uuid, currency, operation, amount, balance_before, balance_after, reason, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, actorUuid);
            ps.setString(3, targetUuid);
            ps.setString(4, currency.name());
            ps.setString(5, txType);
            ps.setLong(6, amount);
            ps.setLong(7, balanceBefore);
            ps.setLong(8, balanceAfter);
            ps.setString(9, fee > 0 ? details + " (fee=" + fee + ")" : details);
            ps.setLong(10, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }
}
