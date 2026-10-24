package nexus.gui;

import nexus.api.IRenderer;
import nexus.api.KeyCodes;
import nexus.api.screen.NexusScreen;
import nexus.core.Nexus;
import nexus.core.event.Render2DEvent;
import nexus.core.hud.HudElement;
import nexus.core.setting.Setting;
import nexus.util.ColorUtil;
import nexus.util.MathUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * WYSIWYG HUD editor: drag & drop, snap to grid, alignment guides, undo / redo,
 * reset layout, save / load layout and per element settings.
 */
public final class HudEditorScreen extends NexusScreen {

    private static final double PANEL_WIDTH = 190.0;
    private static final double ROW_HEIGHT = 20.0;

    private final Nexus nexus;
    private HudElement dragging;
    private double dragOffsetX;
    private double dragOffsetY;
    private HudElement selected;
    private double panelScroll;
    private boolean moved;

    public HudEditorScreen(Nexus nexus) {
        this.nexus = nexus;
    }

    @Override
    public String title() {
        return "Nexus HUD Editor";
    }

    @Override
    public void init(int width, int height) {
        super.init(width, height);
        nexus.hud().setEditorOpen(true);
    }

    @Override
    public void onClosed() {
        nexus.hud().setEditorOpen(false);
        nexus.configManager().saveAll(false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == KeyCodes.ESCAPE) {
            // ESC closes the editor (GuiManager handles nested screens)
            return false;
        }
        if ((keyCode == KeyCodes.UP || keyCode == KeyCodes.DOWN || keyCode == KeyCodes.LEFT
                || keyCode == KeyCodes.RIGHT) && selected != null) {
            double step = (modifiers & 1) != 0 ? 1.0 : nexus.hud().snapToGrid() ? nexus.hud().gridSize() : 1.0;
            double dx = keyCode == KeyCodes.LEFT ? -step : (keyCode == KeyCodes.RIGHT ? step : 0.0);
            double dy = keyCode == KeyCodes.UP ? -step : (keyCode == KeyCodes.DOWN ? step : 0.0);
            nexus.hud().pushUndo();
            selected.moveBy(dx, dy);
            return true;
        }
        if ((keyCode == 90) && (modifiers & 2) != 0) {
            return nexus.hud().undo();
        }
        if ((keyCode == 89) && (modifiers & 2) != 0) {
            return nexus.hud().redo();
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX > width() - PANEL_WIDTH) {
            return panelClicked(mouseX, mouseY, button);
        }
        // element hit test (topmost first)
        List<HudElement> elements = new ArrayList<HudElement>(nexus.hud().all());
        for (int i = elements.size() - 1; i >= 0; i--) {
            HudElement element = elements.get(i);
            if (element.isEnabled() && element.contains(mouseX, mouseY)) {
                selected = element;
                dragging = element;
                dragOffsetX = mouseX - element.x();
                dragOffsetY = mouseY - element.y();
                moved = false;
                return true;
            }
        }
        selected = null;
        return true;
    }

