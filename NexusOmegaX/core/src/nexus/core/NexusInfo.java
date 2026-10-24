package nexus.core;

import java.io.File;

/** Static product information shared by the client, the launcher and the docs. */
public final class NexusInfo {

    public static final String NAME = "NexusOmegaX";
    public static final String PRODUCT = "Nexus Client Omega X";
    public static final String SLOGAN = "THE ULTIMATE MINECRAFT EXPERIENCE";
    public static final String VERSION = "1.0.0";
    public static final String BUILD = "omega-x-1.0.0+release";
    public static final String AUTHOR = "NexusOmegaX Team";
    public static final int SCHEMA = 3;
    public static final String COMMAND_PREFIX = ".";
    public static final String CONFIG_FOLDER = "NexusOmegaX";
    public static final String SUPPORTED_VERSIONS = "1.16.5, 1.21.4, 1.21.11";

    private NexusInfo() {
    }

    /** Root folder of every client file (configs, logs, backups, scripts...). */
    public static File directory() {
        return Nexus.isInitialized() ? Nexus.instance().directory() : new File(CONFIG_FOLDER);
    }

    public static String fullTitle() {
        return PRODUCT + " v" + VERSION;
    }

    public static String shortWatermark() {
        return "NEXUS OMEGA X " + VERSION;
    }
}
