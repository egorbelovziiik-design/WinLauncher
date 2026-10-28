package net.kinggrief.core;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * KINGGRIEF v5.8 — Эксклюзивная система OWNER:
 * 1) /razdacha — Интерактивное меню + команды раздачи Риликов, Денег, Кейсов (с выбором времени и суммы!),
 *    6 Глобальных Квестов всему серверу с живым прогресс-баром, Глобальный Бустер ×2 и кнопка [❤ Спасибо OWNER]!
 * 2) /txt <текст> — Красный текст на экране всех игроков ровно на 4 секунды (80 тиков) с плавным затуханием!
 * 3) /tex (/techwork) + экран при выключении сервера: «⚙ Тех работы!»
 */
public final class OwnerGiveawayManager implements Listener, CommandExecutor, TabCompleter {

    public static final class RazdachaHolder implements InventoryHolder {
        public final String pageType;
        public final String subArg;

        public RazdachaHolder(String pageType, String subArg) {
            this.pageType = pageType;
            this.subArg = subArg;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static final class PendingInput {
        final String kind; // "riliks", "money", "cases"
        final String caseId;

        PendingInput(String kind, String caseId) {
            this.kind = kind;
            this.caseId = caseId;
        }
    }

    private final KingGriefCore plugin;
    private final Map<UUID, PendingInput> awaitingChatInput = new HashMap<UUID, PendingInput>();

    // Active Timed Giveaway State
    private BossBar giveawayBar = null;
    private BukkitRunnable giveawayTask = null;
    private String currentThanksId = null;
    private final Set<UUID> thankedPlayers = new HashSet<UUID>();

    // Active Global Server Quest State
    private BossBar questBar = null;
    private BukkitRunnable questTimerTask = null;
    private String activeQuestType = null; // "mine", "kills", "mobs", "chat", "parkour"
    private String activeQuestTitle = "";
    private int activeQuestGoal = 0;
    private int activeQuestProgress = 0;
    private int activeQuestSecLeft = 0;
    private int activeQuestTotalSec = 1;
    private int activeQuestRelics = 0;
    private double activeQuestCoins = 0;
    private String activeQuestCase = null;

    // Global x2 Booster & Tech-Works Mode
    private volatile long globalX2UntilMs = 0L;
    private volatile boolean techWorkMode = false;

    public OwnerGiveawayManager(KingGriefCore plugin) {
        this.plugin = plugin;
        this.techWorkMode = plugin.getConfig().getBoolean("maintenance-mode", false);
    }

    public boolean isOwnerSender(CommandSender s) {
        if (!(s instanceof Player)) return true;
        Player p = (Player) s;
        RankManager.RankDef r = plugin.getRanks().getRank(p);
        return r != null && "owner".equalsIgnoreCase(r.id);
    }

    public boolean isTechWorkMode() {
        return techWorkMode;
    }

    public boolean isGlobalX2Active() {
        return System.currentTimeMillis() < globalX2UntilMs;
    }

    public void syncBarsForJoin(Player p) {
        if (giveawayBar != null) giveawayBar.addPlayer(p);
        if (questBar != null) questBar.addPlayer(p);
    }

    public void shutdown() {
        if (giveawayTask != null) { try { giveawayTask.cancel(); } catch (Throwable ignored) {} giveawayTask = null; }
        if (questTimerTask != null) { try { questTimerTask.cancel(); } catch (Throwable ignored) {} questTimerTask = null; }
        if (giveawayBar != null) { try { giveawayBar.removeAll(); } catch (Throwable ignored) {} giveawayBar = null; }
        if (questBar != null) { try { questBar.removeAll(); } catch (Throwable ignored) {} questBar = null; }
        awaitingChatInput.clear();
    }

    public void removeBarsForQuit(Player p) {
        if (giveawayBar != null) giveawayBar.removePlayer(p);
        if (questBar != null) questBar.removePlayer(p);
        awaitingChatInput.remove(p.getUniqueId());
    }

    /** Вызывается в onDisable() при выключении или перезагрузке сервера — выводит всем игрокам «Тех работы!» */
    public void kickAllForShutdownTechWorks() {
        if (giveawayBar != null) {
            giveawayBar.removeAll();
            giveawayBar = null;
        }
        if (questBar != null) {
            questBar.removeAll();
            questBar = null;
        }
        String kickScreen = buildTechWorksScreen();
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                p.kickPlayer(kickScreen);
            } catch (Throwable ignored) {}
        }
    }

    public String buildTechWorksScreen() {
        return ColorUtil.c("&#FF1744&l⚙ Тех работы!\n\n"
                + "&fСервер временно закрыт на обновление или перезагружается.\n"
                + "&7Пожалуйста, подождите пару минут и заходите снова!\n\n"
                + "&#FFD740Наш сайт: &#80D8FFhttps://kinggrief.vercel.app");
    }

