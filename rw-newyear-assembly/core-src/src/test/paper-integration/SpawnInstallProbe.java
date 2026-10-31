package net.kinggrief.core.test;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/** Test-only probe for one-time world installation, backup and repeat startup. */
public final class SpawnInstallProbe extends JavaPlugin {
    private final int x = 50, y = 101, z = 50;

    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            @Override public void run() {
                try {
                    String mode = getConfig().getString("mode", "");
                    check(mode);
                    getLogger().info("KG-SPAWN-INSTALL PASS: " + mode);
                } catch (Throwable e) {
                    getLogger().severe("KG-SPAWN-INSTALL FAIL: " + e);
                    e.printStackTrace();
                } finally {
                    Bukkit.shutdown();
                }
            }
        }, 20L);
    }

    private void check(String mode) throws Exception {
        World spawn = Bukkit.getWorld("spawn_world");
        Path coreData = Bukkit.getWorldContainer().toPath().resolve("plugins/KingGriefCore");
        Path done = coreData.resolve(".spawn-v70-template-installed");
        Path oldUid = getDataFolder().toPath().resolve("old-uid.txt");
        if (spawn == null) throw new AssertionError("spawn world is not loaded");
        Path world = spawn.getWorldFolder().toPath();
        if ("seed".equals(mode)) {
            if (Files.exists(world.resolve(".kinggrief_prebuilt_v70"))) throw new AssertionError("seed should not be prebuilt");
            writeChest(spawn, Material.DIAMOND);
            assertChest(spawn, Material.DIAMOND);
            Files.write(world.resolve("user-data.txt"), "old world data\n".getBytes(StandardCharsets.UTF_8));
            Files.createDirectories(getDataFolder().toPath());
            Files.write(oldUid, spawn.getUID().toString().getBytes(StandardCharsets.UTF_8));
            return;
        }
        List<Path> backups = backups();
        if (backups.size() != 1) throw new AssertionError("expected exactly one old-world backup, got " + backups.size());
        boolean prebuilt = Files.isRegularFile(world.resolve(".kinggrief_prebuilt_v70"));
        if (("installed".equals(mode) || "repeat".equals(mode)) && !prebuilt)
            throw new AssertionError("prebuilt marker missing");
        if ("installed".equals(mode)) {
            if (!Files.isRegularFile(done)) throw new AssertionError("one-time marker missing");
            if (!new String(Files.readAllBytes(oldUid), StandardCharsets.UTF_8).trim().equals(spawn.getUID().toString()))
                throw new AssertionError("upgrade did not retain the original world UUID");
            if (spawn.getBlockAt(x, y, z).getType() == Material.CHEST)
                throw new AssertionError("old chest was not replaced by prepared spawn");
            Path backup = backups.get(0);
            if (!"old world data\n".equals(new String(Files.readAllBytes(backup.resolve("user-data.txt")), StandardCharsets.UTF_8)))
                throw new AssertionError("user file missing from backup");
            checkBackupContents(backup);
            writeChest(spawn, Material.GOLD_INGOT);
            assertChest(spawn, Material.GOLD_INGOT);
            Files.write(world.resolve("after-install.txt"), "keep this edit\n".getBytes(StandardCharsets.UTF_8));
            return;
        }
        if ("repeat".equals(mode)) {
            if (!"keep this edit\n".equals(new String(Files.readAllBytes(world.resolve("after-install.txt")), StandardCharsets.UTF_8)))
                throw new AssertionError("repeat startup replaced user data");
            assertChest(spawn, Material.GOLD_INGOT);
            if (!Files.isRegularFile(done)) throw new AssertionError("done marker lost");
            return;
        }
        if ("restored".equals(mode)) {
            if (prebuilt) throw new AssertionError("restored old world was replaced");
            if (!"old world data\n".equals(new String(Files.readAllBytes(world.resolve("user-data.txt")), StandardCharsets.UTF_8)))
                throw new AssertionError("old world data not preserved");
            assertChest(spawn, Material.DIAMOND);
            if (!Files.isRegularFile(done)) throw new AssertionError("done marker lost");
            return;
        }
        throw new AssertionError("unknown mode: " + mode);
    }

    private void writeChest(World world, Material contents) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.CHEST, false);
        Chest chest = (Chest) block.getState();
        chest.getBlockInventory().setItem(0, new ItemStack(contents, 3));
    }

    private void assertChest(World world, Material contents) {
        Block block = world.getBlockAt(x, y, z);
        if (block.getType() != Material.CHEST) throw new AssertionError("expected a chest at probe location");
        ItemStack item = ((Chest) block.getState()).getBlockInventory().getItem(0);
        if (item == null || item.getType() != contents || item.getAmount() != 3)
            throw new AssertionError("chest contents were not preserved");
    }

    private List<Path> backups() throws IOException {
        List<Path> result = new ArrayList<Path>();
        try (java.nio.file.DirectoryStream<Path> entries = Files.newDirectoryStream(Bukkit.getWorldContainer().toPath(), "spawn_world_backup_*")) {
            for (Path entry : entries) if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) result.add(entry);
        }
        return result;
    }

    private void checkBackupContents(final Path backup) throws Exception {
        final Path copy = Files.createTempDirectory(Bukkit.getWorldContainer().toPath(), "kg_spawn_backup_test_");
        Files.walkFileTree(backup, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(copy.resolve(backup.relativize(dir))); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String name = backup.relativize(file).toString().replace('\\', '/');
                if (!name.equals("uid.dat") && !name.equals("session.lock")) Files.copy(file, copy.resolve(name));
                return FileVisitResult.CONTINUE;
            }
        });
        World snapshot = Bukkit.createWorld(new WorldCreator(copy.getFileName().toString()));
        if (snapshot == null) throw new AssertionError("backup copy did not load");
        try {
            assertChest(snapshot, Material.DIAMOND);
            if (snapshot.getBlockAt(x + 1, y, z).getType() != Material.EMERALD_BLOCK)
                throw new AssertionError("loaded world's unsaved block was not flushed into backup");
        } finally {
            if (!Bukkit.unloadWorld(snapshot, false)) throw new AssertionError("could not unload backup copy");
        }
    }
}
