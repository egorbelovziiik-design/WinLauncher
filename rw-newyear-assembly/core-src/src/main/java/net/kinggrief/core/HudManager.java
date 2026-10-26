package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class HudManager {
    private static final String[] HEADER_FRAMES = new String[]{
            "\uE976 &#FF1744&lK&#FF3D57&lI&#FF636A&lN&#FF897D&lG&#FFAF90&lG&#FF897D&lR&#FF636A&lI&#FF3D57&lE&#FF1744&lF \uE976",
            "\uE976 &#FF3D57&lK&#FF636A&lI&#FF897D&lN&#FFFFFF&lG&#FFD740&lG&#FFFFFF&lR&#FF897D&lI&#FF636A&lE&#FF3D57&lF \uE976",
            "\uE976 &#FFD740&lK&#FF897D&lI&#FF3D57&lN&#FF1744&lG&#FF3D57&lG&#FF897D&lR&#FFD740&lI&#FFFFFF&lE&#80D8FF&lF \uE976"
    };

    private static final String[] LINE_KEYS = new String[]{
            ChatColor.BLACK.toString() + ChatColor.RESET,
            ChatColor.DARK_BLUE.toString() + ChatColor.RESET,
            ChatColor.DARK_GREEN.toString() + ChatColor.RESET,
            ChatColor.DARK_AQUA.toString() + ChatColor.RESET,
            ChatColor.DARK_RED.toString() + ChatColor.RESET,
            ChatColor.DARK_PURPLE.toString() + ChatColor.RESET,
            ChatColor.GOLD.toString() + ChatColor.RESET,
            ChatColor.GRAY.toString() + ChatColor.RESET,
            ChatColor.DARK_GRAY.toString() + ChatColor.RESET,
            ChatColor.BLUE.toString() + ChatColor.RESET,
            ChatColor.GREEN.toString() + ChatColor.RESET,
            ChatColor.AQUA.toString() + ChatColor.RESET
    };

    private final KingGriefCore plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<UUID, Scoreboard>();
    private final Map<UUID, Long> combatUntil = new HashMap<UUID, Long>();
    private final Map<UUID, BossBar> combatBars = new HashMap<UUID, BossBar>();
    private int animTick = 0;

    public HudManager(KingGriefCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // 5-FPS (every 4 ticks = 0.2s) smooth animation for ALL 40 Rank Badges in TAB, Scoreboard & Nametags!
        new BukkitRunnable() {
            @Override
            public void run() {
                animTick++;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (animTick % 5 == 0) {
                        plugin.getStore().addPlaySeconds(p.getName(), 1L);
                    }
                    updateCombatBar(p);
                    applyFlightTrails(p);
                    if (plugin.getConfig().getBoolean("enable-builtin-tab-and-scoreboard", true)) {
                        updatePlayerHud(p);
                    }
                }
            }
        }.runTaskTimer(plugin, 10L, 4L);

        // Passive Offhand 3D Spheres + Custom Buffed Elytras every 25 ticks
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    applyOffhandAndElytraPassives(p);
                }
            }
        }.runTaskTimer(plugin, 20L, 25L);
    }

    public void removePlayer(Player p) {
        boards.remove(p.getUniqueId());
        clearCombat(p.getUniqueId());
    }

    public void shutdown() {
        for (BossBar b : combatBars.values()) b.removeAll();
        combatBars.clear();
    }

    public void tagCombat(Player p) {
        if (p == null) return;
        combatUntil.put(p.getUniqueId(), Long.valueOf(System.currentTimeMillis() + 30_000L));
        BossBar bar = combatBars.get(p.getUniqueId());
        if (bar == null) {
            bar = Bukkit.createBossBar(
                    ColorUtil.c("&#FF1744&l⚔ РЕЖИМ БОЯ (КТ): &f30 сек. &8| &7Не выходите с сервера!"),
                    BarColor.RED, BarStyle.SEGMENTED_10);
            bar.addPlayer(p);
            combatBars.put(p.getUniqueId(), bar);
        }
    }

    public boolean isInCombat(Player p) {
        if (p == null) return false;
        Long u = combatUntil.get(p.getUniqueId());
        return u != null && u.longValue() > System.currentTimeMillis();
    }

    public int getCombatSecondsLeft(Player p) {
        Long u = combatUntil.get(p.getUniqueId());
        if (u == null) return 0;
        long d = u.longValue() - System.currentTimeMillis();
        return d <= 0 ? 0 : (int) Math.ceil(d / 1000.0);
    }

    public void clearCombat(UUID uid) {
        combatUntil.remove(uid);
        BossBar b = combatBars.remove(uid);
        if (b != null) b.removeAll();
    }

    private void updateCombatBar(Player p) {
        int sec = getCombatSecondsLeft(p);
        BossBar bar = combatBars.get(p.getUniqueId());
        if (sec <= 0) {
            if (bar != null) {
                bar.removeAll();
                combatBars.remove(p.getUniqueId());
                p.sendMessage(ColorUtil.c("&#00E676✔ &fВы вышли из режима боя (КТ)!"));
            }
            combatUntil.remove(p.getUniqueId());
        } else if (bar != null) {
            bar.setTitle(ColorUtil.c("&#FF1744&l⚔ РЕЖИМ БОЯ (КТ): &#FFD740&l" + sec + " сек. &8| &fНе выходите с сервера!"));
            bar.setProgress(Math.max(0.0, Math.min(1.0, sec / 30.0)));
        }
    }

    public void updatePlayerHud(Player p) {
        if (p == null || !p.isOnline()) return;
        DataStore store = plugin.getStore();
        RankManager ranks = plugin.getRanks();
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        int ping = safePing(p);
        String ip = plugin.getConfig().getString("server-ip", "kinggrief.pickme.cloud");
        String titleAnim = HEADER_FRAMES[(animTick / 4) % HEADER_FRAMES.length];
        int activeBosses = plugin.getBossEvents() != null ? plugin.getBossEvents().getActiveEvents().size() : 0;

        String header = ColorUtil.c("\n" + titleAnim + "\n&#B3E5FCНовогодний Сезон v5.0 · FT & ReallyWorld Edition\n");
        String footer = ColorUtil.c("\n&#80D8FFОнлайн: &#00E676" + online + "&8/&f" + max
                + " &8| &#80D8FFПинг: &#FFD740" + ping + " мс"
                + " &8| &#FF5252Ивентов: &#FFE082" + activeBosses + " &7(/events)"
                + "\n&#FFD740Бафовар: &e/bafs &8• &#E040FBСкины Трапок: &d/skins &8• &#00E5FFШоп: &b/shop"
                + "\n&#FF4081Магазин & LP-Редактор: &fkinggrief.vercel.app &8(&7IP: &f" + ip + "&8)\n");

        RankManager.RankDef myRank = ranks.getRank(p);
        // Animated badge ONLY — NO duplicate rank text on the right!
        String animBadge = ranks.getAnimatedBadge(myRank, animTick);
        String animPref = ranks.getAnimatedPrefix(p, animTick);
        DataStore.Clan myClan = store.getClanOf(p.getName());
        String clanBadge = myClan != null ? " &8[&#B388FF" + myClan.tag + "&8]" : "";

        p.setPlayerListHeaderFooter(header, footer);
        p.setPlayerListName(ColorUtil.c(animPref + p.getName() + clanBadge));

        ScoreboardManager sm = Bukkit.getScoreboardManager();
        if (sm == null) return;
        if (!store.isSbEnabled(p)) {
            p.setScoreboard(sm.getMainScoreboard());
            return;
        }

        Scoreboard sb = boards.get(p.getUniqueId());
        Objective obj;
        if (sb == null) {
            sb = sm.getNewScoreboard();
            obj = sb.registerNewObjective("kg_sb", "dummy", ColorUtil.c(titleAnim));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            for (int i = 0; i < LINE_KEYS.length; i++) {
                Team t = sb.registerNewTeam("ln_" + i);
                t.addEntry(LINE_KEYS[i]);
                obj.getScore(LINE_KEYS[i]).setScore(LINE_KEYS.length - i);
            }
            boards.put(p.getUniqueId(), sb);
            p.setScoreboard(sb);
        } else {
            obj = sb.getObjective("kg_sb");
            if (obj != null) obj.setDisplayName(ColorUtil.c(titleAnim));
            if (p.getScoreboard() != sb) p.setScoreboard(sb);
        }

        String clanStr = myClan != null ? "&#B388FF[" + myClan.tag + "]" : "&7Нет клана";
        String coinsStr = ColorUtil.fmtMoney(store.getCoins(p.getName())) + "$";
        String relicsStr = ColorUtil.fmtInt(store.getRelics(p));
        int snow = store.getSnowflakes(p.getName());
        int kills = store.getKills(p.getName());
        int deaths = store.getDeaths(p.getName());

        String[] lines = new String[]{
                "&8&m                      ",
                " \uE970 &#80D8FFНик: &f" + p.getName(),
                // ONLY the animated badge glyph — NO duplicate text next to the prefix!
                " \uE976 &#FFD740Ранг: " + animBadge,
                " \uE971 &#B388FFКлан: " + clanStr,
                "",
                " \uE972 &#FFEA00Монеты: &a" + coinsStr,
                " \uE973 &#00E5FFРилики: &b" + relicsStr + " ❖ &8(&b" + snow + "❄&8)",
                " \uE974 &#FF5252Убийств: &f" + kills + " &8(&7См: " + deaths + "&8)",
                " \uE975 &#69F0AEПинг: &e" + ping + " мс &8| &a" + online + " онл.",
                " ",
                " \uE977 &#FF4081kinggrief.vercel.app",
                "&8&m                       "
        };

        for (int i = 0; i < LINE_KEYS.length; i++) {
            Team t = sb.getTeam("ln_" + i);
            if (t != null) {
                t.setPrefix(ColorUtil.c(lines[i]));
            }
        }

        for (Player other : Bukkit.getOnlinePlayers()) {
            RankManager.RankDef rOther = ranks.getRank(other);
            DataStore.Clan cOther = store.getClanOf(other.getName());
            String tName = String.format("r%02d_%s", Integer.valueOf(rOther.sortOrder),
                    other.getName().substring(0, Math.min(8, other.getName().length())));
            Team nt = sb.getTeam(tName);
            if (nt == null) nt = sb.registerNewTeam(tName);
            nt.setPrefix(ColorUtil.c(ranks.getAnimatedPrefix(other, animTick)));
            nt.setSuffix(cOther != null ? ColorUtil.c(" &8[&#B388FF" + cOther.tag + "&8]") : "");
            if (!nt.hasEntry(other.getName())) {
                nt.addEntry(other.getName());
            }
        }
    }

    private void applyFlightTrails(Player p) {
        if (!p.isGliding()) return;
        String id = ItemFactory.getCustomId(p.getInventory().getChestplate());
        if (id == null) return;
        try {
            if ("elytra_phoenix".equals(id)) {
                p.getWorld().spawnParticle(Particle.FLAME, p.getLocation(), 4, 0.2, 0.2, 0.2, 0.02);
            } else if ("elytra_santa".equals(id) || "elytra_wind".equals(id)) {
                p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation(), 5, 0.25, 0.25, 0.25, 0.02);
            } else if ("elytra_vampire".equals(id)) {
                p.getWorld().spawnParticle(Particle.CRIMSON_SPORE, p.getLocation(), 6, 0.25, 0.25, 0.25, 0.02);
            } else if ("elytra_thunder".equals(id)) {
                p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation(), 5, 0.25, 0.25, 0.25, 0.05);
            }
        } catch (Throwable ignored) {
        }
    }

    private void applyOffhandAndElytraPassives(Player p) {
        double bonusHp = 0.0;
        String offId = ItemFactory.getCustomId(p.getInventory().getItemInOffHand());
        if (offId != null) {
            if ("shar_titana".equals(offId)) {
                bonusHp += 8.0;
                giveEff(p, "resistance", 60, 0);
                giveEff(p, "strength", 60, 0);
            } else if ("shar_andromedy".equals(offId)) {
                giveEff(p, "speed", 60, 1);
                giveEff(p, "strength", 60, 1);
                giveEff(p, "jump_boost", 60, 1);
            } else if ("shar_chimery".equals(offId)) {
                giveEff(p, "strength", 60, 1);
                giveEff(p, "fire_resistance", 60, 0);
                giveEff(p, "regeneration", 60, 0);
            } else if ("shar_apollona".equals(offId)) {
                giveEff(p, "haste", 60, 1);
                giveEff(p, "speed", 60, 1);
                giveEff(p, "night_vision", 260, 0);
            } else if ("shar_aida".equals(offId)) {
                giveEff(p, "strength", 60, 1);
                giveEff(p, "fire_resistance", 60, 0);
            } else if ("shar_osirisa".equals(offId)) {
                bonusHp += 4.0;
                giveEff(p, "regeneration", 60, 1);
                giveEff(p, "resistance", 60, 0);
            } else if ("shar_aresa".equals(offId)) {
                giveEff(p, "strength", 60, 2);
                giveEff(p, "speed", 60, 0);
            } else if ("shar_santy".equals(offId)) {
                bonusHp += 12.0;
                giveEff(p, "strength", 60, 1);
                giveEff(p, "speed", 60, 1);
                giveEff(p, "regeneration", 60, 0);
            }
        }

        String chestId = ItemFactory.getCustomId(p.getInventory().getChestplate());
        if (chestId != null) {
            if ("elytra_wind".equals(chestId)) {
                giveEff(p, "speed", 60, 1);
                if (p.isSneaking() && !p.isOnGround()) giveEff(p, "slow_falling", 35, 0);
            } else if ("elytra_phoenix".equals(chestId)) {
                bonusHp += 4.0;
                giveEff(p, "fire_resistance", 80, 0);
            } else if ("elytra_vampire".equals(chestId)) {
                giveEff(p, "strength", 60, 0);
                giveEff(p, "night_vision", 260, 0);
            } else if ("elytra_thunder".equals(chestId)) {
                giveEff(p, "speed", 60, 1);
                if (p.isGliding()) giveEff(p, "strength", 60, 1);
            } else if ("elytra_titan".equals(chestId)) {
                bonusHp += 8.0;
                giveEff(p, "resistance", 60, 0);
            } else if ("elytra_santa".equals(chestId)) {
                bonusHp += 12.0;
                giveEff(p, "strength", 60, 1);
                giveEff(p, "speed", 60, 1);
                giveEff(p, "regeneration", 60, 0);
            }
        }

        try {
            double target = 20.0 + bonusHp;
            if (Math.abs(p.getMaxHealth() - target) > 0.1) {
                p.setMaxHealth(target);
                if (p.getHealth() > target) p.setHealth(target);
            }
        } catch (Throwable ignored) {
        }
    }

    private void giveEff(Player p, String key, int ticks, int amp) {
        PotionEffectType pet = PotionEffectType.getByKey(NamespacedKey.minecraft(key));
        if (pet != null) {
            p.addPotionEffect(new PotionEffect(pet, ticks, amp, true, false, true));
        }
    }

    private int safePing(Player p) {
        try {
            return Math.max(1, p.getPing());
        } catch (Throwable t) {
            return 20;
        }
    }

    public int getAnimTick() {
        return animTick;
    }
}
