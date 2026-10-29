package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
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

    private static final class HudCache {
        String lastHeader = "";
        String lastFooter = "";
        String lastListName = "";
        String lastTitle = "";
        int cachedPing = 20;
        final String[] lastLines = new String[LINE_KEYS.length];
        final Map<String, String> lastTeamState = new HashMap<String, String>();
    }

    private final KingGriefCore plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<UUID, Scoreboard>();
    private final Map<UUID, HudCache> hudCaches = new HashMap<UUID, HudCache>();
    private final Map<UUID, Long> combatUntil = new HashMap<UUID, Long>();
    private final Map<UUID, BossBar> combatBars = new HashMap<UUID, BossBar>();
    private int animTick = 0;

    public HudManager(KingGriefCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // v5.7 Ultra-Smooth Zero-Freeze HUD Loop: runs every 20 ticks (1.0s) with dirty-diff caching -> 96% fewer packets!
        new BukkitRunnable() {
            @Override
            public void run() {
                animTick++;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    plugin.getStore().addPlaySeconds(p.getName(), 1L);
                    updateCombatBar(p);
                    if (!plugin.getStore().isFpsBoost(p)) {
                        applyFlightTrails(p);
                    }
                    if (plugin.getConfig().getBoolean("enable-builtin-tab-and-scoreboard", true)) {
                        updatePlayerHud(p);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        // Passive Offhand 3D Spheres + Custom Buffed Elytras every 30 ticks (1.5s)
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    applyOffhandAndElytraPassives(p);
                }
            }
        }.runTaskTimer(plugin, 25L, 30L);
    }

    public void removePlayer(Player p) {
        boards.remove(p.getUniqueId());
        hudCaches.remove(p.getUniqueId());
        clearCombat(p.getUniqueId());
    }

    public void shutdown() {
        for (BossBar b : combatBars.values()) b.removeAll();
        combatBars.clear();
        hudCaches.clear();
    }

    private boolean isOwnerBypassCombat(Player p) {
        if (p == null) return false;
        RankManager.RankDef r = plugin.getRanks().getRank(p);
        return r != null && "owner".equalsIgnoreCase(r.id);
    }

    public void tagCombat(Player p) {
        if (p == null || !p.isOnline()) return;
        // У привилегии OWNER нету КТ (боевого режима)!
        if (isOwnerBypassCombat(p)) {
            clearCombat(p.getUniqueId());
            return;
        }
        boolean wasInCombat = isInCombat(p);
        combatUntil.put(p.getUniqueId(), Long.valueOf(System.currentTimeMillis() + 30_000L));
        BossBar bar = combatBars.get(p.getUniqueId());
        if (bar == null) {
            bar = Bukkit.createBossBar(
                    ColorUtil.c("&#FF1744&l⚔ РЕЖИМ ПВП: &#FFD740&l30 сек. &8│ &cКоманды и выход запрещены!"),
                    BarColor.RED, BarStyle.SEGMENTED_10);
            bar.setProgress(1.0);
            bar.addPlayer(p);
            combatBars.put(p.getUniqueId(), bar);
            // Переставляем все полоски ивентов ПОД полоску ПвП — ПвП ВСЕГДА #1 СВЕРХУ!
            if (plugin.getBossEvents() != null) {
                plugin.getBossEvents().moveEventBarsBelowCombat(p);
            }
            if (!wasInCombat) {
                p.sendMessage(ColorUtil.c("&#FF1744⚔ &fВы вошли в &#FF1744&lРЕЖИМ ПВП &fна &#FFD74030 секунд&f! Все команды заблокированы."));
            }
        } else {
            bar.setTitle(ColorUtil.c("&#FF1744&l⚔ РЕЖИМ ПВП: &#FFD740&l30 сек. &8│ &cКоманды и выход запрещены!"));
            bar.setProgress(1.0);
            if (!bar.getPlayers().contains(p)) {
                bar.addPlayer(p);
                if (plugin.getBossEvents() != null) {
                    plugin.getBossEvents().moveEventBarsBelowCombat(p);
                }
            }
        }
    }

    public boolean isInCombat(Player p) {
        if (p == null || isOwnerBypassCombat(p)) return false;
        Long u = combatUntil.get(p.getUniqueId());
        return u != null && u.longValue() > System.currentTimeMillis();
    }

    public int getCombatSecondsLeft(Player p) {
        if (p == null || isOwnerBypassCombat(p)) return 0;
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
            bar.setTitle(ColorUtil.c("&#FF1744&l⚔ РЕЖИМ ПВП: &#FFD740&l" + sec + " сек. &8│ &cКоманды и выход запрещены!"));
            bar.setProgress(Math.max(0.0, Math.min(1.0, sec / 30.0)));
        }
    }

    public void updatePlayerHud(Player p) {
        if (p == null || !p.isOnline()) return;
        DataStore store = plugin.getStore();
        RankManager ranks = plugin.getRanks();
        HudCache cache = hudCaches.get(p.getUniqueId());
        if (cache == null) {
            cache = new HudCache();
            cache.cachedPing = safePing(p);
            hudCaches.put(p.getUniqueId(), cache);
        }
        boolean fpsBoost = store.isFpsBoost(p);
        int effTick = fpsBoost ? 0 : animTick;
        if (animTick % 4 == 0) {
            cache.cachedPing = safePing(p);
        }
        int ping = cache.cachedPing;
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        String ip = plugin.getConfig().getString("server-ip", "kinggrief.pickme.cloud");
        String titleAnim = ColorUtil.c(HEADER_FRAMES[(effTick / 3) % HEADER_FRAMES.length]);
        int activeBosses = plugin.getBossEvents() != null ? plugin.getBossEvents().getActiveEvents().size() : 0;

        String header = ColorUtil.c("\n" + HEADER_FRAMES[(effTick / 3) % HEADER_FRAMES.length]
                + "\n&#B3E5FC❄ Новогодний Сезон v6.4.0 · Anti-Lag FT & RW Edition ❄\n");
        String footer = ColorUtil.c("\n&#80D8FFОнлайн: &#00E676" + online + "&8/&f" + max
                + " &8| &#80D8FFПинг: &#FFD740" + ping + " мс"
                + " &8| &#FF5252Ивентов: &#FFE082" + activeBosses + " &7(/events)"
                + "\n&#00E676РТП: &a/rtp &8• &#FFD740Варпы: &e/warps &8• &#00E5FFFPS-Буст: &b/fps"
                + "\n&#FF4081Новогодний Магазин: &fkinggrief.vercel.app &8(&7IP: &f" + ip + "&8)\n");

        if (!header.equals(cache.lastHeader) || !footer.equals(cache.lastFooter)) {
            p.setPlayerListHeaderFooter(header, footer);
            cache.lastHeader = header;
            cache.lastFooter = footer;
        }

        RankManager.RankDef myRank = ranks.getRank(p);
        String animBadge = ranks.getAnimatedBadge(myRank, effTick);
        String animPref = ranks.getAnimatedPrefix(p, effTick);
        DataStore.Clan myClan = store.getClanOf(p.getName());
        String clanBadge = myClan != null ? " &8[&#B388FF" + myClan.tag + "&8]" : "";

        String listName = ColorUtil.c(animPref + p.getName() + clanBadge);
        if (!listName.equals(cache.lastListName)) {
            p.setPlayerListName(listName);
            cache.lastListName = listName;
        }

        ScoreboardManager sm = Bukkit.getScoreboardManager();
        if (sm == null) return;
        if (!store.isSbEnabled(p)) {
            if (p.getScoreboard() != sm.getMainScoreboard()) {
                p.setScoreboard(sm.getMainScoreboard());
            }
            return;
        }

        Scoreboard sb = boards.get(p.getUniqueId());
        Objective obj;
        if (sb == null) {
            sb = sm.getNewScoreboard();
            obj = sb.registerNewObjective("kg_sb", "dummy", titleAnim);
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            for (int i = 0; i < LINE_KEYS.length; i++) {
                Team t = sb.registerNewTeam("ln_" + i);
                t.addEntry(LINE_KEYS[i]);
                obj.getScore(LINE_KEYS[i]).setScore(LINE_KEYS.length - i);
            }
            boards.put(p.getUniqueId(), sb);
            p.setScoreboard(sb);
            cache.lastTitle = titleAnim;
        } else {
            obj = sb.getObjective("kg_sb");
            if (obj != null && !titleAnim.equals(cache.lastTitle)) {
                obj.setDisplayName(titleAnim);
                cache.lastTitle = titleAnim;
            }
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
            String rendered = ColorUtil.c(lines[i]);
            if (!rendered.equals(cache.lastLines[i])) {
                Team t = sb.getTeam("ln_" + i);
                if (t != null) {
                    t.setPrefix(rendered);
                    cache.lastLines[i] = rendered;
                }
            }
        }

        // Nametag teams: update only when changed (or every 2nd tick for smooth non-spammy badges)
        if (!fpsBoost || animTick % 4 == 0) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                RankManager.RankDef rOther = ranks.getRank(other);
                DataStore.Clan cOther = store.getClanOf(other.getName());
                String tName = String.format("r%02d_%s", Integer.valueOf(rOther.sortOrder),
                        other.getName().substring(0, Math.min(8, other.getName().length())));
                String prefNow = ColorUtil.c(ranks.getAnimatedPrefix(other, effTick));
                String suffNow = cOther != null ? ColorUtil.c(" &8[&#B388FF" + cOther.tag + "&8]") : "";
                String stateSig = prefNow + "|" + suffNow;
                if (!stateSig.equals(cache.lastTeamState.get(tName))) {
                    Team nt = sb.getTeam(tName);
                    if (nt == null) nt = sb.registerNewTeam(tName);
                    nt.setPrefix(prefNow);
                    nt.setSuffix(suffNow);
                    if (!nt.hasEntry(other.getName())) {
                        nt.addEntry(other.getName());
                    }
                    cache.lastTeamState.put(tName, stateSig);
                }
            }
        }
    }

    private void applyFlightTrails(Player p) {
        if (!p.isGliding()) return;
        String id = ItemFactory.getCustomId(p.getInventory().getChestplate());
        if (id == null) return;
        try {
            if ("elytra_phoenix".equals(id)) {
                p.getWorld().spawnParticle(Particle.FLAME, p.getLocation(), 2, 0.2, 0.2, 0.2, 0.02);
            } else if ("elytra_santa".equals(id) || "elytra_wind".equals(id)) {
                p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation(), 2, 0.25, 0.25, 0.25, 0.02);
            } else if ("elytra_vampire".equals(id)) {
                p.getWorld().spawnParticle(Particle.CRIMSON_SPORE, p.getLocation(), 3, 0.25, 0.25, 0.25, 0.02);
            } else if ("elytra_thunder".equals(id)) {
                p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation(), 2, 0.25, 0.25, 0.25, 0.05);
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
                giveEff(p, "resistance", 80, 0);
                giveEff(p, "strength", 80, 0);
            } else if ("shar_andromedy".equals(offId)) {
                giveEff(p, "speed", 80, 1);
                giveEff(p, "strength", 80, 1);
                giveEff(p, "jump_boost", 80, 1);
            } else if ("shar_chimery".equals(offId)) {
                giveEff(p, "strength", 80, 1);
                giveEff(p, "fire_resistance", 80, 0);
                giveEff(p, "regeneration", 80, 0);
            } else if ("shar_apollona".equals(offId)) {
                giveEff(p, "haste", 80, 1);
                giveEff(p, "speed", 80, 1);
                giveEff(p, "night_vision", 300, 0);
            } else if ("shar_aida".equals(offId)) {
                giveEff(p, "strength", 80, 1);
                giveEff(p, "fire_resistance", 80, 0);
            } else if ("shar_osirisa".equals(offId)) {
                bonusHp += 4.0;
                giveEff(p, "regeneration", 80, 1);
                giveEff(p, "resistance", 80, 0);
            } else if ("shar_aresa".equals(offId)) {
                giveEff(p, "strength", 80, 2);
                giveEff(p, "speed", 80, 0);
            } else if ("shar_santy".equals(offId)) {
                bonusHp += 12.0;
                giveEff(p, "strength", 80, 1);
                giveEff(p, "speed", 80, 1);
                giveEff(p, "regeneration", 80, 0);
            } else if ("tal_owner".equals(offId)) {
                bonusHp += 20.0;
                giveEff(p, "strength", 80, 2);
                giveEff(p, "speed", 80, 2);
                giveEff(p, "resistance", 80, 1);
                giveEff(p, "regeneration", 80, 1);
                giveEff(p, "fire_resistance", 90, 0);
                giveEff(p, "haste", 80, 1);
            } else if ("tal_karatelya".equals(offId)) {
                bonusHp += 6.0;
                giveEff(p, "strength", 80, 2);
                giveEff(p, "speed", 80, 1);
            } else if ("tal_zeusa".equals(offId)) {
                bonusHp += 6.0;
                giveEff(p, "strength", 80, 1);
                giveEff(p, "speed", 80, 1);
            } else if ("tal_draculy".equals(offId)) {
                bonusHp += 8.0;
                giveEff(p, "strength", 80, 1);
                giveEff(p, "night_vision", 300, 0);
            } else if ("tal_kolossa".equals(offId)) {
                bonusHp += 12.0;
                giveEff(p, "resistance", 80, 1);
                giveEff(p, "fire_resistance", 90, 0);
            } else if ("tal_zhneca".equals(offId)) {
                bonusHp += 6.0;
                giveEff(p, "strength", 80, 2);
                giveEff(p, "speed", 80, 1);
            } else if ("tal_severa".equals(offId)) {
                bonusHp += 8.0;
                giveEff(p, "speed", 80, 2);
                giveEff(p, "regeneration", 80, 1);
            } else if ("tal_bezdn".equals(offId)) {
                bonusHp += 8.0;
                giveEff(p, "strength", 80, 1);
                giveEff(p, "resistance", 80, 0);
            } else if ("tal_imperatora".equals(offId)) {
                bonusHp += 10.0;
                giveEff(p, "strength", 80, 1);
                giveEff(p, "speed", 80, 1);
                giveEff(p, "regeneration", 80, 1);
            } else if ("tal_krushitelya".equals(offId)) {
                bonusHp += 4.0;
                giveEff(p, "strength", 80, 1);
            } else if ("tal_grani".equals(offId)) {
                bonusHp += 6.0;
                giveEff(p, "resistance", 80, 0);
            } else if ("tal_dedala".equals(offId)) {
                giveEff(p, "speed", 80, 1);
                giveEff(p, "jump_boost", 80, 1);
            } else if ("tal_tritona".equals(offId)) {
                bonusHp += 4.0;
                giveEff(p, "regeneration", 80, 0);
            } else if ("tal_feniksa".equals(offId)) {
                bonusHp += 4.0;
                giveEff(p, "fire_resistance", 90, 0);
            } else if ("tal_echidny".equals(offId)) {
                giveEff(p, "strength", 80, 0);
                giveEff(p, "speed", 80, 0);
            } else if ("tal_stuzhi".equals(offId)) {
                bonusHp += 4.0;
                giveEff(p, "speed", 80, 1);
                giveEff(p, "regeneration", 80, 0);
            }
        }

        String chestId = ItemFactory.getCustomId(p.getInventory().getChestplate());
        if (chestId != null) {
            if ("elytra_wind".equals(chestId)) {
                giveEff(p, "speed", 80, 1);
                if (p.isSneaking() && !p.isOnGround()) giveEff(p, "slow_falling", 45, 0);
            } else if ("elytra_phoenix".equals(chestId)) {
                bonusHp += 4.0;
                giveEff(p, "fire_resistance", 90, 0);
            } else if ("elytra_vampire".equals(chestId)) {
                giveEff(p, "strength", 80, 0);
                giveEff(p, "night_vision", 300, 0);
            } else if ("elytra_thunder".equals(chestId)) {
                giveEff(p, "speed", 80, 1);
                if (p.isGliding()) giveEff(p, "strength", 80, 1);
            } else if ("elytra_titan".equals(chestId)) {
                bonusHp += 8.0;
                giveEff(p, "resistance", 80, 0);
            } else if ("elytra_santa".equals(chestId)) {
                bonusHp += 12.0;
                giveEff(p, "strength", 80, 1);
                giveEff(p, "speed", 80, 1);
                giveEff(p, "regeneration", 80, 0);
            }
        }

        // Проверка полного сета кастомной 3D брони (4/4 частей!)
        String fullSet = detectFullCustomArmorSet(p);
        if (fullSet != null) {
            spawnSetAura(p, fullSet);
            if ("owner".equals(fullSet)) {
                bonusHp += 24.0;
                giveEff(p, "strength", 80, 2);
                giveEff(p, "speed", 80, 2);
                giveEff(p, "resistance", 80, 1);
                giveEff(p, "regeneration", 80, 1);
                giveEff(p, "fire_resistance", 90, 0);
            } else if ("krush".equals(fullSet)) {
                bonusHp += 12.0;
                giveEff(p, "strength", 80, 1);
                giveEff(p, "fire_resistance", 90, 0);
            } else if ("dracula".equals(fullSet)) {
                bonusHp += 8.0;
                giveEff(p, "speed", 80, 1);
                giveEff(p, "night_vision", 300, 0);
            } else if ("frost".equals(fullSet)) {
                bonusHp += 12.0;
                giveEff(p, "speed", 80, 1);
                giveEff(p, "regeneration", 80, 0);
            } else if ("zeus".equals(fullSet)) {
                bonusHp += 8.0;
                giveEff(p, "speed", 80, 2);
                giveEff(p, "jump_boost", 80, 1);
            } else if ("phantom".equals(fullSet)) {
                bonusHp += 6.0;
                giveEff(p, "speed", 80, 2);
                giveEff(p, "strength", 80, 1);
            }
        }

        ColorUtil.setMaxHealthSafe(p, 20.0 + bonusHp);
    }

    /** v6.2: АУРА ПОЛНОГО СЕТА — цветные частицы вокруг игрока, броню ВИДНО издалека! */
    private static void spawnSetAura(Player p, String set) {
        try {
            org.bukkit.Color c;
            if ("owner".equals(set)) c = org.bukkit.Color.fromRGB(255, 215, 0);
            else if ("krush".equals(set)) c = org.bukkit.Color.fromRGB(255, 109, 0);
            else if ("dracula".equals(set)) c = org.bukkit.Color.fromRGB(225, 29, 72);
            else if ("frost".equals(set)) c = org.bukkit.Color.fromRGB(128, 216, 255);
            else if ("zeus".equals(set)) c = org.bukkit.Color.fromRGB(250, 204, 21);
            else c = org.bukkit.Color.fromRGB(168, 85, 247);
            org.bukkit.Location base = p.getLocation().clone().add(0.0, 0.4, 0.0);
            p.getWorld().spawnParticle(ColorUtil.particle("DUST", "REDSTONE"), base, 10, 0.45, 0.7, 0.45, 0.0,
                    new org.bukkit.Particle.DustOptions(c, 1.2f));
        } catch (Throwable ignored) {}
    }

    public static String detectFullCustomArmorSet(Player p) {
        if (p == null) return null;
        String h = ItemFactory.getCustomId(p.getInventory().getHelmet());
        String c = ItemFactory.getCustomId(p.getInventory().getChestplate());
        String l = ItemFactory.getCustomId(p.getInventory().getLeggings());
        String b = ItemFactory.getCustomId(p.getInventory().getBoots());
        if (h == null || c == null || l == null || b == null) return null;
        if (!h.startsWith("armor_") || !h.endsWith("_helmet")) return null;
        String key = h.substring("armor_".length(), h.length() - "_helmet".length());
        if (("armor_" + key + "_chestplate").equals(c)
                && ("armor_" + key + "_leggings").equals(l)
                && ("armor_" + key + "_boots").equals(b)) {
            return key;
        }
        return null;
    }

    private void giveEff(Player p, String key, int ticks, int amp) {
        PotionEffectType pet = PotionEffectType.getByKey(NamespacedKey.minecraft(key));
        if (pet != null) {
            // particles=false so custom buffs NEVER obscure screen or hurt FPS!
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
