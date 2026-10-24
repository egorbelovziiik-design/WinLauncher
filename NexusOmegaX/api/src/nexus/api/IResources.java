package nexus.api;
import java.util.List;
import java.io.File;

/** Sounds, particles and cosmetic resources. */
public interface IResources {

    void playSound(String soundId, float volume, float pitch);

    void playSound(String soundId);

    /** Spawns a client side particle at a world position. */
    void spawnParticle(String particle, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int count);

    /** Client side block break progress effect used by visualizer modules. */
    void showBlockBreaking(int x, int y, int z, int stage);

    boolean hasSound(String soundId);

    /** Available font names provided by the client (Arial-like + MC fonts). */
    String[] availableFonts();

    /** Opens a folder in the operating system file manager. */
    void openFolder(File directory);

    /** Takes a screenshot and stores it in the given folder. */
    File takeScreenshot(File directory);

    /** Names of the installed resource packs. */
    List<String> resourcePacks();

    /** Folder that contains the resource packs. */
    File resourcePackFolder();

    /** Human readable enchantments of an item. */
    List<String> enchantments(IItemStack stack);
}
