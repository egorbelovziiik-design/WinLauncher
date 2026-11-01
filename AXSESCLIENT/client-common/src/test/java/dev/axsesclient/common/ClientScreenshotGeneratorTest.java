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
        double playerX = 0.0, playerY = 64.0, playerZ = 0.0;
        float yaw = 0.0f;   // Looking towards +Z
        float pitch = 4.0f;
        double fov = 78.0;
        List<TargetEntityInfo> espEntities = Collections.emptyList();
        List<TargetEntityInfo> combatTargets = Collections.emptyList();

        AwtScenePlatform(Path gameDir) {
            this.gameDir = gameDir;
            this.image = new BufferedImage(scaledW * scaleFactor, scaledH * scaleFactor, BufferedImage.TYPE_INT_ARGB);
            this.g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
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

        void paintStylizedWorldBackdrop(boolean drawEntities) {
            // Twilight PvP arena sky + horizon glow
            fillGradientVertical(0, 0, scaledW, scaledH * 0.54f, 0xFF0A0F1F, 0xFF1A2542);
            fillGradientVertical(0, scaledH * 0.54f, scaledW, scaledH * 0.46f, 0xFF16231F, 0xFF0D1514);

            // Perspective arena floor grid lines
            float horizonY = scaledH * 0.54f;
            g.setColor(new Color(120, 180, 160, 26));
            for (int row = 1; row <= 12; row++) {
                float t = (float) Math.pow(row / 12.0, 1.8);
                int gy = Math.round(horizonY + t * (scaledH - horizonY));
                g.drawLine(0, gy, scaledW, gy);
            }
            for (int col = -10; col <= 10; col++) {
                int xTop = Math.round(scaledW * 0.5f + col * 18f);
                int xBot = Math.round(scaledW * 0.5f + col * 95f);
                g.drawLine(xTop, Math.round(horizonY), xBot, scaledH);
            }

            if (drawEntities) {
                for (TargetEntityInfo e : espEntities) {
                    if (e.isItemEntity()) {
                        float[] c = projectWorldToScreen(e.getX(), e.getY() + 0.18, e.getZ());
                        if (c != null && c[2] > 0.2f) {
                            fillRoundedRect(c[0] - 5, c[1] - 5, 10, 10, 3, 0xFFFFCA28);
                        }
                        continue;
                    }
                    float[] top = projectWorldToScreen(e.getX(), e.getMaxY(), e.getZ());
                    float[] bot = projectWorldToScreen(e.getX(), e.getMinY(), e.getZ());
                    if (top != null && bot != null && top[2] > 0.2f && bot[2] > 0.2f) {
                        float h = Math.max(18f, bot[1] - top[1]);
                        float w = h * 0.36f;
                        float cx = (top[0] + bot[0]) * 0.5f;
                        // Stylized 3D player armor silhouette inside hitbox
                        float headS = w * 0.62f;
                        fillRoundedRect(cx - headS * 0.5f, top[1] + 2f, headS, headS, 2f, e.getHurtTime() > 0 ? 0xFFE15566 : 0xFF38BDF8);
                        fillRoundedRect(cx - w * 0.42f, top[1] + headS + 3f, w * 0.84f, h * 0.42f, 2f, e.getHurtTime() > 0 ? 0xFFB8334A : 0xFF334155);
                        fillRoundedRect(cx - w * 0.36f, top[1] + headS + h * 0.46f, w * 0.32f, h * 0.34f, 1f, 0xFF1E293B);
                        fillRoundedRect(cx + w * 0.04f, top[1] + headS + h * 0.46f, w * 0.32f, h * 0.34f, 1f, 0xFF1E293B);
                    }
                }

                // Subtle vanilla-style hotbar at bottom center
                float hbW = 182f, hbH = 22f;
                float hbX = (scaledW - hbW) * 0.5f, hbY = scaledH - 25f;
                fillRoundedRect(hbX, hbY, hbW, hbH, 4f, 0xCC0D111C);
                drawRoundedOutline(hbX, hbY, hbW, hbH, 4f, 1f, 0x669060FF);
                for (int slot = 0; slot < 9; slot++) {
                    float sx = hbX + 2f + slot * 20f;
                    if (slot == 0) {
                        fillRoundedRect(sx, hbY + 2f, 18f, 18f, 3f, 0x559060FF);
                        drawRoundedOutline(sx, hbY + 2f, 18f, 18f, 3f, 1f, 0xFF00D9FF);
                    } else {
                        drawRoundedOutline(sx, hbY + 2f, 18f, 18f, 2f, 1f, 0x26FFFFFF);
                    }
                }
            }
        }

        @Override public String getMinecraftVersion() { return "1.21.11"; }
        @Override public String getJavaVersion() { return "21.0.13"; }
        @Override public String getFabricLoaderVersion() { return "0.20.1"; }
        @Override public String getFabricApiVersion() { return "0.141.6+1.21.11"; }
        @Override public List<String> getLoadedMods() { return Collections.singletonList("axsesclient@2.5.0"); }
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
        @Override public double getPrevPlayerZ() { return playerZ - 0.24; }
        @Override public float getPlayerYaw() { return yaw; }
        @Override public float getPlayerPitch() { return pitch; }
        @Override public void setPlayerRotation(float y, float p) { this.yaw = y; this.pitch = p; }
        @Override public boolean isOnGround() { return true; }
        @Override public boolean isSprinting() { return true; }
        @Override public void setSprinting(boolean s) {}
        @Override public boolean isSneaking() { return false; }
        @Override public void setSneakingKeyHeld(boolean h) {}
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
        @Override public ItemInfo getMainHandItem() { return new ItemInfo("minecraft:netherite_sword", "Незеритовый меч", 1, 32, 2031); }
        @Override public ItemInfo getOffHandItem() { return new ItemInfo("minecraft:totem_of_undying", "Тотем бессмертия", 1, 0, 0); }
        @Override public List<ItemInfo> getInventoryItems() { return Collections.singletonList(new ItemInfo("minecraft:golden_apple", "Золотое яблоко", 16, 0, 0)); }
        @Override public int getSelectedHotbarSlot() { return 0; }
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
            if (items) {
                for (TargetEntityInfo e : espEntities) {
                    if (e.isItemEntity()) return Collections.singletonList(e);
                }
                return Collections.emptyList();
            }
            List<TargetEntityInfo> out = new java.util.ArrayList<TargetEntityInfo>();
            for (TargetEntityInfo e : espEntities) {
                if (!e.isItemEntity()) out.add(e);
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

        // 1. IN-GAME PVP SCENE: Entity ESP + Item ESP + Tracers + KillAura 3D Ring + Target HUD + Keystrokes
        {
            AwtScenePlatform p = new AwtScenePlatform(tempDir.resolve("s1"));
            AxsesCore core = new AxsesCore(tempDir.resolve("s1"), p, p);
            core.getLocalizationManager().setActiveLocale("ru_RU");

            TargetEntityInfo mainEnemy = new TargetEntityInfo(
                    101, "DiamondPlayer", true, false, false, false,
                    14.5f, 20.0f, 2.0f, 3.1f, 4, 18,
                    -0.35, 64.0, 3.1, 65.62,
                    -0.65, 64.0, 2.8, -0.05, 65.8, 3.4,
                    true, "Незеритовый меч"
            );
            TargetEntityInfo secondEnemy = new TargetEntityInfo(
                    102, "HolyWarrior", true, false, false, false,
                    19.0f, 20.0f, 0.0f, 7.6f, 0, 20,
                    2.9, 64.0, 7.0, 65.62,
                    2.6, 64.0, 6.7, 3.2, 65.8, 7.3,
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
                    -1.9, 64.0, 4.8, 64.25,
                    -2.05, 64.0, 4.65, -1.75, 64.3, 4.95,
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
            core.getModuleManager().getById("entity_esp").setEnabled(true, p);
            core.getModuleManager().getById("item_esp").setEnabled(true, p);
            core.getModuleManager().getById("tracers").setEnabled(true, p);

            core.getAnimationManager().setReducedMotion(true);
            core.onInGameMouseClick(0);
            core.onInGameMouseClick(0);
            core.onClientTick();

            //Restore camera facing forward so all 3 entities + Target HUD compose cleanly
            p.yaw = 0.0f;
            p.pitch = 4.0f;
            p.paintStylizedWorldBackdrop(true);
            core.onHudRender(p);

            File out1 = outDir.resolve("ingame-pvp-esp-targethud.png").toFile();
            ImageIO.write(p.image, "PNG", out1);
            p.dispose();
            assertTrue(out1.exists() && out1.length() > 10000);
        }

        // 2. CLICKGUI — COMBAT TAB WITH KILLAURA EXPANDED (FunTime / ReallyWorld / HolyWorld)
        {
            AwtScenePlatform p = new AwtScenePlatform(tempDir.resolve("s2"));
            AxsesCore core = new AxsesCore(tempDir.resolve("s2"), p, p);
            core.getLocalizationManager().setActiveLocale("ru_RU");
            p.clickGuiOpen = true;

            Module ka = core.getModuleManager().getById("kill_aura");
            for (Setting<?> s : ka.getSettings()) {
                if ("server_profile".equals(s.getId())) ((ModeSetting) s).setValue("FunTime");
                if ("range".equals(s.getId())) ((NumberSetting) s).setValue(Double.valueOf(3.15));
                if ("fov".equals(s.getId())) ((NumberSetting) s).setValue(Double.valueOf(190.0));
                if ("rotations".equals(s.getId())) ((ModeSetting) s).setValue("FunTime Human");
            }
            ka.setEnabled(true, p);
            core.getModuleManager().getById("trigger_bot").setEnabled(true, p);

            p.paintStylizedWorldBackdrop(false);
            core.getAnimationManager().setReducedMotion(true);
            core.getClickGuiController().onOpen();
            core.getClickGuiController().setSelectedCategory(ModuleCategory.COMBAT_TRAINING);
            setExpandedModule(core, ka);
            core.getClickGuiController().render(p, p, 420, 145, 1.0f);

            File out2 = outDir.resolve("ingame-clickgui-killaura.png").toFile();
            ImageIO.write(p.image, "PNG", out2);
            p.dispose();
            assertTrue(out2.exists() && out2.length() > 10000);
        }

        // 3. CLICKGUI — VISUALS & ESP TAB WITH ENTITY ESP EXPANDED
        {
            AwtScenePlatform p = new AwtScenePlatform(tempDir.resolve("s3"));
            AxsesCore core = new AxsesCore(tempDir.resolve("s3"), p, p);
            core.getLocalizationManager().setActiveLocale("ru_RU");
            p.clickGuiOpen = true;

            Module esp = core.getModuleManager().getById("entity_esp");
            esp.setEnabled(true, p);
            core.getModuleManager().getById("item_esp").setEnabled(true, p);
            core.getModuleManager().getById("tracers").setEnabled(true, p);

            p.paintStylizedWorldBackdrop(false);
            core.getAnimationManager().setReducedMotion(true);
            core.getClickGuiController().onOpen();
            core.getClickGuiController().setSelectedCategory(ModuleCategory.RENDER);
            core.getClickGuiController().setSearchQuery("ESP");
            setExpandedModule(core, esp);
            core.getClickGuiController().render(p, p, 420, 160, 1.0f);

            File out3 = outDir.resolve("ingame-clickgui-esp.png").toFile();
            ImageIO.write(p.image, "PNG", out3);
            p.dispose();
            assertTrue(out3.exists() && out3.length() > 10000);
        }

        // 4. CLICKGUI — SETTINGS TAB WITH RUSSIAN / ENGLISH LANGUAGE BANNER
        {
            AwtScenePlatform p = new AwtScenePlatform(tempDir.resolve("s4"));
            AxsesCore core = new AxsesCore(tempDir.resolve("s4"), p, p);
            core.getLocalizationManager().setActiveLocale("ru_RU");
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

        // 5. CUSTOM MAIN MENU (OBSIDIAN AURORA v2.5 RU)
        {
            AwtScenePlatform p = new AwtScenePlatform(tempDir.resolve("s5"));
            p.inWorld = false;
            AxsesCore core = new AxsesCore(tempDir.resolve("s5"), p, p);
            core.getLocalizationManager().setActiveLocale("ru_RU");
            core.getThemeManager().setActiveTheme("axses-purple");

            core.getMainMenuController().render(p, p, 480, 290, 1.0f);

            File out5 = outDir.resolve("ingame-mainmenu.png").toFile();
            ImageIO.write(p.image, "PNG", out5);
            p.dispose();
            assertTrue(out5.exists() && out5.length() > 10000);
        }

        // 6. HUD EDITOR (DRAGGABLE WIDGETS + SNAP GRID)
        {
            AwtScenePlatform p = new AwtScenePlatform(tempDir.resolve("s6"));
            AxsesCore core = new AxsesCore(tempDir.resolve("s6"), p, p);
            core.getLocalizationManager().setActiveLocale("ru_RU");
            p.hudEditorOpen = true;

            p.paintStylizedWorldBackdrop(false);
            core.getHudEditorController().onOpen();
            core.getHudEditorController().render(p, p, 390, 285, 1.0f);

            File out6 = outDir.resolve("ingame-hud-editor.png").toFile();
            ImageIO.write(p.image, "PNG", out6);
            p.dispose();
            assertTrue(out6.exists() && out6.length() > 10000);
        }
    }
}
