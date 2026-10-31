package dev.axsesclient.common;

import dev.axsesclient.common.compat.CompatibilityEngine;
import dev.axsesclient.common.hud.HudElement;
import dev.axsesclient.common.modules.Module;
import dev.axsesclient.common.modules.ModuleCategory;
import dev.axsesclient.common.modules.impl.CombatTrainingModulesRegistry;
import dev.axsesclient.common.platform.InputAdapter;
import dev.axsesclient.common.platform.ItemInfo;
import dev.axsesclient.common.platform.KeyCodes;
import dev.axsesclient.common.platform.MinecraftAdapter;
import dev.axsesclient.common.platform.RenderAdapter;
import dev.axsesclient.common.platform.StatusEffectInfo;
import dev.axsesclient.common.platform.TargetEntityInfo;
import dev.axsesclient.common.settings.ColorSetting;
import dev.axsesclient.common.settings.MultiSelectSetting;
import dev.axsesclient.common.settings.NumberSetting;
import dev.axsesclient.common.theme.AxsesTheme;
import dev.axsesclient.common.theme.ColorBlindMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

public class AxsesCoreTest {

    static final class MockPlatform implements MinecraftAdapter, InputAdapter, RenderAdapter {
        final Path gameDir;
        String mcVersion = "1.21.11";
        boolean inWorld = true;
        boolean singleplayer = true;
        boolean clickGuiOpen = false;
        boolean hudEditorOpen = false;
        double gamma = 1.0;
        double fov = 70.0;
        boolean bobbing = true;
        double sensitivity = 0.5;
        boolean sprinting = false;
        boolean sneaking = false;
        int scaledW = 960;
        int scaledH = 540;
        String clipboard = "";
        int drawCalls = 0;
        float attackCooldown = 0.96f;
        int lastAttackedEntityId = -1;
        float yaw = 90.0f;
        float pitch = 0.0f;
        boolean usingItem = false;
        List<TargetEntityInfo> mockTargets = Collections.emptyList();

        MockPlatform(Path gameDir) {
            this.gameDir = gameDir;
        }

