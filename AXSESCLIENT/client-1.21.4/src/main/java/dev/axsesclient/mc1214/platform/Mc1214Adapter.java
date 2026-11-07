package dev.axsesclient.mc1214.platform;

import dev.axsesclient.common.platform.ItemInfo;
import dev.axsesclient.common.platform.MinecraftAdapter;
import dev.axsesclient.common.platform.StatusEffectInfo;
import dev.axsesclient.common.platform.TargetEntityInfo;
import dev.axsesclient.mc1214.gui.AxsesMainMenuScreen1214;
import dev.axsesclient.mc1214.gui.ClickGuiScreen1214;
import dev.axsesclient.mc1214.gui.HudEditorScreen1214;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class Mc1214Adapter implements MinecraftAdapter {
    private boolean chunkBordersVisible = false;
    private static volatile double overrideGamma = -1.0;

    public static double getOverrideGamma() {
        return overrideGamma;
    }

    @Override public String getMinecraftVersion() { return "1.21.4"; }
    @Override public String getJavaVersion() { return System.getProperty("java.version", "21"); }

    @Override
    public String getFabricLoaderVersion() {
        return FabricLoader.getInstance().getModContainer("fabricloader")
                .map(m -> m.getMetadata().getVersion().getFriendlyString())
                .orElse("0.20.1");
    }

    @Override
    public String getFabricApiVersion() {
        Optional<ModContainer> fapi = FabricLoader.getInstance().getModContainer("fabric-api");
        if (fapi.isEmpty()) fapi = FabricLoader.getInstance().getModContainer("fabric-api-base");
        return fapi.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("bundled");
    }

    @Override
    public List<String> getLoadedMods() {
        List<String> mods = new ArrayList<>();
        for (ModContainer c : FabricLoader.getInstance().getAllMods()) {
            mods.add(c.getMetadata().getId() + "@" + c.getMetadata().getVersion().getFriendlyString());
        }
        return mods;
    }

    @Override
    public Path getGameDir() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.gameDirectory != null ? mc.gameDirectory.toPath() : Paths.get(".");
    }

    @Override public boolean isInWorld() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.level != null && mc.player != null; }
    @Override public boolean isSingleplayer() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.hasSingleplayerServer(); }

    @Override
    public String getWorldOrServerName() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return "Main Menu";
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            return mc.getSingleplayerServer().getWorldData().getLevelName();
        }
        if (mc.getCurrentServer() != null) {
            return mc.getCurrentServer().name;
        }
        return "Multiplayer";
    }

    @Override
    public String getDimensionId() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level != null ? mc.level.dimension().location().toString() : "minecraft:overworld";
    }

    @Override public String getPlayerName() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.getUser() != null ? mc.getUser().getName() : "Player"; }
    @Override public double getPlayerX() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.player != null ? mc.player.getX() : 0.0; }
    @Override public double getPlayerY() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.player != null ? mc.player.getY() : 64.0; }
    @Override public double getPlayerZ() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.player != null ? mc.player.getZ() : 0.0; }
    @Override public double getPrevPlayerX() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.player != null ? mc.player.xo : 0.0; }
    @Override public double getPrevPlayerY() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.player != null ? mc.player.yo : 64.0; }
    @Override public double getPrevPlayerZ() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.player != null ? mc.player.zo : 0.0; }
    @Override public float getPlayerYaw() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.player != null ? mc.player.getYRot() : 0.0f; }
    @Override public float getPlayerPitch() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.player != null ? mc.player.getXRot() : 0.0f; }
    @Override public boolean isOnGround() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.player != null && mc.player.onGround(); }
    @Override public boolean isSprinting() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.player != null && mc.player.isSprinting(); }

    @Override
    public void setSprinting(boolean sprinting) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) mc.player.setSprinting(sprinting);
    }

    @Override public boolean isSneaking() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.player != null && mc.player.isShiftKeyDown(); }

    @Override
    public void setSneakingKeyHeld(boolean held) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.options != null && mc.options.keyShift != null) mc.options.keyShift.setDown(held);
    }

    @Override public float getAttackCooldownProgress() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.player != null ? mc.player.getAttackStrengthScale(0.0F) : 1.0f; }

    @Override
    public int getPingMs() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null && mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null) return info.getLatency();
        }
        return 0;
    }

    @Override public float getHealth() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.player != null ? mc.player.getHealth() : 20.0f; }
    @Override public float getMaxHealth() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.player != null ? mc.player.getMaxHealth() : 20.0f; }

    private static ItemInfo toItemInfo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemInfo.EMPTY;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return new ItemInfo(id, stack.getHoverName().getString(), stack.getCount(), stack.getDamageValue(), stack.getMaxDamage());
    }

    @Override
    public List<ItemInfo> getArmorItems() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return Collections.emptyList();
        List<ItemInfo> list = new ArrayList<>(4);
        list.add(toItemInfo(mc.player.getItemBySlot(EquipmentSlot.HEAD)));
        list.add(toItemInfo(mc.player.getItemBySlot(EquipmentSlot.CHEST)));
        list.add(toItemInfo(mc.player.getItemBySlot(EquipmentSlot.LEGS)));
        list.add(toItemInfo(mc.player.getItemBySlot(EquipmentSlot.FEET)));
        return list;
    }

    @Override public ItemInfo getMainHandItem() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.player != null ? toItemInfo(mc.player.getMainHandItem()) : ItemInfo.EMPTY; }
    @Override public ItemInfo getOffHandItem() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.player != null ? toItemInfo(mc.player.getOffhandItem()) : ItemInfo.EMPTY; }

    @Override
    public List<ItemInfo> getInventoryItems() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return Collections.emptyList();
        List<ItemInfo> list = new ArrayList<>(36);
        for (int i = 0; i < 36; i++) {
            list.add(toItemInfo(mc.player.getInventory().getItem(i)));
        }
        return list;
    }

    @Override public int getSelectedHotbarSlot() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.player != null ? mc.player.getInventory().selected : 0; }

    @Override
    public List<StatusEffectInfo> getActiveEffects() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return Collections.emptyList();
        List<StatusEffectInfo> list = new ArrayList<>();
        for (MobEffectInstance eff : mc.player.getActiveEffects()) {
            MobEffect mobEffect = eff.getEffect().value();
            String id = eff.getEffect().getRegisteredName();
            list.add(new StatusEffectInfo(id, mobEffect.getDisplayName().getString(), eff.getAmplifier(), eff.getDuration(), mobEffect.isBeneficial(), mobEffect.getColor()));
        }
        return list;
    }

    @Override
    public String getBiomeName() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return "Plains";
        Holder<Biome> b = mc.level.getBiome(mc.player.blockPosition());
        String reg = b.getRegisteredName();
        int colon = reg.indexOf(':');
        return formatName(colon >= 0 ? reg.substring(colon + 1) : reg);
    }

    @Override
    public String getBiomePrecipitation() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return "None";
        Holder<Biome> b = mc.level.getBiome(mc.player.blockPosition());
        return b.value().hasPrecipitation() ? "Rain/Snow" : "None";
    }

    @Override
    public float getBiomeTemperature() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return 0.8f;
        return mc.level.getBiome(mc.player.blockPosition()).value().getBaseTemperature();
    }

    @Override
    public int getBlockLight() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return 15;
        BlockPos pos = mc.player.blockPosition();
        return mc.level.getBrightness(LightLayer.BLOCK, pos);
    }

    @Override
    public int getSkyLight() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return 15;
        BlockPos pos = mc.player.blockPosition();
        return mc.level.getBrightness(LightLayer.SKY, pos);
    }

    @Override public long getDayTime() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.level != null ? mc.level.getDayTime() : 6000L; }
    @Override public String getDifficultyName() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.level != null ? formatName(mc.level.getDifficulty().name()) : "Normal"; }
    @Override public int getLoadedChunksCount() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.level != null ? mc.level.getChunkSource().getLoadedChunksCount() : 0; }
    @Override public boolean isRaining() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.level != null && mc.level.isRaining(); }
    @Override public boolean isThundering() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.level != null && mc.level.isThundering(); }
    @Override public int getFps() { Minecraft mc = Minecraft.getInstance(); return mc != null ? mc.getFps() : 60; }

    @Override
    public double getGamma() {
        if (overrideGamma > 1.0) return overrideGamma;
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.options != null ? mc.options.gamma().get() : 1.0;
    }

    @Override
    public void setGamma(double gamma) {
        Minecraft mc = Minecraft.getInstance();
        if (gamma > 1.0) {
            overrideGamma = gamma;
        } else {
            overrideGamma = -1.0;
            if (mc != null && mc.options != null) {
                mc.options.gamma().set(Math.max(0.0, Math.min(1.0, gamma)));
            }
        }
    }

    @Override public double getBaseFov() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.options != null ? mc.options.fov().get() : 70.0; }

    @Override
    public void setBaseFov(double fov) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.options != null) {
            mc.options.fov().set(Math.max(30, Math.min(110, (int) Math.round(fov))));
        }
    }

    @Override public boolean isViewBobbing() { Minecraft mc = Minecraft.getInstance(); return mc == null || mc.options == null || mc.options.bobView().get(); }
    @Override public void setViewBobbing(boolean bobbing) { Minecraft mc = Minecraft.getInstance(); if (mc != null && mc.options != null) mc.options.bobView().set(bobbing); }
    @Override public double getMouseSensitivity() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.options != null ? mc.options.sensitivity().get() : 0.5; }
    @Override public void setMouseSensitivity(double s) { Minecraft mc = Minecraft.getInstance(); if (mc != null && mc.options != null) mc.options.sensitivity().set(Math.max(0.0, Math.min(1.0, s))); }

    @Override
    public int getPerspective() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return 0;
        CameraType ct = mc.options.getCameraType();
        return ct == CameraType.FIRST_PERSON ? 0 : (ct == CameraType.THIRD_PERSON_BACK ? 1 : 2);
    }

    @Override
    public void setPerspective(int perspective) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        CameraType ct = perspective == 0 ? CameraType.FIRST_PERSON : (perspective == 1 ? CameraType.THIRD_PERSON_BACK : CameraType.THIRD_PERSON_FRONT);
        mc.options.setCameraType(ct);
    }

    @Override public boolean isChunkBordersVisible() { return chunkBordersVisible; }

    @Override
    public boolean toggleChunkBorders() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.debugRenderer != null) {
            chunkBordersVisible = mc.debugRenderer.switchRenderChunkborder();
        }
        return chunkBordersVisible;
    }

    @Override
    public List<String> getEnabledResourcePacks() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.getResourcePackRepository() != null ? new ArrayList<>(mc.getResourcePackRepository().getSelectedIds()) : Collections.emptyList();
    }

    @Override
    public List<String> getAvailableResourcePacks() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.getResourcePackRepository() != null ? new ArrayList<>(mc.getResourcePackRepository().getAvailableIds()) : Collections.emptyList();
    }

    @Override public void setClipboard(String text) { Minecraft mc = Minecraft.getInstance(); if (mc != null && mc.keyboardHandler != null && text != null) mc.keyboardHandler.setClipboard(text); }
    @Override public String getClipboard() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.keyboardHandler != null ? mc.keyboardHandler.getClipboard() : ""; }
    @Override public int getScaledWidth() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.getWindow() != null ? mc.getWindow().getGuiScaledWidth() : 854; }
    @Override public int getScaledHeight() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.getWindow() != null ? mc.getWindow().getGuiScaledHeight() : 480; }
    @Override public double getGuiScale() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.getWindow() != null ? mc.getWindow().getGuiScale() : 2.0; }

    @Override public boolean isClickGuiOpen() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.screen instanceof ClickGuiScreen1214; }
    @Override public boolean isHudEditorOpen() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.screen instanceof HudEditorScreen1214; }

    @Override public void openClickGui() { Minecraft mc = Minecraft.getInstance(); if (mc != null) mc.setScreen(new ClickGuiScreen1214()); }
    @Override public void openHudEditor() { Minecraft mc = Minecraft.getInstance(); if (mc != null) mc.setScreen(new HudEditorScreen1214()); }
    @Override public void openCustomMainMenu() { Minecraft mc = Minecraft.getInstance(); if (mc != null) mc.setScreen(new AxsesMainMenuScreen1214()); }
    @Override public void openVanillaSingleplayer() { Minecraft mc = Minecraft.getInstance(); if (mc != null) mc.setScreen(new SelectWorldScreen(mc.screen)); }
    @Override public void openVanillaMultiplayer() { Minecraft mc = Minecraft.getInstance(); if (mc != null) mc.setScreen(new JoinMultiplayerScreen(mc.screen)); }
    @Override public void openVanillaOptions() { Minecraft mc = Minecraft.getInstance(); if (mc != null) mc.setScreen(new OptionsScreen(mc.screen, mc.options)); }

    @Override
    public void openVanillaResourcePacks() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            Screen prev = mc.screen;
            mc.setScreen(new PackSelectionScreen(
                    mc.getResourcePackRepository(),
                    repo -> {
                        mc.options.updateResourcePacks(repo);
                        mc.setScreen(prev);
                    },
                    mc.getResourcePackDirectory(),
                    Component.literal("Resource Packs")
            ));
        }
    }

    @Override
    public void closeScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.setScreen(mc.level == null ? new AxsesMainMenuScreen1214() : null);
    }

    @Override public void quitGame() { Minecraft mc = Minecraft.getInstance(); if (mc != null) mc.stop(); }

    @Override
    public boolean isAnyScreenOpen() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.screen != null;
    }

    @Override
    public boolean isChatScreenOpen() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen;
    }

    @Override
    public double getScaledMouseX() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null || mc.mouseHandler == null) return -1.0;
        return mc.mouseHandler.xpos() * (double) mc.getWindow().getGuiScaledWidth() / Math.max(1.0, (double) mc.getWindow().getScreenWidth());
    }

    @Override
    public double getScaledMouseY() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null || mc.mouseHandler == null) return -1.0;
        return mc.mouseHandler.ypos() * (double) mc.getWindow().getGuiScaledHeight() / Math.max(1.0, (double) mc.getWindow().getScreenHeight());
    }

    @Override
    public void stopVanillaMusic() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getMusicManager() != null) {
            mc.getMusicManager().stopPlaying();
        }
    }

    @Override
    public void playUiClickSound(float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, pitch));
        }
    }

    @Override
    public void sendChatOrCommand(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null && mc.player.connection != null && text != null && !text.isEmpty()) {
            if (text.startsWith("/")) {
                mc.player.connection.sendCommand(text.substring(1));
            } else {
                mc.player.connection.sendChat(text);
            }
        }
    }

    @Override
    public void setAutoWalkInput(boolean forward, boolean jump, boolean sprint) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.options != null) {
            if (mc.options.keyUp != null) mc.options.keyUp.setDown(forward);
            if (mc.options.keyJump != null) mc.options.keyJump.setDown(jump);
            if (sprint && forward && mc.player != null) mc.player.setSprinting(true);
        }
    }

    @Override
    public TargetEntityInfo getCrosshairTarget() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || !(mc.crosshairPickEntity instanceof LivingEntity le)) {
            return null;
        }
        if (le == mc.player || !le.isAlive() || le.isSpectator() || le instanceof ArmorStand) {
            return null;
        }
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        return toTargetInfo(mc, le, pt);
    }

    @Override
    public List<TargetEntityInfo> getNearbyTargets(
            double range,
            boolean includePlayers,
            boolean includeHostiles,
            boolean includePassives,
            boolean includeInvisible
    ) {
        return getEspEntities(range, includePlayers, includeHostiles, includePassives, false, includeInvisible);
    }

    @Override
    public List<TargetEntityInfo> getEspEntities(
            double range,
            boolean includePlayers,
            boolean includeHostiles,
            boolean includePassives,
            boolean includeItems,
            boolean includeInvisible
    ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) {
            return Collections.emptyList();
        }
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        List<TargetEntityInfo> result = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player || !e.isAlive() || e.isSpectator() || e instanceof ArmorStand) {
                continue;
            }
            float dist = mc.player.distanceTo(e);
            if (dist > range) {
                continue;
            }
            if (includeItems && e instanceof ItemEntity ie) {
                ItemStack st = ie.getItem();
                if (st == null || st.isEmpty()) continue;
                double ix = ie.xo + (ie.getX() - ie.xo) * pt;
                double iy = ie.yo + (ie.getY() - ie.yo) * pt;
                double iz = ie.zo + (ie.getZ() - ie.zo) * pt;
                double dx = ix - ie.getX(), dy = iy - ie.getY(), dz = iz - ie.getZ();
                AABB b = ie.getBoundingBox();
                String label = st.getHoverName().getString() + (st.getCount() > 1 ? " x" + st.getCount() : "");
                result.add(new TargetEntityInfo(
                        ie.getId(), label, false, false, false, true,
                        1.0f, 1.0f, 0.0f, dist, 0, 0,
                        ix, iy, iz, iy + 0.25,
                        b.minX + dx, b.minY + dy, b.minZ + dz, b.maxX + dx, b.maxY + dy, b.maxZ + dz,
                        true, ""
                ));
                continue;
            }
            if (!(e instanceof LivingEntity le)) {
                continue;
            }
            if (!includeInvisible && le.isInvisible()) {
                continue;
            }
            boolean isPl = le instanceof Player;
            boolean isHost = !isPl && (le instanceof Enemy);
            boolean isPass = !isPl && !isHost;
            if ((isPl && !includePlayers) || (isHost && !includeHostiles) || (isPass && !includePassives)) {
                continue;
            }
            result.add(toTargetInfo(mc, le, pt));
        }
        return result;
    }

    @Override
    public boolean attackEntityById(int entityId, boolean swingHand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null || mc.gameMode == null) {
            return false;
        }
        Entity target = mc.level.getEntity(entityId);
        if (target == null || !target.isAlive() || target == mc.player) {
            return false;
        }
        mc.gameMode.attack(mc.player, target);
        if (swingHand) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
        return true;
    }

    @Override
    public void setPlayerRotation(float yaw, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
        }
    }

    @Override
    public boolean isUsingItem() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.player != null && mc.player.isUsingItem();
    }

    @Override
    public float getFallDistance() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.player != null ? (float) mc.player.fallDistance : 0.0f;
    }

    @Override
    public boolean isInLiquid() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.player != null && (mc.player.isInWater() || mc.player.isInLava());
    }

    @Override
    public double getPlayerEyeHeight() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.player != null ? (mc.player.getEyeY() - mc.player.getY()) : 1.62;
    }

    @Override
    public float[] projectWorldToScreen(double worldX, double worldY, double worldZ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer == null || mc.getWindow() == null) return null;
        Camera cam = mc.gameRenderer.getMainCamera();
        if (cam == null) return null;
        Vec3 cp = cam.getPosition();
        double dx = worldX - cp.x;
        double dy = worldY - cp.y;
        double dz = worldZ - cp.z;

        double yawRad = Math.toRadians(cam.getYRot());
        double pitchRad = Math.toRadians(cam.getXRot());
        double sinY = Math.sin(yawRad), cosY = Math.cos(yawRad);
        double sinP = Math.sin(pitchRad), cosP = Math.cos(pitchRad);

        double fx = -sinY * cosP, fy = -sinP, fz = cosY * cosP;
        double rx = -cosY, ry = 0.0, rz = -sinY;
        double ux = -sinY * sinP, uy = cosP, uz = cosY * sinP;

        double zCam = dx * fx + dy * fy + dz * fz;
        if (zCam <= 0.05) {
            return new float[] { 0.0f, 0.0f, (float) zCam };
        }
        double xCam = dx * rx + dy * ry + dz * rz;
        double yCam = dx * ux + dy * uy + dz * uz;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = Math.max(1, mc.getWindow().getGuiScaledHeight());
        double aspect = (double) sw / (double) sh;
        double fovDeg = getBaseFov();
        if (mc.player != null && mc.player.isSprinting()) fovDeg *= 1.08;
        double tanHalf = Math.tan(Math.toRadians(Math.max(20.0, Math.min(150.0, fovDeg)) * 0.5));

        double ndcX = xCam / (zCam * tanHalf * aspect);
        double ndcY = yCam / (zCam * tanHalf);
        float sx = (float) ((0.5 + 0.5 * ndcX) * sw);
        float sy = (float) ((0.5 - 0.5 * ndcY) * sh);
        return new float[] { sx, sy, (float) zCam };
    }

    private static TargetEntityInfo toTargetInfo(Minecraft mc, LivingEntity le, float pt) {
        boolean isPl = le instanceof Player;
        boolean isHost = !isPl && (le instanceof Enemy);
        boolean isPass = !isPl && !isHost;
        double ix = le.xo + (le.getX() - le.xo) * pt;
        double iy = le.yo + (le.getY() - le.yo) * pt;
        double iz = le.zo + (le.getZ() - le.zo) * pt;
        double dx = ix - le.getX(), dy = iy - le.getY(), dz = iz - le.getZ();
        AABB b = le.getBoundingBox();
        ItemStack held = le.getMainHandItem();
        String heldName = (held != null && !held.isEmpty()) ? held.getHoverName().getString() : "";
        if (le.isInvisible()) {
            heldName = heldName.isEmpty() ? "[INVIS]" : (heldName + " [INVIS]");
        }
        boolean los = hasClearLineOfSight(mc, ix, (iy + le.getEyeY() + dy) * 0.5, iz, le.getEyeY() + dy);
        return new TargetEntityInfo(
                le.getId(),
                le.getName().getString(),
                isPl,
                isHost,
                isPass,
                false,
                le.getHealth(),
                le.getMaxHealth(),
                le.getAbsorptionAmount(),
                mc.player.distanceTo(le),
                le.hurtTime,
                le.getArmorValue(),
                ix,
                iy,
                iz,
                le.getEyeY() + dy,
                b.minX + dx,
                b.minY + dy,
                b.minZ + dz,
                b.maxX + dx,
                b.maxY + dy,
                b.maxZ + dz,
                los,
                heldName
        );
    }

    private static boolean hasClearLineOfSight(Minecraft mc, double tx, double chestY, double tz, double eyeY) {
        if (mc.level == null || mc.player == null) return true;
        Vec3 from = mc.player.getEyePosition();
        HitResult eyeHit = mc.level.clip(new ClipContext(from, new Vec3(tx, eyeY, tz), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        if (eyeHit.getType() == HitResult.Type.MISS) return true;
        HitResult chestHit = mc.level.clip(new ClipContext(from, new Vec3(tx, chestY, tz), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return chestHit.getType() == HitResult.Type.MISS;
    }

    private static String formatName(String raw) {
        if (raw == null || raw.isEmpty()) return "Unknown";
        String[] parts = raw.toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    @Override
    public void selectHotbarSlot(int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null && slot >= 0 && slot <= 8) {
            mc.player.getInventory().selected = slot;
        }
    }

    @Override
    public boolean isStandingOverEdge(double edgeInset) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return false;
        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        int by = (int) Math.floor(py - 0.35);
        BlockPos center = new BlockPos((int) Math.floor(px), by, (int) Math.floor(pz));
        if (!mc.level.getBlockState(center).isAir()) {
            return false;
        }
        double d = Math.max(0.06, Math.min(0.32, edgeInset));
        return mc.level.getBlockState(new BlockPos((int) Math.floor(px + d), by, (int) Math.floor(pz + d))).isAir()
                || mc.level.getBlockState(new BlockPos((int) Math.floor(px - d), by, (int) Math.floor(pz + d))).isAir()
                || mc.level.getBlockState(new BlockPos((int) Math.floor(px + d), by, (int) Math.floor(pz - d))).isAir()
                || mc.level.getBlockState(new BlockPos((int) Math.floor(px - d), by, (int) Math.floor(pz - d))).isAir();
    }

    @Override
    public boolean placeBlockAtCrosshair() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null || mc.gameMode == null) return false;
        if (!(mc.hitResult instanceof BlockHitResult bhr)) return false;
        if (bhr.getType() != HitResult.Type.BLOCK || mc.level.getBlockState(bhr.getBlockPos()).isAir()) {
            return false;
        }
        InteractionResult res = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bhr);
        if (res != null && res != InteractionResult.PASS && res != InteractionResult.FAIL) {
            mc.player.swing(InteractionHand.MAIN_HAND);
            return true;
        }
        return false;
    }

    @Override
    public double[] getTargetedBlockBox() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || !(mc.hitResult instanceof BlockHitResult bhr)) return null;
        if (bhr.getType() != HitResult.Type.BLOCK) return null;
        BlockPos bp = bhr.getBlockPos();
        if (mc.level.getBlockState(bp).isAir()) return null;
        Direction dir = bhr.getDirection();
        double bx = bp.getX(), by = bp.getY(), bz = bp.getZ();
        return new double[] {
                bx, by, bz,
                bx, by, bz,
                bx + 1.0, by + 1.0, bz + 1.0,
                dir.getStepX(), dir.getStepY(), dir.getStepZ()
        };
    }

    @Override
    public String getTargetedBlockId() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || !(mc.hitResult instanceof BlockHitResult bhr)) return "";
        if (bhr.getType() != HitResult.Type.BLOCK) return "";
        BlockState st = mc.level.getBlockState(bhr.getBlockPos());
        return BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString();
    }

    @Override
    public List<double[]> scanBedWarsBeds(int radius) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return Collections.emptyList();
        int r = Math.max(8, Math.min(36, radius));
        int px = (int) Math.floor(mc.player.getX());
        int py = (int) Math.floor(mc.player.getY());
        int pz = (int) Math.floor(mc.player.getZ());
        List<double[]> out = new ArrayList<>();
        for (int dy = -8; dy <= 10; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos bp = new BlockPos(px + dx, py + dy, pz + dz);
                    BlockState st = mc.level.getBlockState(bp);
                    if (st.isAir()) continue;
                    String id = BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
                    if (id.endsWith("_bed")) {
                        String abovePath = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(bp.above()).getBlock()).getPath();
                        int tier = evalBedDefence(abovePath);
                        out.add(new double[] { bp.getX(), bp.getY(), bp.getZ(), tier, bedColorFromId(id) });
                        if (out.size() >= 12) return out;
                    }
                }
            }
        }
        return out;
    }

    @Override
    public List<double[]> getIncomingProjectiles(double range) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return Collections.emptyList();
        List<double[]> out = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player || !e.isAlive()) continue;
            float dist = mc.player.distanceTo(e);
            if (dist > range) continue;
            String p = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
            int type = 0;
            if (p.contains("fireball")) type = 1;
            else if ("tnt".equals(p)) type = 2;
            else if (p.contains("arrow")) type = 3;
            else if (p.contains("ender_pearl")) type = 4;
            if (type > 0) {
                Vec3 v = e.getDeltaMovement();
                out.add(new double[] { e.getX(), e.getY(), e.getZ(), v.x, v.y, v.z, type, e.getId(), dist });
            }
        }
        return out;
    }

    private static int evalBedDefence(String abovePath) {
        if (abovePath == null || abovePath.isEmpty() || "air".equals(abovePath)) return 0;
        if (abovePath.contains("obsidian")) return 4;
        if (abovePath.contains("end_stone")) return 3;
        if (abovePath.contains("planks") || abovePath.contains("wood") || abovePath.contains("log")) return 2;
        return 1;
    }

    private static int bedColorFromId(String id) {
        if (id.startsWith("red_")) return 0xFF3B5C;
        if (id.startsWith("blue_") || id.startsWith("light_blue_")) return 0x38BDF8;
        if (id.startsWith("green_") || id.startsWith("lime_")) return 0x10B981;
        if (id.startsWith("yellow_")) return 0xFACC15;
        if (id.startsWith("cyan_")) return 0x00F5D4;
        if (id.startsWith("pink_") || id.startsWith("magenta_") || id.startsWith("purple_")) return 0xEC4899;
        return 0xF8FAFF;
    }
}
