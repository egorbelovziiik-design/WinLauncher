package dev.axsesclient.mc12111.gui;

import dev.axsesclient.common.AxsesCore;
import dev.axsesclient.mc12111.platform.Mc12111RenderAdapter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class ClickGuiScreen12111 extends Screen {
    public ClickGuiScreen12111() {
        super(Component.literal("AXSESCLIENT ClickGUI"));
    }

    @Override
    protected void init() {
        super.init();
        AxsesCore core = AxsesCore.getInstance();
        if (core != null) {
            core.getClickGuiController().onOpen();
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null) {
            core.getClickGuiController().render(new Mc12111RenderAdapter(graphics), core.getMinecraftAdapter(), mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null && core.getClickGuiController().mouseClicked(core.getMinecraftAdapter(), event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null && core.getClickGuiController().mouseDragged(core.getMinecraftAdapter(), event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null) {
            core.getClickGuiController().mouseReleased(event.x(), event.y(), event.button());
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null && core.getClickGuiController().mouseScrolled(scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null && core.getClickGuiController().keyPressed(core.getMinecraftAdapter(), event.key())) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null && Character.isBmpCodePoint(event.codepoint()) && core.getClickGuiController().charTyped((char) event.codepoint())) {
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
