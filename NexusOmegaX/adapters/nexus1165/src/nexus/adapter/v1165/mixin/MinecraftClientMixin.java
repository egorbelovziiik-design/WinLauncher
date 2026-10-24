package nexus.adapter.v1165.mixin;

import net.minecraft.client.MinecraftClient;
import nexus.adapter.v1165.Nexus1165Hooks;
import nexus.adapter.v1165.Nexus1165Platform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drives the client tick and a clean shutdown from the vanilla game loop. */
@Mixin(MinecraftClient.class)
public final class MinecraftClientMixin {

    @Inject(method = "runTick", at = @At("HEAD"))
    private void nexus$tick(CallbackInfo info) {
        Hooks.HOOKS.onStartTick();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void nexus$shutdown(CallbackInfo info) {
        if (nexus.core.Nexus.isInitialized()) {
            nexus.core.Nexus.instance().shutdown();
        }
    }

    /** Static holder so the hooks survive screen changes. */
    public static final class Hooks {
        public static final Nexus1165Hooks HOOKS = new Nexus1165Hooks();

        static {
            new Nexus1165Platform().installHooks();
        }

        private Hooks() {
        }
    }
}
