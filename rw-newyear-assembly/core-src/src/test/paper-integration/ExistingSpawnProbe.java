package net.kinggrief.probe;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** A non-empty pre-existing folder without level.dat must never be treated as a fresh spawn. */
public final class ExistingSpawnProbe extends JavaPlugin {
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                World spawn = Bukkit.getWorld("spawn_world");
                boolean expectUntouched = getConfig().getBoolean("expect-untouched");
                if (expectUntouched && spawn != null) throw new AssertionError("incomplete world was loaded after installer refused it");
                if (!expectUntouched && spawn == null) throw new AssertionError("existing world not loaded");
                File dir = spawn != null ? spawn.getWorldFolder() : new File(Bukkit.getWorldContainer(), "spawn_world");
                String sentinel = new String(Files.readAllBytes(new File(dir, "kg-user-data.txt").toPath()), StandardCharsets.UTF_8);
                if (!"user data must remain".equals(sentinel)) throw new AssertionError("user file changed");
                if (new File(dir, ".spawn_v70_village_ok").exists()) throw new AssertionError("unknown world auto-rebuilt");
                if (expectUntouched && new File(dir, "level.dat").exists()) throw new AssertionError("incomplete world was modified");
                if (spawn != null && spawn.getBlockAt(0, 100, 52).getType() != Material.AIR) throw new AssertionError("new spawn built in unknown folder");
                getLogger().info("KG-PROBE PASS: non-empty unknown spawn directory preserved without level.dat");
            } catch (Throwable t) {
                getLogger().severe("KG-PROBE FAIL: " + t);
                t.printStackTrace();
            }
        }, 30L);
    }
}
