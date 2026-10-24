package ru.reallyworld.ultimate.core.module;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import ru.reallyworld.ultimate.core.api.CurrencyType;
import ru.reallyworld.ultimate.core.api.ReallyModule;
import ru.reallyworld.ultimate.core.config.ConfigManager;
import ru.reallyworld.ultimate.core.storage.StorageModule;
import ru.reallyworld.ultimate.core.storage.model.ClanData;
import ru.reallyworld.ultimate.core.storage.model.PlayerProfile;
import ru.reallyworld.ultimate.core.util.TextUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Conflict-Free World Event Scheduler (10 Events) & 5 Multi-Phase Boss Engine.
 * Respects protected Spawn regions, tracks per-player boss damage shares,
 * and cleans up all spawned entities, chests, craters, and BossBars on stop/reload.
 */
public final class EventsModule implements ReallyModule, Listener {
    public static final class ActiveWorldEvent {
        public final String id;
        public final String title;
        public final Location location;
        public final long startedAt;
        public final long unlockAt;
        public final long endsAt;
        public final BossBar bossBar;
        public boolean unlocked;
        public UUID bountyTarget;
        public final Map<Long, Integer> clanCaptureTicks = new ConcurrentHashMap<>();
        public final List<Location> modifiedBlocks = new ArrayList<>();

        public ActiveWorldEvent(String id, String title, Location location, long startedAt, long unlockAt, long endsAt, BossBar bossBar) {
            this.id = id;
            this.title = title;
            this.location = location;
            this.startedAt = startedAt;
            this.unlockAt = unlockAt;
            this.endsAt = endsAt;
            this.bossBar = bossBar;
        }
    }

    public static final class ActiveBoss {
        public final String bossId;
        public final String displayName;
        public final LivingEntity entity;
        public final double maxHp;
        public final BossBar bossBar;
        public int phase = 1;
        public final Map<UUID, Double> damageMap = new ConcurrentHashMap<>();
        public final List<Entity> minions = new ArrayList<>();
        public final long despawnAt;

        public ActiveBoss(String bossId, String displayName, LivingEntity entity, double maxHp, BossBar bossBar, long despawnAt) {
            this.bossId = bossId;
            this.displayName = displayName;
            this.entity = entity;
            this.maxHp = maxHp;
            this.bossBar = bossBar;
            this.despawnAt = despawnAt;
        }
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final StorageModule storage;
    private final LocalizationModule locale;
    private final PlayerModule playerModule;
    private final EconomyModule economyModule;
    private final ClanModule clanModule;
    private final CustomItemsModule customItems;
    private final CasesModule casesModule;
    private final KitsModule kitsModule;
    private final QuestsModule questsModule;
    private SpawnWorldModule spawnWorldModule;

    private final NamespacedKey bossKey;
    private volatile ActiveWorldEvent activeEvent;
    private volatile ActiveBoss activeBoss;
    private volatile long nextScheduledEventAt;
    private int rotationCursor = 0;
    private BukkitTask mainLoopTask;
    private volatile boolean enabled;

    public EventsModule(
            JavaPlugin plugin,
            ConfigManager configManager,
            StorageModule storage,
            LocalizationModule locale,
            PlayerModule playerModule,
            EconomyModule economyModule,
            ClanModule clanModule,
            CustomItemsModule customItems,
            CasesModule casesModule,
            KitsModule kitsModule,
            QuestsModule questsModule
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.storage = storage;
        this.locale = locale;
        this.playerModule = playerModule;
        this.economyModule = economyModule;
        this.clanModule = clanModule;
        this.customItems = customItems;
        this.casesModule = casesModule;
        this.kitsModule = kitsModule;
        this.questsModule = questsModule;
        this.bossKey = plugin != null ? new NamespacedKey(plugin, "rw_boss_id") : new NamespacedKey("reallycore", "rw_boss_id");
    }

