package dev.axsesclient.common.gui;

import dev.axsesclient.common.AxsesCore;
import dev.axsesclient.common.animation.AnimationManager;
import dev.axsesclient.common.compat.CompatibilityEngine;
import dev.axsesclient.common.i18n.LocalizationManager;
import dev.axsesclient.common.modules.Module;
import dev.axsesclient.common.modules.ModuleCategory;
import dev.axsesclient.common.modules.ModuleManager;
import dev.axsesclient.common.notifications.NotificationManager;
import dev.axsesclient.common.platform.KeyCodes;
import dev.axsesclient.common.platform.MinecraftAdapter;
import dev.axsesclient.common.platform.RenderAdapter;
import dev.axsesclient.common.settings.BooleanSetting;
import dev.axsesclient.common.settings.ColorSetting;
import dev.axsesclient.common.settings.KeybindSetting;
import dev.axsesclient.common.settings.ModeSetting;
import dev.axsesclient.common.settings.MultiSelectSetting;
import dev.axsesclient.common.settings.NumberSetting;
import dev.axsesclient.common.settings.Setting;
import dev.axsesclient.common.settings.StringSetting;
import dev.axsesclient.common.theme.AxsesTheme;
import java.util.List;
import java.util.Locale;

public final class ClickGuiController {
    public enum TopView {
        MODULES,
        FAVORITES,
        THEMES,
        CONFIGS,
        COMPATIBILITY
    }

    private final AxsesCore core;
    private ModuleCategory selectedCategory = ModuleCategory.COMBAT_TRAINING;
    private TopView activeView = TopView.MODULES;
    private Module expandedModule = null;

    private boolean searchFocused = false;
    private String searchQuery = "";
    private float targetScrollY = 0.0f;
    private float currentScrollY = 0.0f;
    private float maxScrollY = 0.0f;
    private float lastListY = 60.0f;
    private float lastListH = 260.0f;

    private boolean draggingScrollbar = false;
    private float scrollbarDragGrabOffset = 0.0f;

    private KeybindSetting listeningKeybind = null;
    private NumberSetting draggingSlider = null;
    private float sliderDragX = 0f;
    private float sliderDragW = 110f;
    private ColorSetting activeColorPicker = null;
    private int draggingColorChannel = -1;
    private StringSetting editingStringSetting = null;

    private boolean draggingWindow = false;
    private float dragStartMouseX = 0f;
    private float dragStartMouseY = 0f;
    private float dragStartOffsetX = 0f;
    private float dragStartOffsetY = 0f;

    private String hoveredTooltip = null;

    public ClickGuiController(AxsesCore core) {
        this.core = core;
    }

    public void onOpen() {
        core.getAnimationManager().channel("clickgui:open", 0.0f, 0.16f).setImmediate(0.0f);
        core.getAnimationManager().channel("clickgui:open", 0.0f, 0.16f).setTarget(1.0f);
        listeningKeybind = null;
        draggingSlider = null;
        draggingWindow = false;
        draggingScrollbar = false;
    }

    public void onClose(MinecraftAdapter mc) {
        if (!searchQuery.trim().isEmpty()) {
            core.getModuleManager().recordSearchQuery(searchQuery);
        }
        listeningKeybind = null;
        draggingSlider = null;
        editingStringSetting = null;
        draggingWindow = false;
        draggingScrollbar = false;
        core.getConfigManager().saveActiveAsync();
    }

    private float[] computeWindowBounds(float screenW, float screenH) {
        float scalePref = Math.max(0.80f, Math.min(1.25f, core.getConfigManager().getGuiScale()));
        float targetW = Math.round(624.0f * scalePref);
        float targetH = Math.round(372.0f * scalePref);
        float winW = Math.max(430.0f, Math.min(targetW, screenW - 20.0f));
        float winH = Math.max(260.0f, Math.min(targetH, screenH - 20.0f));
        float cx = screenW * 0.5f + core.getConfigManager().getGuiOffsetX();
        float cy = screenH * 0.5f + core.getConfigManager().getGuiOffsetY();
        float wx = Math.round(Math.max(6.0f, Math.min(screenW - winW - 6.0f, cx - winW * 0.5f)));
        float wy = Math.round(Math.max(6.0f, Math.min(screenH - winH - 6.0f, cy - winH * 0.5f)));
        return new float[] { wx, wy, Math.round(winW), Math.round(winH) };
    }

