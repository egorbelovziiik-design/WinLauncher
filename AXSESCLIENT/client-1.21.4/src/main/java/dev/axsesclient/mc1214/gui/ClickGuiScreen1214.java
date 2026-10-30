package dev.axsesclient.mc1214.gui;

import dev.axsesclient.common.AxsesCore;
import dev.axsesclient.mc1214.platform.Mc1214RenderAdapter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ClickGuiScreen1214 extends Screen {
    public ClickGuiScreen1214() {
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
        // AXSES NEO-DARK renders its own custom backdrop; avoid 1.21.4 double-blur crash
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null) {
            core.getClickGuiController().render(new Mc1214RenderAdapter(graphics), core.getMinecraftAdapter(), mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null && core.getClickGuiController().mouseClicked(core.getMinecraftAdapter(), mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null && core.getClickGuiController().mouseDragged(core.getMinecraftAdapter(), mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null) {
            core.getClickGuiController().mouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null && core.getClickGuiController().keyPressed(core.getMinecraftAdapter(), keyCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null && core.getClickGuiController().charTyped(codePoint)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
