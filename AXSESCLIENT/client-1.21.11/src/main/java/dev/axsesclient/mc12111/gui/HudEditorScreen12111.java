package dev.axsesclient.mc12111.gui;

import dev.axsesclient.common.AxsesCore;
import dev.axsesclient.mc12111.platform.Mc12111RenderAdapter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class HudEditorScreen12111 extends Screen {
    public HudEditorScreen12111() {
        super(Component.literal("AXSES HUD Editor"));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null) {
            core.getHudEditorController().render(new Mc12111RenderAdapter(graphics), core.getMinecraftAdapter(), mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null && core.getHudEditorController().mouseClicked(core.getMinecraftAdapter(), event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null && core.getHudEditorController().mouseDragged(core.getMinecraftAdapter(), event.x(), event.y())) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null) {
            core.getHudEditorController().mouseReleased();
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null && core.getHudEditorController().mouseScrolled(scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null && core.getHudEditorController().keyPressed(core.getMinecraftAdapter(), event.key())) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
