package net.kinggrief.bridge;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * KingGriefBridge — безопасная выдача заказов из магазина KINGGRIEF.
 *
 * Плагин периодически опрашивает /api/minecraft/poll, выполняет команды в главном
 * потоке сервера и подтверждает результат через /api/minecraft/ack.
 * Повторная выдача защищена на стороне магазина уникальностью задач.
 */
public final class KingGriefBridge extends JavaPlugin {

    private final Gson gson = new Gson();
    private HttpClient http;
    private BukkitTask pollTask;
    private String baseUrl;
    private String secret;
    private String serverId;
    private List<String> allowedPrefixes = new ArrayList<>();
    private boolean polling = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();

        http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        long intervalTicks = Math.max(20L, getConfig().getLong("poll-interval-seconds", 5L) * 20L);
        pollTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::pollSafe, 40L, intervalTicks);

        getLogger().info("KingGriefBridge enabled. Store: " + baseUrl + " · server: " + serverId);
        if (secret.equals("change-me-bridge-secret")) {
            getLogger().warning("Секрет не изменён! Задайте secret в config.yml и MINECRAFT_BRIDGE_SECRET на сайте.");
        }
    }

    @Override
    public void onDisable() {
        if (pollTask != null) pollTask.cancel();
        getLogger().info("KingGriefBridge disabled.");
    }

    private void loadSettings() {
        reloadConfig();
        baseUrl = getConfig().getString("base-url", "").replaceAll("/+$", "");
        secret = getConfig().getString("secret", "");
        serverId = getConfig().getString("server-id", "main");
        allowedPrefixes = new ArrayList<>();
        for (String prefix : getConfig().getStringList("allowed-command-prefixes")) {
            if (prefix != null && !prefix.isBlank()) allowedPrefixes.add(prefix.toLowerCase(Locale.ROOT));
        }
    }

    private void pollSafe() {
        if (polling) return;
        polling = true;
        try {
            pollOnce().whenComplete((ignored, error) -> {
                polling = false;
                if (error != null) {
                    getLogger().log(Level.WARNING, "Ошибка опроса магазина: " + error.getMessage());
                }
            });
        } catch (Exception exception) {
            polling = false;
            getLogger().log(Level.WARNING, "Ошибка опроса магазина", exception);
        }
    }

    private CompletableFuture<Void> pollOnce() {
        JsonObject body = new JsonObject();
        body.addProperty("serverId", serverId);
        body.addProperty("online", Bukkit.getOnlinePlayers().size());
        body.addProperty("max", Bukkit.getMaxPlayers());
        body.addProperty("version", Bukkit.getVersion());
        Double tps = readTps();
        if (tps != null) body.addProperty("tps", tps);

        HttpRequest request = request("/api/minecraft/poll", body.toString());
        if (request == null) return CompletableFuture.completedFuture(null);

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenAccept(response -> {
                    if (response.statusCode() == 401) {
                        getLogger().warning("Магазин отклонил секрет (401). Проверьте secret и MINECRAFT_BRIDGE_SECRET.");
                        return;
                    }
                    if (response.statusCode() != 200) {
                        getLogger().warning("Poll HTTP " + response.statusCode() + ": " + trim(response.body()));
                        return;
                    }
                    JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                    if (json == null || !json.has("commands")) return;
                    JsonArray commands = json.getAsJsonArray("commands");
                    if (commands.isEmpty()) return;

                    // Все команды выполняются последовательно в главном потоке, затем одним ack.
                    Bukkit.getScheduler().runTask(this, () -> {
                        List<JsonObject> results = new ArrayList<>();
                        for (JsonElement element : commands) {
                            JsonObject command = element.getAsJsonObject();
                            results.add(execute(command.get("id").getAsString(), command.get("command").getAsString()));
                        }
                        ack(results);
                    });
                });
    }

    private JsonObject execute(String id, String rawCommand) {
        JsonObject result = new JsonObject();
        result.addProperty("id", id);
        try {
            if (rawCommand == null || rawCommand.isBlank()) {
                throw new IllegalArgumentException("Пустая команда");
            }
            if (rawCommand.contains("\n") || rawCommand.contains("\r")) {
                throw new IllegalArgumentException("Многострочные команды запрещены");
            }
            String command = rawCommand.startsWith("/") ? rawCommand.substring(1) : rawCommand;
            if (!isAllowed(command)) {
                throw new IllegalArgumentException("Префикс команды не в белом списке");
            }
            boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            result.addProperty("ok", ok);
            if (!ok) result.addProperty("error", "Сервер вернул false (команда не выполнена)");
            if (getConfig().getBoolean("log-deliveries", true)) {
                getLogger().info((ok ? "Выдано: " : "Не выполнено: ") + command);
            }
        } catch (Exception exception) {
            result.addProperty("ok", false);
            result.addProperty("error", trim(exception.getMessage()));
            getLogger().warning("Не удалось выполнить выдачу: " + trim(exception.getMessage()));
        }
        return result;
    }

    private boolean isAllowed(String command) {
        if (allowedPrefixes.isEmpty()) return false; // безопасный дефолт: запрет всего
        String lower = command.toLowerCase(Locale.ROOT);
        for (String prefix : allowedPrefixes) {
            if (lower.startsWith(prefix) || lower.equals(prefix.trim())) return true;
        }
        return false;
    }

    private void ack(List<JsonObject> results) {
        if (results.isEmpty()) return;
        JsonObject body = new JsonObject();
        JsonArray array = new JsonArray();
        for (JsonObject result : results) array.add(result);
        body.add("results", array);

        HttpRequest request = request("/api/minecraft/ack", body.toString());
        if (request == null) return;
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        getLogger().warning("Ack HTTP " + response.statusCode() + ": " + trim(response.body()));
                    }
                })
                .exceptionally(error -> {
                    getLogger().warning("Ack не отправлен: " + error.getMessage());
                    return null;
                });
    }

    private HttpRequest request(String path, String payload) {
        if (baseUrl == null || baseUrl.isBlank() || secret == null || secret.isBlank()) {
            getLogger().warning("base-url или secret не заданы в config.yml");
            return null;
        }
        try {
            return HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofMillis(getConfig().getLong("http-timeout-ms", 8000L)))
                    .header("Content-Type", "application/json")
                    .header("X-Bridge-Secret", secret)
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
        } catch (IllegalArgumentException exception) {
            getLogger().warning("Некорректный base-url: " + baseUrl);
            return null;
        }
    }

    /** TPS доступен в Paper/Purpur; в Spigot метод отсутствует — возвращаем null. */
    private Double readTps() {
        try {
            double[] tps = (double[]) Bukkit.getServer().getClass().getMethod("getTPS").invoke(Bukkit.getServer());
            if (tps.length > 0) return Math.round(tps[0] * 100.0) / 100.0;
        } catch (ReflectiveOperationException ignored) {
            // Spigot: TPS не отдаём
        }
        return null;
    }

    private static String trim(String value) {
        if (value == null) return "";
        String single = value.replace('\n', ' ').replace('\r', ' ');
        return single.length() > 300 ? single.substring(0, 300) + "…" : single;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage("§dKingGriefBridge §7· store " + baseUrl + " · server " + serverId
                    + " · prefix-whitelist " + allowedPrefixes.size());
            return true;
        }
        if (args[0].equalsIgnoreCase("poll")) {
            pollSafe();
            sender.sendMessage("§aОчередь запрошена. Результат смотрите в консоли.");
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            loadSettings();
            sender.sendMessage("§aКонфигурация перезагружена.");
            return true;
        }
        sender.sendMessage("§cИспользование: /kgbridge status | poll | reload");
        return true;
    }
}
