package nexus.adapter.v1165;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import nexus.api.IScreenHost;
import nexus.api.screen.NexusScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link IScreenHost} implementation for Minecraft 1.16.5.
 * <p>
 * 1.16.5 has no screen stack, so the Nexus screen stack is emulated here while
 * the native {@code currentScreen} keeps behaving like a vanilla screen.
 */
public final class Nexus1165Screens implements IScreenHost {

    private final Nexus1165Client client;
    private final List<NexusScreenBridge> stack = new ArrayList<NexusScreenBridge>();
    /** Vanilla screen that was visible before the first Nexus screen opened. */
    private Screen previous;

    Nexus1165Screens(Nexus1165Client client) {
        this.client = client;
    }

    private MinecraftClient handle() {
        return client.handle();
    }

    @Override
    public void open(NexusScreen screen) {
        if (stack.isEmpty()) {
            Screen current = handle().screen;
            previous = current instanceof NexusScreenBridge ? null : current;
        }
        NexusScreenBridge bridge = new NexusScreenBridge(this, screen);
        stack.add(bridge);
        handle().openScreen(bridge);
    }

    @Override
    public void close(NexusScreen screen) {
        for (int i = stack.size() - 1; i >= 0; i--) {
            if (stack.get(i).screen() == screen) {
                stack.remove(i);
                break;
            }
        }
        if (stack.isEmpty()) {
            Screen restore = previous;
            previous = null;
            handle().openScreen(restore);
            if (restore == null) {
                restoreMouseToGame();
            }
        } else {
            handle().openScreen(stack.get(stack.size() - 1));
        }
    }

    @Override
    public void closeTop() {
        if (!stack.isEmpty()) {
            close(stack.get(stack.size() - 1).screen());
        } else if (handle().currentScreen != null) {
            handle().openScreen(null);
        }
    }

    @Override
    public void closeAll() {
        // Defensive path only: the client core already closed every screen via
        // close(NexusScreen). Never call bridge.onClose() from here, that would
        // re-enter the Nexus stack.
        stack.clear();
        Screen restore = previous;
        previous = null;
        handle().openScreen(restore);
        if (restore == null) {
            restoreMouseToGame();
        }
    }

    @Override
    public NexusScreen current() {
        return stack.isEmpty() ? null : stack.get(stack.size() - 1).screen();
    }

    @Override
    public boolean isOpen(NexusScreen screen) {
        for (NexusScreenBridge bridge : stack) {
            if (bridge.screen() == screen) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int stackSize() {
        return stack.size();
    }

    @Override
    public String currentVanillaScreen() {
        Screen screen = handle().currentScreen;
        return screen == null ? null : screen.getClass().getSimpleName().toLowerCase();
    }

    @Override
    public void restoreMouseToGame() {
        client.input().releaseAllKeys();
        client.input().grabMouse();
    }

    @Override
    public void openInventoryScreen() {
        if (handle().player != null) {
            handle().openScreen(new net.minecraft.client.gui.screen.ingame.InventoryScreen(handle().player));
        }
    }

    @Override
    public void fillSignText(String[] lines) {
        Screen current = handle().currentScreen;
        if (!(current instanceof net.minecraft.client.gui.screen.ingame.SignEditScreen) || lines == null) {
            return;
        }
        net.minecraft.client.gui.screen.ingame.SignEditScreen screen =
                (net.minecraft.client.gui.screen.ingame.SignEditScreen) current;
        try {
            java.lang.reflect.Field indexField =
                    net.minecraft.client.gui.screen.ingame.SignEditScreen.class.getDeclaredField("field_2166");
            java.lang.reflect.Field textFieldHolder =
                    net.minecraft.client.gui.screen.ingame.SignEditScreen.class.getDeclaredField("field_2167");
            indexField.setAccessible(true);
            textFieldHolder.setAccessible(true);
            for (int i = 0; i < lines.length && i < 4; i++) {
                indexField.set(screen, i);
                Object textField = textFieldHolder.get(screen);
                textField.getClass().getMethod("method_1882", String.class).invoke(textField, lines[i]);
            }
        } catch (Throwable ignored) {
            // Intermediary names move between loader versions; skip silently.
        }
    }

    @Override
    public void clickEnchantment(int index) {
        if (handle().currentScreen instanceof net.minecraft.client.gui.screen.ingame.EnchantmentScreen) {
            ((net.minecraft.client.gui.screen.ingame.EnchantmentScreen) handle().currentScreen)
                    .mouseClicked(120.0, 40.0 + index * 19.0, 0);
        }
    }

    // ------------------------------------------------------- vanilla menus

    @Override
    public void openSingleplayerMenu() {
        handle().openScreen(new net.minecraft.client.gui.screen.world.SelectWorldScreen(parent()));
    }

    @Override
    public void openMultiplayerMenu() {
        handle().openScreen(new net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen(parent()));
    }

    @Override
    public void openVanillaOptions() {
        handle().openScreen(new net.minecraft.client.gui.screen.option.OptionsScreen(parent(), handle().options));
    }

    /** Parent screen for the vanilla menus: the vanilla screen underneath. */
    private Screen parent() {
        Screen current = handle().screen;
        if (current instanceof NexusScreenBridge) {
            return previous;
        }
        return current;
    }

    @Override
    public boolean isTitleScreen() {
        return handle().screen instanceof net.minecraft.client.gui.screen.TitleScreen;
    }

    @Override
    public boolean isGuiVisible() {
        return handle().screen instanceof NexusScreenBridge;
    }
}