        @Override public String getMinecraftVersion() { return mcVersion; }
        @Override public String getJavaVersion() { return "21.0.5"; }
        @Override public String getFabricLoaderVersion() { return "0.20.1"; }
        @Override public String getFabricApiVersion() { return "0.141.6+1.21.11"; }
        @Override public List<String> getLoadedMods() { return Collections.singletonList("axsesclient@1.0.0"); }
        @Override public Path getGameDir() { return gameDir; }
        @Override public boolean isInWorld() { return inWorld; }
        @Override public boolean isSingleplayer() { return singleplayer; }
        @Override public String getWorldOrServerName() { return singleplayer ? "GenesisPractice" : "play.example.net"; }
        @Override public String getDimensionId() { return "minecraft:overworld"; }
        @Override public String getPlayerName() { return "AxsesTester"; }
        @Override public double getPlayerX() { return 128.5; }
        @Override public double getPlayerY() { return 64.0; }
        @Override public double getPlayerZ() { return -256.0; }
        @Override public double getPrevPlayerX() { return 128.2; }
        @Override public double getPrevPlayerY() { return 64.0; }
        @Override public double getPrevPlayerZ() { return -256.0; }
        @Override public float getPlayerYaw() { return yaw; }
        @Override public float getPlayerPitch() { return pitch; }
        @Override public void setPlayerRotation(float y, float p) { this.yaw = y; this.pitch = p; }
        @Override public boolean isUsingItem() { return usingItem; }
        @Override public boolean isOnGround() { return true; }
        @Override public boolean isSprinting() { return sprinting; }
        @Override public void setSprinting(boolean s) { this.sprinting = s; }
        @Override public boolean isSneaking() { return sneaking; }
        @Override public void setSneakingKeyHeld(boolean h) { this.sneaking = h; }
        @Override public float getAttackCooldownProgress() { return attackCooldown; }
        @Override public int getPingMs() { return 24; }
        @Override public float getHealth() { return 20.0f; }
        @Override public float getMaxHealth() { return 20.0f; }
        @Override public List<ItemInfo> getArmorItems() { return Collections.singletonList(new ItemInfo("minecraft:diamond_helmet", "Diamond Helmet", 1, 20, 363)); }
        @Override public ItemInfo getMainHandItem() { return new ItemInfo("minecraft:diamond_sword", "Diamond Sword", 1, 100, 1561); }
        @Override public ItemInfo getOffHandItem() { return ItemInfo.EMPTY; }
        @Override public List<ItemInfo> getInventoryItems() { return Collections.singletonList(new ItemInfo("minecraft:arrow", "Arrow", 32, 0, 0)); }
        @Override public int getSelectedHotbarSlot() { return 0; }
        @Override public List<StatusEffectInfo> getActiveEffects() { return Collections.singletonList(new StatusEffectInfo("speed", "Speed", 1, 600, true, 0x33EBFF)); }
        @Override public String getBiomeName() { return "Cherry Grove"; }
        @Override public String getBiomePrecipitation() { return "Rain"; }
        @Override public float getBiomeTemperature() { return 0.5f; }
        @Override public int getBlockLight() { return 12; }
        @Override public int getSkyLight() { return 15; }
        @Override public long getDayTime() { return 6000L; }
        @Override public String getDifficultyName() { return "Normal"; }
        @Override public int getLoadedChunksCount() { return 289; }
        @Override public boolean isRaining() { return false; }
        @Override public boolean isThundering() { return false; }
        @Override public int getFps() { return 240; }
        @Override public double getGamma() { return gamma; }
        @Override public void setGamma(double g) { this.gamma = g; }
        @Override public double getBaseFov() { return fov; }
        @Override public void setBaseFov(double f) { this.fov = f; }
        @Override public boolean isViewBobbing() { return bobbing; }
        @Override public void setViewBobbing(boolean b) { this.bobbing = b; }
        @Override public double getMouseSensitivity() { return sensitivity; }
        @Override public void setMouseSensitivity(double s) { this.sensitivity = s; }
        @Override public int getPerspective() { return 0; }
        @Override public void setPerspective(int p) {}
        @Override public boolean isChunkBordersVisible() { return false; }
        @Override public boolean toggleChunkBorders() { return true; }
        @Override public List<String> getEnabledResourcePacks() { return Collections.singletonList("vanilla"); }
        @Override public List<String> getAvailableResourcePacks() { return Collections.singletonList("vanilla"); }
        @Override public void setClipboard(String text) { this.clipboard = text; }
        @Override public String getClipboard() { return clipboard; }
        @Override public int getScaledWidth() { return scaledW; }
        @Override public int getScaledHeight() { return scaledH; }
        @Override public double getGuiScale() { return 2.0; }
        @Override public boolean isClickGuiOpen() { return clickGuiOpen; }
        @Override public boolean isHudEditorOpen() { return hudEditorOpen; }
        @Override public void openClickGui() { clickGuiOpen = true; hudEditorOpen = false; }
        @Override public void openHudEditor() { hudEditorOpen = true; clickGuiOpen = false; }
        @Override public void openCustomMainMenu() {}
        @Override public void openVanillaSingleplayer() {}
        @Override public void openVanillaMultiplayer() {}
        @Override public void openVanillaOptions() {}
        @Override public void openVanillaResourcePacks() {}
        @Override public void closeScreen() { clickGuiOpen = false; hudEditorOpen = false; }
        @Override public void quitGame() {}
        @Override public boolean isKeyDown(int glfwKey) { return glfwKey == KeyCodes.KEY_W; }
        @Override public boolean isMouseButtonDown(int button) { return false; }
        @Override public String getKeyName(int keyCode) { return KeyCodes.formatKey(keyCode); }
        @Override public Map<String, Integer> getVanillaKeyBindings() {
            Map<String, Integer> m = new HashMap<String, Integer>();
            m.put("Jump", Integer.valueOf(KeyCodes.KEY_SPACE));
            return m;
        }
        @Override public void fillRect(float x, float y, float width, float height, int argb) { drawCalls++; }
        @Override public void fillGradientVertical(float x, float y, float width, float height, int topArgb, int bottomArgb) { drawCalls++; }
        @Override public void drawText(String text, float x, float y, int argb, boolean shadow, float scale) { drawCalls++; }
        @Override public float getTextWidth(String text, float scale) { return text == null ? 0f : text.length() * 5.5f * scale; }
        @Override public float getFontHeight(float scale) { return 9.0f * scale; }
        @Override public void pushScissor(int x, int y, int width, int height) {}
        @Override public void popScissor() {}
        @Override public void pushTransform(float translateX, float translateY, float scale) {}
        @Override public void popTransform() {}
        @Override public List<TargetEntityInfo> getNearbyTargets(double range, boolean p, boolean h, boolean a, boolean inv) {
            return mockTargets;
        }
        @Override public List<TargetEntityInfo> getEspEntities(double range, boolean p, boolean h, boolean a, boolean items, boolean inv) {
            return mockTargets;
        }
        @Override public float[] projectWorldToScreen(double wx, double wy, double wz) {
            return new float[] { 480.0f + (float) (wx - 128.5) * 32.0f, 270.0f - (float) (wy - 64.8) * 32.0f, 3.5f };
        }
        @Override public boolean attackEntityById(int entityId, boolean swingHand) {
            lastAttackedEntityId = entityId;
            return true;
        }
    }

