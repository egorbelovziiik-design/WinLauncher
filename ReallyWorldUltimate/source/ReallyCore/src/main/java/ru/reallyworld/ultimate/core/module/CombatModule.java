package ru.reallyworld.ultimate.core.module;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.entity.EnderPearl;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.reallyworld.ultimate.core.api.CurrencyType;
import ru.reallyworld.ultimate.core.api.ReallyModule;
import ru.reallyworld.ultimate.core.config.ConfigManager;
import ru.reallyworld.ultimate.core.storage.StorageModule;
import ru.reallyworld.ultimate.core.storage.model.PlayerProfile;
import ru.reallyworld.ultimate.core.util.TextUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 1.21+ PvP & Combat Tag Engine:
 * - 25-second Combat Tag with dynamic BossBar countdown
 * - Disables /fly, blocks restricted commands, punishes combat-log quit
 * - Anti-farm kill coin rewards with Rank Multipliers (x1 / x2 / x4 / x6)
 * - Killstreak broadcasts, Clan PvP points, and 1.21 Mace burst damage cap
 */
public final class CombatModule implements ReallyModule, Listener {
    private record CombatState(long expiresAtMillis, UUID lastAttacker, BossBar bossBar) {}

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final StorageModule storage;
    private final LocalizationModule locale;
    private final PlayerModule playerModule;
    private final EconomyModule economyModule;
    private final ClanModule clanModule;
    private final QuestsModule questsModule;
    private final SpawnWorldModule spawnWorldModule;

    private final Map<UUID, CombatState> activeCombat = new ConcurrentHashMap<>();
    // key: killerUuid + ":" + victimUuid -> list of recent kill timestamps
    private final Map<String, List<Long>> antiFarmHistory = new ConcurrentHashMap<>();
    private volatile Set<String> blockedCombatCommands = Set.of();
    private BukkitTask tickerTask;
    private volatile boolean enabled;

    public CombatModule(
            JavaPlugin plugin,
            ConfigManager configManager,
            StorageModule storage,
            LocalizationModule locale,
            PlayerModule playerModule,
            EconomyModule economyModule,
            ClanModule clanModule,
            QuestsModule questsModule,
            SpawnWorldModule spawnWorldModule
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.storage = storage;
        this.locale = locale;
        this.playerModule = playerModule;
        this.economyModule = economyModule;
        this.clanModule = clanModule;
        this.questsModule = questsModule;
        this.spawnWorldModule = spawnWorldModule;
    }

    @Override
    public String id() { return "combat"; }

