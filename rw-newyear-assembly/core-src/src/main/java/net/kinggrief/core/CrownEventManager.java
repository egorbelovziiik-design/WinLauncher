package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** A bounded opt-in PvP event. The crown is virtual: it never touches player inventories. */
public final class CrownEventManager implements Listener {
    private enum Phase { IDLE, LOBBY, RUNNING }
    private final KingGriefCore plugin;
    private final Set<UUID> players = new LinkedHashSet<UUID>();
    private final Map<UUID, Integer> scores = new HashMap<UUID, Integer>();
    private final Random random = new Random();
    private Phase phase = Phase.IDLE;
    private BossBar bar;
    private UUID holder;
    private int seconds, goalSeconds, autoCountdown = 1200;
    private long lastTransferNanos;
    private boolean stopping;

    public CrownEventManager(KingGriefCore plugin) { this.plugin = plugin; }

    public void start() {
        new BukkitRunnable() {
            @Override public void run() { tickSecond(); }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void shutdown() {
        stopping = true;
        finish(null, "Ивент остановлен при выключении сервера.");
    }

    public boolean handleCommand(CommandSender sender, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(java.util.Locale.ROOT);
        if ("start".equals(sub) || "stop".equals(sub)) {
            if (sender instanceof Player && !plugin.getBossEvents().canAdminSpawnEvents((Player)sender)) {
                sender.sendMessage(ColorUtil.c("&cУправлять ивентом могут только администраторы."));
                return true;
            }
            if ("stop".equals(sub)) {
                if (phase == Phase.IDLE) sender.sendMessage(ColorUtil.c("&7Охота за короной сейчас не идёт."));
                else finish(null, "Ивент остановлен администратором, награда не выдаётся.");
            } else beginLobby(sender);
            return true;
        }
        if (!(sender instanceof Player)) {
            if ("status".equals(sub)) sender.sendMessage("Crown event: " + phase.name() + ", players=" + players.size() + ", seconds=" + seconds);
            else sender.sendMessage("Use crown start, crown stop or crown status in the console.");
            return true;
        }
        Player player = (Player)sender;
        if ("join".equals(sub)) {
            if (phase != Phase.LOBBY) {
                player.sendMessage(ColorUtil.c("&eЗапись на охоту за короной сейчас закрыта."));
            } else if (!eligible(player)) {
                player.sendMessage(ColorUtil.c("&cВойдите в аккаунт и встаньте в PvP-яму /warp pvp в выживании, без /god и полёта."));
            } else if (players.add(player.getUniqueId())) {
                bar.addPlayer(player);
                player.sendMessage(ColorUtil.c("&#FFD740👑 Вы участвуете в охоте за короной! Не покидайте PvP-яму."));
                updateBar();
            } else player.sendMessage(ColorUtil.c("&eВы уже записаны."));
            return true;
        }
        if ("leave".equals(sub)) {
            remove(player.getUniqueId(), "покинул ивент");
            player.sendMessage(ColorUtil.c("&7Вы вышли из охоты за короной."));
            return true;
        }
        if (!"status".equals(sub)) {
            player.sendMessage(ColorUtil.c("&e/crown join &7— участвовать, &e/crown leave &7— выйти, &e/crown status &7— статус."));
            return true;
        }
        if (phase == Phase.IDLE) player.sendMessage(ColorUtil.c("&7Охота за короной пока не идёт. Следите за объявлениями."));
        else player.sendMessage(ColorUtil.c("&#FFD740👑 " + (phase == Phase.LOBBY ? "Запись" : "Матч")
                + " &7| игроков: &f" + players.size() + " &7| осталось: &f" + ColorUtil.fmtTime(seconds)
                + (holder == null ? "" : " &7| корона: &f" + name(holder))));
        return true;
    }

    private void beginLobby(CommandSender sender) {
        if (stopping || phase != Phase.IDLE) {
            if (sender != null) sender.sendMessage(ColorUtil.c("&eОхота за короной уже идёт."));
            return;
        }
        phase = Phase.LOBBY;
        autoCountdown = bounded("crown-event.interval-seconds", 1800, 300, 86400);
        seconds = bounded("crown-event.lobby-seconds", 45, 10, 180);
        goalSeconds = bounded("crown-event.goal-seconds", 90, 10, 300);
        players.clear(); scores.clear(); holder = null; lastTransferNanos = 0L;
        bar = Bukkit.createBossBar(ColorUtil.c("&#FFD740👑 Охота за короной: запись"), BarColor.YELLOW, BarStyle.SEGMENTED_10);
        Bukkit.broadcastMessage(ColorUtil.c("&#FFD740👑 ОХОТА ЗА КОРОНОЙ! &fВстаньте в PvP-яму /warp pvp и введите &e/crown join&f. Запись: " + seconds + " сек."));
        updateBar();
    }

    private int bounded(String key, int def, int min, int max) {
        return Math.max(min, Math.min(max, plugin.getConfig().getInt(key, def)));
    }

    private void tickSecond() {
        if (stopping) return;
        if (phase == Phase.IDLE) {
            if (!plugin.getConfig().getBoolean("crown-event.auto-enabled", true)) return;
            if (--autoCountdown <= 0) {
                autoCountdown = bounded("crown-event.interval-seconds", 1800, 300, 86400);
                if (Bukkit.getOnlinePlayers().size() >= 2) beginLobby(null);
            }
            return;
        }
        Iterator<UUID> it = players.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Player player = Bukkit.getPlayer(id);
            if (!eligible(player)) {
                it.remove();
                if (bar != null && player != null) {
                    bar.removePlayer(player);
                    player.sendMessage(ColorUtil.c("&cВы выбыли из охоты за короной: необходимо оставаться в PvP-яме."));
                }
                if (id.equals(holder)) holder = null;
            }
        }
        if (phase == Phase.LOBBY) {
            if (--seconds <= 0) {
                if (players.size() < 2) {
                    finish(null, "Недостаточно игроков в PvP-яме; награда не выдаётся.");
                    return;
                }
                phase = Phase.RUNNING;
                seconds = bounded("crown-event.match-seconds", 300, 30, 900);
                holder = randomParticipant(null);
                Bukkit.broadcastMessage(ColorUtil.c("&#FFD740👑 Охота началась! Первая корона у &f" + name(holder)
                        + "&7. Ударьте носителя и удерживайте корону " + goalSeconds + " сек."));
            }
            updateBar();
            return;
        }
        // Do not award an uncontested round when the other player disconnects or leaves.
        if (players.size() < 2) {
            finish(null, "Участников осталось меньше двух; награда не выдаётся.");
            return;
        }
        if (holder == null || !players.contains(holder)) holder = randomParticipant(null);
        if (holder != null) {
            int score = scores.containsKey(holder) ? scores.get(holder).intValue() + 1 : 1;
            scores.put(holder, Integer.valueOf(score));
            Player carrier = Bukkit.getPlayer(holder);
            if (carrier != null) carrier.getWorld().spawnParticle(Particle.END_ROD, carrier.getLocation().add(0, 2.3, 0), 3, 0.18, 0.12, 0.18, 0.005);
            if (score >= goalSeconds) { finish(holder, null); return; }
        }
        if (--seconds <= 0) {
            UUID winner = null; int best = 0; boolean tie = false;
            for (UUID id : players) {
                int score = scores.containsKey(id) ? scores.get(id).intValue() : 0;
                if (score > best) { winner = id; best = score; tie = false; }
                else if (score == best && score > 0) tie = true;
            }
            finish(tie ? null : winner, tie ? "Ничья: награда не выдаётся." : null);
            return;
        }
        updateBar();
    }

    private boolean eligible(Player p) {
        if (p == null || !p.isOnline() || p.isDead() || !plugin.getStore().isAuthenticated(p)
                || plugin.getModeration().isFrozenOrAscending(p) || plugin.getStore().isGodMode(p)
                || p.isInvulnerable() || p.isFlying() || p.isGliding()) return false;
        if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) return false;
        Location loc = p.getLocation();
        return loc.getWorld() != null && loc.getWorld().getPVP()
                && plugin.getSpawnEngine().isInSpawnWorld(loc)
                && plugin.getSpawnEngine().isInsidePvpZone(loc)
                && !plugin.getSpawnEngine().isInsideAnyKabinka(loc);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (phase != Phase.RUNNING || holder == null || !(event.getEntity() instanceof Player)
                || !holder.equals(event.getEntity().getUniqueId()) || event.getFinalDamage() <= 0) return;
        Player attacker = attacker(event.getDamager());
        Player victim = (Player) event.getEntity();
        if (attacker == null || attacker.equals(victim) || !players.contains(attacker.getUniqueId())
                || !eligible(attacker) || !eligible(victim)) return;
        long now = System.nanoTime();
        if (lastTransferNanos != 0L && now - lastTransferNanos < java.util.concurrent.TimeUnit.SECONDS.toNanos(2)) return;
        holder = attacker.getUniqueId();
        lastTransferNanos = now;
        Bukkit.broadcastMessage(ColorUtil.c("&#FFD740👑 Корона теперь у &f" + attacker.getName() + "&7!"));
        updateBar();
    }

