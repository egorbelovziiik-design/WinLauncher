package nexus.core.notification;

import nexus.api.IRenderer;
import nexus.core.Nexus;
import nexus.core.event.Render2DEvent;
import nexus.core.setting.BooleanSetting;
import nexus.core.setting.ColorSetting;
import nexus.core.setting.ModeSetting;
import nexus.core.setting.NumberSetting;
import nexus.util.ColorUtil;
import nexus.util.Easing;
import nexus.util.MathUtil;
import nexus.util.json.Json;

import java.util.ArrayList;
import java.util.List;

/**
 * Toast notification engine with slide animations, stacking, sounds and a fully
 * configurable look (position, duration, size, opacity, animation, sound).
 */
public final class NotificationManager {

    private final Nexus nexus;
    private final List<Notification> queue = new ArrayList<Notification>();
    private final List<Notification> active = new ArrayList<Notification>();

    private final ModeSetting position;
    private final NumberSetting duration;
    private final BooleanSetting sound;
    private final BooleanSetting animations;
    private final NumberSetting width;
    private final NumberSetting opacity;
    private final NumberSetting maxVisible;
    private final ColorSetting background;
    private final BooleanSetting enabled;
    private final NumberSetting scale;

    public NotificationManager(Nexus nexus) {
        this.nexus = nexus;
        this.enabled = new BooleanSetting("Notifications", "Show on-screen notifications", true);
        this.position = new ModeSetting("Position", "Corner of the screen", "TopRight",
                "TopRight", "TopLeft", "BottomRight", "BottomLeft", "TopCenter", "BottomCenter");
        this.duration = new NumberSetting("Duration", "Milliseconds a notification stays visible",
                3200.0, 800.0, 12000.0, 100.0, true, "ms");
        this.sound = new BooleanSetting("Sound", "Play a sound when a notification appears", false);
        this.animations = new BooleanSetting("Animated", "Slide and fade notifications", true);
        this.width = new NumberSetting("Width", "Notification width in GUI pixels", 190.0, 120.0, 420.0, 1.0, true, "px");
        this.opacity = new NumberSetting("Opacity", "Background opacity 0..1", 0.92, 0.1, 1.0, 0.01);
        this.maxVisible = new NumberSetting("Max Visible", "Maximum notifications shown at once", 4.0, 1.0, 10.0, 1.0, true, "");
        this.scale = new NumberSetting("Scale", "Text scale", 1.0, 0.6, 2.0, 0.05);
        this.background = new ColorSetting("Background", "Notification background color", 0xE6101018);
    }

    public boolean isEnabled() {
        return enabled.isEnabled();
    }

    // ----------------------------------------------------------------- pushing

    public void push(Notification.Type type, String title, String message) {
        if (!enabled.isEnabled()) {
            return;
        }
        long life = (long) duration.value();
        queue.add(new Notification(type, title, message, life, sound.isEnabled()));
        while (queue.size() + active.size() > (int) maxVisible.value() + 4 && !queue.isEmpty()) {
            queue.remove(0);
        }
        if (sound.isEnabled() && nexus.isInitializedInstance()) {
            nexus.client().resources().playSound("ui.toast.in", 0.6f, 1.4f);
        }
    }

    public void info(String title, String message) {
        push(Notification.Type.INFO, title, message);
    }

    public void success(String title, String message) {
        push(Notification.Type.SUCCESS, title, message);
    }

    public void warn(String title, String message) {
        push(Notification.Type.WARNING, title, message);
    }

    public void error(String title, String message) {
        push(Notification.Type.ERROR, title, message);
    }

    public void moduleEnabled(String module) {
        push(Notification.Type.MODULE_ENABLED, "Module enabled", module);
    }

    public void moduleDisabled(String module) {
        push(Notification.Type.MODULE_DISABLED, "Module disabled", module);
    }

