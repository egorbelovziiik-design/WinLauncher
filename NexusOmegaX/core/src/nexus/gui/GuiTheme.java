package nexus.gui;

import nexus.api.IRenderer;
import nexus.core.Nexus;
import nexus.util.ColorUtil;
import nexus.util.MathUtil;

/**
 * Shared drawing primitives for every Nexus interface.
 * <p>
 * Everything is themed: colors come from the active {@link nexus.core.theme.Theme},
 * corner radius and glow strength from the render manager, so all screens look
 * consistent and follow the theme editor live.
 */
public final class GuiTheme {

    private GuiTheme() {
    }

    private static Nexus nexus() {
        return Nexus.instance();
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ panels

    public static void panel(IRenderer renderer, double x, double y, double width, double height) {
        double radius = nexus().themes().cornerRadius();
        panel(renderer, x, y, width, height, radius);
    }

    public static void panel(IRenderer renderer, double x, double y, double width, double height, double radius) {
        int panel = nexus().themes().panel();
        int panelAlt = nexus().themes().panelAlt();
        shadow(renderer, x, y, width, height, radius);
        if (nexus().themes().gradient()) {
            renderer.fillGradientRound(x, y, width, height, radius,
                    ColorUtil.blend(panelAlt, panel, 0.35), panel, false);
        } else {
            renderer.fillRoundRect(x, y, width, height, radius, panel);
        }
        renderer.outlineRoundRect(x, y, width, height, radius, 1.0, nexus().themes().outline());
    }

    public static void shadow(IRenderer renderer, double x, double y, double width, double height, double radius) {
        if (nexus().themes().glowStrength() <= 0.0) {
            return;
        }
        renderer.shadow(x, y, width, height, radius, 12.0 * nexus().themes().glowStrength(),
                ColorUtil.withAlpha(nexus().themes().accent(now()), 70));
    }

    public static void titleBar(IRenderer renderer, double x, double y, double width, String title, double height) {
        double radius = nexus().themes().cornerRadius();
        int accent = nexus().themes().accent(now());
        renderer.fillGradientRound(x, y, width, height, radius,
                ColorUtil.withAlpha(nexus().themes().primary(now()), 190),
                ColorUtil.withAlpha(accent, 150), true);
        renderer.drawText(title.toUpperCase(), x + 8.0, y + height / 2.0 - renderer.fontHeight(1.0) / 2.0,
                nexus().themes().text(), false, 0.92);
    }

    // ----------------------------------------------------------------- buttons

    public static void button(IRenderer renderer, double x, double y, double width, double height,
                              String label, boolean hovered, boolean active) {
        double radius = Math.max(3.0, nexus().themes().cornerRadius() * 0.6);
        int accent = nexus().themes().accent(now());
        int base = active ? ColorUtil.withAlpha(accent, 190)
                : (hovered ? ColorUtil.withAlpha(nexus().themes().panelAlt(), 235)
                : ColorUtil.withAlpha(nexus().themes().panelAlt(), 170));
        renderer.fillRoundRect(x, y, width, height, radius, base);
        if (active && nexus().themes().neonGlow()) {
            renderer.outlineRoundRect(x, y, width, height, radius, 1.0, ColorUtil.brighten(accent, 0.4));
        } else {
            renderer.outlineRoundRect(x, y, width, height, radius, 0.75,
                    ColorUtil.withAlpha(accent, hovered ? 140 : 70));
        }
        renderer.drawCenteredText(label, x + width / 2.0, y + height / 2.0 - renderer.fontHeight(1.0) / 2.0,
                active ? 0xFFFFFFFF : (hovered ? nexus().themes().text() : nexus().themes().textDim()), false, 0.9);
    }

    public static void toggle(IRenderer renderer, double x, double y, double width, double height,
                              boolean enabled, double animation) {
        double radius = height / 2.0;
        int accent = nexus().themes().accent(now());
        int track = enabled
                ? ColorUtil.blend(ColorUtil.withAlpha(nexus().themes().panelAlt(), 255), accent, MathUtil.clamp(animation, 0.0, 1.0))
                : ColorUtil.blend(accent, ColorUtil.withAlpha(nexus().themes().panelAlt(), 255), MathUtil.clamp(animation, 0.0, 1.0));
        renderer.fillRoundRect(x, y, width, height, radius, track);
        double knobSize = height - 4.0;
        double travel = width - height;
        double knobX = x + 2.0 + travel * MathUtil.clamp(enabled ? animation : 1.0 - animation, 0.0, 1.0);
        renderer.circle(knobX + knobSize / 2.0, y + height / 2.0, knobSize / 2.0,
                enabled ? 0xFFFFFFFF : ColorUtil.withAlpha(nexus().themes().textDim(), 220));
    }

    public static void slider(IRenderer renderer, double x, double y, double width, double height,
                              double progress, boolean hovered, String label) {
        int accent = nexus().themes().accent(now());
        renderer.fillRoundRect(x, y, width, height, height / 2.0,
                ColorUtil.withAlpha(nexus().themes().panelAlt(), 220));
        double filled = MathUtil.clamp(progress, 0.0, 1.0) * width;
        renderer.fillRoundRect(x, y, Math.max(height, filled), height, height / 2.0,
                nexus().themes().gradient() ? accent : ColorUtil.withAlpha(accent, 220));
        double knobX = x + filled;
        renderer.circle(knobX, y + height / 2.0, (hovered ? height * 0.75 : height * 0.6), 0xFFFFFFFF);
        if (label != null && !label.isEmpty()) {
            renderer.drawText(label, x + width + 6.0, y + height / 2.0 - renderer.fontHeight(0.85) / 2.0,
                    nexus().themes().textDim(), false, 0.85);
        }
    }

    public static void scrollbar(IRenderer renderer, double x, double y, double height,
                                 double contentHeight, double viewHeight, double scroll) {
        if (contentHeight <= viewHeight) {
            return;
        }
        double trackHeight = viewHeight;
        double thumb = Math.max(12.0, trackHeight * (viewHeight / contentHeight));
        double travel = trackHeight - thumb;
        double maxScroll = contentHeight - viewHeight;
        double offset = maxScroll <= 0 ? 0.0 : (MathUtil.clamp(scroll, 0.0, maxScroll) / maxScroll) * travel;
        renderer.fillRoundRect(x, y, 3.0, trackHeight, 1.5, ColorUtil.withAlpha(0xFFFFFFFF, 25));
        renderer.fillRoundRect(x, y + offset, 3.0, thumb, 1.5,
                ColorUtil.withAlpha(nexus().themes().accent(now()), 200));
    }

    public static void searchBox(IRenderer renderer, double x, double y, double width, double height,
                                 String text, boolean focused) {
        double radius = Math.max(3.0, nexus().themes().cornerRadius() * 0.5);
        int accent = nexus().themes().accent(now());
        renderer.fillRoundRect(x, y, width, height, radius, ColorUtil.withAlpha(0xFF000000, 110));
        renderer.outlineRoundRect(x, y, width, height, radius, 1.0,
                ColorUtil.withAlpha(accent, focused ? 220 : 80));
        String display = text.isEmpty() && !focused ? "Search modules..." : text + (focused ? "_" : "");
        renderer.drawText(display, x + 6.0, y + height / 2.0 - renderer.fontHeight(0.9) / 2.0,
                text.isEmpty() && !focused ? ColorUtil.withAlpha(nexus().themes().textDim(), 170)
                        : nexus().themes().text(), false, 0.9);
    }

    public static void chip(IRenderer renderer, double x, double y, double width, double height,
                            String label, boolean selected) {
        int accent = nexus().themes().accent(now());
        int bg = selected ? ColorUtil.withAlpha(accent, 190) : ColorUtil.withAlpha(nexus().themes().panelAlt(), 190);
        renderer.fillRoundRect(x, y, width, height, height / 2.0, bg);
        renderer.outlineRoundRect(x, y, width, height, height / 2.0, 0.75,
                ColorUtil.withAlpha(accent, selected ? 255 : 70));
        renderer.drawCenteredText(label, x + width / 2.0, y + height / 2.0 - renderer.fontHeight(0.85) / 2.0,
                selected ? 0xFFFFFFFF : nexus().themes().textDim(), false, 0.85);
    }

    public static void tooltip(IRenderer renderer, double x, double y, String[] lines) {
        if (lines == null || lines.length == 0) {
            return;
        }
        double width = 0.0;
        for (String line : lines) {
            width = Math.max(width, renderer.textWidth(line, 0.85));
        }
        double height = lines.length * (renderer.fontHeight(0.85) + 2.0) + 6.0;
        panel(renderer, x, y, width + 12.0, height, 4.0);
        for (int i = 0; i < lines.length; i++) {
            renderer.drawText(lines[i], x + 6.0, y + 4.0 + i * (renderer.fontHeight(0.85) + 2.0),
                    nexus().themes().text(), false, 0.85);
        }
    }

    /** Fades the world behind the interface (blur + dim + particle field). */
    public static void backdrop(IRenderer renderer, double width, double height, double progress) {
        int background = nexus().themes().background();
        renderer.blurRegion(0.0, 0.0, width, height, nexus().themes().blurStrength() * progress);
        renderer.fillRect(0.0, 0.0, width, height,
                ColorUtil.withAlpha(background, (int) (ColorUtil.alpha(background) * progress)));
        nexus().render().particles().renderThemed(renderer, nexus().client().window());
    }
}
