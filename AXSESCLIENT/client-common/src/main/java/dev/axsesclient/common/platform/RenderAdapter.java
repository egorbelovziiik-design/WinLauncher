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

    /**
     * Enters native physical-pixel coordinate space (e.g. 2x/3x GUI scale -> 1:1 1080p/1440p pixels)
     * so rounded contours, rings, and 3D ESP lines render crisp and smooth without coarse 2x2 stair-steps.
     */
    default float beginHiDpiSubPixel() {
        return 1.0f;
    }

    default void endHiDpiSubPixel() {
    }

    default void fillRoundedRect(float x, float y, float width, float height, float radius, int argb) {
        if (width <= 0f || height <= 0f || ((argb >>> 24) & 0xFF) == 0) {
            return;
        }
        float s = beginHiDpiSubPixel();
        try {
            int ix1 = Math.round(x * s);
            int iy1 = Math.round(y * s);
            int ix2 = Math.max(ix1 + 1, Math.round((x + width) * s));
            int iy2 = Math.max(iy1 + 1, Math.round((y + height) * s));
            int iw = ix2 - ix1;
            int ih = iy2 - iy1;
            float r = Math.max(0f, Math.min(radius * s, Math.min(iw, ih) * 0.5f));

            if (r <= 1.2f) {
                fillRect(ix1, iy1, iw, ih, argb);
                return;
            }

            int ir = Math.min(Math.min(iw, ih) / 2, (int) Math.ceil(r));
            int baseAlpha = (argb >>> 24) & 0xFF;
            int rgb = argb & 0x00FFFFFF;

            if (ih > 2 * ir) {
                fillRect(ix1, iy1 + ir, iw, ih - 2 * ir, argb);
            }

            int prevSolidInset = ir;
            for (int row = 0; row < ir; row++) {
                float dy = r - (row + 0.5f);
                float exactDx = dy >= r ? 0f : (float) Math.sqrt(Math.max(0f, r * r - dy * dy));
                // Solid span starts strictly inside the SDF boundary (dist <= -0.45) so zero overdraw
                float innerDx = (dy >= r - 0.45f) ? 0f : (float) Math.sqrt(Math.max(0f, (r - 0.45f) * (r - 0.45f) - dy * dy));
                int solidInset = Math.min(ir, Math.max(0, (int) Math.ceil(r - innerDx)));
                int outerStart = Math.max(0, Math.min(prevSolidInset, (int) Math.floor(r - exactDx) - 1));
                prevSolidInset = solidInset;

                int botY = iy2 - 1 - row;
                int spanW = iw - solidInset * 2;
                if (spanW > 0) {
                    fillRect(ix1 + solidInset, iy1 + row, spanW, 1, argb);
                    if (botY != iy1 + row) {
                        fillRect(ix1 + solidInset, botY, spanW, 1, argb);
                    }
                }

                // Exact Euclidean SDF anti-aliased fringe along both shallow & steep arc pixels
                for (int col = outerStart; col < solidInset; col++) {
                    if (col > iw - 1 - col) break;
                    float qx = r - (col + 0.5f);
                    float dist = (float) Math.hypot(qx, dy) - r;
                    float cov = Math.max(0f, Math.min(1f, 0.52f - dist));
                    int aaAlpha = Math.round(baseAlpha * cov);
                    if (aaAlpha > 3) {
                        int aaArgb = (aaAlpha << 24) | rgb;
                        fillRect(ix1 + col, iy1 + row, 1, 1, aaArgb);
                        if (ix2 - 1 - col != ix1 + col) {
                            fillRect(ix2 - 1 - col, iy1 + row, 1, 1, aaArgb);
                        }
                        if (botY != iy1 + row) {
                            fillRect(ix1 + col, botY, 1, 1, aaArgb);
                            if (ix2 - 1 - col != ix1 + col) {
                                fillRect(ix2 - 1 - col, botY, 1, 1, aaArgb);
                            }
                        }
                    }
                }
            }
        } finally {
            endHiDpiSubPixel();
        }
    }

    default void drawRoundedOutline(float x, float y, float width, float height, float radius, float thickness, int argb) {
        drawGradientRoundedOutline(x, y, width, height, radius, thickness, argb, argb);
    }

    default void drawGradientRoundedOutline(
            float x, float y, float width, float height, float radius, float thickness, int startArgb, int endArgb
    ) {
        if (width <= 0f || height <= 0f || thickness <= 0f) {
            return;
        }
        int a1 = (startArgb >>> 24) & 0xFF;
        int a2 = (endArgb >>> 24) & 0xFF;
        if (a1 == 0 && a2 == 0) {
            return;
        }
        float s = beginHiDpiSubPixel();
        try {
            int ix1 = Math.round(x * s);
            int iy1 = Math.round(y * s);
            int ix2 = Math.max(ix1 + 2, Math.round((x + width) * s));
            int iy2 = Math.max(iy1 + 2, Math.round((y + height) * s));
            int iw = ix2 - ix1;
            int ih = iy2 - iy1;
            float tPx = Math.max(1.15f, Math.min(thickness * s, Math.min(iw, ih) * 0.5f));
            int tInt = Math.max(1, Math.round(tPx));
            float rOut = Math.max(0f, Math.min(radius * s, Math.min(iw, ih) * 0.5f));
            boolean uniform = (startArgb == endArgb);

            if (rOut <= 1.2f) {
                fillRect(ix1, iy1, iw, tInt, startArgb);
                fillRect(ix1, iy2 - tInt, iw, tInt, endArgb);
                if (ih > 2 * tInt) {
                    fillGradientVertical(ix1, iy1 + tInt, tInt, ih - 2 * tInt, startArgb, lerpColor(startArgb, endArgb, 0.7f));
                    fillGradientVertical(ix2 - tInt, iy1 + tInt, tInt, ih - 2 * tInt, lerpColor(startArgb, endArgb, 0.3f), endArgb);
                }
                return;
            }

            int ir = Math.min(Math.min(iw, ih) / 2, (int) Math.ceil(rOut + 0.75f));

            // Straight top & bottom horizontal runs between corner quadrants
            int horizW = iw - 2 * ir;
            if (horizW > 0) {
                if (uniform) {
                    fillRect(ix1 + ir, iy1, horizW, tInt, startArgb);
                    fillRect(ix1 + ir, iy2 - tInt, horizW, tInt, endArgb);
                } else {
                    int segs = Math.max(1, Math.min(18, horizW / 12 + 1));
                    int prevX = ix1 + ir;
                    for (int i = 0; i < segs; i++) {
                        int nextX = (i == segs - 1) ? (ix2 - ir) : (ix1 + ir + (horizW * (i + 1)) / segs);
                        if (nextX > prevX) {
                            float gtTop = (float) ((prevX - ix1) * 0.65f) / Math.max(1f, iw);
                            float gtBot = 0.35f + (float) ((prevX - ix1) * 0.65f) / Math.max(1f, iw);
                            fillRect(prevX, iy1, nextX - prevX, tInt, lerpColor(startArgb, endArgb, gtTop));
                            fillRect(prevX, iy2 - tInt, nextX - prevX, tInt, lerpColor(startArgb, endArgb, gtBot));
                            prevX = nextX;
                        }
                    }
                }
            }

            // Straight left & right vertical walls between corner quadrants
            int vertH = ih - 2 * ir;
            if (vertH > 0) {
                if (uniform) {
                    fillRect(ix1, iy1 + ir, tInt, vertH, startArgb);
                    fillRect(ix2 - tInt, iy1 + ir, tInt, vertH, endArgb);
                } else {
                    fillGradientVertical(ix1, iy1 + ir, tInt, vertH,
                            lerpColor(startArgb, endArgb, 0.12f), lerpColor(startArgb, endArgb, 0.72f));
                    fillGradientVertical(ix2 - tInt, iy1 + ir, tInt, vertH,
                            lerpColor(startArgb, endArgb, 0.28f), lerpColor(startArgb, endArgb, 0.88f));
                }
            }

            // Analytical Euclidean SDF ring anti-aliasing in all 4 corner quadrants (silky smooth at any radius!)
            float rIn = Math.max(0f, rOut - tPx);
            int tlCol = startArgb;
            int trCol = uniform ? startArgb : lerpColor(startArgb, endArgb, 0.42f);
            int blCol = uniform ? startArgb : lerpColor(startArgb, endArgb, 0.58f);
            int brCol = endArgb;

            for (int row = 0; row < ir; row++) {
                float dy = rOut - (row + 0.5f);
                int botY = iy2 - 1 - row;
                for (int col = 0; col < ir; col++) {
                    if (col > iw - 1 - col) break;
                    float dx = rOut - (col + 0.5f);
                    float rad;
                    if (dx <= 0f && dy <= 0f) {
                        continue;
                    } else if (dx <= 0f) {
                        rad = dy;
                    } else if (dy <= 0f) {
                        rad = dx;
                    } else {
                        rad = (float) Math.hypot(dx, dy);
                    }
                    if (rad > rOut + 0.65f || rad < rIn - 0.65f) {
                        continue;
                    }
                    float outCov = Math.max(0f, Math.min(1f, rOut + 0.55f - rad));
                    float inCov = rIn <= 0.05f ? 1.0f : Math.max(0f, Math.min(1f, rad - (rIn - 0.55f)));
                    float cov = outCov * inCov;
                    if (cov <= 0.02f) continue;

                    drawAaPixel(ix1 + col, iy1 + row, tlCol, cov);
                    if (ix2 - 1 - col != ix1 + col) {
                        drawAaPixel(ix2 - 1 - col, iy1 + row, trCol, cov);
                    }
                    if (botY != iy1 + row) {
                        drawAaPixel(ix1 + col, botY, blCol, cov);
                        if (ix2 - 1 - col != ix1 + col) {
                            drawAaPixel(ix2 - 1 - col, botY, brCol, cov);
                        }
                    }
                }
            }
        } finally {
            endHiDpiSubPixel();
        }
    }

    default void drawAaPixel(int px, int py, int baseArgb, float coverage) {
        int a = Math.round(((baseArgb >>> 24) & 0xFF) * coverage);
        if (a > 3) {
            fillRect(px, py, 1, 1, (a << 24) | (baseArgb & 0x00FFFFFF));
        }
    }

    static int lerpColor(int c1, int c2, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        int a1 = (c1 >>> 24) & 0xFF, r1 = (c1 >>> 16) & 0xFF, g1 = (c1 >>> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >>> 24) & 0xFF, r2 = (c2 >>> 16) & 0xFF, g2 = (c2 >>> 8) & 0xFF, b2 = c2 & 0xFF;
        int a = Math.round(a1 + (a2 - a1) * k);
        int r = Math.round(r1 + (r2 - r1) * k);
        int g = Math.round(g1 + (g2 - g1) * k);
        int b = Math.round(b1 + (b2 - b1) * k);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    default void drawShadow(float x, float y, float width, float height, float radius, float intensity, int baseRgb) {
        if (intensity <= 0.02f || width <= 0f || height <= 0f) {
            return;
        }
        int rgb = baseRgb & 0x00FFFFFF;
        int aOuter = Math.max(0, Math.min(48, Math.round(24f * intensity)));
        int aMid = Math.max(0, Math.min(68, Math.round(38f * intensity)));
        int aCore = Math.max(0, Math.min(84, Math.round(52f * intensity)));
        if (aOuter > 0) {
            fillRoundedRect(x - 3.0f, y - 1.5f, width + 6.0f, height + 5.0f, radius + 3.0f, (aOuter << 24) | rgb);
        }
        if (aMid > 0) {
            fillRoundedRect(x - 1.5f, y + 0.5f, width + 3.0f, height + 3.0f, radius + 1.5f, (aMid << 24) | rgb);
        }
        if (aCore > 0) {
            fillRoundedRect(x + 0.5f, y + 1.5f, width, height + 1.0f, radius, (aCore << 24) | rgb);
        }
    }

    default void drawLine(float x1, float y1, float x2, float y2, float thickness, int argb) {
        if (thickness <= 0f || ((argb >>> 24) & 0xFF) == 0) {
            return;
        }
        // 2D Liang-Barsky viewport clip so off-screen projected 3D points never stretch step counts
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

        float s = beginHiDpiSubPixel();
        try {
            float cx1 = (x1 + t0 * dx) * s;
            float cy1 = (y1 + t0 * dy) * s;
            float cx2 = (x1 + t1 * dx) * s;
            float cy2 = (y1 + t1 * dy) * s;
            float cdx = cx2 - cx1;
            float cdy = cy2 - cy1;
            float wPx = Math.max(1.1f, thickness * s);

            if (Math.abs(cdy) < 0.35f) {
                float left = Math.min(cx1, cx2);
                fillRect(Math.round(left), Math.round(cy1 - wPx * 0.5f), Math.max(1, Math.round(Math.abs(cdx))), Math.max(1, Math.round(wPx)), argb);
                return;
            }
            if (Math.abs(cdx) < 0.35f) {
                float top = Math.min(cy1, cy2);
                fillRect(Math.round(cx1 - wPx * 0.5f), Math.round(top), Math.max(1, Math.round(wPx)), Math.max(1, Math.round(Math.abs(cdy))), argb);
                return;
            }

            // Anti-aliased Xiaolin-Wu / perpendicular SDF stroke along major axis (zero dotted gaps!)
            int baseAlpha = (argb >>> 24) & 0xFF;
            int rgb = argb & 0x00FFFFFF;
            float halfW = wPx * 0.5f;
            int spanRadius = (int) Math.ceil(halfW + 0.85f);
            boolean xMajor = Math.abs(cdx) >= Math.abs(cdy);
            float len = (float) Math.hypot(cdx, cdy);
            float invLen = len > 1e-4f ? (1.0f / len) : 1.0f;
            // Perpendicular scale factor correcting vertical/horizontal slice distance to true Euclidean perpendicular distance
            float orthoScale = xMajor ? Math.abs(cdx) * invLen : Math.abs(cdy) * invLen;

            if (xMajor) {
                if (cx1 > cx2) {
                    float tx = cx1; cx1 = cx2; cx2 = tx;
                    float ty = cy1; cy1 = cy2; cy2 = ty;
                    cdx = cx2 - cx1; cdy = cy2 - cy1;
                }
                int startX = (int) Math.floor(cx1);
                int endX = (int) Math.ceil(cx2);
                float slope = cdy / cdx;
                for (int px = startX; px <= endX; px++) {
                    float centerY = cy1 + (px + 0.5f - cx1) * slope;
                    int baseY = Math.round(centerY);
                    for (int off = -spanRadius; off <= spanRadius; off++) {
                        int py = baseY + off;
                        float perpDist = Math.abs((py + 0.5f) - centerY) * orthoScale;
                        float cov = Math.max(0f, Math.min(1f, halfW + 0.55f - perpDist));
                        int a = Math.round(baseAlpha * cov);
                        if (a > 3) {
                            fillRect(px, py, 1, 1, (a << 24) | rgb);
                        }
                    }
                }
            } else {
                if (cy1 > cy2) {
                    float tx = cx1; cx1 = cx2; cx2 = tx;
                    float ty = cy1; cy1 = cy2; cy2 = ty;
                    cdx = cx2 - cx1; cdy = cy2 - cy1;
                }
                int startY = (int) Math.floor(cy1);
                int endY = (int) Math.ceil(cy2);
                float invSlope = cdx / cdy;
                for (int py = startY; py <= endY; py++) {
                    float centerX = cx1 + (py + 0.5f - cy1) * invSlope;
                    int baseX = Math.round(centerX);
                    for (int off = -spanRadius; off <= spanRadius; off++) {
                        int px = baseX + off;
                        float perpDist = Math.abs((px + 0.5f) - centerX) * orthoScale;
                        float cov = Math.max(0f, Math.min(1f, halfW + 0.55f - perpDist));
                        int a = Math.round(baseAlpha * cov);
                        if (a > 3) {
                            fillRect(px, py, 1, 1, (a << 24) | rgb);
                        }
                    }
                }
            }
        } finally {
            endHiDpiSubPixel();
        }
    }

    default void drawNeonLine(float x1, float y1, float x2, float y2, float thickness, int coreArgb, int glowArgb) {
        int glowA = (glowArgb >>> 24) & 0xFF;
        if (glowA > 4) {
            drawLine(x1, y1, x2, y2, thickness * 2.6f, ((glowA / 3) << 24) | (glowArgb & 0x00FFFFFF));
            drawLine(x1, y1, x2, y2, thickness * 1.6f, ((glowA * 2 / 3) << 24) | (glowArgb & 0x00FFFFFF));
        }
        drawLine(x1, y1, x2, y2, thickness, coreArgb);
    }
}
