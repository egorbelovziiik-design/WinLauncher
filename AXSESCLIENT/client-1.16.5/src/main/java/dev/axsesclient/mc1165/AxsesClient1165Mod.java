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
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.MixinEnvironment;

public final class AxsesClient1165Mod implements ClientModInitializer {
    private int selfTestTick = 0;
    private boolean selfTestCompleted = false;

    @Override
    public void onInitializeClient() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        Mc1165Adapter mcAdapter = new Mc1165Adapter();
        Mc1165InputAdapter inputAdapter = new Mc1165InputAdapter();
        final AxsesCore core = new AxsesCore(gameDir, mcAdapter, inputAdapter);

        final boolean selfTest = Boolean.getBoolean("axsesclient.selftest");

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            core.onClientTick();
            if (selfTest && !selfTestCompleted && client != null && client.screen != null) {
                runSelfTestStep(client, core);
            }
        });
    }

    private void runSelfTestStep(Minecraft mc, AxsesCore core) {
        selfTestTick++;
        try {
            if (selfTestTick == 5) {
                MixinEnvironment.getCurrentEnvironment().audit();
                if (!(mc.screen instanceof AxsesMainMenuScreen1165)) {
                    mc.setScreen(new AxsesMainMenuScreen1165());
                }
            } else if (selfTestTick == 12) {
                // Verify Right Shift opens ClickGUI from Main Menu / In-Game
                core.onInGameKeyPress(KeyCodes.KEY_RIGHT_SHIFT);
                if (!(mc.screen instanceof ClickGuiScreen1165)) {
                    throw new IllegalStateException("Right Shift did not open ClickGuiScreen1165");
                }
            } else if (selfTestTick == 18) {
                // Exercise ClickGUI search, category clicks, theme switch, and config save
                core.getClickGuiController().mouseClicked(core.getMinecraftAdapter(), 365, 16, 0);
                core.getThemeManager().setActiveTheme("axses-purple");
                core.getModuleManager().getById("fullbright").setEnabled(true, core.getMinecraftAdapter());
                core.getConfigManager().save("selftest_1165");
                // Close via Right Shift
                core.getClickGuiController().keyPressed(core.getMinecraftAdapter(), KeyCodes.KEY_RIGHT_SHIFT);
                if (mc.screen instanceof ClickGuiScreen1165) {
                    throw new IllegalStateException("Right Shift did not close ClickGuiScreen1165");
                }
            } else if (selfTestTick == 22) {
                // Re-open via Right Shift, then close via Escape
                core.onInGameKeyPress(KeyCodes.KEY_RIGHT_SHIFT);
                if (!(mc.screen instanceof ClickGuiScreen1165)) {
                    throw new IllegalStateException("Second Right Shift open failed");
                }
            } else if (selfTestTick == 26) {
                core.getClickGuiController().keyPressed(core.getMinecraftAdapter(), KeyCodes.KEY_ESCAPE);
                if (mc.screen instanceof ClickGuiScreen1165) {
                    throw new IllegalStateException("Escape did not close ClickGuiScreen1165");
                }
                // Open HUD Editor & test drag/preset
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
                report.addProperty("modulesRegistered", Integer.valueOf(core.getModuleManager().getAll().size()));
                report.addProperty("themesRegistered", Integer.valueOf(core.getThemeManager().getAllThemes().size()));
                report.addProperty("activeTheme", core.getThemeManager().getActiveThemeId());
                report.addProperty("fullbrightPersisted", Boolean.valueOf(core.getModuleManager().getById("fullbright").isEnabled()));
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
