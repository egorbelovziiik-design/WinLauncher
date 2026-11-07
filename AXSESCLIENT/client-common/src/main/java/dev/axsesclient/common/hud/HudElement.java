package dev.axsesclient.common.hud;

import com.google.gson.JsonObject;
import dev.axsesclient.common.modules.Module;
import dev.axsesclient.common.platform.MinecraftAdapter;
import dev.axsesclient.common.platform.RenderAdapter;
import dev.axsesclient.common.render.FontManager;
import dev.axsesclient.common.render.RenderManager;
import dev.axsesclient.common.theme.AxsesTheme;
import dev.axsesclient.common.theme.ThemeManager;

public final class HudElement {
    public enum TextAlignment {
        LEFT,
        CENTER,
        RIGHT
    }

    public interface CustomHudPainter {
        boolean paint(
                RenderAdapter r,
                MinecraftAdapter mc,
                ThemeManager themeManager,
                FontManager fontManager,
                float px,
                float py,
                float sw,
                float sh,
                float opacity,
                boolean editorMode,
                boolean selected
        );
    }

    public interface InteractiveHudHandler {
        boolean onMouseClick(
                MinecraftAdapter mc,
                float localX,
                float localY,
                float width,
                float height,
                int button
        );
    }

    private final String id;
    private final String title;
    private final Module boundModule;
    private CustomHudPainter customPainter;
    private InteractiveHudHandler interactiveHandler;

    private float defaultNormX;
    private float defaultNormY;
    private float defaultWidth;
    private float defaultHeight;

