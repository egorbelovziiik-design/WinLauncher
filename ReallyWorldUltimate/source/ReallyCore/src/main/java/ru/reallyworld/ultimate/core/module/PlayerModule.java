package ru.reallyworld.ultimate.core.module;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.*;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.reallyworld.ultimate.core.api.ReallyModule;
import ru.reallyworld.ultimate.core.config.ConfigManager;
import ru.reallyworld.ultimate.core.config.RankDefinition;
import ru.reallyworld.ultimate.core.storage.StorageModule;
import ru.reallyworld.ultimate.core.storage.model.HomePoint;
import ru.reallyworld.ultimate.core.storage.model.PlayerProfile;
import ru.reallyworld.ultimate.core.storage.model.WarpPoint;
import ru.reallyworld.ultimate.core.util.TextUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

/**
 * Manages Online Player Profiles, Rank Permission Resolution (standalone + LuckPerms),
 * Safe Spawn/RTP/Homes/TPA/Warps with warmup cancellation, Ignore list, and Chat Formatting.
 */
public final class PlayerModule implements ReallyModule, Listener {
    public record TpaRequest(UUID senderUuid, String senderName, UUID targetUuid, boolean tpaHere, long expiresAt) {}
    private record PendingTeleport(Location origin, Location destination, BukkitTask task) {}

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final StorageModule storage;
    private final LocalizationModule locale;
    private final CooldownModule cooldowns;

    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private final Map<UUID, List<HomePoint>> homesCache = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> ignoresCache = new ConcurrentHashMap<>();
    private final Map<String, WarpPoint> warpsCache = new ConcurrentHashMap<>();
    private final Map<UUID, TpaRequest> incomingTpa = new ConcurrentHashMap<>();
    private final Map<UUID, PendingTeleport> pendingTeleports = new ConcurrentHashMap<>();
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();

    private ClanModule clanModule;
    private QuestsModule questsModule;
    private CombatModule combatModule;
    private BukkitTask playtimeTicker;
    private volatile boolean enabled;

    public PlayerModule(
            JavaPlugin plugin,
            ConfigManager configManager,
            StorageModule storage,
            LocalizationModule locale,
            CooldownModule cooldowns
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.storage = storage;
        this.locale = locale;
        this.cooldowns = cooldowns;
    }

    public void bindModules(ClanModule clanModule, QuestsModule questsModule, CombatModule combatModule) {
        this.clanModule = clanModule;
        this.questsModule = questsModule;
        this.combatModule = combatModule;
    }

    @Override
    public String id() { return "player"; }

    @Override
    public String displayName() { return "PlayerModule (Profiles, Ranks, Teleport)"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void enable() {
        this.enabled = configManager.main().getBoolean("modules.player", true);
        reloadWarps();
        ensureDefaultWarps();
        if (plugin != null && Bukkit.getServer() != null) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            for (Player online : Bukkit.getOnlinePlayers()) {
                loadOnlinePlayerSync(online);
            }
            this.playtimeTicker = Bukkit.getScheduler().runTaskTimer(plugin, this::tickPlaytimeAndAutosave, 20L * 60L, 20L * 60L);
        }
    }

