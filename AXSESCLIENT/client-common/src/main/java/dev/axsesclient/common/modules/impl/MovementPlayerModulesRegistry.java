package dev.axsesclient.common.modules.impl;

import dev.axsesclient.common.hud.HudElement;
import dev.axsesclient.common.hud.HudManager;
import dev.axsesclient.common.i18n.LocalizationManager;
import dev.axsesclient.common.modules.Module;
import dev.axsesclient.common.modules.ModuleCategory;
import dev.axsesclient.common.modules.ModuleManager;
import dev.axsesclient.common.notifications.NotificationManager;
import dev.axsesclient.common.platform.InputAdapter;
import dev.axsesclient.common.platform.KeyCodes;
import dev.axsesclient.common.platform.MinecraftAdapter;
import dev.axsesclient.common.platform.RenderAdapter;
import dev.axsesclient.common.render.FontManager;
import dev.axsesclient.common.render.RenderManager;
import dev.axsesclient.common.settings.BooleanSetting;
import dev.axsesclient.common.settings.ModeSetting;
import dev.axsesclient.common.settings.NumberSetting;
import dev.axsesclient.common.theme.AxsesTheme;
import dev.axsesclient.common.theme.ThemeManager;
import java.util.Locale;

public final class MovementPlayerModulesRegistry {
    private MovementPlayerModulesRegistry() {
    }

    public static void registerAll(
            ModuleManager mm,
            HudManager hm,
            final ClientRuntimeState state,
            final InputAdapter input,
            final LocalizationManager i18n
    ) {
        registerAll(mm, hm, state, input, i18n, null, null, null);
    }

