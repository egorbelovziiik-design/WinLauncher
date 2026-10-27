package net.kinggrief.kingac;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.io.File;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KingAcPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private static final Pattern HEX_PAT = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final BlockFace[] SIX_FACES = new BlockFace[]{
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    // ================= GUI HOLDER =================
    private static final class KacGuiHolder implements InventoryHolder {
        final String menuType; // "main", "reports", "bans", "report_pick"
        final String targetName;
        KacGuiHolder(String menuType, String targetName) {
            this.menuType = menuType;
            this.targetName = targetName;
        }
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    // ================= REPORT & BAN RECORDS =================
    public static final class ReportRecord {
        public final String targetName;
        public final Set<String> reporters = new HashSet<String>();
        public String lastReason;
        public long lastUpdatedMs;

        public ReportRecord(String targetName, String reporter, String reason) {
            this.targetName = targetName;
            this.reporters.add(reporter);
            this.lastReason = reason;
            this.lastUpdatedMs = System.currentTimeMillis();
        }
    }

    public static final class AutoBanEntry {
        public final String playerName;
        public final String reason;
        public final long expiresAtMs;
        public final long createdAtMs;

        public AutoBanEntry(String playerName, String reason, long expiresAtMs, long createdAtMs) {
            this.playerName = playerName;
            this.reason = reason;
            this.expiresAtMs = expiresAtMs;
            this.createdAtMs = createdAtMs;
        }
    }

    private final Map<UUID, PlayerAcData> players = new HashMap<UUID, PlayerAcData>();
    private final Map<String, ReportRecord> activeReports = new LinkedHashMap<String, ReportRecord>();
    private final Map<String, AutoBanEntry> autoBans = new LinkedHashMap<String, AutoBanEntry>();
    private final Map<UUID, Long> reportCooldowns = new HashMap<UUID, Long>();
    private final Set<UUID> mutedAlertsStaff = new HashSet<UUID>();
    private final Set<UUID> ascendingExecution = new HashSet<UUID>();
    private final Map<UUID, UUID> activeProbes = new HashMap<UUID, UUID>();
    private final Set<UUID> wallProbeIds = new HashSet<UUID>();
    private final Map<String, Long> recentBlockPlaces = new HashMap<String, Long>();

    private File bansFile;
    private YamlConfiguration bansYaml;

    private boolean autoBanEnabled = true;
    private int autoBanVl = 18;
    private long autoBanDurationSec = 2592000L; // 30d
    private boolean setbackEnabled = true;
    private boolean microTwitchEnabled = true;
    private int maxAutobanPingMs = 420;
    private int reportCooldownSec = 25;

    private int sessionFlags = 0;
    private int sessionAutoBans = 0;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettingsFromConfig();
        loadBansFile();

        Bukkit.getPluginManager().registerEvents(this, this);
        for (String cmdName : new String[]{"kingac", "report", "reports"}) {
            PluginCommand pc = getCommand(cmdName);
            if (pc != null) {
                pc.setExecutor(this);
                pc.setTabCompleter(this);
            }
        }

        // Gradual VL decay every 40s so rare lag spikes never result in false bans
        new BukkitRunnable() {
            @Override
            public void run() {
                for (PlayerAcData d : players.values()) {
                    if (d.totalVl > 0 && !d.banInProgress) {
                        d.totalVl = Math.max(0, d.totalVl - 1);
                    }
                    if (d.microTwitchBuffer > 0) d.microTwitchBuffer = Math.max(0, d.microTwitchBuffer - 1);
                    if (d.gcdFailBuffer > 0) d.gcdFailBuffer = Math.max(0, d.gcdFailBuffer - 2);
                    if (d.wallHitBuffer > 0) d.wallHitBuffer = Math.max(0, d.wallHitBuffer - 1);
                }
                long cutoff = System.currentTimeMillis() - 5000L;
                for (Iterator<Map.Entry<String, Long>> it = recentBlockPlaces.entrySet().iterator(); it.hasNext(); ) {
                    if (it.next().getValue().longValue() < cutoff) it.remove();
                }
            }
        }.runTaskTimer(this, 800L, 800L);

        getLogger().info("==================================================================");
        getLogger().info(" 🛡 KingАЧ v6.1 — НЕЙРО-АНТИЧИТ И ЗАЩИТА ОТ WALLHIT ЗАПУЩЕНЫ!     ");
        getLogger().info(" ▸ 3D RayTrace Стены/Стекла Трапки + Инжект-Бот Сквозь Стены: ВКЛ ");
        getLogger().info(" ▸ Сенсор Микро-Дёрганий Головой (Micro-Twitch + GCD + Snap): ВКЛ ");
        getLogger().info(" ▸ Инжект-Бот 360° KillAura + HitBox/Reach + AutoTotem + Fly: ВКЛ ");
        getLogger().info(" ▸ Система жалоб (/report, /reports) + Авто-Бан 24/7: ВКЛ         ");
        getLogger().info("==================================================================");
    }

    @Override
    public void onDisable() {
        for (UUID probeId : new ArrayList<UUID>(activeProbes.keySet())) {
            Entity e = Bukkit.getEntity(probeId);
            if (e != null) e.remove();
        }
        activeProbes.clear();
        wallProbeIds.clear();
        recentBlockPlaces.clear();
        ascendingExecution.clear();
        players.clear();
        saveBansFile();
    }

    private void reloadSettingsFromConfig() {
        reloadConfig();
        this.autoBanEnabled = getConfig().getBoolean("settings.auto-ban-enabled", true);
        this.autoBanVl = Math.max(8, getConfig().getInt("settings.auto-ban-vl", 18));
        this.autoBanDurationSec = Math.max(300L, getConfig().getLong("settings.auto-ban-duration-seconds", 2592000L));
        this.setbackEnabled = getConfig().getBoolean("settings.movement-setback-enabled", true);
        this.microTwitchEnabled = getConfig().getBoolean("settings.micro-head-twitch-check", true);
        this.maxAutobanPingMs = Math.max(150, getConfig().getInt("settings.max-autoban-ping-ms", 420));
        this.reportCooldownSec = Math.max(5, getConfig().getInt("settings.report-cooldown-seconds", 25));
    }

    private void persistSettingsToConfig() {
        getConfig().set("settings.auto-ban-enabled", Boolean.valueOf(autoBanEnabled));
        getConfig().set("settings.auto-ban-vl", Integer.valueOf(autoBanVl));
        getConfig().set("settings.movement-setback-enabled", Boolean.valueOf(setbackEnabled));
        getConfig().set("settings.micro-head-twitch-check", Boolean.valueOf(microTwitchEnabled));
        saveConfig();
    }

    // ================= STANDALONE BANS STORAGE =================
    private void loadBansFile() {
        bansFile = new File(getDataFolder(), "bans.yml");
        if (!bansFile.exists()) {
            try {
                getDataFolder().mkdirs();
                bansFile.createNewFile();
            } catch (Exception ignored) {}
        }
        bansYaml = YamlConfiguration.loadConfiguration(bansFile);
        autoBans.clear();
        ConfigurationSection sec = bansYaml.getConfigurationSection("bans");
        if (sec != null) {
            long now = System.currentTimeMillis();
            for (String key : sec.getKeys(false)) {
                long exp = sec.getLong(key + ".expires", 0L);
                if (exp > now) {
                    String name = sec.getString(key + ".name", key);
                    String reason = sec.getString(key + ".reason", "Читы [KingАЧ]");
                    long created = sec.getLong(key + ".created", now);
                    autoBans.put(key.toLowerCase(Locale.ROOT), new AutoBanEntry(name, reason, exp, created));
                }
            }
        }
    }

    private void saveBansFile() {
        if (bansYaml == null || bansFile == null) return;
        bansYaml.set("bans", null);
        long now = System.currentTimeMillis();
        for (Map.Entry<String, AutoBanEntry> en : autoBans.entrySet()) {
            AutoBanEntry b = en.getValue();
            if (b.expiresAtMs <= now) continue;
            String k = "bans." + en.getKey();
            bansYaml.set(k + ".name", b.playerName);
            bansYaml.set(k + ".reason", b.reason);
            bansYaml.set(k + ".expires", Long.valueOf(b.expiresAtMs));
            bansYaml.set(k + ".created", Long.valueOf(b.createdAtMs));
        }
        try {
            bansYaml.save(bansFile);
        } catch (Exception ignored) {}
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLoginCheckBan(PlayerLoginEvent e) {
        String key = e.getPlayer().getName().toLowerCase(Locale.ROOT);
        AutoBanEntry b = autoBans.get(key);
        if (b == null) return;
        long now = System.currentTimeMillis();
        if (b.expiresAtMs <= now) {
            autoBans.remove(key);
            saveBansFile();
            return;
        }
        if (isOwnerPlayer(e.getPlayer())) {
            autoBans.remove(key);
            saveBansFile();
            return;
        }
        long leftSec = Math.max(1L, (b.expiresAtMs - now) / 1000L);
        e.disallow(PlayerLoginEvent.Result.KICK_BANNED, buildBanScreen(b.reason, leftSec));
    }

    private String buildBanScreen(String reason, long leftSec) {
        return c("&#FF1744&l🛡 KingАЧ — АВТОМАТИЧЕСКАЯ ЗАЩИТА СЕРВЕРА\n\n"
                + "&fВаш аккаунт заблокирован нейро-античитом &#FFD740&lKingАЧ&f!\n\n"
                + "&7Детект / Причина: &#FF5252" + reason + "\n"
                + "&7Осталось до разбана: &#00E5FF" + formatDuration(leftSec) + "\n\n"
                + "&7Апелляция в Discord: &#80D8FFhttps://discord.gg/7Cugj4X3eU\n"
                + "&7Сайт сервера: &#FFD740https://kinggrief.vercel.app");
    }

    // ================= ROLE & KINGGRIEFCORE INTEGRATION =================
    private String getKingGriefRankId(Player p) {
        if (p == null) return "";
        try {
            Plugin kg = Bukkit.getPluginManager().getPlugin("KingGriefCore");
            if (kg != null && kg.isEnabled()) {
                Method getRanks = kg.getClass().getMethod("getRanks");
                Object ranks = getRanks.invoke(kg);
                Method getRank = ranks.getClass().getMethod("getRank", Player.class);
                Object rDef = getRank.invoke(ranks, p);
                if (rDef != null) {
                    Object idObj = rDef.getClass().getField("id").get(rDef);
                    return idObj != null ? String.valueOf(idObj) : "";
                }
            }
        } catch (Throwable ignored) {}
        return "";
    }

    private boolean isOwnerPlayer(Player p) {
        if (p == null) return false;
        String rid = getKingGriefRankId(p);
        if (!rid.isEmpty()) {
            return "owner".equalsIgnoreCase(rid);
        }
        return false;
    }

    private boolean isStaffMember(Player p) {
        if (p == null) return false;
        if (isOwnerPlayer(p)) return true;
        try {
            Plugin kg = Bukkit.getPluginManager().getPlugin("KingGriefCore");
            if (kg != null && kg.isEnabled()) {
                Method getMod = kg.getClass().getMethod("getModeration");
                Object mod = getMod.invoke(kg);
                if (mod != null) {
                    Method isStaff = mod.getClass().getMethod("isStaff", Player.class);
                    return Boolean.TRUE.equals(isStaff.invoke(mod, p));
                }
            }
        } catch (Throwable ignored) {}
        return p.hasPermission("kingac.staff") || p.hasPermission("kingac.admin") || p.isOp();
    }

    private boolean isAdminController(CommandSender s) {
        if (!(s instanceof Player)) return true;
        Player p = (Player) s;
        String rid = getKingGriefRankId(p);
        if (!rid.isEmpty()) {
            return "owner".equalsIgnoreCase(rid) || "techadmin".equalsIgnoreCase(rid) || "curator".equalsIgnoreCase(rid);
        }
        return p.hasPermission("kingac.admin") || p.isOp();
    }

    private boolean isExempt(Player p) {
        if (p == null) return true;
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return true;
        return isOwnerPlayer(p);
    }

    private PlayerAcData data(Player p) {
        PlayerAcData d = players.get(p.getUniqueId());
        if (d == null) {
            d = new PlayerAcData();
            d.lastYaw = p.getLocation().getYaw();
            d.lastPitch = p.getLocation().getPitch();
            d.lastSafeGroundLoc = p.getLocation().clone();
            players.put(p.getUniqueId(), d);
        }
        return d;
    }

    // =================================================================================
    //  1. ГЛУБОКИЙ АНАЛИЗ МИКРО-ДЁРГАНИЙ ГОЛОВОЙ (MICRO-TWITCH / GCD / SNAP) + ДВИЖЕНИЕ
    // =================================================================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMoveAndRotation(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (ascendingExecution.contains(p.getUniqueId())) {
            // Keep target locked horizontally while floating up during KingАЧ execution!
            if (e.getTo() != null) {
                Location f = e.getFrom();
                Location t = e.getTo();
                if (Math.abs(t.getX() - f.getX()) > 0.05 || Math.abs(t.getZ() - f.getZ()) > 0.05) {
                    e.setTo(new Location(f.getWorld(), f.getX(), Math.max(f.getY(), t.getY()), f.getZ(), t.getYaw(), t.getPitch()));
                }
            }
            return;
        }

        Location to = e.getTo();
        Location from = e.getFrom();
        if (to == null || from.getWorld() == null || !from.getWorld().equals(to.getWorld())) return;

        PlayerAcData d = data(p);
        long now = System.currentTimeMillis();

        // -------- A. СЕНСОР ВРАЩЕНИЯ И МИКРО-ДЁРГАНИЙ ГОЛОВОЙ --------
        float signedDyaw = wrapAngle(to.getYaw() - d.lastYaw);
        float signedDpitch = to.getPitch() - d.lastPitch;
        float absYaw = Math.abs(signedDyaw);
        float absPitch = Math.abs(signedDpitch);

        if (absYaw > 0.0004f || absPitch > 0.0004f) {
            if (microTwitchEnabled && !isExempt(p)) {
                analyzeMicroHeadTwitchAndAim(p, d, signedDyaw, signedDpitch, to.getPitch(), now);
            }
            d.prevDeltaYaw = signedDyaw;
            d.prevDeltaPitch = signedDpitch;
            d.lastYaw = to.getYaw();
            d.lastPitch = to.getPitch();
            d.lastRotMs = now;
        }

        // -------- B. ПРОВЕРКИ ДВИЖЕНИЯ (FLY / SPEED / AIRSTRAFE / JESUS / NOSLOW) --------
        if (isExempt(p)) return;
        if (p.getAllowFlight() || p.isFlying() || p.isGliding() || p.isInsideVehicle() || p.isRiptiding() || p.isSwimming()) {
            d.lastTeleportOrGlideMs = now;
            d.airHoverTicks = 0;
            d.waterWalkTicks = 0;
            d.noSlowTicks = 0;
            return;
        }

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double dy = to.getY() - from.getY();
        double horiz = Math.hypot(dx, dz);

        if (horiz > 8.5) {
            d.lastTeleportOrGlideMs = now;
            d.airHoverTicks = 0;
            return;
        }

        boolean onGround = isOnSolidGround(to) || isOnSolidGround(from);
        if (onGround && !to.getBlock().isLiquid()) {
            d.lastSafeGroundLoc = from.clone();
        }

        if (now - d.lastTeleportOrGlideMs < 1500L) {
            d.airHoverTicks = 0;
            d.speedBuffer = 0;
            return;
        }

        boolean recentlyDamaged = (now - d.lastDamagedMs) < 1400L;
        int ping = safePing(p);

        // 1. Speed / Bhop Check
        if (!recentlyDamaged && !hasNearBlock(to, "ICE", "SLIME", "SOUL", "STAIRS", "TRAPDOOR", "BED", "PISTON")) {
            int speedAmp = getPotionAmp(p, "SPEED");
            double maxHoriz = 0.70 + (speedAmp * 0.155) + Math.min(0.22, ping * 0.0009);
            if (horiz > maxHoriz) {
                d.speedBuffer += 2;
                if (d.speedBuffer >= 4) {
                    d.speedBuffer = 1;
                    flag(p, "Speed / Bhop", String.format(Locale.US, "Скорость %.2f бл/т (макс %.2f)", Double.valueOf(horiz), Double.valueOf(maxHoriz)), 2, true);
                    applySetback(p, d, e);
                    return;
                }
            } else if (d.speedBuffer > 0) {
                d.speedBuffer--;
            }
        }

        // 2. AirStrafe (резкая смена вектора движения в воздухе > 115° без потери скорости!)
        if (!onGround && !recentlyDamaged && horiz > 0.29 && Math.hypot(d.lastAirDx, d.lastAirDz) > 0.29) {
            Vector prevV = new Vector(d.lastAirDx, 0, d.lastAirDz).normalize();
            Vector curV = new Vector(dx, 0, dz).normalize();
            double turnDeg = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, prevV.dot(curV)))));
            if (turnDeg > 118.0 && !hasNearBlock(to, "WATER", "LAVA", "LADDER", "VINE", "COBWEB")) {
                d.airStrafeBuffer += 2;
                if (d.airStrafeBuffer >= 5) {
                    d.airStrafeBuffer = 1;
                    flag(p, "Strafe [AirTurn]", "Разворот в воздухе на " + (int) turnDeg + "° без потери скорости", 2, false);
                    applySetback(p, d, e);
                }
            } else if (d.airStrafeBuffer > 0) {
                d.airStrafeBuffer--;
            }
        }
        if (!onGround) {
            d.lastAirDx = dx;
            d.lastAirDz = dz;
        } else {
            d.lastAirDx = 0.0;
            d.lastAirDz = 0.0;
        }

        // 3. Fly / AirJump / Hover Check
        boolean inLiquidOrClimb = to.getBlock().isLiquid() || from.getBlock().isLiquid()
                || hasNearBlock(to, "WATER", "LAVA", "LADDER", "VINE", "COBWEB", "SCAFFOLDING", "BUBBLE", "SLIME", "HONEY", "LILY_PAD");
        if (!inLiquidOrClimb && !onGround && !recentlyDamaged && !p.hasPotionEffect(PotionEffectType.LEVITATION)) {
            int jumpAmp = getPotionAmp(p, "JUMP");
            if (dy >= -0.025 && jumpAmp == 0 && (now - d.lastBlockPlaceMs) > 900L) {
                d.airHoverTicks++;
                if (d.airHoverTicks >= 13) {
                    d.airHoverTicks = 5;
                    flag(p, "Fly / AirJump", String.format(Locale.US, "Полёт в воздухе (dy=%.2f)", Double.valueOf(dy)), 3, false);
                    applySetback(p, d, e);
                    return;
                }
            } else if (dy < -0.11) {
                d.airHoverTicks = 0;
            }
        } else {
            d.airHoverTicks = 0;
        }

        // 4. Jesus [WaterWalk] (бег ровно по поверхности воды dy == 0 без кувшинок и льда!)
        Block below = to.clone().add(0, -0.12, 0).getBlock();
        if (below.getType() == Material.WATER && to.getBlock().getType() == Material.AIR && !onGround
                && !hasNearBlock(to, "LILY_PAD", "ICE", "SLAB", "STAIRS", "TRAPDOOR")) {
            if (Math.abs(dy) < 0.004 && horiz > 0.16) {
                d.waterWalkTicks++;
                if (d.waterWalkTicks >= 10) {
                    d.waterWalkTicks = 3;
                    flag(p, "Jesus [WaterWalk]", "Ходьба по поверхности воды", 2, false);
                    applySetback(p, d, e);
                    return;
                }
            } else {
                d.waterWalkTicks = Math.max(0, d.waterWalkTicks - 1);
            }
        } else {
            d.waterWalkTicks = 0;
        }

        // 5. NoSlow (полный спринт с зажатым щитом или поеданием предмета!)
        if (onGround && !recentlyDamaged && (p.isBlocking() || p.isHandRaised())) {
            int speedAmp = getPotionAmp(p, "SPEED");
            double maxBlockedSpeed = 0.21 + (speedAmp * 0.05);
            if (horiz > maxBlockedSpeed) {
                d.noSlowTicks++;
                if (d.noSlowTicks >= 9) {
                    d.noSlowTicks = 3;
                    flag(p, "NoSlow [Щит/Еда]", String.format(Locale.US, "Спринт с блоком/едой (%.2f бл/т)", Double.valueOf(horiz)), 2, false);
                    applySetback(p, d, e);
                }
            } else if (d.noSlowTicks > 0) {
                d.noSlowTicks--;
            }
        } else {
            d.noSlowTicks = 0;
        }
    }

    /**
     * МАТЕМАТИЧЕСКИЙ НЕЙРО-АНАЛИЗАТОР МИКРО-ДЁРГАНИЙ ГОЛОВОЙ (Micro-Twitch / Jitter / GCD / Snap-Back).
     * Ловит все современные СНГ чит-клиенты (Celestial, Nursultan, Expensive, Wexside, Catlavan, Grim-Bypass),
     * которые делают высокочастотные микро-рывки головой и синтетический рандом углов в бою!
     */
    private void analyzeMicroHeadTwitchAndAim(Player p, PlayerAcData d, float dyaw, float dpitch, float curPitch, long now) {
        if (d.recentDyaw.size() >= 20) {
            d.recentDyaw.pollFirst();
            d.recentDpitch.pollFirst();
            d.recentRotTimes.pollFirst();
        }
        d.recentDyaw.addLast(Float.valueOf(dyaw));
        d.recentDpitch.addLast(Float.valueOf(dpitch));
        d.recentRotTimes.addLast(Long.valueOf(now));

        boolean inCombatWindow = (now - d.lastCombatMs) <= 2200L;
        if (!inCombatWindow) return;

        float absYaw = Math.abs(dyaw);
        float absPitch = Math.abs(dpitch);
        int sensitivityMult = d.isUnderDeepWatch() ? 2 : 1;

        // 1. ДЕТЕКТОР ОТСКОКА КАМЕРЫ ПОСЛЕ МИКРО-ДОВОДКИ НА УДАР (Snap-Back Twitch)
        if (d.pendingHitSnapMs > 0L && (now - d.pendingHitSnapMs) <= 120L) {
            // Если на ударе камера резко прыгнула на цель (4.5°..45°), а через 1-2 тика дёрнулась обратно в противоположную сторону!
            if ((dyaw * d.pendingHitSnapYaw) < 0f && absYaw >= 3.2f && absYaw <= 45.0f) {
                d.pendingHitSnapMs = 0L;
                d.preHitSnapBuffer += 2 * sensitivityMult;
                if (d.preHitSnapBuffer >= 5) {
                    d.preHitSnapBuffer = 1;
                    d.combatConfirmations++;
                    flag(p, "Aim [Микро-Доводка]",
                            String.format(Locale.US, "Рывок на цель %.1f° и отскок -%.1f° за 1 тик",
                                    Float.valueOf(Math.abs(d.pendingHitSnapYaw)), Float.valueOf(absYaw)),
                            3, false);
                    injectInvisibleDecoyProbe(p, d, now, true);
                }
            }
        }

        // 2. ДЕТЕКТОР ВЫСОКОЧАСТОТНОГО МИКРО-ДЁРГАНИЯ ГОЛОВОЙ (Sawtooth +-+- Jitter in PvP)
        if (d.recentDyaw.size() >= 16) {
            Float[] ys = d.recentDyaw.toArray(new Float[0]);
            Float[] ps = d.recentDpitch.toArray(new Float[0]);
            Long[] ts = d.recentRotTimes.toArray(new Long[0]);
            long spanMs = ts[ts.length - 1].longValue() - ts[ts.length - 16].longValue();

            if (spanMs > 0L && spanMs <= 1350L) {
                int yawSignFlips = 0;
                int pitchSignFlips = 0;
                int microAmpCount = 0;

                for (int i = ys.length - 15; i < ys.length; i++) {
                    float y0 = ys[i - 1].floatValue();
                    float y1 = ys[i].floatValue();
                    float p0 = ps[i - 1].floatValue();
                    float p1 = ps[i].floatValue();

                    if ((y0 * y1) < 0f && Math.abs(y1) >= 0.09f && Math.abs(y1) <= 4.4f && Math.abs(y0) <= 4.4f) {
                        yawSignFlips++;
                    }
                    if ((p0 * p1) < 0f && Math.abs(p1) >= 0.06f && Math.abs(p1) <= 3.6f && Math.abs(p0) <= 3.6f) {
                        pitchSignFlips++;
                    }
                    if ((Math.abs(y1) >= 0.10f && Math.abs(y1) <= 2.8f) || (Math.abs(p1) >= 0.08f && Math.abs(p1) <= 2.4f)) {
                        microAmpCount++;
                    }
                }

                int twitchScore = Math.max(yawSignFlips, pitchSignFlips) + (Math.min(yawSignFlips, pitchSignFlips) / 2);
                if (twitchScore > d.maxMicroTwitchScore) d.maxMicroTwitchScore = twitchScore;

                if ((yawSignFlips >= 9 || pitchSignFlips >= 9 || twitchScore >= 11) && microAmpCount >= 10) {
                    d.microTwitchBuffer += 2 * sensitivityMult;
                    if (d.microTwitchBuffer >= 5) {
                        d.microTwitchBuffer = 1;
                        d.combatConfirmations++;
                        flag(p, "Aim [Микро-Дёргание]",
                                "Микро-тремор головы в PvP (рывков: " + twitchScore + "/15 за " + spanMs + "мс)",
                                3, false);
                        injectInvisibleDecoyProbe(p, d, now, true);
                    }
                } else if (d.microTwitchBuffer > 0 && yawSignFlips <= 3 && pitchSignFlips <= 3) {
                    d.microTwitchBuffer--;
                }
            }
        }

        // 3. МАТЕМАТИЧЕСКАЯ ПРОВЕРКА ШАГА СЕНСЫ МЫШИ (GCD Quantization — ловит плавный аим и рандомизацию!)
        if (Math.abs(curPitch) < 88.5f && absPitch >= 0.12f && absPitch <= 16.0f
                && Math.abs(d.prevDeltaPitch) >= 0.12f && Math.abs(d.prevDeltaPitch) <= 16.0f
                && absYaw >= 0.15f) {
            long curScaled = (long) (absPitch * 16777216.0f);
            long prevScaled = (long) (Math.abs(d.prevDeltaPitch) * 16777216.0f);
            long g = gcd(curScaled, prevScaled);
            if (g < 16384L) {
                d.gcdFailBuffer += sensitivityMult;
                if (d.gcdFailBuffer >= 11) {
                    d.gcdFailBuffer = 4;
                    d.combatConfirmations++;
                    flag(p, "Aim [Синтетик-Наводка]",
                            "Сбой шага мыши GCD (микро-подрутка углов, gcd=" + g + ")",
                            2, false);
                }
            } else if (d.gcdFailBuffer > 0) {
                d.gcdFailBuffer = Math.max(0, d.gcdFailBuffer - 2);
            }
        }

        // 4. РОБОТИЗИРОВАННАЯ ГОРИЗОНТАЛЬНАЯ НАВОДКА (Yaw двигается > 4.5°, а Pitch = 0.0000°)
        if (Math.abs(curPitch) < 88.0f && absYaw >= 4.5f && absPitch < 0.0001f) {
            d.flatSnapBuffer += sensitivityMult;
            if (d.flatSnapBuffer >= 7) {
                d.flatSnapBuffer = 2;
                d.combatConfirmations++;
                flag(p, "Aim [Горизонт-Лок]",
                        String.format(Locale.US, "Робот-наводка ΔYaw=%.1f° при ΔPitch=0.000°", Float.valueOf(absYaw)),
                        2, false);
            }
        } else if (absPitch > 0.05f && d.flatSnapBuffer > 0) {
            d.flatSnapBuffer--;
        }
    }

    private void applySetback(Player p, PlayerAcData d, PlayerMoveEvent e) {
        if (!setbackEnabled || d.banInProgress) return;
        if (d.lastSafeGroundLoc != null && d.lastSafeGroundLoc.getWorld() != null
                && d.lastSafeGroundLoc.getWorld().equals(p.getWorld())
                && d.lastSafeGroundLoc.distanceSquared(p.getLocation()) <= 64.0) {
            Location sb = d.lastSafeGroundLoc.clone();
            sb.setYaw(p.getLocation().getYaw());
            sb.setPitch(p.getLocation().getPitch());
            e.setTo(sb);
        }
    }

    // =================================================================================
    //  2. БОЕВОЙ ИНСПЕКТОР (KILLAURA PROBE + INVHIT + SHIELDHIT + REACH + CRITS + CPS)
    // =================================================================================
    @EventHandler(priority = EventPriority.HIGH)
    public void onCombatInspect(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player)) return;
        Player attacker = (Player) e.getDamager();

        // 1. Проверка попадания по НЕВИДИМОМУ ИНЖЕКТ-БОТУ KingАЧ (360° ИЛИ СКВОЗЬ СТЕНУ!)
        UUID probeSuspect = activeProbes.get(e.getEntity().getUniqueId());
        if (probeSuspect != null) {
            e.setCancelled(true);
            Entity probe = e.getEntity();
            boolean isWallProbe = wallProbeIds.remove(probe.getUniqueId());
            activeProbes.remove(probe.getUniqueId());
            Block stillBlockedBy = (isWallProbe && probe instanceof LivingEntity)
                    ? findBlockingWallBetween(attacker, (LivingEntity) probe)
                    : null;
            probe.remove();
            if (probeSuspect.equals(attacker.getUniqueId()) && !isExempt(attacker)
                    && e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
                PlayerAcData d = data(attacker);
                if (isWallProbe) {
                    if (stillBlockedBy != null) {
                        // Честный клиент ФИЗИЧЕСКИ не может кликнуть сущность за сплошным блоком!
                        d.combatConfirmations += 3;
                        d.wallHitBuffer += 2;
                        int addVl = d.wallHitBuffer >= 3 ? Math.max(9, autoBanVl) : 9;
                        flag(attacker, "WallAura [Инжект Через Стену]",
                                "Пробил проверочного инжект-бота сквозь " + stillBlockedBy.getType().name(),
                                addVl, false);
                    }
                } else {
                    d.combatConfirmations += 2;
                    flag(attacker, "KillAura [Инжект-Бот 360°]", "Атаковал невидимую приманку за спиной", 5, false);
                }
            }
            return;
        }

        if (e.isCancelled() || isExempt(attacker)) return;
        // Проверяем только прямые удары рукой/оружием (исключаем Sweeping Edge и Шипы/Thorns!)
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!(e.getEntity() instanceof LivingEntity)) return;
        LivingEntity victim = (LivingEntity) e.getEntity();
        if (victim.getScoreboardTags().contains("kg_npc")
                || victim.getScoreboardTags().contains("kg_npc_body")
                || victim.getScoreboardTags().contains("kg_holo")) {
            return;
        }

        PlayerAcData d = data(attacker);
        long now = System.currentTimeMillis();
        d.lastCombatMs = now;

        // Если игрок под усиленной слежкой после /report — сразу инжектим невидимого проверочного бота за спиной!
        if (d.isUnderDeepWatch()) {
            injectInvisibleDecoyProbe(attacker, d, now, false);
        }

        // Записываем рывок камеры прямо в тик удара (для детекта Snap-Back на следующем тике!)
        float curSnapYaw = d.prevDeltaYaw;
        if ((now - d.lastRotMs) <= 85L && Math.abs(curSnapYaw) >= 4.5f && Math.abs(curSnapYaw) <= 55.0f) {
            d.pendingHitSnapYaw = curSnapYaw;
            d.pendingHitSnapMs = now;
        }

        // 2. AutoClicker (CPS >= 21 + Макрос с постоянной задержкой stdDev < 3.2мс)
        d.clickTimes.add(Long.valueOf(now));
        while (!d.clickTimes.isEmpty() && (now - d.clickTimes.get(0).longValue()) > 1000L) {
            d.clickTimes.remove(0);
        }
        if (d.prevClickMs > 0L) {
            long diff = now - d.prevClickMs;
            if (diff > 5L && diff < 250L) {
                if (d.clickIntervals.size() >= 18) d.clickIntervals.pollFirst();
                d.clickIntervals.addLast(Long.valueOf(diff));
            }
        }
        d.prevClickMs = now;

        int cps = d.clickTimes.size();
        if (cps >= 21) {
            d.combatConfirmations++;
            flag(attacker, "AutoClicker [CPS]", "Скорость кликов: " + cps + " КПС", 2, false);
        } else if (cps >= 12 && d.clickIntervals.size() >= 16) {
            double mean = 0.0;
            for (Long iv : d.clickIntervals) mean += iv.doubleValue();
            mean /= d.clickIntervals.size();
            double var = 0.0;
            for (Long iv : d.clickIntervals) {
                double diff = iv.doubleValue() - mean;
                var += diff * diff;
            }
            double stdDev = Math.sqrt(var / d.clickIntervals.size());
            if (stdDev < 3.2 && mean < 95.0) {
                d.clickIntervals.clear();
                d.combatConfirmations++;
                flag(attacker, "AutoClicker [Макрос]", String.format(Locale.US, "Робот-тайминг кликов (±%.1fмс)", Double.valueOf(stdDev)), 2, false);
            }
        }

        // 3. KillAura [InvHit] (Удар с открытым инвентарём / сундуком)
        if (attacker.getOpenInventory() != null
                && attacker.getOpenInventory().getType() != InventoryType.CRAFTING
                && attacker.getOpenInventory().getType() != InventoryType.CREATIVE) {
            e.setCancelled(true);
            d.combatConfirmations += 2;
            flag(attacker, "KillAura [Удар в GUI]", "Ударил врага с открытым меню/сундуком", 4, false);
            return;
        }

        // 4. KillAura [ShieldHit / EatHit] (Атака сквозь зажатый щит или поедание гепла!)
        if (attacker.isBlocking() || attacker.isHandRaised()) {
            e.setCancelled(true);
            d.combatConfirmations += 2;
            flag(attacker, "KillAura [Щит/Еда]", "Ударил врага, не отпуская щит/еду", 4, false);
            return;
        }

        // 5. Reach / HitBox (Точный 3D луч до BoundingBox с пинг-компенсацией)
        int ping = safePing(attacker);
        double maxReach = 3.38 + (attacker.getGameMode() == GameMode.CREATIVE ? 1.5 : 0.0) + Math.min(0.62, ping * 0.0024);
        double distToBox = distanceToBox(attacker.getEyeLocation().toVector(), victim.getBoundingBox());
        if (distToBox > maxReach) {
            d.reachBuffer += 2;
            if (d.reachBuffer >= 4) {
                d.reachBuffer = 1;
                if (distToBox > maxReach + 0.45) e.setCancelled(true);
                flag(attacker, "Reach / HitBox",
                        String.format(Locale.US, "Удар за %.2f бл. (лимит %.2f)", Double.valueOf(distToBox), Double.valueOf(maxReach)),
                        3, true);
                injectInvisibleDecoyProbe(attacker, d, now, false);
            }
        } else if (d.reachBuffer > 0) {
            d.reachBuffer--;
        }

        // 6. KillAura [Angle & 3D Voxel RayTrace WallHit + Through-Wall Inject-Bot]
        Vector eyeDir = attacker.getEyeLocation().getDirection().normalize();
        Vector toVictim = victim.getBoundingBox().getCenter().subtract(attacker.getEyeLocation().toVector());
        if (toVictim.lengthSquared() > 0.35) {
            double dot = eyeDir.dot(toVictim.clone().normalize());
            double angleDeg = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
            if (angleDeg > 86.0 && distToBox > 1.30) {
                e.setCancelled(true);
                d.combatConfirmations++;
                flag(attacker, "KillAura [Мимо Хитбокса]", "Удар под углом " + (int) angleDeg + "° мимо цели", 3, false);
                injectInvisibleDecoyProbe(attacker, d, now, true);
            }
        }

        // ТОЧНЫЙ 13-ТОЧЕЧНЫЙ 3D RAYTRACE СКВОЗЬ СТЕНЫ / СТЕКЛО ТРАПКИ / ДВЕРИ / КАБИНКИ (от 0.15м до 6.0м!)
        Block wallBlock = findBlockingWallBetween(attacker, victim);
        if (wallBlock != null) {
            // МГНОВЕННО блокируем урон сквозь любую стену / трапку / пласт / стекло!
            e.setCancelled(true);
            // Сразу инжектируем проверочного бота за эту же стену, чтобы на следующем ударе WallAura попалась со 100% точностью!
            injectThroughWallProbe(attacker, d, wallBlock, now, true);

            if (!wasBlockJustPlaced(wallBlock, now, 320L)) {
                d.wallHitBuffer++;
                d.combatConfirmations += 2;
                flag(attacker, "WallHit [Удар Через Стену]",
                        "Атака сквозь " + wallBlock.getType().name() + String.format(Locale.US, " (%.2fм)", Double.valueOf(distToBox)),
                        6, false);
            }
            return;
        }

        // 7. KillAura [MultiAura] (смена 2 разных целей быстрее 50мс)
        if (d.lastTargetId != null && !d.lastTargetId.equals(victim.getUniqueId()) && (now - d.lastCombatMs) < 50L) {
            d.combatConfirmations++;
            flag(attacker, "KillAura [Мульти-Аура]", "Переключение между 2 целями за <50мс", 3, false);
            injectInvisibleDecoyProbe(attacker, d, now, true);
        }

        // 8. Criticals [Packet-Crit] (критический удар стоя на земле / с фейк-падением < 0.05)
        float fall = attacker.getFallDistance();
        if (fall > 0.0f && fall < 0.045f && isOnSolidGround(attacker.getLocation())) {
            d.fakeCritBuffer += 2;
            if (d.fakeCritBuffer >= 5) {
                d.fakeCritBuffer = 1;
                d.combatConfirmations++;
                flag(attacker, "Criticals [Пакет-Крит]", String.format(Locale.US, "Фейк-крит на месте (fall=%.4f)", Float.valueOf(fall)), 3, false);
            }
        } else if (d.fakeCritBuffer > 0 && fall > 0.15f) {
            d.fakeCritBuffer--;
        }

        // 9. Классический резкий AimBot Snap (> 68° за 1 тик с нулевым Pitch)
        if ((now - d.lastRotMs) < 85L && Math.abs(d.prevDeltaYaw) > 68.0f && Math.abs(d.prevDeltaPitch) < 0.015f) {
            d.combatConfirmations++;
            flag(attacker, "AimBot [Резкий Разворот]", "Мгновенный разворот на " + (int) Math.abs(d.prevDeltaYaw) + "°", 3, false);
            injectInvisibleDecoyProbe(attacker, d, now, true);
        }

        d.lastTargetId = victim.getUniqueId();
    }

    /**
     * Инжектирует невидимого микро-бота за спиной/сбоку подозреваемого на 7 тиков (0.35 сек).
     * Честный игрок физически не видит его и не смотрит назад, а любая 360° KillAura / RageAura бьёт по нему!
     */
    private void injectInvisibleDecoyProbe(final Player suspect, PlayerAcData d, long now, boolean forceFast) {
        long cd = (forceFast || d.isUnderDeepWatch()) ? 2500L : 5500L;
        if (now - d.lastProbeMs < cd) return;
        d.lastProbeMs = now;
        try {
            Vector back = suspect.getLocation().getDirection().setY(0).normalize().multiply(-1.65);
            Location spawnLoc = suspect.getLocation().add(back).add(0, 0.35, 0);
            final ArmorStand decoy = (ArmorStand) suspect.getWorld().spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
            decoy.setPersistent(false);
            decoy.setVisible(false);
            decoy.setGravity(false);
            decoy.setSmall(true);
            decoy.setSilent(true);
            decoy.setBasePlate(false);
            decoy.addScoreboardTag("kingac_probe");
            activeProbes.put(decoy.getUniqueId(), suspect.getUniqueId());

            new BukkitRunnable() {
                @Override
                public void run() {
                    activeProbes.remove(decoy.getUniqueId());
                    if (decoy.isValid()) decoy.remove();
                }
            }.runTaskLater(this, 7L);
        } catch (Throwable ignored) {}
    }

    /**
     * 13-лучевая 3D воксельная проверка прямой видимости между глазами атакующего и всем BoundingBox цели.
     * Возвращает преграждающий блок ТОЛЬКО если ВСЕ 13 лучей (голова, центр, ноги, все 8 углов хитбокса
     * + микро-сдвиг глаз ±0.14м на интерполяцию клиента) упираются в твёрдую коллизию блока (включая стекло Трапки, двери, стены 1 блок!).
     * Исключает любые ложные срабатывания при ударе по пяткам под полублоком или из-за угла.
     */
    private Block findBlockingWallBetween(Player attacker, LivingEntity target) {
        if (attacker == null || target == null || !attacker.getWorld().equals(target.getWorld())) return null;
        World world = attacker.getWorld();
        Location eyeLoc = attacker.getEyeLocation();
        Vector eye = eyeLoc.toVector();
        BoundingBox box = target.getBoundingBox().clone().expand(0.06);
        if (box.contains(eye)) return null;

        double minX = box.getMinX(), maxX = box.getMaxX();
        double minY = box.getMinY() + 0.05, maxY = Math.max(minY + 0.05, box.getMaxY() - 0.05);
        double minZ = box.getMinZ(), maxZ = box.getMaxZ();
        double cx = (minX + maxX) * 0.5;
        double cy = (minY + maxY) * 0.5;
        double cz = (minZ + maxZ) * 0.5;

        Vector[] samplePoints = new Vector[]{
                new Vector(cx, cy, cz),
                new Vector(cx, maxY, cz),
                new Vector(cx, minY, cz),
                new Vector(minX, maxY, minZ),
                new Vector(maxX, maxY, minZ),
                new Vector(minX, maxY, maxZ),
                new Vector(maxX, maxY, maxZ),
                new Vector(minX, minY, minZ),
                new Vector(maxX, minY, minZ),
                new Vector(minX, minY, maxZ),
                new Vector(maxX, minY, maxZ)
        };

        Block primaryWall = null;
        for (int i = 0; i < samplePoints.length; i++) {
            Vector delta = samplePoints[i].clone().subtract(eye);
            double len = delta.length();
            if (len <= 0.16) return null;
            RayTraceResult rt = world.rayTraceBlocks(eyeLoc, delta.multiply(1.0 / len), len - 0.05, FluidCollisionMode.NEVER, true);
            if (rt == null || rt.getHitBlock() == null) {
                // Хотя бы один уголок хитбокса реально открыт (например ноги под плитой / щель в блоках) — удар честный!
                return null;
            }
            if (primaryWall == null) primaryWall = rt.getHitBlock();
        }

        // Дополнительные 2 луча с боковым смещением глаз (±0.14м) от микро-рассинхрона 1 тика на углу двери/колонны
        Vector lookHoriz = eyeLoc.getDirection().clone().setY(0);
        if (lookHoriz.lengthSquared() > 0.001) {
            Vector side = new Vector(-lookHoriz.getZ(), 0, lookHoriz.getX()).normalize().multiply(0.14);
            for (int s = -1; s <= 1; s += 2) {
                Location shiftedEye = eyeLoc.clone().add(side.getX() * s, 0, side.getZ() * s);
                if (shiftedEye.getBlock().getType().isSolid() && !shiftedEye.getBlock().isPassable()) continue;
                Vector delta = new Vector(cx, cy, cz).subtract(shiftedEye.toVector());
                double len = delta.length();
                if (len <= 0.16) return null;
                RayTraceResult rt = world.rayTraceBlocks(shiftedEye, delta.multiply(1.0 / len), len - 0.05, FluidCollisionMode.NEVER, true);
                if (rt == null || rt.getHitBlock() == null) {
                    return null;
                }
            }
        }
        return primaryWall;
    }

    /**
     * ИНЖЕКТ ЧЕРЕЗ СТЕНУ (Through-Wall Inject-Bot):
     * Спавнит невидимого проверочного бота СТРОГО ЗА СПЛОШНОЙ СТЕНОЙ перед подозреваемым на 16 тиков (0.80 сек).
     * Обычный клиент Minecraft не может отправить пакет атаки на сущность за стеной (луч упирается в блок стены),
     * а читер с KillAura / WallHit / ThroughWalls мгновенно бьёт инжект-бота сквозь стену и улетает в авто-бан KingАЧ!
     */
    private boolean injectThroughWallProbe(final Player suspect, PlayerAcData d, Block wallBlock, long now, boolean forceFast) {
        if (suspect == null || !suspect.isOnline() || wallBlock == null) return false;
        long cd = forceFast ? 900L : 2600L;
        if (now - d.lastWallProbeMs < cd) return false;
        d.lastWallProbeMs = now;

        try {
            Location eye = suspect.getEyeLocation();
            Vector wallCenter = wallBlock.getLocation().add(0.5, 0.5, 0.5).toVector();
            Vector pushDir = wallCenter.clone().subtract(eye.toVector()).setY(0);
            if (pushDir.lengthSquared() < 0.001) {
                pushDir = eye.getDirection().clone().setY(0);
            }
            if (pushDir.lengthSquared() < 0.001) return false;
            pushDir.normalize();

            // Размещаем приманку на 0.95 блока ЗА центром стены (в соседнем блоке по ту сторону преграды)
            Location behindWall = wallBlock.getLocation().add(0.5 + pushDir.getX() * 0.95, 0.05, 0.5 + pushDir.getZ() * 0.95);
            if (behindWall.distanceSquared(eye) > 9.6) return false; // в пределах досягаемости меча (~3.1м), чтобы WallAura достала!

            final ArmorStand probe = (ArmorStand) suspect.getWorld().spawnEntity(behindWall, EntityType.ARMOR_STAND);
            probe.setPersistent(false);
            probe.setVisible(false);
            probe.setGravity(false);
            probe.setSmall(true);
            probe.setSilent(true);
            probe.setBasePlate(false);
            probe.addScoreboardTag("kingac_probe");
            probe.addScoreboardTag("kingac_wall_probe");

            // Гарантия 0% ложных срабатываний: если сбоку есть окно/проём и бот виден — тут же удаляем его!
            if (findBlockingWallBetween(suspect, probe) == null) {
                probe.remove();
                return false;
            }

            activeProbes.put(probe.getUniqueId(), suspect.getUniqueId());
            wallProbeIds.add(probe.getUniqueId());

            new BukkitRunnable() {
                @Override
                public void run() {
                    activeProbes.remove(probe.getUniqueId());
                    wallProbeIds.remove(probe.getUniqueId());
                    if (probe.isValid()) probe.remove();
                }
            }.runTaskLater(this, 16L);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Проактивный инжект через стену: если игрок под жалобой (/report Deep Watch) или уже подозревается в WallHit
     * машет мечом/топором вплотную к стене (трапке/кабинке), KingАЧ незаметно ставит приманку за этой стеной!
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWallSwingInspect(PlayerInteractEvent e) {
        if (e.getAction() != Action.LEFT_CLICK_BLOCK && e.getAction() != Action.LEFT_CLICK_AIR) return;
        Player p = e.getPlayer();
        if (isExempt(p)) return;
        PlayerAcData d = data(p);
        long now = System.currentTimeMillis();
        if (!d.isUnderDeepWatch() && d.wallHitBuffer <= 0 && d.totalVl < 5 && (now - d.lastCombatMs) > 8000L) {
            return;
        }
        RayTraceResult rt = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), 2.15, FluidCollisionMode.NEVER, true);
        if (rt != null && rt.getHitBlock() != null) {
            injectThroughWallProbe(p, d, rt.getHitBlock(), now, false);
        }
    }

    private static String blockKey(World w, int x, int y, int z) {
        return w.getName() + ":" + x + ":" + y + ":" + z;
    }

    private boolean wasBlockJustPlaced(Block b, long now, long windowMs) {
        if (b == null) return false;
        World w = b.getWorld();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Long placedAt = recentBlockPlaces.get(blockKey(w, b.getX() + dx, b.getY() + dy, b.getZ() + dz));
                    if (placedAt != null && (now - placedAt.longValue()) <= windowMs) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // =================================================================================
    //  3. SCAFFOLD, AUTOTOTEM / GUIMOVE & XRAY
    // =================================================================================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlaceScaffold(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Block placed = e.getBlockPlaced();
        long now = System.currentTimeMillis();
        recentBlockPlaces.put(blockKey(placed.getWorld(), placed.getX(), placed.getY(), placed.getZ()), Long.valueOf(now));
        if (isExempt(p)) return;
        PlayerAcData d = data(p);
        d.lastBlockPlaceMs = now;

        Location pl = p.getLocation();
        if (placed.getY() == pl.getBlockY() - 1 && p.isSprinting() && pl.getPitch() > 79.0f && Math.abs(d.prevDeltaPitch) > 32.0f) {
            d.scaffoldSnapBuffer += 2;
            if (d.scaffoldSnapBuffer >= 5) {
                d.scaffoldSnapBuffer = 1;
                flag(p, "Scaffold [Авто-Мост]", "Флик вниз " + (int) Math.abs(d.prevDeltaPitch) + "° при подстрое на бегу", 3, false);
            }
        } else if (d.scaffoldSnapBuffer > 0) {
            d.scaffoldSnapBuffer--;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTotemPop(EntityResurrectEvent e) {
        if (e.getEntity() instanceof Player) {
            data((Player) e.getEntity()).lastTotemPopMs = System.currentTimeMillis();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInvTotemAndGuiMove(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        if (isExempt(p)) return;
        PlayerAcData d = data(p);
        long sincePop = System.currentTimeMillis() - d.lastTotemPopMs;
        ItemStack cur = e.getCurrentItem();
        boolean isTotemSlot = (cur != null && cur.getType() == Material.TOTEM_OF_UNDYING) || e.getRawSlot() == 45;

        if (isTotemSlot && sincePop >= 0L && sincePop <= 65L && (p.isSprinting() || (System.currentTimeMillis() - d.lastCombatMs) < 1500L)) {
            d.combatConfirmations++;
            flag(p, "AutoTotem", "Авто-взятие тотема за " + sincePop + "мс в бою", 3, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMineXrayCheck(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (isExempt(p)) return;
        Block b = e.getBlock();
        if ("spawn_world".equalsIgnoreCase(b.getWorld().getName())) return;

        Material m = b.getType();
        PlayerAcData d = data(p);
        if (m == Material.STONE || m == Material.DEEPSLATE || m == Material.NETHERRACK || m == Material.TUFF) {
            d.stoneMined++;
            return;
        }
        boolean isRare = m == Material.DIAMOND_ORE || m == Material.DEEPSLATE_DIAMOND_ORE
                || m == Material.ANCIENT_DEBRIS || m == Material.EMERALD_ORE || m == Material.DEEPSLATE_EMERALD_ORE;
        if (!isRare) return;

        if (d.lastRareOreLoc != null && d.lastRareOreLoc.getWorld().equals(b.getWorld())
                && d.lastRareOreLoc.distanceSquared(b.getLocation()) <= 16.0) {
            d.lastRareOreLoc = b.getLocation();
            return;
        }
        d.lastRareOreLoc = b.getLocation();

        boolean exposed = false;
        for (BlockFace face : SIX_FACES) {
            Material adj = b.getRelative(face).getType();
            if (adj == Material.AIR || adj == Material.CAVE_AIR || adj == Material.WATER || adj == Material.LAVA) {
                exposed = true;
                break;
            }
        }
        if (!exposed) {
            d.hiddenRareVeinHits++;
            double ratio = (double) d.hiddenRareVeinHits / (double) Math.max(1, d.stoneMined + d.hiddenRareVeinHits);
            if (d.hiddenRareVeinHits >= 5 && ratio > 0.065) {
                flag(p, "XRay [Скрытые Руды]", "Скрытых жил подряд: " + d.hiddenRareVeinHits + " (" + m.name() + ")", 3, false);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTookDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player) {
            Player p = (Player) e.getEntity();
            if (ascendingExecution.contains(p.getUniqueId())) {
                e.setCancelled(true);
                return;
            }
            data(p).lastDamagedMs = System.currentTimeMillis();
        }
        if (e instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent ed = (EntityDamageByEntityEvent) e;
            if (ed.getDamager() instanceof Projectile && e.getEntity() instanceof Player) {
                data((Player) e.getEntity()).lastDamagedMs = System.currentTimeMillis();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleportGrace(PlayerTeleportEvent e) {
        PlayerAcData d = data(e.getPlayer());
        d.lastTeleportOrGlideMs = System.currentTimeMillis();
        d.airHoverTicks = 0;
        d.speedBuffer = 0;
    }

    @EventHandler(ignoreCancelled = true)
    public void onRussianCommands(PlayerCommandPreprocessEvent e) {
        String raw = e.getMessage().trim();
        String low = raw.toLowerCase(Locale.ROOT);
        if (low.equals("/жалоба") || low.startsWith("/жалоба ")) {
            String rest = raw.length() > 7 ? raw.substring(7) : "";
            e.setMessage("/report" + rest);
        } else if (low.equals("/жалобы") || low.startsWith("/жалобы ")) {
            String rest = raw.length() > 7 ? raw.substring(7) : "";
            e.setMessage("/reports" + rest);
        } else if (low.equals("/ач") || low.startsWith("/ач ")) {
            String rest = raw.length() > 3 ? raw.substring(3) : "";
            e.setMessage("/kingac" + rest);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        ascendingExecution.remove(u);
        players.remove(u);
    }

    // =================================================================================
    //  4. ALERT ENGINE + АВТОМАТИЧЕСКИЙ БАН ЧИТЕРОВ САМИМ KingАЧ (24/7!)
    // =================================================================================
    private void flag(Player suspect, String checkName, String details, int addVl, boolean lagSensitive) {
        if (suspect == null || !suspect.isOnline() || isExempt(suspect)) return;
        PlayerAcData d = data(suspect);
        if (d.banInProgress) return;

        int ping = safePing(suspect);
        // Protect high-ping players from false lag-sensitive accumulations
        if (lagSensitive && ping > maxAutobanPingMs) {
            return;
        }

        int curCheckVl = (d.vlByCheck.containsKey(checkName) ? d.vlByCheck.get(checkName).intValue() : 0) + addVl;
        d.vlByCheck.put(checkName, Integer.valueOf(curCheckVl));
        d.totalVl += addVl;
        d.lastCheat = checkName + " — " + details;
        sessionFlags++;

        long now = System.currentTimeMillis();
        int effectiveThreshold = (d.reporters.size() >= 3 && d.combatConfirmations >= 2)
                ? Math.max(10, autoBanVl - 4)
                : autoBanVl;

        if (curCheckVl >= 2 && (now - d.lastAlertMs) >= 2800L) {
            d.lastAlertMs = now;
            String repBadge = d.reporters.isEmpty() ? "" : c(" &8[&#FF4081📨 Жалоб: " + d.reporters.size() + "&8]");
            String prefixMsg = c("&#FF1744&l🛡 [KingАЧ] &fИгрок &#FFD740" + suspect.getName()
                    + " &8» &#FF5252" + checkName
                    + " &8(&eVL " + d.totalVl + "/" + effectiveThreshold + " &8│ &7" + details + " &8│ &a" + ping + "мс&8)" + repBadge + " ");

            getLogger().info("[KingАЧ] " + suspect.getName() + " failed " + checkName + " (" + details + ") [totalVL=" + d.totalVl + "/" + effectiveThreshold + "]");

            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (!isStaffMember(staff) || mutedAlertsStaff.contains(staff.getUniqueId())) continue;
                playSound(staff, "block.note_block.pling", 0.9f, 1.85f);
                sendClickableButtons(staff, prefixMsg, new String[][]{
                        new String[]{c("&#00E676&l[👁 СПЕК] "), "/spek tp " + suspect.getName(), "&aТелепортироваться в скрытую слежку"},
                        new String[]{c("&#40C4FF&l[🔍 ПРОВЕРКА] "), "/check " + suspect.getName(), "&bВызвать игрока на проверку читов"},
                        new String[]{c("&#FF1744&l[🔨 АВТО-БАН]"), "/kingac ban " + suspect.getName() + " " + checkName, "&cМгновенно забанить через KingАЧ"}
                });
            }
        }

        // АВТОНОМНЫЙ АВТО-БАН САМИМ KingАЧ!
        if (autoBanEnabled && d.totalVl >= effectiveThreshold) {
            triggerAutonomousKingAcBan(suspect, checkName, d.totalVl);
        }
    }

    public void triggerAutonomousKingAcBan(final Player target, final String detectReason, final int finalVl) {
        if (target == null || !target.isOnline() || isOwnerPlayer(target)) return;
        final PlayerAcData d = data(target);
        if (d.banInProgress) return;
        d.banInProgress = true;
        ascendingExecution.add(target.getUniqueId());
        sessionAutoBans++;
        activeReports.remove(target.getName().toLowerCase(Locale.ROOT));

        final String targetName = target.getName();
        final String fullReason = "Читы: " + detectReason + " [KingАЧ]";
        final Location baseLoc = target.getLocation().clone();

        target.sendTitle(
                c("&#FF1744&l🛡 KingАЧ ОБНАРУЖИЛ ЧИТЫ!"),
                c("&fДетект: &#FFD740" + detectReason),
                5, 50, 10
        );

        for (Player on : Bukkit.getOnlinePlayers()) {
            playSound(on, "entity.ENDER_DRAGON.growl".toLowerCase(Locale.ROOT), 0.65f, 1.35f);
            playSound(on, "block.beacon.deactivate", 1.0f, 0.75f);
        }

        // 2.2-секундная небесная казнь KingАЧ с вихрем частиц и молнией!
        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                tick++;
                if (!target.isOnline()) {
                    cancel();
                    finalizeBanPersistence(targetName, fullReason);
                    return;
                }
                if (tick <= 11) {
                    Location lift = baseLoc.clone().add(0, tick * 0.42, 0);
                    lift.setYaw(baseLoc.getYaw() + tick * 28f);
                    lift.setPitch(0f);
                    target.teleport(lift);
                    try {
                        lift.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, lift.clone().add(0, 1.0, 0), 14, 0.45, 0.55, 0.45, 0.02);
                        lift.getWorld().spawnParticle(Particle.CRIT, lift.clone().add(0, 1.0, 0), 10, 0.4, 0.4, 0.4, 0.05);
                    } catch (Throwable ignored) {}
                    return;
                }
                cancel();
                ascendingExecution.remove(target.getUniqueId());
                try {
                    target.getWorld().strikeLightningEffect(target.getLocation());
                } catch (Throwable ignored) {}

                finalizeBanPersistence(targetName, fullReason);

                Bukkit.broadcastMessage(c("&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                Bukkit.broadcastMessage(c(" &#FF1744&l🛡 [KingАЧ] &fНейро-античит автоматически заблокировал читера!"));
                Bukkit.broadcastMessage(c(" &8▸ &fНарушитель: &#FFD740&l" + targetName));
                Bukkit.broadcastMessage(c(" &8▸ &fПричина: &#FF5252" + detectReason + " &8(&eVL: " + finalVl + "&8)"));
                Bukkit.broadcastMessage(c(" &8▸ &fСрок блокировки: &#00E5FF" + formatDuration(autoBanDurationSec)
                        + " &8│ &7Жалоба на софт: &#00E676/report <ник>"));
                Bukkit.broadcastMessage(c("&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));

                target.kickPlayer(buildBanScreen(detectReason, autoBanDurationSec));
            }
        }.runTaskTimer(this, 0L, 4L);
    }

    private void finalizeBanPersistence(String playerName, String fullReason) {
        long now = System.currentTimeMillis();
        long expires = now + (autoBanDurationSec * 1000L);
        autoBans.put(playerName.toLowerCase(Locale.ROOT), new AutoBanEntry(playerName, fullReason, expires, now));
        saveBansFile();

        // Sync directly into KingGriefCore DataStore if KingGriefCore is running!
        try {
            Plugin kg = Bukkit.getPluginManager().getPlugin("KingGriefCore");
            if (kg != null && kg.isEnabled()) {
                Method getStore = kg.getClass().getMethod("getStore");
                Object store = getStore.invoke(kg);
                if (store != null) {
                    Method banM = store.getClass().getMethod("ban", String.class, long.class, String.class, String.class);
                    banM.invoke(store, playerName, Long.valueOf(autoBanDurationSec), fullReason, "KingАЧ");
                    try {
                        Method histM = store.getClass().getMethod("addPunishHistory", String.class, String.class, String.class, String.class, String.class);
                        histM.invoke(store, playerName, "АВТО-БАН", "KingАЧ", formatDuration(autoBanDurationSec), fullReason);
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

    private boolean unbanFromKingAc(String playerName) {
        boolean removedLocal = autoBans.remove(playerName.toLowerCase(Locale.ROOT)) != null;
        if (removedLocal) saveBansFile();

        boolean removedCore = false;
        try {
            Plugin kg = Bukkit.getPluginManager().getPlugin("KingGriefCore");
            if (kg != null && kg.isEnabled()) {
                Method getStore = kg.getClass().getMethod("getStore");
                Object store = getStore.invoke(kg);
                if (store != null) {
                    Method unbanM = store.getClass().getMethod("unban", String.class);
                    unbanM.invoke(store, playerName);
                    removedCore = true;
                }
            }
        } catch (Throwable ignored) {}
        return removedLocal || removedCore;
    }

    // =================================================================================
    //  5. СИСТЕМА ЖАЛОБ ИГРОКОВ (/report <ник> [причина] + GUI выбора причины + /reports)
    // =================================================================================
    private void submitPlayerReport(Player reporter, Player suspect, String reason) {
        if (reporter.getUniqueId().equals(suspect.getUniqueId())) {
            reporter.sendMessage(c("&#FF5252✖ Вы не можете отправить жалобу на самого себя!"));
            return;
        }
        if (isOwnerPlayer(suspect)) {
            reporter.sendMessage(c("&#FF5252✖ На владельца сервера нельзя отправить жалобу."));
            return;
        }
        long now = System.currentTimeMillis();
        Long cd = reportCooldowns.get(reporter.getUniqueId());
        if (cd != null && cd.longValue() > now && !isStaffMember(reporter)) {
            long left = Math.max(1L, (cd.longValue() - now + 999L) / 1000L);
            reporter.sendMessage(c("&#FFD740⏱ Подождите ещё &f" + left + " сек. &#FFD740перед следующей жалобой!"));
            return;
        }
        reportCooldowns.put(reporter.getUniqueId(), Long.valueOf(now + reportCooldownSec * 1000L));

        String key = suspect.getName().toLowerCase(Locale.ROOT);
        ReportRecord rec = activeReports.get(key);
        if (rec == null) {
            rec = new ReportRecord(suspect.getName(), reporter.getName(), reason);
            activeReports.put(key, rec);
        } else {
            rec.reporters.add(reporter.getName());
            rec.lastReason = reason;
            rec.lastUpdatedMs = now;
        }

        // Активируем УСИЛЕННУЮ НЕЙРО-СЛЕЖКУ KingАЧ за подозреваемым на 5 минут!
        PlayerAcData sd = data(suspect);
        sd.reporters.add(reporter.getName().toLowerCase(Locale.ROOT));
        sd.deepWatchUntilMs = now + 300_000L; // 5 min Deep Watch!

        int totalReps = rec.reporters.size();
        int notifiedStaff = 0;
        String staffLine = c("&#FF4081&l📨 [ЖАЛОБА → KingАЧ] &fНа &#FFD740" + suspect.getName()
                + " &fпожаловался &a" + reporter.getName()
                + " &8│ &7Причина: &#FF5252" + reason
                + " &8[&eВсего жалоб: " + totalReps + " &8│ &cVL: " + sd.totalVl + "&8] ");

        for (Player on : Bukkit.getOnlinePlayers()) {
            if (!isStaffMember(on)) continue;
            notifiedStaff++;
            on.sendTitle(
                    c("&#FF4081&l📨 ЖАЛОБА НА ИГРОКА"),
                    c("&e" + suspect.getName() + " &8— &f" + reason + " &7(" + totalReps + " жал.)"),
                    4, 55, 12
            );
            playSound(on, "block.note_block.bell", 1.1f, 1.45f);
            sendClickableButtons(on, staffLine, new String[][]{
                    new String[]{c("&#00E676&l[👁 СПЕК] "), "/spek tp " + suspect.getName(), "&aТелепортироваться в спек"},
                    new String[]{c("&#40C4FF&l[🔍 ПРОВЕРКА] "), "/check " + suspect.getName(), "&bВызвать на проверку"},
                    new String[]{c("&#FFD740&l[📋 ЖАЛОБЫ] "), "/reports", "&eОткрыть меню всех жалоб"}
            });
        }

        playSound(reporter, "entity.experience_orb.pickup", 1.0f, 1.35f);
        reporter.sendMessage(c("&#00E676&l✔ [KingАЧ] &fЖалоба на &#FFD740" + suspect.getName()
                + " &f(&e" + reason + "&f) принята!"));
        reporter.sendMessage(c(" &#80D8FF🛡 Нейро-античит &#FFD740KingАЧ &#80D8FFвключил усиленную слежку за микро-дёрганиями и боем игрока"
                + (notifiedStaff > 0 ? " &7(оповещено модераторов: &a" + notifiedStaff + "&7)!" : " &7— при подтверждении читов он будет забанен автоматически!")));

        // Если уже было много подтверждённых боевых нарушений и пришло 3+ жалобы — перепроверяем порог!
        if (autoBanEnabled && totalReps >= 3 && sd.combatConfirmations >= 2 && sd.totalVl >= Math.max(10, autoBanVl - 4)) {
            triggerAutonomousKingAcBan(suspect, sd.lastCheat, sd.totalVl);
        }
    }

    private void openReportReasonPickerGui(Player reporter, Player suspect) {
        Inventory inv = Bukkit.createInventory(new KacGuiHolder("report_pick", suspect.getName()), 27,
                c("&#FF1744&l📨 ЖАЛОБА НА " + suspect.getName()));
        fillGlass(inv, Material.BLACK_STAINED_GLASS_PANE);

        inv.setItem(4, makeSkull(suspect, "&#FFD740&lПодозреваемый: &f" + suspect.getName(),
                Arrays.asList("&7Выберите точную причину жалобы ниже —", "&7нейро-античит &#FF1744KingАЧ &7сразу начнёт проверку!")));

        inv.setItem(10, makeIcon(Material.NETHERITE_SWORD, "&#FF1744&l⚔ Киллаура / Микро-дёргание",
                "&7Резкая наводка, микро-тряска головой,", "&7удары спиной или сквозь стены.", "", "&#00E676▸ Нажмите, чтобы отправить"));
        inv.setItem(11, makeIcon(Material.SPECTRAL_ARROW, "&#FF9100&l📏 Хитбоксы / Рич (Reach)",
                "&7Слишком далёкие удары (> 3.4 бл.)", "&7или расширенные хитбоксы.", "", "&#00E676▸ Нажмите, чтобы отправить"));
        inv.setItem(12, makeIcon(Material.FEATHER, "&#00E5FF&l🕊 Флай / Спиды / НоуСлоу",
                "&7Полёт, быстрый бег, бег по воде", "&7или спринт с зажатым щитом/едой.", "", "&#00E676▸ Нажмите, чтобы отправить"));
        inv.setItem(14, makeIcon(Material.TOTEM_OF_UNDYING, "&#FFD740&l🛡 АвтоТотем / АвтоКликер",
                "&7Мгновенный переклад тотема на бегу", "&7или макрос 20+ КПС.", "", "&#00E676▸ Нажмите, чтобы отправить"));
        inv.setItem(15, makeIcon(Material.DIAMOND_ORE, "&#40C4FF&l💎 X-Ray / Просвет руд",
                "&7Прямое копание к скрытым алмазам", "&7и древним обломкам.", "", "&#00E676▸ Нажмите, чтобы отправить"));
        inv.setItem(16, makeIcon(Material.TNT, "&#E040FB&l⚠ Багоюз / Другой софт",
                "&7Scaffold, Пакет-Криты и прочие", "&7запрещённые модификации.", "", "&#00E676▸ Нажмите, чтобы отправить"));

        inv.setItem(22, makeIcon(Material.BARRIER, "&c✖ Отмена"));
        reporter.openInventory(inv);
    }

    // =================================================================================
    //  6. ПАНЕЛИ УПРАВЛЕНИЯ KingАЧ (/kingac, /kac, /reports)
    // =================================================================================
    private void openMainControlGui(Player viewer) {
        Inventory inv = Bukkit.createInventory(new KacGuiHolder("main", ""), 54,
                c("&#FF1744&l🛡 KingАЧ v6.0 — ЦЕНТР ДЕТЕКТОВ"));
        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        inv.setItem(4, makeIcon(Material.SHIELD, "&#FF1744&l🛡 KingАЧ v6.0 — Нейро-Античит 24/7",
                "&7Активные модули защиты:",
                " &#00E676✔ Сенсор Микро-Дёрганий Головой &8(Sawtooth + Snap-Back)",
                " &#00E676✔ Анализ Шага Сенсы Мыши &8(GCD Smooth-Aim Bypass)",
                " &#00E676✔ Инжект-Бот 360° &8(KillAura / TriggerBot / WallHit)",
                " &#00E676✔ Reach / HitBox / Packet-Criticals / AutoClicker",
                " &#00E676✔ AutoTotem / Scaffold / Fly / Speed / AirStrafe / NoSlow",
                "",
                "&7Срабатываний за сессию: &#FFD740" + sessionFlags + " &8│ &7Авто-банов: &#FF5252" + sessionAutoBans,
                "&7Активных жалоб игроков: &#FF4081" + activeReports.size()));

        // Список онлайн-подозреваемых, отсортированный по убыванию VL
        List<Player> suspects = new ArrayList<Player>();
        for (Player on : Bukkit.getOnlinePlayers()) {
            PlayerAcData d = players.get(on.getUniqueId());
            if (d != null && (d.totalVl > 0 || d.isUnderDeepWatch())) {
                suspects.add(on);
            }
        }
        Collections.sort(suspects, new Comparator<Player>() {
            @Override
            public int compare(Player a, Player b) {
                return Integer.compare(data(b).totalVl, data(a).totalVl);
            }
        });

        int[] slots = new int[]{
                10,11,12,13,14,15,16,
                19,20,21,22,23,24,25,
                28,29,30,31,32,33,34
        };
        for (int i = 0; i < suspects.size() && i < slots.length; i++) {
            Player sp = suspects.get(i);
            PlayerAcData d = data(sp);
            List<String> lore = new ArrayList<String>();
            lore.add("&7Суммарный уровень нарушений: &#FFD740" + d.totalVl + " / " + autoBanVl + " VL");
            lore.add("&7Последний детект: &#FF5252" + d.lastCheat);
            lore.add("&7Индекс микро-дёрганий головы: &#00E5FF" + d.maxMicroTwitchScore + "/15");
            lore.add("&7Жалоб от игроков: &#FF4081" + d.reporters.size()
                    + (d.isUnderDeepWatch() ? " &8(&#00E676🔥 Усиленная слежка&8)" : ""));
            lore.add("&7Пинг: &a" + safePing(sp) + " мс");
            if (!d.vlByCheck.isEmpty()) {
                lore.add("");
                lore.add("&7По проверкам:");
                int shown = 0;
                for (Map.Entry<String, Integer> en : d.vlByCheck.entrySet()) {
                    if (shown++ >= 4) break;
                    lore.add(" &8• &f" + en.getKey() + ": &e" + en.getValue() + " VL");
                }
            }
            lore.add("");
            lore.add("&#00E676▸ ЛКМ — Скрытая слежка (/spek)");
            lore.add("&#40C4FF▸ ПКМ — Вызвать на проверку (/check)");
            lore.add("&#FF1744▸ Shift+ПКМ — Авто-Бан через KingАЧ!");
            inv.setItem(slots[i], makeSkull(sp, "&#FF5252⚠ " + sp.getName() + " &8[&eVL: " + d.totalVl + "&8]", lore));
        }

        if (suspects.isEmpty()) {
            inv.setItem(22, makeIcon(Material.EMERALD_BLOCK, "&#00E676&l✔ Нарушителей сейчас не обнаружено!",
                    "&7KingАЧ сканирует каждое движение головы,",
                    "&7удары и пакеты всех игроков в реальном времени."));
        }

        // Нижняя панель навигации и переключателей
        inv.setItem(45, makeIcon(Material.BOOK, "&#FF4081&l📨 Жалобы Игроков (/reports) &8[&f" + activeReports.size() + "&8]",
                "&7Открыть список активных репортов",
                "&7и проверить подозреваемых!", "", "&#00E676▸ Нажмите для открытия"));

        inv.setItem(46, makeIcon(Material.IRON_BARS, "&#FF5252&l🔨 Забаненные KingАЧ &8[&f" + autoBans.size() + "&8]",
                "&7Список игроков, заблокированных",
                "&7автоматической защитой KingАЧ.", "", "&#00E676▸ Нажмите для просмотра / разбана"));

        inv.setItem(48, makeIcon(autoBanEnabled ? Material.LIME_DYE : Material.GRAY_DYE,
                "&#FFD740&l🤖 Авто-Бан 24/7: " + (autoBanEnabled ? "&#00E676&lВКЛЮЧЁН" : "&#FF5252&lВЫКЛЮЧЕН"),
                "&7Античит сам банит читеров при достижении",
                "&7порога &#FFD740" + autoBanVl + " VL &7с небесной казнью!",
                "", "&#80D8FF▸ Нажмите для переключения"));

        inv.setItem(49, makeIcon(microTwitchEnabled ? Material.ENDER_EYE : Material.GRAY_DYE,
                "&#00E5FF&l🎯 Сенсор Микро-Дёрганий: " + (microTwitchEnabled ? "&#00E676&lВКЛЮЧЁН" : "&#FF5252&lВЫКЛЮЧЕН"),
                "&7Отслеживает микро-тряску головой в бою,",
                "&7срыв шага мыши GCD и резкие отскоки наводки!",
                "", "&#80D8FF▸ Нажмите для переключения"));

        inv.setItem(50, makeIcon(setbackEnabled ? Material.FEATHER : Material.GRAY_DYE,
                "&#E040FB&l↩ Setback (Откат Fly/Speed): " + (setbackEnabled ? "&#00E676&lВКЛЮЧЁН" : "&#FF5252&lВЫКЛЮЧЕН"),
                "&7Мгновенно притягивает читера обратно на землю",
                "&7при детекте Fly / Speed / Jesus / NoSlow!",
                "", "&#80D8FF▸ Нажмите для переключения"));

        boolean alertsOn = !mutedAlertsStaff.contains(viewer.getUniqueId());
        inv.setItem(53, makeIcon(alertsOn ? Material.BELL : Material.BARRIER,
                "&#FFD740&l🔔 Ваши Уведомления: " + (alertsOn ? "&#00E676&lВКЛ" : "&e&lСКРЫТЫ"),
                "&7Показывать ли вам в чате алерты KingАЧ.",
                "", "&#80D8FF▸ Нажмите для переключения"));

        viewer.openInventory(inv);
    }

    private void openReportsGui(Player viewer) {
        Inventory inv = Bukkit.createInventory(new KacGuiHolder("reports", ""), 54,
                c("&#FF4081&l📨 KingАЧ — ЖАЛОБЫ ИГРОКОВ (" + activeReports.size() + ")"));
        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        int[] slots = new int[]{
                10,11,12,13,14,15,16,
                19,20,21,22,23,24,25,
                28,29,30,31,32,33,34
        };
        int idx = 0;
        for (ReportRecord rec : activeReports.values()) {
            if (idx >= slots.length) break;
            Player onlineTarget = Bukkit.getPlayerExact(rec.targetName);
            int vl = onlineTarget != null ? data(onlineTarget).totalVl : 0;
            int twitch = onlineTarget != null ? data(onlineTarget).maxMicroTwitchScore : 0;
            List<String> lore = new ArrayList<String>();
            lore.add("&7Статус: " + (onlineTarget != null ? "&#00E676В сети (" + safePing(onlineTarget) + " мс)" : "&cВышел с сервера"));
            lore.add("&7Последняя причина: &#FF5252" + rec.lastReason);
            lore.add("&7Количество жалоб: &#FFD740" + rec.reporters.size() + " шт.");
            lore.add("&7Отправители: &f" + joinSet(rec.reporters, 4));
            lore.add("&7Детекты KingАЧ: &e" + vl + " VL &8│ &bМикро-рывки: " + twitch + "/15");
            lore.add("");
            lore.add("&#00E676▸ ЛКМ — Телепорт в /spek");
            lore.add("&#40C4FF▸ ПКМ — Вызвать на /check");
            lore.add("&#FFD740▸ Shift+ЛКМ — Закрыть репорт (проверено)");
            lore.add("&#FF1744▸ Shift+ПКМ — Забанить через KingАЧ!");

            ItemStack head = onlineTarget != null
                    ? makeSkull(onlineTarget, "&#FF4081📨 " + rec.targetName + " &8[&e" + rec.reporters.size() + " жал.&8]", lore)
                    : makeIcon(Material.SKELETON_SKULL, "&#FF4081📨 " + rec.targetName + " &8[&7Оффлайн&8]", lore.toArray(new String[0]));
            inv.setItem(slots[idx++], head);
        }

        if (activeReports.isEmpty()) {
            inv.setItem(22, makeIcon(Material.LIME_STAINED_GLASS_PANE, "&#00E676&l✔ Неразобранных жалоб нет!",
                    "&7Когда игроки напишут &#FFD740/report <ник>&7,",
                    "&7жалобы появятся здесь автоматически."));
        }

        inv.setItem(49, makeIcon(Material.ARROW, "&7« Назад в центр KingАЧ"));
        viewer.openInventory(inv);
    }

    private void openBansGui(Player viewer) {
        Inventory inv = Bukkit.createInventory(new KacGuiHolder("bans", ""), 54,
                c("&#FF1744&l🔨 АВТО-БАНЫ KingАЧ (" + autoBans.size() + ")"));
        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        int[] slots = new int[]{
                10,11,12,13,14,15,16,
                19,20,21,22,23,24,25,
                28,29,30,31,32,33,34
        };
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        long now = System.currentTimeMillis();
        int idx = 0;
        for (Iterator<Map.Entry<String, AutoBanEntry>> it = autoBans.entrySet().iterator(); it.hasNext(); ) {
            AutoBanEntry b = it.next().getValue();
            if (b.expiresAtMs <= now) {
                it.remove();
                continue;
            }
            if (idx >= slots.length) break;
            long leftSec = Math.max(1L, (b.expiresAtMs - now) / 1000L);
            inv.setItem(slots[idx++], makeIcon(Material.REDSTONE_BLOCK,
                    "&#FF5252🔨 " + b.playerName,
                    "&7Причина: &f" + b.reason,
                    "&7Дата бана: &7" + sdf.format(new Date(b.createdAtMs)),
                    "&7Осталось: &#00E5FF" + formatDuration(leftSec),
                    "",
                    isAdminController(viewer)
                            ? "&#00E676▸ Shift+ЛКМ — Разбанить игрока"
                            : "&8Разбан доступен ст. администрации (/kac unban)"));
        }

        if (idx == 0) {
            inv.setItem(22, makeIcon(Material.EMERALD, "&#00E676&lСписок авто-банов пуст",
                    "&7Заблокированные читеры появятся здесь."));
        }

        inv.setItem(49, makeIcon(Material.ARROW, "&7« Назад в центр KingАЧ"));
        viewer.openInventory(inv);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGuiClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof KacGuiHolder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= e.getInventory().getSize()) return;

        KacGuiHolder h = (KacGuiHolder) e.getInventory().getHolder();

        // 1. Меню выбора причины /report <ник>
        if ("report_pick".equals(h.menuType)) {
            if (slot == 22) {
                p.closeInventory();
                return;
            }
            String reason = null;
            if (slot == 10) reason = "Киллаура / Микро-дёргание головой";
            else if (slot == 11) reason = "Хитбоксы / Reach";
            else if (slot == 12) reason = "Fly / Speed / NoSlow";
            else if (slot == 14) reason = "AutoTotem / AutoClicker";
            else if (slot == 15) reason = "X-Ray";
            else if (slot == 16) reason = "Багоюз / Софт";
            if (reason != null) {
                p.closeInventory();
                Player suspect = Bukkit.getPlayerExact(h.targetName);
                if (suspect == null) {
                    p.sendMessage(c("&#FF5252✖ Игрок уже вышел из сети!"));
                    return;
                }
                submitPlayerReport(p, suspect, reason);
            }
            return;
        }

        if (!isStaffMember(p)) {
            p.closeInventory();
            return;
        }

        // 2. Главное меню /kingac
        if ("main".equals(h.menuType)) {
            if (slot == 45) { openReportsGui(p); return; }
            if (slot == 46) { openBansGui(p); return; }
            if (slot == 48) {
                if (!isAdminController(p)) {
                    p.sendMessage(c("&#FF1744✖ Переключать глобальные модули KingАЧ может только ст. администрация / OWNER!"));
                    return;
                }
                autoBanEnabled = !autoBanEnabled;
                persistSettingsToConfig();
                playSound(p, "ui.button.click", 0.9f, 1.2f);
                openMainControlGui(p);
                return;
            }
            if (slot == 49) {
                if (!isAdminController(p)) {
                    p.sendMessage(c("&#FF1744✖ Переключать глобальные модули KingАЧ может только ст. администрация / OWNER!"));
                    return;
                }
                microTwitchEnabled = !microTwitchEnabled;
                persistSettingsToConfig();
                playSound(p, "ui.button.click", 0.9f, 1.2f);
                openMainControlGui(p);
                return;
            }
            if (slot == 50) {
                if (!isAdminController(p)) {
                    p.sendMessage(c("&#FF1744✖ Переключать глобальные модули KingАЧ может только ст. администрация / OWNER!"));
                    return;
                }
                setbackEnabled = !setbackEnabled;
                persistSettingsToConfig();
                playSound(p, "ui.button.click", 0.9f, 1.2f);
                openMainControlGui(p);
                return;
            }
            if (slot == 53) {
                if (mutedAlertsStaff.contains(p.getUniqueId())) mutedAlertsStaff.remove(p.getUniqueId());
                else mutedAlertsStaff.add(p.getUniqueId());
                playSound(p, "ui.button.click", 0.9f, 1.2f);
                openMainControlGui(p);
                return;
            }

            ItemStack clicked = e.getCurrentItem();
            String suspectName = extractSkullTarget(clicked);
            if (suspectName != null) {
                p.closeInventory();
                if (e.getClick() == ClickType.SHIFT_RIGHT) {
                    Player target = Bukkit.getPlayerExact(suspectName);
                    if (target != null && !isOwnerPlayer(target)) {
                        triggerAutonomousKingAcBan(target, data(target).lastCheat, Math.max(autoBanVl, data(target).totalVl));
                    }
                } else if (e.getClick() == ClickType.RIGHT) {
                    p.performCommand("check " + suspectName);
                } else {
                    p.performCommand("spek tp " + suspectName);
                }
            }
            return;
        }

        // 3. Меню жалоб /reports
        if ("reports".equals(h.menuType)) {
            if (slot == 49) { openMainControlGui(p); return; }
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta() || !clicked.getItemMeta().hasDisplayName()) return;
            String rawTitle = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
            // Format: "📨 Nick [X жал.]"
            String clean = rawTitle.replace("📨", "").trim();
            int br = clean.indexOf('[');
            String suspectName = (br > 0 ? clean.substring(0, br) : clean).trim();
            if (suspectName.isEmpty()) return;

            if (e.getClick() == ClickType.SHIFT_LEFT) {
                activeReports.remove(suspectName.toLowerCase(Locale.ROOT));
                p.sendMessage(c("&#00E676✔ Жалоба на &f" + suspectName + " &#00E676отмечена как проверенная и закрыта."));
                openReportsGui(p);
            } else if (e.getClick() == ClickType.SHIFT_RIGHT) {
                p.closeInventory();
                Player target = Bukkit.getPlayerExact(suspectName);
                if (target != null && !isOwnerPlayer(target)) {
                    triggerAutonomousKingAcBan(target, "Жалобы + Проверка KingАЧ", Math.max(autoBanVl, data(target).totalVl));
                } else {
                    p.performCommand("ban " + suspectName + " 30d Читы [KingАЧ]");
                }
            } else if (e.getClick() == ClickType.RIGHT) {
                p.closeInventory();
                p.performCommand("check " + suspectName);
            } else {
                p.closeInventory();
                p.performCommand("spek tp " + suspectName);
            }
            return;
        }

        // 4. Меню авто-банов
        if ("bans".equals(h.menuType)) {
            if (slot == 49) { openMainControlGui(p); return; }
            if (e.getClick() == ClickType.SHIFT_LEFT && isAdminController(p)) {
                ItemStack clicked = e.getCurrentItem();
                if (clicked != null && clicked.getType() == Material.REDSTONE_BLOCK && clicked.hasItemMeta()) {
                    String nick = ChatColor.stripColor(clicked.getItemMeta().getDisplayName()).replace("🔨", "").trim();
                    if (!nick.isEmpty()) {
                        unbanFromKingAc(nick);
                        p.sendMessage(c("&#00E676✔ Игрок &#FFD740" + nick + " &#00E676разбанен в KingАЧ!"));
                        openBansGui(p);
                    }
                }
            }
        }
    }

    // =================================================================================
    //  7. COMMANDS & TAB COMPLETION (/kingac, /report, /reports)
    // =================================================================================
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);

        // -------- /report <ник> [причина] --------
        if ("report".equals(name)) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Только для игроков.");
                return true;
            }
            Player p = (Player) sender;
            if (args.length == 0) {
                p.sendMessage(c("&#FF1744&l🛡 [KingАЧ] &fОтправить жалобу на читера: &#FFD740/report <ник> [причина]"));
                p.sendMessage(c(" &7Подсказка: введите просто &#00E676/report <ник>&7, чтобы открыть удобное меню выбора причины!"));
                return true;
            }
            Player suspect = Bukkit.getPlayerExact(args[0]);
            if (suspect == null) {
                p.sendMessage(c("&#FF5252✖ Игрок &f" + args[0] + " &#FF5252сейчас не в сети!"));
                return true;
            }
            if (args.length == 1) {
                long now = System.currentTimeMillis();
                PlayerAcData rd = data(p);
                if ((now - rd.lastDamagedMs) < 12000L || (now - rd.lastCombatMs) < 12000L) {
                    // В разгар боя не открываем GUI (чтобы не мешать драться), а мгновенно кидаем боевой репорт!
                    submitPlayerReport(p, suspect, "Киллаура / Микро-дёргание (жалоба в бою)");
                } else {
                    openReportReasonPickerGui(p, suspect);
                }
                return true;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                if (i > 1) sb.append(' ');
                sb.append(args[i]);
            }
            submitPlayerReport(p, suspect, sb.toString());
            return true;
        }

        // -------- /reports --------
        if ("reports".equals(name)) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("[KingАЧ] Активных жалоб: " + activeReports.size());
                return true;
            }
            Player p = (Player) sender;
            if (!isStaffMember(p)) {
                p.sendMessage(c("&#FF1744✖ Просмотр списка жалоб доступен только модерации! Подать жалобу: &#FFD740/report <ник>"));
                return true;
            }
            openReportsGui(p);
            return true;
        }

        // -------- /kingac (/kac, /kas, /ач) --------
        if ((sender instanceof Player) && !isStaffMember((Player) sender)) {
            sender.sendMessage(c("&#FF1744&l🛡 [KingАЧ v6.0] &fНейро-античит активен 24/7! Пожаловаться на софт: &#FFD740/report <ник>"));
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player) {
                openMainControlGui((Player) sender);
            } else {
                sender.sendMessage("[KingАЧ v6.0] Флагов: " + sessionFlags + " | Авто-банов: " + sessionAutoBans + " | Жалоб: " + activeReports.size());
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("alerts".equals(sub) && sender instanceof Player) {
            Player p = (Player) sender;
            if (mutedAlertsStaff.contains(p.getUniqueId())) {
                mutedAlertsStaff.remove(p.getUniqueId());
                p.sendMessage(c("&#00E676🛡 Уведомления KingАЧ ВКЛЮЧЕНЫ!"));
            } else {
                mutedAlertsStaff.add(p.getUniqueId());
                p.sendMessage(c("&e🛡 Уведомления KingАЧ скрыты."));
            }
            return true;
        }

        if ("reports".equals(sub) && sender instanceof Player) {
            openReportsGui((Player) sender);
            return true;
        }

        if ("bans".equals(sub) && sender instanceof Player) {
            openBansGui((Player) sender);
            return true;
        }

        if ("info".equals(sub) || "profile".equals(sub)) {
            if (args.length < 2) {
                sender.sendMessage(c("&cИспользование: /kingac info <ник>"));
                return true;
            }
            Player t = Bukkit.getPlayerExact(args[1]);
            if (t == null) {
                sender.sendMessage(c("&cИгрок не в сети."));
                return true;
            }
            PlayerAcData d = data(t);
            sender.sendMessage(c("&#FF1744&l🛡 Досье KingАЧ на &#FFD740" + t.getName() + "&f:"));
            sender.sendMessage(c(" &7Уровень нарушений: &e" + d.totalVl + "/" + autoBanVl + " VL &8│ &7Пинг: &a" + safePing(t) + " мс"));
            sender.sendMessage(c(" &7Индекс микро-дёрганий головы: &#00E5FF" + d.maxMicroTwitchScore + "/15 &8│ &7Жалоб: &#FF4081" + d.reporters.size()));
            sender.sendMessage(c(" &7Последний детект: &f" + d.lastCheat));
            return true;
        }

        if ("ban".equals(sub)) {
            if (args.length < 2) {
                sender.sendMessage(c("&cИспользование: /kingac ban <ник> [причина]"));
                return true;
            }
            Player t = Bukkit.getPlayerExact(args[1]);
            if (t == null) {
                sender.sendMessage(c("&cИгрок не в сети! Используйте /ban " + args[1]));
                return true;
            }
            if (isOwnerPlayer(t)) {
                sender.sendMessage(c("&cНельзя заблокировать OWNER!"));
                return true;
            }
            String r = args.length >= 3 ? joinArgs(args, 2) : data(t).lastCheat;
            if ("Чисто".equals(r)) r = "Читы (KillAura / Aim)";
            triggerAutonomousKingAcBan(t, r, Math.max(autoBanVl, data(t).totalVl));
            return true;
        }

        if ("unban".equals(sub)) {
            if (!isAdminController(sender)) {
                sender.sendMessage(c("&#FF1744✖ Команда /kingac unban доступна только ст. администрации / OWNER!"));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(c("&cИспользование: /kingac unban <ник>"));
                return true;
            }
            boolean ok = unbanFromKingAc(args[1]);
            sender.sendMessage(c(ok
                    ? "&#00E676✔ Игрок &#FFD740" + args[1] + " &#00E676успешно разбанен в KingАЧ!"
                    : "&#FFD740Игрок " + args[1] + " не числился в базе авто-банов KingАЧ."));
            return true;
        }

        if ("probe".equals(sub) || "wallprobe".equals(sub)) {
            if (args.length < 2) {
                sender.sendMessage(c("&cИспользование: /kac probe <ник>"));
                return true;
            }
            Player t = Bukkit.getPlayerExact(args[1]);
            if (t == null) {
                sender.sendMessage(c("&cИгрок не в сети."));
                return true;
            }
            PlayerAcData d = data(t);
            long now = System.currentTimeMillis();
            d.lastProbeMs = 0L;
            d.lastWallProbeMs = 0L;
            injectInvisibleDecoyProbe(t, d, now, true);
            boolean wallSpawned = false;
            RayTraceResult rt = t.getWorld().rayTraceBlocks(t.getEyeLocation(), t.getEyeLocation().getDirection(), 2.4, FluidCollisionMode.NEVER, true);
            if (rt != null && rt.getHitBlock() != null) {
                wallSpawned = injectThroughWallProbe(t, d, rt.getHitBlock(), now, true);
            }
            sender.sendMessage(c("&#00E676🛡 [KingАЧ] Инжект-проверка запущена на &#FFD740" + t.getName()
                    + " &8(360° за спиной" + (wallSpawned ? " + &#00E5FFИнжект СКВОЗЬ СТЕНУ" : "") + "&8)!"));
            return true;
        }

        if ("reload".equals(sub) && isAdminController(sender)) {
            reloadSettingsFromConfig();
            sender.sendMessage(c("&#00E676✔ Конфигурация KingАЧ v6.1 перезагружена!"));
            return true;
        }

        sender.sendMessage(c("&#FF1744&l🛡 Команды KingАЧ v6.1:"));
        sender.sendMessage(c(" &#00E676/kac &8— &fОткрыть центр детектов и настроек"));
        sender.sendMessage(c(" &#00E676/reports &8— &fСписок активных жалоб игроков"));
        sender.sendMessage(c(" &#00E676/kac info <ник> &8— &fНейро-досье и индекс микро-дёрганий"));
        sender.sendMessage(c(" &#00E676/kac probe <ник> &8— &fЗапустить 360° + сквозь-стену Инжект-Бота"));
        sender.sendMessage(c(" &#00E676/kac ban <ник> [причина] &8— &fЗапустить авто-бан KingАЧ"));
        sender.sendMessage(c(" &#00E676/kac unban <ник> &8— &fРазбанить игрока"));
        sender.sendMessage(c(" &#00E676/kac alerts &8— &fВкл/выкл уведомления в чате"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        if ("report".equals(name)) {
            if (args.length == 1) {
                return matchOnline(args[0]);
            }
            if (args.length == 2) {
                return filterPrefix(args[1], Arrays.asList("Киллаура", "УдарЧерезСтену", "Микро-дёргание", "Хитбоксы", "Рич", "Флай", "АвтоТотем", "XRay"));
            }
            return Collections.emptyList();
        }
        if ("kingac".equals(name)) {
            if ((sender instanceof Player) && !isStaffMember((Player) sender)) return Collections.emptyList();
            if (args.length == 1) {
                return filterPrefix(args[0], Arrays.asList("reports", "bans", "info", "probe", "wallprobe", "ban", "unban", "alerts", "reload"));
            }
            if (args.length == 2 && ("info".equalsIgnoreCase(args[0]) || "ban".equalsIgnoreCase(args[0])
                    || "probe".equalsIgnoreCase(args[0]) || "wallprobe".equalsIgnoreCase(args[0]))) {
                return matchOnline(args[1]);
            }
            if (args.length == 2 && "unban".equalsIgnoreCase(args[0])) {
                List<String> banned = new ArrayList<String>();
                for (AutoBanEntry b : autoBans.values()) banned.add(b.playerName);
                return filterPrefix(args[1], banned);
            }
        }
        return Collections.emptyList();
    }

    // ================= GEOMETRY, MATH & UI HELPERS =================
    private static long gcd(long a, long b) {
        while (b != 0L) {
            long t = a % b;
            a = b;
            b = t;
        }
        return a;
    }

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
                Block below = loc.clone().add(ox, -0.18, oz).getBlock();
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

    private static void playSound(Player p, String key, float vol, float pitch) {
        try { p.playSound(p.getLocation(), "minecraft:" + key, vol, pitch); } catch (Throwable ignored) {}
    }

    private static void sendClickableButtons(Player target, String prefixText, String[][] btns) {
        try {
            TextComponent root = new TextComponent(prefixText);
            for (String[] b : btns) {
                TextComponent btn = new TextComponent(b[0]);
                btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, b[1]));
                if (b.length >= 3 && b[2] != null) {
                    btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            new ComponentBuilder(c(b[2])).create()));
                }
                root.addExtra(btn);
            }
            target.spigot().sendMessage(root);
        } catch (Throwable fallback) {
            target.sendMessage(prefixText);
        }
    }

    private static void fillGlass(Inventory inv, Material pane) {
        ItemStack g = makeIcon(pane, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, g);
    }

    private static void fillBorder(Inventory inv, Material pane) {
        ItemStack g = makeIcon(pane, " ");
        int sz = inv.getSize();
        for (int i = 0; i < sz; i++) {
            if (i < 9 || i >= sz - 9 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, g);
            }
        }
    }

    private static ItemStack makeIcon(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat, 1);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName(c(name));
            if (lore != null && lore.length > 0) {
                List<String> l = new ArrayList<String>();
                for (String s : lore) l.add(c(s));
                m.setLore(l);
            }
            m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            it.setItemMeta(m);
        }
        return it;
    }

    private static ItemStack makeSkull(OfflinePlayer owner, String name, List<String> lore) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD, 1);
        ItemMeta m = it.getItemMeta();
        if (m instanceof SkullMeta) {
            try { ((SkullMeta) m).setOwningPlayer(owner); } catch (Throwable ignored) {}
            m.setDisplayName(c(name));
            List<String> col = new ArrayList<String>();
            for (String s : lore) col.add(c(s));
            m.setLore(col);
            it.setItemMeta(m);
        }
        return it;
    }

    private static String extractSkullTarget(ItemStack st) {
        if (st == null || st.getType() != Material.PLAYER_HEAD || !(st.getItemMeta() instanceof SkullMeta)) return null;
        SkullMeta sm = (SkullMeta) st.getItemMeta();
        if (sm.getOwningPlayer() != null && sm.getOwningPlayer().getName() != null) {
            return sm.getOwningPlayer().getName();
        }
        return null;
    }

    private static List<String> matchOnline(String pref) {
        String low = pref.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<String>();
        for (Player on : Bukkit.getOnlinePlayers()) {
            if (on.getName().toLowerCase(Locale.ROOT).startsWith(low)) out.add(on.getName());
        }
        return out;
    }

    private static List<String> filterPrefix(String pref, List<String> candidates) {
        String low = pref.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<String>();
        for (String cnd : candidates) {
            if (cnd.toLowerCase(Locale.ROOT).startsWith(low)) out.add(cnd);
        }
        return out;
    }

    private static String joinSet(Set<String> set, int max) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (String s : set) {
            if (i >= max) {
                sb.append(" +").append(set.size() - max);
                break;
            }
            if (i > 0) sb.append(", ");
            sb.append(s);
            i++;
        }
        return sb.toString();
    }

    private static String joinArgs(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private static String formatDuration(long sec) {
        long days = sec / 86400L;
        long hrs = (sec % 86400L) / 3600L;
        long mins = (sec % 3600L) / 60L;
        if (days > 0) return days + " дн. " + hrs + " ч.";
        if (hrs > 0) return hrs + " ч. " + mins + " мин.";
        if (mins > 0) return mins + " мин.";
        return Math.max(1L, sec) + " сек.";
    }

    public static String c(String s) {
        if (s == null) return "";
        Matcher m = HEX_PAT.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String hex = m.group(1);
            StringBuilder rep = new StringBuilder("\u00A7x");
            for (int i = 0; i < 6; i++) {
                rep.append('\u00A7').append(Character.toLowerCase(hex.charAt(i)));
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(rep.toString()));
        }
        m.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }
}
