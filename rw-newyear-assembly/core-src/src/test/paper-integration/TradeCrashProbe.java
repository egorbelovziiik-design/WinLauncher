package net.kinggrief.probe;

import net.kinggrief.core.ItemFactory;
import net.kinggrief.core.KingGriefCore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

/** Drives a real journal to a crash point; the Python harness SIGKILLs Paper and restarts it. */
public final class TradeCrashProbe extends JavaPlugin implements Listener {
    private KingGriefCore core;
    private Player a,b;
    private boolean run;
    private boolean check;
    @Override public void onEnable() {
        core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");
        check=Files.exists(getDataFolder().toPath().resolve("restart"));
        Bukkit.getPluginManager().registerEvents(this,this);
    }
    @EventHandler public void join(PlayerJoinEvent e) {
        if("OrbTester".equals(e.getPlayer().getName())) a=e.getPlayer();
        if("OrbEnemy".equals(e.getPlayer().getName())) b=e.getPlayer();
        if(a!=null && b!=null && !run) {
            run=true;
            Bukkit.getScheduler().runTaskLater(this,() -> {
                try { if(check) verify(); else prepare(); }
                catch(Throwable error) { getLogger().severe("KG-TRADE-CRASH FAIL " + error); error.printStackTrace(); }
            },30L);
        }
    }
    private static Field field(Object object,String name)throws Exception {
        Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f;
    }
    private static Method method(Object object,String name,Class<?>... args)throws Exception {
        Method m=object.getClass().getDeclaredMethod(name,args);m.setAccessible(true);return m;
    }
    private ItemStack custom() {
        ItemStack item=ItemFactory.sharSanty(); ItemMeta meta=item.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey("kg-crash-probe","identity"),PersistentDataType.STRING,"полные-данные");
        meta.setLore(Arrays.asList("§6Уникальная вещь", "§bпосле аварии")); item.setItemMeta(meta);return item;
    }
    private void prepare() throws Exception {
        a.getInventory().clear();b.getInventory().clear();
        ItemStack santa=custom(), diamond=new ItemStack(Material.DIAMOND,12);
        a.getInventory().setItem(0,santa);b.getInventory().setItem(0,diamond);
        a.saveData();b.saveData(); // durable pre-transaction fixture
        Object manager=field(core,"safeTrades").get(core);
        Class<?> journalClass=Class.forName("net.kinggrief.core.SafeTradeManager$Journal",true,core.getClass().getClassLoader());
        Constructor<?> constructor=journalClass.getDeclaredConstructor();constructor.setAccessible(true);
        Object journal=constructor.newInstance();
        UUID id=UUID.randomUUID();
        field(journal,"id").set(journal,id);field(journal,"a").set(journal,a.getUniqueId());field(journal,"b").set(journal,b.getUniqueId());
        ItemStack[] afterA=new ItemStack[36],afterB=new ItemStack[36];afterA[0]=diamond.clone();afterB[0]=santa.clone();
        Method encode=method(manager,"encodeStorage",ItemStack[].class);
        field(journal,"afterA").set(journal,encode.invoke(manager,(Object)afterA));
        field(journal,"afterB").set(journal,encode.invoke(manager,(Object)afterB));
        Path directory=(Path)field(manager,"directory").get(manager);
        Path path=directory.resolve(a.getUniqueId()+"_"+b.getUniqueId()+"_"+id+".trade");
        field(journal,"path").set(journal,path);
        method(manager,"write",journalClass).invoke(manager,journal);
        // Same production step as a trade: A is persisted and marked, B has not yet
        // been updated. A SIGKILL at the marker below reproduces an interrupted commit.
        boolean applied=(Boolean)method(manager,"apply",journalClass,Player.class).invoke(manager,journal,a);
        if(!applied || !Files.exists(path) || !((Boolean)field(journal,"doneA").get(journal))
                || ((Boolean)field(journal,"doneB").get(journal))) throw new AssertionError("failed to reach crash point");
        getLogger().info("KG-TRADE-CRASH READY " + id);
    }
    private void verify() throws Exception {
        ItemStack a0=a.getInventory().getItem(0),b0=b.getInventory().getItem(0);
        if(a0==null || a0.getType()!=Material.DIAMOND || a0.getAmount()!=12) throw new AssertionError("A did not retain completed half: " + a0);
        if(b0==null || !custom().equals(b0)) throw new AssertionError("B was not recovered with exact metadata: " + b0);
        int diamondsA=0, diamondsB=0, santaA=0,santaB=0;
        for(ItemStack i:a.getInventory().getStorageContents()) if(i!=null){if(i.getType()==Material.DIAMOND)diamondsA+=i.getAmount(); if("shar_santy".equals(ItemFactory.getCustomId(i))) santaA+=i.getAmount();}
        for(ItemStack i:b.getInventory().getStorageContents()) if(i!=null){if(i.getType()==Material.DIAMOND)diamondsB+=i.getAmount(); if("shar_santy".equals(ItemFactory.getCustomId(i))) santaB+=i.getAmount();}
        if(diamondsA!=12 || diamondsB!=0 || santaA!=0 || santaB!=1) throw new AssertionError("lost or duplicate items");
        Path dir=core.getDataFolder().toPath().resolve("safe-trades/pending");
        try(java.util.stream.Stream<Path> paths=Files.list(dir)) {
            if(paths.anyMatch(p -> p.toString().endsWith(".trade"))) throw new AssertionError("completed recovery journal remains");
        }
        getLogger().info("KG-TRADE-CRASH PASS: SIGKILL between player saves recovered once with exact metadata");
    }
}
