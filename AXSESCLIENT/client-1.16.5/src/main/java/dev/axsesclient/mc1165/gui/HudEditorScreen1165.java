package dev.axsesclient.mc1165.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.axsesclient.common.AxsesCore;
import dev.axsesclient.mc1165.platform.Mc1165RenderAdapter;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;

public final class HudEditorScreen1165 extends Screen {
    public HudEditorScreen1165() {
        super(new TextComponent("AXSES HUD Editor"));
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null) {
            core.getHudEditorController().render(new Mc1165RenderAdapter(poseStack), core.getMinecraftAdapter(), mouseX, mouseY, partialTicks);
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
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null && core.getHudEditorController().mouseScrolled(delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
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
