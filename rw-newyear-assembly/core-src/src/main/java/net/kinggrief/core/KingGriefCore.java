package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class KingGriefCore extends JavaPlugin implements CommandExecutor, TabCompleter {

    private static KingGriefCore instance;

    private DataStore store;
    private RankManager ranks;
    private SpawnEngine spawn;
    private HudManager hud;
    private GuiManager guis;
    private GameplayListener listener;

    public static KingGriefCore get() {
        return instance;
    }

    public DataStore getStore() { return store; }
    public RankManager getRanks() { return ranks; }
    public SpawnEngine getSpawn() { return spawn; }
    public SpawnEngine getSpawnEngine() { return spawn; }
    public HudManager getHud() { return hud; }
    public GuiManager getGuis() { return guis; }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.store = new DataStore(this);
        this.ranks = new RankManager(this.store);
        this.spawn = new SpawnEngine(this);
        this.hud = new HudManager(this);
        this.guis = new GuiManager(this);
        this.listener = new GameplayListener(this);

        Bukkit.getPluginManager().registerEvents(listener, this);

        for (String cmdName : getDescription().getCommands().keySet()) {
            PluginCommand pc = getCommand(cmdName);
            if (pc != null) {
                pc.setExecutor(this);
                pc.setTabCompleter(this);
            }
        }

        spawn.initOnStartup();
        hud.start();
        listener.startLoops();

        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                store.save();
            }
        }, 3600L, 3600L);

        for (Player p : Bukkit.getOnlinePlayers()) {
            listener.setAuthed(p, true);
            hud.updatePlayerHud(p);
        }

        getLogger().info("==================================================");
        getLogger().info("  KINGGRIEF NEW YEAR REALLYWORLD CORE v3.0 READY!");
        getLogger().info("  Spawn 3D + 16 NPCs + 11 Cases + Auth + TAB + SB");
        getLogger().info("==================================================");
    }

    @Override
    public void onDisable() {
        if (store != null) store.save();
    }

    private boolean requireRankWeight(Player p, int minWeight, String minRankTitle) {
        if (p.isOp() || p.hasPermission("kinggrief.admin") || ranks.getRank(p).weight >= minWeight) {
            return true;
        }
        ColorUtil.sound(p, "entity.villager.no", 1f, 1f);
        p.sendMessage(ColorUtil.c("&cЭта команда доступна от привилегии &e" + minRankTitle
                + " &cи выше! Купить на: &#FFD740https://kinggrief.vercel.app"));
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();

        // Console-compatible admin commands
        if ("setrank".equals(cmd)) {
            if (!sender.hasPermission("kinggrief.admin") && !sender.isOp()) {
                sender.sendMessage(ColorUtil.c("&cНедостаточно прав!"));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ColorUtil.c("&eИспользование: /setrank <ник> <ранг>"));
                return true;
            }
            ranks.setRank(args[0], args[1], true);
            sender.sendMessage(ColorUtil.c("&#00E676✔ Игроку &f" + args[0] + " &aвыдан ранг &e" + args[1].toUpperCase()));
            return true;
        }
        if ("riliks".equals(cmd) && args.length >= 3 && ("give".equalsIgnoreCase(args[0]) || "set".equalsIgnoreCase(args[0]))) {
            if (!sender.hasPermission("kinggrief.admin") && !sender.isOp()) {
                sender.sendMessage(ColorUtil.c("&cНедостаточно прав!"));
                return true;
            }
            try {
                int amt = Integer.parseInt(args[2]);
                if ("give".equalsIgnoreCase(args[0])) store.addRelics(args[1], amt);
                else store.setRelics(args[1], amt);
                store.save();
                sender.sendMessage(ColorUtil.c("&#00E676✔ Баланс риликов игрока &f" + args[1] + " &aобновлён!"));
            } catch (NumberFormatException ex) {
                sender.sendMessage(ColorUtil.c("&cУкажите корректное число!"));
            }
            return true;
        }
        if ("cases".equals(cmd) && args.length >= 4 && "give".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("kinggrief.admin") && !sender.isOp()) {
                sender.sendMessage(ColorUtil.c("&cНедостаточно прав!"));
                return true;
            }
            try {
                int amt = Integer.parseInt(args[3]);
                store.addCaseKeys(args[1], args[2].toLowerCase(), amt);
                store.save();
                sender.sendMessage(ColorUtil.c("&#00E676✔ Выдано " + amt + " ключ(ей) " + args[2] + " игроку " + args[1]));
            } catch (NumberFormatException ex) {
                sender.sendMessage(ColorUtil.c("&cУкажите число ключей!"));
            }
            return true;
        }
        if ("unban".equals(cmd)) {
            if ((sender instanceof Player) && !requireRankWeight((Player) sender, 220, "D.GLMODER / СТ.МОДЕР")) return true;
            if (args.length < 1) { sender.sendMessage(ColorUtil.c("&e/unban <ник>")); return true; }
            store.unban(args[0]);
            store.save();
            Bukkit.broadcastMessage(ColorUtil.c("&#00E676🔓 Игрок &f" + args[0] + " &aразбанен администратором &e" + sender.getName()));
            return true;
        }
        if ("unmute".equals(cmd)) {
            if ((sender instanceof Player) && !requireRankWeight((Player) sender, 180, "ХЕЛПЕР / D.MODER")) return true;
            if (args.length < 1) { sender.sendMessage(ColorUtil.c("&e/unmute <ник>")); return true; }
            store.unmute(args[0]);
            store.save();
            sender.sendMessage(ColorUtil.c("&#00E676🔊 С игрока &f" + args[0] + " &aснят мут чата!"));
            return true;
        }
        if ("bc".equals(cmd) || "broadcast".equals(cmd)) {
            if ((sender instanceof Player) && !requireRankWeight((Player) sender, 230, "D.ML.ADMIN / ГЛ.МОДЕР")) return true;
            if (args.length < 1) { sender.sendMessage(ColorUtil.c("&e/bc <объявление>")); return true; }
            String msg = joinArgs(args, 0);
            Bukkit.broadcastMessage(ColorUtil.c("&8&m                                                    "));
            Bukkit.broadcastMessage(ColorUtil.c("  &#FF1744&l📢 ОБЪЯВЛЕНИЕ &8(&f" + sender.getName() + "&8):"));
            Bukkit.broadcastMessage(ColorUtil.c("  &f" + msg));
            Bukkit.broadcastMessage(ColorUtil.c("&8&m                                                    "));
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Command requires a player.");
            return true;
        }
        Player p = (Player) sender;

        // ================= AUTH COMMANDS =================
        if ("reg".equals(cmd) || "register".equals(cmd)) {
            if (store.isRegistered(p.getName())) {
                p.sendMessage(ColorUtil.c("&eВы уже зарегистрированы! Введите: &#00E676/login <пароль>"));
                return true;
            }
            if (args.length < 2) {
                p.sendMessage(ColorUtil.c("&eИспользование: &#00E676/reg <пароль> <повтор пароля>"));
                return true;
            }
            if (!args[0].equals(args[1])) {
                p.sendMessage(ColorUtil.c("&cПароли не совпадают! Попробуйте снова."));
                return true;
            }
            if (args[0].length() < 4 || args[0].length() > 32) {
                p.sendMessage(ColorUtil.c("&cДлина пароля должна быть от 4 до 32 символов!"));
                return true;
            }
            store.registerPassword(p, args[0]);
            store.save();
            listener.setAuthed(p, true);
            ColorUtil.sound(p, "ui.toast.challenge_complete", 1f, 1.2f);
            p.sendTitle(ColorUtil.c("&#00E676&l✔ УСПЕШНО!"), ColorUtil.c("&fПриятной игры! Открой &#FFD740/menu"), 5, 50, 10);
            p.sendMessage(ColorUtil.c("&#00E676✔ Вы успешно зарегистрировались! Не забудьте забрать подарок: &e/promo NEWYEAR"));
            return true;
        }
        if ("login".equals(cmd)) {
            if (listener.isAuthed(p)) {
                p.sendMessage(ColorUtil.c("&aВы уже авторизованы!"));
                return true;
            }
            if (!store.isRegistered(p.getName())) {
                p.sendMessage(ColorUtil.c("&eАккаунт не зарегистрирован! Введите: &#00E676/reg <пароль> <повтор>"));
                return true;
            }
            if (args.length < 1) {
                p.sendMessage(ColorUtil.c("&eВведите: &#00E676/login <пароль>"));
                return true;
            }
            if (!store.checkPassword(p.getName(), args[0])) {
                ColorUtil.sound(p, "entity.villager.no", 1f, 0.8f);
                p.sendMessage(ColorUtil.c("&cНеверный пароль! Попробуйте ещё раз."));
                return true;
            }
            listener.setAuthed(p, true);
            store.save();
            ColorUtil.sound(p, "entity.player.levelup", 1f, 1.2f);
            p.sendTitle(ColorUtil.c("&#00E676&l✔ ДОБРО ПОЖАЛОВАТЬ!"), ColorUtil.c("&fГлавное меню: &#FFD740/menu"), 5, 45, 10);
            return true;
        }
        if ("changepassword".equals(cmd)) {
            if (args.length < 2) { p.sendMessage(ColorUtil.c("&e/changepassword <старый> <новый>")); return true; }
            if (!store.checkPassword(p.getName(), args[0])) {
                p.sendMessage(ColorUtil.c("&cСтарый пароль указан неверно!"));
                return true;
            }
            store.changePassword(p.getName(), args[1]);
            store.save();
            p.sendMessage(ColorUtil.c("&#00E676✔ Пароль успешно изменён!"));
            return true;
        }
        if ("logout".equals(cmd)) {
            listener.setAuthed(p, false);
            p.teleport(spawn.getSpawnLocation());
            p.sendMessage(ColorUtil.c("&eВы вышли из аккаунта. Введите /login <пароль>."));
            return true;
        }

        // ================= MENUS & SPAWN & EVENTS =================
        if ("menu".equals(cmd)) { guis.openMainMenu(p); return true; }
        if ("donate".equals(cmd)) { guis.openDonateMenu(p); return true; }
        if ("kp".equals(cmd)) { guis.openKpMenu(p); return true; }
        if ("media".equals(cmd)) { guis.openMediaMenu(p); return true; }
        if ("shop".equals(cmd)) { guis.openShopMenu(p); return true; }
        if ("nyshop".equals(cmd)) { guis.openNyShop(p); return true; }
        if ("gifts".equals(cmd)) { guis.openGiftsMenu(p); return true; }
        if ("cases".equals(cmd)) { guis.openAllCasesMenu(p); return true; }
        if ("kits".equals(cmd)) { guis.openKitsMenu(p); return true; }
        if ("rtp".equals(cmd)) { guis.openRtpMenu(p); return true; }
        if ("buyer".equals(cmd)) { guis.openBuyerMenu(p); return true; }
        if ("jobs".equals(cmd)) { guis.openJobsMenu(p); return true; }
        if ("bp".equals(cmd)) { guis.openBattlePassMenu(p); return true; }

        if ("sb".equals(cmd)) {
            boolean on = store.toggleSb(p);
            hud.updatePlayerHud(p);
            p.sendMessage(ColorUtil.c(on ? "&#00E676✔ Боковая панель включена!" : "&eБоковая панель скрыта."));
            return true;
        }
        if ("spawn".equals(cmd)) {
            store.setBack(p, p.getLocation());
            p.teleport(spawn.getSpawnLocation());
            ColorUtil.sound(p, "entity.enderman.teleport", 1f, 1.1f);
            p.sendMessage(ColorUtil.c("&#80D8FF🎄 Вы телепортированы на Новогодний Спавн KINGGRIEF!"));
            return true;
        }
        if ("setspawn".equals(cmd)) {
            if (!requireRankWeight(p, 280, "OPKA / OWNER")) return true;
            Location l = p.getLocation();
            getConfig().set("spawn-world", l.getWorld().getName());
            getConfig().set("spawn-x", l.getX());
            getConfig().set("spawn-y", l.getY());
            getConfig().set("spawn-z", l.getZ());
            getConfig().set("spawn-yaw", l.getYaw());
            getConfig().set("spawn-pitch", l.getPitch());
            saveConfig();
            p.sendMessage(ColorUtil.c("&#00E676✔ Точка спавна обновлена!"));
            return true;
        }
        if ("spawnbuild".equals(cmd)) {
            if (!requireRankWeight(p, 280, "OPKA / OWNER")) return true;
            p.sendMessage(ColorUtil.c("&#80D8FF❄ Запускаем перестройку 3D Новогоднего Спавна, всех 16 NPC и 11 кейсов..."));
            spawn.buildSpawnAsyncBatched(p);
            return true;
        }
        if ("mine".equals(cmd)) {
            p.teleport(new Location(spawn.getSpawnWorld(), -30.5, 100.0, 20.5, 90f, 10f));
            ColorUtil.sound(p, "entity.enderman.teleport", 1f, 1.1f);
            p.sendMessage(ColorUtil.c("&#FFEE58⛏ Добро пожаловать на Авто-Шахту! Руда обновляется каждые 3 минуты."));
            return true;
        }
        if ("airdrop".equals(cmd)) {
            if (args.length > 0 && "start".equalsIgnoreCase(args[0])) {
                if (!requireRankWeight(p, 260, "КП / OPKA / OWNER")) return true;
                spawn.triggerMysticAirdrop();
                return true;
            }
            Location air = spawn.getActiveAirdropLoc();
            if (air == null) {
                p.sendMessage(ColorUtil.c("&eАктивного Аирдропа сейчас нет. До следующего: &f"
                        + ColorUtil.fmtTime(spawn.getAirdropSecondsLeft())));
            } else {
                p.sendMessage(ColorUtil.c("&#FF1744☄ Мистический Аирдроп находится на: &#FFD740X: "
                        + air.getBlockX() + ", Y: " + air.getBlockY() + ", Z: " + air.getBlockZ()));
            }
            return true;
        }

        // ================= PROMO CODES =================
        if ("promo".equals(cmd)) {
            if (args.length < 1) {
                p.sendMessage(ColorUtil.c("&eДоступные промокоды: &#00E676/promo NEWYEAR &7| &#00E676/promo KINGGRIEF &7| &#00E676/promo FREE"));
                return true;
            }
            String code = args[0].toUpperCase();
            if (!"NEWYEAR".equals(code) && !"KINGGRIEF".equals(code) && !"FREE".equals(code)) {
                p.sendMessage(ColorUtil.c("&cТакого промокода не существует! Попробуйте: &e/promo NEWYEAR"));
                return true;
            }
            if (store.hasUsedPromo(p.getName(), code)) {
                p.sendMessage(ColorUtil.c("&cВы уже активировали промокод &e" + code + "&c!"));
                return true;
            }
            store.markUsedPromo(p.getName(), code);
            if ("NEWYEAR".equals(code)) {
                store.addCoins(p.getName(), 10000.0);
                store.addCaseKeys(p.getName(), "summer", 1);
                p.sendMessage(ColorUtil.c("&#80D8FF🎄 Промокод NEWYEAR активирован! &#FFEA00+10 000$ &fи &b1 Ключ Сезонного Кейса (/cases)&f!"));
            } else if ("KINGGRIEF".equals(code)) {
                store.addRelics(p.getName(), 50);
                p.getInventory().addItem(ItemFactory.godAura(2));
                p.sendMessage(ColorUtil.c("&#FFD740👑 Промокод KINGGRIEF активирован! &#00E5FF+50 риликов ❖ &fи &e2 Божьи Ауры&f!"));
            } else {
                store.addCoins(p.getName(), 5000.0);
                p.getInventory().addItem(ItemFactory.nyCandy(2));
                p.sendMessage(ColorUtil.c("&#00E676🎁 Промокод FREE активирован! &#FFEA00+5 000$ &fи &d2 Леденца&f!"));
            }
            store.save();
            ColorUtil.sound(p, "ui.toast.challenge_complete", 1f, 1.2f);
            return true;
        }

        // ================= KITS (/kit <id>) =================
        if ("kit".equals(cmd)) {
            if (args.length < 1) { guis.openKitsMenu(p); return true; }
            String kit = args[0].toLowerCase();
            RankManager.RankDef myRank = ranks.getRank(p);
            int reqW = 0;
            if ("start".equals(kit) || "newyear".equals(kit)) reqW = 0;
            else if ("media".equals(kit)) reqW = 70;
            else if ("kp".equals(kit)) reqW = 180;
            else reqW = ranks.byId(kit).weight;

            if (myRank.weight < reqW && !p.isOp() && !p.hasPermission("kinggrief.admin")) {
                p.sendMessage(ColorUtil.c("&cНабор &e/kit " + kit + " &cнедоступен вашему рангу! Повысить ранг: &#FFD740/donate"));
                return true;
            }
            long now = System.currentTimeMillis();
            long ready = store.getKitReadyAt(p.getName(), kit);
            if (now < ready && !p.hasPermission("kinggrief.admin")) {
                p.sendMessage(ColorUtil.c("&eПодождите ещё &f" + ColorUtil.fmtTime((ready - now) / 1000L) + " &eдо получения набора!"));
                return true;
            }
            long cdSec = ("start".equals(kit) || "newyear".equals(kit)) ? 3600L : 21600L;
            store.setKitReadyAt(p.getName(), kit, now + cdSec * 1000L);
            store.save();
            ItemFactory.giveKitItems(p, kit);
            ColorUtil.sound(p, "item.armor.equip_netherite", 1f, 1.1f);
            p.sendMessage(ColorUtil.c("&#00E676🎁 Набор &e" + kit.toUpperCase() + " &aвыдан в ваш инвентарь!"));
            return true;
        }

        // ================= AUCTION (/ah, /ah sell <price>) =================
        if ("ah".equals(cmd)) {
            if (args.length >= 2 && "sell".equalsIgnoreCase(args[0])) {
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand == null || hand.getType() == Material.AIR) {
                    p.sendMessage(ColorUtil.c("&cВозьмите предмет в основную руку для продажи на аукционе!"));
                    return true;
                }
                try {
                    double price = Double.parseDouble(args[1]);
                    if (price < 10 || price > 100000000) {
                        p.sendMessage(ColorUtil.c("&cЦена должна быть от 10$ до 100 000 000$!"));
                        return true;
                    }
                    ItemStack toSell = hand.clone();
                    p.getInventory().setItemInMainHand(null);
                    DataStore.AuctionLot lot = store.addAuctionLot(p.getName(), toSell, price);
                    store.save();
                    ColorUtil.sound(p, "entity.experience_orb.pickup", 1f, 1.2f);
                    p.sendMessage(ColorUtil.c("&#00E676⚖ Ваш предмет выставлен на Аукцион (#" + lot.id + ") за &#FFEA00" + ColorUtil.fmtMoney(price) + "$&a!"));
                } catch (NumberFormatException ex) {
                    p.sendMessage(ColorUtil.c("&cУкажите цену числом: &e/ah sell 15000"));
                }
                return true;
            }
            guis.openAuctionMenu(p, 0);
            return true;
        }

        // ================= CLANS (/clan & /cc) =================
        if ("cc".equals(cmd)) {
            DataStore.Clan cl = store.getClanOf(p.getName());
            if (cl == null) { p.sendMessage(ColorUtil.c("&cВы не состоите в клане! Создайте: &e/clan create <тег>")); return true; }
            if (args.length < 1) { p.sendMessage(ColorUtil.c("&e/cc <сообщение> &7(или пишите &f@сообщение &7в обычный чат)")); return true; }
            String line = ColorUtil.c("&8[&#B388FFКлан " + cl.tag + "&8] &e" + p.getName() + "&7: &f" + joinArgs(args, 0));
            for (Player on : Bukkit.getOnlinePlayers()) {
                if (cl.members.contains(on.getName().toLowerCase())) on.sendMessage(line);
            }
            return true;
        }
        if ("clan".equals(cmd)) {
            return handleClanCommand(p, args);
        }

        // ================= REGION / CLAIM MANAGEMENT (/rg) =================
        if ("rg".equals(cmd)) {
            DataStore.Claim cl = store.getClaimAt(p.getLocation());
            if (args.length == 0 || "info".equalsIgnoreCase(args[0])) {
                if (cl == null) {
                    p.sendMessage(ColorUtil.c("&eЗдесь свободная территория! Поставьте блок руды-привата из кита или &6/shop&e."));
                } else {
                    p.sendMessage(ColorUtil.c("&#00E676🛡 Приват #" + cl.id + " &8| &7Владелец: &f" + cl.owner
                            + " &8| &7Размер: &e" + (cl.radius * 2) + "×" + (cl.radius * 2)
                            + " &8| &7Участники: &a" + cl.members));
                }
                return true;
            }
            if (cl == null || (!cl.owner.equalsIgnoreCase(p.getName()) && !p.hasPermission("kinggrief.admin"))) {
                p.sendMessage(ColorUtil.c("&cВстаньте внутрь своего привата для управления им!"));
                return true;
            }
            if ("add".equalsIgnoreCase(args[0]) && args.length >= 2) {
                cl.members.add(args[1].toLowerCase());
                store.save();
                p.sendMessage(ColorUtil.c("&#00E676✔ Игрок &f" + args[1] + " &aдобавлен в ваш приват!"));
            } else if ("remove".equalsIgnoreCase(args[0]) && args.length >= 2) {
                cl.members.remove(args[1].toLowerCase());
                store.save();
                p.sendMessage(ColorUtil.c("&eИгрок &f" + args[1] + " &eудалён из привата."));
            } else {
                p.sendMessage(ColorUtil.c("&eКоманды привата: &f/rg info&7, &f/rg add <ник>&7, &f/rg remove <ник>"));
            }
            return true;
        }

        // ================= HOMES & TPA & BACK =================
        if ("sethome".equals(cmd)) {
            String hName = args.length > 0 ? args[0].toLowerCase() : "home";
            Map<String, Location> homes = store.getHomes(p.getName());
            RankManager.RankDef rk = ranks.getRank(p);
            if (!homes.containsKey(hName) && homes.size() >= rk.maxHomes) {
                p.sendMessage(ColorUtil.c("&cДостигнут лимит домов вашего ранга (&e" + rk.maxHomes + "&c)! Увеличьте лимит: &#FFD740/donate"));
                return true;
            }
            store.setHome(p.getName(), hName, p.getLocation());
            store.save();
            p.sendMessage(ColorUtil.c("&#00E676🏠 Точка дома &e" + hName + " &aуспешно сохранена!"));
            return true;
        }
        if ("home".equals(cmd)) {
            String hName = args.length > 0 ? args[0].toLowerCase() : "home";
            Map<String, Location> homes = store.getHomes(p.getName());
            Location dst = homes.get(hName);
            if (dst == null && !homes.isEmpty() && args.length == 0) {
                dst = homes.values().iterator().next();
            }
            if (dst == null) {
                p.sendMessage(ColorUtil.c("&cТочка дома не найдена! Создайте: &e/sethome"));
                return true;
            }
            store.setBack(p, p.getLocation());
            p.teleport(dst);
            ColorUtil.sound(p, "entity.enderman.teleport", 1f, 1.1f);
            p.sendMessage(ColorUtil.c("&#00E676🏠 Телепортация домой!"));
            return true;
        }
        if ("delhome".equals(cmd)) {
            String hName = args.length > 0 ? args[0].toLowerCase() : "home";
            if (store.delHome(p.getName(), hName)) {
                store.save();
                p.sendMessage(ColorUtil.c("&eТочка дома &f" + hName + " &eудалена."));
            } else {
                p.sendMessage(ColorUtil.c("&cТакого дома нет!"));
            }
            return true;
        }
        if ("homes".equals(cmd)) {
            Map<String, Location> homes = store.getHomes(p.getName());
            p.sendMessage(ColorUtil.c("&#FFD740🏠 Ваши дома (&f" + homes.size() + "/" + ranks.getRank(p).maxHomes + "&#FFD740): &f"
                    + (homes.isEmpty() ? "Нет домов (/sethome)" : homes.keySet())));
            return true;
        }
        if ("tpa".equals(cmd) || "tpahere".equals(cmd)) {
            if (args.length < 1) { p.sendMessage(ColorUtil.c("&e/" + cmd + " <ник>")); return true; }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null || target.equals(p)) {
                p.sendMessage(ColorUtil.c("&cИгрок не найден в сети!"));
                return true;
            }
            boolean here = "tpahere".equals(cmd);
            store.sendTpa(p, target, here);
            p.sendMessage(ColorUtil.c("&#00E676✔ Запрос на телепортацию отправлен игроку &f" + target.getName()));
            target.sendMessage(ColorUtil.c("&#FFD740⚡ Игрок &f" + p.getName()
                    + (here ? " &eпросит телепортироваться К НЕМУ!" : " &eпросит телепортироваться К ВАМ!")
                    + "\n&aПринять: &l/tpaccept &8| &cОтклонить: &l/tpdeny"));
            ColorUtil.sound(target, "block.note_block.pling", 1f, 1.4f);
            return true;
        }
        if ("tpaccept".equals(cmd)) {
            boolean[] isHere = new boolean[1];
            UUID senderId = store.popTpaSender(p, isHere);
            if (senderId == null) { p.sendMessage(ColorUtil.c("&cУ вас нет активных запросов на телепортацию!")); return true; }
            Player senderP = Bukkit.getPlayer(senderId);
            if (senderP == null) { p.sendMessage(ColorUtil.c("&cИгрок уже вышел с сервера.")); return true; }
            if (isHere[0]) {
                store.setBack(p, p.getLocation());
                p.teleport(senderP.getLocation());
            } else {
                store.setBack(senderP, senderP.getLocation());
                senderP.teleport(p.getLocation());
            }
            p.sendMessage(ColorUtil.c("&#00E676✔ Запрос принят!"));
            senderP.sendMessage(ColorUtil.c("&#00E676✔ Игрок &f" + p.getName() + " &aпринял ваш запрос!"));
            return true;
        }
        if ("tpdeny".equals(cmd)) {
            store.popTpaSender(p, new boolean[1]);
            p.sendMessage(ColorUtil.c("&eЗапрос на телепортацию отклонён."));
            return true;
        }
        if ("back".equals(cmd)) {
            if (!requireRankWeight(p, 40, "BUNNY (59₽)")) return true;
            Location b = store.getBack(p);
            if (b == null) { p.sendMessage(ColorUtil.c("&cНет сохранённой точки возврата!")); return true; }
            p.teleport(b);
            p.sendMessage(ColorUtil.c("&#00E676✔ Вы вернулись на предыдущее место!"));
            return true;
        }

        // ================= ECONOMY (/bal, /pay, /baltop, /riliks) =================
        if ("bal".equals(cmd) || "balance".equals(cmd)) {
            String target = args.length > 0 ? args[0] : p.getName();
            p.sendMessage(ColorUtil.c("&#FFEA00💰 Баланс &f" + target + "&#FFEA00: &#00E676"
                    + ColorUtil.fmtMoney(store.getCoins(target)) + "$ &8| &#00E5FF" + store.getRelicsByName(target) + " риликов ❖"));
            return true;
        }
        if ("riliks".equals(cmd)) {
            p.sendMessage(ColorUtil.c("&#00E5FF❖ Ваши рилики: &f" + store.getRelics(p)
                    + " ❖ &8| &7Магазин: &e/shop &8| &7Пополнить от 19₽: &#FFD740https://kinggrief.vercel.app/relics"));
            return true;
        }
        if ("pay".equals(cmd)) {
            if (args.length < 2) { p.sendMessage(ColorUtil.c("&e/pay <ник> <сумма>")); return true; }
            Player dst = Bukkit.getPlayerExact(args[0]);
            if (dst == null || dst.equals(p)) { p.sendMessage(ColorUtil.c("&cУкажите другого игрока онлайн!")); return true; }
            try {
                double amt = Double.parseDouble(args[1]);
                if (amt < 1) { p.sendMessage(ColorUtil.c("&cМинимальный перевод — 1$!")); return true; }
                if (!store.takeCoins(p.getName(), amt)) { p.sendMessage(ColorUtil.c("&cНедостаточно монет!")); return true; }
                store.addCoins(dst.getName(), amt);
                store.save();
                p.sendMessage(ColorUtil.c("&#00E676💸 Вы перевели &#FFEA00" + ColorUtil.fmtMoney(amt) + "$ &aигроку &f" + dst.getName()));
                dst.sendMessage(ColorUtil.c("&#00E676💸 Игрок &f" + p.getName() + " &aперевёл вам &#FFEA00+" + ColorUtil.fmtMoney(amt) + "$&a!"));
            } catch (NumberFormatException ex) {
                p.sendMessage(ColorUtil.c("&cУкажите корректную сумму!"));
            }
            return true;
        }
        if ("baltop".equals(cmd)) {
            List<Map.Entry<String, Double>> top = store.getBalTop(10);
            p.sendMessage(ColorUtil.c("&#FFD740&l🏆 ТОП БОГАЧЕЙ KINGGRIEF:"));
            for (int i = 0; i < top.size(); i++) {
                p.sendMessage(ColorUtil.c(" &e#" + (i + 1) + " &f" + top.get(i).getKey()
                        + " &8— &#00E676" + ColorUtil.fmtMoney(top.get(i).getValue().doubleValue()) + "$"));
            }
            return true;
        }

        // ================= DONATE PERKS & STAFF COMMANDS =================
        if ("wb".equals(cmd)) {
            if (!requireRankWeight(p, 10, "PEGAS (19₽)")) return true;
            p.openWorkbench(null, true);
            return true;
        }
        if ("hat".equals(cmd)) {
            if (!requireRankWeight(p, 10, "PEGAS (19₽)")) return true;
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() == Material.AIR) { p.sendMessage(ColorUtil.c("&cВозьмите предмет в руку!")); return true; }
            ItemStack oldHelm = p.getInventory().getHelmet();
            p.getInventory().setHelmet(hand);
            p.getInventory().setItemInMainHand(oldHelm);
            p.sendMessage(ColorUtil.c("&#00E676🎩 Новая шляпа надета!"));
            return true;
        }
        if ("ext".equals(cmd)) {
            if (!requireRankWeight(p, 20, "COBRA (39₽)")) return true;
            p.setFireTicks(0);
            p.sendMessage(ColorUtil.c("&#00E5FF💧 Вы потушили огонь!"));
            return true;
        }
        if ("ci".equals(cmd)) {
            if (!requireRankWeight(p, 20, "COBRA (39₽)")) return true;
            p.getInventory().clear();
            p.getInventory().setItem(8, ItemFactory.menuStar());
            p.sendMessage(ColorUtil.c("&eИнвентарь очищен!"));
            return true;
        }
        if ("near".equals(cmd)) {
            if (!requireRankWeight(p, 80, "VAMPIRE (99₽)")) return true;
            StringBuilder sb = new StringBuilder();
            for (Player other : p.getWorld().getPlayers()) {
                if (!other.equals(p) && !store.isVanished(other)) {
                    double d = other.getLocation().distance(p.getLocation());
                    if (d <= 150.0) {
                        if (sb.length() > 0) sb.append("&7, ");
                        sb.append("&f").append(other.getName()).append(" &e(").append((int) d).append("м)");
                    }
                }
            }
            p.sendMessage(ColorUtil.c("&#FFD740📡 Игроки рядом (150м): " + (sb.length() == 0 ? "&7Никого нет" : sb.toString())));
            return true;
        }
        if ("fly".equals(cmd)) {
            if (!requireRankWeight(p, 70, "MEDIA / GOD (149₽)")) return true;
            if (listener.isInPvp(p)) { p.sendMessage(ColorUtil.c("&cНельзя включать полёт в PvP!")); return true; }
            boolean next = !p.getAllowFlight();
            p.setAllowFlight(next);
            if (!next) p.setFlying(false);
            p.sendMessage(ColorUtil.c(next ? "&#00E676🕊 Режим полёта включён!" : "&eРежим полёта выключен."));
            return true;
        }
        if ("feed".equals(cmd)) {
            if (!requireRankWeight(p, 110, "GOD (149₽)")) return true;
            p.setFoodLevel(20);
            p.setSaturation(20f);
            p.sendMessage(ColorUtil.c("&#00E676🍖 Голод полностью утолён!"));
            return true;
        }
        if ("fix".equals(cmd)) {
            if (!requireRankWeight(p, 110, "GOD (149₽)")) return true;
            if (args.length > 0 && "all".equalsIgnoreCase(args[0])) {
                for (ItemStack it : p.getInventory().getContents()) repairItem(it);
                for (ItemStack it : p.getInventory().getArmorContents()) repairItem(it);
                p.sendMessage(ColorUtil.c("&#00E676🔨 Все предметы в инвентаре починены!"));
            } else {
                repairItem(p.getInventory().getItemInMainHand());
                p.sendMessage(ColorUtil.c("&#00E676🔨 Предмет в руке починен!"));
            }
            return true;
        }
        if ("ec".equals(cmd)) {
            if (!requireRankWeight(p, 150, "KING (199₽)")) return true;
            p.openInventory(p.getEnderChest());
            return true;
        }
        if ("anvil".equals(cmd)) {
            if (!requireRankWeight(p, 150, "KING (199₽)")) return true;
            p.openInventory(Bukkit.createInventory(p, org.bukkit.event.inventory.InventoryType.ANVIL));
            return true;
        }
        if ("heal".equals(cmd)) {
            if (!requireRankWeight(p, 150, "KING (199₽)")) return true;
            if (listener.isInPvp(p)) { p.sendMessage(ColorUtil.c("&cНельзя лечиться командой в бою!")); return true; }
            p.setHealth(p.getMaxHealth());
            p.setFoodLevel(20);
            p.sendMessage(ColorUtil.c("&#00E676❤ Здоровье восстановлено!"));
            return true;
        }

        // Staff / DKP Moderation
        if ("mute".equals(cmd)) {
            if (!requireRankWeight(p, 180, "ХЕЛПЕР / D.MODER (249₽)")) return true;
            if (args.length < 1) { p.sendMessage(ColorUtil.c("&e/mute <ник> [минуты] [причина]")); return true; }
            long mins = 15L;
            if (args.length >= 2) {
                try { mins = Long.parseLong(args[1].replaceAll("[^0-9]", "")); } catch (Exception ignored) {}
            }
            String reason = args.length >= 3 ? joinArgs(args, 2) : "Нарушение правил чата";
            store.mute(args[0], mins * 60000L, reason);
            store.save();
            Bukkit.broadcastMessage(ColorUtil.c("&#FF5252🔇 &f" + p.getName() + " &cвыдал мут игроку &e" + args[0]
                    + " &cна " + mins + " мин. Причина: &f" + reason));
            return true;
        }
        if ("kick".equals(cmd)) {
            if (!requireRankWeight(p, 190, "СТ.ХЕЛПЕР / D.MODER (249₽)")) return true;
            if (args.length < 1) { p.sendMessage(ColorUtil.c("&e/kick <ник> [причина]")); return true; }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { p.sendMessage(ColorUtil.c("&cИгрок не в сети!")); return true; }
            String reason = args.length >= 2 ? joinArgs(args, 1) : "Нарушение правил";
            target.kickPlayer(ColorUtil.c("&#FF1744Вы кикнуты модератором " + p.getName() + "\n&7Причина: &f" + reason));
            Bukkit.broadcastMessage(ColorUtil.c("&#FF9100⚡ &f" + p.getName() + " &eкикнул &f" + target.getName() + " &7(" + reason + ")"));
            return true;
        }
        if ("invsee".equals(cmd)) {
            if (!requireRankWeight(p, 195, "D.MODER (249₽) / МОДЕР")) return true;
            if (args.length < 1) { p.sendMessage(ColorUtil.c("&e/invsee <ник>")); return true; }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { p.sendMessage(ColorUtil.c("&cИгрок не в сети!")); return true; }
            p.openInventory(target.getInventory());
            return true;
        }
        if ("tp".equals(cmd)) {
            if (!requireRankWeight(p, 195, "D.MODER (249₽) / МОДЕР")) return true;
            if (args.length < 1) { p.sendMessage(ColorUtil.c("&e/tp <ник>")); return true; }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { p.sendMessage(ColorUtil.c("&cИгрок не в сети!")); return true; }
            p.teleport(target.getLocation());
            p.sendMessage(ColorUtil.c("&#00E676✔ Телепортация к &f" + target.getName()));
            return true;
        }
        if ("v".equals(cmd) || "vanish".equals(cmd)) {
            if (!requireRankWeight(p, 200, "МОДЕР / D.ST.MODER (349₽)")) return true;
            boolean vNow = store.toggleVanish(p);
            for (Player on : Bukkit.getOnlinePlayers()) {
                if (vNow) {
                    if (!on.hasPermission("kinggrief.admin") && ranks.getRank(on).weight < 200) on.hidePlayer(this, p);
                } else {
                    on.showPlayer(this, p);
                }
            }
            p.sendMessage(ColorUtil.c(vNow ? "&#00E676👻 Вы скрыты от обычных игроков!" : "&eРежим невидимости выключен."));
            return true;
        }
        if ("tempban".equals(cmd) || "ban".equals(cmd)) {
            if (!requireRankWeight(p, 205, "МОДЕР / D.ST.MODER (349₽)")) return true;
            if (args.length < 1) { p.sendMessage(ColorUtil.c("&e/" + cmd + " <ник> [причина]")); return true; }
            long dur = "tempban".equals(cmd) ? 86400000L : 0L;
            String reason = args.length >= 2 ? joinArgs(args, 1) : "Нарушение правил сервера";
            store.ban(args[0], dur, reason);
            store.save();
            Player online = Bukkit.getPlayerExact(args[0]);
            if (online != null) {
                online.kickPlayer(ColorUtil.c("&#FF1744&l⛔ ВЫ ЗАБАНЕНЫ\n&7Модератор: &f" + p.getName()
                        + "\n&7Причина: &f" + reason + "\n&#00E676Разбан на сайте: &fhttps://kinggrief.vercel.app"));
            }
            Bukkit.broadcastMessage(ColorUtil.c("&#FF1744⛔ &f" + p.getName() + " &cзаблокировал игрока &e" + args[0] + " &7(" + reason + ")"));
            return true;
        }
        if ("tphere".equals(cmd)) {
            if (!requireRankWeight(p, 270, "OPKA (999₽) / OWNER")) return true;
            if (args.length < 1) { p.sendMessage(ColorUtil.c("&e/tphere <ник>")); return true; }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { p.sendMessage(ColorUtil.c("&cИгрок не в сети!")); return true; }
            target.teleport(p.getLocation());
            p.sendMessage(ColorUtil.c("&#00E676✔ Игрок &f" + target.getName() + " &aтелепортирован к вам!"));
            return true;
        }
        if ("gm".equals(cmd)) {
            if (!requireRankWeight(p, 270, "OPKA (999₽) / OWNER")) return true;
            if (args.length < 1) { p.sendMessage(ColorUtil.c("&e/gm <0|1|2|3>")); return true; }
            String m = args[0].toLowerCase();
            if ("1".equals(m) || m.startsWith("c")) p.setGameMode(GameMode.CREATIVE);
            else if ("2".equals(m) || m.startsWith("a")) p.setGameMode(GameMode.ADVENTURE);
            else if ("3".equals(m) || m.startsWith("sp")) p.setGameMode(GameMode.SPECTATOR);
            else p.setGameMode(GameMode.SURVIVAL);
            p.sendMessage(ColorUtil.c("&#00E676🎮 Режим игры изменён на: &e" + p.getGameMode().name()));
            return true;
        }

        return true;
    }

    private boolean handleClanCommand(Player p, String[] args) {
        if (args.length == 0) {
            p.sendMessage(ColorUtil.c("&8&m                                                    "));
            p.sendMessage(ColorUtil.c(" &#B388FF&l🏰 КЛАНЫ KINGGRIEF:"));
            p.sendMessage(ColorUtil.c(" &e/clan create <тег> &7— Создать клан (15 000$)"));
            p.sendMessage(ColorUtil.c(" &e/clan invite <ник> &7— Пригласить игрока"));
            p.sendMessage(ColorUtil.c(" &e/clan join <тег> &7— Вступить по приглашению"));
            p.sendMessage(ColorUtil.c(" &e/clan sethome &7/ &e/clan home &7— База клана"));
            p.sendMessage(ColorUtil.c(" &e/clan pvp &7— Переключить PvP между своими"));
            p.sendMessage(ColorUtil.c(" &e/clan top &7— Открыть ТОП кланов | &e/cc <текст> &7— Чат"));
            p.sendMessage(ColorUtil.c("&8&m                                                    "));
            return true;
        }
        String sub = args[0].toLowerCase();
        if ("top".equals(sub)) {
            guis.openClanTopMenu(p);
            return true;
        }
        if ("create".equals(sub)) {
            if (store.getClanOf(p.getName()) != null) { p.sendMessage(ColorUtil.c("&cВы уже в клане!")); return true; }
            if (args.length < 2) { p.sendMessage(ColorUtil.c("&e/clan create <тег 2-8 букв>")); return true; }
            String tag = args[1].replaceAll("[^A-Za-zА-Яа-я0-9_]", "");
            if (tag.length() < 2 || tag.length() > 8) { p.sendMessage(ColorUtil.c("&cТег клана должен быть от 2 до 8 символов!")); return true; }
            if (store.getClanById(tag) != null) { p.sendMessage(ColorUtil.c("&cКлан с таким тегом уже существует!")); return true; }
            if (!store.takeCoins(p.getName(), 15000.0)) {
                p.sendMessage(ColorUtil.c("&cСоздание клана стоит 15 000$! Заработайте в /buyer и /jobs."));
                return true;
            }
            store.createClan(p.getName(), tag);
            store.save();
            hud.updatePlayerHud(p);
            Bukkit.broadcastMessage(ColorUtil.c("&#B388FF🏰 Игрок &f" + p.getName() + " &dосновал новый клан &8[&#B388FF" + tag + "&8]&d!"));
            return true;
        }
        if ("invite".equals(sub)) {
            DataStore.Clan cl = store.getClanOf(p.getName());
            if (cl == null || !cl.leader.equalsIgnoreCase(p.getName())) {
                p.sendMessage(ColorUtil.c("&cТолько лидер клана может приглашать игроков!"));
                return true;
            }
            if (args.length < 2) { p.sendMessage(ColorUtil.c("&e/clan invite <ник>")); return true; }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { p.sendMessage(ColorUtil.c("&cИгрок не в сети!")); return true; }
            store.inviteToClan(cl.id, target.getName());
            p.sendMessage(ColorUtil.c("&#00E676✔ Приглашение отправлено игроку &f" + target.getName()));
            target.sendMessage(ColorUtil.c("&#B388FF🏰 Вас пригласили в клан &8[&#B388FF" + cl.tag + "&8]&d! Вступить: &a/clan join " + cl.tag));
            return true;
        }
        if ("join".equals(sub)) {
            if (store.getClanOf(p.getName()) != null) { p.sendMessage(ColorUtil.c("&cСначала выйдите из текущего клана (/clan leave)!")); return true; }
            if (args.length < 2) { p.sendMessage(ColorUtil.c("&e/clan join <тег>")); return true; }
            String cid = args[1].toLowerCase();
            if (!store.hasClanInvite(p.getName(), cid)) { p.sendMessage(ColorUtil.c("&cУ вас нет приглашения в этот клан!")); return true; }
            DataStore.Clan cl = store.getClanById(cid);
            if (cl == null) { p.sendMessage(ColorUtil.c("&cКлан больше не существует.")); return true; }
            store.joinClan(p.getName(), cl);
            hud.updatePlayerHud(p);
            p.sendMessage(ColorUtil.c("&#00E676🏰 Вы вступили в клан [" + cl.tag + "]!"));
            return true;
        }
        DataStore.Clan my = store.getClanOf(p.getName());
        if (my == null) { p.sendMessage(ColorUtil.c("&cВы не состоите в клане!")); return true; }

        if ("leave".equals(sub)) {
            if (my.leader.equalsIgnoreCase(p.getName())) {
                store.disbandClan(my);
                p.sendMessage(ColorUtil.c("&cВаш клан [" + my.tag + "] распущен."));
            } else {
                store.leaveClan(p.getName(), my);
                p.sendMessage(ColorUtil.c("&eВы покинули клан [" + my.tag + "]."));
            }
            hud.updatePlayerHud(p);
        } else if ("sethome".equals(sub)) {
            if (!my.leader.equalsIgnoreCase(p.getName())) { p.sendMessage(ColorUtil.c("&cТолько лидер может ставить базу клана!")); return true; }
            my.home = p.getLocation();
            store.save();
            p.sendMessage(ColorUtil.c("&#00E676🏰 База клана сохранена! Телепорт: /clan home"));
        } else if ("home".equals(sub)) {
            if (my.home == null) { p.sendMessage(ColorUtil.c("&cЛидер ещё не установил базу клана (/clan sethome)!")); return true; }
            p.teleport(my.home);
            p.sendMessage(ColorUtil.c("&#00E676🏰 Телепортация на базу клана [" + my.tag + "]!"));
        } else if ("pvp".equals(sub)) {
            if (!my.leader.equalsIgnoreCase(p.getName())) { p.sendMessage(ColorUtil.c("&cТолько лидер клана может менять режим PvP!")); return true; }
            my.friendlyFire = !my.friendlyFire;
            store.save();
            p.sendMessage(ColorUtil.c("&#B388FF🏰 Клановый урон теперь: " + (my.friendlyFire ? "&cВКЛЮЧЁН" : "&aВЫКЛЮЧЕН")));
        } else if ("info".equals(sub)) {
            p.sendMessage(ColorUtil.c("&#B388FF🏰 Клан [" + my.tag + "] &8| &7Лидер: &f" + my.leader
                    + " &8| &7Убийств: &c" + my.kills + " &8| &7Состав: &a" + my.members));
        }
        return true;
    }

    private void repairItem(ItemStack it) {
        if (it == null || it.getType() == Material.AIR) return;
        ItemMeta m = it.getItemMeta();
        if (m instanceof Damageable) {
            ((Damageable) m).setDamage(0);
            it.setItemMeta(m);
        }
    }

    private String joinArgs(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase();
        if ("setrank".equals(cmd) && args.length == 2) {
            List<String> out = new ArrayList<String>();
            for (RankManager.RankDef r : ranks.allRanks()) {
                if (r.id.startsWith(args[1].toLowerCase())) out.add(r.id);
            }
            return out;
        }
        if ("kit".equals(cmd) && args.length == 1) {
            return Arrays.asList("start", "newyear", "pegas", "cobra", "bunny", "rabbit", "vampire", "god", "king", "media", "kp", "dmoder", "dadmin", "dkp", "opka", "downer", "curator");
        }
        if ("clan".equals(cmd) && args.length == 1) {
            return Arrays.asList("create", "invite", "join", "leave", "home", "sethome", "pvp", "top", "info");
        }
        if ("promo".equals(cmd) && args.length == 1) {
            return Arrays.asList("NEWYEAR", "KINGGRIEF", "FREE");
        }
        if ("rg".equals(cmd) && args.length == 1) {
            return Arrays.asList("info", "add", "remove");
        }
        if ("ah".equals(cmd) && args.length == 1) {
            return Collections.singletonList("sell");
        }
        return null;
    }
}
