package net.kinggrief.core.test;

import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/** Actual 7.6.0 -> 7.7.0 migration: old edits stay on disk; new map is active. */
public final class SpawnV77UpgradeProbe extends JavaPlugin {
    @Override public void onEnable() { Bukkit.getScheduler().runTaskLater(this, () -> {
        String mode=getConfig().getString("mode", "");
        if(mode.equals("upgrade")) {
            // A test-only ticket lets the model's scheduled chunk-load handler run.
            try {
                Object core=Bukkit.getPluginManager().getPlugin("KingGriefCore");
                Object engine=core.getClass().getMethod("getSpawnEngine").invoke(core);
                Location arrival=(Location)engine.getClass().getMethod("getPreparedSpawnArrival").invoke(engine);
                arrival.getWorld().addPluginChunkTicket(0,0,this);
            } catch(Exception e){getLogger().severe("KG-SPAWN-INSTALL FAIL: "+e);Bukkit.shutdown();return;}
        }
        Bukkit.getScheduler().runTaskLater(this,()->{
            try { check(mode); getLogger().info("KG-SPAWN-INSTALL PASS: " + mode); }
            catch (Throwable e) { getLogger().severe("KG-SPAWN-INSTALL FAIL: " + e); e.printStackTrace(); }
            finally { Bukkit.shutdown(); }
        },50L);
    }, 30L); }
    private void check(String mode) throws Exception {
        Object core=Bukkit.getPluginManager().getPlugin("KingGriefCore");
        Object engine=core.getClass().getMethod("getSpawnEngine").invoke(core);
        Location arrival=(Location)engine.getClass().getMethod("getPreparedSpawnArrival").invoke(engine);
        if(arrival==null || arrival.getWorld()==null) throw new AssertionError("No active prepared spawn");
        World active=arrival.getWorld(); Path base=Bukkit.getWorldContainer().toPath();
        Object store=core.getClass().getMethod("getStore").invoke(core);
        Path state=getDataFolder().toPath(); Files.createDirectories(state);
        if(mode.equals("v760seed")) {
            if(!Files.isRegularFile(active.getWorldFolder().toPath().resolve(".kinggrief_prebuilt_v76")) || Files.exists(active.getWorldFolder().toPath().resolve(".kinggrief_prebuilt_v77"))) throw new AssertionError("expected genuine v76 template");
            if(!((org.bukkit.plugin.Plugin)core).getDescription().getVersion().equals("7.6.0"))throw new AssertionError("expected genuine old Core 7.6.0");
            if(!Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"elka 2"))throw new AssertionError("old /elka command not available");
            org.bukkit.configuration.file.YamlConfiguration savedData=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(base.resolve("plugins/KingGriefCore/data.yml").toFile());
            if(savedData.getInt("spawnTree.modelLevel",0)!=2)throw new AssertionError("old Core did not durably save nonzero size");
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
        if(active.getName().equals(oldName) || !Files.isRegularFile(active.getWorldFolder().toPath().resolve(".kinggrief_prebuilt_v77")))
            throw new AssertionError("new separate v77 spawn is not active");
        if(Bukkit.getWorld(oldName)!=null) throw new AssertionError("old spawn was unnecessarily loaded");
        String saved=new String(Files.readAllBytes(state.resolve("saved-spawn.txt")),StandardCharsets.UTF_8);
        org.bukkit.configuration.file.YamlConfiguration data=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(base.resolve("plugins/KingGriefCore/data.yml").toFile());
        if(!saved.equals(data.getString("customSpawnArrival")))throw new AssertionError("saved /setspawn was discarded");
        if(data.getInt("spawnTree.modelLevel",0)!=2)throw new AssertionError("old nonzero model level was discarded");
        long count=java.util.Arrays.stream(active.getChunkAt(0,0).getEntities())
                .filter(e->e instanceof org.bukkit.entity.BlockDisplay&&e.getScoreboardTags().contains("kg_spawn_tree_v77")).count();
        if(count!=232)throw new AssertionError("new scale-coupled display count: "+count);
        for(int i=1;i<=3;i++){
            final int n=i;final float x=(16-2*i)*1.2f,y=(1+14*(i-1))*1.2f;
            boolean found=java.util.Arrays.stream(active.getChunkAt(0,0).getEntities()).filter(e->e instanceof org.bukkit.entity.BlockDisplay)
                    .map(e->(org.bukkit.entity.BlockDisplay)e).anyMatch(d->d.getScoreboardTags().contains("kg_elka_v77_person_"+n)
                            &&d.getBlock().getMaterial()==Material.DARK_OAK_PLANKS
                            &&Math.abs(d.getTransformation().getTranslation().x()-x)<.005f
                            &&Math.abs(d.getTransformation().getTranslation().y()-y)<.005f
                            &&Math.abs(d.getTransformation().getScale().x()-1.85f*1.2f)<.005f);
            if(!found)throw new AssertionError("person "+i+" did not inherit old scale");
        }
        if(active.getBlockAt(16,101,1).getType()!=Material.AIR||active.getBlockAt(0,103,0).getType()!=Material.AIR)
            throw new AssertionError("old fixed statue or tall trunk remains in new world");
        if(store.getClass().getMethod("getCustomSpawnLoc").invoke(store)!=null)throw new AssertionError("unloaded old world should not resolve until loaded");
        Location resolved=(Location)engine.getClass().getMethod("getSpawnArrival").invoke(engine);
        if(resolved==null || resolved.getWorld()!=active)throw new AssertionError("unloaded saved spawn must fall back to active village");
        if(!"keep the old world\n".equals(new String(Files.readAllBytes(old.resolve("owner-edit.txt")),StandardCharsets.UTF_8)))
            throw new AssertionError("old user file disappeared");
        if(active.getBlockAt(118,120,24).getType()!=Material.PACKED_ICE && active.getBlockAt(118,120,24).getType()!=Material.BLUE_ICE)
            throw new AssertionError("new ice walls missing");
        Path snapshot=Files.createTempDirectory(base,"kg-old-v76-proof-");
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
            if(copy.getBlockAt(16,101,1).getType()!=Material.DARK_OAK_PLANKS||copy.getBlockAt(0,130,0).getType()!=Material.SPRUCE_WOOD)
                throw new AssertionError("old v76 geometry changed");
            if(copy.getBlockAt(50,101,50).getType()!=Material.CHEST)throw new AssertionError("old chest lost");
            ItemStack item=((Chest)copy.getBlockAt(50,101,50).getState()).getBlockInventory().getItem(0);
            if(item==null||item.getType()!=Material.EMERALD||item.getAmount()!=7)throw new AssertionError("old inventory lost");
            if(!copy.isChunkForceLoaded(2,2))throw new AssertionError("old force-loaded ticket lost");
        } finally {
            if(!Bukkit.unloadWorld(copy,false))throw new AssertionError("could not unload snapshot");
        }
    }
}
