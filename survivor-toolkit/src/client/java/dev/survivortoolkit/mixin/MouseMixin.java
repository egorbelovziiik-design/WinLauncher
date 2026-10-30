package dev.survivortoolkit.mixin;

import dev.survivortoolkit.client.SurvivorToolkitClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Shadow @Final private MinecraftClient client;
    @Shadow private double cursorDeltaX;
    @Shadow private double cursorDeltaY;

    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void survivorToolkit$look(double timeDelta, CallbackInfo ci) {
        if (SurvivorToolkitClient.FREECAM.active() && client.currentScreen == null && client.isWindowFocused()) {
            SurvivorToolkitClient.FREECAM.look(cursorDeltaX, cursorDeltaY);
            cursorDeltaX = 0;
            cursorDeltaY = 0;
            ci.cancel();
        }
    }
}
