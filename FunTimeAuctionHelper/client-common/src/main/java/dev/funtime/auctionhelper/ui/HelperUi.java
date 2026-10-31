package dev.funtime.auctionhelper.ui;

import dev.funtime.auctionhelper.HelperCore;
import dev.funtime.auctionhelper.config.HelperConfig;
import dev.funtime.auctionhelper.engine.AutomationState;
import dev.funtime.auctionhelper.model.FilterRule;
import dev.funtime.auctionhelper.model.NotificationEntry;
import dev.funtime.auctionhelper.model.StrategyConfig;
import dev.funtime.auctionhelper.util.Colors;
import dev.funtime.auctionhelper.util.KeyCodes;
import dev.funtime.auctionhelper.util.Numbers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immediate mode GUI controller for the whole mod.
 *
 * <p>The layout, the animations and the interaction logic live here (one implementation for all
 * Minecraft versions); only the drawing calls go through {@link UiCanvas}.</p>
 */
public final class HelperUi {
    public enum Page {
        DASHBOARD("DASHBOARD", "Dashboard"),
        ANALYSIS("AUCTION ANALYSIS", "Auction analysis"),
        BUY("BUY SETTINGS", "Buy settings"),
        SELL("SELL SETTINGS", "Sell settings"),
        STRATEGIES("STRATEGIES", "Strategies"),
        HISTORY("PRICE HISTORY", "Price history"),
        FILTERS("FILTERS", "Filters"),
        FAVORITES("FAVORITES", "Favourites"),
        TRADES("TRADE HISTORY", "Trade history"),
        NOTIFICATIONS("NOTIFICATIONS", "Notifications"),
        SETTINGS("SETTINGS", "Settings");

        public final String title;
        public final String subtitle;

