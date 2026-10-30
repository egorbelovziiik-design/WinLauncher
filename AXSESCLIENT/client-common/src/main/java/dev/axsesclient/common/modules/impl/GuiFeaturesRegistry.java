package dev.axsesclient.common.modules.impl;

import dev.axsesclient.common.animation.AnimationManager;
import dev.axsesclient.common.animation.Easing;
import dev.axsesclient.common.compat.CompatibilityEngine;
import dev.axsesclient.common.config.ConfigManager;
import dev.axsesclient.common.hud.HudManager;
import dev.axsesclient.common.keybinds.KeybindManager;
import dev.axsesclient.common.modules.Module;
import dev.axsesclient.common.modules.ModuleCategory;
import dev.axsesclient.common.modules.ModuleManager;
import dev.axsesclient.common.notifications.NotificationManager;
import dev.axsesclient.common.platform.InputAdapter;
import dev.axsesclient.common.platform.KeyCodes;
import dev.axsesclient.common.platform.MinecraftAdapter;
import dev.axsesclient.common.render.FontManager;
import dev.axsesclient.common.render.RenderManager;
import dev.axsesclient.common.settings.BooleanSetting;
import dev.axsesclient.common.settings.ModeSetting;
import dev.axsesclient.common.settings.NumberSetting;
import dev.axsesclient.common.theme.ColorBlindMode;
import dev.axsesclient.common.theme.ThemeManager;
import java.util.List;
import java.util.Locale;

public final class GuiFeaturesRegistry {
    private GuiFeaturesRegistry() {
    }

