package nexus.core.event;

import nexus.api.IRenderer;

/** Fired after the HUD pass, before the vanilla debug overlay. */
public final class Render2DEvent extends Event {

    public final IRenderer renderer;
    public final int width;
    public final int height;
    public final float deltaMs;
    public final int mouseX;
    public final int mouseY;

    public Render2DEvent(IRenderer renderer, int width, int height, float deltaMs, int mouseX, int mouseY) {
        this.renderer = renderer;
        this.width = width;
        this.height = height;
        this.deltaMs = deltaMs;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    @Override
    public boolean isCancellable() {
        return false;
    }
}
