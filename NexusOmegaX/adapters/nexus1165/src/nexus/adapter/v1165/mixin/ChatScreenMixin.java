package nexus.adapter.v1165.mixin;

import net.minecraft.client.gui.screen.ChatScreen;
import nexus.adapter.v1165.mixin.MinecraftClientMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Lets the command system and the chat modules inspect outgoing messages. */
@Mixin(ChatScreen.class)
public final class ChatScreenMixin {

    @ModifyVariable(method = "sendMessage", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private String nexus$send(String message) {
        return MinecraftClientMixin.Hooks.HOOKS.onChatSend(message);
    }
}
