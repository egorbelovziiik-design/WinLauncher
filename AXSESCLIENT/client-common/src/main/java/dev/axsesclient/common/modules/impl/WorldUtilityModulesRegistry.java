package dev.axsesclient.common.modules.impl;

import dev.axsesclient.common.hud.HudElement;
import dev.axsesclient.common.hud.HudManager;
import dev.axsesclient.common.modules.Module;
import dev.axsesclient.common.modules.ModuleCategory;
import dev.axsesclient.common.modules.ModuleManager;
import dev.axsesclient.common.notifications.NotificationManager;
import dev.axsesclient.common.platform.ItemInfo;
import dev.axsesclient.common.platform.KeyCodes;
import dev.axsesclient.common.platform.MinecraftAdapter;
import dev.axsesclient.common.settings.BooleanSetting;
import dev.axsesclient.common.settings.ColorSetting;
import dev.axsesclient.common.settings.ModeSetting;
import dev.axsesclient.common.settings.NumberSetting;
import dev.axsesclient.common.settings.StringSetting;
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
        // 55. Waypoints
        Module waypointsMod = mm.register(new Module("waypoints", "Waypoints", "Creates and tracks local coordinates with live distance readout", ModuleCategory.WORLD) {
            private final StringSetting label = addSetting(new StringSetting("wp_name", "New Waypoint Label", "Label saved when enabling or marking", "Home Base", 24));
            private final ColorSetting color = addSetting(new ColorSetting("wp_color", "Marker Color", "Waypoint badge color", 0xFF00D9FF));
            private final BooleanSetting saveCurrentOnEnable = addSetting(new BooleanSetting("mark_on_enable", "Mark Position On Enable", "Bookmark current player coordinates when enabled", false));

            @Override
            public void onEnable(MinecraftAdapter mc) {
                if (saveCurrentOnEnable.isEnabled() && mc != null && mc.isInWorld()) {
                    state.addOrReplaceWaypoint(new ClientRuntimeState.Waypoint(
                            label.getValue(), mc.getPlayerX(), mc.getPlayerY(), mc.getPlayerZ(),
                            mc.getDimensionId(), mc.getWorldOrServerName(), color.getArgb()
                    ));
                    notifications.push("Waypoint Saved", label.getValue(), NotificationManager.Severity.SUCCESS);
                }
            }

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                List<ClientRuntimeState.Waypoint> wps = state.getWaypoints();
                if (wps.isEmpty()) return "Waypoints: 0 saved";
                ClientRuntimeState.Waypoint top = wps.get(wps.size() - 1);
                if (mc != null && mc.isInWorld()) {
                    double dist = Math.hypot(mc.getPlayerX() - top.x, mc.getPlayerZ() - top.z);
                    return String.format(Locale.ROOT, "WP %s [%.0fm]", top.name, Double.valueOf(dist));
                }
                return String.format(Locale.ROOT, "WP %s (%.0f, %.0f, %.0f)", top.name, Double.valueOf(top.x), Double.valueOf(top.y), Double.valueOf(top.z));
            }
        });
        hm.register(new HudElement("waypoints", "Waypoints", waypointsMod, 0.015f, 0.67f, 152f, 20f));

        // 56. Death Location
        Module deathMod = mm.register(new Module("death_location", "Death Location", "Automatically logs last death coordinates and distance back", ModuleCategory.WORLD, true, KeyCodes.KEY_NONE, false) {
            private final BooleanSetting toastAlert = addSetting(new BooleanSetting("toast", "Show Notification", "Notify coordinates upon respawning", true));
            private boolean wasDead = false;

            @Override
            public void onTick(MinecraftAdapter mc) {
                if (mc == null || !mc.isInWorld()) return;
                boolean dead = mc.getHealth() <= 0.0f;
                if (dead && !wasDead) {
                    wasDead = true;
                    state.recordDeath(mc.getPlayerX(), mc.getPlayerY(), mc.getPlayerZ(), mc.getDimensionId(), mc.getWorldOrServerName());
                    if (toastAlert.isEnabled()) {
                        notifications.push("Death Recorded",
                                String.format(Locale.ROOT, "XYZ %.0f, %.0f, %.0f", Double.valueOf(mc.getPlayerX()), Double.valueOf(mc.getPlayerY()), Double.valueOf(mc.getPlayerZ())),
                                NotificationManager.Severity.WARNING);
                    }
                } else if (!dead) {
                    wasDead = false;
                }
            }

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                ClientRuntimeState.Waypoint d = state.getLastDeathWaypoint();
                if (d == null) return null;
                return String.format(Locale.ROOT, "Last Death: %.0f, %.0f, %.0f", Double.valueOf(d.x), Double.valueOf(d.y), Double.valueOf(d.z));
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
    }
}