    public void render(RenderAdapter r, MinecraftAdapter mc, int rawMouseX, int rawMouseY, float partialTick) {
        long startNs = System.nanoTime();
        float dt = core.getAnimationManager().beginFrame();
        float openAnim = core.getAnimationManager().animate("clickgui:open", 1.0f, 0.16f);
        AxsesTheme t = core.getThemeManager().getActiveTheme();
        LocalizationManager i18n = core.getLocalizationManager();

        int sw = r.getScaledWidth();
        int sh = r.getScaledHeight();

        // Deep Obsidian screen dim backdrop
        int backdropAlpha = Math.round(150.0f * core.getRenderManager().getBlurBackdropStrength() * openAnim);
        r.fillRect(0, 0, sw, sh, (backdropAlpha << 24) | 0x04060D);

        float[] bounds = computeWindowBounds(sw, sh);
        float wx = bounds[0];
        float wy = bounds[1];
        float winW = bounds[2];
        float winH = bounds[3];
        if (!core.getAnimationManager().isReducedMotion()) {
            wy = Math.round(wy + (1.0f - openAnim) * 8.0f);
        }

        float mx = rawMouseX;
        float my = rawMouseY;
        hoveredTooltip = null;

        int primaryCol = core.getThemeManager().filterColor(t.getPrimaryColor());
        int accentCol = core.getThemeManager().filterColor(t.getAccentColor());

        // Outer window shell (1:1 crisp GUI coordinates — zero matrix distortion!)
        r.drawShadow(wx, wy, winW, winH, 8.0f, t.getShadowIntensity(), 0x000000);
        r.fillRoundedRect(wx, wy, winW, winH, 8.0f, AxsesTheme.withAlpha(t.getBackgroundColor(), 0.97f));
        r.drawRoundedOutline(wx, wy, winW, winH, 8.0f, 1.0f, AxsesTheme.withAlpha(primaryCol, 0.85f));

        // Top Aurora Header Bar (height 42)
        r.fillRoundedRect(wx + 2, wy + 2, winW - 4, 40, 6.0f, AxsesTheme.withAlpha(t.getSurfaceColor(), 0.98f));
        float halfBar = (winW - 16.0f) * 0.5f;
        r.fillRect(wx + 8, wy + 2, halfBar, 2.0f, primaryCol);
        r.fillRect(wx + 8 + halfBar, wy + 2, halfBar, 2.0f, accentCol);

        // Brand emblem + title
        r.fillRoundedRect(wx + 12, wy + 10, 22, 22, 5.0f, AxsesTheme.withAlpha(primaryCol, 0.25f));
        r.drawRoundedOutline(wx + 12, wy + 10, 22, 22, 5.0f, 1.0f, accentCol);
        core.getFontManager().drawCentered(r, "A", wx + 23, wy + 17, accentCol, 1.0f);
        core.getFontManager().draw(r, "AXSES", wx + 40, wy + 11, t.getTextColor(), 1.0f);
        core.getFontManager().draw(r, "AURORA", wx + 40, wy + 22, accentCol, 1.0f);

        // Right-aligned Top Action Tabs
        String langLabel = i18n.isRussian() ? "RU" : "EN";
        float rightCursor = wx + winW - 10.0f;

        float langW = 30.0f;
        rightCursor -= langW;
        lastLangX = rightCursor;
        lastLangW = langW;
        drawHeaderPill(r, rightCursor, wy + 10, langW, 22, langLabel, false, true, mx, my);

        float hudW = Math.max(40.0f, core.getFontManager().width(r, i18n.tr("gui.hud_editor"), 1.0f) + 14.0f);
        rightCursor -= (hudW + 4.0f);
        lastHudX = rightCursor;
        lastHudW = hudW;
        drawHeaderPill(r, rightCursor, wy + 10, hudW, 22, i18n.tr("gui.hud_editor"), false, false, mx, my);

        float diagW = Math.max(42.0f, core.getFontManager().width(r, i18n.tr("gui.diag"), 1.0f) + 14.0f);
        rightCursor -= (diagW + 4.0f);
        lastDiagX = rightCursor;
        lastDiagW = diagW;
        drawHeaderPill(r, rightCursor, wy + 10, diagW, 22, i18n.tr("gui.diag"), activeView == TopView.COMPATIBILITY, false, mx, my);

        float cfgW = Math.max(52.0f, core.getFontManager().width(r, i18n.tr("gui.configs"), 1.0f) + 14.0f);
        rightCursor -= (cfgW + 4.0f);
        lastCfgX = rightCursor;
        lastCfgW = cfgW;
        drawHeaderPill(r, rightCursor, wy + 10, cfgW, 22, i18n.tr("gui.configs"), activeView == TopView.CONFIGS, false, mx, my);

        float thmW = Math.max(46.0f, core.getFontManager().width(r, i18n.tr("gui.themes"), 1.0f) + 14.0f);
        rightCursor -= (thmW + 4.0f);
        lastThmX = rightCursor;
        lastThmW = thmW;
        drawHeaderPill(r, rightCursor, wy + 10, thmW, 22, i18n.tr("gui.themes"), activeView == TopView.THEMES, false, mx, my);

        float favW = 26.0f;
        rightCursor -= (favW + 4.0f);
        lastFavX = rightCursor;
        drawHeaderPill(r, rightCursor, wy + 10, favW, 22, "★", activeView == TopView.FAVORITES, false, mx, my);

        // Responsive Search Box between Brand & Tabs
        float sx = wx + 92.0f;
        float sy = wy + 10.0f;
        float searchW = Math.max(96.0f, rightCursor - sx - 8.0f);
        lastSearchX = sx;
        lastSearchW = searchW;
        float shBox = 22.0f;
        r.fillRoundedRect(sx, sy, searchW, shBox, 5.0f, t.getBackgroundColor());
        r.drawRoundedOutline(sx, sy, searchW, shBox, 5.0f, 1.0f,
                searchFocused ? accentCol : AxsesTheme.withAlpha(t.getMutedColor(), 0.38f));
        String searchDisp = searchQuery.isEmpty() && !searchFocused
                ? i18n.tr("gui.search_placeholder")
                : (searchQuery + (searchFocused && (System.currentTimeMillis() / 500L) % 2L == 0L ? "|" : ""));
        core.getFontManager().draw(r, trimToWidth(r, searchDisp, searchW - 22.0f), sx + 8.0f, sy + 7.0f,
                searchQuery.isEmpty() && !searchFocused ? t.getMutedColor() : t.getTextColor(), 1.0f);
        if (!searchQuery.isEmpty()) {
            core.getFontManager().draw(r, "×", sx + searchW - 12.0f, sy + 7.0f, t.getMutedColor(), 1.0f);
        }

        // Left Sidebar Navigation Rail
        float sideX = wx + 8.0f;
        float sideY = wy + 48.0f;
        float sideW = 136.0f;
        float sideH = winH - 56.0f;
        r.fillRoundedRect(sideX, sideY, sideW, sideH, 6.0f, AxsesTheme.withAlpha(t.getSurfaceColor(), 0.96f));
        r.drawRoundedOutline(sideX, sideY, sideW, sideH, 6.0f, 1.0f, AxsesTheme.withAlpha(t.getMutedColor(), 0.18f));

        ModuleCategory[] cats = ModuleCategory.values();
        float cy = sideY + 6.0f;
        float catRowH = Math.min(27.0f, (sideH - 54.0f) / cats.length);
        for (int i = 0; i < cats.length; i++) {
            ModuleCategory cat = cats[i];
            boolean sel = (activeView == TopView.MODULES && selectedCategory == cat && searchQuery.isEmpty());
            boolean hov = isInside(mx, my, sideX + 5.0f, cy, sideW - 10.0f, catRowH - 2.0f);
            float hovAnim = core.getAnimationManager().smoothHover("cat:" + cat.name(), hov);

            if (sel) {
                r.fillRoundedRect(sideX + 5.0f, cy, sideW - 10.0f, catRowH - 2.0f, 5.0f, AxsesTheme.withAlpha(primaryCol, 0.28f));
                r.drawRoundedOutline(sideX + 5.0f, cy, sideW - 10.0f, catRowH - 2.0f, 5.0f, 1.0f, AxsesTheme.withAlpha(primaryCol, 0.65f));
                r.fillRect(sideX + 7.0f, cy + 5.0f, 2.0f, Math.max(6.0f, catRowH - 12.0f), accentCol);
            } else if (hovAnim > 0.02f) {
                r.fillRoundedRect(sideX + 5.0f, cy, sideW - 10.0f, catRowH - 2.0f, 5.0f, AxsesTheme.withAlpha(t.getElevatedColor(), 0.85f * hovAnim));
            }

            String catTitle = i18n.tr(cat.getTranslationKey());
            float textY = cy + (catRowH - 2.0f - 8.0f) * 0.5f;
            core.getFontManager().draw(r, catTitle, sideX + 13.0f, textY, sel ? t.getTextColor() : t.getMutedColor(), 1.0f);

            List<Module> inCat = core.getModuleManager().getByCategory(cat);
            int enCount = 0;
            for (int k = 0; k < inCat.size(); k++) {
                if (inCat.get(k).isEnabled()) enCount++;
            }
            String cnt = enCount > 0 ? (enCount + "/" + inCat.size()) : String.valueOf(inCat.size());
            float cntW = core.getFontManager().width(r, cnt, 1.0f);
            core.getFontManager().draw(r, cnt, sideX + sideW - 10.0f - cntW, textY,
                    enCount > 0 ? accentCol : AxsesTheme.withAlpha(t.getMutedColor(), 0.70f), 1.0f);

            cy += catRowH;
        }

        // Sidebar bottom utility buttons (Sort & Open Keybind)
        ModuleManager.SortOrder ord = core.getModuleManager().getSortOrder();
        String sortLabel = ord == ModuleManager.SortOrder.ALPHABETICAL
                ? i18n.tr("gui.sort_az")
                : (ord == ModuleManager.SortOrder.ENABLED_FIRST ? i18n.tr("gui.sort_enabled") : i18n.tr("gui.sort_default"));
        drawSmallButton(r, sideX + 6.0f, sideY + sideH - 42.0f, sideW - 12.0f, 18.0f, trimToWidth(r, sortLabel, sideW - 18.0f), false, mx, my);

        String bindLabel = listeningKeybind == core.getKeybindManager().getOpenGuiBind()
                ? i18n.tr("gui.press_key")
                : (i18n.tr("gui.open_key") + core.getKeybindManager().getOpenGuiBind().getFormattedKey());
        drawSmallButton(r, sideX + 6.0f, sideY + sideH - 21.0f, sideW - 12.0f, 18.0f, trimToWidth(r, bindLabel, sideW - 18.0f),
                listeningKeybind == core.getKeybindManager().getOpenGuiBind(), mx, my);

        // Main Content Area
        float contentX = sideX + sideW + 8.0f;
        float contentY = sideY;
        float contentW = (wx + winW - 8.0f) - contentX;
        float contentH = sideH;

        if (activeView == TopView.THEMES) {
            renderThemesPane(r, contentX, contentY, contentW, contentH, mx, my);
        } else if (activeView == TopView.CONFIGS) {
            renderConfigsPane(r, contentX, contentY, contentW, contentH, mx, my);
        } else if (activeView == TopView.COMPATIBILITY) {
            renderCompatibilityPane(r, mc, contentX, contentY, contentW, contentH);
        } else {
            renderModulesPane(r, mc, contentX, contentY, contentW, contentH, mx, my);
        }

        // Crisp Hover Tooltip
        if (hoveredTooltip != null && !hoveredTooltip.isEmpty()) {
            String tip = trimToWidth(r, hoveredTooltip, winW - 24.0f);
            float tw = core.getFontManager().width(r, tip, 1.0f) + 14.0f;
            float tx = Math.min(wx + winW - tw - 6.0f, Math.max(wx + 6.0f, mx + 10.0f));
            float ty = Math.max(wy + 6.0f, my - 22.0f);
            r.fillRoundedRect(tx, ty, tw, 18.0f, 4.0f, AxsesTheme.withAlpha(t.getBackgroundColor(), 0.98f));
            r.drawRoundedOutline(tx, ty, tw, 18.0f, 4.0f, 1.0f, accentCol);
            core.getFontManager().draw(r, tip, tx + 7.0f, ty + 5.0f, t.getTextColor(), 1.0f);
        }

        core.getRenderManager().renderNotifications(
                r,
                core.getNotificationManager(),
                dt,
                core.getAnimationManager().getAnimationSpeed(),
                core.getAnimationManager().isReducedMotion()
        );

        core.getPerformanceProfiler().recordClickGuiRenderNanos(System.nanoTime() - startNs);
    }

