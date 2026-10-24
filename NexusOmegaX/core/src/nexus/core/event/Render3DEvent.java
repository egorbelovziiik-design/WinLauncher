package nexus.core.event;

import nexus.api.IRenderer;

/** Fired inside the world render pass with an already aligned pose stack. */
public final class Render3DEvent extends Event {

    public final IRenderer renderer;
    public final float deltaMs;
    public final float partialTicks;

    public Render3DEvent(IRenderer renderer, float deltaMs, float partialTicks) {
        this.renderer = renderer;
        this.deltaMs = deltaMs;
        this.partialTicks = partialTicks;
    }

    @Override
    public boolean isCancellable() {
        return false;
    }
}
