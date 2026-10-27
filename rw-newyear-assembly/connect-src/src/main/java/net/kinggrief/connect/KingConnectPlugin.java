package net.kinggrief.connect;

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
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

public final class KingConnectPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter, PluginMessageListener {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final String GUI_SERVERS_TITLE = ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "🌐 ВЫБОР СЕРВЕРА · СВЯЗКА";
    private static final String GUI_ADMIN_TITLE = ChatColor.DARK_RED + "" + ChatColor.BOLD + "⚙ ПАНЕЛЬ СВЯЗКИ KINGCONNECT";
    private static final String BUNGEE_CHANNEL = "BungeeCord";
    private static final String SUBCHANNEL_SSO = "KingConnectSSO";
    private static final String SUBCHANNEL_BUS = "KingConnectBus";

    private NamespacedKey ssoCookieKey;
    private NamespacedKey itemServerKey;
    private NamespacedKey itemCompassKey;

    private final Map<String, ServerNode> servers = new LinkedHashMap<String, ServerNode>();
    private final Map<UUID, Long> transferCooldowns = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, Long> combatUntilMs = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, Long> doubleJumpCooldown = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, String> adminEditingServer = new ConcurrentHashMap<UUID, String>();
    private final Set<UUID> verifiedSsoPlayers = ConcurrentHashMap.newKeySet();

    // Standalone Hub Auth (active when hub-mode.enabled && hub-mode.standalone-auth && KingGriefCore is absent)
    private File hubAuthFile;
    private YamlConfiguration hubAuthYml;
    private final Set<UUID> hubAuthed = ConcurrentHashMap.newKeySet();

    // Cloud Sync queues & cached state
    private final ConcurrentLinkedQueue<String> pendingSsoJson = new ConcurrentLinkedQueue<String>();
    private final ConcurrentLinkedQueue<String> pendingEventsJson = new ConcurrentLinkedQueue<String>();
    private final ConcurrentLinkedQueue<String> pendingBansJson = new ConcurrentLinkedQueue<String>();
    private final ConcurrentLinkedQueue<String> pendingUnbans = new ConcurrentLinkedQueue<String>();
    private final Map<String, String> pendingRanksPush = new ConcurrentHashMap<String, String>();

    private final Map<String, CloudSsoEntry> cloudSsoCache = new ConcurrentHashMap<String, CloudSsoEntry>();
    private final Map<String, String> cloudRankCache = new ConcurrentHashMap<String, String>();
    private final Map<String, CloudBanEntry> cloudBanCache = new ConcurrentHashMap<String, CloudBanEntry>();
    private final Set<String> processedEventIds = Collections.synchronizedSet(new HashSet<String>());
    private final Set<String> pushedLocalBanKeys = Collections.synchronizedSet(new HashSet<String>());
    private volatile boolean lastCloudOk = false;

    // Config fields
    private String thisServerId = "grief1";
    private String thisServerName = "&#80D8FF&l❄ ГРИФ #1";
    private String mode = "auto";
    private String secretKey = "KingGrief-Network-Secret-2026";
    private String hubServerId = "hub";
    private boolean requireSsoFromHub = false;
    private int combatBlockSeconds = 30;

    private boolean hubModeEnabled = false;
    private boolean hubGiveCompass = true;
    private int hubCompassSlot = 4;
    private boolean hubDoubleJump = true;
    private boolean hubProtectWorld = true;
    private boolean hubStandaloneAuth = true;

    private boolean cloudSyncEnabled = true;
    private String cloudSyncUrl = "https://kinggrief.vercel.app/api/minecraft/network";
    private int cloudSyncIntervalSec = 4;
    private boolean cloudSyncRanks = true;
    private boolean cloudSyncBans = true;
    private int pingIntervalSec = 5;

    private static final class CloudSsoEntry {
        final String nick;
        final String ipHash;
        final String rank;
        final String fromServer;

        CloudSsoEntry(String nick, String ipHash, String rank, String fromServer) {
            this.nick = nick;
            this.ipHash = ipHash;
            this.rank = rank;
            this.fromServer = fromServer;
        }
    }

    private static final class CloudBanEntry {
        final String nick;
        final String reason;
        final String staff;
        final long untilMs;

