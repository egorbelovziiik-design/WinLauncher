package net.kinggrief.kingac;

import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerAcData {

    public static final class BoxSnapshot {
        public final long timeMs;
        public final BoundingBox box;

        public BoxSnapshot(long timeMs, BoundingBox box) {
            this.timeMs = timeMs;
            this.box = box;
        }
    }

    public final Map<String, Integer> vlByCheck = new LinkedHashMap<String, Integer>();
    public int totalVl = 0;
    public int combatConfirmations = 0;
    public String lastCheat = "Чисто";
    public long lastAlertMs = 0L;
    public boolean banInProgress = false;

    // ================= ИСТОРИЯ ХИТБОКСОВ ЖЕРТВЫ (ДЛЯ ТОЧНОГО RAYCAST И PING-КОМПЕНСАЦИИ) =================
    public final Deque<BoxSnapshot> boxHistory = new ArrayDeque<BoxSnapshot>(20);
    public Vector prevEyeVec = null;
    public float prevTickYaw = 0f;
    public float prevTickPitch = 0f;
    public double lastRayMissMeters = 0.0;

    // ================= МИКРО-ДЁРГАНИЕ ГОЛОВОЙ (MICRO-TWITCH / GCD / SNAP) =================
    public float lastYaw = 0f;
    public float lastPitch = 0f;
    public float prevDeltaYaw = 0f;
    public float prevDeltaPitch = 0f;
    public long lastRotMs = 0L;

    public final Deque<Float> recentDyaw = new ArrayDeque<Float>(28);
    public final Deque<Float> recentDpitch = new ArrayDeque<Float>(28);
    public final Deque<Long> recentRotTimes = new ArrayDeque<Long>(28);

    public int microTwitchBuffer = 0;
    public int gcdFailBuffer = 0;
    public int flatSnapBuffer = 0;
    public int preHitSnapBuffer = 0;
    public float pendingHitSnapYaw = 0f;
    public long pendingHitSnapMs = 0L;
    public int maxMicroTwitchScore = 0;

    // ================= БОЕВЫЕ ПРОВЕРКИ И СХЕМЫ ИНЖЕКТА =================
    public final List<Long> clickTimes = new ArrayList<Long>();
    public final Deque<Long> clickIntervals = new ArrayDeque<Long>(20);
    public long prevClickMs = 0L;
    public int currentCps = 0;
    public UUID lastTargetId = null;
    public long lastCombatMs = 0L;
    public long lastProbeMs = 0L;
    public long lastWallProbeMs = 0L;
    public String activeSchemeLabel = "Нет";
    public int totalInjectHits = 0;
    public final Set<UUID> swarmHitIds = new HashSet<UUID>();
    public long lastSwarmHitMs = 0L;

    public int wallHitBuffer = 0;
    public int airHitBuffer = 0;
    public int totalAirHitsBlocked = 0;
    public int backtrackBuffer = 0;
    public long lastArmSwingMs = 0L;
    public int noSwingBuffer = 0;
    public int keepSprintBuffer = 0;
    public double lastMoveHoriz = 0.0;
    public long bowDrawStartMs = 0L;
    public int fakeCritBuffer = 0;
    public int reachBuffer = 0;

    public void pushBoxSnapshot(long now, BoundingBox box) {
        if (box == null) return;
        while (boxHistory.size() >= 18) {
            boxHistory.pollFirst();
        }
        boxHistory.addLast(new BoxSnapshot(now, box.clone()));
    }

    // ================= ДВИЖЕНИЕ + TIMER / BLINK + GUIMOVE + SCAFFOLD =================
    public Location lastSafeGroundLoc = null;
    public int airHoverTicks = 0;
    public int waterWalkTicks = 0;
    public int noSlowTicks = 0;
    public int speedBuffer = 0;
    public int airStrafeBuffer = 0;
    public double lastAirDx = 0.0;
    public double lastAirDz = 0.0;
    public long lastDamagedMs = 0L;
    public long lastTeleportOrGlideMs = 0L;
    public long lastBlockPlaceMs = 0L;
    public int scaffoldSnapBuffer = 0;
    public long fastPlaceWindowMs = 0L;
    public int fastPlaceCount = 0;

    // Timer / Blink packet balance
    public long timerWindowStartMs = 0L;
    public int timerMovePackets = 0;
    public int timerBuffer = 0;
    public int blinkBuffer = 0;
    public long lastMovePacketMs = 0L;
    public double lastMovePacketHoriz = 0.0;

    // GuiMove / InvWalk
    public long lastInvClickMs = 0L;
    public int guiMoveBuffer = 0;

    // ================= TRIGGERBOT / ANTIKB / PHASE / MICROHOP =================
    public int triggerBotBuffer = 0;
    public int phaseBuffer = 0;
    public int antiKbBuffer = 0;
    public double lastVelocityY = 0.0;
    public double lastVelocityHoriz = 0.0;
    public long lastVelocityMs = 0L;
    public int velocityTicks = 0;
    public double velocityClimb = 0.0;
    public double velocityRun = 0.0;
    public int microHopBuffer = 0;
    public InjectionScheme lastInjectScheme = null;
    public long lastInjectSchemeMs = 0L;

    // ================= ИНВЕНТАРЬ И МИР (AUTOTOTEM / XRAY) =================
    public long lastTotemPopMs = 0L;
    public int stoneMined = 0;
    public int hiddenRareVeinHits = 0;
    public Location lastRareOreLoc = null;

    // ================= СИСТЕМА ЖАЛОБ И УСИЛЕННАЯ СЛЕЖКА (DEEP WATCH) =================
    public final Set<String> reporters = new HashSet<String>();
    public long deepWatchUntilMs = 0L;

    public boolean isUnderDeepWatch() {
        return System.currentTimeMillis() < deepWatchUntilMs;
    }
}
