package net.kinggrief.core.test;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/** Multi-boot proof of embedded install, deferred rebuild, backup and recovery. */
public final class SpawnRebuildProbe extends JavaPlugin {
    private Path data, pending, journal, probeData; private World spawn;
    private int checks; private boolean finished;
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> { try { begin(); } catch(Throwable e) { fail(e); } }, 20L);
    }
    private void ok(boolean value,String message) { checks++; if(!value)throw new AssertionError(message);getLogger().info("KG-REBUILD PASS " + message); }
    private void fail(Throwable e) { if(finished)return;finished=true;getLogger().severe("KG-REBUILD FAIL " + e);e.printStackTrace();Bukkit.shutdown(); }
    private void finish(String mode) { if(finished)return;finished=true;getLogger().info("KG-REBUILD RESULT PASS " + mode + " checks=" + checks);Bukkit.shutdown(); }
    private void begin() throws Exception {
        String mode=getConfig().getString("mode","");
        data=Bukkit.getWorldContainer().toPath().resolve("plugins/KingGriefCore");pending=data.resolve(".spawn-v72-rebuild-pending");journal=data.resolve(".spawn-v72-swap-journal");probeData=getDataFolder().toPath();
        Object core=Bukkit.getPluginManager().getPlugin("KingGriefCore");
        Object engine=core.getClass().getMethod("getSpawnEngine").invoke(core);
        boolean active=((Boolean)engine.getClass().getMethod("isSpawnActive").invoke(engine)).booleanValue();
        org.bukkit.Location selected=(org.bukkit.Location)engine.getClass().getMethod("getPreparedSpawnArrival").invoke(engine);
        spawn=selected==null?null:selected.getWorld();
        if("custom-preserve".equals(mode)) {
            Path original=Bukkit.getWorldContainer().toPath().resolve("spawn_world");
            ok(active && spawn!=null && spawn.getName().equals("kinggrief_spawn") && Bukkit.getWorld("spawn_world")==null,
                    "unmarked custom world remains untouched and prepared spawn is separate");
            ok("custom user file\n".equals(new String(Files.readAllBytes(original.resolve("custom-data.txt")),StandardCharsets.UTF_8)),"existing custom file remains untouched");
            ok(backups().isEmpty() && !Files.exists(pending),"external template did not replace existing custom world on ordinary startup");
            writeChest(spawn,Material.DIAMOND);
            Files.createDirectories(probeData);Files.write(probeData.resolve("uid.txt"),spawn.getUID().toString().getBytes(StandardCharsets.UTF_8));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"rebuildspawn confirm");
            waitPending(0,()->{ok(Files.isRegularFile(original.resolve("custom-data.txt")),"queue preserves custom world until restart");finish(mode);});
            return;
        }
        if(spawn==null)throw new AssertionError("spawn world not loaded");
        if("custom-installed".equals(mode) || "custom-recovered".equals(mode)) {
            Path original=Bukkit.getWorldContainer().toPath().resolve("spawn_world");
            List<Path> copies=backups();
            ok(active && spawn.getName().equals("kinggrief_spawn") && Bukkit.getWorld("spawn_world")==null,
                    "prepared fallback is active without loading the original custom world");
            ok("custom user file\n".equals(new String(Files.readAllBytes(original.resolve("custom-data.txt")),StandardCharsets.UTF_8)),"original custom world remains untouched");
            ok(new String(Files.readAllBytes(probeData.resolve("uid.txt")),StandardCharsets.UTF_8).equals(spawn.getUID().toString()),"fallback world UUID retained");
            if("custom-installed".equals(mode)) {
                ok(copies.size()==1,"explicit rebuild retains one fallback-world backup");
                verifyBackupChest(copies.get(0));
                writeChest(spawn,Material.GOLD_INGOT);
                Files.write(spawn.getWorldFolder().toPath().resolve("after-rebuild.txt"),"preserve fallback edit\n".getBytes(StandardCharsets.UTF_8));
            } else {
                checkChest(spawn,Material.GOLD_INGOT);
                ok("preserve fallback edit\n".equals(new String(Files.readAllBytes(spawn.getWorldFolder().toPath().resolve("after-rebuild.txt")),StandardCharsets.UTF_8)),"recovery retained fallback edits");
            }
            ok(!Files.exists(pending) && !Files.exists(journal),"replacement/recovery completed cleanly");finish(mode);return;
        }
        if("main-world".equals(mode)) {
            ok(active && spawn.getName().equals("kinggrief_spawn"),"unmarked main world remains separate from managed spawn");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"rebuildspawn confirm");
            waitPending(0,()->{ok(Files.exists(pending),"rebuild request targets fallback");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"rebuildspawn cancel");
                ok(!Files.exists(pending),"fallback rebuild can be cancelled");finish(mode);});
            return;
        }
        if("queue".equals(mode)) { queueTest(); return; }
        if("installed".equals(mode)) {
            ok(Files.isRegularFile(data.resolve(".spawn-v70-template-installed")),"install marker preserved");
            ok(!Files.exists(pending) && !Files.exists(journal),"completed request and journal removed");
            ok(new String(Files.readAllBytes(probeData.resolve("uid.txt")),StandardCharsets.UTF_8).equals(spawn.getUID().toString()),"world UUID retained after rebuild");
            List<Path> backups=backups(); ok(backups.size()==1,"one original world backup retained");
            ok("original user file\n".equals(new String(Files.readAllBytes(backups.get(0).resolve("user-data.txt")),StandardCharsets.UTF_8)),"backup contains original custom file");
            verifyBackupChest(backups.get(0));
            ok(spawn.getBlockAt(50,101,50).getType()!=Material.CHEST,"prepared replacement installed after restart");
            writeChest(spawn,Material.GOLD_INGOT);
            Files.write(spawn.getWorldFolder().toPath().resolve("after-rebuild.txt"),"preserve edit\n".getBytes(StandardCharsets.UTF_8));
            finish(mode);return;
        }
        if("repeat".equals(mode) || "recovered".equals(mode)) {
            ok("preserve edit\n".equals(new String(Files.readAllBytes(spawn.getWorldFolder().toPath().resolve("after-rebuild.txt")),StandardCharsets.UTF_8)),"ordinary restart retains post-install edits");
            checkChest(spawn,Material.GOLD_INGOT);
            ok(!Files.exists(pending) && !Files.exists(journal),"no request or journal remains");
            ok(backups().size()==1,"ordinary restart creates no extra backup");
            finish(mode);return;
        }
        throw new AssertionError("unknown mode " + mode);
    }
    private void queueTest() throws Exception {
        ok(!Files.exists(data.resolve("spawn-v71-template.zip")),"test uses embedded template, not external copy");
        ok(Files.isRegularFile(data.resolve(".spawn-v70-template-installed")),"standalone Core installed prepared spawn");
        ok(Files.isRegularFile(spawn.getWorldFolder().toPath().resolve(".kinggrief_prebuilt_v70")),"prepared marker present");
        writeChest(spawn,Material.DIAMOND); Files.write(spawn.getWorldFolder().toPath().resolve("user-data.txt"),"original user file\n".getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(probeData);Files.write(probeData.resolve("uid.txt"),spawn.getUID().toString().getBytes(StandardCharsets.UTF_8));
        long start=System.nanoTime();
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"rebuildspawn confirm");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"rebuildspawn confirm");
        waitPending(0,()->{
            ok((System.nanoTime()-start)<15_000_000_000L,"server keeps ticking while template is checked asynchronously");
            checkChest(spawn,Material.DIAMOND);
            ok(backupsUnchecked().isEmpty(),"request does not replace or back up world during live tick");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"rebuildspawn cancel");
            ok(!Files.exists(pending),"operator can cancel queued rebuild");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"kgadmin rebuildspawn confirm");
            waitPending(0,()->{checkChest(spawn,Material.DIAMOND);finish("queue");});
        });
    }
    private void waitPending(final int ignored,final Runnable next) {
        new BukkitRunnable() {
            int tries;
            @Override public void run() {
                try {
                    java.lang.reflect.Field busyField=Class.forName("net.kinggrief.core.PreparedSpawnInstaller").getDeclaredField("REQUEST_BUSY");
                    busyField.setAccessible(true);
                    boolean busy=((java.util.concurrent.atomic.AtomicBoolean)busyField.get(null)).get();
                    if(Files.exists(pending) && !busy) { cancel();next.run();return; }
                    if(++tries>200)throw new AssertionError("rebuild request not queued");
                } catch(Throwable e){cancel();fail(e);}
            }
        }.runTaskTimer(this,2L,2L);
    }
    private void writeChest(World world,Material item) {
        Block block=world.getBlockAt(50,101,50);block.setType(Material.CHEST,false);
        ((Chest)block.getState()).getBlockInventory().setItem(0,new ItemStack(item,3));
    }
    private void checkChest(World world,Material item) {
        Block block=world.getBlockAt(50,101,50);
        if(block.getType()!=Material.CHEST)throw new AssertionError("original chest missing");
        ItemStack stack=((Chest)block.getState()).getBlockInventory().getItem(0);
        ok(stack!=null && stack.getType()==item && stack.getAmount()==3,"chest contents preserved: "+item);
    }
    private List<Path> backups() throws IOException {
        List<Path> found=new ArrayList<Path>();
        try(DirectoryStream<Path> stream=Files.newDirectoryStream(Bukkit.getWorldContainer().toPath(),"spawn_world_backup_*")) {
            for(Path path:stream)if(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))found.add(path);
        }
        return found;
    }
    private List<Path> backupsUnchecked() { try{return backups();}catch(IOException e){throw new RuntimeException(e);} }
    private void verifyBackupChest(final Path backup) throws Exception {
        final Path copy=Files.createTempDirectory(Bukkit.getWorldContainer().toPath(),"kg_backup72_");
        Files.walkFileTree(backup,new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes attrs) throws IOException {Files.createDirectories(copy.resolve(backup.relativize(dir)));return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attrs) throws IOException {
                String name=backup.relativize(file).toString().replace('\\','/');
                if(!name.equals("uid.dat") && !name.equals("session.lock"))Files.copy(file,copy.resolve(name));return FileVisitResult.CONTINUE;
            }
        });
        World backupWorld=Bukkit.createWorld(new WorldCreator(copy.getFileName().toString()));
        if(backupWorld==null)throw new AssertionError("backup copy did not load");
        try { checkChest(backupWorld,Material.DIAMOND); }
        finally { if(!Bukkit.unloadWorld(backupWorld,false))throw new AssertionError("cannot unload backup copy"); }
    }
}
