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

    Nexus1165Screens(Nexus1165Client client) {
        this.client = client;
    }

    private MinecraftClient handle() {
        return client.handle();
    }

    @Override
    public void open(NexusScreen screen) {
        NexusScreenBridge bridge = new NexusScreenBridge(screen);
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
            handle().openScreen(null);
            restoreMouseToGame();
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
        while (!stack.isEmpty()) {
            stack.remove(stack.size() - 1).onClose();
        }
        handle().openScreen(null);
        restoreMouseToGame();
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
            handle().openScreen(handle().player.playerScreenHandler.createScreen(
                    handle().player.inventory, handle().player));
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
        if (handle().currentScreen instanceof net.minecraft.client.gui.screen.ingame.EnchantingScreen) {
            ((net.minecraft.client.gui.screen.ingame.EnchantingScreen) handle().currentScreen)
                    .mouseClicked(120.0, 40.0 + index * 19.0, 0);
        }
    }
}
