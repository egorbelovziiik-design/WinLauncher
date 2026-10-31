package dev.funtime.auctionhelper.mc1165;

import dev.funtime.auctionhelper.model.ScreenSnapshot;
import dev.funtime.auctionhelper.model.SlotSnapshot;
import dev.funtime.auctionhelper.platform.MinecraftAdapter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Minecraft 1.16.5 implementation of the platform bridge. */
public final class Platform1165 implements MinecraftAdapter {
    private final Minecraft mc = Minecraft.getInstance();

    @Override
    public String getMinecraftVersion() {
        return "1.16.5";
    }

    @Override
    public String getJavaVersion() {
        return System.getProperty("java.version", "?");
    }

    @Override
    public String getLoaderVersion() {
        return FabricLoader.getInstance().getModContainer("fabricloader")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
    }

    @Override
    public Path getGameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public boolean isInWorld() {
        return mc.player != null && mc.level != null;
    }

    @Override
    public boolean isSingleplayer() {
        return mc.hasSingleplayerServer();
    }

    @Override
    public String getServerName() {
        if (mc.getCurrentServer() != null) {
            return mc.getCurrentServer().ip;
        }
        return isSingleplayer() ? "singleplayer" : "unknown";
    }

    @Override
    public String getPlayerName() {
        return mc.player == null ? "" : mc.player.getGameProfile().getName();
    }

    @Override
    public String getCurrentScreenTitle() {
        Screen screen = mc.screen;
        return screen == null ? "" : screen.getTitle().getString();
    }

    @Override
    public String getCurrentScreenClass() {
        Screen screen = mc.screen;
        return screen == null ? "" : screen.getClass().getName();
    }

    @Override
    public boolean isHelperScreenOpen() {
        return mc.screen instanceof HelperScreen1165;
    }

    @Override
    public void openHelperScreen() {
        mc.setScreen(new HelperScreen1165());
    }

    @Override
    public void closeScreen() {
        mc.setScreen(null);
    }

    @Override
    public ScreenSnapshot captureScreen() {
        Screen screen = mc.screen;
        if (!(screen instanceof AbstractContainerScreen)) {
            return null;
        }
        AbstractContainerScreen<?> container = (AbstractContainerScreen<?>) screen;
        List<SlotSnapshot> slots = new ArrayList<SlotSnapshot>();
        List<Slot> menuSlots = container.getMenu().slots;
        for (int i = 0; i < menuSlots.size(); i++) {
            Slot slot = menuSlots.get(i);
            ItemStack stack = slot.getItem();
            if (stack == null || stack.isEmpty()) {
                slots.add(new SlotSnapshot(i, "minecraft:air", "", 0, Collections.<String>emptyList()));
                continue;
            }
            String itemId = Registry.ITEM.getKey(stack.getItem()).toString();
            // Reflective: the tooltip signature differs between Minecraft versions.
            List<String> lore = dev.funtime.auctionhelper.util.TooltipReader.readLines(stack, mc.player, mc.level);
            slots.add(new SlotSnapshot(i, itemId, stack.getHoverName().getString(), stack.getCount(), lore));
        }
        return new ScreenSnapshot(container.getTitle().getString(), screen.getClass().getName(), slots,
                System.currentTimeMillis(), getServerName(), true);
    }

    @Override
    public List<SlotSnapshot> readInventory() {
        List<SlotSnapshot> slots = new ArrayList<SlotSnapshot>();
        if (mc.player == null) {
            return slots;
        }
        // 1.16.5 exposes the player inventory as a public field, not through a getter.
        net.minecraft.world.entity.player.Inventory inventory = mc.player.inventory;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            slots.add(new SlotSnapshot(i, Registry.ITEM.getKey(stack.getItem()).toString(),
                    stack.getHoverName().getString(), stack.getCount(), Collections.<String>emptyList()));
        }
        return slots;
    }

    @Override
    public void sendChatMessage(String message) {
        if (mc.player != null) {
            mc.player.displayClientMessage(new TextComponent("[AuctionHelper] " + message), false);
        }
    }

    @Override
    public void copyToClipboard(String text) {
        if (mc.keyboardHandler != null) {
            mc.keyboardHandler.setClipboard(text);
        }
    }

    @Override
    public boolean isBalanceKnown() {
        return false;
    }

    @Override
    public List<String> describeEnvironment() {
        List<String> info = new ArrayList<String>();
        info.add("minecraft=" + getMinecraftVersion());
        info.add("java=" + getJavaVersion());
        info.add("server=" + getServerName());
        return info;
    }
}
