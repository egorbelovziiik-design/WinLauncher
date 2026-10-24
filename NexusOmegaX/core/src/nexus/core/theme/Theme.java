package nexus.core.theme;

import nexus.util.ColorUtil;
import nexus.util.json.Json;

/**
 * Complete visual identity of the client: palette, gradients, blur, glow,
 * corner radius, animation speed and font.
 */
public final class Theme {

    private final String id;
    private final String name;

    private int primary = 0xFF7C3AED;
    private int secondary = 0xFF9333EA;
    private int accent = 0xFFA855F7;
    private int highlight = 0xFFC084FC;
    private int background = 0xE608080D;
    private int panel = 0xF2101018;
    private int panelAlt = 0xF2181824;
    private int outline = 0x40C084FC;
    private int text = 0xFFF5F3FF;
    private int textDim = 0x99C4B5FD;
    private int success = 0xFF4ADE80;
    private int warning = 0xFFFBBF24;
    private int error = 0xFFF87171;

    private boolean gradient = true;
    private boolean rainbowAccent = false;
    private double rainbowSpeed = 0.35;
    private double blurStrength = 1.0;
    private double glowStrength = 1.0;
    private double shadowStrength = 0.75;
    private double cornerRadius = 8.0;
    private double transparency = 0.92;
    private double animationSpeed = 1.0;
    private int animationEasing = 3;
    private String font = "Nexus Sans";
    private boolean textShadow = true;
    private boolean neonGlow = true;

