package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
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
    }

    public boolean isStaff(CommandSender s) {
        if (!(s instanceof Player)) return true;
        Player p = (Player) s;
        RankManager.RankDef r = plugin.getRanks().getRank(p);
        return p.isOp() || r.tier >= 4 || p.hasPermission("kinggrief.staff") || p.hasPermission("kinggrief.admin");
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

    // ================= COMMAND ROUTER (/check, /sw, /mute, /unmute, /ban, /unban, /spek) =================
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String c = cmd.getName().toLowerCase(Locale.ROOT);
        if ("spek".equals(c)) {
            return handleSpekCommand(sender, args);
        }
        if ("sw".equals(c)) {
            return handleStaffWorkCommand(sender, args);
        }
        if ("check".equals(c)) {
            return handleCheckCommand(sender, args);
        }
        if ("mute".equals(c)) {
            return handleTimedMute(sender, args);
        }
        if ("unmute".equals(c)) {
            return handleUnmute(sender, args);
        }
        if ("ban".equals(c)) {
            return handleTimedBanWithAscension(sender, args);
        }
        if ("unban".equals(c)) {
            return handleUnban(sender, args);
        }
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
        } else if (("mute".equals(c) || "ban".equals(c)) && isStaff(sender)) {
            if (args.length == 1) return onlineNames(args[0]);
            if (args.length == 2) {
                return filter("mute".equals(c)
                        ? Arrays.asList("15m", "30m", "1h", "3h", "1d", "perm")
                        : Arrays.asList("7d", "14d", "30d", "90d", "perm"), args[1]);
            }
            if (args.length == 3) {
                return filter("mute".equals(c)
                        ? Arrays.asList("Оскорбление", "Флуд", "Спам", "Реклама", "Неадекват")
                        : Arrays.asList("Читы", "KillAura", "XRay", "Уклон_от_проверки", "Багоюз"), args[2]);
            }
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
        if (args.length < 3) {
            sender.sendMessage(ColorUtil.c("&#FFD740Использование: &f/mute <игрок> <время> <причина> &7(Пример: &f/mute Steve 30m Оскорбление&7)"));
            return true;
        }
        String target = args[0];
        long durMs = parseDurationMs(args[1]);
        if (durMs == Long.MIN_VALUE) {
            sender.sendMessage(ColorUtil.c("&#FF1744✖ Неверный формат времени! Примеры: &f15m, 2h, 7d, 30м, 1ч, perm"));
            return true;
        }
        String reason = joinArgs(args, 2);
        String staffName = sender instanceof Player ? sender.getName() : "Консоль";

        plugin.getStore().mute(target, durMs, reason, staffName);
        if (sender instanceof Player) {
            plugin.getStore().addStaffMute(staffName);
        }

        String timeLabel = formatDurationRu(durMs);
        Bukkit.broadcastMessage(ColorUtil.c("&#FF5252&l🔇 [МУТ] &fМодератор &#FFD740" + staffName
                + " &fвыдал мут игроку &c" + target + " &fна &#80D8FF" + timeLabel + "&f. Причина: &e" + reason));

        Player tp = Bukkit.getPlayerExact(target);
        if (tp != null) {
            ColorUtil.sound(tp, "entity.villager.no", 1.0f, 0.8f);
            tp.sendTitle(ColorUtil.c("&#FF5252&l🔇 ВАМ ВЫДАН МУТ"), ColorUtil.c("&fСрок: &e" + timeLabel + " &8│ &f" + reason), 5, 50, 10);
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
        Bukkit.broadcastMessage(ColorUtil.c("&#00E676🔊 Модератор &f" + sender.getName() + " &#00E676снял мут с игрока &f" + args[0] + "&#00E676."));
        return true;
    }

    public boolean handleTimedBanWithAscension(CommandSender sender, String[] args) {
        if (!isStaff(sender)) {
            sender.sendMessage(ColorUtil.c("&#FF1744✖ У вас нет прав на выдачу бана!"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(ColorUtil.c("&#FFD740Использование: &f/ban <игрок> <время> <причина> &7(Пример: &f/ban Cheater 30d KillAura&7)"));
            return true;
        }
        String targetName = args[0];
        Player onlineTarget = Bukkit.getPlayerExact(targetName);
        if (onlineTarget != null && "owner".equals(plugin.getRanks().getRank(onlineTarget).id) && (sender instanceof Player)) {
            sender.sendMessage(ColorUtil.c("&#FF1744✖ Нельзя забанить владельца сервера OWNER!"));
            return true;
        }

        long durMs = parseDurationMs(args[1]);
        if (durMs == Long.MIN_VALUE) {
            sender.sendMessage(ColorUtil.c("&#FF1744✖ Неверный формат времени! Примеры: &f12h, 7d, 30d, 30д, perm"));
            return true;
        }
        String reason = joinArgs(args, 2);
        String staffName = sender instanceof Player ? sender.getName() : "Консоль";
        executeBanWithSkyAnimation(staffName, sender instanceof Player, targetName, durMs, reason);
        return true;
    }

    public void executeBanWithSkyAnimation(final String staffName, boolean countForStaff, final String targetName, final long durMs, final String reason) {
        // 1. Persist ban IMMEDIATELY so quitting mid-flight cannot bypass it!
        plugin.getStore().ban(targetName, durMs, reason, staffName);
        if (countForStaff) {
            plugin.getStore().addStaffBan(staffName);
        }

        final String timeLabel = formatDurationRu(durMs);
        Bukkit.broadcastMessage(ColorUtil.c("&#FF1744&l🔨 [БАН] &fМодератор &#FFD740" + staffName
                + " &fзабанил игрока &c" + targetName + " &fна &#FF8A80" + timeLabel + "&f. Причина: &e" + reason));

        final Player victim = Bukkit.getPlayerExact(targetName);
        if (victim == null || !victim.isOnline()) {
            return;
        }

        // Remove active check if any
        CheckSession cs = activeChecks.remove(victim.getUniqueId());
        if (cs != null) {
            try { cs.bossBar.removeAll(); } catch (Throwable ignored) {}
        }

        // 2. EPIC SKY-ASCENSION BAN ANIMATION: Victim rises up into the sky in swirling crimson/gold fire & gets struck by lightning!
        ascendingBanPlayers.add(victim.getUniqueId());
        victim.setAllowFlight(true);
        victim.setFlying(true);
        victim.setGlowing(true);
        victim.sendTitle(
                ColorUtil.c("&#FF1744&l🔨 ВЫ ЗАБАНЕНЫ!"),
                ColorUtil.c("&fМодератор: &e" + staffName + " &8│ &fСрок: &c" + timeLabel),
                2, 60, 5
        );
        victim.getWorld().playSound(victim.getLocation(), "minecraft:block.beacon.activate", 2.0f, 0.7f);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!victim.isOnline()) {
                    ascendingBanPlayers.remove(victim.getUniqueId());
                    cancel();
                    return;
                }
                ticks++;
                Location loc = victim.getLocation();

                // Smooth upward levitation (+0.34 blocks per tick -> ~17.5 blocks high over 52 ticks!)
                victim.setVelocity(new Vector(0.0, 0.34, 0.0));
                float newYaw = loc.getYaw() + 14.0f;

                // Double-helix flame + soul-fire + totem spiral around the ascending player
                double angle = ticks * 0.45;
                double r = 1.15;
                Location p1 = loc.clone().add(Math.cos(angle) * r, 0.9, Math.sin(angle) * r);
                Location p2 = loc.clone().add(Math.cos(angle + Math.PI) * r, 0.9, Math.sin(angle + Math.PI) * r);
                loc.getWorld().spawnParticle(Particle.FLAME, p1, 4, 0.05, 0.05, 0.05, 0.01);
                loc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, p2, 4, 0.05, 0.05, 0.05, 0.01);
                loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 0.5, 0), 5, 0.25, 0.25, 0.25, 0.05);

                if (ticks % 8 == 0) {
                    float pitch = Math.min(2.0f, 0.75f + ticks * 0.025f);
                    loc.getWorld().playSound(loc, "minecraft:block.note_block.chime", 1.6f, pitch);
                }
                loc.setYaw(newYaw);

                // Climax at tick 52 (~2.6s): Lightning Bolt + Firework Burst + Kick!
                if (ticks >= 52) {
                    ascendingBanPlayers.remove(victim.getUniqueId());
                    victim.setGlowing(false);
                    try {
                        loc.getWorld().strikeLightningEffect(loc);
                        loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1, 0, 0, 0, 0);
                        loc.getWorld().playSound(loc, "minecraft:entity.lightning_bolt.thunder", 2.2f, 0.9f);
                        spawnBanFireworkBurst(loc);
                    } catch (Throwable ignored) {}

                    victim.kickPlayer(buildBanKickScreen(staffName, timeLabel, reason));
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
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
                + (notifiedStaff > 0 ? " (&aонлайн модераторов: " + notifiedStaff + "&a)" : " &7(сейчас свободных модераторов нет, King_antisoft ведёт запись)") + "!"));
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
