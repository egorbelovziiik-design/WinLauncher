package dev.axsesclient.common.perf;

import java.util.Arrays;
import java.util.Locale;

public final class PerformanceProfiler {
    private static final int SAMPLE_WINDOW = 120;
    private final float[] frameTimesMs = new float[SAMPLE_WINDOW];
    private final float[] hudCostsUs = new float[SAMPLE_WINDOW];
    private final float[] clickGuiCostsUs = new float[SAMPLE_WINDOW];
    private int cursor = 0;
    private int filled = 0;
    private int hudCursor = 0, hudFilled = 0;
    private int guiCursor = 0, guiFilled = 0;

    private long startupBeginNanos = System.nanoTime();
    private long startupDurationMs = 0L;
    private long lastFrameNanos = System.nanoTime();

    public void markStartupBegin() {
        this.startupBeginNanos = System.nanoTime();
    }

    public void markStartupComplete() {
        this.startupDurationMs = Math.max(1L, (System.nanoTime() - startupBeginNanos) / 1_000_000L);
    }

    public void recordFrame() {
        long now = System.nanoTime();
        float ms = (now - lastFrameNanos) / 1_000_000.0f;
        lastFrameNanos = now;
        if (ms > 0.0f && ms < 2000.0f) {
            frameTimesMs[cursor] = ms;
            cursor = (cursor + 1) % SAMPLE_WINDOW;
            if (filled < SAMPLE_WINDOW) {
                filled++;
            }
        }
    }

    public void recordHudRenderNanos(long nanos) {
        hudCostsUs[hudCursor] = Math.max(0L, nanos) / 1000.0f;
        hudCursor = (hudCursor + 1) % SAMPLE_WINDOW;
        if (hudFilled < SAMPLE_WINDOW) hudFilled++;
    }

    public void recordClickGuiRenderNanos(long nanos) {
        clickGuiCostsUs[guiCursor] = Math.max(0L, nanos) / 1000.0f;
        guiCursor = (guiCursor + 1) % SAMPLE_WINDOW;
        if (guiFilled < SAMPLE_WINDOW) guiFilled++;
    }

    public float getAverageFrameTimeMs() {
        if (filled == 0) {
            return 16.67f;
        }
        float sum = 0.0f;
        for (int i = 0; i < filled; i++) {
            sum += frameTimesMs[i];
        }
        return sum / filled;
    }

    public float getP95FrameTimeMs() {
        if (filled == 0) {
            return 16.67f;
        }
        float[] copy = Arrays.copyOf(frameTimesMs, filled);
        Arrays.sort(copy);
        int idx = Math.min(filled - 1, Math.max(0, (int) Math.ceil(filled * 0.95) - 1));
        return copy[idx];
    }

    public float getAverageHudCostUs() {
        if (hudFilled == 0) return 0.0f;
        float sum = 0.0f;
        for (int i = 0; i < hudFilled; i++) sum += hudCostsUs[i];
        return sum / hudFilled;
    }

    public float getAverageClickGuiCostUs() {
        if (guiFilled == 0) return 0.0f;
        float sum = 0.0f;
        for (int i = 0; i < guiFilled; i++) sum += clickGuiCostsUs[i];
        return sum / guiFilled;
    }

    public long getUsedMemoryMb() {
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L);
    }

    public long getMaxMemoryMb() {
        return Runtime.getRuntime().maxMemory() / (1024L * 1024L);
    }

    public long getAllocatedMemoryMb() {
        return Runtime.getRuntime().totalMemory() / (1024L * 1024L);
    }

    public long getStartupDurationMs() {
        return startupDurationMs;
    }

    public String formatTelemetrySummary(int mcFps) {
        return String.format(Locale.ROOT,
                "FPS: %d | Frame: %.2fms (P95 %.2fms) | HUD: %.0fus | GUI: %.0fus | RAM: %d/%dMB | Boot: %dms",
                Integer.valueOf(mcFps),
                Float.valueOf(getAverageFrameTimeMs()),
                Float.valueOf(getP95FrameTimeMs()),
                Float.valueOf(getAverageHudCostUs()),
                Float.valueOf(getAverageClickGuiCostUs()),
                Long.valueOf(getUsedMemoryMb()),
                Long.valueOf(getMaxMemoryMb()),
                Long.valueOf(startupDurationMs));
    }
}
