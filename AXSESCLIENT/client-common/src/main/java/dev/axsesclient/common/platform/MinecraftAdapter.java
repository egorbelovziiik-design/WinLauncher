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
}
