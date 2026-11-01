package net.kinggrief.core;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Pure, main-thread tree-season operations; callers commit them with DataStore.transact. */
final class TreeSeasonStore {
    static final int[] GOALS = {30, 100, 300};
    static final int[] COINS = {5000, 15000, 35000};
    static final int DAILY_LIMIT = 10;
    static final int MAX_ARCHIVES = 8;
    private static final int MAX_DONORS = 300;
    private static final int MAX_USED_IDS = 256;
    private static final String ROOT = "winterTree";
    private static final String HISTORY = ROOT + ".history";
    private static final String USED = ROOT + ".usedSeasonIds";
    private static final String DAILY = ROOT + ".dailyLedger";
    private static final String LEGACY = "legacy";

    static final class View {
        final String id, root;
        final int total;
        final int[] goals, coins;
        final boolean archived, legendary, title;
        final long closedAt;
        View(String id, String root, int total, int[] goals, int[] coins,
             boolean archived, boolean legendary, boolean title, long closedAt) {
            this.id = id; this.root = root; this.total = total;
            this.goals = goals; this.coins = coins; this.archived = archived;
            this.legendary = legendary; this.title = title; this.closedAt = closedAt;
        }
    }
    static final class Donor {
        final int total, joinedAt;
        final boolean[] claimed;
        Donor(int total, int joinedAt, boolean[] claimed) {
            this.total = total; this.joinedAt = joinedAt; this.claimed = claimed;
        }
    }
    static final class Payment {
        final int coins, stages;
        final boolean key, title;
        Payment(int coins, int stages, boolean key, boolean title) {
            this.coins = coins; this.stages = stages; this.key = key; this.title = title;
        }
    }
    static final class Rotation {
        final String previous, next;
        final boolean archived;
        Rotation(String previous, String next, boolean archived) {
            this.previous = previous; this.next = next; this.archived = archived;
        }
    }

    private TreeSeasonStore() {}

