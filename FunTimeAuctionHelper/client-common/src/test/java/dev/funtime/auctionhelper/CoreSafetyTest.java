package dev.funtime.auctionhelper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.funtime.auctionhelper.analysis.FilterEngine;
import dev.funtime.auctionhelper.analysis.FlipAnalysis;
import dev.funtime.auctionhelper.analysis.PriceAnalyzer;
import dev.funtime.auctionhelper.analysis.PriceHistoryStore;
import dev.funtime.auctionhelper.analysis.PriceStats;
import dev.funtime.auctionhelper.analysis.TradeHistoryStore;
import dev.funtime.auctionhelper.analysis.StrategyEngine;
import dev.funtime.auctionhelper.analysis.StrategySignal;
import dev.funtime.auctionhelper.config.ConfigManager;
import dev.funtime.auctionhelper.config.HelperConfig;
import dev.funtime.auctionhelper.engine.AuctionIntegration;
import dev.funtime.auctionhelper.engine.AutomationState;
import dev.funtime.auctionhelper.engine.TradeEngine;
import dev.funtime.auctionhelper.engine.TradeResult;
import dev.funtime.auctionhelper.model.AuctionListing;
import dev.funtime.auctionhelper.model.FilterRule;
import dev.funtime.auctionhelper.model.PriceObservation;
import dev.funtime.auctionhelper.model.TradeRecord;
import dev.funtime.auctionhelper.notify.NotificationService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Safety and analysis tests for the version independent core. */
class CoreSafetyTest {
    private static final long NOW = 1_750_000_000_000L;


    /** A verified balance provider: automated buying refuses to run without one. */
    private static void withKnownBalance(TradeEngine engine, final double balance) {
        engine.setBalanceProvider(new TradeEngine.BalanceProvider() {
            @Override
            public boolean isKnown() {
                return true;
            }

            @Override
            public double getBalance() {
                return balance;
            }
        });
    }

    private AuctionListing listing(String id, int quantity, double total) {
        return new AuctionListing(id, id.replace("minecraft:", ""), quantity, total, NOW, "selftest",
                Arrays.asList("Цена: " + total), 0);
    }

    // ------------------------------------------------------------------
    // Automation switches
    // ------------------------------------------------------------------

    @Test
    void automationStartsOffAndCannotBeEnabledWithoutAuthorizedApi() {
        AutomationState state = new AutomationState();
        assertFalse(state.isMasterEnabled(), "master must start OFF");
        assertFalse(state.setMaster(true, "test"), "enabling must be refused without an API");
        assertFalse(state.isMasterEnabled());
        assertFalse(state.setMode(AutomationState.Mode.AUTHORIZED_AUTOMATION, "test"),
                "authorized automation must be refused without an API");
        assertTrue(state.setMode(AutomationState.Mode.MANUAL_ASSISTANT, "test"));
        assertFalse(state.setAutoBuy(true, "test"), "auto buy needs the master switch");
        assertFalse(state.isAutoBuySwitchOn());
    }

    @Test
    void autoSwitchesRequireMasterAndMode() {
        FakeIntegration integration = new FakeIntegration(true);
        AutomationState state = new AutomationState(integration);
        state.setMode(AutomationState.Mode.MANUAL_ASSISTANT, "test");
        assertFalse(state.setMaster(true, "test"), "manual assistant mode must not allow the master switch");
        state.setMode(AutomationState.Mode.AUTHORIZED_AUTOMATION, "test");
        assertTrue(state.setMaster(true, "test"));
        assertTrue(state.setAutoBuy(true, "test"));
        assertTrue(state.isAutoBuyEnabled());
        state.setMaster(false, "test");
        assertFalse(state.isAutoBuyEnabled());
        assertFalse(state.isAutoBuySwitchOn(), "turning the master off also drops the sub switches");
    }

