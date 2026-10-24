package ru.reallyworld.ultimate.core.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.reallyworld.ultimate.core.ReallyCorePlugin;
import ru.reallyworld.ultimate.core.api.ReallyModule;
import ru.reallyworld.ultimate.core.util.TextUtil;

import java.lang.management.ManagementFactory;
import java.util.*;

/**
 * Administrative & Diagnostics Command Suite:
 * /reallycore <help|status|reload|debug|version|modules|diagnostics|buildspawn|giveitem|givekey|startevent|stopevent|setrank>
 */
public final class ReallyCoreCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = List.of(
            "help", "status", "reload", "debug", "version", "modules", "diagnostics",
            "buildspawn", "giveitem", "givekey", "startevent", "stopevent", "setrank"
    );

    private final ReallyCorePlugin plugin;

    public ReallyCoreCommand(ReallyCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("reallycore.admin")) {
            plugin.locale().send(sender, "no-permission");
            return true;
        }
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "version" -> {
                plugin.locale().sendRaw(sender, "<bold><#FBBC00>ReallyCore Ultimate</bold> <white>v"
                        + plugin.getPluginMeta().getVersion() + "</white> (Java " + Runtime.version().feature()
                        + ", API 1.21.4–1.21.11)");
            }
            case "status", "modules" -> {
                sender.sendMessage(TextUtil.mm("<bold><#FBBC00>=== МОДУЛИ REALLYCORE (" + plugin.modules().size() + ") ===</bold>"));
                for (ReallyModule m : plugin.modules()) {
                    String badge = m.isEnabled() ? "<#55FF55>● ВКЛ" : "<red>○ ВЫКЛ";
                    sender.sendMessage(TextUtil.mm(" " + badge + " <white>" + m.displayName()
                            + " <dark_gray>— <gray>" + m.healthStatus()));
                }
            }
            case "diagnostics" -> {
                Runtime rt = Runtime.getRuntime();
                long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
                long maxMb = rt.maxMemory() / (1024 * 1024);
                long upSec = ManagementFactory.getRuntimeMXBean().getUptime() / 1000L;
                double tps = 20.0;
                try { tps = Math.min(20.0, Math.round(Bukkit.getTPS()[0] * 100.0) / 100.0); } catch (Throwable ignored) {}
                sender.sendMessage(TextUtil.mm("<bold><#FBBC00>=== ДИАГНОСТИКА СЕРВЕРА ===</bold>"));
                sender.sendMessage(TextUtil.mm(" <gray>Версия ядра: <white>" + Bukkit.getName() + " " + Bukkit.getMinecraftVersion()));
                sender.sendMessage(TextUtil.mm(" <gray>TPS (1m): <#55FF55>" + tps + " <dark_gray>| <gray>Аптайм: <white>" + TextUtil.formatDuration(upSec)));
                sender.sendMessage(TextUtil.mm(" <gray>Память JVM: <#FBBC00>" + usedMb + " MB / " + maxMb + " MB"));
                sender.sendMessage(TextUtil.mm(" <gray>Хранилище БД: <#55FFFF>" + plugin.storage().healthStatus()));
                sender.sendMessage(TextUtil.mm(" <gray>Онлайн игроков: <white>" + Bukkit.getOnlinePlayers().size() + " <dark_gray>| <gray>Активный ивент: <white>" + plugin.events().getStatusSummary()));
            }
            case "debug" -> {
                boolean next = !plugin.isDebugMode();
                plugin.setDebugMode(next);
                plugin.locale().sendRaw(sender, "<gray>Режим отладки ReallyCore: " + (next ? "<#55FF55>ВКЛЮЧЁН" : "<red>ВЫКЛЮЧЕН"));
            }
            case "reload" -> {
                long ms = plugin.reloadAllSafely();
                plugin.locale().send(sender, "reload-complete", Map.of("ms", String.valueOf(ms)));
            }
            case "buildspawn" -> {
                plugin.spawnWorld().buildSpawnStructure(
                        sender instanceof Player p ? p.getWorld() : Bukkit.getWorlds().getFirst());
                plugin.spawnWorld().respawnNpcsAndHolograms();
                plugin.locale().sendRaw(sender, "<green>Спавн, Колизей, Автошахта, 8 NPC и 7 голограмм успешно построены!");
            }
            case "giveitem" -> {
                // /reallycore giveitem <player> <category> <id> [amount]
                if (args.length < 4) {
                    plugin.locale().sendRaw(sender, "<yellow>Использование: /reallycore giveitem <игрок> <SPHERE|TALISMAN|ARTIFACT|WEAPON|ARMOR|CONSUMABLE> <id> [кол-во]");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    plugin.locale().send(sender, "player-not-found", Map.of("player", args[1]));
                    return true;
                }
                int amt = args.length >= 5 ? Math.max(1, Integer.parseInt(args[4])) : 1;
                Optional<ItemStack> built = plugin.customItems().createCustomItem(args[2], args[3], amt);
                if (built.isEmpty()) {
                    plugin.locale().sendRaw(sender, "<red>Кастомный предмет не найден: " + args[2] + ":" + args[3]);
                    return true;
                }
                target.getInventory().addItem(built.get());
                plugin.locale().sendRaw(sender, "<green>Выдан предмет <white>" + args[2] + ":" + args[3] + " x" + amt + "</white> игроку " + target.getName());
            }
            case "givekey" -> {
                if (args.length < 4) {
                    plugin.locale().sendRaw(sender, "<yellow>Использование: /reallycore givekey <игрок> <caseId> <кол-во>");
                    return true;
                }
                var prof = plugin.players().findProfileByName(args[1]);
                if (prof.isEmpty()) {
                    plugin.locale().send(sender, "player-not-found", Map.of("player", args[1]));
                    return true;
                }
                int amt = Math.max(1, Integer.parseInt(args[3]));
                int total = plugin.cases().giveKeys(prof.get().uuid(), args[2], amt);
                plugin.locale().sendRaw(sender, "<green>Выдано <white>" + amt + "</white> ключей кейса <gold>" + args[2]
                        + "</gold> игроку " + prof.get().username() + " (всего: " + total + ").");
            }
            case "startevent" -> {
                if (args.length < 2) {
                    plugin.locale().sendRaw(sender, "<yellow>Использование: /reallycore startevent <id_ивента_или_босса>");
                    return true;
                }
                boolean ok = plugin.events().startWorldEvent(args[1]);
                plugin.locale().sendRaw(sender, ok ? "<green>Событие «" + args[1] + "» запущено!" : "<red>Неизвестное событие: " + args[1]);
            }
            case "stopevent" -> {
                plugin.events().stopActiveEvent(true);
                plugin.events().despawnActiveBoss();
                plugin.locale().sendRaw(sender, "<yellow>Все активные события и боссы остановлены.");
            }
            case "setrank" -> {
                if (args.length < 3) {
                    plugin.locale().sendRaw(sender, "<yellow>Использование: /reallycore setrank <игрок> <ранг>");
                    return true;
                }
                var prof = plugin.players().findProfileByName(args[1]);
                if (prof.isEmpty()) {
                    plugin.locale().send(sender, "player-not-found", Map.of("player", args[1]));
                    return true;
                }
                plugin.players().setPlayerRank(prof.get().uuid(), prof.get().username(), args[2]);
                Player online = Bukkit.getPlayer(prof.get().uuid());
                if (online != null) plugin.integrations().syncRankToLuckPermsIfPresent(online, args[2]);
                plugin.locale().sendRaw(sender, "<green>Ранг игрока <white>" + prof.get().username()
                        + "</white> изменён на <gold>" + args[2].toUpperCase(Locale.ROOT) + "</gold>!");
            }
            default -> {
                sender.sendMessage(TextUtil.mm("<bold><#FBBC00>=== КОМАНДЫ АДМИНИСТРИРОВАНИЯ REALLYCORE ===</bold>"));
                sender.sendMessage(TextUtil.mm(" <#FF8C00>/rc status <dark_gray>— <gray>Состояние всех 19 модулей"));
                sender.sendMessage(TextUtil.mm(" <#FF8C00>/rc diagnostics <dark_gray>— <gray>TPS, память JVM, SQL статус"));
                sender.sendMessage(TextUtil.mm(" <#FF8C00>/rc reload <dark_gray>— <gray>Безопасная перезагрузка YAML без рестарта"));
                sender.sendMessage(TextUtil.mm(" <#FF8C00>/rc giveitem <ник> <тип> <id> [кол-во] <dark_gray>— <gray>Выдать кастомный предмет"));
                sender.sendMessage(TextUtil.mm(" <#FF8C00>/rc givekey <ник> <кейс> <кол-во> <dark_gray>— <gray>Выдать ключи кейса"));
                sender.sendMessage(TextUtil.mm(" <#FF8C00>/rc startevent <ивент|босс> <dark_gray>— <gray>Запустить мировой ивент/босса"));
                sender.sendMessage(TextUtil.mm(" <#FF8C00>/rc setrank <ник> <ранг> <dark_gray>— <gray>Установить привилегию"));
                sender.sendMessage(TextUtil.mm(" <#FF8C00>/rc buildspawn <dark_gray>— <gray>Перестроить Спавн, NPC и Голограммы"));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("reallycore.admin")) return List.of();
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "giveitem", "givekey", "setrank" -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                case "startevent" -> {
                    List<String> all = new ArrayList<>(plugin.events().getEventIds());
                    all.addAll(plugin.events().getBossIds());
                    yield filter(all, args[1]);
                }
                default -> List.of();
            };
        }
        if (args.length == 3) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "giveitem" -> filter(List.of("SPHERE", "TALISMAN", "ARTIFACT", "WEAPON", "ARMOR", "CONSUMABLE"), args[2]);
                case "givekey" -> filter(plugin.cases().getCases().stream().map(c -> c.id()).toList(), args[2]);
                case "setrank" -> filter(plugin.configs().getPrimaryRanks().stream().map(r -> r.id()).toList(), args[2]);
                default -> List.of();
            };
        }
        if (args.length == 4 && "giveitem".equalsIgnoreCase(args[0])) {
            return filter(plugin.customItems().listItemIds(args[2]), args[3]);
        }
        return List.of();
    }

    private List<String> filter(Collection<String> src, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return src.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
