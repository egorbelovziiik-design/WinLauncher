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
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.inventory.meta.BookMeta;

import java.net.InetAddress;
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
    }

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

    public AntiLagDdosManager(KingGriefCore plugin) {
        this.plugin = plugin;
    }

    public void shutdown() {
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
        WindowCounter wc = map.get(key);
        if (wc == null) {
            wc = new WindowCounter();
            wc.windowStartMs = now;
            wc.count = 1;
            map.put(key, wc);
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
        if (plugin.getSpawnEngine().isInSpawnWorld(b.getLocation())) return;

        long now = System.currentTimeMillis();
        String wName = b.getWorld().getName();
        String cellKey = wName + ":" + (b.getX() >> 3) + ":" + (b.getY() >> 3) + ":" + (b.getZ() >> 3);
        String chunkKey = wName + ":" + (b.getX() >> 4) + ":" + (b.getZ() >> 4);

        int weight = (b.getType() == Material.OBSERVER || b.getType() == Material.COMPARATOR || b.getType() == Material.REPEATER) ? 2 : 1;
        int cellPulses = 0;
        int chunkPulses = 0;
        for (int i = 0; i < weight; i++) {
            cellPulses = incrementWindow(cellRedstoneMap, cellKey, now, 2000L);
            chunkPulses = incrementWindow(chunkRedstoneMap, chunkKey, now, 2000L);
        }

        if (cellPulses >= 36 || chunkPulses >= 85) {
            e.setNewCurrent(0);
            smiteAndDestroyRedstoneMachine(b, chunkKey, now, "Редстоун-клок / Лаг-машина");
        }
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
        String cellKey = wName + ":" + (b.getX() >> 3) + ":" + (b.getY() >> 3) + ":" + (b.getZ() >> 3);
        String chunkKey = wName + ":" + (b.getX() >> 4) + ":" + (b.getZ() >> 4);

        // Detect slime/honey flying lag machines or rapid piston clocks!
        int slimeCount = 0;
        for (Block pushed : e.getBlocks()) {
            Material pm = pushed.getType();
            if (pm == Material.SLIME_BLOCK || pm == Material.HONEY_BLOCK || pm == Material.OBSERVER) {
                slimeCount++;
            }
        }
        int inc = slimeCount >= 2 ? 3 : 1;
        int count = 0;
        for (int i = 0; i < inc; i++) {
            count = incrementWindow(cellPistonMap, cellKey, now, 2500L);
        }
        if (count >= 14) {
            e.setCancelled(true);
            smiteAndDestroyRedstoneMachine(b, chunkKey, now, slimeCount >= 2 ? "Флай-машина / Поршневой лаг-механизм" : "Скоростной поршневой клок");
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
        String cellKey = wName + ":" + (b.getX() >> 3) + ":" + (b.getY() >> 3) + ":" + (b.getZ() >> 3);
        String chunkKey = wName + ":" + (b.getX() >> 4) + ":" + (b.getZ() >> 4);
        int count = incrementWindow(cellPistonMap, cellKey, now, 2500L);
        if (count >= 16) {
            e.setCancelled(true);
            smiteAndDestroyRedstoneMachine(b, chunkKey, now, "Поршневая лаг-машина");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispenseSpam(BlockDispenseEvent e) {
        Block b = e.getBlock();
        if (plugin.getSpawnEngine().isInSpawnWorld(b.getLocation())) return;
        long now = System.currentTimeMillis();
        String wName = b.getWorld().getName();
        String cellKey = wName + ":disp:" + (b.getX() >> 3) + ":" + (b.getY() >> 3) + ":" + (b.getZ() >> 3);
        String chunkKey = wName + ":" + (b.getX() >> 4) + ":" + (b.getZ() >> 4);
        int count = incrementWindow(cellRedstoneMap, cellKey, now, 2000L);
        if (count >= 20) {
            e.setCancelled(true);
            smiteAndDestroyRedstoneMachine(b, chunkKey, now, "Раздатчик-спамер / Лаг-машина");
        }
    }

    /**
     * Сбрасывает настоящую МОЛНИЮ прямо в редстоун-машину и разносит все редстоун/поршни/наблюдатели вокруг!
     */
    public void smiteAndDestroyRedstoneMachine(Block centerBlock, String chunkKey, long now, String kind) {
        Long lastStrike = chunkLightningCd.get(chunkKey);
        if (lastStrike != null && now - lastStrike.longValue() < 2200L) {
            // Even during cooldown, immediately break the offending component silently!
            if (isRedstoneMachinePart(centerBlock.getType())) {
                centerBlock.setType(Material.AIR, false);
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
        int broken = 0;
        int cx = centerBlock.getX(), cy = centerBlock.getY(), cz = centerBlock.getZ();
        for (int dx = -6; dx <= 6; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -6; dz <= 6; dz++) {
                    Block nb = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                    if (plugin.getBossEvents().isProtectedEventStructureBlock(nb, false)) continue;
                    if (isRedstoneMachinePart(nb.getType())) {
                        nb.setType(Material.AIR, false); // Zero item drops -> zero lag & cannot be rebuilt!
                        broken++;
                    }
                }
            }
        }

        String msg = ColorUtil.c("&#FF1744&l⚡ [АНТИ-ЛАГ МОЛНИЯ] &fОбнаружена &#FFD740" + kind
                + " &fна &e" + cx + ", " + cy + ", " + cz
                + " &8— &cудар молнии уничтожил механизм (&f" + broken + " бл.&c)!");

        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean nearby = p.getWorld().equals(w) && p.getLocation().distanceSquared(strikeLoc) <= 1600.0;
            boolean isStaff = plugin.getRanks().getRank(p).weight >= 220;
            if (nearby || isStaff) {
                p.sendMessage(msg);
                if (nearby) {
                    p.sendTitle(ColorUtil.c("&#FF1744&l⚡ УДАР МОЛНИИ!"),
                            ColorUtil.c("&fРедстоун лаг-машины запрещены и уничтожаются!"), 5, 40, 10);
                }
            }
        }
        plugin.getLogger().warning("[AntiLag-Lightning] Destroyed " + kind + " (" + broken + " blocks) at "
                + w.getName() + " " + cx + "," + cy + "," + cz);
    }

    private boolean isRedstoneMachinePart(Material m) {
        return m != null && REDSTONE_MACHINE_PARTS.contains(m.name());
    }

    // Prevent placing excessive Hoppers / Observers / Pistons in a single survival chunk (> 24 per chunk)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLagBlockPlace(BlockPlaceEvent e) {
        Block b = e.getBlock();
        Material m = b.getType();
        if (m != Material.OBSERVER && m != Material.HOPPER && m != Material.PISTON && m != Material.STICKY_PISTON) {
            return;
        }
        Player p = e.getPlayer();
        if ("owner".equalsIgnoreCase(plugin.getRanks().getRank(p).id)) return;
        Chunk ch = b.getChunk();
        int count = 0;
        int minY = Math.max(b.getWorld().getMinHeight(), b.getY() - 16);
        int maxY = Math.min(b.getWorld().getMaxHeight() - 1, b.getY() + 16);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Material cm = ch.getBlock(x, y, z).getType();
                    if (cm == m) {
                        count++;
                        if (count > 28) {
                            e.setCancelled(true);
                            ColorUtil.actionBar(p, "&#FF1744⚡ [Анти-Лаг] Лимит " + m.name() + " в одном чанке (макс. 28 шт.)!");
                            return;
                        }
                    }
                }
            }
        }
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
            e.setMotd(ColorUtil.c("&#80D8FF&l❄ &#FFD740&lKINGGRIEF v6.3 &#80D8FF&l❄ &8│ &#00E676🛡 ANTI-DDOS ЗАЩИТА АКТИВНА\n"
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
        if ("owner".equalsIgnoreCase(plugin.getRanks().getRank(p).id)) return;
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
                p.sendMessage(ColorUtil.c("&#00E676🛡 [KINGGRIEF Anti-DDoS v6.3] &fЗащита активна! TPS: &#00E676"
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
        sender.sendMessage(ColorUtil.c(" &#00E5FF&l🛡 KINGGRIEF ANTI-DDOS & АНТИ-ЛАГ МОЛНИЯ v6.3"));
        sender.sendMessage(ColorUtil.c(" &fСтатус щита: " + (isShieldActive() ? "&#FF1744&lАКТИВНОЕ ОТРАЖЕНИЕ АТАКИ" : "&#00E676&lШТАТНЫЙ РЕЖИМ (ЗАЩИЩЁН)")));
        sender.sendMessage(ColorUtil.c(" &fТекущий TPS: &#00E676" + plugin.getSpawnEngine().getCurrentTps()
                + " &8│ &fПик CPS: &#FFD740" + peakCps + " подкл./сек"));
        sender.sendMessage(ColorUtil.c(" &fОтражено DDoS / Бот-атак: &#FF5252" + blockedBotConnections.get()));
        sender.sendMessage(ColorUtil.c(" &fЗаблокировано Краш-Пакетов: &#FFA726" + blockedExploitPackets.get()));
        sender.sendMessage(ColorUtil.c(" &fСломано Редстоун Лаг-Машин Молнией ⚡: &#FFD740" + destroyedLagMachines.get()));
        sender.sendMessage(ColorUtil.c(" &7Команды (Мл.Админ+): &f/antiddos shield &8│ &f/antiddos smite"));
        sender.sendMessage(ColorUtil.c("&#80D8FF&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        return true;
    }
}
