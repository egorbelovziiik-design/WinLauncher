package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A spawn NPC is a small articulated display sculpture, not a mob or an
 * armor stand. Its vanilla blocks and player-head item do not require a
 * resource pack. The independent Interaction and TextDisplay live in
 * SpawnEngine, and every part belongs to the same chunk and session.
 */
final class NpcDisplayModel {
    private static final class Palette {
        final Material material; final int r, g, b;
        Palette(Material material, int r, int g, int b) { this.material = material; this.r = r; this.g = g; this.b = b; }
    }
    private static final Palette[] COATS = {
        new Palette(Material.WHITE_CONCRETE, 207, 213, 214), new Palette(Material.LIGHT_GRAY_CONCRETE, 125, 125, 115),
        new Palette(Material.GRAY_CONCRETE, 54, 57, 61), new Palette(Material.BLACK_CONCRETE, 8, 10, 15),
        new Palette(Material.RED_CONCRETE, 142, 32, 32), new Palette(Material.ORANGE_CONCRETE, 224, 97, 0),
        new Palette(Material.YELLOW_CONCRETE, 241, 175, 21), new Palette(Material.LIME_CONCRETE, 94, 169, 24),
        new Palette(Material.GREEN_CONCRETE, 73, 91, 36), new Palette(Material.CYAN_CONCRETE, 21, 119, 136),
        new Palette(Material.LIGHT_BLUE_CONCRETE, 36, 137, 199), new Palette(Material.BLUE_CONCRETE, 44, 46, 143),
        new Palette(Material.PURPLE_CONCRETE, 100, 32, 156), new Palette(Material.MAGENTA_CONCRETE, 169, 48, 159),
        new Palette(Material.PINK_CONCRETE, 213, 101, 143), new Palette(Material.BROWN_CONCRETE, 96, 60, 32)
    };

    private static final class Part {
        final UUID id;
        final String name;
        final float x, y, z, width, height, depth;
        // The right sleeve, hand and held item rotate together around the shoulder.
        final boolean arm, item;
        Part(UUID id, String name, float x, float y, float z, float width, float height, float depth, boolean arm, boolean item) {
            this.id = id; this.name = name; this.x = x; this.y = y; this.z = z;
            this.width = width; this.height = height; this.depth = depth;
            this.arm = arm; this.item = item;
        }
    }

    private final List<Part> parts = new ArrayList<Part>();
    private float yaw, swing, walkSwing;
    private final boolean moderationModel;

    NpcDisplayModel(World world, SpawnEngine.NpcDef def, ItemStack head, String sessionTag, Set<UUID> valid) {
        this(world, def, head, sessionTag, valid, false);
    }

    NpcDisplayModel(World world, SpawnEngine.NpcDef def, ItemStack head, String sessionTag, Set<UUID> valid, boolean moderationModel) {
        this.moderationModel = moderationModel;
        this.yaw = def.yaw;
        Location base = new Location(world, def.x, def.y, def.z, 0f, 0f);
        Material coat = nearest(def.armorColor);
        Material trim = ("santa".equals(def.id) || "snowmaiden".equals(def.id)) ? Material.WHITE_CONCRETE : Material.YELLOW_CONCRETE;
        Material pants = (def.id.hashCode() & 1) == 0 ? Material.GRAY_CONCRETE : Material.BLUE_CONCRETE;
        try {
            // Positions are local to the NPC's feet. Front is +Z.
            block(world, base, def, sessionTag, valid, "torso", coat, 0, .72f, 0, .50f, .62f, .27f, false);
            block(world, base, def, sessionTag, valid, "belt", trim, 0, .72f, .006f, .51f, .075f, .28f, false);
            block(world, base, def, sessionTag, valid, "lapel", trim, 0, 1.00f, .141f, .055f, .28f, .025f, false);
            block(world, base, def, sessionTag, valid, "leg_left", pants, -.13f, .15f, 0, .235f, .57f, .25f, false);
            block(world, base, def, sessionTag, valid, "leg_right", pants, .13f, .15f, 0, .235f, .57f, .25f, false);
            block(world, base, def, sessionTag, valid, "boot_left", Material.BLACK_CONCRETE, -.13f, 0, .025f, .235f, .15f, .30f, false);
            block(world, base, def, sessionTag, valid, "boot_right", Material.BLACK_CONCRETE, .13f, 0, .025f, .235f, .15f, .30f, false);
            block(world, base, def, sessionTag, valid, "sleeve_left", coat, -.34f, .83f, 0, .17f, .50f, .22f, false);
            block(world, base, def, sessionTag, valid, "hand_left", Material.SMOOTH_SANDSTONE, -.34f, .68f, 0, .17f, .15f, .22f, false);
            block(world, base, def, sessionTag, valid, "sleeve_right", coat, .34f, .83f, 0, .17f, .50f, .22f, true);
            block(world, base, def, sessionTag, valid, "hand_right", Material.SMOOTH_SANDSTONE, .34f, .68f, 0, .17f, .15f, .22f, true);
            // A player-head item is half a block wide at FIXED transform. The old
            // .54 scale made the face tiny and left a gap above the shoulders.
            item(world, base, def, sessionTag, valid, "head", head, 0, 1.59f, 0, 1.0f, false);
            // A narrow crown / winter hat gives the models a distinct silhouette.
            if ("santa".equals(def.id) || "donate".equals(def.id) || "cases_npc".equals(def.id) || "top_npc".equals(def.id)) {
                block(world, base, def, sessionTag, valid, "hat", trim, 0, 1.83f, 0, .47f, .09f, .47f, false);
            }
            if (def.chestOverride != null) {
                block(world, base, def, sessionTag, valid, "wing_left", Material.LIGHT_BLUE_STAINED_GLASS, -.24f, .78f, -.19f, .27f, .48f, .06f, false);
                block(world, base, def, sessionTag, valid, "wing_right", Material.LIGHT_BLUE_STAINED_GLASS, .24f, .78f, -.19f, .27f, .48f, .06f, false);
            }
            if (def.mainHand != null) item(world, base, def, sessionTag, valid, "held_right", def.mainHand, .36f, .76f, .21f, .33f, true);
            if (def.offHand != null) item(world, base, def, sessionTag, valid, "held_left", def.offHand, -.36f, .76f, .21f, .33f, false);
            pose(def.yaw, 0f, true);
        } catch (RuntimeException failure) {
            remove(valid);
            throw failure;
        }
    }