    @Test
    public void registersAll96RealModulesKillAuraEspAndTargetHudWork(@TempDir Path tempDir) {
        MockPlatform platform = new MockPlatform(tempDir);
        AxsesCore core = new AxsesCore(tempDir, platform, platform);

        assertEquals(96, core.getModuleManager().getAll().size(), "Must register all 96 modules & GUI features");
        assertEquals(8, core.getThemeManager().getAllThemes().size(), "Must register all 8 built-in themes");

        // Verify Russian search finds KillAura, ESP & Target HUD
        assertFalse(core.getModuleManager().query(null, false, "киллаура").isEmpty(), "Russian search must find KillAura");
        assertFalse(core.getModuleManager().query(null, false, "esp").isEmpty(), "Search must find Entity ESP");
        assertFalse(core.getModuleManager().query(null, false, "подсветка").isEmpty(), "Russian search must find ESP Подсветка");
        assertFalse(core.getModuleManager().query(null, false, "таргет").isEmpty(), "Russian search must find Target HUD");

        // Verify Russian setting & option localization
        assertEquals("Профиль обхода", core.getLocalizationManager().settingName("server_profile", "Server Profile"));
        assertEquals("Полоска здоровья", core.getLocalizationManager().settingName("health_bar", "Health Bar"));

        // Verify KillAura locks onto nearby hostile, rotates with GCD quantization & feeds Target HUD
        platform.mockTargets = Collections.singletonList(
                new TargetEntityInfo(42, "Zombie", false, true, false, 14.0f, 20.0f, 0.0f, 3.0f, 4, 2, 130.0, 64.0, -256.0, 65.6)
        );
        Module ka = core.getModuleManager().getById("kill_aura");
        assertNotNull(ka);
        ka.setEnabled(true, platform);
        core.onClientTick();
        assertEquals(42, platform.lastAttackedEntityId, "KillAura must attack valid hostile in range");
        assertNotNull(core.getRuntimeState().getActiveTarget(3000L), "KillAura must populate active Target HUD target");

        // Verify GCD step & Ray-AABB math + eating pause
        float gcd = CombatTrainingModulesRegistry.computeMouseGcd(0.5);
        assertEquals(0.15f, gcd, 0.001f);
        platform.yaw = -90.0f;
        platform.pitch = 0.0f;
        assertTrue(CombatTrainingModulesRegistry.doesLookIntersectBox(platform, platform.mockTargets.get(0), 4.0, 0.12));

        platform.lastAttackedEntityId = -1;
        platform.usingItem = true;
        core.onClientTick();
        assertEquals(-1, platform.lastAttackedEntityId, "KillAura must pause while eating/shielding");
        platform.usingItem = false;

        // Enable Entity ESP, Item ESP, and Tracers and verify real draw calls
        core.getModuleManager().getById("entity_esp").setEnabled(true, platform);
        core.getModuleManager().getById("tracers").setEnabled(true, platform);
        int beforeEspDraws = platform.drawCalls;
        core.onHudRender(platform);
        assertTrue(platform.drawCalls > beforeEspDraws + 15, "Entity ESP & Tracers must project and draw boxes/bars/lines");

        // Verify ClickGUI Settings Language Banner + smooth mouse scrolling without beginFrame() freeze
        core.getClickGuiController().onOpen();
        core.getClickGuiController().setSelectedCategory(ModuleCategory.RENDER);
        core.getClickGuiController().render(platform, platform, 300, 200, 0.5f);
        assertTrue(core.getClickGuiController().mouseScrolled(-2.0));
        core.getClickGuiController().setSelectedCategory(ModuleCategory.SETTINGS);
        core.getClickGuiController().render(platform, platform, 300, 200, 0.5f);
        core.getHudEditorController().render(platform, platform, 300, 200, 0.5f);
        core.getMainMenuController().render(platform, platform, 300, 200, 0.5f);
        core.getConfigManager().saveActiveAsync();
        core.shutdown();
        assertTrue(platform.drawCalls > 150, "UI controllers must emit real render primitives");
    }

