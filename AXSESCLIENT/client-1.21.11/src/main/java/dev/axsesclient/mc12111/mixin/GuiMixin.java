package dev.axsesclient.mc12111.mixin;

import dev.axsesclient.common.AxsesCore;
import dev.axsesclient.common.modules.Module;
import dev.axsesclient.mc12111.platform.Mc12111RenderAdapter;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void axses$suppressVanillaCrosshair(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        AxsesCore core = AxsesCore.getInstance();
        if (core != null) {
            Module ch = core.getModuleManager().getById("custom_crosshair");
            if (ch != null && ch.isEnabled()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void axses$renderHud(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && !mc.options.hideGui) {
            AxsesCore core = AxsesCore.getInstance();
            if (core != null) {
                core.onHudRender(new Mc12111RenderAdapter(graphics));
            }
        }
    }
}
