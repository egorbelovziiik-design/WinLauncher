package nexus.adapter.v1165.mixin;

import nexus.adapter.v1165.Nexus1165Hooks;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drives the client tick and a clean shutdown from the vanilla game loop.
 * <p>
 * 1.16.5 has no {@code runTick} - the game tick is {@code MinecraftClient.tick()}.
 */
@Mixin(MinecraftClient.class)
public final class MinecraftClientMixin {

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void nexus$tick(CallbackInfo info) {
        try {
            Nexus1165Hooks.Hooks.HOOKS.onStartTick();
        } catch (Throwable throwable) {
            nexus.util.Log.error("Nexus tick hook failed", throwable);
        }
    }

    @Inject(method = "close()V", at = @At("HEAD"))
    private void nexus$shutdown(CallbackInfo info) {
        if (nexus.core.Nexus.isInitialized()) {
            nexus.core.Nexus.instance().shutdown();
        }
    }

}
