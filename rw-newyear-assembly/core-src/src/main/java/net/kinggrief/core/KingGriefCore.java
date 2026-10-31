package net.kinggrief.core;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.WeatherType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;

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
    private CrownEventManager crownEvents;
    private SafeTradeManager safeTrades;
    private BuyOrderManager buyOrders;
    private SpawnEngine spawnEngine;
    private GuiManager guis;
    private HudManager hud;
    private ModerationManager moderation;
    private GameplayListener gameplayListener;
    private AntiLagDdosManager antiLagDdos;
    private OwnerGiveawayManager ownerGiveaway;
    private CuratorManager curatorManager;
    private WinterItemsManager winterItems;
    private RtpManager rtpManager;
    private ExpeditionManager expeditions;

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        if (spawnEngine != null && spawnEngine.isSpawnActive()
                && VoidSpawnWorldGenerator.getSpawnWorldName().equalsIgnoreCase(worldName)) {
            return new VoidSpawnWorldGenerator();
        }
        return null;
    }

    @Override
    public void onEnable() {
        CoreInstallDiagnostics.report(this, getFile());
        saveDefaultConfig();
        for (World w : Bukkit.getWorlds()) {
            VoidSpawnWorldGenerator.silenceCommandSpam(w);
        }

        this.store = new DataStore(this);
        this.ranks = new RankManager(this.store);
        this.lp = new LpManager(this);
        this.bossEvents = new BossEventManager(this);
        this.spawnEngine = new SpawnEngine(this);
        this.rtpManager = new RtpManager(this);
        Bukkit.getPluginManager().registerEvents(this.rtpManager, this);
        this.guis = new GuiManager(this);
        this.hud = new HudManager(this);
        this.moderation = new ModerationManager(this);
        this.crownEvents = new CrownEventManager(this);
        Bukkit.getPluginManager().registerEvents(this.crownEvents, this);
        this.safeTrades = new SafeTradeManager(this);
        Bukkit.getPluginManager().registerEvents(this.safeTrades, this);
        this.safeTrades.start();
        this.buyOrders = new BuyOrderManager(this);
        Bukkit.getPluginManager().registerEvents(this.buyOrders, this);
        this.buyOrders.start();
        this.safeTrades.recoverOnlinePlayers();

        for (String lpCmd : new String[]{"lp", "luckperms", "lpeditor"}) {
            PluginCommand pc = getCommand(lpCmd);
            if (pc != null) {
                pc.setExecutor(this.lp);
                pc.setTabCompleter(this.lp);
            }
        }
        for (String modCmd : new String[]{
                "check", "sw", "spek", "mute", "unmute", "muteip", "unmuteip",
                "ban", "unban", "banip", "unbanip", "kick", "dupeip",
                "warn", "unwarn", "warns", "history", "clearchat", "slowchat", "staff"
        }) {
            PluginCommand pc = getCommand(modCmd);
            if (pc != null) {
                pc.setExecutor(this.moderation);
                pc.setTabCompleter(this.moderation);
            }
        }
        for (String tcCmd : new String[]{"cases", "p", "money", "shop", "bounty", "rtp", "kit", "warp", "gm", "tphere", "tp", "invsee", "ecsee", "freeze", "whois", "clan", "free", "events", "crown", "trade", "buyorder"}) {
            PluginCommand pc = getCommand(tcCmd);
            if (pc != null) {
                pc.setTabCompleter(this);
            }
        }

        this.gameplayListener = new GameplayListener(this);
        Bukkit.getPluginManager().registerEvents(this.gameplayListener, this);
        this.expeditions = new ExpeditionManager(this);
        Bukkit.getPluginManager().registerEvents(this.expeditions, this);
        this.expeditions.start();
        PluginCommand expeditionCommand = getCommand("expeditions");
        if (expeditionCommand != null) {
            expeditionCommand.setExecutor(this.expeditions);
            expeditionCommand.setTabCompleter(this.expeditions);
        }
        this.winterItems = new WinterItemsManager(this);
        Bukkit.getPluginManager().registerEvents(this.winterItems, this);
        PluginCommand artifactsCommand = getCommand("artifacts");
        if (artifactsCommand != null) {
            artifactsCommand.setExecutor(this.winterItems);
            artifactsCommand.setTabCompleter(this.winterItems);
        }

        this.antiLagDdos = new AntiLagDdosManager(this);
        Bukkit.getPluginManager().registerEvents(this.antiLagDdos, this);
        this.antiLagDdos.start();   // v6.3.1: фоновая чистка счётчиков (без неё карты текли вечно)
        PluginCommand ddosCmd = getCommand("antiddos");
        if (ddosCmd != null) ddosCmd.setExecutor(this.antiLagDdos);

        this.ownerGiveaway = new OwnerGiveawayManager(this);
        Bukkit.getPluginManager().registerEvents(this.ownerGiveaway, this);
        for (String ogCmd : new String[]{"razdacha", "txt", "tex", "thanksowner"}) {
            PluginCommand pc = getCommand(ogCmd);
            if (pc != null) {
                pc.setExecutor(this.ownerGiveaway);
                pc.setTabCompleter(this.ownerGiveaway);
            }
        }

        this.curatorManager = new CuratorManager(this);
        Bukkit.getPluginManager().registerEvents(this.curatorManager, this);
        PluginCommand curCmd = getCommand("curator");
        if (curCmd != null) {
            curCmd.setExecutor(this.curatorManager);
            curCmd.setTabCompleter(this.curatorManager);
        }

        if (PreparedSpawnInstaller.initialize(this, this.spawnEngine) == PreparedSpawnInstaller.Result.STOP_SERVER) {
            getLogger().severe("[SpawnInstall] Server is stopping after a failed spawn install to protect world data. Fix the error before restarting.");
            Bukkit.shutdown();
            return;
        }
        this.bossEvents.start();
        this.crownEvents.start();
        this.hud.start();
        this.moderation.start();
        startWebsitePromoSyncLoop();
        startAutoBroadcastLoops();

        new BukkitRunnable() {
            @Override
            public void run() {
                ranks.syncAllGroupsToLuckPermsIfPresent();
            }
        }.runTaskLater(this, 40L);

        for (Player on : Bukkit.getOnlinePlayers()) {
            this.store.refreshPermissions(on, this.ranks);
            this.bossEvents.syncBarsForJoin(on);
            this.ownerGiveaway.syncBarsForJoin(on);
            this.hud.updatePlayerHud(on);
        }

        getLogger().info("============================================================");
        getLogger().info(" KINGGRIEF CORE v7.2.2 — PREPARED SPAWN & SAFE WORLDS");
        getLogger().info(" Real Leaf Tree | 3-Floor Palace | 32-Island Sky Parkour    ");
        getLogger().info(" Anti-DDoS Lightning | New TNT & Events | /razdacha | /txt  ");
        getLogger().info("============================================================");
    }

    @Override
    public void onDisable() {
        if (this.gameplayListener != null) this.gameplayListener.rollbackPendingSpawnerBreaks();
        if (this.ownerGiveaway != null) {
            this.ownerGiveaway.kickAllForShutdownTechWorks();
            this.ownerGiveaway.shutdown();
        }
        if (this.buyOrders != null) this.buyOrders.shutdown();
        if (this.safeTrades != null) this.safeTrades.shutdown();
        if (this.crownEvents != null) this.crownEvents.shutdown();
        if (this.curatorManager != null) this.curatorManager.shutdown();
        if (this.antiLagDdos != null) this.antiLagDdos.shutdown();
        if (this.gameplayListener != null) this.gameplayListener.cleanupAllTempBlocks();
        if (this.moderation != null) this.moderation.shutdown();
        if (this.hud != null) this.hud.shutdown();
        if (this.bossEvents != null) this.bossEvents.shutdown();
        if (this.rtpManager != null) this.rtpManager.shutdown();
        if (this.expeditions != null) this.expeditions.shutdown();
        if (this.spawnEngine != null) this.spawnEngine.shutdown();
        if (this.store != null) this.store.save();
    }

    public DataStore getStore() { return store; }
    public RankManager getRanks() { return ranks; }
    public LpManager getLp() { return lp; }
    public BossEventManager getBossEvents() { return bossEvents; }
    public SafeTradeManager getSafeTrades() { return safeTrades; }
    public BuyOrderManager getBuyOrders() { return buyOrders; }
    public SpawnEngine getSpawnEngine() { return spawnEngine; }
    public GuiManager getGuis() { return guis; }
    public WinterItemsManager getWinterItems() { return winterItems; }
    RtpManager getRtpManager() { return rtpManager; }
    public ExpeditionManager getExpeditions() { return expeditions; }
    public HudManager getHud() { return hud; }
    public ModerationManager getModeration() { return moderation; }
    public AntiLagDdosManager getAntiLagDdos() { return antiLagDdos; }
    public OwnerGiveawayManager getOwnerGiveaway() { return ownerGiveaway; }
    public CuratorManager getCurator() { return curatorManager; }

    private boolean isStrictOwnerOrConsole(CommandSender s) {
        if (!(s instanceof Player)) return true;
        Player p = (Player) s;
        return ranks.isOwnerLevel(p);
    }

    private boolean hasEconomyAdmin(CommandSender s) {
        // STRICT OWNER + CONSOLE ONLY — no OP bypass, no non-owner backdoor!
        return isStrictOwnerOrConsole(s);
    }

    public static final String SITE_URL = "https://kinggrief.vercel.app";
    public static final String DISCORD_URL = "https://discord.gg/7Cugj4X3eU";
    private int siteAdRotationIdx = 0;
    private int mediaAdRotationIdx = 0;

    // ================= АВТО-ОБЪЯВЛЕНИЯ: САЙТ (15 МИН), НАБОР В КП (20 МИН), МЕДИА (34 МИН) =================
    private void startAutoBroadcastLoops() {
        // 1. Каждые 15 минут (18 000 тиков) — Реклама сайта с РАЗНЫМИ донатами и кейсами!
        final String[][] siteAds = new String[][]{
                {
                        "&#FFD740&l🛒 МАГАЗИН СЕРВЕРА KINGGRIEF &8• &#80D8FF" + SITE_URL,
                        "&fТоп привилегии: &#F5D0FEDRAGON &7(15₽) &8• &#FDE047GOD &7(149₽) &8• &#FB7185IMPERATOR &7(179₽) &8• &#FFD740KING &7(199₽) &8• &#38BDF8SPONSOR &7(215₽)!",
                        "&7Мгновенная авто-выдача за 3 секунды прямо в игру без перезахода!"
                },
                {
                        "&#FF1744&l🎁 ГОРЯЧИЕ КЕЙСЫ НА САЙТЕ &8• &#80D8FF" + SITE_URL,
                        "&fИспытай удачу: &#FF1744Донат-Кейс &7(29₽) &8• &#E040FBСупер-Донат &7(99₽) &8• &#A855F7Кейс ReallyDev &7(129₽)!",
                        "&7Шанс выбить привилегии вплоть до &#FF9100OPKA &7и &#FF1744D.OWNER &7с первой прокрутки!"
                },
                {
                        "&#FF9100&l👑 ЭЛИТНЫЕ ДКП ПРИВИЛЕГИИ &8• &#80D8FF" + SITE_URL,
                        "&fСтань властелином сервера: &#60A5FAД.МОДЕР &7(249₽) &8• &#F87171Д.АДМИН &7(799₽) &8• &#FF9100ОПКА &7(999₽) &8• &#FF1744Д.ОВНЕР &7(1499₽)!",
                        "&7Доступ к запуску Босс-Ивентов (/events), мощнейшим китам и донат-командам!"
                },
                {
                        "&#00E5FF&l❖ КЕЙСЫ РИЛИКОВ И 3D АРТЕФАКТОВ &8• &#80D8FF" + SITE_URL,
                        "&fЗабирай: &#00E5FFКейс Риликов &7(39₽ — до 2 500 ❖!) &8• &#FFD740Легендарный Кейс &7(59₽) &8• &#FF91003D Донат-Вещи &7(49₽)!",
                        "&7Трать Рилики в &#FFD740/picks &7и &#00E5FF/rshop &7на Кирку Крушителя 3×3, Бур 5×5 и Талисманы!"
                },
                {
                        "&#69F0AE&l💰 ВЫГОДНЫЕ КЕЙСЫ ДЛЯ СТАРТА &8• &#80D8FF" + SITE_URL,
                        "&fПрокачайся за минуту: &#69F0AEДенежный Кейс &7(19₽) &8• &#FFAB40Кейс Джамшута &7(25₽) &8• &#26C6DAКейс Китов &7(35₽)!",
                        "&7Внутри: миллионы монет, кирки Бур 3×3, Божье Прикосновение и полные шалкеры ресов!"
                },
                {
                        "&#FF5252&l🎲 КЕЙС «ВСЁ ИЛИ НИЧЕГО» И НОВОГОДНИЙ КЕЙС &8• &#80D8FF" + SITE_URL,
                        "&fРискни 50/50 в &#FF5252Кейсе Всё или Ничего &7(45₽) или открой &#80D8FFНовогодний Кейс &7(39₽)!",
                        "&7Все 11 кейсов ждут тебя на &#FFD740/warp cases &7и на сайте &#80D8FF" + SITE_URL
                }
        };

        new BukkitRunnable() {
            @Override
            public void run() {
                if (Bukkit.getOnlinePlayers().isEmpty()) return;
                String[] ad = siteAds[siteAdRotationIdx % siteAds.length];
                siteAdRotationIdx++;

                TextComponent btn = new TextComponent(ColorUtil.c(" &#00E676&l[ 🌐 ОТКРЫТЬ МАГАЗИН: kinggrief.vercel.app ]"));
                btn.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, SITE_URL));
                btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder(ColorUtil.c("&#FFD740Нажмите, чтобы перейти на сайт " + SITE_URL)).create()));

                for (Player on : Bukkit.getOnlinePlayers()) {
                    on.sendMessage(ColorUtil.c("&#FFD740&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                    on.sendMessage(ColorUtil.c(" " + ad[0]));
                    on.sendMessage(ColorUtil.c(" " + ad[1]));
                    on.sendMessage(ColorUtil.c(" " + ad[2]));
                    on.spigot().sendMessage(btn);
                    on.sendMessage(ColorUtil.c("&#FFD740&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                    ColorUtil.sound(on, "entity.experience_orb.pickup", 0.55f, 1.35f);
                }
            }
        }.runTaskTimer(this, 18000L, 18000L); // Строго каждые 15 минут!

        // 2. Каждые 20 минут (24 000 тиков) — Набор в КП с Discord https://discord.gg/7Cugj4X3eU!
        new BukkitRunnable() {
            @Override
            public void run() {
                if (Bukkit.getOnlinePlayers().isEmpty()) return;

                TextComponent kpLink = new TextComponent(ColorUtil.c(" &#5865F2&l[ 🛡 ВСТУПИТЬ В КП — " + DISCORD_URL + " ]"));
                kpLink.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, DISCORD_URL));
                kpLink.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder(ColorUtil.c("&#86EFACНажмите, чтобы перейти в Discord и подать заявку в КП!")).create()));

                for (Player on : Bukkit.getOnlinePlayers()) {
                    on.sendMessage(ColorUtil.c("&#4ADE80&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                    on.sendMessage(ColorUtil.c(" &#4ADE80&l🛡 НАБОР В СОСТАВ КП KINGGRIEF!"));
                    on.sendMessage(ColorUtil.c(" &fХочешь вступить в состав и бороться с читерами?"));
                    on.sendMessage(ColorUtil.c(" &fТогда тебе к нам в КП — присоединяйся: &#80D8FF&n" + DISCORD_URL));
                    on.spigot().sendMessage(kpLink);
                    on.sendMessage(ColorUtil.c("&#4ADE80&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                    ColorUtil.sound(on, "block.note_block.bell", 0.65f, 1.25f);
                }
            }
        }.runTaskTimer(this, 24000L, 24000L); // Строго каждые 20 минут!

        // 3. Каждые 34 минуты (40 800 тиков) — Набор на МЕДИА с Discord https://discord.gg/7Cugj4X3eU!
        new BukkitRunnable() {
            @Override
            public void run() {
                if (Bukkit.getOnlinePlayers().isEmpty()) return;
                boolean alt = (mediaAdRotationIdx++ % 2 == 1);

                TextComponent mediaLink = new TextComponent(ColorUtil.c(" &#EC4899&l[ 🎬 ПОДАТЬ ЗАЯВКУ НА МЕДИА — " + DISCORD_URL + " ]"));
                mediaLink.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, DISCORD_URL));
                mediaLink.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder(ColorUtil.c("&#EC4899Нажмите, чтобы подать заявку на TIKTOK / YT / TWITCH / MEDIA+!")).create()));

                for (Player on : Bukkit.getOnlinePlayers()) {
                    on.sendMessage(ColorUtil.c("&#EC4899&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                    on.sendMessage(ColorUtil.c(" &#EC4899&l🎬 СОТРУДНИЧЕСТВО И НАБОР НА МЕДИА KINGGRIEF!"));
                    if (!alt) {
                        on.sendMessage(ColorUtil.c(" &fСнимаешь ролики в &#FF2E93TikTok &f/ &#FF1744YouTube &fили стримишь на &#A855F7Twitch&f?"));
                        on.sendMessage(ColorUtil.c(" &fПолучи статус &#EC4899MEDIA / MEDIA+&f, бесплатные кейсы и зарплату в Риликах!"));
                    } else {
                        on.sendMessage(ColorUtil.c(" &fХочешь прокачать свой канал и получить префикс &#FF1744YT &f/ &#FF2E93TIKTOK &f/ &#EC4899MEDIA+&f?"));
                        on.sendMessage(ColorUtil.c(" &fВыдаём промокоды для подписчиков, Рилики и наборы для съёмок!"));
                    }
                    on.sendMessage(ColorUtil.c(" &fОставляй заявку в нашем Discord: &#80D8FF&n" + DISCORD_URL));
                    on.spigot().sendMessage(mediaLink);
                    on.sendMessage(ColorUtil.c("&#EC4899&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                    ColorUtil.sound(on, "block.amethyst_block.chime", 0.85f, 1.2f);
                }
            }
        }.runTaskTimer(this, 40800L, 40800L); // Строго каждые 34 минуты!
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
            return filterPrefix(Arrays.asList("close", "medium", "high", "near", "nether", "cancel"), args[0]);
        } else if ("kit".equals(c) && args.length == 1) {
            return filterPrefix(Arrays.asList(
                    "start", "newyear", "dragon", "pegas", "cobra", "dracula", "bunny", "rabbit",
                    "vampire", "tiger", "god", "imperator", "king", "sponsor", "media",
                    "dhelper", "dkp", "dmoder", "dstmoder", "dglmoder", "dmladmin", "dadmin",
                    "opka", "downer", "kp", "helper", "mlmoder", "mladmin", "admin", "curator", "techadmin", "owner"
            ), args[0]);
        } else if ("kit".equals(c) && args.length == 2) {
            return onlineNames(args[1]);
        } else if ("free".equals(c) && args.length == 1) {
            return filterPrefix(Arrays.asList("dragon"), args[0]);
        } else if ("buyorder".equals(c)) {
            if (args.length == 1) return filterPrefix(Arrays.asList("create", "list", "sell", "cancel", "claim"), args[0]);
        } else if ("trade".equals(c)) {
            if (args.length == 1) {
                List<String> choices = new ArrayList<String>(Arrays.asList("accept", "deny", "cancel"));
                choices.addAll(onlineNames(args[0]));
                return filterPrefix(choices, args[0]);
            }
            if (args.length == 2 && "accept".equalsIgnoreCase(args[0])) return onlineNames(args[1]);
        } else if ("crown".equals(c)) {
            if (args.length == 1) {
                List<String> choices = new ArrayList<String>(Arrays.asList("join", "leave", "status"));
                if (!(sender instanceof Player) || bossEvents.canAdminSpawnEvents((Player)sender)) {
                    choices.add("start"); choices.add("stop");
                }
                return filterPrefix(choices, args[0]);
            }
        } else if ("events".equals(c)) {
            if (args.length == 1) return filterPrefix(Arrays.asList("start"), args[0]);
            if (args.length == 2) return filterPrefix(Arrays.asList("airdrop", "airship", "meteor", "beacon", "citadel", "volcano", "crystal", "ruins", "camp", "grinch", "krush", "titan", "osiris", "andromeda"), args[1]);
        } else if ("clan".equals(c)) {
            if (args.length == 1) {
                return filterPrefix(Arrays.asList(
                        "help", "menu", "create", "disband", "invite", "accept", "deny",
                        "leave", "kick", "promote", "demote", "leader", "sethome", "home",
                        "delhome", "pvp", "money", "deposit", "withdraw", "storage",
                        "upgrade", "ally", "neutral", "color", "rename", "coords", "glow",
                        "chat", "info", "list", "top"
                ), args[0]);
            }
            if (args.length == 2) {
                String sub = args[0].toLowerCase(Locale.ROOT);
                if ("invite".equals(sub) || "kick".equals(sub) || "promote".equals(sub) || "demote".equals(sub) || "leader".equals(sub)) {
                    return onlineNames(args[1]);
                }
                if ("color".equals(sub)) {
                    return filterPrefix(Arrays.asList("purple", "red", "gold", "aqua", "green", "pink", "white"), args[1]);
                }
            }
        } else if ("warp".equals(c)) {
            if (args.length == 1) {
                List<String> wlist = new ArrayList<String>(Arrays.asList(
                        "set", "del", "list", "info",
                        "pvp", "kabinki", "train", "mine", "farm", "casino", "wheel", "bafs", "parkour", "cases", "top", "spawn"));
                if (store != null) wlist.addAll(store.getAllCustomWarps().keySet());
                return filterPrefix(wlist, args[0]);
            }
            if (args.length == 2 && ("del".equalsIgnoreCase(args[0]) || "delete".equalsIgnoreCase(args[0])
                    || "remove".equalsIgnoreCase(args[0]) || "info".equalsIgnoreCase(args[0]))) {
                List<String> wlist = new ArrayList<String>();
                if (store != null) wlist.addAll(store.getAllCustomWarps().keySet());
                return filterPrefix(wlist, args[1]);
            }
        } else if (("delwarp".equals(c) || "warpinfo".equals(c)) && args.length == 1) {
            List<String> wlist = new ArrayList<String>();
            if (store != null) wlist.addAll(store.getAllCustomWarps().keySet());
            return filterPrefix(wlist, args[0]);
        } else if ("speed".equals(c) && args.length == 1) {
            return filterPrefix(Arrays.asList("1", "2", "3", "4", "5", "7", "10"), args[0]);
        } else if ("ptime".equals(c) && args.length == 1) {
            return filterPrefix(Arrays.asList("day", "night", "reset"), args[0]);
        } else if ("friend".equals(c)) {
            if (args.length == 1) {
                List<String> opts = new ArrayList<String>(Arrays.asList("add", "accept", "deny", "remove", "del", "list", "pvp", "menu"));
                opts.addAll(onlineNames(""));
                return filterPrefix(opts, args[0]);
            }
            if (args.length == 2) return onlineNames(args[1]);
        } else if ("duel".equals(c)) {
            if (args.length == 1) {
                List<String> opts = new ArrayList<String>(Arrays.asList("accept"));
                opts.addAll(onlineNames(""));
                return filterPrefix(opts, args[0]);
            }
            if (args.length == 2) {
                return filterPrefix(Arrays.asList("0", "5000", "15000", "50000", "100000", "500000"), args[1]);
            }
        } else if ("gm".equals(c)) {
            if (args.length == 1) return filterPrefix(Arrays.asList("0", "1", "2", "3"), args[0]);
            if (args.length == 2) return onlineNames(args[1]);
        } else if (("tphere".equals(c) || "tp".equals(c) || "invsee".equals(c) || "ecsee".equals(c)
                || "freeze".equals(c) || "whois".equals(c) || "msg".equals(c) || "ignore".equals(c)
                || "skull".equals(c) || "burn".equals(c) || "smite".equals(c) || "sudo".equals(c)) && args.length == 1) {
            return onlineNames(args[0]);
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
            if (args.length == 1 && "cancel".equalsIgnoreCase(args[0])) {
                PreparedSpawnInstaller.cancelRebuild(this, sender);
                return true;
            }
            if (args.length != 1 || !"confirm".equalsIgnoreCase(args[0])) {
                sender.sendMessage(ColorUtil.c("&eСпавн заменится при полном перезапуске. Сделайте внешний бэкап: &f/rebuildspawn confirm &7(отмена: /rebuildspawn cancel)"));
                return true;
            }
            PreparedSpawnInstaller.requestRebuild(this, sender);
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
        if ("gm".equals(c) && !(sender instanceof Player)) {
            if (args.length < 2) {
                sender.sendMessage("Usage from console: /gm <0|1|2|3> <nickname>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("Player not online: " + args[1]);
                return true;
            }
            GameMode gm = parseGameModeArg(args[0]);
            applyGameModeToTarget(target, gm);
            sender.sendMessage("Set gamemode " + gm.name() + " for " + target.getName());
            return true;
        }
        if ("lag".equals(c) && !(sender instanceof Player)) {
            sendLagReport(sender);
            return true;
        }
        if ("clearlag".equals(c) && !(sender instanceof Player)) {
            spawnEngine.runClearLagSweep(true);
            return true;
        }

        if ("crown".equals(c) && !(sender instanceof Player)) return crownEvents.handleCommand(sender, args);
        if ("events".equals(c) && !(sender instanceof Player)) {
            if (args.length >= 2 && "start".equalsIgnoreCase(args[0])) bossEvents.requestBossEvent(args[1], sender);
            else sender.sendMessage("Usage from console: events start <event>");
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
            p.sendTitle(ColorUtil.c("&#00E676&l✔ ДОБРО ПОЖАЛОВАТЬ!"), ColorUtil.c("&fПриятной игры на KINGGRIEF v6.4.0!"), 5, 40, 10);
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

        if ("crown".equals(c)) return crownEvents.handleCommand(p, args);
        if ("trade".equals(c)) return safeTrades.handleCommand(p, args);
        if ("buyorder".equals(c)) return buyOrders.handleCommand(p, args);

        // ================= NAVIGATION & SEPARATE WORLDS (/rtp ONLY TO SURVIVAL WORLD!) =================
        if ("spawn".equals(c) || "kgspawn".equals(c)) {
            // /spawn respects the owner's saved point; /kgspawn opens the built-in village.
            Location destination = "kgspawn".equals(c)
                    ? spawnEngine.getPreparedSpawnArrival() : spawnEngine.getSpawnArrival();
            if (destination == null) {
                p.sendMessage(ColorUtil.c("&cТочка спавна сейчас недоступна. Сообщите администрации или повторите позже."));
                return true;
            }
            Location previous = p.getLocation();
            if (!p.teleport(destination)) {
                p.sendMessage(ColorUtil.c("&cТелепортация на спавн отменена."));
                return true;
            }
            store.setBack(p, previous);
            store.setParkourCp(p, null);
            ColorUtil.sound(p, "entity.enderman.teleport", 1f, 1.1f);
            p.sendMessage(ColorUtil.c(spawnEngine.isInSpawnWorld(destination)
                    ? "&#80D8FF❄ Вы в отдельном мире Яркого Зимнего Спавна! Вход в мир выживания: &#00E676/rtp"
                    : "&#80D8FF❄ Вы телепортированы на сохранённую точку. Готовый спавн: &f/kgspawn"));
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
            Map<String, DataStore.CustomWarp> cws = store.getAllCustomWarps();
            if (!cws.isEmpty()) {
                p.sendMessage(ColorUtil.c("&#80D8FF🧭 Кастомные варпы (&f" + cws.size() + "&#80D8FF): &f"
                        + String.join("&7, &f", cws.keySet()) + " &8(Вход: &e/warp <название>&8)"));
            }
            return true;
        }
        if ("setwarp".equals(c)) {
            return handleWarpSet(p, args.length >= 1 ? args[0] : null);
        }
        if ("delwarp".equals(c)) {
            return handleWarpDel(p, args.length >= 1 ? args[0] : null);
        }
        if ("warpinfo".equals(c)) {
            return handleWarpInfo(p, args.length >= 1 ? args[0] : null);
        }
        if ("warp".equals(c)) {
            if (args.length < 1) {
                guis.openWarpsMenu(p);
                return true;
            }
            String sub = args[0].toLowerCase(Locale.ROOT);
            if ("set".equals(sub) || "create".equals(sub) || "add".equals(sub)) {
                return handleWarpSet(p, args.length >= 2 ? args[1] : null);
            }
            if ("del".equals(sub) || "delete".equals(sub) || "remove".equals(sub) || "rem".equals(sub)) {
                return handleWarpDel(p, args.length >= 2 ? args[1] : null);
            }
            if ("info".equals(sub)) {
                return handleWarpInfo(p, args.length >= 2 ? args[1] : null);
            }
            if ("list".equals(sub)) {
                Map<String, DataStore.CustomWarp> cws = store.getAllCustomWarps();
                p.sendMessage(ColorUtil.c("&#80D8FF&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                p.sendMessage(ColorUtil.c(" &#00E5FF&l🧭 СПИСОК ВСЕХ ВАРПОВ СЕРВЕРА:"));
                p.sendMessage(ColorUtil.c(" &7Системные: &fpvp, kabinki, train, mine, farm, casino, wheel, bafs, parkour, cases, top, spawn"));
                p.sendMessage(ColorUtil.c(" &7Кастомные (&e" + cws.size() + "&7): "
                        + (cws.isEmpty() ? "&8пока нет (/warp set <имя>)" : "&a" + String.join("&7, &a", cws.keySet()))));
                p.sendMessage(ColorUtil.c("&#80D8FF&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                return true;
            }
            Location wl = spawnEngine.getWarpLocation(args[0]);
            if (wl == null) {
                Map<String, DataStore.CustomWarp> cws = store.getAllCustomWarps();
                p.sendMessage(ColorUtil.c("&#FF5252✖ Варп &f" + args[0] + " &#FF5252не найден!"));
                p.sendMessage(ColorUtil.c("&eСистемные варпы: &fpvp, kabinki, train, mine, farm, casino, wheel, bafs, parkour, cases, top"));
                if (!cws.isEmpty()) {
                    p.sendMessage(ColorUtil.c("&bКастомные варпы: &f" + String.join(", ", cws.keySet())));
                }
                return true;
            }
            store.setBack(p, p.getLocation());
            p.teleport(wl);
            ColorUtil.sound(p, "entity.enderman.teleport", 1f, 1.15f);
            p.sendMessage(ColorUtil.c("&#80D8FF🧭 Вы телепортированы на &f/warp " + args[0].toLowerCase(Locale.ROOT) + "&#80D8FF!"));
            return true;
        }
        if ("mine".equals(c) || "farm".equals(c) || "pvp".equals(c)) {
            Location destination = spawnEngine.getWarpLocation(c);
            if (destination == null) { p.sendMessage(ColorUtil.c("&cЭтот варп недоступен, пока готовый спавн не установлен.")); return true; }
            store.setBack(p, p.getLocation());
            p.teleport(destination);
            ColorUtil.sound(p, "entity.enderman.teleport", 1f, 1.15f);
            return true;
        }
        if ("wheel".equals(c)) {
            boolean useSnow = !(args.length > 0 && "money".equalsIgnoreCase(args[0]));
            if (useSnow && store.getSnowflakes(p.getName()) < 5 && store.getCoins(p.getName()) >= 15000 && args.length == 0) {
                useSnow = false;
            }
            spawnEngine.spinFortuneWheel(p, useSnow);
            return true;
        }
        if ("top".equals(c)) {
            sendTopLeaderboardsChat(p);
            return true;
        }
        if ("gifts".equals(c)) {
            int found = store.getFoundSpawnGiftCount(p.getName());
            p.sendMessage(ColorUtil.c("&#FFD740&l🎅 ОХОТА ЗА ПОДАРКАМИ САНТЫ НА СПАВНЕ:"));
            p.sendMessage(ColorUtil.c(" &fНайдено секретных коробок: &#00E676" + found + " / 10"));
            p.sendMessage(ColorUtil.c(" &7За каждый подарок: &a+3 500$ &7и &b+3 Снежинки ❄"));
            p.sendMessage(ColorUtil.c(" &7Супер-приз за 10/10: &a+25 000$ &7+ &b25 Риликов ❖ &7+ &#FF1744Донат-Кейс&7!"));
            return true;
        }
        if ("fps".equals(c)) {
            boolean enabled = store.toggleFpsBoost(p);
            hud.updatePlayerHud(p);
            ColorUtil.sound(p, "block.note_block.pling", 1f, enabled ? 1.4f : 0.9f);
            p.sendMessage(ColorUtil.c(enabled
                    ? "&#00E676🚀 Режим FPS-Бустера ВКЛЮЧЁН! Лишние частицы и анимации отключены (+40..80 FPS)!"
                    : "&#FFD740✨ Режим FPS-Бустера выключен. Плавные анимации префиксов возвращены."));
            return true;
        }
        if ("lag".equals(c)) {
            sendLagReport(p);
            return true;
        }
        if ("ping".equals(c)) {
            Player t = args.length > 0 ? Bukkit.getPlayer(args[0]) : p;
            if (t == null) { p.sendMessage(ColorUtil.c("&cИгрок не в сети!")); return true; }
            int pg = 20;
            try { pg = Math.max(1, t.getPing()); } catch (Throwable ignored) {}
            p.sendMessage(ColorUtil.c("&#80D8FF📶 Пинг игрока &f" + t.getName() + "&8: &#00E676" + pg + " мс"));
            return true;
        }
        if ("stats".equals(c)) {
            String target = args.length > 0 ? args[0] : p.getName();
            int k = store.getKills(target), d = store.getDeaths(target);
            double kd = d == 0 ? k : Math.round((k * 100.0) / d) / 100.0;
            p.sendMessage(ColorUtil.c("&#FFD740&l📊 СТАТИСТИКА ИГРОКА &f" + target + "&#FFD740:"));
            p.sendMessage(ColorUtil.c(" &7• Монеты: &a" + ColorUtil.fmtMoney(store.getCoins(target)) + "$ &8│ &7Рилики: &b" + store.getRelics(target) + " ❖ &8│ &7Снежинки: &b" + store.getSnowflakes(target) + "❄"));
            p.sendMessage(ColorUtil.c(" &7• Убийств: &c" + k + " &8│ &7Смертей: &7" + d + " &8│ &7K/D: &e" + kd));
            p.sendMessage(ColorUtil.c(" &7• Наиграно: &f" + ColorUtil.fmtTime(store.getPlaySeconds(target)) + " &8│ &7Подарки Санты: &a" + store.getFoundSpawnGiftCount(target) + "/10"));
            return true;
        }
        if ("parkour".equals(c)) {
            if (spawnEngine.getWarpLocation("parkour") == null) { p.sendMessage(ColorUtil.c("&cПаркур недоступен, пока готовый спавн не установлен.")); return true; }
            if (args.length > 0 && ("leave".equalsIgnoreCase(args[0]) || "reset".equalsIgnoreCase(args[0]))) {
                store.setParkourCp(p, null);
                p.teleport(spawnEngine.getWarpLocation("parkour"));
                p.sendMessage(ColorUtil.c("&#FF9100🏃 Чекпоинт паркура сброшен. Вы на старте!"));
            } else if (args.length > 0 && "cp".equalsIgnoreCase(args[0])) {
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
        if ("picks".equals(c)) { guis.openPickaxesMenu(p); return true; }
        if ("swords".equals(c)) { guis.openSwordsMenu(p); return true; }
        if ("plastskins".equals(c)) { guis.openPlastSkinsMenu(p); return true; }
        if ("discord".equals(c)) {
            p.sendMessage(ColorUtil.c("&#5865F2&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            p.sendMessage(ColorUtil.c(" &#80D8FF&l🌐 ОФИЦИАЛЬНЫЕ ССЫЛКИ KINGGRIEF:"));
            TextComponent ds = new TextComponent(ColorUtil.c(" &#5865F2&l💬 Наш Discord (Набор в КП и Медиа): &f&n" + DISCORD_URL));
            ds.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, DISCORD_URL));
            p.spigot().sendMessage(ds);
            TextComponent st = new TextComponent(ColorUtil.c(" &#FFD740&l🛒 Сайт и Авто-Донат: &f&n" + SITE_URL));
            st.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, SITE_URL));
            p.spigot().sendMessage(st);
            p.sendMessage(ColorUtil.c("&#5865F2&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            return true;
        }
        if ("donate".equals(c)) { guis.openDonateMenu(p); return true; }
        if ("shop".equals(c)) {
            if (args.length >= 1) guis.openCoinShopCat(p, args[0]);
            else guis.openCoinShopHub(p);
            return true;
        }
        if ("rshop".equals(c)) { guis.openRelicShop(p); return true; }
        if ("skins".equals(c)) {
            if (args.length >= 1 && ("plast".equalsIgnoreCase(args[0]) || "plasts".equalsIgnoreCase(args[0]))) {
                guis.openPlastSkinsMenu(p);
            } else {
                guis.openTrapSkinsMenu(p);
            }
            return true;
        }
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
                if (!Double.isFinite(amt) || amt < 5000 || target.equalsIgnoreCase(p.getName())) {
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
        if ("free".equals(c)) {
            if (args.length >= 1 && "dragon".equalsIgnoreCase(args[0])) {
                RankManager.RankDef cur = ranks.getRank(p);
                if (cur.weight > 12) {
                    p.sendMessage(ColorUtil.c("&#00E676✔ У вас уже активирована более высокая привилегия: "
                            + cur.shortLabel + " &#00E676! Вы уже можете брать &f/kit dragon&a!"));
                    return true;
                }
                if ("dragon".equalsIgnoreCase(cur.id)) {
                    p.sendMessage(ColorUtil.c("&#F5D0FE🐉 У вас уже активирована бесплатная привилегия &lDRAGON&r&#F5D0FE! Заберите набор: &f/kit dragon"));
                    return true;
                }
                ranks.setRank(p.getName(), "dragon", true);
                store.markClaimedFreeDragon(p.getName());
                store.refreshPermissions(p, ranks);
                hud.updatePlayerHud(p);
                ColorUtil.spawnFirework(p.getLocation());
                ColorUtil.sound(p, "ui.toast.challenge_complete", 1f, 1.2f);
                p.sendTitle(ColorUtil.c("&#F5D0FE&l🐉 DRAGON ПОЛУЧЕН!"), ColorUtil.c("&fВаш бесплатный кит: &#FFD740/kit dragon"), 8, 55, 12);
                Bukkit.broadcastMessage(ColorUtil.c("&#F5D0FE&l🎁 [ХАЛЯВА] &fИгрок &#FFD740" + p.getName()
                        + " &fактивировал &#00E676/free dragon &fи получил привилегию &#F5D0FE&lDRAGON &fБЕСПЛАТНО!"));
                return true;
            }
            guis.openFreebieMenu(p);
            if ("default".equalsIgnoreCase(ranks.getRank(p).id)) {
                p.sendMessage(ColorUtil.c("&#F5D0FE🎁 Подсказка: пропиши &#FFD740/free dragon &7и забери донат &#F5D0FE&lDRAGON &7бесплатно!"));
            }
            return true;
        }
        if ("talismans".equals(c)) { guis.openTalismansMenu(p); return true; }
        if ("armors".equals(c)) { guis.openArmorsMenu(p); return true; }
        // ================= v6.4.0: ШАРЫ КВАДРАТОМ, МЕГА-МАГАЗИН, МАНЕКЕН И СВИТКИ ЗАЧАРОВАНИЙ =================
        if ("shars".equals(c) || "orbs".equals(c) || "shary".equals(c)) { guis.openOrbsMenu(p); return true; }
        if ("mega".equals(c)) { guis.openMegaShop(p); return true; }
        if ("maneken".equals(c) || "manekin".equals(c) || "mannequin".equals(c)) { guis.openMannequinMenu(p); return true; }
        if ("scroll".equals(c) || "scrolls".equals(c) || "svitki".equals(c)) {
            if (args.length < 1) {
                p.sendMessage(ColorUtil.c("&#FFD740📜 &fСвитки НОВЫХ зачарований v6.4.0:"));
                p.sendMessage(ColorUtil.c("&#40C4FF  /scroll ice &8— Ледяная Стужа (заморозка + Замедление)"));
                p.sendMessage(ColorUtil.c("&#FF1744  /scroll vampire &8— Вампиризм (хил с урона)"));
                p.sendMessage(ColorUtil.c("&#FF5722  /scroll blast &8— Взрыв (AOE урон + метка PvP)"));
                p.sendMessage(ColorUtil.c("&#FFD740  /scroll smelt &8— Плавка (переплавка дропа)"));
                p.sendMessage(ColorUtil.c("&#80D8FFКупить свитки: &f/mega &8или &f/shop&8. Применить: &fсвиток в правую руку, инструмент — в левую, ПКМ."));
                return true;
            }
            String w = args[0].toLowerCase(Locale.ROOT);
            if (ItemFactory.scrollIndex(w) < 0) {
                p.sendMessage(ColorUtil.c("&#FF1744✖ Нет такого свитка! Доступны: &fice, vampire, blast, smelt&8."));
                return true;
            }
            if (!(ranks.isOwnerLevel(p) || ranks.getRank(p).weight >= 208)) {
                p.sendMessage(ColorUtil.c("&#FF1744✖ Выдавать свитки может только стафф (HELPER+). Купить: &f/mega"));
                return true;
            }
            int cnt = 1;
            if (args.length >= 2) { try { cnt = Math.max(1, Math.min(64, Integer.parseInt(args[1]))); } catch (Throwable ig) {} }
            for (int i = 0; i < cnt; i++) {
                java.util.Map<Integer, ItemStack> left = p.getInventory().addItem(ItemFactory.enchantScroll(w));
                for (ItemStack rest : left.values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), rest);
                }
            }
            p.sendMessage(ColorUtil.c("&#00E676📜 Выдано свитков: &f" + cnt + " &8(" + ItemFactory.scrollName(w) + "&8)"));
            return true;
        }
        if ("cc".equals(c)) {
            sendClanChat(p, args, 0);
            return true;
        }
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
                bossEvents.requestBossEvent(args[1], p);
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
                if (!Double.isFinite(price) || price <= 0 || hand == null || hand.getType() == Material.AIR || "menu_star".equals(ItemFactory.getCustomId(hand))) {
                    p.sendMessage(ColorUtil.c("&cВозьмите предмет в руку и введите: &f/ah sell <цена>"));
                    return true;
                }
                if (store.addAuctionLot(p.getName(), hand, price) == null) {
                    p.sendMessage(ColorUtil.c("&cЛот не сохранён; предмет остался в руке."));
                    return true;
                }
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
            if (!Double.isFinite(bet) || !Double.isFinite(bet * 2.0) || bet < 1000) {
                p.sendMessage(ColorUtil.c("&eМинимальная ставка Монетки: &a1 000$ &7(/cf <сумма>)"));
                return true;
            }
            if (store.createCoinflip(p.getName(), bet) == null) {
                p.sendMessage(ColorUtil.c("&cИгра не создана: проверьте баланс, лимит (4 игры на игрока) и журнал сохранения."));
                return true;
            }
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
                if (store.getActiveWagerDuel(p.getName()) != null || store.getActiveWagerDuel(ch.getName()) != null) {
                    p.sendMessage(ColorUtil.c("&cОдин из игроков уже участвует в активной дуэли!"));
                    return true;
                }
                if (!spawnEngine.isSpawnActive()) {
                    p.sendMessage(ColorUtil.c("&cАрена дуэлей недоступна, пока готовый спавн не установлен."));
                    return true;
                }
                double bet = Math.max(0.0, dr.bet);
                if (bet > 0.0) {
                    if (store.getCoins(ch.getName()) < bet || store.getCoins(p.getName()) < bet) {
                        p.sendMessage(ColorUtil.c("&cУ одного из дуэлянтов недостаточно монет для ставки &e" + ColorUtil.fmtMoney(bet) + "$&c!"));
                        return true;
                    }
                    store.takeCoins(ch.getName(), bet);
                    store.takeCoins(p.getName(), bet);
                    store.save();
                }
                store.startWagerDuel(ch.getName(), p.getName(), bet);
                World sw = VoidSpawnWorldGenerator.getOrCreateSpawnWorld();
                ch.teleport(new Location(sw, 70.5, 100.0, -42.5, 0f, 0f));
                p.teleport(new Location(sw, 71.5, 100.0, -40.5, 180f, 0f));
                Bukkit.broadcastMessage(ColorUtil.c("&#FF1744⚔ Дуэль началась: &f" + ch.getName() + " &cvs &f" + p.getName()
                        + (bet > 0.0 ? " &8[&#FFD740Банк: &a" + ColorUtil.fmtMoney(bet * 2.0) + "$&8]" : " &7в Кабинке #1") + "!"));
                return true;
            }
            if (args.length < 1) {
                p.sendMessage(ColorUtil.c("&eИспользование: &f/duel <ник> [ставка$] &7или &f/duel accept"));
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null || target.equals(p)) {
                p.sendMessage(ColorUtil.c("&cИгрок не найден!"));
                return true;
            }
            double bet = 0.0;
            if (args.length >= 2) {
                try { bet = Double.parseDouble(args[1]); } catch (Exception ex) { bet = -1.0; }
                if (!Double.isFinite(bet) || !Double.isFinite(bet * 2.0) || bet < 0) {
                    p.sendMessage(ColorUtil.c("&cУкажите корректную конечную ставку."));
                    return true;
                }
                if (bet > 0.0 && bet < 100.0) {
                    p.sendMessage(ColorUtil.c("&cМинимальная ставка на дуэль — 100$ (или 0 для обычной дуэли)!"));
                    return true;
                }
                if (bet > 0.0 && store.getCoins(p.getName()) < bet) {
                    p.sendMessage(ColorUtil.c("&cУ вас недостаточно монет для ставки &e" + ColorUtil.fmtMoney(bet) + "$&c!"));
                    return true;
                }
            }
            store.putDuelRequest(p, target, bet);
            p.sendMessage(ColorUtil.c("&#00E676⚔ Вызов на дуэль"
                    + (bet > 0.0 ? " на ставку &a" + ColorUtil.fmtMoney(bet) + "$&#00E676" : "")
                    + " отправлен игроку &f" + target.getName() + "&7!"));
            TextComponent msg = new TextComponent(ColorUtil.c("&#FF1744⚔ Игрок &f" + p.getName() + " &#FF1744вызывает вас на дуэль"
                    + (bet > 0.0 ? " на ставку &a" + ColorUtil.fmtMoney(bet) + "$ " : " в Кабинку! ")));
            TextComponent btn = new TextComponent(ColorUtil.c("&#00E676&l[ ⚔ ПРИНЯТЬ ДУЭЛЬ ]"));
            btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/duel accept"));
            btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(ColorUtil.c("&aНажмите, чтобы принять дуэль!")).create()));
            msg.addExtra(btn);
            target.spigot().sendMessage(msg);
            return true;
        }

        // ================= PAY, BALTOP, DAILY & WEBSITE+SERVER PROMO CODES =================
        if ("pay".equals(c)) {
            if (args.length < 2) { p.sendMessage(ColorUtil.c("&eИспользование: &f/pay <ник> <сумма>")); return true; }
            Player rec = Bukkit.getPlayer(args[0]);
            double amt;
            try { amt = Double.parseDouble(args[1]); } catch (Exception ex) { amt = -1; }
            if (rec == null || rec.equals(p) || !Double.isFinite(amt) || amt < 10) {
                p.sendMessage(ColorUtil.c("&cУкажите игрока онлайн и сумму от 10$!"));
                return true;
            }
            if (!store.transferCoins(p.getName(), rec.getName(), amt)) {
                p.sendMessage(ColorUtil.c("&cПеревод не выполнен: проверьте баланс и сохранение данных.")); return true;
            }
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
            if (!code.matches("[A-Z0-9_-]{1,32}")) {
                p.sendMessage(ColorUtil.c("&cПромокод может содержать только латинские буквы, цифры, - и _."));
                return true;
            }
            if (store.hasUsedPromo(p.getName(), code)) {
                p.sendMessage(ColorUtil.c("&cВы уже активировали промокод " + code + "!"));
                return true;
            }
            DataStore.ServerPromo serverPromo = store.getServerPromo(code);
            double coins = 0;
            int relics = 0, snow = 0, keys = 0;
            String caseId = "", rankUpgrade = null;
            List<ItemStack> items = new ArrayList<ItemStack>();
            if (serverPromo != null) {
                if (serverPromo.maxUses > 0 && serverPromo.usedCount >= serverPromo.maxUses) {
                    p.sendMessage(ColorUtil.c("&cЛимит активаций этого промокода исчерпан!"));
                    return true;
                }
                coins = serverPromo.coins; relics = serverPromo.relics;
                caseId = serverPromo.caseId == null ? "" : serverPromo.caseId.toLowerCase(Locale.ROOT);
                keys = serverPromo.caseQty;
                if (serverPromo.rankId != null && !serverPromo.rankId.isEmpty()) {
                    if (!ranks.exists(serverPromo.rankId)) {
                        p.sendMessage(ColorUtil.c("&cУ промокода неизвестная привилегия. Сообщите администрации."));
                        return true;
                    }
                    RankManager.RankDef targetRank = ranks.byId(serverPromo.rankId);
                    if (targetRank.weight > ranks.getRank(p).weight) rankUpgrade = targetRank.id;
                }
            } else if ("NEWYEAR2026".equals(code)) {
                coins = 25000; relics = 25; caseId = "summer"; keys = 1;
            } else if ("REALLYWORLD".equals(code)) {
                coins = 30000; relics = 30; snow = 10;
            } else if ("KINGGRIEF".equals(code)) {
                coins = 20000; relics = 20; items.add(ItemFactory.potionKiller(1));
            } else if ("FTRW".equals(code) || "FTRW2026".equals(code)) {
                coins = 25000; items.add(ItemFactory.plast(4)); items.add(ItemFactory.trapka(2));
            } else if ("SANTA".equals(code)) {
                coins = 15000; items.add(ItemFactory.nyMandarin(8)); items.add(ItemFactory.potionChampagne(1));
            } else if ("RILIK".equals(code)) relics = 35;
            else if ("FREE".equals(code)) { coins = 15000; caseId = "money"; keys = 1; }
            else {
                p.sendMessage(ColorUtil.c("&cНеизвестный промокод! Посмотрите доступные коды в &f/free"));
                return true;
            }
            ItemStack[] prizes = items.toArray(new ItemStack[items.size()]);
            if (prizes.length > 0 && !InventoryCapacity.canFit(p, prizes)) {
                p.sendMessage(ColorUtil.c("&cОсвободите место для наград: промокод ещё не использован."));
                return true;
            }
            DataStore.PromoClaim result = store.claimPromoReward(p, code, coins, relics, snow, caseId, keys, rankUpgrade, serverPromo);
            if (result != DataStore.PromoClaim.CLAIMED) {
                String message = result == DataStore.PromoClaim.USED ? "&cЭтот промокод уже использован."
                        : result == DataStore.PromoClaim.EXHAUSTED ? "&cЛимит промокода исчерпан."
                        : "&cПромокод не применён: проверьте данные и сохранение, сообщите администрации.";
                p.sendMessage(ColorUtil.c(message));
                return true;
            }
            if (rankUpgrade != null) {
                try { ranks.setRank(p.getName(), rankUpgrade, true); }
                catch (RuntimeException error) {
                    getLogger().severe("[Promo] Local rank was saved for " + p.getName() + "; check LuckPerms sync: " + error);
                }
            }
            if (prizes.length > 0) for (ItemStack left : p.getInventory().addItem(prizes).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), left);
                getLogger().warning("[Promo] Inventory changed during claim for " + p.getName() + "; remainder dropped nearby.");
                p.sendMessage(ColorUtil.c("&eИнвентарь изменился во время выдачи: остаток лежит рядом."));
            }
            hud.updatePlayerHud(p);
            ColorUtil.sound(p, "ui.toast.challenge_complete", 1f, 1.2f);
            p.sendMessage(ColorUtil.c("&#00E676🎁 Промокод &f" + code + " &#00E676активирован! Награда выдана."));
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
            if (store.isTpDisabled(t.getName()) && !ranks.isOwnerLevel(p)) {
                p.sendMessage(ColorUtil.c("&#FF5252✖ Игрок &f" + t.getName() + " &#FF5252отключил приём запросов телепортации (/tptoggle)!"));
                return true;
            }
            if (store.isIgnoring(t.getName(), p.getName()) && !ranks.isOwnerLevel(p)) {
                p.sendMessage(ColorUtil.c("&#FF5252✖ Этот игрок добавил вас в игнор!"));
                return true;
            }
            boolean here = "tpahere".equals(c);
            store.sendTpa(p, t, here);
            p.sendMessage(ColorUtil.c("&#00E676✔ Запрос отправлен игроку &f" + t.getName()));
            t.sendMessage(ColorUtil.c("&#FFD740⚡ Игрок &f" + p.getName() + (here ? " &7просит вас телепортироваться к нему!" : " &7хочет телепортироваться к вам!") + " &a/tpaccept &8или &c/tpdeny"));
            return true;
        }
        if ("tpaccept".equals(c)) {
            boolean[] isHere = new boolean[1];
            UUID sid = store.popTpaSender(p, isHere);
            Player s = sid != null ? Bukkit.getPlayer(sid) : null;
            if (s == null) { p.sendMessage(ColorUtil.c("&cНет активных запросов телепортации!")); return true; }
            if (isHere[0]) {
                store.setBack(p, p.getLocation());
                p.teleport(s.getLocation());
            } else {
                store.setBack(s, s.getLocation());
                s.teleport(p.getLocation());
            }
            p.sendMessage(ColorUtil.c("&#00E676✔ Запрос принят!"));
            s.sendMessage(ColorUtil.c("&#00E676✔ Телепортация выполнена!"));
            return true;
        }
        if ("tpdeny".equals(c)) {
            UUID sid = store.denyTpaSender(p);
            Player s = sid != null ? Bukkit.getPlayer(sid) : null;
            if (sid == null) {
                p.sendMessage(ColorUtil.c("&cУ вас нет активных запросов телепортации."));
                return true;
            }
            p.sendMessage(ColorUtil.c("&#FF5252✖ Вы отклонили запрос на телепортацию."));
            if (s != null) s.sendMessage(ColorUtil.c("&#FF5252✖ Игрок &f" + p.getName() + " &#FF5252отклонил ваш запрос на телепортацию."));
            return true;
        }
        if ("tptoggle".equals(c)) {
            boolean off = store.toggleTpDisabled(p.getName());
            p.sendMessage(ColorUtil.c(off
                    ? "&#FF5252🔕 Приём запросов /tpa ОТКЛЮЧЁН!"
                    : "&#00E676🔔 Приём запросов /tpa снова ВКЛЮЧЁН!"));
            return true;
        }
        if ("msg".equals(c) || "reply".equals(c)) {
            return handlePrivateMessageCommand(p, c, args);
        }
        if ("msgtoggle".equals(c)) {
            boolean off = store.toggleMsgDisabled(p.getName());
            p.sendMessage(ColorUtil.c(off
                    ? "&#FF5252🔕 Личные сообщения (/msg) ОТКЛЮЧЕНЫ!"
                    : "&#00E676🔔 Личные сообщения (/msg) ВКЛЮЧЕНЫ!"));
            return true;
        }
        if ("ignore".equals(c)) {
            if (args.length < 1) {
                p.sendMessage(ColorUtil.c("&eИспользование: &f/ignore <ник>"));
                return true;
            }
            if (args[0].equalsIgnoreCase(p.getName())) {
                p.sendMessage(ColorUtil.c("&cНельзя добавить в игнор самого себя!"));
                return true;
            }
            boolean ignored = store.toggleIgnore(p.getName(), args[0]);
            p.sendMessage(ColorUtil.c(ignored
                    ? "&#FF5252🚫 Игрок &f" + args[0] + " &#FF5252добавлен в чёрный список (/ignore)!"
                    : "&#00E676✔ Игрок &f" + args[0] + " &#00E676удалён из чёрного списка."));
            return true;
        }
        if ("god".equals(c) || "speed".equals(c) || "ptime".equals(c) || "day".equals(c) || "night".equals(c)
                || "sun".equals(c) || "topblock".equals(c) || "jump".equals(c) || "rename".equals(c)
                || "relore".equals(c) || "skull".equals(c) || "suicide".equals(c) || "xp".equals(c)
                || "burn".equals(c) || "smite".equals(c) || "giveall".equals(c) || "sudo".equals(c)
                || "setspawn".equals(c) || "socialspy".equals(c)) {
            return handleExtraPowerCommands(p, c, args);
        }
        if ("friend".equals(c)) {
            return handleFriendTeamCommand(p, args);
        }
        if ("tc".equals(c)) {
            return handleTeamChatCommand(p, args);
        }
        if ("sb".equals(c)) {
            boolean en = store.toggleSb(p);
            hud.updatePlayerHud(p);
            p.sendMessage(ColorUtil.c(en ? "&aСкорборд включён!" : "&eСкорборд скрыт."));
            return true;
        }
        if ("near".equals(c)) {
            if (ranks.getRank(p).weight < 35 && !ranks.isOwnerLevel(p)) {
                p.sendMessage(ColorUtil.c("&cКоманда /near доступна с привилегии BUNNY (59₽)!"));
                return true;
            }
            StringBuilder sb = new StringBuilder();
            for (Entity en : p.getNearbyEntities(150, 120, 150)) {
                if (en instanceof Player && !en.equals(p) && !store.isVanished((Player) en)) {
                    Player other = (Player) en;
                    int dist = (int) p.getLocation().distance(other.getLocation());
                    boolean ally = store.areTeammates(p.getName(), other.getName());
                    if (sb.length() > 0) sb.append("&7, ");
                    sb.append(ally ? "&#00E676[ТИМА] " : "&#FF5252⚔ ")
                      .append("&f").append(other.getName()).append(" &e(").append(dist).append("м)");
                }
            }
            p.sendMessage(ColorUtil.c("&#80D8FF📡 Игроки рядом (150м): " + (sb.length() == 0 ? "&7никого нет" : sb.toString())));
            return true;
        }
        if ("hat".equals(c)) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() == Material.AIR || "menu_star".equals(ItemFactory.getCustomId(hand))) {
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
            int teamExt = 0;
            for (Entity en : p.getNearbyEntities(10, 5, 10)) {
                if (en instanceof Player && store.areTeammates(p.getName(), ((Player) en).getName())) {
                    ((Player) en).setFireTicks(0);
                    teamExt++;
                }
            }
            p.sendMessage(ColorUtil.c("&#80D8FF💧 Вы потушили себя" + (teamExt > 0 ? " и &#00E676" + teamExt + " тиммейтов рядом" : "") + "!"));
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
            guis.repairAllItems(p);
            ColorUtil.sound(p, "block.anvil.use", 0.85f, 1.25f);
            p.sendMessage(ColorUtil.c("&#00E676🔧 Все предметы, оружие и броня (Закалка ×2) полностью починены!"));
            return true;
        }
        if ("anvil".equals(c) && (myRank.weight >= 80 || p.hasPermission("kinggrief.anvil"))) {
            try {
                p.openInventory(Bukkit.createInventory(p, org.bukkit.event.inventory.InventoryType.ANVIL));
            } catch (Throwable ex) {
                p.sendMessage(ColorUtil.c("&eИспользуйте наковальни на /warp pvp или /warp bafs!"));
            }
            return true;
        }
        if ("fly".equals(c) && (myRank.weight >= 90 || p.hasPermission("kinggrief.fly"))) {
            p.setAllowFlight(!p.getAllowFlight());
            p.sendMessage(ColorUtil.c(p.getAllowFlight() ? "&#00E676🕊 Режим полёта включён!" : "&eРежим полёта выключен."));
            return true;
        }

        // ================= /gm <0|1|2|3> [никнейм] (ВКЛЮЧАЯ ВЫКЛЮЧЕНИЕ /gm 0 ДРУГОМУ ИГРОКУ!) =================
        if ("gm".equals(c) || "gms".equals(c)) {
            String modeRaw = "gms".equals(c) ? "0" : (args.length > 0 ? args[0] : "1");
            String targetArg = "gms".equals(c) ? (args.length > 0 ? args[0] : null) : (args.length >= 2 ? args[1] : null);
            GameMode gm = parseGameModeArg(modeRaw);

            if (targetArg != null) {
                // Changing ANOTHER player's gamemode (/gm 0 <nick>)!
                boolean canChangeOther = (gm == GameMode.SURVIVAL || gm == GameMode.SPECTATOR)
                        ? ranks.isMlModerOrHigher(p)
                        : (myRank.weight >= 255 || "owner".equals(myRank.id));
                if (!canChangeOther) {
                    p.sendMessage(ColorUtil.c("&#FF1744✖ Выключать ГМ другому игроку (/gm 0 <ник>) доступно ТОЛЬКО от Мл. Модера и выше!"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(targetArg);
                if (target == null) target = Bukkit.getPlayer(targetArg);
                if (target == null) {
                    p.sendMessage(ColorUtil.c("&cИгрок &f" + targetArg + " &cне найден в сети!"));
                    return true;
                }
                RankManager.RankDef tRank = ranks.getRank(target);
                if (!target.equals(p) && tRank.weight > myRank.weight && !ranks.isOwnerLevel(myRank.id)) {
                    p.sendMessage(ColorUtil.c("&#FF1744✖ Нельзя менять режим игры старшему по должности (&f" + target.getName() + "&#FF1744)!"));
                    return true;
                }
                applyGameModeToTarget(target, gm);
                p.sendMessage(ColorUtil.c("&#00E676✔ Игроку &f" + target.getName() + " &#00E676установлен режим игры: &e" + gm.name()));
                if (!target.equals(p)) {
                    target.sendMessage(ColorUtil.c("&#FFD740⚡ Модератор &f" + p.getName() + " &#FFD740изменил ваш режим игры на &f" + gm.name()));
                }
                return true;
            }

            // Changing OWN gamemode
            boolean canSelfGm = (gm == GameMode.SURVIVAL || gm == GameMode.SPECTATOR)
                    ? (myRank.weight >= 178 || ranks.isMlModerOrHigher(p) || p.hasPermission("kinggrief.gm"))
                    : (myRank.weight >= 178 || p.hasPermission("kinggrief.gm"));
            if (!canSelfGm) {
                p.sendMessage(ColorUtil.c("&#FF1744✖ У вашей привилегии нет доступа к /gm!"));
                return true;
            }
            if (gm == GameMode.CREATIVE && spawnEngine.isInSpawnWorld(p.getLocation()) && !ranks.isOwnerLevel(myRank.id) && !"techadmin".equals(myRank.id)) {
                p.sendMessage(ColorUtil.c("&eВнимание: строить/ломать на спавне может только OWNER!"));
            }
            applyGameModeToTarget(p, gm);
            p.sendMessage(ColorUtil.c("&#00E676⚡ Ваш режим игры: &f" + gm.name()));
            return true;
        }

        // ================= СТРОГО ОТ МЛ. МОДЕРА (mlmoder, weight >= 220): /tphere, /tp, /tppos, /invsee, /ecsee, /freeze, /whois, /clearlag =================
        if ("tphere".equals(c) || "tp".equals(c) || "tppos".equals(c) || "invsee".equals(c)
                || "ecsee".equals(c) || "freeze".equals(c) || "whois".equals(c) || "clearlag".equals(c) || "tpall".equals(c)) {
            return handleStaffMlModerCommand(p, myRank, c, args);
        }
        if ("staffchat".equals(c)) {
            if (!ranks.isRealStaffHelperOrHigher(p)) {
                p.sendMessage(ColorUtil.c("&#FF1744✖ Чат персонала (/sc) доступен только Хелперам и Модерации!"));
                return true;
            }
            if (args.length == 0) {
                p.sendMessage(ColorUtil.c("&#38BDF8Использование: &f/sc <сообщение>"));
                return true;
            }
            StringBuilder sb = new StringBuilder();
            for (String a : args) sb.append(a).append(' ');
            String line = ColorUtil.c("&#38BDF8&l[STAFF] " + ranks.getAnimatedBadge(myRank, 0) + " &f" + p.getName() + " &8» &#E0F2FE" + sb.toString().trim());
            for (Player on : Bukkit.getOnlinePlayers()) {
                if (ranks.isRealStaffHelperOrHigher(on)) on.sendMessage(line);
            }
            Bukkit.getConsoleSender().sendMessage(line);
            return true;
        }

        if ("vanish".equals(c) && (myRank.tier >= 4 || ranks.isMlModerOrHigher(p) || p.hasPermission("kinggrief.vanish"))) {
            boolean v = store.toggleVanish(p);
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.equals(p)) continue;
                if (v) other.hidePlayer(this, p); else other.showPlayer(this, p);
            }
            p.sendMessage(ColorUtil.c(v ? "&#00E676👻 Ваниш включён!" : "&eВаниш выключен."));
            return true;
        }
        if (("mute".equals(c) || "unmute".equals(c) || "kick".equals(c) || "ban".equals(c) || "unban".equals(c) || "bc".equals(c))
                && (myRank.tier >= 4 || ranks.isRealStaffHelperOrHigher(p) || "owner".equalsIgnoreCase(myRank.id))) {
            handleModeration(p, c, args);
            return true;
        }
        p.sendMessage(ColorUtil.c("&#FF1744✖ У вашей привилегии нет доступа к этой команде! Повысить ранг: &#FFD740/donate"));
        return true;
    }

    private GameMode parseGameModeArg(String raw) {
        String m = raw == null ? "0" : raw.trim().toLowerCase(Locale.ROOT);
        if ("0".equals(m) || m.startsWith("s") && !"sp".equals(m) && !m.startsWith("spec")) return GameMode.SURVIVAL;
        if ("2".equals(m) || m.startsWith("a")) return GameMode.ADVENTURE;
        if ("3".equals(m) || "sp".equals(m) || m.startsWith("spec")) return GameMode.SPECTATOR;
        return GameMode.CREATIVE;
    }

    private void applyGameModeToTarget(Player target, GameMode gm) {
        target.setGameMode(gm);
        if (gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE) {
            target.setFlying(false);
            target.setAllowFlight(false);
        }
    }

    private boolean handleStaffMlModerCommand(Player p, RankManager.RankDef myRank, String c, String[] args) {
        if ("tpall".equals(c)) {
            if (myRank.weight < 255) {
                p.sendMessage(ColorUtil.c("&#FF1744✖ Команда /tpall доступна ТОЛЬКО от КП / Куратора / OWNER!"));
                return true;
            }
            int cnt = 0;
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.equals(p)) continue;
                store.setBack(other, other.getLocation());
                other.teleport(p.getLocation());
                other.sendMessage(ColorUtil.c("&#FFD740⚡ Администратор &f" + p.getName() + " &#FFD740телепортировал всех игроков к себе!"));
                cnt++;
            }
            p.sendMessage(ColorUtil.c("&#00E676✔ Телепортировано игроков к вам: &f" + cnt));
            return true;
        }

        // STRICT GATE: ONLY ML.MODER (weight >= 220) AND HIGHER REAL STAFF!
        if (!ranks.isMlModerOrHigher(p)) {
            ColorUtil.sound(p, "entity.villager.no", 1f, 1f);
            p.sendMessage(ColorUtil.c("&#FF1744✖ Команда &f/" + c + " &#FF1744доступна СТРОГО от должности &#7DD3FC&lМЛ. МОДЕР &#FF1744и выше!"));
            return true;
        }

        if ("clearlag".equals(c)) {
            spawnEngine.runClearLagSweep(true);
            return true;
        }
        if ("tphere".equals(c)) {
            if (args.length < 1) {
                p.sendMessage(ColorUtil.c("&#7DD3FCИспользование: &f/tphere <ник> &7(телепортировать игрока к себе)"));
                return true;
            }
            Player t = Bukkit.getPlayerExact(args[0]);
            if (t == null) t = Bukkit.getPlayer(args[0]);
            if (t == null || t.equals(p)) {
                p.sendMessage(ColorUtil.c("&cИгрок не найден в сети!"));
                return true;
            }
            RankManager.RankDef tr = ranks.getRank(t);
            if (tr.weight > myRank.weight && !ranks.isOwnerLevel(myRank.id)) {
                p.sendMessage(ColorUtil.c("&#FF1744✖ Нельзя телепортировать к себе старшего по должности (&f" + t.getName() + "&#FF1744)!"));
                return true;
            }
            store.setBack(t, t.getLocation());
            t.teleport(p.getLocation());
            ColorUtil.sound(p, "entity.enderman.teleport", 1f, 1.2f);
            p.sendMessage(ColorUtil.c("&#00E676✔ Вы телепортировали игрока &f" + t.getName() + " &#00E676к себе!"));
            t.sendMessage(ColorUtil.c("&#7DD3FC⚡ Модератор &f" + p.getName() + " &#7DD3FCтелепортировал вас к себе."));
            return true;
        }
        if ("tp".equals(c)) {
            if (args.length == 1) {
                Player t = Bukkit.getPlayerExact(args[0]);
                if (t == null) t = Bukkit.getPlayer(args[0]);
                if (t == null) { p.sendMessage(ColorUtil.c("&cИгрок не найден!")); return true; }
                store.setBack(p, p.getLocation());
                p.teleport(t.getLocation());
                ColorUtil.sound(p, "entity.enderman.teleport", 1f, 1.2f);
                p.sendMessage(ColorUtil.c("&#00E676✔ Телепортация к игроку &f" + t.getName() + " &#00E676выполнена!"));
                return true;
            }
            if (args.length == 2) {
                if (myRank.weight < 235) {
                    p.sendMessage(ColorUtil.c("&#FF1744✖ Телепортировать одного игрока к другому (/tp <ник1> <ник2>) доступно от Ст. Модера!"));
                    return true;
                }
                Player p1 = Bukkit.getPlayer(args[0]);
                Player p2 = Bukkit.getPlayer(args[1]);
                if (p1 == null || p2 == null) { p.sendMessage(ColorUtil.c("&cОдин из игроков не в сети!")); return true; }
                store.setBack(p1, p1.getLocation());
                p1.teleport(p2.getLocation());
                p.sendMessage(ColorUtil.c("&#00E676✔ Игрок &f" + p1.getName() + " &#00E676телепортирован к &f" + p2.getName() + "&#00E676!"));
                return true;
            }
            if (args.length >= 3) {
                try {
                    double x = Double.parseDouble(args[0]);
                    double y = Double.parseDouble(args[1]);
                    double z = Double.parseDouble(args[2]);
                    store.setBack(p, p.getLocation());
                    p.teleport(new Location(p.getWorld(), x, y, z, p.getLocation().getYaw(), p.getLocation().getPitch()));
                    p.sendMessage(ColorUtil.c("&#00E676✔ Телепортация на координаты &f" + (int) x + ", " + (int) y + ", " + (int) z));
                } catch (Exception ex) {
                    p.sendMessage(ColorUtil.c("&cУкажите корректные числа X Y Z!"));
                }
                return true;
            }
            p.sendMessage(ColorUtil.c("&#7DD3FCИспользование: &f/tp <ник> &8| &f/tp <x> <y> <z>"));
            return true;
        }
        if ("tppos".equals(c)) {
            if (args.length < 3) {
                p.sendMessage(ColorUtil.c("&#7DD3FCИспользование: &f/tppos <x> <y> <z> [мир]"));
                return true;
            }
            try {
                double x = Double.parseDouble(args[0]);
                double y = Double.parseDouble(args[1]);
                double z = Double.parseDouble(args[2]);
                World w = args.length >= 4 && Bukkit.getWorld(args[3]) != null ? Bukkit.getWorld(args[3]) : p.getWorld();
                store.setBack(p, p.getLocation());
                p.teleport(new Location(w, x, y, z, p.getLocation().getYaw(), p.getLocation().getPitch()));
                p.sendMessage(ColorUtil.c("&#00E676✔ Телепортация на &f" + (int) x + ", " + (int) y + ", " + (int) z + " &7(" + w.getName() + ")"));
            } catch (Exception ex) {
                p.sendMessage(ColorUtil.c("&cНекорректные координаты!"));
            }
            return true;
        }
        if ("invsee".equals(c)) {
            if (args.length < 1) { p.sendMessage(ColorUtil.c("&#7DD3FCИспользование: &f/invsee <ник>")); return true; }
            Player t = Bukkit.getPlayer(args[0]);
            if (t == null) { p.sendMessage(ColorUtil.c("&cИгрок не в сети!")); return true; }
            p.openInventory(t.getInventory());
            p.sendMessage(ColorUtil.c("&#00E676🔍 Открыт инвентарь игрока &f" + t.getName()));
            return true;
        }
        if ("ecsee".equals(c)) {
            if (args.length < 1) { p.sendMessage(ColorUtil.c("&#7DD3FCИспользование: &f/ecsee <ник>")); return true; }
            Player t = Bukkit.getPlayer(args[0]);
            if (t == null) { p.sendMessage(ColorUtil.c("&cИгрок не в сети!")); return true; }
            p.openInventory(t.getEnderChest());
            p.sendMessage(ColorUtil.c("&#00E676🔍 Открыт Эндер-Сундук игрока &f" + t.getName()));
            return true;
        }
        if ("freeze".equals(c)) {
            if (args.length < 1) { p.sendMessage(ColorUtil.c("&#7DD3FCИспользование: &f/freeze <ник>")); return true; }
            Player t = Bukkit.getPlayer(args[0]);
            if (t == null || t.equals(p)) { p.sendMessage(ColorUtil.c("&cИгрок не найден!")); return true; }
            if (ranks.getRank(t).weight > myRank.weight) {
                p.sendMessage(ColorUtil.c("&#FF1744✖ Нельзя заморозить старшего по должности!"));
                return true;
            }
            boolean frozen = store.toggleQuickFreeze(t.getUniqueId());
            p.sendMessage(ColorUtil.c(frozen
                    ? "&#00E676❄ Игрок &f" + t.getName() + " &#00E676заморожен на месте!"
                    : "&#FFD740☀ Игрок &f" + t.getName() + " &#FFD740разморожен."));
            t.sendMessage(ColorUtil.c(frozen
                    ? "&#FF1744&l❄ ВЫ ЗАМОРОЖЕНЫ МОДЕРАТОРОМ &f" + p.getName() + "&#FF1744&l! Не выходите с сервера!"
                    : "&#00E676✔ Вы разморожены модератором &f" + p.getName()));
            return true;
        }
        if ("whois".equals(c)) {
            if (args.length < 1) { p.sendMessage(ColorUtil.c("&#7DD3FCИспользование: &f/whois <ник>")); return true; }
            Player t = Bukkit.getPlayer(args[0]);
            if (t == null) { p.sendMessage(ColorUtil.c("&cИгрок не в сети!")); return true; }
            Location tl = t.getLocation();
            RankManager.RankDef tr = ranks.getRank(t);
            String ipAddr = t.getAddress() != null && t.getAddress().getAddress() != null ? t.getAddress().getAddress().getHostAddress() : "N/A";
            p.sendMessage(ColorUtil.c("&#7DD3FC&l📋 ДОСЬЕ МОДЕРАЦИИ НА ИГРОКА &f" + t.getName() + "&#7DD3FC:"));
            p.sendMessage(ColorUtil.c(" &7• Должность/Ранг: &e" + tr.id.toUpperCase(Locale.ROOT) + " &8(вес " + tr.weight + ") &8│ &7ГМ: &f" + t.getGameMode().name()));
            p.sendMessage(ColorUtil.c(" &7• Мир: &b" + tl.getWorld().getName() + " &8(&f" + tl.getBlockX() + ", " + tl.getBlockY() + ", " + tl.getBlockZ() + "&8)"));
            if (myRank.weight >= 255) {
                p.sendMessage(ColorUtil.c(" &7• IP: &f" + ipAddr));
            }
            p.sendMessage(ColorUtil.c(" &7• Баланс: &a" + ColorUtil.fmtMoney(store.getCoins(t.getName())) + "$ &8│ &b" + store.getRelics(t.getName()) + "❖ &8│ &7Ваниш: " + (store.isVanished(t) ? "&aДа" : "&7Нет")));
            return true;
        }
        return true;
    }

    private void sendTopLeaderboardsChat(CommandSender s) {
        s.sendMessage(ColorUtil.c("&#FFD740&l🏆 ТОП-5 БОГАЧЕЙ И КИЛЛЕРОВ СЕРВЕРА &7(/warp top):"));
        List<Map.Entry<String, Double>> rich = store.getTopCoins(5);
        for (int i = 0; i < rich.size(); i++) {
            s.sendMessage(ColorUtil.c(" &#FFE082#" + (i + 1) + " &f" + rich.get(i).getKey() + " &8— &a" + ColorUtil.fmtMoney(rich.get(i).getValue().doubleValue()) + "$"));
        }
        List<Map.Entry<String, Integer>> kills = store.getTopKills(5);
        for (int i = 0; i < kills.size(); i++) {
            s.sendMessage(ColorUtil.c(" &#FF8A80#" + (i + 1) + " &f" + kills.get(i).getKey() + " &8— &c" + kills.get(i).getValue().intValue() + " ⚔"));
        }
    }

    private void sendLagReport(CommandSender s) {
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L);
        long maxMb = rt.maxMemory() / (1024L * 1024L);
        int chunks = 0, entities = 0;
        for (World w : Bukkit.getWorlds()) {
            chunks += w.getLoadedChunks().length;
            entities += w.getEntities().size();
        }
        s.sendMessage(ColorUtil.c("&#00E676&l⚡ ПРОИЗВОДИТЕЛЬНОСТЬ KINGGRIEF v6.4.0:"));
        s.sendMessage(ColorUtil.c(" &7• TPS сервера: &#00E676" + spawnEngine.getCurrentTps() + " / 20.0"));
        s.sendMessage(ColorUtil.c(" &7• Память ОЗУ: &#FFD740" + usedMb + " МБ &7/ &f" + maxMb + " МБ"));
        s.sendMessage(ColorUtil.c(" &7• Чанки: &#80D8FF" + chunks + " &8│ &7Энтити во всех мирах: &f" + entities));
        s.sendMessage(ColorUtil.c(" &7• Повысить FPS лаунчера на слабом ПК: &#00E676/fps"));
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
        if (!Double.isFinite(amt) || amt < 0) {
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

    private void sendClanChat(Player p, String[] args, int startIdx) {
        DataStore.Clan my = store.getClanOf(p.getName());
        if (my == null) {
            p.sendMessage(ColorUtil.c("&cВы не состоите в клане! Создайте: &f/clan create <тег>"));
            return;
        }
        if (args.length <= startIdx) {
            p.sendMessage(ColorUtil.c("&#B388FFИспользование кланового чата: &f/cc <сообщение> &7или &f/clan chat <сообщение>"));
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = startIdx; i < args.length; i++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(args[i]);
        }
        String msg = ColorUtil.c(my.color + "&l[КЛАН " + my.tag + "] " + my.getRoleName(p.getName())
                + " &f" + p.getName() + " &8» &#E0F2FE" + sb.toString());
        broadcastToClan(my, msg);
    }

    private void broadcastToClan(DataStore.Clan clan, String coloredMsg) {
        if (clan == null) return;
        for (Player on : Bukkit.getOnlinePlayers()) {
            if (clan.members.contains(on.getName().toLowerCase(Locale.ROOT))) {
                on.sendMessage(coloredMsg);
            }
        }
    }

    private void sendClanHelp(Player p) {
        p.sendMessage(ColorUtil.c("&#B388FF&l╔══════════ 🛡 КОМАНДЫ КЛАНА KINGGRIEF v6.4.0 ══════════╗"));
        p.sendMessage(ColorUtil.c("&#B388FF║ &f/clan menu &7— Интерактивное меню клана и ТОП"));
        p.sendMessage(ColorUtil.c("&#B388FF║ &f/clan create <тег> &7— Создать клан (&a25 000$&7)"));
        p.sendMessage(ColorUtil.c("&#B388FF║ &f/clan invite <ник> &7— Пригласить игрока &a[Кнопки Принять/Отклонить]"));
        p.sendMessage(ColorUtil.c("&#B388FF║ &f/clan accept [тег] &8| &f/clan deny [тег] &7— Принять / Отклонить инвайт"));
        p.sendMessage(ColorUtil.c("&#B388FF║ &f/clan kick <ник> &8| &f/clan leave &8| &f/clan disband &7— Состав клана"));
        p.sendMessage(ColorUtil.c("&#B388FF║ &f/clan promote <ник> &8| &f/clan demote <ник> &8| &f/clan leader <ник>"));
        p.sendMessage(ColorUtil.c("&#B388FF║ &f/clan sethome &8| &f/clan home &8| &f/clan delhome &7— База клана"));
        p.sendMessage(ColorUtil.c("&#B388FF║ &f/clan storage &7— Общее хранилище (сейф) клана"));
        p.sendMessage(ColorUtil.c("&#B388FF║ &f/clan money &8| &f/clan deposit <сумма> &8| &f/clan withdraw <сумма>"));
        p.sendMessage(ColorUtil.c("&#B388FF║ &f/clan upgrade &7— Прокачать уровень клана (до 10 ур.)"));
        p.sendMessage(ColorUtil.c("&#B388FF║ &f/clan pvp &7— Вкл/Выкл урон по своим соклановцам"));
        p.sendMessage(ColorUtil.c("&#B388FF║ &f/clan ally <тег> &8| &f/clan neutral <тег> &7— Союзы между кланами"));
        p.sendMessage(ColorUtil.c("&#B388FF║ &f/clan color <цвет> &8| &f/clan rename <тег> &7— Цвет и смена тега"));
        p.sendMessage(ColorUtil.c("&#B388FF║ &f/clan coords &8| &f/clan glow &7— Координаты и подсветка своих"));
        p.sendMessage(ColorUtil.c("&#B388FF║ &f/clan info [тег] &8| &f/clan top &8| &f/cc <текст> &7— Инфо, ТОП и Чат"));
        p.sendMessage(ColorUtil.c("&#B388FF&l╚═════════════════════════════════════════════════════╝"));
    }

    private void handleClanCmd(Player p, String[] args) {
        if (args.length == 0 || "menu".equalsIgnoreCase(args[0])) {
            guis.openClanMenu(p);
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("help".equals(sub) || "?".equals(sub)) {
            sendClanHelp(p);
            return;
        }
        DataStore.Clan my = store.getClanOf(p.getName());
        int myRole = my != null ? my.getRole(p.getName()) : -1;

        if ("create".equals(sub)) {
            if (args.length < 2) { p.sendMessage(ColorUtil.c("&eИспользование: &f/clan create <тег> &7(3–10 букв/цифр, 25 000$)")); return; }
            if (my != null) { p.sendMessage(ColorUtil.c("&cВы уже состоите в клане [" + my.tag + "]!")); return; }
            String rawTag = args[1].replaceAll("[^A-Za-zА-Яа-яЁё0-9_]", "");
            if (rawTag.length() < 3 || rawTag.length() > 10) {
                p.sendMessage(ColorUtil.c("&cТег клана должен содержать от 3 до 10 букв/цифр!"));
                return;
            }
            if (!store.takeCoins(p.getName(), 25000.0)) {
                p.sendMessage(ColorUtil.c("&cДля создания клана требуется &a25 000$&c!"));
                return;
            }
            DataStore.Clan created = store.createClan(p.getName(), rawTag);
            if (created == null) {
                store.addCoins(p.getName(), 25000.0);
                p.sendMessage(ColorUtil.c("&cКлан с таким тегом уже существует!"));
                return;
            }
            hud.updatePlayerHud(p);
            ColorUtil.sound(p, "ui.toast.challenge_complete", 1f, 1.2f);
            Bukkit.broadcastMessage(ColorUtil.c("&#B388FF&l🛡 [КЛАНЫ] &fИгрок &#FFD740" + p.getName() + " &fосновал новый клан " + created.color + "&l[" + created.tag + "]&f!"));
            return;
        }

        if ("invite".equals(sub)) {
            if (my == null || myRole < 2) { p.sendMessage(ColorUtil.c("&cПриглашать игроков могут только Модераторы, Зам.Лидера и Лидер клана!")); return; }
            if (args.length < 2) { p.sendMessage(ColorUtil.c("&eИспользование: &f/clan invite <ник>")); return; }
            if (my.members.size() >= my.getMaxMembers()) {
                p.sendMessage(ColorUtil.c("&cДостигнут лимит мест в клане (" + my.getMaxMembers() + ")! Прокачайте клан: &f/clan upgrade"));
                return;
            }
            Player t = Bukkit.getPlayerExact(args[1]);
            if (t == null) t = Bukkit.getPlayer(args[1]);
            if (t == null || !t.isOnline()) { p.sendMessage(ColorUtil.c("&cИгрок не найден в сети!")); return; }
            if (store.getClanOf(t.getName()) != null) { p.sendMessage(ColorUtil.c("&cЭтот игрок уже состоит в клане!")); return; }

            store.inviteToClan(my.id, t.getName());
            p.sendMessage(ColorUtil.c("&#00E676✔ Приглашение в клан [" + my.tag + "] отправлено игроку &f" + t.getName() + "&#00E676!"));
            ColorUtil.sound(t, "entity.experience_orb.pickup", 1f, 1.25f);

            // КЛИКАБЕЛЬНЫЕ КНОПКИ [ ✔ ПРИНЯТЬ ] и [ ✖ ОТКЛОНИТЬ ] ПРЯМО В ЧАТЕ!
            t.sendMessage("");
            t.sendMessage(ColorUtil.c("&#B388FF&l╔════════════════════════════════════════════╗"));
            t.sendMessage(ColorUtil.c("&#B388FF&l║  🛡 &fИгрок &#FFD740" + p.getName() + " &fприглашает вас в клан " + my.color + "&l[" + my.tag + "]&f!"));

            TextComponent line = new TextComponent(ColorUtil.c("&#B388FF&l║  "));
            TextComponent acceptBtn = new TextComponent(ColorUtil.c("&#00E676&l[ ✔ ПРИНЯТЬ ]"));
            acceptBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/clan accept " + my.id));
            acceptBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(ColorUtil.c("&#00E676Нажмите, чтобы вступить в клан [" + my.tag + "]!")).create()));

            TextComponent sep = new TextComponent(ColorUtil.c("   &8│   "));

            TextComponent denyBtn = new TextComponent(ColorUtil.c("&#FF1744&l[ ✖ ОТКЛОНИТЬ ]"));
            denyBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/clan deny " + my.id));
            denyBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(ColorUtil.c("&#FF5252Нажмите, чтобы отклонить приглашение")).create()));

            line.addExtra(acceptBtn);
            line.addExtra(sep);
            line.addExtra(denyBtn);
            t.spigot().sendMessage(line);

            t.sendMessage(ColorUtil.c("&#B388FF&l╚════════════════════════════════════════════╝"));
            t.sendMessage("");
            return;
        }

        if ("accept".equals(sub) || "join".equals(sub)) {
            if (my != null) { p.sendMessage(ColorUtil.c("&cВы уже состоите в клане! Сначала выйдите: /clan leave")); return; }
            String cid = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : store.getLatestClanInvite(p.getName());
            if (cid == null || !store.hasClanInvite(p.getName(), cid)) {
                p.sendMessage(ColorUtil.c("&cУ вас нет активного приглашения в этот клан (срок действия 5 минут)!"));
                return;
            }
            DataStore.Clan c = store.getClanById(cid);
            if (c == null) { p.sendMessage(ColorUtil.c("&cКлан больше не существует.")); return; }
            if (c.members.size() >= c.getMaxMembers()) { p.sendMessage(ColorUtil.c("&cВ клане нет свободных мест!")); return; }
            store.joinClan(p.getName(), c);
            hud.updatePlayerHud(p);
            ColorUtil.sound(p, "entity.player.levelup", 1f, 1.25f);
            broadcastToClan(c, ColorUtil.c(c.color + "&l[КЛАН " + c.tag + "] &#00E676➕ Игрок &f" + p.getName() + " &#00E676принял приглашение и вступил в клан!"));
            return;
        }

        if ("deny".equals(sub) || "decline".equals(sub) || "reject".equals(sub)) {
            String cid = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : store.getLatestClanInvite(p.getName());
            if (cid == null || !store.denyClanInvite(p.getName(), cid)) {
                p.sendMessage(ColorUtil.c("&cУ вас нет активных приглашений в клан."));
                return;
            }
            DataStore.Clan c = store.getClanById(cid);
            p.sendMessage(ColorUtil.c("&#FF5252✖ Вы отклонили приглашение в клан" + (c != null ? " [" + c.tag + "]" : "") + "."));
            if (c != null) {
                broadcastToClan(c, ColorUtil.c(c.color + "[КЛАН " + c.tag + "] &7Игрок &f" + p.getName() + " &cотклонил приглашение в клан."));
            }
            return;
        }

        if ("top".equals(sub)) {
            List<DataStore.Clan> list = new ArrayList<DataStore.Clan>(store.getAllClans());
            java.util.Collections.sort(list, new java.util.Comparator<DataStore.Clan>() {
                @Override
                public int compare(DataStore.Clan a, DataStore.Clan b) {
                    if (b.kills != a.kills) return Integer.compare(b.kills, a.kills);
                    return Double.compare(b.bank, a.bank);
                }
            });
            p.sendMessage(ColorUtil.c("&#FFD740&l🏆 ТОП-10 КЛАНОВ СЕРВЕРА:"));
            if (list.isEmpty()) { p.sendMessage(ColorUtil.c(" &7Пока не создано ни одного клана (/clan create <тег>).")); return; }
            for (int i = 0; i < Math.min(10, list.size()); i++) {
                DataStore.Clan c = list.get(i);
                p.sendMessage(ColorUtil.c(" &#FFD740#" + (i + 1) + " " + c.color + "&l[" + c.tag + "] &7(Ур." + c.level + ") &8— &c"
                        + c.kills + " ⚔ &8│ &a" + ColorUtil.fmtMoney(c.bank) + "$ &8│ &fЛидер: " + c.leader));
            }
            return;
        }

        if ("info".equals(sub) || "list".equals(sub) || "roster".equals(sub)) {
            DataStore.Clan targetClan = args.length >= 2 ? store.getClanById(args[1].toLowerCase(Locale.ROOT)) : my;
            if (targetClan == null) { p.sendMessage(ColorUtil.c("&cКлан не найден! Введите &f/clan info <тег>")); return; }
            StringBuilder roster = new StringBuilder();
            for (String mem : targetClan.members) {
                boolean on = Bukkit.getPlayerExact(mem) != null;
                if (roster.length() > 0) roster.append("&7, ");
                roster.append(on ? "&a● " : "&7○ ").append("&f").append(mem);
            }
            p.sendMessage(ColorUtil.c(targetClan.color + "&l🛡 ИНФОРМАЦИЯ О КЛАНЕ [" + targetClan.tag + "] (Уровень " + targetClan.level + "/10):"));
            p.sendMessage(ColorUtil.c(" &7• Лидер: &#FFD740" + targetClan.leader + " &8│ &7Казна: &a" + ColorUtil.fmtMoney(targetClan.bank) + "$ &8│ &7Убийств: &c" + targetClan.kills));
            p.sendMessage(ColorUtil.c(" &7• Огонь по своим: " + (targetClan.friendlyFire ? "&cВКЛ" : "&aВЫКЛ") + " &8│ &7Союзы: &b" + (targetClan.allies.isEmpty() ? "нет" : targetClan.allies)));
            p.sendMessage(ColorUtil.c(" &7• Участники (&e" + targetClan.members.size() + "/" + targetClan.getMaxMembers() + "&7): " + roster.toString()));
            return;
        }

        // Остальные команды требуют членства в клане
        if (my == null) {
            p.sendMessage(ColorUtil.c("&cВы не состоите в клане! Создайте свой: &f/clan create <тег> &cили посмотрите: &f/clan help"));
            return;
        }

        if ("chat".equals(sub) || "c".equals(sub)) {
            sendClanChat(p, args, 1);
            return;
        }
        if ("leave".equals(sub)) {
            if (myRole >= 4) {
                p.sendMessage(ColorUtil.c("&cВы Лидер клана! Передайте лидерство (&f/clan leader <ник>&c) или распустите клан (&f/clan disband&c)."));
                return;
            }
            store.leaveClan(p.getName(), my);
            hud.updatePlayerHud(p);
            p.sendMessage(ColorUtil.c("&eВы покинули клан [" + my.tag + "]."));
            broadcastToClan(my, ColorUtil.c(my.color + "[КЛАН " + my.tag + "] &e" + p.getName() + " покинул клан."));
            return;
        }
        if ("disband".equals(sub)) {
            if (myRole < 4) { p.sendMessage(ColorUtil.c("&cРаспустить клан может только Лидер!")); return; }
            double refund = my.bank * 0.5;
            if (refund > 0) store.addCoins(p.getName(), refund);
            String oldTag = my.tag;
            store.disbandClan(my);
            for (Player on : Bukkit.getOnlinePlayers()) hud.updatePlayerHud(on);
            Bukkit.broadcastMessage(ColorUtil.c("&#FF5252🛡 Клан [" + oldTag + "] был распущен лидером &f" + p.getName() + "&#FF5252."));
            return;
        }
        if ("kick".equals(sub)) {
            if (myRole < 2 || args.length < 2) { p.sendMessage(ColorUtil.c("&eИспользование (Модератор+): &f/clan kick <ник>")); return; }
            String target = args[1].toLowerCase(Locale.ROOT);
            if (!my.members.contains(target)) { p.sendMessage(ColorUtil.c("&cИгрок не состоит в вашем клане!")); return; }
            if (my.getRole(target) >= myRole) { p.sendMessage(ColorUtil.c("&cНельзя выгнать равного или старшего по званию в клане!")); return; }
            store.leaveClan(target, my);
            Player tp = Bukkit.getPlayerExact(target);
            if (tp != null) {
                hud.updatePlayerHud(tp);
                tp.sendMessage(ColorUtil.c("&#FF1744✖ Вы были исключены из клана [" + my.tag + "] игроком " + p.getName() + "!"));
            }
            broadcastToClan(my, ColorUtil.c(my.color + "[КЛАН " + my.tag + "] &cИгрок &f" + args[1] + " &cисключён из клана."));
            return;
        }
        if ("promote".equals(sub) || "moder".equals(sub)) {
            if (myRole < 4 || args.length < 2) { p.sendMessage(ColorUtil.c("&eИспользование (Лидер): &f/clan promote <ник>")); return; }
            String target = args[1].toLowerCase(Locale.ROOT);
            if (!my.members.contains(target) || my.leader.equalsIgnoreCase(target)) { p.sendMessage(ColorUtil.c("&cУкажите участника клана!")); return; }
            int next = Math.min(3, my.getRole(target) + 1);
            my.roles.put(target, Integer.valueOf(next));
            store.save();
            broadcastToClan(my, ColorUtil.c(my.color + "[КЛАН " + my.tag + "] &#00E676⬆ &f" + args[1] + " &#00E676повышен до " + my.getRoleName(target) + "&#00E676!"));
            return;
        }
        if ("demote".equals(sub)) {
            if (myRole < 4 || args.length < 2) { p.sendMessage(ColorUtil.c("&eИспользование (Лидер): &f/clan demote <ник>")); return; }
            String target = args[1].toLowerCase(Locale.ROOT);
            if (!my.members.contains(target) || my.leader.equalsIgnoreCase(target)) { p.sendMessage(ColorUtil.c("&cУкажите участника клана!")); return; }
            int next = Math.max(0, my.getRole(target) - 1);
            my.roles.put(target, Integer.valueOf(next));
            store.save();
            broadcastToClan(my, ColorUtil.c(my.color + "[КЛАН " + my.tag + "] &e⬇ &f" + args[1] + " &eпонижен до " + my.getRoleName(target) + "&e."));
            return;
        }
        if ("leader".equals(sub)) {
            if (myRole < 4 || args.length < 2) { p.sendMessage(ColorUtil.c("&eИспользование (Лидер): &f/clan leader <ник>")); return; }
            String target = args[1].toLowerCase(Locale.ROOT);
            if (!my.members.contains(target)) { p.sendMessage(ColorUtil.c("&cЭтот игрок не состоит в вашем клане!")); return; }
            my.roles.put(my.leader.toLowerCase(Locale.ROOT), Integer.valueOf(3));
            my.leader = args[1];
            my.roles.put(target, Integer.valueOf(4));
            store.save();
            broadcastToClan(my, ColorUtil.c(my.color + "&l[КЛАН " + my.tag + "] &#FFD740👑 Новым Лидером клана назначен &f" + args[1] + "&#FFD740!"));
            return;
        }
        if ("sethome".equals(sub)) {
            if (myRole < 3) { p.sendMessage(ColorUtil.c("&cУстановить базу клана могут только Лидер и Зам.Лидера!")); return; }
            if (spawnEngine.isInSpawnWorld(p.getLocation())) { p.sendMessage(ColorUtil.c("&cБазу клана можно ставить только в мире выживания (/rtp)!")); return; }
            DataStore.Claim cl = store.getClaimAt(p.getLocation());
            if (cl != null && !cl.canAccess(p.getName())) { p.sendMessage(ColorUtil.c("&cНельзя ставить базу клана в чужом привате!")); return; }
            my.home = p.getLocation();
            store.save();
            broadcastToClan(my, ColorUtil.c(my.color + "[КЛАН " + my.tag + "] &#00E676🏠 База клана обновлена! Телепорт: &f/clan home"));
            return;
        }
        if ("delhome".equals(sub)) {
            if (myRole < 3) { p.sendMessage(ColorUtil.c("&cДоступно только Лидеру и Зам.Лидера!")); return; }
            my.home = null;
            store.save();
            p.sendMessage(ColorUtil.c("&eТочка базы клана удалена."));
            return;
        }
        if ("home".equals(sub)) {
            if (my.home == null) { p.sendMessage(ColorUtil.c("&cБаза клана ещё не установлена (&f/clan sethome&c)!")); return; }
            store.setBack(p, p.getLocation());
            p.teleport(my.home);
            ColorUtil.sound(p, "entity.enderman.teleport", 1f, 1.15f);
            p.sendMessage(ColorUtil.c(my.color + "🛡 Вы телепортировались на базу клана [" + my.tag + "]!"));
            return;
        }
        if ("pvp".equals(sub) || "ff".equals(sub)) {
            if (myRole < 2) { p.sendMessage(ColorUtil.c("&cПереключать клановый PvP могут Модератор+!")); return; }
            my.friendlyFire = !my.friendlyFire;
            store.save();
            broadcastToClan(my, ColorUtil.c(my.color + "[КЛАН " + my.tag + "] &fОгонь по своим теперь: " + (my.friendlyFire ? "&cВКЛЮЧЁН ⚔" : "&aВЫКЛЮЧЕН 🛡")));
            return;
        }
        if ("money".equals(sub) || "balance".equals(sub) || "bank".equals(sub)) {
            p.sendMessage(ColorUtil.c(my.color + "💰 Казна клана [" + my.tag + "]: &a" + ColorUtil.fmtMoney(my.bank) + "$"));
            return;
        }
        if ("deposit".equals(sub) || "invest".equals(sub) || "put".equals(sub)) {
            if (args.length < 2) { p.sendMessage(ColorUtil.c("&eИспользование: &f/clan deposit <сумма>")); return; }
            double amt;
            try { amt = Double.parseDouble(args[1]); } catch (Exception ex) { amt = -1; }
            if (!Double.isFinite(amt) || amt < 100 || !Double.isFinite(my.bank) || my.bank < 0
                    || !Double.isFinite(my.bank + amt) || my.bank + amt <= my.bank) {
                p.sendMessage(ColorUtil.c("&cНекорректная сумма или баланс казны (минимум 100$).")); return;
            }
            if (!store.takeCoins(p.getName(), amt)) { p.sendMessage(ColorUtil.c("&cНедостаточно монет на балансе!")); return; }
            my.bank += amt;
            store.save();
            broadcastToClan(my, ColorUtil.c(my.color + "[КЛАН " + my.tag + "] &#00E676💰 &f" + p.getName() + " &#00E676внёс в казну &a+" + ColorUtil.fmtMoney(amt) + "$ &7(Всего: &e" + ColorUtil.fmtMoney(my.bank) + "$&7)"));
            return;
        }
        if ("withdraw".equals(sub) || "take".equals(sub)) {
            if (myRole < 3 || args.length < 2) { p.sendMessage(ColorUtil.c("&eСнимать из казны могут Лидер/Зам: &f/clan withdraw <сумма>")); return; }
            double amt;
            try { amt = Double.parseDouble(args[1]); } catch (Exception ex) { amt = -1; }
            double personal = store.getCoins(p.getName());
            if (!Double.isFinite(amt) || amt <= 0 || !Double.isFinite(my.bank) || my.bank < amt
                    || !Double.isFinite(personal) || personal < 0 || !Double.isFinite(personal + amt)
                    || personal + amt <= personal || my.bank - amt >= my.bank) {
                p.sendMessage(ColorUtil.c("&cНедостаточно средств или некорректная сумма!")); return;
            }
            my.bank -= amt;
            store.addCoins(p.getName(), amt);
            store.save();
            broadcastToClan(my, ColorUtil.c(my.color + "[КЛАН " + my.tag + "] &e💸 &f" + p.getName() + " &eснял из казны &f" + ColorUtil.fmtMoney(amt) + "$"));
            return;
        }
        if ("storage".equals(sub) || "chest".equals(sub) || "ec".equals(sub)) {
            guis.openClanStorage(p, my);
            return;
        }
        if ("upgrade".equals(sub) || "up".equals(sub) || "levelup".equals(sub)) {
            if (myRole < 3) { p.sendMessage(ColorUtil.c("&cПрокачивать клан могут Лидер и Зам.Лидера!")); return; }
            if (my.level >= 10) { p.sendMessage(ColorUtil.c("&#00E676✔ Ваш клан уже достиг максимального 10 уровня!")); return; }
            double cost = my.level * 50000.0;
            if (my.bank < cost) {
                p.sendMessage(ColorUtil.c("&cВ казне не хватает монет! Нужно: &e" + ColorUtil.fmtMoney(cost) + "$ &7(пополнить: &f/clan deposit <сумма>&7)"));
                return;
            }
            my.bank -= cost;
            my.level++;
            my.liveStorage = null; // Resize storage on next open if threshold crossed!
            store.save();
            ColorUtil.sound(p, "ui.toast.challenge_complete", 1f, 1.25f);
            broadcastToClan(my, ColorUtil.c(my.color + "&l[КЛАН " + my.tag + "] &#FFD740🎉 Клан повышен до &f" + my.level
                    + " УРОВНЯ&#FFD740! Мест: &a" + my.getMaxMembers() + " &8│ &7Сейф: &b" + my.getStorageSize() + " слотов!"));
            return;
        }
        if ("ally".equals(sub)) {
            if (myRole < 3 || args.length < 2) { p.sendMessage(ColorUtil.c("&eИспользование (Лидер/Зам): &f/clan ally <тегКлана>")); return; }
            DataStore.Clan other = store.getClanById(args[1].toLowerCase(Locale.ROOT));
            if (other == null || other.id.equals(my.id)) { p.sendMessage(ColorUtil.c("&cКлан не найден!")); return; }
            my.allies.add(other.id);
            other.allies.add(my.id);
            store.save();
            broadcastToClan(my, ColorUtil.c(my.color + "[КЛАН] &#00E676🤝 Заключён союз с кланом [" + other.tag + "]! Огонь по союзникам отключён."));
            broadcastToClan(other, ColorUtil.c(other.color + "[КЛАН] &#00E676🤝 Клан [" + my.tag + "] заключил с вами союз!"));
            return;
        }
        if ("neutral".equals(sub) || "unally".equals(sub)) {
            if (myRole < 3 || args.length < 2) { p.sendMessage(ColorUtil.c("&eИспользование: &f/clan neutral <тегКлана>")); return; }
            DataStore.Clan other = store.getClanById(args[1].toLowerCase(Locale.ROOT));
            if (other == null) { p.sendMessage(ColorUtil.c("&cКлан не найден!")); return; }
            my.allies.remove(other.id);
            other.allies.remove(my.id);
            store.save();
            broadcastToClan(my, ColorUtil.c("&eСоюз с кланом [" + other.tag + "] расторгнут."));
            return;
        }
        if ("color".equals(sub)) {
            if (myRole < 4 || args.length < 2) { p.sendMessage(ColorUtil.c("&eИспользование (Лидер): &f/clan color <red|gold|aqua|green|purple|pink|white>")); return; }
            String arg = args[1].toLowerCase(Locale.ROOT);
            String hex = "purple".equals(arg) ? "&#B388FF" : "red".equals(arg) ? "&#FF5252"
                    : "gold".equals(arg) ? "&#FFD740" : "aqua".equals(arg) ? "&#00E5FF"
                    : "green".equals(arg) ? "&#00E676" : "pink".equals(arg) ? "&#FF4081"
                    : "white".equals(arg) ? "&#F8FAFC" : "&#B388FF";
            my.color = hex;
            store.save();
            for (Player on : Bukkit.getOnlinePlayers()) if (my.members.contains(on.getName().toLowerCase(Locale.ROOT))) hud.updatePlayerHud(on);
            broadcastToClan(my, ColorUtil.c(my.color + "🎨 Цвет тега клана [" + my.tag + "] успешно обновлён!"));
            return;
        }
        if ("rename".equals(sub)) {
            if (myRole < 4 || args.length < 2) { p.sendMessage(ColorUtil.c("&eИспользование (Лидер, 15 000$ из казны): &f/clan rename <новыйТег>")); return; }
            String newTag = args[1].replaceAll("[^A-Za-zА-Яа-яЁё0-9_]", "");
            if (newTag.length() < 3 || newTag.length() > 10) { p.sendMessage(ColorUtil.c("&cТег должен быть 3–10 символов!")); return; }
            if (my.bank < 15000.0) { p.sendMessage(ColorUtil.c("&cВ казне нужно 15 000$ для смены тега!")); return; }
            my.bank -= 15000.0;
            my.tag = newTag;
            store.save();
            for (Player on : Bukkit.getOnlinePlayers()) if (my.members.contains(on.getName().toLowerCase(Locale.ROOT))) hud.updatePlayerHud(on);
            broadcastToClan(my, ColorUtil.c(my.color + "✨ Тег клана изменён на [" + my.tag + "]!"));
            return;
        }
        if ("coords".equals(sub)) {
            p.sendMessage(ColorUtil.c(my.color + "&l📍 КООРДИНАТЫ СОКЛАНОВЦЕВ ОНЛАЙН:"));
            for (Player on : Bukkit.getOnlinePlayers()) {
                if (my.members.contains(on.getName().toLowerCase(Locale.ROOT))) {
                    Location l = on.getLocation();
                    p.sendMessage(ColorUtil.c(" &a● &f" + on.getName() + " &7(" + (int) on.getHealth() + "❤) &8— &b"
                            + l.getWorld().getName() + " &fX:" + l.getBlockX() + " Y:" + l.getBlockY() + " Z:" + l.getBlockZ()));
                }
            }
            return;
        }
        if ("glow".equals(sub)) {
            for (Player on : Bukkit.getOnlinePlayers()) {
                if (my.members.contains(on.getName().toLowerCase(Locale.ROOT))) {
                    ColorUtil.addPot(on, "glowing", "GLOWING", 300, 0);
                }
            }
            broadcastToClan(my, ColorUtil.c(my.color + "[КЛАН " + my.tag + "] ✨ Подсветка соклановцев активирована на 15 секунд!"));
            return;
        }

        sendClanHelp(p);
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
            Player mt = Bukkit.getPlayerExact(target);
            if (mt != null && mt.isOnline() && moderation != null) {
                moderation.playSantaTapeMuteAnimation(mt, "30 мин.", reason);
            }
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
            if (args.length == 2 && "cancel".equalsIgnoreCase(args[1])) {
                PreparedSpawnInstaller.cancelRebuild(this, s);
                return true;
            }
            if (args.length != 2 || !"confirm".equalsIgnoreCase(args[1])) {
                s.sendMessage(ColorUtil.c("&eМир заменится после полного перезапуска; сделайте внешний бэкап. &f/kgadmin rebuildspawn confirm &7(отмена: cancel)"));
                return true;
            }
            PreparedSpawnInstaller.requestRebuild(this, s);
            return true;
        }
        if ("startboss".equals(sub)) {
            String bid = args.length >= 2 ? args[1] : "grinch";
            bossEvents.requestBossEvent(bid, s);
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
            double amount = Double.parseDouble(args[2]);
            if (!Double.isFinite(amount)) { s.sendMessage(ColorUtil.c("&cУкажите конечную сумму.")); return true; }
            store.addCoins(args[1], amount);
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

    // ================= НОВАЯ СИСТЕМА ВАРПОВ (/warp set, /setwarp, /delwarp, /warp info) И 20+ ДОП. КОМАНД v5.9.2 =================

    private static final List<String> RESERVED_SYSTEM_WARPS = Arrays.asList(
            "set", "create", "add", "del", "delete", "remove", "rem", "list", "info",
            "mine", "шахта", "farm", "фарм", "casino", "казино", "wheel", "колесо",
            "enchant", "enchants", "bafs", "чары", "pvp", "пвп", "arena",
            "train", "totem", "dummy", "тренировка", "kabinki", "duels", "кабинки",
            "parkour", "паркур", "cases", "кейсы", "top", "топ", "spawn", "спавн"
    );

    private boolean handleWarpSet(Player p, String rawName) {
        RankManager.RankDef r = ranks.getRank(p);
        boolean isOwnerOrAdmin = "owner".equalsIgnoreCase(r.id) || r.weight >= 250;
        if (!isOwnerOrAdmin && r.weight < 12) {
            p.sendMessage(ColorUtil.c("&#FF1744✖ Создание варпов (/warp set) доступно с бесплатной привилегии &#00E676DRAGON &8(/free dragon)&#FF1744!"));
            return true;
        }
        if (rawName == null || !rawName.matches("^[A-Za-zА-Яа-я0-9_-]{2,18}$")) {
            p.sendMessage(ColorUtil.c("&#FFD740Использование: &f/warp set <название> &7или &f/setwarp <название> &8(2–18 букв/цифр)"));
            return true;
        }
        String wName = rawName.toLowerCase(Locale.ROOT);
        if (RESERVED_SYSTEM_WARPS.contains(wName)) {
            p.sendMessage(ColorUtil.c("&#FF1744✖ Имя &f" + wName + " &#FF1744зарезервировано системным варпом!"));
            return true;
        }
        if (!isOwnerOrAdmin && spawnEngine.isInSpawnWorld(p.getLocation())) {
            p.sendMessage(ColorUtil.c("&#FF1744✖ Игрокам нельзя создавать варпы на территории спавна! Перейдите в мир выживания: &f/rtp"));
            return true;
        }
        DataStore.Claim claim = store.getClaimAt(p.getLocation());
        if (!isOwnerOrAdmin && claim != null && !claim.canAccess(p.getName())) {
            p.sendMessage(ColorUtil.c("&#FF1744✖ Нельзя ставить варп в чужом привате!"));
            return true;
        }
        DataStore.CustomWarp existing = store.getCustomWarp(wName);
        if (existing != null && !isOwnerOrAdmin && !existing.creator.equalsIgnoreCase(p.getName())) {
            p.sendMessage(ColorUtil.c("&#FF1744✖ Варп &f" + wName + " &#FF1744уже занят игроком &e" + existing.creator + "&#FF1744!"));
            return true;
        }
        if (!isOwnerOrAdmin && existing == null) {
            int maxWarps = r.weight >= 112 ? 5 : (r.weight >= 75 ? 3 : (r.weight >= 45 ? 2 : 1));
            int myCount = store.countCustomWarpsOf(p.getName());
            if (myCount >= maxWarps) {
                p.sendMessage(ColorUtil.c("&#FF1744✖ Достигнут лимит варпов вашей привилегии (&f" + myCount + "/" + maxWarps + "&#FF1744)! Удалите старый: &f/delwarp <имя>"));
                return true;
            }
        }
        store.setCustomWarp(wName, p.getName(), p.getLocation());
        ColorUtil.sound(p, "entity.player.levelup", 1.0f, 1.25f);
        p.sendMessage(ColorUtil.c("&#00E676🧭 Варп &f/warp " + wName + " &#00E676успешно установлен! Удалить: &f/delwarp " + wName));
        return true;
    }

    private boolean handleWarpDel(Player p, String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) {
            p.sendMessage(ColorUtil.c("&#FFD740Использование: &f/warp del <название> &7или &f/delwarp <название>"));
            return true;
        }
        String wName = rawName.trim().toLowerCase(Locale.ROOT);
        DataStore.CustomWarp cw = store.getCustomWarp(wName);
        if (cw == null) {
            p.sendMessage(ColorUtil.c("&#FF1744✖ Кастомный варп &f" + wName + " &#FF1744не найден!"));
            return true;
        }
        RankManager.RankDef r = ranks.getRank(p);
        boolean isOwnerOrAdmin = "owner".equalsIgnoreCase(r.id) || r.weight >= 250;
        if (!isOwnerOrAdmin && !cw.creator.equalsIgnoreCase(p.getName())) {
            p.sendMessage(ColorUtil.c("&#FF1744✖ Вы не являетесь владельцем варпа &f" + wName + " &#FF1744(создатель: &e" + cw.creator + "&#FF1744)!"));
            return true;
        }
        store.delCustomWarp(wName);
        ColorUtil.sound(p, "block.anvil.break", 0.8f, 1.2f);
        p.sendMessage(ColorUtil.c("&#00E676✔ Варп &f" + wName + " &#00E676успешно удалён!"));
        return true;
    }

    private boolean handleWarpInfo(Player p, String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) {
            p.sendMessage(ColorUtil.c("&eИспользование: &f/warp info <название>"));
            return true;
        }
        String wName = rawName.trim().toLowerCase(Locale.ROOT);
        DataStore.CustomWarp cw = store.getCustomWarp(wName);
        if (cw != null && cw.loc != null) {
            p.sendMessage(ColorUtil.c("&#00E5FF&l🧭 ИНФОРМАЦИЯ О ВАРПЕ &f" + cw.name + "&#00E5FF:"));
            p.sendMessage(ColorUtil.c(" &7• Создатель: &#FFD740" + cw.creator));
            p.sendMessage(ColorUtil.c(" &7• Мир: &f" + (cw.loc.getWorld() != null ? cw.loc.getWorld().getName() : "world")
                    + " &8│ &7Координаты: &b" + cw.loc.getBlockX() + ", " + cw.loc.getBlockY() + ", " + cw.loc.getBlockZ()));
            return true;
        }
        Location sys = spawnEngine.getWarpLocation(wName);
        if (sys != null) {
            p.sendMessage(ColorUtil.c("&#00E5FF&l🧭 СИСТЕМНЫЙ ВАРП &f" + wName + " &8— &7официальная локация Спавна KINGGRIEF!"));
            return true;
        }
        p.sendMessage(ColorUtil.c("&#FF1744✖ Варп &f" + wName + " &#FF1744не найден."));
        return true;
    }

    private boolean handlePrivateMessageCommand(Player sender, String cmdName, String[] args) {
        boolean isOwner = ranks.isOwnerLevel(sender);
        if (!isOwner && (store.getMuteRemainingSec(sender.getName()) > 0
                || store.getIpMuteRemainingSec(store.getPlayerIp(sender)) > 0)) {
            sender.sendMessage(ColorUtil.c("&#FF5252🔇 Вы находитесь в муте и не можете писать в ЛС!"));
            return true;
        }
        Player target;
        String msgText;
        if ("reply".equals(cmdName)) {
            if (args.length < 1) {
                sender.sendMessage(ColorUtil.c("&eИспользование: &f/r <сообщение>"));
                return true;
            }
            UUID partnerId = store.getLastReplyPartner(sender.getUniqueId());
            target = partnerId == null ? null : Bukkit.getPlayer(partnerId);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(ColorUtil.c("&#FF5252✖ У вас нет недавнего собеседника онлайн!"));
                return true;
            }
            msgText = String.join(" ", args);
        } else {
            if (args.length < 2) {
                sender.sendMessage(ColorUtil.c("&eИспользование: &f/msg <ник> <сообщение>"));
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) target = Bukkit.getPlayer(args[0]);
            if (target == null || !target.isOnline() || (store.isVanished(target) && !ranks.isMlModerOrHigher(sender))) {
                sender.sendMessage(ColorUtil.c("&#FF5252✖ Игрок &f" + args[0] + " &#FF5252не в сети!"));
                return true;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                if (i > 1) sb.append(' ');
                sb.append(args[i]);
            }
            msgText = sb.toString();
        }
        if (target.equals(sender)) {
            sender.sendMessage(ColorUtil.c("&eНельзя писать самому себе!"));
            return true;
        }
        if (!isOwner && store.isMsgDisabled(target.getName())) {
            sender.sendMessage(ColorUtil.c("&#FF5252✖ Игрок &f" + target.getName() + " &#FF5252отключил личные сообщения (/msgtoggle)!"));
            return true;
        }
        if (!isOwner && store.isIgnoring(target.getName(), sender.getName())) {
            sender.sendMessage(ColorUtil.c("&#FF5252✖ Этот игрок ограничил сообщения от вас!"));
            return true;
        }

        store.setLastReplyPartner(sender.getUniqueId(), target.getUniqueId());
        sender.sendMessage(ColorUtil.c("&#FF80AB✉ &8[&fЯ &7→ &#FFD740" + target.getName() + "&8] &f") + msgText);
        target.sendMessage(ColorUtil.c("&#FF80AB✉ &8[&#FFD740" + sender.getName() + " &7→ &fМне&8] &f") + msgText);
        ColorUtil.sound(target, "entity.experience_orb.pickup", 0.75f, 1.45f);

        for (Player st : Bukkit.getOnlinePlayers()) {
            if (!st.equals(sender) && !st.equals(target) && store.isSocialSpy(st)) {
                st.sendMessage(ColorUtil.c("&8[👁 Spy] &7" + sender.getName() + " -> " + target.getName() + ": &f") + msgText);
            }
        }
        return true;
    }

    private boolean handleExtraPowerCommands(Player p, String c, String[] args) {
        RankManager.RankDef r = ranks.getRank(p);
        boolean isOwner = ranks.isOwnerLevel(r.id);

        if ("suicide".equals(c)) {
            p.setHealth(0.0);
            p.sendMessage(ColorUtil.c("&#FF5252☠ Вы совершили самоубийство."));
            return true;
        }
        if ("xp".equals(c)) {
            if (args.length >= 3 && "give".equalsIgnoreCase(args[0]) && isOwner) {
                Player t = Bukkit.getPlayer(args[1]);
                if (t == null) { p.sendMessage(ColorUtil.c("&cИгрок не найден!")); return true; }
                int lvls;
                try { lvls = Integer.parseInt(args[2]); } catch (Exception ex) { lvls = 10; }
                t.giveExpLevels(lvls);
                p.sendMessage(ColorUtil.c("&#00E676✔ Выдано &e+" + lvls + " LVL &aигроку &f" + t.getName()));
                return true;
            }
            p.sendMessage(ColorUtil.c("&#69F0AE✨ Ваш опыт: &f" + p.getLevel() + " LVL &7(Всего очков: &e" + p.getTotalExperience() + "&7)"));
            return true;
        }
        if ("day".equals(c) || "night".equals(c) || "ptime".equals(c)) {
            if (!isOwner && r.weight < 12) {
                p.sendMessage(ColorUtil.c("&cДоступно с привилегии DRAGON (/free dragon)!"));
                return true;
            }
            String mode = "ptime".equals(c) ? (args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "day") : c;
            if ("reset".equals(mode)) {
                p.resetPlayerTime();
                p.sendMessage(ColorUtil.c("&#80D8FF🕒 Личное время синхронизировано с сервером!"));
            } else if ("night".equals(mode)) {
                p.setPlayerTime(18000L, false);
                p.sendMessage(ColorUtil.c("&#A855F7🌙 Установлено личное время: &fНочь"));
            } else {
                p.setPlayerTime(6000L, false);
                p.sendMessage(ColorUtil.c("&#FFD740☀ Установлено личное время: &fДень"));
            }
            return true;
        }
        if ("sun".equals(c)) {
            if (!isOwner && r.weight < 12) {
                p.sendMessage(ColorUtil.c("&cДоступно с привилегии DRAGON (/free dragon)!"));
                return true;
            }
            p.setPlayerWeather(WeatherType.CLEAR);
            p.sendMessage(ColorUtil.c("&#FFD740☀ Установлена ясная солнечная погода!"));
            return true;
        }
        if ("rename".equals(c)) {
            if (!isOwner && r.weight < 58) {
                p.sendMessage(ColorUtil.c("&cКоманда /rename доступна с привилегии TIGER!"));
                return true;
            }
            if (args.length < 1) {
                p.sendMessage(ColorUtil.c("&eИспользование: &f/rename <новое имя с цветами & / &#HEX>"));
                return true;
            }
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() == Material.AIR || "menu_star".equals(ItemFactory.getCustomId(hand))) {
                p.sendMessage(ColorUtil.c("&cВозьмите обычный предмет в главную руку!"));
                return true;
            }
            ItemMeta im = hand.getItemMeta();
            if (im != null) {
                im.setDisplayName(ColorUtil.c(String.join(" ", args)));
                hand.setItemMeta(im);
                p.sendMessage(ColorUtil.c("&#00E676✨ Имя предмета успешно изменено!"));
            }
            return true;
        }
        if ("relore".equals(c)) {
            if (!isOwner && r.weight < 75) {
                p.sendMessage(ColorUtil.c("&cКоманда /relore доступна с привилегии IMPERATOR!"));
                return true;
            }
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() == Material.AIR || "menu_star".equals(ItemFactory.getCustomId(hand))) {
                p.sendMessage(ColorUtil.c("&cВозьмите предмет в руку!"));
                return true;
            }
            ItemMeta im = hand.getItemMeta();
            if (im != null) {
                if (args.length == 0 || "clear".equalsIgnoreCase(args[0])) {
                    im.setLore(new ArrayList<String>());
                    p.sendMessage(ColorUtil.c("&#00E676✔ Описание предмета очищено!"));
                } else {
                    List<String> lore = im.hasLore() && im.getLore() != null ? new ArrayList<String>(im.getLore()) : new ArrayList<String>();
                    lore.add(ColorUtil.c(String.join(" ", args)));
                    im.setLore(lore);
                    p.sendMessage(ColorUtil.c("&#00E676✔ Строка добавлена в описание предмета!"));
                }
                hand.setItemMeta(im);
            }
            return true;
        }
        if ("skull".equals(c)) {
            if (!isOwner && r.weight < 85) {
                p.sendMessage(ColorUtil.c("&cКоманда /skull доступна с привилегии KING!"));
                return true;
            }
            String targetNick = args.length >= 1 ? args[0] : p.getName();
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD, 1);
            SkullMeta sm = (SkullMeta) skull.getItemMeta();
            if (sm != null) {
                try { sm.setOwner(targetNick); } catch (Throwable ignored) {}
                sm.setDisplayName(ColorUtil.c("&#FFD740👑 Голова игрока &f" + targetNick));
                skull.setItemMeta(sm);
            }
            p.getInventory().addItem(skull);
            p.sendMessage(ColorUtil.c("&#00E676👑 Получена голова игрока &f" + targetNick + "&#00E676!"));
            return true;
        }
        if ("topblock".equals(c)) {
            if (!isOwner && r.weight < 75) {
                p.sendMessage(ColorUtil.c("&cКоманда /topblock доступна с привилегии IMPERATOR!"));
                return true;
            }
            Block highest = p.getWorld().getHighestBlockAt(p.getLocation());
            Location dest = highest.getLocation().add(0.5, 1.0, 0.5);
            dest.setYaw(p.getLocation().getYaw());
            dest.setPitch(p.getLocation().getPitch());
            store.setBack(p, p.getLocation());
            p.teleport(dest);
            p.sendMessage(ColorUtil.c("&#00E676⬆ Вы поднялись на самую высокую точку!"));
            return true;
        }
        if ("jump".equals(c)) {
            if (!isOwner && r.weight < 92) {
                p.sendMessage(ColorUtil.c("&cКоманда /jump доступна с привилегии SPONSOR!"));
                return true;
            }
            RayTraceResult rt = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), 80.0, FluidCollisionMode.NEVER, true);
            if (rt == null || rt.getHitBlock() == null) {
                p.sendMessage(ColorUtil.c("&cБлок в прицеле (до 80м) не найден!"));
                return true;
            }
            Location dest = rt.getHitBlock().getLocation().add(0.5, 1.05, 0.5);
            dest.setYaw(p.getLocation().getYaw());
            dest.setPitch(p.getLocation().getPitch());
            store.setBack(p, p.getLocation());
            p.teleport(dest);
            ColorUtil.sound(p, "entity.enderman.teleport", 0.9f, 1.25f);
            return true;
        }
        if ("god".equals(c)) {
            if (!isOwner && r.weight < 150) {
                p.sendMessage(ColorUtil.c("&#FF1744✖ Режим /god доступен только старшему составу и OWNER!"));
                return true;
            }
            boolean on = store.toggleGodMode(p);
            p.sendMessage(ColorUtil.c(on
                    ? "&#00E676🛡 Режим бессмертия (/god) ВКЛЮЧЁН!"
                    : "&#FFD740🛡 Режим бессмертия (/god) ВЫКЛЮЧЕН."));
            return true;
        }
        if ("speed".equals(c)) {
            if (!isOwner && r.weight < 112) {
                p.sendMessage(ColorUtil.c("&#FF1744✖ Команда /speed доступна с привилегии D.HELPER и выше!"));
                return true;
            }
            if (args.length < 1) {
                p.sendMessage(ColorUtil.c("&eИспользование: &f/speed <1-10>"));
                return true;
            }
            float val;
            try { val = Float.parseFloat(args[0]); } catch (Exception ex) { val = 2.0f; }
            val = Math.max(0.5f, Math.min(10.0f, val));
            if (p.isFlying()) {
                p.setFlySpeed(Math.min(1.0f, val / 10.0f));
                p.sendMessage(ColorUtil.c("&#00E676🕊 Скорость полёта установлена на &f" + val));
            } else {
                p.setWalkSpeed(Math.min(1.0f, 0.1f + (val / 10.0f) * 0.9f));
                p.sendMessage(ColorUtil.c("&#00E676🏃 Скорость бега установлена на &f" + val));
            }
            return true;
        }
        if ("burn".equals(c)) {
            if (!ranks.isMlModerOrHigher(p)) {
                p.sendMessage(ColorUtil.c("&#FF1744✖ Доступно от Мл. Модера и выше!"));
                return true;
            }
            if (args.length < 1) { p.sendMessage(ColorUtil.c("&eИспользование: &f/burn <ник> [сек]")); return true; }
            Player t = Bukkit.getPlayer(args[0]);
            if (t == null) { p.sendMessage(ColorUtil.c("&cИгрок не найден!")); return true; }
            if (ranks.isOwnerLevel(t) && !isOwner) {
                p.sendMessage(ColorUtil.c("&#FF1744✖ Нельзя поджечь OWNER!"));
                return true;
            }
            int sec = 5;
            if (args.length >= 2) {
                try { sec = Math.max(1, Math.min(60, Integer.parseInt(args[1]))); } catch (Exception ignored) {}
            }
            t.setFireTicks(sec * 20);
            p.sendMessage(ColorUtil.c("&#FF6D00🔥 Игрок &f" + t.getName() + " &#FF6D00подожжён на &e" + sec + " сек.!"));
            return true;
        }
        if ("smite".equals(c)) {
            if (!isOwner && r.weight < 240) {
                p.sendMessage(ColorUtil.c("&#FF1744✖ Команда /smite доступна от Ст. Модера и OWNER!"));
                return true;
            }
            if (args.length >= 1) {
                Player t = Bukkit.getPlayer(args[0]);
                if (t == null) { p.sendMessage(ColorUtil.c("&cИгрок не найден!")); return true; }
                t.getWorld().strikeLightningEffect(t.getLocation());
                p.sendMessage(ColorUtil.c("&#FFD740⚡ Молния ударила в игрока &f" + t.getName() + "&#FFD740!"));
                return true;
            }
            RayTraceResult rt = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), 80.0, FluidCollisionMode.NEVER, true);
            if (rt != null && rt.getHitBlock() != null) {
                p.getWorld().strikeLightningEffect(rt.getHitBlock().getLocation().add(0.5, 1.0, 0.5));
            }
            return true;
        }
        if ("socialspy".equals(c)) {
            if (!isOwner && r.weight < 240) {
                p.sendMessage(ColorUtil.c("&#FF1744✖ Прослушка /socialspy доступна от Ст. Модера и OWNER!"));
                return true;
            }
            boolean on = store.toggleSocialSpy(p);
            p.sendMessage(ColorUtil.c(on ? "&#00E676👁 Режим SocialSpy ВКЛЮЧЁН!" : "&#FFD740👁 Режим SocialSpy ВЫКЛЮЧЕН."));
            return true;
        }
        if ("giveall".equals(c)) {
            if (!isOwner) {
                p.sendMessage(ColorUtil.c("&#FF1744✖ Команда /giveall доступна СТРОГО ТОЛЬКО владельцу (OWNER)!"));
                return true;
            }
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() == Material.AIR || "menu_star".equals(ItemFactory.getCustomId(hand))) {
                p.sendMessage(ColorUtil.c("&eВозьмите в руку предмет, который хотите раздать всем игрокам онлайн!"));
                return true;
            }
            ItemStack gift = hand.clone();
            int count = 0;
            for (Player on : Bukkit.getOnlinePlayers()) {
                for (ItemStack rem : on.getInventory().addItem(gift.clone()).values()) {
                    on.getWorld().dropItemNaturally(on.getLocation(), rem);
                }
                ColorUtil.sound(on, "entity.player.levelup", 1.0f, 1.3f);
                count++;
            }
            String label = (gift.hasItemMeta() && gift.getItemMeta().hasDisplayName())
                    ? gift.getItemMeta().getDisplayName()
                    : ("&e" + gift.getType().name());
            Bukkit.broadcastMessage(ColorUtil.c("&#FF1744&l🎁 [РАЗДАЧА ПРЕДМЕТА] &fВладелец &#FFD740&l" + p.getName()
                    + " &fвыдал всем онлайн (&a" + count + " игр.&f): " + label + " &7×" + gift.getAmount() + "!"));
            return true;
        }
        if ("sudo".equals(c)) {
            if (!isOwner) {
                p.sendMessage(ColorUtil.c("&#FF1744✖ Команда /sudo доступна СТРОГО ТОЛЬКО владельцу (OWNER)!"));
                return true;
            }
            if (args.length < 2) {
                p.sendMessage(ColorUtil.c("&eИспользование: &f/sudo <ник> <команда без / или c:сообщение>"));
                return true;
            }
            Player t = Bukkit.getPlayer(args[0]);
            if (t == null) { p.sendMessage(ColorUtil.c("&cИгрок не найден!")); return true; }
            if (ranks.isOwnerLevel(t) && !t.equals(p)) {
                p.sendMessage(ColorUtil.c("&#FF1744✖ Нельзя применять /sudo на другого OWNER!"));
                return true;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                if (i > 1) sb.append(' ');
                sb.append(args[i]);
            }
            String act = sb.toString();
            if (act.startsWith("c:")) {
                t.chat(act.substring(2));
            } else {
                t.performCommand(act.startsWith("/") ? act.substring(1) : act);
            }
            p.sendMessage(ColorUtil.c("&#00E676✔ Выполнено от лица &f" + t.getName() + "&7: &e" + act));
            return true;
        }
        if ("setspawn".equals(c)) {
            if (!isOwner) {
                p.sendMessage(ColorUtil.c("&#FF1744✖ Команда /setspawn доступна СТРОГО ТОЛЬКО владельцу (OWNER)!"));
                return true;
            }
            if (args.length >= 1 && "reset".equalsIgnoreCase(args[0])) {
                store.setCustomSpawnLoc(null);
                p.sendMessage(ColorUtil.c("&#00E676✔ Точка /spawn возвращена на стандартную площадь Зимней Цитадели!"));
                return true;
            }
            store.setCustomSpawnLoc(p.getLocation());
            p.sendMessage(ColorUtil.c("&#00E676✔ Новая точка появления /spawn успешно установлена! (Сброс: /setspawn reset)"));
            return true;
        }
        return true;
    }

    // ================= СИСТЕМА ТИММЕЙТОВ И ДРУЗЕЙ (/tm, /friend, /friends, /tc) v6.0 =================
    private boolean handleFriendTeamCommand(Player p, String[] args) {
        if (args.length == 0 || "list".equalsIgnoreCase(args[0]) || "menu".equalsIgnoreCase(args[0])) {
            guis.openTeammatesMenu(p);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("pvp".equals(sub) || "ff".equals(sub)) {
            boolean on = store.toggleTeammatePvp(p.getName());
            p.sendMessage(ColorUtil.c(on
                    ? "&#FF5252⚔ Урон по вашим тиммейтам ВКЛЮЧЁН!"
                    : "&#00E676🛡 Урон по вашим тиммейтам ОТКЛЮЧЁН (Защита активна)!"));
            return true;
        }
        if ("accept".equals(sub) || "yes".equals(sub)) {
            String fromKey = store.popTeammateInvite(p.getName(), args.length >= 2 ? args[1] : null);
            if (fromKey == null) {
                p.sendMessage(ColorUtil.c("&#FF5252✖ У вас нет активных приглашений в тиму!"));
                return true;
            }
            Player inviter = Bukkit.getPlayerExact(fromKey);
            String inviterDisp = inviter != null ? inviter.getName() : store.getStoredDisplayName(fromKey);
            store.addMutualTeammates(p.getName(), inviterDisp);
            ColorUtil.sound(p, "entity.player.levelup", 1.0f, 1.3f);
            p.sendMessage(ColorUtil.c("&#00E676🤝 Вы приняли запрос! Игрок &f" + inviterDisp
                    + " &#00E676теперь ваш ТИММЕЙТ (Божья Аура лечит вас обоих, чат: &f/tc <текст>&#00E676)!"));
            if (inviter != null && inviter.isOnline()) {
                ColorUtil.sound(inviter, "entity.player.levelup", 1.0f, 1.3f);
                inviter.sendMessage(ColorUtil.c("&#00E676🤝 Игрок &f" + p.getName() + " &#00E676принял ваш запрос в тиму! Открыть тиму: &f/friends"));
            }
            return true;
        }
        if ("deny".equals(sub) || "no".equals(sub)) {
            String fromKey = store.popTeammateInvite(p.getName(), args.length >= 2 ? args[1] : null);
            if (fromKey == null) {
                p.sendMessage(ColorUtil.c("&cНет активных приглашений в тиму."));
                return true;
            }
            p.sendMessage(ColorUtil.c("&#FF5252✖ Приглашение в тиму отклонено."));
            return true;
        }
        if ("remove".equals(sub) || "del".equals(sub) || "delete".equals(sub) || "unfriend".equals(sub)) {
            if (args.length < 2) {
                p.sendMessage(ColorUtil.c("&eИспользование: &f/tm del <ник>"));
                return true;
            }
            boolean removed = store.removeMutualTeammates(p.getName(), args[1]);
            p.sendMessage(ColorUtil.c(removed
                    ? "&#FF5252✖ Игрок &f" + args[1] + " &#FF5252удалён из списка тиммейтов."
                    : "&cЭтот игрок не состоит с вами в тиме."));
            return true;
        }

        // /tm add <nick> OR shorthand /tm <nick>
        String targetNick = "add".equals(sub) || "invite".equals(sub)
                ? (args.length >= 2 ? args[1] : null)
                : args[0];
        if (targetNick == null || targetNick.trim().isEmpty()) {
            p.sendMessage(ColorUtil.c("&eИспользование: &f/tm <ник> &7(меню: &f/friends&7, урон: &f/tm pvp&7, чат: &f/tc&7)"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(targetNick);
        if (target == null) target = Bukkit.getPlayer(targetNick);
        if (target == null || !target.isOnline() || target.equals(p)) {
            p.sendMessage(ColorUtil.c("&#FF5252✖ Укажите другого игрока онлайн!"));
            return true;
        }
        if (store.isDirectTeammate(p.getName(), target.getName())) {
            p.sendMessage(ColorUtil.c("&#00E676✔ Игрок &f" + target.getName() + " &#00E676уже состоит в вашей тиме! Меню: &f/friends"));
            return true;
        }
        store.sendTeammateInvite(p.getName(), target.getName());
        p.sendMessage(ColorUtil.c("&#00E676🤝 Приглашение в тиммейты отправлено игроку &f" + target.getName() + "&#00E676!"));

        TextComponent line = new TextComponent(ColorUtil.c("&#00E676🤝 Игрок &#FFD740" + p.getName()
                + " &#00E676приглашает вас в &lТИММЕЙТЫ &#80D8FF(общий хил Божьей Аурой + защита от урона)! "));
        TextComponent acceptBtn = new TextComponent(ColorUtil.c("&#00E676&l[ ✔ ПРИНЯТЬ ] "));
        acceptBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tm accept " + p.getName()));
        acceptBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ColorUtil.c("&aСтать тиммейтами с " + p.getName())).create()));
        TextComponent denyBtn = new TextComponent(ColorUtil.c("&#FF5252&l[ ✖ ОТКЛОНИТЬ ]"));
        denyBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tm deny " + p.getName()));
        denyBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ColorUtil.c("&cОтклонить приглашение")).create()));
        line.addExtra(acceptBtn);
        line.addExtra(denyBtn);
        target.spigot().sendMessage(line);
        ColorUtil.sound(target, "entity.experience_orb.pickup", 0.9f, 1.35f);
        return true;
    }

    private boolean handleTeamChatCommand(Player p, String[] args) {
        if (args.length < 1) {
            p.sendMessage(ColorUtil.c("&eИспользование: &f/tc <сообщение для тиммейтов и клана>"));
            return true;
        }
        boolean isOwner = ranks.isOwnerLevel(p);
        if (!isOwner && (store.getMuteRemainingSec(p.getName()) > 0
                || store.getIpMuteRemainingSec(store.getPlayerIp(p)) > 0)) {
            p.sendMessage(ColorUtil.c("&#FF5252🔇 Вы в муте!"));
            return true;
        }
        String text = String.join(" ", args);
        String formatted = ColorUtil.c("&#00E676&l[ТИМА] &#FFD740" + p.getName() + " &8» &f") + text;
        int delivered = 0;
        p.sendMessage(formatted);
        for (Player on : Bukkit.getOnlinePlayers()) {
            if (!on.equals(p) && store.areTeammates(p.getName(), on.getName())) {
                on.sendMessage(formatted);
                ColorUtil.sound(on, "block.note_block.chime", 0.6f, 1.5f);
                delivered++;
            } else if (!on.equals(p) && store.isSocialSpy(on)) {
                on.sendMessage(ColorUtil.c("&8[👁 Spy-TC] &7" + p.getName() + ": &f") + text);
            }
        }
        if (delivered == 0) {
            p.sendMessage(ColorUtil.c("&8(Сейчас никого из ваших тиммейтов нет онлайн — добавьте друга: &e/tm <ник>&8)"));
        }
        return true;
    }
}
