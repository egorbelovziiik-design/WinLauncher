package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.inventory.EquipmentSlot;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * KINGGRIEF v5.8 — Multi-Layer Anti-DDoS Shield & Redstone Lag-Machine Lightning Destroyer!
 * 1) Strikes REAL lightning onto Redstone / Piston / Observer lag machines and shatters them!
 * 2) Protects server from Bot-Floods, Connection Bursts, Crash-Books, TabComplete & Packet Exploits!
 * 3) Protects all 33,182 real Christmas Tree leaves in spawn_world from ever decaying!
 */
public final class AntiLagDdosManager implements Listener, CommandExecutor {

    private static final Pattern VALID_NICK = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final Set<String> REDSTONE_MACHINE_PARTS = new HashSet<String>();

    static {
        String[] names = new String[]{
                "REDSTONE_WIRE", "REPEATER", "COMPARATOR", "OBSERVER",
                "PISTON", "STICKY_PISTON", "PISTON_HEAD", "MOVING_PISTON",
                "REDSTONE_TORCH", "REDSTONE_WALL_TORCH", "REDSTONE_BLOCK",
                "LEVER", "DISPENSER", "DROPPER", "HOPPER", "TARGET",
                "DAYLIGHT_DETECTOR", "SLIME_BLOCK", "HONEY_BLOCK",
                "TRIPWIRE_HOOK", "STONE_BUTTON", "OAK_BUTTON", "POLISHED_BLACKSTONE_BUTTON"
        };
        for (String n : names) REDSTONE_MACHINE_PARTS.add(n);
        // v6.3.1: добираем ВСЕ виды кнопок и нажимных плит (раньше учитывались только 3 кнопки из 10!)
        try {
            for (Material mm : Material.values()) {
                String n = mm.name();
                if (n.endsWith("_BUTTON") || n.endsWith("_PRESSURE_PLATE")) REDSTONE_MACHINE_PARTS.add(n);
            }
        } catch (Throwable ignored) {}
    }

    // ================= v6.3.1: ПОРОГИ АНТИ-ЛАГ МОЛНИИ ( SpamOnly — одиночное нажатие НЕ наказуемо! ) =====
    /** Сколько переключений рычага/кнопки за окно считается СПАМОМ. */
    private static final int SPAM_TOGGLE_LIMIT = 10;
    /** v6.3.3: 22 -> 30. Обычные прыжки/бег по плите дают 5-7 событий в секунду — это НЕ спам. */
    private static final int SPAM_PLATE_LIMIT = 30;
    private static final long SPAM_WINDOW_MS = 3000L;
    /**
     * v6.3.3: суммарный СПАМ по ЛЮБЫМ рычагам/кнопкам (не по одному блоку). Раньше детектор
     * обходился щелчками по 10 разным рычагам — лаг тот же, а наказания не было.
     */
    private static final int SPAM_ANY_TOGGLE_LIMIT = 20;
    /** Де-дупликация каскада: один и тот же блок даёт не больше 1 импульса за это время. */
    private static final long PULSE_DEDUPE_MS = 300L;
    /** Лимиты импульсов за 2 секунды (одиночный механизм столько НИКОГДА не даёт). */
    private static final int CELL_PULSE_LIMIT = 45;
    private static final int CHUNK_PULSE_LIMIT = 110;
    private static final int PISTON_EXTEND_LIMIT = 16;
    private static final int PISTON_RETRACT_LIMIT = 18;
    private static final int DISPENSE_LIMIT = 22;
    /** Молния бьёт только если перегрузка держится ПОДРЯД в двух окнах (т.е. это реально спам/клок). */
    private static final long OVERLOAD_STREAK_MS = 8000L;
    private static final int LAG_BLOCK_PER_CHUNK_LIMIT = 28;

    private static final class WindowCounter {
        long windowStartMs;
        int count;
    }

    private final KingGriefCore plugin;

    // Async Network & DDoS Protection Counters (Thread-Safe!)
    private final Map<String, WindowCounter> ipBurstMap = new ConcurrentHashMap<String, WindowCounter>();
    private final Map<String, Long> lastConnectMsByKey = new ConcurrentHashMap<String, Long>();
    private volatile long cpsWindowMs = System.currentTimeMillis();
    private final AtomicInteger currentCps = new AtomicInteger(0);
    private volatile int peakCps = 0;
    private volatile long shieldActiveUntilMs = 0L;
    private volatile boolean manualShieldForced = false;

    private final AtomicInteger blockedBotConnections = new AtomicInteger(0);
    private final AtomicInteger blockedExploitPackets = new AtomicInteger(0);
    private final AtomicInteger destroyedLagMachines = new AtomicInteger(0);

    // Main-thread Packet / Spam Throttles
    private final Map<UUID, WindowCounter> cmdRateMap = new ConcurrentHashMap<UUID, WindowCounter>();
    private final Map<UUID, WindowCounter> tabRateMap = new ConcurrentHashMap<UUID, WindowCounter>();
    private final Map<UUID, WindowCounter> dropRateMap = new ConcurrentHashMap<UUID, WindowCounter>();

