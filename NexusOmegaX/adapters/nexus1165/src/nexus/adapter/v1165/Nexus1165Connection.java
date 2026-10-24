package nexus.adapter.v1165;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import nexus.api.IConnection;

/** {@link IConnection} implementation for Minecraft 1.16.5. */
public final class Nexus1165Connection implements IConnection {

    private final Nexus1165Client client;
    private long since;

    Nexus1165Connection(Nexus1165Client client) {
        this.client = client;
    }

    private MinecraftClient handle() {
        return client.handle();
    }

    private ClientPlayNetworkHandler listener() {
        return handle().getNetworkHandler();
    }

    void noteJoin() {
        this.since = System.currentTimeMillis();
    }

    @Override
    public boolean isConnected() {
        ClientPlayNetworkHandler listener = listener();
        return listener != null && listener.getConnection() != null
                && !listener.getConnection().isDisconnected();
    }

    @Override
    public boolean isSingleplayer() {
        return handle().isIntegratedServerRunning();
    }

    @Override
    public String serverAddress() {
        net.minecraft.client.network.ServerInfo entry = handle().getCurrentServerEntry();
        return entry == null ? (isSingleplayer() ? "singleplayer" : "unknown") : entry.address;
    }

    @Override
    public String serverBrand() {
        return handle().player == null ? "" : String.valueOf(handle().player.getServerBrand());
    }

    @Override
    public int ping() {
        if (handle().player == null || listener() == null) {
            return 0;
        }
        net.minecraft.client.network.PlayerListEntry entry = listener().getPlayerListEntry(
                handle().player.getGameProfile().getId());
        return entry == null ? 0 : entry.getLatency();
    }

    @Override
    public int playerCount() {
        return listener() == null ? (isSingleplayer() ? 1 : 0) : listener().getPlayerList().size();
    }

    @Override
    public int maxPlayers() {
        net.minecraft.client.network.ServerInfo entry = handle().getCurrentServerEntry();
        return entry == null ? 1 : 100;
    }

    @Override
    public int protocol() {
        return Nexus1165Platform.PROTOCOL;
    }

    @Override
    public String motd() {
        net.minecraft.client.network.ServerInfo entry = handle().getCurrentServerEntry();
        return entry == null ? "" : entry.label.getString();
    }

    @Override
    public long connectedSince() {
        return since;
    }

    @Override
    public void disconnect(String reason) {
        if (handle().world != null) {
            handle().world.disconnect();
        }
        handle().disconnect();
    }
}