    private boolean showLanguageBanner() {
        return activeView == TopView.MODULES
                && selectedCategory == ModuleCategory.SETTINGS
                && searchQuery.isEmpty();
    }

    private void renderModulesPane(
            RenderAdapter r,
            MinecraftAdapter mc,
            float x,
            float y,
            float w,
            float h,
            float mx,
            float my
    ) {
        AxsesTheme t = core.getThemeManager().getActiveTheme();
        LocalizationManager i18n = core.getLocalizationManager();
        int primaryCol = core.getThemeManager().filterColor(t.getPrimaryColor());
        int accentCol = core.getThemeManager().filterColor(t.getAccentColor());

        float topOffset = 0.0f;
        List<String> hist = core.getModuleManager().getSearchHistory();
        if ((searchFocused || !searchQuery.isEmpty()) && !hist.isEmpty()) {
            float chipX = x;
            for (int i = 0; i < Math.min(4, hist.size()); i++) {
                String chip = hist.get(i);
                float cw = Math.min(100.0f, core.getFontManager().width(r, chip, 1.0f) + 14.0f);
                drawSmallButton(r, chipX, y, cw, 17.0f, chip, false, mx, my);
                chipX += cw + 4.0f;
            }
            topOffset = 21.0f;
        } else if (showLanguageBanner()) {
            // Prominent Language Selector Card pinned at top of Settings (Настройки)
            float banW = w - 10.0f;
            float banH = 40.0f;
            r.fillRoundedRect(x, y, banW, banH, 6.0f, AxsesTheme.withAlpha(t.getElevatedColor(), 0.96f));
            r.drawRoundedOutline(x, y, banW, banH, 6.0f, 1.0f, AxsesTheme.withAlpha(accentCol, 0.78f));
            r.fillRect(x + 2.0f, y + 6.0f, 2.5f, banH - 12.0f, accentCol);

            float maxLblW = Math.max(80.0f, banW - 196.0f);
            core.getFontManager().draw(r, trimToWidth(r, "🌐 " + i18n.tr("gui.lang_card_title"), maxLblW),
                    x + 12.0f, y + 7.0f, t.getTextColor(), 1.0f);
            core.getFontManager().draw(r, trimToWidth(r, i18n.tr("gui.lang_card_desc"), maxLblW),
                    x + 12.0f, y + 22.0f, t.getMutedColor(), 1.0f);

            boolean isRu = i18n.isRussian();
            drawSmallButton(r, x + banW - 176.0f, y + 10.0f, 82.0f, 20.0f, isRu ? "Русский ✓" : "Русский", isRu, mx, my);
            drawSmallButton(r, x + banW - 88.0f, y + 10.0f, 80.0f, 20.0f, !isRu ? "English ✓" : "English", !isRu, mx, my);
            topOffset = 46.0f;
        }

        float listY = y + topOffset;
        float listH = h - topOffset;
        lastListY = listY;
        lastListH = listH;

        List<Module> modules = core.getModuleManager().query(
                selectedCategory,
                activeView == TopView.FAVORITES,
                searchQuery
        );

        // Compute exact content height BEFORE clamping scroll so expanding drawers never lags behind by 1 frame
        float totalContentHeight = 0.0f;
        for (int i = 0; i < modules.size(); i++) {
            Module m = modules.get(i);
            float cardH = 38.0f + ((expandedModule == m) ? (10.0f + countSettingsHeight(m)) : 0.0f);
            totalContentHeight += cardH + 6.0f;
        }
        maxScrollY = Math.max(0.0f, totalContentHeight - listH + 4.0f);
        if (targetScrollY > maxScrollY) {
            targetScrollY = maxScrollY;
        }
        if (targetScrollY < 0.0f) {
            targetScrollY = 0.0f;
        }

        if (draggingScrollbar) {
            currentScrollY = targetScrollY;
            core.getAnimationManager().channel("clickgui:modules", targetScrollY, 0.14f).setImmediate(targetScrollY);
        } else {
            currentScrollY = core.getAnimationManager().smoothScroll("clickgui:modules", targetScrollY);
        }

        int scX = Math.max(0, Math.round(x));
        int scY = Math.max(0, Math.round(listY));
        int scW = Math.max(1, Math.round(w));
        int scH = Math.max(1, Math.round(listH));
        r.pushScissor(scX, scY, scW, scH);

        if (modules.isEmpty()) {
            core.getFontManager().drawCentered(r, i18n.tr("gui.no_results"), x + w * 0.5f, listY + listH * 0.45f, t.getMutedColor(), 1.0f);
            r.popScissor();
            return;
        }

        float cardW = w - 10.0f;
        float cursorY = listY - currentScrollY;

        for (int i = 0; i < modules.size(); i++) {
            Module m = modules.get(i);
            boolean expanded = (expandedModule == m);
            float baseCardH = 38.0f;
            float cardH = baseCardH + (expanded ? (10.0f + countSettingsHeight(m)) : 0.0f);

            if (cursorY + cardH >= listY - 4.0f && cursorY <= listY + listH + 4.0f) {
                boolean cardHov = isInside(mx, my, x, cursorY, cardW, baseCardH) && isInside(mx, my, x, listY, w, listH);
                float hovAnim = core.getAnimationManager().smoothHover("mod:" + m.getId(), cardHov);
                float toggleAnim = core.getAnimationManager().smoothToggle("tog:" + m.getId(), m.isEnabled());

                // Smoothly interpolated card background, border & glow bar (zero visual snap!)
                int offBg = AxsesTheme.withAlpha(t.getSurfaceColor(), 0.96f);
                int onBg = AxsesTheme.withAlpha(t.getElevatedColor(), 0.98f);
                int cardBg = AnimationManager.lerpColor(offBg, onBg, toggleAnim);
                r.fillRoundedRect(x, cursorY, cardW, cardH, 6.0f, cardBg);

                if (hovAnim > 0.02f) {
                    r.fillRoundedRect(x, cursorY, cardW, baseCardH, 6.0f, AxsesTheme.withAlpha(primaryCol, 0.09f * hovAnim));
                }

                int offBorder = AxsesTheme.withAlpha(t.getMutedColor(), 0.22f + 0.25f * hovAnim);
                int onBorder = AxsesTheme.withAlpha(primaryCol, 0.88f);
                int borderCol = AnimationManager.lerpColor(offBorder, onBorder, toggleAnim);
                r.drawRoundedOutline(x, cursorY, cardW, cardH, 6.0f, 1.0f, borderCol);

                if (toggleAnim > 0.02f) {
                    float barH = (baseCardH - 12.0f) * toggleAnim;
                    float barY = cursorY + (baseCardH - barH) * 0.5f;
                    r.fillRect(x + 2.0f, barY, 2.5f, barH, AxsesTheme.withAlpha(accentCol, toggleAnim));
                }

                String incomp = m.getIncompatibilityReason(mc);
                String dispName = i18n.moduleName(m.getId(), m.getName());
                String dispDesc = incomp != null ? incomp : i18n.moduleDesc(m.getId(), m.getDescription());
                if (cardHov) {
                    hoveredTooltip = dispDesc;
                }

                // Favorite Star
                int starCol = m.isFavorite() ? t.getWarningColor() : AxsesTheme.withAlpha(t.getMutedColor(), 0.70f);
                core.getFontManager().draw(r, m.isFavorite() ? "★" : "☆", x + 9.0f, cursorY + 8.0f, starCol, 1.0f);

                // Module Name + Clean Truncated Subtitle
                float maxTextW = Math.max(60.0f, cardW - 148.0f);
                core.getFontManager().draw(r, trimToWidth(r, dispName, maxTextW), x + 23.0f, cursorY + 7.0f,
                        incomp != null ? t.getMutedColor() : t.getTextColor(), 1.0f);
                core.getFontManager().draw(r, trimToWidth(r, dispDesc, maxTextW), x + 23.0f, cursorY + 21.0f,
                        incomp != null ? t.getWarningColor() : t.getMutedColor(), 1.0f);

                // Keybind Pill ([R] or [—])
                boolean kbListening = (listeningKeybind == m.getKeybind());
                String rawKey = m.getKeybind().getFormattedKey();
                String kbLabel = kbListening
                        ? "[...]"
                        : ("NONE".equalsIgnoreCase(rawKey) ? ("[" + i18n.tr("gui.no_bind") + "]") : ("[" + rawKey + "]"));
                drawSmallButton(r, x + cardW - 110.0f, cursorY + 10.0f, 44.0f, 18.0f, kbLabel, kbListening, mx, my);

                // Settings Expand Button
                if (!m.getSettings().isEmpty()) {
                    drawSmallButton(r, x + cardW - 62.0f, cursorY + 10.0f, 20.0f, 18.0f, expanded ? "▾" : "▸", expanded, mx, my);
                }

                // Modern Animated Toggle Switch
                core.getRenderManager().drawToggleSwitch(r, x + cardW - 36.0f, cursorY + 11.0f, 28.0f, 16.0f, toggleAnim);

                // Inline Settings Drawer
                if (expanded) {
                    r.fillRect(x + 10.0f, cursorY + baseCardH, cardW - 20.0f, 1.0f, AxsesTheme.withAlpha(t.getMutedColor(), 0.22f));
                    renderModuleSettings(r, m, x + 12.0f, cursorY + baseCardH + 6.0f, cardW - 24.0f, mx, my);
                }
            }

            cursorY += cardH + 6.0f;
        }

        r.popScissor();

        // Interactive Clickable & Draggable Scrollbar Track & Thumb
        if (maxScrollY > 1.0f) {
            float barX = x + w - 5.0f;
            boolean barHov = draggingScrollbar || isInside(mx, my, barX - 4.0f, listY, 10.0f, listH);
            r.fillRoundedRect(barX, listY, 4.0f, listH, 2.0f, AxsesTheme.withAlpha(t.getSurfaceColor(), 0.88f));
            float thumbH = Math.max(24.0f, listH * (listH / (listH + maxScrollY)));
            float ratio = Math.max(0.0f, Math.min(1.0f, currentScrollY / maxScrollY));
            float thumbY = listY + (listH - thumbH) * ratio;
            r.fillRoundedRect(barX, thumbY, 4.0f, thumbH, 2.0f, barHov ? accentCol : primaryCol);
        }
    }

