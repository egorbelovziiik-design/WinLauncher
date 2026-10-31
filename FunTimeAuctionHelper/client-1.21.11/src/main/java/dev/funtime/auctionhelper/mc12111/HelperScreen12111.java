package dev.funtime.auctionhelper.mc12111;

import dev.funtime.auctionhelper.HelperCore;
import dev.funtime.auctionhelper.ui.HelperUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Premium dark GUI screen for Minecraft 1.21.11 (new input event API). */
public final class HelperScreen12111 extends Screen {
    private long lastFrameNanos = System.nanoTime();

    public HelperScreen12111() {
        super(Component.literal("FunTime Auction Helper"));
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
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Custom backdrop: skipping the vanilla blur avoids double darkening.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        float delta = Math.min(0.1f, (now - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = now;
        HelperCore core = HelperCore.getInstance();
        if (core == null) {
            return;
        }
        HelperUi ui = core.getUi();
        try {
            ui.render(new Canvas12111(graphics), mouseX, mouseY, delta);
        } catch (RuntimeException error) {
            core.handleError("gui render", error);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        HelperCore core = HelperCore.getInstance();
        if (core != null && core.getUi().mouseClicked((int) event.x(), (int) event.y(), event.button())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        HelperCore core = HelperCore.getInstance();
        if (core != null && core.getUi().mouseDragged((int) event.x(), (int) event.y(), event.button())) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        HelperCore core = HelperCore.getInstance();
        if (core != null) {
            core.getUi().mouseReleased((int) event.x(), (int) event.y(), event.button());
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        HelperCore core = HelperCore.getInstance();
        if (core != null && core.getUi().mouseScrolled((float) scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        HelperCore core = HelperCore.getInstance();
        if (core != null && core.getUi().keyPressed(event.key())) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        HelperCore core = HelperCore.getInstance();
        if (core != null && Character.isBmpCodePoint(event.codepoint())
                && core.getUi().charTyped((char) event.codepoint())) {
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
