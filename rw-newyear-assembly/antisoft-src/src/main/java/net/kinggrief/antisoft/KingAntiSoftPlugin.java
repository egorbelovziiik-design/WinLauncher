package net.kinggrief.antisoft;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KingAntiSoftPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private static final String GUI_TITLE = "§8🛡 King_antisoft v5.0 — Детекты";
    private static final Pattern HEX_PAT = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final BlockFace[] SIX_FACES = new BlockFace[]{
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    public static final class PlayerSensorData {
        public final Map<String, Integer> vlByCheck = new LinkedHashMap<String, Integer>();
        public int totalVl = 0;
        public String lastCheat = "Нет";
        public long lastAlertMs = 0L;

        // Combat / KillAura / AimBot state
        public final List<Long> clickTimestamps = new ArrayList<Long>();
        public UUID lastTargetId = null;
        public long lastHitMs = 0L;
        public float lastYaw = 0f;
        public float lastPitch = 0f;
        public float lastDeltaYaw = 0f;
        public float lastDeltaPitch = 0f;
        public long lastRotMs = 0L;
        public long lastProbeMs = 0L;

        // Movement / Fly / Speed state
        public int airHoverTicks = 0;
        public long lastDamagedMs = 0L;
        public long lastTeleportOrGlideMs = 0L;

        // XRay state
        public int stoneMined = 0;
        public int hiddenRareVeinHits = 0;
        public long lastRareOreMs = 0L;
        public Location lastRareOreLoc = null;

        // AutoTotem state
        public long lastTotemPopMs = 0L;
    }

    private final Map<UUID, PlayerSensorData> sensors = new HashMap<UUID, PlayerSensorData>();
    private final Set<UUID> mutedAlertsStaff = new HashSet<UUID>();
    // Active injected KillAura decoy probe Entity UUID -> Suspect UUID
    private final Map<UUID, UUID> activeProbes = new HashMap<UUID, UUID>();
    private int totalFlagsSession = 0;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("kingantisoft") != null) {
            getCommand("kingantisoft").setExecutor(this);
        }

        // Decay VL gradually every 45s so legitimate lag spikes never accumulate forever
        new BukkitRunnable() {
            @Override
            public void run() {
                for (PlayerSensorData d : sensors.values()) {
                    if (d.totalVl > 0) d.totalVl = Math.max(0, d.totalVl - 1);
                }
            }
        }.runTaskTimer(this, 900L, 900L);

        getLogger().info("============================================================");
        getLogger().info(" King_antisoft v5.0.0 — LIVE INJECTION ANTI-CHEAT ENABLED   ");
        getLogger().info(" Modules: KillAura (Inject-Probe), AimBot, Reach, XRay,     ");
        getLogger().info("          Fly/AirJump, Speed, AutoTotem -> Staff Alerts ON! ");
        getLogger().info("============================================================");
    }

    @Override
    public void onDisable() {
        for (UUID probeId : new ArrayList<UUID>(activeProbes.keySet())) {
            Entity e = Bukkit.getEntity(probeId);
            if (e != null) e.remove();
        }
        activeProbes.clear();
        sensors.clear();
    }

    private PlayerSensorData data(Player p) {
        PlayerSensorData d = sensors.get(p.getUniqueId());
        if (d == null) {
            d = new PlayerSensorData();
            d.lastYaw = p.getLocation().getYaw();
            d.lastPitch = p.getLocation().getPitch();
            sensors.put(p.getUniqueId(), d);
        }
        return d;
    }

    private boolean isExempt(Player p) {
        if (p == null) return true;
        GameMode gm = p.getGameMode();
        return gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR || p.hasPermission("kinggrief.admin");
    }

    private boolean isModerator(Player p) {
        return p != null && (p.isOp() || p.hasPermission("kinggrief.staff") || p.hasPermission("kinggrief.admin"));
    }

    // ================= 1. KILLAURA (INJECT-PROBE + WALLHIT + ANGLE + MULTIAURA + CPS), AIMBOT & REACH =================
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCombatInspect(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player)) return;
        Player attacker = (Player) e.getDamager();

        // Check if attacker hit an INJECTED invisible behind-the-back KillAura Decoy Probe!
        UUID probeOwner = activeProbes.get(e.getEntity().getUniqueId());
        if (probeOwner != null) {
            e.setCancelled(true);
            Entity probe = e.getEntity();
            activeProbes.remove(probe.getUniqueId());
            probe.remove();
            if (probeOwner.equals(attacker.getUniqueId())) {
                flag(attacker, "KillAura [Inject-Probe]", "Ударил невидимую приманку за спиной", 3);
            }
            return;
        }

        if (e.isCancelled() || isExempt(attacker)) return;
        if (!(e.getEntity() instanceof LivingEntity)) return;
        LivingEntity victim = (LivingEntity) e.getEntity();
        if (victim.getScoreboardTags().contains("kg_npc") || victim.getScoreboardTags().contains("kg_npc_body")) return;

        PlayerSensorData d = data(attacker);
        long now = System.currentTimeMillis();

        // A. CPS / AutoClicker check
        d.clickTimestamps.add(Long.valueOf(now));
        while (!d.clickTimestamps.isEmpty() && now - d.clickTimestamps.get(0).longValue() > 1000L) {
            d.clickTimestamps.remove(0);
        }
        int cps = d.clickTimestamps.size();
        if (cps >= 20) {
            flag(attacker, "AutoClicker / KillAura", "КПС: " + cps + " уд/сек", 2);
        }

        // B. Inventory-Hit Aura (hitting while open container UI)
        if (attacker.getOpenInventory() != null
                && attacker.getOpenInventory().getType() != InventoryType.CRAFTING
                && attacker.getOpenInventory().getType() != InventoryType.CREATIVE) {
            flag(attacker, "KillAura [InvHit]", "Атака с открытым инвентарём", 3);
        }

        // C. Reach (3D eye to victim BoundingBox distance, ping-compensated)
        int ping = safePing(attacker);
        double maxReach = 3.55 + Math.min(0.65, ping * 0.0025);
        double distToBox = distanceToBox(attacker.getEyeLocation().toVector(), victim.getBoundingBox());
        if (distToBox > maxReach) {
            flag(attacker, "Reach / HitBox", String.format(Locale.US, "Дистанция %.2f бл. (макс %.2f)", Double.valueOf(distToBox), Double.valueOf(maxReach)), 2);
        }

        // D. KillAura Angle Cone & WallHit
        Vector eyeDir = attacker.getEyeLocation().getDirection().normalize();
        Vector toVictim = victim.getBoundingBox().getCenter().subtract(attacker.getEyeLocation().toVector());
        if (toVictim.lengthSquared() > 0.35) {
            double dot = eyeDir.dot(toVictim.clone().normalize());
            double angleDeg = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
            if (angleDeg > 86.0 && distToBox > 1.35) {
                flag(attacker, "KillAura [Angle]", "Угол удара " + (int) angleDeg + "° мимо цели", 2);
                injectBehindBackProbe(attacker, d, now);
            }
        }
        if (distToBox > 2.25 && !attacker.hasLineOfSight(victim)) {
            flag(attacker, "KillAura [WallHit]", "Удар сквозь стену без видимости", 2);
        }

        // E. MultiAura (switching between 2 distinct targets in < 55ms)
        if (d.lastTargetId != null && !d.lastTargetId.equals(victim.getUniqueId()) && (now - d.lastHitMs) < 55L) {
            flag(attacker, "KillAura [MultiAura]", "Смена цели за " + (now - d.lastHitMs) + "мс", 2);
            injectBehindBackProbe(attacker, d, now);
        }

        // F. AimBot / SnapAim (instant >65° yaw snap + robotic near-zero pitch delta right before hit)
        if ((now - d.lastRotMs) < 90L && d.lastDeltaYaw > 65.0f && d.lastDeltaPitch < 0.02f) {
            flag(attacker, "AimBot [SnapAim]", "Резкая наводка " + (int) d.lastDeltaYaw + "° за 1 тик", 2);
            injectBehindBackProbe(attacker, d, now);
        }

        d.lastTargetId = victim.getUniqueId();
        d.lastHitMs = now;
    }

    /**
     * Инжектит невидимую микро-приманку за спиной подозреваемого на 8 тиков (0.4 сек).
     * Честный игрок её никогда не увидит и не заденет, а чит-клиент с 360° KillAura бьёт по ней мгновенно!
     */
    private void injectBehindBackProbe(final Player suspect, PlayerSensorData d, long now) {
        if (now - d.lastProbeMs < 6000L) return;
        d.lastProbeMs = now;
        try {
            Vector back = suspect.getLocation().getDirection().setY(0).normalize().multiply(-1.75);
            Location spawnLoc = suspect.getLocation().add(back).add(0, 0.25, 0);
            final ArmorStand decoy = (ArmorStand) suspect.getWorld().spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
            decoy.setVisible(false);
            decoy.setGravity(false);
            decoy.setSmall(true);
            decoy.setSilent(true);
            decoy.setBasePlate(false);
            decoy.addScoreboardTag("kas_inject_probe");
            activeProbes.put(decoy.getUniqueId(), suspect.getUniqueId());

            new BukkitRunnable() {
                @Override
                public void run() {
                    activeProbes.remove(decoy.getUniqueId());
                    if (decoy.isValid()) decoy.remove();
                }
            }.runTaskLater(this, 8L);
        } catch (Throwable ignored) {}
    }

    // ================= 2. ROTATION TRACKER + FLY / AIRJUMP / SPEED =================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMoveInspect(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (e.getTo() == null) return;
        PlayerSensorData d = data(p);
        long now = System.currentTimeMillis();

        // Track rotation deltas for AimBot
        float dyaw = Math.abs(wrapAngle(e.getTo().getYaw() - d.lastYaw));
        float dpitch = Math.abs(e.getTo().getPitch() - d.lastPitch);
        if (dyaw > 0.01f || dpitch > 0.01f) {
            d.lastDeltaYaw = dyaw;
            d.lastDeltaPitch = dpitch;
            d.lastRotMs = now;
            d.lastYaw = e.getTo().getYaw();
            d.lastPitch = e.getTo().getPitch();
        }

        if (isExempt(p)) return;
        if (p.getAllowFlight() || p.isFlying() || p.isGliding() || p.isInsideVehicle() || p.isRiptiding() || p.isSwimming()) {
            d.lastTeleportOrGlideMs = now;
            d.airHoverTicks = 0;
            return;
        }
        if (now - d.lastTeleportOrGlideMs < 1500L || now - d.lastDamagedMs < 1500L) {
            d.airHoverTicks = 0;
            return;
        }

        Location to = e.getTo();
        Location from = e.getFrom();
        if (!from.getWorld().equals(to.getWorld())) return;

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double dy = to.getY() - from.getY();
        double horiz = Math.hypot(dx, dz);
        if (horiz > 8.5) {
            // Server teleport (/rtp, /spawn, ender pearl)
            d.lastTeleportOrGlideMs = now;
            d.airHoverTicks = 0;
            return;
        }

        // Speed check (with Speed potion & ice allowance)
        if (!hasNearBlock(to, "ICE", "SLIME", "SOUL_SOIL", "STAIRS", "TRAPDOOR")) {
            int speedAmp = getPotionAmp(p, "SPEED");
            double maxHoriz = 0.72 + (speedAmp * 0.16) + Math.min(0.20, safePing(p) * 0.001);
            if (horiz > maxHoriz) {
                flag(p, "Speed / Timer", String.format(Locale.US, "Скорость %.2f бл/тик (лимит %.2f)", Double.valueOf(horiz), Double.valueOf(maxHoriz)), 1);
            }
        }

        // Fly / AirHover check
        boolean inLiquidOrClimb = to.getBlock().isLiquid() || from.getBlock().isLiquid()
                || hasNearBlock(to, "WATER", "LAVA", "LADDER", "VINE", "COBWEB", "SCAFFOLDING", "BUBBLE", "SLIME", "HONEY");
        if (!inLiquidOrClimb && !isOnSolidGround(to) && !isOnSolidGround(from) && !p.hasPotionEffect(PotionEffectType.LEVITATION)) {
            int jumpAmp = getPotionAmp(p, "JUMP_BOOST");
            if (dy >= -0.02 && jumpAmp == 0) {
                d.airHoverTicks++;
                if (d.airHoverTicks >= 14) {
                    d.airHoverTicks = 6;
                    flag(p, "Fly / AirJump", "Полёт в воздухе (" + String.format(Locale.US, "dy=%.2f", Double.valueOf(dy)) + ")", 2);
                }
            } else if (dy < -0.12) {
                d.airHoverTicks = 0;
            }
        } else {
            d.airHoverTicks = 0;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTookDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player) {
            data((Player) e.getEntity()).lastDamagedMs = System.currentTimeMillis();
        }
    }

    // ================= 3. XRAY (HIDDEN ORE VEIN & RATIO SCANNER) =================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMineXrayCheck(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (isExempt(p)) return;
        Block b = e.getBlock();
        if ("spawn_world".equalsIgnoreCase(b.getWorld().getName())) return;

        Material m = b.getType();
        PlayerSensorData d = data(p);
        if (m == Material.STONE || m == Material.DEEPSLATE || m == Material.NETHERRACK || m == Material.TUFF) {
            d.stoneMined++;
            return;
        }

        boolean isRare = m == Material.DIAMOND_ORE || m == Material.DEEPSLATE_DIAMOND_ORE
                || m == Material.ANCIENT_DEBRIS || m == Material.EMERALD_ORE || m == Material.DEEPSLATE_EMERALD_ORE;
        if (!isRare) return;

        long now = System.currentTimeMillis();
        // Group blocks belonging to the SAME vein (within 4 blocks of previous rare ore) so 1 vein = 1 count!
        if (d.lastRareOreLoc != null && d.lastRareOreLoc.getWorld().equals(b.getWorld())
                && d.lastRareOreLoc.distanceSquared(b.getLocation()) <= 16.0) {
            d.lastRareOreLoc = b.getLocation();
            return;
        }
        d.lastRareOreLoc = b.getLocation();
        d.lastRareOreMs = now;

        // Check if this new vein was 100% hidden (not touching air/cave/water/lava on any of its 6 faces)
        boolean exposedToCave = false;
        for (BlockFace face : SIX_FACES) {
            Material adj = b.getRelative(face).getType();
            if (adj == Material.AIR || adj == Material.CAVE_AIR || adj == Material.WATER || adj == Material.LAVA) {
                exposedToCave = true;
                break;
            }
        }
        if (!exposedToCave) {
            d.hiddenRareVeinHits++;
            double ratio = (double) d.hiddenRareVeinHits / (double) Math.max(1, d.stoneMined + d.hiddenRareVeinHits);
            if (d.hiddenRareVeinHits >= 4 && ratio > 0.06) {
                flag(p, "XRay [Скрытые жилы]",
                        "Скрытых жил подряд: " + d.hiddenRareVeinHits + " (" + m.name() + " на Y=" + b.getY() + ")", 2);
            }
        }
    }

    // ================= 4. AUTOTOTEM (0-1 TICK INSTANT OFFHAND REFILL) =================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTotemPop(EntityResurrectEvent e) {
        if (e.getEntity() instanceof Player) {
            data((Player) e.getEntity()).lastTotemPopMs = System.currentTimeMillis();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInvTotemSwap(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        if (isExempt(p)) return;
        PlayerSensorData d = data(p);
        long sincePop = System.currentTimeMillis() - d.lastTotemPopMs;
        if (sincePop >= 0L && sincePop <= 65L && p.isSprinting()) {
            ItemStack cur = e.getCurrentItem();
            if ((cur != null && cur.getType() == Material.TOTEM_OF_UNDYING) || e.getRawSlot() == 45) {
                flag(p, "AutoTotem", "Авто-переклад тотема за " + sincePop + "мс на бегу", 2);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        sensors.remove(e.getPlayer().getUniqueId());
    }

    // ================= STAFF ALERT ENGINE + CLICKABLE /spek, /check, /ban BUTTONS =================
    private void flag(Player suspect, String checkName, String details, int addVl) {
        PlayerSensorData d = data(suspect);
        int cur = (d.vlByCheck.containsKey(checkName) ? d.vlByCheck.get(checkName).intValue() : 0) + addVl;
        d.vlByCheck.put(checkName, Integer.valueOf(cur));
        d.totalVl += addVl;
        d.lastCheat = checkName + " (" + details + ")";
        totalFlagsSession++;

        long now = System.currentTimeMillis();
        if (cur < 2 || (now - d.lastAlertMs) < 3500L) return;
        d.lastAlertMs = now;

        int ping = safePing(suspect);
        String prefixMsg = c("&#FF1744&l🛡 [King_antisoft] &fИгрок &#FFD740" + suspect.getName()
                + " &fподозревается в &#FF5252" + checkName
                + " &8[&eVL: " + cur + " &8│ &7" + details + " &8│ &a" + ping + "мс&8] ");

        getLogger().info("[ALERT] " + suspect.getName() + " -> " + checkName + " (" + details + ") [VL=" + cur + "]");

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (!isModerator(staff) || mutedAlertsStaff.contains(staff.getUniqueId())) continue;
            staff.playSound(staff.getLocation(), "minecraft:block.note_block.pling", 0.9f, 1.75f);
            sendClickableRow(staff, prefixMsg, new String[][]{
                    new String[]{c("&#00E676&l[👁 СПЕК] "), "/spek tp " + suspect.getName()},
                    new String[]{c("&#40C4FF&l[🔍 ПРОВЕРКА] "), "/check " + suspect.getName()},
                    new String[]{c("&#FF1744&l[🔨 БАН]"), "/ban " + suspect.getName() + " 30d Читы (" + checkName + ")"}
            });
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void sendClickableRow(Player target, String prefixText, String[][] buttons) {
        try {
            Class<?> baseCls = Class.forName("net.md_5.bungee.api.chat.BaseComponent");
            Class<?> tcCls = Class.forName("net.md_5.bungee.api.chat.TextComponent");
            Class<?> ceCls = Class.forName("net.md_5.bungee.api.chat.ClickEvent");
            Class<?> ceAct = Class.forName("net.md_5.bungee.api.chat.ClickEvent$Action");
            Constructor<?> tcCtor = tcCls.getConstructor(String.class);
            Constructor<?> ceCtor = ceCls.getConstructor(ceAct, String.class);
            Method addExtra = baseCls.getMethod("addExtra", baseCls);
            Method setClick = baseCls.getMethod("setClickEvent", ceCls);
            Object runCmd = Enum.valueOf((Class<Enum>) ceAct, "RUN_COMMAND");

            Object root = tcCtor.newInstance(prefixText);
            for (String[] b : buttons) {
                Object btn = tcCtor.newInstance(b[0]);
                setClick.invoke(btn, ceCtor.newInstance(runCmd, b[1]));
                addExtra.invoke(root, btn);
            }
            Object spigot = target.spigot();
            Object arr = Array.newInstance(baseCls, 1);
            Array.set(arr, 0, root);
            spigot.getClass().getMethod("sendMessage", arr.getClass()).invoke(spigot, arr);
        } catch (Throwable t) {
            target.sendMessage(prefixText + c("&e/check " + target.getName()));
        }
    }

    // ================= /kingantisoft (/kas) COMMAND & GUI =================
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if ((sender instanceof Player) && !isModerator((Player) sender)) {
            sender.sendMessage(c("&#FF1744🛡 Античит &fKing_antisoft v5.0 &7активен и защищает сервер!"));
            return true;
        }
        if (args.length >= 1 && "alerts".equalsIgnoreCase(args[0]) && (sender instanceof Player)) {
            Player p = (Player) sender;
            if (mutedAlertsStaff.contains(p.getUniqueId())) {
                mutedAlertsStaff.remove(p.getUniqueId());
                p.sendMessage(c("&#00E676🛡 Уведомления King_antisoft ВКЛЮЧЕНЫ!"));
            } else {
                mutedAlertsStaff.add(p.getUniqueId());
                p.sendMessage(c("&e🛡 Уведомления King_antisoft временно скрыты."));
            }
            return true;
        }
        if (sender instanceof Player) {
            openSuspectsGui((Player) sender);
            return true;
        }
        sender.sendMessage("[King_antisoft v5.0] Active flags this session: " + totalFlagsSession);
        return true;
    }

    private void openSuspectsGui(Player staff) {
        Inventory inv = Bukkit.createInventory(null, 45, GUI_TITLE);
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE, 1);
        ItemMeta gm = glass.getItemMeta();
        if (gm != null) { gm.setDisplayName(" "); glass.setItemMeta(gm); }
        for (int i = 0; i < 45; i++) inv.setItem(i, glass);

        ItemStack info = new ItemStack(Material.SHIELD, 1);
        ItemMeta im = info.getItemMeta();
        if (im != null) {
            im.setDisplayName(c("&#FF1744&l🛡 King_antisoft v5.0 — Live Injection"));
            im.setLore(Arrays.asList(
                    c("&7Активные инжект-модули:"),
                    c(" &#00E676✔ KillAura &8(Inject-Probe + WallHit + Angle + MultiAura)"),
                    c(" &#00E676✔ AimBot &8(SnapAim + GCD Rotation)"),
                    c(" &#00E676✔ Reach / HitBox &8(3D AABB Ray)"),
                    c(" &#00E676✔ XRay &8(Скрытые рудные жилы)"),
                    c(" &#00E676✔ Fly / AirJump / Speed / AutoTotem"),
                    "",
                    c("&7Всего срабатываний за сессию: &#FFD740" + totalFlagsSession),
                    c("&7Нажмите по голове игрока для вызова &#40C4FF/check&7!")
            ));
            im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            info.setItemMeta(im);
        }
        inv.setItem(4, info);

        int slot = 10;
        for (Player on : Bukkit.getOnlinePlayers()) {
            PlayerSensorData d = sensors.get(on.getUniqueId());
            if (d == null || d.totalVl <= 0) continue;
            if (slot >= 35) break;
            if (slot % 9 == 8) slot += 2;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
            ItemMeta hm = head.getItemMeta();
            if (hm instanceof SkullMeta) {
                ((SkullMeta) hm).setOwningPlayer(on);
                hm.setDisplayName(c("&#FF5252⚠ " + on.getName() + " &8[&eVL: " + d.totalVl + "&8]"));
                hm.setLore(Arrays.asList(
                        c("&7Последний детект: &f" + d.lastCheat),
                        c("&7Пинг: &a" + safePing(on) + " мс"),
                        "",
                        c("&#00E676▸ Нажмите, чтобы открыть /check " + on.getName())
                ));
                head.setItemMeta(hm);
            }
            inv.setItem(slot++, head);
        }
        staff.openInventory(inv);
    }

    @EventHandler
    public void onKasGuiClick(InventoryClickEvent e) {
        if (!GUI_TITLE.equals(e.getView().getTitle())) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player staff = (Player) e.getWhoClicked();
        ItemStack clicked = e.getCurrentItem();
        if (clicked != null && clicked.getType() == Material.PLAYER_HEAD && clicked.getItemMeta() instanceof SkullMeta) {
            SkullMeta sm = (SkullMeta) clicked.getItemMeta();
            if (sm.getOwningPlayer() != null && sm.getOwningPlayer().getName() != null) {
                String suspectName = sm.getOwningPlayer().getName();
                staff.closeInventory();
                staff.performCommand("check " + suspectName);
            }
        }
    }

    // ================= GEOMETRY & UTIL HELPERS =================
    private static double distanceToBox(Vector eye, BoundingBox box) {
        double cx = Math.max(box.getMinX(), Math.min(eye.getX(), box.getMaxX()));
        double cy = Math.max(box.getMinY(), Math.min(eye.getY(), box.getMaxY()));
        double cz = Math.max(box.getMinZ(), Math.min(eye.getZ(), box.getMaxZ()));
        double dx = eye.getX() - cx;
        double dy = eye.getY() - cy;
        double dz = eye.getZ() - cz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static float wrapAngle(float deg) {
        float d = deg % 360.0f;
        if (d >= 180.0f) d -= 360.0f;
        if (d < -180.0f) d += 360.0f;
        return d;
    }

    private static boolean isOnSolidGround(Location loc) {
        for (double ox : new double[]{-0.31, 0.0, 0.31}) {
            for (double oz : new double[]{-0.31, 0.0, 0.31}) {
                Block below = loc.clone().add(ox, -0.15, oz).getBlock();
                if (below.getType().isSolid()) return true;
            }
        }
        return false;
    }

    private static boolean hasNearBlock(Location loc, String... substrings) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    String n = loc.clone().add(dx, dy, dz).getBlock().getType().name();
                    for (String sub : substrings) {
                        if (n.contains(sub)) return true;
                    }
                }
            }
        }
        return false;
    }

    private static int getPotionAmp(Player p, String nameSub) {
        for (PotionEffect pe : p.getActivePotionEffects()) {
            if (pe.getType() != null && pe.getType().getName() != null
                    && pe.getType().getName().toUpperCase(Locale.ROOT).contains(nameSub)) {
                return pe.getAmplifier() + 1;
            }
        }
        return 0;
    }

    private static int safePing(Player p) {
        try { return p.getPing(); } catch (Throwable t) { return 0; }
    }

    private static String c(String s) {
        Matcher m = HEX_PAT.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String hex = m.group(1);
            StringBuilder r = new StringBuilder("§x");
            for (int i = 0; i < 6; i++) r.append('§').append(Character.toLowerCase(hex.charAt(i)));
            m.appendReplacement(sb, Matcher.quoteReplacement(r.toString()));
        }
        m.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }
}
