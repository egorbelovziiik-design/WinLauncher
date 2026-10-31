package dev.funtime.auctionhelper;

import dev.funtime.auctionhelper.adapter.AdapterDiagnostics;
import dev.funtime.auctionhelper.adapter.AdapterRegistry;
import dev.funtime.auctionhelper.adapter.ParseResult;
import dev.funtime.auctionhelper.analysis.FilterEngine;
import dev.funtime.auctionhelper.analysis.PriceAnalyzer;
import dev.funtime.auctionhelper.analysis.PriceHistoryStore;
import dev.funtime.auctionhelper.analysis.StrategyEngine;
import dev.funtime.auctionhelper.analysis.TradeHistoryStore;
import dev.funtime.auctionhelper.config.ConfigManager;
import dev.funtime.auctionhelper.config.HelperConfig;
import dev.funtime.auctionhelper.config.ThemeEngine;
import dev.funtime.auctionhelper.engine.AuctionIntegration;
import dev.funtime.auctionhelper.engine.AutomationState;
import dev.funtime.auctionhelper.engine.TradeEngine;
import dev.funtime.auctionhelper.model.AuctionListing;
import dev.funtime.auctionhelper.model.PriceObservation;
import dev.funtime.auctionhelper.model.ScreenSnapshot;
import dev.funtime.auctionhelper.notify.NotificationService;
import dev.funtime.auctionhelper.platform.MinecraftAdapter;
import dev.funtime.auctionhelper.ui.HelperUi;
import dev.funtime.auctionhelper.util.KeyCodes;
import dev.funtime.auctionhelper.util.Log;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Central object of the mod. One instance per game session, created by the version specific
 * {@code ClientModInitializer}.
 */
public final class HelperCore {
    public static final String MOD_ID = "funtimeauctionhelper";
    public static final String MOD_NAME = "FunTime Auction Helper Ultra Pro";
    public static final String VERSION = "1.0.0";

    private static HelperCore instance;

    private final MinecraftAdapter platform;
    private final Path rootDir;
    private final ConfigManager configManager;
    private HelperConfig config;
    private final ThemeEngine themeEngine = new ThemeEngine();
    private final PriceHistoryStore priceHistory;
    private final TradeHistoryStore tradeHistory;
    private final NotificationService notifications;
    private final AdapterRegistry adapters;
    private final AutomationState automation;
    private final TradeEngine tradeEngine;
    private final PriceAnalyzer priceAnalyzer = new PriceAnalyzer();
    private final StrategyEngine strategies = new StrategyEngine();
    private final FilterEngine filters = new FilterEngine();
    private HelperUi ui;
    private List<AuctionListing> lastListings = new ArrayList<AuctionListing>();
    private long lastScreenCaptureAt;
    private String lastScreenSignature = "";
    private boolean initialised;
    private String lastError = "";

    public HelperCore(MinecraftAdapter platform, AuctionIntegration integration) {
        this.platform = platform;
        this.rootDir = platform.getGameDir().resolve("config").resolve("funtime-auction-helper");
        this.configManager = new ConfigManager(rootDir);
        this.config = configManager.load();
        this.themeEngine.loadCustomThemes(config);
        this.priceHistory = new PriceHistoryStore(rootDir.resolve("price-history.json"));
        this.priceHistory.setCap(config.analysis.historyCap);
        this.priceHistory.load();
        this.tradeHistory = new TradeHistoryStore(rootDir.resolve("trade-history.json"));
        this.tradeHistory.load();
        this.notifications = new NotificationService(config);
        this.notifications.setSink(new NotificationService.Sink() {
            @Override
            public void onNotification(dev.funtime.auctionhelper.model.NotificationEntry entry, boolean mirror) {
                if (mirror && platform.isInWorld()) {
                    platform.sendChatMessage(entry.getMessage());
                }
            }
        });
        this.automation = new AutomationState(integration);
        this.automation.setMode(AutomationState.Mode.parse(config.automation.mode),
                "restored mode from config");
        this.tradeEngine = new TradeEngine(config, automation, tradeHistory, notifications,
                System.currentTimeMillis());
        this.tradeEngine.setBalanceProvider(new TradeEngine.BalanceProvider() {
            @Override
            public boolean isKnown() {
                return platform.isBalanceKnown();
            }

            @Override
            public double getBalance() {
                return platform.getBalance();
            }
        });
        this.adapters = new AdapterRegistry(config, new AdapterDiagnostics(rootDir.resolve("logs")));
        this.automation.addListener(new AutomationState.Listener() {
            @Override
            public void onAutomationChanged(AutomationState state, String reason) {
                config.automation.masterEnabled = state.isMasterEnabled();
                config.automation.autoBuyEnabled = state.isAutoBuySwitchOn();
                config.automation.autoSellEnabled = state.isAutoSellSwitchOn();
                Log.info("Automation state changed: " + state.describe() + " (" + reason + ")");
            }
        });
        instance = this;
        initialised = true;
        Log.info(MOD_NAME + " " + VERSION + " initialised on Minecraft " + platform.getMinecraftVersion());
    }

    public static HelperCore getInstance() {
        return instance;
    }

    public static void clearInstance() {
        instance = null;
    }

    // ------------------------------------------------------------------
    // Accessors used by the GUI
    // ------------------------------------------------------------------

    public MinecraftAdapter getPlatform() {
        return platform;
    }

