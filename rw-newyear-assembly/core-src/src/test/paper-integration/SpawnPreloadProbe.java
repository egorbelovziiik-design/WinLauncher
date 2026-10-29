package net.kinggrief.core.test;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

/** Loads an existing world before Core and leaves an unsaved block for the backup test. */
public final class SpawnPreloadProbe extends JavaPlugin {
    @Override public void onEnable() {
        World world = Bukkit.createWorld(new WorldCreator("spawn_world"));
        if (world == null) throw new IllegalStateException("Could not preload old spawn");
        world.getBlockAt(51, 101, 50).setType(Material.EMERALD_BLOCK, false);
        getLogger().info("KG-PRELOAD: old spawn loaded and changed in memory");
    }
}