    private float countSettingsHeight(Module m) {
        float h = 0.0f;
        for (Setting<?> s : m.getSettings()) {
            if (s instanceof ColorSetting) {
                h += (activeColorPicker == s) ? 74.0f : 24.0f;
            } else if (s instanceof MultiSelectSetting) {
                h += 32.0f;
            } else {
                h += 24.0f;
            }
        }
        return h + 22.0f;
    }

    private void renderModuleSettings(RenderAdapter r, Module m, float sx, float sy, float sw, float mx, float my) {
        AxsesTheme t = core.getThemeManager().getActiveTheme();
        LocalizationManager i18n = core.getLocalizationManager();
        float rowY = sy;

        for (Setting<?> s : m.getSettings()) {
            String title = i18n.settingName(s.getId(), s.getName());
            if (s instanceof BooleanSetting) {
                BooleanSetting bs = (BooleanSetting) s;
                core.getFontManager().draw(r, title, sx, rowY + 4.0f, t.getTextColor(), 1.0f);
                float anim = core.getAnimationManager().smoothToggle("set:" + m.getId() + ":" + bs.getId(), bs.isEnabled());
                core.getRenderManager().drawToggleSwitch(r, sx + sw - 28.0f, rowY + 2.0f, 26.0f, 14.0f, anim);
                rowY += 24.0f;
            } else if (s instanceof NumberSetting) {
                NumberSetting ns = (NumberSetting) s;
                String valStr = ns.getStep() >= 1.0
                        ? String.valueOf(ns.getInt())
                        : String.format(Locale.ROOT, "%.2f", Double.valueOf(ns.getDouble()));
                core.getFontManager().draw(r, title + ": " + valStr, sx, rowY + 4.0f, t.getTextColor(), 1.0f);
                core.getRenderManager().drawSlider(r, sx + sw - 120.0f, rowY + 4.0f, 118.0f, 12.0f, ns.getNormalized());
                rowY += 24.0f;
            } else if (s instanceof ModeSetting) {
                ModeSetting ms = (ModeSetting) s;
                core.getFontManager().draw(r, title, sx, rowY + 4.0f, t.getTextColor(), 1.0f);
                String optLabel = i18n.optionName(ms.getValue());
                float pillW = Math.min(144.0f, Math.max(80.0f, core.getFontManager().width(r, optLabel, 1.0f) + 18.0f));
                drawSmallButton(r, sx + sw - pillW, rowY + 1.0f, pillW, 18.0f, optLabel, false, mx, my);
                rowY += 24.0f;
            } else if (s instanceof KeybindSetting) {
                KeybindSetting ks = (KeybindSetting) s;
                core.getFontManager().draw(r, title, sx, rowY + 4.0f, t.getTextColor(), 1.0f);
                String lbl = (listeningKeybind == ks) ? "[...]" : ("[" + ks.getFormattedKey() + "]");
                drawSmallButton(r, sx + sw - 74.0f, rowY + 1.0f, 74.0f, 18.0f, lbl, listeningKeybind == ks, mx, my);
                rowY += 24.0f;
            } else if (s instanceof StringSetting) {
                StringSetting ss = (StringSetting) s;
                core.getFontManager().draw(r, title, sx, rowY + 4.0f, t.getTextColor(), 1.0f);
                float boxW = Math.min(160.0f, sw * 0.52f);
                float bx = sx + sw - boxW;
                r.fillRoundedRect(bx, rowY + 1.0f, boxW, 18.0f, 4.0f, t.getBackgroundColor());
                r.drawRoundedOutline(bx, rowY + 1.0f, boxW, 18.0f, 4.0f, 1.0f,
                        editingStringSetting == ss ? t.getAccentColor() : t.getMutedColor());
                String txt = ss.getValue() + (editingStringSetting == ss ? "|" : "");
                core.getFontManager().draw(r, trimToWidth(r, txt, boxW - 10.0f), bx + 5.0f, rowY + 5.0f, t.getTextColor(), 1.0f);
                rowY += 24.0f;
            } else if (s instanceof ColorSetting) {
                ColorSetting cs = (ColorSetting) s;
                core.getFontManager().draw(r, title + " (" + cs.toHex() + ")", sx, rowY + 4.0f, t.getTextColor(), 1.0f);
                r.fillRoundedRect(sx + sw - 46.0f, rowY + 2.0f, 44.0f, 16.0f, 4.0f, cs.getArgb());
                r.drawRoundedOutline(sx + sw - 46.0f, rowY + 2.0f, 44.0f, 16.0f, 4.0f, 1.0f, t.getTextColor());
                rowY += 24.0f;
                if (activeColorPicker == cs) {
                    String[] chNames = { "R", "G", "B", "A" };
                    int[] vals = { cs.getRed(), cs.getGreen(), cs.getBlue(), cs.getAlpha() };
                    for (int ch = 0; ch < 4; ch++) {
                        core.getFontManager().draw(r, chNames[ch] + ":" + vals[ch], sx + 8.0f, rowY + 2.0f, t.getMutedColor(), 1.0f);
                        core.getRenderManager().drawSlider(r, sx + sw - 120.0f, rowY + 1.0f, 118.0f, 10.0f, vals[ch] / 255.0f);
                        rowY += 12.5f;
                    }
                }
            } else if (s instanceof MultiSelectSetting) {
                MultiSelectSetting mss = (MultiSelectSetting) s;
                core.getFontManager().draw(r, title, sx, rowY + 4.0f, t.getTextColor(), 1.0f);
                float cx = sx + 110.0f;
                for (String opt : mss.getOptions()) {
                    boolean on = mss.isSelected(opt);
                    String dispOpt = i18n.optionName(opt);
                    float ow = core.getFontManager().width(r, dispOpt, 1.0f) + 12.0f;
                    if (cx + ow <= sx + sw) {
                        r.fillRoundedRect(cx, rowY + 1.0f, ow, 18.0f, 4.0f,
                                on ? core.getThemeManager().filterColor(t.getPrimaryColor()) : t.getBackgroundColor());
                        core.getFontManager().drawCentered(r, dispOpt, cx + ow * 0.5f, rowY + 5.0f, t.getTextColor(), 1.0f);
                        cx += ow + 4.0f;
                    }
                }
                rowY += 32.0f;
            }
        }

        String rst = i18n.tr("gui.reset_all");
        float rw = Math.max(96.0f, core.getFontManager().width(r, rst, 1.0f) + 16.0f);
        drawSmallButton(r, sx + sw - rw, rowY + 2.0f, rw, 18.0f, rst, false, mx, my);
    }

