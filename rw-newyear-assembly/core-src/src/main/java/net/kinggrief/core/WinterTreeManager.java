package net.kinggrief.core;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/** Shared decoration goal and bounded seasons; never modifies spawn blocks. */
final class WinterTreeManager {
    private final KingGriefCore plugin;
    private int visualStage;
    private BukkitRunnable ticker;

    private static final class Row {
        final TreeSeasonStore.View view;
        final int pending;
        Row(TreeSeasonStore.View view, int pending) { this.view = view; this.pending = pending; }
    }

    WinterTreeManager(KingGriefCore plugin) { this.plugin = plugin; }
    void start() {
        try { visualStage = plugin.getStore().inspect(data -> TreeSeasonStore.stage(TreeSeasonStore.current(data))); }
        catch (Exception error) { visualStage = 0; }
        ticker = new BukkitRunnable() { int step;
            @Override public void run() {
                if (visualStage <= 0) return;
                Location spawn = plugin.getSpawnEngine().getPreparedSpawnArrival();
                if (spawn == null || spawn.getWorld() == null) return;
                int viewers = 0;
                for (Player player : spawn.getWorld().getPlayers()) {
                    Location at = player.getLocation();
                    if (Math.abs(at.getX()) > 64 || Math.abs(at.getZ()) > 64 || ++viewers > 32) continue;
                    for (int tier = 0; tier < visualStage; tier++) {
                        double radius = tier == 0 ? 11.8 : tier == 1 ? 8.7 : 5.4;
                        double y = tier == 0 ? 111.5 : tier == 1 ? 124.5 : 137.5;
                        Particle.DustOptions color = new Particle.DustOptions(tier == 0 ? Color.fromRGB(255, 208, 64)
                                : tier == 1 ? Color.fromRGB(125, 220, 255) : Color.fromRGB(255, 100, 155), .7f);
                        for (int i = 0; i < 8; i++) {
                            double angle = (i + step * .25) * Math.PI / 4;
                            player.spawnParticle(Particle.DUST, Math.cos(angle) * radius, y, Math.sin(angle) * radius, 1, 0, 0, 0, 0, color);
                        }
                    }
                }
                step++;
            }
        };
        ticker.runTaskTimer(plugin, 40L, 20L);
    }
    void shutdown() { if (ticker != null) ticker.cancel(); }

