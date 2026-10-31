package dev.funtime.auctionhelper.mc1165;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.funtime.auctionhelper.HelperCore;
import dev.funtime.auctionhelper.ui.HelperUi;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;

/** Premium dark GUI screen for Minecraft 1.16.5. */
public final class HelperScreen1165 extends Screen {
    private long lastFrameNanos = System.nanoTime();

    public HelperScreen1165() {
        super(new TextComponent("FunTime Auction Helper"));
    }

    @Override
    protected void init() {
        super.init();
        HelperCore core = HelperCore.getInstance();
        if (core != null) {
            core.getUi().onUiOpened();
        }
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        long now = System.nanoTime();
        float delta = Math.min(0.1f, (now - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = now;
        HelperCore core = HelperCore.getInstance();
        if (core == null) {
            return;
        }
        HelperUi ui = core.getUi();
        try {
            ui.render(new Canvas1165(poseStack), mouseX, mouseY, delta);
        } catch (RuntimeException error) {
            core.handleError("gui render", error);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        HelperCore core = HelperCore.getInstance();
        if (core != null && core.getUi().mouseClicked((int) mouseX, (int) mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        HelperCore core = HelperCore.getInstance();
        if (core != null && core.getUi().mouseDragged((int) mouseX, (int) mouseY, button)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        HelperCore core = HelperCore.getInstance();
        if (core != null) {
            core.getUi().mouseReleased((int) mouseX, (int) mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        HelperCore core = HelperCore.getInstance();
        if (core != null && core.getUi().mouseScrolled((float) delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        HelperCore core = HelperCore.getInstance();
        if (core != null && core.getUi().keyPressed(keyCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        HelperCore core = HelperCore.getInstance();
        if (core != null && core.getUi().charTyped(codePoint)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
