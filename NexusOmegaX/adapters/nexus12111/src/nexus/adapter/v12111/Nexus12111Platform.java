package nexus.adapter.v12111;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import nexus.api.IClient;
import nexus.api.NexusPlatform;
import nexus.core.Nexus;
import nexus.core.NexusInfo;

import java.io.File;

/**
 * Nexus adapter for Minecraft 1.21.11.
 * <p>
 * The adapter is the only place in the whole client that touches Minecraft
 * classes. Everything else ({@link Nexus}, every module, the GUI, the HUD and
 * the command system) talks to the game through the tiny {@code nexus.api}
 * interfaces, which is why the very same core runs on every supported version.
 */
public final class Nexus12111Platform implements NexusPlatform, ClientModInitializer {

    public static final String MC_VERSION = "1.21.11";
    public static final int PROTOCOL = 774;

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Nexus12111Client client = new Nexus12111Client(minecraft);
    private final File directory;
    private final Nexus12111Hooks hooks = new Nexus12111Hooks();

    public Nexus12111Platform() {
        this.directory = new File(minecraft.gameDirectory, NexusInfo.CONFIG_FOLDER);
    }

    /** Called by Fabric through {@code fabric.mod.json} (entrypoint "client"). */
    @Override
    public void onInitializeClient() {
        installHooks();
        Nexus.init(this, directory);
        nexus.util.Log.info("Nexus " + NexusInfo.VERSION + " ready on Minecraft " + MC_VERSION);
    }

    @Override
    public String id() {
        return "Nexus12111";
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
        hooks.install();
    }

    @Override
    public void uninstallHooks() {
        hooks.uninstall();
    }

    @Override
    public boolean isHealthy() {
        return hooks.installed() && Nexus.isInitialized();
    }

    public Nexus12111Client adapterClient() {
        return client;
    }

    public Minecraft minecraft() {
        return minecraft;
    }
}
