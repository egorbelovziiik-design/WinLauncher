package net.kinggrief.probe;

import net.kinggrief.core.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Stall/late callback tests plus real asynchronous Paper chunk generation and teleport. */
public final class RtpProbe extends JavaPlugin implements Listener {
    private final Map<String,Player> players=new HashMap<String,Player>();
    private final List<Stall> stalls=new ArrayList<Stall>();
    private KingGriefCore core; private Object manager,originalLoader; private Field loaderField;
    private World target; private Player a,b,c; private Location origin;
    private int checks,errors,requests; private boolean started,finished,cancelFinalTeleport; private Boolean lastGenerate;
    private DataStore.Claim protectedSite;
    private static final class Stall extends CompletableFuture<Object>{
        boolean cancelAttempted;
        @Override public boolean cancel(boolean mayInterrupt){cancelAttempted=true;return false;}
    }
    @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);}
    @EventHandler public void join(PlayerJoinEvent e){
        players.put(e.getPlayer().getName(),e.getPlayer());
        if(players.size()==3 && !started){started=true;later(40,()->begin());}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void teleport(PlayerTeleportEvent e){
        if(cancelFinalTeleport && e.getPlayer().equals(a) && e.getTo()!=null && e.getTo().getWorld().equals(target)){
            e.setCancelled(true);cancelFinalTeleport=false;
        }
    }
    private void later(long delay,Runnable r){Bukkit.getScheduler().runTaskLater(this,()->{try{r.run();}catch(Throwable t){fail(t);}},delay);}
    private void ok(boolean v,String m){checks++;if(!v){errors++;getLogger().severe("KG-RTP FAIL: "+m);}}
    private void fail(Throwable t){errors++;getLogger().severe("KG-RTP EXCEPTION: "+t);t.printStackTrace();finish();}
    private void finish(){if(finished)return;finished=true;getLogger().info("KG-RTP RESULT "+(errors==0?"PASS":"FAIL")+" checks="+checks+" errors="+errors);}
    private Field f(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f;}
    @SuppressWarnings("unchecked") private Map<UUID,Object> jobs()throws Exception{return (Map<UUID,Object>)f(manager,"jobs").get(manager);}
    private void start(Player p,String mode){core.getSpawnEngine().performRtp(p,mode);}
    private void installStall()throws Exception{
        Class<?> iface=loaderField.getType();
        Object fake=Proxy.newProxyInstance(iface.getClassLoader(),new Class<?>[]{iface},(proxy,method,args)->{
            if("request".equals(method.getName())){requests++;lastGenerate=(Boolean)args[3];Stall future=new Stall();stalls.add(future);return future;}
            if("toString".equals(method.getName()))return "stalled test loader";
            return null;
        });
        loaderField.set(manager,fake);
    }
    private void begin(){
        try{
            core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");manager=f(core,"rtpManager").get(core);
            loaderField=f(manager,"chunkLoader");originalLoader=loaderField.get(manager);installStall();
            a=players.get("RtpTesterA");b=players.get("RtpTesterB");c=players.get("RtpTesterC");
            target=Bukkit.getWorlds().get(0);origin=a.getLocation().clone();
            start(a,"high");start(a,"near");ok(jobs().isEmpty(),"rank gating denies high/near for normal player");
            start(a,"nether");ok(jobs().isEmpty(),"missing Nether fails without creating a world or falling back");
            start(a,"close");start(b,"close");start(c,"close");
            ok(jobs().size()==2 && !jobs().containsKey(c.getUniqueId()),"global concurrent-search cap");
            later(30,()->{
                try{
                    ok(requests==2 && stalls.size()==2,"two stalled asynchronous requests started without blocking heartbeat");
                    start(a,"close");ok(jobs().size()==2 && requests==2,"duplicate player search refused");
                    a.teleport(origin.clone().add(5,0,0));start(b,"cancel");
                    later(15,()->afterCancel());
                }catch(Throwable t){fail(t);}
            });
        }catch(Throwable t){fail(t);}
    }
    private void afterCancel(){
        try{
            ok(jobs().isEmpty() && stalls.get(0).cancelAttempted && stalls.get(1).cancelAttempted,"movement and explicit cancel stop both jobs");
            Location moved=a.getLocation().clone(), stayed=b.getLocation().clone();
            for(Stall s:stalls)s.complete(new Object());
            later(5,()->{
                try{
                    ok(a.getLocation().distanceSquared(moved)<.1 && b.getLocation().distanceSquared(stayed)<.1,"late callbacks cannot teleport players");
                    a.teleport(origin); start(a,"close");
                    later(15,()->{
                        try{
                            Object job=jobs().get(a.getUniqueId());ok(job!=null,"timeout fixture started");
                            f(job,"requestedAt").setLong(job,System.nanoTime()-TimeUnit.SECONDS.toNanos(21));
                            later(15,()->afterTimeout());
                        }catch(Throwable t){fail(t);}
                    });
                }catch(Throwable t){fail(t);}
            });
        }catch(Throwable t){fail(t);}
    }
    private void afterTimeout(){
        try{
            ok(jobs().isEmpty() && stalls.get(stalls.size()-1).cancelAttempted,"stalled chunk timeout cancels search");
            stalls.get(stalls.size()-1).complete(new Object());
            int previous=requests;core.getConfig().set("rtp.min-free-heap-mb",4096);start(a,"close");
            later(15,()->{
                try{
                    ok(jobs().isEmpty() && requests==previous,"low heap refuses chunk generation before loader request");
                    core.getConfig().set("rtp.min-free-heap-mb",0);
                    core.getConfig().set("rtp.allow-chunk-generation",false);start(a,"close");
                    later(15,()->{try{
                        ok(requests>previous && Boolean.FALSE.equals(lastGenerate),"generation-disabled RTP requests only previously generated chunks");
                        start(a,"cancel");for(Stall stall:stalls) if(!stall.isDone())stall.complete(new Object());
                        core.getConfig().set("rtp.allow-chunk-generation",true);final int borderRequests=requests;
                        target.getWorldBorder().setSize(32);start(a,"close");
                        later(20,()->{
                            try{
                                ok(requests==borderRequests,"outside world-border candidates never request chunks");start(a,"cancel");target.getWorldBorder().setSize(59999968);
                                realSearch();
                            }catch(Throwable t){fail(t);}
                        });
                    }catch(Throwable t){fail(t);}});
                }catch(Throwable t){fail(t);}
            });
        }catch(Throwable t){fail(t);}
    }
    private void realSearch(){
        try{
            Random rng=(Random)f(manager,"random").get(manager);rng.setSeed(12345L);Random predictor=new Random(12345L);
            double angle=predictor.nextDouble()*Math.PI*2,dist=300+predictor.nextDouble()*900;
            Location center=target.getWorldBorder().getCenter();
            int x=(int)Math.round(center.getX()+Math.cos(angle)*dist),z=(int)Math.round(center.getZ()+Math.sin(angle)*dist);
            protectedSite=core.getStore().createClaim("Somebody",new Location(target,x,65,z),4);
            Class<?> iface=loaderField.getType();
            Object counting=Proxy.newProxyInstance(iface.getClassLoader(),new Class<?>[]{iface},(proxy,method,args)->{
                if("request".equals(method.getName())){requests++;method.setAccessible(true);return method.invoke(originalLoader,args);}
                if("toString".equals(method.getName()))return "real counting loader";return null;
            });
            loaderField.set(manager,counting);int previous=requests;
            start(a,"close");pollReal(previous,0);
        }catch(Throwable t){fail(t);}
    }
    private void pollReal(int previous,int ticks){
        later(20,()->{
            try{
                if(jobs().containsKey(a.getUniqueId()) && ticks<1200){pollReal(previous,ticks+20);return;}
                ok(!jobs().containsKey(a.getUniqueId()) && a.getWorld().equals(target),"real Paper async search teleports to survival world");
                ok(requests-previous>=2 && !protectedSite.contains(a.getLocation()),"claimed first candidate rejected before teleport");
                if(a.getWorld().equals(target)){
                    Location at=a.getLocation(),mid=target.getWorldBorder().getCenter();
                    double distance=Math.hypot(at.getX()-mid.getX(),at.getZ()-mid.getZ());
                    ok(distance>=299 && distance<=1202 && at.getBlock().isPassable() && at.clone().add(0,1,0).getBlock().isPassable(),"arrival in requested annulus with two clear blocks");
                    ok(core.getStore().getBack(a)!=null && core.getStore().getBack(a).getWorld().equals(origin.getWorld()),"back location recorded after successful teleport");
                }
                a.teleport(origin);later(15,()->testCancelledTeleport());
            }catch(Throwable t){fail(t);}
        });
    }
    private void testCancelledTeleport(){
        try{
            Location sentinel=origin.clone().add(2,0,0);core.getStore().setBack(a,sentinel);cancelFinalTeleport=true;
            int previous=requests;start(a,"close");pollCancelled(previous,0,sentinel);
        }catch(Throwable t){fail(t);}
    }
    private void pollCancelled(int previous,int ticks,Location sentinel){
        later(20,()->{
            try{
                if(jobs().containsKey(a.getUniqueId()) && ticks<1200){pollCancelled(previous,ticks+20,sentinel);return;}
                ok(requests>previous && !cancelFinalTeleport && a.getWorld().equals(origin.getWorld()),"protection plugin can cancel actual async RTP teleport");
                ok(core.getStore().getBack(a).distanceSquared(sentinel)<.01,"cancelled teleport does not overwrite /back");
                core.getStore().removeClaim(protectedSite);
                finish();
            }catch(Throwable t){fail(t);}
        });
    }
}
