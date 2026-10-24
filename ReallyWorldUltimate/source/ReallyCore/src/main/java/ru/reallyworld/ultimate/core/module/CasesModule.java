package ru.reallyworld.ultimate.core.module;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import ru.reallyworld.ultimate.core.api.CurrencyType;
import ru.reallyworld.ultimate.core.api.RarityType;
import ru.reallyworld.ultimate.core.api.ReallyModule;
import ru.reallyworld.ultimate.core.config.ConfigManager;
import ru.reallyworld.ultimate.core.config.RankDefinition;
import ru.reallyworld.ultimate.core.storage.StorageModule;
import ru.reallyworld.ultimate.core.storage.model.PlayerProfile;
import ru.reallyworld.ultimate.core.storage.repository.ProgressionRepository;
import ru.reallyworld.ultimate.core.util.TextUtil;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 8-Type Crate / Case Engine with Mathematically Exact Weighted RNG,
 * Atomic Key Consumption, Rank-Downgrade Protection, Drop History, and Broadcasts.
 */
public final class CasesModule implements ReallyModule {
    public record CaseReward(
            String id,
            double weight,
            RarityType rarity,
            String type,
            long amount,
            String value,
            String category,
            String display
    ) {}

    public record CaseDefinition(
            String id,
            String title,
            Material icon,
            int slot,
            long keyPriceRilliks,
            long keyPriceCoins,
            double totalWeight,
            List<CaseReward> rewards
    ) {}

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final StorageModule storage;
    private final LocalizationModule locale;
    private final PlayerModule playerModule;
    private final EconomyModule economyModule;
    private final CustomItemsModule customItemsModule;
    private KitsModule kitsModule;
    private QuestsModule questsModule;

    private final Map<String, CaseDefinition> cases = new LinkedHashMap<>();
    private volatile boolean enabled;

    public CasesModule(
            JavaPlugin plugin,
            ConfigManager configManager,
            StorageModule storage,
            LocalizationModule locale,
            PlayerModule playerModule,
            EconomyModule economyModule,
            CustomItemsModule customItemsModule
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.storage = storage;
        this.locale = locale;
        this.playerModule = playerModule;
        this.economyModule = economyModule;
        this.customItemsModule = customItemsModule;
    }

    public void bindModules(KitsModule kitsModule, QuestsModule questsModule) {
        this.kitsModule = kitsModule;
        this.questsModule = questsModule;
    }

    @Override
    public String id() { return "cases"; }

