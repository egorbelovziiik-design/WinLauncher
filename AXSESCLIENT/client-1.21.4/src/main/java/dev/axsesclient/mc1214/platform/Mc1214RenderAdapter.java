package dev.axsesclient.mc1214.platform;

import dev.axsesclient.common.platform.RenderAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class Mc1214RenderAdapter implements RenderAdapter {
    private final GuiGraphics graphics;

    public Mc1214RenderAdapter(GuiGraphics graphics) {
        this.graphics = graphics;
    }

    @Override
    public int getScaledWidth() {
        return graphics.guiWidth();
    }

    @Override
    public int getScaledHeight() {
        return graphics.guiHeight();
    }

    @Override
    public void fillRect(float x, float y, float width, float height, int argb) {
        if (width <= 0.0f || height <= 0.0f || ((argb >>> 24) & 0xFF) == 0) return;
        int x1 = Math.round(x);
        int y1 = Math.round(y);
        int x2 = Math.max(x1 + 1, Math.round(x + width));
        int y2 = Math.max(y1 + 1, Math.round(y + height));
        graphics.fill(x1, y1, x2, y2, argb);
    }

    @Override
    public void fillGradientVertical(float x, float y, float width, float height, int topArgb, int bottomArgb) {
        if (width <= 0.0f || height <= 0.0f) return;
        int x1 = Math.round(x);
        int y1 = Math.round(y);
        int x2 = Math.max(x1 + 1, Math.round(x + width));
        int y2 = Math.max(y1 + 1, Math.round(y + height));
        graphics.fillGradient(x1, y1, x2, y2, topArgb, bottomArgb);
    }

    @Override
    public void drawText(String text, float x, float y, int argb, boolean shadow, float scale) {
        if (text == null || text.isEmpty() || ((argb >>> 24) & 0xFF) == 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return;
        if (Math.abs(scale - 1.0f) < 0.01f) {
            graphics.drawString(mc.font, text, Math.round(x), Math.round(y), argb, shadow);
        } else {
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0.0f);
            graphics.pose().scale(scale, scale, 1.0f);
            graphics.drawString(mc.font, text, 0, 0, argb, shadow);
            graphics.pose().popPose();
        }
    }

    @Override
    public float getTextWidth(String text, float scale) {
        if (text == null || text.isEmpty()) return 0.0f;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return text.length() * 6.0f * scale;
        return mc.font.width(text) * scale;
    }

    @Override
    public float getFontHeight(float scale) {
        Minecraft mc = Minecraft.getInstance();
        int h = (mc != null && mc.font != null) ? mc.font.lineHeight : 9;
        return h * scale;
    }

    @Override
    public void pushScissor(int x, int y, int width, int height) {
        graphics.enableScissor(x, y, x + Math.max(0, width), y + Math.max(0, height));
    }

    @Override
    public void popScissor() {
        graphics.disableScissor();
    }

    @Override
    public void pushTransform(float translateX, float translateY, float scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(translateX, translateY, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
    }

    @Override
    public void popTransform() {
        graphics.pose().popPose();
    }
}
