package nexus.adapter.v1165.mixin;

import net.minecraft.client.MinecraftClient;
import nexus.adapter.v1165.Nexus1165Hooks;
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
            Hooks.HOOKS.onStartTick();
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

    /** Static holder so the hooks survive screen changes. */
    public static final class Hooks {
        public static final Nexus1165Hooks HOOKS = new Nexus1165Hooks();

        static {
            // Just enable the shared hook object. Building a platform here used to
            // run adapter constructors from inside the game loop, and any exception
            // became an ExceptionInInitializerError on the first click.
            Nexus1165Hooks.Hooks.HOOKS.install();
        }

        private Hooks() {
        }
    }
}
