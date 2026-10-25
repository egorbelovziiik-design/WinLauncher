package net.kinggrief.bridge;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Мост между сайтом KINGGRIEF (https://kinggrief.vercel.app) и игровым сервером (kinggrief.pickme.cloud).
 * Совместим с Paper / Purpur / Spigot 1.16.5 — 1.21.4+ и Java 11 / 16 / 17 / 21+.
 * Не требует внешних библиотек (встроенный парсер JSON).
 */
public final class KingGriefBridge extends JavaPlugin {
    private HttpClient http;
    private BukkitTask pollTask;
    private String baseUrl;
    private String secret;
    private String serverId;
    private int pollSeconds;
    private boolean allowAllCommands;
    private List<String> allowedPrefixes;

    private volatile long lastSuccessMillis = 0L;
    private volatile String lastErrorMessage = "";
    private volatile int deliveredCount = 0;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        schedulePoll();
        getLogger().info("KingGriefBridge v" + getDescription().getVersion()
                + " запущен -> " + baseUrl + " (интервал " + pollSeconds + "с, сервер " + serverId + ")");
    }

    @Override
    public void onDisable() {
        if (pollTask != null) {
            pollTask.cancel();
            pollTask = null;
        }
    }

    private void loadSettings() {
        reloadConfig();
        this.baseUrl = trimSlash(getConfig().getString("base-url", "https://kinggrief.vercel.app"));
        this.secret = getConfig().getString("secret", "");
        this.serverId = getConfig().getString("server-id", "kinggrief-main");
        this.pollSeconds = Math.max(3, getConfig().getInt("poll-seconds", 5));
        this.allowAllCommands = getConfig().getBoolean("allow-all-commands", false);
        List<String> prefixes = getConfig().getStringList("allowed-prefixes");
        if (prefixes == null || prefixes.isEmpty()) {
            prefixes = new ArrayList<String>();
            Collections.addAll(prefixes,
                    "cases ", "case ", "dc ", "donatecase ", "crates ", "crate ",
                    "ucase ", "mooncases ", "rwcases ", "treexcases ", "excellentcrates ",
                    "crazycrates ", "lp ", "luckperms ", "relics ", "riliks ",
                    "p ", "points ", "playerpoints ", "eco ", "money ", "kit ", "kits ",
                    "give ", "unban ", "pardon ", "unmute ", "unbanip ", "pardon-ip ", "unmuteip ");
        }
        this.allowedPrefixes = prefixes;
        if (secret == null || secret.isEmpty() || "CHANGE_ME".equals(secret)) {
            getLogger().warning("Укажите секрет MINECRAFT_BRIDGE_SECRET в plugins/KingGriefBridge/config.yml!");
        }
    }

    private void schedulePoll() {
        if (pollTask != null) {
            pollTask.cancel();
        }
        long ticks = pollSeconds * 20L;
        this.pollTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                pollOnce();
            }
        }, 40L, ticks);
    }

    private void pollOnce() {
        if (secret == null || secret.isEmpty() || "CHANGE_ME".equals(secret)) {
            return;
        }
        try {
            String payloadJson = buildPollPayload();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/minecraft/poll"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("X-Bridge-Secret", secret)
                    .header("User-Agent", "KingGriefBridge/" + getDescription().getVersion())
                    .POST(HttpRequest.BodyPublishers.ofString(payloadJson, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                lastErrorMessage = "HTTP " + response.statusCode();
                getLogger().warning("Опрос очереди вернул HTTP " + response.statusCode());
                return;
            }

            lastSuccessMillis = System.currentTimeMillis();
            lastErrorMessage = "";

            Object parsed = MiniJson.parse(response.body());
            if (!(parsed instanceof Map)) {
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) parsed;
            Object commandsObj = body.get("commands");
            if (!(commandsObj instanceof List)) {
                return;
            }
            @SuppressWarnings("unchecked")
            List<Object> commands = (List<Object>) commandsObj;
            if (commands.isEmpty()) {
                return;
            }

            for (Object entry : commands) {
                if (!(entry instanceof Map)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> item = (Map<String, Object>) entry;
                final String id = asString(item.get("id"));
                final String rawCmd = stripLeadingSlash(asString(item.get("command")));
                if (id.isEmpty()) continue;

                if (!isAllowed(rawCmd)) {
                    getLogger().warning("Команда отклонена белым списком: " + rawCmd);
                    ackAsync(id, false, "Команда не разрешена в config.yml плагина");
                    continue;
                }

                Bukkit.getScheduler().runTask(this, new Runnable() {
                    @Override
                    public void run() {
                        executeOnMainThread(id, rawCmd);
                    }
                });
            }
        } catch (Exception ex) {
            lastErrorMessage = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            getLogger().log(Level.WARNING, "Не удалось опросить магазин: " + ex.getMessage());
        }
    }

    private void executeOnMainThread(final String id, final String rawCmd) {
        try {
            String resolved = resolveExecutableCommand(rawCmd);
            getLogger().info("Выдача заказа [" + id + "]: /" + resolved);
            boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
            if (ok) {
                deliveredCount++;
            }
            final String err = ok ? null : "dispatchCommand вернул false для: " + resolved;
            Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
                @Override
                public void run() {
                    ackAsync(id, ok, err);
                }
            });
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Ошибка при выполнении команды /" + rawCmd, ex);
            final String msg = ex.getMessage() != null ? ex.getMessage() : "exception";
            Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
                @Override
                public void run() {
                    ackAsync(id, false, msg);
                }
            });
        }
    }

    /**
     * Если базовая команда плагина кейсов не зарегистрирована (например, на сайте указано cases give,
     * а на сервере стоит DonateCase /dc или Crates /crates), автоматически подбирает установленный плагин.
     */
    private String resolveExecutableCommand(String rawCmd) {
        String trimmed = rawCmd.trim();
        int spaceIdx = trimmed.indexOf(' ');
        String root = (spaceIdx > 0 ? trimmed.substring(0, spaceIdx) : trimmed).toLowerCase(Locale.ROOT);
        if (isCommandRegistered(root)) {
            return trimmed;
        }
        String[] parts = trimmed.split("\\s+");
        // Типовые шаблоны выдачи кейсов из 5 токенов:
        // cases give <player> <case> <amount> | crates key <player> <case> <amount> | dc givekey <player> <case> <amount>
        if (parts.length == 5 && ("cases".equals(root) || "case".equals(root) || "crates".equals(root) || "crate".equals(root) || "dc".equals(root))) {
            String sub = parts[1].toLowerCase(Locale.ROOT);
            if ("give".equals(sub) || "add".equals(sub) || "key".equals(sub) || "givekey".equals(sub)) {
                String player = parts[2];
                String caseId = parts[3];
                String amount = parts[4];
                if (isCommandRegistered("cases")) {
                    String alt = "cases give " + player + " " + caseId + " " + amount;
                    getLogger().info("Авто-маршрутизация команды кейса: /" + trimmed + " -> /" + alt);
                    return alt;
                }
                if (isCommandRegistered("dc")) {
                    String alt = "dc givekey " + player + " " + caseId + " " + amount;
                    getLogger().info("Авто-маршрутизация команды кейса: /" + trimmed + " -> /" + alt);
                    return alt;
                }
                if (isCommandRegistered("crates")) {
                    String alt = "crates key " + player + " " + caseId + " " + amount;
                    getLogger().info("Авто-маршрутизация команды кейса: /" + trimmed + " -> /" + alt);
                    return alt;
                }
                if (isCommandRegistered("crate")) {
                    String alt = "crate key " + player + " " + caseId + " " + amount;
                    getLogger().info("Авто-маршрутизация команды кейса: /" + trimmed + " -> /" + alt);
                    return alt;
                }
            }
        }
        return trimmed;
    }

    private boolean isCommandRegistered(String name) {
        try {
            PluginCommand pc = Bukkit.getPluginCommand(name);
            if (pc != null) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private String buildPollPayload() {
        StringBuilder sb = new StringBuilder(160);
        sb.append('{');
        sb.append("\"serverId\":").append(MiniJson.quote(serverId)).append(',');
        sb.append("\"online\":").append(Bukkit.getOnlinePlayers().size()).append(',');
        sb.append("\"max\":").append(Bukkit.getMaxPlayers()).append(',');
        sb.append("\"version\":").append(MiniJson.quote(readMcVersion()));
        double tps = readServerTps();
        if (tps > 0.0) {
            sb.append(",\"tps\":").append(String.format(Locale.US, "%.2f", Math.min(20.0, tps)));
        }
        sb.append('}');
        return sb.toString();
    }

    private String readMcVersion() {
        try {
            Method m = Bukkit.class.getMethod("getMinecraftVersion");
            Object v = m.invoke(null);
            if (v != null) return String.valueOf(v);
        } catch (Throwable ignored) {
        }
        String bv = Bukkit.getBukkitVersion();
        int dash = bv.indexOf('-');
        return dash > 0 ? bv.substring(0, dash) : bv;
    }

    private double readServerTps() {
        try {
            Method getTps = Bukkit.class.getMethod("getTPS");
            Object res = getTps.invoke(null);
            if (res instanceof double[]) {
                double[] arr = (double[]) res;
                if (arr.length > 0) return arr[0];
            }
        } catch (Throwable ignored) {
            // Spigot без Paper getTPS() — просто пропускаем поле tps
        }
        return -1.0;
    }

    private void ackAsync(String id, boolean ok, String error) {
        try {
            StringBuilder sb = new StringBuilder(128);
            sb.append("{\"id\":").append(MiniJson.quote(id))
              .append(",\"ok\":").append(ok);
            if (error != null && !error.isEmpty()) {
                sb.append(",\"error\":").append(MiniJson.quote(error));
            }
            sb.append('}');

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/minecraft/ack"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("X-Bridge-Secret", secret)
                    .header("User-Agent", "KingGriefBridge/" + getDescription().getVersion())
                    .POST(HttpRequest.BodyPublishers.ofString(sb.toString(), StandardCharsets.UTF_8))
                    .build();
            http.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ex) {
            getLogger().log(Level.WARNING, "Не удалось подтвердить выдачу " + id + ": " + ex.getMessage());
        }
    }

    private boolean isAllowed(String command) {
        if (command == null || command.isEmpty()) return false;
        if (command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0 || command.contains(";") || command.contains("&&")) {
            return false;
        }
        if (allowAllCommands) return true;
        String lower = command.toLowerCase(Locale.ROOT);
        for (String prefix : allowedPrefixes) {
            if (prefix != null && !prefix.isEmpty() && lower.startsWith(prefix.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String stripLeadingSlash(String cmd) {
        if (cmd == null) return "";
        String t = cmd.trim();
        while (t.startsWith("/")) {
            t = t.substring(1).trim();
        }
        return t;
    }

    private static String trimSlash(String url) {
        if (url == null) return "";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String asString(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "status";
        if ("reload".equals(sub)) {
            loadSettings();
            schedulePoll();
            sender.sendMessage("§a[KingGriefBridge] §fКонфигурация перезагружена (§d" + baseUrl + "§f).");
            return true;
        }
        if ("poll".equals(sub)) {
            sender.sendMessage("§e[KingGriefBridge] §fПринудительный опрос очереди выдачи...");
            Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
                @Override
                public void run() {
                    pollOnce();
                }
            });
            return true;
        }
        long agoSec = lastSuccessMillis > 0 ? (System.currentTimeMillis() - lastSuccessMillis) / 1000L : -1L;
        sender.sendMessage("§d[KingGriefBridge] §fМагазин: §b" + baseUrl + " §7(" + serverId + ")");
        sender.sendMessage("§7Статус связи: " + (agoSec >= 0 && agoSec < 30
                ? "§aПОДКЛЮЧЕНО §7(" + agoSec + "с назад)"
                : "§eОЖИДАНИЕ" + (lastErrorMessage.isEmpty() ? "" : " §c(" + lastErrorMessage + ")")));
        sender.sendMessage("§7Выполнено выдач за сессию: §a" + deliveredCount + " §7| Команды: §f/" + label + " poll §7| §f/" + label + " reload");
        return true;
    }

    /**
     * Компактный RFC-8259 JSON-парсер без внешних зависимостей.
     */
    static final class MiniJson {
        private final String s;
        private int pos;

        private MiniJson(String s) {
            this.s = s != null ? s : "";
            this.pos = 0;
        }

        static Object parse(String json) {
            MiniJson p = new MiniJson(json);
            p.skipWs();
            return p.readValue();
        }

        static String quote(String raw) {
            if (raw == null) return "null";
            StringBuilder sb = new StringBuilder(raw.length() + 8);
            sb.append('"');
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                switch (c) {
                    case '"': sb.append("\\\""); break;
                    case '\\': sb.append("\\\\"); break;
                    case '\b': sb.append("\\b"); break;
                    case '\f': sb.append("\\f"); break;
                    case '\n': sb.append("\\n"); break;
                    case '\r': sb.append("\\r"); break;
                    case '\t': sb.append("\\t"); break;
                    default:
                        if (c < 0x20) {
                            sb.append(String.format("\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                }
            }
            sb.append('"');
            return sb.toString();
        }

        private void skipWs() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') pos++;
                else break;
            }
        }

        private Object readValue() {
            skipWs();
            if (pos >= s.length()) return null;
            char c = s.charAt(pos);
            if (c == '{') return readObject();
            if (c == '[') return readArray();
            if (c == '"') return readString();
            if (c == 't' && s.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
            if (c == 'f' && s.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            if (c == 'n' && s.startsWith("null", pos)) { pos += 4; return null; }
            return readNumber();
        }

        private Map<String, Object> readObject() {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            pos++; // '{'
            skipWs();
            if (pos < s.length() && s.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (pos < s.length()) {
                skipWs();
                if (pos >= s.length() || s.charAt(pos) != '"') break;
                String key = readString();
                skipWs();
                if (pos < s.length() && s.charAt(pos) == ':') pos++;
                Object val = readValue();
                map.put(key, val);
                skipWs();
                if (pos < s.length() && s.charAt(pos) == ',') {
                    pos++;
                } else if (pos < s.length() && s.charAt(pos) == '}') {
                    pos++;
                    break;
                } else {
                    break;
                }
            }
            return map;
        }

        private List<Object> readArray() {
            List<Object> list = new ArrayList<Object>();
            pos++; // '['
            skipWs();
            if (pos < s.length() && s.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (pos < s.length()) {
                list.add(readValue());
                skipWs();
                if (pos < s.length() && s.charAt(pos) == ',') {
                    pos++;
                } else if (pos < s.length() && s.charAt(pos) == ']') {
                    pos++;
                    break;
                } else {
                    break;
                }
            }
            return list;
        }

        private String readString() {
            pos++; // '"'
            StringBuilder sb = new StringBuilder();
            while (pos < s.length()) {
                char c = s.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\' && pos < s.length()) {
                    char esc = s.charAt(pos++);
                    switch (esc) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (pos + 4 <= s.length()) {
                                String hex = s.substring(pos, pos + 4);
                                sb.append((char) Integer.parseInt(hex, 16));
                                pos += 4;
                            }
                            break;
                        default: sb.append(esc); break;
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private Number readNumber() {
            int start = pos;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                    pos++;
                } else {
                    break;
                }
            }
            String numStr = s.substring(start, pos);
            try {
                if (numStr.indexOf('.') >= 0 || numStr.indexOf('e') >= 0 || numStr.indexOf('E') >= 0) {
                    return Double.valueOf(numStr);
                }
                return Long.valueOf(numStr);
            } catch (NumberFormatException e) {
                return Integer.valueOf(0);
            }
        }
    }
}
