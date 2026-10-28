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
import org.bukkit.event.entity.EntityShootBowEvent;
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
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
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

                    // v6.4.0: 3D ШАРЫ в ЛЕВОЙ РУКЕ — баффы наконец-то работают (раньше шары были просто декором)!
                    applyOrbBuffs(p, offId);

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

    /**
     * v6.4.0: 3D ШАРЫ (shar_*) в ЛЕВОЙ РУКЕ наконец-то дают свои баффы из описания.
     * Раньше шары продавались в /shop, /rshop, кейсах и за снежинки, но НЕ ДЕЛАЛИ НИЧЕГО.
     * Баффы выдаются на 2.5 сек и обновляются каждую секунду -> снял шар = баффы пропали.
     */
    private void applyOrbBuffs(Player p, String offId) {
        if (offId == null || !offId.startsWith("shar_")) return;
        if (p.getGameMode() == GameMode.SPECTATOR || p.isDead()) return;
        if ("shar_titana".equals(offId)) {
            ColorUtil.addPot(p, "health_boost", "HEALTH_BOOST", 50, 1);
            ColorUtil.addPot(p, "strength", "INCREASE_DAMAGE", 50, 0);
            ColorUtil.addPot(p, "resistance", "DAMAGE_RESISTANCE", 50, 0);
        } else if ("shar_andromedy".equals(offId)) {
            ColorUtil.addPot(p, "strength", "INCREASE_DAMAGE", 50, 1);
            ColorUtil.addPot(p, "speed", "SPEED", 50, 1);
            ColorUtil.addPot(p, "jump_boost", "JUMP", 50, 1);
        } else if ("shar_chimery".equals(offId)) {
            ColorUtil.addPot(p, "strength", "INCREASE_DAMAGE", 50, 1);
            ColorUtil.addPot(p, "fire_resistance", "FIRE_RESISTANCE", 60, 0);
            ColorUtil.addPot(p, "regeneration", "REGENERATION", 50, 0);
        } else if ("shar_apollona".equals(offId)) {
            ColorUtil.addPot(p, "haste", "FAST_DIGGING", 50, 1);
            ColorUtil.addPot(p, "speed", "SPEED", 50, 1);
            ColorUtil.addPot(p, "night_vision", "NIGHT_VISION", 260, 0);
        } else if ("shar_aida".equals(offId)) {
            ColorUtil.addPot(p, "strength", "INCREASE_DAMAGE", 50, 1);
            ColorUtil.addPot(p, "fire_resistance", "FIRE_RESISTANCE", 60, 0);
        } else if ("shar_osirisa".equals(offId)) {
            ColorUtil.addPot(p, "health_boost", "HEALTH_BOOST", 50, 0);
            ColorUtil.addPot(p, "regeneration", "REGENERATION", 50, 1);
            ColorUtil.addPot(p, "resistance", "DAMAGE_RESISTANCE", 50, 0);
        } else if ("shar_aresa".equals(offId)) {
            ColorUtil.addPot(p, "strength", "INCREASE_DAMAGE", 50, 2);
            ColorUtil.addPot(p, "speed", "SPEED", 50, 0);
        } else if ("shar_santy".equals(offId)) {
            ColorUtil.addPot(p, "health_boost", "HEALTH_BOOST", 50, 2);
            ColorUtil.addPot(p, "strength", "INCREASE_DAMAGE", 50, 1);
            ColorUtil.addPot(p, "speed", "SPEED", 50, 1);
            ColorUtil.addPot(p, "regeneration", "REGENERATION", 50, 0);
        } else if ("shar_kronosa".equals(offId)) {
            ColorUtil.addPot(p, "haste", "FAST_DIGGING", 50, 2);
            ColorUtil.addPot(p, "speed", "SPEED", 50, 0);
            ColorUtil.addPot(p, "jump_boost", "JUMP", 50, 0);
        } else if ("shar_geliosa".equals(offId)) {
            ColorUtil.addPot(p, "fire_resistance", "FIRE_RESISTANCE", 60, 0);
            ColorUtil.addPot(p, "night_vision", "NIGHT_VISION", 260, 0);
            ColorUtil.addPot(p, "haste", "FAST_DIGGING", 50, 0);
            ColorUtil.addPot(p, "regeneration", "REGENERATION", 50, 0);
        } else if ("shar_morozko".equals(offId)) {
            ColorUtil.addPot(p, "resistance", "DAMAGE_RESISTANCE", 50, 1);
            ColorUtil.addPot(p, "regeneration", "REGENERATION", 50, 0);
            ColorUtil.addPot(p, "strength", "INCREASE_DAMAGE", 50, 0);
        } else if ("shar_kvadrat".equals(offId)) {
            ColorUtil.addPot(p, "strength", "INCREASE_DAMAGE", 50, 1);
            ColorUtil.addPot(p, "haste", "FAST_DIGGING", 50, 1);
            ColorUtil.addPot(p, "resistance", "DAMAGE_RESISTANCE", 50, 1);
            ColorUtil.addPot(p, "regeneration", "REGENERATION", 50, 0);
            ColorUtil.addPot(p, "jump_boost", "JUMP", 50, 1);
        } else {
            return;
        }
        // v6.4.0: Морозная аура Шара Морозко — враги рядом замерзают и тормозят.
        if ("shar_morozko".equals(offId)) {
            for (Entity en : p.getNearbyEntities(6, 4, 6)) {
                if (en instanceof Player && !en.equals(p) && isEnemyTarget(p, (Player) en)) {
                    en.setFreezeTicks(Math.max(en.getFreezeTicks(), 90));
                    ColorUtil.addPot((Player) en, "slowness", "SLOW", 40, 0);
                }
            }
        }
    }

    /**
     * v6.4.0: НОВЫЕ ЗАЧАРОВАНИЯ-СВИТКИ. Свиток в ПРАВОЙ руке, предмет в ЛЕВОЙ -> ПКМ.
     * Возвращает true, если свиток был применён (событие уже отменено).
     */
    private boolean handleEnchantScroll(Player p, ItemStack scroll, String enchId) {
        if (ItemFactory.scrollIndex(enchId) < 0) return false;
        ItemStack tool = p.getInventory().getItemInOffHand();
        if (tool == null || tool.getType() == Material.AIR) {
            ColorUtil.sound(p, "entity.villager.no", 1.0f, 0.8f);
            p.sendMessage(ColorUtil.c("&#FF1744✖ Положите оружие/инструмент в &fЛЕВУЮ руку&#FF1744, свиток держите в правой и нажмите ПКМ!"));
            return true;
        }
        Material tm = tool.getType();
        String tn = tm.name();
        boolean weapon = tn.endsWith("_SWORD") || tn.endsWith("_AXE") || tm == Material.TRIDENT;
        boolean pick = tn.endsWith("_PICKAXE");
        if ("smelt".equals(enchId) && !pick) {
            ColorUtil.sound(p, "entity.villager.no", 1.0f, 0.8f);
            p.sendMessage(ColorUtil.c("&#FF1744✖ Зачарование «Авто-Плавка» применяется только на &fкирку&#FF1744!"));
            return true;
        }
        if (!"smelt".equals(enchId) && !weapon) {
            ColorUtil.sound(p, "entity.villager.no", 1.0f, 0.8f);
            p.sendMessage(ColorUtil.c("&#FF1744✖ Это зачарование применяется только на &fоружие&#FF1744 (меч, топор, булава, трезубец)!"));
            return true;
        }
        int lvl = ItemFactory.applyScrollToTool(tool, enchId);
        if (lvl < 0) {
            ColorUtil.sound(p, "entity.villager.no", 1.0f, 0.8f);
            p.sendMessage(ColorUtil.c("&#FF1744✖ На этом предмете уже максимальный уровень &#FFD740III&#FF1744!"));
            return true;
        }
        consumeOne(p, scroll);
        p.getInventory().setItemInOffHand(tool);
        ColorUtil.sound(p, "block.enchantment_table.use", 1.0f, 1.25f);
        p.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, p.getLocation().add(0, 1.1, 0), 45, 0.5, 0.7, 0.5, 0.4);
        p.getWorld().spawnParticle(Particle.CRIT_MAGIC, p.getLocation().add(0, 1.1, 0), 25, 0.4, 0.6, 0.4, 0.05);
        p.sendMessage(ColorUtil.c("&#00E676✔ Зачарование &#7DD3FC" + ItemFactory.scrollMark(enchId)
                + " " + ItemFactory.romanLevel(lvl) + " &#00E676успешно нанесено на предмет в левой руке!"));
        ColorUtil.actionBar(p, "&#7DD3FC✨ " + ItemFactory.scrollMark(enchId) + " " + ItemFactory.romanLevel(lvl));
        return true;
    }

    /** v6.4.0: боевые свитки-зачарования срабатывают при ударе (Лёд / Вампиризм / Взрывной Удар). */
    private void applyScrollEnchantsOnHit(Player attacker, LivingEntity victim) {
        ItemStack w = attacker.getInventory().getItemInMainHand();
        if (w == null || w.getType() == Material.AIR) return;
        int ice = ItemFactory.getToolEnchant(w, "ice");
        int vamp = ItemFactory.getToolEnchant(w, "vampire");
        int blast = ItemFactory.getToolEnchant(w, "blast");
        if (ice <= 0 && vamp <= 0 && blast <= 0) return;
        Location vl = victim.getLocation();
        if (ice > 0) {
            victim.setFreezeTicks(Math.max(victim.getFreezeTicks(), 40 + 40 * ice));
            ColorUtil.addPot(victim, "slowness", "SLOW", 40 + 20 * ice, Math.min(3, ice));
            victim.getWorld().spawnParticle(Particle.SNOWFLAKE, vl.add(0, 1.0, 0), 14 * ice, 0.4, 0.6, 0.4, 0.02);
            vl.subtract(0, 1.0, 0);
        }
        if (vamp > 0) {
            double heal = Math.min(vamp * 1.0, victim instanceof Player ? 4.0 : 6.0);
            attacker.setHealth(Math.min(attacker.getMaxHealth(), attacker.getHealth() + heal));
            attacker.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, vl.clone().add(0, 1.0, 0), 6, 0.3, 0.4, 0.3, 0.02);
            ColorUtil.actionBar(attacker, "&#FF1744🩸 Вампиризм " + ItemFactory.romanLevel(vamp) + ": &a+" + heal + "❤");
        }
        if (blast > 0) {
            double dmg = 2.0 * blast;
            int hit = 0;
            for (Entity en : victim.getWorld().getNearbyEntities(vl, 3.0, 2.5, 3.0)) {
                if (en.equals(attacker) || en.equals(victim)) continue;
                if (!(en instanceof LivingEntity)) continue;
                if (en instanceof Player) {
                    Player op = (Player) en;
                    if (!isEnemyTarget(attacker, op)) continue;
                    tagPvp30(attacker, op);
                }
                ((LivingEntity) en).damage(dmg, attacker);
                hit++;
            }
            victim.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, vl.clone().add(0, 0.9, 0), 1, 0, 0, 0, 0);
            victim.getWorld().spawnParticle(Particle.FLAME, vl.clone().add(0, 0.9, 0), 18 * blast, 1.1, 0.7, 1.1, 0.03);
            ColorUtil.sound(attacker, "entity.generic.explode", 0.55f, 1.5f);
            if (hit > 0) ColorUtil.actionBar(attacker, "&#FF9100💥 Взрывной Удар " + ItemFactory.romanLevel(blast) + ": задел &f" + hit + " &7врагов");
        }
    }

    /**
     * v6.4.0: «Авто-Плавка» из свитка работает на ЛЮБОЙ кирке (даже ванильной) и ВЕЗДЕ:
     * в мире выживания, в Незере, в Энде и на всех варпах. Возвращает true, если дроп заменён.
     */
    private boolean applySmeltEnchant(BlockBreakEvent e, Player p, Block b, ItemStack tool) {
        int lvl = ItemFactory.getToolEnchant(tool, "smelt");
        if (lvl <= 0) return false;
        Material m = b.getType();
        String mn = m.name();
        if (!mn.endsWith("_ORE") && m != Material.ANCIENT_DEBRIS && m != Material.GILDED_BLACKSTONE) return false;
        if (plugin.getSpawnEngine().isInSpawnWorld(b.getLocation())) return false; // в авто-шахте своя экономика
        ItemStack drop = smeltedDropForBlock(m, 1 + lvl, 1 + lvl);
        if (drop == null) return false;
        e.setDropItems(false);
        Map<Material, Integer> tally = new HashMap<Material, Integer>();
        addLootBundled(tally, drop);
        deliverBundledLoot(p, b.getLocation().add(0.5, 0.5, 0.5), tally, true);
        b.getWorld().spawnParticle(Particle.FLAME, b.getLocation().add(0.5, 0.5, 0.5), 6 * lvl, 0.25, 0.25, 0.25, 0.02);
        ColorUtil.actionBar(p, "&#FFD740🔥 Авто-Плавка " + ItemFactory.romanLevel(lvl) + ": руда сразу в слитках ×" + (1 + lvl));
        plugin.getStore().addMined(p.getName());
        return true;
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
            if (plugin.getRanks().isOwnerLevel(p) && p.getGameMode() == GameMode.CREATIVE) continue;
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
    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLoginCheck(PlayerLoginEvent e) {
        Player p = e.getPlayer();
        String ip = "";
        try {
            if (e.getAddress() != null) ip = e.getAddress().getHostAddress();
        } catch (Throwable ignored) {}
        if (!ip.isEmpty()) {
            plugin.getStore().recordPlayerIp(p.getName(), ip);
        }
        // v6.2.1: БАН ПО НИКУ действует на ВСЕХ (даже OWNER-ранг) — иначе бан невозможно выдать!
        boolean isOwnerRank = plugin.getRanks().isOwnerLevelByName(p.getName());
        String ban = plugin.getStore().getActiveBanReason(p.getName());
        if (ban != null) {
            long remSec = plugin.getStore().getBanRemainingSec(p.getName());
            String timeLabel = remSec < 0 ? "Навсегда" : ColorUtil.fmtTime(remSec);
            String staff = plugin.getStore().getBanStaff(p.getName());
            e.disallow(PlayerLoginEvent.Result.KICK_BANNED, plugin.getModeration().buildBanKickScreen(staff, timeLabel, ban));
            return;
        }
        if (!ip.isEmpty() && !isOwnerRank) {
            String ipBan = plugin.getStore().getActiveIpBanReason(ip);
            if (ipBan != null) {
                long remSec = plugin.getStore().getIpBanRemainingSec(ip);
                String timeLabel = remSec < 0 ? "Навсегда" : ColorUtil.fmtTime(remSec);
                String staff = plugin.getStore().getIpBanStaff(ip);
                String origTarget = plugin.getStore().getIpBanTarget(ip);
                e.disallow(PlayerLoginEvent.Result.KICK_BANNED,
                        plugin.getModeration().buildIpBanKickScreen(staff, timeLabel, ipBan, origTarget));
            }
        }
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent e) {
        final Player p = e.getPlayer();
        boolean isOwnerJoin = plugin.getRanks().isOwnerLevel(p);
        // v6.2.1: бан по нику кикает ВСЕХ (включая OWNER-ранг)!
        String ban = plugin.getStore().getActiveBanReason(p.getName());
        if (ban != null) {
            long remSec = plugin.getStore().getBanRemainingSec(p.getName());
            String timeLabel = remSec < 0 ? "Навсегда" : ColorUtil.fmtTime(remSec);
            String staff = plugin.getStore().getBanStaff(p.getName());
            p.kickPlayer(plugin.getModeration().buildBanKickScreen(staff, timeLabel, ban));
            return;
        }
        if (!isOwnerJoin) {
            String joinIp = plugin.getStore().getPlayerIp(p);
            String ipBan = plugin.getStore().getActiveIpBanReason(joinIp);
            if (ipBan != null) {
                long remSec = plugin.getStore().getIpBanRemainingSec(joinIp);
                String timeLabel = remSec < 0 ? "Навсегда" : ColorUtil.fmtTime(remSec);
                String staff = plugin.getStore().getIpBanStaff(joinIp);
                String origTarget = plugin.getStore().getIpBanTarget(joinIp);
                p.kickPlayer(plugin.getModeration().buildIpBanKickScreen(staff, timeLabel, ipBan, origTarget));
                return;
            }
        }
        e.setJoinMessage(null);
        plugin.getStore().ensureProfileInitialized(p);
        plugin.getStore().refreshPermissions(p, plugin.getRanks());
        plugin.getBossEvents().syncBarsForJoin(p);

        // v6.3.0: страховка от «замороженного» игрока — если скорость ходьбы/полёта осталась нулевой
        // после сбоя/перезагрузки во время бан-анимации, возвращаем нормальную сразу на входе.
        try {
            if (p.getWalkSpeed() < 0.05f) p.setWalkSpeed(0.2f);
            if (p.getFlySpeed() < 0.02f) p.setFlySpeed(0.1f);
            if (!plugin.getModeration().isFrozenOrAscending(p)) {
                p.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
                p.removePotionEffect(org.bukkit.potion.PotionEffectType.DAMAGE_RESISTANCE);
            }
        } catch (Throwable ignored) {}

        // Give menu star in slot 8 if missing
        ItemStack s8 = p.getInventory().getItem(8);
        if (s8 == null || s8.getType() == Material.AIR) {
            p.getInventory().setItem(8, ItemFactory.menuStar());
        }

        // First join or if standing in spawn_world -> ensure clean arrival on spawn_world
        if (!p.hasPlayedBefore() || plugin.getSpawnEngine().isInSpawnWorld(p.getLocation())) {
            p.teleport(plugin.getSpawnEngine().getSpawnArrival());
        }

        // Auto-push Mega Resourcepack v5.9 with exact SHA-1 & cache-busting query (115 3D items + 12 Swords + 12 Pickaxes + Молот OWNER 10×10 + Bow Animation!)
        final String rawRpUrl = plugin.getConfig().getString("resourcepack-url", "https://kinggrief.vercel.app/kinggrief-mega-rp.zip?v=63");
        final String rpSha1 = plugin.getConfig().getString("resourcepack-sha1", "3637015af079e6fea621c10fe6dfa51e35cc0113");
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) return;
                plugin.getHud().updatePlayerHud(p);
                // v6.3.0: если игрок зашёл с действующим мутом — кляп 🤐 сразу висит над ним!
                plugin.getModeration().syncMuteGagFor(p);
                try {
                    if (plugin.getConfig().getBoolean("push-resourcepack-on-join", true) && rawRpUrl != null && !rawRpUrl.isEmpty()) {
                        String effectiveUrl = rawRpUrl;
                        String effectiveSha1 = rpSha1;
                        if (effectiveUrl.contains("kinggrief.vercel.app/kinggrief-mega-rp.zip")) {
                            effectiveUrl = "https://kinggrief.vercel.app/kinggrief-mega-rp.zip?v=63";
                            effectiveSha1 = "3637015af079e6fea621c10fe6dfa51e35cc0113";
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
                p.sendTitle(ColorUtil.c("&#80D8FF&l❄ KINGGRIEF v6.4.0 ❄"),
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
        DataStore.ActiveWagerDuel quitDuel = plugin.getStore().finishWagerDuel(p.getName());
        if (quitDuel != null) {
            String winnerName = quitDuel.playerA.equalsIgnoreCase(p.getName()) ? quitDuel.playerB : quitDuel.playerA;
            double pot = quitDuel.betEach * 2.0;
            if (pot > 0.0) {
                plugin.getStore().addCoins(winnerName, pot);
                plugin.getStore().save();
            }
            Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l⚔ [ДУЭЛЬ] &fИгрок &c" + p.getName()
                    + " &fливнул с дуэли! Победа присуждена &#00E676" + winnerName
                    + (pot > 0.0 ? " &8(&a+" + ColorUtil.fmtMoney(pot) + "$&8)" : "") + "&f!"));
        }
        if (!wasFrozen && plugin.getHud().isInCombat(p) && !p.isDead()
                && !plugin.getRanks().isOwnerLevel(p)) {
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
        org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause = e.getCause();
        if (cause == org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || cause == org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            if (plugin.getSpawnEngine().isInsideAnyKabinka(e.getFrom())
                    || plugin.getSpawnEngine().isInsideAnyKabinka(e.getTo())) {
                e.setCancelled(true);
                Player p = e.getPlayer();
                ColorUtil.sound(p, "entity.villager.no", 0.9f, 0.9f);
                p.sendMessage(ColorUtil.c("&#FF1744✖ В ПвП-кабинках Перки и Хорусы строго запрещены!"));
                return;
            }
        }
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

        // v6.4.0: зачарование «Авто-Плавка» (свиток) работает на ЛЮБОЙ кирке и везде, кроме авто-шахты.
        if (applySmeltEnchant(e, p, b, tool)) return;

        // Job payouts
        plugin.getStore().addMined(p.getName());
        String job = plugin.getStore().getJob(p.getName());
        if ("miner".equals(job) && (b.getType().name().contains("ORE") || b.getType() == Material.STONE)) {
            plugin.getStore().addCoins(p.getName(), 12.0);
        } else if ("lumber".equals(job) && b.getType().name().contains("_LOG")) {
            plugin.getStore().addCoins(p.getName(), 14.0);
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

    /** v6.1: РЕДКОСТИ АВТО-ШАХТЫ — метка редкости для экшен-бара и эффектов! */
    private String mineRarityTag(Material m) {
        if (m == Material.ANCIENT_DEBRIS) return "&#E040FB&l🌌 АРТЕФАКТНАЯ РУДА (Древние Обломки)!";
        if (m == Material.EMERALD_ORE) return "&#22C55E&l💚 МИФИЧЕСКАЯ РУДА (Изумруд)!";
        if (m == Material.DIAMOND_ORE) return "&#80D8FF&l💎 ЛЕГЕНДАРНАЯ РУДА (Алмаз)!";
        if (m == Material.GOLD_ORE || m == Material.LAPIS_ORE || m == Material.REDSTONE_ORE) return "&#A855F7🟣 ЭПИЧЕСКАЯ РУДА!";
        if (m == Material.IRON_ORE) return "&#FB923C⛓ РЕДКАЯ РУДА (Железо)!";
        if (m == Material.COAL_ORE) return "&#9E9E9E⚫ Необычная руда (Уголь)";
        return "&#BDBDBD🪨 Обычная порода";
    }

    private int mineRarityLevel(Material m) {
        if (m == Material.ANCIENT_DEBRIS) return 3;
        if (m == Material.EMERALD_ORE || m == Material.DIAMOND_ORE) return 2;
        if (m == Material.GOLD_ORE || m == Material.LAPIS_ORE || m == Material.REDSTONE_ORE || m == Material.IRON_ORE) return 1;
        return 0;
    }

    // ================= v6.4.0: АВТО-ШАХТА /warp mine — РАБОТАЮТ ВСЕ 13 ИНСТРУМЕНТОВ + БУРЕНИЕ! =================
    /** v6.4.0: deepslate-руды приводим к обычной руде — раньше они давали булыжник и 5 монет (баг!). */
    private Material autoMineBaseOre(Material m) {
        if (m == null) return Material.STONE;
        String n = m.name();
        if (n.startsWith("DEEPSLATE_")) {
            Material base = Material.matchMaterial(n.substring("DEEPSLATE_".length()));
            if (base != null) return base;
        }
        if (m == Material.NETHER_GOLD_ORE) return Material.GOLD_ORE;
        return m;
    }

    /** v6.4.0: бонус награды Авто-Шахты теперь есть у ВСЕХ инструментов (раньше только у 5 из 13). */
    private int autoMineBonus(String tid) {
        if (tid == null) return 1;
        if ("hammer_owner".equals(tid)) return 3;
        if ("pick_krush".equals(tid) || "pick_molten".equals(tid) || "pick_midas".equals(tid)
                || "pick_tunnel".equals(tid) || "pick_meteor".equals(tid) || "pick_djamshut".equals(tid)
                || "pick_frost".equals(tid) || "pick_magnet".equals(tid) || "pick_vein".equals(tid)
                || "pick_obsidian".equals(tid) || "pick_spawner".equals(tid)) return 2;
        return 1;
    }

    private ItemStack autoMineDrop(Material base, int mult) {
        if (base == Material.COAL_ORE) return new ItemStack(Material.COAL, 2 * mult);
        if (base == Material.IRON_ORE) return new ItemStack(Material.IRON_INGOT, mult);
        if (base == Material.GOLD_ORE) return new ItemStack(Material.GOLD_INGOT, mult);
        if (base == Material.LAPIS_ORE) return new ItemStack(Material.LAPIS_LAZULI, 4 * mult);
        if (base == Material.REDSTONE_ORE) return new ItemStack(Material.REDSTONE, 4 * mult);
        if (base == Material.DIAMOND_ORE) return new ItemStack(Material.DIAMOND, mult);
        if (base == Material.EMERALD_ORE) return new ItemStack(Material.EMERALD, mult);
        if (base == Material.COPPER_ORE) return new ItemStack(Material.COPPER_INGOT, 2 * mult);
        if (base == Material.ANCIENT_DEBRIS) return new ItemStack(Material.NETHERITE_SCRAP, mult);
        return new ItemStack(Material.COBBLESTONE, 1);
    }

    private double autoMineCoins(Material base, int mult) {
        if (base == Material.COAL_ORE) return 20.0 * mult;
        if (base == Material.IRON_ORE) return 45.0 * mult;
        if (base == Material.GOLD_ORE) return 70.0 * mult;
        if (base == Material.LAPIS_ORE) return 50.0 * mult;
        if (base == Material.REDSTONE_ORE) return 50.0 * mult;
        if (base == Material.COPPER_ORE) return 35.0 * mult;
        if (base == Material.DIAMOND_ORE) return 150.0 * mult;
        if (base == Material.EMERALD_ORE) return 200.0 * mult;
        if (base == Material.ANCIENT_DEBRIS) return 700.0 * mult;
        return 5.0 * mult;
    }

    private void handleAutoMineBreak(BlockBreakEvent e, Player p, Block b) {
        Material m = b.getType();
        if (m == Material.COBBLESTONE || m == Material.BEDROCK) {
            e.setCancelled(true);
            return;
        }
        e.setDropItems(false);
        e.setExpToDrop(0);
        ItemStack tool = p.getInventory().getItemInMainHand();
        String tid = ItemFactory.getCustomId(tool);
        int bonusMult = autoMineBonus(tid);
        Material base = autoMineBaseOre(m);

        Map<Material, Integer> tally = new HashMap<Material, Integer>();
        double[] acc = new double[2]; // acc[0] = монеты за добуренные блоки, acc[1] = опыт

        // 1) ЦЕНТРАЛЬНЫЙ БЛОК — полная награда + личный эффект кирки
        int dropMult = "pick_molten".equals(tid) ? bonusMult * 2 : bonusMult; // Вулканическая Гефеста: двойная плавка
        addLootBundled(tally, autoMineDrop(base, dropMult));
        double coins = autoMineCoins(base, bonusMult);
        if ("pick_midas".equals(tid)) {
            int reward = 15 + rng.nextInt(121);
            if (rng.nextInt(100) < 4) reward += 800;
            coins += reward;
            if (rng.nextInt(100) < 16) addLootBundled(tally, new ItemStack(Material.GOLD_INGOT, 1));
        }
        if ("pick_magnet".equals(tid) || "pick_krush".equals(tid) || "hammer_owner".equals(tid)) {
            // телекинез: весь лут бурения сразу в инвентарь (deliverBundledLoot ниже и так это делает)
        }

        // 2) v6.4.0: БУРЕНИЕ В АВТО-ШАХТЕ (3×3, туннель 5×5, куб 3×3×3, молот OWNER, жила до 32)
        int extra = 0;
        if (tid != null && !p.isSneaking()) {
            if ("pick_vein".equals(tid)) extra = drillAutoMineVein(p, b, bonusMult, tally, acc);
            else extra = drillAutoMineArea(p, b, tid, bonusMult, tally, acc);
        }
        coins += acc[0];

        Location dropSpot = b.getLocation().add(0.5, 0.5, 0.5);
        deliverBundledLoot(p, dropSpot, tally, true);
        int exp = Math.min(500, 4 * bonusMult + (int) acc[1]);
        if (exp > 0) p.giveExp(exp);
        plugin.getStore().addCoins(p.getName(), coins);
        plugin.getStore().addMined(p.getName());

        // 3) v6.1/v6.4.0: РЕДКОСТИ ШАХТЫ — эффекты, цвет и объявление артефакта
        int rarityLv = mineRarityLevel(base);
        String rarityTag = mineRarityTag(base);
        if (rarityLv >= 2) {
            b.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, dropSpot, 22, 0.4, 0.4, 0.4, 0.08);
            b.getWorld().spawnParticle(Particle.CRIT, dropSpot, 14, 0.35, 0.35, 0.35, 0.05);
            ColorUtil.sound(p, "entity.player.levelup", 0.85f, rarityLv >= 3 ? 0.8f : 1.3f);
            if (rarityLv >= 3) {
                b.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, dropSpot, 30, 0.5, 0.5, 0.5, 0.1);
                for (Player near : b.getWorld().getPlayers()) {
                    try {
                        if (near.getLocation().distanceSquared(dropSpot) <= 64.0 * 64.0) {
                            near.sendMessage(ColorUtil.c("&#E040FB&l🌌 [АВТО-ШАХТА] &fИгрок &#FFD740" + p.getName()
                                    + " &#E040FBнашёл ДРЕВНИЕ ОБЛОМКИ! (&a+" + (int) coins + "$&#E040FB)"));
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } else if (rarityLv == 1) {
            b.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, dropSpot, 8, 0.3, 0.3, 0.3, 0.05);
            ColorUtil.sound(p, "entity.experience_orb.pickup", 0.8f, 1.15f);
        }
        if (rng.nextInt(100) < 22) {
            plugin.getStore().addSnowflakes(p.getName(), 1);
            ColorUtil.actionBar(p, rarityTag + " &8│ &a+" + (int) coins + "$ &8| &#80D8FF+1 Снежинка Джамшута ❄!"
                    + (extra > 0 ? " &8| &#00E5FF⛏ Бурение: +" + extra + " бл." : ""));
        } else {
            ColorUtil.actionBar(p, rarityTag + " &8│ &#FF9100⛏ &a+" + (int) coins + "$"
                    + (bonusMult > 1 ? " &e(Бонус Кирки ×" + bonusMult + "!)" : "")
                    + (extra > 0 ? " &8| &#00E5FF⛏ Бурение: +" + extra + " бл." : ""));
        }
        // v6.1: блок ЛОМАЕТСЯ ПО-НАСТОЯЩЕМУ (остаётся воздух) — шахта регенерирует каждые 5 минут!
    }

    /**
     * v6.4.0: бурение в Авто-Шахте плоскостью (3×3, туннель 5×5, молот OWNER 7×7) или кубом 3×3×3.
     * СТРОГО в пределах ямы /warp mine — стены, постройки спавна и соседние варпы не затрагиваются.
     * За дополнительные блоки даётся 50% монет (баланс экономики), лут сразу летит в инвентарь.
     */
    private int drillAutoMineArea(Player p, Block center, String tid, int mult,
                                  Map<Material, Integer> tally, double[] acc) {
        int radius;
        boolean cube;
        if ("hammer_owner".equals(tid)) { radius = 3; cube = false; }
        else if ("pick_tunnel".equals(tid)) { radius = 2; cube = false; }
        else if ("pick_meteor".equals(tid)) { radius = 1; cube = true; }
        else if ("pick_krush".equals(tid) || "pick_djamshut".equals(tid)
                || "pick_frost".equals(tid) || "pick_magnet".equals(tid)) { radius = 1; cube = false; }
        else return 0;

        World w = center.getWorld();
        boolean horizPlane = Math.abs(p.getLocation().getPitch()) > 50f;
        Vector dir = p.getLocation().getDirection();
        boolean xNormal = Math.abs(dir.getX()) > Math.abs(dir.getZ());
        int dropMult = "pick_molten".equals(tid) ? mult * 2 : mult;
        int count = 0;
        final int hardCap = 64; // защита от лаг-спайка: максимум 64 блока за один удар

        for (int a = -radius; a <= radius && count < hardCap; a++) {
            for (int bOff = -radius; bOff <= radius && count < hardCap; bOff++) {
                int cMin = cube ? -radius : 0;
                int cMax = cube ? radius : 0;
                for (int cOff = cMin; cOff <= cMax && count < hardCap; cOff++) {
                    int dx, dy, dz;
                    if (cube) { dx = a; dy = bOff; dz = cOff; }
                    else if (horizPlane) { dx = a; dy = 0; dz = bOff; }
                    else if (xNormal) { dx = 0; dy = a; dz = bOff; }
                    else { dx = a; dy = bOff; dz = 0; }
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    Block t = w.getBlockAt(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    Material tm = t.getType();
                    if (tm == Material.AIR || tm == Material.CAVE_AIR || tm == Material.COBBLESTONE
                            || tm == Material.BEDROCK || tm == Material.BARRIER) continue;
                    if (!plugin.getSpawnEngine().isInsideAutoMine(t.getLocation())) continue;
                    Material tb = autoMineBaseOre(tm);
                    addLootBundled(tally, autoMineDrop(tb, dropMult));
                    acc[0] += autoMineCoins(tb, mult) * 0.5;
                    acc[1] += 2;
                    t.setType(Material.AIR, false);
                    count++;
                }
            }
        }
        if (count > 0) {
            ColorUtil.sound(p, "entity.generic.explode", 0.35f, 1.6f);
            w.spawnParticle(Particle.LARGE_SMOKE, center.getLocation().add(0.5, 0.5, 0.5), 12, 0.8, 0.6, 0.8, 0.02);
        }
        return count;
    }

    /** v6.4.0: Жильная кирка в Авто-Шахте вынимает ВСЮ жилу руды (до 32 блоков) прямо из ямы. */
    private int drillAutoMineVein(Player p, Block center, int mult,
                                  Map<Material, Integer> tally, double[] acc) {
        Material target = center.getType();
        String tn = target.name();
        if (!tn.endsWith("_ORE") && target != Material.ANCIENT_DEBRIS) return 0;
        World w = center.getWorld();
        Queue<Block> q = new ArrayDeque<Block>();
        Set<String> seen = new HashSet<String>();
        q.add(center);
        seen.add(blockKey(center));
        int count = 0;
        while (!q.isEmpty() && count < 32) {
            Block cur = q.poll();
            if (!cur.equals(center)) {
                if (cur.getType() != target) continue;
                if (!plugin.getSpawnEngine().isInsideAutoMine(cur.getLocation())) continue;
                Material tb = autoMineBaseOre(target);
                addLootBundled(tally, autoMineDrop(tb, mult));
                acc[0] += autoMineCoins(tb, mult) * 0.5;
                acc[1] += 3;
                cur.setType(Material.AIR, false);
                count++;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block nb = cur.getRelative(dx, dy, dz);
                        String nk = blockKey(nb);
                        if (!seen.contains(nk) && nb.getType() == target) {
                            seen.add(nk);
                            q.add(nb);
                        }
                    }
                }
            }
        }
        if (count > 0) ColorUtil.sound(p, "entity.experience_orb.pickup", 0.9f, 1.35f);
        return count;
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
            int reward = 15 + rng.nextInt(121);
            if (rng.nextInt(100) < 4) reward += 800;
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
            // v6.3.3: безопасный парсинг радиуса — битый предмет не роняет обработку клика.
            int rad = ColorUtil.parseInt(cid.substring(6), -1);
            if (rad <= 0) {
                e.setCancelled(true);
                p.sendMessage(ColorUtil.c("&#FF1744✖ Этот рудный приват повреждён — возьми новый в /shop."));
                return;
            }
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
        boolean rightClick = e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK;
        if (rightClick && hand != null) {
            Material hm = hand.getType();
            if ((hm == Material.ENDER_PEARL || hm == Material.CHORUS_FRUIT)
                    && plugin.getSpawnEngine().isInsideAnyKabinka(p.getLocation())) {
                e.setCancelled(true);
                ColorUtil.sound(p, "entity.villager.no", 0.9f, 0.9f);
                ColorUtil.actionBar(p, "&#FF1744✖ В кабинках Эндер-Перки и Хорусы запрещены!");
                p.sendMessage(ColorUtil.c("&#FF1744✖ В ПвП-кабинках использование Эндер-Перок и Хорусов запрещено!"));
                return;
            }
            if (hm == Material.ENCHANTED_GOLDEN_APPLE || hm == Material.GOLDEN_APPLE) {
                boolean isOwnerP = plugin.getRanks().isOwnerLevel(p);
                if (isOwnerP) {
                    try { p.setCooldown(hm, 0); } catch (Throwable ignored) {}
                } else {
                    String cdKey = p.getUniqueId() + (hm == Material.ENCHANTED_GOLDEN_APPLE ? ":cd_charka" : ":cd_gapple");
                    Long readyAt = itemCooldowns.get(cdKey);
                    long nowMs = System.currentTimeMillis();
                    if (readyAt != null && nowMs < readyAt.longValue()) {
                        long remSec = Math.max(1L, (readyAt.longValue() - nowMs + 999L) / 1000L);
                        int remTicks = (int) Math.max(2L, (readyAt.longValue() - nowMs) / 50L);
                        e.setCancelled(true);
                        try { p.setCooldown(hm, remTicks); } catch (Throwable ignored) {}
                        String appleName = hm == Material.ENCHANTED_GOLDEN_APPLE ? "&#FF9100Чарка" : "&#FFD740Гепл";
                        ColorUtil.actionBar(p, "&#FF5252⏳ " + appleName + " &fна КТ: подождите ещё &#FFD740" + remSec + " сек.&f!");
                        return;
                    }
                }
            }
        }
        String cid = ItemFactory.getCustomId(hand);
        if (cid == null) return;
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
            boolean isOwnerUser = plugin.getRanks().isOwnerLevel(p);
            if (!isOwnerUser && !checkAndSetCd(p, "orb_recall", 25)) return;
            consumeOne(p, hand);
            final long startMs = System.currentTimeMillis();
            p.sendMessage(ColorUtil.c("&#00E5FF🌀 &lСфера Эвакуации &8» &fЗапущен купол экстренной эвакуации на спавн (&#FFD74010 сек.&f) — не получайте урон!"));
            ColorUtil.actionBar(p, "&#00E5FF🌀 Сфера Эвакуации: телепортация через &f10 сек...");
            ColorUtil.sound(p, "block.beacon.power_select", 1.0f, 1.35f);
            new BukkitRunnable() {
                int step = 0;
                @Override
                public void run() {
                    if (!p.isOnline() || p.isDead()) { cancel(); return; }
                    Long dmgAt = recallDamagedAt.get(p.getUniqueId());
                    if (dmgAt != null && dmgAt.longValue() >= startMs) {
                        cancel();
                        p.sendMessage(ColorUtil.c("&#FF1744✖ Телепортация Сферы Эвакуации сбита полученным уроном!"));
                        ColorUtil.actionBar(p, "&#FF1744✖ Сфера Эвакуации сбита ударом!");
                        ColorUtil.sound(p, "entity.item.break", 1.0f, 0.8f);
                        return;
                    }
                    step++;
                    Location center = p.getLocation().add(0, 1.0, 0);
                    p.getWorld().spawnParticle(Particle.END_ROD, center, 16, 0.55, 0.85, 0.55, 0.03);
                    p.getWorld().spawnParticle(Particle.PORTAL, center, 20, 0.45, 0.65, 0.45, 0.15);
                    if (step < 10) {
                        int leftSec = 10 - step;
                        ColorUtil.actionBar(p, "&#00E5FF🌀 Сфера Эвакуации: телепортация через &#FFD740" + leftSec + " сек...");
                        ColorUtil.sound(p, "block.note_block.chime", 0.75f, 0.9f + step * 0.08f);
                        return;
                    }
                    cancel();
                    plugin.getHud().clearCombat(p.getUniqueId());
                    p.teleport(plugin.getSpawnEngine().getSpawnArrival());
                    ColorUtil.sound(p, "entity.enderman.teleport", 1.0f, 1.25f);
                    p.sendTitle(ColorUtil.c("&#00E5FF&l🌀 ЭВАКУАЦИЯ!"), ColorUtil.c("&fВы спасены Сферой Эвакуации (10 сек)!"), 5, 40, 10);
                }
            }.runTaskTimer(plugin, 20L, 20L);
        } else if (cid != null && cid.startsWith("scroll_")) {
            // v6.4.0: НОВЫЕ ЗАЧАРОВАНИЯ — свиток в правой руке, предмет в левой, ПКМ.
            e.setCancelled(true);
            handleEnchantScroll(p, hand, cid.substring(7));
        } else if ("mega_heal".equals(cid)) {
            e.setCancelled(true); // v6.4.0: расходник не должен ставить блок/открывать сундук под курсором
            if (!checkAndSetCd(p, "mega_heal", 45)) return;
            consumeOne(p, hand);
            p.setHealth(p.getMaxHealth());
            p.setFoodLevel(20);
            p.setSaturation(20f);
            p.setFireTicks(0);
            ColorUtil.addPot(p, "absorption", "ABSORPTION", 1200, 3);
            ColorUtil.addPot(p, "regeneration", "REGENERATION", 400, 2);
            p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 1.2, 0), 45, 0.6, 0.9, 0.6, 0.05);
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1.0, 0), 30, 0.5, 0.8, 0.5, 0.1);
            ColorUtil.sound(p, "entity.player.levelup", 1.0f, 1.35f);
            p.sendTitle(ColorUtil.c("&#FF4081&l💗 МЕГА-ХИЛ!"),
                    ColorUtil.c("&f100% ХП + 8 Золотых ❤ + Регенерация III (20 сек)"), 5, 40, 10);
            p.sendMessage(ColorUtil.c("&#FF4081💗 МЕГА-ХИЛ: здоровье полностью восстановлено, +8 Золотых ❤ на 60 сек!"));
        } else if ("mega_buff".equals(cid)) {
            e.setCancelled(true); // v6.4.0: расходник не должен ставить блок/открывать сундук под курсором
            if (!checkAndSetCd(p, "mega_buff", 90)) return;
            consumeOne(p, hand);
            ColorUtil.addPot(p, "strength", "INCREASE_DAMAGE", 3600, 2);
            ColorUtil.addPot(p, "haste", "FAST_DIGGING", 3600, 2);
            ColorUtil.addPot(p, "speed", "SPEED", 3600, 1);
            ColorUtil.addPot(p, "jump_boost", "JUMP", 3600, 1);
            ColorUtil.addPot(p, "regeneration", "REGENERATION", 3600, 1);
            p.getWorld().spawnParticle(Particle.CRIT_MAGIC, p.getLocation().add(0, 1.2, 0), 60, 0.6, 1.0, 0.6, 0.08);
            ColorUtil.sound(p, "block.beacon.activate", 1.0f, 1.3f);
            p.sendTitle(ColorUtil.c("&#FFD740&l⚡ МЕГА-БАФФ!"),
                    ColorUtil.c("&fСила III + Спешка III + Скорость II + Прыгучесть II + Реген II (3 мин)"), 5, 40, 10);
            p.sendMessage(ColorUtil.c("&#FFD740⚡ МЕГА-БАФФ активирован на &f3 минуты&#FFD740! Новый мега-геймплей v6.4.0."));
        } else if ("mega_shield".equals(cid)) {
            e.setCancelled(true); // v6.4.0: расходник не должен ставить блок/открывать сундук под курсором
            if (!checkAndSetCd(p, "mega_shield", 60)) return;
            consumeOne(p, hand);
            ColorUtil.addPot(p, "resistance", "DAMAGE_RESISTANCE", 1200, 2);
            ColorUtil.addPot(p, "absorption", "ABSORPTION", 1800, 3);
            ColorUtil.addPot(p, "fire_resistance", "FIRE_RESISTANCE", 1200, 0);
            p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1.1, 0), 50, 0.7, 1.0, 0.7, 0.02);
            ColorUtil.sound(p, "block.beacon.power_select", 1.0f, 1.2f);
            p.sendTitle(ColorUtil.c("&#00E5FF&l🛡 МЕГА-ЩИТ!"),
                    ColorUtil.c("&fСопротивление III (-60% урона) + 8 Золотых ❤ + Огнестойкость"), 5, 40, 10);
            p.sendMessage(ColorUtil.c("&#00E5FF🛡 МЕГА-ЩИТ: урон снижен на 60% на &f60 секунд&#00E5FF!"));
        } else if ("plast".equals(cid) || "trapka".equals(cid) || "dezorient".equals(cid)
                || "yavnaya_pyl".equals(cid) || "bozhya_aura".equals(cid)
                || "ognenny_smerch".equals(cid) || "pautinnaya_granata".equals(cid)
                || "bozhya_kara".equals(cid) || "grav_sfera".equals(cid) || "sneg_zavesa".equals(cid)
                || "team_totem".equals(cid) || "battle_adrenaline".equals(cid) || "swap_orb".equals(cid)
                || "ice_dome".equals(cid) || "vampire_pulse".equals(cid) || "emp_shock".equals(cid)
                || "smoke_bomb".equals(cid)) {
            e.setCancelled(true);
            handleFtConsumable(p, hand, cid);
        }
    }

    private boolean checkAndSetCd(Player p, String key, int sec) {
        if (plugin.getRanks().isOwnerLevel(p)) {
            return true;
        }
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

    private boolean isTeammateTarget(Player caster, Player other) {
        if (caster == null || other == null || other.equals(caster) || !other.isOnline()) return false;
        if (other.getGameMode() == GameMode.SPECTATOR || other.isDead()) return false;
        return plugin.getStore().areTeammates(caster.getName(), other.getName());
    }

    private boolean isEnemyTarget(Player caster, Player other) {
        if (other == null || other.equals(caster) || other.getGameMode() == GameMode.SPECTATOR) return false;
        if (plugin.getSpawnEngine().isInSpawnWorld(other.getLocation())
                && !plugin.getSpawnEngine().isInsidePvpZone(other.getLocation())) return false;
        if (plugin.getStore().areTeammates(caster.getName(), other.getName())
                && !plugin.getStore().isTeammatePvpAllowed(caster.getName(), other.getName())) {
            return false;
        }
        return true;
    }

    private void cleanseAndBlessBozhyaAuraTarget(Player target) {
        String[][] bads = new String[][]{
                {"poison", "POISON"}, {"wither", "WITHER"}, {"blindness", "BLINDNESS"},
                {"darkness", "DARKNESS"}, {"weakness", "WEAKNESS"}, {"slowness", "SLOW"},
                {"mining_fatigue", "SLOW_DIGGING"}, {"nausea", "CONFUSION"},
                {"hunger", "HUNGER"}, {"levitation", "LEVITATION"}, {"glowing", "GLOWING"}
        };
        for (String[] b : bads) {
            org.bukkit.potion.PotionEffectType t = ColorUtil.potType(b[0], b[1]);
            if (t != null) target.removePotionEffect(t);
        }
        target.setFireTicks(0);
        target.setFreezeTicks(0);
        target.setHealth(Math.min(target.getMaxHealth(), target.getHealth() + 8.0));
        ColorUtil.addPot(target, "regeneration", "REGENERATION", 140, 2);
        ColorUtil.addPot(target, "resistance", "DAMAGE_RESISTANCE", 120, 1);
        ColorUtil.addPot(target, "absorption", "ABSORPTION", 300, 1);
        target.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, target.getLocation().add(0, 1.0, 0), 38, 0.65, 0.85, 0.65, 0.14);
        target.getWorld().spawnParticle(Particle.HEART, target.getLocation().add(0, 2.05, 0), 6, 0.35, 0.25, 0.35, 0.02);
        ColorUtil.sound(target, "item.totem.use", 0.75f, 1.4f);
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
            p.getWorld().spawnParticle(Particle.WITCH, p.getLocation().add(0, 1, 0), 85, 4.0, 1.5, 4.0, 0.05);
            p.getWorld().spawnParticle(Particle.SOUL, p.getLocation().add(0, 1, 0), 40, 3.5, 1.2, 3.5, 0.04);
            ColorUtil.sound(p, "entity.elder_guardian.curse", 1f, 1.1f);
            int hit = 0;
            for (Entity en : p.getNearbyEntities(10, 6, 10)) {
                if (en instanceof Player && isEnemyTarget(p, (Player) en)) {
                    Player v = (Player) en;
                    tagPvp30(p, v);
                    ColorUtil.addPot(v, "blindness", "BLINDNESS", 140, 0);
                    ColorUtil.addPot(v, "nausea", "CONFUSION", 140, 1);
                    ColorUtil.addPot(v, "slowness", "SLOW", 140, 1);
                    // v5.9: Иссушение III (amplifier 2) ровно на 5 секунд (100 тиков)!
                    ColorUtil.addPot(v, "wither", "WITHER", 100, 2);
                    v.getWorld().spawnParticle(Particle.SOUL, v.getLocation().add(0, 1.1, 0), 18, 0.4, 0.5, 0.4, 0.03);
                    v.sendMessage(ColorUtil.c("&#E040FB👁 Вы поражены Дезориентацией игрока &f" + p.getName() + " &#FF1744(+Иссушение III на 5 сек)!"));
                    hit++;
                }
            }
            p.sendMessage(ColorUtil.c("&#E040FB👁 Дезориентация активирована (+Иссушение III 5 сек)! Поражено врагов: &f" + hit));
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
            cleanseAndBlessBozhyaAuraTarget(p);

            int healedTeammates = 0;
            List<String> healedNames = new ArrayList<String>();
            for (Entity en : p.getNearbyEntities(12, 7, 12)) {
                if (!(en instanceof Player)) continue;
                Player other = (Player) en;
                if (isTeammateTarget(p, other)) {
                    cleanseAndBlessBozhyaAuraTarget(other);
                    healedTeammates++;
                    healedNames.add(other.getName());
                    ColorUtil.actionBar(other, "&#FFD740☀ Тиммейт &f" + p.getName() + " &#FFD740исцелил вас Божьей Аурой! &#00E676(+4❤ + Реген III + Сопр II)");
                    other.sendMessage(ColorUtil.c("&#FFD740☀ Ваш тиммейт &f" + p.getName()
                            + " &#FFD740активировал &lБожью Ауру&#FFD740! С вас сняты все дебаффы, выдано &a+4❤&7, &dРегенерация III&7, &bСопротивление II &7и &e+4💛&7!"));
                } else if (isEnemyTarget(p, other) && other.getLocation().distanceSquared(p.getLocation()) <= 64.0) {
                    tagPvp30(p, other);
                }
            }

            if (healedTeammates > 0) {
                p.sendMessage(ColorUtil.c("&#FFD740☀ &lБожья Аура &#FFD740исцелила вас и &#00E676" + healedTeammates
                        + " тиммейтов &#FFD740(&f" + String.join("&7, &f", healedNames) + "&#FFD740)! Активирован Священный Купол на 5 сек!"));
                ColorUtil.actionBar(p, "&#FFD740☀ Исцелено тиммейтов: &#00E676" + healedTeammates + " &8│ &#FFD740Священный Купол 5 сек!");
            } else {
                p.sendMessage(ColorUtil.c("&#FFD740☀ Божья Аура сняла все дебаффы, исцелила &a+4❤ &7и включила Священный Купол (добавить тиму: &f/tm <ник>&7)!"));
            }

            // 5-секундный пульсирующий Священный Купол Божьей Ауры для кастера и всех его тиммейтов!
            new BukkitRunnable() {
                int pulse = 0;
                @Override
                public void run() {
                    if (!p.isOnline() || p.isDead() || pulse >= 5) {
                        cancel();
                        return;
                    }
                    pulse++;
                    Location center = p.getLocation();
                    for (int a = 0; a < 360; a += 20) {
                        double rad = Math.toRadians(a);
                        Location ringPt = center.clone().add(Math.cos(rad) * 4.5, 0.25, Math.sin(rad) * 4.5);
                        center.getWorld().spawnParticle(Particle.END_ROD, ringPt, 1, 0.0, 0.05, 0.0, 0.01);
                    }
                    p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + 2.0));
                    p.setFireTicks(0);
                    p.setFreezeTicks(0);
                    for (Entity en : p.getNearbyEntities(10, 5, 10)) {
                        if (en instanceof Player && isTeammateTarget(p, (Player) en)) {
                            Player ally = (Player) en;
                            ally.setHealth(Math.min(ally.getMaxHealth(), ally.getHealth() + 2.0));
                            ally.setFireTicks(0);
                            ally.setFreezeTicks(0);
                            ally.getWorld().spawnParticle(Particle.HEART, ally.getLocation().add(0, 2.0, 0), 2, 0.25, 0.15, 0.25, 0.01);
                        }
                    }
                }
            }.runTaskTimer(plugin, 20L, 20L);
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
        } else if ("team_totem".equals(cid)) {
            if (!checkAndSetCd(p, "team_totem", 35)) return;
            consumeOne(p, hand);
            final Location totemCenter = p.getLocation().clone();
            ColorUtil.sound(p, "block.beacon.activate", 1.0f, 1.25f);
            int allies = 0;
            List<Player> teamGroup = new ArrayList<Player>();
            teamGroup.add(p);
            for (Entity en : p.getNearbyEntities(10, 6, 10)) {
                if (en instanceof Player) {
                    Player pl = (Player) en;
                    if (isTeammateTarget(p, pl)) {
                        teamGroup.add(pl);
                        allies++;
                        pl.sendMessage(ColorUtil.c("&#00E676🛡 Тиммейт &f" + p.getName() + " &#00E676поставил &lТотем Командного Спасения&#00E676! (+Сопротивление III и Огнестойкость)"));
                    } else if (isEnemyTarget(p, pl)) {
                        tagPvp30(p, pl);
                        Vector kb = pl.getLocation().toVector().subtract(totemCenter.toVector());
                        if (kb.lengthSquared() < 0.01) kb = new Vector(0.5, 0.3, 0.5);
                        pl.setVelocity(kb.normalize().multiply(1.15).setY(0.42));
                    }
                }
            }
            for (Player member : teamGroup) {
                ColorUtil.addPot(member, "resistance", "DAMAGE_RESISTANCE", 130, 2);
                ColorUtil.addPot(member, "fire_resistance", "FIRE_RESISTANCE", 200, 0);
                member.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, member.getLocation().add(0, 1, 0), 35, 0.6, 0.8, 0.6, 0.12);
            }
            p.sendMessage(ColorUtil.c("&#00E676🛡 Тотем Командного Спасения развёрнут на 6 сек! Защищено тиммейтов: &f" + allies));
            new BukkitRunnable() {
                int step = 0;
                @Override
                public void run() {
                    if (step >= 6 || totemCenter.getWorld() == null) { cancel(); return; }
                    step++;
                    for (int a = 0; a < 360; a += 18) {
                        double r = Math.toRadians(a);
                        totemCenter.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                                totemCenter.clone().add(Math.cos(r) * 6.0, 0.35, Math.sin(r) * 6.0), 1, 0, 0, 0, 0);
                    }
                    if (p.isOnline() && !p.isDead() && p.getWorld().equals(totemCenter.getWorld())
                            && p.getLocation().distanceSquared(totemCenter) <= 100.0) {
                        p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + 3.0));
                    }
                    for (Entity en : totemCenter.getWorld().getNearbyEntities(totemCenter, 10, 6, 10)) {
                        if (en instanceof Player && isTeammateTarget(p, (Player) en)) {
                            Player ally = (Player) en;
                            ally.setHealth(Math.min(ally.getMaxHealth(), ally.getHealth() + 3.0));
                        }
                    }
                }
            }.runTaskTimer(plugin, 20L, 20L);
        } else if ("battle_adrenaline".equals(cid)) {
            if (!checkAndSetCd(p, "battle_adrenaline", 25)) return;
            consumeOne(p, hand);
            List<Player> targets = new ArrayList<Player>();
            targets.add(p);
            for (Entity en : p.getNearbyEntities(10, 6, 10)) {
                if (en instanceof Player && isTeammateTarget(p, (Player) en)) {
                    targets.add((Player) en);
                }
            }
            for (Player member : targets) {
                ColorUtil.addPot(member, "speed", "SPEED", 200, 2);
                ColorUtil.addPot(member, "strength", "INCREASE_DAMAGE", 160, 1);
                ColorUtil.addPot(member, "haste", "FAST_DIGGING", 200, 1);
                ColorUtil.addPot(member, "fire_resistance", "FIRE_RESISTANCE", 200, 0);
                member.getWorld().spawnParticle(Particle.FLAME, member.getLocation().add(0, 1, 0), 30, 0.5, 0.7, 0.5, 0.06);
                ColorUtil.sound(member, "entity.ENDER_DRAGON.growl".toLowerCase(java.util.Locale.ROOT), 0.55f, 1.55f);
                if (!member.equals(p)) {
                    member.sendMessage(ColorUtil.c("&#FF6D00💉 Тиммейт &f" + p.getName() + " &#FF6D00вколол вашей тиме &lБоевой Адреналин&#FF6D00! (+Скорость III, Сила II, Спешка II)"));
                }
            }
            p.sendMessage(ColorUtil.c("&#FF6D00💉 Боевой Адреналин активирован! Заряжено бойцов тимы: &f" + targets.size()));
        } else if ("swap_orb".equals(cid)) {
            if (plugin.getSpawnEngine().isInsideAnyKabinka(p.getLocation())) {
                p.sendMessage(ColorUtil.c("&#FF1744✖ В кабинках ПвП Сфера Рокировки запрещена!"));
                return;
            }
            if (!checkAndSetCd(p, "swap_orb", 22)) return;
            consumeOne(p, hand);
            org.bukkit.entity.Snowball proj = p.launchProjectile(org.bukkit.entity.Snowball.class);
            proj.setItem(new ItemStack(Material.ENDER_EYE));
            proj.setVelocity(p.getLocation().getDirection().normalize().multiply(1.75));
            proj.getPersistentDataContainer().set(PROJ_TAG_KEY, PersistentDataType.STRING, "swap_orb");
            ColorUtil.sound(p, "entity.ender_eye.launch", 1.0f, 1.15f);
        } else if ("ice_dome".equals(cid)) {
            if (plugin.getSpawnEngine().isInsideAnyKabinka(p.getLocation())) {
                p.sendMessage(ColorUtil.c("&#FF1744✖ В кабинках нельзя ставить Ледяной Купол!"));
                return;
            }
            if (!checkAndSetCd(p, "ice_dome", 28)) return;
            consumeOne(p, hand);
            spawnTemporaryIceDome(p);
        } else if ("vampire_pulse".equals(cid)) {
            if (!checkAndSetCd(p, "vampire_pulse", 24)) return;
            consumeOne(p, hand);
            double drainedPool = 0.0;
            int hitEnemies = 0;
            for (Entity en : p.getNearbyEntities(8, 5, 8)) {
                if (en instanceof Player && isEnemyTarget(p, (Player) en)) {
                    Player v = (Player) en;
                    tagPvp30(p, v);
                    v.damage(6.0, p);
                    v.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, v.getLocation().add(0, 1.1, 0), 12, 0.35, 0.4, 0.35, 0.05);
                    drainedPool += 6.0;
                    hitEnemies++;
                }
            }
            List<Player> recipients = new ArrayList<Player>();
            recipients.add(p);
            for (Entity en : p.getNearbyEntities(10, 6, 10)) {
                if (en instanceof Player && isTeammateTarget(p, (Player) en)) {
                    recipients.add((Player) en);
                }
            }
            double healEach = Math.max(4.0, drainedPool / Math.max(1, recipients.size()));
            for (Player rec : recipients) {
                rec.setHealth(Math.min(rec.getMaxHealth(), rec.getHealth() + healEach));
                rec.getWorld().spawnParticle(Particle.HEART, rec.getLocation().add(0, 2.0, 0), 5, 0.3, 0.2, 0.3, 0.02);
                if (!rec.equals(p)) {
                    rec.sendMessage(ColorUtil.c("&#FF1744🩸 Тиммейт &f" + p.getName() + " &#FF1744передал вам вампирское исцеление &a+"
                            + String.format(java.util.Locale.US, "%.1f❤", Double.valueOf(healEach / 2.0)) + "&#FF1744!"));
                }
            }
            ColorUtil.sound(p, "entity.phantom.bite", 1.0f, 0.85f);
            p.sendMessage(ColorUtil.c("&#FF1744🩸 Импульс Вампира поразил &f" + hitEnemies
                    + " &#FF1744врагов и восстановил команде по &a+" + String.format(java.util.Locale.US, "%.1f❤", Double.valueOf(healEach / 2.0)) + "&#FF1744!"));
        } else if ("emp_shock".equals(cid)) {
            if (!checkAndSetCd(p, "emp_shock", 20)) return;
            consumeOne(p, hand);
            p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation().add(0, 1.1, 0), 120, 4.5, 2.0, 4.5, 0.12);
            ColorUtil.sound(p, "block.respawn_anchor.deplete", 1.0f, 1.35f);
            int shocked = 0;
            for (Entity en : p.getNearbyEntities(11, 8, 11)) {
                if (en instanceof Player && isEnemyTarget(p, (Player) en)) {
                    Player v = (Player) en;
                    tagPvp30(p, v);
                    if (v.isGliding()) {
                        v.setGliding(false);
                        v.sendMessage(ColorUtil.c("&#FF1744⚡ ЭМИ-Разрядник игрока &f" + p.getName() + " &#FF1744вырубил ваши Элитры!"));
                    }
                    v.setCooldown(Material.SHIELD, 100);
                    ColorUtil.addPot(v, "weakness", "WEAKNESS", 100, 0);
                    ColorUtil.addPot(v, "glowing", "GLOWING", 160, 0);
                    v.damage(4.0, p);
                    shocked++;
                }
            }
            p.sendMessage(ColorUtil.c("&#00E5FF⚡ ЭМИ-Разрядник отключил щиты и Элитры врагам (&f" + shocked + " шт.&#00E5FF)!"));
        } else if ("smoke_bomb".equals(cid)) {
            if (!checkAndSetCd(p, "smoke_bomb", 22)) return;
            consumeOne(p, hand);
            final Location smokeLoc = p.getLocation().clone();
            ColorUtil.sound(p, "entity.generic.extinguish_fire", 1.0f, 0.8f);
            List<Player> allies = new ArrayList<Player>();
            allies.add(p);
            for (Entity en : p.getNearbyEntities(9, 5, 9)) {
                if (en instanceof Player) {
                    Player other = (Player) en;
                    if (isTeammateTarget(p, other)) {
                        allies.add(other);
                    } else if (isEnemyTarget(p, other)) {
                        tagPvp30(p, other);
                        ColorUtil.addPot(other, "blindness", "BLINDNESS", 80, 0);
                        ColorUtil.addPot(other, "darkness", "DARKNESS", 80, 0);
                        ColorUtil.addPot(other, "slowness", "SLOW", 80, 1);
                    }
                }
            }
            for (Player ally : allies) {
                ColorUtil.addPot(ally, "invisibility", "INVISIBILITY", 160, 0);
                ColorUtil.addPot(ally, "speed", "SPEED", 160, 1);
                if (!ally.equals(p)) {
                    ally.sendMessage(ColorUtil.c("&#94A3B8💣 Дымовая бомба тиммейта &f" + p.getName() + " &#94A3B8скрыла вас в инвизе на 8 сек!"));
                }
            }
            p.sendMessage(ColorUtil.c("&#94A3B8💣 Дымовая Бомба активирована! Скрыто тиммейтов: &f" + allies.size()));
            new BukkitRunnable() {
                int t = 0;
                @Override
                public void run() {
                    if (t >= 12 || smokeLoc.getWorld() == null) { cancel(); return; }
                    t++;
                    smokeLoc.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, smokeLoc.clone().add(0, 1.1, 0), 45, 3.2, 1.4, 3.2, 0.01);
                }
            }.runTaskTimer(plugin, 0L, 10L);
        }
    }

    private void spawnTemporaryIceDome(Player p) {
        Location pl = p.getLocation();
        World w = pl.getWorld();
        if (w == null) return;
        int cx = pl.getBlockX(), cy = pl.getBlockY(), cz = pl.getBlockZ();
        final Map<Block, org.bukkit.block.data.BlockData> saved = new HashMap<Block, org.bukkit.block.data.BlockData>();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -1; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    double distSq = dx * dx + dy * dy + dz * dz;
                    boolean domeShell = (dy == -1 && (dx * dx + dz * dz) <= 9)
                            || (dy >= 0 && distSq >= 6.2 && distSq <= 12.6);
                    if (!domeShell) continue;
                    Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                    if (isReplaceableForTemp(b)) {
                        saved.put(b, b.getBlockData().clone());
                        activeTempBlocks.add(blockKey(b));
                        Material m = (dy >= 1 && ((Math.abs(dx) + Math.abs(dz)) % 2 == 1))
                                ? Material.LIGHT_BLUE_STAINED_GLASS
                                : Material.BLUE_ICE;
                        b.setType(m, false);
                    }
                }
            }
        }
        p.setFireTicks(0);
        for (Entity en : p.getNearbyEntities(4, 3, 4)) {
            if (en instanceof Player && isTeammateTarget(p, (Player) en)) {
                ((Player) en).setFireTicks(0);
                ColorUtil.addPot((Player) en, "regeneration", "REGENERATION", 100, 1);
            }
        }
        ColorUtil.sound(p, "block.glass.place", 1.0f, 0.85f);
        p.getWorld().spawnParticle(Particle.SNOWFLAKE, pl.clone().add(0, 1.2, 0), 80, 2.2, 1.4, 2.2, 0.04);
        p.sendMessage(ColorUtil.c("&#40C4FF🧊 Защитный Ледяной Купол 7×7 установлен на &e12 секунд&#40C4FF!"));
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<Block, org.bukkit.block.data.BlockData> en : saved.entrySet()) {
                    activeTempBlocks.remove(blockKey(en.getKey()));
                    en.getKey().setBlockData(en.getValue(), false);
                }
            }
        }.runTaskLater(plugin, 240L);
    }

    @EventHandler
    public void onGrapplingHook(PlayerFishEvent e) {
        Player p = e.getPlayer();
        String cidMain = ItemFactory.getCustomId(p.getInventory().getItemInMainHand());
        String cidOff = ItemFactory.getCustomId(p.getInventory().getItemInOffHand());
        if (!"grappling_hook".equals(cidMain) && !"grappling_hook".equals(cidOff)) return;
        PlayerFishEvent.State st = e.getState();
        if (st != PlayerFishEvent.State.REEL_IN && st != PlayerFishEvent.State.IN_GROUND
                && st != PlayerFishEvent.State.CAUGHT_ENTITY && st != PlayerFishEvent.State.FAILED_ATTEMPT) {
            return;
        }
        if (plugin.getSpawnEngine().isInsideAnyKabinka(p.getLocation())) {
            p.sendMessage(ColorUtil.c("&#FF1744✖ В кабинках Крюк-Кошка запрещён!"));
            return;
        }
        if (plugin.getSpawnEngine().isInSpawnWorld(p.getLocation())
                && !plugin.getSpawnEngine().isInsidePvpZone(p.getLocation())) {
            p.sendMessage(ColorUtil.c("&#FF1744✖ Крюк-Кошка работает в мире выживания (/rtp) и на /warp pvp!"));
            return;
        }
        Location hookLoc = e.getHook().getLocation();
        if (hookLoc.distanceSquared(p.getLocation()) < 4.0) return;
        if (!checkAndSetCd(p, "grappling_hook", 5)) return;

        Vector pull = hookLoc.toVector().subtract(p.getLocation().toVector());
        double dist = Math.min(18.0, pull.length());
        Vector vel = pull.normalize().multiply(Math.min(1.85, 0.85 + dist * 0.08));
        vel.setY(Math.max(0.45, Math.min(1.05, vel.getY() * 0.65 + 0.38)));
        p.setVelocity(vel);
        p.setFallDistance(0.0f);
        ColorUtil.sound(p, "entity.fishing_bobber.retrieve", 1.0f, 1.35f);
        p.getWorld().spawnParticle(Particle.CRIT, p.getLocation().add(0, 0.8, 0), 18, 0.3, 0.3, 0.3, 0.08);
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player)) return;
        Player p = (Player) e.getEntity().getShooter();
        if (e.getEntity() instanceof org.bukkit.entity.EnderPearl
                && plugin.getSpawnEngine().isInsideAnyKabinka(p.getLocation())) {
            e.setCancelled(true);
            ColorUtil.sound(p, "entity.villager.no", 0.9f, 0.9f);
            p.sendMessage(ColorUtil.c("&#FF1744✖ В ПвП-кабинках бросать Эндер-Перки запрещено!"));
            return;
        }
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
                // v6.1: ищем 3D-стрелу только в хотбаре (ванilla-лук берёт стрелы именно оттуда!)
                for (int slot = 0; slot <= 8; slot++) {
                    ItemStack invIt = p.getInventory().getItem(slot);
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

    /**
     * v6.1.3: ТОЧНЫЙ детект стрелы по ФАКТИЧЕСКИ израсходованному стаку (getConsumable) —
     * эффекты вешаются ровно на ту стрелу, которой выстрелили (и на лук)!
     */
    @EventHandler
    public void onBowShootConsume(EntityShootBowEvent e) {
        try {
            if (!(e.getEntity() instanceof Player)) return;
            if (!(e.getProjectile() instanceof Arrow)) return;
            String cid = ItemFactory.getCustomId(e.getConsumable());
            if (cid != null && cid.startsWith("arrow_")) {
                e.getProjectile().getPersistentDataContainer().set(PROJ_ARROW_KEY, PersistentDataType.STRING, cid);
            }
            String bid = ItemFactory.getCustomId(e.getBow());
            if (bid != null && bid.startsWith("bow_")) {
                e.getProjectile().getPersistentDataContainer().set(PROJ_TAG_KEY, PersistentDataType.STRING, bid);
            }
        } catch (Throwable ignored) {}
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
        } else if ("swap_orb".equals(ptype) && e.getHitEntity() instanceof Player && e.getEntity().getShooter() instanceof Player) {
            Player shooter = (Player) e.getEntity().getShooter();
            Player hit = (Player) e.getHitEntity();
            if (shooter.equals(hit) || !shooter.isOnline() || !hit.isOnline()) return;
            if (plugin.getSpawnEngine().isInsideAnyKabinka(shooter.getLocation())
                    || plugin.getSpawnEngine().isInsideAnyKabinka(hit.getLocation())) {
                shooter.sendMessage(ColorUtil.c("&#FF1744✖ Нельзя меняться местами с игроком в ПвП-кабинке!"));
                return;
            }
            if ((plugin.getSpawnEngine().isInSpawnWorld(hit.getLocation()) && !plugin.getSpawnEngine().isInsidePvpZone(hit.getLocation()))
                    || (plugin.getSpawnEngine().isInSpawnWorld(shooter.getLocation()) && !plugin.getSpawnEngine().isInsidePvpZone(shooter.getLocation()))) {
                shooter.sendMessage(ColorUtil.c("&#FF1744✖ Сфера Рокировки не действует в мирной зоне спавна!"));
                return;
            }
            Location locA = shooter.getLocation().clone();
            Location locB = hit.getLocation().clone();
            shooter.teleport(locB);
            hit.teleport(locA);
            shooter.getWorld().spawnParticle(Particle.PORTAL, locB.clone().add(0, 1, 0), 45, 0.5, 0.8, 0.5, 0.2);
            hit.getWorld().spawnParticle(Particle.PORTAL, locA.clone().add(0, 1, 0), 45, 0.5, 0.8, 0.5, 0.2);
            ColorUtil.sound(shooter, "entity.enderman.teleport", 1.0f, 1.25f);
            ColorUtil.sound(hit, "entity.enderman.teleport", 1.0f, 0.85f);
            if (isTeammateTarget(shooter, hit)) {
                ColorUtil.addPot(shooter, "absorption", "ABSORPTION", 200, 1);
                ColorUtil.addPot(shooter, "resistance", "DAMAGE_RESISTANCE", 80, 1);
                ColorUtil.addPot(hit, "absorption", "ABSORPTION", 200, 1);
                ColorUtil.addPot(hit, "resistance", "DAMAGE_RESISTANCE", 80, 1);
                shooter.sendMessage(ColorUtil.c("&#00E676🌀 Вы спасли тиммейта &f" + hit.getName() + " &#00E676Сферой Рокировки! (+4💛 и Сопр II обоим)"));
                hit.sendMessage(ColorUtil.c("&#00E676🌀 Тиммейт &f" + shooter.getName() + " &#00E676поменялся с вами местами и спас вас!"));
            } else if (isEnemyTarget(shooter, hit)) {
                tagPvp30(shooter, hit);
                ColorUtil.addPot(hit, "slowness", "SLOW", 80, 1);
                ColorUtil.addPot(hit, "weakness", "WEAKNESS", 80, 0);
                shooter.sendMessage(ColorUtil.c("&#D500F9🌀 Рокировка выполнена с врагом &f" + hit.getName() + "&#D500F9!"));
                hit.sendMessage(ColorUtil.c("&#D500F9🌀 Игрок &f" + shooter.getName() + " &#D500F9поменялся с вами местами Сферой Рокировки!"));
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
        // v6.1: 3D-СТРЕЛЫ РАБОТАЮТ ПРИ ПОПАДАНИИ В БЛОК (настоящие эффекты в точке удара!)
        Location hitLoc = e.getHitBlock() != null ? e.getHitBlock().getLocation().add(0.5, 1.0, 0.5)
                : (e.getHitEntity() != null ? e.getHitEntity().getLocation() : e.getEntity().getLocation());
        // v6.1.3: стрелы РАБОТАЮТ ВЕЗДЕ (спавн, фармилка, выживание) — запрет только в кабинках!
        boolean hitFxAllowed = !plugin.getSpawnEngine().isInsideAnyKabinka(hitLoc);
        if (atype != null && hitFxAllowed) {
            if ("arrow_explosive".equals(atype)) {
                final Location fLoc = hitLoc;
                final Player shooterRef = e.getEntity().getShooter() instanceof Player ? (Player) e.getEntity().getShooter() : null;
                try {
                    if (shooterRef != null) {
                        fLoc.getWorld().createExplosion(fLoc, 1.6F, false, false, shooterRef);
                    } else {
                        fLoc.getWorld().createExplosion(fLoc.getX(), fLoc.getY(), fLoc.getZ(), 1.6F, false, false);
                    }
                } catch (Throwable t) {
                    try { fLoc.getWorld().createExplosion(fLoc.getX(), fLoc.getY(), fLoc.getZ(), 1.6F, false, false); } catch (Throwable ignored) {}
                }
                fLoc.getWorld().spawnParticle(Particle.LARGE_SMOKE, fLoc, 24, 0.7, 0.55, 0.7, 0.03);
                fLoc.getWorld().playSound(fLoc, "minecraft:entity.generic.explode", 0.9f, 1.15f);
            } else if ("arrow_lightning".equals(atype)) {
                hitLoc.getWorld().strikeLightningEffect(hitLoc);
                hitLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, hitLoc.clone().add(0, 0.6, 0), 24, 0.3, 0.4, 0.3, 0.06);
                hitLoc.getWorld().playSound(hitLoc, "minecraft:entity.lightning_bolt.thunder", 0.8f, 1.45f);
            } else if ("arrow_ice".equals(atype)) {
                hitLoc.getWorld().spawnParticle(Particle.SNOWFLAKE, hitLoc.clone().add(0, 0.4, 0), 28, 0.5, 0.4, 0.5, 0.02);
                hitLoc.getWorld().playSound(hitLoc, "minecraft:block.glass.place", 0.9f, 1.5f);
            } else if ("arrow_vampire".equals(atype)) {
                hitLoc.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, hitLoc.clone().add(0, 0.6, 0), 10, 0.25, 0.3, 0.25, 0.02);
                hitLoc.getWorld().playSound(hitLoc, "minecraft:entity.witch.drink", 0.8f, 0.85f);
            } else if ("arrow_poison".equals(atype)) {
                hitLoc.getWorld().spawnParticle(Particle.ITEM, hitLoc.clone().add(0, 0.5, 0), 18, 0.35, 0.3, 0.35, 0.05, new ItemStack(Material.SLIME_BALL));
                hitLoc.getWorld().playSound(hitLoc, "minecraft:entity.spider.hurt", 0.8f, 1.2f);
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
        String skin = plugin.getStore().getSelectedPlastSkin(p.getName());
        boolean isOwner = plugin.getRanks().isOwnerLevel(p);
        if ("owner".equalsIgnoreCase(skin) && !isOwner) skin = "classic";

        Material mainMat = Material.OBSIDIAN;
        Material accentMat = Material.CRYING_OBSIDIAN;
        Material glassMat = Material.PURPLE_STAINED_GLASS;
        if ("ice".equalsIgnoreCase(skin)) {
            mainMat = Material.BLUE_ICE; accentMat = Material.PACKED_ICE; glassMat = Material.LIGHT_BLUE_STAINED_GLASS;
        } else if ("magma".equalsIgnoreCase(skin)) {
            mainMat = Material.RED_NETHER_BRICKS; accentMat = Material.MAGMA_BLOCK; glassMat = Material.ORANGE_STAINED_GLASS;
        } else if ("toxic".equalsIgnoreCase(skin)) {
            mainMat = Material.EMERALD_BLOCK; accentMat = ColorUtil.mat("OXIDIZED_COPPER", "PRISMARINE_BRICKS"); glassMat = Material.LIME_STAINED_GLASS;
        } else if ("amethyst".equalsIgnoreCase(skin)) {
            mainMat = Material.AMETHYST_BLOCK; accentMat = Material.PURPUR_BLOCK; glassMat = Material.MAGENTA_STAINED_GLASS;
        } else if ("royal".equalsIgnoreCase(skin)) {
            mainMat = Material.GOLD_BLOCK; accentMat = ColorUtil.mat("GILDED_BLACKSTONE", "OBSIDIAN"); glassMat = Material.YELLOW_STAINED_GLASS;
        } else if ("wither".equalsIgnoreCase(skin)) {
            mainMat = Material.NETHERITE_BLOCK; accentMat = ColorUtil.mat("POLISHED_BLACKSTONE_BRICKS", "OBSIDIAN"); glassMat = Material.RED_STAINED_GLASS;
        } else if ("owner".equalsIgnoreCase(skin)) {
            mainMat = Material.NETHERITE_BLOCK; accentMat = Material.GOLD_BLOCK; glassMat = Material.RED_STAINED_GLASS;
        }

        Location pl = p.getLocation();
        World w = pl.getWorld();
        final Map<Block, org.bukkit.block.data.BlockData> saved = new HashMap<Block, org.bukkit.block.data.BlockData>();
        float pitch = pl.getPitch();

        if (Math.abs(pitch) > 42f) {
            // Horizontal 5x5x2 floor/ceiling with armored stained-glass window inlays!
            int baseY = pitch > 0 ? pl.getBlockY() - 1 : pl.getBlockY() + 3;
            int cx = pl.getBlockX(), cz = pl.getBlockZ();
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    boolean windowCell = Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && !(dx == 0 && dz == 0);
                    for (int dy = 0; dy > -2; dy--) {
                        Block b = w.getBlockAt(cx + dx, baseY + dy, cz + dz);
                        if (isReplaceableForTemp(b)) {
                            saved.put(b, b.getBlockData().clone());
                            activeTempBlocks.add(blockKey(b));
                            Material chosen = windowCell ? glassMat : (((dx + dz + dy) % 2 == 0) ? mainMat : accentMat);
                            b.setType(chosen, false);
                        }
                    }
                }
            }
        } else {
            // Vertical 5x4x2 shield wall 2 blocks ahead of player with armored tactical glass viewport!
            Vector dir = pl.getDirection().setY(0).normalize();
            boolean alongX = Math.abs(dir.getX()) > Math.abs(dir.getZ());
            int stepX = alongX ? (dir.getX() >= 0 ? 2 : -2) : 0;
            int stepZ = alongX ? 0 : (dir.getZ() >= 0 ? 2 : -2);
            int cx = pl.getBlockX() + stepX;
            int cz = pl.getBlockZ() + stepZ;
            int cy = pl.getBlockY();
            for (int spread = -2; spread <= 2; spread++) {
                for (int dy = 0; dy <= 3; dy++) {
                    boolean viewport = Math.abs(spread) <= 1 && (dy == 1 || dy == 2);
                    for (int thick = 0; thick <= 1; thick++) {
                        int bx = alongX ? cx + (stepX > 0 ? thick : -thick) : cx + spread;
                        int bz = alongX ? cz + spread : cz + (stepZ > 0 ? thick : -thick);
                        Block b = w.getBlockAt(bx, cy + dy, bz);
                        if (isReplaceableForTemp(b)) {
                            saved.put(b, b.getBlockData().clone());
                            activeTempBlocks.add(blockKey(b));
                            Material chosen = viewport ? glassMat : (((spread + dy + thick) % 2 == 0) ? mainMat : accentMat);
                            b.setType(chosen, false);
                        }
                    }
                }
            }
        }

        // Apply Plast Skin tactical buffs/debuffs!
        if ("magma".equalsIgnoreCase(skin)) {
            ColorUtil.addPot(p, "fire_resistance", "FIRE_RESISTANCE", 160, 0);
        } else if ("amethyst".equalsIgnoreCase(skin)) {
            ColorUtil.addPot(p, "speed", "SPEED", 120, 1);
            ColorUtil.addPot(p, "jump_boost", "JUMP", 120, 1);
        } else if ("royal".equalsIgnoreCase(skin)) {
            ColorUtil.addPot(p, "absorption", "ABSORPTION", 160, 0);
        } else if ("owner".equalsIgnoreCase(skin)) {
            ColorUtil.addPot(p, "regeneration", "REGENERATION", 120, 1);
            ColorUtil.addPot(p, "speed", "SPEED", 120, 2);
            ColorUtil.addPot(p, "absorption", "ABSORPTION", 160, 1);
        }
        for (Entity en : p.getNearbyEntities(5.0, 4.0, 5.0)) {
            if (en instanceof Player && isEnemyTarget(p, (Player) en)) {
                Player v = (Player) en;
                if ("ice".equalsIgnoreCase(skin)) {
                    v.setFreezeTicks(160);
                    ColorUtil.addPot(v, "slowness", "SLOW", 80, 1);
                } else if ("magma".equalsIgnoreCase(skin)) {
                    v.setFireTicks(80);
                } else if ("toxic".equalsIgnoreCase(skin)) {
                    ColorUtil.addPot(v, "poison", "POISON", 80, 1);
                } else if ("wither".equalsIgnoreCase(skin) || "owner".equalsIgnoreCase(skin)) {
                    ColorUtil.addPot(v, "wither", "WITHER", 80, 1);
                }
            }
        }

        ColorUtil.sound(p, "block.anvil.land", 0.85f, 1.2f);
        p.sendMessage(ColorUtil.c("&#A855F7🧱 Пласт 5×5×2 установлен на &e12 сек. &8(Скин: &d" + skin.toUpperCase() + "&8)"));
        scheduleRestore(saved, 240L);
    }

    private void spawnTemporaryTrapka(Player p) {
        String skin = plugin.getStore().getSelectedTrapSkin(p.getName());
        boolean isOwner = plugin.getRanks().isOwnerLevel(p);
        if ("owner".equalsIgnoreCase(skin) && !isOwner) skin = "classic";

        Material mainMat = Material.NETHERITE_BLOCK;
        Material accentMat = Material.OBSIDIAN;
        Material windowGlass = Material.BLACK_STAINED_GLASS;
        Material cornerLight = Material.SEA_LANTERN;
        if ("ice".equalsIgnoreCase(skin)) {
            mainMat = Material.BLUE_ICE; accentMat = Material.PACKED_ICE;
            windowGlass = Material.LIGHT_BLUE_STAINED_GLASS; cornerLight = Material.SEA_LANTERN;
        } else if ("inferno".equalsIgnoreCase(skin)) {
            mainMat = Material.RED_NETHER_BRICKS; accentMat = Material.MAGMA_BLOCK;
            windowGlass = Material.ORANGE_STAINED_GLASS; cornerLight = Material.SHROOMLIGHT;
        } else if ("toxic".equalsIgnoreCase(skin)) {
            mainMat = Material.EMERALD_BLOCK; accentMat = ColorUtil.mat("OXIDIZED_COPPER", "PRISMARINE_BRICKS");
            windowGlass = Material.LIME_STAINED_GLASS; cornerLight = Material.SEA_LANTERN;
        } else if ("void".equalsIgnoreCase(skin)) {
            mainMat = Material.CRYING_OBSIDIAN; accentMat = Material.PURPUR_BLOCK;
            windowGlass = Material.MAGENTA_STAINED_GLASS; cornerLight = ColorUtil.mat("PEARLESCENT_FROGLIGHT", "SEA_LANTERN");
        } else if ("royal".equalsIgnoreCase(skin)) {
            mainMat = Material.GOLD_BLOCK; accentMat = Material.AMETHYST_BLOCK;
            windowGlass = Material.YELLOW_STAINED_GLASS; cornerLight = ColorUtil.mat("OCHRE_FROGLIGHT", "GLOWSTONE");
        } else if ("blood".equalsIgnoreCase(skin)) {
            mainMat = Material.REDSTONE_BLOCK; accentMat = ColorUtil.mat("CRIMSON_HYPHAE", "NETHER_WART_BLOCK");
            windowGlass = Material.RED_STAINED_GLASS; cornerLight = Material.SHROOMLIGHT;
        } else if ("owner".equalsIgnoreCase(skin)) {
            mainMat = Material.NETHERITE_BLOCK; accentMat = Material.GOLD_BLOCK;
            windowGlass = Material.RED_STAINED_GLASS; cornerLight = ColorUtil.mat("OCHRE_FROGLIGHT", "SEA_LANTERN");
        }

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
                        // 3x3 armored stained-glass windows on all 4 side walls + 3x3 roof skylight!
                        boolean sideWindow = (dy >= 0 && dy <= 2)
                                && ((Math.abs(dx) == 2 && Math.abs(dz) <= 1) || (Math.abs(dz) == 2 && Math.abs(dx) <= 1));
                        boolean roofSkylight = (dy == 3) && Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                        boolean floorLight = (dy == -1) && Math.abs(dx) == 1 && Math.abs(dz) == 1;
                        Material chosen;
                        if (sideWindow || roofSkylight) {
                            chosen = windowGlass;
                        } else if (floorLight) {
                            chosen = cornerLight;
                        } else {
                            chosen = ((dx + dy + dz) % 2 == 0) ? mainMat : accentMat;
                        }
                        b.setType(chosen, false);
                    }
                }
            }
        }

        if ("owner".equalsIgnoreCase(skin)) {
            ColorUtil.addPot(p, "regeneration", "REGENERATION", 160, 1);
            ColorUtil.addPot(p, "strength", "INCREASE_DAMAGE", 140, 1);
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
                } else if ("blood".equalsIgnoreCase(skin)) {
                    ColorUtil.addPot(v, "wither", "WITHER", 140, 1);
                    ColorUtil.addPot(v, "slowness", "SLOW", 140, 1);
                } else if ("owner".equalsIgnoreCase(skin)) {
                    ColorUtil.addPot(v, "wither", "WITHER", 120, 2);
                    ColorUtil.addPot(v, "blindness", "BLINDNESS", 100, 0);
                    ColorUtil.addPot(v, "slowness", "SLOW", 160, 2);
                    ColorUtil.addPot(v, "glowing", "GLOWING", 240, 0);
                } else {
                    ColorUtil.addPot(v, "slowness", "SLOW", 120, 0);
                }
                v.sendMessage(ColorUtil.c("&#FF1744⛓ Вы закрыты в Трапке игрока &f" + p.getName() + " &#FF1744(Скин: &e" + skin.toUpperCase() + "&#FF1744)!"));
            }
        }

        ColorUtil.sound(p, "block.iron_door.close", 1f, 0.7f);
        p.sendMessage(ColorUtil.c("&#FF1744⛓ Трапка 5×5×5 [Броне-Стекло] активирована на &e15 сек. &8(Скин: &d" + skin.toUpperCase() + "&8, врагов: &f" + trappedEnemies + "&8)"));
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
                } else if ("totem_ledyanoy".equals(tid)) {
                    // v6.4.0: НОВЫЙ ТОТЕМ — Ледяное Спасение (3 заряда)
                    p.setHealth(p.getMaxHealth());
                    ColorUtil.addPot(p, "resistance", "DAMAGE_RESISTANCE", 300, 2);
                    ColorUtil.addPot(p, "absorption", "ABSORPTION", 500, 3);
                    p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 1.0, 0), 90, 2.5, 1.2, 2.5, 0.05);
                    ColorUtil.sound(p, "block.glass.break", 1.0f, 0.7f);
                    for (Entity en : p.getNearbyEntities(7, 4, 7)) {
                        if (en instanceof Player && !en.equals(p) && isEnemyTarget(p, (Player) en)) {
                            Player v = (Player) en;
                            tagPvp30(p, v);
                            v.setFreezeTicks(220);
                            ColorUtil.addPot(v, "slowness", "SLOW", 140, 2);
                        }
                    }
                    if (curCharges > 1) {
                        ItemStack next = ItemFactory.totemLedyanoy(curCharges - 1);
                        if (inOff) p.getInventory().setItemInOffHand(next); else p.getInventory().setItemInMainHand(next);
                        p.sendMessage(ColorUtil.c("&#7DD3FC🧊 Тотем Ледяного Спасения сработал! Осталось зарядов: &a" + (curCharges - 1) + "/3"));
                    } else {
                        p.sendMessage(ColorUtil.c("&#7DD3FC🧊 Использован последний заряд Тотема Ледяного Спасения (0/3)!"));
                    }
                } else if ("totem_voskresheniya".equals(tid)) {
                    // v6.4.0: НОВЫЙ ТОТЕМ — Воскрешение (5 зарядов + награда)
                    p.setHealth(p.getMaxHealth());
                    ColorUtil.addPot(p, "absorption", "ABSORPTION", 600, 5);
                    ColorUtil.addPot(p, "regeneration", "REGENERATION", 600, 2);
                    ColorUtil.addPot(p, "resistance", "DAMAGE_RESISTANCE", 160, 1);
                    p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1.0, 0), 90, 1.0, 1.4, 1.0, 0.2);
                    ColorUtil.sound(p, "item.totem.use", 1.0f, 1.1f);
                    for (Entity en : p.getNearbyEntities(6, 4, 6)) {
                        if (en instanceof LivingEntity && !en.equals(p)) {
                            en.setVelocity(en.getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(1.5).setY(0.5));
                        }
                    }
                    plugin.getStore().addCoins(p.getName(), 2500);
                    plugin.getStore().addRelics(p.getName(), 5);
                    if (curCharges > 1) {
                        ItemStack next = ItemFactory.totemVoskresheniya(curCharges - 1);
                        if (inOff) p.getInventory().setItemInOffHand(next); else p.getInventory().setItemInMainHand(next);
                        p.sendMessage(ColorUtil.c("&#FFD740✨ Тотем Воскрешения спас вас (+2 500$ и +5❖)! Осталось зарядов: &a" + (curCharges - 1) + "/5"));
                    } else {
                        p.sendMessage(ColorUtil.c("&#FFD740✨ Использован последний заряд Тотема Воскрешения (0/5)!"));
                    }
                } else if ("totem_santy".equals(tid)) {
                    // v6.4.0: НОВЫЙ ТОТЕМ — Деда Мороза (3 заряда + подарки)
                    p.setHealth(p.getMaxHealth());
                    ColorUtil.addPot(p, "resistance", "DAMAGE_RESISTANCE", 240, 1);
                    ColorUtil.addPot(p, "regeneration", "REGENERATION", 300, 1);
                    p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 1.2, 0), 120, 3.0, 1.5, 3.0, 0.06);
                    ColorUtil.sound(p, "entity.villager.celebrate", 1.0f, 1.1f);
                    for (Entity en : p.getNearbyEntities(6, 4, 6)) {
                        if (en instanceof Player && !en.equals(p) && isEnemyTarget(p, (Player) en)) {
                            ColorUtil.addPot((Player) en, "blindness", "BLINDNESS", 100, 0);
                            ColorUtil.addPot((Player) en, "slowness", "SLOW", 100, 1);
                        }
                    }
                    plugin.getStore().addCoins(p.getName(), 5000);
                    plugin.getStore().addSnowflakes(p.getName(), 5);
                    if (curCharges > 1) {
                        ItemStack next = ItemFactory.totemSanty(curCharges - 1);
                        if (inOff) p.getInventory().setItemInOffHand(next); else p.getInventory().setItemInMainHand(next);
                        p.sendMessage(ColorUtil.c("&#FF4081🎅 Дед Мороз спас вас и рассыпал подарки: +5 000$ и +5 Снежинок ❄! Зарядов: &a" + (curCharges - 1) + "/3"));
                    } else {
                        p.sendMessage(ColorUtil.c("&#FF4081🎅 Использован последний заряд Тотема Деда Мороза (0/3)!"));
                    }
                } else if (tid != null && tid.startsWith("shar_")) {
                    // v6.4.0: любой 3D ШАР в левой руке спасает от смерти ОДИН раз (как тотем) —
                    // раньше шар просто исчезал без всякого эффекта.
                    p.setHealth(Math.min(p.getMaxHealth(), 10.0));
                    ColorUtil.addPot(p, "absorption", "ABSORPTION", 300, 1);
                    ColorUtil.addPot(p, "regeneration", "REGENERATION", 200, 1);
                    p.getWorld().spawnParticle(Particle.CRIT_MAGIC, p.getLocation().add(0, 1.0, 0), 70, 0.9, 1.2, 0.9, 0.1);
                    ColorUtil.sound(p, "block.amethyst_cluster.break", 1.0f, 1.2f);
                    p.sendMessage(ColorUtil.c("&#E040FB🔮 Ваш 3D Шар &#FFD740разбился&#E040FB, но спас вас от смерти! (&a10❤ + Поглощение II&8)"));
                }
            }
        }.runTaskLater(plugin, 1L);
    }

    // ================= PVP, АРЕНА, КАБИНКИ, ВАМПИРИЗМ, БРОНЯ, СТРЕЛЫ, ЛУКИ И БОССЫ =================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player) {
            Player gp = (Player) e.getEntity();
            if (plugin.getStore().isGodMode(gp)) {
                if (e instanceof EntityDamageByEntityEvent
                        && ((EntityDamageByEntityEvent) e).getDamager() instanceof Player
                        && !plugin.getRanks().isOwnerLevel(gp)) {
                    plugin.getStore().disableGodMode(gp);
                    gp.sendMessage(ColorUtil.c("&#FF5252⚔ Режим бессмертия (/god) автоматически отключён при входе в ПвП!"));
                } else {
                    e.setCancelled(true);
                    return;
                }
            }
        }
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

        // v6.4.0: НОВЫЕ ЗАЧАРОВАНИЯ из свитков (Ледяной Клинок / Вампиризм / Взрывной Удар)
        if (e.getDamager() instanceof Player && e.getEntity() instanceof LivingEntity) {
            try {
                applyScrollEnchantsOnHit((Player) e.getDamager(), (LivingEntity) e.getEntity());
            } catch (Throwable ignored) {}
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
            boolean inWagerDuel = false;
            DataStore.ActiveWagerDuel wd = plugin.getStore().getActiveWagerDuel(attacker.getName());
            if (wd != null && (wd.playerA.equalsIgnoreCase(victim.getName()) || wd.playerB.equalsIgnoreCase(victim.getName()))) {
                inWagerDuel = true;
            }
            boolean inKabinka = plugin.getSpawnEngine().isInsideAnyKabinka(victim.getLocation());
            if (!inWagerDuel && !inKabinka) {
                if (plugin.getStore().isDirectTeammate(attacker.getName(), victim.getName())
                        && !plugin.getStore().isTeammatePvpAllowed(attacker.getName(), victim.getName())) {
                    e.setCancelled(true);
                    ColorUtil.actionBar(attacker, "&#00E676🤝 Ваш тиммейт! Урон по тиммейтам отключён (/tm pvp).");
                    return;
                }
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
            // Окисление брони (Меч Крушителя и Меч OWNER — сбалансировано с учётом Закалки брони ×2!)
            if ("sword_krush".equals(weaponId) || "sword_owner".equals(weaponId)) {
                int shred = "sword_owner".equals(weaponId) ? 2 : 1;
                for (ItemStack arm : victim.getInventory().getArmorContents()) {
                    if (arm != null && arm.hasItemMeta() && arm.getItemMeta() instanceof Damageable) {
                        ItemMeta m = arm.getItemMeta();
                        Damageable d = (Damageable) m;
                        d.setDamage(d.getDamage() + shred);
                        arm.setItemMeta(m);
                    }
                }
            }
            // ================= v5.9: 9 НОВЫХ КАСТОМНЫХ 3D МЕЧЕЙ (СПОСОБНОСТИ В ПВП) =================
            boolean customBannerShown = false;
            if ("sword_frost".equals(weaponId)) {
                victim.setFreezeTicks(Math.max(victim.getFreezeTicks(), 160));
                ColorUtil.addPot(victim, "slowness", "SLOW", 80, 2);
                victim.getWorld().spawnParticle(Particle.SNOWFLAKE, victim.getLocation().add(0, 1.1, 0), 12, 0.35, 0.4, 0.35, 0.03);
            } else if ("sword_zeus".equals(weaponId)) {
                String zKey = attacker.getUniqueId() + ":sword_zeus";
                long nowMs = System.currentTimeMillis();
                Long zReady = itemCooldowns.get(zKey);
                if (zReady == null || nowMs >= zReady.longValue()) {
                    itemCooldowns.put(zKey, Long.valueOf(nowMs + 5000L));
                    victim.getWorld().strikeLightningEffect(victim.getLocation());
                    e.setDamage(e.getDamage() + 4.0);
                    victim.setCooldown(Material.SHIELD, 80);
                    ColorUtil.actionBar(attacker, "&#FACC15⚡ МЕЧ ЗЕВСА: Удар Молнии Олимпа (+4⚡ урона + Слом Щита)!");
                    customBannerShown = true;
                }
            } else if ("sword_katana".equals(weaponId)) {
                ColorUtil.addPot(attacker, "speed", "SPEED", 60, 2);
                if (attacker.getFallDistance() > 0.05f && !attacker.isOnGround()) {
                    e.setDamage(e.getDamage() * 1.25);
                    victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1.1, 0), 18, 0.35, 0.4, 0.35, 0.15);
                    ColorUtil.actionBar(attacker, "&#F472B6🌸 КАТАНА САКУРЫ: Идеальный Крит +25% + Скорость III!");
                    customBannerShown = true;
                }
            } else if ("sword_wither".equals(weaponId)) {
                ColorUtil.addPot(victim, "wither", "WITHER", 80, 2); // Иссушение III на 4 сек!
                victim.getWorld().spawnParticle(Particle.SOUL, victim.getLocation().add(0, 1.1, 0), 10, 0.35, 0.4, 0.35, 0.03);
            } else if ("sword_venom".equals(weaponId)) {
                ColorUtil.addPot(victim, "poison", "POISON", 80, 2);
                ColorUtil.addPot(victim, "weakness", "WEAKNESS", 80, 0);
            } else if ("sword_phantom".equals(weaponId)) {
                Vector atkDir = attacker.getLocation().getDirection().setY(0).normalize();
                Vector vicDir = victim.getLocation().getDirection().setY(0).normalize();
                if (atkDir.dot(vicDir) > 0.35) {
                    e.setDamage(e.getDamage() * 1.30);
                    String phKey = attacker.getUniqueId() + ":sword_phantom";
                    long nowMs = System.currentTimeMillis();
                    Long phReady = itemCooldowns.get(phKey);
                    if (phReady == null || nowMs >= phReady.longValue()) {
                        itemCooldowns.put(phKey, Long.valueOf(nowMs + 6000L));
                        ColorUtil.addPot(victim, "blindness", "BLINDNESS", 45, 0);
                    }
                    ColorUtil.actionBar(attacker, "&#818CF8👻 РАПИРА ТЕНЕЙ: Удар в Спину +30% урона!");
                    customBannerShown = true;
                }
            } else if ("sword_phoenix".equals(weaponId)) {
                victim.setFireTicks(140);
                ColorUtil.addPot(attacker, "regeneration", "REGENERATION", 80, 1);
                victim.getWorld().spawnParticle(Particle.FLAME, victim.getLocation().add(0, 1.0, 0), 16, 0.6, 0.4, 0.6, 0.04);
                for (Entity near : victim.getNearbyEntities(2.8, 2.0, 2.8)) {
                    if (near instanceof Player && isEnemyTarget(attacker, (Player) near) && !near.equals(victim)) {
                        near.setFireTicks(80);
                    }
                }
            } else if ("sword_void".equals(weaponId)) {
                String vdKey = attacker.getUniqueId() + ":sword_void";
                long nowMs = System.currentTimeMillis();
                Long vdReady = itemCooldowns.get(vdKey);
                if (vdReady == null || nowMs >= vdReady.longValue()) {
                    itemCooldowns.put(vdKey, Long.valueOf(nowMs + 5000L));
                    if (victim.isGliding()) victim.setGliding(false);
                    Vector pull = attacker.getLocation().toVector().subtract(victim.getLocation().toVector());
                    if (pull.lengthSquared() > 0.25) {
                        victim.setVelocity(pull.normalize().multiply(0.75).setY(0.22));
                    }
                    ColorUtil.actionBar(attacker, "&#38BDF8🌌 МЕЧ БЕЗДНЫ: Враг притянут + Анти-Элитры!");
                    customBannerShown = true;
                }
            } else if ("sword_sever".equals(weaponId)) {
                // v6.2: Клинок Севера — заморозка цели!
                victim.setFreezeTicks(Math.max(victim.getFreezeTicks(), 200));
                ColorUtil.addPot(victim, "slowness", "SLOW", 100, 3);
                victim.getWorld().spawnParticle(Particle.SNOWFLAKE, victim.getLocation().add(0, 1.1, 0), 18, 0.4, 0.6, 0.4, 0.03);
                victim.getWorld().playSound(victim.getLocation(), "minecraft:block.glass.place", 0.7f, 1.4f);
            } else if ("sword_zima".equals(weaponId)) {
                // v6.2: Молот Зимы — ледяная нова вокруг цели!
                Location zl = victim.getLocation();
                for (Entity en : zl.getWorld().getNearbyEntities(zl, 3.0, 2.5, 3.0)) {
                    if (!(en instanceof LivingEntity) || en.equals(attacker)) continue;
                    if (en instanceof Player && !isEnemyTarget(attacker, (Player) en)) continue;
                    LivingEntity le = (LivingEntity) en;
                    Vector push = le.getLocation().toVector().subtract(zl.toVector()).setY(0.0);
                    if (push.lengthSquared() < 0.01) push = new Vector(0.0, 0.0, 1.0);
                    le.setVelocity(push.normalize().multiply(0.9).setY(0.45));
                    ColorUtil.addPot(le, "slowness", "SLOW", 100, 2);
                }
                zl.getWorld().spawnParticle(Particle.SNOWFLAKE, zl.clone().add(0, 1.0, 0), 40, 1.0, 0.7, 1.0, 0.05);
                zl.getWorld().playSound(zl, "minecraft:block.glass.break", 0.9f, 0.8f);
            } else if ("sword_midas".equals(weaponId)) {
                int goldHit = 60 + rng.nextInt(141);
                plugin.getStore().addCoins(attacker.getName(), goldHit);
                ColorUtil.addPot(attacker, "absorption", "ABSORPTION", 100, 1);
                ColorUtil.actionBar(attacker, "&#FFD740👑 ЭКСКАЛИБУР МИДАСА: &a+" + goldHit + "$ &8| &eЗолотые Сердца!");
                customBannerShown = true;
            }

            if (!customBannerShown) {
                double remHp = Math.max(0.0, victim.getHealth() - e.getFinalDamage());
                ColorUtil.actionBar(attacker, "&#FF5252⚔ " + victim.getName() + ": &#FF1744"
                        + String.format(java.util.Locale.US, "%.1f", Double.valueOf(remHp / 2.0)) + " ❤ &8(-"
                        + String.format(java.util.Locale.US, "%.1f", Double.valueOf(e.getFinalDamage() / 2.0)) + "❤)");
            }
            // Кастомные Луки, Стрелы и Трезубец Посейдона!
            if (e.getDamager() instanceof Projectile) {
                applyProjectileWeaponEffects(e, attacker, victim);
            }
            // v6.2: Талисман Мороза — атакующий враг замерзает!
            if (ItemFactory.getCustomId(victim.getInventory().getItemInOffHand()) != null
                    && "tal_moroza".equals(ItemFactory.getCustomId(victim.getInventory().getItemInOffHand()))
                    && checkAndSetCd(victim, "tal_moroza", 4)) {
                attacker.setFreezeTicks(120);
                ColorUtil.addPot(attacker, "slowness", "SLOW", 60, 2);
                victim.getWorld().spawnParticle(Particle.SNOWFLAKE, attacker.getLocation().add(0, 1.0, 0), 20, 0.4, 0.6, 0.4, 0.03);
                ColorUtil.actionBar(victim, "&#80D8FF🧊 Талисман Мороза заморозил атакующего!");
            }
            spawnDamageIndicator(victim.getLocation(), e.getFinalDamage());
        } else if (e.getEntity() instanceof LivingEntity) {
            // v6.1: 3D-ЛУКИ И 3D-СТРЕЛЫ ТЕПЕРЬ РАБОТАЮТ И ПО МОБАМ (фармилка, боссы, выживание)!
            LivingEntity mobVictim = (LivingEntity) e.getEntity();
            if (e.getDamager() instanceof Projectile) {
                applyProjectileWeaponEffects(e, attacker, mobVictim);
                spawnDamageIndicator(mobVictim.getLocation(), e.getFinalDamage());
            }
        }
    }

    /**
     * v6.1: ЭФФЕКТЫ 3D-ОРУЖИЯ СТРЕЛКОВ — работают по игрокам И мобам,
     * числа строго как в описании предметов (Зевс +3❤, Вампир 35%, Разрывная +3❤)!
     */
    private void applyProjectileWeaponEffects(EntityDamageByEntityEvent e, Player attacker, LivingEntity victim) {
        String pTag = e.getDamager().getPersistentDataContainer().get(PROJ_TAG_KEY, PersistentDataType.STRING);
        String aTag = e.getDamager().getPersistentDataContainer().get(PROJ_ARROW_KEY, PersistentDataType.STRING);
        if (pTag == null && aTag == null) return;
        Location vl = victim.getLocation();
        if ("trident_poseidon".equals(pTag)) {
            vl.getWorld().strikeLightningEffect(vl);
            e.setDamage(e.getDamage() + 4.0);
        }
        if ("bow_ice_rw".equals(pTag) || "arrow_ice".equals(aTag)) {
            victim.setFreezeTicks(180);
            ColorUtil.addPot(victim, "slowness", "SLOW", 100, 3);
            vl.getWorld().spawnParticle(Particle.SNOWFLAKE, vl.clone().add(0, 1.0, 0), 20, 0.45, 0.7, 0.45, 0.02);
            vl.getWorld().playSound(vl, "minecraft:block.glass.place", 0.7f, 1.5f);
        }
        if ("bow_inferno".equals(pTag)) {
            victim.setFireTicks(140);
            ColorUtil.addPot(victim, "wither", "WITHER", 100, 1);
            vl.getWorld().spawnParticle(Particle.FLAME, vl.clone().add(0, 1.0, 0), 16, 0.4, 0.6, 0.4, 0.03);
        }
        if ("bow_zeus".equals(pTag) || "arrow_lightning".equals(aTag)) {
            vl.getWorld().strikeLightningEffect(vl);
            e.setDamage(e.getDamage() + 6.0); // +3❤ чистого урона по лору!
            if (victim instanceof Player) {
                try { ((Player) victim).setCooldown(Material.SHIELD, 100); } catch (Throwable ignored) {}
            }
        }
        if ("bow_vampire".equals(pTag)) {
            double heal = Math.min(e.getFinalDamage() * 0.35, Math.max(0.0, attacker.getMaxHealth() - attacker.getHealth()));
            if (heal > 0.0) attacker.setHealth(attacker.getHealth() + heal);
            attacker.getWorld().spawnParticle(Particle.HEART, attacker.getLocation().add(0, 1.4, 0), 6, 0.3, 0.3, 0.3, 0.02);
        }
        if ("arrow_vampire".equals(aTag)) {
            e.setDamage(e.getDamage() + 5.0); // вытягивает 2.5❤ у врага!
            double heal = Math.min(5.0, Math.max(0.0, attacker.getMaxHealth() - attacker.getHealth()));
            if (heal > 0.0) attacker.setHealth(attacker.getHealth() + heal);
            attacker.getWorld().spawnParticle(Particle.HEART, attacker.getLocation().add(0, 1.4, 0), 8, 0.3, 0.3, 0.3, 0.02);
        }
        if ("arrow_explosive".equals(aTag)) {
            vl.getWorld().spawnParticle(Particle.EXPLOSION, vl.clone().add(0, 1, 0), 4, 0.5, 0.5, 0.5, 0.02);
            vl.getWorld().playSound(vl, "minecraft:entity.generic.explode", 0.75f, 1.3f);
            e.setDamage(e.getDamage() + 6.0); // +3❤ ударного урона по лору!
        }
        if ("arrow_poison".equals(aTag)) {
            ColorUtil.addPot(victim, "poison", "POISON", 120, 2);
            ColorUtil.addPot(victim, "wither", "WITHER", 100, 1);
            ColorUtil.addPot(victim, "weakness", "WEAKNESS", 120, 1);
            vl.getWorld().spawnParticle(Particle.ITEM, vl.clone().add(0, 1.0, 0), 14, 0.35, 0.4, 0.35, 0.04, new ItemStack(Material.SLIME_BALL));
        }
        if ("arrow_anti_fly".equals(aTag)) {
            if (victim.isGliding()) victim.setGliding(false);
            victim.setVelocity(new Vector(0, -1.1, 0));
            if (victim instanceof Player) {
                ((Player) victim).sendMessage(ColorUtil.c("&#A855F7⛓ Анти-Флай Стрела ПВО сбила ваш полёт!"));
            }
        }
        if ("arrow_thor".equals(aTag)) {
            // v6.1.3: СТРЕЛА ТОРА — цепная молния до 3 целей рядом!
            vl.getWorld().strikeLightningEffect(vl);
            e.setDamage(e.getDamage() + 8.0);
            int chained = 0;
            for (Entity en : vl.getWorld().getNearbyEntities(vl, 6.0, 4.0, 6.0)) {
                if (chained >= 3) break;
                if (en.equals(victim) || !(en instanceof LivingEntity)) continue;
                if (en instanceof Player && !isEnemyTarget(attacker, (Player) en)) continue;
                Location cl = en.getLocation();
                cl.getWorld().strikeLightningEffect(cl);
                try { ((LivingEntity) en).damage(4.0, attacker); } catch (Throwable ignored) {}
                chained++;
            }
            vl.getWorld().playSound(vl, "minecraft:entity.lightning_bolt.thunder", 0.9f, 1.1f);
            ColorUtil.actionBar(attacker, "&#00E5FF⚡ СТРЕЛА ТОРА: +4❤ и цепная молния по " + chained + " целям!");
        }
        if ("arrow_frostnova".equals(aTag)) {
            // v6.1.3: МОРОЗНАЯ НОВА — заморозка всех врагов вокруг точки попадания!
            victim.setFreezeTicks(240);
            ColorUtil.addPot(victim, "slowness", "SLOW", 140, 4);
            int frozen = 0;
            for (Entity en : vl.getWorld().getNearbyEntities(vl, 4.0, 3.0, 4.0)) {
                if (en.equals(victim) || !(en instanceof LivingEntity)) continue;
                if (en instanceof Player && !isEnemyTarget(attacker, (Player) en)) continue;
                LivingEntity le = (LivingEntity) en;
                le.setFreezeTicks(200);
                ColorUtil.addPot(le, "slowness", "SLOW", 120, 3);
                frozen++;
            }
            vl.getWorld().spawnParticle(Particle.SNOWFLAKE, vl.clone().add(0, 1.0, 0), 60, 1.2, 0.8, 1.2, 0.05);
            vl.getWorld().playSound(vl, "minecraft:block.glass.break", 1.0f, 0.8f);
            ColorUtil.actionBar(attacker, "&#80D8FF❄ МОРОЗНАЯ НОВА: заморожено врагов: " + (frozen + 1) + "!");
        }
        if ("arrow_gold".equals(aTag)) {
            // v6.1.3: ЗОЛОТАЯ СТРЕЛА — крадёт монеты у врага (с моба — бонус)!
            double steal = 0.0;
            if (victim instanceof Player) {
                Player vp = (Player) victim;
                steal = Math.min(150.0, plugin.getStore().getCoins(vp.getName()));
                if (steal > 0.0) {
                    plugin.getStore().takeCoins(vp.getName(), steal);
                    plugin.getStore().addCoins(attacker.getName(), steal);
                    vp.sendMessage(ColorUtil.c("&#FFD740💰 Золотая стрела " + attacker.getName() + " украла у вас &c"
                            + String.format(java.util.Locale.US, "%.0f", Double.valueOf(steal)) + "$&7!"));
                }
            } else {
                steal = 150.0;
                plugin.getStore().addCoins(attacker.getName(), steal);
            }
            vl.getWorld().spawnParticle(Particle.ITEM, vl.clone().add(0, 1.0, 0), 20, 0.4, 0.5, 0.4, 0.05, new ItemStack(Material.GOLD_NUGGET));
            vl.getWorld().playSound(vl, "minecraft:entity.experience_orb.pickup", 1.0f, 0.9f);
            ColorUtil.actionBar(attacker, "&#FFD740💰 ЗОЛОТАЯ СТРЕЛА: +" + String.format(java.util.Locale.US, "%.0f", Double.valueOf(steal)) + "$ с врага!");
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
            double c = 40.0;
            if ("tal_zvezdy".equals(ItemFactory.getCustomId(killer.getInventory().getItemInOffHand()))) {
                c = 50.0;
                ColorUtil.actionBar(killer, "&#FFD740⭐ Талисман Звезды: +25% монет!");
            }
            plugin.getStore().addCoins(killer.getName(), c);
            if (rng.nextInt(100) < 35) {
                plugin.getStore().addSnowflakes(killer.getName(), 1);
                ColorUtil.actionBar(killer, "&#AEEA00🧟 Фармилка: &a+40$ &8| &#80D8FF+1 Снежинка ❄!");
            } else {
                ColorUtil.actionBar(killer, "&#AEEA00🧟 Фармилка: &a+40$ &7(+12 XP)");
            }
            return;
        }
        if (dead instanceof Monster && killer != null && "hunter".equals(plugin.getStore().getJob(killer.getName()))) {
            double hc = 30.0;
            if ("tal_zvezdy".equals(ItemFactory.getCustomId(killer.getInventory().getItemInOffHand()))) hc = 38.0;
            plugin.getStore().addCoins(killer.getName(), hc);
        }
    }

    // ================= v5.9.1: БРОНЯ ЛОМАЕТСЯ НАМНОГО ДОЛЬШЕ + НАСТОЯЩАЯ ПРОЧНОСТЬ VI (6) И VII (7)! =================
    /** КТ на Чарки (50 сек) и Геплы (20 сек)! У привилегии OWNER нету КТ (0 сек)! */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGoldenAppleConsume(PlayerItemConsumeEvent e) {
        ItemStack item = e.getItem();
        if (item == null) return;
        Material m = item.getType();
        if (m == Material.CHORUS_FRUIT && plugin.getSpawnEngine().isInsideAnyKabinka(e.getPlayer().getLocation())) {
            e.setCancelled(true);
            ColorUtil.sound(e.getPlayer(), "entity.villager.no", 0.9f, 0.9f);
            e.getPlayer().sendMessage(ColorUtil.c("&#FF1744✖ В ПвП-кабинках Хорусы и Перки запрещены!"));
            return;
        }
        if (m != Material.ENCHANTED_GOLDEN_APPLE && m != Material.GOLDEN_APPLE) return;

        Player p = e.getPlayer();
        boolean isOwner = plugin.getRanks().isOwnerLevel(p);
        if (isOwner) {
            try { p.setCooldown(m, 0); } catch (Throwable ignored) {}
            return;
        }

        boolean isCharka = (m == Material.ENCHANTED_GOLDEN_APPLE);
        String cdKey = p.getUniqueId() + (isCharka ? ":cd_charka" : ":cd_gapple");
        long now = System.currentTimeMillis();
        Long readyAt = itemCooldowns.get(cdKey);
        if (readyAt != null && now < readyAt.longValue()) {
            long remSec = Math.max(1L, (readyAt.longValue() - now + 999L) / 1000L);
            int remTicks = (int) Math.max(2L, (readyAt.longValue() - now) / 50L);
            e.setCancelled(true);
            try { p.setCooldown(m, remTicks); } catch (Throwable ignored) {}
            p.sendMessage(ColorUtil.c("&#FF5252⏳ " + (isCharka ? "Чарка" : "Гепл")
                    + " на перезарядке (КТ): подождите ещё &#FFD740" + remSec + " сек.&f!"));
            return;
        }

        final int cdSec = isCharka ? 50 : 20;
        itemCooldowns.put(cdKey, Long.valueOf(now + cdSec * 1000L));
        // Schedule 1 tick later so Minecraft doesn't overwrite our setCooldown after completing consume!
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (p.isOnline()) {
                    try { p.setCooldown(m, cdSec * 20); } catch (Throwable ignored) {}
                }
            }
        }, 1L);
        ColorUtil.actionBar(p, isCharka
                ? "&#FF9100🍎 Чарка съедена! КТ на чарку: &f50 сек."
                : "&#FFD740🍏 Гепл съеден! КТ на гепл: &f20 сек.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorDurabilityDamage(PlayerItemDamageEvent e) {
        ItemStack item = e.getItem();
        if (!ItemFactory.isArmorOrShieldItem(item)) return;

        int dmg = e.getDamage();
        if (dmg <= 0) return;

        // Vanilla Minecraft nerfs Unbreaking on armor to 60% + 40%/(lvl+1), so even Unbreaking VII still breaks 65% of hits!
        // Here we overcome that formula so ALL armor lasts ~4x longer AND Прочность VI / VII give massive PvP endurance!
        Enchantment unbEnch = ColorUtil.ench("unbreaking", "DURABILITY");
        int unbLvl = unbEnch != null ? item.getEnchantmentLevel(unbEnch) : 0;

        int ignoreChancePct;
        int maxLossPerHit;
        if (unbLvl >= 7) {
            ignoreChancePct = 88; // Прочность VII: 88% шанс полностью игнорировать износ + максимум 1 ед.!
            maxLossPerHit = 1;
        } else if (unbLvl == 6) {
            ignoreChancePct = 82; // Прочность VI: 82% шанс полностью игнорировать износ + максимум 1 ед.!
            maxLossPerHit = 1;
        } else if (unbLvl == 5) {
            ignoreChancePct = 74;
            maxLossPerHit = 1;
        } else if (unbLvl == 4) {
            ignoreChancePct = 68;
            maxLossPerHit = 1;
        } else {
            ignoreChancePct = 64; // Даже обычная броня ломается в ~3.5 - 4 раза дольше!
            maxLossPerHit = 2;
        }

        if (rng.nextInt(100) < ignoreChancePct) {
            e.setCancelled(true);
            return;
        }

        // Cap heavy crystal/axe/sharpness bursts so armor never shreds in 3 crits!
        int reduced = Math.min(maxLossPerHit, Math.max(1, (dmg + 2) / 4));
        e.setDamage(reduced);

        // Warn player if an armor piece drops below 15% remaining durability!
        int maxDur = item.getType().getMaxDurability();
        if (maxDur > 0 && item.hasItemMeta() && item.getItemMeta() instanceof Damageable) {
            int usedAfter = ((Damageable) item.getItemMeta()).getDamage() + reduced;
            int left = Math.max(0, maxDur - usedAfter);
            if (left > 0 && left <= maxDur * 0.15) {
                Player p = e.getPlayer();
                String warnKey = p.getUniqueId() + ":low_armor_warn";
                long now = System.currentTimeMillis();
                Long nextWarn = itemCooldowns.get(warnKey);
                if (nextWarn == null || now >= nextWarn.longValue()) {
                    itemCooldowns.put(warnKey, Long.valueOf(now + 4500L));
                    ColorUtil.actionBar(p, (left <= maxDur * 0.05 ? "&#FF1744&l⚠ КРИТИЧЕСКАЯ ПРОЧНОСТЬ БРОНИ: " : "&#FFD740⚠ Прочность брони низкая: ")
                            + "&fосталось &c" + left + " ед. &8(/repair)");
                    ColorUtil.sound(p, "block.anvil.place", 0.55f, 1.5f);
                }
            }
        }
    }

    /** Поддержка Наковальни для Прочности VI (6) и Прочности VII (7) без сброса до ванильной III! */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareArmorUnbreakingAnvil(PrepareAnvilEvent e) {
        AnvilInventory inv = e.getInventory();
        ItemStack left = inv.getItem(0);
        ItemStack right = inv.getItem(1);
        if (left == null || right == null || left.getType() == Material.AIR || right.getType() == Material.AIR) return;

        Enchantment unb = ColorUtil.ench("unbreaking", "DURABILITY");
        if (unb == null) return;

        int leftLvl = extractUnbreakingLevel(left, unb);
        int rightLvl = extractUnbreakingLevel(right, unb);
        int targetLvl = 0;
        if (leftLvl == 6 && rightLvl == 6) {
            targetLvl = 7; // Соединение двух VI даёт Прочность VII!
        } else {
            targetLvl = Math.max(leftLvl, rightLvl);
        }
        if (targetLvl < 6) return;

        // Case A: Combining two Enchanted Books (e.g. VI + VI -> Book VII)
        if (left.getType() == Material.ENCHANTED_BOOK && right.getType() == Material.ENCHANTED_BOOK && targetLvl == 7) {
            e.setResult(ItemFactory.bookArmorUnbreaking7(1));
            try { inv.setRepairCost(12); } catch (Throwable ignored) {}
            return;
        }

        // Case B: Applying book/armor onto Armor, Elytra, or Shield
        if (!ItemFactory.isArmorOrShieldItem(left)) return;
        ItemStack base = e.getResult();
        ItemStack out = (base != null && base.getType() != Material.AIR) ? base.clone() : left.clone();
        if (ItemFactory.applyArmorUnbreakingLevel(out, targetLvl) || out.getEnchantmentLevel(unb) >= targetLvl) {
            e.setResult(out);
            try { inv.setRepairCost(targetLvl >= 7 ? 15 : 10); } catch (Throwable ignored) {}
        }
    }

    private int extractUnbreakingLevel(ItemStack st, Enchantment unb) {
        if (st == null || st.getType() == Material.AIR) return 0;
        String cid = ItemFactory.getCustomId(st);
        if ("book_armor_unb_7".equals(cid)) return 7;
        if ("book_armor_unb_6".equals(cid)) return 6;
        int direct = st.getEnchantmentLevel(unb);
        if (direct > 0) return direct;
        if (st.hasItemMeta() && st.getItemMeta() instanceof EnchantmentStorageMeta) {
            return ((EnchantmentStorageMeta) st.getItemMeta()).getStoredEnchantLevel(unb);
        }
        return 0;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player dead = e.getEntity();
        plugin.getHud().clearCombat(dead.getUniqueId());
        Integer deadPrevStreakObj = killStreaks.remove(dead.getUniqueId());
        int deadPrevStreak = deadPrevStreakObj != null ? deadPrevStreakObj.intValue() : 0;
        plugin.getStore().setBack(dead, dead.getLocation());
        plugin.getStore().addDeath(dead.getName());
        plugin.getSpawnEngine().notifyKabinkaDeath(dead.getLocation());

        Player killer = dead.getKiller();
        if (killer != null && !killer.equals(dead)) {
            plugin.getStore().addKill(killer.getName());
            DataStore.Clan kClan = plugin.getStore().getClanOf(killer.getName());
            double clanBonus = kClan != null ? kClan.level * 40.0 : 0.0;
            if (kClan != null) kClan.kills++;
            plugin.getStore().addCoins(killer.getName(), 200.0 + clanBonus);

            if (deadPrevStreak >= 3) {
                double shutBonus = deadPrevStreak * 1000.0;
                plugin.getStore().addCoins(killer.getName(), shutBonus);
                Bukkit.broadcastMessage(ColorUtil.c("&#00E5FF&l⚡ [ПРЕРЫВАНИЕ СЕРИИ] &fИгрок &#00E676" + killer.getName()
                        + " &fостановил серию из &c" + deadPrevStreak + " убийств &fигрока &e" + dead.getName()
                        + " &fи получил &a+" + ColorUtil.fmtMoney(shutBonus) + "$&f!"));
            }

            int streak = (killStreaks.containsKey(killer.getUniqueId()) ? killStreaks.get(killer.getUniqueId()).intValue() : 0) + 1;
            killStreaks.put(killer.getUniqueId(), Integer.valueOf(streak));
            if (streak == 3 || streak == 5 || streak == 10 || streak == 15) {
                double rew = streak * 1200.0;
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

            DataStore.ActiveWagerDuel finishedDuel = plugin.getStore().finishWagerDuel(dead.getName());
            if (finishedDuel != null) {
                String duelWinner = finishedDuel.playerA.equalsIgnoreCase(dead.getName()) ? finishedDuel.playerB : finishedDuel.playerA;
                double pot = finishedDuel.betEach * 2.0;
                if (pot > 0.0) {
                    plugin.getStore().addCoins(duelWinner, pot);
                }
                Bukkit.broadcastMessage(ColorUtil.c("&#FFD740&l⚔ [ДУЭЛЬ] &fПобедитель дуэли: &#00E676&l" + duelWinner
                        + " &7(повержен &c" + dead.getName() + "&7)"
                        + (pot > 0.0 ? " &8— &fКуш: &a+" + ColorUtil.fmtMoney(pot) + "$!" : "!")));
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

        // Наложение Книги Прочности VI / VII перетаскиванием курсора прямо на любую Броню / Элитры / Щит!
        if (e.getWhoClicked() instanceof Player && !(e.getView().getTopInventory().getHolder() instanceof GuiManager.KgMenuHolder)) {
            ItemStack cursor = e.getCursor();
            ItemStack target = e.getCurrentItem();
            String bookId = ItemFactory.getCustomId(cursor);
            if (("book_armor_unb_6".equals(bookId) || "book_armor_unb_7".equals(bookId))
                    && ItemFactory.isArmorOrShieldItem(target)) {
                Player p = (Player) e.getWhoClicked();
                int lvl = "book_armor_unb_7".equals(bookId) ? 7 : 6;
                if (ItemFactory.applyArmorUnbreakingLevel(target, lvl)) {
                    e.setCancelled(true);
                    e.setCurrentItem(target);
                    if (cursor.getAmount() > 1) {
                        cursor.setAmount(cursor.getAmount() - 1);
                        e.setCursor(cursor);
                    } else {
                        e.setCursor(null);
                    }
                    ColorUtil.sound(p, "block.anvil.use", 1.0f, 1.25f);
                    ColorUtil.sound(p, "entity.player.levelup", 0.9f, 1.4f);
                    p.sendMessage(ColorUtil.c("&#00E676🛡 &lКУЗНИЦА БРОНИ &8» &fНа предмет успешно наложена &#FFD740Прочность "
                            + (lvl == 7 ? "VII (7 LVL)" : "VI (6 LVL)") + "&f!"));
                } else {
                    p.sendMessage(ColorUtil.c("&#FFD740⚠ На этой броне уже установлена такая же или более высокая Прочность!"));
                }
                return;
            }
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

    // ================= v5.9: HIDE ALL /lp, /op & ADMIN COMMANDS FROM CLIENT COMMAND LIST FOR EVERYONE EXCEPT OWNER! =================
    private static final Set<String> STRICT_OWNER_ONLY_COMMANDS = new HashSet<String>(Arrays.asList(
            "lp", "lpeditor", "luckperms", "perm", "perms", "permission", "permissions", "pex",
            "op", "deop", "kgadmin", "rebuildspawn", "promoadmin", "setupserver",
            "cp", "cpanel", "commandpanels", "razdacha", "giveaway", "txt", "screentxt",
            "tex", "techworks", "maint", "teh", "stop", "reload", "rl", "restart",
            "plugins", "pl", "ver", "version", "about", "icanhasbukkit", "spigot", "paper", "bukkit",
            "give", "i", "item", "enchant", "effect", "execute", "forceload", "datapack",
            "function", "fill", "setblock", "clone", "summon", "kill", "attribute", "bossbar", "scoreboard", "team"
    ));

    /** v6.1.2: LP-команды, которые видит и может вызывать TECH.ADMIN (полный LuckPerms). */
    private static boolean isTechLpCommand(String cmd) {
        return "lp".equals(cmd) || "lpeditor".equals(cmd) || "luckperms".equals(cmd)
                || "perm".equals(cmd) || "perms".equals(cmd) || "permission".equals(cmd)
                || "permissions".equals(cmd) || "pex".equals(cmd);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandSend(PlayerCommandSendEvent e) {
        Player p = e.getPlayer();
        RankManager.RankDef rk = plugin.getRanks().getRank(p);
        boolean isOwner = rk != null && plugin.getRanks().isOwnerLevel(rk.id);
        // v6.2.1: TECH.ADMIN видит все команды как OWNER!
        if (isOwner || (rk != null && "techadmin".equalsIgnoreCase(rk.id))) return;
        final boolean isTech = rk != null && "techadmin".equalsIgnoreCase(rk.id);

        boolean isCuratorPlus = rk != null && rk.weight >= 275;
        // v6.0.1: OP-тестеры и владельцы kingac.staff тоже считаются составом (иначе /ac пропадает из списка команд!)
        boolean isStaff = plugin.getModeration().isStaff(p) || p.isOp() || p.hasPermission("kingac.staff")
                || p.hasPermission("kinggrief.staff");
        boolean isMlModerPlus = plugin.getRanks().isMlModerOrHigher(p);

        java.util.Iterator<String> it = e.getCommands().iterator();
        while (it.hasNext()) {
            String cmd = it.next().toLowerCase(java.util.Locale.ROOT);
            // Hide ALL plugin-namespaced commands (`luckperms:lp`, `minecraft:op`, `bukkit:pl`, etc.) for non-owner!
            if (cmd.contains(":")) {
                String base = cmd.substring(cmd.lastIndexOf(':') + 1);
                if (isTech && isTechLpCommand(base)) continue; // тех.админ видит luckperms:lp!
                it.remove();
                continue;
            }
            if (STRICT_OWNER_ONLY_COMMANDS.contains(cmd)) {
                if (isTech && isTechLpCommand(cmd)) continue; // тех.админ видит /lp в списке!
                it.remove();
                continue;
            }
            if (!isCuratorPlus && ("curator".equals(cmd) || "kurator".equals(cmd) || "cur".equals(cmd))) {
                it.remove();
                continue;
            }
            if (!isStaff && ("ban".equals(cmd) || "banip".equals(cmd) || "ipban".equals(cmd) || "tempbanip".equals(cmd)
                    || "unban".equals(cmd) || "unbanip".equals(cmd) || "unipban".equals(cmd) || "pardonip".equals(cmd)
                    || "mute".equals(cmd) || "muteip".equals(cmd) || "ipmute".equals(cmd) || "tempmuteip".equals(cmd)
                    || "unmute".equals(cmd) || "unmuteip".equals(cmd) || "unipmute".equals(cmd)
                    || "kick".equals(cmd) || "check".equals(cmd) || "ss".equals(cmd) || "cheatcheck".equals(cmd)
                    || "sw".equals(cmd) || "staffwork".equals(cmd) || "dupeip".equals(cmd) || "alts".equals(cmd)
                    || "twinks".equals(cmd) || "checkip".equals(cmd) || "warn".equals(cmd) || "unwarn".equals(cmd)
                    || "history".equals(cmd) || "clearchat".equals(cmd) || "cc".equals(cmd) || "slowchat".equals(cmd)
                    || "freeze".equals(cmd) || "invsee".equals(cmd) || "ecsee".equals(cmd) || "whois".equals(cmd)
                    || "sc".equals(cmd) || "staffchat".equals(cmd) || "bc".equals(cmd) || "broadcast".equals(cmd)
                    || "reports".equals(cmd) || "replist".equals(cmd) || "kingac".equals(cmd) || "kac".equals(cmd)
                    || "kas".equals(cmd) || "kingantisoft".equals(cmd) || "anticheat".equals(cmd)
                    || "ac".equals(cmd) || "ach".equals(cmd) || "kingach".equals(cmd)
                    || "inject".equals(cmd) || "kinject".equals(cmd) || "inj".equals(cmd)
                    || "probe".equals(cmd) || "schemes".equals(cmd) || "kacprobe".equals(cmd)
                    || "kingconnect".equals(cmd) || "kcon".equals(cmd) || "knet".equals(cmd) || "network".equals(cmd)
                    || "send".equals(cmd) || "gsend".equals(cmd) || "galert".equals(cmd) || "gbroadcast".equals(cmd)
                    || "gbc".equals(cmd) || "sethub".equals(cmd) || "sethubip".equals(cmd) || "setlobby".equals(cmd) || "gsc".equals(cmd)
                    || "globalsc".equals(cmd) || "gstaff".equals(cmd))) {
                it.remove();
                continue;
            }
            if (!isMlModerPlus && ("tp".equals(cmd) || "tphere".equals(cmd) || "s".equals(cmd)
                    || "gm".equals(cmd) || "gms".equals(cmd) || "gamemode".equals(cmd) || "clearlag".equals(cmd))) {
                it.remove();
            }
        }
        if (Bukkit.getPluginManager().getPlugin("KingClear") != null) {
            e.getCommands().add("clear");
            e.getCommands().add("ci");
            e.getCommands().add("clearinv");
        }
        if (Bukkit.getPluginManager().getPlugin("KingAC") != null) {
            e.getCommands().add("report");
            e.getCommands().add("rep");
        }
        if (Bukkit.getPluginManager().getPlugin("KingConnect") != null) {
            e.getCommands().add("servers");
            e.getCommands().add("server");
            e.getCommands().add("hub");
            e.getCommands().add("lobby");
            e.getCommands().add("grief");
            e.getCommands().add("grief1");
            e.getCommands().add("grief2");
            e.getCommands().add("anarchy");
            e.getCommands().add("duels");
            e.getCommands().add("glist");
            e.getCommands().add("find");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCmdPreprocess(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        String rawMsg = e.getMessage();
        String msg = rawMsg.toLowerCase(java.util.Locale.ROOT).trim();

        // Поддержка русской команды /спек -> /spek!
        if (msg.equals("/спек") || msg.startsWith("/спек ")) {
            String rest = rawMsg.length() > 5 ? rawMsg.substring(5) : "";
            e.setMessage("/spek" + rest);
            msg = e.getMessage().toLowerCase(java.util.Locale.ROOT).trim();
        }

        if (!plugin.getStore().isAuthenticated(p)) {
            if (!msg.startsWith("/login") && !msg.startsWith("/l ") && !msg.startsWith("/reg")) {
                e.setCancelled(true);
                p.sendMessage(ColorUtil.c("&#FF1744🔐 Сначала войдите на сервер: &f/login &7или &f/register"));
                return;
            }
        }

        String firstTok = msg.contains(" ") ? msg.substring(0, msg.indexOf(' ')) : msg;
        String bareCmd = firstTok.startsWith("/") ? firstTok.substring(1) : firstTok;
        String baseCmd = bareCmd.contains(":") ? bareCmd.substring(bareCmd.lastIndexOf(':') + 1) : bareCmd;

        RankManager.RankDef rk = plugin.getRanks().getRank(p);
        boolean isOwner = rk != null && ("owner".equalsIgnoreCase(rk.id) || "techadmin".equalsIgnoreCase(rk.id));
        boolean isTech = rk != null && "techadmin".equalsIgnoreCase(rk.id);
        boolean lpAllowed = isTech && (isTechLpCommand(baseCmd)
                || bareCmd.startsWith("luckperms:") || bareCmd.startsWith("kinggriefcore:lp"));

        // STRICT OWNER-ONLY LOCKDOWN FOR ALL LP, LUCKPERMS, OP ("ЗВЕЗДА"), PLUGIN-LIST & DANGEROUS COMMANDS!
        if (!isOwner && !lpAllowed && (STRICT_OWNER_ONLY_COMMANDS.contains(baseCmd)
                || bareCmd.startsWith("luckperms:") || bareCmd.startsWith("kinggriefcore:lp")
                || bareCmd.startsWith("minecraft:op") || bareCmd.startsWith("minecraft:deop")
                || bareCmd.startsWith("bukkit:") || bareCmd.startsWith("spigot:") || bareCmd.startsWith("paper:"))) {
            e.setCancelled(true);
            p.sendMessage(ColorUtil.c("&#FF1744✖ Команда недоступна! Откройте меню сервера: &#FFD740/menu"));
            return;
        }

        // Block private-message bypasses when muted or IP-muted!
        if (!isOwner && ("msg".equals(baseCmd) || "tell".equals(baseCmd) || "w".equals(baseCmd)
                || "m".equals(baseCmd) || "pm".equals(baseCmd) || "r".equals(baseCmd)
                || "reply".equals(baseCmd) || "me".equals(baseCmd))) {
            long mSec = plugin.getStore().getMuteRemainingSec(p.getName());
            String pIp = plugin.getStore().getPlayerIp(p);
            long ipMSec = plugin.getStore().getIpMuteRemainingSec(pIp);
            if (mSec > 0 || ipMSec > 0) {
                e.setCancelled(true);
                p.sendMessage(ColorUtil.c("&#FF5252🔇 Вы находитесь в муте — отправка личных сообщений запрещена!"));
                return;
            }
        }

        // STRICT ML.MODER+ GUARD FOR VANILLA /minecraft:tp & /minecraft:teleport!
        if ("tp".equals(baseCmd) || "teleport".equals(baseCmd)) {
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
        // ================= v6.0.1: МОСТ KingАЧ v8.0 (античит + 8 схем инжекта) =================
        boolean isAcCmd = "kingac".equals(baseCmd) || "ac".equals(baseCmd) || "kac".equals(baseCmd)
                || "kas".equals(baseCmd) || "ach".equals(baseCmd) || "kingach".equals(baseCmd)
                || "anticheat".equals(baseCmd) || "kingantisoft".equals(baseCmd)
                || "kinject".equals(baseCmd) || "inject".equals(baseCmd) || "inj".equals(baseCmd)
                || "probe".equals(baseCmd) || "schemes".equals(baseCmd) || "kacprobe".equals(baseCmd)
                || "reports".equals(baseCmd) || "replist".equals(baseCmd)
                || "ач".equals(baseCmd) || "античит".equals(baseCmd) || "кингач".equals(baseCmd)
                || "инжект".equals(baseCmd) || "схема".equals(baseCmd) || "схемы".equals(baseCmd)
                || "жалоба".equals(baseCmd) || "жалобы".equals(baseCmd)
                || "репорт".equals(baseCmd) || "репорты".equals(baseCmd);
        boolean staffForAc = isAcCmd && (plugin.getModeration().isStaff(p) || p.isOp()
                || p.hasPermission("kingac.staff") || p.hasPermission("kinggrief.staff"));

        if (isAcCmd && !"report".equals(baseCmd) && !"rep".equals(baseCmd)
                && !"cheater".equals(baseCmd) && !"жалоба".equals(baseCmd) && !"репорт".equals(baseCmd)) {
            org.bukkit.plugin.Plugin acPlugin = Bukkit.getPluginManager().getPlugin("KingAC");
            if (acPlugin == null || !acPlugin.isEnabled()) {
                // Античита физически нет в plugins/ — честно объясняем составу, что делать!
                if (staffForAc || isOwner) {
                    e.setCancelled(true);
                    p.sendMessage(ColorUtil.c("&#FF1744&l🛡 [KingАЧ] &#FF5252Античит-плагин НЕ загружен на этом сервере!"));
                    p.sendMessage(ColorUtil.c(" &#FFD7401. &fСкачайте &aKingAC.jar &8(&7v8.0&8) &fс &#80D8FFhttps://kinggrief.vercel.app/KingAC.jar"));
                    p.sendMessage(ColorUtil.c(" &#FFD7402. &fПоложите файл в папку &#00E676plugins/ &fсервера и перезапустите ядро."));
                    p.sendMessage(ColorUtil.c(" &#FFD7403. &fПосле старта появятся команды: &#00E676/ac /kac /kingac /inject /report &7и русские &#00E676/ач /античит /инжект"));
                    p.sendMessage(ColorUtil.c(" &#80D8FFЯдро сервера: &7" + Bukkit.getVersion()));
                }
                return;
            }
            // Античит загружен — снимаем любые блокировки состава, чтобы /ac точно сработал!
            if (staffForAc && e.isCancelled()) {
                e.setCancelled(false);
            }
        }

        // ПОЛНАЯ БЛОКИРОВКА ВСЕХ КОМАНД ВО ВРЕМЯ 30-СЕКУНДНОГО РЕЖИМА ПВП (кроме OWNER, состава с командами KingАЧ и жалоб /report)!
        if (plugin.getHud().isInCombat(p) && !isOwner && !staffForAc
                && !"report".equals(baseCmd) && !"rep".equals(baseCmd) && !"cheater".equals(baseCmd)
                && !"жалоба".equals(baseCmd) && !"spek".equals(baseCmd)) {
            int left = plugin.getHud().getCombatSecondsLeft(p);
            e.setCancelled(true);
            p.sendMessage(ColorUtil.c("&#FF1744⚔ Вы находитесь в &#FF1744&lРЕЖИМЕ ПВП &7(осталось &#FFD740" + left + " сек.&7)! В бою разрешена только жалоба на читера: &#00E676/report <ник>"));
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

        boolean isOwnerChat = plugin.getRanks().isOwnerLevel(p);
        if (!isOwnerChat) {
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
            String pIp = plugin.getStore().getPlayerIp(p);
            long ipMuteSec = plugin.getStore().getIpMuteRemainingSec(pIp);
            if (ipMuteSec > 0) {
                e.setCancelled(true);
                String mReason = plugin.getStore().getIpMuteReason(pIp);
                String mStaff = plugin.getStore().getIpMuteStaff(pIp);
                String remLabel = ipMuteSec > 36500L * 86400L ? "Навсегда" : ColorUtil.fmtTime(ipMuteSec);
                p.sendMessage(ColorUtil.c("&#FF5252🔇 Ваш IP-адрес в муте (Модератор: &#FFD740" + mStaff
                        + "&#FF5252)! Осталось: &f" + remLabel + " &8│ &7Причина: &e" + mReason));
                return;
            }
            int slowWait = plugin.getModeration().checkAndRecordSlowChat(p);
            if (slowWait > 0) {
                e.setCancelled(true);
                p.sendMessage(ColorUtil.c("&#FFD740⏱ Включён медленный режим чата! Подождите ещё &f" + slowWait + " сек."));
                return;
            }
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
        // v6.3.2: КРИТИЧНО — этот обработчик работает в АСИНХРОННОМ потоке чата, а здесь раньше
        // читались миры/координаты игроков и конфиг хранилища (гонка потоков -> вылеты и «пропажа» чата).
        // Строка сообщения уже собрана, поэтому сама доставка перенесена в главный поток (задержка 1 тик).
        final String outMsg = formatted;
        final boolean globalFinal = global;
        final boolean ownerChatFinal = isOwnerChat;
        final String senderName = p.getName();
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (!p.isOnline()) return;
                    Bukkit.getConsoleSender().sendMessage(outMsg);
                    World pw = p.getWorld();
                    Location pl = p.getLocation();
                    for (Player rec : Bukkit.getOnlinePlayers()) {
                        try {
                            if (!ownerChatFinal && plugin.getStore().isIgnoring(rec.getName(), senderName)) continue;
                            if (globalFinal || (rec.getWorld().equals(pw) && rec.getLocation().distanceSquared(pl) <= 10000.0)) {
                                rec.sendMessage(outMsg);
                            }
                        } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
            }
        }.runTask(plugin);
    }
}
