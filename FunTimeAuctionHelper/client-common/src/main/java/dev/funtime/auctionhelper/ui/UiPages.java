package dev.funtime.auctionhelper.ui;

import dev.funtime.auctionhelper.analysis.FlipAnalysis;
import dev.funtime.auctionhelper.analysis.PriceStats;
import dev.funtime.auctionhelper.analysis.TradeHistoryStore;
import dev.funtime.auctionhelper.config.HelperConfig;
import dev.funtime.auctionhelper.engine.AutomationState;
import dev.funtime.auctionhelper.engine.TradeEngine;
import dev.funtime.auctionhelper.model.AuctionListing;
import dev.funtime.auctionhelper.model.FilterRule;
import dev.funtime.auctionhelper.model.NotificationEntry;
import dev.funtime.auctionhelper.model.PriceObservation;
import dev.funtime.auctionhelper.model.StrategyConfig;
import dev.funtime.auctionhelper.model.TradeRecord;
import dev.funtime.auctionhelper.util.Colors;
import dev.funtime.auctionhelper.util.KeyCodes;
import dev.funtime.auctionhelper.util.Numbers;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Renders every page of the GUI and turns widget ids into actions. */
final class UiPages {
    private final HelperUi ui;

    UiPages(HelperUi ui) {
        this.ui = ui;
    }

    private HelperConfig config() {
        return ui.config;
    }

    private float col(float x, float w, int index, int count) {
        float gap = 8f;
        float cw = (w - gap * (count - 1)) / count;
        return x + index * (cw + gap);
    }

    private float colWidth(float w, int count) {
        float gap = 8f;
        return (w - gap * (count - 1)) / count;
    }

    float contentHeight(HelperUi.Page page) {
        switch (page) {
            case DASHBOARD:
                return 480f;
            case ANALYSIS:
                return 520f;
            case BUY:
            case SELL:
                return 470f;
            case STRATEGIES:
                return 560f;
            case HISTORY:
                return 420f;
            case FILTERS:
                return 430f;
            case FAVORITES:
                return 260f;
            case TRADES:
                return 520f;
            case NOTIFICATIONS:
                return 470f;
            case SETTINGS:
                return 620f;
            default:
                return 400f;
        }
    }

    // ------------------------------------------------------------------
    // Dispatch
    // ------------------------------------------------------------------

