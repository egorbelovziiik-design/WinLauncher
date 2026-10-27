package net.kinggrief.hub;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class NewYearHubWorldBuilder {

    private static final String SKIN_WINTER_KING = "2340c0e03dd24a11b15a8b33c2a7e9e32ab205232b00a9e7794e7682ba8c92ab";
    public static final String NPC_TAG = "khub_npc_grief1";
    private static final String HOLO_TAG = "khub_holo";

    private final KingHubPlugin plugin;
    private final String sessionTag = "khub_ses_" + UUID.randomUUID().toString().substring(0, 8);
    private final Set<UUID> validEntities = new HashSet<UUID>();
    private final Map<String, UUID> staticHolos = new HashMap<String, UUID>();

    private UUID npcBodyUuid;
    private UUID npcHitboxUuid;
    private UUID npcHoloUuid;

    public NewYearHubWorldBuilder(KingHubPlugin plugin) {
        this.plugin = plugin;
    }

    public static final class VoidHubChunkGenerator extends ChunkGenerator {
        @Override
        public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
            // Pure void chunk — zero lag, instant load!
        }
    }

    public World ensureHubWorldAndBuild(String worldName, boolean autoBuildMap) {
        WorldCreator wc = new WorldCreator(worldName);
        wc.generator(new VoidHubChunkGenerator());
        World w = Bukkit.createWorld(wc);
        if (w == null) {
            w = Bukkit.getWorlds().get(0);
        }
        try {
            w.setDifficulty(Difficulty.PEACEFUL);
            w.setTime(13500L); // Festive winter twilight so Christmas tree lights & lanterns glow warmly!
            w.setStorm(false);
            w.setThundering(false);
            w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            w.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
            w.setGameRule(GameRule.DO_FIRE_TICK, false);
            w.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
            w.setSpawnLocation(0, 100, 22);
        } catch (Throwable ignored) {}

        for (int cx = -3; cx <= 3; cx++) {
            for (int cz = -3; cz <= 3; cz++) {
                w.getChunkAt(cx, cz).load(true);
                try {
                    w.getChunkAt(cx, cz).setForceLoaded(true);
                } catch (Throwable ignored) {}
            }
        }

        if (autoBuildMap) {
            // Build procedural New Year Winter Island if center platform is not built yet (or rebuild cleanly)
            if (w.getBlockAt(0, 99, 22).getType() == Material.AIR || w.getBlockAt(0, 105, -7).getType() == Material.AIR) {
                buildFestiveWinterIsland(w);
            }
        }

        purgeForeignDecorEntities(w);
        ensureAllEntitiesAlive(w);
        return w;
    }

    public void rebuildMapForce(World w) {
        buildFestiveWinterIsland(w);
        purgeForeignDecorEntities(w);
        ensureAllEntitiesAlive(w);
    }

    private void buildFestiveWinterIsland(World w) {
        int radius = 32;

        // 1. Floating Ice-Crystal & Smooth Quartz Island Base (Y = 91..99) + Clear Air Dome (Y = 100..135)
        for (int x = -radius - 2; x <= radius + 2; x++) {
            for (int z = -radius - 2; z <= radius + 2; z++) {
                double dist = Math.hypot(x, z);
                for (int y = 100; y <= 134; y++) {
                    Block ab = w.getBlockAt(x, y, z);
                    if (ab.getType() != Material.AIR) ab.setType(Material.AIR, false);
                }
                if (dist > radius + 0.5) continue;

                // Underside crystal taper
                int depth = Math.max(1, (int) Math.round((1.0 - (dist / (radius + 1.0))) * 8.0));
                for (int dy = 1; dy <= depth; dy++) {
                    Material under = (dy == depth) ? Material.BLUE_ICE : ((dy % 2 == 0) ? Material.PACKED_ICE : Material.QUARTZ_BRICKS);
                    w.getBlockAt(x, 99 - dy, z).setType(under, false);
                }

                // Surface Floor at Y = 99
                Material floor;
                if (dist >= radius - 1.2) {
                    floor = Material.QUARTZ_BRICKS;
                } else if (Math.abs(x) <= 2 && z >= -4 && z <= 25) {
                    // Main Royal Red-and-Gold Carpet Runway from Spawn (Z=22) -> NPC (Z=9.5) -> Tree (Z=-7)
                    floor = (Math.abs(x) == 2) ? Material.GOLD_BLOCK : (( (x + z) % 2 == 0) ? Material.RED_WOOL : Material.RED_CONCRETE);
                } else if ((Math.abs(x) == Math.abs(z) || x == 0 || z == 0) && dist < radius - 2) {
                    floor = ( ((int) Math.round(dist)) % 5 == 0 ) ? Material.SEA_LANTERN : Material.CHISELED_QUARTZ_BLOCK;
                } else if (((x * x + z * z) % 19 == 0) && dist < radius - 3) {
                    floor = Material.SEA_LANTERN;
                } else if (((Math.abs(x) + Math.abs(z)) % 4 == 0) && dist > 6) {
                    floor = Material.PACKED_ICE;
                } else {
                    floor = Material.SMOOTH_QUARTZ;
                }
                w.getBlockAt(x, 99, z).setType(floor, false);

                // Low Perimeter Balustrade (R = 31..32) so Hub looks framed and regal
                if (dist >= radius - 0.9 && dist <= radius + 0.4) {
                    w.getBlockAt(x, 100, z).setType(Material.QUARTZ_BRICKS, false);
                    if ((x + z) % 3 == 0) {
                        w.getBlockAt(x, 101, z).setType(Material.BLUE_ICE, false);
                    }
                } else if (dist > 11 && dist < radius - 2 && floor == Material.SMOOTH_QUARTZ && ((x * 31 + z * 17) & 7) == 0) {
                    // Light dusting of snow away from main center walkway
                    w.getBlockAt(x, 100, z).setType(Material.SNOW, false);
                }
            }
        }

        // 2. Arrival Spawn Medallion (0, 99, 22) + Flanking Slime Launchpads (-6, 99, 16) & (+6, 99, 16)
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Material m = (dx == 0 && dz == 0) ? Material.SEA_LANTERN
                        : (Math.abs(dx) == 2 || Math.abs(dz) == 2 ? Material.CHISELED_QUARTZ_BLOCK : Material.GOLD_BLOCK);
                w.getBlockAt(dx, 99, 22 + dz).setType(m, false);
                w.getBlockAt(dx, 100, 22 + dz).setType(Material.AIR, false);
            }
        }
        int[] padXs = {-6, 6};
        for (int px : padXs) {
            w.getBlockAt(px, 99, 16).setType(Material.SLIME_BLOCK, false);
            w.getBlockAt(px, 100, 16).setType(Material.AIR, false);
            w.getBlockAt(px - 1, 99, 16).setType(Material.GOLD_BLOCK, false);
            w.getBlockAt(px + 1, 99, 16).setType(Material.GOLD_BLOCK, false);
            w.getBlockAt(px, 99, 15).setType(Material.GOLD_BLOCK, false);
            w.getBlockAt(px, 99, 17).setType(Material.GOLD_BLOCK, false);
        }

        // 3. Royal Pedestal for NPC «❄ 1 ГРИФ» at (0, 99, 9) — crystal-clear approach!
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Material m = (dx == 0 && dz == 0) ? Material.GOLD_BLOCK
                        : ((Math.abs(dx) == 1 && Math.abs(dz) == 1) ? Material.SEA_LANTERN : Material.CHISELED_QUARTZ_BLOCK);
                w.getBlockAt(dx, 99, 9 + dz).setType(m, false);
                w.getBlockAt(dx, 100, 9 + dz).setType(Material.AIR, false);
            }
        }

        // 4. 12 Perimeter Winter Crystal Pillars + Lanterns
        for (int i = 0; i < 12; i++) {
            double ang = (Math.PI * 2.0 * i) / 12.0;
            int px = (int) Math.round(Math.cos(ang) * 29.0);
            int pz = (int) Math.round(Math.sin(ang) * 29.0);
            for (int y = 100; y <= 106; y++) {
                w.getBlockAt(px, y, pz).setType(y % 3 == 0 ? Material.BLUE_ICE : Material.QUARTZ_PILLAR, false);
            }
            w.getBlockAt(px, 107, pz).setType(Material.GOLD_BLOCK, false);
            w.getBlockAt(px, 108, pz).setType(Material.SEA_LANTERN, false);
        }

        // 5. Two Festive Snowman Statues welcoming players near Spawn (-9, 100, 18) & (+9, 100, 18)
        int[] snowXs = {-9, 9};
        for (int sx : snowXs) {
            w.getBlockAt(sx, 99, 18).setType(Material.CHISELED_QUARTZ_BLOCK, false);
            w.getBlockAt(sx, 100, 18).setType(Material.SNOW_BLOCK, false);
            w.getBlockAt(sx, 101, 18).setType(Material.SNOW_BLOCK, false);
            w.getBlockAt(sx, 102, 18).setType(Material.CARVED_PUMPKIN, false);
        }

        // 6. GRAND NEW YEAR CHRISTMAS TREE at (0, 100, -8) — REAL PERSISTENT SPRUCE LEAVES (NO CONCRETE!)
        int tx = 0;
        int tz = -8;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                w.getBlockAt(tx + dx, 99, tz + dz).setType(Material.SPRUCE_PLANKS, false);
            }
        }
        for (int y = 100; y <= 123; y++) {
            w.getBlockAt(tx, y, tz).setType(Material.SPRUCE_WOOD, false);
            if (y <= 112) {
                w.getBlockAt(tx + 1, y, tz).setType(Material.SPRUCE_WOOD, false);
                w.getBlockAt(tx - 1, y, tz).setType(Material.SPRUCE_WOOD, false);
                w.getBlockAt(tx, y, tz + 1).setType(Material.SPRUCE_WOOD, false);
                w.getBlockAt(tx, y, tz - 1).setType(Material.SPRUCE_WOOD, false);
            }
        }

        BlockData persistentSpruce = createPersistentSpruceLeaves();
        Material[] baubles = {
                Material.SHROOMLIGHT,
                Material.SEA_LANTERN,
                Material.RED_STAINED_GLASS,
                Material.GOLD_BLOCK,
                Material.CYAN_STAINED_GLASS,
                Material.MAGENTA_STAINED_GLASS
        };

        for (int y = 103; y <= 125; y++) {
            double progress = (y - 103) / 22.0; // 0.0 bottom -> 1.0 top
            double baseR = (1.0 - progress) * 8.5 + 1.1;
            // Tiered pine-bough wave!
            double tierWave = ((y - 103) % 3 == 0) ? 1.15 : (((y - 103) % 3 == 1) ? 0.45 : 0.0);
            double rMax = baseR + tierWave;
            int ir = (int) Math.ceil(rMax);

            for (int dx = -ir; dx <= ir; dx++) {
                for (int dz = -ir; dz <= ir; dz++) {
                    double d = Math.hypot(dx, dz);
                    if (d > rMax) continue;
                    Block b = w.getBlockAt(tx + dx, y, tz + dz);
                    if (b.getType() == Material.SPRUCE_WOOD) continue;

                    boolean outerRim = d >= rMax - 0.95;
                    int hash = Math.abs(dx * 73 + dz * 37 + y * 19);
                    if (outerRim && hash % 7 == 0) {
                        b.setType(baubles[hash % baubles.length], false);
                    } else {
                        b.setBlockData(persistentSpruce, false);
                        if (outerRim && hash % 4 == 0 && y < 124) {
                            Block above = w.getBlockAt(tx + dx, y + 1, tz + dz);
                            if (above.getType() == Material.AIR) {
                                above.setType(Material.SNOW, false);
                            }
                        }
                    }
                }
            }
        }

        // Golden Star topper at Y = 126..130
        w.getBlockAt(tx, 126, tz).setType(Material.GOLD_BLOCK, false);
        w.getBlockAt(tx, 127, tz).setType(Material.SEA_LANTERN, false);
        w.getBlockAt(tx + 1, 127, tz).setType(Material.GOLD_BLOCK, false);
        w.getBlockAt(tx - 1, 127, tz).setType(Material.GOLD_BLOCK, false);
        w.getBlockAt(tx, 127, tz + 1).setType(Material.GOLD_BLOCK, false);
        w.getBlockAt(tx, 127, tz - 1).setType(Material.GOLD_BLOCK, false);
        w.getBlockAt(tx, 128, tz).setType(Material.GOLD_BLOCK, false);
        w.getBlockAt(tx, 129, tz).setType(Material.END_ROD, false);

        // Gift Boxes under the New Year Tree!
        int[][] gifts = {
                {-4, 100, -4}, {4, 100, -4}, {-5, 100, -8}, {5, 100, -8},
                {-3, 100, -12}, {3, 100, -12}, {0, 100, -3}
        };
        Material[] giftMats = {
                Material.RED_SHULKER_BOX, Material.YELLOW_SHULKER_BOX,
                Material.CYAN_SHULKER_BOX, Material.LIME_SHULKER_BOX,
                Material.MAGENTA_SHULKER_BOX, Material.ORANGE_SHULKER_BOX,
                Material.WHITE_SHULKER_BOX
        };
        for (int i = 0; i < gifts.length; i++) {
            Block gb = w.getBlockAt(gifts[i][0], gifts[i][1], gifts[i][2]);
            gb.setType(giftMats[i % giftMats.length], false);
        }
    }

    private BlockData createPersistentSpruceLeaves() {
        BlockData bd = Material.SPRUCE_LEAVES.createBlockData();
        if (bd instanceof Leaves) {
            ((Leaves) bd).setPersistent(true);
        }
        return bd;
    }

    public void purgeForeignDecorEntities(World w) {
        if (w == null) return;
        for (Entity e : w.getEntities()) {
            if (e instanceof Player) continue;
            if (e instanceof ArmorStand || e instanceof TextDisplay || e instanceof Interaction) {
                if (!validEntities.contains(e.getUniqueId()) || !e.getScoreboardTags().contains(sessionTag)) {
                    e.remove();
                }
            }
        }
    }

    public void ensureAllEntitiesAlive(World w) {
        if (w == null) return;

        // 1. Static Festive Holograms
        ensureStaticHolo(w, "welcome", 0.5, 104.35, 16.0,
                "&#80D8FF&l❄ НОВОГОДНЕЕ ЛОББИ · &#FF1744&lKINGGRIEF &#80D8FF&l❄\n"
                + "&fВыбери &#80D8FF&l1 ГРИФ &fчерез &#00E5FF&lКомпас [ПКМ] &fили &#FFD740&lНПС [ПКМ]&f!\n"
                + "&7Авто-донат и кейсы: &#00E676&lkinggrief.vercel.app");

        ensureStaticHolo(w, "left_info", -8.0, 102.55, 12.5,
                "&#FFD740&l👑 БЕСПЛАТНЫЙ ДОНАТ НА 1 ГРИФЕ\n"
                + "&8━━━━━━━━━━━━━━━━━━━━━━\n"
                + "&fЗайди на &#80D8FF&l1 ГРИФ &fи пропиши:\n"
                + "&#00E676&l/free dragon &8— &fпривилегия &#F5D0FE&lDRAGON &aБЕСПЛАТНО!\n"
                + "&7А также 10 новогодних подарков Санты!");

        ensureStaticHolo(w, "right_info", 9.0, 102.55, 12.5,
                "&#FF4081&l⚔ ОБНОВЛЕНИЕ KINGGRIEF v5.9\n"
                + "&8━━━━━━━━━━━━━━━━━━━━━━\n"
                + "&#00E5FF✦ &f115 3D-Предметов и 12 3D-Мечей\n"
                + "&#FFD740✦ &fСкины Трапок со стеклом и Пластов\n"
                + "&#FF1744✦ &fНейро-Античит &#FFD740KingАЧ v6.0");

        // 2. Strict Singleton NPC «❄ 1 ГРИФ»
        Location npcLoc = plugin.getNpcLocation(w);
        int cx = npcLoc.getBlockX() >> 4;
        int cz = npcLoc.getBlockZ() >> 4;
        if (!w.isChunkLoaded(cx, cz)) {
            w.getChunkAt(cx, cz).load(true);
        }

        Entity body = npcBodyUuid != null ? Bukkit.getEntity(npcBodyUuid) : null;
        if (body == null || body.isDead() || !body.isValid()) {
            if (npcBodyUuid != null) validEntities.remove(npcBodyUuid);
            ArmorStand as = (ArmorStand) w.spawnEntity(npcLoc, EntityType.ARMOR_STAND);
            as.setPersistent(false);
            as.setBasePlate(false);
            as.setArms(true);
            as.setGravity(false);
            as.setVisible(true);
            as.setInvulnerable(true);
            as.setCustomNameVisible(false);
            as.setRemoveWhenFarAway(false);
            as.addScoreboardTag(NPC_TAG);
            as.addScoreboardTag(sessionTag);

            EntityEquipment eq = as.getEquipment();
            if (eq != null) {
                eq.setHelmet(makeWinterKingSkull());
                eq.setChestplate(glowItem(Material.DIAMOND_CHESTPLATE));
                eq.setLeggings(dyedLeather(Material.LEATHER_LEGGINGS, Color.fromRGB(14, 165, 233)));
                eq.setBoots(dyedLeather(Material.LEATHER_BOOTS, Color.fromRGB(250, 204, 21)));
                eq.setItemInMainHand(glowItem(Material.DIAMOND_SWORD));
                eq.setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
            }
            npcBodyUuid = as.getUniqueId();
            validEntities.add(npcBodyUuid);
        }

        Entity hit = npcHitboxUuid != null ? Bukkit.getEntity(npcHitboxUuid) : null;
        if (hit == null || hit.isDead() || !hit.isValid()) {
            if (npcHitboxUuid != null) validEntities.remove(npcHitboxUuid);
            Interaction inter = (Interaction) w.spawnEntity(npcLoc, EntityType.INTERACTION);
            inter.setPersistent(false);
            inter.setInteractionWidth(1.35f);
            inter.setInteractionHeight(2.35f);
            inter.setResponsive(true);
            inter.setInvulnerable(true);
            inter.addScoreboardTag(NPC_TAG);
            inter.addScoreboardTag(sessionTag);
            npcHitboxUuid = inter.getUniqueId();
            validEntities.add(npcHitboxUuid);
        }

        String npcText = plugin.buildNpcHoloText();
        Entity holo = npcHoloUuid != null ? Bukkit.getEntity(npcHoloUuid) : null;
        if (holo == null || holo.isDead() || !holo.isValid()) {
            if (npcHoloUuid != null) validEntities.remove(npcHoloUuid);
            Entity spawned = spawnHoloEntity(w, npcLoc.getX(), npcLoc.getY() + 2.48, npcLoc.getZ(), npcText);
            spawned.addScoreboardTag(NPC_TAG);
            npcHoloUuid = spawned.getUniqueId();
        } else {
            updateHoloText(holo, npcText);
        }

        // Strict dedupe around NPC spot so there is NEVER more than 1 NPC!
        for (Entity near : w.getNearbyEntities(npcLoc, 2.0, 4.0, 2.0)) {
            if (near instanceof Player) continue;
            UUID u = near.getUniqueId();
            if (u.equals(npcBodyUuid) || u.equals(npcHitboxUuid) || u.equals(npcHoloUuid)) continue;
            if (near.getScoreboardTags().contains(NPC_TAG)
                    || (!validEntities.contains(u) && (near instanceof ArmorStand || near instanceof Interaction || near instanceof TextDisplay))) {
                near.remove();
            }
        }
    }

    public boolean isGriefNpcEntity(Entity e) {
        if (e == null) return false;
        UUID u = e.getUniqueId();
        if (u.equals(npcBodyUuid) || u.equals(npcHitboxUuid)) return true;
        return e.getScoreboardTags().contains(NPC_TAG);
    }

    public void respawnNpcAtNewSpot(World w) {
        for (UUID u : new UUID[]{npcBodyUuid, npcHitboxUuid, npcHoloUuid}) {
            if (u != null) {
                validEntities.remove(u);
                Entity ent = Bukkit.getEntity(u);
                if (ent != null) ent.remove();
            }
        }
        npcBodyUuid = null;
        npcHitboxUuid = null;
        npcHoloUuid = null;
        ensureAllEntitiesAlive(w);
    }

    private void ensureStaticHolo(World w, String id, double x, double y, double z, String rawText) {
        int cx = ((int) Math.floor(x)) >> 4;
        int cz = ((int) Math.floor(z)) >> 4;
        if (!w.isChunkLoaded(cx, cz)) return;
        UUID curId = staticHolos.get(id);
        Entity cur = curId != null ? Bukkit.getEntity(curId) : null;
        if (cur == null || cur.isDead() || !cur.isValid()) {
            if (curId != null) validEntities.remove(curId);
            Entity spawned = spawnHoloEntity(w, x, y, z, rawText);
            staticHolos.put(id, spawned.getUniqueId());
        }
    }

    private Entity spawnHoloEntity(World w, double x, double y, double z, String rawText) {
        Location loc = new Location(w, x, y, z);
        try {
            TextDisplay td = (TextDisplay) w.spawnEntity(loc, EntityType.TEXT_DISPLAY);
            td.setPersistent(false);
            td.setBillboard(Display.Billboard.CENTER);
            td.setShadowed(true);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setLineWidth(320);
            td.setBackgroundColor(Color.fromARGB(135, 10, 16, 28));
            td.setText(KingHubPlugin.c(rawText));
            td.addScoreboardTag(HOLO_TAG);
            td.addScoreboardTag(sessionTag);
            validEntities.add(td.getUniqueId());
            return td;
        } catch (Throwable fallback) {
            ArmorStand as = (ArmorStand) w.spawnEntity(loc, EntityType.ARMOR_STAND);
            as.setPersistent(false);
            as.setVisible(false);
            as.setGravity(false);
            as.setMarker(true);
            as.setCustomNameVisible(true);
            as.setCustomName(KingHubPlugin.c(rawText.replace("\n", " • ")));
            as.addScoreboardTag(HOLO_TAG);
            as.addScoreboardTag(sessionTag);
            validEntities.add(as.getUniqueId());
            return as;
        }
    }

    private void updateHoloText(Entity e, String rawText) {
        String colored = KingHubPlugin.c(rawText);
        if (e instanceof TextDisplay) {
            TextDisplay td = (TextDisplay) e;
            if (!colored.equals(td.getText())) td.setText(colored);
        } else if (e != null) {
            e.setCustomName(KingHubPlugin.c(rawText.replace("\n", " • ")));
        }
    }

    private ItemStack makeWinterKingSkull() {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        if (sm != null) {
            try {
                PlayerProfile prof = Bukkit.createPlayerProfile(
                        UUID.nameUUIDFromBytes(SKIN_WINTER_KING.getBytes(StandardCharsets.UTF_8)), "KH_WinterKing");
                PlayerTextures tex = prof.getTextures();
                tex.setSkin(new URL("http://textures.minecraft.net/texture/" + SKIN_WINTER_KING));
                prof.setTextures(tex);
                sm.setOwnerProfile(prof);
            } catch (Throwable ignored) {}
            head.setItemMeta(sm);
        }
        return head;
    }

    private ItemStack glowItem(Material mat) {
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.addEnchant(Enchantment.UNBREAKING, 1, true);
            im.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(im);
        }
        return it;
    }

    private ItemStack dyedLeather(Material mat, Color color) {
        ItemStack it = new ItemStack(mat);
        LeatherArmorMeta lm = (LeatherArmorMeta) it.getItemMeta();
        if (lm != null) {
            lm.setColor(color);
            lm.setUnbreakable(true);
            it.setItemMeta(lm);
        }
        return it;
    }
}
