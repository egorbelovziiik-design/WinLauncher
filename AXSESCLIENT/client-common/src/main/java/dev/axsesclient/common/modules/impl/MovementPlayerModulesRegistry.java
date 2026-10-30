package dev.axsesclient.common.modules.impl;

import dev.axsesclient.common.hud.HudElement;
import dev.axsesclient.common.hud.HudManager;
import dev.axsesclient.common.modules.Module;
import dev.axsesclient.common.modules.ModuleCategory;
import dev.axsesclient.common.modules.ModuleManager;
import dev.axsesclient.common.platform.InputAdapter;
import dev.axsesclient.common.platform.KeyCodes;
import dev.axsesclient.common.platform.MinecraftAdapter;
import dev.axsesclient.common.settings.BooleanSetting;
import dev.axsesclient.common.settings.ModeSetting;
import dev.axsesclient.common.settings.NumberSetting;
import java.util.Locale;

public final class MovementPlayerModulesRegistry {
    private MovementPlayerModulesRegistry() {
    }

    public static void registerAll(
            ModuleManager mm,
            HudManager hm,
            final ClientRuntimeState state,
            final InputAdapter input
    ) {
        // 31. Toggle Sprint
        final Module toggleSprint = mm.register(new Module("toggle_sprint", "Toggle Sprint", "Accessibility latch keeping forward sprint active without holding key", ModuleCategory.MOVEMENT, true, KeyCodes.KEY_NONE, false) {
            private final BooleanSetting onlyForward = addSetting(new BooleanSetting("only_forward", "Forward Only", "Engage sprint only when W is pressed", true));

            @Override
            public void onTick(MinecraftAdapter mc) {
                if (mc == null || !mc.isInWorld() || mc.isClickGuiOpen()) return;
                boolean forwardPressed = input == null || input.isKeyDown(KeyCodes.KEY_W);
                if (!onlyForward.isEnabled() || forwardPressed) {
                    if (!mc.isSneaking()) {
                        mc.setSprinting(true);
                    }
                }
            }
        });

        // 32. Toggle Sneak
        final Module toggleSneak = mm.register(new Module("toggle_sneak", "Toggle Sneak", "Accessibility sneak key latch that pauses cleanly inside GUI screens", ModuleCategory.MOVEMENT) {
            private final BooleanSetting pauseInGui = addSetting(new BooleanSetting("pause_in_gui", "Pause In Menus", "Release sneak automatically when a GUI screen opens", true));

            @Override
            public void onTick(MinecraftAdapter mc) {
                if (mc == null || !mc.isInWorld()) return;
                if (pauseInGui.isEnabled() && (mc.isClickGuiOpen() || mc.isHudEditorOpen())) {
                    mc.setSneakingKeyHeld(false);
                } else {
                    mc.setSneakingKeyHeld(true);
                }
            }

            @Override
            public void onDisable(MinecraftAdapter mc) {
                if (mc != null) mc.setSneakingKeyHeld(false);
            }
        });

        // 33. Movement Speed HUD
        Module speedHud = mm.register(new Module("movement_speed_hud", "Movement Speed HUD", "Displays real horizontal speed in blocks/sec (m/s)", ModuleCategory.MOVEMENT) {
            private final BooleanSetting showPeak = addSetting(new BooleanSetting("show_peak", "Show Peak Speed", "Append session maximum m/s", true));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                if (showPeak.isEnabled()) {
                    return String.format(Locale.ROOT, "Speed %.2f m/s (Max %.1f)",
                            Double.valueOf(state.getCurrentHorizontalSpeedBps()),
                            Double.valueOf(state.getPeakSpeedBps()));
                }
                return String.format(Locale.ROOT, "Speed %.2f m/s", Double.valueOf(state.getCurrentHorizontalSpeedBps()));
            }
        });
        hm.register(new HudElement("movement_speed_hud", "Speed HUD", speedHud, 0.015f, 0.43f, 152f, 20f));

