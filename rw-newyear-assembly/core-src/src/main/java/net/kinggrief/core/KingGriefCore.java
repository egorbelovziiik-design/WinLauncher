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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class KingGriefCore extends JavaPlugin {

    private DataStore store;
    private RankManager ranks;
    private LpManager lp;
    private BossEventManager bossEvents;
    private SpawnEngine spawnEngine;
    private GuiManager guis;
    private HudManager hud;

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
        // Silence command block spam across all loaded worlds
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

        // Register /lp, /luckperms, /lpeditor with TabCompleter
        for (String lpCmd : new String[]{"lp", "luckperms", "lpeditor"}) {
            PluginCommand pc = getCommand(lpCmd);
            if (pc != null) {
                pc.setExecutor(this.lp);
                pc.setTabCompleter(this.lp);
            }
        }

        Bukkit.getPluginManager().registerEvents(new GameplayListener(this), this);

        // Build Void World 'spawn_world' + 181x96x181 Spawn + 11 Shulker Cases + 22 RW NPCs + 6 Kabinki
        this.spawnEngine.initializeSpawnWorldAndBuild(false);
        this.bossEvents.start();
        this.hud.start();

        for (Player on : Bukkit.getOnlinePlayers()) {
            this.store.refreshPermissions(on, this.ranks);
            this.bossEvents.syncBarsForJoin(on);
            this.hud.updatePlayerHud(on);
        }

        getLogger().info("============================================================");
        getLogger().info(" KINGGRIEF CORE v4.0.0 — NEW YEAR REALLYWORLD EDITION READY ");
        getLogger().info(" Dedicated Void Spawn: 'spawn_world' (181x96x181)           ");
        getLogger().info(" 22 Working RW NPCs (incl. Bafovar) + 11 Shulker Cases      ");
        getLogger().info(" 5 Stacked Boss Events + Full /lp & Web LP Editor Ready!    ");
        getLogger().info("============================================================");
    }

    @Override
    public void onDisable() {
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

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String c = cmd.getName().toLowerCase(Locale.ROOT);

        // Console / Bridge / Admin command (/kgadmin)
        if ("kgadmin".equals(c)) {
            return handleKgAdmin(sender, args);
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Command is for players only (use /kgadmin or /lp from console).");
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
            p.sendTitle(ColorUtil.c("&#00E676&l✔ ДОБРО ПОЖАЛОВАТЬ!"), ColorUtil.c("&fПриятной игры на ReallyWorld!"), 5, 40, 10);
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

        // ================= NAVIGATION & SEPARATE WORLDS =================
        if ("spawn".equals(c)) {
            store.setBack(p, p.getLocation());
            store.setParkourCp(p, null);
            p.teleport(spawnEngine.getSpawnArrival());
            ColorUtil.sound(p, "entity.enderman.teleport", 1f, 1.1f);
            p.sendMessage(ColorUtil.c("&#80D8FF❄ Вы телепортированы в мир Новогоднего Спавна!"));
            return true;
        }
        if ("rtp".equals(c)) {
            if (args.length > 0 && "gui".equalsIgnoreCase(args[0])) {
                guis.openRtpMenu(p);
            } else {
                String mode = args.length > 0 ? args[0] : "normal";
                spawnEngine.performRtp(p, mode);
            }
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

        // ================= MENUS & REALLYWORLD SYSTEMS =================
        if ("menu".equals(c)) { guis.openMainMenu(p); return true; }
        if ("donate".equals(c)) { guis.openDonateMenu(p); return true; }
        if ("shop".equals(c)) { guis.openRelicShop(p); return true; }
        if ("bafovar".equals(c)) { guis.openBafovarMenu(p); return true; }
        if ("free".equals(c)) { guis.openFreebieMenu(p); return true; }
        if ("trash".equals(c)) { guis.openTrashMenu(p); return true; }
        if ("enchants".equals(c)) { guis.openEnchantsMenu(p); return true; }
        if ("casino".equals(c)) { guis.openCasinoMenu(p); return true; }
        if ("buyer".equals(c)) { guis.openBuyerMenu(p); return true; }
        if ("cases".equals(c)) { guis.openCasesMenu(p); return true; }
        if ("kits".equals(c)) { guis.openKitsMenu(p, 0); return true; }
        if ("kit".equals(c)) {
            if (args.length < 1) guis.openKitsMenu(p, 0);
            else guis.tryClaimKit(p, args[0]);
            return true;
        }
        if ("jobs".equals(c)) { guis.openJobsMenu(p); return true; }
        if ("bp".equals(c)) { guis.openBpMenu(p); return true; }
        if ("quests".equals(c)) { guis.openDjamshutMenu(p); return true; }
        if ("titles".equals(c)) { guis.openTitlesMenu(p); return true; }
        if ("playtime".equals(c)) { guis.openPlaytimeMenu(p); return true; }
        if ("bm".equals(c)) { guis.openBlackMarket(p); return true; }
        if ("events".equals(c)) {
            if (args.length >= 2 && "start".equalsIgnoreCase(args[0]) && ("owner".equals(ranks.getRank(p).id) || p.isOp())) {
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
                ch.teleport(new Location(sw, 50.5, 100.0, -26.5, 0f, 0f));
                p.teleport(new Location(sw, 51.5, 100.0, -24.5, 180f, 0f));
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

        // ================= ECONOMY, PROMO, DAILY, CLAN, CLAIMS =================
        if ("bal".equals(c)) {
            p.sendMessage(ColorUtil.c("&#FFD740💰 Баланс: &a" + ColorUtil.fmtMoney(store.getCoins(p.getName())) + "$ &8| &b"
                    + store.getRelics(p) + " Риликов ❖ &8| &b" + store.getSnowflakes(p.getName()) + " Снежинок ❄"));
            return true;
        }
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
            String code = args[0].toUpperCase(Locale.ROOT);
            if (store.hasUsedPromo(p.getName(), code)) {
                p.sendMessage(ColorUtil.c("&cВы уже активировали этот промокод!"));
                return true;
            }
            if ("NEWYEAR2026".equals(code)) {
                store.addCoins(p.getName(), 25000); store.addRelics(p.getName(), 25); store.addCaseKeys(p.getName(), "summer", 1);
            } else if ("REALLYWORLD".equals(code)) {
                store.addCoins(p.getName(), 30000); store.addRelics(p.getName(), 30); store.addSnowflakes(p.getName(), 10);
            } else if ("KINGGRIEF".equals(code)) {
                store.addCoins(p.getName(), 20000); store.addRelics(p.getName(), 20); p.getInventory().addItem(ItemFactory.potionKiller(1));
            } else if ("SANTA".equals(code)) {
                store.addCoins(p.getName(), 15000); p.getInventory().addItem(ItemFactory.nyMandarin(8), ItemFactory.potionChampagne(1));
            } else if ("RILIK".equals(code)) {
                store.addRelics(p.getName(), 35);
            } else if ("FREE".equals(code)) {
                store.addCoins(p.getName(), 15000); store.addCaseKeys(p.getName(), "money", 1);
            } else {
                p.sendMessage(ColorUtil.c("&cНеизвестный промокод! Все промокоды доступны в меню &f/free"));
                return true;
            }
            store.markUsedPromo(p.getName(), code);
            store.save();
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

        // ================= HOMES, TPA, BACK & REALLYWORLD PERKS =================
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
        if ((s instanceof Player) && !((Player) s).isOp() && !"owner".equals(ranks.getRank((Player) s).id)) {
            s.sendMessage(ColorUtil.c("&cДоступно только OWNER и Консоли!"));
            return true;
        }
        if (args.length < 1) {
            s.sendMessage(ColorUtil.c("&e/kgadmin <givecase | giverank | giverelics | givemoney | rebuildspawn | startboss>"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("rebuildspawn".equals(sub)) {
            spawnEngine.initializeSpawnWorldAndBuild(true);
            s.sendMessage(ColorUtil.c("&#00E676✔ Спавн v4.0 в мире spawn_world перестроен!"));
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
        return true;
    }
}
