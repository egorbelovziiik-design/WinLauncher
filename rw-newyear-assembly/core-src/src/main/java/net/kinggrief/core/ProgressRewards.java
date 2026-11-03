package net.kinggrief.core;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Daily gifts and battle-pass rewards. Their local markers and currencies commit together. */
final class ProgressRewards {
    static final int BP_LEVELS = 15;
    static final long DAILY_INTERVAL = 86_400_000L;
    private static final long STREAK_WINDOW = 48L * 60L * 60L * 1000L;
    private static final int[] BP_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28};

    enum Status { AWARDED, WAIT, ALREADY, NOT_READY, FAILED }
    static final class Result {
        final Status status;
        final int count, coins, relics, snow, streak;
        final long readyAt;
        final boolean pointsSynced;
        Result(Status status, int count, int coins, int relics, int snow, int streak, long readyAt, boolean pointsSynced) {
            this.status = status; this.count = count; this.coins = coins; this.relics = relics;
            this.snow = snow; this.streak = streak; this.readyAt = readyAt; this.pointsSynced = pointsSynced;
        }
        static Result of(Status status) { return new Result(status, 0, 0, 0, 0, 0, 0L, true); }
    }
    static final class DailyStatus {
        final long readyAt;
        final int streak, nextStreak, bonusCoins, bonusSnow;
        DailyStatus(long readyAt, int streak, int nextStreak) {
            this.readyAt = readyAt; this.streak = streak; this.nextStreak = nextStreak;
            this.bonusCoins = bonusCoins(nextStreak); this.bonusSnow = bonusSnow(nextStreak);
        }
    }

    private final KingGriefCore plugin;
    ProgressRewards(KingGriefCore plugin) { this.plugin = plugin; }

    static int bpSlot(int level) { return level >= 1 && level <= BP_LEVELS ? BP_SLOTS[level - 1] : -1; }
    static int bpLevelAt(int slot) {
        for (int i = 0; i < BP_SLOTS.length; i++) if (BP_SLOTS[i] == slot) return i + 1;
        return -1;
    }
    static long bpRequired(int level) {
        if (level < 1 || level > BP_LEVELS) return Long.MAX_VALUE;
        if (level <= 7) return level * 10L; // preserve the original seven tiers
        long n = level - 7L;
        return 70L + 10L * n * (n + 1L);
    }
    static long bpScore(DataStore store, String name) {
        return (long) store.getMinedBlocks(name) / 20L + (long) store.getKills(name) * 5L;
    }
    static int bonusCoins(int streak) {
        if (streak <= 0) return 0;
        if (streak % 30 == 0) return 60_000;
        if (streak % 14 == 0) return 30_000;
        if (streak % 7 == 0) return 15_000;
        if (streak % 3 == 0) return 5_000;
        return 0;
    }
    static int bonusSnow(int streak) {
        if (streak <= 0) return 0;
        if (streak % 30 == 0) return 20;
        if (streak % 14 == 0) return 10;
        if (streak % 7 == 0) return 5;
        if (streak % 3 == 0) return 2;
        return 0;
    }
    private static int nextStreak(int streak, long last, long at) {
        if (streak <= 0 || last <= 0 || at < last || at - last > STREAK_WINDOW) return 1;
        return streak == Integer.MAX_VALUE ? Integer.MAX_VALUE : streak + 1;
    }
    private static String base(Player player) { return "players." + player.getName().toLowerCase(Locale.ROOT); }
    private static long secondsRoundedUp(long millis) { return millis / 1000L + (millis % 1000L == 0 ? 0L : 1L); }

    boolean allowed(Player player) {
        return player != null && player.isOnline() && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR
                && plugin.getStore().isAuthenticated(player) && !plugin.getStore().isQuickFrozen(player)
                && !plugin.getModeration().isFrozenOrAscending(player);
    }
    private boolean checkAllowed(Player player) {
        if (allowed(player)) return true;
        if (player != null) player.sendMessage(ColorUtil.c("&cПолучение награды недоступно: войдите в аккаунт и завершите проверку."));
        return false;
    }
    DailyStatus dailyStatus(Player player, long now) throws Exception {
        return plugin.getStore().inspect(data -> {
            String root = base(player);
            long ready = Math.max(0L, data.getLong(root + ".dailyReady", 0L));
            long last = Math.max(0L, data.getLong(root + ".dailyLastClaim", 0L));
            int streak = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, data.getLong(root + ".dailyStreak", 0L)));
            return new DailyStatus(ready, streak, nextStreak(streak, last, Math.max(now, ready)));
        });
    }
    void showDailyStatus(Player player) {
        try {
            long now = System.currentTimeMillis();
            DailyStatus status = dailyStatus(player, now);
            String wait = status.readyAt > now ? "через " + ColorUtil.fmtTime(secondsRoundedUp(status.readyAt - now)) : "уже доступен";
            player.sendMessage(ColorUtil.c("&#80D8FF🎁 Ежедневный подарок: &f" + wait + " &8| &7серия: &f" + status.streak));
            player.sendMessage(ColorUtil.c("&7Следующее получение: &a20 000$ &8• &b15 риликов &8• &b5 снежинок"
                    + (status.bonusCoins > 0 ? " &8+ &eбонус серии " + status.nextStreak + ": " + status.bonusCoins + "$ и " + status.bonusSnow + " ❄" : "")));
            player.sendMessage(ColorUtil.c("&8Серия сохраняется, если получить следующий подарок не позднее 48 часов после предыдущего."));
        } catch (Exception error) { player.sendMessage(ColorUtil.c("&cНе удалось прочитать статус подарка.")); }
    }
    void dailyCommand(Player player, String[] args) {
        if (args.length == 1 && "status".equalsIgnoreCase(args[0])) { showDailyStatus(player); return; }
        if (args.length > 1 || (args.length == 1 && !"claim".equalsIgnoreCase(args[0]))) {
            player.sendMessage(ColorUtil.c("&e/daily &7или &e/daily status &7— ежедневный подарок")); return;
        }
        if (!checkAllowed(player)) return;
        long now = System.currentTimeMillis();
        Result result = claimDaily(player, now);
        if (result.status == Status.WAIT) {
            long wait = Math.max(0L, result.readyAt - now);
            player.sendMessage(ColorUtil.c("&#FFD740🎅 Следующий подарок через &f" + ColorUtil.fmtTime(secondsRoundedUp(wait)))); return;
        }
        if (result.status != Status.AWARDED) {
            player.sendMessage(ColorUtil.c("&cПодарок не сохранён. Начисления не выполнены; проверьте журнал сервера.")); return;
        }
        player.sendMessage(ColorUtil.c("&#80D8FF🎅 Подарок: &a+" + ColorUtil.fmtMoney(result.coins) + "$ &8• &b+"
                + result.relics + " риликов &8• &b+" + result.snow + " снежинок &8| &7серия: &f" + result.streak));
        if (!result.pointsSynced) pointsWarning(player);
        ColorUtil.spawnFirework(player.getLocation());
        plugin.getHud().updatePlayerHud(player);
    }
    private static void pointsWarning(Player player) {
        player.sendMessage(ColorUtil.c("&eРилики записаны в Core, но PlayerPoints не подтвердил синхронизацию. Сообщите администрации."));
    }

    Result claimDaily(Player player, long now) {
        DataStore store = plugin.getStore(); String name = player.getName(), root = base(player);
        synchronized (store) {
            try {
                DailyStatus before = dailyStatus(player, now);
                if (before.readyAt > now) return new Result(Status.WAIT, 0, 0, 0, 0, before.streak, before.readyAt, true);
                if (now <= 0L || now > Long.MAX_VALUE - DAILY_INTERVAL) return Result.of(Status.FAILED);
                long last = store.inspect(data -> data.getLong(root + ".dailyLastClaim", 0L));
                if (last < 0 || last > now) return Result.of(Status.FAILED);
                int streak = nextStreak(before.streak, last, now);
                int coins = 20_000 + bonusCoins(streak), relics = 15, snow = 5 + bonusSnow(streak);
                Balances balances = balances(player, coins, relics, snow);
                if (balances == null) return Result.of(Status.FAILED);
                store.transact(data -> {
                    if (data.getLong(root + ".dailyReady", 0L) != before.readyAt
                            || data.getLong(root + ".dailyLastClaim", 0L) != last
                            || data.getLong(root + ".dailyStreak", 0L) != before.streak)
                        throw new IOException("daily state changed or is invalid");
                    balances.apply(data, root);
                    data.set(root + ".dailyReady", now + DAILY_INTERVAL);
                    data.set(root + ".dailyLastClaim", now);
                    data.set(root + ".dailyStreak", streak);
                    return null;
                });
                return new Result(Status.AWARDED, 1, coins, relics, snow, streak, now + DAILY_INTERVAL, syncPoints(player));
            } catch (Exception error) {
                plugin.getLogger().warning("[Daily] Reward was not committed for " + name + ": " + error.getMessage());
                return Result.of(Status.FAILED);
            }
        }
    }
    Result claimBp(Player player, int level) {
        DataStore store = plugin.getStore(); String name = player.getName(), root = base(player);
        synchronized (store) {
            try {
                long score = bpScore(store, name);
                List<Integer> levels = new ArrayList<Integer>();
                if (level == 0) {
                    for (int i = 1; i <= BP_LEVELS; i++)
                        if (score >= bpRequired(i) && !store.hasClaimedBp(name, i)) levels.add(Integer.valueOf(i));
                    if (levels.isEmpty()) return Result.of(Status.NOT_READY);
                } else {
                    if (level < 1 || level > BP_LEVELS) return Result.of(Status.NOT_READY);
                    if (store.hasClaimedBp(name, level)) return Result.of(Status.ALREADY);
                    if (score < bpRequired(level)) return Result.of(Status.NOT_READY);
                    levels.add(Integer.valueOf(level));
                }
                int coins = 0, relics = 0, snow = 0;
                for (Integer value : levels) { int n = value.intValue(); coins += n * 15_000; relics += n * 15; snow += n * 5; }
                Balances balances = balances(player, coins, relics, snow);
                if (balances == null) return Result.of(Status.FAILED);
                store.transact(data -> {
                    if (bpScore(store, name) != score) throw new IOException("battle-pass progress changed");
                    for (Integer value : levels) {
                        String marker = root + ".bp." + value;
                        if (data.getBoolean(marker, false)) throw new IOException("battle-pass reward already claimed");
                        data.set(marker, true);
                    }
                    balances.apply(data, root);
                    return null;
                });
                return new Result(Status.AWARDED, levels.size(), coins, relics, snow, 0, 0L, syncPoints(player));
            } catch (Exception error) {
                plugin.getLogger().warning("[BattlePass] Reward was not committed for " + name + ": " + error.getMessage());
                return Result.of(Status.FAILED);
            }
        }
    }
    void claimBpAndNotify(Player player, int level) {
        if (!checkAllowed(player)) return;
        Result result = claimBp(player, level);
        if (result.status == Status.ALREADY) { player.sendMessage(ColorUtil.c("&7Этот уровень уже получен.")); return; }
        if (result.status == Status.NOT_READY) { player.sendMessage(ColorUtil.c("&eНет доступных неполученных уровней боевого пропуска.")); return; }
        if (result.status != Status.AWARDED) {
            player.sendMessage(ColorUtil.c("&cНаграды не сохранены. Начисления не выполнены; проверьте журнал сервера.")); return;
        }
        player.sendMessage(ColorUtil.c("&#00E676🎄 Уровней БП получено: &f" + result.count + " &8| &a+"
                + ColorUtil.fmtMoney(result.coins) + "$ &8| &b+" + result.relics + " риликов &8| &b+" + result.snow + " ❄"));
        if (!result.pointsSynced) pointsWarning(player);
        ColorUtil.sound(player, "entity.player.levelup", 1f, 1.2f);
        plugin.getHud().updatePlayerHud(player);
    }
    void showBpStatus(Player player) {
        DataStore store = plugin.getStore(); String name = player.getName();
        long score = bpScore(store, name), next = Long.MAX_VALUE; int claimed = 0, available = 0;
        for (int i = 1; i <= BP_LEVELS; i++) {
            if (store.hasClaimedBp(name, i)) claimed++;
            else if (score >= bpRequired(i)) available++;
            else next = Math.min(next, bpRequired(i) - score);
        }
        player.sendMessage(ColorUtil.c("&#80D8FF🎄 БП: &f" + score + " очков &8| &7получено: &f" + claimed + "/" + BP_LEVELS + " &8| &7готово: &a" + available));
        if (next != Long.MAX_VALUE) player.sendMessage(ColorUtil.c("&7До следующего уровня: &f" + next + " очков. &8• &f/bp claim"));
        else if (available > 0) player.sendMessage(ColorUtil.c("&7Заберите готовые уровни: &f/bp claim"));
        else player.sendMessage(ColorUtil.c("&7Все уровни уже получены."));
    }

    /** Captures valid local and confirmed external balances before one checked local commit. */
    private Balances balances(Player player, int coinsAward, int relicAward, int snowAward) throws Exception {
        DataStore store = plugin.getStore(); String root = base(player);
        long localRelics = store.inspect(data -> data.getLong(root + ".relics", 50L));
        long snow = store.inspect(data -> data.getLong(root + ".snowflakes", 0L));
        double coins = store.getCoins(player.getName()), afterCoins = coins + coinsAward;
        int effectiveRelics = store.getVerifiedAwardRelics(player.getName(), player.getUniqueId());
        if (localRelics < 0 || localRelics > Integer.MAX_VALUE || snow < 0 || snow > Integer.MAX_VALUE
                || effectiveRelics < 0 || (long) effectiveRelics + relicAward > Integer.MAX_VALUE
                || snow + snowAward > Integer.MAX_VALUE || !Double.isFinite(coins) || coins < 0
                || !Double.isFinite(afterCoins) || afterCoins <= coins) {
            plugin.getLogger().warning("[Rewards] Invalid balances, overflow, or unavailable PlayerPoints for " + player.getName() + "; reward refused.");
            return null;
        }
        return new Balances(coins, localRelics, snow, afterCoins, effectiveRelics + relicAward, (int) snow + snowAward,
                plugin.getConfig().getDouble("starting-coins", 2500.0));
    }
    private static final class Balances {
        final double beforeCoins, afterCoins, initialCoins;
        final long beforeRelics, beforeSnow;
        final int afterRelics, afterSnow;
        Balances(double beforeCoins, long beforeRelics, long beforeSnow, double afterCoins, int afterRelics, int afterSnow, double initialCoins) {
            this.beforeCoins = beforeCoins; this.beforeRelics = beforeRelics; this.beforeSnow = beforeSnow;
            this.afterCoins = afterCoins; this.afterRelics = afterRelics; this.afterSnow = afterSnow; this.initialCoins = initialCoins;
        }
        void apply(org.bukkit.configuration.file.YamlConfiguration data, String root) throws IOException {
            if (Double.compare(data.getDouble(root + ".coins", initialCoins), beforeCoins) != 0
                    || data.getLong(root + ".relics", 50L) != beforeRelics || data.getLong(root + ".snowflakes", 0L) != beforeSnow)
                throw new IOException("balances changed before commit");
            data.set(root + ".coins", afterCoins);
            data.set(root + ".relics", afterRelics);
            data.set(root + ".snowflakes", afterSnow);
        }
    }
    private boolean syncPoints(Player player) {
        try { return plugin.getStore().syncAwardRelics(player.getName(), player.getUniqueId()); }
        catch (Exception | LinkageError error) {
            plugin.getLogger().warning("[Rewards] Local reward committed; PlayerPoints sync failed for " + player.getName() + ": " + error.getMessage());
            return false;
        }
    }
}
