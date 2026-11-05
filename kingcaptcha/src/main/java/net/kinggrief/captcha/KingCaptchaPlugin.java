package net.kinggrief.captcha;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** A deliberately small, session-only human check, not a network/DDoS firewall. */
public final class KingCaptchaPlugin extends JavaPlugin implements Listener {
    private static final String PREFIX = "&b&l✦ KingCaptcha &8» &r";
    private static final int[] ANSWER_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] PROGRESS_SLOTS = {28, 29, 30, 31, 32};
    private static final int MAX_FAILURE_RECORDS = 8192;
    private static final long FAILURE_WINDOW_MS = TimeUnit.MINUTES.toMillis(10);

    private static final Choice[] CHOICES = {
            new Choice(Material.DIAMOND, "АЛМАЗ"), new Choice(Material.EMERALD, "ИЗУМРУД"),
            new Choice(Material.GOLD_INGOT, "ЗОЛОТО"), new Choice(Material.IRON_INGOT, "ЖЕЛЕЗО"),
            new Choice(Material.AMETHYST_SHARD, "АМЕТИСТ"), new Choice(Material.REDSTONE, "РЕДСТОУН"),
            new Choice(Material.LAPIS_LAZULI, "ЛАЗУРИТ"), new Choice(Material.ENDER_PEARL, "ЖЕМЧУГ КРАЯ"),
            new Choice(Material.BLAZE_ROD, "ОГНЕННЫЙ СТЕРЖЕНЬ"), new Choice(Material.NETHER_STAR, "ЗВЕЗДА НЕЗЕРА"),
            new Choice(Material.GOLDEN_APPLE, "ЗОЛОТОЕ ЯБЛОКО"), new Choice(Material.SNOWBALL, "СНЕЖОК"),
            new Choice(Material.CLOCK, "ЧАСЫ"), new Choice(Material.FEATHER, "ПЕРО")
    };

    private final SecureRandom random = new SecureRandom();
    // Async chat/pre-login only read these concurrent structures. Session fields
    // and all Bukkit inventory/player operations are confined to the main thread.
    private final Map<UUID, Session> pending = new ConcurrentHashMap<>();
    private final Set<UUID> verified = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Failures> failures = new ConcurrentHashMap<>();
    private int rounds;
    private int maxMistakes;
    private int timeoutSeconds;
    private int cooldownSeconds;
    private boolean bossbar;
    private int ticks;

    private record Choice(Material material, String label) { }
    private record Failures(int mistakes, long expiresAt, long blockedUntil) { }

    private static final class CaptchaHolder implements InventoryHolder {
        private final Session session;
        private Inventory inventory;
        private CaptchaHolder(Session session) { this.session = session; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private static final class Session {
        private final UUID player;
        private final int rounds;
        private final int maxMistakes;
        private final int cooldownSeconds;
        private final int timeoutSeconds;
        private long deadlineNanos;
        private int step;
        private int mistakes;
        private int expectedSlot;
        private String lastTarget = "";
        private String target = "";
        private Inventory menu;
        private BossBar bar;
        private boolean transitioning;
        private boolean ending;
        private boolean displayed;
        private Session(UUID player, int rounds, int maxMistakes, int timeoutSeconds, int cooldownSeconds, int mistakes) {
            this.player = player;
            this.rounds = rounds;
            this.maxMistakes = maxMistakes;
            this.timeoutSeconds = timeoutSeconds;
            this.cooldownSeconds = cooldownSeconds;
            this.mistakes = mistakes;
            this.deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        }
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        readSettings();
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        // Handles an administrative plugin load with already-connected players;
        // a normal server restart is still strongly recommended over /reload.
        for (Player player : Bukkit.getOnlinePlayers()) begin(player);
        getLogger().info("KingCaptcha 1.0.0: проверка хаба включена (только текущая сессия).");
    }

    @Override public void onDisable() {
        // Clear first: closing a menu must not schedule a reopen on a plugin
        // which is already being disabled.
        List<Session> active = new ArrayList<>(pending.values());
        pending.clear();
        verified.clear();
        failures.clear();
        for (Session session : active) {
            if (session.bar != null) session.bar.removeAll();
            Player player = Bukkit.getPlayer(session.player);
            if (player != null && owns(player.getOpenInventory(), session)) player.closeInventory();
        }
    }

    private void readSettings() {
        rounds = clamp(getConfig().getInt("rounds", 3), 1, 5);
        maxMistakes = clamp(getConfig().getInt("max-mistakes", 3), 1, 10);
        timeoutSeconds = clamp(getConfig().getInt("timeout-seconds", 90), 10, 300);
        cooldownSeconds = clamp(getConfig().getInt("retry-cooldown-seconds", 15), 0, 600);
        bossbar = getConfig().getBoolean("bossbar", true);
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }

    /** Public, read-only integration point used by KingHub 1.2.4; safe to read on an async chat thread. */
    public boolean isVerified(UUID id) { return id != null && verified.contains(id); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) { begin(event.getPlayer()); }

    private void begin(Player player) {
        UUID id = player.getUniqueId();
        verified.remove(id);
        Session previous = pending.remove(id);
        if (previous != null && previous.bar != null) previous.bar.removeAll();
        if (player.hasPermission("kingcaptcha.bypass")) {
            verified.add(id);
            return;
        }
        long now = System.currentTimeMillis();
        Failures prior = failures.get(id);
        if (prior != null && prior.blockedUntil() > now) {
            player.kickPlayer(color(PREFIX + "&cПодождите перед новой попыткой."));
            return;
        }
        if (prior != null && (prior.expiresAt() <= now || prior.blockedUntil() > 0)) {
            failures.remove(id, prior);
            prior = null;
        }
        int mistakes = prior == null ? 0 : Math.min(prior.mistakes(), maxMistakes - 1);
        Session session = new Session(id, rounds, maxMistakes, timeoutSeconds, cooldownSeconds, mistakes);
        pending.put(id, session);
        if (bossbar) {
            session.bar = Bukkit.createBossBar(color("&b✦ &fПроверка безопасности"), BarColor.BLUE, BarStyle.SEGMENTED_10);
            session.bar.addPlayer(player);
        }
        player.sendMessage(color(PREFIX + "&fСначала пройдите проверку: найдите предмет, указанный в заголовке меню."));
        player.sendMessage(color("&7После проверки войдите через &f/login &7или &f/register&7, если потребуется."));
        player.sendTitle(color("&b&l✦ ПРОВЕРКА"), color("&fВыберите предмет из заголовка меню"), 5, 50, 10);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (active(player, session)) nextRound(player, session);
        }, 10L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Session session = pending.remove(id);
        if (session != null && session.bar != null) session.bar.removeAll();
        verified.remove(id);
        // Partial mistakes intentionally survive a reconnect for ten minutes.
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        Failures record = failures.get(event.getUniqueId());
        if (record == null) return;
        long left = record.blockedUntil() - System.currentTimeMillis();
        if (left > 0) {
            long seconds = (left + 999) / 1000;
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    color("&b&l✦ KINGGRIEF\n\n&cПроверка не пройдена.\n&7Повторите вход через &f" + seconds + " сек."));
        }
    }

    private boolean active(Player player, Session session) {
        return player.isOnline() && pending.get(session.player) == session && !session.ending;
    }

    private void nextRound(Player player, Session session) {
        if (!active(player, session)) return;
        List<Choice> pool = new ArrayList<>(Arrays.asList(CHOICES));
        Collections.shuffle(pool, random);
        List<Choice> options = new ArrayList<>(pool.subList(0, ANSWER_SLOTS.length));
        List<Integer> targets = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            if (!options.get(i).label().equals(session.lastTarget)) targets.add(i);
        }
        int targetIndex = targets.get(random.nextInt(targets.size()));
        session.expectedSlot = ANSWER_SLOTS[targetIndex];
        session.target = options.get(targetIndex).label();
        session.lastTarget = session.target;

        CaptchaHolder holder = new CaptchaHolder(session);
        String title = color("&b&l✦ " + (session.step + 1) + "/" + session.rounds + " &8│ &f" + session.target);
        Inventory menu = Bukkit.createInventory(holder, 36, title);
        holder.inventory = menu;
        ItemStack blue = item(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ", List.of());
        ItemStack dark = item(Material.BLUE_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < 36; slot++) menu.setItem(slot, slot % 2 == 0 ? blue : dark);
        menu.setItem(4, item(Material.NAME_TAG, "&b&lНАЙДИ: &f&l" + session.target,
                List.of("&7Нажми на такой предмет ниже.", "&7Название также написано сверху.", "", "&eШаг &f" + (session.step + 1) + "&7/&f" + session.rounds,
                        "&eОшибок: &f" + session.mistakes + "&7/&f" + session.maxMistakes)));
        for (int i = 0; i < options.size(); i++) {
            Choice choice = options.get(i);
            menu.setItem(ANSWER_SLOTS[i], item(choice.material(), "&f&l" + choice.label(),
                    List.of("&7Сравните с названием вверху.", "", "&b▶ &fНажмите, чтобы выбрать")));
        }
        menu.setItem(22, item(Material.CLOCK, "&e&lВАШЕ ВРЕМЯ",
                List.of("&7Осталось: &f" + secondsLeft(session) + " сек.", "&7Закрыли меню? Оно откроется снова.")));
        for (int i = 0; i < session.rounds; i++) {
            boolean complete = i < session.step;
            menu.setItem(PROGRESS_SLOTS[i], item(complete ? Material.LIME_DYE : Material.GRAY_DYE,
                    (complete ? "&a✔ Пройдено" : "&7○ Ожидает") + " &8· &f" + (i + 1), List.of()));
        }
        session.menu = menu;
        open(player, session);
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        List<String> lines = new ArrayList<>();
        for (String line : lore) lines.add(color(line));
        meta.setLore(lines);
        item.setItemMeta(meta);
        return item;
    }

    private static boolean owns(InventoryView view, Session session) {
        if (view == null) return false;
        InventoryHolder holder = view.getTopInventory().getHolder();
        return holder instanceof CaptchaHolder && ((CaptchaHolder) holder).session == session
                && view.getTopInventory().equals(session.menu);
    }

    private void open(Player player, Session session) {
        if (!active(player, session) || session.menu == null || owns(player.getOpenInventory(), session)) return;
        session.transitioning = true;
        try {
            InventoryView view = player.openInventory(session.menu);
            session.displayed = view != null && owns(view, session);
        } finally {
            session.transitioning = false;
        }
    }

    private long secondsLeft(Session session) {
        return Math.max(0, (session.deadlineNanos - System.nanoTime() + 999_999_999L) / 1_000_000_000L);
    }

    private void tick() {
        ticks++;
        long now = System.currentTimeMillis();
        if (ticks % 60 == 0) failures.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        for (Session session : new ArrayList<>(pending.values())) {
            Player player = Bukkit.getPlayer(session.player);
            if (player == null || !active(player, session)) continue;
            long left = secondsLeft(session);
            if (left <= 0) { fail(player, session, "&cВремя проверки истекло."); continue; }
            if (session.bar != null) {
                session.bar.setTitle(color("&b✦ &f" + (session.step + 1) + "/" + session.rounds + " &8│ &fНАЙДИ: &e" + session.target + " &8│ &f" + left + " сек."));
                session.bar.setProgress(Math.max(0.0, Math.min(1.0, (double) left / session.timeoutSeconds)));
            }
            if (session.menu != null && !owns(player.getOpenInventory(), session)) open(player, session);
        }
    }

    private void remember(Session session, long blockedUntil) {
        long now = System.currentTimeMillis();
        if (failures.size() >= MAX_FAILURE_RECORDS && !failures.containsKey(session.player)) {
            failures.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
            // Bounded memory under a UUID flood; this is not a DDoS filter.
            if (failures.size() >= MAX_FAILURE_RECORDS) return;
        }
        failures.put(session.player, new Failures(session.mistakes, Math.max(now + FAILURE_WINDOW_MS, blockedUntil), blockedUntil));
    }

    private void fail(Player player, Session session, String reason) {
        if (!active(player, session)) return;
        session.ending = true;
        long until = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(session.cooldownSeconds);
        remember(session, session.cooldownSeconds > 0 ? until : 0);
        if (session.bar != null) session.bar.removeAll();
        // Kicks and inventory changes from InventoryClickEvent must happen after the event.
        Bukkit.getScheduler().runTask(this, () -> {
            if (player.isOnline() && pending.get(session.player) == session) {
                player.kickPlayer(color("&b&l✦ KINGGRIEF\n\n" + reason
                        + "\n&7Попробуйте ещё раз" + (session.cooldownSeconds > 0 ? " через &f" + session.cooldownSeconds + " сек." : ".")));
            }
        });
    }

    private void succeed(Player player, Session session) {
        if (!active(player, session)) return;
        session.ending = true;
        pending.remove(session.player, session);
        verified.add(session.player);
        failures.remove(session.player);
        if (session.bar != null) session.bar.removeAll();
        Bukkit.getScheduler().runTask(this, () -> {
            if (!player.isOnline() || !isVerified(session.player)) return;
            if (owns(player.getOpenInventory(), session)) player.closeInventory();
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.25f);
            player.sendTitle(color("&a&l✔ ПРОВЕРКА ПРОЙДЕНА"), color("&fДобро пожаловать в &bKingGrief!"), 5, 50, 10);
            player.sendMessage(color(PREFIX + "&aГотово! &fТеперь войдите через &e/login &fили &e/register&f, если требуется, и выберите сервер."));
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Session session = pending.get(player.getUniqueId());
        if (session == null) return;
        event.setCancelled(true);
        if (!active(player, session) || session.transitioning || !session.displayed || !owns(event.getView(), session)) return;
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        int slot = event.getRawSlot();
        boolean answer = false;
        for (int valid : ANSWER_SLOTS) if (valid == slot) answer = true;
        if (!answer) return;
        // Ignore additional packets until the next inventory is actually displayed.
        session.displayed = false;
        if (slot == session.expectedSlot) {
            session.step++;
            if (session.step >= session.rounds) { succeed(player, session); return; }
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.65f, 1.35f);
        } else {
            session.mistakes++;
            remember(session, 0);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
            player.sendMessage(color(PREFIX + "&cНе тот предмет. &7Осталось ошибок: &f" + Math.max(0, session.maxMistakes - session.mistakes)));
            if (session.mistakes >= session.maxMistakes) { fail(player, session, "&cСлишком много ошибок."); return; }
        }
        Bukkit.getScheduler().runTask(this, () -> nextRound(player, session));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && pending.containsKey(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Session session = pending.get(player.getUniqueId());
        if (session == null) return;
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof CaptchaHolder) || ((CaptchaHolder) holder).session != session
                || !event.getInventory().equals(session.menu)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Session session = pending.get(player.getUniqueId());
        if (session == null || session.transitioning || session.ending) return;
        if (!(event.getInventory().getHolder() instanceof CaptchaHolder holder) || holder.session != session) return;
        session.displayed = false;
        Bukkit.getScheduler().runTaskLater(this, () -> open(player, session), 3L);
    }

    private boolean waiting(Player player) { return pending.containsKey(player.getUniqueId()); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (!waiting(event.getPlayer())) return;
        String input = event.getMessage().trim().toLowerCase(Locale.ROOT);
        String label = input.split("\\s+", 2)[0];
        if (label.equals("/captcha") || label.equals("/kingcaptcha:captcha")) return;
        if (!event.isCancelled()) event.getPlayer().sendMessage(color(PREFIX + "&eСначала пройдите проверку в открывшемся меню."));
        event.setCancelled(true);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && (args[0].equalsIgnoreCase("reload") || args[0].equalsIgnoreCase("status"))) {
            if (!sender.hasPermission("kingcaptcha.admin")) { sender.sendMessage(color(PREFIX + "&cНедостаточно прав.")); return true; }
            if (args[0].equalsIgnoreCase("reload")) {
                reloadConfig(); readSettings();
                sender.sendMessage(color(PREFIX + "&aНастройки обновлены для следующих проверок."));
            } else sender.sendMessage(color(PREFIX + "&fОжидают: &e" + pending.size() + " &8│ &fПрошли: &a" + verified.size()));
            return true;
        }
        if (!(sender instanceof Player player)) { sender.sendMessage("/captcha status | /captcha reload"); return true; }
        Session session = pending.get(player.getUniqueId());
        if (session == null) player.sendMessage(color(PREFIX + "&aВы уже прошли проверку в этом входе."));
        else {
            if (session.menu == null) nextRound(player, session);
            else open(player, session);
        }
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLegacyChat(AsyncPlayerChatEvent event) {
        if (pending.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncChat(AsyncChatEvent event) {
        if (pending.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onMove(PlayerMoveEvent event) {
        if (!waiting(event.getPlayer()) || event.getTo() == null) return;
        Location from = event.getFrom(), to = event.getTo();
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            Location still = from.clone(); still.setYaw(to.getYaw()); still.setPitch(to.getPitch()); event.setTo(still);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onTeleport(PlayerTeleportEvent event) {
        // KingHub/admin plugins can still place the player at spawn; their
        // network transfer methods are independently gated in KingHub 1.2.4.
        if (waiting(event.getPlayer()) && event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onInteract(PlayerInteractEvent e) { if (waiting(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void onEntity(PlayerInteractEntityEvent e) { if (waiting(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void onEntityAt(PlayerInteractAtEntityEvent e) { if (waiting(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void onArmor(PlayerArmorStandManipulateEvent e) { if (waiting(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void onAttack(EntityDamageByEntityEvent e) { if (e.getDamager() instanceof Player p && waiting(p)) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onBreak(BlockBreakEvent e) { if (waiting(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onPlace(BlockPlaceEvent e) { if (waiting(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onDrop(PlayerDropItemEvent e) { if (waiting(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onSwap(PlayerSwapHandItemsEvent e) { if (waiting(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onFly(PlayerToggleFlightEvent e) { if (waiting(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onPickup(EntityPickupItemEvent e) { if (e.getEntity() instanceof Player p && waiting(p)) e.setCancelled(true); }
}
