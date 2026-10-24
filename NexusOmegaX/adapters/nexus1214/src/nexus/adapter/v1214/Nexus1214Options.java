package nexus.adapter.v1214;

import net.minecraft.client.Minecraft;
import nexus.api.IOptions;

/** {@link IOptions} implementation for Minecraft 1.21.4. */
public final class Nexus1214Options implements IOptions {

    private final Nexus1214Client client;

    Nexus1214Options(Nexus1214Client client) {
        this.client = client;
    }

    private Minecraft handle() {
        return client.handle();
    }

    @Override
    public double gamma() {
        return handle().options.gamma().get();
    }

    @Override
    public void setGamma(double value) {
        handle().options.gamma().set(value);
    }

    @Override
    public double fov() {
        return handle().options.fov().get();
    }

    @Override
    public void setFov(double value) {
        handle().options.fov().set(Integer.valueOf((int) Math.max(30.0, Math.min(110.0, value))));
    }

    @Override
    public int renderDistance() {
        return handle().options.renderDistance().get();
    }

    @Override
    public void setRenderDistance(int value) {
        handle().options.renderDistance().set(value);
    }

    @Override
    public int guiScale() {
        return handle().options.guiScale().get();
    }

    @Override
    public boolean smoothLighting() {
        return handle().options.ambientOcclusion().get();
    }

    @Override
    public boolean viewBobbing() {
        return handle().options.bobView().get();
    }

    @Override
    public void setViewBobbing(boolean value) {
        handle().options.bobView().set(value);
    }

    @Override
    public boolean entityShadows() {
        return handle().options.entityShadows().get();
    }

    @Override
    public void setEntityShadows(boolean value) {
        handle().options.entityShadows().set(value);
    }

    @Override
    public boolean cloudRenderMode() {
        return handle().options.cloudStatus().get() != net.minecraft.client.CloudStatus.OFF;
    }

    @Override
    public int currentFps() {
        return Minecraft.getInstance().getFps();
    }

    @Override
    public boolean hideGui() {
        return handle().options.hideGui;
    }

    @Override
    public boolean debugEnabled() {
        return handle().getDebugOverlay().showDebugScreen();
    }

    @Override
    public float soundVolumeMaster() {
        return handle().options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER);
    }

    @Override
    public void setSoundVolumeMaster(float value) {
        handle().options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER)
                .set(Double.valueOf(value));
    }
}
