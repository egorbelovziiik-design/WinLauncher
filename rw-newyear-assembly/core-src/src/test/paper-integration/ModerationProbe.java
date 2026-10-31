package net.kinggrief.probe;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kinggrief.core.KingGriefCore;
import net.kinggrief.core.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Display;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;

/** Real protocol login, moderation commands, animations, and offline/IP-ban regression. */
public final class ModerationProbe extends JavaPlugin implements Listener {
    private KingGriefCore core;private Player player;private int stage,checks,errors;private boolean finished;
    @Override public void onEnable(){core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");Bukkit.getPluginManager().registerEvents(this,this);}
    @EventHandler public void join(PlayerJoinEvent e){
        if(!"CaseTester".equals(e.getPlayer().getName()))return;player=e.getPlayer();
        if(stage==0){stage=1;later(25,this::begin);}else if(stage==2){stage=3;later(25,this::ipBanOnline);}
        else if(stage==4){stage=5;later(25,this::cancelAndQuit);}
        else if(stage==6){stage=7;later(25,this::shutdownDuringBan);}
    }
    private interface CheckedRunnable { void run() throws Exception; }
    private void later(long ticks,CheckedRunnable run){Bukkit.getScheduler().runTaskLater(this,()->{try{run.run();}catch(Throwable e){fail(e);}},ticks);}
    private void ok(boolean value,String text){checks++;if(!value)throw new AssertionError(text);getLogger().info("KG-MOD PASS "+text);}
    private void fail(Throwable e){errors++;getLogger().severe("KG-MOD EXCEPTION "+e);e.printStackTrace();finish();}
    private void finish(){if(finished)return;finished=true;getLogger().info("KG-MOD RESULT "+(errors==0?"PASS":"FAIL")+" checks="+checks+" errors="+errors);Bukkit.shutdown();}
    private void command(String text){if(!Bukkit.dispatchCommand(Bukkit.getConsoleSender(),text))throw new AssertionError("command not dispatched: "+text);}
    @SuppressWarnings("unchecked") private Map<UUID,Map<Block,BlockData>> portals() throws Exception {
        Field f=core.getModeration().getClass().getDeclaredField("activeBanAnimPortals");f.setAccessible(true);
        return (Map<UUID,Map<Block,BlockData>>)f.get(core.getModeration());
    }
    private Map<Block,BlockData> portalSnapshot(UUID id) throws Exception {
        Map<Block,BlockData> original=portals().get(id);if(original==null||original.isEmpty())throw new AssertionError("portal not built");
        Map<Block,BlockData> copy=new HashMap<>();for(Map.Entry<Block,BlockData> e:original.entrySet())copy.put(e.getKey(),e.getValue().clone());return copy;
    }
    private boolean restored(Map<Block,BlockData> saved){for(Map.Entry<Block,BlockData> e:saved.entrySet())if(!e.getKey().getBlockData().equals(e.getValue()))return false;return true;}
    private void begin() throws Exception {
        ok(core.getSpawnEngine().isSpawnActive() && core.getSpawnEngine().isInSpawnWorld(player.getLocation()),"moderation target is on prepared spawn");
        command("ban domep444543 1h fixture");
        ok(core.getStore().getActiveBanReason("domep444543")!=null,"plain console ban by offline nickname commits immediately");
        command("unban domep444543");
        ok(core.getStore().getActiveBanReason("domep444543")==null,"plain console unban removes the offline ban");
        // Avoid overlap with the console-unban animation before testing another portal.
        later(125,this::mute);
    }
    private void mute(){
        command("mute CaseTester 1h fixture");
        ok(core.getStore().getMuteRemainingSec("CaseTester")>0,"mute is applied on spawn");
        long parts=player.getWorld().getEntities().stream().filter(e->e.getScoreboardTags().contains("kg_santa_model_part")).count();
        boolean onlyDisplays=player.getWorld().getEntities().stream().filter(e->e.getScoreboardTags().contains("kg_santa_model_part")).allMatch(e->e instanceof Display);
        ok(parts>=10 && onlyDisplays && !player.hasPotionEffect(PotionEffectType.INVISIBILITY),"mute Santa is a separate display model, not a mob or armor stand, and target stays visible");
        later(125,()->{
            ok(core.getModeration().hasMuteGag(player.getUniqueId()),"mute animation leaves the gag");
            command("unmute CaseTester");ok(core.getStore().getMuteRemainingSec("CaseTester")==0,"unmute is applied");
            later(115,()->{ok(!core.getModeration().hasMuteGag(player.getUniqueId()),"unmute animation removes gag");try{banOnline(false);}catch(Exception e){fail(e);}});
        });
    }
    private void banOnline(boolean ip) throws Exception {
        player.removePotionEffect(PotionEffectType.INVISIBILITY);String name=player.getName();
        String address=core.getStore().getPlayerIp(player);
        command((ip?"banip ":"ban ")+name+" 1h fixture");
        ok(player.isOnline() && core.getStore().getActiveBanReason(name)==null,"name ban waits for "+(ip?"IP ":"")+"animation");
        if(ip)ok(core.getStore().getActiveIpBanReason(address)!=null,"online IP block takes effect immediately");
        ok(!player.hasPotionEffect(PotionEffectType.INVISIBILITY),"target remains visible before entering the sack");
        Map<Block,BlockData> saved=portalSnapshot(player.getUniqueId());
        Player target=player;
        new BukkitRunnable(){int ticks;boolean sackSeen,moved,vanished;Location firstSack;
            @Override public void run(){try{
                ticks++;
                if(target.isOnline() && target.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                    Entity vehicle=target.getVehicle();
                    if(vehicle!=null && vehicle.getScoreboardTags().contains("kg_sack")) {
                        if(!sackSeen)firstSack=vehicle.getLocation().clone();
                        sackSeen=true;
                        if(vehicle.getLocation().distanceSquared(firstSack)>1.0)moved=true;
                        if(target.getLocation().distanceSquared(vehicle.getLocation())>9.0)
                            throw new AssertionError("passenger did not move with the sack");
                    } else {
                        // The final portal intentionally removes the entire actor
                        // five ticks before the delayed name ban and kick.
                        Field tf=core.getModeration().getClass().getDeclaredField("activeBanAnimTasks");tf.setAccessible(true);
                        Object task=((Map<?,?>)tf.get(core.getModeration())).get(target.getUniqueId());
                        if(task==null)throw new AssertionError("sack disappeared without an animation task");
                        Field phase=task.getClass().getDeclaredField("phase"),disappeared=task.getClass().getDeclaredField("vanishedInPortal");
                        phase.setAccessible(true);disappeared.setAccessible(true);
                        if(!sackSeen || phase.getInt(task)!=3 || !disappeared.getBoolean(task))
                            throw new AssertionError("target became invisible without riding the sack before portal entry");
                        vanished=true;
                    }
                    if(core.getStore().getActiveBanReason(name)!=null)throw new AssertionError("name ban applied before animation finished");
                }
                if(core.getStore().getActiveBanReason(name)!=null){
                    cancel();ok(sackSeen,"target enters the sack before the name ban");
                    ok(moved,"sack transports the mounted player across the ground");
                    ok(vanished,"actor and sack vanish only inside the portal");
                    later(5,()->{ok(!target.isOnline(),"target kicked after animation");ok(restored(saved),"ban portal restores its original blocks");
                        try{if(ip)afterIpBan(address);else afterNameBan(address);}catch(Exception e){fail(e);}});return;
                }
                if(ticks>320)throw new AssertionError("ban animation timed out");
            }catch(Throwable e){try{cancel();}catch(Throwable ignored){}fail(e);}}
        }.runTaskTimer(this,1L,1L);
    }
    private Map<Block,BlockData> currentUnbanPortal() throws Exception {
        for(Map<Block,BlockData> value:portals().values())if(!value.isEmpty())return new HashMap<>(value);
        return null; // No viewers or an unloaded footprint: animation may be skipped.
    }
    private void afterNameBan(String address) throws Exception {
        command("unban CaseTester");ok(core.getStore().getActiveBanReason("CaseTester")==null,"name unban removes completed ban");
        Map<Block,BlockData> saved=currentUnbanPortal();
        later(125,()->{
            if(saved!=null)ok(restored(saved),"unban portal restores its original blocks");
            command("banip CaseTester 1h fixture");
            ok(core.getStore().getActiveIpBanReason(address)!=null && core.getStore().getActiveBanReason("CaseTester")!=null,"offline banip immediately records both IP and name bans");
            command("unbanip CaseTester");
            ok(core.getStore().getActiveIpBanReason(address)==null && core.getStore().getActiveBanReason("CaseTester")==null,"unbanip removes offline IP and name bans");
            Map<Block,BlockData> ipUnban=currentUnbanPortal();
            later(125,()->{if(ipUnban!=null)ok(restored(ipUnban),"IP-unban portal restores its original blocks");stage=2;getLogger().info("KG-MOD REQUEST RECONNECT");});
        });
    }
    private void ipBanOnline(){try{banOnline(true);}catch(Exception e){fail(e);}}
    private void afterIpBan(String address){
        command("unbanip CaseTester");
        ok(core.getStore().getActiveIpBanReason(address)==null && core.getStore().getActiveBanReason("CaseTester")==null,"online IP and name bans can be removed");
        command("banip 203.0.113.201 1h fixture");
        ok(core.getStore().getActiveIpBanReason("203.0.113.201")!=null,"literal IP ban is recorded without an online target");
        command("unbanip 203.0.113.201");
        ok(core.getStore().getActiveIpBanReason("203.0.113.201")==null,"literal IP unban removes its block");
        stage=4;getLogger().info("KG-MOD REQUEST RECONNECT");
    }
    private void cancelAndQuit() throws Exception {
        String address=core.getStore().getPlayerIp(player);
        PotionEffectType resistance=ColorUtil.potType("resistance","DAMAGE_RESISTANCE");
        player.setWalkSpeed(.14f);player.setFlySpeed(.05f);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,1000,0,false,false,true));
        player.addPotionEffect(new PotionEffect(resistance,1000,1,false,false,true));
        command("ban CaseTester 1h cancelled_fixture");
        ok(!player.hasPotionEffect(PotionEffectType.INVISIBILITY),"pre-existing invisibility is hidden until the target enters the sack");
        Map<Block,BlockData> saved=portalSnapshot(player.getUniqueId());
        command("banip CaseTester 1h duplicate_fixture");
        ok(core.getStore().getActiveIpBanReason(address)==null,"duplicate ban cannot replace a pending animation");
        command("unban CaseTester");
        ok(core.getStore().getActiveBanReason("CaseTester")==null && restored(saved),"explicit unban cancels pending action and restores its portal");
        ok(Math.abs(player.getWalkSpeed()-.14f)<.001 && Math.abs(player.getFlySpeed()-.05f)<.001
                && player.hasPotionEffect(PotionEffectType.INVISIBILITY) && player.getPotionEffect(resistance)!=null
                && player.getPotionEffect(resistance).getAmplifier()==1,"cancelled animation restores prior speed and potion effects");
        later(125,()->{
            ok(player.isOnline() && core.getStore().getActiveBanReason("CaseTester")==null,"cancelled animation cannot re-ban the player later");
            command("banip CaseTester 1h ip_cancel_fixture");
            Map<Block,BlockData> ipPortal=portalSnapshot(player.getUniqueId());
            ok(core.getStore().getActiveIpBanReason(address)!=null,"IP block exists while IP-ban animation is pending");
            command("unbanip "+address);
            ok(core.getStore().getActiveIpBanReason(address)==null,"literal IP unban cancels the immediate block");
            later(125,()->{
                ok(player.isOnline() && core.getStore().getActiveBanReason("CaseTester")==null && restored(ipPortal),"literal IP unban cancels pending name action and both portals restore");
                command("ban CaseTester 1h quit_fixture");
                Map<Block,BlockData> quitPortal=portalSnapshot(player.getUniqueId());
                getLogger().info("KG-MOD REQUEST DISCONNECT");
                later(30,()->{
                    ok(!player.isOnline() && "quit_fixture".equals(core.getStore().getActiveBanReason("CaseTester")),"disconnect ends animation and commits the pending ban");
                    ok(restored(quitPortal),"disconnect restores portal blocks");
                    command("unban CaseTester");
                    later(125,()->{stage=6;getLogger().info("KG-MOD REQUEST RECONNECT");});
                });
            });
        });
    }
    private void shutdownDuringBan() throws Exception {
        command("ban CaseTester 1h shutdown_fixture");
        Map<Block,BlockData> saved=portalSnapshot(player.getUniqueId());
        ok(core.getStore().getActiveBanReason("CaseTester")==null,"shutdown fixture remains pending during animation");
        Bukkit.getPluginManager().disablePlugin(core);
        ok("shutdown_fixture".equals(core.getStore().getActiveBanReason("CaseTester")),"orderly disable commits the interrupted pending ban");
        ok(restored(saved),"orderly disable restores portal blocks");
        File data=new File(core.getDataFolder(),"data.yml");
        ok("shutdown_fixture".equals(YamlConfiguration.loadConfiguration(data).getString("bans.casetester.reason")),"pending ban was saved to disk on disable");
        finish();
    }
}
