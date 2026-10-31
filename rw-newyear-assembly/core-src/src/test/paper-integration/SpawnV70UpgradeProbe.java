package net.kinggrief.core.test;

import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Prove a real v70 installation is not overwritten by the v71 clean template. */
public final class SpawnV70UpgradeProbe extends JavaPlugin {
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{
        String mode=getConfig().getString("mode","");
        try{check(mode);getLogger().info("KG-SPAWN-INSTALL PASS: "+mode);}
        catch(Throwable e){getLogger().severe("KG-SPAWN-INSTALL FAIL: "+e);e.printStackTrace();}
        finally{Bukkit.shutdown();}
    },30L);}
    private void check(String mode)throws Exception{
        World world=Bukkit.getWorld("spawn_world");if(world==null)throw new AssertionError("spawn world absent");
        Path folder=world.getWorldFolder().toPath(), core=Bukkit.getWorldContainer().toPath().resolve("plugins/KingGriefCore");
        Path marker=core.resolve(".spawn-v70-template-installed");
        if(!Files.isRegularFile(marker)||!Files.isRegularFile(folder.resolve(".kinggrief_prebuilt_v70")))throw new AssertionError("v70 markers absent");
        int forced=0;for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)if(world.isChunkForceLoaded(x,z))forced++;
        if(forced!=289)throw new AssertionError("existing force-load tickets changed: "+forced);
        Path uid=getDataFolder().toPath().resolve("world-uid.txt");
        if(mode.equals("v70seed")){
            world.getBlockAt(0,120,52).setType(Material.CHEST,false);
            ((Chest)world.getBlockAt(0,120,52).getState()).getBlockInventory().setItem(0,new ItemStack(Material.EMERALD,7));
            Files.write(folder.resolve("owner-edit.txt"),"do not overwrite\n".getBytes(StandardCharsets.UTF_8));
            Files.createDirectories(getDataFolder().toPath());Files.write(uid,world.getUID().toString().getBytes(StandardCharsets.UTF_8));
            return;
        }
        if(!mode.equals("v71upgrade"))throw new AssertionError("unknown phase");
        if(!new String(Files.readAllBytes(uid),StandardCharsets.UTF_8).equals(world.getUID().toString()))throw new AssertionError("world UUID changed");
        if(!new String(Files.readAllBytes(folder.resolve("owner-edit.txt")),StandardCharsets.UTF_8).equals("do not overwrite\n"))throw new AssertionError("world file lost");
        if(world.getBlockAt(0,120,52).getType()!=Material.CHEST)throw new AssertionError("edited chest lost");
        ItemStack item=((Chest)world.getBlockAt(0,120,52).getState()).getBlockInventory().getItem(0);
        if(item==null||item.getType()!=Material.EMERALD||item.getAmount()!=7)throw new AssertionError("chest contents lost");
        try(DirectoryStream<Path> backups=Files.newDirectoryStream(Bukkit.getWorldContainer().toPath(),"spawn_world_backup_*")){
            if(backups.iterator().hasNext())throw new AssertionError("installed world was unexpectedly migrated");
        }
    }
}
