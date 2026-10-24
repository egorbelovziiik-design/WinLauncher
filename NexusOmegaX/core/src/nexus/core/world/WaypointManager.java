package nexus.core.world;

import nexus.core.Nexus;
import nexus.util.ColorUtil;
import nexus.util.json.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Waypoints with dimension isolation, colors and share codes. */
public final class WaypointManager {

    public static final class Waypoint {

        private String name;
        private final double x;
        private final double y;
        private final double z;
        private String dimension;
        private int color;
        private boolean enabled = true;
        private final long createdAt;

        public Waypoint(String name, double x, double y, double z, String dimension, int color) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimension = dimension;
            this.color = color;
            this.createdAt = System.currentTimeMillis();
        }

        public String name() {
            return name;
        }

        public double x() {
            return x;
        }

        public double y() {
            return y;
        }

        public double z() {
            return z;
        }

        public String dimension() {
            return dimension;
        }

        public int color() {
            return color;
        }

        public void setColor(int color) {
            this.color = color;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean value) {
            enabled = value;
        }

        public long createdAt() {
            return createdAt;
        }

        public Json toJson() {
            Json.JsonObject object = Json.object();
            object.set("name", name);
            object.set("x", x);
            object.set("y", y);
            object.set("z", z);
            object.set("dimension", dimension);
            object.set("color", ColorUtil.toHex(color));
            object.set("enabled", enabled);
            object.set("created", createdAt);
            return object;
        }

        public static Waypoint fromJson(Json value) {
            if (!(value instanceof Json.JsonObject)) {
                return null;
            }
            Json.JsonObject object = (Json.JsonObject) value;
            Waypoint waypoint = new Waypoint(
                    object.string("name", "waypoint"),
                    object.double_("x", 0.0),
                    object.double_("y", 64.0),
                    object.double_("z", 0.0),
                    object.string("dimension", "minecraft:overworld"),
                    ColorUtil.parseHex(object.string("color", "#FFA855F7"), 0xFFA855F7));
            waypoint.setEnabled(object.bool("enabled", true));
            return waypoint;
        }

        /** Share code: {@code name:x:y:z:dimension:hex}. */
        public String toShareCode() {
            return name + ":" + (int) x + ":" + (int) y + ":" + (int) z + ":" + dimension + ":" + ColorUtil.toHexRgb(color);
        }

        public static Waypoint fromShareCode(String code) {
            if (code == null) {
                return null;
            }
            String[] parts = code.split(":");
            if (parts.length < 5) {
                return null;
            }
            try {
                String name = parts[0];
                double x = Double.parseDouble(parts[1]);
                double y = Double.parseDouble(parts[2]);
                double z = Double.parseDouble(parts[3]);
                String dimension = parts.length > 5 ? parts[4] + ":" + parts[5] : parts[4];
                int color = parts.length > 6 ? ColorUtil.parseHex(parts[parts.length - 1], 0xFFA855F7) : 0xFFA855F7;
                return new Waypoint(name, x, y, z, dimension, color);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    private final Nexus nexus;
    private final List<Waypoint> waypoints = new ArrayList<Waypoint>();
    private boolean renderEnabled = true;
    private boolean renderBeacon = true;
    private boolean renderTracer = false;
    private double maxDistance = 2048.0;

    public WaypointManager(Nexus nexus) {
        this.nexus = nexus;
    }

    public Waypoint add(String name, double x, double y, double z, String dimension, int color) {
        Waypoint waypoint = new Waypoint(name, x, y, z, dimension, color);
        waypoints.add(waypoint);
        nexus.configManager().markDirty();
        nexus.notifications().success("Waypoint", "Added " + name);
        return waypoint;
    }

    public Waypoint addHere(String name) {
        nexus.api.IPlayer player = nexus.client().player();
        return add(name, player.blockX(), player.blockY(), player.blockZ(),
                nexus.client().world().dimensionId(), 0xFFA855F7);
    }

    public boolean remove(String name) {
        Waypoint waypoint = byName(name);
        if (waypoint == null) {
            return false;
        }
        waypoints.remove(waypoint);
        nexus.configManager().markDirty();
        nexus.notifications().info("Waypoint", "Removed " + name);
        return true;
    }

    public Waypoint byName(String name) {
        for (Waypoint waypoint : waypoints) {
            if (waypoint.name().equalsIgnoreCase(name)) {
                return waypoint;
            }
        }
        return null;
    }

    public void clear() {
        waypoints.clear();
        nexus.configManager().markDirty();
    }

    public List<Waypoint> all() {
        return waypoints;
    }

    public List<Waypoint> forDimension(String dimension) {
        List<Waypoint> result = new ArrayList<Waypoint>();
        for (Waypoint waypoint : waypoints) {
            if (waypoint.isEnabled() && waypoint.dimension().equalsIgnoreCase(dimension)) {
                result.add(waypoint);
            }
        }
        return result;
    }

    public int size() {
        return waypoints.size();
    }

    public boolean renderEnabled() {
        return renderEnabled;
    }

    public void setRenderEnabled(boolean value) {
        renderEnabled = value;
    }

    public boolean renderBeacon() {
        return renderBeacon;
    }

    public boolean renderTracer() {
        return renderTracer;
    }

    public double maxDistance() {
        return maxDistance;
    }

    public void setMaxDistance(double value) {
        maxDistance = value;
    }

    /** Exports every waypoint as share codes (one per line). */
    public String exportShareCodes() {
        StringBuilder sb = new StringBuilder();
        for (Waypoint waypoint : waypoints) {
            sb.append(waypoint.toShareCode()).append('\n');
        }
        return sb.toString();
    }

    public int importShareCodes(String data) {
        if (data == null) {
            return 0;
        }
        int count = 0;
        for (String line : data.split("\\r?\\n")) {
            if (line.trim().isEmpty()) {
                continue;
            }
            Waypoint waypoint = Waypoint.fromShareCode(line.trim());
            if (waypoint != null) {
                waypoints.add(waypoint);
                count++;
            }
        }
        if (count > 0) {
            nexus.configManager().markDirty();
        }
        return count;
    }

    public Json toJson() {
        Json.JsonObject object = Json.object();
        Json.JsonArray array = Json.array();
        for (Waypoint waypoint : waypoints) {
            array.add(waypoint.toJson());
        }
        object.set("waypoints", array);
        object.set("renderEnabled", renderEnabled);
        object.set("renderBeacon", renderBeacon);
        object.set("renderTracer", renderTracer);
        object.set("maxDistance", maxDistance);
        return object;
    }

    public void fromJson(Json value) {
        if (!(value instanceof Json.JsonObject)) {
            return;
        }
        Json.JsonObject object = (Json.JsonObject) value;
        waypoints.clear();
        Json waypointsValue = object.get("waypoints");
        if (waypointsValue.isArray()) {
            Json.JsonArray array = (Json.JsonArray) waypointsValue;
            for (int i = 0; i < array.size(); i++) {
                Waypoint waypoint = Waypoint.fromJson(array.get(i));
                if (waypoint != null) {
                    waypoints.add(waypoint);
                }
            }
        }
        renderEnabled = object.bool("renderEnabled", true);
        renderBeacon = object.bool("renderBeacon", true);
        renderTracer = object.bool("renderTracer", false);
        maxDistance = object.double_("maxDistance", 2048.0);
    }

    public String normalizeDimension(String dimension) {
        return dimension == null ? "" : dimension.toLowerCase(Locale.ROOT);
    }
}
