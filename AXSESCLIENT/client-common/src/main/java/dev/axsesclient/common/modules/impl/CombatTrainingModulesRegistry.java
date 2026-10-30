package dev.axsesclient.common.modules.impl;

import dev.axsesclient.common.hud.HudElement;
import dev.axsesclient.common.hud.HudManager;
import dev.axsesclient.common.modules.Module;
import dev.axsesclient.common.modules.ModuleCategory;
import dev.axsesclient.common.modules.ModuleManager;
import dev.axsesclient.common.notifications.NotificationManager;
import dev.axsesclient.common.platform.KeyCodes;
import dev.axsesclient.common.platform.MinecraftAdapter;
import dev.axsesclient.common.platform.RenderAdapter;
import dev.axsesclient.common.render.FontManager;
import dev.axsesclient.common.settings.BooleanSetting;
import dev.axsesclient.common.settings.ColorSetting;
import dev.axsesclient.common.settings.ModeSetting;
import dev.axsesclient.common.settings.NumberSetting;
import dev.axsesclient.common.theme.AxsesTheme;
import dev.axsesclient.common.theme.ThemeManager;
import java.util.Locale;

public final class CombatTrainingModulesRegistry {
    private CombatTrainingModulesRegistry() {
    }

    public static void registerAll(
            final ModuleManager mm,
            HudManager hm,
            final ClientRuntimeState state,
            final ThemeManager themes,
            final FontManager fonts,
            final NotificationManager notifications
    ) {
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
    }
}