    public void configSaved(String name) {
        push(Notification.Type.CONFIG_SAVED, "Config saved", name);
    }

    public void configLoaded(String name) {
        push(Notification.Type.CONFIG_LOADED, "Config loaded", name);
    }

    // ------------------------------------------------------------------ update

    public void onTick() {
        long now = System.currentTimeMillis();
        while (!queue.isEmpty() && active.size() < (int) maxVisible.value()) {
            Notification next = queue.remove(0);
            next.start(now);
            active.add(next);
        }
        for (int i = active.size() - 1; i >= 0; i--) {
            if (active.get(i).isExpired(now)) {
                active.remove(i);
            }
        }
    }

    public void render(Render2DEvent event) {
        if (!enabled.isEnabled() || active.isEmpty()) {
            return;
        }
        IRenderer renderer = event.renderer;
        long now = System.currentTimeMillis();
        double boxWidth = width.value();
        double boxHeight = 34.0;
        double gap = 6.0;
        double margin = 10.0;
        String pos = position.get();
        boolean top = pos.startsWith("Top");
        boolean right = pos.endsWith("Right");
        boolean center = pos.endsWith("Center");

        double baseX = center ? (event.width - boxWidth) / 2.0 : (right ? event.width - boxWidth - margin : margin);
        double baseY = top ? margin : event.height - margin - boxHeight;

        for (int i = 0; i < active.size(); i++) {
            Notification notification = active.get(i);
            double anim = animations.isEnabled() ? Easing.ease(Easing.EASE_OUT_EXPO, notification.animation(now)) : 1.0;
            double offset = (boxHeight + gap) * i;
            double y = top ? baseY + offset : baseY - offset;
            double x = baseX;
            if (anim < 1.0) {
                double slide = (1.0 - anim) * boxWidth * 0.6;
                x += right ? slide : (center ? 0.0 : -slide);
            }
            int accent = colorFor(notification.type, now);
            int bg = ColorUtil.withAlpha(background.color(), (int) (ColorUtil.alpha(background.color()) * opacity.value() * anim));

            renderer.shadow(x, y, boxWidth, boxHeight, 6.0, 8.0, 0x66000000);
            renderer.fillRoundRect(x, y, boxWidth, boxHeight, 6.0, bg);
            renderer.fillRoundRect(x, y, 3.0, boxHeight, 1.5, ColorUtil.withAlpha(accent, (int) (255 * anim)));

            double textX = x + 9.0;
            double textScale = 0.85 * scale.value();
            renderer.drawText(notification.title, textX, y + 6.0,
                    ColorUtil.withAlpha(accent, (int) (255 * anim)), false, textScale);
            renderer.drawText(notification.message, textX, y + 18.0,
                    ColorUtil.withAlpha(nexus.themes().textDim(), (int) (255 * anim)), false, textScale * 0.92);

            // progress bar
            double progress = MathUtil.clamp(notification.remaining(now), 0.0, 1.0);
            renderer.fillRect(x, y + boxHeight - 1.5, boxWidth * progress, 1.5,
                    ColorUtil.withAlpha(accent, (int) (200 * anim)));
        }
    }

    private int colorFor(Notification.Type type, long now) {
        switch (type) {
            case SUCCESS:
                return nexus.themes().success();
            case WARNING:
                return nexus.themes().warning();
            case ERROR:
                return nexus.themes().error();
            case MODULE_ENABLED:
                return nexus.themes().primary(now);
            case MODULE_DISABLED:
                return nexus.themes().textDim();
            case CONFIG_SAVED:
            case CONFIG_LOADED:
                return nexus.themes().accent(now);
            case CLIENT_UPDATED:
            case MINECRAFT_CONNECTED:
                return nexus.themes().success();
            default:
                return nexus.themes().accent(now);
        }
    }

    /** Width of the notification stack (used by the HUD editor anchor). */
    public double stackWidth() {
        return width.value();
    }