    @Test
    void configSafetyResetAlwaysDisablesAutomation() {
        HelperConfig config = HelperConfig.createDefault();
        config.automation.masterEnabled = true;
        config.automation.autoBuyEnabled = true;
        config.automation.autoSellEnabled = true;
        assertTrue(config.applySafetyReset());
        assertFalse(config.automation.masterEnabled);
        assertFalse(config.automation.autoBuyEnabled);
        assertFalse(config.automation.autoSellEnabled);
    }

    @Test
    void emergencyStopClearsQueueAndIsRecorded() {
        HelperConfig config = HelperConfig.createDefault();
        config.automation.masterEnabled = true;
        FakeIntegration integration = new FakeIntegration(true);
        AutomationState automation = new AutomationState(integration);
        automation.setMode(AutomationState.Mode.AUTHORIZED_AUTOMATION, "test");
        automation.setMaster(true, "test");
        automation.setAutoBuy(true, "test");
        NotificationService notifications = new NotificationService(config);
        TradeHistoryStore history = new TradeHistoryStore(null);
        TradeEngine engine = new TradeEngine(config, automation, history, notifications, NOW);
        withKnownBalance(engine, 100_000d);
        TradeResult queued = engine.requestAutomatedBuy(listing("minecraft:diamond", 64, 1000d), NOW);
        assertEquals(TradeRecord.Result.PENDING, queued.getResult());
        assertEquals(1, engine.getPendingCount());
        int cancelled = automation.emergencyStop("test", NOW);
        assertEquals(1, cancelled);
        assertEquals(0, engine.getPendingCount());
        assertFalse(automation.isMasterEnabled());
        assertEquals(1, automation.getEmergencyStopCount());
        assertEquals(TradeRecord.Result.CANCELLED,
                history.find(queued.getRequestId()).getResult());
    }

    @Test
    void analysisOnlyModeNeverQueuesAnything() {
        HelperConfig config = HelperConfig.createDefault();
        AutomationState automation = new AutomationState(new FakeIntegration(true));
        NotificationService notifications = new NotificationService(config);
        TradeEngine engine = new TradeEngine(config, automation, new TradeHistoryStore(null), notifications, NOW);
        TradeResult result = engine.requestManualBuy(listing("minecraft:diamond", 1, 10d), NOW);
        assertEquals(TradeRecord.Result.REJECTED, result.getResult());
        assertTrue(result.getMessage().contains("ANALYSIS ONLY"));
        assertEquals(0, engine.getPendingCount());
    }

    @Test
    void incompleteOfferIsNeverTraded() {
        HelperConfig config = HelperConfig.createDefault();
        AutomationState automation = new AutomationState(new FakeIntegration(true));
        automation.setMode(AutomationState.Mode.MANUAL_ASSISTANT, "test");
        NotificationService notifications = new NotificationService(config);
        TradeEngine engine = new TradeEngine(config, automation, new TradeHistoryStore(null), notifications, NOW);
        AuctionListing incomplete = new AuctionListing("minecraft:air", "", 0, Double.NaN, NOW, "x",
                new ArrayList<String>(), 0);
        TradeResult result = engine.requestManualBuy(incomplete, NOW);
        assertEquals(TradeRecord.Result.REJECTED, result.getResult());
        assertTrue(result.getMessage().contains("incomplete"));
    }

    @Test
    void priceChangeAbortsTheOperation() {
        HelperConfig config = HelperConfig.createDefault();
        config.automation.masterEnabled = true;
        FakeIntegration integration = new FakeIntegration(true);
        integration.currentPrice = 2000d;
        AutomationState automation = new AutomationState(integration);
        automation.setMode(AutomationState.Mode.AUTHORIZED_AUTOMATION, "test");
        automation.setMaster(true, "test");
        TradeEngine engine = new TradeEngine(config, automation, new TradeHistoryStore(null),
                new NotificationService(config), NOW);
        withKnownBalance(engine, 100_000d);
        TradeResult queued = engine.requestAutomatedBuy(listing("minecraft:diamond", 1, 1000d), NOW);
        TradeResult executed = engine.execute(queued.getRequestId(), false, NOW);
        assertEquals(TradeRecord.Result.STALE, executed.getResult());
        assertEquals(0, integration.buys, "a stale price must never reach the integration");
    }

