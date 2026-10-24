package nexus.adapter.v1214;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.AABB;
import nexus.api.IEntity;
import nexus.api.IItemStack;
import nexus.api.IRenderer;
import nexus.api.math.Box3;
import nexus.api.math.ScreenPos;
import nexus.api.math.Vec3d;
import nexus.util.ColorUtil;

/**
 * {@link IRenderer} implementation for Minecraft 1.21.4.
 * <p>
 * The HUD pass draws with {@link GuiGraphics}, the world pass draws into the
 * pose stack of the current render pass. Both directions are supported by the
 * same object so modules can render wherever they are called from.
 */
public final class Nexus1214Renderer implements IRenderer {

    private final Minecraft minecraft = Minecraft.getInstance();
    private GuiGraphics gui;
    private PoseStack pose;
    private float partialTicks;
    private net.minecraft.world.phys.Vec3 camera = net.minecraft.world.phys.Vec3.ZERO;
    private VertexConsumer consumer;

    public Nexus1214Renderer() {
        this(null);
    }

    public Nexus1214Renderer(GuiGraphics gui) {
        this.gui = gui;
        this.pose = gui == null ? new PoseStack() : gui.pose();
    }

    public Nexus1214Renderer(PoseStack pose, float partialTicks) {
        this.pose = pose;
        this.partialTicks = partialTicks;
        if (minecraft.gameRenderer != null) {
            this.camera = minecraft.gameRenderer.getMainCamera().getPosition();
        }
    }

    void beginWorld(VertexConsumer consumer) {
        this.consumer = consumer;
    }

    @Override
    public void begin(float deltaMs) {
    }

    @Override
    public void end() {
    }

    private int argbToAbgr(int color) {
        int alpha = (color >>> 24) & 0xFF;
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        return (alpha << 24) | (blue << 16) | (green << 8) | red;
    }

    // ----------------------------------------------------------------- 2D API

    @Override
    public void fillRect(double x, double y, double width, double height, int color) {
        if (gui == null) {
            return;
        }
        gui.fill((int) x, (int) y, (int) (x + width), (int) (y + height), argbToAbgr(color));
    }

    @Override
    public void fillGradientRect(double x, double y, double width, double height, int startColor, int endColor, boolean vertical) {
        if (gui == null) {
            return;
        }
        gui.fillGradient((int) x, (int) y, (int) (x + width), (int) (y + height),
                argbToAbgr(startColor), argbToAbgr(endColor));
    }

    @Override
    public void fillGradientRound(double x, double y, double width, double height, double radius, int startColor, int endColor, boolean vertical) {
        fillGradientRect(x, y, width, height, startColor, endColor, vertical);
    }

    @Override
    public void fillRoundRect(double x, double y, double width, double height, double radius, int color) {
        fillRect(x, y, width, height, color);
    }

    @Override
    public void outlineRect(double x, double y, double width, double height, double thickness, int color) {
        fillRect(x, y, width, thickness, color);
        fillRect(x, y + height - thickness, width, thickness, color);
        fillRect(x, y, thickness, height, color);
        fillRect(x + width - thickness, y, thickness, height, color);
    }

    @Override
    public void outlineRoundRect(double x, double y, double width, double height, double radius, double thickness, int color) {
        outlineRect(x, y, width, height, thickness, color);
    }

    @Override
    public void line(double x1, double y1, double x2, double y2, double thickness, int color) {
        if (gui == null) {
            return;
        }
        gui.renderOutline((int) Math.min(x1, x2), (int) Math.min(y1, y2),
                (int) Math.abs(x2 - x1), (int) Math.abs(y2 - y1), argbToAbgr(color));
    }

    @Override
    public void triangle(double x1, double y1, double x2, double y2, double x3, double y3, int color) {
        line(x1, y1, x2, y2, 1.0, color);
        line(x2, y2, x3, y3, 1.0, color);
        line(x3, y3, x1, y1, 1.0, color);
    }