    public void bindSpawnModule(SpawnWorldModule spawnWorldModule) {
        this.spawnWorldModule = spawnWorldModule;
    }

    @Override
    public String id() { return "events"; }

    @Override
    public String displayName() { return "EventsModule (10 World Events + 5 Multi-Phase Bosses)"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void enable() {
        this.enabled = configManager.main().getBoolean("modules.events", true);
        scheduleNextTimer();
        if (plugin != null && Bukkit.getServer() != null) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            this.mainLoopTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickSchedulerAndBosses, 20L, 20L);
        }
    }

    @Override
    public void reload() {
        this.enabled = configManager.main().getBoolean("modules.events", true);
    }

    @Override
    public void disable() {
        this.enabled = false;
        if (mainLoopTask != null) mainLoopTask.cancel();
        stopActiveEvent(false);
        despawnActiveBoss();
    }

    private void scheduleNextTimer() {
        long intervalSec = Math.max(120L, configManager.get("events.yml").getLong("scheduler.auto-event-interval-minutes", 30L) * 60L);
        this.nextScheduledEventAt = System.currentTimeMillis() + (intervalSec * 1000L);
    }

    public String getStatusSummary() {
        if (activeBoss != null) {
            return TextUtil.plain(TextUtil.mm(activeBoss.displayName));
        }
        if (activeEvent != null) {
            return TextUtil.plain(TextUtil.mm(activeEvent.title));
        }
        long sec = Math.max(0L, (nextScheduledEventAt - System.currentTimeMillis()) / 1000L);
        return "Через " + TextUtil.formatDuration(sec);
    }

    public ActiveWorldEvent getActiveEvent() { return activeEvent; }
    public ActiveBoss getActiveBoss() { return activeBoss; }
    public long getSecondsUntilNextEvent() {
        return Math.max(0L, (nextScheduledEventAt - System.currentTimeMillis()) / 1000L);
    }

    private void tickSchedulerAndBosses() {
        if (!enabled) return;
        long now = System.currentTimeMillis();

        // 1. Auto-start next scheduled event when idle and enough players online
        if (activeEvent == null && activeBoss == null && now >= nextScheduledEventAt) {
            int minOnline = configManager.get("events.yml").getInt("scheduler.min-online-players", 1);
            if (Bukkit.getOnlinePlayers().size() >= minOnline) {
                List<String> keys = new ArrayList<>(Objects.requireNonNull(
                        configManager.get("events.yml").getConfigurationSection("events")).getKeys(false));
                if (!keys.isEmpty()) {
                    String chosen = keys.get(rotationCursor % keys.size());
                    rotationCursor++;
                    startWorldEvent(chosen);
                }
            }
            scheduleNextTimer();
        }

        // 2. Tick active World Event
        if (activeEvent != null) {
            tickWorldEvent(activeEvent, now);
        }

        // 3. Tick active Boss
        if (activeBoss != null) {
            tickBoss(activeBoss, now);
        }
    }

