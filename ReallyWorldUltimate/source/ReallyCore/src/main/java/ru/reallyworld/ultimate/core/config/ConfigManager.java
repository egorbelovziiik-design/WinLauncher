package ru.reallyworld.ultimate.core.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Loads, validates, and hot-reloads all YAML configuration files safely.
 * Resolves environment placeholders like ${RW_ITEM_SALT:default}.
 */
public final class ConfigManager {
    public static final List<String> CONFIG_FILES = List.of(
            "config.yml",
            "messages_ru.yml",
            "ranks.yml",
            "security.yml",
            "economy.yml",
            "shop.yml",
            "auction.yml",
            "clans.yml",
            "pvp.yml",
            "spheres.yml",
            "talismans.yml",
            "artifacts.yml",
            "weapons.yml",
            "armor.yml",
            "consumables.yml",
            "cases.yml",
            "kits.yml",
            "events.yml",
            "bosses.yml",
            "quests.yml",
            "battlepass.yml",
            "rewards.yml",
            "menus.yml",
            "scoreboard_tab.yml",
            "spawn_npcs_holograms.yml"
    );

    private final File dataFolder;
    private final Logger logger;
    private final Map<String, YamlConfiguration> configs = new ConcurrentHashMap<>();
    private volatile Map<String, RankDefinition> ranks = Map.of();

    public ConfigManager(File dataFolder, Logger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    public synchronized void loadAll() {
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            logger.warning("Could not create plugin data directory: " + dataFolder);
        }
        for (String fileName : CONFIG_FILES) {
            File target = new File(dataFolder, fileName);
            if (!target.exists()) {
                copyDefaultResource(fileName, target);
            }
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(target);
            validateConfig(fileName, yaml);
            configs.put(fileName, yaml);
        }
        rebuildRankHierarchy();
        validateShopAntiArbitrage();
    }

