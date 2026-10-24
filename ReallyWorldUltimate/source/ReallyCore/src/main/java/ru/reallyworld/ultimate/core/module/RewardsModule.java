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

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.util.Map;
import java.util.Set;

/**
 * Daily 7-Day Streak (/daily), Weekly Bonus, Playtime Milestones, and Achievements (/rewards).
 * Protected against double-claiming via SQL Primary Key locks.
 */
public final class RewardsModule implements ReallyModule {
    private final ConfigManager configManager;
    private final StorageModule storage;
    private final LocalizationModule locale;
    private final PlayerModule playerModule;
    private final EconomyModule economyModule;
    private final CasesModule casesModule;
    private final KitsModule kitsModule;
    private volatile boolean enabled;

    public RewardsModule(
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
    public String id() { return "rewards"; }

    @Override
    public String displayName() { return "RewardsModule (Daily, Weekly, Playtime, Achievements)"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void enable() {
        this.enabled = configManager.main().getBoolean("modules.rewards", true);
    }

    @Override
    public void reload() {
        enable();
    }

    @Override
    public void disable() {
        this.enabled = false;
    }

    public boolean claimDaily(Player player) {
        long epochDay = LocalDate.now(ZoneOffset.UTC).toEpochDay();
        String dayPeriod = "DAY:" + epochDay;
        if (!storage.progression().tryClaimRewardAtomic(player.getUniqueId(), "DAILY", dayPeriod)) {
            locale.send(player, "rewards.daily-already");
            return false;
        }

        PlayerProfile p = playerModule.getProfile(player);
        int nextStreak = (p.lastDailyDay() == epochDay - 1) ? (p.dailyStreak() % 7) + 1 : 1;
        p.setDailyStreak(nextStreak);
        p.setLastDailyDay(epochDay);
        storage.runAsync(() -> storage.players().saveProfileStats(p));

        var cfg = configManager.get("rewards.yml").getConfigurationSection("daily-streak.days." + nextStreak);
        if (cfg != null) {
            payoutSection(player, cfg, "DAILY_DAY_" + nextStreak);
        }
        locale.send(player, "rewards.daily-claimed", Map.of(
                "day", String.valueOf(nextStreak),
                "reward", cfg != null ? cfg.getLong("coins", 0) + " монет" : "бонус"
        ));
        return true;
    }

    public boolean claimWeekly(Player player) {
        PlayerProfile p = playerModule.getProfile(player);
        long minSec = configManager.get("rewards.yml").getLong("weekly-bonus.min-playtime-seconds", 10800L);
        if (p.playtimeSeconds() < minSec) {
            locale.sendRaw(player, "<red>Для еженедельного бонуса нужно наиграть минимум "
                    + TextUtil.formatDuration(minSec) + "!");
            return false;
        }
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        String weekKey = "W:" + now.get(IsoFields.WEEK_BASED_YEAR) + "-" + now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        if (!storage.progression().tryClaimRewardAtomic(player.getUniqueId(), "WEEKLY", weekKey)) {
            locale.sendRaw(player, "<yellow>Вы уже забирали еженедельный бонус на этой неделе!");
            return false;
        }
        var cfg = configManager.get("rewards.yml").getConfigurationSection("weekly-bonus");
        if (cfg != null) payoutSection(player, cfg, "WEEKLY");
        locale.sendRaw(player, "<green>Еженедельный бонус успешно получен!");
        return true;
    }

    public boolean claimPlaytimeMilestone(Player player, String milestoneKey) {
        var cfg = configManager.get("rewards.yml").getConfigurationSection("playtime-milestones." + milestoneKey);
        if (cfg == null) return false;
        PlayerProfile p = playerModule.getProfile(player);
        long req = cfg.getLong("seconds", 3600L);
        if (p.playtimeSeconds() < req) {
            locale.sendRaw(player, "<red>Недостаточно наигранного времени ("
                    + TextUtil.formatDuration(p.playtimeSeconds()) + " / " + TextUtil.formatDuration(req) + ")!");
            return false;
        }
        if (!storage.progression().tryClaimRewardAtomic(player.getUniqueId(), "PLAYTIME_" + milestoneKey, "ONCE")) {
            locale.sendRaw(player, "<yellow>Вы уже получили эту награду за онлайн!");
            return false;
        }
        payoutSection(player, cfg, "PLAYTIME_" + milestoneKey);
        locale.sendRaw(player, "<green>Получена награда за онлайн: <white>" + cfg.getString("title", milestoneKey) + "</white>!");
        return true;
    }

    public boolean claimAchievement(Player player, String achId) {
        var cfg = configManager.get("rewards.yml").getConfigurationSection("achievements." + achId);
        if (cfg == null) return false;
        PlayerProfile p = playerModule.getProfile(player);
        String metric = cfg.getString("metric", "kills");
        long target = cfg.getLong("target", 1L);
        long current = switch (metric) {
            case "kills" -> p.kills();
            case "coins" -> p.coins();
            case "boss_kills" -> p.bossKills();
            case "events_won" -> p.eventsWon();
            case "cases_opened" -> p.casesOpened();
            default -> 0L;
        };
        if (current < target) {
            locale.sendRaw(player, "<red>Достижение ещё не выполнено (" + current + " / " + target + ")!");
            return false;
        }
        if (!storage.progression().tryClaimRewardAtomic(player.getUniqueId(), "ACH_" + achId, "ONCE")) {
            locale.sendRaw(player, "<yellow>Награда за это достижение уже получена!");
            return false;
        }
        payoutSection(player, cfg, "ACH_" + achId);
        locale.sendRaw(player, "<#FBBC00>Достижение «<white>" + cfg.getString("title", achId) + "</white>» выполнено! Награда выдана.");
        return true;
    }

    public Set<String> getClaimedKeys(Player player) {
        return storage.progression().loadClaimedRewards(player.getUniqueId());
    }

    private void payoutSection(Player player, ConfigurationSection sec, String reason) {
        long coins = sec.getLong("coins", 0L);
        long rilliks = sec.getLong("rilliks", 0L);
        String caseKey = sec.getString("case-key", "");
        String itemSpec = sec.getString("item", "");

        if (coins > 0) economyModule.deposit(player.getUniqueId(), CurrencyType.COINS, coins, "REWARD", reason);
        if (rilliks > 0) economyModule.deposit(player.getUniqueId(), CurrencyType.RILLIKS, rilliks, "REWARD", reason);
        if (caseKey != null && !caseKey.isBlank()) {
            String[] parts = caseKey.split(":");
            casesModule.giveKeys(player.getUniqueId(), parts[0], parts.length > 1 ? Integer.parseInt(parts[1]) : 1);
        }
        if (itemSpec != null && !itemSpec.isBlank()) {
            for (ItemStack it : kitsModule.parseItemSpec(itemSpec)) {
                player.getInventory().addItem(it).values()
                        .forEach(rem -> player.getWorld().dropItemNaturally(player.getLocation(), rem));
            }
        }
    }
}
