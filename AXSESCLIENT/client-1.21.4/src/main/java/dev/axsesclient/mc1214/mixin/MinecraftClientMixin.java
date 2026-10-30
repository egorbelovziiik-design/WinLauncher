package dev.axsesclient.mc1214.mixin;

import dev.axsesclient.common.AxsesCore;
import dev.axsesclient.mc1214.gui.AxsesMainMenuScreen1214;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen axses$replaceTitleScreen(Screen incoming) {
        if (incoming instanceof TitleScreen) {
            return new AxsesMainMenuScreen1214();
        }
        return incoming;
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void axses$onShutdown(CallbackInfo ci) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null) {
            core.shutdown();
        }
    }
}