        Page(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    static final float SIDEBAR_WIDTH = 152f;
    static final float HEADER_HEIGHT = 46f;
    static final float FOOTER_HEIGHT = 18f;
    static final float PAD = 10f;

    final HelperCore core;
    HelperConfig config;
    final UiPages pages;

    private final Map<String, float[]> hitboxes = new HashMap<String, float[]>();
    private final Map<Page, Float> scroll = new HashMap<Page, Float>();
    private final Map<String, Float> animations = new HashMap<String, Float>();

    private Page page = Page.DASHBOARD;
    private String hovered = "";
    private String tooltip = "";
    private String editingField = "";
    private String editBuffer = "";
    private String awaitingBind = "";
    private String draggingSlider = "";
    private String openDropdown = "";
    private float dropX;
    private float dropY;
    private float dropW;
    private String[] dropOptions = new String[0];
    private float timeSeconds;
    private float openAnimation;
    private String[] quickActions = new String[0];
    private int selectedListingIndex = 0;
    private String selectedFilterId = "";
    private String selectedHistoryItem = "";
    private String statusLine = "";
    private long statusUntil;
    private int mouseX;
    private int mouseY;

    public HelperUi(HelperCore core) {
        this.core = core;
        this.config = core.getConfig();
        this.pages = new UiPages(this);
        this.page = Page.values()[Numbers.clampInt(config.ui.lastPage, 0, Page.values().length - 1)];
        if (!config.filters.isEmpty()) {
            selectedFilterId = config.filters.get(0).id;
        }
    }

    // ------------------------------------------------------------------
    // State helpers used by the pages
    // ------------------------------------------------------------------

    public Page getPage() {
        return page;
    }

    public void setPage(Page newPage) {
        if (newPage == null || newPage == page) {
            return;
        }
        page = newPage;
        config.ui.lastPage = newPage.ordinal();
        openDropdown = "";
        editingField = "";
    }

    HelperConfig.ThemeConfig theme() {
        return core.getThemeEngine().active();
    }

    int c(int color) {
        return color;
    }

    int background() {
        return Colors.withAlphaFactor(theme().background, theme().opacity);
    }

    int surface() {
        return Colors.withAlphaFactor(theme().surface, Math.min(1f, theme().opacity + 0.04f));
    }

    int elevated() {
        return Colors.withAlphaFactor(theme().elevated, Math.min(1f, theme().opacity + 0.06f));
    }

    int primary() {
        return theme().primary;
    }

    int secondary() {
        return theme().secondary;
    }

    int accent() {
        return theme().accent;
    }

    int text() {
        return theme().text;
    }

    int muted() {
        return theme().textMuted;
    }

    int success() {
        return theme().success;
    }

    int warning() {
        return theme().warning;
    }

    int error() {
        return theme().error;
    }

    float scrollY() {
        Float value = scroll.get(page);
        return value == null ? 0f : value.floatValue();
    }

    void addScroll(float delta, float contentHeight, float viewHeight) {
        float max = Math.max(0f, contentHeight - viewHeight);
        float value = Numbers.clamp(scrollY() + delta, -max, 0f);
        scroll.put(page, Float.valueOf(value));
    }

    void setScroll(float value) {
        scroll.put(page, Float.valueOf(value));
    }

    void setStatus(String text) {
        statusLine = text;
        statusUntil = System.currentTimeMillis() + 4000L;
    }

    String statusLine() {
        return System.currentTimeMillis() > statusUntil ? "" : statusLine;
    }

    int selectedListingIndex() {
        return selectedListingIndex;
    }

    void selectListing(int index) {
        selectedListingIndex = index;
    }

    String selectedFilterId() {
        return selectedFilterId;
    }

    void selectFilter(String id) {
        selectedFilterId = id;
    }

    String selectedHistoryItem() {
        return selectedHistoryItem;
    }

    void selectHistoryItem(String itemId) {
        selectedHistoryItem = itemId;
    }

    boolean isEditing(String id) {
        return editingField.equals(id);
    }

    String editValue(String id) {
        return editingField.equals(id) ? editBuffer : "";
    }

    String awaitingBind() {
        return awaitingBind;
    }

    void setAwaitingBind(String id) {
        awaitingBind = id;
    }

    // ------------------------------------------------------------------
    // Frame
    // ------------------------------------------------------------------

    /** Called by the Minecraft screen when it is opened or resized. */
    public void onUiOpened() {
        openAnimation = 0f;
        core.getNotifications().markAllRead();
    }

    public void tick(long now) {
        timeSeconds = (now % 100000L) / 1000f;
        // The config object is replaced when a profile is loaded or imported.
        config = core.getConfig();
        core.getNotifications().tick(now);
    }

    public void render(UiCanvas canvas, int mx, int my, float dt) {
        this.mouseX = mx;
        this.mouseY = my;
        hitboxes.clear();
        tooltip = "";
        float speed = config.ui.animationsEnabled ? Math.max(0.15f, config.ui.animationSpeed) : 40f;
        openAnimation = approach(openAnimation, 1f, dt * speed * 6f);
        float width = canvas.getScaledWidth();
        float height = canvas.getScaledHeight();

        // Backdrop
        canvas.fillGradientVertical(0, 0, width, height,
                Colors.withAlpha(background(), 0xF2), Colors.withAlpha(Colors.multiply(background(), 0.7f), 0xF7));
        canvas.fillRect(0, 0, width, height, Colors.withAlpha(0xFF000000, 0x30));

        drawSidebar(canvas, height);
        drawHeader(canvas, width, height);

        float contentX = SIDEBAR_WIDTH + PAD;
        float contentY = HEADER_HEIGHT + PAD;
        float contentW = width - contentX - PAD;
        float contentH = height - contentY - FOOTER_HEIGHT;

        canvas.pushScissor(Math.round(contentX - 2), Math.round(contentY - 2),
                Math.round(contentW + 4), Math.round(contentH + 4));
        pages.render(canvas, page, contentX, contentY + scrollY(), contentW, contentH, mx, my, dt);
        canvas.popScissor();

        drawScrollbar(canvas, contentX + contentW + 2, contentY, contentH, pages.contentHeight(page));
        drawFooter(canvas, width, height);
        drawNotifications(canvas, width, height);

        if (openDropdown != null && !openDropdown.isEmpty()) {
            drawDropdown(canvas);
        }
        if (config.ui.showTooltips && tooltip != null && !tooltip.isEmpty()) {
            drawTooltip(canvas, width, height);
        }
    }

    private void drawSidebar(UiCanvas canvas, float height) {
        canvas.fillRect(0, 0, SIDEBAR_WIDTH, height, Colors.withAlpha(surface(), 0xFF));
        canvas.fillRect(SIDEBAR_WIDTH - 1, 0, 1, height, Colors.withAlpha(primary(), 0x66));

        canvas.fillRoundedRect(10, 10, 30, 30, 8, primary());
        canvas.drawText("FA", 16.5f, 20f, Colors.WHITE, true, 1.15f);
        canvas.drawText("FUNTIME", 47, 12, text(), true, 1f);
        canvas.drawText("AUCTION HELPER", 47, 23, muted(), false, 0.85f);

        float y = 52f;
        Page[] pages1 = Page.values();
        for (int i = 0; i < pages1.length; i++) {
            Page entry = pages1[i];
            if (y > height - 30f) {
                break;
            }
            boolean active = entry == page;
            float[] rect = new float[]{8, y, SIDEBAR_WIDTH - 16, 21};
            register("nav:" + entry.name(), rect);
            boolean hover = contains(rect, mouseX, mouseY);
            if (hover && (tooltip == null || tooltip.isEmpty())) {
                tooltip = entry.subtitle;
            }
            if (active) {
                canvas.fillRoundedRect(8, y, SIDEBAR_WIDTH - 16, 21, 6,
                        Colors.withAlpha(primary(), 0x44));
                canvas.fillRoundedRect(8, y, 3, 21, 2, accent());
            } else if (hover) {
                canvas.fillRoundedRect(8, y, SIDEBAR_WIDTH - 16, 21, 6, Colors.withAlpha(elevated(), 0xAA));
            }
            canvas.drawText(entry.title, 20, y + 7, active ? text() : muted(), false, 0.92f);
            y += 23f;
        }
        canvas.drawText("v" + HelperCore.VERSION, 12, height - 14, muted(), false, 0.8f);
    }

    private void drawHeader(UiCanvas canvas, float width, float height) {
        canvas.fillRect(SIDEBAR_WIDTH, 0, width - SIDEBAR_WIDTH, HEADER_HEIGHT,
                Colors.withAlpha(surface(), 0xFF));
        canvas.fillRect(SIDEBAR_WIDTH, HEADER_HEIGHT - 1, width - SIDEBAR_WIDTH, 1,
                Colors.withAlpha(primary(), 0x55));
        canvas.drawText(page.title, SIDEBAR_WIDTH + PAD, 12, text(), true, 1.15f);
        canvas.drawText(page.subtitle, SIDEBAR_WIDTH + PAD, 27, muted(), false, 0.85f);

        AutomationState automation = core.getAutomation();
        float right = width - PAD;
        String stopLabel = "STOP ALL TRADING";
        float stopW = 118f;
        right -= stopW;
        button("emergency", stopLabel, right, 11, stopW, 24, error(), true);
        right -= 96f;
        String modeLabel = automation.getMode() == AutomationState.Mode.ANALYSIS_ONLY ? "ANALYSIS"
                : automation.getMode() == AutomationState.Mode.MANUAL_ASSISTANT ? "ASSISTANT" : "AUTOMATED";
        chip("modecycle", modeLabel, right, 11, 86, 24,
                automation.getMode() == AutomationState.Mode.AUTHORIZED_AUTOMATION ? success() : secondary());
        right -= 92f;
        statusDot(canvas, right, 23, "MASTER", automation.isMasterEnabled(), 66);
        right -= 72f;
        statusDot(canvas, right, 23, "BUY", automation.isAutoBuySwitchOn() && automation.isMasterEnabled(), 40);
        right -= 46f;
        statusDot(canvas, right, 23, "SELL", automation.isAutoSellSwitchOn() && automation.isMasterEnabled(), 46);
    }

    private void statusDot(UiCanvas canvas, float x, float y, String caption, boolean on, float width) {
        int color = on ? success() : Colors.withAlpha(muted(), 0xAA);
        canvas.fillRoundedRect(x, y - 6, 7, 7, 3, color);
        canvas.drawText(caption, x + 11, y - 6, on ? text() : muted(), false, 0.8f);
    }

    private void drawFooter(UiCanvas canvas, float width, float height) {
        canvas.fillRect(SIDEBAR_WIDTH, height - FOOTER_HEIGHT, width - SIDEBAR_WIDTH, FOOTER_HEIGHT,
                Colors.withAlpha(surface(), 0xEE));
        String left = core.getPlatform().getMinecraftVersion() + "  |  Java "
                + core.getPlatform().getJavaVersion() + "  |  Loader " + core.getPlatform().getLoaderVersion();
        canvas.drawText(left, SIDEBAR_WIDTH + PAD, height - 12, muted(), false, 0.78f);
        String automationText = core.getAutomation().isAutomationAllowed()
                ? "AUTOMATION API: AVAILABLE" : "AUTOMATION API: UNAVAILABLE - manual confirmation only";
        float txtWidth = canvas.getTextWidth(automationText, 0.78f);
        canvas.drawText(automationText, width - PAD - txtWidth, height - 12,
                core.getAutomation().isAutomationAllowed() ? success() : warning(), false, 0.78f);
        String status = statusLine();
        if (!status.isEmpty()) {
            canvas.drawText(status, SIDEBAR_WIDTH + PAD, height - FOOTER_HEIGHT - 12, accent(), true, 0.85f);
        }
    }

    private void drawScrollbar(UiCanvas canvas, float x, float top, float viewHeight, float contentHeight) {
        if (contentHeight <= viewHeight) {
            return;
        }
        float ratio = viewHeight / contentHeight;
        float barHeight = Math.max(24f, viewHeight * ratio);
        float max = contentHeight - viewHeight;
        float t = max <= 0f ? 0f : (-scrollY()) / max;
        float y = top + t * (viewHeight - barHeight);
        canvas.fillRoundedRect(x, top, 3, viewHeight, 2, Colors.withAlpha(elevated(), 0x88));
        canvas.fillRoundedRect(x, y, 3, barHeight, 2, Colors.withAlpha(primary(), 0xCC));
    }

    private void drawNotifications(UiCanvas canvas, float width, float height) {
        List<NotificationEntry> active = core.getNotifications().active();
        if (active.isEmpty()) {
            return;
        }
        HelperConfig.NotificationSettings settings = config.notifications;
        float boxW = 200f;
        float boxH = 24f;
        boolean rightSide = !"TOP_LEFT".equals(settings.position) && !"BOTTOM_LEFT".equals(settings.position);
        boolean bottom = settings.position != null && settings.position.startsWith("BOTTOM");
        float x = rightSide ? width - boxW - PAD : SIDEBAR_WIDTH + PAD;
        for (int i = 0; i < active.size(); i++) {
            NotificationEntry entry = active.get(i);
            float y = bottom ? height - FOOTER_HEIGHT - (i + 1) * (boxH + 4) : HEADER_HEIGHT + PAD + i * (boxH + 4);
            int color = core.getNotifications().colorFor(entry.getKind());
            canvas.fillRoundedRect(x, y, boxW, boxH, 6,
                    Colors.withAlphaFactor(Colors.blend(elevated(), color, 0.25f), settings.opacity));
            canvas.fillRoundedRect(x, y, 3, boxH, 2, color);
            canvas.drawText(trim(entry.getMessage(), canvas, boxW - 18, 0.82f), x + 9, y + 8, text(), false, 0.82f);
        }
    }

    String trim(String text, UiCanvas canvas, float maxWidth, float scale) {
        if (text == null) {
            return "";
        }
        if (canvas.getTextWidth(text, scale) <= maxWidth) {
            return text;
        }
        String result = text;
        while (result.length() > 4 && canvas.getTextWidth(result + "...", scale) > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }

    private void drawTooltip(UiCanvas canvas, float width, float height) {
        float textW = canvas.getTextWidth(tooltip, 0.82f);
        float boxW = Math.min(width - 20, textW + 12);
        float x = Math.min(mouseX + 10, width - boxW - 6);
        float y = Math.min(mouseY + 12, height - 22);
        canvas.fillRoundedRect(x, y, boxW, 18, 5, Colors.withAlpha(0xFF0A0C14, 0xF2));
        canvas.drawRoundedOutline(x, y, boxW, 18, 5, 1, Colors.withAlpha(primary(), 0xAA));
        canvas.drawText(trim(tooltip, canvas, boxW - 10, 0.82f), x + 6, y + 6, text(), false, 0.82f);
    }

    private void drawDropdown(UiCanvas canvas) {
        float rowH = 16f;
        float h = dropOptions.length * rowH + 4f;
        canvas.fillRoundedRect(dropX, dropY, dropW, h, 6, Colors.withAlpha(0xFF0A0C14, 0xF7));
        canvas.drawRoundedOutline(dropX, dropY, dropW, h, 6, 1, Colors.withAlpha(primary(), 0xAA));
        for (int i = 0; i < dropOptions.length; i++) {
            float y = dropY + 2 + i * rowH;
            float[] rect = new float[]{dropX, y, dropW, rowH};
            register(openDropdown + "|" + i, rect);
            boolean hover = contains(rect, mouseX, mouseY);
            if (hover) {
                canvas.fillRoundedRect(dropX + 2, y, dropW - 4, rowH, 4, Colors.withAlpha(primary(), 0x44));
            }
            canvas.drawText(trim(dropOptions[i], canvas, dropW - 10, 0.85f), dropX + 6, y + 5,
                    hover ? text() : muted(), false, 0.85f);
        }
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    public boolean mouseClicked(int mx, int my, int button) {
        if (button != 0 && button != 1) {
            return false;
        }
        if (openDropdown != null && !openDropdown.isEmpty()) {
            String hit = hitAt(mx, my, openDropdown);
            if (hit != null) {
                int index = Integer.parseInt(hit.substring(hit.indexOf('|') + 1));
                handleDropdownChoice(openDropdown, index);
                openDropdown = "";
                return true;
            }
            openDropdown = "";
        }
        String hit = hitAt(mx, my, null);
        if (hit == null) {
            editingField = "";
            return false;
        }
        if (hit.startsWith("slider:")) {
            draggingSlider = hit;
            updateSlider(hit, mx);
            return true;
        }
        if (hit.equals("emergency")) {
            int cancelled = core.emergencyStop("STOP ALL TRADING button");
            setStatus("Emergency stop executed, " + cancelled + " operation(s) cancelled");
            return true;
        }
        if (hit.startsWith("field:")) {
            String id = hit.substring("field:".length());
            editingField = id;
            editBuffer = pages.fieldValue(id);
            return true;
        }
        pages.onClick(hit);
        return true;
    }

    public boolean mouseDragged(int mx, int my, int button) {
        if (draggingSlider != null && !draggingSlider.isEmpty()) {
            updateSlider(draggingSlider, mx);
            return true;
        }
        return false;
    }

    public boolean mouseReleased(int mx, int my, int button) {
        boolean was = draggingSlider != null && !draggingSlider.isEmpty();
        draggingSlider = "";
        return was;
    }

    public boolean mouseScrolled(float delta) {
        addScroll((float) delta * 14f, pages.contentHeight(page), 200f);
        return true;
    }

    private void updateSlider(String id, int mx) {
        float[] rect = hitboxes.get(id);
        if (rect == null) {
            return;
        }
        float t = Numbers.clamp((mx - rect[0]) / Math.max(1f, rect[2]), 0d, 1d);
        pages.onSlider(id, t);
    }

    public boolean keyPressed(int keyCode) {
        if (!awaitingBind.isEmpty()) {
            if (keyCode != KeyCodes.KEY_ESCAPE) {
                if ("open".equals(awaitingBind)) {
                    config.ui.openKeyCode = keyCode;
                } else if ("emergency".equals(awaitingBind)) {
                    config.ui.emergencyKeyCode = keyCode;
                }
                core.getConfigManager().save();
                setStatus("Bound to " + KeyCodes.name(keyCode));
            }
            awaitingBind = "";
            return true;
        }
        if (!editingField.isEmpty()) {
            if (keyCode == KeyCodes.KEY_BACKSPACE) {
                if (!editBuffer.isEmpty()) {
                    editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
                }
                return true;
            }
            if (keyCode == KeyCodes.KEY_ENTER) {
                pages.commitField(editingField, editBuffer);
                editingField = "";
                return true;
            }
            if (keyCode == KeyCodes.KEY_ESCAPE) {
                editingField = "";
                return true;
            }
            return false;
        }
        if (keyCode == config.ui.emergencyKeyCode) {
            int cancelled = core.emergencyStop("emergency hotkey");
            setStatus("EMERGENCY STOP - " + cancelled + " operation(s) cancelled");
            return true;
        }
        if (keyCode == config.ui.openKeyCode && config.ui.closeWithOpenKey) {
            close();
            return true;
        }
        if (keyCode == KeyCodes.KEY_ESCAPE) {
            if (config.ui.closeWithEscape) {
                close();
                return true;
            }
            return false;
        }
        if (keyCode >= KeyCodes.KEY_F1 && keyCode <= KeyCodes.KEY_F10) {
            return false;
        }
        return false;
    }

    public boolean charTyped(char c) {
        if (editingField.isEmpty()) {
            return false;
        }
        if (c >= 32 && c != 127) {
            if (editBuffer.length() < 96) {
                editBuffer = editBuffer + c;
            }
            return true;
        }
        return false;
    }

    private void close() {
        core.saveAll();
        core.getPlatform().closeScreen();
    }

    // ------------------------------------------------------------------
    // Widget primitives (also used by UiPages)
    // ------------------------------------------------------------------

    void register(String id, float[] rect) {
        hitboxes.put(id, rect);
    }

    boolean hover(String id) {
        float[] rect = hitboxes.get(id);
        return rect != null && contains(rect, mouseX, mouseY);
    }

    private static boolean contains(float[] rect, float x, float y) {
        return x >= rect[0] && y >= rect[1] && x <= rect[0] + rect[2] && y <= rect[1] + rect[3];
    }

    private String hitAt(float x, float y, String prefixFilter) {
        String found = null;
        for (Map.Entry<String, float[]> entry : hitboxes.entrySet()) {
            if (prefixFilter != null && !entry.getKey().startsWith(prefixFilter)) {
                continue;
            }
            if (contains(entry.getValue(), x, y)) {
                found = entry.getKey();
                if (prefixFilter != null) {
                    return found;
                }
            }
        }
        return found;
    }

    void card(UiCanvas canvas, float x, float y, float w, float h) {
        canvas.fillRoundedRect(x, y, w, h, config.ui.cornerRadius + 2, surface());
        canvas.drawRoundedOutline(x, y, w, h, config.ui.cornerRadius + 2, 1,
                Colors.withAlpha(elevated(), 0xFF));
    }

    void sectionTitle(UiCanvas canvas, String title, float x, float y) {
        canvas.drawText(title, x, y, text(), true, 1f);
        canvas.fillRect(x, y + 12, 24, 2, accent());
    }

    void label(UiCanvas canvas, String value, float x, float y, int color) {
        canvas.drawText(value, x, y, color, false, 0.85f);
    }

    void value(UiCanvas canvas, String value, float x, float y) {
        canvas.drawText(value, x, y, text(), true, 0.95f);
    }

    void paragraph(UiCanvas canvas, String value, float x, float y, float width, int color) {
        if (value == null) {
            return;
        }
        List<String> lines = wrap(value, canvas, width, 0.82f);
        for (int i = 0; i < lines.size(); i++) {
            canvas.drawText(lines.get(i), x, y + i * 11f, color, false, 0.82f);
        }
    }

    List<String> wrap(String value, UiCanvas canvas, float width, float scale) {
        List<String> lines = new ArrayList<String>();
        if (value == null) {
            return lines;
        }
        String[] words = value.split(" ");
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String candidate = current.length() == 0 ? words[i] : current + " " + words[i];
            if (canvas.getTextWidth(candidate, scale) > width && current.length() > 0) {
                lines.add(current.toString());
                current = new StringBuilder(words[i]);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    boolean button(String id, String text, float x, float y, float w, float h, int color, boolean outlineOnly) {
        float[] rect = new float[]{x, y, w, h};
        register(id, rect);
        boolean isHover = contains(rect, mouseX, mouseY);
        float anim = approach(animations.get(id), isHover ? 1f : 0f, 0.25f);
        animations.put(id, Float.valueOf(anim));
        int fill = outlineOnly
                ? Colors.withAlpha(color, (int) (0x22 + 0x33 * anim))
                : Colors.blend(Colors.withAlpha(color, 0xCC), Colors.withAlpha(color, 0xFF), anim);
        canvas.fillRoundedRect(x, y, w, h, 6, fill);
        canvas.drawRoundedOutline(x, y, w, h, 6, 1, Colors.withAlpha(color, 0xCC));
        float scale = 0.88f;
        float textW = canvas.getTextWidth(text, scale);
        canvas.drawText(text, x + (w - textW) / 2f, y + (h - canvas.getFontHeight(scale)) / 2f + 1f,
                text(), true, scale);
        return isHover;
    }

    void toggle(UiCanvas canvas, String id, boolean on, float x, float y, String caption, boolean enabled) {
        float w = 30f;
        float h = 14f;
        float[] rect = new float[]{x, y, w, h};
        register(id, rect);
        boolean isHover = contains(rect, mouseX, mouseY) && enabled;
        float anim = approach(animations.get(id), on ? 1f : 0f, 0.3f);
        animations.put(id, Float.valueOf(anim));
        int trackColor = Colors.blend(Colors.withAlpha(elevated(), 0xFF),
                Colors.withAlpha(on ? success() : error(), 0xFF), anim);
        if (!enabled) {
            trackColor = Colors.withAlpha(muted(), 0x55);
        }
        canvas.fillRoundedRect(x, y, w, h, h / 2f, trackColor);
        float knobX = x + 2 + (w - h) * anim;
        canvas.fillRoundedRect(knobX, y + 2, h - 4, h - 4, (h - 4) / 2f,
                enabled ? Colors.WHITE : Colors.withAlpha(Colors.WHITE, 0x77));
        if (caption != null && !caption.isEmpty()) {
            canvas.drawText(caption, x + w + 8, y + 3.5f, enabled ? text() : muted(), false, 0.9f);
        }
        if (isHover) {
            tooltip = on ? "Click to turn OFF" : "Click to turn ON";
        }
    }

    void slider(UiCanvas canvas, String id, String caption, double value, double min, double max,
                float x, float y, float w, String suffix) {
        float h = 6f;
        float barY = y + 12f;
        float[] rect = new float[]{x, barY - 4, w, h + 8};
        register("slider:" + id, rect);
        float t = (float) Numbers.clamp((value - min) / Math.max(0.0001d, max - min), 0d, 1d);
        canvas.drawText(caption, x, y, muted(), false, 0.85f);
        String valueText = Numbers.formatAmount(value) + (suffix == null ? "" : " " + suffix);
        float valueW = canvas.getTextWidth(valueText, 0.85f);
        canvas.drawText(valueText, x + w - valueW, y, text(), true, 0.85f);
        canvas.fillRoundedRect(x, barY, w, h, h / 2f, Colors.withAlpha(elevated(), 0xFF));
        canvas.fillRoundedRect(x, barY, Math.max(3f, w * t), h, h / 2f,
                Colors.blend(secondary(), primary(), t));
        canvas.fillRoundedRect(x + Math.max(0f, w * t - 3f), barY - 3, 6, h + 6, 3, Colors.WHITE);
        if (contains(rect, mouseX, mouseY)) {
            tooltip = "Drag to change";
        }
    }

    void numField(UiCanvas canvas, String id, String caption, String value, float x, float y, float w) {
        canvas.drawText(caption, x, y, muted(), false, 0.85f);
        float fieldY = y + 11;
        float[] rect = new float[]{x, fieldY, w, 18};
        register("field:" + id, rect);
        boolean editing = editingField.equals(id);
        boolean isHover = contains(rect, mouseX, mouseY);
        canvas.fillRoundedRect(x, fieldY, w, 18, 5,
                Colors.withAlpha(editing ? primary() : elevated(), editing ? 0x55 : 0xFF));
        canvas.drawRoundedOutline(x, fieldY, w, 18, 5, 1,
                Colors.withAlpha(editing || isHover ? primary() : muted(), 0xAA));
        String shown = editing ? editBuffer + "_" : value;
        canvas.drawText(trim(shown == null ? "" : shown, canvas, w - 10, 0.85f), x + 5, fieldY + 5,
                text(), false, 0.85f);
        if (isHover && !editing) {
            tooltip = "Click to edit, ENTER to apply";
        }
    }

    void dropdown(UiCanvas canvas, String id, String caption, List<String> options, String selected,
                  float x, float y, float w) {
        canvas.drawText(caption, x, y, muted(), false, 0.85f);
        float fieldY = y + 11;
        float[] rect = new float[]{x, fieldY, w, 18};
        register(id, rect);
        boolean open = id.equals(openDropdown);
        boolean isHover = contains(rect, mouseX, mouseY);
        canvas.fillRoundedRect(x, fieldY, w, 18, 5, Colors.withAlpha(elevated(), 0xFF));
        canvas.drawRoundedOutline(x, fieldY, w, 18, 5, 1,
                Colors.withAlpha(open || isHover ? primary() : muted(), 0xAA));
        canvas.drawText(trim(selected == null ? "-" : selected, canvas, w - 18, 0.85f), x + 5, fieldY + 5,
                text(), false, 0.85f);
        canvas.drawText(open ? "^" : "v", x + w - 10, fieldY + 5, muted(), false, 0.85f);
        if (open) {
            dropX = x;
            dropY = fieldY + 20;
            dropW = w;
            dropOptions = options.toArray(new String[0]);
        }
    }

    void chip(String id, String caption, float x, float y, float w, float h, int color) {
        float[] rect = new float[]{x, y, w, h};
        register(id, rect);
        boolean isHover = contains(rect, mouseX, mouseY);
        canvas.fillRoundedRect(x, y, w, h, 6,
                Colors.withAlpha(color, isHover ? 0x55 : 0x33));
        canvas.drawRoundedOutline(x, y, w, h, 6, 1, Colors.withAlpha(color, 0xCC));
        float scale = 0.78f;
        float textW = canvas.getTextWidth(caption, scale);
        canvas.drawText(caption, x + (w - textW) / 2f, y + (h - canvas.getFontHeight(scale)) / 2f + 1,
                text(), true, scale);
    }

    void setDropdownSource(String id, float x, float y, float w, List<String> options) {
        dropX = x;
        dropY = y;
        dropW = w;
        dropOptions = options.toArray(new String[0]);
    }

    void toggleDropdown(String id) {
        openDropdown = id.equals(openDropdown) ? "" : id;
    }

    String openDropdownId() {
        return openDropdown;
    }

    private void handleDropdownChoice(String id, int index) {
        pages.onDropdown(id, index);
    }

    void bar(UiCanvas canvas, float x, float y, float w, float h, double t, int color) {
        canvas.fillRoundedRect(x, y, w, h, h / 2f, Colors.withAlpha(elevated(), 0xFF));
        canvas.fillRoundedRect(x, y, Math.max(2f, (float) (w * Numbers.clamp(t, 0d, 1d))), h, h / 2f, color);
    }

    void chart(UiCanvas canvas, float x, float y, float w, float h, List<double[]> series, int color,
               String emptyText) {
        canvas.fillRoundedRect(x, y, w, h, 6, Colors.withAlpha(0xFF05070C, 0xEE));
        canvas.drawRoundedOutline(x, y, w, h, 6, 1, Colors.withAlpha(elevated(), 0xFF));
        for (int i = 1; i < 4; i++) {
            float lineY = y + h * i / 4f;
            canvas.fillRect(x + 4, lineY, w - 8, 1, Colors.withAlpha(muted(), 0x22));
        }
        if (series == null || series.size() < 2) {
            canvas.drawText(emptyText, x + 8, y + h / 2f - 4, muted(), false, 0.85f);
            return;
        }
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int i = 0; i < series.size(); i++) {
            min = Math.min(min, series.get(i)[1]);
            max = Math.max(max, series.get(i)[1]);
        }
        if (max - min < 0.0001d) {
            max = min + 1d;
        }
        float prevX = 0f;
        float prevY = 0f;
        for (int i = 0; i < series.size(); i++) {
            double[] point = series.get(i);
            float px = x + 6 + (w - 12) * (float) (point[0] / Math.max(1d, maxBucket(series)));
            float py = y + h - 6 - (h - 12) * (float) ((point[1] - min) / (max - min));
            if (i > 0) {
                canvas.drawLine(prevX, prevY, px, py, 1.6f, color);
            }
            canvas.fillRect(px - 1.5f, py - 1.5f, 3, 3, Colors.WHITE);
            prevX = px;
            prevY = py;
        }
        String label = Numbers.formatAmount(min) + " - " + Numbers.formatAmount(max);
        canvas.drawText(label, x + 6, y + 5, muted(), false, 0.75f);
    }

    private static double maxBucket(List<double[]> series) {
        double max = 1d;
        for (int i = 0; i < series.size(); i++) {
            max = Math.max(max, series.get(i)[0]);
        }
        return max;
    }

    void row(UiCanvas canvas, float x, float y, float w, String[] columns, float[] offsets, int color,
             boolean header) {
        for (int i = 0; i < columns.length && i < offsets.length; i++) {
            canvas.drawText(columns[i] == null ? "" : columns[i], x + offsets[i], y,
                    header ? muted() : color, header, header ? 0.8f : 0.82f);
        }
        if (header) {
            canvas.fillRect(x, y + 10, w, 1, Colors.withAlpha(muted(), 0x33));
        }
    }

    void listEditor(UiCanvas canvas, String idPrefix, List<String> values, float x, float y, float w,
                    String placeholder) {
        for (int i = 0; i < values.size(); i++) {
            float rowY = y + i * 16f;
            canvas.fillRoundedRect(x, rowY, w - 20, 14, 4, Colors.withAlpha(elevated(), 0xCC));
            canvas.drawText(trim(values.get(i), canvas, w - 30, 0.82f), x + 5, rowY + 4, text(), false, 0.82f);
            String removeId = idPrefix + ":remove:" + i;
            float[] rect = new float[]{x + w - 18, rowY, 16, 14};
            register(removeId, rect);
            canvas.fillRoundedRect(x + w - 18, rowY, 16, 14, 4,
                    Colors.withAlpha(error(), contains(rect, mouseX, mouseY) ? 0x66 : 0x33));
            canvas.drawText("x", x + w - 12, rowY + 4, text(), false, 0.82f);
        }
        float addY = y + values.size() * 16f + 2f;
        numField(canvas, idPrefix + ":new", placeholder, editValue(idPrefix + ":new"),
                x, addY, w - 52);
        button(idPrefix + ":add", "ADD", x + w - 48, addY + 11, 48, 18, primary(), false);
    }

    float approach(Float current, float target, float step) {
        float value = current == null ? target : current.floatValue();
        if (Math.abs(target - value) <= step) {
            return target;
        }
        return value + (target > value ? step : -step);
    }

    String editBufferValue() {
        return editBuffer;
    }

    void overrideEditBuffer(String value) {
        editBuffer = value == null ? "" : value;
    }

    void notifyError(String message) {
        core.getNotifications().error(message, System.currentTimeMillis());
        setStatus(message);
    }

    void notifySuccess(String message) {
        core.getNotifications().success(message, System.currentTimeMillis());
        setStatus(message);
    }

    List<FilterRule> filters() {
        return config.filters;
    }

    List<StrategyConfig> strategies() {
        return config.strategies;
    }
}