        CloudBanEntry(String nick, String reason, String staff, long untilMs) {
            this.nick = nick;
            this.reason = reason;
            this.staff = staff;
            this.untilMs = untilMs;
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.ssoCookieKey = new NamespacedKey(this, "sso_token");
        this.itemServerKey = new NamespacedKey(this, "server_id");
        this.itemCompassKey = new NamespacedKey(this, "selector_compass");

        ensureServerAcceptsTransfers();
        loadHubAuthFile();
        reloadNetworkConfig();

        Bukkit.getMessenger().registerOutgoingPluginChannel(this, BUNGEE_CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(this, BUNGEE_CHANNEL, this);
        Bukkit.getPluginManager().registerEvents(this, this);

        String[] cmds = {"servers", "hub", "grief", "glist", "find", "send", "gsc", "galert", "sethub", "kingconnect"};
        for (String cn : cmds) {
            PluginCommand pc = getCommand(cn);
            if (pc != null) {
                pc.setExecutor(this);
                pc.setTabCompleter(this);
            }
        }

        startBackgroundTasks();
        getLogger().info("KingConnect v1.0.0 enabled! Server='" + thisServerId + "', Mode='" + mode
                + "', HubMode=" + hubModeEnabled + ", Servers=" + servers.size()
                + ", AcceptsTransfers=" + safeIsAcceptingTransfers());
    }

    @Override
    public void onDisable() {
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(this, BUNGEE_CHANNEL);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(this, BUNGEE_CHANNEL, this);
        saveHubAuthFile();
    }

    // =========================================================================
    // 1. AUTO-ENABLE accepts-transfers=true (1.20.5 - 1.21.11)
    // =========================================================================

    private void ensureServerAcceptsTransfers() {
        try {
            File propFile = new File("server.properties");
            if (propFile.exists()) {
                Properties props = new Properties();
                FileInputStream fis = new FileInputStream(propFile);
                props.load(fis);
                fis.close();
                if (!"true".equalsIgnoreCase(props.getProperty("accepts-transfers", "false"))) {
                    List<String> lines = java.nio.file.Files.readAllLines(propFile.toPath(), StandardCharsets.UTF_8);
                    boolean replaced = false;
                    for (int i = 0; i < lines.size(); i++) {
                        if (lines.get(i).trim().startsWith("accepts-transfers=")) {
                            lines.set(i, "accepts-transfers=true");
                            replaced = true;
                            break;
                        }
                    }
                    if (!replaced) {
                        lines.add("accepts-transfers=true");
                    }
                    java.nio.file.Files.write(propFile.toPath(), lines, StandardCharsets.UTF_8);
                }
            }
        } catch (Throwable ignored) {}

        // Also enable acceptsTransfers in live DedicatedServer properties via reflection for immediate first-boot work!
        if (!safeIsAcceptingTransfers()) {
            try {
                Object craftServer = Bukkit.getServer();
                Method getServerMethod = craftServer.getClass().getMethod("getServer");
                Object dedicatedServer = getServerMethod.invoke(craftServer);
                if (dedicatedServer != null) {
                    patchBooleanFieldContaining(dedicatedServer, "acceptsTransfers", true);
                    for (Field f : dedicatedServer.getClass().getDeclaredFields()) {
                        f.setAccessible(true);
                        Object val = f.get(dedicatedServer);
                        if (val != null && val.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("properties")) {
                            patchBooleanFieldContaining(val, "acceptsTransfers", true);
                            for (Field sf : val.getClass().getDeclaredFields()) {
                                sf.setAccessible(true);
                                Object sub = sf.get(val);
                                if (sub != null && sub.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("properties")) {
                                    patchBooleanFieldContaining(sub, "acceptsTransfers", true);
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    private void patchBooleanFieldContaining(Object target, String nameHint, boolean newVal) {
        Class<?> cls = target.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.getType() == boolean.class && f.getName().toLowerCase(Locale.ROOT).contains(nameHint.toLowerCase(Locale.ROOT))) {
                    try {
                        f.setAccessible(true);
                        f.setBoolean(target, newVal);
                    } catch (Throwable ignored) {}
                }
            }
            cls = cls.getSuperclass();
        }
    }

    private boolean safeIsAcceptingTransfers() {
        try {
            return Bukkit.getServer().isAcceptingTransfers();
        } catch (Throwable t) {
            return false;
        }
    }

    // =========================================================================
    // 2. CONFIGURATION & NETWORK TOPOLOGY
    // =========================================================================

    public void reloadNetworkConfig() {
        reloadConfig();
        this.thisServerId = getConfig().getString("this-server-id", "grief1").trim().toLowerCase(Locale.ROOT);
        this.thisServerName = getConfig().getString("this-server-name", "&#80D8FF&l❄ ГРИФ #1");
        this.mode = getConfig().getString("mode", "auto").trim().toLowerCase(Locale.ROOT);
        this.secretKey = getConfig().getString("secret-key", "KingGrief-Network-Secret-2026").trim();
        this.hubServerId = getConfig().getString("hub-server", "hub").trim().toLowerCase(Locale.ROOT);
        this.requireSsoFromHub = getConfig().getBoolean("require-sso-from-hub", false);
        this.combatBlockSeconds = Math.max(0, getConfig().getInt("combat-block-seconds", 30));

        this.hubModeEnabled = getConfig().getBoolean("hub-mode.enabled", false);
        this.hubGiveCompass = getConfig().getBoolean("hub-mode.give-selector-compass", true);
        this.hubCompassSlot = Math.max(0, Math.min(8, getConfig().getInt("hub-mode.compass-slot", 4)));
        this.hubDoubleJump = getConfig().getBoolean("hub-mode.double-jump", true);
        this.hubProtectWorld = getConfig().getBoolean("hub-mode.protect-world", true);
        this.hubStandaloneAuth = getConfig().getBoolean("hub-mode.standalone-auth", true);

        this.cloudSyncEnabled = getConfig().getBoolean("cloud-sync.enabled", true);
        this.cloudSyncUrl = getConfig().getString("cloud-sync.url", "https://kinggrief.vercel.app/api/minecraft/network").trim();
        this.cloudSyncIntervalSec = Math.max(2, getConfig().getInt("cloud-sync.interval-seconds", 4));
        this.cloudSyncRanks = getConfig().getBoolean("cloud-sync.sync-ranks", true);
        this.cloudSyncBans = getConfig().getBoolean("cloud-sync.sync-bans", true);
        this.pingIntervalSec = Math.max(3, getConfig().getInt("ping-interval-seconds", 5));

        servers.clear();
        ConfigurationSection sec = getConfig().getConfigurationSection("servers");
        if (sec != null) {
            int autoSlot = 19;
            for (String key : sec.getKeys(false)) {
                ConfigurationSection s = sec.getConfigurationSection(key);
                if (s == null) continue;
                String id = key.trim().toLowerCase(Locale.ROOT);
                String name = s.getString("name", "&b&l" + id.toUpperCase(Locale.ROOT));
                String host = s.getString("host", "127.0.0.1");
                int port = s.getInt("port", 25565);
                String proxyName = s.getString("proxy-name", id);
                Material icon = Material.matchMaterial(s.getString("icon", "GRASS_BLOCK"));
                if (icon == null) icon = Material.ENDER_PEARL;
                int slot = s.getInt("slot", autoSlot++);
                boolean maint = s.getBoolean("maintenance", false);
                List<String> lore = s.getStringList("lore");
                ServerNode node = new ServerNode(id, name, host, port, proxyName, icon, slot, maint, lore);
                if (id.equals(thisServerId)) {
                    node.onlineState = true;
                    node.onlinePlayers = Bukkit.getOnlinePlayers().size();
                    node.maxPlayers = Bukkit.getMaxPlayers();
                    node.pingMs = 1;
                }
                servers.put(id, node);
            }
        }
    }

    private void startBackgroundTasks() {
        // 1) Direct Minecraft Server List Ping (every pingIntervalSec seconds) + Proxy PlayerCount query
        new BukkitRunnable() {
            @Override
            public void run() {
                refreshLocalServerNode();
                requestProxyPlayerCounts();
                final List<ServerNode> snapshot = new ArrayList<ServerNode>(servers.values());
                Bukkit.getScheduler().runTaskAsynchronously(KingConnectPlugin.this, new Runnable() {
                    @Override
                    public void run() {
                        for (ServerNode node : snapshot) {
                            if (node.id.equalsIgnoreCase(thisServerId)) continue;
                            if (!node.hasDirectAddress()) continue;
                            ServerPinger.PingResult res = ServerPinger.ping(node.host, node.port, 1200);
                            if (res.alive) {
                                node.onlineState = true;
                                node.onlinePlayers = res.online;
                                node.maxPlayers = res.max;
                                node.pingMs = res.pingMs;
                                node.techWorksRemote = res.techWorks;
                                node.lastUpdatedMs = System.currentTimeMillis();
                            } else if (System.currentTimeMillis() - node.lastUpdatedMs > 18_000L) {
                                node.onlineState = false;
                                node.pingMs = -1;
                            }
                        }
                    }
                });
            }
        }.runTaskTimer(this, 20L, pingIntervalSec * 20L);

        // 2) Cloud Network Bus Sync (every cloudSyncIntervalSec seconds)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!cloudSyncEnabled || cloudSyncUrl.isEmpty()) return;
                scanLocalBansAndRanksForSync();
                final String payload = buildCloudSyncPayload();
                Bukkit.getScheduler().runTaskAsynchronously(KingConnectPlugin.this, new Runnable() {
                    @Override
                    public void run() {
                        String resp = CloudSyncClient.postSync(cloudSyncUrl, secretKey, payload, 2800);
                        if (resp == null || !resp.startsWith("OK\t")) {
                            lastCloudOk = false;
                            return;
                        }
                        lastCloudOk = true;
                        Bukkit.getScheduler().runTask(KingConnectPlugin.this, new Runnable() {
                            @Override
                            public void run() {
                                applyCloudSyncResponse(resp);
                            }
                        });
                    }
                });
            }
        }.runTaskTimer(this, 30L, cloudSyncIntervalSec * 20L);

        // 3) Live GUI Refresh for open /servers menus (every 3 seconds)
        new BukkitRunnable() {
            @Override
            public void run() {
                refreshLocalServerNode();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    String title = p.getOpenInventory().getTitle();
                    if (GUI_SERVERS_TITLE.equals(title)) {
                        populateServersInventory(p, p.getOpenInventory().getTopInventory());
                    } else if (GUI_ADMIN_TITLE.equals(title)) {
                        populateAdminInventory(p, p.getOpenInventory().getTopInventory());
                    }
                }
            }
        }.runTaskTimer(this, 60L, 60L);
    }

    private void refreshLocalServerNode() {
        ServerNode self = servers.get(thisServerId);
        List<String> names = new ArrayList<String>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
        }
        if (self != null) {
            self.onlineState = true;
            self.onlinePlayers = names.size();
            self.maxPlayers = Bukkit.getMaxPlayers();
            self.pingMs = 1;
            self.playerNames = names;
            self.lastUpdatedMs = System.currentTimeMillis();
        }
    }

    // =========================================================================
    // 3. 2-IN-1 TRANSFER ENGINE (DIRECT 1.21 TRANSFER+COOKIE & PROXY BUNGEE/VELOCITY)
    // =========================================================================

    private boolean isProxyBackendDetected() {
        try {
            File spigotYml = new File("spigot.yml");
            if (spigotYml.exists()) {
                YamlConfiguration y = YamlConfiguration.loadConfiguration(spigotYml);
                if (y.getBoolean("settings.bungeecord", false)) return true;
            }
            File paperGlobal = new File("config/paper-global.yml");
            if (paperGlobal.exists()) {
                YamlConfiguration y = YamlConfiguration.loadConfiguration(paperGlobal);
                if (y.getBoolean("proxies.velocity.enabled", false) || y.getBoolean("proxies.bungee-cord.online-mode", false)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private boolean shouldUseProxyFor(ServerNode target) {
        if ("proxy".equalsIgnoreCase(mode)) return true;
        if ("transfer".equalsIgnoreCase(mode)) return false;
        // mode == "auto":
        if (isProxyBackendDetected()) return true;
        return !target.hasDirectAddress();
    }

    public void connectPlayerToServer(Player p, ServerNode target, boolean forceBypass) {
        if (p == null || target == null) return;
        if (target.id.equalsIgnoreCase(thisServerId)) {
            p.sendMessage(c("&#FFD740⭐ Вы уже находитесь на сервере " + target.displayName + "&#FFD740!"));
            playSoundSafe(p, "ENTITY_VILLAGER_NO", 0.8f, 1.1f);
            return;
        }
        if (!forceBypass) {
            if (!isPlayerAuthenticatedEverywhere(p)) {
                p.sendMessage(c("&#FF1744🔐 Сначала войдите в аккаунт (/login или /register), чтобы переходить между серверами!"));
                return;
            }
            int combatSec = getCombatSecondsLeft(p);
            if (combatSec > 0 && !isAdmin(p)) {
                p.sendMessage(c("&#FF1744⚔ Вы в режиме ПвП (осталось &#FFD740" + combatSec + " сек.&#FF1744)! Сбежать на другой сервер нельзя!"));
                playSoundSafe(p, "ENTITY_VILLAGER_NO", 1.0f, 0.9f);
                return;
            }
            if (target.isEffectivelyMaintenance() && !isStaff(p)) {
                p.sendMessage(c("&#FF9100⚙ На сервере " + target.displayName + " &#FF9100сейчас ведутся технические работы!"));
                playSoundSafe(p, "BLOCK_ANVIL_LAND", 0.7f, 1.2f);
                return;
            }
            long now = System.currentTimeMillis();
            Long last = transferCooldowns.get(p.getUniqueId());
            if (last != null && now - last < 2500L) {
                p.sendMessage(c("&#FFD740⏳ Подождите пару секунд перед следующим переходом..."));
                return;
            }
            transferCooldowns.put(p.getUniqueId(), now);
        }

        p.closeInventory();
        String rankId = detectPlayerRank(p);
        String ipHash = sha256Hex(getPlayerIp(p) + "|" + secretKey).substring(0, 24);
        long now = System.currentTimeMillis();
        String rawData = p.getName() + "|" + p.getUniqueId().toString() + "|" + ipHash + "|" + rankId + "|" + now + "|" + thisServerId;
        String sig = hmacSha256(rawData, secretKey);
        String fullToken = rawData + "|" + sig;

        // Push Cloud SSO ticket + Rank immediately so destination server recognizes player even across Proxy/NAT
        pendingSsoJson.add("{\"nick\":\"" + CloudSyncClient.esc(p.getName())
                + "\",\"ipHash\":\"" + CloudSyncClient.esc(ipHash)
                + "\",\"rank\":\"" + CloudSyncClient.esc(rankId) + "\",\"ttlMs\":75000}");
        if (cloudSyncRanks && !"default".equalsIgnoreCase(rankId)) {
            pendingRanksPush.put(p.getName().toLowerCase(Locale.ROOT), rankId);
        }
        triggerImmediateAsyncCloudPush();

        p.sendTitle(c("&#00E5FF&l🌐 ПЕРЕХОД..."), c("&7Подключаемся к " + target.displayName), 5, 40, 10);
        p.sendMessage(c("&#00E5FF🌐 &lKingConnect &8» &fПереносим вас на сервер " + target.displayName + "&f..."));
        playSoundSafe(p, "ENTITY_ENDERMAN_TELEPORT", 0.9f, 1.25f);

        if (shouldUseProxyFor(target)) {
            sendProxySsoForward(p, fullToken);
            sendBungeeConnect(p, target.proxyName);
            return;
        }

        // Direct 1.20.5 - 1.21.11 Transfer + Signed Client Cookie!
        try {
            p.storeCookie(ssoCookieKey, fullToken.getBytes(StandardCharsets.UTF_8));
            // Give 2 ticks so client processes StoreCookie packet right before Transfer packet!
            final String host = target.host;
            final int port = target.port;
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!p.isOnline()) return;
                    try {
                        p.transfer(host, port);
                    } catch (Throwable ex) {
                        p.sendMessage(c("&#FF1744✖ Не удалось выполнить прямой перенос (проверьте версию 1.20.5+ и адрес " + host + ":" + port + ")!"));
                    }
                }
            }.runTaskLater(this, 2L);
        } catch (Throwable ex) {
            // Fallback to Proxy channel if running on older client/server
            sendProxySsoForward(p, fullToken);
            sendBungeeConnect(p, target.proxyName);
        }
    }

    private void sendBungeeConnect(Player p, String proxyServerName) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("Connect");
            out.writeUTF(proxyServerName);
            p.sendPluginMessage(this, BUNGEE_CHANNEL, b.toByteArray());
        } catch (Throwable ignored) {}
    }

    private void sendProxySsoForward(Player p, String signedToken) {
        try {
            ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
            DataOutputStream msgOut = new DataOutputStream(msgBytes);
            msgOut.writeUTF(signedToken);

            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("Forward");
            out.writeUTF("ALL");
            out.writeUTF(SUBCHANNEL_SSO);
            byte[] raw = msgBytes.toByteArray();
            out.writeShort(raw.length);
            out.write(raw);
            p.sendPluginMessage(this, BUNGEE_CHANNEL, b.toByteArray());
        } catch (Throwable ignored) {}
    }

    private void sendProxyBusPacket(String type, String sender, String target, String dest, String message) {
        Player carrier = Bukkit.getOnlinePlayers().isEmpty() ? null : Bukkit.getOnlinePlayers().iterator().next();
        if (carrier == null) return;
        try {
            String rawPayload = type + "\t" + thisServerId + "\t" + sender + "\t" + target + "\t" + dest + "\t" + message + "\t" + System.currentTimeMillis();
            String sig = hmacSha256(rawPayload, secretKey);

            ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
            DataOutputStream msgOut = new DataOutputStream(msgBytes);
            msgOut.writeUTF(rawPayload);
            msgOut.writeUTF(sig);

            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("Forward");
            out.writeUTF("ALL");
            out.writeUTF(SUBCHANNEL_BUS);
            byte[] raw = msgBytes.toByteArray();
            out.writeShort(raw.length);
            out.write(raw);
            carrier.sendPluginMessage(this, BUNGEE_CHANNEL, b.toByteArray());
        } catch (Throwable ignored) {}
    }

    private void requestProxyPlayerCounts() {
        if (!"proxy".equalsIgnoreCase(mode) && !isProxyBackendDetected()) return;
        Player carrier = Bukkit.getOnlinePlayers().isEmpty() ? null : Bukkit.getOnlinePlayers().iterator().next();
        if (carrier == null) return;
        for (ServerNode node : servers.values()) {
            try {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(b);
                out.writeUTF("PlayerCount");
                out.writeUTF(node.proxyName);
                carrier.sendPluginMessage(this, BUNGEE_CHANNEL, b.toByteArray());
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!BUNGEE_CHANNEL.equals(channel)) return;
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            String sub = in.readUTF();
            if ("PlayerCount".equals(sub)) {
                String srv = in.readUTF();
                int count = in.readInt();
                for (ServerNode n : servers.values()) {
                    if (n.proxyName.equalsIgnoreCase(srv) || n.id.equalsIgnoreCase(srv)) {
                        n.onlinePlayers = Math.max(0, count);
                        n.onlineState = true;
                        n.lastUpdatedMs = System.currentTimeMillis();
                    }
                }
            } else if (SUBCHANNEL_SSO.equals(sub)) {
                short len = in.readShort();
                byte[] msgBytes = new byte[len];
                in.readFully(msgBytes);
                DataInputStream msgin = new DataInputStream(new ByteArrayInputStream(msgBytes));
                String signedToken = msgin.readUTF();
                handleVerifiedTokenString(signedToken, null);
            } else if (SUBCHANNEL_BUS.equals(sub)) {
                short len = in.readShort();
                byte[] msgBytes = new byte[len];
                in.readFully(msgBytes);
                DataInputStream msgin = new DataInputStream(new ByteArrayInputStream(msgBytes));
                String rawPayload = msgin.readUTF();
                String sig = msgin.readUTF();
                if (!hmacSha256(rawPayload, secretKey).equals(sig)) return;
                String[] p = rawPayload.split("\t", -1);
                if (p.length >= 6 && !p[1].equalsIgnoreCase(thisServerId)) {
                    dispatchIncomingNetworkEvent("proxy-" + p[6] + "-" + p[1], p[0], p[1], p[2], p[3], p[4], p[5]);
                }
            }
        } catch (Throwable ignored) {}
    }

    // =========================================================================
    // 4. JOIN / SSO VERIFICATION / GLOBAL BAN / STANDALONE HUB LOGIC
    // =========================================================================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoinLowest(final PlayerJoinEvent e) {
        final Player p = e.getPlayer();
        final String lowerNick = p.getName().toLowerCase(Locale.ROOT);

        // 1) Enforce synced network-wide Ban (from KingАЧ / Moderation across all servers)
        if (cloudSyncBans && !isOwner(p)) {
            CloudBanEntry ban = cloudBanCache.get(lowerNick);
            if (ban != null && (ban.untilMs < 0 || System.currentTimeMillis() < ban.untilMs)) {
                String rem = ban.untilMs < 0 ? "Навсегда" : fmtDuration((ban.untilMs - System.currentTimeMillis()) / 1000L);
                p.kickPlayer(c("&#FF1744&l🛡 СЕТЬ KINGGRIEF · ДОСТУП ЗАБЛОКИРОВАН\n\n"
                        + "&7Вы забанены на всей связке серверов!\n"
                        + "&fАдминистратор / Античит: &#FFD740" + ban.staff + "\n"
                        + "&fПричина: &#FF5252" + ban.reason + "\n"
                        + "&fОсталось: &#00E5FF" + rem));
                return;
            }
        }

        // 2) Apply synced rank if available
        if (cloudSyncRanks) {
            String syncedRank = cloudRankCache.get(lowerNick);
            if (syncedRank != null && !syncedRank.isEmpty()) {
                applyRankToPlayer(p, syncedRank);
            }
        }

        // 3) Check Cloud SSO cache immediately (works before KingGriefCore's NORMAL onJoin!)
        String expectedIpHash = sha256Hex(getPlayerIp(p) + "|" + secretKey).substring(0, 24);
        CloudSsoEntry sso = cloudSsoCache.remove(lowerNick);
        if (sso != null && (sso.ipHash.isEmpty() || sso.ipHash.equals(expectedIpHash))) {
            verifiedSsoPlayers.add(p.getUniqueId());
            markAuthenticatedEverywhere(p, sso.rank, sso.fromServer, false);
        }

        // 4) Check 1.20.5 - 1.21+ Client Cookie if player arrived via Player#transfer!
        boolean transferred = false;
        try {
            transferred = p.isTransferred();
        } catch (Throwable ignored) {}

        if (transferred) {
            try {
                p.retrieveCookie(ssoCookieKey).thenAccept(bytes -> {
                    if (bytes == null || bytes.length == 0) return;
                    final String token = new String(bytes, StandardCharsets.UTF_8);
                    Bukkit.getScheduler().runTask(KingConnectPlugin.this, new Runnable() {
                        @Override
                        public void run() {
                            if (!p.isOnline()) return;
                            handleVerifiedTokenString(token, p);
                        }
                    });
                });
            } catch (Throwable ignored) {}
        }

        // 5) Hub-Mode arrival setup (Compass, Hub Spawn, Standalone Hub Auth)
        if (hubModeEnabled) {
            Bukkit.getScheduler().runTaskLater(this, new Runnable() {
                @Override
                public void run() {
                    if (!p.isOnline()) return;
                    setupHubArrival(p);
                }
            }, 3L);
        } else if (requireSsoFromHub && !isOwner(p)) {
            // Anti-Bypass Shield: ensure player entered from Hub with valid SSO within 40 ticks
            Bukkit.getScheduler().runTaskLater(this, new Runnable() {
                @Override
                public void run() {
                    if (!p.isOnline()) return;
                    if (!verifiedSsoPlayers.contains(p.getUniqueId())) {
                        ServerNode hub = servers.get(hubServerId);
                        String hubAddr = (hub != null && hub.hasDirectAddress()) ? (hub.host + ":" + hub.port) : "Главное Лобби";
                        p.kickPlayer(c("&#FF1744&l🛡 ЗАЩИТА СВЯЗКИ KINGCONNECT\n\n"
                                + "&fПрямой вход в обход Хаба запрещён!\n"
                                + "&7Пожалуйста, заходите через главное Лобби:\n"
                                + "&#00E5FF&l" + hubAddr));
                    }
                }
            }, 40L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        verifiedSsoPlayers.remove(u);
        combatUntilMs.remove(u);
        hubAuthed.remove(u);
        adminEditingServer.remove(u);
    }

    private void handleVerifiedTokenString(String fullToken, Player targetPlayerOrNull) {
        if (fullToken == null) return;
        int lastPipe = fullToken.lastIndexOf('|');
        if (lastPipe <= 0) return;
        String rawData = fullToken.substring(0, lastPipe);
        String sig = fullToken.substring(lastPipe + 1);
        if (!hmacSha256(rawData, secretKey).equals(sig)) return;

        String[] parts = rawData.split("\\|");
        if (parts.length < 6) return;
        String nick = parts[0];
        String ipHash = parts[2];
        String rankId = parts[3];
        long ts;
        try {
            ts = Long.parseLong(parts[4]);
        } catch (NumberFormatException ex) {
            return;
        }
        String fromSrv = parts[5];
        if (Math.abs(System.currentTimeMillis() - ts) > 90_000L) return;

        if (targetPlayerOrNull == null) {
            // Store briefly in local cache in case Forward arrives 1 tick before/after join
            cloudSsoCache.put(nick.toLowerCase(Locale.ROOT), new CloudSsoEntry(nick, ipHash, rankId, fromSrv));
            targetPlayerOrNull = Bukkit.getPlayerExact(nick);
        }
        if (targetPlayerOrNull == null || !targetPlayerOrNull.isOnline()) return;
        if (!targetPlayerOrNull.getName().equalsIgnoreCase(nick)) return;

        String actualIpHash = sha256Hex(getPlayerIp(targetPlayerOrNull) + "|" + secretKey).substring(0, 24);
        if (!ipHash.equals(actualIpHash) && !isProxyBackendDetected()) {
            return;
        }

        verifiedSsoPlayers.add(targetPlayerOrNull.getUniqueId());
        markAuthenticatedEverywhere(targetPlayerOrNull, rankId, fromSrv, true);
    }

    private void markAuthenticatedEverywhere(Player p, String rankId, String fromServer, boolean announce) {
        hubAuthed.add(p.getUniqueId());
        if (cloudSyncRanks && rankId != null && !rankId.isEmpty() && !"default".equalsIgnoreCase(rankId)) {
            applyRankToPlayer(p, rankId);
        }

        // Authenticate inside KingGriefCore DataStore via reflection if KingGriefCore is installed!
        try {
            Plugin kg = Bukkit.getPluginManager().getPlugin("KingGriefCore");
            if (kg != null && kg.isEnabled()) {
                Method getStore = kg.getClass().getMethod("getStore");
                Object store = getStore.invoke(kg);
                if (store != null) {
                    Method setAuth = store.getClass().getMethod("setAuthenticated", Player.class, boolean.class);
                    setAuth.invoke(store, p, true);
                }
            }
        } catch (Throwable ignored) {}

        if (announce) {
            ServerNode fromNode = servers.get(fromServer == null ? "" : fromServer.toLowerCase(Locale.ROOT));
            String fromLabel = fromNode != null ? fromNode.displayName : ("&b" + fromServer);
            p.sendTitle(c(thisServerName), c("&#00E676✔ Переход с " + fromLabel + " &#00E676выполнен!"), 5, 35, 10);
            p.sendMessage(c("&#00E5FF🌐 &lKingConnect &8» &aСессия подтверждена автоматически! Вы перешли на " + thisServerName + "&a."));
            playSoundSafe(p, "ENTITY_PLAYER_LEVELUP", 0.8f, 1.3f);
        }
    }

    // =========================================================================
    // 5. STANDALONE HUB / LOBBY MODE (COMPASS, /REGISTER, /LOGIN, DOUBLE JUMP, PROTECTION)
    // =========================================================================

    private void setupHubArrival(Player p) {
        Location hubLoc = getHubSpawnLocation();
        if (hubLoc != null) {
            p.teleport(hubLoc);
        }
        p.setHealth(20.0);
        p.setFoodLevel(20);
        if (hubGiveCompass) {
            giveSelectorCompass(p);
        }
        if (hubDoubleJump && p.getGameMode() == GameMode.SURVIVAL) {
            p.setAllowFlight(true);
        }
        if (isStandaloneHubAuthActive() && !verifiedSsoPlayers.contains(p.getUniqueId())) {
            String key = p.getName().toLowerCase(Locale.ROOT);
            String savedIp = hubAuthYml.getString("users." + key + ".ip", "");
            long until = hubAuthYml.getLong("users." + key + ".until", 0L);
            if (!savedIp.isEmpty() && savedIp.equals(getPlayerIp(p)) && System.currentTimeMillis() < until) {
                hubAuthed.add(p.getUniqueId());
                p.sendMessage(c("&#00E676✔ Автоматический вход по сессии выполнен! Выберите режим компасом или /servers."));
            } else {
                boolean reg = hubAuthYml.contains("users." + key + ".pass");
                p.sendTitle(c("&#FFD740&l🏰 ЛОББИ KINGGRIEF"),
                        c(reg ? "&eВойдите: &f/login <пароль>" : "&aРегистрация: &f/register <пароль> <повтор>"), 10, 120, 10);
                p.sendMessage(c(reg
                        ? "&#FFD740🔐 Введите в чат: &f/login <пароль>"
                        : "&#00E676🔐 Зарегистрируйтесь: &f/register <пароль> <повтор>"));
            }
        }
    }

    private boolean isStandaloneHubAuthActive() {
        return hubModeEnabled && hubStandaloneAuth && Bukkit.getPluginManager().getPlugin("KingGriefCore") == null;
    }

    private boolean isPlayerAuthenticatedEverywhere(Player p) {
        try {
            Plugin kg = Bukkit.getPluginManager().getPlugin("KingGriefCore");
            if (kg != null && kg.isEnabled()) {
                Method getStore = kg.getClass().getMethod("getStore");
                Object store = getStore.invoke(kg);
                if (store != null) {
                    Method isAuth = store.getClass().getMethod("isAuthenticated", Player.class);
                    Object res = isAuth.invoke(store, p);
                    if (res instanceof Boolean) return (Boolean) res;
                }
            }
        } catch (Throwable ignored) {}
        if (isStandaloneHubAuthActive()) {
            return hubAuthed.contains(p.getUniqueId());
        }
        return true;
    }

    private void giveSelectorCompass(Player p) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta im = compass.getItemMeta();
        if (im != null) {
            im.setDisplayName(c("&#00E5FF&l🧭 ВЫБОР СЕРВЕРА &7[ПКМ]"));
            im.setLore(Arrays.asList(
                    c("&7Нажмите ПКМ, чтобы открыть меню"),
                    c("&7всех игровых режимов связки!"),
                    "",
                    c("&#00E676▶ Команда: &f/servers")
            ));
            im.addEnchant(Enchantment.UNBREAKING, 1, true);
            im.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            im.getPersistentDataContainer().set(itemCompassKey, PersistentDataType.BYTE, (byte) 1);
            compass.setItemMeta(im);
        }
        p.getInventory().setItem(hubCompassSlot, compass);
    }

    private Location getHubSpawnLocation() {
        String wName = getConfig().getString("hub-mode.spawn.world", "world");
        World w = Bukkit.getWorld(wName);
        if (w == null && !Bukkit.getWorlds().isEmpty()) w = Bukkit.getWorlds().get(0);
        if (w == null) return null;
        double x = getConfig().getDouble("hub-mode.spawn.x", 0.5);
        double y = getConfig().getDouble("hub-mode.spawn.y", 100.0);
        double z = getConfig().getDouble("hub-mode.spawn.z", 0.5);
        float yaw = (float) getConfig().getDouble("hub-mode.spawn.yaw", 180.0);
        float pitch = (float) getConfig().getDouble("hub-mode.spawn.pitch", 0.0);
        return new Location(w, x, y, z, yaw, pitch);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onStandaloneHubPreprocess(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        String msg = e.getMessage().trim();
        String lower = msg.toLowerCase(Locale.ROOT);

        // Russian quick aliases everywhere (/хаб, /лобби, /режимы, /сервера)
        if (lower.equals("/хаб") || lower.equals("/лобби")) {
            e.setCancelled(true);
            p.performCommand("hub");
            return;
        }
        if (lower.equals("/режимы") || lower.equals("/сервера") || lower.equals("/серверы")) {
            e.setCancelled(true);
            p.performCommand("servers");
            return;
        }

        if (!isStandaloneHubAuthActive()) return;

        String[] parts = msg.split("\\s+");
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        if ("/register".equals(cmd) || "/reg".equals(cmd)) {
            e.setCancelled(true);
            if (hubAuthed.contains(p.getUniqueId())) {
                p.sendMessage(c("&aВы уже авторизованы!"));
                return;
            }
            if (parts.length < 3 || !parts[1].equals(parts[2]) || parts[1].length() < 4) {
                p.sendMessage(c("&#FFD740Использование: &f/register <пароль> <повтор> &7(мин. 4 символа)"));
                return;
            }
            String k = "users." + p.getName().toLowerCase(Locale.ROOT);
            if (hubAuthYml.contains(k + ".pass")) {
                p.sendMessage(c("&#FF1744Этот ник уже зарегистрирован! Введите: &f/login <пароль>"));
                return;
            }
            String salt = UUID.randomUUID().toString().substring(0, 8);
            hubAuthYml.set(k + ".pass", salt + "$" + sha256Hex(salt + ":" + parts[1]));
            hubAuthYml.set(k + ".ip", getPlayerIp(p));
            hubAuthYml.set(k + ".until", System.currentTimeMillis() + 21_600_000L);
            saveHubAuthFile();
            hubAuthed.add(p.getUniqueId());
            p.sendTitle(c("&#00E676&l✔ ДОБРО ПОЖАЛОВАТЬ!"), c("&fВыберите сервер компасом или /servers"), 5, 45, 10);
            playSoundSafe(p, "ENTITY_PLAYER_LEVELUP", 1.0f, 1.2f);
            openServersGui(p);
            return;
        }

        if ("/login".equals(cmd) || "/l".equals(cmd)) {
            e.setCancelled(true);
            if (hubAuthed.contains(p.getUniqueId())) {
                p.sendMessage(c("&aВы уже вошли в аккаунт!"));
                return;
            }
            if (parts.length < 2) {
                p.sendMessage(c("&#FFD740Использование: &f/login <пароль>"));
                return;
            }
            String k = "users." + p.getName().toLowerCase(Locale.ROOT);
            String stored = hubAuthYml.getString(k + ".pass", "");
            if (stored == null || !stored.contains("$")) {
                p.sendMessage(c("&#FFD740Сначала зарегистрируйтесь: &f/register <пароль> <повтор>"));
                return;
            }
            String[] sp = stored.split("\\$", 2);
            if (!sp[1].equals(sha256Hex(sp[0] + ":" + parts[1]))) {
                p.sendMessage(c("&#FF1744✖ Неверный пароль!"));
                playSoundSafe(p, "ENTITY_VILLAGER_NO", 1.0f, 1.0f);
                return;
            }
            hubAuthYml.set(k + ".ip", getPlayerIp(p));
            hubAuthYml.set(k + ".until", System.currentTimeMillis() + 21_600_000L);
            saveHubAuthFile();
            hubAuthed.add(p.getUniqueId());
            p.sendTitle(c("&#00E676&l✔ ВХОД ВЫПОЛНЕН"), c("&fВыберите игровой режим в меню!"), 5, 40, 10);
            playSoundSafe(p, "ENTITY_PLAYER_LEVELUP", 1.0f, 1.25f);
            openServersGui(p);
            return;
        }

        if (!hubAuthed.contains(p.getUniqueId())) {
            e.setCancelled(true);
            p.sendMessage(c("&#FF1744🔐 Сначала авторизуйтесь: &f/login <пароль> &7или &f/register <пароль> <повтор>"));
        }
    }

    @EventHandler
    public void onHubInteractCompass(PlayerInteractEvent e) {
        if (e.getHand() == EquipmentSlot.OFF_HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta im = item.getItemMeta();
        if (im != null && im.getPersistentDataContainer().has(itemCompassKey, PersistentDataType.BYTE)) {
            e.setCancelled(true);
            openServersGui(e.getPlayer());
        }
    }

    @EventHandler
    public void onHubMoveAndCombat(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (isStandaloneHubAuthActive() && !hubAuthed.contains(p.getUniqueId())) {
            if (e.getTo() != null && (e.getFrom().getX() != e.getTo().getX() || e.getFrom().getZ() != e.getTo().getZ())) {
                Location back = e.getFrom();
                back.setYaw(e.getTo().getYaw());
                back.setPitch(e.getTo().getPitch());
                e.setTo(back);
            }
            return;
        }
        if (!hubModeEnabled) return;
        if (e.getTo() != null && e.getTo().getY() < 5.0 && hubProtectWorld) {
            Location hubLoc = getHubSpawnLocation();
            if (hubLoc != null) p.teleport(hubLoc);
            return;
        }
        if (hubDoubleJump && e.getTo() != null) {
            Material below = e.getTo().clone().subtract(0, 0.2, 0).getBlock().getType();
            if (below == Material.SLIME_BLOCK) {
                Vector v = p.getLocation().getDirection().multiply(1.35).setY(1.05);
                p.setVelocity(v);
                playSoundSafe(p, "ENTITY_FIREWORK_ROCKET_LAUNCH", 0.9f, 1.2f);
            }
            if (p.isOnGround() && p.getGameMode() == GameMode.SURVIVAL && !p.getAllowFlight()) {
                p.setAllowFlight(true);
            }
        }
    }

    @EventHandler
    public void onHubDoubleJump(PlayerToggleFlightEvent e) {
        if (!hubModeEnabled || !hubDoubleJump) return;
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
        e.setCancelled(true);
        p.setAllowFlight(false);
        p.setFlying(false);
        long now = System.currentTimeMillis();
        Long last = doubleJumpCooldown.get(p.getUniqueId());
        if (last != null && now - last < 1200L) return;
        doubleJumpCooldown.put(p.getUniqueId(), now);
        p.setVelocity(p.getLocation().getDirection().multiply(1.15).setY(0.85));
        playSoundSafe(p, "ENTITY_BAT_TAKEOFF", 0.8f, 1.3f);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHubProtectionAndPvpTrack(EntityDamageEvent e) {
        if (hubModeEnabled && hubProtectWorld && e.getEntity() instanceof Player) {
            e.setCancelled(true);
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
                Location hub = getHubSpawnLocation();
                if (hub != null) e.getEntity().teleport(hub);
            }
            return;
        }
        if (e instanceof EntityDamageByEntityEvent && combatBlockSeconds > 0 && !hubModeEnabled) {
            EntityDamageByEntityEvent ed = (EntityDamageByEntityEvent) e;
            if (!(ed.getEntity() instanceof Player)) return;
            Player victim = (Player) ed.getEntity();
            Player attacker = null;
            Entity damager = ed.getDamager();
            if (damager instanceof Player) {
                attacker = (Player) damager;
            } else if (damager instanceof Projectile && ((Projectile) damager).getShooter() instanceof Player) {
                attacker = (Player) ((Projectile) damager).getShooter();
            }
            if (attacker != null && !attacker.equals(victim)) {
                long until = System.currentTimeMillis() + combatBlockSeconds * 1000L;
                combatUntilMs.put(victim.getUniqueId(), until);
                combatUntilMs.put(attacker.getUniqueId(), until);
            }
        }
    }

    @EventHandler
    public void onHubBlockBreak(BlockBreakEvent e) {
        if (hubModeEnabled && hubProtectWorld && !isAdmin(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onHubBlockPlace(BlockPlaceEvent e) {
        if (hubModeEnabled && hubProtectWorld && !isAdmin(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onHubFood(FoodLevelChangeEvent e) {
        if (hubModeEnabled && hubProtectWorld) {
            e.setCancelled(true);
            if (e.getEntity() instanceof Player) ((Player) e.getEntity()).setFoodLevel(20);
        }
    }

    @EventHandler
    public void onHubDrop(PlayerDropItemEvent e) {
        if (hubModeEnabled && hubProtectWorld && !isAdmin(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onHubWeather(WeatherChangeEvent e) {
        if (hubModeEnabled && hubProtectWorld && e.toWeatherState()) e.setCancelled(true);
    }

    // =========================================================================
    // 6. GUI MENUS (/servers SELECTOR & /kcon ADMIN NETWORK PANEL)
    // =========================================================================

    public void openServersGui(Player p) {
        Inventory inv = Bukkit.createInventory(null, 45, GUI_SERVERS_TITLE);
        populateServersInventory(p, inv);
        p.openInventory(inv);
        playSoundSafe(p, "BLOCK_ENDER_CHEST_OPEN", 0.8f, 1.2f);
    }

    private void populateServersInventory(Player p, Inventory inv) {
        ItemStack border = makeItem(Material.CYAN_STAINED_GLASS_PANE, " ", null, false);
        ItemStack dark = makeItem(Material.BLACK_STAINED_GLASS_PANE, " ", null, false);
        for (int i = 0; i < 45; i++) {
            inv.setItem(i, (i < 9 || i >= 36 || i % 9 == 0 || i % 9 == 8) ? border : dark);
        }

        int totalOnline = getTotalNetworkOnline();
        String modeLabel = "proxy".equalsIgnoreCase(mode) ? "&dVelocity / BungeeCord"
                : ("transfer".equalsIgnoreCase(mode) ? "&aПрямой Transfer 1.21+ (Без Прокси)" : "&bУмный 2-в-1 (Auto)");
        inv.setItem(4, makeItem(Material.BEACON, "&#00E5FF&l🌐 СЕТЬ СЕРВЕРОВ KINGGRIEF", Arrays.asList(
                "&7Выберите игровой режим для мгновенного",
                "&7перехода без повторного ввода пароля!",
                "",
                "&fОбщий онлайн сети: &#00E676" + totalOnline + " игр.",
                "&fВы сейчас на: " + thisServerName,
                "&fВаша привилегия: &#FFD740" + detectPlayerRank(p).toUpperCase(Locale.ROOT),
                "&fРежим связки: " + modeLabel
        ), true));

        for (ServerNode node : servers.values()) {
            int slot = Math.max(9, Math.min(35, node.slot));
            boolean isHere = node.id.equalsIgnoreCase(thisServerId);
            String statusBadge;
            if (isHere) {
                statusBadge = "&#00E676● ОНЛАЙН &7(Вы здесь)";
            } else if (node.isEffectivelyMaintenance()) {
                statusBadge = "&#FF9100⚙ ТЕХ. РАБОТЫ";
            } else if (node.onlineState) {
                statusBadge = "&#00E676● ОНЛАЙН";
            } else if ("proxy".equalsIgnoreCase(mode) || isProxyBackendDetected()) {
                statusBadge = "&#00E5FF● ДОСТУПЕН ЧЕРЕЗ ПРОКСИ";
            } else {
                statusBadge = "&#FF1744● ОФФЛАЙН / ОЖИДАНИЕ";
            }

            List<String> lore = new ArrayList<String>();
            for (String line : node.lore) lore.add(line);
            lore.add("");
            lore.add("&fСтатус: " + statusBadge);
            lore.add("&fОнлайн: &#FFD740" + node.onlinePlayers + "&7/&#FFD740" + node.maxPlayers + " " + buildProgressBar(node.onlinePlayers, node.maxPlayers));
            if (node.pingMs > 0) {
                lore.add("&fПинг узла: &#00E5FF" + node.pingMs + " мс");
            }
            lore.add("");
            if (isHere) {
                lore.add("&#FFD740⭐ Вы сейчас находитесь здесь!");
            } else if (node.isEffectivelyMaintenance() && !isStaff(p)) {
                lore.add("&#FF9100🔒 Временно закрыт на тех. работы");
            } else {
                lore.add("&#00E676▶ Нажмите ЛКМ для перехода!");
            }

            ItemStack card = makeItem(node.icon, node.displayName, lore, isHere);
            ItemMeta im = card.getItemMeta();
            if (im != null) {
                im.getPersistentDataContainer().set(itemServerKey, PersistentDataType.STRING, node.id);
                card.setItemMeta(im);
            }
            inv.setItem(slot, card);
        }

        ServerNode hubNode = servers.get(hubServerId);
        if (hubNode != null && !thisServerId.equalsIgnoreCase(hubServerId)) {
            ItemStack hubBtn = makeItem(Material.DARK_OAK_DOOR, "&#FFD740&l🏰 ВЕРНУТЬСЯ В ХАБ &7(/hub)", Arrays.asList(
                    "&7Быстрый выход в центральное Лобби",
                    "",
                    "&#00E676▶ Нажмите для перехода в Хаб"
            ), false);
            ItemMeta im = hubBtn.getItemMeta();
            if (im != null) {
                im.getPersistentDataContainer().set(itemServerKey, PersistentDataType.STRING, hubNode.id);
                hubBtn.setItemMeta(im);
            }
            inv.setItem(40, hubBtn);
        }
    }

    public void openAdminGui(Player p) {
        Inventory inv = Bukkit.createInventory(null, 45, GUI_ADMIN_TITLE);
        populateAdminInventory(p, inv);
        p.openInventory(inv);
        playSoundSafe(p, "BLOCK_ANVIL_USE", 0.6f, 1.4f);
    }

    private void populateAdminInventory(Player p, Inventory inv) {
        ItemStack pane = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", null, false);
        for (int i = 0; i < 45; i++) inv.setItem(i, pane);

        inv.setItem(4, makeItem(Material.COMMAND_BLOCK, "&#FF5252&l⚙ УПРАВЛЕНИЕ СВЯЗКОЙ KINGCONNECT", Arrays.asList(
                "&fТекущий сервер: &#00E5FF" + thisServerId + " &7(" + thisServerName + "&7)",
                "&fПриём 1.21 Transfer пакетов: " + (safeIsAcceptingTransfers() ? "&#00E676✔ ВКЛЮЧЁН" : "&#FFD740⚡ Будет активен"),
                "&fОблачная шина синхронизации: " + (lastCloudOk ? "&#00E676✔ ПОДКЛЮЧЕНА" : "&#FFD740⏳ Ожидание"),
                "&fВсего серверов в конфиге: &#FFD740" + servers.size()
        ), true));

        inv.setItem(10, makeItem(Material.COMPARATOR, "&#00E5FF&l🔄 РЕЖИМ ПЕРЕНОСА: &f" + mode.toUpperCase(Locale.ROOT), Arrays.asList(
                "&7Доступные режимы работы плагина:",
                " &b• AUTO &7— Умный 2-в-1 (Transfer 1.21+ или Прокси)",
                " &a• TRANSFER &7— Прямой по IP:Port БЕЗ Velocity!",
                " &d• PROXY &7— Через прокси Velocity / BungeeCord",
                "",
                "&#00E676▶ ЛКМ — переключить режим"
        ), false));

        inv.setItem(12, makeItem(hubModeEnabled ? Material.LIME_DYE : Material.GRAY_DYE,
                "&#FFD740&l🏰 РЕЖИМ ХАБА / ЛОББИ: " + (hubModeEnabled ? "&#00E676ВКЛ" : "&#FF5252ВЫКЛ"), Arrays.asList(
                        "&7Включайте на сервере Хаба / Лобби:",
                        "&7выдаёт компас выбора серверов, включает",
                        "&7двойной прыжок, защиту мира и /login!",
                        "",
                        "&#00E676▶ ЛКМ — включить / выключить"
                ), hubModeEnabled));

        inv.setItem(14, makeItem(requireSsoFromHub ? Material.SHIELD : Material.OAK_FENCE_GATE,
                "&#FF9100&l🛡 ВХОД ТОЛЬКО ЧЕРЕЗ ХАБ: " + (requireSsoFromHub ? "&#00E676ВКЛ" : "&#FF5252ВЫКЛ"), Arrays.asList(
                        "&7Если включено на Грифе — кикает всех,",
                        "&7кто пытается зайти напрямую на порт Грифа",
                        "&7в обход главного Лобби без SSO-токена!",
                        "",
                        "&#00E676▶ ЛКМ — переключить защиту"
                ), requireSsoFromHub));

        inv.setItem(16, makeItem(Material.RECOVERY_COMPASS, "&#00E676&l⚡ ОБНОВИТЬ ПИНГ И КОНФИГ", Arrays.asList(
                "&7Мгновенно опросить все серверы связки",
                "&7и перечитать plugins/KingConnect/config.yml",
                "",
                "&#00E676▶ ЛКМ — обновить сейчас"
        ), false));

        int idx = 28;
        for (ServerNode node : servers.values()) {
            if (idx > 34) break;
            boolean isSelf = node.id.equalsIgnoreCase(thisServerId);
            ItemStack item = makeItem(node.icon, node.displayName + " &8[" + node.id + "]", Arrays.asList(
                    "&fПрямой адрес: &#00E5FF" + node.host + ":" + node.port,
                    "&fИмя в Velocity: &#E040FB" + node.proxyName,
                    "&fСтатус: " + (node.onlineState ? "&#00E676ОНЛАЙН (" + node.onlinePlayers + "/" + node.maxPlayers + ")" : "&#FF5252ОФФЛАЙН"),
                    "&fТех. работы: " + (node.maintenance ? "&#FF9100ВКЛ" : "&#00E676ВЫКЛ"),
                    isSelf ? "&#FFD740⭐ Это текущий сервер (this-server-id)" : "&7Обычный узел связки",
                    "",
                    "&#00E5FF▶ ЛКМ — вписать новый IP:Порт в чате",
                    "&#FF9100▶ ПКМ — переключить Тех. работы",
                    "&#FFD740▶ Shift+ЛКМ — сделать текущим сервером"
            ), isSelf);
            ItemMeta im = item.getItemMeta();
            if (im != null) {
                im.getPersistentDataContainer().set(itemServerKey, PersistentDataType.STRING, node.id);
                item.setItemMeta(im);
            }
            inv.setItem(idx++, item);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        String title = e.getView().getTitle();

        // Protect Hub Selector Compass from being moved in Hub Mode
        if (hubModeEnabled && hubProtectWorld && !isAdmin(p)) {
            ItemStack cur = e.getCurrentItem();
            if (cur != null && cur.hasItemMeta() && cur.getItemMeta().getPersistentDataContainer().has(itemCompassKey, PersistentDataType.BYTE)) {
                e.setCancelled(true);
                openServersGui(p);
                return;
            }
        }

        if (GUI_SERVERS_TITLE.equals(title)) {
            e.setCancelled(true);
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;
            String srvId = clicked.getItemMeta().getPersistentDataContainer().get(itemServerKey, PersistentDataType.STRING);
            if (srvId != null) {
                ServerNode target = servers.get(srvId.toLowerCase(Locale.ROOT));
                if (target != null) {
                    connectPlayerToServer(p, target, false);
                }
            }
            return;
        }

        if (GUI_ADMIN_TITLE.equals(title)) {
            e.setCancelled(true);
            if (!isAdmin(p)) {
                p.closeInventory();
                return;
            }
            int slot = e.getRawSlot();
            if (slot == 10) {
                String next = "auto".equals(mode) ? "transfer" : ("transfer".equals(mode) ? "proxy" : "auto");
                getConfig().set("mode", next);
                saveConfig();
                reloadNetworkConfig();
                populateAdminInventory(p, e.getInventory());
                playSoundSafe(p, "UI_BUTTON_CLICK", 0.9f, 1.2f);
                return;
            }
            if (slot == 12) {
                boolean next = !hubModeEnabled;
                getConfig().set("hub-mode.enabled", next);
                saveConfig();
                reloadNetworkConfig();
                populateAdminInventory(p, e.getInventory());
                p.sendMessage(c("&#00E5FF🌐 &lKingConnect &8» &fРежим Хаба переключён: " + (next ? "&#00E676ВКЛ" : "&#FF5252ВЫКЛ")));
                return;
            }
            if (slot == 14) {
                boolean next = !requireSsoFromHub;
                getConfig().set("require-sso-from-hub", next);
                saveConfig();
                reloadNetworkConfig();
                populateAdminInventory(p, e.getInventory());
                return;
            }
            if (slot == 16) {
                reloadNetworkConfig();
                triggerImmediateAsyncCloudPush();
                populateAdminInventory(p, e.getInventory());
                p.sendMessage(c("&#00E676✔ Конфиг KingConnect перезагружен и запущен опрос всех серверов!"));
                playSoundSafe(p, "ENTITY_EXPERIENCE_ORB_PICKUP", 0.9f, 1.3f);
                return;
            }

            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;
            String srvId = clicked.getItemMeta().getPersistentDataContainer().get(itemServerKey, PersistentDataType.STRING);
            if (srvId == null) return;
            ServerNode node = servers.get(srvId);
            if (node == null) return;

            if (e.getClick() == ClickType.SHIFT_LEFT) {
                getConfig().set("this-server-id", node.id);
                getConfig().set("this-server-name", node.displayName);
                saveConfig();
                reloadNetworkConfig();
                populateAdminInventory(p, e.getInventory());
                p.sendMessage(c("&#00E676✔ Этот сервер теперь назначен в связке как: &f" + node.id + " &7(" + node.displayName + "&7)!"));
            } else if (e.getClick() == ClickType.RIGHT) {
                boolean nextMaint = !node.maintenance;
                getConfig().set("servers." + node.id + ".maintenance", nextMaint);
                saveConfig();
                reloadNetworkConfig();
                populateAdminInventory(p, e.getInventory());
            } else if (e.getClick() == ClickType.LEFT) {
                p.closeInventory();
                adminEditingServer.put(p.getUniqueId(), node.id);
                p.sendMessage(c("&#00E5FF🌐 &lKingConnect &8» &fВведите в чат новый &#FFD740IP:Порт &fдля сервера " + node.displayName
                        + " &7(например: &f45.142.122.10:25566 &7или напишите &cотмена&7):"));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAdminChatAddressInput(AsyncPlayerChatEvent e) {
        final Player p = e.getPlayer();
        if (isStandaloneHubAuthActive() && !hubAuthed.contains(p.getUniqueId())) {
            e.setCancelled(true);
            p.sendMessage(c("&#FF1744🔐 Сначала войдите: &f/login <пароль> &7или &f/register <пароль> <повтор>"));
            return;
        }
        final String editingId = adminEditingServer.remove(p.getUniqueId());
        if (editingId == null) return;
        e.setCancelled(true);
        final String msg = e.getMessage().trim();
        if ("отмена".equalsIgnoreCase(msg) || "cancel".equalsIgnoreCase(msg)) {
            p.sendMessage(c("&#FFD740Ввод адреса отменён."));
            return;
        }
        Bukkit.getScheduler().runTask(this, new Runnable() {
            @Override
            public void run() {
                if (!applyAddressUpdate(editingId, msg)) {
                    p.sendMessage(c("&#FF1744✖ Неверный формат! Укажите в формате &fIP:ПОРТ &7(например &f185.10.20.30:25566&7)."));
                    return;
                }
                p.sendMessage(c("&#00E676✔ Адрес сервера &#FFD740" + editingId + " &#00E676успешно сохранён: &f" + msg + "&a!"));
                openAdminGui(p);
            }
        });
    }

    private boolean applyAddressUpdate(String srvId, String hostPort) {
        String hp = hostPort.trim();
        String host = hp;
        int port = 25565;
        int colon = hp.lastIndexOf(':');
        if (colon > 0) {
            host = hp.substring(0, colon).trim();
            try {
                port = Integer.parseInt(hp.substring(colon + 1).trim());
            } catch (NumberFormatException ex) {
                return false;
            }
        }
        if (host.isEmpty() || port <= 0 || port > 65535) return false;
        getConfig().set("servers." + srvId + ".host", host);
        getConfig().set("servers." + srvId + ".port", port);
        saveConfig();
        reloadNetworkConfig();
        return true;
    }

    // =========================================================================
    // 7. CLOUD NETWORK SYNC (HEARTBEATS, /GSC, /GALERT, /SEND, RANKS & KINGAC BANS)
    // =========================================================================

    private void scanLocalBansAndRanksForSync() {
        // 1) Push local KingАЧ auto-bans to global network!
        if (cloudSyncBans) {
            try {
                File kacBans = new File("plugins/KingAC/bans.yml");
                if (kacBans.exists()) {
                    YamlConfiguration y = YamlConfiguration.loadConfiguration(kacBans);
                    ConfigurationSection bs = y.getConfigurationSection("bans");
                    if (bs != null) {
                        long now = System.currentTimeMillis();
                        for (String nickKey : bs.getKeys(false)) {
                            long until = bs.getLong(nickKey + ".until", -1L);
                            if (until > 0 && now > until) continue;
                            String sigKey = nickKey + ":" + until;
                            if (pushedLocalBanKeys.add(sigKey)) {
                                String realName = bs.getString(nickKey + ".name", nickKey);
                                String reason = bs.getString(nickKey + ".reason", "KingАЧ Auto-Ban");
                                String staff = bs.getString(nickKey + ".staff", "KingАЧ");
                                pendingBansJson.add("{\"nick\":\"" + CloudSyncClient.esc(realName)
                                        + "\",\"reason\":\"" + CloudSyncClient.esc(reason)
                                        + "\",\"staff\":\"" + CloudSyncClient.esc(staff)
                                        + "\",\"untilMs\":" + until + "}");
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 2) Push online non-default ranks so privileges sync across servers!
        if (cloudSyncRanks) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                String r = detectPlayerRank(p);
                if (!"default".equalsIgnoreCase(r)) {
                    pendingRanksPush.put(p.getName().toLowerCase(Locale.ROOT), r);
                }
            }
        }
    }

    private String buildCloudSyncPayload() {
        ServerNode self = servers.get(thisServerId);
        String host = getConfig().getString("cloud-sync.public-host", "");
        int port = getConfig().getInt("cloud-sync.public-port", Bukkit.getPort());
        if ((host == null || host.isEmpty()) && self != null) {
            host = self.host;
            port = self.port;
        }
        boolean maint = self != null && self.maintenance;
        List<String> onlineNames = new ArrayList<String>();
        for (Player p : Bukkit.getOnlinePlayers()) onlineNames.add(p.getName());

        List<String> ssoBatch = new ArrayList<String>();
        String item;
        while ((item = pendingSsoJson.poll()) != null) ssoBatch.add(item);

        List<String> evBatch = new ArrayList<String>();
        while ((item = pendingEventsJson.poll()) != null) evBatch.add(item);

        List<String> banBatch = new ArrayList<String>();
        while ((item = pendingBansJson.poll()) != null) banBatch.add(item);

        List<String> unbanBatch = new ArrayList<String>();
        while ((item = pendingUnbans.poll()) != null) unbanBatch.add(item);

        Map<String, String> ranksBatch = new HashMap<String, String>(pendingRanksPush);
        pendingRanksPush.clear();

        StringBuilder sb = new StringBuilder(512);
        sb.append('{');
        sb.append("\"serverId\":\"").append(CloudSyncClient.esc(thisServerId)).append("\",");
        sb.append("\"serverName\":\"").append(CloudSyncClient.esc(thisServerName)).append("\",");
        sb.append("\"host\":\"").append(CloudSyncClient.esc(host)).append("\",");
        sb.append("\"port\":").append(port).append(',');
        sb.append("\"proxyName\":\"").append(CloudSyncClient.esc(self != null ? self.proxyName : thisServerId)).append("\",");
        sb.append("\"online\":").append(onlineNames.size()).append(',');
        sb.append("\"max\":").append(Bukkit.getMaxPlayers()).append(',');
        sb.append("\"tps\":20.0,");
        sb.append("\"maintenance\":").append(maint ? "true" : "false").append(',');
        sb.append("\"players\":").append(CloudSyncClient.buildStringArray(onlineNames)).append(',');
        sb.append("\"pushSso\":[").append(joinRaw(ssoBatch)).append("],");
        sb.append("\"pushRanks\":").append(CloudSyncClient.buildStringMap(ranksBatch)).append(',');
        sb.append("\"pushBans\":[").append(joinRaw(banBatch)).append("],");
        sb.append("\"pushUnbans\":").append(CloudSyncClient.buildStringArray(unbanBatch)).append(',');
        sb.append("\"pushEvents\":[").append(joinRaw(evBatch)).append(']');
        sb.append('}');
        return sb.toString();
    }

    private void triggerImmediateAsyncCloudPush() {
        if (!cloudSyncEnabled || cloudSyncUrl.isEmpty()) return;
        final String payload = buildCloudSyncPayload();
        Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                final String resp = CloudSyncClient.postSync(cloudSyncUrl, secretKey, payload, 2500);
                if (resp != null && resp.startsWith("OK\t")) {
                    lastCloudOk = true;
                    Bukkit.getScheduler().runTask(KingConnectPlugin.this, new Runnable() {
                        @Override
                        public void run() {
                            applyCloudSyncResponse(resp);
                        }
                    });
                }
            }
        });
    }

    private void applyCloudSyncResponse(String text) {
        String[] lines = text.split("\n");
        Set<String> seenBans = new HashSet<String>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) continue;
            String[] f = line.split("\t", -1);
            String kind = f[0];
            if ("NODE".equals(kind) && f.length >= 12) {
                String id = f[1].toLowerCase(Locale.ROOT);
                if (id.equals(thisServerId)) continue;
                ServerNode node = servers.get(id);
                long ageMs = parseLongSafe(f[10], 99_999L);
                if (node != null) {
                    if (ageMs < 20_000L) {
                        node.onlineState = true;
                        node.onlinePlayers = (int) parseLongSafe(f[6], 0L);
                        node.maxPlayers = Math.max(1, (int) parseLongSafe(f[7], 100L));
                        node.techWorksRemote = "1".equals(f[9]);
                        node.lastUpdatedMs = System.currentTimeMillis();
                        node.playerNames = f[11].isEmpty() ? Collections.<String>emptyList() : Arrays.asList(f[11].split(","));
                    }
                }
            } else if ("SSO".equals(kind) && f.length >= 5) {
                cloudSsoCache.put(f[1].toLowerCase(Locale.ROOT), new CloudSsoEntry(f[1], f[2], f[3], f[4]));
            } else if ("RANK".equals(kind) && f.length >= 3) {
                cloudRankCache.put(f[1].toLowerCase(Locale.ROOT), f[2].toLowerCase(Locale.ROOT));
                Player online = Bukkit.getPlayerExact(f[1]);
                if (online != null && cloudSyncRanks) {
                    applyRankToPlayer(online, f[2]);
                }
            } else if ("BAN".equals(kind) && f.length >= 5) {
                String k = f[1].toLowerCase(Locale.ROOT);
                long untilMs = parseLongSafe(f[4], -1L);
                seenBans.add(k);
                cloudBanCache.put(k, new CloudBanEntry(f[1], f[2], f[3], untilMs));
                if (cloudSyncBans) {
                    Player online = Bukkit.getPlayerExact(f[1]);
                    if (online != null && !isOwner(online)) {
                        online.kickPlayer(c("&#FF1744&l🛡 СЕТЬ KINGGRIEF · ВЫ ЗАБАНЕНЫ\n\n"
                                + "&fВыдал: &#FFD740" + f[3] + "\n"
                                + "&fПричина: &#FF5252" + f[2]));
                    }
                }
            } else if ("EVENT".equals(kind) && f.length >= 8) {
                dispatchIncomingNetworkEvent(f[1], f[2], f[3], f[4], f[5], f[6], f[7]);
            }
        }
        cloudBanCache.keySet().retainAll(seenBans);
    }

    private void dispatchIncomingNetworkEvent(String eventId, String type, String fromSrv, String sender, String target, String dest, String msg) {
        if (!processedEventIds.add(eventId)) return;
        if (processedEventIds.size() > 500) processedEventIds.clear();

        if ("staff_chat".equals(type)) {
            ServerNode fn = servers.get(fromSrv.toLowerCase(Locale.ROOT));
            String srvBadge = fn != null ? fn.displayName : ("&b" + fromSrv);
            String formatted = c("&#00E5FF&l🌐 [КП-СЕТЬ] &8[" + srvBadge + "&8] &#FFD740" + sender + "&8: &f" + msg);
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (isStaff(staff)) staff.sendMessage(formatted);
            }
            Bukkit.getConsoleSender().sendMessage(formatted);
        } else if ("global_alert".equals(type)) {
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage(c("&#FF1744&l📢 [ОБЪЯВЛЕНИЕ СЕТИ] &f" + msg + " &8(&7от " + sender + "&8)"));
            Bukkit.broadcastMessage("");
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendTitle(c("&#FF1744&l📢 ВНИМАНИЕ"), c("&f" + msg), 8, 65, 15);
                playSoundSafe(p, "ENTITY_ENDER_DRAGON_GROWL", 0.55f, 1.35f);
            }
        } else if ("send_player".equals(type)) {
            ServerNode destNode = servers.get(dest == null ? "" : dest.toLowerCase(Locale.ROOT));
            if (destNode == null) return;
            if ("all".equalsIgnoreCase(target) || ("server:" + thisServerId).equalsIgnoreCase(target)) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!destNode.id.equalsIgnoreCase(thisServerId)) {
                        connectPlayerToServer(p, destNode, true);
                    }
                }
            } else {
                Player victim = Bukkit.getPlayerExact(target);
                if (victim != null) {
                    connectPlayerToServer(victim, destNode, true);
                }
            }
        }
    }

    // =========================================================================
    // 8. COMMANDS (/servers, /hub, /grief1, /glist, /find, /send, /gsc, /galert, /sethub, /kcon)
    // =========================================================================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        String alias = label.toLowerCase(Locale.ROOT);

        if ("servers".equals(cmd)) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Только для игроков!");
                return true;
            }
            Player p = (Player) sender;
            if (args.length >= 1) {
                ServerNode target = findServerFuzzy(args[0]);
                if (target == null) {
                    p.sendMessage(c("&#FF1744✖ Сервер &#FFD740" + args[0] + " &#FF1744не найден! Открываем меню..."));
                    openServersGui(p);
                    return true;
                }
                connectPlayerToServer(p, target, false);
                return true;
            }
            openServersGui(p);
            return true;
        }

        if ("hub".equals(cmd)) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;
            ServerNode hub = servers.get(hubServerId);
            if (hub == null) {
                p.sendMessage(c("&#FF1744✖ Сервер Хаба (" + hubServerId + ") не настроен в KingConnect!"));
                return true;
            }
            if (hub.id.equalsIgnoreCase(thisServerId) && hubModeEnabled) {
                Location loc = getHubSpawnLocation();
                if (loc != null) p.teleport(loc);
                p.sendMessage(c("&#00E676✔ Вы телепортированы на спавн Хаба!"));
                return true;
            }
            connectPlayerToServer(p, hub, false);
            return true;
        }

        if ("grief".equals(cmd)) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;
            String targetKey = ("grief".equals(alias)) ? "grief1" : alias;
            if (args.length >= 1) targetKey = args[0];
            ServerNode target = findServerFuzzy(targetKey);
            if (target == null) {
                openServersGui(p);
                return true;
            }
            connectPlayerToServer(p, target, false);
            return true;
        }

        if ("glist".equals(cmd)) {
            sender.sendMessage(c("&#00E5FF&l🌐 ОНЛАЙН СВЯЗКИ KINGGRIEF &8(Всего: &#00E676" + getTotalNetworkOnline() + " игр.&8)"));
            for (ServerNode n : servers.values()) {
                boolean here = n.id.equalsIgnoreCase(thisServerId);
                String st = (here || n.onlineState) ? "&#00E676●" : "&#FF1744●";
                sender.sendMessage(c(" " + st + " " + n.displayName + " &8[" + n.id + "] &7— &#FFD740"
                        + n.onlinePlayers + "&7/&f" + n.maxPlayers + (here ? " &a(Вы здесь)" : "")));
            }
            return true;
        }

        if ("find".equals(cmd)) {
            if (args.length < 1) {
                sender.sendMessage(c("&#FFD740Использование: &f/find <ник>"));
                return true;
            }
            String q = args[0];
            if (Bukkit.getPlayerExact(q) != null) {
                sender.sendMessage(c("&#00E676✔ Игрок &f" + q + " &#00E676играет на текущем сервере: " + thisServerName));
                return true;
            }
            for (ServerNode n : servers.values()) {
                for (String pn : n.playerNames) {
                    if (pn.equalsIgnoreCase(q)) {
                        sender.sendMessage(c("&#00E676✔ Игрок &f" + pn + " &#00E676сейчас на сервере: " + n.displayName + " &8(" + n.id + ")"));
                        return true;
                    }
                }
            }
            sender.sendMessage(c("&#FF1744✖ Игрок &f" + q + " &#FF1744не найден ни на одном сервере связки."));
            return true;
        }

        if ("gsc".equals(cmd)) {
            if (sender instanceof Player && !isStaff((Player) sender)) {
                sender.sendMessage(c("&#FF1744✖ Нет доступа к межсерверному чату администрации!"));
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage(c("&#00E5FFИспользование: &f/gsc <сообщение для КП на всех серверах>"));
                return true;
            }
            String text = joinArgs(args, 0);
            String senderName = sender.getName();
            String localLine = c("&#00E5FF&l🌐 [КП-СЕТЬ] &8[" + thisServerName + "&8] &#FFD740" + senderName + "&8: &f" + text);
            for (Player st : Bukkit.getOnlinePlayers()) {
                if (isStaff(st)) st.sendMessage(localLine);
            }
            Bukkit.getConsoleSender().sendMessage(localLine);

            pendingEventsJson.add("{\"type\":\"staff_chat\",\"sender\":\"" + CloudSyncClient.esc(senderName)
                    + "\",\"message\":\"" + CloudSyncClient.esc(text) + "\"}");
            triggerImmediateAsyncCloudPush();
            sendProxyBusPacket("staff_chat", senderName, "", "", text);
            return true;
        }

        if ("galert".equals(cmd)) {
            if (sender instanceof Player && !isAdmin((Player) sender)) {
                sender.sendMessage(c("&#FF1744✖ Только Администрация связки может отправлять глобальные объявления!"));
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage(c("&#FFD740Использование: &f/galert <текст объявления на все серверы>"));
                return true;
            }
            String text = joinArgs(args, 0);
            dispatchIncomingNetworkEvent("local-" + System.nanoTime(), "global_alert", thisServerId, sender.getName(), "", "", text);
            pendingEventsJson.add("{\"type\":\"global_alert\",\"sender\":\"" + CloudSyncClient.esc(sender.getName())
                    + "\",\"message\":\"" + CloudSyncClient.esc(text) + "\"}");
            triggerImmediateAsyncCloudPush();
            sendProxyBusPacket("global_alert", sender.getName(), "", "", text);
            return true;
        }

        if ("send".equals(cmd)) {
            if (sender instanceof Player && !isAdmin((Player) sender)) {
                sender.sendMessage(c("&#FF1744✖ У вас нет прав на переброс игроков (/send)!"));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(c("&#FFD740Использование: &f/send <ник | current | all> <сервер>"));
                return true;
            }
            String who = args[0];
            ServerNode dest = findServerFuzzy(args[1]);
            if (dest == null) {
                sender.sendMessage(c("&#FF1744✖ Целевой сервер &#FFD740" + args[1] + " &#FF1744не найден!"));
                return true;
            }
            if ("current".equalsIgnoreCase(who)) {
                int moved = 0;
                for (Player pl : Bukkit.getOnlinePlayers()) {
                    connectPlayerToServer(pl, dest, true);
                    moved++;
                }
                sender.sendMessage(c("&#00E676✔ Переправлено игроков с текущего сервера на " + dest.displayName + "&a: &f" + moved));
                return true;
            }
            if ("all".equalsIgnoreCase(who)) {
                for (Player pl : Bukkit.getOnlinePlayers()) {
                    if (!dest.id.equalsIgnoreCase(thisServerId)) connectPlayerToServer(pl, dest, true);
                }
                pendingEventsJson.add("{\"type\":\"send_player\",\"sender\":\"" + CloudSyncClient.esc(sender.getName())
                        + "\",\"target\":\"all\",\"destination\":\"" + CloudSyncClient.esc(dest.id) + "\"}");
                triggerImmediateAsyncCloudPush();
                sendProxyBusPacket("send_player", sender.getName(), "all", dest.id, "");
                sender.sendMessage(c("&#00E676✔ Команда перевода ВСЕХ игроков сети на " + dest.displayName + " &#00E676отправлена!"));
                return true;
            }
            Player localTarget = Bukkit.getPlayerExact(who);
            if (localTarget != null) {
                connectPlayerToServer(localTarget, dest, true);
            } else {
                pendingEventsJson.add("{\"type\":\"send_player\",\"sender\":\"" + CloudSyncClient.esc(sender.getName())
                        + "\",\"target\":\"" + CloudSyncClient.esc(who)
                        + "\",\"destination\":\"" + CloudSyncClient.esc(dest.id) + "\"}");
                triggerImmediateAsyncCloudPush();
                sendProxyBusPacket("send_player", sender.getName(), who, dest.id, "");
            }
            sender.sendMessage(c("&#00E676✔ Запрос на перевод игрока &f" + who + " &#00E676на " + dest.displayName + " &#00E676выполнен!"));
            return true;
        }

        if ("sethub".equals(cmd)) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;
            if (!isAdmin(p)) {
                p.sendMessage(c("&#FF1744✖ У вас нет прав!"));
                return true;
            }
            Location l = p.getLocation();
            getConfig().set("hub-mode.spawn.world", l.getWorld().getName());
            getConfig().set("hub-mode.spawn.x", l.getX());
            getConfig().set("hub-mode.spawn.y", l.getY());
            getConfig().set("hub-mode.spawn.z", l.getZ());
            getConfig().set("hub-mode.spawn.yaw", (double) l.getYaw());
            getConfig().set("hub-mode.spawn.pitch", (double) l.getPitch());
            saveConfig();
            p.sendMessage(c("&#00E676✔ Точка спавна Хаба успешно сохранена! Чтобы включить режим Хаба: &f/kcon hubmode on"));
            return true;
        }

        if ("kingconnect".equals(cmd)) {
            if (sender instanceof Player && !isAdmin((Player) sender)) {
                sender.sendMessage(c("&#FF1744✖ Панель связки доступна только Администрации (OWNER)!"));
                return true;
            }
            if (args.length == 0 && sender instanceof Player) {
                openAdminGui((Player) sender);
                return true;
            }
            if (args.length >= 1) {
                String sub = args[0].toLowerCase(Locale.ROOT);
                if ("reload".equals(sub)) {
                    reloadNetworkConfig();
                    sender.sendMessage(c("&#00E676✔ Конфигурация KingConnect перезагружена!"));
                    return true;
                }
                if ("status".equals(sub)) {
                    sender.sendMessage(c("&#00E5FF&l🌐 СТАТУС KINGCONNECT v1.0.0"));
                    sender.sendMessage(c(" &7• Текущий сервер: &f" + thisServerId + " &7(" + thisServerName + "&7)"));
                    sender.sendMessage(c(" &7• Режим работы: &#FFD740" + mode + " &7| Приём Transfer 1.21+: " + (safeIsAcceptingTransfers() ? "&aДА" : "&eОЖИДАНИЕ")));
                    sender.sendMessage(c(" &7• Режим Хаба: " + (hubModeEnabled ? "&aВКЛ" : "&cВЫКЛ") + " &7| Облачная синхронизация: " + (lastCloudOk ? "&aОНЛАЙН" : "&eОЖИДАНИЕ")));
                    return true;
                }
                if ("hubmode".equals(sub)) {
                    boolean next = args.length >= 2 ? ("on".equalsIgnoreCase(args[1]) || "true".equalsIgnoreCase(args[1])) : !hubModeEnabled;
                    getConfig().set("hub-mode.enabled", next);
                    if (next) {
                        getConfig().set("this-server-id", hubServerId);
                    }
                    saveConfig();
                    reloadNetworkConfig();
                    sender.sendMessage(c("&#00E676✔ Режим Хаба: " + (next ? "&aВКЛЮЧЁН (this-server-id=" + thisServerId + ")" : "&cВЫКЛЮЧЕН")));
                    return true;
                }
                if ("mode".equals(sub) && args.length >= 2) {
                    String m = args[1].toLowerCase(Locale.ROOT);
                    if (!"auto".equals(m) && !"transfer".equals(m) && !"proxy".equals(m)) {
                        sender.sendMessage(c("&#FF1744Доступные режимы: &fauto, transfer, proxy"));
                        return true;
                    }
                    getConfig().set("mode", m);
                    saveConfig();
                    reloadNetworkConfig();
                    sender.sendMessage(c("&#00E676✔ Режим связки установлен: &#FFD740" + m));
                    return true;
                }
                if (("setaddress".equals(sub) || "setport".equals(sub)) && args.length >= 3) {
                    String id = args[1].toLowerCase(Locale.ROOT);
                    if (!servers.containsKey(id)) {
                        sender.sendMessage(c("&#FF1744Сервер " + id + " не найден!"));
                        return true;
                    }
                    if (!applyAddressUpdate(id, args[2])) {
                        sender.sendMessage(c("&#FF1744Формат: /kcon setaddress <id> <ip:port>"));
                        return true;
                    }
                    sender.sendMessage(c("&#00E676✔ Адрес сервера &f" + id + " &#00E676обновлён на &f" + args[2]));
                    return true;
                }
                if ("add".equals(sub) && args.length >= 3) {
                    String id = args[1].toLowerCase(Locale.ROOT);
                    String hp = args[2];
                    String disp = args.length >= 4 ? joinArgs(args, 3) : ("&#00E5FF&l" + id.toUpperCase(Locale.ROOT));
                    int freeSlot = 19 + Math.min(15, servers.size());
                    getConfig().set("servers." + id + ".name", disp);
                    getConfig().set("servers." + id + ".proxy-name", id);
                    getConfig().set("servers." + id + ".icon", "ENDER_EYE");
                    getConfig().set("servers." + id + ".slot", freeSlot);
                    getConfig().set("servers." + id + ".maintenance", false);
                    getConfig().set("servers." + id + ".lore", Arrays.asList("&7Игровой сервер связки KINGGRIEF"));
                    if (!applyAddressUpdate(id, hp)) {
                        sender.sendMessage(c("&#FF1744Формат: /kcon add <id> <ip:port> [Название]"));
                        return true;
                    }
                    sender.sendMessage(c("&#00E676✔ Сервер &f" + id + " &7(" + hp + ") &#00E676добавлен в меню /servers!"));
                    return true;
                }
            }
            sender.sendMessage(c("&#00E5FF&l🌐 Команды KingConnect:"));
            sender.sendMessage(c(" &f/kcon &7— открыть GUI-панель управления связкой"));
            sender.sendMessage(c(" &f/kcon setaddress <id> <ip:port> &7— задать IP:Порт сервера"));
            sender.sendMessage(c(" &f/kcon add <id> <ip:port> <Название> &7— добавить новый сервер"));
            sender.sendMessage(c(" &f/kcon mode <auto|transfer|proxy> &7— выбрать режим работы"));
            sender.sendMessage(c(" &f/kcon hubmode <on|off> &7— включить режим Лобби/Хаба"));
            sender.sendMessage(c(" &f/kcon reload &7— перезагрузить конфиг"));
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if ("servers".equals(cmd) && args.length == 1) {
            return filterPrefix(new ArrayList<String>(servers.keySet()), args[0]);
        }
        if ("send".equals(cmd)) {
            if (args.length == 1) {
                List<String> opts = new ArrayList<String>(Arrays.asList("current", "all"));
                for (Player p : Bukkit.getOnlinePlayers()) opts.add(p.getName());
                return filterPrefix(opts, args[0]);
            }
            if (args.length == 2) {
                return filterPrefix(new ArrayList<String>(servers.keySet()), args[1]);
            }
        }
        if ("kingconnect".equals(cmd)) {
            if (args.length == 1) {
                return filterPrefix(Arrays.asList("status", "setaddress", "add", "mode", "hubmode", "reload"), args[0]);
            }
            if (args.length == 2 && "mode".equalsIgnoreCase(args[0])) {
                return filterPrefix(Arrays.asList("auto", "transfer", "proxy"), args[1]);
            }
            if (args.length == 2 && "setaddress".equalsIgnoreCase(args[0])) {
                return filterPrefix(new ArrayList<String>(servers.keySet()), args[1]);
            }
        }
        return Collections.emptyList();
    }

    // =========================================================================
    // 9. HELPERS, RANK / COMBAT BRIDGES & CRYPTOGRAPHY
    // =========================================================================

    private ServerNode findServerFuzzy(String q) {
        if (q == null) return null;
        String k = q.trim().toLowerCase(Locale.ROOT);
        if (servers.containsKey(k)) return servers.get(k);
        for (ServerNode n : servers.values()) {
            if (n.proxyName.equalsIgnoreCase(k) || n.id.startsWith(k)) return n;
        }
        return null;
    }

    private int getTotalNetworkOnline() {
        int sum = 0;
        for (ServerNode n : servers.values()) {
            if (n.id.equalsIgnoreCase(thisServerId)) {
                sum += Bukkit.getOnlinePlayers().size();
            } else if (n.onlineState) {
                sum += Math.max(0, n.onlinePlayers);
            }
        }
        return sum;
    }

    private int getCombatSecondsLeft(Player p) {
        try {
            Plugin kg = Bukkit.getPluginManager().getPlugin("KingGriefCore");
            if (kg != null && kg.isEnabled()) {
                Method getHud = kg.getClass().getMethod("getHud");
                Object hud = getHud.invoke(kg);
                if (hud != null) {
                    Method secLeft = hud.getClass().getMethod("getCombatSecondsLeft", Player.class);
                    Object val = secLeft.invoke(hud, p);
                    if (val instanceof Integer && ((Integer) val) > 0) {
                        return (Integer) val;
                    }
                }
            }
        } catch (Throwable ignored) {}

        Long until = combatUntilMs.get(p.getUniqueId());
        if (until == null) return 0;
        long diff = until - System.currentTimeMillis();
        if (diff <= 0) {
            combatUntilMs.remove(p.getUniqueId());
            return 0;
        }
        return (int) Math.ceil(diff / 1000.0);
    }

    private String detectPlayerRank(Player p) {
        try {
            Plugin kg = Bukkit.getPluginManager().getPlugin("KingGriefCore");
            if (kg != null && kg.isEnabled()) {
                Method getRanks = kg.getClass().getMethod("getRanks");
                Object rm = getRanks.invoke(kg);
                Method getRank = rm.getClass().getMethod("getRank", Player.class);
                Object rankObj = getRank.invoke(rm, p);
                if (rankObj != null) {
                    Field idField = rankObj.getClass().getDeclaredField("id");
                    idField.setAccessible(true);
                    Object idVal = idField.get(rankObj);
                    if (idVal != null) return String.valueOf(idVal).toLowerCase(Locale.ROOT);
                }
            }
        } catch (Throwable ignored) {}
        return p.isOp() ? "owner" : "default";
    }

    private int detectRankWeight(Player p) {
        try {
            Plugin kg = Bukkit.getPluginManager().getPlugin("KingGriefCore");
            if (kg != null && kg.isEnabled()) {
                Method getRanks = kg.getClass().getMethod("getRanks");
                Object rm = getRanks.invoke(kg);
                Method getRank = rm.getClass().getMethod("getRank", Player.class);
                Object rankObj = getRank.invoke(rm, p);
                if (rankObj != null) {
                    Field wField = rankObj.getClass().getDeclaredField("weight");
                    wField.setAccessible(true);
                    return wField.getInt(rankObj);
                }
            }
        } catch (Throwable ignored) {}
        return p.isOp() ? 300 : 5;
    }

    private void applyRankToPlayer(Player p, String newRank) {
        if (newRank == null || newRank.isEmpty() || "default".equalsIgnoreCase(newRank)) return;
        String current = detectPlayerRank(p);
        if (current.equalsIgnoreCase(newRank) || "owner".equalsIgnoreCase(current)) return;
        // Never auto-grant 'owner' via network sync unless already owner locally
        if ("owner".equalsIgnoreCase(newRank)) return;
        try {
            Plugin kg = Bukkit.getPluginManager().getPlugin("KingGriefCore");
            if (kg != null && kg.isEnabled()) {
                Method getStore = kg.getClass().getMethod("getStore");
                Method getRanks = kg.getClass().getMethod("getRanks");
                Object store = getStore.invoke(kg);
                Object rm = getRanks.invoke(kg);
                Method setRank = store.getClass().getMethod("setPlayerRank", String.class, String.class);
                setRank.invoke(store, p.getName(), newRank.toLowerCase(Locale.ROOT));
                Method refresh = store.getClass().getMethod("refreshPermissions", Player.class, rm.getClass());
                refresh.invoke(store, p, rm);
                return;
            }
            if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + p.getName() + " parent set " + newRank.toLowerCase(Locale.ROOT));
            }
        } catch (Throwable ignored) {}
    }

    private boolean isOwner(Player p) {
        return "owner".equalsIgnoreCase(detectPlayerRank(p));
    }

    private boolean isAdmin(Player p) {
        return isOwner(p) || detectRankWeight(p) >= 275 || p.hasPermission("kingconnect.admin");
    }

    private boolean isStaff(Player p) {
        return detectRankWeight(p) >= 200 || p.hasPermission("kingconnect.staff") || isAdmin(p);
    }

    private void loadHubAuthFile() {
        hubAuthFile = new File(getDataFolder(), "hub_auth.yml");
        if (!hubAuthFile.exists()) {
            try {
                getDataFolder().mkdirs();
                hubAuthFile.createNewFile();
            } catch (Throwable ignored) {}
        }
        hubAuthYml = YamlConfiguration.loadConfiguration(hubAuthFile);
    }

    private void saveHubAuthFile() {
        if (hubAuthYml != null && hubAuthFile != null) {
            try {
                hubAuthYml.save(hubAuthFile);
            } catch (Throwable ignored) {}
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
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static ItemStack makeItem(Material mat, String name, List<String> lore, boolean glint) {
        ItemStack is = new ItemStack(mat == null ? Material.STONE : mat);
        ItemMeta im = is.getItemMeta();
        if (im != null) {
            im.setDisplayName(c(name));
            if (lore != null && !lore.isEmpty()) {
                List<String> colored = new ArrayList<String>(lore.size());
                for (String s : lore) colored.add(c(s));
                im.setLore(colored);
            }
            if (glint) {
                im.addEnchant(Enchantment.UNBREAKING, 1, true);
            }
            im.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            is.setItemMeta(im);
        }
        return is;
    }

    private static String buildProgressBar(int cur, int max) {
        int totalBars = 10;
        int filled = max <= 0 ? 0 : Math.min(totalBars, Math.max(0, (int) Math.round((cur * 10.0) / max)));
        StringBuilder sb = new StringBuilder("&8[");
        for (int i = 0; i < totalBars; i++) {
            sb.append(i < filled ? "&#00E676█" : "&7▒");
        }
        sb.append("&8]");
        return sb.toString();
    }

    private static String fmtDuration(long sec) {
        if (sec <= 0) return "1 сек.";
        long d = sec / 86400;
        long h = (sec % 86400) / 3600;
        long m = (sec % 3600) / 60;
        if (d > 0) return d + " дн. " + h + " ч.";
        if (h > 0) return h + " ч. " + m + " мин.";
        return Math.max(1L, m) + " мин.";
    }

    private static long parseLongSafe(String s, long def) {
        try {
            return Long.parseLong(s.trim());
        } catch (Throwable t) {
            return def;
        }
    }

    private static String joinArgs(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private static String joinRaw(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(items.get(i));
        }
        return sb.toString();
    }

    private static List<String> filterPrefix(List<String> src, String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<String>();
        for (String s : src) {
            if (s.toLowerCase(Locale.ROOT).startsWith(p)) out.add(s);
        }
        return out;
    }

    private static void playSoundSafe(Player p, String soundName, float vol, float pitch) {
        try {
            p.playSound(p.getLocation(), Sound.valueOf(soundName), vol, pitch);
        } catch (Throwable ignored) {}
    }

    public static String c(String msg) {
        if (msg == null) return "";
        Matcher matcher = HEX_PATTERN.matcher(msg);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder rep = new StringBuilder("\u00A7x");
            for (int i = 0; i < 6; i++) {
                rep.append('\u00A7').append(hex.charAt(i));
            }
            matcher.appendReplacement(buffer, rep.toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}