    public HelperConfig getConfig() {
        return config;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ThemeEngine getThemeEngine() {
        return themeEngine;
    }

    public PriceHistoryStore getPriceHistory() {
        return priceHistory;
    }

    public TradeHistoryStore getTradeHistory() {
        return tradeHistory;
    }

    public NotificationService getNotifications() {
        return notifications;
    }

    public AdapterRegistry getAdapters() {
        return adapters;
    }

    public AutomationState getAutomation() {
        return automation;
    }

    public TradeEngine getTradeEngine() {
        return tradeEngine;
    }

    public PriceAnalyzer getPriceAnalyzer() {
        return priceAnalyzer;
    }

    public StrategyEngine getStrategyEngine() {
        return strategies;
    }

    public FilterEngine getFilterEngine() {
        return filters;
    }

    public Path getRootDir() {
        return rootDir;
    }

    public String getLastError() {
        return lastError;
    }

    public boolean isInitialised() {
        return initialised;
    }

    public List<AuctionListing> getLastListings() {
        return new ArrayList<AuctionListing>(lastListings);
    }

    public HelperUi getUi() {
        if (ui == null) {
            ui = new HelperUi(this);
        }
        return ui;
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /** Called on every client tick by the version module. */
    public void onClientTick(long now) {
        try {
            notifications.tick(now);
            observeScreen(now);
            if (ui != null) {
                ui.tick(now);
            }
        } catch (RuntimeException error) {
            handleError("tick", error);
        }
    }

    private void observeScreen(long now) {
        ScreenSnapshot snapshot = platform.captureScreen();
        if (snapshot == null) {
            return;
        }
        String signature = snapshot.getTitle() + "|" + snapshot.getScreenClass() + "|"
                + snapshot.getNonEmptySlotCount() + "|" + snapshot.getCapturedAt() / 500L;
        if (signature.equals(lastScreenSignature) || now - lastScreenCaptureAt < 500L) {
            return;
        }
        lastScreenCaptureAt = now;
        lastScreenSignature = signature;
        ParseResult result = adapters.parse(snapshot);
        if (result != null && !result.isEmpty()) {
            lastListings = result.getListings();
            boolean verified = result.isProfileVerified();
            if (verified || config.analysis.recordUnverifiedProfiles) {
                List<PriceObservation> observations = new ArrayList<PriceObservation>();
                for (int i = 0; i < lastListings.size(); i++) {
                    observations.add(PriceObservation.of(lastListings.get(i)));
                }
                priceHistory.addAll(observations);
                priceHistory.save();
            }
            for (int i = 0; i < result.getWarnings().size(); i++) {
                Log.warn(result.getWarnings().get(i));
            }
        } else if (result != null) {
            for (int i = 0; i < result.getWarnings().size(); i++) {
                Log.info(result.getWarnings().get(i));
            }
        }
    }

    /** Called when the player presses a key that the mod owns (already filtered by the platform). */
    public boolean onInGameKeyPress(int glfwKey, boolean shiftHeld) {
        if (glfwKey == config.ui.emergencyKeyCode) {
            emergencyStop("hotkey pressed");
            return true;
        }
        if (glfwKey == config.ui.openKeyCode) {
            platform.openHelperScreen();
            return true;
        }
        return false;
    }

    /** Called when a world (singleplayer or server) finished joining. */
    public void onWorldJoined(String serverName, long now) {
        boolean wasOn = config.applySafetyReset();
        automation.reset("joined " + serverName);
        tradeEngine.startNewSession(now);
        lastScreenSignature = "";
        lastListings = new ArrayList<AuctionListing>();
        notifications.info("FunTime Auction Helper ready - automation is OFF", now);
        if (wasOn) {
            notifications.warn("Automation was disabled on join and must be re-enabled by you", now);
        }
    }

    /** Called on disconnect: nothing may keep running after the connection is gone. */
    public void onDisconnected(long now) {
        config.applySafetyReset();
        automation.reset("connection lost");
        tradeEngine.cancelAll("connection lost");
        notifications.warn("Disconnected: all automation stopped, pending operations cancelled", now);
        saveAll();
    }

    public String getKeyName(int code) {
        return KeyCodes.name(code);
    }

    /** Emergency stop: everything off, queue cleared, notification + journal entry. */
    public int emergencyStop(String reason) {
        long now = System.currentTimeMillis();
        int cancelled = automation.emergencyStop(reason, now);
        config.applySafetyReset();
        notifications.error("STOP ALL TRADING: automation disabled, "
                + cancelled + " pending operation(s) cancelled", now);
        Log.warn("EMERGENCY STOP (" + reason + "), cancelled=" + cancelled);
        saveAll();
        return cancelled;
    }

    public void handleError(String where, Throwable error) {
        lastError = where + ": " + error.getClass().getSimpleName()
                + (error.getMessage() == null ? "" : " - " + error.getMessage());
        Log.error("Error in " + where, error);
        config.applySafetyReset();
        automation.reset("error in " + where);
        notifications.error("Error (" + where + "): automation disabled for safety", System.currentTimeMillis());
    }

    public void saveAll() {
        configManager.save();
        priceHistory.save();
        tradeHistory.save();
    }

    public void onShutdown() {
        try {
            config.applySafetyReset();
            saveAll();
            adapters.getDiagnostics().flush();
        } catch (RuntimeException error) {
            Log.error("Shutdown failed", error);
        } finally {
            clearInstance();
        }
    }
}
