package nexus.test;

import nexus.api.*;
import nexus.api.math.Box3;
import nexus.api.math.ScreenPos;
import nexus.api.math.Vec3d;
import nexus.api.screen.NexusScreen;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Headless implementation of the whole {@code nexus.api} surface.
 * <p>
 * The client core is completely independent from Minecraft: everything the
 * modules, the HUD, the GUI and the commands do goes through the small
 * interfaces of {@code nexus.api}. This class implements those interfaces with
 * plain Java objects so the entire client can be started, ticked, rendered and
 * tested without a running game. The version adapters later map the very same
 * interfaces onto the real Minecraft classes.
 */
public final class HeadlessClient implements IClient {

    // ------------------------------------------------------------------ state

    /** Every message the client printed into the chat. */
    public final List<String> chatLines = new ArrayList<String>();
    /** Every message the player sent to the server. */
    public final List<String> sentMessages = new ArrayList<String>();
    /** Number of drawing calls the renderer received (proves the HUD renders). */
    public int drawCalls;
    /** Number of blocks the interaction layer broke. */
    public int blocksBroken;
    /** Number of blocks the interaction layer placed. */
    public int blocksPlaced;
    /** Number of attacks performed. */
    public int attacks;
    /** True while the mouse is grabbed by the game (camera control). */
    public boolean mouseGrabbed = true;
    /** Screens currently open (stack). */
    public final List<NexusScreen> screenStack = new ArrayList<NexusScreen>();
    /** Set when {@link IScreenHost#restoreMouseToGame()} is called. */
    public boolean mouseRestored;
    /** Entities present in the fake world. */
    public final List<IEntity> entities = new ArrayList<IEntity>();
    /** Key states of the fake keyboard. */
    public final Map<Integer, Boolean> keys = new HashMap<Integer, Boolean>();
    /** Rendering toggles used by visual modules. */
    public boolean rendererReloaded;
    public int lastEnchantmentClicked = -1;
    public String[] lastSignText;

    private final HeadlessPlayer player = new HeadlessPlayer();
    private final HeadlessWorld world = new HeadlessWorld();
    private final HeadlessInventory inventory = new HeadlessInventory();
    private final HeadlessRenderer renderer = new HeadlessRenderer();
    private final HeadlessInput input = new HeadlessInput();
    private final HeadlessOptions options = new HeadlessOptions();
    private final HeadlessChat chat = new HeadlessChat();
    private final HeadlessScreens screens = new HeadlessScreens();
    private final HeadlessInteraction interaction = new HeadlessInteraction();
    private final HeadlessResources resources = new HeadlessResources();
    private final HeadlessConnection connection = new HeadlessConnection();
    private final HeadlessWindow window = new HeadlessWindow();

    public HeadlessClient() {
        entities.add(new HeadlessEntity(2, "Alex", "minecraft:player", 12.0, 64.0, 6.0, true));
        entities.add(new HeadlessEntity(3, "Steve", "minecraft:player", -8.0, 64.0, -4.0, true));
        entities.add(new HeadlessEntity(4, "Zombie", "minecraft:zombie", 3.0, 64.0, 3.0, false));
        entities.add(new HeadlessEntity(5, "Diamond", "minecraft:item", 1.0, 64.0, 1.0, false));
    }

    // -------------------------------------------------------------- accessors

    @Override
    public IPlayer player() {
        return player;
    }

    @Override
    public IWorld world() {
        return world;
    }

    @Override
    public IChat chat() {
        return chat;
    }

    @Override
    public IConnection connection() {
        return connection;
    }

    @Override
    public IOptions options() {
        return options;
    }

    @Override
    public IWindow window() {
        return window;
    }

    @Override
    public IRenderer renderer() {
        return renderer;
    }

    @Override
    public IInput input() {
        return input;
    }

    @Override
    public IScreenHost screens() {
        return screens;
    }

    @Override
    public IInteraction interaction() {
        return interaction;
    }

    @Override
    public IResources resources() {
        return resources;
    }

    @Override
    public boolean isInWorldRenderPass() {
        return true;
    }

    @Override
    public boolean isInGame() {
        return true;
    }

    @Override
    public IEntity crosshairTarget() {
        return entities.isEmpty() ? null : entities.get(2);
    }

    @Override
    public Vec3d cameraPosition() {
        return player.position();
    }

    @Override
    public float cameraYaw() {
        return player.yaw();
    }

    @Override
    public float cameraPitch() {
        return player.pitch();
    }

    @Override
    public void execute(Runnable task) {
        task.run();
    }

    @Override
    public void schedule(Runnable task, int tickDelay) {
        task.run();
    }

    @Override
    public String minecraftVersion() {
        return "1.21.4";
    }

    @Override
    public String loaderName() {
        return "NexusHeadless";
    }

    // ============================================================== player

    public final class HeadlessPlayer implements IPlayer {

