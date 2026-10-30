package dev.axsesclient.common.hud;

import dev.axsesclient.common.platform.MinecraftAdapter;
import dev.axsesclient.common.platform.RenderAdapter;
import dev.axsesclient.common.render.FontManager;
import dev.axsesclient.common.theme.ThemeManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HudManager {
    public enum ResolutionPreview {
        CURRENT("Current Window", 0, 0),
        HD_720P("1280x720 (HD)", 640, 360),
        FHD_1080P("1920x1080 (FHD)", 960, 540),
        QHD_1440P("2560x1440 (QHD)", 1280, 720);

        private final String label;
        private final int virtualWidth;
        private final int virtualHeight;

        ResolutionPreview(String label, int virtualWidth, int virtualHeight) {
            this.label = label;
            this.virtualWidth = virtualWidth;
            this.virtualHeight = virtualHeight;
        }

        public String getLabel() { return label; }
        public int getVirtualWidth() { return virtualWidth; }
        public int getVirtualHeight() { return virtualHeight; }
    }

    private final Map<String, HudElement> elements = new LinkedHashMap<String, HudElement>();
    private boolean gridSnapping = true;
    private int gridSize = 8;
    private boolean edgeAndCenterSnapping = true;
    private ResolutionPreview resolutionPreview = ResolutionPreview.CURRENT;

    public synchronized HudElement register(HudElement element) {
        elements.put(element.getId(), element);
        return element;
    }

    public synchronized HudElement getById(String id) {
        return elements.get(id);
    }

    public synchronized List<HudElement> getAll() {
        return Collections.unmodifiableList(new ArrayList<HudElement>(elements.values()));
    }

    public void renderInGameHud(RenderAdapter r, MinecraftAdapter mc, ThemeManager themeManager, FontManager fontManager) {
        float w = r.getScaledWidth();
        float h = r.getScaledHeight();
        for (HudElement el : elements.values()) {
            el.renderWidget(r, mc, themeManager, fontManager, w, h, false, false);
        }
    }

    /**
     * Moves a HUD element (and any siblings sharing the same non-empty groupId) with magnetic edge,
     * centerline, and optional grid snapping. Returns snap guide flags [verticalCenterSnap, horizontalCenterSnap].
     */
    public boolean[] moveElementWithSnapping(HudElement target, float rawPx, float rawPy, float canvasW, float canvasH) {
        boolean[] guides = new boolean[] { false, false };
        if (target == null || target.isLocked()) {
            return guides;
        }

        float oldPx = target.getPixelX(canvasW);
        float oldPy = target.getPixelY(canvasH);

        float sw = target.getScaledWidth();
        float sh = target.getScaledHeight();
        float snappedX = rawPx;
        float snappedY = rawPy;

        if (gridSnapping && gridSize > 1) {
            snappedX = Math.round(snappedX / gridSize) * gridSize;
            snappedY = Math.round(snappedY / gridSize) * gridSize;
        }

        if (edgeAndCenterSnapping) {
            float snapThreshold = 6.0f;
            // Edge snapping
            if (Math.abs(snappedX - 6.0f) <= snapThreshold) snappedX = 6.0f;
            if (Math.abs((snappedX + sw) - (canvasW - 6.0f)) <= snapThreshold) snappedX = canvasW - 6.0f - sw;
            if (Math.abs(snappedY - 6.0f) <= snapThreshold) snappedY = 6.0f;
            if (Math.abs((snappedY + sh) - (canvasH - 6.0f)) <= snapThreshold) snappedY = canvasH - 6.0f - sh;

            // Center snapping
            float centerX = (canvasW - sw) * 0.5f;
            float centerY = (canvasH - sh) * 0.5f;
            if (Math.abs(snappedX - centerX) <= snapThreshold) {
                snappedX = centerX;
                guides[0] = true;
            }
            if (Math.abs(snappedY - centerY) <= snapThreshold) {
                snappedY = centerY;
                guides[1] = true;
            }
        }

        snappedX = Math.max(0.0f, Math.min(Math.max(0.0f, canvasW - sw), snappedX));
        snappedY = Math.max(0.0f, Math.min(Math.max(0.0f, canvasH - sh), snappedY));
        target.setPixelPosition(snappedX, snappedY, canvasW, canvasH);

        float dx = target.getPixelX(canvasW) - oldPx;
        float dy = target.getPixelY(canvasH) - oldPy;

        String gid = target.getGroupId();
        if (gid != null && !gid.isEmpty() && (Math.abs(dx) > 0.01f || Math.abs(dy) > 0.01f)) {
            for (HudElement sibling : elements.values()) {
                if (sibling != target && !sibling.isLocked() && gid.equals(sibling.getGroupId())) {
                    sibling.setPixelPosition(sibling.getPixelX(canvasW) + dx, sibling.getPixelY(canvasH) + dy, canvasW, canvasH);
                }
            }
        }
        return guides;
    }

    public void centerHorizontally(HudElement el) {
        if (el != null && !el.isLocked()) {
            el.setNormX(0.5f);
        }
    }

    public void centerVertically(HudElement el) {
        if (el != null && !el.isLocked()) {
            el.setNormY(0.5f);
        }
    }

    public void applyPreset(String presetName, MinecraftAdapter mc) {
        if (presetName == null) return;
        String p = presetName.trim().toLowerCase(java.util.Locale.ROOT);
        int topLeftIdx = 0;
        int topRightIdx = 0;
        for (HudElement el : elements.values()) {
            el.setLocked(false);
            if ("minimal".equals(p) || "minimalist".equals(p)) {
                boolean core = "fps_counter".equals(el.getId())
                        || "coordinates_hud".equals(el.getId())
                        || "sprint_indicator".equals(el.getId());
                if (el.getBoundModule() != null) {
                    el.getBoundModule().setEnabled(core, mc);
                }
                el.setNormX(0.015f);
                el.setNormY(0.02f + topLeftIdx * 0.065f);
                el.setScale(0.95f);
                if (core) topLeftIdx++;
            } else if ("competitive".equals(p) || "pvp".equals(p)) {
                boolean pvp = "fps_counter".equals(el.getId())
                        || "cps_counter".equals(el.getId())
                        || "keystrokes".equals(el.getId())
                        || "armor_hud".equals(el.getId())
                        || "potion_hud".equals(el.getId())
                        || "sprint_indicator".equals(el.getId())
                        || "ping_display".equals(el.getId());
                if (el.getBoundModule() != null) {
                    el.getBoundModule().setEnabled(pvp, mc);
                }
                if (pvp) {
                    if (topLeftIdx < 4) {
                        el.setNormX(0.015f);
                        el.setNormY(0.02f + topLeftIdx * 0.07f);
                        topLeftIdx++;
                    } else {
                        el.setNormX(0.985f);
                        el.setNormY(0.02f + topRightIdx * 0.07f);
                        topRightIdx++;
                    }
                }
            } else if ("explorer".equals(p)) {
                boolean exp = "coordinates_hud".equals(el.getId())
                        || "direction_hud".equals(el.getId())
                        || "biome_display".equals(el.getId())
                        || "clock_hud".equals(el.getId())
                        || "compass_hud".equals(el.getId())
                        || "light_level_display".equals(el.getId())
                        || "durability_hud".equals(el.getId());
                if (el.getBoundModule() != null) {
                    el.getBoundModule().setEnabled(exp, mc);
                }
                if ("compass_hud".equals(el.getId())) {
                    el.setNormX(0.5f);
                    el.setNormY(0.015f);
                } else if (exp) {
                    el.setNormX(0.015f);
                    el.setNormY(0.025f + topLeftIdx * 0.065f);
                    topLeftIdx++;
                }
            } else if ("telemetry".equals(p)) {
                boolean tel = "fps_counter".equals(el.getId())
                        || "memory_display".equals(el.getId())
                        || "ping_display".equals(el.getId())
                        || "movement_speed_hud".equals(el.getId())
                        || "session_timer".equals(el.getId())
                        || "session_information".equals(el.getId());
                if (el.getBoundModule() != null) {
                    el.getBoundModule().setEnabled(tel, mc);
                }
                if (tel) {
                    el.setNormX(0.985f);
                    el.setNormY(0.02f + topRightIdx * 0.068f);
                    topRightIdx++;
                }
            }
        }
    }

    public synchronized void resetAll() {
        for (HudElement el : elements.values()) {
            el.resetLayout();
        }
        gridSnapping = true;
        gridSize = 8;
        edgeAndCenterSnapping = true;
        resolutionPreview = ResolutionPreview.CURRENT;
    }

    public boolean isGridSnapping() { return gridSnapping; }
    public void setGridSnapping(boolean gridSnapping) { this.gridSnapping = gridSnapping; }
    public int getGridSize() { return gridSize; }
    public void setGridSize(int gridSize) { this.gridSize = Math.max(2, Math.min(32, gridSize)); }
    public boolean isEdgeAndCenterSnapping() { return edgeAndCenterSnapping; }
    public void setEdgeAndCenterSnapping(boolean s) { this.edgeAndCenterSnapping = s; }
    public ResolutionPreview getResolutionPreview() { return resolutionPreview; }
    public void cycleResolutionPreview() {
        ResolutionPreview[] vals = ResolutionPreview.values();
        this.resolutionPreview = vals[(resolutionPreview.ordinal() + 1) % vals.length];
    }
}
