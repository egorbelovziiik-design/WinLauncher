package nexus.adapter.v1214.mixin;

import nexus.adapter.v1214.Nexus1214Hooks;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Lets the command system and the chat modules inspect outgoing messages and
 * turns the chat into a HUD editor: while the chat is open, HUD elements can be
 * dragged with the mouse.
 */
@Mixin(ChatScreen.class)
public final class ChatScreenMixin {

    @ModifyVariable(method = "handleChatInput(Ljava/lang/String;Z)V",
            at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private String nexus$send(String message) {
        try {
            return Nexus1214Hooks.Hooks.HOOKS.onChatSend(message);
        } catch (Throwable throwable) {
            nexus.util.Log.error("Nexus chat hook failed", throwable);
            return message;
        }
    }
}
