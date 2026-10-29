package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * KINGGRIEF v5.8 — Панель и команда Куратора (/curator):
 *  • Доступно строго КУРАТОРАМ (curator), ТЕХ.АДМИНАМ (techadmin) и ВЛАДЕЛЬЦАМ (owner)!
 *  • Выдача: КП (kp..admin), МЕДИА (tiktok..mediaplus), ДОНАТЫ и ДКП (dragon..downer), а также снятие ролей!
 *  • Надёжная защита иерархии: куратор НЕ может выдать curator/techadmin/owner или снять/изменить роль старших!
 *  • Удобный GUI (с головами онлайн-игроков и вводом любого оффлайн-ника в чат) + быстрые подкоманды + Журнал выдач!
 */
public final class CuratorManager implements Listener, CommandExecutor, TabCompleter {

    public static final class CuratorHolder implements InventoryHolder {
        public final String page;     // "main", "kp", "media", "donate", "pick_player", "roster", "log"
        public final String targetNick;
        public final String rankId;

        public CuratorHolder(String page, String targetNick, String rankId) {
            this.page = page;
            this.targetNick = targetNick == null ? "" : targetNick;
            this.rankId = rankId == null ? "" : rankId;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    // 1. КП (Команда Проекта — ниже Куратора)
    public static final List<String> KP_RANKS = Collections.unmodifiableList(Arrays.asList(
            "kp", "helper", "sthelper", "mlmoder", "moder", "stmoder", "glmoder", "mladmin", "admin", "stadmin", "gladmin",
            "curator", "curator_dkp", "curator_media", "curator_kp", "curator_discord"
    ));

    public static final List<String> CURATOR_RANKS = Collections.unmodifiableList(Arrays.asList(
            "curator", "curator_dkp", "curator_media", "curator_kp", "curator_discord"
    ));

    // 2. МЕДИА
    public static final List<String> MEDIA_RANKS = Collections.unmodifiableList(Arrays.asList(
            "tiktok", "twitch", "yt", "media", "mediaplus"
    ));

    // 3. ДОНАТЫ + ДКП
    public static final List<String> DONATE_RANKS = Collections.unmodifiableList(Arrays.asList(
            "dragon", "pegas", "cobra", "dracula", "bunny", "rabbit",
            "vampire", "tiger", "god", "imperator", "king", "sponsor",
            "dhelper", "dkp", "dmoder", "dstmoder", "dglmoder", "dmladmin", "dadmin", "opka", "downer"
    ));

    private static final Map<String, String> RANK_TITLES = new LinkedHashMap<String, String>();
    static {
        RANK_TITLES.put("kp",        "&#86EFACКП (Стажёр КП)");
        RANK_TITLES.put("helper",    "&#4ADE80ХЕЛПЕР");
        RANK_TITLES.put("sthelper",  "&#2DD4BFСТ. ХЕЛПЕР");
        RANK_TITLES.put("mlmoder",   "&#38BDF8МЛ. МОДЕР");
        RANK_TITLES.put("moder",     "&#60A5FAМОДЕР");
        RANK_TITLES.put("stmoder",   "&#818CF8СТ. МОДЕР");
        RANK_TITLES.put("glmoder",   "&#C084FCГЛ. МОДЕР");
        RANK_TITLES.put("mladmin",   "&#FB923CМЛ. АДМИН");
        RANK_TITLES.put("admin",     "&#F87171АДМИН");
        RANK_TITLES.put("stadmin",   "&#FFBA9CСТ. АДМИН");
        RANK_TITLES.put("gladmin",   "&#FF9DA8ГЛ. АДМИН");
        RANK_TITLES.put("curator", "&#86EFACКУРАТОР");
        RANK_TITLES.put("curator_dkp", "&#FFD29CКУРАТОР ДКП");
        RANK_TITLES.put("curator_media", "&#FFD0EDКУРАТОР МЕДИА");
        RANK_TITLES.put("curator_kp", "&#A5EFC3КУРАТОР КП");
        RANK_TITLES.put("curator_discord", "&#B4C5FFКУРАТОР DISCORD");

        RANK_TITLES.put("tiktok",    "&#FF2E93TIKTOK");
        RANK_TITLES.put("twitch",    "&#A855F7TWITCH");
        RANK_TITLES.put("yt",        "&#FF1744YOUTUBE");
        RANK_TITLES.put("media",     "&#EC4899MEDIA");
        RANK_TITLES.put("mediaplus", "&#F43F5EMEDIA+");

        RANK_TITLES.put("dragon",    "&#F5D0FEDRAGON");
        RANK_TITLES.put("pegas",     "&#BAE6FDPEGAS");
        RANK_TITLES.put("cobra",     "&#86EFACCOBRA");
        RANK_TITLES.put("dracula",   "&#FCA5A5DRACULA");
        RANK_TITLES.put("bunny",     "&#FBCFE8BUNNY");
        RANK_TITLES.put("rabbit",    "&#F9A8D4RABBIT");
        RANK_TITLES.put("vampire",   "&#F43F5EVAMPIRE");
        RANK_TITLES.put("tiger",     "&#FDBA74TIGER");
        RANK_TITLES.put("god",       "&#FDE047GOD");
        RANK_TITLES.put("imperator", "&#FB7185IMPERATOR");
        RANK_TITLES.put("king",      "&#FFD740KING");
        RANK_TITLES.put("sponsor",   "&#38BDF8SPONSOR");

        RANK_TITLES.put("dhelper",   "&#4ADE80Д. ХЕЛПЕР");
        RANK_TITLES.put("dkp",       "&#86EFACД. КП");
        RANK_TITLES.put("dmoder",    "&#60A5FAД. МОДЕР");
        RANK_TITLES.put("dstmoder",  "&#818CF8Д. СТ. МОДЕР");
        RANK_TITLES.put("dglmoder",  "&#C084FCД. ГЛ. МОДЕР");
        RANK_TITLES.put("dmladmin",  "&#FB923CД. МЛ. АДМИН");
        RANK_TITLES.put("dadmin",    "&#F87171Д. АДМИН");
        RANK_TITLES.put("opka",      "&#FF9100ОПКА (OPKA)");
        RANK_TITLES.put("downer",    "&#FF1744Д. ОВНЕР (D.OWNER)");
    }

    private final KingGriefCore plugin;
    // Pending chat input: Curator UUID -> rankId to grant ("default" = remove rank)
    private final Map<UUID, String> pendingNickForRank = new HashMap<UUID, String>();

    public CuratorManager(KingGriefCore plugin) {
        this.plugin = plugin;
    }

    public void shutdown() {
        pendingNickForRank.clear();
    }

    public boolean hasCuratorAccess(CommandSender sender) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;
        RankManager.RankDef r = plugin.getRanks().getRank(p);
        if (r == null) return false;
        return plugin.getRanks().isCuratorRank(r.id) || plugin.getRanks().isOwnerLevel(r.id);
    }

