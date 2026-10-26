package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.GZIPInputStream;

public final class SpawnEngine {

    public static final class NpcSpec {
        public final String slug;
        public final EntityType entityType;
        public final Villager.Profession prof;
        public final Material handItem;
        public final String title;
        public final String subtitle;
        public final int x, y, z;
        public final float yaw;
        public final String command;

        public NpcSpec(String slug, EntityType et, Villager.Profession prof, Material handItem,
                       String title, String subtitle, int x, int y, int z, float yaw, String command) {
            this.slug = slug;
            this.entityType = et;
            this.prof = prof;
            this.handItem = handItem;
            this.title = title;
            this.subtitle = subtitle;
            this.x = x; this.y = y; this.z = z;
            this.yaw = yaw;
            this.command = command;
        }
    }

    public static final class CasePedestal {
        public final String id;
        public final String title;
        public final String dropLine;
        public final String priceLine;
        public final int x, z;
        public final Material icon;

        public CasePedestal(String id, String title, String dropLine, String priceLine, int x, int z, Material icon) {
            this.id = id;
            this.title = title;
            this.dropLine = dropLine;
            this.priceLine = priceLine;
            this.x = x;
            this.z = z;
            this.icon = icon;
        }
    }

    public static final List<CasePedestal> CASES = new ArrayList<CasePedestal>();
    public static final List<NpcSpec> NPCS = new ArrayList<NpcSpec>();

