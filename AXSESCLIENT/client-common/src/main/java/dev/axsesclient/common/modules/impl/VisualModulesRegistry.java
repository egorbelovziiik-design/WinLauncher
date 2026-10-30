package dev.axsesclient.common.modules.impl;

import dev.axsesclient.common.animation.AnimatedFloat;
import dev.axsesclient.common.hud.HudElement;
import dev.axsesclient.common.hud.HudManager;
import dev.axsesclient.common.modules.Module;
import dev.axsesclient.common.modules.ModuleCategory;
import dev.axsesclient.common.modules.ModuleManager;
import dev.axsesclient.common.perf.PerformanceProfiler;
import dev.axsesclient.common.platform.InputAdapter;
import dev.axsesclient.common.platform.ItemInfo;
import dev.axsesclient.common.platform.KeyCodes;
import dev.axsesclient.common.platform.MinecraftAdapter;
import dev.axsesclient.common.platform.RenderAdapter;
import dev.axsesclient.common.platform.StatusEffectInfo;
import dev.axsesclient.common.render.FontManager;
import dev.axsesclient.common.render.RenderManager;
import dev.axsesclient.common.settings.BooleanSetting;
import dev.axsesclient.common.settings.ColorSetting;
import dev.axsesclient.common.settings.KeybindSetting;
import dev.axsesclient.common.settings.ModeSetting;
import dev.axsesclient.common.settings.NumberSetting;
import dev.axsesclient.common.settings.StringSetting;
import dev.axsesclient.common.theme.AxsesTheme;
import dev.axsesclient.common.theme.ThemeManager;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class VisualModulesRegistry {
    private VisualModulesRegistry() {
    }

    public static void registerAll(
            ModuleManager mm,
            HudManager hm,
            final ClientRuntimeState state,
            final PerformanceProfiler profiler,
            final InputAdapter input,
            final ThemeManager themes,
            final FontManager fonts,
            final RenderManager renderManager
    ) {
        // 01. FullBright
        mm.register(new Module("fullbright", "FullBright", "Elevates ambient gamma / lightmap brightness in dark environments", ModuleCategory.RENDER) {
            private final NumberSetting strength = addSetting(new NumberSetting("strength", "Brightness", "Target gamma multiplier", 12.0, 2.0, 16.0, 0.5));
            private final ModeSetting mode = addSetting(new ModeSetting("mode", "Mode", "Brightness boost method", "Gamma", "Gamma", "Lightmap"));
            private double savedGamma = 1.0;

            @Override
            public void onEnable(MinecraftAdapter mc) {
                if (mc != null) {
                    savedGamma = mc.getGamma();
                    mc.setGamma(strength.getDouble());
                }
            }

            @Override
            public void onTick(MinecraftAdapter mc) {
                if (mc != null && "Gamma".equals(mode.getValue())) {
                    mc.setGamma(strength.getDouble());
                }
            }

            @Override
            public void onDisable(MinecraftAdapter mc) {
                if (mc != null) {
                    mc.setGamma(Math.max(0.0, Math.min(1.0, savedGamma)));
                }
            }
        });

        // 02. Smooth Zoom
        mm.register(new Module("smooth_zoom", "Smooth Zoom", "Hold zoom key for smooth damped optic magnification", ModuleCategory.RENDER, true, KeyCodes.KEY_NONE, false) {
            private final KeybindSetting holdKey = addSetting(new KeybindSetting("hold_key", "Zoom Hold Key", "Key held to zoom camera", KeyCodes.KEY_C));
            private final NumberSetting zoomLevel = addSetting(new NumberSetting("zoom_level", "Zoom Factor", "Magnification factor", 4.0, 1.5, 10.0, 0.5));
            private final NumberSetting smoothness = addSetting(new NumberSetting("smoothness", "Smooth Speed", "Damping speed", 12.0, 3.0, 25.0, 1.0));
            private final BooleanSetting scaleSens = addSetting(new BooleanSetting("scale_sens", "Scale Sensitivity", "Reduce mouse sensitivity proportionally while zoomed", true));

            @Override
            public void onRender(RenderAdapter renderer, MinecraftAdapter mc, float deltaSeconds) {
                boolean held = input != null && holdKey.isBound() && input.isKeyDown(holdKey.getKeyCode()) && !mc.isClickGuiOpen();
                state.setZooming(held);
                float target = held ? zoomLevel.getFloat() : 1.0f;
                float next = AnimatedFloat.damp(state.getCurrentZoomFactor(), target, smoothness.getFloat(), deltaSeconds, 1.0f, false);
                state.setCurrentZoomFactor(next);
            }

            public boolean isScaleSens() { return scaleSens.isEnabled(); }

            @Override
            public void onDisable(MinecraftAdapter mc) {
                state.setZooming(false);
                state.setCurrentZoomFactor(1.0f);
            }
        });

        // 03. Custom Crosshair
        mm.register(new Module("custom_crosshair", "Custom Crosshair", "Customizable precision crosshair with dynamic movement feedback", ModuleCategory.RENDER) {
            private final ModeSetting style = addSetting(new ModeSetting("style", "Style", "Crosshair geometry", "Cross", "Cross", "Dot", "Box", "PlusDot"));
            private final NumberSetting size = addSetting(new NumberSetting("size", "Size", "Arm length in pixels", 5.0, 2.0, 14.0, 0.5));
            private final NumberSetting gap = addSetting(new NumberSetting("gap", "Center Gap", "Gap radius from center", 2.5, 0.0, 10.0, 0.5));
            private final NumberSetting thickness = addSetting(new NumberSetting("thickness", "Thickness", "Stroke thickness", 1.5, 1.0, 4.0, 0.5));
            private final BooleanSetting dynamic = addSetting(new BooleanSetting("dynamic", "Dynamic Spread", "Expand gap when sprinting or airborne", true));
            private final ColorSetting color = addSetting(new ColorSetting("color", "Color", "Crosshair color", 0xFF00D9FF));
            private final BooleanSetting outline = addSetting(new BooleanSetting("outline", "Dark Outline", "Draw 1px contrast border", true));

            @Override
            public void onRender(RenderAdapter r, MinecraftAdapter mc, float dt) {
                if (mc == null || !mc.isInWorld() || mc.isClickGuiOpen() || mc.isHudEditorOpen()) return;
                float cx = r.getScaledWidth() * 0.5f;
                float cy = r.getScaledHeight() * 0.5f;
                float g = gap.getFloat() + (dynamic.isEnabled() && (mc.isSprinting() || !mc.isOnGround()) ? 2.5f : 0.0f);
                float sz = size.getFloat();
                float th = thickness.getFloat();
                int col = themes.filterColor(color.getArgb());
                String st = style.getValue();

                if ("Dot".equals(st) || "PlusDot".equals(st)) {
                    if (outline.isEnabled()) r.fillRect(cx - th * 0.5f - 1f, cy - th * 0.5f - 1f, th + 2f, th + 2f, 0xCC000000);
                    r.fillRect(cx - th * 0.5f, cy - th * 0.5f, th, th, col);
                }
                if ("Cross".equals(st) || "PlusDot".equals(st)) {
                    if (outline.isEnabled()) {
                        r.fillRect(cx - g - sz - 1f, cy - th * 0.5f - 1f, sz + 2f, th + 2f, 0xAA000000);
                        r.fillRect(cx + g - 1f, cy - th * 0.5f - 1f, sz + 2f, th + 2f, 0xAA000000);
                        r.fillRect(cx - th * 0.5f - 1f, cy - g - sz - 1f, th + 2f, sz + 2f, 0xAA000000);
                        r.fillRect(cx - th * 0.5f - 1f, cy + g - 1f, th + 2f, sz + 2f, 0xAA000000);
                    }
                    r.fillRect(cx - g - sz, cy - th * 0.5f, sz, th, col);
                    r.fillRect(cx + g, cy - th * 0.5f, sz, th, col);
                    r.fillRect(cx - th * 0.5f, cy - g - sz, th, sz, col);
                    r.fillRect(cx - th * 0.5f, cy + g, th, sz, col);
                } else if ("Box".equals(st)) {
                    r.drawRoundedOutline(cx - sz, cy - sz, sz * 2f, sz * 2f, 2f, th, col);
                }
            }
        });

        // 04. FreeLook
        mm.register(new Module("freelook", "FreeLook", "Client-side 360-degree third-person camera orbit without altering server aim", ModuleCategory.RENDER) {
            private final KeybindSetting holdKey = addSetting(new KeybindSetting("hold_key", "Orbit Hold Key", "Hold to orbit camera", KeyCodes.KEY_LEFT_ALT));
            private final BooleanSetting autoThirdPerson = addSetting(new BooleanSetting("auto_third_person", "Auto Third Person", "Switch perspective while held", true));
            private int savedPerspective = 0;
            private boolean activeHold = false;

            @Override
            public void onTick(MinecraftAdapter mc) {
                if (mc == null || !mc.isInWorld()) return;
                boolean held = input != null && holdKey.isBound() && input.isKeyDown(holdKey.getKeyCode()) && !mc.isClickGuiOpen();
                if (held && !activeHold) {
                    activeHold = true;
                    savedPerspective = mc.getPerspective();
                    state.setFreeLookYaw(mc.getPlayerYaw());
                    state.setFreeLookPitch(mc.getPlayerPitch());
                    state.setFreeLooking(true);
                    if (autoThirdPerson.isEnabled()) {
                        mc.setPerspective(1);
                    }
                } else if (!held && activeHold) {
                    activeHold = false;
                    state.setFreeLooking(false);
                    if (autoThirdPerson.isEnabled()) {
                        mc.setPerspective(savedPerspective);
                    }
                }
            }

            @Override
            public void onDisable(MinecraftAdapter mc) {
                if (activeHold && mc != null && autoThirdPerson.isEnabled()) {
                    mc.setPerspective(savedPerspective);
                }
                activeHold = false;
                state.setFreeLooking(false);
            }
        });

        // 05. FPS Counter
        Module fpsMod = mm.register(new Module("fps_counter", "FPS Counter", "Displays real-time framerate and average frame time", ModuleCategory.HUD, true, KeyCodes.KEY_NONE, false) {
            private final BooleanSetting showFrameTime = addSetting(new BooleanSetting("show_ms", "Show Frame Time", "Include millisecond frame duration", true));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                int fps = mc == null ? 60 : mc.getFps();
                if (showFrameTime.isEnabled()) {
                    return String.format(Locale.ROOT, "FPS %d (%.1fms)", Integer.valueOf(fps), Float.valueOf(profiler.getAverageFrameTimeMs()));
                }
                return "FPS " + fps;
            }
        });
        hm.register(new HudElement("fps_counter", "FPS Counter", fpsMod, 0.015f, 0.02f, 118f, 20f));

        // 06. Coordinates HUD
        Module coordsMod = mm.register(new Module("coordinates_hud", "Coordinates HUD", "Shows X, Y, Z coordinates with configurable precision", ModuleCategory.HUD, true, KeyCodes.KEY_NONE, false) {
            private final NumberSetting decimals = addSetting(new NumberSetting("decimals", "Decimals", "Coordinate decimal places", 1.0, 0.0, 2.0, 1.0));
            private final BooleanSetting showNether = addSetting(new BooleanSetting("show_nether", "Nether Equivalent", "Show scaled Overworld/Nether pair", false));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                if (mc == null || !mc.isInWorld()) return "XYZ: 0, 64, 0";
                int d = decimals.getInt();
                String fmt = d == 0 ? "XYZ %.0f, %.0f, %.0f" : (d == 1 ? "XYZ %.1f, %.1f, %.1f" : "XYZ %.2f, %.2f, %.2f");
                String base = String.format(Locale.ROOT, fmt, Double.valueOf(mc.getPlayerX()), Double.valueOf(mc.getPlayerY()), Double.valueOf(mc.getPlayerZ()));
                if (showNether.isEnabled()) {
                    boolean isNether = mc.getDimensionId().contains("nether");
                    double factor = isNether ? 8.0 : 0.125;
                    base += String.format(Locale.ROOT, " [%.0f, %.0f]", Double.valueOf(mc.getPlayerX() * factor), Double.valueOf(mc.getPlayerZ() * factor));
                }
                return base;
            }
        });
        hm.register(new HudElement("coordinates_hud", "Coordinates", coordsMod, 0.015f, 0.075f, 156f, 20f));

        // 07. Armor HUD
        Module armorMod = mm.register(new Module("armor_hud", "Armor HUD", "Displays equipped armor durability percentages", ModuleCategory.HUD, true, KeyCodes.KEY_NONE, false) {
            private final BooleanSetting showPercent = addSetting(new BooleanSetting("show_percent", "Show Percentage", "Format remaining durability as %", true));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                if (mc == null || !mc.isInWorld()) return "Armor: 100% | 100% | 100% | 100%";
                List<ItemInfo> armor = mc.getArmorItems();
                if (armor == null || armor.isEmpty()) return "Armor: Empty";
                StringBuilder sb = new StringBuilder("Armor ");
                int nonEmpty = 0;
                for (ItemInfo item : armor) {
                    if (item != null && !item.isEmpty()) {
                        if (nonEmpty > 0) sb.append(" · ");
                        if (item.isDamageable()) {
                            int val = showPercent.isEnabled()
                                    ? Math.round(item.getDurabilityRatio() * 100f)
                                    : item.getRemainingDurability();
                            sb.append(val).append(showPercent.isEnabled() ? "%" : "");
                        } else {
                            sb.append("OK");
                        }
                        nonEmpty++;
                    }
                }
                return nonEmpty == 0 ? "Armor: None" : sb.toString();
            }
        });
        hm.register(new HudElement("armor_hud", "Armor HUD", armorMod, 0.50f, 0.88f, 165f, 20f));

        // 08. Potion HUD
        Module potionMod = mm.register(new Module("potion_hud", "Potion HUD", "Lists active status effects and countdown timers", ModuleCategory.HUD) {
            private final BooleanSetting showAmplifier = addSetting(new BooleanSetting("show_amp", "Show Amplifier", "Show Roman numeral potency", true));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                if (mc == null || !mc.isInWorld()) return "Effects: Speed II (01:24)";
                List<StatusEffectInfo> fx = mc.getActiveEffects();
                if (fx == null || fx.isEmpty()) return null;
                StatusEffectInfo top = fx.get(0);
                String amp = showAmplifier.isEnabled() ? " " + top.getRomanAmplifier() : "";
                String suffix = fx.size() > 1 ? " (+" + (fx.size() - 1) + ")" : "";
                return top.getName() + amp + " " + top.getFormattedDuration() + suffix;
            }
        });
        hm.register(new HudElement("potion_hud", "Potion HUD", potionMod, 0.985f, 0.14f, 150f, 20f));

        // 09. Keystrokes
        Module keystrokesMod = mm.register(new Module("keystrokes", "Keystrokes", "Visualizes live W/A/S/D, Space, and mouse button inputs", ModuleCategory.HUD, true, KeyCodes.KEY_NONE, false) {
            private final BooleanSetting showCps = addSetting(new BooleanSetting("show_cps", "Include CPS", "Show LMB/RMB CPS inside Keystrokes", true));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                boolean w = input != null && input.isKeyDown(KeyCodes.KEY_W);
                boolean a = input != null && input.isKeyDown(KeyCodes.KEY_A);
                boolean s = input != null && input.isKeyDown(KeyCodes.KEY_S);
                boolean d = input != null && input.isKeyDown(KeyCodes.KEY_D);
                String keys = (w ? "[W]" : " W ") + (a ? "[A]" : " A ") + (s ? "[S]" : " S ") + (d ? "[D]" : " D ");
                if (showCps.isEnabled()) {
                    keys += " | " + state.getLeftCps() + "/" + state.getRightCps() + " CPS";
                }
                return keys;
            }
        });
        hm.register(new HudElement("keystrokes", "Keystrokes", keystrokesMod, 0.015f, 0.19f, 162f, 22f));

        // 10. CPS Counter
        Module cpsMod = mm.register(new Module("cps_counter", "CPS Counter", "Real-time 1-second sliding window clicks-per-second counter", ModuleCategory.HUD, true, KeyCodes.KEY_NONE, false) {
            private final BooleanSetting showRight = addSetting(new BooleanSetting("show_right", "Show Right CPS", "Display RMB CPS alongside LMB", true));
            private final BooleanSetting showPeak = addSetting(new BooleanSetting("show_peak", "Show Session Peak", "Append session peak CPS", false));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                String txt = showRight.isEnabled()
                        ? ("CPS " + state.getLeftCps() + " | " + state.getRightCps())
                        : ("CPS " + state.getLeftCps());
                if (showPeak.isEnabled()) {
                    txt += " (Max " + state.getPeakLeftCps() + ")";
                }
                return txt;
            }
        });
        hm.register(new HudElement("cps_counter", "CPS Counter", cpsMod, 0.015f, 0.13f, 110f, 20f));

        // 11. Custom FOV
        mm.register(new Module("custom_fov", "Custom FOV", "Overrides base camera field of view cleanly", ModuleCategory.RENDER) {
            private final NumberSetting fov = addSetting(new NumberSetting("fov", "Base FOV", "Field of view in degrees", 95.0, 30.0, 120.0, 1.0));
            private double savedFov = 70.0;

            @Override
            public void onEnable(MinecraftAdapter mc) {
                if (mc != null) {
                    savedFov = mc.getBaseFov();
                    mc.setBaseFov(fov.getDouble());
                }
            }

            @Override
            public void onTick(MinecraftAdapter mc) {
                if (mc != null && !state.isZooming()) {
                    mc.setBaseFov(fov.getDouble());
                }
            }

            @Override
            public void onDisable(MinecraftAdapter mc) {
                if (mc != null) mc.setBaseFov(savedFov);
            }
        });

        // 12. Direction HUD
        Module dirMod = mm.register(new Module("direction_hud", "Direction HUD", "Displays cardinal heading and exact camera yaw/pitch", ModuleCategory.HUD) {
            private final BooleanSetting showAngles = addSetting(new BooleanSetting("show_angles", "Show Yaw/Pitch", "Include numeric degrees", true));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                float yaw = mc != null && mc.isInWorld() ? mc.getPlayerYaw() : 0f;
                float norm = ((yaw % 360f) + 360f) % 360f;
                String[] dirs = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
                String card = dirs[Math.round(norm / 45f) % 8];
                return showAngles.isEnabled()
                        ? String.format(Locale.ROOT, "Dir %s (%.0f°)", card, Float.valueOf(norm))
                        : ("Dir " + card);
            }
        });
        hm.register(new HudElement("direction_hud", "Direction HUD", dirMod, 0.015f, 0.25f, 116f, 20f));

        // 13. Biome Display
        Module biomeMod = mm.register(new Module("biome_display", "Biome Display", "Displays formatted biome name at current location", ModuleCategory.HUD) {
            private final BooleanSetting showDim = addSetting(new BooleanSetting("show_dim", "Show Dimension", "Append current dimension tag", false));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                String b = mc != null && mc.isInWorld() ? mc.getBiomeName() : "Plains";
                if (showDim.isEnabled() && mc != null && mc.isInWorld()) {
                    b += " [" + mc.getDimensionId().replace("minecraft:", "") + "]";
                }
                return "Biome: " + b;
            }
        });
        hm.register(new HudElement("biome_display", "Biome Display", biomeMod, 0.015f, 0.31f, 145f, 20f));

        // 14. Ping Display
        Module pingMod = mm.register(new Module("ping_display", "Ping Display", "Shows network latency in milliseconds", ModuleCategory.HUD) {
            private final BooleanSetting qualityBadge = addSetting(new BooleanSetting("quality", "Quality Label", "Append connection quality rating", true));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                if (mc == null || !mc.isInWorld() || mc.isSingleplayer()) {
                    return "Ping: 0ms (Local)";
                }
                int ms = mc.getPingMs();
                String q = ms < 60 ? "Good" : (ms < 140 ? "Fair" : "High");
                return qualityBadge.isEnabled() ? ("Ping: " + ms + "ms (" + q + ")") : ("Ping: " + ms + "ms");
            }
        });
        hm.register(new HudElement("ping_display", "Ping Display", pingMod, 0.985f, 0.02f, 125f, 20f));

        // 15. Memory Display
        Module memMod = mm.register(new Module("memory_display", "Memory Display", "Displays JVM heap usage and percentage", ModuleCategory.HUD) {
            private final BooleanSetting showMb = addSetting(new BooleanSetting("show_mb", "Show Megabytes", "Include MB values alongside %", true));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                long used = profiler.getUsedMemoryMb();
                long max = Math.max(1L, profiler.getMaxMemoryMb());
                long pct = (used * 100L) / max;
                return showMb.isEnabled()
                        ? ("RAM " + pct + "% (" + used + "/" + max + "MB)")
                        : ("RAM " + pct + "%");
            }
        });
        hm.register(new HudElement("memory_display", "Memory Display", memMod, 0.985f, 0.08f, 152f, 20f));

        // 16. Session Timer
        Module sessionTimerMod = mm.register(new Module("session_timer", "Session Timer", "Tracks total elapsed client session playtime", ModuleCategory.HUD) {
            private final BooleanSetting prefixLabel = addSetting(new BooleanSetting("label", "Show Prefix", "Prefix timer with Session:", true));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                String t = ClientRuntimeState.formatHhMmSs(state.getSessionElapsedSeconds());
                return prefixLabel.isEnabled() ? "Session " + t : t;
            }
        });
        hm.register(new HudElement("session_timer", "Session Timer", sessionTimerMod, 0.985f, 0.20f, 126f, 20f));

        // 17. Durability HUD
        Module durMod = mm.register(new Module("durability_hud", "Durability HUD", "Displays held item remaining uses and percentage", ModuleCategory.HUD) {
            private final BooleanSetting includeOffhand = addSetting(new BooleanSetting("offhand", "Fallback Offhand", "Show offhand item if main hand is not damageable", true));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                if (mc == null || !mc.isInWorld()) return "Tool: 1561/1561 (100%)";
                ItemInfo item = mc.getMainHandItem();
                if ((item == null || !item.isDamageable()) && includeOffhand.isEnabled()) {
                    item = mc.getOffHandItem();
                }
                if (item == null || item.isEmpty() || !item.isDamageable()) return null;
                return String.format(Locale.ROOT, "%s: %d/%d (%.0f%%)",
                        item.getDisplayName(),
                        Integer.valueOf(item.getRemainingDurability()),
                        Integer.valueOf(item.getMaxDamage()),
                        Float.valueOf(item.getDurabilityRatio() * 100f));
            }
        });
        hm.register(new HudElement("durability_hud", "Durability HUD", durMod, 0.50f, 0.82f, 170f, 20f));

        // 18. Inventory Preview
        Module invPreviewMod = mm.register(new Module("inventory_preview", "Inventory Preview", "Shows occupied inventory slot summary on HUD", ModuleCategory.HUD) {
            private final BooleanSetting showFreeSlots = addSetting(new BooleanSetting("free_slots", "Show Free Slots", "Display empty slot count", true));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                if (mc == null || !mc.isInWorld()) return "Inventory: 12/36 used (24 free)";
                List<ItemInfo> inv = mc.getInventoryItems();
                int used = 0;
                int total = inv == null || inv.isEmpty() ? 36 : inv.size();
                if (inv != null) {
                    for (ItemInfo i : inv) {
                        if (i != null && !i.isEmpty()) used++;
                    }
                }
                return showFreeSlots.isEnabled()
                        ? ("Inv: " + used + "/" + total + " (" + Math.max(0, total - used) + " free)")
                        : ("Inv: " + used + "/" + total);
            }
        });
        hm.register(new HudElement("inventory_preview", "Inventory Preview", invPreviewMod, 0.985f, 0.26f, 154f, 20f));

        // 19. Custom Hotbar
        mm.register(new Module("custom_hotbar", "Custom Hotbar", "Adds smooth animated Neo-Dark highlight frame around active hotbar slot", ModuleCategory.RENDER) {
            private final NumberSetting slideSpeed = addSetting(new NumberSetting("slide_speed", "Slide Smoothness", "Slot transition smoothness", 16.0, 4.0, 30.0, 1.0));
            private final ColorSetting accent = addSetting(new ColorSetting("accent", "Highlight Color", "Selected slot frame color", 0xFF9060FF));
            private float animatedSlotX = -1f;

            @Override
            public void onRender(RenderAdapter r, MinecraftAdapter mc, float dt) {
                if (mc == null || !mc.isInWorld()) return;
                int slot = Math.max(0, Math.min(8, mc.getSelectedHotbarSlot()));
                float barLeft = r.getScaledWidth() * 0.5f - 91.0f;
                float targetX = barLeft + slot * 20.0f;
                if (animatedSlotX < 0f) animatedSlotX = targetX;
                animatedSlotX = AnimatedFloat.damp(animatedSlotX, targetX, slideSpeed.getFloat(), dt, 1.0f, false);
                float y = r.getScaledHeight() - 22.0f;
                r.drawRoundedOutline(animatedSlotX - 1f, y - 1f, 22f, 22f, 4f, 1.5f, themes.filterColor(accent.getArgb()));
            }
        });

        // 20. Custom Scoreboard
        mm.register(new Module("custom_scoreboard", "Custom Scoreboard", "Styles scoreboard backdrop and controls red score numbers", ModuleCategory.RENDER) {
            private final BooleanSetting hideNumbers = addSetting(new BooleanSetting("hide_numbers", "Hide Red Numbers", "Suppress right-column score integers", true));
            private final NumberSetting bgAlpha = addSetting(new NumberSetting("bg_alpha", "Backdrop Opacity", "Scoreboard background opacity", 0.70, 0.0, 1.0, 0.05));
            private final BooleanSetting hideScoreboard = addSetting(new BooleanSetting("hide_all", "Hide Scoreboard", "Completely hide sidebar scoreboard", false));

            public boolean isHideNumbers() { return hideNumbers.isEnabled(); }
            public float getBgAlpha() { return bgAlpha.getFloat(); }
            public boolean isHideScoreboard() { return hideScoreboard.isEnabled(); }
        });

        // 21. Custom Boss Bar
        mm.register(new Module("custom_boss_bar", "Custom Boss Bar", "Customizes boss bar scale, accent frame, or visibility", ModuleCategory.RENDER) {
            private final BooleanSetting accentFrame = addSetting(new BooleanSetting("accent_frame", "Neo-Dark Accent", "Render subtle accent header bar", true));
            private final BooleanSetting hideBossBar = addSetting(new BooleanSetting("hide_boss_bar", "Hide Boss Bar", "Suppress boss bar overlay", false));

            @Override
            public void onRender(RenderAdapter r, MinecraftAdapter mc, float dt) {
                if (mc != null && mc.isInWorld() && accentFrame.isEnabled() && !hideBossBar.isEnabled()) {
                    float cx = r.getScaledWidth() * 0.5f;
                    r.fillRect(cx - 91f, 10f, 182f, 1.0f, AxsesTheme.withAlpha(themes.getActiveTheme().getPrimaryColor(), 0.35f));
                }
            }
        });

        // 22. Custom Chat Background
        mm.register(new Module("custom_chat_bg", "Custom Chat Background", "Applies Neo-Dark rounded styling and configurable opacity to chat area", ModuleCategory.RENDER) {
            private final NumberSetting opacity = addSetting(new NumberSetting("opacity", "Chat Opacity", "Backdrop opacity multiplier", 0.75, 0.0, 1.0, 0.05));
            private final BooleanSetting accentBorder = addSetting(new BooleanSetting("accent_border", "Left Accent Strip", "Draw Neo-Dark primary strip beside chat", true));

            @Override
            public void onRender(RenderAdapter r, MinecraftAdapter mc, float dt) {
                if (mc != null && mc.isInWorld() && accentBorder.isEnabled() && opacity.getFloat() > 0.05f) {
                    int col = AxsesTheme.withAlpha(themes.getActiveTheme().getPrimaryColor(), opacity.getFloat() * 0.45f);
                    r.fillRect(2f, r.getScaledHeight() - 48f, 2f, 16f, col);
                }
            }
        });

        // 23. Custom Tooltip Style
        mm.register(new Module("custom_tooltip_style", "Custom Tooltip Style", "Configures Neo-Dark gradient border and durability info on item tooltips", ModuleCategory.RENDER) {
            private final ColorSetting borderTop = addSetting(new ColorSetting("border_top", "Top Border", "Top tooltip border color", 0xFF9060FF));
            private final ColorSetting borderBottom = addSetting(new ColorSetting("border_bottom", "Bottom Border", "Bottom tooltip border color", 0xFF00D9FF));
            private final BooleanSetting showItemId = addSetting(new BooleanSetting("show_id", "Show Item ID", "Display namespace identifier in held HUD", true));

            public int getTopColor() { return borderTop.getArgb(); }
            public int getBottomColor() { return borderBottom.getArgb(); }
            public boolean isShowItemId() { return showItemId.isEnabled(); }
        });

        // 24. HUD Background Blur
        mm.register(new Module("hud_background_blur", "HUD Background Blur", "Controls frosted dark glass backdrop intensity across HUD and ClickGUI", ModuleCategory.RENDER, true, KeyCodes.KEY_NONE, false) {
            private final NumberSetting strength = addSetting(new NumberSetting("strength", "Frost Intensity", "Backdrop frost layer opacity", 0.65, 0.10, 1.0, 0.05));

            @Override
            public void onTick(MinecraftAdapter mc) {
                renderManager.setBlurBackdropStrength(strength.getFloat());
            }
        });

        // 25. Screenshot Overlay
        mm.register(new Module("screenshot_overlay", "Screenshot Overlay", "Displays an animated preview banner whenever a screenshot is saved", ModuleCategory.RENDER, true, KeyCodes.KEY_NONE, false) {
            private final NumberSetting displaySec = addSetting(new NumberSetting("duration", "Banner Duration (s)", "Seconds to keep screenshot banner visible", 4.0, 1.5, 10.0, 0.5));

            @Override
            public void onRender(RenderAdapter r, MinecraftAdapter mc, float dt) {
                long elapsed = System.currentTimeMillis() - state.getLastScreenshotToastMs();
                long durMs = (long) (displaySec.getDouble() * 1000.0);
                if (state.getLastScreenshotToastFile().isEmpty() || elapsed > durMs) return;
                AxsesTheme t = themes.getActiveTheme();
                float w = 210f;
                float h = 28f;
                float x = (r.getScaledWidth() - w) * 0.5f;
                float y = 26f;
                r.fillRoundedRect(x, y, w, h, 7f, AxsesTheme.withAlpha(t.getSurfaceColor(), 0.94f));
                r.drawRoundedOutline(x, y, w, h, 7f, 1.2f, t.getAccentColor());
                fonts.drawCentered(r, "Saved Screenshot: " + state.getLastScreenshotToastFile(), x + w * 0.5f, y + 9f, t.getTextColor(), 0.85f);
            }
        });

        // 26. Custom Item Counter
        Module itemCounterMod = mm.register(new Module("custom_item_counter", "Custom Item Counter", "Counts total matching items across player inventory", ModuleCategory.HUD) {
            private final StringSetting keyword = addSetting(new StringSetting("item_keyword", "Item Keyword", "Substring of item ID or name to count", "arrow", 32));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                String kw = keyword.getValue().trim().toLowerCase(Locale.ROOT);
                if (kw.isEmpty()) kw = "arrow";
                int total = 0;
                if (mc != null && mc.isInWorld()) {
                    for (ItemInfo i : mc.getInventoryItems()) {
                        if (i != null && !i.isEmpty()) {
                            if (i.getItemId().toLowerCase(Locale.ROOT).contains(kw)
                                    || i.getDisplayName().toLowerCase(Locale.ROOT).contains(kw)) {
                                total += i.getCount();
                            }
                        }
                    }
                    ItemInfo off = mc.getOffHandItem();
                    if (off != null && !off.isEmpty() && (off.getItemId().toLowerCase(Locale.ROOT).contains(kw)
                            || off.getDisplayName().toLowerCase(Locale.ROOT).contains(kw))) {
                        total += off.getCount();
                    }
                }
                return kw.toUpperCase(Locale.ROOT) + ": " + total;
            }
        });
        hm.register(new HudElement("custom_item_counter", "Item Counter", itemCounterMod, 0.50f, 0.76f, 112f, 20f));

        // 27. Clock HUD
        Module clockMod = mm.register(new Module("clock_hud", "Clock HUD", "Displays real-world clock or in-game world time", ModuleCategory.HUD) {
            private final ModeSetting source = addSetting(new ModeSetting("source", "Time Source", "Real clock or Minecraft world time", "Real 24h", "Real 24h", "Minecraft Day"));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                if ("Minecraft Day".equals(source.getValue()) && mc != null && mc.isInWorld()) {
                    long ticks = mc.getDayTime();
                    long day = ticks / 24000L + 1L;
                    long dayTicks = (ticks % 24000L + 6000L) % 24000L;
                    long hh = dayTicks / 1000L;
                    long min = (dayTicks % 1000L) * 60L / 1000L;
                    return String.format(Locale.ROOT, "Day %d · %02d:%02d", Long.valueOf(day), Long.valueOf(hh), Long.valueOf(min));
                }
                return "Time " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            }
        });
        hm.register(new HudElement("clock_hud", "Clock HUD", clockMod, 0.985f, 0.32f, 118f, 20f));

        // 28. Compass HUD
        Module compassMod = mm.register(new Module("compass_hud", "Compass HUD", "Renders horizontal degree tape compass with cardinal markers", ModuleCategory.HUD) {
            private final BooleanSetting showDegrees = addSetting(new BooleanSetting("degrees", "Center Degrees", "Show exact heading angle in center", true));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                float yaw = mc != null && mc.isInWorld() ? mc.getPlayerYaw() : 0f;
                int deg = Math.round(((yaw % 360f) + 360f) % 360f);
                String[] tape = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
                int idx = Math.round(deg / 45f) % 8;
                String left = tape[(idx + 7) % 8];
                String mid = tape[idx];
                String right = tape[(idx + 1) % 8];
                return showDegrees.isEnabled()
                        ? ("« " + left + " · [" + mid + " " + deg + "°] · " + right + " »")
                        : ("« " + left + " · [" + mid + "] · " + right + " »");
            }
        });
        hm.register(new HudElement("compass_hud", "Compass HUD", compassMod, 0.50f, 0.015f, 176f, 20f));

        // 29. Custom Action Bar
        mm.register(new Module("custom_action_bar", "Custom Action Bar", "Styles action-bar status notifications with Neo-Dark elevation", ModuleCategory.RENDER) {
            private final NumberSetting yOffset = addSetting(new NumberSetting("y_offset", "Vertical Offset", "Pixel offset above hotbar", 4.0, -20.0, 40.0, 2.0));
            private final BooleanSetting pillFrame = addSetting(new BooleanSetting("pill_frame", "Pill Backdrop", "Draw rounded translucent backdrop", true));

            public float getYOffset() { return yOffset.getFloat(); }
            public boolean isPillFrame() { return pillFrame.isEnabled(); }
        });

        // 30. Session Information
        Module sessionInfoMod = mm.register(new Module("session_information", "Session Information", "Consolidated player, world, FPS, and playtime card", ModuleCategory.HUD) {
            private final BooleanSetting includeWorld = addSetting(new BooleanSetting("include_world", "Show World/Server", "Include active world or server label", true));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                String player = mc != null ? mc.getPlayerName() : "Player";
                int fps = mc != null ? mc.getFps() : 60;
                String world = includeWorld.isEnabled() && mc != null && mc.isInWorld() ? (" @ " + mc.getWorldOrServerName()) : "";
                return player + world + " | " + fps + " FPS";
            }
        });
        hm.register(new HudElement("session_information", "Session Info", sessionInfoMod, 0.015f, 0.37f, 180f, 20f));
    }
}
