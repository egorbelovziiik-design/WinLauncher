package nexus.core.automation;
import nexus.api.IInput;

import nexus.api.IEntity;
import nexus.api.IItemStack;
import nexus.api.IInventory;
import nexus.api.KeyCodes;
import nexus.core.Nexus;
import nexus.util.Log;
import nexus.util.json.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * NEXUS ACTION EDITOR.
 * <p>
 * A small, deterministic automation VM: a profile is an ordered list of
 * {@link Action}s, each guarded by an optional {@link Condition}. Profiles can
 * loop, use variables and timers, and are imported / exported as JSON.
 * <p>
 * Supported actions: WalkTo, LookAt, Interact, Wait, PlaceBlock, BreakBlock,
 * OpenInventory, CraftItem, SelectSlot, UseItem.
 */
public final class MacroManager {

    // ------------------------------------------------------------- model

    public enum ActionType {
        WALK_TO("WalkTo", "Walk to x y z"),
        LOOK_AT("LookAt", "Look at x y z or an entity name"),
        INTERACT("Interact", "Right click (block, entity or air)"),
        WAIT("Wait", "Wait for a number of ticks"),
        PLACE_BLOCK("PlaceBlock", "Place the held block at x y z"),
        BREAK_BLOCK("BreakBlock", "Break the block at x y z"),
        OPEN_INVENTORY("OpenInventory", "Open the player inventory"),
        CRAFT_ITEM("CraftItem", "Craft using grid slots: output,ingredient..."),
        SELECT_SLOT("SelectSlot", "Select a hotbar slot"),
        USE_ITEM("UseItem", "Use the held item for N ticks");

        private final String label;
        private final String usage;

        ActionType(String label, String usage) {
            this.label = label;
            this.usage = usage;
        }

        public String label() {
            return label;
        }

        public String usage() {
            return usage;
        }

        public static ActionType parse(String text) {
            if (text == null) {
                return null;
            }
            for (ActionType type : values()) {
                if (type.label.equalsIgnoreCase(text.trim()) || type.name().equalsIgnoreCase(text.trim())) {
                    return type;
                }
            }
            return null;
        }
    }

    public enum ConditionType {
        ALWAYS("Always"),
        HEALTH_BELOW("HealthBelow"),
        HEALTH_ABOVE("HealthAbove"),
        HUNGER_BELOW("HungerBelow"),
        HAS_ITEM("HasItem"),
        ITEM_COUNT_BELOW("ItemCountBelow"),
        DISTANCE_BELOW("DistanceBelow"),
        TIME_PASSED("TimePassed"),
        INVENTORY_FULL("InventoryFull"),
        ENTITY_NEAR("EntityNear");

        private final String label;

        ConditionType(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public static ConditionType parse(String text) {
            if (text == null) {
                return ALWAYS;
            }
            for (ConditionType type : values()) {
                if (type.label.equalsIgnoreCase(text.trim()) || type.name().equalsIgnoreCase(text.trim())) {
                    return type;
                }
            }
            return ALWAYS;
        }
    }

    /** Guard evaluated before an action runs. */
    public static final class Condition {

        private ConditionType type = ConditionType.ALWAYS;
        private String param = "";
        private double value = 0.0;
        private boolean inverted = false;

        public Condition() {
        }

        public Condition(ConditionType type, String param, double value) {
            this.type = type;
            this.param = param;
            this.value = value;
        }

        public ConditionType type() {
            return type;
        }

        public String param() {
            return param;
        }

        public double value() {
            return value;
        }

        public boolean isInverted() {
            return inverted;
        }

        public void setInverted(boolean value) {
            inverted = value;
        }

        public boolean evaluate(Nexus nexus) {
            boolean result = evaluateInternal(nexus);
            return inverted != result;
        }

