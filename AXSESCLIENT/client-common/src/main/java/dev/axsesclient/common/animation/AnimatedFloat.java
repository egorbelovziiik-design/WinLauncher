package dev.axsesclient.common.animation;

public final class AnimatedFloat {
    private float startValue;
    private float currentValue;
    private float targetValue;
    private float elapsedSeconds;
    private float durationSeconds;
    private Easing easing;

    public AnimatedFloat(float initialValue, float durationSeconds, Easing easing) {
        this.startValue = initialValue;
        this.currentValue = initialValue;
        this.targetValue = initialValue;
        this.durationSeconds = Math.max(0.01f, durationSeconds);
        this.easing = easing == null ? Easing.EASE_OUT_CUBIC : easing;
        this.elapsedSeconds = this.durationSeconds;
    }

    public void setImmediate(float value) {
        this.startValue = value;
        this.currentValue = value;
        this.targetValue = value;
        this.elapsedSeconds = this.durationSeconds;
    }

    public void setTarget(float newTarget) {
        if (Math.abs(this.targetValue - newTarget) > 1e-5f) {
            this.startValue = this.currentValue;
            this.targetValue = newTarget;
            this.elapsedSeconds = 0.0f;
        }
    }

    public void setDurationSeconds(float durationSeconds) {
        this.durationSeconds = Math.max(0.01f, durationSeconds);
    }

    public void setEasing(Easing easing) {
        if (easing != null) {
            this.easing = easing;
        }
    }

    public float update(float deltaSeconds, float speedMultiplier, boolean reducedMotion) {
        if (reducedMotion || durationSeconds <= 0.011f) {
            currentValue = targetValue;
            elapsedSeconds = durationSeconds;
            return currentValue;
        }
        if (Math.abs(currentValue - targetValue) <= 1e-5f && elapsedSeconds >= durationSeconds) {
            currentValue = targetValue;
            return currentValue;
        }
        float effectiveDelta = Math.max(0.0f, deltaSeconds) * Math.max(0.25f, Math.min(3.0f, speedMultiplier));
        elapsedSeconds = Math.min(durationSeconds, elapsedSeconds + effectiveDelta);
        float progress = elapsedSeconds / durationSeconds;
        float eased = easing.apply(progress);
        currentValue = startValue + (targetValue - startValue) * eased;
        if (progress >= 1.0f) {
            currentValue = targetValue;
        }
        return currentValue;
    }

    /**
     * Smooth exponential damping for continuous mouse hover, scroll, and zoom tracking.
     */
    public static float damp(float current, float target, float sharpness, float deltaSeconds, float speedMultiplier, boolean reducedMotion) {
        if (reducedMotion || deltaSeconds <= 0.0f) {
            return reducedMotion ? target : current;
        }
        float factor = 1.0f - (float) Math.exp(-Math.max(0.5f, sharpness * speedMultiplier) * deltaSeconds);
        return current + (target - current) * factor;
    }

    public float getValue() {
        return currentValue;
    }

    public float getTarget() {
        return targetValue;
    }

    public boolean isAnimating() {
        return Math.abs(currentValue - targetValue) > 1e-4f;
    }
}
