package ru.reallyworld.ultimate.core.module;

import ru.reallyworld.ultimate.core.api.ReallyModule;
import ru.reallyworld.ultimate.core.config.ConfigManager;
import ru.reallyworld.ultimate.core.storage.StorageModule;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CooldownModule implements ReallyModule {
    private final ConfigManager configManager;
    private final StorageModule storage;
    private final Map<UUID, Map<String, Long>> cache = new ConcurrentHashMap<>();
    private volatile boolean enabled;

    public CooldownModule(ConfigManager configManager, StorageModule storage) {
        this.configManager = configManager;
        this.storage = storage;
    }

    @Override
    public String id() { return "cooldowns"; }

    @Override
    public String displayName() { return "CooldownModule"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void enable() {
        this.enabled = configManager.main().getBoolean("modules.cooldowns", true);
    }

    @Override
    public void reload() {
        enable();
    }

    @Override
    public void disable() {
        this.enabled = false;
        cache.clear();
    }

    public void loadPlayer(UUID playerUuid) {
        Map<String, Long> active = storage.progression().loadActiveCooldowns(playerUuid, System.currentTimeMillis());
        cache.put(playerUuid, new ConcurrentHashMap<>(active));
    }

    public void unloadPlayer(UUID playerUuid) {
        cache.remove(playerUuid);
    }

    public long getRemainingSeconds(UUID playerUuid, String key) {
        if (!enabled) return 0L;
        Map<String, Long> map = cache.get(playerUuid);
        if (map == null) return 0L;
        Long exp = map.get(key);
        if (exp == null) return 0L;
        long diffMs = exp - System.currentTimeMillis();
        if (diffMs <= 0) {
            map.remove(key);
            return 0L;
        }
        return (diffMs + 999L) / 1000L;
    }

    public boolean isOnCooldown(UUID playerUuid, String key) {
        return getRemainingSeconds(playerUuid, key) > 0L;
    }

    public void setCooldown(UUID playerUuid, String key, long durationSeconds, boolean persistToSql) {
        if (!enabled || durationSeconds <= 0) return;
        long expiresAt = System.currentTimeMillis() + (durationSeconds * 1000L);
        cache.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>()).put(key, expiresAt);
        if (persistToSql) {
            storage.runAsync(() -> storage.progression().setCooldown(playerUuid, key, expiresAt));
        }
    }
}
