package dev.funtime.auctionhelper.ui;

/**
 * Version independent 2D drawing surface.
 *
 * <p>Each Minecraft module implements this on top of its own render API
 * ({@code GuiGraphics} for 1.21.x, {@code PoseStack + GuiComponent} for 1.16.5).
 * The whole GUI is written once against this interface.</p>
 */
public interface UiCanvas {
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

    default void drawItemStack(String itemId, int x, int y) {
        // Optional: the Minecraft layer may render a real item icon here.
    }

    default void drawTextCentered(String text, float centerX, float y, int argb, boolean shadow, float scale) {
        float width = getTextWidth(text, scale);
        drawText(text, centerX - width / 2f, y, argb, shadow, scale);
    }

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
        if (ih > 2 * ir) {
            fillRect(ix1, iy1 + ir, iw, ih - 2 * ir, argb);
        }
        for (int row = 0; row < ir; row++) {
            float dy = ir - (row + 0.5f);
            int inset = Math.max(0, ir - Math.round((float) Math.sqrt(Math.max(0f, ir * ir - dy * dy))));
            int span = iw - inset * 2;
            if (span > 0) {
                fillRect(ix1 + inset, iy1 + row, span, 1, argb);
                int bottom = iy2 - 1 - row;
                if (bottom != iy1 + row) {
                    fillRect(ix1 + inset, bottom, span, 1, argb);
                }
            }
        }
    }

    default void drawRoundedOutline(float x, float y, float width, float height, float radius,
                                    float thickness, int argb) {
        if (width <= 0f || height <= 0f || thickness <= 0f) {
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
        fillRect(ix1 + inset, iy1, iw - inset * 2, t, argb);
        fillRect(ix1 + inset, iy2 - t, iw - inset * 2, t, argb);
        fillRect(ix1, iy1 + inset, t, ih - inset * 2, argb);
        fillRect(ix2 - t, iy1 + inset, t, ih - inset * 2, argb);
    }

    default void drawLine(float x1, float y1, float x2, float y2, float thickness, int argb) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        if (Math.abs(dy) < 0.5f) {
            fillRect(Math.min(x1, x2), y1 - thickness * 0.5f, Math.abs(dx), thickness, argb);
        } else if (Math.abs(dx) < 0.5f) {
            fillRect(x1 - thickness * 0.5f, Math.min(y1, y2), thickness, Math.abs(dy), argb);
        } else {
            int steps = Math.max(2, Math.min(96, Math.round((float) Math.hypot(dx, dy) / 3f)));
            for (int i = 0; i <= steps; i++) {
                float t = (float) i / steps;
                fillRect(x1 + dx * t, y1 + dy * t, Math.max(1f, thickness), Math.max(1f, thickness), argb);
            }
        }
    }
}
