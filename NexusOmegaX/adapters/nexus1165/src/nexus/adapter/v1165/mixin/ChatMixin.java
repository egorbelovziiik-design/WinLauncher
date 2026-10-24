package nexus.adapter.v1165.mixin;

import nexus.adapter.v1165.Nexus1165Hooks;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Lets the command system and the chat modules inspect outgoing messages.
 * <p>
 * 1.16.5 has no {@code ChatScreen.sendMessage} any more - every outgoing line
 * goes through {@code ClientPlayerEntity.sendChatMessage(String)}.
 */
@Mixin(ClientPlayerEntity.class)
public final class ChatMixin {

    @ModifyVariable(method = "sendChatMessage(Ljava/lang/String;)V",
            at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private String nexus$send(String message) {
        try {
            return Nexus1165Hooks.Hooks.HOOKS.onChatSend(message);
        } catch (Throwable throwable) {
            nexus.util.Log.error("Nexus chat hook failed", throwable);
            return message;
        }
    }
}
