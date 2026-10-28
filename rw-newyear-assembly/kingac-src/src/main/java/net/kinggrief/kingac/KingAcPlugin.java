package net.kinggrief.kingac;

import net.md_5.bungee.api.ChatMessageType;
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
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
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
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumMap;
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

    // ================= v8.2: СХЕМЫ ИНЖЕКТА (8 ШТ.) =================
    private final Map<UUID, InjectionScheme> probeSchemes = new HashMap<UUID, InjectionScheme>();
    private final EnumMap<InjectionScheme, Boolean> schemeEnabled = new EnumMap<InjectionScheme, Boolean>(InjectionScheme.class);
    private boolean autoInjectSchemes = true;
    private int sessionInjectHits = 0;
    private int sessionInjectProbes = 0;

    /** v8.2: слоты GUI для 12 схем инжекта (2 ряда по 7 usable-слотов). */
    private static final int[] SCHEME_SLOTS = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23};

    // ================= v8.2: ЖИВОЙ МОНИТОР-HUD ДЛЯ СОСТАВА =================
    private final Map<UUID, String> monitors = new HashMap<UUID, String>();

    // ================= v8.2: ФАЙЛОВЫЙ ЛОГ ДЕТЕКТОВ =================
    private File logFile;
    private final List<String> logTail = new ArrayList<String>();

    private File bansFile;
    private YamlConfiguration bansYaml;

    private boolean autoBanEnabled = true;
    private int autoBanVl = 18;
    private long autoBanDurationSec = 2592000L; // 30d
    private boolean setbackEnabled = true;
    private boolean microTwitchEnabled = true;
    private boolean rayHitboxEnabled = true;
    private int maxAutobanPingMs = 420;
    private int reportCooldownSec = 25;

    private int sessionFlags = 0;
    private int sessionAutoBans = 0;
    private int sessionAirHitsBlocked = 0;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettingsFromConfig();
        loadBansFile();
        initDetectionLog();

        Bukkit.getPluginManager().registerEvents(this, this);
        for (String cmdName : new String[]{"kingac", "report", "reports", "kinject"}) {
            PluginCommand pc = getCommand(cmdName);
            if (pc != null) {
                pc.setExecutor(this);
                pc.setTabCompleter(this);
            }
        }

        // Потиковая запись истории 3D хитбоксов (18 тиков = 900мс) для точного Raycast "в игрока, а не в воздух"!
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Player on : Bukkit.getOnlinePlayers()) {
                    PlayerAcData d = data(on);
                    d.pushBoxSnapshot(now, on.getBoundingBox());
                    d.prevEyeVec = on.getEyeLocation().toVector();
                    d.prevTickYaw = on.getLocation().getYaw();
                    d.prevTickPitch = on.getLocation().getPitch();
                    // v8.2: сброс CPS для живого монитора, если игрок уже не дерётся
                    if (d.currentCps != 0 && (now - d.lastCombatMs) > 1100L) {
                        d.currentCps = 0;
                        d.clickTimes.clear();
                    }
                }
            }
        }.runTaskTimer(this, 1L, 1L);

        // v8.2: ЖИВОЙ МОНИТОР-HUD В ACTIONBAR ДЛЯ СОСТАВА (/ac monitor <ник>)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (monitors.isEmpty()) return;
                for (Map.Entry<UUID, String> en : new ArrayList<Map.Entry<UUID, String>>(monitors.entrySet())) {
                    Player staff = Bukkit.getPlayer(en.getKey());
                    if (staff == null || !staff.isOnline() || !isStaffMember(staff)) {
                        monitors.remove(en.getKey());
                        continue;
                    }
                    sendMonitorHud(staff, en.getValue());
                }
            }
        }.runTaskTimer(this, 20L, 10L);

        // v8.2: АВТОМАТИЧЕСКИЙ ПРОГОН СХЕМ ИНЖЕКТА ПО ПОДОЗРЕВАЕМЫМ (Deep Watch / жалобы / VL)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!autoInjectSchemes) return;
                long now = System.currentTimeMillis();
                for (Player on : Bukkit.getOnlinePlayers()) {
                    if (isExempt(on)) continue;
                    PlayerAcData d = data(on);
                    if (!d.isUnderDeepWatch() && d.totalVl < 4 && d.combatConfirmations < 1) continue;
                    if (now - d.lastInjectSchemeMs < 18000L) continue;
                    InjectionScheme sc = pickAutoScheme(d);
                    if (sc != null) {
                        runInjectionScheme(on, sc, false, null);
                    }
                }
            }
        }.runTaskTimer(this, 200L, 60L);

        // Gradual VL decay every 40s so rare lag spikes never result in false bans
        new BukkitRunnable() {
            @Override
            public void run() {
                for (PlayerAcData d : players.values()) {
                    if (d.totalVl > 0 && !d.banInProgress) {
                        d.totalVl = Math.max(0, d.totalVl - 1);
                    }
                    if (d.microTwitchBuffer > 0) d.microTwitchBuffer = Math.max(0, d.microTwitchBuffer - 1);
                    if (d.pitchSnapBuffer > 0) d.pitchSnapBuffer = Math.max(0, d.pitchSnapBuffer - 1);
                    if (d.headLockTicks > 0) d.headLockTicks = Math.max(0, d.headLockTicks - 1);
                    if (d.gcdFailBuffer > 0) d.gcdFailBuffer = Math.max(0, d.gcdFailBuffer - 2);
                    if (d.wallHitBuffer > 0) d.wallHitBuffer = Math.max(0, d.wallHitBuffer - 1);
                    if (d.airHitBuffer > 0) d.airHitBuffer = Math.max(0, d.airHitBuffer - 1);
                    if (d.backtrackBuffer > 0) d.backtrackBuffer = Math.max(0, d.backtrackBuffer - 1);
                    if (d.triggerBotBuffer > 0) d.triggerBotBuffer = Math.max(0, d.triggerBotBuffer - 1);
                    if (d.timerBuffer > 0) d.timerBuffer = Math.max(0, d.timerBuffer - 1);
                    if (d.guiMoveBuffer > 0) d.guiMoveBuffer = Math.max(0, d.guiMoveBuffer - 1);
                    if (d.phaseBuffer > 0) d.phaseBuffer = Math.max(0, d.phaseBuffer - 1);
                    if (d.antiKbBuffer > 0) d.antiKbBuffer = Math.max(0, d.antiKbBuffer - 1);
                    if (d.microHopBuffer > 0) d.microHopBuffer = Math.max(0, d.microHopBuffer - 1);
                    if (d.fastPlaceCount > 0) d.fastPlaceCount = Math.max(0, d.fastPlaceCount - 1);
                    d.swarmHitIds.clear();
                }
                long cutoff = System.currentTimeMillis() - 5000L;
                for (Iterator<Map.Entry<String, Long>> it = recentBlockPlaces.entrySet().iterator(); it.hasNext(); ) {
                    if (it.next().getValue().longValue() < cutoff) it.remove();
                }
            }
        }.runTaskTimer(this, 800L, 800L);

        getLogger().info("==================================================================");
        getLogger().info(" KingAC v8.2 - PROFESSIONAL NEURAL ANTI-CHEAT ENABLED              ");
        getLogger().info(" * 12 LIVE INJECTION SCHEMES (360 / WALL / ORBIT / ZENITH / SWARM  ");
        getLogger().info("   x4 / HITBOX / BACKTRACK / NADIR / TWIN-WALL / SWAP / LOW-SWEEP  ");
        getLogger().info(" * 3D RAYCAST HITBOX: air-hit blocker (HitBox / SilentAura)        ");
        getLogger().info(" * 13-POINT WALL RAYTRACE + THROUGH-WALL INJECT-BOT                ");
        getLogger().info(" * Micro-Twitch GCD sensor / NoSwing / KeepSprint / TriggerBot     ");
        getLogger().info(" * Timer / Blink / GuiMove / Scaffold / FastPlace / Phase / AntiKB ");
        getLogger().log(java.util.logging.Level.INFO, " * Server API: {0} | AutoBan 24/7: {1}",
                new Object[]{Bukkit.getVersion(), autoBanEnabled ? "ON" : "OFF"});
        getLogger().info(" * Commands: /ac /kac /kingac /inject /report /reports            ");
        getLogger().info("==================================================================");
        Bukkit.getConsoleSender().sendMessage(c("&#FF1744&l🛡 [KingАЧ v8.2] &fНейро-античит запущен! &7Схем инжекта: &#FFD74012&7, команд: &#00E676/ac /kac /inject /report"));
    }

    @Override
    public void onDisable() {
        for (UUID probeId : new ArrayList<UUID>(activeProbes.keySet())) {
            Entity e = Bukkit.getEntity(probeId);
            if (e != null) e.remove();
        }
        activeProbes.clear();
        probeSchemes.clear();
        wallProbeIds.clear();
        recentBlockPlaces.clear();
        ascendingExecution.clear();
        monitors.clear();
        players.clear();
        saveBansFile();
        closeDetectionLog();
    }

    private void initDetectionLog() {
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();
            logFile = new File(getDataFolder(), "detections.log");
            if (!logFile.exists()) logFile.createNewFile();
            logTail.clear();
            logDetection("SERVER-START", "KingAC v8.2 запущен на " + Bukkit.getVersion()
                    + " | api-version=1.16 | схем инжекта=12");
        } catch (Throwable t) {
            logFile = null;
        }
    }

    private void closeDetectionLog() {
        logFile = null;
    }

    private void logDetection(String type, String details) {
        String line = "[" + stampNow() + "] [" + type + "] " + details;
        synchronized (logTail) {
            logTail.add(line);
            while (logTail.size() > 250) logTail.remove(0);
        }
        final File f = logFile;
        if (f == null) return;
        try {
            Writer w = new OutputStreamWriter(new FileOutputStream(f, true), "UTF-8");
            try {
                w.write(line);
                w.write("\n");
            } finally {
                w.close();
            }
        } catch (Throwable ignored) {}
    }

    private void reloadSettingsFromConfig() {
        reloadConfig();
        this.autoBanEnabled = getConfig().getBoolean("settings.auto-ban-enabled", true);
        this.autoBanVl = Math.max(8, getConfig().getInt("settings.auto-ban-vl", 18));
        this.autoBanDurationSec = Math.max(300L, getConfig().getLong("settings.auto-ban-duration-seconds", 2592000L));
        this.setbackEnabled = getConfig().getBoolean("settings.movement-setback-enabled", true);
        this.microTwitchEnabled = getConfig().getBoolean("settings.micro-head-twitch-check", true);
        this.rayHitboxEnabled = getConfig().getBoolean("settings.ray-hitbox-air-block", true);
        this.maxAutobanPingMs = Math.max(150, getConfig().getInt("settings.max-autoban-ping-ms", 420));
        this.reportCooldownSec = Math.max(5, getConfig().getInt("settings.report-cooldown-seconds", 25));
        this.autoInjectSchemes = getConfig().getBoolean("settings.auto-inject-schemes", true);
        for (InjectionScheme sc : InjectionScheme.values()) {
            this.schemeEnabled.put(sc, Boolean.valueOf(getConfig().getBoolean("schemes." + sc.id, true)));
        }
    }

    private boolean schemeOn(InjectionScheme sc) {
        Boolean b = schemeEnabled.get(sc);
        return b == null || b.booleanValue();
    }

    private void persistSettingsToConfig() {
        getConfig().set("settings.auto-ban-enabled", Boolean.valueOf(autoBanEnabled));
        getConfig().set("settings.auto-ban-vl", Integer.valueOf(autoBanVl));
        getConfig().set("settings.movement-setback-enabled", Boolean.valueOf(setbackEnabled));
        getConfig().set("settings.micro-head-twitch-check", Boolean.valueOf(microTwitchEnabled));
        getConfig().set("settings.ray-hitbox-air-block", Boolean.valueOf(rayHitboxEnabled));
        getConfig().set("settings.auto-inject-schemes", Boolean.valueOf(autoInjectSchemes));
        for (InjectionScheme sc : InjectionScheme.values()) {
            getConfig().set("schemes." + sc.id, Boolean.valueOf(schemeOn(sc)));
        }
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
        d.pushBoxSnapshot(now, p.getBoundingBox());

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

        // -------- B0. v8.2: TIMER / BLINK (пакетный баланс move-пакетов) + GUIMOVE --------
        analyzePacketBalance(p, d, now, from, to);
        analyzeGuiMove(p, d, now, from, to);

        if (p.getAllowFlight() || p.isFlying() || p.isGliding() || p.isInsideVehicle() || isRiptidingSafe(p) || p.isSwimming()) {
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
        d.lastMoveHoriz = horiz;
        if (horiz < 0.235 && d.keepSprintBuffer > 0) {
            d.keepSprintBuffer = Math.max(0, d.keepSprintBuffer - 2);
        }

        if (horiz > 8.5) {
            d.lastTeleportOrGlideMs = now;
            d.airHoverTicks = 0;
            return;
        }

        boolean onGround = isOnSolidGround(to) || isOnSolidGround(from);
        if (onGround && !to.getBlock().isLiquid()) {
            d.lastSafeGroundLoc = from.clone();
        }

        // -------- B1. v8.2: PHASE (проход сквозь блоки) / ANTIKB (вертикаль+горизонталь) / MICROHOP-КРИТЫ --------
        analyzePhaseNoClip(p, d, from, to, now);
        analyzeAntiKnockback(p, d, dx, dz, dy, now, pingEarly(p));
        analyzeMicroHopCrits(p, d, dy, to, now, onGround);

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
        if (!inLiquidOrClimb && !onGround && !recentlyDamaged && !hasEffectName(p, "LEVITATION") && !hasEffectName(p, "SLOW_FALLING")) {
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

        // 5. v8.2: PITCH-SNAP — вертикальный снап прицела (>45° за 1 тик в бою)
        if (absPitch >= 45.0f && absPitch <= 92.0f) {
            d.pitchSnapBuffer += 2 * sensitivityMult;
            if (d.pitchSnapBuffer >= 5) {
                d.pitchSnapBuffer = 1;
                d.combatConfirmations++;
                flag(p, "AimBot [Вертикальный Снап]",
                        "Рывок головы по pitch на " + (int) absPitch + "° за 1 тик в бою", 3, false);
                injectInvisibleDecoyProbe(p, d, now, true);
            }
        } else if (absPitch < 8.0f && d.pitchSnapBuffer > 0) {
            d.pitchSnapBuffer--;
        }

        // 6. v8.2: ГОЛОВА ВНИЗ-ВВЕРХ — осцилляции pitch (флипы знака >= 18° подряд в бою)
        if (absPitch >= 18.0f) {
            if (d.lastFlipPitch != 0f && (dpitch * d.lastFlipPitch) < 0f && (now - d.lastPitchFlipMs) <= 950L) {
                d.pitchFlipCount++;
                if (d.pitchFlipCount >= 3) {
                    d.pitchFlipCount = 0;
                    d.combatConfirmations++;
                    flag(p, "Aim [Голова Вниз-Вверх]",
                            "Вертикальные флипы pitch +" + (int) Math.abs(dpitch) + "° / -"
                                    + (int) Math.abs(d.lastFlipPitch) + "° за <1 сек. в бою", 3, false);
                    injectInvisibleDecoyProbe(p, d, now, true);
                }
            } else if ((now - d.lastPitchFlipMs) > 1400L) {
                d.pitchFlipCount = 0;
            }
            d.lastFlipPitch = dpitch;
            d.lastPitchFlipMs = now;
        }

        // 7. v8.2: ВЕРТИКАЛЬНЫЙ ЛОК — голова зажата вверх/вниз (±75..90°) ВО ВРЕМЯ УДАРОВ
        if (curPitch <= -75.0f || curPitch >= 75.0f) {
            d.headLockTicks++;
            if (d.headLockTicks >= 7) {
                d.headLockTicks = 2;
                d.combatConfirmations++;
                flag(p, "SilentAura [Вертикальный Лок]",
                        String.format(Locale.US, "Удары с головой, зажатой на pitch %.0f° (вне экрана PvP)", Float.valueOf(curPitch)),
                        3, false);
                injectInvisibleDecoyProbe(p, d, now, true);
            }
        } else if (curPitch > -60.0f && curPitch < 60.0f && d.headLockTicks > 0) {
            d.headLockTicks = Math.max(0, d.headLockTicks - 2);
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
    //  1B. v8.2 — НОВЫЕ ПРОВЕРКИ: TIMER / BLINK / GUIMOVE / PHASE / ANTIKB / MICROHOP
    // =================================================================================

    /** Пинг без падения на 1.16.5 (Player#getPing появился только в 1.17). */
    private static int pingEarly(Player p) {
        return safePing(p);
    }

    /**
     * TIMER / BLINK — анализ БАЛАНСА move-пакетов:
     * ванильный клиент шлёт ровно 20 пакетов позиции в секунду (1 на тик).
     * Timer 1.2x-2.0x шлёт 24-40 пакетов/сек, Blink «копит» пакеты и выпускает их рывком.
     */
    private void analyzePacketBalance(Player p, PlayerAcData d, long now, Location from, Location to) {
        int ping = safePing(p);

        if (d.timerWindowStartMs <= 0L) {
            d.timerWindowStartMs = now;
            d.timerMovePackets = 0;
        }
        d.timerMovePackets++;
        long span = now - d.timerWindowStartMs;
        if (span >= 1000L) {
            double rate = d.timerMovePackets * 1000.0 / (double) span;
            if (rate >= 25.0 && ping < 240 && !p.isInsideVehicle() && !p.isFlying() && !p.isGliding()
                    && !p.getAllowFlight() && !hasEffectName(p, "LEVITATION")) {
                d.timerBuffer += 2;
                if (d.timerBuffer >= 6) {
                    d.timerBuffer = 1;
                    flag(p, "Timer [Ускорение Тиков]",
                            String.format(Locale.US, "%.1f move-пакетов/сек (норма 20.0, ускорение x%.2f)",
                                    Double.valueOf(rate), Double.valueOf(rate / 20.0)), 3, true);
                }
            } else if (rate <= 21.5 && d.timerBuffer > 0) {
                d.timerBuffer--;
            }
            d.timerWindowStartMs = now;
            d.timerMovePackets = 0;
        }

        // BLINK: «заморозка» пакетов с последующим мгновенным рывком (только реальные телепорт-рывки!)
        long gap = (d.lastMovePacketMs > 0L) ? (now - d.lastMovePacketMs) : 0L;
        double horiz = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
        d.lastMovePacketMs = now;
        d.lastMovePacketHoriz = horiz;
        if (p.isGliding() || p.isInsideVehicle() || (now - d.lastTeleportOrGlideMs) < 1400L) {
            if (d.blinkBuffer > 0) d.blinkBuffer--;
            return;
        }
        if (gap > 900L && gap < 9000L && horiz > 1.25 && ping < 240) {
            d.blinkBuffer += 2;
            if (d.blinkBuffer >= 4) {
                d.blinkBuffer = 0;
                flag(p, "Blink [Заморозка Пакетов]",
                        "Пакеты держались " + gap + "мс, затем рывок "
                                + String.format(Locale.US, "%.2f", Double.valueOf(horiz)) + " бл.", 3, true);
            }
        } else if (horiz > 2.6 && ping < 260) {
            d.blinkBuffer += 3;
            if (d.blinkBuffer >= 6) {
                d.blinkBuffer = 0;
                flag(p, "Blink / Step [Рывок]",
                        String.format(Locale.US, "Перемещение %.2f бл. за 1 пакет", Double.valueOf(horiz)), 3, true);
                applySetbackSafe(p, d);
            }
        } else if (d.blinkBuffer > 0 && gap < 400L) {
            d.blinkBuffer--;
        }
    }

    private void applySetbackSafe(Player p, PlayerAcData d) {
        if (!setbackEnabled || d.banInProgress || d.lastSafeGroundLoc == null) return;
        if (d.lastSafeGroundLoc.getWorld() != null && d.lastSafeGroundLoc.getWorld().equals(p.getWorld())
                && d.lastSafeGroundLoc.distanceSquared(p.getLocation()) <= 64.0) {
            Location sb = d.lastSafeGroundLoc.clone();
            sb.setYaw(p.getLocation().getYaw());
            sb.setPitch(p.getLocation().getPitch());
            try { p.teleport(sb); } catch (Throwable ignored) {}
        }
    }

    /**
     * GUIMOVE / INVWALK — бег и прыжки с ОТКРЫТЫМ инвентарём/сундуком/меню.
     * Ванильный клиент физически не принимает WASD при открытом GUI!
     */
    private void analyzeGuiMove(Player p, PlayerAcData d, long now, Location from, Location to) {
        if (d.lastInvClickMs <= 0L) return;
        long since = now - d.lastInvClickMs;
        if (since > 450L) return;
        if (now - d.lastTeleportOrGlideMs < 800L) return;
        if (now - d.lastDamagedMs < 900L) return;
        if (p.isGliding() || p.isInsideVehicle() || p.isSwimming() || p.isFlying() || p.getAllowFlight()) return;
        InventoryView view;
        try { view = p.getOpenInventory(); } catch (Throwable t) { return; }
        if (view == null) return;
        InventoryType type;
        try { type = view.getType(); } catch (Throwable t) { return; }
        if (type == null || type == InventoryType.CRAFTING || type == InventoryType.CREATIVE) return;
        if (from.getBlock().isLiquid() || to.getBlock().isLiquid()) return;

        double horiz = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
        double dy = to.getY() - from.getY();
        if (horiz > 0.145 || dy > 0.30) {
            d.guiMoveBuffer += 2;
            if (d.guiMoveBuffer >= 6) {
                d.guiMoveBuffer = 1;
                flag(p, "GuiMove [Ходьба в GUI]",
                        String.format(Locale.US, "Движение %.2f бл/т с открытым меню (%s)",
                                Double.valueOf(horiz), type.name()), 3, true);
                try { p.closeInventory(); } catch (Throwable ignored) {}
            }
        } else if (d.guiMoveBuffer > 0) {
            d.guiMoveBuffer--;
        }
    }

    /** PHASE / NOCLIP — тело игрока целиком внутри твёрдых блоков. */
    private void analyzePhaseNoClip(Player p, PlayerAcData d, Location from, Location to, long now) {
        if (p.isInsideVehicle() || p.isGliding() || p.isSwimming() || p.isFlying() || p.getAllowFlight()) return;
        if (now - d.lastTeleportOrGlideMs < 1300L) return;
        if (now - d.lastDamagedMs < 700L) return;
        Block feet = to.getBlock();
        Block head = to.clone().add(0, 1.15, 0).getBlock();
        boolean insideSolid = !feet.isPassable() && !head.isPassable()
                && feet.getType().isSolid() && head.getType().isSolid();
        if (!insideSolid) {
            if (d.phaseBuffer > 0) d.phaseBuffer--;
            return;
        }
        // v8.2: ловим ТОЛЬКО вход в блок из проходимой позиции (игрок, застрявший в блоке после грифа, не флажится!)
        boolean fromOpen = from.getBlock().isPassable() || from.clone().add(0, 1.15, 0).getBlock().isPassable();
        if (!fromOpen) {
            if (d.phaseBuffer > 0) d.phaseBuffer--;
            return;
        }
        if (hasNearBlock(to, "PISTON", "DOOR", "TRAPDOOR", "SLIME", "HONEY", "BED", "LADDER", "VINE",
                "SCAFFOLDING", "SHULKER", "CHEST", "BARREL", "SIGN", "BANNER", "SKULL", "CAMPFIRE", "ANVIL")) return;
        if (wasBlockJustPlaced(feet, now, 1200L) || wasBlockJustPlaced(head, now, 1200L)) return;

        d.phaseBuffer += 2;
        if (d.phaseBuffer >= 6) {
            d.phaseBuffer = 1;
            flag(p, "Phase [Проход Сквозь Блоки]",
                    "Тело внутри " + feet.getType().name() + " / " + head.getType().name(), 4, false);
            applySetbackSafe(p, d);
        }
    }

    /** ANTIKB — игнорирование отбрасывания по вертикали и горизонтали (с учётом незерита 40%). */
    private void analyzeAntiKnockback(Player p, PlayerAcData d, double dx, double dz, double dy, long now, int ping) {
        if (d.lastVelocityMs <= 0L) return;
        long since = now - d.lastVelocityMs;
        if (since > 900L) {
            d.lastVelocityMs = 0L;
            d.velocityTicks = 0;
            d.velocityClimb = 0.0;
            d.velocityRun = 0.0;
            return;
        }
        if (p.isInsideVehicle() || p.isFlying() || p.isGliding() || p.getAllowFlight()
                || hasEffectName(p, "LEVITATION") || hasEffectName(p, "SLOW_FALLING")) return;
        // v8.2: проверяем AntiKB только сразу после получения урона (боевое отбрасывание),
        // а не после взрывов/поршней/удочек, и не в воде/паутине/со щитом!
        if ((now - d.lastDamagedMs) > 900L) return;
        if (p.isBlocking()) return;
        if (hasNearBlock(p.getLocation(), "WATER", "LAVA", "COBWEB", "HONEY", "SLIME", "LADDER",
                "VINE", "SCAFFOLDING", "BED", "POWDER", "BUBBLE")) return;

        d.velocityTicks++;
        d.velocityClimb += dy;
        d.velocityRun += Math.hypot(dx, dz);
        if (d.velocityTicks < 6) return;

        boolean ceiling = !p.getLocation().clone().add(0, 1.6, 0).getBlock().isPassable();

        // ВЕРТИКАЛЬНОЕ ОТБРАСЫВАНИЕ (самый надёжный признак AntiKB)
        if (d.lastVelocityY > 0.30 && !ceiling) {
            double expectUp = d.lastVelocityY * 2.6;
            if (d.velocityClimb < expectUp * 0.35 && ping < 220) {
                d.antiKbBuffer += 2;
                if (d.antiKbBuffer >= 6) {
                    d.antiKbBuffer = 1;
                    flag(p, "AntiKB [Вертикаль]",
                            String.format(Locale.US, "Подъём %.2f бл. вместо %.2f бл. после удара",
                                    Double.valueOf(Math.max(0.0, d.velocityClimb)), Double.valueOf(expectUp)),
                            3, true);
                }
            } else if (d.antiKbBuffer > 0) {
                d.antiKbBuffer--;
            }
        }

        // ГОРИЗОНТАЛЬНОЕ ОТБРАСЫВАНИЕ (только без незеритовой брони: ваниль даёт 40% сопротивления!)
        if (!hasNetheriteArmor(p) && d.lastVelocityHoriz > 0.34 && ping < 200) {
            double expectRun = d.lastVelocityHoriz * 2.2;
            if (d.velocityRun < expectRun * 0.35) {
                d.antiKbBuffer += 2;
                if (d.antiKbBuffer >= 10) {
                    d.antiKbBuffer = 2;
                    flag(p, "AntiKB [Горизонталь]",
                            String.format(Locale.US, "Смещение %.2f бл. вместо %.2f бл.",
                                    Double.valueOf(d.velocityRun), Double.valueOf(expectRun)), 3, true);
                }
            } else if (d.antiKbBuffer > 0) {
                d.antiKbBuffer--;
            }
        }

        d.lastVelocityMs = 0L;
        d.velocityTicks = 0;
        d.velocityClimb = 0.0;
        d.velocityRun = 0.0;
    }

    private static boolean hasNetheriteArmor(Player p) {
        try {
            ItemStack[] armor = p.getInventory().getArmorContents();
            for (int i = 0; i < armor.length; i++) {
                if (armor[i] != null && armor[i].getType().name().contains("NETHERITE")) return true;
            }
            ItemStack off = p.getInventory().getItemInOffHand();
            if (off != null && off.getType().name().contains("NETHERITE")) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    /** CRITICALS [Микро-Хоп] — серия мини-подпрыгиваний 0.05-0.14 бл. прямо во время боя. */
    private void analyzeMicroHopCrits(Player p, PlayerAcData d, double dy, Location to, long now, boolean onGround) {
        if (!onGround || dy <= 0.045 || dy >= 0.14) {
            if (dy < -0.05 && d.microHopBuffer > 0) d.microHopBuffer--;
            return;
        }
        if (now - d.lastDamagedMs < 900L) return;
        if (hasNearBlock(to, "CARPET", "SNOW", "LILY", "PRESSURE", "SLAB", "STAIRS", "BED", "SKULL",
                "LANTERN", "CHAIN", "CAKE", "COMPOSTER", "END_ROD", "PICKLE", "CANDLE", "AMETHYST",
                "POINTED", "SCAFFOLDING", "HONEY", "SLIME", "PISTON", "DAYLIGHT", "LECTERN", "GRINDSTONE")) return;
        Block under = to.clone().add(0, -0.55, 0).getBlock();
        if (!under.getType().isSolid()) return;

        d.microHopBuffer++;
        if (d.microHopBuffer >= 3 && (now - d.lastCombatMs) < 2600L) {
            d.microHopBuffer = 0;
            d.combatConfirmations++;
            flag(p, "Criticals [Микро-Хоп]",
                    String.format(Locale.US, "Серия мини-подпрыгиваний dy=%.3f в бою", Double.valueOf(dy)), 3, false);
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
        // ================= v8.2: ПОПАДАНИЕ ПО НЕВИДИМОМУ ФАНТОМУ ОДНОЙ ИЗ 8 СХЕМ ИНЖЕКТА =================
        UUID probeSuspect = activeProbes.get(e.getEntity().getUniqueId());
        if (probeSuspect != null) {
            e.setCancelled(true);
            Entity probe = e.getEntity();
            UUID probeId = probe.getUniqueId();
            InjectionScheme scheme = probeSchemes.remove(probeId);
            boolean isWallProbe = wallProbeIds.remove(probeId);
            activeProbes.remove(probeId);
            if (scheme == null) scheme = isWallProbe ? InjectionScheme.THROUGH_WALL : InjectionScheme.BACK_360;
            Block stillBlockedBy = (isWallProbe && probe instanceof LivingEntity)
                    ? findBlockingWallBetween(attacker, (LivingEntity) probe)
                    : null;
            probe.remove();

            if (!isExempt(attacker) && e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
                PlayerAcData d = data(attacker);
                boolean ownProbe = probeSuspect.equals(attacker.getUniqueId());
                d.totalInjectHits++;
                sessionInjectHits++;
                d.lastInjectScheme = scheme;

                if (isWallProbe || scheme == InjectionScheme.THROUGH_WALL) {
                    if (stillBlockedBy != null) {
                        // Честный клиент ФИЗИЧЕСКИ не может кликнуть сущность за сплошным блоком!
                        d.combatConfirmations += 3;
                        d.wallHitBuffer += 2;
                        int addVl = d.wallHitBuffer >= 3 ? Math.max(9, autoBanVl) : 9;
                        flag(attacker, "WallAura [Инжект Сквозь Стену]",
                                "Пробил фантом схемы «" + scheme.displayTitle + "» сквозь " + stillBlockedBy.getType().name(),
                                addVl, false);
                    }
                } else {
                    d.combatConfirmations += ownProbe ? 2 : 1;
                    int vl = scheme.baseVl + (ownProbe ? 0 : 2);

                    if (scheme == InjectionScheme.SWARM_QUAD) {
                        long sNow = System.currentTimeMillis();
                        if (sNow - d.lastSwarmHitMs > 2600L) d.swarmHitIds.clear();
                        d.lastSwarmHitMs = sNow;
                        d.swarmHitIds.add(probeId);
                        int swarmHits = d.swarmHitIds.size();
                        if (swarmHits >= 2) {
                            d.combatConfirmations += 2;
                            flag(attacker, "MultiAura [Рой x" + swarmHits + "]",
                                    "Задел " + swarmHits + " невидимых фантома схемы «Рой x4» за <2.6 сек.", Math.max(vl, 9), false);
                        } else {
                            flag(attacker, "KillAura [Рой x4]", "Ударил невидимого фантома из слепой зоны (рой 1/4)", vl, false);
                        }
                    } else if (scheme == InjectionScheme.ZENITH_CROWN) {
                        flag(attacker, "SilentAura [Купол +85°]",
                                "Ударил фантом над макушкой (+2.30м вне экрана) — SilentAura/MultiPoints", vl, false);
                    } else if (scheme == InjectionScheme.ORBIT_VORTEX) {
                        flag(attacker, "AimLock [Орбита-Вихрь]",
                                "Поймал вращающегося фантома (28°/тик) — магнит камеры AimBot", vl, false);
                    } else if (scheme == InjectionScheme.HITBOX_OFFSET) {
                        d.airHitBuffer += 2;
                        flag(attacker, "HitBoxes [Байт-Хитбокс]",
                                "Ударил фантом в пустом воздухе сбоку от прицела — расширенные хитбоксы", vl, false);
                    } else if (scheme == InjectionScheme.BACKTRACK_PULSE) {
                        d.backtrackBuffer += 2;
                        flag(attacker, "Backtrack / Reach [Отступ]",
                                "Достал отпрыгнувшего на 4.2м фантома — Backtrack/Reach", vl, false);
                    } else if (scheme == InjectionScheme.BACK_360) {
                        flag(attacker, "KillAura [Спина-360°]",
                                "Атаковал невидимого фантома за спиной (схема «Спина-360°»)", vl, false);
                    } else {
                        flag(attacker, "KillAura [Инжект " + scheme.id + "]",
                                "Атаковал невидимую приманку схемы «" + scheme.displayTitle + "»", vl, false);
                    }

                    if (ownProbe && (scheme == InjectionScheme.BACK_360 || scheme == InjectionScheme.ORBIT_VORTEX
                            || scheme == InjectionScheme.ZENITH_CROWN)) {
                        // Продолжаем давление: следующая схема сразу после поимки!
                        InjectionScheme next = nextSchemeAfter(scheme);
                        if (next != null) {
                            final InjectionScheme nxt = next;
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    Player still = Bukkit.getPlayer(probeSuspect);
                                    if (still != null && still.isOnline() && !isExempt(still)) {
                                        runInjectionScheme(still, nxt, false, null);
                                    }
                                }
                            }.runTaskLater(this, 12L);
                        }
                    }
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
        long prevCombatMs = d.lastCombatMs;
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
        d.currentCps = cps;
        if (cps >= 21) {
            d.combatConfirmations++;
            flag(attacker, "AutoClicker [CPS]", "Скорость кликов: " + cps + " КПС", 2, false);
        } else if (cps >= 12 && d.clickIntervals.size() >= 16) {
            double mean = 0.0;
            for (Long iv : d.clickIntervals) mean += iv.doubleValue();
            mean /= d.clickIntervals.size();
            double var = 0.0;
            double m4 = 0.0;
            for (Long iv : d.clickIntervals) {
                double diff = iv.doubleValue() - mean;
                var += diff * diff;
                m4 += diff * diff * diff * diff;
            }
            double stdDev = Math.sqrt(var / d.clickIntervals.size());
            double denom = (var / d.clickIntervals.size());
            double kurtosis = (denom > 0.0001) ? (m4 / d.clickIntervals.size()) / (denom * denom) : 0.0;
            if (stdDev < 3.2 && mean < 95.0) {
                d.clickIntervals.clear();
                d.combatConfirmations++;
                flag(attacker, "AutoClicker [Макрос]", String.format(Locale.US, "Робот-тайминг кликов (±%.1fмс)", Double.valueOf(stdDev)), 2, false);
            } else if (kurtosis > 6.5 && stdDev < 26.0 && mean < 130.0) {
                d.clickIntervals.clear();
                d.combatConfirmations++;
                flag(attacker, "AutoClicker [Куртозис]",
                        String.format(Locale.US, "Пилообразный CPS-ритм (k=%.1f, ±%.1fмс)",
                                Double.valueOf(kurtosis), Double.valueOf(stdDev)), 2, false);
            }
        }

        // 2B. v8.2: TriggerBot — удар в тот же тик, когда цель только-только попала в прицел
        if (victim instanceof Player && d.lastTargetId != null && !d.lastTargetId.equals(victim.getUniqueId())
                && (now - d.lastRotMs) <= 95L && Math.abs(d.prevDeltaYaw) >= 7.0f
                && Math.abs(d.prevDeltaPitch) <= 22.0f) {
            d.triggerBotBuffer += 2;
            if (d.triggerBotBuffer >= 6) {
                d.triggerBotBuffer = 1;
                d.combatConfirmations++;
                flag(attacker, "TriggerBot [Мгновенный Захват]",
                        String.format(Locale.US, "Удар через %dмс после доводки на цель (%.1f°)",
                                Long.valueOf(Math.max(0L, now - d.lastRotMs)), Float.valueOf(Math.abs(d.prevDeltaYaw))),
                        3, false);
                injectInvisibleDecoyProbe(attacker, d, now, true);
            }
        } else if (d.triggerBotBuffer > 0) {
            d.triggerBotBuffer--;
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

        // 4B. KillAura [NoSwing / Пакетный Удар без Взмаха Руки]
        if (victim instanceof Player && d.lastArmSwingMs > 0L && (now - d.lastArmSwingMs) > 290L) {
            d.noSwingBuffer += 2;
            if (d.noSwingBuffer >= 4) {
                d.noSwingBuffer = 1;
                e.setCancelled(true);
                d.combatConfirmations += 2;
                flag(attacker, "KillAura [Без Взмаха Руки]", "Пакет атаки без анимации взмаха руки", 5, false);
                return;
            }
        } else if (d.noSwingBuffer > 0) {
            d.noSwingBuffer--;
        }

        // 5. Reach / HitBox (Точный 3D луч до BoundingBox с пинг-компенсацией)
        int ping = safePing(attacker);
        double maxReach = 3.38 + (attacker.getGameMode() == GameMode.CREATIVE ? 1.5 : 0.0) + Math.min(0.62, ping * 0.0024);
        double distToBox = distanceToBox(attacker.getEyeLocation().toVector(), victim.getBoundingBox());
        if (distToBox > maxReach) {
            if (distToBox > maxReach + 0.25) {
                e.setCancelled(true);
            }
            d.reachBuffer += 2;
            if (d.reachBuffer >= 4) {
                d.reachBuffer = 1;
                e.setCancelled(true);
                flag(attacker, "Reach / HitBox",
                        String.format(Locale.US, "Удар за %.2f бл. (лимит %.2f)", Double.valueOf(distToBox), Double.valueOf(maxReach)),
                        4, true);
                injectInvisibleDecoyProbe(attacker, d, now, false);
                return;
            }
        } else if (d.reachBuffer > 0) {
            d.reachBuffer--;
        }

        // 6A. ТОЧНЫЙ 3D RAYCAST ХИТБОКСА: ПРОВЕРКА ЧТО ИГРОК БЬЁТ В ИГРОКА, А НЕ В ВОЗДУХ! (HitBox / SilentAura / Backtrack)
        if (rayHitboxEnabled && victim instanceof Player) {
            if (!verifyHitboxRayIntersection(attacker, d, (Player) victim, e, now, ping, distToBox)) {
                return;
            }
        } else {
            Vector eyeDir = attacker.getEyeLocation().getDirection().normalize();
            Vector toVictim = victim.getBoundingBox().getCenter().subtract(attacker.getEyeLocation().toVector());
            if (toVictim.lengthSquared() > 0.35) {
                double dot = eyeDir.dot(toVictim.clone().normalize());
                double angleDeg = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
                if (angleDeg > 78.0 && distToBox > 1.15) {
                    e.setCancelled(true);
                    d.combatConfirmations++;
                    flag(attacker, "KillAura [Мимо Хитбокса]", "Удар под углом " + (int) angleDeg + "° мимо цели", 4, false);
                    injectInvisibleDecoyProbe(attacker, d, now, true);
                    return;
                }
            }
        }

        // 6B. v8.2: ЖЁСТКИЙ БЛОК УРОНА СКВОЗЬ СТЕНЫ / СТЕКЛО ТРАПКИ / ПЛАСТ / ДВЕРИ.
        // Голосование 11 лучей: если >= 8 из 11 углов хитбокса жертвы закрыты блоком —
        // урон НЕ регается НИКОГДА (раньше одна открытая щель разрешала весь урон!).
        WallVote vote = voteWallBetween(attacker, victim);
        Block wallBlock = null;
        int blockedRays = 0;
        if (vote != null && vote.blocked >= 8) {
            wallBlock = vote.wall;
            blockedRays = vote.blocked;
        } else {
            Block strict = findBlockingWallBetween(attacker, victim);
            if (strict != null) {
                wallBlock = strict;
                blockedRays = (vote != null) ? vote.blocked : 11;
            }
        }
        if (wallBlock != null) {
            // МГНОВЕННО блокируем урон сквозь любую стену / трапку / пласт / стекло!
            e.setCancelled(true);
            // Сразу инжектируем проверочного бота за эту же стену, чтобы на следующем ударе WallAura попалась со 100% точностью!
            injectThroughWallProbe(attacker, d, wallBlock, now, true);

            if (!wasBlockJustPlaced(wallBlock, now, 320L)) {
                d.wallHitBuffer++;
                d.combatConfirmations += 2;
                int vlAdd = (blockedRays >= 10) ? 7 : 5;
                flag(attacker, "WallHit [Удар Через Стену]",
                        "Урон сквозь " + wallBlock.getType().name() + " заблокирован (лучей закрыто "
                                + blockedRays + "/11," + String.format(Locale.US, " %.2fм)", Double.valueOf(distToBox)),
                        vlAdd, false);
            }
            return;
        }

        // 7. KillAura [MultiAura] (смена 2 разных целей быстрее 45мс)
        if (d.lastTargetId != null && !d.lastTargetId.equals(victim.getUniqueId()) && (now - prevCombatMs) < 45L) {
            e.setCancelled(true);
            d.combatConfirmations++;
            flag(attacker, "KillAura [Мульти-Аура]", "Переключение между 2 целями за <45мс", 4, false);
            injectInvisibleDecoyProbe(attacker, d, now, true);
            return;
        }

        // 7B. KeepSprint [Бег при Атаке без ванильного замедления *0.6]
        if (victim instanceof Player && attacker.isSprinting() && d.lastMoveHoriz > 0.278 && getPotionAmp(attacker, "SPEED") <= 1) {
            d.keepSprintBuffer++;
            if (d.keepSprintBuffer >= 7) {
                d.keepSprintBuffer = 2;
                d.combatConfirmations++;
                flag(attacker, "KeepSprint [Бег при Атаке]", "Серия из 7 ударов без сброса спринта", 3, true);
            }
        } else if (d.keepSprintBuffer > 0) {
            d.keepSprintBuffer--;
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

        // v8.2: АВТО-ИНЖЕКТ ПО БОЮ — КАЖДЫЙ дерущийся игрок раз в 12 сек. получает
        // фантом-приманку одной из 12 схем. Читер с аурой неизбежно заденет невидимку!
        if (autoInjectSchemes && victim instanceof Player && (now - d.lastInjectSchemeMs) > 12000L) {
            InjectionScheme autoSc = pickAutoScheme(d);
            if (autoSc != null) {
                runInjectionScheme(attacker, autoSc, false, null);
            }
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
            probeSchemes.put(decoy.getUniqueId(), InjectionScheme.BACK_360);

            new BukkitRunnable() {
                @Override
                public void run() {
                    activeProbes.remove(decoy.getUniqueId());
                    probeSchemes.remove(decoy.getUniqueId());
                    if (decoy.isValid()) decoy.remove();
                }
            }.runTaskLater(this, 7L);
        } catch (Throwable ignored) {}
    }

    // =================================================================================
    //  2B. v8.2 — 8 ЖИВЫХ СХЕМ ИНЖЕКТА (INJECTION SCHEMES) + /inject + GUI СХЕМ
    //      Схемы спавнят невидимых микро-фантомов в тех точках, куда честный клиент
    //      ФИЗИЧЕСКИ не может отправить пакет атаки. Любой чит-клиент бьёт по ним.
    // =================================================================================
    private ArmorStand spawnSchemeProbe(Player suspect, Location loc, InjectionScheme sc, int ttlTicks) {
        if (suspect == null || loc == null || loc.getWorld() == null) return null;
        try {
            loc.setYaw(suspect.getLocation().getYaw());
            loc.setPitch(0f);
            ArmorStand probe = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
            probe.setPersistent(false);
            probe.setVisible(false);
            probe.setGravity(false);
            probe.setSmall(true);
            probe.setSilent(true);
            probe.setBasePlate(false);
            probe.addScoreboardTag("kingac_probe");
            probe.addScoreboardTag("kingac_scheme_" + sc.id);
            final UUID pid = probe.getUniqueId();
            activeProbes.put(pid, suspect.getUniqueId());
            probeSchemes.put(pid, sc);
            sessionInjectProbes++;
            new BukkitRunnable() {
                @Override
                public void run() {
                    activeProbes.remove(pid);
                    probeSchemes.remove(pid);
                    wallProbeIds.remove(pid);
                    Entity en = Bukkit.getEntity(pid);
                    if (en != null) en.remove();
                }
            }.runTaskLater(this, (long) Math.max(2, ttlTicks));
            return probe;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Vector horizDir(Location loc) {
        Vector v = loc.getDirection().setY(0);
        if (v.lengthSquared() < 0.0001) v = new Vector(0, 0, 1);
        return v.normalize();
    }

    private static Vector rightOf(Vector dir) {
        return new Vector(-dir.getZ(), 0, dir.getX()).normalize();
    }

    public boolean runInjectionScheme(Player suspect, InjectionScheme sc, boolean manual, CommandSender by) {
        if (suspect == null || !suspect.isOnline() || sc == null) return false;
        if (!manual && !schemeOn(sc)) return false;
        if (isOwnerPlayer(suspect)) {
            if (by != null) by.sendMessage(c("&#FF1744✖ OWNER защищён от инжект-схем KingАЧ!"));
            return false;
        }
        if (isExempt(suspect)) {
            if (by != null) by.sendMessage(c("&#FFD740⚠ Игрок в креативе/спектре или под иммунитетом — инжект отменён."));
            return false;
        }
        final Player target = suspect;
        final PlayerAcData d = data(suspect);
        long now = System.currentTimeMillis();
        if (!manual && (now - d.lastInjectSchemeMs) < 7000L) return false;
        d.lastInjectSchemeMs = now;
        d.lastInjectScheme = sc;
        d.activeSchemeLabel = sc.displayTitle;

        // ---------- СХЕМА 1: СПИНА-360° (фанта следует за затылком игрока 20 тиков) ----------
        if (sc == InjectionScheme.BACK_360) {
            Vector back = horizDir(target.getLocation()).multiply(-1.75);
            Location loc = target.getLocation().clone().add(back).add(0, 0.35, 0);
            final ArmorStand probe = spawnSchemeProbe(target, loc, sc, 24);
            if (probe == null) return false;
            final UUID pid = probe.getUniqueId();
            new BukkitRunnable() {
                int t = 0;
                @Override
                public void run() {
                    t++;
                    if (t >= 20 || !target.isOnline() || !probe.isValid() || !activeProbes.containsKey(pid)) {
                        cancel();
                        return;
                    }
                    Location nl = target.getLocation().clone()
                            .add(horizDir(target.getLocation()).multiply(-1.75)).add(0, 0.35, 0);
                    probe.teleport(nl);
                }
            }.runTaskTimer(this, 1L, 1L);
            return true;
        }

        // ---------- СХЕМА 2: СКВОЗЬ-СТЕНУ (фантом за сплошным блоком/стеклом трапки) ----------
        if (sc == InjectionScheme.THROUGH_WALL) {
            d.lastWallProbeMs = 0L;
            Block wall = null;
            RayTraceResult rt = target.getWorld().rayTraceBlocks(target.getEyeLocation(),
                    target.getEyeLocation().getDirection(), 2.6, FluidCollisionMode.NEVER, true);
            if (rt != null) wall = rt.getHitBlock();
            if (wall == null) {
                // Стены перед игроком нет — ищем её по 8 направлениям вокруг (трапка/кабинка/пласт)
                for (int i = 0; i < 8; i++) {
                    float yaw = target.getLocation().getYaw() + (i * 45f);
                    Vector dir = directionFromYawPitch(yaw, 0f);
                    RayTraceResult r2 = target.getWorld().rayTraceBlocks(target.getEyeLocation(), dir, 2.6,
                            FluidCollisionMode.NEVER, true);
                    if (r2 != null && r2.getHitBlock() != null) {
                        wall = r2.getHitBlock();
                        break;
                    }
                }
            }
            if (wall == null) {
                if (by != null) by.sendMessage(c("&#FFD740⚠ Рядом с &#FFD740" + target.getName()
                        + " &#FFD740нет сплошной стены — схема «Сквозь-Стену» пропущена (запущена «Спина-360°»)."));
                d.lastInjectSchemeMs = 0L; // снимаем кулдаун, чтобы резервная схема точно сработала
                return runInjectionScheme(target, InjectionScheme.BACK_360, manual, null);
            }
            boolean ok = injectThroughWallProbe(target, d, wall, now, true);
            if (!ok && by != null) {
                by.sendMessage(c("&#FFD740⚠ Не удалось разместить фантом за стеной (слишком далеко/нет места)."));
            }
            return ok;
        }

        // ---------- СХЕМА 3: ОРБИТА-ВИХРЬ (фантом кружится по 3D-спирали 28°/тик) ----------
        if (sc == InjectionScheme.ORBIT_VORTEX) {
            final double radius = 2.15;
            Location start = target.getLocation().clone()
                    .add(directionFromYawPitch(target.getLocation().getYaw(), 0f).multiply(radius)).add(0, 0.75, 0);
            final ArmorStand probe = spawnSchemeProbe(target, start, sc, 30);
            if (probe == null) return false;
            final UUID pid = probe.getUniqueId();
            final double yaw0 = target.getLocation().getYaw();
            new BukkitRunnable() {
                int t = 0;
                double ang = yaw0;
                @Override
                public void run() {
                    t++;
                    if (t >= 26 || !target.isOnline() || !probe.isValid() || !activeProbes.containsKey(pid)) {
                        cancel();
                        return;
                    }
                    ang += 28.0;
                    Vector v = directionFromYawPitch((float) ang, 0f).multiply(radius);
                    double yOff = 0.60 + Math.sin(Math.toRadians(ang * 2.0)) * 0.62;
                    probe.teleport(target.getLocation().clone().add(v).add(0, yOff, 0));
                }
            }.runTaskTimer(this, 1L, 1L);
            return true;
        }

        // ---------- СХЕМА 4: ВЕРХНИЙ КУПОЛ +85° (фантом над макушкой вне экрана PvP) ----------
        if (sc == InjectionScheme.ZENITH_CROWN) {
            Vector dir = horizDir(target.getLocation());
            Location loc = target.getEyeLocation().clone().add(dir.multiply(0.28)).add(0, 2.30, 0);
            final ArmorStand probe = spawnSchemeProbe(target, loc, sc, 16);
            if (probe == null) return false;
            final UUID pid = probe.getUniqueId();
            new BukkitRunnable() {
                int t = 0;
                @Override
                public void run() {
                    t++;
                    if (t >= 14 || !target.isOnline() || !probe.isValid() || !activeProbes.containsKey(pid)) {
                        cancel();
                        return;
                    }
                    probe.teleport(target.getEyeLocation().clone().add(0, 2.30 + (t % 3) * 0.06, 0));
                }
            }.runTaskTimer(this, 2L, 2L);
            return true;
        }

        // ---------- СХЕМА 5: МУЛЬТИ-РОЙ x4 (четыре фантома во всех слепых зонах) ----------
        if (sc == InjectionScheme.SWARM_QUAD) {
            Vector dir = horizDir(target.getLocation());
            Vector right = rightOf(dir);
            Location base = target.getLocation();
            Location[] spots = new Location[]{
                    base.clone().add(dir.clone().multiply(-1.90)).add(right.clone().multiply(1.35)).add(0, 0.45, 0),
                    base.clone().add(dir.clone().multiply(-1.90)).add(right.clone().multiply(-1.35)).add(0, 0.45, 0),
                    base.clone().add(dir.clone().multiply(-1.05)).add(0, 2.05, 0),
                    base.clone().add(dir.clone().multiply(-2.75)).add(0, 0.25, 0)
            };
            int spawned = 0;
            for (int i = 0; i < spots.length; i++) {
                if (spawnSchemeProbe(target, spots[i], sc, 14) != null) spawned++;
            }
            d.swarmHitIds.clear();
            return spawned > 0;
        }

        // ---------- СХЕМА 6: БАЙТ-ХИТБОКС (фантом в пустом воздухе сбоку от прицела) ----------
        if (sc == InjectionScheme.HITBOX_OFFSET) {
            Vector dir = horizDir(target.getLocation());
            Vector right = rightOf(dir);
            Location eye = target.getEyeLocation();
            for (int s = 1; s >= -1; s -= 2) {
                Location loc = eye.clone().add(dir.clone().multiply(2.35))
                        .add(right.clone().multiply(0.95 * s)).add(0, -0.10, 0);
                if (loc.getBlock().isPassable() || loc.getBlock().isLiquid()) {
                    if (spawnSchemeProbe(target, loc, sc, 12) != null) return true;
                }
            }
            Location fallback = eye.clone().add(dir.clone().multiply(2.35)).add(0, -0.10, 0);
            return spawnSchemeProbe(target, fallback, sc, 12) != null;
        }

        // ---------- СХЕМА 7: ПРИЗРАК-ОТСТУП (2.4м -> резкий прыжок на 4.2м: Reach/Backtrack) ----------
        if (sc == InjectionScheme.BACKTRACK_PULSE) {
            Vector dir = horizDir(target.getLocation());
            Vector right = rightOf(dir);
            Location loc = target.getLocation().clone()
                    .add(dir.clone().multiply(-2.40)).add(right.clone().multiply(0.75)).add(0, 0.35, 0);
            final ArmorStand probe = spawnSchemeProbe(target, loc, sc, 22);
            if (probe == null) return false;
            final UUID pid = probe.getUniqueId();
            new BukkitRunnable() {
                int t = 0;
                @Override
                public void run() {
                    t++;
                    if (t >= 18 || !target.isOnline() || !probe.isValid() || !activeProbes.containsKey(pid)) {
                        cancel();
                        return;
                    }
                    if (t == 6 || t == 12) {
                        Vector d2 = horizDir(target.getLocation());
                        Vector r2 = rightOf(d2);
                        double dist = (t == 6) ? 4.20 : 3.30;
                        probe.teleport(target.getLocation().clone()
                                .add(d2.multiply(-dist)).add(r2.multiply(0.55)).add(0, 0.35, 0));
                    }
                }
            }.runTaskTimer(this, 1L, 1L);
            return true;
        }

        // ---------- СХЕМА 9: НАДИР-ПОЛ (фантом ПОД НОГАМИ — голова-вниз ауры) ----------
        if (sc == InjectionScheme.NADIR_FLOOR) {
            Vector dir = horizDir(target.getLocation());
            Location loc = target.getLocation().clone().add(dir.clone().multiply(1.35)).add(0, -1.15, 0);
            if (!loc.getBlock().isPassable() && !loc.getBlock().isLiquid()) {
                loc = target.getLocation().clone().add(0, -1.15, 0);
            }
            return spawnSchemeProbe(target, loc, sc, 14) != null;
        }

        // ---------- СХЕМА 10: ДВОЙНАЯ-СТЕНА (приманки за ДВУМЯ стенами: фронт + фланг) ----------
        if (sc == InjectionScheme.TWIN_WALL) {
            int spawned = 0;
            for (int i = 0; i < 2; i++) {
                float yaw = target.getLocation().getYaw() + (i == 0 ? 0f : 90f);
                Vector dir = directionFromYawPitch(yaw, 0f);
                RayTraceResult r = target.getWorld().rayTraceBlocks(target.getEyeLocation(), dir, 2.6,
                        FluidCollisionMode.NEVER, true);
                if (r != null && r.getHitBlock() != null) {
                    d.lastWallProbeMs = 0L;
                    if (injectThroughWallProbe(target, d, r.getHitBlock(), now, true)) spawned++;
                }
            }
            if (spawned == 0) {
                d.lastInjectSchemeMs = 0L;
                return runInjectionScheme(target, InjectionScheme.NADIR_FLOOR, manual, null);
            }
            return true;
        }

        // ---------- СХЕМА 11: ФАНТОМ-ОБМАН (спереди 2.3м -> телепорт за спину на 5-м тике) ----------
        if (sc == InjectionScheme.SWAP_DECOY) {
            Vector dir = horizDir(target.getLocation());
            Location front = target.getLocation().clone().add(dir.clone().multiply(2.3)).add(0, 0.4, 0);
            final ArmorStand probe = spawnSchemeProbe(target, front, sc, 18);
            if (probe == null) return false;
            final UUID pid = probe.getUniqueId();
            new BukkitRunnable() {
                int t = 0;
                @Override
                public void run() {
                    t++;
                    if (t >= 14 || !target.isOnline() || !probe.isValid() || !activeProbes.containsKey(pid)) {
                        cancel();
                        return;
                    }
                    if (t == 5) {
                        probe.teleport(target.getLocation().clone()
                                .add(horizDir(target.getLocation()).multiply(-1.7)).add(0, 0.35, 0));
                    }
                }
            }.runTaskTimer(this, 1L, 1L);
            return true;
        }

        // ---------- СХЕМА 12: НИЖНИЙ-ЛУЧ (фантом на уровне ступней, ползёт по дуге 2.9м) ----------
        if (sc == InjectionScheme.LOW_SWEEP) {
            final double radius = 2.9;
            final double yaw0 = target.getLocation().getYaw();
            Location start = target.getLocation().clone()
                    .add(directionFromYawPitch((float) yaw0, 0f).multiply(radius)).add(0, 0.12, 0);
            final ArmorStand probe = spawnSchemeProbe(target, start, sc, 26);
            if (probe == null) return false;
            final UUID pid = probe.getUniqueId();
            new BukkitRunnable() {
                int t = 0;
                double ang = yaw0;
                @Override
                public void run() {
                    t++;
                    if (t >= 22 || !target.isOnline() || !probe.isValid() || !activeProbes.containsKey(pid)) {
                        cancel();
                        return;
                    }
                    ang += 16.0;
                    Vector v = directionFromYawPitch((float) ang, 0f).multiply(radius);
                    probe.teleport(target.getLocation().clone().add(v).add(0, 0.12, 0));
                }
            }.runTaskTimer(this, 1L, 1L);
            return true;
        }

        // ---------- СХЕМА 13: КАСКАД «ПОЛНЫЙ НЕЙРО-СКАН» (прогон всех схем подряд) ----------
        if (sc == InjectionScheme.FULL_CASCADE) {
            final InjectionScheme[] order = new InjectionScheme[]{
                    InjectionScheme.BACK_360, InjectionScheme.ZENITH_CROWN, InjectionScheme.NADIR_FLOOR,
                    InjectionScheme.HITBOX_OFFSET, InjectionScheme.ORBIT_VORTEX, InjectionScheme.SWARM_QUAD,
                    InjectionScheme.BACKTRACK_PULSE, InjectionScheme.LOW_SWEEP, InjectionScheme.SWAP_DECOY,
                    InjectionScheme.THROUGH_WALL
            };
            final CommandSender notify = by;
            new BukkitRunnable() {
                int idx = 0;
                @Override
                public void run() {
                    if (idx >= order.length || !target.isOnline()) {
                        cancel();
                        if (notify != null) {
                            PlayerAcData fd = data(target);
                            notify.sendMessage(c("&#00E676✔ [KingАЧ v8.2] Каскад завершён для &#FFD740" + target.getName()
                                    + " &8│ &7Попаданий по фантомам: &#FF5252" + fd.totalInjectHits
                                    + " &8│ &7VL: &#FFD740" + fd.totalVl + "/" + autoBanVl));
                        }
                        return;
                    }
                    InjectionScheme step = order[idx];
                    idx++;
                    runInjectionScheme(target, step, true, null);
                    if (notify instanceof Player) {
                        sendActionBar((Player) notify, c("&#E040FB&l⚡ Каскад &f" + idx + "/10&8: &f" + step.displayTitle));
                    }
                }
            }.runTaskTimer(this, 0L, 24L);
            if (by != null) {
                by.sendMessage(c("&#E040FB&l⚡ [KingАЧ v8.2] Запущен КАСКАД «Полный Нейро-Скан» на &#FFD740"
                        + target.getName() + " &8(&710 схем по 1.2 сек.&8)"));
            }
            return true;
        }
        return false;
    }

    private InjectionScheme pickAutoScheme(PlayerAcData d) {
        if (d == null) return InjectionScheme.BACK_360;
        if (d.wallHitBuffer > 0 && schemeOn(InjectionScheme.THROUGH_WALL)) return InjectionScheme.THROUGH_WALL;
        if (d.airHitBuffer > 0 && schemeOn(InjectionScheme.HITBOX_OFFSET)) return InjectionScheme.HITBOX_OFFSET;
        if (d.backtrackBuffer > 0 && schemeOn(InjectionScheme.BACKTRACK_PULSE)) return InjectionScheme.BACKTRACK_PULSE;
        if (d.combatConfirmations >= 2 && schemeOn(InjectionScheme.SWARM_QUAD)) return InjectionScheme.SWARM_QUAD;
        InjectionScheme[] pool = InjectionScheme.values();
        int n = pool.length;
        int start = (int) (Math.abs(d.totalVl * 7L + System.currentTimeMillis() / 9000L) % n);
        for (int i = 0; i < n; i++) {
            InjectionScheme sc = pool[(start + i) % n];
            if (sc == InjectionScheme.FULL_CASCADE) continue;
            if (schemeOn(sc)) return sc;
        }
        return null;
    }

    private static InjectionScheme nextSchemeAfter(InjectionScheme sc) {
        if (sc == null) return InjectionScheme.BACK_360;
        InjectionScheme[] pool = InjectionScheme.values();
        for (int i = 0; i < pool.length; i++) {
            if (pool[i] == sc) {
                for (int j = 1; j < pool.length; j++) {
                    InjectionScheme nx = pool[(i + j) % pool.length];
                    if (nx != InjectionScheme.FULL_CASCADE) return nx;
                }
            }
        }
        return InjectionScheme.BACK_360;
    }

    /**
     * ТОЧНЫЙ 3D RAYCAST ХИТБОКСА (Grim/Polar-уровень):
     * Проверяет, что луч взгляда атакующего реально пересекает хитбокс жертвы (0.6×1.8×0.6)
     * с учётом пинга (история позиций жертвы до 550мс) и порядка пакетов 1.8–1.21 (интерполяция камеры).
     * Если игрок бьёт в ВОЗДУХ рядом с моделью игрока (HitBox / SilentAura / Air-Reg) — урон МГНОВЕННО отменяется!
     */
    private boolean verifyHitboxRayIntersection(Player attacker, PlayerAcData atkData, Player victim,
                                                EntityDamageByEntityEvent e, long now, int ping, double distToBox) {
        // Вплотную внутри одного блока (0.0..0.48м в углу трапки) модели пересекаются физически
        if (distToBox <= 0.48) {
            if (atkData.airHitBuffer > 0) atkData.airHitBuffer--;
            return true;
        }

        PlayerAcData vicData = data(victim);
        Vector curEye = attacker.getEyeLocation().toVector();
        Vector prevEye = atkData.prevEyeVec != null ? atkData.prevEyeVec : curEye;

        float curYaw = attacker.getLocation().getYaw();
        float curPitch = attacker.getLocation().getPitch();
        float oldYaw = atkData.prevTickYaw != 0f ? atkData.prevTickYaw : atkData.lastYaw;
        float oldPitch = atkData.prevTickPitch != 0f ? atkData.prevTickPitch : atkData.lastPitch;
        float dYaw = wrapAngle(curYaw - oldYaw);
        float dPitch = curPitch - oldPitch;

        Vector[] rayDirs = new Vector[]{
                directionFromYawPitch(curYaw, curPitch),
                directionFromYawPitch(oldYaw, oldPitch),
                directionFromYawPitch(oldYaw + dYaw * 0.50f, oldPitch + dPitch * 0.50f),
                directionFromYawPitch(oldYaw + dYaw * 0.25f, oldPitch + dPitch * 0.25f),
                directionFromYawPitch(oldYaw + dYaw * 0.75f, oldPitch + dPitch * 0.75f)
        };
        Vector[] origins = curEye.distanceSquared(prevEye) > 0.002
                ? new Vector[]{curEye, prevEye}
                : new Vector[]{curEye};

        long maxRewindMs = Math.min(560L, Math.max(150L, (long) ping + 145L));
        double minMissRecent = minRayMissForBox(origins, rayDirs, victim.getBoundingBox(), 6.3);
        double minMissWindow = minMissRecent;

        for (PlayerAcData.BoxSnapshot snap : vicData.boxHistory) {
            long age = now - snap.timeMs;
            if (age < 0L || age > maxRewindMs) continue;
            double m = minRayMissForBox(origins, rayDirs, snap.box, 6.3);
            if (m < minMissWindow) minMissWindow = m;
            if (age <= 145L && m < minMissRecent) minMissRecent = m;
            if (minMissWindow <= 0.0) break;
        }

        // Ванильный допуск хитбокса (0.10 для 1.8/ViaVersion + 0.13 субтиковая интерполяция клиента + компенсация пинга)
        double allowedMargin = 0.23 + Math.min(0.10, ping * 0.0005);

        if (minMissWindow <= allowedMargin) {
            // Проверка на Backtrack: при низком пинге (<115мс) бьёт в пустое место, где враг был 300-550мс назад!
            if (ping < 115 && minMissRecent > 0.75 && distToBox > 1.45) {
                atkData.backtrackBuffer += 2;
                if (atkData.backtrackBuffer >= 5) {
                    atkData.backtrackBuffer = 1;
                    e.setCancelled(true);
                    atkData.combatConfirmations += 2;
                    flag(attacker, "Backtrack [Задержка Хитбокса]",
                            String.format(Locale.US, "Удар по старой позиции (промах %.2f бл. при %dмс)", Double.valueOf(minMissRecent), Integer.valueOf(ping)),
                            4, true);
                    return false;
                }
            } else if (atkData.backtrackBuffer > 0) {
                atkData.backtrackBuffer--;
            }

            if (atkData.airHitBuffer > 0) atkData.airHitBuffer--;
            return true;
        }

        // ИГРОК УДАРИЛ В ВОЗДУХ МИМО МОДЕЛИ ИГРОКА -> ОТМЕНЯЕМ РЕГИСТРАЦИЮ УДАРА!
        e.setCancelled(true);
        atkData.totalAirHitsBlocked++;
        sessionAirHitsBlocked++;

        int addBuf = minMissWindow > 0.60 ? 3 : 2;
        atkData.airHitBuffer += addBuf;
        if (atkData.airHitBuffer >= 3) {
            atkData.airHitBuffer = 1;
            atkData.combatConfirmations += 2;
            int vl = minMissWindow > 0.85 ? 6 : 5;
            flag(attacker, "HitBox / SilentAura [Удар в Воздух]",
                    String.format(Locale.US, "Бил в воздух мимо игрока на %.2f бл. (не регнуло!)", Double.valueOf(minMissWindow)),
                    vl, false);
            injectInvisibleDecoyProbe(attacker, atkData, now, true);
        }
        return false;
    }

    private static double minRayMissForBox(Vector[] origins, Vector[] dirs, BoundingBox box, double maxDist) {
        double best = 999.0;
        for (int oi = 0; oi < origins.length; oi++) {
            Vector orig = origins[oi];
            if (box.contains(orig)) return 0.0;
            for (int di = 0; di < dirs.length; di++) {
                double d = rayMissDistanceToBox(orig, dirs[di], box, maxDist);
                if (d <= 0.0) return 0.0;
                if (d < best) best = d;
            }
        }
        return best;
    }

    private static double rayMissDistanceToBox(Vector origin, Vector unitDir, BoundingBox box, double maxDist) {
        if (box.rayTrace(origin, unitDir, maxDist) != null) {
            return 0.0;
        }
        // Выпуклый тернарный поиск точного минимума расстояния от луча O + t*D до 3D AABB (точность < 2 мм за 14 шагов)
        double lo = 0.0;
        double hi = maxDist;
        for (int iter = 0; iter < 14; iter++) {
            double m1 = lo + (hi - lo) / 3.0;
            double m2 = hi - (hi - lo) / 3.0;
            double d1 = distSqPointToBox(
                    origin.getX() + unitDir.getX() * m1,
                    origin.getY() + unitDir.getY() * m1,
                    origin.getZ() + unitDir.getZ() * m1, box);
            double d2 = distSqPointToBox(
                    origin.getX() + unitDir.getX() * m2,
                    origin.getY() + unitDir.getY() * m2,
                    origin.getZ() + unitDir.getZ() * m2, box);
            if (d1 < d2) {
                hi = m2;
            } else {
                lo = m1;
            }
        }
        double mid = (lo + hi) * 0.5;
        double minSq = distSqPointToBox(
                origin.getX() + unitDir.getX() * mid,
                origin.getY() + unitDir.getY() * mid,
                origin.getZ() + unitDir.getZ() * mid, box);
        return Math.sqrt(Math.max(0.0, minSq));
    }

    private static double distSqPointToBox(double px, double py, double pz, BoundingBox box) {
        double cx = Math.max(box.getMinX(), Math.min(px, box.getMaxX()));
        double cy = Math.max(box.getMinY(), Math.min(py, box.getMaxY()));
        double cz = Math.max(box.getMinZ(), Math.min(pz, box.getMaxZ()));
        double dx = px - cx;
        double dy = py - cy;
        double dz = pz - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static Vector directionFromYawPitch(float yawDeg, float pitchDeg) {
        double yawRad = Math.toRadians(yawDeg);
        double pitchRad = Math.toRadians(pitchDeg);
        double xz = Math.cos(pitchRad);
        return new Vector(-xz * Math.sin(yawRad), -Math.sin(pitchRad), xz * Math.cos(yawRad));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArmSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() == PlayerAnimationType.ARM_SWING) {
            data(e.getPlayer()).lastArmSwingMs = System.currentTimeMillis();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBowShootInspect(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        if (isExempt(p)) return;
        PlayerAcData d = data(p);
        long now = System.currentTimeMillis();
        if (e.getBow() != null && e.getBow().getType() == Material.BOW
                && d.bowDrawStartMs > 0L && e.getForce() >= 0.94f && (now - d.bowDrawStartMs) < 460L) {
            e.setCancelled(true);
            d.combatConfirmations += 2;
            flag(p, "FastBow [Пакетный Лук]", "Полный натяг лука за " + (now - d.bowDrawStartMs) + "мс", 5, false);
        }
        d.bowDrawStartMs = 0L;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerVelocityGrace(PlayerVelocityEvent e) {
        Player p = e.getPlayer();
        PlayerAcData d = data(p);
        long now = System.currentTimeMillis();
        d.lastTeleportOrGlideMs = now;
        d.airHoverTicks = 0;
        d.speedBuffer = 0;

        // v8.2: фиксируем выданное сервером отбрасывание для проверки AntiKB
        try {
            Vector v = e.getVelocity();
            if (v != null && !isExempt(p)) {
                d.lastVelocityY = v.getY();
                d.lastVelocityHoriz = Math.hypot(v.getX(), v.getZ());
                d.lastVelocityMs = now;
                d.velocityTicks = 0;
                d.velocityClimb = 0.0;
                d.velocityRun = 0.0;
            }
        } catch (Throwable ignored) {}
    }

    /**
     * v8.2: РЕЗУЛЬТАТ ГОЛОСОВАНИЯ ЛУЧЕЙ — сколько из 11 углов хитбокса жертвы
     * закрыто твёрдым блоком от глаз атакующего. Позволяет жёстко блокировать урон
     * сквозь стены/стёкла трапок, даже если читер нашёл одну «щель» в 1 пиксель.
     */
    private static final class WallVote {
        final int blocked;
        final int total;
        final Block wall;
        WallVote(int blocked, int total, Block wall) {
            this.blocked = blocked;
            this.total = total;
            this.wall = wall;
        }
    }

    private WallVote voteWallBetween(Player attacker, LivingEntity target) {
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

        Vector[] pts = new Vector[]{
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

        int blocked = 0;
        Block wall = null;
        boolean centerBlocked = false;
        for (int i = 0; i < pts.length; i++) {
            Vector delta = pts[i].clone().subtract(eye);
            double len = delta.length();
            if (len <= 0.16) return null;
            RayTraceResult rt = world.rayTraceBlocks(eyeLoc, delta.multiply(1.0 / len), len - 0.05, FluidCollisionMode.NEVER, true);
            if (rt != null && rt.getHitBlock() != null) {
                blocked++;
                if (wall == null) wall = rt.getHitBlock();
                if (i == 0) centerBlocked = true;
            }
        }
        if (!centerBlocked) return new WallVote(blocked, pts.length, null);
        return new WallVote(blocked, pts.length, wall);
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
            probeSchemes.put(probe.getUniqueId(), InjectionScheme.THROUGH_WALL);
            wallProbeIds.add(probe.getUniqueId());

            new BukkitRunnable() {
                @Override
                public void run() {
                    activeProbes.remove(probe.getUniqueId());
                    probeSchemes.remove(probe.getUniqueId());
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
        Player p = e.getPlayer();
        if ((e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)
                && e.getItem() != null && e.getItem().getType() == Material.BOW) {
            data(p).bowDrawStartMs = System.currentTimeMillis();
        }
        if (e.getAction() != Action.LEFT_CLICK_BLOCK && e.getAction() != Action.LEFT_CLICK_AIR) return;
        if (isExempt(p)) return;
        PlayerAcData d = data(p);
        long now = System.currentTimeMillis();
        RayTraceResult rt = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), 3.2, FluidCollisionMode.NEVER, true);
        if (rt == null || rt.getHitBlock() == null) return;

        // v8.2: ПРОАКТИВНАЯ ПРИМАНКА ЗА СТЕНОЙ ДЛЯ ВСЕХ ИГРОКОВ (не только под слежкой!):
        // если за простреленной стеной в 6м стоит живой игрок — KingАЧ тихо сует приманку за неё.
        boolean victimBehind = false;
        if (rt.getHitPosition() != null) {
            Vector hit = rt.getHitPosition();
            for (Player other : p.getWorld().getPlayers()) {
                if (other.getUniqueId().equals(p.getUniqueId()) || isExempt(other)) continue;
                if (other.getLocation().distanceSquared(p.getLocation()) > 49.0) continue;
                BoundingBox ob = other.getBoundingBox();
                double nx = Math.max(ob.getMinX(), Math.min(hit.getX(), ob.getMaxX()));
                double ny = Math.max(ob.getMinY(), Math.min(hit.getY(), ob.getMaxY()));
                double nz = Math.max(ob.getMinZ(), Math.min(hit.getZ(), ob.getMaxZ()));
                double dd = (nx - hit.getX()) * (nx - hit.getX()) + (ny - hit.getY()) * (ny - hit.getY()) + (nz - hit.getZ()) * (nz - hit.getZ());
                if (dd <= 4.84 && findBlockingWallBetween(p, other) != null) {
                    victimBehind = true;
                    break;
                }
            }
        }
        if (!victimBehind && !d.isUnderDeepWatch() && d.wallHitBuffer <= 0 && d.totalVl < 5
                && (now - d.lastCombatMs) > 8000L) {
            return;
        }
        injectThroughWallProbe(p, d, rt.getHitBlock(), now, false);
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

        // v8.2: FastPlace — больше 8 поставленных блоков в секунду (человек так не может!)
        if (now - d.fastPlaceWindowMs > 1000L) {
            d.fastPlaceWindowMs = now;
            d.fastPlaceCount = 0;
        }
        d.fastPlaceCount++;
        if (d.fastPlaceCount >= 9) {
            int burst = d.fastPlaceCount;
            d.fastPlaceCount = 0;
            d.combatConfirmations++;
            flag(p, "FastPlace [Спам Блоков]", "Поставлено блоков за 1 сек: " + burst, 3, false);
        }

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
        // v8.2: клики внутри собственных GUI KingАЧ не считаются за GuiMove
        if (!(e.getInventory().getHolder() instanceof KacGuiHolder)) {
            d.lastInvClickMs = System.currentTimeMillis();
        }
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
        if (m == Material.STONE || m == MAT_DEEPSLATE || m == Material.NETHERRACK || m == MAT_TUFF
                || m == Material.GRANITE || m == Material.ANDESITE || m == Material.DIORITE
                || m == Material.COAL_ORE || m == Material.IRON_ORE || m == MAT_COPPER
                || m == Material.REDSTONE_ORE || m == MAT_DEEP_REDSTONE || m == Material.LAPIS_ORE
                || m == MAT_DEEP_LAPIS || m == Material.GOLD_ORE || m == MAT_DEEP_GOLD) {
            d.stoneMined++;
            return;
        }
        boolean isRare = m == Material.DIAMOND_ORE || m == MAT_DEEP_DIAMOND
                || m == MAT_DEBRIS || m == Material.EMERALD_ORE || m == MAT_DEEP_EMERALD;
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

    /**
     * РУССКИЕ И АЛЬТЕРНАТИВНЫЕ АЛИАСЫ КОМАНД KingАЧ v8.2:
     * /ач /античит /кингач /kingach /kingac /ac /kac -> /kingac,
     * /инжект /схемы -> /kinject, /жалоба /репорт -> /report, /жалобы /репорты -> /reports.
     * Перехват идёт через PlayerCommandPreprocessEvent, поэтому работает даже если
     * в plugin.yml алиасы не подхватились ядром (Spigot/Paper 1.16.5 - 1.21.x).
     */
    private static final String[][] RU_ALIASES = new String[][]{
            {"/жалобы", "/reports"},
            {"/репорты", "/reports"},
            {"/жалоба", "/report"},
            {"/репорт", "/report"},
            {"/кингач", "/kingac"},
            {"/kingach", "/kingac"},
            {"/античит", "/kingac"},
            {"/ач", "/kingac"},
            {"/схемы", "/kinject"},
            {"/схема", "/kinject"},
            {"/инжект", "/kinject"},
            {"/инъект", "/kinject"}
    };

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onRussianCommands(PlayerCommandPreprocessEvent e) {
        String raw = e.getMessage();
        if (raw == null) return;
        String trimmed = raw.trim();
        String low = trimmed.toLowerCase(Locale.ROOT);
        for (int i = 0; i < RU_ALIASES.length; i++) {
            String alias = RU_ALIASES[i][0];
            if (!low.equals(alias) && !low.startsWith(alias + " ")) continue;
            String rest = trimmed.length() > alias.length() ? trimmed.substring(alias.length()) : "";
            e.setMessage(RU_ALIASES[i][1] + rest);
            return;
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
        logDetection(checkName, suspect.getName() + " | " + details + " | +" + addVl + " VL"
                + " (итого " + d.totalVl + "/" + autoBanVl + ") | пинг " + safePing(suspect) + "мс"
                + " | схема " + (d.lastInjectScheme != null ? d.lastInjectScheme.id : "-"));

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
        } else if (!autoBanEnabled && d.totalVl >= effectiveThreshold && !d.autoBanSkipNotified) {
            // v8.2: авто-бан выключен командой /ac AutoBanOff — бан не применяем, но стафф видит порог
            d.autoBanSkipNotified = true;
            logDetection("AUTOBAN-SKIP", suspect.getName() + ": VL " + d.totalVl + " >= " + effectiveThreshold
                    + " — бан НЕ применён (авто-бан выключен через /ac AutoBanOff)");
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (!isStaffMember(staff) || mutedAlertsStaff.contains(staff.getUniqueId())) continue;
                staff.sendMessage(c(" &#FFD740⚠ [KingАЧ] &#FF5252" + suspect.getName() + " &#FFD740VL "
                        + d.totalVl + "/" + autoBanVl + " &7— авто-бан ВЫКЛ, решайте вручную: &#FFD740/ac ban "
                        + suspect.getName()));
            }
        }
    }

    /** v8.2: включение/выключение автономного авто-бана командой, с сохранением в config.yml. */
    private void setAutoBanEnabled(boolean on, CommandSender sender) {
        if (autoBanEnabled == on) {
            sender.sendMessage(c(on ? "&#00E676✔ Авто-бан KingАЧ уже ВКЛЮЧЁН (&e" + autoBanVl + " VL&7)."
                                    : "&#FFD740⚠ Авто-бан KingАЧ уже ВЫКЛЮЧЕН."));
            return;
        }
        autoBanEnabled = on;
        getConfig().set("settings.auto-ban-enabled", Boolean.valueOf(on));
        saveConfig();
        for (PlayerAcData pd : players.values()) pd.autoBanSkipNotified = false;
        if (on) {
            sender.sendMessage(c("&#00E676&l✔ [KingАЧ v8.2] АВТО-БАН ВКЛЮЧЁН: &7порог &#FFD740" + autoBanVl
                    + " VL&7, срок &#00E5FF" + formatDuration(autoBanDurationSec)
                    + "&7. Бан снова применяется автоматически 24/7."));
        } else {
            sender.sendMessage(c("&#FFD740&l⚠ [KingАЧ v8.2] АВТО-БАН ВЫКЛЮЧЕН: &7детекты, VL и алерты продолжают"
                    + " работать, но бан — только вручную через &#FFD740/ac ban <ник>&7."));
        }
        logDetection("AUTOBAN-TOGGLE", (on ? "Авто-бан ВКЛЮЧЁН" : "Авто-бан ВЫКЛЮЧЕН") + " — команда от " + sender.getName());
        getLogger().info("[KingАЧ] Авто-бан: " + (on ? "ON" : "OFF") + " (команда от " + sender.getName() + ")");
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
        int schemesOnCount = 0;
        for (InjectionScheme sc : InjectionScheme.values()) if (schemeOn(sc)) schemesOnCount++;

        Inventory inv = Bukkit.createInventory(new KacGuiHolder("main", ""), 54,
                c("&#FF1744&l🛡 KingАЧ v8.2 — ЦЕНТР ДЕТЕКТОВ"));
        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        inv.setItem(4, makeIcon(Material.SHIELD, "&#FF1744&l🛡 KingАЧ v8.2 — Нейро-Античит 24/7",
                "&7Активные модули защиты:",
                " &#E040FB✔ 12 Схем Инжекта &8(360° / Стена x2 / Орбита / Купол / Надир / Рой x4 / Хитбокс / Отступ / Обман / Луч / Каскад)",
                " &#00E676✔ 3D Raycast Хитбокса &8(удар в воздух НЕ регает)",
                " &#00E676✔ Сенсор Микро-Дёрганий Головой &8(Sawtooth + Snap-Back)",
                " &#00E676✔ Анализ Шага Сенсы Мыши &8(GCD Smooth-Aim Bypass)",
                " &#00E676✔ Reach / HitBox / Packet-Criticals / AutoClicker / TriggerBot",
                " &#00E676✔ Timer / Blink / GuiMove / Phase / AntiKB / FastPlace / Scaffold",
                " &#00E676✔ AutoTotem / Fly / Speed / AirStrafe / Jesus / NoSlow / XRay",
                "",
                "&7Срабатываний за сессию: &#FFD740" + sessionFlags
                        + " &8│ &7попаданий по фантомам: &#E040FB" + sessionInjectHits,
                "&7Авто-банов: &#FF5252" + sessionAutoBans + " &8│ &7жалоб: &#FF4081" + activeReports.size(),
                "&7Схем инжекта включено: &#00E5FF" + schemesOnCount + "/12 &8│ &7мониторов: &f" + monitors.size()));

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

        inv.setItem(47, makeIcon(Material.END_CRYSTAL,
                "&#E040FB&l⚙ Модули 12 Схем Инжекта &8[&f" + schemesOnCount + "/12&8]",
                "&7Вкл/выкл каждой схемы инжекта",
                "&7и автоматический прогон по подозреваемым.",
                "", "&7Авто-прогон: " + (autoInjectSchemes ? "&#00E676ВКЛ" : "&#FF5252ВЫКЛ"),
                "&#80D8FF▸ Нажмите для настройки"));

        inv.setItem(52, makeIcon(Material.NETHER_STAR,
                "&#E040FB&l💉 Запустить Инжект на игроке",
                "&7Выбор цели и одной из 12 схем:",
                " &#FFD740Спина-360° / Сквозь-Стену / Двойная-Стена",
                " &#FFD740Орбита-Вихрь / Купол +85° / Надир-Пол",
                " &#FFD740Рой x4 / Байт-Хитбокс / Призрак-Отступ",
                " &#FFD740Фантом-Обман / Нижний-Луч / Каскад",
                "", "&#00E676▸ Нажмите для выбора цели"));

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

        inv.setItem(51, makeIcon(rayHitboxEnabled ? Material.SPECTRAL_ARROW : Material.GRAY_DYE,
                "&#FF1744&l🎯 Анти-Хитбокс (Удар в Игрока): " + (rayHitboxEnabled ? "&#00E676&lВКЛЮЧЁН" : "&#FF5252&lВЫКЛЮЧЕН"),
                "&73D Raycast проверяет, что прицел смотрит в модель игрока!",
                "&7Если читер бьёт в воздух (HitBox / SilentAura) —",
                "&7удар НЕ регает + срабатывает детект KingАЧ!",
                "&7Заблокировано ударов в воздух: &#FFD740" + sessionAirHitsBlocked,
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
                    "&7Дата бана: &7" + stampBan(b.createdAtMs),
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

        // 1B. v8.2: выбор цели для схем инжекта
        if ("inject_targets".equals(h.menuType)) {
            if (slot == 49) { openMainControlGui(p); return; }
            ItemStack clicked = e.getCurrentItem();
            String nick = extractSkullTarget(clicked);
            if (nick != null) {
                openSchemePickerGui(p, nick);
            }
            return;
        }

        // 1C. v8.2: выбор одной из 8 схем инжекта для конкретного игрока
        if ("inject_scheme".equals(h.menuType)) {
            if (slot == 40) { openInjectTargetsGui(p); return; }
            Player target = Bukkit.getPlayerExact(h.targetName);
            if (target == null) {
                p.sendMessage(c("&#FF5252✖ Игрок &f" + h.targetName + " &#FF5252уже не в сети!"));
                p.closeInventory();
                return;
            }
            if (slot == 31) {
                InjectionScheme auto = pickAutoScheme(data(target));
                if (auto == null) {
                    p.sendMessage(c("&#FFD740⚠ Все схемы инжекта выключены в конфиге!"));
                    return;
                }
                boolean ok = runInjectionScheme(target, auto, true, p);
                p.sendMessage(c((ok ? "&#00E676" : "&#FFD740") + "🤖 Авто-подбор: &#FFD740«" + auto.displayTitle
                        + "» " + (ok ? "&#00E676запущена" : "&#FFD740не удалась") + " &8(&f" + target.getName() + "&8)"));
                return;
            }
            InjectionScheme[] all = InjectionScheme.values();
            int idx = schemeSlotIndex(slot);
            if (idx < 0 || idx >= all.length) return;
            InjectionScheme sc = all[idx];
            if (e.getClick() == ClickType.SHIFT_LEFT || e.getClick() == ClickType.SHIFT_RIGHT) {
                if (!isAdminController(p)) {
                    p.sendMessage(c("&#FF1744✖ Переключать модули схем может только ст. администрация / OWNER!"));
                    return;
                }
                boolean nowOn = !schemeOn(sc);
                schemeEnabled.put(sc, Boolean.valueOf(nowOn));
                persistSettingsToConfig();
                playSound(p, "ui.button.click", 0.9f, 1.25f);
                logDetection("SCHEME-TOGGLE", sc.id + " -> " + (nowOn ? "ON" : "OFF") + " (" + p.getName() + ")");
                openSchemePickerGui(p, target.getName());
                return;
            }
            boolean ok = runInjectionScheme(target, sc, true, p);
            p.sendMessage(c((ok ? "&#00E676" : "&#FFD740") + "💉 Схема &#FFD740«" + sc.displayTitle
                    + "» " + (ok ? "&#00E676запущена на &f" + target.getName() : "&#FFD740не сработала") + "&8!"));
            if (sc == InjectionScheme.FULL_CASCADE) {
                p.closeInventory();
            } else {
                openSchemePickerGui(p, target.getName());
            }
            return;
        }

        // 1D. v8.2: глобальные модули схем инжекта
        if ("schemes_config".equals(h.menuType)) {
            if (slot == 40) { openMainControlGui(p); return; }
            if (slot == 31) { openInjectTargetsGui(p); return; }
            if (!isAdminController(p)) {
                p.sendMessage(c("&#FF1744✖ Управление модулями схем доступно только ст. администрации / OWNER!"));
                return;
            }
            if (slot == 4) {
                autoInjectSchemes = !autoInjectSchemes;
                persistSettingsToConfig();
                playSound(p, "ui.button.click", 0.9f, 1.25f);
                openSchemesConfigGui(p);
                return;
            }
            InjectionScheme[] all = InjectionScheme.values();
            int idx = schemeSlotIndex(slot);
            if (idx < 0 || idx >= all.length) return;
            InjectionScheme sc = all[idx];
            boolean nowOn = !schemeOn(sc);
            schemeEnabled.put(sc, Boolean.valueOf(nowOn));
            persistSettingsToConfig();
            playSound(p, "ui.button.click", 0.9f, 1.25f);
            logDetection("SCHEME-TOGGLE", sc.id + " -> " + (nowOn ? "ON" : "OFF") + " (" + p.getName() + ")");
            openSchemesConfigGui(p);
            return;
        }

        // 2. Главное меню /kingac
        if ("main".equals(h.menuType)) {
            if (slot == 45) { openReportsGui(p); return; }
            if (slot == 46) { openBansGui(p); return; }
            if (slot == 47) { openSchemesConfigGui(p); return; }
            if (slot == 52) { openInjectTargetsGui(p); return; }
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
            if (slot == 51) {
                if (!isAdminController(p)) {
                    p.sendMessage(c("&#FF1744✖ Переключать глобальные модули KingАЧ может только ст. администрация / OWNER!"));
                    return;
                }
                rayHitboxEnabled = !rayHitboxEnabled;
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
    //  6B. v8.2 — СХЕМЫ ИНЖЕКТА: GUI + ЖИВОЙ МОНИТОР-HUD + ТОП + СПИСОК ПРОВЕРОК
    // =================================================================================
    private static void sendActionBar(Player p, String text) {
        if (p == null || !p.isOnline()) return;
        try {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text));
        } catch (Throwable ignored) {}
    }

    private static Material schemeIcon(InjectionScheme sc) {
        return safeMat(sc.iconMaterialName, "SHIELD", "IRON_SWORD", "STONE");
    }

    /** v8.2: номер схемы (0..11) по кликнутому слоту GUI, либо -1. */
    private static int schemeSlotIndex(int slot) {
        for (int i = 0; i < SCHEME_SLOTS.length; i++) {
            if (SCHEME_SLOTS[i] == slot) return i;
        }
        return -1;
    }

    private void sendMonitorHud(Player staff, String targetName) {
        if (staff == null || !staff.isOnline()) return;
        Player t = Bukkit.getPlayerExact(targetName);
        if (t == null || !t.isOnline()) {
            sendActionBar(staff, c("&#FF5252⚠ Монитор: &f" + targetName + " &#FF5252не в сети"));
            monitors.remove(staff.getUniqueId());
            return;
        }
        PlayerAcData d = data(t);
        String bar = "&#FF1744&l🛡 &f" + t.getName()
                + " &#FFD740VL " + d.totalVl + "/" + autoBanVl
                + " &8│ &7CPS &f" + d.currentCps
                + " &8│ &7Пинг &f" + safePing(t)
                + " &8│ &7Фант. &f" + d.totalInjectHits
                + " &8│ &7Схема &f" + d.activeSchemeLabel;
        if (d.totalVl > 0 && !"Чисто".equals(d.lastCheat)) {
            String last = d.lastCheat;
            if (last.length() > 30) last = last.substring(0, 30) + "…";
            bar = bar + " &#FF5252▸ " + last;
        }
        sendActionBar(staff, c(bar));
    }

    private void openInjectTargetsGui(Player viewer) {
        Inventory inv = Bukkit.createInventory(new KacGuiHolder("inject_targets", ""), 54,
                c("&#E040FB&l💉 KingАЧ v8.2 — ВЫБОР ЦЕЛИ ИНЖЕКТА"));
        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        int on = 0;
        int[] slots = new int[]{
                10,11,12,13,14,15,16,
                19,20,21,22,23,24,25,
                28,29,30,31,32,33,34
        };
        List<Player> list = new ArrayList<Player>(Bukkit.getOnlinePlayers());
        Collections.sort(list, new Comparator<Player>() {
            @Override
            public int compare(Player a, Player b) {
                return Integer.compare(data(b).totalVl, data(a).totalVl);
            }
        });
        for (Player pl : list) {
            if (on >= slots.length) break;
            if (isExempt(pl)) continue;
            PlayerAcData d = data(pl);
            List<String> lore = new ArrayList<String>();
            lore.add("&7Нарушений: &#FFD740" + d.totalVl + "/" + autoBanVl + " VL");
            lore.add("&7Последняя схема: &#00E5FF" + d.activeSchemeLabel);
            lore.add("&7Попаданий по фантомам: &#FF5252" + d.totalInjectHits);
            lore.add("&7Последний детект: &f" + d.lastCheat);
            lore.add("&7Пинг: &a" + safePing(pl) + " мс &8│ &7CPS: &e" + d.currentCps);
            lore.add("");
            lore.add("&#E040FB▸ Нажмите, чтобы выбрать 1 из 12 схем инжекта");
            inv.setItem(slots[on++], makeSkull(pl, "&#FF5252💉 " + pl.getName() + " &8[&eVL: " + d.totalVl + "&8]", lore));
        }
        if (on == 0) {
            inv.setItem(22, makeIcon(Material.EMERALD_BLOCK, "&#00E676&l✔ Нет подходящих целей",
                    "&7Все игроки в креативе/спектре или под иммунитетом OWNER."));
        }
        inv.setItem(4, makeIcon(Material.END_CRYSTAL, "&#E040FB&l💉 12 Схем Инжекта KingАЧ v8.2",
                "&7Схемы спавнят невидимых микро-фантомов туда,",
                "&7куда честный клиент не может отправить атаку:",
                " &#FFD7401. &fСпина-360° &8(Rage/KillAura)",
                " &#FFD7402. &fСквозь-Стену &8(WallHit/Трапка)",
                " &#FFD7403. &fОрбита-Вихрь &8(AimLock/Track)",
                " &#FFD7404. &fВерхний Купол +85° &8(SilentAura)",
                " &#FFD7405. &fМульти-Рой x4 &8(MultiAura)",
                " &#FFD7406. &fБайт-Хитбокс &8(HitBoxes/AirHit)",
                " &#FFD7407. &fПризрак-Отступ &8(Backtrack/Reach)",
                " &#FFD7408. &fНадир-Пол &8(Голова-Вниз Ауры)",
                " &#FFD7409. &fДвойная-Стена &8(WallHit x2)",
                " &#FFD74010. &fФантом-Обман &8(Switch/Trigger)",
                " &#FFD74011. &fНижний-Луч &8(Low-Aim Ауры)",
                " &#FFD74012. &fКаскад «Полный Нейро-Скан»"));
        inv.setItem(49, makeIcon(Material.ARROW, "&7« Назад в центр KingАЧ"));
        viewer.openInventory(inv);
    }

    private void openSchemePickerGui(Player viewer, String targetName) {
        Inventory inv = Bukkit.createInventory(new KacGuiHolder("inject_scheme", targetName), 45,
                c("&#E040FB&l💉 СХЕМЫ ИНЖЕКТА → " + targetName));
        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        InjectionScheme[] all = InjectionScheme.values();
        for (int i = 0; i < all.length && i < SCHEME_SLOTS.length; i++) {
            InjectionScheme sc = all[i];
            boolean on = schemeOn(sc);
            List<String> lore = new ArrayList<String>();
            lore.add("&7" + sc.descLine1);
            lore.add("&7" + sc.descLine2);
            lore.add("");
            lore.add("&7Базовый вес детекта: &#FFD740" + sc.baseVl + " VL");
            lore.add("&7Статус модуля: " + (on ? "&#00E676✔ Включён" : "&#FF5252✖ Выключен"));
            lore.add("&7ID схемы: &#00E5FF" + sc.id);
            lore.add("");
            lore.add("&#E040FB▸ ЛКМ — Запустить схему на " + targetName);
            lore.add("&#FFD740▸ Shift+ЛКМ — Вкл/выкл модуль глобально");
            inv.setItem(SCHEME_SLOTS[i], makeIcon(schemeIcon(sc),
                    (on ? "&#00E676" : "&#FF5252") + "&l" + (i + 1) + ". " + sc.displayTitle,
                    lore.toArray(new String[0])));
        }

        inv.setItem(4, makeSkull(Bukkit.getOfflinePlayer(targetName),
                "&#FFD740&lЦель: &f" + targetName,
                Arrays.asList("&7Выберите схему инжекта ниже.", "&7Фантомы невидимы и живут 0.5-1.5 сек.")));
        inv.setItem(31, makeIcon(Material.COMPARATOR, "&#00E5FF&l🤖 Авто-подбор схемы",
                "&7KingАЧ сам выберет схему по буферам",
                "&7нарушений игрока (Wall/HitBox/Reach).", "", "&#00E676▸ Нажмите для запуска"));
        inv.setItem(40, makeIcon(Material.ARROW, "&7« Назад к списку целей"));
        viewer.openInventory(inv);
    }

    private void openSchemesConfigGui(Player viewer) {
        Inventory inv = Bukkit.createInventory(new KacGuiHolder("schemes_config", ""), 45,
                c("&#E040FB&l⚙ KingАЧ v8.2 — МОДУЛИ СХЕМ ИНЖЕКТА"));
        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);
        InjectionScheme[] all = InjectionScheme.values();
        for (int i = 0; i < all.length && i < SCHEME_SLOTS.length; i++) {
            InjectionScheme sc = all[i];
            boolean on = schemeOn(sc);
            inv.setItem(SCHEME_SLOTS[i], makeIcon(on ? schemeIcon(sc) : Material.GRAY_DYE,
                    (on ? "&#00E676" : "&#FF5252") + "&l" + sc.displayTitle + " — " + (on ? "ВКЛ" : "ВЫКЛ"),
                    "&7" + sc.descLine1, "&7" + sc.descLine2, "",
                    "&7ID: &#00E5FF" + sc.id + " &8│ &7Вес: &#FFD740" + sc.baseVl + " VL",
                    "&#80D8FF▸ Нажмите для переключения"));
        }
        inv.setItem(4, makeIcon(autoInjectSchemes ? Material.LIME_DYE : Material.GRAY_DYE,
                "&#FFD740&l🤖 Авто-прогон схем: " + (autoInjectSchemes ? "&#00E676&lВКЛ" : "&#FF5252&lВЫКЛ"),
                "&7Каждые 3 сек. KingАЧ сам инжектит фантомов",
                "&7в игроков под жалобой / Deep Watch / VL>4.",
                "", "&#80D8FF▸ Нажмите для переключения"));
        inv.setItem(31, makeIcon(Material.END_CRYSTAL, "&#E040FB&l💉 Запустить инжект на игроке",
                "&7Открыть выбор цели и схемы вручную.", "", "&#00E676▸ Нажмите"));
        inv.setItem(40, makeIcon(Material.ARROW, "&7« Назад в центр KingАЧ"));
        viewer.openInventory(inv);
    }

    private void showTopSuspects(CommandSender s) {
        List<Player> list = new ArrayList<Player>();
        for (Player on : Bukkit.getOnlinePlayers()) {
            PlayerAcData d = players.get(on.getUniqueId());
            if (d != null && d.totalVl > 0) list.add(on);
        }
        Collections.sort(list, new Comparator<Player>() {
            @Override
            public int compare(Player a, Player b) {
                return Integer.compare(data(b).totalVl, data(a).totalVl);
            }
        });
        s.sendMessage(c("&#FF1744&l🛡 [KingАЧ v8.2] ТОП подозреваемых (по VL):"));
        if (list.isEmpty()) {
            s.sendMessage(c(" &#00E676✔ Нарушителей нет — все игроки чисты."));
            return;
        }
        int pos = 0;
        for (Player p : list) {
            if (pos >= 10) break;
            pos++;
            PlayerAcData d = data(p);
            s.sendMessage(c(" &#FFD740" + pos + ". &f" + p.getName()
                    + " &8— &eVL " + d.totalVl + "/" + autoBanVl
                    + " &8│ &7фантомов: &#FF5252" + d.totalInjectHits
                    + " &8│ &7CPS: &e" + d.currentCps
                    + " &8│ &7пинг: &a" + safePing(p)
                    + " &8│ &f" + d.lastCheat));
        }
    }

    private void showChecksList(CommandSender s) {
        s.sendMessage(c("&#FF1744&l🛡 [KingАЧ v8.2] Активные проверки и схемы инжекта:"));
        s.sendMessage(c(" &#00E676▸ &fБой: &7KillAura (360°/Мульти/В GUI/Щит-Еда/Без взмаха), Reach, HitBoxes, SilentAura, Backtrack, WallAura, TriggerBot, AutoClicker (CPS+Макрос+Куртозис), Criticals (Пакет-Крит/Микро-Хоп), KeepSprint, AimBot, Aim (Микро-дёргание/GCD/Горизонт-Лок)"));
        s.sendMessage(c(" &#00E5FF▸ &fДвижение: &7Fly, AirJump, Speed/Bhop, AirStrafe, Jesus, NoSlow, Phase, AntiKB (Вертикаль), Timer/Blink (пакетный баланс), Step"));
        s.sendMessage(c(" &#FFD740▸ &fМир/Инвентарь: &7Scaffold, FastPlace, GuiMove (ИнвХод), AutoTotem, XRay (Скрытые жилы), AutoArmor"));
        s.sendMessage(c(" &#E040FB▸ &f12 схем инжекта: &7Спина-360°, Сквозь-Стену, Орбита, Купол +85°, Рой x4, Байт-Хитбокс, Отступ, Надир-Пол, Двойная-Стена, Обман, Нижний-Луч, Каскад"));
        int active = 0;
        for (InjectionScheme sc : InjectionScheme.values()) {
            if (schemeOn(sc)) active++;
        }
        s.sendMessage(c(" &#80D8FF▸ &fСтатус: &7схем включено &#FFD740" + active + "/12&7, авто-прогон "
                + (autoInjectSchemes ? "&#00E676ВКЛ" : "&#FF5252ВЫКЛ")
                + "&7, авто-бан " + (autoBanEnabled ? "&#00E676ВКЛ" : "&#FF5252ВЫКЛ")
                + " &8│ &7порог &e" + autoBanVl + " VL"));
    }

    // =================================================================================
    //  7. COMMANDS & TAB COMPLETION (/kingac, /report, /reports, /kinject)
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

        // -------- /kinject (/inject) — 8 СХЕМ ИНЖЕКТА v8.2 --------
        if ("kinject".equals(name)) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("[KingАЧ v8.2] Использование: kinject <ник> <back|wall|orbit|zenith|swarm|hitbox|backtrack|cascade|auto|all>");
                return true;
            }
            Player p = (Player) sender;
            if (!isStaffMember(p)) {
                p.sendMessage(c("&#FF1744&l🛡 [KingАЧ v8.2] &fНейро-античит активен 24/7 (8 схем инжекта + 3D Raycast Хитбокса)! Пожаловаться: &#FFD740/report <ник>"));
                return true;
            }
            if (args.length == 0) {
                openInjectTargetsGui(p);
                return true;
            }
            String first = args[0].toLowerCase(Locale.ROOT);
            if ("list".equals(first) || "список".equals(first) || "help".equals(first)) {
                showChecksList(p);
                return true;
            }
            if ("config".equals(first) || "modules".equals(first) || "настройки".equals(first)) {
                if (!isAdminController(p)) {
                    p.sendMessage(c("&#FF1744✖ Управление модулями схем доступно только ст. администрации / OWNER!"));
                    return true;
                }
                openSchemesConfigGui(p);
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                p.sendMessage(c("&#FF5252✖ Игрок &f" + args[0] + " &#FF5252не в сети!"));
                return true;
            }
            if (args.length == 1) {
                openSchemePickerGui(p, target.getName());
                return true;
            }
            String schemeArg = args[1].toLowerCase(Locale.ROOT);
            if ("all".equals(schemeArg) || "cascade".equals(schemeArg) || "full".equals(schemeArg)
                    || "scan".equals(schemeArg) || "каскад".equals(schemeArg) || "все".equals(schemeArg)) {
                runInjectionScheme(target, InjectionScheme.FULL_CASCADE, true, p);
                return true;
            }
            if ("auto".equals(schemeArg) || "авто".equals(schemeArg)) {
                InjectionScheme auto = pickAutoScheme(data(target));
                if (auto == null) {
                    p.sendMessage(c("&#FFD740⚠ Все схемы инжекта выключены в конфиге KingАЧ."));
                    return true;
                }
                boolean ok = runInjectionScheme(target, auto, true, p);
                p.sendMessage(c((ok ? "&#00E676" : "&#FFD740") + "🛡 Авто-подбор схемы: &#FFD740" + auto.displayTitle
                        + (ok ? " &#00E676запущена" : " &#FFD740не удалась") + " &8(&7на &f" + target.getName() + "&8)"));
                return true;
            }
            InjectionScheme sc = InjectionScheme.fromId(schemeArg);
            if (sc == null) {
                p.sendMessage(c("&#FF5252✖ Неизвестная схема: &f" + args[1]));
                p.sendMessage(c(" &7Доступно: &#00E5FFback wall orbit zenith swarm hitbox backtrack cascade auto all"));
                return true;
            }
            boolean ok = runInjectionScheme(target, sc, true, p);
            p.sendMessage(c((ok ? "&#00E676" : "&#FFD740") + "💉 [KingАЧ v8.2] Схема &#FFD740«" + sc.displayTitle
                    + "» " + (ok ? "&#00E676запущена" : "&#FFD740не сработала") + " &8(&7цель: &f" + target.getName() + "&8)"));
            if (ok) {
                p.sendMessage(c(" &7Если игрок заденет невидимого фантома — детект придёт в чат, а VL улетит в авто-бан."));
            }
            return true;
        }

        // -------- /kingac (/ac, /kac, /kas, /ач, /античит) --------
        if ((sender instanceof Player) && !isStaffMember((Player) sender)) {
            Player pl = (Player) sender;
            pl.sendMessage(c("&#FF1744&l🛡 [KingАЧ v8.2] &fНейро-античит активен 24/7!"));
            pl.sendMessage(c(" &#00E676▸ &f12 схем инжекта &8(360°, Стена x2, Орбита, Купол, Надир, Рой x4, Хитбокс, Отступ, Обман, Луч, Каскад)"));
            pl.sendMessage(c(" &#00E676▸ &f3D Raycast Хитбокса: удары в воздух и сквозь стены НЕ региструются"));
            pl.sendMessage(c(" &#00E676▸ &fПожаловаться на читера: &#FFD740/report <ник> &7или &#FFD740/жалоба <ник>"));
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player) {
                openMainControlGui((Player) sender);
            } else {
                sender.sendMessage("[KingАЧ v8.2] Флагов: " + sessionFlags + " | Ударов в воздух отменено: " + sessionAirHitsBlocked
                        + " | Попаданий по фантомам инжекта: " + sessionInjectHits + " | Авто-банов: " + sessionAutoBans
                        + " | Жалоб: " + activeReports.size());
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

        // -------- v8.2: /ac inject <ник> [схема] --------
        if ("inject".equals(sub) || "inj".equals(sub) || "инжект".equals(sub) || "схема".equals(sub) || "схемы".equals(sub)) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("[KingАЧ v8.2] Схемы инжекта доступны только игрокам (GUI).");
                return true;
            }
            Player p = (Player) sender;
            if (args.length == 1) {
                openInjectTargetsGui(p);
                return true;
            }
            StringBuilder rest = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                if (i > 1) rest.append(' ');
                rest.append(args[i]);
            }
            p.performCommand("kinject " + rest.toString());
            return true;
        }

        // -------- v8.2: /ac schemes [config] — модули 8 схем --------
        if ("schemes".equals(sub) || "scheme".equals(sub) || "modules".equals(sub)) {
            if (!(sender instanceof Player)) {
                int on = 0;
                for (InjectionScheme sc : InjectionScheme.values()) if (schemeOn(sc)) on++;
                sender.sendMessage("[KingАЧ v8.2] Схем инжекта включено: " + on + "/12 | авто-прогон: " + autoInjectSchemes);
                return true;
            }
            Player p = (Player) sender;
            if (args.length >= 2 && ("config".equalsIgnoreCase(args[1]) || "toggle".equalsIgnoreCase(args[1]))) {
                if (!isAdminController(p)) {
                    p.sendMessage(c("&#FF1744✖ Управление модулями схем доступно только ст. администрации / OWNER!"));
                    return true;
                }
                openSchemesConfigGui(p);
                return true;
            }
            openSchemesConfigGui(p);
            return true;
        }

        // -------- v8.2: /ac monitor <ник> — живой ActionBar-HUD --------
        if ("monitor".equals(sub) || "hud".equals(sub) || "монитор".equals(sub)) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("[KingАЧ v8.2] Монитор доступен только игрокам.");
                return true;
            }
            Player p = (Player) sender;
            if (args.length < 2) {
                if (monitors.remove(p.getUniqueId()) != null) {
                    p.sendMessage(c("&#FFD740🛡 Живой монитор KingАЧ ВЫКЛЮЧЕН."));
                    sendActionBar(p, c("&#808080Монитор KingАЧ выключен"));
                } else {
                    p.sendMessage(c("&#FF1744Использование: &#FFD740/ac monitor <ник> &7(повторно — выключить)"));
                }
                return true;
            }
            if ("off".equalsIgnoreCase(args[1]) || "stop".equalsIgnoreCase(args[1])) {
                monitors.remove(p.getUniqueId());
                p.sendMessage(c("&#FFD740🛡 Живой монитор KingАЧ ВЫКЛЮЧЕН."));
                return true;
            }
            Player t = Bukkit.getPlayerExact(args[1]);
            if (t == null) {
                p.sendMessage(c("&#FF5252✖ Игрок &f" + args[1] + " &#FF5252не в сети!"));
                return true;
            }
            monitors.put(p.getUniqueId(), t.getName());
            sendMonitorHud(p, t.getName());
            p.sendMessage(c("&#00E676🛡 Живой монитор KingАЧ ВКЛЮЧЁН на &#FFD740" + t.getName()
                    + " &8(&7VL, CPS, пинг, схема и детекты — в ActionBar каждые 0.5 сек.&8)"));
            return true;
        }

        // -------- v8.2: /ac top — топ подозреваемых --------
        if ("top".equals(sub) || "топ".equals(sub)) {
            showTopSuspects(sender);
            return true;
        }

        // -------- v8.2: /ac checks — список всех проверок --------
        if ("checks".equals(sub) || "list".equals(sub) || "проверки".equals(sub)) {
            showChecksList(sender);
            return true;
        }

        // -------- v8.2: /ac log — последние записи detections.log --------
        if ("log".equals(sub) || "logs".equals(sub)) {
            List<String> tail;
            synchronized (logTail) {
                tail = new ArrayList<String>(logTail);
            }
            int from = Math.max(0, tail.size() - 12);
            sender.sendMessage(c("&#FF1744&l🛡 [KingАЧ v8.2] Последние детекты (plugins/KingAC/detections.log):"));
            if (tail.isEmpty()) {
                sender.sendMessage(c(" &#00E676✔ Записей пока нет."));
            }
            for (int i = from; i < tail.size(); i++) {
                sender.sendMessage(c(" &8▸ &f" + tail.get(i)));
            }
            return true;
        }

        // -------- v8.2: /ac status --------
        if ("status".equals(sub) || "stats".equals(sub) || "статус".equals(sub)) {
            int schemesOn = 0;
            for (InjectionScheme sc : InjectionScheme.values()) if (schemeOn(sc)) schemesOn++;
            sender.sendMessage(c("&#FF1744&l🛡 [KingАЧ v8.2] Статус нейро-античита:"));
            sender.sendMessage(c(" &#00E676▸ &fЯдро сервера: &7" + Bukkit.getVersion()));
            sender.sendMessage(c(" &#00E676▸ &fСхем инжекта: &#FFD740" + schemesOn + "/12 &8│ &7авто-прогон: "
                    + (autoInjectSchemes ? "&#00E676ВКЛ" : "&#FF5252ВЫКЛ")));
            sender.sendMessage(c(" &#00E676▸ &fДетектов за сессию: &#FFD740" + sessionFlags
                    + " &8│ &7фантомов создано: &#00E5FF" + sessionInjectProbes
                    + " &8│ &7попаданий по ним: &#FF5252" + sessionInjectHits));
            sender.sendMessage(c(" &#00E676▸ &fУдаров в воздух отменено: &#FFD740" + sessionAirHitsBlocked
                    + " &8│ &7авто-банов: &#FF5252" + sessionAutoBans
                    + " &8│ &7жалоб: &#FF4081" + activeReports.size()));
            sender.sendMessage(c(" &#00E676▸ &fМониторов состава: &7" + monitors.size()
                    + " &8│ &7авто-бан: " + (autoBanEnabled ? "&#00E676ВКЛ (&e" + autoBanVl + " VL&7)" : "&#FF5252ВЫКЛ")));
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
            sender.sendMessage(c("&#FF1744&l🛡 Досье KingАЧ v8.2 на &#FFD740" + t.getName() + "&f:"));
            sender.sendMessage(c(" &7Уровень нарушений: &e" + d.totalVl + "/" + autoBanVl + " VL &8│ &7Пинг: &a" + safePing(t) + " мс &8│ &7CPS: &e" + d.currentCps));
            sender.sendMessage(c(" &7Ударов в воздух отменено (Air-Hit): &#FF5252" + d.totalAirHitsBlocked
                    + " &8│ &7Индекс микро-дёрганий: &#00E5FF" + d.maxMicroTwitchScore + "/15"));
            sender.sendMessage(c(" &#E040FB &7Инжект-схем сработало: &#FFD740" + d.totalInjectHits
                    + " &8│ &7Последняя схема: &#00E5FF" + d.activeSchemeLabel));
            sender.sendMessage(c(" &7Жалоб: &#FF4081" + d.reporters.size() + " &8│ &7Последний детект: &f" + d.lastCheat));
            if (!d.vlByCheck.isEmpty()) {
                sender.sendMessage(c(" &7По проверкам: &f" + vlSummary(d)));
            }
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

        if ("probe".equals(sub) || "wallprobe".equals(sub) || "inj".equals(sub)) {
            if (args.length < 2) {
                if (sender instanceof Player) {
                    openInjectTargetsGui((Player) sender);
                } else {
                    sender.sendMessage(c("&cИспользование: /ac probe <ник> [схема]"));
                }
                return true;
            }
            Player t = Bukkit.getPlayerExact(args[1]);
            if (t == null) {
                sender.sendMessage(c("&#FF5252✖ Игрок не в сети."));
                return true;
            }
            if (args.length >= 3) {
                InjectionScheme sc = InjectionScheme.fromId(args[2]);
                if (sc == null) {
                    sender.sendMessage(c("&#FF5252✖ Неизвестная схема: &f" + args[2]
                            + " &8(&7back wall orbit zenith swarm hitbox backtrack cascade auto&8)"));
                    return true;
                }
                if (sc == InjectionScheme.FULL_CASCADE || "cascade".equalsIgnoreCase(args[2])) {
                    runInjectionScheme(t, InjectionScheme.FULL_CASCADE, true, sender);
                } else {
                    boolean ok = runInjectionScheme(t, sc, true, sender);
                    sender.sendMessage(c((ok ? "&#00E676" : "&#FFD740") + "💉 Схема &#FFD740«" + sc.displayTitle
                            + "» " + (ok ? "&#00E676запущена" : "&#FFD740не сработала") + " &8(&f" + t.getName() + "&8)"));
                }
                return true;
            }
            PlayerAcData d = data(t);
            long now = System.currentTimeMillis();
            d.lastProbeMs = 0L;
            d.lastWallProbeMs = 0L;
            d.lastInjectSchemeMs = 0L;
            injectInvisibleDecoyProbe(t, d, now, true);
            boolean wallSpawned = false;
            RayTraceResult rt = t.getWorld().rayTraceBlocks(t.getEyeLocation(), t.getEyeLocation().getDirection(), 2.4, FluidCollisionMode.NEVER, true);
            if (rt != null && rt.getHitBlock() != null) {
                wallSpawned = injectThroughWallProbe(t, d, rt.getHitBlock(), now, true);
            }
            sender.sendMessage(c("&#00E676🛡 [KingАЧ v8.2] Инжект-проверка запущена на &#FFD740" + t.getName()
                    + " &8(Спина-360°" + (wallSpawned ? " + &#00E5FFСквозь-Стену" : "") + "&8)!"));
            sender.sendMessage(c(" &7Все 8 схем: &#FFD740/ac inject " + t.getName() + " cascade"));
            return true;
        }

        if ("reload".equals(sub) && isAdminController(sender)) {
            reloadSettingsFromConfig();
            int schemesOn = 0;
            for (InjectionScheme sc : InjectionScheme.values()) if (schemeOn(sc)) schemesOn++;
            sender.sendMessage(c("&#00E676✔ Конфигурация KingАЧ v8.2 перезагружена! &7Схем инжекта: &#FFD740" + schemesOn + "/12"));
            logDetection("RELOAD", "Конфиг перезагружен (схем вкл: " + schemesOn + "/8, авто-бан: " + autoBanEnabled + ")");
            return true;
        }

        // -------- v8.2: /ac AutoBanOff | /ac AutoBanOn | /ac autoban [on|off] --------
        if ("autobanoff".equals(sub) || "autoban-off".equals(sub) || "автобанoff".equals(sub) || "автобанвыкл".equals(sub)
                || ("autoban".equals(sub) && args.length >= 2
                    && ("off".equalsIgnoreCase(args[1]) || "disable".equalsIgnoreCase(args[1]) || "выкл".equalsIgnoreCase(args[1])))) {
            if (!isAdminController(sender)) {
                sender.sendMessage(c("&#FF1744✖ Управление авто-баном доступно только ст. администрации / OWNER!"));
                return true;
            }
            setAutoBanEnabled(false, sender);
            return true;
        }
        if ("autobanon".equals(sub) || "autoban-on".equals(sub) || "автобанon".equals(sub) || "автобанвкл".equals(sub)
                || ("autoban".equals(sub) && args.length >= 2
                    && ("on".equalsIgnoreCase(args[1]) || "enable".equalsIgnoreCase(args[1]) || "вкл".equalsIgnoreCase(args[1])))) {
            if (!isAdminController(sender)) {
                sender.sendMessage(c("&#FF1744✖ Управление авто-баном доступно только ст. администрации / OWNER!"));
                return true;
            }
            setAutoBanEnabled(true, sender);
            return true;
        }
        if ("autoban".equals(sub) || "автобан".equals(sub)) {
            sender.sendMessage(c("&#FF1744&l🛡 [KingАЧ v8.2] Авто-бан: " + (autoBanEnabled ? "&#00E676ВКЛ" : "&#FF5252ВЫКЛ")
                    + " &7| порог &#FFD740" + autoBanVl + " VL &7| срок &#00E5FF" + formatDuration(autoBanDurationSec)
                    + " &7| банов за сессию: &#FF5252" + sessionAutoBans));
            sender.sendMessage(c(" &#808080Включить/выключить: &#FFD740/ac AutoBanOn &8/ &#FFD740/ac AutoBanOff"));
            return true;
        }

        sender.sendMessage(c("&#FF1744&l🛡 Команды KingАЧ v8.2 &8(&fнейро-античит + 8 схем инжекта&8):"));
        sender.sendMessage(c(" &#00E676/ac &8— &fЦентр детектов, подозреваемые и настройки"));
        sender.sendMessage(c(" &#E040FB/ac inject &8— &fGUI выбора цели для инжект-схем"));
        sender.sendMessage(c(" &#E040FB/ac inject <ник> <back|wall|orbit|zenith|swarm|hitbox|backtrack|cascade|auto> &8— &fзапуск схемы"));
        sender.sendMessage(c(" &#00E5FF/ac schemes &8— &fМодули 8 схем инжекта (вкл/выкл)"));
        sender.sendMessage(c(" &#FFD740/ac monitor <ник> &8— &fЖивой HUD в ActionBar (VL/CPS/пинг/схема)"));
        sender.sendMessage(c(" &#00E676/ac top &8— &fТоп-10 подозреваемых по VL"));
        sender.sendMessage(c(" &#00E676/ac checks &8— &fПолный список проверок античита"));
        sender.sendMessage(c(" &#00E676/ac status &8— &fСтатистика сессии KingАЧ"));
        sender.sendMessage(c(" &#00E676/ac log &8— &fПоследние записи detections.log"));
        sender.sendMessage(c(" &#00E676/ac info <ник> &8— &fНейро-досье и индекс микро-дёрганий"));
        sender.sendMessage(c(" &#00E676/ac probe <ник> [схема] &8— &fБыстрый инжект (360° + стена)"));
        sender.sendMessage(c(" &#00E676/ac ban <ник> [причина] &8— &fЗапустить авто-бан KingАЧ"));
        sender.sendMessage(c(" &#00E676/ac AutoBanOff &8— &fВыключить авто-бан (детекты и алерты работают)"));
        sender.sendMessage(c(" &#00E676/ac AutoBanOn &8— &fВключить авто-бан обратно"));
        sender.sendMessage(c(" &#00E676/ac unban <ник> &8— &fРазбанить игрока"));
        sender.sendMessage(c(" &#00E676/ac alerts &8— &fВкл/выкл уведомления в чате"));
        sender.sendMessage(c(" &#00E676/reports &8— &fСписок активных жалоб игроков"));
        sender.sendMessage(c(" &#80D8FF/report <ник> &8— &fПожаловаться на читера (для всех игроков)"));
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
        if ("kingac".equals(name) || "ac".equals(name) || "kac".equals(name)) {
            if ((sender instanceof Player) && !isStaffMember((Player) sender)) {
                return args.length == 1 ? filterPrefix(args[0], Arrays.asList("report")) : Collections.<String>emptyList();
            }
            if (args.length == 1) {
                return filterPrefix(args[0], Arrays.asList("inject", "schemes", "monitor", "top", "checks",
                        "status", "log", "reports", "bans", "info", "probe", "wallprobe", "ban", "unban",
                        "alerts", "reload", "autoban", "autobanon", "autobanoff"));
            }
            String s0 = args[0].toLowerCase(Locale.ROOT);
            if (args.length == 2 && ("info".equals(s0) || "ban".equals(s0) || "probe".equals(s0)
                    || "wallprobe".equals(s0) || "inject".equals(s0) || "monitor".equals(s0) || "inj".equals(s0))) {
                return matchOnline(args[1]);
            }
            if (args.length == 2 && "unban".equals(s0)) {
                List<String> banned = new ArrayList<String>();
                for (AutoBanEntry b : autoBans.values()) banned.add(b.playerName);
                return filterPrefix(args[1], banned);
            }
            if (args.length == 3 && ("inject".equals(s0) || "probe".equals(s0))) {
                List<String> ids = new ArrayList<String>();
                for (InjectionScheme sc : InjectionScheme.values()) ids.add(sc.id);
                ids.add("auto");
                ids.add("all");
                return filterPrefix(args[2], ids);
            }
        }
        if ("kinject".equals(name)) {
            if ((sender instanceof Player) && !isStaffMember((Player) sender)) return Collections.emptyList();
            if (args.length == 1) {
                List<String> out = matchOnline(args[0]);
                out.add("list");
                out.add("config");
                return out;
            }
            if (args.length == 2) {
                List<String> ids = new ArrayList<String>();
                for (InjectionScheme sc : InjectionScheme.values()) ids.add(sc.id);
                ids.add("auto");
                return filterPrefix(args[1], ids);
            }
        }
        return Collections.emptyList();
    }


    /** v8.2: формат времени без SimpleDateFormat (макс. совместимость везде). */
    private static String pad2(int v) {
        return (v < 10 ? "0" : "") + v;
    }

    private static String stampNow() {
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.YEAR) + "-" + pad2(cal.get(Calendar.MONTH) + 1) + "-" + pad2(cal.get(Calendar.DAY_OF_MONTH))
                + " " + pad2(cal.get(Calendar.HOUR_OF_DAY)) + ":" + pad2(cal.get(Calendar.MINUTE)) + ":" + pad2(cal.get(Calendar.SECOND));
    }

    private static String stampBan(long ms) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(ms);
        return pad2(cal.get(Calendar.DAY_OF_MONTH)) + "." + pad2(cal.get(Calendar.MONTH) + 1) + "." + cal.get(Calendar.YEAR)
                + " " + pad2(cal.get(Calendar.HOUR_OF_DAY)) + ":" + pad2(cal.get(Calendar.MINUTE));
    }

    private static String vlSummary(PlayerAcData d) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Map.Entry<String, Integer> en : d.vlByCheck.entrySet()) {
            if (i >= 5) {
                sb.append(" +").append(d.vlByCheck.size() - 5);
                break;
            }
            if (i > 0) sb.append(", ");
            sb.append(en.getKey()).append(": ").append(en.getValue().intValue());
            i++;
        }
        return sb.toString();
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

    /**
     * v8.2 КРОСС-ВЕРСИОННАЯ БЕЗОПАСНОСТЬ (1.16.5 - 1.21.x):
     * имя эффекта берётся через Keyed#getKey (1.20.5+) с откатом на getName (1.16-1.20.4),
     * чтобы на новых ядрах не получить NoSuchMethodError.
     */
    private static String effectName(PotionEffectType t) {
        if (t == null) return "";
        try {
            Object key = t.getClass().getMethod("getKey").invoke(t);
            if (key != null) {
                Object k = key.getClass().getMethod("getKey").invoke(key);
                if (k != null) return String.valueOf(k).toUpperCase(Locale.ROOT);
            }
        } catch (Throwable ignored) {}
        try {
            Object n = t.getClass().getMethod("getName").invoke(t);
            if (n != null) return String.valueOf(n).toUpperCase(Locale.ROOT);
        } catch (Throwable ignored) {}
        return "";
    }

    private static boolean hasEffectName(Player p, String upperName) {
        if (p == null) return false;
        try {
            for (PotionEffect pe : p.getActivePotionEffects()) {
                if (effectName(pe.getType()).contains(upperName)) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /** Player#isRiptiding() существует только с 1.17 — на 1.16.5 уходим в безопасный false. */
    private static boolean isRiptidingSafe(Player p) {
        try {
            Method m = Player.class.getMethod("isRiptiding");
            Object r = m.invoke(p);
            return Boolean.TRUE.equals(r);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Кросс-версионный поиск Material по именам (DEEPSLATE/TUFF появились только в 1.17!).
     * На 1.16.5 отсутствующие материалы просто пропускаются — никаких NoSuchFieldError.
     */
    private static Material safeMat(String... names) {
        for (String n : names) {
            try {
                Material m = Material.matchMaterial(n);
                if (m != null) return m;
            } catch (Throwable ignored) {}
        }
        return Material.STONE;
    }

    private static final Material MAT_DEEPSLATE = safeMat("DEEPSLATE");
    private static final Material MAT_TUFF = safeMat("TUFF");
    private static final Material MAT_DEEP_DIAMOND = safeMat("DEEPSLATE_DIAMOND_ORE");
    private static final Material MAT_DEEP_EMERALD = safeMat("DEEPSLATE_EMERALD_ORE");
    private static final Material MAT_DEEP_REDSTONE = safeMat("DEEPSLATE_REDSTONE_ORE");
    private static final Material MAT_DEEP_GOLD = safeMat("DEEPSLATE_GOLD_ORE");
    private static final Material MAT_DEEP_LAPIS = safeMat("DEEPSLATE_LAPIS_ORE");
    private static final Material MAT_DEBRIS = safeMat("ANCIENT_DEBRIS");
    private static final Material MAT_COPPER = safeMat("COPPER_ORE", "DEEPSLATE_COPPER_ORE");

    private static int getPotionAmp(Player p, String nameSub) {
        try {
            for (PotionEffect pe : p.getActivePotionEffects()) {
                if (effectName(pe.getType()).contains(nameSub)) {
                    return pe.getAmplifier() + 1;
                }
            }
        } catch (Throwable ignored) {}
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
