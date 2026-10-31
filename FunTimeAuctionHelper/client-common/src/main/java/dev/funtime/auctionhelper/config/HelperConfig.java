package dev.funtime.auctionhelper.config;

import dev.funtime.auctionhelper.model.FilterRule;
import dev.funtime.auctionhelper.model.StrategyConfig;
import dev.funtime.auctionhelper.util.KeyCodes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The complete persisted configuration.
 *
 * <p>Safety rule: {@link Automation#masterEnabled}, {@link Automation#autoBuyEnabled} and
 * {@link Automation#autoSellEnabled} are written to disk for transparency, but
 * {@link #applySafetyReset()} is executed on <b>every</b> load, on every world join,
 * on every disconnect and after every error. Automation therefore never survives a restart
 * and is never re-enabled implicitly.</p>
 */
public final class HelperConfig {
    public static final int CONFIG_VERSION = 1;

    public int configVersion = CONFIG_VERSION;
    public String profileName = "default";
    public Automation automation = new Automation();
    public BuySettings buy = new BuySettings();
    public SellSettings sell = new SellSettings();
    public AnalysisSettings analysis = new AnalysisSettings();
    public SellFees fees = new SellFees();
    public NotificationSettings notifications = new NotificationSettings();
    public UiSettings ui = new UiSettings();
    public List<FilterRule> filters = new ArrayList<FilterRule>();
    public List<String> favorites = new ArrayList<String>();
    public List<String> blacklist = new ArrayList<String>();
    public List<String> whitelist = new ArrayList<String>();
    public List<StrategyConfig> strategies = new ArrayList<StrategyConfig>();
    public List<AdapterProfileConfig> adapterProfiles = new ArrayList<AdapterProfileConfig>();
    public String activeAdapterProfile = "funtime-generic";
    public String activeTheme = "funtime-dark";
    public List<ThemeConfig> customThemes = new ArrayList<ThemeConfig>();

    public static HelperConfig createDefault() {
        HelperConfig config = new HelperConfig();
        config.filters = Defaults.defaultFilters();
        config.strategies = Defaults.defaultStrategies();
        config.adapterProfiles = Defaults.defaultAdapterProfiles();
        config.ui.pagesHint = Defaults.pageNames();
        config.customThemes = new ArrayList<ThemeConfig>();
        config.applySafetyReset();
        return config;
    }

    /**
     * Forces every automation switch to OFF. Called on load, join, disconnect and after errors.
     *
     * @return true when at least one switch was actually on
     */
    public boolean applySafetyReset() {
        boolean wasOn = automation.masterEnabled || automation.autoBuyEnabled || automation.autoSellEnabled;
        automation.masterEnabled = false;
        automation.autoBuyEnabled = false;
        automation.autoSellEnabled = false;
        automation.pendingConfirmation = false;
        return wasOn;
    }

    public FilterRule findFilter(String id) {
        for (int i = 0; i < filters.size(); i++) {
            if (id != null && id.equals(filters.get(i).id)) {
                return filters.get(i);
            }
        }
        return null;
    }

    public StrategyConfig findStrategy(String id) {
        for (int i = 0; i < strategies.size(); i++) {
            if (id != null && id.equals(strategies.get(i).id)) {
                return strategies.get(i);
            }
        }
        return null;
    }

    public AdapterProfileConfig findAdapterProfile(String id) {
        for (int i = 0; i < adapterProfiles.size(); i++) {
            if (id != null && id.equals(adapterProfiles.get(i).id)) {
                return adapterProfiles.get(i);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Sections
    // ------------------------------------------------------------------

    public static final class Automation {
        /** Master automation switch. Always false after loading. */
        public boolean masterEnabled = false;
        public boolean autoBuyEnabled = false;
        public boolean autoSellEnabled = false;
        /** ANALYSIS_ONLY, MANUAL_ASSISTANT, AUTHORIZED_AUTOMATION. */
        public String mode = "ANALYSIS_ONLY";
        /** Purchase confirmation mode: ALWAYS, ABOVE_THRESHOLD, AUTHORIZED_API_ONLY. */
        public String purchaseConfirmation = "ALWAYS";
        public double purchaseAutoConfirmLimit = 0d;
        /** UI only: remembered "pending confirmation" flag, never a permission to trade. */
        public boolean pendingConfirmation = false;
        public int maxOperationsPerMinute = 6;
        public boolean requireServerIntegration = true;
    }

    public static final class BuySettings {
        public boolean enabled = false;
        public double maxPurchasePrice = 10000d;
        public double minExpectedMargin = 0.15d;
        public int maxPurchasesPerSession = 5;
        public double dailySpendingLimit = 50000d;
        public double minimumBalance = 1000d;
        public List<String> allowedItems = new ArrayList<String>();
        public List<String> blockedItems = new ArrayList<String>();
        public boolean requireCompleteData = true;
        public boolean recheckPriceBeforeBuy = true;
    }

    public static final class SellSettings {
        public boolean enabled = false;
        public double minSellingPrice = 10d;
        public double maxSellingPrice = 1_000_000d;
        public double targetProfitMargin = 0.20d;
        public int maxActiveListings = 5;
        /** FIXED, UNDER_CUT, MARKET_AVERAGE, MANUAL. */
        public String sellingStrategy = "MANUAL";
        public double priceAdjustment = 0d;
        public String confirmation = "ALWAYS";
        public List<String> allowedItems = new ArrayList<String>();
        public List<String> blockedItems = new ArrayList<String>();
        public boolean skipFavorites = true;
    }

    public static final class AnalysisSettings {
        public double minMargin = 0.10d;
        public double maxPrice = 100000d;
        public double minPrice = 1d;
        public int observationWindowMinutes = 60 * 24 * 7;
        public int minimumSampleCount = 5;
        public double outlierPercentile = 0.05d;
        public boolean showIncompleteDataWarnings = true;
        /** Record offers from profiles that the player has not verified yet. */
        public boolean recordUnverifiedProfiles = false;
        public int historyCap = 20000;
        public int historyRetentionDays = 60;
    }

    public static final class SellFees {
        /** Percent of the sale price, e.g. 0.05 for 5%. */
        public double percent = 0.05d;
        public double flat = 0d;
        public double minimum = 0d;
        public String currencyName = "coins";

        public double estimate(double salePrice) {
            if (Double.isNaN(salePrice) || salePrice <= 0d) {
                return 0d;
            }
            double fee = salePrice * percent + flat;
            return Math.max(minimum, fee);
        }
    }

    public static final class NotificationSettings {
        public boolean enabled = true;
        /** TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, TOP_CENTER. */
        public String position = "TOP_RIGHT";
        public int durationMs = 4500;
        public float opacity = 0.95f;
        public boolean sound = false;
        public float volume = 0.6f;
        public int maxVisible = 4;
        public int infoColor = 0xFF5274FF;
        public int successColor = 0xFF39DD9C;
        public int warningColor = 0xFFFFC266;
        public int errorColor = 0xFFFF5B78;
        public boolean chatMirror = true;
    }

    public static final class UiSettings {
        public int openKeyCode = KeyCodes.KEY_GRAVE_ACCENT;
        public int emergencyKeyCode = KeyCodes.KEY_F9;
        public boolean closeWithEscape = true;
        public boolean closeWithOpenKey = true;
        public boolean animationsEnabled = true;
        public float animationSpeed = 1f;
        public int cornerRadius = 8;
        public float backgroundOpacity = 0.86f;
        public boolean showTooltips = true;
        public boolean compactMode = false;
        public String language = "ru";
        public List<String> pagesHint = new ArrayList<String>();
        public int lastPage = 0;
    }

    public static final class AdapterProfileConfig {
        public String id = "funtime-generic";
        public String displayName = "FunTime (unverified)";
        public boolean enabled = true;
        /**
         * The mod does <b>not</b> claim to know FunTime's auction layout. These are generic,
         * editable patterns; a profile is only trusted once the player confirms the first
         * parsed offer. {@code verified} starts as false.
         */
        public boolean verified = false;
        public String screenTitlePattern = "";
        public String pricePattern = "(?i)(?:цена|price)\\s*[:\\-]?\\s*([0-9][0-9\\s.,]*(?:[кКkKmMкк]|kk)?)";
        public String quantityPattern = "(?i)(?:кол-?во|количество|amount|x)\\s*[:\\-]?\\s*(\\d{1,5})";
        public String itemNamePattern = "";
        public String serverNamePattern = "";
        public double priceMultiplier = 1d;
        public String currency = "coins";
        public boolean requireTwoStepConfirmation = true;
        public int minimumConfidence = 1;
    }

    public static final class ThemeConfig {
        public String id = "custom";
        public String displayName = "Custom";
        public int background = 0xFF070910;
        public int surface = 0xFF11141E;
        public int elevated = 0xFF1A1E2C;
        public int primary = 0xFF8B5CFF;
        public int secondary = 0xFF5274FF;
        public int accent = 0xFF00DBFF;
        public int text = 0xFFF7F8FF;
        public int textMuted = 0xFF8A90A6;
        public int success = 0xFF39DD9C;
        public int warning = 0xFFFFC266;
        public int error = 0xFFFF5B78;
        public float opacity = 0.95f;
        public float animationSpeed = 1f;
        public int cornerRadius = 8;

        public ThemeConfig() {
        }

        public ThemeConfig(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }
    }

    /** Built-in defaults for filters, strategies and adapter profiles. */
    public static final class Defaults {
        private Defaults() {
        }

        public static List<String> pageNames() {
            return Arrays.asList(
                    "DASHBOARD", "AUCTION ANALYSIS", "BUY SETTINGS", "SELL SETTINGS", "STRATEGIES",
                    "PRICE HISTORY", "FILTERS", "FAVORITES", "TRADE HISTORY", "NOTIFICATIONS", "SETTINGS");
        }

        public static List<FilterRule> defaultFilters() {
            List<FilterRule> rules = new ArrayList<FilterRule>();

            FilterRule cheapStacks = new FilterRule("tpl-cheap-stacks", "Template: cheap stacks");
            cheapStacks.maxPrice = 1000d;
            cheapStacks.minStackSize = 16;
            rules.add(cheapStacks);

            FilterRule underCut = new FilterRule("tpl-undercut", "Template: below market");
            underCut.maxUnitPrice = 25d;
            rules.add(underCut);

            FilterRule enchantedBooks = new FilterRule("tpl-books", "Template: enchanted books");
            enchantedBooks.itemIdContains = "enchanted_book";
            enchantedBooks.minStackSize = 1;
            rules.add(enchantedBooks);

            FilterRule empty = new FilterRule("tpl-blank", "Template: blank");
            rules.add(empty);
            return rules;
        }

        public static List<StrategyConfig> defaultStrategies() {
            List<StrategyConfig> list = new ArrayList<StrategyConfig>();

            StrategyConfig low = new StrategyConfig("strategy-low-price", StrategyConfig.Type.LOW_PRICE_ALERT,
                    "Low price alert");
            low.threshold = 500d;
            low.notifyOnly = true;
            list.add(low);

            StrategyConfig comparison = new StrategyConfig("strategy-comparison",
                    StrategyConfig.Type.PRICE_COMPARISON, "Price comparison");
            comparison.threshold = 0.10d;
            comparison.notifyOnly = true;
            list.add(comparison);

            StrategyConfig flip = new StrategyConfig("strategy-flip", StrategyConfig.Type.MANUAL_FLIP_ANALYSIS,
                    "Manual flip analysis");
            flip.threshold = 500d;
            flip.secondaryThreshold = 0.15d;
            flip.notifyOnly = true;
            list.add(flip);

            StrategyConfig history = new StrategyConfig("strategy-history",
                    StrategyConfig.Type.PRICE_HISTORY_ANALYSIS, "Price history analysis");
            history.minimumSamples = 5;
            history.threshold = 0.75d;
            history.notifyOnly = true;
            list.add(history);

            StrategyConfig planner = new StrategyConfig("strategy-inventory",
                    StrategyConfig.Type.INVENTORY_SELLING_PLANNER, "Inventory selling planner");
            planner.threshold = 1d;
            planner.notifyOnly = true;
            list.add(planner);
            return list;
        }

        public static List<AdapterProfileConfig> defaultAdapterProfiles() {
            List<AdapterProfileConfig> list = new ArrayList<AdapterProfileConfig>();

            AdapterProfileConfig funtime = new AdapterProfileConfig();
            funtime.id = "funtime-generic";
            funtime.displayName = "FunTime (unverified, editable patterns)";
            funtime.serverNamePattern = "(?i)funtime";
            list.add(funtime);

            AdapterProfileConfig generic = new AdapterProfileConfig();
            generic.id = "generic-auction";
            generic.displayName = "Generic auction GUI";
            generic.verified = false;
            generic.minimumConfidence = 2;
            list.add(generic);
            return list;
        }
    }
}