    public double stackHeight() {
        int count = Math.max(1, active.size());
        return count * (34.0 + 6.0);
    }

    /** Draws the stack at an explicit position (HUD editor / Notifications element). */
    public void renderAt(Render2DEvent event, double x, double y, double overrideWidth) {
        if (!enabled.isEnabled() || active.isEmpty()) {
            return;
        }
        IRenderer renderer = event.renderer;
        long now = System.currentTimeMillis();
        double boxWidth = overrideWidth > 0 ? overrideWidth : width.value();
        double boxHeight = 34.0;
        double gap = 6.0;
        for (int i = 0; i < active.size(); i++) {
            Notification notification = active.get(i);
            double anim = animations.isEnabled() ? Easing.ease(Easing.EASE_OUT_EXPO, notification.animation(now)) : 1.0;
            double itemY = y + i * (boxHeight + gap);
            int accent = colorFor(notification.type, now);
            int bg = ColorUtil.withAlpha(background.color(), (int) (ColorUtil.alpha(background.color()) * opacity.value() * anim));
            renderer.shadow(x, itemY, boxWidth, boxHeight, 6.0, 8.0, 0x66000000);
            renderer.fillRoundRect(x, itemY, boxWidth, boxHeight, 6.0, bg);
            renderer.fillRoundRect(x, itemY, 3.0, boxHeight, 1.5, ColorUtil.withAlpha(accent, (int) (255 * anim)));
            double textScale = 0.85 * scale.value();
            renderer.drawText(notification.title, x + 9.0, itemY + 6.0,
                    ColorUtil.withAlpha(accent, (int) (255 * anim)), false, textScale);
            renderer.drawText(notification.message, x + 9.0, itemY + 18.0,
                    ColorUtil.withAlpha(nexus.themes().textDim(), (int) (255 * anim)), false, textScale * 0.92);
            double progress = MathUtil.clamp(notification.remaining(now), 0.0, 1.0);
            renderer.fillRect(x, itemY + boxHeight - 1.5, boxWidth * progress, 1.5,
                    ColorUtil.withAlpha(accent, (int) (200 * anim)));
        }
    }

    public int activeCount() {
        return active.size() + queue.size();
    }

    public void clear() {
        queue.clear();
        active.clear();
    }

    // ------------------------------------------------------------------ config

    public Json toJson() {
        Json.JsonObject object = Json.object();
        object.set("enabled", enabled.toJson());
        object.set("position", position.toJson());
        object.set("duration", duration.toJson());
        object.set("sound", sound.toJson());
        object.set("animations", animations.toJson());
        object.set("width", width.toJson());
        object.set("opacity", opacity.toJson());
        object.set("maxVisible", maxVisible.toJson());
        object.set("scale", scale.toJson());
        object.set("background", background.toJson());
        return object;
    }

    public void fromJson(Json value) {
        if (!(value instanceof Json.JsonObject)) {
            return;
        }
        Json.JsonObject object = (Json.JsonObject) value;
        enabled.fromJson(object.get("enabled"));
        position.fromJson(object.get("position"));
        duration.fromJson(object.get("duration"));
        sound.fromJson(object.get("sound"));
        animations.fromJson(object.get("animations"));
        width.fromJson(object.get("width"));
        opacity.fromJson(object.get("opacity"));
        maxVisible.fromJson(object.get("maxVisible"));
        scale.fromJson(object.get("scale"));
        background.fromJson(object.get("background"));
    }

    public List<nexus.core.setting.Setting<?>> settings() {
        List<nexus.core.setting.Setting<?>> list = new ArrayList<nexus.core.setting.Setting<?>>();
        list.add(enabled);
        list.add(position);
        list.add(duration);
        list.add(sound);
        list.add(animations);
        list.add(width);
        list.add(opacity);
        list.add(maxVisible);
        list.add(scale);
        list.add(background);
        return list;
    }
}
