package dev.axsesclient.mc1165;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.axsesclient.common.AxsesCore;
import dev.axsesclient.common.platform.KeyCodes;
import dev.axsesclient.mc1165.gui.AxsesMainMenuScreen1165;
import dev.axsesclient.mc1165.gui.ClickGuiScreen1165;
import dev.axsesclient.mc1165.gui.HudEditorScreen1165;
import dev.axsesclient.mc1165.platform.Mc1165Adapter;
import dev.axsesclient.mc1165.platform.Mc1165InputAdapter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.MixinEnvironment;

public final class AxsesClient1165Mod implements ClientModInitializer {
    private static final boolean SELF_TEST = Boolean.getBoolean("axsesclient.selftest");
    private static int selfTestTick = 0;
    private static boolean selfTestCompleted = false;

    @Override
    public void onInitializeClient() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        new AxsesCore(gameDir, new Mc1165Adapter(), new Mc1165InputAdapter());
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
                if (!(mc.screen instanceof AxsesMainMenuScreen1165)) {
                    mc.setScreen(new AxsesMainMenuScreen1165());
                }
            } else if (selfTestTick == 12) {
                core.onInGameKeyPress(KeyCodes.KEY_RIGHT_SHIFT);
                if (!(mc.screen instanceof ClickGuiScreen1165)) {
                    throw new IllegalStateException("Right Shift did not open ClickGuiScreen1165");
                }
            } else if (selfTestTick == 18) {
                core.getClickGuiController().mouseClicked(core.getMinecraftAdapter(), 365, 16, 0);
                core.getThemeManager().setActiveTheme("axses-purple");
                core.getModuleManager().getById("fullbright").setEnabled(true, core.getMinecraftAdapter());
                core.onClientTick();
                core.getConfigManager().save("selftest_1165");
                core.getClickGuiController().keyPressed(core.getMinecraftAdapter(), KeyCodes.KEY_RIGHT_SHIFT);
                if (mc.screen instanceof ClickGuiScreen1165) {
                    throw new IllegalStateException("Right Shift did not close ClickGuiScreen1165");
                }
            } else if (selfTestTick == 22) {
                core.onInGameKeyPress(KeyCodes.KEY_RIGHT_SHIFT);
                if (!(mc.screen instanceof ClickGuiScreen1165)) {
                    throw new IllegalStateException("Second Right Shift open failed");
                }
            } else if (selfTestTick == 26) {
                core.getClickGuiController().keyPressed(core.getMinecraftAdapter(), KeyCodes.KEY_ESCAPE);
                if (mc.screen instanceof ClickGuiScreen1165) {
                    throw new IllegalStateException("Escape did not close ClickGuiScreen1165");
                }
                mc.setScreen(new HudEditorScreen1165());
            } else if (selfTestTick == 30) {
                if (!(mc.screen instanceof HudEditorScreen1165)) {
                    throw new IllegalStateException("HudEditorScreen1165 failed to open");
                }
                core.getHudManager().applyPreset("Competitive", core.getMinecraftAdapter());
                core.getConfigManager().load("selftest_1165", core.getMinecraftAdapter());

                JsonObject report = new JsonObject();
                report.addProperty("status", "PASS");
                report.addProperty("minecraftVersion", "1.16.5");
                report.addProperty("javaRuntime", System.getProperty("java.version"));
                report.addProperty("modulesRegistered", Integer.valueOf(core.getModuleManager().getAll().size()));
                report.addProperty("themesRegistered", Integer.valueOf(core.getThemeManager().getAllThemes().size()));
                report.addProperty("activeTheme", core.getThemeManager().getActiveThemeId());
                report.addProperty("effectiveGamma", Double.valueOf(mc.options.gamma));
                report.addProperty("mixinAudit", "PASS");
                Path out = core.getClientRootDir().resolve("selftest-1.16.5.json");
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
                Files.write(core.getClientRootDir().resolve("selftest-1.16.5.json"), err.toString().getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {
            }
            mc.stop();
        }
    }
}