    // =========================================================================
    // 1. ЭКРАН «Тех работы!» ПРИ ВХОДЕ И В СПИСКЕ СЕРВЕРОВ (КОГДА ВКЛЮЧЁН /tex)
    // =========================================================================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLoginTechWorks(AsyncPlayerPreLoginEvent e) {
        if (!techWorkMode) return;
        String name = e.getName();
        RankManager.RankDef r = plugin.getRanks().getRankByName(name);
        if (r != null && ("owner".equalsIgnoreCase(r.id) || "techadmin".equalsIgnoreCase(r.id))) {
            return; // Владелец и Тех.Админ всегда проходят на тех. работы!
        }
        e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST, buildTechWorksScreen());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPingTechWorks(ServerListPingEvent e) {
        if (techWorkMode) {
            e.setMotd(ColorUtil.c("&#FF1744&l⚙ Тех работы! &8│ &#FFD740&lKINGGRIEF NEW YEAR v6.3.2\n"
                    + "&fИдёт установка обновления — скоро откроемся!"));
            e.setMaxPlayers(0);
        }
    }

    @EventHandler
    public void onJoinSync(PlayerJoinEvent e) {
        syncBarsForJoin(e.getPlayer());
    }

    // =========================================================================
    // 2. КОМАНДЫ /razdacha, /txt, /tex И /thanksowner
    // =========================================================================
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);

        // Кликабельная кнопка благодарности во время раздачи!
        if ("thanksowner".equals(cmd)) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;
            if (currentThanksId == null || args.length < 1 || !currentThanksId.equals(args[0])) {
                p.sendMessage(ColorUtil.c("&7Эта раздача уже завершилась!"));
                return true;
            }
            if (thankedPlayers.contains(p.getUniqueId())) {
                p.sendMessage(ColorUtil.c("&eВы уже поблагодарили OWNER за эту раздачу!"));
                return true;
            }
            thankedPlayers.add(p.getUniqueId());
            plugin.getStore().addCoins(p.getName(), 2500);
            plugin.getStore().save();
            plugin.getHud().updatePlayerHud(p);
            ColorUtil.sound(p, "entity.experience_orb.pickup", 1.0f, 1.4f);
            Bukkit.broadcastMessage(ColorUtil.c("&#FF4081❤ &fИгрок &#FFD740" + p.getName()
                    + " &fпоблагодарил &#FF1744&lOWNER &fза раздачу и получил бонус &a+2 500$&f!"));
            return true;
        }

        // /txt <текст> — КРАСНЫЙ ТЕКСТ НА ЭКРАНЕ ВСЕХ ИГРОКОВ НА 4 СЕКУНДЫ + ПЛАВНОЕ ЗАТУХАНИЕ!
        if ("txt".equals(cmd)) {
            if (!isOwnerSender(sender)) {
                sender.sendMessage(ColorUtil.c("&#FF1744✖ Команда &f/txt &#FF1744доступна СТРОГО только владельцу (OWNER)!"));
                return true;
            }
            if (args.length == 0) {
                sender.sendMessage(ColorUtil.c("&#FF1744📢 Использование: &f/txt <ваш текст на экран всем игрокам>"));
                return true;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < args.length; i++) {
                if (i > 0) sb.append(' ');
                sb.append(args[i]);
            }
            broadcastOwnerRedScreenText(sender.getName(), sb.toString());
            return true;
        }

        // /tex [on|off] — РЕЖИМ «Тех работы!» ДЛЯ OWNER!
        if ("tex".equals(cmd)) {
            if (!isOwnerSender(sender)) {
                sender.sendMessage(ColorUtil.c("&#FF1744✖ Команда &f/tex &#FF1744доступна СТРОГО только владельцу (OWNER)!"));
                return true;
            }
            if (args.length >= 1) {
                if ("on".equalsIgnoreCase(args[0]) || "enable".equalsIgnoreCase(args[0])) techWorkMode = true;
                else if ("off".equalsIgnoreCase(args[0]) || "disable".equalsIgnoreCase(args[0])) techWorkMode = false;
                else techWorkMode = !techWorkMode;
            } else {
                techWorkMode = !techWorkMode;
            }
            plugin.getConfig().set("maintenance-mode", Boolean.valueOf(techWorkMode));
            plugin.saveConfig();

            if (techWorkMode) {
                int kicked = 0;
                for (Player on : Bukkit.getOnlinePlayers()) {
                    RankManager.RankDef rk = plugin.getRanks().getRank(on);
                    if (rk == null || (!"owner".equalsIgnoreCase(rk.id) && !"techadmin".equalsIgnoreCase(rk.id))) {
                        on.kickPlayer(buildTechWorksScreen());
                        kicked++;
                    }
                }
                sender.sendMessage(ColorUtil.c("&#FF1744&l⚙ [ТЕХ. РАБОТЫ] &fРежим &#FF1744ВКЛЮЧЁН&f! При входе игрокам пишет: &c«⚙ Тех работы!» &7(Кикнуто: " + kicked + ")"));
            } else {
                sender.sendMessage(ColorUtil.c("&#00E676&l⚙ [ТЕХ. РАБОТЫ] &fРежим &#00E676ВЫКЛЮЧЁН&f! Сервер открыт для всех игроков!"));
            }
            return true;
        }

        // /razdacha — ГЛАВНАЯ КОМАНДА РАЗДАЧ И СЕРВЕРНЫХ КВЕСТОВ OWNER!
        if (!isOwnerSender(sender)) {
            sender.sendMessage(ColorUtil.c("&#FF1744✖ Команда &f/razdacha &#FF1744доступна СТРОГО только владельцу (OWNER)!"));
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player) {
                openRazdachaMainGui((Player) sender);
            } else {
                sendRazdachaHelp(sender);
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        String host = sender instanceof Player ? sender.getName() : "OWNER";

        if ("stop".equals(sub)) {
            stopActiveGiveaway(sender);
            return true;
        }
        if ("riliks".equals(sub) || "relics".equals(sub) || "рилики".equals(sub)) {
            if (args.length < 3) {
                sender.sendMessage(ColorUtil.c("&#00E5FFИспользование: &f/razdacha riliks <время_сек> <сумма>"));
                return true;
            }
            int sec = parsePosInt(args[1], 30);
            int amt = parsePosInt(args[2], 100);
            startTimedGiveaway(host, "riliks", null, sec, amt);
            return true;
        }
        if ("money".equals(sub) || "coins".equals(sub) || "деньги".equals(sub)) {
            if (args.length < 3) {
                sender.sendMessage(ColorUtil.c("&#00E676Использование: &f/razdacha money <время_сек> <сумма>"));
                return true;
            }
            int sec = parsePosInt(args[1], 30);
            int amt = parsePosInt(args[2], 100000);
            startTimedGiveaway(host, "money", null, sec, amt);
            return true;
        }
        if ("cases".equals(sub) || "case".equals(sub) || "кейсы".equals(sub)) {
            if (args.length < 4) {
                sender.sendMessage(ColorUtil.c("&#FFD740Использование: &f/razdacha cases <тип_кейса> <время_сек> <кол-во>"));
                return true;
            }
            String caseId = args[1].toLowerCase(Locale.ROOT);
            int sec = parsePosInt(args[2], 30);
            int amt = parsePosInt(args[3], 1);
            startTimedGiveaway(host, "cases", caseId, sec, amt);
            return true;
        }
        if ("quest".equals(sub) || "квест".equals(sub)) {
            String qType = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "mine";
            startGlobalServerQuest(host, qType);
            return true;
        }
        if ("x2".equals(sub) || "boost".equals(sub)) {
            int mins = args.length >= 2 ? parsePosInt(args[1], 15) : 15;
            activateGlobalX2(host, mins);
            return true;
        }

        if (sender instanceof Player) {
            openRazdachaMainGui((Player) sender);
        } else {
            sendRazdachaHelp(sender);
        }
        return true;
    }

    private void sendRazdachaHelp(CommandSender s) {
        s.sendMessage(ColorUtil.c("&#FF1744&l🎁 КОМАНДЫ РАЗДАЧИ OWNER (/razdacha):"));
        s.sendMessage(ColorUtil.c(" &f/razdacha &8— &eОткрыть удобное GUI-меню раздач и квестов"));
        s.sendMessage(ColorUtil.c(" &f/razdacha riliks <сек> <сумма> &8— &bРаздача Риликов ❖"));
        s.sendMessage(ColorUtil.c(" &f/razdacha money <сек> <сумма> &8— &aРаздача Денег $"));
        s.sendMessage(ColorUtil.c(" &f/razdacha cases <тип> <сек> <кол-во> &8— &6Раздача Кейсов"));
        s.sendMessage(ColorUtil.c(" &f/razdacha quest <mine|kills|mobs|parkour|chat> &8— &dКвест всему серверу"));
        s.sendMessage(ColorUtil.c(" &f/razdacha x2 <минут> &8— &cГлобальный Бустер ×2 на весь сервер"));
        s.sendMessage(ColorUtil.c(" &f/txt <текст> &8— &cКрасный текст на экран всем игрокам (4 сек + плавное затухание)"));
        s.sendMessage(ColorUtil.c(" &f/tex [on|off] &8— &cВключить/выключить режим «Тех работы!»"));
    }

    /**
     * Выводит ярко-красный текст на экран ВСЕХ игроков ровно на 4 секунды (80 тиков) с плавным появлением и затуханием!
     */
    public void broadcastOwnerRedScreenText(String senderName, String rawText) {
        String clean = ChatColorStripCodesIfNeeded(rawText.trim());
        String line1 = clean;
        String line2 = "";
        if (clean.length() > 28) {
            int splitAt = clean.lastIndexOf(' ', 28);
            if (splitAt <= 6) splitAt = Math.min(clean.length(), 28);
            line1 = clean.substring(0, splitAt).trim();
            line2 = clean.substring(splitAt).trim();
        }

        String redTitle = ColorUtil.c("&#FF1744&l" + line1);
        String redSub = line2.isEmpty() ? ColorUtil.c("&#FF5252&o— Сообщение от " + senderName) : ColorUtil.c("&#FF1744&l" + line2);

        // fadeIn = 10 ticks (0.5s), stay = 80 ticks (РОВНО 4.0 СЕКУНДЫ!), fadeOut = 32 ticks (1.6s ПЛАВНОЕ ЗАТУХАНИЕ!)
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(redTitle, redSub, 10, 80, 32);
            ColorUtil.sound(p, "block.note_block.bell", 1.0f, 0.85f);
        }
        Bukkit.broadcastMessage(ColorUtil.c("&#FF1744&l📢 [ОБЪЯВЛЕНИЕ OWNER] &#FF5252&l" + clean));
    }

    private static String ChatColorStripCodesIfNeeded(String s) {
        return s == null ? "" : s;
    }

    // =========================================================================
    // 3. GUI-МЕНЮ /razdacha (РИЛИКИ, ДЕНЬГИ, 11 КЕЙСОВ, 6 КВЕСТОВ СЕРВЕРА, X2, ТЕХ.РАБОТЫ)
    // =========================================================================
    public void openRazdachaMainGui(Player p) {
        Inventory inv = Bukkit.createInventory(new RazdachaHolder("root", ""), 45,
                ColorUtil.c("&#FF1744&l🎁 ПАНЕЛЬ РАЗДАЧИ OWNER"));
        ItemStack red = ItemFactory.icon(Material.RED_STAINED_GLASS_PANE, " ");
        ItemStack black = ItemFactory.icon(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) {
            if (i < 9 || i >= 36 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, (i % 2 == 0) ? red : black);
            }
        }

        inv.setItem(11, ItemFactory.glowIcon(Material.DIAMOND, "&#00E5FF&l💎 Раздача РИЛИКОВ ❖",
                "&7Выбери длительность таймера и сумму",
                "&7Риликов для всех игроков онлайн!",
                "", "&#00E5FF▸ Нажми для настройки!"));

        inv.setItem(13, ItemFactory.glowIcon(Material.GOLD_BLOCK, "&#00E676&l💰 Раздача ДЕНЕГ ($)",
                "&7Выбери время отсчёта и сумму Монет $",
                "&7или введи свою сумму прямо в чат!",
                "", "&#00E676▸ Нажми для настройки!"));

        inv.setItem(15, ItemFactory.glowIcon(Material.MAGENTA_SHULKER_BOX, "&#FFD740&l📦 Раздача КЕЙСОВ (11 видов)",
                "&7Выбери любой из 11 Шалкер-Кейсов,",
                "&7длительность и количество ключей!",
                "", "&#FFD740▸ Выбрать кейс!"));

        inv.setItem(20, ItemFactory.glowIcon(Material.WRITABLE_BOOK, "&#E040FB&l📜 Дать КВЕСТ всему серверу!",
                "&7Запусти общий серверный квест с живым",
                "&7BossBar-прогрессом и мега-наградой всем!",
                "", "&#E040FB▸ Выбрать квест (5 видов)!"));

        inv.setItem(22, ItemFactory.glowIcon(Material.BLAZE_POWDER,
                "&#FF9100&l🔥 Глобальный Бустер ×2 &8[" + (isGlobalX2Active() ? "&aАКТИВЕН" : "&e15 МИН") + "&8]",
                "&7Включает праздник ×2 наград и радости",
                "&7с салютами для всех игроков онлайн!",
                "", "&#FF9100▸ Запустить на 15 минут!"));

        inv.setItem(24, ItemFactory.glowIcon(techWorkMode ? Material.REDSTONE_BLOCK : Material.ANVIL,
                "&#FF1744&l⚙ Режим «Тех работы!» &8[" + (techWorkMode ? "&cВКЛЮЧЁН" : "&aВЫКЛЮЧЕН") + "&8]",
                "&7При включении игрокам при входе пишет:",
                "&c«⚙ Тех работы!» &7(OWNER заходит свободно!)",
                "", "&#FFD740▸ Нажми для переключения (/tex)"));

        inv.setItem(31, ItemFactory.icon(Material.BARRIER, "&#FF5252&l✖ Остановить активную раздачу",
                "&7Досрочно отменить текущий таймер BossBar."));

        p.openInventory(inv);
    }

    private void openPresetAmountGui(Player p, String kind, String caseId) {
        String title = "riliks".equals(kind) ? "&#00E5FF&l💎 РАЗДАЧА РИЛИКОВ: ВЫБОР"
                : "money".equals(kind) ? "&#00E676&l💰 РАЗДАЧА ДЕНЕГ: ВЫБОР"
                : "&#FFD740&l📦 КЕЙС " + caseId.toUpperCase(Locale.ROOT) + ": ВЫБОР";
        Inventory inv = Bukkit.createInventory(new RazdachaHolder("preset_" + kind, caseId == null ? "" : caseId), 36, ColorUtil.c(title));
        ItemStack pane = ItemFactory.icon(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 36; i++) if (i < 9 || i >= 27 || i % 9 == 0 || i % 9 == 8) inv.setItem(i, pane);

        if ("riliks".equals(kind)) {
            inv.setItem(10, ItemFactory.glowIcon(Material.AMETHYST_SHARD, "&#00E5FF&l+100 Риликов ❖ &7(Таймер 20 сек)", "&a▸ Запустить в 1 клик!"));
            inv.setItem(11, ItemFactory.glowIcon(Material.DIAMOND, "&#00E5FF&l+250 Риликов ❖ &7(Таймер 30 сек)", "&a▸ Запустить в 1 клик!"));
            inv.setItem(12, ItemFactory.glowIcon(Material.EMERALD, "&#00E5FF&l+500 Риликов ❖ &7(Таймер 45 сек)", "&a▸ Запустить в 1 клик!"));
            inv.setItem(13, ItemFactory.glowIcon(Material.NETHER_STAR, "&#FFD740&l+1 000 Риликов ❖ &7(Таймер 60 сек)", "&a▸ МЕГА-РАЗДАЧА!"));
        } else if ("money".equals(kind)) {
            inv.setItem(10, ItemFactory.glowIcon(Material.GOLD_NUGGET, "&#00E676&l+100 000$ &7(Таймер 20 сек)", "&a▸ Запустить в 1 клик!"));
            inv.setItem(11, ItemFactory.glowIcon(Material.GOLD_INGOT, "&#00E676&l+500 000$ &7(Таймер 30 сек)", "&a▸ Запустить в 1 клик!"));
            inv.setItem(12, ItemFactory.glowIcon(Material.GOLD_BLOCK, "&#00E676&l+1 000 000$ &7(Таймер 45 сек)", "&a▸ Запустить в 1 клик!"));
            inv.setItem(13, ItemFactory.glowIcon(Material.NETHER_STAR, "&#FFD740&l+5 000 000$ &7(Таймер 60 сек)", "&a▸ МЕГА-РАЗДАЧА!"));
        } else {
            inv.setItem(10, ItemFactory.glowIcon(Material.TRIPWIRE_HOOK, "&#FFD740&l1 Ключ &7(Таймер 20 сек)", "&a▸ Запустить в 1 клик!"));
            inv.setItem(11, ItemFactory.glowIcon(Material.TRIPWIRE_HOOK, "&#FFD740&l3 Ключа &7(Таймер 30 сек)", "&a▸ Запустить в 1 клик!"));
            inv.setItem(12, ItemFactory.glowIcon(Material.CHEST, "&#FFD740&l5 Ключей &7(Таймер 45 сек)", "&a▸ Запустить в 1 клик!"));
            inv.setItem(13, ItemFactory.glowIcon(Material.ENDER_CHEST, "&#FF4081&l10 Ключей &7(Таймер 60 сек)", "&a▸ МЕГА-РАЗДАЧА!"));
        }

        inv.setItem(16, ItemFactory.glowIcon(Material.NAME_TAG, "&#FF4081&l✍ Своё время и сумма (Ввод в чат!)",
                "&7Нажми и напиши в обычный чат:",
                "&f<время_в_секундах> <количество>",
                "&7Пример: &e40 350"));
        inv.setItem(31, ItemFactory.icon(Material.ARROW, "&7« Назад в /razdacha"));
        p.openInventory(inv);
    }

    private void openCasePickerGui(Player p) {
        Inventory inv = Bukkit.createInventory(new RazdachaHolder("pick_case", ""), 36,
                ColorUtil.c("&#FFD740&l📦 ВЫБЕРИТЕ КЕЙС ДЛЯ РАЗДАЧИ"));
        ItemStack pane = ItemFactory.icon(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 36; i++) if (i < 9 || i >= 27) inv.setItem(i, pane);
        List<SpawnEngine.CasePedestal> list = plugin.getSpawnEngine().getCasePedestals();
        int[] slots = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22};
        for (int i = 0; i < list.size() && i < slots.length; i++) {
            SpawnEngine.CasePedestal cp = list.get(i);
            inv.setItem(slots[i], ItemFactory.make(cp.shulkerMat, 1, cp.title,
                    Arrays.asList(cp.subtitle, "", "&#FFD740▸ Нажми, чтобы выбрать время и кол-во!"),
                    true, "rzcase_" + cp.caseId, null, 0));
        }
        inv.setItem(31, ItemFactory.icon(Material.ARROW, "&7« Назад в /razdacha"));
        p.openInventory(inv);
    }

    private void openQuestPickerGui(Player p) {
        Inventory inv = Bukkit.createInventory(new RazdachaHolder("pick_quest", ""), 36,
                ColorUtil.c("&#E040FB&l📜 ГЛОБАЛЬНЫЕ КВЕСТЫ СЕРВЕРУ"));
        ItemStack pane = ItemFactory.icon(Material.PURPLE_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 36; i++) if (i < 9 || i >= 27 || i % 9 == 0 || i % 9 == 8) inv.setItem(i, pane);

        inv.setItem(10, ItemFactory.glowIcon(Material.NETHERITE_PICKAXE, "&#FF9100&l⛏ Квест #1: Шахтёрский Штурм",
                "&7Задача всем сервером: вскопать &e300 блоков/руд &7за 5 мин!",
                "&7Награда ВСЕМ онлайн: &b+150❖ &f+ &a100 000$ &f+ &6Кейс Джамшута!"));
        inv.setItem(11, ItemFactory.glowIcon(Material.NETHERITE_SWORD, "&#FF1744&l⚔ Квест #2: Кровавая Жатва",
                "&7Задача всем сервером: совершить &c15 убийств &7(PvP/Мобы) за 5 мин!",
                "&7Награда ВСЕМ онлайн: &b+200❖ &f+ &cДонат Кейс!"));
        inv.setItem(12, ItemFactory.glowIcon(Material.ZOMBIE_HEAD, "&#AEEA00&l🧟 Квест #3: Зачистка Монстров",
                "&7Задача всем сервером: убить &a50 мобов &7за 4 мин!",
                "&7Награда ВСЕМ онлайн: &b+120❖ &f+ &a75 000$ &f+ &b15 Снежинок ❄!"));
        inv.setItem(14, ItemFactory.glowIcon(Material.FEATHER, "&#40C4FF&l🏃 Квест #4: Небесный Марафон",
                "&7Задача: пройти чекпоинты/финиш паркура &b8 раз &7за 5 мин!",
                "&7Награда ВСЕМ онлайн: &b+180❖ &f+ &eЛегендарный Кейс!"));
        inv.setItem(15, ItemFactory.glowIcon(Material.FIREWORK_ROCKET, "&#FFD740&l💬 Квест #5: Новогодний Флешмоб",
                "&7Задача: написать в чат слово &eKINGGRIEF &7(&f8 раз &7за 2 мин)!",
                "&7Награда ВСЕМ онлайн: &b+100❖ &f+ &bНовогодний Кейс &f+ &a50 000$!"));

        inv.setItem(31, ItemFactory.icon(Material.ARROW, "&7« Назад в /razdacha"));
        p.openInventory(inv);
    }

    @EventHandler
    public void onRazdachaGuiClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Inventory top = e.getView().getTopInventory();
        if (top == null || !(top.getHolder() instanceof RazdachaHolder)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(top)) return;

        Player p = (Player) e.getWhoClicked();
        if (!isOwnerSender(p)) {
            p.closeInventory();
            return;
        }
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        RazdachaHolder h = (RazdachaHolder) top.getHolder();
        int slot = e.getSlot();

        if ("root".equals(h.pageType)) {
            if (slot == 11) openPresetAmountGui(p, "riliks", null);
            else if (slot == 13) openPresetAmountGui(p, "money", null);
            else if (slot == 15) openCasePickerGui(p);
            else if (slot == 20) openQuestPickerGui(p);
            else if (slot == 22) { p.closeInventory(); activateGlobalX2(p.getName(), 15); }
            else if (slot == 24) { p.performCommand("tex"); openRazdachaMainGui(p); }
            else if (slot == 31) { p.closeInventory(); stopActiveGiveaway(p); }
        } else if ("pick_case".equals(h.pageType)) {
            if (slot == 31) { openRazdachaMainGui(p); return; }
            String cid = ItemFactory.getCustomId(clicked);
            if (cid != null && cid.startsWith("rzcase_")) {
                openPresetAmountGui(p, "cases", cid.substring(7));
            }
        } else if (h.pageType.startsWith("preset_")) {
            if (slot == 31) { openRazdachaMainGui(p); return; }
            String kind = h.pageType.substring(7);
            String caseId = h.subArg;
            if (slot == 16) {
                p.closeInventory();
                awaitingChatInput.put(p.getUniqueId(), new PendingInput(kind, caseId));
                p.sendMessage(ColorUtil.c("&#FFD740&l✍ [РАЗДАЧА OWNER] &fНапишите в чат 2 числа через пробел: &#00E676<время_в_секундах> <сумма/количество>"));
                p.sendMessage(ColorUtil.c("&7Пример: &f45 500 &8(или напишите &ccancel &8для отмены)"));
                return;
            }
            int sec = slot == 10 ? 20 : slot == 11 ? 30 : slot == 12 ? 45 : slot == 13 ? 60 : 0;
            if (sec == 0) return;
            int amt;
            if ("riliks".equals(kind)) amt = slot == 10 ? 100 : slot == 11 ? 250 : slot == 12 ? 500 : 1000;
            else if ("money".equals(kind)) amt = slot == 10 ? 100000 : slot == 11 ? 500000 : slot == 12 ? 1000000 : 5000000;
            else amt = slot == 10 ? 1 : slot == 11 ? 3 : slot == 12 ? 5 : 10;
            p.closeInventory();
            startTimedGiveaway(p.getName(), kind, caseId, sec, amt);
        } else if ("pick_quest".equals(h.pageType)) {
            if (slot == 31) { openRazdachaMainGui(p); return; }
            String qt = slot == 10 ? "mine" : slot == 11 ? "kills" : slot == 12 ? "mobs" : slot == 14 ? "parkour" : slot == 15 ? "chat" : null;
            if (qt != null) {
                p.closeInventory();
                startGlobalServerQuest(p.getName(), qt);
            }
        }
    }

    // Ввод произвольного времени и суммы прямо в чат для OWNER!
    @EventHandler(priority = EventPriority.LOWEST)
    public void onOwnerCustomInputAndQuestChat(AsyncPlayerChatEvent e) {
        final Player p = e.getPlayer();
        final PendingInput pending = awaitingChatInput.remove(p.getUniqueId());
        if (pending != null) {
            e.setCancelled(true);
            final String msg = e.getMessage().trim();
            if ("cancel".equalsIgnoreCase(msg) || "отмена".equalsIgnoreCase(msg)) {
                p.sendMessage(ColorUtil.c("&eНастройка раздачи отменена."));
                return;
            }
            final String[] parts = msg.split("\\s+");
            if (parts.length < 2) {
                awaitingChatInput.put(p.getUniqueId(), pending);
                p.sendMessage(ColorUtil.c("&#FF1744✖ Введите 2 числа через пробел: &f<время_сек> <сумма> &7(или &fcancel&7)!"));
                return;
            }
            final int sec = parsePosInt(parts[0], 30);
            final int amt = parsePosInt(parts[1], 100);
            new BukkitRunnable() {
                @Override
                public void run() {
                    startTimedGiveaway(p.getName(), pending.kind, pending.caseId, sec, amt);
                }
            }.runTask(plugin);
            return;
        }

        // Прогресс глобального чат-квеста #5 (слово KINGGRIEF в чате!)
        if ("chat".equals(activeQuestType) && e.getMessage() != null
                && e.getMessage().toUpperCase(Locale.ROOT).contains("KINGGRIEF")) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    advanceServerQuest(1);
                }
            }.runTask(plugin);
        }
    }

    // =========================================================================
    // 4. ЗАПУСК И ОТСЧЁТ РАЗДАЧИ РИЛИКОВ / ДЕНЕГ / КЕЙСОВ!
    // =========================================================================
    public void startTimedGiveaway(final String hostName, final String kind, final String caseId, int rawSec, final int amount) {
        final int totalSec = Math.max(5, Math.min(600, rawSec));
        final int finalAmt = Math.max(1, amount);
        stopActiveGiveawaySilent();

        final String prizeDesc;
        BarColor barCol;
        if ("riliks".equals(kind)) {
            prizeDesc = "&#00E5FF+" + finalAmt + " Риликов ❖";
            barCol = BarColor.BLUE;
        } else if ("money".equals(kind)) {
            prizeDesc = "&#00E676+" + ColorUtil.fmtMoney(finalAmt) + "$ Монет";
            barCol = BarColor.GREEN;
        } else {
            prizeDesc = "&#FFD740+" + finalAmt + "× Кейс [" + (caseId == null ? "donate" : caseId.toUpperCase(Locale.ROOT)) + "]";
            barCol = BarColor.PINK;
        }

        currentThanksId = Long.toHexString(System.currentTimeMillis() & 0xFFFFFFL);
        thankedPlayers.clear();

        giveawayBar = Bukkit.createBossBar(
                ColorUtil.c("&#FF1744&l🎁 РАЗДАЧА ОТ OWNER " + hostName + " &8• " + prizeDesc + " &8• &fЧерез &#FFD740" + totalSec + "с!"),
                barCol, BarStyle.SEGMENTED_10);
        giveawayBar.setProgress(1.0);
        for (Player on : Bukkit.getOnlinePlayers()) {
            giveawayBar.addPlayer(on);
            on.sendTitle(ColorUtil.c("&#FF1744&l🎁 МЕГА-РАЗДАЧА!"),
                    ColorUtil.c("&fВсе получат " + prizeDesc + " &fчерез &e" + totalSec + " сек.!"), 8, 55, 15);
            ColorUtil.sound(on, "entity.ender_dragon.growl", 0.55f, 1.35f);
        }

        Bukkit.broadcastMessage(ColorUtil.c("&#FF1744&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        Bukkit.broadcastMessage(ColorUtil.c(" &#FFD740&l🎁 ВЛАДЕЛЕЦ &#FF1744&l" + hostName + " &#FFD740&lЗАПУСТИЛ РАЗДАЧУ!"));
        Bukkit.broadcastMessage(ColorUtil.c(" &fНаграда каждому онлайн: " + prizeDesc));
        Bukkit.broadcastMessage(ColorUtil.c(" &fДо выдачи призов осталось: &#FFD740" + totalSec + " сек.! &7(Не выходите с сервера!)"));
        sendClickableThanksLine();
        Bukkit.broadcastMessage(ColorUtil.c("&#FF1744&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));

        giveawayTask = new BukkitRunnable() {
            int left = totalSec;

            @Override
            public void run() {
                left--;
                if (left > 0) {
                    if (giveawayBar != null) {
                        giveawayBar.setProgress(Math.max(0.0, Math.min(1.0, (double) left / (double) totalSec)));
                        giveawayBar.setTitle(ColorUtil.c("&#FF1744&l🎁 РАЗДАЧА OWNER &8• " + prizeDesc + " &8• &fВыдача через: &#FFD740" + left + " сек."));
                    }
                    if (left == 10 || left <= 3) {
                        for (Player on : Bukkit.getOnlinePlayers()) {
                            ColorUtil.sound(on, "block.note_block.pling", 0.9f, left <= 3 ? 1.6f : 1.2f);
                            ColorUtil.actionBar(on, "&#FFD740🎁 До раздачи " + prizeDesc + " &fосталось &#FF1744" + left + " сек.!");
                        }
                    }
                    return;
                }

                // ФИНАЛ ТАЙМЕРА — ВЫДАЧА НАГРАДЫ ВСЕМ ИГРОКАМ ОНЛАЙН + САЛЮТ!
                cancel();
                giveawayTask = null;
                if (giveawayBar != null) {
                    giveawayBar.removeAll();
                    giveawayBar = null;
                }

                String safeCase = (caseId == null || caseId.isEmpty()) ? "donate" : caseId;
                int countPlayers = 0;
                for (Player on : Bukkit.getOnlinePlayers()) {
                    countPlayers++;
                    if ("riliks".equals(kind)) {
                        plugin.getStore().addRelics(on.getName(), finalAmt);
                    } else if ("money".equals(kind)) {
                        plugin.getStore().addCoins(on.getName(), finalAmt);
                    } else {
                        plugin.getStore().addCaseKeys(on.getName(), safeCase, finalAmt);
                    }
                    plugin.getHud().updatePlayerHud(on);
                    on.sendTitle(ColorUtil.c("&#00E676&l🎉 РАЗДАЧА ВЫДАНА!"),
                            ColorUtil.c("&fВы получили " + prizeDesc + "&f!"), 6, 55, 15);
                    ColorUtil.sound(on, "ui.toast.challenge_complete", 1.0f, 1.2f);
                    if (plugin.getSpawnEngine().isInSpawnWorld(on.getLocation())) {
                        ColorUtil.spawnFirework(on.getLocation());
                    }
                }
                plugin.getStore().save();
                Bukkit.broadcastMessage(ColorUtil.c("&#00E676&l🎉 [РАЗДАЧА ЗАВЕРШЕНА] &fВсе &#FFD740" + countPlayers
                        + " игроков &fонлайн получили " + prizeDesc + " &fот &#FF1744&l" + hostName + "&f!"));
            }
        };
        giveawayTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void sendClickableThanksLine() {
        if (currentThanksId == null) return;
        TextComponent prefix = new TextComponent(ColorUtil.c(" &#FF4081▸ Нажми в чате: "));
        TextComponent btn = new TextComponent(ColorUtil.c("&#00E676&l[ ❤ СКАЗАТЬ СПАСИБО OWNER (+2 500$) ]"));
        btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/thanksowner " + currentThanksId));
        btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ColorUtil.c("&aНажми, чтобы поблагодарить OWNER и получить +2 500$!")).create()));
        prefix.addExtra(btn);
        for (Player on : Bukkit.getOnlinePlayers()) {
            try {
                on.spigot().sendMessage(prefix);
            } catch (Throwable ignored) {}
        }
    }

    private void stopActiveGiveawaySilent() {
        if (giveawayTask != null) {
            try { giveawayTask.cancel(); } catch (Throwable ignored) {}
            giveawayTask = null;
        }
        if (giveawayBar != null) {
            giveawayBar.removeAll();
            giveawayBar = null;
        }
    }

    private void stopActiveGiveaway(CommandSender s) {
        stopActiveGiveawaySilent();
        if (questTimerTask != null) {
            try { questTimerTask.cancel(); } catch (Throwable ignored) {}
            questTimerTask = null;
        }
        if (questBar != null) {
            questBar.removeAll();
            questBar = null;
        }
        activeQuestType = null;
        s.sendMessage(ColorUtil.c("&#FFD740✔ Активная раздача / серверный квест остановлены."));
    }

    private void activateGlobalX2(String hostName, int mins) {
        globalX2UntilMs = System.currentTimeMillis() + mins * 60_000L;
        for (Player on : Bukkit.getOnlinePlayers()) {
            plugin.getStore().addSnowflakes(on.getName(), 10);
            ColorUtil.sound(on, "entity.player.levelup", 1.0f, 1.2f);
            on.sendTitle(ColorUtil.c("&#FF9100&l🔥 ПРАЗДНИК OWNER ×2!"),
                    ColorUtil.c("&fВсем выдано &b+10 Снежинок ❄ &f+ Бустер на &e" + mins + " мин!"), 8, 50, 15);
        }
        plugin.getStore().save();
        Bukkit.broadcastMessage(ColorUtil.c("&#FF9100&l🔥 [БУСТЕР OWNER] &fВладелец &#FF1744" + hostName
                + " &fвключил серверный праздник на &#FFD740" + mins + " мин. &fи подарил всем &b+10 Снежинок ❄&f!"));
    }

    // =========================================================================
    // 5. ГЛОБАЛЬНЫЕ СЕРВЕРНЫЕ КВЕСТЫ ИЗ /razdacha!
    // =========================================================================
    public void startGlobalServerQuest(String hostName, String qType) {
        if (questTimerTask != null) {
            try { questTimerTask.cancel(); } catch (Throwable ignored) {}
        }
        if (questBar != null) {
            questBar.removeAll();
            questBar = null;
        }

        activeQuestProgress = 0;
        if ("kills".equals(qType)) {
            activeQuestType = "kills";
            activeQuestTitle = "⚔ Кровавая Жатва (Убийства)";
            activeQuestGoal = 15;
            activeQuestTotalSec = 300;
            activeQuestRelics = 200;
            activeQuestCoins = 50000;
            activeQuestCase = "donate";
        } else if ("mobs".equals(qType)) {
            activeQuestType = "mobs";
            activeQuestTitle = "🧟 Зачистка Монстров";
            activeQuestGoal = 50;
            activeQuestTotalSec = 240;
            activeQuestRelics = 120;
            activeQuestCoins = 75000;
            activeQuestCase = null;
        } else if ("parkour".equals(qType)) {
            activeQuestType = "parkour";
            activeQuestTitle = "🏃 Небесный Марафон (/warp parkour)";
            activeQuestGoal = 8;
            activeQuestTotalSec = 300;
            activeQuestRelics = 180;
            activeQuestCoins = 60000;
            activeQuestCase = "legendary";
        } else if ("chat".equals(qType)) {
            activeQuestType = "chat";
            activeQuestTitle = "💬 Напиши KINGGRIEF в чат";
            activeQuestGoal = 8;
            activeQuestTotalSec = 150;
            activeQuestRelics = 100;
            activeQuestCoins = 50000;
            activeQuestCase = "summer";
        } else {
            activeQuestType = "mine";
            activeQuestTitle = "⛏ Шахтёрский Штурм (Вскопать блоки)";
            activeQuestGoal = 300;
            activeQuestTotalSec = 300;
            activeQuestRelics = 150;
            activeQuestCoins = 100000;
            activeQuestCase = "djamshut";
        }
        activeQuestSecLeft = activeQuestTotalSec;

        questBar = Bukkit.createBossBar(formatQuestBarTitle(), BarColor.PURPLE, BarStyle.SEGMENTED_10);
        questBar.setProgress(0.0);
        for (Player on : Bukkit.getOnlinePlayers()) {
            questBar.addPlayer(on);
            on.sendTitle(ColorUtil.c("&#E040FB&l📜 КВЕСТ ОТ OWNER!"),
                    ColorUtil.c("&f" + activeQuestTitle + " &8(&e0/" + activeQuestGoal + "&8)"), 8, 55, 15);
            ColorUtil.sound(on, "block.END_PORTAL_FRAME.fill".toLowerCase(Locale.ROOT), 1.0f, 1.15f);
        }

        Bukkit.broadcastMessage(ColorUtil.c("&#E040FB&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        Bukkit.broadcastMessage(ColorUtil.c(" &#FFD740&l📜 OWNER &#FF1744&l" + hostName + " &#FFD740&lДАЛ КВЕСТ ВСЕМУ СЕРВЕРУ!"));
        Bukkit.broadcastMessage(ColorUtil.c(" &fЗадание: &#80D8FF" + activeQuestTitle + " &7(Цель: &e" + activeQuestGoal + "&7)"));
        Bukkit.broadcastMessage(ColorUtil.c(" &fНаграда ВСЕМ онлайн: &#00E5FF+" + activeQuestRelics + "❖ &f+ &a+"
                + ColorUtil.fmtMoney(activeQuestCoins) + "$"
                + (activeQuestCase != null ? " &f+ &#FFD740Кейс [" + activeQuestCase.toUpperCase(Locale.ROOT) + "]" : "")));
        Bukkit.broadcastMessage(ColorUtil.c("&#E040FB&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));

        questTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (activeQuestType == null) {
                    cancel();
                    return;
                }
                activeQuestSecLeft--;
                if (activeQuestSecLeft <= 0) {
                    cancel();
                    questTimerTask = null;
                    if (questBar != null) {
                        questBar.removeAll();
                        questBar = null;
                    }
                    activeQuestType = null;
                    Bukkit.broadcastMessage(ColorUtil.c("&#FF5252⌛ Время серверного квеста истекло! Повезёт в следующий раз!"));
                    return;
                }
                if (questBar != null) {
                    questBar.setTitle(formatQuestBarTitle());
                }
            }
        };
        questTimerTask.runTaskTimer(plugin, 20L, 20L);
    }

    private String formatQuestBarTitle() {
        return ColorUtil.c("&#E040FB&l📜 КВЕСТ: &f" + activeQuestTitle + " &8• &#00E676"
                + activeQuestProgress + "/" + activeQuestGoal + " &8• &e" + ColorUtil.fmtTime(activeQuestSecLeft));
    }

    public void advanceServerQuest(int delta) {
        if (activeQuestType == null || delta <= 0) return;
        activeQuestProgress = Math.min(activeQuestGoal, activeQuestProgress + delta);
        if (questBar != null) {
            questBar.setProgress(Math.max(0.0, Math.min(1.0, (double) activeQuestProgress / (double) Math.max(1, activeQuestGoal))));
            questBar.setTitle(formatQuestBarTitle());
        }
        if (activeQuestProgress >= activeQuestGoal) {
            completeGlobalServerQuest();
        }
    }

    private void completeGlobalServerQuest() {
        String titleDone = activeQuestTitle;
        int rel = activeQuestRelics;
        double coins = activeQuestCoins;
        String caseKey = activeQuestCase;
        activeQuestType = null;
        if (questTimerTask != null) {
            try { questTimerTask.cancel(); } catch (Throwable ignored) {}
            questTimerTask = null;
        }
        if (questBar != null) {
            questBar.removeAll();
            questBar = null;
        }

        for (Player on : Bukkit.getOnlinePlayers()) {
            if (rel > 0) plugin.getStore().addRelics(on.getName(), rel);
            if (coins > 0) plugin.getStore().addCoins(on.getName(), coins);
            if (caseKey != null) plugin.getStore().addCaseKeys(on.getName(), caseKey, 1);
            else plugin.getStore().addSnowflakes(on.getName(), 15);
            plugin.getHud().updatePlayerHud(on);
            on.sendTitle(ColorUtil.c("&#00E676&l🏆 КВЕСТ ВЫПОЛНЕН!"),
                    ColorUtil.c("&fВы получили &b+" + rel + "❖ &fи &a+" + ColorUtil.fmtMoney(coins) + "$!"), 6, 55, 15);
            ColorUtil.sound(on, "ui.toast.challenge_complete", 1.0f, 1.15f);
        }
        plugin.getStore().save();
        Bukkit.broadcastMessage(ColorUtil.c("&#00E676&l🏆 [СЕРВЕРНЫЙ КВЕСТ ВЫПОЛНЕН!] &fЗадача «" + titleDone
                + "» завершена! Все игроки онлайн получили &#00E5FF+" + rel + "❖ &fи &a+" + ColorUtil.fmtMoney(coins) + "$&f!"));
    }

    // Трекинг прогресса серверных квестов (Шахта, Убийства, Мобы, Паркур)!
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuestMineTrack(BlockBreakEvent e) {
        if ("mine".equals(activeQuestType)) {
            advanceServerQuest(1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuestKillTrack(EntityDeathEvent e) {
        if (e.getEntity().getKiller() == null) return;
        if ("kills".equals(activeQuestType)) {
            advanceServerQuest(1);
        } else if ("mobs".equals(activeQuestType) && (e.getEntity() instanceof Monster || e.getEntity().getScoreboardTags().contains("kg_farm_mob"))) {
            advanceServerQuest(1);
        }
    }

    public void notifyParkourProgress() {
        if ("parkour".equals(activeQuestType)) {
            advanceServerQuest(1);
        }
    }

    private static int parsePosInt(String s, int def) {
        try {
            return Math.max(1, Integer.parseInt(s.replaceAll("[^0-9]", "")));
        } catch (Exception e) {
            return def;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if ("tex".equals(cmd) && args.length == 1) {
            return Arrays.asList("on", "off");
        }
        if ("razdacha".equals(cmd)) {
            if (args.length == 1) {
                return Arrays.asList("riliks", "money", "cases", "quest", "x2", "stop");
            }
            if (args.length == 2 && "cases".equalsIgnoreCase(args[0])) {
                return Arrays.asList(KingGriefCore.ALL_CASE_IDS);
            }
            if (args.length == 2 && "quest".equalsIgnoreCase(args[0])) {
                return Arrays.asList("mine", "kills", "mobs", "parkour", "chat");
            }
            if (args.length == 2 && ("riliks".equalsIgnoreCase(args[0]) || "money".equalsIgnoreCase(args[0]))) {
                return Arrays.asList("20", "30", "45", "60");
            }
        }
        return Collections.emptyList();
    }
}