    void render(UiCanvas canvas, HelperUi.Page page, float x, float y, float w, float h,
                int mx, int my, float dt) {
        switch (page) {
            case DASHBOARD:
                dashboard(canvas, x, y, w);
                break;
            case ANALYSIS:
                analysis(canvas, x, y, w);
                break;
            case BUY:
                buy(canvas, x, y, w);
                break;
            case SELL:
                sell(canvas, x, y, w);
                break;
            case STRATEGIES:
                strategies(canvas, x, y, w);
                break;
            case HISTORY:
                history(canvas, x, y, w);
                break;
            case FILTERS:
                filters(canvas, x, y, w);
                break;
            case FAVORITES:
                favorites(canvas, x, y, w);
                break;
            case TRADES:
                trades(canvas, x, y, w);
                break;
            case NOTIFICATIONS:
                notifications(canvas, x, y, w);
                break;
            case SETTINGS:
                settings(canvas, x, y, w);
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------------
    // Dashboard
    // ------------------------------------------------------------------

    private void dashboard(UiCanvas canvas, float x, float y, float w) {
        float cw = colWidth(w, 3);
        AutomationState automation = ui.core.getAutomation();

        ui.card(canvas, x, y, cw, 104);
        ui.sectionTitle(canvas, "MASTER AUTOMATION", x + 10, y + 10);
        ui.toggle(canvas, "tg:master", automation.isMasterEnabled(), x + 10, y + 30,
                automation.isMasterEnabled() ? "ON" : "OFF", true);
        ui.paragraph(canvas, automation.isMasterEnabled()
                ? "Automation is armed. Every action is still limited by the buy/sell limits and "
                  + "a valid server API."
                : "Nothing is bought, sold or bid while this is OFF. Switching it off cancels every "
                  + "pending operation immediately.", x + 10, y + 50, cw - 20, ui.muted());
        ui.value(canvas, automation.isAutomationAllowed() ? "AUTHORIZED API AVAILABLE"
                        : "MANUAL CONFIRMATION ONLY", x + 10, y + 88);
        if (!automation.isAutomationAllowed()) {
            canvas.drawText("blocked: " + automation.getAutomationBlockedReason(), x + 10, y + 96,
                    ui.warning(), false, 0.72f);
        }

        ui.card(canvas, x + cw + 8, y, cw, 104);
        ui.sectionTitle(canvas, "AUTO BUY", x + cw + 18, y + 10);
        ui.toggle(canvas, "tg:autobuy", automation.isAutoBuySwitchOn(), x + cw + 18, y + 30,
                automation.isAutoBuySwitchOn() ? "ON" : "OFF", true);
        ui.paragraph(canvas, automation.isAutoBuySwitchOn()
                ? "Automated purchases are allowed within your limits."
                : "No automated purchase can start while AUTO BUY is OFF.", x + cw + 18, y + 50, cw - 20,
                ui.muted());
        ui.value(canvas, "limit " + Numbers.formatAmount(config().buy.maxPurchasePrice), x + cw + 18, y + 88);

        ui.card(canvas, x + (cw + 8) * 2, y, cw, 104);
        ui.sectionTitle(canvas, "AUTO SELL", x + (cw + 8) * 2 + 10, y + 10);
        ui.toggle(canvas, "tg:autosell", automation.isAutoSellSwitchOn(), x + (cw + 8) * 2 + 10, y + 30,
                automation.isAutoSellSwitchOn() ? "ON" : "OFF", true);
        ui.paragraph(canvas, automation.isAutoSellSwitchOn()
                ? "Automated listings are allowed within your limits."
                : "No automated listing can be created while AUTO SELL is OFF.",
                x + (cw + 8) * 2 + 10, y + 50, cw - 20, ui.muted());
        ui.value(canvas, "listings " + config().sell.maxActiveListings,
                x + (cw + 8) * 2 + 10, y + 88);

        float y2 = y + 112;
        ui.button("emergency", "STOP ALL TRADING", x, y2, w, 26, ui.error(), false);
        canvas.drawText("Disables every switch, clears the pending queue and writes a journal entry.",
                x + 4, y2 + 30, ui.muted(), false, 0.78f);

        float y3 = y2 + 44;
        float halfW = (w - 8) / 2f;
        ui.card(canvas, x, y3, halfW, 118);
        ui.sectionTitle(canvas, "STATUS", x + 10, y3 + 10);
        String[][] rows = new String[][]{
                {"Mode", automation.getMode().name()},
                {"Automation API", automation.getIntegration().isAvailable()
                        ? automation.getIntegration().getName() : "unavailable"},
                {"Screens analysed", String.valueOf(ui.core.getAdapters().getAnalysedScreens())},
                {"Offers parsed", String.valueOf(ui.core.getAdapters().getParsedListings())},
                {"Price points", String.valueOf(ui.core.getPriceHistory().size())},
                {"Pending operations", String.valueOf(ui.core.getTradeEngine().getPendingCount())},
        };
        for (int i = 0; i < rows.length; i++) {
            ui.label(canvas, rows[i][0], x + 10, y3 + 28 + i * 13);
            ui.value(canvas, rows[i][1], x + 110, y3 + 28 + i * 13);
        }

        ui.card(canvas, x + halfW + 8, y3, halfW, 118);
        ui.sectionTitle(canvas, "CONFIRMED TRADES", x + halfW + 18, y3 + 10);
        TradeHistoryStore.Stats stats = ui.core.getTradeHistory().stats();
        ui.label(canvas, "Confirmed operations", x + halfW + 18, y3 + 30);
        ui.value(canvas, String.valueOf(stats.confirmed), x + halfW + 160, y3 + 30);
        ui.label(canvas, "Spent", x + halfW + 18, y3 + 44);
        ui.value(canvas, Numbers.formatAmount(stats.spent), x + halfW + 160, y3 + 44);
        ui.label(canvas, "Earned", x + halfW + 18, y3 + 58);
        ui.value(canvas, Numbers.formatAmount(stats.earned), x + halfW + 160, y3 + 58);
        ui.label(canvas, "Net", x + halfW + 18, y3 + 72);
        ui.value(canvas, Numbers.formatSigned(stats.net), x + halfW + 160, y3 + 72);
        ui.paragraph(canvas, "Only confirmed operations are counted. Pending or unverified attempts "
                + "never appear in these totals.", x + halfW + 18, y3 + 88, halfW - 28, ui.muted());

        float y4 = y3 + 126;
        ui.card(canvas, x, y4, w, 118);
        ui.sectionTitle(canvas, "STRATEGY SIGNALS", x + 10, y4 + 10);
        List<dev.funtime.auctionhelper.analysis.StrategySignal> signals =
                ui.core.getStrategyEngine().evaluate(ui.core.getLastListings(), config(),
                        ui.core.getPriceHistory(), System.currentTimeMillis());
        if (signals.isEmpty()) {
            ui.paragraph(canvas, "No signals yet. Enable strategies and open an auction screen so the "
                    + "mod can observe real offers.", x + 10, y4 + 30, w - 20, ui.muted());
        } else {
            for (int i = 0; i < signals.size() && i < 6; i++) {
                dev.funtime.auctionhelper.analysis.StrategySignal signal = signals.get(i);
                int color = signal.getKind() == dev.funtime.auctionhelper.analysis.StrategySignal.Kind.WARNING
                        ? ui.warning() : ui.success();
                canvas.fillRoundedRect(x + 10, y4 + 26 + i * 14, 3, 10, 1, color);
                canvas.drawText(ui.trim(signal.getTitle() + ": " + signal.getDetail(), canvas, w - 40, 0.8f),
                        x + 18, y4 + 28 + i * 14, ui.text(), false, 0.8f);
            }
        }

        float y5 = y4 + 126;
        ui.card(canvas, x, y5, w, 72);
        ui.sectionTitle(canvas, "RECENT NOTIFICATIONS", x + 10, y5 + 10);
        List<NotificationEntry> history = ui.core.getNotifications().history();
        if (history.isEmpty()) {
            ui.label(canvas, "Nothing yet.", x + 10, y5 + 30);
        } else {
            int shown = 0;
            for (int i = history.size() - 1; i >= 0 && shown < 4; i--, shown++) {
                NotificationEntry entry = history.get(i);
                canvas.drawText(ui.trim(entry.getMessage(), canvas, w - 30, 0.8f), x + 10, y5 + 28 + shown * 11,
                        ui.core.getNotifications().colorFor(entry.getKind()), false, 0.8f);
            }
        }
    }

    // ------------------------------------------------------------------
    // Auction analysis
    // ------------------------------------------------------------------

    private void analysis(UiCanvas canvas, float x, float y, float w) {
        List<AuctionListing> listings = ui.core.getLastListings();
        String title = ui.core.getAdapters().getLastTitle();
        String warning = ui.core.getAdapters().getLastWarning();

        ui.card(canvas, x, y, w, 56);
        ui.sectionTitle(canvas, "LAST OBSERVED SCREEN", x + 10, y + 10);
        ui.value(canvas, title == null || title.isEmpty() ? "none" : title, x + 10, y + 28);
        String profile = ui.core.getAdapters().getLastResult() == null
                ? "-" : ui.core.getAdapters().getLastResult().getProfileId();
        ui.label(canvas, "profile: " + profile + "  |  offers: " + listings.size(), x + 10, y + 42);
        if (warning != null && !warning.isEmpty()) {
            canvas.drawText(ui.trim(warning, canvas, w - 150, 0.78f), x + w - 150, y + 42, ui.warning(), false, 0.78f);
        }

        float y1 = y + 64;
        float tableW = w;
        ui.card(canvas, x, y1, tableW, 268);
        ui.sectionTitle(canvas, "OFFERS", x + 10, y1 + 10);
        float[] offsets = new float[]{150f, 250f, 330f, 410f, 500f};
        ui.row(canvas, x + 10, y1 + 30, tableW - 20,
                new String[]{"ITEM", "QTY", "TOTAL", "PER ITEM", "MEDIAN", "SAMPLES"}, offsets, ui.muted(), true);
        if (listings.isEmpty()) {
            ui.paragraph(canvas, "No offers captured. Open the auction GUI in game: the mod only reads "
                    + "what the server already shows you, it never clicks anything.", x + 10, y1 + 46,
                    tableW - 20, ui.muted());
        }
        long now = System.currentTimeMillis();
        for (int i = 0; i < listings.size() && i < 14; i++) {
            AuctionListing listing = listings.get(i);
            float rowY = y1 + 46 + i * 14;
            float[] rect = new float[]{x + 6, rowY - 3, tableW - 12, 13};
            ui.register("listing:" + i, rect);
            boolean selected = ui.selectedListingIndex() == i;
            boolean hovered = ui.hover("listing:" + i);
            if (selected) {
                canvas.fillRoundedRect(rect[0], rect[1], rect[2], rect[3], 3,
                        Colors.withAlpha(ui.primary(), 0x44));
            } else if (hovered) {
                canvas.fillRoundedRect(rect[0], rect[1], rect[2], rect[3], 3,
                        Colors.withAlpha(ui.elevated(), 0x88));
            }
            List<PriceObservation> observations = ui.core.getPriceHistory()
                    .windowFor(listing.getItemId(), config().analysis.observationWindowMinutes, now);
            PriceStats stats = ui.core.getPriceAnalyzer().compute(observations);
            canvas.drawText(ui.trim(listing.getItemName(), canvas, 140, 0.8f), x + 10, rowY, ui.text(), false, 0.8f);
            canvas.drawText(String.valueOf(listing.getQuantity()), x + offsets[0] + 10, rowY, ui.text(), false, 0.8f);
            canvas.drawText(Numbers.formatAmount(listing.getTotalPrice()), x + offsets[1] + 10, rowY,
                    ui.text(), false, 0.8f);
            canvas.drawText(Double.isNaN(listing.getUnitPrice()) ? "-" : Numbers.formatAmount(listing.getUnitPrice()),
                    x + offsets[2] + 10, rowY, ui.text(), false, 0.8f);
            canvas.drawText(stats.isEmpty() ? "-" : Numbers.formatAmount(stats.getMedian()),
                    x + offsets[3] + 10, rowY, stats.isEmpty() ? ui.muted() : ui.text(), false, 0.8f);
            canvas.drawText(String.valueOf(stats.getSampleCount()), x + offsets[4] + 10, rowY,
                    stats.isReliable(config().analysis.minimumSampleCount) ? ui.success() : ui.warning(), false, 0.8f);
        }

        float detailY = y1 + 276;
        ui.card(canvas, x, detailY, w, 132);
        ui.sectionTitle(canvas, "SELECTED OFFER", x + 10, detailY + 10);
        if (listings.isEmpty()) {
            ui.label(canvas, "Select an offer in the table above.", x + 10, detailY + 30);
        } else {
            int index = Math.min(ui.selectedListingIndex(), listings.size() - 1);
            AuctionListing listing = listings.get(index);
            List<PriceObservation> observations = ui.core.getPriceHistory()
                    .windowFor(listing.getItemId(), config().analysis.observationWindowMinutes, System.currentTimeMillis());
            PriceStats stats = ui.core.getPriceAnalyzer().compute(observations);
            FlipAnalysis flip = ui.core.getPriceAnalyzer().analyze(listing, stats, stats.getMedian(),
                    config().fees, config().analysis.minimumSampleCount);
            ui.value(canvas, listing.getItemName() + "  x" + listing.getQuantity(), x + 10, detailY + 30);
            ui.label(canvas, "total " + Numbers.formatAmount(listing.getTotalPrice())
                    + "  |  per item " + Numbers.formatAmount(listing.getUnitPrice())
                    + "  |  per stack " + Numbers.formatAmount(listing.getStackPrice()), x + 10, detailY + 44);
            ui.label(canvas, "estimated fees " + Numbers.formatAmount(flip.getEstimatedFees())
                    + "  |  net per item " + Numbers.formatAmount(flip.getNetProfitPerItem())
                    + "  |  margin " + Numbers.formatPercent(flip.getMarginRatio()), x + 10, detailY + 58);
            ui.paragraph(canvas, flip.getNote() + ". Estimates use your own local history and are not "
                    + "a guarantee of profit.", x + 10, detailY + 72, w - 20,
                    flip.isLowConfidence() ? ui.warning() : ui.muted());
            ui.button("analysis:queueBuy", "QUEUE MANUAL BUY", x + 10, detailY + 100, 130, 20, ui.primary(), false);
            ui.button("analysis:addFavorite", "ADD TO FAVOURITES", x + 148, detailY + 100, 130, 20,
                    ui.secondary(), true);
            ui.button("analysis:verify", "CONFIRM PARSED LAYOUT", x + 286, detailY + 100, 160, 20,
                    ui.accent(), true);
            ui.paragraph(canvas, ui.core.getTradeEngine().getLastRefusal(), x + 456, detailY + 102, w - 460,
                    ui.muted());
        }
    }

    // ------------------------------------------------------------------
    // Buy / sell
    // ------------------------------------------------------------------

    private void buy(UiCanvas canvas, float x, float y, float w) {
        HelperConfig.BuySettings buy = config().buy;
        float half = (w - 8) / 2f;
        ui.card(canvas, x, y, half, 250);
        ui.sectionTitle(canvas, "AUTO BUY LIMITS", x + 10, y + 10);
        ui.toggle(canvas, "tg:buyEnabled", buy.enabled, x + 10, y + 30, "Enable auto buy", true);
        ui.toggle(canvas, "tg:buyRecheck", buy.recheckPriceBeforeBuy, x + 10, y + 50,
                "Re-check price before buying", true);
        ui.toggle(canvas, "tg:buyComplete", buy.requireCompleteData, x + 10, y + 70,
                "Refuse incomplete offer data", true);
        ui.slider(canvas, "buy:maxPurchasePrice", "Maximum purchase price", buy.maxPurchasePrice,
                0d, 100000d, x + 10, y + 94, half - 20, "coins");
        ui.slider(canvas, "buy:minExpectedMargin", "Minimum expected margin", buy.minExpectedMargin,
                0d, 1d, x + 10, y + 126, half - 20, "ratio");
        ui.slider(canvas, "buy:dailySpendingLimit", "Daily spending limit", buy.dailySpendingLimit,
                0d, 500000d, x + 10, y + 158, half - 20, "coins");
        ui.slider(canvas, "buy:minimumBalance", "Minimum balance to keep", buy.minimumBalance,
                0d, 100000d, x + 10, y + 190, half - 20, "coins");
        ui.numField(canvas, "buy:maxPurchases", "Max purchases per session",
                String.valueOf(buy.maxPurchasesPerSession), x + 10, y + 216, 90);

        float rx = x + half + 8;
        ui.card(canvas, rx, y, half, 250);
        ui.sectionTitle(canvas, "ITEM FILTERS", rx + 10, y + 10);
        ui.label(canvas, "Allowed items (empty = all)", rx + 10, y + 28);
        ui.listEditor(canvas, "buy:allow", buy.allowedItems, rx + 10, y + 40, half - 20, "minecraft:diamond");
        ui.label(canvas, "Blocked items", rx + 10, y + 132);
        ui.listEditor(canvas, "buy:block", buy.blockedItems, rx + 10, y + 144, half - 20, "minecraft:dirt");
        ui.paragraph(canvas, "Allowed/blocked lists are matched against the exact item id.", rx + 10, y + 228,
                half - 20, ui.muted());

        float y2 = y + 258;
        ui.card(canvas, x, y2, w, 100);
        ui.sectionTitle(canvas, "SAFETY RULES", x + 10, y2 + 10);
        ui.paragraph(canvas, "Every purchase must pass: item id + amount + price present, price under the "
                + "maximum, margin under your target, session and daily limits, balance above the minimum, "
                + "and an authorized server API. If any of them is unknown the operation is refused. "
                + "A network error leaves the operation UNVERIFIED and the mod will not retry it blindly.",
                x + 10, y2 + 28, w - 20, ui.muted());
    }

    private void sell(UiCanvas canvas, float x, float y, float w) {
        HelperConfig.SellSettings sell = config().sell;
        float half = (w - 8) / 2f;
        ui.card(canvas, x, y, half, 250);
        ui.sectionTitle(canvas, "AUTO SELL LIMITS", x + 10, y + 10);
        ui.toggle(canvas, "tg:sellEnabled", sell.enabled, x + 10, y + 30, "Enable auto sell", true);
        ui.toggle(canvas, "tg:sellSkipFavorites", sell.skipFavorites, x + 10, y + 50,
                "Never sell favourites", true);
        ui.slider(canvas, "sell:minSellingPrice", "Minimum selling price", sell.minSellingPrice,
                0d, 10000d, x + 10, y + 74, half - 20, "coins");
        ui.slider(canvas, "sell:maxSellingPrice", "Maximum selling price", sell.maxSellingPrice,
                0d, 1000000d, x + 10, y + 106, half - 20, "coins");
        ui.slider(canvas, "sell:targetProfitMargin", "Target profit margin", sell.targetProfitMargin,
                0d, 1d, x + 10, y + 138, half - 20, "ratio");
        ui.slider(canvas, "sell:priceAdjustment", "Price adjustment", sell.priceAdjustment,
                -0.5d, 0.5d, x + 10, y + 170, half - 20, "ratio");
        ui.numField(canvas, "sell:maxListings", "Maximum active listings",
                String.valueOf(sell.maxActiveListings), x + 10, y + 202, 90);

        float rx = x + half + 8;
        ui.card(canvas, rx, y, half, 250);
        ui.sectionTitle(canvas, "STRATEGY & ITEMS", rx + 10, y + 10);
        ui.dropdown(canvas, "dd:sellStrategy", "Selling strategy",
                Arrays.asList("MANUAL", "FIXED", "UNDER_CUT", "MARKET_AVERAGE"), sell.sellingStrategy,
                rx + 10, y + 26, half - 20);
        ui.label(canvas, "Allowed items (empty = all)", rx + 10, y + 66);
        ui.listEditor(canvas, "sell:allow", sell.allowedItems, rx + 10, y + 78, half - 20, "minecraft:emerald");
        ui.label(canvas, "Blocked items", rx + 10, y + 150);
        ui.listEditor(canvas, "sell:block", sell.blockedItems, rx + 10, y + 162, half - 20, "minecraft:stick");

        float y2 = y + 258;
        ui.card(canvas, x, y2, w, 96);
        ui.sectionTitle(canvas, "FEES", x + 10, y2 + 10);
        ui.slider(canvas, "fees:percent", "Fee percent", config().fees.percent * 100d, 0d, 30d,
                x + 10, y2 + 28, (w - 30) / 2f, "%");
        ui.numField(canvas, "fees:flat", "Flat fee", String.valueOf(config().fees.flat),
                x + (w + 10) / 2f, y2 + 24, 80);
        ui.paragraph(canvas, "Fees are only used for estimates (margin and net profit). They never change "
                + "the price the server shows you.", x + 10, y2 + 66, w - 20, ui.muted());
    }

    // ------------------------------------------------------------------
    // Strategies
    // ------------------------------------------------------------------

    private void strategies(UiCanvas canvas, float x, float y, float w) {
        ui.paragraph(canvas, "Strategies only produce suggestions. They never buy or sell anything: a "
                + "trade always goes through the buy/sell guard pipeline.", x, y, w, ui.muted());
        float cw = colWidth(w, 2);
        for (int i = 0; i < config().strategies.size(); i++) {
            StrategyConfig strategy = config().strategies.get(i);
            float cx = x + (i % 2) * (cw + 8);
            float cy = y + 24 + (i / 2) * 118f;
            ui.card(canvas, cx, cy, cw, 110);
            ui.sectionTitle(canvas, strategy.displayName.toUpperCase(java.util.Locale.ROOT), cx + 10, cy + 10);
            ui.toggle(canvas, "tg:strategy:" + strategy.id, strategy.enabled, cx + 10, cy + 30,
                    strategy.enabled ? "enabled" : "disabled", true);
            ui.chip("chip:type:" + strategy.id, strategy.getType().name(), cx + cw - 130, cy + 8, 120, 14,
                    ui.secondary());
            ui.slider(canvas, "strategy:" + strategy.id + ":threshold",
                    thresholdCaption(strategy.getType()), strategy.threshold,
                    strategy.getType() == StrategyConfig.Type.LOW_PRICE_ALERT ? 0d : 0d,
                    strategy.getType() == StrategyConfig.Type.LOW_PRICE_ALERT ? 20000d : 2d,
                    cx + 10, cy + 54, cw - 20, "");
            if (strategy.getType() == StrategyConfig.Type.PRICE_HISTORY_ANALYSIS
                    || strategy.getType() == StrategyConfig.Type.MANUAL_FLIP_ANALYSIS) {
                ui.numField(canvas, "strategy:" + strategy.id + ":samples", "Min samples",
                        String.valueOf(strategy.minimumSamples), cx + 10, cy + 84, 70);
                ui.label(canvas, "notify only", cx + cw - 76, cy + 92);
            } else {
                ui.label(canvas, "notify only", cx + 10, cy + 92);
            }
        }
    }

    private String thresholdCaption(StrategyConfig.Type type) {
        switch (type) {
            case LOW_PRICE_ALERT:
                return "Alert below price";
            case PRICE_COMPARISON:
                return "Minimum edge over next offer";
            case MANUAL_FLIP_ANALYSIS:
                return "Minimum estimated profit";
            case PRICE_HISTORY_ANALYSIS:
                return "Below median factor";
            default:
                return "Threshold";
        }
    }

    // ------------------------------------------------------------------
    // Price history
    // ------------------------------------------------------------------

    private void history(UiCanvas canvas, float x, float y, float w) {
        List<String> items = ui.core.getPriceHistory().knownItems();
        String selected = ui.selectedHistoryItem();
        if (selected == null || selected.isEmpty()) {
            selected = items.isEmpty() ? "-" : items.get(0);
            ui.selectHistoryItem(selected);
        }
        ui.card(canvas, x, y, (w - 8) / 2f, 60);
        ui.sectionTitle(canvas, "ITEM", x + 10, y + 10);
        ui.dropdown(canvas, "dd:historyItem", "Tracked items", items.isEmpty()
                ? Arrays.asList("(no data yet)") : items, selected, x + 10, y + 26, (w - 8) / 2f - 20);

        ui.card(canvas, x + (w - 8) / 2f + 8, y, (w - 8) / 2f, 60);
        ui.sectionTitle(canvas, "MAINTENANCE", x + (w - 8) / 2f + 18, y + 10);
        float bx = x + (w - 8) / 2f + 18;
        ui.button("history:export", "EXPORT CSV", bx, y + 28, 84, 18, ui.success(), true);
        ui.button("history:import", "IMPORT CSV", bx + 90, y + 28, 84, 18, ui.secondary(), true);
        ui.button("history:cleanup", "CLEANUP", bx + 180, y + 28, 70, 18, ui.warning(), true);
        canvas.drawText(String.valueOf(ui.core.getPriceHistory().size()) + " points", bx + 258, y + 33,
                ui.muted(), false, 0.8f);

        float chartY = y + 68;
        ui.card(canvas, x, chartY, w, 180);
        long now = System.currentTimeMillis();
        List<double[]> series = ui.core.getPriceHistory().seriesFor(selected, 32,
                config().analysis.observationWindowMinutes, now);
        ui.chart(canvas, x + 10, chartY + 10, w - 20, 120, series, ui.accent(),
                "No price points for this item yet");
        List<PriceObservation> observations = ui.core.getPriceHistory()
                .windowFor(selected, config().analysis.observationWindowMinutes, now);
        PriceStats stats = ui.core.getPriceAnalyzer().compute(observations);
        ui.label(canvas, "samples " + stats.getSampleCount()
                + "   avg " + Numbers.formatAmount(stats.getAverage())
                + "   median " + Numbers.formatAmount(stats.getMedian())
                + "   min " + Numbers.formatAmount(stats.getMinimum())
                + "   max " + Numbers.formatAmount(stats.getMaximum()), x + 10, chartY + 140);
        ui.label(canvas, "Window: " + config().analysis.observationWindowMinutes + " min  |  p25 "
                + Numbers.formatAmount(stats.getP25()) + "  |  p75 " + Numbers.formatAmount(stats.getP75())
                + "  |  avg stack " + Numbers.formatAmount(stats.getAverageQuantity()), x + 10, chartY + 154);
        if (config().analysis.showIncompleteDataWarnings
                && !stats.isReliable(config().analysis.minimumSampleCount)) {
            canvas.drawText("Warning: only " + stats.getSampleCount() + " of "
                    + config().analysis.minimumSampleCount + " samples - treat these numbers as weak.",
                    x + 10, chartY + 168, ui.warning(), false, 0.8f);
        }

        float y2 = chartY + 188;
        ui.card(canvas, x, y2, w, 60);
        ui.sectionTitle(canvas, "SETTINGS", x + 10, y2 + 10);
        ui.slider(canvas, "analysis:window", "Observation window (minutes)",
                config().analysis.observationWindowMinutes, 60d, 20160d, x + 10, y2 + 28, (w - 30) / 2f, "min");
        ui.numField(canvas, "analysis:samples", "Minimum sample count",
                String.valueOf(config().analysis.minimumSampleCount), x + (w + 10) / 2f, y2 + 24, 70);
    }

    // ------------------------------------------------------------------
    // Filters
    // ------------------------------------------------------------------

    private void filters(UiCanvas canvas, float x, float y, float w) {
        float listW = Math.min(200f, w * 0.34f);
        ui.card(canvas, x, y, listW, 300);
        ui.sectionTitle(canvas, "SAVED FILTERS", x + 10, y + 10);
        for (int i = 0; i < config().filters.size(); i++) {
            FilterRule rule = config().filters.get(i);
            float rowY = y + 28 + i * 17f;
            float[] rect = new float[]{x + 6, rowY - 3, listW - 12, 15};
            ui.register("filter:" + rule.id, rect);
            boolean active = rule.id.equals(ui.selectedFilterId());
            if (active) {
                canvas.fillRoundedRect(rect[0], rect[1], rect[2], rect[3], 4,
                        Colors.withAlpha(ui.primary(), 0x44));
            } else if (ui.hover("filter:" + rule.id)) {
                canvas.fillRoundedRect(rect[0], rect[1], rect[2], rect[3], 4,
                        Colors.withAlpha(ui.elevated(), 0x88));
            }
            canvas.drawText(ui.trim(rule.name, canvas, listW - 30, 0.8f), x + 10, rowY,
                    rule.enabled ? ui.text() : ui.muted(), false, 0.8f);
            canvas.fillRoundedRect(x + listW - 18, rowY, 6, 6, 3, rule.enabled ? ui.success() : ui.muted());
        }
        ui.button("filter:new", "+ NEW", x + 10, y + 268, 60, 18, ui.primary(), false);
        ui.button("filter:delete", "DELETE", x + 76, y + 268, 62, 18, ui.error(), true);

        FilterRule rule = config().findFilter(ui.selectedFilterId());
        float ex = x + listW + 8;
        float ew = w - listW - 8;
        ui.card(canvas, ex, y, ew, 300);
        ui.sectionTitle(canvas, "EDIT FILTER", ex + 10, y + 10);
        if (rule == null) {
            ui.paragraph(canvas, "Select a filter on the left or create a new one. Templates: cheap "
                    + "stacks, below market, enchanted books.", ex + 10, y + 30, ew - 20, ui.muted());
        } else {
            ui.toggle(canvas, "tg:filterEnabled", rule.enabled, ex + 10, y + 30, "enabled", true);
            ui.numField(canvas, "filter:name", "Name", rule.name, ex + 10, y + 50, 140);
            ui.numField(canvas, "filter:nameContains", "Name contains", rule.nameContains,
                    ex + 160, y + 50, 120);
            ui.numField(canvas, "filter:itemId", "Item id contains", rule.itemIdContains,
                    ex + 290, y + 50, 140);
            ui.slider(canvas, "filter:maxPrice", "Maximum price", rule.maxPrice, 0d, 100000d,
                    ex + 10, y + 84, (ew - 30) / 2f, "coins");
            ui.slider(canvas, "filter:maxUnitPrice", "Maximum price per item", rule.maxUnitPrice, 0d, 10000d,
                    ex + (ew + 10) / 2f, y + 84, (ew - 30) / 2f, "coins");
            ui.slider(canvas, "filter:minStack", "Minimum stack size", rule.minStackSize, 0d, 64d,
                    ex + 10, y + 116, (ew - 30) / 2f, "items");
            ui.slider(canvas, "filter:maxStack", "Maximum stack size", rule.maxStackSize, 0d, 64d,
                    ex + (ew + 10) / 2f, y + 116, (ew - 30) / 2f, "items");
            ui.toggle(canvas, "tg:filterFavorites", rule.favoritesOnly, ex + 10, y + 150,
                    "Favourites only", true);
            ui.paragraph(canvas, "Criteria: " + rule.describe() + ". Matching a filter never trades by "
                    + "itself - it only decides what the analysis shows you.", ex + 10, y + 172, ew - 20,
                    ui.muted());
            ui.button("filter:save", "SAVE", ex + 10, y + 268, 60, 18, ui.success(), false);
            ui.button("filter:duplicate", "DUPLICATE", ex + 76, y + 268, 72, 18, ui.secondary(), true);
            ui.button("filter:toggle", rule.enabled ? "DISABLE" : "ENABLE", ex + 154, y + 268, 66, 18,
                    ui.warning(), true);
        }

        float y2 = y + 308;
        ui.card(canvas, x, y2, w, 96);
        ui.sectionTitle(canvas, "TEMPLATES", x + 10, y2 + 10);
        ui.paragraph(canvas, "Ready made templates live in the config file and can be recreated with "
                + "NEW. Use them as a starting point: every value is editable and saved immediately.",
                x + 10, y2 + 28, w - 20, ui.muted());
        ui.button("filter:templates", "RESTORE TEMPLATES", x + 10, y2 + 66, 140, 18, ui.secondary(), true);
    }

    // ------------------------------------------------------------------
    // Favourites
    // ------------------------------------------------------------------

    private void favorites(UiCanvas canvas, float x, float y, float w) {
        float cw = colWidth(w, 3);
        ui.card(canvas, x, y, cw, 220);
        ui.sectionTitle(canvas, "FAVOURITES", x + 10, y + 10);
        ui.paragraph(canvas, "Items you track. Favourites can be protected from auto sell.", x + 10, y + 28,
                cw - 20, ui.muted());
        ui.listEditor(canvas, "fav", config().favorites, x + 10, y + 52, cw - 20, "minecraft:diamond");

        ui.card(canvas, x + cw + 8, y, cw, 220);
        ui.sectionTitle(canvas, "WHITELIST", x + cw + 18, y + 10);
        ui.paragraph(canvas, "When this list is not empty only these item ids are considered at all.",
                x + cw + 18, y + 28, cw - 20, ui.muted());
        ui.listEditor(canvas, "white", config().whitelist, x + cw + 18, y + 52, cw - 20, "minecraft:emerald");

        ui.card(canvas, x + (cw + 8) * 2, y, cw, 220);
        ui.sectionTitle(canvas, "BLACKLIST", x + (cw + 8) * 2 + 10, y + 10);
        ui.paragraph(canvas, "Blocked everywhere: filters, strategies and the trade engine ignore them.",
                x + (cw + 8) * 2 + 10, y + 28, cw - 20, ui.muted());
        ui.listEditor(canvas, "black", config().blacklist, x + (cw + 8) * 2 + 10, y + 52, cw - 20,
                "minecraft:dirt");
    }

    // ------------------------------------------------------------------
    // Trade history
    // ------------------------------------------------------------------

    private void trades(UiCanvas canvas, float x, float y, float w) {
        TradeHistoryStore.Stats stats = ui.core.getTradeHistory().stats();
        ui.card(canvas, x, y, w, 46);
        String summary = "confirmed " + stats.confirmed + "   |   spent " + Numbers.formatAmount(stats.spent)
                + "   |   earned " + Numbers.formatAmount(stats.earned)
                + "   |   net " + Numbers.formatSigned(stats.net)
                + "   |   pending " + stats.pending + "   |   unverified " + stats.unverified;
        ui.paragraph(canvas, summary, x + 10, y + 12, w - 20, ui.text());
        ui.label(canvas, "Only CONFIRMED operations are counted in spent/earned/net.", x + 10, y + 30);

        float py = y + 54;
        List<TradeEngine.PendingTask> pending = ui.core.getTradeEngine().pendingTasks();
        ui.card(canvas, x, py, w, 30 + Math.max(1, pending.size()) * 18);
        ui.sectionTitle(canvas, "WAITING FOR CONFIRMATION", x + 10, py + 10);
        if (pending.isEmpty()) {
            ui.label(canvas, "Nothing is queued.", x + 10, py + 30);
        }
        for (int i = 0; i < pending.size(); i++) {
            TradeEngine.PendingTask task = pending.get(i);
            float rowY = py + 28 + i * 18;
            canvas.drawText(task.request.getType().name() + " " + task.request.getListing().getItemName()
                    + " x" + task.request.getListing().getQuantity() + " @ "
                    + Numbers.formatAmount(task.request.getPrice()), x + 10, rowY, ui.text(), false, 0.82f);
            ui.button("trade:confirm:" + task.request.getRequestId(), "CONFIRM", x + w - 150, rowY - 4, 66, 16,
                    ui.success(), false);
            ui.button("trade:reject:" + task.request.getRequestId(), "REJECT", x + w - 78, rowY - 4, 66, 16,
                    ui.error(), true);
        }

        float ty = py + 40 + Math.max(1, pending.size()) * 18;
        ui.card(canvas, x, ty, w, 300);
        ui.sectionTitle(canvas, "JOURNAL", x + 10, ty + 10);
        float[] offsets = new float[]{120f, 230f, 290f, 360f, 450f, 540f};
        ui.row(canvas, x + 10, ty + 30, w - 20, new String[]{"RESULT", "TYPE", "ITEM", "QTY", "PRICE", "AUTOMATION"},
                offsets, ui.muted(), true);
        List<TradeRecord> records = ui.core.getTradeHistory().all();
        if (records.isEmpty()) {
            ui.paragraph(canvas, "No operations yet. Queued operations appear here immediately, but they "
                    + "are only counted once confirmed.", x + 10, ty + 46, w - 20, ui.muted());
        }
        for (int i = 0; i < records.size() && i < 13; i++) {
            TradeRecord record = records.get(i);
            float rowY = ty + 46 + i * 14;
            int color = resultColor(record.getResult());
            canvas.drawText(record.getResult().name(), x + 10, rowY, color, false, 0.8f);
            canvas.drawText(record.getType().name(), x + offsets[0], rowY, ui.text(), false, 0.8f);
            canvas.drawText(ui.trim(record.getItemName(), canvas, 100, 0.8f), x + offsets[1], rowY, ui.text(),
                    false, 0.8f);
            canvas.drawText(String.valueOf(record.getQuantity()), x + offsets[2], rowY, ui.text(), false, 0.8f);
            canvas.drawText(Numbers.formatAmount(record.getPrice()), x + offsets[3], rowY, ui.text(), false, 0.8f);
            canvas.drawText(record.getAutomationMode() == null ? "-" : record.getAutomationMode(),
                    x + offsets[4], rowY, ui.muted(), false, 0.8f);
        }
        ui.button("trade:export", "EXPORT CSV", x + 10, ty + 272, 90, 18, ui.secondary(), true);
        ui.button("trade:clear", "CLEAR HISTORY", x + 106, ty + 272, 100, 18, ui.error(), true);
    }

    private int resultColor(TradeRecord.Result result) {
        switch (result) {
            case CONFIRMED:
                return ui.success();
            case PENDING:
                return ui.secondary();
            case STALE:
            case UNVERIFIED:
                return ui.warning();
            default:
                return ui.error();
        }
    }

    // ------------------------------------------------------------------
    // Notifications
    // ------------------------------------------------------------------

    private void notifications(UiCanvas canvas, float x, float y, float w) {
        HelperConfig.NotificationSettings settings = config().notifications;
        float half = (w - 8) / 2f;
        ui.card(canvas, x, y, half, 250);
        ui.sectionTitle(canvas, "DELIVERY", x + 10, y + 10);
        ui.toggle(canvas, "tg:notifEnabled", settings.enabled, x + 10, y + 30, "Show notifications", true);
        ui.toggle(canvas, "tg:notifSound", settings.sound, x + 10, y + 50, "Play sound", true);
        ui.toggle(canvas, "tg:notifChat", settings.chatMirror, x + 10, y + 70, "Mirror to chat", true);
        ui.dropdown(canvas, "dd:notifPosition", "Position",
                Arrays.asList("TOP_RIGHT", "TOP_LEFT", "BOTTOM_RIGHT", "BOTTOM_LEFT"), settings.position,
                x + 10, y + 90, half - 20);
        ui.slider(canvas, "notif:duration", "Duration (ms)", settings.durationMs, 1000d, 15000d,
                x + 10, y + 130, half - 20, "ms");
        ui.slider(canvas, "notif:opacity", "Opacity", settings.opacity, 0.2d, 1d, x + 10, y + 162, half - 20, "");
        ui.slider(canvas, "notif:volume", "Volume", settings.volume, 0d, 1d, x + 10, y + 194, half - 20, "");
        ui.numField(canvas, "notif:maxVisible", "Max visible", String.valueOf(settings.maxVisible),
                x + 10, y + 222, 70);

        ui.card(canvas, x + half + 8, y, half, 250);
        ui.sectionTitle(canvas, "CENTRE", x + half + 18, y + 10);
        List<NotificationEntry> centre = ui.core.getNotifications().history();
        if (centre.isEmpty()) {
            ui.label(canvas, "No notifications yet.", x + half + 18, y + 30);
        }
        int shown = 0;
        for (int i = centre.size() - 1; i >= 0 && shown < 12; i--, shown++) {
            NotificationEntry entry = centre.get(i);
            canvas.drawText(ui.trim(entry.getMessage(), canvas, half - 30, 0.8f), x + half + 18, y + 30 + shown * 12,
                    ui.core.getNotifications().colorFor(entry.getKind()), false, 0.8f);
        }
        ui.button("notif:clear", "CLEAR", x + half + 18, y + 222, 60, 18, ui.error(), true);
        ui.button("notif:colors", "RESET COLOURS", x + half + 84, y + 222, 110, 18, ui.secondary(), true);

        float y2 = y + 258;
        ui.card(canvas, x, y2, w, 120);
        ui.sectionTitle(canvas, "EVENTS", x + 10, y2 + 10);
        ui.paragraph(canvas, "Notifications cover: matching offers found, price changed, not enough data, "
                + "limit reached, automation disabled, operation confirmed or rejected, connection errors. "
                + "Every notification is also kept in the centre so nothing is missed.", x + 10, y2 + 28,
                w - 20, ui.muted());
    }

    // ------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------

    private void settings(UiCanvas canvas, float x, float y, float w) {
        float half = (w - 8) / 2f;
        ui.card(canvas, x, y, half, 168);
        ui.sectionTitle(canvas, "KEY BINDINGS", x + 10, y + 10);
        ui.label(canvas, "Open GUI (Ё / ` / ~)", x + 10, y + 30);
        ui.button("bind:open", ui.awaitingBind().equals("open") ? "PRESS A KEY..."
                : ui.core.getKeyName(config().ui.openKeyCode), x + 150, y + 24, 120, 16, ui.primary(), true);
        ui.label(canvas, "Emergency stop", x + 10, y + 52);
        ui.button("bind:emergency", ui.awaitingBind().equals("emergency") ? "PRESS A KEY..."
                : ui.core.getKeyName(config().ui.emergencyKeyCode), x + 150, y + 46, 120, 16, ui.error(), true);
        ui.toggle(canvas, "tg:closeEscape", config().ui.closeWithEscape, x + 10, y + 74,
                "ESC closes the GUI", true);
        ui.toggle(canvas, "tg:closeOpenKey", config().ui.closeWithOpenKey, x + 10, y + 94,
                "Ё closes the GUI too", true);
        ui.paragraph(canvas, "The key is matched by its physical GLFW code, so Russian and English "
                + "layouts behave identically. The GUI never opens while you are typing in chat.",
                x + 10, y + 116, half - 20, ui.muted());

        ui.card(canvas, x + half + 8, y, half, 168);
        ui.sectionTitle(canvas, "APPEARANCE", x + half + 18, y + 10);
        List<String> themeIds = new ArrayList<String>();
        List<dev.funtime.auctionhelper.config.HelperConfig.ThemeConfig> themes = ui.core.getThemeEngine().all();
        for (int i = 0; i < themes.size(); i++) {
            themeIds.add(themes.get(i).displayName);
        }
        ui.dropdown(canvas, "dd:theme", "Theme", themeIds,
                ui.core.getThemeEngine().active().displayName, x + half + 18, y + 26, half - 30);
        ui.toggle(canvas, "tg:animations", config().ui.animationsEnabled, x + half + 18, y + 66,
                "Animations", true);
        ui.slider(canvas, "ui:animationSpeed", "Animation speed", config().ui.animationSpeed, 0.2d, 2d,
                x + half + 18, y + 86, half - 30, "x");
        ui.slider(canvas, "ui:radius", "Corner radius", config().ui.cornerRadius, 0d, 14d,
                x + half + 18, y + 118, half - 30, "px");
        ui.slider(canvas, "ui:opacity", "Background opacity", config().ui.backgroundOpacity, 0.3d, 1d,
                x + half + 18, y + 146, half - 30, "");

        float y2 = y + 176;
        ui.card(canvas, x, y2, half, 210);
        ui.sectionTitle(canvas, "CONFIG PROFILES", x + 10, y2 + 10);
        ui.numField(canvas, "profile:name", "Profile name", ui.core.getConfigManager().getActiveProfile(),
                x + 10, y2 + 26, 150);
        ui.button("cfg:save", "SAVE", x + 10, y2 + 56, 60, 18, ui.success(), false);
        ui.button("cfg:create", "CREATE", x + 76, y2 + 56, 60, 18, ui.primary(), true);
        ui.button("cfg:load", "LOAD", x + 142, y2 + 56, 56, 18, ui.secondary(), true);
        ui.button("cfg:rename", "RENAME", x + 204, y2 + 56, 60, 18, ui.secondary(), true);
        ui.button("cfg:delete", "DELETE", x + 270, y2 + 56, 60, 18, ui.error(), true);
        ui.button("cfg:export", "EXPORT", x + 10, y2 + 80, 62, 18, ui.accent(), true);
        ui.button("cfg:import", "IMPORT", x + 78, y2 + 80, 62, 18, ui.accent(), true);
        ui.button("cfg:backup", "BACKUP", x + 146, y2 + 80, 62, 18, ui.secondary(), true);
        ui.button("cfg:restore", "RESTORE", x + 214, y2 + 80, 66, 18, ui.warning(), true);
        List<String> profiles = ui.core.getConfigManager().listProfiles();
        ui.label(canvas, "Profiles: " + (profiles.isEmpty() ? "-" : join(profiles)), x + 10, y2 + 108);
        ui.label(canvas, "Backups: " + ui.core.getConfigManager().listBackups().size(), x + 10, y2 + 120);
        ui.paragraph(canvas, "Automation is always saved as OFF and re-disabled on every load, so a "
                + "config can never silently restart trading.", x + 10, y2 + 134, half - 20, ui.muted());
        String error = ui.core.getConfigManager().getLastError();
        if (error != null) {
            canvas.drawText(ui.trim(error, canvas, half - 20, 0.78f), x + 10, y2 + 178, ui.error(), false, 0.78f);
        }

        HelperConfig.AdapterProfileConfig profile = config().findAdapterProfile(config().activeAdapterProfile);
        ui.card(canvas, x + half + 8, y2, half, 210);
        ui.sectionTitle(canvas, "ADAPTER (SERVER GUI)", x + half + 18, y2 + 10);
        if (profile == null) {
            ui.label(canvas, "No adapter profile configured.", x + half + 18, y2 + 30);
        } else {
            ui.value(canvas, profile.displayName, x + half + 18, y2 + 28);
            ui.label(canvas, profile.verified ? "VERIFIED layout" : "UNVERIFIED layout - confirm first",
                    x + half + 18, y2 + 40);
            ui.numField(canvas, "adapter:title", "Screen title regex", profile.screenTitlePattern,
                    x + half + 18, y2 + 52, half - 30);
            ui.numField(canvas, "adapter:price", "Price regex", profile.pricePattern,
                    x + half + 18, y2 + 84, half - 30);
            ui.numField(canvas, "adapter:quantity", "Quantity regex", profile.quantityPattern,
                    x + half + 18, y2 + 116, half - 30);
            ui.label(canvas, "FunTime's auction layout is not published: the default patterns are generic "
                    + "and must be checked against a real screen.", x + half + 18, y2 + 150);
            ui.button("adapter:verify", profile.verified ? "MARK UNVERIFIED" : "MARK VERIFIED",
                    x + half + 18, y2 + 176, 130, 18, ui.accent(), true);
            ui.button("adapter:diag", "SHOW DIAGNOSTICS", x + half + 156, y2 + 176, 130, 18, ui.secondary(), true);
        }

        float y3 = y2 + 218;
        ui.card(canvas, x, y3, w, 120);
        ui.sectionTitle(canvas, "ABOUT & SAFETY", x + 10, y3 + 10);
        ui.paragraph(canvas, "FunTime Auction Helper Ultra Pro " + dev.funtime.auctionhelper.HelperCore.VERSION
                + " for Minecraft " + ui.core.getPlatform().getMinecraftVersion()
                + ". This mod reads screens that the server already shows you and analyses them locally. "
                + "It does not click, does not send extra packets, does not bypass anti-cheat and does not "
                + "invent offers. Automated trading is only possible with a documented server API: on servers "
                + "without one (including FunTime unless FunTime provides one) every trade needs your manual "
                + "confirmation.", x + 10, y3 + 28, w - 20, ui.muted());
    }

    private String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(values.get(i));
        }
        return builder.toString();
    }

