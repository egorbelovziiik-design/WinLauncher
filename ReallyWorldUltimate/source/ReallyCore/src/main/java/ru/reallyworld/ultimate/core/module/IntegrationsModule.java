package ru.reallyworld.ultimate.core.module;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.milkbowl.vault.economy.AbstractEconomy;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import ru.reallyworld.ultimate.core.api.CurrencyType;
import ru.reallyworld.ultimate.core.api.ReallyModule;
import ru.reallyworld.ultimate.core.config.ConfigManager;
import ru.reallyworld.ultimate.core.storage.model.ClanData;
import ru.reallyworld.ultimate.core.storage.model.PlayerProfile;
import ru.reallyworld.ultimate.core.util.TextUtil;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Safe Soft-Depend Hooks for Vault Economy, PlaceholderAPI (%reallycore_*%), and LuckPerms.
 * Gracefully degrades with zero errors when optional third-party plugins are absent.
 */
public final class IntegrationsModule implements ReallyModule {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final PlayerModule playerModule;
    private final EconomyModule economyModule;
    private final ClanModule clanModule;
    private final EventsModule eventsModule;
    private final Logger logger;

    private boolean vaultHooked;
    private boolean papiHooked;
    private boolean luckPermsHooked;
    private volatile boolean enabled;

    public IntegrationsModule(
            JavaPlugin plugin,
            ConfigManager configManager,
            PlayerModule playerModule,
            EconomyModule economyModule,
            ClanModule clanModule,
            EventsModule eventsModule,
            Logger logger
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playerModule = playerModule;
        this.economyModule = economyModule;
        this.clanModule = clanModule;
        this.eventsModule = eventsModule;
        this.logger = logger;
    }

    @Override
    public String id() { return "integrations"; }

