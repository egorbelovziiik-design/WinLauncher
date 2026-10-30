package dev.survivortoolkit.client;

import dev.survivortoolkit.client.gui.ToolkitScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;

/** Opt-in CI-only screen smoke test. It does not create worlds or connect to servers. */
final class SmokeTest {
    private static int stage, wait;
    private SmokeTest() {}
    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (stage == 99) return;
            if (stage == 0 && (!(client.currentScreen instanceof TitleScreen) || client.getOverlay() != null)) return;
            if (++wait % 4 != 0) return; // allow several rendered frames between transitions
            try { runStage(client); }
            catch (Throwable e) {
                SurvivorToolkitClient.LOGGER.error("SURVIVOR_SMOKE_FAIL", e);
                finish(client, "FAIL: " + e);
            }
        });
    }
    private static void runStage(MinecraftClient client) {
        switch (stage++) {
            case 0 -> client.setScreen(new ToolkitScreen());
            case 1 -> {
                ToolkitScreen screen = (ToolkitScreen) client.currentScreen;
                ClickableWidget auction = (ClickableWidget) screen.children().get(2);
                Click click = new Click(auction.getX() + 3, auction.getY() + 3, new MouseInput(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
                require(screen.mouseClicked(click, false), "left click on tab");
                screen.mouseReleased(click);
                for (int i = 0; i < 12 && screen.children().stream().noneMatch(x -> x instanceof TextFieldWidget); i++)
                    screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0, -1);
                TextFieldWidget field = (TextFieldWidget) screen.children().stream().filter(x -> x instanceof TextFieldWidget).findFirst().orElseThrow();
                Click focus = new Click(field.getX() + 4, field.getY() + 4, new MouseInput(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
                screen.mouseClicked(focus, false); screen.mouseReleased(focus);
                screen.charTyped(new CharInput('x', 0));
                require(field.getText().contains("x"), "text field focus and typing");
                for (int button : new int[]{GLFW.GLFW_MOUSE_BUTTON_RIGHT, GLFW.GLFW_MOUSE_BUTTON_MIDDLE}) {
                    Click other = new Click(2, 2, new MouseInput(button, 0));
                    screen.mouseClicked(other, false); screen.mouseReleased(other);
                    require(client.currentScreen == screen, "non-left click changed screens");
                }
            }
            case 2 -> {
                ToolkitScreen screen = (ToolkitScreen) client.currentScreen;
                require(screen.keyPressed(new KeyInput(GLFW.GLFW_KEY_RIGHT_SHIFT, 0, 0)), "Right Shift was not handled");
                require(!(client.currentScreen instanceof ToolkitScreen), "Right Shift failed to close");
                client.setScreen(new ToolkitScreen());
            }
            case 3 -> {
                ToolkitScreen screen = (ToolkitScreen) client.currentScreen;
                require(screen.keyPressed(new KeyInput(GLFW.GLFW_KEY_ESCAPE, 0, 0)), "Escape was not handled");
                require(!(client.currentScreen instanceof ToolkitScreen), "Escape failed to close");
                SurvivorToolkitClient.LOGGER.info("SURVIVOR_SMOKE_PASS: client initialized, GUI rendered, tabs, mouse, text and close keys");
                finish(client, "PASS: client initialized; GUI rendered; tab, mouse, text, Right Shift and Escape callbacks passed");
            }
            default -> throw new IllegalStateException("unexpected smoke stage");
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static void finish(MinecraftClient client, String result) {
        stage = 99;
        try { Files.writeString(Path.of("survivor-smoke-result.txt"), result + "\n"); }
        catch (Exception e) { SurvivorToolkitClient.LOGGER.error("Could not write smoke result", e); }
        client.scheduleStop();
    }
}