    private void renderThemesPane(RenderAdapter r, float x, float y, float w, float h, float mx, float my) {
        AxsesTheme active = core.getThemeManager().getActiveTheme();
        List<AxsesTheme> list = core.getThemeManager().getAllThemes();
        boolean ru = core.getLocalizationManager().isRussian();
        core.getFontManager().draw(r, ru ? "Темы оформления AXSES AURORA (8 встроенных + свои)" : "AXSES AURORA Themes (8 Built-in + Custom)",
                x, y + 2.0f, active.getAccentColor(), 1.0f);

        float cardW = (w - 10.0f) * 0.5f;
        float cardH = 44.0f;
        for (int i = 0; i < Math.min(8, list.size()); i++) {
            AxsesTheme th = list.get(i);
            float cx = x + (i % 2) * (cardW + 10.0f);
            float cy = y + 20.0f + (i / 2) * (cardH + 7.0f);
            boolean isSel = th.getId().equals(active.getId());
            r.fillRoundedRect(cx, cy, cardW, cardH, 6.0f, th.getSurfaceColor());
            r.drawRoundedOutline(cx, cy, cardW, cardH, 6.0f, 1.0f, isSel ? th.getAccentColor() : th.getPrimaryColor());
            core.getFontManager().draw(r, th.getName() + (isSel ? " ✓" : ""), cx + 10.0f, cy + 8.0f, th.getTextColor(), 1.0f);
            r.fillRoundedRect(cx + 10.0f, cy + 24.0f, 18.0f, 11.0f, 3.0f, th.getPrimaryColor());
            r.fillRoundedRect(cx + 32.0f, cy + 24.0f, 18.0f, 11.0f, 3.0f, th.getSecondaryColor());
            r.fillRoundedRect(cx + 54.0f, cy + 24.0f, 18.0f, 11.0f, 3.0f, th.getAccentColor());
            r.fillRoundedRect(cx + 76.0f, cy + 24.0f, 18.0f, 11.0f, 3.0f, th.getSuccessColor());
        }

        float btnY = y + h - 22.0f;
        drawSmallButton(r, x, btnY, 108.0f, 19.0f, ru ? "+ Своя тема" : "+ New Theme", false, mx, my);
        drawSmallButton(r, x + 114.0f, btnY, 108.0f, 19.0f, ru ? "Экспорт JSON" : "Export JSON", false, mx, my);
        drawSmallButton(r, x + 228.0f, btnY, 108.0f, 19.0f, ru ? "Сброс тем" : "Reset Themes", false, mx, my);
    }

    private void renderConfigsPane(RenderAdapter r, float x, float y, float w, float h, float mx, float my) {
        AxsesTheme t = core.getThemeManager().getActiveTheme();
        boolean ru = core.getLocalizationManager().isRussian();
        core.getFontManager().draw(r,
                (ru ? "Профили конфигурации · Активен: " : "Configuration Profiles · Active: ") + core.getConfigManager().getActiveProfile(),
                x, y + 2.0f, t.getAccentColor(), 1.0f);

        List<String> profiles = core.getConfigManager().listProfiles();
        float py = y + 22.0f;
        for (int i = 0; i < Math.min(6, profiles.size()); i++) {
            String prof = profiles.get(i);
            boolean isAct = prof.equals(core.getConfigManager().getActiveProfile());
            r.fillRoundedRect(x, py, w, 26.0f, 5.0f, isAct ? t.getElevatedColor() : t.getSurfaceColor());
            r.drawRoundedOutline(x, py, w, 26.0f, 5.0f, 1.0f,
                    isAct ? t.getPrimaryColor() : AxsesTheme.withAlpha(t.getMutedColor(), 0.25f));
            core.getFontManager().draw(r, prof + (isAct ? (ru ? " (активен)" : " (active)") : ""), x + 10.0f, py + 9.0f, t.getTextColor(), 1.0f);
            drawSmallButton(r, x + w - 122.0f, py + 4.0f, 54.0f, 18.0f, ru ? "Загр." : "Load", false, mx, my);
            drawSmallButton(r, x + w - 62.0f, py + 4.0f, 54.0f, 18.0f, ru ? "Сохр." : "Save", false, mx, my);
            py += 30.0f;
        }

        float btnY = y + h - 22.0f;
        drawSmallButton(r, x, btnY, 96.0f, 19.0f, ru ? "+ Профиль" : "+ Profile", false, mx, my);
        drawSmallButton(r, x + 102.0f, btnY, 96.0f, 19.0f, ru ? "Бэкап" : "Backup", false, mx, my);
        drawSmallButton(r, x + 204.0f, btnY, 108.0f, 19.0f, ru ? "Восстановить" : "Restore", false, mx, my);
        drawSmallButton(r, x + 318.0f, btnY, 98.0f, 19.0f, ru ? "Сбросить всё" : "Factory Reset", false, mx, my);
    }

    private void renderCompatibilityPane(RenderAdapter r, MinecraftAdapter mc, float x, float y, float w, float h) {
        AxsesTheme t = core.getThemeManager().getActiveTheme();
        boolean ru = core.getLocalizationManager().isRussian();
        CompatibilityEngine.CompatibilityReport rep = core.getCompatibilityEngine().evaluate(
                mc, core.getInputAdapter(), core.getModuleManager(), core.getKeybindManager()
        );
        core.getFontManager().draw(r, ru ? "Диагностика системы, памяти и модов" : "System, Memory & Compatibility Diagnostics",
                x, y + 2.0f, t.getAccentColor(), 1.0f);

        long usedMb = core.getPerformanceProfiler().getUsedMemoryMb();
        long maxMb = core.getPerformanceProfiler().getMaxMemoryMb();

        int totalMods = core.getModuleManager().getAll().size();
        int compatMods = totalMods - rep.getRestrictedModules().size();

        String[] lines = {
                "Minecraft: " + rep.getMinecraftVersion() + "  |  Java: " + rep.getJavaVersion(),
                "Fabric Loader: " + rep.getFabricLoaderVersion(),
                (ru ? "Выделенная ОЗУ JVM (-Xmx): " : "Allocated JVM Heap (-Xmx): ") + usedMb + " / " + maxMb + " MB",
                (ru ? "Совместимых модулей: " : "Compatible Modules: ") + compatMods + " / " + totalMods,
                (ru ? "Запуск клиента: " : "Client Startup: ") + core.getPerformanceProfiler().getStartupDurationMs() + " ms"
                        + "  |  GUI Render: " + String.format(Locale.ROOT, "%.2f ms", Float.valueOf(core.getPerformanceProfiler().getAverageClickGuiCostUs() / 1000.0f))
        };
        float ly = y + 22.0f;
        for (String line : lines) {
            r.fillRoundedRect(x, ly, w, 24.0f, 5.0f, t.getSurfaceColor());
            core.getFontManager().draw(r, trimToWidth(r, line, w - 16.0f), x, ly + 8.0f, t.getTextColor(), 1.0f);
            ly += 28.0f;
        }
    }