    public static void registerAll(
            ModuleManager mm,
            HudManager hm,
            final ClientRuntimeState state,
            final InputAdapter input,
            final LocalizationManager i18n,
            final NotificationManager notifications,
            final FontManager fonts,
            final ThemeManager themes
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
                boolean ru = i18n != null && i18n.isRussian();
                boolean sprinting = mc != null && mc.isInWorld() && mc.isSprinting();
                if (toggleSprint.isEnabled()) {
                    if (ru) return sprinting ? "Спринт: Активен ⚡" : "Спринт: Авто";
                    return sprinting ? "Sprint: Active ⚡" : "Sprint: Latched";
                }
                if (sprinting) return ru ? "Спринт: Бег ⚡" : "Sprint: Running ⚡";
                return hideIdle.isEnabled() ? null : (ru ? "Спринт: Готов" : "Sprint: Ready");
            }
        });
        hm.register(new HudElement("sprint_indicator", "Sprint Indicator", sprintInd, 0.012f, 0.942f, 136f, 21f));

        // 37. Sneak Indicator
        Module sneakInd = mm.register(new Module("sneak_indicator", "Sneak Indicator", "Displays active sneak latch or held crouch indicator", ModuleCategory.HUD) {
            private final BooleanSetting showOnlyActive = addSetting(new BooleanSetting("only_active", "Only When Sneaking", "Hide badge when standing upright", true));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                boolean ru = i18n != null && i18n.isRussian();
                boolean sneaking = mc != null && mc.isInWorld() && mc.isSneaking();
                if (toggleSneak.isEnabled()) return ru ? "Шифт: Зажат" : "Sneak: Latched";
                if (sneaking) return ru ? "Присед" : "Sneaking";
                return showOnlyActive.isEnabled() ? null : (ru ? "Стоя" : "Standing");
            }
        });
        hm.register(new HudElement("sneak_indicator", "Sneak Indicator", sneakInd, 0.015f, 0.86f, 126f, 20f));

        // 38. Direction Indicator
        Module dirInd = mm.register(new Module("direction_indicator", "Direction Indicator", "Displays relative strafe vector vs look direction", ModuleCategory.MOVEMENT) {
            private final BooleanSetting showStrafeLabel = addSetting(new BooleanSetting("strafe_label", "Show Strafe Mode", "Label Forward / Strafe / Backpedal", true));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                boolean ru = i18n != null && i18n.isRussian();
                boolean w = input != null && input.isKeyDown(KeyCodes.KEY_W);
                boolean s = input != null && input.isKeyDown(KeyCodes.KEY_S);
                boolean a = input != null && input.isKeyDown(KeyCodes.KEY_A);
                boolean d = input != null && input.isKeyDown(KeyCodes.KEY_D);
                String mode = ru ? "Стоп" : "Idle";
                if (w && a) mode = ru ? "Вперёд-Влево (45°)" : "Forward-Left (45°)";
                else if (w && d) mode = ru ? "Вперёд-Вправо (45°)" : "Forward-Right (45°)";
                else if (w) mode = ru ? "Вперёд (0°)" : "Forward (0°)";
                else if (s) mode = ru ? "Назад (180°)" : "Backpedal (180°)";
                else if (a) mode = ru ? "Стрейф Влево (-90°)" : "Strafe Left (-90°)";
                else if (d) mode = ru ? "Стрейф Вправо (+90°)" : "Strafe Right (+90°)";
                return showStrafeLabel.isEnabled() ? ((ru ? "Вектор: " : "Vector: ") + mode) : mode;
            }
        });
        hm.register(new HudElement("direction_indicator", "Move Vector", dirInd, 0.015f, 0.49f, 148f, 20f));

        // 39. Jump Statistics
        Module jumpMod = mm.register(new Module("jump_statistics", "Jump Statistics", "Counts total jumps and sprint-jumps in the current session", ModuleCategory.PLAYER) {
            private final BooleanSetting showSprintRatio = addSetting(new BooleanSetting("sprint_ratio", "Show Sprint-Jumps", "Include sprint-jump breakdown", true));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                boolean ru = i18n != null && i18n.isRussian();
                if (ru) {
                    return showSprintRatio.isEnabled()
                            ? ("Прыжки: " + state.getTotalJumps() + " (Спринт: " + state.getSprintJumps() + ")")
                            : ("Прыжки: " + state.getTotalJumps());
                }
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
                boolean ru = i18n != null && i18n.isRussian();
                double total = state.getWalkMeters() + state.getSprintMeters();
                if ("Kilometers".equals(unit.getValue())) {
                    return ru
                            ? String.format(Locale.ROOT, "Путь %.2f км (Бег %.2f км)", Double.valueOf(total / 1000.0), Double.valueOf(state.getSprintMeters() / 1000.0))
                            : String.format(Locale.ROOT, "Dist %.2f km (Sprint %.2f km)", Double.valueOf(total / 1000.0), Double.valueOf(state.getSprintMeters() / 1000.0));
                }
                return ru
                        ? String.format(Locale.ROOT, "Путь %.0fм (Шаг %.0fм · Бег %.0fм)",
                                Double.valueOf(total), Double.valueOf(state.getWalkMeters()), Double.valueOf(state.getSprintMeters()))
                        : String.format(Locale.ROOT, "Dist %.0fm (Walk %.0fm · Sprint %.0fm)",
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

        // 105. Auto Death Guard / Respawn Tracker (Авто-Возрождение и координаты смерти)
        mm.register(new Module("auto_respawn", "Auto Death Guard", "Automatically saves death coordinates to clipboard and alerts on death", ModuleCategory.PLAYER) {
            private final BooleanSetting copyCoords = addSetting(new BooleanSetting("copy_coords", "Copy Death XYZ", "Copy exact death coordinates to clipboard", true));
            private boolean wasDead = false;

            @Override
            public void onTick(MinecraftAdapter mc) {
                if (mc == null || !mc.isInWorld()) {
                    wasDead = false;
                    return;
                }
                boolean dead = mc.getHealth() <= 0.0f;
                if (dead && !wasDead) {
                    wasDead = true;
                    String coords = String.format(Locale.ROOT, "%.0f %.0f %.0f (%s)",
                            Double.valueOf(mc.getPlayerX()), Double.valueOf(mc.getPlayerY()), Double.valueOf(mc.getPlayerZ()), mc.getDimensionName());
                    if (copyCoords.isEnabled()) {
                        mc.setClipboard(coords);
                    }
                    if (notifications != null) {
                        boolean ru = i18n != null && i18n.isRussian();
                        notifications.push(
                                ru ? "Точка смерти сохранена" : "Death Coords Saved",
                                coords,
                                NotificationManager.Severity.WARNING
                        );
                    }
                } else if (!dead) {
                    wasDead = false;
                }
            }
        });

        // 106. Low HP Alert (Тревога низкого ХП) — Pulsing Crimson Vignette & Warning Pill
        mm.register(new Module("low_hp_alert", "Low HP Alert", "Pulsing crimson edge vignette and warning pill when health drops critically low", ModuleCategory.PLAYER, true, KeyCodes.KEY_NONE, false) {
            private final NumberSetting threshold = addSetting(new NumberSetting("threshold", "HP Threshold", "Trigger alert at or below this HP (20 = 10 hearts)", 7.0, 2.0, 14.0, 1.0));
            private final BooleanSetting showBanner = addSetting(new BooleanSetting("show_banner", "Warning Pill", "Show critical HP pill above hotbar", true));

            @Override
            public void onRender(RenderAdapter r, MinecraftAdapter mc, float dt) {
                if (mc == null || !mc.isInWorld() || mc.isClickGuiOpen()) return;
                float hp = mc.getHealth();
                if (hp <= 0.0f || hp > threshold.getFloat()) return;

                int sw = r.getScaledWidth();
                int sh = r.getScaledHeight();
                float pulse = (float) (0.45 + 0.55 * Math.abs(Math.sin((System.currentTimeMillis() % 1200L) / 1200.0 * Math.PI * 2.0)));
                int edgeTop = AxsesTheme.withAlpha(0xFFFF3864, 0.24f * pulse);
                r.fillGradientVertical(0, 0, sw, 26f, edgeTop, 0x00FF3864);
                r.fillGradientVertical(0, sh - 26f, sw, 26f, 0x00FF3864, edgeTop);

                if (showBanner.isEnabled() && fonts != null && themes != null) {
                    boolean ru = i18n != null && i18n.isRussian();
                    String msg = String.format(Locale.ROOT, ru ? "⚠ НИЗКОЕ ХП: %.1f ❤" : "⚠ LOW HP: %.1f ❤", Float.valueOf(hp));
                    float bw = fonts.width(r, msg, 0.90f) + 18.0f;
                    float bx = (sw - bw) * 0.5f;
                    float by = sh - 66.0f;
                    r.fillRoundedRect(bx, by, bw, 18.0f, 9.0f, 0xE01A0610);
                    r.drawRoundedOutline(bx, by, bw, 18.0f, 9.0f, 1.2f, AxsesTheme.withAlpha(0xFFFF3864, 0.70f + 0.30f * pulse));
                    fonts.drawCentered(r, msg, sw * 0.5f, by + 5.0f, 0xFFFF8FA3, 0.90f);
                }
            }
        });
    }
}
