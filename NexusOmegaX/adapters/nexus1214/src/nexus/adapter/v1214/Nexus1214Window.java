package nexus.adapter.v1214;

import net.minecraft.client.Minecraft;
import nexus.api.IWindow;

/** {@link IWindow} implementation for Minecraft 1.21.4. */
public final class Nexus1214Window implements IWindow {

    private final Nexus1214Client client;

    Nexus1214Window(Nexus1214Client client) {
        this.client = client;
    }

    private Minecraft handle() {
        return client.handle();
    }

    @Override
    public int guiWidth() {
        return handle().getWindow().getGuiScaledWidth();
    }

    @Override
    public int guiHeight() {
        return handle().getWindow().getGuiScaledHeight();
    }

    @Override
    public int frameWidth() {
        return handle().getWindow().getScreenWidth();
    }

    @Override
    public int frameHeight() {
        return handle().getWindow().getScreenHeight();
    }

    @Override
    public int guiScale() {
        return (int) handle().getWindow().getGuiScale();
    }

    @Override
    public float deltaMs() {
        return handle().getDeltaTracker().getGameTimeDeltaPartialTick(true) * 50.0f;
    }

    @Override
    public float deltaSeconds() {
        return deltaMs() / 1000.0f;
    }

    @Override
    public boolean isFocused() {
        return handle().isWindowActive();
    }

    @Override
    public boolean isFullscreen() {
        return handle().getWindow().isFullscreen();
    }
}
