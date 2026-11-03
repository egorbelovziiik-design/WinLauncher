package net.kinggrief.probe;

import net.kinggrief.core.KingGriefCore;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.lang.reflect.Field;

/** Four boots, three SIGKILLs, real logins, cancellation and injected save failures. */
public final class BanRecoveryProbe extends JavaPlugin implements Listener {
    private KingGriefCore core;private Path data,saved;private int phase,checks,joins;private boolean finished;
    @Override public void onEnable() {
        core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");
        phase=getConfig().getInt("phase");data=core.getDataFolder().toPath().resolve("data.yml");saved=data.resolveSibling("ban-probe-saved.yml");
        Bukkit.getPluginManager().registerEvents(this,this);
        later(40,this::begin);
    }
    private interface Task { void run() throws Exception; }
    private void later(long ticks,Task task){Bukkit.getScheduler().runTaskLater(this,()->{try{task.run();}catch(Throwable error){fail(error);}},ticks);}
    private void ok(boolean value,String text){if(!value)throw new AssertionError(text);checks++;getLogger().info("KG-BANCRASH PASS "+text);}
    private void fail(Throwable error){if(finished)return;finished=true;restore();getLogger().severe("KG-BANCRASH RESULT FAIL "+error);error.printStackTrace();}
    private void finish(){if(finished)return;finished=true;restore();getLogger().info("KG-BANCRASH RESULT PASS phase="+phase+" checks="+checks);}
    private void command(String text){if(!Bukkit.dispatchCommand(Bukkit.getConsoleSender(),text))throw new AssertionError("missing command "+text);}
    private void request(String name){getLogger().info("KG-BANCRASH REQUEST LOGIN "+name);}
    private YamlConfiguration disk(){return YamlConfiguration.loadConfiguration(data.toFile());}
    private String pending(String name){return "pendingAnimatedBans."+name.toLowerCase(java.util.Locale.ROOT);}
    private boolean hasPending(String name){return disk().contains(pending(name));}
    @SuppressWarnings("unchecked") private boolean active(Player p) throws Exception {
        Field f=core.getModeration().getClass().getDeclaredField("pendingBanActions");f.setAccessible(true);
        return ((Map<UUID,Runnable>)f.get(core.getModeration())).containsKey(p.getUniqueId());
    }
    private void link() throws Exception { Files.move(data,saved);Files.createSymbolicLink(data,saved.getFileName()); }
    private void restore() {try{if(Files.isSymbolicLink(data)){Files.delete(data);Files.move(saved,data);}}catch(Exception error){throw new RuntimeException(error);}}
    private void begin() {
        if(phase==1)request("CrashVictim");
        else if(phase==2){
            ok("crash_name".equals(core.getStore().getActiveBanReason("CrashVictim")) && !hasPending("CrashVictim"),"SIGKILL recovery completes the pending name ban exactly once");
            ok(core.getStore().getPunishHistory("CrashVictim").size()==1,"recovered name ban has one history record");
            request("CrashVictim");
        } else if(phase==3){
            ok("crash_ip".equals(core.getStore().getActiveBanReason("IpVictim")) && !hasPending("IpVictim"),"SIGKILL recovery completes pending IP-ban name action");
            ok(core.getStore().getActiveIpBanReason("127.0.0.1")!=null,"immediate IP block survives the crash");
            ok(core.getStore().getPunishHistory("IpVictim").size()==1,"recovered IP ban has one history record");
            request("IpVictim");
        } else if(phase==4){
            ok(core.getStore().getActiveBanReason("IpVictim")==null && !hasPending("IpVictim")
                    && core.getStore().getActiveIpBanReason("127.0.0.1")==null,"cancelled ban remains cancelled after SIGKILL and restart");
            request("IpVictim");
        } else throw new AssertionError("unknown phase");
    }
    @EventHandler(priority=EventPriority.MONITOR) public void login(PlayerLoginEvent event){
        String name=event.getPlayer().getName();
        if((phase==2 && joins==0 && name.equals("CrashVictim")) || (phase==3 && joins==0 && name.equals("IpVictim"))){
            try {
                ok(event.getResult()==PlayerLoginEvent.Result.KICK_BANNED,"real login is refused by the recovered ban");
                joins++;
                later(3,()->{
                    command((phase==2?"unban CrashVictim":"unbanip IpVictim"));
                    ok(core.getStore().getActiveBanReason(name)==null,"console unban removes recovered ban");
                    request(phase==2?"CrashVictim":"IpVictim");
                });
            }catch(Throwable error){fail(error);}
        }
    }
    @EventHandler public void join(PlayerJoinEvent event){
        Player p=event.getPlayer();
        if(phase==1 && p.getName().equals("CrashVictim"))later(25,()->{
            p.setWalkSpeed(.14f);p.setFlySpeed(.05f);
            p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,2000,0,false,false,true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,2000,1,false,false,true));
            p.saveData();
            command("ban CrashVictim 1h crash_name");
            ok(p.isOnline() && core.getStore().getActiveBanReason("CrashVictim")==null,"online name ban waits for animation");
            ok(hasPending("CrashVictim") && "crash_name".equals(disk().getString(pending("CrashVictim")+".reason")),"pending name ban is committed before animation");
            ok(active(p),"name-ban animation actually started before SIGKILL");
            later(60,()->{
                ok(p.getWalkSpeed()==0f && p.getFlySpeed()==0f && p.getVehicle()!=null && p.hasPotionEffect(PotionEffectType.INVISIBILITY),
                        "player is actually frozen and seated before the crash");
                p.saveData(); // Emulate an autosave while the temporary state is active.
                finish();
            });
        });
        else if(phase==2 && p.getName().equals("CrashVictim"))later(15,()->{
            PotionEffect inv=p.getPotionEffect(PotionEffectType.INVISIBILITY),res=p.getPotionEffect(PotionEffectType.RESISTANCE);
            ok(Math.abs(p.getWalkSpeed()-.14f)<.001 && Math.abs(p.getFlySpeed()-.05f)<.001
                    && inv!=null && inv.getAmplifier()==0 && inv.hasIcon() && !inv.isAmbient()
                    && res!=null && res.getAmplifier()==1 && res.hasIcon() && !res.isAmbient(),
                    "after crash and unban, prior player speeds and effects are restored from saved player state");
            request("IpVictim");
        });
        else if(phase==2 && p.getName().equals("IpVictim"))later(25,()->{
            command("banip IpVictim 1h crash_ip");
            ok(p.isOnline() && core.getStore().getActiveBanReason("IpVictim")==null,"IP-ban name action still waits for animation");
            ok(core.getStore().getActiveIpBanReason("127.0.0.1")!=null && hasPending("IpVictim"),"IP block and pending name action are persisted together");
            ok(active(p),"IP-ban animation actually started before SIGKILL");finish();
        });
        else if(phase==3 && joins>0 && p.getName().equals("IpVictim"))later(25,()->{
            command("ban IpVictim 1h cancelled_crash");
            ok(hasPending("IpVictim") && active(p),"new name-ban animation has a durable pending record");
            command("unban IpVictim");
            ok(!hasPending("IpVictim") && !active(p) && p.isOnline(),"unban cancels both journal and animation before crash");
            // A normal player can intentionally have slow speeds and these effects.
            // They must not be wiped on an unrelated later login or unban portal.
            p.setWalkSpeed(.03f);p.setFlySpeed(.01f);
            p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,2000,2,false,false,true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,2000,3,false,false,true));
            later(85,()->{
                ok(Math.abs(p.getWalkSpeed()-.03f)<.001 && Math.abs(p.getFlySpeed()-.01f)<.001
                        && p.getPotionEffect(PotionEffectType.INVISIBILITY)!=null
                        && p.getPotionEffect(PotionEffectType.INVISIBILITY).getAmplifier()==2
                        && p.getPotionEffect(PotionEffectType.RESISTANCE)!=null
                        && p.getPotionEffect(PotionEffectType.RESISTANCE).getAmplifier()==3,
                        "unban portal does not clear unrelated effects or reset custom speed");
                p.saveData();finish();
            });
        });
        else if(phase==4 && p.getName().equals("IpVictim")){
            if(joins++==0)later(25,()->{
                ok(Math.abs(p.getWalkSpeed()-.03f)<.001 && Math.abs(p.getFlySpeed()-.01f)<.001
                        && p.getPotionEffect(PotionEffectType.INVISIBILITY)!=null
                        && p.getPotionEffect(PotionEffectType.INVISIBILITY).getAmplifier()==2
                        && p.getPotionEffect(PotionEffectType.RESISTANCE)!=null
                        && p.getPotionEffect(PotionEffectType.RESISTANCE).getAmplifier()==3,
                        "ordinary restart and login preserve unrelated slow speeds and effects");
                failureTests(p);
            });
            else later(10,()->{ok(p.isOnline(),"player can reconnect after durable retry and unban");finish();});
        }
    }
    private void failureTests(Player p) throws Exception {
        link();command("ban IpVictim 1h refused_save");restore();
        ok(!hasPending("IpVictim") && !active(p) && core.getStore().getActiveBanReason("IpVictim")==null,
                "failed initial save starts neither ban nor animation");
        command("ban IpVictim 1h failed_cancel");
        ok(hasPending("IpVictim") && active(p),"ban can be retried after fixing storage");
        link();command("unban IpVictim");restore();
        ok(hasPending("IpVictim") && active(p),"failed unban save leaves both pending ban and animation intact");
        command("unban IpVictim");
        ok(!hasPending("IpVictim") && !active(p),"successful unban clears both pending ban and animation");
        command("ban IpVictim 1h retry_save");
        ok(hasPending("IpVictim") && active(p),"retry fixture is queued");
        link();p.kickPlayer("test disconnect");
        later(5,()->{
            restore();
            ok(hasPending("IpVictim") && core.getStore().getActiveBanReason("IpVictim")==null,"failed completion keeps the on-disk intent");
            later(220,()->{
                ok("retry_save".equals(core.getStore().getActiveBanReason("IpVictim")) && !hasPending("IpVictim"),"automatic retry commits the ban after storage is restored");
                command("unban IpVictim");request("IpVictim");
            });
        });
    }
}
