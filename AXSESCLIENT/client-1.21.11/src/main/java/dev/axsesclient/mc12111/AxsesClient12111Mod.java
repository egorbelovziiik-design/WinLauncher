package dev.axsesclient.mc12111;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.axsesclient.common.AxsesCore;
import dev.axsesclient.common.platform.KeyCodes;
import dev.axsesclient.mc12111.gui.AxsesMainMenuScreen12111;
import dev.axsesclient.mc12111.gui.ClickGuiScreen12111;
import dev.axsesclient.mc12111.gui.HudEditorScreen12111;
import dev.axsesclient.mc12111.platform.Mc12111Adapter;
import dev.axsesclient.mc12111.platform.Mc12111InputAdapter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.MixinEnvironment;

public final class AxsesClient12111Mod implements ClientModInitializer {
    private static final boolean SELF_TEST = Boolean.getBoolean("axsesclient.selftest");
    private static int selfTestTick = 0;
    private static boolean selfTestCompleted = false;

    @Override
    public void onInitializeClient() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        new AxsesCore(gameDir, new Mc12111Adapter(), new Mc12111InputAdapter());
    }

    public static void handleTick(Minecraft client) {
        AxsesCore core = AxsesCore.getInstance();
        if (core == null) return;
        core.onClientTick();
        if (SELF_TEST && !selfTestCompleted && client != null && client.screen != null && client.getOverlay() == null) {
            runSelfTestStep(client, core);
        }
    }

    private static void runSelfTestStep(Minecraft mc, AxsesCore core) {
        selfTestTick++;
        try {
            if (selfTestTick == 5) {
                MixinEnvironment.getCurrentEnvironment().audit();
                if (!(mc.screen instanceof AxsesMainMenuScreen12111)) {
                    mc.setScreen(new AxsesMainMenuScreen12111());
                }
            } else if (selfTestTick == 12) {
                core.onInGameKeyPress(KeyCodes.KEY_RIGHT_SHIFT);
                if (!(mc.screen instanceof ClickGuiScreen12111)) {
                    throw new IllegalStateException("Right Shift did not open ClickGuiScreen12111");
                }
            } else if (selfTestTick == 18) {
                ClickGuiScreen12111 gui = (ClickGuiScreen12111) mc.screen;
                if (core.getModuleManager().getById("kill_aura") == null || core.getModuleManager().getById("target_hud") == null) {
                    throw new IllegalStateException("KillAura or TargetHUD missing in 1.21.11");
                }
                core.getThemeManager().setActiveTheme("axses-neon");
                core.getModuleManager().getById("fullbright").setEnabled(true, core.getMinecraftAdapter());
                core.onClientTick();
                double effectiveGamma = mc.options.gamma().get();
                if (effectiveGamma < 10.0) {
                    throw new IllegalStateException("FullBright gamma override failed on 1.21.11: " + effectiveGamma);
                }
                core.getConfigManager().save("selftest_12111");
                gui.keyPressed(new KeyEvent(KeyCodes.KEY_RIGHT_SHIFT, 0, 0));
                if (mc.screen instanceof ClickGuiScreen12111) {
                    throw new IllegalStateException("Right Shift did not close ClickGuiScreen12111");
                }
            } else if (selfTestTick == 22) {
                core.onInGameKeyPress(KeyCodes.KEY_RIGHT_SHIFT);
                if (!(mc.screen instanceof ClickGuiScreen12111)) {
                    throw new IllegalStateException("Second Right Shift open failed");
                }
            } else if (selfTestTick == 26) {
                ClickGuiScreen12111 gui = (ClickGuiScreen12111) mc.screen;
                gui.keyPressed(new KeyEvent(KeyCodes.KEY_ESCAPE, 0, 0));
                if (mc.screen instanceof ClickGuiScreen12111) {
                    throw new IllegalStateException("Escape did not close ClickGuiScreen12111");
                }
                mc.setScreen(new HudEditorScreen12111());
            } else if (selfTestTick == 30) {
                if (!(mc.screen instanceof HudEditorScreen12111)) {
                    throw new IllegalStateException("HudEditorScreen12111 failed to open");
                }
                core.getHudManager().applyPreset("Streamer", core.getMinecraftAdapter());
                core.getConfigManager().load("selftest_12111", core.getMinecraftAdapter());

                JsonObject report = new JsonObject();
                report.addProperty("status", "PASS");
                report.addProperty("minecraftVersion", "1.21.11");
                report.addProperty("javaRuntime", System.getProperty("java.version"));
                report.addProperty("modulesRegistered", core.getModuleManager().getAll().size());
                report.addProperty("themesRegistered", core.getThemeManager().getAllThemes().size());
                report.addProperty("activeTheme", core.getThemeManager().getActiveThemeId());
                report.addProperty("effectiveGamma", mc.options.gamma().get());
                report.addProperty("mixinAudit", "PASS");
                Path out = core.getClientRootDir().resolve("selftest-1.21.11.json");
                Files.write(out, new GsonBuilder().setPrettyPrinting().create().toJson(report).getBytes(StandardCharsets.UTF_8));
                selfTestCompleted = true;
                mc.stop();
            }
        } catch (Throwable t) {
            selfTestCompleted = true;
            try {
                JsonObject err = new JsonObject();
                err.addProperty("status", "FAIL");
                err.addProperty("error", t.getClass().getName() + ": " + t.getMessage());
                Files.write(core.getClientRootDir().resolve("selftest-1.21.11.json"), err.toString().getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {
            }
            mc.stop();
        }
    }
}
