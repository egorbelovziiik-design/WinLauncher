package nexus.core.hud;

import nexus.core.Nexus;
import nexus.core.event.Render2DEvent;
import nexus.core.hud.elements.*;
import nexus.core.setting.BooleanSetting;
import nexus.core.setting.NumberSetting;
import nexus.util.ColorUtil;
import nexus.util.Log;
import nexus.util.MathUtil;
import nexus.util.json.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HUD registry, renderer and layout manager (including the undo / redo stack
 * used by the HUD editor).
 */
public final class HudManager {

    private static final int MAX_HISTORY = 40;

    private final Nexus nexus;
    private final Map<String, HudElement> elements = new LinkedHashMap<String, HudElement>();
    private final List<Json> undoStack = new ArrayList<Json>();
    private final List<Json> redoStack = new ArrayList<Json>();

    private final BooleanSetting visible;
    private final BooleanSetting snapToGrid;
    private final NumberSetting gridSize;
    private final BooleanSetting alignmentGuides;
    private final NumberSetting globalScale;

    private boolean editorOpen;

    public HudManager(Nexus nexus) {
        this.nexus = nexus;
        this.visible = new BooleanSetting("HUD Visible", "Render the HUD", true);
        this.snapToGrid = new BooleanSetting("Snap To Grid", "Snap elements while dragging", true);
        this.gridSize = new NumberSetting("Grid Size", "Snap grid step in pixels", 5.0, 1.0, 40.0, 1.0);
        this.alignmentGuides = new BooleanSetting("Alignment Guides", "Show alignment helpers", true);
        this.globalScale = new NumberSetting("Global Scale", "Scales every element", 1.0, 0.5, 2.5, 0.05);
    }

    // --------------------------------------------------------------- registry

    public void registerDefaults() {
        // A fresh install starts clean: only the watermark and the target HUD
        // are visible, everything else is one click away in the HUD editor.
        // Every element still gets its own default spot, so enabling a second
        // one never stacks it on top of another (that used to make the whole
        // top-left corner unreadable).
        register(new WatermarkElement(), 4, 4, true);
        register(new TargetHudElement(), 120, 140, true);
        register(new ArrayListElement(), 4, 30, false);
        register(new CoordinatesElement(), 4, 200, false);
        register(new CoordinatesElement.Nether(), 4, 216, false);
        register(new FpsElement(), 4, 232, false);
        register(new PingElement(), 4, 248, false);
        register(new CpsElement(), 4, 264, false);
        register(new KeystrokesElement(), 4, 90, false);
        register(new ArmorElement(), 90, 4, false);
        register(new PotionsElement(), 200, 4, false);
        register(new SessionInfoElement(), 200, 40, false);
        register(new ServerInfoElement(), 200, 70, false);
        register(new CompassElement(), 200, 100, false);
        register(new RadarElement(), 200, 220, false);
        register(new InventoryHudElement(), 340, 4, false);
        register(new TotemCounterElement(), 340, 60, false);
        register(new ClockElement(), 340, 80, false);
        register(new BiomeElement(), 340, 96, false);
        register(new DirectionElement(), 340, 112, false);
        register(new DurabilityElement(), 340, 128, false);
        register(new SpeedometerElement(), 340, 144, false);
        register(new MemoryUsageElement(), 340, 160, false);
        register(new CpuUsageElement(), 340, 176, false);
        register(new ModuleListElement(), 340, 200, false);
        register(new NotificationsElement(), 340, 240, false);
        Log.info("Registered " + elements.size() + " HUD elements ("
                + enabledCount() + " visible by default)");
    }

    /** Registers an element with its default position and visibility. */
    private void register(HudElement element, double x, double y, boolean enabled) {
        element.setPosition(x, y);
        element.setEnabled(enabled);
        register(element);
    }

    public void register(HudElement element) {
        elements.put(element.id(), element);
    }

    public HudElement byId(String id) {
        return elements.get(id);
    }

    public HudElement byName(String name) {
        if (name == null) {
            return null;
        }
        for (HudElement element : elements.values()) {
            if (element.displayName().equalsIgnoreCase(name) || element.id().equalsIgnoreCase(name)) {
                return element;
            }
        }
        return null;
    }

    public List<HudElement> all() {
        return new ArrayList<HudElement>(elements.values());
    }

