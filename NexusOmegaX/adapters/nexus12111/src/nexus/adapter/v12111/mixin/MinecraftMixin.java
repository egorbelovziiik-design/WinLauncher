package nexus.adapter.v12111.mixin;

import net.minecraft.client.Minecraft;
import nexus.adapter.v12111.Nexus12111Hooks;
import nexus.adapter.v12111.Nexus12111Platform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drives the client tick and a clean shutdown from the vanilla game loop. */
@Mixin(Minecraft.class)
public final class MinecraftMixin {

    /** runTick(boolean) in 1.21.11 - the parameter list has to match exactly. */
    @Inject(method = "runTick(Z)V", at = @At("HEAD"))
    private void nexus$tick(boolean tick, CallbackInfo info) {
        Hooks.HOOKS.onStartTick();
    }

    @Inject(method = "close()V", at = @At("HEAD"))
    private void nexus$shutdown(CallbackInfo info) {
        if (nexus.core.Nexus.isInitialized()) {
            nexus.core.Nexus.instance().shutdown();
        }
    }

    /** Static holder so the hooks survive screen changes. */
    public static final class Hooks {
        public static final Nexus12111Hooks HOOKS = new Nexus12111Hooks();

        static {
            new Nexus12111Platform().installHooks();
        }

        private Hooks() {
        }
    }
}