    public boolean mouseClicked(MinecraftAdapter mc, double rawX, double rawY, int button) {
        float sw = mc != null ? mc.getScaledWidth() : 854.0f;
        float sh = mc != null ? mc.getScaledHeight() : 480.0f;
        float[] bounds = computeWindowBounds(sw, sh);
        float wx = bounds[0];
        float wy = bounds[1];
        float winW = bounds[2];
        float winH = bounds[3];
        float mx = (float) rawX;
        float my = (float) rawY;

        if (isInside(mx, my, wx + 2, wy + 2, winW - 4, 40)) {
            return handleHeaderClick(mc, wx, wy, winW, mx, my, rawX, rawY);
        }

        searchFocused = false;

        // Left Sidebar Clicks
        float sideX = wx + 8.0f;
        float sideY = wy + 48.0f;
        float sideW = 136.0f;
        float sideH = winH - 56.0f;

        ModuleCategory[] cats = ModuleCategory.values();
        float cy = sideY + 6.0f;
        float catRowH = Math.min(27.0f, (sideH - 54.0f) / cats.length);
        for (ModuleCategory cat : cats) {
            if (isInside(mx, my, sideX + 5.0f, cy, sideW - 10.0f, catRowH - 2.0f)) {
                selectedCategory = cat;
                activeView = TopView.MODULES;
                searchQuery = "";
                targetScrollY = 0.0f;
                return true;
            }
            cy += catRowH;
        }

        if (isInside(mx, my, sideX + 6.0f, sideY + sideH - 42.0f, sideW - 12.0f, 18.0f)) {
            core.getModuleManager().cycleSortOrder();
            return true;
        }
        if (isInside(mx, my, sideX + 6.0f, sideY + sideH - 21.0f, sideW - 12.0f, 18.0f)) {
            listeningKeybind = core.getKeybindManager().getOpenGuiBind();
            return true;
        }

        float contentX = sideX + sideW + 8.0f;
        float contentY = sideY;
        float contentW = (wx + winW - 8.0f) - contentX;
        float contentH = sideH;

        if (activeView == TopView.THEMES) {
            return handleThemesClick(mc, contentX, contentY, contentW, contentH, mx, my);
        }
        if (activeView == TopView.CONFIGS) {
            return handleConfigsClick(mc, contentX, contentY, contentW, contentH, mx, my);
        }
        if (activeView == TopView.MODULES || activeView == TopView.FAVORITES) {
            return handleModulesClick(mc, contentX, contentY, contentW, contentH, mx, my, button);
        }
        return false;
    }

    private float lastSearchX, lastSearchW, lastFavX, lastThmX, lastThmW, lastCfgX, lastCfgW, lastDiagX, lastDiagW, lastHudX, lastHudW, lastLangX, lastLangW = 30.0f;

    private boolean handleHeaderClick(MinecraftAdapter mc, float wx, float wy, float winW, float mx, float my, double rawX, double rawY) {
        float fallbackLangX = lastLangX > 0 ? lastLangX : (wx + winW - 40.0f);
        if (isInside(mx, my, fallbackLangX, wy + 10, lastLangW, 22)) {
            core.getLocalizationManager().toggleLocale();
            core.getConfigManager().saveActiveAsync();
            core.getNotificationManager().push("AXSES",
                    core.getLocalizationManager().isRussian() ? "Язык: Русский" : "Language: English",
                    NotificationManager.Severity.INFO);
            return true;
        }
        if (lastHudX > 0 && isInside(mx, my, lastHudX, wy + 10, lastHudW, 22)) {
            onClose(mc);
            mc.openHudEditor();
            return true;
        }
        if (lastDiagX > 0 && isInside(mx, my, lastDiagX, wy + 10, lastDiagW, 22)) {
            activeView = (activeView == TopView.COMPATIBILITY) ? TopView.MODULES : TopView.COMPATIBILITY;
            return true;
        }
        if (lastCfgX > 0 && isInside(mx, my, lastCfgX, wy + 10, lastCfgW, 22)) {
            activeView = (activeView == TopView.CONFIGS) ? TopView.MODULES : TopView.CONFIGS;
            return true;
        }
        if (lastThmX > 0 && isInside(mx, my, lastThmX, wy + 10, lastThmW, 22)) {
            activeView = (activeView == TopView.THEMES) ? TopView.MODULES : TopView.THEMES;
            return true;
        }
        if (lastFavX > 0 && isInside(mx, my, lastFavX, wy + 10, 26, 22)) {
            activeView = (activeView == TopView.FAVORITES) ? TopView.MODULES : TopView.FAVORITES;
            targetScrollY = 0.0f;
            return true;
        }
        if (lastSearchW > 0 && isInside(mx, my, lastSearchX, wy + 10, lastSearchW, 22)) {
            if (!searchQuery.isEmpty() && mx >= lastSearchX + lastSearchW - 18.0f) {
                searchQuery = "";
                return true;
            }
            searchFocused = true;
            activeView = TopView.MODULES;
            return true;
        }

        // Drag window by header empty region
        searchFocused = false;
        draggingWindow = true;
        dragStartMouseX = (float) rawX;
        dragStartMouseY = (float) rawY;
        dragStartOffsetX = core.getConfigManager().getGuiOffsetX();
        dragStartOffsetY = core.getConfigManager().getGuiOffsetY();
        return true;
    }

    private boolean handleThemesClick(MinecraftAdapter mc, float x, float y, float w, float h, float mx, float my) {
        List<AxsesTheme> list = core.getThemeManager().getAllThemes();
        float cardW = (w - 10.0f) * 0.5f;
        float cardH = 44.0f;
        for (int i = 0; i < Math.min(8, list.size()); i++) {
            float cx = x + (i % 2) * (cardW + 10.0f);
            float cy = y + 20.0f + (i / 2) * (cardH + 7.0f);
            if (isInside(mx, my, cx, cy, cardW, cardH)) {
                AxsesTheme chosen = list.get(i);
                core.getThemeManager().setActiveTheme(chosen.getId());
                core.getConfigManager().saveActiveAsync();
                core.getNotificationManager().push("AXSES", chosen.getName(), NotificationManager.Severity.SUCCESS);
                return true;
            }
        }
        float btnY = y + h - 22.0f;
        if (isInside(mx, my, x, btnY, 108.0f, 19.0f)) {
            AxsesTheme created = core.getThemeManager().createCustomTheme("Custom " + (list.size() + 1), core.getThemeManager().getActiveThemeId());
            core.getThemeManager().setActiveTheme(created.getId());
            core.getConfigManager().saveActiveAsync();
            return true;
        }
        if (isInside(mx, my, x + 114.0f, btnY, 108.0f, 19.0f) && mc != null && mc.getGameDir() != null) {
            try {
                String id = core.getThemeManager().getActiveThemeId();
                core.getThemeManager().exportTheme(id, mc.getGameDir().resolve("axsesclient").resolve("exported-" + id + ".axtheme.json"));
                core.getNotificationManager().push("AXSES", "exported-" + id + ".axtheme.json", NotificationManager.Severity.SUCCESS);
            } catch (Exception ignored) {
            }
            return true;
        }
        if (isInside(mx, my, x + 228.0f, btnY, 108.0f, 19.0f)) {
            core.getThemeManager().resetThemes();
            core.getConfigManager().saveActiveAsync();
            return true;
        }
        return false;
    }

    private boolean handleConfigsClick(MinecraftAdapter mc, float x, float y, float w, float h, float mx, float my) {
        List<String> profiles = core.getConfigManager().listProfiles();
        float py = y + 22.0f;
        for (int i = 0; i < Math.min(6, profiles.size()); i++) {
            String prof = profiles.get(i);
            if (isInside(mx, my, x + w - 122.0f, py + 4.0f, 54.0f, 18.0f)) {
                core.getConfigManager().load(prof, mc);
                core.getNotificationManager().push("AXSES", prof, NotificationManager.Severity.SUCCESS);
                return true;
            }
            if (isInside(mx, my, x + w - 62.0f, py + 4.0f, 54.0f, 18.0f)) {
                core.getConfigManager().save(prof);
                core.getNotificationManager().push("AXSES", prof, NotificationManager.Severity.SUCCESS);
                return true;
            }
            py += 30.0f;
        }
        float btnY = y + h - 22.0f;
        if (isInside(mx, my, x, btnY, 96.0f, 19.0f)) {
            String name = "profile_" + (profiles.size() + 1);
            core.getConfigManager().create(name, mc);
            return true;
        }
        if (isInside(mx, my, x + 102.0f, btnY, 96.0f, 19.0f)) {
            core.getConfigManager().createBackup();
            return true;
        }
        if (isInside(mx, my, x + 204.0f, btnY, 108.0f, 19.0f)) {
            List<String> backups = core.getConfigManager().listBackups();
            if (!backups.isEmpty()) {
                core.getConfigManager().restoreBackup(backups.get(0), mc);
            }
            return true;
        }
        if (isInside(mx, my, x + 318.0f, btnY, 98.0f, 19.0f)) {
            core.getModuleManager().resetAll(mc);
            core.getHudManager().resetAll();
            core.getConfigManager().saveActiveAsync();
            return true;
        }
        return false;
    }

