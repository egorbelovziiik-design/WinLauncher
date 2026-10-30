package dev.axsesclient.mc1214.gui;

import dev.axsesclient.common.AxsesCore;
import dev.axsesclient.mc1214.platform.Mc1214RenderAdapter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class AxsesMainMenuScreen1214 extends Screen {
    public AxsesMainMenuScreen1214() {
        super(Component.literal("AXSESCLIENT MAIN MENU"));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null) {
            core.getMainMenuController().render(new Mc1214RenderAdapter(graphics), core.getMinecraftAdapter(), mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null && core.getMainMenuController().mouseClicked(core.getMinecraftAdapter(), mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null && core.getMainMenuController().keyPressed(core.getMinecraftAdapter(), keyCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
