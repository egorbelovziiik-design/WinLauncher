package ru.reallyworld.ultimate.core.command;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import ru.reallyworld.ultimate.core.ReallyCorePlugin;
import ru.reallyworld.ultimate.core.config.RankDefinition;
import ru.reallyworld.ultimate.core.storage.model.PlayerProfile;
import ru.reallyworld.ultimate.core.util.TextUtil;

import java.util.*;

/**
 * Authentic ReallyWorld Privilege Utility Commands:
 * /craft, /recipe, /enderchest, /feed, /heal, /repair, /ext, /hat, /near, /ptime,
 * /nick, /prefix, /grant, /fly, /clear, /ignore, /getpos, /itemdb, /broadcast,
 * /lightning, /sun, /rain, /day, /night, /invsee, /realname
 * Strictly permission-gated and combat-safe (never grants OP/WorldEdit/moderation).
 */
public final class DonateUtilityCommands implements CommandExecutor, TabCompleter {
    private static final Set<String> GRANTABLE_STARTER_RANKS = Set.of("HERO", "TITAN");

    private final ReallyCorePlugin plugin;

    public DonateUtilityCommands(ReallyCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.locale().send(sender, "player-only");
            return true;
        }
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        String reqPerm = cmd.getPermission();
        if (reqPerm != null && !reqPerm.isBlank() && !player.hasPermission(reqPerm)) {
            plugin.locale().send(player, "no-permission");
            return true;
        }

