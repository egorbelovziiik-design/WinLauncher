package net.kinggrief.probe;

import net.kinggrief.core.DataStore;
import net.kinggrief.core.GuiManager;
import net.kinggrief.core.ItemFactory;
import net.kinggrief.core.KingGriefCore;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Real protocol clicks, failure injection, privacy and a kill/restart. */
public final class V710Probe extends JavaPlugin implements Listener {
    private KingGriefCore core; private DataStore store; private Player a,b; private Plugin points;
    private int checks,clicks; private boolean started,finished,cancelSpeed,cancelWither;
    private Path dataFile,savedFile;
    private final String[] pets={"cat","panda","bee","turtle","frog","ice_golem"};
    private final int[] sizes={14,14,13,11,11,13};
    @Override public void onEnable(){
        core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");store=core.getStore();
        points=Bukkit.getPluginManager().getPlugin("PlayerPoints");
        dataFile=core.getDataFolder().toPath().resolve("data.yml");savedFile=dataFile.resolveSibling("v710-probe-backup.yml");
        Bukkit.getPluginManager().registerEvents(this,this);
    }
    @EventHandler public void join(PlayerJoinEvent event){
        if("WheelOne".equals(event.getPlayer().getName()))a=event.getPlayer();
        if("WheelTwo".equals(event.getPlayer().getName()))b=event.getPlayer();
        if(!started&&a!=null&&b!=null){started=true;later(100,()->{if(getConfig().getInt("phase",1)==1)begin();else restart();});}
    }
    @EventHandler(priority=EventPriority.MONITOR) public void clicks(InventoryClickEvent event){if(a!=null&&event.getWhoClicked().equals(a))clicks++;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void potion(EntityPotionEffectEvent event){
        if(a==null||!event.getEntity().equals(a))return;
        if(cancelSpeed&&event.getNewEffect()!=null&&event.getNewEffect().getType().equals(PotionEffectType.SPEED))event.setCancelled(true);
        if(cancelWither&&event.getOldEffect()!=null&&event.getOldEffect().getType().equals(PotionEffectType.WITHER)&&event.getNewEffect()==null)event.setCancelled(true);
    }
    private void later(long ticks,Runnable task){Bukkit.getScheduler().runTaskLater(this,()->{try{task.run();}catch(Throwable e){fail(e);}},ticks);}
    private void ok(boolean value,String text){if(!value)throw new AssertionError(text);checks++;getLogger().info("KG-V710 PASS "+text);}
    private void fail(Throwable error){if(finished)return;finished=true;restore();getLogger().severe("KG-V710 RESULT FAIL "+error);error.printStackTrace();}
    private void finish(){if(finished)return;finished=true;restore();getLogger().info("KG-V710 RESULT PASS checks="+checks);}
    private void click(int slot,Runnable after){click(slot,0,0,after);}
    private void click(int slot,int button,int mode,Runnable after){
        int expected=clicks+1;later(8,()->{
            getLogger().info("KG-V710 REQUEST CLICK "+slot+" "+button+" "+mode);
            later(20,()->{ok(clicks>=expected,"real protocol action reached server: "+slot+"/"+button+"/"+mode);after.run();});
        });
    }
    private void link(){try{Files.move(dataFile,savedFile);Files.createSymbolicLink(dataFile,savedFile.getFileName());}catch(Exception e){throw new RuntimeException(e);}}
    private void restore(){try{if(Files.isSymbolicLink(dataFile)){Files.delete(dataFile);Files.move(savedFile,dataFile);}}catch(Exception e){throw new RuntimeException(e);}}
    @SuppressWarnings("unchecked") private <T>T field(Object object,String name){try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return (T)f.get(object);}catch(Exception e){throw new RuntimeException(e);}}
    private YamlConfiguration data(){return field(store,"yml");}
    private String base(Player p){return "players."+p.getName().toLowerCase(Locale.ROOT);}
    private double coins(){return store.getCoins(a.getName());}
    private void pointsMode(String mode){try{points.getClass().getMethod("mode",String.class).invoke(points,mode);}catch(Exception e){throw new RuntimeException(e);}}
    private int pointsBalance(Player player){try{return (Integer)points.getClass().getMethod("balance",UUID.class).invoke(points,player.getUniqueId());}catch(Exception e){throw new RuntimeException(e);}}
    private void clearEffects(){cancelSpeed=false;cancelWither=false;for(PotionEffect e:new ArrayList<PotionEffect>(a.getActivePotionEffects()))a.removePotionEffect(e.getType());}
    private void apply(PotionEffectType type,int duration,int amp){a.addPotionEffect(new PotionEffect(type,duration,amp));}
    private boolean has(PotionEffectType type,int amp){PotionEffect e=a.getPotionEffect(type);return e!=null&&e.getAmplifier()==amp;}
    private ItemStack potionItem(int amount){
        ItemStack item=new ItemStack(Material.POTION,amount);PotionMeta meta=(PotionMeta)item.getItemMeta();
        meta.addCustomEffect(new PotionEffect(PotionEffectType.SPEED,2000,1),true);item.setItemMeta(meta);return item;
    }
    private Inventory top(){return a.getOpenInventory().getTopInventory();}
    private void begin(){
        ok(core.getDescription().getVersion().equals("7.10.0"),"new Core version loaded");
        a.setGameMode(GameMode.SURVIVAL);b.setGameMode(GameMode.SURVIVAL);a.getInventory().clear();b.getInventory().clear();clearEffects();
        Location spawn=core.getSpawnEngine().getPreparedSpawnArrival();a.teleport(spawn);b.teleport(spawn.clone().add(2,0,0));
        store.setCoins(a.getName(),1_000_000);store.setRelics(a.getName(),100);
        data().set(base(a)+".playSec",0L);data().set(base(a)+".playRewards",null);store.save();
        a.performCommand("playtime");
        int[] slots={11,12,13,14,15,20,21,22,23,24};int[] seconds={900,1800,3600,10800,21600,43200,86400,259200,604800,1209600};
        for(int i=0;i<slots.length;i++)ok(("pt_"+seconds[i]).equals(ItemFactory.getCustomId(top().getItem(slots[i]))),"online reward tier "+seconds[i]+" is in the GUI");
        ok(top().getItem(11).getItemMeta().getLore().toString().contains("10 000"),"first tier displays its real 10000-coin award");
        ok(top().getItem(30).getType()==Material.HOPPER,"collect-all control is present");
        click(11,()->{
            ok(coins()==1_000_000&&!store.hasClaimedPlayReward(a.getName(),"pt_900"),"locked playtime reward gives nothing");
            data().set(base(a)+".playSec",900L);click(11,this::firstClaim);
        });
    }
    private void firstClaim(){
        ok(coins()==1_010_000&&store.getRelics(a)==110&&pointsBalance(a)==110&&store.hasClaimedPlayReward(a.getName(),"pt_900"),"single reward grants exactly displayed coins and relics, with marker");
        double before=coins();a.performCommand("playtime claim");ok(coins()==before,"replay does not pay a claimed tier twice");
        data().set(base(a)+".playSec",3600L);store.save();a.performCommand("playtime");link();
        click(12,()->{
            restore();ok(coins()==before&&store.getRelics(a)==110&&!store.hasClaimedPlayReward(a.getName(),"pt_1800"),"failed playtime save rolls back the marker and both local rewards");
            a.performCommand("playtime claim");
            ok(coins()==before+75_000&&store.getRelics(a)==170&&pointsBalance(a)==170
                    &&store.hasClaimedPlayReward(a.getName(),"pt_1800")&&store.hasClaimedPlayReward(a.getName(),"pt_3600"),"claim-all commits both available tiers exactly once");
            double paid=coins();a.performCommand("playtime claim");a.performCommand("playtime status");ok(coins()==paid,"claim-all replay and status do not pay again");
            data().set(base(b)+".playSec",Long.MAX_VALUE-2);store.addPlaySeconds(b.getName(),10);store.addPlaySeconds(b.getName(),10);
            ok(store.getPlaySeconds(b.getName())==Long.MAX_VALUE,"playtime counter saturates instead of overflowing");data().set(base(b)+".playSec",0);
            effectsCancelled();
        });
    }
    private void effectsCancelled(){
        clearEffects();apply(PotionEffectType.STRENGTH,12000,0);double before=coins();cancelSpeed=true;
        a.performCommand("bafovar");click(22,()->{
            cancelSpeed=false;
            ok(coins()==before&&has(PotionEffectType.STRENGTH,0)&&!a.hasPotionEffect(PotionEffectType.SPEED),"cancelled speed rolls back earlier strength and keeps coins");
            clearEffects();link();click(22,()->{
                restore();ok(coins()==before&&!a.hasPotionEffect(PotionEffectType.STRENGTH)&&!a.hasPotionEffect(PotionEffectType.SPEED),"failed effect payment rolls back actual applied effects");
                click(22,()->{
                    ok(coins()==before-12000&&has(PotionEffectType.STRENGTH,1)&&has(PotionEffectType.SPEED,1)
                            &&a.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE)&&a.hasPotionEffect(PotionEffectType.NIGHT_VISION),"combat buff applies its whole package and charges once");
                    double now=coins();click(22,()->{
                        ok(coins()==now,"near-immediate duplicate buff click is free");
                        clearEffects();apply(PotionEffectType.STRENGTH,2000,4);click(22,()->{
                            ok(coins()==now&&has(PotionEffectType.STRENGTH,4)&&!a.hasPotionEffect(PotionEffectType.SPEED),"short stronger effect is never downgraded or charged");
                            clearEffects();click(23,()->{
                                ok(coins()==now-30000&&has(PotionEffectType.STRENGTH,2)&&a.hasPotionEffect(PotionEffectType.ABSORPTION),"royal package has correct price and effects");
                                clearEffects();double next=coins();click(24,()->{
                                    ok(coins()==next-8000&&has(PotionEffectType.HASTE,2)&&has(PotionEffectType.SPEED,2),"miner package has correct price and effects");
                                    clearEffects();cleanse();
                                });
                            });
                        });
                    });
                });
            });
        });
    }
    private void cleanse(){
        double before=coins();click(32,()->{
            ok(coins()==before,"cleansing without negative effects is not charged");
            apply(PotionEffectType.POISON,5000,0);apply(PotionEffectType.WITHER,5000,0);cancelWither=true;
            click(32,()->{
                cancelWither=false;ok(coins()==before&&a.hasPotionEffect(PotionEffectType.POISON)&&a.hasPotionEffect(PotionEffectType.WITHER),"cancelled cleanse restores prior effects without charging");
                link();click(32,()->{
                    restore();ok(coins()==before&&a.hasPotionEffect(PotionEffectType.POISON)&&a.hasPotionEffect(PotionEffectType.WITHER),"failed cleanse save restores both effects");
                    click(32,()->{ok(coins()==before-2500&&!a.hasPotionEffect(PotionEffectType.POISON)&&!a.hasPotionEffect(PotionEffectType.WITHER),"cleanse removes effects and charges once");stacking();});
                });
            });
        });
    }
    private void stacking(){
        clearEffects();a.getInventory().clear();a.getInventory().setItem(0,potionItem(1));a.getInventory().setItem(1,potionItem(1));
        double before=coins();link();click(30,()->{
            restore();ok(coins()==before&&a.getInventory().getItem(0).getAmount()==1&&a.getInventory().getItem(1).getAmount()==1,"failed stack payment leaves both input potions intact");
            click(30,()->{
                ok(coins()==before-5000&&a.getInventory().getItem(0).getAmount()==2&&a.getInventory().getItem(1)==null,"identical potions stack after one saved payment");
                double now=coins();click(30,()->{ok(coins()==now,"stacking again without another match is free");brewerStart();});
            });
        });
    }
    private void brewerStart(){
        a.getInventory().clear();a.getInventory().setItem(0,potionItem(1));a.getInventory().setItem(1,new ItemStack(Material.DIAMOND,3));
        a.getInventory().setItemInOffHand(new ItemStack(Material.NETHERITE_INGOT,1));
        click(29,()->{
            ok(top().getHolder() instanceof GuiManager.KgMenuHolder&&"baf_brew".equals(((GuiManager.KgMenuHolder)top().getHolder()).menuType),"brewer opens from the real menu");
            click(72,0,1,()->{
                ok(a.getInventory().getItem(0)!=null&&a.getInventory().getItem(0).getAmount()==1&&top().getItem(10)!=null,"shift-click selects a preview but leaves the real item in inventory");
                click(11,1,2,()->{
                    ok(top().getItem(11)==null&&a.getInventory().getItem(1).getAmount()==3,"number-key cannot insert a diamond into the brewer");
                    click(11,40,2,()->{
                        ok(top().getItem(11)==null&&a.getInventory().getItemInOffHand().getType()==Material.NETHERITE_INGOT,"offhand swap cannot inject an item");
                        click(10,1,4,()->{
                            ok(top().getItem(10)!=null&&a.getInventory().getItem(0)!=null,"drop action cannot remove a preview or ingredient");
                            InventoryClickEvent collect=new InventoryClickEvent(a.getOpenInventory(),InventoryType.SlotType.CONTAINER,72,ClickType.DOUBLE_CLICK,InventoryAction.COLLECT_TO_CURSOR);
                            Bukkit.getPluginManager().callEvent(collect);
                            ok(collect.isCancelled(),"collect-to-cursor is denied even when clicking the bottom inventory");
                            a.setItemOnCursor(potionItem(1));
                            click(72,0,6,()->{
                                ok(top().getItem(10)!=null&&a.getItemOnCursor()!=null&&a.getItemOnCursor().getAmount()==1,"real double-click cannot collect preview clones");
                                a.setItemOnCursor(null);
                                click(10,()->{
                                    ok(top().getItem(10)==null&&a.getInventory().getItem(0)!=null,"normal click removes selection, not the source item");
                                    click(72,0,1,()->click(72,0,1,()->{
                                        ok(top().getItem(10)!=null&&top().getItem(11)==null,"cannot select a single potion twice");
                                        staleRecipe();
                                    }));
                                });
                            });
                        });
                    });
                });
            });
        });
    }
    private void staleRecipe(){
        ItemStack replacement=potionItem(1);PotionMeta meta=(PotionMeta)replacement.getItemMeta();meta.setDisplayName("changed");replacement.setItemMeta(meta);
        a.getInventory().setItem(0,replacement);double before=coins();click(30,()->{
            ok(coins()==before&&a.getInventory().getItem(0).isSimilar(replacement),"stale source metadata refuses brew without charging or consuming");
            a.getInventory().setItem(0,potionItem(1));link();click(30,()->{
                restore();ok(coins()==before&&a.getInventory().getItem(0)!=null&&top().getItem(10)!=null,"failed brew payment preserves source and selection");
                fullBrew();
            });
        });
    }
    private void fullBrew(){
        a.closeInventory();a.getInventory().clear();for(int i=1;i<36;i++)a.getInventory().setItem(i,new ItemStack(Material.DIRT,64));a.getInventory().setItem(0,potionItem(2));
        a.performCommand("bafovar");click(29,()->click(72,0,1,()->{
            double before=coins();click(30,()->{
                ok(coins()==before&&a.getInventory().getItem(0).getAmount()==2,"full inventory refuses a result when the selected stack would not free a slot");
                a.closeInventory();ok(a.getInventory().getItem(0).getAmount()==2,"closing the preview never duplicates or removes inputs");
                a.getInventory().setItem(0,potionItem(1));a.performCommand("bafovar");
                click(29,()->click(72,0,1,()->click(32,()->{
                    ok(coins()==before-5000&&a.getInventory().getItem(0)!=null&&a.getInventory().getItem(0).getType()==Material.SPLASH_POTION,
                            "full inventory can brew when consuming the input frees exactly one slot");
                    petsStart();
                })));
            });
        }));
    }
    @SuppressWarnings("unchecked") private List<Entity> parts(Player player){
        Object manager=field(core,"displayPets");Object pet=((Map<?,?>)field(manager,"pets")).get(player.getUniqueId());
        return pet==null?Collections.emptyList():(List<Entity>)field(pet,"parts");
    }
    private void resetRate(Player player,String field){((Map<?,?>)field(field(core,"displayPets"),field)).remove(player.getUniqueId());}
    private void choose(Player player,String id){resetRate(player,"lastChoice");player.performCommand("pet "+id);}
    private void view(Player player,String mode){resetRate(player,"lastView");player.performCommand("pet view "+mode);}
    private void petsStart(){a.closeInventory();a.getInventory().clear();b.getInventory().clear();cyclePet(0);}
    private void cyclePet(int i){
        if(i==pets.length){privacy();return;}
        List<Entity> old=new ArrayList<Entity>(parts(a));choose(a,pets[i]);later(15,()->{
            ok(parts(a).size()==sizes[i]&&parts(a).stream().allMatch(e->e instanceof BlockDisplay&&!e.isPersistent()&&!e.isVisibleByDefault()),"new "+pets[i]+" model has the correct private nonpersistent parts");
            ok(old.stream().noneMatch(Entity::isValid),"previous model is removed before "+pets[i]);cyclePet(i+1);
        });
    }
    private void privacy(){
        choose(b,"cat");later(15,()->{
            ok(!parts(a).isEmpty()&&!parts(b).isEmpty()&&parts(a).stream().allMatch(b::canSee),"observer normally sees both pets");
            view(b,"none");ok(parts(a).stream().noneMatch(b::canSee)&&parts(b).stream().noneMatch(b::canSee),"none hides both pets immediately");
            view(b,"mine");later(15,()->{
                ok(parts(a).stream().noneMatch(b::canSee)&&parts(b).stream().allMatch(b::canSee),"mine shows only the viewer's own pet");
                view(b,"all");later(15,()->{
                    ok(parts(a).stream().allMatch(b::canSee)&&parts(b).stream().allMatch(b::canSee),"all restores both pets");
                    b.hidePlayer(this,a);ok(parts(a).stream().noneMatch(b::canSee),"hidePlayer immediately hides the owner's pet");
                    b.showPlayer(this,a);later(15,()->{
                        ok(parts(a).stream().allMatch(b::canSee),"pet returns after showPlayer");
                        resetRate(b,"lastView");link();b.performCommand("pet view none");restore();
                        ok("all".equals(data().getString("cosmeticPetView."+b.getUniqueId()))&&parts(a).stream().allMatch(b::canSee),"failed view save keeps previous preference and visibility");
                        petMenu();
                    });
                });
            });
        });
    }
    private void petMenu(){
        a.performCommand("pet");ok(top().getItem(21).getType()==Material.CAT_SPAWN_EGG&&top().getItem(28).getType()==Material.BLUE_ICE,"pet menu includes new models");
        resetRate(a,"lastView");click(30,()->{
            ok("mine".equals(data().getString("cosmeticPetView."+a.getUniqueId())),"real menu toggles viewer preference");
            later(15,()->{ok(parts(b).stream().noneMatch(a::canSee)&&parts(a).stream().allMatch(a::canSee),"mine preference applies to real displays");prepareCrash();});
        });
    }
    private void prepareCrash(){
        a.closeInventory();choose(a,"ice_golem");view(b,"none");
        a.getInventory().clear();a.getInventory().setItem(0,potionItem(1));a.saveData();store.save();
        a.performCommand("bafovar");click(29,()->click(72,0,1,()->{
            ok(top().getItem(10)!=null&&a.getInventory().getItem(0)!=null,"crash fixture leaves selected potion in actual player inventory");
            try{
                YamlConfiguration state=new YamlConfiguration();state.set("coins",coins());state.set("relics",store.getRelics(a));state.save(getDataFolder().toPath().resolve("probe-state.yml").toFile());
            }catch(Exception e){throw new RuntimeException(e);}finish();
        }));
    }
    private void restart(){
        YamlConfiguration state=YamlConfiguration.loadConfiguration(getDataFolder().toPath().resolve("probe-state.yml").toFile());
        ok(coins()==state.getDouble("coins")&&store.getRelics(a)==state.getInt("relics"),"local balances survive SIGKILL and restart");
        ok(store.hasClaimedPlayReward(a.getName(),"pt_900")&&store.hasClaimedPlayReward(a.getName(),"pt_1800")&&store.hasClaimedPlayReward(a.getName(),"pt_3600"),"claimed tiers survive SIGKILL");
        ok(a.getInventory().getItem(0)!=null&&a.getInventory().getItem(0).isSimilar(potionItem(1))&&a.getInventory().getItem(0).getAmount()==1,"unbrewed preview ingredient survives in player inventory without duplicate");
        later(25,()->{
            ok(parts(a).size()==13&&parts(b).size()==14,"selected new pet models return after restart");
            ok("mine".equals(data().getString("cosmeticPetView."+a.getUniqueId()))&&"none".equals(data().getString("cosmeticPetView."+b.getUniqueId())),"both viewer preferences survive restart");
            ok(parts(a).stream().allMatch(a::canSee)&&parts(b).stream().noneMatch(a::canSee)&&parts(a).stream().noneMatch(b::canSee),"restarted displays respect viewer preferences");
            double before=coins();a.performCommand("playtime claim");ok(coins()==before,"restart does not re-award previously claimed tiers");
            data().set(base(a)+".playSec",1209600L);store.save();link();a.performCommand("playtime claim");restore();
            ok(coins()==before&&!store.hasClaimedPlayReward(a.getName(),"pt_10800"),"failed batch save commits no additional tier or currency");
            pointsMode("reject");int beforeRelics=store.getRelics(a);a.performCommand("playtime claim");
            ok(coins()==before+10_220_000&&store.getRelics(a)==beforeRelics+4230&&store.hasClaimedPlayReward(a.getName(),"pt_1209600"),"remaining seven tiers commit their exact combined local rewards");
            ok(pointsBalance(a)==0,"rejected external PlayerPoints sync is separate from the committed local award");
            double total=coins();a.performCommand("playtime claim");ok(coins()==total,"rejected external sync cannot duplicate the local batch");pointsMode("normal");
            a.performCommand("playtime");for(int slot:new int[]{11,12,13,14,15,20,21,22,23,24})ok(top().getItem(slot).getType()==Material.MINECART,"claimed tier remains marked after restart and batch");
            finish();
        });
    }
}