    static {
        CASES.add(new CasePedestal("donate",    "&#A855F7&l👑 ДОНАТ-КЕЙС",       "&7Выпадают: &aPEGAS &7.. &5KING &7.. &cD.ADMIN",       "&f×1: &#00E67629₽ &8(&m39₽&8) &7| &f×5: &#00E676119₽ &7| &f×10: &#FFD740199₽", -20, -32, Material.GOLDEN_HELMET));
        CASES.add(new CasePedestal("sdonate",   "&#EC4899&l✨ СУПЕР ДОНАТ-КЕЙС", "&7Только элита: &eGOD &7.. &cD.ADMIN &7.. &6D.OWNER", "&f×1: &#00E67699₽ &8(&m139₽&8) &7| &f×3: &#00E676259₽ &7| &f×5: &#FFD740399₽",  -16, -35, Material.NETHER_STAR));
        CASES.add(new CasePedestal("legendary", "&#FACC15&l🏆 ЛЕГЕНДАРНЫЙ КЕЙС", "&7Шанс на &eGOD&7, &3D.MODER &7и &615 000 ❖",          "&f×1: &#00E67659₽ &8(&m79₽&8) &7| &f×3: &#00E676149₽ &7| &f×5: &#FFD740229₽",   -12, -37, Material.DRAGON_BREATH));
        CASES.add(new CasePedestal("reallydev", "&#8B5CF6&l💎 КЕЙС REALLYDEV",   "&7Шанс на &dOPKA&7, &6D.OWNER &7и &e15 000 ❖",         "&f×1: &#00E676129₽ &8(&m179₽&8) &7| &f×3: &#00E676339₽ &7| &f×5: &#FFD740529₽",  -8, -39, Material.AMETHYST_CLUSTER));
        CASES.add(new CasePedestal("riliks",    "&#F59E0B&l❖ КЕЙС С РИЛИКАМИ",  "&7От &f250 ❖ &7до джекпота &#FF174450 000 ❖",          "&f×1: &#00E67639₽ &8(&m55₽&8) &7| &f×5: &#00E676169₽ &7| &f×10: &#FFD740299₽",   -4, -40, Material.SUNFLOWER));
        CASES.add(new CasePedestal("allorno",   "&#DC2626&l🔥 ВСЁ ИЛИ НИЧЕГО",   "&750/50: &5KING&7, &cD.ADMIN &7или &dOPKA!",            "&f×1: &#00E67645₽ &8(&m65₽&8) &7| &f×3: &#00E676119₽ &7| &f×5: &#FFD740179₽",     0, -40, Material.TNT));
        CASES.add(new CasePedestal("ditems",    "&#EF4444&l⚔ ДОНАТ-ВЕЩИ",       "&7Сфера Андромеды, Талисман Крушителя, Трапки",         "&f×1: &#00E67649₽ &8(&m69₽&8) &7| &f×3: &#00E676129₽ &7| &f×5: &#FFD740199₽",     4, -40, Material.TOTEM_OF_UNDYING));
        CASES.add(new CasePedestal("kits",      "&#3B82F6&l🛡 КЕЙС С КИТАМИ",    "&7Готовые сеты от &cVAMPIRE &7до &6D.OWNER",            "&f×1: &#00E67635₽ &8(&m49₽&8) &7| &f×3: &#00E67689₽ &7| &f×5: &#FFD740139₽",      8, -39, Material.NETHERITE_CHESTPLATE));
        CASES.add(new CasePedestal("money",     "&#22C55E&l💰 ДЕНЕЖНЫЙ КЕЙС",    "&7От &a50 000$ &7до &#FFD74010 000 000$",               "&f×1: &#00E67619₽ &8(&m29₽&8) &7| &f×5: &#00E67679₽ &7| &f×10: &#FFD740139₽",    12, -37, Material.EMERALD));
        CASES.add(new CasePedestal("djamshut",  "&#FB923C&l⛏ КЕЙС ДЖАМШУТА",    "&7Бур 3×3, рилики, &eGOD &7и &cD.ADMIN",                "&f×1: &#00E67625₽ &8(&m39₽&8) &7| &f×5: &#00E67699₽ &7| &f×10: &#FFD740179₽",    16, -35, Material.NETHERITE_PICKAXE));
        CASES.add(new CasePedestal("summer",    "&#38BDF8&l❄ СЕЗОННЫЙ КЕЙС",    "&7Талисман Мерзлоты, &5KING &7и &620 000 ❖",            "&f×1: &#00E67639₽ &8(&m59₽&8) &7| &f×3: &#00E67699₽ &7| &f×5: &#FFD740149₽",     20, -32, Material.SNOWBALL));

        NPCS.add(new NpcSpec("santa",      EntityType.VILLAGER, Villager.Profession.CLERIC,     Material.NETHER_STAR,       "&#FF5252&l🎅 ДЕД МОРОЗ",               "&fНовогодняя лавка &7(/nyshop)",        -6, 101,   8,  -35f, "nyshop"));
        NPCS.add(new NpcSpec("snowmaiden", EntityType.SNOW_GOLEM, null,                         Material.DIAMOND,           "&#80D8FF&l🤶 СНЕГУРОЧКА",              "&fБесплатные подарки &7(/gifts)",        6, 101,   8,   35f, "gifts"));
        NPCS.add(new NpcSpec("grinch",     EntityType.WITCH, null,                              Material.SLIME_BALL,        "&#69F0AE&l👹 ГРИНЧ",                   "&fБоевой Пропуск Сезона &7(/bp)",        0, 101,  -9,    0f, "bp"));
        NPCS.add(new NpcSpec("rtp",        EntityType.VILLAGER, Villager.Profession.CARTOGRAPHER, Material.COMPASS,         "&#00E676&l🧭 РАНДОМНЫЙ ТЕЛЕПОРТ",      "&fВыход в выживание &7(/rtp)",         -18, 101,   6,  -90f, "rtp"));
        NPCS.add(new NpcSpec("shop",       EntityType.VILLAGER, Villager.Profession.LIBRARIAN,  Material.TOTEM_OF_UNDYING,  "&#FF4081&l💎 ДОНАТ-МАГАЗИН",           "&fСферы, талисманы, трапки &7(/shop)",  18, 101,   6,   90f, "shop"));
        NPCS.add(new NpcSpec("auction",    EntityType.VILLAGER, Villager.Profession.NITWIT,     Material.GOLD_INGOT,        "&#FFD740&l⚖ АУКЦИОН ИГРОКОВ",          "&fТоргуй с игроками &7(/ah)",           20, 101,   0,   90f, "ah"));
        NPCS.add(new NpcSpec("buyer",      EntityType.VILLAGER, Villager.Profession.FARMER,     Material.EMERALD,           "&#FFAB40&l💰 СКУПЩИК РЕСУРСОВ",        "&fПродавай ресурсы &7(/buyer)",         18, 101,  -6,   90f, "buyer"));
        NPCS.add(new NpcSpec("jobs",       EntityType.VILLAGER, Villager.Profession.MASON,      Material.IRON_PICKAXE,      "&#FFEE58&l⛏ РАБОТЫ СЕРВЕРА",           "&fЗаработок монет &7(/jobs)",           15, 101,  14,  135f, "jobs"));
        NPCS.add(new NpcSpec("clans",      EntityType.IRON_GOLEM, null,                         Material.SHIELD,            "&#B388FF&l🏰 КЛАНЫ И ТОПЫ",            "&fСоздай клан &7(/clan)",              -20, 101,   0,  -90f, "clan"));
        NPCS.add(new NpcSpec("blacksmith", EntityType.VILLAGER, Villager.Profession.WEAPONSMITH, Material.ANVIL,            "&#FF7043&l⚒ ОПЫТНЫЙ КУЗНЕЦ",          "&fЧаровальня 30 ур. и Киты &7(/kits)", -18, 101,  -6,  -90f, "kits"));
        NPCS.add(new NpcSpec("freedonate", EntityType.VILLAGER, Villager.Profession.SHEPHERD,   Material.CHEST,             "&#76FF03&l🎁 БЕСПЛАТНЫЙ ДОНАТ",        "&fЕжедневные награды &7(/free)",       -15, 101,  14, -135f, "gifts"));
        NPCS.add(new NpcSpec("pvp",        EntityType.VINDICATOR, null,                         Material.NETHERITE_SWORD,   "&#FF1744&l⚔ PVP КОЛИЗЕЙ",              "&fПрыгай вниз за красную линию!",       -8, 101,  24,  180f, "kits"));
        NPCS.add(new NpcSpec("djamshut",   EntityType.VILLAGER, Villager.Profession.TOOLSMITH,  Material.NETHERITE_PICKAXE, "&#FF9100&l🎄 ДЖАМШУТ (ВСЕ КИТЫ)",      "&f29 наборов привилегий &7(/kits)",      8, 101,  24,  180f, "kits"));
        NPCS.add(new NpcSpec("donate",     EntityType.VILLAGER, Villager.Profession.ARMORER,    Material.GOLDEN_HELMET,     "&#FACC15&l👑 ПРИВИЛЕГИИ (ОТ 19₽)",     "&fKING · OPKA · D.OWNER &7(/donate)",    0, 101, -20,    0f, "donate"));
        NPCS.add(new NpcSpec("kp_staff",   EntityType.VILLAGER, Villager.Profession.FLETCHER,   Material.DIAMOND_SWORD,     "&#10B981&l🛡 КОМАНДА ПРОЕКТА (КП/ДКП)", "&fНабор в КП и ДКП &7(/kp)",           -10, 101, -15,  -25f, "kp"));
        NPCS.add(new NpcSpec("media_npc",  EntityType.VILLAGER, Villager.Profession.LIBRARIAN,  Material.SPYGLASS,          "&#FF0050&l🎬 МЕДИА-ПАРТНЁРКА",         "&fYT · TikTok · Twitch &7(/media)",     10, 101, -15,   25f, "media"));
    }