    public List<HudElement> enabled() {
        List<HudElement> list = new ArrayList<HudElement>();
        for (HudElement element : elements.values()) {
            if (element.isEnabled()) {
                list.add(element);
            }
        }
        return list;
    }

    public int size() {
        return elements.size();
    }

    public int enabledCount() {
        return enabled().size();
    }

    // ---------------------------------------------------------------- settings

    public boolean isVisible() {
        return visible.isEnabled();
    }

    public void setVisible(boolean value) {
        visible.set(Boolean.valueOf(value));
    }

    public void toggleVisible() {
        setVisible(!isVisible());
    }

    public boolean snapToGrid() {
        return snapToGrid.isEnabled();
    }

    public double gridSize() {
        return gridSize.value();
    }

    public boolean alignmentGuides() {
        return alignmentGuides.isEnabled();
    }

    public double globalScale() {
        return globalScale.value();
    }

    public List<nexus.core.setting.Setting<?>> settings() {
        List<nexus.core.setting.Setting<?>> list = new ArrayList<nexus.core.setting.Setting<?>>();
        list.add(visible);
        list.add(snapToGrid);
        list.add(gridSize);
        list.add(alignmentGuides);
        list.add(globalScale);
        return list;
    }

    public boolean isEditorOpen() {
        return editorOpen;
    }

    public void setEditorOpen(boolean value) {
        editorOpen = value;
    }

    // ----------------------------------------------------------------- render

    public void render(Render2DEvent event) {
        if (!visible.isEnabled() || !nexus.isInitializedInstance()) {
            return;
        }
        int width = event.width;
        int height = event.height;
        for (HudElement element : elements.values()) {
            if (!element.isEnabled()) {
                continue;
            }
            double maxX = width - element.width() - 2.0;
            double maxY = height - element.height() - 2.0;
            if (element.x() > maxX) {
                element.setPosition(Math.max(2.0, maxX), element.y());
            }
            if (element.y() > maxY) {
                element.setPosition(element.x(), Math.max(2.0, maxY));
            }
            try {
                element.setEditing(chatDragActive || editorOpen);
                element.render(event);
                element.setEditing(false);
            } catch (Throwable throwable) {
                Log.error("HUD element " + element.id() + " failed to render", throwable);
            }
        }
        renderChatDragOverlay(event.renderer, width, height);
    }

    public void onTick() {
        for (HudElement element : elements.values()) {
            if (element.isEnabled()) {
                try {
                    element.onTick();
                } catch (Throwable throwable) {
                    Log.error("HUD element " + element.id() + " tick failed", throwable);
                }
            }
        }
    }

    // ------------------------------------------------------------ editor tools

    /** Snaps a coordinate to the configured grid. */
    public double snap(double value) {
        if (!snapToGrid.isEnabled()) {
            return value;
        }
        double step = Math.max(1.0, gridSize.value());
        return Math.round(value / step) * step;
    }

    /** Vertical / horizontal guide lines close to the dragged element. */
    public List<Double> guidesX(HudElement dragged) {
        List<Double> guides = new ArrayList<Double>();
        if (!alignmentGuides.isEnabled() || dragged == null) {
            return guides;
        }
        int center = nexus.client().window().guiWidth() / 2;
        guides.add((double) center);
        for (HudElement element : elements.values()) {
            if (element == dragged || !element.isEnabled()) {
                continue;
            }
            guides.add(element.x());
            guides.add(element.x() + element.width() / 2.0);
        }
        return guides;
    }

    public List<Double> guidesY(HudElement dragged) {
        List<Double> guides = new ArrayList<Double>();
        if (!alignmentGuides.isEnabled() || dragged == null) {
            return guides;
        }
        int center = nexus.client().window().guiHeight() / 2;
        guides.add((double) center);
        for (HudElement element : elements.values()) {
            if (element == dragged || !element.isEnabled()) {
                continue;
            }
            guides.add(element.y());
            guides.add(element.y() + element.height() / 2.0);
        }
        return guides;
    }

    // --------------------------------------------------------------- history

    public Json snapshot() {
        Json.JsonObject object = Json.object();
        for (Map.Entry<String, HudElement> entry : elements.entrySet()) {
            object.set(entry.getKey(), entry.getValue().toJson());
        }
        return object;
    }