    // ------------------------------------------------------------------
    // Interaction
    // ------------------------------------------------------------------

    void onClick(String id) {
        if (id.startsWith("nav:")) {
            HelperUi.Page target = HelperUi.Page.valueOf(id.substring(4));
            ui.setPage(target);
            return;
        }
        if (id.startsWith("tg:")) {
            handleToggle(id.substring(3));
            return;
        }
        if (id.startsWith("dd:")) {
            ui.toggleDropdown(id);
            return;
        }
        if (id.startsWith("listing:")) {
            ui.selectListing(Integer.parseInt(id.substring(8)));
            return;
        }
        if (id.startsWith("bind:")) {
            ui.setAwaitingBind(id.substring(5));
            return;
        }
        if (id.startsWith("trade:confirm:")) {
            String requestId = id.substring("trade:confirm:".length());
            dev.funtime.auctionhelper.engine.TradeResult result = ui.core.getTradeEngine().execute(requestId, true,
                    System.currentTimeMillis());
            ui.setStatus("Operation " + result.getResult().name() + ": " + result.getMessage());
            ui.core.saveAll();
            return;
        }
        if (id.startsWith("trade:reject:")) {
            String requestId = id.substring("trade:reject:".length());
            ui.core.getTradeEngine().reject(requestId, "Rejected in GUI", System.currentTimeMillis());
            ui.setStatus("Operation rejected");
            return;
        }
        if (id.equals("trade:export")) {
            Path target = ui.core.getRootDir().resolve("exports/trade-history.csv");
            boolean ok = ui.core.getTradeHistory().exportCsv(target);
            ui.setStatus(ok ? "Exported to " + target : "Export failed");
            return;
        }
        if (id.equals("trade:clear")) {
            ui.core.getTradeHistory().clear();
            ui.core.saveAll();
            ui.setStatus("Trade history cleared");
            return;
        }
        if (id.startsWith("analysis:")) {
            handleAnalysisAction(id.substring("analysis:".length()));
            return;
        }
        if (id.startsWith("history:")) {
            handleHistoryAction(id.substring("history:".length()));
            return;
        }
        if (id.startsWith("filter:")) {
            handleFilterAction(id.substring("filter:".length()));
            return;
        }
        if (id.startsWith("cfg:")) {
            handleConfigAction(id.substring("cfg:".length()));
            return;
        }
        if (id.startsWith("adapter:")) {
            handleAdapterAction(id.substring("adapter:".length()));
            return;
        }
        if (id.equals("notif:clear")) {
            ui.core.getNotifications().clearHistory();
            return;
        }
        if (id.equals("notif:colors")) {
            resetNotificationColors();
            return;
        }
        if (id.startsWith("fav:") || id.startsWith("white:") || id.startsWith("black:")
                || id.startsWith("buy:allow:") || id.startsWith("buy:block:")
                || id.startsWith("sell:allow:") || id.startsWith("sell:block:")) {
            handleListAction(id);
            return;
        }
        if (id.equals("emergency")) {
            ui.core.emergencyStop("STOP ALL TRADING button");
        }
    }

