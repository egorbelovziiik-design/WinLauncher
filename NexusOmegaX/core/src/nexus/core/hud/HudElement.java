package nexus.core.hud;

import nexus.core.Nexus;
import nexus.core.event.Render2DEvent;
import nexus.core.setting.BooleanSetting;
import nexus.core.setting.ColorSetting;
import nexus.core.setting.ModeSetting;
import nexus.core.setting.NumberSetting;
import nexus.core.setting.Setting;
import nexus.util.ColorUtil;
import nexus.util.Easing;
import nexus.util.MathUtil;
import nexus.util.json.Json;

import java.util.ArrayList;
import java.util.List;

/**
 * A single HUD widget.
 * <p>
 * Common settings (position, scale, color, font, shadow, blur, glow,
 * transparency, animations, background, border, gradient) live here so every
 * element behaves identically in the HUD editor.
 */
public abstract class HudElement {

    public static final String[] FONTS = new String[]{"Nexus Sans", "Minecraft", "Arial", "Consolas", "Verdana"};

    private final String id;
    private final String displayName;

    private final BooleanSetting enabled;
    private final NumberSetting scale;
    private final ColorSetting color;
    private final BooleanSetting textShadow;
    private final BooleanSetting background;
    private final ColorSetting backgroundColor;
    private final BooleanSetting border;
    private final ColorSetting borderColor;
    private final BooleanSetting glow;
    private final BooleanSetting blur;
    private final BooleanSetting gradient;
    private final ModeSetting font;
    private final BooleanSetting animated;
    private final NumberSetting backgroundPadding;

    private double x = 4.0;
    private double y = 4.0;
    private double animation = 0.0;
    private boolean editing;

    protected HudElement(String id, String displayName) {
        // nothing is enabled on a fresh install: the player decides what to show
        this(id, displayName, false);
    }

    protected HudElement(String id, String displayName, boolean enabledByDefault) {
        this.id = id;
        this.displayName = displayName;
        this.enabled = new BooleanSetting("Enabled", "Show this HUD element", enabledByDefault);
        this.scale = new NumberSetting("Scale", "Element scale", 1.0, 0.5, 3.0, 0.05);
        this.color = new ColorSetting("Color", "Primary color", 0xFFC084FC);
        this.textShadow = new BooleanSetting("Shadow", "Draw text shadow", true);
        this.background = new BooleanSetting("Background", "Draw a background plate", true);
        this.backgroundColor = new ColorSetting("Background Color", "Background plate color", 0x99101018);
        this.border = new BooleanSetting("Border", "Draw a border", false);
        this.borderColor = new ColorSetting("Border Color", "Border color", 0x66C084FC);
        this.glow = new BooleanSetting("Glow", "Neon glow", true);
        this.blur = new BooleanSetting("Blur", "Blur behind the element", false);
        this.gradient = new BooleanSetting("Gradient", "Gradient background", true);
        this.font = new ModeSetting("Font", "Text font", "Nexus Sans", FONTS);
        this.animated = new BooleanSetting("Animated", "Animate changes", true);
        this.backgroundPadding = new NumberSetting("Padding", "Background padding", 4.0, 0.0, 12.0, 1.0);
    }

    // ---------------------------------------------------------------- identity

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    // ---------------------------------------------------------------- position

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public void moveBy(double dx, double dy) {
        this.x += dx;
        this.y += dy;
    }

    /** Bounding box used by drag & drop, snapping and alignment guides. */
    public double width() {
        return 60.0 * scale.value();
    }

    public double height() {
        return 12.0 * scale.value();
    }

    public boolean contains(double pointX, double pointY) {
        return pointX >= x && pointX <= x + width() && pointY >= y && pointY <= y + height();
    }

    public boolean isEditing() {
        return editing;
    }

    public void setEditing(boolean value) {
        editing = value;
    }

    // ---------------------------------------------------------------- settings

    public boolean isEnabled() {
        return enabled.isEnabled();
    }

    public void setEnabled(boolean value) {
        enabled.set(Boolean.valueOf(value));
    }

    public List<Setting<?>> settings() {
        List<Setting<?>> list = new ArrayList<Setting<?>>();
        list.add(enabled);
        list.add(scale);
        list.add(color);
        list.add(textShadow);
        list.add(background);
        list.add(backgroundColor);
        list.add(border);
        list.add(borderColor);
        list.add(glow);
        list.add(blur);
        list.add(gradient);
        list.add(font);
        list.add(animated);
        list.add(backgroundPadding);
        return list;
    }

    public Setting<?> setting(String name) {
        for (Setting<?> setting : settings()) {
            if (setting.name().equalsIgnoreCase(name)) {
                return setting;
            }
        }
        return null;
    }

