package dev.axsesclient.mc1165.platform;

import dev.axsesclient.common.platform.ItemInfo;
import dev.axsesclient.common.platform.MinecraftAdapter;
import dev.axsesclient.common.platform.StatusEffectInfo;
import dev.axsesclient.common.platform.TargetEntityInfo;
import dev.axsesclient.mc1165.gui.AxsesMainMenuScreen1165;
import dev.axsesclient.mc1165.gui.ClickGuiScreen1165;
import dev.axsesclient.mc1165.gui.HudEditorScreen1165;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;

public final class Mc1165Adapter implements MinecraftAdapter {
    private boolean chunkBordersVisible = false;

    @Override
    public String getMinecraftVersion() {
        return "1.16.5";
    }

    @Override
    public String getJavaVersion() {
        return System.getProperty("java.version", "1.8");
    }

    @Override
    public String getFabricLoaderVersion() {
        return FabricLoader.getInstance().getModContainer("fabricloader")
                .map(m -> m.getMetadata().getVersion().getFriendlyString())
                .orElse("0.20.1");
    }

    @Override
    public String getFabricApiVersion() {
        Optional<ModContainer> fapi = FabricLoader.getInstance().getModContainer("fabric");
        if (!fapi.isPresent()) {
            fapi = FabricLoader.getInstance().getModContainer("fabric-api-base");
        }
        return fapi.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("bundled");
    }

    @Override
    public List<String> getLoadedMods() {
        List<String> mods = new ArrayList<String>();
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

    @Override
    public boolean isInWorld() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level != null && mc.player != null;
    }

