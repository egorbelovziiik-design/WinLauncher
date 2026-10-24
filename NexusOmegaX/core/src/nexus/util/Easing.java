package nexus.util;

/**
 * Easing functions for ClickGUI / HUD / notification animations.
 */
public final class Easing {

    public static final int LINEAR = 0;
    public static final int EASE_IN = 1;
    public static final int EASE_OUT = 2;
    public static final int EASE_IN_OUT = 3;
    public static final int EASE_OUT_BACK = 4;
    public static final int EASE_OUT_EXPO = 5;
    public static final int EASE_OUT_BOUNCE = 6;
    public static final int EASE_OUT_ELASTIC = 7;

    private Easing() {
    }

    public static String[] names() {
        return new String[]{"Linear", "EaseIn", "EaseOut", "EaseInOut", "EaseOutBack", "EaseOutExpo", "EaseOutBounce", "EaseOutElastic"};
    }

    public static double ease(int type, double t) {
        double x = MathUtil.clamp(t, 0.0, 1.0);
        switch (type) {
            case EASE_IN:
                return x * x * x;
            case EASE_OUT:
                return 1.0 - Math.pow(1.0 - x, 3.0);
            case EASE_IN_OUT:
                return x < 0.5 ? 4.0 * x * x * x : 1.0 - Math.pow(-2.0 * x + 2.0, 3.0) / 2.0;
            case EASE_OUT_BACK: {
                double c1 = 1.70158;
                double c3 = c1 + 1.0;
                return 1.0 + c3 * Math.pow(x - 1.0, 3.0) + c1 * Math.pow(x - 1.0, 2.0);
            }
            case EASE_OUT_EXPO:
                return x >= 1.0 ? 1.0 : 1.0 - Math.pow(2.0, -10.0 * x);
            case EASE_OUT_BOUNCE:
                return outBounce(x);
            case EASE_OUT_ELASTIC: {
                double c4 = (2.0 * Math.PI) / 3.0;
                if (x <= 0.0) {
                    return 0.0;
                }
                if (x >= 1.0) {
                    return 1.0;
                }
                return Math.pow(2.0, -10.0 * x) * Math.sin((x * 10.0 - 0.75) * c4) + 1.0;
            }
            case LINEAR:
            default:
                return x;
        }
    }

    private static double outBounce(double x) {
        double n1 = 7.5625;
        double d1 = 2.75;
        if (x < 1.0 / d1) {
            return n1 * x * x;
        }
        if (x < 2.0 / d1) {
            x -= 1.5 / d1;
            return n1 * x * x + 0.75;
        }
        if (x < 2.5 / d1) {
            x -= 2.25 / d1;
            return n1 * x * x + 0.9375;
        }
        x -= 2.625 / d1;
        return n1 * x * x + 0.984375;
    }
}
