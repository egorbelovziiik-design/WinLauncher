package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class GameplayListener implements Listener {

    private static final NamespacedKey SPAWNER_TYPE_KEY = new NamespacedKey("kinggrief", "spawner_mob");
    private static final NamespacedKey PROJ_TAG_KEY = new NamespacedKey("kinggrief", "proj_type");
    private final KingGriefCore plugin;
    private final Random rng = new Random();
    private final Map<UUID, Long> combatUntil = new HashMap<UUID, Long>();
    private final Set<UUID> drillGuard = new HashSet<UUID>();
    private final Map<UUID, Long> parkourPlateCd = new HashMap<UUID, Long>();
    private final Map<UUID, Long> npcClickCd = new HashMap<UUID, Long>();
    private final Map<String, Long> itemCooldowns = new HashMap<String, Long>();
    private final Set<String> activeTempBlocks = new HashSet<String>();
    private String activeChatAnswer = null;

    private static String blockKey(Block b) {
        return b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }

    public GameplayListener(KingGriefCore plugin) {
        this.plugin = plugin;
        startPassiveArtifactLoop();
        startChatQuizLoop();
    }

    // ================= ПАССИВНЫЕ БАФФЫ ШАРОВ И ТАЛИСМАНОВ РВ В ЛЕВОЙ РУКЕ =================
    private void startPassiveArtifactLoop() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    // Combat actionbar timer
                    Long cu = combatUntil.get(p.getUniqueId());
                    if (cu != null) {
                        if (now < cu.longValue()) {
                            long sec = (cu.longValue() - now) / 1000L + 1L;
                            ColorUtil.actionBar(p, "&#FF1744⚔ РЕЖИМ ПВП: &f" + sec + " сек &7(Не выходите с сервера!)");
                        } else {
                            combatUntil.remove(p.getUniqueId());
                            ColorUtil.actionBar(p, "&#00E676✔ Вы вышли из режима боя!");
                        }
                    }

                    ItemStack off = p.getInventory().getItemInOffHand();
                    String cid = ItemFactory.getCustomId(off);
                    if (cid == null) continue;
                    int dur = 90; // 4.5s refreshed every 2s
                    if ("shar_titana".equals(cid)) {
                        ColorUtil.addPot(p, "health_boost", "HEALTH_BOOST", dur, 1);
                        ColorUtil.addPot(p, "strength", "INCREASE_DAMAGE", dur, 1);
                        ColorUtil.addPot(p, "resistance", "DAMAGE_RESISTANCE", dur, 0);
                        ColorUtil.addPot(p, "fire_resistance", "FIRE_RESISTANCE", dur, 0);
                    } else if ("shar_andromedy".equals(cid)) {
                        ColorUtil.addPot(p, "strength", "INCREASE_DAMAGE", dur, 1);
                        ColorUtil.addPot(p, "speed", "SPEED", dur, 2);
                        ColorUtil.addPot(p, "fire_resistance", "FIRE_RESISTANCE", dur, 0);
                    } else if ("shar_chimery".equals(cid)) {
                        ColorUtil.addPot(p, "strength", "INCREASE_DAMAGE", dur, 1);
                        ColorUtil.addPot(p, "speed", "SPEED", dur, 1);
                        ColorUtil.addPot(p, "haste", "FAST_DIGGING", dur, 1);
                        ColorUtil.addPot(p, "jump_boost", "JUMP", dur, 1);
                    } else if ("shar_apollona".equals(cid)) {
                        ColorUtil.addPot(p, "strength", "INCREASE_DAMAGE", dur, 2);
                        ColorUtil.addPot(p, "speed", "SPEED", dur, 1);
                        ColorUtil.addPot(p, "regeneration", "REGENERATION", dur, 1);
                    } else if ("shar_aida".equals(cid)) {
                        ColorUtil.addPot(p, "strength", "INCREASE_DAMAGE", dur, 2);
                        ColorUtil.addPot(p, "resistance", "DAMAGE_RESISTANCE", dur, 1);
                        ColorUtil.addPot(p, "fire_resistance", "FIRE_RESISTANCE", dur, 0);
                    } else if ("shar_osirisa".equals(cid)) {
                        ColorUtil.addPot(p, "speed", "SPEED", dur, 2);
                        ColorUtil.addPot(p, "strength", "INCREASE_DAMAGE", dur, 1);
                        ColorUtil.addPot(p, "water_breathing", "WATER_BREATHING", dur, 0);
                        ColorUtil.addPot(p, "night_vision", "NIGHT_VISION", 260, 0);
                    } else if ("shar_aresa".equals(cid)) {
                        ColorUtil.addPot(p, "strength", "INCREASE_DAMAGE", dur, 2);
                        ColorUtil.addPot(p, "speed", "SPEED", dur, 1);
                        ColorUtil.addPot(p, "fire_resistance", "FIRE_RESISTANCE", dur, 0);
                    } else if ("shar_santy".equals(cid)) {
                        ColorUtil.addPot(p, "health_boost", "HEALTH_BOOST", dur, 2);
                        ColorUtil.addPot(p, "strength", "INCREASE_DAMAGE", dur, 2);
                        ColorUtil.addPot(p, "speed", "SPEED", dur, 1);
                        ColorUtil.addPot(p, "fire_resistance", "FIRE_RESISTANCE", dur, 0);
                        p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 1.0, 0), 5, 0.4, 0.5, 0.4, 0.01);
                    } else if ("tal_krushitelya".equals(cid)) {
                        ColorUtil.addPot(p, "strength", "INCREASE_DAMAGE", dur, 2);
                        ColorUtil.addPot(p, "speed", "SPEED", dur, 1);
                        ColorUtil.addPot(p, "fire_resistance", "FIRE_RESISTANCE", dur, 0);
                    } else if ("tal_grani".equals(cid)) {
                        ColorUtil.addPot(p, "health_boost", "HEALTH_BOOST", dur, 1);
                        ColorUtil.addPot(p, "resistance", "DAMAGE_RESISTANCE", dur, 1);
                        ColorUtil.addPot(p, "strength", "INCREASE_DAMAGE", dur, 1);
                    } else if ("tal_dedala".equals(cid)) {
                        ColorUtil.addPot(p, "speed", "SPEED", dur, 2);
                        ColorUtil.addPot(p, "strength", "INCREASE_DAMAGE", dur, 1);
                        ColorUtil.addPot(p, "haste", "FAST_DIGGING", dur, 1);
                    } else if ("tal_tritona".equals(cid)) {
                        ColorUtil.addPot(p, "strength", "INCREASE_DAMAGE", dur, 1);
                        ColorUtil.addPot(p, "resistance", "DAMAGE_RESISTANCE", dur, 1);
                        ColorUtil.addPot(p, "dolphins_grace", "DOLPHINS_GRACE", dur, 0);
                        ColorUtil.addPot(p, "water_breathing", "WATER_BREATHING", dur, 0);
                    } else if ("tal_feniksa".equals(cid)) {
                        ColorUtil.addPot(p, "regeneration", "REGENERATION", dur, 1);
                        ColorUtil.addPot(p, "fire_resistance", "FIRE_RESISTANCE", dur, 0);
                    } else if ("tal_echidny".equals(cid)) {
                        ColorUtil.addPot(p, "strength", "INCREASE_DAMAGE", dur, 2);
                    } else if ("tal_stuzhi".equals(cid)) {
                        ColorUtil.addPot(p, "resistance", "DAMAGE_RESISTANCE", dur, 1);
                        ColorUtil.addPot(p, "regeneration", "REGENERATION", dur, 1);
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 40L);
    }

    private void startChatQuizLoop() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (Bukkit.getOnlinePlayers().isEmpty()) return;
                int a = 12 + rng.nextInt(45);
                int b = 11 + rng.nextInt(39);
                activeChatAnswer = String.valueOf(a + b);
                Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l[Викторина РВ] &fКто первым решит в чате: &#80D8FF" + a + " + " + b
                        + " &f— получит &a+7 500$ &fи &b+4 Снежинки ❄&f!"));
            }
        }.runTaskTimer(plugin, 3600L, 7200L);
    }

    // ================= ВХОД / ВЫХОД / РЕСПАВН В МИРЕ СПАВНА =================
    @EventHandler
    public void onJoin(final PlayerJoinEvent e) {
        final Player p = e.getPlayer();
        String ban = plugin.getStore().getActiveBanReason(p.getName());
        if (ban != null) {
            long remSec = plugin.getStore().getBanRemainingSec(p.getName());
            String timeLabel = remSec < 0 ? "Навсегда" : ColorUtil.fmtTime(remSec);
            String staff = plugin.getStore().getBanStaff(p.getName());
            p.kickPlayer(plugin.getModeration().buildBanKickScreen(staff, timeLabel, ban));
            return;
        }
        e.setJoinMessage(null);
        plugin.getStore().ensureProfileInitialized(p);
        plugin.getStore().refreshPermissions(p, plugin.getRanks());
        plugin.getBossEvents().syncBarsForJoin(p);

        // Give menu star in slot 8 if missing
        ItemStack s8 = p.getInventory().getItem(8);
        if (s8 == null || s8.getType() == Material.AIR) {
            p.getInventory().setItem(8, ItemFactory.menuStar());
        }

        // First join or if standing in spawn_world -> ensure clean arrival on spawn_world
        if (!p.hasPlayedBefore() || plugin.getSpawnEngine().isInSpawnWorld(p.getLocation())) {
            p.teleport(plugin.getSpawnEngine().getSpawnArrival());
        }

        // Auto-push Mega Resourcepack v5.0 if server.properties doesn't push it
        final String rpUrl = plugin.getConfig().getString("resourcepack-url", "https://kinggrief.vercel.app/kinggrief-mega-rp.zip");
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) return;
                plugin.getHud().updatePlayerHud(p);
                try {
                    if (plugin.getConfig().getBoolean("push-resourcepack-on-join", true) && rpUrl != null && !rpUrl.isEmpty()) {
                        p.setResourcePack(rpUrl);
                    }
                } catch (Throwable ignored) {}
            }
        }.runTaskLater(plugin, 15L);

        if (!plugin.getStore().isExternalAuthActive()) {
            if (plugin.getStore().trySessionAutoLogin(p)) {
                p.sendMessage(ColorUtil.c("&#00E676✔ Автоматический вход по сессии выполнен! Приятной игры!"));
            } else {
                p.teleport(plugin.getSpawnEngine().getSpawnArrival());
                boolean reg = plugin.getStore().isRegistered(p.getName());
                p.sendTitle(ColorUtil.c("&#80D8FF&l❄ KINGGRIEF v5.0 ❄"),
                        ColorUtil.c(reg ? "&eВведите: &f/login <пароль>" : "&aВведите: &f/register <пароль> <повтор>"), 10, 120, 10);
                p.sendMessage(ColorUtil.c(reg
                        ? "&#FFD740🔐 Авторизуйтесь в чате: &f/login <пароль>"
                        : "&#00E676🔐 Зарегистрируйтесь в чате: &f/register <пароль> <повтор>"));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        e.setQuitMessage(null);
        boolean wasFrozen = plugin.getModeration().isFrozenOrAscending(p);
        plugin.getModeration().handleQuitDuringCheck(p);
        Long cu = combatUntil.remove(p.getUniqueId());
        if (!wasFrozen && cu != null && System.currentTimeMillis() < cu.longValue() && !p.isDead()) {
            p.setHealth(0.0);
            Bukkit.broadcastMessage(ColorUtil.c("&#FF1744⚔ Игрок &f" + p.getName() + " &#FF1744вышел из игры во время боя и был наказан!"));
        }
        plugin.getBossEvents().removeBarsForQuit(p);
        plugin.getStore().removeAttachment(p);
        plugin.getHud().removePlayer(p);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        e.setRespawnLocation(plugin.getSpawnEngine().getSpawnArrival());
    }

    // ================= ЗАЩИТА ДО АВТОРИЗАЦИИ И ЗАМОРОЗКА НА ПРОВЕРКЕ /check =================
    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!plugin.getStore().isAuthenticated(p) || plugin.getModeration().isUnderCheck(p)) {
            if (e.getTo() != null && (e.getFrom().getX() != e.getTo().getX()
                    || e.getFrom().getZ() != e.getTo().getZ()
                    || e.getTo().getY() > e.getFrom().getY())) {
                Location lock = e.getFrom().clone();
                lock.setYaw(e.getTo().getYaw());
                lock.setPitch(e.getTo().getPitch());
                e.setTo(lock);
            }
        }
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player && plugin.getSpawnEngine().isInSpawnWorld(e.getEntity().getLocation())) {
            e.setCancelled(true);
            ((Player) e.getEntity()).setFoodLevel(20);
        }
    }

    @EventHandler
    public void onMobSpawn(CreatureSpawnEvent e) {
        if (plugin.getSpawnEngine().isInSpawnWorld(e.getLocation())) {
            CreatureSpawnEvent.SpawnReason r = e.getSpawnReason();
            if (r != CreatureSpawnEvent.SpawnReason.CUSTOM && r != CreatureSpawnEvent.SpawnReason.COMMAND) {
                e.setCancelled(true);
            }
        }
    }

    // ================= ЖЕЛЕЗОБЕТОННАЯ ЗАЩИТА СПАВНА (ЛОМАТЬ МОЖЕТ ТОЛЬКО OWNER!) =================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!plugin.getStore().isAuthenticated(p) || plugin.getModeration().isFrozenOrAscending(p)) {
            e.setCancelled(true);
            return;
        }
        Block b = e.getBlock();
        if (activeTempBlocks.contains(blockKey(b))) {
            e.setCancelled(true);
            ColorUtil.actionBar(p, "&#A855F7⛓ Временный блок Пласта / Трапки нерушим!");
            return;
        }
        Location loc = b.getLocation();

        // 1. В мире спавна (spawn_world)
        if (plugin.getSpawnEngine().isInSpawnWorld(loc)) {
            // Разрешено копать руду ТОЛЬКО внутри ямы Авто-Шахты (/warp mine)
            if (plugin.getSpawnEngine().isInsideAutoMine(loc)) {
                handleAutoMineBreak(e, p, b);
                return;
            }
            // Во всех остальных точках спавна ломать может СТРОГО ТОЛЬКО OWNER в CREATIVE!
            if (!plugin.getRanks().canModifySpawn(p)) {
                e.setCancelled(true);
                ColorUtil.actionBar(p, "&#FF1744🛡 Ломать спавн может только OWNER! Для выживания: &#00E676/rtp");
                return;
            }
            return;
        }

        // 2. Запрет ломать алтарь Босс-Ивента
        if (plugin.getBossEvents().isLockedEventChest(b)) {
            e.setCancelled(true);
            p.sendMessage(ColorUtil.c("&#FF1744🔒 Сначала убейте Босса Ивента!"));
            return;
        }

        // 3. Проверка приватов в мире выживания
        DataStore.Claim cl = plugin.getStore().getClaimAt(loc);
        if (cl != null) {
            if (cl.isCenter(loc)) {
                if (!cl.owner.equalsIgnoreCase(p.getName()) && !"owner".equals(plugin.getRanks().getRank(p).id)) {
                    e.setCancelled(true);
                    p.sendMessage(ColorUtil.c("&#FF1744🛡 Это центральный блок чужого привата (&f" + cl.owner + "&#FF1744)!"));
                    return;
                }
                plugin.getStore().removeClaim(cl);
                e.setDropItems(false);
                b.getWorld().dropItemNaturally(loc, ItemFactory.claimBlock(b.getType(), cl.radius, "&a🛡 Рудный Приват"));
                p.sendMessage(ColorUtil.c("&#FFD740🛡 Вы сняли свой приват! Блок возвращён."));
                return;
            }
            if (!cl.canAccess(p.getName()) && !"owner".equals(plugin.getRanks().getRank(p).id)) {
                e.setCancelled(true);
                ColorUtil.actionBar(p, "&#FF1744🛡 Территория защищена игроком &f" + cl.owner);
                return;
            }
        }

        ItemStack tool = p.getInventory().getItemInMainHand();
        String toolId = ItemFactory.getCustomId(tool);

        // 4. Божье Прикосновение (добыча Спавнера!)
        if (b.getType() == Material.SPAWNER && "pick_spawner".equals(toolId)) {
            EntityType spawned = EntityType.PIG;
            if (b.getState() instanceof CreatureSpawner) {
                CreatureSpawner cs = (CreatureSpawner) b.getState();
                if (cs.getSpawnedType() != null) spawned = cs.getSpawnedType();
            }
            e.setExpToDrop(0);
            ItemStack drop = ItemFactory.make(Material.SPAWNER, 1,
                    "&#FFD740&l🔥 Рассадник Мобов [" + spawned.name() + "]",
                    Arrays.asList("&7Добыт киркой Божье Прикосновение РВ!", "&7Поставь на базе для спавна мобов."),
                    true, "saved_spawner", null, 0);
            ItemMeta dm = drop.getItemMeta();
            if (dm != null) {
                dm.getPersistentDataContainer().set(SPAWNER_TYPE_KEY, PersistentDataType.STRING, spawned.name());
                drop.setItemMeta(dm);
            }
            b.getWorld().dropItemNaturally(loc.add(0.5, 0.5, 0.5), drop);
            p.getInventory().setItemInMainHand(null);
            ColorUtil.sound(p, "entity.item.break", 1f, 0.9f);
            p.sendMessage(ColorUtil.c("&#00E676✨ Вы успешно добыли спавнер [" + spawned.name() + "] Божьим Прикосновением!"));
            return;
        }

        // 5. Кирка Джамшута (Бульдозер 3×3 + Авто-Плавка!)
        if ("pick_djamshut".equals(toolId) && !drillGuard.contains(p.getUniqueId()) && !p.isSneaking()) {
            drillGuard.add(p.getUniqueId());
            try {
                mine3x3AndSmelt(p, b, tool);
            } finally {
                drillGuard.remove(p.getUniqueId());
            }
        }

        // Job payouts
        plugin.getStore().addMined(p.getName());
        String job = plugin.getStore().getJob(p.getName());
        if ("miner".equals(job) && (b.getType().name().contains("ORE") || b.getType() == Material.STONE)) {
            plugin.getStore().addCoins(p.getName(), 15.0);
        } else if ("lumber".equals(job) && b.getType().name().contains("_LOG")) {
            plugin.getStore().addCoins(p.getName(), 18.0);
        }
    }

    private void handleAutoMineBreak(BlockBreakEvent e, Player p, Block b) {
        Material m = b.getType();
        if (m == Material.COBBLESTONE || m == Material.BEDROCK) {
            e.setCancelled(true);
            return;
        }
        e.setDropItems(false);
        ItemStack drop;
        double coins;
        if (m == Material.COAL_ORE) { drop = new ItemStack(Material.COAL, 2); coins = 25; }
        else if (m == Material.IRON_ORE) { drop = new ItemStack(Material.IRON_INGOT, 1); coins = 55; }
        else if (m == Material.GOLD_ORE) { drop = new ItemStack(Material.GOLD_INGOT, 1); coins = 85; }
        else if (m == Material.LAPIS_ORE) { drop = new ItemStack(Material.LAPIS_LAZULI, 4); coins = 60; }
        else if (m == Material.REDSTONE_ORE) { drop = new ItemStack(Material.REDSTONE, 4); coins = 60; }
        else if (m == Material.DIAMOND_ORE) { drop = new ItemStack(Material.DIAMOND, 1); coins = 190; }
        else if (m == Material.EMERALD_ORE) { drop = new ItemStack(Material.EMERALD, 1); coins = 240; }
        else if (m == Material.ANCIENT_DEBRIS) { drop = new ItemStack(Material.NETHERITE_SCRAP, 1); coins = 900; }
        else { drop = new ItemStack(Material.COBBLESTONE, 1); coins = 8; }

        p.getInventory().addItem(drop);
        p.giveExp(4);
        plugin.getStore().addCoins(p.getName(), coins);
        plugin.getStore().addMined(p.getName());
        if (rng.nextInt(100) < 22) {
            plugin.getStore().addSnowflakes(p.getName(), 1);
            ColorUtil.actionBar(p, "&#FF9100⛏ +" + (int) coins + "$ &8| &#80D8FF+1 Снежинка Джамшута ❄!");
        } else {
            ColorUtil.actionBar(p, "&#FF9100⛏ Авто-Шахта: &a+" + (int) coins + "$");
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                b.setType(Material.COBBLESTONE, false);
            }
        }.runTaskLater(plugin, 1L);
    }

    private void mine3x3AndSmelt(Player p, Block center, ItemStack tool) {
        World w = center.getWorld();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    // Mine 3x3 vertical or horizontal slice depending on pitch
                    if (Math.abs(p.getLocation().getPitch()) > 50f) {
                        if (dy != 0) continue;
                    } else {
                        Vector dir = p.getLocation().getDirection();
                        if (Math.abs(dir.getX()) > Math.abs(dir.getZ())) {
                            if (dx != 0) continue;
                        } else {
                            if (dz != 0) continue;
                        }
                    }
                    Block t = w.getBlockAt(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    Material m = t.getType();
                    if (m == Material.AIR || m == Material.BEDROCK || m == Material.OBSIDIAN || m == Material.SPAWNER || m == Material.CHEST) continue;
                    DataStore.Claim cl = plugin.getStore().getClaimAt(t.getLocation());
                    if (cl != null && (!cl.canAccess(p.getName()) || cl.isCenter(t.getLocation()))) continue;
                    if (m == Material.IRON_ORE || m == Material.DEEPSLATE_IRON_ORE) {
                        t.setType(Material.AIR);
                        w.dropItemNaturally(t.getLocation(), new ItemStack(Material.IRON_INGOT, 1));
                    } else if (m == Material.GOLD_ORE || m == Material.DEEPSLATE_GOLD_ORE) {
                        t.setType(Material.AIR);
                        w.dropItemNaturally(t.getLocation(), new ItemStack(Material.GOLD_INGOT, 1));
                    } else if (!t.equals(center)) {
                        t.breakNaturally(tool);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (!plugin.getStore().isAuthenticated(p)) { e.setCancelled(true); return; }
        Block b = e.getBlockPlaced();
        if (plugin.getSpawnEngine().isInSpawnWorld(b.getLocation())) {
            if (!plugin.getRanks().canModifySpawn(p)) {
                e.setCancelled(true);
                ColorUtil.actionBar(p, "&#FF1744🛡 Строить на спавне может только OWNER! Выживание: &#00E676/rtp");
            }
            return;
        }
        DataStore.Claim cl = plugin.getStore().getClaimAt(b.getLocation());
        if (cl != null && !cl.canAccess(p.getName()) && !"owner".equals(plugin.getRanks().getRank(p).id)) {
            e.setCancelled(true);
            ColorUtil.actionBar(p, "&#FF1744🛡 Чужая территория (&f" + cl.owner + "&#FF1744)!");
            return;
        }

        // Установка добытого Спавнера
        ItemStack inHand = e.getItemInHand();
        String cid = ItemFactory.getCustomId(inHand);
        if ("saved_spawner".equals(cid) && b.getState() instanceof CreatureSpawner && inHand.hasItemMeta()) {
            String mobStr = inHand.getItemMeta().getPersistentDataContainer().get(SPAWNER_TYPE_KEY, PersistentDataType.STRING);
            if (mobStr != null) {
                try {
                    CreatureSpawner cs = (CreatureSpawner) b.getState();
                    cs.setSpawnedType(EntityType.valueOf(mobStr));
                    cs.update(true, false);
                } catch (Throwable ignored) {}
            }
            return;
        }

        // Установка Рудного Привата ReallyWorld
        if (cid != null && cid.startsWith("claim_")) {
            int rad = Integer.parseInt(cid.substring(6));
            int max = plugin.getRanks().getRank(p).maxClaims;
            if (plugin.getStore().getClaimsOf(p.getName()).size() >= max) {
                e.setCancelled(true);
                p.sendMessage(ColorUtil.c("&#FF1744✖ Достигнут лимит приватов вашей привилегии (&f" + max + "&#FF1744)!"));
                return;
            }
            if (plugin.getStore().overlapsAnyClaim(b.getWorld().getName(), b.getX(), b.getZ(), rad)) {
                e.setCancelled(true);
                p.sendMessage(ColorUtil.c("&#FF1744✖ Рядом уже есть другой приват!"));
                return;
            }
            plugin.getStore().createClaim(p.getName(), b.getLocation(), rad);
            int sz = rad * 2 + 1;
            ColorUtil.sound(p, "block.beacon.activate", 1f, 1.2f);
            p.sendMessage(ColorUtil.c("&#00E676🛡 Территория &e" + sz + "×" + sz + " &#00E676успешно запривачена! (/rg info, /rg add <ник>)"));
        }
    }

    @EventHandler public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (plugin.getSpawnEngine().isInSpawnWorld(e.getBlock().getLocation()) && !plugin.getRanks().canModifySpawn(e.getPlayer())) {
            e.setCancelled(true);
        }
    }
    @EventHandler public void onBucketFill(PlayerBucketFillEvent e) {
        if (plugin.getSpawnEngine().isInSpawnWorld(e.getBlock().getLocation()) && !plugin.getRanks().canModifySpawn(e.getPlayer())) {
            e.setCancelled(true);
        }
    }
    @EventHandler public void onEntityExplode(EntityExplodeEvent e) {
        if (plugin.getSpawnEngine().isInSpawnWorld(e.getLocation())) e.blockList().clear();
    }
    @EventHandler public void onBlockExplode(BlockExplodeEvent e) {
        if (plugin.getSpawnEngine().isInSpawnWorld(e.getBlock().getLocation())) e.blockList().clear();
    }
    @EventHandler public void onIgnite(BlockIgniteEvent e) {
        if (plugin.getSpawnEngine().isInSpawnWorld(e.getBlock().getLocation())
                && (e.getPlayer() == null || !plugin.getRanks().canModifySpawn(e.getPlayer()))) {
            e.setCancelled(true);
        }
    }
    @EventHandler public void onBurn(BlockBurnEvent e) {
        if (plugin.getSpawnEngine().isInSpawnWorld(e.getBlock().getLocation())) e.setCancelled(true);
    }
    @EventHandler public void onArmorStandManip(PlayerArmorStandManipulateEvent e) {
        if (e.getRightClicked().getScoreboardTags().contains("kg_holo")
                || (plugin.getSpawnEngine().isInSpawnWorld(e.getRightClicked().getLocation()) && !plugin.getRanks().canModifySpawn(e.getPlayer()))) {
            e.setCancelled(true);
        }
    }
    @EventHandler public void onHangingBreak(HangingBreakByEntityEvent e) {
        if (plugin.getSpawnEngine().isInSpawnWorld(e.getEntity().getLocation())) {
            if (!(e.getRemover() instanceof Player) || !plugin.getRanks().canModifySpawn((Player) e.getRemover())) {
                e.setCancelled(true);
            }
        }
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpen(InventoryOpenEvent e) {
        // NEVER allow vanilla Villager/Merchant trade GUI anywhere on spawn_world!
        if (e.getInventory().getType() == InventoryType.MERCHANT
                && plugin.getSpawnEngine().isInSpawnWorld(e.getPlayer().getLocation())) {
            e.setCancelled(true);
            return;
        }
        if (e.getInventory().getLocation() != null) {
            Block b = e.getInventory().getLocation().getBlock();
            if (plugin.getSpawnEngine().getCasePedestalAt(b) != null) {
                e.setCancelled(true);
            }
        }
    }

    // ================= КЛИКИ ПО ШАЛКЕР-КЕЙСАМ, КАЗИНО, ПАРКУРУ И КАСТОМНЫМ ВЕЩАМ РВ =================
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!plugin.getStore().isAuthenticated(p)) { e.setCancelled(true); return; }

        // 1. Нажимные плиты Радужного Паркура v5.0 (40 островов: #10/#20/#30 Золотая плита = Чекпоинт, #39 Железная плита Y=131 = Финиш!)
        if (e.getAction() == Action.PHYSICAL && e.getClickedBlock() != null
                && plugin.getSpawnEngine().isInSpawnWorld(e.getClickedBlock().getLocation())) {
            Block pb = e.getClickedBlock();
            if (pb.getY() >= 108) {
                long now = System.currentTimeMillis();
                Long last = parkourPlateCd.get(p.getUniqueId());
                if (last != null && now - last.longValue() < 2000L) return;
                parkourPlateCd.put(p.getUniqueId(), Long.valueOf(now));
                if (pb.getType() == Material.LIGHT_WEIGHTED_PRESSURE_PLATE) {
                    plugin.getStore().setParkourCp(p, pb.getLocation().add(0.5, 0.2, 0.5));
                    ColorUtil.sound(p, "entity.experience_orb.pickup", 1f, 1.3f);
                    p.sendMessage(ColorUtil.c("&#40C4FF🏃 Чекпоинт паркура сохранён! Вернуться при падении: &f/parkour cp"));
                } else if (pb.getType() == Material.HEAVY_WEIGHTED_PRESSURE_PLATE && pb.getY() >= 129) {
                    plugin.getStore().setParkourCp(p, null);
                    long ready = plugin.getStore().getParkourRewardReadyAt(p.getName());
                    if (now >= ready) {
                        plugin.getStore().setParkourRewardReadyAt(p.getName(), now + 900_000L);
                        plugin.getStore().addCoins(p.getName(), 25000);
                        plugin.getStore().addRelics(p.getName(), 15);
                        plugin.getStore().addSnowflakes(p.getName(), 15);
                        plugin.getStore().unlockTitle(p.getName(), "parkour_pro");
                        plugin.getStore().save();
                        ColorUtil.spawnFirework(p.getLocation());
                        Bukkit.broadcastMessage(ColorUtil.c("&#40C4FF&l🏃 [ПАРКУР] &fИгрок &#00E676" + p.getName()
                                + " &fпрошёл Радужный Паркур (40 островов) и получил &a+25 000$&f, &b+15❖&f, &b+15❄ &fи титул &b[Паркурист]&f!"));
                    } else {
                        p.sendMessage(ColorUtil.c("&#40C4FF🏃 Вы снова прошли паркур! Повторная награда через " + ColorUtil.fmtTime((ready - now) / 1000L)));
                    }
                    p.teleport(plugin.getSpawnEngine().getSpawnArrival());
                }
            }
            return;
        }

        if (e.getHand() == EquipmentSlot.OFF_HAND) return;

        // 2. Шалкер-Кейсы, Запечатанные Сундуки Боссов и Автоматы Казино
        Block cb = e.getClickedBlock();
        if (cb != null) {
            SpawnEngine.CasePedestal cp = plugin.getSpawnEngine().getCasePedestalAt(cb);
            if (cp != null) {
                e.setCancelled(true);
                if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
                    plugin.getGuis().openCasesMenu(p);
                } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    plugin.getSpawnEngine().openShulkerCaseAnimated(p, cp);
                }
                return;
            }
            if (plugin.getBossEvents().isLockedEventChest(cb)) {
                e.setCancelled(true);
                ColorUtil.sound(p, "block.chest.locked", 1f, 1f);
                p.sendMessage(ColorUtil.c("&#FF1744🔒 Мифический Сундук запечатан! Убейте Босса рядом, чтобы открыть его!"));
                return;
            }
            if (plugin.getSpawnEngine().isInSpawnWorld(cb.getLocation())
                    && (cb.getType() == Material.LEVER || cb.getType().name().endsWith("_BUTTON"))
                    && cb.getX() <= -45 && cb.getZ() >= 30) {
                e.setCancelled(true);
                plugin.getGuis().openCasinoMenu(p);
                return;
            }
        }

        // 3. Звезда Меню, 8 Боевых Расходников FT×RW (Пласт, Трапка, Дезориентация, Явная Пыль, Божья Аура, Смерч, Граната) и Предметы РВ
        ItemStack hand = e.getItem();
        String cid = ItemFactory.getCustomId(hand);
        if (cid == null) return;
        boolean rightClick = e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK;
        if (!rightClick) return;

        if ("menu_star".equals(cid)) {
            e.setCancelled(true);
            plugin.getGuis().openMainMenu(p);
        } else if ("xp_orb_100".equals(cid)) {
            e.setCancelled(true);
            consumeOne(p, hand);
            p.setLevel(p.getLevel() + 30);
            ColorUtil.sound(p, "entity.player.levelup", 1f, 1.3f);
            p.sendMessage(ColorUtil.c("&#69F0AE✨ Пузырёк Джамшута начислил вам &a+30 уровней опыта&7!"));
        } else if ("ny_mandarin".equals(cid)) {
            e.setCancelled(true);
            consumeOne(p, hand);
            p.setFoodLevel(20);
            p.setSaturation(20f);
            p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + 8.0));
            ColorUtil.addPot(p, "speed", "SPEED", 300, 1);
            ColorUtil.addPot(p, "regeneration", "REGENERATION", 200, 1);
            ColorUtil.sound(p, "entity.generic.eat", 1f, 1.1f);
            p.sendMessage(ColorUtil.c("&#FF9100🍊 Вы съели Новогодний Мандарин (+4❤ и Скорость II)!"));
        } else if ("mystery_egg".equals(cid) && cb != null && cb.getType() == Material.SPAWNER) {
            e.setCancelled(true);
            DataStore.Claim cl = plugin.getStore().getClaimAt(cb.getLocation());
            if (cl != null && !cl.canAccess(p.getName())) return;
            consumeOne(p, hand);
            EntityType[] pool = new EntityType[]{EntityType.COW, EntityType.CREEPER, EntityType.BLAZE, EntityType.SKELETON, EntityType.ZOMBIE};
            EntityType pick = pool[rng.nextInt(pool.length)];
            if (cb.getState() instanceof CreatureSpawner) {
                CreatureSpawner cs = (CreatureSpawner) cb.getState();
                cs.setSpawnedType(pick);
                cs.update(true, false);
                ColorUtil.sound(p, "entity.ender_dragon.flap", 1f, 1.2f);
                p.sendMessage(ColorUtil.c("&#E040FB🥚 Загадочное Яйцо изменило спавнер на: &f" + pick.name() + "&7!"));
            }
        } else if ("plast".equals(cid) || "trapka".equals(cid) || "dezorient".equals(cid)
                || "yavnaya_pyl".equals(cid) || "bozhya_aura".equals(cid)
                || "ognenny_smerch".equals(cid) || "pautinnaya_granata".equals(cid)) {
            e.setCancelled(true);
            handleFtConsumable(p, hand, cid);
        }
    }

    private boolean checkAndSetCd(Player p, String key, int sec) {
        String k = p.getUniqueId() + ":" + key;
        long now = System.currentTimeMillis();
        Long until = itemCooldowns.get(k);
        if (until != null && now < until.longValue()) {
            long left = (until.longValue() - now) / 1000L + 1L;
            ColorUtil.actionBar(p, "&#FF5252⏳ Перезарядка предмета: &f" + left + " сек.");
            return false;
        }
        itemCooldowns.put(k, Long.valueOf(now + sec * 1000L));
        return true;
    }

    private boolean isEnemyTarget(Player caster, Player other) {
        if (other == null || other.equals(caster) || other.getGameMode() == GameMode.SPECTATOR) return false;
        if (plugin.getSpawnEngine().isInSpawnWorld(other.getLocation())
                && !plugin.getSpawnEngine().isInsidePvpZone(other.getLocation())) return false;
        DataStore.Clan c1 = plugin.getStore().getClanOf(caster.getName());
        DataStore.Clan c2 = plugin.getStore().getClanOf(other.getName());
        return !(c1 != null && c1.equals(c2) && !c1.friendlyFire);
    }

    private void handleFtConsumable(final Player p, ItemStack hand, String cid) {
        // Disallow building consumables on peaceful spawn plaza (allowed in world AND in /warp pvp pit / Kabinki!)
        if (plugin.getSpawnEngine().isInSpawnWorld(p.getLocation())
                && !plugin.getSpawnEngine().isInsidePvpZone(p.getLocation())) {
            p.sendMessage(ColorUtil.c("&#FF1744✖ Расходники FT можно использовать в мире выживания (&a/rtp&#FF1744) или на &e/warp pvp&#FF1744!"));
            return;
        }
        if ("plast".equals(cid)) {
            if (!checkAndSetCd(p, "plast", 10)) return;
            consumeOne(p, hand);
            spawnTemporaryPlast(p);
        } else if ("trapka".equals(cid)) {
            if (!checkAndSetCd(p, "trapka", 20)) return;
            consumeOne(p, hand);
            spawnTemporaryTrapka(p);
        } else if ("dezorient".equals(cid)) {
            if (!checkAndSetCd(p, "dezorient", 20)) return;
            consumeOne(p, hand);
            p.getWorld().spawnParticle(Particle.WITCH, p.getLocation().add(0, 1, 0), 80, 4.0, 1.5, 4.0, 0.05);
            ColorUtil.sound(p, "entity.elder_guardian.curse", 1f, 1.1f);
            int hit = 0;
            for (Entity en : p.getNearbyEntities(10, 6, 10)) {
                if (en instanceof Player && isEnemyTarget(p, (Player) en)) {
                    Player v = (Player) en;
                    ColorUtil.addPot(v, "blindness", "BLINDNESS", 140, 0);
                    ColorUtil.addPot(v, "nausea", "CONFUSION", 140, 1);
                    ColorUtil.addPot(v, "slowness", "SLOW", 140, 1);
                    v.sendMessage(ColorUtil.c("&#E040FB👁 Вы поражены Дезориентацией игрока &f" + p.getName() + "&#E040FB!"));
                    hit++;
                }
            }
            p.sendMessage(ColorUtil.c("&#E040FB👁 Дезориентация активирована! Поражено врагов: &f" + hit));
        } else if ("yavnaya_pyl".equals(cid)) {
            if (!checkAndSetCd(p, "yavnaya_pyl", 15)) return;
            consumeOne(p, hand);
            p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 90, 6.0, 2.0, 6.0, 0.05);
            ColorUtil.sound(p, "block.amethyst_block.chime", 1f, 1.3f);
            int rev = 0;
            for (Entity en : p.getNearbyEntities(25, 12, 25)) {
                if (en instanceof Player && !en.equals(p)) {
                    Player v = (Player) en;
                    org.bukkit.potion.PotionEffectType invT = ColorUtil.potType("invisibility", "INVISIBILITY");
                    if (invT != null) v.removePotionEffect(invT);
                    ColorUtil.addPot(v, "glowing", "GLOWING", 300, 0);
                    rev++;
                }
            }
            p.sendMessage(ColorUtil.c("&#00E5FF✨ Явная Пыль подсветила игроков вокруг (&f" + rev + " шт.&#00E5FF)!"));
        } else if ("bozhya_aura".equals(cid)) {
            if (!checkAndSetCd(p, "bozhya_aura", 30)) return;
            consumeOne(p, hand);
            String[][] bads = new String[][]{
                    {"poison","POISON"}, {"wither","WITHER"}, {"blindness","BLINDNESS"},
                    {"weakness","WEAKNESS"}, {"slowness","SLOW"}, {"mining_fatigue","SLOW_DIGGING"}, {"nausea","CONFUSION"}
            };
            for (String[] b : bads) {
                org.bukkit.potion.PotionEffectType t = ColorUtil.potType(b[0], b[1]);
                if (t != null) p.removePotionEffect(t);
            }
            p.setFireTicks(0);
            p.setFreezeTicks(0);
            ColorUtil.addPot(p, "regeneration", "REGENERATION", 120, 2);
            ColorUtil.addPot(p, "resistance", "DAMAGE_RESISTANCE", 120, 1);
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 50, 0.8, 1.0, 0.8, 0.15);
            ColorUtil.sound(p, "item.totem.use", 0.9f, 1.35f);
            p.sendMessage(ColorUtil.c("&#FFD740☀ Божья Аура сняла все дебаффы и дала Регенерацию III + Сопротивление II!"));
        } else if ("ognenny_smerch".equals(cid)) {
            if (!checkAndSetCd(p, "ognenny_smerch", 18)) return;
            consumeOne(p, hand);
            p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 110, 3.5, 1.5, 3.5, 0.08);
            ColorUtil.sound(p, "entity.blaze.shoot", 1f, 0.9f);
            for (Entity en : p.getNearbyEntities(8, 5, 8)) {
                if (en instanceof Player && isEnemyTarget(p, (Player) en)) {
                    Player v = (Player) en;
                    v.setFireTicks(140);
                    v.damage(10.0, p);
                    Vector kb = v.getLocation().toVector().subtract(p.getLocation().toVector());
                    if (kb.lengthSquared() < 0.01) kb = new Vector(0.5, 0.4, 0.5);
                    v.setVelocity(kb.normalize().multiply(1.25).setY(0.48));
                }
            }
            p.sendMessage(ColorUtil.c("&#FF6D00🌪 Огненный Смерч обрушился на врагов вокруг!"));
        } else if ("pautinnaya_granata".equals(cid)) {
            if (!checkAndSetCd(p, "pautinnaya_granata", 15)) return;
            consumeOne(p, hand);
            org.bukkit.entity.Snowball proj = p.launchProjectile(org.bukkit.entity.Snowball.class);
            proj.setItem(new ItemStack(Material.SLIME_BALL));
            proj.getPersistentDataContainer().set(PROJ_TAG_KEY, PersistentDataType.STRING, "web_grenade");
            ColorUtil.sound(p, "entity.snowball.throw", 1f, 0.8f);
        }
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player)) return;
        Player p = (Player) e.getEntity().getShooter();
        ItemStack main = p.getInventory().getItemInMainHand();
        if ("snezok_zamorozki".equals(ItemFactory.getCustomId(main))) {
            e.getEntity().getPersistentDataContainer().set(PROJ_TAG_KEY, PersistentDataType.STRING, "frost_ball");
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        String ptype = e.getEntity().getPersistentDataContainer().get(PROJ_TAG_KEY, PersistentDataType.STRING);
        if (ptype == null) return;
        if ("frost_ball".equals(ptype) && e.getHitEntity() instanceof Player && e.getEntity().getShooter() instanceof Player) {
            Player shooter = (Player) e.getEntity().getShooter();
            Player hit = (Player) e.getHitEntity();
            if (isEnemyTarget(shooter, hit)) {
                hit.setFreezeTicks(200);
                ColorUtil.addPot(hit, "slowness", "SLOW", 120, 3);
                ColorUtil.addPot(hit, "weakness", "WEAKNESS", 120, 1);
                hit.getWorld().spawnParticle(Particle.SNOWFLAKE, hit.getLocation().add(0, 1, 0), 40, 0.6, 0.8, 0.6, 0.03);
                hit.sendMessage(ColorUtil.c("&#80D8FF❄ Вы заморожены Снежком Заморозки от &f" + shooter.getName() + "&#80D8FF!"));
            }
        } else if ("web_grenade".equals(ptype)) {
            Location center = e.getHitBlock() != null ? e.getHitBlock().getLocation().add(0, 1, 0)
                    : (e.getHitEntity() != null ? e.getHitEntity().getLocation() : e.getEntity().getLocation());
            if (plugin.getSpawnEngine().isInSpawnWorld(center) && !plugin.getSpawnEngine().isInsidePvpZone(center)) return;
            final java.util.List<Block> placed = new java.util.ArrayList<Block>();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block b = center.getWorld().getBlockAt(center.getBlockX() + dx, center.getBlockY(), center.getBlockZ() + dz);
                    if (b.getType() == Material.AIR) {
                        b.setType(Material.COBWEB, false);
                        placed.add(b);
                    }
                }
            }
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (Block b : placed) {
                        if (b.getType() == Material.COBWEB) b.setType(Material.AIR, false);
                    }
                }
            }.runTaskLater(plugin, 160L);
        }
    }

    private boolean isReplaceableForTemp(Block b) {
        Material m = b.getType();
        if (activeTempBlocks.contains(blockKey(b))) return false;
        if (m == Material.BEDROCK || m == Material.SPAWNER || m == Material.END_PORTAL_FRAME
                || m == Material.CHEST || m == Material.ENDER_CHEST || m.name().endsWith("SHULKER_BOX")) return false;
        return m == Material.AIR || m == Material.CAVE_AIR || m == Material.WATER || m == Material.LAVA
                || m == Material.SHORT_GRASS || m == Material.TALL_GRASS || m == Material.SNOW || !m.isSolid();
    }

    private void spawnTemporaryPlast(Player p) {
        Location pl = p.getLocation();
        World w = pl.getWorld();
        final Map<Block, org.bukkit.block.data.BlockData> saved = new HashMap<Block, org.bukkit.block.data.BlockData>();
        float pitch = pl.getPitch();

        if (Math.abs(pitch) > 42f) {
            // Horizontal 5x5x2 floor/ceiling!
            int baseY = pitch > 0 ? pl.getBlockY() - 1 : pl.getBlockY() + 3;
            int cx = pl.getBlockX(), cz = pl.getBlockZ();
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = 0; dy > -2; dy--) {
                        Block b = w.getBlockAt(cx + dx, baseY + dy, cz + dz);
                        if (isReplaceableForTemp(b)) {
                            saved.put(b, b.getBlockData().clone());
                            activeTempBlocks.add(blockKey(b));
                            b.setType(((dx + dz) % 2 == 0) ? Material.OBSIDIAN : Material.CRYING_OBSIDIAN, false);
                        }
                    }
                }
            }
        } else {
            // Vertical 5x4x2 shield wall 2 blocks ahead of player!
            Vector dir = pl.getDirection().setY(0).normalize();
            boolean alongX = Math.abs(dir.getX()) > Math.abs(dir.getZ());
            int stepX = alongX ? (dir.getX() >= 0 ? 2 : -2) : 0;
            int stepZ = alongX ? 0 : (dir.getZ() >= 0 ? 2 : -2);
            int cx = pl.getBlockX() + stepX;
            int cz = pl.getBlockZ() + stepZ;
            int cy = pl.getBlockY();
            for (int spread = -2; spread <= 2; spread++) {
                for (int dy = 0; dy <= 3; dy++) {
                    for (int thick = 0; thick <= 1; thick++) {
                        int bx = alongX ? cx + (stepX > 0 ? thick : -thick) : cx + spread;
                        int bz = alongX ? cz + spread : cz + (stepZ > 0 ? thick : -thick);
                        Block b = w.getBlockAt(bx, cy + dy, bz);
                        if (isReplaceableForTemp(b)) {
                            saved.put(b, b.getBlockData().clone());
                            activeTempBlocks.add(blockKey(b));
                            b.setType(((spread + dy) % 2 == 0) ? Material.OBSIDIAN : Material.CRYING_OBSIDIAN, false);
                        }
                    }
                }
            }
        }
        ColorUtil.sound(p, "block.anvil.land", 0.85f, 1.2f);
        p.sendMessage(ColorUtil.c("&#A855F7🧱 Пласт 5×5×2 установлен на &e12 секунд&#A855F7!"));
        scheduleRestore(saved, 240L);
    }

    private void spawnTemporaryTrapka(Player p) {
        String skin = plugin.getStore().getSelectedTrapSkin(p.getName());
        Material mainMat = Material.NETHERITE_BLOCK;
        Material accentMat = Material.OBSIDIAN;
        if ("ice".equalsIgnoreCase(skin)) { mainMat = Material.BLUE_ICE; accentMat = Material.PACKED_ICE; }
        else if ("inferno".equalsIgnoreCase(skin)) { mainMat = Material.RED_NETHER_BRICKS; accentMat = Material.MAGMA_BLOCK; }
        else if ("toxic".equalsIgnoreCase(skin)) { mainMat = Material.EMERALD_BLOCK; accentMat = Material.LIME_STAINED_GLASS; }
        else if ("void".equalsIgnoreCase(skin)) { mainMat = Material.CRYING_OBSIDIAN; accentMat = Material.PURPUR_BLOCK; }
        else if ("royal".equalsIgnoreCase(skin)) { mainMat = Material.GOLD_BLOCK; accentMat = Material.AMETHYST_BLOCK; }

        Location pl = p.getLocation();
        World w = pl.getWorld();
        int cx = pl.getBlockX(), cy = pl.getBlockY(), cz = pl.getBlockZ();
        final Map<Block, org.bukkit.block.data.BlockData> saved = new HashMap<Block, org.bukkit.block.data.BlockData>();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 3; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    boolean shell = Math.abs(dx) == 2 || Math.abs(dz) == 2 || dy == -1 || dy == 3;
                    Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                    if (shell && isReplaceableForTemp(b)) {
                        saved.put(b, b.getBlockData().clone());
                        activeTempBlocks.add(blockKey(b));
                        b.setType(((dx + dy + dz) % 2 == 0) ? mainMat : accentMat, false);
                    }
                }
            }
        }

        // Apply /skins Trapka Debuff to all enemies caught inside the 5x5x5 cage!
        int trappedEnemies = 0;
        for (Entity en : p.getNearbyEntities(3.2, 3.2, 3.2)) {
            if (en instanceof Player && isEnemyTarget(p, (Player) en)) {
                Player v = (Player) en;
                trappedEnemies++;
                if ("ice".equalsIgnoreCase(skin)) {
                    v.setFreezeTicks(220);
                    ColorUtil.addPot(v, "slowness", "SLOW", 160, 2);
                } else if ("inferno".equalsIgnoreCase(skin)) {
                    v.setFireTicks(120);
                    ColorUtil.addPot(v, "weakness", "WEAKNESS", 140, 0);
                } else if ("toxic".equalsIgnoreCase(skin)) {
                    ColorUtil.addPot(v, "poison", "POISON", 140, 1);
                    ColorUtil.addPot(v, "hunger", "HUNGER", 180, 2);
                } else if ("void".equalsIgnoreCase(skin)) {
                    ColorUtil.addPot(v, "blindness", "BLINDNESS", 120, 0);
                    ColorUtil.addPot(v, "mining_fatigue", "SLOW_DIGGING", 160, 1);
                } else if ("royal".equalsIgnoreCase(skin)) {
                    ColorUtil.addPot(v, "wither", "WITHER", 120, 1);
                    ColorUtil.addPot(v, "glowing", "GLOWING", 200, 0);
                    ColorUtil.addPot(v, "weakness", "WEAKNESS", 120, 1);
                } else {
                    ColorUtil.addPot(v, "slowness", "SLOW", 120, 0);
                }
                v.sendMessage(ColorUtil.c("&#FF1744⛓ Вы закрыты в Трапке игрока &f" + p.getName() + " &#FF1744(Скин: &e" + skin.toUpperCase() + "&#FF1744)!"));
            }
        }

        ColorUtil.sound(p, "block.iron_door.close", 1f, 0.7f);
        p.sendMessage(ColorUtil.c("&#FF1744⛓ Трапка 5×5×5 активирована на &e15 сек. &8(Скин: &d" + skin.toUpperCase() + "&8, врагов: &f" + trappedEnemies + "&8)"));
        scheduleRestore(saved, 300L);
    }

    private void scheduleRestore(final Map<Block, org.bukkit.block.data.BlockData> saved, long delayTicks) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<Block, org.bukkit.block.data.BlockData> en : saved.entrySet()) {
                    Block b = en.getKey();
                    activeTempBlocks.remove(blockKey(b));
                    b.setBlockData(en.getValue(), false);
                }
            }
        }.runTaskLater(plugin, delayTicks);
    }

    private void consumeOne(Player p, ItemStack hand) {
        if (hand.getAmount() > 1) hand.setAmount(hand.getAmount() - 1);
        else p.getInventory().setItemInMainHand(null);
    }

    // ================= КЛИКИ ПО 24 АНИМИРОВАННЫМ NPC В СВОИХ СКИНАХ (0 ВАНИЛЬНЫХ ЖИТЕЛЕЙ!) =================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onNpcRightClick(PlayerInteractEntityEvent e) {
        Entity target = e.getRightClicked();
        final SpawnEngine.NpcDef nd = plugin.getSpawnEngine().getNpcByEntity(target);
        if (nd != null) {
            e.setCancelled(true);
            final Player p = e.getPlayer();
            long now = System.currentTimeMillis();
            Long last = npcClickCd.get(p.getUniqueId());
            if (last != null && now - last.longValue() < 250L) return;
            npcClickCd.put(p.getUniqueId(), Long.valueOf(now));
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (p.isOnline()) triggerNpcAction(p, nd);
                }
            }.runTask(plugin);
            return;
        }
        if (target instanceof AbstractVillager && plugin.getSpawnEngine().isInSpawnWorld(target.getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onNpcInteractAt(PlayerInteractAtEntityEvent e) {
        Entity target = e.getRightClicked();
        final SpawnEngine.NpcDef nd = plugin.getSpawnEngine().getNpcByEntity(target);
        if (nd != null) {
            e.setCancelled(true);
            final Player p = e.getPlayer();
            long now = System.currentTimeMillis();
            Long last = npcClickCd.get(p.getUniqueId());
            if (last != null && now - last.longValue() < 250L) return;
            npcClickCd.put(p.getUniqueId(), Long.valueOf(now));
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (p.isOnline()) triggerNpcAction(p, nd);
                }
            }.runTask(plugin);
            return;
        }
        if (target instanceof AbstractVillager && plugin.getSpawnEngine().isInSpawnWorld(target.getLocation())) {
            e.setCancelled(true);
        }
    }

    private void triggerNpcAction(Player p, SpawnEngine.NpcDef nd) {
        if (!plugin.getStore().isAuthenticated(p)) return;
        ColorUtil.sound(p, "entity.experience_orb.pickup", 0.9f, 1.2f);
        String act = nd.commandOrAction;
        if ("gui:rtp".equals(act)) plugin.getGuis().openRtpMenu(p);
        else if ("gui:donate".equals(act)) plugin.getGuis().openDonateMenu(p);
        else if ("gui:shop".equals(act)) plugin.getGuis().openCoinShopHub(p);
        else if ("gui:rshop".equals(act)) plugin.getGuis().openRelicShop(p);
        else if ("gui:skins".equals(act)) plugin.getGuis().openTrapSkinsMenu(p);
        else if ("gui:bounty".equals(act)) plugin.getGuis().openBountyMenu(p);
        else if ("gui:cases".equals(act)) plugin.getGuis().openCasesMenu(p);
        else if ("gui:buyer".equals(act)) plugin.getGuis().openBuyerMenu(p);
        else if ("gui:ah".equals(act)) plugin.getGuis().openAuctionMenu(p, 0);
        else if ("gui:kits".equals(act)) plugin.getGuis().openKitsMenu(p, 0);
        else if ("gui:bp".equals(act)) plugin.getGuis().openBpMenu(p);
        else if ("gui:casino".equals(act)) plugin.getGuis().openCasinoMenu(p);
        else if ("gui:enchants".equals(act)) plugin.getGuis().openEnchantsMenu(p);
        else if ("gui:bafovar".equals(act)) plugin.getGuis().openBafovarMenu(p);
        else if ("gui:freebie".equals(act)) plugin.getGuis().openFreebieMenu(p);
        else if ("gui:clans".equals(act)) plugin.getGuis().openClanMenu(p);
        else if ("gui:jobs".equals(act)) plugin.getGuis().openJobsMenu(p);
        else if ("gui:events".equals(act)) plugin.getGuis().openEventsMenu(p);
        else if ("gui:djamshut".equals(act)) plugin.getGuis().openDjamshutMenu(p);
        else if ("gui:warps".equals(act)) plugin.getGuis().openWarpsMenu(p);
        else if ("gui:titles".equals(act)) plugin.getGuis().openTitlesMenu(p);
        else if (act.startsWith("shop:")) plugin.getGuis().openCoinShopCat(p, act.substring(5));
        else if (act.startsWith("warp:")) {
            Location wl = plugin.getSpawnEngine().getWarpLocation(act.substring(5));
            if (wl != null) p.teleport(wl);
        } else if ("action:santa".equals(act)) {
            p.performCommand("daily");
        }
    }

    // ================= НАСТОЯЩИЕ КАСТОМНЫЕ ТОТЕМЫ БЕССМЕРТИЯ REALLYWORLD =================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onResurrect(final EntityResurrectEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        final Player p = (Player) e.getEntity();
        ItemStack off = p.getInventory().getItemInOffHand();
        ItemStack main = p.getInventory().getItemInMainHand();
        final boolean inOff = off != null && off.getType() == Material.TOTEM_OF_UNDYING;
        ItemStack totem = inOff ? off : (main != null && main.getType() == Material.TOTEM_OF_UNDYING ? main : null);
        if (totem == null) return;
        final String tid = ItemFactory.getCustomId(totem);
        if (tid == null) return;
        final int curCharges = ItemFactory.getTotemCharges(totem);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) return;
                if ("tal_krushitelya".equals(tid)) {
                    ColorUtil.addPot(p, "strength", "INCREASE_DAMAGE", 300, 2);
                    ColorUtil.addPot(p, "absorption", "ABSORPTION", 400, 3);
                    p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 50, 2.0, 1.0, 2.0, 0.08);
                    for (Entity en : p.getNearbyEntities(6, 4, 6)) {
                        if (en instanceof Player && !en.equals(p)) {
                            en.setFireTicks(100);
                            en.setVelocity(en.getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(1.1).setY(0.4));
                        }
                    }
                    p.sendMessage(ColorUtil.c("&#FF6D00🛡 Сработал Талисман Крушителя! (+8 Золотых ❤ + Огненная Волна!)"));
                } else if ("tal_grani".equals(tid)) {
                    ColorUtil.addPot(p, "absorption", "ABSORPTION", 500, 4);
                    ColorUtil.addPot(p, "resistance", "DAMAGE_RESISTANCE", 200, 2);
                    p.sendMessage(ColorUtil.c("&#FACC15🛡 Сработал Талисман Грани! (+10 Золотых ❤ + Сопротивление III!)"));
                } else if ("tal_dedala".equals(tid)) {
                    ColorUtil.addPot(p, "speed", "SPEED", 240, 3);
                    ColorUtil.addPot(p, "jump_boost", "JUMP", 240, 2);
                    p.sendMessage(ColorUtil.c("&#38BDF8🛡 Сработал Талисман Дедала! (Скорость IV!)"));
                } else if ("tal_tritona".equals(tid)) {
                    ColorUtil.addPot(p, "regeneration", "REGENERATION", 240, 2);
                    for (Entity en : p.getNearbyEntities(6, 4, 6)) {
                        if (en instanceof Player && !en.equals(p)) {
                            en.setVelocity(en.getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(1.3).setY(0.45));
                        }
                    }
                    p.sendMessage(ColorUtil.c("&#00E5FF🔱 Сработал Талисман Тритона!"));
                } else if ("tal_echidny".equals(tid)) {
                    for (Entity en : p.getNearbyEntities(6, 4, 6)) {
                        if (en instanceof Player && !en.equals(p)) {
                            ColorUtil.addPot((Player) en, "poison", "POISON", 160, 2);
                            ColorUtil.addPot((Player) en, "weakness", "WEAKNESS", 160, 1);
                        }
                    }
                    p.sendMessage(ColorUtil.c("&#22C55E🐍 Сработал Талисман Ехидны! Враги отравлены!"));
                } else if ("tal_stuzhi".equals(tid)) {
                    p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 1, 0), 65, 2.5, 1.0, 2.5, 0.04);
                    for (Entity en : p.getNearbyEntities(7, 4, 7)) {
                        if (en instanceof Player && !en.equals(p)) {
                            en.setFreezeTicks(180);
                            ColorUtil.addPot((Player) en, "slowness", "SLOW", 120, 3);
                        }
                    }
                    p.sendMessage(ColorUtil.c("&#80D8FF❄ Сработал Тотем Стужи! Враги заморожены!"));
                } else if ("tal_feniksa".equals(tid)) {
                    p.setHealth(p.getMaxHealth());
                    ColorUtil.addPot(p, "regeneration", "REGENERATION", 240, 3);
                    if (curCharges > 1) {
                        ItemStack nextTotem = ItemFactory.talFeniksa(curCharges - 1);
                        if (inOff) p.getInventory().setItemInOffHand(nextTotem);
                        else p.getInventory().setItemInMainHand(nextTotem);
                        p.sendMessage(ColorUtil.c("&#FFAB00🦅 Талисман Феникса спас вам жизнь и восстановил 100% ХП! Осталось зарядов: &a" + (curCharges - 1) + "/3"));
                    } else {
                        p.sendMessage(ColorUtil.c("&#FFAB00🦅 Использован последний заряд Талисмана Феникса (0/3)!"));
                    }
                }
            }
        }.runTaskLater(plugin, 1L);
    }

    // ================= PVP, АРЕНА, КАБИНКИ, ВАМПИРИЗМ, БОССЫ И ФАРМИЛКА =================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!plugin.getSpawnEngine().isInSpawnWorld(e.getEntity().getLocation())) return;
        if (e.getEntity() instanceof Player) {
            if (e.getCause() == EntityDamageEvent.DamageCause.FALL || e.getCause() == EntityDamageEvent.DamageCause.VOID) {
                e.setCancelled(true);
                return;
            }
            if (!plugin.getSpawnEngine().isInsidePvpZone(e.getEntity().getLocation()) && !(e instanceof EntityDamageByEntityEvent)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityHit(EntityDamageByEntityEvent e) {
        // Left-click NPC -> trigger NPC menu!
        SpawnEngine.NpcDef nd = plugin.getSpawnEngine().getNpcByEntity(e.getEntity());
        if (nd != null) {
            e.setCancelled(true);
            if (e.getDamager() instanceof Player) triggerNpcAction((Player) e.getDamager(), nd);
            return;
        }
        if (e.getEntity().getScoreboardTags().contains("kg_holo")) {
            e.setCancelled(true);
            return;
        }

        Player attacker = null;
        if (e.getDamager() instanceof Player) attacker = (Player) e.getDamager();
        else if (e.getDamager() instanceof Projectile && ((Projectile) e.getDamager()).getShooter() instanceof Player) {
            attacker = (Player) ((Projectile) e.getDamager()).getShooter();
        }
        if (attacker == null) return;
        if (!plugin.getStore().isAuthenticated(attacker) || plugin.getModeration().isFrozenOrAscending(attacker)) {
            e.setCancelled(true);
            return;
        }
        if (e.getEntity() instanceof Player && plugin.getModeration().isFrozenOrAscending((Player) e.getEntity())) {
            e.setCancelled(true);
            ColorUtil.actionBar(attacker, "&#FFD740🔍 Этот игрок находится на проверке модерации!");
            return;
        }

        // Event Boss damage tracking
        if (e.getEntity() instanceof LivingEntity && e.getEntity().getScoreboardTags().contains("kg_event_boss")) {
            plugin.getBossEvents().recordBossDamage((LivingEntity) e.getEntity(), attacker, e.getFinalDamage());
            spawnDamageIndicator(e.getEntity().getLocation(), e.getFinalDamage());
            return;
        }

        // PvP checks
        if (e.getEntity() instanceof Player) {
            Player victim = (Player) e.getEntity();
            if (plugin.getSpawnEngine().isInSpawnWorld(victim.getLocation())) {
                if (!plugin.getSpawnEngine().isInsidePvpZone(victim.getLocation())
                        || !plugin.getSpawnEngine().isInsidePvpZone(attacker.getLocation())) {
                    e.setCancelled(true);
                    ColorUtil.actionBar(attacker, "&#FF5252⚔ На спавне PvP работает только в яме /warp pvp и в Кабинках!");
                    return;
                }
            }
            DataStore.Clan c1 = plugin.getStore().getClanOf(attacker.getName());
            DataStore.Clan c2 = plugin.getStore().getClanOf(victim.getName());
            if (c1 != null && c1.equals(c2) && !c1.friendlyFire) {
                e.setCancelled(true);
                ColorUtil.actionBar(attacker, "&#B388FF🛡 Соклановец! Огонь по своим отключён.");
                return;
            }

            long tagEnd = System.currentTimeMillis() + 15_000L;
            combatUntil.put(attacker.getUniqueId(), Long.valueOf(tagEnd));
            combatUntil.put(victim.getUniqueId(), Long.valueOf(tagEnd));
            if (attacker.isFlying() && attacker.getGameMode() == GameMode.SURVIVAL) attacker.setFlying(false);
            if (victim.isFlying() && victim.getGameMode() == GameMode.SURVIVAL) victim.setFlying(false);

            // Кастомные чары и Элитры FT×RW: Вампиризм, Окисление, Элитры Феникса/Вампира/Грома, Стужа
            String weaponId = ItemFactory.getCustomId(attacker.getInventory().getItemInMainHand());
            String offId = ItemFactory.getCustomId(attacker.getInventory().getItemInOffHand());
            String atkChest = ItemFactory.getCustomId(attacker.getInventory().getChestplate());
            String vicChest = ItemFactory.getCustomId(victim.getInventory().getChestplate());

            if ("sword_dracula".equals(weaponId) || "tal_echidny".equals(offId) || "elytra_vampire".equals(atkChest)) {
                double heal = Math.min(3.5, e.getFinalDamage() * 0.22);
                attacker.setHealth(Math.min(attacker.getMaxHealth(), attacker.getHealth() + heal));
            }
            if ("elytra_phoenix".equals(vicChest)) {
                attacker.setFireTicks(80);
            }
            if ("elytra_thunder".equals(atkChest) && rng.nextInt(100) < 18) {
                victim.getWorld().strikeLightningEffect(victim.getLocation());
                e.setDamage(e.getDamage() + 3.0);
            }
            if ("sword_krush".equals(weaponId)) {
                for (ItemStack arm : victim.getInventory().getArmorContents()) {
                    if (arm != null && arm.hasItemMeta() && arm.getItemMeta() instanceof Damageable) {
                        ItemMeta m = arm.getItemMeta();
                        Damageable d = (Damageable) m;
                        d.setDamage(d.getDamage() + 3);
                        arm.setItemMeta(m);
                    }
                }
            }
            if (e.getDamager() instanceof Arrow && "bow_ice_rw".equals(weaponId)) {
                victim.setFreezeTicks(140);
                ColorUtil.addPot(victim, "slowness", "SLOW", 60, 1);
            }
            spawnDamageIndicator(victim.getLocation(), e.getFinalDamage());
        }
    }

    private void spawnDamageIndicator(Location base, double dmg) {
        if (dmg < 0.5 || base.getWorld() == null) return;
        Location hl = base.clone().add((rng.nextDouble() - 0.5) * 0.7, 1.4, (rng.nextDouble() - 0.5) * 0.7);
        final ArmorStand st = (ArmorStand) base.getWorld().spawnEntity(hl, EntityType.ARMOR_STAND);
        st.setVisible(false);
        st.setMarker(true);
        st.setGravity(false);
        st.setSmall(true);
        st.setCustomName(ColorUtil.c("&#FF1744-" + String.format("%.1f", Double.valueOf(dmg)) + " ❤"));
        st.setCustomNameVisible(true);
        st.addScoreboardTag("kg_holo");
        new BukkitRunnable() {
            @Override
            public void run() {
                st.remove();
            }
        }.runTaskLater(plugin, 18L);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        Player killer = dead.getKiller();
        if (dead.getScoreboardTags().contains("kg_event_boss")) {
            e.getDrops().clear();
            plugin.getBossEvents().handleBossDeath(dead, killer);
            return;
        }
        if (dead.getScoreboardTags().contains("kg_farm_mob") && killer != null) {
            e.setDroppedExp(12);
            double c = 45.0;
            plugin.getStore().addCoins(killer.getName(), c);
            if (rng.nextInt(100) < 35) {
                plugin.getStore().addSnowflakes(killer.getName(), 1);
                ColorUtil.actionBar(killer, "&#AEEA00🧟 Фармилка: &a+45$ &8| &#80D8FF+1 Снежинка ❄!");
            } else {
                ColorUtil.actionBar(killer, "&#AEEA00🧟 Фармилка: &a+45$ &7(+12 XP)");
            }
            return;
        }
        if (dead instanceof Monster && killer != null && "hunter".equals(plugin.getStore().getJob(killer.getName()))) {
            plugin.getStore().addCoins(killer.getName(), 35.0);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player dead = e.getEntity();
        combatUntil.remove(dead.getUniqueId());
        plugin.getStore().setBack(dead, dead.getLocation());
        plugin.getStore().addDeath(dead.getName());
        plugin.getSpawnEngine().notifyKabinkaDeath(dead.getLocation());

        Player killer = dead.getKiller();
        if (killer != null && !killer.equals(dead)) {
            plugin.getStore().addKill(killer.getName());
            plugin.getStore().addCoins(killer.getName(), 250.0);
            double bounty = plugin.getStore().claimBounty(dead.getName());
            if (bounty > 0.0) {
                plugin.getStore().addCoins(killer.getName(), bounty);
                Bukkit.broadcastMessage(ColorUtil.c("&#FF1744&l🎯 [ОХОТА ЗА ГОЛОВАМИ] &fИгрок &#00E676" + killer.getName()
                        + " &fликвидировал цель &c" + dead.getName() + " &fи забрал контракт &a+" + ColorUtil.fmtMoney(bounty) + "$&f!"));
            }
            // Drop PvP Trophy Skull
            ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
            ItemMeta hm = head.getItemMeta();
            if (hm instanceof SkullMeta) {
                ((SkullMeta) hm).setOwningPlayer(dead);
                hm.setDisplayName(ColorUtil.c("&#FF1744💀 Голова игрока &f" + dead.getName()));
                hm.setLore(Arrays.asList(ColorUtil.c("&7Убит игроком: &a" + killer.getName())));
                head.setItemMeta(hm);
            }
            e.getDrops().add(head);
            e.setDeathMessage(ColorUtil.c("&#FF1744⚔ &f" + dead.getName() + " &7пал от руки &c" + killer.getName()));
        } else {
            e.setDeathMessage(null);
        }
        plugin.getStore().save();
    }

    // ================= ИНВЕНТАРЬ И ЧАТ REALLYWORLD =================
    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        if (plugin.getModeration().handleGuiClick(e, e.getView().getTitle())) {
            return;
        }
        if (e.getWhoClicked() instanceof Player && plugin.getModeration().isFrozenOrAscending((Player) e.getWhoClicked())) {
            e.setCancelled(true);
            return;
        }
        plugin.getGuis().handleInventoryClick(e);
    }

    @EventHandler
    public void onInvClose(InventoryCloseEvent e) {
        plugin.getGuis().handleInventoryClose(e);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (!plugin.getStore().isAuthenticated(e.getPlayer())
                || plugin.getModeration().isFrozenOrAscending(e.getPlayer())
                || e.getItemDrop().getScoreboardTags().contains("kg_case_float")) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getItem().getScoreboardTags().contains("kg_case_float")) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onCmdPreprocess(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        String rawMsg = e.getMessage();
        String msg = rawMsg.toLowerCase(java.util.Locale.ROOT);

        // Поддержка русской команды /спек -> /spek!
        if (msg.equals("/спек") || msg.startsWith("/спек ")) {
            String rest = rawMsg.length() > 5 ? rawMsg.substring(5) : "";
            e.setMessage("/spek" + rest);
            msg = e.getMessage().toLowerCase(java.util.Locale.ROOT);
        }

        if (!plugin.getStore().isAuthenticated(p)) {
            if (!msg.startsWith("/login") && !msg.startsWith("/l ") && !msg.startsWith("/reg")) {
                e.setCancelled(true);
                p.sendMessage(ColorUtil.c("&#FF1744🔐 Сначала войдите на сервер: &f/login &7или &f/register"));
                return;
            }
        }
        if (plugin.getModeration().isFrozenOrAscending(p)) {
            e.setCancelled(true);
            p.sendMessage(ColorUtil.c("&#FF1744⚠ Во время проверки на читы команды отключены! Пишите в обычный чат."));
            return;
        }
        Long cu = combatUntil.get(p.getUniqueId());
        if (cu != null && System.currentTimeMillis() < cu.longValue() && !"owner".equals(plugin.getRanks().getRank(p).id)) {
            if (msg.startsWith("/spawn") || msg.startsWith("/rtp") || msg.startsWith("/warp")
                    || msg.startsWith("/home") || msg.startsWith("/tpa") || msg.startsWith("/ec")) {
                e.setCancelled(true);
                p.sendMessage(ColorUtil.c("&#FF1744⚔ Команды телепортации заблокированы во время PvP!"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        final Player p = e.getPlayer();
        if (!plugin.getStore().isAuthenticated(p)) {
            e.setCancelled(true);
            p.sendMessage(ColorUtil.c("&#FF1744🔐 Сначала авторизуйтесь: /login или /register"));
            return;
        }
        String raw = e.getMessage().trim();

        // Прямой приватный канал чата на проверке /check (работает даже при муте!)
        if (plugin.getModeration().handleCheckChat(p, raw)) {
            e.setCancelled(true);
            return;
        }

        long muteSec = plugin.getStore().getMuteRemainingSec(p.getName());
        if (muteSec > 0) {
            e.setCancelled(true);
            String mReason = plugin.getStore().getMuteReason(p.getName());
            String mStaff = plugin.getStore().getMuteStaff(p.getName());
            String remLabel = muteSec > 36500L * 86400L ? "Навсегда" : ColorUtil.fmtTime(muteSec);
            p.sendMessage(ColorUtil.c("&#FF5252🔇 Вы замучены модератором &#FFD740" + mStaff
                    + "&#FF5252! Осталось: &f" + remLabel + " &8│ &7Причина: &e" + mReason));
            return;
        }
        if (activeChatAnswer != null && raw.equalsIgnoreCase(activeChatAnswer)) {
            activeChatAnswer = null;
            new BukkitRunnable() {
                @Override
                public void run() {
                    plugin.getStore().addCoins(p.getName(), 7500);
                    plugin.getStore().addSnowflakes(p.getName(), 4);
                    plugin.getStore().save();
                    Bukkit.broadcastMessage(ColorUtil.c("&#00E676&l[Викторина РВ] &fИгрок &e" + p.getName()
                            + " &fответил первым и получил &a+7 500$ &fи &b+4 Снежинки ❄&f!"));
                }
            }.runTask(plugin);
        }

        boolean global = raw.startsWith("!");
        if (global && raw.length() > 1) raw = raw.substring(1).trim();

        String pref = plugin.getRanks().getEffectivePrefix(p);
        DataStore.Clan clan = plugin.getStore().getClanOf(p.getName());
        String clanPart = clan != null ? "&#B388FF[" + clan.tag + "] " : "";
        String chTag = global ? "&#FFD740&l[G] " : "&#80D8FF&l[L] ";
        RankManager.RankDef r = plugin.getRanks().getRank(p);
        String msgText = r.weight >= 65 ? ColorUtil.c(raw) : raw;
        String formatted = ColorUtil.c(chTag + clanPart + pref + p.getName() + " &8» &f") + msgText;

        e.setCancelled(true);
        Bukkit.getConsoleSender().sendMessage(formatted);
        for (Player rec : Bukkit.getOnlinePlayers()) {
            if (global || (rec.getWorld().equals(p.getWorld()) && rec.getLocation().distanceSquared(p.getLocation()) <= 10000.0)) {
                rec.sendMessage(formatted);
            }
        }
    }
}
