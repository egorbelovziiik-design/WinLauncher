package ru.reallyworld.ultimate.core.module;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.reallyworld.ultimate.core.api.CurrencyType;
import ru.reallyworld.ultimate.core.api.ReallyModule;
import ru.reallyworld.ultimate.core.config.ConfigManager;
import ru.reallyworld.ultimate.core.storage.StorageModule;
import ru.reallyworld.ultimate.core.storage.model.PlayerProfile;
import ru.reallyworld.ultimate.core.util.TextUtil;

import java.util.*;

/**
 * Toggleable Seasonal Battle Pass Engine (/bp) with 30 Free & Premium Levels.
 */
public final class BattlePassModule implements ReallyModule {
    public record BpLevel(int level, String freeRewardSpec, String premiumRewardSpec) {}

    private final ConfigManager configManager;
    private final StorageModule storage;
    private final LocalizationModule locale;
    private final PlayerModule playerModule;
    private final EconomyModule economyModule;
    private final CasesModule casesModule;
    private final KitsModule kitsModule;

    private final Map<Integer, BpLevel> levels = new TreeMap<>();
    private volatile boolean enabled;

    public BattlePassModule(
            ConfigManager configManager,
            StorageModule storage,
            LocalizationModule locale,
            PlayerModule playerModule,
            EconomyModule economyModule,
            CasesModule casesModule,
            KitsModule kitsModule
    ) {
        this.configManager = configManager;
        this.storage = storage;
        this.locale = locale;
        this.playerModule = playerModule;
        this.economyModule = economyModule;
        this.casesModule = casesModule;
        this.kitsModule = kitsModule;
    }

    @Override
    public String id() { return "battlepass"; }

    @Override
    public String displayName() { return "BattlePassModule (Season 1)"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void enable() {
        this.enabled = configManager.main().getBoolean("modules.battlepass", true)
                && configManager.get("battlepass.yml").getBoolean("enabled", true);
        reloadLevels();
    }

    @Override
    public void reload() {
        enable();
    }

    @Override
    public void disable() {
        this.enabled = false;
    }

    private synchronized void reloadLevels() {
        levels.clear();
        ConfigurationSection sec = configManager.get("battlepass.yml").getConfigurationSection("levels");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            int lvl = Integer.parseInt(key);
            levels.put(lvl, new BpLevel(
                    lvl,
                    sec.getString(key + ".free", ""),
                    sec.getString(key + ".premium", "")
            ));
        }
    }

    public Collection<BpLevel> getLevels() {
        return levels.values();
    }

    public int calculateLevel(long xp) {
        long xpPerLvl = Math.max(100L, configManager.get("battlepass.yml").getLong("xp-per-level", 250L));
        int maxLvl = configManager.get("battlepass.yml").getInt("max-level", 30);
        return (int) Math.min(maxLvl, xp / xpPerLvl);
    }

    public void addXp(Player player, long xpDelta) {
        if (!enabled || xpDelta <= 0) return;
        PlayerProfile p = playerModule.getProfile(player);
        int oldLvl = calculateLevel(p.battlepassXp());
        p.setBattlepassXp(p.battlepassXp() + xpDelta);
        int newLvl = calculateLevel(p.battlepassXp());
        storage.runAsync(() -> storage.players().saveProfileStats(p));
        if (newLvl > oldLvl) {
            locale.sendRaw(player, "<#FBBC00>Боевой Пропуск повышен до <white>" + newLvl + " уровня</white>! (<gold>/bp</gold>)");
        }
    }

    public boolean buyPremiumPass(Player player) {
        if (!enabled) return false;
        PlayerProfile p = playerModule.getProfile(player);
        if (p.battlepassPremium()) {
            locale.sendRaw(player, "<yellow>У вас уже активирован Премиум Боевой Пропуск!");
            return false;
        }
        long cost = configManager.get("battlepass.yml").getLong("premium-price-rilliks", 399L);
        var res = economyModule.withdraw(player.getUniqueId(), CurrencyType.RILLIKS, cost, "BUY_BP_PREMIUM", "Season 1 Premium");
        if (!res.success()) {
            locale.send(player, "economy.not-enough-rilliks", Map.of("needed", TextUtil.formatAmount(cost)));
            return false;
        }
        p.setBattlepassPremium(true);
        storage.runAsync(() -> storage.players().saveProfileStats(p));
        locale.sendRaw(player, "<green>Премиум Боевой Пропуск успешно разблокирован!");
        return true;
    }

    public boolean claimTrackLevel(Player player, int level, boolean premiumTrack) {
        if (!enabled) return false;
        BpLevel def = levels.get(level);
        if (def == null) return false;
        PlayerProfile p = playerModule.getProfile(player);
        if (calculateLevel(p.battlepassXp()) < level) {
            locale.sendRaw(player, "<red>Вы ещё не достигли " + level + " уровня Боевого Пропуска!");
            return false;
        }
        if (premiumTrack && !p.battlepassPremium()) {
            locale.sendRaw(player, "<red>Для этой награды требуется Премиум Боевой Пропуск!");
            return false;
        }
        String seasonId = "S" + configManager.get("battlepass.yml").getInt("season-id", 1);
        String claimKey = "BP_" + (premiumTrack ? "PREM_" : "FREE_") + level;
        if (!storage.progression().tryClaimRewardAtomic(player.getUniqueId(), claimKey, seasonId)) {
            locale.sendRaw(player, "<yellow>Эта награда уже получена!");
            return false;
        }
        String spec = premiumTrack ? def.premiumRewardSpec() : def.freeRewardSpec();
        grantRewardSpec(player, spec);
        locale.sendRaw(player, "<green>Получена награда " + level + " уровня Боевого Пропуска!");
        return true;
    }

    private void grantRewardSpec(Player player, String spec) {
        if (spec == null || spec.isBlank()) return;
        String[] parts = spec.split(":");
        switch (parts[0].toUpperCase(Locale.ROOT)) {
            case "COINS" -> economyModule.deposit(player.getUniqueId(), CurrencyType.COINS, Long.parseLong(parts[1]), "BP_REWARD", spec);
            case "RILLIKS" -> economyModule.deposit(player.getUniqueId(), CurrencyType.RILLIKS, Long.parseLong(parts[1]), "BP_REWARD", spec);
            case "CASE_KEY" -> casesModule.giveKeys(player.getUniqueId(), parts[1], Integer.parseInt(parts[2]));
            case "CUSTOM", "VANILLA" -> {
                for (ItemStack item : kitsModule.parseItemSpec(spec)) {
                    player.getInventory().addItem(item).values()
                            .forEach(rem -> player.getWorld().dropItemNaturally(player.getLocation(), rem));
                }
            }
        }
    }
}
