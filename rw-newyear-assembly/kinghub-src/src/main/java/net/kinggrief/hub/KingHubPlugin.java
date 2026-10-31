package net.kinggrief.hub;

import net.kinggrief.common.ResourcePackSettings;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.Vector;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KingHubPlugin extends JavaPlugin implements Listener, CommandExecutor, PluginMessageListener {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final String GUI_SELECTOR_TITLE = ChatColor.AQUA + "" + ChatColor.BOLD + "❄ ВЫБОР СЕРВЕРА · KINGGRIEF";
    private static final String GUI_ADMIN_TITLE = ChatColor.DARK_RED + "" + ChatColor.BOLD + "⚙ УПРАВЛЕНИЕ ХАБОМ KINGHUB";
    private static final String BUNGEE_CHANNEL = "BungeeCord";
    private static final String SUBCHANNEL_SSO = "KingConnectSSO";

    // Explicit "kingconnect:sso_token" so KingHub.jar (#1) and KingConnect.jar (#2) share the exact same Cookie key!
    private final NamespacedKey ssoCookieKey = new NamespacedKey("kingconnect", "sso_token");
    private NamespacedKey compassKey;
    private NamespacedKey griefItemKey;

    private NewYearHubWorldBuilder worldBuilder;
    private World hubWorld;

    private File authFile;
    private YamlConfiguration authYml;
    private final Set<UUID> authedPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> transferCooldowns = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, Long> doubleJumpCooldowns = new ConcurrentHashMap<UUID, Long>();
    private final Set<UUID> awaitingGriefIpChat = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<String> pendingSsoJson = new ConcurrentLinkedQueue<String>();
    private final Set<String> globalBannedNicks = Collections.synchronizedSet(new HashSet<String>());

    // Config state
    private String mode = "auto";
    private String secretKey = "KingGrief-Network-Secret-2026";
    private String griefId = "grief1";
    private String griefName = "&#80D8FF&l❄ 1 ГРИФ · НОВОГОДНИЙ";
    private String griefHost = "127.0.0.1";
    private int griefPort = 25566;
    private String griefProxyName = "grief1";
    private boolean griefMaintenance = false;
    private List<String> griefLore = new ArrayList<String>();

    private boolean pushRpOnJoin = true;
    private String rpUrl = ResourcePackSettings.LATEST_URL;
    private String rpSha1 = ResourcePackSettings.LATEST_SHA1;

    private String hubWorldName = "hub_world";
    private boolean autoBuildMap = true;
    private int compassSlot = 4;
    private boolean doubleJumpEnabled = true;
    private boolean authEnabled = true;

    private boolean cloudEnabled = true;
    private String cloudUrl = "https://kinggrief.vercel.app/api/minecraft/network";
    private int cloudIntervalSec = 4;
    private int pingIntervalSec = 4;

    // Live Grief #1 status + Auto-Discovered Cloud Address (eliminates Connection refused: getsockopt!)
    private volatile boolean griefOnline = false;
    private volatile boolean griefTechWorksRemote = false;
    private volatile int griefPlayers = 0;
    private volatile int griefMaxPlayers = 250;
    private volatile int griefPingMs = -1;
    private volatile long griefLastSeenMs = 0L;
    private volatile long griefLastCloudSeenMs = 0L;
    private volatile String cloudDiscoveredGriefHost = "";
    private volatile int cloudDiscoveredGriefPort = 0;
    private volatile String learnedSelfPublicHost = "";
    private final Map<UUID, String> playerHubOriginHostPort = new ConcurrentHashMap<UUID, String>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.compassKey = new NamespacedKey(this, "hub_compass");
        this.griefItemKey = new NamespacedKey(this, "select_grief1");

        ensureServerAcceptsTransfers();
        loadAuthStorage();
        reloadHubConfig();

        this.worldBuilder = new NewYearHubWorldBuilder(this);
        this.hubWorld = worldBuilder.ensureHubWorldAndBuild(hubWorldName, autoBuildMap);

        Bukkit.getMessenger().registerOutgoingPluginChannel(this, BUNGEE_CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(this, BUNGEE_CHANNEL, this);
        Bukkit.getPluginManager().registerEvents(this, this);

        String[] cmds = {"servers", "register", "login", "changepassword", "spawn", "setgrief", "khub"};
        for (String cn : cmds) {
            PluginCommand pc = getCommand(cn);
            if (pc != null) pc.setExecutor(this);
        }

        startHubTasks();
        getLogger().info("KingHub v1.2.3 enabled! New Year Map ready in '" + hubWorld.getName()
                + "', Target Grief1=" + griefHost + ":" + griefPort);
    }

    @Override
    public void onDisable() {
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(this, BUNGEE_CHANNEL);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(this, BUNGEE_CHANNEL, this);
        saveAuthStorage();
    }

    public void reloadHubConfig() {
        reloadConfig();
        this.mode = getConfig().getString("mode", "auto").trim().toLowerCase(Locale.ROOT);
        this.secretKey = getConfig().getString("secret-key", "KingGrief-Network-Secret-2026").trim();

        this.griefId = getConfig().getString("grief1.id", "grief1").trim().toLowerCase(Locale.ROOT);
        this.griefName = getConfig().getString("grief1.name", "&#80D8FF&l❄ 1 ГРИФ · НОВОГОДНИЙ");
        this.griefHost = getConfig().getString("grief1.host", "127.0.0.1").trim();
        this.griefPort = getConfig().getInt("grief1.port", 25566);
        this.griefProxyName = getConfig().getString("grief1.proxy-name", "grief1").trim();
        this.griefMaintenance = getConfig().getBoolean("grief1.maintenance", false);
        this.griefLore = getConfig().getStringList("grief1.lore");

        this.pushRpOnJoin = getConfig().getBoolean("push-resourcepack-on-join", true);
        String configuredUrl = getConfig().getString("resourcepack-url", ResourcePackSettings.LATEST_URL);
        String configuredSha = getConfig().contains("resourcepack-sha1", true)
                ? getConfig().getString("resourcepack-sha1")
                : (ResourcePackSettings.LATEST_URL.equals(configuredUrl) ? ResourcePackSettings.LATEST_SHA1 : null);
        ResourcePackSettings.Pack pack = ResourcePackSettings.resolve(configuredUrl, configuredSha);
        this.rpUrl = pack.url;
        this.rpSha1 = pack.sha1;

        this.hubWorldName = getConfig().getString("hub.world-name", "hub_world").trim();
        this.autoBuildMap = getConfig().getBoolean("hub.auto-build-newyear-map", true);
        this.compassSlot = Math.max(0, Math.min(8, getConfig().getInt("hub.compass-slot", 4)));
        this.doubleJumpEnabled = getConfig().getBoolean("hub.double-jump", true);
        this.authEnabled = getConfig().getBoolean("hub.auth-enabled", true);

        this.cloudEnabled = getConfig().getBoolean("cloud-sync.enabled", true);
        this.cloudUrl = getConfig().getString("cloud-sync.url", "https://kinggrief.vercel.app/api/minecraft/network").trim();
        this.cloudIntervalSec = Math.max(2, getConfig().getInt("cloud-sync.interval-seconds", 4));
        this.pingIntervalSec = Math.max(3, getConfig().getInt("ping-interval-seconds", 4));
    }

    public Location getSpawnLocation() {
        World w = hubWorld != null ? hubWorld : Bukkit.getWorlds().get(0);
        double x = getConfig().getDouble("hub.spawn.x", 0.5);
        double y = getConfig().getDouble("hub.spawn.y", 100.0);
        double z = getConfig().getDouble("hub.spawn.z", 22.5);
        float yaw = (float) getConfig().getDouble("hub.spawn.yaw", 180.0);
        float pitch = (float) getConfig().getDouble("hub.spawn.pitch", 0.0);
        return new Location(w, x, y, z, yaw, pitch);
    }

    public Location getNpcLocation(World w) {
        double x = getConfig().getDouble("hub.npc.x", 0.5);
        double y = getConfig().getDouble("hub.npc.y", 100.0);
        double z = getConfig().getDouble("hub.npc.z", 9.5);
        float yaw = (float) getConfig().getDouble("hub.npc.yaw", 0.0);
        return new Location(w, x, y, z, yaw, 0.0f);
    }

    public String buildNpcHoloText() {
        String st = (griefMaintenance || griefTechWorksRemote)
                ? "&#FF9100⚙ ТЕХ. РАБОТЫ"
                : (griefOnline ? "&#00E676● ОНЛАЙН" : "&#00E5FF● ГОТОВ К ВХОДУ");
        return griefName + "\n"
                + "&fОнлайн: &#FFD740" + griefPlayers + "&7/&f" + griefMaxPlayers + " &8• " + st + "\n"
                + "&#00E676&l▶ Нажми ПКМ — выбрать 1 ГРИФ и зайти!";
    }

    public String getEffectiveGriefHost() {
        if (!HubPingAndCloud.isLoopbackHost(griefHost)) {
            return griefHost;
        }
        if (!HubPingAndCloud.isLoopbackHost(cloudDiscoveredGriefHost)) {
            return cloudDiscoveredGriefHost;
        }
        return griefHost;
    }

    public int getEffectiveGriefPort() {
        if (!HubPingAndCloud.isLoopbackHost(griefHost)) {
            return griefPort;
        }
        if (!HubPingAndCloud.isLoopbackHost(cloudDiscoveredGriefHost) && cloudDiscoveredGriefPort > 0) {
            return cloudDiscoveredGriefPort;
        }
        return griefPort;
    }

    private int detectRealListeningPort() {
        try {
            String envPort = System.getenv("SERVER_PORT");
            if (envPort != null && !envPort.trim().isEmpty()) {
                int p = Integer.parseInt(envPort.trim());
                if (p > 0 && p <= 65535) return p;
            }
        } catch (Throwable ignored) {}
        return Bukkit.getPort();
    }

    private void startHubTasks() {
        // 1) Ping Grief #1 + Proxy PlayerCount (uses auto-discovered cloud host if config is still 127.0.0.1!)
        new BukkitRunnable() {
            @Override
            public void run() {
                requestProxyCount();
                final String host = getEffectiveGriefHost();
                final int port = getEffectiveGriefPort();
                Bukkit.getScheduler().runTaskAsynchronously(KingHubPlugin.this, new Runnable() {
                    @Override
                    public void run() {
                        HubPingAndCloud.PingStatus st = HubPingAndCloud.pingServer(host, port, 1200);
                        if (st.alive) {
                            griefOnline = true;
                            griefPlayers = st.online;
                            griefMaxPlayers = st.max;
                            griefPingMs = st.pingMs;
                            griefTechWorksRemote = st.techWorks;
                            griefLastSeenMs = System.currentTimeMillis();
                        } else if (System.currentTimeMillis() - griefLastSeenMs > 18_000L
                                && System.currentTimeMillis() - griefLastCloudSeenMs > 20_000L) {
                            griefOnline = false;
                            griefPingMs = -1;
                        }
                    }
                });
            }
        }.runTaskTimer(this, 20L, pingIntervalSec * 20L);

        // 2) Cloud Sync with KingConnect on Grief #1
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!cloudEnabled || cloudUrl.isEmpty()) return;
                final String payload = buildHubCloudPayload();
                Bukkit.getScheduler().runTaskAsynchronously(KingHubPlugin.this, new Runnable() {
                    @Override
                    public void run() {
                        final String resp = HubPingAndCloud.postCloudLines(cloudUrl, secretKey, payload, 2600);
                        if (resp != null && resp.startsWith("OK\t")) {
                            Bukkit.getScheduler().runTask(KingHubPlugin.this, new Runnable() {
                                @Override
                                public void run() {
                                    applyCloudLines(resp);
                                }
                            });
                        }
                    }
                });
            }
        }.runTaskTimer(this, 30L, cloudIntervalSec * 20L);

        // 3) Keep NPC & Holograms alive + Update Hub Scoreboard & Open Selector GUIs every 2.5s
        new BukkitRunnable() {
            @Override
            public void run() {
                if (hubWorld != null && worldBuilder != null) {
                    worldBuilder.ensureAllEntitiesAlive(hubWorld);
                }
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateHubScoreboard(p);
                    if (GUI_SELECTOR_TITLE.equals(p.getOpenInventory().getTitle())) {
                        populateSelectorGui(p, p.getOpenInventory().getTopInventory());
                    }
                }
            }
        }.runTaskTimer(this, 40L, 50L);
    }

    // =========================================================================
    // 1. COMPASS & NPC RIGHT-CLICK -> SELECT «1 ГРИФ» -> JOIN SERVER!
    // =========================================================================

    public void openGriefSelectorGui(Player p) {
        if (!isAuthed(p)) {
            p.sendMessage(c("&#FF1744🔐 Сначала войдите в аккаунт: &f/login <пароль> &7или &f/register <пароль> <повтор>"));
            playSoundSafe(p, "ENTITY_VILLAGER_NO", 1.0f, 1.0f);
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 27, GUI_SELECTOR_TITLE);
        populateSelectorGui(p, inv);
        p.openInventory(inv);
        playSoundSafe(p, "BLOCK_ENDER_CHEST_OPEN", 0.85f, 1.25f);
    }

    private void populateSelectorGui(Player p, Inventory inv) {
        ItemStack icePane = makeItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ", null, false);
        ItemStack snowPane = makeItem(Material.WHITE_STAINED_GLASS_PANE, " ", null, false);
        ItemStack darkPane = makeItem(Material.BLACK_STAINED_GLASS_PANE, " ", null, false);

        for (int i = 0; i < 27; i++) {
            if (i < 9 || i >= 18 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, (i % 2 == 0) ? icePane : snowPane);
            } else {
                inv.setItem(i, darkPane);
            }
        }

        int totalOnline = Bukkit.getOnlinePlayers().size() + (griefOnline ? griefPlayers : 0);
        inv.setItem(4, makeItem(Material.SPRUCE_SAPLING, "&#80D8FF&l🎄 НОВОГОДНИЙ ХАБ · KINGGRIEF", Arrays.asList(
                "&7Добро пожаловать на праздничный сервер!",
                "",
                "&fВаш ник: &#FFD740" + p.getName(),
                "&fОбщий онлайн сети: &#00E676" + totalOnline + " игр.",
                "&fВ Лобби сейчас: &#00E5FF" + Bukkit.getOnlinePlayers().size() + " игр.",
                "",
                "&7Нажмите на &#80D8FF&l1 ГРИФ &7ниже для входа!"
        ), true));

        boolean maint = griefMaintenance || griefTechWorksRemote;
        String statusStr = maint ? "&#FF9100⚙ ТЕХ. РАБОТЫ"
                : (griefOnline ? "&#00E676● ОНЛАЙН" : "&#00E5FF● ДОСТУПЕН ДЛЯ ВХОДА");

        List<String> lore = new ArrayList<String>();
        for (String l : griefLore) lore.add(l);
        lore.add("");
        lore.add("&fСтатус сервера: " + statusStr);
        lore.add("&fИгроков онлайн: &#FFD740" + griefPlayers + "&7/&#FFD740" + griefMaxPlayers + " " + progressBar(griefPlayers, griefMaxPlayers));
        if (griefPingMs > 0) {
            lore.add("&fПинг: &#00E5FF" + griefPingMs + " мс");
        }
        lore.add("");
        if (maint && !isAdmin(p)) {
            lore.add("&#FF9100🔒 Сервер временно на тех. работах");
        } else {
            lore.add("&#00E676&l▶ НАЖМИТЕ ЛКМ — ЗАЙТИ НА 1 ГРИФ!");
        }

        ItemStack griefCard = makeItem(Material.DIAMOND_SWORD, griefName, lore, true);
        ItemMeta gm = griefCard.getItemMeta();
        if (gm != null) {
            gm.getPersistentDataContainer().set(griefItemKey, PersistentDataType.STRING, griefId);
            griefCard.setItemMeta(gm);
        }
        inv.setItem(13, griefCard);
    }

    public void connectToGrief1(Player p) {
        if (!isAuthed(p)) {
            p.sendMessage(c("&#FF1744🔐 Сначала войдите: &f/login <пароль> &7или &f/register <пароль> <повтор>"));
            return;
        }
        if ((griefMaintenance || griefTechWorksRemote) && !isAdmin(p)) {
            p.sendMessage(c("&#FF9100⚙ На сервере " + griefName + " &#FF9100сейчас идут технические работы!"));
            playSoundSafe(p, "BLOCK_ANVIL_LAND", 0.7f, 1.2f);
            return;
        }
        long now = System.currentTimeMillis();
        Long prev = transferCooldowns.get(p.getUniqueId());
        if (prev != null && now - prev < 2500L) {
            p.sendMessage(c("&#FFD740⏳ Подождите пару секунд, выполняется подключение..."));
            return;
        }
        transferCooldowns.put(p.getUniqueId(), now);
        p.closeInventory();

        // Build HMAC-SHA256 SSO session token identical to KingConnect.jar (#2) + embed exact Hub return host:port!
        String rank = p.isOp() ? "owner" : "default";
        String ipHash = sha256Hex(getPlayerIp(p) + "|" + secretKey).substring(0, 24);
        String originHp = playerHubOriginHostPort.get(p.getUniqueId());
        if (originHp == null || originHp.isEmpty()) {
            String sh = !HubPingAndCloud.isLoopbackHost(learnedSelfPublicHost) ? learnedSelfPublicHost : "127.0.0.1";
            originHp = sh + ":" + detectRealListeningPort();
        }
        String rawData = p.getName() + "|" + p.getUniqueId().toString() + "|" + ipHash + "|" + rank + "|" + now + "|hub@" + originHp;
        String sig = hmacSha256(rawData, secretKey);
        String fullToken = rawData + "|" + sig;

        pendingSsoJson.add("{\"nick\":\"" + HubPingAndCloud.esc(p.getName())
                + "\",\"ipHash\":\"" + HubPingAndCloud.esc(ipHash)
                + "\",\"rank\":\"" + HubPingAndCloud.esc(rank) + "\",\"ttlMs\":75000}");
        pushCloudImmediately();

        if (shouldUseProxy()) {
            p.sendTitle(c("&#80D8FF&l❄ ВХОД НА 1 ГРИФ..."), c("&fПереносим вас на " + griefName), 5, 45, 10);
            p.sendMessage(c("&#80D8FF❄ &lKingHub &8» &fПодключаем вас к серверу " + griefName + "&f..."));
            playSoundSafe(p, "ENTITY_ENDERMAN_TELEPORT", 0.95f, 1.25f);
            sendProxySsoForward(p, fullToken);
            sendBungeeConnect(p, griefProxyName);
            return;
        }

        final String rawHost = getEffectiveGriefHost();
        final int rawPort = getEffectiveGriefPort();
        final String playerIp = getPlayerIp(p);

        // Block localhost transfer for remote players BEFORE disconnecting client (fixes Connection refused: getsockopt!)
        if (HubPingAndCloud.isLoopbackHost(rawHost) && !HubPingAndCloud.isLoopbackHost(playerIp)) {
            transferCooldowns.remove(p.getUniqueId());
            playSoundSafe(p, "ENTITY_VILLAGER_NO", 1.0f, 0.85f);
            p.sendTitle(c("&#FFD740&l⚠ ПРИВЯЖИТЕ 1 ГРИФ"), c("&fПропишите: &#00E676/setgrief <IP:Порт>"), 5, 65, 10);
            p.sendMessage(c("&#FF5252⚠ &lKingHub &8» &fАдрес &#80D8FF1 ГРИФ &fещё не указан (сейчас стоит &e127.0.0.1&f), поэтому переход остановлен без вылета!"));
            p.sendMessage(c("&#00E676💡 Решение 1: &fПросто запустите сервер &#80D8FF1 Гриф &fс плагином &#FFD740KingConnect.jar &f— он привяжется автоматически за 4 сек!"));
            p.sendMessage(c("&#00E676💡 Решение 2: &fПропишите в чате (с OP) или в консоли Хаба: &#FFD740setgrief <IP_Грифа:Порт>"));
            return;
        }

        p.sendMessage(c("&#80D8FF❄ &lKingHub &8» &7Проверяем доступность " + griefName + "&7..."));
        Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                final HubPingAndCloud.PingStatus check = HubPingAndCloud.pingServer(rawHost, rawPort, 850);
                final boolean cloudAlive = (System.currentTimeMillis() - griefLastCloudSeenMs) < 25_000L
                        && !HubPingAndCloud.isLoopbackHost(check.resolvedHost);

                Bukkit.getScheduler().runTask(KingHubPlugin.this, new Runnable() {
                    @Override
                    public void run() {
                        if (!p.isOnline()) return;
                        if (!check.alive && !cloudAlive) {
                            transferCooldowns.remove(p.getUniqueId());
                            playSoundSafe(p, "ENTITY_VILLAGER_NO", 1.0f, 0.85f);
                            p.sendTitle(c("&#FF5252&l⚠ 1 ГРИФ ВЫКЛЮЧЕН"),
                                    c("&7Сервер " + check.resolvedHost + ":" + check.resolvedPort + " не отвечает!"), 5, 60, 10);
                            p.sendMessage(c("&#FF5252✖ &lKingHub &8» &fСервер " + griefName + " &7(&e"
                                    + check.resolvedHost + ":" + check.resolvedPort
                                    + "&7) &fсейчас выключен или указан неверный порт — вы оставлены в Хабе без вылета (getsockopt)!"));
                            if (isAdmin(p)) {
                                p.sendMessage(c("&#FFD740⚙ Админ-подсказка: &fпроверьте, запущен ли 1 Гриф, или укажите его точный порт: &#00E676/setgrief <IP:Порт>"));
                            }
                            return;
                        }

                        p.sendTitle(c("&#80D8FF&l❄ ВХОД НА 1 ГРИФ..."), c("&fПереносим вас на " + griefName), 5, 45, 10);
                        playSoundSafe(p, "ENTITY_ENDERMAN_TELEPORT", 0.95f, 1.25f);
                        try {
                            p.storeCookie(ssoCookieKey, fullToken.getBytes(StandardCharsets.UTF_8));
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    if (!p.isOnline()) return;
                                    try {
                                        p.transfer(check.resolvedHost, check.resolvedPort);
                                    } catch (Throwable ex) {
                                        p.sendMessage(c("&#FF1744✖ Перенос доступен на клиентах 1.20.5 - 1.21+!"));
                                    }
                                }
                            }.runTaskLater(KingHubPlugin.this, 2L);
                        } catch (Throwable fallback) {
                            sendProxySsoForward(p, fullToken);
                            sendBungeeConnect(p, griefProxyName);
                        }
                    }
                });
            }
        });
    }

    // =========================================================================
    // 2. EVENTS: JOIN, COMPASS CLICK, NPC RIGHT-CLICK, AUTH, HUB PROTECTION
    // =========================================================================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLoginCaptureHost(PlayerLoginEvent e) {
        try {
            String hn = e.getHostname();
            if (hn != null && !hn.isEmpty()) {
                int nul = hn.indexOf('\0');
                if (nul >= 0) hn = hn.substring(0, nul);
                int parsedPort = detectRealListeningPort();
                int col = hn.lastIndexOf(':');
                if (col > 0) {
                    try {
                        int pCand = Integer.parseInt(hn.substring(col + 1).trim());
                        if (pCand > 0 && pCand <= 65535) parsedPort = pCand;
                    } catch (Throwable ignored) {}
                    hn = hn.substring(0, col);
                }
                hn = hn.trim();
                if (!hn.isEmpty()) {
                    playerHubOriginHostPort.put(e.getPlayer().getUniqueId(), hn + ":" + parsedPort);
                }
                if (!HubPingAndCloud.isLoopbackHost(hn)) {
                    learnedSelfPublicHost = hn;
                }
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(final PlayerJoinEvent e) {
        final Player p = e.getPlayer();
        e.setJoinMessage(null);

        if (!p.isOp() && globalBannedNicks.contains(p.getName().toLowerCase(Locale.ROOT))) {
            p.kickPlayer(c("&#FF1744&l🛡 СЕТЬ KINGGRIEF · ВЫ ЗАБАНЕНЫ АНТИЧИТОМ KingАЧ!"));
            return;
        }

        p.teleport(getSpawnLocation());
        p.setGameMode(GameMode.SURVIVAL);
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        giveHubCompass(p);

        if (doubleJumpEnabled) {
            p.setAllowFlight(true);
        }

        // Check if returning from Grief #1 via /hub with signed SSO Cookie!
        boolean transferred = false;
        try {
            transferred = p.isTransferred();
        } catch (Throwable ignored) {}

        if (transferred) {
            try {
                p.retrieveCookie(ssoCookieKey).thenAccept(bytes -> {
                    if (bytes == null || bytes.length == 0) return;
                    final String token = new String(bytes, StandardCharsets.UTF_8);
                    Bukkit.getScheduler().runTask(KingHubPlugin.this, new Runnable() {
                        @Override
                        public void run() {
                            if (!p.isOnline()) return;
                            if (verifyIncomingSsoToken(token, p)) {
                                authedPlayers.add(p.getUniqueId());
                                p.sendTitle(c("&#80D8FF&l❄ НОВОГОДНИЙ ХАБ"), c("&#00E676✔ Вы вернулись в Лобби!"), 5, 35, 10);
                                playSoundSafe(p, "ENTITY_PLAYER_LEVELUP", 0.8f, 1.3f);
                            }
                        }
                    });
                });
            } catch (Throwable ignored) {}
        }

        // Session auto-login or prompt /register | /login
        if (!authEnabled) {
            authedPlayers.add(p.getUniqueId());
        } else {
            String key = "users." + p.getName().toLowerCase(Locale.ROOT);
            String savedIp = authYml.getString(key + ".ip", "");
            long until = authYml.getLong(key + ".until", 0L);
            if (!savedIp.isEmpty() && savedIp.equals(getPlayerIp(p)) && System.currentTimeMillis() < until) {
                authedPlayers.add(p.getUniqueId());
                p.sendTitle(c("&#80D8FF&l❄ KINGGRIEF ХАБ ❄"), c("&fНажми ПКМ Компасом или на НПС &#80D8FF1 ГРИФ&f!"), 10, 60, 15);
                p.sendMessage(c("&#00E676✔ Автоматический вход выполнен! Выберите &#80D8FF&l1 ГРИФ &aКомпасом или через НПС."));
            } else {
                boolean reg = authYml.contains(key + ".pass");
                p.sendTitle(c("&#80D8FF&l❄ KINGGRIEF ЛОББИ ❄"),
                        c(reg ? "&eВведите: &f/login <пароль>" : "&aРегистрация: &f/register <пароль> <повтор>"), 10, 140, 10);
                p.sendMessage(c(reg
                        ? "&#FFD740🔐 Войдите в аккаунт: &f/login <пароль>"
                        : "&#00E676🔐 Зарегистрируйтесь: &f/register <пароль> <повтор>"));
            }
        }

        updateHubScoreboard(p);

        // Push Mega Resourcepack in Hub so textures & 3D items are already loaded before entering Grief #1!
        if (pushRpOnJoin && rpUrl != null && !rpUrl.isEmpty()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!p.isOnline()) return;
                    try {
                        byte[] sha = hexToBytes(rpSha1);
                        if (sha != null) p.setResourcePack(rpUrl, sha);
                        else p.setResourcePack(rpUrl);
                    } catch (Throwable ignored) {}
                }
            }.runTaskLater(this, 18L);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        e.setRespawnLocation(getSpawnLocation());
        final Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            @Override
            public void run() {
                if (p.isOnline()) giveHubCompass(p);
            }
        }, 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        e.setQuitMessage(null);
        UUID u = e.getPlayer().getUniqueId();
        authedPlayers.remove(u);
        awaitingGriefIpChat.remove(u);
    }

    // Compass Click (Right-Click OR Left-Click!) -> Opens «1 ГРИФ» Selector Menu!
    @EventHandler
    public void onCompassUse(PlayerInteractEvent e) {
        if (e.getHand() == EquipmentSlot.OFF_HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK
                && a != Action.LEFT_CLICK_AIR && a != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = e.getItem();
        if (isHubCompass(item)) {
            e.setCancelled(true);
            openGriefSelectorGui(e.getPlayer());
        }
    }

    // NPC Right-Click (Interaction entity) -> Opens «1 ГРИФ» Selector Menu!
    @EventHandler
    public void onNpcRightClick(PlayerInteractEntityEvent e) {
        if (e.getHand() == EquipmentSlot.OFF_HAND) return;
        if (worldBuilder != null && worldBuilder.isGriefNpcEntity(e.getRightClicked())) {
            e.setCancelled(true);
            openGriefSelectorGui(e.getPlayer());
        }
    }

    // NPC Right-Click (ArmorStand body fallback) -> Opens «1 ГРИФ» Selector Menu!
    @EventHandler
    public void onNpcRightClickAt(PlayerInteractAtEntityEvent e) {
        if (e.getHand() == EquipmentSlot.OFF_HAND) return;
        if (worldBuilder != null && worldBuilder.isGriefNpcEntity(e.getRightClicked())) {
            e.setCancelled(true);
            openGriefSelectorGui(e.getPlayer());
        }
    }

    @EventHandler
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent e) {
        e.setCancelled(true);
        if (worldBuilder != null && worldBuilder.isGriefNpcEntity(e.getRightClicked())) {
            openGriefSelectorGui(e.getPlayer());
        }
    }

    // NPC Left-Click & Full Hub Damage / Void Protection
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent e) {
        if (e instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent ed = (EntityDamageByEntityEvent) e;
            if (ed.getDamager() instanceof Player && worldBuilder != null && worldBuilder.isGriefNpcEntity(ed.getEntity())) {
                e.setCancelled(true);
                openGriefSelectorGui((Player) ed.getDamager());
                return;
            }
        }
        e.setCancelled(true);
        if (e.getEntity() instanceof Player && e.getCause() == EntityDamageEvent.DamageCause.VOID) {
            e.getEntity().teleport(getSpawnLocation());
        }
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        String title = e.getView().getTitle();

        if (GUI_SELECTOR_TITLE.equals(title)) {
            e.setCancelled(true);
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;
            if (clicked.getItemMeta().getPersistentDataContainer().has(griefItemKey, PersistentDataType.STRING)) {
                connectToGrief1(p);
            }
            return;
        }

        if (GUI_ADMIN_TITLE.equals(title)) {
            e.setCancelled(true);
            if (!isAdmin(p)) return;
            int slot = e.getRawSlot();
            if (slot == 11) {
                p.closeInventory();
                awaitingGriefIpChat.add(p.getUniqueId());
                p.sendMessage(c("&#80D8FF❄ &lKingHub &8» &fВведите в чат &#FFD740IP:Порт &fсервера &#80D8FF1 ГРИФ &7(например &f45.142.122.10:25566 &7или &cотмена&7):"));
            } else if (slot == 13) {
                String next = "auto".equals(mode) ? "transfer" : ("transfer".equals(mode) ? "proxy" : "auto");
                getConfig().set("mode", next);
                saveConfig();
                reloadHubConfig();
                openAdminHubGui(p);
            } else if (slot == 15) {
                boolean next = !griefMaintenance;
                getConfig().set("grief1.maintenance", next);
                saveConfig();
                reloadHubConfig();
                openAdminHubGui(p);
            } else if (slot == 22 && hubWorld != null) {
                p.closeInventory();
                worldBuilder.rebuildMapForce(hubWorld);
                p.teleport(getSpawnLocation());
                p.sendMessage(c("&#00E676✔ Новогодняя Карта Хаба и НПС «1 ГРИФ» успешно перестроены!"));
            }
            return;
        }

        if (!isAdmin(p) && p.getGameMode() != GameMode.CREATIVE) {
            e.setCancelled(true);
            if (isHubCompass(e.getCurrentItem())) {
                openGriefSelectorGui(p);
            }
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!isAuthed(p)) {
            if (e.getTo() != null && (e.getFrom().getX() != e.getTo().getX() || e.getFrom().getZ() != e.getTo().getZ())) {
                Location back = e.getFrom();
                back.setYaw(e.getTo().getYaw());
                back.setPitch(e.getTo().getPitch());
                e.setTo(back);
            }
            return;
        }
        if (e.getTo() == null) return;
        if (e.getTo().getY() < 65.0) {
            p.teleport(getSpawnLocation());
            playSoundSafe(p, "ENTITY_ENDERMAN_TELEPORT", 0.7f, 1.2f);
            return;
        }
        Material below = e.getTo().clone().subtract(0, 0.25, 0).getBlock().getType();
        if (below == Material.SLIME_BLOCK) {
            p.setVelocity(p.getLocation().getDirection().multiply(1.15).setY(0.95));
            playSoundSafe(p, "ENTITY_FIREWORK_ROCKET_LAUNCH", 0.9f, 1.25f);
        }
        if (doubleJumpEnabled && p.isOnGround() && p.getGameMode() == GameMode.SURVIVAL && !p.getAllowFlight()) {
            p.setAllowFlight(true);
        }
    }

    @EventHandler
    public void onDoubleJump(PlayerToggleFlightEvent e) {
        if (!doubleJumpEnabled) return;
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
        e.setCancelled(true);
        p.setAllowFlight(false);
        p.setFlying(false);
        if (!isAuthed(p)) return;
        long now = System.currentTimeMillis();
        Long last = doubleJumpCooldowns.get(p.getUniqueId());
        if (last != null && now - last < 1200L) return;
        doubleJumpCooldowns.put(p.getUniqueId(), now);
        p.setVelocity(p.getLocation().getDirection().multiply(1.1).setY(0.82));
        playSoundSafe(p, "ENTITY_BAT_TAKEOFF", 0.8f, 1.35f);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (!isAdmin(e.getPlayer()) || e.getPlayer().getGameMode() != GameMode.CREATIVE) e.setCancelled(true);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (!isAdmin(e.getPlayer()) || e.getPlayer().getGameMode() != GameMode.CREATIVE) e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (!isAdmin(e.getPlayer()) || isHubCompass(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent e) {
        if (isHubCompass(e.getMainHandItem()) || isHubCompass(e.getOffHandItem())) e.setCancelled(true);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        e.setCancelled(true);
        if (e.getEntity() instanceof Player) ((Player) e.getEntity()).setFoodLevel(20);
    }

    @EventHandler
    public void onWeather(WeatherChangeEvent e) {
        if (e.toWeatherState()) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        final Player p = e.getPlayer();
        if (!isAuthed(p)) {
            e.setCancelled(true);
            p.sendMessage(c("&#FF1744🔐 Сначала войдите: &f/login <пароль> &7или &f/register <пароль> <повтор>"));
            return;
        }
        if (awaitingGriefIpChat.remove(p.getUniqueId())) {
            e.setCancelled(true);
            final String input = e.getMessage().trim();
            if ("отмена".equalsIgnoreCase(input) || "cancel".equalsIgnoreCase(input)) {
                p.sendMessage(c("&#FFD740Ввод отменён."));
                return;
            }
            Bukkit.getScheduler().runTask(this, new Runnable() {
                @Override
                public void run() {
                    if (saveGriefHostPort(input)) {
                        p.sendMessage(c("&#00E676✔ Адрес сервера &#80D8FF1 ГРИФ &#00E676сохранён: &f" + griefHost + ":" + griefPort + "&a!"));
                        openAdminHubGui(p);
                    } else {
                        p.sendMessage(c("&#FF1744✖ Укажите адрес в формате &fIP:ПОРТ &7(например &f185.10.20.30:25566&7)."));
                    }
                }
            });
            return;
        }
        e.setFormat(c("&#80D8FF[ЛОББИ] &f") + "%1$s" + c(" &8» &7") + "%2$s");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreprocess(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        String lower = e.getMessage().trim().toLowerCase(Locale.ROOT);
        if (!isAuthed(p)) {
            if (!lower.startsWith("/login") && !lower.startsWith("/l ") && !lower.equals("/l")
                    && !lower.startsWith("/register") && !lower.startsWith("/reg")) {
                e.setCancelled(true);
                p.sendMessage(c("&#FF1744🔐 Сначала авторизуйтесь: &f/login <пароль> &7или &f/register <пароль> <повтор>"));
            }
        }
    }

    // =========================================================================
    // 3. COMMANDS (/register, /login, /servers, /spawn, /setgrief, /khub)
    // =========================================================================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        String alias = label.toLowerCase(Locale.ROOT);

        if ("setgrief".equals(cmd)) {
            if (sender instanceof Player && !isAdmin((Player) sender)) {
                sender.sendMessage(c("&#FF1744✖ У вас нет прав!"));
                return true;
            }
            if (args.length < 1 || !saveGriefHostPort(args[0])) {
                sender.sendMessage(c("&#FFD740Использование: &f/setgrief <IP:ПОРТ> &7(например: &f/setgrief 185.10.20.30:25566&7)"));
                return true;
            }
            sender.sendMessage(c("&#00E676✔ Сервер &#80D8FF1 ГРИФ &#00E676привязан к адресу: &f" + griefHost + ":" + griefPort + "&a!"));
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Команда для игроков.");
            return true;
        }
        Player p = (Player) sender;

        if ("register".equals(cmd)) {
            if (isAuthed(p)) {
                p.sendMessage(c("&aВы уже авторизованы!"));
                return true;
            }
            String k = "users." + p.getName().toLowerCase(Locale.ROOT);
            if (authYml.contains(k + ".pass")) {
                p.sendMessage(c("&#FF1744Этот ник уже зарегистрирован! Введите: &f/login <пароль>"));
                return true;
            }
            if (args.length < 2 || !args[0].equals(args[1]) || args[0].length() < 4) {
                p.sendMessage(c("&#FFD740Использование: &f/register <пароль> <повтор> &7(мин. 4 символа)"));
                return true;
            }
            String salt = UUID.randomUUID().toString().substring(0, 8);
            authYml.set(k + ".pass", salt + "$" + sha256Hex(salt + ":" + args[0]));
            authYml.set(k + ".ip", getPlayerIp(p));
            authYml.set(k + ".until", System.currentTimeMillis() + 21_600_000L);
            saveAuthStorage();
            authedPlayers.add(p.getUniqueId());
            p.sendTitle(c("&#00E676&l✔ РЕГИСТРАЦИЯ УСПЕШНА!"), c("&fВыбирай &#80D8FF1 ГРИФ &fи заходи играть!"), 5, 45, 10);
            playSoundSafe(p, "ENTITY_PLAYER_LEVELUP", 1.0f, 1.2f);
            openGriefSelectorGui(p);
            return true;
        }

        if ("login".equals(cmd)) {
            if (isAuthed(p)) {
                p.sendMessage(c("&aВы уже вошли в аккаунт!"));
                return true;
            }
            if (args.length < 1) {
                p.sendMessage(c("&#FFD740Использование: &f/login <пароль>"));
                return true;
            }
            String k = "users." + p.getName().toLowerCase(Locale.ROOT);
            String stored = authYml.getString(k + ".pass", "");
            if (stored == null || !stored.contains("$")) {
                p.sendMessage(c("&#FFD740Сначала зарегистрируйтесь: &f/register <пароль> <повтор>"));
                return true;
            }
            String[] sp = stored.split("\\$", 2);
            if (!sp[1].equals(sha256Hex(sp[0] + ":" + args[0]))) {
                p.sendMessage(c("&#FF1744✖ Неверный пароль!"));
                playSoundSafe(p, "ENTITY_VILLAGER_NO", 1.0f, 1.0f);
                return true;
            }
            authYml.set(k + ".ip", getPlayerIp(p));
            authYml.set(k + ".until", System.currentTimeMillis() + 21_600_000L);
            saveAuthStorage();
            authedPlayers.add(p.getUniqueId());
            p.sendTitle(c("&#00E676&l✔ ВХОД ВЫПОЛНЕН!"), c("&fВыбери &#80D8FF1 ГРИФ &fв меню!"), 5, 40, 10);
            playSoundSafe(p, "ENTITY_PLAYER_LEVELUP", 1.0f, 1.25f);
            openGriefSelectorGui(p);
            return true;
        }

        if ("changepassword".equals(cmd)) {
            if (!isAuthed(p)) return true;
            String k = "users." + p.getName().toLowerCase(Locale.ROOT);
            String stored = authYml.getString(k + ".pass", "");
            if (args.length < 2 || stored == null || !stored.contains("$")) {
                p.sendMessage(c("&#FFD740Использование: &f/changepassword <старый> <новый>"));
                return true;
            }
            String[] sp = stored.split("\\$", 2);
            if (!sp[1].equals(sha256Hex(sp[0] + ":" + args[0])) || args[1].length() < 4) {
                p.sendMessage(c("&#FF1744✖ Старый пароль неверен или новый короче 4 символов!"));
                return true;
            }
            String salt = UUID.randomUUID().toString().substring(0, 8);
            authYml.set(k + ".pass", salt + "$" + sha256Hex(salt + ":" + args[1]));
            saveAuthStorage();
            p.sendMessage(c("&#00E676✔ Пароль успешно изменён!"));
            return true;
        }

        if ("spawn".equals(cmd)) {
            p.teleport(getSpawnLocation());
            p.sendMessage(c("&#80D8FF❄ Вы на главной площади Новогоднего Хаба!"));
            return true;
        }

        if ("servers".equals(cmd)) {
            if ("grief".equals(alias) || "grief1".equals(alias)) {
                connectToGrief1(p);
                return true;
            }
            openGriefSelectorGui(p);
            return true;
        }

        if ("khub".equals(cmd)) {
            if (!isAdmin(p)) {
                p.sendMessage(c("&#FF1744✖ У вас нет прав!"));
                return true;
            }
            if ("sethub".equals(alias) || (args.length >= 1 && "sethub".equalsIgnoreCase(args[0]))) {
                Location l = p.getLocation();
                getConfig().set("hub.spawn.x", l.getX());
                getConfig().set("hub.spawn.y", l.getY());
                getConfig().set("hub.spawn.z", l.getZ());
                getConfig().set("hub.spawn.yaw", (double) l.getYaw());
                getConfig().set("hub.spawn.pitch", (double) l.getPitch());
                saveConfig();
                p.sendMessage(c("&#00E676✔ Точка спавна Новогоднего Хаба обновлена!"));
                return true;
            }
            if ("setnpc".equals(alias) || (args.length >= 1 && "setnpc".equalsIgnoreCase(args[0]))) {
                Location l = p.getLocation();
                getConfig().set("hub.npc.x", l.getX());
                getConfig().set("hub.npc.y", l.getY());
                getConfig().set("hub.npc.z", l.getZ());
                getConfig().set("hub.npc.yaw", (double) l.getYaw());
                saveConfig();
                if (hubWorld != null) worldBuilder.respawnNpcAtNewSpot(hubWorld);
                p.sendMessage(c("&#00E676✔ НПС «❄ 1 ГРИФ» переставлен на вашу позицию!"));
                return true;
            }
            openAdminHubGui(p);
            return true;
        }

        return false;
    }

    private void openAdminHubGui(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI_ADMIN_TITLE);
        ItemStack pane = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", null, false);
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);

        inv.setItem(11, makeItem(Material.DIAMOND_SWORD, "&#00E5FF&l🌐 АДРЕС «1 ГРИФ»: &f" + griefHost + ":" + griefPort, Arrays.asList(
                "&7Текущий статус: " + (griefOnline ? "&#00E676ОНЛАЙН (" + griefPlayers + "/" + griefMaxPlayers + ")" : "&#FFD740ОЖИДАНИЕ"),
                "",
                "&#00E676▶ Нажмите ЛКМ, чтобы ввести новый IP:Порт в чате!"
        ), true));

        inv.setItem(13, makeItem(Material.COMPARATOR, "&#FFD740&l🔄 РЕЖИМ СВЯЗКИ: &f" + mode.toUpperCase(Locale.ROOT), Arrays.asList(
                "&7AUTO — 2-в-1 (Прямой 1.21+ или Velocity)",
                "&7TRANSFER — Прямой перенос без прокси",
                "&7PROXY — Через прокси Velocity / Bungee",
                "",
                "&#00E676▶ ЛКМ — переключить"
        ), false));

        inv.setItem(15, makeItem(griefMaintenance ? Material.REDSTONE_TORCH : Material.LIME_DYE,
                "&#FF9100&l⚙ ТЕХ. РАБОТЫ НА 1 ГРИФЕ: " + (griefMaintenance ? "&#FF1744ВКЛ" : "&#00E676ВЫКЛ"), Arrays.asList(
                        "&7Временно закрыть вход обычным игрокам",
                        "",
                        "&#00E676▶ ЛКМ — переключить"
                ), false));

        inv.setItem(22, makeItem(Material.SPRUCE_SAPLING, "&#80D8FF&l🎄 ПЕРЕСТРОИТЬ НОВОГОДНЮЮ КАРТУ И НПС", Arrays.asList(
                "&7Мгновенно обновить новогодний остров,",
                "&7живую ёлку, голограммы и НПС «1 ГРИФ»!",
                "",
                "&#00E676▶ ЛКМ — перестроить"
        ), true));

        p.openInventory(inv);
    }

    // =========================================================================
    // 4. SCOREBOARD, COMPASS, PROXY / CLOUD / CRYPTO HELPERS
    // =========================================================================

    private void giveHubCompass(Player p) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta im = compass.getItemMeta();
        if (im != null) {
            im.setDisplayName(c("&#80D8FF&l❄ ВЫБОР СЕРВЕРА · 1 ГРИФ &7[ПКМ]"));
            im.setLore(Arrays.asList(
                    c("&7Нажмите ПКМ, чтобы выбрать"),
                    c("&#80D8FF&l1 ГРИФ &7и зайти на сервер!"),
                    "",
                    c("&#00E676▶ Нажмите ПКМ в руке")
            ));
            im.addEnchant(Enchantment.UNBREAKING, 1, true);
            im.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            im.getPersistentDataContainer().set(compassKey, PersistentDataType.BYTE, (byte) 1);
            compass.setItemMeta(im);
        }
        p.getInventory().setItem(compassSlot, compass);
    }

    private boolean isHubCompass(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(compassKey, PersistentDataType.BYTE);
    }

    private void updateHubScoreboard(Player p) {
        try {
            if (Bukkit.getScoreboardManager() == null) return;
            Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective obj = sb.registerNewObjective("khub", Criteria.DUMMY, c("&#80D8FF&l❄ KINGGRIEF ❄"));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);

            int total = Bukkit.getOnlinePlayers().size() + (griefOnline ? griefPlayers : 0);
            String[] lines = new String[]{
                    c("&8━━━━━━━━━━━━━━━"),
                    c(" &fНик: &#FFD740" + p.getName()),
                    c(" &fСервер: &#80D8FFНовогодний Хаб"),
                    c("&1 "),
                    c(" &#80D8FF&l❄ 1 ГРИФ:"),
                    c(" &fОнлайн: &#00E676" + griefPlayers + "&7/&f" + griefMaxPlayers),
                    c(" &fОбщий онлайн: &#00E5FF" + total),
                    c("&2 "),
                    c(" &eПКМ Компасом &7или &eНПС!"),
                    c(" &#FFD740kinggrief.vercel.app")
            };
            int score = lines.length;
            for (String ln : lines) {
                obj.getScore(ln).setScore(score--);
            }
            p.setScoreboard(sb);
        } catch (Throwable ignored) {}
    }

    private boolean saveGriefHostPort(String raw) {
        if (raw == null) return false;
        String hp = raw.trim()
                .replace("http://", "")
                .replace("https://", "")
                .replace("/", "")
                .replace(" ", "");
        String h = hp;
        int pt = 25565;
        int colon = hp.lastIndexOf(':');
        if (colon > 0) {
            h = hp.substring(0, colon).trim();
            try {
                pt = Integer.parseInt(hp.substring(colon + 1).trim());
            } catch (NumberFormatException ex) {
                return false;
            }
        }
        if (h.isEmpty() || pt <= 0 || pt > 65535) return false;
        getConfig().set("grief1.host", h);
        getConfig().set("grief1.port", pt);
        saveConfig();
        reloadHubConfig();
        return true;
    }

    private boolean shouldUseProxy() {
        if ("proxy".equalsIgnoreCase(mode)) return true;
        if ("transfer".equalsIgnoreCase(mode)) return false;
        try {
            File sp = new File("spigot.yml");
            if (sp.exists() && YamlConfiguration.loadConfiguration(sp).getBoolean("settings.bungeecord", false)) {
                return true;
            }
            File pg = new File("config/paper-global.yml");
            if (pg.exists() && YamlConfiguration.loadConfiguration(pg).getBoolean("proxies.velocity.enabled", false)) {
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private void sendBungeeConnect(Player p, String proxySrv) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("Connect");
            out.writeUTF(proxySrv);
            p.sendPluginMessage(this, BUNGEE_CHANNEL, b.toByteArray());
        } catch (Throwable ignored) {}
    }

    private void sendProxySsoForward(Player p, String signedToken) {
        try {
            ByteArrayOutputStream mb = new ByteArrayOutputStream();
            new DataOutputStream(mb).writeUTF(signedToken);
            byte[] raw = mb.toByteArray();

            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("Forward");
            out.writeUTF("ALL");
            out.writeUTF(SUBCHANNEL_SSO);
            out.writeShort(raw.length);
            out.write(raw);
            p.sendPluginMessage(this, BUNGEE_CHANNEL, b.toByteArray());
        } catch (Throwable ignored) {}
    }

    private void requestProxyCount() {
        if (!shouldUseProxy() || Bukkit.getOnlinePlayers().isEmpty()) return;
        Player carrier = Bukkit.getOnlinePlayers().iterator().next();
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("PlayerCount");
            out.writeUTF(griefProxyName);
            carrier.sendPluginMessage(this, BUNGEE_CHANNEL, b.toByteArray());
        } catch (Throwable ignored) {}
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!BUNGEE_CHANNEL.equals(channel)) return;
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            String sub = in.readUTF();
            if ("PlayerCount".equals(sub)) {
                String srv = in.readUTF();
                int cnt = in.readInt();
                if (srv.equalsIgnoreCase(griefProxyName) || srv.equalsIgnoreCase(griefId)) {
                    griefOnline = true;
                    griefPlayers = Math.max(0, cnt);
                    griefLastSeenMs = System.currentTimeMillis();
                }
            }
        } catch (Throwable ignored) {}
    }

    private String buildHubCloudPayload() {
        List<String> names = new ArrayList<String>();
        for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
        List<String> ssoBatch = new ArrayList<String>();
        String it;
        while ((it = pendingSsoJson.poll()) != null) ssoBatch.add(it);
        StringBuilder ssoJoined = new StringBuilder();
        for (int i = 0; i < ssoBatch.size(); i++) {
            if (i > 0) ssoJoined.append(',');
            ssoJoined.append(ssoBatch.get(i));
        }

        return "{\"serverId\":\"hub\",\"serverName\":\"&#FFD740&l🏰 НОВОГОДНИЙ ХАБ\",\"host\":\""
                + HubPingAndCloud.esc(learnedSelfPublicHost) + "\","
                + "\"port\":" + detectRealListeningPort() + ",\"proxyName\":\"hub\",\"online\":" + names.size()
                + ",\"max\":" + Bukkit.getMaxPlayers() + ",\"tps\":20.0,\"maintenance\":false,"
                + "\"players\":" + HubPingAndCloud.buildStrArray(names)
                + ",\"pushSso\":[" + ssoJoined + "]}";
    }

    private void pushCloudImmediately() {
        if (!cloudEnabled || cloudUrl.isEmpty()) return;
        final String payload = buildHubCloudPayload();
        Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                final String resp = HubPingAndCloud.postCloudLines(cloudUrl, secretKey, payload, 2500);
                if (resp != null && resp.startsWith("OK\t")) {
                    Bukkit.getScheduler().runTask(KingHubPlugin.this, new Runnable() {
                        @Override
                        public void run() {
                            applyCloudLines(resp);
                        }
                    });
                }
            }
        });
    }

    private void applyCloudLines(String resp) {
        String[] lines = resp.split("\n");
        Set<String> banned = new HashSet<String>();
        for (int i = 1; i < lines.length; i++) {
            String[] f = lines[i].split("\t", -1);
            if ("NODE".equals(f[0]) && f.length >= 12 && f[1].equalsIgnoreCase(griefId)) {
                long age = parseLongSafe(f[10], 99_999L);
                String nodeHost = f[3] == null ? "" : f[3].trim();
                int nodePort = (int) parseLongSafe(f[4], 25565L);
                if (!HubPingAndCloud.isLoopbackHost(nodeHost) && nodePort > 0) {
                    cloudDiscoveredGriefHost = nodeHost;
                    cloudDiscoveredGriefPort = nodePort;
                }
                if (age < 20_000L) {
                    griefOnline = true;
                    griefPlayers = (int) parseLongSafe(f[6], 0L);
                    griefMaxPlayers = Math.max(1, (int) parseLongSafe(f[7], 250L));
                    griefTechWorksRemote = "1".equals(f[9]);
                    griefLastSeenMs = System.currentTimeMillis();
                    griefLastCloudSeenMs = System.currentTimeMillis();
                }
            } else if ("BAN".equals(f[0]) && f.length >= 5) {
                banned.add(f[1].toLowerCase(Locale.ROOT));
            } else if ("EVENT".equals(f[0]) && f.length >= 8 && "global_alert".equals(f[2])) {
                // Global announcements also show in Hub!
            }
        }
        globalBannedNicks.clear();
        globalBannedNicks.addAll(banned);
    }

    private boolean verifyIncomingSsoToken(String fullToken, Player p) {
        int last = fullToken.lastIndexOf('|');
        if (last <= 0) return false;
        String raw = fullToken.substring(0, last);
        String sig = fullToken.substring(last + 1);
        if (!hmacSha256(raw, secretKey).equals(sig)) return false;
        String[] parts = raw.split("\\|");
        if (parts.length < 6) return false;
        if (!parts[0].equalsIgnoreCase(p.getName())) return false;
        long ts = parseLongSafe(parts[4], 0L);
        return Math.abs(System.currentTimeMillis() - ts) < 90_000L;
    }

    private void ensureServerAcceptsTransfers() {
        try {
            File pf = new File("server.properties");
            if (pf.exists()) {
                Properties pr = new Properties();
                FileInputStream fis = new FileInputStream(pf);
                pr.load(fis);
                fis.close();
                if (!"true".equalsIgnoreCase(pr.getProperty("accepts-transfers", "false"))) {
                    List<String> lines = java.nio.file.Files.readAllLines(pf.toPath(), StandardCharsets.UTF_8);
                    boolean set = false;
                    for (int i = 0; i < lines.size(); i++) {
                        if (lines.get(i).trim().startsWith("accepts-transfers=")) {
                            lines.set(i, "accepts-transfers=true");
                            set = true;
                            break;
                        }
                    }
                    if (!set) lines.add("accepts-transfers=true");
                    java.nio.file.Files.write(pf.toPath(), lines, StandardCharsets.UTF_8);
                }
            }
        } catch (Throwable ignored) {}

        try {
            if (!Bukkit.getServer().isAcceptingTransfers()) {
                Object cs = Bukkit.getServer();
                Method gm = cs.getClass().getMethod("getServer");
                Object ds = gm.invoke(cs);
                if (ds != null) {
                    patchTransfersField(ds);
                    for (Field f : ds.getClass().getDeclaredFields()) {
                        f.setAccessible(true);
                        Object v = f.get(ds);
                        if (v != null && v.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("properties")) {
                            patchTransfersField(v);
                            for (Field sf : v.getClass().getDeclaredFields()) {
                                sf.setAccessible(true);
                                Object sv = sf.get(v);
                                if (sv != null && sv.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("properties")) {
                                    patchTransfersField(sv);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private void patchTransfersField(Object obj) {
        Class<?> c = obj.getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType() == boolean.class && f.getName().toLowerCase(Locale.ROOT).contains("acceptstransfers")) {
                    try {
                        f.setAccessible(true);
                        f.setBoolean(obj, true);
                    } catch (Throwable ignored) {}
                }
            }
            c = c.getSuperclass();
        }
    }

    private boolean isAuthed(Player p) {
        return !authEnabled || authedPlayers.contains(p.getUniqueId());
    }

    private boolean isAdmin(Player p) {
        return p.isOp() || p.hasPermission("kinghub.admin");
    }

    private void loadAuthStorage() {
        authFile = new File(getDataFolder(), "auth.yml");
        if (!authFile.exists()) {
            try {
                getDataFolder().mkdirs();
                authFile.createNewFile();
            } catch (Throwable ignored) {}
        }
        authYml = YamlConfiguration.loadConfiguration(authFile);
    }

    private void saveAuthStorage() {
        if (authYml != null && authFile != null) {
            try { authYml.save(authFile); } catch (Throwable ignored) {}
        }
    }

    private static String getPlayerIp(Player p) {
        try {
            if (p != null && p.getAddress() != null && p.getAddress().getAddress() != null) {
                return p.getAddress().getAddress().getHostAddress();
            }
        } catch (Throwable ignored) {}
        return "";
    }

    private static String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return bytesToHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Throwable t) {
            return sha256Hex(data + ":" + secret);
        }
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return bytesToHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Throwable t) {
            return Integer.toHexString(input.hashCode());
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() != 40) return null;
        byte[] out = new byte[20];
        try {
            for (int i = 0; i < 20; i++) {
                out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            }
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    private static ItemStack makeItem(Material mat, String name, List<String> lore, boolean glint) {
        ItemStack it = new ItemStack(mat == null ? Material.STONE : mat);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.setDisplayName(c(name));
            if (lore != null) {
                List<String> col = new ArrayList<String>(lore.size());
                for (String s : lore) col.add(c(s));
                im.setLore(col);
            }
            if (glint) im.addEnchant(Enchantment.UNBREAKING, 1, true);
            im.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            it.setItemMeta(im);
        }
        return it;
    }

    private static String progressBar(int cur, int max) {
        int filled = max <= 0 ? 0 : Math.min(10, Math.max(0, (int) Math.round((cur * 10.0) / max)));
        StringBuilder sb = new StringBuilder("&8[");
        for (int i = 0; i < 10; i++) sb.append(i < filled ? "&#00E676█" : "&7▒");
        return sb.append("&8]").toString();
    }

    private static long parseLongSafe(String s, long def) {
        try { return Long.parseLong(s.trim()); } catch (Throwable t) { return def; }
    }

    private static void playSoundSafe(Player p, String name, float vol, float pitch) {
        try { p.playSound(p.getLocation(), Sound.valueOf(name), vol, pitch); } catch (Throwable ignored) {}
    }

    public static String c(String msg) {
        if (msg == null) return "";
        Matcher matcher = HEX_PATTERN.matcher(msg);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder rep = new StringBuilder("\u00A7x");
            for (int i = 0; i < 6; i++) rep.append('\u00A7').append(hex.charAt(i));
            matcher.appendReplacement(buffer, rep.toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}
