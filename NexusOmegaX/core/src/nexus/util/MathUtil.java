package nexus.util;

/**
 * General purpose math helpers used by modules, the GUI animation system and
 * the HUD editor. All methods are allocation free where possible.
 */
public final class MathUtil {

    public static final double PI2 = Math.PI * 2.0;
    public static final double DEG_TO_RAD = Math.PI / 180.0;

    private MathUtil() {
    }

    public static double clamp(double value, double min, double max) {
        if (min > max) {
            double tmp = min;
            min = max;
            max = tmp;
        }
        return value < min ? min : (value > max ? max : value);
    }

    public static float clamp(float value, float min, float max) {
        return (float) clamp((double) value, min, max);
    }

    public static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    /** Linear interpolation. */
    public static double lerp(double a, double b, double t) {
        return a + (b - a) * clamp(t, 0.0, 1.0);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * (float) clamp(t, 0.0, 1.0);
    }

    /**
     * Frame-rate independent interpolation. {@code speed} is the fraction of the
     * remaining distance covered in 100 ms.
     */
    public static double approach(double current, double target, double speed, double deltaMs) {
        double factor = 1.0 - Math.pow(1.0 - clamp(speed, 0.0, 1.0), deltaMs / 100.0);
        double result = current + (target - current) * factor;
        return Math.abs(target - result) < 0.001 ? target : result;
    }

    public static float approach(float current, float target, float speed, float deltaMs) {
        return (float) approach((double) current, target, speed, deltaMs);
    }

    public static double round(double value, int decimals) {
        double factor = Math.pow(10.0, decimals);
        return Math.round(value * factor) / factor;
    }

    public static double roundToStep(double value, double step) {
        if (step <= 0.0) {
            return value;
        }
        return Math.round(value / step) * step;
    }

    public static double distance(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public static double distance3(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static double wrapDegrees(double value) {
        double result = value % 360.0;
        if (result >= 180.0) {
            result -= 360.0;
        }
        if (result < -180.0) {
            result += 360.0;
        }
        return result;
    }

    public static double angleDifference(double a, double b) {
        return Math.abs(wrapDegrees(a - b));
    }

    public static double toRadians(double degrees) {
        return degrees * DEG_TO_RAD;
    }

    public static double toDegrees(double radians) {
        return radians / DEG_TO_RAD;
    }

    /** 0..1 progress of {@code value} inside [min,max]. */
    public static double normalize(double value, double min, double max) {
        if (max - min == 0.0) {
            return 0.0;
        }
        return clamp((value - min) / (max - min), 0.0, 1.0);
    }

    public static double map(double value, double inMin, double inMax, double outMin, double outMax) {
        return outMin + (outMax - outMin) * normalize(value, inMin, inMax);
    }

    public static int signum(double value) {
        return value > 0.0 ? 1 : (value < 0.0 ? -1 : 0);
    }

    /** Deterministic pseudo random in [0,1) from integer coordinates (particles/world effects). */
    public static double hashNoise(int x, int y, int z) {
        long n = x * 374761393L + y * 668265263L + z * 2147483647L;
        n = (n ^ (n >> 13)) * 1274126177L;
        n = n ^ (n >> 16);
        return ((n >>> 8) & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    /** Smooth 0..1..0 pulse used for glow and breathing animations. */
    public static double pulse(long timeMs, double periodMs) {
        double phase = (timeMs % (long) periodMs) / periodMs;
        return 0.5 + 0.5 * Math.sin(phase * PI2);
    }
}