    public void pushUndo() {
        undoStack.add(snapshot());
        if (undoStack.size() > MAX_HISTORY) {
            undoStack.remove(0);
        }
        redoStack.clear();
    }

    public boolean undo() {
        if (undoStack.isEmpty()) {
            return false;
        }
        redoStack.add(snapshot());
        Json state = undoStack.remove(undoStack.size() - 1);
        applyLayout(state);
        return true;
    }

    public boolean redo() {
        if (redoStack.isEmpty()) {
            return false;
        }
        undoStack.add(snapshot());
        Json state = redoStack.remove(redoStack.size() - 1);
        applyLayout(state);
        return true;
    }

    public int undoSize() {
        return undoStack.size();
    }

    public int redoSize() {
        return redoStack.size();
    }

    public void resetLayout() {
        pushUndo();
        for (HudElement element : elements.values()) {
            element.setEnabled(false);
        }
        HudElement watermark = elements.get("watermark");
        if (watermark != null) {
            watermark.setPosition(4, 4);
            watermark.setEnabled(true);
        }
        HudElement target = elements.get("targethud");
        if (target != null) {
            target.setPosition(120, 140);
            target.setEnabled(true);
        }
        nexus.notifications().info("HUD", "Layout reset");
    }

    // ------------------------------------------------------- chat drag & drop

    /**
     * While the chat is open the player can grab any HUD element and move it
     * with the mouse - no need to open the ClickGUI first.
     */
    private boolean chatDragActive;
    private HudElement dragging;
    private double dragOffsetX;
    private double dragOffsetY;
    private boolean dragMoved;
    private double lastMouseX;
    private double lastMouseY;

    public boolean isChatDragActive() {
        return chatDragActive;
    }

    /** Called once per tick with the state of the vanilla chat screen. */
    public void setChatDragActive(boolean active) {
        if (this.chatDragActive == active) {
            return;
        }
        this.chatDragActive = active;
        if (!active) {
            endDrag();
        }
    }

    public boolean isDragging() {
        return dragging != null;
    }

    /** Topmost enabled element under the cursor, or null. */
    public HudElement elementAt(double mouseX, double mouseY) {
        HudElement found = null;
        for (HudElement element : elements.values()) {
            if (!element.isEnabled()) {
                continue;
            }
            // a little padding makes thin elements grabbable
            if (mouseX >= element.x() - 2.0 && mouseX <= element.x() + element.width() + 2.0
                    && mouseY >= element.y() - 2.0 && mouseY <= element.y() + element.height() + 2.0) {
                found = element;
            }
        }
        return found;
    }

