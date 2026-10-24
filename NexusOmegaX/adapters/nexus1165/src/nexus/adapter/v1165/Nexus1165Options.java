package nexus.adapter.v1165;

import net.minecraft.client.options.GameOptions;
import nexus.api.IOptions;

/** {@link IOptions} implementation for Minecraft 1.16.5. */
public final class Nexus1165Options implements IOptions {

    private final Nexus1165Client client;

    Nexus1165Options(Nexus1165Client client) {
        this.client = client;
    }

    private GameOptions options() {
        return client.handle().options;
    }

    @Override
    public double gamma() {
        return options().gamma;
    }

    @Override
    public void setGamma(double value) {
        options().gamma = (float) value;
    }

    @Override
    public double fov() {
        return options().fov;
    }

    @Override
    public void setFov(double value) {
        options().fov = (float) value;
    }

    @Override
    public int renderDistance() {
        return options().viewDistance;
    }

    @Override
    public void setRenderDistance(int value) {
        options().viewDistance = Math.max(2, Math.min(32, value));
    }

    @Override
    public int guiScale() {
        return options().guiScale;
    }

    @Override
    public boolean smoothLighting() {
        return options().ao != 0;
    }

    @Override
    public boolean viewBobbing() {
        return options().bobView;
    }

    @Override
    public void setViewBobbing(boolean value) {
        options().bobView = value;
    }

    @Override
    public boolean entityShadows() {
        return options().entityShadows;
    }

    @Override
    public void setEntityShadows(boolean value) {
        options().entityShadows = value;
    }

    @Override
    public boolean cloudRenderMode() {
        return options().clouds;
    }

    @Override
    public int currentFps() {
        return (int) client.handle().fpsCounter.getFps();
    }

    @Override
    public boolean hideGui() {
        return options().hudHidden;
    }

    @Override
    public boolean debugEnabled() {
        return options().debugEnabled;
    }

    @Override
    public float soundVolumeMaster() {
        return options().getSoundVolume(net.minecraft.sound.SoundCategory.MASTER);
    }

    @Override
    public void setSoundVolumeMaster(float value) {
    }
}
