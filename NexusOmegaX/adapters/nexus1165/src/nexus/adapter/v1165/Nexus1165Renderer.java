package nexus.adapter.v1165;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import nexus.api.IEntity;
import nexus.api.IItemStack;
import nexus.api.IRenderer;
import nexus.api.math.Box3;
import nexus.api.math.ScreenPos;
import nexus.util.ColorUtil;

/**
 * {@link IRenderer} implementation for Minecraft 1.16.5.
 * <p>
 * 1.16.5 draws 2D with {@code DrawableHelper} and 3D with the immediate mode
 * {@code Tessellator}, so both directions are implemented here.
 */
public final class Nexus1165Renderer implements IRenderer {

    private final MinecraftClient minecraft = MinecraftClient.getInstance();
    private final Object stack;
    private boolean worldPass;
    private int scissorX = -1;

    public Nexus1165Renderer() {
        this(new MatrixStack());
    }

    public Nexus1165Renderer(Object matrixStack) {
        this.stack = matrixStack;
    }

    public boolean isWorldPass() {
        return worldPass;
    }

    void setWorldPass(boolean value) {
        this.worldPass = value;
    }

    private MatrixStack pose() {
        return (MatrixStack) stack;
    }

    @Override
    public void begin(float deltaMs) {
    }

    @Override
    public void end() {
    }

    // ----------------------------------------------------------------- 2D API

    @Override
    public void fillRect(double x, double y, double width, double height, int color) {
        net.minecraft.client.gui.DrawableHelper.fill(pose(), (int) x, (int) y,
                (int) (x + width), (int) (y + height), color);
    }

    @Override
    public void fillGradientRect(double x, double y, double width, double height, int startColor, int endColor, boolean vertical) {
        int x0 = (int) x;
        int y0 = (int) y;
        int x1 = (int) (x + width);
        int y1 = (int) (y + height);
        if (y1 <= y0) {
            return;
        }
        for (int row = y0; row < y1; row++) {
            float t = (float) (row - y0) / (float) (y1 - y0);
            int color = blend(startColor, endColor, vertical ? t : 0.0f);
            net.minecraft.client.gui.DrawableHelper.fill(pose(), x0, row, x1, row + 1, color);
        }
    }

    private static int blend(int from, int to, float t) {
        int a = (int) ((from >>> 24 & 0xFF) + ((to >>> 24 & 0xFF) - (from >>> 24 & 0xFF)) * t);
        int r = (int) ((from >>> 16 & 0xFF) + ((to >>> 16 & 0xFF) - (from >>> 16 & 0xFF)) * t);
        int g = (int) ((from >>> 8 & 0xFF) + ((to >>> 8 & 0xFF) - (from >>> 8 & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
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
        double length = Math.max(1.0, Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)));
        fillRect(Math.min(x1, x2), Math.min(y1, y2),
                Math.abs(x2 - x1) + thickness, Math.abs(y2 - y1) + thickness, color);
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
        MatrixStack pose = pose();
        pose.push();
        pose.scale((float) scale, (float) scale, 1.0f);
        if (shadow) {
            minecraft.textRenderer.drawWithShadow(pose, text, (float) (x / scale), (float) (y / scale), color);
        } else {
            minecraft.textRenderer.draw(pose, text, (float) (x / scale), (float) (y / scale), color);
        }
        pose.pop();
    }

    @Override
    public void drawCenteredText(String text, double x, double y, int color, boolean shadow, double scale) {
        drawText(text, x - textWidth(text, scale) / 2.0, y, color, shadow, scale);
    }

    @Override
    public double textWidth(String text, double scale) {
        return minecraft.textRenderer.getWidth(text) * scale;
    }

    @Override
    public double fontHeight(double scale) {
        return minecraft.textRenderer.fontHeight * scale;
    }

    @Override
    public void drawItem(IItemStack stack, double x, double y, double scale) {
        if (stack.isEmpty()) {
            return;
        }
        minecraft.getItemRenderer().renderInGui(((Nexus1165ItemStack) stack).handle(), (int) x, (int) y);
    }

    @Override
    public void drawItemWithCount(IItemStack stack, double x, double y, double scale) {
        drawItem(stack, x, y, scale);
    }

    @Override
    public void drawTooltip(String[] lines, double x, double y) {
        double width = 0.0;
        for (String line : lines) {
            width = Math.max(width, textWidth(line, 1.0));
        }
        double height = lines.length * (fontHeight(1.0) + 2.0) + 4.0;
        fillRect(x, y, width + 6.0, height, 0xF208080D);
        outlineRect(x, y, width + 6.0, height, 1.0, 0x60C084FC);
        for (int i = 0; i < lines.length; i++) {
            drawText(lines[i], x + 3.0, y + 3.0 + i * (fontHeight(1.0) + 2.0), 0xFFF5F3FF, true);
        }
    }

    @Override
    public void pushScissor(double x, double y, double width, double height) {
        scissorX = (int) x;
        RenderSystem.enableScissor((int) x, (int) y, (int) width, (int) height);
    }

    @Override
    public void popScissor() {
        scissorX = -1;
        RenderSystem.disableScissor();
    }

    @Override
    public ScreenPos project(nexus.api.math.Vec3d worldPosition) {
        Vec3d camera = minecraft.gameRenderer.getCamera().getPos();
        return new ScreenPos(
                minecraft.getWindow().getScaledWidth() / 2.0 + (worldPosition.x - camera.x) * 20.0,
                minecraft.getWindow().getScaledHeight() / 2.0 - (worldPosition.y - camera.y) * 20.0,
                camera.distanceTo(new Vec3d(worldPosition.x, worldPosition.y, worldPosition.z)), true);
    }

    // ----------------------------------------------------------------- 3D API

    private void box(Box box, int red, int green, int blue, int alpha) {
        RenderSystem.disableTexture();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.lineWidth(2.0f);
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(3, VertexFormats.POSITION_COLOR);
        net.minecraft.client.render.WorldRenderer.drawBox(pose(), buffer, box,
                red / 255.0f, green / 255.0f, blue / 255.0f, alpha / 255.0f);
        Tessellator.getInstance().draw();
        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
    }

    @Override
    public void drawBox(Box3 box, int fillColor, int lineColor, boolean filled) {
        box(new Box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ),
                ColorUtil.red(lineColor), ColorUtil.green(lineColor), ColorUtil.blue(lineColor),
                ColorUtil.alpha(lineColor));
    }

