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
        int t = Math.max(1, Math.min(Math.round(thickness), Math.min(iw, ih) / 2));
        int ir = Math.max(0, Math.min(Math.round(radius), Math.min(iw, ih) / 2));

        if (ir <= 1) {
            fillRect(ix1, iy1, iw, t, argb);
            fillRect(ix1, iy2 - t, iw, t, argb);
            if (ih > 2 * t) {
                fillRect(ix1, iy1 + t, t, ih - 2 * t, argb);
                fillRect(ix2 - t, iy1 + t, t, ih - 2 * t, argb);
            }
            return;
        }

        // Straight middle vertical walls (single rect each -> fast & zero overdraw)
        if (ih > 2 * ir) {
            fillRect(ix1, iy1 + ir, t, ih - 2 * ir, argb);
            fillRect(ix2 - t, iy1 + ir, t, ih - 2 * ir, argb);
        }

        // Exact circular arc scanlines for top & bottom curved caps (zero overlapping rows)
        float inR = Math.max(0.0f, ir - t);
        for (int row = 0; row < ir; row++) {
            float dy = ir - (row + 0.5f);
            int outInset = Math.max(0, ir - Math.round((float) Math.sqrt(Math.max(0f, ir * ir - dy * dy))));
            int botY = iy2 - 1 - row;

            if (row < t) {
                int spanW = iw - outInset * 2;
                if (spanW > 0) {
                    fillRect(ix1 + outInset, iy1 + row, spanW, 1, argb);
                    if (botY != iy1 + row) {
                        fillRect(ix1 + outInset, botY, spanW, 1, argb);
                    }
                }
            } else {
                int inInset;
                if (dy >= inR || inR <= 0.0f) {
                    inInset = ir;
                } else {
                    inInset = Math.max(outInset + t, ir - Math.round((float) Math.sqrt(Math.max(0f, inR * inR - dy * dy))));
                }
                int segW = Math.max(t, inInset - outInset);
                if (outInset + segW * 2 >= iw) {
                    int spanW = iw - outInset * 2;
                    if (spanW > 0) {
                        fillRect(ix1 + outInset, iy1 + row, spanW, 1, argb);
                        if (botY != iy1 + row) {
                            fillRect(ix1 + outInset, botY, spanW, 1, argb);
                        }
                    }
                } else {
                    fillRect(ix1 + outInset, iy1 + row, segW, 1, argb);
                    fillRect(ix2 - outInset - segW, iy1 + row, segW, 1, argb);
                    if (botY != iy1 + row) {
                        fillRect(ix1 + outInset, botY, segW, 1, argb);
                        fillRect(ix2 - outInset - segW, botY, segW, 1, argb);
                    }
                }
            }
        }
    }

    default void drawShadow(float x, float y, float width, float height, float radius, float intensity, int baseRgb) {
        if (intensity <= 0.02f || width <= 0f || height <= 0f) {
            return;
        }
        int alpha1 = Math.max(0, Math.min(64, Math.round(44f * intensity)));
        int alpha2 = Math.max(0, Math.min(36, Math.round(22f * intensity)));
        int rgb = baseRgb & 0x00FFFFFF;
        if (alpha2 > 0) {
            fillRoundedRect(x - 2f, y + 1f, width + 4f, height + 3f, radius + 2f, (alpha2 << 24) | rgb);
        }
        if (alpha1 > 0) {
            fillRoundedRect(x + 1f, y + 2f, width, height, radius, (alpha1 << 24) | rgb);
        }
    }

    default void drawLine(float x1, float y1, float x2, float y2, float thickness, int argb) {
        if (thickness <= 0f || ((argb >>> 24) & 0xFF) == 0) {
            return;
        }
        // 2D Liang-Barsky viewport clip so off-screen projected 3D points never stretch step size
        float minX = -24.0f;
        float minY = -24.0f;
        float maxX = getScaledWidth() + 24.0f;
        float maxY = getScaledHeight() + 24.0f;
        float dx = x2 - x1;
        float dy = y2 - y1;
        float t0 = 0.0f;
        float t1 = 1.0f;
        float[] p = { -dx, dx, -dy, dy };
        float[] q = { x1 - minX, maxX - x1, y1 - minY, maxY - y1 };
        for (int k = 0; k < 4; k++) {
            if (Math.abs(p[k]) < 1e-5f) {
                if (q[k] < 0f) return;
            } else {
                float r = q[k] / p[k];
                if (p[k] < 0f) {
                    if (r > t1) return;
                    if (r > t0) t0 = r;
                } else {
                    if (r < t0) return;
                    if (r < t1) t1 = r;
                }
            }
        }
        float cx1 = x1 + t0 * dx;
        float cy1 = y1 + t0 * dy;
        float cx2 = x1 + t1 * dx;
        float cy2 = y1 + t1 * dy;
        float cdx = cx2 - cx1;
        float cdy = cy2 - cy1;

        if (Math.abs(cdy) < 0.5f) {
            float left = Math.min(cx1, cx2);
            fillRect(left, cy1 - thickness * 0.5f, Math.max(1.0f, Math.abs(cdx)), thickness, argb);
        } else if (Math.abs(cdx) < 0.5f) {
            float top = Math.min(cy1, cy2);
            fillRect(cx1 - thickness * 0.5f, top, thickness, Math.max(1.0f, Math.abs(cdy)), argb);
        } else {
            float dist = (float) Math.hypot(cdx, cdy);
            float stepSize = Math.max(0.95f, thickness * 0.72f);
            int steps = Math.max(2, Math.min(900, Math.round(dist / stepSize)));
            for (int i = 0; i <= steps; i++) {
                float t = (float) i / (float) steps;
                fillRect(cx1 + cdx * t - thickness * 0.5f, cy1 + cdy * t - thickness * 0.5f, thickness, thickness, argb);
            }
        }
    }
}