    /**
     * Chat mouse press. Returns true when a HUD element was grabbed (the chat
     * then ignores the click).
     */
    public boolean chatMousePressed(double mouseX, double mouseY, int button) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (!chatDragActive || button != 0) {
            return false;
        }
        HudElement element = elementAt(mouseX, mouseY);
        if (element == null) {
            return false;
        }
        dragging = element;
        dragOffsetX = mouseX - element.x();
        dragOffsetY = mouseY - element.y();
        dragMoved = false;
        return true;
    }

    /** Chat mouse drag. Returns true while an element is being moved. */
    public boolean chatMouseMoved(double mouseX, double mouseY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (dragging == null) {
            return false;
        }
        if (!dragMoved) {
            pushUndo();
            dragMoved = true;
        }
        double newX = mouseX - dragOffsetX;
        double newY = mouseY - dragOffsetY;
        if (snapToGrid.isEnabled()) {
            newX = snap(newX);
            newY = snap(newY);
        }
        int guiWidth = nexus.client() != null && nexus.client().window() != null
                ? nexus.client().window().guiWidth() : 1920;
        int guiHeight = nexus.client() != null && nexus.client().window() != null
                ? nexus.client().window().guiHeight() : 1080;
        dragging.setPosition(
                Math.max(0.0, Math.min(newX, guiWidth - dragging.width())),
                Math.max(0.0, Math.min(newY, guiHeight - dragging.height())));
        return true;
    }

    /** Chat mouse release. Saves the layout when something actually moved. */
    public boolean chatMouseReleased() {
        if (dragging == null) {
            return false;
        }
        boolean moved = dragMoved;
        endDrag();
        if (moved) {
            nexus.configManager().markDirty();
            nexus.configManager().saveAll(false);
            nexus.notifications().info("HUD", "Layout saved");
        }
        return true;
    }

    private void endDrag() {
        dragging = null;
        dragMoved = false;
    }

    /** Hint + outlines drawn on top of the HUD while the chat is open. */
    public void renderChatDragOverlay(nexus.api.IRenderer renderer, int width, int height) {
        if (!chatDragActive) {
            return;
        }
        int accent = nexus.themes().accent(System.currentTimeMillis());
        for (HudElement element : elements.values()) {
            if (!element.isEnabled()) {
                continue;
            }
            boolean active = element == dragging;
            int color = active ? accent : ColorUtil.withAlpha(accent, 90);
            double x = element.x() - 2.0;
            double y = element.y() - 2.0;
            double w = element.width() + 4.0;
            double h = element.height() + 4.0;
            renderer.outlineRect(x, y, w, h, active ? 1.5 : 1.0, color);
            if (active) {
                for (Double guideX : guidesX(element)) {
                    if (Math.abs(guideX - element.x()) < 3.0
                            || Math.abs(guideX - (element.x() + element.width() / 2.0)) < 3.0) {
                        renderer.fillRect(guideX, 0.0, 1.0, height, ColorUtil.withAlpha(accent, 120));
                    }
                }
                for (Double guideY : guidesY(element)) {
                    if (Math.abs(guideY - element.y()) < 3.0
                            || Math.abs(guideY - (element.y() + element.height() / 2.0)) < 3.0) {
                        renderer.fillRect(0.0, guideY, width, 1.0, ColorUtil.withAlpha(accent, 120));
                    }
                }
            }
        }
        String hint = "HUD: перетащи элемент мышью  |  ESC закрыть чат";
        double hintWidth = renderer.textWidth(hint, 1.0);
        renderer.fillRoundRect((width - hintWidth) / 2.0 - 8.0, 6.0, hintWidth + 16.0, 16.0, 5.0,
                ColorUtil.withAlpha(0xFF000000, 150));
        renderer.drawCenteredText(hint, width / 2.0, 10.0, 0xFFFFFFFF, true, 1.0);
    }

    // ------------------------------------------------------------------ config

    public Json toJson() {
        Json.JsonObject object = Json.object();
        object.set("visible", visible.toJson());
        object.set("snapToGrid", snapToGrid.toJson());
        object.set("gridSize", gridSize.toJson());
        object.set("alignmentGuides", alignmentGuides.toJson());
        object.set("globalScale", globalScale.toJson());
        return object;
    }

    public void fromJson(Json value) {
        if (!(value instanceof Json.JsonObject)) {
            return;
        }
        Json.JsonObject object = (Json.JsonObject) value;
        visible.fromJson(object.get("visible"));
        snapToGrid.fromJson(object.get("snapToGrid"));
        gridSize.fromJson(object.get("gridSize"));
        alignmentGuides.fromJson(object.get("alignmentGuides"));
        globalScale.fromJson(object.get("globalScale"));
    }

    public Json layoutToJson() {
        Json.JsonObject object = Json.object();
        for (Map.Entry<String, HudElement> entry : elements.entrySet()) {
            object.set(entry.getKey(), entry.getValue().toJson());
        }
        return object;
    }

    public void layoutFromJson(Json value) {
        if (value instanceof Json.JsonObject) {
            applyLayout(value);
        }
    }

    /** Applies a stored layout, clamping every element back into the screen. */
    public void applyLayout(Json value) {
        if (!(value instanceof Json.JsonObject)) {
            return;
        }
        Json.JsonObject object = (Json.JsonObject) value;
        int width = 320;
        int height = 240;
        if (nexus.isInitializedInstance() && nexus.client() != null && nexus.client().window() != null) {
            width = Math.max(1, nexus.client().window().guiWidth());
            height = Math.max(1, nexus.client().window().guiHeight());
        }
        for (Map.Entry<String, Json> entry : object.members().entrySet()) {
            HudElement element = elements.get(entry.getKey());
            if (element == null) {
                continue;
            }
            element.fromJson(entry.getValue());
            double clampedX = MathUtil.clamp(element.x(), 0.0, Math.max(0.0, width - element.width()));
            double clampedY = MathUtil.clamp(element.y(), 0.0, Math.max(0.0, height - element.height()));
            element.setPosition(clampedX, clampedY);
        }
    }
}
