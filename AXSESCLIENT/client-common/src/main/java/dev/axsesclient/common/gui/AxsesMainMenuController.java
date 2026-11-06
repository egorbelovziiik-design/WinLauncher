package dev.axsesclient.common.gui;

import dev.axsesclient.common.AxsesCore;
import dev.axsesclient.common.notifications.NotificationManager;
import dev.axsesclient.common.platform.KeyCodes;
import dev.axsesclient.common.platform.MinecraftAdapter;
import dev.axsesclient.common.platform.RenderAdapter;
import dev.axsesclient.common.render.RenderManager;
import dev.axsesclient.common.theme.AxsesTheme;

public final class AxsesMainMenuController {
    private static final String[] BUTTON_IDS = {
            "singleplayer", "multiplayer", "settings", "axses_gui", "accounts", "quit"
    };
    private static final String[] BUTTON_ICONS = {
            "▶ ", "⚡ ", "⚙ ", "✦ ", "◈ ", "✕ "
    };

    private final AxsesCore core;
    private boolean showAccountsModal = false;
    private float bgPhase = 1.25f;

    public AxsesMainMenuController(AxsesCore core) {
        this.core = core;
    }

    public void render(RenderAdapter r, MinecraftAdapter mc, int mouseX, int mouseY, float partialTick) {
        float dt = core.getAnimationManager().beginFrame();
        if (!core.getAnimationManager().isReducedMotion()) {
            bgPhase += dt * 0.65f * core.getAnimationManager().getAnimationSpeed();
        }

        AxsesTheme t = core.getThemeManager().getActiveTheme();
        int sw = r.getScaledWidth();
        int sh = r.getScaledHeight();
        int prim = core.getThemeManager().filterColor(t.getPrimaryColor());
        int acc = core.getThemeManager().filterColor(t.getAccentColor());
        boolean isRu = core.getLocalizationManager().isRussian();

        // 1. Deep Cosmic Obsidian Sky + Horizon Floor
        float horizonY = sh * 0.68f;
        r.fillGradientVertical(0, 0, sw, horizonY, t.getBackgroundColor(), t.getSurfaceColor());
        r.fillGradientVertical(0, horizonY, sw, sh - horizonY, t.getSurfaceColor(), t.getBackgroundColor());

        // 2. Animated Multi-Ribbon Aurora Borealis Curtains in Sky
        int bands = 42;
        float bandW = (float) sw / (float) bands;
        for (int i = 0; i < bands; i++) {
            double xNorm = (double) i / (double) bands;
            double wave1 = Math.sin(xNorm * 5.2 + bgPhase * 1.1) * 26.0 + Math.cos(xNorm * 9.0 - bgPhase * 0.7) * 12.0;
            double wave2 = Math.cos(xNorm * 4.4 - bgPhase * 0.9) * 22.0;
            float ay1 = (float) (sh * 0.16 + wave1);
            float ah1 = (float) (54.0 + 24.0 * Math.sin(xNorm * 3.1 + bgPhase));
            int col1 = RenderManager.blendArgb(prim, acc, (float) xNorm);
            r.fillGradientVertical(i * bandW, ay1, bandW + 1.0f, ah1,
                    AxsesTheme.withAlpha(col1, 0.22f), 0x00000000);

            float ay2 = (float) (sh * 0.28 + wave2);
            r.fillGradientVertical(i * bandW, ay2, bandW + 1.0f, 38.0f,
                    AxsesTheme.withAlpha(acc, 0.14f), 0x00000000);
        }

        // 3. Subtle Twinkling Starfield Dots
        for (int s = 0; s < 36; s++) {
            float sx = ((s * 173 + 41) % 997) / 997.0f * sw;
            float sy = ((s * 311 + 19) % 613) / 613.0f * (horizonY - 20.0f);
            float pulse = (float) (0.35 + 0.65 * Math.abs(Math.sin(bgPhase * 1.5 + s)));
            float sz = (s % 5 == 0) ? 2.0f : 1.2f;
            r.fillRect(sx, sy, sz, sz, AxsesTheme.withAlpha(0xFFFFFFFF, 0.45f * pulse));
        }

        // 4. Perspective Cyber-Horizon Grid below Command Deck
        r.fillRect(0, horizonY, sw, 1.2f, AxsesTheme.withAlpha(acc, 0.32f));
        for (int row = 1; row <= 6; row++) {
            float gy = horizonY + (sh - horizonY) * (float) Math.pow(row / 6.0, 1.65);
            r.fillRect(0, gy, sw, 1.0f, AxsesTheme.withAlpha(prim, 0.10f));
        }
        for (int col = -8; col <= 8; col++) {
            float topX = sw * 0.5f + col * 24.0f;
            float botX = sw * 0.5f + col * 92.0f;
            r.drawLine(topX, horizonY, botX, sh, 1.0f, AxsesTheme.withAlpha(prim, 0.10f));
        }

        // 5. Top Status & Quick Switcher Stadium Pills
        String engineTag = isRu ? "⚡ VISION v3.5 · 108 МОДУЛЕЙ · 14 ТЕМ" : "⚡ VISION v3.5 · 108 MODULES · 14 THEMES";
        float ew = core.getFontManager().width(r, engineTag, 0.88f) + 18.0f;
        r.fillRoundedRect(10f, 10f, ew, 20f, 10.0f, 0xDC080B18);
        r.drawRoundedOutline(10f, 10f, ew, 20f, 10.0f, 1.0f, AxsesTheme.withAlpha(prim, 0.72f));
        core.getFontManager().draw(r, engineTag, 19f, 16f, acc, 0.88f);

        String langPill = isRu ? "Язык: Русский" : "Lang: English";
        float lw = Math.max(98f, core.getFontManager().width(r, langPill, 0.92f) + 18f);
        float lx = sw - lw - 10f;
        float ly = 10f;
        boolean lHov = isInside(mouseX, mouseY, lx, ly, lw, 20f);
        r.fillRoundedRect(lx, ly, lw, 20f, 10.0f, lHov ? t.getElevatedColor() : 0xDC080B18);
        r.drawRoundedOutline(lx, ly, lw, 20f, 10.0f, 1f, acc);
        core.getFontManager().drawCentered(r, langPill, lx + lw * 0.5f, ly + 6f, 0xFFFFFFFF, 0.92f);

        String themePill = t.getName();
        float tw = Math.max(98f, core.getFontManager().width(r, themePill, 0.88f) + 18f);
        float tx = lx - tw - 6f;
        boolean tHov = isInside(mouseX, mouseY, tx, ly, tw, 20f);
        r.fillRoundedRect(tx, ly, tw, 20f, 10.0f, tHov ? t.getElevatedColor() : 0xDC080B18);
        r.drawRoundedOutline(tx, ly, tw, 20f, 10.0f, 1f, AxsesTheme.withAlpha(prim, 0.72f));
        core.getFontManager().drawCentered(r, themePill, tx + tw * 0.5f, ly + 6f, t.getTextColor(), 0.88f);

        // 6. Central Curved Vision-Glass Dual-Column Command Deck (468 x 252, radius 20)
        float cx = sw * 0.5f;
        float panelW = Math.min(468.0f, sw - 28.0f);
        float panelH = 252.0f;
        float panelX = cx - panelW * 0.5f;
        float panelY = Math.max(36.0f, (sh - panelH) * 0.5f);

        RenderManager.drawSoftGlow(r, panelX, panelY, panelW, panelH, 20.0f, 4, prim, 0.24f);
        r.drawShadow(panelX, panelY, panelW, panelH, 20f, t.getShadowIntensity(), 0x000000);
        r.fillRoundedRect(panelX, panelY, panelW, panelH, 20f, 0xF2060A18);
        RenderManager.fillRoundedHorizontalGradient(r, panelX + 22f, panelY + 1.0f, panelW - 44f, 2.2f, 1.0f, prim, acc);
        r.drawRoundedOutline(panelX, panelY, panelW, panelH, 20f, 1.5f, AxsesTheme.withAlpha(prim, 0.85f));

        // Left Column: Brand Hero & Active Session Telemetry (width 182)
        float leftX = panelX + 14.0f;
        float leftW = 182.0f;
        r.fillRoundedRect(leftX, panelY + 14.0f, leftW, panelH - 28.0f, 14.0f, AxsesTheme.withAlpha(t.getSurfaceColor(), 0.94f));
        r.drawRoundedOutline(leftX, panelY + 14.0f, leftW, panelH - 28.0f, 14.0f, 1.0f, 0x28FFFFFF);

        // Glowing Curved AX Crest
        float crestX = leftX + (leftW - 46.0f) * 0.5f;
        float crestY = panelY + 28.0f;
        r.fillRoundedRect(crestX - 2f, crestY - 2f, 50f, 50f, 16f, AxsesTheme.withAlpha(acc, 0.20f));
        RenderManager.fillRoundedHorizontalGradient(r, crestX, crestY, 46f, 46f, 14f,
                AxsesTheme.withAlpha(prim, 0.65f), AxsesTheme.withAlpha(acc, 0.45f));
        r.drawRoundedOutline(crestX, crestY, 46f, 46f, 14f, 1.2f, acc);
        core.getFontManager().drawCentered(r, "AX", crestX + 23f, crestY + 18f, 0xFFFFFFFF, 1.25f);

        core.getFontManager().drawCentered(r, "AXSESCLIENT 3.5", leftX + leftW * 0.5f, crestY + 56f, 0xFFFFFFFF, 1.0f);
        core.getFontManager().drawCentered(r, "CELESTIAL VISION", leftX + leftW * 0.5f, crestY + 69f, acc, 0.88f);

        String mcVer = mc != null ? mc.getMinecraftVersion() : "1.21.11";
        String user = mc != null ? mc.getPlayerName() : "Player";
        float cardY = crestY + 87.0f;
        r.fillRoundedRect(leftX + 10f, cardY, leftW - 20f, 48f, 10.0f, 0xCC050814);
        r.drawRoundedOutline(leftX + 10f, cardY, leftW - 20f, 48f, 10.0f, 1.0f, AxsesTheme.withAlpha(acc, 0.50f));
        core.getFontManager().drawCentered(r, user, leftX + leftW * 0.5f, cardY + 8f, 0xFFFFFFFF, 0.94f);
        core.getFontManager().drawCentered(r, "MC " + mcVer + " · Fabric 0.20.1", leftX + leftW * 0.5f, cardY + 22f, t.getMutedColor(), 0.82f);
        core.getFontManager().drawCentered(r, isRu ? "FunTime · RW · HolyWorld" : "FunTime · RW · HolyWorld",
                leftX + leftW * 0.5f, cardY + 34f, t.getSuccessColor(), 0.80f);

        String hint = isRu ? "Правый Shift — меню ClickGUI" : "Right Shift — Open ClickGUI";
        core.getFontManager().drawCentered(r, hint, leftX + leftW * 0.5f, panelY + panelH - 28f, t.getMutedColor(), 0.80f);

        // Right Column: 6 Sculpted Action Buttons
        String[] labels = {
                core.getLocalizationManager().tr("menu.singleplayer"),
                core.getLocalizationManager().tr("menu.multiplayer"),
                core.getLocalizationManager().tr("menu.settings"),
                core.getLocalizationManager().tr("menu.axses_gui"),
                core.getLocalizationManager().tr("menu.accounts"),
                core.getLocalizationManager().tr("menu.quit")
        };

        float btnX = leftX + leftW + 14.0f;
        float btnW = (panelX + panelW - 14.0f) - btnX;
        float btnH = 30.0f;
        float startY = panelY + 16.0f;

        for (int i = 0; i < BUTTON_IDS.length; i++) {
            float by = startY + i * 37.5f;
            boolean hov = !showAccountsModal && isInside(mouseX, mouseY, btnX, by, btnW, btnH);
            float hovAnim = core.getAnimationManager().smoothHover("menu:" + BUTTON_IDS[i], hov);
            boolean primary = (i == 0 || i == 1 || i == 3);
            float inset = hovAnim * -2.0f;
            float dx = btnX + inset;
            float dw = btnW - inset * 2.0f;
            float btnRad = 13.0f;

            if (primary) {
                int cL = AxsesTheme.withAlpha(prim, 0.82f + 0.18f * hovAnim);
                int cR = AxsesTheme.withAlpha(i == 3 ? acc : RenderManager.blendArgb(prim, acc, 0.58f), 0.82f + 0.18f * hovAnim);
                RenderManager.fillRoundedHorizontalGradient(r, dx, by, dw, btnH, btnRad, cL, cR);
                r.drawRoundedOutline(dx, by, dw, btnH, btnRad, 1.0f, hov ? 0xFFFFFFFF : acc);
            } else {
                int bg = AxsesTheme.withAlpha(t.getSurfaceColor(), 0.92f + 0.08f * hovAnim);
                r.fillRoundedRect(dx, by, dw, btnH, btnRad, bg);
                r.drawRoundedOutline(dx, by, dw, btnH, btnRad, 1.0f,
                        hov ? acc : AxsesTheme.withAlpha(prim, 0.38f));
            }
            core.getFontManager().drawCentered(r, BUTTON_ICONS[i] + labels[i], dx + dw * 0.5f, by + 10.5f, 0xFFFFFFFF, 0.96f);
        }

        // Footer version & memory strip
        long maxMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
        core.getFontManager().draw(r, "AXSESCLIENT v3.5.0 · MC " + mcVer + " · JVM " + maxMb + " MB", 10f, sh - 14f, 0xFF94A3B8, 0.88f);
        String rightTag = user + " · " + t.getName();
        float rw = core.getFontManager().width(r, rightTag, 0.88f);
        core.getFontManager().draw(r, rightTag, sw - rw - 10f, sh - 14f, acc, 0.88f);

        // Accounts & Themes Modal
        if (showAccountsModal) {
            r.fillRect(0, 0, sw, sh, 0xB4050710);
            float mw = 276f, mh = 136f;
            float mx0 = cx - mw * 0.5f, my0 = (sh - mh) * 0.5f;
            r.fillRoundedRect(mx0, my0, mw, mh, 10f, t.getSurfaceColor());
            RenderManager.fillHorizontalGradient(r, mx0 + 8f, my0 + 1f, mw - 16f, 2f, prim, acc);
            r.drawRoundedOutline(mx0, my0, mw, mh, 10f, 1.2f, acc);
            core.getFontManager().drawCentered(r, isRu ? "АККАУНТ И ТЕМЫ ОФОРМЛЕНИЯ" : "ACTIVE PROFILE & THEMES", cx, my0 + 14f, acc, 0.92f);
            core.getFontManager().drawCentered(r, (isRu ? "Профиль: " : "Profile: ") + user, cx, my0 + 36f, 0xFFFFFFFF, 0.92f);
            core.getFontManager().drawCentered(r, (isRu ? "Активная тема: " : "Active Theme: ") + t.getName(), cx, my0 + 54f, t.getMutedColor(), 0.86f);

            r.fillRoundedRect(cx - 112f, my0 + 88f, 106f, 24f, 5.5f, AxsesTheme.withAlpha(prim, 0.35f));
            r.drawRoundedOutline(cx - 112f, my0 + 88f, 106f, 24f, 5.5f, 1f, prim);
            core.getFontManager().drawCentered(r, isRu ? "Сменить тему" : "Next Theme", cx - 59f, my0 + 96f, 0xFFFFFFFF, 0.86f);

            r.fillRoundedRect(cx + 6f, my0 + 88f, 106f, 24f, 5.5f, AxsesTheme.withAlpha(acc, 0.25f));
            r.drawRoundedOutline(cx + 6f, my0 + 88f, 106f, 24f, 5.5f, 1f, acc);
            core.getFontManager().drawCentered(r, isRu ? "Закрыть" : "Close", cx + 59f, my0 + 96f, 0xFFFFFFFF, 0.86f);
        }

        core.getRenderManager().renderNotifications(
                r,
                core.getNotificationManager(),
                dt,
                core.getAnimationManager().getAnimationSpeed(),
                core.getAnimationManager().isReducedMotion()
        );
    }

