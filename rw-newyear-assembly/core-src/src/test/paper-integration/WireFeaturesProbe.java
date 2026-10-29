package net.kinggrief.probe;

import net.kinggrief.core.BossEventManager;
import net.kinggrief.core.KingGriefCore;
import net.kinggrief.core.SafeTradeManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Random;

/** Verifies actual client packets, not synthesized Bukkit clicks. */
public final class WireFeaturesProbe extends JavaPlugin implements Listener {
    private KingGriefCore core;private Player a,b;private boolean running;private BossEventManager.ActiveBossEvent event;
    @Override public void onEnable(){core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");Bukkit.getPluginManager().registerEvents(this,this);}
    @EventHandler public void join(PlayerJoinEvent e){
        if("OrbTester".equals(e.getPlayer().getName()))a=e.getPlayer();if("OrbEnemy".equals(e.getPlayer().getName()))b=e.getPlayer();
        if(a!=null&&b!=null&&!running){running=true;later(30,()->begin());}
    }
    private void later(long t,Runnable action){Bukkit.getScheduler().runTaskLater(this,()->{try{action.run();}catch(Throwable error){fail(error);}},t);}
    private void fail(Throwable e){getLogger().severe("KG-WIRE-FEATURE FAIL " + e);e.printStackTrace();Bukkit.getScheduler().runTaskLater(this,()->Bukkit.shutdown(),10);}
    private void ok(boolean value,String message){if(!value)throw new AssertionError(message);getLogger().info("KG-WIRE-FEATURE PASS " + message);}
    private void begin(){
        WorldCreator wc=new WorldCreator("kg_wire_fixture");wc.generator(new ChunkGenerator(){
            @Override public ChunkData generateChunkData(World w,Random random,int x,int z,BiomeGrid biome){
                ChunkData d=createChunkData(w);d.setRegion(0,60,0,16,64,16,Material.DIRT);d.setRegion(0,64,0,16,65,16,Material.GRASS_BLOCK);
                for(int i=0;i<16;i++)for(int j=0;j<16;j++)biome.setBiome(i,j,org.bukkit.block.Biome.PLAINS);return d;}
            @Override public boolean shouldGenerateNoise(){return false;}@Override public boolean shouldGenerateSurface(){return false;}
            @Override public boolean shouldGenerateCaves(){return false;}@Override public boolean shouldGenerateDecorations(){return false;}
            @Override public boolean shouldGenerateMobs(){return false;}@Override public boolean shouldGenerateStructures(){return false;}
        });wc.generateStructures(false);World w=Bukkit.createWorld(wc);int x=422,z=422;
        for(int cx=(x-9)>>4;cx<=(x+9)>>4;cx++)for(int cz=(z-9)>>4;cz<=(z+9)>>4;cz++)w.getChunkAt(cx,cz).load();
        event=core.getBossEvents().spawnBossEvent("airship",new Location(w,x,65,z));
        if(event==null)throw new AssertionError("airship fixture not created");
        Location part=new ArrayList<Location>(event.airshipParts).get(0);
        a.getInventory().clear();a.teleport(part.clone().add(1.5,1,0.5));
        later(35,()->{
            getLogger().info("KG-WIRE REQUEST BLOCK " + part.getBlockX()+" " + part.getBlockY()+" " + part.getBlockZ());
            later(25,()->{
                ok(event.airshipParts.size()==2 && part.getBlock().getType()==Material.AIR,"real client right-click collects one airship part");
                tradeStart();
            });
        });
    }
    private void tradeStart(){
        a.getInventory().clear();b.getInventory().clear();a.getInventory().setItem(0,new ItemStack(Material.DIAMOND,4));b.getInventory().setItem(0,new ItemStack(Material.EMERALD,6));
        a.performCommand("trade " + b.getName());b.performCommand("trade accept " + a.getName());
        ok(a.getOpenInventory().getTopInventory().getHolder() instanceof SafeTradeManager.TradeHolder,"both real clients opened trade windows");
        later(10,()->request("CLICK A 81",()->{
            ok(a.getOpenInventory().getTopInventory().getItem(9)!=null && a.getOpenInventory().getTopInventory().getItem(9).getType()==Material.DIAMOND,
                    "actual client click offered first stack");
            request("CLICK B 81",()->{
                ok(b.getOpenInventory().getTopInventory().getItem(9)!=null && b.getOpenInventory().getTopInventory().getItem(9).getType()==Material.EMERALD,
                        "actual client click offered second stack");
                request("CLICK A 45",()->request("CLICK B 45",()->{
                    ok(a.getInventory().containsAtLeast(new ItemStack(Material.EMERALD),6) && b.getInventory().containsAtLeast(new ItemStack(Material.DIAMOND),4),
                            "both real client confirmations commit the swap");
                    ok(!a.getInventory().containsAtLeast(new ItemStack(Material.DIAMOND),4) && !b.getInventory().containsAtLeast(new ItemStack(Material.EMERALD),6),
                            "real packet trade removed original stacks once");
                    getLogger().info("KG-WIRE-FEATURE RESULT PASS");
                }));
            });
        }));
    }
    private void request(String command,Runnable next){getLogger().info("KG-WIRE REQUEST " + command);later(20,next);}
}