    private final KingGriefCore plugin;
    private final Random rng = new Random();
    private final List<Entity> managedEntities = new ArrayList<Entity>();
    private final Map<String, CasePedestal> caseByCoord = new HashMap<String, CasePedestal>();

    private int mineSecondsLeft = 180;
    private int airdropSecondsLeft = 1800;
    private Location activeAirdropLoc = null;
    private int airdropUnlockSec = 0;
    private TextDisplay airdropHolo = null;
    private TextDisplay mineHolo = null;

    public SpawnEngine(KingGriefCore plugin) {
        this.plugin = plugin;
        for (CasePedestal cp : CASES) {
            caseByCoord.put(cp.x + ":101:" + cp.z, cp);
        }
    }

    public World getSpawnWorld() {
        String wn = plugin.getConfig().getString("spawn-world", "world");
        World w = Bukkit.getWorld(wn);
        return w != null ? w : Bukkit.getWorlds().get(0);
    }

    public Location getSpawnLocation() {
        double sx = plugin.getConfig().getDouble("spawn-x", 0.5);
        double sy = plugin.getConfig().getDouble("spawn-y", 100.0);
        double sz = plugin.getConfig().getDouble("spawn-z", 14.5);
        float yaw = (float) plugin.getConfig().getDouble("spawn-yaw", 180.0);
        float pitch = (float) plugin.getConfig().getDouble("spawn-pitch", 0.0);
        return new Location(getSpawnWorld(), sx, sy, sz, yaw, pitch);
    }