    public Theme(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public Theme(String id, String name, int primary, int secondary, int accent, int highlight,
                 int background, int panel, int panelAlt, int text, int textDim) {
        this(id, name);
        this.primary = primary;
        this.secondary = secondary;
        this.accent = accent;
        this.highlight = highlight;
        this.background = background;
        this.panel = panel;
        this.panelAlt = panelAlt;
        this.text = text;
        this.textDim = textDim;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    // -------------------------------------------------------------- palette

    public int primary(long timeMs) {
        return rainbowAccent ? ColorUtil.rainbow(timeMs, rainbowSpeed, 0.75f, 1.0f, ColorUtil.alpha(primary)) : primary;
    }

    public int secondary() {
        return secondary;
    }

    public int accent(long timeMs) {
        return rainbowAccent ? ColorUtil.rainbow(timeMs + 900L, rainbowSpeed, 0.8f, 1.0f, ColorUtil.alpha(accent)) : accent;
    }

    public int highlight() {
        return highlight;
    }

    public int background() {
        return background;
    }

    public int panel() {
        return panel;
    }

    public int panelAlt() {
        return panelAlt;
    }

    public int outline() {
        return outline;
    }

    public int text() {
        return text;
    }

    public int textDim() {
        return textDim;
    }

    public int success() {
        return success;
    }

    public int warning() {
        return warning;
    }

    public int error() {
        return error;
    }

    // -------------------------------------------------------------- modifiers

    public boolean gradient() {
        return gradient;
    }

    public void setGradient(boolean value) {
        gradient = value;
    }

    public boolean rainbowAccent() {
        return rainbowAccent;
    }

    public void setRainbowAccent(boolean value) {
        rainbowAccent = value;
    }

    public double rainbowSpeed() {
        return rainbowSpeed;
    }

    public void setRainbowSpeed(double value) {
        rainbowSpeed = value;
    }

    public double blurStrength() {
        return blurStrength;
    }

    public void setBlurStrength(double value) {
        blurStrength = value;
    }

    public double glowStrength() {
        return glowStrength;
    }

    public void setGlowStrength(double value) {
        glowStrength = value;
    }

    public double shadowStrength() {
        return shadowStrength;
    }

    public double cornerRadius() {
        return cornerRadius;
    }

    public void setCornerRadius(double value) {
        cornerRadius = value;
    }

    public double transparency() {
        return transparency;
    }

    public void setTransparency(double value) {
        transparency = value;
    }

    public double animationSpeed() {
        return animationSpeed;
    }

    public void setAnimationSpeed(double value) {
        animationSpeed = value;
    }

    public int animationEasing() {
        return animationEasing;
    }

    public void setAnimationEasing(int value) {
        animationEasing = value;
    }

    public String font() {
        return font;
    }

    public void setFont(String value) {
        font = value;
    }

    public boolean textShadow() {
        return textShadow;
    }

    public void setTextShadow(boolean value) {
        textShadow = value;
    }

    public boolean neonGlow() {
        return neonGlow;
    }

    public void setNeonGlow(boolean value) {
        neonGlow = value;
    }

    // ----------------------------------------------------------------- config

    public Json toJson() {
        Json.JsonObject object = Json.object();
        Json.JsonObject colors = Json.object();
        colors.set("primary", ColorUtil.toHex(primary));
        colors.set("secondary", ColorUtil.toHex(secondary));
        colors.set("accent", ColorUtil.toHex(accent));
        colors.set("highlight", ColorUtil.toHex(highlight));
        colors.set("background", ColorUtil.toHex(background));
        colors.set("panel", ColorUtil.toHex(panel));
        colors.set("panelAlt", ColorUtil.toHex(panelAlt));
        colors.set("outline", ColorUtil.toHex(outline));
        colors.set("text", ColorUtil.toHex(text));
        colors.set("textDim", ColorUtil.toHex(textDim));
        colors.set("success", ColorUtil.toHex(success));
        colors.set("warning", ColorUtil.toHex(warning));
        colors.set("error", ColorUtil.toHex(error));
        object.set("colors", colors);
        object.set("gradient", gradient);
        object.set("rainbowAccent", rainbowAccent);
        object.set("rainbowSpeed", rainbowSpeed);
        object.set("blurStrength", blurStrength);
        object.set("glowStrength", glowStrength);
        object.set("shadowStrength", shadowStrength);
        object.set("cornerRadius", cornerRadius);
        object.set("transparency", transparency);
        object.set("animationSpeed", animationSpeed);
        object.set("animationEasing", animationEasing);
        object.set("font", font);
        object.set("textShadow", textShadow);
        object.set("neonGlow", neonGlow);
        return object;
    }

    public void fromJson(Json value) {
        if (!(value instanceof Json.JsonObject)) {
            return;
        }
        Json.JsonObject object = (Json.JsonObject) value;
        Json.JsonObject colors = object.getObject("colors", null);
        if (colors != null) {
            primary = ColorUtil.parseHex(colors.string("primary", ColorUtil.toHex(primary)), primary);
            secondary = ColorUtil.parseHex(colors.string("secondary", ColorUtil.toHex(secondary)), secondary);
            accent = ColorUtil.parseHex(colors.string("accent", ColorUtil.toHex(accent)), accent);
            highlight = ColorUtil.parseHex(colors.string("highlight", ColorUtil.toHex(highlight)), highlight);
            background = ColorUtil.parseHex(colors.string("background", ColorUtil.toHex(background)), background);
            panel = ColorUtil.parseHex(colors.string("panel", ColorUtil.toHex(panel)), panel);
            panelAlt = ColorUtil.parseHex(colors.string("panelAlt", ColorUtil.toHex(panelAlt)), panelAlt);
            outline = ColorUtil.parseHex(colors.string("outline", ColorUtil.toHex(outline)), outline);
            text = ColorUtil.parseHex(colors.string("text", ColorUtil.toHex(text)), text);
            textDim = ColorUtil.parseHex(colors.string("textDim", ColorUtil.toHex(textDim)), textDim);
            success = ColorUtil.parseHex(colors.string("success", ColorUtil.toHex(success)), success);
            warning = ColorUtil.parseHex(colors.string("warning", ColorUtil.toHex(warning)), warning);
            error = ColorUtil.parseHex(colors.string("error", ColorUtil.toHex(error)), error);
        }
        gradient = object.bool("gradient", gradient);
        rainbowAccent = object.bool("rainbowAccent", rainbowAccent);
        rainbowSpeed = object.double_("rainbowSpeed", rainbowSpeed);
        blurStrength = object.double_("blurStrength", blurStrength);
        glowStrength = object.double_("glowStrength", glowStrength);
        shadowStrength = object.double_("shadowStrength", shadowStrength);
        cornerRadius = object.double_("cornerRadius", cornerRadius);
        transparency = object.double_("transparency", transparency);
        animationSpeed = object.double_("animationSpeed", animationSpeed);
        animationEasing = object.int_("animationEasing", animationEasing);
        font = object.string("font", font);
        textShadow = object.bool("textShadow", textShadow);
        neonGlow = object.bool("neonGlow", neonGlow);
    }

    public Theme copy(String newId, String newName) {
        Theme copy = new Theme(newId, newName);
        copy.fromJson(toJson());
        return copy;
    }

    @Override
    public String toString() {
        return name;
    }
}
