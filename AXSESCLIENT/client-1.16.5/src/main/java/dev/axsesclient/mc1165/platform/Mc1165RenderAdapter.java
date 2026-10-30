package dev.axsesclient.mc1165.platform;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.axsesclient.common.platform.RenderAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;

public final class Mc1165RenderAdapter implements RenderAdapter {
    private static final class GradientHelper extends GuiComponent {
        void drawVertical(PoseStack ps, int x1, int y1, int x2, int y2, int topColor, int bottomColor) {
            this.fillGradient(ps, x1, y1, x2, y2, topColor, bottomColor);
        }
    }

    private static final GradientHelper GRADIENT = new GradientHelper();
    private PoseStack poseStack;

    public Mc1165RenderAdapter(PoseStack poseStack) {
        this.poseStack = poseStack;
    }

    public void setPoseStack(PoseStack poseStack) {
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
        if (poseStack == null || width <= 0f || height <= 0f) return;
        GuiComponent.fill(poseStack, Math.round(x), Math.round(y), Math.round(x + width), Math.round(y + height), argb);
    }

    @Override
    public void fillGradientVertical(float x, float y, float width, float height, int topArgb, int bottomArgb) {
        if (poseStack == null || width <= 0f || height <= 0f) return;
        GRADIENT.drawVertical(poseStack, Math.round(x), Math.round(y), Math.round(x + width), Math.round(y + height), topArgb, bottomArgb);
    }

    @Override
    public void drawText(String text, float x, float y, int argb, boolean shadow, float scale) {
        Minecraft mc = Minecraft.getInstance();
        if (poseStack == null || mc == null || mc.font == null || text == null || text.isEmpty()) return;
        if (Math.abs(scale - 1.0f) < 0.01f) {
            if (shadow) {
                mc.font.drawShadow(poseStack, text, x, y, argb);
            } else {
                mc.font.draw(poseStack, text, x, y, argb);
            }
        } else {
            poseStack.pushPose();
            poseStack.translate(x, y, 0.0);
            poseStack.scale(scale, scale, 1.0f);
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
        if (mc == null || mc.font == null || text == null) return 0f;
        return mc.font.width(text) * scale;
    }

    @Override
    public float getFontHeight(float scale) {
        return 9.0f * scale;
    }

    @Override
    public void pushScissor(int x, int y, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        double guiScale = mc.getWindow().getGuiScale();
        int fbHeight = mc.getWindow().getHeight();
        int sx = Math.max(0, (int) Math.round(x * guiScale));
        int sw = Math.max(1, (int) Math.round(width * guiScale));
        int sh = Math.max(1, (int) Math.round(height * guiScale));
        int sy = Math.max(0, fbHeight - (int) Math.round((y + height) * guiScale));
        RenderSystem.enableScissor(sx, sy, sw, sh);
    }

    @Override
    public void popScissor() {
        RenderSystem.disableScissor();
    }

    @Override
    public void pushTransform(float translateX, float translateY, float scale) {
        if (poseStack == null) return;
        poseStack.pushPose();
        poseStack.translate(translateX, translateY, 0.0);
        if (Math.abs(scale - 1.0f) > 0.001f) {
            poseStack.scale(scale, scale, 1.0f);
        }
    }

    @Override
    public void popTransform() {
        if (poseStack != null) {
            poseStack.popPose();
        }
    }
}
