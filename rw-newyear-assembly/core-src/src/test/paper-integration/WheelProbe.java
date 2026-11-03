package net.kinggrief.probe;

import net.kinggrief.core.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Real players, actual NPC packets, deterministic prizes, save failures and crash restart. */
public final class WheelProbe extends JavaPlugin implements Listener {
    private KingGriefCore core;private SpawnEngine engine;private Player a,b;
    private boolean started,finished;private int checks,npcClicks;private UUID wheelHitbox;
    @EventHandler(priority=EventPriority.MONITOR) public void observedNpc(PlayerInteractEntityEvent event){
        if(event.getPlayer().equals(a)&&event.getRightClicked().getUniqueId().equals(wheelHitbox))npcClicks++;
    }
    @Override public void onEnable(){core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");engine=core.getSpawnEngine();Bukkit.getPluginManager().registerEvents(this,this);}
    @EventHandler public void join(PlayerJoinEvent e){
        if(e.getPlayer().getName().equals("WheelOne"))a=e.getPlayer();
        if(e.getPlayer().getName().equals("WheelTwo"))b=e.getPlayer();
        if(!started&&a!=null&&b!=null){started=true;later(25,()->{
            World w=engine.getPreparedSpawnArrival().getWorld();
            a.teleport(new Location(w,.5,101,34.5));b.teleport(new Location(w,2.5,101,34.5));
            later(100,()->{if(getConfig().getInt("phase",1)==1)first();else second();});
        });}
    }
    private void later(long ticks,Runnable action){Bukkit.getScheduler().runTaskLater(this,()->{try{action.run();}catch(Throwable e){fail(e);}},ticks);}
    private void ok(boolean value,String text){if(!value)throw new AssertionError(text);checks++;getLogger().info("KG-WHEEL PASS "+text);}
    private void fail(Throwable e){if(finished)return;finished=true;getLogger().severe("KG-WHEEL RESULT FAIL "+e);e.printStackTrace();}
    private void finish(){finished=true;getLogger().info("KG-WHEEL RESULT PASS "+checks+" checks");}
    @SuppressWarnings("unchecked") private <T>T field(Object o,String n){try{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return (T)f.get(o);}catch(Exception e){throw new RuntimeException(e);}}
    private YamlConfiguration data(){return field(core.getStore(),"yml");}
    private String root(Player p){return "players."+p.getName().toLowerCase(Locale.ROOT)+".fortuneWheel";}
    private long ready(Player p){return data().getLong(root(p)+".readyAt",0);}
    private void expired(Player p){data().set(root(p)+".readyAt",0L);}
    private void clearPending(Player p){data().set(root(p)+".pending",null);}
    private void fund(Player p,double coins,int snow){core.getStore().setCoins(p.getName(),coins);data().set("players."+p.getName().toLowerCase(Locale.ROOT)+".snowflakes",snow);}
    private void seed(int target){
        Random random=field(field(engine,"fortuneWheel"),"rng");
        for(long seed=0;seed<100000;seed++)if(new Random(seed).nextInt(100)==target){random.setSeed(seed);return;}
        throw new AssertionError("cannot seed reward "+target);
    }
    private void cmd(Player p,String args){p.performCommand("wheel"+(args.isEmpty()?"":" "+args));}
    private void full(Player p){p.getInventory().clear();for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Material.COBBLESTONE,64));}
    private int count(Player p,ItemStack sought){int n=0;for(ItemStack it:p.getInventory().getStorageContents())if(it!=null&&it.isSimilar(sought))n+=it.getAmount();return n;}
    private void blocked(Runnable task){
        Path f=core.getDataFolder().toPath().resolve("data.yml"),backup=f.resolveSibling("wheel-save-test.yml");
        try{Files.move(f,backup);Files.createSymbolicLink(f,backup.getFileName());task.run();}
        catch(Exception e){throw new RuntimeException(e);}
        finally{try{if(Files.isSymbolicLink(f))Files.delete(f);if(Files.exists(backup))Files.move(backup,f);}catch(Exception e){throw new RuntimeException(e);}}
    }
    private void first(){
        ok(core.getDescription().getVersion().equals("7.7.0"),"correct Core loaded");
        a.getInventory().clear();b.getInventory().clear();fund(a,0,0);
        cmd(a,"snow");cmd(a,"money");cmd(a,"nonsense");
        ok(ready(a)==0&&core.getStore().getCoins(a.getName())==0&&core.getStore().getSnowflakes(a.getName())==0,"insufficient funds and invalid arguments do not charge or start cooldown");
        fund(a,50000,20);full(a);cmd(a,"");cmd(a,"money");
        ok(ready(a)==0&&core.getStore().getCoins(a.getName())==50000&&core.getStore().getSnowflakes(a.getName())==20,"full inventory cannot lose payment or start cooldown");
        a.getInventory().clear();core.getStore().save();seed(0);blocked(()->cmd(a,"snow"));
        ok(ready(a)==0&&core.getStore().getCoins(a.getName())==50000&&core.getStore().getSnowflakes(a.getName())==20,"failed durable save rolls back charge, reward and cooldown");
        data().set(root(a)+".readyAt","bad");seed(0);cmd(a,"snow");
        ok(core.getStore().getSnowflakes(a.getName())==20&&core.getStore().getCoins(a.getName())==50000,"malformed cooldown fails closed");
        expired(a);a.setOp(true);core.getStore().setSavedRank(a.getName(),"owner");seed(0);
        long now=System.currentTimeMillis();cmd(a,"snow");
        ok(ready(a)>=now+899000&&ready(a)<=System.currentTimeMillis()+900000,"successful spin sets exactly fifteen minutes");
        ok(core.getStore().getSnowflakes(a.getName())==15&&core.getStore().getCoins(a.getName())==80000,"snow payment and coin prize commit together");
        long deadline=ready(a);cmd(a,"money");engine.spinFortuneWheel(a,true);
        ok(ready(a)==deadline&&core.getStore().getSnowflakes(a.getName())==15&&core.getStore().getCoins(a.getName())==80000,"both currency routes share the cooldown, including owner/OP");
        @SuppressWarnings("unchecked") Map<String,UUID> hits=field(engine,"npcHitboxMap");
        Entity npc=Bukkit.getEntity(hits.get("wheel_npc"));ok(npc!=null,"real wheel NPC hitbox exists");
        wheelHitbox=npc.getUniqueId();
        getLogger().info("KG-WHEEL REQUEST NPC "+npc.getEntityId());
        later(30,()->{
            ok(npcClicks>0,"real client reached the wheel NPC hitbox");
            ok(ready(a)==deadline&&core.getStore().getSnowflakes(a.getName())==15&&core.getStore().getCoins(a.getName())==80000,"real NPC packet cannot bypass command cooldown");
            expired(a);fund(a,50000,20);seed(90);
            int keys=core.getStore().getCaseKeys(a.getName(),"summer");
            getLogger().info("KG-WHEEL REQUEST NPC "+npc.getEntityId());
            later(30,()->{
                ok(npcClicks>=2&&core.getStore().getSnowflakes(a.getName())==15
                        &&core.getStore().getCaseKeys(a.getName(),"summer")==keys+1&&ready(a)>System.currentTimeMillis(),
                        "NPC performs a paid spin after expiry and starts the same cooldown");
                prizes();
            });
        });
    }
    private void prizes(){
        b.getInventory().clear();fund(b,50000,20);int oldKey=core.getStore().getCaseKeys(b.getName(),"summer");seed(90);cmd(b,"money");
        ok(core.getStore().getCoins(b.getName())==35000&&core.getStore().getCaseKeys(b.getName(),"summer")==oldKey+1&&ready(b)>System.currentTimeMillis(),"second player independently wins a case for money");
        String[] names={"COINS","PLAST","MANDARIN","RELICS","AURA","CASE","SANTA"};int[] rolls={0,22,42,58,74,88,97};
        for(int i=0;i<names.length;i++){
            expired(a);clearPending(a);a.getInventory().clear();fund(a,50000,20);core.getStore().setRelics(a.getName(),50);
            int keys=core.getStore().getCaseKeys(a.getName(),"summer");seed(rolls[i]);cmd(a,"snow");
            ok(names[i].equals(data().getString(root(a)+".last.prize"))&&core.getStore().getSnowflakes(a.getName())==15&&ready(a)>System.currentTimeMillis(),"deterministic "+names[i]+" roll is paid and recorded");
            ok(!data().contains(root(a)+".pending"),names[i]+" completes or has no item delivery");
            if(i==0)ok(core.getStore().getCoins(a.getName())==80000,"coin reward credited");
            if(i==1)ok(count(a,ItemFactory.plast(4))==4&&count(a,ItemFactory.trapka(2))==2,"plast and trapka delivered");
            if(i==2)ok(count(a,ItemFactory.nyMandarin(8))==8&&count(a,ItemFactory.potionKiller(1))==1,"mandarin and potion delivered");
            if(i==3){
                ok(core.getStore().getRelics(a)==75,"relic reward credited locally");
                try {Plugin points=Bukkit.getPluginManager().getPlugin("PlayerPoints");int balance=(Integer)points.getClass().getMethod("balance",UUID.class).invoke(points,a.getUniqueId());ok(balance==75,"relic reward synchronized to mock PlayerPoints");}
                catch(Exception e){throw new RuntimeException(e);}
            }
            if(i==4)ok(count(a,ItemFactory.bozhyaAura(2))==2&&count(a,ItemFactory.dezorient(2))==2,"aura and dezorient delivered");
            if(i==5)ok(core.getStore().getCaseKeys(a.getName(),"summer")==keys+1,"case reward credited");
            if(i==6)ok(count(a,ItemFactory.sharSanty())==1,"jackpot orb delivered");
        }
        expired(a);clearPending(a);fund(a,50000,20);a.getInventory().clear();seed(0);cmd(a,"money");
        ok(core.getStore().getCoins(a.getName())==65000,"money payment and coin prize use correct net balance");
        expired(a);clearPending(a);a.getInventory().clear();fund(a,50000,20);
        core.getStore().toggleQuickFreeze(a.getUniqueId());cmd(a,"snow");core.getStore().clearQuickFreeze(a.getUniqueId());
        ok(ready(a)==0&&core.getStore().getSnowflakes(a.getName())==20,"frozen player cannot spin");
        core.getConfig().set("enable-builtin-auth",true);core.getStore().setAuthenticated(a,false);cmd(a,"snow");
        ok(ready(a)==0&&core.getStore().getSnowflakes(a.getName())==20,"unauthenticated player cannot spin");
        core.getStore().setAuthenticated(a,true);core.getConfig().set("enable-builtin-auth",false);
        pending();
    }
    private void pending(){
        expired(a);clearPending(a);String pending=root(a)+".pending",receipt=UUID.randomUUID().toString();
        data().set(pending+".prize","PLAST");data().set(pending+".state","ready");data().set(pending+".receipt",receipt);
        full(a);core.getStore().save();cmd(a,"claim");
        ok("ready".equals(data().getString(pending+".state")),"full inventory keeps a saved prize available");
        int snow=core.getStore().getSnowflakes(a.getName());cmd(a,"snow");ok(core.getStore().getSnowflakes(a.getName())==snow&&ready(a)==0,"unclaimed prize blocks another spin without charging");
        a.getInventory().clear();cmd(a,"claim");
        ok(!data().contains(pending)&&count(a,ItemFactory.plast(4))==4&&count(a,ItemFactory.trapka(2))==2,"claim delivers saved prize once and clears receipt");
        cmd(a,"claim");ok(count(a,ItemFactory.plast(4))==4,"duplicate claim cannot duplicate items");
        a.getInventory().clear();data().set(pending+".prize","SANTA");data().set(pending+".state","delivering");data().set(pending+".receipt",UUID.randomUUID().toString());
        cmd(a,"claim");cmd(a,"snow");ok(count(a,ItemFactory.sharSanty())==0&&"delivering".equals(data().getString(pending+".state"))&&ready(a)==0,"ambiguous in-progress delivery fails closed without a duplicate or a charge");
        clearPending(a);expired(a);a.getInventory().clear();fund(a,20000,20);seed(0);cmd(a,"snow");
        ok(core.getStore().getSnowflakes(a.getName())==15&&core.getStore().getCoins(a.getName())==50000&&ready(a)>System.currentTimeMillis(),"durable restart fixture created");
        later(35,this::finish);
    }
    private void second(){
        ok(ready(a)>System.currentTimeMillis()&&ready(b)>System.currentTimeMillis(),"both independent cooldowns survive SIGKILL and reconnect");
        ok(core.getStore().getSnowflakes(a.getName())==15&&core.getStore().getCoins(a.getName())==50000,"payment and coin prize survive crash restart");
        long before=ready(a);cmd(a,"snow");cmd(a,"money");cmd(a,"status");cmd(a,"claim");
        ok(before==ready(a)&&core.getStore().getSnowflakes(a.getName())==15&&core.getStore().getCoins(a.getName())==50000,"restart does not allow another spin or change balances");
        ok(Bukkit.getPluginCommand("wheel").tabComplete(a,"wheel",new String[]{""}).containsAll(Arrays.asList("snow","money","status","claim")),"wheel subcommands are discoverable");
        later(20,this::finish);
    }
}