    private Player attacker(Entity entity) {
        if (entity instanceof Player) return (Player)entity;
        if (entity instanceof Projectile) {
            ProjectileSource source = ((Projectile)entity).getShooter();
            if (source instanceof Player) return (Player)source;
        }
        return null;
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) { remove(e.getPlayer().getUniqueId(), "вышел с сервера"); }
    @EventHandler public void onDeath(PlayerDeathEvent e) { remove(e.getEntity().getUniqueId(), "выбыл после смерти"); }

    private void remove(UUID id, String why) {
        if (!players.remove(id)) return;
        Player p = Bukkit.getPlayer(id);
        if (bar != null && p != null) bar.removePlayer(p);
        if (id.equals(holder)) holder = null;
        if (!stopping && phase != Phase.IDLE) Bukkit.broadcastMessage(ColorUtil.c("&7[Корона] &f" + name(id) + " &7" + why + "."));
        updateBar();
    }

    private UUID randomParticipant(UUID exclude) {
        List<UUID> candidates = new ArrayList<UUID>(players);
        candidates.remove(exclude);
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    private String name(UUID id) {
        Player p = id == null ? null : Bukkit.getPlayer(id);
        return p == null ? "игрок" : p.getName();
    }

    private void updateBar() {
        if (bar == null) return;
        if (phase == Phase.LOBBY) {
            bar.setTitle(ColorUtil.c("&#FFD740👑 Запись: " + seconds + " сек. &7| игроков: &f" + players.size()));
            bar.setProgress(Math.max(0, Math.min(1, seconds / (double)bounded("crown-event.lobby-seconds", 45, 10, 180))));
        } else if (phase == Phase.RUNNING) {
            int score = holder != null && scores.containsKey(holder) ? scores.get(holder).intValue() : 0;
            bar.setTitle(ColorUtil.c("&#FFD740👑 " + name(holder) + " &7| удержано: &f" + score + "/" + goalSeconds
                    + " сек. &7| " + ColorUtil.fmtTime(seconds)));
            bar.setProgress(Math.max(0, Math.min(1, score / (double)goalSeconds)));
        }
    }

    private void finish(UUID winner, String reason) {
        if (phase == Phase.IDLE) return;
        if (winner != null && players.contains(winner)) {
            Player p = Bukkit.getPlayer(winner);
            if (eligible(p)) {
                plugin.getStore().addCoins(p.getName(), 50000.0);
                plugin.getStore().addRelics(p.getName(), 20);
                plugin.getStore().save();
                Bukkit.broadcastMessage(ColorUtil.c("&#FFD740👑 Победитель охоты: &f" + p.getName() + " &8→ &a50 000$ &b+20❖"));
            }
        } else if (!stopping && reason != null) Bukkit.broadcastMessage(ColorUtil.c("&7[Корона] " + reason));
        if (bar != null) { bar.removeAll(); bar.setVisible(false); bar = null; }
        players.clear(); scores.clear(); holder = null; phase = Phase.IDLE;
    }
}
