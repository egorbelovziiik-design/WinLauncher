package dev.survivortoolkit.client;

import dev.survivortoolkit.policy.ServerPolicy;
import net.minecraft.client.MinecraftClient;
import java.util.Set;

public final class WorldPolicy {
    private WorldPolicy() {}

    /** LAN-hosted integrated worlds are intentionally treated as multiplayer. */
    public static boolean privateLocal(MinecraftClient client) {
        return client.world != null && client.isInSingleplayer() && client.getServer() != null && !client.getServer().isRemote();
    }

    public static String serverAddress(MinecraftClient client) {
        return client.getCurrentServerEntry() == null ? "" : ServerPolicy.normalize(client.getCurrentServerEntry().address);
    }

    public static boolean allowed(MinecraftClient client, Set<String> addresses) {
        return ServerPolicy.permitted(client.world != null, privateLocal(client), serverAddress(client), addresses);
    }
}
