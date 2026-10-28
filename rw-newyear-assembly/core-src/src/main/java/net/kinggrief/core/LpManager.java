package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LpManager implements CommandExecutor, TabCompleter {

    private static final String[] COMMON_PERMS = new String[]{
            "*", "kinggrief.admin", "kinggrief.lp", "kinggrief.spawn.edit", "kinggrief.staff",
            "kinggrief.fly", "kinggrief.god", "kinggrief.gm", "kinggrief.vanish",
            "kinggrief.heal", "kinggrief.feed", "kinggrief.repair", "kinggrief.enderchest",
            "kinggrief.workbench", "kinggrief.tp", "kinggrief.ban", "kinggrief.mute", "kinggrief.kick"
    };

    private final KingGriefCore plugin;

    public LpManager(KingGriefCore plugin) {
        this.plugin = plugin;
    }

    public boolean hasLpAccess(CommandSender s) {
        if (s instanceof ConsoleCommandSender) return true;
        if (!(s instanceof Player)) return false;
        Player p = (Player) s;
        RankManager.RankDef r = plugin.getRanks().getRank(p);
        // v6.1.2: OWNER + TECH.ADMIN (тех.админу выдан полный LP); куратор/даунер/опка — НЕТ!
        return r != null && ("owner".equalsIgnoreCase(r.id) || "techadmin".equalsIgnoreCase(r.id));
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (!hasLpAccess(s)) {
            s.sendMessage(ColorUtil.c("&#FF1744✖ Доступ к LP Editor (/lp) разрешён только OWNER и TECH.ADMIN!"));
            return true;
        }
        String lbl = label.toLowerCase(Locale.ROOT);
        if ("lpeditor".equals(lbl)) {
            openEditorSession(s);
            return true;
        }
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sendHelp(s);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("editor".equals(sub) || "webeditor".equals(sub)) {
            openEditorSession(s);
            return true;
        }
        if ("gui".equals(sub) || "menu".equals(sub)) {
            if (s instanceof Player) {
                plugin.getGuis().openLpGroupsGui((Player) s, 0);
            } else {
                s.sendMessage(ColorUtil.c("&cGUI доступен только в игре. Используйте /lp editor!"));
            }
            return true;
        }
        if ("applyedits".equals(sub)) {
            if (args.length < 2) {
                s.sendMessage(ColorUtil.c("&#FFD740Использование: &f/lp applyedits <код из Web LP Editor>"));
                return true;
            }
            applyWebEdits(s, args[1]);
            return true;
        }
        if ("sync".equals(sub) || "reload".equals(sub)) {
            for (Player on : Bukkit.getOnlinePlayers()) {
                plugin.getStore().refreshPermissions(on, plugin.getRanks());
                plugin.getHud().updatePlayerHud(on);
            }
            s.sendMessage(ColorUtil.c("&#00E676[LP] ✔ Все 40+ групп, права, префиксы, ТАБ и Скорборд синхронизированы!"));
            return true;
        }
        if ("listgroups".equals(sub) || "groups".equals(sub)) {
            s.sendMessage(ColorUtil.c("&#00E676&l[LP] Группы сервера (" + plugin.getRanks().allRanks().size() + "):"));
            StringBuilder sb = new StringBuilder();
            for (RankManager.RankDef r : plugin.getRanks().allRanks()) {
                if (sb.length() > 0) sb.append("&8, ");
                sb.append("&f").append(r.id).append("&7(").append(r.weight).append(")");
            }
            s.sendMessage(ColorUtil.c(sb.toString()));
            return true;
        }
        if ("tracks".equals(sub)) {
            s.sendMessage(ColorUtil.c("&#00E676&l[LP] Ветки повышений (Tracks v5.9):"));
            s.sendMessage(ColorUtil.c(" &e• donate: &7default → dragon → pegas → cobra → dracula → bunny → rabbit → vampire → tiger → god → imperator → king → sponsor"));
            s.sendMessage(ColorUtil.c(" &6• dkp: &7dhelper → dkp → dmoder → dstmoder → dglmoder → dmladmin → dadmin → opka → downer"));
            s.sendMessage(ColorUtil.c(" &a• kp: &7kp → helper → sthelper → mlmoder → moder → stmoder → glmoder → mladmin → admin → curator → techadmin → owner"));
            s.sendMessage(ColorUtil.c(" &d• media: &7tiktok → twitch → yt → media → mediaplus"));
            return true;
        }
        if ("creategroup".equals(sub)) {
            if (args.length < 2) {
                s.sendMessage(ColorUtil.c("&eИспользование: &f/lp creategroup <название> [вес] [префикс]"));
                return true;
            }
            String gid = args[1].toLowerCase(Locale.ROOT);
            int w = args.length >= 3 ? parseIntSafe(args[2], 25) : 25;
            String pref = args.length >= 4 ? joinFrom(args, 3) : ("&#38BDF8&l[" + gid.toUpperCase(Locale.ROOT) + "] &f");
            RankManager.RankDef def = plugin.getRanks().createOrUpdateGroup(gid, w, pref);
            s.sendMessage(ColorUtil.c("&#00E676[LP] ✔ Группа &f" + def.id + " &#00E676создана! Вес: &e" + def.weight + " &7| Префикс: " + def.chatPrefix));
            return true;
        }
        if ("deletegroup".equals(sub)) {
            if (args.length < 2) {
                s.sendMessage(ColorUtil.c("&eИспользование: &f/lp deletegroup <название>"));
                return true;
            }
            if (plugin.getRanks().deleteCustomGroup(args[1])) {
                s.sendMessage(ColorUtil.c("&#00E676[LP] ✔ Группа &f" + args[1] + " &#00E676удалена."));
            } else {
                s.sendMessage(ColorUtil.c("&c[LP] Нельзя удалить системную группу default/owner!"));
            }
            return true;
        }
        if ("user".equals(sub)) {
            handleUserSub(s, args);
            return true;
        }
        if ("group".equals(sub)) {
            handleGroupSub(s, args);
            return true;
        }
        sendHelp(s);
        return true;
    }

    private static boolean isPermWord(String s) {
        if (s == null) return false;
        String w = s.toLowerCase(Locale.ROOT);
        return "permission".equals(w) || "permision".equals(w) || "permissions".equals(w)
                || "permisions".equals(w) || "perms".equals(w) || "perm".equals(w);
    }

    private void executeUserPermissionClear(CommandSender s, String nick, boolean alsoResetGroup) {
        int removed = plugin.getStore().clearUserPermissions(nick);
        plugin.getStore().setUserCustomPrefix(nick, "");
        try {
            org.bukkit.OfflinePlayer off = Bukkit.getOfflinePlayer(nick);
            if (off != null && off.isOp()) off.setOp(false);
        } catch (Throwable ignored) {}
        Player on = Bukkit.getPlayerExact(nick);
        if (on != null && on.isOp()) {
            try { on.setOp(false); } catch (Throwable ignored) {}
        }
        if (alsoResetGroup) {
            plugin.getRanks().setRank(nick, "default", true);
        }
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + nick + " permission clear");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + nick + " meta clear");
        }
        if (on != null) {
            plugin.getStore().refreshPermissions(on, plugin.getRanks());
            plugin.getHud().updatePlayerHud(on);
        }
        String curRank = plugin.getStore().getSavedRank(nick);
        s.sendMessage(ColorUtil.c("&#00E676&l[LP] ✔ Все личные права, звезда (*) и OP игрока &f" + nick
                + " &#00E676&lполностью очищены! &8(Удалено прав: &f" + removed + "&8, текущая группа: &e" + curRank + "&8)"));
        if (!alsoResetGroup && !"default".equalsIgnoreCase(curRank) && !"owner".equalsIgnoreCase(curRank) && s instanceof Player) {
            ModerationManager.sendClickableButtons((Player) s,
                    ColorUtil.c(" &7Хотите также снять группу &e" + curRank + "&7 до обычного Игрока? "),
                    new String[][]{{ColorUtil.c("&#FF5252&l[ ✖ СБРОСИТЬ ГРУППУ ДО DEFAULT ]"), "/lp user " + nick + " parent clear"}});
        }
    }

    private void handleUserSub(CommandSender s, String[] args) {
        // Support reversed order `/lp user permision clear <ник>` as well as `/lp user <ник> permission clear`!
        if (args.length >= 4 && isPermWord(args[1]) && ("clear".equalsIgnoreCase(args[2]) || "reset".equalsIgnoreCase(args[2]))) {
            executeUserPermissionClear(s, args[3], false);
            return;
        }
        if (args.length < 3) {
            s.sendMessage(ColorUtil.c("&eИспользование: &f/lp user <ник> <info | parent | permission | meta | clear>"));
            s.sendMessage(ColorUtil.c("&7• Снять все права и звезду (*): &#FFD740/lp user <ник> permission clear"));
            return;
        }
        String nick = args[1];
        String act = args[2].toLowerCase(Locale.ROOT);
        if ("clear".equals(act) || "reset".equals(act)) {
            executeUserPermissionClear(s, nick, true);
            return;
        }
        if ("info".equals(act)) {
            String rankId = plugin.getStore().getSavedRank(nick);
            RankManager.RankDef r = plugin.getRanks().byId(rankId);
            Map<String, Boolean> perms = plugin.getStore().getUserPermissions(nick);
            String uPref = plugin.getStore().getUserCustomPrefix(nick);
            s.sendMessage(ColorUtil.c("&#00E676&l[LP] Информация об игроке &f" + nick + "&7:"));
            s.sendMessage(ColorUtil.c(" &7• Основная группа: &e" + r.id + " &8(Вес " + r.weight + ") &7→ " + r.shortLabel));
            s.sendMessage(ColorUtil.c(" &7• Личный префикс: " + (uPref.isEmpty() ? "&8нет (от группы)" : uPref)));
            s.sendMessage(ColorUtil.c(" &7• Личных прав: &b" + perms.size() + (perms.isEmpty() ? "" : " &8" + perms.keySet())));
            return;
        }
        if ("parent".equals(act)) {
            if (args.length >= 4 && ("clear".equalsIgnoreCase(args[3]) || ("remove".equalsIgnoreCase(args[3]) && args.length == 4))) {
                plugin.getRanks().setRank(nick, "default", true);
                Player target = Bukkit.getPlayerExact(nick);
                if (target != null) plugin.getHud().updatePlayerHud(target);
                s.sendMessage(ColorUtil.c("&#00E676[LP] ✔ Группа игрока &f" + nick + " &#00E676сброшена до &fdefault&7."));
                return;
            }
            if (args.length < 5) {
                s.sendMessage(ColorUtil.c("&eИспользование: &f/lp user " + nick + " parent <set|add|remove|clear> <группа>"));
                return;
            }
            String mode = args[3].toLowerCase(Locale.ROOT);
            String group = args[4].toLowerCase(Locale.ROOT);
            if ("remove".equals(mode) || "clear".equals(mode)) {
                plugin.getRanks().setRank(nick, "default", true);
                Player target = Bukkit.getPlayerExact(nick);
                if (target != null) plugin.getHud().updatePlayerHud(target);
                s.sendMessage(ColorUtil.c("&#00E676[LP] ✔ Группа игрока &f" + nick + " &#00E676сброшена до &fdefault&7."));
            } else {
                if (!plugin.getRanks().exists(group)) {
                    s.sendMessage(ColorUtil.c("&c[LP] Группа '" + group + "' не найдена! Список: /lp listgroups"));
                    return;
                }
                plugin.getRanks().setRank(nick, group, true);
                RankManager.RankDef def = plugin.getRanks().byId(group);
                s.sendMessage(ColorUtil.c("&#00E676[LP] ✔ Игроку &f" + nick + " &#00E676установлена привилегия " + def.shortLabel + " &7(&f" + def.id + "&7)!"));
                Player target = Bukkit.getPlayerExact(nick);
                if (target != null) {
                    plugin.getHud().updatePlayerHud(target);
                    target.sendMessage(ColorUtil.c("&#00E676&l★ ВАМ ВЫДАНА ПРИВИЛЕГИЯ: " + def.shortLabel + "&#00E676&l! &7Ваш кит: &f/kits"));
                    ColorUtil.sound(target, "ui.toast.challenge_complete", 1.0f, 1.15f);
                }
            }
            return;
        }
        if (isPermWord(act)) {
            if (args.length >= 4 && ("clear".equalsIgnoreCase(args[3]) || "reset".equalsIgnoreCase(args[3]))) {
                executeUserPermissionClear(s, nick, false);
                return;
            }
            if (args.length < 5) {
                s.sendMessage(ColorUtil.c("&eИспользование: &f/lp user " + nick + " permission <set|unset|clear> [право] [true|false]"));
                s.sendMessage(ColorUtil.c("&7• Очистить все права и звезду (*): &#FFD740/lp user " + nick + " permission clear"));
                return;
            }
            String op = args[3].toLowerCase(Locale.ROOT);
            String node = args[4].toLowerCase(Locale.ROOT);
            if ("unset".equals(op) || "remove".equals(op)) {
                plugin.getStore().unsetUserPermission(nick, node);
                if ("*".equals(node)) {
                    Player on = Bukkit.getPlayerExact(nick);
                    if (on != null && on.isOp()) { try { on.setOp(false); } catch (Throwable ignored) {} }
                }
                if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + nick + " permission unset " + node);
                }
                s.sendMessage(ColorUtil.c("&#00E676[LP] ✔ Право &f" + node + " &#00E676снято с игрока &f" + nick));
            } else {
                boolean val = args.length < 6 || !"false".equalsIgnoreCase(args[5]);
                plugin.getStore().setUserPermission(nick, node, val);
                if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + nick + " permission set " + node + " " + val);
                }
                s.sendMessage(ColorUtil.c("&#00E676[LP] ✔ Игроку &f" + nick + " &#00E676установлено право &e" + node + " &7= " + (val ? "&atrue" : "&cfalse")));
                if ("*".equals(node) && val) {
                    s.sendMessage(ColorUtil.c(" &8(&7Снять все права и звезду в любой момент: &#FFD740/lp user " + nick + " permission clear&8)"));
                }
            }
            Player on = Bukkit.getPlayerExact(nick);
            if (on != null) plugin.getStore().refreshPermissions(on, plugin.getRanks());
            return;
        }
        if ("meta".equals(act)) {
            if (args.length >= 4 && "clearprefix".equalsIgnoreCase(args[3])) {
                plugin.getStore().setUserCustomPrefix(nick, "");
                Player on = Bukkit.getPlayerExact(nick);
                if (on != null) plugin.getHud().updatePlayerHud(on);
                s.sendMessage(ColorUtil.c("&#00E676[LP] ✔ Личный префикс игрока &f" + nick + " &#00E676очищен."));
                return;
            }
            if (args.length >= 5 && "setprefix".equalsIgnoreCase(args[3])) {
                // Support both `/lp user X meta setprefix 100 "&c[BOSS] "` and `/lp user X meta setprefix "&c[BOSS] "`
                int startIdx = (args.length >= 6 && isInteger(args[4])) ? 5 : 4;
                String rawPref = stripQuotes(joinFrom(args, startIdx));
                plugin.getStore().setUserCustomPrefix(nick, rawPref);
                Player on = Bukkit.getPlayerExact(nick);
                if (on != null) plugin.getHud().updatePlayerHud(on);
                s.sendMessage(ColorUtil.c("&#00E676[LP] ✔ Префикс &f" + nick + " &#00E676изменён на: " + rawPref));
                return;
            }
            s.sendMessage(ColorUtil.c("&eИспользование: &f/lp user " + nick + " meta setprefix <префикс> | clearprefix"));
        }
    }

    private void handleGroupSub(CommandSender s, String[] args) {
        if (args.length < 3) {
            s.sendMessage(ColorUtil.c("&eИспользование: &f/lp group <группа> <info | permission | meta | setweight>"));
            return;
        }
        String gid = args[1].toLowerCase(Locale.ROOT);
        if (!plugin.getRanks().exists(gid)) {
            s.sendMessage(ColorUtil.c("&c[LP] Группа '" + gid + "' не существует! Создайте: /lp creategroup " + gid));
            return;
        }
        RankManager.RankDef def = plugin.getRanks().byId(gid);
        String act = args[2].toLowerCase(Locale.ROOT);
        if ("info".equals(act)) {
            Map<String, Boolean> perms = plugin.getStore().getGroupPermissions(gid);
            s.sendMessage(ColorUtil.c("&#00E676&l[LP] Группа &f" + def.id + " &8(Вес: &e" + def.weight + "&8):"));
            s.sendMessage(ColorUtil.c(" &7• Префикс: " + def.chatPrefix + "&7ПримерНика"));
            s.sendMessage(ColorUtil.c(" &7• Кит: &f/kit " + def.kitName + " &8| &7Домов: &e" + def.maxHomes + " &8| &7Приватов: &e" + def.maxClaims));
            s.sendMessage(ColorUtil.c(" &7• Доп. прав группы: &b" + perms.size() + (perms.isEmpty() ? "" : " &8" + perms.keySet())));
            return;
        }
        if ("setweight".equals(act) && args.length >= 4) {
            int w = parseIntSafe(args[3], def.weight);
            plugin.getRanks().createOrUpdateGroup(gid, w, def.chatPrefix);
            s.sendMessage(ColorUtil.c("&#00E676[LP] ✔ Вес группы &f" + gid + " &#00E676установлен на &e" + w));
            return;
        }
        if (isPermWord(act)) {
            if (args.length >= 4 && ("clear".equalsIgnoreCase(args[3]) || "reset".equalsIgnoreCase(args[3]))) {
                int removed = plugin.getStore().clearGroupPermissions(gid);
                if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null && !"owner".equals(gid)) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp group " + gid + " permission clear");
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp group " + gid + " setweight " + def.weight);
                }
                for (Player on : Bukkit.getOnlinePlayers()) {
                    plugin.getStore().refreshPermissions(on, plugin.getRanks());
                }
                s.sendMessage(ColorUtil.c("&#00E676[LP] ✔ Все дополнительные права группы &f" + gid + " &#00E676очищены (&f" + removed + " шт.&#00E676)!"));
                return;
            }
            if (args.length >= 5) {
                String op = args[3].toLowerCase(Locale.ROOT);
                String node = args[4].toLowerCase(Locale.ROOT);
                if ("*".equals(node) && !"owner".equals(gid)) {
                    s.sendMessage(ColorUtil.c("&#FF1744✖ Право звезды (*) на уровне групп разрешено ТОЛЬКО группе owner!"));
                    return;
                }
                if ("unset".equals(op) || "remove".equals(op)) {
                    plugin.getStore().unsetGroupPermission(gid, node);
                    s.sendMessage(ColorUtil.c("&#00E676[LP] ✔ Право &f" + node + " &#00E676удалено у группы &f" + gid));
                } else {
                    boolean val = args.length < 6 || !"false".equalsIgnoreCase(args[5]);
                    plugin.getStore().setGroupPermission(gid, node, val);
                    s.sendMessage(ColorUtil.c("&#00E676[LP] ✔ Группе &f" + gid + " &#00E676добавлено право &e" + node + " &7= " + (val ? "&atrue" : "&cfalse")));
                }
                for (Player on : Bukkit.getOnlinePlayers()) {
                    plugin.getStore().refreshPermissions(on, plugin.getRanks());
                }
                return;
            }
        }
        if ("meta".equals(act) && args.length >= 5 && "setprefix".equalsIgnoreCase(args[3])) {
            int startIdx = (args.length >= 6 && isInteger(args[4])) ? 5 : 4;
            String rawPref = stripQuotes(joinFrom(args, startIdx));
            plugin.getRanks().createOrUpdateGroup(gid, def.weight, rawPref);
            for (Player on : Bukkit.getOnlinePlayers()) {
                plugin.getHud().updatePlayerHud(on);
            }
            s.sendMessage(ColorUtil.c("&#00E676[LP] ✔ Префикс группы &f" + gid + " &#00E676обновлён: " + rawPref));
            return;
        }
        s.sendMessage(ColorUtil.c("&eИспользование: &f/lp group " + gid + " <info | setweight <N> | permission set <право> | meta setprefix <префикс>>"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void openEditorSession(CommandSender s) {
        String webUrl = "https://kinggrief.vercel.app/lp-editor";
        s.sendMessage("");
        s.sendMessage(ColorUtil.c("&#00E676&l⚡ LUCKPERMS WEB & GUI EDITOR (KINGGRIEF v4.0)"));
        s.sendMessage(ColorUtil.c("&7Редактируйте все 40 привилегий, префиксы, веса, права и игроков:"));
        if (s instanceof Player) {
            Player p = (Player) s;
            boolean sentButtons = false;
            try {
                Class<?> bcCls = Class.forName("net.md_5.bungee.api.chat.BaseComponent");
                Class<?> tcCls = Class.forName("net.md_5.bungee.api.chat.TextComponent");
                Class<?> ceCls = Class.forName("net.md_5.bungee.api.chat.ClickEvent");
                Class<?> actCls = Class.forName("net.md_5.bungee.api.chat.ClickEvent$Action");
                Constructor<?> tcCtor = tcCls.getConstructor(String.class);
                Constructor<?> ceCtor = ceCls.getConstructor(actCls, String.class);
                Method setClick = bcCls.getMethod("setClickEvent", ceCls);

                Object webBtn = tcCtor.newInstance(ColorUtil.c("  &#00E676&l[ 🌐 ОТКРЫТЬ WEB LP EDITOR ]"));
                setClick.invoke(webBtn, ceCtor.newInstance(Enum.valueOf((Class<Enum>) actCls, "OPEN_URL"), webUrl));

                Object space = tcCtor.newInstance("  ");

                Object guiBtn = tcCtor.newInstance(ColorUtil.c("&#FFD740&l[ ⚙ ОТКРЫТЬ GUI В ИГРЕ ]"));
                setClick.invoke(guiBtn, ceCtor.newInstance(Enum.valueOf((Class<Enum>) actCls, "RUN_COMMAND"), "/lp gui"));

                Object arr = Array.newInstance(bcCls, 3);
                Array.set(arr, 0, webBtn);
                Array.set(arr, 1, space);
                Array.set(arr, 2, guiBtn);

                Object spigot = p.spigot();
                for (Method m : spigot.getClass().getMethods()) {
                    if (m.getName().equals("sendMessage") && m.getParameterCount() == 1
                            && m.getParameterTypes()[0].isArray()) {
                        m.invoke(spigot, arr);
                        sentButtons = true;
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }
            if (!sentButtons) {
                p.sendMessage(ColorUtil.c(" &a▸ Веб-Редактор: &f" + webUrl));
                p.sendMessage(ColorUtil.c(" &e▸ Внутриигровой редактор: &f/lp gui"));
            } else {
                p.sendMessage(ColorUtil.c(" &8(&7Прямая ссылка: &f" + webUrl + "&8)"));
            }
            ColorUtil.sound(p, "block.enchantment_table.use", 1.0f, 1.25f);
        } else {
            s.sendMessage(ColorUtil.c(" &a▸ Ссылка на Веб-Редактор: &f" + webUrl));
        }
        s.sendMessage(ColorUtil.c("&7После сохранения на сайте вставьте команду: &#80D8FF/lp applyedits <код>"));
        s.sendMessage("");
    }

    private void applyWebEdits(CommandSender s, String token) {
        String clean = token.trim();
        if (clean.startsWith("LP4_")) {
            clean = clean.substring(4);
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(clean);
            String payload = new String(decoded, StandardCharsets.UTF_8);
            String[] lines = payload.split("\\n");
            int applied = 0;
            for (String ln : lines) {
                String c = ln.trim();
                if (c.isEmpty()) continue;
                if (c.startsWith("/")) c = c.substring(1);
                if (c.toLowerCase(Locale.ROOT).startsWith("lp ")) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
                    applied++;
                }
            }
            for (Player on : Bukkit.getOnlinePlayers()) {
                plugin.getStore().refreshPermissions(on, plugin.getRanks());
                plugin.getHud().updatePlayerHud(on);
            }
            s.sendMessage(ColorUtil.c("&#00E676&l[LP] ✔ Успешно применено изменений из Web LP Editor: &f" + applied + "&#00E676&l!"));
            if (s instanceof Player) {
                ColorUtil.sound((Player) s, "entity.player.levelup", 1.0f, 1.2f);
            }
        } catch (Exception ex) {
            s.sendMessage(ColorUtil.c("&c[LP] Неверный токен Web LP Editor! Скопируйте полную команду с сайта https://kinggrief.vercel.app/lp-editor"));
        }
    }

    private void sendHelp(CommandSender s) {
        s.sendMessage(ColorUtil.c("&#00E676&l⚡ КОМАНДЫ LUCKPERMS (СТРОГО ТОЛЬКО OWNER · v5.9):"));
        s.sendMessage(ColorUtil.c(" &#00E676/lp editor &7— Открыть Веб LP-Редактор (kinggrief.vercel.app/lp-editor)"));
        s.sendMessage(ColorUtil.c(" &#FFD740/lp gui &7— Открыть внутриигровое меню групп и прав"));
        s.sendMessage(ColorUtil.c(" &#FF5252/lp user <ник> permission clear &7— Снять ВСЕ права, звезду (*) и OP!"));
        s.sendMessage(ColorUtil.c(" &f/lp user <ник> parent set <группа> &7— Выдать привилегию игроку"));
        s.sendMessage(ColorUtil.c(" &f/lp user <ник> parent clear &7— Сбросить группу игрока до default"));
        s.sendMessage(ColorUtil.c(" &f/lp user <ник> permission set <право> [true|false] &7— Выдать право"));
        s.sendMessage(ColorUtil.c(" &f/lp user <ник> meta setprefix <префикс> &7— Личный префикс"));
        s.sendMessage(ColorUtil.c(" &f/lp group <группа> permission clear &7— Очистить доп. права группы"));
        s.sendMessage(ColorUtil.c(" &f/lp group <группа> info | setweight <N> | permission set <право>"));
        s.sendMessage(ColorUtil.c(" &f/lp listgroups &8| &f/lp tracks &8| &f/lp sync"));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<String>();
        if (!hasLpAccess(s)) return out;
        if (args.length == 1) {
            filter(out, args[0], Arrays.asList("editor", "gui", "applyedits", "user", "group", "listgroups", "tracks", "creategroup", "deletegroup", "sync", "help"));
        } else if (args.length == 2) {
            if ("user".equalsIgnoreCase(args[0])) {
                for (Player p : Bukkit.getOnlinePlayers()) filterOne(out, args[1], p.getName());
            } else if ("group".equalsIgnoreCase(args[0]) || "deletegroup".equalsIgnoreCase(args[0])) {
                for (RankManager.RankDef r : plugin.getRanks().allRanks()) filterOne(out, args[1], r.id);
            }
        } else if (args.length == 3) {
            if ("user".equalsIgnoreCase(args[0])) {
                filter(out, args[2], Arrays.asList("info", "parent", "permission", "permision", "meta", "clear"));
            } else if ("group".equalsIgnoreCase(args[0])) {
                filter(out, args[2], Arrays.asList("info", "permission", "meta", "setweight"));
            }
        } else if (args.length == 4) {
            if ("parent".equalsIgnoreCase(args[2])) {
                filter(out, args[3], Arrays.asList("set", "add", "remove", "clear"));
            } else if (isPermWord(args[2])) {
                filter(out, args[3], Arrays.asList("clear", "set", "unset"));
            } else if ("meta".equalsIgnoreCase(args[2])) {
                filter(out, args[3], Arrays.asList("setprefix", "clearprefix"));
            }
        } else if (args.length == 5) {
            if ("parent".equalsIgnoreCase(args[2])) {
                for (RankManager.RankDef r : plugin.getRanks().allRanks()) filterOne(out, args[4], r.id);
            } else if (isPermWord(args[2]) && !"clear".equalsIgnoreCase(args[3])) {
                filter(out, args[4], Arrays.asList(COMMON_PERMS));
            }
        }
        return out;
    }

    private void filter(List<String> out, String prefix, List<String> candidates) {
        for (String c : candidates) filterOne(out, prefix, c);
    }

    private void filterOne(List<String> out, String prefix, String cand) {
        if (cand.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
            out.add(cand);
        }
    }

    private int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private boolean isInteger(String s) {
        try { Integer.parseInt(s); return true; } catch (Exception e) { return false; }
    }

    private String joinFrom(String[] arr, int idx) {
        StringBuilder sb = new StringBuilder();
        for (int i = idx; i < arr.length; i++) {
            if (i > idx) sb.append(' ');
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    private String stripQuotes(String s) {
        if (s != null && s.length() >= 2 && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
