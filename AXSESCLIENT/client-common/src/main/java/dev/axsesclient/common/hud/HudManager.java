package dev.axsesclient.common.hud;

import dev.axsesclient.common.config.ConfigManager;
import dev.axsesclient.common.i18n.LocalizationManager;
import dev.axsesclient.common.notifications.NotificationManager;
import dev.axsesclient.common.platform.MinecraftAdapter;
import dev.axsesclient.common.platform.RenderAdapter;
import dev.axsesclient.common.render.FontManager;
import dev.axsesclient.common.render.RenderManager;
import dev.axsesclient.common.theme.AxsesTheme;
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

    // Live In-Chat ('T' / ChatScreen) HUD Dragging & Interactive Widget State
    private boolean chatHudMoverEnabled = true;
    private boolean chatSnapGuides = true;
    private boolean chatHoverFrames = true;
    private boolean chatTopBanner = true;
    private boolean chatRightClickLock = true;
    private HudElement chatDraggedElement = null;
    private float chatDragOffsetX = 0.0f;
    private float chatDragOffsetY = 0.0f;
    private boolean chatVertCenterGuide = false;
    private boolean chatHorizCenterGuide = false;

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

    public void configureChatMover(boolean enabled, boolean snapGuides, boolean hoverFrames, boolean topBanner, boolean rightClickLock) {
        this.chatHudMoverEnabled = enabled;
        this.chatSnapGuides = snapGuides;
        this.chatHoverFrames = hoverFrames;
        this.chatTopBanner = topBanner;
        this.chatRightClickLock = rightClickLock;
        if (!enabled) {
            this.chatDraggedElement = null;
            this.chatVertCenterGuide = false;
            this.chatHorizCenterGuide = false;
        }
    }

    public boolean isChatHudMoverEnabled() {
        return chatHudMoverEnabled;
    }

    public HudElement getChatDraggedElement() {
        return chatDraggedElement;
    }

    public void setChatDragShowcaseState(HudElement target, boolean vertGuide, boolean horizGuide) {
        this.chatDraggedElement = target;
        this.chatVertCenterGuide = vertGuide;
        this.chatHorizCenterGuide = horizGuide;
    }

    public boolean onChatMousePress(
            MinecraftAdapter mc,
            ConfigManager cfg,
            NotificationManager notif,
            LocalizationManager i18n,
            double mx,
            double my,
            int button
    ) {
        if (mc == null || !mc.isChatScreenOpen()) {
            return false;
        }
        float sw = Math.max(1.0f, (float) mc.getScaledWidth());
        float sh = Math.max(1.0f, (float) mc.getScaledHeight());

        // Never intercept clicks inside the bottom 22px vanilla Chat input strip
        if (my >= sh - 22.0) {
            return false;
        }

        List<HudElement> all = getAll();
        for (int i = all.size() - 1; i >= 0; i--) {
            HudElement el = all.get(i);
            if (!el.isVisibleInGame()) {
                continue;
            }
            float px = el.getPixelX(sw);
            float py = el.getPixelY(sh);
            float ew = el.getScaledWidth();
            float eh = el.getScaledHeight();
            if (mx >= px && mx <= px + ew && my >= py && my <= py + eh) {
                // 1. Interactive buttons inside HUD cards (Spotify Play/Pause/Stop/Next/Prev, Baritone Stop/Pause)
                if (el.getInteractiveHandler() != null) {
                    boolean handledBtn = el.getInteractiveHandler().onMouseClick(
                            mc, (float) (mx - px), (float) (my - py), ew, eh, button
                    );
                    if (handledBtn) {
                        mc.playUiClickSound(1.08f);
                        return true;
                    }
                }

                // 2. Chat HUD Mover (Left-click drag / Right-click lock)
                if (!chatHudMoverEnabled) {
                    continue;
                }
                if (button == 1 && chatRightClickLock) {
                    el.setLocked(!el.isLocked());
                    mc.playUiClickSound(0.95f);
                    if (notif != null) {
                        boolean ru = i18n == null || i18n.isRussian();
                        notif.push(
                                el.getTitle(),
                                el.isLocked() ? (ru ? "Виджет закреплён в чате" : "Pinned in Chat") : (ru ? "Виджет разблокирован" : "Unlocked in Chat"),
                                NotificationManager.Severity.INFO
                        );
                    }
                    if (cfg != null) {
                        cfg.saveActiveAsync();
                    }
                    return true;
                }
                if (button == 0 && !el.isLocked()) {
                    chatDraggedElement = el;
                    chatDragOffsetX = (float) mx - px;
                    chatDragOffsetY = (float) my - py;
                    mc.playUiClickSound(1.02f);
                    return true;
                }
            }
        }
        return false;
    }

    public void onChatMouseRelease(ConfigManager cfg, int button) {
        if (button == 0 && chatDraggedElement != null) {
            chatDraggedElement = null;
            chatVertCenterGuide = false;
            chatHorizCenterGuide = false;
            if (cfg != null) {
                cfg.saveActiveAsync();
            }
        }
    }

    public void renderInGameHud(RenderAdapter r, MinecraftAdapter mc, ThemeManager themeManager, FontManager fontManager) {
        renderInGameHud(r, mc, themeManager, fontManager, null);
    }

    public void renderInGameHud(
            RenderAdapter r,
            MinecraftAdapter mc,
            ThemeManager themeManager,
            FontManager fontManager,
            LocalizationManager i18n
    ) {
        float w = r.getScaledWidth();
        float h = r.getScaledHeight();
        boolean chatOpen = mc != null && mc.isChatScreenOpen();
        if (!chatOpen && chatDraggedElement != null) {
            chatDraggedElement = null;
            chatVertCenterGuide = false;
            chatHorizCenterGuide = false;
        }

        float cmx = chatOpen ? (float) mc.getScaledMouseX() : -100.0f;
        float cmy = chatOpen ? (float) mc.getScaledMouseY() : -100.0f;

        if (chatOpen && chatHudMoverEnabled && chatDraggedElement != null && cmx >= 0.0f && cmy >= 0.0f) {
            boolean[] guides = moveElementWithSnapping(
                    chatDraggedElement,
                    cmx - chatDragOffsetX,
                    cmy - chatDragOffsetY,
                    w,
                    h
            );
            chatVertCenterGuide = guides[0];
            chatHorizCenterGuide = guides[1];
        }

        AxsesTheme t = themeManager.getActiveTheme();
        int prim = themeManager.filterColor(t.getPrimaryColor());
        int acc = themeManager.filterColor(t.getAccentColor());

        if (chatOpen && chatHudMoverEnabled && chatSnapGuides) {
            if (chatVertCenterGuide) {
                r.fillRect(w * 0.5f - 0.5f, 0.0f, 1.0f, h - 22.0f, AxsesTheme.withAlpha(acc, 0.78f));
            }
            if (chatHorizCenterGuide) {
                r.fillRect(0.0f, h * 0.5f - 0.5f, w, 1.0f, AxsesTheme.withAlpha(acc, 0.78f));
            }
        }

        HudElement hoveredInChat = null;
        if (chatOpen && chatHudMoverEnabled && chatDraggedElement == null && cmx >= 0.0f && cmy >= 0.0f && cmy < h - 22.0f) {
            List<HudElement> list = getAll();
            for (int i = list.size() - 1; i >= 0; i--) {
                HudElement el = list.get(i);
                if (!el.isVisibleInGame()) continue;
                float px = el.getPixelX(w);
                float py = el.getPixelY(h);
                if (cmx >= px && cmx <= px + el.getScaledWidth() && cmy >= py && cmy <= py + el.getScaledHeight()) {
                    hoveredInChat = el;
                    break;
                }
            }
        }

        for (HudElement el : elements.values()) {
            boolean activeSel = chatOpen && (el == chatDraggedElement || (chatHoverFrames && el == hoveredInChat));
            el.renderWidget(r, mc, themeManager, fontManager, w, h, false, activeSel);
            if (activeSel && el.isVisibleInGame()) {
                float px = el.getPixelX(w);
                float py = el.getPixelY(h);
                float ew = el.getScaledWidth();
                float eh = el.getScaledHeight();
                int frameCol = (el == chatDraggedElement) ? acc : AxsesTheme.withAlpha(prim, 0.80f);
                r.drawRoundedOutline(px - 2.0f, py - 2.0f, ew + 4.0f, eh + 4.0f, 10.0f, 1.2f, frameCol);
                if (el == chatDraggedElement || el == hoveredInChat) {
                    boolean ru = i18n == null || i18n.isRussian();
                    String tag = el.isLocked()
                            ? (ru ? "🔒 Закреплён (ПКМ)" : "🔒 Locked (RMB)")
                            : (el == chatDraggedElement
                                    ? (ru ? "✦ Перемещение · " + Math.round(px) + "," + Math.round(py) : "✦ Moving · " + Math.round(px) + "," + Math.round(py))
                                    : (ru ? "✦ ЛКМ: двигать · ПКМ: замок" : "✦ LMB: Drag · RMB: Lock"));
                    float tw = fontManager.width(r, tag, 0.78f) + 12.0f;
                    float ty = py >= 16.0f ? (py - 14.0f) : (py + eh + 3.0f);
                    r.fillRoundedRect(px, ty, tw, 12.5f, 6.0f, 0xE6050816);
                    r.drawRoundedOutline(px, ty, tw, 12.5f, 6.0f, 1.0f, frameCol);
                    fontManager.draw(r, tag, px + 6.0f, ty + 3.0f, 0xFFF8FAFF, 0.78f);
                }
            }
        }

        // Compact Top-Center Chat HUD Mover Banner
        if (chatOpen && chatHudMoverEnabled && chatTopBanner) {
            boolean ru = i18n == null || i18n.isRussian();
            String hint = ru
                    ? "✦ ЧАТ-РЕЖИМ HUD: перетаскивайте любые виджеты ЛКМ · Кнопки Spotify и Баритона кликабельны"
                    : "✦ CHAT HUD MODE: Drag any widget with LMB · Spotify & Baritone controls are clickable";
            float bw = fontManager.width(r, hint, 0.82f) + 22.0f;
            float bx = (w - bw) * 0.5f;
            float by = 30.0f;
            r.fillRoundedRect(bx, by, bw, 18.0f, 9.0f, 0xE4060A18);
            RenderManager.fillRoundedHorizontalGradient(r, bx + 10.0f, by + 1.0f, bw - 20.0f, 1.5f, 0.75f, prim, acc);
            r.drawRoundedOutline(bx, by, bw, 18.0f, 9.0f, 1.0f, AxsesTheme.withAlpha(acc, 0.75f));
            fontManager.drawCentered(r, hint, w * 0.5f, by + 5.2f, 0xFFF8FAFF, 0.82f);
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