    private void copyDefaultResource(String resourcePath, File target) {
        try (InputStream in = ConfigManager.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in != null) {
                if (target.getParentFile() != null) {
                    target.getParentFile().mkdirs();
                }
                Files.copy(in, target.toPath());
            }
        } catch (Exception e) {
            logger.severe("Failed to extract default resource " + resourcePath + ": " + e.getMessage());
        }
    }

    private void validateConfig(String fileName, YamlConfiguration yaml) {
        if (yaml.getKeys(false).isEmpty()) {
            throw new IllegalStateException("Configuration file is empty or corrupt: " + fileName);
        }
        if ("cases.yml".equals(fileName)) {
            ConfigurationSection casesSec = yaml.getConfigurationSection("cases");
            if (casesSec == null || casesSec.getKeys(false).isEmpty()) {
                throw new IllegalStateException("cases.yml must define at least one case");
            }
            for (String caseId : casesSec.getKeys(false)) {
                ConfigurationSection rewards = casesSec.getConfigurationSection(caseId + ".rewards");
                if (rewards == null || rewards.getKeys(false).isEmpty()) {
                    throw new IllegalStateException("Case '" + caseId + "' has no rewards configured");
                }
                double totalWeight = 0.0;
                for (String rKey : rewards.getKeys(false)) {
                    double w = rewards.getDouble(rKey + ".weight", 0.0);
                    if (w <= 0.0) {
                        throw new IllegalStateException("Invalid weight <= 0 in case " + caseId + "." + rKey);
                    }
                    totalWeight += w;
                }
                if (totalWeight <= 0.0) {
                    throw new IllegalStateException("Case '" + caseId + "' total weight must be positive");
                }
            }
        }
    }

    /**
     * Mathematically verifies there are NO infinite money arbitrage loops between /shop and /buyer,
     * even under the highest hot-deal multiplier!
     */
    public void validateShopAntiArbitrage() {
        YamlConfiguration eco = get("economy.yml");
        YamlConfiguration shop = get("shop.yml");
        List<Integer> mults = eco.getIntegerList("buyer.hot-deal-multipliers");
        int maxHotMultiplier = mults.stream().mapToInt(Integer::intValue).max().orElse(3);

        ConfigurationSection categories = shop.getConfigurationSection("categories");
        if (categories == null) return;
        for (String catKey : categories.getKeys(false)) {
            ConfigurationSection items = categories.getConfigurationSection(catKey + ".items");
            if (items == null) continue;
            for (String itemKey : items.getKeys(false)) {
                long buyPrice = items.getLong(itemKey + ".buy-coins", 0L);
                long sellPrice = items.getLong(itemKey + ".sell-coins", 0L);
                int amount = items.getInt(itemKey + ".amount", 1);
                if (amount <= 0) {
                    throw new IllegalStateException("Invalid amount in shop.yml: " + catKey + "." + itemKey);
                }
                if (buyPrice > 0 && sellPrice > 0) {
                    long maxHotSell = Math.multiplyExact(sellPrice, maxHotMultiplier);
                    if (maxHotSell >= buyPrice) {
                        throw new IllegalStateException(
                                "Shop arbitrage loop detected for " + catKey + "." + itemKey
                                        + ": buy=" + buyPrice + " vs hotSell(x" + maxHotMultiplier + ")=" + maxHotSell
                        );
                    }
                }
            }
        }
    }

    private void rebuildRankHierarchy() {
        YamlConfiguration yaml = get("ranks.yml");
        ConfigurationSection sec = yaml.getConfigurationSection("ranks");
        if (sec == null) return;

        Map<String, ConfigurationSection> rawRanks = new LinkedHashMap<>();
        for (String key : sec.getKeys(false)) {
            rawRanks.put(key.toUpperCase(Locale.ROOT), sec.getConfigurationSection(key));
        }

        Map<String, RankDefinition> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, ConfigurationSection> entry : rawRanks.entrySet()) {
            String rankId = entry.getKey();
            ConfigurationSection r = entry.getValue();
            Set<String> perms = new LinkedHashSet<>();
            collectInheritedPermissions(rankId, rawRanks, perms, new HashSet<>(), true);
            RankDefinition def = new RankDefinition(
                    rankId,
                    r.getString("alias", ""),
                    r.getInt("weight", 10),
                    r.getString("display-name", rankId),
                    r.getString("prefix", ""),
                    r.getString("tab-prefix", ""),
                    r.getString("color", "<white>"),
                    r.getLong("price-rilliks", 0L),
                    r.getBoolean("purchasable", r.getLong("price-rilliks", 0L) > 0),
                    r.getInt("max-homes", 2),
                    r.getInt("auction-slots", 3),
                    r.getDouble("kill-coin-multiplier", 1.0),
                    r.getInt("monthly-grant-quota", 0),
                    r.getStringList("inherits"),
                    Collections.unmodifiableSet(perms)
            );
            resolved.put(rankId, def);
            if (!def.alias().isBlank()) {
                resolved.putIfAbsent(def.alias().toUpperCase(Locale.ROOT), def);
            }
        }
        this.ranks = Collections.unmodifiableMap(resolved);
    }

    private void collectInheritedPermissions(
            String rankId,
            Map<String, ConfigurationSection> rawRanks,
            Set<String> target,
            Set<String> visited,
            boolean isSelf
    ) {
        if (!visited.add(rankId)) return;
        ConfigurationSection sec = rawRanks.get(rankId);
        if (sec == null) return;
        for (String parent : sec.getStringList("inherits")) {
            collectInheritedPermissions(parent.toUpperCase(Locale.ROOT), rawRanks, target, visited, false);
        }
        for (String perm : sec.getStringList("permissions")) {
            // Per ReallyWorld specification: inherited ranks do NOT inherit personal /kit <rank>, except plus/pluspro
            if (!isSelf && perm.startsWith("reallycore.kit.")
                    && !perm.equals("reallycore.kit.plus")
                    && !perm.equals("reallycore.kit.pluspro")) {
                continue;
            }
            target.add(perm.toLowerCase(Locale.ROOT));
        }
    }

    public YamlConfiguration get(String fileName) {
        YamlConfiguration cfg = configs.get(fileName);
        if (cfg == null) {
            throw new IllegalArgumentException("Unknown configuration file: " + fileName);
        }
        return cfg;
    }

    public YamlConfiguration main() {
        return get("config.yml");
    }

    public RankDefinition getRank(String rankIdOrAlias) {
        if (rankIdOrAlias == null) return ranks.get("PLAYER");
        RankDefinition found = ranks.get(rankIdOrAlias.toUpperCase(Locale.ROOT));
        return found != null ? found : ranks.get("PLAYER");
    }

    public Collection<RankDefinition> getPrimaryRanks() {
        Map<String, RankDefinition> unique = new LinkedHashMap<>();
        for (RankDefinition def : ranks.values()) {
            unique.putIfAbsent(def.id(), def);
        }
        return unique.values();
    }

    public String resolveEnvValue(String raw) {
        if (raw == null) return "";
        if (raw.startsWith("${") && raw.endsWith("}")) {
            String inner = raw.substring(2, raw.length() - 1);
            int idx = inner.indexOf(':');
            String varName = idx >= 0 ? inner.substring(0, idx) : inner;
            String fallback = idx >= 0 ? inner.substring(idx + 1) : "";
            String env = System.getenv(varName);
            return (env != null && !env.isBlank()) ? env : fallback;
        }
        return raw;
    }
}
