package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class KingGriefCore extends JavaPlugin {

    public static final String[] ALL_CASE_IDS = new String[]{
            "donate", "sdonate", "legendary", "reallydev", "riliks",
            "allorno", "ditems", "kits", "money", "djamshut", "summer"
    };

    private DataStore store;
    private RankManager ranks;
    private LpManager lp;
    private BossEventManager bossEvents;
    private SpawnEngine spawnEngine;
    private GuiManager guis;
    private HudManager hud;
    private ModerationManager moderation;

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        if (VoidSpawnWorldGenerator.SPAWN_WORLD_NAME.equalsIgnoreCase(worldName)) {
            return new VoidSpawnWorldGenerator();
        }
        return null;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        for (World w : Bukkit.getWorlds()) {
            VoidSpawnWorldGenerator.silenceCommandSpam(w);
        }

        this.store = new DataStore(this);
        this.ranks = new RankManager(this.store);
        this.lp = new LpManager(this);
        this.bossEvents = new BossEventManager(this);
        this.spawnEngine = new SpawnEngine(this);
        this.guis = new GuiManager(this);
        this.hud = new HudManager(this);
        this.moderation = new ModerationManager(this);

        for (String lpCmd : new String[]{"lp", "luckperms", "lpeditor"}) {
            PluginCommand pc = getCommand(lpCmd);
            if (pc != null) {
                pc.setExecutor(this.lp);
                pc.setTabCompleter(this.lp);
            }
        }
        for (String modCmd : new String[]{"check", "sw", "spek", "mute", "unmute", "ban", "unban"}) {
            PluginCommand pc = getCommand(modCmd);
            if (pc != null) {
                pc.setExecutor(this.moderation);
                pc.setTabCompleter(this.moderation);
            }
        }
        for (String tcCmd : new String[]{"cases", "p", "money", "shop", "bounty", "rtp", "kit"}) {
            PluginCommand pc = getCommand(tcCmd);
            if (pc != null) {
                pc.setTabCompleter(this);
            }
        }

        Bukkit.getPluginManager().registerEvents(new GameplayListener(this), this);

        this.spawnEngine.initializeSpawnWorldAndBuild(false);
        this.bossEvents.start();
        this.hud.start();
        this.moderation.start();
        startWebsitePromoSyncLoop();

        new BukkitRunnable() {
            @Override
            public void run() {
                ranks.syncAllGroupsToLuckPermsIfPresent();
            }
        }.runTaskLater(this, 40L);

        for (Player on : Bukkit.getOnlinePlayers()) {
            this.store.refreshPermissions(on, this.ranks);
            this.bossEvents.syncBarsForJoin(on);
            this.hud.updatePlayerHud(on);
        }

        getLogger().info("============================================================");
        getLogger().info(" KINGGRIEF CORE v5.6.0 — NEW YEAR LUXURY FT x RW EDITION    ");
        getLogger().info(" 40 Luxury OWNER-Style Badges + Festive New Year Spawn 2026 ");
        getLogger().info(" Tiered /rtp + /rtp near | /kit <kit> [nick] | OWNER-Only LP");
        getLogger().info("============================================================");
    }

    @Override
    public void onDisable() {
        if (this.moderation != null) this.moderation.shutdown();
        if (this.hud != null) this.hud.shutdown();
        if (this.bossEvents != null) this.bossEvents.shutdown();
        if (this.store != null) this.store.save();
    }

    public DataStore getStore() { return store; }
    public RankManager getRanks() { return ranks; }
    public LpManager getLp() { return lp; }
    public BossEventManager getBossEvents() { return bossEvents; }
    public SpawnEngine getSpawnEngine() { return spawnEngine; }
    public GuiManager getGuis() { return guis; }
    public HudManager getHud() { return hud; }
    public ModerationManager getModeration() { return moderation; }

    private boolean hasEconomyAdmin(CommandSender s) {
        if (!(s instanceof Player)) return true;
        Player p = (Player) s;
        String rid = ranks.getRank(p).id;
        return p.isOp() || "owner".equals(rid) || "techadmin".equals(rid) || "curator".equals(rid);
    }

    // ================= CLOUD PROMO SYNC WITH https://kinggrief.vercel.app =================
    private void startWebsitePromoSyncLoop() {
        new BukkitRunnable() {
            @Override
            public void run() {
                syncPromosFromWebsiteNow();
            }
        }.runTaskTimerAsynchronously(this, 60L, 900L); // every 45 seconds
    }

    public void syncPromosFromWebsiteNow() {
        String apiBase = getConfig().getString("site-api-url", "https://kinggrief.vercel.app");
        try {
            URL url = new URL(apiBase + "/api/promos/server");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);
            con.setRequestProperty("Accept", "text/plain");
            if (con.getResponseCode() != 200) return;

            BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));
            final List<String[]> rows = new ArrayList<String[]>();
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                // Format: CODE|MAX_USES|COINS|RELICS|CASE_ID|CASE_QTY|RANK_ID
                String[] parts = line.split("\\|", -1);
                if (parts.length >= 4) rows.add(parts);
            }
            br.close();
            if (!rows.isEmpty()) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        for (String[] r : rows) {
                            try {
                                String code = r[0];
                                int maxU = Integer.parseInt(r[1]);
                                double coins = Double.parseDouble(r[2]);
                                int relics = Integer.parseInt(r[3]);
                                String cid = r.length > 4 ? r[4] : "";
                                int cqty = r.length > 5 && !r[5].isEmpty() ? Integer.parseInt(r[5]) : (cid.isEmpty() ? 0 : 1);
                                String rid = r.length > 6 ? r[6] : "";
                                store.syncServerPromo(code, maxU, coins, relics, cid, cqty, rid);
                            } catch (Exception ignored) {}
                        }
                    }
                }.runTask(this);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        String c = cmd.getName().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<String>();
        if ("cases".equals(c)) {
            if (args.length == 1 && hasEconomyAdmin(sender)) return filterPrefix(Arrays.asList("give", "set", "take"), args[0]);
            if (args.length == 2 && hasEconomyAdmin(sender)) return onlineNames(args[1]);
            if (args.length == 3 && hasEconomyAdmin(sender)) return filterPrefix(Arrays.asList(ALL_CASE_IDS), args[2]);
            if (args.length == 4 && hasEconomyAdmin(sender)) return filterPrefix(Arrays.asList("1", "3", "5", "10", "25"), args[3]);
        } else if ("p".equals(c) || "money".equals(c)) {
            if (args.length == 1 && hasEconomyAdmin(sender)) return filterPrefix(Arrays.asList("give", "set", "take", "look"), args[0]);
            if (args.length == 2) return onlineNames(args[1]);
            if (args.length == 3 && hasEconomyAdmin(sender)) {
                return filterPrefix("p".equals(c) ? Arrays.asList("50", "100", "250", "500", "1000")
                        : Arrays.asList("10000", "50000", "100000", "500000", "1000000"), args[2]);
            }
        } else if ("shop".equals(c) && args.length == 1) {
            return filterPrefix(Arrays.asList("pvp", "food", "gear", "raid", "elytra", "blocks", "spawners", "ores"), args[0]);
        } else if ("bounty".equals(c) && args.length == 1) {
            return filterPrefix(Arrays.asList("add"), args[0]);
        } else if ("bounty".equals(c) && args.length == 2) {
            return onlineNames(args[1]);
        } else if ("rtp".equals(c) && args.length == 1) {
            return filterPrefix(Arrays.asList("close", "medium", "high", "near", "nether"), args[0]);
        } else if ("kit".equals(c) && args.length == 1) {
            return filterPrefix(Arrays.asList(
                    "start", "newyear", "pegas", "cobra", "hero", "bunny", "titan", "rabbit",
                    "vampire", "tiger", "dracula", "god", "overlord", "imperator", "dragon",
                    "king", "bull", "sponsor", "media", "kp", "dhelper", "dmoder", "dstmoder",
                    "dglmoder", "dmladmin", "dadmin", "dkp", "opka", "downer", "curator", "owner"
            ), args[0]);
        } else if ("kit".equals(c) && args.length == 2) {
            return onlineNames(args[1]);
        }
        return out;
    }

    private List<String> onlineNames(String pref) {
        List<String> list = new ArrayList<String>();
        for (Player p : Bukkit.getOnlinePlayers()) list.add(p.getName());
        return filterPrefix(list, pref);
    }

    private List<String> filterPrefix(List<String> src, String pref) {
        String p = pref == null ? "" : pref.toLowerCase(Locale.ROOT);
        List<String> res = new ArrayList<String>();
        for (String s : src) {
            if (s.toLowerCase(Locale.ROOT).startsWith(p)) res.add(s);
        }
        return res;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String c = cmd.getName().toLowerCase(Locale.ROOT);

        // ================= CONSOLE + ADMIN COMMANDS: /kgadmin, /cases give, /p give, /money give, /promoadmin =================
        if ("kgadmin".equals(c)) {
            return handleKgAdmin(sender, args);
        }
        if ("rebuildspawn".equals(c)) {
            if (!hasEconomyAdmin(sender)) {
                sender.sendMessage(ColorUtil.c("&cДоступно только OWNER и Консоли!"));
                return true;
            }
            spawnEngine.initializeSpawnWorldAndBuild(true);
            sender.sendMessage(ColorUtil.c("&#00E676✔ Яркий красочный спавн v5.0 успешно перестроен в мире spawn_world!"));
            return true;
        }
        if ("promoadmin".equals(c)) {
            return handlePromoAdmin(sender, args);
        }
        if ("cases".equals(c) && args.length >= 1 && ("give".equalsIgnoreCase(args[0]) || "set".equalsIgnoreCase(args[0]) || "take".equalsIgnoreCase(args[0]))) {
            return handleCasesAdminCmd(sender, args);
        }
        if ("p".equals(c) && (args.length >= 1 || !(sender instanceof Player))) {
            return handleRelicsCommand(sender, args);
        }
        if ("money".equals(c) && (args.length >= 1 || !(sender instanceof Player))) {
            return handleMoneyCommand(sender, args);
        }
        if ("kit".equals(c) && !(sender instanceof Player)) {
            if (args.length < 2) {
                sender.sendMessage("Usage from console: /kit <kit> <nickname>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) target = Bukkit.getPlayer(args[1]);
            guis.tryClaimKitToTarget(sender, args[0], target);
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Use /cases give, /p give, /money give, /kgadmin or /lp from console.");
            return true;
        }
        Player p = (Player) sender;

        // ================= AUTH COMMANDS =================
        if ("register".equals(c)) {
            if (store.isAuthenticated(p) && store.isRegistered(p.getName())) {
                p.sendMessage(ColorUtil.c("&aВы уже авторизованы!"));
                return true;
            }
            if (args.length < 2 || !args[0].equals(args[1]) || args[0].length() < 4) {
                p.sendMessage(ColorUtil.c("&#FFD740Использование: &f/register <пароль> <повтор> &7(мин. 4 символа)"));
                return true;
            }
            store.registerPassword(p, args[0]);
            ColorUtil.sound(p, "entity.player.levelup", 1f, 1.2f);
            p.sendTitle(ColorUtil.c("&#00E676&l✔ ДОБРО ПОЖАЛОВАТЬ!"), ColorUtil.c("&fПриятной игры на KINGGRIEF v5.0!"), 5, 40, 10);
            p.sendMessage(ColorUtil.c("&#00E676✔ Регистрация успешна! Вам выдан Новогодний Кейс (/cases) и стартовый баланс!"));
            return true;
        }
        if ("login".equals(c)) {
            if (store.isAuthenticated(p)) {
                p.sendMessage(ColorUtil.c("&aВы уже вошли в аккаунт!"));
                return true;
            }
            if (args.length < 1) {
                p.sendMessage(ColorUtil.c("&#FFD740Использование: &f/login <пароль>"));
                return true;
            }
            if (!store.checkPassword(p.getName(), args[0])) {
                ColorUtil.sound(p, "entity.villager.no", 1f, 1f);
                p.sendMessage(ColorUtil.c("&#FF1744✖ Неверный пароль!"));
                return true;
            }
            store.setAuthenticated(p, true);
            ColorUtil.sound(p, "entity.player.levelup", 1f, 1.25f);
            p.sendTitle(ColorUtil.c("&#00E676&l✔ ВХОД ВЫПОЛНЕН"), ColorUtil.c("&fС возвращением, " + p.getName() + "!"), 5, 35, 10);
            return true;
        }
        if ("changepassword".equals(c)) {
            if (args.length < 2 || !store.checkPassword(p.getName(), args[0])) {
                p.sendMessage(ColorUtil.c("&eИспользование: &f/changepassword <старый> <новый>"));
                return true;
            }
            store.changePassword(p.getName(), args[1]);
            p.sendMessage(ColorUtil.c("&#00E676✔ Пароль успешно обновлён!"));
            return true;
        }

        if (!store.isAuthenticated(p)) {
            p.sendMessage(ColorUtil.c("&#FF1744🔐 Сначала войдите: /login или /register"));
            return true;
        }

        // ================= NAVIGATION & SEPARATE WORLDS (/rtp ONLY TO SURVIVAL WORLD!) =================
        if ("spawn".equals(c)) {
            store.setBack(p, p.getLocation());
            store.setParkourCp(p, null);
            p.teleport(spawnEngine.getSpawnArrival());
            ColorUtil.sound(p, "entity.enderman.teleport", 1f, 1.1f);
            p.sendMessage(ColorUtil.c("&#80D8FF❄ Вы в отдельном мире Яркого Зимнего Спавна! Вход в мир выживания: &#00E676/rtp"));
            return true;
        }
        if ("rtp".equals(c)) {
            if (args.length == 0 || "gui".equalsIgnoreCase(args[0]) || "menu".equalsIgnoreCase(args[0])) {
                guis.openRtpMenu(p);
            } else {
                spawnEngine.performRtp(p, args[0]);
            }
            return true;
        }
        if ("rtpnear".equals(c)) {
            spawnEngine.performRtp(p, "near");
            return true;
        }
        if ("warps".equals(c)) {
            guis.openWarpsMenu(p);
            return true;
        }
        if ("warp".equals(c)) {
            if (args.length < 1) {
                guis.openWarpsMenu(p);
                return true;
            }
            Location wl = spawnEngine.getWarpLocation(args[0]);
            if (wl == null) {
                p.sendMessage(ColorUtil.c("&eДоступные варпы: &fmine, farm, casino, pvp, kabinki, parkour, cases, enchant"));
                return true;
            }
            store.setBack(p, p.getLocation());
            p.teleport(wl);
            ColorUtil.sound(p, "entity.enderman.teleport", 1f, 1.15f);
            p.sendMessage(ColorUtil.c("&#80D8FF🧭 Вы телепортированы на &f/warp " + args[0].toLowerCase(Locale.ROOT) + "&#80D8FF!"));
            return true;
        }
        if ("mine".equals(c) || "farm".equals(c) || "pvp".equals(c)) {
            p.teleport(spawnEngine.getWarpLocation(c));
            ColorUtil.sound(p, "entity.enderman.teleport", 1f, 1.15f);
            return true;
        }
        if ("parkour".equals(c)) {
            if (args.length > 0 && "cp".equalsIgnoreCase(args[0])) {
                Location cp = store.getParkourCp(p);
                p.teleport(cp != null ? cp : spawnEngine.getWarpLocation("parkour"));
                p.sendMessage(ColorUtil.c("&#40C4FF🏃 Телепорт на чекпоинт паркура!"));
            } else {
                p.teleport(spawnEngine.getWarpLocation("parkour"));
            }
            return true;
        }

        // ================= MENUS, SHOPS, BAFOVAR, SKINS & BOUNTY =================
        if ("menu".equals(c)) { guis.openMainMenu(p); return true; }
        if ("donate".equals(c)) { guis.openDonateMenu(p); return true; }
        if ("shop".equals(c)) {
            if (args.length >= 1) guis.openCoinShopCat(p, args[0]);
            else guis.openCoinShopHub(p);
            return true;
        }
        if ("rshop".equals(c)) { guis.openRelicShop(p); return true; }
        if ("skins".equals(c)) { guis.openTrapSkinsMenu(p); return true; }
        if ("bafovar".equals(c)) {
            if (args.length >= 1 && "brew".equalsIgnoreCase(args[0])) guis.openBafovarBrewerMenu(p);
            else guis.openBafovarMenu(p);
            return true;
        }
        if ("bounty".equals(c)) {
            if (args.length >= 3 && "add".equalsIgnoreCase(args[0])) {
                String target = args[1];
                double amt;
                try { amt = Double.parseDouble(args[2]); } catch (Exception ex) { amt = -1; }
                if (amt < 5000 || target.equalsIgnoreCase(p.getName())) {
                    p.sendMessage(ColorUtil.c("&eИспользование: &f/bounty add <ник> <сумма от 5000$>"));
                    return true;
                }
                if (!store.takeCoins(p.getName(), amt)) {
                    p.sendMessage(ColorUtil.c("&cНедостаточно монет!"));
                    return true;
                }
                store.addBounty(target, amt);
                Bukkit.broadcastMessage(ColorUtil.c("&#FF1744&l🎯 [ОХОТА ЗА ГОЛОВАМИ] &f" + p.getName()
                        + " &7назначил &a+" + ColorUtil.fmtMoney(amt) + "$ &7за голову игрока &c" + target
                        + " &8(Всего: &a" + ColorUtil.fmtMoney(store.getBounty(target)) + "$&8)&7!"));
                return true;
            }
            guis.openBountyMenu(p);
            return true;
        }
        if ("free".equals(c)) { guis.openFreebieMenu(p); return true; }
        if ("trash".equals(c)) { guis.openTrashMenu(p); return true; }
        if ("enchants".equals(c)) { guis.openEnchantsMenu(p); return true; }
        if ("casino".equals(c)) { guis.openCasinoMenu(p); return true; }
        if ("buyer".equals(c)) { guis.openBuyerMenu(p); return true; }
        if ("cases".equals(c)) { guis.openCasesMenu(p); return true; }
        if ("p".equals(c)) {
            p.sendMessage(ColorUtil.c("&#00E5FF❖ Ваши Рилики: &b" + ColorUtil.fmtInt(store.getRelics(p)) + " ❖ &8(&7Пополнить: &fkinggrief.vercel.app&8)"));
            return true;
        }
        if ("money".equals(c) || "bal".equals(c)) {
            p.sendMessage(ColorUtil.c("&#FFD740💰 Баланс: &a" + ColorUtil.fmtMoney(store.getCoins(p.getName())) + "$ &8| &b"
                    + store.getRelics(p) + " Риликов ❖ &8| &b" + store.getSnowflakes(p.getName()) + " Снежинок ❄"));
            return true;
        }
        if ("kits".equals(c)) { guis.openKitsMenu(p, 0); return true; }
        if ("kit".equals(c)) {
            if (args.length < 1) {
                guis.openKitsMenu(p, 0);
            } else if (args.length == 1) {
                guis.tryClaimKitToTarget(p, args[0], p);
            } else {
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) target = Bukkit.getPlayer(args[1]);
                guis.tryClaimKitToTarget(p, args[0], target);
            }
            return true;
        }
        if ("jobs".equals(c)) { guis.openJobsMenu(p); return true; }
        if ("bp".equals(c)) { guis.openBpMenu(p); return true; }
        if ("quests".equals(c)) { guis.openDjamshutMenu(p); return true; }
        if ("titles".equals(c)) { guis.openTitlesMenu(p); return true; }
        if ("playtime".equals(c)) { guis.openPlaytimeMenu(p); return true; }
        if ("bm".equals(c)) { guis.openBlackMarket(p); return true; }
        if ("events".equals(c)) {
            if (args.length >= 2 && "start".equalsIgnoreCase(args[0])) {
                if (!bossEvents.canAdminSpawnEvents(p)) {
                    p.sendMessage(ColorUtil.c("&#FF1744✖ Запускать ивенты могут только Админы и выше (D.ADMIN+ / OWNER)!"));
                    return true;
                }
                bossEvents.spawnBossEvent(args[1], null);
                return true;
            }
            guis.openEventsMenu(p);
            return true;
        }
        if ("ah".equals(c)) {
            if (args.length >= 2 && "sell".equalsIgnoreCase(args[0])) {
                double price;
                try { price = Double.parseDouble(args[1]); } catch (Exception ex) { price = -1; }
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (price <= 0 || hand == null || hand.getType() == Material.AIR || "menu_star".equals(ItemFactory.getCustomId(hand))) {
                    p.sendMessage(ColorUtil.c("&cВозьмите предмет в руку и введите: &f/ah sell <цена>"));
                    return true;
                }
                store.addAuctionLot(p.getName(), hand, price);
                p.getInventory().setItemInMainHand(null);
                p.sendMessage(ColorUtil.c("&#00E676⚖ Предмет выставлен на Аукцион за &a" + ColorUtil.fmtMoney(price) + "$&7!"));
                return true;
            }
            guis.openAuctionMenu(p, 0);
            return true;
        }

        // ================= CASINO COINFLIP (/cf) & DUELS (/duel) =================
        if ("cf".equals(c)) {
            if (args.length < 1) { guis.openCasinoMenu(p); return true; }
            double bet;
            try { bet = Double.parseDouble(args[0]); } catch (Exception ex) { bet = -1; }
            if (bet < 1000) {
                p.sendMessage(ColorUtil.c("&eМинимальная ставка Монетки: &a1 000$ &7(/cf <сумма>)"));
                return true;
            }
            if (!store.takeCoins(p.getName(), bet)) {
                p.sendMessage(ColorUtil.c("&cНедостаточно монет!"));
                return true;
            }
            store.createCoinflip(p.getName(), bet);
            Bukkit.broadcastMessage(ColorUtil.c("&#FFD740🪙 [Монетка] &f" + p.getName() + " &7создал игру на &a"
                    + ColorUtil.fmtMoney(bet) + "$&7! Принять: &f/casino"));
            return true;
        }
        if ("duel".equals(c)) {
            if (args.length >= 1 && "accept".equalsIgnoreCase(args[0])) {
                DataStore.DuelRequest dr = store.popDuelRequest(p);
                if (dr == null) {
                    p.sendMessage(ColorUtil.c("&cУ вас нет активных вызовов на дуэль!"));
                    return true;
                }
                Player ch = Bukkit.getPlayer(dr.challenger);
                if (ch == null || !ch.isOnline()) {
                    p.sendMessage(ColorUtil.c("&cСоперник вышел из игры."));
                    return true;
                }
                World sw = VoidSpawnWorldGenerator.getOrCreateSpawnWorld();
                ch.teleport(new Location(sw, 70.5, 100.0, -42.5, 0f, 0f));
                p.teleport(new Location(sw, 71.5, 100.0, -40.5, 180f, 0f));
                Bukkit.broadcastMessage(ColorUtil.c("&#FF1744⚔ Дуэль началась: &f" + ch.getName() + " &cvs &f" + p.getName() + " &7в Кабинке #1!"));
                return true;
            }
            if (args.length < 1) {
                p.sendMessage(ColorUtil.c("&eИспользование: &f/duel <ник> &7или &f/duel accept"));
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null || target.equals(p)) {
                p.sendMessage(ColorUtil.c("&cИгрок не найден!"));
                return true;
            }
            store.putDuelRequest(p, target, 0.0);
            p.sendMessage(ColorUtil.c("&#00E676⚔ Вызов на дуэль отправлен игроку &f" + target.getName() + "&7!"));
            target.sendMessage(ColorUtil.c("&#FF1744⚔ Игрок &f" + p.getName() + " &#FF1744вызывает вас на дуэль в Кабинку! Введите: &#00E676/duel accept"));
            return true;
        }

        // ================= PAY, BALTOP, DAILY & WEBSITE+SERVER PROMO CODES =================
        if ("pay".equals(c)) {
            if (args.length < 2) { p.sendMessage(ColorUtil.c("&eИспользование: &f/pay <ник> <сумма>")); return true; }
            Player rec = Bukkit.getPlayer(args[0]);
            double amt;
            try { amt = Double.parseDouble(args[1]); } catch (Exception ex) { amt = -1; }
            if (rec == null || rec.equals(p) || amt < 10) {
                p.sendMessage(ColorUtil.c("&cУкажите игрока онлайн и сумму от 10$!"));
                return true;
            }
            if (!store.takeCoins(p.getName(), amt)) { p.sendMessage(ColorUtil.c("&cНедостаточно монет!")); return true; }
            store.addCoins(rec.getName(), amt);
            store.save();
            p.sendMessage(ColorUtil.c("&#00E676✔ Вы перевели &a" + ColorUtil.fmtMoney(amt) + "$ &7игроку &f" + rec.getName()));
            rec.sendMessage(ColorUtil.c("&#00E676💰 Игрок &f" + p.getName() + " &7перевёл вам &a+" + ColorUtil.fmtMoney(amt) + "$&7!"));
            return true;
        }
        if ("baltop".equals(c)) {
            p.sendMessage(ColorUtil.c("&#FFD740&l🏆 ТОП БОГАЧЕЙ СЕРВЕРА:"));
            List<Map.Entry<String, Double>> top = store.getBalTop(10);
            for (int i = 0; i < top.size(); i++) {
                p.sendMessage(ColorUtil.c(" &#FFD740#" + (i + 1) + " &f" + top.get(i).getKey() + " &8— &a" + ColorUtil.fmtMoney(top.get(i).getValue().doubleValue()) + "$"));
            }
            return true;
        }
        if ("daily".equals(c)) {
            long now = System.currentTimeMillis();
            long ready = store.getDailyReadyAt(p.getName());
            if (now < ready) {
                p.sendMessage(ColorUtil.c("&#FFD740🎅 Подарок уже получен! Следующий через &f" + ColorUtil.fmtTime((ready - now) / 1000L)));
                return true;
            }
            store.setDailyReadyAt(p.getName(), now + 86400_000L);
            store.addCoins(p.getName(), 20000);
            store.addRelics(p.getName(), 15);
            store.addSnowflakes(p.getName(), 5);
            store.save();
            ColorUtil.spawnFirework(p.getLocation());
            p.sendMessage(ColorUtil.c("&#80D8FF🎅 Дедушка Мороз подарил вам &a+20 000$&f, &b+15 Риликов ❖ &fи &b+5 Снежинок ❄&f!"));
            return true;
        }
        if ("promo".equals(c)) {
            if (args.length < 1) { guis.openFreebieMenu(p); return true; }
            String code = args[0].trim().toUpperCase(Locale.ROOT);
            if (store.hasUsedPromo(p.getName(), code)) {
                p.sendMessage(ColorUtil.c("&cВы уже активировали промокод " + code + "!"));
                return true;
            }

            // 1. Check Cloud/Website & Admin-Created Server Promo Codes FIRST!
            DataStore.ServerPromo sp = store.getServerPromo(code);
            if (sp != null) {
                if (sp.maxUses > 0 && sp.usedCount >= sp.maxUses) {
                    p.sendMessage(ColorUtil.c("&cЛимит активаций этого промокода исчерпан (" + sp.maxUses + "/" + sp.maxUses + ")!"));
                    return true;
                }
                List<String> granted = new ArrayList<String>();
                if (sp.coins > 0) {
                    store.addCoins(p.getName(), sp.coins);
                    granted.add("&a+" + ColorUtil.fmtMoney(sp.coins) + "$");
                }
                if (sp.relics > 0) {
                    store.addRelics(p.getName(), sp.relics);
                    granted.add("&b+" + sp.relics + " Риликов ❖");
                }
                if (sp.caseId != null && !sp.caseId.isEmpty() && sp.caseQty > 0) {
                    store.addCaseKeys(p.getName(), sp.caseId, sp.caseQty);
                    granted.add("&#FF4081+" + sp.caseQty + " Кейс [" + sp.caseId.toUpperCase(Locale.ROOT) + "]");
                }
                if (sp.rankId != null && !sp.rankId.isEmpty()) {
                    RankManager.RankDef targetRank = ranks.byId(sp.rankId);
                    if (targetRank.weight > ranks.getRank(p).weight) {
                        ranks.setRank(p.getName(), targetRank.id, true);
                    }
                    granted.add("&#FFD740Привилегия " + targetRank.id.toUpperCase(Locale.ROOT));
                }
                store.incrementServerPromoUsed(code);
                store.markUsedPromo(p.getName(), code);
                store.save();
                hud.updatePlayerHud(p);
                ColorUtil.sound(p, "ui.toast.challenge_complete", 1f, 1.2f);
                p.sendMessage(ColorUtil.c("&#00E676🎁 Промокод с сайта &f" + code + " &#00E676активирован! Награда: " + String.join("&7, ", granted)));
                return true;
            }

            // 2. Built-in Festive & FT×RW Promo Codes
            if ("NEWYEAR2026".equals(code)) {
                store.addCoins(p.getName(), 25000); store.addRelics(p.getName(), 25); store.addCaseKeys(p.getName(), "summer", 1);
            } else if ("REALLYWORLD".equals(code)) {
                store.addCoins(p.getName(), 30000); store.addRelics(p.getName(), 30); store.addSnowflakes(p.getName(), 10);
            } else if ("KINGGRIEF".equals(code)) {
                store.addCoins(p.getName(), 20000); store.addRelics(p.getName(), 20); p.getInventory().addItem(ItemFactory.potionKiller(1));
            } else if ("FTRW".equals(code) || "FTRW2026".equals(code)) {
                store.addCoins(p.getName(), 25000); p.getInventory().addItem(ItemFactory.plast(4), ItemFactory.trapka(2));
            } else if ("SANTA".equals(code)) {
                store.addCoins(p.getName(), 15000); p.getInventory().addItem(ItemFactory.nyMandarin(8), ItemFactory.potionChampagne(1));
            } else if ("RILIK".equals(code)) {
                store.addRelics(p.getName(), 35);
            } else if ("FREE".equals(code)) {
                store.addCoins(p.getName(), 15000); store.addCaseKeys(p.getName(), "money", 1);
            } else {
                p.sendMessage(ColorUtil.c("&cНеизвестный промокод! Создайте на сайте &fkinggrief.vercel.app &cили посмотрите в &f/free"));
                return true;
            }
            store.markUsedPromo(p.getName(), code);
            store.save();
            hud.updatePlayerHud(p);
            ColorUtil.sound(p, "ui.toast.challenge_complete", 1f, 1.2f);
            p.sendMessage(ColorUtil.c("&#00E676🎁 Промокод &f" + code + " &#00E676активирован! Награда выдана!"));
            return true;
        }
        if ("clan".equals(c)) {
            handleClanCmd(p, args);
            return true;
        }
        if ("rg".equals(c)) {
            handleRgCmd(p, args);
            return true;
        }

        // ================= HOMES, TPA, BACK & PERKS =================
        if ("sethome".equals(c)) {
            if (spawnEngine.isInSpawnWorld(p.getLocation())) {
                p.sendMessage(ColorUtil.c("&cНельзя ставить дом на спавне! Перейдите в выживание: /rtp"));
                return true;
            }
            String hn = args.length > 0 ? args[0] : "home";
            Map<String, Location> homes = store.getHomes(p.getName());
            int max = ranks.getRank(p).maxHomes;
            if (!homes.containsKey(hn.toLowerCase(Locale.ROOT)) && homes.size() >= max) {
                p.sendMessage(ColorUtil.c("&cЛимит точек дома вашей привилегии: &f" + max));
                return true;
            }
            store.setHome(p.getName(), hn, p.getLocation());
            p.sendMessage(ColorUtil.c("&#00E676🏠 Точка дома &f" + hn + " &#00E676установлена!"));
            return true;
        }
        if ("home".equals(c)) {
            String hn = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "home";
            Location hl = store.getHomes(p.getName()).get(hn);
            if (hl == null) { p.sendMessage(ColorUtil.c("&cДом не найден! Список: /homes")); return true; }
            store.setBack(p, p.getLocation());
            p.teleport(hl);
            p.sendMessage(ColorUtil.c("&#00E676🏠 Телепорт домой (&f" + hn + "&#00E676)!"));
            return true;
        }
        if ("delhome".equals(c)) {
            String hn = args.length > 0 ? args[0] : "home";
            p.sendMessage(ColorUtil.c(store.delHome(p.getName(), hn) ? "&#00E676✔ Дом удалён." : "&cДом не найден."));
            return true;
        }
        if ("homes".equals(c)) {
            Map<String, Location> hs = store.getHomes(p.getName());
            p.sendMessage(ColorUtil.c("&#FFD740Ваши дома (" + hs.size() + "/" + ranks.getRank(p).maxHomes + "): &f" + hs.keySet()));
            return true;
        }
        if ("back".equals(c)) {
            Location b = store.getBack(p);
            if (b == null) { p.sendMessage(ColorUtil.c("&cНет сохранённой локации для возврата.")); return true; }
            p.teleport(b);
            p.sendMessage(ColorUtil.c("&#00E676↩ Возврат на предыдущее место!"));
            return true;
        }
        if ("tpa".equals(c) || "tpahere".equals(c)) {
            if (args.length < 1) { p.sendMessage(ColorUtil.c("&eИспользование: &f/" + c + " <ник>")); return true; }
            Player t = Bukkit.getPlayer(args[0]);
            if (t == null || t.equals(p)) { p.sendMessage(ColorUtil.c("&cИгрок не найден!")); return true; }
            boolean here = "tpahere".equals(c);
            store.sendTpa(p, t, here);
            p.sendMessage(ColorUtil.c("&#00E676✔ Запрос отправлен игроку &f" + t.getName()));
            t.sendMessage(ColorUtil.c("&#FFD740⚡ Игрок &f" + p.getName() + (here ? " &7просит вас телепортироваться к нему!" : " &7хочет телепортироваться к вам!") + " &aВведите: &f/tpaccept"));
            return true;
        }
        if ("tpaccept".equals(c)) {
            boolean[] isHere = new boolean[1];
            UUID sid = store.popTpaSender(p, isHere);
            Player s = sid != null ? Bukkit.getPlayer(sid) : null;
            if (s == null) { p.sendMessage(ColorUtil.c("&cНет активных запросов телепортации!")); return true; }
            if (isHere[0]) p.teleport(s.getLocation());
            else s.teleport(p.getLocation());
            p.sendMessage(ColorUtil.c("&#00E676✔ Запрос принят!"));
            s.sendMessage(ColorUtil.c("&#00E676✔ Телепортация выполнена!"));
            return true;
        }
        if ("sb".equals(c)) {
            boolean en = store.toggleSb(p);
            hud.updatePlayerHud(p);
            p.sendMessage(ColorUtil.c(en ? "&aСкорборд включён!" : "&eСкорборд скрыт."));
            return true;
        }
        if ("near".equals(c)) {
            if (ranks.getRank(p).weight < 35) {
                p.sendMessage(ColorUtil.c("&cКоманда /near доступна с привилегии BUNNY (59₽)!"));
                return true;
            }
            StringBuilder sb = new StringBuilder();
            for (Entity en : p.getNearbyEntities(150, 120, 150)) {
                if (en instanceof Player && !en.equals(p) && !store.isVanished((Player) en)) {
                    int dist = (int) p.getLocation().distance(en.getLocation());
                    if (sb.length() > 0) sb.append("&7, ");
                    sb.append("&f").append(en.getName()).append(" &e(").append(dist).append("м)");
                }
            }
            p.sendMessage(ColorUtil.c("&#80D8FF📡 Игроки рядом (150м): " + (sb.length() == 0 ? "&7никого нет" : sb.toString())));
            return true;
        }
        if ("hat".equals(c)) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() == Material.AIR) {
                p.sendMessage(ColorUtil.c("&cВозьмите предмет в руку!"));
                return true;
            }
            ItemStack oldHelm = p.getInventory().getHelmet();
            p.getInventory().setHelmet(hand);
            p.getInventory().setItemInMainHand(oldHelm);
            p.sendMessage(ColorUtil.c("&#00E676🎩 Предмет надет на голову!"));
            return true;
        }
        if ("ext".equals(c)) {
            p.setFireTicks(0);
            p.sendMessage(ColorUtil.c("&#80D8FF💧 Вы потушили себя!"));
            return true;
        }

        // ================= PRIVILEGE & STAFF COMMANDS =================
        RankManager.RankDef myRank = ranks.getRank(p);
        if ("wb".equals(c) && (myRank.weight >= 35 || p.hasPermission("kinggrief.workbench"))) {
            p.openWorkbench(null, true); return true;
        }
        if ("ec".equals(c) && (myRank.weight >= 65 || p.hasPermission("kinggrief.enderchest"))) {
            p.openInventory(p.getEnderChest()); return true;
        }
        if ("feed".equals(c) && (myRank.weight >= 45 || p.hasPermission("kinggrief.feed"))) {
            p.setFoodLevel(20); p.setSaturation(20f); p.sendMessage(ColorUtil.c("&#00E676🍖 Голод утолён!")); return true;
        }
        if ("heal".equals(c) && (myRank.weight >= 65 || p.hasPermission("kinggrief.heal"))) {
            p.setHealth(p.getMaxHealth()); p.setFoodLevel(20); p.sendMessage(ColorUtil.c("&#00E676💖 Здоровье восстановлено!")); return true;
        }
        if ("repair".equals(c) && (myRank.weight >= 80 || p.hasPermission("kinggrief.repair"))) {
            guis.repairAllItems(p); p.sendMessage(ColorUtil.c("&#00E676🔧 Все предметы починены!")); return true;
        }
        if ("fly".equals(c) && (myRank.weight >= 90 || p.hasPermission("kinggrief.fly"))) {
            p.setAllowFlight(!p.getAllowFlight());
            p.sendMessage(ColorUtil.c(p.getAllowFlight() ? "&#00E676🕊 Режим полёта включён!" : "&eРежим полёта выключен."));
            return true;
        }
        if ("gm".equals(c) && (myRank.weight >= 178 || p.hasPermission("kinggrief.gm"))) {
            String m = args.length > 0 ? args[0] : "1";
            GameMode gm = "0".equals(m) ? GameMode.SURVIVAL : "3".equals(m) ? GameMode.SPECTATOR : GameMode.CREATIVE;
            if (gm == GameMode.CREATIVE && spawnEngine.isInSpawnWorld(p.getLocation()) && !"owner".equals(myRank.id) && !"techadmin".equals(myRank.id)) {
                p.sendMessage(ColorUtil.c("&eВнимание: строить/ломать на спавне может только OWNER!"));
            }
            p.setGameMode(gm);
            p.sendMessage(ColorUtil.c("&#00E676⚡ Режим игры: &f" + gm.name()));
            return true;
        }
        if ("vanish".equals(c) && (myRank.tier >= 4 || p.hasPermission("kinggrief.vanish"))) {
            boolean v = store.toggleVanish(p);
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.equals(p)) continue;
                if (v) other.hidePlayer(this, p); else other.showPlayer(this, p);
            }
            p.sendMessage(ColorUtil.c(v ? "&#00E676👻 Ваниш включён!" : "&eВаниш выключен."));
            return true;
        }
        if (("mute".equals(c) || "unmute".equals(c) || "kick".equals(c) || "ban".equals(c) || "unban".equals(c) || "bc".equals(c))
                && (myRank.tier >= 4 || p.isOp())) {
            handleModeration(p, c, args);
            return true;
        }
        p.sendMessage(ColorUtil.c("&#FF1744✖ У вашей привилегии нет доступа к этой команде! Повысить ранг: &#FFD740/donate"));
        return true;
    }

    // ================= /cases give | /p give (Рилики ❖) | /money give (Монеты $) =================
    private boolean handleCasesAdminCmd(CommandSender s, String[] args) {
        if (!hasEconomyAdmin(s)) {
            s.sendMessage(ColorUtil.c("&cНедостаточно прав! Команда доступна OWNER / Администрации / Консоли."));
            return true;
        }
        if (args.length < 3) {
            s.sendMessage(ColorUtil.c("&#FFD740Использование: &f/cases give <ник> <кейс> [кол-во]"));
            s.sendMessage(ColorUtil.c("&7Кейсы: &fdonate, sdonate, legendary, reallydev, riliks, allorno, ditems, kits, money, djamshut, summer"));
            return true;
        }
        String mode = args[0].toLowerCase(Locale.ROOT);
        String target = args[1];
        String caseId = args[2].toLowerCase(Locale.ROOT);
        int qty = 1;
        if (args.length >= 4) {
            try { qty = Math.max(0, Integer.parseInt(args[3])); } catch (Exception ex) { qty = 1; }
        }
        if ("set".equals(mode)) {
            store.setCaseKeys(target, caseId, qty);
        } else if ("take".equals(mode)) {
            store.addCaseKeys(target, caseId, -qty);
            store.save();
        } else {
            store.addCaseKeys(target, caseId, qty);
            store.save();
        }
        int totalNow = store.getCaseKeys(target, caseId);
        s.sendMessage(ColorUtil.c("&#00E676✔ Кейсы обновлены для &f" + target + "&7: кейс &#FFD740" + caseId + " &7→ сейчас &a" + totalNow + " шт."));
        Player tp = Bukkit.getPlayerExact(target);
        if (tp != null && "give".equals(mode)) {
            ColorUtil.sound(tp, "entity.player.levelup", 1f, 1.25f);
            tp.sendMessage(ColorUtil.c("&#FF4081🎁 Вам выдано &f+" + qty + " шт. &#FF4081кейса [&e" + caseId.toUpperCase(Locale.ROOT) + "&#FF4081]! Открыть: &f/cases"));
        }
        return true;
    }

    private boolean handleRelicsCommand(CommandSender s, String[] args) {
        if (args.length == 0 && s instanceof Player) {
            Player p = (Player) s;
            p.sendMessage(ColorUtil.c("&#00E5FF❖ Ваши Рилики: &b" + ColorUtil.fmtInt(store.getRelics(p)) + " ❖"));
            return true;
        }
        if (args.length >= 1 && ("look".equalsIgnoreCase(args[0]) || "me".equalsIgnoreCase(args[0]))) {
            String target = args.length >= 2 ? args[1] : s.getName();
            s.sendMessage(ColorUtil.c("&#00E5FF❖ Рилики игрока &f" + target + "&7: &b" + store.getRelics(target) + " ❖"));
            return true;
        }
        if (!hasEconomyAdmin(s)) {
            s.sendMessage(ColorUtil.c("&cНедостаточно прав для управления Риликами!"));
            return true;
        }
        if (args.length < 3) {
            s.sendMessage(ColorUtil.c("&#00E5FFИспользование: &f/p give <ник> <кол-во> &8| &f/p set <ник> <кол-во> &8| &f/p take <ник> <кол-во>"));
            return true;
        }
        String mode = args[0].toLowerCase(Locale.ROOT);
        String target = args[1];
        int amt;
        try { amt = Integer.parseInt(args[2]); } catch (Exception ex) { amt = -1; }
        if (amt < 0) {
            s.sendMessage(ColorUtil.c("&cУкажите корректное число Риликов!"));
            return true;
        }
        if ("set".equals(mode)) {
            store.setRelics(target, amt);
        } else if ("take".equals(mode)) {
            store.addRelics(target, -amt);
        } else {
            store.addRelics(target, amt);
        }
        store.save();
        Player tp = Bukkit.getPlayerExact(target);
        if (tp != null) {
            hud.updatePlayerHud(tp);
            if ("give".equals(mode)) {
                ColorUtil.sound(tp, "entity.player.levelup", 1f, 1.3f);
                tp.sendMessage(ColorUtil.c("&#00E5FF❖ Вам начислено &b+" + amt + " Риликов ❖&7! Потратить: &f/rshop"));
            }
        }
        s.sendMessage(ColorUtil.c("&#00E676✔ Баланс Риликов игрока &f" + target + " &#00E676обновлён: &b" + store.getRelics(target) + " ❖"));
        return true;
    }

    private boolean handleMoneyCommand(CommandSender s, String[] args) {
        if (args.length == 0 && s instanceof Player) {
            Player p = (Player) s;
            p.sendMessage(ColorUtil.c("&#FFD740💰 Ваши Монеты: &a" + ColorUtil.fmtMoney(store.getCoins(p.getName())) + "$"));
            return true;
        }
        if (!hasEconomyAdmin(s)) {
            s.sendMessage(ColorUtil.c("&cНедостаточно прав для управления Монетами!"));
            return true;
        }
        if (args.length < 3) {
            s.sendMessage(ColorUtil.c("&#FFD740Использование: &f/money give <ник> <сумма> &8| &f/money set <ник> <сумма> &8| &f/money take <ник> <сумма>"));
            return true;
        }
        String mode = args[0].toLowerCase(Locale.ROOT);
        String target = args[1];
        double amt;
        try { amt = Double.parseDouble(args[2]); } catch (Exception ex) { amt = -1; }
        if (amt < 0) {
            s.sendMessage(ColorUtil.c("&cУкажите корректную сумму Монет!"));
            return true;
        }
        if ("set".equals(mode)) {
            store.setCoins(target, amt);
        } else if ("take".equals(mode)) {
            store.addCoins(target, -amt);
        } else {
            store.addCoins(target, amt);
        }
        store.save();
        Player tp = Bukkit.getPlayerExact(target);
        if (tp != null) {
            hud.updatePlayerHud(tp);
            if ("give".equals(mode)) {
                ColorUtil.sound(tp, "entity.experience_orb.pickup", 1f, 1.3f);
                tp.sendMessage(ColorUtil.c("&#FFD740💰 Вам начислено &a+" + ColorUtil.fmtMoney(amt) + "$ Монет&7! Магазин: &f/shop"));
            }
        }
        s.sendMessage(ColorUtil.c("&#00E676✔ Баланс Монет игрока &f" + target + " &#00E676обновлён: &a" + ColorUtil.fmtMoney(store.getCoins(target)) + "$"));
        return true;
    }

    private boolean handlePromoAdmin(CommandSender s, String[] args) {
        if (!hasEconomyAdmin(s)) {
            s.sendMessage(ColorUtil.c("&cДоступно только OWNER / Администрации / Консоли!"));
            return true;
        }
        if (args.length < 1) {
            s.sendMessage(ColorUtil.c("&#FFD740/promoadmin create <КОД> <лимит> <монеты> <рилики> [кейс] [ранг]"));
            s.sendMessage(ColorUtil.c("&#FFD740/promoadmin delete <КОД> &8| &f/promoadmin sync &8| &f/promoadmin list"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("sync".equals(sub)) {
            new BukkitRunnable() {
                @Override
                public void run() { syncPromosFromWebsiteNow(); }
            }.runTaskAsynchronously(this);
            s.sendMessage(ColorUtil.c("&#00E676✔ Запущена синхронизация промокодов с сайта kinggrief.vercel.app!"));
            return true;
        }
        if ("list".equals(sub)) {
            List<DataStore.ServerPromo> all = store.getAllServerPromos();
            s.sendMessage(ColorUtil.c("&#FFD740Активные промокоды сервера/сайта (" + all.size() + "):"));
            for (DataStore.ServerPromo sp : all) {
                s.sendMessage(ColorUtil.c(" &f• &#00E676" + sp.code + " &7(" + sp.usedCount + "/" + sp.maxUses
                        + ") — &a" + (int) sp.coins + "$ &b" + sp.relics + "❖ &d" + sp.caseId + " &e" + sp.rankId));
            }
            return true;
        }
        if ("delete".equals(sub) && args.length >= 2) {
            store.deleteServerPromo(args[1]);
            s.sendMessage(ColorUtil.c("&#00E676✔ Промокод " + args[1].toUpperCase(Locale.ROOT) + " удалён."));
            return true;
        }
        if ("create".equals(sub) && args.length >= 5) {
            String code = args[1];
            int maxU = Integer.parseInt(args[2]);
            double coins = Double.parseDouble(args[3]);
            int relics = Integer.parseInt(args[4]);
            String caseId = args.length >= 6 ? args[5] : "";
            String rankId = args.length >= 7 ? args[6] : "";
            store.syncServerPromo(code, maxU, coins, relics, caseId, caseId.isEmpty() ? 0 : 1, rankId);
            s.sendMessage(ColorUtil.c("&#00E676✔ Промокод &f" + code.toUpperCase(Locale.ROOT) + " &#00E676создан и активен в &f/promo " + code.toUpperCase(Locale.ROOT) + "&#00E676!"));
            return true;
        }
        return true;
    }

    private void handleClanCmd(Player p, String[] args) {
        if (args.length == 0) { guis.openClanMenu(p); return; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        DataStore.Clan my = store.getClanOf(p.getName());
        if ("create".equals(sub) && args.length >= 2) {
            if (my != null) { p.sendMessage(ColorUtil.c("&cВы уже в клане!")); return; }
            if (!store.takeCoins(p.getName(), 25000)) { p.sendMessage(ColorUtil.c("&cСоздание клана стоит 25 000$!")); return; }
            DataStore.Clan created = store.createClan(p.getName(), args[1]);
            if (created == null) { store.addCoins(p.getName(), 25000); p.sendMessage(ColorUtil.c("&cТакой тег уже занят!")); return; }
            Bukkit.broadcastMessage(ColorUtil.c("&#B388FF🛡 Игрок &f" + p.getName() + " &#B388FFосновал клан [" + created.tag + "]!"));
        } else if ("invite".equals(sub) && args.length >= 2 && my != null && my.leader.equalsIgnoreCase(p.getName())) {
            store.inviteToClan(my.id, args[1]);
            Player t = Bukkit.getPlayer(args[1]);
            if (t != null) t.sendMessage(ColorUtil.c("&#B388FF🛡 Вас пригласили в клан [" + my.tag + "]! Введите: &f/clan join " + my.id));
            p.sendMessage(ColorUtil.c("&aПриглашение отправлено!"));
        } else if ("join".equals(sub) && args.length >= 2 && my == null) {
            DataStore.Clan c = store.getClanById(args[1]);
            if (c != null && store.hasClanInvite(p.getName(), c.id)) {
                store.joinClan(p.getName(), c);
                p.sendMessage(ColorUtil.c("&#00E676🛡 Вы вступили в клан [" + c.tag + "]!"));
            }
        } else if ("sethome".equals(sub) && my != null && my.leader.equalsIgnoreCase(p.getName())) {
            if (spawnEngine.isInSpawnWorld(p.getLocation())) { p.sendMessage(ColorUtil.c("&cТолько в мире выживания (/rtp)!")); return; }
            my.home = p.getLocation(); store.save();
            p.sendMessage(ColorUtil.c("&#00E676🛡 База клана установлена!"));
        } else if ("home".equals(sub) && my != null && my.home != null) {
            p.teleport(my.home);
            p.sendMessage(ColorUtil.c("&#B388FF🛡 Телепорт на базу клана!"));
        } else if ("leave".equals(sub) && my != null) {
            if (my.leader.equalsIgnoreCase(p.getName())) store.disbandClan(my);
            else store.leaveClan(p.getName(), my);
            p.sendMessage(ColorUtil.c("&eВы покинули клан."));
        } else {
            guis.openClanMenu(p);
        }
    }

    private void handleRgCmd(Player p, String[] args) {
        DataStore.Claim cl = store.getClaimAt(p.getLocation());
        if (cl == null) {
            p.sendMessage(ColorUtil.c("&eВы стоите вне региона! Поставьте Рудный Приват в выживании (/rtp)."));
            return;
        }
        if (args.length >= 2 && "add".equalsIgnoreCase(args[0]) && cl.owner.equalsIgnoreCase(p.getName())) {
            cl.members.add(args[1].toLowerCase(Locale.ROOT));
            store.save();
            p.sendMessage(ColorUtil.c("&#00E676🛡 Игрок &f" + args[1] + " &#00E676добавлен в ваш приват!"));
            return;
        }
        if (args.length >= 2 && "remove".equalsIgnoreCase(args[0]) && cl.owner.equalsIgnoreCase(p.getName())) {
            cl.members.remove(args[1].toLowerCase(Locale.ROOT));
            store.save();
            p.sendMessage(ColorUtil.c("&eИгрок удалён из привата."));
            return;
        }
        p.sendMessage(ColorUtil.c("&#00E676🛡 Регион владельца: &f" + cl.owner + " &8| &7Участники: &f" + cl.members));
    }

    private void handleModeration(Player staff, String c, String[] args) {
        if ("bc".equals(c) && args.length >= 1) {
            StringBuilder sb = new StringBuilder();
            for (String a : args) sb.append(a).append(' ');
            Bukkit.broadcastMessage(ColorUtil.c("&#FF1744&l[ОБЪЯВЛЕНИЕ] &f" + sb.toString().trim()));
            return;
        }
        if (args.length < 1) { staff.sendMessage(ColorUtil.c("&eУкажите ник игрока!")); return; }
        String target = args[0];
        if ("unmute".equals(c)) { store.unmute(target); staff.sendMessage(ColorUtil.c("&aМут снят с " + target)); return; }
        if ("unban".equals(c)) { store.unban(target); staff.sendMessage(ColorUtil.c("&aБан снят с " + target)); return; }
        String reason = args.length >= 2 ? args[1] : "Нарушение правил";
        if ("mute".equals(c)) {
            store.mute(target, 1800_000L, reason);
            Bukkit.broadcastMessage(ColorUtil.c("&#FF5252🔇 &f" + staff.getName() + " &7выдал мут &f" + target + " &7на 30м (" + reason + ")"));
        } else if ("kick".equals(c)) {
            Player t = Bukkit.getPlayerExact(target);
            if (t != null) t.kickPlayer(ColorUtil.c("&cВы кикнуты: &f" + reason));
        } else if ("ban".equals(c)) {
            store.ban(target, 0L, reason);
            Player t = Bukkit.getPlayerExact(target);
            if (t != null) t.kickPlayer(ColorUtil.c("&#FF1744Вы забанены! Причина: &f" + reason));
            Bukkit.broadcastMessage(ColorUtil.c("&#FF1744🔨 &f" + staff.getName() + " &7заблокировал &f" + target + " &7(" + reason + ")"));
        }
    }

    private boolean handleKgAdmin(CommandSender s, String[] args) {
        if (!hasEconomyAdmin(s)) {
            s.sendMessage(ColorUtil.c("&cДоступно только OWNER и Консоли!"));
            return true;
        }
        if (args.length < 1) {
            s.sendMessage(ColorUtil.c("&e/kgadmin <givecase | giverank | giverelics | givemoney | syncpromo | rebuildspawn | startboss>"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("rebuildspawn".equals(sub)) {
            spawnEngine.initializeSpawnWorldAndBuild(true);
            s.sendMessage(ColorUtil.c("&#00E676✔ Яркий Спавн v5.0 в мире spawn_world перестроен!"));
            return true;
        }
        if ("startboss".equals(sub)) {
            String bid = args.length >= 2 ? args[1] : "grinch";
            bossEvents.spawnBossEvent(bid, null);
            s.sendMessage(ColorUtil.c("&#00E676✔ Босс-Ивент запущен!"));
            return true;
        }
        if ("givecase".equals(sub) && args.length >= 4) {
            int amt = Integer.parseInt(args[3]);
            store.addCaseKeys(args[1], args[2], amt);
            store.save();
            s.sendMessage(ColorUtil.c("&#00E676✔ Выдано " + amt + " кейсов [" + args[2] + "] игроку " + args[1]));
            return true;
        }
        if ("giverank".equals(sub) && args.length >= 3) {
            ranks.setRank(args[1], args[2], true);
            s.sendMessage(ColorUtil.c("&#00E676✔ Выдана привилегия " + args[2] + " игроку " + args[1]));
            return true;
        }
        if ("giverelics".equals(sub) && args.length >= 3) {
            store.addRelics(args[1], Integer.parseInt(args[2]));
            store.save();
            s.sendMessage(ColorUtil.c("&#00E676✔ Начислены Рилики игроку " + args[1]));
            return true;
        }
        if ("givemoney".equals(sub) && args.length >= 3) {
            store.addCoins(args[1], Double.parseDouble(args[2]));
            store.save();
            s.sendMessage(ColorUtil.c("&#00E676✔ Начислены Монеты игроку " + args[1]));
            return true;
        }
        if ("syncpromo".equals(sub) && args.length >= 5) {
            String code = args[1];
            int maxU = Integer.parseInt(args[2]);
            double coins = Double.parseDouble(args[3]);
            int relics = Integer.parseInt(args[4]);
            String caseId = args.length >= 6 ? args[5] : "";
            int caseQty = args.length >= 7 ? Integer.parseInt(args[6]) : (caseId.isEmpty() ? 0 : 1);
            String rankId = args.length >= 8 ? args[7] : "";
            store.syncServerPromo(code, maxU, coins, relics, caseId, caseQty, rankId);
            s.sendMessage(ColorUtil.c("&#00E676✔ Серверный промокод " + code + " синхронизирован!"));
            return true;
        }
        return true;
    }
}