        private boolean evaluateInternal(Nexus nexus) {
            if (!nexus.client().isInGame()) {
                return type == ConditionType.TIME_PASSED;
            }
            nexus.api.IPlayer player = nexus.client().player();
            switch (type) {
                case HEALTH_BELOW:
                    return player.health() < value;
                case HEALTH_ABOVE:
                    return player.health() > value;
                case HUNGER_BELOW:
                    return player.hunger() < value;
                case HAS_ITEM:
                    return player.inventory().has(param);
                case ITEM_COUNT_BELOW:
                    return player.inventory().count(param) < value;
                case DISTANCE_BELOW: {
                    double dx = player.position().x - parseDouble(param.split(","), 0);
                    double dy = player.position().y - parseDouble(param.split(","), 1);
                    double dz = player.position().z - parseDouble(param.split(","), 2);
                    return Math.sqrt(dx * dx + dy * dy + dz * dz) < value;
                }
                case TIME_PASSED:
                    return true;
                case INVENTORY_FULL:
                    return player.inventory().isFull();
                case ENTITY_NEAR:
                    for (IEntity entity : nexus.client().world().entities()) {
                        if (entity.name() != null && entity.name().toLowerCase(Locale.ROOT).contains(param.toLowerCase(Locale.ROOT))
                                && entity.distanceToPlayer() < value) {
                            return true;
                        }
                    }
                    return false;
                default:
                    return true;
            }
        }

        private static double parseDouble(String[] parts, int index) {
            if (parts.length <= index) {
                return 0.0;
            }
            try {
                return Double.parseDouble(parts[index].trim());
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }

        public Json toJson() {
            Json.JsonObject object = Json.object();
            object.set("type", type.label());
            object.set("param", param);
            object.set("value", value);
            object.set("inverted", inverted);
            return object;
        }

        public static Condition fromJson(Json value) {
            Condition condition = new Condition();
            if (!(value instanceof Json.JsonObject)) {
                return condition;
            }
            Json.JsonObject object = (Json.JsonObject) value;
            condition.type = ConditionType.parse(object.string("type", "Always"));
            condition.param = object.string("param", "");
            condition.value = object.double_("value", 0.0);
            condition.inverted = object.bool("inverted", false);
            return condition;
        }

        @Override
        public String toString() {
            return (inverted ? "!" : "") + type.label() + (param.isEmpty() ? "" : ":" + param)
                    + (type == ConditionType.ALWAYS ? "" : ":" + value);
        }
    }

    /** A single step of a macro profile. */
    public static final class Action {

        private ActionType type;
        private final List<String> args = new ArrayList<String>();
        private Condition condition = new Condition();
        private String comment = "";

        public Action(ActionType type, String... args) {
            this.type = type;
            if (args != null) {
                for (String arg : args) {
                    this.args.add(arg);
                }
            }
        }

        public ActionType type() {
            return type;
        }

        public void setType(ActionType type) {
            this.type = type;
        }

        public List<String> args() {
            return args;
        }

        public Condition condition() {
            return condition;
        }

        public void setCondition(Condition condition) {
            this.condition = condition == null ? new Condition() : condition;
        }

        public String comment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment == null ? "" : comment;
        }

        public Json toJson() {
            Json.JsonObject object = Json.object();
            object.set("action", type.label());
            Json.JsonArray array = Json.array();
            for (String arg : args) {
                array.add(arg);
            }
            object.set("args", array);
            object.set("condition", condition.toJson());
            object.set("comment", comment);
            return object;
        }

        public static Action fromJson(Json value) {
            if (!(value instanceof Json.JsonObject)) {
                return null;
            }
            Json.JsonObject object = (Json.JsonObject) value;
            ActionType type = ActionType.parse(object.string("action", "Wait"));
            if (type == null) {
                return null;
            }
            Action action = new Action(type);
            Json argsValue = object.get("args");
            if (argsValue.isArray()) {
                Json.JsonArray array = (Json.JsonArray) argsValue;
                for (int i = 0; i < array.size(); i++) {
                    Json item = array.get(i);
                    if (item instanceof Json.JsonString) {
                        action.args.add(((Json.JsonString) item).value());
                    } else if (item instanceof Json.JsonNumber) {
                        action.args.add(String.valueOf(((Json.JsonNumber) item).asInt(0)));
                    }
                }
            }
            action.setCondition(Condition.fromJson(object.get("condition")));
            action.setComment(object.string("comment", ""));
            return action;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(type.label());
            if (!args.isEmpty()) {
                sb.append(' ');
                for (int i = 0; i < args.size(); i++) {
                    if (i > 0) {
                        sb.append(' ');
                    }
                    sb.append(args.get(i));
                }
            }
            if (condition.type() != ConditionType.ALWAYS) {
                sb.append(" [if ").append(condition.toString()).append(']');
            }
            return sb.toString();
        }
    }

