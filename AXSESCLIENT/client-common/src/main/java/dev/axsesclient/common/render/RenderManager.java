package dev.axsesclient.common.render;

import dev.axsesclient.common.notifications.NotificationManager;
import dev.axsesclient.common.platform.MinecraftAdapter;
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
        float s = r.beginHiDpiSubPixel();
        try {
            int ix1 = Math.round(x * s);
            int iy1 = Math.round(y * s);
            int ix2 = Math.max(ix1 + 1, Math.round((x + w) * s));
            int iy2 = Math.max(iy1 + 1, Math.round((y + h) * s));
            int iw = ix2 - ix1;
            int ih = iy2 - iy1;
            int ir = Math.max(0, Math.min(Math.round(radius * s), Math.min(iw, ih) / 2));

            if (ir <= 1) {
                int steps = Math.max(2, Math.min(40, Math.round(iw / 6.0f)));
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
        } finally {
            r.endHiDpiSubPixel();
        }
    }

    public static void drawSoftGlow(RenderAdapter r, float x, float y, float w, float h, float radius, int layers, int rgb, float maxAlpha) {
        if (layers <= 0 || maxAlpha <= 0.005f || w <= 0f || h <= 0f) return;
        for (int i = layers; i >= 1; i--) {
            float spread = i * 2.2f;
            float norm = (float) (layers + 1 - i) / (float) (layers + 1);
            float alpha = maxAlpha * norm * norm * 0.58f;
            r.fillRoundedRect(x - spread, y - spread, w + spread * 2f, h + spread * 2f, radius + spread, AxsesTheme.withAlpha(rgb, alpha));
        }
    }

    /**
     * Multi-layer frosted glass surface with exponential dual-color neon outer bloom,
     * deep obsidian-indigo core, subtle diagonal aurora sheen, and crisp dual-gradient contour.
     */
    public static void drawFrostedGlassPanel(
            RenderAdapter r, float x, float y, float w, float h, float radius,
            int primaryArgb, int accentArgb, float opacity, float glowStrength
    ) {
        if (w <= 0f || h <= 0f) return;
        int midGlow = blendArgb(primaryArgb, accentArgb, 0.5f);
        if (glowStrength > 0.01f) {
            r.fillRoundedRect(x - 5.5f, y - 5.0f, w + 11.0f, h + 11.0f, radius + 5.5f, AxsesTheme.withAlpha(midGlow, 0.055f * glowStrength));
            r.fillRoundedRect(x - 3.4f, y - 3.0f, w + 6.8f, h + 6.8f, radius + 3.4f, AxsesTheme.withAlpha(primaryArgb, 0.095f * glowStrength));
            r.fillRoundedRect(x - 1.6f, y - 1.4f, w + 3.2f, h + 3.4f, radius + 1.6f, AxsesTheme.withAlpha(accentArgb, 0.16f * glowStrength));
        }

        // Deep obsidian frosted base
        r.fillRoundedRect(x, y, w, h, radius, AxsesTheme.withAlpha(0xFF060917, Math.min(0.97f, opacity + 0.04f)));
        // Dual-stop ambient glass tint
        fillRoundedHorizontalGradient(r, x + 0.8f, y + 0.8f, Math.max(2f, w - 1.6f), Math.max(2f, h - 1.6f), Math.max(1f, radius - 0.8f),
                AxsesTheme.withAlpha(primaryArgb, 0.19f * opacity),
                AxsesTheme.withAlpha(accentArgb, 0.14f * opacity));
        // Upper frosted specular reflection band
        float sheenH = Math.max(3.0f, Math.min(11.0f, h * 0.40f));
        r.fillRoundedRect(x + 1.5f, y + 1.2f, Math.max(2f, w - 3.0f), sheenH, Math.max(1f, radius - 1.2f), 0x14FFFFFF);

        drawHyperionContour(r, x, y, w, h, radius, 1.3f,
                AxsesTheme.withAlpha(primaryArgb, 0.94f * opacity),
                AxsesTheme.withAlpha(accentArgb, 0.94f * opacity),
                glowStrength * 0.60f);
    }

    /**
     * Signature Hyperion Ultra anti-aliased contour: multi-layer outer neon aura, dark contrast cushion,
     * dual-tone gradient rounded stroke, and top inner crystal highlight.
     */
    public static void drawHyperionContour(
            RenderAdapter r, float x, float y, float w, float h, float radius,
            float thickness, int primaryArgb, int accentArgb, float glowAlpha
    ) {
        if (w <= 0f || h <= 0f) return;
        if (glowAlpha > 0.01f) {
            r.drawGradientRoundedOutline(x - 2.4f, y - 2.4f, w + 4.8f, h + 4.8f, radius + 2.4f, thickness + 1.8f,
                    AxsesTheme.withAlpha(primaryArgb, glowAlpha * 0.22f),
                    AxsesTheme.withAlpha(accentArgb, glowAlpha * 0.22f));
            r.drawGradientRoundedOutline(x - 1.1f, y - 1.1f, w + 2.2f, h + 2.2f, radius + 1.1f, thickness + 0.9f,
                    AxsesTheme.withAlpha(primaryArgb, glowAlpha * 0.42f),
                    AxsesTheme.withAlpha(accentArgb, glowAlpha * 0.42f));
        }
        r.drawRoundedOutline(x - 0.6f, y - 0.6f, w + 1.2f, h + 1.2f, radius + 0.6f, 1.0f, 0xA802040B);
        r.drawGradientRoundedOutline(x, y, w, h, radius, thickness, primaryArgb, accentArgb);
        if (w > radius * 2f + 6f) {
            fillHorizontalGradient(r, x + radius + 1f, y + 1.0f, w - radius * 2f - 2f, 1.0f, 0x46FFFFFF, 0x14FFFFFF);
        }
    }

    /**
     * Projects and renders a volumetric 3D Chams prism (translucent glowing faces + smooth 12-edge neon contour).
     */
    public static void draw3dNeonPrism(
            RenderAdapter r, MinecraftAdapter mc,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float strokeWidth, int topColor, int bottomColor, boolean glow
    ) {
        draw3dVolumetricPrism(r, mc, minX, minY, minZ, maxX, maxY, maxZ, strokeWidth, topColor, bottomColor, glow ? 0.15f : 0.0f, glow);
    }

    public static void draw3dVolumetricPrism(
            RenderAdapter r, MinecraftAdapter mc,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float strokeWidth, int topColor, int bottomColor, float chamsAlpha, boolean glow
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

        int midCol = blendArgb(bottomColor, topColor, 0.5f);
        if (chamsAlpha > 0.005f) {
            int sideFill = AxsesTheme.withAlpha(midCol, chamsAlpha * 0.72f);
            int topFill = AxsesTheme.withAlpha(topColor, chamsAlpha);
            int botFill = AxsesTheme.withAlpha(bottomColor, chamsAlpha * 0.55f);
            // Bottom + 4 vertical faces + Top face
            r.fillQuad(sp[0][0], sp[0][1], sp[1][0], sp[1][1], sp[2][0], sp[2][1], sp[3][0], sp[3][1], botFill);
            r.fillQuad(sp[0][0], sp[0][1], sp[1][0], sp[1][1], sp[5][0], sp[5][1], sp[4][0], sp[4][1], sideFill);
            r.fillQuad(sp[1][0], sp[1][1], sp[2][0], sp[2][1], sp[6][0], sp[6][1], sp[5][0], sp[5][1], sideFill);
            r.fillQuad(sp[2][0], sp[2][1], sp[3][0], sp[3][1], sp[7][0], sp[7][1], sp[6][0], sp[6][1], sideFill);
            r.fillQuad(sp[3][0], sp[3][1], sp[0][0], sp[0][1], sp[4][0], sp[4][1], sp[7][0], sp[7][1], sideFill);
            r.fillQuad(sp[4][0], sp[4][1], sp[5][0], sp[5][1], sp[6][0], sp[6][1], sp[7][0], sp[7][1], topFill);
        }

        int[][] bottomEdges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}};
        int[][] topEdges = {{4, 5}, {5, 6}, {6, 7}, {7, 4}};
        int[][] pillars = {{0, 4}, {1, 5}, {2, 6}, {3, 7}};

        int glowBot = AxsesTheme.withAlpha(bottomColor, 0.48f);
        int glowTop = AxsesTheme.withAlpha(topColor, 0.48f);
        int glowMid = AxsesTheme.withAlpha(midCol, 0.45f);

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
        if (active) {
            drawFrostedGlassPanel(r, x, y, w, h, rad, prim, acc, t.getOpacity(), 0.72f + hoverProgress * 0.25f);
            return;
        }
        if (t.getShadowIntensity() > 0.02f) {
            r.drawShadow(x, y, w, h, rad, t.getShadowIntensity() * 0.72f, 0x000000);
        }
        int fill = AxsesTheme.withAlpha(themeManager.filterColor(t.getSurfaceColor()), t.getOpacity());
        r.fillRoundedRect(x, y, w, h, rad, fill);
        r.fillRoundedRect(x + 1.2f, y + 1.0f, Math.max(2f, w - 2.4f), Math.min(9f, h * 0.36f), Math.max(1f, rad - 1f), 0x10FFFFFF);

        if (hoverProgress > 0.01f) {
            fillRoundedHorizontalGradient(r, x + 1f, y + 1f, w - 2f, h - 2f, Math.max(1f, rad - 1f),
                    AxsesTheme.withAlpha(prim, 0.16f * hoverProgress),
                    AxsesTheme.withAlpha(acc, 0.12f * hoverProgress));
        }

        int c1 = AxsesTheme.withAlpha(themeManager.filterColor(t.getMutedColor()), highContrastBorders ? 0.72f : 0.38f);
        int c2 = AxsesTheme.withAlpha(prim, highContrastBorders ? 0.60f : 0.28f);
        r.drawGradientRoundedOutline(x, y, w, h, rad, highContrastBorders ? 1.3f : 1.08f, c1, c2);
    }

    public void drawToggleSwitch(RenderAdapter r, float x, float y, float w, float h, float animProgress) {
        AxsesTheme t = themeManager.getActiveTheme();
        float k = Math.max(0f, Math.min(1f, animProgress));
        float rad = h * 0.5f;
        int offCol = AxsesTheme.withAlpha(0xFF0E1428, 0.96f);
        int onPrimary = themeManager.filterColor(t.getPrimaryColor());
        int onAccent = themeManager.filterColor(t.getAccentColor());

        if (k > 0.08f) {
            r.fillRoundedRect(x - 2.6f, y - 2.4f, w + 5.2f, h + 4.8f, rad + 2.5f, AxsesTheme.withAlpha(onAccent, 0.22f * k));
            r.fillRoundedRect(x - 1.2f, y - 1.2f, w + 2.4f, h + 2.4f, rad + 1.2f, AxsesTheme.withAlpha(onPrimary, 0.32f * k));
        }
        if (k > 0.05f) {
            fillRoundedHorizontalGradient(r, x, y, w, h, rad,
                    blendArgb(offCol, onPrimary, k),
                    blendArgb(offCol, onAccent, k));
        } else {
            r.fillRoundedRect(x, y, w, h, rad, offCol);
        }
        r.drawRoundedOutline(x, y, w, h, rad, 1.1f, k > 0.45f ? AxsesTheme.withAlpha(0xFFFFFFFF, 0.55f) : 0x3D94A3B8);

        float knobSize = h - 3.6f;
        float knobX = x + 1.8f + (w - knobSize - 3.6f) * k;
        float knobY = y + 1.8f;
        if (k > 0.25f) {
            r.fillRoundedRect(knobX - 1.2f, knobY - 1.2f, knobSize + 2.4f, knobSize + 2.4f, (knobSize + 2.4f) * 0.5f, AxsesTheme.withAlpha(0xFFFFFFFF, 0.32f * k));
        }
        r.fillRoundedRect(knobX, knobY, knobSize, knobSize, knobSize * 0.5f, 0xFFFFFFFF);
        if (k > 0.45f) {
            r.fillRoundedRect(knobX + knobSize * 0.5f - 1.5f, y + h * 0.5f - 1.5f, 3.0f, 3.0f, 1.5f, onPrimary);
        }
    }

    public void drawSlider(RenderAdapter r, float x, float y, float w, float h, float normalized) {
        AxsesTheme t = themeManager.getActiveTheme();
        float norm = Math.max(0.0f, Math.min(1.0f, normalized));
        float trackH = Math.max(5.5f, h * 0.50f);
        float ty = y + (h - trackH) * 0.5f;

        r.fillRoundedRect(x, ty, w, trackH, trackH * 0.5f, 0xEE060A18);
        r.drawRoundedOutline(x, ty, w, trackH, trackH * 0.5f, 1.0f, 0x2E94A3B8);

        float fillW = Math.max(trackH, w * norm);
        int c1 = themeManager.filterColor(t.getPrimaryColor());
        int c2 = themeManager.filterColor(t.getAccentColor());
        r.fillRoundedRect(x - 1.0f, ty - 1.0f, fillW + 2.0f, trackH + 2.0f, (trackH + 2.0f) * 0.5f, AxsesTheme.withAlpha(c2, 0.22f));
        fillRoundedHorizontalGradient(r, x, ty, fillW, trackH, trackH * 0.5f, c1, c2);

        float knobX = x + w * norm - 5.5f;
        r.fillRoundedRect(knobX - 2.2f, y - 1.2f, 15.4f, h + 2.4f, (h + 2.4f) * 0.5f, AxsesTheme.withAlpha(c2, 0.34f));
        r.fillRoundedRect(knobX, y + 0.8f, 11.0f, h - 1.6f, (h - 1.6f) * 0.5f, 0xFFFFFFFF);
        r.fillRoundedRect(knobX + 3.2f, y + 3.2f, 4.6f, Math.max(2.4f, h - 6.4f), 2.3f, c1);
    }

    public void renderNotifications(RenderAdapter r, NotificationManager nm, float deltaSeconds, float animSpeed, boolean reducedMotion) {
        List<NotificationManager.NotificationEntry> active = nm.updateAndGetActive(deltaSeconds, animSpeed, reducedMotion);
        if (active.isEmpty()) {
            return;
        }
        AxsesTheme t = themeManager.getActiveTheme();
        long now = System.currentTimeMillis();
        float cardW = 232.0f;
        float cardH = 42.0f;
        float baseRight = r.getScaledWidth() - 10.0f;
        float currentBottom = r.getScaledHeight() - 50.0f;
        int prim = themeManager.filterColor(t.getPrimaryColor());

        for (int i = active.size() - 1; i >= 0; i--) {
            NotificationManager.NotificationEntry n = active.get(i);
            float vis = Math.max(0.0f, Math.min(1.0f, n.updateVisibility(now, 0.0f, animSpeed, reducedMotion)));
            float slideOffset = reducedMotion ? 0.0f : (1.0f - vis) * 48.0f;
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
                    icon = "✦";
                    break;
            }
            drawFrostedGlassPanel(r, x, y, cardW, cardH, 10.5f, AxsesTheme.withAlpha(prim, vis), AxsesTheme.withAlpha(accent, vis), 0.94f * vis, 0.85f * vis);

            // Glowing gradient icon gem
            fillRoundedHorizontalGradient(r, x + 8.0f, y + 9.0f, 22.0f, 22.0f, 7.0f,
                    AxsesTheme.withAlpha(prim, 0.85f * vis), AxsesTheme.withAlpha(accent, 0.92f * vis));
            fontManager.drawCentered(r, icon, x + 19.0f, y + 15.5f, AxsesTheme.withAlpha(0xFFFFFFFF, vis), 1.0f);

            // Liquid progress bar along bottom
            float progW = Math.max(4.0f, (cardW - 18.0f) * n.getRemainingFraction(now));
            fillRoundedHorizontalGradient(r, x + 9.0f, y + cardH - 4.0f, progW, 2.4f, 1.2f,
                    AxsesTheme.withAlpha(prim, vis), AxsesTheme.withAlpha(accent, vis));

            fontManager.draw(r, n.getTitle(), x + 36.0f, y + 7.5f, AxsesTheme.withAlpha(0xFFFFFFFF, vis), 0.92f);
            fontManager.draw(r, n.getMessage(), x + 36.0f, y + 21.0f, AxsesTheme.withAlpha(0xFFCBD5E1, vis), 0.84f);

            currentBottom -= (cardH + 7.0f);
        }
    }

    public static int blendArgb(int c1, int c2, float t) {
        float k = Math.max(0.0f, Math.min(1.0f, t));
        int a1 = (c1 >>> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >>> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int a = Math.round(a1 + (a2 - a1) * k);
        int r = Math.round(r1 + (r2 - r1) * k);
        int g = Math.round(g1 + (g2 - g1) * k);
        int b = Math.round(b1 + (b2 - b1) * k);
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }
}
