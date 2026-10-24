package ru.reallyworld.ultimate.core.module;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;
import ru.reallyworld.ultimate.core.api.ReallyModule;
import ru.reallyworld.ultimate.core.config.ConfigManager;
import ru.reallyworld.ultimate.core.config.RankDefinition;
import ru.reallyworld.ultimate.core.storage.model.ClanData;
import ru.reallyworld.ultimate.core.storage.model.PlayerProfile;
import ru.reallyworld.ultimate.core.util.TextUtil;

import java.util.*;

/**
 * Flicker-Free Native Scoreboard & Animated TAB Header/Footer/Prefix Engine.
 * Automatically yields to external TAB plugin if installed, or runs natively standalone!
 */
public final class ScoreboardTabModule implements ReallyModule {
    private static final ChatColor[] LINE_ENTRIES = ChatColor.values();

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final PlayerModule playerModule;
    private final ClanModule clanModule;
    private final CombatModule combatModule;
    private final EventsModule eventsModule;

    private BukkitTask updateTask;
    private int animFrame = 0;
    private volatile boolean enabled;

    public ScoreboardTabModule(
            JavaPlugin plugin,
            ConfigManager configManager,
            PlayerModule playerModule,
            ClanModule clanModule,
            CombatModule combatModule,
            EventsModule eventsModule
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playerModule = playerModule;
        this.clanModule = clanModule;
        this.combatModule = combatModule;
        this.eventsModule = eventsModule;
    }

    @Override
    public String id() { return "scoreboard-tab"; }

    @Override
    public String displayName() { return "ScoreboardTabModule (Hex Sidebar & TAB)"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void enable() {
        this.enabled = true;
        if (plugin != null && Bukkit.getServer() != null) {
            int ticks = Math.max(10, configManager.get("scoreboard_tab.yml").getInt("scoreboard.update-ticks", 20));
            this.updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAllPlayers, 20L, ticks);
        }
    }

    @Override
    public void reload() {
        enable();
    }

    @Override
    public void disable() {
        this.enabled = false;
        if (updateTask != null) updateTask.cancel();
    }

    private void tickAllPlayers() {
        if (!enabled) return;
        animFrame++;
        var cfg = configManager.get("scoreboard_tab.yml");
        boolean sbEnabled = cfg.getBoolean("scoreboard.enabled", true);
        boolean externalTabPresent = Bukkit.getPluginManager().isPluginEnabled("TAB");
        boolean tabEnabled = cfg.getBoolean("tab.enabled", true) && !externalTabPresent;

        List<String> titles = cfg.getStringList("scoreboard.animated-titles");
        String titleRaw = titles.isEmpty() ? "<bold><#FBBC00>REALLYWORLD</bold>" : titles.get(animFrame % titles.size());
        List<String> rawLines = cfg.getStringList("scoreboard.lines");

        for (Player player : Bukkit.getOnlinePlayers()) {
            Map<String, String> ph = buildPlaceholders(player);
            if (sbEnabled && playerModule.getProfile(player).scoreboardEnabled()) {
                renderSidebar(player, titleRaw, rawLines, ph);
            }
            if (tabEnabled) {
                renderTab(player, cfg.getStringList("tab.header"), cfg.getStringList("tab.footer"), ph);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void renderSidebar(Player player, String titleRaw, List<String> lines, Map<String, String> ph) {
        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        if (mgr == null) return;
        Scoreboard board = player.getScoreboard();
        if (board.equals(mgr.getMainScoreboard())) {
            board = mgr.getNewScoreboard();
            player.setScoreboard(board);
        }

        Objective obj = board.getObjective("rw_sb");
        if (obj == null) {
            obj = board.registerNewObjective("rw_sb", Criteria.DUMMY, TextUtil.mm(titleRaw));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            try {
                obj.numberFormat(NumberFormat.blank());
            } catch (Throwable ignored) {}
        } else {
            obj.displayName(TextUtil.mm(titleRaw));
        }

        int maxLines = Math.min(15, lines.size());
        for (int i = 0; i < maxLines; i++) {
            String entryKey = LINE_ENTRIES[i].toString();
            String teamName = "rw_line_" + i;
            Team team = board.getTeam(teamName);
            if (team == null) {
                team = board.registerNewTeam(teamName);
                team.addEntry(entryKey);
            }
            Component lineComp = TextUtil.mm(lines.get(i), ph);
            team.prefix(lineComp);
            obj.getScore(entryKey).setScore(maxLines - i);
        }
    }

    private void renderTab(Player viewer, List<String> headerLines, List<String> footerLines, Map<String, String> ph) {
        Component header = TextUtil.mm(String.join("\n", headerLines), ph);
        Component footer = TextUtil.mm(String.join("\n", footerLines), ph);
        viewer.sendPlayerListHeaderAndFooter(header, footer);

        RankDefinition rank = playerModule.getRank(viewer);
        String clanPart = clanModule.getPlayerClan(viewer.getUniqueId())
                .map(c -> "<dark_gray>[<#FBBC00>" + c.tag() + "</#FBBC00>]</dark_gray> ")
                .orElse("");
        PlayerProfile prof = playerModule.getProfile(viewer);
        String name = (prof.nickname() != null && !prof.nickname().isBlank()) ? prof.nickname() : viewer.getName();
        viewer.playerListName(TextUtil.mm(clanPart + rank.tabPrefix() + rank.color() + name));
    }

    private Map<String, String> buildPlaceholders(Player p) {
        PlayerProfile prof = playerModule.getProfile(p);
        RankDefinition rank = playerModule.getRank(p);
        String clanName = clanModule.getPlayerClan(p.getUniqueId()).map(ClanData::name).orElse("Нет");
        long combatRem = combatModule.getCombatRemainingSeconds(p.getUniqueId());
        String combatStr = combatRem > 0 ? "<#FF4500>В бою (" + combatRem + "с)" : "<#55FF55>Безопасно";

        double tps = 20.0;
        try { tps = Math.min(20.0, Math.round(Bukkit.getTPS()[0] * 10.0) / 10.0); } catch (Throwable ignored) {}

        Map<String, String> map = new HashMap<>();
        map.put("mode", configManager.main().getString("server.mode-name", "ГРИФ #1"));
        map.put("player", p.getName());
        map.put("rank", rank.displayName());
        map.put("clan", clanName);
        map.put("coins", TextUtil.formatAmount(prof.coins()));
        map.put("rilliks", TextUtil.formatAmount(prof.rilliks()));
        map.put("kills", String.valueOf(prof.kills()));
        map.put("deaths", String.valueOf(prof.deaths()));
        map.put("kd", String.valueOf(prof.kdRatio()));
        map.put("combat", combatStr);
        map.put("event", eventsModule.getStatusSummary());
        map.put("online", String.valueOf(Bukkit.getOnlinePlayers().size()));
        map.put("max_online", String.valueOf(Bukkit.getMaxPlayers()));
        map.put("ping", String.valueOf(p.getPing()));
        map.put("tps", String.valueOf(tps));
        map.put("domain", configManager.main().getString("server.domain", "play.reallyworld-local.ru"));
        return map;
    }
}
