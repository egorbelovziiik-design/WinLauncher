package dev.axsesclient.common.hud;

import com.google.gson.JsonObject;
import dev.axsesclient.common.modules.Module;
import dev.axsesclient.common.platform.MinecraftAdapter;
import dev.axsesclient.common.platform.RenderAdapter;
import dev.axsesclient.common.render.FontManager;
import dev.axsesclient.common.theme.AxsesTheme;
import dev.axsesclient.common.theme.ThemeManager;

public final class HudElement {
    public enum TextAlignment {
        LEFT,
        CENTER,
        RIGHT
    }

    private final String id;
    private final String title;
    private final Module boundModule;

    // Normalized position [0.0 .. 1.0] relative to canvas width/height so resolution changes scale properly
    private float defaultNormX;
    private float defaultNormY;
    private float defaultWidth;
    private float defaultHeight;

    private float normX;
    private float normY;
    private float width;
    private float height;
    private float scale = 1.0f;
    private float opacity = 0.88f;
    private boolean showBackground = true;
    private boolean showShadow = true;
    private boolean showOutline = true;
    private TextAlignment alignment = TextAlignment.LEFT;
    private boolean locked = false;
    private String groupId = "";

    public HudElement(String id, String title, Module boundModule, float normX, float normY, float width, float height) {
        this.id = id;
        this.title = title;
        this.boundModule = boundModule;
        this.defaultNormX = clamp01(normX);
        this.defaultNormY = clamp01(normY);
        this.defaultWidth = Math.max(44.0f, width);
        this.defaultHeight = Math.max(18.0f, height);
        resetLayout();
    }

    public void resetLayout() {
        this.normX = defaultNormX;
        this.normY = defaultNormY;
        this.width = defaultWidth;
        this.height = defaultHeight;
        this.scale = 1.0f;
        this.opacity = 0.88f;
        this.showBackground = true;
        this.showShadow = true;
        this.showOutline = true;
        this.alignment = TextAlignment.LEFT;
        this.locked = false;
        this.groupId = "";
    }

    public boolean isVisibleInGame() {
        return boundModule == null || boundModule.isEnabled();
    }

    public float getPixelX(float canvasW) {
        float scaledW = getScaledWidth();
        float maxLeft = Math.max(0.0f, canvasW - scaledW);
        return maxLeft * normX;
    }

    public float getPixelY(float canvasH) {
        float scaledH = getScaledHeight();
        float maxTop = Math.max(0.0f, canvasH - scaledH);
        return maxTop * normY;
    }

    public void setPixelPosition(float px, float py, float canvasW, float canvasH) {
        if (locked) {
            return;
        }
        float maxLeft = Math.max(1.0f, canvasW - getScaledWidth());
        float maxTop = Math.max(1.0f, canvasH - getScaledHeight());
        this.normX = clamp01(px / maxLeft);
        this.normY = clamp01(py / maxTop);
    }

    public float getScaledWidth() {
        return width * scale;
    }

    public float getScaledHeight() {
        return height * scale;
    }