    private float normX;
    private float normY;
    private float width;
    private float height;
    private float scale = 1.0f;
    private float opacity = 0.90f;
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
        this.defaultWidth = Math.max(48.0f, width);
        this.defaultHeight = Math.max(20.0f, height);
        resetLayout();
    }

    public HudElement withCustomPainter(CustomHudPainter painter) {
        this.customPainter = painter;
        return this;
    }

    public HudElement withInteractiveHandler(InteractiveHudHandler handler) {
        this.interactiveHandler = handler;
        return this;
    }

    public InteractiveHudHandler getInteractiveHandler() {
        return interactiveHandler;
    }

    public void resetLayout() {
        this.normX = defaultNormX;
        this.normY = defaultNormY;
        this.width = defaultWidth;
        this.height = defaultHeight;
        this.scale = 1.0f;
        this.opacity = 0.90f;
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

        float px = getPixelX(canvasW);
        float py = getPixelY(canvasH);
        float sw = getScaledWidth();
        float sh = getScaledHeight();

        if (customPainter != null) {
            boolean drew = customPainter.paint(r, mc, themeManager, fontManager, px, py, sw, sh, opacity, editorMode, selected);
            if (drew && editorMode && !locked) {
                int gripCol = selected ? themeManager.filterColor(themeManager.getActiveTheme().getAccentColor()) : 0xFF929BB1;
                r.fillRect(px + sw - 5.0f, py + sh - 5.0f, 4.0f, 4.0f, gripCol);
            }
            return;
        }

        String text = boundModule != null ? boundModule.getHudBadgeText(mc) : null;
        if ((text == null || text.isEmpty()) && editorMode) {
            text = title;
        }
        if (text == null || text.isEmpty()) {
            return;
        }

        AxsesTheme t = themeManager.getActiveTheme();
        float tw = fontManager.width(r, text, 0.94f);
        float th = fontManager.lineHeight(r, 0.94f);

        // Sculpted Dynamic-Island Frosted Glass Capsule with Left Gradient Icon Gem
        float gemW = 16.0f;
        float drawW = Math.max(sw, tw + gemW + 19.0f);
        float drawH = Math.max(sh, 22.0f);
        float drawX = px;
        if (drawX + drawW > canvasW - 4.0f) {
            drawX = Math.max(4.0f, canvasW - 4.0f - drawW);
        }
        float rad = Math.min(11.0f, drawH * 0.50f);

        int prim = themeManager.filterColor(t.getPrimaryColor());
        int acc = themeManager.filterColor(t.getAccentColor());

        if (showBackground) {
            RenderManager.drawFrostedGlassPanel(r, drawX, py, drawW, drawH, rad, prim, acc, opacity, selected ? 1.15f : 0.72f);
            // Left glowing gradient icon gem
            float gx = drawX + 3.5f;
            float gy = py + 3.2f;
            float gh = drawH - 6.4f;
            RenderManager.fillRoundedHorizontalGradient(r, gx, gy, gemW, gh, gh * 0.46f,
                    AxsesTheme.withAlpha(prim, 0.95f), AxsesTheme.withAlpha(acc, 0.95f));
            r.drawRoundedOutline(gx, gy, gemW, gh, gh * 0.46f, 0.9f, 0x55FFFFFF);
            drawWidgetVectorGlyph(r, id, gx + gemW * 0.5f, gy + gh * 0.5f);
        } else if (showOutline || editorMode) {
            RenderManager.drawHyperionContour(r, drawX, py, drawW, drawH, rad, 1.25f, prim, acc, 0.45f);
        }

        if (locked && editorMode) {
            int warn = themeManager.filterColor(t.getWarningColor());
            r.drawRoundedOutline(drawX, py, drawW, drawH, rad, 1.3f, warn);
        }

        float contentLeft = showBackground ? (drawX + gemW + 8.5f) : (drawX + 8.0f);
        float tx;
        switch (alignment) {
            case CENTER:
                tx = contentLeft + Math.max(0f, (drawW - (contentLeft - drawX) - 6f - tw) * 0.5f);
                break;
            case RIGHT:
                tx = drawX + drawW - tw - 7.5f;
                break;
            default:
                tx = contentLeft;
                break;
        }
        float ty = py + (drawH - th) * 0.5f + 0.8f;
        int txtCol = AxsesTheme.withAlpha(0xFFF8FAFF, Math.max(0.88f, opacity));

        int splitIdx = text.indexOf(": ");
        if (splitIdx < 0) {
            int sp = text.indexOf(' ');
            if (sp >= 2 && sp <= 7 && !text.startsWith("[") && !text.startsWith("«")) {
                splitIdx = sp - 1;
            }
        }
        if (splitIdx > 0 && splitIdx < text.length() - 2 && alignment == TextAlignment.LEFT) {
            String head = text.substring(0, splitIdx + 1);
            String tail = text.substring(splitIdx + 1);
            int headCol = RenderManager.blendArgb(0xFFFFFFFF, acc, 0.68f);
            fontManager.draw(r, head, tx, ty, AxsesTheme.withAlpha(headCol, Math.max(0.92f, opacity)), 0.94f);
            float hw = fontManager.width(r, head, 0.94f);
            fontManager.draw(r, tail, tx + hw, ty, txtCol, 0.94f);
        } else {
            fontManager.draw(r, text, tx, ty, txtCol, 0.94f);
        }

        if (editorMode && !locked) {
            int gripCol = selected ? themeManager.filterColor(t.getAccentColor()) : t.getMutedColor();
            r.fillRoundedRect(drawX + drawW - 6.0f, py + drawH - 6.0f, 4.0f, 4.0f, 2.0f, gripCol);
        }
    }

    private static void drawWidgetVectorGlyph(RenderAdapter r, String id, float cx, float cy) {
        int w = 0xFFFFFFFF;
        String k = id == null ? "" : id;
        if (k.contains("fps") || k.contains("sprint") || k.contains("speed")) {
            // Crisp lightning bolt vector
            r.drawLine(cx + 1.2f, cy - 4.0f, cx - 2.2f, cy + 0.4f, 1.4f, w);
            r.drawLine(cx - 2.2f, cy + 0.4f, cx + 1.2f, cy + 0.2f, 1.3f, w);
            r.drawLine(cx + 1.2f, cy + 0.2f, cx - 1.2f, cy + 4.0f, 1.4f, w);
        } else if (k.contains("coord") || k.contains("waypoint") || k.contains("compass") || k.contains("direction")) {
            // Diamond reticle vector
            r.drawLine(cx, cy - 3.8f, cx + 3.6f, cy, 1.3f, w);
            r.drawLine(cx + 3.6f, cy, cx, cy + 3.8f, 1.3f, w);
            r.drawLine(cx, cy + 3.8f, cx - 3.6f, cy, 1.3f, w);
            r.drawLine(cx - 3.6f, cy, cx, cy - 3.8f, 1.3f, w);
            r.fillRoundedRect(cx - 1.0f, cy - 1.0f, 2.0f, 2.0f, 1.0f, w);
        } else if (k.contains("bridge")) {
            // Isometric 3D step blocks vector
            r.fillRoundedRect(cx - 3.8f, cy - 0.5f, 3.8f, 3.6f, 1.0f, w);
            r.fillRoundedRect(cx + 0.2f, cy - 3.2f, 3.8f, 3.6f, 1.0f, 0xD8FFFFFF);
        } else if (k.contains("totem") || k.contains("armor") || k.contains("durability")) {
            // Shield crest vector
            r.drawLine(cx - 3.2f, cy - 3.4f, cx + 3.2f, cy - 3.4f, 1.3f, w);
            r.drawLine(cx + 3.2f, cy - 3.4f, cx + 2.6f, cy + 0.8f, 1.3f, w);
            r.drawLine(cx + 2.6f, cy + 0.8f, cx, cy + 3.8f, 1.3f, w);
            r.drawLine(cx, cy + 3.8f, cx - 2.6f, cy + 0.8f, 1.3f, w);
            r.drawLine(cx - 2.6f, cy + 0.8f, cx - 3.2f, cy - 3.4f, 1.3f, w);
        } else if (k.contains("gapple") || k.contains("bedwars") || k.contains("item")) {
            // Hexagonal crystal gem
            r.fillRoundedRect(cx - 3.0f, cy - 3.0f, 6.0f, 6.0f, 2.2f, w);
            r.fillRoundedRect(cx - 1.3f, cy - 1.3f, 2.6f, 2.6f, 1.3f, 0xFF0B132B);
        } else {
            // Cyber pulse bars
            r.fillRoundedRect(cx - 3.4f, cy - 1.2f, 1.6f, 3.8f, 0.8f, w);
            r.fillRoundedRect(cx - 0.8f, cy - 3.4f, 1.6f, 6.8f, 0.8f, w);
            r.fillRoundedRect(cx + 1.8f, cy - 2.0f, 1.6f, 4.6f, 0.8f, w);
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
    public void setWidth(float width) { if (!locked) this.width = Math.max(48.0f, Math.min(420.0f, width)); }
    public float getHeight() { return height; }
    public void setHeight(float height) { if (!locked) this.height = Math.max(18.0f, Math.min(220.0f, height)); }
    public float getScale() { return scale; }
    public void setScale(float scale) { this.scale = Math.max(0.6f, Math.min(2.2f, scale)); }
    public float getOpacity() { return opacity; }
    public void setOpacity(float opacity) { this.opacity = Math.max(0.25f, Math.min(1.0f, opacity)); }
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
        this.locked = false;
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