    // ---- WORLD EVENTS ----
    public boolean startWorldEvent(String eventId) {
        if (activeEvent != null) {
            stopActiveEvent(false);
        }
        if ("boss".equalsIgnoreCase(eventId) || configManager.get("bosses.yml").contains("bosses." + eventId)) {
            String bId = "boss".equalsIgnoreCase(eventId) ? "ancient_guardian" : eventId;
            return spawnBoss(bId, findSafeWildLocation("world", 450, 1200));
        }
        ConfigurationSection sec = configManager.get("events.yml").getConfigurationSection("events." + eventId);
        if (sec == null) return false;

        if ("rare_resources".equalsIgnoreCase(eventId) && spawnWorldModule != null) {
            spawnWorldModule.refillAutoMine(true);
            Bukkit.getServer().sendMessage(TextUtil.mm(locale.prefix() + "<#FBBC00>Ивент «Редкие Ресурсы»: Автошахта (<gold>/warp mine</gold>) заполнена алмазами, изумрудами и обломками незерита!"));
            return true;
        }

        String worldName = switch (eventId) {
            case "nether_invasion" -> "world_nether";
            case "end_rift" -> "world_the_end";
            default -> configManager.main().getString("spawn.world", "world");
        };
        Location loc = "pvp_arena".equalsIgnoreCase(eventId)
                ? new Location(Bukkit.getWorld(worldName), 0.5, 95.0, 42.5)
                : findSafeWildLocation(worldName, 380, 1800);
        if (loc == null || loc.getWorld() == null) return false;

        long now = System.currentTimeMillis();
        long unlockDelayMs = sec.getLong("cooling-seconds", 60L) * 1000L;
        long durationMs = sec.getLong("duration-seconds", 420L) * 1000L;
        String title = sec.getString("title", eventId);

        BossBar bar = BossBar.bossBar(
                TextUtil.mm(title + " <gray>[ X:" + loc.getBlockX() + " Z:" + loc.getBlockZ() + " ]"),
                1.0f,
                BossBar.Color.YELLOW,
                BossBar.Overlay.NOTCHED_10
        );
        for (Player p : Bukkit.getOnlinePlayers()) p.showBossBar(bar);

        ActiveWorldEvent ev = new ActiveWorldEvent(eventId, title, loc, now, now + unlockDelayMs, now + durationMs, bar);

        // Build physical event structure safely outside protected Spawn
        if ("airdrop".equalsIgnoreCase(eventId) || "treasure_chest".equalsIgnoreCase(eventId) || "altar".equalsIgnoreCase(eventId)) {
            Block chestBlock = loc.getBlock();
            chestBlock.setType(Material.RESPAWN_ANCHOR.equals(chestBlock.getType()) ? Material.CHEST : Material.CHEST, false);
            ev.modifiedBlocks.add(chestBlock.getLocation());
            ev.unlocked = ("treasure_chest".equalsIgnoreCase(eventId));
            if (ev.unlocked) populateEventChest(chestBlock);
        } else if ("meteorite".equalsIgnoreCase(eventId)) {
            buildMeteorCrater(ev);
        } else if ("bounty_hunt".equalsIgnoreCase(eventId) && !Bukkit.getOnlinePlayers().isEmpty()) {
            List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
            Player hunted = online.get(ThreadLocalRandom.current().nextInt(online.size()));
            ev.bountyTarget = hunted.getUniqueId();
            hunted.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, (int) (durationMs / 50L), 0));
        }

        this.activeEvent = ev;
        Bukkit.getServer().sendMessage(locale.component("events.started", Map.of(
                "name", title,
                "x", String.valueOf(loc.getBlockX()),
                "y", String.valueOf(loc.getBlockY()),
                "z", String.valueOf(loc.getBlockZ())
        )));
        return true;
    }

    private void tickWorldEvent(ActiveWorldEvent ev, long now) {
        if (now >= ev.endsAt) {
            stopActiveEvent(true);
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(ev.bossBar);
        }
        long totalMs = Math.max(1L, ev.endsAt - ev.startedAt);
        ev.bossBar.progress(Math.max(0f, Math.min(1f, (float) (ev.endsAt - now) / totalMs)));

        if (!ev.unlocked && now >= ev.unlockAt) {
            ev.unlocked = true;
            populateEventChest(ev.location.getBlock());
            ev.location.getWorld().strikeLightningEffect(ev.location);
            Bukkit.getServer().sendMessage(locale.component("events.airdrop-unlocked", Map.of(
                    "x", String.valueOf(ev.location.getBlockX()),
                    "y", String.valueOf(ev.location.getBlockY()),
                    "z", String.valueOf(ev.location.getBlockZ())
            )));
        }

        // Authentic ReallyWorld "Захват Талисмана": 150-block anti-fly zone + clan point capture
        if ("talisman_capture".equalsIgnoreCase(ev.id) && ev.location.getWorld() != null) {
            for (Player p : ev.location.getWorld().getPlayers()) {
                double distSq = p.getLocation().distanceSquared(ev.location);
                if (distSq <= 150 * 150 && p.isFlying() && !p.hasPermission("reallycore.admin")) {
                    p.setFlying(false);
                }
                if (distSq <= 14 * 14) {
                    clanModule.getPlayerClan(p.getUniqueId()).ifPresent(clan -> {
                        int ticks = ev.clanCaptureTicks.merge(clan.id(), 1, Integer::sum);
                        if (ticks >= 120) { // Held for 120 player-seconds -> victory!
                            rewardCapturingClan(clan);
                            stopActiveEvent(false);
                        }
                    });
                }
            }
        }
        ev.location.getWorld().spawnParticle(Particle.FLAME, ev.location.clone().add(0.5, 1.4, 0.5), 10, 0.4, 0.6, 0.4, 0.02);
    }

    private void rewardCapturingClan(ClanData clan) {
        int pts = configManager.get("events.yml").getInt("events.talisman_capture.clan-points-reward", 10);
        long coins = configManager.get("events.yml").getLong("events.talisman_capture.reward-coins", 50_000L);
        clanModule.addEventRating(clan.id(), pts, true);
        Player leader = Bukkit.getPlayer(clan.leaderUuid());
        if (leader != null) {
            economyModule.deposit(leader.getUniqueId(), CurrencyType.COINS, coins, "EVENT_CAPTURE", "Talisman Capture");
            customItems.createCustomItem("TALISMAN", "tyrant", 1)
                    .ifPresent(st -> leader.getInventory().addItem(st));
        }
        Bukkit.getServer().sendMessage(TextUtil.mm(locale.prefix() + "<#FBBC00>Клан <white>[" + clan.tag() + "] "
                + clan.name() + "</white> захватил точку и получил <gold>+" + pts + " очков рейтинга</gold> и Талисман Тирана!"));
    }

    private void buildMeteorCrater(ActiveWorldEvent ev) {
        World w = ev.location.getWorld();
        if (w == null) return;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz <= 5) {
                    Block b = w.getBlockAt(ev.location.getBlockX() + dx, ev.location.getBlockY() - 1, ev.location.getBlockZ() + dz);
                    if (spawnWorldModule != null && spawnWorldModule.isProtectedSpawn(b.getLocation())) continue;
                    b.setType((dx == 0 && dz == 0) ? Material.ANCIENT_DEBRIS : Material.MAGMA_BLOCK, false);
                    ev.modifiedBlocks.add(b.getLocation());
                }
            }
        }
        Block chest = ev.location.getBlock();
        chest.setType(Material.CHEST, false);
        ev.modifiedBlocks.add(chest.getLocation());
    }

    private void populateEventChest(Block block) {
        if (!(block.getState() instanceof Chest chest)) return;
        chest.getBlockInventory().clear();
        List<ItemStack> pool = new ArrayList<>();
        pool.add(new ItemStack(Material.TOTEM_OF_UNDYING, 1));
        pool.add(new ItemStack(Material.GOLDEN_APPLE, 12));
        pool.add(new ItemStack(Material.EXPERIENCE_BOTTLE, 32));
        pool.add(new ItemStack(Material.NETHERITE_INGOT, 2));
        customItems.createCustomItem("CONSUMABLE", "trapka", 3).ifPresent(pool::add);
        customItems.createCustomItem("CONSUMABLE", "divine_aura", 2).ifPresent(pool::add);
        customItems.createCustomItem("SPHERE", "ares", 1).ifPresent(pool::add);

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (ItemStack item : pool) {
            chest.getBlockInventory().setItem(rng.nextInt(chest.getBlockInventory().getSize()), item);
        }
    }

    public void stopActiveEvent(boolean announce) {
        ActiveWorldEvent ev = this.activeEvent;
        this.activeEvent = null;
        if (ev == null) return;
        if (Bukkit.getServer() != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(ev.bossBar);
            }
            for (Location loc : ev.modifiedBlocks) {
                if (loc.getWorld() != null) {
                    loc.getBlock().setType(Material.AIR, false);
                }
            }
            if (announce) {
                Bukkit.getServer().sendMessage(locale.component("events.ended", Map.of("name", ev.title)));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLockedEventChestInteract(PlayerInteractEvent event) {
        ActiveWorldEvent ev = this.activeEvent;
        if (ev == null || event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getLocation().equals(ev.location.getBlock().getLocation())) {
            if (!ev.unlocked) {
                event.setCancelled(true);
                long sec = Math.max(1L, (ev.unlockAt - System.currentTimeMillis()) / 1000L);
                locale.send(event.getPlayer(), "events.airdrop-locked", Map.of("seconds", String.valueOf(sec)));
            } else {
                questsModule.recordProgress(event.getPlayer(), "OPEN_AIRDROP", 1L);
            }
        }
    }

    // ---- 5 MULTI-PHASE BOSSES ----
    public boolean spawnBoss(String bossId, Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        ConfigurationSection bSec = configManager.get("bosses.yml").getConfigurationSection("bosses." + bossId);
        if (bSec == null) return false;

        despawnActiveBoss();
        EntityType type = EntityType.valueOf(bSec.getString("entity-type", "WITHER_SKELETON"));
        Entity raw = loc.getWorld().spawnEntity(loc, type);
        if (!(raw instanceof LivingEntity boss)) {
            raw.remove();
            return false;
        }

        double maxHp = bSec.getDouble("max-health", 800.0);
        Objects.requireNonNull(boss.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(maxHp);
        boss.setHealth(maxHp);
        if (boss.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
            Objects.requireNonNull(boss.getAttribute(Attribute.ATTACK_DAMAGE))
                    .setBaseValue(bSec.getDouble("attack-damage", 16.0));
        }
        String name = bSec.getString("name", bossId);
        boss.customName(TextUtil.mm(name + " <red>[" + (int) maxHp + "❤]"));
        boss.setCustomNameVisible(true);
        boss.setRemoveWhenFarAway(false);
        boss.getPersistentDataContainer().set(bossKey, PersistentDataType.STRING, bossId);

        BossBar bar = BossBar.bossBar(
                TextUtil.mm(name + " <dark_gray>• <yellow>Фаза I"),
                1.0f,
                BossBar.Color.RED,
                BossBar.Overlay.NOTCHED_20
        );
        for (Player p : Bukkit.getOnlinePlayers()) p.showBossBar(bar);

        long despawnMs = configManager.get("bosses.yml").getLong("settings.despawn-timeout-minutes", 25L) * 60_000L;
        this.activeBoss = new ActiveBoss(bossId, name, boss, maxHp, bar, System.currentTimeMillis() + despawnMs);

        Bukkit.getServer().sendMessage(locale.component("events.boss-spawned", Map.of(
                "boss", name,
                "x", String.valueOf(loc.getBlockX()),
                "y", String.valueOf(loc.getBlockY()),
                "z", String.valueOf(loc.getBlockZ())
        )));
        return true;
    }

    private void tickBoss(ActiveBoss b, long now) {
        if (b.entity.isDead() || !b.entity.isValid() || now >= b.despawnAt) {
            despawnActiveBoss();
            return;
        }
        double hp = b.entity.getHealth();
        double ratio = Math.max(0.0, Math.min(1.0, hp / b.maxHp));
        int newPhase = ratio <= 0.33 ? 3 : (ratio <= 0.66 ? 2 : 1);
        if (newPhase > b.phase) {
            b.phase = newPhase;
            b.entity.getWorld().playSound(b.entity.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.2f);
            b.entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, newPhase - 1));
            b.entity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, newPhase - 1));
        }

        String roman = switch (b.phase) { case 2 -> "II"; case 3 -> "III"; default -> "I"; };
        b.bossBar.name(TextUtil.mm(b.displayName + " <dark_gray>• <yellow>Фаза " + roman + " <red>[" + (int) hp + "❤]"));
        b.bossBar.progress((float) ratio);
        b.entity.customName(TextUtil.mm(b.displayName + " <red>[" + (int) hp + "❤]"));

        // Execute Boss skill every ~8 seconds
        if ((now / 1000L) % 8 == 0) {
            castBossSkill(b);
        }
    }

    private void castBossSkill(ActiveBoss b) {
        Location center = b.entity.getLocation();
        List<Player> targets = new ArrayList<>();
        for (Player p : center.getWorld().getPlayers()) {
            if (p.getGameMode() == GameMode.SURVIVAL && p.getLocation().distanceSquared(center) <= 18 * 18) {
                targets.add(p);
            }
        }
        if (targets.isEmpty()) return;

        List<String> skills = configManager.get("bosses.yml").getStringList("bosses." + b.bossId + ".skills");
        if (skills.isEmpty()) return;
        String chosen = skills.get(ThreadLocalRandom.current().nextInt(skills.size()));

        switch (chosen) {
            case "SLAM_WAVE", "SHIELD_SHOCKWAVE" -> {
                center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 2);
                for (Player t : targets) {
                    t.setVelocity(t.getLocation().toVector().subtract(center.toVector()).normalize().multiply(1.1).setY(0.55));
                    t.damage(4.0 * b.phase, b.entity);
                }
            }
            case "METEOR_RAIN", "FIRE_NOVA", "DRAGON_BREATH" -> {
                for (Player t : targets) {
                    t.setFireTicks(80);
                    t.damage(5.0, b.entity);
                    t.getWorld().spawnParticle(Particle.FLAME, t.getLocation().add(0, 1, 0), 25, 0.4, 0.5, 0.4, 0.05);
                }
            }
            case "GRAVITY_VORTEX", "DARK_BLINDNESS" -> {
                for (Player t : targets) {
                    t.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0));
                    Vector pull = center.toVector().subtract(t.getLocation().toVector()).normalize().multiply(0.8);
                    t.setVelocity(pull);
                }
            }
            case "THUNDER_LANCE", "LASER_PULSE" -> {
                Player t = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
                t.getWorld().strikeLightningEffect(t.getLocation());
                t.damage(6.0, b.entity);
            }
            case "SUMMON_MINIONS" -> {
                b.minions.removeIf(Entity::isDead);
                if (b.minions.size() < 4) {
                    Zombie minion = (Zombie) center.getWorld().spawnEntity(center, EntityType.ZOMBIE);
                    minion.customName(TextUtil.mm("<gray>Слуга Босса"));
                    b.minions.add(minion);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossDamaged(EntityDamageByEntityEvent event) {
        ActiveBoss b = this.activeBoss;
        if (b == null || !event.getEntity().getUniqueId().equals(b.entity.getUniqueId())) return;
        Player attacker = null;
        if (event.getDamager() instanceof Player p) attacker = p;
        else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) attacker = p;
        if (attacker != null) {
            b.damageMap.merge(attacker.getUniqueId(), event.getFinalDamage(), Double::sum);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBossDeath(EntityDeathEvent event) {
        ActiveBoss b = this.activeBoss;
        if (b == null || !event.getEntity().getUniqueId().equals(b.entity.getUniqueId())) return;
        event.getDrops().clear();

        ConfigurationSection bSec = configManager.get("bosses.yml").getConfigurationSection("bosses." + b.bossId);
        List<Map.Entry<UUID, Double>> sorted = b.damageMap.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .toList();

        double minShare = configManager.get("bosses.yml").getDouble("settings.min-damage-percent-for-participation", 3.0) / 100.0;
        String topName = "Неизвестный Герой";
        double topDamage = 0.0;

        for (int i = 0; i < sorted.size(); i++) {
            UUID u = sorted.get(i).getKey();
            double dmg = sorted.get(i).getValue();
            Player p = Bukkit.getPlayer(u);
            String pName = p != null ? p.getName() : playerModule.findProfile(u).map(PlayerProfile::username).orElse("Игрок");
            if (i == 0) {
                topName = pName;
                topDamage = dmg;
            }
            if (dmg < b.maxHp * minShare) continue;

            double rankMult = switch (i) { case 0 -> 1.0; case 1 -> 0.7; case 2 -> 0.5; default -> 0.25; };
            long coins = Math.round(bSec.getLong("rewards.top-1-coins", 35_000L) * rankMult);
            long rilliks = Math.round(bSec.getLong("rewards.top-1-rilliks", 40L) * rankMult);
            economyModule.deposit(u, CurrencyType.COINS, coins, "BOSS_REWARD", b.bossId);
            if (rilliks > 0) economyModule.deposit(u, CurrencyType.RILLIKS, rilliks, "BOSS_REWARD", b.bossId);

            if (i < 3) {
                String[] kc = bSec.getString("rewards.case-key", "spheres:1").split(":");
                casesModule.giveKeys(u, kc[0], Integer.parseInt(kc[1]));
            }
            if (i == 0 && p != null) {
                for (String dropSpec : bSec.getStringList("rewards.guaranteed-drops")) {
                    for (ItemStack item : kitsModule.parseItemSpec(dropSpec)) {
                        p.getInventory().addItem(item).values()
                                .forEach(rem -> p.getWorld().dropItemNaturally(p.getLocation(), rem));
                    }
                }
            }
            if (p != null) {
                PlayerProfile prof = playerModule.getProfile(p);
                prof.setBossKills(prof.bossKills() + 1);
                storage.runAsync(() -> storage.players().saveProfileStats(prof));
                questsModule.recordProgress(p, "BOSS_PARTICIPATION", 1L);
            }
        }

        Bukkit.getServer().sendMessage(locale.component("events.boss-slain", Map.of(
                "boss", b.displayName,
                "top1", topName,
                "damage", String.valueOf((int) topDamage)
        )));
        despawnActiveBoss();
    }

    public void despawnActiveBoss() {
        ActiveBoss b = this.activeBoss;
        this.activeBoss = null;
        if (b == null) return;
        if (Bukkit.getServer() != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(b.bossBar);
            }
        }
        for (Entity m : b.minions) {
            if (m != null && m.isValid()) m.remove();
        }
        if (b.entity != null && b.entity.isValid()) {
            b.entity.remove();
        }
    }

    public Set<String> getBossIds() {
        ConfigurationSection sec = configManager.get("bosses.yml").getConfigurationSection("bosses");
        return sec == null ? Set.of() : sec.getKeys(false);
    }

    public Set<String> getEventIds() {
        ConfigurationSection sec = configManager.get("events.yml").getConfigurationSection("events");
        return sec == null ? Set.of() : sec.getKeys(false);
    }

    private Location findSafeWildLocation(String worldName, int minR, int maxR) {
        World w = Bukkit.getWorld(worldName);
        if (w == null && !Bukkit.getWorlds().isEmpty()) w = Bukkit.getWorlds().getFirst();
        if (w == null) return null;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < 20; i++) {
            double ang = rng.nextDouble(0, Math.PI * 2);
            double dist = rng.nextDouble(minR, maxR);
            int x = (int) Math.round(Math.cos(ang) * dist);
            int z = (int) Math.round(Math.sin(ang) * dist);
            int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Location cand = new Location(w, x + 0.5, Math.max(64, y + 1), z + 0.5);
            if (spawnWorldModule != null && spawnWorldModule.isProtectedSpawn(cand)) continue;
            return cand;
        }
        return new Location(w, minR + 0.5, 80.0, minR + 0.5);
    }
}
