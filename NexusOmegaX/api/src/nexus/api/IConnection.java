package nexus.api;

/** Multiplayer / integrated server connection info. */
public interface IConnection {

    boolean isConnected();

    boolean isSingleplayer();

    String serverAddress();

    String serverBrand();

    int ping();

    int playerCount();

    int maxPlayers();

    /** Protocol number reported by the server, -1 when unknown. */
    int protocol();

    /** Server MOTD or singleplayer world name. */
    String motd();

    long connectedSince();

    void disconnect(String reason);
}
