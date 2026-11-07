package dev.axsesclient.common;

import dev.axsesclient.common.modules.Module;
import dev.axsesclient.common.modules.ModuleCategory;
import dev.axsesclient.common.platform.InputAdapter;
import dev.axsesclient.common.platform.ItemInfo;
import dev.axsesclient.common.platform.KeyCodes;
import dev.axsesclient.common.platform.MinecraftAdapter;
import dev.axsesclient.common.platform.RenderAdapter;
import dev.axsesclient.common.platform.StatusEffectInfo;
import dev.axsesclient.common.platform.TargetEntityInfo;
import dev.axsesclient.common.settings.ModeSetting;
import dev.axsesclient.common.settings.NumberSetting;
import dev.axsesclient.common.settings.Setting;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClientScreenshotGeneratorTest {

    static final class AwtScenePlatform implements MinecraftAdapter, InputAdapter, RenderAdapter {
        final Path gameDir;
        final int scaledW = 960;
        final int scaledH = 540;
        final int scaleFactor = 2;
        final BufferedImage image;
        final Graphics2D g;
        final Deque<Shape> clipStack = new LinkedList<Shape>();
        final Deque<AffineTransform> txStack = new ArrayDeque<AffineTransform>();
        final Font baseFont = new Font(Font.SANS_SERIF, Font.BOLD, 9);

        boolean inWorld = true;
        boolean clickGuiOpen = false;
        boolean hudEditorOpen = false;
        boolean chatScreenOpen = false;
        double scaledMouseX = -1.0;
        double scaledMouseY = -1.0;
        double playerX = 0.0, playerY = 64.0, playerZ = 0.0;
        float yaw = 0.0f;   // Looking towards +Z
        float pitch = 4.0f;
        double fov = 78.0;
        int selectedSlot = 0;
        boolean sneakingHeld = false;
        boolean standingOverEdge = false;
        double[] targetedBlockBox = null;
        String targetedBlockId = "";
        List<double[]> bedWarsBeds = Collections.emptyList();
        List<double[]> incomingProjectiles = Collections.emptyList();
        List<ItemInfo> customInventory = null;
        ItemInfo customMainHand = null;
        List<TargetEntityInfo> espEntities = Collections.emptyList();
        List<TargetEntityInfo> combatTargets = Collections.emptyList();
        int subPixelDepth = 0;
        boolean subPixelScaled = false;

        AwtScenePlatform(Path gameDir) {
            this.gameDir = gameDir;
            this.image = new BufferedImage(scaledW * scaleFactor, scaledH * scaleFactor, BufferedImage.TYPE_INT_ARGB);
            this.g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.scale(scaleFactor, scaleFactor);
            g.setFont(baseFont);
        }

        void dispose() {
            g.dispose();
        }

        private static Color toAwtColor(int argb) {
            return new Color(
                    (argb >> 16) & 0xFF,
                    (argb >> 8) & 0xFF,
                    argb & 0xFF,
                    (argb >>> 24) & 0xFF
            );
        }

        @Override public int getScaledWidth() { return scaledW; }
        @Override public int getScaledHeight() { return scaledH; }

        @Override
        public void fillRect(float x, float y, float width, float height, int argb) {
            if (width <= 0f || height <= 0f || ((argb >>> 24) & 0xFF) == 0) return;
            g.setColor(toAwtColor(argb));
            g.fillRect(Math.round(x), Math.round(y), Math.max(1, Math.round(width)), Math.max(1, Math.round(height)));
        }

        @Override
        public void fillGradientVertical(float x, float y, float width, float height, int topArgb, int bottomArgb) {
            if (width <= 0f || height <= 0f) return;
            GradientPaint gp = new GradientPaint(x, y, toAwtColor(topArgb), x, y + height, toAwtColor(bottomArgb));
            g.setPaint(gp);
            g.fillRect(Math.round(x), Math.round(y), Math.max(1, Math.round(width)), Math.max(1, Math.round(height)));
        }

        @Override
        public void drawText(String text, float x, float y, int argb, boolean shadow, float scale) {
            if (text == null || text.isEmpty()) return;
            Font f = Math.abs(scale - 1.0f) < 0.03f ? baseFont : baseFont.deriveFont(9.0f * scale);
            g.setFont(f);
            FontMetrics fm = g.getFontMetrics(f);
            float baselineY = y + fm.getAscent() - 1.0f;
            if (shadow) {
                int alpha = (argb >>> 24) & 0xFF;
                g.setColor(new Color(0, 0, 0, Math.min(200, alpha)));
                g.drawString(text, x + 0.8f, baselineY + 0.8f);
            }
            g.setColor(toAwtColor(argb));
            g.drawString(text, x, baselineY);
        }

        @Override
        public float getTextWidth(String text, float scale) {
            if (text == null || text.isEmpty()) return 0f;
            Font f = Math.abs(scale - 1.0f) < 0.03f ? baseFont : baseFont.deriveFont(9.0f * scale);
            return (float) g.getFontMetrics(f).getStringBounds(text, g).getWidth();
        }

        @Override
        public float getFontHeight(float scale) {
            return 8.5f * scale;
        }

        @Override
        public void pushScissor(int x, int y, int width, int height) {
            clipStack.push(g.getClip());
            g.clipRect(x, y, Math.max(1, width), Math.max(1, height));
        }

        @Override
        public void popScissor() {
            if (!clipStack.isEmpty()) {
                g.setClip(clipStack.pop());
            }
        }

        @Override
        public void pushTransform(float translateX, float translateY, float scale) {
            txStack.push(g.getTransform());
            g.translate(translateX, translateY);
            g.scale(scale, scale);
        }

        @Override
        public void popTransform() {
            if (!txStack.isEmpty()) {
                g.setTransform(txStack.pop());
            }
        }

        @Override
        public float beginHiDpiSubPixel() {
            if (subPixelDepth > 0) {
                subPixelDepth++;
                return 1.0f;
            }
            subPixelDepth = 1;
            if (scaleFactor > 1) {
                txStack.push(g.getTransform());
                g.scale(1.0 / scaleFactor, 1.0 / scaleFactor);
                subPixelScaled = true;
                return (float) scaleFactor;
            }
            subPixelScaled = false;
            return 1.0f;
        }

        @Override
        public void endHiDpiSubPixel() {
            if (subPixelDepth <= 0) return;
            subPixelDepth--;
            if (subPixelDepth == 0 && subPixelScaled) {
                subPixelScaled = false;
                if (!txStack.isEmpty()) {
                    g.setTransform(txStack.pop());
                }
            }
        }

        @Override
        public float[] projectWorldToScreen(double worldX, double worldY, double worldZ) {
            double cpX = playerX;
            double cpY = playerY + 1.62;
            double cpZ = playerZ;
            double dx = worldX - cpX;
            double dy = worldY - cpY;
            double dz = worldZ - cpZ;

            double yawRad = Math.toRadians(yaw);
            double pitchRad = Math.toRadians(pitch);
            double sinY = Math.sin(yawRad), cosY = Math.cos(yawRad);
            double sinP = Math.sin(pitchRad), cosP = Math.cos(pitchRad);

            double fx = -sinY * cosP, fy = -sinP, fz = cosY * cosP;
            double rx = -cosY, ry = 0.0, rz = -sinY;
            double ux = -sinY * sinP, uy = cosP, uz = cosY * sinP;

            double zCam = dx * fx + dy * fy + dz * fz;
            if (zCam <= 0.05) {
                return new float[] { 0f, 0f, (float) zCam };
            }
            double xCam = dx * rx + dy * ry + dz * rz;
            double yCam = dx * ux + dy * uy + dz * uz;

            double aspect = (double) scaledW / (double) scaledH;
            double tanHalf = Math.tan(Math.toRadians(fov * 0.5));
            double ndcX = xCam / (zCam * tanHalf * aspect);
            double ndcY = yCam / (zCam * tanHalf);
            float sx = (float) ((0.5 + 0.5 * ndcX) * scaledW);
            float sy = (float) ((0.5 - 0.5 * ndcY) * scaledH);
            return new float[] { sx, sy, (float) zCam };
        }

        @Override
        public void fillTriangle(float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
            if (((argb >>> 24) & 0xFF) == 0) return;
            java.awt.geom.Path2D.Float poly = new java.awt.geom.Path2D.Float();
            poly.moveTo(x1, y1);
            poly.lineTo(x2, y2);
            poly.lineTo(x3, y3);
            poly.closePath();
            g.setColor(toAwtColor(argb));
            g.fill(poly);
        }

        @Override
        public void fillQuad(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int argb) {
            if (((argb >>> 24) & 0xFF) == 0) return;
            java.awt.geom.Path2D.Float poly = new java.awt.geom.Path2D.Float();
            poly.moveTo(x1, y1);
            poly.lineTo(x2, y2);
            poly.lineTo(x3, y3);
            poly.lineTo(x4, y4);
            poly.closePath();
            g.setColor(toAwtColor(argb));
            g.fill(poly);
        }

        static final class ShadedFace3D {
            final double[] xs;
            final double[] ys;
            final double[] zs;
            final int fillArgb;
            final int edgeArgb;
            final double camDepth;

            ShadedFace3D(double[] xs, double[] ys, double[] zs, int fillArgb, int edgeArgb, double camDepth) {
                this.xs = xs;
                this.ys = ys;
                this.zs = zs;
                this.fillArgb = fillArgb;
                this.edgeArgb = edgeArgb;
                this.camDepth = camDepth;
            }
        }

        void addShadedBox(java.util.List<ShadedFace3D> out,
                          double bx, double by, double bz,
                          double sx, double sy, double sz,
                          int topCol, int sideCol, int frontCol, int edgeCol) {
            double x0 = bx, y0 = by, z0 = bz;
            double x1 = bx + sx, y1 = by + sy, z1 = bz + sz;
            double camX = playerX;
            double camY = playerY + 1.62;
            double camZ = playerZ;

            // Camera-facing backface culling so only outward faces visible from camera eye are emitted
            if (camY > y1) {
                emitFace(out, new double[]{x0, x1, x1, x0}, new double[]{y1, y1, y1, y1}, new double[]{z0, z0, z1, z1}, topCol, edgeCol);
            } else if (camY < y0) {
                emitFace(out, new double[]{x0, x1, x1, x0}, new double[]{y0, y0, y0, y0}, new double[]{z0, z0, z1, z1}, sideCol, edgeCol);
            }
            if (camZ < z0) {
                emitFace(out, new double[]{x0, x1, x1, x0}, new double[]{y0, y0, y1, y1}, new double[]{z0, z0, z0, z0}, frontCol, edgeCol);
            } else if (camZ > z1) {
                emitFace(out, new double[]{x0, x1, x1, x0}, new double[]{y0, y0, y1, y1}, new double[]{z1, z1, z1, z1}, frontCol, edgeCol);
            }
            if (camX < x0) {
                emitFace(out, new double[]{x0, x0, x0, x0}, new double[]{y0, y0, y1, y1}, new double[]{z0, z1, z1, z0}, sideCol, edgeCol);
            } else if (camX > x1) {
                emitFace(out, new double[]{x1, x1, x1, x1}, new double[]{y0, y0, y1, y1}, new double[]{z0, z1, z1, z0}, sideCol, edgeCol);
            }
        }

        private void emitFace(java.util.List<ShadedFace3D> out, double[] xs, double[] ys, double[] zs, int fillCol, int edgeCol) {
            double cx = (xs[0] + xs[1] + xs[2] + xs[3]) * 0.25 - playerX;
            double cy = (ys[0] + ys[1] + ys[2] + ys[3]) * 0.25 - (playerY + 1.62);
            double cz = (zs[0] + zs[1] + zs[2] + zs[3]) * 0.25 - playerZ;
            double distSq = cx * cx + cy * cy + cz * cz;
            out.add(new ShadedFace3D(xs, ys, zs, fillCol, edgeCol, distSq));
        }

        void flushShadedFaces(java.util.List<ShadedFace3D> faces) {
            Collections.sort(faces, new java.util.Comparator<ShadedFace3D>() {
                @Override
                public int compare(ShadedFace3D a, ShadedFace3D b) {
                    return Double.compare(b.camDepth, a.camDepth); // Painter's algorithm: far -> near
                }
            });
            for (int i = 0; i < faces.size(); i++) {
                ShadedFace3D f = faces.get(i);
                float[] p0 = projectWorldToScreen(f.xs[0], f.ys[0], f.zs[0]);
                float[] p1 = projectWorldToScreen(f.xs[1], f.ys[1], f.zs[1]);
                float[] p2 = projectWorldToScreen(f.xs[2], f.ys[2], f.zs[2]);
                float[] p3 = projectWorldToScreen(f.xs[3], f.ys[3], f.zs[3]);
                if (p0 == null || p1 == null || p2 == null || p3 == null) continue;
                if (p0[2] <= 0.14f || p1[2] <= 0.14f || p2[2] <= 0.14f || p3[2] <= 0.14f) continue;
                fillQuad(p0[0], p0[1], p1[0], p1[1], p2[0], p2[1], p3[0], p3[1], f.fillArgb);
                if (((f.edgeArgb >>> 24) & 0xFF) > 0) {
                    drawLine(p0[0], p0[1], p1[0], p1[1], 0.85f, f.edgeArgb);
                    drawLine(p1[0], p1[1], p2[0], p2[1], 0.85f, f.edgeArgb);
                    drawLine(p2[0], p2[1], p3[0], p3[1], 0.85f, f.edgeArgb);
                    drawLine(p3[0], p3[1], p0[0], p0[1], 0.85f, f.edgeArgb);
                }
            }
        }

        void paintCelestialShaderSkybox() {
            // Multi-stop Celestial Shader Night Sky + Aurora Nebula + Glowing Crescent Moon
            fillGradientVertical(0, 0, scaledW, scaledH * 0.56f, 0xFF040613, 0xFF131B3E);
            fillGradientVertical(0, scaledH * 0.28f, scaledW, scaledH * 0.28f, 0x00000000, 0x426D28D9);
            fillGradientVertical(0, scaledH * 0.56f, scaledW, scaledH * 0.44f, 0xFF0B132B, 0xFF040712);

            // Flowing Aurora Borealis ribbons across upper sky
            for (int b = 0; b < 96; b++) {
                float x = b * (scaledW / 96.0f);
                float w = (scaledW / 96.0f) + 1.0f;
                float yWave = 52.0f + (float) Math.sin(b * 0.14 + 0.8) * 20.0f + (float) Math.cos(b * 0.07) * 11.0f;
                int cTop = (b % 2 == 0) ? 0x2400F5D4 : 0x2238BDF8;
                fillGradientVertical(x, yWave, w, 58.0f, cTop, 0x00000000);
                fillGradientVertical(x, yWave + 22.0f, w, 46.0f, 0x1CA855F7, 0x00000000);
            }

            // Starfield with diamond twinkles
            for (int i = 0; i < 64; i++) {
                float sx = ((i * 73 + 19) % 948) + 6f;
                float sy = ((i * 41 + 11) % 218) + 6f;
                float sz = (i % 5 == 0) ? 2.2f : 1.3f;
                fillRoundedRect(sx, sy, sz, sz, sz * 0.5f, (i % 3 == 0) ? 0x9900F5D4 : 0x88F8FAFC);
            }

            // Volumetric Glowing Moon with Multi-Ring Atmospheric Halo (upper-right sky)
            float mx = scaledW * 0.76f;
            float my = 76.0f;
            fillRoundedRect(mx - 44f, my - 44f, 88f, 88f, 44f, 0x1038BDF8);
            fillRoundedRect(mx - 28f, my - 28f, 56f, 56f, 28f, 0x227DD3FC);
            fillRoundedRect(mx - 16f, my - 16f, 32f, 32f, 16f, 0xFFE0F2FE);
            fillRoundedRect(mx - 10f, my - 19f, 26f, 26f, 13f, 0xFF0E1533); // Crescent shadow carve
        }

        void add3dArmoredPlayer(java.util.List<ShadedFace3D> faces, TargetEntityInfo e, boolean crimsonTeam) {
            double cx = e.getX();
            double y0 = e.getMinY();
            double cz = e.getZ();
            boolean hurt = e.getHurtTime() > 0;
            int helmTop = hurt ? 0xFFFF4D6D : (crimsonTeam ? 0xFFF43F5E : 0xFF38BDF8);
            int helmSide = hurt ? 0xFFC9184A : (crimsonTeam ? 0xFFBE123C : 0xFF0284C7);
            int armorTop = hurt ? 0xFFE11D48 : (crimsonTeam ? 0xFF9F1239 : 0xFF334155);
            int armorSide = hurt ? 0xFF9F1239 : (crimsonTeam ? 0xFF881337 : 0xFF1E293B);
            int armorFront = hurt ? 0xFFBE123C : (crimsonTeam ? 0xFFBE123C : 0xFF27364F);
            int trimEdge = hurt ? 0xAAFF8FA3 : (crimsonTeam ? 0x88FDA4AF : 0x8838BDF8);

            // 3D Boots + Legs
            addShadedBox(faces, cx - 0.24, y0, cz - 0.13, 0.21, 0.72, 0.26, armorTop, 0xFF0F172A, 0xFF1E293B, trimEdge);
            addShadedBox(faces, cx + 0.03, y0, cz - 0.13, 0.21, 0.72, 0.26, armorTop, 0xFF0F172A, 0xFF1E293B, trimEdge);
            // 3D Netherite/Diamond Chestplate Torso
            addShadedBox(faces, cx - 0.26, y0 + 0.72, cz - 0.15, 0.52, 0.66, 0.30, armorTop, armorSide, armorFront, trimEdge);
            // 3D Armored Pauldrons & Arms
            addShadedBox(faces, cx - 0.46, y0 + 0.70, cz - 0.13, 0.19, 0.66, 0.26, helmTop, armorSide, armorFront, trimEdge);
            addShadedBox(faces, cx + 0.27, y0 + 0.70, cz - 0.13, 0.19, 0.66, 0.26, helmTop, armorSide, armorFront, trimEdge);
            // 3D Helmeted Head + Glowing Cyber Visor
            addShadedBox(faces, cx - 0.22, y0 + 1.38, cz - 0.22, 0.44, 0.44, 0.44, helmTop, helmSide, helmSide, 0xAAFFFFFF);
            addShadedBox(faces, cx - 0.16, y0 + 1.52, cz - 0.24, 0.32, 0.10, 0.04, 0xFF00F5D4, 0xFF00F5D4, 0xFFFFFFFF, 0x00000000);
            // 3D Held Enchanted Sword extending forward toward player (-Z)
            addShadedBox(faces, cx - 0.40, y0 + 0.84, cz - 0.68, 0.07, 0.10, 0.58, 0xFF67E8F9, 0xFF06B6D4, 0xFFA855F7, 0xCCFFFFFF);
        }

        void paintFirstPersonViewModel(boolean holdingBlock) {
            // Left foreground: 3D Offhand Golden Totem of Undying with subtle golden aura
            fillRoundedRect(108f, scaledH - 108f, 76f, 96f, 38f, 0x16FACC15);
            // Totem wings & sculpted golden body
            fillQuad(114f, scaledH - 64f, 178f, scaledH - 72f, 174f, scaledH - 52f, 118f, scaledH - 46f, 0xFFD97706);
            fillQuad(132f, scaledH - 92f, 162f, scaledH - 95f, 168f, scaledH - 18f, 126f, scaledH - 14f, 0xFFFACC15);
            fillQuad(136f, scaledH - 88f, 158f, scaledH - 90f, 161f, scaledH - 58f, 134f, scaledH - 55f, 0xFFFDE047);
            // Glowing Emerald Totem Eyes
            fillRoundedRect(139f, scaledH - 77f, 6.5f, 5.5f, 1.5f, 0xFF10B981);
            fillRoundedRect(150f, scaledH - 78f, 6.5f, 5.5f, 1.5f, 0xFF10B981);
            drawLine(132f, scaledH - 92f, 162f, scaledH - 95f, 1.2f, 0xAAFFFFFF);

            if (holdingBlock) {
                // Right foreground: 3D Isometric Shaded Cyan BedWars Wool Block in hand
                float bx = scaledW - 168f;
                float by = scaledH - 86f;
                fillRoundedRect(bx - 16f, by - 14f, 116f, 96f, 36f, 0x1800F5D4);
                fillQuad(bx + 42f, by - 8f, bx + 92f, by + 14f, bx + 44f, by + 38f, bx - 4f, by + 16f, 0xFF38BDF8); // Top
                fillQuad(bx - 4f, by + 16f, bx + 44f, by + 38f, bx + 40f, by + 92f, bx - 6f, by + 68f, 0xFF0284C7); // Left
                fillQuad(bx + 44f, by + 38f, bx + 92f, by + 14f, bx + 86f, by + 68f, bx + 40f, by + 92f, 0xFF0369A1); // Right
                drawNeonLine(bx - 4f, by + 16f, bx + 42f, by - 8f, 1.4f, 0xCCFFFFFF, 0x6600F5D4);
                drawNeonLine(bx + 42f, by - 8f, bx + 92f, by + 14f, 1.4f, 0xCCFFFFFF, 0x6600F5D4);
            } else {
                // Right foreground: Custom Celestial Smooth 3D Enchanted Netherite-Diamond Blade
                float sx = scaledW - 192f;
                float sy = scaledH - 22f;
                // Enchantment aura glow around blade
                drawNeonLine(sx + 56f, sy - 26f, sx - 28f, sy - 134f, 9.5f, 0x24A855F7, 0x1800F5D4);
                // 3D Dual-bevel Enchanted Blade (left lit bevel + right violet-glint bevel)
                fillQuad(sx + 48f, sy - 34f, sx - 26f, sy - 132f, sx - 32f, sy - 138f, sx + 38f, sy - 42f, 0xFF67E8F9);
                fillQuad(sx + 58f, sy - 26f, sx - 18f, sy - 124f, sx - 32f, sy - 138f, sx + 48f, sy - 34f, 0xFF312E81);
                // Glowing Enchantment Glint Core Vein
                drawNeonLine(sx + 48f, sy - 34f, sx - 29f, sy - 135f, 1.9f, 0xFFFFFFFF, 0xAA00F5D4);
                // Sculpted Crossguard + Pommel
                fillQuad(sx + 24f, sy - 24f, sx + 74f, sy - 52f, sx + 80f, sy - 42f, sx + 30f, sy - 14f, 0xFFFBBF24);
                drawNeonLine(sx + 26f, sy - 20f, sx + 76f, sy - 48f, 1.4f, 0xFFFFFFFF, 0x88FACC15);
                fillQuad(sx + 52f, sy - 28f, sx + 64f, sy - 35f, sx + 88f, sy - 2f, sx + 74f, sy + 6f, 0xFF451A03);
            }
        }

        void paintStylizedWorldBackdrop(boolean drawEntities) {
            paintCelestialShaderSkybox();

            java.util.List<ShadedFace3D> faces = new java.util.ArrayList<ShadedFace3D>(512);

            // 3D Shaded Cyber-Quartz & Dark-Prismarine PvP Colosseum Tiles (x = -7..6, z = 1..19)
            for (int gz = 1; gz <= 18; gz += 2) {
                for (int gx = -8; gx <= 6; gx += 2) {
                    boolean checker = (((gx + 8) / 2 + gz / 2) % 2 == 0);
                    boolean centerLane = Math.abs(gx + 1) <= 1;
                    int topC = centerLane ? (checker ? 0xFF1E293B : 0xFF172554) : (checker ? 0xFF0F172A : 0xFF131C31);
                    int sideC = 0xFF090D1A;
                    int frontC = 0xFF0B1120;
                    int seamC = centerLane ? 0x3800F5D4 : 0x226366F1;
                    addShadedBox(faces, gx, 63.0, gz, 2.0, 1.0, 2.0, topC, sideC, frontC, seamC);
                }
            }

            // Ornate 3D Colosseum Pillars + Glowing Sea-Lantern Crystal Crowns along left & right flanks
            double[] pillarXs = { -6.6, 5.4 };
            double[] pillarZs = { 6.5, 12.5, 18.5 };
            for (int px = 0; px < pillarXs.length; px++) {
                for (int pz = 0; pz < pillarZs.length; pz++) {
                    double bx = pillarXs[px];
                    double bz = pillarZs[pz];
                    addShadedBox(faces, bx - 0.2, 64.0, bz - 0.2, 1.6, 0.5, 1.6, 0xFF334155, 0xFF1E293B, 0xFF27364F, 0x4494A3B8);
                    addShadedBox(faces, bx, 64.5, bz, 1.2, 3.6, 1.2, 0xFF1E293B, 0xFF0F172A, 0xFF172036, 0x3838BDF8);
                    addShadedBox(faces, bx + 0.1, 68.1, bz + 0.1, 1.0, 0.9, 1.0, 0xFF67E8F9, 0xFF06B6D4, 0xFF22D3EE, 0xCCFFFFFF);
                }
            }
            // Distant Arena Sanctum Arch at z = 19.5
            addShadedBox(faces, -3.5, 64.0, 19.2, 7.0, 0.8, 1.6, 0xFF1E293B, 0xFF0F172A, 0xFF1E293B, 0x44A855F7);

            if (drawEntities) {
                for (TargetEntityInfo e : espEntities) {
                    if (!e.isItemEntity()) {
                        add3dArmoredPlayer(faces, e, e.getEntityId() % 2 == 0);
                    }
                }
            }

            flushShadedFaces(faces);

            if (drawEntities) {
                for (TargetEntityInfo e : espEntities) {
                    if (e.isItemEntity()) {
                        float[] c = projectWorldToScreen(e.getX(), e.getY() + 0.18, e.getZ());
                        if (c != null && c[2] > 0.2f) {
                            fillRoundedRect(c[0] - 6.5f, c[1] - 6.5f, 13f, 13f, 6.5f, 0x44FFD166);
                            fillRoundedRect(c[0] - 4.2f, c[1] - 4.2f, 8.4f, 8.4f, 4.2f, 0xFFFACC15);
                            drawRoundedOutline(c[0] - 4.2f, c[1] - 4.2f, 8.4f, 8.4f, 4.2f, 1.0f, 0xFFFFFFFF);
                        }
                    }
                }
                paintFirstPersonViewModel(false);
            }
        }

        void paintBedWarsSkyScene() {
            paintCelestialShaderSkybox();

            java.util.List<ShadedFace3D> faces = new java.util.ArrayList<ShadedFace3D>(512);

            // Distant Floating Diamond Generator Island on left (+X projects left at yaw=0)
            addShadedBox(faces, 6.8, 61.8, 15.5, 4.4, 2.2, 4.4, 0xFF1E293B, 0xFF0F172A, 0xFF162033, 0x4438BDF8);
            addShadedBox(faces, 8.4, 64.6, 17.1, 1.1, 1.1, 1.1, 0xFF67E8F9, 0xFF0284C7, 0xFF0EA5E9, 0xEEFFFFFF);

            // Distant Floating Emerald Generator Island on right (-X projects right at yaw=0)
            addShadedBox(faces, -11.2, 62.0, 16.5, 4.0, 2.0, 4.0, 0xFF1E293B, 0xFF0F172A, 0xFF162033, 0x4410B981);
            addShadedBox(faces, -9.7, 64.5, 18.0, 1.0, 1.0, 1.0, 0xFF34D399, 0xFF059669, 0xFF10B981, 0xEEFFFFFF);

            // Enemy Red Team Floating Fortress Sky-Island ahead (z = 12..19.5, x = -5..5)
            addShadedBox(faces, -3.6, 60.6, 13.2, 7.2, 1.9, 5.6, 0xFF1E293B, 0xFF0B1120, 0xFF0F172A, 0x3394A3B8);
            addShadedBox(faces, -4.8, 62.5, 12.0, 9.6, 1.5, 7.2, 0xFF334155, 0xFF1E293B, 0xFF243247, 0x5564748B);
            // Castle Battlement Towers flanking the Red Bed
            addShadedBox(faces, -4.5, 64.0, 16.8, 1.8, 3.6, 1.8, 0xFF475569, 0xFF1E293B, 0xFF334155, 0x5594A3B8);
            addShadedBox(faces, 2.7, 64.0, 16.8, 1.8, 3.6, 1.8, 0xFF475569, 0xFF1E293B, 0xFF334155, 0x5594A3B8);
            addShadedBox(faces, -4.3, 67.6, 17.0, 1.4, 0.8, 1.4, 0xFFEF4444, 0xFFB91C1C, 0xFFDC2626, 0xAAFDA4AF);
            addShadedBox(faces, 2.9, 67.6, 17.0, 1.4, 0.8, 1.4, 0xFFEF4444, 0xFFB91C1C, 0xFFDC2626, 0xAAFDA4AF);

            // Red Team Bed + End Stone Defense Mound
            addShadedBox(faces, -0.5, 64.0, 15.0, 2.0, 0.65, 1.0, 0xFFEF4444, 0xFFB91C1C, 0xFFDC2626, 0xFFFF8FA3);
            addShadedBox(faces, -1.2, 64.0, 16.2, 3.4, 1.3, 1.6, 0xFFE2E8B8, 0xFFB8C090, 0xFFD0D8A6, 0x66FEF08A);

            // Incoming 3D Magma Fireball in sky
            addShadedBox(faces, 4.65, 68.15, 11.15, 0.55, 0.55, 0.55, 0xFFFDE047, 0xFFEA580C, 0xFFF97316, 0xFFFFFFFF);

            // FastBridge Cyan Wool Blocks placed from z = 1..6 (depth-sorted cleanly!)
            for (int bz = 1; bz <= 6; bz++) {
                boolean alt = (bz % 2 == 0);
                int topC = alt ? 0xFF38BDF8 : 0xFF22D3EE;
                int sideC = alt ? 0xFF0284C7 : 0xFF0891B2;
                int frontC = alt ? 0xFF0369A1 : 0xFF0E7490;
                addShadedBox(faces, -0.5, 63.0, bz, 1.0, 1.0, 1.0, topC, sideC, frontC, 0x887DD3FC);
            }

            // 3D Armored BedWars Rushers
            for (TargetEntityInfo e : espEntities) {
                if (e.isItemEntity()) continue;
                if (e.getHeldItemName() != null && e.getHeldItemName().contains("[INVIS]")) {
                    // Invisible flanker: floating 3D TNT block in hand!
                    addShadedBox(faces, e.getX() - 0.18, e.getMinY() + 0.90, e.getZ() - 0.35, 0.36, 0.36, 0.36,
                            0xFFEF4444, 0xFFB91C1C, 0xFFF8FAFC, 0xCCFFFFFF);
                    continue;
                }
                add3dArmoredPlayer(faces, e, true);
            }

            flushShadedFaces(faces);

            // Generator resource drops + First-person Wool & Totem hands (when not in ClickGUI)
            for (TargetEntityInfo e : espEntities) {
                if (e.isItemEntity()) {
                    float[] c = projectWorldToScreen(e.getX(), e.getY() + 0.18, e.getZ());
                    if (c != null && c[2] > 0.2f) {
                        int itemCol = e.getName().contains("Изумруд") ? 0xFF10B981 : 0xFFFACC15;
                        fillRoundedRect(c[0] - 6.5f, c[1] - 6.5f, 13f, 13f, 6.5f, (itemCol & 0x00FFFFFF) | 0x44000000);
                        fillRoundedRect(c[0] - 4.5f, c[1] - 4.5f, 9f, 9f, 3.5f, itemCol);
                        drawRoundedOutline(c[0] - 4.5f, c[1] - 4.5f, 9f, 9f, 3.5f, 1.0f, 0xFFFFFFFF);
                    }
                }
            }
            if (!clickGuiOpen) {
                paintFirstPersonViewModel(true);
            }
        }

        boolean onGround = true;

        @Override public String getMinecraftVersion() { return "1.21.11"; }
        @Override public String getJavaVersion() { return "21.0.13"; }
        @Override public String getFabricLoaderVersion() { return "0.20.1"; }
        @Override public String getFabricApiVersion() { return "0.141.6+1.21.11"; }
        @Override public List<String> getLoadedMods() {
            return Arrays.asList("axsesclient@6.0.0", "sodium@0.6.5", "lithium@0.14.7", "ferritecore@7.1.1", "baritone@1.11.1");
        }
        @Override public Path getGameDir() { return gameDir; }
        @Override public boolean isInWorld() { return inWorld; }
        @Override public boolean isSingleplayer() { return false; }
        @Override public String getWorldOrServerName() { return "mc.funtime.su"; }
        @Override public String getDimensionId() { return "minecraft:overworld"; }
        @Override public String getPlayerName() { return "AxsesPlayer"; }
        @Override public double getPlayerX() { return playerX; }
        @Override public double getPlayerY() { return playerY; }
        @Override public double getPlayerZ() { return playerZ; }
        @Override public double getPrevPlayerX() { return playerX; }
        @Override public double getPrevPlayerY() { return playerY; }
        @Override public double getPrevPlayerZ() { return playerZ - 0.28; }
        @Override public float getPlayerYaw() { return yaw; }
        @Override public float getPlayerPitch() { return pitch; }
        @Override public void setPlayerRotation(float y, float p) { this.yaw = y; this.pitch = p; }
        @Override public boolean isOnGround() { return onGround; }
        @Override public boolean isSprinting() { return true; }
        @Override public void setSprinting(boolean s) {}
        @Override public boolean isSneaking() { return sneakingHeld; }
        @Override public void setSneakingKeyHeld(boolean h) { this.sneakingHeld = h; }
        @Override public float getAttackCooldownProgress() { return 0.96f; }
        @Override public int getPingMs() { return 21; }
        @Override public float getHealth() { return 19.5f; }
        @Override public float getMaxHealth() { return 20.0f; }
        @Override public List<ItemInfo> getArmorItems() {
            return Arrays.asList(
                    new ItemInfo("minecraft:netherite_helmet", "Незеритовый шлем", 1, 18, 407),
                    new ItemInfo("minecraft:netherite_chestplate", "Незеритовый нагрудник", 1, 24, 592),
                    new ItemInfo("minecraft:netherite_leggings", "Незеритовые поножи", 1, 19, 555),
                    new ItemInfo("minecraft:netherite_boots", "Незеритовые ботинки", 1, 15, 481)
            );
        }
        @Override public ItemInfo getMainHandItem() {
            if (customMainHand != null) return customMainHand;
            return new ItemInfo("minecraft:netherite_sword", "Незеритовый меч", 1, 32, 2031);
        }
        @Override public ItemInfo getOffHandItem() { return new ItemInfo("minecraft:totem_of_undying", "Тотем бессмертия", 1, 0, 0); }
        @Override public List<ItemInfo> getInventoryItems() {
            if (customInventory != null) return customInventory;
            return Arrays.asList(
                    new ItemInfo("minecraft:golden_apple", "Золотое яблоко", 16, 0, 0),
                    new ItemInfo("minecraft:ender_pearl", "Эндер-жемчуг", 8, 0, 0),
                    new ItemInfo("minecraft:totem_of_undying", "Тотем бессмертия", 3, 0, 0)
            );
        }
        @Override public int getSelectedHotbarSlot() { return selectedSlot; }
        @Override public void selectHotbarSlot(int slot) { this.selectedSlot = slot; }
        @Override public boolean isStandingOverEdge(double edgeInset) { return standingOverEdge; }
        @Override public boolean placeBlockAtCrosshair() { return targetedBlockBox != null; }
        @Override public double[] getTargetedBlockBox() { return targetedBlockBox; }
        @Override public String getTargetedBlockId() { return targetedBlockId; }
        @Override public List<double[]> scanBedWarsBeds(int radius) { return bedWarsBeds; }
        @Override public List<double[]> getIncomingProjectiles(double range) { return incomingProjectiles; }
        @Override public List<StatusEffectInfo> getActiveEffects() {
            return Arrays.asList(
                    new StatusEffectInfo("speed", "Скорость II", 2, 1640, true, 0x33EBFF),
                    new StatusEffectInfo("strength", "Сила II", 2, 1280, true, 0xFF6B6B)
            );
        }
        @Override public String getBiomeName() { return "PvP Arena"; }
        @Override public String getBiomePrecipitation() { return "Clear"; }
        @Override public float getBiomeTemperature() { return 0.8f; }
        @Override public int getBlockLight() { return 15; }
        @Override public int getSkyLight() { return 15; }
        @Override public long getDayTime() { return 13000L; }
        @Override public String getDifficultyName() { return "Hard"; }
        @Override public int getLoadedChunksCount() { return 441; }
        @Override public boolean isRaining() { return false; }
        @Override public boolean isThundering() { return false; }
        @Override public int getFps() { return 384; }
        @Override public double getGamma() { return 12.0; }
        @Override public void setGamma(double g) {}
        @Override public double getBaseFov() { return fov; }
        @Override public void setBaseFov(double f) { this.fov = f; }
        @Override public boolean isViewBobbing() { return false; }
        @Override public void setViewBobbing(boolean b) {}
        @Override public double getMouseSensitivity() { return 0.5; }
        @Override public void setMouseSensitivity(double s) {}
        @Override public int getPerspective() { return 0; }
        @Override public void setPerspective(int p) {}
        @Override public boolean isChunkBordersVisible() { return false; }
        @Override public boolean toggleChunkBorders() { return false; }
        @Override public List<String> getEnabledResourcePacks() { return Collections.singletonList("vanilla"); }
        @Override public List<String> getAvailableResourcePacks() { return Collections.singletonList("vanilla"); }
        @Override public void setClipboard(String text) {}
        @Override public String getClipboard() { return ""; }
        @Override public double getGuiScale() { return 2.0; }
        @Override public boolean isClickGuiOpen() { return clickGuiOpen; }
        @Override public boolean isHudEditorOpen() { return hudEditorOpen; }
        @Override public boolean isChatScreenOpen() { return chatScreenOpen; }
        @Override public double getScaledMouseX() { return scaledMouseX; }
        @Override public double getScaledMouseY() { return scaledMouseY; }
        @Override public void openClickGui() { clickGuiOpen = true; }
        @Override public void openHudEditor() { hudEditorOpen = true; }
        @Override public void openCustomMainMenu() {}
        @Override public void openVanillaSingleplayer() {}
        @Override public void openVanillaMultiplayer() {}
        @Override public void openVanillaOptions() {}
        @Override public void openVanillaResourcePacks() {}
        @Override public void closeScreen() { clickGuiOpen = false; hudEditorOpen = false; }
        @Override public void quitGame() {}
        @Override public boolean isKeyDown(int glfwKey) { return glfwKey == KeyCodes.KEY_W || glfwKey == KeyCodes.KEY_A; }
        @Override public boolean isMouseButtonDown(int button) { return button == 0; }
        @Override public String getKeyName(int keyCode) { return KeyCodes.formatKey(keyCode); }
        @Override public Map<String, Integer> getVanillaKeyBindings() {
            Map<String, Integer> m = new HashMap<String, Integer>();
            m.put("Jump", Integer.valueOf(KeyCodes.KEY_SPACE));
            return m;
        }
        @Override public List<TargetEntityInfo> getNearbyTargets(double range, boolean p, boolean h, boolean a, boolean inv) {
            return combatTargets;
        }
        @Override public List<TargetEntityInfo> getEspEntities(double range, boolean p, boolean h, boolean a, boolean items, boolean inv) {
            List<TargetEntityInfo> out = new java.util.ArrayList<TargetEntityInfo>();
            for (TargetEntityInfo e : espEntities) {
                if (e.isItemEntity()) {
                    if (items) out.add(e);
                } else if (e.isPlayer()) {
                    if (p) out.add(e);
                } else if (e.isHostile()) {
                    if (h) out.add(e);
                } else if (a) {
                    out.add(e);
                }
            }
            return out;
        }
        @Override public boolean attackEntityById(int entityId, boolean swingHand) { return true; }
    }

    private static void setExpandedModule(AxsesCore core, Module m) throws Exception {
        Field f = core.getClickGuiController().getClass().getDeclaredField("expandedModule");
        f.setAccessible(true);
        f.set(core.getClickGuiController(), m);
    }

    @Test
    public void renderRealInGameClientScreenshots(@TempDir Path tempDir) throws Exception {
        String outProp = System.getenv("AXSES_INGAME_SCREENSHOT_OUT");
        Path outDir = (outProp != null && !outProp.trim().isEmpty())
                ? Paths.get(outProp)
                : tempDir.resolve("screenshots");
        Files.createDirectories(outDir);

        // 1. IN-GAME PVP SCENE: Celestial Vision v3.5 — Entity ESP + 3D Soul Orbs + Dual Ring + 2D Radar + Totem/Gapple HUD + 3D Damage Indicators + Curved Target HUD
        {
            AwtScenePlatform p = new AwtScenePlatform(tempDir.resolve("s1"));
            AxsesCore core = new AxsesCore(tempDir.resolve("s1"), p, p);
            core.getLocalizationManager().setActiveLocale("ru_RU");
            core.getThemeManager().setActiveTheme("axses-vision");

            TargetEntityInfo mainEnemy = new TargetEntityInfo(
                    101, "DiamondPlayer", true, false, false, false,
                    14.5f, 20.0f, 2.0f, 3.1f, 6, 18,
                    0.55, 64.0, 3.35, 65.62,
                    0.25, 64.0, 3.05, 0.85, 65.8, 3.65,
                    true, "Незеритовый меч"
            );
            TargetEntityInfo secondEnemy = new TargetEntityInfo(
                    102, "HolyWarrior", true, false, false, false,
                    19.0f, 20.0f, 0.0f, 7.6f, 0, 20,
                    3.05, 64.0, 7.4, 65.62,
                    2.75, 64.0, 7.1, 3.35, 65.8, 7.7,
                    true, "Золотое яблоко x8"
            );
            TargetEntityInfo behindEnemy = new TargetEntityInfo(
                    103, "FlankerBehind", true, false, false, false,
                    12.0f, 20.0f, 0.0f, 9.0f, 0, 16,
                    3.0, 64.0, -6.0, 65.62,
                    2.7, 64.0, -6.3, 3.3, 65.8, -5.7,
                    true, "Тотем бессмертия"
            );
            TargetEntityInfo droppedGapple = new TargetEntityInfo(
                    201, "Золотое яблоко x12", false, false, false, true,
                    1.0f, 1.0f, 0.0f, 5.2f, 0, 0,
                    -2.15, 64.0, 4.9, 64.25,
                    -2.30, 64.0, 4.75, -2.00, 64.3, 5.05,
                    true, ""
            );

            p.combatTargets = Collections.singletonList(mainEnemy);
            p.espEntities = Arrays.asList(mainEnemy, secondEnemy, behindEnemy, droppedGapple);

            Module ka = core.getModuleManager().getById("kill_aura");
            for (Setting<?> s : ka.getSettings()) {
                if ("server_profile".equals(s.getId())) ((ModeSetting) s).setValue("FunTime");
                if ("range".equals(s.getId())) ((NumberSetting) s).setValue(Double.valueOf(3.15));
            }
            ka.setEnabled(true, p);
            Module esp = core.getModuleManager().getById("entity_esp");
            for (Setting<?> s : esp.getSettings()) {
                if ("box_style".equals(s.getId())) ((ModeSetting) s).setValue("Hyperion Neon");
            }
            esp.setEnabled(true, p);
            core.getModuleManager().getById("target_strafe").setEnabled(true, p);
            core.getModuleManager().getById("neon_halo_trails").setEnabled(true, p);
            core.getModuleManager().getById("china_hat").setEnabled(true, p);
            core.getModuleManager().getById("jump_circles").setEnabled(true, p);
            core.getModuleManager().getById("breadcrumbs").setEnabled(true, p);
            core.getModuleManager().getById("hit_particles").setEnabled(true, p);
            core.getModuleManager().getById("damage_indicators").setEnabled(true, p);
            core.getModuleManager().getById("radar_hud").setEnabled(true, p);
            core.getModuleManager().getById("totem_counter").setEnabled(true, p);
            core.getModuleManager().getById("gapple_tracker").setEnabled(true, p);
            core.getModuleManager().getById("item_esp").setEnabled(true, p);
            core.getModuleManager().getById("tracers").setEnabled(true, p);
            core.getModuleManager().getById("potion_hud").setEnabled(true, p);
            core.getRuntimeState().addOrReplaceWaypoint(new dev.axsesclient.common.modules.impl.ClientRuntimeState.Waypoint(
                    "Арена", 5.6, 64.0, 11.0, "minecraft:overworld", "mc.funtime.su", 0xFF00F5D4
            ));
            core.getModuleManager().getById("waypoints").setEnabled(true, p);

            core.getAnimationManager().setReducedMotion(true);
            dev.axsesclient.common.hud.HudElement thud = core.getHudManager().getById("target_hud");
            if (thud != null) {
                thud.setNormX(0.665f);
                thud.setNormY(0.405f);
            }

            // Simulate a curved arena dash + jump so Breadcrumbs & JumpCircles spawn live 3D geometry in view
            double[][] pathSteps = new double[][] {
                    { 2.10, 5.8 }, { 1.75, 5.2 }, { 1.35, 4.7 }, { 0.95, 4.2 },
                    { 0.50, 3.8 }, { -0.10, 3.5 }, { 1.65, 4.4 }
            };
            p.onGround = true;
            for (int step = 0; step < pathSteps.length; step++) {
                p.playerX = pathSteps[step][0];
                p.playerZ = pathSteps[step][1];
                if (step == pathSteps.length - 1) {
                    p.onGround = false; // triggers live JumpCircle at (1.45, 64.04, 4.3)!
                }
                core.onClientTick();
            }
            Thread.sleep(240);
            p.playerX = 0.0;
            p.playerY = 64.0;
            p.playerZ = 0.0;
            p.onGround = true;

            for (int click = 0; click < 8; click++) {
                core.onInGameMouseClick(0);
            }
            core.onInGameMouseClick(1);
            core.onInGameMouseClick(1);
            core.onClientTick();

            // Restore camera facing forward so all 3D effects + entities + Target HUD compose cleanly
            p.yaw = 0.0f;
            p.pitch = 5.0f;
            p.paintStylizedWorldBackdrop(true);
            core.onHudRender(p);

            File out1 = outDir.resolve("ingame-pvp-esp-targethud.png").toFile();
            ImageIO.write(p.image, "PNG", out1);
            p.dispose();
            assertTrue(out1.exists() && out1.length() > 10000);
        }

        // 2. RIGHT-SHIFT CURVED CLICKGUI — COMBAT TAB WITH KILLAURA EXPANDED (CELESTIAL VISION)
        {
            AwtScenePlatform p = new AwtScenePlatform(tempDir.resolve("s2"));
            AxsesCore core = new AxsesCore(tempDir.resolve("s2"), p, p);
            core.getLocalizationManager().setActiveLocale("ru_RU");
            core.getThemeManager().setActiveTheme("axses-vision");
            p.clickGuiOpen = true;

            Module ka = core.getModuleManager().getById("kill_aura");
            for (Setting<?> s : ka.getSettings()) {
                if ("server_profile".equals(s.getId())) ((ModeSetting) s).setValue("SnowGrim");
                if ("range".equals(s.getId())) ((NumberSetting) s).setValue(Double.valueOf(3.00));
                if ("fov".equals(s.getId())) ((NumberSetting) s).setValue(Double.valueOf(175.0));
                if ("rotations".equals(s.getId())) ((ModeSetting) s).setValue("SnowGrim Coupled");
                if ("move_fix".equals(s.getId())) ((ModeSetting) s).setValue("Silent Strafe");
            }
            ka.setEnabled(true, p);
            core.getModuleManager().getById("trigger_bot").setEnabled(true, p);
            core.getModuleManager().getById("totem_counter").setEnabled(true, p);
            core.getModuleManager().getById("gapple_tracker").setEnabled(true, p);

            p.paintStylizedWorldBackdrop(false);
            core.getAnimationManager().setReducedMotion(true);
            core.getClickGuiController().onOpen();
            core.getClickGuiController().setSelectedCategory(ModuleCategory.COMBAT_TRAINING);
            setExpandedModule(core, ka);
            core.getClickGuiController().render(p, p, 20, 20, 1.0f);

            File out2 = outDir.resolve("ingame-clickgui-killaura.png").toFile();
            ImageIO.write(p.image, "PNG", out2);
            p.dispose();
            assertTrue(out2.exists() && out2.length() > 10000);
        }

        // 3. RIGHT-SHIFT CURVED CLICKGUI — VISUALS & ESP TAB (EMERALD AURORA THEME)
        {
            AwtScenePlatform p = new AwtScenePlatform(tempDir.resolve("s3"));
            AxsesCore core = new AxsesCore(tempDir.resolve("s3"), p, p);
            core.getLocalizationManager().setActiveLocale("ru_RU");
            core.getThemeManager().setActiveTheme("axses-emerald");
            p.clickGuiOpen = true;

            Module esp = core.getModuleManager().getById("entity_esp");
            for (Setting<?> s : esp.getSettings()) {
                if ("box_style".equals(s.getId())) ((ModeSetting) s).setValue("Vision Capsule");
            }
            esp.setEnabled(true, p);
            core.getModuleManager().getById("china_hat").setEnabled(true, p);
            core.getModuleManager().getById("jump_circles").setEnabled(true, p);
            core.getModuleManager().getById("breadcrumbs").setEnabled(true, p);
            core.getModuleManager().getById("hit_particles").setEnabled(true, p);
            core.getModuleManager().getById("damage_indicators").setEnabled(true, p);

            p.paintStylizedWorldBackdrop(false);
            core.getAnimationManager().setReducedMotion(true);
            core.getClickGuiController().onOpen();
            core.getClickGuiController().setSelectedCategory(ModuleCategory.RENDER);
            core.getClickGuiController().setSearchQuery("ESP");
            setExpandedModule(core, esp);
            core.getClickGuiController().render(p, p, 20, 20, 1.0f);

            File out3 = outDir.resolve("ingame-clickgui-esp.png").toFile();
            ImageIO.write(p.image, "PNG", out3);
            p.dispose();
            assertTrue(out3.exists() && out3.length() > 10000);
        }

        // 4. RIGHT-SHIFT CURVED CLICKGUI — SETTINGS TAB + 14-THEME GALLERY
        {
            AwtScenePlatform p = new AwtScenePlatform(tempDir.resolve("s4"));
            AxsesCore core = new AxsesCore(tempDir.resolve("s4"), p, p);
            core.getLocalizationManager().setActiveLocale("ru_RU");
            core.getThemeManager().setActiveTheme("axses-vision");
            p.clickGuiOpen = true;

            p.paintStylizedWorldBackdrop(false);
            core.getAnimationManager().setReducedMotion(true);
            core.getClickGuiController().onOpen();
            core.getClickGuiController().setSelectedCategory(ModuleCategory.SETTINGS);
            core.getClickGuiController().render(p, p, 535, 102, 1.0f);

            File out4 = outDir.resolve("ingame-clickgui-settings-ru.png").toFile();
            ImageIO.write(p.image, "PNG", out4);
            p.dispose();
            assertTrue(out4.exists() && out4.length() > 10000);
        }

        // 5. CUSTOM MAIN MENU (CELESTIAL VISION v3.5 RU)
        {
            AwtScenePlatform p = new AwtScenePlatform(tempDir.resolve("s5"));
            p.inWorld = false;
            AxsesCore core = new AxsesCore(tempDir.resolve("s5"), p, p);
            core.getLocalizationManager().setActiveLocale("ru_RU");
            core.getThemeManager().setActiveTheme("axses-vision");

            core.getMainMenuController().render(p, p, 480, 290, 1.0f);

            File out5 = outDir.resolve("ingame-mainmenu.png").toFile();
            ImageIO.write(p.image, "PNG", out5);
            p.dispose();
            assertTrue(out5.exists() && out5.length() > 10000);
        }

        // 6. HUD EDITOR (DRAGGABLE CURVED VISION WIDGETS + 2D RADAR + TOTEMS + TARGET HUD + KEYSTROKES)
        {
            AwtScenePlatform p = new AwtScenePlatform(tempDir.resolve("s6"));
            AxsesCore core = new AxsesCore(tempDir.resolve("s6"), p, p);
            core.getLocalizationManager().setActiveLocale("ru_RU");
            core.getThemeManager().setActiveTheme("axses-vision");
            core.getModuleManager().getById("kill_aura").setEnabled(true, p);
            core.getModuleManager().getById("entity_esp").setEnabled(true, p);
            core.getModuleManager().getById("china_hat").setEnabled(true, p);
            core.getModuleManager().getById("radar_hud").setEnabled(true, p);
            core.getModuleManager().getById("totem_counter").setEnabled(true, p);
            core.getModuleManager().getById("gapple_tracker").setEnabled(true, p);
            core.getModuleManager().getById("potion_hud").setEnabled(true, p);
            p.hudEditorOpen = true;

            p.paintStylizedWorldBackdrop(false);
            dev.axsesclient.common.hud.HudElement thud = core.getHudManager().getById("target_hud");
            if (thud != null) {
                core.getHudEditorController().mouseClicked(p, thud.getPixelX(p.scaledW) + 12f, thud.getPixelY(p.scaledH) + 12f, 0);
                core.getHudEditorController().mouseReleased();
            }
            core.getHudEditorController().render(p, p, 390, 285, 1.0f);

            File out6 = outDir.resolve("ingame-hud-editor.png").toFile();
            ImageIO.write(p.image, "PNG", out6);
            p.dispose();
            assertTrue(out6.exists() && out6.length() > 10000);
        }

        // 7. RIGHT-SHIFT CURVED CLICKGUI — 14-THEME GALLERY VIEW
        {
            AwtScenePlatform p = new AwtScenePlatform(tempDir.resolve("s7"));
            AxsesCore core = new AxsesCore(tempDir.resolve("s7"), p, p);
            core.getLocalizationManager().setActiveLocale("ru_RU");
            core.getThemeManager().setActiveTheme("axses-vision");
            p.clickGuiOpen = true;

            p.paintStylizedWorldBackdrop(false);
            core.getAnimationManager().setReducedMotion(true);
            core.getClickGuiController().onOpen();
            Field viewField = core.getClickGuiController().getClass().getDeclaredField("activeView");
            viewField.setAccessible(true);
            Object[] views = viewField.getType().getEnumConstants();
            for (Object v : views) {
                if ("THEMES".equals(v.toString())) {
                    viewField.set(core.getClickGuiController(), v);
                    break;
                }
            }
            core.getClickGuiController().render(p, p, 240, 140, 1.0f);

            File out7 = outDir.resolve("ingame-clickgui-themes.png").toFile();
            ImageIO.write(p.image, "PNG", out7);
            p.dispose();
            assertTrue(out7.exists() && out7.length() > 10000);
        }

        // 8. IN-GAME CHAT HUD DRAGGING + SPOTIFY MUSIC PLAYER (CONTINUE / STOP) + BARITONE 3D AUTOPILOT ROUTE
        {
            AwtScenePlatform p = new AwtScenePlatform(tempDir.resolve("s8"));
            AxsesCore core = new AxsesCore(tempDir.resolve("s8"), p, p);
            core.getLocalizationManager().setActiveLocale("ru_RU");
            core.getThemeManager().setActiveTheme("axses-vision");

            // Configure Spotify MediaSessionBridge showcase state
            dev.axsesclient.common.media.MediaSessionBridge.getInstance().setSimulatedSpotifyStateForShowcase(
                    true,
                    dev.axsesclient.common.media.MediaSessionBridge.PlaybackState.PAUSED,
                    "Kordhell",
                    "Murder In My Mind",
                    64.0f,
                    145
            );

            TargetEntityInfo rival = new TargetEntityInfo(
                    109, "MatrixGuard", true, false, false, false,
                    15.5f, 20.0f, 2.0f, 2.95f, 3, 19,
                    -1.45, 64.0, 4.6, 65.62,
                    -1.75, 64.0, 4.3, -1.15, 65.8, 4.9,
                    true, "Незеритовый топор"
            );
            p.combatTargets = Collections.singletonList(rival);
            p.espEntities = Collections.singletonList(rival);
            core.getRuntimeState().lockTarget(rival);

            // Set active Waypoint ahead at (-3.2, 64, 24.0) so Baritone 3D route & beacon project right down the arena
            core.getRuntimeState().addOrReplaceWaypoint(new dev.axsesclient.common.modules.impl.ClientRuntimeState.Waypoint(
                    "Алмазная шахта", -3.2, 64.0, 24.0, "minecraft:overworld", "mc.funtime.su", 0xFF00F5D4
            ));
            Module baritone = core.getModuleManager().getById("baritone_bot");
            baritone.setEnabled(true, p);
            core.getModuleManager().getById("spotify_music_hud").setEnabled(true, p);
            core.getModuleManager().getById("fps_optimizer").setEnabled(true, p);
            core.getModuleManager().getById("chat_hud_drag").setEnabled(true, p);
            core.getModuleManager().getById("entity_esp").setEnabled(true, p);
            core.getModuleManager().getById("radar_hud").setEnabled(true, p);
            core.onClientTick();

            // Open Chat screen ('T') and actively drag Target HUD onto the vertical magnetic center snap line via real onChatHudMousePress!
            p.chatScreenOpen = true;
            dev.axsesclient.common.hud.HudElement thud = core.getHudManager().getById("target_hud");
            if (thud != null) {
                float centeredX = (p.scaledW - thud.getScaledWidth()) * 0.5f;
                core.getHudManager().moveElementWithSnapping(thud, centeredX, 112.0f, p.scaledW, p.scaledH);
                p.scaledMouseX = centeredX + 40.0f;
                p.scaledMouseY = 128.0f;
                core.onChatHudMousePress(p.scaledMouseX, p.scaledMouseY, 0);
            }

            p.yaw = 0.0f;
            p.pitch = 5.0f;
            p.paintStylizedWorldBackdrop(true);

            // Render stylized open Minecraft Chat log & bottom ChatScreen input bar (cleanly above Custom Glass Hotbar & Sprint HUD)
            float chatLogX = 8.0f, chatLogY = p.scaledH - 148.0f, chatLogW = 360.0f, chatLogH = 66.0f;
            p.fillRoundedRect(chatLogX, chatLogY, chatLogW, chatLogH, 8.0f, 0xD8050814);
            p.drawRoundedOutline(chatLogX, chatLogY, chatLogW, chatLogH, 8.0f, 1.0f, 0x6638BDF8);
            core.getFontManager().draw(p, "[AXSES 6.0] Celestial Ultra: 3D Chams + Glass Hotbar активны", chatLogX + 7f, chatLogY + 7f, 0xFF38BDF8, 0.80f);
            core.getFontManager().draw(p, "[Spotify] Синхронизация активна · Музыка Minecraft остановлена", chatLogX + 7f, chatLogY + 21f, 0xFF1DB954, 0.80f);
            core.getFontManager().draw(p, "[Baritone] Построен 3D-маршрут к метке «Алмазная шахта» (24м)", chatLogX + 7f, chatLogY + 35f, 0xFF00F5D4, 0.80f);
            core.getFontManager().draw(p, "[Чат HUD] Зажмите ЛКМ на любом виджете для перемещения!", chatLogX + 7f, chatLogY + 49f, 0xFFE2E8F0, 0.80f);

            float inputY = p.scaledH - 64.0f;
            p.fillRoundedRect(8.0f, inputY, 360.0f, 15.0f, 6.0f, 0xEB040712);
            p.drawRoundedOutline(8.0f, inputY, 360.0f, 15.0f, 6.0f, 1.0f, 0xFF00F5D4);
            core.getFontManager().draw(p, "> #mine diamond_ore deepslate_diamond_ore █", 14.0f, inputY + 4.0f, 0xFFF8FAFF, 0.84f);

            core.onHudRender(p);

            // Reset Spotify bridge back to playing for any subsequent callers
            dev.axsesclient.common.media.MediaSessionBridge.getInstance().playContinue();

            File out8 = outDir.resolve("ingame-chat-hud-music-baritone.png").toFile();
            ImageIO.write(p.image, "PNG", out8);
            p.dispose();
            assertTrue(out8.exists() && out8.length() > 10000);
        }

        // 9. IN-GAME BEDWARS SKY-ISLAND: FASTBRIDGE (NINJA/EAGLE) + HYPERION 3D BLOCK CONTOURS + BEDWARS ESP + FIREBALL ARC + INVIS SCANNER
        {
            AwtScenePlatform p = new AwtScenePlatform(tempDir.resolve("s9"));
            AxsesCore core = new AxsesCore(tempDir.resolve("s9"), p, p);
            core.getLocalizationManager().setActiveLocale("ru_RU");
            core.getThemeManager().setActiveTheme("axses-vision");

            p.selectedSlot = 1;
            p.standingOverEdge = true;
            p.customMainHand = new ItemInfo("minecraft:cyan_wool", "Бирюзовая шерсть", 48, 0, 0);
            p.customInventory = Arrays.asList(
                    new ItemInfo("minecraft:diamond_sword", "Алмазный меч", 1, 0, 1561),
                    new ItemInfo("minecraft:cyan_wool", "Бирюзовая шерсть", 48, 0, 0),
                    new ItemInfo("minecraft:cyan_wool", "Бирюзовая шерсть", 64, 0, 0),
                    new ItemInfo("minecraft:golden_apple", "Золотое яблоко", 6, 0, 0),
                    new ItemInfo("minecraft:shears", "Ножницы", 1, 0, 238),
                    new ItemInfo("minecraft:diamond_pickaxe", "Алмазная кирка", 1, 0, 1561),
                    new ItemInfo("minecraft:iron_ingot", "Железный слиток", 44, 0, 0),
                    new ItemInfo("minecraft:gold_ingot", "Золотой слиток", 14, 0, 0),
                    new ItemInfo("minecraft:diamond", "Алмаз", 6, 0, 0),
                    new ItemInfo("minecraft:emerald", "Изумруд", 4, 0, 0)
            );
            // Crosshair aimed at the leading bridge block (-0.5..0.5, 63..64, 6..7) on its +Z face -> ghost preview at z = 7..8!
            p.targetedBlockBox = new double[] {
                    -0.5, 63.0, 6.0,
                    -0.5, 63.0, 6.0,
                    0.5, 64.0, 7.0,
                    0.0, 0.0, 1.0
            };
            p.targetedBlockId = "minecraft:cyan_wool";
            p.bedWarsBeds = Collections.singletonList(new double[] { -0.5, 64.0, 15.0, 3.0, 0xFF3B5C });
            p.incomingProjectiles = Collections.singletonList(new double[] {
                    4.9, 68.4, 11.4, -0.24, -0.24, -0.44, 1.0, 501.0, 12.8
            });

            TargetEntityInfo rusher = new TargetEntityInfo(
                    301, "BedBreakerPro", true, false, false, false,
                    16.0f, 20.0f, 0.0f, 12.6f, 2, 15,
                    -1.85, 64.0, 12.8, 65.62,
                    -2.15, 64.0, 12.5, -1.55, 65.8, 13.1,
                    true, "Алмазный меч"
            );
            TargetEntityInfo invisFlanker = new TargetEntityInfo(
                    302, "GhostFlanker", true, false, false, false,
                    20.0f, 20.0f, 0.0f, 9.4f, 0, 8,
                    2.65, 64.0, 9.2, 65.62,
                    2.35, 64.0, 8.9, 2.95, 65.8, 9.5,
                    true, "Динамит [INVIS]"
            );
            TargetEntityInfo emeraldDrop = new TargetEntityInfo(
                    401, "Изумруд x2", false, false, false, true,
                    1.0f, 1.0f, 0.0f, 14.2f, 0, 0,
                    2.4, 64.0, 14.2, 64.25,
                    2.25, 64.0, 14.05, 2.55, 64.3, 14.35,
                    true, ""
            );
            TargetEntityInfo goldDrop = new TargetEntityInfo(
                    402, "Золотой слиток x6", false, false, false, true,
                    1.0f, 1.0f, 0.0f, 13.8f, 0, 0,
                    -3.1, 64.0, 13.8, 64.25,
                    -3.25, 64.0, 13.65, -2.95, 64.3, 13.95,
                    true, ""
            );

            p.combatTargets = Collections.singletonList(rusher);
            p.espEntities = Arrays.asList(rusher, invisFlanker, emeraldDrop, goldDrop);
            core.getRuntimeState().lockTarget(rusher);

            Module fb9 = core.getModuleManager().getById("fast_bridge");
            for (Setting<?> s : fb9.getSettings()) {
                if ("place_cps".equals(s.getId())) ((NumberSetting) s).setValue(Double.valueOf(22.0));
            }
            fb9.setEnabled(true, p);
            core.getModuleManager().getById("block_contour_pro").setEnabled(true, p);
            core.getModuleManager().getById("bedwars_esp").setEnabled(true, p);
            core.getModuleManager().getById("fireball_tnt_alert").setEnabled(true, p);
            core.getModuleManager().getById("invis_armor_detector").setEnabled(true, p);
            core.getModuleManager().getById("neon_halo_trails").setEnabled(true, p);
            core.getModuleManager().getById("entity_esp").setEnabled(true, p);
            core.getModuleManager().getById("radar_hud").setEnabled(true, p);

            // Simulate several bridging ticks at bridging pitch 75.6° so FastBridge places blocks & builds real blocks/sec telemetry + trail ribbon
            dev.axsesclient.common.hud.HudElement thud9 = core.getHudManager().getById("target_hud");
            if (thud9 != null) {
                thud9.setNormX(0.695f);
                thud9.setNormY(0.285f);
            }
            p.pitch = 75.6f;
            for (int t = 0; t < 8; t++) {
                p.playerZ = t * 0.18;
                core.onClientTick();
                Thread.sleep(50L);
            }
            p.playerZ = 0.0;
            p.yaw = 0.0f;
            p.pitch = 8.5f;

            p.paintBedWarsSkyScene();
            core.onHudRender(p);

            File out9 = outDir.resolve("ingame-bedwars-fastbridge-contours.png").toFile();
            ImageIO.write(p.image, "PNG", out9);
            p.dispose();
            assertTrue(out9.exists() && out9.length() > 10000);
        }

        // 10. RIGHT-SHIFT HYPERION CLICKGUI — MOVEMENT TAB WITH FASTBRIDGE EXPANDED
        {
            AwtScenePlatform p = new AwtScenePlatform(tempDir.resolve("s10"));
            AxsesCore core = new AxsesCore(tempDir.resolve("s10"), p, p);
            core.getLocalizationManager().setActiveLocale("ru_RU");
            core.getThemeManager().setActiveTheme("axses-vision");
            p.clickGuiOpen = true;

            Module fb = core.getModuleManager().getById("fast_bridge");
            fb.setEnabled(true, p);
            core.getModuleManager().getById("bridge_clutch").setEnabled(true, p);
            core.getModuleManager().getById("toggle_sprint").setEnabled(true, p);

            p.paintBedWarsSkyScene();
            core.getAnimationManager().setReducedMotion(true);
            core.getClickGuiController().onOpen();
            core.getClickGuiController().setSelectedCategory(ModuleCategory.MOVEMENT);
            setExpandedModule(core, fb);
            core.getClickGuiController().mouseScrolled(-2.2);
            core.getClickGuiController().render(p, p, 20, 20, 1.0f);

            File out10 = outDir.resolve("ingame-clickgui-bedwars-contours.png").toFile();
            ImageIO.write(p.image, "PNG", out10);
            p.dispose();
            assertTrue(out10.exists() && out10.length() > 10000);
        }
    }
}