    private boolean panelClicked(double mouseX, double mouseY, int button) {
        double panelX = width() - PANEL_WIDTH;
        List<HudElement> elements = nexus.hud().all();
        double contentY = mouseY - 40.0 + panelScroll;
        int index = (int) Math.floor(contentY / ROW_HEIGHT);
        if (index >= 0 && index < elements.size()) {
            HudElement element = elements.get(index);
            if (mouseX > panelX + PANEL_WIDTH - 30.0) {
                element.setEnabled(!element.isEnabled());
            } else {
                selected = element;
            }
            return true;
        }
        // buttons
        double buttonY = height() - 92.0;
        if (mouseY >= buttonY && mouseY <= buttonY + 16.0) {
            nexus.hud().pushUndo();
            nexus.hud().resetLayout();
            return true;
        }
        buttonY += 20.0;
        if (mouseY >= buttonY && mouseY <= buttonY + 16.0) {
            nexus.hud().undo();
            return true;
        }
        buttonY += 20.0;
        if (mouseY >= buttonY && mouseY <= buttonY + 16.0) {
            nexus.hud().redo();
            return true;
        }
        buttonY += 20.0;
        if (mouseY >= buttonY && mouseY <= buttonY + 16.0) {
            nexus.configManager().saveAll(true);
            nexus.notifications().success("HUD", "Layout saved");
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging != null) {
            if (moved) {
                nexus.configManager().markDirty();
            }
            dragging = null;
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging != null) {
            if (!moved) {
                nexus.hud().pushUndo();
                moved = true;
            }
            double newX = mouseX - dragOffsetX;
            double newY = mouseY - dragOffsetY;
            double grid = nexus.hud().gridSize();
            if (nexus.hud().snapToGrid()) {
                newX = Math.round(newX / grid) * grid;
                newY = Math.round(newY / grid) * grid;
            }
            dragging.setPosition(MathUtil.clamp(newX, 0.0, Math.max(0.0, width() - PANEL_WIDTH - dragging.width())),
                    MathUtil.clamp(newY, 0.0, Math.max(0.0, height() - dragging.height())));
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (selected != null && mouseX < width() - PANEL_WIDTH) {
            nexus.hud().pushUndo();
            selected.scaleSetting().set(Double.valueOf(MathUtil.clamp(
                    selected.scaleSetting().value() - amount * 0.05, 0.5, 3.0)));
            return true;
        }
        panelScroll = Math.max(0.0, panelScroll - amount * 12.0);
        return true;
    }

    @Override
    public void render(IRenderer renderer, int mouseX, int mouseY, float deltaMs) {
        long now = System.currentTimeMillis();
        GuiTheme.backdrop(renderer, width(), height(), 1.0);
        drawGrid(renderer);

        // live preview of every enabled element
        Render2DEvent event = new Render2DEvent(renderer, width(), height(), deltaMs, mouseX, mouseY);
        for (HudElement element : nexus.hud().all()) {
            if (!element.isEnabled()) {
                continue;
            }
            element.setEditing(element == selected || element == dragging);
            element.render(event);
            element.setEditing(false);
        }

        if (dragging != null && nexus.hud().alignmentGuides()) {
            drawGuides(renderer, dragging);
        }
        drawPanel(renderer, mouseX, mouseY, now);
    }

    private void drawGrid(IRenderer renderer) {
        if (!nexus.hud().snapToGrid()) {
            return;
        }
        double step = nexus.hud().gridSize();
        int color = ColorUtil.withAlpha(nexus.themes().accent(System.currentTimeMillis()), 30);
        for (double x = 0.0; x < width() - PANEL_WIDTH; x += step) {
            for (double y = 0.0; y < height(); y += step * 2.0) {
                renderer.fillRect(x, y, 1.0, 1.0, color);
            }
        }
    }

    private void drawGuides(IRenderer renderer, HudElement element) {
        int guide = ColorUtil.withAlpha(nexus.themes().accent(System.currentTimeMillis()), 150);
        for (Double guideX : nexus.hud().guidesX(element)) {
            if (Math.abs(guideX - element.x()) < 3.0 || Math.abs(guideX - (element.x() + element.width() / 2.0)) < 3.0) {
                renderer.fillRect(guideX, 0.0, 1.0, height(), guide);
            }
        }
        for (Double guideY : nexus.hud().guidesY(element)) {
            if (Math.abs(guideY - element.y()) < 3.0 || Math.abs(guideY - (element.y() + element.height() / 2.0)) < 3.0) {
                renderer.fillRect(0.0, guideY, width() - PANEL_WIDTH, 1.0, guide);
            }
        }
    }

    private void drawPanel(IRenderer renderer, double mouseX, double mouseY, long now) {
        double panelX = width() - PANEL_WIDTH;
        GuiTheme.panel(renderer, panelX, 0.0, PANEL_WIDTH, height(), 0.0);
        renderer.drawText("HUD EDITOR", panelX + 10.0, 10.0, nexus.themes().accent(now), false, 1.0);
        renderer.drawText("drag to move  |  scroll to scale  |  arrows nudge", panelX + 10.0, 24.0,
                ColorUtil.withAlpha(nexus.themes().textDim(), 180), false, 0.75);

        renderer.pushScissor(panelX, 36.0, PANEL_WIDTH, height() - 130.0);
        List<HudElement> elements = nexus.hud().all();
        for (int i = 0; i < elements.size(); i++) {
            HudElement element = elements.get(i);
            double rowY = 40.0 + i * ROW_HEIGHT - panelScroll;
            boolean hovered = mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH
                    && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT;
            if (element == selected) {
                renderer.fillRect(panelX + 4.0, rowY, PANEL_WIDTH - 8.0, ROW_HEIGHT - 2.0,
                        ColorUtil.withAlpha(nexus.themes().accent(now), 90));
            } else if (hovered) {
                renderer.fillRect(panelX + 4.0, rowY, PANEL_WIDTH - 8.0, ROW_HEIGHT - 2.0,
                        ColorUtil.withAlpha(0xFFFFFFFF, 18));
            }
            renderer.drawText(element.displayName(), panelX + 10.0, rowY + 5.0,
                    element.isEnabled() ? nexus.themes().text() : ColorUtil.withAlpha(nexus.themes().textDim(), 150),
                    false, 1.0);
            renderer.fillRoundRect(panelX + PANEL_WIDTH - 26.0, rowY + 3.0, 20.0, 12.0, 6.0,
                    element.isEnabled() ? ColorUtil.withAlpha(nexus.themes().accent(now), 220)
                            : ColorUtil.withAlpha(0xFFFFFFFF, 40));
            renderer.circle(panelX + PANEL_WIDTH - (element.isEnabled() ? 12.0 : 20.0), rowY + 9.0, 4.0,
                    element.isEnabled() ? 0xFFFFFFFF : ColorUtil.withAlpha(nexus.themes().textDim(), 200));
        }
        renderer.popScissor();

        // selected element settings
        double settingsY = height() - 130.0;
        renderer.drawText(selected == null ? "No element selected" : selected.displayName(),
                panelX + 10.0, settingsY, nexus.themes().text(), false, 1.0);
        if (selected != null) {
            Setting<?> scale = selected.setting("Scale");
            Setting<?> shadow = selected.setting("Shadow");
            Setting<?> background = selected.setting("Background");
            renderer.drawText("scale " + (scale == null ? "?" : scale.displayValue())
                            + "   shadow " + (shadow == null ? "?" : shadow.displayValue())
                            + "   bg " + (background == null ? "?" : background.displayValue()),
                    panelX + 10.0, settingsY + 12.0, ColorUtil.withAlpha(nexus.themes().textDim(), 200), false, 0.7);
        }

        double buttonY = height() - 92.0;
        GuiTheme.button(renderer, panelX + 8.0, buttonY, PANEL_WIDTH - 16.0, 16.0, "Reset Layout", hover(mouseX, mouseY, panelX, buttonY), false);
        buttonY += 20.0;
        GuiTheme.button(renderer, panelX + 8.0, buttonY, (PANEL_WIDTH - 20.0) / 2.0, 16.0,
                "Undo (" + nexus.hud().undoSize() + ")", hover(mouseX, mouseY, panelX, buttonY), false);
        GuiTheme.button(renderer, panelX + 12.0 + (PANEL_WIDTH - 20.0) / 2.0, buttonY, (PANEL_WIDTH - 20.0) / 2.0, 16.0,
                "Redo (" + nexus.hud().redoSize() + ")", hover(mouseX, mouseY, panelX, buttonY), false);
        buttonY += 20.0;
        GuiTheme.button(renderer, panelX + 8.0, buttonY, PANEL_WIDTH - 16.0, 16.0, "Save Layout", hover(mouseX, mouseY, panelX, buttonY), true);
        buttonY += 20.0;
        renderer.drawText("ESC / RIGHT SHIFT to close", panelX + 10.0, buttonY + 4.0,
                ColorUtil.withAlpha(nexus.themes().textDim(), 170), false, 0.75);
    }

    private boolean hover(double mouseX, double mouseY, double panelX, double buttonY) {
        return mouseX >= panelX + 8.0 && mouseX <= panelX + PANEL_WIDTH - 8.0
                && mouseY >= buttonY && mouseY <= buttonY + 16.0;
    }
}
