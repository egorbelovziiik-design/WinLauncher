package dev.axsesclient.common.animation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AnimationManager {
    private long lastFrameNanos = System.nanoTime();
    private float deltaSeconds = 1.0f / 60.0f;
    private float animationSpeed = 1.0f;
    private float animationIntensity = 1.0f;
    private boolean reducedMotion = false;
    private Easing defaultEasing = Easing.EASE_OUT_CUBIC;

    private final Map<String, AnimatedFloat> channels = new ConcurrentHashMap<String, AnimatedFloat>();

    public float beginFrame() {
        long now = System.nanoTime();
        long diff = now - lastFrameNanos;
        lastFrameNanos = now;
        if (diff <= 0L) {
            deltaSeconds = 0.0005f;
        } else {
            deltaSeconds = Math.min(0.10f, diff / 1_000_000_000.0f);
        }
        return deltaSeconds;
    }

    public float beginFrameExplicit(float explicitDeltaSeconds) {
        this.deltaSeconds = Math.max(0.0001f, Math.min(0.10f, explicitDeltaSeconds));
        this.lastFrameNanos = System.nanoTime();
        return this.deltaSeconds;
    }

    public AnimatedFloat channel(String key, float initialValue, float durationSeconds) {
        AnimatedFloat existing = channels.get(key);
        if (existing == null) {
            existing = new AnimatedFloat(initialValue, durationSeconds, defaultEasing);
            channels.put(key, existing);
        }
        return existing;
    }

    public float animate(String key, float target, float durationSeconds) {
        AnimatedFloat ch = channel(key, target, durationSeconds);
        ch.setDurationSeconds(durationSeconds);
        ch.setEasing(defaultEasing);
        ch.setTarget(target);
        return ch.update(deltaSeconds, animationSpeed, reducedMotion);
    }

    public float fade(String key, boolean visible, float durationSeconds) {
        return animate("fade:" + key, visible ? 1.0f : 0.0f, durationSeconds);
    }

    public float slide(String key, float targetOffsetPx, float durationSeconds) {
        if (reducedMotion) {
            return 0.0f;
        }
        return animate("slide:" + key, targetOffsetPx * animationIntensity, durationSeconds);
    }

    public float scale(String key, boolean active, float baseScale, float activeDelta, float durationSeconds) {
        float target = active ? baseScale + activeDelta * animationIntensity : baseScale;
        return animate("scale:" + key, target, durationSeconds);
    }

    public float smoothHover(String key, boolean hovered) {
        return animate("hover:" + key, hovered ? 1.0f : 0.0f, 0.14f);
    }

    public float smoothToggle(String key, boolean enabled) {
        return animate("toggle:" + key, enabled ? 1.0f : 0.0f, 0.18f);
    }

    public float smoothScroll(String key, float targetScroll) {
        AnimatedFloat ch = channel("scroll:" + key, targetScroll, 0.12f);
        if (reducedMotion) {
            ch.setImmediate(targetScroll);
            return targetScroll;
        }
        float damped = AnimatedFloat.damp(ch.getValue(), targetScroll, 18.0f, deltaSeconds, animationSpeed, false);
        ch.setImmediate(damped);
        return damped;
    }

    public float pageTransition(String pageId) {
        AnimatedFloat ch = new AnimatedFloat(0.0f, 0.24f, Easing.EASE_OUT_CUBIC);
        ch.setTarget(1.0f);
        channels.put("page:active", ch);
        return ch.update(deltaSeconds, animationSpeed, reducedMotion);
    }

    public float getActivePageProgress() {
        AnimatedFloat ch = channels.get("page:active");
        if (ch == null) {
            return 1.0f;
        }
        return ch.update(deltaSeconds, animationSpeed, reducedMotion);
    }

    public float getDeltaSeconds() {
        return deltaSeconds;
    }

    public float getAnimationSpeed() {
        return animationSpeed;
    }

    public void setAnimationSpeed(float animationSpeed) {
        if (!Float.isNaN(animationSpeed) && !Float.isInfinite(animationSpeed)) {
            this.animationSpeed = Math.max(0.25f, Math.min(3.0f, animationSpeed));
        }
    }

    public float getAnimationIntensity() {
        return animationIntensity;
    }

    public void setAnimationIntensity(float animationIntensity) {
        if (!Float.isNaN(animationIntensity) && !Float.isInfinite(animationIntensity)) {
            this.animationIntensity = Math.max(0.0f, Math.min(1.0f, animationIntensity));
        }
    }

    public boolean isReducedMotion() {
        return reducedMotion;
    }

    public void setReducedMotion(boolean reducedMotion) {
        this.reducedMotion = reducedMotion;
    }

    public Easing getDefaultEasing() {
        return defaultEasing;
    }

    public void setDefaultEasing(Easing defaultEasing) {
        if (defaultEasing != null) {
            this.defaultEasing = defaultEasing;
        }
    }

    public void reset() {
        this.animationSpeed = 1.0f;
        this.animationIntensity = 1.0f;
        this.reducedMotion = false;
        this.defaultEasing = Easing.EASE_OUT_CUBIC;
        this.channels.clear();
    }
}
