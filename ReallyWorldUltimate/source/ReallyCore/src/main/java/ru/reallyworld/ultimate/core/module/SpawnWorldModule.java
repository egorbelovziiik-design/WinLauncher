package ru.reallyworld.ultimate.core.module;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import ru.reallyworld.ultimate.core.api.ReallyModule;
import ru.reallyworld.ultimate.core.config.ConfigManager;
import ru.reallyworld.ultimate.core.gui.MenuModule;
import ru.reallyworld.ultimate.core.storage.model.ClanData;
import ru.reallyworld.ultimate.core.storage.repository.PlayerRepository.LeaderboardEntry;
import ru.reallyworld.ultimate.core.util.TextUtil;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Modern ReallyWorld Spawn Architect, Spawn Safezone Protection,
 * Auto-Mine (/warp mine), 8 Interactive NPCs, and 7 Native 1.21 TextDisplay Holograms.
 */
public final class SpawnWorldModule implements ReallyModule, Listener {
    private static final int SPAWN_PROTECTION_RADIUS = 72;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final LocalizationModule locale;
    private final PlayerModule playerModule;
    private final StatisticsModule statisticsModule;

    private final NamespacedKey npcActionKey;
    private final NamespacedKey holoIdKey;
    private final List<Entity> managedEntities = new ArrayList<>();

    private EventsModule eventsModule;
    private MenuModule menuModule;
    private BukkitTask hologramRefreshTask;
    private BukkitTask autoMineTask;
    private volatile boolean enabled;

    public SpawnWorldModule(
            JavaPlugin plugin,
            ConfigManager configManager,
            LocalizationModule locale,
            PlayerModule playerModule,
            StatisticsModule statisticsModule
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.locale = locale;
        this.playerModule = playerModule;
        this.statisticsModule = statisticsModule;
        this.npcActionKey = plugin != null ? new NamespacedKey(plugin, "rw_npc_action") : new NamespacedKey("reallycore", "rw_npc_action");
        this.holoIdKey = plugin != null ? new NamespacedKey(plugin, "rw_holo_id") : new NamespacedKey("reallycore", "rw_holo_id");
    }

    public void bindModules(EventsModule eventsModule, MenuModule menuModule) {
        this.eventsModule = eventsModule;
        this.menuModule = menuModule;
    }

    @Override
    public String id() { return "spawn-world"; }