    @Test
    void networkErrorLeavesOperationUnverifiedAndBlocksBlindRetry() {
        HelperConfig config = HelperConfig.createDefault();
        config.automation.masterEnabled = true;
        FakeIntegration integration = new FakeIntegration(true);
        integration.throwOnBuy = true;
        AutomationState automation = new AutomationState(integration);
        automation.setMode(AutomationState.Mode.AUTHORIZED_AUTOMATION, "test");
        automation.setMaster(true, "test");
        TradeHistoryStore history = new TradeHistoryStore(null);
        TradeEngine engine = new TradeEngine(config, automation, history, new NotificationService(config), NOW);
        withKnownBalance(engine, 100_000d);
        TradeResult queued = engine.requestAutomatedBuy(listing("minecraft:diamond", 1, 100d), NOW);
        TradeResult executed = engine.execute(queued.getRequestId(), false, NOW);
        assertEquals(TradeRecord.Result.UNVERIFIED, executed.getResult());
        assertEquals(TradeRecord.Result.UNVERIFIED, history.find(queued.getRequestId()).getResult());
        TradeResult retry = engine.requestAutomatedBuy(listing("minecraft:diamond", 1, 100d), NOW);
        assertEquals(TradeRecord.Result.UNVERIFIED, retry.getResult());
        assertTrue(retry.getMessage().toLowerCase().contains("unverified"));
    }

    @Test
    void automatedBuyNeedsAKnownBalance() {
        HelperConfig config = HelperConfig.createDefault();
        config.automation.masterEnabled = true;
        AutomationState automation = new AutomationState(new FakeIntegration(true));
        automation.setMode(AutomationState.Mode.AUTHORIZED_AUTOMATION, "test");
        automation.setMaster(true, "test");
        automation.setAutoBuy(true, "test");
        TradeEngine engine = new TradeEngine(config, automation, new TradeHistoryStore(null),
                new NotificationService(config), NOW);
        // No balance provider at all: the server does not expose the balance.
        TradeResult result = engine.requestAutomatedBuy(listing("minecraft:diamond", 1, 100d), NOW);
        assertEquals(TradeRecord.Result.REJECTED, result.getResult());
        assertTrue(result.getMessage().contains("Balance is unknown"), result.getMessage());
        // A manual confirmation is still allowed: the player sees the price and decides.
        TradeResult manual = engine.requestManualBuy(listing("minecraft:diamond", 1, 100d), NOW);
        assertEquals(TradeRecord.Result.PENDING, manual.getResult(), manual.getMessage());
    }

    @Test
    void dailySpendingLimitIsEnforced() {
        HelperConfig config = HelperConfig.createDefault();
        config.buy.dailySpendingLimit = 150d;
        config.automation.masterEnabled = true;
        FakeIntegration integration = new FakeIntegration(true);
        AutomationState automation = new AutomationState(integration);
        automation.setMode(AutomationState.Mode.AUTHORIZED_AUTOMATION, "test");
        automation.setMaster(true, "test");
        automation.setAutoBuy(true, "test");
        TradeHistoryStore history = new TradeHistoryStore(null);
        TradeEngine engine = new TradeEngine(config, automation, history, new NotificationService(config), NOW);
        withKnownBalance(engine, 100_000d);
        TradeResult first = engine.requestAutomatedBuy(listing("minecraft:gold_ingot", 1, 100d), NOW);
        assertEquals(TradeRecord.Result.PENDING, first.getResult());
        assertNotNull(engine.execute(first.getRequestId(), false, NOW));
        TradeResult second = engine.requestAutomatedBuy(listing("minecraft:gold_ingot", 1, 100d), NOW);
        assertEquals(TradeRecord.Result.REJECTED, second.getResult());
        assertTrue(second.getMessage().contains("Daily spending limit"));
    }

