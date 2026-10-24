package nexus.adapter.v12111.mixin;

import nexus.adapter.v12111.Nexus12111Hooks;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the Nexus HUD, the ClickGUI and every 2D module on top of the vanilla HUD. */
@Mixin(Gui.class)
public final class GuiMixin {

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("TAIL"))
    private void nexus$render2D(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo info) {
        try {
            Nexus12111Hooks.Hooks.HOOKS.onRender2D(graphics,
                deltaTracker.getGameTimeDeltaPartialTick(true) * 50.0f,
                graphics.guiWidth(), graphics.guiHeight());
        } catch (Throwable throwable) {
            nexus.util.Log.error("Nexus render hook failed", throwable);
        }
    }
}
