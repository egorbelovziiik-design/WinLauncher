package dev.axsesclient.mc1165.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.axsesclient.common.AxsesCore;
import dev.axsesclient.mc1165.platform.Mc1165RenderAdapter;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;

public final class AxsesMainMenuScreen1165 extends Screen {
    public AxsesMainMenuScreen1165() {
        super(new TextComponent("AXSESCLIENT MAIN MENU"));
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null) {
            core.getMainMenuController().render(new Mc1165RenderAdapter(poseStack), core.getMinecraftAdapter(), mouseX, mouseY, partialTicks);
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
