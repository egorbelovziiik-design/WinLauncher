package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ModerationManager implements CommandExecutor, TabCompleter {

    public static final String GUI_CHECK_MAIN_PREFIX = "§8🔍 Проверка: §c";
    public static final String GUI_CHECK_SOFT_PREFIX = "§8📲 Программа проверки: §e";
    public static final String GUI_SW_TITLE = "§8🛡 Работа персонала (/sw)";

    public static final class CheckSession {
        public final UUID suspectId;
        public final String suspectName;
        public final UUID moderatorId;
        public final String moderatorName;
        public final String program; // "ANYDESK" or "DISCORD"
        public long expiresAtMs;
        public final BossBar bossBar;

        public CheckSession(Player suspect, Player moderator, String program, long durationMs) {
            this.suspectId = suspect.getUniqueId();
            this.suspectName = suspect.getName();
            this.moderatorId = moderator.getUniqueId();
            this.moderatorName = moderator.getName();
            this.program = program.toUpperCase(Locale.ROOT);
            this.expiresAtMs = System.currentTimeMillis() + durationMs;
            this.bossBar = Bukkit.createBossBar(
                    ColorUtil.c("&#FF1744&l⚠ ПРОВЕРКА НА ЧИТЫ &8[&#FFD740" + this.program + "&8] &7• Модератор: &f" + moderator.getName()),
                    "DISCORD".equals(this.program) ? BarColor.BLUE : BarColor.RED,
                    BarStyle.SOLID
            );
            this.bossBar.setProgress(1.0);
            this.bossBar.addPlayer(suspect);
            this.bossBar.addPlayer(moderator);
        }
    }

    public static final class SpekReturnState {
        public final Location prevLoc;
        public final GameMode prevMode;

        public SpekReturnState(Location prevLoc, GameMode prevMode) {
            this.prevLoc = prevLoc;
            this.prevMode = prevMode;
        }
    }

    private final KingGriefCore plugin;
    // Active /check sessions keyed by suspect UUID
    private final Map<UUID, CheckSession> activeChecks = new HashMap<UUID, CheckSession>();
    // Active /sw shifts: staff UUID -> shift start millis
    private final Map<UUID, Long> activeStaffShifts = new HashMap<UUID, Long>();
    // Players currently floating upward in /ban ascension animation
    private final Set<UUID> ascendingBanPlayers = new HashSet<UUID>();
    // /spek cooldowns & return locations
    private final Map<UUID, Long> spekCooldowns = new HashMap<UUID, Long>();
    private final Map<UUID, SpekReturnState> spekReturns = new HashMap<UUID, SpekReturnState>();
    // v5.9: SlowChat global cooldown & per-player last chat timestamp
    private volatile int slowChatSeconds = 0;
    private final Map<UUID, Long> lastChatAtMs = new HashMap<UUID, Long>();
    // v5.9.1: 6-Second (120 ticks) Santa Claus Duct-Tape Mute Animation entity tracker
    private static final String SANTA_SKIN_HASH = "d178ae03d84227f21fa23682d9c620131000ffb7673395533b2740fa83ab1037";
    private final Map<UUID, BukkitRunnable> activeMuteAnimTasks = new HashMap<UUID, BukkitRunnable>();
    private final Map<UUID, List<Entity>> activeMuteAnimEntities = new HashMap<UUID, List<Entity>>();
    private final Map<UUID, BukkitRunnable> activeBanAnimTasks = new HashMap<UUID, BukkitRunnable>();
    private final Map<UUID, List<Entity>> activeBanAnimEntities = new HashMap<UUID, List<Entity>>();
    private final Map<UUID, Map<Block, BlockData>> activeBanAnimPortals = new HashMap<UUID, Map<Block, BlockData>>();
    private ItemStack cachedSantaHead = null;
    private ItemStack cachedSackItem = null;

    private BukkitRunnable checkTickTask;

    public ModerationManager(KingGriefCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        checkTickTask = new BukkitRunnable() {
            int tickSec = 0;

            @Override
            public void run() {
                tickSec++;
                tickActiveChecks(tickSec);
            }
        };
        checkTickTask.runTaskTimer(plugin, 20L, 20L);
    }

    public void shutdown() {
        if (checkTickTask != null) {
            try { checkTickTask.cancel(); } catch (Throwable ignored) {}
        }
        for (CheckSession cs : new ArrayList<CheckSession>(activeChecks.values())) {
            try { cs.bossBar.removeAll(); } catch (Throwable ignored) {}
        }
        activeChecks.clear();
        // Save any active /sw shift durations
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> en : activeStaffShifts.entrySet()) {
            Player st = Bukkit.getPlayer(en.getKey());
            if (st != null) {
                long sec = Math.max(1L, (now - en.getValue().longValue()) / 1000L);
                plugin.getStore().addStaffWorkSeconds(st.getName(), sec);
            }
        }
        activeStaffShifts.clear();
        for (UUID uid : new ArrayList<UUID>(activeMuteAnimTasks.keySet())) {
            cleanupMuteAnimationFor(uid);
        }
        for (UUID uid : new ArrayList<UUID>(activeBanAnimTasks.keySet())) {
            cleanupSantaBanAnimationFor(uid);
        }
    }

    public boolean isStaff(CommandSender s) {
        if (!(s instanceof Player)) return true;
        Player p = (Player) s;
        RankManager.RankDef r = plugin.getRanks().getRank(p);
        return r != null && ("owner".equalsIgnoreCase(r.id) || r.tier >= 4 || r.weight >= 200 || p.hasPermission("kinggrief.staff"));
    }

    public int getSlowChatSeconds() {
        return slowChatSeconds;
    }

    /** Returns remaining wait seconds if blocked by /slowchat, or 0 if allowed. */
    public int checkAndRecordSlowChat(Player p) {
        int cd = slowChatSeconds;
        if (cd <= 0 || isStaff(p)) return 0;
        long now = System.currentTimeMillis();
        Long prev = lastChatAtMs.get(p.getUniqueId());
        if (prev != null && now - prev.longValue() < cd * 1000L) {
            return (int) ((cd * 1000L - (now - prev.longValue())) / 1000L + 1L);
        }
        lastChatAtMs.put(p.getUniqueId(), Long.valueOf(now));
        return 0;
    }

    private boolean canPunishTarget(CommandSender sender, String targetName) {
        if (!(sender instanceof Player)) return true;
        Player staff = (Player) sender;
        RankManager.RankDef myRank = plugin.getRanks().getRank(staff);
        if ("owner".equalsIgnoreCase(myRank.id)) return true;
        RankManager.RankDef targetRank = plugin.getRanks().getRankByName(targetName);
        Player onTarget = Bukkit.getPlayerExact(targetName);
        if (onTarget != null) targetRank = plugin.getRanks().getRank(onTarget);
        if ("owner".equalsIgnoreCase(targetRank.id)) {
            sender.sendMessage(ColorUtil.c("&#FF1744✖ Нельзя выдать наказание владельцу сервера OWNER!"));
            return false;
        }
        if (targetRank.weight >= myRank.weight && targetRank.weight >= 200) {
            sender.sendMessage(ColorUtil.c("&#FF1744✖ Нельзя наказать сотрудника КП равного или более высокого ранга!"));
            return false;
        }
        return true;
    }

    private boolean ipBelongsToOwner(String ip) {
        for (String acc : plugin.getStore().getAccountsByIp(ip)) {
            if ("owner".equalsIgnoreCase(plugin.getRanks().getRankByName(acc).id)) return true;
        }
        for (Player on : Bukkit.getOnlinePlayers()) {
            if (ip.equalsIgnoreCase(plugin.getStore().getPlayerIp(on))
                    && "owner".equalsIgnoreCase(plugin.getRanks().getRank(on).id)) {
                return true;
            }
        }
        return false;
    }

    private static String maskIp(String ip, boolean isOwnerViewer) {
        if (ip == null || ip.isEmpty()) return "неизвестен";
        if (isOwnerViewer) return ip;
        String[] parts = ip.split("\\.");
        if (parts.length == 4) return parts[0] + "." + parts[1] + ".*.*";
        return ip.length() > 6 ? ip.substring(0, 6) + "***" : "***";
    }

    public boolean isOnShift(Player p) {
        return p != null && activeStaffShifts.containsKey(p.getUniqueId());
    }

    public boolean isUnderCheck(Player p) {
        return p != null && activeChecks.containsKey(p.getUniqueId());
    }

    public boolean isAscendingBan(Player p) {
        return p != null && ascendingBanPlayers.contains(p.getUniqueId());
    }

    public boolean isFrozenOrAscending(Player p) {
        if (p == null) return false;
        return activeChecks.containsKey(p.getUniqueId()) || ascendingBanPlayers.contains(p.getUniqueId());
    }

    public CheckSession getCheckBySuspect(UUID id) {
        return activeChecks.get(id);
    }

    public CheckSession getCheckByModerator(UUID modId) {
        for (CheckSession cs : activeChecks.values()) {
            if (cs.moderatorId.equals(modId)) return cs;
        }
        return null;
    }

    private void tickActiveChecks(int secCounter) {
        if (activeChecks.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (CheckSession cs : new ArrayList<CheckSession>(activeChecks.values())) {
            Player suspect = Bukkit.getPlayer(cs.suspectId);
            if (suspect == null || !suspect.isOnline()) continue;

            long remMs = Math.max(0L, cs.expiresAtMs - now);
            long remSec = remMs / 1000L;
            double prog = Math.min(1.0, Math.max(0.0, remMs / 300_000.0));
            cs.bossBar.setProgress(prog);

            String progBadge = "DISCORD".equals(cs.program) ? "&#5865F2&lDISCORD" : "&#FF5252&lANYDESK";
            String hint = "DISCORD".equals(cs.program)
                    ? "&fОтправьте ваш логин &#5865F2Discord &fв чат модератору!"
                    : "&fОтправьте ваш 9-значный код &#FF5252AnyDesk &fв чат!";

            cs.bossBar.setTitle(ColorUtil.c("&#FF1744&l⚠ ПРОВЕРКА НА ЧИТЫ &8[" + progBadge + "&8] &7• Модер: &f"
                    + cs.moderatorName + " &7• Время: &#FFD740" + fmtClock(remSec) + " &8(&cВыход = БАН!&8)"));

            // Persistent On-Screen Title + Subtitle every second so it stays clearly visible on screen!
            suspect.sendTitle(
                    ColorUtil.c("&#FF1744&l⚠ ПРОВЕРКА НА ЧИТЫ ⚠"),
                    ColorUtil.c("&fПрограмма: " + progBadge + " &8│ &eСкиньте данные в чат!"),
                    0, 32, 5
            );
            ColorUtil.actionBar(suspect, "&#FF1744⚠ ВЫ НА ПРОВЕРКЕ ЧЕРЕЗ " + progBadge + " &8│ &fОсталось: &#FFD740" + fmtClock(remSec) + " &8│ &cВыход с сервера = АВТО-БАН!");

            if (secCounter % 4 == 0) {
                ColorUtil.sound(suspect, "block.note_block.pling", 0.9f, 0.75f);
                suspect.sendMessage(ColorUtil.c("&#FF1744&l[ПРОВЕРКА] " + hint + " &7(Проверяющий: &e" + cs.moderatorName + "&7)"));
            }
        }
    }

    public void handleQuitDuringCheck(Player quitting) {
        ascendingBanPlayers.remove(quitting.getUniqueId());
        cleanupMuteAnimationFor(quitting.getUniqueId());
        // Save shift if quitting moderator was on /sw
        Long shiftStart = activeStaffShifts.remove(quitting.getUniqueId());
        if (shiftStart != null) {
            long sec = Math.max(1L, (System.currentTimeMillis() - shiftStart.longValue()) / 1000L);
            plugin.getStore().addStaffWorkSeconds(quitting.getName(), sec);
        }

        CheckSession cs = activeChecks.remove(quitting.getUniqueId());
        if (cs != null) {
            try { cs.bossBar.removeAll(); } catch (Throwable ignored) {}
            long thirtyDaysMs = 30L * 86400_000L;
            String reason = "Выход с проверки на читы (" + cs.program + ")";
            plugin.getStore().ban(quitting.getName(), thirtyDaysMs, reason, cs.moderatorName);
            plugin.getStore().addStaffBan(cs.moderatorName);
            plugin.getStore().addStaffCheck(cs.moderatorName);
            Bukkit.broadcastMessage(ColorUtil.c("&#FF1744&l🔨 [АВТО-БАН ПРОВЕРКИ] &fИгрок &c" + quitting.getName()
                    + " &fвышел с проверки (&#FFD740" + cs.program + "&f) у модератора &#00E676" + cs.moderatorName
                    + " &fи забанен на &c30 дн.&f!"));
        }
    }

    /**
     * Перехватывает чат подозреваемого (и ответы модератора), чтобы они общались напрямую во время проверки!
     */
    public boolean handleCheckChat(Player sender, String message) {
        CheckSession asSuspect = activeChecks.get(sender.getUniqueId());
        if (asSuspect != null) {
            String line = ColorUtil.c("&#FF1744&l[ПРОВЕРКА • " + asSuspect.program + "] &e" + sender.getName()
                    + " &8» &f") + message;
            sender.sendMessage(line);
            Player mod = Bukkit.getPlayer(asSuspect.moderatorId);
            if (mod != null && mod.isOnline()) {
                mod.sendMessage(line);
                ColorUtil.sound(mod, "entity.experience_orb.pickup", 1.0f, 1.4f);
            }
            Bukkit.getConsoleSender().sendMessage(line);
            return true;
        }

        CheckSession asMod = getCheckByModerator(sender.getUniqueId());
        if (asMod != null && !message.startsWith("!")) {
            Player susp = Bukkit.getPlayer(asMod.suspectId);
            if (susp != null && susp.isOnline()) {
                String line = ColorUtil.c("&#00E676&l[МОДЕРАТОР • " + asMod.program + "] &a" + sender.getName()
                        + " &8» &f") + message;
                sender.sendMessage(line);
                susp.sendMessage(line);
                ColorUtil.sound(susp, "block.note_block.bell", 1.0f, 1.3f);
                Bukkit.getConsoleSender().sendMessage(line);
                return true;
            }
        }
        return false;
    }

    // ================= COMMAND ROUTER (/check, /sw, /mute, /muteip, /unmute, /unmuteip, /ban, /banip, /unban, /unbanip, /dupeip, /warn, /unwarn, /warns, /history, /clearchat, /slowchat, /kick, /staff, /spek) =================
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String c = cmd.getName().toLowerCase(Locale.ROOT);
        if ("spek".equals(c)) return handleSpekCommand(sender, args);
        if ("sw".equals(c)) return handleStaffWorkCommand(sender, args);
        if ("check".equals(c)) return handleCheckCommand(sender, args);
        if ("mute".equals(c)) return handleTimedMute(sender, args);
        if ("unmute".equals(c)) return handleUnmute(sender, args);
        if ("muteip".equals(c)) return handleTimedIpMute(sender, args);
        if ("unmuteip".equals(c)) return handleUnmuteIp(sender, args);
        if ("ban".equals(c)) return handleTimedBanWithAscension(sender, args);
        if ("unban".equals(c)) return handleUnban(sender, args);
        if ("banip".equals(c)) return handleTimedIpBan(sender, args);
        if ("unbanip".equals(c)) return handleUnbanIp(sender, args);
        if ("kick".equals(c)) return handleKick(sender, args);
        if ("dupeip".equals(c)) return handleDupeIp(sender, args);
        if ("warn".equals(c)) return handleWarn(sender, args);
        if ("unwarn".equals(c)) return handleUnwarn(sender, args);
        if ("warns".equals(c)) return handleWarnsList(sender, args);
        if ("history".equals(c)) return handleHistory(sender, args);
        if ("clearchat".equals(c)) return handleClearChat(sender);
        if ("slowchat".equals(c)) return handleSlowChat(sender, args);
        if ("staff".equals(c)) return handleStaffOnlineList(sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        String c = cmd.getName().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<String>();
        if ("check".equals(c) && isStaff(sender)) {
            if (args.length == 1) {
                List<String> opts = new ArrayList<String>(Arrays.asList("start", "stop"));
                for (Player p : Bukkit.getOnlinePlayers()) opts.add(p.getName());
                return filter(opts, args[0]);
            }
            if (args.length == 2) {
                if ("start".equalsIgnoreCase(args[0]) || "stop".equalsIgnoreCase(args[0])) {
                    return onlineNames(args[1]);
                }
                return filter(Arrays.asList("start", "stop"), args[1]);
            }
            if (args.length == 3) {
                return filter(Arrays.asList("anydesk", "discord"), args[2]);
            }
        } else if ("sw".equals(c) && isStaff(sender)) {
            if (args.length == 1) {
                return filter(Arrays.asList("начать", "закончить", "status", "start", "stop"), args[0]);
            }
            if (args.length == 2 && ("начать".equalsIgnoreCase(args[0]) || "закончить".equalsIgnoreCase(args[0]))) {
                return filter(Arrays.asList("работу"), args[1]);
            }
            if (args.length == 2 && "status".equalsIgnoreCase(args[0])) {
                return onlineNames(args[1]);
            }
        } else if (("mute".equals(c) || "muteip".equals(c) || "ban".equals(c) || "banip".equals(c)) && isStaff(sender)) {
            if (args.length == 1) return onlineNames(args[0]);
            if (args.length == 2) {
                return filter(c.startsWith("mute")
                        ? Arrays.asList("15m", "30m", "1h", "3h", "1d", "perm")
                        : Arrays.asList("7d", "14d", "30d", "90d", "perm"), args[1]);
            }
            if (args.length == 3) {
                return filter(c.startsWith("mute")
                        ? Arrays.asList("Оскорбление", "Обход_мута", "Флуд", "Спам", "Реклама")
                        : Arrays.asList("Читы", "Обход_бана", "Твинк_читера", "KillAura", "XRay"), args[2]);
            }
        } else if (("unmute".equals(c) || "unmuteip".equals(c) || "unban".equals(c) || "unbanip".equals(c)
                || "dupeip".equals(c) || "warn".equals(c) || "unwarn".equals(c) || "warns".equals(c)
                || "history".equals(c) || "kick".equals(c)) && isStaff(sender)) {
            if (args.length == 1) return onlineNames(args[0]);
            if (args.length == 2 && "warn".equals(c)) {
                return filter(Arrays.asList("Спам", "Оскорбление", "Багоюз", "Помеха", "Нарушение_правил"), args[1]);
            }
        } else if ("slowchat".equals(c) && isStaff(sender) && args.length == 1) {
            return filter(Arrays.asList("off", "3", "5", "10", "15"), args[0]);
        } else if ("spek".equals(c)) {
            if (args.length == 1) {
                List<String> opts = new ArrayList<String>();
                if (isStaff(sender)) {
                    opts.add("tp");
                    opts.add("back");
                }
                for (Player p : Bukkit.getOnlinePlayers()) opts.add(p.getName());
                return filter(opts, args[0]);
            }
            if (args.length == 2 && isStaff(sender) && "tp".equalsIgnoreCase(args[0])) {
                return onlineNames(args[1]);
            }
        }
        return out;
    }

    // ================= 1. /check (START / STOP + ANYDESK / DISCORD + GUI) =================
    private boolean handleCheckCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Команда /check доступна модераторам в игре!");
            return true;
        }
        Player staff = (Player) sender;
        if (!isStaff(staff)) {
            staff.sendMessage(ColorUtil.c("&#FF1744✖ У вас нет прав на проведение проверок!"));
            return true;
        }
        if (args.length == 0) {
            staff.sendMessage(ColorUtil.c("&#FFD740Использование: &f/check <ник> &7или &f/check <ник> <start|stop> [anydesk|discord]"));
            return true;
        }

        // Support both `/check <nick> [start|stop] [anydesk|discord]` AND `/check <start|stop> <nick> [anydesk|discord]`
        String action = null;
        String targetName = null;
        String prog = null;

        if ("start".equalsIgnoreCase(args[0]) || "stop".equalsIgnoreCase(args[0])) {
            action = args[0].toLowerCase(Locale.ROOT);
            if (args.length >= 2) targetName = args[1];
            if (args.length >= 3) prog = args[2];
        } else {
            targetName = args[0];
            if (args.length >= 2) action = args[1].toLowerCase(Locale.ROOT);
            if (args.length >= 3) prog = args[2];
        }

        if (targetName == null) {
            staff.sendMessage(ColorUtil.c("&#FFD740Укажите ник игрока: &f/check <ник>"));
            return true;
        }
        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !target.isOnline()) {
            staff.sendMessage(ColorUtil.c("&#FF1744✖ Игрок &f" + targetName + " &#FF1744сейчас не в сети!"));
            return true;
        }
        if (target.equals(staff)) {
            staff.sendMessage(ColorUtil.c("&#FF1744✖ Нельзя вызвать на проверку самого себя!"));
            return true;
        }

        if (action == null) {
            openCheckMainGui(staff, target);
            return true;
        }
        if ("stop".equals(action)) {
            stopCheck(staff, target);
            return true;
        }
        if ("start".equals(action)) {
            if (prog == null || (!"anydesk".equalsIgnoreCase(prog) && !"discord".equalsIgnoreCase(prog))) {
                openCheckSoftwareGui(staff, target);
            } else {
                startCheck(staff, target, prog.toUpperCase(Locale.ROOT));
            }
            return true;
        }
        openCheckMainGui(staff, target);
        return true;
    }

    public void openCheckMainGui(Player staff, Player target) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI_CHECK_MAIN_PREFIX + target.getName());
        ItemStack pane = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);

        CheckSession cur = activeChecks.get(target.getUniqueId());
        boolean inCheck = cur != null;

        inv.setItem(11, makeButton(Material.LIME_WOOL, "&#00E676&l▶ НАЧАТЬ ПРОВЕРКУ (START)",
                "&7Нажмите, чтобы выбрать программу",
                "&7для проверки: &#FF5252AnyDesk &7или &#5865F2Discord&7!",
                "",
                inCheck ? "&eИгрок уже проверяется (" + cur.program + ")" : "&#00E676▸ Нажмите для выбора программы!"));

        ItemStack skull = new ItemStack(Material.PLAYER_HEAD, 1);
        ItemMeta sm = skull.getItemMeta();
        if (sm instanceof SkullMeta) {
            ((SkullMeta) sm).setOwningPlayer(target);
            sm.setDisplayName(ColorUtil.c("&#FFD740👤 Подозреваемый: &f" + target.getName()));
            sm.setLore(Arrays.asList(
                    ColorUtil.c("&7Мир: &b" + target.getWorld().getName()),
                    ColorUtil.c("&7Статус: " + (inCheck ? "&#FF1744На проверке (" + cur.program + ")" : "&#00E676Не на проверке")),
                    "",
                    ColorUtil.c("&7Ваша смена /sw: " + (isOnShift(staff) ? "&#00E676Активна" : "&eНе начата (/sw начать работу)"))
            ));
            skull.setItemMeta(sm);
        }
        inv.setItem(13, skull);

        inv.setItem(15, makeButton(Material.RED_WOOL, "&#FF5252&l■ ОСТАНОВИТЬ ПРОВЕРКУ (STOP)",
                "&7Завершить проверку (игрок чист),",
                "&7разморозить и снять табличку с экрана.",
                "",
                inCheck ? "&#00E676▸ Нажмите, чтобы отпустить игрока!" : "&8Проверка сейчас не запущена"));

        if (inCheck) {
            inv.setItem(21, makeButton(Material.CLOCK, "&#FFD740&l⏱ ПРОДЛИТЬ ТАЙМЕР (+3 МИН)",
                    "&7Добавить +3 минуты на скачивание программы"));
            inv.setItem(23, makeButton(Material.NETHERITE_AXE, "&#FF1744&l🔨 ЗАБАНИТЬ ЗА ЧИТЫ (30д + Взлёт!)",
                    "&7Мгновенно забанить на 30 дней с",
                    "&7анимацией взлёта вверх и зачётом в /sw status!"));
        }

        staff.openInventory(inv);
        ColorUtil.sound(staff, "block.chest.open", 0.8f, 1.2f);
    }

    public void openCheckSoftwareGui(Player staff, Player target) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI_CHECK_SOFT_PREFIX + target.getName());
        ItemStack pane = namedItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);

        inv.setItem(11, makeButton(Material.REDSTONE_BLOCK, "&#FF5252&l🟥 ПРОВЕРКА ЧЕРЕЗ ANYDESK",
                "&7Запускает проверку игрока &f" + target.getName(),
                "&7по программе &#FF5252AnyDesk&7!",
                "",
                "&fУ игрока на экране появится:",
                "&#FF1744«ПРОВЕРКА НА ЧИТЫ — Программа: ANYDESK»",
                "&7и требование скинуть 9-значный код в чат!",
                "",
                "&#00E676▸ Нажмите, чтобы начать через AnyDesk!"));

        inv.setItem(15, makeButton(Material.LAPIS_BLOCK, "&#5865F2&l🟦 ПРОВЕРКА ЧЕРЕЗ DISCORD",
                "&7Запускает проверку игрока &f" + target.getName(),
                "&7по программе &#5865F2Discord&7!",
                "",
                "&fУ игрока на экране появится:",
                "&#FF1744«ПРОВЕРКА НА ЧИТЫ — Программа: DISCORD»",
                "&7и требование скинуть свой Discord для демки!",
                "",
                "&#00E676▸ Нажмите, чтобы начать через Discord!"));

        inv.setItem(22, makeButton(Material.ARROW, "&e← Назад", "&7Вернуться в меню /check"));
        staff.openInventory(inv);
        ColorUtil.sound(staff, "ui.button.click", 1.0f, 1.2f);
    }

    public void startCheck(Player staff, Player suspect, String program) {
        RankManager.RankDef targetRank = plugin.getRanks().getRank(suspect);
        if ("owner".equals(targetRank.id) && !"owner".equals(plugin.getRanks().getRank(staff).id)) {
            staff.sendMessage(ColorUtil.c("&#FF1744✖ Нельзя вызвать OWNER на проверку!"));
            return;
        }
        // Clean up existing session if restarting
        CheckSession prev = activeChecks.remove(suspect.getUniqueId());
        if (prev != null) {
            try { prev.bossBar.removeAll(); } catch (Throwable ignored) {}
        }

        // Teleport both to safe spawn plaza check spot if in survival/combat so nobody kills suspect!
        CheckSession cs = new CheckSession(suspect, staff, program, 300_000L);
        activeChecks.put(suspect.getUniqueId(), cs);

        ColorUtil.sound(suspect, "entity.elder_guardian.curse", 0.9f, 1.1f);
        ColorUtil.sound(staff, "entity.player.levelup", 0.9f, 1.2f);

        String progColored = "DISCORD".equalsIgnoreCase(program) ? "&#5865F2&lDISCORD" : "&#FF5252&lANYDESK";
        suspect.sendTitle(
                ColorUtil.c("&#FF1744&l⚠ ПРОВЕРКА НА ЧИТЫ ⚠"),
                ColorUtil.c("&fПрограмма: " + progColored + " &8│ &eПишите данные в чат!"),
                5, 60, 10
        );
        suspect.sendMessage("");
        suspect.sendMessage(ColorUtil.c("&#FF1744&l=============================================="));
        suspect.sendMessage(ColorUtil.c(" &#FF1744&l⚠ ВЫ ВЫЗВАНЫ НА ПРОВЕРКУ НА ЧИТЫ!"));
        suspect.sendMessage(ColorUtil.c(" &fПроверяющий модератор: &#00E676" + staff.getName()));
        suspect.sendMessage(ColorUtil.c(" &fВыбранная программа: " + progColored));
        if ("DISCORD".equalsIgnoreCase(program)) {
            suspect.sendMessage(ColorUtil.c(" &e▸ Напишите ваш логин Discord прямо в чат (У вас 5:00 мин)!"));
        } else {
            suspect.sendMessage(ColorUtil.c(" &e▸ Скачайте AnyDesk и напишите ваш код прямо в чат (У вас 5:00 мин)!"));
        }
        suspect.sendMessage(ColorUtil.c(" &#FF1744&l✖ Выход с сервера или игнор = БАН НА 30 ДНЕЙ!"));
        suspect.sendMessage(ColorUtil.c("&#FF1744&l=============================================="));
        suspect.sendMessage("");

        staff.sendMessage(ColorUtil.c("&#00E676&l🔍 [ПРОВЕРКА] &fВы начали проверку игрока &#FFD740" + suspect.getName()
                + " &fчерез " + progColored + "&f! Пишите в обычный чат для общения с ним. Остановить: &e/check " + suspect.getName() + " stop"));
        broadcastToStaff("&#FFD740🔍 [SW] &fМодератор &#00E676" + staff.getName() + " &fвызвал игрока &c"
                + suspect.getName() + " &fна проверку через " + progColored + "&f!");
    }

    public void stopCheck(Player staff, Player suspect) {
        CheckSession cs = activeChecks.remove(suspect.getUniqueId());
        if (cs == null) {
            staff.sendMessage(ColorUtil.c("&eИгрок &f" + suspect.getName() + " &eсейчас не находится на проверке."));
            return;
        }
        try { cs.bossBar.removeAll(); } catch (Throwable ignored) {}
        plugin.getStore().addStaffCheck(staff.getName());

        suspect.sendTitle(
                ColorUtil.c("&#00E676&l✔ ПРОВЕРКА ЗАВЕРШЕНА!"),
                ColorUtil.c("&fЧитов не обнаружено — приятной игры!"),
                5, 50, 10
        );
        ColorUtil.sound(suspect, "ui.toast.challenge_complete", 1.0f, 1.25f);
        suspect.sendMessage(ColorUtil.c("&#00E676&l✔ Модератор &f" + staff.getName() + " &#00E676завершил вашу проверку. Вы свободны!"));
        staff.sendMessage(ColorUtil.c("&#00E676✔ Проверка игрока &f" + suspect.getName() + " &#00E676остановлена (+1 проверка в /sw status)!"));
        broadcastToStaff("&#00E676✔ [SW] &fМодератор &a" + staff.getName() + " &fзавершил проверку игрока &f" + suspect.getName() + " &7(Чист).");
    }

    // ================= 2. /sw (НАЧАТЬ РАБОТУ / ЗАКОНЧИТЬ РАБОТУ / STATUS) =================
    private boolean handleStaffWorkCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            if (args.length >= 2 && "status".equalsIgnoreCase(args[0])) {
                sendStaffStatusCard(sender, args[1]);
                return true;
            }
            sender.sendMessage("Использование из консоли: /sw status <ник>");
            return true;
        }
        Player staff = (Player) sender;
        if (!isStaff(staff)) {
            staff.sendMessage(ColorUtil.c("&#FF1744✖ Команда /sw доступна только персоналу сервера!"));
            return true;
        }
        if (args.length == 0) {
            openStaffWorkGui(staff);
            return true;
        }
        String joined = String.join(" ", args).trim().toLowerCase(Locale.ROOT);
        if (joined.startsWith("начать") || joined.equals("start") || joined.equals("on")) {
            startStaffWork(staff);
            return true;
        }
        if (joined.startsWith("закончить") || joined.startsWith("окончить") || joined.equals("stop") || joined.equals("end") || joined.equals("off")) {
            stopStaffWork(staff);
            return true;
        }
        if (args[0].equalsIgnoreCase("status") || args[0].equalsIgnoreCase("статус") || args[0].equalsIgnoreCase("stats")) {
            String target = args.length >= 2 ? args[1] : staff.getName();
            sendStaffStatusCard(staff, target);
            return true;
        }

        staff.sendMessage(ColorUtil.c("&#FFD740Использование /sw:"));
        staff.sendMessage(ColorUtil.c(" &f/sw начать работу &7— заступить на смену модератора"));
        staff.sendMessage(ColorUtil.c(" &f/sw закончить работу &7— завершить смену модератора"));
        staff.sendMessage(ColorUtil.c(" &f/sw status [ник] &7— статистика банов, мутов и проверок"));
        return true;
    }

    public void startStaffWork(Player staff) {
        if (activeStaffShifts.containsKey(staff.getUniqueId())) {
            staff.sendMessage(ColorUtil.c("&eВы уже находитесь на рабочей смене! Статистика: &f/sw status"));
            return;
        }
        activeStaffShifts.put(staff.getUniqueId(), Long.valueOf(System.currentTimeMillis()));
        ColorUtil.sound(staff, "entity.player.levelup", 1.0f, 1.3f);
        staff.sendTitle(ColorUtil.c("&#00E676&l🛡 СМЕНА НАЧАТА!"), ColorUtil.c("&fБаны, муты и проверки идут в /sw status"), 5, 40, 10);
        broadcastToStaff("&#00E676&l🟢 [SW] &fМодератор &#FFD740" + staff.getName() + " &fначал работу на сервере!");
    }

    public void stopStaffWork(Player staff) {
        Long started = activeStaffShifts.remove(staff.getUniqueId());
        if (started == null) {
            staff.sendMessage(ColorUtil.c("&cВы сейчас не на рабочей смене! Введите: &f/sw начать работу"));
            return;
        }
        long elapsedSec = Math.max(1L, (System.currentTimeMillis() - started.longValue()) / 1000L);
        plugin.getStore().addStaffWorkSeconds(staff.getName(), elapsedSec);
        ColorUtil.sound(staff, "block.beacon.deactivate", 1.0f, 1.2f);
        staff.sendTitle(ColorUtil.c("&#FF9100&l🛡 СМЕНА ОКОНЧЕНА"), ColorUtil.c("&fВремя смены: &e" + ColorUtil.fmtTime(elapsedSec)), 5, 40, 10);
        broadcastToStaff("&#FF9100&l🔴 [SW] &fМодератор &#FFD740" + staff.getName() + " &fзакончил работу (&e" + ColorUtil.fmtTime(elapsedSec) + "&f).");
        sendStaffStatusCard(staff, staff.getName());
    }

    public void sendStaffStatusCard(CommandSender viewer, String staffName) {
        Player onlineStaff = Bukkit.getPlayerExact(staffName);
        boolean onShift = onlineStaff != null && activeStaffShifts.containsKey(onlineStaff.getUniqueId());
        long curShiftSec = 0L;
        if (onShift) {
            curShiftSec = Math.max(0L, (System.currentTimeMillis() - activeStaffShifts.get(onlineStaff.getUniqueId()).longValue()) / 1000L);
        }
        long totalWorkSec = plugin.getStore().getStaffWorkSeconds(staffName) + curShiftSec;
        int bans = plugin.getStore().getStaffBans(staffName);
        int mutes = plugin.getStore().getStaffMutes(staffName);
        int checks = plugin.getStore().getStaffChecks(staffName);
        int speks = plugin.getStore().getStaffSpeks(staffName);

        viewer.sendMessage(ColorUtil.c("&#80D8FF&m                                                  "));
        viewer.sendMessage(ColorUtil.c(" &#FFD740&l🛡 СТАТИСТИКА ПЕРСОНАЛА (/sw status): &f" + staffName));
        viewer.sendMessage(ColorUtil.c(" &7Статус смены: " + (onShift
                ? "&#00E676&l● НА РАБОТЕ &7(текущая смена: &f" + ColorUtil.fmtTime(curShiftSec) + "&7)"
                : "&#FF5252&l● НЕ НА СМЕНЕ")));
        viewer.sendMessage(ColorUtil.c(" &7Отработано времени: &#80D8FF" + ColorUtil.fmtTime(totalWorkSec)));
        viewer.sendMessage(ColorUtil.c(" &7🔨 Засчитано БАНОВ: &#FF1744&l" + bans));
        viewer.sendMessage(ColorUtil.c(" &7🔇 Засчитано МУТОВ: &#FF9100&l" + mutes));
        viewer.sendMessage(ColorUtil.c(" &7🔍 Проведено ПРОВЕРОК (/check): &#00E676&l" + checks));
        viewer.sendMessage(ColorUtil.c(" &7👁 Принято СПЕКОВ (/spek): &#E040FB&l" + speks));
        viewer.sendMessage(ColorUtil.c("&#80D8FF&m                                                  "));
    }

    public void openStaffWorkGui(Player staff) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI_SW_TITLE);
        ItemStack pane = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);

        boolean on = isOnShift(staff);
        inv.setItem(11, makeButton(Material.EMERALD_BLOCK, "&#00E676&l▶ НАЧАТЬ РАБОТУ (/sw начать работу)",
                "&7Заступить на пост модератора",
                on ? "&a✔ Вы уже на смене!" : "&#00E676▸ Нажмите, чтобы начать смену!"));
        inv.setItem(13, makeButton(Material.NETHER_STAR, "&#FFD740&l📊 МОЯ СТАТИСТИКА (/sw status)",
                "&7🔨 Банов выдано: &#FF1744" + plugin.getStore().getStaffBans(staff.getName()),
                "&7🔇 Мутов выдано: &#FF9100" + plugin.getStore().getStaffMutes(staff.getName()),
                "&7🔍 Проверок /check: &#00E676" + plugin.getStore().getStaffChecks(staff.getName()),
                "&7👁 Принято /spek: &#E040FB" + plugin.getStore().getStaffSpeks(staff.getName()),
                "",
                "&#80D8FF▸ Нажмите для вывода в чат!"));
        inv.setItem(15, makeButton(Material.REDSTONE_BLOCK, "&#FF5252&l■ ЗАКОНЧИТЬ РАБОТУ (/sw закончить работу)",
                "&7Завершить смену и сохранить время",
                on ? "&#FF9100▸ Нажмите, чтобы закончить смену!" : "&8Вы сейчас не на смене"));

        staff.openInventory(inv);
    }

    // ================= 3. /mute (игрок) (время) (причина) & /ban (игрок) (время) (причина) + ВЗЛЁТ-АНИМАЦИЯ =================
    private boolean handleTimedMute(CommandSender sender, String[] args) {
        if (!isStaff(sender)) {
            sender.sendMessage(ColorUtil.c("&#FF1744✖ У вас нет прав на выдачу мута!"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ColorUtil.c("&#FFD740Использование: &f/mute <игрок> [время] [причина] &7(Пример: &f/mute Steve 30m Оскорбление&7)"));
            return true;
        }
        String target = args[0];
        if (!canPunishTarget(sender, target)) return true;
        long durMs;
        String reason;
        if (args.length >= 2) {
            long parsed = parseDurationMs(args[1]);
            if (parsed != Long.MIN_VALUE) {
                durMs = parsed;
                reason = args.length >= 3 ? joinArgs(args, 2) : "Нарушение правил чата";
            } else {
                durMs = 30L * 60_000L;
                reason = joinArgs(args, 1);
            }
        } else {
            durMs = 30L * 60_000L;
            reason = "Нарушение правил чата";
        }
        String staffName = sender instanceof Player ? sender.getName() : "Консоль";

        plugin.getStore().mute(target, durMs, reason, staffName);
        String timeLabel = formatDurationRu(durMs);
        plugin.getStore().appendPunishHistory(target, "&#FF5252[МУТ]", staffName, timeLabel, reason);
        if (sender instanceof Player) {
            plugin.getStore().addStaffMute(staffName);
        }

        Bukkit.broadcastMessage(ColorUtil.c("&#FF5252&l🔇 [МУТ] &fМодератор &#FFD740" + staffName
                + " &fвыдал мут игроку &c" + target + " &fна &#80D8FF" + timeLabel + "&f. Причина: &e" + reason));

        Player tp = Bukkit.getPlayerExact(target);
        if (tp != null && tp.isOnline()) {
            playSantaTapeMuteAnimation(tp, timeLabel, reason);
        }
        return true;
    }

    private boolean handleUnmute(CommandSender sender, String[] args) {
        if (!isStaff(sender)) {
            sender.sendMessage(ColorUtil.c("&#FF1744✖ Недостаточно прав!"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ColorUtil.c("&eИспользование: &f/unmute <игрок>"));
            return true;
        }
        plugin.getStore().unmute(args[0]);
        Player unmutedOnline = Bukkit.getPlayerExact(args[0]);
        if (unmutedOnline != null) {
            cleanupMuteAnimationFor(unmutedOnline.getUniqueId());
        }
        Bukkit.broadcastMessage(ColorUtil.c("&#00E676🔊 Модератор &f" + sender.getName() + " &#00E676снял мут с игрока &f" + args[0] + "&#00E676."));
        return true;
    }

    /**
     * 6-СЕКУНДНАЯ (РОВНО 120 ТИКОВ) АНИМАЦИЯ МУТА С ДЕДОМ МОРОЗОМ И СКОТЧЕМ!
     *  - Тики 1..40   (0.0 - 2.0 сек): Дед Мороз появляется в вихре снежинок и подходит вплотную к лицу игрока.
     *  - Тики 41..85  (2.0 - 4.25 сек): Дед Мороз поднимает руки с рулоном скотча и ножницами, наклеивает серебристый
     *                                   скотч прямо на рот игрока и отрезает ножницами ("ЩЁЛК!").
     *  - Тики 86..120 (4.25 - 6.0 сек): Дед Мороз разворачивается на 180°, машет рукой, уходит и исчезает на 120-м тике (6.0 сек)!
     */
    public void playSantaTapeMuteAnimation(final Player victim, final String timeLabel, final String reason) {
        if (victim == null || !victim.isOnline() || victim.getWorld() == null) return;
        final UUID victimId = victim.getUniqueId();
        cleanupMuteAnimationFor(victimId);

        final World w = victim.getWorld();
        final Location vLoc0 = victim.getLocation();
        Vector fwd0 = vLoc0.getDirection().setY(0.0);
        if (fwd0.lengthSquared() < 0.001) {
            fwd0 = new Vector(0.0, 0.0, 1.0);
        } else {
            fwd0.normalize();
        }
        final Vector approachAxis = fwd0.clone();

        Location spawnPos = vLoc0.clone().add(approachAxis.clone().multiply(3.6));
        spawnPos.setYaw(yawFacing(spawnPos, vLoc0));
        spawnPos.setPitch(0.0f);

        final List<Entity> spawned = new ArrayList<Entity>();
        activeMuteAnimEntities.put(victimId, spawned);

        final Skeleton santa;
        final TextDisplay speechBubble;
        try {
            // v6.1: ЖИВАЯ 3D-МОДЕЛЬКА Деда Мороза (анимированный персонаж, а не стойка!)!
            santa = spawnSantaModel(w, spawnPos, "tape");
            spawned.add(santa);

            Location bubbleLoc = spawnPos.clone().add(0.0, 2.38, 0.0);
            speechBubble = w.spawn(bubbleLoc, TextDisplay.class, new org.bukkit.util.Consumer<TextDisplay>() {
                @Override
                public void accept(TextDisplay td) {
                    td.setPersistent(false);
                    td.addScoreboardTag("kg_mod_anim");
                    td.setBillboard(Display.Billboard.CENTER);
                    td.setShadowed(true);
                    td.setSeeThrough(true);
                    td.setBackgroundColor(Color.fromARGB(185, 22, 24, 34));
                    td.setText(ColorUtil.c("&#FFD740🎅 «Хо-хо-хо! Много шумишь — несу скотч!»"));
                }
            });
            spawned.add(speechBubble);
        } catch (Throwable ex) {
            cleanupMuteAnimationFor(victimId);
            return;
        }

        // Initial arrival sound & snow swirl
        w.spawnParticle(Particle.SNOWFLAKE, spawnPos.clone().add(0.0, 1.1, 0.0), 32, 0.45, 0.75, 0.45, 0.04);
        w.spawnParticle(Particle.CLOUD, spawnPos.clone().add(0.0, 0.25, 0.0), 14, 0.35, 0.15, 0.35, 0.02);
        ColorUtil.sound(victim, "block.bell.use", 1.0f, 1.25f);
        ColorUtil.sound(victim, "entity.villager.no", 0.9f, 0.85f);
        victim.sendTitle(
                ColorUtil.c("&#FF5252&l🎅 К ВАМ ИДЁТ ДЕД МОРОЗ..."),
                ColorUtil.c("&fМут на &#FFD740" + timeLabel + " &8│ &7Причина: &e" + reason),
                4, 42, 6
        );

        final TextDisplay[] faceTapeRef = new TextDisplay[1];

        BukkitRunnable animTask = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (!victim.isOnline() || victim.getWorld() == null || !victim.getWorld().equals(w) || !santa.isValid()) {
                    cleanupMuteAnimationFor(victimId);
                    return;
                }

                Location vNow = victim.getLocation();
                Vector vDir = vNow.getDirection().setY(0.0);
                if (vDir.lengthSquared() < 0.001) {
                    vDir = approachAxis.clone();
                } else {
                    vDir.normalize();
                }

                // =====================================================================
                // ACT 1: Ticks 1..40 (0.0s .. 2.0s) — Дед Мороз подходит к игроку!
                // =====================================================================
                if (ticks <= 40) {
                    double progress = ticks / 40.0; // 0 -> 1
                    double dist = 3.6 - (3.6 - 0.92) * progress;
                    Location sLoc = vNow.clone().add(vDir.clone().multiply(dist));
                    sLoc.setYaw(yawFacing(sLoc, vNow));
                    sLoc.setPitch(0.0f);
                    sLoc.setY(groundYAt(w, sLoc.getX(), sLoc.getY(), sLoc.getZ()));
                    santa.teleport(sLoc);

                    // v6.1: живая моделька сама шагает — машем рукой со скотчем!
                    if (ticks % 4 == 0) {
                        try { santa.swingMainHand(); } catch (Throwable ignored) {}
                    }

                    if (speechBubble.isValid()) {
                        speechBubble.teleport(sLoc.clone().add(0.0, 2.38, 0.0));
                    }
                    if (ticks % 4 == 0) {
                        w.spawnParticle(Particle.SNOWFLAKE, sLoc.clone().add(0.0, 0.25, 0.0), 5, 0.25, 0.1, 0.25, 0.01);
                    }
                    if (ticks % 7 == 0) {
                        w.playSound(sLoc, "minecraft:block.snow.step", 0.9f, 0.95f);
                    }
                    ColorUtil.actionBar(victim, "&#FF5252🎅 Дед Мороз подходит с рулоном скотча... &8[&e" + timeLabel + "&8]");
                }
                // =====================================================================
                // ACT 2: Ticks 41..85 (2.0s .. 4.25s) — Наклеивает скотч прямо на лицо!
                // =====================================================================
                else if (ticks <= 85) {
                    Location sLoc = vNow.clone().add(vDir.clone().multiply(0.88));
                    sLoc.setYaw(yawFacing(sLoc, vNow));
                    sLoc.setPitch(0.0f);
                    santa.teleport(sLoc);

                    // v6.1: Дед Мороз шлепает скотчем по рту живыми взмахами руки (3D-модель)!
                    if (ticks % 5 == 0 || ticks == 45 || ticks == 57 || ticks == 68) {
                        try { santa.swingMainHand(); } catch (Throwable ignored) {}
                    }

                    if (speechBubble.isValid()) {
                        speechBubble.teleport(sLoc.clone().add(0.0, 2.38, 0.0));
                        if (ticks == 42) {
                            speechBubble.setText(ColorUtil.c("&#FF5252🎅 «А ну-ка ротик на замок! Клеим СКОТЧ...»"));
                        } else if (ticks == 68) {
                            speechBubble.setText(ColorUtil.c("&#00E676✂ «ЩЁЛК! Держится крепко!»"));
                        }
                    }

                    // Spawn & stick 3D Duct-Tape Display directly on the victim's mouth!
                    Location mouthLoc = victim.getEyeLocation().add(vDir.clone().multiply(0.32)).add(0.0, -0.14, 0.0);
                    if (ticks == 45 && faceTapeRef[0] == null) {
                        try {
                            faceTapeRef[0] = w.spawn(mouthLoc, TextDisplay.class, new org.bukkit.util.Consumer<TextDisplay>() {
                                @Override
                                public void accept(TextDisplay td) {
                                    td.setPersistent(false);
                                    td.setBillboard(Display.Billboard.CENTER);
                                    td.setShadowed(true);
                                    td.setBackgroundColor(Color.fromARGB(235, 165, 175, 184));
                                    td.setText(ColorUtil.c("&#263238&l[ ▒▒ СКОТЧ... ]"));
                                }
                            });
                            spawned.add(faceTapeRef[0]);
                        } catch (Throwable ignored) {}
                        w.playSound(mouthLoc, "minecraft:block.honey_block.slide", 1.2f, 1.35f);
                        victim.sendTitle(
                                ColorUtil.c("&#CFD8DC&l[ ▒▒▒ СКОТЧ... ]"),
                                ColorUtil.c("&#FF5252🎅 Дед Мороз заклеивает вам рот!"),
                                0, 30, 5
                        );
                    } else if (faceTapeRef[0] != null && faceTapeRef[0].isValid()) {
                        faceTapeRef[0].teleport(mouthLoc);
                        if (ticks == 57) {
                            faceTapeRef[0].setText(ColorUtil.c("&#263238&l[ ▒▒▒▒ СКОТЧ ▒▒▒▒ ]"));
                            w.playSound(mouthLoc, "minecraft:block.slime_block.place", 1.1f, 1.45f);
                        } else if (ticks == 68) {
                            // Shears SNIP! Full silver duct-tape sealed across mouth!
                            faceTapeRef[0].setText(ColorUtil.c("&#263238&l[ ████ СКОТЧ ████ ]"));
                            w.playSound(mouthLoc, "minecraft:entity.sheep.shear", 1.5f, 1.35f);
                            w.spawnParticle(Particle.CRIT, mouthLoc, 10, 0.15, 0.08, 0.15, 0.03);
                            victim.sendTitle(
                                    ColorUtil.c("&#CFD8DC&l[ ████ СКОТЧ ████ ]"),
                                    ColorUtil.c("&#FF5252🤐 Рот заклеен на &#FFD740" + timeLabel + " &8│ &f" + reason),
                                    2, 62, 10
                            );
                        }
                    }
                    ColorUtil.actionBar(victim, "&#CFD8DC&l[ ████ СКОТЧ ████ ] &8│ &#FF5252Дед Мороз заклеил вам лицо! &7(" + timeLabel + ")");
                }
                // =====================================================================
                // ACT 3: Ticks 86..120 (4.25s .. 6.00s) — Разворачивается и уходит!
                // =====================================================================
                else if (ticks < 120) {
                    double walkOut = (ticks - 85) / 35.0; // 0..1
                    double dist = 0.90 + walkOut * 3.35;  // walks away to 4.25 blocks!
                    Location sLoc = vNow.clone().add(vDir.clone().multiply(dist));
                    // Turn 180 degrees away from player!
                    sLoc.setYaw(yawFacing(vNow, sLoc));
                    sLoc.setPitch(0.0f);
                    santa.teleport(sLoc);

                    if (ticks % 4 == 0) {
                        try { santa.swingMainHand(); } catch (Throwable ignored) {}
                    }

                    if (speechBubble.isValid()) {
                        speechBubble.teleport(sLoc.clone().add(0.0, 2.38, 0.0));
                        if (ticks == 86) {
                            speechBubble.setText(ColorUtil.c("&#FFD740🎅 «Вот так тише! Не нарушай, с Новым Годом!»"));
                            w.playSound(sLoc, "minecraft:block.note_block.chime", 1.2f, 1.4f);
                        }
                    }

                    // Keep the silver duct-tape visibly stuck on the player's face while Santa walks away!
                    if (faceTapeRef[0] != null && faceTapeRef[0].isValid()) {
                        Location mouthLoc = victim.getEyeLocation().add(vDir.clone().multiply(0.32)).add(0.0, -0.14, 0.0);
                        faceTapeRef[0].teleport(mouthLoc);
                    }

                    if (ticks % 4 == 0) {
                        w.spawnParticle(Particle.SNOWFLAKE, sLoc.clone().add(0.0, 0.5, 0.0), 6, 0.28, 0.25, 0.28, 0.02);
                    }
                    if (ticks % 7 == 0) {
                        w.playSound(sLoc, "minecraft:block.snow.step", 0.85f, 1.0f);
                    }
                }
                // =====================================================================
                // EXACT TICK 120 (6.00 SECONDS) — Финальное исчезновение и очистка!
                // =====================================================================
                else {
                    Location endLoc = santa.getLocation().add(0.0, 1.0, 0.0);
                    w.spawnParticle(Particle.SNOWFLAKE, endLoc, 38, 0.45, 0.75, 0.45, 0.05);
                    w.spawnParticle(Particle.CLOUD, endLoc, 16, 0.35, 0.35, 0.35, 0.03);
                    w.playSound(endLoc, "minecraft:entity.enderman.teleport", 0.85f, 1.35f);
                    cleanupMuteAnimationFor(victimId);
                }
            }
        };

        activeMuteAnimTasks.put(victimId, animTask);
        animTask.runTaskTimer(plugin, 1L, 1L);
    }

    private void cleanupMuteAnimationFor(UUID victimId) {
        BukkitRunnable task = activeMuteAnimTasks.remove(victimId);
        if (task != null) {
            try { task.cancel(); } catch (Throwable ignored) {}
        }
        List<Entity> ents = activeMuteAnimEntities.remove(victimId);
        if (ents != null) {
            for (Entity en : ents) {
                if (en != null) {
                    try { en.remove(); } catch (Throwable ignored) {}
                }
            }
        }
    }

    // =====================================================================
    // v6.1: БАН-АНИМАЦИЯ «МЕШОК ДЕДА МОРОЗА» (полное кино на ~10 секунд):
    //  АКТ 1: Дед Мороз появляется в вихре снега ПРЯМО ПЕРЕД игроком и идёт к нему.
    //  АКТ 2: ХВАТАЕТ игрока, сажает на плечо и прячет в мешок (торчит только голова!).
    //  АКТ 3: Строится волшебный обсидиановый портал; Мороз несёт мешок к порталу.
    //  АКТ 4: Заходит в портал — вспышка, портал исчезает, игрок выкинут с бан-экраном.
    //  Все эффекты временные: блоки портала восстанавливаются, сущности удаляются,
    //  скорость/эффекты игрока возвращаются. Полный фейл-сейф на любой сбой/выход!
    // =====================================================================
    public void playSantaBagBanAnimation(final Player victim, final String staffName, final String timeLabel, final String kickScreen, final Runnable banAction) {
        if (victim == null || !victim.isOnline() || victim.getWorld() == null) return;
        final UUID victimId = victim.getUniqueId();
        cleanupSantaBanAnimationFor(victimId);

        final World w = victim.getWorld();
        final Location startLoc = victim.getLocation().clone();

        ascendingBanPlayers.add(victimId);
        try { victim.leaveVehicle(); } catch (Throwable ignored) {}
        try { victim.setWalkSpeed(0.0f); victim.setFlySpeed(0.0f); } catch (Throwable ignored) {}
        ColorUtil.addPot(victim, "resistance", "DAMAGE_RESISTANCE", 460, 9);
        // v6.1.1: невидимость даётся ТОЛЬКО в момент посадки в мешок — до этого все видят игрока!

        Vector fwd0 = startLoc.getDirection().setY(0.0);
        if (fwd0.lengthSquared() < 0.001) fwd0 = new Vector(0.0, 0.0, 1.0);
        fwd0.normalize();
        final Vector approachDir = fwd0.clone();
        Location santaSpawn = startLoc.clone().add(approachDir.clone().multiply(6.5));
        santaSpawn.setYaw(yawFacing(santaSpawn, startLoc));
        santaSpawn.setPitch(0.0f);
        santaSpawn.setY(groundYAt(w, santaSpawn.getX(), santaSpawn.getY(), santaSpawn.getZ()));

        final List<Entity> ents = new ArrayList<Entity>();
        activeBanAnimEntities.put(victimId, ents);
        activeBanAnimPortals.put(victimId, new HashMap<Block, BlockData>());

        // v6.1.1: НОВОГОДНИЙ портал строится СРАЗУ — никаких пауз посреди анимации!
        final Location portalCenter = buildNewYearPortal(w, startLoc, approachDir, victimId);

        final Skeleton santa;
        try {
            santa = spawnSantaModel(w, santaSpawn, "staff");
            ents.add(santa);
        } catch (Throwable ex) {
            cleanupSantaBanAnimationFor(victimId);
            if (banAction != null) { try { banAction.run(); } catch (Throwable ignored) {} }
            try { victim.kickPlayer(kickScreen); } catch (Throwable ignored) {}
            return;
        }

        w.spawnParticle(Particle.SNOWFLAKE, santaSpawn.clone().add(0.0, 1.2, 0.0), 46, 0.6, 1.0, 0.6, 0.05);
        w.spawnParticle(Particle.CLOUD, santaSpawn.clone().add(0.0, 0.3, 0.0), 16, 0.4, 0.2, 0.4, 0.02);
        w.playSound(santaSpawn, "minecraft:block.bell.use", 1.8f, 0.85f);
        victim.sendTitle(
                ColorUtil.c("&#FF1744&l🎅 ЗА ВАМИ ПРИШЁЛ ДЕД МОРОЗ!"),
                ColorUtil.c("&fСрок: &c" + timeLabel + " &8│ &7Модератор: &e" + staffName),
                2, 60, 8
        );

        BukkitRunnable task = new BukkitRunnable() {
            int ticks = 0;
            int phase = 0;
            int phaseTick = 0;
            int enterTick = -1;
            ArmorStand sack = null;

            @Override
            public void run() {
                try {
                    ticks++;
                    if (!victim.isOnline() || ticks > 300 || !santa.isValid()) {
                        finishUp(true);
                        return;
                    }
                    if (phase == 3) {
                        if (ticks >= enterTick + 12) finishUp(true);
                        return;
                    }

                    if (phase == 0) {
                        // АКТ 1: Дед Мороз ШАГАЕТ к нарушителю нормальными шагами по земле!
                        Location sLoc = santa.getLocation();
                        Vector toV = victim.getLocation().toVector().subtract(sLoc.toVector()).setY(0.0);
                        double dist = toV.length();
                        if (dist > 1.15 && phaseTick < 50) {
                            phaseTick++;
                            Location step = sLoc.clone().add(toV.clone().normalize().multiply(0.34));
                            step.setYaw(yawFacing(step, victim.getLocation()));
                            step.setPitch(0.0f);
                            step.setY(groundYAt(w, step.getX(), sLoc.getY(), step.getZ()));
                            santa.teleport(step);
                            if (ticks % 4 == 0) { try { santa.swingMainHand(); } catch (Throwable ignored) {} }
                            if (ticks % 3 == 0) w.spawnParticle(Particle.SNOWFLAKE, step.clone().add(0.0, 0.22, 0.0), 3, 0.22, 0.08, 0.22, 0.01);
                            if (ticks % 6 == 0) w.playSound(step, "minecraft:block.snow.step", 0.9f, 0.95f);
                            ColorUtil.actionBar(victim, "&#FF5252🎅 Дед Мороз идёт к вам... &8[&f"
                                    + String.format(Locale.US, "%.0f", Double.valueOf(dist)) + " блоков&8]");
                        } else {
                            if (dist > 1.6) {
                                Location snap = victim.getLocation().clone().subtract(approachDir.clone().multiply(0.95));
                                snap.setYaw(yawFacing(snap, victim.getLocation()));
                                snap.setPitch(0.0f);
                                snap.setY(groundYAt(w, snap.getX(), victim.getLocation().getY(), snap.getZ()));
                                santa.teleport(snap);
                            }
                            grabVictim();
                        }
                    } else if (phase == 1) {
                        // АКТ 2: нарушитель ВИДИМЫЙ сидит на плече — Дед разворачивается к порталу!
                        phaseTick++;
                        if (portalCenter != null) {
                            Location sLoc = santa.getLocation();
                            sLoc.setYaw(yawFacing(sLoc, portalCenter));
                            sLoc.setPitch(0.0f);
                            santa.teleport(sLoc);
                        }
                        if (ticks % 4 == 0) { try { santa.swingMainHand(); } catch (Throwable ignored) {} }
                        if (ticks % 6 == 0) w.playSound(santa.getLocation(), "minecraft:block.snow.step", 0.85f, 0.9f);
                        if (victim.isOnline() && !santa.getPassengers().contains(victim)) {
                            try { santa.addPassenger(victim); } catch (Throwable ignored) {}
                        }
                        ColorUtil.actionBar(victim, "&#FFD740🎅 Дед Мороз схватил вас и несёт к мешку!");
                        if (phaseTick >= 16) putInSack();
                    } else if (phase == 2) {
                        // АКТ 3: несёт МЕШОК-МОДЕЛЬ с нарушителем к новогоднему порталу!
                        phaseTick++;
                        if (portalCenter == null) { enterPortal(); return; }
                        Location sLoc = santa.getLocation();
                        Vector toP = portalCenter.toVector().subtract(sLoc.toVector()).setY(0.0);
                        double pd = toP.length();
                        if (pd > 1.15 && phaseTick < 220) {
                            Location step = sLoc.clone().add(toP.clone().normalize().multiply(0.38));
                            step.setYaw(yawFacing(step, portalCenter));
                            step.setPitch(0.0f);
                            step.setY(groundYAt(w, step.getX(), sLoc.getY(), step.getZ()));
                            santa.teleport(step);
                            if (sack != null && sack.isValid()) {
                                Vector face = step.getDirection().setY(0.0);
                                if (face.lengthSquared() < 0.001) face = new Vector(0.0, 0.0, 1.0);
                                Location sl = step.clone().add(face.normalize().multiply(0.62));
                                sl.setY(step.getY() + 0.35 + Math.sin(phaseTick * 0.25) * 0.06);
                                sl.setYaw(step.getYaw());
                                sl.setPitch(10.0f);
                                sack.teleport(sl);
                            }
                            if (ticks % 4 == 0) { try { santa.swingMainHand(); } catch (Throwable ignored) {} }
                            if (ticks % 3 == 0) w.spawnParticle(Particle.SNOWFLAKE, step.clone().add(0.0, 0.22, 0.0), 3, 0.22, 0.08, 0.22, 0.01);
                            if (ticks % 6 == 0) w.playSound(step, "minecraft:block.snow.step", 0.9f, 0.9f);
                            if (ticks % 24 == 0) w.playSound(step, "minecraft:block.bell.use", 0.7f, 1.3f);
                            if (ticks % 8 == 0 && portalCenter != null) {
                                w.spawnParticle(Particle.PORTAL, portalCenter.clone().add(0.0, 1.2, 0.0), 10, 0.35, 0.7, 0.35, 0.2);
                                w.spawnParticle(Particle.SNOWFLAKE, portalCenter.clone().add(0.0, 2.2, 0.0), 6, 0.6, 0.9, 0.6, 0.02);
                            }
                            ColorUtil.actionBar(victim, "&#A855F7🌀 Вас несут в новогодний портал... &8[&f"
                                    + String.format(Locale.US, "%.0f", Double.valueOf(pd)) + " блоков&8]");
                        } else {
                            enterPortal();
                        }
                    }
                } catch (Throwable ex) {
                    finishUp(true);
                }
            }

            void grabVictim() {
                phase = 1;
                phaseTick = 0;
                Location vl = victim.getLocation();
                w.playSound(vl, "minecraft:block.bell.use", 1.6f, 0.9f);
                w.playSound(vl, "minecraft:entity.iron_golem.attack", 1.3f, 0.85f);
                w.playSound(vl, "minecraft:entity.slime.attack", 1.0f, 0.6f);
                w.spawnParticle(Particle.CRIT, vl.clone().add(0.0, 1.2, 0.0), 22, 0.35, 0.4, 0.35, 0.06);
                w.spawnParticle(Particle.SNOWFLAKE, vl.clone().add(0.0, 1.0, 0.0), 28, 0.5, 0.7, 0.5, 0.03);
                try { victim.leaveVehicle(); } catch (Throwable ignored) {}
                try { santa.addPassenger(victim); } catch (Throwable ignored) {}
                victim.sendTitle(
                        ColorUtil.c("&#FF1744&l🎅 ДЕД МОРОЗ СХВАТИЛ ВАС!"),
                        ColorUtil.c("&fОн взвалил вас на плечо и несёт к мешку..."),
                        2, 34, 6
                );
                for (Player near : w.getPlayers()) {
                    if (near.equals(victim)) continue;
                    try {
                        if (near.getLocation().distanceSquared(vl) <= 64.0 * 64.0) {
                            near.sendMessage(ColorUtil.c("&#FF1744&l🎅 [ДЕД МОРОЗ] &fСхватил &c" + victim.getName()
                                    + " &fи взвалил на плечо! &8(&7бан: &e" + timeLabel + "&8)"));
                        }
                    } catch (Throwable ignored) {}
                }
            }

            void putInSack() {
                phase = 2;
                phaseTick = 0;
                try { victim.leaveVehicle(); } catch (Throwable ignored) {}
                Location vl = victim.getLocation().clone();
                // МЕШОК-МОДЕЛЬ: коричневое тело из кожи + голова нарушителя торчит сверху!
                try {
                    sack = spawnSackModel(w, vl, victim);
                    ents.add(sack);
                } catch (Throwable ignored) {}
                ColorUtil.addPot(victim, "invisibility", "INVISIBILITY", 420, 0);
                if (sack != null) {
                    try { sack.addPassenger(victim); } catch (Throwable ignored) {}
                }
                w.playSound(vl, "minecraft:block.wool.place", 1.4f, 0.8f);
                w.playSound(vl, "minecraft:entity.player.burp", 1.2f, 0.7f);
                w.spawnParticle(Particle.POOF, vl.clone().add(0.0, 0.9, 0.0), 26, 0.4, 0.5, 0.4, 0.04);
                w.spawnParticle(Particle.CRIT, vl.clone().add(0.0, 1.1, 0.0), 18, 0.3, 0.4, 0.3, 0.05);
                victim.sendTitle(
                        ColorUtil.c("&#8D6E63&l💰 ВЫ В МЕШКЕ!"),
                        ColorUtil.c("&#FF5252🎅 Дед Мороз несёт вас в новогодний портал... &8(&c" + timeLabel + "&8)"),
                        2, 50, 6
                );
                ColorUtil.actionBar(victim, "&#8D6E63&l💰 Вы сидите в мешке Деда Мороза! Сопротивление бесполезно!");
                for (Player near : w.getPlayers()) {
                    if (near.equals(victim)) continue;
                    try {
                        if (near.getLocation().distanceSquared(vl) <= 64.0 * 64.0) {
                            near.sendMessage(ColorUtil.c("&#FF1744&l🎅 [ДЕД МОРОЗ] &fЗасунул &c" + victim.getName()
                                    + " &fв мешок и уносит в новогодний портал! &8(&7бан: &e" + timeLabel + "&8)"));
                        }
                    } catch (Throwable ignored) {}
                }
            }

            void enterPortal() {
                phase = 3;
                enterTick = ticks;
                Location fx = portalCenter != null ? portalCenter.clone() : santa.getLocation().clone();
                try { santa.teleport(fx); } catch (Throwable ignored) {}
                if (sack != null && sack.isValid()) {
                    try { sack.teleport(fx.clone().add(0.0, 0.15, 0.0)); } catch (Throwable ignored) {}
                }
                w.spawnParticle(Particle.FIREWORK, fx.clone().add(0.0, 1.2, 0.0), 70, 0.5, 1.0, 0.5, 0.35);
                w.spawnParticle(Particle.PORTAL, fx.clone().add(0.0, 1.0, 0.0), 90, 0.45, 1.0, 0.45, 0.45);
                w.spawnParticle(Particle.POOF, fx.clone().add(0.0, 0.8, 0.0), 30, 0.45, 0.7, 0.45, 0.05);
                w.spawnParticle(Particle.SNOWFLAKE, fx.clone().add(0.0, 1.6, 0.0), 40, 0.7, 1.0, 0.7, 0.04);
                w.playSound(fx, "minecraft:entity.firework.rocket.launch", 1.4f, 1.0f);
                w.playSound(fx, "minecraft:entity.firework.rocket.blast", 1.2f, 0.9f);
                w.playSound(fx, "minecraft:block.bell.use", 1.6f, 1.1f);
                w.playSound(fx, "minecraft:block.portal.travel", 1.2f, 1.25f);
                victim.sendTitle(
                        ColorUtil.c("&#A855F7&l🌀 УНЕСЁН В НОВОГОДНИЙ ПОРТАЛ!"),
                        ColorUtil.c("&#FF1744🎅 Дед Мороз забрал вас на &#FFD740" + timeLabel),
                        2, 30, 8
                );
            }

            void finishUp(boolean kickNow) {
                try { cleanupSantaBanAnimationFor(victimId); } catch (Throwable ignored) {}
                if (kickNow) {
                    // v6.1.1: БАН вступает в силу ТОЛЬКО ПОСЛЕ завершения анимации!
                    if (banAction != null) { try { banAction.run(); } catch (Throwable ignored) {} }
                    if (victim.isOnline()) { try { victim.kickPlayer(kickScreen); } catch (Throwable ignored) {} }
                }
                try { cancel(); } catch (Throwable ignored) {}
            }
        };

        activeBanAnimTasks.put(victimId, task);
        task.runTaskTimer(plugin, 0L, 1L);
    }

    /** v6.1.1: МЕШОК-МОДЕЛЬ — коричневое кожаное тело + голова нарушителя торчит сверху! */
    private ArmorStand spawnSackModel(World w, Location loc, final Player victim) {
        return w.spawn(loc, ArmorStand.class, new org.bukkit.util.Consumer<ArmorStand>() {
            @Override
            public void accept(ArmorStand as) {
                as.setPersistent(false);
                as.setVisible(false);
                as.setMarker(false);
                as.setSmall(false);
                as.setBasePlate(false);
                as.setArms(false);
                as.setGravity(false);
                as.setInvulnerable(true);
                as.setSilent(true);
                as.addScoreboardTag("kg_mod_anim");
                EntityEquipment eq = as.getEquipment();
                if (eq != null) {
                    eq.setChestplate(dyedLeather(Material.LEATHER_CHESTPLATE, Color.fromRGB(112, 66, 32)));
                    eq.setLeggings(dyedLeather(Material.LEATHER_LEGGINGS, Color.fromRGB(96, 56, 26)));
                    eq.setHelmet(victimHeadItem(victim));
                    eq.setChestplateDropChance(0.0f);
                    eq.setLeggingsDropChance(0.0f);
                    eq.setHelmetDropChance(0.0f);
                }
            }
        });
    }

    /** v6.1.1: НОВОГОДНИЙ портал — лёд, золото, фонарики-гирлянда, морозное стекло и подарки по бокам! */
    private Location buildNewYearPortal(World w, Location startLoc, Vector approachDir, UUID victimId) {
        try {
            Location base = startLoc.clone().add(approachDir.clone().multiply(-7.0));
            int gy = base.getBlockY();
            for (int yy = base.getBlockY() + 4; yy >= base.getBlockY() - 14; yy--) {
                Block under = w.getBlockAt(base.getBlockX(), yy, base.getBlockZ());
                if (under.getType().isSolid()) {
                    gy = yy + 1;
                    break;
                }
            }
            base.setY(gy);
            boolean alongX = Math.abs(approachDir.getX()) >= Math.abs(approachDir.getZ());
            Map<Block, BlockData> saved = activeBanAnimPortals.get(victimId);
            for (int fw = -2; fw <= 3; fw++) {
                for (int fh = 0; fh <= 4; fh++) {
                    int bx = base.getBlockX() + (alongX ? 0 : fw);
                    int bz = base.getBlockZ() + (alongX ? fw : 0);
                    Block blk = w.getBlockAt(bx, base.getBlockY() + fh, bz);
                    boolean pillar = (fw == -1 || fw == 2);
                    boolean inner = (fw == 0 || fw == 1) && fh >= 1 && fh <= 3;
                    Material m;
                    if ((fw == -2 || fw == 3) && fh == 0) {
                        m = (fw == -2) ? Material.RED_WOOL : Material.GREEN_WOOL;
                    } else if ((fw == -2 || fw == 3) && fh == 1) {
                        m = Material.WHITE_WOOL;
                    } else if (inner) {
                        m = ((fw + fh) % 2 == 0) ? Material.WHITE_STAINED_GLASS : Material.LIGHT_BLUE_STAINED_GLASS;
                    } else if (fh == 4) {
                        m = pillar ? Material.GOLD_BLOCK : Material.SEA_LANTERN;
                    } else if (pillar) {
                        m = (fh == 2) ? Material.RED_WOOL : Material.PACKED_ICE;
                    } else if (fh == 0) {
                        m = Material.GOLD_BLOCK;
                    } else {
                        continue;
                    }
                    if (saved != null && !saved.containsKey(blk)) {
                        saved.put(blk, blk.getBlockData().clone());
                    }
                    blk.setType(m, false);
                }
            }
            Location center = alongX
                    ? new Location(w, base.getBlockX() + 0.5, base.getBlockY() + 2.0, base.getBlockZ() + 1.0)
                    : new Location(w, base.getBlockX() + 1.0, base.getBlockY() + 2.0, base.getBlockZ() + 0.5);
            center.setYaw(yawFacing(center, startLoc));
            w.spawnParticle(Particle.SNOWFLAKE, center.clone(), 40, 0.8, 1.4, 0.8, 0.04);
            w.spawnParticle(Particle.PORTAL, center.clone(), 30, 0.4, 0.9, 0.4, 0.25);
            w.playSound(center, "minecraft:block.bell.use", 1.4f, 1.0f);
            w.playSound(center, "minecraft:block.amethyst_block.chime", 1.2f, 0.9f);
            return center;
        } catch (Throwable t) {
            return null;
        }
    }

    /** v6.1.1: нормальные шаги — моделька всегда стоит НА земле (скан опоры под ногами). */
    private double groundYAt(World w, double x, double y, double z) {
        try {
            int bx = (int) Math.floor(x);
            int bz = (int) Math.floor(z);
            int startY = (int) Math.floor(y) + 1;
            for (int yy = startY; yy >= startY - 4; yy--) {
                Block b = w.getBlockAt(bx, yy, bz);
                if (b.getType().isSolid()) {
                    double top = yy + 1.0;
                    if (top >= y - 3.0 && top <= y + 1.2) return top;
                    return y;
                }
            }
        } catch (Throwable ignored) {}
        return y;
    }

    /** v6.1: полная очистка бан-анимации — сущности, портал (блоки возвращаются!), эффекты и скорость игрока. */
    private void cleanupSantaBanAnimationFor(UUID victimId) {
        BukkitRunnable task = activeBanAnimTasks.remove(victimId);
        if (task != null) {
            try { task.cancel(); } catch (Throwable ignored) {}
        }
        List<Entity> ents = activeBanAnimEntities.remove(victimId);
        if (ents != null) {
            for (Entity en : ents) {
                if (en != null) {
                    try { en.eject(); } catch (Throwable ignored) {}
                    try { en.remove(); } catch (Throwable ignored) {}
                }
            }
        }
        Map<Block, BlockData> portal = activeBanAnimPortals.remove(victimId);
        if (portal != null) {
            for (Map.Entry<Block, BlockData> pe : portal.entrySet()) {
                try {
                    Block blk = pe.getKey();
                    blk.setBlockData(pe.getValue(), false);
                    blk.getWorld().spawnParticle(Particle.POOF, blk.getLocation().add(0.5, 0.5, 0.5), 3, 0.2, 0.2, 0.2, 0.01);
                } catch (Throwable ignored) {}
            }
        }
        ascendingBanPlayers.remove(victimId);
        Player v = Bukkit.getPlayer(victimId);
        if (v != null && v.isOnline()) {
            try { v.leaveVehicle(); } catch (Throwable ignored) {}
            try { v.setWalkSpeed(0.2f); v.setFlySpeed(0.1f); } catch (Throwable ignored) {}
            try { v.removePotionEffect(PotionEffectType.INVISIBILITY); } catch (Throwable ignored) {}
            try { v.removePotionEffect(PotionEffectType.DAMAGE_RESISTANCE); } catch (Throwable ignored) {}
        }
    }

    private float yawFacing(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private ItemStack dyedLeather(Material mat, Color color) {
        ItemStack it = new ItemStack(mat, 1);
        LeatherArmorMeta lam = (LeatherArmorMeta) it.getItemMeta();
        if (lam != null) {
            lam.setColor(color);
            lam.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_DYE);
            it.setItemMeta(lam);
        }
        return it;
    }

    private ItemStack getSantaHeadItem() {
        if (cachedSantaHead != null) return cachedSantaHead.clone();
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        if (sm != null) {
            try {
                String shortName = "KGS_" + SANTA_SKIN_HASH.substring(0, 10);
                PlayerProfile prof = Bukkit.createPlayerProfile(UUID.nameUUIDFromBytes(SANTA_SKIN_HASH.getBytes("UTF-8")), shortName);
                PlayerTextures tex = prof.getTextures();
                tex.setSkin(new URL("http://textures.minecraft.net/texture/" + SANTA_SKIN_HASH));
                prof.setTextures(tex);
                sm.setOwnerProfile(prof);
            } catch (Throwable ignored) {}
            head.setItemMeta(sm);
        }
        cachedSantaHead = head;
        return head.clone();
    }

    private ItemStack getSackItem() {
        if (cachedSackItem != null) return cachedSackItem.clone();
        Material sackMat = ColorUtil.mat("BUNDLE", "BROWN_SHULKER_BOX", "CHEST");
        cachedSackItem = namedItem(sackMat != null ? sackMat : Material.CHEST, "&#8D6E63&l🎒 Мешок Деда Мороза");
        return cachedSackItem.clone();
    }

    private ItemStack victimHeadItem(Player victim) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        try {
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            if (sm != null) {
                sm.setOwningPlayer(victim);
                head.setItemMeta(sm);
            }
        } catch (Throwable ignored) {}
        return head;
    }

    /**
     * v6.1: ЕДИНАЯ ЖИВАЯ 3D-МОДЕЛЬ ДЕДА МОРОЗА (анимированный персонаж вместо стойки!) —
     * настоящий скин- head, красная шуба из крашеной кожи, посох/скотч/мешок в руках.
     * Используется и в мут-анимации, и в бан-анимации с мешком!
     */
    private Skeleton spawnSantaModel(World w, Location loc, final String handMode) {
        return w.spawn(loc, Skeleton.class, new org.bukkit.util.Consumer<Skeleton>() {
            @Override
            public void accept(Skeleton zo) {
                zo.setPersistent(false);
                zo.addScoreboardTag("kg_mod_anim");
                zo.setSilent(true);
                zo.setInvulnerable(true);
                zo.setGravity(false);
                zo.setCanPickupItems(false);
                zo.setRemoveWhenFarAway(false);
                try { zo.setAI(false); } catch (Throwable ignored) {}
                try { zo.setCollidable(false); } catch (Throwable ignored) {}
                try { zo.setVisualFire(false); } catch (Throwable ignored) {}
                zo.setCustomName(ColorUtil.c("&#FF1744&l🎅 ДЕД МОРОЗ"));
                zo.setCustomNameVisible(true);
                EntityEquipment eq = zo.getEquipment();
                if (eq != null) {
                    eq.setHelmet(getSantaHeadItem());
                    eq.setChestplate(dyedLeather(Material.LEATHER_CHESTPLATE, Color.fromRGB(212, 24, 36)));
                    eq.setLeggings(dyedLeather(Material.LEATHER_LEGGINGS, Color.fromRGB(190, 20, 32)));
                    eq.setBoots(dyedLeather(Material.LEATHER_BOOTS, Color.fromRGB(28, 28, 32)));
                    if ("tape".equals(handMode)) {
                        eq.setItemInMainHand(namedItem(Material.IRON_BLOCK, "&#CFD8DC&l🧻 Серебристый скотч"));
                        eq.setItemInOffHand(namedItem(Material.SHEARS, "&#FFD740&l✂ Ножницы Деда Мороза"));
                    } else if ("bag".equals(handMode)) {
                        eq.setItemInMainHand(getSackItem());
                        eq.setItemInOffHand(namedItem(Material.IRON_BLOCK, "&#CFD8DC&l🧻 Скотч"));
                    } else {
                        eq.setItemInMainHand(namedItem(Material.BLAZE_ROD, "&#FFD740&l✨ Волшебный Посох"));
                    }
                    eq.setHelmetDropChance(0.0f);
                    eq.setChestplateDropChance(0.0f);
                    eq.setLeggingsDropChance(0.0f);
                    eq.setBootsDropChance(0.0f);
                    eq.setItemInMainHandDropChance(0.0f);
                    eq.setItemInOffHandDropChance(0.0f);
                }
            }
        });
    }

    public boolean handleTimedBanWithAscension(CommandSender sender, String[] args) {
        if (!isStaff(sender)) {
            sender.sendMessage(ColorUtil.c("&#FF1744✖ У вас нет прав на выдачу бана!"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ColorUtil.c("&#FFD740Использование: &f/ban <игрок> [время] [причина] &7(Пример: &f/ban Cheater 30d KillAura&7)"));
            return true;
        }
        String targetName = args[0];
        if (!canPunishTarget(sender, targetName)) return true;

        long durMs;
        String reason;
        if (args.length >= 2) {
            long parsed = parseDurationMs(args[1]);
            if (parsed != Long.MIN_VALUE) {
                durMs = parsed;
                reason = args.length >= 3 ? joinArgs(args, 2) : "Нарушение правил сервера";
            } else {
                durMs = 30L * 86_400_000L;
                reason = joinArgs(args, 1);
            }
        } else {
            durMs = 30L * 86_400_000L;
            reason = "Нарушение правил сервера";
        }
        String staffName = sender instanceof Player ? sender.getName() : "Консоль";
        plugin.getStore().appendPunishHistory(targetName, "&#FF1744[БАН]", staffName, formatDurationRu(durMs), reason);
        executeBanWithSkyAnimation(staffName, sender instanceof Player, targetName, durMs, reason);
        return true;
    }

    public void executeBanWithSkyAnimation(final String staffName, boolean countForStaff, final String targetName, final long durMs, final String reason) {
        final String timeLabel = formatDurationRu(durMs);
        // v6.1.1: бан ВСТУПАЕТ В СИЛУ ТОЛЬКО ПОСЛЕ анимации — во время анимации игрок ещё играет!
        final Runnable banAction = new Runnable() {
            @Override
            public void run() {
                plugin.getStore().ban(targetName, durMs, reason, staffName);
                if (countForStaff) {
                    plugin.getStore().addStaffBan(staffName);
                }
                Bukkit.broadcastMessage(ColorUtil.c("&#FF1744&l🔨 [БАН] &fМодератор &#FFD740" + staffName
                        + " &fзабанил игрока &c" + targetName + " &fна &#FF8A80" + timeLabel + "&f. Причина: &e" + reason));
            }
        };

        final Player victim = Bukkit.getPlayerExact(targetName);
        if (victim == null || !victim.isOnline()) {
            banAction.run(); // оффлайн — анимации нет, бан сразу
            return;
        }

        // Remove active check if any
        CheckSession cs = activeChecks.remove(victim.getUniqueId());
        if (cs != null) {
            try { cs.bossBar.removeAll(); } catch (Throwable ignored) {}
        }

        // 2. v6.1: БАН-АНИМАЦИЯ «МЕШОК ДЕДА МОРОЗА» — приходит, хватает, кладёт в мешок и уносит в портал!
        playSantaBagBanAnimation(victim, staffName, timeLabel, buildBanKickScreen(staffName, timeLabel, reason), banAction);
    }

    private void spawnBanFireworkBurst(Location loc) {
        ColorUtil.spawnFirework(loc);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void sendClickableButtons(Player target, String prefixText, String[][] buttons) {
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
            StringBuilder fb = new StringBuilder(prefixText);
            for (String[] b : buttons) fb.append(b[0]).append(" (").append(b[1]).append(") ");
            target.sendMessage(fb.toString());
        }
    }

    public String buildBanKickScreen(String staff, String timeLabel, String reason) {
        return ColorUtil.c(
                "&#FF1744&l❄ KINGGRIEF — АККАУНТ ЗАБЛОКИРОВАН ❄\n\n"
                        + "&7Модератор: &#FFD740" + staff + "\n"
                        + "&7Срок блокировки: &#FF5252" + timeLabel + "\n"
                        + "&7Причина: &f" + reason + "\n\n"
                        + "&7Разбан и обжалование: &#00E5FFkinggrief.vercel.app"
        );
    }

    public String buildIpBanKickScreen(String staff, String timeLabel, String reason, String origTarget) {
        return ColorUtil.c(
                "&#FF1744&l❄ KINGGRIEF — ВАШ IP-АДРЕС ЗАБЛОКИРОВАН ❄\n\n"
                        + "&7Основание (аккаунт): &#FF8A80" + origTarget + "\n"
                        + "&7Модератор: &#FFD740" + staff + "\n"
                        + "&7Срок блокировки IP: &#FF5252" + timeLabel + "\n"
                        + "&7Причина: &f" + reason + "\n\n"
                        + "&7Разбан и обжалование: &#00E5FFkinggrief.vercel.app"
        );
    }

    private boolean handleUnban(CommandSender sender, String[] args) {
        if (!isStaff(sender)) {
            sender.sendMessage(ColorUtil.c("&#FF1744✖ Недостаточно прав!"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ColorUtil.c("&eИспользование: &f/unban <игрок>"));
            return true;
        }
        plugin.getStore().unban(args[0]);
        Bukkit.broadcastMessage(ColorUtil.c("&#00E676🔓 Модератор &f" + sender.getName() + " &#00E676разбанил игрока &f" + args[0] + "&#00E676."));
        return true;
    }

    // ================= v5.9: МУТ ПО IP (/muteip, /unmuteip), БАН ПО IP (/banip, /unbanip), ТВИНКИ (/dupeip), ВАРНЫ (/warn), ИСТОРИЯ (/history) =================
    private boolean handleTimedIpMute(CommandSender sender, String[] args) {
        if (!isStaff(sender)) {
            sender.sendMessage(ColorUtil.c("&#FF1744✖ У вас нет прав на выдачу мута по IP!"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ColorUtil.c("&#FFD740Использование: &f/muteip <игрок | IP> [время] [причина] &7(Пример: &f/muteip Steve 2h Обход мута с твинка&7)"));
            return true;
        }
        String rawTarget = args[0];
        if (!DataStore.looksLikeIp(rawTarget) && !canPunishTarget(sender, rawTarget)) return true;

        String ip = plugin.getStore().resolveIpForTarget(rawTarget);
        if (ip.isEmpty()) {
            sender.sendMessage(ColorUtil.c("&#FF1744✖ Не удалось найти IP-адрес игрока &f" + rawTarget + " &#FF1744(игрок ещё ни разу не заходил на сервер)!"));
            return true;
        }
        boolean isOwnerViewer = !(sender instanceof Player) || "owner".equalsIgnoreCase(plugin.getRanks().getRank((Player) sender).id);
        if (!isOwnerViewer && ipBelongsToOwner(ip)) {
            sender.sendMessage(ColorUtil.c("&#FF1744✖ Запрещено выдавать IP-мут на IP владельца сервера OWNER!"));
            return true;
        }

        long durMs = 60L * 60_000L; // default 1h
        String reason = "Обход мута / Нарушение правил чата";
        if (args.length >= 2) {
            long parsed = parseDurationMs(args[1]);
            if (parsed != Long.MIN_VALUE) {
                durMs = parsed;
                reason = args.length >= 3 ? joinArgs(args, 2) : reason;
            } else {
                reason = joinArgs(args, 1);
            }
        }
        String staffName = sender instanceof Player ? sender.getName() : "Консоль";
        String targetLabel = DataStore.looksLikeIp(rawTarget) ? maskIp(ip, false) : rawTarget;

        plugin.getStore().muteIp(ip, targetLabel, durMs, reason, staffName);
        if (!DataStore.looksLikeIp(rawTarget)) {
            plugin.getStore().mute(rawTarget, durMs, reason, staffName);
            plugin.getStore().appendPunishHistory(rawTarget, "&#FF5252[МУТ-IP]", staffName, formatDurationRu(durMs), reason);
        }
        if (sender instanceof Player) plugin.getStore().addStaffMute(staffName);

        String timeLabel = formatDurationRu(durMs);
        Bukkit.broadcastMessage(ColorUtil.c("&#FF5252&l🔇 [МУТ ПО IP] &fМодератор &#FFD740" + staffName
                + " &fзамутил по IP &c" + targetLabel + " &fна &#80D8FF" + timeLabel + "&f. Причина: &e" + reason));

        for (Player on : Bukkit.getOnlinePlayers()) {
            if (ip.equalsIgnoreCase(plugin.getStore().getPlayerIp(on))
                    && !"owner".equalsIgnoreCase(plugin.getRanks().getRank(on).id)) {
                playSantaTapeMuteAnimation(on, timeLabel, reason);
            }
        }
        return true;
    }

    private boolean handleUnmuteIp(CommandSender sender, String[] args) {
        if (!isStaff(sender)) {
            sender.sendMessage(ColorUtil.c("&#FF1744✖ Недостаточно прав!"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ColorUtil.c("&eИспользование: &f/unmuteip <игрок | IP>"));
            return true;
        }
        boolean removedIp = plugin.getStore().unmuteIp(args[0]);
        if (!DataStore.looksLikeIp(args[0])) plugin.getStore().unmute(args[0]);
        if (!removedIp) {
            sender.sendMessage(ColorUtil.c("&eАктивный IP-мут для &f" + args[0] + " &eне найден (обычный мут также снят)."));
            return true;
        }
        Bukkit.broadcastMessage(ColorUtil.c("&#00E676🔊 Модератор &f" + sender.getName() + " &#00E676снял IP-мут с &f" + args[0] + "&#00E676."));
        return true;
    }

    private boolean handleTimedIpBan(CommandSender sender, String[] args) {
        if (!isStaff(sender)) {
            sender.sendMessage(ColorUtil.c("&#FF1744✖ У вас нет прав на выдачу бана по IP!"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ColorUtil.c("&#FFD740Использование: &f/banip <игрок | IP> [время] [причина] &7(Пример: &f/banip Cheater 30d Обход бана твинками&7)"));
            return true;
        }
        String rawTarget = args[0];
        if (!DataStore.looksLikeIp(rawTarget) && !canPunishTarget(sender, rawTarget)) return true;

        final String ip = plugin.getStore().resolveIpForTarget(rawTarget);
        if (ip.isEmpty()) {
            sender.sendMessage(ColorUtil.c("&#FF1744✖ Не удалось найти IP-адрес игрока &f" + rawTarget + " &#FF1744(игрок ещё ни разу не заходил на сервер)!"));
            return true;
        }
        boolean isOwnerViewer = !(sender instanceof Player) || "owner".equalsIgnoreCase(plugin.getRanks().getRank((Player) sender).id);
        if (!isOwnerViewer && ipBelongsToOwner(ip)) {
            sender.sendMessage(ColorUtil.c("&#FF1744✖ Запрещено выдавать IP-бан на IP владельца сервера OWNER!"));
            return true;
        }

        long durMs = 0L; // default permanent for IP-ban unless duration specified
        String reason = "Обход блокировки / Читы с твинков";
        if (args.length >= 2) {
            long parsed = parseDurationMs(args[1]);
            if (parsed != Long.MIN_VALUE) {
                durMs = parsed;
                reason = args.length >= 3 ? joinArgs(args, 2) : reason;
            } else {
                reason = joinArgs(args, 1);
            }
        }
        final String staffName = sender instanceof Player ? sender.getName() : "Консоль";
        final String targetLabel = DataStore.looksLikeIp(rawTarget) ? maskIp(ip, false) : rawTarget;
        final String timeLabel = formatDurationRu(durMs);
        final long finalDurMs = durMs;
        final String finalReasonMsg = reason;

        // v6.1.1: IP-блок сразу (твинки не rejoins), а бан по нику иbroadcast — ТОЛЬКО после анимации!
        plugin.getStore().banIp(ip, targetLabel, durMs, reason, staffName);
        final Runnable deferredBan = new Runnable() {
            @Override
            public void run() {
                if (!DataStore.looksLikeIp(rawTarget)) {
                    plugin.getStore().ban(rawTarget, finalDurMs, finalReasonMsg, staffName);
                    plugin.getStore().appendPunishHistory(rawTarget, "&#FF1744[БАН-IP]", staffName, timeLabel, finalReasonMsg);
                }
                if (sender instanceof Player) plugin.getStore().addStaffBan(staffName);
                Bukkit.broadcastMessage(ColorUtil.c("&#FF1744&l🔨 [БАН ПО IP] &fМодератор &#FFD740" + staffName
                        + " &fзаблокировал по IP &c" + targetLabel + " &fна &#FF8A80" + timeLabel + "&f. Причина: &e" + finalReasonMsg));
            }
        };

        // Kick ALL online accounts sharing this IP (with sky animation on main target if online!)
        final String finalReason = reason;
        Player primaryOnline = DataStore.looksLikeIp(rawTarget) ? null : Bukkit.getPlayerExact(rawTarget);
        for (Player on : new ArrayList<Player>(Bukkit.getOnlinePlayers())) {
            if (!ip.equalsIgnoreCase(plugin.getStore().getPlayerIp(on))) continue;
            if ("owner".equalsIgnoreCase(plugin.getRanks().getRank(on).id)) continue;
            if (primaryOnline != null && on.equals(primaryOnline)) {
                executeBanSkyAnimationOnly(on, staffName, timeLabel, buildIpBanKickScreen(staffName, timeLabel, finalReason, targetLabel), deferredBan);
            } else {
                on.kickPlayer(buildIpBanKickScreen(staffName, timeLabel, finalReason, targetLabel));
            }
        }
        return true;
    }

    private void executeBanSkyAnimationOnly(final Player victim, final String staffName, final String timeLabel, final String kickScreen, final Runnable banAction) {
        CheckSession cs = activeChecks.remove(victim.getUniqueId());
        if (cs != null) {
            try { cs.bossBar.removeAll(); } catch (Throwable ignored) {}
        }
        // v6.1: та же киношная анимация с мешком Деда Мороза (для IP-бана)!
        playSantaBagBanAnimation(victim, staffName, timeLabel, kickScreen, banAction);
    }

    private boolean handleUnbanIp(CommandSender sender, String[] args) {
        if (!isStaff(sender)) {
            sender.sendMessage(ColorUtil.c("&#FF1744✖ Недостаточно прав!"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ColorUtil.c("&eИспользование: &f/unbanip <игрок | IP>"));
            return true;
        }
        boolean removedIp = plugin.getStore().unbanIp(args[0]);
        if (!DataStore.looksLikeIp(args[0])) plugin.getStore().unban(args[0]);
        if (!removedIp) {
            sender.sendMessage(ColorUtil.c("&eАктивный IP-бан для &f" + args[0] + " &eне найден (обычный бан также снят)."));
            return true;
        }
        Bukkit.broadcastMessage(ColorUtil.c("&#00E676🔓 Модератор &f" + sender.getName() + " &#00E676разблокировал IP &f" + args[0] + "&#00E676."));
        return true;
    }

    private boolean handleKick(CommandSender sender, String[] args) {
        if (!isStaff(sender)) {
            sender.sendMessage(ColorUtil.c("&#FF1744✖ У вас нет прав на кик игроков!"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ColorUtil.c("&eИспользование: &f/kick <игрок> [причина]"));
            return true;
        }
        String targetName = args[0];
        if (!canPunishTarget(sender, targetName)) return true;
        Player tp = Bukkit.getPlayerExact(targetName);
        if (tp == null || !tp.isOnline()) {
            sender.sendMessage(ColorUtil.c("&cИгрок &f" + targetName + " &cсейчас не в сети!"));
            return true;
        }
        String reason = args.length >= 2 ? joinArgs(args, 1) : "Нарушение правил сервера";
        String staffName = sender instanceof Player ? sender.getName() : "Консоль";
        plugin.getStore().appendPunishHistory(targetName, "&#FFAB40[КИК]", staffName, "Мгновенно", reason);
        tp.kickPlayer(ColorUtil.c("&#FF9100&l❄ KINGGRIEF — ВЫ КИКНУТЫ С СЕРВЕРА ❄\n\n&7Модератор: &#FFD740" + staffName + "\n&7Причина: &f" + reason));
        Bukkit.broadcastMessage(ColorUtil.c("&#FF9100👢 [КИК] &fМодератор &#FFD740" + staffName + " &fкикнул &c" + tp.getName() + "&f. Причина: &e" + reason));
        return true;
    }

    private boolean handleDupeIp(CommandSender sender, String[] args) {
        if (!isStaff(sender)) {
            sender.sendMessage(ColorUtil.c("&#FF1744✖ Доступно только персоналу сервера!"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ColorUtil.c("&eИспользование: &f/dupeip <игрок | IP> &7— проверка твинков на одном IP"));
            return true;
        }
        String ip = plugin.getStore().resolveIpForTarget(args[0]);
        if (ip.isEmpty()) {
            sender.sendMessage(ColorUtil.c("&cДанные по IP для &f" + args[0] + " &cне найдены."));
            return true;
        }
        boolean isOwnerViewer = !(sender instanceof Player) || "owner".equalsIgnoreCase(plugin.getRanks().getRank((Player) sender).id);
        List<String> accs = plugin.getStore().getAccountsByIp(ip);
        String ipLabel = maskIp(ip, isOwnerViewer);
        boolean ipBanned = plugin.getStore().getActiveIpBanReason(ip) != null;
        boolean ipMuted = plugin.getStore().getIpMuteRemainingSec(ip) > 0;

        sender.sendMessage(ColorUtil.c("&#38BDF8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        sender.sendMessage(ColorUtil.c(" &#38BDF8&l🔎 ПРОВЕРКА ТВИНКОВ (/dupeip): &f" + args[0] + " &8[" + ipLabel + "]"));
        sender.sendMessage(ColorUtil.c(" &7Статус IP: " + (ipBanned ? "&#FF1744ЗАБАНЕН ПО IP" : (ipMuted ? "&#FF9100В МУТЕ ПО IP" : "&#00E676Чист"))
                + " &8│ &7Найдено аккаунтов: &e" + accs.size()));
        StringBuilder sb = new StringBuilder(" ");
        for (int i = 0; i < accs.size(); i++) {
            String acc = accs.get(i);
            if (i > 0) sb.append("&8, ");
            boolean banned = plugin.getStore().getActiveBanReason(acc) != null;
            boolean muted = plugin.getStore().getMuteRemainingSec(acc) > 0;
            boolean online = Bukkit.getPlayerExact(acc) != null;
            String col = banned ? "&#FF1744" : (muted ? "&#FF9100" : (online ? "&#00E676" : "&7"));
            String tag = banned ? " [БАН]" : (muted ? " [МУТ]" : (online ? " [ОНЛАЙН]" : ""));
            sb.append(col).append(acc).append(tag);
        }
        sender.sendMessage(ColorUtil.c(sb.toString()));
        sender.sendMessage(ColorUtil.c(" &8Легенда: &#00E676Зелёный=Онлайн &8| &7Серый=Оффлайн &8| &#FF9100Оранж=Мут &8| &#FF1744Красный=Бан"));
        sender.sendMessage(ColorUtil.c("&#38BDF8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        return true;
    }

    private boolean handleWarn(CommandSender sender, String[] args) {
        if (!isStaff(sender)) {
            sender.sendMessage(ColorUtil.c("&#FF1744✖ У вас нет прав на выдачу предупреждений!"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ColorUtil.c("&eИспользование: &f/warn <игрок> <причина> &7(2/3 = авто-мут 30м, 3/3 = авто-бан 3д!)"));
            return true;
        }
        String target = args[0];
        if (!canPunishTarget(sender, target)) return true;
        String reason = joinArgs(args, 1);
        String staffName = sender instanceof Player ? sender.getName() : "Консоль";

        int count = plugin.getStore().addWarn(target, staffName, reason);
        plugin.getStore().appendPunishHistory(target, "&#FFD740[ВАРН " + count + "/3]", staffName, "-", reason);
        Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l⚠ [ПРЕДУПРЕЖДЕНИЕ " + count + "/3] &fМодератор &#00E676" + staffName
                + " &fвыдал варн &c" + target + "&f: &e" + reason));

        Player tp = Bukkit.getPlayerExact(target);
        if (tp != null) {
            ColorUtil.sound(tp, "block.anvil.place", 0.9f, 0.85f);
            tp.sendTitle(ColorUtil.c("&#FFD740&l⚠ ВАРН [" + count + "/3]"), ColorUtil.c("&fПричина: &e" + reason), 5, 55, 10);
        }
        if (count == 2) {
            plugin.getStore().mute(target, 30L * 60_000L, "2/3 предупреждений (" + reason + ")", "Авто-Система");
            Bukkit.broadcastMessage(ColorUtil.c("&#FF5252🔇 &fИгрок &c" + target + " &fполучил &e2/3 варнов &f→ Авто-Мут на &b30 мин.&f!"));
            if (tp != null && tp.isOnline()) {
                playSantaTapeMuteAnimation(tp, "30 мин.", "2/3 предупреждений (" + reason + ")");
            }
        } else if (count >= 3) {
            plugin.getStore().clearWarns(target);
            executeBanWithSkyAnimation("Авто-Система (3/3 Варнов)", false, target, 3L * 86_400_000L, "Достигнут лимит 3/3 предупреждений (" + reason + ")");
        }
        return true;
    }

    private boolean handleUnwarn(CommandSender sender, String[] args) {
        if (!isStaff(sender)) {
            sender.sendMessage(ColorUtil.c("&#FF1744✖ Недостаточно прав!"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ColorUtil.c("&eИспользование: &f/unwarn <игрок>"));
            return true;
        }
        int left = plugin.getStore().removeLastWarn(args[0]);
        sender.sendMessage(ColorUtil.c("&#00E676✔ Снято последнее предупреждение у &f" + args[0] + " &7(Осталось: &e" + left + "/3&7)."));
        return true;
    }

    private boolean handleWarnsList(CommandSender sender, String[] args) {
        String target = args.length >= 1 ? args[0] : sender.getName();
        List<String> list = plugin.getStore().getWarns(target);
        sender.sendMessage(ColorUtil.c("&#FFD740&l⚠ Предупреждения игрока &f" + target + " &8(&e" + list.size() + "/3&8):"));
        if (list.isEmpty()) {
            sender.sendMessage(ColorUtil.c(" &aУ игрока нет активных предупреждений!"));
        } else {
            for (int i = 0; i < list.size(); i++) {
                sender.sendMessage(ColorUtil.c(" &e#" + (i + 1) + " " + list.get(i)));
            }
        }
        return true;
    }

    private boolean handleHistory(CommandSender sender, String[] args) {
        if (!isStaff(sender)) {
            sender.sendMessage(ColorUtil.c("&#FF1744✖ Доступно только персоналу сервера!"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ColorUtil.c("&eИспользование: &f/history <игрок> &7— досье наказаний игрока"));
            return true;
        }
        String target = args[0];
        String ip = plugin.getStore().resolveIpForTarget(target);
        String activeBan = plugin.getStore().getActiveBanReason(target);
        String activeIpBan = ip.isEmpty() ? null : plugin.getStore().getActiveIpBanReason(ip);
        long muteSec = plugin.getStore().getMuteRemainingSec(target);
        long ipMuteSec = ip.isEmpty() ? 0L : plugin.getStore().getIpMuteRemainingSec(ip);
        int warns = plugin.getStore().getWarns(target).size();
        List<String> hist = plugin.getStore().getPunishHistory(target);

        sender.sendMessage(ColorUtil.c("&#FF1744&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        sender.sendMessage(ColorUtil.c(" &#FFD740&l📋 ДОСЬЕ НАКАЗАНИЙ ИГРОКА: &f" + target));
        sender.sendMessage(ColorUtil.c(" &7Активный Бан: " + (activeBan != null ? "&#FF1744Да (" + activeBan + ")" : "&#00E676Нет")
                + " &8│ &7IP-Бан: " + (activeIpBan != null ? "&#FF1744Да (" + activeIpBan + ")" : "&#00E676Нет")));
        sender.sendMessage(ColorUtil.c(" &7Активный Мут: " + (muteSec > 0 ? "&#FF9100Да" : "&#00E676Нет")
                + " &8│ &7IP-Мут: " + (ipMuteSec > 0 ? "&#FF9100Да" : "&#00E676Нет")
                + " &8│ &7Варны: &e" + warns + "/3"));
        if (hist.isEmpty()) {
            sender.sendMessage(ColorUtil.c(" &7История прошлых наказаний пуста."));
        } else {
            for (int i = 0; i < Math.min(10, hist.size()); i++) {
                sender.sendMessage(ColorUtil.c(" " + hist.get(i)));
            }
        }
        sender.sendMessage(ColorUtil.c("&#FF1744&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        return true;
    }

    private boolean handleClearChat(CommandSender sender) {
        if (!isStaff(sender)) {
            sender.sendMessage(ColorUtil.c("&#FF1744✖ Доступно только модерации!"));
            return true;
        }
        for (Player on : Bukkit.getOnlinePlayers()) {
            if (!isStaff(on)) {
                for (int i = 0; i < 90; i++) on.sendMessage("");
            }
        }
        Bukkit.broadcastMessage(ColorUtil.c("&#80D8FF&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        Bukkit.broadcastMessage(ColorUtil.c(" &#00E5FF&l🧹 ЧАТ ОЧИЩЕН &fмодератором &#FFD740" + sender.getName() + "&f!"));
        Bukkit.broadcastMessage(ColorUtil.c(" &7Соблюдайте правила общения · Discord: &#5865F2https://discord.gg/7Cugj4X3eU"));
        Bukkit.broadcastMessage(ColorUtil.c("&#80D8FF&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        return true;
    }

    private boolean handleSlowChat(CommandSender sender, String[] args) {
        if (!isStaff(sender)) {
            sender.sendMessage(ColorUtil.c("&#FF1744✖ Доступно только модерации!"));
            return true;
        }
        if (args.length < 1 || "off".equalsIgnoreCase(args[0]) || "0".equals(args[0])) {
            slowChatSeconds = 0;
            Bukkit.broadcastMessage(ColorUtil.c("&#00E676💬 Медленный режим чата выключен модератором &f" + sender.getName() + "&#00E676!"));
            return true;
        }
        int sec;
        try { sec = Math.max(1, Math.min(120, Integer.parseInt(args[0]))); } catch (Exception ex) { sec = 5; }
        slowChatSeconds = sec;
        Bukkit.broadcastMessage(ColorUtil.c("&#FFD740⏱ [МЕДЛЕННЫЙ ЧАТ] &fМодератор &#00E676" + sender.getName()
                + " &fустановил задержку чата: &e1 сообщение в " + sec + " сек.&f!"));
        return true;
    }

    private boolean handleStaffOnlineList(CommandSender sender) {
        List<Player> list = new ArrayList<Player>();
        for (Player on : Bukkit.getOnlinePlayers()) {
            RankManager.RankDef r = plugin.getRanks().getRank(on);
            if (r.weight >= 200 && !plugin.getStore().isVanished(on)) {
                list.add(on);
            }
        }
        sender.sendMessage(ColorUtil.c("&#00E676&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        sender.sendMessage(ColorUtil.c(" &#00E676&l🛡 ПЕРСОНАЛ КП В СЕТИ (&f" + list.size() + "&#00E676&l):"));
        if (list.isEmpty()) {
            sender.sendMessage(ColorUtil.c(" &7Сейчас нет видимых сотрудников КП онлайн. Вызвать проверку: &f/спек"));
        } else {
            for (Player st : list) {
                String badge = plugin.getRanks().getAnimatedBadge(plugin.getRanks().getRank(st), 0);
                boolean shift = isOnShift(st);
                sender.sendMessage(ColorUtil.c(" &8• " + badge + " &f" + st.getName()
                        + (shift ? " &8[&#00E676На дежурстве /sw&8]" : " &8[&7В сети&8]")));
            }
        }
        sender.sendMessage(ColorUtil.c("&#00E676&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        return true;
    }

    // ================= 4. /спек | /spek — НАДПИСЬ НА ЭКРАНЕ У МОДЕРАЦИИ «ИГРОК ПРОСИТ СПЕК» =================
    public boolean handleSpekCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Команда только для игроков.");
            return true;
        }
        Player p = (Player) sender;

        // Staff subcommands: `/spek tp <nick>` and `/spek back`
        if (args.length >= 1 && isStaff(p)) {
            if ("back".equalsIgnoreCase(args[0])) {
                SpekReturnState st = spekReturns.remove(p.getUniqueId());
                if (st == null) {
                    p.setGameMode(GameMode.SURVIVAL);
                    p.teleport(plugin.getSpawnEngine().getSpawnArrival());
                } else {
                    p.teleport(st.prevLoc);
                    p.setGameMode(st.prevMode);
                }
                p.sendMessage(ColorUtil.c("&#00E676✔ Вы вышли из режима слежки (/spek back)."));
                return true;
            }
            if ("tp".equalsIgnoreCase(args[0]) && args.length >= 2) {
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null || !target.isOnline()) {
                    p.sendMessage(ColorUtil.c("&cИгрок уже вышел из сети."));
                    return true;
                }
                if (!spekReturns.containsKey(p.getUniqueId())) {
                    spekReturns.put(p.getUniqueId(), new SpekReturnState(p.getLocation(), p.getGameMode()));
                }
                p.setGameMode(GameMode.SPECTATOR);
                p.teleport(target.getLocation().add(0, 1.5, 0));
                plugin.getStore().addStaffSpek(p.getName());
                p.sendMessage(ColorUtil.c("&#00E676👁 Вы начали слежку за &f" + target.getName()
                        + " &#00E676(+1 в /sw status)! Проверить: &e/check " + target.getName() + " &8│ &7Вернуться: &e/spek back"));
                return true;
            }
        }

        // Player requesting moderator spectate (/спек or /spek [suspect] [reason])
        long now = System.currentTimeMillis();
        Long cd = spekCooldowns.get(p.getUniqueId());
        if (cd != null && now < cd.longValue() && !isStaff(p)) {
            long wait = (cd.longValue() - now) / 1000L + 1L;
            p.sendMessage(ColorUtil.c("&eПодождите &f" + wait + " сек. &eперед повторным вызовом модерации!"));
            return true;
        }
        spekCooldowns.put(p.getUniqueId(), Long.valueOf(now + 20_000L));

        String suspectName = null;
        String extraReason = "";
        if (args.length >= 1) {
            Player maybeTarget = Bukkit.getPlayer(args[0]);
            if (maybeTarget != null) {
                suspectName = maybeTarget.getName();
                if (args.length >= 2) extraReason = joinArgs(args, 1);
            } else {
                extraReason = joinArgs(args, 0);
            }
        }

        String focusTarget = suspectName != null ? suspectName : p.getName();
        String subText = suspectName != null
                ? "&fИгрок &a" + p.getName() + " &fпросит спек на &c" + suspectName
                : "&fИгрок &a" + p.getName() + " &fпросит спек!";

        int notifiedStaff = 0;
        for (Player on : Bukkit.getOnlinePlayers()) {
            if (!isStaff(on)) continue;
            notifiedStaff++;
            // BIG ON-SCREEN MESSAGE FOR MODERATORS: «Игрок просит спек»!
            on.sendTitle(
                    ColorUtil.c("&#FFD740&l👁 ИГРОК ПРОСИТ СПЕК"),
                    ColorUtil.c(subText),
                    5, 70, 15
            );
            ColorUtil.actionBar(on, "&#FFD740👁 Игрок &a" + p.getName() + " &#FFD740просит спек! Нажмите в чате для телепорта!");
            ColorUtil.sound(on, "block.note_block.bell", 1.2f, 1.35f);

            sendClickableButtons(on,
                    ColorUtil.c("&#FFD740&l[СПЕК] &fИгрок &#00E676" + p.getName()
                            + " &fпросит спек" + (suspectName != null ? " на &c" + suspectName : "")
                            + (extraReason.isEmpty() ? "" : " &7(" + extraReason + ")") + " "),
                    new String[][]{
                            new String[]{ColorUtil.c("&#00E676&l[👁 СЛЕДИТЬ] "), "/spek tp " + focusTarget},
                            new String[]{ColorUtil.c("&#FF5252&l[🔍 ПРОВЕРКА]"), "/check " + focusTarget}
                    });
        }

        ColorUtil.sound(p, "entity.experience_orb.pickup", 1.0f, 1.3f);
        p.sendMessage(ColorUtil.c("&#00E676✔ Запрос на спек отправлен модерации"
                + (notifiedStaff > 0 ? " (&aонлайн модераторов: " + notifiedStaff + "&a)" : " &7(сейчас свободных модераторов нет, нейро-античит KingАЧ ведёт слежку и сам забанит читера)") + "!"));
        return true;
    }

    // ================= GUI CLICK HANDLER =================
    public boolean handleGuiClick(InventoryClickEvent e, String title) {
        if (!(e.getWhoClicked() instanceof Player)) return false;
        Player staff = (Player) e.getWhoClicked();

        if (GUI_SW_TITLE.equals(title)) {
            e.setCancelled(true);
            int slot = e.getRawSlot();
            if (slot == 11) {
                staff.closeInventory();
                startStaffWork(staff);
            } else if (slot == 13) {
                staff.closeInventory();
                sendStaffStatusCard(staff, staff.getName());
            } else if (slot == 15) {
                staff.closeInventory();
                stopStaffWork(staff);
            }
            return true;
        }

        if (title.startsWith(GUI_CHECK_MAIN_PREFIX)) {
            e.setCancelled(true);
            String targetName = title.substring(GUI_CHECK_MAIN_PREFIX.length()).trim();
            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                staff.closeInventory();
                staff.sendMessage(ColorUtil.c("&cИгрок вышел из сети."));
                return true;
            }
            int slot = e.getRawSlot();
            if (slot == 11) {
                openCheckSoftwareGui(staff, target);
            } else if (slot == 15) {
                staff.closeInventory();
                stopCheck(staff, target);
            } else if (slot == 21) {
                CheckSession cs = activeChecks.get(target.getUniqueId());
                if (cs != null) {
                    cs.expiresAtMs += 180_000L;
                    staff.sendMessage(ColorUtil.c("&#00E676⏱ Таймер проверки продлён на +3 минуты!"));
                    target.sendMessage(ColorUtil.c("&#00E676⏱ Модератор продлил время проверки на +3 минуты!"));
                }
            } else if (slot == 23) {
                staff.closeInventory();
                executeBanWithSkyAnimation(staff.getName(), true, target.getName(), 30L * 86400_000L, "Читы (Обнаружены на проверке)");
            }
            return true;
        }

        if (title.startsWith(GUI_CHECK_SOFT_PREFIX)) {
            e.setCancelled(true);
            String targetName = title.substring(GUI_CHECK_SOFT_PREFIX.length()).trim();
            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                staff.closeInventory();
                staff.sendMessage(ColorUtil.c("&cИгрок вышел из сети."));
                return true;
            }
            int slot = e.getRawSlot();
            if (slot == 11) {
                staff.closeInventory();
                startCheck(staff, target, "ANYDESK");
            } else if (slot == 15) {
                staff.closeInventory();
                startCheck(staff, target, "DISCORD");
            } else if (slot == 22) {
                openCheckMainGui(staff, target);
            }
            return true;
        }
        return false;
    }

    // ================= DURATION PARSER & HELPERS =================
    /**
     * Parses "30s", "15m", "2h", "7d", "30м", "2ч", "7д", "perm"/"навсегда".
     * Returns 0L for permanent, >0L for milliseconds, or Long.MIN_VALUE on invalid format.
     */
    public static long parseDurationMs(String raw) {
        if (raw == null || raw.isEmpty()) return Long.MIN_VALUE;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if ("perm".equals(s) || "permanent".equals(s) || "forever".equals(s) || "навсегда".equals(s) || "0".equals(s)) {
            return 0L;
        }
        int numEnd = 0;
        while (numEnd < s.length() && Character.isDigit(s.charAt(numEnd))) {
            numEnd++;
        }
        if (numEnd == 0) return Long.MIN_VALUE;
        long val;
        try {
            val = Long.parseLong(s.substring(0, numEnd));
        } catch (Exception e) {
            return Long.MIN_VALUE;
        }
        if (val <= 0) return 0L;
        String unit = s.substring(numEnd).trim();
        if (unit.isEmpty() || unit.startsWith("m") || unit.startsWith("м")) {
            return val * 60_000L;
        }
        if (unit.startsWith("s") || unit.startsWith("с")) {
            return val * 1_000L;
        }
        if (unit.startsWith("h") || unit.startsWith("ч")) {
            return val * 3_600_000L;
        }
        if (unit.startsWith("d") || unit.startsWith("д")) {
            return val * 86_400_000L;
        }
        if (unit.startsWith("w") || unit.startsWith("н")) {
            return val * 7L * 86_400_000L;
        }
        return Long.MIN_VALUE;
    }

    public static String formatDurationRu(long durMs) {
        if (durMs <= 0L) return "Навсегда";
        long sec = durMs / 1000L;
        if (sec % 86400L == 0) return (sec / 86400L) + " дн.";
        if (sec % 3600L == 0) return (sec / 3600L) + " ч.";
        if (sec % 60L == 0) return (sec / 60L) + " мин.";
        return ColorUtil.fmtTime(sec);
    }

    private static String fmtClock(long sec) {
        long m = sec / 60L;
        long s = sec % 60L;
        return String.format(Locale.US, "%02d:%02d", Long.valueOf(m), Long.valueOf(s));
    }

    private static String joinArgs(String[] args, int startIdx) {
        StringBuilder sb = new StringBuilder();
        for (int i = startIdx; i < args.length; i++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private void broadcastToStaff(String msg) {
        String colored = ColorUtil.c(msg);
        Bukkit.getConsoleSender().sendMessage(colored);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isStaff(p)) p.sendMessage(colored);
        }
    }

    private ItemStack namedItem(Material mat, String name) {
        ItemStack it = new ItemStack(mat, 1);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName(ColorUtil.c(name));
            it.setItemMeta(m);
        }
        return it;
    }

    private ItemStack makeButton(Material mat, String title, String... lore) {
        ItemStack it = new ItemStack(mat, 1);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName(ColorUtil.c(title));
            List<String> ll = new ArrayList<String>();
            for (String line : lore) ll.add(ColorUtil.c(line));
            m.setLore(ll);
            m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(m);
        }
        return it;
    }

    private List<String> onlineNames(String pref) {
        List<String> list = new ArrayList<String>();
        for (Player p : Bukkit.getOnlinePlayers()) list.add(p.getName());
        return filter(list, pref);
    }

    private List<String> filter(List<String> src, String pref) {
        String p = pref == null ? "" : pref.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<String>();
        for (String s : src) {
            if (s.toLowerCase(Locale.ROOT).startsWith(p)) out.add(s);
        }
        return out;
    }
}
