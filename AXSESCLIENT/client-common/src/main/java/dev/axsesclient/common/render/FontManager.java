package dev.axsesclient.common.render;

import dev.axsesclient.common.platform.RenderAdapter;

public final class FontManager {
    public enum FontScale {
        SMALL(0.90f),
        NORMAL(1.00f),
        LARGE(1.15f);

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

    public void draw(RenderAdapter r, String text, float x, float y, int argb, float relativeScale) {
        if (text == null || text.isEmpty()) {
            return;
        }
        r.drawText(text, x, y, argb, textShadow, relativeScale * fontScale.getFactor());
    }

    public void drawCentered(RenderAdapter r, String text, float centerX, float y, int argb, float relativeScale) {
        float w = width(r, text, relativeScale);
        draw(r, text, centerX - w * 0.5f, y, argb, relativeScale);
    }

    public float width(RenderAdapter r, String text, float relativeScale) {
        if (text == null || text.isEmpty()) {
            return 0.0f;
        }
        return r.getTextWidth(text, relativeScale * fontScale.getFactor());
    }

    public float lineHeight(RenderAdapter r, float relativeScale) {
        return r.getFontHeight(relativeScale * fontScale.getFactor());
    }
}
