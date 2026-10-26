package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
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
    private int frame = 0;

    public HudManager(KingGriefCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.getConfig().getBoolean("enable-builtin-tab-and-scoreboard", true)) return;
                if (Bukkit.getPluginManager().isPluginEnabled("TAB")) return;
                frame = (frame + 1) % HEADER_FRAMES.length;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    plugin.getStore().addPlaySeconds(p.getName(), 1L);
                    updatePlayerHud(p);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void removePlayer(Player p) {
        boards.remove(p.getUniqueId());
    }

    public void updatePlayerHud(Player p) {
        if (p == null || !p.isOnline()) return;
        DataStore store = plugin.getStore();
        RankManager ranks = plugin.getRanks();
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        int ping = safePing(p);
        String ip = plugin.getConfig().getString("server-ip", "kinggrief.pickme.cloud");
        String titleAnim = HEADER_FRAMES[frame];
        int activeBosses = plugin.getBossEvents() != null ? plugin.getBossEvents().getActiveEvents().size() : 0;

        String header = ColorUtil.c("\n" + titleAnim + "\n&#B3E5FCЗимний Новогодний Сезон v4.0 · Копия ReallyWorld\n");
        String footer = ColorUtil.c("\n&#80D8FFОнлайн: &#00E676" + online + "&8/&f" + max
                + " &8| &#80D8FFПинг: &#FFD740" + ping + " мс"
                + " &8| &#FF5252Активных Боссов: &#FFE082" + activeBosses + " &7(/events)"
                + "\n&#FFD740Донат, Кейсы и LP-Редактор: &#FF4081kinggrief.vercel.app"
                + "\n&#80D8FFIP сервера: &f" + ip + "\n");
        p.setPlayerListHeaderFooter(header, footer);

        RankManager.RankDef myRank = ranks.getRank(p);
        String effPrefix = ranks.getEffectivePrefix(p);
        DataStore.Clan myClan = store.getClanOf(p.getName());
        String clanBadge = myClan != null ? " &8[&#B388FF" + myClan.tag + "&8]" : "";
        p.setPlayerListName(ColorUtil.c(effPrefix + p.getName() + clanBadge));

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
                " \uE976 &#FFD740Ранг: &f" + myRank.shortLabel,
                " \uE973 &#B388FFКлан: " + clanStr,
                "",
                " \uE971 &#FFEA00Монеты: &a" + coinsStr,
                " \uE972 &#00E5FFРилики: &b" + relicsStr + " ❖ &8(&b" + snow + "❄&8)",
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
            nt.setPrefix(ColorUtil.c(ranks.getEffectivePrefix(other)));
            nt.setSuffix(cOther != null ? ColorUtil.c(" &8[&#B388FF" + cOther.tag + "&8]") : "");
            if (!nt.hasEntry(other.getName())) {
                nt.addEntry(other.getName());
            }
        }
    }

    private int safePing(Player p) {
        try {
            return Math.max(1, p.getPing());
        } catch (Throwable t) {
            return 20;
        }
    }
}