    // Main-thread Redstone / Piston / Dispenser Lag-Machine Trackers
    private final Map<String, WindowCounter> cellRedstoneMap = new ConcurrentHashMap<String, WindowCounter>();
    private final Map<String, WindowCounter> chunkRedstoneMap = new ConcurrentHashMap<String, WindowCounter>();
    private final Map<String, WindowCounter> cellPistonMap = new ConcurrentHashMap<String, WindowCounter>();
    private final Map<String, Long> chunkLightningCd = new ConcurrentHashMap<String, Long>();
    // v6.3.1: СПАМ-детектор рычагов/кнопок/плит + де-дупликатор каскадов + «подряд идущие» окна перегрузки
    private final Map<String, WindowCounter> toggleSpamMap = new ConcurrentHashMap<String, WindowCounter>();
    private final Map<String, WindowCounter> toggleSpamAnyMap = new ConcurrentHashMap<String, WindowCounter>();
    private final Map<String, Long> lastPulseAtMs = new ConcurrentHashMap<String, Long>();
    private final Map<String, Integer> overloadStreak = new ConcurrentHashMap<String, Integer>();
    private final Map<String, Long> overloadFlaggedAt = new ConcurrentHashMap<String, Long>();
    private final Map<String, Long> lastOverloadWarnAt = new ConcurrentHashMap<String, Long>();
    private final Map<UUID, Long> spamStrikes = new ConcurrentHashMap<UUID, Long>();
    // v6.3.1: O(1)-счётчики лаг-блоков в чанке вместо скана 8448 блоков на каждое размещение!
    private final Map<String, Integer> chunkLagCounts = new ConcurrentHashMap<String, Integer>();
    private BukkitRunnable cleanupTask;

    public AntiLagDdosManager(KingGriefCore plugin) {
        this.plugin = plugin;
    }