    @Test
    void confirmedTradesOnlyCountOnceAndOnlyWhenConfirmed() {
        TradeHistoryStore history = new TradeHistoryStore(null);
        history.add(new TradeRecord("a", "minecraft:diamond", "Diamond", 1, 500d, TradeRecord.Type.BUY,
                NOW, TradeRecord.Result.CONFIRMED, "MANUAL_ASSISTANT", "ok"));
        history.add(new TradeRecord("b", "minecraft:diamond", "Diamond", 1, 900d, TradeRecord.Type.SELL,
                NOW, TradeRecord.Result.PENDING, "MANUAL_ASSISTANT", "waiting"));
        history.add(new TradeRecord("c", "minecraft:emerald", "Emerald", 1, 50d, TradeRecord.Type.SELL,
                NOW, TradeRecord.Result.UNVERIFIED, "MANUAL_ASSISTANT", "network"));
        TradeHistoryStore.Stats stats = history.stats();
        assertEquals(1, stats.confirmed);
        assertEquals(500d, stats.spent, 0.001d);
        assertEquals(0d, stats.earned, 0.001d);
        assertEquals(-500d, stats.net, 0.001d);
        assertEquals(1, stats.pending);
        assertEquals(1, stats.unverified);
    }

    // ------------------------------------------------------------------
    // Price analysis
    // ------------------------------------------------------------------

    @Test
    void priceStatisticsUseMedianNotAverageOfOutliers() {
        List<PriceObservation> observations = new ArrayList<PriceObservation>();
        double[] prices = {10d, 12d, 11d, 13d, 1000d};
        for (int i = 0; i < prices.length; i++) {
            observations.add(new PriceObservation("minecraft:diamond", "Diamond", prices[i], prices[i] * 64,
                    64, NOW + i, "selftest"));
        }
        PriceStats stats = new PriceAnalyzer().compute(observations);
        assertEquals(5, stats.getSampleCount());
        assertEquals(12d, stats.getMedian(), 0.001d);
        assertEquals(10d, stats.getMinimum(), 0.001d);
        assertEquals(1000d, stats.getMaximum(), 0.001d);
        assertTrue(stats.getAverage() > stats.getMedian());
        assertFalse(stats.isReliable(10));
    }

    @Test
    void flipAnalysisSubtractsFeesAndWarnsAboutWeakData() {
        HelperConfig.SellFees fees = new HelperConfig.SellFees();
        fees.percent = 0.05d;
        PriceStats stats = new PriceStats(2, 100d, 100d, 95d, 105d, 97d, 103d, 64d);
        FlipAnalysis analysis = new PriceAnalyzer().analyze(listing("minecraft:diamond", 1, 60d), stats,
                100d, fees, 10);
        assertEquals(5d, analysis.getEstimatedFees(), 0.001d);
        assertEquals(35d, analysis.getNetProfitPerItem(), 0.001d);
        assertTrue(analysis.isLowConfidence());
        assertTrue(analysis.isProfitable());
    }

    @Test
    void insufficientDataIsReportedInsteadOfGuessed() {
        FlipAnalysis analysis = new PriceAnalyzer().analyze(listing("minecraft:diamond", 1, 60d),
                PriceStats.EMPTY, Double.NaN, new HelperConfig.SellFees(), 5);
        assertTrue(analysis.isInsufficientData());
        assertFalse(analysis.isProfitable());
    }

    @Test
    void priceAmountParserHandlesServerFormats() {
        assertEquals(1234d, dev.funtime.auctionhelper.util.Numbers.parseAmount("1 234"), 0.001d);
        assertEquals(1234500d, dev.funtime.auctionhelper.util.Numbers.parseAmount("1.234.500"), 0.001d);
        assertEquals(12.5d, dev.funtime.auctionhelper.util.Numbers.parseAmount("12,5"), 0.001d);
        assertEquals(15000d, dev.funtime.auctionhelper.util.Numbers.parseAmount("15k"), 0.001d);
        assertEquals(2000000d, dev.funtime.auctionhelper.util.Numbers.parseAmount("2кк"), 0.001d);
        assertTrue(Double.isNaN(dev.funtime.auctionhelper.util.Numbers.parseAmount("no price here")));
    }

