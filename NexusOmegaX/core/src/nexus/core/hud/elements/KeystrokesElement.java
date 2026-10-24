package nexus.core.hud.elements;

import nexus.api.IInput;
import nexus.api.KeyCodes;
import nexus.core.Nexus;
import nexus.core.event.Render2DEvent;
import nexus.core.hud.HudElement;
import nexus.core.setting.BooleanSetting;
import nexus.core.setting.NumberSetting;
import nexus.util.ColorUtil;

/** WASD / mouse / space key display. */
public final class KeystrokesElement extends HudElement {

    private final BooleanSetting showMouse;
    private final BooleanSetting showSpace;
    private final NumberSetting keySize;

    public KeystrokesElement() {
        super("keystrokes", "Keystrokes");
        this.showMouse = new BooleanSetting("Show Mouse", "Show the mouse buttons", true);
        this.showSpace = new BooleanSetting("Show Space", "Show the space bar", true);
        this.keySize = new NumberSetting("Key Size", "Size of a single key", 16.0, 10.0, 32.0, 1.0);
    }

    private double size() {
        return keySize.value() * scale();
    }

    @Override
    public double width() {
        return size() * 3.0 + 4.0 * scale();
    }

    @Override
    public double height() {
        double base = size() * (showSpace.isEnabled() ? 3.2 : 2.0);
        return base + (showMouse.isEnabled() ? size() + 2.0 : 0.0);
    }

    @Override
    protected void draw(Render2DEvent event, double x, double y) {
        long now = System.currentTimeMillis();
        IInput input = Nexus.instance().client().input();
        double s = size();
        double gap = 2.0 * scale();
        int accent = color(now);
        int idle = ColorUtil.withAlpha(0x30FFFFFF, 40);

        key(event, x + s + gap, y, s, s, "W", input.isKeyDown(87), accent, idle);
        key(event, x, y + s + gap, s, s, "A", input.isKeyDown(65), accent, idle);
        key(event, x + s + gap, y + s + gap, s, s, "S", input.isKeyDown(83), accent, idle);
        key(event, x + 2.0 * (s + gap), y + s + gap, s, s, "D", input.isKeyDown(68), accent, idle);

        double offsetY = y + 2.0 * (s + gap);
        if (showSpace.isEnabled()) {
            key(event, x, offsetY, width(), s * 0.7, "___", input.isKeyDown(KeyCodes.SPACE), accent, idle);
            offsetY += s * 0.7 + gap;
        }
        if (showMouse.isEnabled()) {
            double half = (width() - gap) / 2.0;
            key(event, x, offsetY, half, s, "LMB", input.isMouseDown(0), accent, idle);
            key(event, x + half + gap, offsetY, half, s, "RMB", input.isMouseDown(1), accent, idle);
        }
    }

    private void key(Render2DEvent event, double x, double y, double w, double h, String label,
                     boolean pressed, int accent, int idle) {
        int bg = pressed ? ColorUtil.withAlpha(accent, 150) : idle;
        int outline = pressed ? accent : ColorUtil.withAlpha(accent, 70);
        event.renderer.fillRoundRect(x, y, w, h, 3.0 * scale(), bg);
        event.renderer.outlineRoundRect(x, y, w, h, 3.0 * scale(), 1.0, outline);
        double scale = scale();
        event.renderer.drawCenteredText(label, x + w / 2.0, y + h / 2.0 - fontHeight(scale) / 2.0,
                pressed ? 0xFFFFFFFF : colorSetting().effectiveColor(System.currentTimeMillis()), false, scale * 0.8);
    }

    @Override
    public java.util.List<nexus.core.setting.Setting<?>> settings() {
        java.util.List<nexus.core.setting.Setting<?>> list = super.settings();
        list.add(showMouse);
        list.add(showSpace);
        list.add(keySize);
        return list;
    }

    @Override
    public String previewText() {
        return "[W][A][S][D]";
    }
}
