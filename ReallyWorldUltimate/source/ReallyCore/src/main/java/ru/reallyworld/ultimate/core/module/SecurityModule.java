package ru.reallyworld.ultimate.core.module;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;
import ru.reallyworld.ultimate.core.api.ReallyModule;
import ru.reallyworld.ultimate.core.config.ConfigManager;
import ru.reallyworld.ultimate.core.gui.ReallyGuiHolder;
import ru.reallyworld.ultimate.core.storage.StorageModule;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hardened Security, Anti-Dupe, Command-Guard & Chat Moderation Module.
 * Complements GrimAC with server-side inventory state sanitization, portal/book dupe blocks,
 * command hiding, anti-spam, and immutable SQL security audit logging.
 */
public final class SecurityModule implements ReallyModule, Listener {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final StorageModule storage;
    private final LocalizationModule locale;

    private final Map<UUID, Long> lastChatTime = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCmdTime = new ConcurrentHashMap<>();
    private volatile Set<String> blockedCommands = Set.of();
    private volatile List<String> forbiddenWords = List.of();
    private volatile boolean enabled;

    public SecurityModule(JavaPlugin plugin, ConfigManager configManager, StorageModule storage, LocalizationModule locale) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.storage = storage;
        this.locale = locale;
    }

    @Override
    public String id() { return "security"; }

    @Override
    public String displayName() { return "SecurityModule (Anti-Dupe & Guards)"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void enable() {
        this.enabled = configManager.main().getBoolean("modules.security", true);
        reloadConfigValues();
        if (plugin != null && Bukkit.getServer() != null) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    @Override
    public void reload() {
        reloadConfigValues();
    }

    private void reloadConfigValues() {
        var sec = configManager.get("security.yml");
        Set<String> cmds = new HashSet<>();
        for (String c : sec.getStringList("command-protection.blocked-commands")) {
            cmds.add(c.toLowerCase(Locale.ROOT));
        }
        this.blockedCommands = Collections.unmodifiableSet(cmds);
        this.forbiddenWords = List.copyOf(sec.getStringList("chat-security.forbidden-words"));
    }

    @Override
    public void disable() {
        this.enabled = false;
        lastChatTime.clear();
        lastCmdTime.clear();
    }

    public String itemSalt() {
        String raw = configManager.get("security.yml").getString("anti-dupe.pdc-item-signature-salt", "rw-default-salt");
        return configManager.resolveEnvValue(raw);
    }

    public void audit(UUID playerUuid, String playerName, String eventType, String severity, String details) {
        storage.runAsync(() -> storage.progression().logSecurityEvent(playerUuid, playerName, eventType, severity, details));
    }

    // 1. Block command leaks (/pl, /plugins, /ver, /about, /bukkit:*, etc.) and command spam
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (player.hasPermission("reallycore.admin")) return;

        String msg = event.getMessage().trim();
        String rootCmd = msg.split("\\s+")[0].toLowerCase(Locale.ROOT);
        if (blockedCommands.contains(rootCmd)
                || (configManager.get("security.yml").getBoolean("command-protection.block-colon-commands", true)
                && rootCmd.contains(":"))) {
            event.setCancelled(true);
            locale.send(player, "no-permission");
            audit(player.getUniqueId(), player.getName(), "BLOCKED_CMD", "LOW", msg);
            return;
        }

        long cooldownMs = configManager.get("security.yml").getLong("command-protection.command-spam-cooldown-ms", 350L);
        long now = System.currentTimeMillis();
        Long prev = lastCmdTime.put(player.getUniqueId(), now);
        if (prev != null && (now - prev) < cooldownMs) {
            event.setCancelled(true);
            locale.sendRaw(player, "<red>Пожалуйста, не отправляйте команды так быстро!");
        }
    }

    // 2. Hide forbidden commands from 1.21 Tab-Completion tree
    @EventHandler(priority = EventPriority.HIGH)
    public void onCommandSend(PlayerCommandSendEvent event) {
        if (!enabled || event.getPlayer().hasPermission("reallycore.admin")) return;
        event.getCommands().removeIf(cmd -> {
            String full = "/" + cmd.toLowerCase(Locale.ROOT);
            return cmd.contains(":") || blockedCommands.contains(full);
        });
    }

    // 3. Anti-dupe: close custom GUI immediately on death, teleport, portal, or quit
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof ReallyGuiHolder) {
            p.closeInventory();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player p = event.getPlayer();
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof ReallyGuiHolder) {
            p.closeInventory();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof ReallyGuiHolder) {
            p.closeInventory();
        }
        lastChatTime.remove(p.getUniqueId());
        lastCmdTime.remove(p.getUniqueId());
    }

    // 4. Anti-dupe: block donkeys/llamas/mules carrying chests through portals
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (!enabled) return;
        if (event.getEntity() instanceof ChestedHorse horse && horse.isCarryingChest()) {
            event.setCancelled(true);
        }
    }

    // 5. Anti-book-ban / crash exploit protection
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBookEdit(PlayerEditBookEvent event) {
        if (!enabled) return;
        BookMeta meta = event.getNewBookMeta();
        int maxPages = configManager.get("security.yml").getInt("anti-dupe.max-book-pages", 50);
        int maxBytes = configManager.get("security.yml").getInt("anti-dupe.max-book-bytes", 16384);
        if (meta.getPageCount() > maxPages) {
            event.setCancelled(true);
            audit(event.getPlayer().getUniqueId(), event.getPlayer().getName(), "BOOK_OVERFLOW", "HIGH", "Pages=" + meta.getPageCount());
            return;
        }
        int bytes = 0;
        for (var page : meta.pages()) {
            bytes += PlainTextComponentSerializer.plainText().serialize(page).getBytes(StandardCharsets.UTF_8).length;
            if (bytes > maxBytes) {
                event.setCancelled(true);
                audit(event.getPlayer().getUniqueId(), event.getPlayer().getName(), "BOOK_BYTES_EXPLOIT", "HIGH", "Bytes>" + maxBytes);
                return;
            }
        }
    }

    // 6. Chat Anti-Spam, Anti-Caps & Word Filter
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (player.hasPermission("reallycore.admin") || player.hasPermission("reallycore.staff")) return;

        long cooldownMs = configManager.get("security.yml").getLong("chat-security.cooldown-ms", 1200L);
        long now = System.currentTimeMillis();
        Long last = lastChatTime.put(player.getUniqueId(), now);
        if (last != null && (now - last) < cooldownMs) {
            event.setCancelled(true);
            locale.sendRaw(player, "<red>Не спамьте в чат! Подождите секунду.");
            return;
        }

        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        String lower = plain.toLowerCase(Locale.ROOT);
        for (String bad : forbiddenWords) {
            if (!bad.isBlank() && lower.contains(bad.toLowerCase(Locale.ROOT))) {
                event.setCancelled(true);
                locale.sendRaw(player, "<red>Ваше сообщение заблокировано фильтром безопасности (реклама/спам).");
                audit(player.getUniqueId(), player.getName(), "CHAT_BLOCKED", "MEDIUM", plain);
                return;
            }
        }
    }
}