    public boolean mouseClicked(MinecraftAdapter mc, double mouseX, double mouseY, int button) {
        if (button != 0 || mc == null) return false;
        int sw = mc.getScaledWidth();
        int sh = mc.getScaledHeight();
        float cx = sw * 0.5f;

        if (!showAccountsModal) {
            if (isInside((float) mouseX, (float) mouseY, sw - 128f, 10f, 118f, 22f)) {
                core.getLocalizationManager().toggleLocale();
                core.getConfigManager().saveActiveAsync();
                core.getNotificationManager().push("AXSES",
                        core.getLocalizationManager().isRussian() ? "Язык: Русский" : "Language: English",
                        NotificationManager.Severity.SUCCESS);
                return true;
            }
            if (isInside((float) mouseX, (float) mouseY, sw - 252f, 10f, 118f, 22f)) {
                core.getThemeManager().cycleNextTheme();
                core.getConfigManager().saveActiveAsync();
                core.getNotificationManager().push("AXSES",
                        core.getThemeManager().getActiveTheme().getName(),
                        NotificationManager.Severity.INFO);
                return true;
            }
        }

        if (showAccountsModal) {
            float my0 = (sh - 136f) * 0.5f;
            if (isInside((float) mouseX, (float) mouseY, cx - 112f, my0 + 88f, 106f, 24f)) {
                core.getThemeManager().cycleNextTheme();
                core.getConfigManager().saveActiveAsync();
                core.getNotificationManager().push("AXSES", core.getThemeManager().getActiveTheme().getName(), NotificationManager.Severity.INFO);
                return true;
            }
            showAccountsModal = false;
            return true;
        }

        float panelW = Math.min(468.0f, sw - 28.0f);
        float panelH = 252.0f;
        float panelX = cx - panelW * 0.5f;
        float panelY = Math.max(36.0f, (sh - panelH) * 0.5f);
        float btnX = panelX + 14.0f + 182.0f + 14.0f;
        float btnW = (panelX + panelW - 14.0f) - btnX;
        float btnH = 30.0f;
        float startY = panelY + 16.0f;

        for (int i = 0; i < BUTTON_IDS.length; i++) {
            float by = startY + i * 37.5f;
            if (isInside((float) mouseX, (float) mouseY, btnX, by, btnW, btnH)) {
                switch (i) {
                    case 0: mc.openVanillaSingleplayer(); break;
                    case 1: mc.openVanillaMultiplayer(); break;
                    case 2: mc.openVanillaOptions(); break;
                    case 3: mc.openClickGui(); break;
                    case 4: showAccountsModal = true; break;
                    case 5: mc.quitGame(); break;
                    default: break;
                }
                return true;
            }
        }
        return false;
    }

    public boolean keyPressed(MinecraftAdapter mc, int keyCode) {
        if (showAccountsModal && keyCode == KeyCodes.KEY_ESCAPE) {
            showAccountsModal = false;
            return true;
        }
        if (keyCode == core.getKeybindManager().getOpenGuiBind().getKeyCode()) {
            mc.openClickGui();
            return true;
        }
        return false;
    }

    private static boolean isInside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