    private static Material nearest(Color c) {
        Palette best = COATS[0]; long score = Long.MAX_VALUE;
        for (Palette p : COATS) {
            long r = c.getRed() - p.r, g = c.getGreen() - p.g, b = c.getBlue() - p.b;
            long d = r*r*3 + g*g*4 + b*b*2;
            if (d < score) { score = d; best = p; }
        }
        return best.material;
    }

    private void decorate(Display display, SpawnEngine.NpcDef def, String tag, String part, Set<UUID> valid) {
        display.setPersistent(false);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.setSilent(true);
        display.setBillboard(Display.Billboard.FIXED);
        display.setViewRange(.85f);
        display.setDisplayWidth(1.5f);
        display.setDisplayHeight(2.1f);
        display.setInterpolationDuration(4);
        if (moderationModel) {
            display.addScoreboardTag("kg_mod_anim");
            display.addScoreboardTag("kg_santa_model_part");
        } else {
            display.addScoreboardTag("kg_npc");
            display.addScoreboardTag("kg_npc_model_part");
            display.addScoreboardTag("kg_npc_" + def.id);
        }
        display.addScoreboardTag("kg_part_" + part);
        display.addScoreboardTag(tag);
        valid.add(display.getUniqueId());
    }

    private void block(World w, Location base, SpawnEngine.NpcDef def, String tag, Set<UUID> valid,
                       String name, Material material, float x, float y, float z, float width, float height, float depth, boolean arm) {
        BlockDisplay d = (BlockDisplay) w.spawnEntity(base, EntityType.BLOCK_DISPLAY);
        // Register before later setters: a failed spawn must also clean this part.
        parts.add(new Part(d.getUniqueId(), name, x, y, z, width, height, depth, arm, false));
        decorate(d, def, tag, name, valid);
        d.setBlock(Bukkit.createBlockData(material));
    }

    private void item(World w, Location base, SpawnEngine.NpcDef def, String tag, Set<UUID> valid,
                      String name, ItemStack stack, float x, float y, float z, float size, boolean arm) {
        ItemDisplay d = (ItemDisplay) w.spawnEntity(base, EntityType.ITEM_DISPLAY);
        parts.add(new Part(d.getUniqueId(), name, x, y, z, size, size, size, arm, true));
        decorate(d, def, tag, name, valid);
        d.setItemStack(stack.clone());
        d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
    }

    boolean isComplete() {
        if (parts.isEmpty()) return false;
        for (Part part : parts) {
            Entity entity = Bukkit.getEntity(part.id);
            if (!(entity instanceof Display) || !entity.isValid() || entity.isDead()) return false;
        }
        return true;
    }

    void remove(Set<UUID> valid) {
        for (Part part : parts) {
            valid.remove(part.id);
            Entity entity = Bukkit.getEntity(part.id);
            if (entity != null) entity.remove();
        }
        parts.clear();
    }

    float yaw() { return yaw; }
    float swing() { return swing; }

    List<Entity> entities() {
        List<Entity> result = new ArrayList<Entity>();
        for (Part part : parts) {
            Entity entity = Bukkit.getEntity(part.id);
            if (entity != null) result.add(entity);
        }
        return result;
    }

    /** Used for short-lived moderation actors; spawn NPCs remain fixed in place. */
    boolean move(Location location, float walk, float armSwing) {
        Location base = location.clone();
        base.setYaw(0f); base.setPitch(0f);
        for (Entity entity : entities()) {
            if (!entity.teleport(base)) return false;
        }
        walkSwing = walk;
        pose(location.getYaw(), armSwing, true);
        return isComplete();
    }

    /** No teleport or living-entity animation packets; transforms interpolate client-side. */
    void pose(float newYaw, float newSwing, boolean force) {
        if (!force && Math.abs(newYaw - yaw) < .35f && Math.abs(newSwing - swing) < .015f) return;
        yaw = newYaw;
        swing = newSwing;
        for (Part part : parts) {
            Entity entity = Bukkit.getEntity(part.id);
            if (!(entity instanceof Display) || !entity.isValid()) continue;
            Matrix4f m = new Matrix4f().rotateY((float) Math.toRadians(-newYaw));
            if (part.arm) {
                m.translate(part.x, 1.33f, 0).rotateX(newSwing).translate(-part.x, -1.33f, 0);
            } else if (walkSwing != 0f && (part.name.startsWith("leg_") || part.name.startsWith("boot_"))) {
                float legAngle = part.name.endsWith("left") ? walkSwing : -walkSwing;
                m.translate(part.x, .72f, 0).rotateX(legAngle).translate(-part.x, -.72f, 0);
            }
            if (part.item) {
                m.translate(part.x, part.y, part.z).scale(part.width, part.height, part.depth);
            } else {
                m.translate(part.x - part.width/2, part.y, part.z - part.depth/2)
                        .scale(part.width, part.height, part.depth);
            }
            Display display = (Display) entity;
            display.setInterpolationDelay(0);
            display.setTransformationMatrix(m);
        }
    }
}
