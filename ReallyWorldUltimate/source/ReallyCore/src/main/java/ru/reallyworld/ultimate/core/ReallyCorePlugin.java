package ru.reallyworld.ultimate.core;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import ru.reallyworld.ultimate.core.api.ReallyModule;
import ru.reallyworld.ultimate.core.command.*;
import ru.reallyworld.ultimate.core.config.ConfigManager;
import ru.reallyworld.ultimate.core.gui.MenuModule;
import ru.reallyworld.ultimate.core.module.*;
import ru.reallyworld.ultimate.core.storage.StorageModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Main Entry Point of ReallyCore — Modular Core Plugin for ReallyWorld Ultimate (1.21.4–1.21.11).
 */
public final class ReallyCorePlugin extends JavaPlugin {
    private ConfigManager configManager;
    private StorageModule storageModule;
    private LocalizationModule localizationModule;
    private CooldownModule cooldownModule;
    private SecurityModule securityModule;
    private PlayerModule playerModule;
    private CustomItemsModule customItemsModule;
    private EconomyModule economyModule;
    private ClanModule clanModule;
    private KitsModule kitsModule;
    private CasesModule casesModule;
    private QuestsModule questsModule;
    private BattlePassModule battlePassModule;
    private RewardsModule rewardsModule;
    private AuctionModule auctionModule;
    private StatisticsModule statisticsModule;
    private EventsModule eventsModule;
    private SpawnWorldModule spawnWorldModule;
    private CombatModule combatModule;
    private ScoreboardTabModule scoreboardTabModule;
    private IntegrationsModule integrationsModule;
    private MenuModule menuModule;

    private final List<ReallyModule> orderedModules = new ArrayList<>();
    private volatile boolean debugMode;

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();
        getLogger().info("=== Запуск ReallyCore Ultimate v" + getPluginMeta().getVersion() + " (1.21.4–1.21.11) ===");

        this.configManager = new ConfigManager(getDataFolder(), getLogger());
        this.configManager.loadAll();
        this.debugMode = configManager.main().getBoolean("server.debug", false);

        // Instantiate all 19 modular subsystems
        this.storageModule = new StorageModule(getDataFolder(), configManager, getLogger());
        this.localizationModule = new LocalizationModule(configManager);
        this.cooldownModule = new CooldownModule(configManager, storageModule);
        this.securityModule = new SecurityModule(this, configManager, storageModule, localizationModule);
        this.playerModule = new PlayerModule(this, configManager, storageModule, localizationModule, cooldownModule);
        this.customItemsModule = new CustomItemsModule(this, configManager, localizationModule, cooldownModule, securityModule);
        this.economyModule = new EconomyModule(this, configManager, storageModule, localizationModule, playerModule);
        this.clanModule = new ClanModule(this, configManager, storageModule, localizationModule, playerModule);
        this.kitsModule = new KitsModule(configManager, localizationModule, cooldownModule, customItemsModule);
        this.casesModule = new CasesModule(this, configManager, storageModule, localizationModule, playerModule, economyModule, customItemsModule);
        this.questsModule = new QuestsModule(configManager, storageModule, localizationModule);
        this.battlePassModule = new BattlePassModule(configManager, storageModule, localizationModule, playerModule, economyModule, casesModule, kitsModule);
        this.rewardsModule = new RewardsModule(configManager, storageModule, localizationModule, playerModule, economyModule, casesModule, kitsModule);
        this.auctionModule = new AuctionModule(this, configManager, storageModule, localizationModule, playerModule, customItemsModule, questsModule);
        this.statisticsModule = new StatisticsModule(this, configManager, storageModule, clanModule);
        this.eventsModule = new EventsModule(this, configManager, storageModule, localizationModule, playerModule, economyModule, clanModule, customItemsModule, casesModule, kitsModule, questsModule);
        this.spawnWorldModule = new SpawnWorldModule(this, configManager, localizationModule, playerModule, statisticsModule);
        this.combatModule = new CombatModule(this, configManager, storageModule, localizationModule, playerModule, economyModule, clanModule, questsModule, spawnWorldModule);
        this.scoreboardTabModule = new ScoreboardTabModule(this, configManager, playerModule, clanModule, combatModule, eventsModule);
        this.integrationsModule = new IntegrationsModule(this, configManager, playerModule, economyModule, clanModule, eventsModule, getLogger());
        this.menuModule = new MenuModule(this, configManager, localizationModule, cooldownModule, playerModule, economyModule, auctionModule, clanModule, casesModule, kitsModule, questsModule, battlePassModule, rewardsModule, statisticsModule, eventsModule);

        // Cross-wire peer dependencies cleanly
        this.playerModule.bindModules(clanModule, questsModule, combatModule);
        this.customItemsModule.bindModules(spawnWorldModule, clanModule);
        this.economyModule.bindModules(customItemsModule, casesModule, questsModule);
        this.casesModule.bindModules(kitsModule, questsModule);
        this.questsModule.bindModules(playerModule, economyModule, kitsModule, battlePassModule);
        this.eventsModule.bindSpawnModule(spawnWorldModule);
        this.spawnWorldModule.bindModules(eventsModule, menuModule);

