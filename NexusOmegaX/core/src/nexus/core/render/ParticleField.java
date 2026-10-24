package nexus.core.render;

import nexus.api.IRenderer;
import nexus.api.IWindow;
import nexus.core.Nexus;
import nexus.util.MathUtil;

/**
 * Animated background particle field used by the ClickGUI and the custom main
 * menu. Purely client side, allocation free after construction.
 */
public final class ParticleField {

    private static final int MAX_PARTICLES = 90;

    private final float[] x = new float[MAX_PARTICLES];
    private final float[] y = new float[MAX_PARTICLES];
    private final float[] vx = new float[MAX_PARTICLES];
    private final float[] vy = new float[MAX_PARTICLES];
    private final float[] size = new float[MAX_PARTICLES];
    private final float[] alpha = new float[MAX_PARTICLES];
    private boolean initialized;

    public void update(float deltaMs, IWindow window) {
        if (window == null) {
            return;
        }
        float width = Math.max(1, window.guiWidth());
        float height = Math.max(1, window.guiHeight());
        float dt = MathUtil.clamp(deltaMs / 16.0f, 0.2f, 3.0f);
        for (int i = 0; i < MAX_PARTICLES; i++) {
            if (!initialized) {
                x[i] = (float) (Math.random() * width);
                y[i] = (float) (Math.random() * height);
                vx[i] = (float) ((Math.random() - 0.5) * 0.35);
                vy[i] = (float) ((Math.random() - 0.5) * 0.35);
                size[i] = (float) (0.8 + Math.random() * 2.2);
                alpha[i] = (float) (0.12 + Math.random() * 0.4);
            }
            x[i] += vx[i] * dt;
            y[i] += vy[i] * dt;
            if (x[i] < -10.0f) {
                x[i] = width + 10.0f;
            }
            if (x[i] > width + 10.0f) {
                x[i] = -10.0f;
            }
            if (y[i] < -10.0f) {
                y[i] = height + 10.0f;
            }
            if (y[i] > height + 10.0f) {
                y[i] = -10.0f;
            }
        }
        initialized = true;
    }

    public void render(IRenderer renderer, IWindow window, int accent, int secondary) {
        if (window == null || renderer == null) {
            return;
        }
        for (int i = 0; i < MAX_PARTICLES; i++) {
            int color = (i % 3 == 0) ? secondary : accent;
            int a = (int) (alpha[i] * 255);
            renderer.fillRect(x[i], y[i], size[i], size[i], (a << 24) | (color & 0x00FFFFFF));
        }
        // soft glow lines between close particles (nebula effect)
        for (int i = 0; i < MAX_PARTICLES; i += 3) {
            for (int j = i + 3; j < MAX_PARTICLES; j += 3) {
                double dx = x[i] - x[j];
                double dy = y[i] - y[j];
                double dist = dx * dx + dy * dy;
                if (dist < 6400.0) {
                    int a = (int) ((1.0 - dist / 6400.0) * 42.0);
                    if (a > 2) {
                        renderer.line(x[i], y[i], x[j], y[j], 0.6, (a << 24) | (accent & 0x00FFFFFF));
                    }
                }
            }
        }
    }

    public void renderThemed(IRenderer renderer, IWindow window) {
        if (Nexus.isInitialized()) {
            long now = System.currentTimeMillis();
            render(renderer, window, Nexus.instance().themes().accent(now), Nexus.instance().themes().primary(now));
        }
    }
}