    // ------------------------------------------------------------------
    // Filters, strategies, history
    // ------------------------------------------------------------------

    @Test
    void filterEngineRespectsBoundsAndLists() {
        FilterEngine engine = new FilterEngine();
        FilterRule rule = new FilterRule("f", "f");
        rule.maxPrice = 1000d;
        rule.minStackSize = 16;
        assertTrue(engine.matches(rule, listing("minecraft:diamond", 64, 900d), new ArrayList<String>()));
        assertFalse(engine.matches(rule, listing("minecraft:diamond", 8, 900d), new ArrayList<String>()));
        assertFalse(engine.matches(rule, listing("minecraft:diamond", 64, 2000d), new ArrayList<String>()));
        rule.enabled = false;
        assertFalse(engine.matches(rule, listing("minecraft:diamond", 64, 900d), new ArrayList<String>()));
        assertFalse(engine.isGloballyAllowed(listing("minecraft:diamond", 64, 900d),
                new ArrayList<String>(), Arrays.asList("minecraft:diamond")));
        assertTrue(engine.isGloballyAllowed(listing("minecraft:emerald", 1, 10d),
                new ArrayList<String>(), Arrays.asList("minecraft:diamond")));
    }

    @Test
    void strategiesOnlyProduceSignals() {
        HelperConfig config = HelperConfig.createDefault();
        for (int i = 0; i < config.strategies.size(); i++) {
            config.strategies.get(i).enabled = true;
        }
        PriceHistoryStore history = new PriceHistoryStore(null);
        for (int i = 0; i < 10; i++) {
            history.add(new PriceObservation("minecraft:diamond", "Diamond", 100d + i, 6400d, 64,
                    NOW - i * 1000L, "selftest"));
        }
        List<StrategySignal> signals = new StrategyEngine().evaluate(
                Arrays.asList(listing("minecraft:diamond", 64, 3200d)), config, history, NOW);
        assertFalse(signals.isEmpty());
        for (int i = 0; i < signals.size(); i++) {
            assertNotNull(signals.get(i).getTitle());
        }
        // Strategies are read only: nothing is queued by evaluating them.
    }

    @Test
    void priceHistoryCsvRoundTripAndCleanup(@TempDir Path dir) throws Exception {
        PriceHistoryStore store = new PriceHistoryStore(null);
        for (int i = 0; i < 5; i++) {
            store.add(new PriceObservation("minecraft:diamond", "Diamond", 100d + i, 6400d, 64,
                    NOW, "selftest"));
        }
        Path csv = dir.resolve("prices.csv");
        assertTrue(store.exportCsv(csv));
        assertTrue(Files.size(csv) > 0);
        PriceHistoryStore imported = new PriceHistoryStore(null);
        assertEquals(5, imported.importCsv(csv));
        assertEquals(5, imported.size());
        assertEquals(0, imported.cleanup(1, NOW));
        assertEquals(5, imported.cleanup(1, NOW + 3L * 86_400_000L));
    }

    @Test
    void configManagerRoundTripForcesAutomationOff(@TempDir Path dir) throws Exception {
        ConfigManager manager = new ConfigManager(dir);
        HelperConfig config = manager.getConfig();
        config.buy.maxPurchasePrice = 4242d;
        config.automation.masterEnabled = true;
        manager.saveAs("arena");
        assertTrue(Files.isRegularFile(dir.resolve("profiles/arena.json")));
        ConfigManager second = new ConfigManager(dir);
        second.load();
        second.loadProfile("arena");
        assertEquals(4242d, second.getConfig().buy.maxPurchasePrice, 0.001d);
        assertFalse(second.getConfig().automation.masterEnabled,
                "a saved ON switch must never survive loading");
        assertTrue(second.listProfiles().contains("arena"));
        assertTrue(second.renameProfile("arena", "renamed"));
        assertTrue(second.deleteProfile("renamed"));
        assertFalse(second.deleteProfile("missing"));
    }

