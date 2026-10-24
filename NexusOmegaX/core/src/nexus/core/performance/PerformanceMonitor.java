package nexus.core.performance;

import nexus.core.Nexus;
import nexus.util.MathUtil;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;

/**
 * FPS / frame time / memory / CPU monitor used by the performance HUD elements
 * and by the diagnostics report.
 */
public final class PerformanceMonitor {

    private static final int HISTORY = 120;

    private final Nexus nexus;
    private final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    private final OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();

    private final float[] frameTimes = new float[HISTORY];
    private final float[] fpsHistory = new float[HISTORY];
    private int cursor;
    private int samples;

    private float fps;
    private float minFps = Float.MAX_VALUE;
    private float maxFps;
    private float averageFrameMs;
    private long lastTickNanos = System.nanoTime();
    private long tickCount;
    private float ticksPerSecond;
    private long lastSecond = System.currentTimeMillis();
    private int framesThisSecond;
    private double cpuLoad = -1.0;
    private long cpuProbeTime;

    public PerformanceMonitor(Nexus nexus) {
        this.nexus = nexus;
    }

    public void onFrame() {
        long now = System.nanoTime();
        float deltaMs = (now - lastTickNanos) / 1000000.0f;
        if (deltaMs > 0.0f && deltaMs < 2000.0f) {
            frameTimes[cursor] = deltaMs;
            fpsHistory[cursor] = 1000.0f / Math.max(0.0001f, deltaMs);
            cursor = (cursor + 1) % HISTORY;
            if (samples < HISTORY) {
                samples++;
            }
            recompute();
        }
        framesThisSecond++;
        long millis = System.currentTimeMillis();
        if (millis - lastSecond >= 1000L) {
            fps = framesThisSecond * 1000.0f / (float) (millis - lastSecond);
            framesThisSecond = 0;
            lastSecond = millis;
            probeCpu(millis);
        }
    }

    public void onTick() {
        tickCount++;
        long now = System.nanoTime();
        float delta = (now - lastTickNanos) / 1000000.0f;
        lastTickNanos = now;
        if (delta > 0.0f) {
            ticksPerSecond = 1000.0f / Math.max(1.0f, delta);
        }
    }

    private void recompute() {
        float total = 0.0f;
        float min = Float.MAX_VALUE;
        float max = 0.0f;
        for (int i = 0; i < samples; i++) {
            total += frameTimes[i];
            if (frameTimes[i] < min) {
                min = frameTimes[i];
            }
            if (frameTimes[i] > max) {
                max = frameTimes[i];
            }
        }
        averageFrameMs = samples == 0 ? 0.0f : total / samples;
        if (samples > 0) {
            minFps = 1000.0f / Math.max(0.0001f, max);
            maxFps = 1000.0f / Math.max(0.0001f, min);
        }
    }

    private void probeCpu(long now) {
        if (now - cpuProbeTime < 2000L) {
            return;
        }
        cpuProbeTime = now;
        try {
            Object value = os.getClass().getMethod("getProcessCpuLoad").invoke(os);
            if (value instanceof Double) {
                cpuLoad = ((Double) value).doubleValue() * 100.0;
                return;
            }
        } catch (Throwable ignored) {
            // not supported on every JVM
        }
        cpuLoad = -1.0;
    }

    public float fps() {
        return fps > 0.0f ? fps : (samples == 0 ? 0.0f : 1000.0f / Math.max(0.0001f, averageFrameMs));
    }

    public float minFps() {
        return minFps == Float.MAX_VALUE ? 0.0f : minFps;
    }

    public float maxFps() {
        return maxFps;
    }

    public float averageFrameMs() {
        return averageFrameMs;
    }

    public float worstFrameMs() {
        float worst = 0.0f;
        for (int i = 0; i < samples; i++) {
            if (frameTimes[i] > worst) {
                worst = frameTimes[i];
            }
        }
        return worst;
    }

    public float[] fpsHistory() {
        return fpsHistory;
    }

    public int historySize() {
        return samples;
    }

    public float ticksPerSecond() {
        return MathUtil.clamp(ticksPerSecond, 0.0f, 40.0f);
    }

    public long usedMemoryMb() {
        return memory.getHeapMemoryUsage().getUsed() / (1024L * 1024L);
    }

    public long maxMemoryMb() {
        return memory.getHeapMemoryUsage().getMax() / (1024L * 1024L);
    }

    public long committedMemoryMb() {
        return memory.getHeapMemoryUsage().getCommitted() / (1024L * 1024L);
    }

    public int memoryPercent() {
        long max = maxMemoryMb();
        return max <= 0 ? 0 : (int) (usedMemoryMb() * 100L / max);
    }

    public double cpuLoad() {
        return cpuLoad;
    }

    /** GPU name / load is not exposed by LWJGL to the JVM: reported per adapter. */
    public String gpuInfo() {
        String renderer = System.getProperty("nexus.gpu", "");
        return renderer == null || renderer.isEmpty() ? "NOT AVAILABLE" : renderer;
    }

    public int activeModules() {
        return nexus.modules().enabledCount();
    }

    public String summary() {
        return String.format("FPS %.0f (min %.0f / max %.0f) | frame %.2f ms | mem %d/%d MB | tps %.1f | modules %d",
                fps(), minFps(), maxFps(), averageFrameMs(), usedMemoryMb(), maxMemoryMb(),
                ticksPerSecond(), activeModules());
    }
}
