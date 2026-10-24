package nexus.adapter.v1214;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import nexus.api.IItemStack;
import nexus.api.IResources;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** {@link IResources} implementation for Minecraft 1.21.4. */
public final class Nexus1214Resources implements IResources {

    private final Nexus1214Client client;

    Nexus1214Resources(Nexus1214Client client) {
        this.client = client;
    }

    private Minecraft handle() {
        return client.handle();
    }

    @Override
    public void playSound(String soundId, float volume, float pitch) {
        net.minecraft.resources.ResourceLocation id =
                net.minecraft.resources.ResourceLocation.tryParse(soundId);
        if (id == null) {
            return;
        }
        SoundEvent event = BuiltInRegistries.SOUND_EVENT.getValue(id);
        if (event != null) {
            handle().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
        }
    }

    @Override
    public void playSound(String soundId) {
        playSound(soundId, 1.0f, 1.0f);
    }

    @Override
    public void spawnParticle(String particle, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int count) {
        if (handle().level == null) {
            return;
        }
        handle().level.addParticle(net.minecraft.core.particles.ParticleTypes.FLAME, x, y, z,
                velocityX, velocityY, velocityZ);
    }

    @Override
    public void showBlockBreaking(int x, int y, int z, int stage) {
        if (handle().levelRenderer != null) {
            handle().levelRenderer.destroyBlockProgress(nexus.api.IEntity.class.hashCode() + x + y + z,
                    new net.minecraft.core.BlockPos(x, y, z), stage);
        }
    }

    @Override
    public boolean hasSound(String soundId) {
        net.minecraft.resources.ResourceLocation id =
                net.minecraft.resources.ResourceLocation.tryParse(soundId);
        return id != null && BuiltInRegistries.SOUND_EVENT.get(id) != null;
    }

    @Override
    public String[] availableFonts() {
        return new String[]{"Nexus Sans", "Minecraft", "Arial", "Consolas", "Verdana"};
    }

    @Override
    public void openFolder(File directory) {
        try {
            java.awt.Desktop.getDesktop().open(directory);
        } catch (Throwable throwable) {
            nexus.util.Log.warn("Could not open folder: " + directory);
        }
    }

    @Override
    public File takeScreenshot(File directory) {
        net.minecraft.client.Screenshot.grab(handle().gameDirectory, "nexus",
                handle().getMainRenderTarget(), message -> {
                });
        return new File(handle().gameDirectory, "screenshots");
    }

    @Override
    public List<String> resourcePacks() {
        List<String> result = new ArrayList<String>();
        try {
            for (Object pack : handle().getResourcePackRepository().getAvailablePacks()) {
                result.add(pack.toString());
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    @Override
    public File resourcePackFolder() {
        return new File(handle().gameDirectory, "resourcepacks");
    }

    @Override
    public List<String> enchantments(IItemStack stack) {
        List<String> result = new ArrayList<String>();
        if (stack.isEmpty()) {
            return result;
        }
        net.minecraft.world.item.ItemStack vanilla =
                ((Nexus1214ItemStack) stack).handle();
        net.minecraft.world.item.enchantment.ItemEnchantments enchantments =
                net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantmentsForCrafting(vanilla);
        for (java.util.Map.Entry<net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>,
                Integer> entry : enchantments.entrySet()) {
            result.add(net.minecraft.world.item.enchantment.Enchantment
                    .getFullname(entry.getKey(), entry.getValue().intValue()).getString());
        }
        return result;
    }
}
