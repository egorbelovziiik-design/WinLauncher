package nexus.adapter.v12111;

import net.minecraft.client.Minecraft;
import nexus.api.IWindow;

/** {@link IWindow} implementation for Minecraft 1.21.11. */
public final class Nexus12111Window implements IWindow {

    private final Nexus12111Client client;

    Nexus12111Window(Nexus12111Client client) {
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
        return handle().getWindow().getGuiScale();
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
        return handle().getWindow().isFocused();
    }

    @Override
    public boolean isFullscreen() {
        return handle().getWindow().isFullscreen();
    }
}
