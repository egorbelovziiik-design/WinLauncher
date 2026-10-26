package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class BossEventManager {

    public static final class BossTemplate {
        public final String id;
        public final String displayName;
        public final EntityType entityType;
        public final double maxHp;
        public final BarColor barColor;
        public final BarStyle barStyle;
        public final Material platformMat;
        public final double coinReward;
        public final int relicReward;

        public BossTemplate(String id, String displayName, EntityType entityType, double maxHp,
                            BarColor barColor, BarStyle barStyle, Material platformMat,
                            double coinReward, int relicReward) {
            this.id = id;
            this.displayName = displayName;
            this.entityType = entityType;
            this.maxHp = maxHp;
            this.barColor = barColor;
            this.barStyle = barStyle;
            this.platformMat = platformMat;
            this.coinReward = coinReward;
            this.relicReward = relicReward;
        }
    }

    public static final class ActiveBossEvent {
        public String instanceId;
        public BossTemplate template;
        public Location center; // chest location
        public UUID bossUuid;
        public BossBar bossBar;
        public boolean bossDefeated = false;
        public int secondsLeft;
        public final Map<String, Double> damageMap = new HashMap<String, Double>();
        public final Map<Location, BlockState> savedBlocks = new LinkedHashMap<Location, BlockState>();
        public final List<UUID> hologramStands = new ArrayList<UUID>();
    }

    private final KingGriefCore plugin;
    private final Random rng = new Random();
    private final Map<String, BossTemplate> templates = new LinkedHashMap<String, BossTemplate>();
    // LinkedHashMap preserves exact insertion order -> BossBars stack cleanly top-to-bottom without overlapping!
    private final Map<String, ActiveBossEvent> activeEvents = new LinkedHashMap<String, ActiveBossEvent>();
    private int autoCountdownSec = 420; // First auto-boss in 7 minutes, then every 12 minutes
    private int seq = 1;

    public BossEventManager(KingGriefCore plugin) {
        this.plugin = plugin;
        registerTemplates();
    }

    private void registerTemplates() {
        templates.put("grinch", new BossTemplate(
                "grinch", "&#80D8FF❄ Ледяной Король Гринч", EntityType.STRAY, 800.0,
                BarColor.BLUE, BarStyle.SEGMENTED_10, Material.PACKED_ICE, 75000.0, 25));
        templates.put("krush", new BossTemplate(
                "krush", "&#FF1744🔥 Адский Крушитель", EntityType.WITHER_SKELETON, 1100.0,
                BarColor.RED, BarStyle.SEGMENTED_12, Material.CRYING_OBSIDIAN, 100000.0, 35));
        templates.put("titan", new BossTemplate(
                "titan", "&#FFD740⚡ Титан Олимпа", EntityType.IRON_GOLEM, 1400.0,
                BarColor.YELLOW, BarStyle.SEGMENTED_20, Material.GOLD_BLOCK, 125000.0, 40));
        templates.put("osiris", new BossTemplate(
                "osiris", "&#00E676☠ Фараон Осирис", EntityType.HUSK, 950.0,
                BarColor.GREEN, BarStyle.SEGMENTED_10, Material.EMERALD_BLOCK, 90000.0, 30));
        templates.put("andromeda", new BossTemplate(
                "andromeda", "&#E040FB🌠 Страж Андромеды", EntityType.BLAZE, 900.0,
                BarColor.PURPLE, BarStyle.SEGMENTED_10, Material.AMETHYST_BLOCK, 95000.0, 32));
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                tickSecond();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void shutdown() {
        List<String> ids = new ArrayList<String>(activeEvents.keySet());
        for (String id : ids) {
            endEvent(id, false);
        }
    }

    public List<ActiveBossEvent> getActiveEvents() {
        return new ArrayList<ActiveBossEvent>(activeEvents.values());
    }

    public int getNextAutoSeconds() {
        return autoCountdownSec;
    }

    public void syncBarsForJoin(Player p) {
        if (p == null) return;
        for (ActiveBossEvent ev : activeEvents.values()) {
            if (ev.bossBar != null) {
                ev.bossBar.addPlayer(p);
            }
        }
    }

    public void removeBarsForQuit(Player p) {
        if (p == null) return;
        for (ActiveBossEvent ev : activeEvents.values()) {
            if (ev.bossBar != null) {
                ev.bossBar.removePlayer(p);
            }
        }
    }

    public ActiveBossEvent startRandomEvent() {
        List<BossTemplate> list = new ArrayList<BossTemplate>(templates.values());
        // Prefer a template not currently active so bars look maximally distinct
        List<BossTemplate> unused = new ArrayList<BossTemplate>();
        for (BossTemplate bt : list) {
            boolean used = false;
            for (ActiveBossEvent ev : activeEvents.values()) {
                if (ev.template.id.equals(bt.id)) { used = true; break; }
            }
            if (!used) unused.add(bt);
        }
        BossTemplate pick = unused.isEmpty() ? list.get(rng.nextInt(list.size())) : unused.get(rng.nextInt(unused.size()));
        return spawnBossEvent(pick.id, null);
    }

    public ActiveBossEvent spawnBossEvent(String templateId, Location forcedCenter) {
        if (activeEvents.size() >= 4) {
            // Remove oldest finished/expiring event if 4 already stacked so screen stays readable
            String oldest = activeEvents.keySet().iterator().next();
            endEvent(oldest, false);
        }
        BossTemplate tpl = templates.get(templateId == null ? "" : templateId.toLowerCase(Locale.ROOT));
        if (tpl == null) {
            tpl = templates.get("grinch");
        }

        World surv = forcedCenter != null && forcedCenter.getWorld() != null
                ? forcedCenter.getWorld()
                : VoidSpawnWorldGenerator.getSurvivalWorld();
        if (surv == null) return null;

        Location center = forcedCenter != null ? forcedCenter.getBlock().getLocation() : findNonOverlappingSpot(surv);
        if (center == null) return null;

        ActiveBossEvent ev = new ActiveBossEvent();
        ev.instanceId = tpl.id + "_" + (seq++);
        ev.template = tpl;
        ev.center = center;
        ev.secondsLeft = 900; // 15 minutes

        // Build 5x5 event altar & save original blocks
        buildEventAltar(ev);

        // Spawn Boss Mob above altar
        Location bossLoc = center.clone().add(0.5, 1.2, 2.5);
        LivingEntity boss = (LivingEntity) surv.spawnEntity(bossLoc, tpl.entityType);
        configureBossEntity(boss, tpl, ev.instanceId);
        ev.bossUuid = boss.getUniqueId();

        // Spawn floating hologram lines above Event Chest
        spawnChestHologram(ev);

        // Create dedicated BossBar (automatically stacks BELOW any existing BossBars at top-center!)
        String initTitle = formatBarTitle(ev, tpl.maxHp);
        BossBar bar = Bukkit.createBossBar(initTitle, tpl.barColor, tpl.barStyle);
        bar.setProgress(1.0);
        bar.setVisible(true);
        for (Player on : Bukkit.getOnlinePlayers()) {
            bar.addPlayer(on);
        }
        ev.bossBar = bar;
        activeEvents.put(ev.instanceId, ev);

        int bx = center.getBlockX(), by = center.getBlockY(), bz = center.getBlockZ();
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ColorUtil.c("&#FF1744&l╔══════════════════════════════════════════╗"));
        Bukkit.broadcastMessage(ColorUtil.c("&#FF1744&l║  \uE97A &#FFD740&lНОВЫЙ БОСС-ИВЕНТ НА СЕРВЕРЕ! &#FF1744&l        ║"));
        Bukkit.broadcastMessage(ColorUtil.c("&#FF1744&l║  &fБосс: " + tpl.displayName + " &7(&c" + (int) tpl.maxHp + " ❤&7)"));
        Bukkit.broadcastMessage(ColorUtil.c("&#FF1744&l║  &fМир: &#00E676Выживание (/rtp) &8| &fКоординаты: &#80D8FF" + bx + ", " + by + ", " + bz));
        Bukkit.broadcastMessage(ColorUtil.c("&#FF1744&l║  &7Убейте Босса, чтобы открыть Мифический Сундук!"));
        Bukkit.broadcastMessage(ColorUtil.c("&#FF1744&l╚══════════════════════════════════════════╝"));
        Bukkit.broadcastMessage("");
        for (Player on : Bukkit.getOnlinePlayers()) {
            ColorUtil.sound(on, "entity.ender_dragon.growl", 0.75f, 0.9f);
        }
        return ev;
    }

    private Location findNonOverlappingSpot(World surv) {
        for (int attempt = 0; attempt < 40; attempt++) {
            int x = rng.nextInt(2200) - 1100;
            int z = rng.nextInt(2200) - 1100;
            if (Math.abs(x) < 200 && Math.abs(z) < 200) continue;

            // Ensure >= 450 blocks distance from ALL other active Boss Events so they NEVER overlap!
            boolean tooClose = false;
            for (ActiveBossEvent other : activeEvents.values()) {
                if (other.center.getWorld() != null && other.center.getWorld().equals(surv)) {
                    double dx = other.center.getBlockX() - x;
                    double dz = other.center.getBlockZ() - z;
                    if (Math.hypot(dx, dz) < 450.0) {
                        tooClose = true;
                        break;
                    }
                }
            }
            if (tooClose) continue;
            if (plugin.getStore().overlapsAnyClaim(surv.getName(), x, z, 15)) continue;

            surv.getChunkAt(x >> 4, z >> 4).load();
            Block highest = surv.getHighestBlockAt(x, z);
            Material m = highest.getType();
            if (m == Material.WATER || m == Material.LAVA || m.name().contains("LEAVES")) continue;
            return highest.getLocation().add(0, 1, 0);
        }
        Block fb = surv.getHighestBlockAt(450 + activeEvents.size() * 500, 450);
        return fb.getLocation().add(0, 1, 0);
    }

    private void buildEventAltar(ActiveBossEvent ev) {
        Location c = ev.center;
        World w = c.getWorld();
        if (w == null) return;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Location floor = c.clone().add(dx, -1, dz);
                saveBlock(ev, floor);
                boolean border = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                floor.getBlock().setType(border ? Material.OBSIDIAN : ev.template.platformMat);
                for (int dy = 0; dy <= 3; dy++) {
                    Location air = c.clone().add(dx, dy, dz);
                    saveBlock(ev, air);
                    air.getBlock().setType(Material.AIR);
                }
            }
        }
        // Corner pillars
        int[][] corners = new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}};
        for (int[] cr : corners) {
            Location p1 = c.clone().add(cr[0], 0, cr[1]);
            Location p2 = c.clone().add(cr[0], 1, cr[1]);
            p1.getBlock().setType(Material.COBBLED_DEEPSLATE_WALL);
            p2.getBlock().setType(Material.SEA_LANTERN);
        }
        // Center Loot Chest
        c.getBlock().setType(Material.ENDER_CHEST); // Locked visual until boss dies, then turns into CHEST with loot!
    }

    private void saveBlock(ActiveBossEvent ev, Location loc) {
        Location key = loc.getBlock().getLocation();
        if (!ev.savedBlocks.containsKey(key)) {
            ev.savedBlocks.put(key, key.getBlock().getState());
        }
    }

    private void spawnChestHologram(ActiveBossEvent ev) {
        removeHologram(ev);
        String[] lines = ev.bossDefeated
                ? new String[]{
                        "&#00E676&l🔓 МИФИЧЕСКИЙ СУНДУК ОТКРЫТ!",
                        "&fБосс повержен: " + ev.template.displayName,
                        "&#FFD740▸ Открой и забери награду РВ!"
                }
                : new String[]{
                        "&#FF1744&l🔒 СУНДУК ЗАПЕЧАТАН БОССОМ",
                        "&fСтраж: " + ev.template.displayName,
                        "&7Убейте Босса рядом, чтобы открыть!"
                };
        double baseY = ev.center.getY() + 1.95;
        for (int i = 0; i < lines.length; i++) {
            Location hl = ev.center.clone().add(0.5, baseY - i * 0.28, 0.5);
            ArmorStand as = (ArmorStand) hl.getWorld().spawnEntity(hl, EntityType.ARMOR_STAND);
            as.setVisible(false);
            as.setGravity(false);
            as.setMarker(true);
            as.setSmall(true);
            as.setCustomName(ColorUtil.c(lines[i]));
            as.setCustomNameVisible(true);
            as.addScoreboardTag("kg_holo");
            ev.hologramStands.add(as.getUniqueId());
        }
    }

    private void removeHologram(ActiveBossEvent ev) {
        for (UUID id : ev.hologramStands) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        ev.hologramStands.clear();
    }

    private void configureBossEntity(LivingEntity boss, BossTemplate tpl, String instanceId) {
        boss.addScoreboardTag("kg_event_boss");
        boss.addScoreboardTag("kg_boss_" + instanceId);
        boss.setRemoveWhenFarAway(false);
        boss.setPersistent(true);
        setMaxHealthSafe(boss, tpl.maxHp);
        boss.setHealth(tpl.maxHp);
        boss.setCustomName(ColorUtil.c(tpl.displayName + " &c❤ " + (int) tpl.maxHp));
        boss.setCustomNameVisible(true);

        EntityEquipment eq = boss.getEquipment();
        if (eq != null) {
            if ("krush".equals(tpl.id)) {
                eq.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
                eq.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
                eq.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
                eq.setBoots(new ItemStack(Material.NETHERITE_BOOTS));
                eq.setItemInMainHand(ItemFactory.swordKrush());
            } else if ("grinch".equals(tpl.id)) {
                eq.setHelmet(new ItemStack(Material.DIAMOND_HELMET));
                eq.setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
                eq.setItemInMainHand(ItemFactory.bowIceRw());
            } else if ("osiris".equals(tpl.id)) {
                eq.setHelmet(new ItemStack(Material.GOLDEN_HELMET));
                eq.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
                eq.setItemInMainHand(ItemFactory.swordDracula());
            }
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            eq.setLeggingsDropChance(0f);
            eq.setBootsDropChance(0f);
            eq.setItemInMainHandDropChance(0f);
        }
    }

    private void setMaxHealthSafe(LivingEntity ent, double maxHp) {
        try {
            Attribute attr = null;
            for (String fn : new String[]{"MAX_HEALTH", "GENERIC_MAX_HEALTH"}) {
                try {
                    Field f = Attribute.class.getField(fn);
                    attr = (Attribute) f.get(null);
                    if (attr != null) break;
                } catch (Throwable ignored) {}
            }
            if (attr != null) {
                AttributeInstance inst = ent.getAttribute(attr);
                if (inst != null) inst.setBaseValue(maxHp);
            }
        } catch (Throwable ignored) {
        }
    }

    private String formatBarTitle(ActiveBossEvent ev, double curHp) {
        int bx = ev.center.getBlockX(), by = ev.center.getBlockY(), bz = ev.center.getBlockZ();
        if (!ev.bossDefeated) {
            return ColorUtil.c(ev.template.displayName
                    + " &8│ &c❤ " + (int) Math.ceil(curHp) + "&7/&c" + (int) ev.template.maxHp
                    + " &8│ &#80D8FFXYZ: &f" + bx + " " + by + " " + bz
                    + " &8│ &#FFD740⏳ " + ColorUtil.fmtTime(ev.secondsLeft));
        }
        return ColorUtil.c("&#00E676&l🔓 СУНДУК ОТКРЫТ &8(" + ev.template.displayName + "&8)"
                + " &8│ &#80D8FFXYZ: &f" + bx + " " + by + " " + bz
                + " &8│ &#FFD740⏳ " + ColorUtil.fmtTime(ev.secondsLeft));
    }

    private void tickSecond() {
        autoCountdownSec--;
        if (autoCountdownSec <= 0) {
            autoCountdownSec = 720; // Next auto-event in 12 minutes
            if (activeEvents.size() < 3) {
                startRandomEvent();
            }
        }

        Iterator<Map.Entry<String, ActiveBossEvent>> it = activeEvents.entrySet().iterator();
        List<String> toEnd = new ArrayList<String>();
        while (it.hasNext()) {
            ActiveBossEvent ev = it.next().getValue();
            ev.secondsLeft--;
            if (ev.secondsLeft <= 0) {
                toEnd.add(ev.instanceId);
                continue;
            }
            double curHp = 0.0;
            if (!ev.bossDefeated) {
                Entity e = ev.bossUuid != null ? Bukkit.getEntity(ev.bossUuid) : null;
                if (e instanceof LivingEntity && !e.isDead()) {
                    LivingEntity le = (LivingEntity) e;
                    curHp = le.getHealth();
                    le.setCustomName(ColorUtil.c(ev.template.displayName + " &c❤ " + (int) Math.ceil(curHp)));
                    // Keep boss near its event altar so it never wanders into ocean/caves
                    if (le.getLocation().distanceSquared(ev.center) > 22 * 22) {
                        le.teleport(ev.center.clone().add(0.5, 1.2, 1.5));
                    }
                    // Boss Special Ability every 9 seconds!
                    if (ev.secondsLeft % 9 == 0) {
                        castBossSkill(ev, le);
                    }
                } else if (ev.center.getChunk().isLoaded()) {
                    // Entity died or vanished unexpectedly while chunk loaded -> mark defeated
                    onBossSlain(ev, null);
                } else {
                    curHp = ev.template.maxHp;
                }
            }
            if (ev.bossBar != null) {
                ev.bossBar.setTitle(formatBarTitle(ev, curHp));
                double prog = ev.bossDefeated
                        ? Math.max(0.0, Math.min(1.0, ev.secondsLeft / 180.0))
                        : Math.max(0.0, Math.min(1.0, curHp / ev.template.maxHp));
                ev.bossBar.setProgress(prog);
            }
        }
        for (String id : toEnd) {
            endEvent(id, true);
        }
    }

    private void castBossSkill(ActiveBossEvent ev, LivingEntity boss) {
        Location bl = boss.getLocation();
        World w = bl.getWorld();
        if (w == null) return;
        List<Player> nearby = new ArrayList<Player>();
        for (Entity e : w.getNearbyEntities(bl, 14, 10, 14)) {
            if (e instanceof Player) nearby.add((Player) e);
        }
        if (nearby.isEmpty()) return;

        String id = ev.template.id;
        if ("grinch".equals(id)) {
            w.spawnParticle(Particle.SNOWFLAKE, bl.clone().add(0, 1, 0), 60, 3.0, 1.5, 3.0, 0.05);
            for (Player p : nearby) {
                p.setFreezeTicks(140);
                ColorUtil.addPot(p, "slowness", "SLOW", 80, 1);
                ColorUtil.sound(p, "entity.player.hurt_freeze", 1.0f, 0.9f);
            }
        } else if ("krush".equals(id)) {
            w.spawnParticle(Particle.FLAME, bl.clone().add(0, 1, 0), 60, 3.0, 1.2, 3.0, 0.08);
            for (Player p : nearby) {
                p.setFireTicks(80);
                Vector kb = p.getLocation().toVector().subtract(bl.toVector()).normalize().multiply(0.9).setY(0.45);
                p.setVelocity(kb);
                ColorUtil.sound(p, "entity.blaze.shoot", 1.0f, 0.8f);
            }
        } else if ("titan".equals(id)) {
            for (Player p : nearby) {
                w.strikeLightningEffect(p.getLocation());
                p.damage(4.0, boss);
                p.setVelocity(new Vector(0, 0.75, 0));
            }
        } else if ("osiris".equals(id)) {
            for (Player p : nearby) {
                ColorUtil.addPot(p, "poison", "POISON", 100, 1);
                ColorUtil.addPot(p, "weakness", "WEAKNESS", 100, 0);
            }
        } else {
            w.spawnParticle(Particle.PORTAL, bl.clone().add(0, 1, 0), 70, 2.5, 1.5, 2.5, 0.2);
            for (Player p : nearby) {
                ColorUtil.addPot(p, "levitation", "LEVITATION", 35, 1);
            }
        }
    }

    public void recordBossDamage(LivingEntity boss, Player attacker, double dmg) {
        if (boss == null || attacker == null) return;
        ActiveBossEvent ev = findByBoss(boss);
        if (ev == null) return;
        Double prev = ev.damageMap.get(attacker.getName());
        ev.damageMap.put(attacker.getName(), Double.valueOf((prev != null ? prev.doubleValue() : 0.0) + dmg));
    }

    public boolean handleBossDeath(LivingEntity deadEntity, Player killer) {
        ActiveBossEvent ev = findByBoss(deadEntity);
        if (ev == null) return false;
        onBossSlain(ev, killer);
        return true;
    }

    public boolean isLockedEventChest(Block block) {
        if (block == null) return false;
        Location loc = block.getLocation();
        for (ActiveBossEvent ev : activeEvents.values()) {
            if (!ev.bossDefeated && ev.center.equals(loc)) {
                return true;
            }
        }
        return false;
    }

    private ActiveBossEvent findByBoss(LivingEntity ent) {
        for (ActiveBossEvent ev : activeEvents.values()) {
            if (ev.bossUuid != null && ev.bossUuid.equals(ent.getUniqueId())) return ev;
            if (ent.getScoreboardTags().contains("kg_boss_" + ev.instanceId)) return ev;
        }
        return null;
    }

    private void onBossSlain(ActiveBossEvent ev, Player killer) {
        if (ev.bossDefeated) return;
        ev.bossDefeated = true;
        ev.secondsLeft = 180; // 3 minutes to loot chest!

        // Sort top damagers
        List<Map.Entry<String, Double>> top = new ArrayList<Map.Entry<String, Double>>(ev.damageMap.entrySet());
        Collections.sort(top, new Comparator<Map.Entry<String, Double>>() {
            @Override
            public int compare(Map.Entry<String, Double> a, Map.Entry<String, Double> b) {
                return Double.compare(b.getValue().doubleValue(), a.getValue().doubleValue());
            }
        });
        if (top.isEmpty() && killer != null) {
            top.add(new HashMap.SimpleEntry<String, Double>(killer.getName(), Double.valueOf(ev.template.maxHp)));
        }

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ColorUtil.c("&#00E676&l╔══════════════════════════════════════════╗"));
        Bukkit.broadcastMessage(ColorUtil.c("&#00E676&l║  🏆 &fБосс " + ev.template.displayName + " &#00E676&lПОВЕРЖЕН!"));
        for (int i = 0; i < Math.min(3, top.size()); i++) {
            String pName = top.get(i).getKey();
            double dmg = top.get(i).getValue().doubleValue();
            double mult = (i == 0) ? 1.0 : (i == 1 ? 0.6 : 0.35);
            double coins = Math.round(ev.template.coinReward * mult);
            int relics = Math.max(5, (int) Math.round(ev.template.relicReward * mult));
            int snow = (i == 0) ? 15 : 8;
            plugin.getStore().addCoins(pName, coins);
            plugin.getStore().addRelics(pName, relics);
            plugin.getStore().addSnowflakes(pName, snow);
            if (i == 0) {
                plugin.getStore().unlockTitle(pName, "boss_slayer");
                plugin.getStore().addCaseKeys(pName, "legendary", 1);
            }
            Bukkit.broadcastMessage(ColorUtil.c("&#00E676&l║  &#FFD740#" + (i + 1) + " &f" + pName
                    + " &7(" + (int) dmg + " урона) &8→ &a+" + ColorUtil.fmtMoney(coins) + "$ &b+" + relics + "❖"));
        }
        Bukkit.broadcastMessage(ColorUtil.c("&#00E676&l║  🔓 &fМифический Сундук открыт на 3 минуты!"));
        Bukkit.broadcastMessage(ColorUtil.c("&#00E676&l╚══════════════════════════════════════════╝"));
        Bukkit.broadcastMessage("");
        plugin.getStore().save();

        // Replace EnderChest seal with real Loot Chest stocked with authentic ReallyWorld items!
        Block b = ev.center.getBlock();
        b.setType(Material.CHEST);
        if (b.getState() instanceof Chest) {
            Chest ch = (Chest) b.getState();
            Inventory inv = ch.getBlockInventory();
            populateBossChestLoot(inv, ev.template.id);
        }
        spawnChestHologram(ev);
        ColorUtil.spawnFirework(ev.center);
    }

    private void populateBossChestLoot(Inventory inv, String id) {
        inv.setItem(4, "grinch".equals(id) ? ItemFactory.sharSanty()
                : "krush".equals(id) ? ItemFactory.talKrushitelya()
                : "titan".equals(id) ? ItemFactory.sharTitana()
                : "osiris".equals(id) ? ItemFactory.sharOsirisa()
                : ItemFactory.sharAndromedy());
        inv.setItem(11, ItemFactory.potionKiller(2));
        inv.setItem(12, ItemFactory.potionMedic(2));
        inv.setItem(13, rng.nextBoolean() ? ItemFactory.talFeniksa(3) : ItemFactory.pickSpawner());
        inv.setItem(14, ItemFactory.potionBurp(2));
        inv.setItem(15, ItemFactory.snowflakeCoin(16));
        inv.setItem(21, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 3));
        inv.setItem(22, rng.nextBoolean() ? ItemFactory.swordDracula() : ItemFactory.pickDjamshut());
        inv.setItem(23, new ItemStack(Material.NETHERITE_INGOT, 4));
    }

    public void endEvent(String instanceId, boolean announce) {
        ActiveBossEvent ev = activeEvents.remove(instanceId);
        if (ev == null) return;
        if (ev.bossBar != null) {
            ev.bossBar.removeAll();
            ev.bossBar.setVisible(false);
        }
        if (ev.bossUuid != null) {
            Entity e = Bukkit.getEntity(ev.bossUuid);
            if (e != null) e.remove();
        }
        removeHologram(ev);
        // Clear chest inventory before restoring terrain so items don't scatter
        if (ev.center.getBlock().getState() instanceof Chest) {
            ((Chest) ev.center.getBlock().getState()).getBlockInventory().clear();
        }
        List<BlockState> states = new ArrayList<BlockState>(ev.savedBlocks.values());
        Collections.reverse(states);
        for (BlockState st : states) {
            try { st.update(true, false); } catch (Throwable ignored) {}
        }
        if (announce && !ev.bossDefeated) {
            Bukkit.broadcastMessage(ColorUtil.c("&#FF5252[Ивенты] &7Время ивента " + ev.template.displayName + " &7истекло, алтарь исчез."));
        }
    }
}
