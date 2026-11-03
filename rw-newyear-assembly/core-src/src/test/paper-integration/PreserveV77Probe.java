package net.kinggrief.core.test;
import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Published 7.7.0 to 7.8.0: the existing v77 world is not reinstalled. */
public final class PreserveV77Probe extends JavaPlugin {
 @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{
  String mode=getConfig().getString("mode","");
  try{check(mode);getLogger().info("KG-SPAWN-INSTALL PASS: "+mode);}catch(Throwable e){getLogger().severe("KG-SPAWN-INSTALL FAIL: "+e);e.printStackTrace();}
  finally{Bukkit.shutdown();}
 },80L);}
 private void check(String mode)throws Exception{
  Plugin core=Bukkit.getPluginManager().getPlugin("KingGriefCore");
  Object engine=core.getClass().getMethod("getSpawnEngine").invoke(core);
  Location arrival=(Location)engine.getClass().getMethod("getPreparedSpawnArrival").invoke(engine);
  if(arrival==null || arrival.getWorld()==null)throw new AssertionError("no active spawn");
  World active=arrival.getWorld();Object store=core.getClass().getMethod("getStore").invoke(core);
  Path state=getDataFolder().toPath();Files.createDirectories(state);
  if(mode.equals("seed77")){
   if(!core.getDescription().getVersion().equals("7.7.0"))throw new AssertionError("old Core mismatch");
   active.getBlockAt(50,115,50).setType(Material.CHEST,false);
   ((Chest)active.getBlockAt(50,115,50).getState()).getBlockInventory().setItem(0,new ItemStack(Material.DIAMOND,21));
   active.setChunkForceLoaded(2,2,true);
   store.getClass().getMethod("setCoins",String.class,double.class).invoke(store,"PersistUser",123456.0);
   store.getClass().getMethod("setCustomWarp",String.class,String.class,Location.class).invoke(store,"preserve","PersistUser",new Location(active,50.5,116,50.5));
   if(!Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"elka 2"))throw new AssertionError("elka not available");
   Field field=store.getClass().getDeclaredField("yml");field.setAccessible(true);
   YamlConfiguration yml=(YamlConfiguration)field.get(store);yml.set("players.persistuser.fortuneWheel.readyAt",1999999999999L);
   store.getClass().getMethod("save").invoke(store);
   Files.writeString(state.resolve("world-name"),active.getName(),StandardCharsets.UTF_8);
   Files.writeString(state.resolve("world-uuid"),active.getUID().toString(),StandardCharsets.UTF_8);
   Files.writeString(active.getWorldFolder().toPath().resolve("owner-78-proof.txt"),"keep intact\n",StandardCharsets.UTF_8);
   return;
  }
  if(!mode.equals("verify78") || !core.getDescription().getVersion().equals("7.8.0"))throw new AssertionError("new Core mismatch");
  if(!active.getName().equals(Files.readString(state.resolve("world-name")))
          || !active.getUID().toString().equals(Files.readString(state.resolve("world-uuid"))))throw new AssertionError("active world changed");
  if(!Files.readString(active.getWorldFolder().toPath().resolve("owner-78-proof.txt")).equals("keep intact\n"))throw new AssertionError("world file changed");
  if(active.getBlockAt(50,115,50).getType()!=Material.CHEST)throw new AssertionError("edited chest missing");
  ItemStack item=((Chest)active.getBlockAt(50,115,50).getState()).getBlockInventory().getItem(0);
  if(item==null || item.getType()!=Material.DIAMOND || item.getAmount()!=21)throw new AssertionError("edited chest inventory lost");
  if(!active.isChunkForceLoaded(2,2))throw new AssertionError("force-loaded chunk lost");
  if(((Number)store.getClass().getMethod("getCoins",String.class).invoke(store,"PersistUser")).doubleValue()!=123456.0)throw new AssertionError("coins lost");
  if(store.getClass().getMethod("getCustomWarp",String.class).invoke(store,"preserve")==null)throw new AssertionError("custom warp lost");
  YamlConfiguration yml=YamlConfiguration.loadConfiguration(core.getDataFolder().toPath().resolve("data.yml").toFile());
  if(yml.getLong("players.persistuser.fortuneWheel.readyAt")!=1999999999999L || yml.getInt("spawnTree.modelLevel")!=2)throw new AssertionError("wheel cooldown or tree scale lost");
 }
}
