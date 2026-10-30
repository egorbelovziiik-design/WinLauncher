package dev.axsesclient.mc1214.gui;

import dev.axsesclient.common.AxsesCore;
import dev.axsesclient.mc1214.platform.Mc1214RenderAdapter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class HudEditorScreen1214 extends Screen {
    public HudEditorScreen1214() {
        super(Component.literal("AXSES HUD Editor"));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null) {
            core.getHudEditorController().render(new Mc1214RenderAdapter(graphics), core.getMinecraftAdapter(), mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null && core.getHudEditorController().mouseClicked(core.getMinecraftAdapter(), mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null && core.getHudEditorController().mouseDragged(core.getMinecraftAdapter(), mouseX, mouseY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null) {
            core.getHudEditorController().mouseReleased();
        }
        return super.mouseReleased(mouseX, mouseY, button);
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null && core.getHudEditorController().keyPressed(core.getMinecraftAdapter(), keyCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
