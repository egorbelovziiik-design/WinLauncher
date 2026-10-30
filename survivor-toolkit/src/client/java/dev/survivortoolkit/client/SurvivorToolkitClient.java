package dev.survivortoolkit.client;

import dev.survivortoolkit.client.feature.Freecam;
import dev.survivortoolkit.client.feature.ToolkitHud;
import dev.survivortoolkit.client.feature.WardenAlert;
import dev.survivortoolkit.client.gui.ToolkitScreen;
import dev.survivortoolkit.config.ConfigStore;
import dev.survivortoolkit.config.ToolkitConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SurvivorToolkitClient implements ClientModInitializer {
    public static final String ID = "survivor_toolkit";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);
    public static ConfigStore STORE;
    public static KeyBinding MENU_KEY;
    public static KeyBinding FREECAM_KEY;
    public static final Freecam FREECAM = new Freecam();
    public static final WardenAlert WARDEN = new WardenAlert();

    public static ToolkitConfig config() { return STORE.get(); }
    public static Identifier id(String path) { return Identifier.of(ID, path); }

    @Override public void onInitializeClient() {
        STORE = new ConfigStore(FabricLoader.getInstance().getConfigDir().resolve("survivor-toolkit.json"));
        STORE.load();
        KeyBinding.Category category = KeyBinding.Category.create(id("controls"));
        MENU_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.survivor_toolkit.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
        FREECAM_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.survivor_toolkit.freecam", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F6, category));
        ClientTickEvents.END_CLIENT_TICK.register(SurvivorToolkitClient::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { FREECAM.stop(client); STORE.save(); });
        HudElementRegistry.addLast(id("hud"), ToolkitHud::render);
        LOGGER.info("Survivor Toolkit initialized (Minecraft 1.21.11)");
        if (Boolean.getBoolean("survivortoolkit.smoke")) SmokeTest.register();
    }

    private static void tick(MinecraftClient client) {
        boolean menuPressed = false;
        while (MENU_KEY.wasPressed()) menuPressed = true;
        boolean freecamPressed = false;
        while (FREECAM_KEY.wasPressed()) freecamPressed = true;
        FREECAM.tick(client);
        if (menuPressed && client.currentScreen == null && client.world != null) {
            FREECAM.stop(client);
            client.setScreen(new ToolkitScreen());
        } else if (freecamPressed && client.currentScreen == null) {
            FREECAM.toggle(client);
        }
        WARDEN.tick(client);
    }
}