    @Override
    public String displayName() { return "SpawnWorldModule (Spawn, NPCs, TextDisplay Holograms, Auto-Mine)"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void enable() {
        this.enabled = true;
        if (plugin != null && Bukkit.getServer() != null) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                ensureSpawnPlazaBuilt();
                respawnNpcsAndHolograms();
            }, 40L);
            this.hologramRefreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateDynamicHolograms, 200L, 200L);
            this.autoMineTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> refillAutoMine(false), 20L * 600L, 20L * 600L);
        }
    }

    @Override
    public void reload() {
        if (plugin != null && Bukkit.getServer() != null) {
            respawnNpcsAndHolograms();
        }
    }

    @Override
    public void disable() {
        this.enabled = false;
        if (hologramRefreshTask != null) hologramRefreshTask.cancel();
        if (autoMineTask != null) autoMineTask.cancel();
        removeManagedEntities();
    }

    private World getSpawnWorld() {
        String name = configManager.main().getString("spawn.world", "world");
        World w = Bukkit.getWorld(name);
        return (w != null || Bukkit.getWorlds().isEmpty()) ? w : Bukkit.getWorlds().getFirst();
    }

    // ---- SPAWN PROTECTION & PVP / MINE ZONES ----
    public boolean isInsideSpawnRegion(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        World sw = getSpawnWorld();
        if (sw == null || !loc.getWorld().equals(sw)) return false;
        return Math.abs(loc.getBlockX()) <= SPAWN_PROTECTION_RADIUS
                && Math.abs(loc.getBlockZ()) <= SPAWN_PROTECTION_RADIUS;
    }

    public boolean isInsideColiseumPit(Location loc) {
        if (!isInsideSpawnRegion(loc)) return false;
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= -14 && x <= 14 && z >= 34 && z <= 56 && y <= 98;
    }

    public boolean isInsideAutoMinePit(Location loc) {
        if (!isInsideSpawnRegion(loc)) return false;
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= -8 && x <= 8 && z >= -46 && z <= -34 && y >= 93 && y <= 99;
    }

    /**
     * Returns true if location is inside peaceful Spawn (NOT inside Coliseum PvP pit).
     */
    public boolean isProtectedSpawn(Location loc) {
        return isInsideSpawnRegion(loc) && !isInsideColiseumPit(loc);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpawnBlockBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        if (!isInsideSpawnRegion(loc)) return;
        if (isInsideAutoMinePit(loc)) return; // Players CAN mine ores in Auto-Mine!
        if (!event.getPlayer().hasPermission("reallycore.admin")) {
            event.setCancelled(true);
            locale.sendRaw(event.getPlayer(), "<red>Ломать блоки на Спавне запрещено! Шахта ресурсов: <gold>/warp mine</gold>.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpawnBlockPlace(BlockPlaceEvent event) {
        if (isInsideSpawnRegion(event.getBlock().getLocation()) && !event.getPlayer().hasPermission("reallycore.admin")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpawnExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> isInsideSpawnRegion(b.getLocation()));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpawnFire(BlockIgniteEvent event) {
        if (isInsideSpawnRegion(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpawnFallOrMobDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && isProtectedSpawn(p.getLocation())) {
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL
                    || event.getCause() == EntityDamageEvent.DamageCause.STARVATION
                    || event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                    || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
                event.setCancelled(true);
            }
        }
        if (event.getEntity().getPersistentDataContainer().has(npcActionKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onNaturalMobSpawnInPlaza(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL
                && isInsideSpawnRegion(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    // ---- PROCEDURAL SPAWN ARCHITECT (Plaza + Trade Wing + Crate Hall + Auto-Mine + Coliseum) ----
    public void ensureSpawnPlazaBuilt() {
        World w = getSpawnWorld();
        if (w == null) return;
        Block marker = w.getBlockAt(0, 99, 0);
        if (marker.getType() == Material.GOLD_BLOCK) {
            return; // Already built!
        }
        buildSpawnStructure(w);
    }

    public void buildSpawnStructure(World w) {
        // Clear airspace above plaza
        for (int x = -36; x <= 36; x++) {
            for (int z = -50; z <= 60; z++) {
                for (int y = 100; y <= 114; y++) {
                    w.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }

        // Main Grand Octagonal Polished Blackstone & Deepslate Tile Plaza (Y=99) with Gold/Lantern inlays
        for (int x = -32; x <= 32; x++) {
            for (int z = -34; z <= 34; z++) {
                if (Math.abs(x) + Math.abs(z) > 50) continue;
                Material floor = ((Math.abs(x) + Math.abs(z)) % 7 == 0)
                        ? Material.SHROOMLIGHT
                        : ((Math.abs(x) == Math.abs(z) || x == 0 || z == 0) ? Material.POLISHED_BLACKSTONE_BRICKS : Material.DEEPSLATE_TILES);
                w.getBlockAt(x, 99, z).setType(floor, false);
                w.getBlockAt(x, 98, z).setType(Material.BEDROCK, false);
            }
        }
        w.getBlockAt(0, 99, 0).setType(Material.GOLD_BLOCK, false);

        // Decorative Pillars with Soul/Fire Lanterns
        int[][] pillars = {{-12, -12}, {12, -12}, {-12, 12}, {12, 12}, {-24, 0}, {24, 0}};
        for (int[] p : pillars) {
            for (int y = 100; y <= 105; y++) {
                w.getBlockAt(p[0], y, p[1]).setType(Material.POLISHED_BASALT, false);
            }
            w.getBlockAt(p[0], 106, p[1]).setType(Material.CRYING_OBSIDIAN, false);
            w.getBlockAt(p[0], 107, p[1]).setType(Material.LANTERN, false);
        }

        // Crate Pedestal in Crate Wing (+24, 100, 0)
        w.getBlockAt(24, 100, 0).setType(Material.ENDER_CHEST, false);
        // Enchanting & Anvil Station (-16, 100, -16)
        w.getBlockAt(-16, 100, -16).setType(Material.ENCHANTING_TABLE, false);
        w.getBlockAt(-15, 100, -16).setType(Material.ANVIL, false);

        // North Auto-Mine Pit (X: -9..9, Z: -47..-33, Y: 92..99)
        for (int x = -9; x <= 9; x++) {
            for (int z = -47; z <= -33; z++) {
                for (int y = 92; y <= 99; y++) {
                    boolean wall = x == -9 || x == 9 || z == -47 || z == -33 || y == 92;
                    w.getBlockAt(x, y, z).setType(wall ? Material.POLISHED_DEEPSLATE : Material.STONE, false);
                }
            }
        }
        refillAutoMine(false);

        // South PvP Coliseum Pit (X: -15..15, Z: 33..57, Y: 94..99)
        for (int x = -15; x <= 15; x++) {
            for (int z = 33; z <= 57; z++) {
                boolean wall = x == -15 || x == 15 || z == 33 || z == 57;
                w.getBlockAt(x, 94, z).setType(Material.SMOOTH_SANDSTONE, false);
                for (int y = 95; y <= 99; y++) {
                    w.getBlockAt(x, y, z).setType(wall ? Material.POLISHED_BLACKSTONE_BRICKS : Material.AIR, false);
                }
            }
        }
        w.setSpawnLocation(0, 100, 0);
    }

    public void refillAutoMine(boolean epicEventTier) {
        World w = getSpawnWorld();
        if (w == null) return;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int x = -8; x <= 8; x++) {
            for (int z = -46; z <= -34; z++) {
                for (int y = 93; y <= 99; y++) {
                    int roll = rng.nextInt(100);
                    Material m;
                    if (epicEventTier) {
                        if (roll < 3) m = Material.ANCIENT_DEBRIS;
                        else if (roll < 20) m = Material.DIAMOND_ORE;
                        else if (roll < 40) m = Material.EMERALD_ORE;
                        else if (roll < 65) m = Material.GOLD_ORE;
                        else m = Material.IRON_ORE;
                    } else {
                        if (roll < 5) m = Material.DIAMOND_ORE;
                        else if (roll < 12) m = Material.EMERALD_ORE;
                        else if (roll < 25) m = Material.GOLD_ORE;
                        else if (roll < 45) m = Material.IRON_ORE;
                        else if (roll < 65) m = Material.COAL_ORE;
                        else m = Material.STONE;
                    }
                    w.getBlockAt(x, y, z).setType(m, false);
                }
            }
        }
    }

    // ---- 8 INTERACTIVE NPCs & 7 TEXTDISPLAY HOLOGRAMS ----
    public void respawnNpcsAndHolograms() {
        removeManagedEntities();
        World w = getSpawnWorld();
        if (w == null) return;

        // Clean up any leftover tagged NPCs/Holograms in Spawn chunks from prior restarts
        for (Entity e : w.getEntities()) {
            if (e.getPersistentDataContainer().has(npcActionKey, PersistentDataType.STRING)
                    || e.getPersistentDataContainer().has(holoIdKey, PersistentDataType.STRING)) {
                e.remove();
            }
        }

        var cfg = configManager.get("spawn_npcs_holograms.yml");
        ConfigurationSection npcs = cfg.getConfigurationSection("npcs");
        if (npcs != null) {
            for (String key : npcs.getKeys(false)) {
                ConfigurationSection n = npcs.getConfigurationSection(key);
                Location loc = new Location(w, n.getDouble("x"), n.getDouble("y"), n.getDouble("z"),
                        (float) n.getDouble("yaw"), (float) n.getDouble("pitch"));
                loc.getChunk().load();
                Villager npc = (Villager) w.spawnEntity(loc, EntityType.VILLAGER);
                npc.setAI(false);
                npc.setInvulnerable(true);
                npc.setSilent(true);
                npc.setCollidable(false);
                npc.setPersistent(true);
                npc.setRemoveWhenFarAway(false);
                npc.customName(TextUtil.mm(n.getString("title", key)));
                npc.setCustomNameVisible(true);
                try {
                    Villager.Profession prof = Registry.VILLAGER_PROFESSION.get(
                            NamespacedKey.minecraft(n.getString("profession", "nitwit").toLowerCase(Locale.ROOT)));
                    if (prof != null) npc.setProfession(prof);
                } catch (Throwable ignored) {}
                npc.getPersistentDataContainer().set(npcActionKey, PersistentDataType.STRING, n.getString("action", "OPEN_GUI:MAIN"));
                managedEntities.add(npc);

                // Floating subtitle TextDisplay above NPC
                TextDisplay sub = (TextDisplay) w.spawnEntity(loc.clone().add(0, 2.35, 0), EntityType.TEXT_DISPLAY);
                sub.setBillboard(Display.Billboard.CENTER);
                sub.setSeeThrough(false);
                sub.text(TextUtil.mm(n.getString("subtitle", "<yellow>Нажмите ПКМ")));
                sub.getPersistentDataContainer().set(holoIdKey, PersistentDataType.STRING, "npc_sub_" + key);
                managedEntities.add(sub);
            }
        }

        ConfigurationSection holos = cfg.getConfigurationSection("holograms");
        if (holos != null) {
            for (String key : holos.getKeys(false)) {
                ConfigurationSection h = holos.getConfigurationSection(key);
                Location loc = new Location(w, h.getDouble("x"), h.getDouble("y"), h.getDouble("z"));
                loc.getChunk().load();
                TextDisplay td = (TextDisplay) w.spawnEntity(loc, EntityType.TEXT_DISPLAY);
                td.setBillboard(Display.Billboard.CENTER);
                td.setAlignment(TextDisplay.TextAlignment.CENTER);
                td.setBackgroundColor(Color.fromARGB(130, 15, 15, 20));
                td.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 0, 1),
                        new Vector3f(1.15f, 1.15f, 1.15f),
                        new AxisAngle4f(0, 0, 0, 1)
                ));
                td.getPersistentDataContainer().set(holoIdKey, PersistentDataType.STRING, key);
                managedEntities.add(td);
            }
        }
        updateDynamicHolograms();
    }

    public void updateDynamicHolograms() {
        var cfg = configManager.get("spawn_npcs_holograms.yml");
        for (Entity e : managedEntities) {
            if (!(e instanceof TextDisplay td) || !td.isValid()) continue;
            String hId = td.getPersistentDataContainer().get(holoIdKey, PersistentDataType.STRING);
            if (hId == null || hId.startsWith("npc_sub_")) continue;

            if (cfg.contains("holograms." + hId + ".dynamic-board")) {
                String board = cfg.getString("holograms." + hId + ".dynamic-board", "COINS");
                String header = cfg.getString("holograms." + hId + ".header", "");
                td.text(TextUtil.mm(buildLeaderboardHoloText(header, board)));
            } else {
                List<String> lines = cfg.getStringList("holograms." + hId + ".lines");
                String joined = String.join("\n", lines)
                        .replace("<event_status>", eventsModule != null ? eventsModule.getStatusSummary() : "Ожидание")
                        .replace("<event_timer>", eventsModule != null ? TextUtil.formatDuration(eventsModule.getSecondsUntilNextEvent()) : "—");
                td.text(TextUtil.mm(joined));
            }
        }
    }

    private String buildLeaderboardHoloText(String header, String board) {
        StringBuilder sb = new StringBuilder(header).append("\n");
        if ("CLANS".equalsIgnoreCase(board)) {
            List<ClanData> clans = statisticsModule.getTopClans();
            if (clans.isEmpty()) sb.append("<gray>Пока нет созданных кланов");
            for (int i = 0; i < Math.min(5, clans.size()); i++) {
                ClanData c = clans.get(i);
                sb.append("<#FBBC00>#").append(i + 1).append(" <white>[").append(c.tag()).append("] ")
                        .append(c.name()).append(" <dark_gray>— <#FF6A00>").append(c.ratingPoints()).append(" очк.\n");
            }
        } else {
            String col = "KILLS".equalsIgnoreCase(board) ? "kills" : "coins";
            String unit = "KILLS".equalsIgnoreCase(board) ? " уб." : " ⛃";
            List<LeaderboardEntry> list = statisticsModule.getTopPlayers(col);
            if (list.isEmpty()) sb.append("<gray>Данные обновляются...");
            for (int i = 0; i < Math.min(5, list.size()); i++) {
                LeaderboardEntry row = list.get(i);
                sb.append("<#FBBC00>#").append(i + 1).append(" <white>").append(row.username())
                        .append(" <dark_gray>— <#FF6A00>").append(TextUtil.formatAmount(row.value())).append(unit).append("\n");
            }
        }
        return sb.toString().trim();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onNpcClick(PlayerInteractEntityEvent event) {
        Entity rightClicked = event.getRightClicked();
        String action = rightClicked.getPersistentDataContainer().get(npcActionKey, PersistentDataType.STRING);
        if (action == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (action.startsWith("OPEN_GUI:") && menuModule != null) {
            menuModule.openNamedMenu(player, action.substring("OPEN_GUI:".length()));
        } else if ("COMMAND:rtp".equalsIgnoreCase(action)) {
            playerModule.randomTeleport(player, false);
        }
    }

    private void removeManagedEntities() {
        for (Entity e : managedEntities) {
            if (e != null && e.isValid()) e.remove();
        }
        managedEntities.clear();
    }
}
