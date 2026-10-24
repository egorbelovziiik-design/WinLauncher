package nexus.test;

import nexus.api.IClient;
import nexus.api.NexusPlatform;

import java.io.File;

/** Adapter that boots {@link HeadlessClient} through the normal platform contract. */
public final class HeadlessPlatform implements NexusPlatform {

    private final HeadlessClient client = new HeadlessClient();
    private final File directory;
    private int hooks;

    public HeadlessPlatform(File directory) {
        this.directory = directory;
    }

    public HeadlessClient headless() {
        return client;
    }

    @Override
    public String id() {
        return "NexusHeadless";
    }

    @Override
    public String minecraftVersion() {
        return "1.21.4";
    }

    @Override
    public int protocol() {
        return 769;
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
        hooks++;
    }

    @Override
    public void uninstallHooks() {
        hooks--;
    }

    @Override
    public boolean isHealthy() {
        return hooks == 1 && client.connection().isConnected();
    }

    public int hooks() {
        return hooks;
    }
}