    @Test
    void adapterParsesConfigurablePatternsAndFlagsUnverifiedProfiles() {
        HelperConfig config = HelperConfig.createDefault();
        HelperConfig.AdapterProfileConfig profile = config.adapterProfiles.get(0);
        dev.funtime.auctionhelper.adapter.RegexAuctionAdapter adapter =
                new dev.funtime.auctionhelper.adapter.RegexAuctionAdapter(profile);
        List<dev.funtime.auctionhelper.model.SlotSnapshot> slots = new ArrayList<dev.funtime.auctionhelper.model.SlotSnapshot>();
        slots.add(new dev.funtime.auctionhelper.model.SlotSnapshot(11, "minecraft:diamond", "Алмаз", 64,
                Arrays.asList("Цена: 1 250", "Кол-во: 64")));
        slots.add(new dev.funtime.auctionhelper.model.SlotSnapshot(12, "minecraft:barrier", "Закрыть", 1,
                Arrays.asList("Закрыть меню")));
        dev.funtime.auctionhelper.model.ScreenSnapshot snapshot =
                new dev.funtime.auctionhelper.model.ScreenSnapshot("Аукцион", "test", slots, NOW, "FunTime", true);
        dev.funtime.auctionhelper.adapter.ParseResult result = adapter.parse(snapshot);
        assertEquals(1, result.getListings().size());
        assertEquals(1250d, result.getListings().get(0).getTotalPrice(), 0.001d);
        assertEquals(64, result.getListings().get(0).getQuantity());
        assertEquals(19.53125d, result.getListings().get(0).getUnitPrice(), 0.0001d);
        assertFalse(result.isProfileVerified());
        assertTrue(result.getWarnings().toString().contains("unverified"));

        profile.pricePattern = "((unclosed";
        dev.funtime.auctionhelper.adapter.ParseResult broken = adapter.parse(snapshot);
        assertTrue(broken.isEmpty());
        assertTrue(broken.getWarnings().get(0).contains("invalid"));
    }

    @Test
    void unknownScreenNeverThrowsAndIsCounted() {
        HelperConfig config = HelperConfig.createDefault();
        dev.funtime.auctionhelper.adapter.AdapterRegistry registry = new dev.funtime.auctionhelper.adapter.AdapterRegistry(
                config, null);
        dev.funtime.auctionhelper.model.ScreenSnapshot snapshot = new dev.funtime.auctionhelper.model.ScreenSnapshot(
                "Chest", "net.minecraft.ChestScreen",
                Arrays.asList(new dev.funtime.auctionhelper.model.SlotSnapshot(0, "minecraft:stone", "Stone", 1,
                        new ArrayList<String>())), NOW, "unknown", true);
        dev.funtime.auctionhelper.adapter.ParseResult result = registry.parse(snapshot);
        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertEquals(1, registry.getUnknownScreenCount());
        assertFalse(registry.isAnalysisAvailable());
        assertNull(registry.getLastResult().getListings().isEmpty() ? null : "unexpected");
    }

    private static final class FakeIntegration implements AuctionIntegration {
        private final boolean available;
        double currentPrice = Double.NaN;
        boolean throwOnBuy;
        int buys;
        int sells;

        FakeIntegration(boolean available) {
            this.available = available;
        }

        @Override
        public String getName() {
            return "FakeIntegration";
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public String getUnavailableReason() {
            return available ? "" : "not available";
        }

        @Override
        public TradeResult buy(dev.funtime.auctionhelper.engine.TradeRequest request) {
            if (throwOnBuy) {
                throw new IllegalStateException("connection reset");
            }
            buys++;
            return TradeResult.confirmed(request, request.getPrice(), "ok");
        }

        @Override
        public TradeResult sell(dev.funtime.auctionhelper.engine.TradeRequest request) {
            sells++;
            return TradeResult.confirmed(request, request.getPrice(), "ok");
        }

        @Override
        public double currentPriceOf(AuctionListing listing) {
            return Double.isNaN(currentPrice) ? listing.getTotalPrice() : currentPrice;
        }
    }
}
