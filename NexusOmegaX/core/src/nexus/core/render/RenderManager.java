package nexus.core.render;
import nexus.util.Log;

import nexus.api.IRenderer;
import nexus.core.Nexus;
import nexus.core.module.Module;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the per-frame render state, the visual overrides registry and the
 * animated particle field used by the ClickGUI background.
 * <p>
 * Visual overrides (gamma, fov, render distance, ...) are registered here so
 * PANIC and module shutdown can always restore them, even if a module crashes.
 */
public final class RenderManager {

    /** Restores a vanilla value when a module is disabled. */
    public interface Override {
        void restore();
    }

    private final Nexus nexus;
    private final Map<String, Override> overrides = new LinkedHashMap<String, Override>();
    private final ParticleField particles = new ParticleField();

    private IRenderer renderer;
    private double blurStrength = 1.0;
    private double glowStrength = 1.0;
    private double cornerRadius = 8.0;
    private String font = "Nexus Sans";

    private long frameCount;
    private long lastFrameStart;
    private double lastFrameMs;
    private int drawnElements;
    private int drawnElementsLastFrame;

    public RenderManager(Nexus nexus) {
        this.nexus = nexus;
    }

    // ------------------------------------------------------------------- frame

    public void beginFrame(IRenderer renderer, float deltaMs) {
        this.renderer = renderer;
        this.lastFrameMs = deltaMs;
        this.lastFrameStart = System.nanoTime();
        this.drawnElementsLastFrame = drawnElements;
        this.drawnElements = 0;
        frameCount++;
        if (frameCount % 2L == 0L) {
            particles.update(deltaMs, nexus.client().window());
        }
    }

    public void endFrame() {
        renderer = null;
    }

    public void beginWorld(IRenderer renderer, float deltaMs) {
        this.renderer = renderer;
    }

    public void endWorld() {
        renderer = null;
    }

    public IRenderer renderer() {
        return renderer;
    }

    public long frameCount() {
        return frameCount;
    }

    public double lastFrameMs() {
        return lastFrameMs;
    }

    public int drawnElements() {
        return drawnElements;
    }

    public int drawnElementsLastFrame() {
        return drawnElementsLastFrame;
    }

    /** Called by every draw helper so the performance HUD can report real work. */
    public void countDraw() {
        drawnElements++;
    }

    // --------------------------------------------------------------- overrides

    /** Registers (or replaces) a named override. */
    public void registerOverride(String id, Override override) {
        Override previous = overrides.put(id, override);
        if (previous != null) {
            previous.restore();
        }
    }

    public void releaseOverride(String id) {
        Override override = overrides.remove(id);
        if (override != null) {
            override.restore();
        }
    }

    public boolean hasOverride(String id) {
        return overrides.containsKey(id);
    }

    /** Restores every registered vanilla value (PANIC and shutdown). */
    public void resetVisualOverrides() {
        List<Map.Entry<String, Override>> entries = new ArrayList<Map.Entry<String, Override>>(overrides.entrySet());
        for (Map.Entry<String, Override> entry : entries) {
            try {
                entry.getValue().restore();
            } catch (Throwable throwable) {
                Log.error("Failed to restore override " + entry.getKey(), throwable);
            }
        }
        overrides.clear();
        for (Module module : nexus.modules().enabledRaw()) {
            try {
                module.cleanup();
            } catch (Throwable ignored) {
                // cleanup must never break shutdown
            }
        }
    }

    // ------------------------------------------------------------------ style

    public double blurStrength() {
        return blurStrength;
    }

    public void setBlurStrength(double value) {
        blurStrength = value;
    }

    public double glowStrength() {
        return glowStrength;
    }

    public void setGlowStrength(double value) {
        glowStrength = value;
    }

    public double cornerRadius() {
        return cornerRadius;
    }

    public void setCornerRadius(double value) {
        cornerRadius = value;
    }

    public String font() {
        return font;
    }

    public void setFont(String value) {
        font = value;
    }

    public ParticleField particles() {
        return particles;
    }
}
