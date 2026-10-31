package dev.funtime.auctionhelper.mc1165;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.funtime.auctionhelper.ui.UiCanvas;
import dev.funtime.auctionhelper.util.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;

/** {@link UiCanvas} implementation for Minecraft 1.16.5 (PoseStack + GuiComponent). */
public final class Canvas1165 implements UiCanvas {
    private static final class GradientHelper extends GuiComponent {
        void drawVertical(PoseStack poseStack, int x1, int y1, int x2, int y2, int top, int bottom) {
            this.fillGradient(poseStack, x1, y1, x2, y2, top, bottom);
        }
    }

    private static final GradientHelper GRADIENT = new GradientHelper();

    private final PoseStack poseStack;

    public Canvas1165(PoseStack poseStack) {
        this.poseStack = poseStack;
    }

    @Override
    public int getScaledWidth() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.getWindow() != null ? mc.getWindow().getGuiScaledWidth() : 854;
    }

    @Override
    public int getScaledHeight() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.getWindow() != null ? mc.getWindow().getGuiScaledHeight() : 480;
    }

    @Override
    public void fillRect(float x, float y, float width, float height, int argb) {
        if (width <= 0f || height <= 0f || ((argb >>> 24) & 0xFF) == 0) {
            return;
        }
        GuiComponent.fill(poseStack, Math.round(x), Math.round(y),
                Math.max(Math.round(x) + 1, Math.round(x + width)),
                Math.max(Math.round(y) + 1, Math.round(y + height)), argb);
    }

    @Override
    public void fillGradientVertical(float x, float y, float width, float height, int topArgb, int bottomArgb) {
        float w = Math.max(1f, width);
        float h = Math.max(1f, height);
        int steps = Math.min(24, Math.max(1, Math.round(h)));
        for (int i = 0; i < steps; i++) {
            int color = Colors.blend(topArgb, bottomArgb, steps == 1 ? 0f : (float) i / (steps - 1));
            fillRect(x, y + h * i / steps, w, h / steps + 1f, color);
        }
    }

    @Override
    public void drawText(String text, float x, float y, int argb, boolean shadow, float scale) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null || text == null || text.isEmpty()) {
            return;
        }
        if (Math.abs(scale - 1f) < 0.01f) {
            if (shadow) {
                mc.font.drawShadow(poseStack, text, x, y, argb);
            } else {
                mc.font.draw(poseStack, text, x, y, argb);
            }
        } else {
            poseStack.pushPose();
            poseStack.translate(x, y, 0.0);
            poseStack.scale(scale, scale, 1f);
            if (shadow) {
                mc.font.drawShadow(poseStack, text, 0f, 0f, argb);
            } else {
                mc.font.draw(poseStack, text, 0f, 0f, argb);
            }
            poseStack.popPose();
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
        return 9f * scale;
    }

    @Override
    public void pushScissor(int x, int y, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return;
        }
        double scale = mc.getWindow().getGuiScale();
        int scaledX = Math.max(0, (int) Math.round(x * scale));
        int scaledY = Math.max(0, mc.getWindow().getHeight() - (int) Math.round((y + height) * scale));
        int scaledW = Math.max(1, (int) Math.round(width * scale));
        int scaledH = Math.max(1, (int) Math.round(height * scale));
        RenderSystem.enableScissor(scaledX, scaledY, scaledW, scaledH);
    }

    @Override
    public void popScissor() {
        RenderSystem.disableScissor();
    }

    @Override
    public void pushTransform(float translateX, float translateY, float scale) {
        poseStack.pushPose();
        poseStack.translate(translateX, translateY, 0.0);
        if (Math.abs(scale - 1f) > 0.001f) {
            poseStack.scale(scale, scale, 1f);
        }
    }

    @Override
    public void popTransform() {
        poseStack.popPose();
    }
}
