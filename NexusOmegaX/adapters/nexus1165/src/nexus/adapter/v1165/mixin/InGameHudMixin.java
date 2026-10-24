package nexus.adapter.v1165.mixin;

import nexus.adapter.v1165.Nexus1165Hooks;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the Nexus HUD, the ClickGUI and every 2D module on top of the vanilla HUD. */
@Mixin(InGameHud.class)
public final class InGameHudMixin {

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;F)V", at = @At("TAIL"))
    private void nexus$render2D(MatrixStack matrices, float tickDelta, CallbackInfo info) {
        try {
            Nexus1165Hooks.Hooks.HOOKS.onRender2D(matrices, tickDelta * 50.0f,
                net.minecraft.client.MinecraftClient.getInstance().getWindow().getScaledWidth(),
                net.minecraft.client.MinecraftClient.getInstance().getWindow().getScaledHeight());
        } catch (Throwable throwable) {
            nexus.util.Log.error("Nexus render hook failed", throwable);
        }
    }
}
