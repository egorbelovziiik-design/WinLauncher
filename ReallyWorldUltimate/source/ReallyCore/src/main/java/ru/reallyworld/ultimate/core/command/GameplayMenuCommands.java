package ru.reallyworld.ultimate.core.command;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import ru.reallyworld.ultimate.core.ReallyCorePlugin;
import ru.reallyworld.ultimate.core.module.KitsModule;
import ru.reallyworld.ultimate.core.storage.model.PlayerProfile;

import java.util.List;
import java.util.Locale;

public final class GameplayMenuCommands implements CommandExecutor, TabCompleter {
    private final ReallyCorePlugin plugin;

    public GameplayMenuCommands(ReallyCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.locale().send(sender, "player-only");
            return true;
        }
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "menu" -> plugin.menus().openMainMenu(player);
            case "donate" -> plugin.menus().openDonate(player);
            case "kits" -> plugin.menus().openKits(player);
            case "kit" -> {
                if (args.length == 0) {
                    plugin.menus().openKits(player);
                } else {
                    plugin.kits().claimKit(player, args[0]);
                }
            }
            case "cases" -> plugin.menus().openCases(player);
            case "profile" -> plugin.menus().openProfile(player);
            case "stats" -> plugin.menus().openStats(player);
            case "daily" -> plugin.rewards().claimDaily(player);
            case "rewards" -> plugin.menus().openRewards(player);
            case "events" -> plugin.menus().openEvents(player);
            case "quests" -> plugin.menus().openQuests(player);
            case "bp" -> plugin.menus().openBattlePass(player, 0);
            case "top" -> plugin.menus().openTop(player, args.length > 0 ? args[0] : "coins");
            case "sb" -> {
                PlayerProfile prof = plugin.players().getProfile(player);
                prof.setScoreboardEnabled(!prof.scoreboardEnabled());
                if (!prof.scoreboardEnabled() && Bukkit.getScoreboardManager() != null) {
                    player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
                }
                plugin.locale().sendRaw(player, "<gray>Боковая панель (Scoreboard): "
                        + (prof.scoreboardEnabled() ? "<green>ВКЛЮЧЕНА" : "<red>СКРЫТА"));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1 && "kit".equalsIgnoreCase(cmd.getName())) {
            String pref = args[0].toLowerCase(Locale.ROOT);
            return plugin.kits().getKits().stream()
                    .map(KitsModule.KitDefinition::id)
                    .filter(id -> id.startsWith(pref))
                    .toList();
        }
        if (args.length == 1 && "top".equalsIgnoreCase(cmd.getName())) {
            return List.of("coins", "kills", "clans", "playtime_seconds", "boss_kills");
        }
        return List.of();
    }
}
