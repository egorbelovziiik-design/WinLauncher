package nexus.adapter.v1214;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.network.chat.Component;
import nexus.api.IScreenHost;
import nexus.api.screen.NexusScreen;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** {@link IScreenHost} implementation for Minecraft 1.21.4. */
public final class Nexus1214Screens implements IScreenHost {

    private final Nexus1214Client client;
    private final List<NexusScreenBridge> stack = new ArrayList<NexusScreenBridge>();
    /** Vanilla screen that was visible before the first Nexus screen opened. */
    private Screen previous;

    Nexus1214Screens(Nexus1214Client client) {
        this.client = client;
    }

    private Minecraft handle() {
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
        handle().setScreen(bridge);
    }

    @Override
    public void close(NexusScreen screen) {
        for (int i = stack.size() - 1; i >= 0; i--) {
            if (stack.get(i).nexusScreen() == screen) {
                stack.remove(i);
                break;
            }
        }
        if (stack.isEmpty()) {
            Screen restore = previous;
            previous = null;
            handle().setScreen(restore);
            if (restore == null) {
                restoreMouseToGame();
            }
        } else {
            handle().setScreen(stack.get(stack.size() - 1));
        }
    }

    @Override
    public void closeTop() {
        if (!stack.isEmpty()) {
            close(stack.remove(stack.size() - 1).nexusScreen());
        } else if (handle().screen != null) {
            handle().setScreen(null);
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
        handle().setScreen(restore);
        if (restore == null) {
            restoreMouseToGame();
        }
    }

    @Override
    public NexusScreen current() {
        return stack.isEmpty() ? null : stack.get(stack.size() - 1).nexusScreen();
    }

    @Override
    public boolean isOpen(NexusScreen screen) {
        for (NexusScreenBridge bridge : stack) {
            if (bridge.nexusScreen() == screen) {
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
        Screen screen = handle().screen;
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
            handle().setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(handle().player));
        }
    }

    @Override
    public void fillSignText(String[] lines) {
        Screen screen = handle().screen;
        if (!(screen instanceof SignEditScreen)) {
            return;
        }
        try {
            java.lang.reflect.Field field = SignEditScreen.class.getDeclaredField("sign");
            field.setAccessible(true);
            Object sign = field.get(screen);
            if (sign instanceof net.minecraft.world.level.block.entity.SignBlockEntity) {
                net.minecraft.world.level.block.entity.SignBlockEntity entity =
                        (net.minecraft.world.level.block.entity.SignBlockEntity) sign;
                net.minecraft.world.level.block.entity.SignText front = entity.getFrontText();
                net.minecraft.world.level.block.entity.SignText back = entity.getBackText();
                for (int i = 0; i < lines.length && i < 4; i++) {
                    front = front.setMessage(i, Component.literal(lines[i]));
                    back = back.setMessage(i, Component.literal(lines[i]));
                }
                entity.setText(front, true);
                entity.setText(back, false);
            }
        } catch (Throwable ignored) {
            // the sign screen layout changed, nothing we can do generically
        }
    }

    @Override
    public void clickEnchantment(int index) {
        Screen screen = handle().screen;
        if (!(screen instanceof EnchantmentScreen)) {
            return;
        }
        EnchantmentScreen enchanting = (EnchantmentScreen) screen;
        net.minecraft.world.inventory.EnchantmentMenu menu = enchanting.getMenu();
        if (handle().gameMode != null && menu != null) {
            handle().gameMode.handleInventoryButtonClick(menu.containerId, index);
        }
    }

    /** Utility: true when a container screen is open. */
    public boolean containerOpen() {
        return handle().screen instanceof AbstractContainerScreen;
    }

    /** Folder helper used by the client settings. */
    public File gameDirectory() {
        return handle().gameDirectory;
    }

    // ------------------------------------------------------- vanilla menus

    @Override
    public void openSingleplayerMenu() {
        handle().setScreen(new net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(parent()));
    }

    @Override
    public void openMultiplayerMenu() {
        handle().setScreen(new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(parent()));
    }

    @Override
    public void openVanillaOptions() {
        handle().setScreen(new net.minecraft.client.gui.screens.options.OptionsScreen(parent(), handle().options));
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
        return handle().screen instanceof net.minecraft.client.gui.screens.TitleScreen;
    }

    @Override
    public boolean isGuiVisible() {
        return handle().screen instanceof NexusScreenBridge;
    }
}