    public void renderWidget(
            RenderAdapter r,
            MinecraftAdapter mc,
            ThemeManager themeManager,
            FontManager fontManager,
            float canvasW,
            float canvasH,
            boolean editorMode,
            boolean selected
    ) {
        if (!editorMode && !isVisibleInGame()) {
            return;
        }
        String text = boundModule != null ? boundModule.getHudBadgeText(mc) : null;
        if ((text == null || text.isEmpty()) && editorMode) {
            text = title + " [Preview]";
        }
        if (text == null || text.isEmpty()) {
            return;
        }

        AxsesTheme t = themeManager.getActiveTheme();
        float px = getPixelX(canvasW);
        float py = getPixelY(canvasH);
        float sw = getScaledWidth();
        float sh = getScaledHeight();
        float rad = Math.min(t.getCornerRadius(), sh * 0.45f);

        if (showShadow && t.getShadowIntensity() > 0.02f) {
            r.drawShadow(px, py, sw, sh, rad, t.getShadowIntensity() * 0.55f * opacity, 0x000000);
        }
        if (showBackground) {
            int bg = AxsesTheme.withAlpha(themeManager.filterColor(t.getSurfaceColor()), opacity);
            r.fillRoundedRect(px, py, sw, sh, rad, bg);
        }
        if (showOutline || editorMode) {
            int outCol = selected
                    ? themeManager.filterColor(t.getAccentColor())
                    : (locked && editorMode
                        ? themeManager.filterColor(t.getWarningColor())
                        : AxsesTheme.withAlpha(themeManager.filterColor(t.getPrimaryColor()), opacity * 0.65f));
            r.drawRoundedOutline(px, py, sw, sh, rad, selected ? 1.5f : 1.0f, outCol);
        }

        float textScale = 0.90f * scale;
        float tw = fontManager.width(r, text, textScale);
        float th = fontManager.lineHeight(r, textScale);
        float tx;
        switch (alignment) {
            case CENTER:
                tx = px + (sw - tw) * 0.5f;
                break;
            case RIGHT:
                tx = px + sw - tw - 6.0f * scale;
                break;
            default:
                tx = px + 6.0f * scale;
                break;
        }
        float ty = py + (sh - th) * 0.5f + 1.0f;
        int txtCol = AxsesTheme.withAlpha(t.getTextColor(), Math.max(0.45f, opacity));
        fontManager.draw(r, text, tx, ty, txtCol, textScale);

        if (editorMode && !locked) {
            // Bottom-right resize grip indicator
            int gripCol = selected ? themeManager.filterColor(t.getAccentColor()) : t.getMutedColor();
            r.fillRect(px + sw - 5.0f, py + sh - 5.0f, 4.0f, 4.0f, gripCol);
        }
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public Module getBoundModule() { return boundModule; }
    public float getNormX() { return normX; }
    public void setNormX(float normX) { if (!locked) this.normX = clamp01(normX); }
    public float getNormY() { return normY; }
    public void setNormY(float normY) { if (!locked) this.normY = clamp01(normY); }
    public float getWidth() { return width; }
    public void setWidth(float width) { if (!locked) this.width = Math.max(44.0f, Math.min(420.0f, width)); }
    public float getHeight() { return height; }
    public void setHeight(float height) { if (!locked) this.height = Math.max(16.0f, Math.min(220.0f, height)); }
    public float getScale() { return scale; }
    public void setScale(float scale) { this.scale = Math.max(0.5f, Math.min(2.5f, scale)); }
    public float getOpacity() { return opacity; }
    public void setOpacity(float opacity) { this.opacity = Math.max(0.20f, Math.min(1.0f, opacity)); }
    public boolean isShowBackground() { return showBackground; }
    public void setShowBackground(boolean showBackground) { this.showBackground = showBackground; }
    public boolean isShowShadow() { return showShadow; }
    public void setShowShadow(boolean showShadow) { this.showShadow = showShadow; }
    public boolean isShowOutline() { return showOutline; }
    public void setShowOutline(boolean showOutline) { this.showOutline = showOutline; }
    public TextAlignment getAlignment() { return alignment; }
    public void setAlignment(TextAlignment alignment) { if (alignment != null) this.alignment = alignment; }
    public void cycleAlignment() {
        TextAlignment[] vals = TextAlignment.values();
        this.alignment = vals[(alignment.ordinal() + 1) % vals.length];
    }
    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId == null ? "" : groupId.trim(); }

    private static float clamp01(float v) {
        if (Float.isNaN(v) || Float.isInfinite(v)) return 0.0f;
        return Math.max(0.0f, Math.min(1.0f, v));
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("normX", Float.valueOf(normX));
        o.addProperty("normY", Float.valueOf(normY));
        o.addProperty("width", Float.valueOf(width));
        o.addProperty("height", Float.valueOf(height));
        o.addProperty("scale", Float.valueOf(scale));
        o.addProperty("opacity", Float.valueOf(opacity));
        o.addProperty("showBackground", Boolean.valueOf(showBackground));
        o.addProperty("showShadow", Boolean.valueOf(showShadow));
        o.addProperty("showOutline", Boolean.valueOf(showOutline));
        o.addProperty("alignment", alignment.name());
        o.addProperty("locked", Boolean.valueOf(locked));
        o.addProperty("groupId", groupId);
        return o;
    }

    public void fromJson(JsonObject o) {
        if (o == null) return;
        this.locked = false; // unlock temporarily to restore coordinates
        if (o.has("normX")) setNormX(o.get("normX").getAsFloat());
        if (o.has("normY")) setNormY(o.get("normY").getAsFloat());
        if (o.has("width")) setWidth(o.get("width").getAsFloat());
        if (o.has("height")) setHeight(o.get("height").getAsFloat());
        if (o.has("scale")) setScale(o.get("scale").getAsFloat());
        if (o.has("opacity")) setOpacity(o.get("opacity").getAsFloat());
        if (o.has("showBackground")) setShowBackground(o.get("showBackground").getAsBoolean());
        if (o.has("showShadow")) setShowShadow(o.get("showShadow").getAsBoolean());
        if (o.has("showOutline")) setShowOutline(o.get("showOutline").getAsBoolean());
        if (o.has("alignment")) {
            try {
                setAlignment(TextAlignment.valueOf(o.get("alignment").getAsString()));
            } catch (Exception ignored) {
            }
        }
        if (o.has("groupId")) setGroupId(o.get("groupId").getAsString());
        if (o.has("locked")) setLocked(o.get("locked").getAsBoolean());
    }
}
