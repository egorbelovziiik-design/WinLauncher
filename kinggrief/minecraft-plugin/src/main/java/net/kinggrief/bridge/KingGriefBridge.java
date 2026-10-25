package net.kinggrief.bridge;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Облачный мост между сайтом KINGGRIEF (https://kinggrief.vercel.app) и игровым сервером (kinggrief.pickme.cloud).
 * Совместим с Paper / Purpur / Spigot 1.16.5 — 1.21.4+ и Java 11 / 16 / 17 / 21+.
 * Автоматически определяет установленный плагин кейсов (DonateCase, MoonCases, Cases, ExcellentCrates, CrazyCrates и др.)
 * по PluginManager и TabCompleter, гарантируя точную выдачу ключей игроку.
 */
public final class KingGriefBridge extends JavaPlugin {
    private static final Set<String> KNOWN_CASES = new HashSet<String>(Arrays.asList(
            "donate", "sdonate", "legendary", "summer", "kits", "money",
            "allorno", "ditems", "djamshut", "reallydev", "riliks"
    ));

    private HttpClient http;
    private BukkitTask pollTask;
    private BukkitTask snapshotTask;
    private String baseUrl;
    private String secret;
    private String serverId;
    private int pollSeconds;
    private String caseCommandOverride;
    private boolean notifyPlayer;
    private boolean allowAllCommands;
    private List<String> allowedPrefixes;

    private volatile int cachedOnline = 0;
    private volatile int cachedMax = 100;
    private volatile double cachedTps = -1.0;
    private volatile String cachedMcVersion = "";
    private volatile String detectedCaseTemplate = "";
    private volatile String detectedPluginsSummary = "";

    private volatile long lastSuccessMillis = 0L;
    private volatile String lastErrorMessage = "";
    private volatile int deliveredCount = 0;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        // После загрузки всех плагинов сервера определяем точный синтаксис плагина кейсов
        Bukkit.getScheduler().runTask(this, new Runnable() {
            @Override
            public void run() {
                refreshServerSnapshot();
                detectCasePluginOnMainThread();
            }
        });

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
        if (snapshotTask != null) {
            snapshotTask.cancel();
            snapshotTask = null;
        }
    }

    private void loadSettings() {
        reloadConfig();
        this.baseUrl = trimSlash(getConfig().getString("base-url", "https://kinggrief.vercel.app"));
        this.secret = getConfig().getString("secret", "");
        this.serverId = getConfig().getString("server-id", "kinggrief-main");
        this.pollSeconds = Math.max(3, getConfig().getInt("poll-seconds", 5));
        this.caseCommandOverride = stripLeadingSlash(getConfig().getString("case-command-override", ""));
        this.notifyPlayer = getConfig().getBoolean("notify-player", true);
        this.allowAllCommands = getConfig().getBoolean("allow-all-commands", false);

        List<String> prefixes = getConfig().getStringList("allowed-prefixes");
        if (prefixes == null || prefixes.isEmpty()) {
            prefixes = new ArrayList<String>();
            Collections.addAll(prefixes,
                    "dc ", "donatecase ", "jdonatecase ", "cases ", "case ",
                    "crates ", "crate ", "cc ", "ucase ", "universalcase ",
                    "mooncases ", "mcases ", "rwcases ", "treexcases ",
                    "excellentcrates ", "crazycrates ", "acubelets ",
                    "lp ", "luckperms ", "pex ", "relics ", "riliks ",
                    "p ", "points ", "playerpoints ", "eco ", "money ",
                    "kit ", "kits ", "give ", "unban ", "pardon ",
                    "unmute ", "unbanip ", "pardon-ip ", "unmuteip ");
        }
        this.allowedPrefixes = prefixes;
        if (secret == null || secret.trim().isEmpty() || "CHANGE_ME".equals(secret.trim())) {
            getLogger().warning("Укажите секрет MINECRAFT_BRIDGE_SECRET в plugins/KingGriefBridge/config.yml!");
        }
    }

    private void schedulePoll() {
        if (pollTask != null) pollTask.cancel();
        if (snapshotTask != null) snapshotTask.cancel();

        long ticks = pollSeconds * 20L;
        // Обновляем снимок онлайна и TPS строго в основном потоке сервера
        this.snapshotTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                Bukkit.getScheduler().runTask(KingGriefBridge.this, new Runnable() {
                    @Override
                    public void run() {
                        refreshServerSnapshot();
                        if (detectedCaseTemplate == null || detectedCaseTemplate.isEmpty()) {
                            detectCasePluginOnMainThread();
                        }
                    }
                });
            }
        }, 20L, ticks);

        this.pollTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                pollOnce();
            }
        }, 40L, ticks);
    }

    private void refreshServerSnapshot() {
        try {
            Collection<? extends Player> players = Bukkit.getOnlinePlayers();
            this.cachedOnline = players != null ? players.size() : 0;
            this.cachedMax = Bukkit.getMaxPlayers();
            this.cachedMcVersion = readMcVersion();
            this.cachedTps = readServerTps();
        } catch (Throwable ignored) {
        }
    }

    /**
     * Автоматически определяет установленный на сервере плагин кейсов и точный рабочий синтаксис выдачи ключей.
     */
    private void detectCasePluginOnMainThread() {
        try {
            List<String> pluginNames = listInstalledPluginNames();
            List<String> interesting = new ArrayList<String>();
            for (String p : pluginNames) {
                String low = p.toLowerCase(Locale.ROOT);
                if (low.contains("case") || low.contains("crate") || low.contains("luckperms")
                        || low.contains("authme") || low.contains("playerpoints") || low.contains("cubelet")) {
                    interesting.add(p);
                }
            }
            if (!interesting.isEmpty()) {
                this.detectedPluginsSummary = joinStrings(interesting, ", ");
            } else if (!pluginNames.isEmpty()) {
                int cap = Math.min(pluginNames.size(), 8);
                this.detectedPluginsSummary = joinStrings(pluginNames.subList(0, cap), ", ");
            }

            if (caseCommandOverride != null && !caseCommandOverride.isEmpty()) {
                this.detectedCaseTemplate = caseCommandOverride;
                getLogger().info("Используется ручной шаблон выдачи кейсов из config.yml: /" + detectedCaseTemplate);
                return;
            }

            // 1. Проверяем DonateCase / JDonateCase (самый частый плагин на сборках ReallyWorld / Grief)
            if (hasInstalledPlugin(pluginNames, "DonateCase") || hasInstalledPlugin(pluginNames, "JDonateCase")) {
                PluginCommand dcCmd = firstRegisteredCommand("dc", "donatecase", "jdonatecase", "cases");
                String root = dcCmd != null ? preferredRootAlias("dc", "donatecase") : "dc";
                List<String> subs = safeTabComplete(dcCmd, root, new String[]{""});
                if (containsIgnoreCase(subs, "givekey") || subs.isEmpty()) {
                    this.detectedCaseTemplate = root + " givekey {player} {case} {amount}";
                } else if (containsIgnoreCase(subs, "keys")) {
                    List<String> arg2 = safeTabComplete(dcCmd, root, new String[]{"keys", "give", ""});
                    if (containsAnyKnownCase(arg2)) {
                        this.detectedCaseTemplate = root + " keys give {case} {player} {amount}";
                    } else {
                        this.detectedCaseTemplate = root + " keys give {player} {case} {amount}";
                    }
                } else {
                    this.detectedCaseTemplate = root + " givekey {player} {case} {amount}";
                }
                getLogger().info("Обнаружен плагин DonateCase -> команда выдачи: /" + this.detectedCaseTemplate);
                return;
            }

            // 2. Проверяем ExcellentCrates / GoldenCrates
            if (hasInstalledPlugin(pluginNames, "ExcellentCrates") || hasInstalledPlugin(pluginNames, "GoldenCrates")) {
                String root = preferredRootAlias("crates", "excellentcrates");
                this.detectedCaseTemplate = root + " key give {player} {case} {amount}";
                getLogger().info("Обнаружен ExcellentCrates -> команда выдачи: /" + this.detectedCaseTemplate);
                return;
            }

            // 3. Проверяем CrazyCrates
            if (hasInstalledPlugin(pluginNames, "CrazyCrates")) {
                String root = preferredRootAlias("cc", "crazycrates");
                this.detectedCaseTemplate = root + " give v {case} {amount} {player}";
                getLogger().info("Обнаружен CrazyCrates -> команда выдачи: /" + this.detectedCaseTemplate);
                return;
            }

            // 4. Проверяем CratesPlus
            if (hasInstalledPlugin(pluginNames, "CratesPlus")) {
                String root = preferredRootAlias("crate", "crates");
                this.detectedCaseTemplate = root + " key {player} {case} {amount}";
                getLogger().info("Обнаружен CratesPlus -> команда выдачи: /" + this.detectedCaseTemplate);
                return;
            }

            // 5. Универсальное зондирование зарегистрированных команд через TabCompleter и владельца PluginCommand
            String[] candidates = new String[]{
                    "dc", "donatecase", "cases", "case", "crates", "crate",
                    "ucase", "universalcase", "mooncases", "mcases", "rwcases", "treexcases", "acubelets"
            };
            for (String candidate : candidates) {
                PluginCommand pc = safeGetPluginCommand(candidate);
                if (pc == null) continue;

                String ownerName = readCommandOwnerName(pc).toLowerCase(Locale.ROOT);
                if (ownerName.contains("donatecase")) {
                    this.detectedCaseTemplate = "dc givekey {player} {case} {amount}";
                    getLogger().info("Команда /" + candidate + " принадлежит DonateCase -> /" + this.detectedCaseTemplate);
                    return;
                }

                List<String> subs = safeTabComplete(pc, candidate, new String[]{""});
                if (containsIgnoreCase(subs, "givekey")) {
                    this.detectedCaseTemplate = candidate + " givekey {player} {case} {amount}";
                    getLogger().info("Автоопределена команда кейсов (givekey): /" + this.detectedCaseTemplate);
                    return;
                }
                if (containsIgnoreCase(subs, "keys")) {
                    List<String> sub2 = safeTabComplete(pc, candidate, new String[]{"keys", ""});
                    if (containsIgnoreCase(sub2, "give")) {
                        List<String> arg2 = safeTabComplete(pc, candidate, new String[]{"keys", "give", ""});
                        this.detectedCaseTemplate = containsAnyKnownCase(arg2)
                                ? candidate + " keys give {case} {player} {amount}"
                                : candidate + " keys give {player} {case} {amount}";
                        getLogger().info("Автоопределена команда кейсов (keys give): /" + this.detectedCaseTemplate);
                        return;
                    }
                }
                if (containsIgnoreCase(subs, "key")) {
                    List<String> sub2 = safeTabComplete(pc, candidate, new String[]{"key", ""});
                    if (containsIgnoreCase(sub2, "give")) {
                        this.detectedCaseTemplate = candidate + " key give {player} {case} {amount}";
                    } else {
                        this.detectedCaseTemplate = candidate + " key {player} {case} {amount}";
                    }
                    getLogger().info("Автоопределена команда кейсов (key): /" + this.detectedCaseTemplate);
                    return;
                }
                if (containsIgnoreCase(subs, "give")) {
                    List<String> arg1 = safeTabComplete(pc, candidate, new String[]{"give", ""});
                    if (containsIgnoreCase(arg1, "v") && containsIgnoreCase(arg1, "p")) {
                        this.detectedCaseTemplate = candidate + " give v {case} {amount} {player}";
                    } else if (containsAnyKnownCase(arg1)) {
                        this.detectedCaseTemplate = candidate + " give {case} {player} {amount}";
                    } else {
                        this.detectedCaseTemplate = candidate + " give {player} {case} {amount}";
                    }
                    getLogger().info("Автоопределена команда кейсов (give): /" + this.detectedCaseTemplate);
                    return;
                }
                if (containsIgnoreCase(subs, "add")) {
                    List<String> arg1 = safeTabComplete(pc, candidate, new String[]{"add", ""});
                    this.detectedCaseTemplate = containsAnyKnownCase(arg1)
                            ? candidate + " add {case} {player} {amount}"
                            : candidate + " add {player} {case} {amount}";
                    getLogger().info("Автоопределена команда кейсов (add): /" + this.detectedCaseTemplate);
                    return;
                }
            }
        } catch (Throwable t) {
            getLogger().log(Level.FINE, "Не удалось автоопределить плагин кейсов", t);
        }
    }

    private void pollOnce() {
        if (secret == null || secret.trim().isEmpty() || "CHANGE_ME".equals(secret.trim())) {
            return;
        }
        try {
            String payloadJson = buildPollPayload();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/minecraft/poll"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("X-Bridge-Secret", secret.trim())
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
                final String nickname = asString(item.get("nickname"));
                final String kind = asString(item.get("kind"));
                final String caseId = asString(item.get("caseId"));
                final int amount = asInt(item.get("amount"), 0);
                if (id.isEmpty()) continue;

                Bukkit.getScheduler().runTask(this, new Runnable() {
                    @Override
                    public void run() {
                        executeOnMainThread(id, rawCmd, nickname, kind, caseId, amount);
                    }
                });
            }
        } catch (Exception ex) {
            lastErrorMessage = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            getLogger().log(Level.WARNING, "Не удалось опросить магазин: " + ex.getMessage());
        }
    }

    private void executeOnMainThread(
            final String id,
            final String rawCmd,
            final String nickname,
            final String kind,
            final String explicitCaseId,
            final int explicitAmount
    ) {
        try {
            if (detectedCaseTemplate == null || detectedCaseTemplate.isEmpty()) {
                detectCasePluginOnMainThread();
            }

            final String resolved = resolveExecutableCommand(rawCmd, nickname, kind, explicitCaseId, explicitAmount);
            if (!isAllowed(resolved)) {
                getLogger().warning("Команда отклонена белым списком: " + resolved);
                Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
                    @Override
                    public void run() {
                        ackAsync(id, false, "Команда не разрешена в config.yml: " + resolved, resolved);
                    }
                });
                return;
            }

            getLogger().info("Выдача заказа [" + id + "] для " + nickname + ": /" + resolved);
            ConsoleCommandSender console = Bukkit.getConsoleSender();
            final boolean ok = Bukkit.dispatchCommand(console, resolved);
            if (ok) {
                deliveredCount++;
                if (notifyPlayer && nickname != null && !nickname.isEmpty()) {
                    notifyOnlinePlayer(nickname, kind, explicitCaseId, explicitAmount);
                }
            }
            final String err = ok ? null : "dispatchCommand вернул false для: /" + resolved;
            Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
                @Override
                public void run() {
                    ackAsync(id, ok, err, resolved);
                }
            });
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Ошибка при выполнении команды /" + rawCmd, ex);
            final String msg = ex.getMessage() != null ? ex.getMessage() : "exception";
            Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
                @Override
                public void run() {
                    ackAsync(id, false, msg, rawCmd);
                }
            });
        }
    }

    /**
     * Подбирает точную команду плагина кейсов на сервере, даже если на сайте стоял другой дефолтный шаблон
     * (например, cases give вместо dc givekey для DonateCase или наоборот).
     */
    private String resolveExecutableCommand(
            String rawCmd,
            String nickname,
            String kind,
            String explicitCaseId,
            int explicitAmount
    ) {
        String trimmed = stripLeadingSlash(rawCmd);
        String templateToUse = (caseCommandOverride != null && !caseCommandOverride.isEmpty())
                ? caseCommandOverride
                : detectedCaseTemplate;

        // Извлекаем (player, caseId, amount), если это выдача кейсов
        String parsedPlayer = nickname != null ? nickname.trim() : "";
        String parsedCase = explicitCaseId != null ? explicitCaseId.trim().toLowerCase(Locale.ROOT) : "";
        int parsedAmount = explicitAmount;

        String[] parts = trimmed.split("\\s+");
        String root = parts.length > 0 ? parts[0].toLowerCase(Locale.ROOT) : "";
        boolean looksLikeDefaultCaseCmd = false;

        // Распознаём стандартные шаблоны:
        // cases give <player> <case> <amount> | dc givekey <player> <case> <amount> | crates key <player> <case> <amount>
        if (parts.length == 5 && ("cases".equals(root) || "case".equals(root) || "dc".equals(root)
                || "donatecase".equals(root) || "crates".equals(root) || "crate".equals(root))) {
            String sub = parts[1].toLowerCase(Locale.ROOT);
            if ("give".equals(sub) || "givekey".equals(sub) || "add".equals(sub) || "key".equals(sub)) {
                looksLikeDefaultCaseCmd = true;
                if (parsedPlayer.isEmpty()) parsedPlayer = parts[2];
                if (parsedCase.isEmpty()) parsedCase = parts[3].toLowerCase(Locale.ROOT);
                if (parsedAmount <= 0) parsedAmount = parsePositiveInt(parts[4], 1);
            }
        } else if (parts.length == 6 && ("dc".equals(root) || "donatecase".equals(root) || "crates".equals(root))) {
            // dc keys give <case> <player> <amount> или crates key give <player> <case> <amount>
            String sub1 = parts[1].toLowerCase(Locale.ROOT);
            String sub2 = parts[2].toLowerCase(Locale.ROOT);
            if (("keys".equals(sub1) || "key".equals(sub1)) && "give".equals(sub2)) {
                looksLikeDefaultCaseCmd = true;
                if (parsedAmount <= 0) parsedAmount = parsePositiveInt(parts[5], 1);
            }
        }

        boolean isCaseDelivery = "case".equalsIgnoreCase(kind) || looksLikeDefaultCaseCmd;
        if (isCaseDelivery
                && templateToUse != null && !templateToUse.isEmpty()
                && !parsedPlayer.isEmpty() && !parsedCase.isEmpty() && parsedAmount > 0) {
            // Если задан override или команда с сайта является стандартным шаблоном (dc givekey / cases give),
            // применяем автоопределённый под этот сервер шаблон плагина кейсов.
            if ((caseCommandOverride != null && !caseCommandOverride.isEmpty()) || looksLikeDefaultCaseCmd) {
                String adapted = templateToUse
                        .replace("{player}", parsedPlayer)
                        .replace("{case}", parsedCase)
                        .replace("{amount}", String.valueOf(parsedAmount));
                if (!adapted.equalsIgnoreCase(trimmed)) {
                    getLogger().info("Адаптация под плагин кейсов сервера: /" + trimmed + " -> /" + adapted);
                }
                return adapted;
            }
        }

        // Fallback, если базовая команда вообще не зарегистрирована
        if (!root.isEmpty() && safeGetPluginCommand(root) == null
                && !parsedPlayer.isEmpty() && !parsedCase.isEmpty() && parsedAmount > 0) {
            if (safeGetPluginCommand("dc") != null) {
                return "dc givekey " + parsedPlayer + " " + parsedCase + " " + parsedAmount;
            }
            if (safeGetPluginCommand("cases") != null) {
                return "cases give " + parsedPlayer + " " + parsedCase + " " + parsedAmount;
            }
            if (safeGetPluginCommand("crates") != null) {
                return "crates key give " + parsedPlayer + " " + parsedCase + " " + parsedAmount;
            }
        }
        return trimmed;
    }

    private void notifyOnlinePlayer(String nickname, String kind, String caseId, int amount) {
        try {
            Collection<? extends Player> players = Bukkit.getOnlinePlayers();
            if (players == null) return;
            for (Player p : players) {
                String name = readPlayerName(p);
                if (name != null && name.equalsIgnoreCase(nickname)) {
                    if ("case".equalsIgnoreCase(kind) && caseId != null && !caseId.isEmpty()) {
                        int qty = amount > 0 ? amount : 1;
                        p.sendMessage("§6§l[KINGGRIEF] §aВам выданы ключи с сайта: §e" + caseId + " §f×" + qty + "§a! Откройте кейс на спавне.");
                    } else {
                        p.sendMessage("§6§l[KINGGRIEF] §aВаша покупка с сайта успешно выдана! Приятной игры!");
                    }
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private String buildPollPayload() {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        sb.append("\"serverId\":").append(MiniJson.quote(serverId)).append(',');
        sb.append("\"online\":").append(Math.max(0, cachedOnline)).append(',');
        sb.append("\"max\":").append(Math.max(0, cachedMax)).append(',');
        String ver = (cachedMcVersion != null && !cachedMcVersion.isEmpty()) ? cachedMcVersion : readMcVersion();
        sb.append("\"version\":").append(MiniJson.quote(ver));
        if (cachedTps > 0.0) {
            sb.append(",\"tps\":").append(String.format(Locale.US, "%.2f", Math.min(20.0, cachedTps)));
        }
        if (detectedCaseTemplate != null && !detectedCaseTemplate.isEmpty()) {
            sb.append(",\"detectedCaseCmd\":").append(MiniJson.quote(detectedCaseTemplate));
        }
        if (detectedPluginsSummary != null && !detectedPluginsSummary.isEmpty()) {
            sb.append(",\"plugins\":").append(MiniJson.quote(detectedPluginsSummary));
        }
        sb.append('}');
        return sb.toString();
    }

    private void ackAsync(String id, boolean ok, String error, String executedCommand) {
        try {
            StringBuilder item = new StringBuilder(160);
            item.append("{\"id\":").append(MiniJson.quote(id))
                .append(",\"ok\":").append(ok);
            if (executedCommand != null && !executedCommand.isEmpty()) {
                item.append(",\"executedCommand\":").append(MiniJson.quote(executedCommand));
            }
            if (error != null && !error.isEmpty()) {
                item.append(",\"error\":").append(MiniJson.quote(error));
            }
            item.append('}');

            String body = "{\"results\":[" + item.toString() + "]}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/minecraft/ack"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("X-Bridge-Secret", secret.trim())
                    .header("User-Agent", "KingGriefBridge/" + getDescription().getVersion())
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                getLogger().warning("Подтверждение выдачи " + id + " вернуло HTTP " + resp.statusCode());
            }
        } catch (Exception ex) {
            getLogger().log(Level.WARNING, "Не удалось подтвердить выдачу " + id + ": " + ex.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "status";
        if ("reload".equals(sub)) {
            loadSettings();
            refreshServerSnapshot();
            detectCasePluginOnMainThread();
            schedulePoll();
            sender.sendMessage("§a[KingGriefBridge] §fКонфигурация перезагружена (§d" + baseUrl + "§f).");
            if (!detectedCaseTemplate.isEmpty()) {
                sender.sendMessage("§7Команда кейсов: §e/" + detectedCaseTemplate);
            }
            return true;
        }
        if ("poll".equals(sub)) {
            sender.sendMessage("§e[KingGriefBridge] §fПринудительный опрос облачной очереди выдачи...");
            Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
                @Override
                public void run() {
                    pollOnce();
                }
            });
            return true;
        }
        if ("testcase".equals(sub)) {
            if (args.length < 3) {
                sender.sendMessage("§cИспользование: /" + label + " testcase <ник> <кейс> [кол-во]");
                return true;
            }
            String targetNick = args[1];
            String targetCase = args[2].toLowerCase(Locale.ROOT);
            int qty = args.length >= 4 ? parsePositiveInt(args[3], 1) : 1;
            if (detectedCaseTemplate == null || detectedCaseTemplate.isEmpty()) {
                detectCasePluginOnMainThread();
            }
            String cmdToRun = resolveExecutableCommand(
                    "dc givekey " + targetNick + " " + targetCase + " " + qty,
                    targetNick,
                    "case",
                    targetCase,
                    qty
            );
            sender.sendMessage("§e[KingGriefBridge] §fТестовая выдача: §b/" + cmdToRun);
            boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmdToRun);
            if (ok && notifyPlayer) {
                notifyOnlinePlayer(targetNick, "case", targetCase, qty);
            }
            sender.sendMessage(ok
                    ? "§a[KingGriefBridge] §fКоманда успешно отправлена в консоль!"
                    : "§c[KingGriefBridge] §fКоманда вернула false. Проверьте плагин кейсов.");
            return true;
        }

        long agoSec = lastSuccessMillis > 0 ? (System.currentTimeMillis() - lastSuccessMillis) / 1000L : -1L;
        sender.sendMessage("§d[KingGriefBridge v" + getDescription().getVersion() + "] §fМагазин: §b" + baseUrl + " §7(" + serverId + ")");
        sender.sendMessage("§7Статус связи: " + (agoSec >= 0 && agoSec < 30
                ? "§aПОДКЛЮЧЕНО §7(" + agoSec + "с назад)"
                : "§eОЖИДАНИЕ" + (lastErrorMessage.isEmpty() ? "" : " §c(" + lastErrorMessage + ")")));
        sender.sendMessage("§7Авто-команда кейсов: §e/" + (detectedCaseTemplate.isEmpty() ? "dc givekey {player} {case} {amount}" : detectedCaseTemplate));
        if (!detectedPluginsSummary.isEmpty()) {
            sender.sendMessage("§7Плагины: §f" + detectedPluginsSummary);
        }
        sender.sendMessage("§7Выдано за сессию: §a" + deliveredCount
                + " §7| §f/" + label + " poll §7| §f/" + label + " testcase <ник> <кейс> 1");
        return true;
    }

    /* ---------------- Reflection & Bukkit Helpers ---------------- */

    private PluginCommand safeGetPluginCommand(String name) {
        try {
            return Bukkit.getPluginCommand(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private PluginCommand firstRegisteredCommand(String... names) {
        for (String n : names) {
            PluginCommand pc = safeGetPluginCommand(n);
            if (pc != null) return pc;
        }
        return null;
    }

    private String preferredRootAlias(String primary, String fallback) {
        return safeGetPluginCommand(primary) != null ? primary : fallback;
    }

    private List<String> listInstalledPluginNames() {
        List<String> names = new ArrayList<String>();
        try {
            Method getPm = Bukkit.class.getMethod("getPluginManager");
            Object pm = getPm.invoke(null);
            if (pm == null) return names;
            Method getPlugins = pm.getClass().getMethod("getPlugins");
            Object arrObj = getPlugins.invoke(pm);
            if (arrObj instanceof Object[]) {
                Object[] arr = (Object[]) arrObj;
                for (Object plugin : arr) {
                    if (plugin == null) continue;
                    Method getName = plugin.getClass().getMethod("getName");
                    Object n = getName.invoke(plugin);
                    if (n != null) names.add(String.valueOf(n));
                }
            }
        } catch (Throwable ignored) {
        }
        return names;
    }

    private boolean hasInstalledPlugin(List<String> names, String target) {
        for (String n : names) {
            if (n.equalsIgnoreCase(target)) return true;
        }
        return false;
    }

    private String readCommandOwnerName(PluginCommand pc) {
        if (pc == null) return "";
        try {
            Method getPlugin = pc.getClass().getMethod("getPlugin");
            Object plugin = getPlugin.invoke(pc);
            if (plugin != null) {
                Method getName = plugin.getClass().getMethod("getName");
                Object n = getName.invoke(plugin);
                if (n != null) return String.valueOf(n);
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private List<String> safeTabComplete(PluginCommand pc, String alias, String[] args) {
        if (pc == null) return Collections.emptyList();
        try {
            ConsoleCommandSender console = Bukkit.getConsoleSender();
            if (console == null) return Collections.emptyList();
            Method tab = Command.class.getMethod("tabComplete", CommandSender.class, String.class, String[].class);
            Object res = tab.invoke(pc, console, alias, args);
            if (res instanceof List) {
                List<?> raw = (List<?>) res;
                List<String> out = new ArrayList<String>(raw.size());
                for (Object item : raw) {
                    if (item != null) out.add(String.valueOf(item).trim());
                }
                return out;
            }
        } catch (Throwable ignored) {
        }
        return Collections.emptyList();
    }

    private String readPlayerName(Player p) {
        if (p == null) return null;
        try {
            Method m = p.getClass().getMethod("getName");
            Object v = m.invoke(p);
            return v != null ? String.valueOf(v) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean containsIgnoreCase(List<String> list, String needle) {
        if (list == null) return false;
        for (String item : list) {
            if (item != null && item.equalsIgnoreCase(needle)) return true;
        }
        return false;
    }

    private static boolean containsAnyKnownCase(List<String> list) {
        if (list == null) return false;
        for (String item : list) {
            if (item != null && KNOWN_CASES.contains(item.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String joinStrings(List<String> items, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(items.get(i));
        }
        return sb.toString();
    }

    private String readMcVersion() {
        try {
            Method m = Bukkit.class.getMethod("getMinecraftVersion");
            Object v = m.invoke(null);
            if (v != null) return String.valueOf(v);
        } catch (Throwable ignored) {
        }
        try {
            String bv = Bukkit.getBukkitVersion();
            if (bv != null) {
                int dash = bv.indexOf('-');
                return dash > 0 ? bv.substring(0, dash) : bv;
            }
        } catch (Throwable ignored) {
        }
        return "1.16.5+";
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
        }
        return -1.0;
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

    private static int asInt(Object o, int fallback) {
        if (o instanceof Number) return ((Number) o).intValue();
        if (o != null) return parsePositiveInt(String.valueOf(o), fallback);
        return fallback;
    }

    private static int parsePositiveInt(String s, int fallback) {
        try {
            int v = Integer.parseInt(s.trim());
            return v > 0 ? v : fallback;
        } catch (Exception e) {
            return fallback;
        }
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
            pos++;
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
            pos++;
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
            pos++;
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
