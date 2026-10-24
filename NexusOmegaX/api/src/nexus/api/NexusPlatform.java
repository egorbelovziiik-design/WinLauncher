package nexus.api;

/**
 * Entry point contract implemented by every version adapter
 * ({@code Nexus1165}, {@code Nexus1214}, {@code Nexus12111}).
 */
public interface NexusPlatform {

    /** Adapter id, e.g. {@code Nexus1214}. */
    String id();

    /** Supported Minecraft version, e.g. {@code 1.21.4}. */
    String minecraftVersion();

    /** Protocol number of the supported version. */
    int protocol();

    IClient client();

    /** Directory of the NexusOmegaX folder inside the game directory. */
    String configDirectory();

    /** Registers the render / tick / input hooks the core needs. */
    void installHooks();

    /** Removes all hooks (used on unload / shutdown). */
    void uninstallHooks();

    /** Reports whether the adapter considers this runtime healthy. */
    boolean isHealthy();
}
