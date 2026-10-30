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
        float r = Math.max(0f, Math.min(radius, Math.min(width, height) * 0.5f));
        if (r < 1.5f) {
            fillRect(x, y, width, height, argb);
            return;
        }
        int steps = Math.min(8, Math.max(2, Math.round(r)));
        // Center body
        fillRect(x, y + r, width, height - 2f * r, argb);
        // Top and bottom rounded caps via horizontal scan bands
        for (int i = 0; i < steps; i++) {
            float dy = ((i + 0.5f) / steps) * r;
            float chord = (float) Math.sqrt(Math.max(0f, r * r - (r - dy) * (r - dy)));
            float inset = r - chord;
            float bandH = r / steps + 0.35f;
            fillRect(x + inset, y + (i * r / steps), Math.max(0f, width - 2f * inset), bandH, argb);
            fillRect(x + inset, y + height - ((i + 1) * r / steps), Math.max(0f, width - 2f * inset), bandH, argb);
        }
    }

    default void drawRoundedOutline(float x, float y, float width, float height, float radius, float thickness, int argb) {
        if (width <= 0f || height <= 0f || thickness <= 0f || ((argb >>> 24) & 0xFF) == 0) {
            return;
        }
        float t = Math.max(1f, thickness);
        float r = Math.max(0f, Math.min(radius, Math.min(width, height) * 0.5f));
        float inset = Math.min(r * 0.6f, 4f);
        // Top & bottom edges
        fillRect(x + inset, y, Math.max(0f, width - 2f * inset), t, argb);
        fillRect(x + inset, y + height - t, Math.max(0f, width - 2f * inset), t, argb);
        // Left & right edges
        fillRect(x, y + inset, t, Math.max(0f, height - 2f * inset), argb);
        fillRect(x + width - t, y + inset, t, Math.max(0f, height - 2f * inset), argb);
        // Corner chamfers
        if (inset >= 1.5f) {
            fillRect(x + inset * 0.5f, y + inset * 0.5f, t, t, argb);
            fillRect(x + width - inset * 0.5f - t, y + inset * 0.5f, t, t, argb);
            fillRect(x + inset * 0.5f, y + height - inset * 0.5f - t, t, t, argb);
            fillRect(x + width - inset * 0.5f - t, y + height - inset * 0.5f - t, t, t, argb);
        }
    }

    default void drawShadow(float x, float y, float width, float height, float radius, float intensity, int baseRgb) {
        if (intensity <= 0.01f || width <= 0f || height <= 0f) {
            return;
        }
        int layers = 4;
        for (int i = layers; i >= 1; i--) {
            float spread = i * 2.0f;
            int alpha = Math.max(0, Math.min(90, Math.round(26f * intensity * (layers - i + 1) / layers)));
            int color = (alpha << 24) | (baseRgb & 0x00FFFFFF);
            fillRoundedRect(x - spread, y - spread + 1.5f, width + spread * 2f, height + spread * 2f, radius + spread, color);
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
            int steps = Math.max(2, Math.round(Math.max(Math.abs(dx), Math.abs(dy))));
            for (int i = 0; i <= steps; i++) {
                float t = (float) i / (float) steps;
                fillRect(x1 + dx * t - thickness * 0.5f, y1 + dy * t - thickness * 0.5f, thickness, thickness, argb);
            }
        }
    }
}