        Collections.addAll(orderedModules,
                storageModule, localizationModule, cooldownModule, securityModule,
                playerModule, customItemsModule, economyModule, clanModule,
                kitsModule, casesModule, questsModule, battlePassModule,
                rewardsModule, auctionModule, statisticsModule, eventsModule,
                spawnWorldModule, combatModule, scoreboardTabModule, integrationsModule, menuModule
        );

        for (ReallyModule mod : orderedModules) {
            try {
                mod.enable();
                getLogger().info(" [+] Модуль активирован: " + mod.displayName() + " [" + mod.healthStatus() + "]");
            } catch (Exception e) {
                getLogger().severe(" [!] Ошибка запуска модуля " + mod.displayName() + ": " + e.getMessage());
                throw new RuntimeException("Failed to initialize module " + mod.id(), e);
            }
        }

        registerAllCommands();
        getLogger().info("=== ReallyCore успешно запущен за " + (System.currentTimeMillis() - start) + " мс! ===");
    }

    @Override
    public void onDisable() {
        getLogger().info("=== Корректная остановка ReallyCore Ultimate... ===");
        for (int i = orderedModules.size() - 1; i >= 0; i--) {
            ReallyModule mod = orderedModules.get(i);
            try {
                mod.disable();
            } catch (Exception e) {
                getLogger().warning("Ошибка остановки модуля " + mod.displayName() + ": " + e.getMessage());
            }
        }
        orderedModules.clear();
    }

    public synchronized long reloadAllSafely() {
        long t0 = System.currentTimeMillis();
        configManager.loadAll();
        this.debugMode = configManager.main().getBoolean("server.debug", false);
        for (ReallyModule mod : orderedModules) {
            try {
                mod.reload();
            } catch (Exception e) {
                getLogger().warning("Ошибка перезагрузки модуля " + mod.displayName() + ": " + e.getMessage());
            }
        }
        return System.currentTimeMillis() - t0;
    }

    private void registerAllCommands() {
        bindCmd(new ReallyCoreCommand(this), "reallycore");
        bindCmd(new TeleportCommands(this),
                "spawn", "setspawn", "rtp", "rtpfar", "sethome", "home", "delhome", "homes",
                "tpa", "tpahere", "tpaccept", "tpdeny", "tptoggle", "warp", "warps", "setwarp", "delwarp");
        bindCmd(new EconomyAuctionCommands(this),
                "bal", "pay", "baltop", "eco", "rilliks", "shop", "sell", "buyer", "ah");
        bindCmd(new ClanCommand(this), "clan");
        bindCmd(new GameplayMenuCommands(this),
                "menu", "donate", "kit", "kits", "cases", "profile", "stats", "daily",
                "rewards", "events", "quests", "bp", "top", "sb");
        bindCmd(new DonateUtilityCommands(this),
                "craft", "recipe", "enderchest", "feed", "heal", "repair", "ext", "hat", "near", "ptime",
                "nick", "prefix", "grant", "fly", "clear", "ignore", "getpos", "itemdb",
                "invsee", "realname", "broadcast", "lightning", "sun", "rain", "day", "night");
    }

    private <T extends CommandExecutor & TabCompleter> void bindCmd(T handler, String... names) {
        for (String name : names) {
            PluginCommand pc = getCommand(name);
            if (pc != null) {
                pc.setExecutor(handler);
                pc.setTabCompleter(handler);
            } else {
                getLogger().warning("Команда отсутствует в plugin.yml: /" + name);
            }
        }
    }

    public List<ReallyModule> modules() { return Collections.unmodifiableList(orderedModules); }
    public ConfigManager configs() { return configManager; }
    public StorageModule storage() { return storageModule; }
    public LocalizationModule locale() { return localizationModule; }
    public CooldownModule cooldowns() { return cooldownModule; }
    public SecurityModule security() { return securityModule; }
    public PlayerModule players() { return playerModule; }
    public CustomItemsModule customItems() { return customItemsModule; }
    public EconomyModule economy() { return economyModule; }
    public ClanModule clans() { return clanModule; }
    public KitsModule kits() { return kitsModule; }
    public CasesModule cases() { return casesModule; }
    public QuestsModule quests() { return questsModule; }
    public BattlePassModule battlePass() { return battlePassModule; }
    public RewardsModule rewards() { return rewardsModule; }
    public AuctionModule auction() { return auctionModule; }
    public StatisticsModule statistics() { return statisticsModule; }
    public EventsModule events() { return eventsModule; }
    public SpawnWorldModule spawnWorld() { return spawnWorldModule; }
    public CombatModule combat() { return combatModule; }
    public IntegrationsModule integrations() { return integrationsModule; }
    public MenuModule menus() { return menuModule; }
    public boolean isDebugMode() { return debugMode; }
    public void setDebugMode(boolean debugMode) { this.debugMode = debugMode; }
}