    @Override
    public boolean isSingleplayer() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.hasSingleplayerServer();
    }

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

    @Override
    public String getPlayerName() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.getUser() != null ? mc.getUser().getName() : "Player";
    }

    @Override public double getPlayerX() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.player != null ? mc.player.getX() : 0.0; }
    @Override public double getPlayerY() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.player != null ? mc.player.getY() : 64.0; }
    @Override public double getPlayerZ() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.player != null ? mc.player.getZ() : 0.0; }
    @Override public double getPrevPlayerX() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.player != null ? mc.player.xo : 0.0; }
    @Override public double getPrevPlayerY() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.player != null ? mc.player.yo : 64.0; }
    @Override public double getPrevPlayerZ() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.player != null ? mc.player.zo : 0.0; }
    @Override public float getPlayerYaw() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.player != null ? mc.player.yRot : 0.0f; }
    @Override public float getPlayerPitch() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.player != null ? mc.player.xRot : 0.0f; }
    @Override public boolean isOnGround() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.player != null && mc.player.isOnGround(); }
    @Override public boolean isSprinting() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.player != null && mc.player.isSprinting(); }

    @Override
    public void setSprinting(boolean sprinting) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            mc.player.setSprinting(sprinting);
        }
    }

    @Override
    public boolean isSneaking() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.player != null && mc.player.isShiftKeyDown();
    }

    @Override
    public void setSneakingKeyHeld(boolean held) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.options != null && mc.options.keyShift != null) {
            mc.options.keyShift.setDown(held);
        }
    }

    @Override
    public float getAttackCooldownProgress() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.player != null ? mc.player.getAttackStrengthScale(0.0F) : 1.0f;
    }

    @Override
    public int getPingMs() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null && mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null) {
                return info.getLatency();
            }
        }
        return 0;
    }

    @Override public float getHealth() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.player != null ? mc.player.getHealth() : 20.0f; }
    @Override public float getMaxHealth() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.player != null ? mc.player.getMaxHealth() : 20.0f; }

    private static ItemInfo toItemInfo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemInfo.EMPTY;
        ResourceLocation key = Registry.ITEM.getKey(stack.getItem());
        return new ItemInfo(
                key != null ? key.toString() : "minecraft:air",
                stack.getHoverName().getString(),
                stack.getCount(),
                stack.getDamageValue(),
                stack.getMaxDamage()
        );
    }

    @Override
    public List<ItemInfo> getArmorItems() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return Collections.emptyList();
        List<ItemInfo> list = new ArrayList<ItemInfo>(4);
        list.add(toItemInfo(mc.player.getItemBySlot(EquipmentSlot.HEAD)));
        list.add(toItemInfo(mc.player.getItemBySlot(EquipmentSlot.CHEST)));
        list.add(toItemInfo(mc.player.getItemBySlot(EquipmentSlot.LEGS)));
        list.add(toItemInfo(mc.player.getItemBySlot(EquipmentSlot.FEET)));
        return list;
    }

    @Override
    public ItemInfo getMainHandItem() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.player != null ? toItemInfo(mc.player.getMainHandItem()) : ItemInfo.EMPTY;
    }

    @Override
    public ItemInfo getOffHandItem() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.player != null ? toItemInfo(mc.player.getOffhandItem()) : ItemInfo.EMPTY;
    }

    @Override
    public List<ItemInfo> getInventoryItems() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return Collections.emptyList();
        List<ItemInfo> list = new ArrayList<ItemInfo>(36);
        for (int i = 0; i < 36; i++) {
            list.add(toItemInfo(mc.player.inventory.getItem(i)));
        }
        return list;
    }

    @Override
    public int getSelectedHotbarSlot() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.player != null ? mc.player.inventory.selected : 0;
    }

    @Override
    public List<StatusEffectInfo> getActiveEffects() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return Collections.emptyList();
        List<StatusEffectInfo> list = new ArrayList<StatusEffectInfo>();
        for (MobEffectInstance eff : mc.player.getActiveEffects()) {
            ResourceLocation key = Registry.MOB_EFFECT.getKey(eff.getEffect());
            list.add(new StatusEffectInfo(
                    key != null ? key.toString() : "effect",
                    eff.getEffect().getDisplayName().getString(),
                    eff.getAmplifier(),
                    eff.getDuration(),
                    eff.getEffect().isBeneficial(),
                    eff.getEffect().getColor()
            ));
        }
        return list;
    }

    @Override
    public String getBiomeName() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return "Plains";
        Biome b = mc.level.getBiome(mc.player.blockPosition());
        ResourceLocation key = mc.level.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY).getKey(b);
        return key != null ? formatName(key.getPath()) : "Plains";
    }

    @Override
    public String getBiomePrecipitation() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return "None";
        Biome b = mc.level.getBiome(mc.player.blockPosition());
        return formatName(b.getPrecipitation().name());
    }

    @Override
    public float getBiomeTemperature() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return 0.8f;
        return mc.level.getBiome(mc.player.blockPosition()).getBaseTemperature();
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

    @Override
    public long getDayTime() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level != null ? mc.level.getDayTime() : 6000L;
    }

    @Override
    public String getDifficultyName() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level != null ? formatName(mc.level.getDifficulty().name()) : "Normal";
    }

    @Override
    public int getLoadedChunksCount() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level != null ? mc.level.getChunkSource().getLoadedChunksCount() : 0;
    }

    @Override public boolean isRaining() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.level != null && mc.level.isRaining(); }
    @Override public boolean isThundering() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.level != null && mc.level.isThundering(); }

    @Override
    public int getFps() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.fpsString != null && !mc.fpsString.isEmpty()) {
            int sp = mc.fpsString.indexOf(' ');
            if (sp > 0) {
                try {
                    return Integer.parseInt(mc.fpsString.substring(0, sp));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 60;
    }

    @Override public double getGamma() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.options != null ? mc.options.gamma : 1.0; }
    @Override public void setGamma(double gamma) { Minecraft mc = Minecraft.getInstance(); if (mc != null && mc.options != null) mc.options.gamma = gamma; }
    @Override public double getBaseFov() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.options != null ? mc.options.fov : 70.0; }
    @Override public void setBaseFov(double fov) { Minecraft mc = Minecraft.getInstance(); if (mc != null && mc.options != null) mc.options.fov = Math.max(30.0, Math.min(120.0, fov)); }
    @Override public boolean isViewBobbing() { Minecraft mc = Minecraft.getInstance(); return mc == null || mc.options == null || mc.options.bobView; }
    @Override public void setViewBobbing(boolean bobbing) { Minecraft mc = Minecraft.getInstance(); if (mc != null && mc.options != null) mc.options.bobView = bobbing; }
    @Override public double getMouseSensitivity() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.options != null ? mc.options.sensitivity : 0.5; }
    @Override public void setMouseSensitivity(double s) { Minecraft mc = Minecraft.getInstance(); if (mc != null && mc.options != null) mc.options.sensitivity = Math.max(0.01, Math.min(1.0, s)); }

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
        return mc != null && mc.getResourcePackRepository() != null
                ? new ArrayList<String>(mc.getResourcePackRepository().getSelectedIds())
                : Collections.<String>emptyList();
    }

    @Override
    public List<String> getAvailableResourcePacks() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.getResourcePackRepository() != null
                ? new ArrayList<String>(mc.getResourcePackRepository().getAvailableIds())
                : Collections.<String>emptyList();
    }

    @Override
    public void setClipboard(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.keyboardHandler != null && text != null) {
            mc.keyboardHandler.setClipboard(text);
        }
    }

    @Override
    public String getClipboard() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.keyboardHandler != null ? mc.keyboardHandler.getClipboard() : "";
    }

    @Override public int getScaledWidth() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.getWindow() != null ? mc.getWindow().getGuiScaledWidth() : 854; }
    @Override public int getScaledHeight() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.getWindow() != null ? mc.getWindow().getGuiScaledHeight() : 480; }
    @Override public double getGuiScale() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.getWindow() != null ? mc.getWindow().getGuiScale() : 2.0; }

    @Override public boolean isClickGuiOpen() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.screen instanceof ClickGuiScreen1165; }
    @Override public boolean isHudEditorOpen() { Minecraft mc = Minecraft.getInstance(); return mc != null && mc.screen instanceof HudEditorScreen1165; }

    @Override
    public void openClickGui() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.setScreen(new ClickGuiScreen1165());
    }

    @Override
    public void openHudEditor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.setScreen(new HudEditorScreen1165());
    }

    @Override
    public void openCustomMainMenu() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.setScreen(new AxsesMainMenuScreen1165());
    }

    @Override
    public void openVanillaSingleplayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.setScreen(new SelectWorldScreen(mc.screen));
    }

    @Override
    public void openVanillaMultiplayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.setScreen(new JoinMultiplayerScreen(mc.screen));
    }

    @Override
    public void openVanillaOptions() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.setScreen(new OptionsScreen(mc.screen, mc.options));
    }

    @Override
    public void openVanillaResourcePacks() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreen(new PackSelectionScreen(
                    mc.screen,
                    mc.getResourcePackRepository(),
                    repo -> {
                        mc.options.loadSelectedResourcePacks(repo);
                        mc.reloadResourcePacks();
                    },
                    mc.getResourcePackDirectory(),
                    new TextComponent("Resource Packs")
            ));
        }
    }

    @Override
    public void closeScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreen(mc.level == null ? new AxsesMainMenuScreen1165() : null);
        }
    }

    @Override
    public void quitGame() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.stop();
    }

    @Override
    public boolean isAnyScreenOpen() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.screen != null;
    }

    @Override
    public TargetEntityInfo getCrosshairTarget() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || !(mc.crosshairPickEntity instanceof LivingEntity)) {
            return null;
        }
        LivingEntity le = (LivingEntity) mc.crosshairPickEntity;
        if (le == mc.player || !le.isAlive() || le.isSpectator() || le instanceof ArmorStand) {
            return null;
        }
        return toTargetInfo(mc, le, mc.getFrameTime());
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
        float pt = mc.getFrameTime();
        List<TargetEntityInfo> result = new ArrayList<TargetEntityInfo>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player || !e.isAlive() || e.isSpectator() || e instanceof ArmorStand) {
                continue;
            }
            float dist = mc.player.distanceTo(e);
            if (dist > range) {
                continue;
            }
            if (includeItems && e instanceof ItemEntity) {
                ItemEntity ie = (ItemEntity) e;
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
            if (!(e instanceof LivingEntity)) {
                continue;
            }
            if (!includeInvisible && e.isInvisible()) {
                continue;
            }
            LivingEntity le = (LivingEntity) e;
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
            mc.player.yRot = yaw;
            mc.player.xRot = pitch;
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
        boolean los = mc.player.canSee(le);
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

    private static String formatName(String raw) {
        if (raw == null || raw.isEmpty()) return "Unknown";
        String[] parts = raw.toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}
