package dev.survivortoolkit.client.feature;

import dev.survivortoolkit.client.SurvivorToolkitClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.BlockItem;
import net.minecraft.text.Text;

public final class ToolkitHud {
    private ToolkitHud() {}
    public static void render(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        int width = ctx.getScaledWindowWidth();
        if (SurvivorToolkitClient.FREECAM.active()) {
            String label = Text.translatable("survivor_toolkit.hud.freecam").getString();
            int tw = client.textRenderer.getWidth(label);
            ctx.fill(width / 2 - tw / 2 - 6, 7, width / 2 + tw / 2 + 6, 23, 0xD9271F19);
            ctx.drawCenteredTextWithShadow(client.textRenderer, label, width / 2, 11, 0xFFFFC46B);
        } else if (SurvivorToolkitClient.WARDEN.visible()) {
            String label = Text.translatable("survivor_toolkit.hud.warden").getString();
            int tw = client.textRenderer.getWidth(label);
            ctx.fill(width / 2 - tw / 2 - 7, 7, width / 2 + tw / 2 + 7, 23, 0xDD1D1A20);
            ctx.drawCenteredTextWithShadow(client.textRenderer, label, width / 2, 11, 0xFF000000 | SurvivorToolkitClient.config().warningColor);
        }
        var cfg = SurvivorToolkitClient.config();
        if (cfg.buildAssistEnabled && cfg.showSelectedBlock && client.currentScreen == null && !SurvivorToolkitClient.FREECAM.active()) {
            if (MinecraftClient.isHudEnabled()) {
                int slot = client.player.getInventory().getSelectedSlot();
                int slotX = width / 2 - 91 + slot * 20;
                int slotY = ctx.getScaledWindowHeight() - 23;
                ctx.fill(slotX + 2, slotY, slotX + 20, slotY + 1, 0xFF7DD5B8);
            }
            var stack = client.player.getMainHandStack();
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                String label = Text.translatable("survivor_toolkit.hud.block", stack.getName()).getString();
                label = client.textRenderer.trimToWidth(label, Math.max(20, width - 24));
                int x = 8, y = ctx.getScaledWindowHeight() - 35;
                ctx.fill(x - 3, y - 3, x + client.textRenderer.getWidth(label) + 4, y + 12, 0xA812191D);
                ctx.fill(x - 3, y - 3, x - 1, y + 12, 0xFF7DD5B8);
                ctx.drawTextWithShadow(client.textRenderer, label, x, y, 0xFFE8F2EF);
            }
        }
    }
}
