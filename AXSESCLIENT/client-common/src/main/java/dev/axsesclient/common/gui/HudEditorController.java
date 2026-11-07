package dev.axsesclient.common.gui;

import dev.axsesclient.common.AxsesCore;
import dev.axsesclient.common.hud.HudElement;
import dev.axsesclient.common.hud.HudManager;
import dev.axsesclient.common.notifications.NotificationManager;
import dev.axsesclient.common.platform.KeyCodes;
import dev.axsesclient.common.platform.MinecraftAdapter;
import dev.axsesclient.common.platform.RenderAdapter;
import dev.axsesclient.common.theme.AxsesTheme;
import java.util.List;

public final class HudEditorController {
    private final AxsesCore core;
    private HudElement selectedElement = null;
    private boolean dragging = false;
    private boolean resizing = false;
    private float dragOffsetX = 0f;
    private float dragOffsetY = 0f;
    private boolean showVerticalCenterGuide = false;
    private boolean showHorizontalCenterGuide = false;
    private boolean showDisabledWidgets = false;

    public HudEditorController(AxsesCore core) {
        this.core = core;
    }

    public void render(RenderAdapter r, MinecraftAdapter mc, int mouseX, int mouseY, float partialTick) {
        AxsesTheme t = core.getThemeManager().getActiveTheme();
        HudManager hm = core.getHudManager();
        int sw = r.getScaledWidth();
        int sh = r.getScaledHeight();

        // Dark translucent canvas + grid lines
        r.fillRect(0, 0, sw, sh, 0x88070911);
        if (hm.isGridSnapping()) {
            int step = Math.max(8, hm.getGridSize() * 2);
            int gridCol = 0x16929BB1;
            for (int gx = step; gx < sw; gx += step) {
                r.fillRect(gx, 0, 1, sh, gridCol);
            }
            for (int gy = step; gy < sh; gy += step) {
                r.fillRect(0, gy, sw, 1, gridCol);
            }
        }

        // Virtual Resolution Preview frame if non-CURRENT
        HudManager.ResolutionPreview rp = hm.getResolutionPreview();
        float canvasW = sw;
        float canvasH = sh;
        float canvasOffsetX = 0f;
        float canvasOffsetY = 0f;
        if (rp != HudManager.ResolutionPreview.CURRENT) {
            float ratio = Math.min((sw - 40f) / rp.getVirtualWidth(), (sh - 70f) / rp.getVirtualHeight());
            canvasW = rp.getVirtualWidth() * ratio;
            canvasH = rp.getVirtualHeight() * ratio;
            canvasOffsetX = (sw - canvasW) * 0.5f;
            canvasOffsetY = (sh - canvasH) * 0.5f + 10f;
            r.drawRoundedOutline(canvasOffsetX - 1, canvasOffsetY - 1, canvasW + 2, canvasH + 2, 4f, 1.2f, t.getAccentColor());
        }

        // Magnetic center guide lines
        if (showVerticalCenterGuide) {
            r.fillRect(canvasOffsetX + canvasW * 0.5f, canvasOffsetY, 1.0f, canvasH, t.getAccentColor());
        }
        if (showHorizontalCenterGuide) {
            r.fillRect(canvasOffsetX, canvasOffsetY + canvasH * 0.5f, canvasW, 1.0f, t.getAccentColor());
        }

        r.pushTransform(canvasOffsetX, canvasOffsetY, 1.0f);
        for (HudElement el : hm.getAll()) {
            if (!showDisabledWidgets && el.getBoundModule() != null && !el.getBoundModule().isEnabled()) {
                continue;
            }
            el.renderWidget(r, mc, core.getThemeManager(), core.getFontManager(), canvasW, canvasH, showDisabledWidgets, el == selectedElement);
        }
        r.popTransform();

        // Top HUD Editor Toolbar (positioned in the clear central corridor below top HUD rows)
        boolean ru = core.getLocalizationManager().isRussian();
        int prim = core.getThemeManager().filterColor(t.getPrimaryColor());
        int acc = core.getThemeManager().filterColor(t.getAccentColor());
        float barW = Math.min(sw - 16f, 514f);
        float barX = (sw - barW) * 0.5f - 28f;
        float barY = 64f;
        dev.axsesclient.common.render.RenderManager.drawFrostedGlassPanel(r, barX, barY, barW, 24f, 9f, prim, acc, 0.96f, 0.72f);
        core.getFontManager().draw(r, ru ? "РЕДАКТОР HUD 6.0" : "HUD EDITOR 6.0", barX + 10f, barY + 8f, acc, 0.80f);
        drawToolBtn(r, barX + 116f, barY + 3f, 58f, 18f, hm.isGridSnapping() ? (ru ? "Сетка:ВКЛ" : "Grid:ON") : (ru ? "Сетка:ВЫКЛ" : "Grid:OFF"), mouseX, mouseY);
        drawToolBtn(r, barX + 177f, barY + 3f, 78f, 18f, showDisabledWidgets ? (ru ? "Вид:Все" : "Show:All") : (ru ? "Вид:Актив" : "Show:Active"), mouseX, mouseY);
        drawToolBtn(r, barX + 258f, barY + 3f, 62f, 18f, rp.getLabel().split(" ")[0], mouseX, mouseY);
        drawToolBtn(r, barX + 323f, barY + 3f, 56f, 18f, ru ? "Пресет" : "Preset", mouseX, mouseY);
        drawToolBtn(r, barX + 382f, barY + 3f, 50f, 18f, ru ? "Сброс" : "Reset", mouseX, mouseY);
        drawToolBtn(r, barX + 435f, barY + 3f, 72f, 18f, ru ? "Сохранить" : "Save & GUI", mouseX, mouseY);

        // Selected Widget Inspector Dock right below top toolbar (keeps bottom Glass Hotbar 100% unobstructed)
        if (selectedElement != null) {
            float dockW = Math.min(sw - 16f, 514f);
            float dockX = barX;
            float dockY = barY + 29f;
            dev.axsesclient.common.render.RenderManager.drawFrostedGlassPanel(r, dockX, dockY, dockW, 25f, 9f, acc, prim, 0.96f, 0.72f);
            core.getFontManager().draw(r, selectedElement.getTitle(), dockX + 10f, dockY + 8.5f, t.getTextColor(), 0.78f);

            drawToolBtn(r, dockX + 88f, dockY + 3.5f, 36f, 18f, "Sc-", mouseX, mouseY);
            drawToolBtn(r, dockX + 127f, dockY + 3.5f, 36f, 18f, "Sc+", mouseX, mouseY);
            drawToolBtn(r, dockX + 166f, dockY + 3.5f, 44f, 18f, selectedElement.isShowBackground() ? "BG:ON" : "BG:OFF", mouseX, mouseY);
            drawToolBtn(r, dockX + 213f, dockY + 3.5f, 46f, 18f, selectedElement.isShowShadow() ? "Shd:ON" : "Shd:OFF", mouseX, mouseY);
            drawToolBtn(r, dockX + 262f, dockY + 3.5f, 46f, 18f, selectedElement.isShowOutline() ? "Out:ON" : "Out:OFF", mouseX, mouseY);
            drawToolBtn(r, dockX + 311f, dockY + 3.5f, 46f, 18f, selectedElement.getAlignment().name().substring(0, 1) + "-Align", mouseX, mouseY);
            drawToolBtn(r, dockX + 360f, dockY + 3.5f, 44f, 18f, "Center", mouseX, mouseY);
            String grp = selectedElement.getGroupId().isEmpty() ? "Grp:-" : ("Grp:" + selectedElement.getGroupId());
            drawToolBtn(r, dockX + 407f, dockY + 3.5f, 46f, 18f, grp, mouseX, mouseY);
            drawToolBtn(r, dockX + 456f, dockY + 3.5f, 52f, 18f, selectedElement.isLocked() ? "Locked" : "Unlock", mouseX, mouseY);
        }
    }