        switch (name) {
            case "craft", "recipe" -> player.openWorkbench(null, true);
            case "enderchest" -> player.openInventory(player.getEnderChest());
            case "feed" -> {
                if (checkCooldown(player, "feed", 30)) return true;
                player.setFoodLevel(20);
                player.setSaturation(14f);
                plugin.locale().sendRaw(player, "<green>Голод полностью утолён!");
            }
            case "heal" -> {
                if (plugin.combat().isInCombat(player.getUniqueId())) {
                    plugin.locale().send(player, "combat.blocked-command");
                    return true;
                }
                if (checkCooldown(player, "heal", 120)) return true;
                double maxHp = Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue();
                player.setHealth(maxHp);
                player.setFoodLevel(20);
                player.setFireTicks(0);
                plugin.locale().sendRaw(player, "<green>Ваше здоровье полностью восстановлено!");
            }
            case "ext" -> {
                if (checkCooldown(player, "ext", 20)) return true;
                player.setFireTicks(0);
                plugin.locale().sendRaw(player, "<#55FFFF>Пламя потушено!");
            }
            case "hat" -> {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType() == Material.AIR) {
                    plugin.locale().sendRaw(player, "<red>Возьмите предмет в руку!");
                    return true;
                }
                ItemStack prevHelmet = player.getInventory().getHelmet();
                player.getInventory().setHelmet(hand.clone());
                player.getInventory().setItemInMainHand(prevHelmet);
                plugin.locale().sendRaw(player, "<green>Предмет надет на голову!");
            }
            case "repair" -> {
                boolean repairAll = args.length > 0 && "all".equalsIgnoreCase(args[0]);
                if (repairAll && !player.hasPermission("reallycore.repair.all")) {
                    plugin.locale().send(player, "no-permission");
                    return true;
                }
                if (checkCooldown(player, repairAll ? "repair_all" : "repair", repairAll ? 300 : 120)) return true;
                if (repairAll) {
                    for (ItemStack st : player.getInventory().getContents()) {
                        repairStack(st);
                    }
                    plugin.locale().sendRaw(player, "<green>Все предметы в инвентаре отремонтированы!");
                } else {
                    if (!repairStack(player.getInventory().getItemInMainHand())) {
                        plugin.locale().sendRaw(player, "<red>Этот предмет не нуждается в починке!");
                        return true;
                    }
                    plugin.locale().sendRaw(player, "<green>Предмет в руке отремонтирован!");
                }
            }
            case "near" -> {
                List<String> found = new ArrayList<>();
                for (Player other : player.getWorld().getPlayers()) {
                    if (other.equals(player) || other.getGameMode() == GameMode.SPECTATOR) continue;
                    double d = other.getLocation().distance(player.getLocation());
                    if (d <= 150.0) {
                        found.add("<white>" + other.getName() + " <gray>(" + (int) d + "м)");
                    }
                }
                plugin.locale().sendRaw(player, found.isEmpty()
                        ? "<gray>Рядом (150м) никого не обнаружено."
                        : "<#FBBC00>Игроки поблизости: " + String.join(", ", found));
            }
            case "ptime" -> {
                if (args.length == 0 || "reset".equalsIgnoreCase(args[0])) {
                    player.resetPlayerTime();
                    plugin.locale().sendRaw(player, "<green>Личное время синхронизировано с сервером.");
                } else {
                    long ticks = "night".equalsIgnoreCase(args[0]) ? 18000L : 6000L;
                    player.setPlayerTime(ticks, false);
                    plugin.locale().sendRaw(player, "<green>Личное время установлено!");
                }
            }
            case "nick" -> {
                PlayerProfile prof = plugin.players().getProfile(player);
                if (args.length == 0 || "off".equalsIgnoreCase(args[0]) || "clear".equalsIgnoreCase(args[0])) {
                    prof.setNickname(null);
                    player.displayName(null);
                    plugin.locale().sendRaw(player, "<yellow>Псевдоним сброшен.");
                } else {
                    String clean = TextUtil.sanitizeInput(args[0], 16);
                    prof.setNickname(clean);
                    player.displayName(TextUtil.mm(clean));
                    plugin.locale().sendRaw(player, "<green>Ваш ник в чате изменён на: <white>" + clean);
                }
            }
            case "prefix" -> {
                PlayerProfile prof = plugin.players().getProfile(player);
                if (args.length == 0 || "reset".equalsIgnoreCase(args[0])) {
                    prof.setCustomPrefix(null);
                    plugin.locale().sendRaw(player, "<yellow>Кастомный титул сброшен на префикс ранга.");
                } else {
                    String clean = TextUtil.sanitizeInput(args[0], 14).toUpperCase(Locale.ROOT);
                    prof.setCustomPrefix("<bold><#FBBC00>[" + clean + "]</bold>");
                    plugin.locale().sendRaw(player, "<green>Установлен титул: <bold><#FBBC00>[" + clean + "]</bold>");
                }
            }
            case "fly" -> {
                if (plugin.combat().isInCombat(player.getUniqueId())) {
                    plugin.locale().send(player, "combat.blocked-command");
                    return true;
                }
                boolean next = !player.getAllowFlight();
                player.setAllowFlight(next);
                if (!next) player.setFlying(false);
                plugin.locale().sendRaw(player, "<gray>Режим полёта: " + (next ? "<green>ВКЛЮЧЁН" : "<red>ВЫКЛЮЧЕН"));
            }
            case "clear" -> {
                player.getInventory().clear();
                plugin.locale().sendRaw(player, "<yellow>Ваш личный инвентарь очищен.");
            }
            case "ignore" -> {
                if (args.length == 0) {
                    plugin.locale().sendRaw(player, "<yellow>Использование: /ignore <игрок>");
                    return true;
                }
                Optional<PlayerProfile> t = plugin.players().findProfileByName(args[0]);
                if (t.isEmpty() || t.get().uuid().equals(player.getUniqueId())) {
                    plugin.locale().send(player, "player-not-found", Map.of("player", args[0]));
                    return true;
                }
                boolean ignored = plugin.players().toggleIgnore(player, t.get().uuid());
                plugin.locale().sendRaw(player, ignored
                        ? "<yellow>Игрок <white>" + t.get().username() + "</white> добавлен в чёрный список."
                        : "<green>Игрок <white>" + t.get().username() + "</white> удалён из чёрного списка.");
            }
            case "getpos" -> {
                Location l = player.getLocation();
                plugin.locale().sendRaw(player, "<gray>Ваши координаты: <white>X:" + l.getBlockX()
                        + " Y:" + l.getBlockY() + " Z:" + l.getBlockZ() + " (" + l.getWorld().getName() + ")");
            }
            case "itemdb" -> {
                ItemStack st = player.getInventory().getItemInMainHand();
                plugin.locale().sendRaw(player, "<gray>Материал предмета: <#FBBC00>" + st.getType().name()
                        + " <gray>(Прочность: " + st.getType().getMaxDurability() + ")");
            }
            case "invsee" -> {
                if (args.length == 0) return true;
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    plugin.locale().send(player, "player-not-found", Map.of("player", args[0]));
                    return true;
                }
                // Read-only snapshot GUI so donor /invsee cannot steal items!
                var snap = Bukkit.createInventory(new ru.reallyworld.ultimate.core.gui.ReallyGuiHolder("INVSEE"),
                        36, TextUtil.mm("<bold><#FBBC00>Инвентарь • " + target.getName()));
                ItemStack[] src = target.getInventory().getStorageContents();
                for (int i = 0; i < Math.min(36, src.length); i++) {
                    if (src[i] != null) snap.setItem(i, src[i].clone());
                }
                player.openInventory(snap);
            }
            case "realname" -> {
                if (args.length == 0) return true;
                String q = args[0].toLowerCase(Locale.ROOT);
                for (Player online : Bukkit.getOnlinePlayers()) {
                    PlayerProfile p = plugin.players().getProfile(online);
                    if (p.nickname() != null && p.nickname().toLowerCase(Locale.ROOT).contains(q)) {
                        plugin.locale().sendRaw(player, "<gray>Настоящий ник игрока «" + p.nickname() + "»: <#FBBC00>" + online.getName());
                        return true;
                    }
                }
                plugin.locale().sendRaw(player, "<gray>Совпадений среди псевдонимов не найдено.");
            }
            case "broadcast" -> {
                if (args.length == 0 || checkCooldown(player, "broadcast", 300)) return true;
                String msg = TextUtil.sanitizeInput(String.join(" ", args), 140);
                Bukkit.getServer().sendMessage(TextUtil.mm("<bold><#FF6A00>[ОБЪЯВЛЕНИЕ]</bold> <white>"
                        + player.getName() + " <dark_gray>» <#FBBC00>" + msg));
            }
            case "lightning" -> {
                if (checkCooldown(player, "lightning", 60)) return true;
                // Strictly cosmetic visual lightning effect (zero damage, zero fire!)
                player.getWorld().strikeLightningEffect(player.getTargetBlockExact(30) != null
                        ? Objects.requireNonNull(player.getTargetBlockExact(30)).getLocation()
                        : player.getLocation());
            }
            case "grant" -> {
                if (args.length < 2) {
                    plugin.locale().sendRaw(player, "<yellow>Использование: /grant <игрок> <HERO|TITAN>");
                    return true;
                }
                String giftRank = args[1].toUpperCase(Locale.ROOT);
                if (!GRANTABLE_STARTER_RANKS.contains(giftRank)) {
                    plugin.locale().sendRaw(player, "<red>Подарить через /grant можно только стартовые ранги HERO или TITAN!");
                    return true;
                }
                PlayerProfile giver = plugin.players().getProfile(player);
                RankDefinition giverRank = plugin.players().getRank(player);
                if (giver.grantsUsed() >= giverRank.monthlyGrantQuota()) {
                    plugin.locale().sendRaw(player, "<red>Вы исчерпали лимит подарков /grant (" + giverRank.monthlyGrantQuota() + ")!");
                    return true;
                }
                Player recipient = Bukkit.getPlayerExact(args[0]);
                if (recipient == null || recipient.equals(player)) {
                    plugin.locale().send(player, "player-not-found", Map.of("player", args[0]));
                    return true;
                }
                if (plugin.players().getRank(recipient).weight() >= plugin.configs().getRank(giftRank).weight()) {
                    plugin.locale().sendRaw(player, "<yellow>У этого игрока уже есть такой или более высокий ранг!");
                    return true;
                }
                giver.setGrantsUsed(giver.grantsUsed() + 1);
                plugin.players().setPlayerRank(recipient.getUniqueId(), recipient.getName(), giftRank);
                Bukkit.getServer().sendMessage(TextUtil.mm(plugin.locale().prefix() + "<#FBBC00>Игрок <white>"
                        + player.getName() + "</white> подарил привилегию <gold>" + giftRank + "</gold> игроку <white>" + recipient.getName() + "</white>!"));
            }
            case "sun" -> {
                if (!checkCooldown(player, "weather", 180)) {
                    player.getWorld().setStorm(false);
                    player.getWorld().setThundering(false);
                    plugin.locale().sendRaw(player, "<#FBBC00>Установлена солнечная погода!");
                }
            }
            case "rain" -> {
                if (!checkCooldown(player, "weather", 180)) {
                    player.getWorld().setStorm(true);
                    plugin.locale().sendRaw(player, "<#55FFFF>Начался дождь!");
                }
            }
            case "day" -> {
                if (!checkCooldown(player, "worldtime", 180)) {
                    player.getWorld().setTime(1000L);
                    plugin.locale().sendRaw(player, "<#FBBC00>В мире наступило утро!");
                }
            }
            case "night" -> {
                if (!checkCooldown(player, "worldtime", 180)) {
                    player.getWorld().setTime(14000L);
                    plugin.locale().sendRaw(player, "<#AA00AA>В мире наступила ночь!");
                }
            }
        }
        return true;
    }

    private boolean checkCooldown(Player player, String key, long seconds) {
        if (player.hasPermission("reallycore.admin")) return false;
        long rem = plugin.cooldowns().getRemainingSeconds(player.getUniqueId(), "util:" + key);
        if (rem > 0) {
            plugin.locale().send(player, "on-cooldown", Map.of("time", TextUtil.formatDuration(rem)));
            return true;
        }
        plugin.cooldowns().setCooldown(player.getUniqueId(), "util:" + key, seconds, false);
        return false;
    }

    private boolean repairStack(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta()) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof Damageable dmg && dmg.hasDamage()) {
            dmg.setDamage(0);
            stack.setItemMeta(meta);
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            return switch (name) {
                case "repair" -> List.of("hand", "all");
                case "ptime" -> List.of("day", "night", "reset");
                case "ignore", "invsee", "grant" -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                default -> List.of();
            };
        }
        if (args.length == 2 && "grant".equals(name)) {
            return List.of("HERO", "TITAN");
        }
        return List.of();
    }
}
