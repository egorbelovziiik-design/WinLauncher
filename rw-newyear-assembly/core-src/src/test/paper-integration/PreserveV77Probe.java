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

/** Actual published 7.7–7.12 to 7.13: keep the existing world and player state. */
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
  if(mode.equals("seed77") || mode.equals("seed78") || mode.equals("seed79") || mode.equals("seed710") || mode.equals("seed711") || mode.equals("seed712")){
   String expected=mode.equals("seed77")?"7.7.0":mode.equals("seed78")?"7.8.0":mode.equals("seed79")?"7.9.0":mode.equals("seed710")?"7.10.0":mode.equals("seed711")?"7.11.0":"7.12.0";
   if(!core.getDescription().getVersion().equals(expected))throw new AssertionError("old Core mismatch");
   active.getBlockAt(50,115,50).setType(Material.CHEST,false);
   ((Chest)active.getBlockAt(50,115,50).getState()).getBlockInventory().setItem(0,new ItemStack(Material.DIAMOND,21));
   active.setChunkForceLoaded(2,2,true);
   store.getClass().getMethod("setCoins",String.class,double.class).invoke(store,"PersistUser",123456.0);
   store.getClass().getMethod("setCustomWarp",String.class,String.class,Location.class).invoke(store,"preserve","PersistUser",new Location(active,50.5,116,50.5));
   if(!Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"elka 2"))throw new AssertionError("elka not available");
   Field field=store.getClass().getDeclaredField("yml");field.setAccessible(true);
   YamlConfiguration yml=(YamlConfiguration)field.get(store);yml.set("players.persistuser.fortuneWheel.readyAt",1999999999999L);
   yml.set("cosmeticPets.00000000-0000-4000-8000-000000000123","deer");
   yml.set("players.persistuser.spawnGifts.g0",true);
   yml.set("players.persistuser.playSec",3600L);yml.set("players.persistuser.playRewards.pt_900",true);
   yml.set("players.persistuser.dailyReady",1999999999999L);
   yml.set("players.persistuser.bp.1",true);yml.set("players.persistuser.bp.7",true);
   yml.set("players.persistuser.mined",1234);yml.set("players.persistuser.kills",56);
   yml.set("players.persistuser.relics",314);yml.set("players.persistuser.snowflakes",271);
   yml.set("achievements.00000000-0000-4000-8000-000000000123.claimed.ore_25",true);
   String exped="expeditions.00000000-0000-4000-8000-000000000123";
   yml.set(exped+".day.date","2026-01-05");yml.set(exped+".day.progress.ore",27);yml.set(exped+".day.claimed.harvest",true);
   yml.set(exped+".day.bonus",true);yml.set(exped+".week.date","2026-01-05");yml.set(exped+".week.claims",7);yml.set(exped+".week.bonus",true);
   if(mode.equals("seed712")){
    World survival=Bukkit.getWorlds().get(0);Location center=new Location(survival,400,100,400);
    survival.getBlockAt(center).setType(Material.DIAMOND_ORE,false);
    Object claim=store.getClass().getMethod("createClaimDurably",String.class,Location.class,int.class).invoke(store,"PersistUser",center,4);
    if(claim==null)throw new AssertionError("legacy claim not created");
    @SuppressWarnings("unchecked") java.util.Set<String> members=(java.util.Set<String>)claim.getClass().getField("members").get(claim);
    @SuppressWarnings("unchecked") java.util.Set<String> containers=(java.util.Set<String>)claim.getClass().getField("containerMembers").get(claim);
    members.add("preservebuilder");containers.add("preservechest");
    Object lot=store.getClass().getMethod("addAuctionLot",String.class,ItemStack.class,double.class)
            .invoke(store,"PersistUser",new ItemStack(Material.EMERALD,6),321.0);
    if(lot==null)throw new AssertionError("legacy auction lot not created");
    Files.writeString(state.resolve("legacy-lot-id"),String.valueOf(lot.getClass().getField("id").getInt(lot)),StandardCharsets.UTF_8);
   }
   store.getClass().getMethod("save").invoke(store);
   Files.writeString(state.resolve("world-name"),active.getName(),StandardCharsets.UTF_8);
   Files.writeString(state.resolve("world-uuid"),active.getUID().toString(),StandardCharsets.UTF_8);
   Files.writeString(active.getWorldFolder().toPath().resolve("owner-78-proof.txt"),"keep intact\n",StandardCharsets.UTF_8);
   return;
  }
  if(!mode.equals("verify713") || !core.getDescription().getVersion().equals("7.13.0"))throw new AssertionError("new Core mismatch");
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
  if(!"deer".equals(yml.getString("cosmeticPets.00000000-0000-4000-8000-000000000123")) || !yml.getBoolean("players.persistuser.spawnGifts.g0"))throw new AssertionError("pet selection or gift progress lost");
  if(yml.getLong("players.persistuser.playSec")!=3600L || !yml.getBoolean("players.persistuser.playRewards.pt_900"))throw new AssertionError("existing playtime or reward marker lost");
  if(yml.getLong("players.persistuser.dailyReady")!=1999999999999L || !yml.getBoolean("players.persistuser.bp.1")
          || !yml.getBoolean("players.persistuser.bp.7") || yml.getInt("players.persistuser.mined")!=1234
          || yml.getInt("players.persistuser.kills")!=56 || yml.getInt("players.persistuser.relics")!=314
          || yml.getInt("players.persistuser.snowflakes")!=271
          || !yml.getBoolean("achievements.00000000-0000-4000-8000-000000000123.claimed.ore_25"))
   throw new AssertionError("legacy daily, pass, counters, currencies or achievements lost");
  String exped="expeditions.00000000-0000-4000-8000-000000000123";
  if(yml.getInt(exped+".day.progress.ore")!=27 || !yml.getBoolean(exped+".day.claimed.harvest") || !yml.getBoolean(exped+".day.bonus")
          || yml.getInt(exped+".week.claims")!=7 || !yml.getBoolean(exped+".week.bonus"))throw new AssertionError("legacy expedition state lost");
  if(Files.exists(state.resolve("legacy-lot-id"))){
   World survival=Bukkit.getWorlds().get(0);Location center=new Location(survival,400,100,400);
   if(survival.getBlockAt(center).getType()!=Material.DIAMOND_ORE)throw new AssertionError("legacy claim center missing");
   Object claim=store.getClass().getMethod("getClaimAt",Location.class).invoke(store,center);
   if(claim==null || !"PersistUser".equals(claim.getClass().getField("owner").get(claim)))throw new AssertionError("legacy claim missing");
   if(!((java.util.Set<?>)claim.getClass().getField("members").get(claim)).contains("preservebuilder")
           || !((java.util.Set<?>)claim.getClass().getField("containerMembers").get(claim)).contains("preservechest"))throw new AssertionError("legacy claim roles lost");
   int id=Integer.parseInt(Files.readString(state.resolve("legacy-lot-id")).trim());
   Object lot=store.getClass().getMethod("findAuctionLot",int.class).invoke(store,id);
   if(lot==null)throw new AssertionError("legacy auction lot lost");
   ItemStack old=(ItemStack)lot.getClass().getField("item").get(lot);
   if(old.getType()!=Material.EMERALD||old.getAmount()!=6)throw new AssertionError("legacy auction item changed");
  }
 }
}