    private String[] presetCycle = {"Competitive", "Explorer", "Minimal", "Telemetry"};
    private int presetIdx = 0;

    public boolean mouseClicked(MinecraftAdapter mc, double mx, double my, int button) {
        HudManager hm = core.getHudManager();
        int sw = mc != null ? mc.getScaledWidth() : 854;
        int sh = mc != null ? mc.getScaledHeight() : 480;

        float barW = Math.min(sw - 16f, 514f);
        float barX = (sw - barW) * 0.5f - 28f;
        float barY = 64f;
        if (hit((float) mx, (float) my, barX + 116f, barY + 3f, 58f, 18f)) { hm.setGridSnapping(!hm.isGridSnapping()); return true; }
        if (hit((float) mx, (float) my, barX + 177f, barY + 3f, 78f, 18f)) { showDisabledWidgets = !showDisabledWidgets; return true; }
        if (hit((float) mx, (float) my, barX + 258f, barY + 3f, 62f, 18f)) { hm.cycleResolutionPreview(); return true; }
        if (hit((float) mx, (float) my, barX + 323f, barY + 3f, 56f, 18f)) {
            String p = presetCycle[presetIdx % presetCycle.length];
            presetIdx++;
            hm.applyPreset(p, mc);
            core.getNotificationManager().push("HUD Preset", p, NotificationManager.Severity.SUCCESS);
            return true;
        }
        if (hit((float) mx, (float) my, barX + 382f, barY + 3f, 50f, 18f)) { hm.resetAll(); return true; }
        if (hit((float) mx, (float) my, barX + 435f, barY + 3f, 72f, 18f)) {
            core.getConfigManager().saveActiveAsync();
            mc.openClickGui();
            return true;
        }

        if (selectedElement != null) {
            float dockW = Math.min(sw - 16f, 514f);
            float dockX = barX;
            float dockY = barY + 29f;
            if (hit((float) mx, (float) my, dockX + 88f, dockY + 3.5f, 36f, 18f)) { selectedElement.setScale(selectedElement.getScale() - 0.10f); return true; }
            if (hit((float) mx, (float) my, dockX + 127f, dockY + 3.5f, 36f, 18f)) { selectedElement.setScale(selectedElement.getScale() + 0.10f); return true; }
            if (hit((float) mx, (float) my, dockX + 166f, dockY + 3.5f, 44f, 18f)) { selectedElement.setShowBackground(!selectedElement.isShowBackground()); return true; }
            if (hit((float) mx, (float) my, dockX + 213f, dockY + 3.5f, 46f, 18f)) { selectedElement.setShowShadow(!selectedElement.isShowShadow()); return true; }
            if (hit((float) mx, (float) my, dockX + 262f, dockY + 3.5f, 46f, 18f)) { selectedElement.setShowOutline(!selectedElement.isShowOutline()); return true; }
            if (hit((float) mx, (float) my, dockX + 311f, dockY + 3.5f, 46f, 18f)) { selectedElement.cycleAlignment(); return true; }
            if (hit((float) mx, (float) my, dockX + 360f, dockY + 3.5f, 44f, 18f)) { hm.centerHorizontally(selectedElement); return true; }
            if (hit((float) mx, (float) my, dockX + 407f, dockY + 3.5f, 46f, 18f)) {
                String cur = selectedElement.getGroupId();
                selectedElement.setGroupId(cur.isEmpty() ? "A" : ("A".equals(cur) ? "B" : ""));
                return true;
            }
            if (hit((float) mx, (float) my, dockX + 456f, dockY + 3.5f, 52f, 18f)) { selectedElement.setLocked(!selectedElement.isLocked()); return true; }
        }

        // Hit-test HUD elements (topmost first)
        List<HudElement> all = hm.getAll();
        for (int i = all.size() - 1; i >= 0; i--) {
            HudElement el = all.get(i);
            if (!showDisabledWidgets && el.getBoundModule() != null && !el.getBoundModule().isEnabled()) {
                continue;
            }
            float px = el.getPixelX(sw);
            float py = el.getPixelY(sh);
            float ew = el.getScaledWidth();
            float eh = el.getScaledHeight();
            if (hit((float) mx, (float) my, px, py, ew, eh)) {
                selectedElement = el;
                if (el.getInteractiveHandler() != null && el.getInteractiveHandler().onMouseClick(mc, (float) (mx - px), (float) (my - py), ew, eh, button)) {
                    mc.playUiClickSound(1.08f);
                    return true;
                }
                if (button == 1 && el.getBoundModule() != null) {
                    el.getBoundModule().toggle(mc);
                    return true;
                }
                if (!el.isLocked()) {
                    if (mx >= px + ew - 8f && my >= py + eh - 8f) {
                        resizing = true;
                        dragging = false;
                    } else {
                        dragging = true;
                        resizing = false;
                        dragOffsetX = (float) mx - px;
                        dragOffsetY = (float) my - py;
                    }
                }
                return true;
            }
        }
        selectedElement = null;
        return false;
    }