        private float health = 20.0f;
        private float hunger = 18.0f;
        private float yaw;
        private float pitch;
        private boolean sprinting;
        private boolean sneaking;
        private int jumps;
        private int swings;
        private Vec3d position = new Vec3d(0.0, 64.0, 0.0);
        private Vec3d velocity = new Vec3d(0.0, 0.0, 0.0);

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public String name() {
            return "Tester";
        }

        @Override
        public Vec3d position() {
            return position;
        }

        @Override
        public Vec3d eyePosition() {
            return new Vec3d(position.x, position.y + 1.62, position.z);
        }

        @Override
        public Vec3d velocity() {
            return velocity;
        }

        @Override
        public Box3 boundingBox() {
            return new Box3(position.x - 0.3, position.y, position.z - 0.3,
                    position.x + 0.3, position.y + 1.8, position.z + 0.3);
        }

        @Override
        public float yaw() {
            return yaw;
        }

        @Override
        public float pitch() {
            return pitch;
        }

        @Override
        public void setRotation(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }

        @Override
        public void lookAt(Vec3d target) {
            double dx = target.x - position.x;
            double dz = target.z - position.z;
            double dy = target.y - (position.y + 1.62);
            this.yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            this.pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        }

        @Override
        public void rotateTowards(Vec3d target, double maxDegreesPerTick) {
            lookAt(target);
        }

        @Override
        public float health() {
            return health;
        }

        @Override
        public float maxHealth() {
            return 20.0f;
        }

        @Override
        public float absorption() {
            return 0.0f;
        }

        @Override
        public float hunger() {
            return hunger;
        }

        @Override
        public float saturation() {
            return 5.0f;
        }

        @Override
        public int airSupply() {
            return 300;
        }

        @Override
        public int maxAirSupply() {
            return 300;
        }

        @Override
        public float experienceLevel() {
            return 12.0f;
        }

        @Override
        public float experienceProgress() {
            return 0.4f;
        }

        @Override
        public boolean isAlive() {
            return health > 0.0f;
        }

        @Override
        public boolean isDead() {
            return health <= 0.0f;
        }

        @Override
        public boolean isCreative() {
            return false;
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isSprinting() {
            return sprinting;
        }

        @Override
        public boolean isSneaking() {
            return sneaking;
        }

        @Override
        public boolean isOnGround() {
            return true;
        }

        @Override
        public boolean isInWater() {
            return false;
        }

        @Override
        public boolean isInLava() {
            return false;
        }

        @Override
        public boolean isClimbing() {
            return false;
        }

        @Override
        public boolean isFallFlying() {
            return false;
        }

        @Override
        public boolean isRiding() {
            return false;
        }

        @Override
        public boolean isSleeping() {
            return false;
        }

        @Override
        public boolean isUsingItem() {
            return false;
        }

        @Override
        public boolean isBlocking() {
            return false;
        }

        @Override
        public boolean isHurt() {
            return false;
        }

        @Override
        public int hurtTime() {
            return 0;
        }

        @Override
        public float fallDistance() {
            return 0.0f;
        }

        @Override
        public float attackCooldown() {
            return 1.0f;
        }

        @Override
        public int ping() {
            return 42;
        }

        @Override
        public IInventory inventory() {
            return inventory;
        }

        @Override
        public List<IPotionEffect> potionEffects() {
            return Collections.emptyList();
        }

        @Override
        public void setSprinting(boolean value) {
            this.sprinting = value;
        }

        @Override
        public void setSneaking(boolean value) {
            this.sneaking = value;
        }

        @Override
        public void jump() {
            jumps++;
        }

        @Override
        public void swingHand() {
            swings++;
        }

        @Override
        public void attack(IEntity target) {
            attacks++;
        }

        @Override
        public void startUsingItem() {
        }

        @Override
        public void stopUsingItem() {
        }

        @Override
        public void dropSelectedItem(boolean all) {
        }

        @Override
        public void respawn() {
            health = 20.0f;
        }

        @Override
        public void sendChat(String message) {
            sentMessages.add(message);
        }

        @Override
        public void sendCommand(String command) {
            sentMessages.add(command);
        }

        @Override
        public void setVelocity(Vec3d velocity) {
            this.velocity = velocity;
        }

        @Override
        public void setPosition(Vec3d position) {
            this.position = position;
        }

        @Override
        public double horizontalSpeed() {
            return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        }

        @Override
        public double speedBps() {
            return horizontalSpeed() * 20.0;
        }

        @Override
        public int blockX() {
            return (int) Math.floor(position.x);
        }

        @Override
        public int blockY() {
            return (int) Math.floor(position.y);
        }

        @Override
        public int blockZ() {
            return (int) Math.floor(position.z);
        }

        @Override
        public String facing() {
            return "north";
        }

        @Override
        public float itemCooldown(String itemId) {
            return 0.0f;
        }

        @Override
        public int score() {
            return 7;
        }

        public void setHealth(float value) {
            this.health = value;
        }

        public int jumps() {
            return jumps;
        }

        public int swings() {
            return swings;
        }
    }

    // ================================================================ world

    public final class HeadlessWorld implements IWorld {

