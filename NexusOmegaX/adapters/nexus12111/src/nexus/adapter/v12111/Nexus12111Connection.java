package nexus.adapter.v12111;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import nexus.api.IConnection;

/** {@link IConnection} implementation for Minecraft 1.21.11. */
public final class Nexus12111Connection implements IConnection {

    private final Nexus12111Client client;

    Nexus12111Connection(Nexus12111Client client) {
        this.client = client;
    }

    private Minecraft handle() {
        return client.handle();
    }

    private ClientPacketListener listener() {
        return handle().getConnection();
    }

    @Override
    public boolean isConnected() {
        return listener() != null && !listener().getConnection().isDisconnected();
    }

    @Override
    public boolean isSingleplayer() {
        return handle().getSingleplayerServer() != null && !handle().isLocalServer();
    }

    @Override
    public String serverAddress() {
        ServerData data = handle().getCurrentServer();
        return data == null ? "singleplayer" : data.ip;
    }

    @Override
    public String serverBrand() {
        ClientPacketListener listener = listener();
        return listener == null ? "" : "vanilla";
    }

    @Override
    public int ping() {
        if (handle().player == null || listener() == null) {
            return 0;
        }
        for (net.minecraft.client.multiplayer.PlayerInfo info : listener().getOnlinePlayers()) {
            if (info.getProfile().getId().equals(handle().player.getUUID())) {
                return info.getLatency();
            }
        }
        return 0;
    }

    @Override
    public int playerCount() {
        return listener() == null ? 1 : listener().getOnlinePlayers().size();
    }

    @Override
    public int maxPlayers() {
        return 8;
    }

    @Override
    public int protocol() {
        return Nexus12111Platform.PROTOCOL;
    }

    @Override
    public String motd() {
        ServerData data = handle().getCurrentServer();
        return data == null ? "" : data.name;
    }

    @Override
    public long connectedSince() {
        return nexus.core.Nexus.isInitialized()
                ? System.currentTimeMillis() - nexus.core.Nexus.instance().session().worldMs()
                : System.currentTimeMillis();
    }

    @Override
    public void disconnect(String reason) {
        if (listener() != null) {
            listener().getConnection().disconnect(
                    net.minecraft.network.chat.Component.literal(reason));
        } else {
            handle().disconnect();
        }
    }
}