    public boolean mouseDragged(MinecraftAdapter mc, double mx, double my) {
        if (selectedElement == null || selectedElement.isLocked()) return false;
        int sw = mc != null ? mc.getScaledWidth() : 854;
        int sh = mc != null ? mc.getScaledHeight() : 480;
        if (resizing) {
            float px = selectedElement.getPixelX(sw);
            float py = selectedElement.getPixelY(sh);
            selectedElement.setWidth(((float) mx - px) / selectedElement.getScale());
            selectedElement.setHeight(((float) my - py) / selectedElement.getScale());
            return true;
        }
        if (dragging) {
            boolean[] guides = core.getHudManager().moveElementWithSnapping(
                    selectedElement,
                    (float) mx - dragOffsetX,
                    (float) my - dragOffsetY,
                    sw,
                    sh
            );
            showVerticalCenterGuide = guides[0];
            showHorizontalCenterGuide = guides[1];
            return true;
        }
        return false;
    }

    public boolean mouseReleased() {
        dragging = false;
        resizing = false;
        showVerticalCenterGuide = false;
        showHorizontalCenterGuide = false;
        return false;
    }

    public boolean mouseScrolled(double verticalAmount) {
        if (selectedElement != null) {
            selectedElement.setScale(selectedElement.getScale() + (float) verticalAmount * 0.05f);
            return true;
        }
        return false;
    }

