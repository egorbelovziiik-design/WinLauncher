package nexus.adapter.v1214.mixin;

import net.minecraft.client.gui.screens.Screen;
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
@Mixin(net.minecraft.client.gui.screens.Screen.class)
public final class ScreenMouseMixin {

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true)
    private void nexus$hudClick(double mouseX, double mouseY, int button,
                                CallbackInfoReturnable<Boolean> info) {
        if (nexus$chatHudMouse(0, mouseX, mouseY, button)) {
            info.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased(DDI)Z", at = @At("HEAD"), cancellable = true)
    private void nexus$hudRelease(double mouseX, double mouseY, int button,
                                  CallbackInfoReturnable<Boolean> info) {
        if (nexus$chatHudMouse(2, mouseX, mouseY, button)) {
            info.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged(DDIDD)Z", at = @At("HEAD"), cancellable = true)
    private void nexus$hudDrag(double mouseX, double mouseY, int button, double dragX, double dragY,
                               CallbackInfoReturnable<Boolean> info) {
        if (nexus$chatHudMouse(1, mouseX, mouseY, button)) {
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
