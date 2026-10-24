package nexus.adapter.v1165;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import nexus.api.IClient;
import nexus.api.NexusPlatform;
import nexus.core.Nexus;
import nexus.core.NexusInfo;

import java.io.File;

/**
 * Nexus adapter for Minecraft 1.16.5.
 * <p>
 * 1.16.5 still runs on Java 8, so this adapter (and the shared core) is written
 * in Java 8 compatible source. It maps the old client names
 * ({@code MinecraftClient}, {@code ClientPlayerEntity}, {@code ClientWorld},
 * {@code MatrixStack}, {@code GameOptions}...) onto the same {@code nexus.api}
 * interfaces that the 1.21 adapters implement.
 */
public final class Nexus1165Platform implements NexusPlatform, ClientModInitializer {

    public static final String MC_VERSION = "1.16.5";
    public static final int PROTOCOL = 754;

    private final MinecraftClient minecraft = MinecraftClient.getInstance();
    private final Nexus1165Client client = new Nexus1165Client(minecraft);
    private final File directory;

    public Nexus1165Platform() {
        this.directory = new File(minecraft.runDirectory, NexusInfo.CONFIG_FOLDER);
    }

    @Override
    public void onInitializeClient() {
        installHooks();
        Nexus.init(this, directory);
        nexus.util.Log.info("Nexus " + NexusInfo.VERSION + " ready on Minecraft " + MC_VERSION);
    }

    @Override
    public String id() {
        return "Nexus1165";
    }

    @Override
    public String minecraftVersion() {
        return MC_VERSION;
    }

    @Override
    public int protocol() {
        return PROTOCOL;
    }

    @Override
    public IClient client() {
        return client;
    }

    @Override
    public String configDirectory() {
        return directory.getAbsolutePath();
    }

    @Override
    public void installHooks() {
        Nexus1165Hooks.Hooks.HOOKS.install();
    }

    @Override
    public void uninstallHooks() {
        Nexus1165Hooks.Hooks.HOOKS.uninstall();
    }

    @Override
    public boolean isHealthy() {
        return Nexus1165Hooks.Hooks.HOOKS.installed() && Nexus.isInitialized();
    }

    public Nexus1165Client adapterClient() {
        return client;
    }
}