    public boolean isInSpawnRegion(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().equals(getSpawnWorld())) return false;
        return Math.abs(loc.getBlockX()) <= 60 && Math.abs(loc.getBlockZ()) <= 60 && loc.getBlockY() >= 78 && loc.getBlockY() <= 165;
    }

    public boolean isInAutoMineOres(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().equals(getSpawnWorld())) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        return x >= -36 && x <= -32 && y >= 95 && y <= 98 && z >= 17 && z <= 23;
    }

    public boolean isInPvpZone(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().equals(getSpawnWorld())) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        // PvP Colosseum pit
        if (x >= -14 && x <= 14 && y >= 84 && y <= 98 && z >= 28 && z <= 48) return true;
        // 3 1v1 Kabinki
        if (x >= 29 && x <= 35 && y >= 100 && y <= 104) {
            if ((z >= 18 && z <= 22) || (z >= 25 && z <= 29) || (z >= 32 && z <= 36)) return true;
        }
        return false;
    }

    public CasePedestal getCaseAtBlock(Block b) {
        if (b == null || !b.getWorld().equals(getSpawnWorld())) return null;
        return caseByCoord.get(b.getX() + ":" + b.getY() + ":" + b.getZ());
    }

    public int getAirdropSecondsLeft() { return airdropSecondsLeft; }
    public Location getActiveAirdropLoc() { return activeAirdropLoc; }
    public boolean isAirdropUnlocked() { return activeAirdropLoc != null && airdropUnlockSec <= 0; }

    public void initOnStartup() {
        World w = getSpawnWorld();
        applyWinterWorldRules(w);
        File flag = new File(plugin.getDataFolder(), "spawn_v3_built.flag");
        if (!flag.exists() && plugin.getConfig().getBoolean("auto-build-spawn-on-first-boot", true)) {
            buildSpawnAsyncBatched(null);
        } else {
            w.setSpawnLocation(0, 100, 14, 180f);
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    respawnNpcsAndHolograms();
                    refillAutoMine();
                }
            }, 30L);
        }
        startTickTasks();
    }

    public void applyWinterWorldRules(World w) {
        if (w == null) return;
        w.setTime(6000L);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "gamerule doDaylightCycle false");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "gamerule doFireTick false");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "gamerule randomTickSpeed 0");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "gamerule announceAdvancements false");
    }

    public void buildSpawnAsyncBatched(final Player caller) {
        final World world = getSpawnWorld();
        applyWinterWorldRules(world);
        InputStream rawIn = plugin.getResource("spawn_v3.dat");
        if (rawIn == null) {
            plugin.getLogger().warning("spawn_v3.dat not found in jar!");
            return;
        }
        final int W, H, L, OX, OY, OZ;
        final BlockData[] palData;
        final byte[] vol;
        try {
            DataInputStream dis = new DataInputStream(new GZIPInputStream(rawIn));
            W = dis.readShort();
            H = dis.readShort();
            L = dis.readShort();
            OX = dis.readShort();
            OY = dis.readShort();
            OZ = dis.readShort();
            int palCount = dis.readUnsignedShort();
            palData = new BlockData[palCount];
            for (int i = 0; i < palCount; i++) {
                int slen = dis.readUnsignedShort();
                byte[] sb = new byte[slen];
                dis.readFully(sb);
                String bs = new String(sb, "UTF-8");
                try {
                    palData[i] = Bukkit.createBlockData(bs);
                } catch (Throwable t) {
                    palData[i] = Bukkit.createBlockData("minecraft:smooth_quartz");
                }
            }
            vol = new byte[W * H * L];
            dis.readFully(vol);
            dis.close();
        } catch (IOException e) {
            plugin.getLogger().warning("Failed reading spawn_v3.dat: " + e.getMessage());
            return;
        }

        // Preload spawn chunks
        for (int cx = -4; cx <= 4; cx++) {
            for (int cz = -4; cz <= 4; cz++) {
                world.getChunkAt(cx, cz).load(true);
            }
        }

        if (caller != null) {
            caller.sendMessage(ColorUtil.c("&#80D8FF❄ &fСтрою 3D Новогодний Спавн ReallyWorld (121×58×121)..."));
        }
        plugin.getLogger().info("Строительство 3D Новогоднего Спавна KINGGRIEF (121x58x121)...");

        new BukkitRunnable() {
            int idx = 0;
            final int total = vol.length;
            final int batch = 22000;

            @Override
            public void run() {
                int processed = 0;
                while (idx < total && processed < batch) {
                    int bId = vol[idx] & 0xFF;
                    int rem = idx;
                    int lx = rem % W;
                    rem /= W;
                    int lz = rem % L;
                    int ly = rem / L;
                    idx++;
                    processed++;

                    int wx = lx - OX;
                    int wy = ly + 100 - OY;
                    int wz = lz - OZ;
                    if (bId == 0 && (wx * wx + wz * wz > 56 * 56)) {
                        continue;
                    }
                    Block blk = world.getBlockAt(wx, wy, wz);
                    if (bId == 0 && blk.getType() == Material.AIR) continue;
                    blk.setBlockData(palData[bId], false);
                }
                if (idx >= total) {
                    cancel();
                    world.setSpawnLocation(0, 100, 14, 180f);
                    try {
                        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
                        new File(plugin.getDataFolder(), "spawn_v3_built.flag").createNewFile();
                    } catch (IOException ignored) {
                    }
                    refillAutoMine();
                    respawnNpcsAndHolograms();
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.teleport(getSpawnLocation());
                        ColorUtil.sound(p, "entity.player.levelup", 1.0f, 1.2f);
                        p.sendMessage(ColorUtil.c("&#00E676✔ &fНовогодний 3D-Спавн, все 16 NPC, 11 Кейсов, Авто-Шахта и Голограммы успешно загружены!"));
                    }
                    plugin.getLogger().info("3D Новогодний Спавн KINGGRIEF успешно построен!");
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    public void clearManagedEntities() {
        for (Entity e : managedEntities) {
            if (e != null && e.isValid()) e.remove();
        }
        managedEntities.clear();
        World w = getSpawnWorld();
        if (w != null) {
            for (Entity e : w.getEntities()) {
                if (e.getScoreboardTags().contains("kg_managed")) {
                    e.remove();
                }
            }
        }
    }

    public void respawnNpcsAndHolograms() {
        clearManagedEntities();
        if (!plugin.getConfig().getBoolean("enable-builtin-npcs-and-holograms", true)) return;
        boolean hasDh = Bukkit.getPluginManager().isPluginEnabled("DecentHolograms");
        boolean hasFn = Bukkit.getPluginManager().isPluginEnabled("FancyNpcs");
        World w = getSpawnWorld();

        // Force load spawn chunks so entities spawn reliably even when 0 players online
        for (int cx = -4; cx <= 4; cx++) {
            for (int cz = -4; cz <= 4; cz++) {
                w.getChunkAt(cx, cz).load(true);
            }
        }

        if (!hasFn) {
            for (NpcSpec ns : NPCS) {
                spawnNpc(w, ns);
            }
        }

        if (!hasDh) {
            for (NpcSpec ns : NPCS) {
                spawnHolo(new Location(w, ns.x + 0.5, ns.y + 2.45, ns.z + 0.5),
                        ns.title, ns.subtitle, "&#FFE082▸ Нажми ПКМ / ЛКМ");
            }
            for (CasePedestal cp : CASES) {
                spawnHolo(new Location(w, cp.x + 0.5, 102.65, cp.z + 0.5),
                        cp.title, cp.dropLine, cp.priceLine, "&#00E676ПКМ — Открыть &8| &eЛКМ — Призы");
            }
            // Main Welcome Hologram
            spawnHolo(new Location(w, 0.5, 103.5, 11.5),
                    "&#80D8FF❄ &#FFFFFF&lЗИМНИЙ СЕЗОН · &#FF1744&lKING&#FF9100&lGRIEF &#80D8FF❄",
                    "&fФулл Копия ReallyWorld · Новогодний Гриф Выживание",
                    "&7Авто-Шахта: &#FFEE58/mine &8| &7Аирдроп: &#FF4081/airdrop &8| &7Паркур на севере!",
                    "&7Промокоды новичкам: &#00E676/promo NEWYEAR &7и &#FFD740/promo KINGGRIEF",
                    "&7Автодонат от 19₽: &#FF4081kinggrief.vercel.app &8| &7Меню: &#FFD740/menu");

            // Real Prices from Site — Ranks Board 1
            spawnHolo(new Location(w, -11.0, 103.8, 15.0),
                    "&#FFD740&l👑 ЦЕНЫ НА ПРИВИЛЕГИИ (С САЙТА) 👑",
                    "&#22C55E&lPEGAS &8— &#00E67619₽ &8(&m39₽&8) &7| &#84CC16&lCOBRA &8— &#00E67639₽ &8(&m59₽&8)",
                    "&#EC4899&lBUNNY &8— &#00E67659₽ &8(&m89₽&8) &7| &#38BDF8&lRABBIT &8— &#00E67689₽ &8(&m129₽&8)",
                    "&#EF4444&lVAMPIRE &8— &#00E67699₽ &8(&m159₽&8) &7| &#FACC15&lGOD &8— &#00E676149₽ &8(&m229₽&8)",
                    "&#A855F7&l👑 KING &8— &#FFD740&l199₽ &8(&m299₽&8) &e★ ХИТ СЕЗОНА!",
                    "&#00E5FF▸ Купить: &fkinggrief.vercel.app &7или &e/donate");

            // Real Prices from Site — Top Ranks & DKP Board 2
            spawnHolo(new Location(w, 11.0, 103.8, 15.0),
                    "&#FF1744&l🔥 ТОП-ДОНАТ И ДКП (С САЙТА) 🔥",
                    "&#14B8A6&lD.MODER &8— &#00E676249₽ &7| &#3B82F6&lD.ST.MODER &8— &#00E676349₽",
                    "&#8B5CF6&lD.GLMODER &8— &#00E676449₽ &7| &#F97316&lD.ML.ADMIN &8— &#00E676599₽",
                    "&#DC2626&lD.ADMIN &8— &#00E676799₽ &8(&m1099₽&8) &7| &#FF6D00&l⚡ ДКП",
                    "&#C026D3&l🔥 OPKA &8— &#FFD740&l999₽ &8(&m1399₽&8) &d★ УЛЬТИМАТУМ",
                    "&#FACC15&l👑 D.OWNER &8— &#FFEA00&l1 499₽ &8(&m1999₽&8) &6★ ВЕРШИНА!",
                    "&#00E5FF▸ Сайт: &fkinggrief.vercel.app/privileges");

            // Relics Board
            spawnHolo(new Location(w, 14.0, 103.6, 2.0),
                    "&#F59E0B&l❖ КУРС РИЛИКОВ НА САЙТЕ ❖",
                    "&f1 000 ❖ — &#00E67619₽ &7| &f2 500 ❖ — &#00E67639₽ &7| &f5 000 ❖ — &#00E67669₽",
                    "&f10 000 ❖ — &#00E676119₽ &7| &f25 000 ❖ — &#00E676249₽ &7| &f50 000 ❖ — &#00E676449₽",
                    "&#FACC15&l★ 100 000 ❖ — &#00E676&l799₽ &8(&m1099₽&8) &eМАКС. ВЫГОДА!",
                    "&7Трать рилики в &#FF4081/shop &7на Сферы, Талисманы и Трапки!");

            // Services + KP + Media Board
            spawnHolo(new Location(w, -14.0, 103.6, 2.0),
                    "&#38BDF8&l🛡 УСЛУГИ · КП · МЕДИА 🛡",
                    "&fРазмут — &#00E67649₽ &8(&m79₽&8) &7| &fРазбан — &#00E67699₽ &8(&m149₽&8)",
                    "&fРазмут по IP — &#00E67699₽ &7| &fРазбан по IP — &#00E676199₽",
                    "&#10B981&l🛡 Команда Проекта (Хелпер → Куратор): &f/kp",
                    "&#FF0050&l🎬 YouTube / TikTok / Twitch / MEDIA+: &f/media");

            // Cases Arena Arch + PvP + Kabinki + Parkour + AutoMine
            spawnHolo(new Location(w, 0.5, 104.8, -25.0),
                    "&#FFD740&l🎁 АРЕНА ВСЕХ 11 РАБОЧИХ КЕЙСОВ 🎁",
                    "&fДенежный &#00E67619₽ &7· Джамшута &#00E67625₽ &7· Донат &#00E67629₽ &7· Киты &#00E67635₽",
                    "&fРилики &#00E67639₽ &7· Сезонный &#00E67639₽ &7· Легендарный &#00E67659₽ &7· Супер &#00E67699₽",
                    "&#00E676▸ Открывай ключи прямо здесь или через &e/cases");

            spawnHolo(new Location(w, 0.5, 102.4, 27.5),
                    "&#FF1744&l⚔ ЗИМНИЙ КОЛИЗЕЙ PVP ⚔",
                    "&fПрыгай за &#FF1744красную линию &fдля честного боя!",
                    "&7Урон от падения отключён · 2 лестницы по бокам");

            spawnHolo(new Location(w, 28.5, 103.0, 27.0),
                    "&#FF5252&l⚔ ДУЭЛЬНЫЕ КАБИНКИ 1 НА 1 ⚔",
                    "&fKabinka 1 · Kabinka 2 · Kabinka 3",
                    "&#00E676Авто-закрытие стеклянных дверей при входе 2 игроков!");

            spawnHolo(new Location(w, 24.5, 102.3, -14.0),
                    "&#80D8FF&l❄ НОВОГОДНИЙ ЛЕДЯНОЙ ПАРКУР ❄",
                    "&fДоберись до золотой плиты наверху!",
                    "&7Награда: &#FFEA00+5 000$ &7и &#00E5FF+25 риликов ❖");

            spawnHolo(new Location(w, 30.5, 112.8, -27.5),
                    "&#FFD740&l🏆 ФИНИШ ПАРКУРА 🏆",
                    "&aНаступи на золотую плиту для получения приза!");

            mineHolo = spawnHolo(new Location(w, -32.0, 102.4, 20.5),
                    "&#FFEE58&l⛏ ЗИМНЯЯ АВТО-ШАХТА ДЕДА МОРОЗА ⛏",
                    "&fКопай руду прямо на спавне и продавай в &e/buyer&f!",
                    "&7Обновление шахты через: &#00E6763м 0с");
        }
    }

    private void spawnNpc(World w, NpcSpec ns) {
        Location loc = new Location(w, ns.x + 0.5, ns.y, ns.z + 0.5, ns.yaw, 0f);
        Entity ent = w.spawnEntity(loc, ns.entityType);
        ent.addScoreboardTag("kg_managed");
        ent.addScoreboardTag("kg_npc:" + ns.command);
        ent.setInvulnerable(true);
        ent.setSilent(true);
        ent.setGlowing(true);
        ent.setPersistent(true);
        if (ent instanceof LivingEntity) {
            LivingEntity le = (LivingEntity) ent;
            le.setAI(false);
            le.setRemoveWhenFarAway(false);
            le.setCollidable(false);
            EntityEquipment eq = le.getEquipment();
            if (eq != null && ns.handItem != null) {
                eq.setItemInMainHand(new ItemStack(ns.handItem));
            }
        }
        if (ent instanceof Villager && ns.prof != null) {
            Villager v = (Villager) ent;
            v.setProfession(ns.prof);
            v.setVillagerLevel(5);
        }
        managedEntities.add(ent);
    }

    public TextDisplay spawnHolo(Location loc, String... lines) {
        if (loc == null || loc.getWorld() == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) sb.append('\n');
            sb.append(ColorUtil.c(lines[i]));
        }
        TextDisplay td = loc.getWorld().spawn(loc, TextDisplay.class);
        td.setText(sb.toString());
        td.setBillboard(Display.Billboard.CENTER);
        td.setShadowed(true);
        td.setSeeThrough(false);
        td.setBackgroundColor(Color.fromARGB(135, 10, 16, 28));
        td.setViewRange(0.75f);
        td.setPersistent(true);
        td.addScoreboardTag("kg_managed");
        td.addScoreboardTag("kg_holo");
        managedEntities.add(td);
        return td;
    }

    public void refillAutoMine() {
        World w = getSpawnWorld();
        Material[] ores = new Material[]{
                Material.COAL_ORE, Material.COAL_ORE, Material.IRON_ORE, Material.IRON_ORE,
                Material.GOLD_ORE, Material.LAPIS_ORE, Material.REDSTONE_ORE,
                Material.DIAMOND_ORE, Material.EMERALD_ORE
        };
        // Safely lift players inside ore cube before refilling
        for (Player p : w.getPlayers()) {
            if (isInAutoMineOres(p.getLocation())) {
                p.teleport(new Location(w, -28.5, 100.0, 20.5, 90f, 0f));
                p.sendMessage(ColorUtil.c("&#FFEE58⛏ &fЗимняя Авто-Шахта обновилась свежей рудой!"));
            }
        }
        for (int y = 95; y <= 98; y++) {
            for (int z = 17; z <= 23; z++) {
                for (int x = -36; x <= -32; x++) {
                    w.getBlockAt(x, y, z).setType(ores[rng.nextInt(ores.length)], false);
                }
            }
        }
        mineSecondsLeft = Math.max(60, plugin.getConfig().getInt("automine-reset-seconds", 180));
    }

    public void triggerMysticAirdrop() {
        World w = getSpawnWorld();
        // Clean previous airdrop
        clearAirdrop();
        int ax = (rng.nextBoolean() ? 1 : -1) * (350 + rng.nextInt(650));
        int az = (rng.nextBoolean() ? 1 : -1) * (350 + rng.nextInt(650));
        w.getChunkAt(ax >> 4, az >> 4).load(true);
        int ay = w.getHighestBlockYAt(ax, az) + 1;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                w.getBlockAt(ax + dx, ay - 1, az + dz).setType((dx == 0 && dz == 0) ? Material.SEA_LANTERN : Material.GOLD_BLOCK, false);
            }
        }
        w.getBlockAt(ax, ay, az).setType(Material.ENDER_CHEST, false);
        activeAirdropLoc = new Location(w, ax, ay, az);
        airdropUnlockSec = 60;
        airdropHolo = spawnHolo(new Location(w, ax + 0.5, ay + 2.2, az + 0.5),
                "&#FF1744&l☄ НОВОГОДНИЙ МИСТИЧЕСКИЙ СУНДУК ☄",
                "&fОткрытие через: &#FFD74060с",
                "&7Внутри: Рилики, Сфера, Талисман и Чар-Яблоки!");

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ColorUtil.c("&#80D8FF❄ &#FF1744&lМИСТИЧЕСКИЙ АИРДРОП ДЕДА МОРОЗА! &#80D8FF❄"));
        Bukkit.broadcastMessage(ColorUtil.c(" &fСундук упал на координатах: &#FFD740X: " + ax + " Y: " + ay + " Z: " + az));
        Bukkit.broadcastMessage(ColorUtil.c(" &7Откроется через &e60 секунд&7! Телепорт ближе: &#00E676/airdrop"));
        Bukkit.broadcastMessage("");
        for (Player p : Bukkit.getOnlinePlayers()) {
            ColorUtil.sound(p, "entity.ender_dragon.growl", 0.7f, 1.2f);
        }
    }

    public String getNpcCommand(Entity ent) {
        if (ent == null) return null;
        for (String tag : ent.getScoreboardTags()) {
            if (tag.startsWith("kg_npc:")) {
                return tag.substring("kg_npc:".length());
            }
        }
        return null;
    }

    public boolean isAirdropBlock(Location loc) {
        return activeAirdropLoc != null && loc != null && loc.getWorld() != null
                && loc.getWorld().equals(activeAirdropLoc.getWorld())
                && loc.getBlockX() == activeAirdropLoc.getBlockX()
                && loc.getBlockY() == activeAirdropLoc.getBlockY()
                && loc.getBlockZ() == activeAirdropLoc.getBlockZ();
    }

    public void claimAirdrop(Player p) {
        if (activeAirdropLoc == null) return;
        if (airdropUnlockSec > 0) {
            ColorUtil.sound(p, "block.chest.locked", 1f, 1f);
            p.sendMessage(ColorUtil.c("&#FF1744☄ Мистический Сундук ещё закрыт! До открытия: &#FFD740" + airdropUnlockSec + " сек."));
            return;
        }
        clearAirdrop();
        plugin.getStore().addCoins(p.getName(), 35000.0);
        plugin.getStore().addRelics(p.getName(), 75);
        plugin.getStore().addCaseKeys(p.getName(), "summer", 1);
        p.getInventory().addItem(ItemFactory.godAura(2), ItemFactory.trapka(2), new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 4));
        plugin.getStore().save();
        ColorUtil.spawnFirework(p.getLocation());
        ColorUtil.sound(p, "ui.toast.challenge_complete", 1f, 1.1f);
        Bukkit.broadcastMessage(ColorUtil.c("&#FF1744☄ &fИгрок &#00E676" + p.getName()
                + " &fзалутал &cМистический Аирдроп Деда Мороза &f(&#FFEA00+35 000$&f, &#00E5FF+75 ❖&f, Кейс и Топ-Лут)!"));
    }

    public void clearAirdrop() {
        if (airdropHolo != null && airdropHolo.isValid()) airdropHolo.remove();
        airdropHolo = null;
        if (activeAirdropLoc != null && activeAirdropLoc.getWorld() != null) {
            activeAirdropLoc.getBlock().setType(Material.AIR, false);
        }
        activeAirdropLoc = null;
    }

    private void startTickTasks() {
        // Head tracking for NPCs + Snow Particles around Christmas Tree every 6 ticks
        new BukkitRunnable() {
            @Override
            public void run() {
                World w = getSpawnWorld();
                if (w == null || w.getPlayers().isEmpty()) return;
                w.spawnParticle(Particle.SNOWFLAKE, new Location(w, 0.5, 112.0, 0.5), 35, 16.0, 8.0, 16.0, 0.01);
                for (Entity e : managedEntities) {
                    if (e == null || !e.isValid() || !(e instanceof LivingEntity)) continue;
                    Player nearest = null;
                    double best = 100.0;
                    for (Player p : w.getPlayers()) {
                        double d = p.getLocation().distanceSquared(e.getLocation());
                        if (d < best) { best = d; nearest = p; }
                    }
                    if (nearest != null) {
                        Location el = e.getLocation();
                        org.bukkit.util.Vector dir = nearest.getEyeLocation().toVector().subtract(((LivingEntity) e).getEyeLocation().toVector());
                        el.setDirection(dir);
                        e.teleport(el);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 6L);

        // 1-second timer: Auto-Mine, Mystic Airdrop, Smart 1v1 Kabinki doors
        new BukkitRunnable() {
            @Override
            public void run() {
                // Auto-Mine countdown
                mineSecondsLeft--;
                if (mineSecondsLeft <= 0) {
                    refillAutoMine();
                } else if (mineHolo != null && mineHolo.isValid()) {
                    mineHolo.setText(ColorUtil.c("&#FFEE58&l⛏ ЗИМНЯЯ АВТО-ШАХТА ДЕДА МОРОЗА ⛏\n"
                            + "&fКопай руду прямо на спавне и продавай в &e/buyer&f!\n"
                            + "&7Обновление шахты через: &#00E676" + ColorUtil.fmtTime(mineSecondsLeft)));
                }

                // Airdrop timer
                airdropSecondsLeft--;
                if (airdropSecondsLeft <= 0) {
                    airdropSecondsLeft = Math.max(300, plugin.getConfig().getInt("airdrop-interval-minutes", 30) * 60);
                    triggerMysticAirdrop();
                }
                if (activeAirdropLoc != null) {
                    if (airdropUnlockSec > 0) {
                        airdropUnlockSec--;
                        if (airdropHolo != null && airdropHolo.isValid()) {
                            String st = airdropUnlockSec > 0
                                    ? "&fОткрытие через: &#FFD740" + airdropUnlockSec + "с"
                                    : "&#00E676&lОТКРЫТ! НАЖМИ ПКМ ПО СУНДУКУ!";
                            airdropHolo.setText(ColorUtil.c("&#FF1744&l☄ НОВОГОДНИЙ МИСТИЧЕСКИЙ СУНДУК ☄\n" + st
                                    + "\n&7Внутри: Рилики, Сфера, Талисман и Чар-Яблоки!"));
                        }
                    }
                }

                // Smart 1v1 Kabinki doors (X=29, Y=100..101, Z=20 / 27 / 34)
                World w = getSpawnWorld();
                int[] kzs = new int[]{20, 27, 34};
                for (int kz : kzs) {
                    int inside = 0;
                    for (Player p : w.getPlayers()) {
                        Location pl = p.getLocation();
                        if (pl.getBlockX() >= 30 && pl.getBlockX() <= 34
                                && pl.getBlockY() >= 100 && pl.getBlockY() <= 103
                                && pl.getBlockZ() >= kz - 2 && pl.getBlockZ() <= kz + 2) {
                            inside++;
                        }
                    }
                    Material targetDoor = inside >= 2 ? Material.RED_STAINED_GLASS : Material.AIR;
                    Block d1 = w.getBlockAt(29, 100, kz);
                    Block d2 = w.getBlockAt(29, 101, kz);
                    if (d1.getType() != targetDoor) d1.setType(targetDoor, false);
                    if (d2.getType() != targetDoor) d2.setType(targetDoor, false);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }
}