    @Override
    public String displayName() { return "CombatModule (PvP & Combat Tag)"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void enable() {
        this.enabled = configManager.main().getBoolean("modules.combat", true);
        reloadSettings();
        if (plugin != null && Bukkit.getServer() != null) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            this.tickerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickCombatBars, 10L, 10L);
        }
    }

    @Override
    public void reload() {
        reloadSettings();
    }

    private void reloadSettings() {
        Set<String> cmds = new HashSet<>();
        for (String c : configManager.get("pvp.yml").getStringList("combat-tag.blocked-commands")) {
            cmds.add(c.toLowerCase(Locale.ROOT));
        }
        this.blockedCombatCommands = Collections.unmodifiableSet(cmds);
    }

    @Override
    public void disable() {
        this.enabled = false;
        if (tickerTask != null) tickerTask.cancel();
        if (Bukkit.getServer() != null) {
            for (Map.Entry<UUID, CombatState> e : activeCombat.entrySet()) {
                Player p = Bukkit.getPlayer(e.getKey());
                if (p != null) p.hideBossBar(e.getValue().bossBar());
            }
        }
        activeCombat.clear();
    }

    public boolean isInCombat(UUID playerUuid) {
        CombatState st = activeCombat.get(playerUuid);
        if (st == null) return false;
        if (System.currentTimeMillis() >= st.expiresAtMillis()) {
            clearCombat(playerUuid, true);
            return false;
        }
        return true;
    }

    public long getCombatRemainingSeconds(UUID playerUuid) {
        CombatState st = activeCombat.get(playerUuid);
        if (st == null) return 0L;
        long diff = st.expiresAtMillis() - System.currentTimeMillis();
        return diff <= 0 ? 0L : (diff + 999L) / 1000L;
    }

    public void tagPlayers(Player victim, Player attacker) {
        if (!enabled) return;
        int duration = configManager.get("pvp.yml").getInt("combat-tag.duration-seconds", 25);
        applyTag(victim, attacker.getUniqueId(), duration);
        applyTag(attacker, victim.getUniqueId(), duration);
    }

    private void applyTag(Player player, UUID opponent, int durationSec) {
        if (player.hasPermission("reallycore.admin") && player.getGameMode().name().equals("CREATIVE")) {
            return;
        }
        if (configManager.get("pvp.yml").getBoolean("combat-tag.disable-fly-on-tag", true) && player.isFlying()) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
        long expiresAt = System.currentTimeMillis() + (durationSec * 1000L);
        CombatState prev = activeCombat.get(player.getUniqueId());
        if (prev == null) {
            String titleTpl = configManager.get("pvp.yml").getString("combat-tag.bossbar.title", "<#FF4500>Режим боя: <seconds> сек.");
            BossBar bar = BossBar.bossBar(
                    TextUtil.mm(titleTpl, Map.of("seconds", String.valueOf(durationSec))),
                    1.0f,
                    BossBar.Color.RED,
                    BossBar.Overlay.NOTCHED_10
            );
            player.showBossBar(bar);
            activeCombat.put(player.getUniqueId(), new CombatState(expiresAt, opponent, bar));
            locale.send(player, "combat.tagged", Map.of("seconds", String.valueOf(durationSec)));
        } else {
            activeCombat.put(player.getUniqueId(), new CombatState(expiresAt, opponent, prev.bossBar()));
        }
    }

    private void clearCombat(UUID uuid, boolean notify) {
        CombatState removed = activeCombat.remove(uuid);
        if (removed != null && Bukkit.getServer() != null) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.hideBossBar(removed.bossBar());
                if (notify) {
                    locale.send(p, "combat.untagged");
                }
            }
        }
    }

    private void tickCombatBars() {
        long now = System.currentTimeMillis();
        int maxSec = Math.max(1, configManager.get("pvp.yml").getInt("combat-tag.duration-seconds", 25));
        String titleTpl = configManager.get("pvp.yml").getString("combat-tag.bossbar.title", "<#FF4500>Режим боя: <seconds> сек.");

        for (UUID uuid : new ArrayList<>(activeCombat.keySet())) {
            CombatState st = activeCombat.get(uuid);
            if (st == null) continue;
            long remMs = st.expiresAtMillis() - now;
            if (remMs <= 0) {
                clearCombat(uuid, true);
                continue;
            }
            long remSec = (remMs + 999L) / 1000L;
            float progress = Math.max(0.0f, Math.min(1.0f, (float) remMs / (maxSec * 1000.0f)));
            st.bossBar().name(TextUtil.mm(titleTpl, Map.of("seconds", String.valueOf(remSec))));
            st.bossBar().progress(progress);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvpDamage(EntityDamageByEntityEvent event) {
        if (!enabled || !(event.getEntity() instanceof Player victim)) return;
        Player attacker = resolvePlayerAttacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) return;

        // Protect Spawn Safezone (unless inside Coliseum arena)
        if (spawnWorldModule != null && (spawnWorldModule.isProtectedSpawn(victim.getLocation())
                || spawnWorldModule.isProtectedSpawn(attacker.getLocation()))) {
            event.setCancelled(true);
            locale.sendRaw(attacker, "<red>PvP отключено в мирной зоне Спавна! Спускайтесь в Колизей (<gold>/warp pvp</gold>).");
            return;
        }

        // Check Clan Friendly Fire & Ally Protection
        if (clanModule != null && !clanModule.canDamage(attacker.getUniqueId(), victim.getUniqueId())) {
            event.setCancelled(true);
            locale.sendRaw(attacker, "<yellow>Этот игрок состоит в вашем клане или союзе!");
            return;
        }

        // 1.21 Mace Burst Cap so fights remain skill-based rather than one-shot exploits
        if (attacker.getInventory().getItemInMainHand().getType() == Material.MACE) {
            double maceCap = configManager.get("pvp.yml").getDouble("balance-121.mace-max-final-damage", 26.0);
            if (event.getDamage() > maceCap) {
                event.setDamage(maceCap);
            }
        }

        tagPlayers(victim, attacker);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCombatCommand(PlayerCommandPreprocessEvent event) {
        if (!enabled) return;
        Player p = event.getPlayer();
        if (!isInCombat(p.getUniqueId()) || p.hasPermission("reallycore.admin")) return;
        String cmd = event.getMessage().trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
        if (blockedCombatCommands.contains(cmd)) {
            event.setCancelled(true);
            locale.send(p, "combat.blocked-command");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEnderPearlLaunch(ProjectileLaunchEvent event) {
        if (!enabled || !(event.getEntity() instanceof EnderPearl pearl)) return;
        if (pearl.getShooter() instanceof Player p) {
            int cdTicks = configManager.get("pvp.yml").getInt("cooldowns.ender-pearl-seconds", 15) * 20;
            Bukkit.getScheduler().runTask(plugin, () -> p.setCooldown(Material.ENDER_PEARL, cdTicks));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onConsumeCooldown(PlayerItemConsumeEvent event) {
        if (!enabled) return;
        Player p = event.getPlayer();
        Material type = event.getItem().getType();
        if (type == Material.ENCHANTED_GOLDEN_APPLE) {
            int cdTicks = configManager.get("pvp.yml").getInt("cooldowns.enchanted-golden-apple-seconds", 45) * 20;
            if (p.hasCooldown(Material.ENCHANTED_GOLDEN_APPLE)) {
                event.setCancelled(true);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> p.setCooldown(Material.ENCHANTED_GOLDEN_APPLE, cdTicks));
        } else if (type == Material.GOLDEN_APPLE) {
            int cdTicks = configManager.get("pvp.yml").getInt("cooldowns.golden-apple-seconds", 6) * 20;
            if (p.hasCooldown(Material.GOLDEN_APPLE)) {
                event.setCancelled(true);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> p.setCooldown(Material.GOLDEN_APPLE, cdTicks));
        }
    }

    // Punish Combat Log on Quit
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCombatQuit(PlayerQuitEvent event) {
        if (!enabled) return;
        Player quitter = event.getPlayer();
        if (!isInCombat(quitter.getUniqueId())) return;
        clearCombat(quitter.getUniqueId(), false);
        if (configManager.get("pvp.yml").getBoolean("combat-tag.kill-on-combat-quit", true)) {
            quitter.setHealth(0.0);
            if (configManager.get("pvp.yml").getBoolean("combat-tag.broadcast-combat-quit", true)) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    locale.send(online, "combat.quit-punished", Map.of("player", quitter.getName()));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        clearCombat(victim.getUniqueId(), false);

        PlayerProfile victimProfile = playerModule.getProfile(victim);
        victimProfile.setDeaths(victimProfile.deaths() + 1);
        victimProfile.setKillStreak(0);
        storage.runAsync(() -> storage.players().saveProfileStats(victimProfile));

        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;

        PlayerProfile killerProfile = playerModule.getProfile(killer);
        killerProfile.setKills(killerProfile.kills() + 1);
        int newStreak = killerProfile.killStreak() + 1;
        killerProfile.setKillStreak(newStreak);
        storage.runAsync(() -> storage.players().saveProfileStats(killerProfile));

        if (clanModule != null) {
            clanModule.recordPvpKill(killer.getUniqueId(), victim.getUniqueId());
        }
        if (questsModule != null) {
            questsModule.recordProgress(killer, "PVP_KILLS", 1L);
        }

        // Anti-farm check before granting coin reward
        if (canRewardKill(killer, victim)) {
            long minCoins = configManager.get("pvp.yml").getLong("rewards.base-kill-coins-min", 60L);
            long maxCoins = configManager.get("pvp.yml").getLong("rewards.base-kill-coins-max", 140L);
            long base = ThreadLocalRandom.current().nextLong(minCoins, maxCoins + 1L);
            double mult = playerModule.getRank(killer).killCoinMultiplier();
            long totalReward = Math.max(1L, Math.round(base * mult));
            economyModule.deposit(killer.getUniqueId(), CurrencyType.COINS, totalReward, "PVP_KILL", "Killed " + victim.getName());
            locale.send(killer, "combat.kill-reward", Map.of(
                    "victim", victim.getName(),
                    "coins", TextUtil.formatAmount(totalReward),
                    "mult", String.valueOf((int) mult)
            ));
        }

        var streakMsg = configManager.get("pvp.yml").getString("killstreaks.broadcasts." + newStreak);
        if (streakMsg != null && !streakMsg.isBlank()) {
            Component comp = TextUtil.mm(locale.prefix() + streakMsg, Map.of("player", killer.getName()));
            Bukkit.getServer().sendMessage(comp);
        }
    }

    private boolean canRewardKill(Player killer, Player victim) {
        var pvp = configManager.get("pvp.yml");
        if (pvp.getBoolean("anti-farm.block-same-ip-rewards", true)
                && killer.getAddress() != null && victim.getAddress() != null
                && Objects.equals(killer.getAddress().getAddress(), victim.getAddress().getAddress())) {
            return false;
        }
        String key = killer.getUniqueId() + ":" + victim.getUniqueId();
        long now = System.currentTimeMillis();
        long windowMs = pvp.getLong("anti-farm.same-victim-window-seconds", 900L) * 1000L;
        int maxKills = pvp.getInt("anti-farm.max-rewarded-kills-per-window", 2);
        List<Long> timestamps = antiFarmHistory.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()));
        timestamps.removeIf(ts -> (now - ts) > windowMs);
        if (timestamps.size() >= maxKills) {
            return false;
        }
        timestamps.add(now);
        return true;
    }

    private Player resolvePlayerAttacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