    public boolean isAllowedCuratorRank(String rankId) {
        if (rankId == null) return false;
        String low = rankId.toLowerCase(Locale.ROOT);
        return KP_RANKS.contains(low) || MEDIA_RANKS.contains(low) || DONATE_RANKS.contains(low) || CURATOR_RANKS.contains(low);
    }

    private boolean isOwnerSender(CommandSender s) {
        if (!(s instanceof Player)) return true;
        RankManager.RankDef r = plugin.getRanks().getRank((Player) s);
        return r != null && plugin.getRanks().isOwnerLevel(r.id);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pendingNickForRank.remove(e.getPlayer().getUniqueId());
    }

    // =========================================================================
    // 1. КОМАНДА /curator
    // =========================================================================
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!hasCuratorAccess(sender)) {
            sender.sendMessage(ColorUtil.c("&#FF1744✖ Команда &l/curator &r&#FF1744доступна строго &lКУРАТОРАМ &r&#FF1744и выше!"));
            if (sender instanceof Player) ColorUtil.sound((Player) sender, "entity.villager.no", 1.0f, 0.8f);
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player) {
                openMainCuratorGui((Player) sender, "");
            } else {
                sendCuratorHelp(sender);
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if ("help".equals(sub) || "?".equals(sub)) {
            sendCuratorHelp(sender);
            return true;
        }

        if ("list".equals(sub) || "staff".equals(sub)) {
            sendStaffAndMediaList(sender);
            return true;
        }

        if ("log".equals(sub) || "logs".equals(sub) || "history".equals(sub)) {
            sendAuditLogChat(sender);
            return true;
        }

        // /curator remove <ник> [причина]
        if ("remove".equals(sub) || "take".equals(sub) || "demote".equals(sub) || "unrank".equals(sub)) {
            if (args.length < 2) {
                sender.sendMessage(ColorUtil.c("&eИспользование: &f/curator remove <ник> [причина]"));
                return true;
            }
            String reason = args.length >= 3 ? joinArgs(args, 2) : "Решение куратора";
            executeRankChange(sender, args[1], "default", reason);
            return true;
        }

        // /curator <give|set|kp|media|donate> <ник> <ранг>
        if ("give".equals(sub) || "set".equals(sub) || "grant".equals(sub) || "add".equals(sub)
                || "kp".equals(sub) || "media".equals(sub) || "donate".equals(sub)) {
            if (args.length < 3) {
                if (sender instanceof Player && args.length == 1 && ("kp".equals(sub) || "media".equals(sub) || "donate".equals(sub))) {
                    openCategoryGui((Player) sender, sub, "");
                    return true;
                }
                sender.sendMessage(ColorUtil.c("&eИспользование: &f/curator " + sub + " <ник> <ранг>"));
                return true;
            }
            String target = args[1];
            String rankRaw = args[2].toLowerCase(Locale.ROOT);
            if ("kp".equals(sub) && !KP_RANKS.contains(rankRaw)) {
                sender.sendMessage(ColorUtil.c("&#FF1744✖ Доступные роли КП: &f" + String.join(", ", KP_RANKS)));
                return true;
            }
            if ("media".equals(sub) && !MEDIA_RANKS.contains(rankRaw)) {
                sender.sendMessage(ColorUtil.c("&#FF1744✖ Доступные роли МЕДИА: &f" + String.join(", ", MEDIA_RANKS)));
                return true;
            }
            if ("donate".equals(sub) && !DONATE_RANKS.contains(rankRaw)) {
                sender.sendMessage(ColorUtil.c("&#FF1744✖ Доступные ДОНАТЫ: &f" + String.join(", ", DONATE_RANKS)));
                return true;
            }
            executeRankChange(sender, target, rankRaw, null);
            return true;
        }

        // /curator <ник> <ранг> (короткий синтаксис в 2 аргумента!)
        if (args.length >= 2 && (isAllowedCuratorRank(args[1]) || "default".equalsIgnoreCase(args[1]))) {
            executeRankChange(sender, args[0], args[1].toLowerCase(Locale.ROOT), null);
            return true;
        }

        // /curator <ник> -> открыть GUI выдачи конкретному игроку!
        if (sender instanceof Player && args.length == 1) {
            openMainCuratorGui((Player) sender, args[0]);
            return true;
        }

        sendCuratorHelp(sender);
        return true;
    }

