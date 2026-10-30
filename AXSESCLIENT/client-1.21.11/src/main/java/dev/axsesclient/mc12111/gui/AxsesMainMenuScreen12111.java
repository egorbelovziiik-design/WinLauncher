package dev.axsesclient.mc12111.gui;

import dev.axsesclient.common.AxsesCore;
import dev.axsesclient.mc12111.platform.Mc12111RenderAdapter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class AxsesMainMenuScreen12111 extends Screen {
    public AxsesMainMenuScreen12111() {
        super(Component.literal("AXSESCLIENT MAIN MENU"));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null) {
            core.getMainMenuController().render(new Mc12111RenderAdapter(graphics), core.getMinecraftAdapter(), mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null && core.getMainMenuController().mouseClicked(core.getMinecraftAdapter(), event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null && core.getMainMenuController().keyPressed(core.getMinecraftAdapter(), event.key())) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