    @Override
    public void circle(double x, double y, double radius, int color) {
        for (double angle = 0.0; angle < Math.PI * 2.0; angle += 0.12) {
            for (double r = 0.0; r < radius; r += 1.0) {
                fillRect(x + Math.cos(angle) * r, y + Math.sin(angle) * r, 1.0, 1.0, color);
            }
        }
    }

    @Override
    public void circleOutline(double x, double y, double radius, double thickness, int color) {
        for (double angle = 0.0; angle < Math.PI * 2.0; angle += 0.08) {
            fillRect(x + Math.cos(angle) * radius, y + Math.sin(angle) * radius, thickness, thickness, color);
        }
    }

    @Override
    public void shadow(double x, double y, double width, double height, double radius, double blur, int color) {
        fillRect(x + blur, y + blur, width, height, ColorUtil.withAlpha(0xFF000000, (int) (blur * 20.0)));
    }

    @Override
    public void blurRegion(double x, double y, double width, double height, double strength) {
        fillRect(x, y, width, height, ColorUtil.withAlpha(0xFF08080D, (int) (strength * 60.0)));
    }

    @Override
    public void drawText(String text, double x, double y, int color, boolean shadow) {
        drawText(text, x, y, color, shadow, 1.0);
    }

    @Override
    public void drawText(String text, double x, double y, int color, boolean shadow, double scale) {
        if (gui == null) {
            return;
        }
        if (scale == 1.0) {
            gui.drawString(minecraft.font, text, (int) x, (int) y, argbToAbgr(color), shadow);
            return;
        }
        PoseStack stack = gui.pose();
        stack.pushPose();
        stack.scale((float) scale, (float) scale, 1.0f);
        gui.drawString(minecraft.font, text, (int) (x / scale), (int) (y / scale), argbToAbgr(color), shadow);
        stack.popPose();
    }

    @Override
    public void drawCenteredText(String text, double x, double y, int color, boolean shadow, double scale) {
        drawText(text, x - textWidth(text, scale) / 2.0, y, color, shadow, scale);
    }

    @Override
    public double textWidth(String text, double scale) {
        return minecraft.font.width(text) * scale;
    }

    @Override
    public double fontHeight(double scale) {
        return minecraft.font.lineHeight * scale;
    }

    @Override
    public void drawItem(IItemStack stack, double x, double y, double scale) {
        if (gui == null || stack.isEmpty()) {
            return;
        }
        gui.renderItem(((nexus.adapter.v1214.Nexus1214ItemStack) stack).handle(), (int) x, (int) y);
    }

    @Override
    public void drawItemWithCount(IItemStack stack, double x, double y, double scale) {
        drawItem(stack, x, y, scale);
    }

    @Override
    public void drawTooltip(String[] lines, double x, double y) {
        if (gui == null) {
            return;
        }
        double width = 0.0;
        for (String line : lines) {
            width = Math.max(width, textWidth(line, 1.0));
        }
        double height = lines.length * (fontHeight(1.0) + 2.0) + 4.0;
        double tooltipX = Math.min(x, minecraft.getWindow().getGuiScaledWidth() - width - 6.0);
        double tooltipY = Math.min(y, minecraft.getWindow().getGuiScaledHeight() - height - 6.0);
        fillRect(tooltipX, tooltipY, width + 6.0, height, 0xF208080D);
        outlineRect(tooltipX, tooltipY, width + 6.0, height, 1.0, 0x60C084FC);
        for (int i = 0; i < lines.length; i++) {
            drawText(lines[i], tooltipX + 3.0, tooltipY + 3.0 + i * (fontHeight(1.0) + 2.0),
                    0xFFF5F3FF, true);
        }
    }

    @Override
    public void pushScissor(double x, double y, double width, double height) {
        if (gui != null) {
            gui.enableScissor((int) x, (int) y, (int) (x + width), (int) (y + height));
        }
    }

    @Override
    public void popScissor() {
        if (gui != null) {
            gui.disableScissor();
        }
    }

