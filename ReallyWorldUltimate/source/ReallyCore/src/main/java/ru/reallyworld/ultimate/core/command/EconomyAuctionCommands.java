package ru.reallyworld.ultimate.core.command;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import ru.reallyworld.ultimate.core.ReallyCorePlugin;
import ru.reallyworld.ultimate.core.api.CurrencyType;
import ru.reallyworld.ultimate.core.storage.model.PlayerProfile;
import ru.reallyworld.ultimate.core.storage.repository.PlayerRepository.LeaderboardEntry;
import ru.reallyworld.ultimate.core.util.TextUtil;

import java.util.*;

public final class EconomyAuctionCommands implements CommandExecutor, TabCompleter {
    private final ReallyCorePlugin plugin;

    public EconomyAuctionCommands(ReallyCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        switch (name) {
            case "bal" -> {
                PlayerProfile target;
                if (args.length > 0) {
                    target = plugin.players().findProfileByName(args[0]).orElse(null);
                    if (target == null) {
                        plugin.locale().send(sender, "player-not-found", Map.of("player", args[0]));
                        return true;
                    }
                    plugin.locale().send(sender, "economy.balance-other", Map.of(
                            "player", target.username(),
                            "coins", TextUtil.formatAmount(target.coins()),
                            "rilliks", TextUtil.formatAmount(target.rilliks())
                    ));
                } else {
                    if (!(sender instanceof Player p)) {
                        plugin.locale().send(sender, "player-only");
                        return true;
                    }
                    target = plugin.players().getProfile(p);
                    plugin.locale().send(sender, "economy.balance-self", Map.of(
                            "coins", TextUtil.formatAmount(target.coins()),
                            "rilliks", TextUtil.formatAmount(target.rilliks()),
                            "tokens", TextUtil.formatAmount(target.tokens())
                    ));
                }
            }
            case "pay" -> {
                if (!(sender instanceof Player p)) {
                    plugin.locale().send(sender, "player-only");
                    return true;
                }
                if (args.length < 2) {
                    plugin.locale().sendRaw(p, "<yellow>Использование: /pay <игрок> <сумма>");
                    return true;
                }
                Optional<PlayerProfile> target = plugin.players().findProfileByName(args[0]);
                if (target.isEmpty()) {
                    plugin.locale().send(p, "player-not-found", Map.of("player", args[0]));
                    return true;
                }
                long amount = parsePositiveLong(args[1]);
                if (amount <= 0) {
                    plugin.locale().send(p, "invalid-amount");
                    return true;
                }
                var res = plugin.economy().pay(p, target.get().uuid(), target.get().username(), amount);
                if (!res.success()) {
                    switch (res.failureReason() == null ? "" : res.failureReason()) {
                        case "SELF_TRANSFER" -> plugin.locale().send(p, "economy.pay-self");
                        case "INSUFFICIENT_FUNDS" -> plugin.locale().send(p, "economy.not-enough-coins", Map.of("needed", TextUtil.formatAmount(amount)));
                        default -> plugin.locale().sendRaw(p, "<red>Сумма перевода вне допустимых границ!");
                    }
                    return true;
                }
                plugin.locale().send(p, "economy.pay-sent", Map.of(
                        "amount", TextUtil.formatAmount(amount),
                        "player", target.get().username(),
                        "fee", String.valueOf(plugin.configs().get("economy.yml").getInt("transfer.fee-percent", 3))
                ));
                Player onlineRec = Bukkit.getPlayer(target.get().uuid());
                if (onlineRec != null) {
                    plugin.locale().send(onlineRec, "economy.pay-received", Map.of(
                            "amount", TextUtil.formatAmount(res.netReceived()),
                            "player", p.getName()
                    ));
                }
            }
            case "baltop" -> {
                List<LeaderboardEntry> top = plugin.statistics().getTopPlayers("coins");
                sender.sendMessage(TextUtil.mm("<bold><#FBBC00>=== ТОП-10 БОГАЧЕЙ СЕРВЕРА ===</bold>"));
                for (int i = 0; i < top.size(); i++) {
                    LeaderboardEntry r = top.get(i);
                    sender.sendMessage(TextUtil.mm(" <#FBBC00>#" + (i + 1) + " <white>" + r.username()
                            + " <dark_gray>— <#55FF55>" + TextUtil.formatAmount(r.value()) + " ⛃"));
                }
            }
            case "eco", "rilliks" -> {
                if (!sender.hasPermission("reallycore.admin")) {
                    plugin.locale().send(sender, "no-permission");
                    return true;
                }
                if (args.length < 3) {
                    plugin.locale().sendRaw(sender, "<yellow>Использование: /" + name + " <give|take|set> <игрок> <сумма>");
                    return true;
                }
                Optional<PlayerProfile> target = plugin.players().findProfileByName(args[1]);
                if (target.isEmpty()) {
                    plugin.locale().send(sender, "player-not-found", Map.of("player", args[1]));
                    return true;
                }
                long amt = parsePositiveLong(args[2]);
                if (amt < 0) {
                    plugin.locale().send(sender, "invalid-amount");
                    return true;
                }
                CurrencyType curr = "rilliks".equals(name) ? CurrencyType.RILLIKS : CurrencyType.COINS;
                switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "give" -> plugin.economy().deposit(target.get().uuid(), curr, amt, "ADMIN_GIVE", sender.getName());
                    case "take" -> plugin.economy().withdraw(target.get().uuid(), curr, amt, "ADMIN_TAKE", sender.getName());
                    case "set" -> plugin.economy().setBalance(target.get().uuid(), curr, amt, sender.getName(), "Admin set");
                    default -> {
                        plugin.locale().sendRaw(sender, "<red>Доступные действия: give, take, set");
                        return true;
                    }
                }
                plugin.locale().sendRaw(sender, "<green>Баланс " + curr.displayName() + " игрока <white>"
                        + target.get().username() + "</white> обновлён!");
            }
            case "shop" -> {
                if (sender instanceof Player p) plugin.menus().openShopHome(p);
                else plugin.locale().send(sender, "player-only");
            }
            case "buyer" -> {
                if (sender instanceof Player p) plugin.menus().openBuyer(p);
                else plugin.locale().send(sender, "player-only");
            }
            case "sell" -> {
                if (!(sender instanceof Player p)) {
                    plugin.locale().send(sender, "player-only");
                    return true;
                }
                String mode = args.length == 0 ? "hand" : args[0].toLowerCase(Locale.ROOT);
                if ("all".equals(mode)) {
                    plugin.economy().sellAllInventory(p, null, false);
                } else {
                    plugin.economy().sellHand(p, false);
                }
            }
            case "ah" -> {
                if (!(sender instanceof Player p)) {
                    plugin.locale().send(sender, "player-only");
                    return true;
                }
                if (args.length == 0) {
                    plugin.menus().openAuction(p, "ALL", null, "NEWEST", 0);
                    return true;
                }
                switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "sell" -> {
                        if (args.length < 2) {
                            plugin.locale().sendRaw(p, "<yellow>Использование: /ah sell <цена>");
                            return true;
                        }
                        long price = parsePositiveLong(args[1]);
                        if (price <= 0) {
                            plugin.locale().send(p, "invalid-amount");
                            return true;
                        }
                        plugin.auction().sellFromMainHand(p, price);
                    }
                    case "search" -> {
                        String q = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : null;
                        plugin.menus().openAuction(p, "ALL", q, "NEWEST", 0);
                    }
                    case "history" -> plugin.menus().openAuctionHistory(p);
                    case "my", "expired" -> plugin.menus().openAuctionMyItems(p);
                    default -> plugin.menus().openAuction(p, "ALL", null, "NEWEST", 0);
                }
            }
        }
        return true;
    }

    private long parsePositiveLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            return switch (name) {
                case "sell" -> List.of("hand", "all");
                case "ah" -> List.of("sell", "search", "history", "my");
                case "eco", "rilliks" -> sender.hasPermission("reallycore.admin") ? List.of("give", "take", "set") : List.of();
                case "pay", "bal" -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                default -> List.of();
            };
        }
        if (args.length == 2 && ("eco".equals(name) || "rilliks".equals(name))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}
