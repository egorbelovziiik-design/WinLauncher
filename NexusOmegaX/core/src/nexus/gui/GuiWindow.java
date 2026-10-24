package nexus.gui;

import nexus.api.IRenderer;
import nexus.core.Nexus;
import nexus.util.ColorUtil;
import nexus.util.Easing;
import nexus.util.MathUtil;

/**
 * Draggable, resizable and scrollable window used by every Nexus interface.
 * <p>
 * Windows animate their content in and out, clip it with a scissor region and
 * keep their own scroll offset, which keeps the ClickGUI allocation free while
 * scrolling long module lists.
 */
public abstract class GuiWindow {

    protected static final double TITLE_HEIGHT = 22.0;

    private final String title;
    protected double x;
    protected double y;
    protected double width;
    protected double height;
    protected double scroll;
    protected double targetScroll;
    protected double animation;
    protected boolean closable;
    protected boolean resizable;
    protected boolean dragging;
    protected boolean resizing;
    private double dragOffsetX;
    private double dragOffsetY;
    private boolean hovered;

    protected GuiWindow(String title, double x, double y, double width, double height) {
        this(title, x, y, width, height, true, true);
    }

    protected GuiWindow(String title, double x, double y, double width, double height,
                        boolean closable, boolean resizable) {
        this.title = title;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.closable = closable;
        this.resizable = resizable;
    }

    // ---------------------------------------------------------------- accessors

    public String title() {
        return title;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double width() {
        return width;
    }

    public double height() {
        return height;
    }

    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public void setSize(double width, double height) {
        this.width = Math.max(80.0, width);
        this.height = Math.max(60.0, height);
    }

    public boolean isInside(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public boolean isInsideContent(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y + TITLE_HEIGHT && mouseY <= y + height;
    }

    /** Stops every running animation (called when the interface closes). */
    public void resetAnimations() {
        animation = 0.0;
        scroll = 0.0;
        targetScroll = 0.0;
        dragging = false;
        resizing = false;
    }

    // ----------------------------------------------------------------- drawing

    public final void render(IRenderer renderer, double mouseX, double mouseY, float deltaMs) {
        animation = MathUtil.approach(animation, 1.0, 0.4, deltaMs);
        double eased = Easing.ease(Easing.EASE_OUT_EXPO, MathUtil.clamp(animation, 0.0, 1.0));
        double scale = 0.96 + 0.04 * eased;
        double drawWidth = width * scale;
        double drawHeight = height * scale;
        double offsetX = (width - drawWidth) / 2.0;
        double offsetY = (height - drawHeight) / 2.0;

        hovered = isInside(mouseX, mouseY);
        GuiTheme.panel(renderer, x + offsetX, y + offsetY, drawWidth, drawHeight);
        GuiTheme.titleBar(renderer, x + offsetX, y + offsetY, drawWidth, title, TITLE_HEIGHT * scale);

        if (closable) {
            double closeX = x + offsetX + drawWidth - 16.0;
            double closeY = y + offsetY + 5.0;
            boolean closeHovered = mouseX >= closeX && mouseX <= closeX + 10.0
                    && mouseY >= closeY && mouseY <= closeY + 10.0;
            renderer.fillRoundRect(closeX, closeY, 10.0, 10.0, 2.0,
                    closeHovered ? ColorUtil.withAlpha(0xFFF87171, 220) : ColorUtil.withAlpha(0xFFFFFFFF, 40));
            renderer.line(closeX + 3.0, closeY + 3.0, closeX + 7.0, closeY + 7.0, 1.0, 0xFFFFFFFF);
            renderer.line(closeX + 7.0, closeY + 3.0, closeX + 3.0, closeY + 7.0, 1.0, 0xFFFFFFFF);
        }

        double viewY = y + offsetY + TITLE_HEIGHT * scale;
        double viewHeight = drawHeight - TITLE_HEIGHT * scale - 4.0;
        double contentHeight = contentHeight();
        double maxScroll = Math.max(0.0, contentHeight - viewHeight);
        targetScroll = MathUtil.clamp(targetScroll, 0.0, maxScroll);
        scroll = MathUtil.approach(scroll, targetScroll, 0.5, deltaMs);

        renderer.pushScissor(x + offsetX + 1.0, viewY, drawWidth - 2.0, viewHeight);
        drawContent(renderer, mouseX, mouseY, x + offsetX, viewY - scroll, drawWidth, viewHeight);
        renderer.popScissor();

        if (maxScroll > 0.0) {
            GuiTheme.scrollbar(renderer, x + offsetX + drawWidth - 6.0, viewY, viewHeight,
                    contentHeight, viewHeight, scroll);
        }
    }

    public void tick(float deltaMs) {
    }

    /** Height of the scrollable content in GUI pixels. */
    protected abstract double contentHeight();

    /** Draws the window body. {@code contentY} already includes the scroll offset. */
    protected abstract void drawContent(IRenderer renderer, double mouseX, double mouseY,
                                        double contentX, double contentY, double viewWidth, double viewHeight);

    // ------------------------------------------------------------------- input

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isInside(mouseX, mouseY)) {
            return false;
        }
        if (closable && button == 0 && mouseX >= x + width - 16.0 && mouseX <= x + width - 6.0
                && mouseY >= y + 5.0 && mouseY <= y + 15.0) {
            onCloseRequested();
            return true;
        }
        if (button == 0 && mouseY <= y + TITLE_HEIGHT) {
            dragging = true;
            dragOffsetX = mouseX - x;
            dragOffsetY = mouseY - y;
            return true;
        }
        if (button == 0 && resizable && mouseX >= x + width - 8.0 && mouseY >= y + height - 8.0) {
            resizing = true;
            return true;
        }
        return contentClicked(mouseX, mouseY, button);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            dragging = false;
            onMoved();
            return true;
        }
        if (resizing) {
            resizing = false;
            onResized();
            return true;
        }
        return contentReleased(mouseX, mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging) {
            x = MathUtil.clamp(mouseX - dragOffsetX, -width + 40.0, Nexus.instance().client().window().guiWidth() - 40.0);
            y = MathUtil.clamp(mouseY - dragOffsetY, 0.0, Nexus.instance().client().window().guiHeight() - TITLE_HEIGHT);
            return true;
        }
        if (resizing) {
            setSize(mouseX - x, mouseY - y);
            return true;
        }
        return contentDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!isInside(mouseX, mouseY)) {
            return false;
        }
        targetScroll -= amount * 14.0;
        targetScroll = Math.max(0.0, targetScroll);
        return contentScrolled(mouseX, mouseY, amount);
    }

    // ----------------------------------------------------------- overridables

    protected boolean contentClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    protected boolean contentReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    protected boolean contentDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return false;
    }

    protected boolean contentScrolled(double mouseX, double mouseY, double amount) {
        return true;
    }

    /** Called when the close button is pressed (nested window behaviour). */
    protected void onCloseRequested() {
    }

    protected void onMoved() {
    }

    protected void onResized() {
    }

    public boolean isHovered() {
        return hovered;
    }

    public void setClosable(boolean value) {
        closable = value;
    }
}