    public static void registerAll(
            final ModuleManager mm,
            final HudManager hm,
            final ThemeManager themes,
            final ConfigManager configs,
            final KeybindManager keybinds,
            final NotificationManager notifications,
            final AnimationManager animations,
            final FontManager fonts,
            final RenderManager renderManager,
            final CompatibilityEngine compat,
            final InputAdapter input
    ) {
        // 75. GUI Scale Manager
        mm.register(new Module("gui_scale_manager", "GUI Scale Manager", "Controls AXSES ClickGUI independent scale factor (0.75x - 1.50x)", ModuleCategory.SETTINGS, true, KeyCodes.KEY_NONE, false) {
            private final NumberSetting scale = addSetting(new NumberSetting("scale", "ClickGUI Scale", "UI scale multiplier", 1.00, 0.75, 1.50, 0.05));

            @Override
            public void onTick(MinecraftAdapter mc) {
                configs.setGuiScale(scale.getFloat());
            }
        });

        // 76. Theme Preview
        mm.register(new Module("theme_preview", "Theme Preview", "Live-previews built-in NEO-DARK color themes before applying", ModuleCategory.SETTINGS) {
            private final ModeSetting previewId = addSetting(new ModeSetting("preview_id", "Preview Theme", "Theme to preview live", "axses-dark",
                    "axses-dark", "axses-purple", "axses-blue", "axses-cyber",
                    "axses-midnight", "axses-minimal", "axses-graphite", "axses-neon"));

            @Override
            public void onTick(MinecraftAdapter mc) {
                themes.setPreviewThemeId(previewId.getValue());
            }

            @Override
            public void onDisable(MinecraftAdapter mc) {
                themes.setPreviewThemeId(null);
            }
        });

        // 77. Module Search History
        mm.register(new Module("module_search_history", "Module Search History", "Maintains clickable recent search chips inside ClickGUI", ModuleCategory.SETTINGS, true, KeyCodes.KEY_NONE, false) {
            private final BooleanSetting clearNow = addSetting(new BooleanSetting("clear_now", "Clear History On Toggle", "Wipe recent search queries when toggled", false));

            @Override
            public void onEnable(MinecraftAdapter mc) {
                if (clearNow.isEnabled()) {
                    mm.clearSearchHistory();
                    clearNow.setValue(Boolean.FALSE);
                    notifications.push("Search History", "Cleared recent queries", NotificationManager.Severity.INFO);
                }
            }
        });

        // 78. Favorites Manager
        mm.register(new Module("favorites_manager", "Favorites Manager", "Manages pinned favorite modules and quick-star sorting", ModuleCategory.SETTINGS, true, KeyCodes.KEY_NONE, false) {
            private final BooleanSetting sortFavoritesFirst = addSetting(new BooleanSetting("fav_first", "Pin Favorites First", "Sort starred modules to top of categories", false));

            @Override
            public void onTick(MinecraftAdapter mc) {
                if (sortFavoritesFirst.isEnabled() && mm.getSortOrder() != ModuleManager.SortOrder.FAVORITES_FIRST) {
                    mm.setSortOrder(ModuleManager.SortOrder.FAVORITES_FIRST);
                }
            }
        });

        // 79. Keyboard Shortcut Editor
        mm.register(new Module("keyboard_shortcut_editor", "Keyboard Shortcut Editor", "Configures global ClickGUI and HUD Editor hotkeys with conflict checking", ModuleCategory.SETTINGS, true, KeyCodes.KEY_NONE, false) {
            private final BooleanSetting alertConflicts = addSetting(new BooleanSetting("alert_conflicts", "Warn On Conflict", "Notify if two binds share a key", true));
            private int lastConflictCount = 0;

            @Override
            public void onTick(MinecraftAdapter mc) {
                if (!alertConflicts.isEnabled()) return;
                int count = keybinds.detectConflicts(input).size();
                if (count > lastConflictCount) {
                    notifications.push("Keybind Conflict", count + " conflicting keybind(s) detected", NotificationManager.Severity.WARNING);
                }
                lastConflictCount = count;
            }
        });

        // 80. Notification History
        mm.register(new Module("notification_history", "Notification History", "Retains scrollable log of up to 200 session notifications", ModuleCategory.SETTINGS, true, KeyCodes.KEY_NONE, false) {
            private final BooleanSetting clearLog = addSetting(new BooleanSetting("clear_log", "Clear History Now", "Purge stored notification history", false));

            @Override
            public void onTick(MinecraftAdapter mc) {
                if (clearLog.isEnabled()) {
                    notifications.clearHistory();
                    clearLog.setValue(Boolean.FALSE);
                }
            }
        });

        // 81. Interface Presets
        mm.register(new Module("interface_presets", "Interface Presets", "Applies cohesive ClickGUI scale, contrast, and corner presets", ModuleCategory.SETTINGS) {
            private final ModeSetting preset = addSetting(new ModeSetting("preset", "UI Preset", "ClickGUI layout preset", "Standard", "Compact", "Standard", "Spacious", "High Contrast"));

            @Override
            public void onEnable(MinecraftAdapter mc) {
                applyPreset();
            }

            @Override
            public void onTick(MinecraftAdapter mc) {
                applyPreset();
            }

            private void applyPreset() {
                String p = preset.getValue();
                if ("Compact".equals(p)) {
                    configs.setGuiScale(0.85f);
                    renderManager.setHighContrastBorders(false);
                } else if ("Spacious".equals(p)) {
                    configs.setGuiScale(1.15f);
                    renderManager.setHighContrastBorders(false);
                } else if ("High Contrast".equals(p)) {
                    configs.setGuiScale(1.05f);
                    renderManager.setHighContrastBorders(true);
                } else {
                    configs.setGuiScale(1.00f);
                    renderManager.setHighContrastBorders(false);
                }
            }
        });

        // 82. HUD Presets
        mm.register(new Module("hud_presets", "HUD Presets", "Applies curated HUD widget arrangements (Minimal, Competitive, Explorer, Telemetry)", ModuleCategory.HUD) {
            private final ModeSetting layout = addSetting(new ModeSetting("layout", "HUD Layout Preset", "Target HUD arrangement", "Competitive", "Minimal", "Competitive", "Explorer", "Telemetry"));

            @Override
            public void onEnable(MinecraftAdapter mc) {
                hm.applyPreset(layout.getValue(), mc);
                notifications.push("HUD Preset Applied", layout.getValue(), NotificationManager.Severity.SUCCESS);
                setEnabled(false, mc);
            }
        });

        // 83. Animation Settings
        mm.register(new Module("animation_settings", "Animation Settings", "Controls global Animation Engine speed, intensity, and easing curve", ModuleCategory.SETTINGS, true, KeyCodes.KEY_NONE, false) {
            private final NumberSetting speed = addSetting(new NumberSetting("speed", "Animation Speed", "Global animation speed multiplier", 1.0, 0.25, 3.0, 0.05));
            private final NumberSetting intensity = addSetting(new NumberSetting("intensity", "Animation Intensity", "Slide and scale amplitude", 1.0, 0.0, 1.0, 0.05));
            private final ModeSetting easing = addSetting(new ModeSetting("easing", "Easing Function", "Interpolation curve", "EASE_OUT_CUBIC",
                    "LINEAR", "EASE_OUT_CUBIC", "EASE_IN_OUT_CUBIC", "EASE_OUT_QUINT", "EASE_OUT_BACK", "SPRING_SMOOTH"));

            @Override
            public void onTick(MinecraftAdapter mc) {
                animations.setAnimationSpeed(speed.getFloat());
                animations.setAnimationIntensity(intensity.getFloat());
                try {
                    animations.setDefaultEasing(Easing.valueOf(easing.getValue()));
                } catch (Exception ignored) {
                }
            }
        });

        // 84. Accessibility Settings
        mm.register(new Module("accessibility_settings", "Accessibility Settings", "High-contrast card outlines and crisp text shadow controls", ModuleCategory.SETTINGS) {
            private final BooleanSetting highContrast = addSetting(new BooleanSetting("high_contrast", "High-Contrast Borders", "Strengthen card border visibility", true));
            private final BooleanSetting textShadow = addSetting(new BooleanSetting("text_shadow", "Crisp Text Drop Shadow", "Enable drop shadow behind UI text", true));

            @Override
            public void onTick(MinecraftAdapter mc) {
                renderManager.setHighContrastBorders(highContrast.isEnabled());
                fonts.setTextShadow(textShadow.isEnabled());
            }

            @Override
            public void onDisable(MinecraftAdapter mc) {
                renderManager.setHighContrastBorders(false);
            }
        });

        // 85. Color Blindness Presets
        mm.register(new Module("color_blindness_presets", "Color Blindness Presets", "Applies perceptual color matrix filters across UI and HUD accents", ModuleCategory.SETTINGS) {
            private final ModeSetting mode = addSetting(new ModeSetting("mode", "Vision Filter", "Color vision compensation matrix", "NONE", "NONE", "PROTANOPIA", "DEUTERANOPIA", "TRITANOPIA"));

            @Override
            public void onTick(MinecraftAdapter mc) {
                try {
                    themes.setColorBlindMode(ColorBlindMode.valueOf(mode.getValue().toUpperCase(Locale.ROOT)));
                } catch (Exception ignored) {
                }
            }

            @Override
            public void onDisable(MinecraftAdapter mc) {
                themes.setColorBlindMode(ColorBlindMode.NONE);
            }
        });

        // 86. Font Size Settings
        mm.register(new Module("font_size_settings", "Font Size Settings", "Adjusts typography scale across ClickGUI and HUD elements", ModuleCategory.SETTINGS, true, KeyCodes.KEY_NONE, false) {
            private final ModeSetting scale = addSetting(new ModeSetting("font_scale", "Typography Scale", "Small (90%), Normal (100%), or Large (115%)", "NORMAL", "SMALL", "NORMAL", "LARGE"));

            @Override
            public void onTick(MinecraftAdapter mc) {
                try {
                    fonts.setFontScale(FontManager.FontScale.valueOf(scale.getValue().toUpperCase(Locale.ROOT)));
                } catch (Exception ignored) {
                }
            }
        });

        // 87. Reduced Motion Mode
        mm.register(new Module("reduced_motion_mode", "Reduced Motion Mode", "Disables sliding and elastic motion for instant low-distraction transitions", ModuleCategory.SETTINGS) {
            private final BooleanSetting instantTransitions = addSetting(new BooleanSetting("instant", "Instant State Transitions", "Snap animations immediately to target", true));

            @Override
            public void onEnable(MinecraftAdapter mc) {
                animations.setReducedMotion(instantTransitions.isEnabled());
            }

            @Override
            public void onTick(MinecraftAdapter mc) {
                animations.setReducedMotion(instantTransitions.isEnabled());
            }

            @Override
            public void onDisable(MinecraftAdapter mc) {
                animations.setReducedMotion(false);
            }
        });

        // 88. Interface Reset
        mm.register(new Module("interface_reset", "Interface Reset", "Restores ClickGUI position, scale, theme, and HUD layout to factory defaults", ModuleCategory.SETTINGS) {
            private final BooleanSetting confirmReset = addSetting(new BooleanSetting("confirm", "Confirm Factory UI Reset", "Enable this confirmation before toggling Interface Reset", false));

            @Override
            public void onEnable(MinecraftAdapter mc) {
                if (confirmReset.isEnabled()) {
                    configs.setGuiScale(1.0f);
                    configs.setGuiOffsetX(0f);
                    configs.setGuiOffsetY(0f);
                    themes.resetThemes();
                    hm.resetAll();
                    animations.reset();
                    confirmReset.setValue(Boolean.FALSE);
                    notifications.push("Interface Reset", "Restored factory UI, theme, and HUD defaults", NotificationManager.Severity.SUCCESS);
                } else {
                    notifications.push("Confirmation Required", "Enable 'Confirm Factory UI Reset' in module settings first", NotificationManager.Severity.WARNING);
                }
                setEnabled(false, mc);
            }
        });

        // 89. Config Backup Manager
        mm.register(new Module("config_backup_manager", "Config Backup Manager", "Creates timestamped configuration backups and restores latest snapshot", ModuleCategory.SETTINGS) {
            private final ModeSetting action = addSetting(new ModeSetting("action", "Action On Enable", "Create snapshot or restore newest backup", "Create Snapshot", "Create Snapshot", "Restore Latest Backup"));

            @Override
            public void onEnable(MinecraftAdapter mc) {
                if ("Create Snapshot".equals(action.getValue())) {
                    configs.createBackup();
                } else {
                    List<String> backups = configs.listBackups();
                    if (!backups.isEmpty()) {
                        configs.restoreBackup(backups.get(0), mc);
                        notifications.push("Backup Restored", backups.get(0), NotificationManager.Severity.SUCCESS);
                    } else {
                        notifications.push("No Backups Found", "Create a snapshot first", NotificationManager.Severity.WARNING);
                    }
                }
                setEnabled(false, mc);
            }
        });

        // 90. Module Compatibility Viewer
        mm.register(new Module("module_compatibility_viewer", "Module Compatibility Viewer", "Generates live runtime Compatibility Report across MC, Java, Fabric, and Mods", ModuleCategory.SETTINGS) {
            private final BooleanSetting exportJson = addSetting(new BooleanSetting("export_json", "Write Report JSON", "Save compatibility-report.json on refresh", true));

            @Override
            public void onEnable(MinecraftAdapter mc) {
                CompatibilityEngine.CompatibilityReport rep = compat.evaluate(mc, input, mm, keybinds);
                if (exportJson.isEnabled() && mc != null && mc.getGameDir() != null) {
                    compat.writeReportToDisk(mc.getGameDir().resolve("axsesclient"));
                }
                notifications.push(
                        "Compatibility " + (rep.isHealthy() ? "OK" : "Notice"),
                        "MC " + rep.getMinecraftVersion() + " · Java " + rep.getJavaVersion() + " · " + rep.getLoadedMods().size() + " mods",
                        rep.isHealthy() ? NotificationManager.Severity.SUCCESS : NotificationManager.Severity.WARNING
                );
                setEnabled(false, mc);
            }
        });
    }
}