        @Override
        public boolean isLoaded() {
            return true;
        }

        @Override
        public String dimensionId() {
            return "minecraft:overworld";
        }

        @Override
        public String dimensionName() {
            return "Overworld";
        }

        @Override
        public String dimensionPath() {
            return "overworld";
        }

        @Override
        public List<IEntity> entities() {
            return entities;
        }

        @Override
        public List<IEntity> players() {
            List<IEntity> result = new ArrayList<IEntity>();
            for (IEntity entity : entities) {
                if (entity.isPlayer()) {
                    result.add(entity);
                }
            }
            return result;
        }

        @Override
        public List<IEntity> mobs() {
            List<IEntity> result = new ArrayList<IEntity>();
            for (IEntity entity : entities) {
                if (entity.isMob()) {
                    result.add(entity);
                }
            }
            return result;
        }

        @Override
        public List<IEntity> items() {
            List<IEntity> result = new ArrayList<IEntity>();
            for (IEntity entity : entities) {
                if (entity.isItem()) {
                    result.add(entity);
                }
            }
            return result;
        }

        @Override
        public IEntity entityById(int id) {
            for (IEntity entity : entities) {
                if (entity.id() == id) {
                    return entity;
                }
            }
            return null;
        }

        @Override
        public IBlockRef blockAt(int x, int y, int z) {
            String id;
            if (y <= 63) {
                id = y < 60 ? "minecraft:deepslate" : "minecraft:stone";
            } else if (y == 64 && (x + z) % 17 == 0) {
                id = "minecraft:diamond_ore";
            } else {
                id = "minecraft:air";
            }
            return new HeadlessBlock(id);
        }

        @Override
        public boolean isAir(int x, int y, int z) {
            return blockAt(x, y, z).isAir();
        }

        @Override
        public boolean isSolid(int x, int y, int z) {
            return blockAt(x, y, z).isSolid();
        }

        @Override
        public int lightLevel(int x, int y, int z) {
            return y > 63 ? 12 : 4;
        }

        @Override
        public int blockLight(int x, int y, int z) {
            return y > 63 ? 8 : 2;
        }

        @Override
        public int skyLight(int x, int y, int z) {
            return y > 63 ? 15 : 0;
        }

        @Override
        public boolean isChunkLoaded(int chunkX, int chunkZ) {
            return Math.abs(chunkX) < 8 && Math.abs(chunkZ) < 8;
        }

        @Override
        public void scanBlocks(Box3 region, BlockVisitor visitor) {
            for (int x = (int) region.minX; x <= (int) region.maxX; x++) {
                for (int y = (int) region.minY; y <= (int) region.maxY; y++) {
                    for (int z = (int) region.minZ; z <= (int) region.maxZ; z++) {
                        visitor.visit(x, y, z, blockAt(x, y, z).id());
                    }
                }
            }
        }

        @Override
        public int chunkRenderDistance() {
            return 12;
        }

        @Override
        public long timeOfDay() {
            return 6000L;
        }

        @Override
        public boolean isRaining() {
            return false;
        }

        @Override
        public boolean isThundering() {
            return false;
        }

        @Override
        public float rainLevel() {
            return 0.0f;
        }

        @Override
        public String biomeAt(int x, int z) {
            return "minecraft:plains";
        }

        @Override
        public int seaLevel() {
            return 63;
        }

        @Override
        public int minHeight() {
            return -64;
        }

        @Override
        public int maxHeight() {
            return 320;
        }

        @Override
        public int borderRadius() {
            return 29999984;
        }

        @Override
        public void reloadRenderer() {
            rendererReloaded = true;
        }

        @Override
        public String structureAt(int chunkX, int chunkZ) {
            return chunkX == 2 && chunkZ == 2 ? "Village" : null;
        }

        @Override
        public String blockEntityText(int x, int y, int z) {
            return null;
        }

        @Override
        public int blockEntitySize(int x, int y, int z) {
            return 0;
        }

        @Override
        public int[] mapPixels(IItemStack stack, int resolution) {
            return null;
        }

        @Override
        public int spawnX() {
            return 0;
        }

        @Override
        public int spawnY() {
            return 64;
        }

        @Override
        public int spawnZ() {
            return 0;
        }
    }

    // ================================================================ blocks

    public static final class HeadlessBlock implements IBlockRef {

        private final String id;

        HeadlessBlock(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public boolean isAir() {
            return "minecraft:air".equals(id);
        }

        @Override
        public boolean isSolid() {
            return !isAir();
        }

        @Override
        public boolean isOpaque() {
            return !isAir();
        }

        @Override
        public boolean isLiquid() {
            return id.contains("water") || id.contains("lava");
        }

        @Override
        public int lightLevel() {
            return 0;
        }

        @Override
        public int blockLight() {
            return 0;
        }

        @Override
        public float hardness() {
            return 1.5f;
        }

        @Override
        public boolean isOre() {
            return id.contains("_ore");
        }

        @Override
        public boolean isChest() {
            return id.contains("chest");
        }

        @Override
        public boolean isSpawner() {
            return id.contains("spawner");
        }

        @Override
        public boolean isLeaf() {
            return id.contains("leaves");
        }

        @Override
        public boolean isLog() {
            return id.contains("log");
        }

        @Override
        public boolean isSource() {
            return isLiquid();
        }
    }

