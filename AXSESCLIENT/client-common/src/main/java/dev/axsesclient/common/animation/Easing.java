package dev.axsesclient.common.animation;

public enum Easing {
    LINEAR {
        @Override
        public float apply(float t) {
            return clamp01(t);
        }
    },
    EASE_OUT_CUBIC {
        @Override
        public float apply(float t) {
            float x = 1.0f - clamp01(t);
            return 1.0f - x * x * x;
        }
    },
    EASE_IN_OUT_CUBIC {
        @Override
        public float apply(float t) {
            float x = clamp01(t);
            return x < 0.5f ? 4.0f * x * x * x : 1.0f - (float) Math.pow(-2.0 * x + 2.0, 3.0) / 2.0f;
        }
    },
    EASE_OUT_QUINT {
        @Override
        public float apply(float t) {
            float x = 1.0f - clamp01(t);
            return 1.0f - x * x * x * x * x;
        }
    },
    EASE_OUT_BACK {
        @Override
        public float apply(float t) {
            float x = clamp01(t);
            float c1 = 1.70158f;
            float c3 = c1 + 1.0f;
            float p = x - 1.0f;
            return 1.0f + c3 * p * p * p + c1 * p * p;
        }
    },
    SPRING_SMOOTH {
        @Override
        public float apply(float t) {
            float x = clamp01(t);
            return 1.0f - (float) (Math.exp(-6.0 * x) * Math.cos(6.5 * x));
        }
    };

    public abstract float apply(float t);

    protected static float clamp01(float v) {
        return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
    }
}