    private void handleToggle(String key) {
        AutomationState automation = ui.core.getAutomation();
        if (key.equals("master")) {
            boolean target = !automation.isMasterEnabled();
            if (!automation.setMaster(target, "GUI master switch")) {
                ui.notifyError(automation.getLastChangeReason());
            } else {
                ui.setStatus(target ? "Automation armed" : "Automation stopped, queue cleared");
            }
            syncAutomation();
            return;
        }
        if (key.equals("autobuy")) {
            boolean target = !automation.isAutoBuySwitchOn();
            if (!automation.setAutoBuy(target, "GUI auto buy switch")) {
                ui.notifyError(automation.getLastChangeReason());
            }
            syncAutomation();
            return;
        }
        if (key.equals("autosell")) {
            boolean target = !automation.isAutoSellSwitchOn();
            if (!automation.setAutoSell(target, "GUI auto sell switch")) {
                ui.notifyError(automation.getLastChangeReason());
            }
            syncAutomation();
            return;
        }
        if (key.startsWith("strategy:")) {
            String id = key.substring("strategy:".length());
            StrategyConfig strategy = config().findStrategy(id);
            if (strategy != null) {
                strategy.enabled = !strategy.enabled;
                saveConfig();
                ui.setStatus(strategy.displayName + (strategy.enabled ? " enabled" : " disabled"));
            }
            return;
        }
        switch (key) {
            case "buyEnabled":
                config().buy.enabled = !config().buy.enabled;
                break;
            case "buyRecheck":
                config().buy.recheckPriceBeforeBuy = !config().buy.recheckPriceBeforeBuy;
                break;
            case "buyComplete":
                config().buy.requireCompleteData = !config().buy.requireCompleteData;
                break;
            case "sellEnabled":
                config().sell.enabled = !config().sell.enabled;
                break;
            case "sellSkipFavorites":
                config().sell.skipFavorites = !config().sell.skipFavorites;
                break;
            case "notifEnabled":
                config().notifications.enabled = !config().notifications.enabled;
                break;
            case "notifSound":
                config().notifications.sound = !config().notifications.sound;
                break;
            case "notifChat":
                config().notifications.chatMirror = !config().notifications.chatMirror;
                break;
            case "animations":
                config().ui.animationsEnabled = !config().ui.animationsEnabled;
                break;
            case "closeEscape":
                config().ui.closeWithEscape = !config().ui.closeWithEscape;
                break;
            case "closeOpenKey":
                config().ui.closeWithOpenKey = !config().ui.closeWithOpenKey;
                break;
            case "filterEnabled": {
                FilterRule rule = config().findFilter(ui.selectedFilterId());
                if (rule != null) {
                    rule.enabled = !rule.enabled;
                }
                break;
            }
            case "filterFavorites": {
                FilterRule rule = config().findFilter(ui.selectedFilterId());
                if (rule != null) {
                    rule.favoritesOnly = !rule.favoritesOnly;
                }
                break;
            }
            default:
                return;
        }
        saveConfig();
    }