    private void sendCuratorHelp(CommandSender s) {
        s.sendMessage(ColorUtil.c("&#86EFAC&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        s.sendMessage(ColorUtil.c(" &#86EFAC&l👑 ПАНЕЛЬ УПРАВЛЕНИЯ КУРАТОРА (/curator):"));
        s.sendMessage(ColorUtil.c(" &f/curator &8— &7Открыть удобное GUI-меню Куратора"));
        s.sendMessage(ColorUtil.c(" &f/curator <ник> &8— &7Открыть меню выдачи для выбранного игрока"));
        s.sendMessage(ColorUtil.c(" &f/curator kp <ник> <роль> &8— &7Выдать роль КП (&a" + String.join(", ", KP_RANKS) + "&7)"));
        s.sendMessage(ColorUtil.c(" &f/curator media <ник> <роль> &8— &7Выдать МЕДИА (&d" + String.join(", ", MEDIA_RANKS) + "&7)"));
        s.sendMessage(ColorUtil.c(" &f/curator donate <ник> <донат> &8— &7Выдать любой ДОНАТ / ДКП"));
        s.sendMessage(ColorUtil.c(" &f/curator remove <ник> [причина] &8— &7Снять роль до Игрока"));
        s.sendMessage(ColorUtil.c(" &f/curator list &8— &7Список всего состава КП и МЕДИА"));
        s.sendMessage(ColorUtil.c(" &f/curator log &8— &7Журнал последних выдач Кураторов"));
        s.sendMessage(ColorUtil.c("&#86EFAC&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    // =========================================================================
    // 2. БЕЗОПАСНАЯ ВЫДАЧА / СНЯТИЕ РОЛИ С ПРОВЕРКОЙ ИЕРАРХИИ И ЛОГИРОВАНИЕМ!
    // =========================================================================
    public void executeRankChange(CommandSender actor, String targetNick, String newRankId, String optReason) {
        executeRankChange(actor, targetNick, newRankId, optReason, null, false);
    }

    private void executeRankChange(CommandSender actor, String targetNick, String newRankId,
                                   String optReason, String verifiedLpGroup, boolean lpChecked) {
        if (!hasCuratorAccess(actor)) {
            actor.sendMessage(ColorUtil.c("&cНедостаточно прав для управления ролями."));
            return;
        }
        if (targetNick == null || !targetNick.trim().matches("[A-Za-z0-9_]{1,16}")) {
            actor.sendMessage(ColorUtil.c("&#FF1744✖ Укажите корректный ник игрока!"));
            return;
        }
        targetNick = targetNick.trim();
        Player exactOnline = Bukkit.getPlayerExact(targetNick);
        if (exactOnline != null) targetNick = exactOnline.getName();

        String lowRank = RankManager.normalizeRankAlias(newRankId);
        boolean isDemote = "default".equals(lowRank);

        if (!isDemote && !isAllowedCuratorRank(lowRank)) {
            actor.sendMessage(ColorUtil.c("&#FF1744✖ Привилегия &f" + newRankId + " &#FF1744недоступна для выдачи через /curator!"));
            actor.sendMessage(ColorUtil.c("&7Кураторы могут выдавать все роли &aКП&7, &dМЕДИА &7и &eДОНАТЫ&7 (в пределах своей иерархии; TechAdmin / Owner через LP)."));
            if (actor instanceof Player) ColorUtil.sound((Player) actor, "entity.villager.no", 1f, 0.8f);
            return;
        }

        // A saved rank may be stale if LuckPerms was edited independently. Resolve
        // even offline users asynchronously, then repeat all authorization checks
        // on the server thread. An API/storage error must never imply "default".
        if (!isOwnerSender(actor) && Bukkit.getPluginManager().getPlugin("LuckPerms") != null && !lpChecked) {
            verifyLuckPermsThenChange(actor, targetNick, newRankId, optReason, exactOnline);
            return;
        }
        if (lpChecked && (verifiedLpGroup == null || !plugin.getRanks().exists(verifiedLpGroup))) {
            actor.sendMessage(ColorUtil.c("&cНе удалось безопасно проверить группу цели в LuckPerms. Обратитесь к владельцу."));
            return;
        }

        // Check the highest of Core, live and verified LuckPerms ranks.
        String curSaved = plugin.getStore().getSavedRank(targetNick);
        RankManager.RankDef curDef = plugin.getRanks().byId(curSaved);
        if (exactOnline != null) {
            RankManager.RankDef liveDef = plugin.getRanks().getRank(exactOnline);
            if (liveDef != null && liveDef.weight > curDef.weight) curDef = liveDef;
        }

        if (lpChecked) {
            RankManager.RankDef lpDef = plugin.getRanks().byId(verifiedLpGroup);
            if (lpDef.weight > curDef.weight) curDef = lpDef;
        }

        RankManager.RankDef targetDef = plugin.getRanks().byId(lowRank);
        if (!isOwnerSender(actor)) {
            RankManager.RankDef actorDef = plugin.getRanks().getRank((Player) actor);
            if (curDef.weight >= actorDef.weight || targetDef.weight >= actorDef.weight) {
                actor.sendMessage(ColorUtil.c("&#FF1744✖ Защита иерархии: можно изменять и выдавать только роли ниже своей!"));
                ColorUtil.sound((Player) actor, "entity.villager.no", 1f, 0.8f);
                return;
            }
        }

        plugin.getRanks().setRank(targetNick, targetDef.id, true);

        String actorName = actor instanceof Player ? ((Player) actor).getName() : "CONSOLE";
        String timeStr = new SimpleDateFormat("dd.MM HH:mm").format(new Date());
        String prettyRank = isDemote ? "&7ИГРОК (Снятие)" : RANK_TITLES.getOrDefault(targetDef.id, "&#FFD740" + targetDef.id.toUpperCase(Locale.ROOT));
        String categoryLabel = isDemote ? "СНЯТИЕ"
                : KP_RANKS.contains(targetDef.id) ? "КП"
                : MEDIA_RANKS.contains(targetDef.id) ? "МЕДИА" : "ДОНАТ";

        String logLine = "&8[" + timeStr + "] &#86EFAC" + actorName + " &7→ &f" + targetNick + " &8[" + categoryLabel + "] &f" + prettyRank
                + (optReason != null && !optReason.isEmpty() ? " &8(" + optReason + ")" : "");
        plugin.getStore().appendCuratorAuditLog(logLine);

        if (exactOnline != null) {
            plugin.getHud().updatePlayerHud(exactOnline);
            if (isDemote) {
                exactOnline.sendTitle(ColorUtil.c("&#FF5252&lРОЛЬ СНЯТА"),
                        ColorUtil.c("&7Куратор &f" + actorName + " &7снял вашу привилегию"), 8, 60, 15);
                exactOnline.sendMessage(ColorUtil.c("&#FF5252⚠ Куратор &f" + actorName + " &#FF5252снял вашу роль"
                        + (optReason != null ? " &7(Причина: &f" + optReason + "&7)" : "") + "."));
                ColorUtil.sound(exactOnline, "block.anvil.land", 0.8f, 0.9f);
            } else {
                ColorUtil.spawnFirework(exactOnline.getLocation());
                exactOnline.sendTitle(ColorUtil.c("&#86EFAC&l👑 ВЫДАНА РОЛЬ!"),
                        ColorUtil.c("&fВаша новая роль: " + targetDef.shortLabel + " " + prettyRank), 8, 70, 18);
                exactOnline.sendMessage(ColorUtil.c("&#86EFAC&l👑 [КУРАТОР] &fКуратор &#FFD740" + actorName
                        + " &fвыдал вам роль " + targetDef.shortLabel + " " + prettyRank + "&f!"));
                ColorUtil.sound(exactOnline, "ui.toast.challenge_complete", 1.0f, 1.2f);
            }
        }

        actor.sendMessage(ColorUtil.c("&#00E676✔ [КУРАТОР] Игроку &f" + targetNick + " &#00E676успешно установлена роль: "
                + (isDemote ? "&7ИГРОК (default)" : (targetDef.shortLabel + " " + prettyRank)) + "&#00E676!"));
        if (actor instanceof Player) {
            ColorUtil.sound((Player) actor, "entity.player.levelup", 0.9f, 1.3f);
        }

        // Уведомляем состав КП (Staff Chat) о кадровом действии Куратора!
        String staffAlert = ColorUtil.c("&#86EFAC&l[КУРАТОР-ЛОГ] &f" + actorName
                + (isDemote ? " &cснял роль с &f" : " &aвыдал роль &f") + targetNick
                + (isDemote ? "" : " &8→ " + targetDef.shortLabel + " " + prettyRank));
        for (Player on : Bukkit.getOnlinePlayers()) {
            RankManager.RankDef r = plugin.getRanks().getRank(on);
            if (r != null && r.weight >= 200) {
                on.sendMessage(staffAlert);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void verifyLuckPermsThenChange(final CommandSender actor, final String target, final String rank,
                                            final String reason, Player online) {
        try {
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object api = provider.getMethod("get").invoke(null);
            Object manager = Class.forName("net.luckperms.api.LuckPerms").getMethod("getUserManager").invoke(api);
            Class<?> managerApi = Class.forName("net.luckperms.api.model.user.UserManager");
            final java.lang.reflect.Method load = managerApi.getMethod("loadUser", UUID.class);
            final java.lang.reflect.Method primary = Class.forName("net.luckperms.api.model.user.User").getMethod("getPrimaryGroup");
            CompletableFuture<UUID> lookup = online != null
                    ? CompletableFuture.completedFuture(online.getUniqueId())
                    : (CompletableFuture<UUID>) managerApi.getMethod("lookupUniqueId", String.class).invoke(manager, target);
            actor.sendMessage(ColorUtil.c("&7Проверка группы игрока в LuckPerms…"));
            lookup.thenCompose(uuid -> {
                if (uuid == null) return CompletableFuture.completedFuture("default");
                try {
                    CompletableFuture<?> userFuture = (CompletableFuture<?>) load.invoke(manager, uuid);
                    return userFuture.thenApply(user -> {
                        try { return user == null ? null : (String) primary.invoke(user); }
                        catch (Exception ex) { throw new CompletionException(ex); }
                    });
                } catch (Exception ex) {
                    CompletableFuture<String> failed = new CompletableFuture<String>();
                    failed.completeExceptionally(ex);
                    return failed;
                }
            }).whenComplete((group, error) -> {
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (actor instanceof Player && !((Player) actor).isOnline()) return;
                    if (error != null) {
                        actor.sendMessage(ColorUtil.c("&cLuckPerms недоступен: изменение роли отменено."));
                    } else {
                        executeRankChange(actor, target, rank, reason, group, true);
                    }
                });
            });
        } catch (Throwable ex) {
            actor.sendMessage(ColorUtil.c("&cНе удалось проверить LuckPerms: изменение роли отменено."));
        }
    }

    // =========================================================================
    // 3. GUI МЕНЮ КУРАТОРА (/curator)
    // =========================================================================
    public void openMainCuratorGui(Player p, String targetNick) {
        String titleSuffix = (targetNick != null && !targetNick.isEmpty()) ? (" &8• &f" + targetNick) : "";
        Inventory inv = Bukkit.createInventory(new CuratorHolder("main", targetNick, ""), 45,
                ColorUtil.c("&#86EFAC&l👑 ПАНЕЛЬ КУРАТОРА" + titleSuffix));
        fillBorder(inv, Material.LIME_STAINED_GLASS_PANE, Material.BLACK_STAINED_GLASS_PANE);

        String targetLine = (targetNick != null && !targetNick.isEmpty())
                ? "&#00E676Выбранный игрок: &f&l" + targetNick
                : "&7Игрок будет выбран на следующем шаге";

        inv.setItem(4, ItemFactory.glowIcon(Material.NETHER_STAR, "&#86EFAC&l👑 УПРАВЛЕНИЕ КУРАТОРА KINGGRIEF",
                "&7Выдача ролей КП, Медиа и Донатов в 2 клика!",
                targetLine,
                "",
                "&7Все действия записываются в лог кураторов."));

        inv.setItem(11, ItemFactory.glowIcon(Material.DIAMOND_SWORD, "&#4ADE80&l🛡 ВЫДАТЬ РОЛЬ КП И КУРАТОРА",
                "&7Назначение персонала проекта:",
                "&f• КП, Хелпер, Ст. Хелпер",
                "&f• Мл. Модер, Модер, Ст. Модер, Гл. Модер",
                "&f• Мл. Админ, Админ, Ст. и Гл. Админ",
                "&f• Кураторы ДКП, Медиа, КП, Discord",
                "",
                targetLine,
                "&#00E676▸ Нажмите для выбора должности КП!"));

        inv.setItem(13, ItemFactory.glowIcon(Material.AMETHYST_SHARD, "&#EC4899&l🎬 ВЫДАТЬ РОЛЬ МЕДИА (5 ролей)",
                "&7Назначение контент-мейкеров и стримеров:",
                "&f• TikTok, Twitch, YouTube",
                "&f• Media, Media+",
                "",
                targetLine,
                "&#EC4899▸ Нажмите для выбора роли МЕДИА!"));

        inv.setItem(15, ItemFactory.glowIcon(Material.TOTEM_OF_UNDYING, "&#FFD740&l💎 ВЫДАТЬ ДОНАТ / ДКП (21 ранг)",
                "&7Выдача всех донат-привилегий:",
                "&f• Dragon, Pegas, Cobra ... King, Sponsor",
                "&f• Д.Хелпер, Д.Модер ... Опка, Д.Овнер",
                "",
                targetLine,
                "&#FFD740▸ Нажмите для выбора Доната!"));

        inv.setItem(29, ItemFactory.glowIcon(Material.WRITABLE_BOOK, "&#38BDF8&l📋 СОСТАВ КП И МЕДИА СЕРВЕРА",
                "&7Просмотр всех действующих сотрудников КП",
                "&7и Медиа (онлайн + оффлайн) с быстрым управлением!",
                "",
                "&#38BDF8▸ Открыть список состава"));

        inv.setItem(31, ItemFactory.glowIcon(Material.BARRIER, "&#FF5252&l❌ СНЯТЬ РОЛЬ С ИГРОКА",
                "&7Сбросить КП / Медиа / Донат до обычного Игрока.",
                "&8(Защищено от снятия Кураторов и Владельцев)",
                "",
                "&#FF5252▸ Нажмите для снятия роли"));

        inv.setItem(33, ItemFactory.glowIcon(Material.PAPER, "&#FDE047&l📜 ЖУРНАЛ ВЫДАЧ КУРАТОРОВ",
                "&7Последние 40 выдач и снятий привилегий.",
                "",
                "&#FDE047▸ Открыть историю действий"));

        inv.setItem(40, ItemFactory.icon(Material.ARROW, "&7« В главное меню (/menu)"));
        p.openInventory(inv);
    }

    public void openCategoryGui(Player p, String cat, String targetNick) {
        List<String> list;
        String catTitle;
        Material borderMat;
        Material iconMat;
        if ("kp".equals(cat)) {
            list = KP_RANKS;
            catTitle = "&#4ADE80&l🛡 ВЫДАЧА РОЛЕЙ КП";
            borderMat = Material.LIME_STAINED_GLASS_PANE;
            iconMat = Material.SHIELD;
        } else if ("media".equals(cat)) {
            list = MEDIA_RANKS;
            catTitle = "&#EC4899&l🎬 ВЫДАЧА РОЛЕЙ МЕДИА";
            borderMat = Material.MAGENTA_STAINED_GLASS_PANE;
            iconMat = Material.SPYGLASS;
        } else {
            list = DONATE_RANKS;
            catTitle = "&#FFD740&l💎 ВЫДАЧА ДОНАТОВ И ДКП";
            borderMat = Material.YELLOW_STAINED_GLASS_PANE;
            iconMat = Material.NETHERITE_INGOT;
        }

        Inventory inv = Bukkit.createInventory(new CuratorHolder(cat, targetNick, ""), 54, ColorUtil.c(catTitle));
        fillBorder(inv, borderMat, Material.BLACK_STAINED_GLASS_PANE);

        int[] slots = new int[]{
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34
        };

        for (int i = 0; i < list.size() && i < slots.length; i++) {
            String rid = list.get(i);
            RankManager.RankDef def = plugin.getRanks().byId(rid);
            String pretty = RANK_TITLES.getOrDefault(rid, "&#FFD740" + rid.toUpperCase(Locale.ROOT));
            Material m = pickMaterialForRank(rid, iconMat);
            String actionHint = (targetNick != null && !targetNick.isEmpty())
                    ? "&#00E676▸ Нажмите, чтобы сразу выдать игроку &f" + targetNick + "&#00E676!"
                    : "&#FFD740▸ Нажмите, чтобы выбрать игрока!";
            inv.setItem(slots[i], ItemFactory.glowIcon(m,
                    def.shortLabel + " " + pretty,
                    "&7ID группы: &f" + def.id + " &8(Вес: " + def.weight + ")",
                    "&7Точек дома: &a" + def.maxHomes + " &8│ &7Приватов: &b" + def.maxClaims,
                    "&7Кит привилегии: &e/kit " + def.kitName,
                    "",
                    actionHint));
        }

        inv.setItem(49, ItemFactory.icon(Material.ARROW, "&7« Назад в Панель Куратора"));
        p.openInventory(inv);
    }

    public void openPlayerPickerGui(Player p, String rankId) {
        boolean isDemote = "default".equalsIgnoreCase(rankId);
        String pretty = isDemote ? "&#FF5252СНЯТИЕ РОЛИ" : RANK_TITLES.getOrDefault(rankId, "&#FFD740" + rankId.toUpperCase(Locale.ROOT));
        Inventory inv = Bukkit.createInventory(new CuratorHolder("pick_player", "", rankId), 54,
                ColorUtil.c("&#86EFAC&lВЫБЕРИТЕ ИГРОКА &8• " + pretty));
        fillBorder(inv, Material.CYAN_STAINED_GLASS_PANE, Material.BLACK_STAINED_GLASS_PANE);

        inv.setItem(4, ItemFactory.glowIcon(Material.NAME_TAG, "&#00E676&l✍ ВВЕСТИ ЛЮБОЙ НИК В ЧАТ (ОНЛАЙН / ОФФЛАЙН)",
                "&7Нажмите сюда и напишите ник любого игрока в чат!",
                "&7Выбранное действие: " + pretty,
                "",
                "&#FFD740▸ Нажмите для ввода ника в чат"));

        int[] slots = new int[]{
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };
        int idx = 0;
        for (Player on : Bukkit.getOnlinePlayers()) {
            if (idx >= slots.length) break;
            RankManager.RankDef cur = plugin.getRanks().getRank(on);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            if (sm != null) {
                try { sm.setOwningPlayer(on); } catch (Throwable ignored) {}
                sm.setDisplayName(ColorUtil.c("&#00E676● &f&l" + on.getName()));
                List<String> lore = new ArrayList<String>();
                lore.add(ColorUtil.c("&7Текущая роль: " + (cur != null ? cur.shortLabel + " &f(" + cur.id + ")" : "&7default")));
                lore.add(ColorUtil.c("&7Выдать: " + pretty));
                lore.add("");
                lore.add(ColorUtil.c("&#00E676▸ Нажмите для мгновенной выдачи!"));
                sm.setLore(lore);
                head.setItemMeta(sm);
            }
            inv.setItem(slots[idx++], head);
        }

        inv.setItem(49, ItemFactory.icon(Material.ARROW, "&7« Назад в Панель Куратора"));
        p.openInventory(inv);
    }

    public void openRosterGui(Player p) {
        Inventory inv = Bukkit.createInventory(new CuratorHolder("roster", "", ""), 54,
                ColorUtil.c("&#38BDF8&l📋 СОСТАВ КП И МЕДИА СЕРВЕРА"));
        fillBorder(inv, Material.LIGHT_BLUE_STAINED_GLASS_PANE, Material.BLACK_STAINED_GLASS_PANE);

        Map<String, String> allSaved = plugin.getStore().getAllSavedPlayerRanks();
        List<Map.Entry<String, String>> staffAndMedia = new ArrayList<Map.Entry<String, String>>();
        for (Map.Entry<String, String> en : allSaved.entrySet()) {
            String rid = en.getValue();
            if (KP_RANKS.contains(rid) || MEDIA_RANKS.contains(rid)
                    || CURATOR_RANKS.contains(rid) || "techadmin".equals(rid) || "owner".equals(rid)) {
                staffAndMedia.add(en);
            }
        }
        Collections.sort(staffAndMedia, new Comparator<Map.Entry<String, String>>() {
            @Override
            public int compare(Map.Entry<String, String> a, Map.Entry<String, String> b) {
                int wa = plugin.getRanks().byId(a.getValue()).weight;
                int wb = plugin.getRanks().byId(b.getValue()).weight;
                return Integer.compare(wb, wa);
            }
        });

        int[] slots = new int[]{
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };
        for (int i = 0; i < staffAndMedia.size() && i < slots.length; i++) {
            String nick = staffAndMedia.get(i).getKey();
            String rid = staffAndMedia.get(i).getValue();
            RankManager.RankDef def = plugin.getRanks().byId(rid);
            Player on = Bukkit.getPlayerExact(nick);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            if (sm != null) {
                if (on != null) {
                    try { sm.setOwningPlayer(on); } catch (Throwable ignored) {}
                } else {
                    try {
                        OfflinePlayer off = Bukkit.getOfflinePlayer(nick);
                        sm.setOwningPlayer(off);
                    } catch (Throwable ignored) {}
                }
                sm.setDisplayName(ColorUtil.c((on != null ? "&#00E676● ОНЛАЙН " : "&7○ ОФФЛАЙН ") + "&f&l" + nick));
                List<String> lore = new ArrayList<String>();
                lore.add(ColorUtil.c("&7Должность: " + def.shortLabel + " &f(" + rid.toUpperCase(Locale.ROOT) + ")"));
                lore.add(ColorUtil.c("&7Наиграно: &f" + ColorUtil.fmtTime(plugin.getStore().getPlaySeconds(nick))));
                if (def.weight < 275 || isOwnerSender(p)) {
                    lore.add("");
                    lore.add(ColorUtil.c("&#FFD740▸ ЛКМ: Изменить роль игроку"));
                    lore.add(ColorUtil.c("&#FF5252▸ ПКМ: Снять роль до Игрока"));
                }
                sm.setLore(lore);
                head.setItemMeta(sm);
            }
            inv.setItem(slots[i], head);
        }

        inv.setItem(49, ItemFactory.icon(Material.ARROW, "&7« Назад в Панель Куратора"));
        p.openInventory(inv);
    }

    public void openAuditLogGui(Player p) {
        Inventory inv = Bukkit.createInventory(new CuratorHolder("log", "", ""), 54,
                ColorUtil.c("&#FDE047&l📜 ЖУРНАЛ ВЫДАЧ КУРАТОРОВ"));
        fillBorder(inv, Material.YELLOW_STAINED_GLASS_PANE, Material.BLACK_STAINED_GLASS_PANE);

        List<String> logs = plugin.getStore().getCuratorAuditLog();
        int[] slots = new int[]{
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };
        if (logs.isEmpty()) {
            inv.setItem(22, ItemFactory.icon(Material.BOOK, "&7Журнал выдач пока пуст",
                    "&8Здесь появятся все выдачи КП, Медиа и Донатов."));
        } else {
            for (int i = 0; i < logs.size() && i < slots.length; i++) {
                inv.setItem(slots[i], ItemFactory.icon(Material.PAPER,
                        "&#FDE047Запись #" + (i + 1),
                        logs.get(i)));
            }
        }
        inv.setItem(49, ItemFactory.icon(Material.ARROW, "&7« Назад в Панель Куратора"));
        p.openInventory(inv);
    }

    // =========================================================================
    // 4. ОБРАБОТЧИКИ КЛИКОВ В GUI И ВВОДА НИКА В ЧАТ
    // =========================================================================
    @EventHandler
    public void onGuiClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        if (!(e.getInventory().getHolder() instanceof CuratorHolder)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getInventory()) return;

        Player p = (Player) e.getWhoClicked();
        if (!hasCuratorAccess(p)) {
            p.closeInventory();
            return;
        }

        CuratorHolder h = (CuratorHolder) e.getInventory().getHolder();
        int slot = e.getRawSlot();
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if ("main".equals(h.page)) {
            if (slot == 40) { plugin.getGuis().openMainMenu(p); return; }
            if (slot == 11) { openCategoryGui(p, "kp", h.targetNick); return; }
            if (slot == 13) { openCategoryGui(p, "media", h.targetNick); return; }
            if (slot == 15) { openCategoryGui(p, "donate", h.targetNick); return; }
            if (slot == 29) { openRosterGui(p); return; }
            if (slot == 31) {
                if (!h.targetNick.isEmpty()) {
                    p.closeInventory();
                    executeRankChange(p, h.targetNick, "default", "Снято через /curator");
                } else {
                    openPlayerPickerGui(p, "default");
                }
                return;
            }
            if (slot == 33) { openAuditLogGui(p); return; }
            return;
        }

        if ("kp".equals(h.page) || "media".equals(h.page) || "donate".equals(h.page)) {
            if (slot == 49) { openMainCuratorGui(p, h.targetNick); return; }
            List<String> list = "kp".equals(h.page) ? KP_RANKS : "media".equals(h.page) ? MEDIA_RANKS : DONATE_RANKS;
            int idx = slotToGridIndex(slot);
            if (idx >= 0 && idx < list.size()) {
                String chosenRank = list.get(idx);
                if (!h.targetNick.isEmpty()) {
                    p.closeInventory();
                    executeRankChange(p, h.targetNick, chosenRank, null);
                } else {
                    openPlayerPickerGui(p, chosenRank);
                }
            }
            return;
        }

        if ("pick_player".equals(h.page)) {
            if (slot == 49) { openMainCuratorGui(p, ""); return; }
            if (slot == 4) {
                p.closeInventory();
                pendingNickForRank.put(p.getUniqueId(), h.rankId);
                p.sendMessage(ColorUtil.c("&#86EFAC&l👑 [КУРАТОР] &fНапишите в чат &eник игрока &f(можно даже оффлайн) для выдачи роли &b"
                        + h.rankId.toUpperCase(Locale.ROOT) + " &7(или &cотмена &7для выхода):"));
                ColorUtil.sound(p, "block.note_block.pling", 1f, 1.3f);
                return;
            }
            if (clicked.getType() == Material.PLAYER_HEAD && clicked.hasItemMeta() && clicked.getItemMeta() instanceof SkullMeta) {
                SkullMeta sm = (SkullMeta) clicked.getItemMeta();
                String targetName = null;
                if (sm.getOwningPlayer() != null && sm.getOwningPlayer().getName() != null) {
                    targetName = sm.getOwningPlayer().getName();
                } else if (sm.hasDisplayName()) {
                    targetName = org.bukkit.ChatColor.stripColor(sm.getDisplayName()).replace("●", "").trim();
                }
                if (targetName != null && !targetName.isEmpty()) {
                    p.closeInventory();
                    executeRankChange(p, targetName, h.rankId, null);
                }
            }
            return;
        }

        if ("roster".equals(h.page)) {
            if (slot == 49) { openMainCuratorGui(p, ""); return; }
            if (clicked.getType() == Material.PLAYER_HEAD && clicked.hasItemMeta()) {
                String stripped = org.bukkit.ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
                String nick = stripped.replace("● ОНЛАЙН", "").replace("○ ОФФЛАЙН", "").trim();
                if (!nick.isEmpty()) {
                    if (e.isRightClick()) {
                        p.closeInventory();
                        executeRankChange(p, nick, "default", "Снято в списке состава /curator");
                    } else {
                        openMainCuratorGui(p, nick);
                    }
                }
            }
            return;
        }

        if ("log".equals(h.page)) {
            if (slot == 49) openMainCuratorGui(p, "");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChatNickInput(AsyncPlayerChatEvent e) {
        final Player p = e.getPlayer();
        final String pendingRank = pendingNickForRank.remove(p.getUniqueId());
        if (pendingRank == null) return;

        e.setCancelled(true);
        final String msg = e.getMessage().trim();
        if ("отмена".equalsIgnoreCase(msg) || "cancel".equalsIgnoreCase(msg)) {
            p.sendMessage(ColorUtil.c("&eВвод ника отменён."));
            return;
        }
        final String targetNick = msg.split("\\s+")[0];
        new BukkitRunnable() {
            @Override
            public void run() {
                if (hasCuratorAccess(p)) {
                    executeRankChange(p, targetNick, pendingRank, null);
                }
            }
        }.runTask(plugin);
    }

    // =========================================================================
    // 5. ВЫВОД СОСТАВА И ЛОГОВ В ЧАТ + TAB-COMPLETE
    // =========================================================================
    private void sendStaffAndMediaList(CommandSender s) {
        Map<String, String> all = plugin.getStore().getAllSavedPlayerRanks();
        s.sendMessage(ColorUtil.c("&#86EFAC&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        s.sendMessage(ColorUtil.c(" &#86EFAC&l📋 ДЕЙСТВУЮЩИЙ СОСТАВ КП И МЕДИА KINGGRIEF:"));
        int count = 0;
        for (Map.Entry<String, String> en : all.entrySet()) {
            String rid = en.getValue();
            if (KP_RANKS.contains(rid) || MEDIA_RANKS.contains(rid)
                    || CURATOR_RANKS.contains(rid) || "techadmin".equals(rid) || "owner".equals(rid)) {
                RankManager.RankDef def = plugin.getRanks().byId(rid);
                boolean online = Bukkit.getPlayerExact(en.getKey()) != null;
                s.sendMessage(ColorUtil.c(" " + (online ? "&#00E676●" : "&7○") + " &f" + en.getKey()
                        + " &8— " + def.shortLabel + " &7(" + rid.toUpperCase(Locale.ROOT) + ")"));
                count++;
            }
        }
        if (count == 0) {
            s.sendMessage(ColorUtil.c(" &7Список КП и Медиа пока пуст."));
        }
        s.sendMessage(ColorUtil.c("&#86EFAC&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    private void sendAuditLogChat(CommandSender s) {
        List<String> logs = plugin.getStore().getCuratorAuditLog();
        s.sendMessage(ColorUtil.c("&#FDE047&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        s.sendMessage(ColorUtil.c(" &#FDE047&l📜 ПОСЛЕДНИЕ ВЫДАЧИ КУРАТОРОВ:"));
        if (logs.isEmpty()) {
            s.sendMessage(ColorUtil.c(" &7Записей пока нет."));
        } else {
            for (int i = 0; i < Math.min(15, logs.size()); i++) {
                s.sendMessage(ColorUtil.c(" " + logs.get(i)));
            }
        }
        s.sendMessage(ColorUtil.c("&#FDE047&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!hasCuratorAccess(sender)) return Collections.emptyList();
        if (args.length == 1) {
            List<String> base = new ArrayList<String>(Arrays.asList("kp", "media", "donate", "give", "remove", "list", "log"));
            for (Player on : Bukkit.getOnlinePlayers()) base.add(on.getName());
            return filterPrefix(base, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if ("kp".equals(sub) || "media".equals(sub) || "donate".equals(sub)
                    || "give".equals(sub) || "set".equals(sub) || "remove".equals(sub) || "demote".equals(sub)) {
                List<String> names = new ArrayList<String>();
                for (Player on : Bukkit.getOnlinePlayers()) names.add(on.getName());
                return filterPrefix(names, args[1]);
            }
            // /curator <ник> <ранг>
            List<String> allRanks = new ArrayList<String>();
            allRanks.addAll(KP_RANKS);
            allRanks.addAll(MEDIA_RANKS);
            allRanks.addAll(DONATE_RANKS);
            allRanks.addAll(CURATOR_RANKS);
            return filterPrefix(allRanks, args[1]);
        }
        if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if ("kp".equals(sub)) return filterPrefix(KP_RANKS, args[2]);
            if ("media".equals(sub)) return filterPrefix(MEDIA_RANKS, args[2]);
            if ("donate".equals(sub)) return filterPrefix(DONATE_RANKS, args[2]);
            if ("give".equals(sub) || "set".equals(sub) || "grant".equals(sub)) {
                List<String> allRanks = new ArrayList<String>();
                allRanks.addAll(KP_RANKS);
                allRanks.addAll(MEDIA_RANKS);
                allRanks.addAll(DONATE_RANKS);
                allRanks.addAll(CURATOR_RANKS);
                return filterPrefix(allRanks, args[2]);
            }
        }
        return Collections.emptyList();
    }

    private static List<String> filterPrefix(List<String> src, String pref) {
        String low = pref.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<String>();
        for (String s : src) {
            if (s.toLowerCase(Locale.ROOT).startsWith(low)) out.add(s);
        }
        return out;
    }

    private static int slotToGridIndex(int slot) {
        int row = slot / 9;
        int col = slot % 9;
        if (row < 1 || row > 3 || col < 1 || col > 7) return -1;
        return (row - 1) * 7 + (col - 1);
    }

    private static Material pickMaterialForRank(String rid, Material fallback) {
        if ("admin".equals(rid) || "dadmin".equals(rid)) return Material.REDSTONE_BLOCK;
        if ("mladmin".equals(rid) || "dmladmin".equals(rid)) return Material.BLAZE_POWDER;
        if ("glmoder".equals(rid) || "dglmoder".equals(rid)) return Material.AMETHYST_CLUSTER;
        if ("stmoder".equals(rid) || "dstmoder".equals(rid)) return Material.DIAMOND_CHESTPLATE;
        if ("moder".equals(rid) || "dmoder".equals(rid)) return Material.DIAMOND_SWORD;
        if ("mlmoder".equals(rid)) return Material.IRON_SWORD;
        if ("sthelper".equals(rid)) return Material.GOLDEN_APPLE;
        if ("helper".equals(rid) || "dhelper".equals(rid)) return Material.EMERALD;
        if ("kp".equals(rid) || "dkp".equals(rid)) return Material.SHIELD;
        if ("mediaplus".equals(rid)) return Material.NETHER_STAR;
        if ("media".equals(rid)) return Material.HEART_OF_THE_SEA;
        if ("yt".equals(rid)) return Material.RED_DYE;
        if ("twitch".equals(rid)) return Material.PURPLE_DYE;
        if ("tiktok".equals(rid)) return Material.MAGENTA_DYE;
        if ("downer".equals(rid)) return Material.DRAGON_EGG;
        if ("opka".equals(rid)) return Material.ENCHANTED_GOLDEN_APPLE;
        if ("sponsor".equals(rid)) return Material.BEACON;
        if ("king".equals(rid)) return Material.GOLD_BLOCK;
        if ("imperator".equals(rid)) return Material.NETHERITE_SWORD;
        if ("god".equals(rid)) return Material.TOTEM_OF_UNDYING;
        return fallback;
    }

    private static void fillBorder(Inventory inv, Material accent, Material dark) {
        ItemStack a = ItemFactory.icon(accent, " ");
        ItemStack d = ItemFactory.icon(dark, " ");
        int size = inv.getSize();
        for (int i = 0; i < size; i++) {
            int r = i / 9, c = i % 9;
            if (r == 0 || r == (size / 9) - 1 || c == 0 || c == 8) {
                inv.setItem(i, (i % 2 == 0) ? a : d);
            }
        }
    }

    private static String joinArgs(String[] args, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }
}