    private boolean handleModulesClick(MinecraftAdapter mc, float x, float y, float w, float h, float mx, float my, int button) {
        float topOffset = 0.0f;
        List<String> hist = core.getModuleManager().getSearchHistory();
        if ((searchFocused || !searchQuery.isEmpty()) && !hist.isEmpty()) {
            float chipX = x;
            for (int i = 0; i < Math.min(4, hist.size()); i++) {
                String chip = hist.get(i);
                float cw = Math.min(100.0f, chip.length() * 6.0f + 14.0f);
                if (isInside(mx, my, chipX, y, cw, 17.0f)) {
                    searchQuery = chip;
                    return true;
                }
                chipX += cw + 4.0f;
            }
            topOffset = 21.0f;
        } else if (showLanguageBanner()) {
            float banW = w - 10.0f;
            if (isInside(mx, my, x + banW - 176.0f, y + 8.0f, 84.0f, 24.0f)) {
                core.getLocalizationManager().setActiveLocale("ru_RU");
                core.getConfigManager().saveActiveAsync();
                core.getNotificationManager().push("AXSES", "Русский язык активирован", NotificationManager.Severity.SUCCESS);
                return true;
            }
            if (isInside(mx, my, x + banW - 88.0f, y + 8.0f, 82.0f, 24.0f)) {
                core.getLocalizationManager().setActiveLocale("en_US");
                core.getConfigManager().saveActiveAsync();
                core.getNotificationManager().push("AXSES", "English language active", NotificationManager.Severity.SUCCESS);
                return true;
            }
            topOffset = 46.0f;
        }

        float listY = y + topOffset;
        float listH = h - topOffset;
        if (!isInside(mx, my, x, listY, w + 4.0f, listH)) {
            return false;
        }

        // Scrollbar click / drag support on right edge
        if (maxScrollY > 1.0f && mx >= x + w - 9.0f) {
            draggingScrollbar = true;
            float thumbH = Math.max(24.0f, listH * (listH / (listH + maxScrollY)));
            float ratio = Math.max(0.0f, Math.min(1.0f, currentScrollY / maxScrollY));
            float thumbY = listY + (listH - thumbH) * ratio;
            if (my >= thumbY && my <= thumbY + thumbH) {
                scrollbarDragGrabOffset = my - thumbY;
            } else {
                scrollbarDragGrabOffset = thumbH * 0.5f;
                float norm = (my - listY - scrollbarDragGrabOffset) / Math.max(1.0f, listH - thumbH);
                targetScrollY = Math.max(0.0f, Math.min(maxScrollY, norm * maxScrollY));
            }
            return true;
        }

        List<Module> modules = core.getModuleManager().query(selectedCategory, activeView == TopView.FAVORITES, searchQuery);
        float cardW = w - 10.0f;
        float cursorY = listY - currentScrollY;

        for (Module m : modules) {
            boolean expanded = (expandedModule == m);
            float baseCardH = 38.0f;
            float cardH = baseCardH + (expanded ? (10.0f + countSettingsHeight(m)) : 0.0f);

            if (isInside(mx, my, x, cursorY, cardW, baseCardH)) {
                if (isInside(mx, my, x + 4.0f, cursorY + 4.0f, 18.0f, 28.0f)) {
                    m.toggleFavorite();
                    core.getConfigManager().saveActiveAsync();
                    return true;
                }
                if (isInside(mx, my, x + cardW - 110.0f, cursorY + 10.0f, 44.0f, 18.0f)) {
                    listeningKeybind = m.getKeybind();
                    return true;
                }
                if ((button == 1 || isInside(mx, my, x + cardW - 62.0f, cursorY + 10.0f, 20.0f, 18.0f)) && !m.getSettings().isEmpty()) {
                    expandedModule = (expandedModule == m) ? null : m;
                    return true;
                }
                core.getModuleManager().toggleModuleWithFeedback(m, mc);
                core.getConfigManager().saveActiveAsync();
                return true;
            }

            if (expanded && isInside(mx, my, x, cursorY + baseCardH, cardW, cardH - baseCardH)) {
                boolean handled = handleModuleSettingsClick(mc, m, x + 12.0f, cursorY + baseCardH + 6.0f, cardW - 24.0f, mx, my);
                if (handled) {
                    core.getConfigManager().saveActiveAsync();
                }
                return handled;
            }
            cursorY += cardH + 6.0f;
        }
        return false;
    }

    private boolean handleModuleSettingsClick(MinecraftAdapter mc, Module m, float sx, float sy, float sw, float mx, float my) {
        LocalizationManager i18n = core.getLocalizationManager();
        float rowY = sy;
        for (Setting<?> s : m.getSettings()) {
            if (s instanceof BooleanSetting) {
                if (isInside(mx, my, sx, rowY, sw, 22.0f)) {
                    ((BooleanSetting) s).toggle();
                    return true;
                }
                rowY += 24.0f;
            } else if (s instanceof NumberSetting) {
                NumberSetting ns = (NumberSetting) s;
                float slX = sx + sw - 120.0f;
                if (isInside(mx, my, slX - 4.0f, rowY, 126.0f, 22.0f)) {
                    draggingSlider = ns;
                    sliderDragX = slX;
                    sliderDragW = 118.0f;
                    ns.setFromNormalized((mx - slX) / 118.0f);
                    return true;
                }
                rowY += 24.0f;
            } else if (s instanceof ModeSetting) {
                if (isInside(mx, my, sx, rowY, sw, 22.0f)) {
                    ((ModeSetting) s).cycleNext();
                    return true;
                }
                rowY += 24.0f;
            } else if (s instanceof KeybindSetting) {
                if (isInside(mx, my, sx + sw - 80.0f, rowY, 80.0f, 22.0f)) {
                    listeningKeybind = (KeybindSetting) s;
                    return true;
                }
                rowY += 24.0f;
            } else if (s instanceof StringSetting) {
                if (isInside(mx, my, sx + sw - 165.0f, rowY, 165.0f, 22.0f)) {
                    editingStringSetting = (StringSetting) s;
                    return true;
                }
                rowY += 24.0f;
            } else if (s instanceof ColorSetting) {
                ColorSetting cs = (ColorSetting) s;
                if (isInside(mx, my, sx + sw - 50.0f, rowY, 50.0f, 20.0f)) {
                    activeColorPicker = (activeColorPicker == cs) ? null : cs;
                    return true;
                }
                rowY += 24.0f;
                if (activeColorPicker == cs) {
                    float slX = sx + sw - 120.0f;
                    for (int ch = 0; ch < 4; ch++) {
                        if (isInside(mx, my, slX - 4.0f, rowY, 126.0f, 12.0f)) {
                            draggingColorChannel = ch;
                            sliderDragX = slX;
                            sliderDragW = 118.0f;
                            applyColorChannelDrag(cs, ch, (mx - slX) / 118.0f);
                            return true;
                        }
                        rowY += 12.5f;
                    }
                }
            } else if (s instanceof MultiSelectSetting) {
                MultiSelectSetting mss = (MultiSelectSetting) s;
                float cx = sx + 110.0f;
                for (String opt : mss.getOptions()) {
                    String dispOpt = i18n.optionName(opt);
                    float ow = dispOpt.length() * 6.0f + 12.0f;
                    if (isInside(mx, my, cx, rowY, ow, 20.0f)) {
                        mss.toggleOption(opt);
                        return true;
                    }
                    cx += ow + 4.0f;
                }
                rowY += 32.0f;
            }
        }
        if (isInside(mx, my, sx + sw - 120.0f, rowY, 120.0f, 20.0f)) {
            m.resetSettings(mc);
            return true;
        }
        return false;
    }