    /** v6.3.1: фоновая чистка счётчиков — раньше карты росли вечно (утечка памяти за дни аптайма)! */
    public void start() {
        if (cleanupTask != null) return;
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    long now = System.currentTimeMillis();
                    purgeStale(ipBurstMap, now, 120000L);
                    purgeStale(cmdRateMap, now, 120000L);
                    purgeStale(tabRateMap, now, 120000L);
                    purgeStale(dropRateMap, now, 120000L);
                    purgeStale(cellRedstoneMap, now, 60000L);
                    purgeStale(chunkRedstoneMap, now, 60000L);
                    purgeStale(cellPistonMap, now, 60000L);
                    purgeStale(toggleSpamMap, now, 60000L);
                    purgeStale(toggleSpamAnyMap, now, 60000L);
                    purgeOldValues(lastPulseAtMs, now, 15000L);
                    purgeOldValues(chunkLightningCd, now, 60000L);
                    purgeOldValues(lastOverloadWarnAt, now, 60000L);
                    purgeOldValues(spamStrikes, now, 600000L);
                    purgeOldValues(overloadFlaggedAt, now, 60000L);
                    for (String k : new ArrayList<String>(overloadStreak.keySet())) {
                        Long at = overloadFlaggedAt.get(k);
                        if (at == null || now - at.longValue() > 60000L) overloadStreak.remove(k);
                    }
                    for (String k : new ArrayList<String>(lastConnectMsByKey.keySet())) {
                        Long at = lastConnectMsByKey.get(k);
                        if (at != null && now - at.longValue() > 300000L) lastConnectMsByKey.remove(k);
                    }
                } catch (Throwable ignored) {}
            }
        };
        cleanupTask.runTaskTimer(plugin, 600L, 600L);
    }

    private void purgeStale(Map<?, WindowCounter> map, long now, long maxAgeMs) {
        try {
            for (Object k : new ArrayList<Object>(map.keySet())) {
                WindowCounter wc = map.get(k);
                if (wc == null || now - wc.windowStartMs > maxAgeMs) map.remove(k);
            }
        } catch (Throwable ignored) {}
    }

    private void purgeOldValues(Map<?, Long> map, long now, long maxAgeMs) {
        try {
            for (Object k : new ArrayList<Object>(map.keySet())) {
                Long v = map.get(k);
                if (v == null || now - v.longValue() > maxAgeMs) map.remove(k);
            }
        } catch (Throwable ignored) {}
    }

    public void shutdown() {
        if (cleanupTask != null) {
            try { cleanupTask.cancel(); } catch (Throwable ignored) {}
            cleanupTask = null;
        }
        toggleSpamMap.clear();
        toggleSpamAnyMap.clear();
        lastPulseAtMs.clear();
        overloadStreak.clear();
        overloadFlaggedAt.clear();
        lastOverloadWarnAt.clear();
        spamStrikes.clear();
        chunkLagCounts.clear();
        ipBurstMap.clear();
        lastConnectMsByKey.clear();
        cmdRateMap.clear();
        tabRateMap.clear();
        dropRateMap.clear();
        cellRedstoneMap.clear();
        chunkRedstoneMap.clear();
        cellPistonMap.clear();
        chunkLightningCd.clear();
    }

    private int incrementWindow(Map<String, WindowCounter> map, String key, long now, long windowMs) {
        return incrementWindowObj(map, key, now, windowMs).count;
    }

    /** v6.3.1: возвращает сам счётчик окна — нужно, чтобы отличать «одно окно» от «следующего». */
    private WindowCounter incrementWindowObj(Map<String, WindowCounter> map, String key, long now, long windowMs) {
        WindowCounter wc = map.get(key);
        if (wc == null) {
            wc = new WindowCounter();
            wc.windowStartMs = now;
            wc.count = 1;
            map.put(key, wc);
            return wc;
        }
        if (now - wc.windowStartMs > windowMs) {
            wc.windowStartMs = now;
            wc.count = 1;
            return wc;
        }
        wc.count++;
        return wc;
    }

    private int incrementUuidWindow(Map<UUID, WindowCounter> map, UUID id, long now, long windowMs) {
        WindowCounter wc = map.get(id);
        if (wc == null) {
            wc = new WindowCounter();
            wc.windowStartMs = now;
            wc.count = 1;
            map.put(id, wc);
            return 1;
        }
        if (now - wc.windowStartMs > windowMs) {
            wc.windowStartMs = now;
            wc.count = 1;
            return 1;
        }
        wc.count++;
        return wc.count;
    }

    // =========================================================================
    // 1. LIGHTNING DESTRUCTION OF REDSTONE / PISTON / OBSERVER LAG MACHINES!
    // =========================================================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRedstonePulse(BlockRedstoneEvent e) {
        if (e.getOldCurrent() == e.getNewCurrent()) return;
        Block b = e.getBlock();
        Material m = b.getType();
        // v6.3.1: ПРОВОД НЕ СЧИТАЕМ ВОВСЕ. Одно нажатие рычага рассылает десятки событий по всем
        // сегментам провода (каскад) — именно поэтому молния раньше била по обычным механизмам!
        if (m == Material.REDSTONE_WIRE) return;
        if (plugin.getSpawnEngine().isInSpawnWorld(b.getLocation())) return;

        long now = System.currentTimeMillis();
        String wName = b.getWorld().getName();
        String blockKey = wName + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
        // Де-дупликация каскада: один и тот же блок даёт не больше ОДНОГО импульса за 300 мс.
        Long prevPulse = lastPulseAtMs.get(blockKey);
        if (prevPulse != null && now - prevPulse.longValue() < PULSE_DEDUPE_MS) return;
        lastPulseAtMs.put(blockKey, Long.valueOf(now));

        String cellKey = wName + ":" + (b.getX() >> 3) + ":" + (b.getY() >> 3) + ":" + (b.getZ() >> 3);
        String chunkKey = wName + ":" + (b.getX() >> 4) + ":" + (b.getZ() >> 4);

        int weight = (m == Material.OBSERVER || m == Material.COMPARATOR || m == Material.REPEATER) ? 2 : 1;
        WindowCounter cw = null;
        WindowCounter kw = null;
        for (int i = 0; i < weight; i++) {
            cw = incrementWindowObj(cellRedstoneMap, cellKey, now, 2000L);
            kw = incrementWindowObj(chunkRedstoneMap, chunkKey, now, 2000L);
        }
        int pulses = Math.max(cw.count, kw.count);
        boolean overloaded = (cw.count >= CELL_PULSE_LIMIT) || (kw.count >= CHUNK_PULSE_LIMIT);
        // Одиночное нажатие рычага/кнопки физически не набирает лимит -> НИКАКОГО наказания!
        if (!overloaded) return;

        if (overloadConfirmed(cellKey, cw.windowStartMs, now)) {
            // ПОДТВЕРЖДЁННАЯ перегрузка (2 окна подряд) -> глушим сигнал и бьём молнией.
            e.setNewCurrent(0);
            overloadStreak.remove(cellKey);
            smiteAndDestroyRedstoneMachine(b, chunkKey, now, "Редстоун-клок / Лаг-машина (СПАМ импульсами)");
        } else {
            // v6.3.3: ПЕРВОЕ окно — только предупреждение. Глушим ЛИШЬ авто-клоковые детали,
            // а рычаги/кнопки/плиты игрока НЕ трогаем: одиночное нажатие механизма ОБЯЗАНО
            // сработать — раньше setNewCurrent(0) стоял ДО проверки и ломал обычный механизм.
            if (isClockComponent(m)) e.setNewCurrent(0);
            warnOverload(b, cellKey, now, pulses);
        }
    }

    /**
     * v6.3.3: детали, которые сами себя тактуют (бесконечный клок). Рычаг, кнопка и нажимная
     * плита сюда НЕ входят — их импульс всегда вызван игроком и не может быть «клоком».
     */
    private boolean isClockComponent(Material m) {
        if (m == null) return false;
        return m == Material.OBSERVER || m == Material.REPEATER || m == Material.COMPARATOR
                || m == Material.REDSTONE_TORCH || m == Material.REDSTONE_WALL_TORCH
                || m == Material.PISTON || m == Material.STICKY_PISTON
                || m == Material.DISPENSER || m == Material.DROPPER;
    }

    /**
     * v6.3.1: молния бьёт ТОЛЬКО если перегрузка держится подряд в двух окнах (2 сек каждое),
     * т.е. это реальный спам/клок. Первое окно — только честное предупреждение без разрушений.
     */
    private boolean overloadConfirmed(String streakKey, long windowStartMs, long now) {
        Long flagged = overloadFlaggedAt.get(streakKey);
        if (flagged != null && flagged.longValue() == windowStartMs) return false; // это окно уже учтено
        Integer st = overloadStreak.get(streakKey);
        int v = (st == null || flagged == null || now - flagged.longValue() > OVERLOAD_STREAK_MS) ? 1 : st.intValue() + 1;
        overloadStreak.put(streakKey, Integer.valueOf(v));
        overloadFlaggedAt.put(streakKey, Long.valueOf(windowStartMs));
        return v >= 2;
    }

    /** v6.3.1: предупреждение вместо молнии — искры, звук выгоревшего факела и сообщение всем рядом. */
    private void warnOverload(Block b, String cellKey, long now, int pulses) {
        Long last = lastOverloadWarnAt.get(cellKey);
        if (last != null && now - last.longValue() < 5000L) return;
        lastOverloadWarnAt.put(cellKey, Long.valueOf(now));
        Location loc = b.getLocation().add(0.5, 1.0, 0.5);
        try {
            b.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 16, 0.5, 0.6, 0.5, 0.05);
            b.getWorld().playSound(loc, "minecraft:block.redstone_torch.burnout", 0.9f, 1.0f);
        } catch (Throwable ignored) {}
        String bar = ColorUtil.c("&#FFD740&l⚡ [АНТИ-ЛАГ] &fПерегрузка редстоуна (&e" + pulses
                + " импульсов/2 сек&f)! &#FF1744Прекрати спам — следующим ударом будет МОЛНИЯ!");
        String staffMsg = ColorUtil.c("&#FFD740&l⚡ [АНТИ-ЛАГ] &fПерегрузка редстоуна на &e" + b.getX() + ", "
                + b.getY() + ", " + b.getZ() + " &7(" + pulses + " импульсов/2 сек) — выдано предупреждение.");
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                boolean nearby = p.getWorld().equals(b.getWorld()) && p.getLocation().distanceSquared(loc) <= 1024.0;
                if (nearby) ColorUtil.actionBar(p, bar);
                else if (plugin.getRanks().getRank(p).weight >= 220) p.sendMessage(staffMsg);
            } catch (Throwable ignored) {}
        }
    }

    /**
     * v6.3.1: ГЛАВНОЕ — СПАМ рычагом/кнопкой/нажимной плитой. Молния прилетает именно за СПАМ
     * (10+ переключений за 3 секунды одним игроком), а одиночное нажатие механизма не наказуемо вообще.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleSpam(PlayerInteractEvent e) {
        Action act = e.getAction();
        if (act != Action.RIGHT_CLICK_BLOCK && act != Action.PHYSICAL) return;
        if (act == Action.RIGHT_CLICK_BLOCK) {
            // событие приходит на обе руки — считаем только основную, иначе всё удваивается!
            try {
                EquipmentSlot hand = e.getHand();
                if (hand != null && hand != EquipmentSlot.HAND) return;
            } catch (Throwable ignored) {}
        }
        // v6.3.3: если взаимодействие запретил другой плагин/регион — нажатие ничего не делало,
        // считать его «спамом» нельзя.
        try {
            if (e.useInteractedBlock() == org.bukkit.event.Event.Result.DENY) return;
        } catch (Throwable ignored) {}
        Block b = e.getClickedBlock();
        if (b == null) return;
        Material m = b.getType();
        String mn = m.name();
        boolean leverOrButton = (m == Material.LEVER || mn.endsWith("_BUTTON"));
        boolean plate = mn.endsWith("_PRESSURE_PLATE");
        if (!leverOrButton && !plate) return;
        Player p = e.getPlayer();
        if (plugin.getSpawnEngine().isInSpawnWorld(b.getLocation())) return;
        // OWNER / TECH.ADMIN / Мл.Админ+ (weight>=260) — свои механизмы, без наказания
        try {
            if (plugin.getRanks().getRank(p).weight >= 260) return;
        } catch (Throwable ignored) {}
        long now = System.currentTimeMillis();
        String key = p.getUniqueId() + ":" + b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
        int hits = incrementWindow(toggleSpamMap, key, now, SPAM_WINDOW_MS);
        int limit = plate ? SPAM_PLATE_LIMIT : SPAM_TOGGLE_LIMIT;
        // v6.3.3: плиты в суммарный счётчик НЕ идут — игрок, пробежавший по дорожке из плит,
        // не спамер. Считаем только осознанные клики по рычагам и кнопкам.
        int anyHits = plate ? 0 : incrementWindow(toggleSpamAnyMap, p.getUniqueId().toString(), now, SPAM_WINDOW_MS);
        if (hits >= limit || anyHits >= SPAM_ANY_TOGGLE_LIMIT) {
            int usedHits = (hits >= limit) ? hits : anyHits;
            toggleSpamMap.remove(key); // сбрасываем счётчики после удара
            toggleSpamAnyMap.remove(p.getUniqueId().toString());
            punishToggleSpam(p, b, usedHits, now, plate);
        }
    }

    private void punishToggleSpam(Player p, Block b, int hits, long now, boolean plate) {
        UUID id = p.getUniqueId();
        String chunkKey = b.getWorld().getName() + ":" + (b.getX() >> 4) + ":" + (b.getZ() >> 4);
        Long prev = spamStrikes.get(id);
        int strikeNo = (prev != null && now - prev.longValue() < 300000L) ? 2 : 1;
        spamStrikes.put(id, Long.valueOf(now));
        String kind = (plate ? "СПАМ по нажимной плите (" : "СПАМ рычагом/кнопкой (")
                + hits + " нажатий за 3 сек" + (plate ? "" : ", в т.ч. по разным блокам") + ") — " + p.getName();
        smiteAndDestroyRedstoneMachine(b, chunkKey, now, kind);
        p.sendMessage(ColorUtil.c("&#FF1744&l⚡ [АНТИ-ЛАГ МОЛНИЯ] &fТы &cСПАМИЛ "
                + (plate ? "нажимную плиту" : "рычаг/кнопку") + " &f(&e" + hits
                + " раз за 3 сек&f) — механизм уничтожен молнией! &7Одиночные нажатия НЕ наказываются, "
                + "&7бег по плитам и обычные механизмы — тоже."));
        if (strikeNo >= 2) {
            p.kickPlayer(ColorUtil.c("&#FF1744&l⚡ КИК: СПАМ МЕХАНИЗМАМИ\n\n"
                    + "&fТы дважды за 5 минут спамил рычагом/кнопкой/плитой.\n"
                    + "&7Одиночные нажатия разрешены, СПАМ — запрещён.\n\n&8KINGGRIEF Anti-Lag v6.3.3"));
            Bukkit.broadcastMessage(ColorUtil.c("&#FF1744&l⚡ [АНТИ-ЛАГ] &fИгрок &c" + p.getName()
                    + " &fкикнут за СПАМ рычагом (&e" + hits + " нажатий за 3 сек&e)."));
            return;
        }
        p.sendTitle(ColorUtil.c("&#FF1744&l⚡ МОЛНИЯ ЗА СПАМ!"),
                ColorUtil.c("&fЕщё раз — будет КИК. Одиночные нажатия НЕ наказываются!"), 5, 60, 10);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        Block b = e.getBlock();
        if (plugin.getSpawnEngine().isInSpawnWorld(b.getLocation())) {
            e.setCancelled(true);
            return;
        }
        long now = System.currentTimeMillis();
        String wName = b.getWorld().getName();
        String blockKey = wName + ":p:" + b.getX() + ":" + b.getY() + ":" + b.getZ();
        Long prevPulse = lastPulseAtMs.get(blockKey);
        boolean dup = (prevPulse != null && now - prevPulse.longValue() < 200L);
        lastPulseAtMs.put(blockKey, Long.valueOf(now));

        // Detect slime/honey flying lag machines or rapid piston clocks!
        int slimeCount = 0;
        for (Block pushed : e.getBlocks()) {
            Material pm = pushed.getType();
            if (pm == Material.SLIME_BLOCK || pm == Material.HONEY_BLOCK || pm == Material.OBSERVER) {
                slimeCount++;
            }
        }
        boolean flyMachine = slimeCount >= 2;
        String cellKey = wName + ":" + (b.getX() >> 3) + ":" + (b.getY() >> 3) + ":" + (b.getZ() >> 3);
        String chunkKey = wName + ":" + (b.getX() >> 4) + ":" + (b.getZ() >> 4);

        // Одиночное срабатывание поршня (дверь, ферма) — 1 импульс, никакого наказания.
        if (dup && !flyMachine) return;
        int inc = flyMachine ? 3 : 1;
        WindowCounter wc = null;
        for (int i = 0; i < inc; i++) {
            wc = incrementWindowObj(cellPistonMap, cellKey, now, 2500L);
        }
        if (wc == null || wc.count < PISTON_EXTEND_LIMIT) return;
        e.setCancelled(true);
        // Флай-машина на слизи/мёде запрещена ВСЕГДА -> бьём сразу; обычный спам -> после 2 окон подряд.
        if (flyMachine || overloadConfirmed(cellKey, wc.windowStartMs, now)) {
            overloadStreak.remove(cellKey);
            smiteAndDestroyRedstoneMachine(b, chunkKey, now, flyMachine
                    ? "Флай-машина / Поршневой лаг-механизм" : "Скоростной поршневой клок (СПАМ)");
        } else {
            warnOverload(b, cellKey, now, wc.count);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        Block b = e.getBlock();
        if (plugin.getSpawnEngine().isInSpawnWorld(b.getLocation())) {
            e.setCancelled(true);
            return;
        }
        long now = System.currentTimeMillis();
        String wName = b.getWorld().getName();
        String blockKey = wName + ":pr:" + b.getX() + ":" + b.getY() + ":" + b.getZ();
        Long prevPulse = lastPulseAtMs.get(blockKey);
        boolean dup = (prevPulse != null && now - prevPulse.longValue() < 200L);
        lastPulseAtMs.put(blockKey, Long.valueOf(now));
        if (dup) return;
        String cellKey = wName + ":" + (b.getX() >> 3) + ":" + (b.getY() >> 3) + ":" + (b.getZ() >> 3);
        String chunkKey = wName + ":" + (b.getX() >> 4) + ":" + (b.getZ() >> 4);
        WindowCounter wc = incrementWindowObj(cellPistonMap, cellKey, now, 2500L);
        if (wc.count < PISTON_RETRACT_LIMIT) return;
        e.setCancelled(true);
        if (overloadConfirmed(cellKey, wc.windowStartMs, now)) {
            overloadStreak.remove(cellKey);
            smiteAndDestroyRedstoneMachine(b, chunkKey, now, "Поршневая лаг-машина (СПАМ)");
        } else {
            warnOverload(b, cellKey, now, wc.count);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispenseSpam(BlockDispenseEvent e) {
        Block b = e.getBlock();
        if (plugin.getSpawnEngine().isInSpawnWorld(b.getLocation())) return;
        long now = System.currentTimeMillis();
        String wName = b.getWorld().getName();
        String blockKey = wName + ":d:" + b.getX() + ":" + b.getY() + ":" + b.getZ();
        Long prevPulse = lastPulseAtMs.get(blockKey);
        boolean dup = (prevPulse != null && now - prevPulse.longValue() < 200L);
        lastPulseAtMs.put(blockKey, Long.valueOf(now));
        if (dup) return;
        String cellKey = wName + ":disp:" + (b.getX() >> 3) + ":" + (b.getY() >> 3) + ":" + (b.getZ() >> 3);
        String chunkKey = wName + ":" + (b.getX() >> 4) + ":" + (b.getZ() >> 4);
        WindowCounter wc = incrementWindowObj(cellRedstoneMap, cellKey, now, 2000L);
        if (wc.count < DISPENSE_LIMIT) return;
        e.setCancelled(true);
        if (overloadConfirmed(cellKey, wc.windowStartMs, now)) {
            overloadStreak.remove(cellKey);
            smiteAndDestroyRedstoneMachine(b, chunkKey, now, "Раздатчик-спамер / Лаг-машина (СПАМ)");
        } else {
            warnOverload(b, cellKey, now, wc.count);
        }
    }

    /**
     * Сбрасывает настоящую МОЛНИЮ прямо в редстоун-машину и разносит все редстоун/поршни/наблюдатели вокруг!
     */
    public void smiteAndDestroyRedstoneMachine(Block centerBlock, String chunkKey, long now, String kind) {
        Long lastStrike = chunkLightningCd.get(chunkKey);
        if (lastStrike != null && now - lastStrike.longValue() < 2200L) {
            // Even during cooldown, immediately break the offending component silently!
            // v6.3.3: блоки активной ивентовой структуры не ломаем даже «тихо».
            if (isRedstoneMachinePart(centerBlock.getType())) {
                boolean prot = false;
                try {
                    prot = plugin.getBossEvents().isProtectedEventStructureBlock(centerBlock, false);
                } catch (Throwable ignored) {}
                if (!prot) centerBlock.setType(Material.AIR, false);
            }
            return;
        }
        chunkLightningCd.put(chunkKey, Long.valueOf(now));
        destroyedLagMachines.incrementAndGet();

        World w = centerBlock.getWorld();
        Location strikeLoc = centerBlock.getLocation().add(0.5, 0.5, 0.5);

        // 1. REAL LIGHTNING BOLT + THUNDER + SPARKS!
        w.strikeLightningEffect(strikeLoc);
        try {
            w.spawnParticle(Particle.ELECTRIC_SPARK, strikeLoc, 55, 2.2, 1.5, 2.2, 0.12);
            w.spawnParticle(Particle.LARGE_SMOKE, strikeLoc, 35, 2.0, 1.2, 2.0, 0.05);
            w.spawnParticle(Particle.EXPLOSION, strikeLoc, 4, 1.2, 0.8, 1.2, 0.02);
        } catch (Throwable ignored) {}
        w.playSound(strikeLoc, "minecraft:entity.lightning_bolt.thunder", 2.0f, 0.9f);
        w.playSound(strikeLoc, "minecraft:entity.generic.explode", 1.3f, 1.15f);

        // 2. SHATTER ALL REDSTONE / PISTON / OBSERVER / REPEATER / HOPPER BLOCKS IN 6x5x6 RADIUS!
        // v6.3.3: структуру активного босс-ивента не разносим ВООБЩЕ (молния — эффект, блоки целы).
        boolean centerProtected = false;
        try {
            centerProtected = plugin.getBossEvents().isProtectedEventStructureBlock(centerBlock, false);
        } catch (Throwable ignored) {}
        int broken = 0;
        int cx = centerBlock.getX(), cy = centerBlock.getY(), cz = centerBlock.getZ();
        if (!centerProtected) {
            for (int dx = -6; dx <= 6; dx++) {
                for (int dy = -4; dy <= 4; dy++) {
                    for (int dz = -6; dz <= 6; dz++) {
                        Block nb = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                        // v6.3.3: сначала ДЕШЁВАЯ проверка материала. Раньше isProtectedEventStructureBlock
                        // вызывалась для всех 1521 блоков радиуса (и каждый раз new Location()) —
                        // сам удар молнии сажал TPS сильнее, чем лаг-машина.
                        if (!isRedstoneMachinePart(nb.getType())) continue;
                        if (plugin.getBossEvents().isProtectedEventStructureBlock(nb, false)) continue;
                        nb.setType(Material.AIR, false); // Zero item drops -> zero lag & cannot be rebuilt!
                        broken++;
                    }
                }
            }
        }

        String msg = ColorUtil.c("&#FF1744&l⚡ [АНТИ-ЛАГ МОЛНИЯ] &fОбнаружена &#FFD740" + kind
                + " &fна &e" + cx + ", " + cy + ", " + cz
                + (centerProtected
                        ? " &8— &eмеханизм стоит внутри структуры ивента: блоки НЕ разрушены, только удар молнии."
                        : " &8— &cудар молнии уничтожил механизм (&f" + broken + " бл.&c)!"));

        // v6.3.3: per-player try/catch — один «сложный» игрок больше не обрывает рассылку всем.
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                boolean nearby = p.getWorld().equals(w) && p.getLocation().distanceSquared(strikeLoc) <= 1600.0;
                boolean isStaff = plugin.getRanks().getRank(p).weight >= 220;
                if (nearby || isStaff) {
                    p.sendMessage(msg);
                    if (nearby) {
                        p.sendTitle(ColorUtil.c("&#FF1744&l⚡ УДАР МОЛНИИ!"),
                                ColorUtil.c("&fРедстоун лаг-машины запрещены и уничтожаются!"), 5, 40, 10);
                    }
                }
            } catch (Throwable ignored) {}
        }
        plugin.getLogger().warning("[AntiLag-Lightning] Destroyed " + kind + " (" + broken + " blocks) at "
                + w.getName() + " " + cx + "," + cy + "," + cz);
    }

    private boolean isRedstoneMachinePart(Material m) {
        return m != null && REDSTONE_MACHINE_PARTS.contains(m.name());
    }

    /**
     * v6.3.1: лимит лаг-блоков в чанке БЕЗ скана 8448 блоков на каждое размещение (раньше это
     * само по себе сажало TPS при постройке). Теперь O(1)-счётчики: +1 при установке, -1 при ломании.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLagBlockPlace(BlockPlaceEvent e) {
        Block b = e.getBlock();
        Material m = b.getType();
        if (m != Material.OBSERVER && m != Material.HOPPER && m != Material.PISTON && m != Material.STICKY_PISTON) {
            return;
        }
        Player p = e.getPlayer();
        String rid = plugin.getRanks().getRank(p).id;
        // OWNER и TECH.ADMIN (права уровня OWNER) не ограничиваются
        if ("owner".equalsIgnoreCase(rid) || "techadmin".equalsIgnoreCase(rid)) return;
        if (p.hasPermission("kinggrief.owner")) return;
        if (plugin.getSpawnEngine().isInSpawnWorld(b.getLocation())) return;
        String key = lagKey(b, m);
        Integer cur = chunkLagCounts.get(key);
        int next = (cur == null ? 0 : cur.intValue()) + 1;
        if (next > LAG_BLOCK_PER_CHUNK_LIMIT) {
            e.setCancelled(true);
            ColorUtil.actionBar(p, "&#FF1744⚡ [Анти-Лаг] Лимит " + m.name() + " в одном чанке (макс. "
                    + LAG_BLOCK_PER_CHUNK_LIMIT + " шт.)!");
            return;
        }
        chunkLagCounts.put(key, Integer.valueOf(next));
    }

    /** v6.3.1: сломал лаг-блок -> счётчик чанка уменьшается (иначе лимит «залипал» навсегда). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLagBlockBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Material m = b.getType();
        if (m != Material.OBSERVER && m != Material.HOPPER && m != Material.PISTON && m != Material.STICKY_PISTON) {
            return;
        }
        String key = lagKey(b, m);
        Integer cur = chunkLagCounts.get(key);
        if (cur == null) return;
        if (cur.intValue() <= 1) chunkLagCounts.remove(key);
        else chunkLagCounts.put(key, Integer.valueOf(cur.intValue() - 1));
    }

    private String lagKey(Block b, Material m) {
        return b.getWorld().getName() + ":" + (b.getX() >> 4) + ":" + (b.getZ() >> 4) + ":" + m.name();
    }

    // =========================================================================
    // 2. LEAF DECAY GUARD IN SPAWN_WORLD (REAL CHRISTMAS TREE LEAVES STAY 100% INTACT!)
    // =========================================================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent e) {
        if (plugin.getSpawnEngine().isInSpawnWorld(e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    // =========================================================================
    // 3. ASYNC NETWORK ANTI-DDOS & ANTI-BOT CONNECTION SHIELD!
    // =========================================================================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent e) {
        String name = e.getName();
        long now = System.currentTimeMillis();

        // 1. Strict Nickname Sanitizer (blocks NullPing / Unicode crash bots)
        if (name == null || !VALID_NICK.matcher(name).matches()) {
            blockedBotConnections.incrementAndGet();
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    ColorUtil.c("&#FF1744&l🛡 KINGGRIEF ANTI-DDOS\n\n&fНедопустимый никнейм (&e3–16 символов A-Z, 0-9, _&f)!"));
            return;
        }

        // 2. Global CPS Meter & Automatic 60s Bot-Attack Lockdown Shield
        if (now - cpsWindowMs >= 1000L) {
            cpsWindowMs = now;
            currentCps.set(0);
        }
        int cps = currentCps.incrementAndGet();
        if (cps > peakCps) peakCps = cps;
        if (cps >= 12) {
            shieldActiveUntilMs = now + 60_000L;
        }

        boolean shieldOn = isShieldActive();
        if (shieldOn && !plugin.getStore().isRegistered(name)) {
            if (cps > 4) {
                blockedBotConnections.incrementAndGet();
                e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        ColorUtil.c("&#FF1744&l🛡 KINGGRIEF ANTI-DDOS SHIELD\n\n"
                                + "&fСервер отражает волну ботов!\n"
                                + "&7Подождите &#FFD74010–15 секунд &7и подключитесь снова."));
                return;
            }
        }

        // 3. Per-IP / Per-Nick Reconnect & Burst Limiter (Proxy-safe for Pterodactyl/Velocity!)
        InetAddress addr = e.getAddress();
        boolean isInternalProxy = addr == null || addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isAnyLocalAddress();
        String rateKey = isInternalProxy ? ("nick:" + name.toLowerCase(Locale.ROOT)) : ("ip:" + addr.getHostAddress());

        Long lastConn = lastConnectMsByKey.get(rateKey);
        if (lastConn != null && now - lastConn.longValue() < 1100L) {
            blockedBotConnections.incrementAndGet();
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    ColorUtil.c("&#FF1744&l🛡 АНТИ-БОТ ЗАЩИТА\n\n&fСлишком частое переподключение! Подождите 2 секунды."));
            return;
        }
        lastConnectMsByKey.put(rateKey, Long.valueOf(now));

        if (!isInternalProxy) {
            int ipBurst = incrementWindow(ipBurstMap, rateKey, now, 10_000L);
            if (ipBurst > 6) {
                blockedBotConnections.incrementAndGet();
                e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        ColorUtil.c("&#FF1744&l🛡 KINGGRIEF ANTI-DDOS\n\n&fПревышен лимит подключений с вашего IP! Подождите 10 сек."));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onServerPing(ServerListPingEvent e) {
        if (isShieldActive()) {
            e.setMotd(ColorUtil.c("&#80D8FF&l❄ &#FFD740&lKINGGRIEF v6.3.3 &#80D8FF&l❄ &8│ &#00E676🛡 ANTI-DDOS ЗАЩИТА АКТИВНА\n"
                    + "&#FF408112 Кирок &8• &#FFD740Молот 10×10 &8• &#00E5FFРабочий ТНТ &8• &#AEEA00Новый Спавн"));
        }
    }

    // =========================================================================
    // 4. PACKET / EXPLOIT / CRASH-BOOK / TAB-COMPLETE / BOAT-STACK PROTECTION!
    // =========================================================================
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTabCompleteGuard(TabCompleteEvent e) {
        if (!(e.getSender() instanceof Player)) return;
        Player p = (Player) e.getSender();
        String buf = e.getBuffer() == null ? "" : e.getBuffer().toLowerCase(Locale.ROOT);
        if (buf.length() > 100 || buf.contains("//calc") || buf.contains("//eval") || buf.contains("//solve")
                || buf.contains("targetoffset") || countChar(buf, '[') > 4 || countChar(buf, '{') > 4) {
            e.setCancelled(true);
            blockedExploitPackets.incrementAndGet();
            return;
        }
        int rate = incrementUuidWindow(tabRateMap, p.getUniqueId(), System.currentTimeMillis(), 1000L);
        if (rate > 14) {
            e.setCancelled(true);
            blockedExploitPackets.incrementAndGet();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommandFloodGuard(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        String raw = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        if (raw.startsWith("//calc") || raw.startsWith("//eval") || raw.startsWith("//solve")
                || raw.startsWith("/worldedit:/calc") || raw.contains("${jndi:")) {
            e.setCancelled(true);
            blockedExploitPackets.incrementAndGet();
            p.sendMessage(ColorUtil.c("&#FF1744🛡 [Anti-Crash] Запрещённый эксплойт заблокирован!"));
            return;
        }
        if (plugin.getRanks().isOwnerLevel(p)) return;
        int rate = incrementUuidWindow(cmdRateMap, p.getUniqueId(), System.currentTimeMillis(), 1000L);
        if (rate > 9) {
            e.setCancelled(true);
            blockedExploitPackets.incrementAndGet();
            ColorUtil.actionBar(p, "&#FF1744🛡 Не спамьте командами так быстро!");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBookCrashGuard(PlayerEditBookEvent e) {
        BookMeta bm = e.getNewBookMeta();
        if (bm == null) return;
        if (bm.getPageCount() > 15 || (bm.hasTitle() && bm.getTitle().length() > 32)) {
            e.setCancelled(true);
            blockedExploitPackets.incrementAndGet();
            e.getPlayer().sendMessage(ColorUtil.c("&#FF1744🛡 [Anti-Crash] Слишком объёмная книга заблокирована!"));
            return;
        }
        for (String page : bm.getPages()) {
            if (page != null && page.length() > 450) {
                e.setCancelled(true);
                blockedExploitPackets.incrementAndGet();
                e.getPlayer().sendMessage(ColorUtil.c("&#FF1744🛡 [Anti-Crash] Переполнение страницы книги заблокировано!"));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSignCrashGuard(SignChangeEvent e) {
        for (String line : e.getLines()) {
            if (line != null && line.length() > 48) {
                e.setCancelled(true);
                blockedExploitPackets.incrementAndGet();
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDropFloodGuard(PlayerDropItemEvent e) {
        int rate = incrementUuidWindow(dropRateMap, e.getPlayer().getUniqueId(), System.currentTimeMillis(), 1000L);
        if (rate > 14) {
            e.setCancelled(true);
            blockedExploitPackets.incrementAndGet();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVehicleLagStack(VehicleCreateEvent e) {
        Vehicle v = e.getVehicle();
        int count = 0;
        for (Entity ent : v.getLocation().getChunk().getEntities()) {
            if (ent instanceof Vehicle) count++;
        }
        if (count > 12) {
            e.setCancelled(true);
            v.remove();
            blockedExploitPackets.incrementAndGet();
        }
    }

    private static int countChar(String s, char ch) {
        int c = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == ch) c++;
        }
        return c;
    }

    public boolean isShieldActive() {
        return manualShieldForced || System.currentTimeMillis() < shieldActiveUntilMs;
    }

    public int getDestroyedLagMachines() {
        return destroyedLagMachines.get();
    }

    public int getBlockedBots() {
        return blockedBotConnections.get();
    }

    // =========================================================================
    // 5. COMMAND /antiddos (/ddos, /antilag)
    // =========================================================================
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player p = (Player) sender;
            if (plugin.getRanks().getRank(p).weight < 220) {
                p.sendMessage(ColorUtil.c("&#00E676🛡 [KINGGRIEF Anti-DDoS v6.3.3] &fЗащита активна! TPS: &#00E676"
                        + plugin.getSpawnEngine().getCurrentTps() + " &8│ &fУничтожено лаг-машин молнией: &#FFD740"
                        + destroyedLagMachines.get()));
                return true;
            }
            if (args.length >= 1 && "shield".equalsIgnoreCase(args[0]) && plugin.getRanks().getRank(p).weight >= 260) {
                manualShieldForced = !manualShieldForced;
                p.sendMessage(ColorUtil.c("&#FFD740🛡 [Anti-DDoS] Ручной режим Щита: "
                        + (manualShieldForced ? "&#00E676ВКЛЮЧЁН" : "&#FF5252АВТОМАТИЧЕСКИЙ")));
                return true;
            }
            if (args.length >= 1 && "smite".equalsIgnoreCase(args[0]) && plugin.getRanks().getRank(p).weight >= 260) {
                Block target = p.getTargetBlockExact(25);
                if (target == null) target = p.getLocation().getBlock();
                String ck = target.getWorld().getName() + ":demo:" + System.nanoTime();
                smiteAndDestroyRedstoneMachine(target, ck, System.currentTimeMillis(), "Тест молнии Администратора");
                return true;
            }
        }

        sender.sendMessage(ColorUtil.c("&#80D8FF&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        sender.sendMessage(ColorUtil.c(" &#00E5FF&l🛡 KINGGRIEF ANTI-DDOS & АНТИ-ЛАГ МОЛНИЯ v6.3.3"));
        sender.sendMessage(ColorUtil.c(" &fСтатус щита: " + (isShieldActive() ? "&#FF1744&lАКТИВНОЕ ОТРАЖЕНИЕ АТАКИ" : "&#00E676&lШТАТНЫЙ РЕЖИМ (ЗАЩИЩЁН)")));
        sender.sendMessage(ColorUtil.c(" &fТекущий TPS: &#00E676" + plugin.getSpawnEngine().getCurrentTps()
                + " &8│ &fПик CPS: &#FFD740" + peakCps + " подкл./сек"));
        sender.sendMessage(ColorUtil.c(" &fОтражено DDoS / Бот-атак: &#FF5252" + blockedBotConnections.get()));
        sender.sendMessage(ColorUtil.c(" &fЗаблокировано Краш-Пакетов: &#FFA726" + blockedExploitPackets.get()));
        sender.sendMessage(ColorUtil.c(" &fСломано Редстоун Лаг-Машин Молнией ⚡: &#FFD740" + destroyedLagMachines.get()));
        sender.sendMessage(ColorUtil.c(" &#00E676&l✓ ПРАВИЛО v6.3.3: &fмолния бьёт ТОЛЬКО за &cСПАМ &f— рычаг/кнопка "
                + SPAM_TOGGLE_LIMIT + "+ нажатий за 3 сек по одному блоку, "
                + SPAM_ANY_TOGGLE_LIMIT + "+ нажатий за 3 сек по ЛЮБЫМ рычагам/кнопкам, плита "
                + SPAM_PLATE_LIMIT + "+, или устойчивый редстоун-клок ("
                + CELL_PULSE_LIMIT + "+ импульсов/2 сек подряд в двух окнах)."));
        sender.sendMessage(ColorUtil.c(" &7Одиночное нажатие рычага, обычный механизм и бег по плитам молнией &aНЕ &7наказываются:"));
        sender.sendMessage(ColorUtil.c(" &7первое превышение лимита импульсов — только &eпредупреждение&7, сигнал механизма при этом &aНЕ глушится&7."));
        sender.sendMessage(ColorUtil.c(" &7Команды (Мл.Админ+): &f/antiddos shield &8│ &f/antiddos smite"));
        sender.sendMessage(ColorUtil.c("&#80D8FF&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        return true;
    }
}
