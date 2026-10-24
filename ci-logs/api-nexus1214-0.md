# nexus1214 API (Sat Oct 24 14:50:31 UTC 2026)
GRADLE_USER_HOME=/home/runner/.gradle  HOME=/home/runner
jar from gradle = /home/runner/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.21.4-loom.mappings.1_21_4.layered+hash.2198-v2/minecraft-merged-1.21.4-loom.mappings.1_21_4.layered+hash.2198-v2.jar
```
=========== net.minecraft.client.Options
Compiled from "Options.java"
public class net.minecraft.client.Options {
  public static final int RENDER_DISTANCE_TINY;
  public static final int RENDER_DISTANCE_SHORT;
  public static final int RENDER_DISTANCE_NORMAL;
  public static final int RENDER_DISTANCE_FAR;
  public static final int RENDER_DISTANCE_REALLY_FAR;
  public static final int RENDER_DISTANCE_EXTREME;
  public static final java.lang.String DEFAULT_SOUND_DEVICE;
  public static final int UNLIMITED_FRAMERATE_CUTOFF;
  public java.util.List<java.lang.String> resourcePacks;
  public java.util.List<java.lang.String> incompatibleResourcePacks;
  public java.lang.String fullscreenVideoModeString;
  public boolean hideServerAddress;
  public boolean advancedItemTooltips;
  public boolean pauseOnLostFocus;
  public int overrideWidth;
  public int overrideHeight;
  public boolean useNativeTransport;
  public net.minecraft.client.tutorial.TutorialSteps tutorialStep;
  public boolean joinedFirstServer;
  public int glDebugVerbosity;
  public boolean skipMultiplayerWarning;
  public final net.minecraft.client.KeyMapping keyUp;
  public final net.minecraft.client.KeyMapping keyLeft;
  public final net.minecraft.client.KeyMapping keyDown;
  public final net.minecraft.client.KeyMapping keyRight;
  public final net.minecraft.client.KeyMapping keyJump;
  public final net.minecraft.client.KeyMapping keyShift;
  public final net.minecraft.client.KeyMapping keySprint;
  public final net.minecraft.client.KeyMapping keyInventory;
  public final net.minecraft.client.KeyMapping keySwapOffhand;
  public final net.minecraft.client.KeyMapping keyDrop;
  public final net.minecraft.client.KeyMapping keyUse;
  public final net.minecraft.client.KeyMapping keyAttack;
  public final net.minecraft.client.KeyMapping keyPickItem;
  public final net.minecraft.client.KeyMapping keyChat;
  public final net.minecraft.client.KeyMapping keyPlayerList;
  public final net.minecraft.client.KeyMapping keyCommand;
  public final net.minecraft.client.KeyMapping keySocialInteractions;
  public final net.minecraft.client.KeyMapping keyScreenshot;
  public final net.minecraft.client.KeyMapping keyTogglePerspective;
  public final net.minecraft.client.KeyMapping keySmoothCamera;
  public final net.minecraft.client.KeyMapping keyFullscreen;
  public final net.minecraft.client.KeyMapping keySpectatorOutlines;
  public final net.minecraft.client.KeyMapping keyAdvancements;
  public final net.minecraft.client.KeyMapping[] keyHotbarSlots;
  public final net.minecraft.client.KeyMapping keySaveHotbarActivator;
  public final net.minecraft.client.KeyMapping keyLoadHotbarActivator;
  public final net.minecraft.client.KeyMapping[] keyMappings;
  public boolean hideGui;
  public java.lang.String lastMpIp;
  public boolean smoothCamera;
  public static final int AUTO_GUI_SCALE;
  public java.lang.String languageCode;
  public boolean onboardAccessibility;
  public boolean syncWrites;
  public net.minecraft.client.OptionInstance<java.lang.Boolean> darkMojangStudiosBackground();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> hideLightningFlash();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> hideSplashTexts();
  public net.minecraft.client.OptionInstance<java.lang.Double> sensitivity();
  public net.minecraft.client.OptionInstance<java.lang.Integer> renderDistance();
  public net.minecraft.client.OptionInstance<java.lang.Integer> simulationDistance();
  public net.minecraft.client.OptionInstance<java.lang.Double> entityDistanceScaling();
  public net.minecraft.client.OptionInstance<java.lang.Integer> framerateLimit();
  public net.minecraft.client.OptionInstance<net.minecraft.client.InactivityFpsLimit> inactivityFpsLimit();
  public net.minecraft.client.OptionInstance<net.minecraft.client.CloudStatus> cloudStatus();
  public net.minecraft.client.OptionInstance<net.minecraft.client.GraphicsStatus> graphicsMode();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> ambientOcclusion();
  public net.minecraft.client.OptionInstance<net.minecraft.client.PrioritizeChunkUpdates> prioritizeChunkUpdates();
  public void updateResourcePacks(net.minecraft.server.packs.repository.PackRepository);
  public net.minecraft.client.OptionInstance<net.minecraft.world.entity.player.ChatVisiblity> chatVisibility();
  public net.minecraft.client.OptionInstance<java.lang.Double> chatOpacity();
  public net.minecraft.client.OptionInstance<java.lang.Double> chatLineSpacing();
  public net.minecraft.client.OptionInstance<java.lang.Integer> menuBackgroundBlurriness();
  public int getMenuBackgroundBlurriness();
  public net.minecraft.client.OptionInstance<java.lang.Double> textBackgroundOpacity();
  public net.minecraft.client.OptionInstance<java.lang.Double> panoramaSpeed();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> highContrast();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> highContrastBlockOutline();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> narratorHotkey();
  public net.minecraft.client.OptionInstance<net.minecraft.world.entity.HumanoidArm> mainHand();
  public net.minecraft.client.OptionInstance<java.lang.Double> chatScale();
  public net.minecraft.client.OptionInstance<java.lang.Double> chatWidth();
  public net.minecraft.client.OptionInstance<java.lang.Double> chatHeightUnfocused();
  public net.minecraft.client.OptionInstance<java.lang.Double> chatHeightFocused();
  public net.minecraft.client.OptionInstance<java.lang.Double> chatDelay();
  public net.minecraft.client.OptionInstance<java.lang.Double> notificationDisplayTime();
  public net.minecraft.client.OptionInstance<java.lang.Integer> mipmapLevels();
  public net.minecraft.client.OptionInstance<net.minecraft.client.AttackIndicatorStatus> attackIndicator();
  public net.minecraft.client.OptionInstance<java.lang.Integer> biomeBlendRadius();
  public net.minecraft.client.OptionInstance<java.lang.Double> mouseWheelSensitivity();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> rawMouseInput();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> autoJump();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> rotateWithMinecart();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> operatorItemsTab();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> autoSuggestions();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> chatColors();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> chatLinks();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> chatLinksPrompt();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> enableVsync();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> entityShadows();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> forceUnicodeFont();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> japaneseGlyphVariants();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> invertYMouse();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> discreteMouseScroll();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> realmsNotifications();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> allowServerListing();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> reducedDebugInfo();
  public final float getSoundSourceVolume(net.minecraft.sounds.SoundSource);
  public final net.minecraft.client.OptionInstance<java.lang.Double> getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource);
  public net.minecraft.client.OptionInstance<java.lang.Boolean> showSubtitles();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> directionalAudio();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> backgroundForChatOnly();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> touchscreen();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> fullscreen();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> bobView();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> toggleCrouch();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> toggleSprint();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> hideMatchedNames();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> showAutosaveIndicator();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> onlyShowSecureChat();
  public net.minecraft.client.OptionInstance<java.lang.Integer> fov();
  public net.minecraft.client.OptionInstance<java.lang.Boolean> telemetryOptInExtra();
  public net.minecraft.client.OptionInstance<java.lang.Double> screenEffectScale();
  public net.minecraft.client.OptionInstance<java.lang.Double> fovEffectScale();
  public net.minecraft.client.OptionInstance<java.lang.Double> darknessEffectScale();
  public net.minecraft.client.OptionInstance<java.lang.Double> glintSpeed();
  public net.minecraft.client.OptionInstance<java.lang.Double> glintStrength();
  public net.minecraft.client.OptionInstance<java.lang.Double> damageTiltStrength();
  public net.minecraft.client.OptionInstance<java.lang.Double> gamma();
  public net.minecraft.client.OptionInstance<java.lang.Integer> guiScale();
  public net.minecraft.client.OptionInstance<net.minecraft.server.level.ParticleStatus> particles();
  public net.minecraft.client.OptionInstance<net.minecraft.client.NarratorStatus> narrator();
  public net.minecraft.client.OptionInstance<java.lang.String> soundDevice();
  public void onboardingAccessibilityFinished();
  public net.minecraft.client.Options(net.minecraft.client.Minecraft, java.io.File);
  public float getBackgroundOpacity(float);
  public int getBackgroundColor(float);
  public int getBackgroundColor(int);
  public void load();
  public void save();
  public net.minecraft.server.level.ClientInformation buildPlayerInformation();
  public void broadcastOptions();
  public void setModelPart(net.minecraft.world.entity.player.PlayerModelPart, boolean);
  public boolean isModelPartEnabled(net.minecraft.world.entity.player.PlayerModelPart);
  public net.minecraft.client.CloudStatus getCloudsType();
  public boolean useNativeTransport();
  public void loadSelectedResourcePacks(net.minecraft.server.packs.repository.PackRepository);
  public net.minecraft.client.CameraType getCameraType();
  public void setCameraType(net.minecraft.client.CameraType);
  public java.io.File getFile();
  public java.lang.String dumpOptionsForReport();
  public void setServerRenderDistance(int);
  public int getEffectiveRenderDistance();
  public static net.minecraft.network.chat.Component genericValueLabel(net.minecraft.network.chat.Component, net.minecraft.network.chat.Component);
  public static net.minecraft.network.chat.Component genericValueLabel(net.minecraft.network.chat.Component, int);
  public static net.minecraft.network.chat.Component genericValueOrOffLabel(net.minecraft.network.chat.Component, int);
}
=========== net.minecraft.client.OptionInstance
Compiled from "OptionInstance.java"
public final class net.minecraft.client.OptionInstance<T> {
  public static final net.minecraft.client.OptionInstance$Enum<java.lang.Boolean> BOOLEAN_VALUES;
  public static final net.minecraft.client.OptionInstance$CaptionBasedToString<java.lang.Boolean> BOOLEAN_TO_STRING;
  public static net.minecraft.client.OptionInstance<java.lang.Boolean> createBoolean(java.lang.String, boolean, java.util.function.Consumer<java.lang.Boolean>);
  public static net.minecraft.client.OptionInstance<java.lang.Boolean> createBoolean(java.lang.String, boolean);
  public static net.minecraft.client.OptionInstance<java.lang.Boolean> createBoolean(java.lang.String, net.minecraft.client.OptionInstance$TooltipSupplier<java.lang.Boolean>, boolean);
  public static net.minecraft.client.OptionInstance<java.lang.Boolean> createBoolean(java.lang.String, net.minecraft.client.OptionInstance$TooltipSupplier<java.lang.Boolean>, boolean, java.util.function.Consumer<java.lang.Boolean>);
  public static net.minecraft.client.OptionInstance<java.lang.Boolean> createBoolean(java.lang.String, net.minecraft.client.OptionInstance$TooltipSupplier<java.lang.Boolean>, net.minecraft.client.OptionInstance$CaptionBasedToString<java.lang.Boolean>, boolean, java.util.function.Consumer<java.lang.Boolean>);
  public net.minecraft.client.OptionInstance(java.lang.String, net.minecraft.client.OptionInstance$TooltipSupplier<T>, net.minecraft.client.OptionInstance$CaptionBasedToString<T>, net.minecraft.client.OptionInstance$ValueSet<T>, T, java.util.function.Consumer<T>);
  public net.minecraft.client.OptionInstance(java.lang.String, net.minecraft.client.OptionInstance$TooltipSupplier<T>, net.minecraft.client.OptionInstance$CaptionBasedToString<T>, net.minecraft.client.OptionInstance$ValueSet<T>, com.mojang.serialization.Codec<T>, T, java.util.function.Consumer<T>);
  public static <T> net.minecraft.client.OptionInstance$TooltipSupplier<T> noTooltip();
  public static <T> net.minecraft.client.OptionInstance$TooltipSupplier<T> cachedConstantTooltip(net.minecraft.network.chat.Component);
  public static <T extends net.minecraft.util.OptionEnum> net.minecraft.client.OptionInstance$CaptionBasedToString<T> forOptionEnum();
  public net.minecraft.client.gui.components.AbstractWidget createButton(net.minecraft.client.Options);
  public net.minecraft.client.gui.components.AbstractWidget createButton(net.minecraft.client.Options, int, int, int);
  public net.minecraft.client.gui.components.AbstractWidget createButton(net.minecraft.client.Options, int, int, int, java.util.function.Consumer<T>);
  public T get();
  public com.mojang.serialization.Codec<T> codec();
  public java.lang.String toString();
  public void set(T);
  public net.minecraft.client.OptionInstance$ValueSet<T> values();
}
=========== net.minecraft.client.MouseHandler
Compiled from "MouseHandler.java"
public class net.minecraft.client.MouseHandler {
  public net.minecraft.client.MouseHandler(net.minecraft.client.Minecraft);
  public void setup(long);
  public void handleAccumulatedMovement();
  public boolean isLeftPressed();
  public boolean isMiddlePressed();
  public boolean isRightPressed();
  public double xpos();
  public double ypos();
  public void setIgnoreFirstMove();
  public boolean isMouseGrabbed();
  public void grabMouse();
  public void releaseMouse();
  public void cursorEntered();
}
=========== net.minecraft.client.KeyboardHandler
Compiled from "KeyboardHandler.java"
public class net.minecraft.client.KeyboardHandler {
  public static final int DEBUG_CRASH_TIME;
  public net.minecraft.client.KeyboardHandler(net.minecraft.client.Minecraft);
  public void keyPress(long, int, int, int, int);
  public void setup(long);
  public java.lang.String getClipboard();
  public void setClipboard(java.lang.String);
  public void tick();
}
=========== net.minecraft.client.KeyMapping
Compiled from "KeyMapping.java"
public class net.minecraft.client.KeyMapping implements java.lang.Comparable<net.minecraft.client.KeyMapping> {
  public static final java.lang.String CATEGORY_MOVEMENT;
  public static final java.lang.String CATEGORY_MISC;
  public static final java.lang.String CATEGORY_MULTIPLAYER;
  public static final java.lang.String CATEGORY_GAMEPLAY;
  public static final java.lang.String CATEGORY_INVENTORY;
  public static final java.lang.String CATEGORY_INTERFACE;
  public static final java.lang.String CATEGORY_CREATIVE;
  public static void click(com.mojang.blaze3d.platform.InputConstants$Key);
  public static void set(com.mojang.blaze3d.platform.InputConstants$Key, boolean);
  public static void setAll();
  public static void releaseAll();
  public static void resetToggleKeys();
  public static void resetMapping();
  public net.minecraft.client.KeyMapping(java.lang.String, int, java.lang.String);
  public net.minecraft.client.KeyMapping(java.lang.String, com.mojang.blaze3d.platform.InputConstants$Type, int, java.lang.String);
  public boolean isDown();
  public java.lang.String getCategory();
  public boolean consumeClick();
  public java.lang.String getName();
  public com.mojang.blaze3d.platform.InputConstants$Key getDefaultKey();
  public void setKey(com.mojang.blaze3d.platform.InputConstants$Key);
  public int compareTo(net.minecraft.client.KeyMapping);
  public static java.util.function.Supplier<net.minecraft.network.chat.Component> createNameSupplier(java.lang.String);
  public boolean same(net.minecraft.client.KeyMapping);
  public boolean isUnbound();
  public boolean matches(int, int);
  public boolean matchesMouse(int);
  public net.minecraft.network.chat.Component getTranslatedKeyMessage();
  public boolean isDefault();
  public java.lang.String saveString();
  public void setDown(boolean);
  public static net.minecraft.client.KeyMapping get(java.lang.String);
  public int compareTo(java.lang.Object);
}
=========== net.minecraft.client.gui.components.ChatComponent
Compiled from "ChatComponent.java"
public class net.minecraft.client.gui.components.ChatComponent {
  public net.minecraft.client.gui.components.ChatComponent(net.minecraft.client.Minecraft);
  public void tick();
  public void render(net.minecraft.client.gui.GuiGraphics, int, int, int, boolean);
  public void clearMessages(boolean);
  public void addMessage(net.minecraft.network.chat.Component);
  public void addMessage(net.minecraft.network.chat.Component, net.minecraft.network.chat.MessageSignature, net.minecraft.client.GuiMessageTag);
  public void deleteMessage(net.minecraft.network.chat.MessageSignature);
  public void rescaleChat();
  public net.minecraft.util.ArrayListDeque<java.lang.String> getRecentChat();
  public void addRecentChat(java.lang.String);
  public void resetChatScroll();
  public void scrollChat(int);
  public boolean handleChatQueueClicked(double, double);
  public net.minecraft.network.chat.Style getClickedComponentStyleAt(double, double);
  public net.minecraft.client.GuiMessageTag getMessageTagAt(double, double);
  public boolean isChatFocused();
  public int getWidth();
  public int getHeight();
  public double getScale();
  public static int getWidth(double);
  public static int getHeight(double);
  public static double defaultUnfocusedPct();
  public int getLinesPerPage();
  public net.minecraft.client.gui.components.ChatComponent$State storeState();
  public void restoreState(net.minecraft.client.gui.components.ChatComponent$State);
}
=========== net.minecraft.world.level.block.entity.SignBlockEntity
Compiled from "SignBlockEntity.java"
public class net.minecraft.world.level.block.entity.SignBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity {
  public net.minecraft.world.level.block.entity.SignBlockEntity(net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState);
  public net.minecraft.world.level.block.entity.SignBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType, net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState);
  public boolean isFacingFrontText(net.minecraft.world.entity.player.Player);
  public net.minecraft.world.level.block.entity.SignText getText(boolean);
  public net.minecraft.world.level.block.entity.SignText getFrontText();
  public net.minecraft.world.level.block.entity.SignText getBackText();
  public int getTextLineHeight();
  public int getMaxTextLineWidth();
  public void updateSignText(net.minecraft.world.entity.player.Player, boolean, java.util.List<net.minecraft.server.network.FilteredText>);
  public boolean updateText(java.util.function.UnaryOperator<net.minecraft.world.level.block.entity.SignText>, boolean);
  public boolean setText(net.minecraft.world.level.block.entity.SignText, boolean);
  public boolean canExecuteClickCommands(boolean, net.minecraft.world.entity.player.Player);
  public boolean executeClickCommandsIfPresent(net.minecraft.world.entity.player.Player, net.minecraft.world.level.Level, net.minecraft.core.BlockPos, boolean);
  public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket();
  public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup$Provider);
  public void setAllowedPlayerEditor(java.util.UUID);
  public java.util.UUID getPlayerWhoMayEdit();
  public boolean isWaxed();
  public boolean setWaxed(boolean);
  public boolean playerIsTooFarAwayToEdit(java.util.UUID);
  public static void tick(net.minecraft.world.level.Level, net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState, net.minecraft.world.level.block.entity.SignBlockEntity);
  public net.minecraft.sounds.SoundEvent getSignInteractionFailedSoundEvent();
  public net.minecraft.network.protocol.Packet getUpdatePacket();
}
=========== net.minecraft.world.level.block.entity.SignText
Compiled from "SignText.java"
public class net.minecraft.world.level.block.entity.SignText {
  public static final com.mojang.serialization.Codec<net.minecraft.world.level.block.entity.SignText> DIRECT_CODEC;
  public static final int LINES;
  public net.minecraft.world.level.block.entity.SignText();
  public net.minecraft.world.level.block.entity.SignText(net.minecraft.network.chat.Component[], net.minecraft.network.chat.Component[], net.minecraft.world.item.DyeColor, boolean);
  public boolean hasGlowingText();
  public net.minecraft.world.level.block.entity.SignText setHasGlowingText(boolean);
  public net.minecraft.world.item.DyeColor getColor();
  public net.minecraft.world.level.block.entity.SignText setColor(net.minecraft.world.item.DyeColor);
  public net.minecraft.network.chat.Component getMessage(int, boolean);
  public net.minecraft.world.level.block.entity.SignText setMessage(int, net.minecraft.network.chat.Component);
  public net.minecraft.world.level.block.entity.SignText setMessage(int, net.minecraft.network.chat.Component, net.minecraft.network.chat.Component);
  public boolean hasMessage(net.minecraft.world.entity.player.Player);
  public net.minecraft.network.chat.Component[] getMessages(boolean);
  public net.minecraft.util.FormattedCharSequence[] getRenderMessages(boolean, java.util.function.Function<net.minecraft.network.chat.Component, net.minecraft.util.FormattedCharSequence>);
  public boolean hasAnyClickCommands(net.minecraft.world.entity.player.Player);
}
=========== net.minecraft.core.RegistryAccess
Compiled from "RegistryAccess.java"
public interface net.minecraft.core.RegistryAccess extends net.minecraft.core.HolderLookup$Provider {
  public static final org.slf4j.Logger LOGGER;
  public static final net.minecraft.core.RegistryAccess$Frozen EMPTY;
  public abstract <E> java.util.Optional<net.minecraft.core.Registry<E>> lookup(net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<? extends E>>);
  public default <E> net.minecraft.core.Registry<E> lookupOrThrow(net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<? extends E>>);
  public abstract java.util.stream.Stream<net.minecraft.core.RegistryAccess$RegistryEntry<?>> registries();
  public default java.util.stream.Stream<net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<?>>> listRegistryKeys();
  public static net.minecraft.core.RegistryAccess$Frozen fromRegistryOfRegistries(net.minecraft.core.Registry<? extends net.minecraft.core.Registry<?>>);
  public default net.minecraft.core.RegistryAccess$Frozen freeze();
  public default net.minecraft.core.HolderLookup$RegistryLookup lookupOrThrow(net.minecraft.resources.ResourceKey);
  public default net.minecraft.core.HolderGetter lookupOrThrow(net.minecraft.resources.ResourceKey);
}
=========== net.minecraft.core.Registry
Compiled from "Registry.java"
public interface net.minecraft.core.Registry<T> extends com.mojang.serialization.Keyable, net.minecraft.core.HolderLookup$RegistryLookup<T>, net.minecraft.core.IdMap<T> {
  public abstract net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<T>> key();
  public default com.mojang.serialization.Codec<T> byNameCodec();
  public default com.mojang.serialization.Codec<net.minecraft.core.Holder<T>> holderByNameCodec();
  public default <U> java.util.stream.Stream<U> keys(com.mojang.serialization.DynamicOps<U>);
  public abstract net.minecraft.resources.ResourceLocation getKey(T);
  public abstract java.util.Optional<net.minecraft.resources.ResourceKey<T>> getResourceKey(T);
  public abstract int getId(T);
  public abstract T getValue(net.minecraft.resources.ResourceKey<T>);
  public abstract T getValue(net.minecraft.resources.ResourceLocation);
  public abstract java.util.Optional<net.minecraft.core.RegistrationInfo> registrationInfo(net.minecraft.resources.ResourceKey<T>);
  public default java.util.Optional<T> getOptional(net.minecraft.resources.ResourceLocation);
  public default java.util.Optional<T> getOptional(net.minecraft.resources.ResourceKey<T>);
  public abstract java.util.Optional<net.minecraft.core.Holder$Reference<T>> getAny();
  public default T getValueOrThrow(net.minecraft.resources.ResourceKey<T>);
  public abstract java.util.Set<net.minecraft.resources.ResourceLocation> keySet();
  public abstract java.util.Set<java.util.Map$Entry<net.minecraft.resources.ResourceKey<T>, T>> entrySet();
  public abstract java.util.Set<net.minecraft.resources.ResourceKey<T>> registryKeySet();
  public abstract java.util.Optional<net.minecraft.core.Holder$Reference<T>> getRandom(net.minecraft.util.RandomSource);
  public default java.util.stream.Stream<T> stream();
  public abstract boolean containsKey(net.minecraft.resources.ResourceLocation);
  public abstract boolean containsKey(net.minecraft.resources.ResourceKey<T>);
  public static <T> T register(net.minecraft.core.Registry<? super T>, java.lang.String, T);
  public static <V, T extends V> T register(net.minecraft.core.Registry<V>, net.minecraft.resources.ResourceLocation, T);
  public static <V, T extends V> T register(net.minecraft.core.Registry<V>, net.minecraft.resources.ResourceKey<V>, T);
  public static <T> net.minecraft.core.Holder$Reference<T> registerForHolder(net.minecraft.core.Registry<T>, net.minecraft.resources.ResourceKey<T>, T);
  public static <T> net.minecraft.core.Holder$Reference<T> registerForHolder(net.minecraft.core.Registry<T>, net.minecraft.resources.ResourceLocation, T);
  public abstract net.minecraft.core.Registry<T> freeze();
  public abstract net.minecraft.core.Holder$Reference<T> createIntrusiveHolder(T);
  public abstract java.util.Optional<net.minecraft.core.Holder$Reference<T>> get(int);
  public abstract java.util.Optional<net.minecraft.core.Holder$Reference<T>> get(net.minecraft.resources.ResourceLocation);
  public abstract net.minecraft.core.Holder<T> wrapAsHolder(T);
  public default java.lang.Iterable<net.minecraft.core.Holder<T>> getTagOrEmpty(net.minecraft.tags.TagKey<T>);
  public default java.util.Optional<net.minecraft.core.Holder<T>> getRandomElementOf(net.minecraft.tags.TagKey<T>, net.minecraft.util.RandomSource);
  public abstract java.util.stream.Stream<net.minecraft.core.HolderSet$Named<T>> getTags();
  public default net.minecraft.core.IdMap<net.minecraft.core.Holder<T>> asHolderIdMap();
  public abstract net.minecraft.core.Registry$PendingTags<T> prepareTagReload(net.minecraft.tags.TagLoader$LoadResult<T>);
}
=========== net.minecraft.core.Holder
Compiled from "Holder.java"
public interface net.minecraft.core.Holder<T> {
  public abstract T value();
  public abstract boolean isBound();
  public abstract boolean is(net.minecraft.resources.ResourceLocation);
  public abstract boolean is(net.minecraft.resources.ResourceKey<T>);
  public abstract boolean is(java.util.function.Predicate<net.minecraft.resources.ResourceKey<T>>);
  public abstract boolean is(net.minecraft.tags.TagKey<T>);
  public abstract boolean is(net.minecraft.core.Holder<T>);
  public abstract java.util.stream.Stream<net.minecraft.tags.TagKey<T>> tags();
  public abstract com.mojang.datafixers.util.Either<net.minecraft.resources.ResourceKey<T>, T> unwrap();
  public abstract java.util.Optional<net.minecraft.resources.ResourceKey<T>> unwrapKey();
  public abstract net.minecraft.core.Holder$Kind kind();
  public abstract boolean canSerializeIn(net.minecraft.core.HolderOwner<T>);
  public default java.lang.String getRegisteredName();
  public static <T> net.minecraft.core.Holder<T> direct(T);
}
=========== net.minecraft.core.Holder
Compiled from "Holder.java"
public interface net.minecraft.core.Holder<T> {
  public abstract T value();
  public abstract boolean isBound();
  public abstract boolean is(net.minecraft.resources.ResourceLocation);
  public abstract boolean is(net.minecraft.resources.ResourceKey<T>);
  public abstract boolean is(java.util.function.Predicate<net.minecraft.resources.ResourceKey<T>>);
  public abstract boolean is(net.minecraft.tags.TagKey<T>);
  public abstract boolean is(net.minecraft.core.Holder<T>);
  public abstract java.util.stream.Stream<net.minecraft.tags.TagKey<T>> tags();
  public abstract com.mojang.datafixers.util.Either<net.minecraft.resources.ResourceKey<T>, T> unwrap();
  public abstract java.util.Optional<net.minecraft.resources.ResourceKey<T>> unwrapKey();
  public abstract net.minecraft.core.Holder$Kind kind();
  public abstract boolean canSerializeIn(net.minecraft.core.HolderOwner<T>);
  public default java.lang.String getRegisteredName();
  public static <T> net.minecraft.core.Holder<T> direct(T);
}
=========== net.minecraft.core.registries.BuiltInRegistries
Compiled from "BuiltInRegistries.java"
public class net.minecraft.core.registries.BuiltInRegistries {
  public static final net.minecraft.core.DefaultedRegistry<net.minecraft.world.level.gameevent.GameEvent> GAME_EVENT;
  public static final net.minecraft.core.Registry<net.minecraft.sounds.SoundEvent> SOUND_EVENT;
  public static final net.minecraft.core.DefaultedRegistry<net.minecraft.world.level.material.Fluid> FLUID;
  public static final net.minecraft.core.Registry<net.minecraft.world.effect.MobEffect> MOB_EFFECT;
  public static final net.minecraft.core.DefaultedRegistry<net.minecraft.world.level.block.Block> BLOCK;
  public static final net.minecraft.core.DefaultedRegistry<net.minecraft.world.entity.EntityType<?>> ENTITY_TYPE;
  public static final net.minecraft.core.DefaultedRegistry<net.minecraft.world.item.Item> ITEM;
  public static final net.minecraft.core.Registry<net.minecraft.world.item.alchemy.Potion> POTION;
  public static final net.minecraft.core.Registry<net.minecraft.core.particles.ParticleType<?>> PARTICLE_TYPE;
  public static final net.minecraft.core.Registry<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITY_TYPE;
  public static final net.minecraft.core.Registry<net.minecraft.resources.ResourceLocation> CUSTOM_STAT;
  public static final net.minecraft.core.DefaultedRegistry<net.minecraft.world.level.chunk.status.ChunkStatus> CHUNK_STATUS;
  public static final net.minecraft.core.Registry<net.minecraft.world.level.levelgen.structure.templatesystem.RuleTestType<?>> RULE_TEST;
  public static final net.minecraft.core.Registry<net.minecraft.world.level.levelgen.structure.templatesystem.rule.blockentity.RuleBlockEntityModifierType<?>> RULE_BLOCK_ENTITY_MODIFIER;
  public static final net.minecraft.core.Registry<net.minecraft.world.level.levelgen.structure.templatesystem.PosRuleTestType<?>> POS_RULE_TEST;
  public static final net.minecraft.core.Registry<net.minecraft.world.inventory.MenuType<?>> MENU;
  public static final net.minecraft.core.Registry<net.minecraft.world.item.crafting.RecipeType<?>> RECIPE_TYPE;
  public static final net.minecraft.core.Registry<net.minecraft.world.item.crafting.RecipeSerializer<?>> RECIPE_SERIALIZER;
  public static final net.minecraft.core.Registry<net.minecraft.world.entity.ai.attributes.Attribute> ATTRIBUTE;
  public static final net.minecraft.core.Registry<net.minecraft.world.level.gameevent.PositionSourceType<?>> POSITION_SOURCE_TYPE;
  public static final net.minecraft.core.Registry<net.minecraft.commands.synchronization.ArgumentTypeInfo<?, ?>> COMMAND_ARGUMENT_TYPE;
  public static final net.minecraft.core.Registry<net.minecraft.stats.StatType<?>> STAT_TYPE;
  public static final net.minecraft.core.DefaultedRegistry<net.minecraft.world.entity.npc.VillagerType> VILLAGER_TYPE;
  public static final net.minecraft.core.DefaultedRegistry<net.minecraft.world.entity.npc.VillagerProfession> VILLAGER_PROFESSION;
  public static final net.minecraft.core.Registry<net.minecraft.world.entity.ai.village.poi.PoiType> POINT_OF_INTEREST_TYPE;
  public static final net.minecraft.core.DefaultedRegistry<net.minecraft.world.entity.ai.memory.MemoryModuleType<?>> MEMORY_MODULE_TYPE;
  public static final net.minecraft.core.DefaultedRegistry<net.minecraft.world.entity.ai.sensing.SensorType<?>> SENSOR_TYPE;
  public static final net.minecraft.core.Registry<net.minecraft.world.entity.schedule.Schedule> SCHEDULE;
  public static final net.minecraft.core.Registry<net.minecraft.world.entity.schedule.Activity> ACTIVITY;
  public static final net.minecraft.core.Registry<net.minecraft.world.level.storage.loot.entries.LootPoolEntryType> LOOT_POOL_ENTRY_TYPE;
  public static final net.minecraft.core.Registry<net.minecraft.world.level.storage.loot.functions.LootItemFunctionType<?>> LOOT_FUNCTION_TYPE;
  public static final net.minecraft.core.Registry<net.minecraft.world.level.storage.loot.predicates.LootItemConditionType> LOOT_CONDITION_TYPE;
  public static final net.minecraft.core.Registry<net.minecraft.world.level.storage.loot.providers.number.LootNumberProviderType> LOOT_NUMBER_PROVIDER_TYPE;
  public static final net.minecraft.core.Registry<net.minecraft.world.level.storage.loot.providers.nbt.LootNbtProviderType> LOOT_NBT_PROVIDER_TYPE;
  public static final net.minecraft.core.Registry<net.minecraft.world.level.storage.loot.providers.score.LootScoreProviderType> LOOT_SCORE_PROVIDER_TYPE;
  public static final net.minecraft.core.Registry<net.minecraft.util.valueproviders.FloatProviderType<?>> FLOAT_PROVIDER_TYPE;
  public static final net.minecraft.core.Registry<net.minecraft.util.valueproviders.IntProviderType<?>> INT_PROVIDER_TYPE;
  public static final net.minecraft.core.Registry<net.minecraft.world.level.levelgen.heightproviders.HeightProviderType<?>> HEIGHT_PROVIDER_TYPE;
  public static final net.minecraft.core.Registry<net.minecraft.world.level.levelgen.blockpredicates.BlockPredicateType<?>> BLOCK_PREDICATE_TYPE;
  public static final net.minecraft.core.Registry<net.minecraft.world.level.levelgen.carver.WorldCarver<?>> CARVER;
  public static final net.minecraft.core.Registry<net.minecraft.world.level.levelgen.feature.Feature<?>> FEATURE;
  public static final net.minecraft.core.Registry<net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType<?>> STRUCTURE_PLACEMENT;
  public static final net.minecraft.core.Registry<net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType> STRUCTURE_PIECE;
  public static final net.minecraft.core.Registry<net.minecraft.world.level.levelgen.structure.StructureType<?>> STRUCTURE_TYPE;
  public static final net.minecraft.core.Registry<net.minecraft.world.level.levelgen.placement.PlacementModifierType<?>> PLACEMENT_MODIFIER_TYPE;
  public static final net.minecraft.core.Registry<net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProviderType<?>> BLOCKSTATE_PROVIDER_TYPE;
  public static final net.minecraft.core.Registry<net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType<?>> FOLIAGE_PLACER_TYPE;
  public static final net.minecraft.core.Registry<net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType<?>> TRUNK_PLACER_TYPE;
  public static final net.minecraft.core.Registry<net.minecraft.world.level.levelgen.feature.rootplacers.RootPlacerType<?>> ROOT_PLACER_TYPE;
  public static final net.minecraft.core.Registry<net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType<?>> TREE_DECORATOR_TYPE;
  public static final net.minecraft.core.Registry<net.minecraft.world.level.levelgen.feature.featuresize.FeatureSizeType<?>> FEATURE_SIZE_TYPE;
  public static final net.minecraft.core.Registry<com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.biome.BiomeSource>> BIOME_SOURCE;
  public static final net.minecraft.core.Registry<com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.chunk.ChunkGenerator>> CHUNK_GENERATOR;
  public static final net.minecraft.core.Registry<com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.levelgen.SurfaceRules$ConditionSource>> MATERIAL_CONDITION;
  public static final net.minecraft.core.Registry<com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.levelgen.SurfaceRules$RuleSource>> MATERIAL_RULE;
  public static final net.minecraft.core.Registry<com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.levelgen.DensityFunction>> DENSITY_FUNCTION_TYPE;
  public static final net.minecraft.core.Registry<com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.block.Block>> BLOCK_TYPE;
  public static final net.minecraft.core.Registry<net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType<?>> STRUCTURE_PROCESSOR;
  public static final net.minecraft.core.Registry<net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType<?>> STRUCTURE_POOL_ELEMENT;
  public static final net.minecraft.core.Registry<com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasBinding>> POOL_ALIAS_BINDING_TYPE;
  public static final net.minecraft.core.Registry<net.minecraft.world.entity.animal.CatVariant> CAT_VARIANT;
  public static final net.minecraft.core.Registry<net.minecraft.world.entity.animal.FrogVariant> FROG_VARIANT;
  public static final net.minecraft.core.Registry<net.minecraft.world.level.block.entity.DecoratedPotPattern> DECORATED_POT_PATTERN;
  public static final net.minecraft.core.Registry<net.minecraft.world.item.CreativeModeTab> CREATIVE_MODE_TAB;
  public static final net.minecraft.core.Registry<net.minecraft.advancements.CriterionTrigger<?>> TRIGGER_TYPES;
  public static final net.minecraft.core.Registry<net.minecraft.network.chat.numbers.NumberFormatType<?>> NUMBER_FORMAT_TYPE;
  public static final net.minecraft.core.Registry<net.minecraft.core.component.DataComponentType<?>> DATA_COMPONENT_TYPE;
  public static final net.minecraft.core.Registry<com.mojang.serialization.MapCodec<? extends net.minecraft.advancements.critereon.EntitySubPredicate>> ENTITY_SUB_PREDICATE_TYPE;
  public static final net.minecraft.core.Registry<net.minecraft.advancements.critereon.ItemSubPredicate$Type<?>> ITEM_SUB_PREDICATE_TYPE;
  public static final net.minecraft.core.Registry<net.minecraft.world.level.saveddata.maps.MapDecorationType> MAP_DECORATION_TYPE;
  public static final net.minecraft.core.Registry<net.minecraft.core.component.DataComponentType<?>> ENCHANTMENT_EFFECT_COMPONENT_TYPE;
  public static final net.minecraft.core.Registry<com.mojang.serialization.MapCodec<? extends net.minecraft.world.item.enchantment.LevelBasedValue>> ENCHANTMENT_LEVEL_BASED_VALUE_TYPE;
  public static final net.minecraft.core.Registry<com.mojang.serialization.MapCodec<? extends net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect>> ENCHANTMENT_ENTITY_EFFECT_TYPE;
  public static final net.minecraft.core.Registry<com.mojang.serialization.MapCodec<? extends net.minecraft.world.item.enchantment.effects.EnchantmentLocationBasedEffect>> ENCHANTMENT_LOCATION_BASED_EFFECT_TYPE;
  public static final net.minecraft.core.Registry<com.mojang.serialization.MapCodec<? extends net.minecraft.world.item.enchantment.effects.EnchantmentValueEffect>> ENCHANTMENT_VALUE_EFFECT_TYPE;
  public static final net.minecraft.core.Registry<com.mojang.serialization.MapCodec<? extends net.minecraft.world.item.enchantment.providers.EnchantmentProvider>> ENCHANTMENT_PROVIDER_TYPE;
  public static final net.minecraft.core.Registry<net.minecraft.world.item.consume_effects.ConsumeEffect$Type<?>> CONSUME_EFFECT_TYPE;
  public static final net.minecraft.core.Registry<net.minecraft.world.item.crafting.display.RecipeDisplay$Type<?>> RECIPE_DISPLAY;
  public static final net.minecraft.core.Registry<net.minecraft.world.item.crafting.display.SlotDisplay$Type<?>> SLOT_DISPLAY;
  public static final net.minecraft.core.Registry<net.minecraft.world.item.crafting.RecipeBookCategory> RECIPE_BOOK_CATEGORY;
  public static final net.minecraft.core.Registry<? extends net.minecraft.core.Registry<?>> REGISTRY;
  public net.minecraft.core.registries.BuiltInRegistries();
  public static void bootStrap();
  public static <T> net.minecraft.core.HolderGetter<T> acquireBootstrapRegistrationLookup(net.minecraft.core.Registry<T>);
}
=========== net.minecraft.world.item.enchantment.EnchantmentHelper
Compiled from "EnchantmentHelper.java"
public class net.minecraft.world.item.enchantment.EnchantmentHelper {
  public net.minecraft.world.item.enchantment.EnchantmentHelper();
  public static int getItemEnchantmentLevel(net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>, net.minecraft.world.item.ItemStack);
  public static net.minecraft.world.item.enchantment.ItemEnchantments updateEnchantments(net.minecraft.world.item.ItemStack, java.util.function.Consumer<net.minecraft.world.item.enchantment.ItemEnchantments$Mutable>);
  public static boolean canStoreEnchantments(net.minecraft.world.item.ItemStack);
  public static void setEnchantments(net.minecraft.world.item.ItemStack, net.minecraft.world.item.enchantment.ItemEnchantments);
  public static net.minecraft.world.item.enchantment.ItemEnchantments getEnchantmentsForCrafting(net.minecraft.world.item.ItemStack);
  public static boolean hasAnyEnchantments(net.minecraft.world.item.ItemStack);
  public static int processDurabilityChange(net.minecraft.server.level.ServerLevel, net.minecraft.world.item.ItemStack, int);
  public static int processAmmoUse(net.minecraft.server.level.ServerLevel, net.minecraft.world.item.ItemStack, net.minecraft.world.item.ItemStack, int);
  public static int processBlockExperience(net.minecraft.server.level.ServerLevel, net.minecraft.world.item.ItemStack, int);
  public static int processMobExperience(net.minecraft.server.level.ServerLevel, net.minecraft.world.entity.Entity, net.minecraft.world.entity.Entity, int);
  public static net.minecraft.world.item.ItemStack createBook(net.minecraft.world.item.enchantment.EnchantmentInstance);
  public static boolean isImmuneToDamage(net.minecraft.server.level.ServerLevel, net.minecraft.world.entity.LivingEntity, net.minecraft.world.damagesource.DamageSource);
  public static float getDamageProtection(net.minecraft.server.level.ServerLevel, net.minecraft.world.entity.LivingEntity, net.minecraft.world.damagesource.DamageSource);
  public static float modifyDamage(net.minecraft.server.level.ServerLevel, net.minecraft.world.item.ItemStack, net.minecraft.world.entity.Entity, net.minecraft.world.damagesource.DamageSource, float);
  public static float modifyFallBasedDamage(net.minecraft.server.level.ServerLevel, net.minecraft.world.item.ItemStack, net.minecraft.world.entity.Entity, net.minecraft.world.damagesource.DamageSource, float);
  public static float modifyArmorEffectiveness(net.minecraft.server.level.ServerLevel, net.minecraft.world.item.ItemStack, net.minecraft.world.entity.Entity, net.minecraft.world.damagesource.DamageSource, float);
  public static float modifyKnockback(net.minecraft.server.level.ServerLevel, net.minecraft.world.item.ItemStack, net.minecraft.world.entity.Entity, net.minecraft.world.damagesource.DamageSource, float);
  public static void doPostAttackEffects(net.minecraft.server.level.ServerLevel, net.minecraft.world.entity.Entity, net.minecraft.world.damagesource.DamageSource);
  public static void doPostAttackEffectsWithItemSource(net.minecraft.server.level.ServerLevel, net.minecraft.world.entity.Entity, net.minecraft.world.damagesource.DamageSource, net.minecraft.world.item.ItemStack);
  public static void doPostAttackEffectsWithItemSourceOnBreak(net.minecraft.server.level.ServerLevel, net.minecraft.world.entity.Entity, net.minecraft.world.damagesource.DamageSource, net.minecraft.world.item.ItemStack, java.util.function.Consumer<net.minecraft.world.item.Item>);
  public static void runLocationChangedEffects(net.minecraft.server.level.ServerLevel, net.minecraft.world.entity.LivingEntity);
  public static void runLo