    private void syncAutomation() {
        config().automation.masterEnabled = ui.core.getAutomation().isMasterEnabled();
        config().automation.autoBuyEnabled = ui.core.getAutomation().isAutoBuySwitchOn();
        config().automation.autoSellEnabled = ui.core.getAutomation().isAutoSellSwitchOn();
        saveConfig();
    }

    private void handleAnalysisAction(String action) {
        List<AuctionListing> listings = ui.core.getLastListings();
        if (action.equals("verify")) {
            HelperConfig.AdapterProfileConfig profile =
                    config().findAdapterProfile(config().activeAdapterProfile);
            if (profile != null) {
                profile.verified = !profile.verified;
                ui.core.getAdapters().rebuild();
                saveConfig();
                ui.setStatus(profile.verified ? "Adapter profile marked VERIFIED"
                        : "Adapter profile marked unverified");
            }
            return;
        }
        if (listings.isEmpty()) {
            ui.notifyError("No offer selected");
            return;
        }
        int index = Math.min(ui.selectedListingIndex(), listings.size() - 1);
        AuctionListing listing = listings.get(index);
        if (action.equals("queueBuy")) {
            dev.funtime.auctionhelper.engine.TradeResult result = ui.core.getTradeEngine().requestManualBuy(listing,
                    System.currentTimeMillis());
            ui.setStatus(result.getMessage());
            ui.core.saveAll();
            return;
        }
        if (action.equals("addFavorite")) {
            if (!config().favorites.contains(listing.getItemId())) {
                config().favorites.add(listing.getItemId());
                saveConfig();
                ui.setStatus("Added " + listing.getItemId() + " to favourites");
            }
        }
    }