    /** Named, reusable automation profile. */
    public static final class MacroProfile {

        private String name;
        private final List<Action> actions = new ArrayList<Action>();
        private final Map<String, String> variables = new LinkedHashMap<String, String>();
        private boolean loop = false;
        private int loopCount = 1;
        private boolean enabled = false;

        public MacroProfile(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<Action> actions() {
            return actions;
        }

        public Map<String, String> variables() {
            return variables;
        }

        public boolean isLoop() {
            return loop;
        }

        public void setLoop(boolean loop) {
            this.loop = loop;
        }

        public int loopCount() {
            return loopCount;
        }

        public void setLoopCount(int loopCount) {
            this.loopCount = Math.max(1, loopCount);
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean value) {
            enabled = value;
        }

        public void add(Action action) {
            actions.add(action);
        }

        public void remove(int index) {
            if (index >= 0 && index < actions.size()) {
                actions.remove(index);
            }
        }

        public void move(int from, int to) {
            if (from < 0 || from >= actions.size() || to < 0 || to >= actions.size()) {
                return;
            }
            Action action = actions.remove(from);
            actions.add(to, action);
        }

        public String variable(String key, String fallback) {
            String value = variables.get(key);
            return value == null ? fallback : value;
        }

        public void setVariable(String key, String value) {
            variables.put(key, value);
        }

        public Json toJson() {
            Json.JsonObject object = Json.object();
            object.set("name", name);
            object.set("loop", loop);
            object.set("loopCount", loopCount);
            Json.JsonArray array = Json.array();
            for (Action action : actions) {
                array.add(action.toJson());
            }
            object.set("actions", array);
            Json.JsonObject vars = Json.object();
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                vars.set(entry.getKey(), entry.getValue());
            }
            object.set("variables", vars);
            return object;
        }

        public static MacroProfile fromJson(Json value) {
            if (!(value instanceof Json.JsonObject)) {
                return null;
            }
            Json.JsonObject object = (Json.JsonObject) value;
            MacroProfile profile = new MacroProfile(object.string("name", "profile"));
            profile.setLoop(object.bool("loop", false));
            profile.setLoopCount(object.int_("loopCount", 1));
            Json actionsValue = object.get("actions");
            if (actionsValue.isArray()) {
                Json.JsonArray array = (Json.JsonArray) actionsValue;
                for (int i = 0; i < array.size(); i++) {
                    Action action = Action.fromJson(array.get(i));
                    if (action != null) {
                        profile.add(action);
                    }
                }
            }
            Json variablesValue = object.get("variables");
            if (variablesValue.isObject()) {
                Json.JsonObject vars = (Json.JsonObject) variablesValue;
                for (Map.Entry<String, Json> entry : vars.members().entrySet()) {
                    if (entry.getValue() instanceof Json.JsonString) {
                        profile.setVariable(entry.getKey(), ((Json.JsonString) entry.getValue()).value());
                    }
                }
            }
            return profile;
        }
    }

    // ------------------------------------------------------------- runtime

    /** Executes a profile step by step, tick by tick. */
    public final class Runner {

        private MacroProfile profile;
        private int index;
        private int ticksInAction;
        private int iteration;
        private boolean running;
        private boolean walking;
        private long startedAt;
        private long lastActionAt;
        private int useTicks;

        public boolean start(String profileName) {
            MacroProfile target = MacroManager.this.profile(profileName);
            if (target == null || target.actions().isEmpty()) {
                return false;
            }
            profile = target;
            index = 0;
            ticksInAction = 0;
            iteration = 1;
            running = true;
            startedAt = System.currentTimeMillis();
            lastActionAt = startedAt;
            profile.setEnabled(true);
            nexus.notifications().success("Automation", "Started '" + profile.name() + "'");
            return true;
        }

        public void stop() {
            if (walking) {
                nexus.client().input().setKeyState(KeyCodes.SPACE, false);
                nexus.client().input().releaseAllKeys();
                walking = false;
            }
            if (nexus.client().interaction() != null) {
                nexus.client().interaction().stopBreaking();
            }
            running = false;
            if (profile != null) {
                profile.setEnabled(false);
                nexus.notifications().info("Automation", "Stopped '" + profile.name() + "'");
            }
            profile = null;
        }

