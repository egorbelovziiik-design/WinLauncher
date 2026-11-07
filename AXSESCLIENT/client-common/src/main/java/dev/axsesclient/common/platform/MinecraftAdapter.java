package dev.axsesclient.common.platform;

import java.nio.file.Path;
import java.util.List;

public interface MinecraftAdapter {
    String getMinecraftVersion();
    String getJavaVersion();
    String getFabricLoaderVersion();
    String getFabricApiVersion();
    List<String> getLoadedMods();
    Path getGameDir();

    boolean isInWorld();
    boolean isSingleplayer();
    String getWorldOrServerName();
    String getDimensionId();

    String getPlayerName();
    double getPlayerX();
    double getPlayerY();
    double getPlayerZ();
    double getPrevPlayerX();
    double getPrevPlayerY();
    double getPrevPlayerZ();
    float getPlayerYaw();
    float getPlayerPitch();
    boolean isOnGround();
    boolean isSprinting();
    void setSprinting(boolean sprinting);
    boolean isSneaking();
    void setSneakingKeyHeld(boolean held);
    float getAttackCooldownProgress();
    int getPingMs();
    float getHealth();
    float getMaxHealth();

    List<ItemInfo> getArmorItems();
    ItemInfo getMainHandItem();
    ItemInfo getOffHandItem();
    List<ItemInfo> getInventoryItems();
    int getSelectedHotbarSlot();

    List<StatusEffectInfo> getActiveEffects();

    String getBiomeName();
    String getBiomePrecipitation();
    float getBiomeTemperature();
    int getBlockLight();
    int getSkyLight();
    long getDayTime();
    String getDifficultyName();
    int getLoadedChunksCount();
    boolean isRaining();
    boolean isThundering();

    int getFps();
    double getGamma();
    void setGamma(double gamma);
    double getBaseFov();
    void setBaseFov(double fov);
    boolean isViewBobbing();
    void setViewBobbing(boolean bobbing);
    double getMouseSensitivity();
    void setMouseSensitivity(double sensitivity);
    int getPerspective();
    void setPerspective(int perspective);
    boolean isChunkBordersVisible();
    boolean toggleChunkBorders();

    List<String> getEnabledResourcePacks();
    List<String> getAvailableResourcePacks();
    void setClipboard(String text);
    String getClipboard();

    int getScaledWidth();
    int getScaledHeight();
    double getGuiScale();

    boolean isClickGuiOpen();
    boolean isHudEditorOpen();
    void openClickGui();
    void openHudEditor();
    void openCustomMainMenu();
    void openVanillaSingleplayer();
    void openVanillaMultiplayer();
    void openVanillaOptions();
    void openVanillaResourcePacks();
    void closeScreen();
    void quitGame();

    default boolean isAnyScreenOpen() {
        return isClickGuiOpen() || isHudEditorOpen();
    }

    default TargetEntityInfo getCrosshairTarget() {
        return null;
    }

    default List<TargetEntityInfo> getNearbyTargets(
            double range,
            boolean includePlayers,
            boolean includeHostiles,
            boolean includePassives,
            boolean includeInvisible
    ) {
        return java.util.Collections.emptyList();
    }

    default boolean attackEntityById(int entityId, boolean swingHand) {
        return false;
    }

    default void setPlayerRotation(float yaw, float pitch) {
    }

    default boolean isUsingItem() {
        return false;
    }

    default float getFallDistance() {
        return 0.0f;
    }

    default boolean isInLiquid() {
        return false;
    }

    default double getPlayerEyeHeight() {
        return 1.62;
    }

    default List<TargetEntityInfo> getEspEntities(
            double range,
            boolean includePlayers,
            boolean includeHostiles,
            boolean includePassives,
            boolean includeItems,
            boolean includeInvisible
    ) {
        return getNearbyTargets(range, includePlayers, includeHostiles, includePassives, includeInvisible);
    }

    /**
     * Projects a 3D world point (wx, wy, wz) into 2D GUI screen space [screenX, screenY, cameraDepth].
     * Returns null or depth <= 0 when behind the camera.
     */
    default float[] projectWorldToScreen(double worldX, double worldY, double worldZ) {
        return null;
    }

    /**
     * True when the vanilla in-game ChatScreen ('T' / '/') is currently open.
     */
    default boolean isChatScreenOpen() {
        return false;
    }

    /**
     * Current mouse cursor X in GUI-scaled coordinates.
     */
    default double getScaledMouseX() {
        return -1.0;
    }

    /**
     * Current mouse cursor Y in GUI-scaled coordinates.
     */
    default double getScaledMouseY() {
        return -1.0;
    }

    /**
     * Silences/stops vanilla Minecraft background music when external Spotify or in-game music is active.
     */
    default void stopVanillaMusic() {
    }

    /**
     * Plays a subtle tactile UI click sound.
     */
    default void playUiClickSound(float pitch) {
    }

    /**
     * Sends a chat message or Baritone command (e.g. "#goto 100 64 100", "#mine diamond_ore", "#stop").
     */
    default void sendChatOrCommand(String text) {
    }

    /**
     * Drives movement keys for AXSES Smart Baritone Navigator when active in world.
     */
    default void setAutoWalkInput(boolean forward, boolean jump, boolean sprint) {
    }

    /**
     * Checks whether external Baritone / Automatone Fabric mod is loaded in mods/.
     */
    default boolean isExternalBaritoneLoaded() {
        List<String> loaded = getLoadedMods();
        if (loaded == null) {
            return false;
        }
        for (int i = 0; i < loaded.size(); i++) {
            String m = loaded.get(i).toLowerCase(java.util.Locale.ROOT);
            if (m.startsWith("baritone") || m.contains("baritone-api") || m.startsWith("automatone")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Selects a hotbar slot (0..8) on the client player.
     */
    default void selectHotbarSlot(int slot) {
    }

    /**
     * True when the player is on or near a block edge with air directly beneath their support footprint
     * (used by Ninja / Eagle FastBridge & SafeBridge).
     */
    default boolean isStandingOverEdge(double edgeInset) {
        return false;
    }

    /**
     * Attempts to place the currently held block against the targeted block face at the crosshair.
     */
    default boolean placeBlockAtCrosshair() {
        return false;
    }

    /**
     * Returns targeted block bounds & hit face normal:
     * [bx, by, bz, minX, minY, minZ, maxX, maxY, maxZ, normalX, normalY, normalZ], or null if none.
     */
    default double[] getTargetedBlockBox() {
        return null;
    }

    /**
     * Registry identifier of the currently targeted block (e.g. "minecraft:white_wool", "minecraft:end_stone"), or "".
     */
    default String getTargetedBlockId() {
        return "";
    }

    /**
     * Scans nearby blocks for BedWars beds & defence layers.
     * Each entry: [x, y, z, defenceTier (0=open,1=wool,2=wood,3=end_stone,4=obsidian), rgbColor].
     */
    default List<double[]> scanBedWarsBeds(int radius) {
        return java.util.Collections.emptyList();
    }

    /**
     * Returns incoming projectiles (Fireballs, Primed TNT, Arrows, Ender Pearls) within range.
     * Each entry: [x, y, z, vx, vy, vz, typeId (1=Fireball,2=TNT,3=Arrow,4=Pearl), entityId, distance].
     */
    default List<double[]> getIncomingProjectiles(double range) {
        return java.util.Collections.emptyList();
    }
}
