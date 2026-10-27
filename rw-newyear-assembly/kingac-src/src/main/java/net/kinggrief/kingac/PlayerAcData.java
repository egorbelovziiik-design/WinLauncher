package net.kinggrief.kingac;

import org.bukkit.Location;

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

    public final Map<String, Integer> vlByCheck = new LinkedHashMap<String, Integer>();
    public int totalVl = 0;
    public int combatConfirmations = 0;
    public String lastCheat = "Чисто";
    public long lastAlertMs = 0L;
    public boolean banInProgress = false;

    // ================= МИКРО-ДЁРГАНИЕ ГОЛОВОЙ (MICRO-TWITCH / GCD / SNAP) =================
    public float lastYaw = 0f;
    public float lastPitch = 0f;
    public float prevDeltaYaw = 0f;
    public float prevDeltaPitch = 0f;
    public long lastRotMs = 0L;

    // Кольцевой буфер последних 24 тиков вращения камеры
    public final Deque<Float> recentDyaw = new ArrayDeque<Float>(28);
    public final Deque<Float> recentDpitch = new ArrayDeque<Float>(28);
    public final Deque<Long> recentRotTimes = new ArrayDeque<Long>(28);

    // Счётчики микро-дёрганий (осцилляций + -> - -> +), нарушений GCD сенсы и плоской наводки
    public int microTwitchBuffer = 0;
    public int gcdFailBuffer = 0;
    public int flatSnapBuffer = 0;
    public int preHitSnapBuffer = 0;
    public float pendingHitSnapYaw = 0f;
    public long pendingHitSnapMs = 0L;
    public int maxMicroTwitchScore = 0;

    // ================= БОЕВЫЕ ПРОВЕРКИ (KILLAURA / CPS / REACH / CRITS) =================
    public final List<Long> clickTimes = new ArrayList<Long>();
    public final Deque<Long> clickIntervals = new ArrayDeque<Long>(20);
    public long prevClickMs = 0L;
    public UUID lastTargetId = null;
    public long lastCombatMs = 0L;
    public long lastProbeMs = 0L;
    public long lastWallProbeMs = 0L;
    public int wallHitBuffer = 0;
    public int fakeCritBuffer = 0;
    public int reachBuffer = 0;

    // ================= ДВИЖЕНИЕ (FLY / SPEED / AIRSTRAFE / JESUS / NOSLOW / SCAFFOLD) =================
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