        public boolean isRunning() {
            return running;
        }

        public MacroProfile profile() {
            return profile;
        }

        public int index() {
            return index;
        }

        public int iteration() {
            return iteration;
        }

        public long elapsedMs() {
            return running ? System.currentTimeMillis() - startedAt : 0L;
        }

        public String currentAction() {
            if (!running || profile == null || index >= profile.actions().size()) {
                return "-";
            }
            return profile.actions().get(index).type().label();
        }

        private void tick() {
            if (!running || profile == null) {
                return;
            }
            if (!nexus.client().isInGame()) {
                stop();
                return;
            }
            if (index >= profile.actions().size()) {
                if (profile.isLoop() && (profile.loopCount() <= 0 || iteration < profile.loopCount())) {
                    iteration++;
                    index = 0;
                } else {
                    nexus.notifications().success("Automation", "Finished '" + profile.name() + "'");
                    stop();
                    return;
                }
            }
            Action action = profile.actions().get(index);
            if (!action.condition().evaluate(nexus)) {
                advance();
                return;
            }
            ticksInAction++;
            if (execute(action)) {
                advance();
            }
        }

        private void advance() {
            index++;
            ticksInAction = 0;
            useTicks = 0;
            lastActionAt = System.currentTimeMillis();
            if (walking) {
                nexus.client().input().setKeyState(KeyCodes.SPACE, false);
                walking = false;
            }
        }

        /** @return true when the action is finished. */
        private boolean execute(Action action) {
            nexus.api.IPlayer player = nexus.client().player();
            nexus.api.IWorld world = nexus.client().world();
            List<String> args = resolveArgs(action);
            switch (action.type()) {
                case WALK_TO: {
                    double[] target = coords(args);
                    nexus.api.math.Vec3d destination = new nexus.api.math.Vec3d(target[0], target[1], target[2]);
                    double distance = player.position().distanceTo(destination);
                    double tolerance = args.size() > 3 ? parseDouble(args.get(3), 1.5) : 1.5;
                    if (distance <= tolerance || ticksInAction > 3000) {
                        nexus.client().input().setKeyState(KeyCodes.SPACE, false);
                        nexus.client().input().releaseAllKeys();
                        return true;
                    }
                    player.lookAt(destination);
                    walking = true;
                    nexus.client().input().setKeyState(KeyMappingKeys.FORWARD, true);
                    if (distance > 8.0) {
                        nexus.client().input().setKeyState(KeyMappingKeys.SPRINT, true);
                    }
                    // jump over small obstacles
                    if (player.isOnGround() && world.isSolid((int) Math.floor(player.position().x
                            + Math.sin(Math.toRadians(player.yaw() + 90.0))),
                            (int) Math.floor(player.position().y),
                            (int) Math.floor(player.position().z
                                    - Math.cos(Math.toRadians(player.yaw() + 90.0))))) {
                        nexus.client().input().setKeyState(KeyCodes.SPACE, true);
                    }
                    return false;
                }
                case LOOK_AT: {
                    if (args.size() >= 3 && isNumeric(args.get(0))) {
                        double[] target = coords(args);
                        player.lookAt(new nexus.api.math.Vec3d(target[0], target[1], target[2]));
                    } else if (!args.isEmpty()) {
                        IEntity entity = findEntity(args.get(0));
                        if (entity != null) {
                            player.lookAt(entity.eyePosition());
                        }
                    }
                    return true;
                }
                case INTERACT: {
                    int ticks = args.size() > 0 ? (int) parseDouble(args.get(0), 2.0) : 2;
                    nexus.client().input().setKeyState(IInput.MOUSE_RIGHT, useTicks < ticks);
                    return ++useTicks >= ticks + 1;
                }
                case USE_ITEM: {
                    int ticks = args.size() > 0 ? (int) parseDouble(args.get(0), 5.0) : 5;
                    nexus.client().input().setKeyState(IInput.MOUSE_RIGHT, useTicks < ticks);
                    return ++useTicks >= ticks + 1;
                }
                case WAIT: {
                    double ticks = args.size() > 0 ? parseDouble(args.get(0), 20.0) : 20.0;
                    return ticksInAction >= ticks;
                }
                case PLACE_BLOCK: {
                    double[] target = coords(args);
                    int x = (int) Math.floor(target[0]);
                    int y = (int) Math.floor(target[1]);
                    int z = (int) Math.floor(target[2]);
                    if (args.size() > 3) {
                        int slot = player.inventory().findHotbar(args.get(3));
                        if (slot >= 0) {
                            player.inventory().setSelectedSlot(slot);
                        }
                    }
                    nexus.client().interaction().lookAtBlock(x, y, z);
                    nexus.client().interaction().placeBlock(x, y, z, args.size() > 4 ? (int) parseDouble(args.get(4), 1.0) : 1);
                    return !world.isAir(x, y, z) || ticksInAction > 40;
                }
                case BREAK_BLOCK: {
                    double[] target = coords(args);
                    int x = (int) Math.floor(target[0]);
                    int y = (int) Math.floor(target[1]);
                    int z = (int) Math.floor(target[2]);
                    if (world.isAir(x, y, z)) {
                        nexus.client().interaction().stopBreaking();
                        return true;
                    }
                    nexus.client().interaction().lookAtBlock(x, y, z);
                    nexus.client().interaction().breakBlock(x, y, z);
                    return ticksInAction > 400;
                }
                case OPEN_INVENTORY: {
                    nexus.client().screens().openInventoryScreen();
                    return nexus.client().interaction().openSyncId() >= 0 || ticksInAction > 20;
                }
                case SELECT_SLOT: {
                    int slot = args.isEmpty() ? 0 : (int) parseDouble(args.get(0), 0.0);
                    player.inventory().setSelectedSlot(Math.max(0, Math.min(8, slot)));
                    return true;
                }
                case CRAFT_ITEM: {
                    return craft(args);
                }
                default:
                    return true;
            }
        }

