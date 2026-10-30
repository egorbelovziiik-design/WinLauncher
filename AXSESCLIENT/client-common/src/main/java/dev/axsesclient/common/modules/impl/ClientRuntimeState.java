package dev.axsesclient.common.modules.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.axsesclient.common.platform.MinecraftAdapter;
import dev.axsesclient.common.platform.TargetEntityInfo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClientRuntimeState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final class Waypoint {
        public final String name;
        public final double x;
        public final double y;
        public final double z;
        public final String dimension;
        public final String worldId;
        public final int color;

        public Waypoint(String name, double x, double y, double z, String dimension, String worldId, int color) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimension = dimension == null ? "minecraft:overworld" : dimension;
            this.worldId = worldId == null ? "global" : worldId;
            this.color = color;
        }
    }

    private final Path clientRootDir;
    private final long sessionStartMs = System.currentTimeMillis();
    private long worldSessionStartMs = System.currentTimeMillis();
    private String lastWorldName = "";

    // CPS tracking
    private final Deque<Long> leftClicks = new ArrayDeque<Long>();
    private final Deque<Long> rightClicks = new ArrayDeque<Long>();
    private int peakLeftCps = 0;
    private int peakRightCps = 0;

    // Movement & Jump telemetry
    private boolean wasOnGround = true;
    private int totalJumps = 0;
    private int sprintJumps = 0;
    private double walkMeters = 0.0;
    private double sprintMeters = 0.0;
    private double fallMeters = 0.0;
    private double currentHorizontalSpeedBps = 0.0;
    private double peakSpeedBps = 0.0;
    private int worldsVisited = 0;

    // Combat Training telemetry
    private int totalSwings = 0;
    private int totalHits = 0;
    private int criticalHits = 0;
    private int currentCombo = 0;
    private int peakCombo = 0;
    private int perfectTimingSwings = 0;
    private long lastHitTimestampMs = 0L;
    private boolean lastHitCritical = false;
    private float lastHitCooldownScale = 1.0f;

    // Reaction Training & Practice Timer
    private boolean reactionDrillArmed = false;
    private long reactionCueTimestampMs = 0L;
    private long lastReactionTimeMs = 0L;
    private long bestReactionTimeMs = 0L;
    private long practiceTimerEndMs = 0L;

    // Waypoints, Death Location, Notes, Screenshots
    private final List<Waypoint> waypoints = new ArrayList<Waypoint>();
    private Waypoint lastDeathWaypoint = null;
    private String globalNotes = "Welcome to AXSESCLIENT GENESIS X!";
    private final Map<String, String> worldNotes = new HashMap<String, String>();
    private final List<String> recentScreenshots = new ArrayList<String>();
    private long lastScreenshotScanMs = 0L;
    private String lastScreenshotToastFile = "";
    private long lastScreenshotToastMs = 0L;

    // Camera overrides (Zoom / FreeLook)
    private boolean zooming = false;
    private float currentZoomFactor = 1.0f;
    private boolean freeLooking = false;
    private float freeLookYaw = 0.0f;
    private float freeLookPitch = 0.0f;

    // Active Combat Target (shared by KillAura, TriggerBot, Crosshair, and Target HUD)
    private volatile TargetEntityInfo activeTarget = null;
    private volatile long lastTargetTimestampMs = 0L;
    private float animatedTargetHpFrac = 1.0f;
    private float damageTrailHpFrac = 1.0f;
    private int lastTrackedEntityId = -1;

    public ClientRuntimeState(Path clientRootDir) {
        this.clientRootDir = clientRootDir;
        loadWaypointsAndNotes();
    }

    public void lockTarget(TargetEntityInfo target) {
        if (target == null) return;
        this.activeTarget = target;
        this.lastTargetTimestampMs = System.currentTimeMillis();
        if (target.getEntityId() != this.lastTrackedEntityId) {
            this.lastTrackedEntityId = target.getEntityId();
            this.animatedTargetHpFrac = target.getHealthFraction();
            this.damageTrailHpFrac = target.getHealthFraction();
        }
    }

    public TargetEntityInfo getActiveTarget(long lingerDurationMs) {
        TargetEntityInfo t = this.activeTarget;
        if (t == null) return null;
        if (System.currentTimeMillis() - this.lastTargetTimestampMs > lingerDurationMs) {
            return null;
        }
        return t;
    }

    public float updateAndGetAnimatedHpFrac(float targetFrac, float dt) {
        float speed = Math.min(1.0f, dt * 14.0f);
        animatedTargetHpFrac += (targetFrac - animatedTargetHpFrac) * speed;
        if (damageTrailHpFrac < animatedTargetHpFrac) {
            damageTrailHpFrac = animatedTargetHpFrac;
        } else {
            damageTrailHpFrac += (targetFrac - damageTrailHpFrac) * Math.min(1.0f, dt * 3.8f);
        }
        return Math.max(0.0f, Math.min(1.0f, animatedTargetHpFrac));
    }

    public float getDamageTrailHpFrac() {
        return Math.max(0.0f, Math.min(1.0f, damageTrailHpFrac));
    }

    public void updateTick(MinecraftAdapter mc) {
        if (mc == null) return;
        String world = mc.isInWorld() ? mc.getWorldOrServerName() : "";
        if (!world.equals(lastWorldName)) {
            lastWorldName = world;
            if (!world.isEmpty()) {
                worldSessionStartMs = System.currentTimeMillis();
                worldsVisited++;
            }
        }
        if (mc.isInWorld()) {
            double dx = mc.getPlayerX() - mc.getPrevPlayerX();
            double dy = mc.getPlayerY() - mc.getPrevPlayerY();
            double dz = mc.getPlayerZ() - mc.getPrevPlayerZ();
            double horiz = Math.hypot(dx, dz);
            currentHorizontalSpeedBps = horiz * 20.0;
            if (currentHorizontalSpeedBps > peakSpeedBps && currentHorizontalSpeedBps < 120.0) {
                peakSpeedBps = currentHorizontalSpeedBps;
            }
            if (horiz > 0.0001 && horiz < 10.0) {
                if (mc.isSprinting()) {
                    sprintMeters += horiz;
                } else {
                    walkMeters += horiz;
                }
            }
            if (dy < -0.05 && dy > -10.0) {
                fallMeters += (-dy);
            }
            boolean onGround = mc.isOnGround();
            if (wasOnGround && !onGround && dy > 0.10) {
                totalJumps++;
                if (mc.isSprinting()) {
                    sprintJumps++;
                }
            }
            wasOnGround = onGround;

            TargetEntityInfo xhair = mc.getCrosshairTarget();
            if (xhair != null) {
                lockTarget(xhair);
            } else {
                TargetEntityInfo existing = this.activeTarget;
                if (existing != null && (System.currentTimeMillis() - this.lastTargetTimestampMs) <= 3500L) {
                    List<TargetEntityInfo> near = mc.getNearbyTargets(10.0, true, true, true, true);
                    for (int i = 0; i < near.size(); i++) {
                        TargetEntityInfo cand = near.get(i);
                        if (cand.getEntityId() == existing.getEntityId()) {
                            this.activeTarget = cand;
                            break;
                        }
                    }
                }
            }
        }
    }

    public synchronized void recordMouseClick(int button) {
        long now = System.currentTimeMillis();
        if (button == 0) {
            leftClicks.addLast(Long.valueOf(now));
            pruneClicks(leftClicks, now);
            peakLeftCps = Math.max(peakLeftCps, leftClicks.size());
            if (reactionDrillArmed && now >= reactionCueTimestampMs) {
                lastReactionTimeMs = now - reactionCueTimestampMs;
                if (bestReactionTimeMs == 0L || lastReactionTimeMs < bestReactionTimeMs) {
                    bestReactionTimeMs = lastReactionTimeMs;
                }
                reactionDrillArmed = false;
            }
        } else if (button == 1) {
            rightClicks.addLast(Long.valueOf(now));
            pruneClicks(rightClicks, now);
            peakRightCps = Math.max(peakRightCps, rightClicks.size());
        }
    }

    public synchronized int getLeftCps() {
        pruneClicks(leftClicks, System.currentTimeMillis());
        return leftClicks.size();
    }

    public synchronized int getRightCps() {
        pruneClicks(rightClicks, System.currentTimeMillis());
        return rightClicks.size();
    }

    private static void pruneClicks(Deque<Long> q, long now) {
        while (!q.isEmpty() && (now - q.peekFirst().longValue()) > 1000L) {
            q.pollFirst();
        }
    }

    public synchronized void recordAttack(float cooldownProgress, boolean hit, boolean critical) {
        totalSwings++;
        if (cooldownProgress >= 0.90f) {
            perfectTimingSwings++;
        }
        if (hit) {
            totalHits++;
            currentCombo++;
            peakCombo = Math.max(peakCombo, currentCombo);
            lastHitTimestampMs = System.currentTimeMillis();
            lastHitCritical = critical;
            lastHitCooldownScale = cooldownProgress;
            if (critical) {
                criticalHits++;
            }
        } else {
            currentCombo = 0;
        }
    }

    public void armReactionDrill() {
        long delayMs = 1200L + (System.currentTimeMillis() % 1800L);
        this.reactionCueTimestampMs = System.currentTimeMillis() + delayMs;
        this.reactionDrillArmed = true;
    }

    public void startPracticeTimerSeconds(int seconds) {
        this.practiceTimerEndMs = System.currentTimeMillis() + Math.max(5, seconds) * 1000L;
    }

    public int getPracticeRemainingSeconds() {
        long rem = practiceTimerEndMs - System.currentTimeMillis();
        return rem <= 0L ? 0 : (int) ((rem + 999L) / 1000L);
    }

    public synchronized void recordDeath(double x, double y, double z, String dim, String worldId) {
        this.lastDeathWaypoint = new Waypoint("Last Death", x, y, z, dim, worldId, 0xFFFF5B79);
        addOrReplaceWaypoint(this.lastDeathWaypoint);
    }

    public synchronized void addOrReplaceWaypoint(Waypoint wp) {
        for (int i = waypoints.size() - 1; i >= 0; i--) {
            if (waypoints.get(i).name.equalsIgnoreCase(wp.name) && waypoints.get(i).worldId.equals(wp.worldId)) {
                waypoints.remove(i);
            }
        }
        waypoints.add(wp);
        saveWaypointsAndNotes();
    }

    public synchronized List<Waypoint> getWaypoints() {
        return Collections.unmodifiableList(new ArrayList<Waypoint>(waypoints));
    }

    public Waypoint getLastDeathWaypoint() { return lastDeathWaypoint; }

    public void recordScreenshot(String fileName) {
        this.lastScreenshotToastFile = fileName;
        this.lastScreenshotToastMs = System.currentTimeMillis();
        this.lastScreenshotScanMs = 0L;
    }

    public List<String> scanScreenshots(Path gameDir) {
        long now = System.currentTimeMillis();
        if (now - lastScreenshotScanMs < 3000L && !recentScreenshots.isEmpty()) {
            return Collections.unmodifiableList(recentScreenshots);
        }
        lastScreenshotScanMs = now;
        recentScreenshots.clear();
        if (gameDir != null) {
            Path dir = gameDir.resolve("screenshots");
            if (Files.isDirectory(dir)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.png")) {
                    for (Path p : ds) {
                        recentScreenshots.add(p.getFileName().toString());
                    }
                } catch (IOException ignored) {
                }
            }
        }
        Collections.sort(recentScreenshots, Collections.reverseOrder());
        return Collections.unmodifiableList(recentScreenshots);
    }

    public void loadWaypointsAndNotes() {
        try {
            Path f = clientRootDir.resolve("userdata.json");
            if (!Files.isRegularFile(f)) return;
            JsonElement el = new JsonParser().parse(new String(Files.readAllBytes(f), StandardCharsets.UTF_8));
            if (!el.isJsonObject()) return;
            JsonObject o = el.getAsJsonObject();
            if (o.has("globalNotes")) globalNotes = o.get("globalNotes").getAsString();
            if (o.has("waypoints") && o.get("waypoints").isJsonArray()) {
                waypoints.clear();
                for (JsonElement item : o.getAsJsonArray("waypoints")) {
                    JsonObject w = item.getAsJsonObject();
                    waypoints.add(new Waypoint(
                            w.get("name").getAsString(),
                            w.get("x").getAsDouble(),
                            w.get("y").getAsDouble(),
                            w.get("z").getAsDouble(),
                            w.get("dimension").getAsString(),
                            w.get("worldId").getAsString(),
                            w.get("color").getAsInt()
                    ));
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void saveWaypointsAndNotes() {
        try {
            Files.createDirectories(clientRootDir);
            JsonObject o = new JsonObject();
            o.addProperty("globalNotes", globalNotes);
            JsonArray arr = new JsonArray();
            for (Waypoint w : waypoints) {
                JsonObject wo = new JsonObject();
                wo.addProperty("name", w.name);
                wo.addProperty("x", Double.valueOf(w.x));
                wo.addProperty("y", Double.valueOf(w.y));
                wo.addProperty("z", Double.valueOf(w.z));
                wo.addProperty("dimension", w.dimension);
                wo.addProperty("worldId", w.worldId);
                wo.addProperty("color", Integer.valueOf(w.color));
                arr.add(wo);
            }
            o.add("waypoints", arr);
            Files.write(clientRootDir.resolve("userdata.json"), GSON.toJson(o).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    public long getSessionElapsedSeconds() { return Math.max(0L, (System.currentTimeMillis() - sessionStartMs) / 1000L); }
    public long getWorldSessionElapsedSeconds() { return Math.max(0L, (System.currentTimeMillis() - worldSessionStartMs) / 1000L); }
    public int getPeakLeftCps() { return peakLeftCps; }
    public int getPeakRightCps() { return peakRightCps; }
    public int getTotalJumps() { return totalJumps; }
    public int getSprintJumps() { return sprintJumps; }
    public double getWalkMeters() { return walkMeters; }
    public double getSprintMeters() { return sprintMeters; }
    public double getFallMeters() { return fallMeters; }
    public double getCurrentHorizontalSpeedBps() { return currentHorizontalSpeedBps; }
    public double getPeakSpeedBps() { return peakSpeedBps; }
    public int getWorldsVisited() { return worldsVisited; }
    public int getTotalSwings() { return totalSwings; }
    public int getTotalHits() { return totalHits; }
    public int getCriticalHits() { return criticalHits; }
    public int getCurrentCombo() { return currentCombo; }
    public int getPeakCombo() { return peakCombo; }
    public int getPerfectTimingSwings() { return perfectTimingSwings; }
    public long getLastHitTimestampMs() { return lastHitTimestampMs; }
    public boolean isLastHitCritical() { return lastHitCritical; }
    public float getLastHitCooldownScale() { return lastHitCooldownScale; }
    public boolean isReactionDrillArmed() { return reactionDrillArmed; }
    public long getReactionCueTimestampMs() { return reactionCueTimestampMs; }
    public long getLastReactionTimeMs() { return lastReactionTimeMs; }
    public long getBestReactionTimeMs() { return bestReactionTimeMs; }
    public String getGlobalNotes() { return globalNotes; }
    public void setGlobalNotes(String notes) { this.globalNotes = notes == null ? "" : notes; saveWaypointsAndNotes(); }
    public String getWorldNote(String worldId) {
        String note = worldNotes.get(worldId);
        return note == null ? "World note: Ready" : note;
    }
    public void setWorldNote(String worldId, String note) {
        if (worldId != null) worldNotes.put(worldId, note == null ? "" : note);
    }
    public String getLastScreenshotToastFile() { return lastScreenshotToastFile; }
    public long getLastScreenshotToastMs() { return lastScreenshotToastMs; }
    public boolean isZooming() { return zooming; }
    public void setZooming(boolean zooming) { this.zooming = zooming; }
    public float getCurrentZoomFactor() { return currentZoomFactor; }
    public void setCurrentZoomFactor(float f) { this.currentZoomFactor = Math.max(1.0f, Math.min(12.0f, f)); }
    public boolean isFreeLooking() { return freeLooking; }
    public void setFreeLooking(boolean f) { this.freeLooking = f; }
    public float getFreeLookYaw() { return freeLookYaw; }
    public void setFreeLookYaw(float y) { this.freeLookYaw = y; }
    public float getFreeLookPitch() { return freeLookPitch; }
    public void setFreeLookPitch(float p) { this.freeLookPitch = Math.max(-90.0f, Math.min(90.0f, p)); }

    public void resetTrainingStats() {
        totalSwings = 0;
        totalHits = 0;
        criticalHits = 0;
        currentCombo = 0;
        peakCombo = 0;
        perfectTimingSwings = 0;
        lastReactionTimeMs = 0L;
    }

    public static String formatHhMmSs(long totalSec) {
        long h = totalSec / 3600L;
        long m = (totalSec % 3600L) / 60L;
        long s = totalSec % 60L;
        return String.format("%02d:%02d:%02d", Long.valueOf(h), Long.valueOf(m), Long.valueOf(s));
    }
}
