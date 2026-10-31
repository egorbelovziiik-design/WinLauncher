package dev.funtime.auctionhelper.mc1214;

import dev.funtime.auctionhelper.ui.UiCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** {@link UiCanvas} implementation for Minecraft 1.21.4 (GuiGraphics based). */
public final class Canvas1214 implements UiCanvas {
    private final GuiGraphics graphics;

    public Canvas1214(GuiGraphics graphics) {
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
        if (width <= 0f || height <= 0f || ((argb >>> 24) & 0xFF) == 0) {
            return;
        }
        int x1 = Math.round(x);
        int y1 = Math.round(y);
        int x2 = Math.max(x1 + 1, Math.round(x + width));
        int y2 = Math.max(y1 + 1, Math.round(y + height));
        graphics.fill(x1, y1, x2, y2, argb);
    }

    @Override
    public void fillGradientVertical(float x, float y, float width, float height, int topArgb, int bottomArgb) {
        float w = Math.max(1f, width);
        float h = Math.max(1f, height);
        int steps = Math.min(24, Math.max(1, Math.round(h)));
        for (int i = 0; i < steps; i++) {
            float t = steps == 1 ? 0f : (float) i / (steps - 1);
            int color = dev.funtime.auctionhelper.util.Colors.blend(topArgb, bottomArgb, t);
            graphics.fill(Math.round(x), Math.round(y + h * i / steps),
                    Math.round(x + w), Math.round(y + h * (i + 1) / steps) + 1, color);
        }
    }

    @Override
    public void drawText(String text, float x, float y, int argb, boolean shadow, float scale) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) {
            return;
        }
        if (Math.abs(scale - 1f) < 0.01f) {
            graphics.drawString(mc.font, text, Math.round(x), Math.round(y), argb, shadow);
        } else {
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0f);
            graphics.pose().scale(scale, scale, 1f);
            graphics.drawString(mc.font, text, 0, 0, argb, shadow);
            graphics.pose().popPose();
        }
    }

    @Override
    public float getTextWidth(String text, float scale) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null || text == null) {
            return 0f;
        }
        return mc.font.width(text) * scale;
    }

    @Override
    public float getFontHeight(float scale) {
        Minecraft mc = Minecraft.getInstance();
        int height = mc != null && mc.font != null ? mc.font.lineHeight : 9;
        return height * scale;
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
        graphics.pose().translate(translateX, translateY, 0f);
        graphics.pose().scale(scale, scale, 1f);
    }

    @Override
    public void popTransform() {
        graphics.pose().popPose();
    }
}
