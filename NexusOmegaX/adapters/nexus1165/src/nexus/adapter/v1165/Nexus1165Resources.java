package nexus.adapter.v1165;

import net.minecraft.client.MinecraftClient;
import nexus.api.IItemStack;
import nexus.api.IResources;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** {@link IResources} implementation for Minecraft 1.16.5. */
public final class Nexus1165Resources implements IResources {

    private final Nexus1165Client client;

    Nexus1165Resources(Nexus1165Client client) {
        this.client = client;
    }

    private MinecraftClient handle() {
        return client.handle();
    }

    @Override
    public void playSound(String soundId, float volume, float pitch) {
        if (handle().player != null) {
            handle().player.playSound(
                    net.minecraft.util.registry.Registry.SOUND_EVENT.get(
                            new net.minecraft.util.Identifier(soundId)), volume, pitch);
        }
    }

    @Override
    public void playSound(String soundId) {
        playSound(soundId, 1.0f, 1.0f);
    }

    @Override
    public void spawnParticle(String particle, double x, double y, double z,
                              double velocityX, double velocityY, double velocityZ, int count) {
        if (handle().world == null) {
            return;
        }
        net.minecraft.particle.ParticleType<?> type =
                net.minecraft.util.registry.Registry.PARTICLE_TYPE.get(
                        new net.minecraft.util.Identifier(particle));
        if (!(type instanceof net.minecraft.particle.ParticleEffect)) {
            return;
        }
        net.minecraft.particle.ParticleEffect effect = (net.minecraft.particle.ParticleEffect) type;
        for (int i = 0; i < count; i++) {
            handle().world.addParticle(effect, x, y, z, velocityX, velocityY, velocityZ);
        }
    }

    @Override
    public void showBlockBreaking(int x, int y, int z, int stage) {
        if (handle().world != null && handle().player != null) {
            handle().world.setBlockBreakingInfo(handle().player.getEntityId(),
                    new net.minecraft.util.math.BlockPos(x, y, z), stage);
        }
    }

    @Override
    public boolean hasSound(String soundId) {
        try {
            return net.minecraft.util.registry.Registry.SOUND_EVENT.get(
                    new net.minecraft.util.Identifier(soundId)) != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String[] availableFonts() {
        return GraphicsEnvironmentFonts.list();
    }

    @Override
    public void openFolder(File directory) {
        try {
            if (directory.isDirectory()) {
                java.awt.Desktop.getDesktop().open(directory);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public File takeScreenshot(File directory) {
        try {
            net.minecraft.client.util.ScreenshotUtils.saveScreenshot(directory,
                    "nexus-" + System.currentTimeMillis(),
                    handle().getFramebuffer().textureWidth,
                    handle().getFramebuffer().textureHeight, handle().getFramebuffer(), null);
            return directory;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<String> resourcePacks() {
        return new ArrayList<String>(handle().getResourcePackManager().getEnabledNames());
    }

    @Override
    public File resourcePackFolder() {
        return new File(handle().runDirectory, "resourcepacks");
    }

    @Override
    public List<String> enchantments(IItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<String>();
        net.minecraft.item.ItemStack nativeStack = ((Nexus1165ItemStack) stack).handle();
        net.minecraft.enchantment.EnchantmentHelper.get(nativeStack).forEach(
                (net.minecraft.enchantment.Enchantment enchantment, Integer level) ->
                        result.add(enchantment.getName(level).getString()));
        return result;
    }

    /** Keeps the AWT import out of the main class (it is unavailable headless). */
    private static final class GraphicsEnvironmentFonts {
        static String[] list() {
            try {
                return java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getAvailableFontFamilyNames();
            } catch (Throwable e) {
                return new String[]{"Minecraft", "Unicode"};
            }
        }
    }
}
