package nexus.adapter.v1214.mixin;

import net.minecraft.client.gui.screens.ChatScreen;
import nexus.adapter.v1214.mixin.MinecraftMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Lets the command system and the chat modules inspect outgoing messages. */
@Mixin(ChatScreen.class)
public final class ChatScreenMixin {

    @ModifyVariable(method = "handleChatInput", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private String nexus$send(String message) {
        return MinecraftMixin.Hooks.HOOKS.onChatSend(message);
    }
}
