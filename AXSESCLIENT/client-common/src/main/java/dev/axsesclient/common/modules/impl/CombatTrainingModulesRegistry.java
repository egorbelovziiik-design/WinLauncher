package dev.axsesclient.common.modules.impl;

import dev.axsesclient.common.hud.HudElement;
import dev.axsesclient.common.hud.HudManager;
import dev.axsesclient.common.i18n.LocalizationManager;
import dev.axsesclient.common.modules.Module;
import dev.axsesclient.common.modules.ModuleCategory;
import dev.axsesclient.common.modules.ModuleManager;
import dev.axsesclient.common.notifications.NotificationManager;
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
        // NEW FLAGSHIP 01: KillAura (КиллАура)
        Module killAura = mm.register(new Module("kill_aura", "KillAura", "Smart 1.9+ cooldown combat aura with target priority, filters, and smooth rotations", ModuleCategory.COMBAT_TRAINING, false, KeyCodes.KEY_R, false) {
            private final NumberSetting range = addSetting(new NumberSetting("range", "Range", "Attack reach radius in blocks", 4.2, 2.5, 6.0, 0.1));
            private final ModeSetting attackMode = addSetting(new ModeSetting("attack_mode", "Timing Mode", "Weapon recharge vs fixed CPS", "1.9+ Cooldown", "1.9+ Cooldown", "CPS Timer"));
            private final NumberSetting cps = addSetting(new NumberSetting("cps", "CPS Speed", "Clicks per second in CPS Timer mode", 11.0, 4.0, 18.0, 1.0));
            private final ModeSetting priority = addSetting(new ModeSetting("priority", "Priority", "Target selection priority", "Distance", "Distance", "Lowest HP", "Angle"));
            private final ModeSetting rotations = addSetting(new ModeSetting("rotations", "Rotations", "Camera aim assist behavior", "Silent", "Silent", "Smooth", "Snap"));
            private final BooleanSetting targetPlayers = addSetting(new BooleanSetting("players", "Target Players", "Attack nearby players", true));
            private final BooleanSetting targetHostiles = addSetting(new BooleanSetting("hostiles", "Target Monsters", "Attack hostile mobs", true));
            private final BooleanSetting targetAnimals = addSetting(new BooleanSetting("animals", "Target Animals", "Attack passive/neutral mobs", false));
            private final BooleanSetting pauseInGui = addSetting(new BooleanSetting("pause_gui", "Pause In Menus", "Do not attack while inventory or GUI is open", true));
            private final BooleanSetting disableOnDeath = addSetting(new BooleanSetting("disable_death", "Disable On Death", "Automatically turn off KillAura on death", true));
            private final BooleanSetting showLockBadge = addSetting(new BooleanSetting("lock_badge", "Crosshair Indicator", "Show locked target badge under crosshair", true));

            private long lastSwingMs = 0L;
            private TargetEntityInfo lockedNow = null;

            @Override
            public void onDisable(MinecraftAdapter mc) {
                lockedNow = null;
            }

            @Override
            public void onTick(MinecraftAdapter mc) {
                if (mc == null || !mc.isInWorld()) {
                    lockedNow = null;
                    return;
                }
                if (disableOnDeath.isEnabled() && mc.getHealth() <= 0.0f) {
                    lockedNow = null;
                    setEnabled(false, mc);
                    notifications.push("KillAura", i18n.isRussian() ? "Отключена после смерти" : "Auto-disabled on death", NotificationManager.Severity.WARNING);
                    return;
                }
                if (pauseInGui.isEnabled() && mc.isAnyScreenOpen()) {
                    lockedNow = null;
                    return;
                }

                List<TargetEntityInfo> candidates = mc.getNearbyTargets(
                        range.getDouble(),
                        targetPlayers.isEnabled(),
                        targetHostiles.isEnabled(),
                        targetAnimals.isEnabled(),
                        false
                );
                if (candidates == null || candidates.isEmpty()) {
                    lockedNow = null;
                    return;
                }

                TargetEntityInfo best = selectBest(mc, candidates, priority.getValue());
                if (best == null) {
                    lockedNow = null;
                    return;
                }

                lockedNow = best;
                state.lockTarget(best);

                String rotMode = rotations.getValue();
                if (!"Silent".equalsIgnoreCase(rotMode)) {
                    float[] angles = computeLookAngles(mc, best);
                    if ("Snap".equalsIgnoreCase(rotMode)) {
                        mc.setPlayerRotation(angles[0], angles[1]);
                    } else {
                        float curYaw = mc.getPlayerYaw();
                        float curPitch = mc.getPlayerPitch();
                        float dyaw = wrapDegrees(angles[0] - curYaw);
                        float dpitch = angles[1] - curPitch;
                        mc.setPlayerRotation(curYaw + dyaw * 0.42f, clampPitch(curPitch + dpitch * 0.42f));
                    }
                }

                long now = System.currentTimeMillis();
                boolean ready;
                if ("CPS Timer".equalsIgnoreCase(attackMode.getValue())) {
                    long delay = Math.max(55L, Math.round(1000.0 / Math.max(1.0, cps.getDouble())));
                    ready = (now - lastSwingMs) >= delay;
                } else {
                    ready = mc.getAttackCooldownProgress() >= 0.91f && (now - lastSwingMs) >= 95L;
                }

                if (ready) {
                    float cd = mc.getAttackCooldownProgress();
                    boolean crit = !mc.isOnGround() && cd >= 0.90f;
                    if (mc.attackEntityById(best.getEntityId(), true)) {
                        lastSwingMs = now;
                        state.recordAttack(cd, true, crit);
                    }
                }
            }

            @Override
            public void onRender(RenderAdapter r, MinecraftAdapter mc, float dt) {
                if (!showLockBadge.isEnabled() || lockedNow == null || mc == null || !mc.isInWorld() || mc.isClickGuiOpen()) {
                    return;
                }
                AxsesTheme t = themes.getActiveTheme();
                String label = "⚡ " + lockedNow.getName() + String.format(Locale.ROOT, " · %.1fm", Float.valueOf(lockedNow.getDistance()));
                float tw = fonts.width(r, label, 1.0f);
                float bw = tw + 14.0f;
                float bx = (r.getScaledWidth() - bw) * 0.5f;
                float by = r.getScaledHeight() * 0.5f + 16.0f;
                r.fillRoundedRect(bx, by, bw, 16.0f, 4.0f, AxsesTheme.withAlpha(t.getBackgroundColor(), 0.86f));
                r.drawRoundedOutline(bx, by, bw, 16.0f, 4.0f, 1.0f, themes.filterColor(t.getAccentColor()));
                fonts.draw(r, label, bx + 7.0f, by + 4.0f, t.getTextColor(), 1.0f);
            }
        });
        killAura.setFavorite(true);

        // NEW FLAGSHIP 02: TriggerBot (ТриггерБот)
        mm.register(new Module("trigger_bot", "TriggerBot", "Automatically strikes crosshair target at optimal weapon recharge", ModuleCategory.COMBAT_TRAINING, false, KeyCodes.KEY_NONE, false) {
            private final NumberSetting minCooldown = addSetting(new NumberSetting("min_cd", "Min Recharge %", "Minimum weapon cooldown % to swing", 92.0, 50.0, 100.0, 2.0));
            private final BooleanSetting players = addSetting(new BooleanSetting("players", "Players", "Attack players in crosshair", true));
            private final BooleanSetting hostiles = addSetting(new BooleanSetting("hostiles", "Monsters", "Attack monsters in crosshair", true));
            private final BooleanSetting animals = addSetting(new BooleanSetting("animals", "Animals", "Attack animals in crosshair", false));
            private long lastStrikeMs = 0L;

            @Override
            public void onTick(MinecraftAdapter mc) {
                if (mc == null || !mc.isInWorld() || mc.isAnyScreenOpen()) return;
                TargetEntityInfo xhair = mc.getCrosshairTarget();
                if (xhair == null) return;
                if (xhair.isPlayer() && !players.isEnabled()) return;
                if (xhair.isHostile() && !hostiles.isEnabled()) return;
                if (xhair.isPassive() && !animals.isEnabled()) return;

                float req = minCooldown.getFloat() / 100.0f;
                long now = System.currentTimeMillis();
                if (mc.getAttackCooldownProgress() >= req && (now - lastStrikeMs) >= 100L) {
                    float cd = mc.getAttackCooldownProgress();
                    if (mc.attackEntityById(xhair.getEntityId(), true)) {
                        lastStrikeMs = now;
                        state.lockTarget(xhair);
                        state.recordAttack(cd, true, !mc.isOnGround() && cd >= 0.90f);
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

        HudElement targetHudElement = new HudElement("target_hud", "Target HUD", registeredTargetHud, 0.60f, 0.58f, 198f, 54f)
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
                                    16,
                                    0.0, 64.0, 0.0, 65.6
                            );
                            preview = true;
                        }
                        if (target == null) {
                            return false;
                        }

                        AxsesTheme t = themeManager.getActiveTheme();
                        float cardW = Math.max(194.0f, sw);
                        float cardH = Math.max(54.0f, sh);
                        float drawX = Math.min(px, Math.max(4.0f, r.getScaledWidth() - cardW - 4.0f));
                        float drawY = Math.min(py, Math.max(4.0f, r.getScaledHeight() - cardH - 4.0f));

                        boolean hurt = target.getHurtTime() > 0;
                        float hurtRatio = Math.min(1.0f, target.getHurtTime() / 10.0f);

                        // Outer drop shadow + deep Obsidian glass card
                        r.drawShadow(drawX, drawY, cardW, cardH, 7.0f, 0.70f * opacity, 0x000000);
                        int bgCol = AxsesTheme.withAlpha(t.getBackgroundColor(), Math.min(0.95f, opacity + 0.05f));
                        r.fillRoundedRect(drawX, drawY, cardW, cardH, 7.0f, bgCol);

                        // Top glowing Aurora bar
                        int topBarCol = hurt
                                ? t.getErrorColor()
                                : themeManager.filterColor(t.getPrimaryColor());
                        r.fillRect(drawX + 6.0f, drawY + 1.0f, cardW - 12.0f, 2.0f, AxsesTheme.withAlpha(topBarCol, opacity));

                        int borderCol = selected
                                ? themeManager.filterColor(t.getAccentColor())
                                : (hurt ? t.getErrorColor() : AxsesTheme.withAlpha(themeManager.filterColor(t.getPrimaryColor()), 0.72f * opacity));
                        r.drawRoundedOutline(drawX, drawY, cardW, cardH, 7.0f, 1.0f, borderCol);

                        // Left stylized 3D-inspired entity avatar emblem (36x36)
                        float avX = drawX + 8.0f;
                        float avY = drawY + 9.0f;
                        float avS = 36.0f;
                        int avBase = target.isPlayer() ? themeManager.filterColor(t.getPrimaryColor()) : themeManager.filterColor(t.getAccentColor());
                        if (hurt) {
                            avBase = RenderManager.blendArgb(avBase, t.getErrorColor(), 0.72f * hurtRatio);
                        }
                        r.fillRoundedRect(avX, avY, avS, avS, 6.0f, AxsesTheme.withAlpha(t.getElevatedColor(), 0.96f));
                        r.fillRoundedRect(avX + 3.0f, avY + 3.0f, avS - 6.0f, avS - 6.0f, 4.0f, AxsesTheme.withAlpha(avBase, 0.32f));
                        // Pixel-crisp geometric head & visor icon inside avatar box
                        r.fillRect(avX + 9.0f, avY + 7.0f, 18.0f, 16.0f, AxsesTheme.withAlpha(avBase, 0.92f));
                        r.fillRect(avX + 12.0f, avY + 12.0f, 4.0f, 3.0f, 0xFFF7F8FF);
                        r.fillRect(avX + 20.0f, avY + 12.0f, 4.0f, 3.0f, 0xFFF7F8FF);
                        r.fillRect(avX + 11.0f, avY + 25.0f, 14.0f, 5.0f, AxsesTheme.withAlpha(avBase, 0.65f));
                        r.drawRoundedOutline(avX, avY, avS, avS, 6.0f, 1.0f, AxsesTheme.withAlpha(avBase, 0.85f));

                        // Row 1: Target Name + Distance tag
                        float infoX = avX + avS + 8.0f;
                        float infoW = cardW - (infoX - drawX) - 8.0f;
                        String distUnit = i18n.isRussian() ? "м" : "m";
                        String distStr = preview ? "DEMO" : String.format(Locale.ROOT, "%.1f%s", Float.valueOf(target.getDistance()), distUnit);
                        float distW = fontManager.width(r, distStr, 1.0f);

                        String cleanName = trimText(r, fontManager, target.getName(), Math.max(40.0f, infoW - distW - 8.0f));
                        fontManager.draw(r, cleanName, infoX, drawY + 8.0f, t.getTextColor(), 1.0f);
                        fontManager.draw(r, distStr, drawX + cardW - distW - 8.0f, drawY + 8.0f, themeManager.filterColor(t.getAccentColor()), 1.0f);

                        // Row 2: HP numbers + Combat Advantage Status
                        float hpVal = target.getHealth() + target.getAbsorption();
                        String hpText = String.format(Locale.ROOT, "%.1f / %.0f HP", Float.valueOf(hpVal), Float.valueOf(target.getMaxHealth()));
                        fontManager.draw(r, hpText, infoX, drawY + 21.0f, t.getMutedColor(), 1.0f);

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
                            float bw = fontManager.width(r, badge, 1.0f);
                            if (infoX + fontManager.width(r, hpText, 1.0f) + 8.0f + bw <= drawX + cardW - 6.0f) {
                                fontManager.draw(r, badge, drawX + cardW - bw - 8.0f, drawY + 21.0f, statusCol, 1.0f);
                            }
                        }

                        // Row 3: Dual-Layer Smooth Health Bar + Damage Trail
                        float barX = infoX;
                        float barY = drawY + 36.0f;
                        float barW = infoW;
                        float barH = 9.0f;
                        float targetFrac = target.getHealthFraction();
                        float animFrac = preview ? targetFrac : state.updateAndGetAnimatedHpFrac(targetFrac, dt);
                        float trailFrac = preview ? Math.min(1.0f, targetFrac + 0.12f) : state.getDamageTrailHpFrac();

                        r.fillRoundedRect(barX, barY, barW, barH, 4.0f, AxsesTheme.withAlpha(t.getElevatedColor(), 0.95f));
                        if (trailFrac > animFrac + 0.005f) {
                            r.fillRoundedRect(barX, barY, Math.max(4.0f, barW * trailFrac), barH, 4.0f, AxsesTheme.withAlpha(t.getErrorColor(), 0.65f));
                        }
                        int hpFillCol = animFrac > 0.55f
                                ? themeManager.filterColor(t.getSuccessColor())
                                : (animFrac > 0.25f ? t.getWarningColor() : t.getErrorColor());
                        if (animFrac > 0.005f) {
                            r.fillRoundedRect(barX, barY, Math.max(4.0f, barW * animFrac), barH, 4.0f, hpFillCol);
                        }
                        if (target.getAbsorption() > 0.1f) {
                            float absFrac = Math.min(1.0f, target.getAbsorption() / 20.0f);
                            r.fillRoundedRect(barX, barY + barH - 2.0f, Math.max(4.0f, barW * absFrac), 2.0f, 1.0f, 0xFFFFD166);
                        }
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
    }

    private static TargetEntityInfo selectBest(MinecraftAdapter mc, List<TargetEntityInfo> list, String priorityMode) {
        TargetEntityInfo best = null;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < list.size(); i++) {
            TargetEntityInfo cand = list.get(i);
            double score;
            if ("Lowest HP".equalsIgnoreCase(priorityMode)) {
                score = cand.getHealth() + cand.getAbsorption() * 0.5f + cand.getDistance() * 0.15f;
            } else if ("Angle".equalsIgnoreCase(priorityMode)) {
                float[] ang = computeLookAngles(mc, cand);
                score = Math.abs(wrapDegrees(ang[0] - mc.getPlayerYaw())) + Math.abs(ang[1] - mc.getPlayerPitch()) * 0.5;
            } else {
                score = cand.getDistance();
            }
            if (score < bestScore) {
                bestScore = score;
                best = cand;
            }
        }
        return best;
    }

    private static float[] computeLookAngles(MinecraftAdapter mc, TargetEntityInfo target) {
        double dx = target.getX() - mc.getPlayerX();
        double dy = target.getEyeY() - (mc.getPlayerY() + 1.62);
        double dz = target.getZ() - mc.getPlayerZ();
        double horiz = Math.hypot(dx, dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.max(0.001, horiz))));
        return new float[] { wrapDegrees(yaw), clampPitch(pitch) };
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
