package nexus.adapter.v12111;

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

/** {@link IScreenHost} implementation for Minecraft 1.21.11. */
public final class Nexus12111Screens implements IScreenHost {

    private final Nexus12111Client client;
    private final List<NexusScreenBridge> stack = new ArrayList<NexusScreenBridge>();

    Nexus12111Screens(Nexus12111Client client) {
        this.client = client;
    }

    private Minecraft handle() {
        return client.handle();
    }

    @Override
    public void open(NexusScreen screen) {
        NexusScreenBridge bridge = new NexusScreenBridge(screen);
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
            handle().setScreen(null);
            restoreMouseToGame();
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
        while (!stack.isEmpty()) {
            stack.remove(stack.size() - 1).onClose();
        }
        handle().setScreen(null);
        restoreMouseToGame();
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
                for (int i = 0; i < lines.length && i < 4; i++) {
                    entity.setMessage(i, Component.literal(lines[i]));
                    entity.setMessage(i + 4, Component.literal(lines[i]));
                }
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
}