        // 34. Camera Bobbing Settings
        mm.register(new Module("camera_bobbing", "Camera Bobbing Settings", "Configures view bobbing behavior for steadier crosshair tracking", ModuleCategory.PLAYER) {
            private final BooleanSetting enableWorldBob = addSetting(new BooleanSetting("world_bob", "Enable View Bobbing", "Keep or disable camera bobbing while walking", false));
            private boolean savedBob = true;

            @Override
            public void onEnable(MinecraftAdapter mc) {
                if (mc != null) {
                    savedBob = mc.isViewBobbing();
                    mc.setViewBobbing(enableWorldBob.isEnabled());
                }
            }

            @Override
            public void onTick(MinecraftAdapter mc) {
                if (mc != null) mc.setViewBobbing(enableWorldBob.isEnabled());
            }

            @Override
            public void onDisable(MinecraftAdapter mc) {
                if (mc != null) mc.setViewBobbing(savedBob);
            }
        });

        // 35. FOV Transition Settings
        mm.register(new Module("fov_transition", "FOV Transition Settings", "Controls dynamic FOV scaling intensity during sprint and speed effects", ModuleCategory.PLAYER) {
            private final NumberSetting sprintScale = addSetting(new NumberSetting("sprint_scale", "Dynamic FOV Scale", "Multiplier for dynamic speed FOV", 0.50, 0.0, 1.0, 0.05));
            private final NumberSetting transitionSpeed = addSetting(new NumberSetting("speed", "Transition Speed", "Smoothing speed for FOV changes", 10.0, 2.0, 25.0, 1.0));

            public float getSprintScale() { return sprintScale.getFloat(); }
            public float getTransitionSpeed() { return transitionSpeed.getFloat(); }
        });

