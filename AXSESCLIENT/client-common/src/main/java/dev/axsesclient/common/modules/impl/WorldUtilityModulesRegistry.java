package dev.axsesclient.common.modules.impl;

import dev.axsesclient.common.hud.HudElement;
import dev.axsesclient.common.hud.HudManager;
import dev.axsesclient.common.i18n.LocalizationManager;
import dev.axsesclient.common.media.MediaSessionBridge;
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
import dev.axsesclient.common.settings.StringSetting;
import dev.axsesclient.common.theme.AxsesTheme;
import dev.axsesclient.common.theme.ThemeManager;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class WorldUtilityModulesRegistry {
    private WorldUtilityModulesRegistry() {
    }

    public static void registerAll(
            ModuleManager mm,
            HudManager hm,
            final ClientRuntimeState state,
            final NotificationManager notifications
    ) {
        registerAll(mm, hm, state, notifications, null, null, null);
    }

    public static void registerAll(
            ModuleManager mm,
            HudManager hm,
            final ClientRuntimeState state,
            final NotificationManager notifications,
            final LocalizationManager i18n,
            final FontManager fonts,
            final ThemeManager themes
    ) {
        // 55. Waypoints (with real 3D World Beacons!)
        Module waypointsMod = mm.register(new Module("waypoints", "Waypoints", "Creates and tracks local coordinates with live 3D beacons & distance readout", ModuleCategory.WORLD) {
            private final StringSetting label = addSetting(new StringSetting("wp_name", "New Waypoint Label", "Label saved when enabling or marking", "Home Base", 24));
            private final ColorSetting color = addSetting(new ColorSetting("wp_color", "Marker Color", "Waypoint badge color", 0xFF00D9FF));
            private final BooleanSetting saveCurrentOnEnable = addSetting(new BooleanSetting("mark_on_enable", "Mark Position On Enable", "Bookmark current player coordinates when enabled", false));
            private final BooleanSetting beacons3d = addSetting(new BooleanSetting("beacons_3d", "3D World Beacons", "Project floating waypoint beacons in 3D space", true));

            @Override
            public void onEnable(MinecraftAdapter mc) {
                if (saveCurrentOnEnable.isEnabled() && mc != null && mc.isInWorld()) {
                    state.addOrReplaceWaypoint(new ClientRuntimeState.Waypoint(
                            label.getValue(), mc.getPlayerX(), mc.getPlayerY(), mc.getPlayerZ(),
                            mc.getDimensionId(), mc.getWorldOrServerName(), color.getArgb()
                    ));
                    boolean ru = i18n != null && i18n.isRussian();
                    notifications.push(ru ? "Метка сохранена" : "Waypoint Saved", label.getValue(), NotificationManager.Severity.SUCCESS);
                }
            }

            @Override
            public void onRender(RenderAdapter r, MinecraftAdapter mc, float dt) {
                if (!beacons3d.isEnabled() || mc == null || !mc.isInWorld() || mc.isClickGuiOpen() || fonts == null) return;
                List<ClientRuntimeState.Waypoint> wps = state.getWaypoints();
                boolean ru = i18n != null && i18n.isRussian();
                for (int i = 0; i < wps.size(); i++) {
                    ClientRuntimeState.Waypoint wp = wps.get(i);
                    float[] proj = mc.projectWorldToScreen(wp.x, wp.y + 1.6, wp.z);
                    if (proj != null && proj[2] > 0.2f) {
                        double dist = Math.hypot(mc.getPlayerX() - wp.x, mc.getPlayerZ() - wp.z);
                        String txt = String.format(Locale.ROOT, "◈ %s · %.0f%s", wp.name, Double.valueOf(dist), ru ? "м" : "m");
                        float bw = fonts.width(r, txt, 0.86f) + 14.0f;
                        float bx = proj[0] - bw * 0.5f;
                        float by = proj[1] - 20.0f;
                        int col = themes != null ? themes.filterColor(wp.color) : wp.color;
                        r.drawLine(proj[0], proj[1] - 2.0f, proj[0], proj[1] + 8.0f, 1.4f, AxsesTheme.withAlpha(col, 0.65f));
                        r.fillRoundedRect(proj[0] - 3.0f, proj[1] - 3.0f, 6.0f, 6.0f, 3.0f, col);
                        r.fillRoundedRect(bx, by, bw, 16.0f, 8.0f, 0xDF060A18);
                        r.drawRoundedOutline(bx, by, bw, 16.0f, 8.0f, 1.0f, col);
                        fonts.drawCentered(r, txt, proj[0], by + 4.5f, 0xFFF8FAFF, 0.86f);
                    }
                }
            }

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                boolean ru = i18n != null && i18n.isRussian();
                List<ClientRuntimeState.Waypoint> wps = state.getWaypoints();
                if (wps.isEmpty()) return ru ? "Метки: 0 сохр." : "Waypoints: 0 saved";
                ClientRuntimeState.Waypoint top = wps.get(wps.size() - 1);
                if (mc != null && mc.isInWorld()) {
                    double dist = Math.hypot(mc.getPlayerX() - top.x, mc.getPlayerZ() - top.z);
                    return String.format(Locale.ROOT, ru ? "◈ %s [%.0fм]" : "◈ %s [%.0fm]", top.name, Double.valueOf(dist));
                }
                return String.format(Locale.ROOT, "◈ %s (%.0f, %.0f, %.0f)", top.name, Double.valueOf(top.x), Double.valueOf(top.y), Double.valueOf(top.z));
            }
        });
        hm.register(new HudElement("waypoints", "Waypoints", waypointsMod, 0.015f, 0.67f, 152f, 20f));

        // 56. Death Location (with real 3D Death Beacon!)
        Module deathMod = mm.register(new Module("death_location", "Death Location", "Automatically logs last death coordinates with 3D world beacon & distance", ModuleCategory.WORLD, true, KeyCodes.KEY_NONE, false) {
            private final BooleanSetting toastAlert = addSetting(new BooleanSetting("toast", "Show Notification", "Notify coordinates upon respawning", true));
            private final BooleanSetting beacon3d = addSetting(new BooleanSetting("beacon_3d", "3D Death Beacon", "Render 3D skull beacon at last death position", true));
            private boolean wasDead = false;

            @Override
            public void onTick(MinecraftAdapter mc) {
                if (mc == null || !mc.isInWorld()) return;
                boolean dead = mc.getHealth() <= 0.0f;
                if (dead && !wasDead) {
                    wasDead = true;
                    state.recordDeath(mc.getPlayerX(), mc.getPlayerY(), mc.getPlayerZ(), mc.getDimensionId(), mc.getWorldOrServerName());
                    if (toastAlert.isEnabled()) {
                        boolean ru = i18n != null && i18n.isRussian();
                        notifications.push(ru ? "Точка смерти записана" : "Death Recorded",
                                String.format(Locale.ROOT, "XYZ %.0f, %.0f, %.0f", Double.valueOf(mc.getPlayerX()), Double.valueOf(mc.getPlayerY()), Double.valueOf(mc.getPlayerZ())),
                                NotificationManager.Severity.WARNING);
                    }
                } else if (!dead) {
                    wasDead = false;
                }
            }

            @Override
            public void onRender(RenderAdapter r, MinecraftAdapter mc, float dt) {
                if (!beacon3d.isEnabled() || mc == null || !mc.isInWorld() || mc.isClickGuiOpen() || fonts == null) return;
                ClientRuntimeState.Waypoint d = state.getLastDeathWaypoint();
                if (d == null) return;
                float[] proj = mc.projectWorldToScreen(d.x, d.y + 1.5, d.z);
                if (proj != null && proj[2] > 0.2f) {
                    boolean ru = i18n != null && i18n.isRussian();
                    double dist = Math.hypot(mc.getPlayerX() - d.x, mc.getPlayerZ() - d.z);
                    String txt = String.format(Locale.ROOT, ru ? "☠ Смерть · %.0fм" : "☠ Death · %.0fm", Double.valueOf(dist));
                    float bw = fonts.width(r, txt, 0.86f) + 14.0f;
                    float bx = proj[0] - bw * 0.5f;
                    float by = proj[1] - 20.0f;
                    r.fillRoundedRect(bx, by, bw, 16.0f, 8.0f, 0xE0180612);
                    r.drawRoundedOutline(bx, by, bw, 16.0f, 8.0f, 1.0f, 0xFFFF3864);
                    fonts.drawCentered(r, txt, proj[0], by + 4.5f, 0xFFFF8FA3, 0.86f);
                }
            }

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                ClientRuntimeState.Waypoint d = state.getLastDeathWaypoint();
                if (d == null) return null;
                boolean ru = i18n != null && i18n.isRussian();
                return String.format(Locale.ROOT, ru ? "Смерть: %.0f, %.0f, %.0f" : "Last Death: %.0f, %.0f, %.0f",
                        Double.valueOf(d.x), Double.valueOf(d.y), Double.valueOf(d.z));
            }
        });
        hm.register(new HudElement("death_location", "Death Location", deathMod, 0.015f, 0.73f, 165f, 20f));

        // 57. Screenshot Manager
        Module ssMgr = mm.register(new Module("screenshot_manager", "Screenshot Manager", "Monitors local screenshots directory count and latest capture", ModuleCategory.UTILITY) {
            private final BooleanSetting copyLatestName = addSetting(new BooleanSetting("copy_name", "Copy Latest On Enable", "Copy newest screenshot filename to clipboard", false));

            @Override
            public void onEnable(MinecraftAdapter mc) {
                if (mc != null && copyLatestName.isEnabled()) {
                    List<String> files = state.scanScreenshots(mc.getGameDir());
                    if (!files.isEmpty()) {
                        mc.setClipboard(files.get(0));
                        notifications.push("Screenshot Copied", files.get(0), NotificationManager.Severity.INFO);
                    }
                }
            }

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                List<String> files = state.scanScreenshots(mc != null ? mc.getGameDir() : null);
                return "Screenshots: " + files.size() + " saved";
            }
        });
        hm.register(new HudElement("screenshot_manager", "Screenshots", ssMgr, 0.985f, 0.74f, 142f, 20f));

        // 58. World Information
        Module worldInfo = mm.register(new Module("world_information", "World Information", "Displays world name, difficulty, and loaded client chunks", ModuleCategory.WORLD) {
            private final BooleanSetting showChunks = addSetting(new BooleanSetting("chunks", "Show Chunk Count", "Include loaded client chunks", true));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                if (mc == null || !mc.isInWorld()) return "World: Menu";
                String base = mc.getWorldOrServerName() + " (" + mc.getDifficultyName() + ")";
                return showChunks.isEnabled() ? (base + " · " + mc.getLoadedChunksCount() + " ch") : base;
            }
        });
        hm.register(new HudElement("world_information", "World Info", worldInfo, 0.985f, 0.80f, 185f, 20f));

        // 59. Biome Information
        Module biomeInfo = mm.register(new Module("biome_information", "Biome Information", "Shows detailed biome climate temperature and precipitation", ModuleCategory.WORLD) {
            private final BooleanSetting showTemp = addSetting(new BooleanSetting("show_temp", "Show Temperature", "Include climate float", true));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                if (mc == null || !mc.isInWorld()) return "Climate: Temperate · Rain";
                String text = mc.getBiomeName() + " (" + mc.getBiomePrecipitation() + ")";
                return showTemp.isEnabled()
                        ? String.format(Locale.ROOT, "%s %.2f", text, Float.valueOf(mc.getBiomeTemperature()))
                        : text;
            }
        });
        hm.register(new HudElement("biome_information", "Biome Info", biomeInfo, 0.015f, 0.79f, 175f, 20f));

        // 60. Light Level Display
        Module lightMod = mm.register(new Module("light_level_display", "Light Level Display", "Shows Block and Sky light levels at player position with spawn alert", ModuleCategory.WORLD) {
            private final NumberSetting warnBelow = addSetting(new NumberSetting("warn_below", "Spawn Alert Level", "Warn when block light is at or below threshold", 0.0, 0.0, 7.0, 1.0));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                if (mc == null || !mc.isInWorld()) return "Light: Block 15 | Sky 15";
                int bl = mc.getBlockLight();
                int sl = mc.getSkyLight();
                String warn = bl <= warnBelow.getInt() ? " [!]" : "";
                return "Light B:" + bl + " S:" + sl + warn;
            }
        });
        hm.register(new HudElement("light_level_display", "Light Level", lightMod, 0.50f, 0.70f, 134f, 20f));

        // 61. Chunk Border Toggle
        mm.register(new Module("chunk_border_toggle", "Chunk Border Toggle", "Toggles vanilla chunk boundary grid overlay cleanly via keybind or GUI", ModuleCategory.WORLD) {
            private final BooleanSetting notifyState = addSetting(new BooleanSetting("notify", "Toast Feedback", "Show notification when chunk grid toggles", true));

            @Override
            public void onEnable(MinecraftAdapter mc) {
                if (mc != null && !mc.isChunkBordersVisible()) {
                    boolean now = mc.toggleChunkBorders();
                    if (notifyState.isEnabled()) notifications.push("Chunk Borders", now ? "Shown" : "Hidden", NotificationManager.Severity.INFO);
                }
            }

            @Override
            public void onDisable(MinecraftAdapter mc) {
                if (mc != null && mc.isChunkBordersVisible()) {
                    mc.toggleChunkBorders();
                }
            }
        });

        // 62. Local Notes
        Module localNotes = mm.register(new Module("local_notes", "Local Notes", "Persistent personal scratchpad viewable in GUI and optionally on HUD", ModuleCategory.UTILITY) {
            private final StringSetting noteText = addSetting(new StringSetting("text", "Note Content", "Saved local note text", "Remember potions & ender pearls", 96));

            @Override
            public void onTick(MinecraftAdapter mc) {
                if (!noteText.getValue().equals(state.getGlobalNotes())) {
                    state.setGlobalNotes(noteText.getValue());
                }
            }

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                return "Note: " + noteText.getValue();
            }
        });
        hm.register(new HudElement("local_notes", "Local Notes", localNotes, 0.50f, 0.14f, 210f, 20f));

        // 63. Coordinate Converter
        Module coordConv = mm.register(new Module("coordinate_converter", "Coordinate Converter", "Real-time Overworld (÷8) <-> Nether (×8) portal coordinate calculator", ModuleCategory.UTILITY) {
            private final BooleanSetting includeY = addSetting(new BooleanSetting("include_y", "Include Y Axis", "Keep Y height in converted output", true));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                if (mc == null || !mc.isInWorld()) return "Portal: 0, 64, 0";
                boolean inNether = mc.getDimensionId().contains("nether");
                double factor = inNether ? 8.0 : 0.125;
                String targetDim = inNether ? "OW" : "Nether";
                if (includeY.isEnabled()) {
                    return String.format(Locale.ROOT, "%s: %.0f, %.0f, %.0f", targetDim,
                            Double.valueOf(mc.getPlayerX() * factor), Double.valueOf(mc.getPlayerY()), Double.valueOf(mc.getPlayerZ() * factor));
                }
                return String.format(Locale.ROOT, "%s: %.0f, %.0f", targetDim,
                        Double.valueOf(mc.getPlayerX() * factor), Double.valueOf(mc.getPlayerZ() * factor));
            }
        });
        hm.register(new HudElement("coordinate_converter", "Portal Coords", coordConv, 0.50f, 0.20f, 152f, 20f));

        // 64. Session Statistics
        Module sessStats = mm.register(new Module("session_statistics", "Session Statistics", "Summarizes worlds visited, jumps, and distance traveled this session", ModuleCategory.UTILITY) {
            private final BooleanSetting showWorlds = addSetting(new BooleanSetting("worlds", "Show Worlds Count", "Include worlds visited count", true));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                double m = state.getWalkMeters() + state.getSprintMeters();
                String base = String.format(Locale.ROOT, "Stats: %d jumps · %.0fm", Integer.valueOf(state.getTotalJumps()), Double.valueOf(m));
                return showWorlds.isEnabled() ? (base + " · " + state.getWorldsVisited() + " worlds") : base;
            }
        });
        hm.register(new HudElement("session_statistics", "Session Stats", sessStats, 0.985f, 0.86f, 190f, 20f));

        // 65. Time Display
        Module timeDisp = mm.register(new Module("time_display", "Time Display", "Displays local calendar date and clock time", ModuleCategory.UTILITY) {
            private final ModeSetting format = addSetting(new ModeSetting("format", "Format", "Date/time pattern", "Date + Time", "Date + Time", "ISO Date"));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                LocalDateTime now = LocalDateTime.now();
                if ("ISO Date".equals(format.getValue())) {
                    return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                }
                return now.format(DateTimeFormatter.ofPattern("dd MMM HH:mm"));
            }
        });
        hm.register(new HudElement("time_display", "Date & Time", timeDisp, 0.985f, 0.92f, 128f, 20f));

        // 66. Durability Warning
        mm.register(new Module("durability_warning", "Durability Warning", "Pushes warning notifications when held tool or armor nears breaking", ModuleCategory.UTILITY, true, KeyCodes.KEY_NONE, false) {
            private final NumberSetting thresholdPct = addSetting(new NumberSetting("threshold", "Alert Threshold %", "Warn below durability %", 15.0, 3.0, 40.0, 1.0));
            private long lastAlertMs = 0L;

            @Override
            public void onTick(MinecraftAdapter mc) {
                if (mc == null || !mc.isInWorld()) return;
                long now = System.currentTimeMillis();
                if (now - lastAlertMs < 15000L) return;
                float thresh = thresholdPct.getFloat() / 100.0f;
                ItemInfo main = mc.getMainHandItem();
                if (main != null && main.isDamageable() && main.getDurabilityRatio() <= thresh) {
                    lastAlertMs = now;
                    notifications.push("Low Tool Durability", main.getDisplayName() + " at " + Math.round(main.getDurabilityRatio() * 100f) + "%!", NotificationManager.Severity.WARNING);
                    return;
                }
                for (ItemInfo ar : mc.getArmorItems()) {
                    if (ar != null && ar.isDamageable() && ar.getDurabilityRatio() <= thresh) {
                        lastAlertMs = now;
                        notifications.push("Low Armor Durability", ar.getDisplayName() + " at " + Math.round(ar.getDurabilityRatio() * 100f) + "%!", NotificationManager.Severity.WARNING);
                        return;
                    }
                }
            }
        });

        // 67. Inventory Search
        Module invSearch = mm.register(new Module("inventory_search", "Inventory Search", "Searches player inventory slots by name and reports matching slot indices", ModuleCategory.UTILITY) {
            private final StringSetting query = addSetting(new StringSetting("query", "Search Query", "Item name filter", "diamond", 32));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                String q = query.getValue().trim().toLowerCase(Locale.ROOT);
                if (q.isEmpty() || mc == null || !mc.isInWorld()) return "Inv Search [" + q + "]: 0 slots";
                int slots = 0;
                int qty = 0;
                for (ItemInfo item : mc.getInventoryItems()) {
                    if (item != null && !item.isEmpty() && (item.getDisplayName().toLowerCase(Locale.ROOT).contains(q)
                            || item.getItemId().toLowerCase(Locale.ROOT).contains(q))) {
                        slots++;
                        qty += item.getCount();
                    }
                }
                return "Search '" + q + "': " + qty + " in " + slots + " slots";
            }
        });
        hm.register(new HudElement("inventory_search", "Inv Search", invSearch, 0.50f, 0.26f, 175f, 20f));

        // 68. Custom Notifications
        mm.register(new Module("custom_notifications", "Custom Notifications", "Configures AXSES toast notification duration and toggle feedback", ModuleCategory.UTILITY, true, KeyCodes.KEY_NONE, false) {
            private final BooleanSetting toggleAlerts = addSetting(new BooleanSetting("toggle_alerts", "Module Toggle Toasts", "Show toast when modules turn on/off", true));
            private final NumberSetting durationSec = addSetting(new NumberSetting("duration_sec", "Toast Duration (s)", "Seconds each toast stays visible", 2.8, 1.0, 8.0, 0.2));

            @Override
            public void onTick(MinecraftAdapter mc) {
                notifications.setNotificationsEnabled(true);
                notifications.setModuleToggleAlerts(toggleAlerts.isEnabled());
                notifications.setDefaultDurationMs((long) (durationSec.getDouble() * 1000.0));
            }

            @Override
            public void onDisable(MinecraftAdapter mc) {
                notifications.setNotificationsEnabled(false);
            }
        });

        // 69. World Session Timer
        Module worldTimer = mm.register(new Module("world_session_timer", "World Session Timer", "Tracks time spent in the currently joined world or server", ModuleCategory.WORLD) {
            private final BooleanSetting showWorldLabel = addSetting(new BooleanSetting("world_label", "Show World Name", "Prefix timer with current world", false));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                String t = ClientRuntimeState.formatHhMmSs(state.getWorldSessionElapsedSeconds());
                if (showWorldLabel.isEnabled() && mc != null && mc.isInWorld()) {
                    return mc.getWorldOrServerName() + ": " + t;
                }
                return "World Time " + t;
            }
        });
        hm.register(new HudElement("world_session_timer", "World Timer", worldTimer, 0.50f, 0.32f, 145f, 20f));

        // 70. Resource Pack Manager
        mm.register(new Module("resource_pack_manager", "Resource Pack Manager", "Inspects active resource packs and provides instant pack screen access", ModuleCategory.UTILITY) {
            private final BooleanSetting openScreenOnEnable = addSetting(new BooleanSetting("open_screen", "Open Pack Screen", "Open Minecraft Resource Pack menu when enabled", false));

            @Override
            public void onEnable(MinecraftAdapter mc) {
                if (mc != null) {
                    int active = mc.getEnabledResourcePacks().size();
                    int total = mc.getAvailableResourcePacks().size();
                    notifications.push("Resource Packs", active + " active / " + total + " available", NotificationManager.Severity.INFO);
                    if (openScreenOnEnable.isEnabled()) {
                        mc.openVanillaResourcePacks();
                        setEnabled(false, mc);
                    }
                }
            }
        });

        // 71. Screenshot Gallery
        mm.register(new Module("screenshot_gallery", "Screenshot Gallery", "Lists recent PNG captures from screenshots directory inside ClickGUI", ModuleCategory.UTILITY) {
            private final NumberSetting maxItems = addSetting(new NumberSetting("max_items", "Gallery Limit", "Number of recent screenshots listed", 12.0, 4.0, 50.0, 4.0));

            public int getMaxItems() { return maxItems.getInt(); }
        });

        // 72. Local World Notes
        Module worldNotesMod = mm.register(new Module("local_world_notes", "Local World Notes", "Per-world scoped notes that switch automatically per world/server", ModuleCategory.WORLD) {
            private final StringSetting note = addSetting(new StringSetting("world_note", "World Note", "Note attached to active world", "Spawn portal at 0, 70, 0", 96));

            @Override
            public void onTick(MinecraftAdapter mc) {
                if (mc != null && mc.isInWorld()) {
                    state.setWorldNote(mc.getWorldOrServerName(), note.getValue());
                }
            }

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                String w = mc != null && mc.isInWorld() ? mc.getWorldOrServerName() : "World";
                return "[" + w + "] " + note.getValue();
            }
        });
        hm.register(new HudElement("local_world_notes", "World Notes", worldNotesMod, 0.50f, 0.38f, 195f, 20f));

        // 73. Coordinate Clipboard
        mm.register(new Module("coordinate_clipboard", "Coordinate Clipboard", "Copies formatted player coordinates and dimension to clipboard", ModuleCategory.UTILITY, false, KeyCodes.KEY_K, false) {
            private final BooleanSetting includeDimension = addSetting(new BooleanSetting("include_dim", "Include Dimension", "Append dimension identifier", true));

            @Override
            public void onEnable(MinecraftAdapter mc) {
                if (mc != null) {
                    String formatted = String.format(Locale.ROOT, "X: %.0f, Y: %.0f, Z: %.0f%s",
                            Double.valueOf(mc.getPlayerX()),
                            Double.valueOf(mc.getPlayerY()),
                            Double.valueOf(mc.getPlayerZ()),
                            includeDimension.isEnabled() ? (" [" + mc.getDimensionId() + "]") : "");
                    mc.setClipboard(formatted);
                    notifications.push("Coordinates Copied", formatted, NotificationManager.Severity.SUCCESS);
                }
                setEnabled(false, mc);
            }
        });

        // 74. Local World Information Panel
        Module localWorldPanel = mm.register(new Module("local_world_info_panel", "Local World Information Panel", "Displays weather state, lunar phase, and day cycle", ModuleCategory.WORLD) {
            private final BooleanSetting showMoonPhase = addSetting(new BooleanSetting("moon_phase", "Show Moon Phase", "Calculate 8-phase lunar cycle", true));

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                if (mc == null || !mc.isInWorld()) return "Env: Clear · Full Moon";
                String weather = mc.isThundering() ? "Thunder" : (mc.isRaining() ? "Rain" : "Clear");
                if (!showMoonPhase.isEnabled()) return "Weather: " + weather;
                String[] phases = {"Full Moon", "Waning Gibbous", "Third Quarter", "Waning Crescent",
                        "New Moon", "Waxing Crescent", "First Quarter", "Waxing Gibbous"};
                int phaseIdx = (int) ((mc.getDayTime() / 24000L) % 8L + 8L) % 8;
                return weather + " · " + phases[phaseIdx];
            }
        });
        hm.register(new HudElement("local_world_info_panel", "Environment Panel", localWorldPanel, 0.50f, 0.44f, 178f, 20f));

        // 109. Spotify & In-Game Music Player HUD (Музыка и Spotify Плеер) — Auto-mutes MC music + Interactive Play/Continue, Pause, Stop, Next, Prev
        final BooleanSetting musicShowControlsRef = new BooleanSetting("show_controls", "Interactive Buttons", "Show clickable Prev / Continue-Pause / Stop / Next buttons on HUD & in Chat", true);
        final ModeSetting musicStyleRef = new ModeSetting("eq_style", "Player Style", "Visual accent style for music equalizer & progress", "Spotify Emerald", "Spotify Emerald", "Aurora", "Theme");
        final BooleanSetting musicAutoMuteMcRef = new BooleanSetting("auto_mute_mc", "Auto-Stop MC Music", "Automatically silence vanilla Minecraft background music when Spotify/Music plays", true);
        final BooleanSetting musicSyncSpotifyRef = new BooleanSetting("sync_spotify", "Sync With Spotify", "Detect open Spotify desktop app track & control playback", true);

        Module spotifyMusicMod = mm.register(new Module("spotify_music_hud", "Spotify & Music", "Interactive Spotify & in-game music controller with Play/Continue, Stop, Next/Prev & auto-mute MC music", ModuleCategory.UTILITY, true, KeyCodes.KEY_NONE, false) {
            {
                addSetting(musicAutoMuteMcRef);
                addSetting(musicSyncSpotifyRef);
                addSetting(musicStyleRef);
                addSetting(musicShowControlsRef);
            }

            @Override
            public void onTick(MinecraftAdapter mc) {
                MediaSessionBridge bridge = MediaSessionBridge.getInstance();
                bridge.tick(0.05f, musicSyncSpotifyRef.isEnabled());
                if (mc != null && musicAutoMuteMcRef.isEnabled() && bridge.isPlaying()) {
                    mc.stopVanillaMusic();
                }
            }

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                MediaSessionBridge b = MediaSessionBridge.getInstance();
                return "♫ " + b.getArtist() + " — " + b.getTrackTitle();
            }
        });
        spotifyMusicMod.setFavorite(true);

        HudElement spotifyHudEl = new HudElement("spotify_music_hud", "Spotify Music", spotifyMusicMod, 0.986f, 0.635f, 238f, 55f)
                .withCustomPainter(new HudElement.CustomHudPainter() {
                    @Override
                    public boolean paint(RenderAdapter r, MinecraftAdapter mc, ThemeManager tm, FontManager fm,
                                         float px, float py, float sw, float sh, float opacity, boolean editorMode, boolean selected) {
                        if (fm == null || tm == null) return false;
                        MediaSessionBridge bridge = MediaSessionBridge.getInstance();
                        AxsesTheme t = tm.getActiveTheme();
                        boolean ru = i18n == null || i18n.isRussian();
                        boolean playing = bridge.isPlaying();
                        boolean stopped = bridge.getState() == MediaSessionBridge.PlaybackState.STOPPED;

                        int spotifyGreen = 0xFF1DB954;
                        int prim = "Spotify Emerald".equalsIgnoreCase(musicStyleRef.getValue())
                                ? spotifyGreen
                                : tm.filterColor(t.getPrimaryColor());
                        int acc = tm.filterColor(t.getAccentColor());

                        float rad = 12.0f;
                        r.drawShadow(px, py, sw, sh, rad, 0.56f * opacity, 0x000000);
                        r.fillRoundedRect(px, py, sw, sh, rad, AxsesTheme.withAlpha(t.getBackgroundColor(), Math.min(0.96f, opacity + 0.04f)));
                        RenderManager.fillRoundedHorizontalGradient(r, px + 10.0f, py + 1.0f, sw - 20.0f, 1.8f, 0.9f, prim, acc);
                        r.drawRoundedOutline(px, py, sw, sh, rad, selected ? 1.4f : 1.0f,
                                selected ? acc : AxsesTheme.withAlpha(prim, playing ? 0.85f : 0.48f));

                        // Vinyl / Equalizer badge on top-left
                        float discX = px + 7.0f;
                        float discY = py + 6.0f;
                        r.fillRoundedRect(discX, discY, 24.0f, 24.0f, 8.0f, AxsesTheme.withAlpha(prim, 0.20f));
                        r.drawRoundedOutline(discX, discY, 24.0f, 24.0f, 8.0f, 1.0f, AxsesTheme.withAlpha(prim, 0.75f));
                        double nowSec = (System.currentTimeMillis() % 4000L) / 1000.0;
                        for (int b = 0; b < 4; b++) {
                            float amp = playing
                                    ? (float) (4.0 + 10.0 * Math.abs(Math.sin(nowSec * 5.2 + b * 1.15)))
                                    : (stopped ? 2.5f : 5.0f);
                            float bx = discX + 4.2f + b * 4.3f;
                            float by = discY + 19.5f - amp;
                            r.fillRoundedRect(bx, by, 2.8f, amp, 1.2f, playing ? prim : 0xFF94A3B8);
                        }

                        // Header status badge (Spotify Live + Auto-Mute MC indicator)
                        String srcBadge;
                        int srcCol;
                        if (playing) {
                            srcBadge = bridge.isSpotifyProcessDetected()
                                    ? (ru ? "● SPOTIFY · MC СТОП" : "● SPOTIFY · MC MUTED")
                                    : (ru ? "● МУЗЫКА · MC СТОП" : "● MUSIC · MC MUTED");
                            srcCol = prim;
                        } else if (stopped) {
                            srcBadge = ru ? "■ СТОП" : "■ STOPPED";
                            srcCol = 0xFFFF5D8F;
                        } else {
                            srcBadge = ru ? "❚❚ ПАУЗА · SPOTIFY" : "❚❚ PAUSED · SPOTIFY";
                            srcCol = 0xFFFFD166;
                        }
                        fm.draw(r, srcBadge, px + 36.0f, py + 5.0f, srcCol, 0.80f);
                        String timeCode = bridge.formatTimeCode();
                        fm.draw(r, timeCode, px + sw - 7.0f - fm.width(r, timeCode, 0.80f), py + 5.0f, t.getMutedColor(), 0.80f);

                        // Track title & artist (pixel-width clamped cleanly)
                        String fullTrack = bridge.getArtist() + " - " + bridge.getTrackTitle();
                        while (fullTrack.length() > 8 && fm.width(r, fullTrack, 0.84f) > sw - 44.0f) {
                            fullTrack = fullTrack.substring(0, fullTrack.length() - 2) + "…";
                        }
                        fm.draw(r, fullTrack, px + 36.0f, py + 15.5f, 0xFFF8FAFF, 0.84f);

                        // Smooth progress bar
                        float barX = px + 36.0f;
                        float barY = py + 27.0f;
                        float barW = sw - 43.0f;
                        r.fillRoundedRect(barX, barY, barW, 3.2f, 1.6f, 0x36FFFFFF);
                        float fillW = Math.max(3.0f, barW * bridge.getProgressRatio());
                        RenderManager.fillRoundedHorizontalGradient(r, barX, barY, fillW, 3.2f, 1.6f, prim, acc);

                        // Upgraded 35-Year Tactile Transport Controls: [⏮] [▶ Продолжить / ⏸ Пауза] [⏹ Стоп] [⏭]
                        if (musicShowControlsRef.isEnabled()) {
                            float mx = (mc != null && (mc.isChatScreenOpen() || editorMode)) ? (float) mc.getScaledMouseX() : -999f;
                            float my = (mc != null && (mc.isChatScreenOpen() || editorMode)) ? (float) mc.getScaledMouseY() : -999f;
                            float btnY = py + 34.0f;
                            float btnH = 16.5f;

                            drawTactileHudBtn(r, fm, px + 7.0f, btnY, 24.0f, btnH, "<<", false, prim, mx, my);
                            String playLabel = playing
                                    ? (ru ? "|| Пауза" : "|| Pause")
                                    : (ru ? "> Продолжить" : "> Continue");
                            drawTactileHudBtn(r, fm, px + 35.0f, btnY, 104.0f, btnH, playLabel, true, prim, mx, my);
                            drawTactileHudBtn(r, fm, px + 143.0f, btnY, 62.0f, btnH, ru ? "■ Стоп" : "■ Stop", false, 0xFFFF4D6D, mx, my);
                            drawTactileHudBtn(r, fm, px + 209.0f, btnY, 22.0f, btnH, ">>", false, acc, mx, my);
                        }
                        return true;
                    }
                })
                .withInteractiveHandler(new HudElement.InteractiveHudHandler() {
                    @Override
                    public boolean onMouseClick(MinecraftAdapter mc, float localX, float localY, float width, float height, int button) {
                        if (button != 0 || !musicShowControlsRef.isEnabled()) return false;
                        if (localY < 32.0f || localY > 53.0f) return false;
                        MediaSessionBridge bridge = MediaSessionBridge.getInstance();
                        boolean ru = i18n == null || i18n.isRussian();
                        if (localX >= 6.0f && localX <= 32.0f) {
                            bridge.previousTrack();
                            if (mc != null && musicAutoMuteMcRef.isEnabled()) mc.stopVanillaMusic();
                            if (notifications != null) {
                                notifications.push("Spotify", (ru ? "Предыдущий: " : "Prev: ") + bridge.getTrackTitle(), NotificationManager.Severity.INFO);
                            }
                            return true;
                        }
                        if (localX >= 34.0f && localX <= 140.0f) {
                            MediaSessionBridge.PlaybackState st = bridge.togglePlayPause();
                            if (st == MediaSessionBridge.PlaybackState.PLAYING && mc != null && musicAutoMuteMcRef.isEnabled()) {
                                mc.stopVanillaMusic();
                            }
                            if (notifications != null) {
                                notifications.push("Spotify",
                                        st == MediaSessionBridge.PlaybackState.PLAYING
                                                ? (ru ? "Продолжено (музыка MC остановлена)" : "Resumed (MC music muted)")
                                                : (ru ? "Пауза воспроизведения" : "Playback Paused"),
                                        NotificationManager.Severity.SUCCESS);
                            }
                            return true;
                        }
                        if (localX >= 142.0f && localX <= 206.0f) {
                            bridge.stopMusic();
                            if (notifications != null) {
                                notifications.push("Spotify", ru ? "Музыка полностью остановлена" : "Playback Stopped", NotificationManager.Severity.WARNING);
                            }
                            return true;
                        }
                        if (localX >= 208.0f && localX <= 234.0f) {
                            bridge.nextTrack();
                            if (mc != null && musicAutoMuteMcRef.isEnabled()) mc.stopVanillaMusic();
                            if (notifications != null) {
                                notifications.push("Spotify", (ru ? "Следующий: " : "Next: ") + bridge.getTrackTitle(), NotificationManager.Severity.INFO);
                            }
                            return true;
                        }
                        return false;
                    }
                });
        hm.register(spotifyHudEl);

        // 110. Baritone Autopilot & 3D Smart Navigator (Баритон Автопилот)
        final ModeSetting baritoneTaskRef = new ModeSetting(
                "task_mode", "Baritone Task", "Navigation or mining objective for Baritone Autopilot",
                "Goto Waypoint", "Goto Waypoint", "Goto Coords", "Mine Diamonds", "Mine Ancient Debris", "Follow Nearest", "Patrol Loop"
        );
        final NumberSetting baritoneGoalXRef = new NumberSetting("goal_x", "Goal X", "Target X coordinate in Goto Coords mode", 180.0, -30000.0, 30000.0, 10.0);
        final NumberSetting baritoneGoalZRef = new NumberSetting("goal_z", "Goal Z", "Target Z coordinate in Goto Coords mode", -120.0, -30000.0, 30000.0, 10.0);
        final BooleanSetting baritoneBridgeRef = new BooleanSetting("baritone_bridge", "External Baritone Bridge", "Automatically send #goto / #mine / #stop commands when cabaletta/baritone mod is installed", true);
        final BooleanSetting baritoneJumpRef = new BooleanSetting("auto_jump", "Smart Step & Jump", "Jump automatically over 1-block terrain steps while pathing", true);
        final BooleanSetting baritoneSprintRef = new BooleanSetting("auto_sprint", "Sprint On Route", "Maintain sprint along clear path segments", true);
        final BooleanSetting baritonePath3dRef = new BooleanSetting("path_3d", "3D Neon Route", "Render glowing 3D Baritone path line and destination beacon in world", true);
        final boolean[] baritonePausedState = new boolean[] { false };

        final Module baritoneMod = mm.register(new Module("baritone_bot", "Baritone Autopilot", "Integrated Baritone AI pathfinder, #mine/#goto bridge & 3D neon route visualizer", ModuleCategory.WORLD, false, KeyCodes.KEY_NONE, false) {
            {
                addSetting(baritoneTaskRef);
                addSetting(baritoneGoalXRef);
                addSetting(baritoneGoalZRef);
                addSetting(baritoneBridgeRef);
                addSetting(baritoneJumpRef);
                addSetting(baritoneSprintRef);
                addSetting(baritonePath3dRef);
            }

            private String lastDispatchedExternalTask = "";

            private double[] resolveGoal(MinecraftAdapter mc) {
                String mode = baritoneTaskRef.getValue();
                if ("Goto Waypoint".equalsIgnoreCase(mode)) {
                    List<ClientRuntimeState.Waypoint> wps = state.getWaypoints();
                    if (!wps.isEmpty()) {
                        ClientRuntimeState.Waypoint wp = wps.get(wps.size() - 1);
                        return new double[] { wp.x, wp.y, wp.z };
                    }
                    ClientRuntimeState.Waypoint d = state.getLastDeathWaypoint();
                    if (d != null) {
                        return new double[] { d.x, d.y, d.z };
                    }
                } else if ("Follow Nearest".equalsIgnoreCase(mode) && mc != null) {
                    List<TargetEntityInfo> near = mc.getNearbyTargets(48.0, true, false, false, false);
                    if (near != null && !near.isEmpty()) {
                        TargetEntityInfo t = near.get(0);
                        return new double[] { t.getX(), t.getY(), t.getZ() };
                    }
                } else if ("Patrol Loop".equalsIgnoreCase(mode) && mc != null) {
                    double ang = ((System.currentTimeMillis() % 24000L) / 24000.0) * Math.PI * 2.0;
                    return new double[] { mc.getPlayerX() + Math.cos(ang) * 18.0, mc.getPlayerY(), mc.getPlayerZ() + Math.sin(ang) * 18.0 };
                }
                double py = mc != null ? mc.getPlayerY() : 64.0;
                return new double[] { baritoneGoalXRef.getDouble(), py, baritoneGoalZRef.getDouble() };
            }

            @Override
            public void onEnable(MinecraftAdapter mc) {
                baritonePausedState[0] = false;
                lastDispatchedExternalTask = "";
                if (mc != null && baritoneBridgeRef.isEnabled() && mc.isExternalBaritoneLoaded()) {
                    dispatchExternalCommand(mc);
                }
            }

            @Override
            public void onDisable(MinecraftAdapter mc) {
                baritonePausedState[0] = false;
                lastDispatchedExternalTask = "";
                if (mc != null) {
                    mc.setAutoWalkInput(false, false, false);
                    if (baritoneBridgeRef.isEnabled() && mc.isExternalBaritoneLoaded()) {
                        mc.sendChatOrCommand("#stop");
                    }
                }
            }

            private void dispatchExternalCommand(MinecraftAdapter mc) {
                String mode = baritoneTaskRef.getValue();
                double[] g = resolveGoal(mc);
                String cmd;
                if ("Mine Diamonds".equalsIgnoreCase(mode)) {
                    cmd = "#mine diamond_ore deepslate_diamond_ore";
                } else if ("Mine Ancient Debris".equalsIgnoreCase(mode)) {
                    cmd = "#mine ancient_debris";
                } else if ("Follow Nearest".equalsIgnoreCase(mode)) {
                    cmd = "#follow players";
                } else {
                    cmd = String.format(Locale.ROOT, "#goto %d %d", Long.valueOf(Math.round(g[0])), Long.valueOf(Math.round(g[2])));
                }
                if (!cmd.equals(lastDispatchedExternalTask)) {
                    lastDispatchedExternalTask = cmd;
                    mc.sendChatOrCommand(cmd);
                }
            }

            @Override
            public void onTick(MinecraftAdapter mc) {
                if (mc == null || !mc.isInWorld()) return;
                if (baritonePausedState[0] || mc.isAnyScreenOpen()) {
                    mc.setAutoWalkInput(false, false, false);
                    return;
                }
                if (baritoneBridgeRef.isEnabled() && mc.isExternalBaritoneLoaded()) {
                    dispatchExternalCommand(mc);
                    return;
                }
                double[] g = resolveGoal(mc);
                double dx = g[0] - mc.getPlayerX();
                double dz = g[2] - mc.getPlayerZ();
                double dist = Math.hypot(dx, dz);
                if (dist <= 1.65) {
                    mc.setAutoWalkInput(false, false, false);
                    boolean ru = i18n == null || i18n.isRussian();
                    if (notifications != null) {
                        notifications.push("Baritone", ru ? "Точка маршрута достигнута!" : "Destination reached!", NotificationManager.Severity.SUCCESS);
                    }
                    setEnabled(false, mc);
                    return;
                }
                float desiredYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
                float curYaw = mc.getPlayerYaw();
                float diff = ((desiredYaw - curYaw) % 360.0f);
                if (diff >= 180.0f) diff -= 360.0f;
                if (diff < -180.0f) diff += 360.0f;
                float step = Math.max(-12.0f, Math.min(12.0f, diff * 0.35f));
                mc.setPlayerRotation(curYaw + step, mc.getPlayerPitch());

                double moved = Math.hypot(mc.getPlayerX() - mc.getPrevPlayerX(), mc.getPlayerZ() - mc.getPrevPlayerZ());
                boolean needStepJump = baritoneJumpRef.isEnabled() && mc.isOnGround() && moved < 0.035 && dist > 2.5;
                mc.setAutoWalkInput(true, needStepJump, baritoneSprintRef.isEnabled());
            }

            @Override
            public void onRender(RenderAdapter r, MinecraftAdapter mc, float dt) {
                if (!baritonePath3dRef.isEnabled() || mc == null || !mc.isInWorld() || mc.isClickGuiOpen() || fonts == null) {
                    return;
                }
                double[] g = resolveGoal(mc);
                double px = mc.getPlayerX();
                double py = mc.getPlayerY();
                double pz = mc.getPlayerZ();
                double dx = g[0] - px;
                double dy = g[1] - py;
                double dz = g[2] - pz;
                double dist = Math.hypot(dx, dz);
                int steps = Math.max(4, Math.min(18, (int) (dist / 2.2)));
                int pathCol = baritonePausedState[0] ? 0xFFFFD166 : 0xFF00F5D4;

                float prevSx = 0f, prevSy = 0f;
                boolean hasPrev = false;
                double maxDrawDist = Math.min(dist, 34.0);
                double ux = dist > 0.01 ? (dx / dist) : 0.0;
                double uz = dist > 0.01 ? (dz / dist) : 1.0;

                for (int s = 1; s <= steps; s++) {
                    double frac = (double) s / (double) steps;
                    double dAlong = maxDrawDist * frac;
                    double wx = px + ux * dAlong;
                    double wy = py + 0.12 + dy * Math.min(1.0, dAlong / Math.max(1.0, dist));
                    double wz = pz + uz * dAlong;
                    float[] proj = mc.projectWorldToScreen(wx, wy, wz);
                    if (proj != null && proj[2] > 0.2f) {
                        if (hasPrev) {
                            r.drawLine(prevSx, prevSy + 1.0f, proj[0], proj[1] + 1.0f, 3.2f, AxsesTheme.withAlpha(pathCol, 0.26f));
                            r.drawLine(prevSx, prevSy, proj[0], proj[1], 1.8f, AxsesTheme.withAlpha(pathCol, 0.92f));
                        }
                        r.fillRoundedRect(proj[0] - 2.2f, proj[1] - 2.2f, 4.4f, 4.4f, 2.2f, pathCol);
                        prevSx = proj[0];
                        prevSy = proj[1];
                        hasPrev = true;
                    } else {
                        hasPrev = false;
                    }
                }

                // Destination 3D Goal Beacon
                double bxWorld = px + ux * maxDrawDist;
                double bzWorld = pz + uz * maxDrawDist;
                float[] goalScreen = mc.projectWorldToScreen(bxWorld, py + 1.35, bzWorld);
                if (goalScreen != null && goalScreen[2] > 0.2f) {
                    boolean ru = i18n == null || i18n.isRussian();
                    String tag = String.format(Locale.ROOT, ru ? "⚡ БАРИТОН ЦЕЛЬ · %.0fм" : "⚡ BARITONE GOAL · %.0fm", Double.valueOf(dist));
                    float bw = fonts.width(r, tag, 0.84f) + 14.0f;
                    float bx = goalScreen[0] - bw * 0.5f;
                    float by = goalScreen[1] - 22.0f;
                    r.drawLine(goalScreen[0], goalScreen[1] - 4.0f, goalScreen[0], goalScreen[1] + 14.0f, 1.8f, AxsesTheme.withAlpha(pathCol, 0.80f));
                    r.fillRoundedRect(bx, by, bw, 16.0f, 8.0f, 0xE6050B18);
                    r.drawRoundedOutline(bx, by, bw, 16.0f, 8.0f, 1.1f, pathCol);
                    fonts.drawCentered(r, tag, goalScreen[0], by + 4.5f, 0xFFF8FAFF, 0.84f);
                }
            }

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                double[] g = resolveGoal(mc);
                double dist = mc != null ? Math.hypot(g[0] - mc.getPlayerX(), g[2] - mc.getPlayerZ()) : 0.0;
                return String.format(Locale.ROOT, "⚡ Baritone: %s [%.0fm]", baritoneTaskRef.getValue(), Double.valueOf(dist));
            }
        });
        baritoneMod.setFavorite(true);

        HudElement baritoneHudEl = new HudElement("baritone_bot", "Baritone Autopilot", baritoneMod, 0.986f, 0.540f, 238f, 38f)
                .withCustomPainter(new HudElement.CustomHudPainter() {
                    @Override
                    public boolean paint(RenderAdapter r, MinecraftAdapter mc, ThemeManager tm, FontManager fm,
                                         float px, float py, float sw, float sh, float opacity, boolean editorMode, boolean selected) {
                        if (fm == null || tm == null) return false;
                        AxsesTheme t = tm.getActiveTheme();
                        boolean ru = i18n == null || i18n.isRussian();
                        int acc = tm.filterColor(t.getAccentColor());
                        boolean paused = baritonePausedState[0];
                        int statusCol = paused ? 0xFFFFD166 : 0xFF00F5D4;

                        r.drawShadow(px, py, sw, sh, 10.0f, 0.50f * opacity, 0x000000);
                        r.fillRoundedRect(px, py, sw, sh, 10.0f, AxsesTheme.withAlpha(t.getBackgroundColor(), Math.min(0.96f, opacity + 0.04f)));
                        r.drawRoundedOutline(px, py, sw, sh, 10.0f, selected ? 1.4f : 1.0f, selected ? acc : AxsesTheme.withAlpha(statusCol, 0.82f));

                        String taskName = i18n != null ? i18n.optionName(baritoneTaskRef.getValue()) : baritoneTaskRef.getValue();
                        double gx = baritoneGoalXRef.getDouble();
                        double gz = baritoneGoalZRef.getDouble();
                        List<ClientRuntimeState.Waypoint> wps = state.getWaypoints();
                        if ("Goto Waypoint".equalsIgnoreCase(baritoneTaskRef.getValue()) && !wps.isEmpty()) {
                            ClientRuntimeState.Waypoint wp = wps.get(wps.size() - 1);
                            gx = wp.x;
                            gz = wp.z;
                        }
                        double dist = (mc != null && mc.isInWorld()) ? Math.hypot(gx - mc.getPlayerX(), gz - mc.getPlayerZ()) : 42.0;
                        boolean ext = mc != null && mc.isExternalBaritoneLoaded();

                        String hdr = (paused ? "|| " : "⚡ ") + (ru ? "БАРИТОН" : "BARITONE") + (ext ? " [API]" : " [AI]");
                        fm.draw(r, hdr, px + 8.0f, py + 6.0f, statusCol, 0.78f);
                        String sub = String.format(Locale.ROOT, "%.0f%s · %s", Double.valueOf(dist), ru ? "м" : "m", taskName);
                        while (sub.length() > 6 && fm.width(r, sub, 0.80f) > 124.0f) {
                            sub = sub.substring(0, sub.length() - 2) + "…";
                        }
                        fm.draw(r, sub, px + 8.0f, py + 19.0f, 0xFFF8FAFF, 0.80f);

                        float mx = (mc != null && (mc.isChatScreenOpen() || editorMode)) ? (float) mc.getScaledMouseX() : -999f;
                        float my = (mc != null && (mc.isChatScreenOpen() || editorMode)) ? (float) mc.getScaledMouseY() : -999f;
                        drawTactileHudBtn(r, fm, px + sw - 101.0f, py + 11.0f, 50.0f, 16.5f,
                                paused ? (ru ? "> Пуск" : "> Go") : (ru ? "|| Пауза" : "|| Pause"), true, statusCol, mx, my);
                        drawTactileHudBtn(r, fm, px + sw - 48.0f, py + 11.0f, 42.0f, 16.5f,
                                ru ? "■ Стоп" : "■ Stop", false, 0xFFFF4D6D, mx, my);
                        return true;
                    }
                })
                .withInteractiveHandler(new HudElement.InteractiveHudHandler() {
                    @Override
                    public boolean onMouseClick(MinecraftAdapter mc, float localX, float localY, float width, float height, int button) {
                        if (button != 0 || localY < 9.0f || localY > 30.0f) return false;
                        boolean ru = i18n == null || i18n.isRussian();
                        if (localX >= width - 102.0f && localX <= width - 50.0f) {
                            baritonePausedState[0] = !baritonePausedState[0];
                            if (mc != null && mc.isExternalBaritoneLoaded()) {
                                mc.sendChatOrCommand(baritonePausedState[0] ? "#pause" : "#resume");
                            }
                            if (notifications != null) {
                                notifications.push("Baritone",
                                        baritonePausedState[0] ? (ru ? "Маршрут на паузе" : "Pathing paused") : (ru ? "Маршрут продолжен" : "Pathing resumed"),
                                        NotificationManager.Severity.INFO);
                            }
                            return true;
                        }
                        if (localX >= width - 49.0f && localX <= width - 5.0f) {
                            baritoneMod.setEnabled(false, mc);
                            if (notifications != null) {
                                notifications.push("Baritone", ru ? "Автопилот остановлен (#stop)" : "Autopilot stopped (#stop)", NotificationManager.Severity.WARNING);
                            }
                            return true;
                        }
                        return false;
                    }
                });
        hm.register(baritoneHudEl);

        // 111. FPS & Chunk Optimizer (Турбо Оптимизация FPS + Детектор Sodium/Lithium/FerriteCore)
        Module fpsOptimizerMod = mm.register(new Module("fps_optimizer", "FPS Optimizer", "Client-side frametime stabilizer, entity distance culler & Sodium/Lithium/FerriteCore stack monitor", ModuleCategory.UTILITY, true, KeyCodes.KEY_NONE, false) {
            private final ModeSetting optMode = addSetting(new ModeSetting("opt_preset", "Optimization Mode", "Frametime & visual culling profile", "Ultra FPS", "Ultra FPS", "Balanced PvP", "Zero Stutter"));
            private final NumberSetting cullRadius = addSetting(new NumberSetting("cull_dist", "Entity Cull Radius", "Soft distance threshold for distant overlay culling", 56.0, 24.0, 128.0, 8.0));
            private final BooleanSetting showStack = addSetting(new BooleanSetting("show_mods", "Detect FPS Mods", "Display active Sodium / Lithium / FerriteCore / Iris stack on HUD", true));

            private String detectLoadedBoosters(MinecraftAdapter mc) {
                if (mc == null) return "Turbo";
                List<String> mods = mc.getLoadedMods();
                if (mods == null || mods.isEmpty()) return "Turbo";
                StringBuilder sb = new StringBuilder();
                String[] known = { "sodium", "lithium", "ferritecore", "immediatelyfast", "entityculling", "iris", "modernfix", "baritone" };
                String[] labels = { "Sodium", "Lithium", "Ferrite", "ImmFast", "Culling", "Iris", "ModFix", "Baritone" };
                int count = 0;
                for (int k = 0; k < known.length; k++) {
                    for (int m = 0; m < mods.size(); m++) {
                        if (mods.get(m).toLowerCase(Locale.ROOT).startsWith(known[k])) {
                            if (count > 0) sb.append('+');
                            sb.append(labels[k]);
                            count++;
                            break;
                        }
                    }
                    if (count >= 3) break;
                }
                return count > 0 ? sb.toString() : optMode.getValue();
            }

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                int fps = mc != null ? Math.max(1, mc.getFps()) : 240;
                float ms = 1000.0f / (float) fps;
                boolean ru = i18n == null || i18n.isRussian();
                String stack = showStack.isEnabled() ? detectLoadedBoosters(mc) : String.format(Locale.ROOT, "%.0fm", Double.valueOf(cullRadius.getDouble()));
                return String.format(Locale.ROOT, ru ? "⚡ %d FPS · %.1fмс [%s]" : "⚡ %d FPS · %.1fms [%s]",
                        Integer.valueOf(fps), Float.valueOf(ms), stack);
            }
        });
        fpsOptimizerMod.setFavorite(true);
        hm.register(new HudElement("fps_optimizer", "FPS Optimizer", fpsOptimizerMod, 0.195f, 0.078f, 178f, 20f));

        // 112. Live Chat HUD Mover (Перемещение HUD в Чате 'T')
        Module chatMoverMod = mm.register(new Module("chat_hud_drag", "Chat HUD Mover", "Drag, snap, lock & click any HUD widget directly inside the in-game Chat screen (T)", ModuleCategory.HUD, true, KeyCodes.KEY_NONE, false) {
            private final BooleanSetting snapGuides = addSetting(new BooleanSetting("snap_guides", "Magnetic Snap Lines", "Show glowing center alignment guides while dragging in Chat", true));
            private final BooleanSetting hoverFrames = addSetting(new BooleanSetting("hover_frames", "Hover Outlines", "Highlight HUD widgets when hovering with mouse in Chat", true));
            private final BooleanSetting topBanner = addSetting(new BooleanSetting("top_banner", "Chat Mode Hint Bar", "Show compact Chat HUD instructions at top of screen", true));
            private final BooleanSetting rmbLock = addSetting(new BooleanSetting("rmb_lock", "Right-Click Lock", "Toggle widget pin/lock with Right Click in Chat", true));

            private void sync() {
                hm.configureChatMover(isEnabled(), snapGuides.isEnabled(), hoverFrames.isEnabled(), topBanner.isEnabled(), rmbLock.isEnabled());
            }

            @Override
            public void onEnable(MinecraftAdapter mc) {
                sync();
            }

            @Override
            public void onDisable(MinecraftAdapter mc) {
                sync();
            }

            @Override
            public void onTick(MinecraftAdapter mc) {
                sync();
            }
        });
        chatMoverMod.setFavorite(true);
    }

    private static void drawTactileHudBtn(
            RenderAdapter r,
            FontManager fm,
            float x,
            float y,
            float w,
            float h,
            String label,
            boolean primary,
            int accentCol,
            float mx,
            float my
    ) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        float rad = Math.min(7.0f, h * 0.48f);
        int fill = primary
                ? AxsesTheme.withAlpha(accentCol, hov ? 0.44f : 0.28f)
                : (hov ? 0x38FFFFFF : 0x1EFFFFFF);
        int border = primary
                ? AxsesTheme.withAlpha(accentCol, hov ? 1.0f : 0.82f)
                : AxsesTheme.withAlpha(accentCol, hov ? 0.90f : 0.52f);
        r.fillRoundedRect(x, y, w, h, rad, fill);
        if (w > 10.0f) {
            r.fillRect(x + rad, y + 1.0f, w - rad * 2.0f, 1.0f, hov ? 0x42FFFFFF : 0x22FFFFFF);
        }
        r.drawRoundedOutline(x, y, w, h, rad, 1.0f, border);
        fm.drawCentered(r, label, x + w * 0.5f, y + (h - 7.0f) * 0.5f, 0xFFFFFFFF, 0.76f);
    }
}
