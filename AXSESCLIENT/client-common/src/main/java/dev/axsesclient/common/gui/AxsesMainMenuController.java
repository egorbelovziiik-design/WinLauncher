package dev.axsesclient.common.gui;

import dev.axsesclient.common.AxsesCore;
import dev.axsesclient.common.notifications.NotificationManager;
import dev.axsesclient.common.platform.KeyCodes;
import dev.axsesclient.common.platform.MinecraftAdapter;
import dev.axsesclient.common.platform.RenderAdapter;
import dev.axsesclient.common.theme.AxsesTheme;

public final class AxsesMainMenuController {
    private static final String[] BUTTON_IDS = {
            "singleplayer", "multiplayer", "settings", "axses_gui", "accounts", "quit"
    };

    private final AxsesCore core;
    private boolean showAccountsModal = false;
    private float bgPhase = 0.0f;

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

        // Animated Neo-Dark gradient + drifting geometric aurora bands
        r.fillGradientVertical(0, 0, sw, sh, t.getBackgroundColor(), t.getSurfaceColor());
        int gridSpacing = 28;
        int shift = Math.round((bgPhase * 14.0f) % gridSpacing);
        int gridCol = AxsesTheme.withAlpha(core.getThemeManager().filterColor(t.getPrimaryColor()), 0.07f);
        for (int gx = shift; gx < sw; gx += gridSpacing) {
            r.fillRect(gx, 0, 1, sh, gridCol);
        }
        for (int gy = shift; gy < sh; gy += gridSpacing) {
            r.fillRect(0, gy, sw, 1, gridCol);
        }

        // Subtle animated top & bottom accent beams
        float waveX = (float) ((Math.sin(bgPhase) * 0.5 + 0.5) * (sw - 180));
        r.fillRoundedRect(waveX, 0, 180, 2.5f, 1.2f, core.getThemeManager().filterColor(t.getAccentColor()));
        r.fillRoundedRect(sw - waveX - 180, sh - 2.5f, 180, 2.5f, 1.2f, core.getThemeManager().filterColor(t.getPrimaryColor()));

        float cx = sw * 0.5f;
        float panelW = 268.0f;
        float panelH = 244.0f;
        float panelX = cx - panelW * 0.5f;
        float panelY = Math.max(18.0f, (sh - panelH) * 0.5f);

        r.drawShadow(panelX, panelY, panelW, panelH, 10f, t.getShadowIntensity(), 0x000000);
        r.fillRoundedRect(panelX, panelY, panelW, panelH, 10f, 0xF00B0F1A);
        r.fillRoundedRect(panelX + 18f, panelY, panelW - 36f, 2f, 1f, core.getThemeManager().filterColor(t.getAccentColor()));
        r.drawRoundedOutline(panelX, panelY, panelW, panelH, 10f, 1.0f, AxsesTheme.withAlpha(core.getThemeManager().filterColor(t.getPrimaryColor()), 0.55f));

        // AXSESCLIENT OBSIDIAN AURORA Emblem & Header
        core.getFontManager().drawCentered(r, "AXSESCLIENT", cx, panelY + 14f, 0xFFF8FAFC, 1.0f);
        core.getFontManager().drawCentered(r, "OBSIDIAN AURORA · v2.0", cx, panelY + 28f, t.getAccentColor(), 1.0f);

        String[] labels = {
                core.getLocalizationManager().tr("menu.singleplayer"),
                core.getLocalizationManager().tr("menu.multiplayer"),
                core.getLocalizationManager().tr("menu.settings"),
                core.getLocalizationManager().tr("menu.axses_gui"),
                core.getLocalizationManager().tr("menu.accounts"),
                core.getLocalizationManager().tr("menu.quit")
        };

        float btnW = 228.0f;
        float btnH = 24.0f;
        float btnX = cx - btnW * 0.5f;
        float startY = panelY + 48.0f;

