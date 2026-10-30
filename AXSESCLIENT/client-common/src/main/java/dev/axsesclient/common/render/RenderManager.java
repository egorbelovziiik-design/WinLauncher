package dev.axsesclient.common.render;

import dev.axsesclient.common.notifications.NotificationManager;
import dev.axsesclient.common.platform.RenderAdapter;
import dev.axsesclient.common.theme.AxsesTheme;
import dev.axsesclient.common.theme.ThemeManager;
import java.util.List;

public final class RenderManager {
    private final ThemeManager themeManager;
    private final FontManager fontManager;
    private boolean highContrastBorders = false;
    private float blurBackdropStrength = 0.65f;

    public RenderManager(ThemeManager themeManager, FontManager fontManager) {
        this.themeManager = themeManager;
        this.fontManager = fontManager;
    }

    public boolean isHighContrastBorders() { return highContrastBorders; }
    public void setHighContrastBorders(boolean highContrastBorders) { this.highContrastBorders = highContrastBorders; }
    public float getBlurBackdropStrength() { return blurBackdropStrength; }
    public void setBlurBackdropStrength(float s) { this.blurBackdropStrength = Math.max(0f, Math.min(1f, s)); }

    public void drawCard(RenderAdapter r, float x, float y, float w, float h, boolean active, float hoverProgress) {
        AxsesTheme t = themeManager.getActiveTheme();
        float rad = t.getCornerRadius();
        if (t.getShadowIntensity() > 0.02f) {
            r.drawShadow(x, y, w, h, rad, t.getShadowIntensity() * 0.6f, active ? themeManager.filterColor(t.getPrimaryColor()) : 0x000000);
        }
        int baseColor = active ? t.getElevatedColor() : t.getSurfaceColor();
        int fill = AxsesTheme.withAlpha(themeManager.filterColor(baseColor), t.getOpacity());
        r.fillRoundedRect(x, y, w, h, rad, fill);

        if (hoverProgress > 0.01f) {
            int hoverTint = AxsesTheme.withAlpha(themeManager.filterColor(t.getPrimaryColor()), 0.10f * hoverProgress);
            r.fillRoundedRect(x, y, w, h, rad, hoverTint);
        }

        int borderCol = active
                ? themeManager.filterColor(t.getPrimaryColor())
                : AxsesTheme.withAlpha(themeManager.filterColor(t.getMutedColor()), highContrastBorders ? 0.65f : 0.25f);
        r.drawRoundedOutline(x, y, w, h, rad, active || highContrastBorders ? 1.5f : 1.0f, borderCol);
    }

    public void drawToggleSwitch(RenderAdapter r, float x, float y, float w, float h, float animProgress) {
        AxsesTheme t = themeManager.getActiveTheme();
        float rad = h * 0.5f;
        int offCol = AxsesTheme.withAlpha(t.getElevatedColor(), 0.95f);
        int onCol = themeManager.filterColor(t.getPrimaryColor());
        int track = blendArgb(offCol, onCol, animProgress);
        r.fillRoundedRect(x, y, w, h, rad, track);
        float knobSize = h - 4.0f;
        float knobX = x + 2.0f + (w - knobSize - 4.0f) * animProgress;
        r.fillRoundedRect(knobX, y + 2.0f, knobSize, knobSize, knobSize * 0.5f, t.getTextColor());
    }

    public void drawSlider(RenderAdapter r, float x, float y, float w, float h, float normalized) {
        AxsesTheme t = themeManager.getActiveTheme();
        float trackH = Math.max(4.0f, h * 0.45f);
        float ty = y + (h - trackH) * 0.5f;
        r.fillRoundedRect(x, ty, w, trackH, trackH * 0.5f, AxsesTheme.withAlpha(t.getBackgroundColor(), 0.95f));
        float fillW = Math.max(trackH, w * Math.max(0.0f, Math.min(1.0f, normalized)));
        r.fillRoundedRect(x, ty, fillW, trackH, trackH * 0.5f, themeManager.filterColor(t.getAccentColor()));
        float knobX = x + w * Math.max(0.0f, Math.min(1.0f, normalized)) - 4.0f;
        r.fillRoundedRect(knobX, y + 1.0f, 8.0f, h - 2.0f, 4.0f, t.getTextColor());
    }

    public void renderNotifications(RenderAdapter r, NotificationManager nm, float deltaSeconds, float animSpeed, boolean reducedMotion) {
        List<NotificationManager.NotificationEntry> active = nm.updateAndGetActive(deltaSeconds, animSpeed, reducedMotion);
        if (active.isEmpty()) {
            return;
        }
        AxsesTheme t = themeManager.getActiveTheme();
        long now = System.currentTimeMillis();
        float cardW = 210.0f;
        float cardH = 38.0f;
        float baseRight = r.getScaledWidth() - 10.0f;
        float currentBottom = r.getScaledHeight() - 12.0f;

        for (int i = active.size() - 1; i >= 0; i--) {
            NotificationManager.NotificationEntry n = active.get(i);
            float vis = Math.max(0.0f, Math.min(1.0f, n.updateVisibility(now, 0.0f, animSpeed, reducedMotion)));
            float slideOffset = reducedMotion ? 0.0f : (1.0f - vis) * 40.0f;
            float x = baseRight - cardW + slideOffset;
            float y = currentBottom - cardH;

            int accent;
            switch (n.getSeverity()) {
                case SUCCESS: accent = themeManager.filterColor(t.getSuccessColor()); break;
                case WARNING: accent = themeManager.filterColor(t.getWarningColor()); break;
                case ERROR: accent = themeManager.filterColor(t.getErrorColor()); break;
                default: accent = themeManager.filterColor(t.getAccentColor()); break;
            }
            int bg = AxsesTheme.withAlpha(t.getSurfaceColor(), 0.95f * vis);
            r.fillRoundedRect(x, y, cardW, cardH, 6.0f, bg);
            r.drawRoundedOutline(x, y, cardW, cardH, 6.0f, 1.0f, AxsesTheme.withAlpha(accent, 0.8f * vis));
            r.fillRect(x + 2.0f, y + cardH - 3.0f, (cardW - 4.0f) * n.getRemainingFraction(now), 2.0f, AxsesTheme.withAlpha(accent, vis));

            fontManager.draw(r, n.getTitle(), x + 8.0f, y + 6.0f, AxsesTheme.withAlpha(accent, vis), 0.85f);
            fontManager.draw(r, n.getMessage(), x + 8.0f, y + 19.0f, AxsesTheme.withAlpha(t.getTextColor(), vis), 0.80f);

            currentBottom -= (cardH + 6.0f);
        }
    }

    public static int blendArgb(int c1, int c2, float t) {
        float k = Math.max(0.0f, Math.min(1.0f, t));
        int a1 = (c1 >>> 24) & 0xFF, r1 = (c1 >>> 16) & 0xFF, g1 = (c1 >>> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >>> 24) & 0xFF, r2 = (c2 >>> 16) & 0xFF, g2 = (c2 >>> 8) & 0xFF, b2 = c2 & 0xFF;
        int a = Math.round(a1 + (a2 - a1) * k);
        int r = Math.round(r1 + (r2 - r1) * k);
        int g = Math.round(g1 + (g2 - g1) * k);
        int b = Math.round(b1 + (b2 - b1) * k);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
