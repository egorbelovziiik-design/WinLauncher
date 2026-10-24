package ru.reallyworld.ultimate.core.command;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import ru.reallyworld.ultimate.core.ReallyCorePlugin;
import ru.reallyworld.ultimate.core.storage.model.HomePoint;
import ru.reallyworld.ultimate.core.storage.model.PlayerProfile;
import ru.reallyworld.ultimate.core.storage.model.WarpPoint;

import java.util.*;

public final class TeleportCommands implements CommandExecutor, TabCompleter {
    private final ReallyCorePlugin plugin;

    public TeleportCommands(ReallyCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.locale().send(sender, "player-only");
            return true;
        }
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        switch (name) {
            case "spawn" -> plugin.players().teleportWithWarmup(player, plugin.players().getSpawnLocation(),
                    (p, ok) -> { if (ok) plugin.locale().send(p, "teleport.spawn-success"); });
            case "setspawn" -> {
                if (!player.hasPermission("reallycore.admin")) {
                    plugin.locale().send(player, "no-permission");
                    return true;
                }
                plugin.players().setSpawnLocation(player.getLocation());
                plugin.locale().sendRaw(player, "<green>Точка главного Спавна обновлена!");
            }
            case "rtp" -> plugin.players().randomTeleport(player, false);
            case "rtpfar" -> {
                if (!player.hasPermission("reallycore.rtpfar")) {
                    plugin.locale().send(player, "no-permission");
                    return true;
                }
                plugin.players().randomTeleport(player, true);
            }
            case "sethome" -> plugin.players().setHome(player, args.length > 0 ? args[0] : "home");
            case "delhome" -> {
                if (args.length == 0) {
                    plugin.locale().sendRaw(player, "<yellow>Использование: /delhome <название>");
                    return true;
                }
                plugin.players().deleteHome(player, args[0]);
            }
            case "homes" -> plugin.menus().openProfile(player);
            case "home" -> {
                String hName = args.length > 0 ? args[0] : "home";
                List<HomePoint> homes = plugin.players().getHomes(player.getUniqueId());
                if (homes.isEmpty()) {
                    plugin.locale().send(player, "teleport.homes-empty");
                    return true;
                }
                HomePoint hp = homes.stream().filter(h -> h.name().equalsIgnoreCase(hName)).findFirst()
                        .orElse(args.length == 0 ? homes.getFirst() : null);
                if (hp == null) {
                    plugin.locale().send(player, "teleport.home-not-found", Map.of("name", hName));
                    return true;
                }
                World w = Bukkit.getWorld(hp.worldName());
                if (w != null) {
                    plugin.players().teleportWithWarmup(player,
                            new Location(w, hp.x(), hp.y(), hp.z(), hp.yaw(), hp.pitch()),
                            (p, ok) -> { if (ok) plugin.locale().send(p, "teleport.home-teleported", Map.of("name", hp.name())); });
                }
            }
            case "tpa", "tpahere" -> {
                if (args.length == 0) {
                    plugin.locale().sendRaw(player, "<yellow>Использование: /" + name + " <игрок>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    plugin.locale().send(player, "player-not-found", Map.of("player", args[0]));
                    return true;
                }
                plugin.players().sendTpa(player, target, "tpahere".equals(name));
            }
            case "tpaccept" -> plugin.players().respondTpa(player, true);
            case "tpdeny" -> plugin.players().respondTpa(player, false);
            case "tptoggle" -> {
                if (!player.hasPermission("reallycore.tptoggle")) {
                    plugin.locale().send(player, "no-permission");
                    return true;
                }
                PlayerProfile prof = plugin.players().getProfile(player);
                prof.setTpToggle(!prof.tpToggle());
                plugin.locale().sendRaw(player, "<gray>Приём запросов телепортации: "
                        + (prof.tpToggle() ? "<green>ВКЛЮЧЁН" : "<red>ВЫКЛЮЧЕН"));
            }
            case "warps" -> plugin.menus().openWarps(player);
            case "warp" -> {
                if (args.length == 0) {
                    plugin.menus().openWarps(player);
                    return true;
                }
                Optional<WarpPoint> wpOpt = plugin.players().getWarp(args[0]);
                if (wpOpt.isEmpty()) {
                    plugin.locale().send(player, "teleport.warp-not-found", Map.of("name", args[0]));
                    return true;
                }
                WarpPoint wp = wpOpt.get();
                World w = Bukkit.getWorld(wp.worldName());
                if (w != null) {
                    plugin.players().teleportWithWarmup(player,
                            new Location(w, wp.x(), wp.y(), wp.z(), wp.yaw(), wp.pitch()),
                            (p, ok) -> { if (ok) plugin.locale().send(p, "teleport.warp-teleported", Map.of("name", wp.displayName())); });
                }
            }
            case "setwarp" -> {
                if (!player.hasPermission("reallycore.admin")) {
                    plugin.locale().send(player, "no-permission");
                    return true;
                }
                if (args.length == 0) {
                    plugin.locale().sendRaw(player, "<yellow>Использование: /setwarp <id> [отображаемое имя]");
                    return true;
                }
                String disp = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : args[0];
                plugin.players().setWarp(args[0], disp, "MAIN", player.getLocation(), player.getName());
                plugin.locale().sendRaw(player, "<green>Варп <gold>" + args[0] + "</gold> установлен!");
            }
            case "delwarp" -> {
                if (!player.hasPermission("reallycore.admin")) {
                    plugin.locale().send(player, "no-permission");
                    return true;
                }
                if (args.length == 0 || !plugin.players().deleteWarp(args[0])) {
                    plugin.locale().sendRaw(player, "<red>Варп не найден.");
                } else {
                    plugin.locale().sendRaw(player, "<yellow>Варп <white>" + args[0] + "</white> удалён.");
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player p) || args.length != 1) return List.of();
        String pref = args[0].toLowerCase(Locale.ROOT);
        return switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "home", "delhome" -> plugin.players().getHomes(p.getUniqueId()).stream()
                    .map(HomePoint::name).filter(n -> n.startsWith(pref)).toList();
            case "warp", "delwarp" -> plugin.players().getWarps().stream()
                    .map(WarpPoint::name).filter(n -> n.startsWith(pref)).toList();
            case "tpa", "tpahere" -> Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName).filter(n -> n.toLowerCase(Locale.ROOT).startsWith(pref)).toList();
            default -> List.of();
        };
    }
}