        for (int i = 0; i < BUTTON_IDS.length; i++) {
            float by = startY + i * 30.0f;
            boolean hov = !showAccountsModal && isInside(mouseX, mouseY, btnX, by, btnW, btnH);
            float hovAnim = core.getAnimationManager().smoothHover("menu:" + BUTTON_IDS[i], hov);
            boolean primary = i == 0 || i == 3;
            int baseBg = primary ? core.getThemeManager().filterColor(t.getPrimaryColor()) : 0xFF141B2D;
            int fill = AxsesTheme.withAlpha(baseBg, primary ? (0.82f + 0.18f * hovAnim) : (0.90f + 0.10f * hovAnim));
            float inset = hovAnim * -2.0f;
            r.fillRoundedRect(btnX + inset, by, btnW - inset * 2f, btnH, 6f, fill);
            r.drawRoundedOutline(btnX + inset, by, btnW - inset * 2f, btnH, 6f, 1.0f,
                    hov ? t.getAccentColor() : AxsesTheme.withAlpha(0xFFFFFFFF, primary ? 0.35f : 0.12f));
            core.getFontManager().drawCentered(r, labels[i], cx, by + 8.0f, 0xFFF8FAFC, 1.0f);
        }

        // Footer version & account strip
        String mcVer = mc != null ? mc.getMinecraftVersion() : "1.21";
        String user = mc != null ? mc.getPlayerName() : "Player";
        long maxMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
        core.getFontManager().draw(r, "AXSES v2.0 · MC " + mcVer + " · RAM " + maxMb + " MB", 8f, sh - 14f, 0xFF94A3B8, 1.0f);
        String rightTag = user + " · " + t.getName();
        float rw = core.getFontManager().width(r, rightTag, 1.0f);
        core.getFontManager().draw(r, rightTag, sw - rw - 8f, sh - 14f, t.getAccentColor(), 1.0f);

        // Accounts session modal
        if (showAccountsModal) {
            r.fillRect(0, 0, sw, sh, 0xAA070911);
            float mw = 260f, mh = 128f;
            float mx0 = cx - mw * 0.5f, my0 = (sh - mh) * 0.5f;
            r.fillRoundedRect(mx0, my0, mw, mh, 10f, t.getSurfaceColor());
            r.drawRoundedOutline(mx0, my0, mw, mh, 10f, 1.5f, t.getAccentColor());
            core.getFontManager().drawCentered(r, "ACTIVE MINECRAFT ACCOUNT", cx, my0 + 12f, t.getAccentColor(), 0.90f);
            core.getFontManager().drawCentered(r, "Session Profile: " + user, cx, my0 + 34f, t.getTextColor(), 0.88f);
            core.getFontManager().drawCentered(r, "Microsoft OAuth Managed via AXSES Launcher", cx, my0 + 52f, t.getMutedColor(), 0.75f);
            core.getFontManager().drawCentered(r, "Cycle Theme", cx - 54f, my0 + 92f, t.getTextColor(), 0.80f);
            r.drawRoundedOutline(cx - 104f, my0 + 84f, 100f, 22f, 5f, 1f, t.getPrimaryColor());
            core.getFontManager().drawCentered(r, "Close", cx + 54f, my0 + 92f, t.getTextColor(), 0.80f);
            r.drawRoundedOutline(cx + 4f, my0 + 84f, 100f, 22f, 5f, 1f, t.getAccentColor());
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

        if (showAccountsModal) {
            float my0 = (sh - 128f) * 0.5f;
            if (isInside((float) mouseX, (float) mouseY, cx - 104f, my0 + 84f, 100f, 22f)) {
                core.getThemeManager().cycleNextTheme();
                core.getNotificationManager().push("Theme Switched", core.getThemeManager().getActiveTheme().getName(), NotificationManager.Severity.INFO);
                return true;
            }
            if (isInside((float) mouseX, (float) mouseY, cx + 4f, my0 + 84f, 100f, 22f)) {
                showAccountsModal = false;
                return true;
            }
            showAccountsModal = false;
            return true;
        }

        float panelH = 244.0f;
        float panelY = Math.max(18.0f, (sh - panelH) * 0.5f);
        float btnW = 228.0f, btnH = 24.0f;
        float btnX = cx - btnW * 0.5f;
        float startY = panelY + 48.0f;

        for (int i = 0; i < BUTTON_IDS.length; i++) {
            float by = startY + i * 30.0f;
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
