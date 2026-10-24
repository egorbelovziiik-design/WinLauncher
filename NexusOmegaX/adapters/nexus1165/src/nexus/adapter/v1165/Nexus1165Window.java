package nexus.adapter.v1165;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import nexus.api.IWindow;

/** {@link IWindow} implementation for Minecraft 1.16.5. */
public final class Nexus1165Window implements IWindow {

    private final Nexus1165Client client;

    Nexus1165Window(Nexus1165Client client) {
        this.client = client;
    }

    private MinecraftClient handle() {
        return client.handle();
    }

    private Window window() {
        return handle().getWindow();
    }

    @Override
    public int guiWidth() {
        return window().getScaledWidth();
    }

    @Override
    public int guiHeight() {
        return window().getScaledHeight();
    }

    @Override
    public int frameWidth() {
        return window().getWidth();
    }

    @Override
    public int frameHeight() {
        return window().getHeight();
    }

    @Override
    public int guiScale() {
        return window().calculateScaleFactor(0, handle().getWindow().isFullscreen());
    }

    @Override
    public float deltaMs() {
        return (float) handle().getLastFrameDuration() * 50.0f;
    }

    @Override
    public float deltaSeconds() {
        return deltaMs() / 1000.0f;
    }

    @Override
    public boolean isFocused() {
        return handle().isWindowFocused();
    }

    @Override
    public boolean isFullscreen() {
        return handle().getWindow().isFullscreen();
    }
}