    @Override
    public ScreenPos project(Vec3d worldPosition) {
        net.minecraft.world.phys.Vec3 relative = new net.minecraft.world.phys.Vec3(
                worldPosition.x - camera.x, worldPosition.y - camera.y, worldPosition.z - camera.z);
        net.minecraft.world.phys.Vec3 projected = relative;
        return new ScreenPos(minecraft.getWindow().getGuiScaledWidth() / 2.0 + projected.x * 20.0,
                minecraft.getWindow().getGuiScaledHeight() / 2.0 - projected.y * 20.0,
                relative.length(), true);
    }

    // ----------------------------------------------------------------- 3D API

    @Override
    public void drawBox(Box3 box, int fillColor, int lineColor, boolean filled) {
        AABB vanilla = new AABB(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)
                .move(-camera.x, -camera.y, -camera.z);
        if (consumer != null) {
            net.minecraft.client.renderer.LevelRenderer.renderLineBox(pose, consumer, vanilla,
                    ColorUtil.red(lineColor) / 255.0f, ColorUtil.green(lineColor) / 255.0f,
                    ColorUtil.blue(lineColor) / 255.0f, ColorUtil.alpha(lineColor) / 255.0f);
        }
    }

    @Override
    public void drawCornerBox(Box3 box, int color, double cornerLength, double thickness) {
        drawBox(box, 0, color, false);
    }

    @Override
    public void drawLine3D(Vec3d from, Vec3d to, int color, double thickness) {
        if (pose == null || consumer == null) {
            return;
        }
        PoseStack.Pose entry = pose.last();
        com.mojang.math.Axis axis = com.mojang.math.Axis.XP;
        VertexConsumer buffer = consumer;
        buffer.vertex(entry.pose(), (float) (from.x - camera.x), (float) (from.y - camera.y), (float) (from.z - camera.z))
                .setColor(ColorUtil.red(color), ColorUtil.green(color), ColorUtil.blue(color), ColorUtil.alpha(color))
                .setNormal((float) 0, (float) 1, (float) 0);
        buffer.vertex(entry.pose(), (float) (to.x - camera.x), (float) (to.y - camera.y), (float) (to.z - camera.z))
                .setColor(ColorUtil.red(color), ColorUtil.green(color), ColorUtil.blue(color), ColorUtil.alpha(color))
                .setNormal((float) 0, (float) 1, (float) 0);
    }

    @Override
    public void drawTracer(Vec3d worldPosition, int color, double thickness) {
        drawLine3D(new Vec3d(camera.x, camera.y, camera.z), worldPosition, color, thickness);
    }

    @Override
    public void drawNametag(Vec3d worldPosition, String text, int color, double scale, boolean shadow, int backgroundColor) {
        if (gui != null) {
            ScreenPos position = project(worldPosition);
            if (position.visible) {
                if (backgroundColor != 0) {
                    fillRect(position.x - textWidth(text, scale) / 2.0 - 2.0, position.y - 2.0,
                            textWidth(text, scale) + 4.0, fontHeight(scale) + 2.0, backgroundColor);
                }
                drawCenteredText(text, position.x, position.y, color, shadow, scale);
            }
        }
    }

    @Override
    public void drawEntityBox(IEntity entity, int fillColor, int lineColor, boolean filled) {
        drawBox(entity.boundingBox(), fillColor, lineColor, filled);
    }

    @Override
    public void drawSkeleton(IEntity entity, int color, double thickness) {
        drawBox(entity.boundingBox(), 0, color, false);
    }

    @Override
    public void drawGlow(IEntity entity, int color, double scale) {
        drawBox(entity.boundingBox().expand(scale - 1.0), 0, color, false);
    }

    @Override
    public void drawWorldQuad(Vec3d a, Vec3d b, Vec3d c, Vec3d d, int color) {
        drawLine3D(a, b, color, 1.0);
        drawLine3D(b, c, color, 1.0);
        drawLine3D(c, d, color, 1.0);
        drawLine3D(d, a, color, 1.0);
    }

    @Override
    public void drawWorldPoint(Vec3d position, double size, int color) {
        drawBox(new Box3(position.x - size, position.y - size, position.z - size,
                position.x + size, position.y + size, position.z + size), color, color, true);
    }
}