    @Test
    public void rightShiftAndEscapeControlClickGuiAndRebindingWorks(@TempDir Path tempDir) {
        MockPlatform platform = new MockPlatform(tempDir);
        AxsesCore core = new AxsesCore(tempDir, platform, platform);

        assertFalse(platform.isClickGuiOpen());
        assertTrue(core.onInGameKeyPress(KeyCodes.KEY_RIGHT_SHIFT), "Right Shift must open ClickGUI");
        assertTrue(platform.isClickGuiOpen());

        // Right Shift closes ClickGUI
        assertTrue(core.getClickGuiController().keyPressed(platform, KeyCodes.KEY_RIGHT_SHIFT));
        assertFalse(platform.isClickGuiOpen());

        // Re-open and close via Escape
        assertTrue(core.onInGameKeyPress(KeyCodes.KEY_RIGHT_SHIFT));
        assertTrue(platform.isClickGuiOpen());
        assertTrue(core.getClickGuiController().keyPressed(platform, KeyCodes.KEY_ESCAPE));
        assertFalse(platform.isClickGuiOpen());

        // Rebind Open ClickGUI key to P and verify
        core.getKeybindManager().getOpenGuiBind().assign(KeyCodes.KEY_P);
        assertFalse(core.onInGameKeyPress(KeyCodes.KEY_RIGHT_SHIFT));
        assertTrue(core.onInGameKeyPress(KeyCodes.KEY_P));
        assertTrue(platform.isClickGuiOpen());
    }

    @Test
    public void configManagerPersistsAndRecoversFromCorruption(@TempDir Path tempDir) throws Exception {
        MockPlatform platform = new MockPlatform(tempDir);
        AxsesCore core = new AxsesCore(tempDir, platform, platform);

        Module fb = core.getModuleManager().getById("fullbright");
        assertNotNull(fb);
        fb.setEnabled(true, platform);
        fb.setFavorite(true);
        core.getThemeManager().setActiveTheme("axses-cyber");
        assertTrue(core.getConfigManager().save("competitive"));

        // Corrupt the saved JSON intentionally
        Path cfgFile = tempDir.resolve("axsesclient").resolve("configs").resolve("competitive.json");
        assertTrue(Files.exists(cfgFile));
        // Save once more so rolling .lastgood.bak exists, then corrupt active file
        core.getConfigManager().save("competitive");
        Files.write(cfgFile, "{corrupted_json:::!!!".getBytes(StandardCharsets.UTF_8));

        // Loading corrupted config must NOT throw, must quarantine broken file, and restore rolling backup!
        assertDoesNotThrow(() -> core.getConfigManager().load("competitive", platform));
        assertNotNull(core.getConfigManager().getLastCorruptBackupName());
        assertEquals("axses-cyber", core.getThemeManager().getActiveThemeId());
        assertTrue(fb.isEnabled());
    }