    @Override
    public String displayName() { return "CasesModule (8 Crates & Keys)"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void enable() {
        this.enabled = configManager.main().getBoolean("modules.cases", true);
        reloadCases();
    }

    @Override
    public void reload() {
        reloadCases();
    }

    @Override
    public void disable() {
        this.enabled = false;
    }

    private synchronized void reloadCases() {
        cases.clear();
        ConfigurationSection sec = configManager.get("cases.yml").getConfigurationSection("cases");
        if (sec == null) return;
        for (String caseId : sec.getKeys(false)) {
            ConfigurationSection c = sec.getConfigurationSection(caseId);
            Material icon = Material.matchMaterial(c.getString("icon", "ENDER_CHEST"));
            List<CaseReward> rewardList = new ArrayList<>();
            double sumWeight = 0.0;
            ConfigurationSection rSec = c.getConfigurationSection("rewards");
            if (rSec != null) {
                for (String rId : rSec.getKeys(false)) {
                    ConfigurationSection r = rSec.getConfigurationSection(rId);
                    double w = r.getDouble("weight", 1.0);
                    sumWeight += w;
                    rewardList.add(new CaseReward(
                            rId,
                            w,
                            RarityType.fromString(r.getString("rarity", "COMMON")),
                            r.getString("type", "COINS"),
                            r.getLong("amount", 1L),
                            r.getString("value", ""),
                            r.getString("category", ""),
                            r.getString("display", rId)
                    ));
                }
            }
            cases.put(caseId.toLowerCase(Locale.ROOT), new CaseDefinition(
                    caseId.toLowerCase(Locale.ROOT),
                    c.getString("title", caseId),
                    icon == null ? Material.ENDER_CHEST : icon,
                    c.getInt("slot", 10),
                    c.getLong("key-price-rilliks", 0L),
                    c.getLong("key-price-coins", 0L),
                    sumWeight,
                    Collections.unmodifiableList(rewardList)
            ));
        }
    }

    public Collection<CaseDefinition> getCases() {
        return cases.values();
    }

    public Optional<CaseDefinition> getCase(String caseId) {
        return Optional.ofNullable(cases.get(caseId.toLowerCase(Locale.ROOT)));
    }

    public Map<String, Integer> getPlayerKeys(UUID playerUuid) {
        return storage.progression().getCaseKeys(playerUuid);
    }

    public int giveKeys(UUID playerUuid, String caseId, int amount) {
        return storage.progression().addCaseKeys(playerUuid, caseId, amount);
    }

    public boolean buyKey(Player player, CaseDefinition def) {
        if (def.keyPriceRilliks() > 0) {
            var res = economyModule.withdraw(player.getUniqueId(), CurrencyType.RILLIKS,
                    def.keyPriceRilliks(), "BUY_CASE_KEY", "Key for " + def.id());
            if (!res.success()) {
                locale.send(player, "economy.not-enough-rilliks", Map.of("needed", TextUtil.formatAmount(def.keyPriceRilliks())));
                return false;
            }
        } else if (def.keyPriceCoins() > 0) {
            var res = economyModule.withdraw(player.getUniqueId(), CurrencyType.COINS,
                    def.keyPriceCoins(), "BUY_CASE_KEY_COINS", "Key for " + def.id());
            if (!res.success()) {
                locale.send(player, "economy.not-enough-coins", Map.of("needed", TextUtil.formatAmount(def.keyPriceCoins())));
                return false;
            }
        } else {
            locale.sendRaw(player, "<red>Ключи этого кейса добываются только на ивентах или у боссов!");
            return false;
        }
        giveKeys(player.getUniqueId(), def.id(), 1);
        locale.sendRaw(player, "<green>Вы приобрели <white>1x Ключ</white> от " + def.title() + "!");
        return true;
    }

    /**
     * Exact weighted roulette draw matching configuration weights.
     */
    public CaseReward rollWeightedReward(CaseDefinition def, Random random) {
        double r = random.nextDouble() * def.totalWeight();
        double cumulative = 0.0;
        for (CaseReward reward : def.rewards()) {
            cumulative += reward.weight();
            if (r <= cumulative) {
                return reward;
            }
        }
        return def.rewards().getLast();
    }

    /**
     * Atomically consumes 1 key (`WHERE amount >= 1`) and returns the rolled reward;
     * call `deliverRolledReward` immediately or at the end of GUI spin animation!
     */
    public Optional<CaseReward> consumeKeyAndRoll(Player player, CaseDefinition def) {
        if (!storage.progression().consumeOneKeyAtomic(player.getUniqueId(), def.id())) {
            locale.send(player, "cases.no-keys", Map.of("case", def.title()));
            return Optional.empty();
        }
        CaseReward won = rollWeightedReward(def, ThreadLocalRandom.current());
        PlayerProfile profile = playerModule.getProfile(player);
        profile.setCasesOpened(profile.casesOpened() + 1);
        storage.runAsync(() -> {
            storage.players().saveProfileStats(profile);
            storage.progression().recordCaseDrop(
                    player.getUniqueId(), player.getName(), def.id(),
                    won.id(), won.display(), won.rarity().name()
            );
        });
        if (questsModule != null) {
            questsModule.recordProgress(player, "OPEN_CASES", 1L);
        }
        return Optional.of(won);
    }

    public void deliverRolledReward(Player player, CaseDefinition def, CaseReward reward) {
        switch (reward.type().toUpperCase(Locale.ROOT)) {
            case "COINS" -> economyModule.deposit(player.getUniqueId(), CurrencyType.COINS,
                    reward.amount(), "CASE_REWARD", def.id() + ":" + reward.id());
            case "RILLIKS" -> economyModule.deposit(player.getUniqueId(), CurrencyType.RILLIKS,
                    reward.amount(), "CASE_REWARD", def.id() + ":" + reward.id());
            case "RANK" -> {
                RankDefinition current = playerModule.getRank(player);
                RankDefinition wonRank = configManager.getRank(reward.value());
                if (wonRank.weight() > current.weight()) {
                    playerModule.setPlayerRank(player.getUniqueId(), player.getName(), wonRank.id());
                } else {
                    long comp = Math.max(50L, wonRank.priceRilliks() / 3L);
                    economyModule.deposit(player.getUniqueId(), CurrencyType.RILLIKS, comp, "CASE_DUP_RANK", wonRank.id());
                    locale.sendRaw(player, "<yellow>У вас уже есть более высокий ранг! Начислена компенсация: <#FF6A00>" + comp + " рилликов</#FF6A00>.");
                }
            }
            case "PREFIX" -> {
                PlayerProfile p = playerModule.getProfile(player);
                p.setCustomPrefix(reward.value());
                storage.runAsync(() -> storage.players().saveProfileStats(p));
            }
            case "KIT" -> {
                if (kitsModule != null) {
                    kitsModule.deliverKitItemsDirect(player, reward.value());
                }
            }
            case "CUSTOM" -> {
                if ("ARMOR_SET".equalsIgnoreCase(reward.category())) {
                    for (ItemStack piece : customItemsModule.createArmorSet(reward.value())) {
                        giveOrDrop(player, piece);
                    }
                } else {
                    customItemsModule.createCustomItem(reward.category(), reward.value(), (int) Math.max(1, reward.amount()))
                            .ifPresent(st -> giveOrDrop(player, st));
                }
            }
            case "VANILLA" -> {
                Material mat = Material.matchMaterial(reward.value());
                if (mat != null) {
                    giveOrDrop(player, new ItemStack(mat, (int) Math.max(1, reward.amount())));
                }
            }
        }

        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.15f);
        locale.send(player, "cases.won", Map.of("case", def.title(), "reward", reward.display()));

        List<String> broadcastRarities = configManager.get("cases.yml").getStringList("settings.broadcast-rarities");
        if (broadcastRarities.contains(reward.rarity().name()) && Bukkit.getServer() != null) {
            Bukkit.getServer().sendMessage(locale.component("cases.rare-broadcast", Map.of(
                    "player", player.getName(),
                    "reward", reward.display(),
                    "case", def.title()
            )));
        }
    }

    public List<ProgressionRepository.CaseHistoryEntry> getRecentDrops(int limit) {
        return storage.progression().getRecentCaseDrops(limit);
    }

    private void giveOrDrop(Player player, ItemStack stack) {
        Map<Integer, ItemStack> rem = player.getInventory().addItem(stack);
        rem.values().forEach(it -> player.getWorld().dropItemNaturally(player.getLocation(), it));
    }
}
