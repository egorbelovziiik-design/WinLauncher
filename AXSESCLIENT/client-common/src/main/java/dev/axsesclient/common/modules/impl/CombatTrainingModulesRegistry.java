package dev.axsesclient.common.modules.impl;

import dev.axsesclient.common.hud.HudElement;
import dev.axsesclient.common.hud.HudManager;
import dev.axsesclient.common.i18n.LocalizationManager;
import dev.axsesclient.common.modules.Module;
import dev.axsesclient.common.modules.ModuleCategory;
import dev.axsesclient.common.modules.ModuleManager;
import dev.axsesclient.common.notifications.NotificationManager;
import dev.axsesclient.common.platform.ItemInfo;
import dev.axsesclient.common.platform.KeyCodes;
import dev.axsesclient.common.platform.MinecraftAdapter;
import dev.axsesclient.common.platform.RenderAdapter;
import dev.axsesclient.common.platform.TargetEntityInfo;
import dev.axsesclient.common.render.FontManager;
import dev.axsesclient.common.render.RenderManager;
import dev.axsesclient.common.settings.BooleanSetting;
import dev.axsesclient.common.settings.ColorSetting;
import dev.axsesclient.common.settings.ModeSetting;
import dev.axsesclient.common.settings.NumberSetting;
import dev.axsesclient.common.theme.AxsesTheme;
import dev.axsesclient.common.theme.ThemeManager;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class CombatTrainingModulesRegistry {
    private CombatTrainingModulesRegistry() {
    }

    public static void registerAll(
            final ModuleManager mm,
            HudManager hm,
            final ClientRuntimeState state,
            final ThemeManager themes,
            final FontManager fonts,
            final NotificationManager notifications,
            final LocalizationManager i18n
    ) {
        // NEW FLAGSHIP 01: KillAura v4.0 (КиллАура) — 35-Year Kinematic Anti-Cheat Profiles (SnowGrim / Grim / Matrix / Vulcan / Intave / Polar / FunTime)
        Module killAura = mm.register(new Module("kill_aura", "KillAura", "35-year kinematic 1.9+ combat aura with Matrix, Grim, SnowGrim, Vulcan, Intave, Polar & FunTime/RW/HW profiles", ModuleCategory.COMBAT_TRAINING, false, KeyCodes.KEY_R, false) {
            private final ModeSetting serverProfile = addSetting(new ModeSetting(
                    "server_profile", "AC / Server Profile", "Pre-tuned kinematic & raycast profiles for popular anti-cheats and servers",
                    "Universal",
                    "Universal", "SnowGrim", "Grim", "Matrix",
                    "FunTime", "ReallyWorld", "HolyWorld",
                    "Vulcan", "Intave", "Polar", "SpookyTime", "Legit Ghost"
            ));
            private final NumberSetting range = addSetting(new NumberSetting("range", "Range", "Attack reach radius in blocks", 4.2, 2.5, 6.0, 0.05));
            private final NumberSetting fov = addSetting(new NumberSetting("fov", "FOV Cone", "Maximum target angle around crosshair", 360.0, 30.0, 360.0, 5.0));
            private final ModeSetting attackMode = addSetting(new ModeSetting("attack_mode", "Timing Mode", "Weapon recharge vs humanized smart timing", "1.9+ Smart", "1.9+ Smart", "1.9+ Cooldown", "CPS Timer"));
            private final NumberSetting cps = addSetting(new NumberSetting("cps", "CPS Speed", "Clicks per second in CPS Timer mode", 11.0, 4.0, 18.0, 1.0));
            private final ModeSetting priority = addSetting(new ModeSetting("priority", "Priority", "Target selection priority", "Distance", "Distance", "Lowest HP", "Angle", "Least Armor"));
            private final ModeSetting rotations = addSetting(new ModeSetting(
                    "rotations", "Rotations", "Kinematic camera aim interpolation curve",
                    "Smooth S-Curve",
                    "Smooth S-Curve", "SnowGrim Coupled", "Matrix Damped", "Grim Quantized", "Polar Min-Jerk", "FunTime Human", "Multipoint Clamp", "Snap", "Off"
            ));
            private final ModeSetting moveFix = addSetting(new ModeSetting(
                    "move_fix", "Movement Fix", "Align yaw rotation phase with player movement vectors for Grim / SnowGrim",
                    "Silent Strafe", "Silent Strafe", "Strict Grim 45", "Off"
            ));
            private final NumberSetting turnSpeed = addSetting(new NumberSetting("turn_speed", "Turn Speed", "Maximum smooth camera degrees per second", 220.0, 60.0, 420.0, 10.0));
            private final NumberSetting switchDelay = addSetting(new NumberSetting("switch_delay", "Switch Delay (ms)", "Humanized acquisition pause when switching between targets", 90.0, 0.0, 400.0, 10.0));
            private final NumberSetting hitboxMargin = addSetting(new NumberSetting("hitbox_margin", "Hitbox Margin", "Inset safety margin inside target bounding box for strict ray checks", 0.08, 0.02, 0.24, 0.02));
            private final ModeSetting aimPoint = addSetting(new ModeSetting("aim_point", "Aim Point", "Hitbox zone to track on target", "Nearest Hitbox", "Nearest Hitbox", "Chest", "Eyes", "Dynamic"));
            private final ModeSetting critMode = addSetting(new ModeSetting("crit_mode", "Criticals", "Wait for falling apex to land 1.5x critical hits", "Smart Fall", "Smart Fall", "Always Swing", "Only Crits"));
            private final BooleanSetting sprintReset = addSetting(new BooleanSetting("sprint_reset", "W-Tap Sprint Reset", "Reset sprint before falling hits so 1.9+ criticals register cleanly", true));
            private final BooleanSetting gcdFix = addSetting(new BooleanSetting("gcd_fix", "Mouse GCD Fix", "Quantize head rotations to exact vanilla mouse sensitivity steps", true));
            private final BooleanSetting humanJitter = addSetting(new BooleanSetting("human_jitter", "Human Micro-Jitter", "Organic hand micro-drift and cooldown reaction variance", true));
            private final BooleanSetting checkWalls = addSetting(new BooleanSetting("check_walls", "Check Walls (LoS)", "Do not swing at targets hidden behind solid blocks", true));
            private final BooleanSetting strictRaycast = addSetting(new BooleanSetting("strict_raycast", "Strict Hitbox Ray", "Only swing when look vector intersects target bounding box", false));
            private final BooleanSetting pauseEating = addSetting(new BooleanSetting("pause_eating", "Pause While Eating", "Pause attacks while eating golden apples, drinking potions, or shielding", true));
            private final BooleanSetting targetPlayers = addSetting(new BooleanSetting("players", "Target Players", "Attack nearby players", true));
            private final BooleanSetting targetHostiles = addSetting(new BooleanSetting("hostiles", "Target Monsters", "Attack hostile mobs", true));
            private final BooleanSetting targetAnimals = addSetting(new BooleanSetting("animals", "Target Animals", "Attack passive/neutral mobs", false));
            private final BooleanSetting targetInvisible = addSetting(new BooleanSetting("invisible", "Target Invisible", "Include invisible entities", false));
            private final BooleanSetting pauseInGui = addSetting(new BooleanSetting("pause_gui", "Pause In Menus", "Do not attack while inventory or GUI is open", true));
            private final BooleanSetting disableOnDeath = addSetting(new BooleanSetting("disable_death", "Disable On Death", "Automatically turn off KillAura on death", true));
            private final BooleanSetting targetRing = addSetting(new BooleanSetting("target_ring", "3D Target Ring", "Render animated 3D Aurora ring around locked target", true));
            private final BooleanSetting soulOrbs = addSetting(new BooleanSetting("soul_orbs", "3D Soul Orbs", "Orbiting celestial energy spheres around active target", true));
            private final BooleanSetting showLockBadge = addSetting(new BooleanSetting("lock_badge", "Crosshair Indicator", "Show locked target badge under crosshair", true));

            private long lastSwingMs = 0L;
            private long nextJitterDelayMs = 95L;
            private float nextCooldownThreshold = 0.91f;
            private boolean restoreSprintNextTick = false;
            private TargetEntityInfo lockedNow = null;
            private int lastTargetId = -1;
            private long targetSwitchedAtMs = 0L;
            private float prevVelYaw = 0.0f;
            private float prevVelPitch = 0.0f;

            @Override
            public void onDisable(MinecraftAdapter mc) {
                lockedNow = null;
                restoreSprintNextTick = false;
                lastTargetId = -1;
                prevVelYaw = 0.0f;
                prevVelPitch = 0.0f;
            }

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                String prof = serverProfile.getValue();
                return "KillAura [" + prof + "]";
            }

            private double effectiveRange() {
                String prof = serverProfile.getValue();
                double r = range.getDouble();
                if ("Grim".equalsIgnoreCase(prof) || "SnowGrim".equalsIgnoreCase(prof)) return Math.min(r, 3.00);
                if ("Intave".equalsIgnoreCase(prof) || "Polar".equalsIgnoreCase(prof)) return Math.min(r, 3.04);
                if ("HolyWorld".equalsIgnoreCase(prof) || "Legit Ghost".equalsIgnoreCase(prof)) return Math.min(r, 3.05);
                if ("Matrix".equalsIgnoreCase(prof)) return Math.min(r, 3.12);
                if ("FunTime".equalsIgnoreCase(prof)) return Math.min(r, 3.15);
                if ("Vulcan".equalsIgnoreCase(prof)) return Math.min(r, 3.24);
                if ("ReallyWorld".equalsIgnoreCase(prof)) return Math.min(r, 3.30);
                if ("SpookyTime".equalsIgnoreCase(prof)) return Math.min(r, 3.35);
                return r;
            }

            private double effectiveFov() {
                String prof = serverProfile.getValue();
                double f = fov.getDouble();
                if ("Legit Ghost".equalsIgnoreCase(prof)) return Math.min(f, 115.0);
                if ("Polar".equalsIgnoreCase(prof) && f >= 355.0) return 155.0;
                if ("Intave".equalsIgnoreCase(prof) && f >= 355.0) return 165.0;
                if ("SnowGrim".equalsIgnoreCase(prof) && f >= 355.0) return 175.0;
                if ("HolyWorld".equalsIgnoreCase(prof) && f >= 355.0) return 170.0;
                if ("Matrix".equalsIgnoreCase(prof) && f >= 355.0) return 185.0;
                if ("FunTime".equalsIgnoreCase(prof) && f >= 355.0) return 190.0;
                return f;
            }

            private boolean effectiveStrictRay() {
                if (strictRaycast.isEnabled()) return true;
                String prof = serverProfile.getValue();
                return "Grim".equalsIgnoreCase(prof)
                        || "SnowGrim".equalsIgnoreCase(prof)
                        || "Matrix".equalsIgnoreCase(prof)
                        || "Intave".equalsIgnoreCase(prof)
                        || "Polar".equalsIgnoreCase(prof)
                        || "FunTime".equalsIgnoreCase(prof)
                        || "HolyWorld".equalsIgnoreCase(prof)
                        || "Legit Ghost".equalsIgnoreCase(prof);
            }

            private void stepAimTowards(MinecraftAdapter mc, TargetEntityInfo target, float dtSeconds) {
                String rotMode = rotations.getValue();
                if ("Off".equalsIgnoreCase(rotMode)) {
                    return;
                }
                String prof = serverProfile.getValue();
                String aimZone = aimPoint.getValue();
                if ("Grim".equalsIgnoreCase(prof) || "SnowGrim".equalsIgnoreCase(prof)
                        || "FunTime".equalsIgnoreCase(prof) || "HolyWorld".equalsIgnoreCase(prof)
                        || "Intave".equalsIgnoreCase(prof) || "Polar".equalsIgnoreCase(prof)) {
                    aimZone = "Nearest Hitbox";
                }
                boolean useJitter = humanJitter.isEnabled() || !"Universal".equalsIgnoreCase(prof);
                float[] desired = computeAimAngles(mc, target, aimZone, useJitter);

                float curYaw = mc.getPlayerYaw();
                float curPitch = mc.getPlayerPitch();
                float dyaw = wrapDegrees(desired[0] - curYaw);
                float dpitch = desired[1] - curPitch;

                if (!"Snap".equalsIgnoreCase(rotMode) && !"Universal".equalsIgnoreCase(prof)) {
                    // Coupled Yaw-Pitch Lissajous wrist micro-coupling (prevents robotic deltaPitch == 0.0 on SnowGrim / Matrix / Polar)
                    double tSec = (System.currentTimeMillis() % 12000L) / 1000.0;
                    float coupledPitch = (float) (Math.sin(tSec * 6.7 + target.getEntityId()) * 0.16 + dyaw * 0.032);
                    dpitch += coupledPitch;
                }

                if (!"Snap".equalsIgnoreCase(rotMode)) {
                    float baseDegPerSec = turnSpeed.getFloat();
                    if ("Legit Ghost".equalsIgnoreCase(prof)) baseDegPerSec = Math.min(baseDegPerSec, 148.0f);
                    else if ("Polar".equalsIgnoreCase(prof) || "Intave".equalsIgnoreCase(prof)) baseDegPerSec = Math.min(baseDegPerSec, 162.0f);
                    else if ("Matrix".equalsIgnoreCase(prof)) baseDegPerSec = Math.min(baseDegPerSec, 176.0f);
                    else if ("SnowGrim".equalsIgnoreCase(prof) || "Grim".equalsIgnoreCase(prof)) baseDegPerSec = Math.min(baseDegPerSec, 188.0f);
                    else if ("HolyWorld".equalsIgnoreCase(prof)) baseDegPerSec = Math.min(baseDegPerSec, 195.0f);
                    else if ("Vulcan".equalsIgnoreCase(prof)) baseDegPerSec = Math.min(baseDegPerSec, 205.0f);
                    else if ("FunTime".equalsIgnoreCase(prof)) baseDegPerSec = Math.min(baseDegPerSec, 215.0f);

                    float dtClamped = Math.max(0.004f, Math.min(0.06f, dtSeconds));
                    float distDeg = (float) Math.hypot(dyaw, dpitch);

                    // Minimum-jerk / S-curve velocity envelope
                    float normErr = Math.max(0.0f, Math.min(1.0f, distDeg / 34.0f));
                    float minJerk = normErr * normErr * (3.0f - 2.0f * normErr);
                    float speedScale = ("Polar".equalsIgnoreCase(prof) || "Polar Min-Jerk".equalsIgnoreCase(rotMode))
                            ? (0.20f + 0.98f * minJerk)
                            : Math.max(0.22f, Math.min(1.16f, distDeg / 28.0f));

                    float maxStep = Math.max(1.1f, baseDegPerSec * speedScale * dtClamped);
                    if (distDeg > maxStep && distDeg > 0.001f) {
                        float scale = maxStep / distDeg;
                        dyaw *= scale;
                        dpitch *= scale;
                    }

                    // Second-order wrist damping for Matrix / SnowGrim / Intave / Polar so angular acceleration never spikes
                    if ("Matrix".equalsIgnoreCase(prof) || "SnowGrim".equalsIgnoreCase(prof)
                            || "Intave".equalsIgnoreCase(prof) || "Polar".equalsIgnoreCase(prof)
                            || "Matrix Damped".equalsIgnoreCase(rotMode) || "SnowGrim Coupled".equalsIgnoreCase(rotMode)) {
                        float damp = 0.68f;
                        dyaw = prevVelYaw * (1.0f - damp) + dyaw * damp;
                        dpitch = prevVelPitch * (1.0f - damp) + dpitch * damp;
                        prevVelYaw = dyaw;
                        prevVelPitch = dpitch;
                    }
                }

                // Movement-aligned phase damping for Grim / SnowGrim 45-degree strafe simulation
                String mf = moveFix.getValue();
                if ("Strict Grim 45".equalsIgnoreCase(mf) || ("Silent Strafe".equalsIgnoreCase(mf)
                        && ("Grim".equalsIgnoreCase(prof) || "SnowGrim".equalsIgnoreCase(prof)))) {
                    double vx = mc.getPlayerX() - mc.getPrevPlayerX();
                    double vz = mc.getPlayerZ() - mc.getPrevPlayerZ();
                    if (vx * vx + vz * vz > 0.0004) {
                        float moveYaw = (float) (Math.toDegrees(Math.atan2(vz, vx)) - 90.0);
                        float sectorDiff = wrapDegrees((curYaw + dyaw) - moveYaw);
                        float nearest45 = Math.round(sectorDiff / 45.0f) * 45.0f;
                        float nudge = wrapDegrees(nearest45 - sectorDiff) * 0.08f;
                        dyaw += nudge;
                    }
                }

                if (gcdFix.isEnabled() || !"Universal".equalsIgnoreCase(prof)) {
                    float gcd = computeMouseGcd(mc.getMouseSensitivity());
                    if (gcd > 0.0001f) {
                        dyaw = Math.round(dyaw / gcd) * gcd;
                        dpitch = Math.round(dpitch / gcd) * gcd;
                    }
                }

                if (Math.abs(dyaw) > 0.0005f || Math.abs(dpitch) > 0.0005f) {
                    mc.setPlayerRotation(curYaw + dyaw, clampPitch(curPitch + dpitch));
                }
            }

            @Override
            public void onTick(MinecraftAdapter mc) {
                if (mc == null || !mc.isInWorld()) {
                    lockedNow = null;
                    restoreSprintNextTick = false;
                    return;
                }
                if (restoreSprintNextTick) {
                    restoreSprintNextTick = false;
                    if (!mc.isUsingItem() && !mc.isSneaking()) {
                        mc.setSprinting(true);
                    }
                }
                if (disableOnDeath.isEnabled() && mc.getHealth() <= 0.0f) {
                    lockedNow = null;
                    setEnabled(false, mc);
                    notifications.push(i18n.translateModuleName("kill_aura", "KillAura"),
                            i18n.isRussian() ? "Отключена после смерти" : "Auto-disabled on death",
                            NotificationManager.Severity.WARNING);
                    return;
                }
                if (pauseInGui.isEnabled() && mc.isAnyScreenOpen()) {
                    lockedNow = null;
                    return;
                }
                if (pauseEating.isEnabled() && mc.isUsingItem()) {
                    return;
                }

                double effRange = effectiveRange();
                List<TargetEntityInfo> candidates = mc.getNearbyTargets(
                        effRange + 0.35,
                        targetPlayers.isEnabled(),
                        targetHostiles.isEnabled(),
                        targetAnimals.isEnabled(),
                        targetInvisible.isEnabled()
                );
                if (candidates == null || candidates.isEmpty()) {
                    lockedNow = null;
                    return;
                }

                double maxFovHalf = effectiveFov() * 0.5;
                boolean walls = checkWalls.isEnabled() || !"Universal".equalsIgnoreCase(serverProfile.getValue());
                TargetEntityInfo best = selectBestFiltered(mc, candidates, priority.getValue(), effRange, maxFovHalf, walls);
                if (best == null) {
                    lockedNow = null;
                    return;
                }

                lockedNow = best;
                state.lockTarget(best);

                long nowMs = System.currentTimeMillis();
                if (lastTargetId != -1 && lastTargetId != best.getEntityId()) {
                    targetSwitchedAtMs = nowMs;
                }
                lastTargetId = best.getEntityId();

                // Tick-level rotation fallback (render-level smooth interpolation also runs at full FPS)
                stepAimTowards(mc, best, 0.05f);

                // Humanized target-switch acquisition guard (Polar / Matrix / Intave / SnowGrim)
                long swDelay = Math.round(switchDelay.getDouble());
                if (swDelay > 0L && (nowMs - targetSwitchedAtMs) < swDelay) {
                    return;
                }

                // Strict ray-AABB hitbox verification for Grim / SnowGrim / Matrix / Polar / FunTime / HolyWorld
                String prof = serverProfile.getValue();
                double boxExpand = ("Grim".equalsIgnoreCase(prof) || "SnowGrim".equalsIgnoreCase(prof)
                        || "Polar".equalsIgnoreCase(prof) || "Intave".equalsIgnoreCase(prof))
                        ? Math.max(0.0, 0.10 - hitboxMargin.getDouble())
                        : 0.14;
                if (effectiveStrictRay() && !doesLookIntersectBox(mc, best, effRange + 0.12, boxExpand)) {
                    return;
                }

                // Smart Criticals check: if rising in a jump, wait for peak apex so hit is a 1.5x critical
                String cMode = critMode.getValue();
                boolean airborne = !mc.isOnGround() && !mc.isInLiquid();
                boolean fallingReady = airborne && mc.getFallDistance() > 0.055f;
                if ("Only Crits".equalsIgnoreCase(cMode) && !fallingReady && !mc.isInLiquid()) {
                    return;
                }
                if (("Smart Fall".equalsIgnoreCase(cMode) || !"Universal".equalsIgnoreCase(serverProfile.getValue()))
                        && airborne && mc.getFallDistance() <= 0.045f) {
                    return;
                }

                long now = System.currentTimeMillis();
                boolean ready;
                String tMode = attackMode.getValue();
                if ("CPS Timer".equalsIgnoreCase(tMode)) {
                    double jitterCps = humanJitter.isEnabled()
                            ? (ThreadLocalRandom.current().nextDouble(-0.85, 0.85))
                            : 0.0;
                    long delay = Math.max(52L, Math.round(1000.0 / Math.max(1.0, cps.getDouble() + jitterCps)));
                    ready = (now - lastSwingMs) >= delay;
                } else {
                    float reqCd = "1.9+ Smart".equalsIgnoreCase(tMode) ? nextCooldownThreshold : 0.91f;
                    ready = mc.getAttackCooldownProgress() >= reqCd && (now - lastSwingMs) >= nextJitterDelayMs;
                }

                if (ready) {
                    // Vanilla W-Tap Sprint Reset before falling hit -> guarantees 1.9+ critical strike registration!
                    boolean wasSprinting = mc.isSprinting();
                    if (sprintReset.isEnabled() && fallingReady && wasSprinting) {
                        mc.setSprinting(false);
                        restoreSprintNextTick = true;
                    }

                    float cd = mc.getAttackCooldownProgress();
                    boolean crit = fallingReady && cd >= 0.89f;
                    if (mc.attackEntityById(best.getEntityId(), true)) {
                        lastSwingMs = now;
                        if (humanJitter.isEnabled() || !"Universal".equalsIgnoreCase(serverProfile.getValue())) {
                            nextCooldownThreshold = 0.895f + ThreadLocalRandom.current().nextFloat() * 0.065f;
                            nextJitterDelayMs = 88L + ThreadLocalRandom.current().nextLong(0L, 46L);
                        } else {
                            nextCooldownThreshold = 0.91f;
                            nextJitterDelayMs = 95L;
                        }
                        state.recordAttack(cd, true, crit);
                    }
                }
            }

            @Override
            public void onRender(RenderAdapter r, MinecraftAdapter mc, float dt) {
                if (lockedNow == null || mc == null || !mc.isInWorld() || mc.isClickGuiOpen()) {
                    return;
                }
                if (!(pauseInGui.isEnabled() && mc.isAnyScreenOpen()) && !(pauseEating.isEnabled() && mc.isUsingItem())) {
                    stepAimTowards(mc, lockedNow, dt);
                }

                AxsesTheme t = themes.getActiveTheme();

                // 3D Projected Dual-Ring Aurora Jello Wave + Energy Curtain around locked entity
                if (targetRing.isEnabled()) {
                    double nowSec = (System.currentTimeMillis() % 4000L) / 1000.0;
                    double phase = nowSec * Math.PI;
                    double wave = 0.5 + 0.5 * Math.sin(phase);
                    double echoWave = 0.5 + 0.5 * Math.sin(phase - 0.55);
                    double entH = Math.max(0.8, lockedNow.getBoxHeight());
                    double ringY = lockedNow.getMinY() + entH * (0.10 + 0.76 * wave);
                    double echoY = lockedNow.getMinY() + entH * (0.10 + 0.76 * echoWave);
                    double radius = Math.max(0.50, lockedNow.getBoxWidth() * 0.72);
                    boolean hurt = lockedNow.getHurtTime() > 0;

                    int segs = 28;
                    float prevX = 0f, prevY = 0f, prevEx = 0f, prevEy = 0f;
                    boolean hasPrev = false;
                    for (int i = 0; i <= segs; i++) {
                        double ang = (Math.PI * 2.0 * i) / segs + nowSec * 1.6;
                        double wx = lockedNow.getX() + Math.cos(ang) * radius;
                        double wz = lockedNow.getZ() + Math.sin(ang) * radius;
                        float[] proj = mc.projectWorldToScreen(wx, ringY, wz);
                        float[] echo = mc.projectWorldToScreen(wx, echoY, wz);
                        if (proj != null && echo != null && proj[2] > 0.1f && echo[2] > 0.1f) {
                            int waveCol = hurt ? t.getErrorColor() : themes.getAuroraWaveColor(i * 0.24f);
                            if (i % 2 == 0) {
                                r.drawLine(proj[0], proj[1], echo[0], echo[1], 1.2f, AxsesTheme.withAlpha(waveCol, 0.26f));
                            }
                            if (hasPrev) {
                                r.drawLine(prevEx, prevEy, echo[0], echo[1], 1.5f, AxsesTheme.withAlpha(waveCol, 0.42f));
                                r.drawLine(prevX, prevY + 1.2f, proj[0], proj[1] + 1.2f, 2.8f, AxsesTheme.withAlpha(waveCol, 0.30f));
                                r.drawLine(prevX, prevY, proj[0], proj[1], 1.9f, AxsesTheme.withAlpha(waveCol, 0.95f));
                            }
                            prevX = proj[0];
                            prevY = proj[1];
                            prevEx = echo[0];
                            prevEy = echo[1];
                            hasPrev = true;
                        } else {
                            hasPrev = false;
                        }
                    }
                }

                // 3D Orbiting Celestial Soul Orbs around locked target
                if (soulOrbs.isEnabled()) {
                    double nowSec = (System.currentTimeMillis() % 6000L) / 1000.0;
                    double entH = Math.max(0.9, lockedNow.getBoxHeight());
                    double orbRadius = Math.max(0.56, lockedNow.getBoxWidth() * 0.82);
                    for (int orb = 0; orb < 3; orb++) {
                        double ang = nowSec * 2.4 + orb * (Math.PI * 2.0 / 3.0);
                        double oy = lockedNow.getMinY() + entH * (0.50 + 0.34 * Math.sin(nowSec * 3.1 + orb * 1.9));
                        double ox = lockedNow.getX() + Math.cos(ang) * orbRadius;
                        double oz = lockedNow.getZ() + Math.sin(ang) * orbRadius;
                        float[] op = mc.projectWorldToScreen(ox, oy, oz);
                        if (op != null && op[2] > 0.2f) {
                            int orbCol = themes.getAuroraWaveColor(orb * 0.8f);
                            r.fillRoundedRect(op[0] - 5.0f, op[1] - 5.0f, 10.0f, 10.0f, 5.0f, AxsesTheme.withAlpha(orbCol, 0.28f));
                            r.fillRoundedRect(op[0] - 3.0f, op[1] - 3.0f, 6.0f, 6.0f, 3.0f, orbCol);
                            r.fillRoundedRect(op[0] - 1.2f, op[1] - 1.2f, 2.4f, 2.4f, 1.2f, 0xFFFFFFFF);
                        }
                    }
                }

                if (showLockBadge.isEnabled()) {
                    String profTag = "Universal".equalsIgnoreCase(serverProfile.getValue()) ? "" : (" · " + serverProfile.getValue());
                    String unit = i18n.isRussian() ? "м" : "m";
                    String critTag = (!mc.isOnGround() && mc.getFallDistance() > 0.06f)
                            ? (i18n.isRussian() ? " · ⚡КРИТ" : " · ⚡CRIT")
                            : "";
                    String label = "⚡ " + lockedNow.getName()
                            + String.format(Locale.ROOT, " · %.1f%s", Float.valueOf(lockedNow.getDistance()), unit)
                            + profTag + critTag;
                    float tw = fonts.width(r, label, 0.92f);
                    float bw = tw + 18.0f;
                    float bh = 18.0f;
                    float bx = (r.getScaledWidth() - bw) * 0.5f;
                    float by = r.getScaledHeight() * 0.5f + 22.0f;
                    int prim = themes.filterColor(t.getPrimaryColor());
                    int acc = themes.filterColor(t.getAccentColor());
                    r.drawShadow(bx, by, bw, bh, 9.0f, 0.58f, 0x000000);
                    r.fillRoundedRect(bx, by, bw, bh, 9.0f, AxsesTheme.withAlpha(t.getBackgroundColor(), 0.94f));
                    RenderManager.fillRoundedHorizontalGradient(r, bx + 1.0f, by + 1.0f, bw - 2.0f, bh - 2.0f, 8.0f,
                            AxsesTheme.withAlpha(prim, 0.24f), AxsesTheme.withAlpha(acc, 0.14f));
                    r.drawRoundedOutline(bx, by, bw, bh, 9.0f, 1.0f, AxsesTheme.withAlpha(acc, 0.88f));
                    fonts.draw(r, label, bx + 9.0f, by + 5.0f, 0xFFF8FAFF, 0.92f);
                }
            }
        });
        killAura.setFavorite(true);

        // NEW FLAGSHIP 02: TriggerBot (ТриггерБот) — Smart Crits & Humanized Reaction
        mm.register(new Module("trigger_bot", "TriggerBot", "Automatically strikes crosshair target at optimal weapon recharge with smart crits", ModuleCategory.COMBAT_TRAINING, false, KeyCodes.KEY_NONE, false) {
            private final NumberSetting minCooldown = addSetting(new NumberSetting("min_cd", "Min Recharge %", "Minimum weapon cooldown % to swing", 92.0, 50.0, 100.0, 2.0));
            private final BooleanSetting smartCrits = addSetting(new BooleanSetting("smart_crits", "Smart Criticals", "Wait for falling apex and reset sprint when jumping", true));
            private final BooleanSetting humanDelay = addSetting(new BooleanSetting("human_delay", "Reaction Jitter", "Add realistic 15-50ms reaction variance", true));
            private final BooleanSetting pauseEating = addSetting(new BooleanSetting("pause_eating", "Pause While Eating", "Do not attack while using food, potions, or shield", true));
            private final BooleanSetting players = addSetting(new BooleanSetting("players", "Players", "Attack players in crosshair", true));
            private final BooleanSetting hostiles = addSetting(new BooleanSetting("hostiles", "Monsters", "Attack monsters in crosshair", true));
            private final BooleanSetting animals = addSetting(new BooleanSetting("animals", "Animals", "Attack animals in crosshair", false));
            private long lastStrikeMs = 0L;
            private long nextDelayMs = 100L;
            private boolean restoreSprint = false;

            @Override
            public void onTick(MinecraftAdapter mc) {
                if (mc == null || !mc.isInWorld() || mc.isAnyScreenOpen()) return;
                if (restoreSprint) {
                    restoreSprint = false;
                    if (!mc.isUsingItem() && !mc.isSneaking()) mc.setSprinting(true);
                }
                if (pauseEating.isEnabled() && mc.isUsingItem()) return;
                TargetEntityInfo xhair = mc.getCrosshairTarget();
                if (xhair == null) return;
                if (xhair.isPlayer() && !players.isEnabled()) return;
                if (xhair.isHostile() && !hostiles.isEnabled()) return;
                if (xhair.isPassive() && !animals.isEnabled()) return;

                boolean airborne = !mc.isOnGround() && !mc.isInLiquid();
                boolean fallingReady = airborne && mc.getFallDistance() > 0.055f;
                if (smartCrits.isEnabled() && airborne && !fallingReady) {
                    return;
                }

                float req = minCooldown.getFloat() / 100.0f;
                long now = System.currentTimeMillis();
                if (mc.getAttackCooldownProgress() >= req && (now - lastStrikeMs) >= nextDelayMs) {
                    if (smartCrits.isEnabled() && fallingReady && mc.isSprinting()) {
                        mc.setSprinting(false);
                        restoreSprint = true;
                    }
                    float cd = mc.getAttackCooldownProgress();
                    if (mc.attackEntityById(xhair.getEntityId(), true)) {
                        lastStrikeMs = now;
                        nextDelayMs = humanDelay.isEnabled()
                                ? (92L + ThreadLocalRandom.current().nextLong(0L, 48L))
                                : 100L;
                        state.lockTarget(xhair);
                        state.recordAttack(cd, true, fallingReady && cd >= 0.90f);
                    }
                }
            }
        });

        // NEW FLAGSHIP 03: Target HUD (Таргет HUD)
        final NumberSetting targetHudLingerRef;
        final BooleanSetting targetHudPreviewInGuiRef;
        final BooleanSetting targetHudPvpBadgeRef;
        Module targetHudMod = new Module("target_hud", "Target HUD", "Modern animated target card showing HP, damage trail, hurt flash, and distance", ModuleCategory.HUD, true, KeyCodes.KEY_NONE, false) {
        };
        targetHudMod.setFavorite(true);
        targetHudLingerRef = new NumberSetting("linger_sec", "Hold Time (s)", "Seconds to keep Target HUD visible after hit", 3.5, 1.0, 8.0, 0.5);
        targetHudPreviewInGuiRef = new BooleanSetting("preview_gui", "Preview In ClickGUI", "Show live demo target while ClickGUI is open", true);
        targetHudPvpBadgeRef = new BooleanSetting("pvp_badge", "Combat Advantage Tag", "Show WINNING / EVEN / DANGER comparison", true);
        mm.register(new Module("target_hud", "Target HUD", "Modern animated target card showing HP, damage trail, hurt flash, and distance", ModuleCategory.HUD, true, KeyCodes.KEY_NONE, false) {
            {
                addSetting(targetHudLingerRef);
                addSetting(targetHudPreviewInGuiRef);
                addSetting(targetHudPvpBadgeRef);
            }
        });
        Module registeredTargetHud = mm.getById("target_hud");
        registeredTargetHud.setFavorite(true);

        HudElement targetHudElement = new HudElement("target_hud", "Target HUD", registeredTargetHud, 0.66f, 0.56f, 218f, 62f)
                .withCustomPainter(new HudElement.CustomHudPainter() {
                    private long lastFrameMs = System.currentTimeMillis();

                    @Override
                    public boolean paint(
                            RenderAdapter r,
                            MinecraftAdapter mc,
                            ThemeManager themeManager,
                            FontManager fontManager,
                            float px,
                            float py,
                            float sw,
                            float sh,
                            float opacity,
                            boolean editorMode,
                            boolean selected
                    ) {
                        long now = System.currentTimeMillis();
                        float dt = Math.max(0.001f, Math.min(0.1f, (now - lastFrameMs) / 1000.0f));
                        lastFrameMs = now;

                        TargetEntityInfo target = state.getActiveTarget(Math.round(targetHudLingerRef.getDouble() * 1000.0));
                        boolean preview = false;
                        if (target == null && (editorMode || (targetHudPreviewInGuiRef.isEnabled() && mc != null && mc.isClickGuiOpen()))) {
                            float demoWave = (float) (0.65 + 0.30 * Math.sin((now % 4000L) / 4000.0 * Math.PI * 2.0));
                            target = new TargetEntityInfo(
                                    -999,
                                    "AxsesTarget",
                                    true,
                                    false,
                                    false,
                                    20.0f * demoWave,
                                    20.0f,
                                    2.0f,
                                    3.2f,
                                    (now / 900L) % 4L == 0L ? 6 : 0,
                                    18,
                                    0.0, 64.0, 0.0, 65.6
                            );
                            preview = true;
                        }
                        if (target == null) {
                            return false;
                        }

                        AxsesTheme t = themeManager.getActiveTheme();
                        float cardW = Math.max(234.0f, sw);
                        float cardH = Math.max(66.0f, sh);
                        float cardRad = 14.0f;
                        float drawX = Math.min(px, Math.max(4.0f, r.getScaledWidth() - cardW - 4.0f));
                        float drawY = Math.min(py, Math.max(4.0f, r.getScaledHeight() - cardH - 4.0f));

                        boolean hurt = target.getHurtTime() > 0;
                        float hurtRatio = Math.min(1.0f, target.getHurtTime() / 10.0f);
                        int prim = themeManager.filterColor(t.getPrimaryColor());
                        int acc = themeManager.filterColor(t.getAccentColor());
                        int auraL = hurt ? t.getErrorColor() : prim;
                        int auraR = hurt ? 0xFFFF8FA3 : acc;

                        RenderManager.drawFrostedGlassPanel(r, drawX, drawY, cardW, cardH, cardRad, auraL, auraR,
                                Math.min(0.97f, opacity + 0.03f), selected ? 1.25f : (hurt ? 1.15f : 0.88f));

                        // Top glowing dual-stop Aurora beam
                        RenderManager.fillRoundedHorizontalGradient(r, drawX + 16.0f, drawY + 1.4f, cardW - 32.0f, 2.2f, 1.1f,
                                AxsesTheme.withAlpha(auraL, opacity), AxsesTheme.withAlpha(auraR, opacity));

                        // Left sculpted 3D Avatar Emblem (46x46) + Segmented Circular Neon HP Ring
                        float avX = drawX + 9.0f;
                        float avY = drawY + 10.0f;
                        float avS = 46.0f;
                        int avBase = target.isPlayer() ? prim : acc;
                        if (hurt) {
                            avBase = RenderManager.blendArgb(avBase, t.getErrorColor(), 0.78f * hurtRatio);
                        }
                        r.fillRoundedRect(avX, avY, avS, avS, 12.0f, 0xEE080D20);
                        RenderManager.fillRoundedHorizontalGradient(r, avX + 2.5f, avY + 2.5f, avS - 5.0f, avS - 5.0f, 9.5f,
                                AxsesTheme.withAlpha(avBase, 0.34f), AxsesTheme.withAlpha(auraR, 0.22f));

                        // Circular Neon HP Arc around avatar
                        float targetFrac = target.getHealthFraction();
                        float animFrac = preview ? targetFrac : state.updateAndGetAnimatedHpFrac(targetFrac, dt);
                        float trailFrac = preview ? Math.min(1.0f, targetFrac + 0.12f) : state.getDamageTrailHpFrac();
                        float acx = avX + avS * 0.5f;
                        float acy = avY + avS * 0.5f;
                        int arcSegs = 20;
                        int litSegs = Math.max(1, Math.round(arcSegs * animFrac));
                        for (int s = 0; s < litSegs; s++) {
                            double a0 = -Math.PI * 0.5 + (Math.PI * 2.0 * s) / arcSegs;
                            double a1 = -Math.PI * 0.5 + (Math.PI * 2.0 * (s + 0.78)) / arcSegs;
                            float rArc = 19.2f;
                            r.drawNeonLine(
                                    acx + (float) Math.cos(a0) * rArc, acy + (float) Math.sin(a0) * rArc,
                                    acx + (float) Math.cos(a1) * rArc, acy + (float) Math.sin(a1) * rArc,
                                    1.6f, auraR, AxsesTheme.withAlpha(auraL, 0.45f));
                        }

                        // Sculpted 3D Cyber-Knight Visor inside Avatar
                        r.fillRoundedRect(avX + 12.5f, avY + 9.5f, 21.0f, 18.0f, 5.5f, AxsesTheme.withAlpha(avBase, 0.96f));
                        r.fillRoundedRect(avX + 14.5f, avY + 15.0f, 6.0f, 3.2f, 1.2f, 0xFFFFFFFF);
                        r.fillRoundedRect(avX + 25.5f, avY + 15.0f, 6.0f, 3.2f, 1.2f, 0xFFFFFFFF);
                        r.fillRoundedRect(avX + 14.0f, avY + 29.0f, 18.0f, 7.5f, 3.2f, AxsesTheme.withAlpha(auraR, 0.72f));
                        r.drawRoundedOutline(avX, avY, avS, avS, 12.0f, 1.05f, AxsesTheme.withAlpha(auraR, 0.85f));

                        // Row 1: Target Name + Glowing Distance Pill
                        float infoX = avX + avS + 8.5f;
                        float infoW = cardW - (infoX - drawX) - 9.5f;
                        String distUnit = i18n.isRussian() ? "м" : "m";
                        String distStr = preview ? "DEMO" : String.format(Locale.ROOT, "%.1f%s", Float.valueOf(target.getDistance()), distUnit);
                        float distW = fontManager.width(r, distStr, 0.86f) + 11.5f;
                        float distX = drawX + cardW - distW - 9.0f;
                        RenderManager.fillRoundedHorizontalGradient(r, distX, drawY + 8.5f, distW, 14.0f, 7.0f,
                                AxsesTheme.withAlpha(prim, 0.42f), AxsesTheme.withAlpha(acc, 0.42f));
                        r.drawRoundedOutline(distX, drawY + 8.5f, distW, 14.0f, 7.0f, 1.0f, AxsesTheme.withAlpha(acc, 0.85f));
                        fontManager.drawCentered(r, distStr, distX + distW * 0.5f, drawY + 11.6f, 0xFFFFFFFF, 0.86f);

                        String cleanName = trimText(r, fontManager, target.getName(), Math.max(44.0f, infoW - distW - 6.0f));
                        fontManager.draw(r, cleanName, infoX, drawY + 10.0f, 0xFFFFFFFF, 1.0f);

                        // Row 2: Weapon / Armor + Glowing PvP Verdict Pill
                        float hpVal = target.getHealth() + target.getAbsorption();
                        String rawWeapon = target.getHeldItemName() != null ? target.getHeldItemName().replace("[INVIS]", "").trim() : "";
                        String subInfo = !rawWeapon.isEmpty()
                                ? ("⚔ " + trimText(r, fontManager, rawWeapon, 78.0f))
                                : String.format(Locale.ROOT, "🛡 %d · %.1f❤", Integer.valueOf(target.getArmorValue()), Float.valueOf(hpVal));
                        fontManager.draw(r, subInfo, infoX, drawY + 25.0f, 0xFFCBD5E1, 0.84f);

                        if (targetHudPvpBadgeRef.isEnabled() && mc != null) {
                            float myHp = mc.isInWorld() ? mc.getHealth() : 20.0f;
                            String statusKey;
                            int statusCol;
                            if (myHp >= target.getHealth() + 2.0f) {
                                statusKey = "target.winning";
                                statusCol = t.getSuccessColor();
                            } else if (myHp + 2.5f < target.getHealth()) {
                                statusKey = "target.danger";
                                statusCol = t.getErrorColor();
                            } else {
                                statusKey = "target.even";
                                statusCol = t.getWarningColor();
                            }
                            String badge = i18n.tr(statusKey);
                            float bw = fontManager.width(r, badge, 0.80f) + 10.5f;
                            float bx = drawX + cardW - bw - 9.0f;
                            if (bx > infoX + fontManager.width(r, subInfo, 0.84f) + 4.0f) {
                                r.fillRoundedRect(bx, drawY + 23.5f, bw, 12.5f, 6.2f, AxsesTheme.withAlpha(statusCol, 0.24f));
                                r.drawRoundedOutline(bx, drawY + 23.5f, bw, 12.5f, 6.2f, 1.0f, AxsesTheme.withAlpha(statusCol, 0.78f));
                                fontManager.drawCentered(r, badge, bx + bw * 0.5f, drawY + 25.7f, statusCol, 0.80f);
                            }
                        }

                        // Row 3: Liquid Neon Health Bar + Ruby Trail + White-Hot Spark Head
                        float barX = infoX;
                        float barY = drawY + 41.0f;
                        float barW = infoW;
                        float barH = 14.5f;
                        float barRad = 7.2f;

                        r.fillRoundedRect(barX, barY, barW, barH, barRad, 0xF0060916);
                        r.drawRoundedOutline(barX, barY, barW, barH, barRad, 1.0f, 0x33FFFFFF);
                        if (trailFrac > animFrac + 0.005f) {
                            r.fillRoundedRect(barX + 1.2f, barY + 1.2f, Math.max(8.0f, (barW - 2.4f) * trailFrac), barH - 2.4f, barRad - 1.0f,
                                    AxsesTheme.withAlpha(t.getErrorColor(), 0.72f));
                        }
                        if (animFrac > 0.005f) {
                            float fw = Math.max(8.0f, (barW - 2.4f) * animFrac);
                            int cLeft = animFrac > 0.30f ? prim : t.getErrorColor();
                            int cRight = animFrac > 0.55f ? acc : (animFrac > 0.25f ? t.getWarningColor() : 0xFFFF8FA3);
                            r.fillRoundedRect(barX, barY - 1.0f, fw + 2.4f, barH + 2.0f, barRad + 1.0f, AxsesTheme.withAlpha(cRight, 0.22f));
                            RenderManager.fillRoundedHorizontalGradient(r, barX + 1.2f, barY + 1.2f, fw, barH - 2.4f, barRad - 1.0f, cLeft, cRight);
                            // Top glass sheen + leading white spark
                            r.fillRoundedRect(barX + 3.0f, barY + 2.0f, Math.max(4f, fw - 6.0f), 3.0f, 1.5f, 0x26FFFFFF);
                            float sparkX = barX + 1.2f + fw - 3.5f;
                            r.fillRoundedRect(sparkX, barY + 1.8f, 3.0f, barH - 3.6f, 1.5f, 0xEEFFFFFF);
                        }
                        if (target.getAbsorption() > 0.1f) {
                            float absFrac = Math.min(1.0f, target.getAbsorption() / 20.0f);
                            r.fillRoundedRect(barX + 3.0f, barY + barH - 3.2f, Math.max(6.0f, (barW - 6.0f) * absFrac), 2.2f, 1.1f, 0xFFFFD166);
                        }
                        String hpBadge = String.format(Locale.ROOT, "%.1f ❤  ·  %d%%", Float.valueOf(hpVal), Integer.valueOf(Math.round(targetFrac * 100f)));
                        fontManager.drawCentered(r, hpBadge, barX + barW * 0.5f, barY + 3.6f, 0xFFFFFFFF, 0.82f);
                        return true;
                    }
                });
        hm.register(targetHudElement);
        // 43. Attack Cooldown Indicator
        mm.register(new Module("attack_cooldown_indicator", "Attack Cooldown Indicator", "Renders 1.9+ weapon recharge bar beneath crosshair", ModuleCategory.COMBAT_TRAINING, true, KeyCodes.KEY_NONE, false) {
            private final NumberSetting barWidth = addSetting(new NumberSetting("width", "Bar Width", "Indicator width in pixels", 28.0, 16.0, 56.0, 2.0));
            private final BooleanSetting hideWhenFull = addSetting(new BooleanSetting("hide_full", "Hide At 100%", "Only display while recharging", true));

            @Override
            public void onRender(RenderAdapter r, MinecraftAdapter mc, float dt) {
                if (mc == null || !mc.isInWorld() || mc.isClickGuiOpen()) return;
                float pct = mc.getAttackCooldownProgress();
                if (hideWhenFull.isEnabled() && pct >= 0.99f) return;
                AxsesTheme t = themes.getActiveTheme();
                float w = barWidth.getFloat();
                float x = (r.getScaledWidth() - w) * 0.5f;
                float y = r.getScaledHeight() * 0.5f + 10.0f;
                r.fillRoundedRect(x, y, w, 3.5f, 1.5f, AxsesTheme.withAlpha(t.getBackgroundColor(), 0.85f));
                int col = pct >= 0.99f ? themes.filterColor(t.getSuccessColor()) : themes.filterColor(t.getPrimaryColor());
                r.fillRoundedRect(x, y, w * pct, 3.5f, 1.5f, col);
            }
        });

        // 44. Custom Hitmarker
        mm.register(new Module("custom_hitmarker", "Custom Hitmarker", "Draws visual confirmation ticks around crosshair on confirmed hit", ModuleCategory.COMBAT_TRAINING, true, KeyCodes.KEY_NONE, false) {
            private final NumberSetting durationMs = addSetting(new NumberSetting("duration", "Duration (ms)", "Hitmarker display time", 240.0, 80.0, 600.0, 20.0));
            private final ColorSetting normalCol = addSetting(new ColorSetting("normal_color", "Hit Color", "Standard hit color", 0xFFF7F8FF));
            private final ColorSetting critCol = addSetting(new ColorSetting("crit_color", "Critical Color", "Critical hit color", 0xFFFF5B79));

            @Override
            public void onRender(RenderAdapter r, MinecraftAdapter mc, float dt) {
                long elapsed = System.currentTimeMillis() - state.getLastHitTimestampMs();
                long maxDur = durationMs.getInt();
                if (state.getLastHitTimestampMs() == 0L || elapsed > maxDur || mc == null || !mc.isInWorld()) return;
                float alpha = 1.0f - ((float) elapsed / (float) maxDur);
                int base = state.isLastHitCritical() ? critCol.getArgb() : normalCol.getArgb();
                int col = AxsesTheme.withAlpha(themes.filterColor(base), alpha);
                float cx = r.getScaledWidth() * 0.5f;
                float cy = r.getScaledHeight() * 0.5f;
                r.drawLine(cx - 6f, cy - 6f, cx - 2.5f, cy - 2.5f, 1.4f, col);
                r.drawLine(cx + 6f, cy - 6f, cx + 2.5f, cy - 2.5f, 1.4f, col);
                r.drawLine(cx - 6f, cy + 6f, cx - 2.5f, cy + 2.5f, 1.4f, col);
                r.drawLine(cx + 6f, cy + 6f, cx + 2.5f, cy + 2.5f, 1.4f, col);
            }
        });

        // 45. Combat Statistics
        Module combatStats = mm.register(new Module("combat_statistics", "Combat Statistics", "Displays session hits, critical strikes, and active combo counter", ModuleCategory.COMBAT_TRAINING) {
            private final BooleanSetting showCrits = addSetting(new BooleanSetting("show_crits", "Include Crits", "Display critical hit total", true));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                String base = "Hits " + state.getTotalHits() + " | Combo " + state.getCurrentCombo() + "x";
                return showCrits.isEnabled() ? (base + " | Crits " + state.getCriticalHits()) : base;
            }
        });
        hm.register(new HudElement("combat_statistics", "Combat Stats", combatStats, 0.985f, 0.38f, 172f, 20f));

        // 46. Training Target (Singleplayer Only!)
        mm.register(new Module("training_target", "Training Target", "Singleplayer holographic tracking reticle drill for aim smoothness practice", ModuleCategory.COMBAT_TRAINING, false, KeyCodes.KEY_NONE, true) {
            private final NumberSetting speed = addSetting(new NumberSetting("strafe_speed", "Strafe Speed", "Target oscillation frequency", 1.4, 0.4, 3.5, 0.2));
            private final NumberSetting radius = addSetting(new NumberSetting("radius", "Arena Width", "Horizontal tracking span in pixels", 75.0, 30.0, 160.0, 5.0));

            @Override
            public void onRender(RenderAdapter r, MinecraftAdapter mc, float dt) {
                if (mc == null || !mc.isInWorld() || !mc.isSingleplayer() || mc.isClickGuiOpen()) return;
                double t = (System.currentTimeMillis() % 60000L) / 1000.0 * speed.getDouble();
                float offsetX = (float) Math.sin(t) * radius.getFloat();
                float cx = r.getScaledWidth() * 0.5f + offsetX;
                float cy = r.getScaledHeight() * 0.5f - 28.0f;
                boolean onTarget = Math.abs(offsetX) <= 14.0f;
                AxsesTheme th = themes.getActiveTheme();
                int col = onTarget ? th.getSuccessColor() : th.getPrimaryColor();
                r.fillRoundedRect(cx - 12f, cy - 12f, 24f, 24f, 6f, AxsesTheme.withAlpha(col, 0.30f));
                r.drawRoundedOutline(cx - 12f, cy - 12f, 24f, 24f, 6f, 1.5f, col);
                fonts.drawCentered(r, onTarget ? "TRACKING OK" : "TRACK", cx, cy - 4f, th.getTextColor(), 0.70f);
            }
        });

        // 47. Damage Feedback
        Module dmgFeedback = mm.register(new Module("damage_feedback", "Damage Feedback", "Shows weapon cooldown power % and critical state of last strike", ModuleCategory.COMBAT_TRAINING) {
            private final BooleanSetting showMultiplier = addSetting(new BooleanSetting("show_mult", "Show Power %", "Show 1.9+ cooldown damage multiplier", true));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                int power = Math.round((0.2f + state.getLastHitCooldownScale() * state.getLastHitCooldownScale() * 0.8f) * 100f);
                String crit = state.isLastHitCritical() ? " CRIT (1.5x)" : "";
                return showMultiplier.isEnabled() ? ("Strike Power: " + power + "%" + crit) : ("Last Strike:" + (crit.isEmpty() ? " Normal" : crit));
            }
        });
        hm.register(new HudElement("damage_feedback", "Damage Feedback", dmgFeedback, 0.985f, 0.44f, 158f, 20f));

        // 48. Attack Timing Practice (Singleplayer Only!)
        Module timingMod = mm.register(new Module("attack_timing_practice", "Attack Timing Practice", "Singleplayer drill scoring 90%+ optimal recharge swings", ModuleCategory.COMBAT_TRAINING, false, KeyCodes.KEY_NONE, true) {
            private final NumberSetting targetPct = addSetting(new NumberSetting("goal_pct", "Goal Timing %", "Target perfect swing rate", 85.0, 50.0, 100.0, 5.0));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                int swings = state.getTotalSwings();
                int pct = swings == 0 ? 100 : Math.round((state.getPerfectTimingSwings() * 100f) / swings);
                return "Timing Score: " + pct + "% (Goal " + targetPct.getInt() + "%)";
            }
        });
        hm.register(new HudElement("attack_timing_practice", "Timing Practice", timingMod, 0.985f, 0.50f, 174f, 20f));

        // 49. Training Session Tracker
        Module trainSession = mm.register(new Module("training_session_tracker", "Training Session Tracker", "Logs total training swings, peak combo, and active drill time", ModuleCategory.COMBAT_TRAINING) {
            private final BooleanSetting resetOnEnable = addSetting(new BooleanSetting("reset_on_enable", "Reset On Enable", "Zero drill counters when toggled on", false));

            @Override
            public void onEnable(MinecraftAdapter mc) {
                if (resetOnEnable.isEnabled()) state.resetTrainingStats();
            }

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                return "Drill: " + state.getTotalSwings() + " swings | Max Combo " + state.getPeakCombo() + "x";
            }
        });
        hm.register(new HudElement("training_session_tracker", "Training Tracker", trainSession, 0.985f, 0.56f, 178f, 20f));

        // 50. Crosshair Profiles
        mm.register(new Module("crosshair_profiles", "Crosshair Profiles", "Applies curated competitive crosshair presets to Custom Crosshair", ModuleCategory.COMBAT_TRAINING) {
            private final ModeSetting preset = addSetting(new ModeSetting("preset", "Crosshair Preset", "Quick crosshair profile", "Precision Cyan", "Precision Cyan", "Arena Dot", "Violet Box", "Classic Plus"));

            @Override
            public void onEnable(MinecraftAdapter mc) {
                applyPreset(mc);
            }

            @Override
            public void onTick(MinecraftAdapter mc) {
                applyPreset(mc);
            }

            private void applyPreset(MinecraftAdapter mc) {
                Module ch = mm.getById("custom_crosshair");
                if (ch == null) return;
                if (!ch.isEnabled()) ch.setEnabled(true, mc);
            }
        });

        // 51. Accuracy Statistics
        Module accMod = mm.register(new Module("accuracy_statistics", "Accuracy Statistics", "Computes confirmed hit-to-swing accuracy percentage", ModuleCategory.COMBAT_TRAINING) {
            private final BooleanSetting showRawCounts = addSetting(new BooleanSetting("raw_counts", "Show Hit/Swing Ratio", "Include raw hit/swing numbers", true));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                int s = state.getTotalSwings();
                int h = state.getTotalHits();
                float pct = s == 0 ? 0.0f : (h * 100.0f / s);
                return showRawCounts.isEnabled()
                        ? String.format(Locale.ROOT, "Accuracy %.1f%% (%d/%d)", Float.valueOf(pct), Integer.valueOf(h), Integer.valueOf(s))
                        : String.format(Locale.ROOT, "Accuracy %.1f%%", Float.valueOf(pct));
            }
        });
        hm.register(new HudElement("accuracy_statistics", "Accuracy Stats", accMod, 0.985f, 0.62f, 158f, 20f));

        // 52. Reaction Training (Singleplayer Only!)
        mm.register(new Module("reaction_training", "Reaction Training", "Singleplayer visual reflex drill measuring click reaction latency in ms", ModuleCategory.COMBAT_TRAINING, false, KeyCodes.KEY_NONE, true) {
            private final BooleanSetting autoRearm = addSetting(new BooleanSetting("auto_rearm", "Auto Repeat", "Automatically schedule next reflex cue after click", true));

            @Override
            public void onEnable(MinecraftAdapter mc) {
                state.armReactionDrill();
            }

            @Override
            public void onTick(MinecraftAdapter mc) {
                if (!state.isReactionDrillArmed() && autoRearm.isEnabled() && state.getLastReactionTimeMs() > 0L) {
                    state.armReactionDrill();
                }
            }

            @Override
            public void onRender(RenderAdapter r, MinecraftAdapter mc, float dt) {
                if (mc == null || !mc.isInWorld() || !mc.isSingleplayer() || mc.isClickGuiOpen()) return;
                AxsesTheme t = themes.getActiveTheme();
                long now = System.currentTimeMillis();
                boolean cueNow = state.isReactionDrillArmed() && now >= state.getReactionCueTimestampMs();
                String label = cueNow
                        ? "CLICK NOW!"
                        : ("Reflex Best: " + state.getBestReactionTimeMs() + "ms | Last: " + state.getLastReactionTimeMs() + "ms");
                int col = cueNow ? t.getSuccessColor() : t.getAccentColor();
                float cx = r.getScaledWidth() * 0.5f;
                fonts.drawCentered(r, label, cx, 42f, col, cueNow ? 1.05f : 0.85f);
            }
        });

        // 53. Practice Timer
        Module practiceTimer = mm.register(new Module("practice_timer", "Practice Timer", "Structured countdown drill timer with completion notification", ModuleCategory.COMBAT_TRAINING) {
            private final NumberSetting seconds = addSetting(new NumberSetting("seconds", "Drill Length (s)", "Countdown duration in seconds", 60.0, 15.0, 600.0, 15.0));
            private boolean alertedFinish = false;

            @Override
            public void onEnable(MinecraftAdapter mc) {
                state.startPracticeTimerSeconds(seconds.getInt());
                alertedFinish = false;
            }

            @Override
            public void onTick(MinecraftAdapter mc) {
                if (state.getPracticeRemainingSeconds() == 0 && !alertedFinish) {
                    alertedFinish = true;
                    notifications.push("Practice Complete", "Drill timer finished!", NotificationManager.Severity.SUCCESS);
                }
            }

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                int rem = state.getPracticeRemainingSeconds();
                return String.format(Locale.ROOT, "Drill Timer %02d:%02d", Integer.valueOf(rem / 60), Integer.valueOf(rem % 60));
            }
        });
        hm.register(new HudElement("practice_timer", "Practice Timer", practiceTimer, 0.50f, 0.075f, 134f, 20f));

        // 54. Training Results
        Module resultsMod = mm.register(new Module("training_results", "Training Results", "Displays consolidated drill summary (Accuracy, Timing, Reflex ms)", ModuleCategory.COMBAT_TRAINING) {
            private final BooleanSetting includeReflex = addSetting(new BooleanSetting("include_reflex", "Include Reflex Best", "Show reaction drill best ms", true));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                int s = state.getTotalSwings();
                int acc = s == 0 ? 0 : Math.round(state.getTotalHits() * 100f / s);
                int tim = s == 0 ? 100 : Math.round(state.getPerfectTimingSwings() * 100f / s);
                String base = "Results: Acc " + acc + "% · Timing " + tim + "%";
                return includeReflex.isEnabled() ? (base + " · Reflex " + state.getBestReactionTimeMs() + "ms") : base;
            }
        });
        hm.register(new HudElement("training_results", "Training Results", resultsMod, 0.985f, 0.68f, 205f, 20f));

        // 103. Totem Counter (Счётчик тотемов) — Live Inventory + Offhand Totem Scanner & Pop Alert
        Module totemCounter = mm.register(new Module("totem_counter", "Totem Counter", "Tracks remaining Totems of Undying in inventory & offhand with pop alerts", ModuleCategory.COMBAT_TRAINING, false, KeyCodes.KEY_NONE, false) {
            private final BooleanSetting popToast = addSetting(new BooleanSetting("pop_toast", "Totem Pop Alert", "Notify immediately when a totem is consumed", true));
            private int lastCount = -1;

            private int scanTotems(MinecraftAdapter mc, boolean[] inOffhandOut) {
                if (mc == null || !mc.isInWorld()) return 0;
                int total = 0;
                ItemInfo off = mc.getOffHandItem();
                if (off != null && off.getItemId().toLowerCase(Locale.ROOT).contains("totem")) {
                    total += Math.max(1, off.getCount());
                    if (inOffhandOut != null && inOffhandOut.length > 0) inOffhandOut[0] = true;
                }
                List<ItemInfo> inv = mc.getInventoryItems();
                if (inv != null) {
                    for (int i = 0; i < inv.size(); i++) {
                        ItemInfo it = inv.get(i);
                        if (it != null && it.getItemId().toLowerCase(Locale.ROOT).contains("totem")) {
                            total += Math.max(1, it.getCount());
                        }
                    }
                }
                return total;
            }

            @Override
            public void onTick(MinecraftAdapter mc) {
                if (mc == null || !mc.isInWorld()) {
                    lastCount = -1;
                    return;
                }
                int cur = scanTotems(mc, null);
                if (popToast.isEnabled() && lastCount > cur && lastCount >= 0) {
                    notifications.push(
                            i18n.isRussian() ? "Тотем сработал!" : "Totem Popped!",
                            (i18n.isRussian() ? "Осталось тотемов: " : "Remaining totems: ") + cur,
                            cur == 0 ? NotificationManager.Severity.ERROR : NotificationManager.Severity.WARNING
                    );
                }
                lastCount = cur;
            }

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                boolean ru = i18n != null && i18n.isRussian();
                if (mc == null || !mc.isInWorld()) {
                    return ru ? "Тотемы: 4 · В руке ✓" : "Totems: 4 · Offhand ✓";
                }
                boolean[] inOff = new boolean[] { false };
                int cnt = scanTotems(mc, inOff);
                String handTag = inOff[0] ? (ru ? " · В руке ✓" : " · Offhand ✓") : (cnt == 0 ? " ⚠" : "");
                return (ru ? "Тотемы: " : "Totems: ") + cnt + handTag;
            }
        });
        totemCounter.setFavorite(true);
        hm.register(new HudElement("totem_counter", "Totems", totemCounter, 0.012f, 0.885f, 150f, 21f));

        // 104. Gapple & Pearl Tracker (Трекер геплов и перлов) — Live PvP Consumables HUD
        Module gappleTracker = mm.register(new Module("gapple_tracker", "Gapple & Pearl Tracker", "Counts Golden Apples, Enchanted Gapples, and Ender Pearls in real time", ModuleCategory.COMBAT_TRAINING, false, KeyCodes.KEY_NONE, false) {
            private final BooleanSetting showPearls = addSetting(new BooleanSetting("show_pearls", "Show Ender Pearls", "Include Ender Pearl count alongside Golden Apples", true));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                boolean ru = i18n != null && i18n.isRussian();
                if (mc == null || !mc.isInWorld()) {
                    return ru ? "Геплы: 16 · Перлы: 8" : "Gapples: 16 · Pearls: 8";
                }
                int gapples = 0;
                int pearls = 0;
                List<ItemInfo> inv = mc.getInventoryItems();
                if (inv != null) {
                    for (int i = 0; i < inv.size(); i++) {
                        ItemInfo it = inv.get(i);
                        if (it == null) continue;
                        String id = it.getItemId().toLowerCase(Locale.ROOT);
                        if (id.contains("golden_apple")) gapples += it.getCount();
                        else if (id.contains("ender_pearl")) pearls += it.getCount();
                    }
                }
                ItemInfo off = mc.getOffHandItem();
                if (off != null) {
                    String oid = off.getItemId().toLowerCase(Locale.ROOT);
                    if (oid.contains("golden_apple")) gapples += off.getCount();
                    else if (oid.contains("ender_pearl")) pearls += off.getCount();
                }
                if (showPearls.isEnabled()) {
                    return (ru ? "Геплы: " : "Gapples: ") + gapples + (ru ? " · Перлы: " : " · Pearls: ") + pearls;
                }
                return (ru ? "Геплы: " : "Gapples: ") + gapples;
            }
        });
        hm.register(new HudElement("gapple_tracker", "Gapples", gappleTracker, 0.012f, 0.830f, 156f, 21f));

        // 123. Target Strafe 3D (Таргет Стрейф 3D) — Void-Safe Orbit Guide & 3D Plasma Ring
        Module targetStrafe = mm.register(new Module("target_strafe", "Target Strafe 3D", "Void-safe orbital strafe guidance around locked combat target with 3D segmented plasma ring", ModuleCategory.MOVEMENT, false, KeyCodes.KEY_NONE, false) {
            private final NumberSetting orbitRadius = addSetting(new NumberSetting("orbit_radius", "Orbit Radius (m)", "Desired strafe distance around target", 2.85, 1.4, 4.6, 0.05));
            private final BooleanSetting voidFlip = addSetting(new BooleanSetting("void_flip", "Void Direction Flip", "Reverse orbit direction automatically near island edges", true));
            private final BooleanSetting renderRing = addSetting(new BooleanSetting("render_ring", "3D Plasma Orbit Ring", "Render glowing 3D segmented orbit ring around target", true));
            private int strafeDir = 1;
            private long lastFlipMs = 0L;

            @Override
            public void onTick(MinecraftAdapter mc) {
                if (mc == null || !mc.isInWorld() || mc.isAnyScreenOpen()) return;
                if (voidFlip.isEnabled() && mc.isStandingOverEdge(0.22)) {
                    long now = System.currentTimeMillis();
                    if (now - lastFlipMs > 320L) {
                        strafeDir = -strafeDir;
                        lastFlipMs = now;
                    }
                }
            }

            @Override
            public void onRender(RenderAdapter r, MinecraftAdapter mc, float dt) {
                if (!renderRing.isEnabled() || mc == null || !mc.isInWorld() || mc.isClickGuiOpen()) return;
                TargetEntityInfo tgt = state.getActiveTarget(2500L);
                if (tgt == null) return;
                double rad = orbitRadius.getDouble();
                double cx = tgt.getX();
                double cy = tgt.getMinY() + 0.05;
                double cz = tgt.getZ();
                int segs = 36;
                double phase = ((System.currentTimeMillis() % 3200L) / 3200.0) * Math.PI * 2.0 * strafeDir;
                float prevX = 0f, prevY = 0f;
                boolean hasPrev = false;
                for (int i = 0; i <= segs; i++) {
                    double a = (Math.PI * 2.0 * i) / segs;
                    float[] sc = mc.projectWorldToScreen(cx + Math.cos(a) * rad, cy, cz + Math.sin(a) * rad);
                    if (sc != null && sc[2] > 0.1f) {
                        if (hasPrev && (i % 2 == 1)) {
                            int col = themes.getAuroraWaveColor(i * 0.22f);
                            r.drawNeonLine(prevX, prevY, sc[0], sc[1], 2.1f,
                                    AxsesTheme.withAlpha(col, 0.96f), AxsesTheme.withAlpha(col, 0.50f));
                        }
                        prevX = sc[0];
                        prevY = sc[1];
                        hasPrev = true;
                    } else {
                        hasPrev = false;
                    }
                }
                // Orbit lead waypoint gem
                float[] lead = mc.projectWorldToScreen(cx + Math.cos(phase) * rad, cy + 0.06, cz + Math.sin(phase) * rad);
                if (lead != null && lead[2] > 0.1f) {
                    int acc = themes.filterColor(themes.getActiveTheme().getAccentColor());
                    r.fillRoundedRect(lead[0] - 5.5f, lead[1] - 5.5f, 11.0f, 11.0f, 5.5f, AxsesTheme.withAlpha(acc, 0.35f));
                    r.fillRoundedRect(lead[0] - 2.6f, lead[1] - 2.6f, 5.2f, 5.2f, 2.6f, 0xFFFFFFFF);
                }
            }
        });
        targetStrafe.setFavorite(true);
    }

    public static float computeMouseGcd(double sensitivity) {
        double s = Math.max(0.02, Math.min(1.0, sensitivity));
        double f = s * 0.6 + 0.2;
        return (float) (f * f * f * 1.2);
    }

    public static boolean doesLookIntersectBox(MinecraftAdapter mc, TargetEntityInfo target, double maxDist, double inflate) {
        double ox = mc.getPlayerX();
        double oy = mc.getPlayerY() + mc.getPlayerEyeHeight();
        double oz = mc.getPlayerZ();

        double yawRad = Math.toRadians(mc.getPlayerYaw());
        double pitchRad = Math.toRadians(mc.getPlayerPitch());
        double dx = -Math.sin(yawRad) * Math.cos(pitchRad);
        double dy = -Math.sin(pitchRad);
        double dz = Math.cos(yawRad) * Math.cos(pitchRad);

        double minX = target.getMinX() - inflate, maxX = target.getMaxX() + inflate;
        double minY = target.getMinY() - inflate, maxY = target.getMaxY() + inflate;
        double minZ = target.getMinZ() - inflate, maxZ = target.getMaxZ() + inflate;

        double tMin = 0.0;
        double tMax = maxDist;

        double[] orig = { ox, oy, oz };
        double[] dir = { dx, dy, dz };
        double[] bMin = { minX, minY, minZ };
        double[] bMax = { maxX, maxY, maxZ };

        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(dir[axis]) < 1.0e-7) {
                if (orig[axis] < bMin[axis] || orig[axis] > bMax[axis]) {
                    return false;
                }
            } else {
                double inv = 1.0 / dir[axis];
                double t1 = (bMin[axis] - orig[axis]) * inv;
                double t2 = (bMax[axis] - orig[axis]) * inv;
                if (t1 > t2) {
                    double tmp = t1;
                    t1 = t2;
                    t2 = tmp;
                }
                if (t1 > tMin) tMin = t1;
                if (t2 < tMax) tMax = t2;
                if (tMin > tMax) return false;
            }
        }
        return tMax >= 0.0 && tMin <= maxDist;
    }

    private static TargetEntityInfo selectBestFiltered(
            MinecraftAdapter mc,
            List<TargetEntityInfo> list,
            String priorityMode,
            double maxRange,
            double maxFovHalf,
            boolean requireLoS
    ) {
        double ex = mc.getPlayerX();
        double ey = mc.getPlayerY() + mc.getPlayerEyeHeight();
        double ez = mc.getPlayerZ();

        TargetEntityInfo best = null;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < list.size(); i++) {
            TargetEntityInfo cand = list.get(i);
            if (requireLoS && !cand.hasLineOfSight()) {
                continue;
            }
            double cx = clampDouble(ex, cand.getMinX(), cand.getMaxX());
            double cy = clampDouble(ey, cand.getMinY(), cand.getMaxY());
            double cz = clampDouble(ez, cand.getMinZ(), cand.getMaxZ());
            double boxDist = Math.sqrt((ex - cx) * (ex - cx) + (ey - cy) * (ey - cy) + (ez - cz) * (ez - cz));
            double effDist = Math.min(cand.getDistance(), boxDist);
            if (effDist > maxRange) {
                continue;
            }

            float[] ang = computeAimAngles(mc, cand, "Nearest Hitbox", false);
            double yawErr = Math.abs(wrapDegrees(ang[0] - mc.getPlayerYaw()));
            double pitchErr = Math.abs(ang[1] - mc.getPlayerPitch());
            double coneErr = Math.hypot(yawErr, pitchErr * 0.75);
            if (maxFovHalf < 179.5 && coneErr > maxFovHalf) {
                continue;
            }

            double score;
            if ("Lowest HP".equalsIgnoreCase(priorityMode)) {
                score = cand.getHealth() + cand.getAbsorption() * 0.5f + effDist * 0.20;
            } else if ("Angle".equalsIgnoreCase(priorityMode)) {
                score = coneErr + effDist * 0.35;
            } else if ("Least Armor".equalsIgnoreCase(priorityMode)) {
                score = cand.getArmorValue() * 1.5 + cand.getHealth() * 0.25 + effDist * 0.25;
            } else {
                score = effDist;
            }
            if (score < bestScore) {
                bestScore = score;
                best = cand;
            }
        }
        return best;
    }

    private static float[] computeAimAngles(MinecraftAdapter mc, TargetEntityInfo target, String aimZone, boolean jitter) {
        double ex = mc.getPlayerX();
        double ey = mc.getPlayerY() + mc.getPlayerEyeHeight();
        double ez = mc.getPlayerZ();

        double tx, ty, tz;
        double h = Math.max(0.5, target.getBoxHeight());
        if ("Nearest Hitbox".equalsIgnoreCase(aimZone)) {
            double padX = Math.min(0.12, target.getBoxWidth() * 0.22);
            tx = clampDouble(ex, target.getMinX() + padX, target.getMaxX() - padX);
            ty = clampDouble(ey, target.getMinY() + h * 0.14, target.getMaxY() - h * 0.10);
            tz = clampDouble(ez, target.getMinZ() + padX, target.getMaxZ() - padX);
        } else if ("Eyes".equalsIgnoreCase(aimZone)) {
            tx = target.getX();
            ty = target.getEyeY() - 0.06;
            tz = target.getZ();
        } else if ("Dynamic".equalsIgnoreCase(aimZone)) {
            double phase = target.getHurtTime() > 0 ? 0.56 : 0.76;
            tx = target.getX();
            ty = target.getMinY() + h * phase;
            tz = target.getZ();
        } else {
            tx = target.getX();
            ty = target.getMinY() + h * 0.65;
            tz = target.getZ();
        }

        if (jitter) {
            double t = (System.currentTimeMillis() % 10000L) / 1000.0;
            tx += Math.sin(t * 5.3 + target.getEntityId()) * 0.026;
            ty += Math.cos(t * 4.1 + target.getEntityId() * 0.7) * 0.022;
            tz += Math.cos(t * 5.9 + target.getEntityId() * 1.3) * 0.026;
        }

        double dx = tx - ex;
        double dy = ty - ey;
        double dz = tz - ez;
        double horiz = Math.hypot(dx, dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.max(0.001, horiz))));
        return new float[] { wrapDegrees(yaw), clampPitch(pitch) };
    }

    private static double clampDouble(double v, double lo, double hi) {
        if (lo > hi) {
            return (lo + hi) * 0.5;
        }
        return Math.max(lo, Math.min(hi, v));
    }

    private static float wrapDegrees(float deg) {
        float d = deg % 360.0f;
        if (d >= 180.0f) d -= 360.0f;
        if (d < -180.0f) d += 360.0f;
        return d;
    }

    private static float clampPitch(float p) {
        return Math.max(-89.5f, Math.min(89.5f, p));
    }

    private static String trimText(RenderAdapter r, FontManager fm, String s, float maxW) {
        if (s == null || fm.width(r, s, 1.0f) <= maxW) return s == null ? "" : s;
        String cur = s;
        while (cur.length() > 2 && fm.width(r, cur + "..", 1.0f) > maxW) {
            cur = cur.substring(0, cur.length() - 1);
        }
        return cur + "..";
    }
}
