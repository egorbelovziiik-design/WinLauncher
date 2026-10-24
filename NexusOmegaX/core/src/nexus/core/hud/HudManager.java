package nexus.core.hud;

import nexus.core.Nexus;
import nexus.core.event.Render2DEvent;
import nexus.core.hud.elements.*;
import nexus.core.setting.BooleanSetting;
import nexus.core.setting.NumberSetting;
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
        register(new WatermarkElement());
        register(new ArrayListElement());
        register(new CoordinatesElement());
        register(new FpsElement());
        register(new PingElement());
        register(new CpsElement());
        register(new KeystrokesElement());
        register(new ArmorElement());
        register(new PotionsElement());
        register(new TargetHudElement());
        register(new SessionInfoElement());
        register(new ServerInfoElement());
        register(new CompassElement());
        register(new RadarElement());
        register(new InventoryHudElement());
        register(new TotemCounterElement());
        register(new ClockElement());
        register(new BiomeElement());
        register(new DirectionElement());
        register(new DurabilityElement());
        register(new SpeedometerElement());
        register(new MemoryUsageElement());
        register(new CpuUsageElement());
        register(new ModuleListElement());
        register(new NotificationsElement());
        register(new CoordinatesElement.Nether());
        Log.info("Registered " + elements.size() + " HUD elements");
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
                element.render(event);
            } catch (Throwable throwable) {
                Log.error("HUD element " + element.id() + " failed to render", throwable);
            }
        }
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
        int index = 0;
        for (HudElement element : elements.values()) {
            element.setPosition(6.0, 6.0 + index * 22.0);
            index++;
        }
        nexus.notifications().info("HUD", "Layout reset");
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
