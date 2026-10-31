package dev.axsesclient.common.platform;

public interface RenderAdapter {
    int getScaledWidth();
    int getScaledHeight();

    void fillRect(float x, float y, float width, float height, int argb);
    void fillGradientVertical(float x, float y, float width, float height, int topArgb, int bottomArgb);
    void drawText(String text, float x, float y, int argb, boolean shadow, float scale);
    float getTextWidth(String text, float scale);
    float getFontHeight(float scale);

    void pushScissor(int x, int y, int width, int height);
    void popScissor();

    void pushTransform(float translateX, float translateY, float scale);
    void popTransform();

    default void fillRoundedRect(float x, float y, float width, float height, float radius, int argb) {
        if (width <= 0f || height <= 0f || ((argb >>> 24) & 0xFF) == 0) {
            return;
        }
        int ix1 = Math.round(x);
        int iy1 = Math.round(y);
        int ix2 = Math.max(ix1 + 1, Math.round(x + width));
        int iy2 = Math.max(iy1 + 1, Math.round(y + height));
        int iw = ix2 - ix1;
        int ih = iy2 - iy1;
        int ir = Math.max(0, Math.min(Math.round(radius), Math.min(iw, ih) / 2));

        if (ir <= 1) {
            fillRect(ix1, iy1, iw, ih, argb);
            return;
        }

        // Exact integer scanlines: zero overlapping rows -> zero dark stripes on translucent glass!
        if (ih > 2 * ir) {
            fillRect(ix1, iy1 + ir, iw, ih - 2 * ir, argb);
        }
        for (int row = 0; row < ir; row++) {
            float dy = ir - (row + 0.5f);
            int inset = Math.max(0, ir - Math.round((float) Math.sqrt(Math.max(0f, ir * ir - dy * dy))));
            int spanW = iw - inset * 2;
            if (spanW > 0) {
                fillRect(ix1 + inset, iy1 + row, spanW, 1, argb);
                int bottomY = iy2 - 1 - row;
                if (bottomY != iy1 + row) {
                    fillRect(ix1 + inset, bottomY, spanW, 1, argb);
                }
            }
        }
    }

    default void drawRoundedOutline(float x, float y, float width, float height, float radius, float thickness, int argb) {
        if (width <= 0f || height <= 0f || thickness <= 0f || ((argb >>> 24) & 0xFF) == 0) {
            return;
        }
        int ix1 = Math.round(x);
        int iy1 = Math.round(y);
        int ix2 = Math.max(ix1 + 2, Math.round(x + width));
        int iy2 = Math.max(iy1 + 2, Math.round(y + height));
        int iw = ix2 - ix1;
        int ih = iy2 - iy1;
        int t = Math.max(1, Math.round(thickness));
        int inset = radius >= 4f ? 2 : (radius >= 2f ? 1 : 0);

        // Non-overlapping top, bottom, left, right border segments + crisp corner pixels
        fillRect(ix1 + inset, iy1, Math.max(1, iw - inset * 2), t, argb);
        fillRect(ix1 + inset, iy2 - t, Math.max(1, iw - inset * 2), t, argb);
        fillRect(ix1, iy1 + inset, t, Math.max(1, ih - inset * 2), argb);
        fillRect(ix2 - t, iy1 + inset, t, Math.max(1, ih - inset * 2), argb);
        if (inset == 2) {
            fillRect(ix1 + 1, iy1 + 1, 1, 1, argb);
            fillRect(ix2 - 2, iy1 + 1, 1, 1, argb);
            fillRect(ix1 + 1, iy2 - 2, 1, 1, argb);
            fillRect(ix2 - 2, iy2 - 2, 1, 1, argb);
        }
    }

    default void drawShadow(float x, float y, float width, float height, float radius, float intensity, int baseRgb) {
        if (intensity <= 0.02f || width <= 0f || height <= 0f) {
            return;
        }
        // Subtle 2px drop shadow behind card (only drawn outside card bounds on right/bottom or soft halo)
        int alpha = Math.max(0, Math.min(72, Math.round(48f * intensity)));
        if (alpha > 0) {
            int color = (alpha << 24) | (baseRgb & 0x00FFFFFF);
            fillRoundedRect(x + 1, y + 2, width, height, radius, color);
        }
    }

    default void drawLine(float x1, float y1, float x2, float y2, float thickness, int argb) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        if (Math.abs(dy) < 0.5f) {
            float left = Math.min(x1, x2);
            fillRect(left, y1 - thickness * 0.5f, Math.abs(dx), thickness, argb);
        } else if (Math.abs(dx) < 0.5f) {
            float top = Math.min(y1, y2);
            fillRect(x1 - thickness * 0.5f, top, thickness, Math.abs(dy), argb);
        } else {
            float dist = (float) Math.hypot(dx, dy);
            float stepSize = Math.max(1.25f, thickness * 0.9f);
            int steps = Math.max(2, Math.min(140, Math.round(dist / stepSize)));
            float dotSize = Math.max(thickness, dist / steps + 0.35f);
            for (int i = 0; i <= steps; i++) {
                float t = (float) i / (float) steps;
                fillRect(x1 + dx * t - dotSize * 0.5f, y1 + dy * t - dotSize * 0.5f, dotSize, dotSize, argb);
            }
        }
    }
}