        /** Simple recipe crafting: CRAFT_ITEM outputSlot ingredientSlot... */
        private boolean craft(List<String> args) {
            IInventory inventory = nexus.client().player().inventory();
            if (nexus.client().interaction().openSyncId() < 0) {
                if (ticksInAction > 20) {
                    return true;
                }
                nexus.client().screens().openInventoryScreen();
                return false;
            }
            if (args.isEmpty()) {
                return true;
            }
            int outputSlot = (int) parseDouble(args.get(0), 0.0);
            if (ticksInAction == 1) {
                for (int i = 1; i < args.size(); i++) {
                    int slot = (int) parseDouble(args.get(i), -1.0);
                    if (slot >= 0) {
                        inventory.clickSlot(slot, 0, IInventory.ClickType.QUICK_MOVE);
                    }
                }
                return false;
            }
            if (ticksInAction == 3) {
                inventory.clickSlot(outputSlot, 0, IInventory.ClickType.QUICK_MOVE);
            }
            return ticksInAction > 5;
        }

        private List<String> resolveArgs(Action action) {
            List<String> resolved = new ArrayList<String>(action.args().size());
            for (String arg : action.args()) {
                resolved.add(resolve(arg, profile));
            }
            return resolved;
        }

        private double[] coords(List<String> args) {
            double[] result = new double[3];
            for (int i = 0; i < 3 && i < args.size(); i++) {
                result[i] = parseDouble(args.get(i), 0.0);
            }
            return result;
        }

        private IEntity findEntity(String name) {
            for (IEntity entity : nexus.client().world().entities()) {
                if (entity.name() != null && entity.name().equalsIgnoreCase(name)) {
                    return entity;
                }
            }
            return null;
        }
    }

    /** Virtual key ids resolved by the adapter to the vanilla key bindings. */
    public static final class KeyMappingKeys {
        public static final int FORWARD = -200;
        public static final int BACK = -201;
        public static final int LEFT = -202;
        public static final int RIGHT = -203;
        public static final int JUMP = -204;
        public static final int SNEAK = -205;
        public static final int SPRINT = -206;
        public static final int ATTACK = -207;
        public static final int USE = -208;
    }

    // --------------------------------------------------------------- manager

    private final Nexus nexus;
    private final Map<String, MacroProfile> profiles = new LinkedHashMap<String, MacroProfile>();
    private final Runner runner;
    private boolean masterEnabled = true;

