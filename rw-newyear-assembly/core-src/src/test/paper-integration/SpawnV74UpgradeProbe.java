package net.kinggrief.core.test;

import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/** Actual 7.3.0 -> 7.4.0 migration: old edits stay on disk; new map is active. */
public final class SpawnV74UpgradeProbe extends JavaPlugin {
    @Override public void onEnable() { Bukkit.getScheduler().runTaskLater(this, () -> {
        String mode=getConfig().getString("mode", "");
        try { check(mode); getLogger().info("KG-SPAWN-INSTALL PASS: " + mode); }
        catch (Throwable e) { getLogger().severe("KG-SPAWN-INSTALL FAIL: " + e); e.printStackTrace(); }
        finally { Bukkit.shutdown(); }
    }, 30L); }
    private void check(String mode) throws Exception {
        Object core=Bukkit.getPluginManager().getPlugin("KingGriefCore");
        Object engine=core.getClass().getMethod("getSpawnEngine").invoke(core);
        Location arrival=(Location)engine.getClass().getMethod("getPreparedSpawnArrival").invoke(engine);
        if(arrival==null || arrival.getWorld()==null) throw new AssertionError("No active prepared spawn");
        World active=arrival.getWorld(); Path base=Bukkit.getWorldContainer().toPath();
        Object store=core.getClass().getMethod("getStore").invoke(core);
        Path state=getDataFolder().toPath(); Files.createDirectories(state);
        if(mode.equals("v730seed")) {
            if(!Files.isRegularFile(active.getWorldFolder().toPath().resolve(".kinggrief_prebuilt_v73")) || Files.exists(active.getWorldFolder().toPath().resolve(".kinggrief_prebuilt_v74"))) throw new AssertionError("expected genuine v73 template");
            active.getBlockAt(50,101,50).setType(Material.CHEST,false);
            ((Chest)active.getBlockAt(50,101,50).getState()).getBlockInventory().setItem(0,new ItemStack(Material.EMERALD,7));
            active.setChunkForceLoaded(2,2,true);
            Location custom=new Location(active, 12.5, 101, 22.5, 0, 0);
            store.getClass().getMethod("setCustomSpawnLoc", Location.class).invoke(store, custom);
            Files.write(state.resolve("saved-spawn.txt"), (active.getName()+";12.5;101.0;22.5;0.0;0.0").getBytes(StandardCharsets.UTF_8));
            Files.write(active.getWorldFolder().toPath().resolve("owner-edit.txt"),"keep the old world\n".getBytes(StandardCharsets.UTF_8));
            Files.write(state.resolve("old-name.txt"),active.getName().getBytes(StandardCharsets.UTF_8));
            Files.write(state.resolve("old-uuid.txt"),active.getUID().toString().getBytes(StandardCharsets.UTF_8));
            return;
        }
        if(!mode.equals("upgrade")) throw new AssertionError("Unknown phase");
        String oldName=new String(Files.readAllBytes(state.resolve("old-name.txt")),StandardCharsets.UTF_8);
        Path old=base.resolve(oldName);
        if(active.getName().equals(oldName) || !Files.isRegularFile(active.getWorldFolder().toPath().resolve(".kinggrief_prebuilt_v74")))
            throw new AssertionError("new separate v74 spawn is not active");
        if(Bukkit.getWorld(oldName)!=null) throw new AssertionError("old spawn was unnecessarily loaded");
        String saved=new String(Files.readAllBytes(state.resolve("saved-spawn.txt")),StandardCharsets.UTF_8);
        org.bukkit.configuration.file.YamlConfiguration data=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(base.resolve("plugins/KingGriefCore/data.yml").toFile());
        if(!saved.equals(data.getString("customSpawnArrival")))throw new AssertionError("saved /setspawn was discarded");
        if(store.getClass().getMethod("getCustomSpawnLoc").invoke(store)!=null)throw new AssertionError("unloaded old world should not resolve until loaded");
        Location resolved=(Location)engine.getClass().getMethod("getSpawnArrival").invoke(engine);
        if(resolved==null || resolved.getWorld()!=active)throw new AssertionError("unloaded saved spawn must fall back to active village");
        if(!"keep the old world\n".equals(new String(Files.readAllBytes(old.resolve("owner-edit.txt")),StandardCharsets.UTF_8)))
            throw new AssertionError("old user file disappeared");
        if(active.getBlockAt(118,120,24).getType()!=Material.PACKED_ICE && active.getBlockAt(118,120,24).getType()!=Material.BLUE_ICE)
            throw new AssertionError("new ice walls missing");
        Path snapshot=Files.createTempDirectory(base,"kg-old-v74-proof-");
        Files.walkFileTree(old,new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes attrs)throws IOException {
                Files.createDirectories(snapshot.resolve(old.relativize(dir)));return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attrs)throws IOException {
                String name=old.relativize(file).toString().replace('\\','/');
                if(!name.equals("uid.dat")&&!name.equals("session.lock"))Files.copy(file,snapshot.resolve(name));
                return FileVisitResult.CONTINUE;
            }
        });
        World copy=Bukkit.createWorld(new WorldCreator(snapshot.getFileName().toString()));
        if(copy==null) throw new AssertionError("old world snapshot did not load");
        try {
            if(copy.getBlockAt(50,101,50).getType()!=Material.CHEST)throw new AssertionError("old chest lost");
            ItemStack item=((Chest)copy.getBlockAt(50,101,50).getState()).getBlockInventory().getItem(0);
            if(item==null||item.getType()!=Material.EMERALD||item.getAmount()!=7)throw new AssertionError("old inventory lost");
            if(!copy.isChunkForceLoaded(2,2))throw new AssertionError("old force-loaded ticket lost");
        } finally {
            if(!Bukkit.unloadWorld(copy,false))throw new AssertionError("could not unload snapshot");
        }
    }
}