    void command(CommandSender sender, String[] args, boolean admin) {
        Player player = sender instanceof Player ? (Player) sender : null;
        if (player != null && (!player.isOnline() || !plugin.getStore().isAuthenticated(player))) {
            send(sender, "&cСначала войдите в аккаунт."); return;
        }
        if (player != null && (plugin.getStore().isQuickFrozen(player) || plugin.getModeration().isFrozenOrAscending(player))) {
            send(sender, "&cКоманда ёлки недоступна во время заморозки или проверки."); return;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        if ("season".equals(sub)) { seasonAdmin(sender, args, admin); return; }
        if ("status".equals(sub) && args.length <= 1) { status(sender, player); return; }
        if ("history".equals(sub) && args.length == 1) { history(sender, player); return; }
        if ("top".equals(sub) && args.length <= 2) { top(sender, args.length == 2 ? args[1] : null); return; }
        if (player == null) { send(sender, "&eДля игроков: /kgtree donate, /kgtree claim. Для консоли: /kgtree history или /kgtree season."); return; }
        if ("donate".equals(sub)) { donate(player, args); return; }
        if ("claim".equals(sub) && args.length <= 2) { claim(player, args.length == 2 ? args[1] : null); return; }
        send(sender, "&e/kgtree <status|donate [1-10]|claim [сезон]|top [сезон]|history>");
    }
    private static void send(CommandSender sender, String text) { sender.sendMessage(ColorUtil.c(text)); }
    private static String name(Player player) { return player.getName().toLowerCase(Locale.ROOT); }
    private static String day() { return LocalDate.now(ZoneOffset.UTC).toString(); }

    private void status(CommandSender sender, Player player) {
        final String today = day();
        try {
            Object[] snapshot = plugin.getStore().inspect(data -> {
                TreeSeasonStore.View view = TreeSeasonStore.current(data);
                TreeSeasonStore.Donor donor = player == null ? null : TreeSeasonStore.donor(data, view, name(player));
                int used = player == null ? 0 : TreeSeasonStore.daily(data, view, name(player), today);
                return new Object[]{view, donor, Integer.valueOf(used)};
            });
            TreeSeasonStore.View view = (TreeSeasonStore.View) snapshot[0];
            int stage = TreeSeasonStore.stage(view);
            int next = stage < view.goals.length ? view.goals[stage] : view.goals[view.goals.length - 1];
            send(sender, "&#80D8FF❄ Ёлка · сезон &f" + view.id + "&7: &f" + view.total + "/" + next + " ❄ &8| &eэтап " + stage + "/3");
            if (player != null) {
                TreeSeasonStore.Donor donor = (TreeSeasonStore.Donor) snapshot[1];
                send(sender, "&7Ваш вклад: &f" + donor.total + " ❄ &8| &7сегодня: &f" + snapshot[2] + "/" + TreeSeasonStore.DAILY_LIMIT + " (UTC)");
            }
            send(sender, "&e/kgtree donate [1-10] &8• &e/kgtree claim &8• &e/kgtree history &8• &e/kgtree top");
        } catch (Exception error) {
            plugin.getLogger().warning("[Tree] Status unavailable: " + error.getClass().getSimpleName());
            send(sender, "&cНе удалось прочитать состояние ёлки.");
        }
    }
    private void donate(Player player, String[] args) {
        int requested = 1;
        if (args.length > 2) { send(player, "&e/kgtree donate [1-10]"); return; }
        if (args.length == 2) try { requested = Integer.parseInt(args[1]); }
        catch (NumberFormatException error) { requested = 0; }
        if (requested < 1 || requested > TreeSeasonStore.DAILY_LIMIT) {
            send(player, "&cКоличество — от 1 до " + TreeSeasonStore.DAILY_LIMIT + " снежинок."); return;
        }
        final int amount = requested; final String name = name(player), today = day();
        try {
            int[] result = plugin.getStore().transact(data -> TreeSeasonStore.donate(data, name, amount, today));
            send(player, "&#80D8FFСпасибо! В ёлку внесено &f" + amount + " ❄&7. Прогресс: &f" + result[1] + "/300");
            int before = stage(result[0]), after = stage(result[1]); visualStage = after;
            if (after > before) Bukkit.broadcastMessage(ColorUtil.c("&#FFD740✦ Игроки украсили ёлку! Достигнут этап &f" + after + "/3&7. Участникам: &e/kgtree claim"));
        } catch (IllegalArgumentException rejected) { send(player, "&c" + rejected.getMessage()); }
        catch (Exception error) {
            plugin.getLogger().warning("[Tree] Donation not committed: " + error.getMessage());
            send(player, "&cНе удалось сохранить взнос. Снежинки не списаны.");
        }
    }
    private void claim(Player player, String rawId) {
        try {
            String id = rawId == null ? null : TreeSeasonStore.normalizeId(rawId);
            TreeSeasonStore.Payment result = plugin.getStore().transact(data -> TreeSeasonStore.claim(data, name(player), id,
                    plugin.getConfig().getDouble("starting-coins", 2500.0)));
            String extra = result.key ? " &8• &dключ legendary" : "";
            if (result.title) extra += " &8• &bтитул Друга ёлки";
            send(player, "&#00E676Награда ёлки" + (id == null ? "" : " за сезон &f" + id) + ": &f+"
                    + ColorUtil.fmtMoney(result.coins) + " монет" + extra + "&7. Этапов: " + result.stages);
        } catch (IllegalArgumentException rejected) { send(player, "&e" + rejected.getMessage()); }
        catch (Exception error) {
            plugin.getLogger().warning("[Tree] Claim not committed: " + error.getMessage());
            send(player, "&cНе удалось сохранить награду. Попробуйте позже.");
        }
    }
    private void history(CommandSender sender, Player player) {
        try {
            List<Row> rows = plugin.getStore().inspect(data -> {
                List<Row> result = new ArrayList<Row>();
                TreeSeasonStore.View current = TreeSeasonStore.current(data);
                int pending = player == null ? 0 : TreeSeasonStore.pending(current, TreeSeasonStore.donor(data, current, name(player)));
                result.add(new Row(current, pending));
                List<Row> archived = new ArrayList<Row>();
                for (String id : TreeSeasonStore.archiveIds(data)) {
                    TreeSeasonStore.View view = TreeSeasonStore.resolve(data, id);
                    int due = player == null ? 0 : TreeSeasonStore.pending(view, TreeSeasonStore.donor(data, view, name(player)));
                    archived.add(new Row(view, due));
                }
                archived.sort(Comparator.<Row>comparingLong(row -> row.view.closedAt).reversed().thenComparing(row -> row.view.id));
                result.addAll(archived);
                return result;
            });
            send(sender, "&#80D8FF✦ Сезоны общей ёлки &8(архив: " + Math.max(0, rows.size() - 1) + "/" + TreeSeasonStore.MAX_ARCHIVES + "):");
            for (Row row : rows) {
                String line = "&f" + row.view.id + " &8— &b" + row.view.total + "/" + row.view.goals[row.view.goals.length - 1]
                        + " ❄ &8· &7этап " + TreeSeasonStore.stage(row.view) + "/3"
                        + (row.view.archived ? " &8[архив]" : " &a[активен]")
                        + (player == null ? "" : " &8· &eнаград: " + row.pending);
                if (player == null) { send(sender, line); continue; }
                TextComponent message = new TextComponent(ColorUtil.c(line + "  "));
                TextComponent claim = new TextComponent(ColorUtil.c("&a[Награды] "));
                claim.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/kgtree claim " + row.view.id));
                claim.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Получить доступные награды сезона").create()));
                TextComponent top = new TextComponent(ColorUtil.c("&b[Топ]"));
                top.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/kgtree top " + row.view.id));
                top.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Посмотреть участников сезона").create()));
                message.addExtra(claim); message.addExtra(top); player.spigot().sendMessage(message);
            }
        } catch (Exception error) {
            plugin.getLogger().warning("[Tree] History unavailable: " + error.getMessage());
            send(sender, "&cНе удалось прочитать историю сезонов; данные не изменены.");
        }
    }
    private void top(CommandSender sender, String rawId) {
        try {
            final String requested = rawId == null ? null : TreeSeasonStore.normalizeId(rawId);
            Object[] result = plugin.getStore().inspect(data -> {
                TreeSeasonStore.View view = TreeSeasonStore.resolve(data, requested);
                List<String[]> entries = new ArrayList<String[]>();
                ConfigurationSection section = data.getConfigurationSection(view.root + ".donors");
                if (section != null) for (String donor : section.getKeys(false)) {
                    if (!donor.matches("[a-z0-9_]{1,16}")) continue;
                    TreeSeasonStore.Donor value = TreeSeasonStore.donor(data, view, donor);
                    if (value.total > 0) entries.add(new String[]{donor, Integer.toString(value.total)});
                }
                return new Object[]{view, entries};
            });
            TreeSeasonStore.View view = (TreeSeasonStore.View) result[0];
            @SuppressWarnings("unchecked") List<String[]> entries = (List<String[]>) result[1];
            entries.sort(Comparator.<String[]>comparingInt(entry -> Integer.parseInt(entry[1])).reversed().thenComparing(entry -> entry[0]));
            send(sender, "&#80D8FF✦ Топ помощников ёлки · &f" + view.id + "&7:");
            for (int i = 0; i < Math.min(10, entries.size()); i++) {
                send(sender, "&7" + (i + 1) + ". &f" + entries.get(i)[0] + " &8— &b" + entries.get(i)[1] + " ❄");
            }
            if (entries.isEmpty()) send(sender, "&7Пока нет взносов.");
        } catch (IllegalArgumentException rejected) { send(sender, "&e" + rejected.getMessage()); }
        catch (Exception error) { send(sender, "&cНе удалось прочитать топ."); }
    }
    private void seasonAdmin(CommandSender sender, String[] args, boolean admin) {
        if (!admin) { send(sender, "&cУправлять сезонами могут OWNER, ТЕХ.АДМИН и консоль."); return; }
        if (args.length != 4 || !"confirm".equalsIgnoreCase(args[3])) {
            send(sender, "&e/kgtree season start <название> confirm &7— новый сезон с сохранением старых наград.");
            send(sender, "&e/kgtree season prune <архив> confirm &7— удалить только полностью оплаченный архив.");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        try {
            String id = TreeSeasonStore.normalizeId(args[2]);
            if ("start".equals(action)) {
                TreeSeasonStore.Rotation result = plugin.getStore().transact(data -> TreeSeasonStore.rotate(data, id, day(), System.currentTimeMillis()));
                visualStage = 0;
                plugin.getLogger().info("[Tree] " + sender.getName() + " started season " + result.next + " after " + result.previous);
                Bukkit.broadcastMessage(ColorUtil.c("&#80D8FF✦ Новый сезон ёлки: &f" + result.next
                        + "&7! Старые награды: &e/kgtree history&7. Новый вклад: &e/kgtree donate"));
                if (!result.archived) send(sender, "&7Пустой сезон не занял место в архиве.");
                return;
            }
            if ("prune".equals(action)) {
                int donors = plugin.getStore().transact(data -> TreeSeasonStore.prune(data, id));
                plugin.getLogger().info("[Tree] " + sender.getName() + " pruned settled season " + id + " (" + donors + " donors)");
                send(sender, "&aПолностью оплаченный архив &f" + id + " &aудалён; накопленные достижения сохранены.");
                return;
            }
            send(sender, "&eДоступны /kgtree season start и /kgtree season prune.");
        } catch (IllegalArgumentException rejected) { send(sender, "&e" + rejected.getMessage()); }
        catch (Exception error) {
            plugin.getLogger().warning("[Tree] Season change not committed: " + error.getMessage());
            send(sender, "&cНе удалось сохранить изменение сезона; прежние данные не изменены.");
        }
    }
    List<String> seasonIds() {
        try {
            return plugin.getStore().inspect(data -> {
                List<String> ids = new ArrayList<String>();
                ids.add(TreeSeasonStore.currentId(data));
                ids.addAll(TreeSeasonStore.archiveIds(data));
                return ids;
            });
        } catch (Exception error) { return new ArrayList<String>(); }
    }
    private static int stage(int total) {
        int stage = 0;
        for (int goal : TreeSeasonStore.GOALS) if (total >= goal) stage++;
        return stage;
    }
}