    static boolean validId(String id) { return id != null && id.matches("[a-z0-9_-]{1,24}"); }
    static String normalizeId(String raw) {
        String id = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        if (!validId(id)) throw new IllegalArgumentException("Название сезона: 1–24 латинские буквы, цифры, _ или -.");
        return id;
    }
    static String currentId(YamlConfiguration data) throws IOException {
        String id = data.getString(ROOT + ".season", LEGACY);
        if (!validId(id)) throw new IOException("invalid current tree season");
        return id;
    }
    private static int number(ConfigurationSection data, String path, int fallback, int min, int max) throws IOException {
        Object value = data.get(path);
        if (value == null) return fallback;
        if (!(value instanceof Number)) throw new IOException("invalid numeric field: " + path);
        Number n = (Number) value;
        double d = n.doubleValue();
        long l = n.longValue();
        if (!Double.isFinite(d) || d != (double) l || l < min || l > max) throw new IOException("out-of-range field: " + path);
        return (int) l;
    }
    private static boolean bool(ConfigurationSection data, String path, boolean fallback) throws IOException {
        Object value = data.get(path);
        if (value == null) return fallback;
        if (!(value instanceof Boolean)) throw new IOException("invalid boolean field: " + path);
        return ((Boolean) value).booleanValue();
    }
    private static int[] policy(YamlConfiguration data, String path, boolean goals) throws IOException {
        List<?> list = data.getList(path);
        if (list == null || list.size() != GOALS.length) throw new IOException("missing tree season policy: " + path);
        int[] values = new int[GOALS.length];
        int prior = 0;
        long total = 0;
        for (int i = 0; i < values.length; i++) {
            Object value = list.get(i);
            if (!(value instanceof Number)) throw new IOException("invalid tree season policy: " + path);
            Number n = (Number) value;
            double d = n.doubleValue(); long l = n.longValue();
            long limit = goals ? 1_000_000L : 100_000_000L;
            if (!Double.isFinite(d) || d != (double) l || l <= 0 || l > limit || (goals && l <= prior)) {
                throw new IOException("invalid tree season policy: " + path);
            }
            values[i] = (int) l; prior = values[i]; total += l;
        }
        if (!goals && total > Integer.MAX_VALUE) throw new IOException("tree reward overflow");
        return values;
    }
    static View current(YamlConfiguration data) throws IOException {
        // Never replace an unexpected scalar section with a new map during a
        // later transaction: malformed legacy data requires admin inspection.
        sectionOrNull(data, ROOT);
        sectionOrNull(data, ROOT + ".donors");
        sectionOrNull(data, ROOT + ".lifetime");
        sectionOrNull(data, HISTORY);
        sectionOrNull(data, USED);
        sectionOrNull(data, DAILY);
        ConfigurationSection usedToday = sectionOrNull(data, DAILY + ".used");
        String ledgerDay = storedDay(data, DAILY + ".day");
        if (ledgerDay == null && usedToday != null && !usedToday.getKeys(false).isEmpty()) {
            throw new IOException("daily tree ledger has no date");
        }
        String id = currentId(data);
        int total = number(data, ROOT + ".total", 0, 0, GOALS[GOALS.length - 1]);
        return new View(id, ROOT, total, GOALS.clone(), COINS.clone(), false, true, true, 0L);
    }
    static View resolve(YamlConfiguration data, String requested) throws IOException {
        View current = current(data);
        if (requested == null || requested.equals(current.id)) return current;
        if (!validId(requested)) throw new IllegalArgumentException("Некорректное название сезона.");
        String root = HISTORY + "." + requested;
        if (!data.isConfigurationSection(root)) throw new IllegalArgumentException("Сезон не найден: " + requested);
        if (number(data, root + ".policy.version", 0, 0, 1) != 1) throw new IOException("unknown tree season policy");
        int[] goals = policy(data, root + ".policy.goals", true);
        int[] coins = policy(data, root + ".policy.coins", false);
        int total = number(data, root + ".total", 0, 0, goals[goals.length - 1]);
        boolean legendary = bool(data, root + ".policy.legendary", false);
        boolean title = bool(data, root + ".policy.title", false);
        long closed = data.getLong(root + ".closedAt", 0L);
        if (closed <= 0 || !data.isBoolean(root + ".policy.legendary") || !data.isBoolean(root + ".policy.title")) {
            throw new IOException("invalid tree season metadata");
        }
        return new View(requested, root, total, goals, coins, true, legendary, title, closed);
    }
    static int stage(View view) {
        int stage = 0;
        for (int goal : view.goals) if (view.total >= goal) stage++;
        return stage;
    }
    static Donor donor(YamlConfiguration data, View view, String name) throws IOException {
        if (!validName(name)) throw new IOException("invalid donor name");
        String path = view.root + ".donors." + name;
        if (data.contains(path) && !data.isConfigurationSection(path)) throw new IOException("invalid donor section");
        sectionOrNull(data, path + ".claimed");
        int count = number(data, path + ".total", 0, 0, view.total);
        int joined = number(data, path + ".joinedAt", 0, 0, view.goals[view.goals.length - 1]);
        if (count > 0 && joined >= view.total) throw new IOException("invalid donor join position");
        boolean[] claimed = new boolean[view.goals.length];
        for (int i = 0; i < claimed.length; i++) claimed[i] = bool(data, path + ".claimed." + (i + 1), false);
        return new Donor(count, joined, claimed);
    }
    private static boolean validName(String name) { return name != null && name.matches("[a-z0-9_]{1,16}"); }
    static int pending(View view, Donor donor) {
        if (donor.total <= 0) return 0;
        int count = 0;
        for (int i = 0; i < view.goals.length; i++) {
            if (donor.joinedAt < view.goals[i] && view.total >= view.goals[i] && !donor.claimed[i]) count++;
        }
        return count;
    }
    private static String storedDay(YamlConfiguration data, String path) throws IOException {
        Object value = data.get(path);
        if (value == null) return null;
        if (!(value instanceof String)) throw new IOException("invalid tree ledger date: " + path);
        String day = (String) value;
        try {
            if (!LocalDate.parse(day).toString().equals(day)) throw new IOException("invalid tree ledger date: " + path);
        } catch (DateTimeParseException error) { throw new IOException("invalid tree ledger date: " + path, error); }
        return day;
    }
    static int daily(YamlConfiguration data, View view, String name, String today) throws IOException {
        if (!validName(name)) throw new IOException("invalid donor name");
        String donorPath = view.root + ".donors." + name;
        String donorDay = storedDay(data, donorPath + ".day");
        int donorDaily = number(data, donorPath + ".daily", 0, 0, DAILY_LIMIT);
        if (donorDay == null && donorDaily > 0) throw new IOException("daily tree donor count has no date");
        int previous = today.equals(donorDay) ? donorDaily : 0;
        int ledger = today.equals(storedDay(data, DAILY + ".day"))
                ? number(data, DAILY + ".used." + name, 0, 0, DAILY_LIMIT) : 0;
        return Math.max(previous, ledger);
    }
    private static void prepareLedger(YamlConfiguration data, String today) throws IOException {
        if (!today.equals(storedDay(data, DAILY + ".day"))) {
            data.set(DAILY + ".used", null);
            data.set(DAILY + ".day", today);
        }
    }
    static int[] donate(YamlConfiguration data, String name, int amount, String today) throws IOException {
        View view = current(data);
        Donor donor = donor(data, view, name);
        int daily = daily(data, view, name, today);
        sectionOrNull(data, "players");
        sectionOrNull(data, "players." + name);
        String balance = "players." + name + ".snowflakes";
        int snow = number(data, balance, 0, 0, Integer.MAX_VALUE);
        if (view.total >= view.goals[view.goals.length - 1]) throw new IllegalArgumentException("Цель уже достигнута.");
        if (amount < 1 || amount > DAILY_LIMIT || daily + amount > DAILY_LIMIT) {
            throw new IllegalArgumentException("Сегодняшний лимит: " + DAILY_LIMIT + " снежинок (UTC).");
        }
        if (amount > view.goals[view.goals.length - 1] - view.total) {
            throw new IllegalArgumentException("До цели осталось " + (view.goals[view.goals.length - 1] - view.total) + " снежинок.");
        }
        if (snow < amount) throw new IllegalArgumentException("Недостаточно снежинок.");
        String path = view.root + ".donors." + name;
        data.set(balance, snow - amount);
        if (donor.total == 0) data.set(path + ".joinedAt", view.total);
        data.set(path + ".day", today); data.set(path + ".daily", daily + amount);
        data.set(path + ".total", donor.total + amount); data.set(view.root + ".total", view.total + amount);
        prepareLedger(data, today);
        data.set(DAILY + ".used." + name, daily + amount);
        return new int[]{view.total, view.total + amount};
    }
    static Payment claim(YamlConfiguration data, String name, String requested, double startingCoins) throws IOException {
        View view = resolve(data, requested);
        Donor donor = donor(data, view, name);
        if (donor.total <= 0) throw new IllegalArgumentException("В этом сезоне нет вашего взноса.");
        int count = 0; long earned = 0; boolean last = false;
        for (int i = 0; i < view.goals.length; i++) {
            if (donor.joinedAt < view.goals[i] && view.total >= view.goals[i] && !donor.claimed[i]) {
                earned += view.coins[i]; count++; if (i == view.goals.length - 1) last = true;
            }
        }
        if (count == 0) throw new IllegalArgumentException("Новых наград пока нет.");
        if (earned > Integer.MAX_VALUE) throw new IOException("tree reward overflow");
        String base = "players." + name;
        sectionOrNull(data, "players");
        sectionOrNull(data, base);
        sectionOrNull(data, base + ".cases");
        sectionOrNull(data, base + ".titles");
        Object raw = data.get(base + ".coins");
        if (raw != null && !(raw instanceof Number)) throw new IOException("invalid coin balance");
        double coins = raw == null ? startingCoins : ((Number) raw).doubleValue();
        if (!Double.isFinite(coins) || coins < 0 || !Double.isFinite(coins + earned) || coins + earned <= coins) {
            throw new IOException("invalid coin balance");
        }
        boolean key = last && view.legendary, title = last && view.title;
        int keys = number(data, base + ".cases.legendary", 0, 0, Integer.MAX_VALUE);
        if (key && keys == Integer.MAX_VALUE) throw new IOException("invalid key balance");
        data.set(base + ".coins", coins + earned);
        if (key) data.set(base + ".cases.legendary", keys + 1);
        if (title) data.set(base + ".titles.tree_friend", true);
        for (int i = 0; i < view.goals.length; i++) {
            if (donor.joinedAt < view.goals[i] && view.total >= view.goals[i]) {
                data.set(view.root + ".donors." + name + ".claimed." + (i + 1), true);
            }
        }
        return new Payment((int) earned, count, key, title);
    }
    static int lifetimeProgress(YamlConfiguration data, String name) {
        int current = Math.max(0, data.getInt(ROOT + ".donors." + name + ".total", 0));
        int previous = Math.max(0, data.getInt(ROOT + ".lifetime." + name, 0));
        return previous > Integer.MAX_VALUE - current ? Integer.MAX_VALUE : previous + current;
    }
    private static ConfigurationSection sectionOrNull(YamlConfiguration data, String path) throws IOException {
        if (data.contains(path) && !data.isConfigurationSection(path)) throw new IOException("invalid tree section: " + path);
        return data.getConfigurationSection(path);
    }
    private static Map<String, Object> copySection(ConfigurationSection section, int depth) throws IOException {
        if (depth > 5) throw new IOException("tree donor data too deep");
        Map<String, Object> copy = new LinkedHashMap<String, Object>();
        if (section == null) return copy;
        if (section.getKeys(false).size() > 64) throw new IOException("too many tree donor fields");
        for (String key : section.getKeys(false)) {
            if (key == null || key.isEmpty() || key.indexOf('.') >= 0) throw new IOException("invalid tree donor field");
            Object value = section.get(key);
            if (value instanceof ConfigurationSection) value = copySection((ConfigurationSection) value, depth + 1);
            else if (value instanceof String || value instanceof Number || value instanceof Boolean) { /* immutable YAML scalar */ }
            else if (value instanceof List<?>) value = copyList((List<?>) value, depth + 1);
            else throw new IOException("unsupported tree donor value");
            copy.put(key, value);
        }
        return copy;
    }
    private static List<Object> copyList(List<?> values, int depth) throws IOException {
        if (depth > 5 || values.size() > 64) throw new IOException("tree donor list too large");
        List<Object> copy = new ArrayList<Object>();
        for (Object value : values) {
            if (value instanceof String || value instanceof Number || value instanceof Boolean) copy.add(value);
            else if (value instanceof List<?>) copy.add(copyList((List<?>) value, depth + 1));
            else throw new IOException("unsupported tree donor list value");
        }
        return copy;
    }
    static Rotation rotate(YamlConfiguration data, String requested, String today, long now) throws IOException {
        String next = normalizeId(requested);
        View old = current(data);
        if (next.equals(old.id)) throw new IllegalArgumentException("Этот сезон уже активен.");
        if (now <= 0) throw new IOException("invalid season time");
        ConfigurationSection history = sectionOrNull(data, HISTORY);
        ConfigurationSection used = sectionOrNull(data, USED);
        int archiveCount = history == null ? 0 : history.getKeys(false).size();
        int usedCount = used == null ? 0 : used.getKeys(false).size();
        if ((history != null && (history.contains(old.id) || history.contains(next)))
                || (used != null && used.contains(next))) throw new IllegalArgumentException("Название сезона уже использовалось.");
        int additionalIds = used != null && used.contains(old.id) ? 1 : 2;
        if (usedCount + additionalIds > MAX_USED_IDS) throw new IllegalArgumentException("Достигнут лимит названий сезонов; обратитесь к администратору.");
        ConfigurationSection donors = sectionOrNull(data, ROOT + ".donors");
        int donorCount = donors == null ? 0 : donors.getKeys(false).size();
        if (donorCount > MAX_DONORS) throw new IOException("too many tree donors");
        boolean archived = old.total > 0 || donorCount > 0;
        if (archived && archiveCount >= MAX_ARCHIVES) throw new IllegalArgumentException("Архив сезонов заполнен; сначала очистите полностью оплаченный сезон.");
        Map<String, Object> snapshot = new LinkedHashMap<String, Object>();
        Map<String, Integer> donations = new LinkedHashMap<String, Integer>();
        Map<String, Integer> todayUsed = new LinkedHashMap<String, Integer>();
        long sum = 0;
        if (donors != null) for (String name : donors.getKeys(false)) {
            if (!validName(name) || !donors.isConfigurationSection(name)) throw new IOException("invalid tree donor entry");
            Donor donor = donor(data, old, name);
            if (donor.total <= 0) throw new IOException("empty tree donor entry");
            sum += donor.total;
            donations.put(name, donor.total);
            int daily = daily(data, old, name, today);
            if (daily > 0) todayUsed.put(name, daily);
            snapshot.put(name, copySection(donors.getConfigurationSection(name), 0));
        }
        if (sum > old.total) throw new IOException("tree donations exceed total");
        for (Map.Entry<String, Integer> entry : donations.entrySet()) {
            String path = ROOT + ".lifetime." + entry.getKey();
            int prior = number(data, path, 0, 0, 1_000_000);
            if ((long) prior + entry.getValue() > 1_000_000L) throw new IOException("tree lifetime overflow");
        }
        if (archived) {
            String target = HISTORY + "." + old.id;
            data.set(target + ".total", old.total);
            data.set(target + ".closedAt", now);
            data.set(target + ".policy.version", 1);
            data.set(target + ".policy.goals", Arrays.asList(old.goals[0], old.goals[1], old.goals[2]));
            data.set(target + ".policy.coins", Arrays.asList(old.coins[0], old.coins[1], old.coins[2]));
            data.set(target + ".policy.legendary", old.legendary);
            data.set(target + ".policy.title", old.title);
            if (!snapshot.isEmpty()) data.createSection(target + ".donors", snapshot);
        }
        for (Map.Entry<String, Integer> entry : donations.entrySet()) {
            String path = ROOT + ".lifetime." + entry.getKey();
            data.set(path, number(data, path, 0, 0, 1_000_000) + entry.getValue());
        }
        prepareLedger(data, today);
        for (Map.Entry<String, Integer> entry : todayUsed.entrySet()) {
            int prior = number(data, DAILY + ".used." + entry.getKey(), 0, 0, DAILY_LIMIT);
            data.set(DAILY + ".used." + entry.getKey(), Math.max(prior, entry.getValue()));
        }
        data.set(USED + "." + old.id, true); data.set(USED + "." + next, true);
        data.set(ROOT + ".donors", null); data.set(ROOT + ".total", 0); data.set(ROOT + ".season", next);
        return new Rotation(old.id, next, archived);
    }
    static int prune(YamlConfiguration data, String requested) throws IOException {
        String id = normalizeId(requested);
        View view = resolve(data, id);
        if (!view.archived) throw new IllegalArgumentException("Текущий сезон удалить нельзя.");
        ConfigurationSection donors = sectionOrNull(data, view.root + ".donors");
        int donorCount = donors == null ? 0 : donors.getKeys(false).size();
        if (donorCount > MAX_DONORS) throw new IOException("too many archived tree donors");
        int pending = 0;
        if (donors != null) for (String name : donors.getKeys(false)) {
            if (!validName(name) || !donors.isConfigurationSection(name)) throw new IOException("invalid archived donor");
            pending += pending(view, donor(data, view, name));
        }
        if (pending > 0) throw new IllegalArgumentException("Есть невостребованные награды: " + pending + ". Архив сохранён.");
        data.set(view.root, null);
        return donorCount;
    }
    static List<String> archiveIds(YamlConfiguration data) throws IOException {
        ConfigurationSection history = sectionOrNull(data, HISTORY);
        List<String> ids = new ArrayList<String>();
        if (history != null) for (String id : history.getKeys(false)) if (validId(id)) ids.add(id);
        return ids;
    }
}
