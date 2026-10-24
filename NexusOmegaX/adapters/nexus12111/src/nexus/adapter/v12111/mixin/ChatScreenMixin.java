package nexus.adapter.v12111.mixin;

import net.minecraft.client.gui.screens.ChatScreen;
import nexus.adapter.v12111.mixin.MinecraftMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Lets the command system and the chat modules inspect outgoing messages. */
@Mixin(ChatScreen.class)
public final class ChatScreenMixin {

    @ModifyVariable(method = "handleChatInput(Ljava/lang/String;Z)V",
            at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private String nexus$send(String message) {
        try {
            return MinecraftMixin.Hooks.HOOKS.onChatSend(message);
        } catch (Throwable throwable) {
            nexus.util.Log.error("Nexus chat hook failed", throwable);
            return message;
        }
    }
}
