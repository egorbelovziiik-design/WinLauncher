package nexus.adapter.v12111.mixin;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Drag HUD elements with the mouse while the chat is open.
 * <p>
 * Every vanilla screen is hooked, but the handler only reacts while the chat is
 * open and an enabled HUD element is under the cursor, so nothing else changes.
 */
@Mixin(Screen.class)
public final class ScreenMouseMixin {

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            at = @At("HEAD"), cancellable = true)
    private void nexus$hudClick(MouseButtonEvent event, boolean doubleClick,
                                CallbackInfoReturnable<Boolean> info) {
        if (nexus$chatHudMouse(0, event.x(), event.y(), event.button())) {
            info.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z",
            at = @At("HEAD"), cancellable = true)
    private void nexus$hudRelease(MouseButtonEvent event, CallbackInfoReturnable<Boolean> info) {
        if (nexus$chatHudMouse(2, event.x(), event.y(), event.button())) {
            info.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged(Lnet/minecraft/client/input/MouseButtonEvent;DD)Z",
            at = @At("HEAD"), cancellable = true)
    private void nexus$hudDrag(MouseButtonEvent event, double dragX, double dragY,
                               CallbackInfoReturnable<Boolean> info) {
        if (nexus$chatHudMouse(1, event.x(), event.y(), event.button())) {
            info.setReturnValue(true);
        }
    }

    /** 0 = press, 1 = drag, 2 = release. True when Nexus used the event. */
    private static boolean nexus$chatHudMouse(int phase, double mouseX, double mouseY, int button) {
        if (!nexus.core.Nexus.isInitialized()) {
            return false;
        }
        try {
            nexus.core.hud.HudManager hud = nexus.core.Nexus.instance().hud();
            if (phase == 0) {
                return hud.chatMousePressed(mouseX, mouseY, button);
            }
            if (phase == 1) {
                return hud.chatMouseMoved(mouseX, mouseY);
            }
            return hud.chatMouseReleased();
        } catch (Throwable throwable) {
            nexus.util.Log.error("Nexus chat HUD drag failed", throwable);
            return false;
        }
    }
}
