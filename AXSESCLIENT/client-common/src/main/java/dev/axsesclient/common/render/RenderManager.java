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
    private float blurBackdropStrength = 0.68f;

    public RenderManager(ThemeManager themeManager, FontManager fontManager) {
        this.themeManager = themeManager;
        this.fontManager = fontManager;
    }

    public boolean isHighContrastBorders() { return highContrastBorders; }
    public void setHighContrastBorders(boolean highContrastBorders) { this.highContrastBorders = highContrastBorders; }
    public float getBlurBackdropStrength() { return blurBackdropStrength; }
    public void setBlurBackdropStrength(float s) { this.blurBackdropStrength = Math.max(0f, Math.min(1f, s)); }

    public static void fillHorizontalGradient(RenderAdapter r, float x, float y, float w, float h, int leftArgb, int rightArgb) {
        if (w <= 0f || h <= 0f) return;
        int steps = Math.max(2, Math.min(20, Math.round(w / 8.0f)));
        float segW = w / steps;
        for (int i = 0; i < steps; i++) {
            float t = steps == 1 ? 0f : (float) i / (float) (steps - 1);
            float sx = x + i * segW;
            float sw = (i == steps - 1) ? (x + w - sx) : (segW + 0.5f);
            r.fillRect(sx, y, sw, h, blendArgb(leftArgb, rightArgb, t));
        }
    }

    public void drawCard(RenderAdapter r, float x, float y, float w, float h, boolean active, float hoverProgress) {
        AxsesTheme t = themeManager.getActiveTheme();
        float rad = t.getCornerRadius();
        if (t.getShadowIntensity() > 0.02f) {
            r.drawShadow(x, y, w, h, rad, t.getShadowIntensity() * 0.65f, active ? themeManager.filterColor(t.getPrimaryColor()) : 0x000000);
        }
        int baseColor = active ? t.getElevatedColor() : t.getSurfaceColor();
        int fill = AxsesTheme.withAlpha(themeManager.filterColor(baseColor), t.getOpacity());
        r.fillRoundedRect(x, y, w, h, rad, fill);

        // Top glass sheen
        r.fillRect(x + rad, y + 1.0f, Math.max(4f, w - rad * 2f), 1.0f, 0x16FFFFFF);

        if (hoverProgress > 0.01f) {
            int hoverTint = AxsesTheme.withAlpha(themeManager.filterColor(t.getPrimaryColor()), 0.12f * hoverProgress);
            r.fillRoundedRect(x, y, w, h, rad, hoverTint);
        }

        int borderCol = active
                ? themeManager.filterColor(t.getAccentColor())
                : AxsesTheme.withAlpha(themeManager.filterColor(t.getMutedColor()), highContrastBorders ? 0.65f : 0.28f);
        r.drawRoundedOutline(x, y, w, h, rad, active || highContrastBorders ? 1.5f : 1.0f, borderCol);
    }

    public void drawToggleSwitch(RenderAdapter r, float x, float y, float w, float h, float animProgress) {
        AxsesTheme t = themeManager.getActiveTheme();
        float k = Math.max(0f, Math.min(1f, animProgress));
        float rad = h * 0.5f;
        int offCol = AxsesTheme.withAlpha(0xFF161C2E, 0.96f);
        int onPrimary = themeManager.filterColor(t.getPrimaryColor());
        int onAccent = themeManager.filterColor(t.getAccentColor());
        int track = blendArgb(offCol, blendArgb(onPrimary, onAccent, 0.38f), k);

        if (k > 0.08f) {
            r.fillRoundedRect(x - 1.5f, y - 1.5f, w + 3.0f, h + 3.0f, rad + 1.5f, AxsesTheme.withAlpha(onAccent, 0.22f * k));
        }
        r.fillRoundedRect(x, y, w, h, rad, track);
        r.drawRoundedOutline(x, y, w, h, rad, 1.0f, k > 0.5f ? AxsesTheme.withAlpha(onAccent, 0.85f) : 0x3394A3B8);

        float knobSize = h - 4.0f;
        float knobX = x + 2.0f + (w - knobSize - 4.0f) * k;
        r.fillRoundedRect(knobX, y + 2.0f, knobSize, knobSize, knobSize * 0.5f, 0xFFFFFFFF);
        if (k > 0.45f) {
            r.fillRect(knobX + knobSize * 0.5f - 1.0f, y + h * 0.5f - 1.0f, 2.0f, 2.0f, onPrimary);
        }
    }

    public void drawSlider(RenderAdapter r, float x, float y, float w, float h, float normalized) {
        AxsesTheme t = themeManager.getActiveTheme();
        float norm = Math.max(0.0f, Math.min(1.0f, normalized));
        float trackH = Math.max(4.5f, h * 0.46f);
        float ty = y + (h - trackH) * 0.5f;

        r.fillRoundedRect(x, ty, w, trackH, trackH * 0.5f, 0xEE080B16);
        r.drawRoundedOutline(x, ty, w, trackH, trackH * 0.5f, 1.0f, 0x26FFFFFF);

        float fillW = Math.max(trackH, w * norm);
        int c1 = themeManager.filterColor(t.getPrimaryColor());
        int c2 = themeManager.filterColor(t.getAccentColor());
        fillHorizontalGradient(r, x + 1.0f, ty + 0.5f, Math.max(2.0f, fillW - 1.5f), Math.max(2.0f, trackH - 1.0f), c1, c2);

        float knobX = x + w * norm - 4.5f;
        r.fillRoundedRect(knobX - 1.5f, y - 0.5f, 12.0f, h + 1.0f, 5.5f, AxsesTheme.withAlpha(c2, 0.28f));
        r.fillRoundedRect(knobX, y + 1.0f, 9.0f, h - 2.0f, 4.5f, 0xFFFFFFFF);
        r.fillRoundedRect(knobX + 2.5f, y + 3.5f, 4.0f, Math.max(2.0f, h - 7.0f), 2.0f, c1);
    }

    public void renderNotifications(RenderAdapter r, NotificationManager nm, float deltaSeconds, float animSpeed, boolean reducedMotion) {
        List<NotificationManager.NotificationEntry> active = nm.updateAndGetActive(deltaSeconds, animSpeed, reducedMotion);
        if (active.isEmpty()) {
            return;
        }
        AxsesTheme t = themeManager.getActiveTheme();
        long now = System.currentTimeMillis();
        float cardW = 224.0f;
        float cardH = 40.0f;
        float baseRight = r.getScaledWidth() - 10.0f;
        float currentBottom = r.getScaledHeight() - 14.0f;

        for (int i = active.size() - 1; i >= 0; i--) {
            NotificationManager.NotificationEntry n = active.get(i);
            float vis = Math.max(0.0f, Math.min(1.0f, n.updateVisibility(now, 0.0f, animSpeed, reducedMotion)));
            float slideOffset = reducedMotion ? 0.0f : (1.0f - vis) * 44.0f;
            float x = baseRight - cardW + slideOffset;
            float y = currentBottom - cardH;

            int accent;
            String icon;
            switch (n.getSeverity()) {
                case SUCCESS:
                    accent = themeManager.filterColor(t.getSuccessColor());
                    icon = "✓";
                    break;
                case WARNING:
                    accent = themeManager.filterColor(t.getWarningColor());
                    icon = "!";
                    break;
                case ERROR:
                    accent = themeManager.filterColor(t.getErrorColor());
                    icon = "×";
                    break;
                default:
                    accent = themeManager.filterColor(t.getAccentColor());
                    icon = "⚡";
                    break;
            }
            r.drawShadow(x, y, cardW, cardH, 7.0f, 0.65f * vis, 0x000000);
            int bg = AxsesTheme.withAlpha(t.getBackgroundColor(), 0.96f * vis);
            r.fillRoundedRect(x, y, cardW, cardH, 7.0f, bg);
            fillHorizontalGradient(r, x + 6.0f, y + 1.0f, cardW - 12.0f, 1.5f,
                    AxsesTheme.withAlpha(themeManager.filterColor(t.getPrimaryColor()), vis),
                    AxsesTheme.withAlpha(accent, vis));
            r.drawRoundedOutline(x, y, cardW, cardH, 7.0f, 1.0f, AxsesTheme.withAlpha(accent, 0.78f * vis));

            // Left icon badge
            r.fillRoundedRect(x + 8.0f, y + 9.0f, 20.0f, 20.0f, 5.0f, AxsesTheme.withAlpha(accent, 0.22f * vis));
            fontManager.drawCentered(r, icon, x + 18.0f, y + 15.0f, AxsesTheme.withAlpha(accent, vis), 1.0f);

            // Progress bar along bottom
            r.fillRect(x + 6.0f, y + cardH - 2.5f, (cardW - 12.0f) * n.getRemainingFraction(now), 2.0f, AxsesTheme.withAlpha(accent, vis));

            fontManager.draw(r, n.getTitle(), x + 34.0f, y + 7.5f, AxsesTheme.withAlpha(accent, vis), 0.90f);
            fontManager.draw(r, n.getMessage(), x + 34.0f, y + 20.5f, AxsesTheme.withAlpha(t.getTextColor(), vis), 0.85f);

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
