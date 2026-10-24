package ru.reallyworld.ultimate.core.command;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import ru.reallyworld.ultimate.core.ReallyCorePlugin;
import ru.reallyworld.ultimate.core.storage.model.ClanData;
import ru.reallyworld.ultimate.core.util.TextUtil;

import java.util.*;

public final class ClanCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = List.of(
            "create", "invite", "accept", "deny", "leave", "kick", "promote", "demote",
            "disband", "info", "top", "chat", "ally", "enemy", "home", "sethome", "pvp", "deposit", "withdraw"
    );

    private final ReallyCorePlugin plugin;

    public ClanCommand(ReallyCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.locale().send(sender, "player-only");
            return true;
        }
        if (args.length == 0) {
            plugin.menus().openClan(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> {
                if (args.length < 3) {
                    plugin.locale().sendRaw(player, "<yellow>Использование: /clan create <название> <тег>");
                    return true;
                }
                plugin.clans().createClan(player, args[1], args[2]);
            }
            case "invite" -> {
                if (args.length < 2) {
                    plugin.locale().sendRaw(player, "<yellow>Использование: /clan invite <игрок>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    plugin.locale().send(player, "player-not-found", Map.of("player", args[1]));
                    return true;
                }
                plugin.clans().invitePlayer(player, target);
            }
            case "accept" -> plugin.clans().respondInvite(player, true);
            case "deny" -> plugin.clans().respondInvite(player, false);
            case "leave" -> plugin.clans().leaveClan(player);
            case "kick" -> {
                if (args.length < 2) {
                    plugin.locale().sendRaw(player, "<yellow>Использование: /clan kick <игрок>");
                    return true;
                }
                plugin.clans().kickMember(player, args[1]);
            }
            case "promote" -> {
                if (args.length >= 2) plugin.clans().changeRole(player, args[1], true);
            }
            case "demote" -> {
                if (args.length >= 2) plugin.clans().changeRole(player, args[1], false);
            }
            case "disband" -> plugin.clans().disband(player);
            case "sethome" -> plugin.clans().setClanHome(player);
            case "home" -> plugin.clans().teleportClanHome(player);
            case "pvp" -> plugin.clans().toggleFriendlyFire(player);
            case "ally" -> {
                if (args.length >= 2) plugin.clans().setRelation(player, args[1], "ALLY");
            }
            case "enemy" -> {
                if (args.length >= 2) plugin.clans().setRelation(player, args[1], "ENEMY");
            }
            case "deposit" -> {
                if (args.length >= 2) {
                    try { plugin.clans().depositBank(player, Long.parseLong(args[1])); }
                    catch (NumberFormatException e) { plugin.locale().send(player, "invalid-amount"); }
                }
            }
            case "withdraw" -> {
                if (args.length >= 2) {
                    try { plugin.clans().withdrawBank(player, Long.parseLong(args[1])); }
                    catch (NumberFormatException e) { plugin.locale().send(player, "invalid-amount"); }
                }
            }
            case "chat", "c" -> {
                if (args.length >= 2) {
                    plugin.clans().sendClanChat(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                }
            }
            case "top" -> plugin.menus().openTop(player, "clans");
            case "info" -> {
                Optional<ClanData> cOpt = args.length >= 2
                        ? plugin.clans().findClanByNameOrTag(args[1])
                        : plugin.clans().getPlayerClan(player.getUniqueId());
                if (cOpt.isEmpty()) {
                    plugin.locale().send(player, "clan.not-in-clan");
                    return true;
                }
                ClanData c = cOpt.get();
                player.sendMessage(TextUtil.mm("<bold><#FBBC00>=== КЛАН [" + c.tag() + "] " + c.name() + " ===</bold>"));
                player.sendMessage(TextUtil.mm(" <gray>Рейтинг: <#FF6A00>" + c.ratingPoints() + " <dark_gray>| <gray>Казна: <#FBBC00>" + TextUtil.formatAmount(c.bankCoins()) + " ⛃"));
                player.sendMessage(TextUtil.mm(" <gray>Участники (" + c.members().size() + "): <white>"
                        + String.join(", ", c.members().stream().map(ClanData.ClanMember::playerName).toList())));
            }
            default -> plugin.menus().openClan(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            String pref = args[0].toLowerCase(Locale.ROOT);
            return SUBS.stream().filter(s -> s.startsWith(pref)).toList();
        }
        if (args.length == 2 && List.of("invite", "kick", "promote", "demote").contains(args[0].toLowerCase(Locale.ROOT))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}
