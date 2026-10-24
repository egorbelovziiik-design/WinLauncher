package ru.reallyworld.ultimate.core.module;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.reallyworld.ultimate.core.api.ReallyModule;
import ru.reallyworld.ultimate.core.config.ConfigManager;
import ru.reallyworld.ultimate.core.storage.StorageModule;
import ru.reallyworld.ultimate.core.storage.model.ClanData;
import ru.reallyworld.ultimate.core.storage.repository.PlayerRepository.LeaderboardEntry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Asynchronous Leaderboard & Analytics Module (/top, /baltop, /stats).
 * Caches Top-10 rankings off-thread so GUI/command queries run in O(1) without DB lag.
 */
public final class StatisticsModule implements ReallyModule {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final StorageModule storage;
    private final ClanModule clanModule;

    private final Map<String, List<LeaderboardEntry>> cachedBoards = new ConcurrentHashMap<>();
    private BukkitTask refreshTask;
    private volatile boolean enabled;

    public StatisticsModule(JavaPlugin plugin, ConfigManager configManager, StorageModule storage, ClanModule clanModule) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.storage = storage;
        this.clanModule = clanModule;
    }

    @Override
    public String id() { return "statistics"; }

    @Override
    public String displayName() { return "StatisticsModule (Leaderboards & Analytics)"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void enable() {
        this.enabled = configManager.main().getBoolean("modules.statistics", true);
        refreshAllSync();
        if (plugin != null && Bukkit.getServer() != null) {
            this.refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refreshAllSync, 20L * 60L, 20L * 60L);
        }
    }

    @Override
    public void reload() {
        refreshAllSync();
    }

    @Override
    public void disable() {
        this.enabled = false;
        if (refreshTask != null) refreshTask.cancel();
        cachedBoards.clear();
    }

    public void refreshAllSync() {
        for (String col : List.of("coins", "rilliks", "kills", "playtime_seconds", "boss_kills", "events_won")) {
            cachedBoards.put(col, storage.players().getTopByColumn(col, 10));
        }
    }

    public List<LeaderboardEntry> getTopPlayers(String column) {
        List<LeaderboardEntry> list = cachedBoards.get(column);
        if (list == null) {
            list = storage.players().getTopByColumn(column, 10);
            cachedBoards.put(column, list);
        }
        return list;
    }

    public List<ClanData> getTopClans() {
        return clanModule.getTopClans(10);
    }
}