    private void handleHistoryAction(String action) {
        Path root = ui.core.getRootDir();
        if (action.equals("export")) {
            Path target = root.resolve("exports/price-history.csv");
            boolean ok = ui.core.getPriceHistory().exportCsv(target);
            ui.setStatus(ok ? "Exported to " + target : "Export failed");
            return;
        }
        if (action.equals("import")) {
            Path source = root.resolve("exports/price-history.csv");
            int imported = ui.core.getPriceHistory().importCsv(source);
            ui.setStatus(imported > 0 ? "Imported " + imported + " price point(s)" : "Nothing to import at " + source);
            ui.core.saveAll();
            return;
        }
        if (action.equals("cleanup")) {
            int removed = ui.core.getPriceHistory().cleanup(config().analysis.historyRetentionDays,
                    System.currentTimeMillis());
            ui.core.saveAll();
            ui.setStatus("Removed " + removed + " old price point(s)");
        }
    }

    private void handleFilterAction(String action) {
        if (action.equals("new")) {
            FilterRule rule = new FilterRule("filter-" + System.currentTimeMillis(), "New filter");
            config().filters.add(rule);
            ui.selectFilter(rule.id);
            saveConfig();
            return;
        }
        if (action.equals("delete")) {
            FilterRule rule = config().findFilter(ui.selectedFilterId());
            if (rule != null) {
                config().filters.remove(rule);
                ui.selectFilter(config().filters.isEmpty() ? "" : config().filters.get(0).id);
                saveConfig();
                ui.setStatus("Filter deleted");
            }
            return;
        }
        if (action.equals("duplicate")) {
            FilterRule rule = config().findFilter(ui.selectedFilterId());
            if (rule != null) {
                FilterRule copy = rule.copy();
                copy.id = "filter-" + System.currentTimeMillis();
                copy.name = rule.name + " copy";
                config().filters.add(copy);
                ui.selectFilter(copy.id);
                saveConfig();
            }
            return;
        }
        if (action.equals("toggle")) {
            FilterRule rule = config().findFilter(ui.selectedFilterId());
            if (rule != null) {
                rule.enabled = !rule.enabled;
                saveConfig();
            }
            return;
        }
        if (action.equals("save")) {
            saveConfig();
            ui.setStatus("Filters saved");
            return;
        }
        if (action.equals("templates")) {
            config().filters.addAll(HelperConfig.Defaults.defaultFilters());
            saveConfig();
            ui.setStatus("Filter templates restored");
        }
    }