    public boolean keyPressed(MinecraftAdapter mc, int keyCode) {
        if (keyCode == KeyCodes.KEY_ESCAPE || keyCode == core.getKeybindManager().getOpenGuiBind().getKeyCode()) {
            core.getConfigManager().saveActiveAsync();
            mc.closeScreen();
            return true;
        }
        return false;
    }

    private void drawToolBtn(RenderAdapter r, float x, float y, float w, float h, String txt, int mx, int my) {
        AxsesTheme t = core.getThemeManager().getActiveTheme();
        boolean hov = hit(mx, my, x, y, w, h);
        int prim = core.getThemeManager().filterColor(t.getPrimaryColor());
        int acc = core.getThemeManager().filterColor(t.getAccentColor());
        float rad = Math.min(7.5f, h * 0.46f);
        r.fillRoundedRect(x, y, w, h, rad, hov ? AxsesTheme.withAlpha(prim, 0.55f) : t.getElevatedColor());
        if (w > 12f) {
            r.fillRect(x + rad, y + 1.0f, w - rad * 2.0f, 1.0f, hov ? 0x3AFFFFFF : 0x1AFFFFFF);
        }
        r.drawRoundedOutline(x, y, w, h, rad, 1.0f, hov ? acc : AxsesTheme.withAlpha(prim, 0.42f));
        core.getFontManager().drawCentered(r, txt, x + w * 0.5f, y + 5.2f, 0xFFFFFFFF, 0.74f);
    }

    private static boolean hit(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