    public MacroManager(Nexus nexus) {
        this.nexus = nexus;
        this.runner = new Runner();
    }

    public Runner runner() {
        return runner;
    }

    public boolean isMasterEnabled() {
        return masterEnabled;
    }

    public void setMasterEnabled(boolean value) {
        masterEnabled = value;
        if (!value) {
            stopAll();
        }
    }

    public MacroProfile create(String name) {
        MacroProfile profile = new MacroProfile(name);
        profiles.put(name.toLowerCase(Locale.ROOT), profile);
        nexus.configManager().markDirty();
        return profile;
    }

    public MacroProfile profile(String name) {
        return profiles.get(name == null ? "" : name.toLowerCase(Locale.ROOT));
    }

    public List<MacroProfile> profiles() {
        return new ArrayList<MacroProfile>(profiles.values());
    }

    public boolean delete(String name) {
        MacroProfile removed = profiles.remove(name == null ? "" : name.toLowerCase(Locale.ROOT));
        if (removed != null) {
            nexus.configManager().markDirty();
        }
        return removed != null;
    }

    public boolean start(String name) {
        if (!masterEnabled) {
            nexus.notifications().warn("Automation", "Automation is disabled");
            return false;
        }
        if (runner.isRunning()) {
            runner.stop();
        }
        return runner.start(name);
    }

    public void stopAll() {
        runner.stop();
        for (MacroProfile profile : profiles.values()) {
            profile.setEnabled(false);
        }
        if (nexus.client() != null && nexus.client().input() != null) {
            nexus.client().input().releaseAllKeys();
        }
    }

    public void onTick() {
        if (!masterEnabled) {
            return;
        }
        runner.tick();
    }

    public int size() {
        return profiles.size();
    }

    // ------------------------------------------------------------------ helpers

    /** Replaces {@code ${variable}} occurrences with the profile variable value. */
    public static String resolve(String text, MacroProfile profile) {
        if (text == null || profile == null || !text.contains("${")) {
            return text;
        }
        String result = text;
        for (Map.Entry<String, String> entry : profile.variables().entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private static double parseDouble(String text, double fallback) {
        try {
            return Double.parseDouble(text.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static boolean isNumeric(String text) {
        try {
            Double.parseDouble(text.trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ config

    public Json toJson() {
        Json.JsonObject object = Json.object();
        object.set("masterEnabled", masterEnabled);
        Json.JsonObject all = Json.object();
        for (Map.Entry<String, MacroProfile> entry : profiles.entrySet()) {
            all.set(entry.getKey(), entry.getValue().toJson());
        }
        object.set("profiles", all);
        return object;
    }

    public void fromJson(Json value) {
        if (!(value instanceof Json.JsonObject)) {
            return;
        }
        Json.JsonObject object = (Json.JsonObject) value;
        profiles.clear();
        masterEnabled = object.bool("masterEnabled", true);
        Json profilesValue = object.get("profiles");
        if (profilesValue.isObject()) {
            Json.JsonObject all = (Json.JsonObject) profilesValue;
            for (Map.Entry<String, Json> entry : all.members().entrySet()) {
                MacroProfile profile = MacroProfile.fromJson(entry.getValue());
                if (profile != null) {
                    profiles.put(profile.name().toLowerCase(Locale.ROOT), profile);
                }
            }
        }
        if (!profiles.isEmpty()) {
            Log.info("Automation profiles loaded: " + profiles.size());
        }
    }

    /** Creates a ready to use example profile (farm walk, mine block, ...). */
    public MacroProfile createExample() {
        MacroProfile profile = create("example");
        profile.setVariable("radius", "6");
        profile.add(new Action(ActionType.SELECT_SLOT, "0"));
        profile.add(new Action(ActionType.WALK_TO, "0", "64", "0", "2"));
        profile.add(new Action(ActionType.LOOK_AT, "0", "64", "4"));
        profile.add(new Action(ActionType.BREAK_BLOCK, "0", "64", "4"));
        profile.add(new Action(ActionType.WAIT, "10"));
        profile.add(new Action(ActionType.WALK_TO, "0", "64", "8", "2"));
        profile.setLoop(true);
        profile.setLoopCount(3);
        return profile;
    }
}