    @Override
    public void reload() {
        reloadWarps();
        if (plugin != null && Bukkit.getServer() != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                applyRankPermissions(p);
            }
        }
    }

    @Override
    public void disable() {
        this.enabled = false;
        if (playtimeTicker != null) {
            playtimeTicker.cancel();
        }
        pendingTeleports.values().forEach(pt -> pt.task().cancel());
        pendingTeleports.clear();
        for (PlayerProfile p : profiles.values()) {
            storage.players().saveProfileStats(p);
        }
        if (Bukkit.getServer() != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                PermissionAttachment att = attachments.remove(p.getUniqueId());
                if (att != null) {
                    p.removeAttachment(att);
                }
            }
        }
        profiles.clear();
        homesCache.clear();
        ignoresCache.clear();
    }

    private void reloadWarps() {
        warpsCache.clear();
        for (WarpPoint w : storage.players().getWarps()) {
            warpsCache.put(w.name().toLowerCase(Locale.ROOT), w);
        }
    }

    private void ensureDefaultWarps() {
        if (!warpsCache.isEmpty()) return;
        String wName = configManager.main().getString("spawn.world", "world");
        long now = System.currentTimeMillis();
        List<WarpPoint> defaults = List.of(
                new WarpPoint("spawn", "Главный Спавн", "MAIN", null, wName, 0.5, 100.0, 0.5, 0f, 0f, "SYSTEM", now),
                new WarpPoint("pvp", "Колизей PvP", "PVP", null, wName, 0.5, 100.0, 42.5, 0f, 0f, "SYSTEM", now),
                new WarpPoint("cases", "Зал Кейсов", "DONATE", null, wName, 24.5, 100.0, 0.5, -90f, 0f, "SYSTEM", now),
                new WarpPoint("shop", "Торговый Квартал", "ECONOMY", null, wName, -24.5, 100.0, 0.5, 90f, 0f, "SYSTEM", now),
                new WarpPoint("mine", "Автошахта", "FARM", null, wName, 0.5, 100.0, -32.5, 180f, 0f, "SYSTEM", now)
        );
        for (WarpPoint wp : defaults) {
            storage.players().upsertWarp(wp);
            warpsCache.put(wp.name(), wp);
        }
    }

    private void tickPlaytimeAndAutosave() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            PlayerProfile p = profiles.get(online.getUniqueId());
            if (p != null) {
                p.setPlaytimeSeconds(p.playtimeSeconds() + 60L);
                p.setLastSeenAt(System.currentTimeMillis());
                if (questsModule != null) {
                    questsModule.recordProgress(online, "PLAYTIME_SECONDS", 60L);
                }
                storage.runAsync(() -> storage.players().saveProfileStats(p));
            }
        }
    }

    public PlayerProfile getOrLoadProfile(UUID uuid, String name) {
        PlayerProfile cached = profiles.get(uuid);
        if (cached != null) return cached;
        var eco = configManager.get("economy.yml");
        PlayerProfile loaded = storage.players().loadOrCreate(
                uuid,
                name,
                eco.getLong("currencies.COINS.starting-balance", 1500L),
                eco.getLong("currencies.RILLIKS.starting-balance", 0L),
                eco.getLong("currencies.TOKENS.starting-balance", 0L)
        );
        profiles.put(uuid, loaded);
        return loaded;
    }

    public PlayerProfile getProfile(Player player) {
        return getOrLoadProfile(player.getUniqueId(), player.getName());
    }

    public Optional<PlayerProfile> findProfile(UUID uuid) {
        PlayerProfile mem = profiles.get(uuid);
        return mem != null ? Optional.of(mem) : storage.players().findByUuid(uuid);
    }

    public Optional<PlayerProfile> findProfileByName(String username) {
        for (PlayerProfile p : profiles.values()) {
            if (p.username().equalsIgnoreCase(username)) {
                return Optional.of(p);
            }
        }
        return storage.players().findByUsername(username);
    }

    public RankDefinition getRank(Player player) {
        PlayerProfile p = getProfile(player);
        return configManager.getRank(p.rankId());
    }

    public void setPlayerRank(UUID targetUuid, String targetName, String rankId) {
        RankDefinition rank = configManager.getRank(rankId);
        PlayerProfile profile = getOrLoadProfile(targetUuid, targetName);
        profile.setRankId(rank.id());
        storage.runAsync(() -> storage.players().saveProfileStats(profile));
        if (Bukkit.getServer() != null) {
            Player online = Bukkit.getPlayer(targetUuid);
            if (online != null) {
                applyRankPermissions(online);
            }
        }
    }

    public void applyRankPermissions(Player player) {
        if (plugin == null) return;
        PermissionAttachment old = attachments.remove(player.getUniqueId());
        if (old != null) {
            try { player.removeAttachment(old); } catch (Exception ignored) {}
        }
        PermissionAttachment att = player.addAttachment(plugin);
        RankDefinition rank = getRank(player);
        for (String perm : rank.resolvedPermissions()) {
            att.setPermission(perm, true);
        }
        attachments.put(player.getUniqueId(), att);
        player.recalculatePermissions();
    }

    private void loadOnlinePlayerSync(Player player) {
        PlayerProfile p = getOrLoadProfile(player.getUniqueId(), player.getName());
        homesCache.put(player.getUniqueId(), new ArrayList<>(storage.players().getHomes(player.getUniqueId())));
        ignoresCache.put(player.getUniqueId(), ConcurrentHashMap.newKeySet());
        ignoresCache.get(player.getUniqueId()).addAll(storage.players().getIgnoredPlayers(player.getUniqueId()));
        cooldowns.loadPlayer(player.getUniqueId());
        applyRankPermissions(player);
        if (p.nickname() != null && !p.nickname().isBlank()) {
            player.displayName(TextUtil.mm(p.nickname()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean firstBefore = !player.hasPlayedBefore();
        loadOnlinePlayerSync(player);
        event.joinMessage(null);
        if (firstBefore && configManager.main().getBoolean("spawn.teleport-on-first-join", true)) {
            player.teleport(getSpawnLocation());
        }
        locale.sendRaw(player, "<#FBBC00>Добро пожаловать на <bold>ReallyWorld Ultimate</bold>! Откройте меню: <#FF6A00>/menu</#FF6A00>");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!event.isBedSpawn() && !event.isAnchorSpawn() && configManager.main().getBoolean("spawn.teleport-on-respawn", true)) {
            event.setRespawnLocation(getSpawnLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        event.quitMessage(null);
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        PendingTeleport pt = pendingTeleports.remove(uuid);
        if (pt != null) pt.task().cancel();
        incomingTpa.remove(uuid);
        PermissionAttachment att = attachments.remove(uuid);
        if (att != null) {
            try { player.removeAttachment(att); } catch (Exception ignored) {}
        }
        PlayerProfile p = profiles.remove(uuid);
        if (p != null) {
            p.setLastSeenAt(System.currentTimeMillis());
            storage.runAsync(() -> storage.players().saveProfileStats(p));
        }
        homesCache.remove(uuid);
        ignoresCache.remove(uuid);
        cooldowns.unloadPlayer(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        PlayerProfile p = getProfile(event.getPlayer());
        p.setBlocksMined(p.blocksMined() + 1L);
        if (questsModule != null) {
            questsModule.recordProgress(event.getPlayer(), "MINE_BLOCKS", 1L);
            String mat = event.getBlock().getType().name();
            if (mat.contains("ORE") || mat.equals("ANCIENT_DEBRIS")) {
                questsModule.recordProgress(event.getPlayer(), "MINE_ORES", 1L);
            }
        }
    }

    // Cancel pending teleport on movement
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMoveCancelTeleport(PlayerMoveEvent event) {
        if (!configManager.main().getBoolean("teleport.cancel-on-move", true)) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        PendingTeleport pending = pendingTeleports.remove(event.getPlayer().getUniqueId());
        if (pending != null) {
            pending.task().cancel();
            locale.send(event.getPlayer(), "teleport.cancelled-moved");
        }
    }

    // Formatted Russian Chat with Clan Tag, Rank/Custom Prefix, Nickname, and /ignore filtering
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChatFormat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        PlayerProfile profile = getProfile(sender);
        RankDefinition rank = configManager.getRank(profile.rankId());

        String clanPart = "";
        if (clanModule != null) {
            var clanOpt = clanModule.getPlayerClan(sender.getUniqueId());
            if (clanOpt.isPresent()) {
                clanPart = "<dark_gray>[<#FBBC00>" + clanOpt.get().tag() + "</#FBBC00>]</dark_gray> ";
            }
        }

        String prefix = (profile.customPrefix() != null && !profile.customPrefix().isBlank())
                ? profile.customPrefix() + " "
                : rank.prefix();
        String displayName = (profile.nickname() != null && !profile.nickname().isBlank())
                ? profile.nickname()
                : sender.getName();
        String plainMsg = PlainTextComponentSerializer.plainText().serialize(event.message());
        String msgColor = rank.weight() >= 40 ? "<white>" : "<gray>";

        Component formatted = TextUtil.mm(clanPart + prefix + rank.color() + displayName + " <dark_gray>»</dark_gray> " + msgColor)
                .append(Component.text(plainMsg));

        event.viewers().removeIf(aud -> {
            if (aud instanceof Player viewer) {
                Set<UUID> ignored = ignoresCache.get(viewer.getUniqueId());
                return ignored != null && ignored.contains(sender.getUniqueId());
            }
            return false;
        });
        event.renderer((source, sourceDisplayName, message, viewer) -> formatted);
    }

    // ---- TELEPORT ENGINE ----
    public Location getSpawnLocation() {
        var cfg = configManager.main();
        String wName = cfg.getString("spawn.world", "world");
        World w = Bukkit.getWorld(wName);
        if (w == null && !Bukkit.getWorlds().isEmpty()) {
            w = Bukkit.getWorlds().getFirst();
        }
        return new Location(
                w,
                cfg.getDouble("spawn.x", 0.5),
                cfg.getDouble("spawn.y", 100.0),
                cfg.getDouble("spawn.z", 0.5),
                (float) cfg.getDouble("spawn.yaw", 0.0),
                (float) cfg.getDouble("spawn.pitch", 0.0)
        );
    }

    public void setSpawnLocation(Location loc) {
        var cfg = configManager.main();
        cfg.set("spawn.world", loc.getWorld().getName());
        cfg.set("spawn.x", loc.getX());
        cfg.set("spawn.y", loc.getY());
        cfg.set("spawn.z", loc.getZ());
        cfg.set("spawn.yaw", loc.getYaw());
        cfg.set("spawn.pitch", loc.getPitch());
        try {
            cfg.save(new java.io.File(plugin.getDataFolder(), "config.yml"));
        } catch (Exception ignored) {}
    }

    public void teleportWithWarmup(Player player, Location destination, BiConsumer<Player, Boolean> afterTeleport) {
        if (combatModule != null && combatModule.isInCombat(player.getUniqueId())
                && configManager.main().getBoolean("teleport.cancel-in-combat", true)
                && !player.hasPermission("reallycore.admin")) {
            locale.send(player, "combat.blocked-command");
            return;
        }
        PendingTeleport old = pendingTeleports.remove(player.getUniqueId());
        if (old != null) old.task().cancel();

        int warmup = player.hasPermission("reallycore.staff") || getRank(player).weight() >= 80
                ? 0
                : configManager.main().getInt("teleport.warmup-seconds", 3);
        if (warmup <= 0) {
            player.teleport(destination);
            player.playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.1f);
            if (afterTeleport != null) afterTeleport.accept(player, true);
            return;
        }

        locale.send(player, "teleport.warmup-started", Map.of("seconds", String.valueOf(warmup)));
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingTeleports.remove(player.getUniqueId());
            if (!player.isOnline()) return;
            if (combatModule != null && combatModule.isInCombat(player.getUniqueId())) {
                locale.send(player, "teleport.cancelled-damage");
                return;
            }
            player.teleport(destination);
            player.playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.1f);
            if (afterTeleport != null) afterTeleport.accept(player, true);
        }, warmup * 20L);
        pendingTeleports.put(player.getUniqueId(), new PendingTeleport(player.getLocation().clone(), destination, task));
    }

    // Safe Random Teleport (/rtp and /rtpfar)
    public void randomTeleport(Player player, boolean farMode) {
        String cdKey = farMode ? "rtpfar" : "rtp";
        long rem = cooldowns.getRemainingSeconds(player.getUniqueId(), cdKey);
        if (rem > 0 && !player.hasPermission("reallycore.admin")) {
            locale.send(player, "on-cooldown", Map.of("time", TextUtil.formatDuration(rem)));
            return;
        }

        var cfg = configManager.main();
        String worldName = cfg.getString("rtp.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) world = player.getWorld();

        int minR = farMode ? cfg.getInt("rtp.far.min-radius", 3000) : cfg.getInt("rtp.normal.min-radius", 350);
        int maxR = farMode ? cfg.getInt("rtp.far.max-radius", 7500) : cfg.getInt("rtp.normal.max-radius", 2500);
        int attempts = cfg.getInt("rtp.max-attempts", 25);
        Set<String> unsafeMats = new HashSet<>(cfg.getStringList("rtp.unsafe-blocks"));

        Location found = null;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < attempts; i++) {
            double angle = rng.nextDouble(0, Math.PI * 2);
            double dist = rng.nextDouble(minR, maxR);
            int x = (int) Math.round(Math.cos(angle) * dist);
            int z = (int) Math.round(Math.sin(angle) * dist);
            if (!world.getWorldBorder().isInside(new Location(world, x + 0.5, 64, z + 0.5))) {
                continue;
            }
            Block highest = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Material ground = highest.getType();
            if (ground.isAir() || ground == Material.WATER || ground == Material.LAVA || unsafeMats.contains(ground.name())) {
                continue;
            }
            Block head1 = highest.getRelative(0, 1, 0);
            Block head2 = highest.getRelative(0, 2, 0);
            if (!head1.isPassable() || !head2.isPassable() || head1.isLiquid() || head2.isLiquid()) {
                continue;
            }
            found = new Location(world, x + 0.5, highest.getY() + 1.0, z + 0.5, player.getLocation().getYaw(), 0f);
            break;
        }

        if (found == null) {
            locale.send(player, "teleport.rtp-failed");
            return;
        }

        int cdSec = farMode ? cfg.getInt("rtp.far.cooldown-seconds", 90) : cfg.getInt("rtp.normal.cooldown-seconds", 45);
        Location dest = found;
        teleportWithWarmup(player, dest, (p, ok) -> {
            if (ok) {
                cooldowns.setCooldown(p.getUniqueId(), cdKey, cdSec, false);
                locale.send(p, "teleport.rtp-success", Map.of(
                        "x", String.valueOf(dest.getBlockX()),
                        "y", String.valueOf(dest.getBlockY()),
                        "z", String.valueOf(dest.getBlockZ())
                ));
            }
        });
    }

    // ---- HOMES ----
    public List<HomePoint> getHomes(UUID playerUuid) {
        return homesCache.getOrDefault(playerUuid, List.of());
    }

    public boolean setHome(Player player, String rawName) {
        String name = TextUtil.sanitizeInput(rawName, 16).toLowerCase(Locale.ROOT);
        if (name.isBlank()) name = "home";
        List<HomePoint> current = homesCache.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
        boolean replacing = false;
        for (HomePoint hp : current) {
            if (hp.name().equalsIgnoreCase(name)) {
                replacing = true;
                break;
            }
        }
        int maxHomes = getRank(player).maxHomes();
        if (!replacing && current.size() >= maxHomes) {
            locale.send(player, "teleport.home-limit-reached", Map.of("max", String.valueOf(maxHomes)));
            return false;
        }
        Location l = player.getLocation();
        HomePoint point = new HomePoint(
                player.getUniqueId(), name, l.getWorld().getName(),
                l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch(), System.currentTimeMillis()
        );
        String finalName = name;
        current.removeIf(h -> h.name().equalsIgnoreCase(finalName));
        current.add(point);
        storage.runAsync(() -> storage.players().upsertHome(point));
        locale.send(player, "teleport.home-set", Map.of("name", name));
        return true;
    }

    public boolean deleteHome(Player player, String name) {
        List<HomePoint> current = homesCache.get(player.getUniqueId());
        if (current == null || !current.removeIf(h -> h.name().equalsIgnoreCase(name))) {
            locale.send(player, "teleport.home-not-found", Map.of("name", name));
            return false;
        }
        storage.runAsync(() -> storage.players().deleteHome(player.getUniqueId(), name));
        locale.send(player, "teleport.home-deleted", Map.of("name", name));
        return true;
    }

    // ---- TPA ----
    public void sendTpa(Player sender, Player target, boolean tpaHere) {
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            locale.send(sender, "teleport.tpa-self");
            return;
        }
        PlayerProfile targetProfile = getProfile(target);
        Set<UUID> targetIgnores = ignoresCache.getOrDefault(target.getUniqueId(), Set.of());
        if (!targetProfile.tpToggle() || targetIgnores.contains(sender.getUniqueId())) {
            locale.send(sender, "teleport.tpa-disabled");
            return;
        }
        int expireSec = configManager.main().getInt("teleport.tpa-expire-seconds", 60);
        incomingTpa.put(target.getUniqueId(), new TpaRequest(
                sender.getUniqueId(), sender.getName(), target.getUniqueId(), tpaHere,
                System.currentTimeMillis() + (expireSec * 1000L)
        ));
        locale.send(sender, "teleport.tpa-sent", Map.of("player", target.getName()));
        locale.send(target, tpaHere ? "teleport.tpahere-received" : "teleport.tpa-received",
                Map.of("player", sender.getName()));
    }

    public void respondTpa(Player target, boolean accept) {
        TpaRequest req = incomingTpa.remove(target.getUniqueId());
        if (req == null || System.currentTimeMillis() > req.expiresAt()) {
            locale.send(target, "teleport.tpa-none");
            return;
        }
        Player sender = Bukkit.getPlayer(req.senderUuid());
        if (!accept) {
            locale.send(target, "teleport.tpa-denied");
            if (sender != null) locale.send(sender, "teleport.tpa-denied");
            return;
        }
        if (sender == null || !sender.isOnline()) {
            locale.send(target, "player-not-found", Map.of("player", req.senderName()));
            return;
        }
        locale.send(target, "teleport.tpa-accepted");
        locale.send(sender, "teleport.tpa-accepted");
        if (req.tpaHere()) {
            teleportWithWarmup(target, sender.getLocation(), null);
        } else {
            teleportWithWarmup(sender, target.getLocation(), null);
        }
    }

    // ---- WARPS ----
    public Collection<WarpPoint> getWarps() {
        return warpsCache.values();
    }

    public Optional<WarpPoint> getWarp(String name) {
        return Optional.ofNullable(warpsCache.get(name.toLowerCase(Locale.ROOT)));
    }

    public void setWarp(String name, String display, String category, Location loc, String actor) {
        WarpPoint wp = new WarpPoint(
                name.toLowerCase(Locale.ROOT), display, category, null,
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch(), actor, System.currentTimeMillis()
        );
        warpsCache.put(wp.name(), wp);
        storage.runAsync(() -> storage.players().upsertWarp(wp));
    }

    public boolean deleteWarp(String name) {
        WarpPoint removed = warpsCache.remove(name.toLowerCase(Locale.ROOT));
        if (removed == null) return false;
        storage.runAsync(() -> storage.players().deleteWarp(name));
        return true;
    }

    public boolean toggleIgnore(Player actor, UUID ignoredUuid) {
        Set<UUID> set = ignoresCache.computeIfAbsent(actor.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
        boolean nowIgnored;
        if (set.contains(ignoredUuid)) {
            set.remove(ignoredUuid);
            nowIgnored = false;
        } else {
            set.add(ignoredUuid);
            nowIgnored = true;
        }
        storage.runAsync(() -> storage.players().toggleIgnore(actor.getUniqueId(), ignoredUuid));
        return nowIgnored;
    }
}