    private void handleConfigAction(String action) {
        String name = ui.isEditing("profile:name")
                ? ui.editBufferValue() : ui.core.getConfigManager().getActiveProfile();
        if (action.equals("save")) {
            ui.core.getConfigManager().save();
            ui.setStatus("Configuration saved");
            return;
        }
        if (action.equals("create") || action.equals("rename")) {
            if (action.equals("create")) {
                ui.core.getConfigManager().saveAs(name);
            } else {
                ui.core.getConfigManager().renameProfile(ui.core.getConfigManager().getActiveProfile(), name);
            }
            ui.setStatus("Profile " + name + " stored");
            return;
        }
        if (action.equals("load")) {
            boolean ok = ui.core.getConfigManager().loadProfile(name);
            if (ok) {
                ui.setStatus("Profile " + name + " loaded (automation stays OFF)");
            } else {
                ui.notifyError(ui.core.getConfigManager().getLastError());
            }
            return;
        }
        if (action.equals("delete")) {
            ui.core.getConfigManager().deleteProfile(name);
            ui.setStatus("Profile " + name + " deleted");
            return;
        }
        if (action.equals("export")) {
            Path target = ui.core.getRootDir().resolve("exports/config-" + name + ".json");
            ui.setStatus(ui.core.getConfigManager().exportTo(target) ? "Exported to " + target : "Export failed");
            return;
        }
        if (action.equals("import")) {
            Path source = ui.core.getRootDir().resolve("exports/config-" + name + ".json");
            ui.setStatus(ui.core.getConfigManager().importFrom(source)
                    ? "Imported from " + source : "Import failed: " + ui.core.getConfigManager().getLastError());
            return;
        }
        if (action.equals("backup")) {
            ui.core.getConfigManager().backupNow();
            ui.setStatus("Backup created");
            return;
        }
        if (action.equals("restore")) {
            List<Path> backups = ui.core.getConfigManager().listBackups();
            if (backups.isEmpty()) {
                ui.notifyError("No backup available yet");
            } else {
                Path latest = backups.get(backups.size() - 1);
                ui.core.getConfigManager().restore(latest);
                ui.setStatus("Restored " + latest.getFileName());
            }
        }
    }

    private void handleAdapterAction(String action) {
        HelperConfig.AdapterProfileConfig profile = config().findAdapterProfile(config().activeAdapterProfile);
        if (profile == null) {
            return;
        }
        if (action.equals("verify")) {
            profile.verified = !profile.verified;
            ui.core.getAdapters().rebuild();
            saveConfig();
            ui.setStatus(profile.verified ? "Marked verified: offers may feed the price history"
                    : "Marked unverified: offers are shown but not recorded");
            return;
        }
        if (action.equals("diag")) {
            List<String> lines = ui.core.getAdapters().getDiagnostics().recent();
            if (lines.isEmpty()) {
                ui.setStatus("No adapter events yet");
            } else {
                ui.setStatus(lines.get(lines.size() - 1));
            }
        }
    }

    private void handleListAction(String id) {
        String prefix;
        List<String> target;
        if (id.startsWith("fav:")) {
            prefix = "fav";
            target = config().favorites;
        } else if (id.startsWith("white:")) {
            prefix = "white";
            target = config().whitelist;
        } else if (id.startsWith("black:")) {
            prefix = "black";
            target = config().blacklist;
        } else if (id.startsWith("buy:allow:")) {
            prefix = "buy:allow";
            target = config().buy.allowedItems;
        } else if (id.startsWith("buy:block:")) {
            prefix = "buy:block";
            target = config().buy.blockedItems;
        } else if (id.startsWith("sell:allow:")) {
            prefix = "sell:allow";
            target = config().sell.allowedItems;
        } else {
            prefix = "sell:block";
            target = config().sell.blockedItems;
        }
        String rest = id.substring(prefix.length() + 1);
        if (rest.startsWith("remove:")) {
            int index = Integer.parseInt(rest.substring(7));
            if (index >= 0 && index < target.size()) {
                target.remove(index);
                saveConfig();
            }
            return;
        }
        if (rest.equals("add")) {
            String value = ui.editBufferValue();
            commitField(prefix + ":new", value);
        }
    }

    private void resetNotificationColors() {
        config().notifications.infoColor = 0xFF5274FF;
        config().notifications.successColor = 0xFF39DD9C;
        config().notifications.warningColor = 0xFFFFC266;
        config().notifications.errorColor = 0xFFFF5B78;
        saveConfig();
    }

    private void saveConfig() {
        ui.core.getConfigManager().save();
    }

    // ------------------------------------------------------------------
    // Sliders, dropdowns and text fields
    // ------------------------------------------------------------------

