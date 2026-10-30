package dev.axsesclient.common.render;

import dev.axsesclient.common.platform.RenderAdapter;

public final class FontManager {
    public enum FontScale {
        SMALL(1.00f),
        NORMAL(1.00f),
        LARGE(1.00f);

        private final float factor;

        FontScale(float factor) {
            this.factor = factor;
        }

        public float getFactor() {
            return factor;
        }
    }

    private FontScale fontScale = FontScale.NORMAL;
    private boolean textShadow = true;

    public FontScale getFontScale() {
        return fontScale;
    }

    public void setFontScale(FontScale fontScale) {
        if (fontScale != null) {
            this.fontScale = fontScale;
        }
    }

    public boolean isTextShadow() {
        return textShadow;
    }

    public void setTextShadow(boolean textShadow) {
        this.textShadow = textShadow;
    }

    /**
     * Snaps standard UI text to crisp 1:1 native pixel grid so Cyrillic & Latin bitmap glyphs
     * never lose strokes from non-integer OpenGL nearest-neighbor resampling.
     */
    private static float normalizeCrispScale(float requestedScale) {
        if (requestedScale <= 1.15f) {
            return 1.0f;
        }
        return requestedScale >= 1.75f ? 2.0f : requestedScale;
    }

    public void draw(RenderAdapter r, String text, float x, float y, int argb, float relativeScale) {
        if (text == null || text.isEmpty()) {
            return;
        }
        float s = normalizeCrispScale(relativeScale * fontScale.getFactor());
        r.drawText(text, Math.round(x), Math.round(y), argb, textShadow, s);
    }

    public void drawCentered(RenderAdapter r, String text, float centerX, float y, int argb, float relativeScale) {
        float w = width(r, text, relativeScale);
        draw(r, text, Math.round(centerX - w * 0.5f), Math.round(y), argb, relativeScale);
    }

    public float width(RenderAdapter r, String text, float relativeScale) {
        if (text == null || text.isEmpty()) {
            return 0.0f;
        }
        float s = normalizeCrispScale(relativeScale * fontScale.getFactor());
        return r.getTextWidth(text, s);
    }

    public float lineHeight(RenderAdapter r, float relativeScale) {
        float s = normalizeCrispScale(relativeScale * fontScale.getFactor());
        return r.getFontHeight(s);
    }
}
