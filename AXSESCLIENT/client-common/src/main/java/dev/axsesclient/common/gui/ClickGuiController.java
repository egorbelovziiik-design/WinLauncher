package dev.axsesclient.common.gui;

import dev.axsesclient.common.AxsesCore;
import dev.axsesclient.common.compat.CompatibilityEngine;
import dev.axsesclient.common.modules.Module;
import dev.axsesclient.common.modules.ModuleCategory;
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
    private ModuleCategory selectedCategory = ModuleCategory.RENDER;
    private TopView activeView = TopView.MODULES;
    private Module expandedModule = null;

    private boolean searchFocused = false;
    private String searchQuery = "";
    private float targetScrollY = 0.0f;
    private float currentScrollY = 0.0f;
    private float maxScrollY = 0.0f;

    private KeybindSetting listeningKeybind = null;
    private NumberSetting draggingSlider = null;
    private float sliderDragX = 0f;
    private float sliderDragW = 100f;
    private ColorSetting activeColorPicker = null;
    private int draggingColorChannel = -1; // 0=R,1=G,2=B,3=A
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
        core.getAnimationManager().channel("clickgui:open", 0.0f, 0.20f).setImmediate(0.0f);
        core.getAnimationManager().channel("clickgui:open", 0.0f, 0.20f).setTarget(1.0f);
        listeningKeybind = null;
        draggingSlider = null;
        draggingWindow = false;
    }

    public void onClose(MinecraftAdapter mc) {
        if (!searchQuery.trim().isEmpty()) {
            core.getModuleManager().recordSearchQuery(searchQuery);
        }
        listeningKeybind = null;
        draggingSlider = null;
        editingStringSetting = null;
        draggingWindow = false;
        core.getConfigManager().saveActive();
    }

    private float getBaseWidth() { return 540.0f; }
    private float getBaseHeight() { return 330.0f; }

    public void render(RenderAdapter r, MinecraftAdapter mc, int rawMouseX, int rawMouseY, float partialTick) {
        long startNs = System.nanoTime();
        float dt = core.getAnimationManager().beginFrame();
        float openAnim = core.getAnimationManager().animate("clickgui:open", 1.0f, 0.20f);
        AxsesTheme t = core.getThemeManager().getActiveTheme();

        // Frosted Neo-Dark screen backdrop
        int backdropAlpha = Math.round(165.0f * core.getRenderManager().getBlurBackdropStrength() * openAnim);
        r.fillRect(0, 0, r.getScaledWidth(), r.getScaledHeight(), (backdropAlpha << 24) | 0x05070E);

        float uiScale = core.getConfigManager().getGuiScale();
        // Clamp scale so window always fits current resolution
        float maxFitScale = Math.min(
                (r.getScaledWidth() - 16.0f) / getBaseWidth(),
                (r.getScaledHeight() - 16.0f) / getBaseHeight()
        );
        float effectiveScale = Math.max(0.55f, Math.min(uiScale, Math.max(0.55f, maxFitScale)));
        float animScale = core.getAnimationManager().isReducedMotion()
                ? effectiveScale
                : effectiveScale * (0.94f + 0.06f * openAnim);

        float winW = getBaseWidth();
        float winH = getBaseHeight();
        float centerX = r.getScaledWidth() * 0.5f + core.getConfigManager().getGuiOffsetX();
        float centerY = r.getScaledHeight() * 0.5f + core.getConfigManager().getGuiOffsetY();
        float originX = centerX - (winW * animScale) * 0.5f;
        float originY = centerY - (winH * animScale) * 0.5f;

        float mx = (rawMouseX - originX) / animScale;
        float my = (rawMouseY - originY) / animScale;
        hoveredTooltip = null;

        r.pushTransform(originX, originY, animScale);

        // Main window container
        r.drawShadow(0, 0, winW, winH, t.getCornerRadius() + 2, t.getShadowIntensity(), 0x000000);
        r.fillRoundedRect(0, 0, winW, winH, t.getCornerRadius() + 2, AxsesTheme.withAlpha(t.getBackgroundColor(), Math.min(1.0f, t.getOpacity() + 0.04f)));
        r.drawRoundedOutline(0, 0, winW, winH, t.getCornerRadius() + 2, 1.5f, core.getThemeManager().filterColor(t.getPrimaryColor()));

        // Header bar (height 38)
        r.fillRoundedRect(2, 2, winW - 4, 36, t.getCornerRadius(), AxsesTheme.withAlpha(t.getSurfaceColor(), 0.98f));
        core.getFontManager().draw(r, "AXSESCLIENT", 14, 10, core.getThemeManager().filterColor(t.getPrimaryColor()), 1.05f);
        core.getFontManager().draw(r, "GENESIS X", 14, 23, t.getAccentColor(), 0.72f);

        // Search input box (x=122..302, y=8..30)
        float sx = 122, sy = 8, sw = 180, sh = 22;
        r.fillRoundedRect(sx, sy, sw, sh, 6, t.getElevatedColor());
        r.drawRoundedOutline(sx, sy, sw, sh, 6, 1.0f, searchFocused ? t.getAccentColor() : AxsesTheme.withAlpha(t.getMutedColor(), 0.35f));
        String searchText = searchQuery.isEmpty() && !searchFocused
                ? core.getLocalizationManager().tr("gui.search_placeholder")
                : (searchQuery + (searchFocused && (System.currentTimeMillis() / 500L) % 2L == 0L ? "|" : ""));
        core.getFontManager().draw(r, trimToWidth(r, searchText, sw - 14), sx + 7, sy + 7,
                searchQuery.isEmpty() && !searchFocused ? t.getMutedColor() : t.getTextColor(), 0.82f);

        // Top action pills: ★ Favs | Themes | Configs | Compat | HUD Editor
        drawHeaderPill(r, 308, 8, 44, 22, "★ Fav", activeView == TopView.FAVORITES, mx, my);
        drawHeaderPill(r, 355, 8, 46, 22, "Theme", activeView == TopView.THEMES, mx, my);
        drawHeaderPill(r, 404, 8, 46, 22, "Config", activeView == TopView.CONFIGS, mx, my);
        drawHeaderPill(r, 453, 8, 36, 22, "Diag", activeView == TopView.COMPATIBILITY, mx, my);
        drawHeaderPill(r, 492, 8, 40, 22, "HUD", false, mx, my);

        // Left Sidebar (x=8..116, y=44..322)
        r.fillRoundedRect(8, 44, 108, winH - 52, t.getCornerRadius(), AxsesTheme.withAlpha(t.getSurfaceColor(), 0.95f));
        ModuleCategory[] cats = ModuleCategory.values();
        float cy = 50.0f;
        for (int i = 0; i < cats.length; i++) {
            ModuleCategory cat = cats[i];
            boolean sel = (activeView == TopView.MODULES && selectedCategory == cat);
            boolean hov = isInside(mx, my, 12, cy, 100, 25);
            float hovAnim = core.getAnimationManager().smoothHover("cat:" + cat.name(), hov);
            if (sel) {
                r.fillRoundedRect(12, cy, 100, 25, 6, AxsesTheme.withAlpha(core.getThemeManager().filterColor(t.getPrimaryColor()), 0.26f));
                r.fillRect(12, cy + 5, 2.5f, 15, core.getThemeManager().filterColor(t.getAccentColor()));
            } else if (hovAnim > 0.02f) {
                r.fillRoundedRect(12, cy, 100, 25, 6, AxsesTheme.withAlpha(t.getElevatedColor(), 0.7f * hovAnim));
            }
            String title = core.getLocalizationManager().tr(cat.getTranslationKey());
            core.getFontManager().draw(r, title, 20, cy + 8, sel ? t.getTextColor() : t.getMutedColor(), 0.85f);
            cy += 28.0f;
        }

        // Sidebar bottom utility strip: Sort + Open Key indicator
        String sortLbl = "Sort: " + core.getModuleManager().getSortOrder().name().substring(0, 4);
        drawSmallButton(r, 12, winH - 48, 48, 16, sortLbl, mx, my);
        String bindLbl = listeningKeybind == core.getKeybindManager().getOpenGuiBind()
                ? "[Press..]"
                : ("GUI:" + core.getKeybindManager().getOpenGuiBind().getFormattedKey());
        drawSmallButton(r, 64, winH - 48, 48, 16, bindLbl, mx, my);

        drawSmallButton(r, 12, winH - 28, 30, 16, "A-", mx, my);
        drawSmallButton(r, 46, winH - 28, 30, 16, "A+", mx, my);
        drawSmallButton(r, 80, winH - 28, 32, 16, core.getLocalizationManager().getActiveLocale().startsWith("ru") ? "RU" : "EN", mx, my);

        // Main Content Pane (x=122..532, y=44..322)
        float contentX = 122.0f;
        float contentY = 44.0f;
        float contentW = winW - contentX - 8.0f;
        float contentH = winH - contentY - 8.0f;

        if (activeView == TopView.THEMES) {
            renderThemesPane(r, contentX, contentY, contentW, contentH, mx, my);
        } else if (activeView == TopView.CONFIGS) {
            renderConfigsPane(r, mc, contentX, contentY, contentW, contentH, mx, my);
        } else if (activeView == TopView.COMPATIBILITY) {
            renderCompatibilityPane(r, mc, contentX, contentY, contentW, contentH);
        } else {
            renderModulesPane(r, mc, contentX, contentY, contentW, contentH, mx, my, originX, originY, animScale);
        }

        // Hover tooltip
        if (hoveredTooltip != null && !hoveredTooltip.isEmpty()) {
            float tw = core.getFontManager().width(r, hoveredTooltip, 0.78f) + 12.0f;
            float tx = Math.min(winW - tw - 6.0f, Math.max(6.0f, mx + 8.0f));
            float ty = Math.max(6.0f, my - 20.0f);
            r.fillRoundedRect(tx, ty, tw, 17.0f, 4.0f, AxsesTheme.withAlpha(t.getBackgroundColor(), 0.96f));
            r.drawRoundedOutline(tx, ty, tw, 17.0f, 4.0f, 1.0f, t.getAccentColor());
            core.getFontManager().draw(r, hoveredTooltip, tx + 6.0f, ty + 4.5f, t.getTextColor(), 0.78f);
        }

        r.popTransform();

        // Render active notifications on top
        core.getRenderManager().renderNotifications(
                r,
                core.getNotificationManager(),
                dt,
                core.getAnimationManager().getAnimationSpeed(),
                core.getAnimationManager().isReducedMotion()
        );

        core.getPerformanceProfiler().recordClickGuiRenderNanos(System.nanoTime() - startNs);
    }

    private void renderModulesPane(
            RenderAdapter r,
            MinecraftAdapter mc,
            float x,
            float y,
            float w,
            float h,
            float mx,
            float my,
            float originX,
            float originY,
            float animScale
    ) {
        AxsesTheme t = core.getThemeManager().getActiveTheme();
        float topOffset = 0.0f;

        // Recent Search History quick chips when search bar focused or non-empty
        List<String> hist = core.getModuleManager().getSearchHistory();
        if ((searchFocused || !searchQuery.isEmpty()) && !hist.isEmpty()) {
            float chipX = x;
            for (int i = 0; i < Math.min(4, hist.size()); i++) {
                String chip = hist.get(i);
                float cw = Math.min(90f, core.getFontManager().width(r, chip, 0.75f) + 12f);
                drawSmallButton(r, chipX, y, cw, 15f, chip, mx, my);
                chipX += cw + 4f;
            }
            topOffset = 19.0f;
        }

        float listY = y + topOffset;
        float listH = h - topOffset;
        currentScrollY = core.getAnimationManager().smoothScroll("clickgui:modules", targetScrollY);

        int scX = Math.round(originX + x * animScale);
        int scY = Math.round(originY + listY * animScale);
        int scW = Math.max(1, Math.round(w * animScale));
        int scH = Math.max(1, Math.round(listH * animScale));
        r.pushScissor(scX, scY, scW, scH);

        List<Module> modules = core.getModuleManager().query(
                selectedCategory,
                activeView == TopView.FAVORITES,
                searchQuery
        );

        float cursorY = listY - currentScrollY;
        float totalContentHeight = 0.0f;

        for (int i = 0; i < modules.size(); i++) {
            Module m = modules.get(i);
            boolean expanded = (expandedModule == m);
            float cardH = 34.0f;
            if (expanded) {
                cardH += 8.0f + countSettingsHeight(m);
            }

            if (cursorY + cardH >= listY - 8.0f && cursorY <= listY + listH + 8.0f) {
                boolean cardHov = isInside(mx, my, x, cursorY, w - 6f, 34f) && isInside(mx, my, x, listY, w, listH);
                float hovAnim = core.getAnimationManager().smoothHover("mod:" + m.getId(), cardHov);
                core.getRenderManager().drawCard(r, x, cursorY, w - 6f, cardH, m.isEnabled(), hovAnim);

                String incomp = m.getIncompatibilityReason(mc);
                if (cardHov) {
                    hoveredTooltip = incomp != null ? ("⚠ " + incomp) : m.getDescription();
                }

                // Favorite star
                int starCol = m.isFavorite() ? t.getWarningColor() : t.getMutedColor();
                core.getFontManager().draw(r, m.isFavorite() ? "★" : "☆", x + 8, cursorY + 8, starCol, 0.90f);

                // Name + description
                core.getFontManager().draw(r, m.getName(), x + 22, cursorY + 6,
                        incomp != null ? t.getMutedColor() : t.getTextColor(), 0.92f);
                core.getFontManager().draw(r, trimToWidth(r, incomp != null ? incomp : m.getDescription(), w - 135f),
                        x + 22, cursorY + 19, incomp != null ? t.getWarningColor() : t.getMutedColor(), 0.72f);

                // Keybind badge
                String kbText = (listeningKeybind == m.getKeybind()) ? "[...]" : ("[" + m.getKeybind().getFormattedKey() + "]");
                drawSmallButton(r, x + w - 102f, cursorY + 9, 44f, 16f, kbText, mx, my);

                // Expand gear indicator
                core.getFontManager().draw(r, expanded ? "▾" : "▸", x + w - 52f, cursorY + 11, t.getMutedColor(), 0.90f);

                // Animated Toggle Switch
                float togAnim = core.getAnimationManager().smoothToggle("mod:" + m.getId(), m.isEnabled());
                core.getRenderManager().drawToggleSwitch(r, x + w - 38f, cursorY + 9, 26f, 15f, togAnim);

                if (expanded) {
                    renderModuleSettings(r, m, x + 12f, cursorY + 38f, w - 26f, mx, my);
                }
            }

            cursorY += cardH + 6.0f;
            totalContentHeight += cardH + 6.0f;
        }

        maxScrollY = Math.max(0.0f, totalContentHeight - listH);
        targetScrollY = Math.max(0.0f, Math.min(maxScrollY, targetScrollY));

        r.popScissor();

        // Scrollbar track
        if (maxScrollY > 1.0f) {
            float barH = Math.max(18.0f, listH * (listH / (totalContentHeight + 1.0f)));
            float barY = listY + (listH - barH) * (currentScrollY / maxScrollY);
            r.fillRoundedRect(x + w - 3.5f, barY, 2.5f, barH, 1.2f, AxsesTheme.withAlpha(t.getAccentColor(), 0.7f));
        }
    }

    private float countSettingsHeight(Module m) {
        float h = 20.0f; // Reset module button row
        for (Setting<?> s : m.getSettings()) {
            if (s instanceof ColorSetting && activeColorPicker == s) {
                h += 70.0f;
            } else if (s instanceof MultiSelectSetting) {
                h += 32.0f;
            } else {
                h += 22.0f;
            }
        }
        return h;
    }

    private void renderModuleSettings(RenderAdapter r, Module m, float sx, float sy, float sw, float mx, float my) {
        AxsesTheme t = core.getThemeManager().getActiveTheme();
        float rowY = sy;

        for (Setting<?> s : m.getSettings()) {
            core.getFontManager().draw(r, s.getName(), sx, rowY + 4, t.getTextColor(), 0.80f);

            if (s instanceof BooleanSetting) {
                BooleanSetting bs = (BooleanSetting) s;
                float anim = core.getAnimationManager().smoothToggle(m.getId() + "." + s.getId(), bs.isEnabled());
                core.getRenderManager().drawToggleSwitch(r, sx + sw - 28f, rowY + 2f, 24f, 13f, anim);
                rowY += 22.0f;
            } else if (s instanceof NumberSetting) {
                NumberSetting ns = (NumberSetting) s;
                String valTxt = ns.formatValue();
                core.getFontManager().draw(r, valTxt, sx + sw - 148f, rowY + 4f, t.getAccentColor(), 0.78f);
                core.getRenderManager().drawSlider(r, sx + sw - 110f, rowY + 3f, 106f, 12f, ns.getNormalized());
                rowY += 22.0f;
            } else if (s instanceof ModeSetting) {
                ModeSetting ms = (ModeSetting) s;
                drawSmallButton(r, sx + sw - 110f, rowY + 1f, 106f, 16f, "◂ " + ms.getValue() + " ▸", mx, my);
                rowY += 22.0f;
            } else if (s instanceof KeybindSetting) {
                KeybindSetting ks = (KeybindSetting) s;
                String lbl = (listeningKeybind == ks) ? "[Press Key...]" : ks.getFormattedKey();
                drawSmallButton(r, sx + sw - 90f, rowY + 1f, 86f, 16f, lbl, mx, my);
                rowY += 22.0f;
            } else if (s instanceof StringSetting) {
                StringSetting ss = (StringSetting) s;
                boolean ed = (editingStringSetting == ss);
                r.fillRoundedRect(sx + sw - 140f, rowY + 1f, 136f, 16f, 4f, t.getBackgroundColor());
                r.drawRoundedOutline(sx + sw - 140f, rowY + 1f, 136f, 16f, 4f, 1f, ed ? t.getAccentColor() : t.getMutedColor());
                String val = ss.getValue() + (ed ? "|" : "");
                core.getFontManager().draw(r, trimToWidth(r, val, 128f), sx + sw - 135f, rowY + 4.5f, t.getTextColor(), 0.76f);
                rowY += 22.0f;
            } else if (s instanceof ColorSetting) {
                ColorSetting cs = (ColorSetting) s;
                r.fillRoundedRect(sx + sw - 82f, rowY + 2f, 22f, 14f, 4f, cs.getArgb());
                r.drawRoundedOutline(sx + sw - 82f, rowY + 2f, 22f, 14f, 4f, 1f, t.getTextColor());
                drawSmallButton(r, sx + sw - 56f, rowY + 1f, 52f, 16f, cs.toRgbHex(), mx, my);
                rowY += 22.0f;
                if (activeColorPicker == cs) {
                    String[] chNames = {"R", "G", "B", "A"};
                    int[] vals = {cs.getRed(), cs.getGreen(), cs.getBlue(), cs.getAlpha()};
                    for (int c = 0; c < 4; c++) {
                        core.getFontManager().draw(r, chNames[c] + ":" + vals[c], sx + 8f, rowY + 2f, t.getMutedColor(), 0.72f);
                        core.getRenderManager().drawSlider(r, sx + 48f, rowY + 1f, sw - 56f, 10f, vals[c] / 255.0f);
                        rowY += 12.0f;
                    }
                }
            } else if (s instanceof MultiSelectSetting) {
                MultiSelectSetting mss = (MultiSelectSetting) s;
                float cx = sx + 95f;
                for (String opt : mss.getOptions()) {
                    boolean on = mss.isSelected(opt);
                    float ow = core.getFontManager().width(r, opt, 0.72f) + 10f;
                    r.fillRoundedRect(cx, rowY + 2f, ow, 15f, 4f, on ? t.getPrimaryColor() : t.getBackgroundColor());
                    core.getFontManager().draw(r, opt, cx + 5f, rowY + 5f, t.getTextColor(), 0.72f);
                    cx += ow + 4f;
                }
                rowY += 32.0f;
            }
        }

        drawSmallButton(r, sx + sw - 88f, rowY + 1f, 84f, 15f, "Reset Module", mx, my);
    }

    private void renderThemesPane(RenderAdapter r, float x, float y, float w, float h, float mx, float my) {
        AxsesTheme active = core.getThemeManager().getActiveTheme();
        core.getFontManager().draw(r, "AXSES NEO-DARK THEME ENGINE (8 Built-in + Custom)", x, y + 2, active.getTextColor(), 0.92f);

        List<AxsesTheme> list = core.getThemeManager().getAllThemes();
        float cardW = (w - 14f) * 0.5f;
        float cardH = 44f;
        for (int i = 0; i < Math.min(8, list.size()); i++) {
            AxsesTheme item = list.get(i);
            int col = i % 2;
            int row = i / 2;
            float cx = x + col * (cardW + 8f);
            float cy = y + 20f + row * (cardH + 6f);
            boolean isCurrent = item.getId().equals(core.getThemeManager().getActiveThemeId());
            r.fillRoundedRect(cx, cy, cardW, cardH, 8f, item.getSurfaceColor());
            r.drawRoundedOutline(cx, cy, cardW, cardH, 8f, isCurrent ? 1.8f : 1.0f, isCurrent ? item.getAccentColor() : item.getPrimaryColor());
            core.getFontManager().draw(r, item.getName(), cx + 8f, cy + 7f, item.getTextColor(), 0.85f);
            // Color swatches
            r.fillRoundedRect(cx + 8f, cy + 23f, 16f, 12f, 3f, item.getPrimaryColor());
            r.fillRoundedRect(cx + 28f, cy + 23f, 16f, 12f, 3f, item.getSecondaryColor());
            r.fillRoundedRect(cx + 48f, cy + 23f, 16f, 12f, 3f, item.getAccentColor());
            core.getFontManager().draw(r, isCurrent ? "ACTIVE" : "Apply", cx + cardW - 44f, cy + 25f, item.getAccentColor(), 0.76f);
        }

        float btnY = y + h - 24f;
        drawSmallButton(r, x, btnY, 96f, 18f, "+ Custom Theme", mx, my);
        drawSmallButton(r, x + 102f, btnY, 92f, 18f, "Export Theme", mx, my);
        drawSmallButton(r, x + 200f, btnY, 92f, 18f, "Reset Themes", mx, my);
    }

    private void renderConfigsPane(RenderAdapter r, MinecraftAdapter mc, float x, float y, float w, float h, float mx, float my) {
        AxsesTheme t = core.getThemeManager().getActiveTheme();
        core.getFontManager().draw(r, "CONFIG & BACKUP MANAGER — Active: " + core.getConfigManager().getActiveProfile(), x, y + 2, t.getTextColor(), 0.92f);

        List<String> profiles = core.getConfigManager().listProfiles();
        float py = y + 22f;
        for (int i = 0; i < Math.min(6, profiles.size()); i++) {
            String prof = profiles.get(i);
            boolean cur = prof.equals(core.getConfigManager().getActiveProfile());
            r.fillRoundedRect(x, py, w - 8f, 24f, 6f, cur ? t.getElevatedColor() : t.getSurfaceColor());
            r.drawRoundedOutline(x, py, w - 8f, 24f, 6f, 1f, cur ? t.getPrimaryColor() : AxsesTheme.withAlpha(t.getMutedColor(), 0.25f));
            core.getFontManager().draw(r, prof + (cur ? " (Loaded)" : ""), x + 10f, py + 7f, t.getTextColor(), 0.84f);
            drawSmallButton(r, x + w - 108f, py + 4f, 44f, 16f, "Load", mx, my);
            drawSmallButton(r, x + w - 58f, py + 4f, 44f, 16f, "Save", mx, my);
            py += 28f;
        }

        float btnY = y + h - 24f;
        drawSmallButton(r, x, btnY, 88f, 18f, "+ New Profile", mx, my);
        drawSmallButton(r, x + 94f, btnY, 88f, 18f, "Create Backup", mx, my);
        drawSmallButton(r, x + 188f, btnY, 98f, 18f, "Restore Backup", mx, my);
        drawSmallButton(r, x + 292f, btnY, 96f, 18f, "Reset Defaults", mx, my);
    }

    private void renderCompatibilityPane(RenderAdapter r, MinecraftAdapter mc, float x, float y, float w, float h) {
        AxsesTheme t = core.getThemeManager().getActiveTheme();
        CompatibilityEngine.CompatibilityReport rep = core.getCompatibilityEngine().evaluate(
                mc, core.getInputAdapter(), core.getModuleManager(), core.getKeybindManager()
        );
        core.getFontManager().draw(r, "COMPATIBILITY & TELEMETRY REPORT", x, y + 2, t.getAccentColor(), 0.92f);
        float ly = y + 22f;
        String[] lines = new String[] {
                "Minecraft Version: " + rep.getMinecraftVersion() + (rep.isMinecraftSupported() ? " [VERIFIED]" : " [UNVERIFIED]"),
                "Java Runtime: " + rep.getJavaVersion() + (rep.isJavaCompatible() ? " [COMPATIBLE]" : " [CHECK VERSION]"),
                "Fabric Loader: " + rep.getFabricLoaderVersion() + " | Fabric API: " + rep.getFabricApiVersion(),
                "Mappings: " + rep.getMappingsInfo(),
                "Loaded Mods (" + rep.getLoadedMods().size() + "): " + trimToWidth(r, String.valueOf(rep.getLoadedMods()), w - 120f),
                "Keybind Conflicts: " + (rep.getKeybindConflicts().isEmpty() ? "None (0)" : rep.getKeybindConflicts().get(0)),
                "Server Fair-Play Restrictions: " + (rep.getRestrictedModules().isEmpty() ? "All modules available" : (rep.getRestrictedModules().size() + " SP-only gated")),
                core.getPerformanceProfiler().formatTelemetrySummary(mc != null ? mc.getFps() : 60)
        };
        for (String line : lines) {
            r.fillRoundedRect(x, ly, w - 8f, 22f, 5f, t.getSurfaceColor());
            core.getFontManager().draw(r, line, x + 8f, ly + 6.5f, t.getTextColor(), 0.78f);
            ly += 26f;
        }
    }

    public boolean mouseClicked(MinecraftAdapter mc, double rawX, double rawY, int button) {
        float[] local = toLocalCoords(mc, rawX, rawY);
        float mx = local[0];
        float my = local[1];
        float winW = getBaseWidth();
        float winH = getBaseHeight();

        searchFocused = isInside(mx, my, 122, 8, 180, 22);
        editingStringSetting = null;

        // Header drag (x=0..120, y=0..38)
        if (button == 0 && isInside(mx, my, 0, 0, 118, 38)) {
            draggingWindow = true;
            dragStartMouseX = (float) rawX;
            dragStartMouseY = (float) rawY;
            dragStartOffsetX = core.getConfigManager().getGuiOffsetX();
            dragStartOffsetY = core.getConfigManager().getGuiOffsetY();
            return true;
        }

        // Header pills
        if (isInside(mx, my, 308, 8, 44, 22)) { activeView = TopView.FAVORITES; core.getAnimationManager().pageTransition("fav"); return true; }
        if (isInside(mx, my, 355, 8, 46, 22)) { activeView = TopView.THEMES; core.getAnimationManager().pageTransition("themes"); return true; }
        if (isInside(mx, my, 404, 8, 46, 22)) { activeView = TopView.CONFIGS; core.getAnimationManager().pageTransition("configs"); return true; }
        if (isInside(mx, my, 453, 8, 36, 22)) { activeView = TopView.COMPATIBILITY; core.getAnimationManager().pageTransition("compat"); return true; }
        if (isInside(mx, my, 492, 8, 40, 22)) { mc.openHudEditor(); return true; }

        // Sidebar categories
        ModuleCategory[] cats = ModuleCategory.values();
        float cy = 50.0f;
        for (ModuleCategory cat : cats) {
            if (isInside(mx, my, 12, cy, 100, 25)) {
                selectedCategory = cat;
                activeView = TopView.MODULES;
                targetScrollY = 0.0f;
                core.getAnimationManager().pageTransition("cat:" + cat.name());
                return true;
            }
            cy += 28.0f;
        }

        // Sidebar bottom controls
        if (isInside(mx, my, 12, winH - 48, 48, 16)) { core.getModuleManager().cycleSortOrder(); return true; }
        if (isInside(mx, my, 64, winH - 48, 48, 16)) { listeningKeybind = core.getKeybindManager().getOpenGuiBind(); return true; }
        if (isInside(mx, my, 12, winH - 28, 30, 16)) { core.getConfigManager().setGuiScale(core.getConfigManager().getGuiScale() - 0.10f); return true; }
        if (isInside(mx, my, 46, winH - 28, 30, 16)) { core.getConfigManager().setGuiScale(core.getConfigManager().getGuiScale() + 0.10f); return true; }
        if (isInside(mx, my, 80, winH - 28, 32, 16)) { core.getLocalizationManager().toggleLocale(); return true; }

        float contentX = 122.0f, contentY = 44.0f, contentW = winW - 130.0f, contentH = winH - 52.0f;

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

    private boolean handleThemesClick(MinecraftAdapter mc, float x, float y, float w, float h, float mx, float my) {
        List<AxsesTheme> list = core.getThemeManager().getAllThemes();
        float cardW = (w - 14f) * 0.5f;
        float cardH = 44f;
        for (int i = 0; i < Math.min(8, list.size()); i++) {
            float cx = x + (i % 2) * (cardW + 8f);
            float cy = y + 20f + (i / 2) * (cardH + 6f);
            if (isInside(mx, my, cx, cy, cardW, cardH)) {
                AxsesTheme chosen = list.get(i);
                core.getThemeManager().setActiveTheme(chosen.getId());
                core.getConfigManager().saveActive();
                core.getNotificationManager().push("Theme Applied", chosen.getName(), NotificationManager.Severity.SUCCESS);
                return true;
            }
        }
        float btnY = y + h - 24f;
        if (isInside(mx, my, x, btnY, 96f, 18f)) {
            AxsesTheme created = core.getThemeManager().createCustomTheme("Custom " + (list.size() + 1), core.getThemeManager().getActiveThemeId());
            core.getThemeManager().setActiveTheme(created.getId());
            core.getNotificationManager().push("Theme Created", created.getName(), NotificationManager.Severity.SUCCESS);
            return true;
        }
        if (isInside(mx, my, x + 102f, btnY, 92f, 18f) && mc != null && mc.getGameDir() != null) {
            try {
                String id = core.getThemeManager().getActiveThemeId();
                core.getThemeManager().exportTheme(id, mc.getGameDir().resolve("axsesclient").resolve("exported-" + id + ".axtheme.json"));
                core.getNotificationManager().push("Theme Exported", "exported-" + id + ".axtheme.json", NotificationManager.Severity.SUCCESS);
            } catch (Exception ignored) {
            }
            return true;
        }
        if (isInside(mx, my, x + 200f, btnY, 92f, 18f)) {
            core.getThemeManager().resetThemes();
            core.getNotificationManager().push("Themes Reset", "Restored AXSES DARK", NotificationManager.Severity.INFO);
            return true;
        }
        return false;
    }

    private boolean handleConfigsClick(MinecraftAdapter mc, float x, float y, float w, float h, float mx, float my) {
        List<String> profiles = core.getConfigManager().listProfiles();
        float py = y + 22f;
        for (int i = 0; i < Math.min(6, profiles.size()); i++) {
            String prof = profiles.get(i);
            if (isInside(mx, my, x + w - 108f, py + 4f, 44f, 16f)) {
                core.getConfigManager().load(prof, mc);
                core.getNotificationManager().push("Profile Loaded", prof, NotificationManager.Severity.SUCCESS);
                return true;
            }
            if (isInside(mx, my, x + w - 58f, py + 4f, 44f, 16f)) {
                core.getConfigManager().save(prof);
                core.getNotificationManager().push("Profile Saved", prof, NotificationManager.Severity.SUCCESS);
                return true;
            }
            py += 28f;
        }
        float btnY = y + h - 24f;
        if (isInside(mx, my, x, btnY, 88f, 18f)) {
            String name = "profile_" + (profiles.size() + 1);
            core.getConfigManager().create(name, mc);
            core.getNotificationManager().push("Profile Created", name, NotificationManager.Severity.SUCCESS);
            return true;
        }
        if (isInside(mx, my, x + 94f, btnY, 88f, 18f)) {
            core.getConfigManager().createBackup();
            return true;
        }
        if (isInside(mx, my, x + 188f, btnY, 98f, 18f)) {
            List<String> backups = core.getConfigManager().listBackups();
            if (!backups.isEmpty()) {
                core.getConfigManager().restoreBackup(backups.get(0), mc);
                core.getNotificationManager().push("Backup Restored", backups.get(0), NotificationManager.Severity.SUCCESS);
            }
            return true;
        }
        if (isInside(mx, my, x + 292f, btnY, 96f, 18f)) {
            core.getModuleManager().resetAll(mc);
            core.getHudManager().resetAll();
            core.getConfigManager().saveActive();
            core.getNotificationManager().push("Factory Defaults", "All modules and HUD reset", NotificationManager.Severity.INFO);
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
                float cw = Math.min(90f, chip.length() * 5.2f + 12f);
                if (isInside(mx, my, chipX, y, cw, 15f)) {
                    searchQuery = chip;
                    return true;
                }
                chipX += cw + 4f;
            }
            topOffset = 19.0f;
        }

        float listY = y + topOffset;
        float listH = h - topOffset;
        if (!isInside(mx, my, x, listY, w, listH)) {
            return false;
        }

        List<Module> modules = core.getModuleManager().query(selectedCategory, activeView == TopView.FAVORITES, searchQuery);
        float cursorY = listY - currentScrollY;

        for (Module m : modules) {
            boolean expanded = (expandedModule == m);
            float cardH = 34.0f + (expanded ? (8.0f + countSettingsHeight(m)) : 0.0f);

            if (isInside(mx, my, x, cursorY, w - 6f, 34f)) {
                // Star click
                if (isInside(mx, my, x + 4f, cursorY + 4f, 16f, 24f)) {
                    m.toggleFavorite();
                    return true;
                }
                // Keybind pill click
                if (isInside(mx, my, x + w - 102f, cursorY + 9f, 44f, 16f)) {
                    listeningKeybind = m.getKeybind();
                    return true;
                }
                // Right click or expand arrow click opens settings
                if (button == 1 || isInside(mx, my, x + w - 58f, cursorY + 6f, 18f, 22f)) {
                    expandedModule = (expandedModule == m) ? null : m;
                    return true;
                }
                // Toggle module
                core.getModuleManager().toggleModuleWithFeedback(m, mc);
                return true;
            }

            if (expanded && isInside(mx, my, x, cursorY + 34f, w - 6f, cardH - 34f)) {
                return handleModuleSettingsClick(mc, m, x + 12f, cursorY + 38f, w - 26f, mx, my, button);
            }
            cursorY += cardH + 6.0f;
        }
        return false;
    }

    private boolean handleModuleSettingsClick(MinecraftAdapter mc, Module m, float sx, float sy, float sw, float mx, float my, int button) {
        float rowY = sy;
        for (Setting<?> s : m.getSettings()) {
            if (s instanceof BooleanSetting) {
                if (isInside(mx, my, sx, rowY, sw, 20f)) {
                    ((BooleanSetting) s).toggle();
                    return true;
                }
                rowY += 22f;
            } else if (s instanceof NumberSetting) {
                NumberSetting ns = (NumberSetting) s;
                float slX = sx + sw - 110f;
                if (isInside(mx, my, slX - 4f, rowY, 114f, 20f)) {
                    draggingSlider = ns;
                    sliderDragX = slX;
                    sliderDragW = 106f;
                    ns.setFromNormalized((mx - slX) / 106f);
                    return true;
                }
                rowY += 22f;
            } else if (s instanceof ModeSetting) {
                ModeSetting ms = (ModeSetting) s;
                if (isInside(mx, my, sx + sw - 110f, rowY + 1f, 106f, 18f)) {
                    if (button == 1) ms.cyclePrevious(); else ms.cycleNext();
                    return true;
                }
                rowY += 22f;
            } else if (s instanceof KeybindSetting) {
                if (isInside(mx, my, sx + sw - 90f, rowY + 1f, 86f, 18f)) {
                    listeningKeybind = (KeybindSetting) s;
                    return true;
                }
                rowY += 22f;
            } else if (s instanceof StringSetting) {
                if (isInside(mx, my, sx + sw - 140f, rowY + 1f, 136f, 18f)) {
                    editingStringSetting = (StringSetting) s;
                    return true;
                }
                rowY += 22f;
            } else if (s instanceof ColorSetting) {
                ColorSetting cs = (ColorSetting) s;
                if (isInside(mx, my, sx + sw - 86f, rowY, 84f, 20f)) {
                    activeColorPicker = (activeColorPicker == cs) ? null : cs;
                    return true;
                }
                rowY += 22f;
                if (activeColorPicker == cs) {
                    for (int c = 0; c < 4; c++) {
                        if (isInside(mx, my, sx + 48f, rowY, sw - 56f, 12f)) {
                            draggingColorChannel = c;
                            activeColorPicker = cs;
                            sliderDragX = sx + 48f;
                            sliderDragW = sw - 56f;
                            applyColorChannelDrag(cs, c, (mx - sliderDragX) / sliderDragW);
                            return true;
                        }
                        rowY += 12f;
                    }
                }
            } else if (s instanceof MultiSelectSetting) {
                MultiSelectSetting mss = (MultiSelectSetting) s;
                float cx = sx + 95f;
                for (String opt : mss.getOptions()) {
                    float ow = opt.length() * 4.8f + 12f;
                    if (isInside(mx, my, cx, rowY, ow, 18f)) {
                        mss.toggleOption(opt);
                        return true;
                    }
                    cx += ow + 4f;
                }
                rowY += 32f;
            }
        }
        if (isInside(mx, my, sx + sw - 88f, rowY, 86f, 17f)) {
            m.resetSettings(mc);
            core.getNotificationManager().push(m.getName(), "Settings reset to defaults", NotificationManager.Severity.INFO);
            return true;
        }
        return false;
    }

    public boolean mouseDragged(MinecraftAdapter mc, double rawX, double rawY, int button) {
        if (draggingWindow) {
            core.getConfigManager().setGuiOffsetX(dragStartOffsetX + ((float) rawX - dragStartMouseX));
            core.getConfigManager().setGuiOffsetY(dragStartOffsetY + ((float) rawY - dragStartMouseY));
            return true;
        }
        float[] local = toLocalCoords(mc, rawX, rawY);
        if (draggingSlider != null) {
            draggingSlider.setFromNormalized((local[0] - sliderDragX) / sliderDragW);
            return true;
        }
        if (draggingColorChannel >= 0 && activeColorPicker != null) {
            applyColorChannelDrag(activeColorPicker, draggingColorChannel, (local[0] - sliderDragX) / sliderDragW);
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double rawX, double rawY, int button) {
        draggingWindow = false;
        draggingSlider = null;
        draggingColorChannel = -1;
        return false;
    }

    public boolean mouseScrolled(double verticalAmount) {
        targetScrollY = Math.max(0.0f, Math.min(maxScrollY, targetScrollY - (float) verticalAmount * 22.0f));
        return true;
    }

    public boolean keyPressed(MinecraftAdapter mc, int keyCode) {
        if (listeningKeybind != null) {
            if (keyCode == KeyCodes.KEY_ESCAPE) {
                listeningKeybind = null;
            } else if (keyCode == KeyCodes.KEY_DELETE || keyCode == KeyCodes.KEY_BACKSPACE) {
                listeningKeybind.remove();
                listeningKeybind = null;
            } else {
                listeningKeybind.assign(keyCode);
                listeningKeybind = null;
            }
            return true;
        }

        if (editingStringSetting != null) {
            if (keyCode == KeyCodes.KEY_ENTER || keyCode == KeyCodes.KEY_ESCAPE) {
                editingStringSetting = null;
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

        // Close ClickGUI on ESCAPE or Open-ClickGUI key (default RIGHT SHIFT)
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

    private float[] toLocalCoords(MinecraftAdapter mc, double rawX, double rawY) {
        float sw = mc != null ? mc.getScaledWidth() : 854f;
        float sh = mc != null ? mc.getScaledHeight() : 480f;
        float uiScale = core.getConfigManager().getGuiScale();
        float maxFit = Math.min((sw - 16f) / getBaseWidth(), (sh - 16f) / getBaseHeight());
        float s = Math.max(0.55f, Math.min(uiScale, Math.max(0.55f, maxFit)));
        float ox = (sw * 0.5f + core.getConfigManager().getGuiOffsetX()) - (getBaseWidth() * s) * 0.5f;
        float oy = (sh * 0.5f + core.getConfigManager().getGuiOffsetY()) - (getBaseHeight() * s) * 0.5f;
        return new float[] { ((float) rawX - ox) / s, ((float) rawY - oy) / s };
    }

    private void drawHeaderPill(RenderAdapter r, float x, float y, float w, float h, String text, boolean active, float mx, float my) {
        AxsesTheme t = core.getThemeManager().getActiveTheme();
        boolean hov = isInside(mx, my, x, y, w, h);
        int bg = active ? core.getThemeManager().filterColor(t.getPrimaryColor()) : (hov ? t.getElevatedColor() : t.getBackgroundColor());
        r.fillRoundedRect(x, y, w, h, 6f, bg);
        core.getFontManager().drawCentered(r, text, x + w * 0.5f, y + 7f, t.getTextColor(), 0.76f);
    }

    private void drawSmallButton(RenderAdapter r, float x, float y, float w, float h, String text, float mx, float my) {
        AxsesTheme t = core.getThemeManager().getActiveTheme();
        boolean hov = isInside(mx, my, x, y, w, h);
        r.fillRoundedRect(x, y, w, h, 4f, hov ? core.getThemeManager().filterColor(t.getSecondaryColor()) : t.getElevatedColor());
        core.getFontManager().drawCentered(r, text, x + w * 0.5f, y + (h - 7f) * 0.5f, t.getTextColor(), 0.72f);
    }

    private String trimToWidth(RenderAdapter r, String s, float maxW) {
        if (s == null) return "";
        if (core.getFontManager().width(r, s, 0.80f) <= maxW) return s;
        String cur = s;
        while (cur.length() > 4 && core.getFontManager().width(r, cur + "..", 0.80f) > maxW) {
            cur = cur.substring(0, cur.length() - 1);
        }
        return cur.toLowerCase(Locale.ROOT).equals(s.toLowerCase(Locale.ROOT)) ? cur : (cur + "..");
    }

    private static boolean isInside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= (x + w) && my >= y && my <= (y + h);
    }

    public String getSearchQuery() { return searchQuery; }
    public void setSearchQuery(String q) { this.searchQuery = q == null ? "" : q; }
    public ModuleCategory getSelectedCategory() { return selectedCategory; }
    public void setSelectedCategory(ModuleCategory cat) { if (cat != null) this.selectedCategory = cat; }
}