    void onSlider(String id, double t) {
        if (id.startsWith("strategy:")) {
            String rest = id.substring("strategy:".length());
            int split = rest.lastIndexOf(':');
            String strategyId = rest.substring(0, split);
            StrategyConfig strategy = config().findStrategy(strategyId);
            if (strategy == null) {
                return;
            }
            boolean lowPrice = strategy.getType() == StrategyConfig.Type.LOW_PRICE_ALERT;
            strategy.threshold = lowPrice ? round(t * 20000d) : round(t * 2d);
            saveConfig();
            return;
        }
        if (id.startsWith("filter:")) {
            FilterRule rule = config().findFilter(ui.selectedFilterId());
            if (rule == null) {
                return;
            }
            String field = id.substring("filter:".length());
            switch (field) {
                case "maxPrice":
                    rule.maxPrice = round(t * 100000d);
                    break;
                case "maxUnitPrice":
                    rule.maxUnitPrice = round(t * 10000d);
                    break;
                case "minStack":
                    rule.minStackSize = (int) Math.round(t * 64d);
                    break;
                case "maxStack":
                    rule.maxStackSize = (int) Math.round(t * 64d);
                    break;
                default:
                    return;
            }
            saveConfig();
            return;
        }
        if (id.startsWith("buy:")) {
            String field = id.substring(4);
            switch (field) {
                case "maxPurchasePrice":
                    config().buy.maxPurchasePrice = round(t * 100000d);
                    break;
                case "minExpectedMargin":
                    config().buy.minExpectedMargin = round(t);
                    break;
                case "dailySpendingLimit":
                    config().buy.dailySpendingLimit = round(t * 500000d);
                    break;
                case "minimumBalance":
                    config().buy.minimumBalance = round(t * 100000d);
                    break;
                default:
                    return;
            }
            saveConfig();
            return;
        }
        if (id.startsWith("sell:")) {
            String field = id.substring(5);
            switch (field) {
                case "minSellingPrice":
                    config().sell.minSellingPrice = round(t * 10000d);
                    break;
                case "maxSellingPrice":
                    config().sell.maxSellingPrice = round(t * 1000000d);
                    break;
                case "targetProfitMargin":
                    config().sell.targetProfitMargin = round(t);
                    break;
                case "priceAdjustment":
                    config().sell.priceAdjustment = round(t - 0.5d);
                    break;
                default:
                    return;
            }
            saveConfig();
            return;
        }
        if (id.startsWith("fees:")) {
            config().fees.percent = round(t * 30d) / 100d;
            saveConfig();
            return;
        }
        if (id.startsWith("notif:")) {
            String field = id.substring(6);
            switch (field) {
                case "duration":
                    config().notifications.durationMs = (int) Math.round(1000d + t * 14000d);
                    break;
                case "opacity":
                    config().notifications.opacity = (float) Numbers.clamp(0.2d + t * 0.8d, 0.2d, 1d);
                    break;
                case "volume":
                    config().notifications.volume = (float) Numbers.clamp(t, 0d, 1d);
                    break;
                default:
                    return;
            }
            saveConfig();
            return;
        }
        if (id.startsWith("ui:")) {
            String field = id.substring(3);
            if (field.equals("animationSpeed")) {
                config().ui.animationSpeed = (float) (0.2d + t * 1.8d);
            } else if (field.equals("radius")) {
                config().ui.cornerRadius = (int) Math.round(t * 14d);
            } else if (field.equals("opacity")) {
                config().ui.backgroundOpacity = (float) (0.3d + t * 0.7d);
            }
            saveConfig();
            return;
        }
        if (id.startsWith("analysis:")) {
            String field = id.substring("analysis:".length());
            if (field.equals("window")) {
                int minutes = (int) Math.round(60d + t * 20100d);
                config().analysis.observationWindowMinutes = Math.max(60, minutes);
                saveConfig();
            }
        }
    }

    private static double round(double value) {
        return Math.round(value * 100d) / 100d;
    }

    void onDropdown(String id, int index) {
        if (id.equals("dd:sellStrategy")) {
            String[] options = {"MANUAL", "FIXED", "UNDER_CUT", "MARKET_AVERAGE"};
            if (index >= 0 && index < options.length) {
                config().sell.sellingStrategy = options[index];
                saveConfig();
            }
            return;
        }
        if (id.equals("dd:notifPosition")) {
            String[] options = {"TOP_RIGHT", "TOP_LEFT", "BOTTOM_RIGHT", "BOTTOM_LEFT"};
            if (index >= 0 && index < options.length) {
                config().notifications.position = options[index];
                saveConfig();
            }
            return;
        }
        if (id.equals("dd:theme")) {
            List<dev.funtime.auctionhelper.config.HelperConfig.ThemeConfig> themes = ui.core.getThemeEngine().all();
            if (index >= 0 && index < themes.size()) {
                ui.core.getThemeEngine().setActive(themes.get(index).id);
                config().activeTheme = themes.get(index).id;
                saveConfig();
            }
            return;
        }
        if (id.equals("dd:historyItem")) {
            List<String> items = ui.core.getPriceHistory().knownItems();
            if (index >= 0 && index < items.size()) {
                ui.selectHistoryItem(items.get(index));
            }
        }
    }

    String fieldValue(String id) {
        HelperConfig.BuySettings buy = config().buy;
        HelperConfig.SellSettings sell = config().sell;
        if (id.equals("buy:maxPurchases")) {
            return String.valueOf(buy.maxPurchasesPerSession);
        }
        if (id.equals("buy:allow:new") || id.equals("buy:block:new") || id.equals("sell:allow:new")
                || id.equals("sell:block:new") || id.equals("fav:new") || id.equals("white:new")
                || id.equals("black:new")) {
            return ui.editValue(id);
        }
        if (id.equals("sell:maxListings")) {
            return String.valueOf(sell.maxActiveListings);
        }
        if (id.equals("fees:flat")) {
            return String.valueOf(config().fees.flat);
        }
        if (id.equals("analysis:samples")) {
            return String.valueOf(config().analysis.minimumSampleCount);
        }
        if (id.equals("notif:maxVisible")) {
            return String.valueOf(config().notifications.maxVisible);
        }
        if (id.equals("profile:name")) {
            return ui.core.getConfigManager().getActiveProfile();
        }
        if (id.equals("filter:name")) {
            FilterRule rule = config().findFilter(ui.selectedFilterId());
            return rule == null ? "" : rule.name;
        }
        if (id.equals("filter:nameContains")) {
            FilterRule rule = config().findFilter(ui.selectedFilterId());
            return rule == null ? "" : rule.nameContains;
        }
        if (id.equals("filter:itemId")) {
            FilterRule rule = config().findFilter(ui.selectedFilterId());
            return rule == null ? "" : rule.itemIdContains;
        }
        if (id.startsWith("strategy:")) {
            StrategyConfig strategy = config().findStrategy(id.substring("strategy:".length(),
                    id.length() - ":samples".length()));
            return strategy == null ? "0" : String.valueOf(strategy.minimumSamples);
        }
        HelperConfig.AdapterProfileConfig profile = config().findAdapterProfile(config().activeAdapterProfile);
        if (id.equals("adapter:title")) {
            return profile == null ? "" : profile.screenTitlePattern;
        }
        if (id.equals("adapter:price")) {
            return profile == null ? "" : profile.pricePattern;
        }
        if (id.equals("adapter:quantity")) {
            return profile == null ? "" : profile.quantityPattern;
        }
        return "";
    }

    void commitField(String id, String buffer) {
        String value = buffer == null ? "" : buffer.trim();
        HelperConfig.BuySettings buy = config().buy;
        HelperConfig.SellSettings sell = config().sell;
        if (id.equals("buy:maxPurchases")) {
            buy.maxPurchasesPerSession = Math.max(0, parseInt(value, buy.maxPurchasesPerSession));
        } else if (id.equals("sell:maxListings")) {
            sell.maxActiveListings = Math.max(0, parseInt(value, sell.maxActiveListings));
        } else if (id.equals("fees:flat")) {
            config().fees.flat = Math.max(0d, parseDouble(value, config().fees.flat));
        } else if (id.equals("analysis:samples")) {
            config().analysis.minimumSampleCount = Math.max(1, parseInt(value, config().analysis.minimumSampleCount));
        } else if (id.equals("notif:maxVisible")) {
            config().notifications.maxVisible = Math.max(1, parseInt(value, config().notifications.maxVisible));
        } else if (id.equals("profile:name")) {
            ui.core.getConfigManager().saveAs(value);
        } else if (id.equals("filter:name")) {
            FilterRule rule = config().findFilter(ui.selectedFilterId());
            if (rule != null) {
                rule.name = value.isEmpty() ? rule.name : value;
            }
        } else if (id.equals("filter:nameContains")) {
            FilterRule rule = config().findFilter(ui.selectedFilterId());
            if (rule != null) {
                rule.nameContains = value;
            }
        } else if (id.equals("filter:itemId")) {
            FilterRule rule = config().findFilter(ui.selectedFilterId());
            if (rule != null) {
                rule.itemIdContains = value;
            }
        } else if (id.startsWith("strategy:")) {
            StrategyConfig strategy = config().findStrategy(id.substring("strategy:".length(),
                    id.length() - ":samples".length()));
            if (strategy != null) {
                strategy.minimumSamples = Math.max(1, parseInt(value, strategy.minimumSamples));
            }
        } else if (id.startsWith("adapter:")) {
            HelperConfig.AdapterProfileConfig profile = config().findAdapterProfile(config().activeAdapterProfile);
            if (profile != null) {
                if (id.equals("adapter:title")) {
                    profile.screenTitlePattern = value;
                } else if (id.equals("adapter:price")) {
                    profile.pricePattern = value;
                } else if (id.equals("adapter:quantity")) {
                    profile.quantityPattern = value;
                }
                ui.core.getAdapters().rebuild();
            }
        } else if (id.endsWith(":new")) {
            String prefix = id.substring(0, id.length() - 4);
            List<String> target = null;
            if (prefix.equals("buy:allow")) {
                target = buy.allowedItems;
            } else if (prefix.equals("buy:block")) {
                target = buy.blockedItems;
            } else if (prefix.equals("sell:allow")) {
                target = sell.allowedItems;
            } else if (prefix.equals("sell:block")) {
                target = sell.blockedItems;
            } else if (prefix.equals("fav")) {
                target = config().favorites;
            } else if (prefix.equals("white")) {
                target = config().whitelist;
            } else if (prefix.equals("black")) {
                target = config().blacklist;
            }
            if (target != null && !value.isEmpty()) {
                target.add(value.toLowerCase(java.util.Locale.ROOT));
                ui.setStatus("Added " + value);
            }
        }
        saveConfig();
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException error) {
            double parsed = Numbers.parseAmount(value);
            return Double.isNaN(parsed) ? fallback : (int) parsed;
        }
    }

    private static double parseDouble(String value, double fallback) {
        double parsed = Numbers.parseAmount(value);
        return Double.isNaN(parsed) ? fallback : parsed;
    }

    String describeKey(int keyCode) {
        return KeyCodes.name(keyCode);
    }
}