    public boolean mouseDragged(MinecraftAdapter mc, double rawX, double rawY, int button) {
        if (draggingScrollbar && maxScrollY > 1.0f) {
            float thumbH = Math.max(24.0f, lastListH * (lastListH / (lastListH + maxScrollY)));
            float norm = ((float) rawY - lastListY - scrollbarDragGrabOffset) / Math.max(1.0f, lastListH - thumbH);
            targetScrollY = Math.max(0.0f, Math.min(maxScrollY, norm * maxScrollY));
            currentScrollY = targetScrollY;
            return true;
        }
        if (draggingWindow) {
            core.getConfigManager().setGuiOffsetX(dragStartOffsetX + ((float) rawX - dragStartMouseX));
            core.getConfigManager().setGuiOffsetY(dragStartOffsetY + ((float) rawY - dragStartMouseY));
            return true;
        }
        if (draggingSlider != null) {
            draggingSlider.setFromNormalized(((float) rawX - sliderDragX) / sliderDragW);
            return true;
        }
        if (draggingColorChannel >= 0 && activeColorPicker != null) {
            applyColorChannelDrag(activeColorPicker, draggingColorChannel, ((float) rawX - sliderDragX) / sliderDragW);
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double rawX, double rawY, int button) {
        if (draggingWindow || draggingSlider != null || draggingColorChannel >= 0) {
            core.getConfigManager().saveActiveAsync();
        }
        draggingWindow = false;
        draggingSlider = null;
        draggingColorChannel = -1;
        draggingScrollbar = false;
        return false;
    }

    public boolean mouseScrolled(double verticalAmount) {
        if (Math.abs(verticalAmount) < 1.0e-5) {
            return false;
        }
        float step = 46.0f;
        float next = targetScrollY - (float) verticalAmount * step;
        targetScrollY = Math.max(0.0f, Math.min(Math.max(0.0f, maxScrollY), next));
        return true;
    }

    public boolean keyPressed(MinecraftAdapter mc, int keyCode) {
        if (listeningKeybind != null) {
            if (keyCode == KeyCodes.KEY_ESCAPE) {
                listeningKeybind = null;
            } else if (keyCode == KeyCodes.KEY_DELETE || keyCode == KeyCodes.KEY_BACKSPACE) {
                listeningKeybind.remove();
                listeningKeybind = null;
                core.getConfigManager().saveActiveAsync();
            } else {
                listeningKeybind.assign(keyCode);
                listeningKeybind = null;
                core.getConfigManager().saveActiveAsync();
            }
            return true;
        }

        if (editingStringSetting != null) {
            if (keyCode == KeyCodes.KEY_ENTER || keyCode == KeyCodes.KEY_ESCAPE) {
                editingStringSetting = null;
                core.getConfigManager().saveActiveAsync();
                return true;
            }
            if (keyCode == KeyCodes.KEY_BACKSPACE) {
                String cur = editingStringSetting.getValue();
                if (!cur.isEmpty()) {
                    editingStringSetting.setValue(cur.substring(0, cur.length() - 1));
                }
                return true;
            }
            return true;
        }

        if (searchFocused) {
            if (keyCode == KeyCodes.KEY_ESCAPE) {
                searchFocused = false;
                return true;
            }
            if (keyCode == KeyCodes.KEY_ENTER) {
                core.getModuleManager().recordSearchQuery(searchQuery);
                searchFocused = false;
                return true;
            }
            if (keyCode == KeyCodes.KEY_BACKSPACE) {
                if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                }
                return true;
            }
        }

        // Keyboard scroll navigation
        if (keyCode == KeyCodes.KEY_DOWN) {
            return mouseScrolled(-1.0);
        }
        if (keyCode == KeyCodes.KEY_UP) {
            return mouseScrolled(1.0);
        }
        if (keyCode == KeyCodes.KEY_PAGE_DOWN) {
            return mouseScrolled(-4.5);
        }
        if (keyCode == KeyCodes.KEY_PAGE_UP) {
            return mouseScrolled(4.5);
        }
        if (keyCode == KeyCodes.KEY_HOME) {
            targetScrollY = 0.0f;
            return true;
        }
        if (keyCode == KeyCodes.KEY_END) {
            targetScrollY = maxScrollY;
            return true;
        }

        if (keyCode == KeyCodes.KEY_ESCAPE || keyCode == core.getKeybindManager().getOpenGuiBind().getKeyCode()) {
            onClose(mc);
            mc.closeScreen();
            return true;
        }
        return false;
    }

    public boolean charTyped(char codePoint) {
        if (codePoint < 32 || codePoint == 127) return false;
        if (editingStringSetting != null) {
            editingStringSetting.setValue(editingStringSetting.getValue() + codePoint);
            return true;
        }
        if (searchFocused && searchQuery.length() < 40) {
            searchQuery += codePoint;
            targetScrollY = 0.0f;
            return true;
        }
        return false;
    }

    private void applyColorChannelDrag(ColorSetting cs, int ch, float norm) {
        int v = Math.max(0, Math.min(255, Math.round(norm * 255f)));
        int r = ch == 0 ? v : cs.getRed();
        int g = ch == 1 ? v : cs.getGreen();
        int b = ch == 2 ? v : cs.getBlue();
        int a = ch == 3 ? v : cs.getAlpha();
        cs.setRgba(r, g, b, a);
    }

    private void drawHeaderPill(RenderAdapter r, float x, float y, float w, float h, String text, boolean active, boolean accentOutline, float mx, float my) {
        AxsesTheme t = core.getThemeManager().getActiveTheme();
        boolean hov = isInside(mx, my, x, y, w, h);
        int bg = active
                ? core.getThemeManager().filterColor(t.getPrimaryColor())
                : (hov ? t.getElevatedColor() : t.getBackgroundColor());
        r.fillRoundedRect(x, y, w, h, 5.0f, bg);
        if (active || accentOutline || hov) {
            r.drawRoundedOutline(x, y, w, h, 5.0f, 1.0f,
                    accentOutline ? core.getThemeManager().filterColor(t.getAccentColor()) : core.getThemeManager().filterColor(t.getPrimaryColor()));
        }
        core.getFontManager().drawCentered(r, text, x + w * 0.5f, y + 7.0f, t.getTextColor(), 1.0f);
    }

    private void drawSmallButton(RenderAdapter r, float x, float y, float w, float h, String text, boolean active, float mx, float my) {
        AxsesTheme t = core.getThemeManager().getActiveTheme();
        boolean hov = isInside(mx, my, x, y, w, h);
        int bg = active
                ? core.getThemeManager().filterColor(t.getPrimaryColor())
                : (hov ? core.getThemeManager().filterColor(t.getSecondaryColor()) : t.getElevatedColor());
        r.fillRoundedRect(x, y, w, h, 4.0f, bg);
        r.drawRoundedOutline(x, y, w, h, 4.0f, 1.0f, AxsesTheme.withAlpha(t.getMutedColor(), active || hov ? 0.65f : 0.25f));
        core.getFontManager().drawCentered(r, text, x + w * 0.5f, y + (h - 8.0f) * 0.5f, t.getTextColor(), 1.0f);
    }

    private String trimToWidth(RenderAdapter r, String s, float maxW) {
        if (s == null) return "";
        if (core.getFontManager().width(r, s, 1.0f) <= maxW) return s;
        String cur = s;
        while (cur.length() > 3 && core.getFontManager().width(r, cur + "..", 1.0f) > maxW) {
            cur = cur.substring(0, cur.length() - 1);
        }
        return cur + "..";
    }

    private static boolean isInside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= (x + w) && my >= y && my <= (y + h);
    }

    public String getSearchQuery() { return searchQuery; }
    public void setSearchQuery(String q) { this.searchQuery = q == null ? "" : q; }
    public ModuleCategory getSelectedCategory() { return selectedCategory; }
    public void setSelectedCategory(ModuleCategory cat) { if (cat != null) this.selectedCategory = cat; }
}