    public NumberSetting scaleSetting() {
        return scale;
    }

    public double scale() {
        return scale.value();
    }

    public ColorSetting colorSetting() {
        return color;
    }

    public int color(long timeMs) {
        return color.effectiveColor(timeMs, y / 100.0);
    }

    public boolean shadow() {
        return textShadow.isEnabled();
    }

    public String font() {
        return font.get();
    }

    // ----------------------------------------------------------------- drawing

    /**
     * Draws the element: background plate, optional blur / glow / border and the
     * content implemented by {@link #draw}.
     */
    public final void render(Render2DEvent event) {
        if (!isEnabled()) {
            animation = 0.0;
            return;
        }
        Nexus nexus = Nexus.instance();
        animation = animated.isEnabled()
                ? MathUtil.approach(animation, 1.0, 0.35, event.deltaMs)
                : 1.0;
        if (animation <= 0.01) {
            return;
        }
        double eased = Easing.ease(Easing.EASE_OUT, animation);
        double width = width() * eased;
        double height = height() * eased;

        if (blur.isEnabled()) {
            event.renderer.blurRegion(x, y, width, height, nexus.themes().blurStrength());
        }
        if (background.isEnabled()) {
            double padding = backgroundPadding.value();
            double bx = x - padding;
            double by = y - padding;
            double bw = width + padding * 2.0;
            double bh = height + padding * 2.0;
            double radius = Math.max(1.0, nexus.themes().cornerRadius() * 0.6);
            if (glow.isEnabled()) {
                event.renderer.shadow(bx, by, bw, bh, radius, 10.0 * nexus.themes().glowStrength(),
                        ColorUtil.withAlpha(nexus.themes().accent(System.currentTimeMillis()), 90));
            }
            int bg = ColorUtil.withAlpha(backgroundColor.color(), (int) (ColorUtil.alpha(backgroundColor.color()) * eased));
            if (gradient.isEnabled()) {
                event.renderer.fillGradientRound(bx, by, bw, bh, radius,
                        ColorUtil.blend(bg, nexus.themes().panelAlt(), 0.5), bg, false);
            } else {
                event.renderer.fillRoundRect(bx, by, bw, bh, radius, bg);
            }
            if (border.isEnabled()) {
                event.renderer.outlineRoundRect(bx, by, bw, bh, radius, 1.0,
                        ColorUtil.withAlpha(borderColor.color(), (int) (ColorUtil.alpha(borderColor.color()) * eased)));
            }
        }
        event.renderer.pushScissor(x - 40.0, y - 40.0, width + 80.0, height + 80.0);
        draw(event, x, y);
        event.renderer.popScissor();
        if (editing) {
            event.renderer.outlineRect(x - 2.0, y - 2.0, width + 4.0, height + 4.0, 1.0, 0xFFFFFFFF);
        }
    }

    /** Element specific drawing, in GUI coordinates, already scaled. */
    protected abstract void draw(Render2DEvent event, double x, double y);

    /** Called once per client tick for stateful elements (CPS, session, ...). */
    public void onTick() {
    }

    /** Safe text measurement (works even outside of a frame). */
    protected double textWidth(String text, double scale) {
        nexus.api.IRenderer renderer = Nexus.instance().render().renderer();
        return renderer == null ? text.length() * 5.4 * scale : renderer.textWidth(text, scale);
    }

    protected double fontHeight(double scale) {
        nexus.api.IRenderer renderer = Nexus.instance().render().renderer();
        return renderer == null ? 9.0 * scale : renderer.fontHeight(scale);
    }

    protected double lineHeight() {
        return fontHeight(scale()) + 2.0;
    }

    /** Preview text used by the HUD editor when the element has no data. */
    public String previewText() {
        return displayName;
    }

    // ------------------------------------------------------------------ config

    public Json toJson() {
        Json.JsonObject object = Json.object();
        object.set("x", x);
        object.set("y", y);
        Json.JsonObject values = Json.object();
        for (Setting<?> setting : settings()) {
            values.set(setting.name(), setting.toJson());
        }
        object.set("settings", values);
        return object;
    }

    public void fromJson(Json value) {
        if (!(value instanceof Json.JsonObject)) {
            return;
        }
        Json.JsonObject object = (Json.JsonObject) value;
        x = object.double_("x", x);
        y = object.double_("y", y);
        Json.JsonObject values = object.getObject("settings", Json.object());
        for (Setting<?> setting : settings()) {
            Json stored = values.get(setting.name());
            if (!stored.isNull()) {
                setting.fromJson(stored);
            }
        }
    }
}