    @Override
    public String displayName() { return "IntegrationsModule (Vault, PAPI, LuckPerms)"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void enable() {
        this.enabled = configManager.main().getBoolean("modules.integrations", true);
        if (!enabled || plugin == null || Bukkit.getServer() == null) return;

        if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            try {
                registerVaultProvider();
                this.vaultHooked = true;
                logger.info("[Integrations] Registered ReallyCore as Vault Economy provider.");
            } catch (Throwable t) {
                logger.warning("[Integrations] Vault hook skipped: " + t.getMessage());
            }
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                registerPapiExpansion();
                this.papiHooked = true;
                logger.info("[Integrations] Registered %reallycore_*% PlaceholderAPI expansion.");
            } catch (Throwable t) {
                logger.warning("[Integrations] PlaceholderAPI hook skipped: " + t.getMessage());
            }
        }

        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                LuckPerms lp = LuckPermsProvider.get();
                if (lp != null) {
                    this.luckPermsHooked = true;
                    logger.info("[Integrations] Connected to LuckPerms v5 API.");
                }
            } catch (Throwable t) {
                logger.warning("[Integrations] LuckPerms hook skipped: " + t.getMessage());
            }
        }
    }

    @Override
    public String healthStatus() {
        if (!enabled) return "DISABLED";
        return "OK (Vault=" + vaultHooked + ", PAPI=" + papiHooked + ", LP=" + luckPermsHooked + ")";
    }

    @Override
    public void disable() {
        this.enabled = false;
    }

    public void syncRankToLuckPermsIfPresent(Player player, String rankId) {
        if (!luckPermsHooked) return;
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                user.setPrimaryGroup(rankId.toLowerCase(Locale.ROOT));
                lp.getUserManager().saveUser(user);
            }
        } catch (Throwable ignored) {}
    }

    private void registerVaultProvider() {
        Economy provider = new AbstractEconomy() {
            @Override public boolean isEnabled() { return economyModule.isEnabled(); }
            @Override public String getName() { return "ReallyCoreEconomy"; }
            @Override public boolean hasBankSupport() { return false; }
            @Override public int fractionalDigits() { return 0; }
            @Override public String format(double amount) { return TextUtil.formatAmount(Math.round(amount)) + " ⛃"; }
            @Override public String currencyNamePlural() { return "монет"; }
            @Override public String currencyNameSingular() { return "монета"; }

            @Override
            public boolean hasAccount(OfflinePlayer player) {
                return player != null && playerModule.findProfile(player.getUniqueId()).isPresent();
            }

            @Override
            public boolean hasAccount(String playerName) {
                return playerModule.findProfileByName(playerName).isPresent();
            }

            @Override
            public double getBalance(OfflinePlayer player) {
                return player == null ? 0.0 : economyModule.getBalance(player.getUniqueId(), CurrencyType.COINS);
            }

            @Override
            public double getBalance(String playerName) {
                return playerModule.findProfileByName(playerName)
                        .map(p -> (double) economyModule.getBalance(p.uuid(), CurrencyType.COINS))
                        .orElse(0.0);
            }

            @Override
            public boolean has(OfflinePlayer player, double amount) {
                return getBalance(player) >= Math.round(amount);
            }

            @Override
            public boolean has(String playerName, double amount) {
                return getBalance(playerName) >= Math.round(amount);
            }

            @Override
            public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
                long exact = Math.round(amount);
                if (exact <= 0) return new EconomyResponse(0, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Invalid amount");
                var res = economyModule.withdraw(player.getUniqueId(), CurrencyType.COINS, exact, "VAULT_WITHDRAW", "Vault API");
                return res.success()
                        ? new EconomyResponse(exact, res.newBalance(), EconomyResponse.ResponseType.SUCCESS, "")
                        : new EconomyResponse(0, res.newBalance(), EconomyResponse.ResponseType.FAILURE, res.failureReason());
            }

            @Override
            public EconomyResponse withdrawPlayer(String playerName, double amount) {
                Optional<PlayerProfile> p = playerModule.findProfileByName(playerName);
                if (p.isEmpty()) return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Player not found");
                long exact = Math.round(amount);
                var res = economyModule.withdraw(p.get().uuid(), CurrencyType.COINS, exact, "VAULT_WITHDRAW", "Vault API");
                return res.success()
                        ? new EconomyResponse(exact, res.newBalance(), EconomyResponse.ResponseType.SUCCESS, "")
                        : new EconomyResponse(0, res.newBalance(), EconomyResponse.ResponseType.FAILURE, res.failureReason());
            }

            @Override
            public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
                long exact = Math.round(amount);
                if (exact <= 0) return new EconomyResponse(0, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Invalid amount");
                var res = economyModule.deposit(player.getUniqueId(), CurrencyType.COINS, exact, "VAULT_DEPOSIT", "Vault API");
                return res.success()
                        ? new EconomyResponse(exact, res.newBalance(), EconomyResponse.ResponseType.SUCCESS, "")
                        : new EconomyResponse(0, res.newBalance(), EconomyResponse.ResponseType.FAILURE, res.failureReason());
            }

            @Override
            public EconomyResponse depositPlayer(String playerName, double amount) {
                Optional<PlayerProfile> p = playerModule.findProfileByName(playerName);
                if (p.isEmpty()) return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Player not found");
                long exact = Math.round(amount);
                var res = economyModule.deposit(p.get().uuid(), CurrencyType.COINS, exact, "VAULT_DEPOSIT", "Vault API");
                return res.success()
                        ? new EconomyResponse(exact, res.newBalance(), EconomyResponse.ResponseType.SUCCESS, "")
                        : new EconomyResponse(0, res.newBalance(), EconomyResponse.ResponseType.FAILURE, res.failureReason());
            }

            @Override public boolean createPlayerAccount(OfflinePlayer player) { return true; }
            @Override public boolean createPlayerAccount(String playerName) { return true; }
            @Override public boolean hasAccount(String p, String w) { return hasAccount(p); }
            @Override public double getBalance(String p, String w) { return getBalance(p); }
            @Override public boolean has(String p, String w, double a) { return has(p, a); }
            @Override public EconomyResponse withdrawPlayer(String p, String w, double a) { return withdrawPlayer(p, a); }
            @Override public EconomyResponse depositPlayer(String p, String w, double a) { return depositPlayer(p, a); }
            @Override public boolean createPlayerAccount(String p, String w) { return true; }
            @Override public EconomyResponse createBank(String n, String p) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, ""); }
            @Override public EconomyResponse deleteBank(String n) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, ""); }
            @Override public EconomyResponse bankBalance(String n) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, ""); }
            @Override public EconomyResponse bankHas(String n, double a) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, ""); }
            @Override public EconomyResponse bankWithdraw(String n, double a) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, ""); }
            @Override public EconomyResponse bankDeposit(String n, double a) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, ""); }
            @Override public EconomyResponse isBankOwner(String n, String p) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, ""); }
            @Override public EconomyResponse isBankMember(String n, String p) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, ""); }
            @Override public List<String> getBanks() { return List.of(); }
        };
        Bukkit.getServicesManager().register(Economy.class, provider, plugin, ServicePriority.Highest);
    }

    private void registerPapiExpansion() {
        PlaceholderExpansion exp = new PlaceholderExpansion() {
            @Override public String getIdentifier() { return "reallycore"; }
            @Override public String getAuthor() { return "ReallyWorldUltimate"; }
            @Override public String getVersion() { return "1.0.0"; }
            @Override public boolean persist() { return true; }

            @Override
            public String onRequest(OfflinePlayer offline, String params) {
                if (params == null) return "";
                if ("event".equalsIgnoreCase(params)) return eventsModule.getStatusSummary();
                if (offline == null) return "";
                Optional<PlayerProfile> pOpt = playerModule.findProfile(offline.getUniqueId());
                if (pOpt.isEmpty()) return "0";
                PlayerProfile p = pOpt.get();
                return switch (params.toLowerCase(Locale.ROOT)) {
                    case "coins" -> TextUtil.formatAmount(p.coins());
                    case "coins_raw" -> String.valueOf(p.coins());
                    case "rilliks" -> TextUtil.formatAmount(p.rilliks());
                    case "tokens" -> TextUtil.formatAmount(p.tokens());
                    case "rank" -> configManager.getRank(p.rankId()).displayName();
                    case "prefix" -> (p.customPrefix() != null && !p.customPrefix().isBlank())
                            ? p.customPrefix() : configManager.getRank(p.rankId()).prefix();
                    case "clan" -> clanModule.getPlayerClan(p.uuid()).map(ClanData::name).orElse("Нет");
                    case "clan_tag" -> clanModule.getPlayerClan(p.uuid()).map(ClanData::tag).orElse("");
                    case "kills" -> String.valueOf(p.kills());
                    case "deaths" -> String.valueOf(p.deaths());
                    case "kd" -> String.valueOf(p.kdRatio());
                    default -> "";
                };
            }
        };
        exp.register();
    }
}