        // 36. Sprint Indicator
        Module sprintInd = mm.register(new Module("sprint_indicator", "Sprint Indicator", "Shows current sprint state and whether Toggle Sprint is active", ModuleCategory.HUD, true, KeyCodes.KEY_NONE, false) {
            private final BooleanSetting hideIdle = addSetting(new BooleanSetting("hide_idle", "Hide When Standing", "Only display when sprinting or latched", false));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                boolean sprinting = mc != null && mc.isInWorld() && mc.isSprinting();
                if (toggleSprint.isEnabled()) {
                    return sprinting ? "[Sprint: Toggled · Active]" : "[Sprint: Toggled]";
                }
                if (sprinting) return "[Sprinting]";
                return hideIdle.isEnabled() ? null : "[Sprint: Ready]";
            }
        });
        hm.register(new HudElement("sprint_indicator", "Sprint Indicator", sprintInd, 0.015f, 0.92f, 145f, 20f));

        // 37. Sneak Indicator
        Module sneakInd = mm.register(new Module("sneak_indicator", "Sneak Indicator", "Displays active sneak latch or held crouch indicator", ModuleCategory.HUD) {
            private final BooleanSetting showOnlyActive = addSetting(new BooleanSetting("only_active", "Only When Sneaking", "Hide badge when standing upright", true));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                boolean sneaking = mc != null && mc.isInWorld() && mc.isSneaking();
                if (toggleSneak.isEnabled()) return "[Sneak: Toggled]";
                if (sneaking) return "[Sneaking]";
                return showOnlyActive.isEnabled() ? null : "[Standing]";
            }
        });
        hm.register(new HudElement("sneak_indicator", "Sneak Indicator", sneakInd, 0.015f, 0.86f, 126f, 20f));

        // 38. Direction Indicator
        Module dirInd = mm.register(new Module("direction_indicator", "Direction Indicator", "Displays relative strafe vector vs look direction", ModuleCategory.MOVEMENT) {
            private final BooleanSetting showStrafeLabel = addSetting(new BooleanSetting("strafe_label", "Show Strafe Mode", "Label Forward / Strafe / Backpedal", true));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                boolean w = input != null && input.isKeyDown(KeyCodes.KEY_W);
                boolean s = input != null && input.isKeyDown(KeyCodes.KEY_S);
                boolean a = input != null && input.isKeyDown(KeyCodes.KEY_A);
                boolean d = input != null && input.isKeyDown(KeyCodes.KEY_D);
                String mode = "Idle";
                if (w && a) mode = "Forward-Left (45°)";
                else if (w && d) mode = "Forward-Right (45°)";
                else if (w) mode = "Forward (0°)";
                else if (s) mode = "Backpedal (180°)";
                else if (a) mode = "Strafe Left (-90°)";
                else if (d) mode = "Strafe Right (+90°)";
                return showStrafeLabel.isEnabled() ? ("Vector: " + mode) : mode;
            }
        });
        hm.register(new HudElement("direction_indicator", "Move Vector", dirInd, 0.015f, 0.49f, 148f, 20f));

        // 39. Jump Statistics
        Module jumpMod = mm.register(new Module("jump_statistics", "Jump Statistics", "Counts total jumps and sprint-jumps in the current session", ModuleCategory.PLAYER) {
            private final BooleanSetting showSprintRatio = addSetting(new BooleanSetting("sprint_ratio", "Show Sprint-Jumps", "Include sprint-jump breakdown", true));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                return showSprintRatio.isEnabled()
                        ? ("Jumps: " + state.getTotalJumps() + " (Sprint: " + state.getSprintJumps() + ")")
                        : ("Jumps: " + state.getTotalJumps());
            }
        });
        hm.register(new HudElement("jump_statistics", "Jump Stats", jumpMod, 0.015f, 0.55f, 150f, 20f));

        // 40. Movement Session Tracker
        Module moveTracker = mm.register(new Module("movement_session_tracker", "Movement Session Tracker", "Tracks distance walked, sprinted, and fallen during session", ModuleCategory.PLAYER) {
            private final ModeSetting unit = addSetting(new ModeSetting("unit", "Distance Unit", "Meters or Kilometers", "Meters", "Meters", "Kilometers"));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                double total = state.getWalkMeters() + state.getSprintMeters();
                if ("Kilometers".equals(unit.getValue())) {
                    return String.format(Locale.ROOT, "Dist %.2f km (Sprint %.2f km)", Double.valueOf(total / 1000.0), Double.valueOf(state.getSprintMeters() / 1000.0));
                }
                return String.format(Locale.ROOT, "Dist %.0fm (Walk %.0fm · Sprint %.0fm)",
                        Double.valueOf(total), Double.valueOf(state.getWalkMeters()), Double.valueOf(state.getSprintMeters()));
            }
        });
        hm.register(new HudElement("movement_session_tracker", "Distance Tracker", moveTracker, 0.015f, 0.61f, 190f, 20f));

        // 41. Custom Camera Sensitivity Profiles
        mm.register(new Module("camera_sensitivity_profiles", "Custom Camera Sensitivity Profiles", "Switches between calibrated client mouse sensitivity profiles", ModuleCategory.PLAYER) {
            private final ModeSetting profile = addSetting(new ModeSetting("profile", "Active Profile", "Sensitivity preset", "Standard", "Standard", "Precision Bow", "Fast Arena", "Custom"));
            private final NumberSetting customSens = addSetting(new NumberSetting("custom_sens", "Custom Value", "Sensitivity when Custom is selected", 0.50, 0.05, 1.00, 0.01));
            private double savedSens = 0.5;

            @Override
            public void onEnable(MinecraftAdapter mc) {
                if (mc != null) savedSens = mc.getMouseSensitivity();
            }

            @Override
            public void onTick(MinecraftAdapter mc) {
                if (mc == null) return;
                String p = profile.getValue();
                double target = savedSens;
                if ("Standard".equals(p)) target = 0.50;
                else if ("Precision Bow".equals(p)) target = 0.28;
                else if ("Fast Arena".equals(p)) target = 0.68;
                else if ("Custom".equals(p)) target = customSens.getDouble();

                if (state.isZooming() && state.getCurrentZoomFactor() > 1.05f) {
                    target = target / state.getCurrentZoomFactor();
                }
                mc.setMouseSensitivity(target);
            }

            @Override
            public void onDisable(MinecraftAdapter mc) {
                if (mc != null) mc.setMouseSensitivity(savedSens);
            }
        });

        // 42. Camera Transition Settings
        mm.register(new Module("camera_transition_settings", "Camera Transition Settings", "Configures smooth eye-height and perspective damping parameters", ModuleCategory.PLAYER) {
            private final NumberSetting eyeSmoothing = addSetting(new NumberSetting("eye_smoothing", "Sneak Eye Damping", "Smoothness of crouch camera transition", 14.0, 4.0, 30.0, 1.0));
            private final BooleanSetting smoothPerspective = addSetting(new BooleanSetting("smooth_f5", "Smooth F5 Transition", "Interpolate third-person distance", true));

            public float getEyeSmoothing() { return eyeSmoothing.getFloat(); }
            public boolean isSmoothPerspective() { return smoothPerspective.isEnabled(); }
        });
    }
}
