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
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
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
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class GameplayListener implements Listener {

    private static final class PrimedTntEntry {
        final String tntType;
        final String baseLabel;
        final UUID ownerId;

        PrimedTntEntry(String tntType, String baseLabel, UUID ownerId) {
            this.tntType = tntType;
            this.baseLabel = baseLabel;
            this.ownerId = ownerId;
        }
    }

    private static final NamespacedKey SPAWNER_TYPE_KEY = new NamespacedKey("kinggrief", "spawner_mob");
    private static final NamespacedKey PROJ_TAG_KEY = new NamespacedKey("kinggrief", "proj_type");
    private static final NamespacedKey PROJ_ARROW_KEY = new NamespacedKey("kinggrief", "arrow_type");
    private final KingGriefCore plugin;
    private final Random rng = new Random();
    private final Set<UUID> drillGuard = new HashSet<UUID>();
    private final Map<UUID, Long> parkourPlateCd = new HashMap<UUID, Long>();
    private final Map<UUID, Long> parkourRunStartMs = new HashMap<UUID, Long>();
    private final Map<UUID, Long> npcClickCd = new HashMap<UUID, Long>();
    private final Map<String, Long> itemCooldowns = new HashMap<String, Long>();
    private final Set<String> activeTempBlocks = new HashSet<String>();
    private final Map<Block, org.bukkit.block.data.BlockData> allSavedTempBlocks = new HashMap<Block, org.bukkit.block.data.BlockData>();
    private final Map<UUID, Integer> killStreaks = new HashMap<UUID, Integer>();
    private final Map<UUID, PrimedTntEntry> activeCustomTnts = new HashMap<UUID, PrimedTntEntry>();
    private final Set<UUID> detonatedTntIds = new HashSet<UUID>();
    private final Map<UUID, Long> recallDamagedAt = new HashMap<UUID, Long>();
    private String activeChatAnswer = null;

    private static String blockKey(Block b) {
        return b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }

    public GameplayListener(KingGriefCore plugin) {
        this.plugin = plugin;
        startChatQuizLoop();
        startCustomTntAndPassiveRelicLoops();
    }

    private void startCustomTntAndPassiveRelicLoops() {
        // 1. Live 2-Tick Custom TNT Countdown + Failsafe Detonation Engine (100% immune to cancelled vanilla explosions!)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (activeCustomTnts.isEmpty()) return;
                java.util.Iterator<Map.Entry<UUID, PrimedTntEntry>> it = activeCustomTnts.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<UUID, PrimedTntEntry> en = it.next();
                    UUID uid = en.getKey();
                    PrimedTntEntry info = en.getValue();
                    Entity ent = Bukkit.getEntity(uid);
                    if (!(ent instanceof TNTPrimed) || ent.isDead() || !ent.isValid()) {
                        it.remove();
                        detonatedTntIds.remove(uid);
                        continue;
                    }
                    TNTPrimed tnt = (TNTPrimed) ent;
                    int fuse = tnt.getFuseTicks();
                    double sec = Math.max(0.0, fuse / 20.0);
                    tnt.setCustomName(ColorUtil.c(info.baseLabel + " &8• &f" + String.format(java.util.Locale.US, "%.1fс", Double.valueOf(sec))));
                    Location tl = tnt.getLocation().add(0, 0.85, 0);
                    World w = tl.getWorld();
                    if (w != null) {
                        try {
                            if ("tnt_black".equals(info.tntType)) {
                                w.spawnParticle(Particle.WITCH, tl, 4, 0.25, 0.25, 0.25, 0.02);
                            } else if ("tnt_nuke".equals(info.tntType)) {
                                w.spawnParticle(Particle.FLAME, tl, 5, 0.3, 0.3, 0.3, 0.03);
                                if (fuse % 10 <= 1) w.playSound(tl, "minecraft:block.note_block.pling", 1.2f, 1.8f);
                            } else if ("tnt_cryo".equals(info.tntType)) {
                                w.spawnParticle(Particle.SNOWFLAKE, tl, 5, 0.3, 0.3, 0.3, 0.02);
                            } else if ("tnt_flash".equals(info.tntType)) {
                                w.spawnParticle(Particle.ELECTRIC_SPARK, tl, 5, 0.25, 0.25, 0.25, 0.05);
                            } else if ("tnt_miner".equals(info.tntType)) {
                                w.spawnParticle(Particle.HAPPY_VILLAGER, tl, 4, 0.3, 0.3, 0.3, 0.02);
                            } else {
                                w.spawnParticle(Particle.LAVA, tl, 2, 0.2, 0.2, 0.2, 0.01);
                            }
                        } catch (Throwable ignored) {}
                    }
                    // Failsafe: if fuse reached <= 2 ticks, guarantee custom detonation even if WorldGuard/Essentials cancels EntityExplodeEvent!
                    if (fuse <= 2 && !detonatedTntIds.contains(uid)) {
                        detonatedTntIds.add(uid);
                        Player src = info.ownerId != null ? Bukkit.getPlayer(info.ownerId) : null;
                        Location detLoc = tnt.getLocation().clone();
                        tnt.remove();
                        it.remove();
                        detonateCustomTnt(detLoc, info.tntType, src);
                    }
                }
            }
        }.runTaskTimer(plugin, 4L, 2L);

        // 2. 1-Second Passive Pickaxe Buffs (Молот OWNER 10×10, Кирка Крушителя 3×3, Бур Титана, Вулканическая) + Магнит Лута!
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.isOnline() || p.isDead()) continue;
                    String mainId = ItemFactory.getCustomId(p.getInventory().getItemInMainHand());
                    String offId = ItemFactory.getCustomId(p.getInventory().getItemInOffHand());

                    if ("hammer_owner".equals(mainId)) {
                        ColorUtil.addPot(p, "haste", "FAST_DIGGING", 50, 3);
                        ColorUtil.addPot(p, "night_vision", "NIGHT_VISION", 260, 0);
                    } else if ("pick_krush".equals(mainId) || "pick_tunnel".equals(mainId) || "pick_meteor".equals(mainId)) {
                        ColorUtil.addPot(p, "haste", "FAST_DIGGING", 50, 1);
                    } else if ("pick_molten".equals(mainId)) {
                        ColorUtil.addPot(p, "fire_resistance", "FIRE_RESISTANCE", 60, 0);
                    }

                    // 3D Квантовый Магнит Лута (в левой руке или в хотбаре) — притягивает дроп и опыт в радиусе 9 блоков!
                    boolean hasMagnet = "loot_magnet".equals(offId) || "loot_magnet".equals(mainId);
                    if (!hasMagnet) {
                        for (int s = 0; s < 9; s++) {
                            if ("loot_magnet".equals(ItemFactory.getCustomId(p.getInventory().getItem(s)))) {
                                hasMagnet = true;
                                break;
                            }
                        }
                    }
                    if (hasMagnet && p.getGameMode() != GameMode.SPECTATOR) {
                        Location pl = p.getLocation().add(0, 0.6, 0);
                        for (Entity near : p.getWorld().getNearbyEntities(pl, 9.0, 6.0, 9.0)) {
                            if (near instanceof org.bukkit.entity.Item) {
                                org.bukkit.entity.Item it = (org.bukkit.entity.Item) near;
                                if (it.getScoreboardTags().contains("kg_case_float") || it.getPickupDelay() > 60) continue;
                                it.teleport(pl);
                            } else if (near instanceof org.bukkit.entity.ExperienceOrb) {
                                near.teleport(pl);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void cleanupAllTempBlocks() {
        for (Map.Entry<Block, org.bukkit.block.data.BlockData> en : allSavedTempBlocks.entrySet()) {
            try {
                en.getKey().setBlockData(en.getValue(), false);
            } catch (Throwable ignored) {}
        }
        allSavedTempBlocks.clear();
        activeTempBlocks.clear();
    }

    public void tagPvp30(Player... players) {
        for (Player p : players) {
            if (p == null || !p.isOnline()) continue;
            if ("owner".equalsIgnoreCase(plugin.getRanks().getRank(p).id) && p.getGameMode() == GameMode.CREATIVE) continue;
            plugin.getHud().tagCombat(p);
            if (p.isFlying() && p.getGameMode() == GameMode.SURVIVAL) {
                p.setFlying(false);
                p.setAllowFlight(false);
            }
        }
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

        // Auto-push Mega Resourcepack v5.8 with exact SHA-1 & cache-busting query (106 3D items + 12 Pickaxes + Молот OWNER 10×10 + Bow Animation!)
        final String rawRpUrl = plugin.getConfig().getString("resourcepack-url", "https://kinggrief.vercel.app/kinggrief-mega-rp.zip?v=62");
        final String rpSha1 = plugin.getConfig().getString("resourcepack-sha1", "83393b7629c17a1163183fe3894824339a898d2c");
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) return;
                plugin.getHud().updatePlayerHud(p);
                try {
                    if (plugin.getConfig().getBoolean("push-resourcepack-on-join", true) && rawRpUrl != null && !rawRpUrl.isEmpty()) {
                        String effectiveUrl = rawRpUrl;
                        String effectiveSha1 = rpSha1;
                        if (effectiveUrl.contains("kinggrief.vercel.app/kinggrief-mega-rp.zip")) {
                            effectiveUrl = "https://kinggrief.vercel.app/kinggrief-mega-rp.zip?v=62";
                            effectiveSha1 = "83393b7629c17a1163183fe3894824339a898d2c";
                        }
                        byte[] hashBytes = ColorUtil.hexToBytes(effectiveSha1);
                        if (hashBytes != null) {
                            p.setResourcePack(effectiveUrl, hashBytes);
                        } else {
                            p.setResourcePack(effectiveUrl);
                        }
                    }
                } catch (Throwable ignored) {}
                if ("default".equalsIgnoreCase(plugin.getRanks().getRank(p).id)) {
                    p.sendMessage(ColorUtil.c("&#F5D0FE&l🎁 [ХАЛЯВА] &fПропиши команду &#FFD740&l/free dragon &fи забери привилегию &#F5D0FE&lDRAGON &fБЕСПЛАТНО!"));
                }
            }
        }.runTaskLater(plugin, 15L);

        if (!plugin.getStore().isExternalAuthActive()) {
            if (plugin.getStore().trySessionAutoLogin(p)) {
                p.sendMessage(ColorUtil.c("&#00E676✔ Автоматический вход по сессии выполнен! Приятной игры!"));
            } else {
                p.teleport(plugin.getSpawnEngine().getSpawnArrival());
                boolean reg = plugin.getStore().isRegistered(p.getName());
                p.sendTitle(ColorUtil.c("&#80D8FF&l❄ KINGGRIEF v5.8 ❄"),
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
        killStreaks.remove(p.getUniqueId());
        boolean wasFrozen = plugin.getModeration().isFrozenOrAscending(p);
        plugin.getModeration().handleQuitDuringCheck(p);
        if (!wasFrozen && plugin.getHud().isInCombat(p) && !p.isDead()
                && !"owner".equalsIgnoreCase(plugin.getRanks().getRank(p).id)) {
            p.setHealth(0.0);
            Bukkit.broadcastMessage(ColorUtil.c("&#FF1744⚔ Игрок &f" + p.getName() + " &#FF1744вышел с сервера во время 30-секундного ПвП и потерял вещи!"));
        }
        plugin.getBossEvents().removeBarsForQuit(p);
        if (plugin.getOwnerGiveaway() != null) plugin.getOwnerGiveaway().removeBarsForQuit(p);
        plugin.getStore().clearQuickFreeze(p.getUniqueId());
        plugin.getStore().removeAttachment(p);
        plugin.getHud().removePlayer(p);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        plugin.getSpawnEngine().onSpawnChunkLoad(e.getChunk());
        plugin.getBossEvents().purgeOrphanEventEntitiesInChunk(e.getChunk());
    }

    @EventHandler
    public void onPlayerTeleportToSpawn(org.bukkit.event.player.PlayerTeleportEvent e) {
        if (e.getTo() != null && plugin.getSpawnEngine().isInSpawnWorld(e.getTo())) {
            final World sw = e.getTo().getWorld();
            new BukkitRunnable() {
                @Override
                public void run() {
                    plugin.getSpawnEngine().enforceStrictSingletonsOnSpawn(sw);
                }
            }.runTaskLater(plugin, 4L);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        e.setRespawnLocation(plugin.getSpawnEngine().getSpawnArrival());
    }

    // ================= ЗАЩИТА ДО АВТОРИЗАЦИИ И ЗАМОРОЗКА НА ПРОВЕРКЕ /check ИЛИ /freeze =================
    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!plugin.getStore().isAuthenticated(p) || plugin.getModeration().isUnderCheck(p) || plugin.getStore().isQuickFrozen(p)) {
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

        // 2B. Защита стильных архитектурных храмов Ивентов (кроме добываемых руд Метеорита!)
        if (plugin.getBossEvents().isProtectedEventStructureBlock(b, true)) {
            e.setCancelled(true);
            ColorUtil.actionBar(p, "&#FF1744🛡 Архитектура активного Ивента защищена от разрушения!");
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

        // 5. СИСТЕМА 12 КАСТОМНЫХ 3D КИРОК И МОЛОТОВ v5.8 (Кирка Крушителя 3×3, Молот OWNER 10×10, Бур 5×5, Жильная, Мидас, Гефест и др.)!
        if (toolId != null && !drillGuard.contains(p.getUniqueId())) {
            drillGuard.add(p.getUniqueId());
            try {
                if (handleCustomPickaxeBreak(e, p, b, tool, toolId)) {
                    plugin.getStore().addMined(p.getName());
                    return;
                }
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

    // Мгновенный раскол Обсидиана 9-й киркой «Обсидиановый Крушитель» (pick_obsidian)!
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onObsidianBreakerTap(BlockDamageEvent e) {
        Player p = e.getPlayer();
        if (!plugin.getStore().isAuthenticated(p) || plugin.getModeration().isFrozenOrAscending(p)) return;
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!"pick_obsidian".equals(ItemFactory.getCustomId(hand))) return;

        Block b = e.getBlock();
        Material m = b.getType();
        if (m != Material.OBSIDIAN && m != Material.CRYING_OBSIDIAN
                && m != Material.RESPAWN_ANCHOR && m != Material.ENDER_CHEST) {
            return;
        }
        if (plugin.getSpawnEngine().isInSpawnWorld(b.getLocation())) return;
        if (activeTempBlocks.contains(blockKey(b)) || plugin.getBossEvents().isProtectedEventStructureBlock(b, false)) return;
        DataStore.Claim cl = plugin.getStore().getClaimAt(b.getLocation());
        if (cl != null && (!cl.canAccess(p.getName()) || cl.isCenter(b.getLocation()))
                && !"owner".equals(plugin.getRanks().getRank(p).id)) {
            return;
        }
        if (!checkAndSetCd(p, "obs_pick", 1)) return;
        e.setInstaBreak(true);
        b.getWorld().spawnParticle(Particle.PORTAL, b.getLocation().add(0.5, 0.5, 0.5), 25, 0.3, 0.3, 0.3, 0.2);
        ColorUtil.sound(p, "block.glass.break", 1.0f, 0.65f);
    }

    private void handleAutoMineBreak(BlockBreakEvent e, Player p, Block b) {
        Material m = b.getType();
        if (m == Material.COBBLESTONE || m == Material.BEDROCK) {
            e.setCancelled(true);
            return;
        }
        e.setDropItems(false);
        String tid = ItemFactory.getCustomId(p.getInventory().getItemInMainHand());
        int bonusMult = ("hammer_owner".equals(tid) ? 3 : ("pick_krush".equals(tid) || "pick_molten".equals(tid) || "pick_midas".equals(tid) || "pick_tunnel".equals(tid) ? 2 : 1));
        ItemStack drop;
        double coins;
        if (m == Material.COAL_ORE) { drop = new ItemStack(Material.COAL, 2 * bonusMult); coins = 25 * bonusMult; }
        else if (m == Material.IRON_ORE) { drop = new ItemStack(Material.IRON_INGOT, bonusMult); coins = 55 * bonusMult; }
        else if (m == Material.GOLD_ORE) { drop = new ItemStack(Material.GOLD_INGOT, bonusMult); coins = 85 * bonusMult; }
        else if (m == Material.LAPIS_ORE) { drop = new ItemStack(Material.LAPIS_LAZULI, 4 * bonusMult); coins = 60 * bonusMult; }
        else if (m == Material.REDSTONE_ORE) { drop = new ItemStack(Material.REDSTONE, 4 * bonusMult); coins = 60 * bonusMult; }
        else if (m == Material.DIAMOND_ORE) { drop = new ItemStack(Material.DIAMOND, bonusMult); coins = 190 * bonusMult; }
        else if (m == Material.EMERALD_ORE) { drop = new ItemStack(Material.EMERALD, bonusMult); coins = 240 * bonusMult; }
        else if (m == Material.ANCIENT_DEBRIS) { drop = new ItemStack(Material.NETHERITE_SCRAP, bonusMult); coins = 900 * bonusMult; }
        else { drop = new ItemStack(Material.COBBLESTONE, 1); coins = 8 * bonusMult; }

        p.getInventory().addItem(drop);
        p.giveExp(4 * bonusMult);
        plugin.getStore().addCoins(p.getName(), coins);
        plugin.getStore().addMined(p.getName());
        if (rng.nextInt(100) < 22) {
            plugin.getStore().addSnowflakes(p.getName(), 1);
            ColorUtil.actionBar(p, "&#FF9100⛏ +" + (int) coins + "$ &8| &#80D8FF+1 Снежинка Джамшута ❄!");
        } else {
            ColorUtil.actionBar(p, "&#FF9100⛏ Авто-Шахта: &a+" + (int) coins + "$" + (bonusMult > 1 ? " &e(Бонус Кирки ×" + bonusMult + "!)" : ""));
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                b.setType(Material.COBBLESTONE, false);
            }
        }.runTaskLater(plugin, 1L);
    }

    private boolean canDrillBreakBlock(Player p, Block t) {
        Material m = t.getType();
        if (m == Material.AIR || m == Material.BEDROCK || m == Material.BARRIER || m == Material.END_PORTAL_FRAME
                || m == Material.END_PORTAL || m == Material.NETHER_PORTAL || m == Material.WATER || m == Material.LAVA
                || m == Material.SPAWNER || m == Material.CHEST || m == Material.TRAPPED_CHEST || m == Material.BARREL
                || m == Material.ENDER_CHEST || m.name().endsWith("SHULKER_BOX")) {
            return false;
        }
        if (activeTempBlocks.contains(blockKey(t)) || plugin.getBossEvents().isLockedEventChest(t)
                || plugin.getBossEvents().isProtectedEventStructureBlock(t, true)) {
            return false;
        }
        DataStore.Claim cl = plugin.getStore().getClaimAt(t.getLocation());
        if (cl != null) {
            if (cl.isCenter(t.getLocation())) return false;
            if (!cl.canAccess(p.getName()) && !"owner".equals(plugin.getRanks().getRank(p).id)) return false;
        }
        return true;
    }

    private ItemStack smeltedDropForBlock(Material m, int smeltMult, int fortuneBonus) {
        if (m == Material.IRON_ORE || m == Material.DEEPSLATE_IRON_ORE) {
            return new ItemStack(Material.IRON_INGOT, Math.max(1, smeltMult + (fortuneBonus > 0 ? rng.nextInt(fortuneBonus) : 0)));
        }
        if (m == Material.GOLD_ORE || m == Material.DEEPSLATE_GOLD_ORE || m == Material.NETHER_GOLD_ORE) {
            return new ItemStack(Material.GOLD_INGOT, Math.max(1, smeltMult + (fortuneBonus > 0 ? rng.nextInt(fortuneBonus) : 0)));
        }
        if (m == Material.COPPER_ORE || m == Material.DEEPSLATE_COPPER_ORE) {
            return new ItemStack(Material.COPPER_INGOT, 2 * smeltMult);
        }
        if (m == Material.ANCIENT_DEBRIS) {
            return new ItemStack(Material.NETHERITE_SCRAP, smeltMult);
        }
        if (m == Material.DIAMOND_ORE || m == Material.DEEPSLATE_DIAMOND_ORE) {
            return new ItemStack(Material.DIAMOND, 1 + (fortuneBonus > 0 ? rng.nextInt(fortuneBonus + 1) : 0));
        }
        if (m == Material.EMERALD_ORE || m == Material.DEEPSLATE_EMERALD_ORE) {
            return new ItemStack(Material.EMERALD, 1 + (fortuneBonus > 0 ? rng.nextInt(fortuneBonus + 1) : 0));
        }
        if (m == Material.COAL_ORE || m == Material.DEEPSLATE_COAL_ORE) {
            return new ItemStack(Material.COAL, 1 + rng.nextInt(2 + fortuneBonus));
        }
        if (m == Material.LAPIS_ORE || m == Material.DEEPSLATE_LAPIS_ORE) {
            return new ItemStack(Material.LAPIS_LAZULI, 4 + rng.nextInt(4 + fortuneBonus * 2));
        }
        if (m == Material.REDSTONE_ORE || m == Material.DEEPSLATE_REDSTONE_ORE) {
            return new ItemStack(Material.REDSTONE, 4 + rng.nextInt(3 + fortuneBonus));
        }
        if (m == Material.NETHER_QUARTZ_ORE) {
            return new ItemStack(Material.QUARTZ, 2 + rng.nextInt(2 + fortuneBonus));
        }
        if (m == Material.STONE) return new ItemStack(Material.COBBLESTONE, 1);
        if (m == Material.DEEPSLATE) return new ItemStack(Material.COBBLED_DEEPSLATE, 1);
        if (m == Material.GRASS_BLOCK) return new ItemStack(Material.DIRT, 1);
        if (m.isItem()) return new ItemStack(m, 1);
        return null;
    }

    private void addLootBundled(Map<Material, Integer> tally, ItemStack drop) {
        if (drop == null || drop.getType() == Material.AIR || drop.getAmount() <= 0) return;
        Integer prev = tally.get(drop.getType());
        tally.put(drop.getType(), Integer.valueOf((prev == null ? 0 : prev.intValue()) + drop.getAmount()));
    }

    private void deliverBundledLoot(Player p, Location dropSpot, Map<Material, Integer> tally, boolean telekinesis) {
        World w = dropSpot.getWorld();
        for (Map.Entry<Material, Integer> en : tally.entrySet()) {
            Material mat = en.getKey();
            int rem = en.getValue().intValue();
            int maxStack = Math.max(1, mat.getMaxStackSize());
            while (rem > 0) {
                int batch = Math.min(maxStack, rem);
                rem -= batch;
                ItemStack stack = new ItemStack(mat, batch);
                if (telekinesis) {
                    for (ItemStack left : p.getInventory().addItem(stack).values()) {
                        if (w != null) w.dropItemNaturally(p.getLocation(), left);
                    }
                } else if (w != null) {
                    w.dropItemNaturally(dropSpot, stack);
                }
            }
        }
    }

    /**
     * Полная обработка ВСЕХ 12 кастомных 3D кирок и молотов!
     * Shift (приседание) временно включает точный режим 1×1, чтобы не сломать лишний блок на базе!
     */
    private boolean handleCustomPickaxeBreak(BlockBreakEvent e, Player p, Block center, ItemStack tool, String toolId) {
        // #10 Золотая Кирка Мидаса (+Монеты и Золото с камня/руды!)
        if ("pick_midas".equals(toolId)) {
            e.setDropItems(false);
            ItemStack mainDrop = smeltedDropForBlock(center.getType(), 1, 2);
            if (mainDrop != null) p.getInventory().addItem(mainDrop);
            int reward = 25 + rng.nextInt(176);
            if (rng.nextInt(100) < 4) reward += 1200;
            plugin.getStore().addCoins(p.getName(), reward);
            if (rng.nextInt(100) < 16) {
                p.getInventory().addItem(new ItemStack(Material.GOLD_INGOT, 1));
            }
            p.giveExp(3);
            ColorUtil.actionBar(p, "&#FFD740🪙 Дар Мидаса: &a+" + reward + "$ Монет!");
            return true;
        }

        // #7 Вулканическая Кирка Гефеста (Двойная плавка 1×1 с гарантированным ×2 слитков!)
        if ("pick_molten".equals(toolId)) {
            e.setDropItems(false);
            ItemStack drop = smeltedDropForBlock(center.getType(), 2, 2);
            if (drop != null) {
                for (ItemStack rem : p.getInventory().addItem(drop).values()) {
                    center.getWorld().dropItemNaturally(center.getLocation().add(0.5, 0.5, 0.5), rem);
                }
            }
            center.getWorld().spawnParticle(Particle.FLAME, center.getLocation().add(0.5, 0.5, 0.5), 8, 0.25, 0.25, 0.25, 0.02);
            p.giveExp(4);
            return true;
        }

        // #5 Изумрудная Жильная Кирка (VeinMiner — вся жила руды до 32 блоков сразу!)
        if ("pick_vein".equals(toolId)) {
            String mn = center.getType().name();
            if (!mn.endsWith("_ORE") && center.getType() != Material.ANCIENT_DEBRIS && center.getType() != Material.GILDED_BLACKSTONE) {
                return false;
            }
            e.setDropItems(false);
            Material targetMat = center.getType();
            Map<Material, Integer> tally = new HashMap<Material, Integer>();
            Queue<Block> q = new ArrayDeque<Block>();
            Set<String> seen = new HashSet<String>();
            q.add(center);
            seen.add(blockKey(center));
            int broken = 0;
            while (!q.isEmpty() && broken < 32) {
                Block cur = q.poll();
                if (!cur.equals(center) && !canDrillBreakBlock(p, cur)) continue;
                addLootBundled(tally, smeltedDropForBlock(cur.getType(), 1, 2));
                if (!cur.equals(center)) cur.setType(Material.AIR, false);
                broken++;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            Block nb = cur.getRelative(dx, dy, dz);
                            String nk = blockKey(nb);
                            if (!seen.contains(nk) && nb.getType() == targetMat) {
                                seen.add(nk);
                                q.add(nb);
                            }
                        }
                    }
                }
            }
            deliverBundledLoot(p, center.getLocation().add(0.5, 0.5, 0.5), tally, true);
            p.giveExp(broken * 4);
            ColorUtil.sound(p, "entity.experience_orb.pickup", 0.9f, 1.35f);
            ColorUtil.actionBar(p, "&#00E676💎 Жилоискатель добыл всю жилу: &f" + broken + " шт.!");
            return true;
        }

        // Буровые кирки и молоты (3×3, 5×5, Куб 3×3×3, и МОЛОТ OWNER'А 10×10!)
        boolean isOwnerHammer10x10 = "hammer_owner".equals(toolId);
        boolean isTunnel5x5 = "pick_tunnel".equals(toolId);
        boolean isMeteorCube = "pick_meteor".equals(toolId);
        boolean is3x3 = "pick_krush".equals(toolId) || "pick_djamshut".equals(toolId)
                || "pick_frost".equals(toolId) || "pick_magnet".equals(toolId);

        if (!isOwnerHammer10x10 && !isTunnel5x5 && !isMeteorCube && !is3x3) {
            return false;
        }
        // Shift = точный режим 1×1 (кроме авто-плавки на центральном блоке!)
        if (p.isSneaking()) {
            e.setDropItems(false);
            ItemStack single = smeltedDropForBlock(center.getType(), isOwnerHammer10x10 ? 2 : 1, 2);
            if (single != null) p.getInventory().addItem(single);
            return true;
        }

        e.setDropItems(false);
        World w = center.getWorld();
        int minOff, maxOff;
        if (isOwnerHammer10x10) {
            minOff = -4;
            maxOff = 5; // 10×10 = 100 блоков за 1 удар!
        } else if (isTunnel5x5) {
            minOff = -2;
            maxOff = 2; // 5×5 = 25 блоков!
        } else {
            minOff = -1;
            maxOff = 1; // 3×3 или 3×3×3 куб!
        }

        boolean horizPlane = Math.abs(p.getLocation().getPitch()) > 50f;
        Vector dir = p.getLocation().getDirection();
        boolean xNormal = Math.abs(dir.getX()) > Math.abs(dir.getZ());

        boolean telekinesis = isOwnerHammer10x10 || "pick_krush".equals(toolId)
                || "pick_magnet".equals(toolId) || isTunnel5x5 || isMeteorCube;
        int smeltMult = isOwnerHammer10x10 ? 2 : 1;
        int fortuneBonus = isOwnerHammer10x10 ? 3 : ("pick_krush".equals(toolId) || isMeteorCube ? 2 : 1);

        Map<Material, Integer> tally = new HashMap<Material, Integer>();
        int brokenCount = 0;

        for (int a = minOff; a <= maxOff; a++) {
            for (int bOff = minOff; bOff <= maxOff; bOff++) {
                int cMin = isMeteorCube ? -1 : 0;
                int cMax = isMeteorCube ? 1 : 0;
                for (int cOff = cMin; cOff <= cMax; cOff++) {
                    int dx, dy, dz;
                    if (isMeteorCube) {
                        dx = a; dy = bOff; dz = cOff;
                    } else if (horizPlane) {
                        dx = a; dy = 0; dz = bOff;
                    } else if (xNormal) {
                        dx = 0; dy = a; dz = bOff;
                    } else {
                        dx = a; dy = bOff; dz = 0;
                    }
                    Block t = w.getBlockAt(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    Material m = t.getType();
                    if (m == Material.OBSIDIAN && !isOwnerHammer10x10) continue;
                    if (!t.equals(center) && !canDrillBreakBlock(p, t)) continue;
                    if (t.equals(center) && (m == Material.BEDROCK || m == Material.SPAWNER)) continue;

                    addLootBundled(tally, smeltedDropForBlock(m, smeltMult, fortuneBonus));
                    if (!t.equals(center)) {
                        t.setType(Material.AIR, false);
                    }
                    brokenCount++;

                    if (isMeteorCube && m.name().endsWith("_ORE") && rng.nextInt(100) < 8) {
                        plugin.getStore().addSnowflakes(p.getName(), 1);
                    }
                }
            }
        }

        // #6 Ледяная Кирка Стужи: замораживает любую соседнюю лаву в Обсидиан, а воду — в Плотный Лёд!
        if ("pick_frost".equals(toolId)) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        Block lb = w.getBlockAt(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                        if (lb.getType() == Material.LAVA) lb.setType(Material.OBSIDIAN, false);
                        else if (lb.getType() == Material.WATER) lb.setType(Material.PACKED_ICE, false);
                    }
                }
            }
            w.spawnParticle(Particle.SNOWFLAKE, center.getLocation().add(0.5, 0.5, 0.5), 14, 0.8, 0.8, 0.8, 0.03);
        } else if (isOwnerHammer10x10) {
            w.spawnParticle(Particle.EXPLOSION, center.getLocation().add(0.5, 0.5, 0.5), 3, 1.5, 1.5, 1.5, 0.02);
            ColorUtil.sound(p, "entity.zombie.break_wooden_door", 0.55f, 1.35f);
            ColorUtil.actionBar(p, "&#FF1744&l👑 МОЛОТ OWNER 10×10: &fСнесено &#FFD740" + brokenCount + " блоков &f(Телекинез + Плавка ×2)!");
        } else if (isMeteorCube) {
            w.spawnParticle(Particle.ELECTRIC_SPARK, center.getLocation().add(0.5, 0.5, 0.5), 18, 0.9, 0.9, 0.9, 0.08);
        }

        deliverBundledLoot(p, center.getLocation().add(0.5, 0.5, 0.5), tally, telekinesis);
        p.giveExp(Math.min(40, brokenCount));
        return true;
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
        if (plugin.getBossEvents().isProtectedEventStructureBlock(b, false)) {
            e.setCancelled(true);
            ColorUtil.actionBar(p, "&#FF1744🛡 Нельзя ставить блоки в зоне святилища активного Ивента!");
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

        // Установка 6 разновидностей РАБОЧЕГО Кастомного 3D ТНТ для рейдов!
        if (cid != null && cid.startsWith("tnt_")) {
            final Location spawnLoc = b.getLocation().add(0.5, 0.0, 0.5);
            final String tntType = cid;
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (b.getType() == Material.TNT) {
                        b.setType(Material.AIR, false);
                    }
                    spawnCustomPrimedTnt(spawnLoc, tntType, p, null);
                }
            }.runTask(plugin);
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

    public TNTPrimed spawnCustomPrimedTnt(Location spawnLoc, String tntType, Player owner, Vector launchVel) {
        if (spawnLoc.getWorld() == null) return null;
        TNTPrimed tnt = (TNTPrimed) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.TNT);
        tnt.getPersistentDataContainer().set(PROJ_TAG_KEY, PersistentDataType.STRING, tntType);
        tnt.setCustomNameVisible(true);
        int fuse = 70;
        String label = "&#EF4444&l🧨 ТНТ TIER-II";
        float yield = 6.5f;
        if ("tnt_black".equals(tntType)) {
            label = "&#A855F7&l💣 ТНТ BLACK [Обсидиан]";
            fuse = 70;
            yield = 5.5f;
        } else if ("tnt_nuke".equals(tntType)) {
            label = "&#84CC16&l☢ ЯДЕРНЫЙ ТНТ";
            fuse = 90;
            yield = 9.0f;
        } else if ("tnt_flash".equals(tntType)) {
            label = "&#FACC15&l⚡ ШТУРМОВОЙ ТНТ";
            fuse = 20;
            yield = 4.5f;
        } else if ("tnt_cryo".equals(tntType)) {
            label = "&#38BDF8&l❄ КРИО-ТНТ";
            fuse = 60;
            yield = 4.0f;
        } else if ("tnt_miner".equals(tntType)) {
            label = "&#FB923C&l⛏ БУРОВОЙ ТНТ";
            fuse = 50;
            yield = 4.0f;
        }
        tnt.setFuseTicks(fuse);
        tnt.setYield(yield);
        tnt.setCustomName(ColorUtil.c(label));
        if (launchVel != null) {
            tnt.setVelocity(launchVel);
        }
        if (owner != null) {
            try {
                TNTPrimed.class.getMethod("setSource", Entity.class).invoke(tnt, owner);
            } catch (Throwable ignored) {}
            ColorUtil.sound(owner, "entity.tnt.primed", 1.0f, 1.05f);
        } else {
            spawnLoc.getWorld().playSound(spawnLoc, "minecraft:entity.tnt.primed", 1.0f, 1.05f);
        }
        activeCustomTnts.put(tnt.getUniqueId(), new PrimedTntEntry(tntType, label, owner != null ? owner.getUniqueId() : null));
        return tnt;
    }

    // Поддержка стрельбы Кастомным ТНТ из Раздатчиков (ТНТ-пушки для рейдов!)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCustomTntDispense(BlockDispenseEvent e) {
        String cid = ItemFactory.getCustomId(e.getItem());
        if (cid == null || !cid.startsWith("tnt_")) return;
        Block b = e.getBlock();
        if (plugin.getSpawnEngine().isInSpawnWorld(b.getLocation())) {
            e.setCancelled(true);
            return;
        }
        e.setCancelled(true);
        org.bukkit.block.BlockFace face = org.bukkit.block.BlockFace.NORTH;
        if (b.getBlockData() instanceof org.bukkit.block.data.Directional) {
            face = ((org.bukkit.block.data.Directional) b.getBlockData()).getFacing();
        }
        final String tntType = cid;
        final Location spLoc = b.getRelative(face).getLocation().add(0.5, 0.1, 0.5);
        final Vector vel = new Vector(face.getModX() * 0.45, face.getModY() * 0.25 + 0.12, face.getModZ() * 0.45);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (b.getState() instanceof org.bukkit.block.Container) {
                    org.bukkit.inventory.Inventory inv = ((org.bukkit.block.Container) b.getState()).getInventory();
                    for (int s = 0; s < inv.getSize(); s++) {
                        ItemStack st = inv.getItem(s);
                        if (tntType.equals(ItemFactory.getCustomId(st))) {
                            if (st.getAmount() > 1) st.setAmount(st.getAmount() - 1);
                            else inv.setItem(s, null);
                            break;
                        }
                    }
                }
                spawnCustomPrimedTnt(spLoc, tntType, null, vel);
            }
        }.runTask(plugin);
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

    // ignoreCancelled = false: Даже если WorldGuard/Essentials отменяет ванильный ТНТ или ТНТ в воде — КАСТОМНЫЙ ТНТ СРАБАТЫВАЕТ НА 100%!
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (plugin.getSpawnEngine().isInSpawnWorld(e.getLocation())) {
            e.blockList().clear();
            e.setCancelled(true);
            return;
        }
        // Всегда защищаем временные блоки Пласта/Трапки и архитектуру Ивентов от любых взрывов!
        java.util.Iterator<Block> bit = e.blockList().iterator();
        while (bit.hasNext()) {
            Block b = bit.next();
            if (activeTempBlocks.contains(blockKey(b))
                    || plugin.getBossEvents().isLockedEventChest(b)
                    || plugin.getBossEvents().isProtectedEventStructureBlock(b, false)) {
                bit.remove();
            }
        }
        String tntType = e.getEntity().getPersistentDataContainer().get(PROJ_TAG_KEY, PersistentDataType.STRING);
        if (tntType == null) return;

        UUID eid = e.getEntity().getUniqueId();
        activeCustomTnts.remove(eid);
        if (detonatedTntIds.contains(eid)) {
            e.blockList().clear();
            return;
        }
        detonatedTntIds.add(eid);
        e.blockList().clear();
        e.setCancelled(false);

        Player srcPlayer = null;
        if (e.getEntity() instanceof TNTPrimed && ((TNTPrimed) e.getEntity()).getSource() instanceof Player) {
            srcPlayer = (Player) ((TNTPrimed) e.getEntity()).getSource();
        }
        detonateCustomTnt(e.getLocation(), tntType, srcPlayer);
    }

    private boolean isUnbreakableRaidProtected(Block b) {
        Material m = b.getType();
        if (m == Material.BEDROCK || m == Material.BARRIER || m == Material.END_PORTAL_FRAME
                || m == Material.END_PORTAL || m == Material.NETHER_PORTAL || m == Material.COMMAND_BLOCK) {
            return true;
        }
        if (activeTempBlocks.contains(blockKey(b))
                || plugin.getBossEvents().isLockedEventChest(b)
                || plugin.getBossEvents().isProtectedEventStructureBlock(b, false)) {
            return true;
        }
        DataStore.Claim cl = plugin.getStore().getClaimAt(b.getLocation());
        return cl != null && cl.isCenter(b.getLocation());
    }

    /**
     * РЕАЛЬНАЯ МЕХАНИКА ВСЕХ 6 ВИДОВ КАСТОМНОГО 3D ТНТ (РАБОТАЕТ ДАЖЕ ПОД ВОДОЙ И ЧЕРЕЗ ESSENTIALS/WG!):
     */
    public void detonateCustomTnt(Location c, String tntType, Player srcPlayer) {
        if (c == null || c.getWorld() == null || plugin.getSpawnEngine().isInSpawnWorld(c)) return;
        World w = c.getWorld();
        int cx = c.getBlockX(), cy = c.getBlockY(), cz = c.getBlockZ();

        w.playSound(c, "minecraft:entity.generic.explode", 2.0f, 0.85f);
        try {
            w.spawnParticle(Particle.EXPLOSION_EMITTER, c, 3, 1.0, 0.8, 1.0, 0.02);
        } catch (Throwable ignored) {}

        if ("tnt_tier1".equals(tntType)) {
            // 1. 🧨 ТНТ TIER-II [Усиленный ×2]: Мощный сферический взрыв R=5.5 (пробивает стены даже в воде!)
            w.playSound(c, "minecraft:entity.dragon_fireball.explode", 1.6f, 0.95f);
            w.spawnParticle(Particle.FLAME, c, 70, 2.5, 2.0, 2.5, 0.08);
            int r = 5;
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (dx * dx + dy * dy + dz * dz > 29) continue;
                        Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                        if (isUnbreakableRaidProtected(b)) continue;
                        Material m = b.getType();
                        if (m == Material.AIR || m == Material.OBSIDIAN || m == Material.CRYING_OBSIDIAN || m == Material.ANCIENT_DEBRIS) continue;
                        if (m == Material.WATER || m == Material.LAVA) {
                            if (dx * dx + dy * dy + dz * dz <= 12) b.setType(Material.AIR, false);
                            continue;
                        }
                        if (rng.nextInt(100) < 25) b.breakNaturally();
                        else b.setType(Material.AIR, false);
                    }
                }
            }
            damageNearbyPlayersFromTnt(c, srcPlayer, 7.5, 11.0);

        } else if ("tnt_black".equals(tntType)) {
            // 2. 💣 ТНТ BLACK [Ломает Обсидиан и Воду!]: Испаряет всю воду/лаву R=6 и разносит Обсидиан, Якоря и Сундуки R=4.2!
            w.playSound(c, "minecraft:entity.wither.break_block", 1.8f, 0.85f);
            w.spawnParticle(Particle.WITCH, c, 120, 3.0, 2.5, 3.0, 0.10);
            w.spawnParticle(Particle.PORTAL, c, 90, 2.5, 2.0, 2.5, 0.35);
            for (int dx = -6; dx <= 6; dx++) {
                for (int dy = -5; dy <= 5; dy++) {
                    for (int dz = -6; dz <= 6; dz++) {
                        int d2 = dx * dx + dy * dy + dz * dz;
                        if (d2 > 38) continue;
                        Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                        if (isUnbreakableRaidProtected(b)) continue;
                        Material m = b.getType();
                        if (m == Material.AIR) continue;
                        if (m == Material.WATER || m == Material.LAVA || m == Material.BUBBLE_COLUMN || m == Material.KELP || m == Material.SEAGRASS) {
                            b.setType(Material.AIR, false);
                        } else if (d2 <= 18) {
                            if (m == Material.OBSIDIAN || m == Material.CRYING_OBSIDIAN || m == Material.RESPAWN_ANCHOR
                                    || m == Material.ENDER_CHEST || m == Material.ANVIL || m == Material.ENCHANTING_TABLE) {
                                b.breakNaturally();
                            } else if (rng.nextInt(100) < 30) {
                                b.breakNaturally();
                            } else {
                                b.setType(Material.AIR, false);
                            }
                        }
                    }
                }
            }
            damageNearbyPlayersFromTnt(c, srcPlayer, 7.5, 13.0);

        } else if ("tnt_nuke".equals(tntType)) {
            // 3. ☢ ЯДЕРНЫЙ ТНТ КРУШИТЕЛЯ: Огромный кратер R=8.5 + Ядерный Гриб + Оплавленная магма + Радиация!
            w.playSound(c, "minecraft:entity.wither.spawn", 1.5f, 0.65f);
            w.playSound(c, "minecraft:entity.generic.explode", 2.5f, 0.55f);
            w.spawnParticle(Particle.EXPLOSION_EMITTER, c, 10, 3.8, 2.8, 3.8, 0.05);
            w.spawnParticle(Particle.FLAME, c, 200, 5.0, 3.5, 5.0, 0.16);
            w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, c.clone().add(0, 4, 0), 90, 2.5, 4.5, 2.5, 0.04);
            int r = 8;
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -6; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        int d2 = dx * dx + dy * dy + dz * dz;
                        if (d2 > 68) continue;
                        Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                        if (isUnbreakableRaidProtected(b)) continue;
                        Material m = b.getType();
                        if (m == Material.AIR) continue;
                        if (d2 <= 54) {
                            b.setType(Material.AIR, false);
                        } else if (m.isSolid() && dy <= -2 && rng.nextInt(100) < 28) {
                            b.setType(rng.nextBoolean() ? Material.MAGMA_BLOCK : Material.NETHERRACK, false);
                        }
                    }
                }
            }
            for (Entity en : w.getNearbyEntities(c, 12, 10, 12)) {
                if (en instanceof Player) {
                    Player vp = (Player) en;
                    vp.setFireTicks(200);
                    ColorUtil.addPot(vp, "wither", "WITHER", 160, 1);
                    ColorUtil.addPot(vp, "poison", "POISON", 140, 1);
                    if (srcPlayer != null && isEnemyTarget(srcPlayer, vp)) {
                        tagPvp30(srcPlayer, vp);
                        vp.damage(10.0, srcPlayer);
                    }
                }
            }

        } else if ("tnt_flash".equals(tntType)) {
            // 4. ⚡ ШТУРМОВОЙ МГНОВЕННЫЙ ТНТ [1 сек]: Светошумовой взрыв + мощный отброс + Слепота + Слом паутины/стёкол!
            w.playSound(c, "minecraft:entity.lightning_bolt.thunder", 1.6f, 1.35f);
            w.spawnParticle(Particle.FLASH, c, 4, 0.5, 0.5, 0.5, 0.01);
            w.spawnParticle(Particle.ELECTRIC_SPARK, c, 95, 3.0, 2.0, 3.0, 0.18);
            for (int dx = -4; dx <= 4; dx++) {
                for (int dy = -3; dy <= 3; dy++) {
                    for (int dz = -4; dz <= 4; dz++) {
                        if (dx * dx + dy * dy + dz * dz > 18) continue;
                        Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                        if (isUnbreakableRaidProtected(b)) continue;
                        Material m = b.getType();
                        if (m == Material.COBWEB || m.name().contains("GLASS") || m.name().contains("DOOR") || m.name().contains("PLANKS")) {
                            b.setType(Material.AIR, false);
                        }
                    }
                }
            }
            for (Entity en : w.getNearbyEntities(c, 8, 6, 8)) {
                if (en instanceof Player) {
                    Player vp = (Player) en;
                    Vector kb = vp.getLocation().toVector().subtract(c.toVector());
                    if (kb.lengthSquared() < 0.01) kb = new Vector(0.4, 0.5, 0.4);
                    vp.setVelocity(kb.normalize().multiply(1.55).setY(0.68));
                    if (srcPlayer == null || isEnemyTarget(srcPlayer, vp)) {
                        ColorUtil.addPot(vp, "blindness", "BLINDNESS", 50, 0);
                        ColorUtil.addPot(vp, "slowness", "SLOW", 70, 1);
                        ColorUtil.addPot(vp, "glowing", "GLOWING", 160, 0);
                        vp.setCooldown(Material.SHIELD, 80);
                        if (srcPlayer != null) tagPvp30(srcPlayer, vp);
                    }
                }
            }

        } else if ("tnt_cryo".equals(tntType)) {
            // 5. ❄ ЛЕДЯНОЙ КРИО-ТНТ: Замораживает воду в Синий Лёд, лаву в Обсидиан, землю покрывает льдом и сковывает врагов!
            w.playSound(c, "minecraft:block.glass.break", 2.0f, 0.7f);
            w.spawnParticle(Particle.SNOWFLAKE, c, 180, 4.5, 2.8, 4.5, 0.06);
            for (int dx = -6; dx <= 6; dx++) {
                for (int dy = -4; dy <= 4; dy++) {
                    for (int dz = -6; dz <= 6; dz++) {
                        if (dx * dx + dy * dy + dz * dz > 38) continue;
                        Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                        if (isUnbreakableRaidProtected(b)) continue;
                        Material m = b.getType();
                        if (m == Material.WATER || m == Material.BUBBLE_COLUMN) {
                            b.setType(Material.BLUE_ICE, false);
                        } else if (m == Material.LAVA) {
                            b.setType(Material.OBSIDIAN, false);
                        } else if (m == Material.FIRE) {
                            b.setType(Material.AIR, false);
                        } else if (m.isSolid() && w.getBlockAt(cx + dx, cy + dy + 1, cz + dz).getType() == Material.AIR && rng.nextInt(100) < 35) {
                            DataStore.Claim cl = plugin.getStore().getClaimAt(b.getLocation());
                            if (cl == null || (srcPlayer != null && cl.canAccess(srcPlayer.getName()))) {
                                b.setType(Material.PACKED_ICE, false);
                            }
                        }
                    }
                }
            }
            for (Entity en : w.getNearbyEntities(c, 8.5, 6.0, 8.5)) {
                if (en instanceof Player) {
                    Player vp = (Player) en;
                    if (srcPlayer == null || isEnemyTarget(srcPlayer, vp)) {
                        vp.setFreezeTicks(280);
                        ColorUtil.addPot(vp, "slowness", "SLOW", 140, 3);
                        ColorUtil.addPot(vp, "mining_fatigue", "SLOW_DIGGING", 120, 1);
                        if (srcPlayer != null) {
                            tagPvp30(srcPlayer, vp);
                            vp.damage(5.0, srcPlayer);
                        }
                    }
                }
            }

        } else if ("tnt_miner".equals(tntType)) {
            // 6. ⛏ ШАХТЁРСКИЙ БУРОВОЙ ТНТ [9×9×9]: Выкапывает шахту 9×9×9 с авто-плавкой и Удачей прямо в инвентарь/центр!
            w.playSound(c, "minecraft:block.anvil.use", 1.5f, 1.2f);
            w.spawnParticle(Particle.HAPPY_VILLAGER, c, 80, 3.5, 3.0, 3.5, 0.08);
            Map<Material, Integer> mined = new HashMap<Material, Integer>();
            int oresFound = 0;
            for (int dx = -4; dx <= 4; dx++) {
                for (int dy = -4; dy <= 4; dy++) {
                    for (int dz = -4; dz <= 4; dz++) {
                        Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                        if (isUnbreakableRaidProtected(b)) continue;
                        DataStore.Claim cl = plugin.getStore().getClaimAt(b.getLocation());
                        if (cl != null && (srcPlayer == null || !cl.canAccess(srcPlayer.getName()))) continue;
                        Material m = b.getType();
                        if (m == Material.AIR || m == Material.OBSIDIAN || m == Material.SPAWNER
                                || m == Material.CHEST || m == Material.BARREL || m == Material.ENDER_CHEST
                                || m.name().endsWith("SHULKER_BOX") || m == Material.WATER || m == Material.LAVA) {
                            continue;
                        }
                        if (m.name().endsWith("_ORE") || m == Material.ANCIENT_DEBRIS) {
                            oresFound++;
                            addLootBundled(mined, smeltedDropForBlock(m, 2, 2));
                        } else if (rng.nextInt(100) < 35) {
                            addLootBundled(mined, smeltedDropForBlock(m, 1, 0));
                        }
                        b.setType(Material.AIR, false);
                    }
                }
            }
            deliverBundledLoot(srcPlayer, c, mined, srcPlayer != null && srcPlayer.isOnline());
            if (srcPlayer != null && srcPlayer.isOnline()) {
                srcPlayer.giveExp(25 + oresFound * 5);
                srcPlayer.sendMessage(ColorUtil.c("&#FB923C&l⛏ [БУРОВОЙ ТНТ] &fШахта &e9×9×9 &fвыкопана! Найдено и переплавлено руд: &#00E676" + oresFound + " шт.&f!"));
            }
        }
    }

    private void damageNearbyPlayersFromTnt(Location c, Player srcPlayer, double radius, double maxDmg) {
        if (c.getWorld() == null) return;
        for (Entity en : c.getWorld().getNearbyEntities(c, radius, radius, radius)) {
            if (en instanceof Player) {
                Player vp = (Player) en;
                double d = Math.max(0.5, vp.getLocation().distance(c));
                if (d > radius) continue;
                double dmg = Math.max(2.0, maxDmg * (1.0 - d / (radius + 1.5)));
                if (srcPlayer != null && isEnemyTarget(srcPlayer, vp)) {
                    tagPvp30(srcPlayer, vp);
                    vp.damage(dmg, srcPlayer);
                } else if (srcPlayer == null || vp.equals(srcPlayer)) {
                    vp.damage(dmg * 0.5);
                }
            }
        }
    }
    @EventHandler public void onBlockExplode(BlockExplodeEvent e) {
        if (plugin.getSpawnEngine().isInSpawnWorld(e.getBlock().getLocation())) {
            e.blockList().clear();
            return;
        }
        java.util.Iterator<Block> bit = e.blockList().iterator();
        while (bit.hasNext()) {
            Block b = bit.next();
            if (activeTempBlocks.contains(blockKey(b)) || plugin.getBossEvents().isLockedEventChest(b)) bit.remove();
        }
    }

    // ================= РЕЖИМ ПВП 30 СЕК ОТ ВЗРЫВНЫХ И ТУМАННЫХ ЗЕЛИЙ =================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent e) {
        if (!(e.getPotion().getShooter() instanceof Player)) return;
        Player shooter = (Player) e.getPotion().getShooter();
        for (LivingEntity le : e.getAffectedEntities()) {
            if (le instanceof Player && !le.equals(shooter)) {
                Player target = (Player) le;
                if (plugin.getSpawnEngine().isInSpawnWorld(target.getLocation())
                        && (!plugin.getSpawnEngine().isInsidePvpZone(target.getLocation())
                        || !plugin.getSpawnEngine().isInsidePvpZone(shooter.getLocation()))) {
                    e.setIntensity(le, 0.0);
                    continue;
                }
                if (isEnemyTarget(shooter, target)) {
                    tagPvp30(shooter, target);
                } else {
                    e.setIntensity(le, 0.0);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLingeringApply(AreaEffectCloudApplyEvent e) {
        if (!(e.getEntity().getSource() instanceof Player)) return;
        Player shooter = (Player) e.getEntity().getSource();
        java.util.Iterator<LivingEntity> it = e.getAffectedEntities().iterator();
        while (it.hasNext()) {
            LivingEntity le = it.next();
            if (le instanceof Player && !le.equals(shooter)) {
                Player target = (Player) le;
                if (plugin.getSpawnEngine().isInSpawnWorld(target.getLocation())
                        && (!plugin.getSpawnEngine().isInsidePvpZone(target.getLocation())
                        || !plugin.getSpawnEngine().isInsidePvpZone(shooter.getLocation()))) {
                    it.remove();
                    continue;
                }
                if (isEnemyTarget(shooter, target)) {
                    tagPvp30(shooter, target);
                } else {
                    it.remove();
                }
            }
        }
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
            if (plugin.getSpawnEngine().getCasePedestalAt(b) != null || plugin.getSpawnEngine().getSantaGiftAt(b) >= 0) {
                e.setCancelled(true);
                return;
            }
            if (plugin.getBossEvents().isLockedEventChest(b)) {
                e.setCancelled(true);
                if (e.getPlayer() instanceof Player) {
                    ColorUtil.actionBar((Player) e.getPlayer(), "&#FF1744🔒 Сокровищница Ивента ещё запечатана! Дождитесь открытия!");
                }
            }
        }
    }

    // ================= КЛИКИ ПО ШАЛКЕР-КЕЙСАМ, КАЗИНО, ПАРКУРУ И КАСТОМНЫМ ВЕЩАМ РВ =================
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!plugin.getStore().isAuthenticated(p)) { e.setCancelled(true); return; }

        // 1. Нажимные плиты Небесного Паркура v5.8 (32 острова: Старт Y=102, Чекпоинты #1/#2/#3, Финиш Y=130!)
        if (e.getAction() == Action.PHYSICAL && e.getClickedBlock() != null
                && plugin.getSpawnEngine().isInSpawnWorld(e.getClickedBlock().getLocation())) {
            Block pb = e.getClickedBlock();
            if (pb.getY() >= 101) {
                long now = System.currentTimeMillis();
                Long last = parkourPlateCd.get(p.getUniqueId());
                if (last != null && now - last.longValue() < 1500L) return;
                parkourPlateCd.put(p.getUniqueId(), Long.valueOf(now));
                if (pb.getType() == Material.LIGHT_WEIGHTED_PRESSURE_PLATE) {
                    Location cpLoc = pb.getLocation().add(0.5, 0.2, 0.5);
                    cpLoc.setYaw(p.getLocation().getYaw());
                    plugin.getStore().setParkourCp(p, cpLoc);
                    if (p.isFlying()) p.setFlying(false);
                    if (p.getGameMode() == GameMode.SURVIVAL) p.setAllowFlight(false);
                    ColorUtil.sound(p, "entity.experience_orb.pickup", 1f, 1.35f);
                    if (pb.getY() <= 104) {
                        parkourRunStartMs.put(p.getUniqueId(), Long.valueOf(now));
                        p.sendMessage(ColorUtil.c("&#40C4FF&l🏃 [ПАРКУР] &fСтарт забега на &e32 острова&f! Полёт отключён. Выйти: &f/parkour leave"));
                    } else {
                        Long stMs = parkourRunStartMs.get(p.getUniqueId());
                        String elapsed = stMs != null ? (" &8(" + String.format(java.util.Locale.US, "%.1fс", Double.valueOf((now - stMs.longValue()) / 1000.0)) + ")") : "";
                        p.sendMessage(ColorUtil.c("&#40C4FF🏃 Чекпоинт сохранён!" + elapsed + " &7При падении вы вернётесь сюда автоматически!"));
                        if (plugin.getOwnerGiveaway() != null) plugin.getOwnerGiveaway().notifyParkourProgress();
                    }
                } else if (pb.getType() == Material.HEAVY_WEIGHTED_PRESSURE_PLATE && pb.getY() >= 126) {
                    if (plugin.getOwnerGiveaway() != null) plugin.getOwnerGiveaway().notifyParkourProgress();
                    plugin.getStore().setParkourCp(p, null);
                    Long stMs = parkourRunStartMs.remove(p.getUniqueId());
                    String timeStr = stMs != null ? String.format(java.util.Locale.US, "%.1f сек.", Double.valueOf((now - stMs.longValue()) / 1000.0)) : "рекордное время";
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
                                + " &fпрошёл Небесный Паркур (32 острова) за &#FFD740" + timeStr
                                + " &fи получил &a+25 000$&f, &b+15❖&f, &b+15❄ &fи титул &b[Паркурист]&f!"));
                    } else {
                        p.sendMessage(ColorUtil.c("&#40C4FF🏃 Вы прошли паркур за &#FFD740" + timeStr + "&#40C4FF! Повторная награда через " + ColorUtil.fmtTime((ready - now) / 1000L)));
                    }
                    p.teleport(plugin.getSpawnEngine().getSpawnArrival());
                }
            }
            return;
        }

        if (e.getHand() == EquipmentSlot.OFF_HAND) return;

        // 2. Шалкер-Кейсы, Подарки Санты 10/10, Запечатанные Сундуки Боссов и Автоматы Казино
        Block cb = e.getClickedBlock();
        if (cb != null) {
            int giftIdx = plugin.getSpawnEngine().getSantaGiftAt(cb);
            if (giftIdx >= 0) {
                e.setCancelled(true);
                handleSantaGiftClick(p, giftIdx);
                return;
            }
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
        } else if ("orb_recall".equals(cid)) {
            e.setCancelled(true);
            if (!checkAndSetCd(p, "orb_recall", 20)) return;
            consumeOne(p, hand);
            final long startMs = System.currentTimeMillis();
            p.sendMessage(ColorUtil.c("&#00E5FF🧪 [Сфера Возврата] &fЗапущена экстренная эвакуация на спавн (&e3 сек.&f) — не получайте урон!"));
            ColorUtil.sound(p, "block.beacon.power_select", 1.0f, 1.35f);
            new BukkitRunnable() {
                int step = 0;
                @Override
                public void run() {
                    if (!p.isOnline() || p.isDead()) { cancel(); return; }
                    Long dmgAt = recallDamagedAt.get(p.getUniqueId());
                    if (dmgAt != null && dmgAt.longValue() >= startMs) {
                        cancel();
                        p.sendMessage(ColorUtil.c("&#FF1744✖ Телепортация Сферы Возврата сбита полученным уроном!"));
                        ColorUtil.sound(p, "entity.item.break", 1.0f, 0.8f);
                        return;
                    }
                    step++;
                    p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1.0, 0), 14, 0.45, 0.8, 0.45, 0.03);
                    if (step < 3) {
                        ColorUtil.actionBar(p, "&#00E5FF🧪 Эвакуация через &f" + (3 - step) + " сек...");
                        return;
                    }
                    cancel();
                    plugin.getHud().clearCombat(p.getUniqueId());
                    p.teleport(plugin.getSpawnEngine().getSpawnArrival());
                    ColorUtil.sound(p, "entity.enderman.teleport", 1.0f, 1.25f);
                    p.sendTitle(ColorUtil.c("&#00E5FF&l🧪 ЭВАКУАЦИЯ!"), ColorUtil.c("&fВы спасены Сферой Возврата!"), 5, 35, 10);
                }
            }.runTaskTimer(plugin, 20L, 20L);
        } else if ("plast".equals(cid) || "trapka".equals(cid) || "dezorient".equals(cid)
                || "yavnaya_pyl".equals(cid) || "bozhya_aura".equals(cid)
                || "ognenny_smerch".equals(cid) || "pautinnaya_granata".equals(cid)
                || "bozhya_kara".equals(cid) || "grav_sfera".equals(cid) || "sneg_zavesa".equals(cid)) {
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
        if (c1 != null && c2 != null) {
            if (c1.equals(c2) && !c1.friendlyFire) return false;
            if (c1.allies.contains(c2.id)) return false;
        }
        return true;
    }

    private void handleFtConsumable(final Player p, ItemStack hand, String cid) {
        if (plugin.getSpawnEngine().isInSpawnWorld(p.getLocation())
                && !plugin.getSpawnEngine().isInsidePvpZone(p.getLocation())) {
            p.sendMessage(ColorUtil.c("&#FF1744✖ Расходники FT можно использовать в мире выживания (&a/rtp&#FF1744) или на &e/warp pvp&#FF1744!"));
            return;
        }
        if ("plast".equals(cid)) {
            if (!checkAndSetCd(p, "plast", 10)) return;
            consumeOne(p, hand);
            spawnTemporaryPlast(p);
            for (Entity en : p.getNearbyEntities(7, 5, 7)) {
                if (en instanceof Player && isEnemyTarget(p, (Player) en)) {
                    tagPvp30(p, (Player) en);
                }
            }
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
                    tagPvp30(p, v);
                    ColorUtil.addPot(v, "blindness", "BLINDNESS", 140, 0);
                    ColorUtil.addPot(v, "nausea", "CONFUSION", 140, 1);
                    ColorUtil.addPot(v, "slowness", "SLOW", 140, 1);
                    v.sendMessage(ColorUtil.c("&#E040FB👁 Вы поражены Дезориентацией игрока &f" + p.getName() + " &#E040FB(+30 сек ПвП)!"));
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
                    if (isEnemyTarget(p, v)) tagPvp30(p, v);
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
            for (Entity en : p.getNearbyEntities(8, 5, 8)) {
                if (en instanceof Player && isEnemyTarget(p, (Player) en)) tagPvp30(p, (Player) en);
            }
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
                    tagPvp30(p, v);
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
        } else if ("bozhya_kara".equals(cid)) {
            if (!checkAndSetCd(p, "bozhya_kara", 22)) return;
            consumeOne(p, hand);
            int hits = 0;
            for (Entity en : p.getNearbyEntities(8, 6, 8)) {
                if (en instanceof Player && isEnemyTarget(p, (Player) en)) {
                    Player v = (Player) en;
                    tagPvp30(p, v);
                    v.getWorld().strikeLightningEffect(v.getLocation());
                    v.setFireTicks(100);
                    v.damage(12.0, p);
                    hits++;
                }
            }
            ColorUtil.sound(p, "entity.lightning_bolt.thunder", 1.0f, 1.1f);
            p.sendMessage(ColorUtil.c("&#FACC15⚡ Божья Кара Зевса поразила молнией врагов: &f" + hits));
        } else if ("grav_sfera".equals(cid)) {
            if (!checkAndSetCd(p, "grav_sfera", 20)) return;
            consumeOne(p, hand);
            Location pullCenter = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(2.0));
            p.getWorld().spawnParticle(Particle.PORTAL, pullCenter.clone().add(0, 1, 0), 95, 2.0, 1.5, 2.0, 0.25);
            int pulled = 0;
            for (Entity en : p.getNearbyEntities(9, 6, 9)) {
                if (en instanceof Player && isEnemyTarget(p, (Player) en)) {
                    Player v = (Player) en;
                    tagPvp30(p, v);
                    Vector pull = pullCenter.toVector().subtract(v.getLocation().toVector());
                    v.setVelocity(pull.multiply(0.38).setY(0.32));
                    ColorUtil.addPot(v, "slowness", "SLOW", 60, 1);
                    pulled++;
                }
            }
            ColorUtil.sound(p, "entity.enderman.teleport", 1.0f, 0.75f);
            p.sendMessage(ColorUtil.c("&#C084FC🕳 Гравитационная Сфера стянула врагов: &f" + pulled));
        } else if ("sneg_zavesa".equals(cid)) {
            if (!checkAndSetCd(p, "sneg_zavesa", 16)) return;
            consumeOne(p, hand);
            p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 1, 0), 90, 2.5, 1.2, 2.5, 0.08);
            p.setVelocity(p.getLocation().getDirection().normalize().multiply(1.35).setY(0.42));
            ColorUtil.addPot(p, "speed", "SPEED", 160, 2);
            for (Entity en : p.getNearbyEntities(6, 4, 6)) {
                if (en instanceof Player && isEnemyTarget(p, (Player) en)) {
                    Player v = (Player) en;
                    tagPvp30(p, v);
                    ColorUtil.addPot(v, "blindness", "BLINDNESS", 60, 0);
                    v.setFreezeTicks(140);
                }
            }
            ColorUtil.sound(p, "entity.breeze.jump", 1.0f, 1.2f);
            p.sendMessage(ColorUtil.c("&#7DD3FC❄ Снежная Завеса активирована!"));
        }
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player)) return;
        Player p = (Player) e.getEntity().getShooter();
        ItemStack main = p.getInventory().getItemInMainHand();
        ItemStack off = p.getInventory().getItemInOffHand();
        String mainId = ItemFactory.getCustomId(main);
        String offId = ItemFactory.getCustomId(off);
        if ("snezok_zamorozki".equals(mainId)) {
            e.getEntity().getPersistentDataContainer().set(PROJ_TAG_KEY, PersistentDataType.STRING, "frost_ball");
            return;
        }
        if ("trident_poseidon".equals(mainId)) {
            e.getEntity().getPersistentDataContainer().set(PROJ_TAG_KEY, PersistentDataType.STRING, "trident_poseidon");
            return;
        }
        if (e.getEntity() instanceof Arrow) {
            if (mainId != null && mainId.startsWith("bow_")) {
                e.getEntity().getPersistentDataContainer().set(PROJ_TAG_KEY, PersistentDataType.STRING, mainId);
            } else if (offId != null && offId.startsWith("bow_")) {
                e.getEntity().getPersistentDataContainer().set(PROJ_TAG_KEY, PersistentDataType.STRING, offId);
            }
            // Detect custom arrow in offhand or first arrow slot in inventory!
            String arrowType = null;
            if (offId != null && offId.startsWith("arrow_")) {
                arrowType = offId;
            } else {
                for (ItemStack invIt : p.getInventory().getContents()) {
                    if (invIt != null && invIt.getType() == Material.ARROW) {
                        String aid = ItemFactory.getCustomId(invIt);
                        if (aid != null && aid.startsWith("arrow_")) {
                            arrowType = aid;
                            break;
                        }
                    }
                }
            }
            if (arrowType != null) {
                e.getEntity().getPersistentDataContainer().set(PROJ_ARROW_KEY, PersistentDataType.STRING, arrowType);
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        String ptype = e.getEntity().getPersistentDataContainer().get(PROJ_TAG_KEY, PersistentDataType.STRING);
        String atype = e.getEntity().getPersistentDataContainer().get(PROJ_ARROW_KEY, PersistentDataType.STRING);
        if ("frost_ball".equals(ptype) && e.getHitEntity() instanceof Player && e.getEntity().getShooter() instanceof Player) {
            Player shooter = (Player) e.getEntity().getShooter();
            Player hit = (Player) e.getHitEntity();
            if (isEnemyTarget(shooter, hit)) {
                tagPvp30(shooter, hit);
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
            if (e.getEntity().getShooter() instanceof Player) {
                Player shooter = (Player) e.getEntity().getShooter();
                for (Entity en : center.getWorld().getNearbyEntities(center, 3.5, 3.0, 3.5)) {
                    if (en instanceof Player && isEnemyTarget(shooter, (Player) en)) {
                        tagPvp30(shooter, (Player) en);
                    }
                }
            }
            final java.util.List<Block> placed = new java.util.ArrayList<Block>();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block b = center.getWorld().getBlockAt(center.getBlockX() + dx, center.getBlockY(), center.getBlockZ() + dz);
                    if (b.getType() == Material.AIR && !activeTempBlocks.contains(blockKey(b))) {
                        activeTempBlocks.add(blockKey(b));
                        allSavedTempBlocks.put(b, b.getBlockData().clone());
                        b.setType(Material.COBWEB, false);
                        placed.add(b);
                    }
                }
            }
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (Block b : placed) {
                        activeTempBlocks.remove(blockKey(b));
                        allSavedTempBlocks.remove(b);
                        if (b.getType() == Material.COBWEB) b.setType(Material.AIR, false);
                    }
                }
            }.runTaskLater(plugin, 160L);
        }
        // Разрывная стрела на блоке
        if ("arrow_explosive".equals(atype) && e.getHitBlock() != null && e.getEntity().getShooter() instanceof Player) {
            Location loc = e.getHitBlock().getLocation().add(0.5, 1.0, 0.5);
            if (!plugin.getSpawnEngine().isInSpawnWorld(loc) || plugin.getSpawnEngine().isInsidePvpZone(loc)) {
                loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 3, 0.5, 0.5, 0.5, 0.02);
                ColorUtil.sound((Player) e.getEntity().getShooter(), "entity.generic.explode", 0.7f, 1.2f);
            }
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
                tagPvp30(p, v);
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
        allSavedTempBlocks.putAll(saved);
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<Block, org.bukkit.block.data.BlockData> en : saved.entrySet()) {
                    Block b = en.getKey();
                    activeTempBlocks.remove(blockKey(b));
                    allSavedTempBlocks.remove(b);
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
        final int giftIdx = plugin.getSpawnEngine().getGiftIndexByEntity(target);
        if (giftIdx >= 0) {
            e.setCancelled(true);
            handleSantaGiftClick(e.getPlayer(), giftIdx);
            return;
        }
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
        final int giftIdx = plugin.getSpawnEngine().getGiftIndexByEntity(target);
        if (giftIdx >= 0) {
            e.setCancelled(true);
            handleSantaGiftClick(e.getPlayer(), giftIdx);
            return;
        }
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

    private void handleSantaGiftClick(Player p, int giftIdx) {
        if (!plugin.getStore().isAuthenticated(p)) return;
        long now = System.currentTimeMillis();
        Long last = npcClickCd.get(p.getUniqueId());
        if (last != null && now - last.longValue() < 350L) return;
        npcClickCd.put(p.getUniqueId(), Long.valueOf(now));

        if (plugin.getStore().hasFoundSpawnGift(p.getName(), giftIdx)) {
            int found = plugin.getStore().getFoundSpawnGiftCount(p.getName());
            p.sendMessage(ColorUtil.c("&#FFD740🎁 Вы уже нашли Подарок #" + (giftIdx + 1)
                    + "! Всего собрано: &#00E676" + found + "/10 &7(/gifts)"));
            return;
        }
        plugin.getStore().markFoundSpawnGift(p.getName(), giftIdx);
        plugin.getStore().addCoins(p.getName(), 3500);
        plugin.getStore().addSnowflakes(p.getName(), 3);
        int total = plugin.getStore().getFoundSpawnGiftCount(p.getName());
        ColorUtil.sound(p, "entity.player.levelup", 1.0f, 1.35f);
        p.sendMessage(ColorUtil.c("&#00E676🎁 Найден Секретный Подарок Санты #" + (giftIdx + 1)
                + "! &a+3 500$ &fи &b+3 Снежинки ❄ &8(&e" + total + "/10&8)"));
        if (total >= 10) {
            plugin.getStore().addCoins(p.getName(), 25000);
            plugin.getStore().addRelics(p.getName(), 25);
            plugin.getStore().addCaseKeys(p.getName(), "donate", 1);
            ColorUtil.spawnFirework(p.getLocation());
            Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l🎅 [ПОДАРКИ САНТЫ] &fИгрок &#00E676" + p.getName()
                    + " &fнашёл ВСЕ 10/10 подарков на спавне и забрал &a+25 000$&f, &b+25❖ &fи &#FF1744Донат-Кейс&f!"));
        }
        plugin.getStore().save();
        plugin.getHud().updatePlayerHud(p);
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
        } else if ("action:wheel".equals(act)) {
            plugin.getSpawnEngine().spinFortuneWheel(p, plugin.getStore().getSnowflakes(p.getName()) >= 5);
        } else if ("action:top".equals(act)) {
            p.performCommand("top");
        } else if ("action:train_hint".equals(act)) {
            p.sendMessage(ColorUtil.c("&#FF5252🥊 Бейте Незеритовые Манекены рядом для точного замера урона, критов и баффов!"));
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
                } else if ("tal_owner".equals(tid)) {
                    p.setHealth(p.getMaxHealth());
                    ColorUtil.addPot(p, "absorption", "ABSORPTION", 600, 4);
                    ColorUtil.addPot(p, "regeneration", "REGENERATION", 300, 3);
                    ColorUtil.addPot(p, "strength", "INCREASE_DAMAGE", 300, 2);
                    for (Entity en : p.getNearbyEntities(8, 5, 8)) {
                        if (en instanceof Player && !en.equals(p)) {
                            en.getWorld().strikeLightningEffect(en.getLocation());
                            en.setFireTicks(120);
                        }
                    }
                    if (curCharges > 1) {
                        ItemStack nextTotem = ItemFactory.talOwner(curCharges - 1);
                        if (inOff) p.getInventory().setItemInOffHand(nextTotem);
                        else p.getInventory().setItemInMainHand(nextTotem);
                        p.sendMessage(ColorUtil.c("&#FF1744👑 &#FFD740&lТАЛИСМАН OWNER &fвоскресил вас со 100% ХП и +10 Золотыми ❤! Осталось зарядов: &#FFD740" + (curCharges - 1) + "/5"));
                    } else {
                        p.sendMessage(ColorUtil.c("&#FF1744👑 Использован финальный заряд Божественного Талисмана OWNER (0/5)!"));
                    }
                } else if ("tal_bezdn".equals(tid)) {
                    p.setHealth(p.getMaxHealth());
                    ColorUtil.addPot(p, "absorption", "ABSORPTION", 360, 2);
                    if (curCharges > 1) {
                        ItemStack nextTotem = ItemFactory.talBezdn(curCharges - 1);
                        if (inOff) p.getInventory().setItemInOffHand(nextTotem);
                        else p.getInventory().setItemInMainHand(nextTotem);
                        p.sendMessage(ColorUtil.c("&#8B5CF6🔮 Талисман Бездны спас вам жизнь! Осталось зарядов: &a" + (curCharges - 1) + "/2"));
                    } else {
                        p.sendMessage(ColorUtil.c("&#8B5CF6🔮 Использован последний заряд Талисмана Бездны (0/2)!"));
                    }
                } else if ("tal_karatelya".equals(tid)) {
                    ColorUtil.addPot(p, "strength", "INCREASE_DAMAGE", 260, 2);
                    for (Entity en : p.getNearbyEntities(6, 4, 6)) {
                        if (en instanceof Player && !en.equals(p)) {
                            en.setFireTicks(120);
                            ColorUtil.addPot((Player) en, "wither", "WITHER", 120, 1);
                        }
                    }
                    p.sendMessage(ColorUtil.c("&#EF4444⚔ Сработал Талисман Карателя! Враги охвачены Иссушением!"));
                } else if ("tal_zeusa".equals(tid)) {
                    for (Entity en : p.getNearbyEntities(7, 5, 7)) {
                        if (en instanceof Player && !en.equals(p)) {
                            en.getWorld().strikeLightningEffect(en.getLocation());
                            ((Player) en).damage(6.0, p);
                        }
                    }
                    p.sendMessage(ColorUtil.c("&#FACC15⚡ Сработал Талисман Зевса! Громовой удар по врагам!"));
                } else if ("tal_draculy".equals(tid)) {
                    p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + 10.0));
                    for (Entity en : p.getNearbyEntities(6, 4, 6)) {
                        if (en instanceof Player && !en.equals(p)) {
                            ((Player) en).damage(6.0, p);
                        }
                    }
                    p.sendMessage(ColorUtil.c("&#E11D48🩸 Сработал Талисман Дракулы!"));
                } else if ("tal_kolossa".equals(tid)) {
                    ColorUtil.addPot(p, "absorption", "ABSORPTION", 500, 5);
                    ColorUtil.addPot(p, "resistance", "DAMAGE_RESISTANCE", 260, 2);
                    p.sendMessage(ColorUtil.c("&#6EE7B7🛡 Сработал Талисман Колосса (+12 Золотых ❤)!"));
                } else if ("tal_zhneca".equals(tid)) {
                    for (Entity en : p.getNearbyEntities(7, 4, 7)) {
                        if (en instanceof Player && !en.equals(p)) {
                            ColorUtil.addPot((Player) en, "blindness", "BLINDNESS", 100, 0);
                            ColorUtil.addPot((Player) en, "weakness", "WEAKNESS", 140, 1);
                        }
                    }
                    p.sendMessage(ColorUtil.c("&#A855F7💀 Сработал Талисман Жнеца!"));
                } else if ("tal_severa".equals(tid)) {
                    for (Entity en : p.getNearbyEntities(7, 4, 7)) {
                        if (en instanceof Player && !en.equals(p)) {
                            en.setFreezeTicks(220);
                            ColorUtil.addPot((Player) en, "slowness", "SLOW", 140, 3);
                        }
                    }
                    p.sendMessage(ColorUtil.c("&#38BDF8❄ Сработал Талисман Северной Вьюги!"));
                } else if ("tal_imperatora".equals(tid)) {
                    ColorUtil.addPot(p, "absorption", "ABSORPTION", 450, 3);
                    ColorUtil.addPot(p, "strength", "INCREASE_DAMAGE", 260, 2);
                    p.sendMessage(ColorUtil.c("&#FB923C👑 Сработал Талисман Императора (+8 Золотых ❤ + Сила III)!"));
                }
            }
        }.runTaskLater(plugin, 1L);
    }

    // ================= PVP, АРЕНА, КАБИНКИ, ВАМПИРИЗМ, БРОНЯ, СТРЕЛЫ, ЛУКИ И БОССЫ =================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player && e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            Player fp = (Player) e.getEntity();
            if ("mace_krush".equals(ItemFactory.getCustomId(fp.getInventory().getItemInMainHand()))) {
                e.setCancelled(true);
                return;
            }
        }
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

        // DPS Training Dummy at /warp train!
        if (e.getEntity().getScoreboardTags().contains("kg_dps_dummy")) {
            e.setCancelled(true);
            if (e.getDamager() instanceof Player) {
                Player hitter = (Player) e.getDamager();
                boolean isCrit = hitter.getFallDistance() > 0.0f && !hitter.isOnGround() && !hitter.isSwimming();
                double rawDmg = e.getDamage() * (isCrit ? 1.5 : 1.0);
                double netDmg = Math.max(0.5, rawDmg * 0.42); // Simulated Netherite Prot IV reduction
                ColorUtil.sound(hitter, isCrit ? "entity.player.attack.crit" : "entity.player.attack.strong", 0.9f, 1.15f);
                ColorUtil.actionBar(hitter, (isCrit ? "&#FFD740&l⚡ КРИТ! " : "&#FF5252⚔ Удар: ")
                        + "&fЧистый: &#FF8A80" + String.format("%.1f", Double.valueOf(rawDmg)) + " ❤ &8│ "
                        + "&fПо Незериту: &#00E676" + String.format("%.1f", Double.valueOf(netDmg)) + " ❤");
                spawnDamageIndicator(e.getEntity().getLocation(), netDmg);
            }
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
            if (c1 != null && c2 != null) {
                if (c1.equals(c2) && !c1.friendlyFire) {
                    e.setCancelled(true);
                    ColorUtil.actionBar(attacker, "&#B388FF🛡 Соклановец! Огонь по своим отключён (/clan pvp).");
                    return;
                }
                if (c1.allies.contains(c2.id)) {
                    e.setCancelled(true);
                    ColorUtil.actionBar(attacker, "&#69F0AE🤝 Игрок из союзного клана [" + c2.tag + "]!");
                    return;
                }
            }

            // 30-СЕКУНДНЫЙ РЕЖИМ ПВП СВЕРХУ ЭКРАНА НАД ИВЕНТАМИ!
            tagPvp30(attacker, victim);
            recallDamagedAt.put(victim.getUniqueId(), Long.valueOf(System.currentTimeMillis()));

            String weaponId = ItemFactory.getCustomId(attacker.getInventory().getItemInMainHand());
            String offId = ItemFactory.getCustomId(attacker.getInventory().getItemInOffHand());
            String atkChest = ItemFactory.getCustomId(attacker.getInventory().getChestplate());
            String vicChest = ItemFactory.getCustomId(victim.getInventory().getChestplate());
            String atkArmorSet = HudManager.detectFullCustomArmorSet(attacker);
            String vicArmorSet = HudManager.detectFullCustomArmorSet(victim);

            // Вампиризм (Меч Дракулы / Меч OWNER / Коса Смерти Жнеца / Талисман Дракулы / Сет Дракулы / Элитры Вампира)
            if ("sword_dracula".equals(weaponId) || "sword_owner".equals(weaponId) || "scythe_reaper".equals(weaponId)
                    || "tal_draculy".equals(offId) || "elytra_vampire".equals(atkChest)
                    || "dracula".equals(atkArmorSet)) {
                double heal = Math.min(5.0, e.getFinalDamage() * 0.25);
                attacker.setHealth(Math.min(attacker.getMaxHealth(), attacker.getHealth() + heal));
            }
            // Коса Смерти Жнеца (scythe_reaper): Казнь (+35% урона по целям < 16 HP) + Иссушение II!
            if ("scythe_reaper".equals(weaponId)) {
                ColorUtil.addPot(victim, "wither", "WITHER", 80, 1);
                victim.getWorld().spawnParticle(Particle.SOUL, victim.getLocation().add(0, 1.1, 0), 10, 0.35, 0.4, 0.35, 0.03);
                if (victim.getHealth() <= 16.0) {
                    e.setDamage(e.getDamage() * 1.35);
                    ColorUtil.actionBar(attacker, "&#E11D48⚔ ЖАТВА ДУШ: Казнь +35% урона!");
                }
            }
            // Громовая Секира Тора (axe_thor): Слом Щита + Цепная Молния (КД 7 сек)!
            if ("axe_thor".equals(weaponId)) {
                victim.setCooldown(Material.SHIELD, 100);
                String thorKey = attacker.getUniqueId() + ":thor_bolt";
                long nowMs = System.currentTimeMillis();
                Long boltReady = itemCooldowns.get(thorKey);
                if (boltReady == null || nowMs >= boltReady.longValue()) {
                    itemCooldowns.put(thorKey, Long.valueOf(nowMs + 7000L));
                    victim.getWorld().strikeLightningEffect(victim.getLocation());
                    e.setDamage(e.getDamage() + 5.0);
                    ColorUtil.addPot(victim, "slowness", "SLOW", 40, 1);
                    ColorUtil.actionBar(attacker, "&#38BDF8🪓 СЕКИРА ТОРА: Удар Грома (+5⚡ урона + Слом Щита)!");
                }
            }
            // Сет Фантома: +18% крита в прыжке!
            if ("phantom".equals(atkArmorSet) && attacker.getFallDistance() > 0.05f && !attacker.isOnGround()) {
                e.setDamage(e.getDamage() * 1.18);
            }
            // Булава Крушителя: удар с воздуха!
            if ("mace_krush".equals(weaponId) && attacker.getFallDistance() > 1.5f) {
                double extra = Math.min(12.0, attacker.getFallDistance() * 1.6);
                e.setDamage(e.getDamage() + extra);
                victim.getWorld().spawnParticle(Particle.EXPLOSION, victim.getLocation().add(0, 1, 0), 3, 0.5, 0.4, 0.5, 0.02);
                attacker.setFallDistance(0f);
            }
            // Ответка сетов брони и элитр
            if ("elytra_phoenix".equals(vicChest)) attacker.setFireTicks(80);
            if ("frost".equals(vicArmorSet)) attacker.setFreezeTicks(110);
            if (("elytra_thunder".equals(atkChest) || "zeus".equals(vicArmorSet)) && rng.nextInt(100) < 20) {
                Player targetBolt = "zeus".equals(vicArmorSet) ? attacker : victim;
                targetBolt.getWorld().strikeLightningEffect(targetBolt.getLocation());
                e.setDamage(e.getDamage() + 3.0);
            }
            // Окисление брони (Меч Крушителя и Меч OWNER)
            if ("sword_krush".equals(weaponId) || "sword_owner".equals(weaponId)) {
                int shred = "sword_owner".equals(weaponId) ? 5 : 3;
                for (ItemStack arm : victim.getInventory().getArmorContents()) {
                    if (arm != null && arm.hasItemMeta() && arm.getItemMeta() instanceof Damageable) {
                        ItemMeta m = arm.getItemMeta();
                        Damageable d = (Damageable) m;
                        d.setDamage(d.getDamage() + shred);
                        arm.setItemMeta(m);
                    }
                }
            }
            // Кастомные Луки, Стрелы и Трезубец Посейдона!
            if (e.getDamager() instanceof Projectile) {
                String pTag = e.getDamager().getPersistentDataContainer().get(PROJ_TAG_KEY, PersistentDataType.STRING);
                String aTag = e.getDamager().getPersistentDataContainer().get(PROJ_ARROW_KEY, PersistentDataType.STRING);
                if ("trident_poseidon".equals(pTag)) {
                    victim.getWorld().strikeLightningEffect(victim.getLocation());
                    e.setDamage(e.getDamage() + 4.0);
                }
                if ("bow_ice_rw".equals(pTag) || "arrow_ice".equals(aTag)) {
                    victim.setFreezeTicks(180);
                    ColorUtil.addPot(victim, "slowness", "SLOW", 100, 3);
                }
                if ("bow_inferno".equals(pTag)) {
                    victim.setFireTicks(140);
                    ColorUtil.addPot(victim, "wither", "WITHER", 100, 1);
                }
                if ("bow_zeus".equals(pTag) || "arrow_lightning".equals(aTag)) {
                    victim.getWorld().strikeLightningEffect(victim.getLocation());
                    e.setDamage(e.getDamage() + 4.0);
                    victim.setCooldown(Material.SHIELD, 100);
                }
                if ("bow_vampire".equals(pTag) || "arrow_vampire".equals(aTag)) {
                    attacker.setHealth(Math.min(attacker.getMaxHealth(), attacker.getHealth() + 4.5));
                }
                if ("arrow_explosive".equals(aTag)) {
                    victim.getWorld().spawnParticle(Particle.EXPLOSION, victim.getLocation().add(0, 1, 0), 3, 0.4, 0.4, 0.4, 0.02);
                    e.setDamage(e.getDamage() + 5.0);
                }
                if ("arrow_poison".equals(aTag)) {
                    ColorUtil.addPot(victim, "poison", "POISON", 120, 2);
                    ColorUtil.addPot(victim, "wither", "WITHER", 100, 1);
                    ColorUtil.addPot(victim, "weakness", "WEAKNESS", 120, 1);
                }
                if ("arrow_anti_fly".equals(aTag)) {
                    if (victim.isGliding()) victim.setGliding(false);
                    victim.setVelocity(new Vector(0, -1.1, 0));
                    victim.sendMessage(ColorUtil.c("&#A855F7⛓ Анти-Флай Стрела ПВО сбила ваш полёт!"));
                }
            }
            spawnDamageIndicator(victim.getLocation(), e.getFinalDamage());
        }
    }

    private void spawnDamageIndicator(Location base, double dmg) {
        if (dmg < 0.5 || base.getWorld() == null) return;
        Location hl = base.clone().add((rng.nextDouble() - 0.5) * 0.7, 1.4, (rng.nextDouble() - 0.5) * 0.7);
        final ArmorStand st = (ArmorStand) base.getWorld().spawnEntity(hl, EntityType.ARMOR_STAND);
        st.setPersistent(false);
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
        plugin.getHud().clearCombat(dead.getUniqueId());
        killStreaks.remove(dead.getUniqueId());
        plugin.getStore().setBack(dead, dead.getLocation());
        plugin.getStore().addDeath(dead.getName());
        plugin.getSpawnEngine().notifyKabinkaDeath(dead.getLocation());

        Player killer = dead.getKiller();
        if (killer != null && !killer.equals(dead)) {
            plugin.getStore().addKill(killer.getName());
            DataStore.Clan kClan = plugin.getStore().getClanOf(killer.getName());
            double clanBonus = kClan != null ? kClan.level * 50.0 : 0.0;
            if (kClan != null) kClan.kills++;
            plugin.getStore().addCoins(killer.getName(), 250.0 + clanBonus);

            int streak = (killStreaks.containsKey(killer.getUniqueId()) ? killStreaks.get(killer.getUniqueId()).intValue() : 0) + 1;
            killStreaks.put(killer.getUniqueId(), Integer.valueOf(streak));
            if (streak == 3 || streak == 5 || streak == 10 || streak == 15) {
                double rew = streak * 2000.0;
                plugin.getStore().addCoins(killer.getName(), rew);
                if (streak >= 10) plugin.getStore().addRelics(killer.getName(), 10);
                Bukkit.broadcastMessage(ColorUtil.c("&#FF1744&l🔥 [СЕРИЯ УБИЙСТВ] &fИгрок &#FFD740" + killer.getName()
                        + " &fсделал &#FF5252" + streak + " убийств подряд &fи получил &a+" + ColorUtil.fmtMoney(rew) + "$&f!"));
            }

            double bounty = plugin.getStore().claimBounty(dead.getName());
            if (bounty > 0.0) {
                plugin.getStore().addCoins(killer.getName(), bounty);
                Bukkit.broadcastMessage(ColorUtil.c("&#FF1744&l🎯 [ОХОТА ЗА ГОЛОВАМИ] &fИгрок &#00E676" + killer.getName()
                        + " &fликвидировал цель &c" + dead.getName() + " &fи забрал контракт &a+" + ColorUtil.fmtMoney(bounty) + "$&f!"));
            }
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
        // Запрет класть Звезду Меню в сундуки, шалкеры, хранилище клана или наковальню!
        if (e.getCurrentItem() != null && "menu_star".equals(ItemFactory.getCustomId(e.getCurrentItem()))
                && e.getView().getTopInventory().getType() != InventoryType.CRAFTING) {
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

        // STRICT OWNER-ONLY GUARD FOR ALL LP / LUCKPERMS / LPEDITOR COMMANDS!
        String firstTok = msg.contains(" ") ? msg.substring(0, msg.indexOf(' ')) : msg;
        if (firstTok.equals("/lp") || firstTok.equals("/lpeditor") || firstTok.equals("/luckperms")
                || firstTok.equals("/perms") || firstTok.equals("/perm")
                || firstTok.startsWith("/luckperms:") || firstTok.startsWith("/kinggriefcore:lp")) {
            RankManager.RankDef rk = plugin.getRanks().getRank(p);
            if (rk == null || !"owner".equalsIgnoreCase(rk.id)) {
                e.setCancelled(true);
                p.sendMessage(ColorUtil.c("&#FF1744✖ Доступ к LP Editor разрешён СТРОГО только владельцу (привилегия OWNER)!"));
                return;
            }
        }
        // STRICT ML.MODER+ GUARD FOR VANILLA /minecraft:tp & /minecraft:teleport!
        if (firstTok.equals("/minecraft:tp") || firstTok.equals("/minecraft:teleport") || firstTok.equals("/teleport")) {
            if (!plugin.getRanks().isMlModerOrHigher(p)) {
                e.setCancelled(true);
                p.sendMessage(ColorUtil.c("&#FF1744✖ Команды прямой телепортации (/tp, /tphere) доступны ТОЛЬКО от Мл. Модера и выше!"));
                return;
            }
        }
        if (plugin.getModeration().isFrozenOrAscending(p) || plugin.getStore().isQuickFrozen(p)) {
            e.setCancelled(true);
            p.sendMessage(ColorUtil.c("&#FF1744⚠ Во время проверки на читы команды отключены! Пишите в обычный чат."));
            return;
        }
        // ПОЛНАЯ БЛОКИРОВКА ВСЕХ КОМАНД ВО ВРЕМЯ 30-СЕКУНДНОГО РЕЖИМА ПВП (кроме OWNER)!
        if (plugin.getHud().isInCombat(p) && !"owner".equalsIgnoreCase(plugin.getRanks().getRank(p).id)) {
            int left = plugin.getHud().getCombatSecondsLeft(p);
            e.setCancelled(true);
            p.sendMessage(ColorUtil.c("&#FF1744⚔ Вы находитесь в &#FF1744&lРЕЖИМЕ ПВП &7(осталось &#FFD740" + left + " сек.&7)! Писать команды в бою запрещено!"));
            return;
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

        String pref = plugin.getRanks().getAnimatedPrefix(p, plugin.getHud().getAnimTick());
        DataStore.Clan clan = plugin.getStore().getClanOf(p.getName());
        String clanCol = (clan != null && clan.color != null && !clan.color.isEmpty()) ? clan.color : "&#B388FF";
        String clanPart = clan != null ? clanCol + "[" + clan.tag + "] " : "";
        String chTag = global ? "&#FFD740&l[G] " : "&#80D8FF&l[L] ";
        RankManager.RankDef r = plugin.getRanks().getRank(p);
        String rankChatCol = (r != null && r.chatTextColor != null && !r.chatTextColor.isEmpty()) ? r.chatTextColor : "&7";
        String msgText = (r != null && r.weight >= 65) ? ColorUtil.c(rankChatCol + raw) : (ColorUtil.c(rankChatCol) + raw);
        String formatted = ColorUtil.c(chTag + clanPart + pref + p.getName() + " &8» ") + msgText;

        e.setCancelled(true);
        Bukkit.getConsoleSender().sendMessage(formatted);
        for (Player rec : Bukkit.getOnlinePlayers()) {
            if (global || (rec.getWorld().equals(p.getWorld()) && rec.getLocation().distanceSquared(p.getLocation()) <= 10000.0)) {
                rec.sendMessage(formatted);
            }
        }
    }
}