    @Test
    public void settingsValidationAndMultiplayerFairPlayRestrictionsWork(@TempDir Path tempDir) {
        MockPlatform platform = new MockPlatform(tempDir);
        AxsesCore core = new AxsesCore(tempDir, platform, platform);

        NumberSetting num = new NumberSetting("test_num", "Num", "Desc", 5.0, 1.0, 10.0, 0.5);
        num.setValue(Double.valueOf(999.0));
        assertEquals(10.0, num.getDouble(), 1e-5);
        num.setValue(Double.valueOf(Double.NaN));
        assertEquals(5.0, num.getDouble(), 1e-5);

        ColorSetting col = new ColorSetting("test_col", "Col", "Desc", 0xFF9060FF);
        assertTrue(col.setFromHex("#00D9FF"));
        assertEquals(0, col.getRed());
        assertEquals(217, col.getGreen());
        assertEquals(255, col.getBlue());
        assertFalse(col.setFromHex("not-a-color"));

        MultiSelectSetting ms = new MultiSelectSetting("ms", "MS", "Desc",
                MultiSelectSetting.listOf("A", "B", "C"), "A");
        assertTrue(ms.isSelected("A"));
        ms.toggleOption("B");
        assertTrue(ms.isSelected("B"));

        // Singleplayer-only Training Target must disable automatically on external multiplayer servers
        Module target = core.getModuleManager().getById("training_target");
        assertTrue(target.setEnabled(true, platform));
        platform.singleplayer = false;
        core.onClientTick();
        assertFalse(target.isEnabled(), "Training Target must auto-disable on multiplayer servers");
        assertNotNull(target.getIncompatibilityReason(platform));

        CompatibilityEngine.CompatibilityReport rep = core.getCompatibilityEngine().evaluate(
                platform, platform, core.getModuleManager(), core.getKeybindManager()
        );
        assertTrue(rep.isMinecraftSupported());
        assertTrue(rep.isJavaCompatible());
        assertFalse(rep.getRestrictedModules().isEmpty());

        // Color blindness filter produces valid opaque ARGB
        core.getThemeManager().setColorBlindMode(ColorBlindMode.DEUTERANOPIA);
        int filtered = core.getThemeManager().filterColor(0xFF9060FF);
        assertEquals(0xFF, (filtered >>> 24) & 0xFF);

        // Theme import validation rejects invalid JSON
        assertThrows(Exception.class, () -> core.getThemeManager().importThemeFromJsonString("{bad}", false));
        AxsesTheme custom = core.getThemeManager().createCustomTheme("My Neon", "axses-neon");
        assertNotNull(custom);
    }

    @Test
    public void hudEditorGroupingSnappingAndScalingWork(@TempDir Path tempDir) {
        MockPlatform platform = new MockPlatform(tempDir);
        AxsesCore core = new AxsesCore(tempDir, platform, platform);

        HudElement fps = core.getHudManager().getById("fps_counter");
        HudElement cps = core.getHudManager().getById("cps_counter");
        assertNotNull(fps);
        assertNotNull(cps);

        fps.setGroupId("A");
        cps.setGroupId("A");
        float initialCpsY = cps.getPixelY(960);
        core.getHudManager().moveElementWithSnapping(fps, 120f, 100f, 960f, 540f);
        assertNotEquals(initialCpsY, cps.getPixelY(960), "Grouped HUD sibling must move synchronously");

        // Resolution scaling keeps element inside bounds
        assertTrue(fps.getPixelX(640) >= 0f && fps.getPixelX(640) <= 640f);
        assertTrue(fps.getPixelX(1920) >= 0f && fps.getPixelX(1920) <= 1920f);
    }
}
