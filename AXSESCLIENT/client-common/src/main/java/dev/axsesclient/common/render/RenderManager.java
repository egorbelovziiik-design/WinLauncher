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
        fillRoundedHorizontalGradient(r, x, y, w, h, 0.0f, leftArgb, rightArgb);
    }

    public static void fillRoundedHorizontalGradient(RenderAdapter r, float x, float y, float w, float h, float radius, int leftArgb, int rightArgb) {
        if (w <= 0f || h <= 0f) return;
        int ix1 = Math.round(x);
        int iy1 = Math.round(y);
        int ix2 = Math.max(ix1 + 1, Math.round(x + w));
        int iy2 = Math.max(iy1 + 1, Math.round(y + h));
        int iw = ix2 - ix1;
        int ih = iy2 - iy1;
        int ir = Math.max(0, Math.min(Math.round(radius), Math.min(iw, ih) / 2));

        if (ir <= 1) {
            int steps = Math.max(2, Math.min(28, Math.round(w / 6.0f)));
            int prevColX = ix1;
            for (int i = 0; i < steps; i++) {
                float t = steps == 1 ? 0f : (float) i / (float) (steps - 1);
                int nextColX = (i == steps - 1) ? ix2 : Math.round(ix1 + (iw * (i + 1)) / (float) steps);
                if (nextColX > prevColX) {
                    r.fillRect(prevColX, iy1, nextColX - prevColX, ih, blendArgb(leftArgb, rightArgb, t));
                    prevColX = nextColX;
                }
            }
            return;
        }

        // Exact 1px integer columns in curved end-caps + wider strips in straight center (zero overdraw!)
        int col = ix1;
        while (col < ix2) {
            boolean inLeftArc = (col - ix1) < ir;
            boolean inRightArc = (ix2 - 1 - col) < ir;
            int stepW;
            int insetY = 0;
            if (inLeftArc) {
                stepW = 1;
                float dx = ir - ((col - ix1) + 0.5f);
                insetY = Math.max(0, ir - Math.round((float) Math.sqrt(Math.max(0f, ir * ir - dx * dx))));
            } else if (inRightArc) {
                stepW = 1;
                float dx = ir - ((ix2 - 1 - col) + 0.5f);
                insetY = Math.max(0, ir - Math.round((float) Math.sqrt(Math.max(0f, ir * ir - dx * dx))));
            } else {
                int straightLimit = ix2 - ir;
                stepW = Math.max(1, Math.min(6, straightLimit - col));
            }
            int spanH = ih - insetY * 2;
            if (spanH > 0) {
                float t = iw <= 1 ? 0f : (float) (col - ix1) / (float) (iw - 1);
                r.fillRect(col, iy1 + insetY, stepW, spanH, blendArgb(leftArgb, rightArgb, t));
            }
            col += stepW;
        }
    }

    public static void drawSoftGlow(RenderAdapter r, float x, float y, float w, float h, float radius, int layers, int rgb, float maxAlpha) {
        if (layers <= 0 || maxAlpha <= 0.005f || w <= 0f || h <= 0f) return;
        for (int i = layers; i >= 1; i--) {
            float spread = i * 2.0f;
            float falloff = (float) (layers + 1 - i) / (float) (layers + 1);
            float alpha = maxAlpha * falloff * 0.50f;
            r.fillRoundedRect(x - spread, y - spread, w + spread * 2f, h + spread * 2f, radius + spread, AxsesTheme.withAlpha(rgb, alpha));
        }
    }

    /**
     * Signature Hyperion Pro anti-aliased contour: soft outer neon aura, dark contrast cushion,
     * dual-tone gradient rounded stroke, and top inner glass specular highlight.
     */
    public static void drawHyperionContour(
            RenderAdapter r, float x, float y, float w, float h, float radius,
            float thickness, int primaryArgb, int accentArgb, float glowAlpha
    ) {
        if (w <= 0f || h <= 0f) return;
        if (glowAlpha > 0.01f) {
            r.drawGradientRoundedOutline(x - 1.6f, y - 1.6f, w + 3.2f, h + 3.2f, radius + 1.6f, thickness + 1.4f,
                    AxsesTheme.withAlpha(primaryArgb, glowAlpha * 0.34f),
                    AxsesTheme.withAlpha(accentArgb, glowAlpha * 0.34f));
        }
        r.drawRoundedOutline(x - 0.7f, y - 0.7f, w + 1.4f, h + 1.4f, radius + 0.7f, 1.0f, 0x9603050C);
        r.drawGradientRoundedOutline(x, y, w, h, radius, thickness, primaryArgb, accentArgb);
        if (w > radius * 2f + 6f) {
            fillHorizontalGradient(r, x + radius + 1f, y + 1.0f, w - radius * 2f - 2f, 0.9f, 0x36FFFFFF, 0x10FFFFFF);
        }
    }

    /**
     * Projects and renders a smooth 12-edge anti-aliased 3D neon prism contour in world space.
     */
    public static void draw3dNeonPrism(
            RenderAdapter r, MinecraftAdapter mc,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float strokeWidth, int topColor, int bottomColor, boolean glow
    ) {
        if (mc == null) return;
        double[][] pts = new double[][] {
                {minX, minY, minZ}, {maxX, minY, minZ}, {maxX, minY, maxZ}, {minX, minY, maxZ},
                {minX, maxY, minZ}, {maxX, maxY, minZ}, {maxX, maxY, maxZ}, {minX, maxY, maxZ}
        };
        float[][] sp = new float[8][];
        for (int i = 0; i < 8; i++) {
            sp[i] = mc.projectWorldToScreen(pts[i][0], pts[i][1], pts[i][2]);
            if (sp[i] == null || sp[i][2] <= 0.08f) {
                return;
            }
        }
        int[][] bottomEdges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}};
        int[][] topEdges = {{4, 5}, {5, 6}, {6, 7}, {7, 4}};
        int[][] pillars = {{0, 4}, {1, 5}, {2, 6}, {3, 7}};

        int glowBot = AxsesTheme.withAlpha(bottomColor, 0.42f);
        int glowTop = AxsesTheme.withAlpha(topColor, 0.42f);
        int midCol = blendArgb(bottomColor, topColor, 0.5f);
        int glowMid = AxsesTheme.withAlpha(midCol, 0.40f);

        for (int i = 0; i < 4; i++) {
            float[] a = sp[bottomEdges[i][0]], b = sp[bottomEdges[i][1]];
            if (glow) r.drawNeonLine(a[0], a[1], b[0], b[1], strokeWidth, bottomColor, glowBot);
            else r.drawLine(a[0], a[1], b[0], b[1], strokeWidth, bottomColor);
        }
        for (int i = 0; i < 4; i++) {
            float[] a = sp[pillars[i][0]], b = sp[pillars[i][1]];
            if (glow) r.drawNeonLine(a[0], a[1], b[0], b[1], strokeWidth, midCol, glowMid);
            else r.drawLine(a[0], a[1], b[0], b[1], strokeWidth, midCol);
        }
        for (int i = 0; i < 4; i++) {
            float[] a = sp[topEdges[i][0]], b = sp[topEdges[i][1]];
            if (glow) r.drawNeonLine(a[0], a[1], b[0], b[1], strokeWidth, topColor, glowTop);
            else r.drawLine(a[0], a[1], b[0], b[1], strokeWidth, topColor);
        }
    }

    public void drawCard(RenderAdapter r, float x, float y, float w, float h, boolean active, float hoverProgress) {
        AxsesTheme t = themeManager.getActiveTheme();
        float rad = t.getCornerRadius();
        int prim = themeManager.filterColor(t.getPrimaryColor());
        int acc = themeManager.filterColor(t.getAccentColor());
        if (t.getShadowIntensity() > 0.02f) {
            r.drawShadow(x, y, w, h, rad, t.getShadowIntensity() * 0.70f, active ? prim : 0x000000);
        }
        int baseColor = active ? t.getElevatedColor() : t.getSurfaceColor();
        int fill = AxsesTheme.withAlpha(themeManager.filterColor(baseColor), t.getOpacity());
        r.fillRoundedRect(x, y, w, h, rad, fill);

        // Top glass sheen
        r.fillRect(x + rad, y + 1.0f, Math.max(4f, w - rad * 2f), 1.0f, 0x22FFFFFF);

        if (hoverProgress > 0.01f) {
            int hoverTint = AxsesTheme.withAlpha(prim, 0.14f * hoverProgress);
            r.fillRoundedRect(x, y, w, h, rad, hoverTint);
        }

        if (active) {
            drawHyperionContour(r, x, y, w, h, rad, 1.35f,
                    AxsesTheme.withAlpha(prim, 0.95f), AxsesTheme.withAlpha(acc, 0.98f), 0.45f);
        } else {
            int c1 = AxsesTheme.withAlpha(themeManager.filterColor(t.getMutedColor()), highContrastBorders ? 0.68f : 0.34f);
            int c2 = AxsesTheme.withAlpha(prim, highContrastBorders ? 0.55f : 0.24f);
            r.drawGradientRoundedOutline(x, y, w, h, rad, highContrastBorders ? 1.3f : 1.05f, c1, c2);
        }
    }

    public void drawToggleSwitch(RenderAdapter r, float x, float y, float w, float h, float animProgress) {
        AxsesTheme t = themeManager.getActiveTheme();
        float k = Math.max(0f, Math.min(1f, animProgress));
        float rad = h * 0.5f;
        int offCol = AxsesTheme.withAlpha(0xFF12182A, 0.96f);
        int onPrimary = themeManager.filterColor(t.getPrimaryColor());
        int onAccent = themeManager.filterColor(t.getAccentColor());

        if (k > 0.08f) {
            r.fillRoundedRect(x - 1.5f, y - 1.5f, w + 3.0f, h + 3.0f, rad + 1.5f, AxsesTheme.withAlpha(onAccent, 0.24f * k));
        }
        if (k > 0.05f) {
            fillRoundedHorizontalGradient(r, x, y, w, h, rad,
                    blendArgb(offCol, onPrimary, k),
                    blendArgb(offCol, onAccent, k));
        } else {
            r.fillRoundedRect(x, y, w, h, rad, offCol);
        }
        r.drawRoundedOutline(x, y, w, h, rad, 1.0f, k > 0.45f ? AxsesTheme.withAlpha(onAccent, 0.90f) : 0x3D94A3B8);

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
        float trackH = Math.max(5.0f, h * 0.48f);
        float ty = y + (h - trackH) * 0.5f;

        r.fillRoundedRect(x, ty, w, trackH, trackH * 0.5f, 0xEE080C1A);
        r.drawRoundedOutline(x, ty, w, trackH, trackH * 0.5f, 1.0f, 0x2AFFFFFF);

        float fillW = Math.max(trackH, w * norm);
        int c1 = themeManager.filterColor(t.getPrimaryColor());
        int c2 = themeManager.filterColor(t.getAccentColor());
        fillRoundedHorizontalGradient(r, x, ty, fillW, trackH, trackH * 0.5f, c1, c2);

        float knobX = x + w * norm - 5.0f;
        r.fillRoundedRect(knobX - 1.5f, y - 0.5f, 13.0f, h + 1.0f, (h + 1.0f) * 0.5f, AxsesTheme.withAlpha(c2, 0.30f));
        r.fillRoundedRect(knobX, y + 1.0f, 10.0f, h - 2.0f, (h - 2.0f) * 0.5f, 0xFFFFFFFF);
        r.fillRoundedRect(knobX + 3.0f, y + 3.5f, 4.0f, Math.max(2.0f, h - 7.0f), 2.0f, c1);
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