    // ============================================================== entities

    public final class HeadlessEntity implements IEntity {

        private final int id;
        private final String name;
        private final String typeId;
        private final Vec3d position;
        private final boolean player;

        HeadlessEntity(int id, String name, String typeId, double x, double y, double z, boolean player) {
            this.id = id;
            this.name = name;
            this.typeId = typeId;
            this.position = new Vec3d(x, y, z);
            this.player = player;
        }

        @Override
        public int id() {
            return id;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String typeId() {
            return typeId;
        }

        @Override
        public Vec3d position() {
            return position;
        }

        @Override
        public Vec3d eyePosition() {
            return new Vec3d(position.x, position.y + 1.6, position.z);
        }

        @Override
        public Vec3d velocity() {
            return new Vec3d(0.0, 0.0, 0.0);
        }

        @Override
        public Box3 boundingBox() {
            return new Box3(position.x - 0.3, position.y, position.z - 0.3,
                    position.x + 0.3, position.y + 1.8, position.z + 0.3);
        }

        @Override
        public float yaw() {
            return 0.0f;
        }

        @Override
        public float pitch() {
            return 0.0f;
        }

        @Override
        public float health() {
            return 20.0f;
        }

        @Override
        public float maxHealth() {
            return 20.0f;
        }

        @Override
        public float distanceTo(IEntity other) {
            return (float) position.distanceTo(other.position());
        }

        @Override
        public double distanceToPlayer() {
            return position.distanceTo(player().position());
        }

        @Override
        public boolean isAlive() {
            return true;
        }

        @Override
        public boolean isPlayer() {
            return player;
        }

        @Override
        public boolean isLocalPlayer() {
            return false;
        }

        @Override
        public boolean isHostile() {
            return typeId.contains("zombie") || typeId.contains("skeleton");
        }

        @Override
        public boolean isPassive() {
            return !isHostile() && isMob();
        }

        @Override
        public boolean isNeutral() {
            return false;
        }

        @Override
        public boolean isItem() {
            return typeId.contains("item");
        }

        @Override
        public boolean isMob() {
            return !player && !isItem();
        }

        @Override
        public boolean isVehicle() {
            return false;
        }

        @Override
        public boolean isBaby() {
            return false;
        }

        @Override
        public boolean isInvisible() {
            return false;
        }

        @Override
        public boolean isSneaking() {
            return false;
        }

        @Override
        public boolean isOnGround() {
            return true;
        }

        @Override
        public boolean isInWater() {
            return false;
        }

        @Override
        public boolean isGlowing() {
            return false;
        }

        @Override
        public boolean isAttackable() {
            return true;
        }

        @Override
        public IItemStack heldItem() {
            return new HeadlessItem("minecraft:diamond_sword", 1);
        }

        @Override
        public float[] limbAngles() {
            return new float[]{0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        }

        @Override
        public float bodyYaw() {
            return 0.0f;
        }

        @Override
        public float height() {
            return 1.8f;
        }

        @Override
        public float width() {
            return 0.6f;
        }

        @Override
        public int airSupply() {
            return 300;
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public int ping() {
            return 30;
        }

        @Override
        public int blockX() {
            return (int) Math.floor(position.x);
        }

        @Override
        public int blockY() {
            return (int) Math.floor(position.y);
        }

        @Override
        public int blockZ() {
            return (int) Math.floor(position.z);
        }

        @Override
        public boolean isBlocking() {
            return false;
        }

        @Override
        public boolean isFallFlying() {
            return false;
        }
    }

    // ================================================================= items

    public static final class HeadlessItem implements IItemStack {

        private final String id;
        private final int count;

        public HeadlessItem(String id, int count) {
            this.id = id;
            this.count = count;
        }

        @Override
        public boolean isEmpty() {
            return id == null || id.isEmpty() || count <= 0;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String displayName() {
            return id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        }

        @Override
        public int count() {
            return count;
        }

        @Override
        public int maxStackSize() {
            return 64;
        }

        @Override
        public int damage() {
            return 0;
        }

        @Override
        public int maxDamage() {
            return 1561;
        }

        @Override
        public double durabilityRatio() {
            return 1.0;
        }

        @Override
        public boolean isDamageable() {
            return isTool() || isWeapon() || isArmor();
        }

        @Override
        public boolean isFood() {
            return id.contains("bread") || id.contains("apple") || id.contains("steak");
        }

        @Override
        public int foodValue() {
            return isFood() ? 5 : 0;
        }

        @Override
        public boolean isTool() {
            return id.contains("pickaxe") || id.contains("axe") || id.contains("shovel")
                    || id.contains("hoe") || id.contains("shears");
        }

        @Override
        public boolean isWeapon() {
            return id.contains("sword") || id.contains("axe") || id.contains("trident");
        }

        @Override
        public boolean isArmor() {
            return id.contains("helmet") || id.contains("chestplate") || id.contains("leggings")
                    || id.contains("boots");
        }

        @Override
        public boolean isBlockItem() {
            return id.contains("cobblestone") || id.contains("planks") || id.contains("torch");
        }

        @Override
        public boolean isEnchanted() {
            return false;
        }

        @Override
        public float attackDamage() {
            return isWeapon() ? 7.0f : 1.0f;
        }

        @Override
        public boolean isInTag(String tag) {
            return id.contains(tag);
        }

        @Override
        public boolean isTotem() {
            return id.contains("totem");
        }

        @Override
        public boolean isPotion() {
            return id.contains("potion");
        }

        @Override
        public boolean isArrow() {
            return id.contains("arrow");
        }

        @Override
        public boolean isBucket() {
            return id.contains("bucket");
        }
    }

    // ============================================================ inventory

    public final class HeadlessInventory implements IInventory {

        private final IItemStack[] main = new IItemStack[MAIN_SIZE];
        private int selected;
        private int clicks;

        HeadlessInventory() {
            main[0] = new HeadlessItem("minecraft:diamond_sword", 1);
            main[1] = new HeadlessItem("minecraft:diamond_pickaxe", 1);
            main[2] = new HeadlessItem("minecraft:cobblestone", 64);
            main[3] = new HeadlessItem("minecraft:bread", 12);
            main[4] = new HeadlessItem("minecraft:totem_of_undying", 1);
            main[5] = new HeadlessItem("minecraft:gold_ingot", 8);
            main[6] = new HeadlessItem("minecraft:bow", 1);
            main[7] = new HeadlessItem("minecraft:torch", 32);
            main[8] = new HeadlessItem("minecraft:shears", 1);
        }

        @Override
        public IItemStack stack(int slot) {
            if (slot >= 0 && slot < MAIN_SIZE) {
                return main[slot] == null ? new HeadlessItem("", 0) : main[slot];
            }
            return new HeadlessItem("", 0);
        }

        @Override
        public int mainSize() {
            return MAIN_SIZE;
        }

        @Override
        public List<IItemStack> mainStacks() {
            List<IItemStack> result = new ArrayList<IItemStack>();
            for (int i = 0; i < MAIN_SIZE; i++) {
                result.add(stack(i));
            }
            return result;
        }

        @Override
        public IItemStack heldItem() {
            return stack(selected);
        }

        @Override
        public IItemStack offhandItem() {
            return new HeadlessItem("minecraft:shield", 1);
        }

        @Override
        public IItemStack helmet() {
            return new HeadlessItem("minecraft:diamond_helmet", 1);
        }

        @Override
        public IItemStack chestplate() {
            return new HeadlessItem("minecraft:diamond_chestplate", 1);
        }

        @Override
        public IItemStack leggings() {
            return new HeadlessItem("minecraft:diamond_leggings", 1);
        }

        @Override
        public IItemStack boots() {
            return new HeadlessItem("minecraft:diamond_boots", 1);
        }

        @Override
        public int selectedSlot() {
            return selected;
        }

        @Override
        public void setSelectedSlot(int slot) {
            if (slot >= 0 && slot < 9) {
                this.selected = slot;
            }
        }

        @Override
        public int findHotbar(String text) {
            for (int i = 0; i < 9; i++) {
                if (main[i] != null && main[i].id().contains(text)) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public int findHotbar(Predicate<IItemStack> predicate) {
            for (int i = 0; i < 9; i++) {
                if (main[i] != null && predicate.test(main[i])) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public int find(String text) {
            for (int i = 0; i < MAIN_SIZE; i++) {
                if (main[i] != null && main[i].id().contains(text)) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public int find(Predicate<IItemStack> predicate) {
            for (int i = 0; i < MAIN_SIZE; i++) {
                if (main[i] != null && predicate.test(main[i])) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public int count(String itemId) {
            int total = 0;
            for (int i = 0; i < MAIN_SIZE; i++) {
                if (main[i] != null && main[i].id().contains(itemId)) {
                    total += main[i].count();
                }
            }
            return total;
        }

        @Override
        public boolean has(String itemId) {
            return count(itemId) > 0;
        }

        @Override
        public int firstEmptySlot() {
            for (int i = 0; i < MAIN_SIZE; i++) {
                if (main[i] == null) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public int freeSlots() {
            int free = 0;
            for (int i = 0; i < MAIN_SIZE; i++) {
                if (main[i] == null) {
                    free++;
                }
            }
            return free;
        }

        @Override
        public boolean isFull() {
            return freeSlots() == 0;
        }

        @Override
        public int armorValue() {
            return 20;
        }

        @Override
        public int bestWeaponSlot() {
            return 0;
        }

        @Override
        public int bestArmorSlotFor(int armorSlot) {
            return -1;
        }

        @Override
        public void swapWithHotbar(int inventorySlot, int hotbarSlot) {
            IItemStack tmp = main[hotbarSlot];
            main[hotbarSlot] = main[inventorySlot];
            main[inventorySlot] = tmp;
        }

        @Override
        public void drop(int slot, boolean all) {
            if (slot >= 0 && slot < MAIN_SIZE) {
                main[slot] = null;
            }
        }

        @Override
        public int syncId() {
            return 1;
        }

        @Override
        public int containerSlots() {
            return 27;
        }

        @Override
        public IItemStack containerStack(int slot) {
            return slot == 3 ? new HeadlessItem("minecraft:diamond", 5) : new HeadlessItem("", 0);
        }

        @Override
        public void clickSlot(int slot, int button, ClickType clickType) {
            clicks++;
        }

        public int clicks() {
            return clicks;
        }
    }

    // ============================================================== renderer

    public final class HeadlessRenderer implements IRenderer {

        @Override
        public void begin(float deltaMs) {
        }

        @Override
        public void end() {
        }

        @Override
        public void fillRect(double x, double y, double width, double height, int color) {
            drawCalls++;
        }

        @Override
        public void fillGradientRect(double x, double y, double width, double height, int startColor, int endColor, boolean vertical) {
            drawCalls++;
        }

        @Override
        public void fillGradientRound(double x, double y, double width, double height, double radius, int startColor, int endColor, boolean vertical) {
            drawCalls++;
        }

        @Override
        public void fillRoundRect(double x, double y, double width, double height, double radius, int color) {
            drawCalls++;
        }

        @Override
        public void outlineRect(double x, double y, double width, double height, double thickness, int color) {
            drawCalls++;
        }

        @Override
        public void outlineRoundRect(double x, double y, double width, double height, double radius, double thickness, int color) {
            drawCalls++;
        }

        @Override
        public void line(double x1, double y1, double x2, double y2, double thickness, int color) {
            drawCalls++;
        }

        @Override
        public void triangle(double x1, double y1, double x2, double y2, double x3, double y3, int color) {
            drawCalls++;
        }

        @Override
        public void circle(double x, double y, double radius, int color) {
            drawCalls++;
        }

        @Override
        public void circleOutline(double x, double y, double radius, double thickness, int color) {
            drawCalls++;
        }

        @Override
        public void shadow(double x, double y, double width, double height, double radius, double blur, int color) {
            drawCalls++;
        }

        @Override
        public void blurRegion(double x, double y, double width, double height, double strength) {
            drawCalls++;
        }

        @Override
        public void drawText(String text, double x, double y, int color, boolean shadow) {
            drawCalls++;
        }

        @Override
        public void drawText(String text, double x, double y, int color, boolean shadow, double scale) {
            drawCalls++;
        }

        @Override
        public void drawCenteredText(String text, double x, double y, int color, boolean shadow, double scale) {
            drawCalls++;
        }

        @Override
        public double textWidth(String text, double scale) {
            return text.length() * 6.0 * scale;
        }

        @Override
        public double fontHeight(double scale) {
            return 9.0 * scale;
        }

        @Override
        public void drawItem(IItemStack stack, double x, double y, double scale) {
            drawCalls++;
        }

        @Override
        public void drawItemWithCount(IItemStack stack, double x, double y, double scale) {
            drawCalls++;
        }

        @Override
        public void drawTooltip(String[] lines, double x, double y) {
            drawCalls++;
        }

        @Override
        public void pushScissor(double x, double y, double width, double height) {
        }

        @Override
        public void popScissor() {
        }

        @Override
        public ScreenPos project(Vec3d worldPosition) {
            return new ScreenPos(100.0, 100.0, 1.0, true);
        }

        @Override
        public void drawBox(Box3 box, int fillColor, int lineColor, boolean filled) {
            drawCalls++;
        }

        @Override
        public void drawCornerBox(Box3 box, int color, double cornerLength, double thickness) {
            drawCalls++;
        }

        @Override
        public void drawLine3D(Vec3d from, Vec3d to, int color, double thickness) {
            drawCalls++;
        }

        @Override
        public void drawTracer(Vec3d worldPosition, int color, double thickness) {
            drawCalls++;
        }

        @Override
        public void drawNametag(Vec3d worldPosition, String text, int color, double scale, boolean shadow, int backgroundColor) {
            drawCalls++;
        }

        @Override
        public void drawEntityBox(IEntity entity, int fillColor, int lineColor, boolean filled) {
            drawCalls++;
        }

        @Override
        public void drawSkeleton(IEntity entity, int color, double thickness) {
            drawCalls++;
        }

        @Override
        public void drawGlow(IEntity entity, int color, double scale) {
            drawCalls++;
        }

        @Override
        public void drawWorldQuad(Vec3d a, Vec3d b, Vec3d c, Vec3d d, int color) {
            drawCalls++;
        }

        @Override
        public void drawWorldPoint(Vec3d position, double size, int color) {
            drawCalls++;
        }
    }

    // ================================================================= input

    public final class HeadlessInput implements IInput {

        private int mouseX = 100;
        private int mouseY = 100;
        private double scroll;
        private final Set<Integer> down = new HashSet<Integer>();

        @Override
        public int mouseX() {
            return mouseX;
        }

        @Override
        public int mouseY() {
            return mouseY;
        }

        @Override
        public double scrollDelta() {
            return scroll;
        }

        @Override
        public boolean isKeyDown(int keyCode) {
            Boolean value = keys.get(Integer.valueOf(keyCode));
            return value != null && value.booleanValue();
        }

        @Override
        public boolean wasKeyPressed(int keyCode) {
            return isKeyDown(keyCode);
        }

        @Override
        public boolean isMouseDown(int button) {
            return false;
        }

        @Override
        public void setKeyState(int keyCode, boolean pressed) {
            keys.put(Integer.valueOf(keyCode), Boolean.valueOf(pressed));
        }

        @Override
        public void releaseAllKeys() {
            keys.clear();
        }

        @Override
        public void releaseMouse() {
            mouseGrabbed = false;
        }

        @Override
        public void grabMouse() {
            mouseGrabbed = true;
        }

        @Override
        public boolean isMouseGrabbed() {
            return mouseGrabbed;
        }

        public void setMouse(int x, int y) {
            this.mouseX = x;
            this.mouseY = y;
        }
    }

    // =============================================================== options

    public static final class HeadlessOptions implements IOptions {

        private double gamma = 1.0;
        private double fov = 70.0;
        private int renderDistance = 12;
        private float volume = 1.0f;
        private boolean viewBobbing = true;
        private boolean entityShadows = true;

        @Override
        public double gamma() {
            return gamma;
        }

        @Override
        public void setGamma(double value) {
            this.gamma = value;
        }

        @Override
        public double fov() {
            return fov;
        }

        @Override
        public void setFov(double value) {
            this.fov = value;
        }

        @Override
        public int renderDistance() {
            return renderDistance;
        }

        @Override
        public void setRenderDistance(int value) {
            this.renderDistance = value;
        }

        @Override
        public int guiScale() {
            return 2;
        }

        @Override
        public boolean smoothLighting() {
            return true;
        }

        @Override
        public boolean viewBobbing() {
            return viewBobbing;
        }

        @Override
        public void setViewBobbing(boolean value) {
            this.viewBobbing = value;
        }

        @Override
        public boolean entityShadows() {
            return entityShadows;
        }

        @Override
        public void setEntityShadows(boolean value) {
            this.entityShadows = value;
        }

        @Override
        public boolean cloudRenderMode() {
            return true;
        }

        @Override
        public int currentFps() {
            return 144;
        }

        @Override
        public boolean hideGui() {
            return false;
        }

        @Override
        public boolean debugEnabled() {
            return false;
        }

        @Override
        public float soundVolumeMaster() {
            return volume;
        }

        @Override
        public void setSoundVolumeMaster(float value) {
            this.volume = value;
        }
    }

    // ================================================================== chat

    public final class HeadlessChat implements IChat {

        private final List<String> history = new ArrayList<String>();

        @Override
        public void send(String text) {
            sentMessages.add(text);
            history.add(text);
        }

        @Override
        public void print(String text) {
            chatLines.add(text);
        }

        @Override
        public void print(String prefix, String text, int color) {
            chatLines.add(prefix + " " + text);
        }

        @Override
        public List<String> history() {
            return history;
        }

        @Override
        public void clearHistory() {
            history.clear();
        }

        @Override
        public void addSuggestion(String suggestion) {
        }
    }

    // =============================================================== screens

    public final class HeadlessScreens implements IScreenHost {

        private String vanillaMenu;
        private boolean titleScreen;

        @Override
        public void open(NexusScreen screen) {
            screen.init(window.guiWidth(), window.guiHeight());
            screenStack.add(screen);
            mouseGrabbed = false;
        }

        @Override
        public void close(NexusScreen screen) {
            screen.onClosed();
            screenStack.remove(screen);
            if (screenStack.isEmpty()) {
                restoreMouseToGame();
            }
        }

        @Override
        public void closeTop() {
            if (!screenStack.isEmpty()) {
                close(screenStack.get(screenStack.size() - 1));
            }
        }

        @Override
        public void closeAll() {
            while (!screenStack.isEmpty()) {
                close(screenStack.get(screenStack.size() - 1));
            }
        }

        @Override
        public NexusScreen current() {
            return screenStack.isEmpty() ? null : screenStack.get(screenStack.size() - 1);
        }

        @Override
        public boolean isOpen(NexusScreen screen) {
            return screenStack.contains(screen);
        }

        @Override
        public int stackSize() {
            return screenStack.size();
        }

        @Override
        public String currentVanillaScreen() {
            return null;
        }

        @Override
        public void restoreMouseToGame() {
            mouseRestored = true;
            mouseGrabbed = true;
            input.releaseAllKeys();
        }

        @Override
        public void openInventoryScreen() {
        }

        @Override
        public void fillSignText(String[] lines) {
            lastSignText = lines;
        }

        @Override
        public void clickEnchantment(int index) {
            lastEnchantmentClicked = index;
        }

        @Override
        public void openSingleplayerMenu() {
            vanillaMenu = "selectworld";
        }

        @Override
        public void openMultiplayerMenu() {
            vanillaMenu = "multiplayer";
        }

        @Override
        public void openVanillaOptions() {
            vanillaMenu = "options";
        }

        @Override
        public boolean isTitleScreen() {
            return titleScreen;
        }

        @Override
        public boolean isGuiVisible() {
            return !screenStack.isEmpty();
        }

        /** Makes the harness pretend the vanilla title screen is (in)visible. */
        public void setTitleScreen(boolean visible) {
            this.titleScreen = visible;
        }

        /** Last vanilla menu the Nexus menu opened (headless recorder). */
        public String vanillaMenu() {
            return vanillaMenu;
        }
    }

    // =========================================================== interaction

    public final class HeadlessInteraction implements IInteraction {

        @Override
        public boolean breakBlock(int x, int y, int z) {
            blocksBroken++;
            return true;
        }

        @Override
        public void stopBreaking() {
        }

        @Override
        public boolean placeBlock(int x, int y, int z, int faceIndex) {
            blocksPlaced++;
            return true;
        }

        @Override
        public boolean useBlock(int x, int y, int z, int faceIndex) {
            return true;
        }

        @Override
        public void attackEntity(IEntity entity) {
            attacks++;
        }

        @Override
        public void interactEntity(IEntity entity) {
        }

        @Override
        public void clickSlot(int syncId, int slot, int button, int actionType) {
        }

        @Override
        public void closeContainer() {
        }

        @Override
        public int openSyncId() {
            return 1;
        }

        @Override
        public void lookAtBlock(int x, int y, int z) {
        }

        @Override
        public Vec3d hitPosition() {
            int[] block = hitBlock();
            return block == null ? null
                    : new Vec3d(block[0] + 0.5, block[1] + 0.5, block[2] + 0.5);
        }

        @Override
        public int[] hitBlock() {
            return new int[]{player.blockX(), player.blockY() - 1, player.blockZ()};
        }

        @Override
        public IEntity hitEntity() {
            return null;
        }
    }

    // ============================================================= resources

    public final class HeadlessResources implements IResources {

        @Override
        public void playSound(String soundId, float volume, float pitch) {
        }

        @Override
        public void playSound(String soundId) {
        }

        @Override
        public void spawnParticle(String particle, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int count) {
        }

        @Override
        public void showBlockBreaking(int x, int y, int z, int stage) {
        }

        @Override
        public boolean hasSound(String soundId) {
            return false;
        }

        @Override
        public String[] availableFonts() {
            return new String[]{"Nexus Sans", "Minecraft", "Arial", "Consolas", "Verdana"};
        }

        @Override
        public void openFolder(File directory) {
        }

        @Override
        public File takeScreenshot(File directory) {
            return new File(directory, "screenshot.png");
        }

        @Override
        public List<String> resourcePacks() {
            return new ArrayList<String>();
        }

        @Override
        public File resourcePackFolder() {
            return new File("resourcepacks");
        }

        @Override
        public List<String> enchantments(IItemStack stack) {
            List<String> result = new ArrayList<String>();
            result.add("Sharpness V");
            return result;
        }
    }

    // ============================================================ connection

    public final class HeadlessConnection implements IConnection {

        private boolean connected = true;
        private String disconnectReason;

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public boolean isSingleplayer() {
            return true;
        }

        @Override
        public String serverAddress() {
            return "local";
        }

        @Override
        public String serverBrand() {
            return "nexus-headless";
        }

        @Override
        public int ping() {
            return 42;
        }

        @Override
        public int playerCount() {
            return 3;
        }

        @Override
        public int maxPlayers() {
            return 8;
        }

        @Override
        public int protocol() {
            return 769;
        }

        @Override
        public String motd() {
            return "Nexus headless test world";
        }

        @Override
        public long connectedSince() {
            return System.currentTimeMillis() - 60000L;
        }

        @Override
        public void disconnect(String reason) {
            this.disconnectReason = reason;
            this.connected = false;
        }

        public String disconnectReason() {
            return disconnectReason;
        }
    }

    // ================================================================ window

    public static final class HeadlessWindow implements IWindow {

        @Override
        public int guiWidth() {
            return 960;
        }

        @Override
        public int guiHeight() {
            return 540;
        }

        @Override
        public int frameWidth() {
            return 1920;
        }

        @Override
        public int frameHeight() {
            return 1080;
        }

        @Override
        public int guiScale() {
            return 2;
        }

        @Override
        public float deltaMs() {
            return 16.6f;
        }

        @Override
        public float deltaSeconds() {
            return 0.0166f;
        }

        @Override
        public boolean isFocused() {
            return true;
        }

        @Override
        public boolean isFullscreen() {
            return false;
        }
    }
}
