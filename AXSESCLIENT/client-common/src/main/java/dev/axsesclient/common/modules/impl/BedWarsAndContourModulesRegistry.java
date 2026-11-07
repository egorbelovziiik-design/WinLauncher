package dev.axsesclient.common.modules.impl;

import dev.axsesclient.common.hud.HudElement;
import dev.axsesclient.common.hud.HudManager;
import dev.axsesclient.common.i18n.LocalizationManager;
import dev.axsesclient.common.modules.Module;
import dev.axsesclient.common.modules.ModuleCategory;
import dev.axsesclient.common.modules.ModuleManager;
import dev.axsesclient.common.notifications.NotificationManager;
import dev.axsesclient.common.platform.InputAdapter;
import dev.axsesclient.common.platform.ItemInfo;
import dev.axsesclient.common.platform.KeyCodes;
import dev.axsesclient.common.platform.MinecraftAdapter;
import dev.axsesclient.common.platform.RenderAdapter;
import dev.axsesclient.common.platform.TargetEntityInfo;
import dev.axsesclient.common.render.FontManager;
import dev.axsesclient.common.render.RenderManager;
import dev.axsesclient.common.settings.BooleanSetting;
import dev.axsesclient.common.settings.ModeSetting;
import dev.axsesclient.common.settings.NumberSetting;
import dev.axsesclient.common.theme.AxsesTheme;
import dev.axsesclient.common.theme.ThemeManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * AXSESCLIENT v5.0 — BedWars Competitive Suite & Next-Gen 3D Neon Contour Modules (#113..#120).
 * Java 8 bytecode compatible for Minecraft 1.16.5, 1.21.4, and 1.21.11.
 */
public final class BedWarsAndContourModulesRegistry {
    private BedWarsAndContourModulesRegistry() {
    }

    public static boolean isPlaceableBridgeBlock(ItemInfo item) {
        if (item == null || item.isEmpty()) return false;
        String id = item.getItemId().toLowerCase(Locale.ROOT);
        if (id.contains("sword") || id.contains("pickaxe") || id.contains("axe") || id.contains("shovel")
                || id.contains("shears") || id.contains("apple") || id.contains("potion") || id.contains("pearl")
                || id.contains("bow") || id.contains("fire_charge") || id.contains("ingot") || id.contains("diamond")
                || id.contains("emerald") || id.contains("stick") || id.contains("compass") || id.contains("bed")) {
            return false;
        }
        return id.contains("wool") || id.contains("sandstone") || id.contains("end_stone")
                || id.contains("planks") || id.contains("terracotta") || id.contains("clay")
                || id.contains("concrete") || id.contains("stone") || id.contains("glass")
                || id.contains("obsidian") || id.contains("wood") || id.contains("brick")
                || id.contains("dirt") || id.contains("netherrack") || id.contains("quartz");
    }

    public static int findBestHotbarBlockSlot(MinecraftAdapter mc) {
        if (mc == null) return -1;
        List<ItemInfo> inv = mc.getInventoryItems();
        if (inv == null || inv.isEmpty()) return -1;
        int bestSlot = -1;
        int bestCount = 0;
        int limit = Math.min(9, inv.size());
        for (int s = 0; s < limit; s++) {
            ItemInfo it = inv.get(s);
            if (isPlaceableBridgeBlock(it) && it.getCount() > bestCount) {
                bestCount = it.getCount();
                bestSlot = s;
            }
        }
        return bestSlot;
    }

    public static int countAllBridgeBlocks(MinecraftAdapter mc) {
        if (mc == null || !mc.isInWorld()) return 64;
        int total = 0;
        List<ItemInfo> inv = mc.getInventoryItems();
        if (inv != null) {
            for (int i = 0; i < inv.size(); i++) {
                ItemInfo it = inv.get(i);
                if (isPlaceableBridgeBlock(it)) {
                    total += it.getCount();
                }
            }
        }
        ItemInfo off = mc.getOffHandItem();
        if (isPlaceableBridgeBlock(off)) {
            total += off.getCount();
        }
        return total;
    }

    public static void registerAll(
            final ModuleManager mm,
            final HudManager hm,
            final ClientRuntimeState state,
            final InputAdapter input,
            final ThemeManager themes,
            final FontManager fonts,
            final LocalizationManager i18n,
            final NotificationManager notifications
    ) {
        // 113. FastBridge Pro / Ninja Eagle (ФастБридж для BedWars)
        final ModeSetting fbModeRef = new ModeSetting("bridge_mode", "Bridge Style", "BedWars fast block bridging technique",
                "Ninja Eagle", "Ninja Eagle", "GodBridge 75.6", "Breezily Fast", "SafeBridge");
        final NumberSetting fbCpsRef = new NumberSetting("place_cps", "Place Speed (CPS)", "Right-click block placement speed in blocks/clicks per second", 16.0, 6.0, 22.0, 1.0);
        final NumberSetting fbEdgeRef = new NumberSetting("edge_offset", "Edge Sensitivity", "Distance from block edge where Eagle sneak triggers", 0.18, 0.06, 0.34, 0.02);
        final BooleanSetting fbAutoSlotRef = new BooleanSetting("auto_blocks", "Auto Wool Slot", "Automatically switch hotbar to fullest wool/block stack when low", true);
        final BooleanSetting fbPitchLockRef = new BooleanSetting("pitch_assist", "Angle Stabilizer", "Smoothly stabilize pitch around 76.2 deg when bridging backward", false);
        final BooleanSetting fbPreviewRef = new BooleanSetting("preview_contour", "3D Block Preview", "Render glowing 3D neon contour where next bridge block will place", true);

        final int[] fbPlacedSession = new int[] { 0 };
        final LinkedList<Long> fbPlaceTimestamps = new LinkedList<Long>();
        final boolean[] fbHoldingSneak = new boolean[] { false };

        Module fastBridgeMod = mm.register(new Module("fast_bridge", "FastBridge Pro", "High-speed BedWars block bridging (Ninja Eagle, GodBridge 75.6, Auto-Wool & 3D block contour)", ModuleCategory.MOVEMENT, false, KeyCodes.KEY_NONE, false) {
            private long lastPlaceMs = 0L;
            private int overEdgeTicks = 0;

            {
                addSetting(fbModeRef);
                addSetting(fbCpsRef);
                addSetting(fbEdgeRef);
                addSetting(fbAutoSlotRef);
                addSetting(fbPitchLockRef);
                addSetting(fbPreviewRef);
            }

            @Override
            public void onDisable(MinecraftAdapter mc) {
                if (fbHoldingSneak[0] && mc != null) {
                    mc.setSneakingKeyHeld(false);
                }
                fbHoldingSneak[0] = false;
                overEdgeTicks = 0;
            }

            @Override
            public void onTick(MinecraftAdapter mc) {
                if (mc == null || !mc.isInWorld() || mc.isClickGuiOpen() || mc.isHudEditorOpen() || mc.isChatScreenOpen()) {
                    if (fbHoldingSneak[0] && mc != null) {
                        mc.setSneakingKeyHeld(false);
                        fbHoldingSneak[0] = false;
                    }
                    return;
                }

                // Prune rolling 2-second placement rate window
                long now = System.currentTimeMillis();
                while (!fbPlaceTimestamps.isEmpty() && now - fbPlaceTimestamps.getFirst().longValue() > 2000L) {
                    fbPlaceTimestamps.removeFirst();
                }

                boolean movingBackwardOrLookingDown = mc.getPlayerPitch() >= 62.0f
                        || (input != null && (input.isKeyDown(KeyCodes.KEY_S) || input.isMouseButtonDown(1)));
                if (!movingBackwardOrLookingDown) {
                    if (fbHoldingSneak[0]) {
                        mc.setSneakingKeyHeld(false);
                        fbHoldingSneak[0] = false;
                    }
                    overEdgeTicks = 0;
                    return;
                }

                // Auto refill hotbar wool/sandstone slot if current hand has no blocks or < 4 blocks left
                ItemInfo held = mc.getMainHandItem();
                if (fbAutoSlotRef.isEnabled() && (!isPlaceableBridgeBlock(held) || held.getCount() <= 3)) {
                    int bestSlot = findBestHotbarBlockSlot(mc);
                    if (bestSlot >= 0 && bestSlot != mc.getSelectedHotbarSlot()) {
                        mc.selectHotbarSlot(bestSlot);
                        held = mc.getMainHandItem();
                    }
                }
                if (!isPlaceableBridgeBlock(held)) {
                    if (fbHoldingSneak[0]) {
                        mc.setSneakingKeyHeld(false);
                        fbHoldingSneak[0] = false;
                    }
                    return;
                }

                // Optional gentle pitch stabilizer around optimal 76.0 deg BedWars bridge angle
                String mode = fbModeRef.getValue();
                if (fbPitchLockRef.isEnabled() || "GodBridge 75.6".equalsIgnoreCase(mode)) {
                    float curPitch = mc.getPlayerPitch();
                    if (curPitch >= 68.0f && curPitch <= 84.5f) {
                        float targetPitch = "GodBridge 75.6".equalsIgnoreCase(mode) ? 75.6f : 77.2f;
                        float nextPitch = curPitch + (targetPitch - curPitch) * 0.35f;
                        mc.setPlayerRotation(mc.getPlayerYaw(), nextPitch);
                    }
                }

                boolean overEdge = mc.isStandingOverEdge(fbEdgeRef.getDouble());
                if (overEdge) {
                    overEdgeTicks++;
                } else {
                    overEdgeTicks = 0;
                }

                // Eagle / Ninja sneak control
                if ("Ninja Eagle".equalsIgnoreCase(mode) || "SafeBridge".equalsIgnoreCase(mode)) {
                    if (overEdge && mc.isOnGround()) {
                        mc.setSneakingKeyHeld(true);
                        fbHoldingSneak[0] = true;
                    } else if (!overEdge && fbHoldingSneak[0]) {
                        mc.setSneakingKeyHeld(false);
                        fbHoldingSneak[0] = false;
                    }
                } else if ("GodBridge 75.6".equalsIgnoreCase(mode)) {
                    // Pure unsneaked speed-bridging with emergency 1-tick edge catch if slipping > 2 ticks
                    if (overEdgeTicks >= 3 && mc.isOnGround()) {
                        mc.setSneakingKeyHeld(true);
                        fbHoldingSneak[0] = true;
                    } else if (fbHoldingSneak[0]) {
                        mc.setSneakingKeyHeld(false);
                        fbHoldingSneak[0] = false;
                    }
                } else if (fbHoldingSneak[0]) {
                    mc.setSneakingKeyHeld(false);
                    fbHoldingSneak[0] = false;
                }

                // High-speed block placement cadence
                long minIntervalMs = Math.max(42L, Math.round(1000.0 / Math.max(4.0, fbCpsRef.getDouble())));
                if ((overEdge || "Breezily Fast".equalsIgnoreCase(mode) || "GodBridge 75.6".equalsIgnoreCase(mode))
                        && (now - lastPlaceMs >= minIntervalMs)) {
                    if (mc.placeBlockAtCrosshair()) {
                        lastPlaceMs = now;
                        fbPlacedSession[0]++;
                        fbPlaceTimestamps.addLast(Long.valueOf(now));
                    }
                }
            }

            @Override
            public void onRender(RenderAdapter r, MinecraftAdapter mc, float dt) {
                if (!fbPreviewRef.isEnabled() || mc == null || !mc.isInWorld() || mc.isClickGuiOpen()) {
                    return;
                }
                ItemInfo held = mc.getMainHandItem();
                if (!isPlaceableBridgeBlock(held) && !mc.isHudEditorOpen()) {
                    return;
                }
                AxsesTheme t = themes.getActiveTheme();
                int prim = themes.filterColor(t.getPrimaryColor());
                int acc = themes.filterColor(t.getAccentColor());

                double[] tb = mc.getTargetedBlockBox();
                double bx, by, bz;
                if (tb != null && tb.length >= 12) {
                    bx = tb[0] + tb[9];
                    by = tb[1] + tb[10];
                    bz = tb[2] + tb[11];
                } else {
                    // Fallback bridge cell directly behind movement vector under feet
                    double rad = Math.toRadians(mc.getPlayerYaw());
                    bx = Math.floor(mc.getPlayerX() + Math.sin(rad) * 0.85);
                    by = Math.floor(mc.getPlayerY() - 1.0);
                    bz = Math.floor(mc.getPlayerZ() - Math.cos(rad) * 0.85);
                }

                RenderManager.draw3dNeonPrism(r, mc,
                        bx + 0.01, by + 0.01, bz + 0.01,
                        bx + 0.99, by + 0.99, bz + 0.99,
                        1.65f, acc, prim, true);
                drawTopFaceCross(r, mc, bx, by + 1.0, bz, AxsesTheme.withAlpha(acc, 0.72f));
            }

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                boolean ru = i18n == null || i18n.isRussian();
                int blocks = countAllBridgeBlocks(mc);
                double bps = fbPlaceTimestamps.size() * 0.5;
                String m = i18n != null ? i18n.optionName(fbModeRef.getValue()) : fbModeRef.getValue();
                return String.format(Locale.ROOT,
                        ru ? "🧱 %s · %d бл. · %.1f бл/с" : "🧱 %s · %d blk · %.1f b/s",
                        m, Integer.valueOf(blocks), Double.valueOf(bps));
            }
        });
        fastBridgeMod.setFavorite(true);
        hm.register(new HudElement("fast_bridge", "FastBridge HUD", fastBridgeMod, 0.50f, 0.822f, 232f, 22f));

        // 114. Custom Block Contour Pro (Неоновый Контур Блоков — анти-ванильная подсветка)
        Module blockContourMod = mm.register(new Module("block_contour_pro", "Block Contour Pro", "Replaces plain vanilla block wireframe with anti-aliased multi-layer 3D neon prism & placement face glow", ModuleCategory.RENDER, true, KeyCodes.KEY_NONE, false) {
            private final ModeSetting style = addSetting(new ModeSetting("contour_style", "Contour Style", "3D block outline visual shader mode",
                    "Hyperion Dual", "Hyperion Dual", "Chroma Pulse", "BedWars Face", "Cyber Corners"));
            private final NumberSetting stroke = addSetting(new NumberSetting("line_width", "Line Width", "Anti-aliased neon contour stroke thickness", 1.75, 1.0, 3.5, 0.25));
            private final BooleanSetting faceHighlight = addSetting(new BooleanSetting("face_glow", "Placement Face Glow", "Highlight targeted block side where next block attaches", true));

            @Override
            public void onRender(RenderAdapter r, MinecraftAdapter mc, float dt) {
                if (mc == null || !mc.isInWorld() || mc.isClickGuiOpen()) return;
                double[] tb = mc.getTargetedBlockBox();
                if (tb == null || tb.length < 12) return;

                AxsesTheme t = themes.getActiveTheme();
                int c1, c2;
                if ("Chroma Pulse".equalsIgnoreCase(style.getValue())) {
                    c1 = themes.getAuroraWaveColor(0.0f);
                    c2 = themes.getAuroraWaveColor(2.2f);
                } else {
                    c1 = themes.filterColor(t.getAccentColor());
                    c2 = themes.filterColor(t.getPrimaryColor());
                }

                double x0 = tb[3] - 0.004, y0 = tb[4] - 0.004, z0 = tb[5] - 0.004;
                double x1 = tb[6] + 0.004, y1 = tb[7] + 0.004, z1 = tb[8] + 0.004;
                String st = style.getValue();

                if ("Cyber Corners".equalsIgnoreCase(st)) {
                    draw3dCornerTicks(r, mc, x0, y0, z0, x1, y1, z1, stroke.getFloat(), c1);
                } else {
                    RenderManager.draw3dNeonPrism(r, mc, x0, y0, z0, x1, y1, z1, stroke.getFloat(), c1, c2, true);
                }

                if (faceHighlight.isEnabled() || "BedWars Face".equalsIgnoreCase(st)) {
                    drawBlockFaceRing(r, mc, x0, y0, z0, x1, y1, z1, tb[9], tb[10], tb[11], c1);
                }
            }
        });
        blockContourMod.setFavorite(true);

        // 115. BedWars ESP & Resource Tracker (БедВарс ESP: Кровати + Защита + Ресурсы)
        Module bedwarsEspMod = mm.register(new Module("bedwars_esp", "BedWars ESP", "Highlights BedWars beds through walls with defence-layer scanner (Obsidian/EndStone/Wool) & generator resource HUD", ModuleCategory.RENDER, false, KeyCodes.KEY_NONE, false) {
            private final NumberSetting radius = addSetting(new NumberSetting("scan_radius", "Bed Scan Radius", "Block radius to scan for beds & defence layers", 28.0, 12.0, 48.0, 4.0));
            private final BooleanSetting showBeds = addSetting(new BooleanSetting("show_beds", "3D Bed Contours", "Render glowing neon prisms and defence tags on beds", true));
            private final BooleanSetting baseAlert = addSetting(new BooleanSetting("proximity_warn", "Base Raid Alert", "Warn when an enemy approaches within 10m of a nearby bed", true));

            private List<double[]> cachedBeds = Collections.emptyList();
            private int scanTickCounter = 0;
            private long lastRaidAlertMs = 0L;

            @Override
            public void onTick(MinecraftAdapter mc) {
                if (mc == null || !mc.isInWorld()) {
                    cachedBeds = Collections.emptyList();
                    return;
                }
                scanTickCounter++;
                if (scanTickCounter >= 12 || cachedBeds.isEmpty()) {
                    scanTickCounter = 0;
                    cachedBeds = mc.scanBedWarsBeds((int) Math.round(radius.getDouble()));
                }

                if (baseAlert.isEnabled() && !cachedBeds.isEmpty() && notifications != null) {
                    long now = System.currentTimeMillis();
                    if (now - lastRaidAlertMs > 6000L) {
                        List<TargetEntityInfo> enemies = mc.getNearbyTargets(28.0, true, false, false, true);
                        for (int i = 0; i < enemies.size(); i++) {
                            TargetEntityInfo e = enemies.get(i);
                            for (int b = 0; b < cachedBeds.size(); b++) {
                                double[] bed = cachedBeds.get(b);
                                double d = Math.hypot(e.getX() - bed[0], e.getZ() - bed[2]);
                                if (d <= 9.5) {
                                    lastRaidAlertMs = now;
                                    boolean ru = i18n == null || i18n.isRussian();
                                    notifications.push("BedWars",
                                            (ru ? "⚠ Игрок у кровати: " : "⚠ Enemy near bed: ") + e.getName(),
                                            NotificationManager.Severity.WARNING);
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            @Override
            public void onRender(RenderAdapter r, MinecraftAdapter mc, float dt) {
                if (!showBeds.isEnabled() || mc == null || !mc.isInWorld() || mc.isClickGuiOpen() || cachedBeds.isEmpty()) {
                    return;
                }
                boolean ru = i18n == null || i18n.isRussian();
                for (int i = 0; i < cachedBeds.size(); i++) {
                    double[] bed = cachedBeds.get(i);
                    double bx = bed[0], by = bed[1], bz = bed[2];
                    int tier = (int) Math.round(bed[3]);
                    int bedCol = bed.length >= 5 && ((int) bed[4]) != 0 ? (0xFF000000 | ((int) bed[4] & 0xFFFFFF)) : 0xFFFF3B6B;
                    int accentCol = tier >= 4 ? 0xFFA855F7 : (tier == 3 ? 0xFFFACC15 : (tier == 2 ? 0xFFFB923C : 0xFF00F5D4));

                    RenderManager.draw3dNeonPrism(r, mc,
                            bx, by, bz,
                            bx + 1.0, by + 0.5625, bz + 1.0,
                            1.8f, bedCol, accentCol, true);

                    float[] sp = mc.projectWorldToScreen(bx + 0.5, by + 1.05, bz + 0.5);
                    if (sp != null && sp[2] > 0.1f) {
                        double dist = Math.sqrt(sq(mc.getPlayerX() - (bx + 0.5)) + sq(mc.getPlayerY() - by) + sq(mc.getPlayerZ() - (bz + 0.5)));
                        String defName = defenceLabel(tier, ru);
                        String txt = String.format(Locale.ROOT,
                                ru ? "🛏 Кровать [%s] · %.0fм" : "🛏 Bed [%s] · %.0fm",
                                defName, Double.valueOf(dist));
                        float tw = fonts.width(r, txt, 0.82f) + 12.0f;
                        float px = sp[0] - tw * 0.5f;
                        float py = sp[1] - 12.0f;
                        r.fillRoundedRect(px, py, tw, 14.0f, 7.0f, 0xE6060916);
                        r.drawGradientRoundedOutline(px, py, tw, 14.0f, 7.0f, 1.2f, bedCol, accentCol);
                        fonts.draw(r, txt, px + 6.0f, py + 3.2f, 0xFFF8FAFF, 0.82f);
                    }
                }
            }

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                int iron = 0, gold = 0, dia = 0, eme = 0;
                if (mc != null && mc.isInWorld()) {
                    List<ItemInfo> inv = mc.getInventoryItems();
                    if (inv != null) {
                        for (int i = 0; i < inv.size(); i++) {
                            ItemInfo it = inv.get(i);
                            if (it == null || it.isEmpty()) continue;
                            String id = it.getItemId().toLowerCase(Locale.ROOT);
                            if (id.contains("iron_ingot")) iron += it.getCount();
                            else if (id.contains("gold_ingot")) gold += it.getCount();
                            else if (id.endsWith("diamond")) dia += it.getCount();
                            else if (id.endsWith("emerald")) eme += it.getCount();
                        }
                    }
                } else {
                    iron = 36; gold = 12; dia = 6; eme = 4;
                }
                boolean ru = i18n == null || i18n.isRussian();
                return String.format(Locale.ROOT,
                        ru ? "Ресурсы: Ж:%d · З:%d · Алм:%d · Изм:%d" : "BW: Fe:%d · Au:%d · Dia:%d · Em:%d",
                        Integer.valueOf(iron), Integer.valueOf(gold), Integer.valueOf(dia), Integer.valueOf(eme));
            }
        });
        bedwarsEspMod.setFavorite(true);
        hm.register(new HudElement("bedwars_esp", "BedWars Resources", bedwarsEspMod, 0.014f, 0.650f, 214f, 20f));

        // 116. Fireball & TNT Predictor (Защита от Фаерболов и ТНТ)
        mm.register(new Module("fireball_tnt_alert", "Fireball & TNT Shield", "Predicts incoming BedWars Fireballs, TNT & Pearls with 3D trajectory arcs, blast radius contours & auto-deflect", ModuleCategory.COMBAT_TRAINING, false, KeyCodes.KEY_NONE, false) {
            private final NumberSetting range = addSetting(new NumberSetting("detect_range", "Scan Range", "Maximum projectile detection distance", 52.0, 16.0, 96.0, 4.0));
            private final BooleanSetting drawArc = addSetting(new BooleanSetting("trajectory_line", "3D Trajectory Arc", "Draw glowing ballistic flight path", true));
            private final BooleanSetting blastRing = addSetting(new BooleanSetting("blast_ring", "Blast Radius Ring", "Draw neon danger circle on impact zone", true));
            private final BooleanSetting autoDeflect = addSetting(new BooleanSetting("auto_deflect", "Auto-Deflect Fireball", "Automatically punch incoming Fireballs within 3.2m", true));

            @Override
            public void onTick(MinecraftAdapter mc) {
                if (!autoDeflect.isEnabled() || mc == null || !mc.isInWorld() || mc.isClickGuiOpen()) return;
                List<double[]> projs = mc.getIncomingProjectiles(range.getDouble());
                if (projs == null || projs.isEmpty()) return;
                for (int i = 0; i < projs.size(); i++) {
                    double[] p = projs.get(i);
                    int type = (int) Math.round(p[6]);
                    int entityId = (int) Math.round(p[7]);
                    double dist = p[8];
                    if (type == 1 && dist <= 3.25 && entityId >= 0) {
                        mc.attackEntityById(entityId, true);
                        break;
                    }
                }
            }

            @Override
            public void onRender(RenderAdapter r, MinecraftAdapter mc, float dt) {
                if (mc == null || !mc.isInWorld() || mc.isClickGuiOpen()) return;
                List<double[]> projs = mc.getIncomingProjectiles(range.getDouble());
                if (projs == null || projs.isEmpty()) return;
                boolean ru = i18n == null || i18n.isRussian();

                for (int i = 0; i < projs.size(); i++) {
                    double[] p = projs.get(i);
                    double px = p[0], py = p[1], pz = p[2];
                    double vx = p[3], vy = p[4], vz = p[5];
                    int type = (int) Math.round(p[6]);
                    double dist = p[8];
                    int col = (type == 1) ? 0xFFFF5D38 : (type == 2 ? 0xFFFF2E63 : (type == 4 ? 0xFFC084FC : 0xFF38BDF8));

                    if (drawArc.isEnabled()) {
                        double sx = px, sy = py, sz = pz;
                        double svx = vx, svy = vy, svz = vz;
                        float prev2dX = 0f, prev2dY = 0f;
                        boolean hasPrev = false;
                        double drag = (type == 1) ? 0.95 : 0.99;
                        double grav = (type == 1) ? 0.0 : 0.03;
                        for (int step = 0; step < 18; step++) {
                            float[] pr = mc.projectWorldToScreen(sx, sy, sz);
                            if (pr != null && pr[2] > 0.1f) {
                                if (hasPrev) {
                                    r.drawNeonLine(prev2dX, prev2dY, pr[0], pr[1], 1.6f, col, AxsesTheme.withAlpha(col, 0.45f));
                                }
                                prev2dX = pr[0];
                                prev2dY = pr[1];
                                hasPrev = true;
                            } else {
                                hasPrev = false;
                            }
                            sx += svx;
                            sy += svy;
                            sz += svz;
                            svx *= drag;
                            svy = svy * drag - grav;
                            svz *= drag;
                        }
                        if (blastRing.isEnabled() && (type == 1 || type == 2)) {
                            drawImpactRing(r, mc, sx, sy, sz, type == 2 ? 3.8 : 2.6, col);
                        }
                    }

                    float[] head = mc.projectWorldToScreen(px, py + 0.35, pz);
                    if (head != null && head[2] > 0.1f) {
                        String name = type == 1 ? (ru ? "🔥 ФАЕРБОЛ" : "🔥 FIREBALL")
                                : (type == 2 ? (ru ? "🧨 ТНТ" : "🧨 TNT")
                                : (type == 4 ? (ru ? "🟣 ЭНДЕР-ПЕРЛ" : "🟣 PEARL") : (ru ? "🏹 СТРЕЛА" : "🏹 ARROW")));
                        String lbl = String.format(Locale.ROOT, "%s · %.1f%s", name, Double.valueOf(dist), ru ? "м" : "m");
                        float tw = fonts.width(r, lbl, 0.82f) + 10.0f;
                        float lx = head[0] - tw * 0.5f;
                        float ly = head[1] - 14.0f;
                        r.fillRoundedRect(lx, ly, tw, 13.0f, 6.5f, 0xEA080510);
                        r.drawRoundedOutline(lx, ly, tw, 13.0f, 6.5f, 1.2f, col);
                        fonts.draw(r, lbl, lx + 5.0f, ly + 2.8f, 0xFFFFFFFF, 0.82f);
                    }
                }
            }
        });

        // 117. Smart AutoTool & Sword (Авто-Инструмент для Кроватей и Авто-Меч)
        mm.register(new Module("auto_tool_weapon", "Smart AutoTool & Sword", "Automatically selects Shears/Pickaxe/Axe when breaking BedWars bed defences and snaps to Sword on enemies", ModuleCategory.PLAYER, false, KeyCodes.KEY_NONE, false) {
            private final BooleanSetting autoBedTool = addSetting(new BooleanSetting("bedwars_tools", "Auto Bed-Break Tool", "Select Shears for wool, Pickaxe for endstone/obsidian, Axe for wood", true));
            private final BooleanSetting autoSword = addSetting(new BooleanSetting("auto_sword", "Auto Combat Sword", "Snap to strongest Sword when aiming at an enemy player", true));
            private final BooleanSetting restoreSlot = addSetting(new BooleanSetting("restore_slot", "Restore Previous Slot", "Return to prior hotbar slot after mining finishes", true));

            private int savedSlot = -1;

            @Override
            public void onTick(MinecraftAdapter mc) {
                if (mc == null || !mc.isInWorld() || mc.isClickGuiOpen() || mc.isUsingItem()) return;

                if (autoSword.isEnabled()) {
                    TargetEntityInfo ch = mc.getCrosshairTarget();
                    if (ch != null && ch.isPlayer()) {
                        int swordSlot = findHotbarItemByKeyword(mc, "sword");
                        if (swordSlot >= 0 && swordSlot != mc.getSelectedHotbarSlot()) {
                            mc.selectHotbarSlot(swordSlot);
                        }
                        return;
                    }
                }

                boolean leftHeld = input != null && input.isMouseButtonDown(0);
                if (autoBedTool.isEnabled() && leftHeld) {
                    String blockId = mc.getTargetedBlockId().toLowerCase(Locale.ROOT);
                    if (!blockId.isEmpty() && !blockId.endsWith("air")) {
                        int toolSlot = -1;
                        if (blockId.contains("wool")) {
                            toolSlot = findHotbarItemByKeyword(mc, "shears");
                        } else if (blockId.contains("planks") || blockId.contains("wood") || blockId.contains("log") || blockId.contains("ladder")) {
                            toolSlot = findHotbarItemByKeyword(mc, "_axe");
                        } else if (blockId.contains("end_stone") || blockId.contains("obsidian") || blockId.contains("stone") || blockId.contains("terracotta") || blockId.contains("clay")) {
                            toolSlot = findHotbarItemByKeyword(mc, "pickaxe");
                        }
                        if (toolSlot >= 0 && toolSlot != mc.getSelectedHotbarSlot()) {
                            if (savedSlot < 0) savedSlot = mc.getSelectedHotbarSlot();
                            mc.selectHotbarSlot(toolSlot);
                        }
                        return;
                    }
                }

                if (!leftHeld && savedSlot >= 0 && restoreSlot.isEnabled()) {
                    mc.selectHotbarSlot(savedSlot);
                    savedSlot = -1;
                }
            }
        });

        // 118. Block Clutch / Void Rescue (Клатчинг Блоками при сбитии с моста)
        mm.register(new Module("bridge_clutch", "Block Clutch Rescue", "Assists emergency side-block clutch placement when knocked off a BedWars bridge", ModuleCategory.MOVEMENT, false, KeyCodes.KEY_NONE, false) {
            private final NumberSetting fallTrigger = addSetting(new NumberSetting("trigger_fall", "Fall Distance (m)", "Minimum fall distance before clutch assist activates", 1.20, 0.60, 3.00, 0.20));
            private final BooleanSetting switchBlocks = addSetting(new BooleanSetting("auto_block_slot", "Auto Select Blocks", "Automatically switch to wool/blocks when knocked airborne", true));

            @Override
            public void onTick(MinecraftAdapter mc) {
                if (mc == null || !mc.isInWorld() || mc.isClickGuiOpen() || mc.isOnGround() || mc.isInLiquid()) {
                    return;
                }
                if (mc.getFallDistance() < fallTrigger.getFloat() || !mc.isStandingOverEdge(0.25)) {
                    return;
                }
                ItemInfo held = mc.getMainHandItem();
                if (!isPlaceableBridgeBlock(held) && switchBlocks.isEnabled()) {
                    int slot = findBestHotbarBlockSlot(mc);
                    if (slot >= 0) {
                        mc.selectHotbarSlot(slot);
                        held = mc.getMainHandItem();
                    }
                }
                if (isPlaceableBridgeBlock(held)) {
                    mc.placeBlockAtCrosshair();
                }
            }
        });

        // 119. Invis & Threat Scanner (Детектор Инвизок, Обсидиана и Фаерболов)
        mm.register(new Module("invis_armor_detector", "Invis & Threat Scanner", "Highlights invisible BedWars rushers & displays Fireball / TNT / Obsidian / Pearl threat icons on enemies", ModuleCategory.RENDER, false, KeyCodes.KEY_NONE, false) {
            private final NumberSetting scanRange = addSetting(new NumberSetting("range", "Scanner Range", "Enemy threat inspection distance", 64.0, 20.0, 128.0, 4.0));
            private final BooleanSetting showThreats = addSetting(new BooleanSetting("threat_tags", "Held Threat Badges", "Highlight enemies carrying TNT, Fireballs, Pearls or Obsidian", true));

            @Override
            public void onRender(RenderAdapter r, MinecraftAdapter mc, float dt) {
                if (mc == null || !mc.isInWorld() || mc.isClickGuiOpen()) return;
                List<TargetEntityInfo> list = mc.getEspEntities(scanRange.getDouble(), true, false, false, false, true);
                if (list == null || list.isEmpty()) return;
                boolean ru = i18n == null || i18n.isRussian();

                for (int i = 0; i < list.size(); i++) {
                    TargetEntityInfo ent = list.get(i);
                    String held = ent.getHeldItemName() != null ? ent.getHeldItemName().toLowerCase(Locale.ROOT) : "";
                    boolean invis = held.contains("[invis]");
                    String threat = "";
                    if (showThreats.isEnabled() && !held.isEmpty()) {
                        if (held.contains("fire") || held.contains("огнен") || held.contains("фаер")) threat = ru ? "🔥 ФАЕРБОЛ" : "🔥 FIREBALL";
                        else if (held.contains("tnt") || held.contains("динамит") || held.contains("тнт")) threat = ru ? "🧨 ТНТ" : "🧨 TNT";
                        else if (held.contains("pearl") || held.contains("жемчуг") || held.contains("перл")) threat = ru ? "🟣 ПЕРЛ" : "🟣 PEARL";
                        else if (held.contains("obsidian") || held.contains("обсидиан")) threat = ru ? "🟪 ОБСИДИАН" : "🟪 OBSIDIAN";
                        else if (held.contains("bow") || held.contains("лук")) threat = ru ? "🏹 ЛУК" : "🏹 BOW";
                    }
                    if (!invis && threat.isEmpty()) continue;

                    if (invis) {
                        RenderManager.draw3dNeonPrism(r, mc,
                                ent.getMinX(), ent.getMinY(), ent.getMinZ(),
                                ent.getMaxX(), ent.getMaxY(), ent.getMaxZ(),
                                1.7f, 0xFFFF3DF0, 0xFF00F5D4, true);
                    }
                    float[] top = mc.projectWorldToScreen(ent.getX(), ent.getMaxY() + 0.48, ent.getZ());
                    if (top != null && top[2] > 0.1f) {
                        String badge = invis
                                ? ((ru ? "👻 ИНВИЗКА! " : "👻 INVIS! ") + threat).trim()
                                : threat;
                        float tw = fonts.width(r, badge, 0.80f) + 10.0f;
                        float bx = top[0] - tw * 0.5f;
                        float by = top[1] - 14.0f;
                        int col = invis ? 0xFFFF3DF0 : 0xFFFF9F1C;
                        r.fillRoundedRect(bx, by, tw, 13.0f, 6.5f, 0xE8090516);
                        r.drawRoundedOutline(bx, by, tw, 13.0f, 6.5f, 1.2f, col);
                        fonts.draw(r, badge, bx + 5.0f, by + 2.8f, 0xFFFFFFFF, 0.80f);
                    }
                }
            }
        });

        // 120. Hyperion Neon Ribbon & Halo (Неоновый Шлейф и Кибер-Нимб)
        Module neonTrailsMod = mm.register(new Module("neon_halo_trails", "Hyperion Ribbon & Halo", "Renders smooth anti-aliased dual-ribbon 3D player trails and rotating crystalline halo contours", ModuleCategory.RENDER, false, KeyCodes.KEY_NONE, false) {
            private final NumberSetting maxPoints = addSetting(new NumberSetting("trail_points", "Ribbon Length", "Number of smooth 3D trail ribbon samples", 34.0, 12.0, 64.0, 4.0));
            private final BooleanSetting targetHalo = addSetting(new BooleanSetting("target_halo", "Target Cyber Halo", "Render rotating geometric neon halo above locked combat target", true));
            private final LinkedList<double[]> trail = new LinkedList<double[]>();

            @Override
            public void onTick(MinecraftAdapter mc) {
                if (mc == null || !mc.isInWorld()) {
                    trail.clear();
                    return;
                }
                double px = mc.getPlayerX(), py = mc.getPlayerY(), pz = mc.getPlayerZ();
                if (trail.isEmpty()) {
                    trail.addLast(new double[] { px, py, pz });
                } else {
                    double[] last = trail.getLast();
                    if (sq(px - last[0]) + sq(py - last[1]) + sq(pz - last[2]) > 0.015) {
                        trail.addLast(new double[] { px, py, pz });
                    }
                }
                int cap = (int) Math.round(maxPoints.getDouble());
                while (trail.size() > cap) {
                    trail.removeFirst();
                }
            }

            @Override
            public void onRender(RenderAdapter r, MinecraftAdapter mc, float dt) {
                if (mc == null || !mc.isInWorld() || mc.isClickGuiOpen()) return;

                // Dual neon ribbon behind player
                if (trail.size() >= 2) {
                    float prevLowX = 0f, prevLowY = 0f, prevHighX = 0f, prevHighY = 0f;
                    boolean hasPrev = false;
                    int idx = 0;
                    int n = trail.size();
                    for (double[] pt : trail) {
                        float ageFrac = (float) idx / (float) Math.max(1, n - 1);
                        float[] pLow = mc.projectWorldToScreen(pt[0], pt[1] + 0.12, pt[2]);
                        float[] pHigh = mc.projectWorldToScreen(pt[0], pt[1] + 0.95, pt[2]);
                        if (pLow != null && pHigh != null && pLow[2] > 0.1f && pHigh[2] > 0.1f) {
                            int wave = themes.getAuroraWaveColor(idx * 0.22f);
                            if (hasPrev) {
                                r.fillQuad(prevLowX, prevLowY, pLow[0], pLow[1], pHigh[0], pHigh[1], prevHighX, prevHighY,
                                        AxsesTheme.withAlpha(wave, 0.24f * ageFrac));
                                r.drawNeonLine(prevLowX, prevLowY, pLow[0], pLow[1], 1.85f,
                                        AxsesTheme.withAlpha(wave, 0.92f * ageFrac), AxsesTheme.withAlpha(wave, 0.42f * ageFrac));
                                r.drawNeonLine(prevHighX, prevHighY, pHigh[0], pHigh[1], 1.45f,
                                        AxsesTheme.withAlpha(wave, 0.75f * ageFrac), AxsesTheme.withAlpha(wave, 0.30f * ageFrac));
                            }
                            prevLowX = pLow[0]; prevLowY = pLow[1];
                            prevHighX = pHigh[0]; prevHighY = pHigh[1];
                            hasPrev = true;
                        } else {
                            hasPrev = false;
                        }
                        idx++;
                    }
                }

                // Rotating crystalline cyber halo on locked target
                if (targetHalo.isEnabled()) {
                    TargetEntityInfo tgt = state.getActiveTarget(3500L);
                    if (tgt != null) {
                        drawCyberHalo(r, mc, themes, tgt.getX(), tgt.getMaxY() + 0.26, tgt.getZ());
                    }
                }
            }
        });
        neonTrailsMod.setFavorite(true);

        // 124. 3D Hand & Sword ViewModel Animations (Анимации Рук и Меча 3D)
        Module swingAnimMod = mm.register(new Module("swing_animations", "3D Hand & Sword Animations", "Custom first-person sword swing styles, compact viewmodel scale/offsets, and neon slash arc", ModuleCategory.RENDER, true, KeyCodes.KEY_NONE, false) {
            private final ModeSetting style = addSetting(new ModeSetting("anim_style", "Swing Style", "First-person weapon animation preset",
                    "Celestial Smooth", "Celestial Smooth", "Nursultan Spin", "Blockhit Classic", "Glide Low", "Exhibition"));
            private final NumberSetting vmScale = addSetting(new NumberSetting("vm_scale", "ViewModel Scale", "First-person held item scale", 0.86, 0.50, 1.40, 0.02));
            private final NumberSetting vmX = addSetting(new NumberSetting("vm_x", "Offset X", "Horizontal hand offset", 0.10, -0.60, 0.60, 0.02));
            private final NumberSetting vmY = addSetting(new NumberSetting("vm_y", "Offset Y", "Vertical hand offset", -0.06, -0.60, 0.60, 0.02));
            private final BooleanSetting slashArc = addSetting(new BooleanSetting("slash_arc", "Neon Slash Trail", "Render glowing Aurora slash ribbon during weapon swing", true));

            @Override
            public void onRender(RenderAdapter r, MinecraftAdapter mc, float dt) {
                if (!slashArc.isEnabled() || mc == null || !mc.isInWorld() || mc.isClickGuiOpen() || mc.getPerspective() != 0) return;
                float cd = mc.getAttackCooldownProgress();
                if (cd >= 0.93f) return;
                float prog = 1.0f - cd;
                float sw = r.getScaledWidth();
                float sh = r.getScaledHeight();
                float cx = sw * 0.68f + vmX.getFloat() * 48.0f;
                float cy = sh * 0.72f - vmY.getFloat() * 42.0f;
                float rad = 46.0f * vmScale.getFloat();
                int segs = 10;
                float prevX = 0f, prevY = 0f;
                for (int i = 0; i <= segs; i++) {
                    float t = i / (float) segs;
                    double ang = -2.25 + (t * 1.45) - prog * 0.65;
                    float px = cx + (float) Math.cos(ang) * rad;
                    float py = cy + (float) Math.sin(ang) * (rad * 0.62f);
                    if (i > 0) {
                        int col = themes.getAuroraWaveColor(i * 0.25f);
                        float alpha = (1.0f - prog * 0.7f) * (1.0f - Math.abs(t - 0.5f));
                        r.drawNeonLine(prevX, prevY, px, py, 2.4f * (1.0f - t * 0.4f),
                                AxsesTheme.withAlpha(0xFFFFFFFF, alpha * 0.85f),
                                AxsesTheme.withAlpha(col, alpha * 0.70f));
                    }
                    prevX = px;
                    prevY = py;
                }
            }

            @Override
            public String getHudBadgeText(MinecraftAdapter mc) {
                String st = i18n != null ? i18n.optionName(style.getValue()) : style.getValue();
                return ((i18n != null && i18n.isRussian()) ? "Руки: " : "Hands: ") + st;
            }
        });
        swingAnimMod.setFavorite(true);
    }

    private static int findHotbarItemByKeyword(MinecraftAdapter mc, String kw) {
        List<ItemInfo> inv = mc.getInventoryItems();
        if (inv == null) return -1;
        int limit = Math.min(9, inv.size());
        for (int i = 0; i < limit; i++) {
            ItemInfo it = inv.get(i);
            if (it != null && !it.isEmpty() && it.getItemId().toLowerCase(Locale.ROOT).contains(kw)) {
                return i;
            }
        }
        return -1;
    }

    private static double sq(double v) {
        return v * v;
    }

    private static String defenceLabel(int tier, boolean ru) {
        switch (tier) {
            case 4: return ru ? "Обсидиан" : "Obsidian";
            case 3: return ru ? "Эндерняк" : "EndStone";
            case 2: return ru ? "Доски" : "Wood";
            case 1: return ru ? "Шерсть" : "Wool";
            default: return ru ? "ОТКРЫТА" : "OPEN";
        }
    }

    private static void drawTopFaceCross(RenderAdapter r, MinecraftAdapter mc, double bx, double y, double bz, int col) {
        float[] a = mc.projectWorldToScreen(bx + 0.15, y, bz + 0.15);
        float[] b = mc.projectWorldToScreen(bx + 0.85, y, bz + 0.85);
        float[] c = mc.projectWorldToScreen(bx + 0.85, y, bz + 0.15);
        float[] d = mc.projectWorldToScreen(bx + 0.15, y, bz + 0.85);
        if (a != null && b != null && a[2] > 0.1f && b[2] > 0.1f) {
            r.drawLine(a[0], a[1], b[0], b[1], 1.15f, col);
        }
        if (c != null && d != null && c[2] > 0.1f && d[2] > 0.1f) {
            r.drawLine(c[0], c[1], d[0], d[1], 1.15f, col);
        }
    }

    private static void draw3dCornerTicks(
            RenderAdapter r, MinecraftAdapter mc,
            double x0, double y0, double z0, double x1, double y1, double z1,
            float w, int col
    ) {
        double len = 0.28;
        double[][] corners = {
                {x0, y0, z0, 1, 1, 1}, {x1, y0, z0, -1, 1, 1}, {x1, y0, z1, -1, 1, -1}, {x0, y0, z1, 1, 1, -1},
                {x0, y1, z0, 1, -1, 1}, {x1, y1, z0, -1, -1, 1}, {x1, y1, z1, -1, -1, -1}, {x0, y1, z1, 1, -1, -1}
        };
        int glow = AxsesTheme.withAlpha(col, 0.45f);
        for (int i = 0; i < corners.length; i++) {
            double[] c = corners[i];
            float[] o = mc.projectWorldToScreen(c[0], c[1], c[2]);
            if (o == null || o[2] <= 0.1f) continue;
            float[] px = mc.projectWorldToScreen(c[0] + c[3] * len, c[1], c[2]);
            float[] py = mc.projectWorldToScreen(c[0], c[1] + c[4] * len, c[2]);
            float[] pz = mc.projectWorldToScreen(c[0], c[1], c[2] + c[5] * len);
            if (px != null && px[2] > 0.1f) r.drawNeonLine(o[0], o[1], px[0], px[1], w, col, glow);
            if (py != null && py[2] > 0.1f) r.drawNeonLine(o[0], o[1], py[0], py[1], w, col, glow);
            if (pz != null && pz[2] > 0.1f) r.drawNeonLine(o[0], o[1], pz[0], pz[1], w, col, glow);
        }
    }

    private static void drawBlockFaceRing(
            RenderAdapter r, MinecraftAdapter mc,
            double x0, double y0, double z0, double x1, double y1, double z1,
            double nx, double ny, double nz, int col
    ) {
        double in = 0.14;
        double[][] quad;
        if (Math.abs(ny) > 0.5) {
            double y = ny > 0 ? y1 + 0.006 : y0 - 0.006;
            quad = new double[][] {{x0 + in, y, z0 + in}, {x1 - in, y, z0 + in}, {x1 - in, y, z1 - in}, {x0 + in, y, z1 - in}};
        } else if (Math.abs(nx) > 0.5) {
            double x = nx > 0 ? x1 + 0.006 : x0 - 0.006;
            quad = new double[][] {{x, y0 + in, z0 + in}, {x, y1 - in, z0 + in}, {x, y1 - in, z1 - in}, {x, y0 + in, z1 - in}};
        } else {
            double z = nz > 0 ? z1 + 0.006 : z0 - 0.006;
            quad = new double[][] {{x0 + in, y0 + in, z}, {x1 - in, y0 + in, z}, {x1 - in, y1 - in, z}, {x0 + in, y1 - in, z}};
        }
        float[][] sp = new float[4][];
        for (int i = 0; i < 4; i++) {
            sp[i] = mc.projectWorldToScreen(quad[i][0], quad[i][1], quad[i][2]);
            if (sp[i] == null || sp[i][2] <= 0.1f) return;
        }
        int bright = 0xEEFFFFFF;
        int glow = AxsesTheme.withAlpha(col, 0.58f);
        r.fillQuad(sp[0][0], sp[0][1], sp[1][0], sp[1][1], sp[2][0], sp[2][1], sp[3][0], sp[3][1], AxsesTheme.withAlpha(col, 0.22f));
        for (int i = 0; i < 4; i++) {
            float[] a = sp[i];
            float[] b = sp[(i + 1) % 4];
            r.drawNeonLine(a[0], a[1], b[0], b[1], 1.55f, bright, glow);
        }
    }

    private static void drawImpactRing(RenderAdapter r, MinecraftAdapter mc, double cx, double cy, double cz, double rad, int col) {
        int segs = 28;
        float prevX = 0f, prevY = 0f;
        boolean has = false;
        int glow = AxsesTheme.withAlpha(col, 0.45f);
        for (int i = 0; i <= segs; i++) {
            double a = (Math.PI * 2.0 * i) / segs;
            float[] p = mc.projectWorldToScreen(cx + Math.cos(a) * rad, cy + 0.05, cz + Math.sin(a) * rad);
            if (p != null && p[2] > 0.1f) {
                if (has) r.drawNeonLine(prevX, prevY, p[0], p[1], 1.5f, col, glow);
                prevX = p[0]; prevY = p[1]; has = true;
            } else {
                has = false;
            }
        }
    }

    private static void drawCyberHalo(RenderAdapter r, MinecraftAdapter mc, ThemeManager tm, double cx, double cy, double cz) {
        double rot = (System.currentTimeMillis() % 4000L) / 4000.0 * Math.PI * 2.0;
        int sides = 6;
        double rad = 0.46;
        float prevX = 0f, prevY = 0f;
        boolean has = false;
        for (int i = 0; i <= sides; i++) {
            double a = (Math.PI * 2.0 * i) / sides + rot;
            float[] p = mc.projectWorldToScreen(cx + Math.cos(a) * rad, cy, cz + Math.sin(a) * rad);
            if (p != null && p[2] > 0.1f) {
                int col = tm.getAuroraWaveColor(i * 0.5f);
                if (has) r.drawNeonLine(prevX, prevY, p[0], p[1], 1.65f, col, AxsesTheme.withAlpha(col, 0.50f));
                prevX = p[0]; prevY = p[1]; has = true;
            } else {
                has = false;
            }
        }
    }
}