    @Override
    public void drawCornerBox(Box3 box, int color, double cornerLength, double thickness) {
        drawBox(box, 0, color, false);
    }

    @Override
    public void drawLine3D(nexus.api.math.Vec3d from, nexus.api.math.Vec3d to, int color, double thickness) {
        Vec3d camera = minecraft.gameRenderer.getCamera().getPos();
        RenderSystem.disableTexture();
        RenderSystem.enableBlend();
        RenderSystem.lineWidth((float) thickness);
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(3, VertexFormats.POSITION_COLOR);
        buffer.vertex(from.x - camera.x, from.y - camera.y, from.z - camera.z)
                .color(ColorUtil.red(color), ColorUtil.green(color), ColorUtil.blue(color), ColorUtil.alpha(color))
                .next();
        buffer.vertex(to.x - camera.x, to.y - camera.y, to.z - camera.z)
                .color(ColorUtil.red(color), ColorUtil.green(color), ColorUtil.blue(color), ColorUtil.alpha(color))
                .next();
        Tessellator.getInstance().draw();
        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
    }

    @Override
    public void drawTracer(nexus.api.math.Vec3d worldPosition, int color, double thickness) {
        Vec3d camera = minecraft.gameRenderer.getCamera().getPos();
        drawLine3D(new nexus.api.math.Vec3d(camera.x, camera.y, camera.z), worldPosition, color, thickness);
    }

    @Override
    public void drawNametag(nexus.api.math.Vec3d worldPosition, String text, int color, double scale, boolean shadow, int backgroundColor) {
        ScreenPos position = project(worldPosition);
        if (position.visible) {
            if (backgroundColor != 0) {
                fillRect(position.x - textWidth(text, scale) / 2.0 - 2.0, position.y - 2.0,
                        textWidth(text, scale) + 4.0, fontHeight(scale) + 2.0, backgroundColor);
            }
            drawCenteredText(text, position.x, position.y, color, shadow, scale);
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
    public void drawWorldQuad(nexus.api.math.Vec3d a, nexus.api.math.Vec3d b, nexus.api.math.Vec3d c, nexus.api.math.Vec3d d, int color) {
        drawLine3D(a, b, color, 1.0);
        drawLine3D(b, c, color, 1.0);
        drawLine3D(c, d, color, 1.0);
        drawLine3D(d, a, color, 1.0);
    }

    @Override
    public void drawWorldPoint(nexus.api.math.Vec3d position, double size, int color) {
        drawBox(new Box3(position.x - size, position.y - size, position.z - size,
                position.x + size, position.y + size, position.z + size), color, color, true);
    }
}
